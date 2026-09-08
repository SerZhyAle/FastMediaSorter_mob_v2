#requires -Version 7.0
<#
.SYNOPSIS
    S2547 - binds the declared watch pre-release walk to the wear module it claims to walk.

.DESCRIPTION
    `scripts/devtest/wear-prerelease-screens.json` is data the walk driver replays. Nothing used to
    connect it to the application: a label renamed in Compose left the walk entry behind, and the run
    then printed a failure for a screen that in fact works. The reverse error was just as silent - an
    entry whose token happened to match anything on screen was counted as an observation.

    Measured 2026-09-04, five of the eighteen declared entries failed on a qualified watch and not one
    of them was an application defect. They were three different faults, which is why this gate makes
    three different checks rather than grepping the module for the token:

      value  - `expectRes` still resolves, and its value CONTAINS `expect`. The walk matches the UI
               tree by substring, so this check uses the same rule. Catches the rename that turned
               `enable_audio` from "Enable Audio" into "Audio".
      render - `R.string.<expectRes>` is referenced from at least one file under wear/src/main/java.
               Catches a token that is alive in resources but reaches no composable: `app_name` is
               "FastMedia Wear" and is named only by AndroidManifest.xml, so the `home` entry could
               never have matched, on any screen size.
      screen - `screen` names a composable that exists in the module.

    What this gate CANNOT see, stated so nobody reads a PASS as more than it is: a string that is
    alive AND rendered, but by a different screen than the entry opens. That is `settings-system-info`
    after S2008 moved the route from settings into Apps, and it belongs to S2552. Path drift needs the
    navigation chain, which this gate does not model.

    Ratchet: the baseline file holds the count of divergences accepted on the day the gate landed and
    moves DOWN only. Raising it hides a regression; a repair lowers it.

.PARAMETER Gate
    Return exit code 1 when the divergence count exceeds the baseline. Without it the script reports
    and returns 0, which is how an advisory caller inspects the tree.

.PARAMETER UpdateBaseline
    Rewrite the baseline to the count measured now. Refuses to RAISE it - a ratchet only tightens.

.PARAMETER ChangedFiles
    CSV of the paths a closure is judging. Without it the gate judges the whole module, which is what
    the fg battery and the release scope want. With it a divergence is reported only when it belongs
    to the presented set: the screen is declared in one of those files, or the entry itself is one
    this edit added, removed or changed.

    S2621: this exists because post-change.ps1 now calls the gate, and the gate always scans the whole
    module. Run unscoped from a closure it would refuse a ticket for a screen another session had just
    added and not yet classified - the exact failure this gate's own ticket was filed about, only
    narrowed to sessions touching the watch module. The dirty-tree rule (S0826/S1338) asks a scoped
    gate to judge the delta it was handed, not a project-wide count.

    S2723: ownership of the contract is by RECORD, not by file. S2621's rule read "the screen list
    being in the set restores the full project-wide judgement", and every session that adds a screen
    must edit the screen list, so the narrowing never applied to the sessions it was written for -
    measured 2026-09-08, S2550's closure was refused over TileTargetsSettingsScreen, a screen S2587
    had added in a neighbouring session forty minutes earlier. Removing the rule was not an option
    either: it is what catches a screen renamed or deleted inside the contract with no code edit to
    attribute it to. So a scoped run that presents the screen list now compares the contract with its
    pre-edit copy and owns only the entries that differ, keyed by id for screens[] and by screen name
    for excluded[]; a touched record contributes BOTH its id and its screen name, so a rename owns the
    old name as well as the new one. A pre-edit copy that cannot be obtained falls back to owning the
    whole contract - "could not measure the delta" is judged strictly, never leniently.

.PARAMETER ScreenListBaseline
    Path to the pre-edit copy of the screen list, used only by a scoped run that presents it. Default
    is `git show HEAD:<screen list>` - the same delta-against-HEAD measurement the ratchet closing
    gates already use (CLAUDE.md section 12), and a measurement of this edit rather than a claim about
    current state. The regression suite overrides it because it cannot commit a fixture to produce a
    contract edit.

.PARAMETER ScreenList
.PARAMETER StringsFile
.PARAMETER WearSource
.PARAMETER BaselineFile
    Path overrides. Default to the real tree; the regression suite points them at fixtures, which is
    the only way to prove the gate catches a rename without renaming a shipped string to find out.
    StringsFile names ONE file and replaces the default sweep of every wear/src/main/res/values/strings*.xml.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-wear-walk-contract.ps1 -Gate

