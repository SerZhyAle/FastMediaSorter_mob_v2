# Run-Tests.ps1 (S2974) - regression suite for assert-docs-termbase.ps1.
#
# The live corpus is empty when this gate is born, so the live tree can only ever show it green.
# Every case therefore builds a synthetic tree under temp/scratch - a flavor matrix, one English
# strings.xml, a termbase and optionally corpus pages - runs the gate against it, and removes the
# tree in a finally block.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-docs-termbase.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateScript = Join-Path $repoRoot 'scripts/quality/assert-docs-termbase.ps1'
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

$matrixJson = '{ "flavors": ["standard", "noLegal", "lite", "photos", "legacy", "vr", "foss"] }'
$stringsXml = @'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="browse_title">File browser</string>
    <string name="fav_title">Favorites</string>
    <string name="color_label">Text color</string>
</resources>
'@

function New-BaseRecords {
    return @(
        [ordered]@{ id = 'file-browser'; canonical_en = 'file browser'; category = 'navigation_surface'
            definition_en = 'The screen that lists the files of one place.'; disambiguation_en = 'Not a web browser.'
            forbidden_synonyms_en = @('explorer'); ui_strings = @('browse_title'); locales = [ordered]@{ ru = 'ru-fb'; uk = 'uk-fb' }
            flavors = @('all'); platforms = @('phone'); related_terms = @('favorite') }
        [ordered]@{ id = 'favorite'; canonical_en = 'favorite'; category = 'entity'
            definition_en = 'A file you marked to find it quickly.'; disambiguation_en = 'Not a bookmark in a web browser.'
            forbidden_synonyms_en = @('favourite'); ui_strings = @('fav_title'); locales = [ordered]@{ ru = 'ru-fav'; uk = 'uk-fav' }
            flavors = @('all'); platforms = @('phone', 'wear'); related_terms = @('file-browser') }
        [ordered]@{ id = 'color'; canonical_en = 'color'; category = 'ui_component'
            definition_en = 'The tint of text or a background.'; disambiguation_en = 'Not a theme.'
            forbidden_synonyms_en = @('colour'); ui_strings = @('color_label'); locales = [ordered]@{ ru = 'ru-col'; uk = 'uk-col' }
            flavors = @('standard', 'lite'); platforms = @('phone'); related_terms = @() }
    )
}

$cleanPage = "# Sorting`n`nOpen the file browser and mark a favorite.`n"

