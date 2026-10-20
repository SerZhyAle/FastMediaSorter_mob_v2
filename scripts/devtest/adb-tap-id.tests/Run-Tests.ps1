#requires -Version 7.0
<#
.SYNOPSIS
    S1879 matcher suite for `adb.ps1 tap-id` - selecting a node by its resource-id.

.DESCRIPTION
    Subject: scripts/devtest/lib/ui-tree.ps1, scripts/devtest/adb.ps1

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

# ---- the soft keyboard's region (S4290) ------------------------------------------------------------
# The IME block is the shape recorded on emulator-5554 (Android 15, Gboard) with the keyboard shown.
# The neighbouring window's region is constructed to the same shape: it must never be read as the keyboard's.
$imeDump = @'
  Window #6 Window{ab5c413 u0 com.android.settings/com.android.settings.homepage.SettingsHomepageActivity}:
    mOwnerUid=10140 showForAllUsers=false package=com.android.settings appop=NONE
    touchable region=SkRegion((0,0,1080,2424))
    isOnScreen=true
    isVisible=true
  Window #7 Window{737a0f2 u0 InputMethod}:
    mOwnerUid=10156 showForAllUsers=false package=com.google.android.inputmethod.latin appop=NONE
    mGivenContentInsets=[0,1399][0,0] mGivenVisibleInsets=[0,1399][0,0]
    touchable region=SkRegion((0,1541,1080,2424))
    mHasSurface=true isReadyForDisplay()=true mWindowRemovalAllowed=false
    isOnScreen=true
    isVisible=true
  Window #8 Window{c0285d u0 com.android.settings/com.android.settings.homepage.SettingsHomepageActivity}:
    isVisible=false
'@
$shown = Get-ImeTouchRegion $imeDump
Assert-Equal $true  $shown.visible 'an open keyboard is read as visible'
Assert-Equal 'com.google.android.inputmethod.latin' $shown.package 'the keyboard reports its own package, so the refusal can name it'
Assert-Equal 1 @($shown.rects).Count 'only the InputMethod block is read - the neighbouring window region is not the keyboard'
Assert-Equal 1541 $shown.rects[0].y1 'the region top is the keyboard top, not the window top'

Assert-Equal $true  (Test-PointUnderImeRegion -X 540  -Y 1600 -Region $shown) 'a centre inside the keyboard region is under it'
Assert-Equal $false (Test-PointUnderImeRegion -X 540  -Y 1540 -Region $shown) 'a centre one pixel above the keyboard top is clear of it'
Assert-Equal $true  (Test-PointUnderImeRegion -X 1079 -Y 2423 -Region $shown) 'the last pixel inside the keyboard is under it'
Assert-Equal $false (Test-PointUnderImeRegion -X 1080 -Y 2000 -Region $shown) 'the right edge is exclusive - a centre there is clear of the keyboard'
Assert-Equal $false (Test-PointUnderImeRegion -X 540  -Y 2424 -Region $shown) 'the bottom edge is exclusive'

# A keyboard with its window present but hidden prints no touchable region: nothing is under it.
$hiddenDump = @'
  Window #7 Window{737a0f2 u0 InputMethod}:
    mOwnerUid=10156 showForAllUsers=false package=com.google.android.inputmethod.latin appop=NONE
    mHasSurface=false isReadyForDisplay()=false mWindowRemovalAllowed=false
    isOnScreen=false
    isVisible=false
'@
$hidden = Get-ImeTouchRegion $hiddenDump
Assert-Equal $false $hidden.visible 'a hidden keyboard is not visible, even though its window exists'
Assert-Equal $false (Test-PointUnderImeRegion -X 540 -Y 2000 -Region $hidden) 'a hidden keyboard covers no point'

$absentIme = Get-ImeTouchRegion ''
Assert-Equal $false $absentIme.visible 'a dump with no InputMethod window reads as no keyboard'

# A keyboard split into two pieces: every rectangle on the line counts, not only the first.
$splitDump = @'
  Window #7 Window{737a0f2 u0 InputMethod}:
    mOwnerUid=10156 showForAllUsers=false package=org.example.ime appop=NONE
    touchable region=SkRegion((0,1541,1080,2000),(0,2000,1080,2424))
    isVisible=true
'@
$split = Get-ImeTouchRegion $splitDump
Assert-Equal 2 @($split.rects).Count 'both rectangles of a split region are kept'
Assert-Equal $true (Test-PointUnderImeRegion -X 540 -Y 2100 -Region $split) 'a centre in the second piece is under the keyboard'

# A visible keyboard with an empty region covers nothing: the tap goes to the node.
$emptyDump = @'
  Window #7 Window{737a0f2 u0 InputMethod}:
    mOwnerUid=10156 showForAllUsers=false package=com.google.android.inputmethod.latin appop=NONE
    touchable region=SkRegion()
    isVisible=true
'@
$empty = Get-ImeTouchRegion $emptyDump
Assert-Equal $true  $empty.visible 'a visible keyboard with no region is still visible'
Assert-Equal $false (Test-PointUnderImeRegion -X 540 -Y 2000 -Region $empty) 'an empty region covers no point - the tap is not refused'

Write-Host ""
Write-Host "adb-tap-id.tests: $(if ($script:failed -gt 0) { 'FAIL' } else { 'PASS' }) (run=$($script:passed + $script:failed) skipped=0)"
if ($script:failed -gt 0) { exit 1 }
exit 0
