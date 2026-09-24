#requires -Version 7.0
<#
.SYNOPSIS
    S3432: rungs 2, 3 and 5 of the icon contract's conformance ladder (ICON-SET, catalog folder
    iconography/, README section 6) - every glyph maps to a meaning, labels agree with glyphs, and
    the docs show the meaning's glyph.

.DESCRIPTION
    Rung 1 is the exporter (scripts/docs/export-icon-contract.ps1); rung 4's mandatory half, a baked
    fill with no tint, is rule `tint` of assert-icon-style.ps1. This gate holds the other three,
    reading the vocabulary from the catalog and this product's declaration from
    docs/icons/icon-contract-map.json.

    Dimensions, each finding one stable key (no line numbers, so an edit above a site moves nothing):
      unmapped           unmapped|<module>|<drawable>
                         an ic*/ico* drawable referenced by module source that is neither a vocabulary
                         ref/state/level/variant, nor a declared 'glyphs' mapping, nor 'private'.
      label-glyph        label-glyph|<module>|<file>|<drawable>|<string key>
                         a layout/menu element, or a Kotlin line with exactly one drawable and one
                         string, pairs a label whose meaning is known with a drawable of a different
                         meaning that no sharedWith declares.
      name-substitution  name-substitution|<module>|<string key>|<locale>
                         a string whose meaning is known reads, in ru or uk, exactly another
                         meaning's name in that language (ICON-SET rule 3: "Назад" is Back, never
                         Previous).
      doc-glyph          doc-glyph|doc-icon-map|<drawable>|<why> and doc-glyph|termbase|<term>|<drawable>|<why>
                         a docs picture that is private or unmapped, or a termbase term declared to a
                         meaning whose reference_visual draws another one.
      accessible-name    accessible-name|<module>|<file>|<drawable>|<string key>
                         S3443, ICON-RENDER rule 8: a control whose only label is its glyph - a
                         layout/menu control (a *Button, a menu item, a navigation glyph or a
                         clickable view) with one glyph attribute, no visible text, and a
                         contentDescription (navigationContentDescription for a navigation glyph, the
                         title of a menu item), or a Kotlin line setting contentDescription within three
                         lines of exactly one drawable - carries, in every locale of en/ru/uk that has
                         both strings, the name of its glyph's meaning (or of a sharedWith) as whole
                         words. "Contains", not "equals": the rule lets a name add its object.
                         The declaration's 'forms' section ({ "<meaning>": { "<locale>": [..] } })
                         adds accepted word forms of a name - an inflection agreeing with the object
                         ("Следующая страница") or a spelling variant, never a synonym.
      compose-unmapped, compose-label-glyph, compose-accessible-name
                         S3482: the same three rules for a Compose Material vector (`Icons.Filled.Star`),
                         which draws a glyph with no drawable file. A reference is named
                         `Icons.<Style>.<Name>` with `Default` read as `Filled` and `AutoMirrored`
                         dropped, and is mapped through the same 'glyphs' declaration
                         (`"wear:Icons.Filled.Star": "action.favorite"`). A Kotlin line or window is
                         judged for a vector only when it carries no drawable, so no drawable key moves.

    A label's meaning: the 'labels' declaration, else every meaning whose name.en equals the
    string's EN value exactly (trailing '..' ignored). Exact names only - a qualified label
    ("Previous page") is declared or not judged, which keeps the rule free of guesses.

    Baseline scripts/quality/icon-contract-baseline.txt, one key per line (CHECK-BASELINE: may fall,
    never rise). A declared 'exceptions' entry {key, why} excuses a key for good.

    Scoping (fixed-input shape, S2824): with -ChangedFiles, a new or stale key is charged to this
    closure only when the set carries a file that key depends on - the site's own file, a file
    referencing the drawable, the drawable itself, a strings file of the module, doc-icon-map.json,
    termbase.jsonl - or when the set carries the declaration, the baseline or this gate, which widen
    the charge to every key. Keys charged elsewhere make it advisory (exit 3).

    The catalog root: -CatalogRoot, then FMS_CONTRACTS_ROOT in the process, then the same variable
    at user scope (a session started before the variable was set still finds it).

    Modes:
      -Gate            judge against the baseline (post-change calling convention).
      -UpdateBaseline  write the current keys - seeds a missing file, lowers an existing one, and
                       refuses a raise. With -SeedDimension <name[,name]> it also adds the keys of
                       those dimensions, each only while the baseline carries none of it (a new
                       dimension's day one); any other new key is still refused.
      -List            print every finding with its site.

.NOTES
    Exit codes:
      0 - every key is baselined or excused and no charged baseline line is stale; a -List run; a
          completed baseline write.
      1 - a charged key is new, a charged baseline line is stale, or -UpdateBaseline was asked to
          RAISE the baseline.
      2 - could not verify: no catalog root, the vocabulary or the declaration is missing or invalid.
      3 - advisory: new or stale keys exist, but none depends on a file in -ChangedFiles.
      4 - -UpdateBaseline found Code.Scripts held by another session; nothing was written.
#>
[CmdletBinding()]
param(
    [switch] $Gate,
    [switch] $UpdateBaseline,
    [switch] $List,
    [switch] $Quiet,
    [string] $ChangedFiles = '',
    [string] $CatalogRoot = '',
    [string] $SeedDimension = '',
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $BaselineFile = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')

$mapRel = 'docs/icons/icon-contract-map.json'
$baselineRel = 'scripts/quality/icon-contract-baseline.txt'
$docMapRel = 'docs/icons/doc-icon-map.json'
$termbaseRel = 'docs/termbase.jsonl'
$gateRel = 'scripts/quality/assert-icon-contract.ps1'
if (-not $BaselineFile) { $BaselineFile = Join-Path $RepoRoot $baselineRel }
$androidNs = 'http://schemas.android.com/apk/res/android'
$autoNs = 'http://schemas.android.com/apk/res-auto'
$labelAttrs = @('contentDescription', 'title', 'tooltipText', 'text', 'label', 'shortcutShortLabel')
$modules = @('app_v2', 'wear')
# Drawable attributes that paint a surface rather than show a control's glyph.
$decorAttrs = @('background', 'foreground', 'logo', 'overflowIcon', 'collapseIcon', 'thumb', 'track', 'progressDrawable', 'divider', 'popupBackground', 'checkMark', 'windowBackground')
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Stop-Verdict([int] $Code, [string] $Word, [string[]] $Lines = @()) {
    foreach ($l in $Lines) { Write-Host "  $l" }
    Write-Host "assert-icon-contract: $Word"
    exit $Code
}

function ConvertTo-RelPath([string] $Path) {
    $p = $Path -replace '\\', '/'
    $root = ($RepoRoot -replace '\\', '/').TrimEnd('/') + '/'
    if ($p.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { $p = $p.Substring($root.Length) }
    return ($p -replace '^\./', '')
}

function Get-NormalName([string] $Text) {
    if ($null -eq $Text) { return '' }
    $t = $Text.Trim() -replace '\\n', ' ' -replace '\s+', ' '
    $t = $t -replace '(\.\.\.?|…)$', ''
    return $t.Trim().ToLowerInvariant()
}

# Case-sensitive on purpose: an import line spells the package `icons.filled.Star`, never `Icons.Filled.Star`.
$composeGlyphRegex = [regex]::new('\bIcons\.(?:AutoMirrored\.)?(Default|Filled|Outlined|Rounded|Sharp|TwoTone)\.([A-Z][A-Za-z0-9_]*)\b')

function Get-ComposeGlyphs([string] $Text) {
    $out = [System.Collections.Generic.List[string]]::new()
    foreach ($cm in $composeGlyphRegex.Matches($Text)) {
        $style = $cm.Groups[1].Value
        if ($style -eq 'Default') { $style = 'Filled' }
        $out.Add("Icons.$style.$($cm.Groups[2].Value)")
    }
    return $out
}

Write-CheckSubject -Axes ([ordered]@{ module = 'app_v2,wear'; scope = $(if ($ChangedFiles) { 'changed-set' } else { 'tree' }) })

# ---- catalog root -------------------------------------------------------------------------
if (-not $CatalogRoot) { $CatalogRoot = $env:FMS_CONTRACTS_ROOT }
if (-not $CatalogRoot) { $CatalogRoot = [Environment]::GetEnvironmentVariable('FMS_CONTRACTS_ROOT', 'User') }
if (-not $CatalogRoot) {
    Stop-Verdict 2 'COULD NOT VERIFY' @('no catalog root: pass -CatalogRoot or set FMS_CONTRACTS_ROOT (CLAUDE.md names the location)')
}
$vocabPath = Join-Path $CatalogRoot 'iconography/vocabulary.jsonl'
if (-not (Test-Path -LiteralPath $vocabPath)) {
    Stop-Verdict 2 'COULD NOT VERIFY' @("no vocabulary at $vocabPath")
}
$mapPath = Join-Path $RepoRoot $mapRel
if (-not (Test-Path -LiteralPath $mapPath)) { Stop-Verdict 2 'COULD NOT VERIFY' @("no declaration at $mapRel") }

try {
    $vocab = @(Get-Content -LiteralPath $vocabPath -Encoding utf8 | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
    $decl = Get-Content -LiteralPath $mapPath -Raw -Encoding utf8 | ConvertFrom-Json
}
catch {
    Stop-Verdict 2 'COULD NOT VERIFY' @("unreadable input: $($_.Exception.Message)")
}

function Get-DeclSection([string] $Name) {
    if ($decl.PSObject.Properties[$Name]) { return $decl.$Name }
    return $null
}

# ---- meaning index ------------------------------------------------------------------------
$meanings = @{}
$shared = @{}
$nameIndex = @{ en = @{}; ru = @{}; uk = @{} }
# drawable index keyed "<module>|<name>" -> HashSet of meaning ids
$glyphIndex = @{}

function Add-Glyph([string] $Module, [string] $Name, [string] $Id) {
    $k = "$Module|$Name"
    if (-not $glyphIndex.ContainsKey($k)) { $glyphIndex[$k] = [System.Collections.Generic.HashSet[string]]::new() }
    [void]$glyphIndex[$k].Add($Id)
}

function Add-VocabRef([string] $Raw, [string] $Id, [string] $DefaultScope) {
    if (-not $Raw) { return }
    $scope = $DefaultScope
    $name = $Raw
    if ($Raw -match '^([A-Za-z]+):(.+)$') {
        $name = $Matches[2]
        if ($Matches[1] -eq 'wear') { $scope = 'wear' } else { $scope = 'app_v2' }
    }
    if ($scope -eq 'both' -or $scope -eq 'app_v2') { Add-Glyph 'app_v2' $name $Id }
    if ($scope -eq 'both' -or $scope -eq 'wear') { Add-Glyph 'wear' $name $Id }
}

foreach ($r in $vocab) {
    $meanings[$r.id] = $r
    $shared[$r.id] = @()
    if ($r.PSObject.Properties['sharedWith']) { $shared[$r.id] = @($r.sharedWith) }
    foreach ($loc in 'en', 'ru', 'uk') {
        if ($r.name.PSObject.Properties[$loc]) {
            $n = Get-NormalName $r.name.$loc
            if (-not $nameIndex[$loc].ContainsKey($n)) { $nameIndex[$loc][$n] = [System.Collections.Generic.HashSet[string]]::new() }
            [void]$nameIndex[$loc][$n].Add($r.id)
        }
    }
    if ($r.PSObject.Properties['ref']) {
        # The android ref names a drawable both modules carry under one name; a wear ref is the watch's own.
        if ($r.ref.PSObject.Properties['android']) { Add-VocabRef $r.ref.android $r.id 'both' }
        if ($r.ref.PSObject.Properties['wear']) { Add-VocabRef $r.ref.wear $r.id 'wear' }
    }
    foreach ($k in 'states', 'variants') {
        if ($r.PSObject.Properties[$k]) { foreach ($p in $r.$k.PSObject.Properties) { Add-VocabRef ([string]$p.Value) $r.id 'both' } }
    }
    if ($r.PSObject.Properties['levels']) { foreach ($l in $r.levels) { Add-VocabRef ([string]$l) $r.id 'both' } }
}

$declErrors = [System.Collections.Generic.List[string]]::new()
$glyphDecl = Get-DeclSection 'glyphs'
if ($glyphDecl) {
    foreach ($p in $glyphDecl.PSObject.Properties) {
        if (-not $meanings.ContainsKey([string]$p.Value)) { $declErrors.Add("glyphs.$($p.Name): unknown meaning '$($p.Value)'"); continue }
        $scope = 'both'; $name = $p.Name
        if ($p.Name -match '^(wear|app_v2):(.+)$') { $scope = $Matches[1]; $name = $Matches[2] }
        if ($scope -ne 'wear') { Add-Glyph 'app_v2' $name ([string]$p.Value) }
        if ($scope -ne 'app_v2') { Add-Glyph 'wear' $name ([string]$p.Value) }
    }
}
$privatePatterns = @()
$privDecl = Get-DeclSection 'private'
if ($privDecl) { $privatePatterns = @($privDecl | ForEach-Object { [string]$_.pattern }) }
$labelDecl = @{}
$ld = Get-DeclSection 'labels'
if ($ld) {
    foreach ($p in $ld.PSObject.Properties) {
        if (-not $meanings.ContainsKey([string]$p.Value)) { $declErrors.Add("labels.$($p.Name): unknown meaning '$($p.Value)'"); continue }
        $labelDecl[$p.Name] = [string]$p.Value
    }
}
$termDecl = @{}
$td = Get-DeclSection 'terms'
if ($td) {
    foreach ($p in $td.PSObject.Properties) {
        if (-not $meanings.ContainsKey([string]$p.Value)) { $declErrors.Add("terms.$($p.Name): unknown meaning '$($p.Value)'"); continue }
        $termDecl[$p.Name] = [string]$p.Value
    }
}
# S3484: "Следующая страница" agrees with its object, so the whole-word match needs the inflected
# form declared. Grammatical forms and spelling variants only - a synonym would defeat ICON-SET rule 3.
$formDecl = @{}
$fd = Get-DeclSection 'forms'
if ($fd) {
    foreach ($p in $fd.PSObject.Properties) {
        if (-not $meanings.ContainsKey($p.Name)) { $declErrors.Add("forms.$($p.Name): unknown meaning"); continue }
        foreach ($lp in $p.Value.PSObject.Properties) {
            if (@('en', 'ru', 'uk') -notcontains $lp.Name) { $declErrors.Add("forms.$($p.Name).$($lp.Name): unknown locale"); continue }
            $formDecl["$($p.Name)|$($lp.Name)"] = @($lp.Value | ForEach-Object { Get-NormalName ([string]$_) } | Where-Object { $_ })
        }
    }
}
$excused = @{}
$ed = Get-DeclSection 'exceptions'
if ($ed) { foreach ($e in $ed) { $excused[[string]$e.key] = [string]$e.why } }
if ($declErrors.Count -gt 0) { Stop-Verdict 2 'COULD NOT VERIFY' (@("invalid $mapRel") + $declErrors) }

function Test-Private([string] $Name) {
    foreach ($pat in $privatePatterns) { if ($Name -like $pat) { return $true } }
    return $false
}

function Get-GlyphMeanings([string] $Module, [string] $Name) {
    $k = "$Module|$Name"
    if ($glyphIndex.ContainsKey($k)) { return @($glyphIndex[$k]) }
    return @()
}

function Get-ExpandedMeanings([string[]] $Ids) {
    $set = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($id in $Ids) {
        [void]$set.Add($id)
        if ($shared.ContainsKey($id)) { foreach ($s in $shared[$id]) { [void]$set.Add([string]$s) } }
    }
    return $set
}

function Test-ContainsName([string] $Text, [string] $Name) {
    # "Picture-in-picture" and "picture in picture" are one name.
    $Text = $Text -replace '[-‐‑]', ' '
    $Name = $Name -replace '[-‐‑]', ' '
    return [regex]::IsMatch($Text,'(?<![\p{L}\p{N}])' + [regex]::Escape($Name) + '(?![\p{L}\p{N}])')
}

# Reads $strings, $stringFiles and $m of the module loop below.
function Test-AccessibleName([string] $Rel, [string] $Drawable, [string] $Key, [string] $Where, [string] $Dim = 'accessible-name') {
    $gm = @(Get-GlyphMeanings $m $Drawable)
    if ($gm.Count -eq 0) { return }
    $ex = @(Get-ExpandedMeanings $gm)
    $miss = [System.Collections.Generic.List[string]]::new()
    foreach ($loc in 'en', 'ru', 'uk') {
        if (-not $strings[$loc].ContainsKey($Key)) { continue }
        $desc = Get-NormalName $strings[$loc][$Key]
        $names = @($ex | Where-Object { $meanings[$_].name.PSObject.Properties[$loc] } | ForEach-Object { Get-NormalName $meanings[$_].name.$loc } | Where-Object { $_ })
        $names += @($ex | ForEach-Object { $formDecl["$_|$loc"] } | Where-Object { $_ })
        if ($names.Count -eq 0) { continue }
        if (@($names | Where-Object { Test-ContainsName $desc $_ }).Count -gt 0) { continue }
        $miss.Add("$loc '$($strings[$loc][$Key])' lacks '$($names -join "' / '")'")
    }
    if ($miss.Count -eq 0) { return }
    Add-Finding "$Dim|$m|$Rel|$Drawable|$Key" (@($Rel) + $stringFiles.ToArray()) "$Where $Drawable ($($gm -join ',')) named by ${Key}: $($miss -join '; ')"
}

# ---- findings -----------------------------------------------------------------------------
# key -> list of repo-relative files the key depends on
$findings = [ordered]@{}
$sites = @{}
function Add-Finding([string] $Key, [string[]] $DependsOn, [string] $Site) {
    if (-not $findings.Contains($Key)) {
        $findings[$Key] = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        $sites[$Key] = $Site
    }
    foreach ($d in $DependsOn) { if ($d) { [void]$findings[$Key].Add($d) } }
}

foreach ($m in $modules) {
    $srcRoot = Join-Path $RepoRoot "$m/src"
    if (-not (Test-Path -LiteralPath $srcRoot)) { continue }

    # strings of the module's main source set, per locale
    $strings = @{ en = @{}; ru = @{}; uk = @{} }
    $stringFiles = [System.Collections.Generic.List[string]]::new()
    foreach ($pair in @(@('en', 'values'), @('ru', 'values-ru'), @('uk', 'values-uk'))) {
        $dir = Join-Path $srcRoot "main/res/$($pair[1])"
        if (-not (Test-Path -LiteralPath $dir)) { continue }
        foreach ($f in Get-ChildItem -LiteralPath $dir -Filter 'strings*.xml' -File) {
            $stringFiles.Add((ConvertTo-RelPath $f.FullName))
            $doc = New-Object System.Xml.XmlDocument
            $doc.LoadXml([System.IO.File]::ReadAllText($f.FullName).TrimStart([char]0xFEFF))
            foreach ($s in $doc.DocumentElement.SelectNodes('string')) { $strings[$pair[0]][$s.GetAttribute('name')] = $s.InnerText }
        }
    }

    $labelMeaningCache = @{}
    function Get-LabelMeanings([string] $Key) {
        if ($labelMeaningCache.ContainsKey($Key)) { return $labelMeaningCache[$Key] }
        $ids = @()
        if ($labelDecl.ContainsKey($Key)) { $ids = @($labelDecl[$Key]) }
        elseif ($strings.en.ContainsKey($Key)) {
            $n = Get-NormalName $strings.en[$Key]
            if ($n -and $nameIndex.en.ContainsKey($n)) { $ids = @($nameIndex.en[$n]) }
        }
        $labelMeaningCache[$Key] = $ids
        return $ids
    }

    # name-substitution
    foreach ($key in @($strings.en.Keys)) {
        $ids = @(Get-LabelMeanings $key)
        if ($ids.Count -eq 0) { continue }
        foreach ($loc in 'ru', 'uk') {
            if (-not $strings[$loc].ContainsKey($key)) { continue }
            $n = Get-NormalName $strings[$loc][$key]
            if (-not $n -or -not $nameIndex[$loc].ContainsKey($n)) { continue }
            $own = @($ids | Where-Object { $meanings[$_].name.PSObject.Properties[$loc] -and (Get-NormalName $meanings[$_].name.$loc) -eq $n })
            if ($own.Count -gt 0) { continue }
            $other = (@($nameIndex[$loc][$n]) | Sort-Object) -join ','
            Add-Finding "name-substitution|$m|$key|$loc" ($stringFiles.ToArray()) "$key ($loc) reads '$($strings[$loc][$key])' = name of $other; meaning $($ids -join ',')"
        }
    }

    # reference index and label-glyph sites
    $refFiles = @{}
    $composeRefFiles = @{}
    $files = Get-ChildItem -LiteralPath $srcRoot -Recurse -File -Include *.kt, *.xml, *.java |
        Where-Object { ($_.FullName -replace '\\', '/') -notmatch '/(build|test|androidTest)/' }
    foreach ($f in $files) {
        $text = [System.IO.File]::ReadAllText($f.FullName)
        $hasDrawable = $text -match '(@drawable/|R\.drawable\.)ic'
        $hasCompose = ($f.Extension -eq '.kt') -and $composeGlyphRegex.IsMatch($text)
        if (-not $hasDrawable -and -not $hasCompose) { continue }
        $rel = ConvertTo-RelPath $f.FullName
        if ($hasCompose) {
            foreach ($cg in (Get-ComposeGlyphs $text)) {
                if (-not $composeRefFiles.ContainsKey($cg)) { $composeRefFiles[$cg] = [System.Collections.Generic.HashSet[string]]::new() }
                [void]$composeRefFiles[$cg].Add($rel)
            }
        }
        foreach ($mm in [regex]::Matches($text, '(?:@drawable/|R\.drawable\.)(ic[a-z0-9_]*)')) {
            $d = $mm.Groups[1].Value
            if (-not $refFiles.ContainsKey($d)) { $refFiles[$d] = [System.Collections.Generic.HashSet[string]]::new() }
            [void]$refFiles[$d].Add($rel)
        }
        if ($f.Extension -eq '.xml' -and $rel -notmatch '/res/(drawable|mipmap)') {
            $doc = New-Object System.Xml.XmlDocument
            try { $doc.LoadXml($text.TrimStart([char]0xFEFF)) } catch { Write-Verbose "skip unparsable $rel"; continue }
            foreach ($el in $doc.SelectNodes('//*')) {
                $glyphAttrs = @(@($el.Attributes) | Where-Object { $_.Value -match '^@drawable/(ic[a-z0-9_]*)$' -and $decorAttrs -notcontains $_.LocalName })
                if ($glyphAttrs.Count -ne 1) { continue }
                $drawables = @($glyphAttrs[0].Value.Substring(10))
                $visible = $el.GetAttribute('text', $androidNs)
                if (-not $visible) { $visible = $el.GetAttribute('text', $autoNs) }
                # A settings row renders its app:str_title as visible text beside the glyph.
                if (-not $visible) { $visible = $el.GetAttribute('str_title', $autoNs) }
                $isControl = ($el.LocalName -match 'Button$|^item$') -or $glyphAttrs[0].LocalName -eq 'navigationIcon' -or $el.GetAttribute('clickable', $androidNs) -eq 'true'
                if (-not $visible -and $isControl) {
                    $nameAttr = if ($glyphAttrs[0].LocalName -eq 'navigationIcon') { 'navigationContentDescription' } else { 'contentDescription' }
                    $a11y = $el.GetAttribute($nameAttr, $androidNs)
                    if (-not $a11y) { $a11y = $el.GetAttribute($nameAttr, $autoNs) }
                    if (-not $a11y -and $el.LocalName -eq 'item') { $a11y = $el.GetAttribute('title', $androidNs) }
                    if ($a11y -match '^@string/([A-Za-z0-9_]+)$') { Test-AccessibleName $rel $drawables[0] $Matches[1] "$rel <$($el.LocalName)>" }
                }
                # A toolbar's title names the screen, not its navigation glyph; only the glyph's own description does.
                $pairLabels = $labelAttrs
                if ($glyphAttrs[0].LocalName -eq 'navigationIcon') { $pairLabels = @('navigationContentDescription') }
                $labelKey = $null
                foreach ($attr in $pairLabels) {
                    $val = $el.GetAttribute($attr, $androidNs)
                    if (-not $val) { $val = $el.GetAttribute($attr, $autoNs) }
                    if ($val) { if ($val -match '^@string/([A-Za-z0-9_]+)$') { $labelKey = $Matches[1] }; break }
                }
                if (-not $labelKey) { continue }
                $d = $drawables[0]
                $lm = @(Get-LabelMeanings $labelKey)
                $gm = @(Get-GlyphMeanings $m $d)
                if ($lm.Count -eq 0 -or $gm.Count -eq 0) { continue }
                $ex = Get-ExpandedMeanings $gm
                if (@($lm | Where-Object { $ex.Contains($_) }).Count -gt 0) { continue }
                Add-Finding "label-glyph|$m|$rel|$d|$labelKey" @($rel) "$rel <$($el.LocalName)> $d ($($gm -join ',')) labelled $labelKey ($($lm -join ','))"
            }
        }
        elseif ($f.Extension -eq '.kt') {
            $ln = 0
            $ktLines = $text -split "`n"
            foreach ($line in $ktLines) {
                $ln++
                if ($line -match 'contentDescription') {
                    $cm = [regex]::Matches($line, 'R\.string\.([A-Za-z0-9_]+)')
                    if ($cm.Count -eq 1) {
                        $window = ($ktLines[[Math]::Max(0, $ln - 4)..($ln - 1)]) -join "`n"
                        $wd = @([regex]::Matches($window, 'R\.drawable\.(ic[a-z0-9_]*)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
                        if ($wd.Count -eq 1) { Test-AccessibleName $rel $wd[0] $cm[0].Groups[1].Value "${rel}:$ln" }
                        elseif ($wd.Count -eq 0 -and $hasCompose) {
                            $wc = @(Get-ComposeGlyphs $window | Sort-Object -Unique)
                            if ($wc.Count -eq 1) { Test-AccessibleName $rel $wc[0] $cm[0].Groups[1].Value "${rel}:$ln" 'compose-accessible-name' }
                        }
                    }
                }
                $dm =[regex]::Matches($line, 'R\.drawable\.(ic[a-z0-9_]*)')
                $sm = [regex]::Matches($line, 'R\.string\.([A-Za-z0-9_]+)')
                if ($sm.Count -ne 1) { continue }
                $dim = 'label-glyph'
                if ($dm.Count -eq 1) { $d = $dm[0].Groups[1].Value }
                elseif ($dm.Count -eq 0 -and $hasCompose) {
                    $lc = @(Get-ComposeGlyphs $line)
                    if ($lc.Count -ne 1) { continue }
                    $d = $lc[0]
                    $dim = 'compose-label-glyph'
                }
                else { continue }
                $labelKey = $sm[0].Groups[1].Value
                $lm = @(Get-LabelMeanings $labelKey)
                $gm = @(Get-GlyphMeanings $m $d)
                if ($lm.Count -eq 0 -or $gm.Count -eq 0) { continue }
                $ex = Get-ExpandedMeanings $gm
                if (@($lm | Where-Object { $ex.Contains($_) }).Count -gt 0) { continue }
                Add-Finding "$dim|$m|$rel|$d|$labelKey" @($rel) "${rel}:$ln $d ($($gm -join ',')) labelled $labelKey ($($lm -join ','))"
            }
        }
    }

    # unmapped: every referenced ic*/ico* drawable the module actually carries
    $carried = @{}
    foreach ($ss in Get-ChildItem -LiteralPath $srcRoot -Directory) {
        $res = Join-Path $ss.FullName 'res'
        if (-not (Test-Path -LiteralPath $res)) { continue }
        foreach ($dir in Get-ChildItem -LiteralPath $res -Directory | Where-Object { $_.Name -match '^drawable' }) {
            foreach ($f in Get-ChildItem -LiteralPath $dir.FullName -File) {
                $n = [IO.Path]::GetFileNameWithoutExtension($f.Name)
                if ($n -notlike 'ic*') { continue }
                if (-not $carried.ContainsKey($n)) { $carried[$n] = [System.Collections.Generic.List[string]]::new() }
                $carried[$n].Add((ConvertTo-RelPath $f.FullName))
            }
        }
    }
    foreach ($n in ($carried.Keys | Sort-Object)) {
        if (-not $refFiles.ContainsKey($n)) { continue }
        if (@(Get-GlyphMeanings $m $n).Count -gt 0) { continue }
        if (Test-Private $n) { continue }
        $deps = @($carried[$n]) + @($refFiles[$n])
        Add-Finding "unmapped|$m|$n" $deps "$m $n referenced by $(@($refFiles[$n] | Sort-Object | Select-Object -First 3) -join ', ')"
    }
    foreach ($n in ($composeRefFiles.Keys | Sort-Object)) {
        if (@(Get-GlyphMeanings $m $n).Count -gt 0) { continue }
        if (Test-Private $n) { continue }
        Add-Finding "compose-unmapped|$m|$n" @($composeRefFiles[$n]) "$m $n referenced by $(@($composeRefFiles[$n] | Sort-Object | Select-Object -First 3) -join ', ')"
    }
}

# ---- rung 5: doc pictures -----------------------------------------------------------------
function Test-DocDrawable([string] $Source, [string] $Drawable, [string] $DependsOn, [string] $Context) {
    if (Test-Private $Drawable) {
        Add-Finding "doc-glyph|$Source|$Drawable|private" @($DependsOn) "$Context shows private artwork $Drawable"
        return @()
    }
    $gm = @(Get-GlyphMeanings 'app_v2' $Drawable)
    if ($gm.Count -eq 0) { Add-Finding "doc-glyph|$Source|$Drawable|unmapped" @($DependsOn) "$Context shows $Drawable, which maps to no meaning" }
    return $gm
}

$docMapPath = Join-Path $RepoRoot $docMapRel
if (Test-Path -LiteralPath $docMapPath) {
    $dm = Get-Content -LiteralPath $docMapPath -Raw -Encoding utf8 | ConvertFrom-Json
    foreach ($p in $dm.PSObject.Properties) {
        if ($p.Name -like '_*') { continue }
        $entries = @()
        if ($p.Value -is [System.Array]) { $entries = @($p.Value | ForEach-Object { [pscustomobject]@{ ctx = "$($p.Name):$(if ($_.PSObject.Properties['title']) { $_.title } else { $_.emoji })"; title = $(if ($_.PSObject.Properties['title']) { $_.title } else { '' }); drawable = [string]$_.drawable } }) }
        else { $entries = @($p.Value.PSObject.Properties | ForEach-Object { [pscustomobject]@{ ctx = "$($p.Name):$($_.Name)"; title = ''; drawable = [string]$_.Value } }) }
        foreach ($e in $entries) {
            if (-not $e.drawable) { continue }
            $gm = @(Test-DocDrawable 'doc-icon-map' $e.drawable $docMapRel $e.ctx)
            $tn = Get-NormalName $e.title
            if ($gm.Count -eq 0 -or -not $tn -or -not $nameIndex.en.ContainsKey($tn)) { continue }
            $tm = @($nameIndex.en[$tn])
            $ex = Get-ExpandedMeanings $gm
            if (@($tm | Where-Object { $ex.Contains($_) }).Count -eq 0) {
                Add-Finding "doc-glyph|doc-icon-map|$($e.drawable)|title:$($e.title)" @($docMapRel) "$($e.ctx) names $($tm -join ',') but shows $($e.drawable) ($($gm -join ','))"
            }
        }
    }
}

$termbasePath = Join-Path $RepoRoot $termbaseRel
if (Test-Path -LiteralPath $termbasePath) {
    foreach ($line in Get-Content -LiteralPath $termbasePath -Encoding utf8) {
        if (-not $line.Trim()) { continue }
        $t = $line | ConvertFrom-Json
        if (-not $t.PSObject.Properties['reference_visual'] -or -not $t.reference_visual) { continue }
        $d = [IO.Path]::GetFileNameWithoutExtension([string]$t.reference_visual)
        $gm = @(Test-DocDrawable 'termbase' $d $termbaseRel "termbase $($t.id)")
        if ($gm.Count -eq 0 -or -not $termDecl.ContainsKey($t.id)) { continue }
        $want = $termDecl[$t.id]
        if (-not (Get-ExpandedMeanings $gm).Contains($want)) {
            Add-Finding "doc-glyph|termbase|$($t.id)|$d|$want" @($termbaseRel, $mapRel) "termbase $($t.id) is $want but shows $d ($($gm -join ','))"
        }
    }
}

# ---- verdict ------------------------------------------------------------------------------
$current = @($findings.Keys | Where-Object { -not $excused.ContainsKey($_) } | Sort-Object -Unique)

if ($List) {
    foreach ($k in $current) { Write-Host "$k`n    $($sites[$k])" }
    Stop-Verdict 0 "LIST ($($current.Count) finding(s))"
}

$baseline = @()
if (Test-Path -LiteralPath $BaselineFile) {
    $baseline = @(Get-Content -LiteralPath $BaselineFile -Encoding utf8 | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') })
}
$baseSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$baseline)
$curSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$current)
$new = @($current | Where-Object { -not $baseSet.Contains($_) })
$stale = @($baseline | Where-Object { -not $curSet.Contains($_) })

if ($UpdateBaseline) {
    if ((Test-Path -LiteralPath $BaselineFile) -and $new.Count -gt 0) {
        $refused = $new
        $carriedDims = @($baseline | ForEach-Object { ($_ -split '\|')[0] } | Sort-Object -Unique)
        # A comma list, because one change can open several dimensions and each would refuse the other's keys.
        $seedDims = @($SeedDimension -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $carriedDims -notcontains $_ })
        if ($seedDims.Count -gt 0) {
            $refused = @($new | Where-Object { $seedDims -notcontains ($_ -split '\|')[0] })
        }
        if ($refused.Count -gt 0) { Stop-Verdict 1 'REFUSED - the baseline may fall, never rise' (@('new keys:') + $refused) }
    }
    . (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')
    $scope = $null
    # A baseline outside this checkout (a test fixture) belongs to no code domain; the lock table
    # would answer "every domain" for it and queue a write nobody else can touch.
    $ownRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $inTree = [IO.Path]::GetFullPath($BaselineFile).StartsWith($ownRoot, [StringComparison]::OrdinalIgnoreCase)
    try {
        if ($inTree) { $scope = Enter-CodeLockOrExit -Path $BaselineFile -Reason 'assert-icon-contract.ps1 -UpdateBaseline' }
        $header = @(
            '# S3432 icon-contract baseline - one finding key per line; may fall, never rise.',
            '# Written by assert-icon-contract.ps1 -UpdateBaseline. A fix removes its line; a new key is fixed, mapped in',
            '# docs/icons/icon-contract-map.json, or answered by a vocabulary amendment - never added here by hand.'
        )
        [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($BaselineFile)))
        [System.IO.File]::WriteAllLines($BaselineFile, [string[]]($header + $current), [System.Text.UTF8Encoding]::new($false))
    }
    finally { if ($scope) { Exit-CodeLockScope -Scope $scope } }
    Stop-Verdict 0 "BASELINE WRITTEN ($($baseline.Count) -> $($current.Count))"
}

if ($new.Count -eq 0 -and $stale.Count -eq 0) {
    Stop-Verdict 0 "PASS ($($current.Count) baselined, $($excused.Count) excused)"
}

$changed = @()
if ($ChangedFiles) { $changed = @($ChangedFiles -split '[,;]' | ForEach-Object { ConvertTo-RelPath $_.Trim() } | Where-Object { $_ }) }
$widening = @($mapRel, $baselineRel, $gateRel)
$chargeAll = ($changed.Count -eq 0) -or (@($changed | Where-Object { $widening -contains $_ }).Count -gt 0)
$changedSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$changed, [StringComparer]::OrdinalIgnoreCase)

function Test-Charged([string] $Key, [bool] $IsStale) {
    if ($chargeAll) { return $true }
    if ($IsStale) {
        # A stale key's sources are gone from the scan; charge it to the file its key names, or to
        # any strings/doc input of the kind that produced it.
        $parts = $Key -split '\|'
        # A vector's key names no drawable file; its last reference left some Kotlin file of the module.
        if ($parts[0] -eq 'compose-unmapped') { return @($changed | Where-Object { $_ -like "$($parts[1])/*.kt" }).Count -gt 0 }
        $parts[0] = $parts[0] -replace '^compose-', ''
        if ($parts[0] -eq 'label-glyph') { return $changedSet.Contains($parts[2]) }
        if ($parts[0] -eq 'accessible-name') {
            return $changedSet.Contains($parts[2]) -or @($changed | Where-Object { $_ -like "$($parts[1])/*/strings*.xml" }).Count -gt 0
        }
        if ($parts[0] -eq 'unmapped') { return @($changed | Where-Object { $_ -match "/$([regex]::Escape($parts[2]))\.(xml|png|webp)$" }).Count -gt 0 }
        if ($parts[0] -eq 'name-substitution') { return @($changed | Where-Object { $_ -like "$($parts[1])/*/strings*.xml" }).Count -gt 0 }
        return @($changed | Where-Object { $_ -eq $docMapRel -or $_ -eq $termbaseRel }).Count -gt 0
    }
    return @($findings[$Key] | Where-Object { $changedSet.Contains($_) }).Count -gt 0
}

$chargedNew = @($new | Where-Object { Test-Charged $_ $false })
$chargedStale = @($stale | Where-Object { Test-Charged $_ $true })
$lines = [System.Collections.Generic.List[string]]::new()
foreach ($k in $new) { $lines.Add("NEW   $k"); $lines.Add("        $($sites[$k])") }
foreach ($k in $stale) { $lines.Add("STALE $k  (fixed - lower the baseline: assert-icon-contract.ps1 -UpdateBaseline)") }

if ($chargedNew.Count -gt 0 -or $chargedStale.Count -gt 0) {
    $lines.Add("A new key is fixed, mapped in $mapRel, or answered by a vocabulary amendment (ICON-SET rule 5) - never baselined by hand.")
    Stop-Verdict 1 "FAIL ($($chargedNew.Count) new, $($chargedStale.Count) stale charged to this set)" $lines.ToArray()
}
Stop-Verdict 3 "ADVISORY ($($new.Count) new, $($stale.Count) stale - none depends on a changed file)" $lines.ToArray()