# Each case: Mutate edits the records (optional), Pages maps documentation-relative names to text,
# Raw replaces the whole termbase text, NoTermbase/NoCorpus remove a part of the tree, Changed is
# the -ChangedFiles value, Exit is the code the gate must return.
$cases = @(
    @{ Name = 'clean termbase and clean page pass'; Pages = @{ 'a.md' = $cleanPage }; Exit = 0 }
    @{ Name = "'explorer' on a page fails"; Pages = @{ 'a.md' = "Open the explorer.`n" }; Exit = 1 }
    @{ Name = "'favourite' on a page fails"; Pages = @{ 'a.md' = "Mark it as a favourite.`n" }; Exit = 1 }
    @{ Name = "plural 'colours' on a page fails"; Pages = @{ 'a.md' = "Pick one of the colours.`n" }; Exit = 1 }
    @{ Name = "'colourful' is not the word 'colour'"; Pages = @{ 'a.md' = "A colourful desktop.`n" }; Exit = 0 }
    @{ Name = 'hyphenated file-explorer fails'; Pages = @{ 'a.md' = "Unlike a file-explorer app.`n" }; Exit = 1 }
    @{ Name = 'word inside a fenced block passes'; Pages = @{ 'a.md' = "Text.`n`n``````text`nexplorer`n```````n" }; Exit = 0 }
    @{ Name = 'word inside a backtick span passes'; Pages = @{ 'a.md' = "The class is ``ExplorerView``.`n" }; Exit = 0 }
    @{ Name = 'word inside a link target passes'; Pages = @{ 'a.md' = "See [the guide](https://example.com/explorer).`n" }; Exit = 0 }
    @{ Name = 'word inside a bare URL passes'; Pages = @{ 'a.md' = "Visit https://example.com/favourite now.`n" }; Exit = 0 }
    @{ Name = 'word inside a multi-line HTML comment passes'; Pages = @{ 'a.md' = "Text <!-- old`nexplorer name`n--> end.`n" }; Exit = 0 }
    @{ Name = 'termbase-ignore on the line above passes'; Pages = @{ 'a.md' = "<!-- termbase-ignore: explorer -->`nOther apps call it an explorer.`n" }; Exit = 0 }
    @{ Name = 'termbase-ignore by term id on the same line passes'; Pages = @{ 'a.md' = "British readers write favourite. <!-- termbase-ignore: favorite -->`n" }; Exit = 0 }
    @{ Name = 'termbase-ignore reaches one line only'; Pages = @{ 'a.md' = "<!-- termbase-ignore: explorer -->`nFirst line.`nOpen the explorer.`n" }; Exit = 1 }
    @{ Name = 'finding keeps its line number after blanked spans and a comment'
        Pages = @{ 'a.md' = "Run ``a long code span here`` now.`n<!-- one`ntwo -->`nOpen the explorer.`n" }; Exit = 1; Expect = 'a.md:4:' }
    @{ Name = 'unterminated fence hides the rest of the page'; Pages = @{ 'a.md' = "Text.`n``````text`nexplorer`nmore explorer`n" }; Exit = 0 }
    @{ Name = 'missing required field fails'; Mutate = { param($r) $r[0].Remove('definition_en') }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 'unknown field fails'; Mutate = { param($r) $r[0]['synonyms'] = @('x') }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 'unknown ui_strings key fails'; Mutate = { param($r) $r[0]['ui_strings'] = @('no_such_key') }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 'canonical_en absent from its keyed English value fails'; Mutate = { param($r) $r[2]['ui_strings'] = @('fav_title') }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 'dangling related_terms id fails'; Mutate = { param($r) $r[2]['related_terms'] = @('ghost') }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 'flavor not in the matrix fails'; Mutate = { param($r) $r[2]['flavors'] = @('standard', 'pro') }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = "'all' mixed with a flavor fails"; Mutate = { param($r) $r[2]['flavors'] = @('all', 'lite') }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 'forbidden synonym equal to another canonical_en fails'; Mutate = { param($r) $r[0]['forbidden_synonyms_en'] = @('explorer', 'favorite') }; Pages = @{ 'a.md' = "Open it.`n" }; Exit = 1 }
    @{ Name = 'definition using a forbidden synonym fails'; Mutate = { param($r) $r[2]['definition_en'] = 'The colour of text.' }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 'duplicate id fails'; Mutate = { param($r) $r[2]['id'] = 'favorite' }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 'invalid re: pattern fails'; Mutate = { param($r) $r[2]['forbidden_synonyms_en'] = @('re:colo(u') }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 're: pattern matches its morphology'; Mutate = { param($r) $r[2]['forbidden_synonyms_en'] = @('re:\bcolou?red\b') }; Pages = @{ 'a.md' = "A coloured frame.`n" }; Exit = 1 }
    @{ Name = 'replacement hint fails the page and names the replacement'
        Mutate = { param($r) $r[2]['forbidden_synonyms_en'] = @('colour', 'organise => organize') }
        Pages = @{ 'a.md' = "Organise your photos.`n" }; Exit = 1; Expect = "use 'organize'" }
    @{ Name = 'replacement hint with an empty side fails the schema'
        Mutate = { param($r) $r[2]['forbidden_synonyms_en'] = @('organise => ') }; Pages = @{ 'a.md' = $cleanPage }; Exit = 1 }
    @{ Name = 'replacement hint equal to another canonical_en fails on its word, not its hint'
        Mutate = { param($r) $r[2]['forbidden_synonyms_en'] = @('favorite => color') }; Pages = @{ 'a.md' = "Open it.`n" }; Exit = 1 }
    @{ Name = 'missing termbase cannot verify'; NoTermbase = $true; Pages = @{ 'a.md' = $cleanPage }; Exit = 2 }
    @{ Name = 'non-JSON termbase line cannot verify'; Raw = "{`"id`": `"x`"`n"; Pages = @{ 'a.md' = $cleanPage }; Exit = 2 }
    @{ Name = 'changed set judges only its own page'; Pages = @{ 'a.md' = $cleanPage; 'b.md' = "Open the explorer.`n" }
        Changed = 'documentation/a.md'; Exit = 0 }
    @{ Name = 'changed set naming the dirty page fails'; Pages = @{ 'a.md' = $cleanPage; 'b.md' = "Open the explorer.`n" }
        Changed = 'documentation/a.md,documentation/b.md'; Exit = 1 }
    @{ Name = 'changed termbase re-judges the whole corpus'; Pages = @{ 'a.md' = $cleanPage; 'sub/b.md' = "Open the explorer.`n" }
        Changed = 'docs/termbase.jsonl'; Exit = 1 }
    @{ Name = 'missing corpus root passes with zero pages'; NoCorpus = $true; Exit = 0 }
)

