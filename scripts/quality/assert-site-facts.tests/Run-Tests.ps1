<#
.SYNOPSIS
    Contract tests for assert-site-facts.ps1.
.DESCRIPTION
Run-Tests.ps1 - contract tests for assert-site-facts.ps1 (S4100, availability dimension S4107).
# Subject: docs/flavors/public-editions.psd1

Every case builds a throwaway site under the system temp directory with its own build matrix and
editions declaration and runs the gate against it, so no case depends on what the live site says this
minute. The decisive cases are the refusals - a variant declared nowhere, a count that is not the
declared one in a digit, a word and a tag-split digit, a minimum Android version no edition has, a label
spelled another way, an edition that is not public named as one - and the claims that must stay silent:
a subset count, a colour-theme "eight variants", a feature requirement "Android 13+".

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-site-facts.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

$matrixText = @'
{
  "flavors": ["standard", "noLegal", "lite", "photos", "legacy", "vr", "xr", "foss"],
  "minSdk": { "standard": 26, "noLegal": 26, "lite": 26, "photos": 26, "legacy": 23, "vr": 29, "xr": 26, "foss": 23 },
  "flags": ["SUPPORT_LAUNCHER"],
  "matrix": { "standard": { "SUPPORT_LAUNCHER": true }, "noLegal": { "SUPPORT_LAUNCHER": true }, "lite": { "SUPPORT_LAUNCHER": false } }
}
'@

# The availability dimension reads the source-set map from a real module build file; the fixture
# borrows the repository's own, so a case never depends on a hand-written gradle subset.
$GradleFile = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\app_v2\build.gradle.kts')).Path
$badgeRow = '<div class="doc-meta-row"><span class="doc-badge">Area</span><span class="doc-edition-badge">{0}</span>{1}<span class="doc-badge doc-badge-sm">Recipe #01</span></div>'

$declText = @'
@{
    Editions  = @(
        @{ Flavor = 'standard'; Name = 'Standard' }
        @{ Flavor = 'noLegal'; Name = 'noLegal' }
        @{ Flavor = 'lite'; Name = 'Lite' }
        @{ Flavor = 'photos'; Name = 'Photos' }
        @{ Flavor = 'legacy'; Name = 'Legacy' }
        @{ Flavor = 'vr'; Name = 'VR' }
        @{ Flavor = 'foss'; Name = 'FOSS' }
    )
    NotPublic = @( @{ Flavor = 'xr'; Reason = 'not listed' } )
    Mainline  = 'standard'
    Companions = @('wear', 'watchface')
    CompanionNames = @{ wear = 'FastMedia Wear' }
    Surfaces  = @('index*.html', 'documentation/**/*.html', 'docs/content/**/*.md')
    Landing   = @('index*.html')
    LabelScript = 'assets/site-ui.js'
}
'@

