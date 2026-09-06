# S2604 - regression tests for the shared settings-doc input map.
#
# Subject: scripts/quality/lib/settings-doc-inputs.ps1, read by BOTH scripts/post-change.ps1
# (the settings-doc-sync-gate trigger) and scripts/quality/assert-settings-doc-sync.ps1 (its
# stage-2 and stage-4 delta predicates). The defect this guards: a 'docs/settings/' path prefix
# fired four stages for device-profile-nonpresettable.json, which feeds none of them, and the
# project-wide reference stage then failed the close on a sibling ticket's unrendered row.
#
# The docs/settings/ folder is enumerated at RUN TIME and every file must be classified here by
# name. A sixth file added later therefore fails this test instead of silently missing the
# narrowed trigger - that risk is the price of narrowing at all, and this is what converts it
# into a refusal.
#
# Hermetic: pure in-process function calls, no gradle, no writes. Exit 0 = all cases pass.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$testsRoot = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $testsRoot '..' '..')).Path
. (Join-Path $repoRoot 'scripts/quality/lib/settings-doc-inputs.ps1')

$script:pass = 0
$script:fail = 0

function Assert-Equal {
    param([Parameter(Mandatory)][string]$Name, $Expected, $Actual)
    if ($Expected -eq $Actual) {
        Write-Host ("PASS | {0} | {1}" -f $Name, $Actual)
        $script:pass++
    }
    else {
        Write-Host ("FAIL | {0} | expected: {1} | actual: {2}" -f $Name, $Expected, $Actual)
        $script:fail++
    }
}

# --- The five files of docs/settings/, each classified by name ---------------------------------
# Expected verdicts per file: does it trigger the gate at all, and does it feed the reference render.
$expected = @{
    'settings-manifest.json'            = @{ Artifact = $true;  Reference = $true }
    'settings-annotations.json'         = @{ Artifact = $true;  Reference = $true }
    'settings-scope-exclusions.json'    = @{ Artifact = $true;  Reference = $false }
    'howto-path-vocab.json'             = @{ Artifact = $true;  Reference = $false }
    'device-profile-nonpresettable.json' = @{ Artifact = $false; Reference = $false }
}

foreach ($name in ($expected.Keys | Sort-Object)) {
    $path = "docs/settings/$name"
    Assert-Equal "artifact  | $name" $expected[$name].Artifact (Test-SettingsDocArtifactInput -ChangedFiles @($path))
    Assert-Equal "reference | $name" $expected[$name].Reference (Test-SettingsReferenceInput -ChangedFiles @($path) -RepoRoot $repoRoot)
    # No file in this folder can move the manifest: the manifest is scanned from layouts and
    # strings, and every one of these is downstream of that scan or unrelated to it.
    Assert-Equal "manifest  | $name" $false (Test-SettingsManifestInput -ChangedFiles @($path) -RepoRoot $repoRoot)
}

# --- Nothing in the folder is unclassified -----------------------------------------------------
$settingsDir = Join-Path $repoRoot 'docs/settings'
$onDisk = @(Get-ChildItem -LiteralPath $settingsDir -File | ForEach-Object { $_.Name } | Sort-Object)
$unclassified = @($onDisk | Where-Object { -not $expected.ContainsKey($_) })
Assert-Equal 'every docs/settings file is classified' '' ($unclassified -join ', ')
$missing = @($expected.Keys | Where-Object { $onDisk -notcontains $_ } | Sort-Object)
Assert-Equal 'every classified file still exists' '' ($missing -join ', ')

# --- The measurement the advisory predicate rests on --------------------------------------------
# An annotations-only change matches no manifest input, so stage 2 is skipped - yet the reference
# IS rendered from the annotations. This is why the stage-4 downgrade keys off the RENDERER's
# inputs and not off "stage 2 was skipped": treating them as the same would ship a stale reference.
$annotations = @('docs/settings/settings-annotations.json')
Assert-Equal 'annotations-only is NOT a manifest input' $false (Test-SettingsManifestInput -ChangedFiles $annotations -RepoRoot $repoRoot)
Assert-Equal 'annotations-only IS a reference input'    $true  (Test-SettingsReferenceInput -ChangedFiles $annotations -RepoRoot $repoRoot)

# --- The other reference inputs, which are not in docs/settings/ --------------------------------
$otherReferenceInputs = @(
    'docs/SETTINGS_REFERENCE.md',
    'docs/SETTINGS_REFERENCE_RU.md',
    'docs/SETTINGS_REFERENCE_noLegal.md',
    'docs/icons/doc-icon-map.json',
    'scripts/docs/render-settings-reference.ps1',
    'app_v2/src/lite/java/com/sza/fastmediasorter/di/LiteSettingsSearchAvailabilityModule.kt'
)
foreach ($path in $otherReferenceInputs) {
    Assert-Equal "reference | $path" $true (Test-SettingsReferenceInput -ChangedFiles @($path) -RepoRoot $repoRoot)
}
# The renderer and an availability module are source-tree paths: post-change.ps1 already triggers
# the gate on them by its own disjuncts, so the doc-artifact predicate deliberately ignores them.
Assert-Equal 'artifact | render-settings-reference.ps1' $false (Test-SettingsDocArtifactInput -ChangedFiles @('scripts/docs/render-settings-reference.ps1'))
Assert-Equal 'artifact | SETTINGS_REFERENCE.md'         $true  (Test-SettingsDocArtifactInput -ChangedFiles @('docs/SETTINGS_REFERENCE.md'))

