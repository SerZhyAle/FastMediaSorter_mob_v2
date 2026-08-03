#requires -Version 7.0
<#
.SYNOPSIS
    Run an external process under a wall-clock ceiling (S1338).

.DESCRIPTION
    One `post-change.ps1` run hung for three hours and still reported PASS. A gate that never
    returns is indistinguishable from a gate that passed, unless something bounds it - so every
    gradle-backed gate now runs through here.

    The contract that matters: a timeout is "cannot verify", never PASS and never FAIL. The
    caller gets $null for the exit code and `TimedOut = $true`, and must map that to exit 2.

    Dot-source it:  . (Join-Path $repoRoot 'scripts/utils/process-timeout.ps1')

.NOTES
    Pure .NET process handling - no jobs, no runspaces, so it works the same inside a
    background job as it does at the top level.
#>

<#
.SYNOPSIS
    Start a process, wait at most -TimeoutSeconds, and return its output and exit code.

.OUTPUTS
    [pscustomobject] with:
      Output     [string]  - stdout and stderr, concatenated, whatever was produced.
      ExitCode   [int?]    - the process exit code, or $null when it was killed on timeout.
      TimedOut   [bool]    - $true when the ceiling fired.
      ElapsedMs  [int]     - wall clock actually spent.
#>
function Invoke-ProcessWithTimeout {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [AllowEmptyCollection()][string[]]$ArgumentList = @(),
        [string]$WorkingDirectory,
        # 600 s: the observed tail is 5 gate runs over 300 s out of 311, so this never fires
        # on a healthy run. Callers raise it deliberately for a genuinely long release build.
        [int]$TimeoutSeconds = 600
    )

    $stdoutFile = [System.IO.Path]::GetTempFileName()
    $stderrFile = [System.IO.Path]::GetTempFileName()
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = $null
    $timedOut = $false
    $exitCode = $null

    try {
        $startArgs = @{
            FilePath               = $FilePath
            PassThru               = $true
            NoNewWindow            = $true
            RedirectStandardOutput = $stdoutFile
            RedirectStandardError  = $stderrFile
        }
        if ($ArgumentList.Count -gt 0) { $startArgs.ArgumentList = $ArgumentList }
        if ($WorkingDirectory) { $startArgs.WorkingDirectory = $WorkingDirectory }

        $proc = Start-Process @startArgs
        if (-not $proc.WaitForExit($TimeoutSeconds * 1000)) {
            $timedOut = $true
            # Kill the tree, not just the launcher: gradlew.bat spawns java, and killing only
            # the batch file leaves the build running while the caller reports a timeout.
            try { taskkill.exe /PID $proc.Id /T /F 2>&1 | Out-Null } catch { }
            try { $proc.WaitForExit(5000) | Out-Null } catch { }
        }
        else {
            $exitCode = $proc.ExitCode
        }
    }
    finally {
        $sw.Stop()
    }

    $out = ''
    foreach ($f in @($stdoutFile, $stderrFile)) {
        if (Test-Path -LiteralPath $f) {
            $text = Get-Content -LiteralPath $f -Raw -ErrorAction SilentlyContinue
            if ($text) { $out += $text }
            Remove-Item -LiteralPath $f -Force -ErrorAction SilentlyContinue
        }
    }

    [pscustomobject]@{
        Output    = $out
        ExitCode  = $exitCode
        TimedOut  = $timedOut
        ElapsedMs = [int]$sw.Elapsed.TotalMilliseconds
    }
}
