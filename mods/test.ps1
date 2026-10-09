param([ValidateSet('vanilla', 'mod', 'backups', 'gui', 'native')][string[]]$Modes = @('vanilla', 'mod', 'backups', 'gui', 'native'))
$ErrorActionPreference = 'Stop'
$gameRoot = Split-Path -Parent $PSScriptRoot
& "$PSScriptRoot\build.ps1"
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$runRoot = Join-Path $PSScriptRoot "test-runs\$runId"
$classes = Join-Path $runRoot 'test-classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath "$PSScriptRoot\tests\tametsimod" -Filter '*.java' -File | ForEach-Object { $_.FullName })
$runtime = Join-Path $PSScriptRoot (Get-Content -LiteralPath "$PSScriptRoot\active-runtime.txt").Trim()
$classpath = "$runtime;$PSScriptRoot\lib\asm-9.7.1.jar;$gameRoot\tametsi.exe"
& "$gameRoot\jre\bin\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -classpath $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
$originalPaths = @('tametsi.exe', 'config.txt', 'profiles\p0.sav', 'profiles\p1.sav', 'profiles\p2.sav')
$before = @{}
foreach ($relative in $originalPaths) {
    $full = Join-Path $gameRoot $relative
    if (Test-Path -LiteralPath $full) { $before[$relative] = (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash }
}
foreach ($mode in $Modes) {
    $sandbox = Join-Path $runRoot $mode
    if ($mode -eq 'native') { $sandbox = Join-Path $runRoot 'native with spaces' }
    New-Item -ItemType Directory -Path "$sandbox\mods", "$sandbox\profiles", "$sandbox\puzzles" -Force | Out-Null
    Copy-Item -LiteralPath "$gameRoot\tametsi.exe" -Destination "$sandbox\tametsi.exe"
    Copy-Item -LiteralPath "$gameRoot\config.txt" -Destination "$sandbox\config.txt"
    Copy-Item -LiteralPath "$PSScriptRoot\check-game.ps1" -Destination "$sandbox\mods\check-game.ps1"
    New-Item -ItemType Directory -Path "$sandbox\mods\versions", "$sandbox\mods\lib" -Force | Out-Null
    $sandboxRuntime = Join-Path "$sandbox\mods\versions" ([IO.Path]::GetFileName($runtime))
    Copy-Item -LiteralPath $runtime -Destination $sandboxRuntime
    Copy-Item -LiteralPath "$PSScriptRoot\lib\asm-9.7.1.jar" -Destination "$sandbox\mods\lib"
    if ($mode -eq 'native') { New-Item -ItemType Junction -Path "$sandbox\jre" -Target "$gameRoot\jre" | Out-Null }
    Get-ChildItem -LiteralPath "$gameRoot\puzzles" -File | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path "$sandbox\puzzles" $_.Name)
    }
    # Synthetic test saves are created only in this sandbox, never copied over user saves.
    if ($mode -eq 'backups') { Set-Content -LiteralPath "$sandbox\profiles\p0.sav" -Value 'fixture' -Encoding ASCII }
    Push-Location -LiteralPath $sandbox
    try {
        # Original renderer needs a graphics device; tests paint offscreen and show no windows.
        $agentArgs = @()
        if ($mode -eq 'mod') { $agentArgs = @("-javaagent:$sandboxRuntime") }
        $sandboxClasspath = "$sandboxRuntime;$sandbox\mods\lib\asm-9.7.1.jar;$sandbox\tametsi.exe"
        & "$gameRoot\jre\bin\java.exe" @agentArgs "-Djava.awt.headless=false" -cp "$classes;$sandboxClasspath" tametsimod.ModTests $mode
        if ($LASTEXITCODE -ne 0) { throw "Tests failed: $mode; artifacts preserved at $sandbox" }
        if ($mode -eq 'mod') {
            & "$gameRoot\jre\bin\java.exe" "-Djava.awt.headless=true" -cp "$classes;$sandboxClasspath" tametsimod.ModTests reload
            if ($LASTEXITCODE -ne 0) { throw 'Mod-to-vanilla save compatibility failed.' }
        }
    } finally { Pop-Location }
}
foreach ($relative in $before.Keys) {
    $after = (Get-FileHash -LiteralPath (Join-Path $gameRoot $relative) -Algorithm SHA256).Hash
    if ($before[$relative] -ne $after) { throw "Original file changed unexpectedly: $relative" }
}
Write-Output 'PASS: original executable, config and three save slots unchanged.'
Write-Output "Test artifacts preserved: $runRoot"
