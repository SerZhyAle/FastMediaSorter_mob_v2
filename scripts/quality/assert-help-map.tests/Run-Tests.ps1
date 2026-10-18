<#
.SYNOPSIS
    Contract tests for assert-help-map.ps1 (S4288, CONTEXT-HELP rule 19).

.DESCRIPTION
    Every case builds a throwaway tree under the system temp directory - a manifest, one dialog class, a help
    map, a baseline and the pages - so no case depends on what the live map carries this minute.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - every case passed
      1 - at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-help-map.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

$manifest = @'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application>
    <activity android:name=".ui.main.MainActivity" android:exported="true" />
    <activity android:name=".ui.other.OtherActivity" android:exported="false" />
</application></manifest>
'@

$dialog = @'
package com.example
class FooDialog : DialogFragment() {
}
'@

$map = @'
{ "schemaVersion": 1, "index": "index", "surfaces": [
    { "id": "MainActivity", "form": "page", "help": "main" },
    { "id": "FooDialog", "form": "inherit", "parent": "MainActivity", "reason": "Opened only from the main screen." }
] }
'@

$page = @'
<!DOCTYPE html><html><head><link rel="stylesheet" href="../help.css"></head><body>
<h1 id="top">Main</h1><a href="index.html">Index</a> <a href="#top">Top</a>
<a href="https://example.org/docs">Open on the site</a>
</body></html>
'@

