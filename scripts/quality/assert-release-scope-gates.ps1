#requires -Version 7.0
<#
.SYNOPSIS
    S1939: run the RELEASE-SCOPE quality gates in ONE process over the whole tree.

.DESCRIPTION
    The third gate runner. Two already existed - post-change.ps1 judges one ticket's changed
    set on every closure, assert-fast-gates.ps1 judges the tree on demand. Neither fits a gate
    whose subject is the state of the whole repository or a shipped artifact: applied per
    changed file such a gate cannot attribute its finding to the change in front of it, so it
    either fails on another session's work in flight or is demoted to advisory and stops
    meaning anything. Measured 2026-08-22: three such gates produced 68 of the 191 red lines
    across 53 fast-gates runs, and device-profile-matrix cost 33 minutes of closure time over
    a month to report a single finding.

    Placement test (CLAUDE.md Rule 33). A gate belongs HERE when all four hold:
      1. Between releases the defect cannot reach a user.
      2. Its subject is the whole tree or a shipped artifact, not the changed file.
      3. The finding names its own location - no attribution to a ticket is needed.
      4. Fixing the batch is no more expensive than fixing it per ticket.
    It stays per-ticket when any one of these holds: later work builds on the defect
    (compilation, resource linking, a migration, a cross-module contract); the evidence exists
    only at the moment of the change (author's intent, a ticket status a probe is bound to);
    or agents read the artifact between releases, where staleness poisons decisions (S1392).

    This runner is a SCRIPT with an exit code on purpose, not a list of calls in the
    /spec-prerelease markdown. Measured in the 2026-07-31 process audit: gated rules hold at
    ~99%, rules stated as prose at 1-8%. Moving a gate into prose changes its force, not its
    stage - which would have made the whole relocation a downgrade (S1939 ADR-1).

    Gates (in order):
      - assert-gate-timing-claims      (S2453 documented run times vs the gate telemetry journal)
      - assert-play-listing-locales    (S2340 Play listing locales vs locales_config.xml)
      - assert-unreferenced-strings    (S1568 string keys nothing under <module>/src references)
      - assert-splash-brand-sync       (S1706 generated splash drawables vs strings and template)
      - assert-icon-inventory-sync     (S0815 icon docs vs the settings icon/title sources)
      - assert-doc-icons-sync          (S0889 doc icon assets vs their inventory)
      - assert-device-profile-matrix   (S1216 device matrix, registry and applier agreement)
      - assert-source-gates            (S2110 every lexical ratchet baseline, over the whole tree)
      - run-script-suites              (S2122 every *.tests/Run-Tests.ps1 suite in the repository)
      - assert-suite-tracked           (S2411 every discovered suite runner is in the git index)

    Deliberately NOT moved here: assert-oss-notices. It ships inside the package, so criteria 1
    and 2 hold - but its own wiring comment records that both of its findings ARE attributable to
    the change that fired them (a coordinate this change declared, a page this change edited), and
    it executes 8 times a month. Criterion 3 fails, so it stays per-ticket. Applying the test
    honestly matters more than the size of the moved set.

    Deliberately NOT absorbed: assert-new-lexemes-translated, assert-guide-coverage,
    assert-no-orphan-merged-resources and assert-deobfuscation-retained already run from
    /spec-prerelease with their own surrounding flow (a bulk locale import loop, an advisory
    report, a previous-release artifact comparison). Folding them in would hide those flows.

    Each child runs as its own process so a child `exit` cannot kill this aggregator, and each
    outcome is appended to the gate journal under runner 'assert-release-scope-gates'.

.PARAMETER Json
    Emit the per-gate result set as JSON instead of the human table.

.PARAMETER ReuseFinding
    Opt-in finding reuse (S2409): when an alive finding for gates:release-scope written by this session
    exists, answer PASS immediately without re-running children.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-release-scope-gates.ps1

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every gate passed (or reused this session's own green run under -ReuseFinding).
      1  at least one gate found a defect. The release does not ship until it is fixed.
      2  cannot verify - a gate script is missing from scripts/quality/.
#>
[CmdletBinding()]
param(
    [switch]$Json,
    [switch]$Help,
    [switch]$ReuseFinding
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/gate-telemetry.ps1')

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

if ($ReuseFinding) {
    try {
        $chatStore = Join-Path $PSScriptRoot '../utils/agent-chat-store.ps1'
        if (Test-Path -LiteralPath $chatStore) {
            . $chatStore
            $match = Get-AgentChatCoveringFinding -Topic 'gates:release-scope' -Request 'assert-release-scope-gates.ps1' -OwnAgentOnly
            if ($null -ne $match) {
                $agentObj = Get-AgentChatProp $match 'agent'
                $authorName = [string](Get-AgentChatProp $agentObj 'name' ([string](Get-AgentChatProp $agentObj 'id' '?')))
                $atUtc = [DateTime](Get-AgentChatProp $match 'atUtc' ([DateTime]::UtcNow))
                $ageMin = [double]([DateTime]::UtcNow - $atUtc).TotalMinutes
                $ageStr = Format-AgentChatAge $ageMin

                if ($Json) {
                    [ordered]@{
                        status     = 'pass'
                        reused     = $true
                        reusedFrom = "$authorName ($ageStr ago)"
                        gates      = @()
                    } | ConvertTo-Json -Depth 4
                } else {
                    Write-Host "assert-release-scope-gates: PASS (reused from $authorName, $ageStr ago - release scope clean)." -ForegroundColor Green
                }
                exit 0
            }
        }
    } catch { }
}

# S2453: the batch's own wall clock. Started AFTER the -ReuseFinding exit above, because that
# path runs no gate at all - journalling its microseconds as a run of this batch would drag the
# median of the very figure the timing gate judges toward zero.
$batchStopwatch = [System.Diagnostics.Stopwatch]::StartNew()

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    'pwsh'
}

# name -> extra args (beyond -Gate). Cheapest first, so a missing script surfaces early.
$gates = [ordered]@{
    # S2453. Judges the run times documented in prose against the telemetry journal both batch
    # runners already write. Rule 33 puts it here on all four criteria: a stale figure reaches no
    # user at all, only an agent choosing foreground or background; its subject is a document
    # against months of accumulated journal, which no changed file can be blamed for; each finding
    # names its own claim id and both numbers; and re-measuring a target costs the same whenever it
    # is done. Cheapest member by a wide margin - one regex per claim over one document, plus one
    # pass of the journal - so it goes first and a reworded row surfaces before the slow gates run.
    # Not passed -Quiet: which claim drifted, and by how much, is the whole content of its report.
    'assert-gate-timing-claims.ps1'    = @()
    # S2340. Reads two declarations - locales_config.xml and the LOCALES dict in
    # publish-play-listing.py - plus 39 small text files, so it is the cheapest member and goes first.
    # Rule 33 puts it in release scope on all four criteria (strategic S2340 "Гейт"): the listing
    # reaches a user only when the owner publishes it; its subject is the whole listing tree against
    # the whole locale declaration; each finding names its own locale and folder; and adding the
    # missing locales is one batch either way. Deliberately NOT passed -Quiet - that switch suppresses
    # the per-violation lines, and "which locale" is the whole content of this gate's report.
    'assert-play-listing-locales.ps1'  = @()
    'assert-unreferenced-strings.ps1'  = @('-Quiet')
    'assert-splash-brand-sync.ps1'     = @('-Quiet')
    'assert-icon-inventory-sync.ps1'   = @()
    'assert-doc-icons-sync.ps1'        = @()
    'assert-device-profile-matrix.ps1' = @('-Quiet')
    # S2110. Every lexical ratchet baseline, judged over the WHOLE tree. Rule 33 puts it here on
    # all four criteria (strategic S2110 section 6.3): a hardcoded dp breaks nothing at runtime, so
    # between releases it cannot reach a user; its subject is the tree, not a changed file; -Explain
    # makes the finding name its own files; and batch conversion costs no more than per-ticket.
    # It stays in assert-fast-gates.ps1 as well, and that is NOT duplication (ADR-2): there the
    # runner is handed -ChangedFiles and judges the named set, here it never is and always judges
    # the tree. Deleting either entry is what made the baselines nominal - measured 2026-08-27,
    # layout-hardcoded-dimens sat 6 above its baseline in committed HEAD with every closure green,
    # because a file no author named is judged by neither mode.
    'assert-source-gates.ps1'          = @()
    # S2122. The full sweep of every *.tests/Run-Tests.ps1 in the repository. Last on purpose: the
    # table is ordered cheapest first and this is the most expensive member by a wide margin - the
    # 37-suite sweep measured 214.8 s on 2026-08-27, against the 120 s foreground budget.
    #
    # Rule 33 puts it here on three grounds at once. It is attributable to no changed file, since it
    # runs everything; it exceeds the foreground budget the per-ticket closure must stay inside; and
    # it can fail on a sibling session's in-flight work, which is the accepted price of the release
    # scope and intolerable in a ticket close. The per-ticket half lives in post-change.ps1 as
    # `script-suite-regression`, where the changed set selects only the neighbouring suites.
    #
    # -Gate is what makes "could not verify" fatal here. The runner returns 2 when a suite could not
    # run for want of an environment tool; the closure calls it without -Gate and treats that as
    # advisory, because a developer machine missing rg must still be able to close a ticket. Before a
    # release the environment must be complete, and the loop below collapses every non-zero code to
    # FAIL - so the inversion needs no second code path here, only the switch.
    # S2411. Asks git whether every discovered *.tests/Run-Tests.ps1 is in the index. Placed here on
    # all four Rule 33 criteria: an untracked suite reaches no user between releases, its subject is
    # the whole tree, each finding prints its own path and the `git add` that clears it, and staging
    # eight paths costs one command either way. The per-ticket half in post-change.ps1 (`suite-tracked`)
    # judges only a runner the changed set names - it catches the defect at birth, this catches what
    # already accumulated, including suites belonging to sessions that have long since ended.
    #
    # Runs after the sweep above rather than before it: both read the same discovery, and a sweep that
    # went red is the more urgent report of the two.
    'run-script-suites.ps1'            = @('-Quiet')
    'assert-suite-tracked.ps1'         = @()
}

$results = [System.Collections.Generic.List[object]]::new()
$missing = 0
foreach ($entry in $gates.GetEnumerator()) {
    $path = Join-Path $PSScriptRoot $entry.Key
    if (-not (Test-Path $path)) {
        $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = 'MISSING'; Ms = 0 })
        Write-GateTelemetryRecord -Runner 'assert-release-scope-gates' -Gate $entry.Key `
            -Status 'MISSING' -ExitCode 2 -ElapsedMs 0
        $missing++
        continue
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $pwshExe -NoProfile -File $path -Gate @($entry.Value) | Write-Host
    $sw.Stop()
    $status = ($LASTEXITCODE -eq 0) ? 'PASS' : 'FAIL'
    $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = $status; Ms = [int]$sw.Elapsed.TotalMilliseconds })
    Write-GateTelemetryRecord -Runner 'assert-release-scope-gates' -Gate $entry.Key `
        -Status $status -ExitCode ([int]$LASTEXITCODE) -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
}

if ($Json) {
    [ordered]@{
        status = if ($missing -gt 0) { 'cannot-verify' } elseif (@($results | Where-Object { $_.Status -ne 'PASS' }).Count -gt 0) { 'fail' } else { 'pass' }
        gates  = $results
    } | ConvertTo-Json -Depth 4
}
else {
    Write-Host ''
    Write-Host 'assert-release-scope-gates summary:' -ForegroundColor Cyan
    foreach ($r in $results) {
        $color = switch ($r.Status) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
        Write-Host ("  {0,-40} {1} ({2} ms)" -f $r.Gate, $r.Status, $r.Ms) -ForegroundColor $color
    }
}

$batchStopwatch.Stop()
$batchMs = [int]$batchStopwatch.Elapsed.TotalMilliseconds

if ($missing -gt 0) {
    Write-GateBatchTelemetryRecord -Runner 'assert-release-scope-gates' -ExitCode 2 -ElapsedMs $batchMs
    Write-Error "assert-release-scope-gates: CANNOT VERIFY - $missing gate script(s) absent." -ErrorAction Continue
    exit 2
}

$failed = @($results | Where-Object { $_.Status -ne 'PASS' }).Count
if ($failed -gt 0) {
    Write-GateBatchTelemetryRecord -Runner 'assert-release-scope-gates' -ExitCode 1 -ElapsedMs $batchMs
    $names = (@($results | Where-Object { $_.Status -ne 'PASS' } | ForEach-Object { $_.Gate }) -join ', ')
    Write-Error ("assert-release-scope-gates: FAIL - $failed gate(s) found a defect in the release scope: " +
        "$names. Each printed its own remediation above; fix and re-run until this exits 0. " +
        'The release does not ship on a red scope.') -ErrorAction Continue
    exit 1
}

# S2409: post a best-effort finding on a clean run
try {
    $chatStore = Join-Path $PSScriptRoot '../utils/agent-chat-store.ps1'
    if (Test-Path -LiteralPath $chatStore) {
        . $chatStore
        $candidateScopes = @('app_v2/src', 'wear/src', 'scripts', 'docs', 'fastlane', 'store_assets')
        $repoRoot = Join-Path $PSScriptRoot '../..'
        $scopeList = @()
        foreach ($cs in $candidateScopes) {
            if (Test-Path -LiteralPath (Join-Path $repoRoot $cs)) {
                $scopeList += $cs
            }
        }
        [void](New-AgentChatMessage -Stream finding -Kind check -Topic 'gates:release-scope' -TtlMinutes 1440 -Note 'assert-release-scope-gates passed (release scope clean)' -EvidenceCommand 'assert-release-scope-gates.ps1' -EvidenceExit 0 -Scope $scopeList)
    }
} catch { }

Write-GateBatchTelemetryRecord -Runner 'assert-release-scope-gates' -ExitCode 0 -ElapsedMs $batchMs
Write-Host 'assert-release-scope-gates: PASS (release scope clean).' -ForegroundColor Green
exit 0
