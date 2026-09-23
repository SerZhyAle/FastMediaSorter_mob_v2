#requires -Version 7.0
<#
.SYNOPSIS
    S2380 - binds the declared phone UI sweep to the app_v2 module it claims to walk.

.DESCRIPTION
    `scripts/devtest/ui-sweep-screens.json` is data the sweep driver replays. Nothing else connects it
    to the application, so every way it can rot is silent: a screen added to the module and to no list,
    a marker string renamed under an entry that then reports a working screen as broken, or a marker
    that is alive in resources but reaches no layout and so could never have matched on any screen.

    Modelled on `assert-wear-walk-contract.ps1` (S2547) rather than generalised with it - strategic
    ADR-2 keeps the phone and watch declarations separate until there is a second working consumer.

    Four checks, because the four failures are different faults:

      coverage - every activity in dev/ACTIVITY_CATALOG/app_v2.jsonl, and every sectionId in
                 docs/settings/settings-manifest.json, is either mapped to a walk entry or named in
                 the matching excluded list. A screen in neither is the failure the whole gate exists
                 for: it is invisible to the sweep AND invisible to the reader of the catalog.
      value    - `expectRes` still resolves, and its value CONTAINS `expect`. The sweep matches the UI
                 tree by substring, so this check uses the same rule.
      render   - `R.string.<expectRes>` is referenced from at least one file under app_v2/src/main
                 (layout, menu or java). Catches a token alive in resources that reaches no surface -
                 a manifest-only android:label can never match a node in the tree.
      reason   - every excluded entry carries a `reason` from the closed set the file declares in
                 `reasonSet`. An unclassified exclusion is how "forgotten" disguises itself as
                 "deliberately skipped".

    What this gate CANNOT see, stated so nobody reads a PASS as more than it is: a string that is
    alive AND rendered, but by a DIFFERENT screen than the entry opens. Path drift needs the tap chain
    modelled, which this gate does not do - it is caught by the run itself, where the entry scores
    `failed` and the frame shows the wrong screen. The known instance of that shape on the watch side
    is S2552; the phone equivalent has not been measured because no full phone run has happened yet.

.PARAMETER Gate
    Return exit code 1 when any divergence is found. Without it the script reports and returns 0,
    which is how an advisory caller inspects the tree.

.PARAMETER ChangedFiles
    CSV of the paths a closure is judging. This gate is FIXED-INPUT (S2824): its inputs are the screen
    catalog, the activity catalog and the settings manifest. Present any of those in the set and the
    gate is FATAL; present none and it downgrades to advisory exit 3, because the divergence then
    belongs to another session and refusing this closure would punish the wrong ticket.

.PARAMETER ScreenList
.PARAMETER ActivityCatalog
.PARAMETER SettingsManifest
.PARAMETER ResRoot
.PARAMETER SourceRoot
    Path overrides. Default to the real tree; a regression suite points them at fixtures, which is the
    only way to prove the gate catches a rename without renaming a shipped string to find out.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-ui-sweep-catalog.ps1 -Gate

.NOTES
    Exit codes:
      0 - no divergence, or reporting mode, or advisory downgrade under -ChangedFiles.
      1 - divergence found and -Gate was passed.
      2 - cannot verify: a declared input file is missing or does not parse.
      3 - advisory: -ChangedFiles was given and named none of this gate's inputs.
#>

