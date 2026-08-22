#requires -Version 7.0
<#
.SYNOPSIS
    S1879 matcher suite for `adb.ps1 tap-id` - selecting a node by its resource-id.

.DESCRIPTION
    Hermetic: drives scripts/devtest/lib/ui-tree.ps1 against a recorded phone-shaped uiautomator
    dump. No adb call, no device, no network, no writes.

    What the cases defend. A resource-id is the locale-independent way to aim a tap, and the whole
    value of that is lost if the match rule is loose enough to hit the neighbouring row - which is
    the failure `tap-label` was introduced to prevent. So the suite pins both directions: the short
    name from the layout reaches its node without the package prefix, and the exact form refuses to
    treat `rowExport` as `rowExportAll`.

    It also pins the parser widening that made all of this possible: a node carrying only a
    resource-id now enters the list, while the set of LABELLED nodes - the set clip-check judges -
    stays exactly what it was before S1879.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/adb-tap-id.tests/Run-Tests.ps1

    Exit codes:
      0 - every case passed
      1 - at least one case failed
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
. (Join-Path $repoRoot 'scripts/devtest/lib/ui-tree.ps1')

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
    } else {
        Write-Host "FAIL | $Label -> expected: $Expected | actual: $Actual"
        $script:failed++
    }
}

$fixture = Join-Path $PSScriptRoot 'fixtures/phone_settings_ids.xml'
$tree    = [xml](Get-Content -LiteralPath $fixture -Raw -Encoding UTF8)
$nodes   = @(Get-UiNodes $tree)

# ---- what the widened parser collects ---------------------------------------------------------
# 8 = 5 labelled + 3 that carry only an identifier. The two nodes with neither are still dropped:
# a node the tree cannot name is a node no verb can ever aim at.
Assert-Equal 8 $nodes.Count 'every node carrying a label OR an identifier is collected'
Assert-Equal 5 @($nodes | Where-Object { $_.labelled }).Count `
    'the labelled set is unchanged - this is the set clip-check judges'

$switch = @($nodes | Where-Object { $_.resIdShort -eq 'switchLauncherMode' })
Assert-Equal 1   $switch.Count       'a switch with no text at all is reachable through its identifier'
Assert-Equal ''  $switch[0].label    'an id-only node carries no label'
Assert-Equal 'id' $switch[0].source  'an id-only node reports where it was named from'
Assert-Equal $false $switch[0].labelled 'an id-only node is not labelled'

$plain = @($nodes | Where-Object { $_.label -eq 'Plain label' })
Assert-Equal ''  $plain[0].resId      'a node with no resource-id carries an empty one, not a placeholder'
Assert-Equal ''  $plain[0].resIdShort 'no identifier means no short form either'

# ---- the match rule --------------------------------------------------------------------------
$both = @(Select-UiNodesById $nodes 'rowExport')
Assert-Equal 2 $both.Count 'the default substring match reaches every row whose id contains the value'
Assert-Equal 'rowExport'    $both[0].resIdShort 'matches come back in document order (first)'
Assert-Equal 'rowExportAll' $both[1].resIdShort 'matches come back in document order (second)'

$one = @(Select-UiNodesById $nodes 'rowExport' -Exact)
Assert-Equal 1 $one.Count 'exact refuses to treat rowExport as rowExportAll - the neighbouring-row failure'
Assert-Equal 'com.sza.fastmediasorter.debug:id/rowExport' $one[0].resId 'exact took the whole-value match'

$full = @(Select-UiNodesById $nodes 'com.sza.fastmediasorter.debug:id/switchLauncherMode' -Exact)
Assert-Equal 1 $full.Count 'the full package-qualified form matches exactly'

$short = @(Select-UiNodesById $nodes 'content' -Exact)
Assert-Equal 'android:id/content' $short[0].resId `
    'the short form matches without the package, so a .debug suffix cannot break the call'

$cased = @(Select-UiNodesById $nodes 'ROWEXPORTALL')
Assert-Equal 1 $cased.Count 'matching is case-insensitive'

$byLabel = @(Select-UiNodesById $nodes 'Plain')
Assert-Equal 0 $byLabel.Count 'a node without an identifier is never matched by one'

$absent = @(Select-UiNodesById $nodes 'rowNothingHere')
Assert-Equal 0 $absent.Count 'an identifier that is not on screen matches nothing - the caller taps nothing'

Write-Host ""
Write-Host "passed: $script:passed  failed: $script:failed"
if ($script:failed -gt 0) { exit 1 }
exit 0
