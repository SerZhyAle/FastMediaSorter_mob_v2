# Combined post-change runner.
# Chains the applicable mechanical post-change steps for a given change type.
#
# Usage:
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -File "scripts/post-change.ps1" `
#       -Target "post-change.ps1" `
#       -Description "added change-type routing" `
#       -ChangeType Script
#
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -Files "wear/build.gradle.kts,scripts/builders/check-standard-fast.ps1" `
#       -Target "S1553" `
#       -Description "closed build and repository tooling changes" `
#       -ChangeType Tooling
#
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -File "app_v2/src/main/java/.../Foo.kt" `
#       -Target "FooClass" `
#       -Description "added bar feature" `
#       -ChangeType Kotlin `
#       [-Module app_v2]
#
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -File "app_v2/src/main/res/values/strings.xml" `
#       -Target "string/foo_title" `
#       -Description "added foo strings" `
#       -ChangeType Xml `
#       -KeyPrefix "foo_"
#
# Backward compatibility:
#   -SkipScan + -KeyPrefix => Xml
#   -SkipScan              => Doc
#   -KeyPrefix             => Mixed
#   no router flags        => Kotlin
#
# .NOTES
#   Exit codes (S1338, CLAUDE.md Rule 7):
#     0  every gate that ran passed. The verdict line reads either
#        "post-change: PASS" or "post-change: PASS WITH ADVISORIES (n)" -
#        the latter means a gate found something it could not attribute to
#        this change, and the caller is expected to read the listed names.
#     1  a gate failed. Something was inspected and judged defective. The run
#        does NOT stop at that gate (S1598): every remaining gate still runs, and
#        the tail reads "post-change: FAIL (n gate(s))" followed by the full list,
#        each with the command that reproduces it alone. Nothing is written.
#     2  could not verify. Nothing was inspected, or a gate could not run:
#        an invalid/absent/unexpanded file argument, or missing tooling.
#
#   A caller must distinguish 1 from 2. "Found a defect" and "did not look"
#   are different answers, and treating 2 as success is how a green verdict
#   comes to certify nothing at all.
#
#   Output shape (S1937): steps that did not apply are NOT printed one per line.
#   They are collapsed into a single "skipped (n, not applicable ..): a, b, c"
#   line above the verdict, on both the passing and the failing path. Pass
#   -ShowSkips for the old per-step lines with their reasons. Every step's
#   outcome - PASS, FAIL and SKIP alike - is appended to the machine journal
#   temp/metrics/gate-executions.jsonl regardless of what the console shows,
#   and scripts/quality/measure-gate-frequency.ps1 reports from that journal.

param(
    [string]$File,
    # S1338: a closure spans 4.34 files on average and 62% of closures span more
    # than one, so scoping the gates to a single -File certified roughly a
    # quarter of the change. Pass the whole changed set here; -File stays for
    # every existing caller and is folded into the same set.
    [string[]]$Files,
    [string[]]$Deleted,
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][string]$Description,
    [ValidateSet('Doc', 'Script', 'Config', 'Tooling', 'Kotlin', 'Xml', 'Mixed')]
    [string]$ChangeType,
    [string]$Module = "app_v2",
    [string]$KeyPrefix,
    [switch]$SkipScan,
    # S0826: per-change closure on an always-dirty tree. When set, detekt is diff-scoped to
    # -File (fails only on findings in THIS change), and every count-ratchet gate (neuroslop,
    # listener-symmetry, flavor-flag, deprecated-pm; S0848/S0850) judges a real FATAL delta on
    # the changed file (growth vs HEAD) instead of a full-project scan - other tickets' WIP no
    # longer trips them. Only icon-inventory-sync stays advisory (its re-render is repo-wide).
    # Release/CI omit the switch for the strict full project gate.
    [switch]$ScopeToFile,
    # S1338 phase 05: acknowledge the document-registry records this change touches. The
    # mandate was stated in five always-on places and obeyed at 0.6-3% of its own cadence,
    # which teaches that a mandate is optional. It is a trigger now: a closure whose changed
    # set intersects a registered document's `paths` names the matching record ids and stays
    # short of a clean PASS until they are passed back here. Accepts ids or 'all'.
    [string[]]$RegistryAck,
    # S1937: restore the per-step SKIP lines. Off by default because a closure skips far more
    # steps than it runs (measured 17,973 SKIP lines against 16,253 PASS lines over one month),
    # and every one of them rides in the session context for the rest of the session. The names
    # still appear in the collapsed summary; this switch adds each step's reason back.
    [switch]$ShowSkips
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# S1937: the gate journal that measure-gate-frequency.ps1 reads. S1795 wired it into
# assert-fast-gates.ps1 only, so the journal saw ~11 runs a day and missed the ~61 closures
# that actually execute these gates - the runner carrying the majority of the evidence was
# the one not reporting. Telemetry never changes a verdict (the writer swallows its own
# errors), so a broken journal cannot fail a closure.
. (Join-Path $root 'scripts/quality/lib/gate-telemetry.ps1')

# S1338: one resolved set drives every downstream consumer, so a gate can never
# silently judge a narrower slice than the verdict claims to cover. $File keeps
# working as the "primary" file for the dev-log line and the single-file gates
# that have no changed-set parameter.
# S1184: `pwsh -File` binds `-Files a.kt,b.kt` as ONE array element, so the set has to be
# comma-split here or the validation below rejects the whole CSV as a single missing path.
if (-not [string]::IsNullOrWhiteSpace($File) -and $Files.Count -gt 0) {
    Write-Error 'post-change: use either -File or -Files, not both.' -ErrorAction Continue
    exit 2
}
$rawFiles = if ($Files.Count -gt 0) { @($Files) } else { @($File) }
$changedFiles = @(
    $rawFiles |
        ForEach-Object { ([string]$_) -split ',' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
)
$deletedFiles = @(
    $Deleted |
        ForEach-Object { ([string]$_) -split ',' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
)
if ($changedFiles.Count + $deletedFiles.Count -eq 0) {
    Write-Error 'post-change: provide -File, -Files or -Deleted.' -ErrorAction Continue
    exit 2
}
if (-not $File) {
    $File = if ($changedFiles.Count -gt 0) { $changedFiles[0] } else { $deletedFiles[0] }
}

# S1338: an unexpanded shell variable used to reach the gates as a literal path
# and every gate then found nothing to complain about, so the facade printed a
# full green PASS certifying no file at all - 3 confirmed occurrences in 463
# runs. Refuse the run instead, and refuse it as "could not verify" (exit 2)
# rather than "found a defect" (exit 1): nothing was inspected.
$badArgs = @()
foreach ($candidate in $changedFiles) {
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        $badArgs += "<empty>"
        continue
    }
    if ($candidate -match '[$%]') {
        $badArgs += "$candidate (unexpanded shell variable)"
        continue
    }
    $probe = if ([System.IO.Path]::IsPathRooted($candidate)) {
        $candidate
    }
    else {
        Join-Path $root $candidate
    }
    if (-not (Test-Path $probe)) {
        $badArgs += "$candidate (not found)"
    }
}
foreach ($candidate in $deletedFiles) {
    if ($candidate -match '[$%]') {
        $badArgs += "$candidate (unexpanded shell variable)"
        continue
    }
    $probe = if ([System.IO.Path]::IsPathRooted($candidate)) {
        $candidate
    }
    else {
        Join-Path $root $candidate
    }
    if (Test-Path $probe) {
        $badArgs += "$candidate (named as deleted but still on disk)"
    }
}
if ($badArgs.Count -gt 0) {
    Write-Host "post-change: CANNOT VERIFY - invalid file argument(s):" -ForegroundColor Red
    foreach ($bad in $badArgs) { Write-Host "  $bad" -ForegroundColor Red }
    Write-Error "post-change: $($badArgs.Count) invalid file argument(s); nothing was inspected." -ErrorAction Continue
    exit 2
}

$pwsh = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    "pwsh"
}

$totalSw = [System.Diagnostics.Stopwatch]::StartNew()

# S1338: every advisory gate that found something lands here, so the final
# verdict can report what it could not attribute instead of swallowing it.
$script:AdvisoryFindings = @()

