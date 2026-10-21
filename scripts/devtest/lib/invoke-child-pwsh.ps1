#requires -Version 7.0
<#
.SYNOPSIS
    S4413 - runs a child pwsh script through System.Diagnostics.Process, never the call operator.

.DESCRIPTION
    Invoke-ChildPwsh starts the running pwsh executable with `-NoProfile -File <script>` and the
    given arguments, with no window and both output streams redirected, and returns
    [pscustomobject]@{ Exit; Output } where Output is every stdout and stderr line in arrival order,
    joined with newlines.

    Consumers: the three Invoke-AdbVerb wrappers of scripts/devtest/wear-prerelease-walk.ps1,
    scripts/devtest/wear-prerelease-prepare.ps1 and scripts/devtest/lib/ui-sweep-device-io.ps1.
    Any later script that spawns adb.ps1 from a long walk should use it too.

    A start the operating system refuses (missing executable, loader failure before the child runs)
    is returned as a negative Exit with the reason in Output, the same sign a Windows loader NTSTATUS
    carries, so the spawn-failure accounting of ui-sweep-device-io.ps1 counts it as a dead spawn.

.PARAMETER Script
    Path of the child .ps1 script, passed to `pwsh -File`.

.PARAMETER Arguments
    Arguments for the child script, each passed as one ArgumentList element so spaces and quotes
    arrive intact.

.PARAMETER Executable
    The pwsh executable to start. Default: the running process's own path. The contract suite
    overrides it to prove the refused-start path.

.EXAMPLE
    . (Join-Path $PSScriptRoot 'lib/invoke-child-pwsh.ps1')
    $call = Invoke-ChildPwsh -Script $AdbWrapper -Arguments @('shell', '-Cmd', 'wm size')
    if ($call.Exit -ne 0) { Write-Warning $call.Output }

.NOTES
    Dot-sourced library: it defines one function and assigns nothing at script scope.
    Exit codes: none of its own; the child's exit code is returned in the Exit property.
#>

function Invoke-ChildPwsh {
    param(
        [Parameter(Mandatory)][string]$Script,
        [string[]]$Arguments = @(),
        [string]$Executable = (Get-Process -Id $PID).Path
    )
    # The call operator sets the console window title and throws Win32 0xE9 once the launching
    # console is gone (release 46 wear walk log: two of three small-round runs died on it).
    $psi = [System.Diagnostics.ProcessStartInfo]::new($Executable)
    $psi.ArgumentList.Add('-NoProfile')
    $psi.ArgumentList.Add('-File')
    $psi.ArgumentList.Add($Script)
    foreach ($arg in $Arguments) { $psi.ArgumentList.Add([string]$arg) }
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8

    $process = $null
    try { $process = [System.Diagnostics.Process]::Start($psi) }
    catch {
        return [pscustomobject]@{ Exit = -1; Output = "child process did not start: $($_.Exception.Message)" }
    }
    if ($null -eq $process) {
        return [pscustomobject]@{ Exit = -1; Output = 'child process did not start: Process.Start returned no process' }
    }

    # Both streams are read concurrently, one pending line read each, so a child filling one pipe
    # cannot block while the parent waits on the other; lines are appended as each read completes.
    $lines = [System.Collections.Generic.List[string]]::new()
    try {
        $outTask = $process.StandardOutput.ReadLineAsync()
        $errTask = $process.StandardError.ReadLineAsync()
        while ($outTask -or $errTask) {
            $pending = [System.Threading.Tasks.Task[]]@(@($outTask, $errTask) | Where-Object { $_ })
            $done = $pending[[System.Threading.Tasks.Task]::WaitAny($pending)]
            $line = $done.GetAwaiter().GetResult()
            $isOut = [object]::ReferenceEquals($done, $outTask)
            if ($null -eq $line) {
                if ($isOut) { $outTask = $null } else { $errTask = $null }
                continue
            }
            $lines.Add($line)
            if ($isOut) { $outTask = $process.StandardOutput.ReadLineAsync() }
            else { $errTask = $process.StandardError.ReadLineAsync() }
        }
        $process.WaitForExit()
        return [pscustomobject]@{ Exit = $process.ExitCode; Output = ($lines -join "`n") }
    }
    finally { $process.Dispose() }
}
