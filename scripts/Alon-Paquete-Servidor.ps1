# Herramientas compartidas de inicio y detención. No inicia ni detiene procesos al cargarlo.
function Test-AlonServerCommand([string]$CommandLine, [string]$Project) {
    if (!$CommandLine) { return $false }
    $alonNormalized = $CommandLine.Replace('\','/')
    $alonLegacy = [regex]::Escape((Join-Path $Project 'backend/target/alon-api-0.1.0-SNAPSHOT.jar').Replace('\','/'))
    $alonPublished = [regex]::Escape((Join-Path $Project '.validation/runtime/server').Replace('\','/')) + '/alon-api-[0-9a-f]{64}\.jar'
    return [regex]::IsMatch($alonNormalized, '(?:^|\s)-jar\s+(?:"(?:' + $alonLegacy + '|' + $alonPublished + ')"|(?:' + $alonLegacy + '|' + $alonPublished + '))(?:\s|$)', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
}

function Get-AlonServerSnapshot([string]$SourceJar, [string]$RuntimeRoot) {
    $alonPublishedFolder = Join-Path $RuntimeRoot 'server'
    New-Item -ItemType Directory -Path $alonPublishedFolder -Force | Out-Null
    $alonTemporary = Join-Path $alonPublishedFolder ('preparando-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        Copy-Item -LiteralPath $SourceJar -Destination $alonTemporary
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $alonArchive = [IO.Compression.ZipFile]::OpenRead($alonTemporary)
        try {
            if (!$alonArchive.GetEntry('BOOT-INF/classes/ar/com/alon/AlonApplication.class') -or
                !$alonArchive.GetEntry('org/springframework/boot/loader/launch/JarLauncher.class') -or
                !($alonArchive.Entries | Where-Object { $_.FullName.StartsWith('BOOT-INF/lib/') -and $_.FullName.EndsWith('.jar') } | Select-Object -First 1)) {
                throw 'El paquete del servidor está incompleto. Esperá a que termine la compilación antes de iniciar Alon.'
            }
        } finally { $alonArchive.Dispose() }
        $alonHash = (Get-FileHash -LiteralPath $alonTemporary -Algorithm SHA256).Hash.ToLowerInvariant()
        $alonPublishedJar = Join-Path $alonPublishedFolder ('alon-api-' + $alonHash + '.jar')
        if (Test-Path -LiteralPath $alonPublishedJar) {
            if ((Get-FileHash -LiteralPath $alonPublishedJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $alonHash) {
                throw 'La copia de ejecución del servidor está dañada. Revisá los archivos antes de iniciar Alon.'
            }
        } else {
            $alonExpectedFolder = [IO.Path]::GetFullPath($alonPublishedFolder).TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
            if (![IO.Path]::GetFullPath($alonTemporary).StartsWith($alonExpectedFolder, [StringComparison]::OrdinalIgnoreCase) -or
                ![IO.Path]::GetFullPath($alonPublishedJar).StartsWith($alonExpectedFolder, [StringComparison]::OrdinalIgnoreCase)) {
                throw 'La copia de ejecución debe permanecer dentro de la carpeta del servidor.'
            }
            [IO.File]::Copy($alonTemporary, $alonPublishedJar, $false)
        }
        return $alonPublishedJar
    } finally {
        if (Test-Path -LiteralPath $alonTemporary) { Remove-Item -LiteralPath $alonTemporary }
    }
}