# S1598: every FATAL gate failure lands here instead of ending the process on the
# spot. Fail-fast made one set of defects cost several full runs of the facade:
# 215 failed runs in the week of 2026-08-05, median 8 turns from a failed run to
# the next one, because each run could only ever name the first thing wrong.
$script:FatalFindings = @()

# S1937: steps that did not apply to this change. Collected instead of printed one per line -
# a closure skips more steps than it runs, and each printed line stays in the session context
# for every later request. The summary keeps every name, so "why did detekt not run" is still
# answerable; -ShowSkips restores the per-step reason.
$script:SkippedSteps = @()

# S1598: label -> @{ Repro = '<command that runs this gate alone>'; Fix = '<what to do>' }.
# Data, not prose in the facade, so registering a new gate never edits the output
# logic (owner input). A label with no entry prints without a hint - not an error;
# assert-gate-hints-sync.ps1 is what keeps the two sets in step.
$script:GateHints = @{}
$hintFile = Join-Path $root "scripts/quality/gate-recovery-hints.psd1"
if (Test-Path $hintFile) {
    try { $script:GateHints = Import-PowerShellDataFile -LiteralPath $hintFile }
    catch { Write-Host "  [gate-hints] WARN - unreadable: $($_.Exception.Message)" -ForegroundColor Yellow }
}

function Get-GateHint([string]$Label) {
    if ($script:GateHints.ContainsKey($Label)) { return $script:GateHints[$Label] }
    return $null
}

function Write-StepResult(
    [string]$Label,
    [ValidateSet('PASS', 'FAIL', 'SKIP')][string]$Status,
    [int]$ElapsedMs,
    [string]$Details = '',
    [int]$ExitCode = 0
) {
    Write-GateTelemetryRecord -Runner 'post-change' -Gate $Label -Status $Status `
        -ExitCode $ExitCode -ElapsedMs ([Math]::Max($ElapsedMs, 0))

    $color = switch ($Status) {
        'PASS' { 'Green' }
        'FAIL' { 'Red' }
        default { 'DarkGray' }
    }

    $message = "  [$Label] $Status"
    if ($ElapsedMs -ge 0) {
        $message += " ($ElapsedMs ms)"
    }
    if (-not [string]::IsNullOrWhiteSpace($Details)) {
        $message += " - $Details"
    }

    Write-Host $message -ForegroundColor $color
}

function Invoke-Step([string]$Label, [scriptblock]$Action) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    try {
        $global:LASTEXITCODE = 0
        & $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        if ($exitCode -ne 0) {
            throw "exit $exitCode"
        }

        $sw.Stop()
        Write-StepResult -Label $Label -Status PASS -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
    }
    catch {
        $sw.Stop()
        $exitCode = if ($LASTEXITCODE -and [int]$LASTEXITCODE -ne 0) { [int]$LASTEXITCODE } else { 1 }
        $reason = $_.Exception.Message
        if ($reason -eq "exit $exitCode") {
            $reason = "child exit code $exitCode"
        }

        Write-StepResult -Label $Label -Status FAIL -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) `
            -Details $reason -ExitCode $exitCode
        exit $exitCode
    }
}

