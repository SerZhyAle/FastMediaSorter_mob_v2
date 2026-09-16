#requires -Version 7.0
<#
.SYNOPSIS
    Advisory closing-gate (S3141): judge the Codex session transcript that authored the given
    ticket against the bounded-read protocol, and flag it if it did not follow one.

.DESCRIPTION
    Calls scripts/quality/measure-codex-transcript.ps1 -Id <Sxxxx> -Json and judges the result:
      - not Codex runtime, or no matching rollout found - "not applicable", exit 0. Never fails
        a Claude Code, Gemini or ZCode closure, and never fails a Codex ticket whose session
        simply is not on disk yet (a normal state for most tickets).
      - a rollout was found - any call flagged `oversized` (its output exceeded
        codexContext.maxInlineChars inline, per Phase 01), `truncated` (a truncation marker
        followed by output the model then used), `chainedRead` (two or more reads chained
        into one exec call, so the per-call offload threshold never applied), `uncapped` (an exec call
        requesting no `max_output_tokens` or more than codexContext.maxOutputTokens), `crossTicketId` (a call named another
        ticket's id with no link), or a `sleepStreak` of 2 or more (a wait spread across
        several model turns instead of one blocking call) is a finding.
      - zero findings - PASS, exit 0.
      - one or more findings - print each with its call number, exit 3 (advisory - this gate's
        first landing; research/05 records why a mechanism, not a rule, is the point, and why
        advisory-first mirrors this repo's other first-landing gates).

    This is the enforcement half of the S3141 protocol; research/05 concludes it is the only
    mechanism that does not depend on the model choosing to comply, since Codex has no
    PreToolUse-style hook to refuse a call before it happens.

    Exit codes (S1070):
      0 - not applicable (wrong runtime, or no rollout found), or applicable with zero findings.
      2 - measure-codex-transcript.ps1 itself could not verify (its own exit 2).
      3 - applicable, and one or more findings (advisory).

.PARAMETER Id
    Ticket id (Sxxxx) being closed.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-codex-transcript-hygiene.ps1 -Id S3137
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Id
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# S3149: FMS_AGENT_RUNTIME can be inherited from a persisted environment, and a Claude Code
# process carrying 'Codex' that way was judged as a Codex session. The runtime's own markers win.
if ($env:CLAUDECODE -or $env:CLAUDE_CODE_SESSION_ID) {
    Write-Host "assert-codex-transcript-hygiene: not applicable - this is a Claude Code process (inherited FMS_AGENT_RUNTIME='$($env:FMS_AGENT_RUNTIME)' ignored)."
    exit 0
}
# S3177: no FMS_AGENT_RUNTIME test. Codex never sets it - the S3103 session closed without it, so
# the gate answered "not applicable" to a 25M-token transcript. A rollout whose first user message
# names the ticket is the authorship evidence; no rollout -> not applicable below.

$measureScript = Join-Path $PSScriptRoot 'measure-codex-transcript.ps1'
# Invoked as a real child process, not dot-sourced or `&`-called in-process: the measured
# script ends every path with its own `exit N`, which would terminate THIS process too if
# called in-process rather than as a subprocess whose exit code is merely observed.
$rawJson = & pwsh -NoProfile -File $measureScript -Id $Id -Json
$measureExit = $LASTEXITCODE

if ($measureExit -eq 2) {
    Write-Host "assert-codex-transcript-hygiene: cannot verify - measure-codex-transcript.ps1 exited 2." -ForegroundColor Red
    Write-Host $rawJson
    exit 2
}

$parsed = $rawJson | ConvertFrom-Json -Depth 6
if (-not $parsed.found) {
    Write-Host "assert-codex-transcript-hygiene: not applicable - no rollout on disk names $Id."
    exit 0
}

$calls = @($parsed.calls)
$findings = @($calls | Where-Object {
    $_.uncapped -or $_.oversized -or $_.truncated -or $_.chainedRead -or $_.crossTicketId -or ($_.sleepPoll -and $_.sleepStreak -ge 2)
})

if ($findings.Count -eq 0) {
    Write-Host "assert-codex-transcript-hygiene: PASS - $Id`'s transcript ($($parsed.path)) carries no bounded-read violation across $($calls.Count) call(s)."
    exit 0
}

Write-Host "assert-codex-transcript-hygiene: $($findings.Count) finding(s) in $($parsed.path):" -ForegroundColor Yellow
foreach ($f in $findings) {
    $tags = [System.Collections.Generic.List[string]]::new()
    if ($f.uncapped) { $tags.Add("uncapped (max_output_tokens $($f.maxOutputTokens))") }
    if ($f.oversized) { $tags.Add("oversized ($($f.chars) chars)") }
    if ($f.truncated) { $tags.Add('truncated-output-used-as-fact') }
    if ($f.chainedRead) { $tags.Add("chained-read: $($f.readOps) reads in one call") }
    if ($f.crossTicketId) { $tags.Add("cross-ticket:$($f.crossTicketId)") }
    if ($f.sleepPoll -and $f.sleepStreak -ge 2) { $tags.Add("sleep-poll: Start-Sleep or timed Wait-* call, streak $($f.sleepStreak)") }
    Write-Host "  call $($f.n): $($tags -join ', ')"
}
Write-Host "assert-codex-transcript-hygiene: advisory - see research/05__enforcement-without-hooks.md for what each flag means."
exit 3