# --- A set unrelated to settings feeds nothing ---------------------------------------------------
$unrelated = @('scripts/check_device_profile_presets.ps1', 'docs/DEV_OPS.md', 'app_v2/src/main/res/values/dimens.xml')
Assert-Equal 'unrelated set | artifact'  $false (Test-SettingsDocArtifactInput -ChangedFiles $unrelated)
Assert-Equal 'unrelated set | reference' $false (Test-SettingsReferenceInput -ChangedFiles $unrelated -RepoRoot $repoRoot)
Assert-Equal 'unrelated set | manifest'  $false (Test-SettingsManifestInput -ChangedFiles $unrelated -RepoRoot $repoRoot)

# --- The empty set keeps the unscoped project-wide judgement --------------------------------------
# Both stage predicates must answer $true so an unscoped run judges everything; the trigger must
# answer $false, because it decides whether to run at all and post-change always knows its set.
Assert-Equal 'empty set | manifest  -> true'  $true  (Test-SettingsManifestInput -ChangedFiles @() -RepoRoot $repoRoot)
Assert-Equal 'empty set | reference -> true'  $true  (Test-SettingsReferenceInput -ChangedFiles @() -RepoRoot $repoRoot)
Assert-Equal 'empty set | artifact  -> false' $false (Test-SettingsDocArtifactInput -ChangedFiles @())

# --- Comma-joined and backslash spellings agree with the array form -------------------------------
# post-change.ps1 hands -ChangedFiles a comma-joined string; Windows supplies both separators.
$csv = @('docs/settings/device-profile-nonpresettable.json,docs/settings/settings-annotations.json')
$arr = @('docs/settings/device-profile-nonpresettable.json', 'docs/settings/settings-annotations.json')
Assert-Equal 'csv == array | artifact'  (Test-SettingsDocArtifactInput -ChangedFiles $arr) (Test-SettingsDocArtifactInput -ChangedFiles $csv)
Assert-Equal 'csv == array | reference' (Test-SettingsReferenceInput -ChangedFiles $arr -RepoRoot $repoRoot) (Test-SettingsReferenceInput -ChangedFiles $csv -RepoRoot $repoRoot)
Assert-Equal 'backslash spelling | artifact' $true (Test-SettingsDocArtifactInput -ChangedFiles @('docs\settings\settings-manifest.json'))
Assert-Equal 'leading ./ spelling | artifact' $true (Test-SettingsDocArtifactInput -ChangedFiles @('./docs/settings/settings-manifest.json'))

# --- A missing layout widens the manifest predicate back ------------------------------------------
# A deleted layout was removed from the set the scan reads, which moves the manifest, so an
# unreadable path must not be read as clean.
Assert-Equal 'deleted layout widens manifest back' $true (Test-SettingsManifestInput -ChangedFiles @('app_v2/src/main/res/layout/fragment_settings_gone.xml') -RepoRoot $repoRoot)

# --- The facade is wired to the same map, and routes exit 3 to the advisory path ------------------
# A wiring check, not a behavioural one: the advisory route can only be exercised end to end while
# the tree actually carries a reference divergence, which is a transient state belonging to another
# ticket. These assertions fail if the trigger goes back to a path prefix, or if exit 3 stops being
# downgraded - the two halves of the defect this ticket removed.
$postChange = [System.IO.File]::ReadAllText((Join-Path $repoRoot 'scripts/post-change.ps1'))
Assert-Equal 'facade reads the shared input map'     $true ($postChange -match 'lib/settings-doc-inputs\.ps1')
Assert-Equal 'facade trigger uses the predicate'     $true ($postChange -match 'Test-SettingsDocArtifactInput')
Assert-Equal "facade no longer prefixes 'docs/settings/'" $false ($postChange -match "Test-AnyChangedFile 'docs/settings/'")
Assert-Equal 'facade downgrades exit 3'              $true ($postChange -match 'settingsDocExit -eq 3')
Assert-Equal 'facade advisory names this gate'       $true ($postChange -match 'Invoke-AdvisoryStep "settings-doc-sync-gate"')

Write-Host ""
if ($script:fail -gt 0) {
    Write-Host ("settings-doc-inputs: FAIL - {0} passed, {1} failed." -f $script:pass, $script:fail) -ForegroundColor Red
    exit 1
}
Write-Host ("settings-doc-inputs: PASS - {0} assertions." -f $script:pass) -ForegroundColor Green
exit 0
