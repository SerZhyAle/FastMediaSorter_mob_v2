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
      - assert-play-listing-graphics   (S2597 declared single images have a source and one artwork)
      - assert-play-listing-screenshot-geometry (S2602 caption band under 20%, one shape per
                                        carousel; S2764 no transparency, no wear device frame)
      - assert-delivery-size-estimates (S2652 compiled download sizes vs the published assets)
      - assert-unreferenced-strings    (S1568 string keys nothing under <module>/src references)
      - assert-splash-brand-sync       (S1706 generated splash drawables vs strings and template)
      - assert-icon-inventory-sync     (S0815 icon docs vs the settings icon/title sources)
      - assert-doc-icons-sync          (S0889 doc icon assets vs their inventory)
      - assert-device-profile-matrix   (S1216 device matrix, registry and applier agreement)
      - assert-archive-artefacts       (S2592 every spec archive record vs the file it points at)
      - assert-source-gates            (S2110 every lexical ratchet baseline, over the whole tree)
      - run-script-suites              (S2122 every *.tests/Run-Tests.ps1 suite in the repository)
      - assert-suite-tracked           (S2411 every discovered suite runner is in the git index)
      - assert-dotsource-tracked       (S2616 every dot-sourced script target is in the git index)
      - assert-document-registry-coverage (S2618 every directory holding documents is registered or excused)
      - assert-temp-root-inventory     (S3030 every top-level entry of temp/ is declared or ticket-bound)
      - assert-wear-store-boundary     (S3178 both Wear merged manifests vs the store boundary policy)

    Where every gate belongs, and who decided it: scripts/quality/gate-placement.jsonl (S2870).
    That registry replaced the two paragraphs that used to stand here naming the gate deliberately
    NOT moved into this runner and the four deliberately NOT absorbed into it. Those decisions were
    right and are preserved verbatim in the registry, each with its ticket, date and the criterion
    that decided it - but as prose they could not be queried and could not be checked, so nothing
    noticed if a gate drifted back. Applying the test honestly still matters more than the size of
    the moved set; the registry is what makes the answer readable and enforceable.
    assert-gate-placement.ps1 fails the closure when the registry and the runners disagree.

    Each child runs as its own process so a child `exit` cannot kill this aggregator, and each
    outcome is appended to the gate journal under runner 'assert-release-scope-gates'.

.PARAMETER Json
    Emit the per-gate result set as JSON instead of the human table.

.PARAMETER ReuseFinding
    Opt-in finding reuse (S2409): when an alive finding for gates:release-scope written by this session
    exists, answer PASS immediately without re-running children.