function New-Fixture {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ('s4288-' + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($root)
    Set-File $root 'app_v2/src/main/AndroidManifest.xml' $manifest
    Set-File $root 'app_v2/src/main/java/com/example/FooDialog.kt' $dialog
    Set-File $root 'app_v2/src/main/assets/help/help-map.json' $map
    Set-File $root 'app_v2/src/main/assets/help/help.css' 'body { margin: 0; }'
    foreach ($locale in @('en', 'ru', 'uk')) {
        Set-File $root "app_v2/src/main/assets/help/$locale/main.html" $page
        Set-File $root "app_v2/src/main/assets/help/$locale/index.html" '<html><body><a href="main.html">Main</a></body></html>'
    }
    Set-File $root 'scripts/quality/help-map-baseline.txt' "# baseline`nOtherActivity"
    return $root
}

$wearRoutes = @'
package com.example.wear
fun graph() {
    composable(WearRoutes.HOME) { HomeScreen() }
    composable(
        route = SettingsRoutes.ABOUT,
    ) { AboutScreen() }
}
'@

$wearMap = @'
{ "schemaVersion": 1, "surfaces": [
    { "id": "WearRoutes.HOME", "name": "Home", "form": "none", "reason": "A short list of labelled entries." },
    { "id": "SettingsRoutes.ABOUT", "name": "About", "form": "none", "reason": "Version text and one labelled button." }
] }
'@

# A fixture with a watch module: two routes (one split over lines) and a map holding one none row for each.
function New-WearFixture {
    $root = New-Fixture
    Set-File $root 'wear/src/main/java/com/example/wear/Nav.kt' $wearRoutes
    Set-File $root 'wear/help-map.json' $wearMap
    return $root
}

function Set-File([string]$root, [string]$relative, [string]$text) {
    $path = Join-Path $root $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    Set-Content -LiteralPath $path -Value $text -Encoding utf8
}

function Invoke-Case([string]$Name, [string]$Root, [int]$ExpectedExit, [string]$ExpectedText, [string[]]$GateArgs = @()) {
    $out = & pwsh -NoProfile -File $Gate -Root $Root @GateArgs 2>&1 | Out-String
    $code = $LASTEXITCODE
    $ok = ($code -eq $ExpectedExit) -and (-not $ExpectedText -or $out -match [regex]::Escape($ExpectedText))
    if ($ok) {
        $script:passed++
        Write-Host "  PASS $Name"
    } else {
        $script:failed++
        Write-Host "  FAIL $Name (expected exit $ExpectedExit with '$ExpectedText', got $code)"
        Write-Host ($out.Trim() -replace '(?m)^', '       ')
    }
}

try {
    Invoke-Case 'clean fixture passes' (New-Fixture) 0 'assert-help-map: PASS'

    $root = New-Fixture
    Set-File $root 'scripts/quality/help-map-baseline.txt' '# baseline'
    Invoke-Case 'a surface with no row fails' $root 1 'UNMAPPED'

    $root = New-Fixture
    Set-File $root 'app_v2/src/main/assets/help/help-map.json' ($map.Replace('"form": "inherit", "parent": "MainActivity", "reason": "Opened only from the main screen."', '"form": "none"'))
    Invoke-Case 'a none row without reason fails' $root 1 'NO-REASON'

    $root = New-Fixture
    Remove-Item -LiteralPath (Join-Path $root 'app_v2/src/main/assets/help/uk/main.html')
    Invoke-Case 'a page missing in uk fails' $root 1 'MISSING-PAGE'

    $root = New-Fixture
    Set-File $root 'app_v2/src/main/assets/help/en/main.html' ($page.Replace('</body>', '<script>var x = 1;</script></body>'))
    Invoke-Case 'a page with a script fails' $root 1 'SCRIPT'

    $root = New-Fixture
    Set-File $root 'scripts/quality/help-map-baseline.txt' "# baseline`nOtherActivity`nMainActivity"
    Invoke-Case 'a stale baseline row fails' $root 1 'STALE-BASELINE'

    $root = New-Fixture
    Set-File $root 'app_v2/src/main/assets/help/en/main.html' ($page.Replace('href="index.html"', 'href="gone.html"'))
    Invoke-Case 'an unresolved local link fails' $root 1 'BROKEN-LINK'

    $root = New-Fixture
    Set-File $root 'app_v2/src/main/assets/help/en/main.html' ($page.Replace('<h1 id="top">', '<img src="https://example.org/a.png"><h1 id="top">'))
    Invoke-Case 'a remotely loaded image fails' $root 1 'REMOTE-URL'

    Invoke-Case 'a watch fixture with a row per route passes' (New-WearFixture) 0 '2 watch routes'

    $root = New-WearFixture
    Set-File $root 'wear/help-map.json' ($wearMap.Replace('{ "id": "SettingsRoutes.ABOUT", "name": "About", "form": "none", "reason": "Version text and one labelled button." }', '{ "id": "WearRoutes.HOME", "name": "Home", "form": "none", "reason": "Repeated." }'))
    Invoke-Case 'a watch route with no row fails' $root 1 'wear SettingsRoutes.ABOUT'

    $root = New-WearFixture
    Set-File $root 'wear/help-map.json' ($wearMap.Replace('] }', ', { "id": "WearRoutes.GONE", "name": "Gone", "form": "none", "reason": "No such route." }] }'))
    Invoke-Case 'a watch row with no route fails' $root 1 'wear row ''WearRoutes.GONE'' names no composable route'

    $root = New-WearFixture
    Set-File $root 'wear/help-map.json' ($wearMap.Replace(', "reason": "A short list of labelled entries."', ''))
    Invoke-Case 'a watch none row without reason fails' $root 1 'NO-REASON'

    $root = New-WearFixture
    Set-File $root 'wear/help-map.json' ($wearMap.Replace('"form": "none", "reason": "A short list of labelled entries."', '"form": "page", "help": "home"'))
    Invoke-Case 'a watch row that is not none fails' $root 1 'BAD-FORM'

    $root = New-WearFixture
    Remove-Item -LiteralPath (Join-Path $root 'wear/help-map.json')
    Invoke-Case 'a watch module without a watch map cannot be verified' $root 2 'COULD NOT VERIFY'

    $root = New-WearFixture
    Set-File $root 'wear/help-map.json' ($wearMap.Replace('"id": "WearRoutes.HOME"', '"id": "WearRoutes.OLD"'))
    Invoke-Case 'a watch finding is not charged when no input is in the changed set' $root 3 'not charged' @('-ChangedFiles', 'README.md')
    Invoke-Case 'a watch finding is charged when the declaring file is in the changed set' $root 1 'UNMAPPED' @('-ChangedFiles', 'wear/src/main/java/com/example/wear/Nav.kt')
} finally {
    foreach ($f in $fixtures) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-help-map tests: $(if ($failed -gt 0) { 'FAIL' } else { 'PASS' }) (run=$($passed + $failed) skipped=0)"
if ($failed -gt 0) { exit 1 }
exit 0
