#requires -Version 7.0
<#
.SYNOPSIS
    `.\a.ps1 r0` - the release queue for the one MONO agent (S3158): a fresh process per ticket, each
    running `/spec-all -m <id>`.

.DESCRIPTION
    MONO is the owner's declaration that exactly one agent works on the project, so the chain runs
    the MONO start once and then hands over to the ordinary queue runner as instance `mono`. Nothing
    here looks for another runner or waits for anyone: the declaration is that there is nobody, and
    that check is the bureaucracy the mode removes. Each child still runs the start step itself, which
    drops whatever the previous child left behind.

    The runner is the canon harness's (CLAUDE.md section 8), reached through its forwarder. Every
    extra argument goes to it unchanged, so `.\a.ps1 r0 -MaxTickets 5 -TimeoutMinutes 45` behaves as
    it does on r1. The start is skipped for the runner's -DryRun, -Stop, -Kill and -Help, which begin
    no work and must not drop anybody's state.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/run-mono-queue.ps1 -MaxTickets 3

Exit codes (CLAUDE.md Rule 7):
  2  the MONO start could not clear a store (mono-mode.ps1's own code), or the runner forwarder
     could not locate the harness.
  otherwise  the queue runner's own exit code.
#>

$ErrorActionPreference = 'Stop'

# Simple-script mode on purpose, as in a.ps1: $args carries -flag tokens through untouched, which a
# declared parameter set would reject before they reached the runner.
$forwarded = @($args | ForEach-Object { "$_" })

$pwshExe = (Get-Process -Id $PID).Path
$startScript = Join-Path $PSScriptRoot 'mono-mode.ps1'
$runnerScript = Join-Path $PSScriptRoot 'run-spec-queue.ps1'

$noWork = @($forwarded | Where-Object { $_ -match '^-(DryRun|Stop|Kill|Help)$' }).Count -gt 0
if (-not $noWork) {
    & $pwshExe -NoProfile -File $startScript -Verb Start -Note 'MONO start - r0 runner'
    if ($LASTEXITCODE -ne 0) {
        Write-Host "run-mono-queue: the MONO start failed (exit $LASTEXITCODE) - the queue was not started." -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

& $pwshExe -NoProfile -File $runnerScript -Instance mono -PromptTemplate '/spec-all -m {id}' @forwarded
exit $LASTEXITCODE
