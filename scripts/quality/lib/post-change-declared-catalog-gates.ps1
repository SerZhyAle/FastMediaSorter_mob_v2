<#
.SYNOPSIS
    The declared-catalog gates of scripts/post-change.ps1 - each one judging a hand-maintained
    declaration against the module it claims to describe.

.DESCRIPTION
    Six gates with one shape: a file in the repository declares what the product contains - the
    watch settings pair, the mini-program route catalog, the wear pre-release walk list, the phone UI
    sweep list, the bridge scenario registry, the ratchet-baseline inventory - and the gate refuses a
    member the declaration names nowhere. All six are PER-TICKET by Rule 33, and all six are SCOPED with
    -ChangedFiles for the same reason: each reads its catalog whole, so an unscoped FATAL would refuse
    this closure over a neighbour's unclassified work.

    One gate of another shape lives beside the inventory gate because it enforces the same contract
    (CHECK-BASELINE rule 4): the lint baseline absorption gate (S3459), a set comparison that needs
    no scoping because it reads only the two files that trigger it.

    Last, the check-subject gate (S3440): the declaration is the shrink-only list of check scripts
    that predate the subject line, and the member is every check script in the tree.

    Extracted here because the facade crossed the 2000-line ceiling of CLAUDE.md Rule 2 (S2380). It is
    DOT-SOURCED, not invoked, so $root, $pwsh, $changedFiles, $ScopeToFile, Invoke-Gate, Skip-Step and
    Test-AnyChangedFile all resolve in the caller's scope exactly as they did inline - the extraction
    moves lines, it changes nothing the closure does.

    Sourced, never executed directly, so it declares no exit codes of its own.
#>

# S2093: a watch setting present on one side of the phone/watch pair and absent on the other. The list
# lived in four independently maintained places, so a one-sided setting diverged in silence and was
# found only when the owner could not see it where it was expected. Here and not only in the fg batch:
# the closure is where a ticket is judged, and a check the closure never runs cannot stop the ticket
# that skipped a side.
#
# S2824 removed the claim that stood here - that a sibling session's WIP could not fail this gate
# unless the WIP was itself the defect. S2820 refuted it: a changed set lying entirely in
# wear/../ui/streams/ was refused by a half-written pair another session was mid-way through, and the
# gate journal shows 40 such refusals in seventeen days, nineteen of them consecutive on one night
# (temp/metrics/gate-executions.jsonl). What holds instead is that the gate charges
# its findings only when a file it declares as an input is in the changed set. The trigger stays wide
# because the path list lives in the gate (S1621) and duplicating it here would drift; it fires on 825
# files while only 24 can be charged, and the gap between those two numbers is what code 3 covers.
if (Test-AnyChangedFile '(^|/)wear/|Wear[A-Za-z]*\.kt$|SettingsDocScopeCatalog\.kt$') {
    $argvWearSettingsParity = @('-NoProfile', '-File',
        (Join-Path $root "scripts/quality/assert-wear-settings-parity.ps1"), '-Gate', '-Quiet')
    if ($ScopeToFile -and $changedFiles.Count -gt 0) {
        $argvWearSettingsParity += @('-ChangedFiles', ($changedFiles -join ','))
    }
    Invoke-FixedInputGate "wear-settings-parity-gate" $argvWearSettingsParity 'assert-wear-settings-parity.ps1'
}
else {
    Skip-Step "wear-settings-parity-gate" "not applicable - no changed file touches the watch module or a watch-settings surface"
}

# S2579: a watch mini-program's canonicalKey that is neither a phone route key nor a declared
# watch-only program. The enum's own KDoc calls that key the phone's, four of five entries obeyed it
# and the fifth did not, and nothing looked - the watch's test compares the watch against itself.
# Fatal on a divergence between the three files it reads, and since S2824 it declines to charge one
# when none of them is in the changed set, for the reason recorded at the gate above.
if (Test-AnyChangedFile '(^|/)wear/|InternalRouteCatalog\.kt$') {
    $argvWearCanonicalKeyParity = @('-NoProfile', '-File',
        (Join-Path $root "scripts/quality/assert-wear-canonical-key-parity.ps1"), '-Gate', '-Quiet')
    if ($ScopeToFile -and $changedFiles.Count -gt 0) {
        $argvWearCanonicalKeyParity += @('-ChangedFiles', ($changedFiles -join ','))
    }
    Invoke-FixedInputGate "wear-canonical-key-parity-gate" $argvWearCanonicalKeyParity 'assert-wear-canonical-key-parity.ps1'
}
else {
    Skip-Step "wear-canonical-key-parity-gate" "not applicable - no changed file touches the watch module or the phone route catalog"
}