.NOTES
    Exit codes:
      0  divergences are at or below the baseline (or -Gate was not passed).
      1  divergences exceed the baseline, or -UpdateBaseline was asked to raise it.
      2  could not verify: the screen list, the strings file or the wear source tree is missing or
         unreadable. Never conflated with 1 - "found a defect" and "did not look" are different answers.
      4  Code.Scripts is held by another session, so no baseline was written. The queue place is held -
         wait for the turn in the background and rerun (S2635).
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [string]$ChangedFiles,
    [string]$ScreenListBaseline,
    [string]$ScreenList,
    [string]$StringsFile,
    [string]$WearSource,
    [string]$BaselineFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')

$screenList = if ($ScreenList) { $ScreenList } else { Join-Path $repoRoot 'scripts/devtest/wear-prerelease-screens.json' }
# S2587: the default locale keeps its strings in several files - strings.xml, strings_browse.xml,
# strings_tiles.xml - so reading only the first one reported a key that exists as resolving to nothing,
# and the fix an author would reach for is to weaken the walk entry rather than the lookup.
# The @() wraps the whole `if`, not each branch: an assignment from an if-statement unwraps a
# one-element array back to a scalar, and under StrictMode the .Count below then throws.
$stringsFiles = @(if ($StringsFile) {
        $StringsFile
    } else {
        Get-ChildItem -LiteralPath (Join-Path $repoRoot 'wear/src/main/res/values') -Filter 'strings*.xml' -File -ErrorAction SilentlyContinue |
            Sort-Object Name |
            ForEach-Object { $_.FullName }
    })
$wearSource = if ($WearSource) { $WearSource } else { Join-Path $repoRoot 'wear/src/main/java' }
$baselineFile = if ($BaselineFile) { $BaselineFile } else { Join-Path $PSScriptRoot 'wear-walk-contract-baseline.txt' }

function Stop-Unverifiable {
    param([string]$Reason)
    Write-Error "assert-wear-walk-contract: could not verify - $Reason" -ErrorAction Continue
    exit 2
}

# S2621: the presented set, normalised so a repo-relative path and an absolute one compare equal -
# post-change.ps1 hands repo-relative paths, a hand call may hand absolute ones, and Windows treats
# both cases and both separators as the same file.
$scopeEnabled = -not [string]::IsNullOrWhiteSpace($ChangedFiles)
$scopePaths = @()
if ($scopeEnabled) {
    $scopePaths = @($ChangedFiles -split ',' |
        ForEach-Object { ($_.Trim() -replace '\\', '/').ToLowerInvariant() } |
        Where-Object { $_ })
}
# A changed screen source is the third attribution channel, and it is here for the rename this gate
# was built for: renaming a composable leaves the OLD entry naming a screen that now exists in no
# file, so that divergence can be attributed to no file at all. Without this clause an author who
# classified the new name and forgot the old entry would close green.
$screenSourceChanged = @($scopePaths | Where-Object { $_ -match 'screen\.kt$' }).Count -gt 0

function Test-InScope {
    param([string]$Path)
    if (-not $Path) { return $false }
    $normalised = ($Path -replace '\\', '/').ToLowerInvariant()
    foreach ($candidate in $scopePaths) {
        if ($normalised -eq $candidate -or $normalised.EndsWith('/' + $candidate)) { return $true }
    }
    return $false
}

if (-not (Test-Path -LiteralPath $screenList)) { Stop-Unverifiable "screen list not found: $screenList" }
if ($stringsFiles.Count -eq 0) { Stop-Unverifiable "wear strings not found under wear/src/main/res/values" }
foreach ($file in $stringsFiles) {
    if (-not (Test-Path -LiteralPath $file)) { Stop-Unverifiable "wear strings not found: $file" }
}
if (-not (Test-Path -LiteralPath $wearSource)) { Stop-Unverifiable "wear source tree not found: $wearSource" }

try { $walk = Get-Content -LiteralPath $screenList -Raw | ConvertFrom-Json }
catch { Stop-Unverifiable "screen list is not readable JSON: $screenList" }
if (-not $walk.PSObject.Properties.Name.Contains('screens')) { Stop-Unverifiable "screen list declares no 'screens' array" }
$entries = @($walk.screens)
if ($entries.Count -eq 0) { Stop-Unverifiable "screen list declares no screen" }

