param([string]$DirectorioImagenes = $env:ALON_IMAGES_DIRECTORY, [string]$Destino = 'D:\Respaldos\Alon\Imagenes')
$ErrorActionPreference = 'Stop'
if (!$DirectorioImagenes) { $DirectorioImagenes = Join-Path $PSScriptRoot 'backend/data/article-images' }
if (![IO.Path]::IsPathRooted($DirectorioImagenes)) { $DirectorioImagenes = Join-Path (Join-Path $PSScriptRoot 'backend') $DirectorioImagenes }
$alonSource = (Resolve-Path -LiteralPath $DirectorioImagenes).Path
$alonDestination = [IO.Path]::GetFullPath($Destino)
if ($alonDestination.StartsWith($alonSource.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -or $alonDestination -eq $alonSource) { throw 'El destino debe estar fuera de las imágenes originales.' }
New-Item -ItemType Directory -Path $alonDestination -Force | Out-Null
$alonZip = Join-Path $alonDestination ('alon-imagenes-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString() + '.zip')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$alonFiles = @(Get-ChildItem -LiteralPath $alonSource -File -Recurse)
if (!$alonFiles.Count) { throw 'No hay imágenes para respaldar.' }
$alonManifest = @($alonFiles | ForEach-Object { [pscustomobject]@{ archivo = $_.FullName.Substring($alonSource.Length + 1).Replace('\','/'); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } })
[IO.Compression.ZipFile]::CreateFromDirectory($alonSource, $alonZip + '.partial')
$alonArchive = [IO.Compression.ZipFile]::OpenRead($alonZip + '.partial')
try {
    if ($alonArchive.Entries.Where({$_.Name}).Count -ne $alonManifest.Count) { throw 'Cambió la cantidad de imágenes durante el respaldo; repetí sin modificar fotos.' }
    foreach ($alonItem in $alonManifest) {
        $alonEntry = $alonArchive.GetEntry($alonItem.archivo)
        if (!$alonEntry) { throw 'Falta una imagen en el respaldo.' }
        $alonStream = $alonEntry.Open()
        $alonSha = [Security.Cryptography.SHA256]::Create()
        try { $alonHash = [BitConverter]::ToString($alonSha.ComputeHash($alonStream)).Replace('-','') } finally { $alonStream.Dispose(); $alonSha.Dispose() }
        if ($alonHash -ne $alonItem.sha256) { throw 'Una imagen cambió durante el respaldo; repetí sin modificar fotos.' }
    }
} finally { $alonArchive.Dispose() }
Move-Item -LiteralPath ($alonZip + '.partial') -Destination $alonZip
$alonManifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath ($alonZip + '.sha256.json') -Encoding UTF8
Write-Output "Respaldo verificado: $alonZip ($($alonManifest.Count) imágenes)"