# S1598: the gate wrapper. Same verdict surface as Invoke-Step, but a failure is
# recorded and the run continues, so ONE run names every gate the changed set
# breaks. Certification is unchanged: Test-FatalFindings barricades the mutating
# steps and exits 1, so a failed run still writes no changelog row and no catalog
# index. Invoke-Step stays for those mutating steps, where "cannot go on" is real.
function Invoke-Gate([string]$Label, [scriptblock]$Action) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    try {
        $global:LASTEXITCODE = 0
        & $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        if ($exitCode -ne 0) {
            throw "exit $exitCode"
        }

        $sw.Stop()
        Write-StepResult -Label $Label -Status PASS -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
    }
    catch {
        $sw.Stop()
        $exitCode = if ($LASTEXITCODE -and [int]$LASTEXITCODE -ne 0) { [int]$LASTEXITCODE } else { 1 }
        $reason = $_.Exception.Message
        if ($reason -eq "exit $exitCode") {
            $reason = "child exit code $exitCode"
        }

        Write-StepResult -Label $Label -Status FAIL -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) `
            -Details $reason -ExitCode $exitCode
        $hint = Get-GateHint $Label
        if ($hint) {
            if ($hint.Repro) { Write-Host "      repro: $($hint.Repro)" -ForegroundColor Yellow }
            if ($hint.Fix) { Write-Host "      fix:   $($hint.Fix)" -ForegroundColor Yellow }
        }
        $script:FatalFindings += [pscustomobject]@{ Label = $Label; ExitCode = $exitCode }
    }
}

# S1598: the barrier. Called immediately before the first mutating step, so the
# accumulated failures end the run exactly where fail-fast used to end it - with
# nothing written. Exit 1 means "found a defect", per the exit contract above.
function Test-FatalFindings {
    if ($script:FatalFindings.Count -eq 0) { return }

    Write-Host ''
    Write-SkippedSummary
    Write-Host "post-change: FAIL ($($script:FatalFindings.Count) gate(s), $resolvedChangeType)" -ForegroundColor Red
    foreach ($finding in $script:FatalFindings) {
        Write-Host "  failed: $($finding.Label) (exit $($finding.ExitCode))" -ForegroundColor Red
        $hint = Get-GateHint $finding.Label
        if ($hint -and $hint.Repro) { Write-Host "      repro: $($hint.Repro)" -ForegroundColor Yellow }
    }
    Write-Host "  Nothing was written: no changelog row, no catalog sync. Fix the above and re-run." -ForegroundColor Red
    exit 1
}

function Skip-Step([string]$Label, [string]$Reason) {
    $script:SkippedSteps += [pscustomobject]@{ Label = $Label; Reason = $Reason }
    if ($ShowSkips) {
        Write-StepResult -Label $Label -Status SKIP -ElapsedMs 0 -Details $Reason
        return
    }
    # The journal still records every skip even when the console does not print it - the
    # question "which gate never runs" is answered from the journal, not from scrollback.
    Write-GateTelemetryRecord -Runner 'post-change' -Gate $Label -Status 'SKIP' -ExitCode 0 -ElapsedMs 0
}

# S1937: one line for the whole not-applicable set, printed before the verdict on both the
# passing and the failing path so a failed closure still shows what never ran.
function Write-SkippedSummary {
    if ($ShowSkips -or $script:SkippedSteps.Count -eq 0) { return }
    $names = ($script:SkippedSteps | ForEach-Object { $_.Label }) -join ', '
    Write-Host "  skipped ($($script:SkippedSteps.Count), not applicable to this change): $names" -ForegroundColor DarkGray
    Write-Host '  reasons: re-run with -ShowSkips' -ForegroundColor DarkGray
}

# S0826: like Invoke-Step but non-fatal. A project-wide gate that cannot attribute its
# failure to THIS change (today only icon-inventory-sync; the count-ratchet gates moved to
# FATAL per-file deltas in S0848/S0850) is reported as a WARN under -ScopeToFile and the
# facade keeps going instead of aborting the close. The operator still sees it.
function Invoke-AdvisoryStep([string]$Label, [scriptblock]$Action, [string]$AdvisoryDetails) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $global:LASTEXITCODE = 0
        & $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        $sw.Stop()
        if ($exitCode -ne 0) {
            # Most advisory gates are project-wide ratchets whose finding may belong to another
            # ticket's WIP. A caller that knows better (the preflight judges YOUR files only)
            # passes its own wording, so the verdict line never misdescribes what was found.
            $details = if ($AdvisoryDetails) { $AdvisoryDetails } else { "advisory (project-wide ratchet; not attributed to your change - verify your files manually)" }
            Write-StepResult -Label $Label -Status SKIP -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details $details
            $script:AdvisoryFindings += "$Label (exit $exitCode)"
        }
        else {
            Write-StepResult -Label $Label -Status PASS -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
        }
    }
    catch {
        $sw.Stop()
        Write-StepResult -Label $Label -Status SKIP -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details "advisory (gate error: $($_.Exception.Message))"
        $script:AdvisoryFindings += "$Label (gate error)"
    }
}

$resolvedChangeType = if ($PSBoundParameters.ContainsKey('ChangeType')) {
    $ChangeType
}
elseif ($SkipScan -and -not [string]::IsNullOrWhiteSpace($KeyPrefix)) {
    'Xml'
}
elseif ($SkipScan) {
    'Doc'
}
elseif (-not [string]::IsNullOrWhiteSpace($KeyPrefix)) {
    'Mixed'
}
else {
    'Kotlin'
}

# S1372: every path-keyed gate used to test the FIRST path the caller named, so a
# multi-file close silently skipped any gate whose trigger file was not first, while still printing
# a clean PASS. Two confirmed occurrences: S1363 (script-cheatsheet-sync skipped over a new .ps1)
# and S1370 (all-features skipped over docs/ALL_FEATURES.jsonl, in the same run where
# document-registry DID see it). Applicability is a property of the changed SET, not of one member.
$normChangedFiles = @($changedFiles | ForEach-Object { ($_ -replace '\\', '/') -replace '^\./', '' })
function Test-AnyChangedFile([string]$Pattern) {
    foreach ($candidate in $normChangedFiles) {
        if ($candidate -match $Pattern) { return $true }
    }
    return $false
}
function Get-FirstChangedFileMatch([string]$Pattern) {
    foreach ($candidate in $normChangedFiles) {
        if ($candidate -match $Pattern) { return $candidate }
    }
    return $null
}

# S1915: the flavors app_v2 declares, spelled exactly as check-standard-fast.ps1 accepts them.
$script:ResourceLinkFlavors = @('Standard', 'NoLegal', 'Lite', 'Photos', 'Legacy', 'Vr')

function Get-ResourceLinkFlavors([string]$TargetModule) {
    # Which variants have to link before the changed set counts as proven. A resource under
    # src/<flavor>/res is compiled into that flavor alone, so linking it as `standard` renders a
    # verdict about a variant that never sees the file - the same false green S1807 found when a
    # phone target was quoted as proof under a wear change.
    #
    # wear declares no product flavors and check-standard-fast.ps1 exits 2 on any -Flavor but the
    # default, so the watch module is answered before the source sets are read at all.
    if ($TargetModule -eq 'wear') { return @('Standard') }

    # src/main, src/debug and every other non-flavor source set ship inside the default variant,
    # so it is always linked; the loop only ever ADDS flavors on top of it.
    $selected = [System.Collections.Generic.List[string]]::new()
    $selected.Add('Standard')

    foreach ($candidate in $normChangedFiles) {
        if ($candidate -match '(^|/)src/([^/]+)/') {
            $sourceSet = $Matches[2]
            foreach ($flavor in $script:ResourceLinkFlavors) {
                # -eq on strings is case-insensitive here, which is what lets `vr` select `Vr`.
                if ($sourceSet -eq $flavor -and -not $selected.Contains($flavor)) { $selected.Add($flavor) }
            }
        }
    }
    return $selected.ToArray()
}

$docIconRoutingFile = Join-Path $root 'scripts/quality/lib/doc-icon-gate-routing.ps1'
. $docIconRoutingFile

$hasJvmSource = Test-AnyChangedFile '(^|/)src/.*\.(kt|java)$'
$hasXmlResource = Test-AnyChangedFile '(^|/)src/.*\.xml$'
$hasStringResource = Test-AnyChangedFile 'src/[^/]+/res/values[^/]*/strings.*\.xml$'
$isCodeChange = ($resolvedChangeType -in @('Kotlin', 'Mixed')) -and $hasJvmSource
$isResourceChange = ($resolvedChangeType -in @('Xml', 'Mixed')) -and $hasXmlResource

# S1553: ChangeType describes the intended closure family, but the changed set decides whether
# a source-specific gate has anything to inspect. This keeps Mixed valid for code-plus-strings
# while making a build/config-plus-script set a cheap, honest closure.
$hasDeletedJvmSource = @($deletedFiles | Where-Object {
        ($_ -replace '\\', '/') -match '(^|/)src/.*\.(kt|java)$'
    }).Count -gt 0
$catalogChangedFiles = @($changedFiles) + @($deletedFiles)
$runsCatalogSync = $isCodeChange -or $hasDeletedJvmSource
$runsStringsAudit = $isResourceChange -and $hasStringResource
$runsStringFormatGate = $isResourceChange -and $hasStringResource
$runsTicketLogAudit = $isCodeChange
$runsAcceptanceProbeGate = Test-AnyChangedFile 'scripts/(quality/(assert-ticket-acceptance-probes|lib/ticket-acceptance-probes)|spec_catalog/update)\.ps1$'
$runsDetektPreflight = $resolvedChangeType -in @('Kotlin', 'Mixed')
$runsDocPinsSync = $resolvedChangeType -in @('Config', 'Doc', 'Mixed', 'Tooling')
# S1075: same trigger as doc-pins-sync - drift enters via a Gradle bump (Config) or a
# hand edit to dev/TECH_REQUIREMENTS.md (Doc). Checks the doc pins the generator does not own.
$runsDocPinDrift = $resolvedChangeType -in @('Config', 'Doc', 'Mixed', 'Tooling')
# S1979: a live document naming a .ps1 that does not exist hands its reader a command that cannot
# run - thirteen such lines survived in three registered documents until a hand sweep found them.
# Keyed on the changed set, not on ChangeType: the corpus is docs/, dev/, .claude/ and the four
# agent-rule files at the root, and a .ps1 in the set is included because renaming or deleting a
# script is what breaks the documents naming it.
$runsDocScriptReferences =
    (Test-AnyChangedFile '^(docs|dev|\.claude)/.*\.(md|json|jsonl)$') -or
    (Test-AnyChangedFile '^(CLAUDE|AGENTS|GEMINI|README)\.md$') -or
    (Test-AnyChangedFile '\.ps1$')
# S0383 neuroslop ratchet gate. Covers Kotlin (trivial comments / swallowing catch /
# unsafe Flow collects) and Xml (hardcoded layout colors, build-invisible string-resource
# quotes). Baselines only ratchet DOWN. The live set is whatever source-matchers.ps1
# registers - the umbrella forwards unfiltered, so a new rule needs no wiring here.
$runsNeuroslopGate = $isCodeChange -or $isResourceChange
# S0720 detekt + ktlint static-analysis gate. Runs :app_v2:detekt :wear:detekt over a
# committed per-module baseline (only NEW findings fail). It invokes gradle, so it is scoped to
# a changed Kotlin/Java source file rather than the caller's label.
$runsDetektGate = $isCodeChange
# S1356 detekt-baseline absorption gate. Fires when a committed detekt baseline (or its ID snapshot)
# is among the changed files. A whole-module `detektBaseline` re-freeze accepts every live finding in
# that module at once - on 2026-08-02 one absorbed the debt S1198 and S1328 were written about, and
# left no journal row, so it was found only by accident. Keyed on the WHOLE changed set rather than
# $File: a baseline is almost never the first path a caller names, and the single-file applicability
# bug is its own ticket (S1372) - do not copy that shape here.
$runsBaselineAbsorptionGate = @($changedFiles | Where-Object {
        ($_ -replace '\\', '/') -match 'config/detekt/baseline-[A-Za-z0-9_]+\.(xml|ids)$'
    }).Count -gt 0
# S0416 FGS-notification gate. Blocks the Android 16 "Bad notification for startForeground"
# crash class: ?attr-tinted notification small icons (A) and foreground-service paths that
# build a notification without ensuring their channel (B). Covers Kotlin + Xml (drawables).
$runsFgsGate = $isCodeChange -or $isResourceChange
# S0507 focus-highlight ratchet gate. Layout-only concern: interactive views without a visible
# focus indication (Rule 16) must never grow. Covers Xml + Mixed (layout edits). Baseline ratchets DOWN.
$runsFocusHighlightGate = $isResourceChange
# S0489 ALL_FEATURES inventory drift gate. Fires only when the touched file is the
# inventory data, its schema, or the noLegal variant - validates the JSONL and blocks
# a silent record-count drop below the committed baseline. Narrow trigger by path.
$runsAllFeaturesGate = Test-AnyChangedFile 'docs/ALL_FEATURES.*\.(jsonl|json)$'
# S0440 settings-doc drift gate. Fires only when the touched file is a settings
# surface (settings fragment layout, the settings-search pipeline, a per-flavor
# availability module) or a settings doc artifact (manifest / annotations /
# reference). Re-runs the composite gate so a settings change that skipped
# regenerating the manifest, annotations, or reference is blocked. Narrow trigger.
$runsSettingsDocGate = (
    (Test-AnyChangedFile 'app_v2/src/main/res/layout/fragment_settings_.*\.xml$') -or
    (Test-AnyChangedFile 'app_v2/.*/ui/settings/search/') -or
    (Test-AnyChangedFile 'wear/.*/ui/settings/') -or
    (Test-AnyChangedFile 'wear/src/main/res/values[^/]*/strings') -or
    (Test-AnyChangedFile 'SettingsSearchAvailabilityModule\.kt$') -or
    (Test-AnyChangedFile 'docs/settings/') -or
    (Test-AnyChangedFile 'docs/SETTINGS_REFERENCE')
)

# S0558/S0945 settings-path drift gate. Fires when a HOW_TO or narrative guide
# (README/QUICK_START/FAQ/TROUBLESHOOTING, all locales) is edited - validates the
# embedded "Settings -> .." recipes against the manifest. Standalone (pure text, no
# gradle) so a doc edit stays fast; also runs as stage 5 of the settings-doc
# composite so a manifest/vocab change re-checks every guide.
$runsHowToPathGate = Test-AnyChangedFile 'docs/(HOW_TO|README|QUICK_START|FAQ|TROUBLESHOOTING)[A-Z_]*\.md$'
# S1548 rule-digest gate. Fires when a file holding one of the four mirroring roles is in the
# changed set: the authority (CLAUDE.md), a full digest (AGENTS.md, .github/copilot-instructions.md)
# or the pointer (GEMINI.md). Editing any of them is exactly when a digest can fall behind, and the
# check is pure text over four markdown files, so it costs nothing on the fast path.
# Roles and their obligations: dev/RULE_AND_SKILL_AUTHORING.md "Rule mirroring contract".
$runsRuleDigestGate = Test-AnyChangedFile '^(CLAUDE\.md|AGENTS\.md|GEMINI\.md|\.github/copilot-instructions\.md)$'
# S0684 dialog-cancel-style gate. Fires only when a dialog / bottom-sheet layout is touched -
# a cancel/negative action button in such a pair must use Widget.FastMediaSorter.Button.DialogCancel,
# never a one-off cancel style. Baseline ratchets DOWN. Narrow trigger keeps it cheap.
$runsDialogCancelGate = ($isResourceChange -and
    (Test-AnyChangedFile 'res/layout.*/(dialog_|bottom_sheet_).*\.xml$'))
# S1190 RTL gate. An absolute Left/Right attribute reads correctly in English and wrong in Arabic
# or Urdu, which no English-language check can see - so a touched layout is judged directly.
$runsRtlLayoutGate = ($isResourceChange -and
    (Test-AnyChangedFile 'res/layout.*/.*\.xml$'))
# S0721 listener symmetry gate. Runs on Kotlin or Mixed change types.
$runsListenerSymmetryGate = $isCodeChange
# S0918 orientation-implied-feature gate. Fires only when a manifest is touched - an
# activity that pins screenOrientation implies a required screen.* hardware feature,
# which shrinks Google Play device reach unless src/main declares it not-required.
$runsOrientationFeatureGate = Test-AnyChangedFile 'AndroidManifest\.xml$'
# S1598 orientation layout-pairing gate. The defect needs BOTH halves - an activity that absorbs
# orientation in configChanges AND a landscape layout it therefore never re-inflates - so either a
# touched manifest or a touched landscape layout can create it, and neither half sees the other
# alone. The recovery hint for this label already shipped in gate-recovery-hints.psd1; without the
# call site it described a gate the facade never ran.
$runsOrientationLayoutPairingGate = (Test-AnyChangedFile 'AndroidManifest\.xml$') -or
    (Test-AnyChangedFile 'res/layout[^/]*-land[^/]*/.*\.xml$')
# S1639 Gson persistence contract gate. Fires on a Kotlin source or an obfuscation rules file, the two
# halves of the invariant: a model whose JSON outlives the process must have its field names pinned, and
# either half can break it alone - a new model, or a keep rule that stopped covering an old one. The
# defect class reached users six times before anything tied the two facts together.
$runsGsonContractGate = (Test-AnyChangedFile '\.kt$') -or (Test-AnyChangedFile 'proguard-.*\.pro$')
# S1844: the instrumented set is compiled by NO other check. fk/fkn compile src/main, fc adds resources,
# fu compiles and runs src/test - a different source set - and d/db build the app, not its tests. So a
# test under src/androidTest could sit in the tree for months unable to compile at all, which is what
# AppDatabaseMigration50To51Test.kt did: it referenced a TEST_DB constant it never declared. A test that
# cannot compile is indistinguishable from an absent one, except that it looks present.
#
# Narrow trigger on purpose. This is the only gradle task the facade adds beyond detekt, so it fires only
# when the change can actually break that set: a file under src/androidTest, or a Room schema file whose
# entities the migration tests assert against. Measured 6.8 s on a warm daemon, well inside the
# foreground threshold of CLAUDE.md section 6.
$runsAndroidTestCompileGate = (Test-AnyChangedFile '(^|/)src/androidTest/') -or `
    (Test-AnyChangedFile 'data/local/db/(Migration\d+To\d+|AppDatabase|.*Dao|.*Entity)\.kt$')
