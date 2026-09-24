<#
Run-Tests.ps1 - contract tests for assert-icon-contract.ps1 (S3432).

Every case builds a throwaway tree and a throwaway catalog under the system temp directory, so no
case depends on what the live vocabulary or the live layouts carry this minute. The decisive
cases are the label/glyph mismatch and the Russian name substitution - the two deviations the
contract was written from ("Назад" read as Previous).

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-icon-contract.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()
$utf8 = [System.Text.UTF8Encoding]::new($false)

$vocabulary = @(
    '{"id":"nav.back","group":"navigation","status":"active","name":{"en":"Back","ru":"Назад","uk":"Назад"},"ref":{"android":"ic_arrow_back"}}',
    '{"id":"media.previous","group":"media","status":"active","name":{"en":"Previous","ru":"Предыдущий","uk":"Попередній"},"ref":{"android":"ic_skip_previous"}}',
    '{"id":"nav.close","group":"navigation","status":"active","name":{"en":"Close","ru":"Закрыть","uk":"Закрити"},"ref":{"android":"ic_close_x"},"sharedWith":["action.clear-input"]}',
    '{"id":"action.clear-input","group":"action","status":"active","name":{"en":"Clear","ru":"Очистить","uk":"Очистити"},"ref":{"android":"ic_clear"},"sharedWith":["nav.close"]}'
)

$declaration = '{"private":[{"pattern":"ico_*","why":"fixture"}],"glyphs":{},"labels":{},"terms":{},"exceptions":[]}'

function Write-Text([string] $Path, [string] $Text) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    [System.IO.File]::WriteAllText($Path, $Text, $utf8)
}