.PARAMETER OnlyGroups
    S3010. Restrict the run to the gates that read at least one of the named fingerprint input
    groups, so a sweep's second pass re-runs only what the tree actually moved under. A gate absent
    from the mapping below is ALWAYS selected: the safe default is to re-measure, never to assume
    unchanged, because a gate wrongly skipped certifies a release scope nobody judged. This does not
    scope any gate's own subject - each one still measures the whole tree when it runs (Rule 33).

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-release-scope-gates.ps1

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every gate passed (or reused this session's own green run under -ReuseFinding).
      1  at least one gate found a defect. The release does not ship until it is fixed.
      2  cannot verify - a gate script is missing from scripts/quality/.
      Under -OnlyGroups the codes are unchanged; a gate that did not run is neither PASS nor FAIL and
      is reported as SKIPPED.
#>
[CmdletBinding()]
param(
    [switch]$Json,
    [switch]$Help,
    [switch]$ReuseFinding,

    # S3010. Run only the gates that read at least one of these input groups, as named by
    # scripts/quality/release-scope-fingerprint.ps1. Omitted - the default and the only shape any
    # existing call site uses - runs every gate exactly as before.
    [string[]]$OnlyGroups = @()
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
    # is done. First not by cost but by subject: it judges the run times this very table's members
    # are documented to have, so a reworded row surfaces before the slow gates run. Its own cost is
    # modest rather than least - measured 4896 ms, against 373 for the locale gate below it.
    # Not passed -Quiet: which claim drifted, and by how much, is the whole content of its report.
    'assert-gate-timing-claims.ps1'    = @()
    # S2340. Reads two declarations - locales_config.xml and the LOCALES dict in
    # publish-play-listing.py - plus 39 small text files, so it is the cheapest member at 373 ms.
    # Rule 33 puts it in release scope on all four criteria (strategic S2340 "Гейт"): the listing
    # reaches a user only when the owner publishes it; its subject is the whole listing tree against
    # the whole locale declaration; each finding names its own locale and folder; and adding the
    # missing locales is one batch either way. Deliberately NOT passed -Quiet - that switch suppresses
    # the per-violation lines, and "which locale" is the whole content of this gate's report.
    'assert-play-listing-locales.ps1'  = @()
    # S3364. Three store pre-publication checks added as one block because they share one subject
    # (Rule 33): the shipped artifact a release publishes, not any one changed file. The identity
    # gate reads the phone and wear build configurations (WO-G7), the ABI gate reads the built wear
    # release APK, and the packaging gate measures the built VR-flavor APK against the Meta VRC
    # limits - a miss reaches a user only when the owner submits an artifact to a store; each gate
    # names its own measured pair or ABI set; and re-running one costs about a second. Thresholds
    # come from store-prepublish-thresholds.psd1, so a store policy change is a data edit. Each gate
    # degrades to a stated advisory skip (exit 0) when its artifact is not built yet, which is the
    # same semantics the delivery-size gate above accepts for an unreachable release.
    'assert-wear-phone-identity-parity.ps1' = @()   # S3364 - subject: the shipped phone+wear identity pair
    'assert-wear-64bit-abi.ps1'             = @()   # S3364 - subject: the shipped wear release APK
    'assert-meta-packaging-limits.ps1'      = @()   # S3364 - subject: the shipped VR-flavor release APK
    # S2597. The other half of the same listing tree: its locale sibling above judges the TEXTS,
    # this one judges the images the publisher declares in SINGLE_IMAGES. Rule 33 places it here on
    # the same four criteria, and for one more reason of its own - the defect it guards is invisible
    # by construction, because publish-play-listing.py skips an image whose file is absent and exits
    # 0, so for months the Play feature graphic had no source in the repository at all and nothing
    # said so. Not passed -Quiet: which image lost its source, or which copy of one artwork drifted,
    # is the whole content of the report.
    'assert-play-listing-graphics.ps1' = @()
    # S2652. The three download sizes the app compiles in, against the assets they name in the
    # delivery release. Rule 33 puts it here on all four criteria: a stale estimate reaches a user
    # only when a build ships; its subject is a release published by a run no session's diff
    # contains; each finding prints its asset, both byte counts and the literal to write; and
    # correcting them is one edit whenever it is done. It is also the only member that reads the
    # network, so it answers an unreachable release with exit 0 and a printed advisory - the loop
    # below collapses every non-zero code to FAIL, and an offline machine is not a defect.
    # Not passed -Quiet: the per-payload drift line is the report, green or red.
    'assert-delivery-size-estimates.ps1' = @()
    'assert-unreferenced-strings.ps1'  = @('-Quiet')
    'assert-splash-brand-sync.ps1'     = @('-Quiet')
    'assert-icon-inventory-sync.ps1'   = @()
    'assert-doc-icons-sync.ps1'        = @()
    'assert-device-profile-matrix.ps1' = @('-Quiet')
    # S2592. Every record in PLAN/spec-catalog-archive.jsonl against the file it names. Rule 33
    # puts it here on all four criteria: a lost archived spec reaches no user between releases, its
    # subject is the whole journal against the whole archive directory and no changed file can be
    # blamed for a dangling row, every finding prints its own id and path, and repairing a batch of
    # them costs one recovery run whenever it is done. Not passed -Quiet: which ticket lost its text
    # is the entire content of the report, and the release sweep is the moment 166 specs move.
    'assert-archive-artefacts.ps1'     = @()
    # S2602. The third member of the listing family, and the one that judges the SCREENSHOT PIXELS:
    # the locale gate judges the texts, the graphics gate judges that the single images exist and
    # agree, and neither ever looked at what a composed screenshot actually shows. Measured
    # 2026-09-06, all 24 tenInchScreenshots carried a caption band over 22% of image height against
    # Google's stated 20% tagline ceiling, painted across the app bar of every tablet frame.
    #
    # Rule 33 puts it here on all four criteria, exactly as for its two siblings: a listing image
    # reaches a user only when the owner publishes; its subject is the whole published asset tree,
    # which no changed file created; every finding prints its own path and its own measured share;
    # and recomposing a set is one run whenever it is done. It is also the clearest case yet for the
    # release scope rather than the closure - the debt it reports is 24 files of asset work that no
    # session closing an unrelated ticket could clear, which is precisely the shape S1939 measured.
    #
    # Sits apart from its two siblings because this table is ordered by COST and it opens 24 PNGs:
    # measured 7735 ms against their 373 and 1765. The family reads as one block in the .DESCRIPTION
    # list above, which is where grouping by subject belongs.
    #
    # It was EXPECTED RED until S2602 Phase 03 and Phase 04 recaptured both sets; measured
    # 2026-09-09 it is green, at 10.5-10.6% band share against the 20% ceiling. The claim is kept
    # rather than deleted because it says what a red verdict here means: a recapture that has not
    # happened, not a gate that broke.
    # Not passed -Quiet - which frame breached the ceiling, and by how much, is the whole report.
    'assert-play-listing-screenshot-geometry.ps1' = @()
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
    # S2616. The same index question over a wider selection: every statically resolvable dot-source
    # target, which is the set whose absence stops a consumer from PARSING in a fresh clone. Here on
    # the same four Rule 33 criteria as its neighbour, and for one more - the per-ticket half only
    # ever sees consumers a session changed, so a target orphaned by a session that has since ended
    # is visible nowhere else.
    'assert-dotsource-tracked.ps1'     = @()
    # S2618. The document registry's reverse direction: the harness validator enforces
    # registry -> disk, nothing enforced disk -> registry, so a directory full of documents that no
    # record named failed no gate - which is how the missing `.claude/rules/*.md` glob survived to
    # S2607. Release scope on the S1939 arithmetic quoted in post-change.ps1: the state is
    # repository-wide and a new documentation tree appears on the scale of months, so a per-closure
    # run would spend the same minutes that gate spent to report a single finding.
    'assert-document-registry-coverage.ps1' = @('-Quiet')
    # S3030. The top level of temp/ against the one inventory that declares it. Release scope for
    # Rule 33's four criteria, and the attribution corollary is not theoretical here: the directory
    # is shared by every concurrent session, so a per-closure run would fail whoever ran it over a
    # neighbour's lock file or queue marker - which is how S2998's blacklist and then S3025's suite
    # assertion were each broken by a process that was not under test.
    'assert-temp-root-inventory.ps1'   = @('-Quiet')
    # S3178. The two Wear merged manifests against wear/config/store-boundary-policy.json. Release
    # scope on all four Rule 33 criteria: a sensitive permission in the store variant reaches a user
    # only when the Wear bundle is published; its subject is a built artifact rather than a changed
    # file, because a dependency manifest can reintroduce a permission no source file here declares;
    # every finding prints its own permission or component and the category that excludes it; and
    # moving a batch of declarations costs one edit whenever it is done.
    # Not passed -Quiet: which declaration crossed the boundary is the whole content of the report.
    # It answers an unbuilt wear module with exit 0 and a printed advisory rather than 2 - see its
    # -RequireArtifacts note; the loop below collapses every non-zero code to FAIL, and a phone
    # release is not the moment to refuse over a watch artifact this checkout never built.
    'assert-wear-store-boundary.ps1'   = @()
}

# S3010. Which fingerprint input groups each gate reads, for -OnlyGroups. Deliberately PARTIAL: a
# gate missing from this map is always selected, so the mapping can grow one confident entry at a
# time and an unmapped gate can never be skipped by accident. The groups are named by
# scripts/quality/release-scope-fingerprint.ps1 and nowhere else.
$gateInputGroups = @{
    'assert-play-listing-locales.ps1'             = @('play-listing', 'phone-src')
    'assert-play-listing-graphics.ps1'            = @('play-listing')
    'assert-play-listing-screenshot-geometry.ps1' = @('play-listing')
    'assert-unreferenced-strings.ps1'             = @('phone-src', 'wear-src')
    'assert-splash-brand-sync.ps1'                = @('phone-src')
    'assert-icon-inventory-sync.ps1'              = @('docs', 'phone-src')
    'assert-doc-icons-sync.ps1'                   = @('docs')
    'assert-archive-artefacts.ps1'                = @('specs-archive')
    'assert-source-gates.ps1'                     = @('phone-src', 'wear-src')
    'assert-document-registry-coverage.ps1'       = @('docs', 'scripts')
    'run-script-suites.ps1'                       = @('scripts')
    'assert-suite-tracked.ps1'                    = @('scripts')
    'assert-dotsource-tracked.ps1'                = @('scripts')
    'assert-wear-store-boundary.ps1'              = @('wear-src')
}

# S3010 follow-up: a value that names no group selects nothing but the unmapped gates, and the batch
# still prints "PASS (release scope clean)" - a release scope nobody judged, which is the exact
# failure the parameter's own documentation calls out. Measured 2026-09-12 on the r37 sweep:
# `-OnlyGroups wear-src,docs,scripts` reached this script as ONE string, because `pwsh -File` does
# not split a comma list into an array, and 13 of 16 gates were skipped in silence - including all
# three that were failing at the time. Refuse instead, and name both the bad value and the real set.
if ($OnlyGroups.Count -gt 0) {
    $groupsFile = Join-Path $PSScriptRoot 'release-scope-fingerprint.groups.json'
    if (Test-Path -LiteralPath $groupsFile) {
        $knownGroups = @((Get-Content -LiteralPath $groupsFile -Raw -Encoding UTF8 |
                ConvertFrom-Json).groups.PSObject.Properties.Name)
        $unknownGroups = @($OnlyGroups | Where-Object { $knownGroups -notcontains $_ })
        if ($unknownGroups.Count -gt 0) {
            Write-Host ("assert-release-scope-gates: -OnlyGroups names {0} group(s) that do not exist: {1}" -f
                $unknownGroups.Count, ($unknownGroups -join ', ')) -ForegroundColor Red
            Write-Host ("  known groups: {0}" -f ($knownGroups -join ', ')) -ForegroundColor Red
            Write-Host "  A comma list passed through 'pwsh -File' arrives as one string - use a wrapper .ps1 that calls this script with -OnlyGroups @('a','b'), or pass -Command." -ForegroundColor Red
            exit 2
        }
    }
}

function Test-GateSelected {
    param([string]$GateName)
    if ($OnlyGroups.Count -eq 0) { return $true }
    if (-not $gateInputGroups.ContainsKey($GateName)) { return $true }
    foreach ($group in $gateInputGroups[$GateName]) {
        if ($OnlyGroups -contains $group) { return $true }
    }
    return $false
}

$results = [System.Collections.Generic.List[object]]::new()
$missing = 0
$skipped = 0
foreach ($entry in $gates.GetEnumerator()) {
    if (-not (Test-GateSelected -GateName $entry.Key)) {
        $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = 'SKIPPED'; Ms = 0 })
        $skipped++
        continue
    }
    $path = Join-Path $PSScriptRoot $entry.Key
    if (-not (Test-Path $path)) {
        $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = 'MISSING'; Ms = 0 })
        Write-GateTelemetryRecord -Runner 'assert-release-scope-gates' -Gate $entry.Key `
            -Status 'MISSING' -ExitCode 2 -ElapsedMs 0
        $missing++
        continue
    }

    # -Gate only reaches a gate that declares it. Passing it blindly made two gates that do not
    # (assert-play-listing-graphics, assert-delivery-size-estimates) die on parameter binding before
    # they read anything, and the loop below scored that as FAIL - so "could not verify" was
    # reported as "found a defect in the release scope" on every sweep, and neither gate was ever
    # actually judged. S2687, measured 2026-09-08.
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $gateArgs = @($entry.Value)
    if ((Get-Command -Name $path -ErrorAction SilentlyContinue)?.Parameters.ContainsKey('Gate')) {
        $gateArgs = @('-Gate') + $gateArgs
    }
    & $pwshExe -NoProfile -File $path @gateArgs | Write-Host
    $sw.Stop()
    $status = ($LASTEXITCODE -eq 0) ? 'PASS' : 'FAIL'
    $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = $status; Ms = [int]$sw.Elapsed.TotalMilliseconds })
    Write-GateTelemetryRecord -Runner 'assert-release-scope-gates' -Gate $entry.Key `
        -Status $status -ExitCode ([int]$LASTEXITCODE) -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
}

