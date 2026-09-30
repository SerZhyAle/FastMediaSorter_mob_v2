#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for rule `tint` of assert-icon-style.ps1 (S3430), judged by
    scripts/quality/lib/icon-tint-rule.ps1.

.DESCRIPTION
    Writes throwaway vector files under temp/scratch and judges each with Test-GlyphTint, so the rule
    is pinned without a python venv or the live drawable set.

    Cases:
      C1 a literal white fill with no android:tint fails and names the paint
      C2 the same file with android:tint on <vector> passes
      C3 a theme-attribute fill passes without a tint
      C4 a transparent fill beside a theme-attribute stroke passes
      C5 an android:tint quoted only inside an XML comment does not count - the file still fails
      C6 a non-vector drawable (a <shape>) is not judged
      C7 a literal black fill fails

    Exit codes (CLAUDE.md Rule 7):
      0  every case passed
      1  at least one case failed
      2  could not run - the rule library is missing
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$lib = Join-Path (Split-Path -Parent $PSScriptRoot) 'lib/icon-tint-rule.ps1'
if (-not (Test-Path -LiteralPath $lib)) {
    Write-Error "rule library not found: $lib" -ErrorAction Continue
    exit 2
}
. $lib
$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$sandbox = Join-Path $repoRoot ('temp/scratch/icon-tint-rule-tests-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $sandbox -Force | Out-Null

$failed = 0
function Write-Glyph([string] $name, [string] $body) {
    $p = Join-Path $sandbox "$name.xml"
    [System.IO.File]::WriteAllText($p, $body)
    return $p
}
function Assert-Case([string] $id, [bool] $ok, [string] $detail) {
    if ($ok) { Write-Host "  PASS $id" } else { Write-Host "  FAIL $id - $detail"; $script:failed++ }
}

$head = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"'
try {
    $r = Test-GlyphTint (Write-Glyph 'c1' "$head><path android:fillColor=`"@android:color/white`" android:pathData=`"M0,0h24v24h-24z`"/></vector>")
    Assert-Case 'C1 baked white fails' ($null -ne $r -and $r -match '@android:color/white') "got '$r'"

    $r = Test-GlyphTint (Write-Glyph 'c2' "$head android:tint=`"?attr/colorControlNormal`"><path android:fillColor=`"@android:color/white`" android:pathData=`"M0,0h24v24h-24z`"/></vector>")
    Assert-Case 'C2 declared tint passes' ($null -eq $r) "got '$r'"

    $r = Test-GlyphTint (Write-Glyph 'c3' "$head><path android:fillColor=`"?android:attr/textColorPrimary`" android:pathData=`"M0,0h24v24h-24z`"/></vector>")
    Assert-Case 'C3 theme fill passes' ($null -eq $r) "got '$r'"

    $r = Test-GlyphTint (Write-Glyph 'c4' "$head><path android:fillColor=`"@android:color/transparent`" android:strokeColor=`"?attr/colorOnSurface`" android:pathData=`"M0,0h24v24h-24z`"/></vector>")
    Assert-Case 'C4 transparent fill passes' ($null -eq $r) "got '$r'"

    $r = Test-GlyphTint (Write-Glyph 'c5' "<!-- the original carries android:tint=`"?attr/colorControlNormal`" -->$head><path android:fillColor=`"@color/white`" android:pathData=`"M0,0h24v24h-24z`"/></vector>")
    Assert-Case 'C5 comment tint ignored' ($null -ne $r) 'a tint inside a comment was counted'

    $r = Test-GlyphTint (Write-Glyph 'c6' '<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="#FFFFFF"/></shape>')
    Assert-Case 'C6 shape not judged' ($null -eq $r) "got '$r'"

    $r = Test-GlyphTint (Write-Glyph 'c7' "$head><path android:fillColor=`"#FF000000`" android:pathData=`"M0,0h24v24h-24z`"/></vector>")
    Assert-Case 'C7 baked black fails' ($null -ne $r -and $r -match '#FF000000') "got '$r'"
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failed) { Write-Host "icon-tint-rule tests: FAIL ($failed)"; exit 1 }
Write-Host 'icon-tint-rule tests: PASS (7 cases)'
exit 0
