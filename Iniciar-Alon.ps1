# Inicia Alon con la conexión local guardada; -Reiniciar vuelve a abrir ambos componentes.
param([switch]$Reiniciar, [switch]$Configurar)
$ErrorActionPreference = 'Stop'
$alonProject = $PSScriptRoot
$alonSettings = Join-Path $alonProject '.validation/connection'
$alonRuntime = Join-Path $alonProject '.validation/runtime'
$alonBackend = Join-Path $alonProject 'backend'
$alonFrontend = Join-Path $alonProject 'frontend'
$alonJar = Join-Path $alonBackend 'target/alon-api-0.1.0-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $alonJar)) { throw 'Primero compilá el servidor desde la carpeta backend con mvn package.' }
New-Item -ItemType Directory -Path $alonRuntime, $alonSettings -Force | Out-Null
$alonConfigFile = Join-Path $alonSettings 'startup.json'
$alonCredentialFile = Join-Path $alonSettings 'mysql-credential.xml'
$alonConfig = $null
$alonCredential = $null

# Recupera la configuración anterior cuando todavía está disponible.
$alonOldSettings = Join-Path $env:LOCALAPPDATA 'Alon'
if (!(Test-Path -LiteralPath $alonConfigFile) -and (Test-Path -LiteralPath (Join-Path $alonOldSettings 'startup.json'))) {
    Copy-Item -LiteralPath (Join-Path $alonOldSettings 'startup.json') -Destination $alonConfigFile
}
if (!(Test-Path -LiteralPath $alonCredentialFile) -and (Test-Path -LiteralPath (Join-Path $alonOldSettings 'mysql-credential.xml'))) {
    Copy-Item -LiteralPath (Join-Path $alonOldSettings 'mysql-credential.xml') -Destination $alonCredentialFile
}
if (!$Configurar) {
    try {
        if (Test-Path -LiteralPath $alonConfigFile) { $alonConfig = Get-Content -LiteralPath $alonConfigFile -Raw | ConvertFrom-Json }
        if (Test-Path -LiteralPath $alonCredentialFile) { $alonCredential = Import-Clixml -LiteralPath $alonCredentialFile }
    } catch { Write-Host 'Se necesita configurar nuevamente la conexión local de Alon.' }
}

# Si faltan archivos, usa las variables de Git Bash o pide la conexión una sola vez.
if (!$alonConfig -or !$alonConfig.databaseUrl -or $Configurar) {
    $alonUrl = $env:SPRING_DATASOURCE_URL
    if (!$alonUrl -or $Configurar) { $alonUrl = Read-Host 'URL JDBC de tu base actual (jdbc:mysql://servidor:puerto/base)' }
    if ($alonUrl -notmatch '^jdbc:mysql://[^/]+/[^?;\s]+') { throw 'La dirección debe incluir el servidor y el nombre de tu base actual.' }
    $alonConfig = [pscustomobject]@{databaseUrl=$alonUrl}
    $alonConfig | ConvertTo-Json | Set-Content -LiteralPath $alonConfigFile -Encoding UTF8
}
if (!$alonCredential -or $Configurar) {
    if (!$Configurar -and $env:SPRING_DATASOURCE_USERNAME -and $null -ne $env:SPRING_DATASOURCE_PASSWORD) {
        $alonSecurePassword = ConvertTo-SecureString $env:SPRING_DATASOURCE_PASSWORD -AsPlainText -Force
        $alonCredential = New-Object System.Management.Automation.PSCredential($env:SPRING_DATASOURCE_USERNAME, $alonSecurePassword)
    } else {
        $alonUser = Read-Host 'Usuario de MySQL'
        $alonSecurePassword = Read-Host 'Contraseña de MySQL' -AsSecureString
        $alonCredential = New-Object System.Management.Automation.PSCredential($alonUser, $alonSecurePassword)
    }
    $alonCredential | Export-Clixml -LiteralPath $alonCredentialFile
}

function Wait-AlonUrl([string]$Url, [int]$TimeoutSeconds) {
    $alonDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $alonDeadline) {
        try {
            $alonResponse = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($alonResponse.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Milliseconds 750
    }
    throw "Alon no respondió. Revisá los registros en $alonRuntime."
}

# Solo detiene procesos de Java y Vite que pertenecen a este proyecto.
function Restart-AlonPort([int]$Port, [string]$Executable, [string]$ExpectedPath) {
    $alonListener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if (!$alonListener) { return }
    $alonProcess = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $alonListener.OwningProcess)
    if ($alonProcess.Name -ne $Executable -or !$alonProcess.CommandLine -or $alonProcess.CommandLine.IndexOf($ExpectedPath, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw "El puerto $Port pertenece a otro programa; no se cerró ese proceso."
    }
    Stop-Process -Id $alonListener.OwningProcess -Force
    $alonDeadline = (Get-Date).AddSeconds(10)
    while ((Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) -and (Get-Date) -lt $alonDeadline) { Start-Sleep -Milliseconds 250 }
}
if ($Reiniciar) {
    Restart-AlonPort 8080 'java.exe' $alonJar
    Restart-AlonPort 5173 'node.exe' (Join-Path $alonFrontend 'node_modules/vite/bin/vite.js')
}

# Windows descifra la contraseña para este usuario; no se incluye en el comando de Java.
if (!(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)) {
    $alonPreviousUrl = $env:SPRING_DATASOURCE_URL
    $alonPreviousUser = $env:SPRING_DATASOURCE_USERNAME
    $alonPreviousPassword = $env:SPRING_DATASOURCE_PASSWORD
    try {
        $env:SPRING_DATASOURCE_URL = $alonConfig.databaseUrl
        $env:SPRING_DATASOURCE_USERNAME = $alonCredential.UserName
        $env:SPRING_DATASOURCE_PASSWORD = $alonCredential.GetNetworkCredential().Password
        Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList '-jar',('"' + $alonJar + '"') -WorkingDirectory $alonBackend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $alonRuntime 'servidor.log') -RedirectStandardError (Join-Path $alonRuntime 'servidor-error.log') | Out-Null
    } finally {
        $env:SPRING_DATASOURCE_URL = $alonPreviousUrl
        $env:SPRING_DATASOURCE_USERNAME = $alonPreviousUser
        $env:SPRING_DATASOURCE_PASSWORD = $alonPreviousPassword
    }
}
Write-Host 'Esperando al servidor de Alon...'
Wait-AlonUrl 'http://127.0.0.1:8080/actuator/health' 150
if (!(Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue)) {
    $alonVite = Join-Path $alonFrontend 'node_modules/vite/bin/vite.js'
    Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList ('"' + $alonVite + '"'),'--host','127.0.0.1','--port','5173','--strictPort' -WorkingDirectory $alonFrontend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $alonRuntime 'interfaz.log') -RedirectStandardError (Join-Path $alonRuntime 'interfaz-error.log') | Out-Null
}
Wait-AlonUrl 'http://127.0.0.1:5173/' 45
Write-Host 'Alon está listo: http://127.0.0.1:5173'
Write-Host 'Tienda web: http://127.0.0.1:5173/tienda'
