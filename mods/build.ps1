$ErrorActionPreference = 'Stop'
$gameRoot = Split-Path -Parent $PSScriptRoot
if ((Get-FileHash -LiteralPath "$PSScriptRoot\lib\asm-9.7.1.jar" -Algorithm SHA256).Hash.ToLowerInvariant() -ne '8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281') {
    throw 'ASM dependency checksum mismatch.'
}
$sources = @(Get-ChildItem -LiteralPath "$PSScriptRoot\src\tametsimod" -Filter '*.java' -File | ForEach-Object { $_.FullName })
$buildId = (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$classes = Join-Path $PSScriptRoot "build\$buildId\classes"
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& "$gameRoot\jre\bin\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -classpath "$gameRoot\tametsi.exe;$PSScriptRoot\lib\asm-9.7.1.jar" -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
$candidate = Join-Path $PSScriptRoot "build\$buildId\tametsi-mod.jar"
$manifest = Join-Path $PSScriptRoot "build\$buildId\MANIFEST.MF"
Set-Content -LiteralPath $manifest -Value "Premain-Class: tametsimod.ModAgent`r`nClass-Path: ../lib/asm-9.7.1.jar`r`n" -Encoding ASCII
& "$gameRoot\jre\bin\jar.exe" cfm $candidate $manifest -C $classes . -C "$PSScriptRoot\resources" tametsimod/flag.png -C $PSScriptRoot THIRD_PARTY.md -C $PSScriptRoot vendor/tf0119-minesweeper/LICENSE -C $PSScriptRoot vendor/asm/LICENSE -C $PSScriptRoot vendor/twemoji/LICENSE-GRAPHICS
if ($LASTEXITCODE -ne 0) { throw 'Mod packaging failed.' }
New-Item -ItemType Directory -Path "$PSScriptRoot\versions" -Force | Out-Null
$relative = "versions/tametsi-mod-v4-$buildId.jar"
Copy-Item -LiteralPath $candidate -Destination (Join-Path $PSScriptRoot $relative)
Set-Content -LiteralPath "$PSScriptRoot\active-runtime.txt" -Value $relative -Encoding ASCII
Write-Output "Built: $PSScriptRoot\$relative"
Write-Output "Classes: $classes"