if ($Json) {
    [ordered]@{
        status = if ($missing -gt 0) { 'cannot-verify' } elseif (@($results | Where-Object { $_.Status -ne 'PASS' -and $_.Status -ne 'SKIPPED' }).Count -gt 0) { 'fail' } else { 'pass' }
        gates  = $results
    } | ConvertTo-Json -Depth 4
}
else {
    Write-Host ''
    Write-Host 'assert-release-scope-gates summary:' -ForegroundColor Cyan
    foreach ($r in $results) {
        $color = switch ($r.Status) { 'PASS' { 'Green' } 'FAIL' { 'Red' } 'SKIPPED' { 'DarkGray' } default { 'Yellow' } }
        Write-Host ("  {0,-40} {1} ({2} ms)" -f $r.Gate, $r.Status, $r.Ms) -ForegroundColor $color
    }
    if ($skipped -gt 0) {
        Write-Host ("  {0} gate(s) skipped - none of their input groups moved (-OnlyGroups {1})" -f $skipped, ($OnlyGroups -join ', ')) -ForegroundColor DarkGray
    }
}

# S2537: the gate PLACEMENT review, run here and nowhere else. Rule 33 sorted every gate into a
# scope once, and nothing re-sorted them since - the per-ticket set grew one gate per ticket, each
# addition locally right, and by 2026-09-04 a typical closure paid tens of seconds for steps nobody
# had re-costed. The release boundary is where that gets re-read, because it is the only moment the
# whole set is in view at once.
#
# A REPORT, never a gate, and deliberately outside the table above: the runner collapses every
# non-zero code to FAIL, and a candidate row is not a defect - it is a row a human then judges by
# the four-part test, whose exceptions no arithmetic over the journal can see. Blocking a ship on
# one would be failing a release for bookkeeping. Its own failure is swallowed for the same reason:
# an absent journal must not turn a clean release scope red.
if (-not $Json) {
    try {
        Write-Host ''
        Write-Host 'gate placement review (S2537 - advisory, affects no verdict):' -ForegroundColor Cyan
        # It writes to the host itself, so it is called rather than piped - a pipeline here would
        # capture nothing and print nothing.
        & (Join-Path $PSScriptRoot 'measure-gate-frequency.ps1') -Placement
    }
    catch {
        Write-Host "  placement review unavailable: $($_.Exception.Message)" -ForegroundColor DarkGray
    }

    # S2608, a REPORT for the same reason as the review above: the runner collapses every non-zero
    # code to FAIL, and this one returns 3 whenever the canon checkout and the running harness
    # diverge - a state no ticket caused, no session can fix from here, and which is legitimately
    # absent on any machine without a canon checkout. Blocking a ship on it would fail a release for
    # another repository's uncommitted work. It belongs at the release boundary rather than in a
    # closure because its subject is the machine's plugin deployment, which changes on plugin update.
    try {
        Write-Host ''
        Write-Host 'harness delivery (S2608 - advisory, affects no verdict):' -ForegroundColor Cyan
        & (Join-Path $PSScriptRoot 'assert-harness-drift.ps1')
    }
    catch {
        Write-Host "  harness drift check unavailable: $($_.Exception.Message)" -ForegroundColor DarkGray
    }
}

