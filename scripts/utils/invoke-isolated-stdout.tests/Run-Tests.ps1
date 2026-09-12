#requires -Version 7.0
<#
.SYNOPSIS
    S2412 - contract suite for the stdout-isolation runner and the a.ps1 routing that reaches it.

.DESCRIPTION
    Subject: scripts/utils/invoke-isolated-stdout.ps1, a.ps1

    HERMETIC. Every case runs a throw-away child script under temp/S2412/tests-scratch and asserts
    on what the runner returned. Nothing here starts Gradle: the defect is about a file handle, not
    about a build, and a suite that needed a real daemon could not run inside a closure.

    THE RUNNER IS ALWAYS DRIVEN AS A CHILD PROCESS, never with `& $helper` in this session. It
    writes through [Console]::Out so its output reaches the process stdout HANDLE - which is the
    whole point, since that handle is what a.ps1's caller reads - and PowerShell's own capture and
    redirection operators cannot see that stream at all. Asserting on `& $helper` output would
    therefore fail against a perfectly working runner, which is exactly what the first draft of
    this suite did.

    Pinned, each because losing it silently either restores the hang or hides a build's verdict:
      - the child's exit code reaches the caller, zero and non-zero alike. This runner now sits
        between a.ps1 and every gradle-backed target, so a code it swallowed would turn a red build
        green everywhere at once.
      - stdout arrives in order, including the line written last before exit. That tail is what a
        poll loop drops when it stops at HasExited and never reads again.
      - output spanning several poll intervals is not truncated.
      - stderr reaches the caller's stderr and is not merged into stdout.
      - an argument containing a space survives. It did not in the first draft: Start-Process joins
        an argument array with plain spaces and quotes nothing, so `has space` arrived as `has`.
      - a child that leaves a survivor behind does NOT hold the caller.
      - the same with the caller's stdout on a real PIPE and a survivor started with redirection -
        the defect itself, and the only case that can see it. The case above cannot: it gives the
        runner a file for stdout, so it passed for a whole revision against a runner that still
        hung a piped caller for as long as the daemon lived.
      - a.ps1 still recognises a gradle-backed target from its text, which is the condition that
        decides whether any of the above is reached at all.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/invoke-isolated-stdout.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
      2 - could not verify - the runner script is missing.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$helper = Join-Path $repoRoot 'scripts\utils\invoke-isolated-stdout.ps1'
if (-not (Test-Path -LiteralPath $helper)) {
    Write-Error "invoke-isolated-stdout tests: cannot verify - the runner is missing at $helper." -ErrorAction Continue
    exit 2
}

$scratch = Join-Path $repoRoot 'temp\S2412\tests-scratch'
if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch -Recurse -Force }
New-Item -ItemType Directory -Path $scratch -Force | Out-Null

$pwshExe = (Get-Process -Id $PID).Path
$caseIndex = 0
$failures = 0

function Assert-That {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
    }
    else {
        Write-Host "  FAIL  $Name $Detail" -ForegroundColor Red
        $script:failures++
    }
}

function New-ChildScript {
    param([string]$Name, [string]$Body)
    $path = Join-Path $scratch $Name
    Set-Content -LiteralPath $path -Value $Body -Encoding utf8
    return $path
}

