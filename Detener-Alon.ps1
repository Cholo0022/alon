# Detiene solo el servidor y la interfaz de este proyecto. No detiene MySQL.
param([switch]$Comprobar)
$ErrorActionPreference = 'Stop'
$alonTargets = @(
    @{Port=8080; Executable='java.exe'; Path=(Join-Path $PSScriptRoot 'backend/target/alon-api-0.1.0-SNAPSHOT.jar')},
    @{Port=5173; Executable='node.exe'; Path=(Join-Path $PSScriptRoot 'frontend/node_modules/vite/bin/vite.js')}
)
$alonProcesses = @()
# Primero comprueba ambos procesos para evitar cerrar otro programa por error.
foreach ($alonTarget in $alonTargets) {
    $alonListener = Get-NetTCPConnection -State Listen -LocalPort $alonTarget.Port -ErrorAction SilentlyContinue | Select-Object -First 1
    if (!$alonListener) { continue }
    $alonProcess = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $alonListener.OwningProcess)
    $alonExpectedPath = $alonTarget.Path.Replace('\','/')
    if ($alonProcess.Name -ne $alonTarget.Executable -or !$alonProcess.CommandLine -or $alonProcess.CommandLine.Replace('\','/').IndexOf($alonExpectedPath, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw "El puerto $($alonTarget.Port) pertenece a otro programa. No se detuvo ningún proceso."
    }
    $alonProcesses += $alonListener.OwningProcess
}
if ($Comprobar) { Write-Host "Comprobación correcta: $($alonProcesses.Count) procesos de Alon identificados. No se detuvieron."; exit 0 }
foreach ($alonProcessId in $alonProcesses) { Stop-Process -Id $alonProcessId -Force }
Write-Host 'Alon está detenido.'