function New-Fixture {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ('s4100-' + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($root)
    foreach ($dir in 'docs/flavors', 'docs/content/recipes', 'docs/content/recipes-ru', 'documentation/area', 'assets', 'app_v2') {
        New-Item -ItemType Directory -Path (Join-Path $root $dir) -Force | Out-Null
    }
    $files = [ordered]@{
        'docs/flavors/flavor-matrix.json'      = $matrixText
        'docs/flavors/public-editions.psd1'    = $declText
        'index.html'                           = "<html><body><p>Runs on Android 8.0+ in all <strong>7</strong> Android editions, and on Android 6.0+ in two of them.</p>`n<div data-download-edition=`"noLegal`"></div><div data-download-edition=`"wear`"></div></body></html>"
        'assets/site-ui.js'                    = "var labels = { standard: 'Standard', vr: 'VR', legacy: 'Legacy', wear: 'Wear OS', noLegal: 'noLegal' };`n"
        'documentation/area/page.html'         = "<html><body><p>Compare the seven editions; семи редакций; семи редакцій.</p><p>Five editions carry it. A theme of восьми вариантов. Needs Android 13+ for this.</p></body></html>"
        'docs/content/recipes/recipe.md'       = "---`npage_id: a.b`ncanonical_url: documentation/area/recipe.html`navailability: SUPPORT_LAUNCHER`navailability_note: Standard and noLegal`n---`nBody text.`n"
        'docs/content/recipes-ru/recipe.md'    = "---`npage_id: a.b`ncanonical_url: documentation/area/recipe-ru.html`n---`nТекст.`n"
        'documentation/area/recipe.html'       = "<html><body>$($badgeRow -f 'Standard and noLegal', '')</body></html>"
        'documentation/area/recipe-ru.html'    = "<html><body>$($badgeRow -f 'Standard и noLegal', '')</body></html>"
    }
    foreach ($name in $files.Keys) {
        Set-Content -LiteralPath (Join-Path $root $name) -Value $files[$name] -Encoding utf8 -NoNewline
    }
    Copy-Item -LiteralPath $GradleFile -Destination (Join-Path $root 'app_v2/build.gradle.kts')
    return $root
}

function Invoke-Case {
    param(
        [string]$Name,
        [scriptblock]$Mutate,
        [int]$ExpectedExit,
        [string]$ExpectedText,
        [string]$Dimension = 'all'
    )
    $root = New-Fixture
    if ($Mutate) { & $Mutate $root }
    $output = & pwsh -NoProfile -File $Gate -Root $root -Dimension $Dimension 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    $ok = ($exitCode -eq $ExpectedExit) -and (-not $ExpectedText -or $output.Contains($ExpectedText))
    if ($ok) {
        $script:passed++
        Write-Host "  PASS  $Name"
    }
    else {
        $script:failed++
        Write-Host "  FAIL  $Name - expected: exit $ExpectedExit '$ExpectedText' | actual: exit $exitCode" -ForegroundColor Red
        Write-Host $output
    }
}

function Add-Text([string]$Root, [string]$Relative, [string]$Text) {
    Set-Content -LiteralPath (Join-Path $Root $Relative) -Value $Text -Encoding utf8 -NoNewline
}

function Edit-Text([string]$Root, [string]$Relative, [string]$Old, [string]$New) {
    $path = Join-Path $Root $Relative
    $text = [IO.File]::ReadAllText($path)
    if (-not $text.Contains($Old)) { throw "fixture edit: '$Old' not found in $Relative" }
    [IO.File]::WriteAllText($path, $text.Replace($Old, $New))
}

try {
    Invoke-Case 'clean site passes' $null 0 'PASS'

    # declaration
    Invoke-Case 'matrix variant declared nowhere fails' {
        param($r) Edit-Text $r 'docs/flavors/flavor-matrix.json' '"foss"]' '"foss", "extra"]'
    } 1 "UNDECLARED variant 'extra'" 'declaration'

    Invoke-Case 'declared variant absent from the matrix fails' {
        param($r) Edit-Text $r 'docs/flavors/flavor-matrix.json' '"xr", "foss"]' '"xr"]'
    } 1 "STALE variant 'foss'" 'declaration'

    Invoke-Case 'variant declared both public and not public fails' {
        param($r) Edit-Text $r 'docs/flavors/public-editions.psd1' "@{ Flavor = 'xr'; Reason = 'not listed' }" "@{ Flavor = 'xr'; Reason = 'x' }, @{ Flavor = 'vr'; Reason = 'y' }"
    } 1 "variant 'vr' is declared 2 times" 'declaration'

    Invoke-Case 'two editions sharing a display name fails' {
        param($r) Edit-Text $r 'docs/flavors/public-editions.psd1' "Name = 'Lite'" "Name = 'standard'"
    } 1 "display name 'standard' is used by more than one edition" 'declaration'

    Invoke-Case 'mainline that is not public fails' {
        param($r) Edit-Text $r 'docs/flavors/public-editions.psd1' "Mainline  = 'standard'" "Mainline  = 'xr'"
    } 1 "Mainline 'xr' is not a public edition" 'declaration'

    # count
    Invoke-Case 'digit count that is not the declared one fails' {
        param($r) Edit-Text $r 'index.html' '<strong>7</strong>' '<strong>8</strong>'
    } 1 'states 8 editions, the declaration holds 7' 'count'

    Invoke-Case 'word count that is not the declared one fails' {
        param($r) Edit-Text $r 'documentation/area/page.html' 'Compare the seven editions' 'Compare the eight editions'
    } 1 "'eight editions' states 8 editions" 'count'

    Invoke-Case 'russian count that is not the declared one fails' {
        param($r) Add-Text $r 'documentation/area/ru.html' '<p>Здесь восемь редакций приложения.</p>'
    } 1 'documentation/area/ru.html:1' 'count'

    Invoke-Case 'ukrainian count that is not the declared one fails' {
        param($r) Add-Text $r 'documentation/area/uk.html' '<p>Тут вісім редакцій застосунку.</p>'
    } 1 'documentation/area/uk.html:1' 'count'

    Invoke-Case 'a count inside a meta tag attribute fails' {
        param($r) Add-Text $r 'documentation/area/meta.html' '<meta name="description" content="Guides across all 8 Android editions.">'
    } 1 'documentation/area/meta.html:1' 'count'

    Invoke-Case 'a count split by tags fails' {
        param($r) Add-Text $r 'documentation/area/split.html' "<p>`n<em>nine</em> <b>editions</b>`n</p>"
    } 1 'documentation/area/split.html:2' 'count'

    Invoke-Case 'a markdown recipe is a surface' {
        param($r) Add-Text $r 'docs/content/recipes/two.md' "---`navailability: all`n---`nAll 9 editions run it.`n"
    } 1 'docs/content/recipes/two.md:4' 'count'

    Invoke-Case 'declaring a sixth edition moves the expected count' {
        param($r) Edit-Text $r 'docs/flavors/public-editions.psd1' "        @{ Flavor = 'foss'; Name = 'FOSS' }`n" ''
        Edit-Text $r 'docs/flavors/public-editions.psd1' "@( @{ Flavor = 'xr'; Reason = 'not listed' } )" "@( @{ Flavor = 'xr'; Reason = 'a' }, @{ Flavor = 'foss'; Reason = 'b' } )"
    } 1 'the declaration holds 6 public ones' 'count'

    Invoke-Case 'a subset count is not judged' {
        param($r) Add-Text $r 'documentation/area/subset.html' '<p>Only five editions and 3 editions carry this.</p>'
    } 0 'PASS' 'count'

    Invoke-Case 'a colour theme of eight variants is not an edition claim' {
        param($r) Add-Text $r 'documentation/area/theme.html' '<p>The watch has eight variants and восьми вариантов of colour.</p>'
    } 0 'PASS' 'count'

    # android-min
    Invoke-Case 'landing minimum no edition has fails' {
        param($r) Edit-Text $r 'index.html' 'Android 8.0+' 'Android 5.0+'
    } 1 "'Android 5.0+' is no minimum of a public edition" 'android-min'

    Invoke-Case 'landing integer form is judged too' {
        param($r) Edit-Text $r 'index.html' 'Android 6.0+' 'Android 7+'
    } 1 "'Android 7+' is no minimum" 'android-min'

    Invoke-Case 'landing legacy minimum passes' {
        param($r) Edit-Text $r 'index.html' 'Android 6.0+' 'Android 6+'
    } 0 'PASS' 'android-min'

    Invoke-Case 'dotted minimum outside the landing fails' {
        param($r) Add-Text $r 'documentation/area/min.html' '<p>Works on Android 7.0+ only.</p>'
    } 1 "documentation/area/min.html:1: 'Android 7.0+'" 'android-min'

    Invoke-Case 'feature requirement outside the landing is not a minimum claim' {
        param($r) Add-Text $r 'documentation/area/feature.html' '<p>Predictive back needs Android 15+ here.</p>'
    } 0 'PASS' 'android-min'

    Invoke-Case 'a minimum moves with the matrix' {
        param($r) Edit-Text $r 'docs/flavors/flavor-matrix.json' ': 26' ': 24'
    } 1 "'Android 8.0+' is no minimum" 'android-min'

    # names
    Invoke-Case 'label key outside the declaration fails' {
        param($r) Edit-Text $r 'assets/site-ui.js' "wear: 'Wear OS'" "wear: 'Wear OS', gadget: 'Gadget'"
    } 1 "label key 'gadget'" 'names'

    Invoke-Case 'label spelled another way fails' {
        param($r) Edit-Text $r 'assets/site-ui.js' "standard: 'Standard'" "standard: 'Std'"
    } 1 "label 'Std' for 'standard' - the declared name is 'Standard'" 'names'

    Invoke-Case 'download card of an unpublished variant fails' {
        param($r) Edit-Text $r 'index.html' 'data-download-edition="wear"' 'data-download-edition="xr"'
    } 1 "data-download-edition 'xr'" 'names'

    Invoke-Case 'wrong-case edition in an availability note fails' {
        param($r) Edit-Text $r 'docs/content/recipes/recipe.md' 'availability_note: Standard and noLegal' 'availability_note: Standard & NoLegal'
    } 1 "edition written 'NoLegal', the declared name is 'noLegal'" 'names'

    Invoke-Case 'variant that is not public in an availability note fails' {
        param($r) Edit-Text $r 'docs/content/recipes/recipe.md' 'availability_note: Standard and noLegal' 'availability_note: Standard and xr'
    } 1 "'xr' is declared not public but is named in an availability note" 'names'

    Invoke-Case 'variant that is not public named as an edition fails' {
        param($r) Add-Text $r 'documentation/area/xr.html' '<p>Get the XR edition today.</p>'
    } 1 "'XR edition' names a variant declared not public" 'names'

    Invoke-Case 'Android XR is not the variant' {
        param($r) Add-Text $r 'documentation/area/immersive.html' '<p>Immersive XR needs a headset; the Android XR platform is not an edition.</p>'
    } 0 'PASS' 'names'

    # availability (S4107)
    Invoke-Case 'a badge that matches the build in both languages passes' $null 0 'PASS' 'availability'

    Invoke-Case 'a hand-edited badge fails' {
        param($r) Edit-Text $r 'documentation/area/recipe.html' '>Standard and noLegal<' '>All editions<'
    } 1 "the edition badge says 'All editions', the build gives 'Standard and noLegal'" 'availability'

    Invoke-Case 'a translated badge is judged in its own language' {
        param($r) Edit-Text $r 'documentation/area/recipe-ru.html' 'Standard и noLegal' 'Standard and noLegal'
    } 1 "recipe-ru.html: the edition badge says 'Standard and noLegal', the build gives 'Standard и noLegal'" 'availability'

    Invoke-Case 'a source-set term resolves through the build file' {
        param($r) Edit-Text $r 'docs/content/recipes/recipe.md' 'availability: SUPPORT_LAUNCHER' 'availability: launcherEnabled'
    } 0 'PASS' 'availability'

    Invoke-Case 'a term the build does not declare fails' {
        param($r) Edit-Text $r 'docs/content/recipes/recipe.md' 'availability: SUPPORT_LAUNCHER' 'availability: SUPPORT_TELEPORT'
    } 1 "'SUPPORT_TELEPORT' is not 'all', a flag" 'availability'

    Invoke-Case 'an english recipe without availability fails' {
        param($r) Edit-Text $r 'docs/content/recipes/recipe.md' "availability: SUPPORT_LAUNCHER`n" ''
    } 1 "recipe.md: no 'availability:'" 'availability'

    Invoke-Case 'the retired flavor line fails' {
        param($r) Edit-Text $r 'docs/content/recipes-ru/recipe.md' "page_id: a.b`n" "page_id: a.b`nflavor: Все редакции`n"
    } 1 "'flavor:' is retired" 'availability'

    Invoke-Case 'a translation declaring availability fails' {
        param($r) Edit-Text $r 'docs/content/recipes-ru/recipe.md' "page_id: a.b`n" "page_id: a.b`navailability: all`n"
    } 1 "'availability:' belongs to the English recipe only" 'availability'

    Invoke-Case 'a device outside the vocabulary fails' {
        param($r) Edit-Text $r 'docs/content/recipes/recipe.md' "availability: SUPPORT_LAUNCHER`n" "availability: SUPPORT_LAUNCHER`ndevices: toaster`n"
    } 1 "devices 'toaster'" 'availability'

    Invoke-Case 'a declared device without its badge fails' {
        param($r) Edit-Text $r 'docs/content/recipes/recipe.md' "availability: SUPPORT_LAUNCHER`n" "availability: SUPPORT_LAUNCHER`ndevices: watch`n"
    } 1 "device badges '', the recipe declares 'Watch'" 'availability'

    Invoke-Case 'a companion renders its declared name' {
        param($r) Edit-Text $r 'docs/content/recipes/recipe.md' 'availability: SUPPORT_LAUNCHER' 'availability: companion:wear'
        Edit-Text $r 'documentation/area/recipe.html' '>Standard and noLegal<' '>FastMedia Wear<'
        Edit-Text $r 'documentation/area/recipe-ru.html' '>Standard и noLegal<' '>FastMedia Wear<'
    } 0 'PASS' 'availability'

    Invoke-Case 'a missing module build file cannot verify' {
        param($r) Remove-Item -LiteralPath (Join-Path $r 'app_v2/build.gradle.kts')
    } 2 'COULD NOT VERIFY' 'availability'

    # cannot verify
    Invoke-Case 'missing matrix cannot verify' {
        param($r) Remove-Item -LiteralPath (Join-Path $r 'docs/flavors/flavor-matrix.json')
    } 2 'COULD NOT VERIFY'

    Invoke-Case 'unreadable declaration cannot verify' {
        param($r) Add-Text $r 'docs/flavors/public-editions.psd1' '@{ Editions = '
    } 2 'COULD NOT VERIFY'

    Invoke-Case 'minSdk without a recorded Android release cannot verify' {
        param($r) Edit-Text $r 'docs/flavors/flavor-matrix.json' '"standard": 26' '"standard": 99'
    } 2 'COULD NOT VERIFY'
}
finally {
    foreach ($f in $fixtures) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-site-facts tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