# Resource values, read from the default locale only: the walk runs on a device left in English by
# wear-prerelease-prepare, and a translated value would make this gate disagree with the run.
$stringValue = @{}
foreach ($file in $stringsFiles) {
    try { $stringsXml = [xml](Get-Content -LiteralPath $file -Raw) }
    catch { Stop-Unverifiable "wear strings file is not readable XML: $file" }
    foreach ($node in $stringsXml.resources.string) {
        if ($null -ne $node.name) { $stringValue[[string]$node.name] = [string]$node.InnerText }
    }
}

# One pass over the module: every composable whose name ends in Screen, and the full text the
# render check greps. Reading each file once keeps this gate inside the fast batch's budget.
$screenNames = [System.Collections.Generic.HashSet[string]]::new()
# S2621: which file declares each screen, so a scoped run can tell a divergence that belongs to the
# presented set from one that belongs to a neighbour's unfinished work.
$screenFile = @{}
$sourceText = [System.Text.StringBuilder]::new()
foreach ($file in Get-ChildItem -LiteralPath $wearSource -Recurse -Filter '*.kt' -File) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    [void]$sourceText.AppendLine($text)
    # PascalCase only: a composable is a screen, `fun Modifier.applyScreen(..)` is not, and counting
    # the helper made the module look like it had 39 screens instead of 38.
    foreach ($m in [regex]::Matches($text, 'fun\s+([A-Z][A-Za-z0-9_]*Screen)\s*\(')) {
        $screenName = $m.Groups[1].Value
        [void]$screenNames.Add($screenName)
        if (-not $screenFile.ContainsKey($screenName)) { $screenFile[$screenName] = $file.FullName }
    }
}
$allSource = $sourceText.ToString()

$divergences = @()

# S2621: every divergence records the screen it is about, so a scoped run can attribute it. The
# unscoped run reports the same texts in the same order it always did.
# S2723: it also records the entry id, because the contract keys its walked entries by id and a
# divergence about an entry that declares no screen can be attributed by nothing else.
function Add-Divergence {
    param([string]$Text, [string]$Screen, [string]$Id)
    $script:divergences += [pscustomobject]@{ Text = $Text; Screen = $Screen; Id = $Id }
}

foreach ($entry in $entries) {
    # The declared id, or $null when the entry carries none. Kept apart from the display text so that
    # the S2723 attribution key is never the literal '<no id>' placeholder.
    $entryId = if ($entry.PSObject.Properties.Name -contains 'id' -and $entry.id) { [string]$entry.id } else { $null }
    $id = if ($entryId) { $entryId } else { '<no id>' }

    $expect = if ($entry.PSObject.Properties.Name -contains 'expect') { [string]$entry.expect } else { $null }
    $expectRes = if ($entry.PSObject.Properties.Name -contains 'expectRes') { [string]$entry.expectRes } else { $null }
    $screen = if ($entry.PSObject.Properties.Name -contains 'screen') { [string]$entry.screen } else { $null }

    if (-not $expectRes) {
        Add-Divergence -Text "$id : no 'expectRes' declared" -Screen $screen -Id $entryId
    }
    elseif (-not $stringValue.ContainsKey($expectRes)) {
        Add-Divergence -Text "$id : expectRes '$expectRes' resolves to no string in wear values/strings*.xml" -Screen $screen -Id $entryId
    }
    elseif ($expect -and ($stringValue[$expectRes] -notlike "*$expect*")) {
        Add-Divergence -Text "$id : expect '$expect' is not contained in R.string.$expectRes = '$($stringValue[$expectRes])'" -Screen $screen -Id $entryId
    }

    if ($expectRes -and $stringValue.ContainsKey($expectRes)) {
        if ($allSource -notmatch ('R\.string\.' + [regex]::Escape($expectRes) + '\b')) {
            Add-Divergence -Text "$id : R.string.$expectRes is never referenced under wear/src/main/java - no composable can render it" -Screen $screen -Id $entryId
        }
    }

    if (-not $screen) {
        Add-Divergence -Text "$id : no 'screen' declared" -Screen $null -Id $entryId
    }
    elseif (-not $screenNames.Contains($screen)) {
        Add-Divergence -Text "$id : screen '$screen' is not a composable in the wear module" -Screen $screen -Id $entryId
    }
}

