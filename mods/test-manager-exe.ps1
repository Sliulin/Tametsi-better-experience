$ErrorActionPreference = 'Stop'
$gameRoot = Split-Path -Parent $PSScriptRoot
$testId = (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$testRoot = Join-Path $PSScriptRoot "manager-tests\$testId"
$classes = Join-Path $testRoot 'classes'
$asm = Join-Path $PSScriptRoot 'lib\asm-9.7.1.jar'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& "$gameRoot\jre\bin\javac.exe" -encoding UTF-8 -source 1.8 -target 1.8 -cp $asm -d $classes "$PSScriptRoot\tests\ManagerExeProbe.java"
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed.' }
Set-Content -LiteralPath "$testRoot\MANIFEST.MF" -Value "Premain-Class: tametsimodtests.ManagerExeProbe`r`nClass-Path: lib/asm-9.7.1.jar`r`n" -Encoding ASCII
& "$gameRoot\jre\bin\jar.exe" cfm "$testRoot\probe.jar" "$testRoot\MANIFEST.MF" -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed.' }
$version = (Get-Content -LiteralPath "$PSScriptRoot\active-runtime.txt" -Raw).Trim()
$checks = 0
foreach ($scenario in @('saved settings with spaces', 'repointed runtime with spaces')) {
    $sandbox = Join-Path $testRoot $scenario
    New-Item -ItemType Directory -Path "$sandbox\mods\versions", "$sandbox\mods\lib" -Force | Out-Null
    New-Item -ItemType Junction -Path "$sandbox\jre" -Target "$gameRoot\jre" | Out-Null
    Copy-Item -LiteralPath "$gameRoot\Tametsi Mod Manager.exe", "$gameRoot\tametsi.exe" -Destination $sandbox
    Copy-Item -LiteralPath $asm -Destination "$sandbox\mods\lib"
    Copy-Item -LiteralPath "$testRoot\probe.jar" -Destination "$sandbox\mods\manager-probe.jar"
    $selected = if ($scenario.StartsWith('repointed')) { 'versions/tametsi-mod-repointed.jar' } else { $version }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $version) -Destination (Join-Path "$sandbox\mods" $selected)
    Set-Content -LiteralPath "$sandbox\mods\active-runtime.txt" -Value $selected -Encoding ASCII
    $enabled = if ($scenario.StartsWith('repointed')) { 'false' } else { 'true' }
    Set-Content -LiteralPath "$sandbox\mods\settings.properties" -Value "enabled=$enabled`r`nflagChord=false`r`nautoBackup=false" -Encoding ASCII
    $settingsBefore = (Get-FileHash -LiteralPath "$sandbox\mods\settings.properties").Hash
    # Start from outside the installation directory, as shortcuts and Explorer can do.
    $previousJavaOptions = $env:JAVA_TOOL_OPTIONS
    try {
        $env:JAVA_TOOL_OPTIONS = '-javaagent:mods/manager-probe.jar'
        Start-Process -FilePath "$sandbox\Tametsi Mod Manager.exe" -WorkingDirectory $testRoot -WindowStyle Hidden | Out-Null
    } finally {
        $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    }
    $marker = "$sandbox\mods\manager-exe-probe.properties"
    $deadline = (Get-Date).AddSeconds(25)
    while (-not (Test-Path -LiteralPath $marker)) {
        if ((Get-Date) -gt $deadline) { throw "Manager EXE probe timed out: $sandbox" }
        Start-Sleep -Milliseconds 100
    }
    # Properties.store escapes Chinese and backslashes.
    $content = Get-Content -LiteralPath $marker -Raw
    if ($content -notmatch '(?m)^success=true\r?$') { throw $content }; $checks++
    if ($content -notmatch '(?m)^checkboxes=9\r?$') { throw 'Feature UI was not loaded.' }; $checks++
    $expectedPath = (Join-Path "$sandbox\mods" $selected).Replace('\','\\').Replace(':','\:')
    if (-not $content.Contains("runtime=$expectedPath")) { throw 'Active runtime source mismatch.' }; $checks++
    $expectedRoot = $sandbox.Replace('\','\\').Replace(':','\:')
    if (-not $content.Contains("root=$expectedRoot")) { throw 'Wrong working directory.' }; $checks++
    $expectedJre = (Join-Path $sandbox 'jre\jre').Replace('\','\\').Replace(':','\:')
    if (-not $content.Contains("java.home=$expectedJre")) { throw 'Bundled Java was not used.' }; $checks++
    if (-not $content.Contains("box.\u542F\u7528\ Mod=$enabled")) { throw 'Master switch was not loaded.' }; $checks++
    if (-not $content.Contains('box.P\ \u952E\u5207\u6362\u5E38\u9A7B\u90BB\u5C45\u9AD8\u4EAE=true')) { throw 'P feature switch was not loaded.' }; $checks++
    if (-not $content.Contains('box.\u6570\u5B57\u683C\u81EA\u52A8\u63D2\u65D7=false') -or -not $content.Contains('box.\u6BCF\u6B21\u542F\u52A8\u81EA\u52A8\u5907\u4EFD\uFF08\u6700\u8FD1\ 10\ \u4E2A\uFF09=false')) { throw 'Saved feature switches were not loaded.' }; $checks++
    if (-not $content.Contains("game.command=[$expectedRoot\\tametsi.exe]")) { throw 'Original game launch command mismatch.' }; $checks++
    if ((Get-FileHash -LiteralPath "$sandbox\mods\settings.properties").Hash -ne $settingsBefore -or (Test-Path -LiteralPath "$sandbox\tametsi.l4j.ini")) { throw 'Opening manager mutated settings or native integration.' }; $checks++
    if ((Get-Item -LiteralPath "$sandbox\mods\manager-exe-preview.png").Length -lt 5000) { throw 'Manager preview was not painted.' }; $checks++
}
Write-Output "Manager EXE checks passed: $checks"
Write-Output "Artifacts: $testRoot"
