#requires -Version 7.0
<#
.SYNOPSIS
    Gate: the documentation termbase is well-formed and no corpus page uses a forbidden synonym (S2974).

.DESCRIPTION
    The user documentation corpus is written by some twenty separate agent sessions, one topic
    ticket each (S2946-S2967). Left alone, each picks its own name for the same thing - "explorer"
    for the file browser, "favourite" for favorite, "flavor" for edition - and the reader gets
    contradictory instructions. docs/termbase.jsonl holds one record per concept with its canonical
    en-US name; this gate makes the record binding in two halves.

    SCHEMA HALF. Every line of the termbase is one JSON object with a fixed field set. Beyond types
    and enums it checks the references a hand-edit breaks silently: related_terms must name records
    that exist, ui_strings must name string keys the app declares, and - the screen wins (S2974
    ADR-1) - a record naming on-screen strings must use a canonical name that actually appears in
    at least one of their English values, so a UI rename surfaces here instead of in a page that no
    longer matches the screen. Edition names come from docs/flavors/flavor-matrix.json, never from
    a list written here. A forbidden synonym may not equal another record's canonical name, and no
    canonical name or definition may contain a forbidden synonym.

    SCAN HALF. Every corpus page is judged for forbidden synonyms in prose only: fenced blocks,
    backtick spans, link and image targets, page:/term: targets, bare URLs, HTML comments and HTML
    tags are skipped. A recipe keeps its prose inside a YAML front matter, so there the snake_case
    keys and every whole line of a machine key (page_id, shot_id, url and the like) are skipped too:
    the key 'flavor:' opens every recipe and an id such as 'built-in-mini-apps' is not prose. A
    literal synonym matches case-insensitively on letter boundaries with an optional plural s/es;
    an entry prefixed 're:' is a .NET regex. An entry of the form 'word => replacement' names the
    word to suggest instead of the record's canonical name - a British spelling such as
    'organise => organize' is wrong everywhere, but telling its author to write the host record's
    name instead would be wrong advice. A deliberate quote is excused with
    <!-- termbase-ignore: X --> on the same line or the line above, where X is the synonym or the
    id of the term whose synonyms are excused.

    SCOPE. -ChangedFiles narrows the scan to the changed corpus pages, so one closure is not charged
    for another session's in-flight page. A changed termbase widens it back to the whole corpus:
    a newly forbidden word must be fixed everywhere in the change that forbids it. A missing corpus
    root is not an error - the corpus is written after this gate, and zero pages judged is the
    honest verdict for an empty tree.

.PARAMETER Termbase
    Termbase path, relative to -RepoRoot unless rooted. Default docs/termbase.jsonl.

.PARAMETER CorpusRoot
    Directories holding the corpus pages (*.md, recursive), relative to -RepoRoot unless rooted;
    an array or a comma-joined string. Default docs/content/recipes and documentation: the recipes
    are the sources, and documentation/ is scanned for any hand-written page beside the generated
    HTML. With documentation/ alone the gate judged zero pages and passed every closure (S3530).

.PARAMETER ChangedFiles
    The closure's changed set. Accepts an array or post-change.ps1's comma-joined single string.
    Only .md members under a -CorpusRoot are scanned, unless the set carries the termbase itself.
    Omit to scan the whole corpus.

.PARAMETER Quiet
    Print the verdict line only.

.PARAMETER RepoRoot
    Repository root. Defaults to the directory two levels above this script.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-docs-termbase.ps1

.NOTES
    Exit codes:
      0 - the termbase is valid and no judged page uses a forbidden synonym
      1 - at least one schema finding or forbidden synonym, each printed as FAIL <path>:<line>: ..
      2 - cannot verify: the termbase or docs/flavors/flavor-matrix.json is missing or unreadable,
          a termbase line is not a JSON object, no string resources exist while a record names
          ui_strings, or a judged page cannot be read