function Invoke-Runner {
    param([string]$ChildScript, [string[]]$ChildArguments = @(), [string]$RunnerWorkingDirectory)

    $script:caseIndex++
    $id = '{0:d2}' -f $script:caseIndex
    $outFile = Join-Path $scratch "capture-$id.out"
    $errFile = Join-Path $scratch "capture-$id.err"

    # The call is written into a wrapper script rather than passed on a command line: `pwsh -File`
    # binds a comma list as ONE element, so -Arguments could not survive the crossing, and
    # `pwsh -Command` would need its own quoting layer around the one being tested.
    $call = @("& '$helper' -ScriptPath '$ChildScript' -LogDirectory '$scratch'")
    if ($ChildArguments.Count -gt 0) {
        $literals = ($ChildArguments | ForEach-Object { "'" + ($_ -replace "'", "''") + "'" }) -join ','
        $call += "-Arguments @($literals)"
    }
    if ($RunnerWorkingDirectory) { $call += "-WorkingDirectory '$RunnerWorkingDirectory'" }
    $wrapper = Join-Path $scratch "wrapper-$id.ps1"
    Set-Content -LiteralPath $wrapper -Value (($call -join ' ') + "`nexit `$LASTEXITCODE") -Encoding utf8

    # -PassThru plus an explicit WaitForExit, never -Wait: PowerShell's -Wait puts the process in a
    # job object and waits for every process in it, so the survivor case would measure the harness
    # rather than the runner - it reported 25.6 s against a runner that had already returned.
    $proc = Start-Process -FilePath $pwshExe -ArgumentList "-NoProfile -File `"$wrapper`"" `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile `
        -NoNewWindow -PassThru
    $proc.WaitForExit()

    return [pscustomobject]@{
        ExitCode = $proc.ExitCode
        StdOut   = [string](Get-Content -LiteralPath $outFile -Raw -ErrorAction SilentlyContinue)
        StdErr   = [string](Get-Content -LiteralPath $errFile -Raw -ErrorAction SilentlyContinue)
    }
}

Write-Host 'invoke-isolated-stdout contract suite' -ForegroundColor Cyan

try {
    # --- exit code passthrough ---------------------------------------------------------------
    $zero = New-ChildScript 'exit-zero.ps1' "Write-Host 'ok'`nexit 0"
    $r = Invoke-Runner -ChildScript $zero
    Assert-That 'exit 0 is passed through' ($r.ExitCode -eq 0) "got $($r.ExitCode)"

    $seven = New-ChildScript 'exit-seven.ps1' "Write-Host 'nope'`nexit 7"
    $r = Invoke-Runner -ChildScript $seven
    Assert-That 'a non-zero exit code is passed through' ($r.ExitCode -eq 7) "got $($r.ExitCode)"

    # --- output fidelity ---------------------------------------------------------------------
    $lines = New-ChildScript 'many-lines.ps1' "1..500 | ForEach-Object { Write-Host `"line-`$_`" }`nWrite-Host 'LAST-LINE'`nexit 0"
    $r = Invoke-Runner -ChildScript $lines
    Assert-That 'the first line survives' ($r.StdOut -match 'line-1\b')
    Assert-That 'the 500th line survives' ($r.StdOut -match 'line-500\b')
    Assert-That 'the line written last before exit survives' ($r.StdOut -match 'LAST-LINE')
    Assert-That 'order is preserved' ($r.StdOut.IndexOf('line-1') -ge 0 -and $r.StdOut.IndexOf('LAST-LINE') -gt $r.StdOut.IndexOf('line-1'))

    $slow = New-ChildScript 'slow-writer.ps1' "1..4 | ForEach-Object { Write-Host `"chunk-`$_`"; Start-Sleep -Milliseconds 300 }`nWrite-Host 'TAIL-AFTER-SLEEP'`nexit 0"
    $r = Invoke-Runner -ChildScript $slow
    Assert-That 'output spanning several polls keeps its tail' ($r.StdOut -match 'TAIL-AFTER-SLEEP' -and $r.StdOut -match 'chunk-1') "got '$($r.StdOut)'"

    # --- stderr ------------------------------------------------------------------------------
    $errScript = New-ChildScript 'writes-stderr.ps1' "[Console]::Error.WriteLine('CHILD-STDERR')`nWrite-Host 'CHILD-STDOUT'`nexit 0"
    $r = Invoke-Runner -ChildScript $errScript
    Assert-That 'child stderr reaches the caller stderr' ($r.StdErr -match 'CHILD-STDERR') "got '$($r.StdErr)'"
    Assert-That 'child stderr is not merged into stdout' ($r.StdOut -notmatch 'CHILD-STDERR' -and $r.StdOut -match 'CHILD-STDOUT') "got '$($r.StdOut)'"

    # --- arguments ---------------------------------------------------------------------------
    $argScript = New-ChildScript 'echo-args.ps1' "param([string]`$Alpha, [switch]`$Beta)`nWrite-Host `"alpha=`$Alpha beta=`$(`$Beta.IsPresent)`"`nexit 0"
    $r = Invoke-Runner -ChildScript $argScript -ChildArguments @('-Alpha', 'has space', '-Beta')
    Assert-That 'an argument containing a space survives' ($r.StdOut -match 'alpha=has space beta=True') "got '$($r.StdOut)'"

    # --- working directory -------------------------------------------------------------------
    $cwdScript = New-ChildScript 'echo-cwd.ps1' "Write-Host `"cwd=`$((Get-Location).Path)`"`nexit 0"
    $r = Invoke-Runner -ChildScript $cwdScript -RunnerWorkingDirectory $scratch
    Assert-That 'the requested working directory is honoured' ($r.StdOut -match [regex]::Escape($scratch)) "got '$($r.StdOut)'"

    # --- the defect itself -------------------------------------------------------------------
    # A child that leaves behind a process outliving it. Through the runner the caller must still
    # return promptly: the survivor inherited files, so the caller's own pipe reaches EOF anyway.
    $orphanBody = @"
Start-Process -FilePath (Get-Process -Id `$PID).Path ``
    -ArgumentList '-NoProfile','-Command','Start-Sleep -Seconds 25' -NoNewWindow | Out-Null
Write-Host 'parent-done'
exit 0
"@
    $orphan = New-ChildScript 'leaves-orphan.ps1' $orphanBody
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $r = Invoke-Runner -ChildScript $orphan
    $sw.Stop()
    Assert-That 'a child that leaves a survivor does not hold the caller' ($sw.Elapsed.TotalSeconds -lt 15) "took $([math]::Round($sw.Elapsed.TotalSeconds, 1))s"
    Assert-That 'the survivor case still reports its own output' ($r.StdOut -match 'parent-done') "got '$($r.StdOut)'"

    # --- the defect under its REAL condition -------------------------------------------------
    # The case above cannot see the defect and passed against a runner that still had it: it starts
    # the runner with -RedirectStandardOutput, so the caller's stdout is a FILE and the pipe whose
    # EOF never arrives does not exist. The measured shape needs two things the case above lacks -
    # the caller's stdout must be a real PIPE, and the survivor must be started WITH redirection,
    # because that is what makes Windows hand it every inheritable handle its parent owns (a
    # survivor started without redirection inherits nothing, which is why a synthetic orphan was
    # once measured at 1 s and wrongly cleared the mechanism).
    #
    # cmd builds the pipe, and `sort` on its far end returns only at EOF, so the .cmd exits when
    # the pipe closes rather than when the runner does - which is exactly the gap being measured.
    $survivorSeconds = 25
    $inheritingOrphanBody = @"
Start-Process -FilePath (Get-Process -Id `$PID).Path ``
    -ArgumentList '-NoProfile','-Command','Start-Sleep -Seconds $survivorSeconds' ``
    -RedirectStandardOutput '$scratch\survivor.out' -RedirectStandardError '$scratch\survivor.err' ``
    -NoNewWindow | Out-Null
Write-Host 'parent-done'
exit 0
"@
    $inheritingOrphan = New-ChildScript 'leaves-inheriting-orphan.ps1' $inheritingOrphanBody

    $script:caseIndex++
    $pipeId = '{0:d2}' -f $script:caseIndex
    $pipeWrapper = Join-Path $scratch "wrapper-$pipeId.ps1"
    Set-Content -LiteralPath $pipeWrapper `
        -Value "& '$helper' -ScriptPath '$inheritingOrphan' -LogDirectory '$scratch'`nexit `$LASTEXITCODE" `
        -Encoding utf8
    $pipeSink = Join-Path $scratch "capture-$pipeId.out"
    $pipeCmd = Join-Path $scratch "pipe-case-$pipeId.cmd"
    Set-Content -LiteralPath $pipeCmd -Value @"
@echo off
"$pwshExe" -NoProfile -File "$pipeWrapper" | sort > "$pipeSink"
"@ -Encoding ascii

    $sw = [Diagnostics.Stopwatch]::StartNew()
    $pipeProc = Start-Process -FilePath $pipeCmd -NoNewWindow -PassThru
    # Bounded, so a regression fails the case instead of hanging the suite for the survivor's life.
    $returned = $pipeProc.WaitForExit(($survivorSeconds - 5) * 1000)
    $sw.Stop()
    if (-not $returned) { Stop-Process -Id $pipeProc.Id -Force -ErrorAction SilentlyContinue }
    Assert-That 'a survivor that inherited handles does not hold a PIPED caller' `
        $returned "the pipe was still open $([math]::Round($sw.Elapsed.TotalSeconds, 1))s after the runner returned"
    $pipedOut = [string](Get-Content -LiteralPath $pipeSink -Raw -ErrorAction SilentlyContinue)
    Assert-That 'the piped case still delivers the output' ($pipedOut -match 'parent-done') "got '$pipedOut'"

    # --- a.ps1 routing -----------------------------------------------------------------------
    # The runner above is only reached when a.ps1 decides the target is gradle-backed, and that
    # decision is read from the target's own text. So it is asserted against REAL repository
    # scripts: a rename that broke the match would otherwise pass here and hang there.
    $launcherText = Get-Content -LiteralPath (Join-Path $repoRoot 'a.ps1') -Raw
    Assert-That 'a.ps1 routes gradle-backed targets through the runner' ($launcherText -match 'invoke-isolated-stdout\.ps1')
    Assert-That 'a.ps1 isolates only when its own stdout is redirected' ($launcherText -match 'IsOutputRedirected')

    $gradleBacked = @(
        'scripts\builders\check-standard-fast.ps1',
        'scripts\builders\build-debug.PS1',
        'scripts\builders\build-aab-release.ps1'
    )
    foreach ($rel in $gradleBacked) {
        $full = Join-Path $repoRoot $rel
        $body = if (Test-Path -LiteralPath $full) { [string](Get-Content -LiteralPath $full -Raw) } else { '' }
        Assert-That "$rel still reads as gradle-backed" ($body -match 'gradlew' -or $body -match 'Enter-BuildLockOrExit')
    }

    $chatPath = Join-Path $repoRoot 'scripts\utils\agent-chat.ps1'
    $chatBody = if (Test-Path -LiteralPath $chatPath) { [string](Get-Content -LiteralPath $chatPath -Raw) } else { 'gradlew' }
    Assert-That 'a non-build target does not read as gradle-backed' ($chatBody -notmatch 'gradlew' -and $chatBody -notmatch 'Enter-BuildLockOrExit')
}
finally {
    if (Test-Path -LiteralPath $scratch) {
        Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ''
if ($failures -eq 0) {
    Write-Host 'invoke-isolated-stdout: ALL PASS' -ForegroundColor Green
    exit 0
}
Write-Host "invoke-isolated-stdout: $failures FAILED" -ForegroundColor Red
exit 1