[CmdletBinding()]
param(
    [switch]$Gate,
    [string]$ChangedFiles,
    [string]$ScreenList,
    [string]$ActivityCatalog,
    [string]$SettingsManifest,
    [string]$ResRoot,
    [string]$SourceRoot
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..' '..')
function Resolve-Default([string]$value, [string]$relative) {
    if ($value) { return $value }
    return (Join-Path $repoRoot $relative)
}

$ScreenList       = Resolve-Default $ScreenList       'scripts/devtest/ui-sweep-screens.json'
$ActivityCatalog  = Resolve-Default $ActivityCatalog  'dev/ACTIVITY_CATALOG/app_v2.jsonl'
$SettingsManifest = Resolve-Default $SettingsManifest 'docs/settings/settings-manifest.json'
$ResRoot          = Resolve-Default $ResRoot          'app_v2/src/main/res'
$SourceRoot       = Resolve-Default $SourceRoot       'app_v2/src/main'

# S2824 fixed-input scoping: the subject is these three files, not whatever the closure happened to
# touch, so a set naming none of them gets an advisory rather than a refusal it cannot act on.
if ($ChangedFiles) {
    $inputs = @($ScreenList, $ActivityCatalog, $SettingsManifest) |
        ForEach-Object { (Split-Path $_ -Leaf) }
    $presented = $ChangedFiles -split ',' | ForEach-Object { (Split-Path $_.Trim() -Leaf) }
    if (-not ($presented | Where-Object { $inputs -contains $_ })) {
        Write-Host 'assert-ui-sweep-catalog: ADVISORY - the changed set names none of this gate inputs (screen catalog, activity catalog, settings manifest).'
        exit 3
    }
}

foreach ($required in @($ScreenList, $ActivityCatalog, $SettingsManifest)) {
    if (-not (Test-Path $required)) {
        Write-Host "assert-ui-sweep-catalog: CANNOT VERIFY - missing input: $required"
        exit 2
    }
}

try {
    $catalog = Get-Content $ScreenList -Raw | ConvertFrom-Json
}
catch {
    Write-Host "assert-ui-sweep-catalog: CANNOT VERIFY - $ScreenList does not parse: $_"
    exit 2
}

$problems = [System.Collections.Generic.List[string]]::new()

# --- strings: name -> value, over every values/*.xml in the module ---
$stringXml = Get-ChildItem (Join-Path $ResRoot 'values') -Filter '*.xml' -ErrorAction SilentlyContinue
$stringBlob = ($stringXml | ForEach-Object { Get-Content $_.FullName -Raw }) -join "`n"
$strings = @{}
foreach ($m in [regex]::Matches($stringBlob, '<string\s+name="([A-Za-z0-9_]+)"[^>]*>(.*?)</string>', 'Singleline')) {
    $strings[$m.Groups[1].Value] = ($m.Groups[2].Value -replace '\s+', ' ').Trim()
}

# --- render index: every string resource named anywhere under src/main, minus the manifest and
# minus res/values itself. Both exclusions are the same point: a name that reaches no SURFACE cannot
# be matched in the UI tree. A manifest android:label is the case that motivated the check on the
# watch side; a string referenced only by another string or by an array in res/values is the same
# fault wearing different clothes, and leaving values/ in would have let it pass.
$valuesDir = Join-Path $ResRoot 'values'
$renderFiles = Get-ChildItem $SourceRoot -Recurse -Include '*.xml', '*.kt', '*.java' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -ne 'AndroidManifest.xml' -and $_.DirectoryName -notlike "$valuesDir*" }
$renderBlob = ($renderFiles | ForEach-Object { Get-Content $_.FullName -Raw }) -join "`n"
$rendered = [System.Collections.Generic.HashSet[string]]::new()
foreach ($m in [regex]::Matches($renderBlob, '(?:@string/|R\.string\.)([A-Za-z0-9_]+)')) {
    [void]$rendered.Add($m.Groups[1].Value)
}

# --- value + render, over every entry that declares a marker ---
function Test-Marker($entry, [string]$where) {
    # An entry may prove itself by resource-id instead of by text (S2380). An id is not translated
    # and does not disappear when a button collapses to its icon, so it is the stronger marker - but
    # it is matched against the live UI tree, which this gate never reads, so there is nothing here
    # to resolve it against. The entry is then exempt from the text checks rather than silently
    # failing them; an entry carrying NEITHER form is caught by the coverage check above.
    if ($entry.expectId) { return }
    if (-not $entry.expectRes) { return }
    $name = $entry.expectRes
    if (-not $strings.ContainsKey($name)) {
        $problems.Add("value  | $where -> expectRes '$name' does not resolve to a string resource.")
        return
    }
    # The comparison is against the rendered text, so the XML escapes the sweep never sees are undone.
    $value = $strings[$name] -replace '&amp;', '&' -replace "\\'", "'" -replace '&lt;', '<' -replace '&gt;', '>'
    if ($entry.expect -and $value -notlike "*$($entry.expect)*") {
        $problems.Add("value  | $where -> '$name' is '$value', which does not contain expect '$($entry.expect)'.")
    }
    if (-not $rendered.Contains($name)) {
        $problems.Add("render | $where -> '$name' is named by no layout, menu or source file in the module, so no node can ever carry it.")
    }
}