#>
[CmdletBinding()]
param(
    [string] $Termbase = 'docs/termbase.jsonl',
    [string[]] $CorpusRoot = @('docs/content/recipes', 'documentation'),
    [string[]] $ChangedFiles = @(),
    [switch] $Quiet,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$gateName = 'assert-docs-termbase'
$started = [Diagnostics.Stopwatch]::StartNew()

function Resolve-RepoPath([string] $Path) {
    if ([IO.Path]::IsPathRooted($Path)) { return $Path }
    return (Join-Path $RepoRoot $Path)
}

function Get-Relative([string] $Full) {
    $rootFull = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
    $itemFull = [IO.Path]::GetFullPath($Full)
    if ($itemFull.StartsWith($rootFull, [StringComparison]::OrdinalIgnoreCase)) {
        return $itemFull.Substring($rootFull.Length).TrimStart('\', '/').Replace('\', '/')
    }
    return $itemFull.Replace('\', '/')
}

function Exit-CannotVerify([string] $Reason) {
    Write-Host "${gateName}: cannot verify - $Reason" -ForegroundColor Yellow
    exit 2
}

function Test-IsList($Value) {
    return ($null -ne $Value) -and ($Value -isnot [string]) -and ($Value -is [System.Collections.IList])
}

function Test-IsStringList($Value) {
    if (-not (Test-IsList $Value)) { return $false }
    foreach ($item in $Value) {
        if ($item -isnot [string] -or [string]::IsNullOrWhiteSpace($item)) { return $false }
    }
    return $true
}

function Test-IsText($Value) {
    return ($Value -is [string]) -and -not [string]::IsNullOrWhiteSpace($Value)
}

# A synonym matches whole words only: no letter, digit or underscore may touch either end, so
# "colour" does not fire inside "colourful" while "explorer" still fires in "file-explorer".
$wordBefore = '(?<![\p{L}\p{N}_])'
$wordAfter = '(?:s|es)?(?![\p{L}\p{N}_])'

function Get-SynonymBody([string] $Synonym) {
    return ([regex]::Escape($Synonym.Trim()) -replace '\\ ', '\s+')
}

function New-SynonymRegex([string] $Synonym) {
    $options = [Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [Text.RegularExpressions.RegexOptions]::CultureInvariant
    if ($Synonym.StartsWith('re:')) {
        return [regex]::new($Synonym.Substring(3), $options)
    }
    return [regex]::new("$wordBefore$(Get-SynonymBody $Synonym)$wordAfter", $options)
}

# ---------------------------------------------------------------- inputs

$termbasePath = Resolve-RepoPath $Termbase
if (-not (Test-Path -LiteralPath $termbasePath -PathType Leaf)) {
    Exit-CannotVerify "termbase missing: $Termbase"
}
$termbaseRel = Get-Relative $termbasePath

$matrixPath = Join-Path $RepoRoot 'docs/flavors/flavor-matrix.json'
try {
    $knownFlavors = @((Get-Content -LiteralPath $matrixPath -Raw -Encoding UTF8 | ConvertFrom-Json).flavors)
}
catch {
    Exit-CannotVerify "flavor matrix unreadable: docs/flavors/flavor-matrix.json ($($_.Exception.Message))"
}
if ($knownFlavors.Count -eq 0) { Exit-CannotVerify 'flavor matrix declares no flavors: docs/flavors/flavor-matrix.json' }

$allowedFields = @('id', 'canonical_en', 'category', 'definition_en', 'disambiguation_en', 'forbidden_synonyms_en',
    'ui_strings', 'locales', 'flavors', 'platforms', 'related_terms', 'reference_visual')
$requiredFields = $allowedFields | Where-Object { $_ -ne 'reference_visual' }
$categories = @('entity', 'navigation_surface', 'ui_component', 'distribution_edition', 'hardware_platform', 'network_stream')
$platforms = @('phone', 'wear', 'vr')

$records = New-Object System.Collections.Generic.List[object]
$rawLines = [IO.File]::ReadAllLines($termbasePath, [Text.Encoding]::UTF8)
for ($i = 0; $i -lt $rawLines.Count; $i++) {
    $text = $rawLines[$i]
    if ([string]::IsNullOrWhiteSpace($text)) { continue }
    try {
        $obj = $text | ConvertFrom-Json -AsHashtable
    }
    catch {
        Exit-CannotVerify ("{0}:{1} is not valid JSON ({2})" -f $termbaseRel, ($i + 1), $_.Exception.Message)
    }
    if ($obj -isnot [System.Collections.IDictionary]) {
        Exit-CannotVerify ("{0}:{1} is not a JSON object" -f $termbaseRel, ($i + 1))
    }
    $records.Add([pscustomobject]@{ Line = $i + 1; Data = $obj })
}

$schemaFindings = New-Object System.Collections.Generic.List[string]
function Add-SchemaFinding([int] $Line, [string] $Message) {
    $schemaFindings.Add(("{0}:{1}: {2}" -f $termbaseRel, $Line, $Message))
}

# ---------------------------------------------------------------- per-record shape

$byId = @{}
$byCanonical = @{}
foreach ($rec in $records) {
    $d = $rec.Data
    $ln = $rec.Line
    foreach ($key in $d.Keys) {
        if ($allowedFields -notcontains $key) { Add-SchemaFinding $ln "unknown field '$key'" }
    }
    foreach ($key in $requiredFields) {
        if (-not $d.Contains($key)) { Add-SchemaFinding $ln "missing required field '$key'" }
    }

    $id = $d['id']
    if (-not (Test-IsText $id) -or $id -cnotmatch '^[a-z0-9]+(-[a-z0-9]+)*$') {
        Add-SchemaFinding $ln "id must be a kebab-case string, got '$id'"
    }
    elseif ($byId.ContainsKey($id)) {
        Add-SchemaFinding $ln "duplicate id '$id' (first on line $($byId[$id].Line))"
    }
    else { $byId[$id] = $rec }

    $canonical = $d['canonical_en']
    if (-not (Test-IsText $canonical)) {
        if ($d.Contains('canonical_en')) { Add-SchemaFinding $ln 'canonical_en must be a non-empty string' }
    }
    elseif ($byCanonical.ContainsKey($canonical.ToLowerInvariant())) {
        Add-SchemaFinding $ln "duplicate canonical_en '$canonical' (first on line $($byCanonical[$canonical.ToLowerInvariant()].Line))"
    }
    else { $byCanonical[$canonical.ToLowerInvariant()] = $rec }

    if ($d.Contains('category') -and $categories -cnotcontains $d['category']) {
        Add-SchemaFinding $ln "category '$($d['category'])' is not one of: $($categories -join ', ')"
    }
    foreach ($key in 'definition_en', 'disambiguation_en') {
        if ($d.Contains($key) -and -not (Test-IsText $d[$key])) { Add-SchemaFinding $ln "$key must be a non-empty string" }
    }
    foreach ($key in 'forbidden_synonyms_en', 'ui_strings', 'related_terms') {
        if (-not $d.Contains($key)) { continue }
        $value = $d[$key]
        if (-not (Test-IsList $value)) { Add-SchemaFinding $ln "$key must be an array"; continue }
        if (-not (Test-IsStringList $value)) { Add-SchemaFinding $ln "$key must hold non-empty strings only" }
    }

    if ($d.Contains('locales')) {
        $loc = $d['locales']
        if ($loc -isnot [System.Collections.IDictionary]) {
            Add-SchemaFinding $ln 'locales must be an object'
        }
        else {
            foreach ($lang in 'ru', 'uk') {
                if (-not $loc.Contains($lang) -or -not (Test-IsText $loc[$lang])) {
                    Add-SchemaFinding $ln "locales.$lang must be a non-empty string"
                }
            }
            foreach ($lang in $loc.Keys) {
                if ($lang -cnotmatch '^[a-z]{2}(-[A-Za-z]+)?$' -or -not (Test-IsText $loc[$lang])) {
                    Add-SchemaFinding $ln "locales.$lang must be a language code mapped to a non-empty string"
                }
            }
        }
    }

    if ($d.Contains('flavors')) {
        $fl = $d['flavors']
        if (-not (Test-IsStringList $fl) -or $fl.Count -eq 0) {
            Add-SchemaFinding $ln 'flavors must be a non-empty array of strings'
        }
        elseif (-not ($fl.Count -eq 1 -and $fl[0] -ceq 'all')) {
            foreach ($f in $fl) {
                if ($knownFlavors -cnotcontains $f) {
                    Add-SchemaFinding $ln "flavor '$f' is not 'all' alone and not in docs/flavors/flavor-matrix.json"
                }
            }
        }
    }

    if ($d.Contains('platforms')) {
        $pl = $d['platforms']
        if (-not (Test-IsStringList $pl) -or $pl.Count -eq 0) {
            Add-SchemaFinding $ln 'platforms must be a non-empty array of strings'
        }
        else {
            foreach ($p in $pl) {
                if ($platforms -cnotcontains $p) { Add-SchemaFinding $ln "platform '$p' is not one of: $($platforms -join ', ')" }
            }
        }
    }

    if ($d.Contains('reference_visual') -and $null -ne $d['reference_visual']) {
        $rv = $d['reference_visual']
        if (-not (Test-IsText $rv)) {
            Add-SchemaFinding $ln 'reference_visual must be a non-empty string when present'
        }
        elseif ($rv -match '(?i)\.(png|jpe?g|webp|gif|svg)$' -and -not (Test-Path -LiteralPath (Resolve-RepoPath $rv) -PathType Leaf)) {
            Add-SchemaFinding $ln "reference_visual names a missing file: $rv"
        }
    }
}

# ---------------------------------------------------------------- forbidden-synonym matchers

$matchers = New-Object System.Collections.Generic.List[object]
foreach ($rec in $records) {
    $d = $rec.Data
    if (-not (Test-IsStringList $d['forbidden_synonyms_en'])) { continue }
    foreach ($entry in $d['forbidden_synonyms_en']) {
        $syn = $entry
        $replacement = [string]$d['canonical_en']
        $arrow = $entry.LastIndexOf(' => ')
        if ($arrow -ge 0) {
            $syn = $entry.Substring(0, $arrow).Trim()
            $replacement = $entry.Substring($arrow + 4).Trim()
            if (-not $syn -or -not $replacement) {
                Add-SchemaFinding $rec.Line "forbidden synonym '$entry' must read 'word => replacement' with both sides non-empty"
                continue
            }
        }
        try {
            $rx = New-SynonymRegex $syn
        }
        catch {
            Add-SchemaFinding $rec.Line "forbidden synonym '$syn' is not a valid regex ($($_.Exception.Message))"
            continue
        }
        $matchers.Add([pscustomobject]@{
                Id          = [string]$d['id']
                Replacement = $replacement
                Synonym     = $syn
                Regex       = $rx
                Line        = $rec.Line
            })
    }
}

foreach ($m in $matchers) {
    if (-not $m.Synonym.StartsWith('re:') -and $byCanonical.ContainsKey($m.Synonym.Trim().ToLowerInvariant())) {
        $owner = $byCanonical[$m.Synonym.Trim().ToLowerInvariant()].Data['id']
        Add-SchemaFinding $m.Line "forbidden synonym '$($m.Synonym)' is the canonical_en of record '$owner'"
    }
}

foreach ($rec in $records) {
    $d = $rec.Data
    foreach ($key in 'canonical_en', 'definition_en') {
        if (-not (Test-IsText $d[$key])) { continue }
        foreach ($m in $matchers) {
            if ($m.Regex.IsMatch($d[$key])) {
                Add-SchemaFinding $rec.Line "$key contains forbidden synonym '$($m.Synonym)' of '$($m.Id)'"
            }
        }
    }
}

# ---------------------------------------------------------------- cross-record references

foreach ($rec in $records) {
    $d = $rec.Data
    if (-not (Test-IsStringList $d['related_terms'])) { continue }
    foreach ($rt in $d['related_terms']) {
        if ($rt -ceq $d['id']) { Add-SchemaFinding $rec.Line "related_terms names the record itself ('$rt')" }
        elseif (-not $byId.ContainsKey($rt)) { Add-SchemaFinding $rec.Line "related_terms names unknown id '$rt'" }
    }
}

$needsStrings = @($records | Where-Object { (Test-IsStringList $_.Data['ui_strings']) -and $_.Data['ui_strings'].Count -gt 0 }).Count -gt 0
if ($needsStrings) {
    # English default values only: the screen-wins check compares canonical_en with what an en-US
    # reader sees, and a locale folder would answer with a translation.
    $stringFiles = New-Object System.Collections.Generic.List[string]
    foreach ($module in 'app_v2', 'wear') {
        $srcRoot = Join-Path $RepoRoot "$module/src"
        if (-not (Test-Path -LiteralPath $srcRoot -PathType Container)) { continue }
        foreach ($set in (Get-ChildItem -LiteralPath $srcRoot -Directory)) {
            $values = Join-Path $set.FullName 'res/values'
            if (-not (Test-Path -LiteralPath $values -PathType Container)) { continue }
            foreach ($f in (Get-ChildItem -LiteralPath $values -File -Filter 'strings*.xml')) { $stringFiles.Add($f.FullName) }
        }
    }
    if ($stringFiles.Count -eq 0) {
        Exit-CannotVerify 'records name ui_strings but no strings*.xml exists under app_v2/src/*/res/values or wear/src/*/res/values'
    }

    $stringValues = @{}
    $stringPattern = [regex]::new('<string\s+name="([^"]+)"[^>]*?(?:/>|>(.*?)</string>)', [Text.RegularExpressions.RegexOptions]::Singleline)
    foreach ($file in $stringFiles) {
        $xml = [IO.File]::ReadAllText($file, [Text.Encoding]::UTF8)
        foreach ($sm in $stringPattern.Matches($xml)) {
            $value = [Net.WebUtility]::HtmlDecode(($sm.Groups[2].Value -replace '<[^>]+>', '' -replace "\\(['""])", '$1'))
            if (-not $stringValues.ContainsKey($sm.Groups[1].Value)) {
                $stringValues[$sm.Groups[1].Value] = New-Object System.Collections.Generic.List[string]
            }
            $stringValues[$sm.Groups[1].Value].Add($value)
        }
    }

    foreach ($rec in $records) {
        $d = $rec.Data
        if (-not (Test-IsStringList $d['ui_strings']) -or $d['ui_strings'].Count -eq 0) { continue }
        $seen = New-Object System.Collections.Generic.List[string]
        foreach ($key in $d['ui_strings']) {
            if ($stringValues.ContainsKey($key)) { $seen.AddRange($stringValues[$key]) }
            else { Add-SchemaFinding $rec.Line "ui_strings key '$key' is not declared in any English strings*.xml" }
        }
        if ((Test-IsText $d['canonical_en']) -and $seen.Count -gt 0) {
            $onScreen = @($seen | Where-Object { $_.IndexOf($d['canonical_en'], [StringComparison]::OrdinalIgnoreCase) -ge 0 })
            if ($onScreen.Count -eq 0) {
                Add-SchemaFinding $rec.Line "canonical_en '$($d['canonical_en'])' appears in none of its ui_strings English values (the screen wins, S2974 ADR-1)"
            }
        }
    }
}

# ---------------------------------------------------------------- corpus pages

$corpusRoots = @($CorpusRoot |
        Where-Object { $_ } |
        ForEach-Object { $_ -split ',' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ } |
        ForEach-Object {
            $full = Resolve-RepoPath $_
            [pscustomobject]@{ Name = $_; Full = $full; Rel = (Get-Relative $full).TrimEnd('/') }
        })
$normChanged = @($ChangedFiles |
        Where-Object { $_ } |
        ForEach-Object { $_ -split ',' } |
        ForEach-Object { $_.Trim().Replace('\', '/') -replace '^\./', '' } |
        Where-Object { $_ })
$termbaseChanged = @($normChanged | Where-Object { $_ -ieq $termbaseRel }).Count -gt 0

$pages = New-Object System.Collections.Generic.List[string]
if ($normChanged.Count -gt 0 -and -not $termbaseChanged) {
    $scope = 'changed set'
    foreach ($rel in $normChanged) {
        if ($rel -notmatch '(?i)\.md$') { continue }
        $full = Resolve-RepoPath $rel
        $fullRel = Get-Relative $full
        $inCorpus = @($corpusRoots | Where-Object { $fullRel.StartsWith("$($_.Rel)/", [StringComparison]::OrdinalIgnoreCase) }).Count -gt 0
        if (-not $inCorpus) { continue }
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
            if (-not $Quiet) { Write-Host "  skipped (not found): $rel" -ForegroundColor DarkGray }
            continue
        }
        $pages.Add($full)
    }
}
else {
    $scope = if ($termbaseChanged) { 'whole corpus - termbase changed' } else { 'whole corpus' }
    foreach ($root in $corpusRoots) {
        if (Test-Path -LiteralPath $root.Full -PathType Container) {
            foreach ($f in (Get-ChildItem -LiteralPath $root.Full -Recurse -File -Filter '*.md')) {
                if (-not $pages.Contains($f.FullName)) { $pages.Add($f.FullName) }
            }
        }
        elseif (-not $Quiet) {
            Write-Host "  corpus root absent, no page judged there: $($root.Name)" -ForegroundColor DarkGray
        }
    }
}

$ignorePattern = [regex]::new('<!--\s*termbase-ignore:\s*(.*?)\s*-->', [Text.RegularExpressions.RegexOptions]::IgnoreCase)

# A page is judged whole, never line by line: a per-line PowerShell loop over a 250-page x 200-line
# corpus took 32.7 s, then 5.9 s with a per-line pre-filter, against the 5 s budget (S2974 section
# 3.2). Every non-prose construct is blanked by native regex passes that keep each line on its line
# - multi-line ones (fences, HTML comments) keep their newlines, one-line ones become a space - so a
# match offset still maps to its source line, and the line map is built only for a page that has a
# finding or an annotation.
$fenceBlockPattern = [regex]::new('(?ms)^[ \t]*(```|~~~)[^\n]*(?:\n.*?^[ \t]*\1[^\n]*$|.*\z)')
$commentPattern = [regex]::new('(?s)<!--.*?(?:-->|\z)')
$codeSpanPattern = [regex]::new('(`+)[^\n]+?\1')
$linkTargetPattern = [regex]::new('\]\([^)\n]*\)')
$refDefinitionPattern = [regex]::new('(?m)^[ \t]*\[[^\]\n]+\]:[ \t]*\S+[^\n]*$')
$urlPattern = [regex]::new('(?i)\b(?:https?|ftp)://\S+|\bwww\.\S+')
$tagPattern = [regex]::new('<[^>\n]+>')
$keepNewlines = [Text.RegularExpressions.MatchEvaluator] { param($m) $m.Value -replace '[^\n]', '' }
# A recipe page is a YAML front matter whose values are the prose. Keys are lowercase snake_case,
# so a capitalised "Note:" opening a prose line inside a block scalar survives.
$frontMatterPattern = [regex]::new('(?s)\A---[ \t]*\r?\n.*?\n---[ \t]*(?=\r?\n|\z)')
$machineKeyLinePattern = [regex]::new('(?m)^[ \t]*(?:-[ \t]+)?(?:page_id|category_slug|canonical_url|shot_id|screen_state|device_profile|url|badge_type|ticket|recipe_number|id|number)[ \t]*:[^\r\n]*')
$yamlKeyPattern = [regex]::new('(?m)^([ \t]*(?:-[ \t]+)?)[a-z_][a-z0-9_]*[ \t]*:(?=\s|\z)')
$pageTargetPattern = [regex]::new('(?<![\p{L}\p{N}_])(?:page|term):[\w.-]+')
$frontMatterEvaluator = [Text.RegularExpressions.MatchEvaluator] {
    param($m)
    $fm = $machineKeyLinePattern.Replace($m.Value, ' ')
    return $yamlKeyPattern.Replace($fm, '$1 ')
}

function Get-ProsePage([string] $Content) {
    $t = $frontMatterPattern.Replace($Content, $frontMatterEvaluator, 1)
    $t = $pageTargetPattern.Replace($t, ' ')
    $t = $fenceBlockPattern.Replace($t, $keepNewlines)
    $t = $commentPattern.Replace($t, $keepNewlines)
    $t = $codeSpanPattern.Replace($t, ' ')
    $t = $linkTargetPattern.Replace($t, '] ')
    $t = $refDefinitionPattern.Replace($t, ' ')
    $t = $urlPattern.Replace($t, ' ')
    return $tagPattern.Replace($t, ' ')
}

function Get-LineIndex([int[]] $Starts, [int] $Offset) {
    $found = [Array]::BinarySearch($Starts, $Offset)
    if ($found -ge 0) { return $found }
    return (-bnot $found) - 1
}

function Test-Suppressed($Matcher, $Tokens) {
    foreach ($tok in $Tokens) {
        if ($tok -ieq $Matcher.Synonym -or $tok -ceq $Matcher.Id) { return $true }
    }
    return $false
}

# One alternation of every synonym decides whether a page needs the per-matcher pass at all. The
# word-boundary guards are factored out of the literal branches: a guard repeated in each of 23
# branches cost 6.5 ms per 32 KB page, one shared guard 1.6 ms. A user regex whose numbered
# back-references would break inside the alternation leaves the pre-filter off.
$anySynonym = $null
if ($matchers.Count -gt 0) {
    try {
        $anyOptions = [Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [Text.RegularExpressions.RegexOptions]::CultureInvariant -bor [Text.RegularExpressions.RegexOptions]::Compiled
        $literals = @($matchers | Where-Object { -not $_.Synonym.StartsWith('re:') } | ForEach-Object { Get-SynonymBody $_.Synonym })
        $branches = @($matchers | Where-Object { $_.Synonym.StartsWith('re:') } | ForEach-Object { "(?:$($_.Synonym.Substring(3)))" })
        if ($literals.Count -gt 0) { $branches = @("$wordBefore(?:$($literals -join '|'))$wordAfter") + $branches }
        $anySynonym = [regex]::new(($branches -join '|'), $anyOptions)
    }
    catch {
        $anySynonym = $null
    }
}

$corpusFindings = New-Object System.Collections.Generic.List[string]
foreach ($page in $pages) {
    try {
        $content = [IO.File]::ReadAllText($page, [Text.Encoding]::UTF8)
    }
    catch {
        Exit-CannotVerify ("unreadable page: {0} ({1})" -f (Get-Relative $page), $_.Exception.Message)
    }
    if ($matchers.Count -eq 0 -or ($anySynonym -and -not $anySynonym.IsMatch($content))) { continue }
    $prose = Get-ProsePage $content
    if ($anySynonym -and -not $anySynonym.IsMatch($prose)) { continue }

    # Blanking keeps the line count but not the offsets, so a hit is placed with the prose text's own
    # line starts and an annotation with the raw text's.
    $rawStarts = [int[]](@(0) + @([regex]::Matches($content, '\n') | ForEach-Object { $_.Index + 1 }))
    $proseStarts = [int[]](@(0) + @([regex]::Matches($prose, '\n') | ForEach-Object { $_.Index + 1 }))
    $pageLines = $content -split '\n'
    # An annotation excuses its own line and the next one.
    $excused = @{}
    foreach ($im in $ignorePattern.Matches($content)) {
        $line = Get-LineIndex $rawStarts $im.Index
        $names = @($im.Groups[1].Value -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        foreach ($target in $line, ($line + 1)) {
            if (-not $excused.ContainsKey($target)) { $excused[$target] = New-Object System.Collections.Generic.List[string] }
            $excused[$target].AddRange([string[]]$names)
        }
    }

    $pageRel = Get-Relative $page
    foreach ($m in $matchers) {
        foreach ($hit in $m.Regex.Matches($prose)) {
            $line = Get-LineIndex $proseStarts $hit.Index
            if ($excused.ContainsKey($line) -and (Test-Suppressed $m $excused[$line])) { continue }
            $context = $pageLines[$line].Trim()
            if ($context.Length -gt 120) { $context = $context.Substring(0, 117) + '..' }
            $corpusFindings.Add(("{0}:{1}: '{2}' -> use '{3}' ({4}): {5}" -f $pageRel, ($line + 1), $hit.Value, $m.Replacement, $m.Id, $context))
        }
    }
}

# ---------------------------------------------------------------- verdict

$started.Stop()
$elapsed = '{0:N1} s' -f $started.Elapsed.TotalSeconds
if (-not $Quiet) {
    foreach ($f in $schemaFindings) { Write-Host "  FAIL $f" -ForegroundColor Red }
    foreach ($f in $corpusFindings) { Write-Host "  FAIL $f" -ForegroundColor Red }
}

if ($schemaFindings.Count -gt 0 -or $corpusFindings.Count -gt 0) {
    Write-Host ("{0}: FAIL - {1} schema finding(s) in {2}, {3} forbidden synonym(s) in {4} page(s) judged [{5}], {6}." -f `
            $gateName, $schemaFindings.Count, $termbaseRel, $corpusFindings.Count, $pages.Count, $scope, $elapsed) -ForegroundColor Red
    Write-Host '  Fix: use the canonical name on the page, or add/repair the record in the termbase (docs/COMMUNICATION_POLICY.md section 7).'
    exit 1
}

Write-Host ("{0}: PASS - {1} record(s), {2} forbidden synonym(s), {3} page(s) judged [{4}], {5}." -f `
        $gateName, $records.Count, $matchers.Count, $pages.Count, $scope, $elapsed) -ForegroundColor Green
exit 0
