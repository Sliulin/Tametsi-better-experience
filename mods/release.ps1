$ErrorActionPreference = 'Stop'
$gameRoot = Split-Path -Parent $PSScriptRoot
$relative = (Get-Content -LiteralPath "$PSScriptRoot\active-runtime.txt").Trim()
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $relative))) { throw 'Build the mod before packaging.' }
if (-not (Test-Path -LiteralPath "$gameRoot\Tametsi Mod Manager.exe" -PathType Leaf)) { throw 'Build the manager EXE before packaging.' }
$packageId = (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$stage = Join-Path $PSScriptRoot "package-staging\$packageId"
$modStage = Join-Path $stage 'mods'
New-Item -ItemType Directory -Path $modStage, "$PSScriptRoot\dist" -Force | Out-Null
Copy-Item -LiteralPath "$gameRoot\Tametsi Mod.cmd" -Destination $stage
Copy-Item -LiteralPath "$gameRoot\Tametsi Mod Manager.exe" -Destination $stage
New-Item -ItemType Directory -Path "$modStage\versions", "$modStage\lib" -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot $relative) -Destination (Join-Path $modStage $relative)
Copy-Item -LiteralPath "$PSScriptRoot\lib\asm-9.7.1.jar" -Destination "$modStage\lib"
foreach ($name in @('active-runtime.txt', 'check-game.ps1', 'README.md', 'THIRD_PARTY.md', 'build.ps1', 'test.ps1', 'release.ps1', 'build-manager-exe.ps1', 'test-manager-exe.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $modStage
}
New-Item -ItemType Directory -Path "$modStage\launcher-src" -Force | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot\launcher-src\ManagerLauncher.cs" -Destination "$modStage\launcher-src"
foreach ($name in @('src', 'tests', 'vendor', 'resources')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $modStage -Recurse
}
$archive = Join-Path $PSScriptRoot "dist\Tametsi-Mod-v4-$packageId.zip"
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $archive
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$archive.sha256" -Value "$hash  $([IO.Path]::GetFileName($archive))" -Encoding ASCII
Write-Output "Package: $archive"
Write-Output "SHA256: $hash"