function Get-Strings([hashtable] $Map) {
    $rows = $Map.GetEnumerator() | ForEach-Object { "    <string name=`"$($_.Key)`">$($_.Value)</string>" }
    return "<resources>`n$($rows -join "`n")`n</resources>`n"
}

function Get-Layout([string[]] $Elements) {
    return "<LinearLayout xmlns:android=`"http://schemas.android.com/apk/res/android`">`n$($Elements -join "`n")`n</LinearLayout>`n"
}

function New-Fixture {
    param(
        [string[]] $LayoutElements = @(
            '<ImageButton android:src="@drawable/ic_arrow_back" android:contentDescription="@string/back" />',
            '<ImageButton android:src="@drawable/ic_close_x" android:contentDescription="@string/clear" />',
            '<ImageView android:src="@drawable/ico_01" />'
        ),
        [hashtable] $RuStrings = @{ previous = 'Предыдущий' },
        [hashtable] $EnStrings = @{ back = 'Back'; previous = 'Previous'; close = 'Close'; clear = 'Clear' },
        [string] $Kotlin = '',
        [string] $DocMap = '',
        [string[]] $Baseline = $null,
        [string] $Declaration = $declaration
    )
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("icon-contract-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    $fixtures.Add($root)
    $repo = Join-Path $root 'repo'
    $catalog = Join-Path $root 'catalog'
    Write-Text (Join-Path $catalog 'iconography/vocabulary.jsonl') (($vocabulary -join "`n") + "`n")
    Write-Text (Join-Path $repo 'docs/icons/icon-contract-map.json') $Declaration
    foreach ($d in 'ic_arrow_back', 'ic_skip_previous', 'ic_close_x', 'ic_clear', 'ico_01', 'ic_orphan') {
        Write-Text (Join-Path $repo "app_v2/src/main/res/drawable/$d.xml") '<vector />'
    }
    $res = Join-Path $repo 'app_v2/src/main/res'
    Write-Text (Join-Path $res 'values/strings.xml') (Get-Strings $EnStrings)
    Write-Text (Join-Path $res 'values-ru/strings.xml') (Get-Strings $RuStrings)
    Write-Text (Join-Path $res 'layout/activity_a.xml') (Get-Layout $LayoutElements)
    if ($Kotlin) { Write-Text (Join-Path $repo 'app_v2/src/main/java/A.kt') $Kotlin }
    if ($DocMap) { Write-Text (Join-Path $repo 'docs/icons/doc-icon-map.json') $DocMap }
    $baselineFile = Join-Path $repo 'scripts/quality/icon-contract-baseline.txt'
    if ($null -ne $Baseline) { Write-Text $baselineFile (($Baseline -join "`n") + "`n") }
    return [pscustomobject]@{ Repo = $repo; Catalog = $catalog; Baseline = $baselineFile }
}

function Invoke-GateRun($Fixture, [string[]] $Extra = @('-Gate'), [string] $CatalogOverride = '') {
    $cat = if ($CatalogOverride) { $CatalogOverride } else { $Fixture.Catalog }
    $out = & pwsh -NoProfile -File $Gate -RepoRoot $Fixture.Repo -CatalogRoot $cat -BaselineFile $Fixture.Baseline @Extra 2>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Out = $out }
}

function Assert-Case([string] $Name, $Result, [int] $Code, [string] $Needle = '') {
    $ok = ($Result.Code -eq $Code) -and (-not $Needle -or $Result.Out.Contains($Needle))
    if ($ok) { $script:passed++; Write-Host "PASS  $Name" }
    else {
        $script:failed++
        Write-Host "FAIL  $Name  (expected exit $Code$(if ($Needle) { " with '$Needle'" }), got $($Result.Code))"
        Write-Host ($Result.Out -replace '(?m)^', '        ')
    }
}

try {
    $f = New-Fixture
    Assert-Case 'clean tree passes; sharedWith and private artwork are not findings' (Invoke-GateRun $f) 0 'PASS (0 baselined'

    $f = New-Fixture -LayoutElements @('<ImageView android:src="@drawable/ic_orphan" />')
    Assert-Case 'an unmapped drawable fails' (Invoke-GateRun $f) 1 'unmapped|app_v2|ic_orphan'

    $f = New-Fixture -LayoutElements @('<ImageButton android:src="@drawable/ic_arrow_back" android:contentDescription="@string/previous" />')
    Assert-Case 'a Back glyph labelled Previous fails' (Invoke-GateRun $f) 1 'label-glyph|app_v2|app_v2/src/main/res/layout/activity_a.xml|ic_arrow_back|previous'

    $f = New-Fixture -LayoutElements @('<Toolbar android:navigationIcon="@drawable/ic_arrow_back" android:title="@string/previous" />')
    Assert-Case 'a toolbar title does not label its navigation glyph' (Invoke-GateRun $f) 0 'PASS'

    $f = New-Fixture -RuStrings @{ previous = 'Назад' }
    Assert-Case 'Russian Previous reading Back fails' (Invoke-GateRun $f) 1 'name-substitution|app_v2|previous|ru'

    $f = New-Fixture -DocMap '{"landing":[{"title":"Icons","drawable":"ico_01"}]}'
    Assert-Case 'a doc picture taken from private artwork fails' (Invoke-GateRun $f) 1 'doc-glyph|doc-icon-map|ico_01|private'

    $f = New-Fixture -Baseline @('unmapped|app_v2|ic_gone')
    Assert-Case 'a stale baseline line fails a full run' (Invoke-GateRun $f) 1 'STALE unmapped|app_v2|ic_gone'

    $f = New-Fixture -LayoutElements @('<ImageView android:src="@drawable/ic_orphan" />')
    Assert-Case 'a finding outside the changed set is advisory' (Invoke-GateRun $f @('-Gate', '-ChangedFiles', 'docs/unrelated.md')) 3 'ADVISORY'

    $f = New-Fixture -LayoutElements @('<ImageView android:src="@drawable/ic_orphan" />')
    Assert-Case 'a finding in the changed set is charged' (Invoke-GateRun $f @('-Gate', '-ChangedFiles', 'app_v2/src/main/res/layout/activity_a.xml')) 1 'FAIL (1 new'

    $f = New-Fixture -LayoutElements @('<ImageView android:src="@drawable/ic_orphan" />') -Baseline @('# header')
    Assert-Case 'the baseline refuses to rise' (Invoke-GateRun $f @('-UpdateBaseline')) 1 'may fall, never rise'

    $f = New-Fixture -LayoutElements @('<ImageView android:src="@drawable/ic_orphan" />')
    $seed = Invoke-GateRun $f @('-UpdateBaseline')
    $after = Invoke-GateRun $f
    Assert-Case 'a missing baseline is seeded, then the gate passes' ([pscustomobject]@{ Code = [int]($seed.Code -ne 0) + $after.Code; Out = $seed.Out + $after.Out }) 0 'PASS (1 baselined'

    # S3443: ICON-RENDER rule 8 - the accessible name of a glyph-only control carries the meaning's name.
    $en = @{ back = 'Back'; back_list = 'Back to list'; previous = 'Previous'; go_home = 'Home screen'; close = 'Close'; clear = 'Clear' }
    $f = New-Fixture -EnStrings $en -LayoutElements @('<ImageButton android:src="@drawable/ic_arrow_back" android:contentDescription="@string/back_list" />')
    Assert-Case 'an accessible name that adds its object passes' (Invoke-GateRun $f) 0 'PASS (0 baselined'

    $f = New-Fixture -EnStrings $en -LayoutElements @('<ImageButton android:src="@drawable/ic_arrow_back" android:contentDescription="@string/go_home" />')
    Assert-Case 'a glyph-only control named without its meaning fails' (Invoke-GateRun $f) 1 'accessible-name|app_v2|app_v2/src/main/res/layout/activity_a.xml|ic_arrow_back|go_home'

    $f = New-Fixture -EnStrings $en -RuStrings @{ back = 'Вернуться' } -LayoutElements @('<ImageButton android:src="@drawable/ic_arrow_back" android:contentDescription="@string/back" />')
    Assert-Case 'a Russian accessible name without the Russian name fails' (Invoke-GateRun $f) 1 "ru 'Вернуться' lacks 'назад'"

    # S3484: a declared form of the name agrees with its object; an undeclared one still fails.
    $prevLayout = @('<ImageButton android:src="@drawable/ic_skip_previous" android:contentDescription="@string/previous_page" />')
    $prevEn = @{ previous_page = 'Previous page'; close = 'Close'; clear = 'Clear' }
    $f = New-Fixture -EnStrings $prevEn -RuStrings @{ previous_page = 'Предыдущая страница' } -LayoutElements $prevLayout
    Assert-Case 'an inflected Russian name without a forms declaration fails' (Invoke-GateRun $f) 1 'accessible-name|app_v2|app_v2/src/main/res/layout/activity_a.xml|ic_skip_previous|previous_page'
    $formsDecl = '{"private":[],"glyphs":{},"labels":{},"terms":{},"forms":{"media.previous":{"ru":["предыдущая"]}},"exceptions":[]}'
    $f = New-Fixture -EnStrings $prevEn -RuStrings @{ previous_page = 'Предыдущая страница' } -LayoutElements $prevLayout -Declaration $formsDecl
    Assert-Case 'a declared form of the name passes' (Invoke-GateRun $f) 0 'PASS (0 baselined'
    $f = New-Fixture -Declaration '{"private":[],"glyphs":{},"labels":{},"terms":{},"forms":{"media.nowhere":{"ru":["x"]}},"exceptions":[]}'
    Assert-Case 'a form of an unknown meaning cannot verify' (Invoke-GateRun $f) 2 'forms.media.nowhere: unknown meaning'

    $f = New-Fixture -EnStrings $en -LayoutElements @('<Row xmlns:app="http://schemas.android.com/apk/res-auto" android:clickable="true" app:str_icon="@drawable/ic_arrow_back" app:str_title="@string/go_home" android:contentDescription="@string/go_home" />')
    Assert-Case 'a settings row with a visible str_title is not glyph-only' (Invoke-GateRun $f) 0 'PASS (0 baselined'

    $f = New-Fixture -EnStrings $en -LayoutElements @('<Button android:text="@string/go_home" android:drawableStart="@drawable/ic_arrow_back" android:contentDescription="@string/go_home" />', '<ImageView android:src="@drawable/ic_arrow_back" android:contentDescription="@string/go_home" />')
    Assert-Case 'visible text and a non-control picture are not judged' (Invoke-GateRun $f) 0 'PASS (0 baselined'

    $f = New-Fixture -EnStrings $en -LayoutElements @() -Kotlin "b.setImageResource(R.drawable.ic_arrow_back)`nb.contentDescription = getString(R.string.go_home)`n"
    Assert-Case 'a Kotlin description two lines under its glyph is judged' (Invoke-GateRun $f) 1 'accessible-name|app_v2|app_v2/src/main/java/A.kt|ic_arrow_back|go_home'

    $lg = 'label-glyph|app_v2|app_v2/src/main/res/layout/activity_a.xml|ic_arrow_back|previous'
    $prevBack = @('<ImageButton android:src="@drawable/ic_arrow_back" android:contentDescription="@string/previous" />')
    $f = New-Fixture -EnStrings $en -LayoutElements $prevBack -Baseline @($lg)
    Assert-Case 'a new dimension is not seeded without -SeedDimension' (Invoke-GateRun $f @('-UpdateBaseline')) 1 'may fall, never rise'

    $f = New-Fixture -EnStrings $en -LayoutElements $prevBack -Baseline @($lg)
    $seed = Invoke-GateRun $f @('-UpdateBaseline', '-SeedDimension', 'accessible-name')
    $after = Invoke-GateRun $f
    Assert-Case '-SeedDimension seeds a dimension the baseline lacks' ([pscustomobject]@{ Code = [int]($seed.Code -ne 0) + $after.Code; Out = $seed.Out + $after.Out }) 0 'PASS (2 baselined'

    $f = New-Fixture -EnStrings $en -LayoutElements ($prevBack + @('<ImageButton android:src="@drawable/ic_arrow_back" android:contentDescription="@string/go_home" />')) -Baseline @($lg, 'accessible-name|app_v2|app_v2/src/main/res/layout/activity_a.xml|ic_arrow_back|previous')
    Assert-Case '-SeedDimension refuses a dimension the baseline already carries' (Invoke-GateRun $f @('-UpdateBaseline', '-SeedDimension', 'accessible-name')) 1 'may fall, never rise'

    # S3482: a Compose Material vector is a glyph too, mapped through the same 'glyphs' declaration.
    $composeDecl = '{"private":[],"glyphs":{"Icons.Filled.ArrowBack":"nav.back"},"labels":{},"terms":{},"exceptions":[]}'
    $f = New-Fixture -EnStrings $en -LayoutElements @() -Declaration $composeDecl -Kotlin "import androidx.compose.material.icons.filled.Star`nIcon(Icons.Default.Star, contentDescription = null)`n"
    Assert-Case 'an unmapped Material vector fails under its normalised name' (Invoke-GateRun $f) 1 'compose-unmapped|app_v2|Icons.Filled.Star'

    $f = New-Fixture -EnStrings $en -LayoutElements @() -Declaration $composeDecl -Kotlin "Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_list))`n"
    Assert-Case 'a mapped AutoMirrored vector named with its meaning passes' (Invoke-GateRun $f) 0 'PASS (0 baselined'

    $f = New-Fixture -EnStrings $en -LayoutElements @() -Declaration $composeDecl -Kotlin "Icon(`n    imageVector = Icons.Filled.ArrowBack,`n    contentDescription = stringResource(R.string.go_home)`n)`n"
    Assert-Case 'a Material vector named without its meaning fails' (Invoke-GateRun $f) 1 'compose-accessible-name|app_v2|app_v2/src/main/java/A.kt|Icons.Filled.ArrowBack|go_home'

    $f = New-Fixture -EnStrings $en -LayoutElements @() -Declaration $composeDecl -Kotlin "Chip(Icons.Filled.ArrowBack, R.string.previous)`n"
    Assert-Case 'a Material vector labelled with another meaning fails' (Invoke-GateRun $f) 1 'compose-label-glyph|app_v2|app_v2/src/main/java/A.kt|Icons.Filled.ArrowBack|previous'

    $f = New-Fixture -EnStrings $en -LayoutElements @() -Declaration $composeDecl -Kotlin "Icon(Icons.Default.Star, contentDescription = null)`n" -Baseline @('# header', 'label-glyph|app_v2|x|ic_arrow_back|previous')
    $seed = Invoke-GateRun $f @('-UpdateBaseline', '-SeedDimension', 'compose-unmapped')
    Assert-Case '-SeedDimension seeds a compose dimension' $seed 0 'BASELINE WRITTEN'

    $f = New-Fixture -EnStrings $en -LayoutElements @() -Declaration $composeDecl -Kotlin "Icon(`n    imageVector = Icons.Filled.ArrowBack,`n    contentDescription = stringResource(R.string.go_home)`n)`nIcon(Icons.Default.Star, contentDescription = null)`n" -Baseline @('# header', 'label-glyph|app_v2|x|ic_arrow_back|previous')
    $seed = Invoke-GateRun $f @('-UpdateBaseline', '-SeedDimension', 'compose-unmapped,compose-accessible-name')
    $after = Invoke-GateRun $f
    Assert-Case '-SeedDimension takes a list of new dimensions' ([pscustomobject]@{ Code = [int]($seed.Code -ne 0) + $after.Code; Out = $seed.Out + $after.Out }) 0 'PASS (2 baselined'

    $f = New-Fixture
    $empty = Join-Path (Split-Path -Parent $f.Repo) 'no-catalog'
    New-Item -ItemType Directory -Force -Path $empty | Out-Null
    Assert-Case 'no vocabulary under the catalog root cannot verify' (Invoke-GateRun $f @('-Gate') $empty) 2 'COULD NOT VERIFY'
}
finally {
    foreach ($p in $fixtures) { Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-icon-contract tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
