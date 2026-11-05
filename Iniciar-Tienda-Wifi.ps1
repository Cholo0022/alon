# Inicia exclusivamente la tienda para celulares de la misma Wi-Fi, sin modificar el firewall ni el inicio de Windows.
param([switch]$SegundoPlano)
$ErrorActionPreference = 'Stop'
$alonFrontend = Join-Path $PSScriptRoot 'frontend'
$alonBridge = Join-Path $alonFrontend 'storefront-lan.mjs'
$alonWifi = Get-NetIPConfiguration | Where-Object { $_.InterfaceAlias -eq 'Wi-Fi' -and $_.IPv4DefaultGateway -and $_.IPv4Address } | Select-Object -First 1
if (!$alonWifi) { throw 'Conectá esta PC a Wi-Fi antes de habilitar la tienda.' }
$alonAddress = $alonWifi.IPv4Address | Where-Object { $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1
if (!$alonAddress) { throw 'La conexión Wi-Fi todavía no tiene una dirección válida.' }
if (!(Test-Path -LiteralPath (Join-Path $alonFrontend 'dist/index.html'))) { throw 'Primero prepará la tienda con npm run build desde frontend.' }
$alonUrl = 'http://' + $alonAddress.IPAddress + ':5174/tienda'
$alonListener = Get-NetTCPConnection -State Listen -LocalPort 5174 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($alonListener) {
    $alonProcess = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $alonListener.OwningProcess)
    if ($alonProcess.Name -ne 'node.exe' -or !$alonProcess.CommandLine -or ($alonProcess.CommandLine.IndexOf($alonBridge, [StringComparison]::OrdinalIgnoreCase) -lt 0 -and $alonProcess.CommandLine -notmatch '(?:\s|")frontend[\\/]storefront-lan\.mjs(?:\s|"|$)')) { throw 'El puerto 5174 pertenece a otro programa. No se modificó ese proceso.' }
    if ($alonListener.LocalAddress -eq $alonAddress.IPAddress) {
        Write-Host ('La tienda ya está disponible en: ' + $alonUrl)
        return
    }
    # Reinicia solo el puente de este proyecto cuando cambia la dirección Wi-Fi.
    Stop-Process -Id $alonListener.OwningProcess -Force
    Start-Sleep -Milliseconds 500
}
try { $alonHealth = Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 5 } catch { throw 'Primero iniciá Alon con Iniciar-Alon.ps1.' }
if ($alonHealth.status -ne 'UP') { throw 'Esperá que termine de iniciar Alon y volvé a abrir la tienda por Wi-Fi.' }
$alonOldHost = $env:ALON_SHOP_WIFI_HOST
$alonOldPrefix = $env:ALON_SHOP_WIFI_PREFIX
try {
    $env:ALON_SHOP_WIFI_HOST = $alonAddress.IPAddress
    $env:ALON_SHOP_WIFI_PREFIX = [string]$alonAddress.PrefixLength
    Write-Host ('Abrí esta dirección en el celular conectado al mismo Wi-Fi: ' + $alonUrl)
    if (!$SegundoPlano) { Write-Host 'Mantené esta ventana abierta mientras uses la tienda desde el celular.' }
    if ($SegundoPlano) {
        $alonRuntime = Join-Path $PSScriptRoot '.validation/runtime'
        New-Item -ItemType Directory -Path $alonRuntime -Force | Out-Null
        Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList ('"' + $alonBridge + '"') -WorkingDirectory $alonFrontend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $alonRuntime 'tienda-wifi.log') -RedirectStandardError (Join-Path $alonRuntime 'tienda-wifi-error.log') | Out-Null
        $alonDeadline = (Get-Date).AddSeconds(15)
        do {
            try {
                $alonResponse = Invoke-WebRequest -Uri $alonUrl -UseBasicParsing -TimeoutSec 2
                if ($alonResponse.StatusCode -eq 200) { return }
            } catch { }
            Start-Sleep -Milliseconds 500
        } while ((Get-Date) -lt $alonDeadline)
        throw 'No se pudo iniciar la tienda por Wi-Fi. Revisá los registros de la tienda.'
    } else {
        & (Get-Command node.exe).Source $alonBridge
    }
} finally {
    $env:ALON_SHOP_WIFI_HOST = $alonOldHost
    $env:ALON_SHOP_WIFI_PREFIX = $alonOldPrefix
}