foreach ($s in $catalog.screens) {
    Test-Marker $s "screen '$($s.id)'"
    foreach ($node in $s.expand) { Test-Marker $node "screen '$($s.id)' expand '$($node.resourceId)'" }
}
foreach ($s in $catalog.setup) { Test-Marker $s "setup '$($s.id)'" }

# --- reason: every exclusion classified from the file own closed set ---
$reasonSet = @($catalog.reasonSet)
$allExclusions = @($catalog.excluded) + @($catalog.excludedSurfaces) + @($catalog.excludedSettingsSections)
foreach ($e in $allExclusions) {
    $subject = $e.activity ?? $e.surface ?? $e.sectionId ?? '<unnamed>'
    if (-not $e.reason) {
        $problems.Add("reason | exclusion '$subject' carries no reason.")
    }
    elseif ($reasonSet -notcontains $e.reason) {
        $problems.Add("reason | exclusion '$subject' has reason '$($e.reason)', which is not in the file declared reasonSet.")
    }
}

# --- coverage: activities ---
$screenIds = @($catalog.screens.id)
$activityMap = @{}
foreach ($p in $catalog._activityCoverage.PSObject.Properties) { $activityMap[$p.Name] = $p.Value }

$catalogActivities = Get-Content $ActivityCatalog | Where-Object { $_.Trim() } | ForEach-Object { ($_ | ConvertFrom-Json).class }
$excludedActivities = @($catalog.excluded.activity)

foreach ($k in $activityMap.Keys) {
    if ($screenIds -notcontains $k) {
        $problems.Add("coverage | _activityCoverage maps '$k', which is not a screen id.")
    }
}
$mappedActivities = @($activityMap.Values)
foreach ($a in $mappedActivities) {
    if ($catalogActivities -notcontains $a) {
        $problems.Add("coverage | _activityCoverage names activity '$a', which is not in the activity catalog.")
    }
}
foreach ($a in $excludedActivities) {
    if ($catalogActivities -notcontains $a) {
        $problems.Add("coverage | excluded names activity '$a', which is not in the activity catalog.")
    }
}
$coveredActivities = $mappedActivities + $excludedActivities
foreach ($a in $catalogActivities) {
    if ($coveredActivities -notcontains $a) {
        $problems.Add("coverage | activity '$a' is in neither the walk nor the excluded list - classify it.")
    }
}
$bothActivities = $mappedActivities | Where-Object { $excludedActivities -contains $_ }
foreach ($a in $bothActivities) {
    $problems.Add("coverage | activity '$a' is BOTH walked and excluded - it must be exactly one.")
}

# --- coverage: settings sections ---
try {
    $manifest = Get-Content $SettingsManifest -Raw | ConvertFrom-Json
}
catch {
    Write-Host "assert-ui-sweep-catalog: CANNOT VERIFY - $SettingsManifest does not parse: $_"
    exit 2
}
$sections = @($manifest.entries.sectionId | Sort-Object -Unique)
$sectionMap = @{}
foreach ($p in $catalog._sectionCoverage.PSObject.Properties) { $sectionMap[$p.Name] = $p.Value }
$excludedSections = @($catalog.excludedSettingsSections.sectionId)

foreach ($p in $sectionMap.GetEnumerator()) {
    if ($screenIds -notcontains $p.Value) {
        $problems.Add("coverage | _sectionCoverage maps section '$($p.Key)' to '$($p.Value)', which is not a screen id.")
    }
}
$coveredSections = @($sectionMap.Keys) + $excludedSections
foreach ($s in $sections) {
    if ($coveredSections -notcontains $s) {
        $problems.Add("coverage | settings section '$s' is in neither the walk nor the excluded list - classify it.")
    }
}
foreach ($s in $coveredSections) {
    if ($sections -notcontains $s) {
        $problems.Add("coverage | '$s' is classified but is not a sectionId in the settings manifest.")
    }
}

# --- verdict ---
$counts = "{0} activities, {1} settings sections, {2} screen records, {3} exclusions" -f `
    $catalogActivities.Count, $sections.Count, $catalog.screens.Count, $allExclusions.Count

if ($problems.Count -eq 0) {
    Write-Host "assert-ui-sweep-catalog: PASS - $counts, every one classified and every marker resolving."
    exit 0
}

Write-Host "assert-ui-sweep-catalog: $($problems.Count) divergence(s) over $counts."
foreach ($p in $problems) { Write-Host "  $p" }

if ($Gate) { exit 1 }
exit 0
