# Prueba aislada y optativa. No modifica ni elimina la base configurada de Alon.
param(
    [ValidateRange(20,200)][int]$VentasPorPuesto = 40
)
$ErrorActionPreference = 'Stop'
$alonProject = Split-Path -Parent $PSScriptRoot
$alonSettingsPath = Join-Path $alonProject '.validation/connection/startup.json'
$alonCredentialPath = Join-Path $alonProject '.validation/connection/mysql-credential.xml'
$alonRunId = (Get-Date -Format 'yyyyMMdd') + '_' + [guid]::NewGuid().ToString('N').Substring(0,8)
$alonSchema = 'alon_perf_validation_' + $alonRunId
$alonOutput = Join-Path $alonProject ('output/performance/' + $alonRunId)
New-Item -ItemType Directory -Path $alonOutput -Force | Out-Null
$alonEnvNames = @('MYSQL_PWD','ALON_PERF_MYSQL_URL','ALON_PERF_MYSQL_USER','ALON_PERF_MYSQL_PASSWORD','ALON_PERF_REPORT','ALON_PERF_GIT_REVISION','ALON_PERF_SOURCE_SHA256','ALON_PERF_SALES_PER_WORKER')
$alonPrevious = @{}
foreach ($alonName in $alonEnvNames) { $alonPrevious[$alonName] = [Environment]::GetEnvironmentVariable($alonName, 'Process') }
$alonLocation = Get-Location
$alonPhase = 'preflight'
try {
    $alonSettings = Get-Content -LiteralPath $alonSettingsPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $alonCredential = Import-Clixml -LiteralPath $alonCredentialPath
    $alonPrefix = [regex]::Match($alonSettings.databaseUrl, '^jdbc:mysql://[^/]+/').Value
    if (!$alonPrefix) { throw 'La URL JDBC guardada no es compatible con esta prueba.' }
    $alonUri = [uri]($alonSettings.databaseUrl -replace '^jdbc:','')
    $alonPort = if ($alonUri.Port -gt 0) { $alonUri.Port } else { 3306 }
    $alonMySql = Get-Command mysql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
    if (!$alonMySql) {
        $alonMySql = @('C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe','C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe') | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
    if (!$alonMySql) { throw 'No se encontró mysql.exe para comprobar la conexión.' }
    $env:MYSQL_PWD = $alonCredential.GetNetworkCredential().Password
    & $alonMySql "--host=$($alonUri.Host)" "--port=$alonPort" "--user=$($alonCredential.UserName)" --connect-timeout=4 --batch --skip-column-names --execute='SELECT VERSION()'
    if ($LASTEXITCODE -ne 0) { throw 'MySQL no aceptó la conexión. No se crearon datos de prueba.' }
    [Environment]::SetEnvironmentVariable('MYSQL_PWD',$alonPrevious['MYSQL_PWD'],'Process')
    $alonExistingQuery = [regex]::Match($alonSettings.databaseUrl, '\?.*$').Value.TrimStart('?')
    # Conserva SSL y los parámetros de la conexión instalada; cambia únicamente el esquema.
    $env:ALON_PERF_MYSQL_URL = $alonPrefix + $alonSchema + '?' + $(if ($alonExistingQuery) { $alonExistingQuery + '&' } else { '' }) + 'createDatabaseIfNotExist=true&rewriteBatchedStatements=true&connectTimeout=5000&socketTimeout=130000'
    $env:ALON_PERF_MYSQL_USER = $alonCredential.UserName
    $env:ALON_PERF_MYSQL_PASSWORD = $alonCredential.GetNetworkCredential().Password
    $env:ALON_PERF_REPORT = Join-Path $alonOutput 'results.json'
    $env:ALON_PERF_SALES_PER_WORKER = [string]$VentasPorPuesto
    Set-Location -LiteralPath $alonProject
    $env:ALON_PERF_GIT_REVISION = (git rev-parse HEAD)
    $alonFiles = @(Get-ChildItem backend/src/main/java,backend/src/main/resources -Recurse -File) + @(Get-Item backend/src/test/java/ar/com/alon/performance/CapacityBenchmarkTest.java)
    $alonFingerprint = $alonFiles | Sort-Object FullName | ForEach-Object { [pscustomobject]@{ path=$_.FullName.Substring($alonProject.Length+1); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } }
    $alonFingerprint | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $alonOutput 'source-fingerprint.json') -Encoding utf8
    $env:ALON_PERF_SOURCE_SHA256 = (Get-FileHash -LiteralPath (Join-Path $alonOutput 'source-fingerprint.json') -Algorithm SHA256).Hash
    Write-Host "Base exclusiva: $alonSchema. No se reutilizan ni se borran bases."
    Write-Host 'Generando 10.000 artículos, 100.000 ventas y 1.000.000 de renglones. Puede tardar varios minutos.'
    $alonPhase = 'benchmark'
    Set-Location -LiteralPath (Join-Path $alonProject 'backend')
    & mvn test '-Dtest=CapacityBenchmarkTest' 2>&1 | Tee-Object -FilePath (Join-Path $alonOutput 'execution.log')
    if ($LASTEXITCODE -ne 0) { throw 'La prueba no terminó correctamente. Revisá execution.log y results.json.' }
    $alonResult = Get-Content -LiteralPath $env:ALON_PERF_REPORT -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($alonResult.status -ne 'completed') { throw 'El informe no acredita una ejecución completa.' }
    Write-Host "Prueba terminada. Resultados: $alonOutput"
    Write-Host "Se conserva la base $alonSchema para inspección. No se elimina automáticamente."
} catch {
    if ($alonPhase -eq 'preflight') {
        [pscustomobject]@{ status='blocked'; phase='preflight'; checked_at=[DateTimeOffset]::Now.ToString('o'); timings_measured=$false; note='No se pudo completar la conexión o preparar la prueba. No se modificó la base del negocio.' } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $alonOutput 'status.json') -Encoding utf8
    }
    throw
} finally {
    Set-Location -LiteralPath $alonLocation
    foreach ($alonName in $alonEnvNames) { [Environment]::SetEnvironmentVariable($alonName,$alonPrevious[$alonName],'Process') }
}