# S2621: a wear screen that is in neither the walked nor the excluded list of the pre-release walk is
# not opened by /spec-prerelease-wear and is not declared skipped either, so it ships unchecked in
# silence. Until now the only caller was the project-wide fg battery, which is not bound to any
# ticket's changed set: the three screens that produced this gate's first real failure were added by
# S2457, S2458 and S2516 on 2026-09-03/04, and the refusal was collected on 2026-09-05 and 2026-09-06
# by two uninvolved sessions running fg for unrelated work - one of which filed a duplicate ticket
# because the finding reaches whoever ran the battery rather than whoever added the screen.
#
# PER-TICKET by Rule 33: the subject is a screen this change added or renamed, and only its author
# knows whether it is a destination worth walking or an exclusion with a reason. Scoped rather than
# unconditional for the same reason the wear-mirrored-strings gate is: the gate reads the whole
# module, so an unscoped FATAL here would refuse this closure over a neighbour's unclassified WIP -
# reproducing the very complaint above, narrowed to sessions touching the watch.
if (Test-AnyChangedFile '(^|/)wear/.*Screen\.kt$|(^|/)scripts/devtest/wear-prerelease-screens\.json$') {
    $argvWearWalkContract = @('-NoProfile', '-File',
        (Join-Path $root "scripts/quality/assert-wear-walk-contract.ps1"), '-Gate')
    if ($ScopeToFile -and $changedFiles.Count -gt 0) {
        $argvWearWalkContract += @('-ChangedFiles', ($changedFiles -join ','))
    }
    Invoke-FixedInputGate "wear-walk-contract-gate" $argvWearWalkContract 'assert-wear-walk-contract.ps1'
}
else {
    Skip-Step "wear-walk-contract-gate" "not applicable - no changed file is a wear screen or the declared walk list"
}

# S2380: the phone side of the same gap, and the same placement verdict. `assert-ui-sweep-catalog.ps1`
# binds `scripts/devtest/ui-sweep-screens.json` to the activities and settings sections the module
# actually has; an activity added to neither the walk list nor the excluded list is invisible to the
# sweep AND to the reader of the catalog. It shipped wired into the fg battery alone, which is the
# population the gate-placement registry names as "either legitimately hand-run or an S2300 waiting to
# happen" - and this one is not hand-run: its subject is a screen a ticket just added, and only that
# ticket's author knows whether it is a destination worth walking or an exclusion with a reason.
#
# PER-TICKET by Rule 33. Scoped for the reason the wear gate above is: the gate reads the whole
# module, so an unscoped FATAL would refuse this closure over a neighbour's unclassified screen. The
# gate is fixed-input (S2824) and downgrades itself to advisory exit 3 when the set carries none of
# its declared inputs. That agreement needs the call site to honour code 3: both this gate and the
# wear one above were converted to fixed-input but left calling Invoke-Gate, which knows only pass
# and fail, so a closure whose set merely CONTAINED an Activity was refused by a gate that had just
# declined to charge it - measured 2026-09-20 on a set of eight probe removals. Both now route
# through Invoke-FixedInputGate, like the two gates at the top of this file.
if (Test-AnyChangedFile '(^|/)app_v2/src/main/.*Activity\.kt$|(^|/)scripts/devtest/ui-sweep-screens\.json$|(^|/)dev/ACTIVITY_CATALOG/app_v2\.jsonl$|(^|/)docs/settings/settings-manifest\.json$') {
    $argvUiSweepCatalog = @('-NoProfile', '-File',
        (Join-Path $root "scripts/quality/assert-ui-sweep-catalog.ps1"), '-Gate')
    if ($ScopeToFile -and $changedFiles.Count -gt 0) {
        $argvUiSweepCatalog += @('-ChangedFiles', ($changedFiles -join ','))
    }
    Invoke-FixedInputGate "ui-sweep-catalog-gate" $argvUiSweepCatalog 'assert-ui-sweep-catalog.ps1'
}
else {
    Skip-Step "ui-sweep-catalog-gate" "not applicable - no changed file is a phone activity, the declared sweep list, the activity catalog or the settings manifest"
}