# S1915: the resource-link gate - the third gradle task the facade adds beyond detekt. Every other
# gate here is lexical, so before this one NO path in the facade ran aapt: a layout that did not link
# closed green, and the ticket reached BlockNeedUserTest - "install this and test it" - without
# anything ever having packaged what the tester installs (S1881). `fk` cannot cover the gap because
# it compiles Kotlin and never links resources.
#
# Reuses $isResourceChange rather than a new predicate so a Kotlin-only or docs-only set is skipped
# by construction and pays nothing, which is what keeps the gate from being falsely strict.
$runsResourceLinkGate = $isResourceChange
# Script-cheatsheet drift gate. Fires when a repo PowerShell script (under the
# cheatsheet's discovery roots scripts/ and dev/CATALOG/scripts/) or the generated
# doc itself is touched - a changed param() block staleens docs/SCRIPT_CHEATSHEET.md
# unless regenerated. Pure AST parse + byte-diff (no gradle), so it stays cheap.
$runsScriptCheatsheetGate = (
    (Test-AnyChangedFile '(^|/)scripts/.*\.ps1$') -or
    (Test-AnyChangedFile 'docs/SCRIPT_CHEATSHEET\.md$')
)
# S1392 flavor-matrix doc-conformance gate. Fires when the flavor grid itself moves
# (app_v2/build.gradle.kts), when the generated snapshot / rendered table is touched, or when one
# of the documents carrying a checked glyph table is edited. Compares cell VALUES against
# docs/flavors/flavor-matrix.json, so a marker inverted against the gates cannot land silently -
# the failure mode that left docs/HOW_TO.md stating the opposite of the lite flags on two rows.
# Pure text plus one JSON, no gradle daemon.
$runsFlavorMatrixDocGate = (
    (Test-AnyChangedFile 'app_v2/build\.gradle\.kts$') -or
    (Test-AnyChangedFile 'docs/FLAVOR_MATRIX\.md$') -or
    (Test-AnyChangedFile 'docs/flavors/flavor-matrix\.json$') -or
    (Test-AnyChangedFile 'docs/DEV_OPS\.md$') -or
    (Test-AnyChangedFile 'docs/HOW_TO[A-Z_]*\.md$') -or
    (Test-AnyChangedFile 'scripts/(quality/flavor-matrix-docs\.psd1|quality/assert-flavor-matrix-docs\.ps1|docs/generate-flavor-matrix\.ps1)$')
)
# S1495 OSS-notice conformance gate. Fires when the dependency set moves (either build file),
# when the licence manifest or the rendering pipeline is touched, or when a rendered notice page
# or its snapshot is edited. Two findings: a shipping coordinate with no licence entry, and a
# published page that no longer matches what the generator produces. Before S1495 nothing tied
# the two together, which is how the published page came to name 2 libraries out of 97 and to
# state a wrong licence for two of them. Pure text plus one JSON, no gradle daemon.
$runsOssNoticesGate = (
    (Test-AnyChangedFile 'app_v2/build\.gradle\.kts$') -or
    (Test-AnyChangedFile 'wear/build\.gradle\.kts$') -or
    (Test-AnyChangedFile 'docs/OPEN_SOURCE[a-z.]*\.md$') -or
    (Test-AnyChangedFile 'docs/legal/oss-notices\.json$') -or
    (Test-AnyChangedFile 'scripts/(docs/oss-licenses\.psd1|docs/generate-oss-notices\.ps1|docs/OssDependencyParser\.ps1|quality/assert-oss-notices\.ps1)$')
)

