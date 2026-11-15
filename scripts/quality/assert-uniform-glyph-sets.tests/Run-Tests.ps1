# Run-Tests.ps1 (S4570) - regression suite for assert-uniform-glyph-sets.ps1.
#
# Every case builds a synthetic app_v2/src tree under temp/scratch, runs the gate against it with
# -Root and removes the tree in a finally block.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-uniform-glyph-sets.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateScript = Join-Path $repoRoot 'scripts/quality/assert-uniform-glyph-sets.ps1'
$pwshExe = (Get-Process -Id $PID).Path

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

$head = '<?xml version="1.0" encoding="utf-8"?>'
$ns = 'xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto"'
$w = 'com.sza.fastmediasorter.ui.common.widget'

function New-Tree([hashtable]$files) {
    $root = Join-Path $repoRoot ("temp/scratch/glyph-sets-test-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    New-Item -ItemType Directory -Force -Path (Join-Path $root 'app_v2/src/main/res') | Out-Null
    foreach ($k in $files.Keys) {
        $full = Join-Path $root $k
        New-Item -ItemType Directory -Force -Path (Split-Path $full) | Out-Null
        [System.IO.File]::WriteAllText($full, $files[$k])
    }
    return $root
}

function Test-Case([string]$name, [hashtable]$files, [int]$expectedExit, [string]$expectedText, [string]$changed = '') {
    $root = New-Tree $files
    try {
        $out = & $pwshExe -NoProfile -File $gateScript -Root $root -ChangedFiles $changed 2>&1 | Out-String
        $ok = $LASTEXITCODE -eq $expectedExit -and (-not $expectedText -or $out.Contains($expectedText))
        Assert-That $name $ok "expected exit $expectedExit '$expectedText', got exit $($LASTEXITCODE):`n$out"
    }
    finally { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
}

$menuClean = "$head<menu $ns><item android:id=`"@+id/a`" android:icon=`"@drawable/ic_a`" android:title=`"A`"/><item android:id=`"@+id/b`" android:icon=`"@drawable/ic_b`" android:title=`"B`"/></menu>"
$menuMixed = "$head<menu $ns><item android:id=`"@+id/a`" android:icon=`"@drawable/ic_a`" android:title=`"A`"/><item android:id=`"@+id/b`" android:title=`"B`"/></menu>"
$menuSubHeader = "$head<menu $ns><item android:id=`"@+id/a`" android:icon=`"@drawable/ic_a`" android:title=`"A`"/><item android:id=`"@+id/sub`" android:title=`"S`"><menu><item android:id=`"@+id/c`" android:title=`"C`"/></menu></item></menu>"
$menuToolbar = "$head<menu $ns><item android:id=`"@+id/a`" android:icon=`"@drawable/ic_a`" android:title=`"A`" app:showAsAction=`"always`"/><item android:id=`"@+id/b`" android:title=`"B`" app:showAsAction=`"never`"/></menu>"
$rowsMixed = "$head<LinearLayout $ns android:id=`"@+id/containerX`"><LinearLayout><$w.SettingsToggleRow android:id=`"@+id/rowA`" app:str_icon=`"@drawable/ic_a`"/><$w.SettingsToggleRow android:id=`"@+id/rowB`"/></LinearLayout><$w.SettingsDropdownRow android:id=`"@+id/rowC`" app:sdr_icon=`"@drawable/ic_c`"/></LinearLayout>"
$rowsClean = $rowsMixed.Replace('android:id="@+id/rowB"/>', 'android:id="@+id/rowB" app:str_icon="@drawable/ic_b"/>')
$headersMixed = "$head<LinearLayout $ns><$w.CollapsibleSectionHeader android:id=`"@+id/h1`" app:csh_icon=`"@drawable/ic_a`"/><$w.CollapsibleSectionHeader android:id=`"@+id/h2`"/></LinearLayout>"

Test-Case 'clean menu passes' @{ 'app_v2/src/main/res/menu/m.xml' = $menuClean } 0 'PASS'
Test-Case 'mixed menu fails and names the glyph-less item' @{ 'app_v2/src/main/res/menu/m.xml' = $menuMixed } 1 'menu|app_v2/src/main/res/menu/m.xml|menu|b'
Test-Case 'a submenu header is a member of the parent level' @{ 'app_v2/src/main/res/menu/m.xml' = $menuSubHeader } 1 '|menu|sub'
Test-Case 'toolbar and overflow are two sets' @{ 'app_v2/src/main/res/menu/m.xml' = $menuToolbar } 0 'PASS'
Test-Case 'rows through an id-less wrapper form one set' @{ 'app_v2/src/main/res/layout/f.xml' = $rowsMixed } 1 'rows|app_v2/src/main/res/layout/f.xml|containerX|rowB'
Test-Case 'uniform rows pass' @{ 'app_v2/src/main/res/layout/f.xml' = $rowsClean } 0 'PASS'
Test-Case 'mixed headers fail in a flavor source set' @{ 'app_v2/src/launcherEnabled/res/layout-land/f.xml' = $headersMixed } 1 'header|app_v2/src/launcherEnabled/res/layout-land/f.xml'
Test-Case 'a finding outside the changed set is advisory' @{ 'app_v2/src/main/res/menu/m.xml' = $menuMixed } 3 'none in the changed set' 'app_v2/src/main/res/menu/other.xml'

$missing = Join-Path $repoRoot ("temp/scratch/glyph-sets-missing-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
$out = & $pwshExe -NoProfile -File $gateScript -Root $missing 2>&1 | Out-String
Assert-That 'a missing source root cannot be verified' ($LASTEXITCODE -eq 2) "got exit $($LASTEXITCODE):`n$out"

$glyphRun = $script:pass + $script:fail
if ($script:fail -gt 0) {
    Write-Host "assert-uniform-glyph-sets tests: FAIL (run=$glyphRun skipped=0, failed=$script:fail)"
    exit 1
}
Write-Host "assert-uniform-glyph-sets tests: PASS (run=$glyphRun skipped=0)"
exit 0
