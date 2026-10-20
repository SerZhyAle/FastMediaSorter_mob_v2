#requires -Version 7.0
<#
.SYNOPSIS
    Advisory closing-gate (S3141): judge the Codex session transcript that authored the given
    ticket against the bounded-read protocol, and flag it if it did not follow one.

.DESCRIPTION
    Calls scripts/quality/measure-codex-transcript.ps1 -Id <Sxxxx> -Json and judges the result:
      - a Claude Code process - NOT APPLICABLE, exit 0. A Gemini or ZCode process (named by
        FMS_AGENT_RUNTIME) with no matching rollout is NOT APPLICABLE too.
      - no matching rollout and no runtime marker that names a non-Codex runtime - COULD NOT
        VERIFY, exit 2 (CHECK-VERDICT 0.12 item A.2): a Codex ticket whose rollout is gone looks
        the same as a ticket Codex never saw, and Codex sets no marker of its own. The closure
        facade runs this gate as an advisory step, so exit 2 surfaces there as an advisory and
        never fails a closure.
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
      0 - NOT APPLICABLE on a Claude Code process (S4130) or on a named non-Codex runtime with
          no rollout for the ticket, or applicable with zero findings.
      2 - could not verify: measure-codex-transcript.ps1 itself exited 2, or no rollout names the
          ticket on a runtime that is not identified as a non-Codex one.
      3 - applicable, and one or more findings (advisory).

    The last stdout line is the verdict: `assert-codex-transcript-hygiene: PASS (..)`,
    `: PASS WITH ADVISORIES (..)`, `: COULD NOT VERIFY (..)` or `: NOT APPLICABLE (..)`. The gate
    has no FAIL path by design (advisory-first landing), which is why its row stays in
    scripts/quality/verdict-line-baseline.txt.

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
    # S4130: CHECK-VERDICT 0.12 item A - the runtime the caller runs in selects no Codex transcript.
    Write-Host "assert-codex-transcript-hygiene: NOT APPLICABLE (Claude Code process; inherited FMS_AGENT_RUNTIME='$($env:FMS_AGENT_RUNTIME)' ignored)"
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
    Write-Host $rawJson
    Write-Host "assert-codex-transcript-hygiene: COULD NOT VERIFY (measure-codex-transcript.ps1 exited 2)" -ForegroundColor Red
    exit 2
}

$parsed = $rawJson | ConvertFrom-Json -Depth 6
if (-not $parsed.found) {
    # A named non-Codex runtime selects no Codex transcript, so nothing was requested of this gate. Any
    # other process may be Codex - which sets no marker - and then a rollout that is gone is requested
    # work that did not run (CHECK-VERDICT 0.12 item A.2). The marker is read only on this path: a stale
    # inherited value must never switch off the judging of a rollout that exists.
    if ($env:FMS_AGENT_RUNTIME -match '(?i)zcode|gemini|antigravity') {
        Write-Host "assert-codex-transcript-hygiene: NOT APPLICABLE (runtime '$($env:FMS_AGENT_RUNTIME)' is not Codex; no rollout on disk names $Id)"
        exit 0
    }
    Write-Host "assert-codex-transcript-hygiene: COULD NOT VERIFY (no rollout on disk names $Id, and this process is not identified as a non-Codex runtime)" -ForegroundColor Yellow
    exit 2
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
Write-Host "assert-codex-transcript-hygiene: PASS WITH ADVISORIES ($($findings.Count) finding(s); research/05__enforcement-without-hooks.md says what each flag means)"
exit 3