# Coverage: every screen the module has is either walked or excluded with a reason someone can
# re-judge. Without this the 18-against-38 gap was recorded nowhere, so a PASS could not be read as a
# statement about what the run opened - and clip-check, which decides WO-V16, only runs on a screen
# the walk actually opened.
# `absent-from-this-flavor` is the seventh class and the only one that is not about the screen: the
# module builds standard and noLegal from one graph, and S2486 hid the credential-entry route from the
# store build to satisfy WO-P6. The screen is a real destination - just not in the variant this sweep
# judges - so calling it `not-a-destination` would file a flavor decision as a navigation fact and
# hide the one thing a reader needs, which is that the other flavor still reaches it (S2555).
$REASONS = @('not-a-destination', 'arg-external', 'gesture-only', 'timed', 'needs-seeded-content', 'pre-graph-gate', 'no-static-marker', 'absent-from-this-flavor')
$declared = [System.Collections.Generic.HashSet[string]]::new()
foreach ($entry in $entries) {
    if ($entry.PSObject.Properties.Name -contains 'screen' -and $entry.screen) { [void]$declared.Add([string]$entry.screen) }
}

$excludedRecords = @()
if ($walk.PSObject.Properties.Name.Contains('excluded')) { $excludedRecords = @($walk.excluded) }
$excluded = [System.Collections.Generic.HashSet[string]]::new()
foreach ($record in $excludedRecords) {
    $name = if ($record.PSObject.Properties.Name -contains 'screen') { [string]$record.screen } else { $null }
    $reason = if ($record.PSObject.Properties.Name -contains 'reason') { [string]$record.reason } else { $null }
    if (-not $name) { Add-Divergence -Text "excluded[] : a record declares no 'screen'" -Screen $null; continue }
    if (-not $excluded.Add($name)) { Add-Divergence -Text "$name : listed twice in excluded[]" -Screen $name }
    if ($reason -notin $REASONS) {
        Add-Divergence -Text "$name : reason '$reason' is not one of: $($REASONS -join ', ')" -Screen $name
    }
    if ($declared.Contains($name)) {
        Add-Divergence -Text "$name : both walked and excluded - it can only be one" -Screen $name
    }
}

foreach ($name in $screenNames) {
    if (-not $declared.Contains($name) -and -not $excluded.Contains($name)) {
        Add-Divergence -Text "$name : neither walked nor excluded - classify it in wear-prerelease-screens.json" -Screen $name
    }
}
foreach ($name in $excluded) {
    if (-not $screenNames.Contains($name)) {
        Add-Divergence -Text "$name : excluded but no such composable exists in the wear module" -Screen $name
    }
}

# S2723: the contract's records, keyed the way the contract itself keys them - walked entries by id
# (a missing id falls back to the screen name, which is what the display text does too), exclusions by
# screen name. Each key carries the names it can be attributed under, so a touched record owns both
# its id and its screen name.
function Get-ContractRecords {
    param($Walk)
    $records = @{}
    $names = @{}
    $walked = @()
    if ($Walk.PSObject.Properties.Name.Contains('screens')) { $walked = @($Walk.screens) }
    foreach ($record in $walked) {
        $recordScreen = if ($record.PSObject.Properties.Name -contains 'screen' -and $record.screen) { [string]$record.screen } else { $null }
        $recordId = if ($record.PSObject.Properties.Name -contains 'id' -and $record.id) { [string]$record.id } else { $recordScreen }
        if (-not $recordId) { continue }
        $key = "screens/$recordId"
        $records[$key] = ($record | ConvertTo-Json -Depth 8 -Compress)
        $names[$key] = @($recordId, $recordScreen)
    }
    $excludedRaw = @()
    if ($Walk.PSObject.Properties.Name.Contains('excluded')) { $excludedRaw = @($Walk.excluded) }
    foreach ($record in $excludedRaw) {
        $recordScreen = if ($record.PSObject.Properties.Name -contains 'screen' -and $record.screen) { [string]$record.screen } else { $null }
        if (-not $recordScreen) { continue }
        $key = "excluded/$recordScreen"
        $records[$key] = ($record | ConvertTo-Json -Depth 8 -Compress)
        $names[$key] = @($recordScreen)
    }
    return [pscustomobject]@{ Records = $records; Names = $names }
}