# S2880: a route declared in either WearDataLayerPaths.kt with no scenario naming it and no recorded
# exclusion is the gap class S2861 measured - two of thirty-eight routes were named nowhere, and both
# were exactly where the campaign's only confirmed defect lives. PER-TICKET by Rule 33: the subject is
# the route catalog any bridge ticket may extend, so the author of the extension classifies it in the
# same change. Scoped rather than unconditional for the walk-contract reason: the gate reads both
# catalogs whole, and an unscoped FATAL here would refuse this closure over a neighbour's
# unclassified new route (S2621).
if (Test-AnyChangedFile '(^|/)WearDataLayerPaths\.kt$|(^|/)scripts/devtest/bridge-scenarios\.json$') {
    Invoke-Gate "bridge-scenario-coverage-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-bridge-scenario-coverage.ps1"), '-Gate')
        if ($ScopeToFile -and $changedFiles.Count -gt 0) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "bridge-scenario-coverage-gate" "not applicable - no changed file is a route catalog or the bridge scenario registry"
}

# S3438: a ratchet baseline with no row in scripts/quality/baseline-inventory.jsonl, or a row that
# declares a wholesale writer safe. PER-TICKET by Rule 33: only the author of a new baseline knows
# its shape and how it is written (contract CHECK-BASELINE rule 3), and the answer is cheapest in the
# change that creates the file. Fixed-input like the gates above: a finding is charged only when the
# inventory or that baseline file is in the changed set.
if (Test-AnyChangedFile 'baseline[^/]*\.(txt|ids|xml)$|(^|/)scripts/quality/(baseline-inventory\.jsonl|assert-baseline-inventory\.ps1)$') {
    $argvBaselineInventory = @('-NoProfile', '-File',
        (Join-Path $root "scripts/quality/assert-baseline-inventory.ps1"), '-Gate', '-Quiet')
    if ($ScopeToFile -and $changedFiles.Count -gt 0) {
        $argvBaselineInventory += @('-ChangedFiles', ($changedFiles -join ','))
    }
    Invoke-FixedInputGate "baseline-inventory-gate" $argvBaselineInventory 'assert-baseline-inventory.ps1'
}
else {
    Skip-Step "baseline-inventory-gate" "not applicable - no changed file is a ratchet baseline or the baseline inventory"
}

# S3459: the lint twin of detekt-baseline-absorption (S1356). check-lint.ps1 -Regenerate and the CI
# regenerate-lint-baseline dispatch both rewrite <module>/lint-baseline.xml from every live finding;
# this refuses an XML carrying an identifier the committed <module>/lint-baseline.ids does not.
# Fatal, never advisory: a warning would reproduce the silent absorption politely. Pure text.
if (Test-AnyChangedFile '(^|/)(app_v2|wear)/lint-baseline\.(xml|ids)$') {
    Invoke-Gate "lint-baseline-absorption" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-lint-baseline-absorption.ps1") -Gate
    }
}
else {
    Skip-Step "lint-baseline-absorption" "not applicable - no lint baseline or its identifier snapshot among the changed files"
}

# S3440: a check script that prints no `subject:` line naming what it checked (contract BUILD-EVIDENCE
# rule 1). PER-TICKET by Rule 33: only the author of a new check knows its subject, and the line is
# cheapest in the change that adds the check. Fixed-input like the gates above: a finding is charged
# only when its own check, the shrink-only baseline, the gate or the subject library is in the set.
if (Test-AnyChangedFile '(^|/)scripts/(builders/check-[^/]*|quality/assert-[^/]*|quality/lib/check-subject)\.ps1$|(^|/)scripts/quality/check-subject-baseline\.txt$') {
    $argvCheckSubject = @('-NoProfile', '-File',
        (Join-Path $root "scripts/quality/assert-check-subject.ps1"), '-Gate', '-Quiet')
    if ($ScopeToFile -and $changedFiles.Count -gt 0) {
        $argvCheckSubject += @('-ChangedFiles', ($changedFiles -join ','))
    }
    Invoke-FixedInputGate "check-subject-gate" $argvCheckSubject 'assert-check-subject.ps1'
}
else {
    Skip-Step "check-subject-gate" "not applicable - no changed file is a check script or the check-subject baseline"
}
