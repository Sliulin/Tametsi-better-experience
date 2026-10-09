$ErrorActionPreference = 'Stop'
$gameRoot = Split-Path -Parent $PSScriptRoot
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $compiler)) {
    $compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe'
}
if (-not (Test-Path -LiteralPath $compiler)) { throw 'The Windows .NET Framework 4 compiler was not found.' }
$buildId = (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$build = Join-Path $PSScriptRoot "manager-build\$buildId"
New-Item -ItemType Directory -Path $build -Force | Out-Null
$output = Join-Path $build 'Tametsi Mod Manager.exe'
& $compiler /nologo /target:winexe /platform:anycpu /optimize+ /codepage:65001 /reference:System.Windows.Forms.dll "/out:$output" "$PSScriptRoot\launcher-src\ManagerLauncher.cs"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $output)) { throw 'EXE compilation failed.' }
Copy-Item -LiteralPath $output -Destination "$gameRoot\Tametsi Mod Manager.exe" -Force
Write-Output "Manager EXE: $gameRoot\Tametsi Mod Manager.exe"
Write-Output "SHA256: $((Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant())"