function Get-TouchedNames {
    param($Current, $Baseline)
    $touched = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($key in $Current.Records.Keys) {
        if ((-not $Baseline.Records.ContainsKey($key)) -or ($Baseline.Records[$key] -ne $Current.Records[$key])) {
            foreach ($name in $Current.Names[$key]) { if ($name) { [void]$touched.Add($name) } }
        }
    }
    # A record the edit REMOVED has no entry left to attribute it to, and removal is one of the two
    # faults the ownership rule exists for, so its names come from the pre-edit copy.
    foreach ($key in $Baseline.Records.Keys) {
        if (-not $Current.Records.ContainsKey($key)) {
            foreach ($name in $Baseline.Names[$key]) { if ($name) { [void]$touched.Add($name) } }
        }
    }
    return $touched
}

# S2723: $touchedNames stays $null when the delta could not be measured, which means "this edit owns
# the whole contract" - the pre-S2723 behaviour, kept as the strict fallback.
$contractPresented = $scopeEnabled -and (Test-InScope $screenList)
$touchedNames = $null
if ($contractPresented) {
    $baselineText = $null
    if ($ScreenListBaseline) {
        if (Test-Path -LiteralPath $ScreenListBaseline) { $baselineText = Get-Content -LiteralPath $ScreenListBaseline -Raw }
    }
    else {
        $relative = ([System.IO.Path]::GetRelativePath($repoRoot, (Resolve-Path -LiteralPath $screenList).Path)) -replace '\\', '/'
        $gitOutput = & git -C $repoRoot show "HEAD:$relative" 2>$null
        if ($LASTEXITCODE -eq 0) { $baselineText = ($gitOutput -join "`n") }
    }
    if ($baselineText) {
        try { $touchedNames = Get-TouchedNames (Get-ContractRecords $walk) (Get-ContractRecords ($baselineText | ConvertFrom-Json)) }
        catch { $touchedNames = $null }
    }
}

# S2621 + S2723: keep only what the presented set is answerable for - a screen declared in one of the
# presented files, a stale entry naming no file at all when the set carries a changed screen source,
# or a contract record this edit touched.
if ($scopeEnabled) {
    $divergences = @($divergences | Where-Object {
        if ($contractPresented) {
            if ($null -eq $touchedNames) { return $true }
            if ((-not $_.Screen) -and (-not $_.Id)) { return $true }
            if ($_.Screen -and $touchedNames.Contains($_.Screen)) { return $true }
            if ($_.Id -and $touchedNames.Contains($_.Id)) { return $true }
        }
        if (-not $_.Screen) { return $false }
        if ($screenFile.ContainsKey($_.Screen)) { return (Test-InScope $screenFile[$_.Screen]) }
        return $screenSourceChanged
    })
}
$divergences = @($divergences | ForEach-Object { $_.Text })

$baseline = 0
if (Test-Path -LiteralPath $baselineFile) {
    $firstLine = (Get-Content -LiteralPath $baselineFile | Where-Object { $_ -match '^\s*\d+\s*$' } | Select-Object -First 1)
    if ($null -ne $firstLine) { $baseline = [int]$firstLine.Trim() }
}

$count = $divergences.Count

if ($UpdateBaseline) {
    if ($count -gt $baseline) {
        Write-Error "assert-wear-walk-contract: refusing to raise the baseline from $baseline to $count - the ratchet only tightens. Repair the entries instead." -ErrorAction Continue
        exit 1
    }
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-wear-walk-contract.ps1 -UpdateBaseline'
        Set-Content -LiteralPath $baselineFile -Value $count -Encoding UTF8
    }
    finally { Exit-CodeLockScope -Scope $scope }
    Write-Host "assert-wear-walk-contract: baseline lowered to $count." -ForegroundColor Green
    exit 0
}

foreach ($d in $divergences) { Write-Host "  $d" -ForegroundColor Yellow }

Write-Host ("assert-wear-walk-contract: coverage {0} walked + {1} excluded of {2} wear screens ({3} entries); expected: <= {4} divergence(s) | actual: {5}" -f `
    $declared.Count, $excluded.Count, $screenNames.Count, $entries.Count, $baseline, $count)

if ($count -gt $baseline) {
    Write-Error "assert-wear-walk-contract: FAIL - $count divergence(s) above the baseline of $baseline." -ErrorAction Continue
    if ($Gate) { exit 1 }
    exit 0
}

Write-Host 'assert-wear-walk-contract: PASS' -ForegroundColor Green
exit 0