Write-Host "post-change: $resolvedChangeType | $File -> $Target" -ForegroundColor Yellow

# S1372: keyed on the whole set - a resource file is rarely the first path a caller names, and
# resolving the source set from $File alone judges the wrong flavor in a mixed close.
# S1209: among resource files, a changed strings file wins. Both consumers below are strings gates, and
# a flavor source set almost never carries its own values/ dir - a close that names a layout under
# src/<flavor>/res and its keys under src/main/res used to audit the flavor and fail on locale dirs that
# do not exist, reporting a defect in the caller's change that was really a resolution bug here.
$resourceSourceSet = $null
$resourceSourceSetFile = Get-FirstChangedFileMatch '^[^/]+/src/([^/]+)/res/values[^/]*/strings[^/]*\.xml$'
if (-not $resourceSourceSetFile) {
    $resourceSourceSetFile = Get-FirstChangedFileMatch '^[^/]+/src/([^/]+)/res/'
}
if ($resourceSourceSetFile -and $resourceSourceSetFile -match '^[^/]+/src/([^/]+)/res/') {
    $resourceSourceSet = $Matches[1]
}

if ($SkipScan) {
    Write-Host "  [compat] -SkipScan is deprecated; resolved ChangeType=$resolvedChangeType" -ForegroundColor DarkGray
}

if ($runsStringsAudit) {
    # S1193: an absent -KeyPrefix used to SKIP this step, so the parity gate only ran when a caller
    # happened to pass a prefix. Nobody ever swept the whole catalog, and 21 keys reached the shipping
    # app untranslated. No prefix now means audit everything, not audit nothing.
    $auditPrefix = if ([string]::IsNullOrWhiteSpace($KeyPrefix)) { '*' } else { $KeyPrefix }
    # A strings edit under src/<flavor>/res must be audited against that flavor's locale dirs. Auditing
    # main instead would report a clean pass over files the change never touched.
    $auditSourceSet = if ($resourceSourceSet) { $resourceSourceSet } else { 'main' }
    Invoke-Gate "strings-audit" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/check_strings_localized.ps1") `
            -Module $Module -SourceSet $auditSourceSet -KeyPrefix $auditPrefix
    }
}
else {
    Skip-Step "strings-audit" "not applicable for ChangeType $resolvedChangeType"
}

# S1627: name the strings that do not yet reach all thirteen declared locales. Advisory on purpose -
# the refusal lives at the pre-release stage, where one bulk round trip clears every key of the
# release at once, so failing a ticket's close here would demand ten translations per key for no
# shipping benefit. Silence is what let 1887 keys accumulate, so the count is printed either way.
if ($runsStringFormatGate) {
    Invoke-AdvisoryStep "new-lexeme-count" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/utils/list-new-lexemes.ps1") -Module $Module
    } "advisory (new strings not yet in every locale; the pre-release stage translates them in bulk)"
}
else {
    Skip-Step "new-lexeme-count" "not applicable - no changed file is a strings resource file"
}

if ($runsStringFormatGate) {
    Invoke-Gate "string-format-gate" {
        $a = @(
            '-NoProfile',
            '-File',
            (Join-Path $root "scripts/quality/assert-string-format.ps1"),
            '-Gate',
            '-Module',
            $Module
        )
        if (-not [string]::IsNullOrWhiteSpace($resourceSourceSet)) {
            $a += @('-SourceSet', $resourceSourceSet)
        }
        & $pwsh @a
    }
}
else {
    Skip-Step "string-format-gate" "not applicable - no changed file is a strings resource file"
}

if ($runsTicketLogAudit) {
    # S1338: -Quiet suppressed exactly the File:Line list needed to fix a violation, so a FAIL here
    # reported that something was wrong and nothing about where.
    $ticketLogArgs = @(
        '-NoProfile',
        '-File',
        (Join-Path $root "scripts/quality/assert-no-ticket-logs.ps1"),
        '-Gate'
    )
    # S1912: hand the gate the changed set under -ScopeToFile, the same way the ratchets and detekt
    # already get it. It then judges forbidden log ids on THIS change and returns 3 - not 1 - when the
    # only remaining complaint is that some other ticket sits in BlockNeedUserTest without its probe.
    # That window is minutes long and its owner is another session; charging this closure for it made
    # CLAUDE.md section 12's promise ("other tickets' in-flight WIP does not block the close") false
    # for this one gate, and blocked every session in the tree at once (S1889, S1895).
    if ($ScopeToFile -and $changedFiles.Count -gt 0) {
        $ticketLogArgs += @('-ChangedFiles', ($changedFiles -join ','))
    }
    # The gate runs first and the verdict is chosen from its exit code, because only code 3 is
    # advisory. Deciding inside Invoke-AdvisoryStep is not an option: its catch block turns any
    # exception into a SKIP, so raising one for code 1 would downgrade this change's own forbidden
    # log id to an advisory - weakening the gate instead of scoping it.
    $ticketLogOutput = & $pwsh @ticketLogArgs 2>&1 | Out-String
    $ticketLogExit = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
    if (-not [string]::IsNullOrWhiteSpace($ticketLogOutput)) { Write-Host $ticketLogOutput.TrimEnd() }

    if ($ScopeToFile -and $ticketLogExit -eq 3) {
        Invoke-AdvisoryStep "ticket-log-audit" { $global:LASTEXITCODE = 3 } `
            -AdvisoryDetails ("a ticket other than this change's sits in BlockNeedUserTest without its probe. " +
                "Its author owns that probe - it belongs at the entry of their changed flow - so this closure is " +
                "not charged for it. Run assert-no-ticket-logs.ps1 with no -ChangedFiles for the project-wide verdict.")
    }
    else {
        # Code 1 (a forbidden log id in the changed set) and code 2 (the gate could not run) both
        # stay fatal, scoped or not.
        Invoke-Gate "ticket-log-audit" { $global:LASTEXITCODE = $ticketLogExit }
    }
}
else {
    Skip-Step "ticket-log-audit" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsAcceptanceProbeGate) {
    Invoke-Gate "acceptance-probe-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-ticket-acceptance-probes.ps1") -Gate
    }
}
else {
    Skip-Step "acceptance-probe-gate" "not applicable - no acceptance-probe or catalog mutation script changed"
}

if ($runsDocPinsSync) {
    Invoke-Gate "doc-pins-sync" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/generate-toolchain-pins.ps1") -Check
    }
}
else {
    Skip-Step "doc-pins-sync" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsDocPinDrift) {
    Invoke-Gate "doc-pin-drift" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-doc-pin-drift.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "doc-pin-drift" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsDocScriptReferences) {
    Invoke-Gate "doc-script-references" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-script-references.ps1"), '-Docs', '-Quiet')
        # Under -ScopeToFile the gate judges only the documents this change touched, so another
        # session's in-flight document cannot fail this closure. The gate itself widens back to the
        # whole corpus when the set carries a .ps1.
        if ($ScopeToFile -and $changedFiles.Count -gt 0) {
            $a += @('-ChangedFiles', ($changedFiles -join ','))
        }
        & $pwsh @a
    }
}
else {
    Skip-Step "doc-script-references" "not applicable - no changed file is a corpus document or a script"
}

# S1356: fatal, never advisory. The whole defect was that absorbing another ticket's debt produced
# no signal at all - a warning here would reproduce it politely. Pure text, no gradle.
if ($runsBaselineAbsorptionGate) {
    Invoke-Gate "detekt-baseline-absorption" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-detekt-baseline-absorption.ps1") -Gate
    }
}
else {
    Skip-Step "detekt-baseline-absorption" "not applicable - no detekt baseline among the changed files"
}

# S0826: a project-wide gate without per-file delta support runs advisory (warn, non-fatal)
# under -ScopeToFile; fatal otherwise. Since S0850 only icon-inventory-sync still uses this -
# the count-ratchet gates all judge FATAL per-file deltas.
# S1598: the fatal branch dispatches to Invoke-Gate, not Invoke-Step - these are gates, and
# Invoke-Step is now reserved for the two mutating steps, where ending the run on the spot is
# the intended behaviour. Naming it here by string is why the rename had to be made by hand.
$ratchetRunner = if ($ScopeToFile) { 'Invoke-AdvisoryStep' } else { 'Invoke-Gate' }

# S0848 Phase 02: start the gradle-backed detekt gate as a background thread job BEFORE the
# fast lexical/ratchet gates, then join it after them. detekt does not depend on the lexical
# gates, so overlapping turns wall-clock from (lexical + detekt) into ~max(lexical, detekt).
# Verdict and exit code stay identical to the serial run. The try/finally below guarantees the
# job is stopped even when a lexical gate fails and Invoke-Step calls exit (verified: a finally
# runs before exit propagates), so no orphan detekt/gradle launcher survives a fail-fast close.
# S1338 phase 04 step 04.7: the preflight runs BEFORE the gradle detekt gate is even started.
# S1595 made it FATAL, and that is the change that actually recovers the round-trip. It used to be
# advisory, so a finding it caught was still paid for in full: the gradle job started anyway and
# failed on the same finding ~87 s later. Invoke-Step exits on a non-zero child, so the job below
# is never started - and since S1595 the preflight's exit 1 comes from the REAL analyser run over
# the changed files (detekt-scoped.ps1), whose verdict was measured finding-for-finding identical
# to the whole-module gate's on the same set.
#
# Only exit 1 is fatal, and the preflight only returns it when the analyser actually ran. If the
# analyser could not start, the preflight degrades to its lexical scan and exits 0 whatever that
# scan found - a lexical guess must never abort a closure, and the gate below still judges.
# Formatting is corrected before it is judged. Measured on this repo: two closures in one session
# failed on ImportOrdering and MatchingDeclarationName alone - findings a formatter rewrites without
# a decision - and each failure cost a full second pass over every gate. The housekeeping pass runs
# the same analyser over the same files with the ktlint ruleset in auto-correct mode, takes ~2 s, and
# never renders a verdict: it always exits 0, and the preflight below is still what can fail.
if ($runsDetektPreflight -and $changedFiles.Count -gt 0) {
    Invoke-Step "detekt-format" {
        & $pwsh '-NoProfile' '-File' (Join-Path $root "scripts/quality/detekt-scoped.ps1") `
            '-Fix' '-ChangedFiles' ($changedFiles -join ',')
        # A housekeeping pass that could not run (no java, cold dependency cache) must not abort a
        # closure - it formats nothing and the preflight below still judges everything. The child
        # prints its own reason, so swallowing the code here hides no evidence.
        $global:LASTEXITCODE = 0
    }
}