$scratchRoot = Join-Path $repoRoot 'temp/scratch'
if (-not (Test-Path -LiteralPath $scratchRoot)) { New-Item -ItemType Directory -Path $scratchRoot | Out-Null }
$utf8 = New-Object Text.UTF8Encoding($false)

Write-Host "assert-docs-termbase suite: $($cases.Count) case(s)"
foreach ($case in $cases) {
    $tree = Join-Path $scratchRoot ("docs-termbase-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
    try {
        New-Item -ItemType Directory -Path (Join-Path $tree 'docs/flavors') -Force | Out-Null
        New-Item -ItemType Directory -Path (Join-Path $tree 'app_v2/src/main/res/values') -Force | Out-Null
        [IO.File]::WriteAllText((Join-Path $tree 'docs/flavors/flavor-matrix.json'), $matrixJson, $utf8)
        [IO.File]::WriteAllText((Join-Path $tree 'app_v2/src/main/res/values/strings.xml'), $stringsXml, $utf8)

        if (-not $case.ContainsKey('NoTermbase')) {
            if ($case.ContainsKey('Raw')) {
                $termbaseText = $case.Raw
            }
            else {
                $records = New-BaseRecords
                if ($case.ContainsKey('Mutate')) { & $case.Mutate $records }
                $termbaseText = (($records | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 5 }) -join "`n") + "`n"
            }
            [IO.File]::WriteAllText((Join-Path $tree 'docs/termbase.jsonl'), $termbaseText, $utf8)
        }

        if (-not $case.ContainsKey('NoCorpus')) {
            New-Item -ItemType Directory -Path (Join-Path $tree 'documentation') -Force | Out-Null
            if ($case.ContainsKey('Pages')) {
                foreach ($name in $case.Pages.Keys) {
                    $pagePath = Join-Path $tree "documentation/$name"
                    New-Item -ItemType Directory -Path (Split-Path $pagePath -Parent) -Force | Out-Null
                    [IO.File]::WriteAllText($pagePath, $case.Pages[$name], $utf8)
                }
            }
        }

        $gateArgs = @('-NoProfile', '-File', $gateScript, '-RepoRoot', $tree)
        if ($case.ContainsKey('Changed')) { $gateArgs += @('-ChangedFiles', $case.Changed) }
        $output = & $pwshExe @gateArgs 2>&1
        $code = $LASTEXITCODE
        $text = ($output | Out-String).Trim()
        $ok = ($code -eq $case.Exit) -and (-not $case.ContainsKey('Expect') -or $text.Contains($case.Expect))
        Assert-That $case.Name $ok ("expected exit {0}{1}, got {2}: {3}" -f $case.Exit,
            $(if ($case.ContainsKey('Expect')) { " and output '$($case.Expect)'" } else { '' }), $code, $text)
    }
    finally {
        if (Test-Path -LiteralPath $tree) { Remove-Item -LiteralPath $tree -Recurse -Force }
    }
}

Write-Host ("assert-docs-termbase suite: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
