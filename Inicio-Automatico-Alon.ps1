# Se ejecuta oculto al iniciar sesión en Windows. No abre la gestión ni modifica el firewall.
$ErrorActionPreference = 'Stop'
$alonRuntime = Join-Path $PSScriptRoot '.validation/runtime'
New-Item -ItemType Directory -Path $alonRuntime -Force | Out-Null
$alonStartupLog = Join-Path $alonRuntime 'inicio-automatico.log'
try {
    & (Join-Path $PSScriptRoot 'Iniciar-Alon.ps1') *>&1 | Out-File -LiteralPath $alonStartupLog -Encoding utf8
    # La conexión Wi-Fi puede tardar un poco más que Windows en estar disponible.
    for ($alonAttempt = 0; $alonAttempt -lt 36; $alonAttempt++) {
        try {
            & (Join-Path $PSScriptRoot 'Iniciar-Tienda-Wifi.ps1') -SegundoPlano *>&1 | Out-File -LiteralPath $alonStartupLog -Encoding utf8 -Append
            return
        } catch {
            if ($alonAttempt -eq 35) { throw }
            Start-Sleep -Seconds 10
        }
    }
} catch {
    $_ | Out-File -LiteralPath $alonStartupLog -Encoding utf8 -Append
    exit 1
}