if ($runsDetektPreflight -and $changedFiles.Count -gt 0) {
    Invoke-Gate "detekt-preflight" {
        & $pwsh '-NoProfile' '-File' (Join-Path $root "scripts/quality/detekt-preflight.ps1") `
            '-ChangedFiles' ($changedFiles -join ',') '-Gate'
    }
}

# S1598: collecting failures instead of exiting on the first one would have restored the
# ~87 s the preflight exists to save - the job below would start even though the preflight
# already ran the real analyser over the same files and already named the same findings.
# The preflight's verdict therefore still suppresses the job, and the gate below reports
# SKIP naming it, so the run stays honest about what was and was not judged.
$detektPreflightFailed = @($script:FatalFindings | Where-Object { $_.Label -eq 'detekt-preflight' }).Count -gt 0

$detektJob = $null
if ($runsDetektGate -and -not $detektPreflightFailed) {
    $detektArgs = @(
        '-NoProfile'
        '-File'
        (Join-Path $root "scripts/quality/assert-detekt.ps1")
        '-Gate'
    )
    if ($PSBoundParameters.ContainsKey('Module')) {
        $detektArgs += @('-Module', $Module)
    }
    if ($ScopeToFile) {
        # diff-scope to this change: detekt fails only on findings in -File, not on
        # other tickets' WIP that also sits above baseline on the dirty tree.
        # S1184: one comma-joined argument, never one array element per file. `pwsh -File`
        # binds only the first element to [string[]] and rejects the rest as positional,
        # which is why a multi-file closure died at the first scoped gate. Every consumer
        # comma-splits (Expand-ChangedFiles / Measure-ChangedFileGrowth).
        $detektArgs += @('-ChangedFiles', ($changedFiles -join ','))
    }
    $detektJob = Start-ThreadJob -Name 'detekt-gate' -ScriptBlock {
        param($PwshExe, $Argv)
        $out = & $PwshExe @Argv 2>&1 | Out-String
        [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out }
    } -ArgumentList $pwsh, $detektArgs
}

try {

if ($runsNeuroslopGate) {
    # S0850: under -ScopeToFile every child judges a real delta on the changed file (growth vs
    # HEAD) and the gate stays FATAL - a NEW violation in this change fails, while other
    # tickets' pre-existing findings no longer trip it (mirrors flavor-flags/deprecated-pm).
    Invoke-Gate "neuroslop-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-neuroslop.ps1"), '-Gate')
        if ($ScopeToFile) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "neuroslop-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsOrientationFeatureGate) {
    Invoke-Gate "orientation-implied-feature-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-orientation-implied-feature.ps1") -Gate
    }
}
else {
    Skip-Step "orientation-implied-feature-gate" "not applicable - no changed file is an AndroidManifest.xml"
}

if ($runsOrientationLayoutPairingGate) {
    Invoke-Gate "orientation-layout-pairing-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-orientation-layout-pairing.ps1") -Gate
    }
}
else {
    Skip-Step "orientation-layout-pairing-gate" "not applicable - no changed file is a manifest or a landscape layout"
}

if ($runsFgsGate) {
    Invoke-Gate "fgs-notification-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-fgs-notifications.ps1") -Gate
    }
}
else {
    Skip-Step "fgs-notification-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsFocusHighlightGate) {
    Invoke-Gate "focus-highlight-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-focus-highlight.ps1") -Gate
    }
}
else {
    Skip-Step "focus-highlight-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsDialogCancelGate) {
    Invoke-Gate "dialog-cancel-style-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-dialog-cancel-style.ps1") -Gate
    }
}
else {
    Skip-Step "dialog-cancel-style-gate" "not applicable - no changed file is a dialog/bottom-sheet layout"
}

if ($runsRtlLayoutGate) {
    # Scoped to the changed layouts under -ScopeToFile: the project-wide baseline says how many
    # absolute attributes may exist at all, and a file being edited is not the one to raise it.
    Invoke-Gate "rtl-layout-attrs-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-rtl-layout-attrs.ps1"), '-Gate')
        if ($ScopeToFile) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "rtl-layout-attrs-gate" "not applicable - no changed file is a layout"
}

if ($runsListenerSymmetryGate) {
    # S0850: under -ScopeToFile the gate judges per-file imbalance growth vs HEAD and stays
    # FATAL - an edit that degrades symmetry in this change fails, unrelated pre-existing
    # imbalance elsewhere does not.
    Invoke-Gate "listener-symmetry-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-listener-symmetry.ps1"), '-Gate')
        if ($ScopeToFile) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "listener-symmetry-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsAllFeaturesGate) {
    Invoke-Gate "all-features-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-allfeatures-sync.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "all-features-gate" "not applicable - no changed file is an ALL_FEATURES artifact"
}

if ($runsSettingsDocGate) {
    Invoke-Gate "settings-doc-sync-gate" {
        # S1338 step 04.7: under -ScopeToFile hand it the changed set so its ~28 s gradle stage
        # runs only when a manifest input actually moved. Same rule as the detekt branch above -
        # an unscoped run (release, CI) keeps the strict project-wide judgement.
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-settings-doc-sync.ps1"), '-Gate')
        if ($ScopeToFile -and $changedFiles.Count -gt 0) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "settings-doc-sync-gate" "not applicable - no changed file is a settings surface or settings doc"
}

if ($runsHowToPathGate) {
    Invoke-Gate "howto-settings-paths-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-howto-settings-paths.ps1") -Gate
    }
}
else {
    Skip-Step "howto-settings-paths-gate" "not applicable - no changed file is a HOW_TO or narrative settings-path guide"
}

# S1939: icon-inventory-sync and doc-icons-sync moved to the release-scope runner
# (scripts/quality/assert-release-scope-gates.ps1, run by /spec-prerelease). Both judge a
# repository-wide inventory rather than the changed file - icon-inventory was already advisory
# under -ScopeToFile for exactly that reason, because its legend re-render reads live app
# strings and another ticket's string WIP showed up as drift here. Measured over a month:
# 896 and 237 closures, 85 and 7 actual executions, zero findings between them.

if ($runsScriptCheatsheetGate) {
    # Advisory under -ScopeToFile: the check regenerates from every script, so
    # unrelated script-param WIP on a dirty tree could read as cheatsheet drift
    # not attributable to this change. Strict on a full run.
    & $ratchetRunner "script-cheatsheet-sync-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-script-cheatsheet-sync.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "script-cheatsheet-sync-gate" "not applicable - no changed file is a repo script or the script cheatsheet"
}

if ($runsFlavorMatrixDocGate) {
    # Strict even under -ScopeToFile: the gate judges each declared table against the generated
    # snapshot, so its verdict is attributable to the tables named in this change and never to
    # another ticket's in-flight drift. Nothing about it is a project-wide count ratchet.
    Invoke-Gate "flavor-matrix-doc-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-flavor-matrix-docs.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "flavor-matrix-doc-gate" "not applicable - no changed file is the flavor grid, the generated matrix, or a doc carrying a checked flavor table"
}

if ($runsOssNoticesGate) {
    # Strict even under -ScopeToFile: both findings are attributable to the change that fired
    # them - a coordinate this change declared, or a page this change edited. Not a count ratchet.
    Invoke-Gate "oss-notices-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-oss-notices.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "oss-notices-gate" "not applicable - no changed file is a build file, the licence manifest, the notice pipeline, or a rendered notice page"
}

if ($runsRuleDigestGate) {
    Invoke-Gate "rule-digest-sync-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-rule-digest-sync.ps1") -Gate
    }
}
else {
    Skip-Step "rule-digest-sync-gate" "not applicable - no changed file is the rule authority, a full digest or the pointer"
}

# S1338 phase 05: the document-registry trigger. Reads docs/DOCUMENT_REGISTRY.jsonl and reports
# every record whose `paths` cover a file in this change - a registered document moved, so its
# siblings (other locales, the site export, the mirrored page) may now disagree with it. Fires on
# registered paths only, never on every closure, so it stays real where it fires.
$registryPath = Join-Path $root 'docs/DOCUMENT_REGISTRY.jsonl'
if (Test-Path -LiteralPath $registryPath) {
    # `-replace '^\./'`, never `TrimStart('./')`: TrimStart takes a CHAR SET, so it ate the
    # leading dot of `.claude/commands/*.md` and every command-file edit missed its record.
    $normalizedChanged = @($changedFiles | ForEach-Object { ($_ -replace '\\', '/') -replace '^\./', '' })
    $matchedRecords = @()
    foreach ($line in (Get-Content -LiteralPath $registryPath -Encoding UTF8)) {
        $trimmed = "$line".Trim()
        if (-not $trimmed) { continue }
        try { $record = $trimmed | ConvertFrom-Json } catch { continue }
        if (-not ($record.PSObject.Properties.Name -contains 'paths')) { continue }
        # A generated document is owned by its generator and its own sync gate; asking the
        # operator to acknowledge it teaches nothing. Skipping it is why the cheatsheet does
        # not raise an advisory on every closure that changes a param block.
        if (($record.PSObject.Properties.Name -contains 'generated') -and $record.generated) { continue }
        $hitPaths = @()
        $hitPatterns = @()
        foreach ($registered in @($record.paths)) {
            $reg = ($registered -replace '\\', '/')
            foreach ($changed in $normalizedChanged) {
                if ($changed -ieq $reg -or $changed -ilike "$reg/*" -or $changed -ilike $reg) { $hitPatterns += $reg }
                # Exact path, a directory prefix, or a glob - `repository-rules` registers
                # `.claude/commands/*.md`, and a literal-only match silently missed every
                # command-file edit, which is the largest registered surface in the repo.
                if ($changed -ieq $reg -or $changed -ilike "$reg/*" -or $changed -ilike $reg) { $hitPaths += $changed }
            }
        }
        if ($hitPaths.Count -gt 0) {
            $matchedRecords += [pscustomobject]@{
                Id     = [string]$record.id
                Title  = [string]$record.title
                Files  = @($hitPaths | Select-Object -Unique)
                # Siblings are the registered entries this change did NOT touch - listing the
                # pattern that just matched as something to go and update is noise.
                Others = @(@($record.paths) | Where-Object { ($_ -replace '\\', '/') -notin $hitPatterns })
            }
        }
    }

    if ($matchedRecords.Count -eq 0) {
        Skip-Step "document-registry" "not applicable - no changed file is a registered document"
    }
    else {
        # S1340: pwsh -File does not re-split a quoted CSV into array elements (feedback_string_array_param_csv_via_file.md) -
        # split each bound element on comma too, so `-RegistryAck "a,b"` and `-RegistryAck a,b` both work from the Bash tool.
        $ackSet = @($RegistryAck | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        $ackAll = ($ackSet -contains 'all')
        $unacked = @($matchedRecords | Where-Object { -not $ackAll -and $_.Id -notin $ackSet })
        foreach ($rec in $matchedRecords) {
            Write-Host ("  registry: {0} ({1}) <- {2}" -f $rec.Id, $rec.Title, ($rec.Files -join ', '))
            if ($rec.Others.Count -gt 0) {
                Write-Host ("    siblings that may need the same edit: {0}" -f ($rec.Others -join ', '))
            }
        }
        if ($unacked.Count -eq 0) {
            Invoke-Gate "document-registry" {
                Write-Host ("  acknowledged: {0}" -f (($matchedRecords | ForEach-Object { $_.Id }) -join ', '))
                $global:LASTEXITCODE = 0
            }
        }
        else {
            Invoke-AdvisoryStep "document-registry" {
                $global:LASTEXITCODE = 1
            } -AdvisoryDetails ("registered document(s) changed and not acknowledged: " +
                (($unacked | ForEach-Object { $_.Id }) -join ', ') +
                ". Read them, update the siblings listed above, then re-run with -RegistryAck '" +
                (($unacked | ForEach-Object { $_.Id }) -join ',') + "'.")
        }
    }
}
else {
    Skip-Step "document-registry" "cannot verify - docs/DOCUMENT_REGISTRY.jsonl not found"
}

# S1939: the S1216 device-profile matrix gate moved to the release-scope runner. It ran on 1040
# of 1053 closures over a month - 33.2 minutes of closure time - and reported ONE finding, and it
# was already advisory under -ScopeToFile because it reads the whole matrix and another ticket's
# settings WIP would otherwise block this closure. A gate that cannot attribute its finding to the
# change in front of it is not judging that change; it is judging the tree, which is what
# scripts/quality/assert-release-scope-gates.ps1 does once, over the whole release scope.

# S1540: the fourth edit adding a launcher setting needs - the line in the launcher reset - had no gate,
# so a forgotten one reached the user as a reset that leaves that setting alone. Stays FATAL under
# -ScopeToFile, unlike the matrix gate above: it reads exactly two files and judges one rule between
# them, so another ticket's WIP cannot make it fail unless that WIP is itself the defect.
Invoke-Gate "launcher-reset-coverage-gate" {
    & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-launcher-reset-coverage.ps1") -Gate -Quiet
}

# S1639: FATAL rather than advisory. The registry beside the gate already carries every finding the tree
# holds today, so a failure here is new by construction and belongs to the change being closed.
if ($runsGsonContractGate) {
    Invoke-Gate "gson-persistence-contract-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-gson-persistence-contract.ps1"), '-Gate')
        # A changed keep rule can unpin any model in its module, so a rules edit is judged project-wide.
        # Only a pure Kotlin change may be scoped, and then to its Kotlin files alone - scoping to a set
        # that holds no source would collect no serialization point at all and report green for nothing.
        $kotlinChanged = @($changedFiles | Where-Object { $_ -match '\.kt$' })
        if ($ScopeToFile -and $kotlinChanged.Count -gt 0 -and -not (Test-AnyChangedFile 'proguard-.*\.pro$')) {
            $a += @('-ChangedFiles', ($kotlinChanged -join ','))
        }
        & $pwsh @a
    }
}
else {
    Skip-Step "gson-persistence-contract-gate" "not applicable - no changed file is a Kotlin source or an obfuscation rules file"
}

# S1915: links the changed resources. Routed through check-standard-fast.ps1 for the same reason as the
# gate below - the helper takes BUILD.LOCK, and calling gradlew here would race every sibling session
# (CLAUDE.md Rule 23).
if ($runsResourceLinkGate) {
    $resourceLinkFlavors = Get-ResourceLinkFlavors -TargetModule $Module
    Write-Host "  resource-link: module $Module, flavor(s) $($resourceLinkFlavors -join ', ')" -ForegroundColor DarkGray
    Invoke-Gate "resource-link-gate" {
        foreach ($resourceFlavor in $resourceLinkFlavors) {
            & $pwsh -NoProfile -File (Join-Path $root "scripts/builders/check-standard-fast.ps1") `
                -Mode Resources -Module $Module -Flavor $resourceFlavor
            # Stop at the first red. Without this the loop would run on and Invoke-Gate would read the
            # LAST flavor's exit code, turning an earlier failure into a pass.
            if ($LASTEXITCODE -ne 0) { return }
        }
    }
}
else {
    Skip-Step "resource-link-gate" "not applicable - no changed file is a resource or manifest under a source set"
}

# S1844: compiles app_v2's instrumented set. Routed through check-standard-fast.ps1 rather than gradlew
# so BUILD.LOCK is acquired by the same helper every other build path uses (CLAUDE.md Rule 23).
if ($runsAndroidTestCompileGate) {
    Invoke-Gate "androidtest-compile-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/builders/check-standard-fast.ps1") -Mode AndroidTest -Module $Module
    }
}
else {
    Skip-Step "androidtest-compile-gate" "not applicable - no changed file is under src/androidTest or a Room schema source"
}

# S0848 Phase 02: join the detekt job started before the lexical gates. Preserves the old
# inline verdict surface (failing rule lines printed, fatal on FAIL). Nulling $detektJob right
# after the drain keeps the finally cleanup a no-op once the job has already been received.
if ($runsDetektGate -and $detektPreflightFailed) {
    Skip-Step "detekt-gate" "detekt-preflight already failed on the same files - fix those findings first"
}
elseif ($runsDetektGate) {
    Invoke-Gate "detekt-gate" {
        $r = Receive-Job -Job $detektJob -Wait -AutoRemoveJob
        $script:detektJob = $null
        if ($r -and -not [string]::IsNullOrWhiteSpace($r.Output)) {
            Write-Host ($r.Output.TrimEnd())
        }
        $global:LASTEXITCODE = if ($r) { [int]$r.ExitCode } else { 1 }
    }
}
else {
    Skip-Step "detekt-gate" "not applicable for ChangeType $resolvedChangeType"
}

}
finally {
    # Guarantee no orphan detekt/gradle launcher survives an early exit from the gate block.
    if ($detektJob) {
        try { Stop-Job -Job $detektJob -ErrorAction SilentlyContinue } catch { }
        try { Remove-Job -Job $detektJob -Force -ErrorAction SilentlyContinue } catch { }
    }
    # Tier-2 coordination lock (CLAUDE.md Rule 23): post-change.ps1 is the "logical change is
    # done" checkpoint every code-editing skill already calls, so releasing CODE.LOCK here makes
    # release automatic - skills only need to acquire it (scripts/utils/enter-code-lock.ps1)
    # before their first source edit. Safe no-op if nothing was ever acquired in this run.
    try {
        . (Join-Path $root "scripts/utils/agent-lock.ps1")
        Exit-AgentLock -Name Code
    }
    catch {
        Write-Host "  [code-lock-release] WARN - could not release CODE.LOCK: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# S1598: the barrier. Sits after the finally so CODE.LOCK is released on a failed run
# too, and before the first mutating step so "a gate failed" still means nothing was
# written. Exits 1 with the full list of failed gates - the whole point of collecting
# them rather than ending the run at the first one.
Test-FatalFindings

# S1338: the two mutating steps run only after every gate has passed. Before this
# they ran first, so 430 failed closures still paid for catalog-sync and still
# wrote a changelog row - 183 duplicate rows for changes that never closed. Running
# them last also makes the facade idempotent: re-running after fixing a gate
# failure produces exactly one row, not two.
if ($runsCatalogSync) {
    Invoke-Step "catalog-sync" {
        # S0848: incremental scan - only the changed file gets a fresh git last-touched;
        # the rest reuse their prior JSONL date, avoiding a per-file `git log` storm.
        & $pwsh -NoProfile -File (Join-Path $root "scripts/catalog_sync.ps1") -Module $Module -ChangedFiles ($catalogChangedFiles -join ',')
    }
}
else {
    Skip-Step "catalog-sync" "not applicable for ChangeType $resolvedChangeType"
}

# S1338: the changelog row is written LAST, after every gate and after the only other
# mutating step. Writing it first, or even before catalog-sync, meant a run that failed
# afterwards still left a row claiming the change had closed - and a re-run then added a
# second. Last position makes "there is a row" equivalent to "the closure passed", and the
# catalog index it follows is a gitignored artifact that is safe to rebuild on a re-run.
Invoke-Step "dev-log" {
    # S1338 follow-up (2026-08-08): a -Files close ran every gate over the whole set but wrote a
    # row naming only the primary, so the changelog understated its own change and the set could
    # not be recovered from it. That silently punished batching - the cheap way to close - and
    # pushed callers back to one post-change run per file, which is what re-ran the detekt gate
    # on an unchanged tree 530 times in three weeks. Still ONE row per logical change (Rule 12
    # journalling granularity): the extra files go in the description, not in extra rows.
    $logDescription = $Description
    $deletedLogEntries = @($deletedFiles | ForEach-Object { "$_ (deleted)" })
    $logEntries = @($changedFiles) + $deletedLogEntries
    $primaryLogEntry = if ($changedFiles.Count -gt 0) { $File } else { "$File (deleted)" }
    if ($logEntries.Count -gt 1) {
        $others = @($logEntries | Where-Object { $_ -ne $primaryLogEntry })
        # Cap the list: a 20-file close would otherwise write a row longer than the table it sits in.
        $shown = @($others | Select-Object -First 6)
        $suffix = if ($others.Count -gt $shown.Count) { ", +$($others.Count - $shown.Count) more" } else { '' }
        $logDescription = "$Description [set of $($logEntries.Count): $($shown -join ', ')$suffix]"
    }
    elseif ($deletedLogEntries.Count -gt 0) {
        $logDescription = "$Description [deleted: $primaryLogEntry]"
    }
    & $pwsh -NoProfile -File (Join-Path $root "scripts/add_to_dev_log.ps1") $File $Target $logDescription
}

Skip-Step "feature-docs" "skill-owned; evaluate only for new public capability"
Skip-Step "functionality-log" "skill-owned; evaluate only for user-visible behaviour change"

Skip-Step "spec-catalog-sync" "skill-owned; run only on spec status transition"

$totalSw.Stop()
# S1338: the bare word PASS is reserved for a run where nothing failed and
# nothing was downgraded. An advisory gate that found something used to be
# invisible in the verdict, so the facade printed PASS on 19% of runs that
# contained a gate failure - and 66% of callers read only the tail.
$elapsedMs = [int]$totalSw.Elapsed.TotalMilliseconds
Write-SkippedSummary
if ($script:AdvisoryFindings.Count -gt 0) {
    Write-Host ("post-change: PASS WITH ADVISORIES ($($script:AdvisoryFindings.Count)) " +
        "($resolvedChangeType, $elapsedMs ms)") -ForegroundColor Yellow
    foreach ($advisory in $script:AdvisoryFindings) {
        Write-Host "  advisory: $advisory" -ForegroundColor Yellow
    }
    Write-Host "  These gates found something but could not attribute it to this change. Verify your files." -ForegroundColor Yellow
}
else {
    Write-Host "post-change: PASS ($resolvedChangeType, $elapsedMs ms)" -ForegroundColor Green
}
exit 0
