param([Parameter(Mandatory=$true)][string]$GameRoot, [int]$ExcludePid = 0, [switch]$AllowWrapperParent)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
try {
    $rootPath = [IO.Path]::GetFullPath($GameRoot).TrimEnd('\').Replace('/', '\')
    $gameExe = [IO.Path]::Combine($rootPath, 'tametsi.exe')
    $processes = Get-CimInstance Win32_Process -Filter "Name = 'tametsi.exe' OR Name = 'java.exe' OR Name = 'javaw.exe'"
    $wrapperPid = 0
    if ($AllowWrapperParent -and $ExcludePid -gt 0) {
        $selfProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $ExcludePid"
        if ($selfProcess) {
            $parentProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($selfProcess.ParentProcessId)"
            if ($parentProcess -and $parentProcess.Name -ieq 'tametsi.exe' -and $parentProcess.ExecutablePath -ieq $gameExe) {
                $wrapperPid = $parentProcess.ProcessId
            }
        }
    }
    :scanProcesses foreach ($process in $processes) {
        if ($process.ProcessId -eq $ExcludePid) { continue }
        if ($wrapperPid -gt 0 -and $process.ProcessId -eq $wrapperPid) { continue }
        $running = $false
        if ($process.Name -ieq 'tametsi.exe') {
            # An inaccessible path cannot establish that this is another installation.
            $running = -not $process.ExecutablePath -or $process.ExecutablePath -ieq $gameExe
        } else {
            $commandLine = $process.CommandLine
            if (-not $commandLine) {
                # A process can exit between enumeration and metadata collection.
                for ($retry = 0; $retry -lt 3 -and -not $commandLine; $retry++) {
                    $liveProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.ProcessId)"
                    if (-not $liveProcess) { continue scanProcesses }
                    $commandLine = $liveProcess.CommandLine
                    if (-not $commandLine) { Start-Sleep -Milliseconds 100 }
                }
                if (-not $commandLine) { throw 'Cannot inspect a Java process. Start and restore are blocked until its status can be checked.' }
            }
            $normalized = $commandLine.Replace('/', '\')
            $sameRoot = $normalized.IndexOf($rootPath, [StringComparison]::OrdinalIgnoreCase) -ge 0
            $gameEntry = $commandLine -match 'tametsimod\.Bootstrap|launcher\.Launcher|(?:-jar\s+).*tametsi\.exe'
            $running = $sameRoot -and $gameEntry
        }
        if ($running) { Write-Output ('RUNNING|' + $process.ProcessId + '|' + $process.Name) }
    }
    Write-Output 'CHECK_OK'
    exit 0
} catch {
    Write-Output ('CHECK_ERROR|' + $_.Exception.Message)
    exit 2
}
