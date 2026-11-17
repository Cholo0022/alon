# Prueba archivos temporales y reconocimiento de comandos; no toca servicios ni bases.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Alon-Paquete-Servidor.ps1')
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$alonTestRoot = Join-Path ([IO.Path]::GetTempPath()) ('alon-start-test-' + [guid]::NewGuid().ToString('N'))
$alonRuntimeTest = Join-Path $alonTestRoot 'runtime'
$alonSourceTest = Join-Path $alonTestRoot 'source.jar'
$alonSnapshotsTest = @()
function Assert-AlonStart($Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}
try {
    New-Item -ItemType Directory -Path $alonTestRoot -Force | Out-Null
    $alonArchiveTest = [IO.Compression.ZipFile]::Open($alonSourceTest, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($alonEntryName in @('BOOT-INF/classes/ar/com/alon/AlonApplication.class', 'org/springframework/boot/loader/launch/JarLauncher.class', 'BOOT-INF/lib/example.jar')) {
            $alonWriter = [IO.StreamWriter]::new($alonArchiveTest.CreateEntry($alonEntryName).Open())
            try { $alonWriter.Write('test fixture') } finally { $alonWriter.Dispose() }
        }
    } finally { $alonArchiveTest.Dispose() }
    $alonFirst = Get-AlonServerSnapshot $alonSourceTest $alonRuntimeTest
    $alonSnapshotsTest += $alonFirst
    $alonHashBefore = (Get-FileHash -LiteralPath $alonFirst).Hash
    Assert-AlonStart ((Get-AlonServerSnapshot $alonSourceTest $alonRuntimeTest) -eq $alonFirst) 'La misma versión debe reutilizar su copia estable.'
    [IO.File]::WriteAllText($alonSourceTest, 'compilación incompleta')
    $alonRejected = $false
    try { Get-AlonServerSnapshot $alonSourceTest $alonRuntimeTest | Out-Null } catch { $alonRejected = $true }
    Assert-AlonStart $alonRejected 'Debe rechazar un paquete incompleto.'
    Assert-AlonStart ((Get-FileHash -LiteralPath $alonFirst).Hash -eq $alonHashBefore) 'Compilar no debe alterar la copia en ejecución.'
    $alonProjectTest = Split-Path -Parent $PSScriptRoot
    $alonLegacyTest = Join-Path $alonProjectTest 'backend/target/alon-api-0.1.0-SNAPSHOT.jar'
    $alonPublishedTest = Join-Path $alonProjectTest ('.validation/runtime/server/alon-api-' + ('a' * 64) + '.jar')
    Assert-AlonStart (Test-AlonServerCommand ('java -jar "' + $alonLegacyTest + '"') $alonProjectTest) 'Debe reconocer el servidor anterior.'
    Assert-AlonStart (Test-AlonServerCommand ('java -jar "' + $alonPublishedTest + '"') $alonProjectTest) 'Debe reconocer la copia estable del proyecto.'
    Assert-AlonStart (!(Test-AlonServerCommand ('java -Dexample="' + $alonLegacyTest + '" -jar other.jar') $alonProjectTest)) 'No debe reconocer una propiedad como ejecutable.'
    Assert-AlonStart (!(Test-AlonServerCommand ('java -jar "' + $alonPublishedTest + '.other"') $alonProjectTest)) 'No debe reconocer archivos ajenos con un prefijo parecido.'
    Write-Host 'Inicio seguro: 7 comprobaciones correctas. No se iniciaron ni detuvieron procesos.'
} finally {
    foreach ($alonTestFile in (@($alonSourceTest) + $alonSnapshotsTest)) {
        if (Test-Path -LiteralPath $alonTestFile) { Remove-Item -LiteralPath $alonTestFile }
    }
    foreach ($alonTestDirectory in @((Join-Path $alonRuntimeTest 'server'), $alonRuntimeTest, $alonTestRoot)) {
        if (Test-Path -LiteralPath $alonTestDirectory) { Remove-Item -LiteralPath $alonTestDirectory }
    }
}
