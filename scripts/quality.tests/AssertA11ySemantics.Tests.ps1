#requires -Version 7.0
<#
.SYNOPSIS
    S3371: contract suite for scripts/quality/assert-a11y-semantics.ps1.

.DESCRIPTION
    This gate refuses a layout somebody has already shipped, and its one escape hatch - the
    decorative allowlist - is exactly the lever an author reaches for when the count is
    inconvenient. So both halves are DEMONSTRATED here rather than asserted: every case builds a
    fixture tree, a fixture baseline and a fixture allowlist, then runs the real script and reads
    the real exit code. A refusal path that is never executed is the same unobserved green the
    gate was written to end.

    The fixture tree is not a git repository on purpose. The scoped -ChangedFiles path treats a
    file with no HEAD copy as wholly new, which is the fail-closed reading, so the same fixtures
    exercise both the full-scan verdict and the changed-set verdict.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every test passed.
      1  at least one test failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:pass = 0
$script:fail = 0

function Test-Case([string]$Name, [scriptblock]$Body) {
    try {
        & $Body
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    }
    catch {
        $script:fail++
        Write-Host "  FAIL  $Name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Assert-Equal($Expected, $Actual, [string]$What) {
    if ($Expected -ne $Actual) { throw "$What - expected: $Expected | actual: $Actual" }
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gate = Join-Path $repoRoot 'scripts/quality/assert-a11y-semantics.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("a11y-semantics-fixture-{0}" -f $PID)
$layoutDir = Join-Path $fixtureRoot 'app_v2/src/main/res/layout'
$kotlinDir = Join-Path $fixtureRoot 'app_v2/src/main/java/com/sza/fastmediasorter/ui/widget'
$baseline = Join-Path $fixtureRoot 'baseline.txt'
$allowlist = Join-Path $fixtureRoot 'allowlist.txt'

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $layoutDir -Force | Out-Null
    New-Item -ItemType Directory -Path $kotlinDir -Force | Out-Null
    Set-Content -LiteralPath $allowlist -Value @('# fixture allowlist') -Encoding UTF8
    Set-Content -LiteralPath $baseline -Value '0' -Encoding UTF8
}

function Set-Layout([string]$Name, [string[]]$Body) {
    $lines = @('<?xml version="1.0" encoding="utf-8"?>',
        '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" android:layout_width="match_parent" android:layout_height="match_parent">') +
        $Body + @('</LinearLayout>')
    Set-Content -LiteralPath (Join-Path $layoutDir $Name) -Value $lines -Encoding UTF8
}

function Set-CustomView([string]$Name, [string[]]$Body) {
    Set-Content -LiteralPath (Join-Path $kotlinDir $Name) -Value $Body -Encoding UTF8
}

function Set-Baseline([int]$Value) { Set-Content -LiteralPath $baseline -Value "$Value" -Encoding UTF8 }
function Set-Allowlist([string[]]$Rows) { Set-Content -LiteralPath $allowlist -Value $Rows -Encoding UTF8 }

function Invoke-Gate([string[]]$Extra) {
    $argv = @('-NoProfile', '-NonInteractive', '-File', $gate, '-Gate',
        '-RepoRoot', $fixtureRoot, '-BaselinePath', $baseline, '-AllowlistPath', $allowlist)
    if ($Extra) { $argv += $Extra }
    $output = & $pwshExe @argv 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

$namedIconButton = '    <ImageButton android:id="@+id/btnShare" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_share" android:contentDescription="@string/share" />'

try {
    Test-Case 'an icon-only clickable without an accessible name is refused' {
        Reset-Fixture
        Set-Layout 'dialog_share.xml' @(
            '    <ImageButton android:id="@+id/btnShare" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_share" />'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode 'unnamed icon-button verdict'
        if ($r.Output -notmatch 'contentDescription') { throw "the refusal did not say what is missing - output: $($r.Output)" }
    }

    Test-Case 'the same element with a contentDescription passes' {
        Reset-Fixture
        Set-Layout 'dialog_share.xml' @($namedIconButton)
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "named icon-button verdict - output: $($r.Output)"
    }

    Test-Case 'a marked-decorative element with an allowlist row passes' {
        Reset-Fixture
        Set-Allowlist @('dialog_share.xml:imgOrnament  # the frame glyph repeats the dialog title icon and says nothing new')
        Set-Layout 'dialog_share.xml' @(
            '    <ImageView android:id="@+id/imgOrnament" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_ornament" android:clickable="true" android:importantForAccessibility="no" />'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "allowlisted decorative verdict - output: $($r.Output)"
    }

    Test-Case 'the same element WITHOUT an allowlist row is still refused' {
        Reset-Fixture
        Set-Layout 'dialog_share.xml' @(
            '    <ImageView android:id="@+id/imgOrnament" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_ornament" android:clickable="true" android:importantForAccessibility="no" />'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode 'unlisted decorative verdict'
        if ($r.Output -notmatch 'allowlist') { throw "the refusal did not name the missing row - output: $($r.Output)" }
    }

    Test-Case 'contentDescription="@null" is a marking, not a name' {
        Reset-Fixture
        Set-Layout 'dialog_share.xml' @(
            '    <ImageButton android:id="@+id/btnShare" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_share" android:contentDescription="@null" />'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode 'nulled contentDescription verdict'
    }

    Test-Case 'an undersized clickable is refused' {
        Reset-Fixture
        Set-Layout 'dialog_share.xml' @(
            '    <ImageButton android:id="@+id/btnShare" android:layout_width="24dp" android:layout_height="24dp" android:src="@drawable/ic_share" android:contentDescription="@string/share" />'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode 'undersized target verdict'
        if ($r.Output -notmatch '48dp') { throw "the refusal did not name the floor - output: $($r.Output)" }
    }

    Test-Case 'a minWidth/minHeight that reaches the floor clears the undersized rule' {
        Reset-Fixture
        Set-Layout 'dialog_share.xml' @(
            '    <ImageButton android:id="@+id/btnShare" android:layout_width="24dp" android:layout_height="24dp" android:minWidth="48dp" android:minHeight="48dp" android:src="@drawable/ic_share" android:contentDescription="@string/share" />'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "min-dimension verdict - output: $($r.Output)"
    }

    # 0dp is ConstraintLayout's match-constraint directive. Judging it as a size reported every
    # stretched row in the tree on the first survey run, which is how it was found.
    Test-Case 'a 0dp match-constraint width is not an undersized target' {
        Reset-Fixture
        Set-Layout 'dialog_share.xml' @(
            '    <ImageButton android:id="@+id/btnShare" android:layout_width="0dp" android:layout_height="56dp" android:src="@drawable/ic_share" android:contentDescription="@string/share" />'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "match-constraint verdict - output: $($r.Output)"
    }

    Test-Case 'an element carrying its own text is not an icon-only element' {
        Reset-Fixture
        Set-Layout 'dialog_share.xml' @(
            '    <com.google.android.material.button.MaterialButton android:id="@+id/btnShare" android:layout_width="wrap_content" android:layout_height="48dp" app:icon="@drawable/ic_share" android:text="@string/share" />'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "labelled button verdict - output: $($r.Output)"
    }

    Test-Case 'a baselined pre-existing finding passes' {
        Reset-Fixture
        Set-Baseline 1
        Set-Layout 'dialog_share.xml' @(
            '    <ImageButton android:id="@+id/btnShare" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_share" />'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "baselined verdict - output: $($r.Output)"
        if ($r.Output -notmatch 'baseline 1 \| actual 1') { throw "the run did not report the comparison - output: $($r.Output)" }
    }

    Test-Case 'one finding beyond the baseline is refused' {
        Reset-Fixture
        Set-Baseline 1
        Set-Layout 'dialog_share.xml' @(
            '    <ImageButton android:id="@+id/btnShare" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_share" />',
            '    <ImageButton android:id="@+id/btnCopy" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_copy" />'
        )
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode 'above-baseline verdict'
    }

    Test-Case 'a custom interactive view with no accessibility delegate is refused' {
        Reset-Fixture
        Set-CustomView 'RatingStripView.kt' @(
            'package com.sza.fastmediasorter.ui.widget',
            '',
            'import android.view.View',
            '',
            'class RatingStripView(context: Context) : View(context) {',
            '    override fun onTouchEvent(event: MotionEvent): Boolean = true',
            '}'
        )
        # -List because the full-scan refusal prints the comparison, not 408 baselined lines;
        # naming the finding is what -List and the recovery hint are for.
        $r = Invoke-Gate @('-List')
        Assert-Equal 1 $r.ExitCode 'undelegated custom view verdict'
        if ($r.Output -notmatch 'RatingStripView') { throw "the listing did not name the class - output: $($r.Output)" }
        if ($r.Output -notmatch 'custom-view-no-a11y') { throw "the listing did not name the rule - output: $($r.Output)" }
    }

    Test-Case 'the same custom view with an accessibility delegate passes' {
        Reset-Fixture
        Set-CustomView 'RatingStripView.kt' @(
            'package com.sza.fastmediasorter.ui.widget',
            '',
            'import android.view.View',
            'import androidx.core.view.AccessibilityDelegateCompat',
            '',
            'class RatingStripView(context: Context) : View(context) {',
            '    override fun onTouchEvent(event: MotionEvent): Boolean = true',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "delegated custom view verdict - output: $($r.Output)"
    }

    # A VerticalSeekBar inherits SeekBar's node, role and range. Demanding a delegate of it would
    # demand a second copy of something that already works, which is how a gate earns its reputation
    # for noise.
    Test-Case 'a subclass of a framework control is not a roleless custom view' {
        Reset-Fixture
        Set-CustomView 'VerticalSeekBar.kt' @(
            'package com.sza.fastmediasorter.ui.widget',
            '',
            'class VerticalSeekBar(context: Context) : AppCompatSeekBar(context) {',
            '    override fun onTouchEvent(event: MotionEvent): Boolean = true',
            '}'
        )
        $r = Invoke-Gate
        Assert-Equal 0 $r.ExitCode "framework-control subclass verdict - output: $($r.Output)"
    }

    Test-Case 'an allowlist row with no reason is refused' {
        Reset-Fixture
        Set-Allowlist @('dialog_share.xml:imgOrnament')
        Set-Layout 'dialog_share.xml' @($namedIconButton)
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode 'reasonless allowlist row verdict'
        if ($r.Output -notmatch 'no reason') { throw "the refusal did not say why - output: $($r.Output)" }
    }

    Test-Case 'an allowlist row that is not file:id is refused' {
        Reset-Fixture
        Set-Allowlist @('imgOrnament  # names no layout, so it would exempt every screen at once')
        Set-Layout 'dialog_share.xml' @($namedIconButton)
        $r = Invoke-Gate
        Assert-Equal 1 $r.ExitCode 'malformed allowlist row verdict'
    }

    Test-Case 'the changed set is judged against the HEAD copies, not the baseline' {
        Reset-Fixture
        Set-Baseline 99
        Set-Layout 'dialog_share.xml' @(
            '    <ImageButton android:id="@+id/btnShare" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_share" />'
        )
        $r = Invoke-Gate @('-ChangedFiles', 'app_v2/src/main/res/layout/dialog_share.xml')
        Assert-Equal 1 $r.ExitCode 'scoped growth verdict'
        if ($r.Output -notmatch 'new finding') { throw "the refusal did not report the delta - output: $($r.Output)" }
    }

    Test-Case 'a changed set holding no layout and no Kotlin source passes trivially' {
        Reset-Fixture
        Set-Layout 'dialog_share.xml' @(
            '    <ImageButton android:id="@+id/btnShare" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_share" />'
        )
        $r = Invoke-Gate @('-ChangedFiles', 'docs/RULES_DIGEST.md')
        Assert-Equal 0 $r.ExitCode "non-subject changed set verdict - output: $($r.Output)"
    }

    Test-Case 'a named changed file that does not exist cannot verify rather than passing' {
        Reset-Fixture
        $r = Invoke-Gate @('-ChangedFiles', 'app_v2/src/main/res/layout/absent.xml')
        Assert-Equal 2 $r.ExitCode 'absent changed file verdict'
    }

    Test-Case 'a missing scan root cannot verify rather than passing' {
        Reset-Fixture
        Remove-Item -LiteralPath (Join-Path $fixtureRoot 'app_v2') -Recurse -Force
        $r = Invoke-Gate
        Assert-Equal 2 $r.ExitCode 'missing scan root verdict'
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertA11ySemantics.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