$batchStopwatch.Stop()
$batchMs = [int]$batchStopwatch.Elapsed.TotalMilliseconds

if ($missing -gt 0) {
    Write-GateBatchTelemetryRecord -Runner 'assert-release-scope-gates' -ExitCode 2 -ElapsedMs $batchMs
    Write-Error "assert-release-scope-gates: CANNOT VERIFY - $missing gate script(s) absent." -ErrorAction Continue
    exit 2
}

$failed = @($results | Where-Object { $_.Status -ne 'PASS' -and $_.Status -ne 'SKIPPED' }).Count
if ($failed -gt 0) {
    Write-GateBatchTelemetryRecord -Runner 'assert-release-scope-gates' -ExitCode 1 -ElapsedMs $batchMs
    $names = (@($results | Where-Object { $_.Status -ne 'PASS' -and $_.Status -ne 'SKIPPED' } | ForEach-Object { $_.Gate }) -join ', ')
    Write-Host @'
  Why these gates run HERE and not in every closure (S2517 moved this off the always-loaded rules
  page): a gate is placed by its subject, and the subject of each of these is the tree as a whole,
  not the change in front of you. Measured 2026-08-22 (S1939): three tree-scope gates produced 68 of
  the 191 red lines across 53 fast-gate runs, and assert-device-profile-matrix spent 33 minutes of
  closure time in one month to report a single finding - a per-ticket placement charges every
  session for debt none of them owns. A new gate names its scope class at birth; unnamed still means
  per ticket, which is how the imbalance arose.
'@
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
