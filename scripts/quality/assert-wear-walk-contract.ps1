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

.PARAMETER ScreenList
.PARAMETER StringsFile
.PARAMETER WearSource
.PARAMETER BaselineFile
    Path overrides. Default to the real tree; the regression suite points them at fixtures, which is
    the only way to prove the gate catches a rename without renaming a shipped string to find out.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-wear-walk-contract.ps1 -Gate

.NOTES
    Exit codes:
      0  divergences are at or below the baseline (or -Gate was not passed).
      1  divergences exceed the baseline, or -UpdateBaseline was asked to raise it.
      2  could not verify: the screen list, the strings file or the wear source tree is missing or
         unreadable. Never conflated with 1 - "found a defect" and "did not look" are different answers.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [string]$ScreenList,
    [string]$StringsFile,
    [string]$WearSource,
    [string]$BaselineFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$screenList = if ($ScreenList) { $ScreenList } else { Join-Path $repoRoot 'scripts/devtest/wear-prerelease-screens.json' }
$stringsFile = if ($StringsFile) { $StringsFile } else { Join-Path $repoRoot 'wear/src/main/res/values/strings.xml' }
$wearSource = if ($WearSource) { $WearSource } else { Join-Path $repoRoot 'wear/src/main/java' }
$baselineFile = if ($BaselineFile) { $BaselineFile } else { Join-Path $PSScriptRoot 'wear-walk-contract-baseline.txt' }

function Stop-Unverifiable {
    param([string]$Reason)
    Write-Error "assert-wear-walk-contract: could not verify - $Reason" -ErrorAction Continue
    exit 2
}

if (-not (Test-Path -LiteralPath $screenList)) { Stop-Unverifiable "screen list not found: $screenList" }
if (-not (Test-Path -LiteralPath $stringsFile)) { Stop-Unverifiable "wear strings not found: $stringsFile" }
if (-not (Test-Path -LiteralPath $wearSource)) { Stop-Unverifiable "wear source tree not found: $wearSource" }

try { $walk = Get-Content -LiteralPath $screenList -Raw | ConvertFrom-Json }
catch { Stop-Unverifiable "screen list is not readable JSON: $screenList" }
if (-not $walk.PSObject.Properties.Name.Contains('screens')) { Stop-Unverifiable "screen list declares no 'screens' array" }
$entries = @($walk.screens)
if ($entries.Count -eq 0) { Stop-Unverifiable "screen list declares no screen" }

# Resource values, read from the default locale only: the walk runs on a device left in English by
# wear-prerelease-prepare, and a translated value would make this gate disagree with the run.
$stringValue = @{}
try { $stringsXml = [xml](Get-Content -LiteralPath $stringsFile -Raw) }
catch { Stop-Unverifiable "wear strings file is not readable XML: $stringsFile" }
foreach ($node in $stringsXml.resources.string) {
    if ($null -ne $node.name) { $stringValue[[string]$node.name] = [string]$node.InnerText }
}

# One pass over the module: every composable whose name ends in Screen, and the full text the
# render check greps. Reading each file once keeps this gate inside the fast batch's budget.
$screenNames = [System.Collections.Generic.HashSet[string]]::new()
$sourceText = [System.Text.StringBuilder]::new()
foreach ($file in Get-ChildItem -LiteralPath $wearSource -Recurse -Filter '*.kt' -File) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    [void]$sourceText.AppendLine($text)
    # PascalCase only: a composable is a screen, `fun Modifier.applyScreen(..)` is not, and counting
    # the helper made the module look like it had 39 screens instead of 38.
    foreach ($m in [regex]::Matches($text, 'fun\s+([A-Z][A-Za-z0-9_]*Screen)\s*\(')) {
        [void]$screenNames.Add($m.Groups[1].Value)
    }
}
$allSource = $sourceText.ToString()

$divergences = @()
foreach ($entry in $entries) {
    $id = if ($entry.PSObject.Properties.Name -contains 'id') { [string]$entry.id } else { '<no id>' }

    $expect = if ($entry.PSObject.Properties.Name -contains 'expect') { [string]$entry.expect } else { $null }
    $expectRes = if ($entry.PSObject.Properties.Name -contains 'expectRes') { [string]$entry.expectRes } else { $null }
    $screen = if ($entry.PSObject.Properties.Name -contains 'screen') { [string]$entry.screen } else { $null }

    if (-not $expectRes) {
        $divergences += "$id : no 'expectRes' declared"
    }
    elseif (-not $stringValue.ContainsKey($expectRes)) {
        $divergences += "$id : expectRes '$expectRes' resolves to no string in wear values/strings.xml"
    }
    elseif ($expect -and ($stringValue[$expectRes] -notlike "*$expect*")) {
        $divergences += "$id : expect '$expect' is not contained in R.string.$expectRes = '$($stringValue[$expectRes])'"
    }

    if ($expectRes -and $stringValue.ContainsKey($expectRes)) {
        if ($allSource -notmatch ('R\.string\.' + [regex]::Escape($expectRes) + '\b')) {
            $divergences += "$id : R.string.$expectRes is never referenced under wear/src/main/java - no composable can render it"
        }
    }

    if (-not $screen) {
        $divergences += "$id : no 'screen' declared"
    }
    elseif (-not $screenNames.Contains($screen)) {
        $divergences += "$id : screen '$screen' is not a composable in the wear module"
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
    if (-not $name) { $divergences += "excluded[] : a record declares no 'screen'"; continue }
    if (-not $excluded.Add($name)) { $divergences += "$name : listed twice in excluded[]" }
    if ($reason -notin $REASONS) {
        $divergences += "$name : reason '$reason' is not one of: $($REASONS -join ', ')"
    }
    if ($declared.Contains($name)) {
        $divergences += "$name : both walked and excluded - it can only be one"
    }
}

foreach ($name in $screenNames) {
    if (-not $declared.Contains($name) -and -not $excluded.Contains($name)) {
        $divergences += "$name : neither walked nor excluded - classify it in wear-prerelease-screens.json"
    }
}
foreach ($name in $excluded) {
    if (-not $screenNames.Contains($name)) {
        $divergences += "$name : excluded but no such composable exists in the wear module"
    }
}

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
    Set-Content -LiteralPath $baselineFile -Value $count -Encoding UTF8
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
