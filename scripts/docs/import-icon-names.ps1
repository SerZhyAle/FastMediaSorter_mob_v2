<#
.SYNOPSIS
    Import the reference product's names for every icon meaning in the languages beyond en, ru and uk.

.DESCRIPTION
    ICON-SET section 2 rule 3 gives every language beyond the three authored ones one wording per meaning:
    FastMediaSorter Android's own, read from its string resources where it ships the language and names the
    meaning. This script makes that import into iconography/vocabulary.jsonl of the shared contracts catalog.

    A string key names a meaning only when its default, ru and uk texts equal the record's name.en, name.ru and
    name.uk - exactly, or case-insensitively when no key matches exactly. That triple match is what keeps out a
    qualified form ("Previous page") and another meaning's word: a key whose three texts are the meaning's own
    three names cannot be either. For each further language of app_v2/src/main/res/xml/locales_config.xml:

      - a key's text is a candidate only when the locale defines the key and the text has no placeholder,
        markup, escape, line break or sentence-final mark the English name does not end with;
      - several matched keys that disagree are decided by majority; a tie leaves the language out;
      - a candidate equal to another meaning's name in that language is refused ("Zurück" is nav.back, never
        media.previous), and the record keeps whatever name it already had; when two candidates collide, the
        record that already carries the word keeps it, and when neither does, both are refused;
      - an existing name from another product that differs from the chosen wording is replaced.

    A locale with a script subtag is written under its language code (zh-Hans -> zh). Only the "name" object of
    a changed line is rewritten, in the order en, ru, uk, then the further languages in a fixed order, then any
    other key; every other byte of the file is kept, so a second run changes nothing. Rerun
    scripts/docs/export-icon-contract.ps1 afterwards to regenerate the catalog artifacts.

    Exit codes:
      0 - the import ran (or, under -DryRun, was computed); counts and every refusal and tie are printed.
      1 - the vocabulary or a string resource could not be parsed; nothing is written.
      2 - could not verify: no catalog root, no iconography/vocabulary.jsonl inside it, or no locales_config.xml.

.PARAMETER CatalogRoot
    Root of the shared contracts catalog. Falls back to FMS_CONTRACTS_ROOT in the process, then at user scope.

.PARAMETER RepoRoot
    Root of this repository, whose app_v2 string resources are read.

.PARAMETER DryRun
    Compute and print the import without writing vocabulary.jsonl.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/import-icon-names.ps1 -DryRun
#>
param(
    [string] $CatalogRoot = $env:FMS_CONTRACTS_ROOT,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Stop-Verdict([int] $code, [string] $word, [string[]] $reasons) {
    foreach ($r in $reasons) { Write-Host ('  - ' + $r) }
    Write-Host ('import-icon-names: ' + $word)
    exit $code
}

if ([string]::IsNullOrWhiteSpace($CatalogRoot)) { $CatalogRoot = [Environment]::GetEnvironmentVariable('FMS_CONTRACTS_ROOT', 'User') }
if ([string]::IsNullOrWhiteSpace($CatalogRoot)) {
    Stop-Verdict 2 'COULD NOT VERIFY' @('no catalog root: pass -CatalogRoot or set FMS_CONTRACTS_ROOT')
}
$vocabPath = Join-Path (Join-Path $CatalogRoot 'iconography') 'vocabulary.jsonl'
if (-not (Test-Path -LiteralPath $vocabPath)) { Stop-Verdict 2 'COULD NOT VERIFY' @("vocabulary not found: $vocabPath") }
$resDir = Join-Path $RepoRoot 'app_v2/src/main/res'
$localesPath = Join-Path $resDir 'xml/locales_config.xml'
if (-not (Test-Path -LiteralPath $localesPath)) { Stop-Verdict 2 'COULD NOT VERIFY' @("locales not found: $localesPath") }

$authored = @('en', 'ru', 'uk')
$preferredOrder = @('de', 'it', 'es', 'fr', 'pt', 'ar', 'hi', 'bn', 'ur', 'zh')
$finalMarks = [char[]] '.!?:;…۔।。！？'

# ------------------------------------------------------------------ string resources
function Get-NameText([System.Xml.XmlElement] $node) {
    if ($node.HasChildNodes -and @($node.ChildNodes | Where-Object { $_.NodeType -eq 'Element' }).Count) { return $null }
    $s = $node.InnerText.Trim()
    if ($s.Length -ge 2 -and $s.StartsWith('"') -and $s.EndsWith('"')) { $s = $s.Substring(1, $s.Length - 2).Trim() }
    $s = $s -replace "\\'", "'" -replace '\\"', '"' -replace '\\@', '@' -replace '\\\?', '?'
    if ($s -eq '' -or $s -match '[\\%\r\n<>]') { return $null }
    return $s
}

function Read-Strings([string] $dir) {
    $map = @{}
    $path = Join-Path $resDir $dir
    if (-not (Test-Path -LiteralPath $path)) { return $map }
    foreach ($f in Get-ChildItem -LiteralPath $path -Filter 'strings*.xml' | Sort-Object Name) {
        try { [xml] $x = [System.IO.File]::ReadAllText($f.FullName, $utf8) }
        catch { Stop-Verdict 1 'FAIL' @("cannot parse $($f.FullName): $($_.Exception.Message)") }
        foreach ($s in @($x.resources.string)) {
            if ($null -eq $s -or $s.GetAttribute('translatable') -eq 'false') { continue }
            $text = Get-NameText $s
            if ($null -ne $text) { $map[$s.GetAttribute('name')] = $text }
        }
    }
    return $map
}

[xml] $localesXml = [System.IO.File]::ReadAllText($localesPath, $utf8)
$further = [ordered]@{}
foreach ($loc in @($localesXml.'locale-config'.locale)) {
    $tag = $loc.GetAttribute('name', 'http://schemas.android.com/apk/res/android')
    $code = $tag.Split('-')[0]
    if ($authored -contains $code) { continue }
    $dir = if ($tag.Contains('-')) { 'values-b+' + $tag.Replace('-', '+') } else { 'values-' + $tag }
    $further[$code] = $dir
}
$langOrder = @($preferredOrder | Where-Object { $further.Contains($_) }) + @($further.Keys | Where-Object { $preferredOrder -notcontains $_ } | Sort-Object)

$strings = @{ en = Read-Strings 'values'; ru = Read-Strings 'values-ru'; uk = Read-Strings 'values-uk' }
foreach ($code in $further.Keys) { $strings[$code] = Read-Strings $further[$code] }

# ------------------------------------------------------------------ vocabulary
$lines = [System.IO.File]::ReadAllText($vocabPath, $utf8).Split("`n")
$records = New-Object System.Collections.Generic.List[object]
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ([string]::IsNullOrWhiteSpace($lines[$i])) { continue }
    try { $r = $lines[$i] | ConvertFrom-Json -AsHashtable }
    catch { Stop-Verdict 1 'FAIL' @("vocabulary.jsonl:$($i + 1) is not valid JSON") }
    $records.Add([pscustomobject]@{ Line = $i; Id = $r['id']; Name = $r['name']; Candidates = @{}; Ties = @() })
}

function Find-Keys($name, [bool] $exact) {
    $hits = New-Object System.Collections.Generic.List[string]
    foreach ($key in $strings.en.Keys) {
        $ok = $true
        foreach ($l in $authored) {
            $v = $strings[$l][$key]
            if ($null -eq $v -or -not $name[$l]) { $ok = $false; break }
            $same = if ($exact) { $v -ceq $name[$l] } else { $v -eq $name[$l] }
            if (-not $same) { $ok = $false; break }
        }
        if ($ok) { $hits.Add($key) }
    }
    return , @($hits | Sort-Object)
}

$matched = 0
foreach ($rec in $records) {
    $keys = Find-Keys $rec.Name $true
    if (-not $keys.Count) { $keys = Find-Keys $rec.Name $false }
    if (-not $keys.Count) { continue }
    $matched++
    $enFinal = $rec.Name['en'].TrimEnd().Length -gt 0 -and $finalMarks -contains $rec.Name['en'].TrimEnd()[-1]
    foreach ($code in $langOrder) {
        $votes = @{}
        foreach ($key in $keys) {
            $v = $strings[$code][$key]
            if ($null -eq $v) { continue }
            if (-not $enFinal -and $finalMarks -contains $v[-1]) { continue }
            $votes[$v] = 1 + [int] $votes[$v]
        }
        if (-not $votes.Count) { continue }
        $ranked = @($votes.GetEnumerator() | Sort-Object @{ Expression = 'Value'; Descending = $true }, @{ Expression = 'Key' })
        if ($ranked.Count -gt 1 -and $ranked[0].Value -eq $ranked[1].Value) {
            $top = @($ranked | Where-Object { $_.Value -eq $ranked[0].Value } | ForEach-Object Key)
            $rec.Ties += "$($rec.Id) $code - tie: $($top -join ' | ')"
            continue
        }
        $rec.Candidates[$code] = $ranked[0].Key
    }
}

# A refusal restores the record's old name, which may collide with a candidate elsewhere, so refuse until stable.
$refused = @{}
$byId = @{}
foreach ($rec in $records) { $byId[$rec.Id] = $rec }
do {
    $before = $refused.Count
    foreach ($code in $langOrder) {
        $final = @{}
        foreach ($rec in $records) {
            $v = if ($rec.Candidates.ContainsKey($code) -and -not $refused.ContainsKey("$($rec.Id)|$code")) { $rec.Candidates[$code] } else { $rec.Name[$code] }
            if ($v) { $final[$rec.Id] = $v.ToLowerInvariant() }
        }
        foreach ($rec in $records) {
            if (-not $rec.Candidates.ContainsKey($code) -or $refused.ContainsKey("$($rec.Id)|$code")) { continue }
            $mine = $rec.Candidates[$code].ToLowerInvariant()
            $others = @($final.GetEnumerator() | Where-Object { $_.Key -ne $rec.Id -and $_.Value -eq $mine } | ForEach-Object Key)
            if (-not $others.Count) { continue }
            # The record that already carries the word keeps it; only when none or several do is every claimant refused.
            $carriers = @($others | Where-Object { "$($byId[$_].Name[$code])".ToLowerInvariant() -eq $mine })
            $iCarry = "$($rec.Name[$code])".ToLowerInvariant() -eq $mine
            if ($iCarry -and -not $carriers.Count) { continue }
            $refused["$($rec.Id)|$code"] = "$($rec.Id) $code - '$($rec.Candidates[$code])' is the name of $($others -join ', ')"
        }
    }
} while ($refused.Count -ne $before)

# ------------------------------------------------------------------ write
function ConvertTo-JsonText([string] $s) {
    $sb = [System.Text.StringBuilder]::new('"')
    foreach ($ch in $s.ToCharArray()) {
        switch ($ch) {
            '"' { [void] $sb.Append('\"') }
            '\' { [void] $sb.Append('\\') }
            default { if ([int] $ch -lt 0x20) { [void] $sb.Append(('\u{0:x4}' -f [int] $ch)) } else { [void] $sb.Append($ch) } }
        }
    }
    return $sb.Append('"').ToString()
}

# The span of the top-level "name" object, found by a string-aware scan so a brace inside a value cannot end it.
function Get-NameSpan([string] $line) {
    $start = $line.IndexOf('"name": {')
    if ($start -lt 0) { return $null }
    $open = $start + '"name": '.Length
    $depth = 0; $inString = $false
    for ($j = $open; $j -lt $line.Length; $j++) {
        $ch = $line[$j]
        if ($inString) {
            if ($ch -eq '\') { $j++ } elseif ($ch -eq '"') { $inString = $false }
            continue
        }
        if ($ch -eq '"') { $inString = $true }
        elseif ($ch -eq '{') { $depth++ }
        elseif ($ch -eq '}') { $depth--; if ($depth -eq 0) { return @($open, $j) } }
    }
    return $null
}

$changed = 0; $written = 0; $replaced = New-Object System.Collections.Generic.List[string]
foreach ($rec in $records) {
    $names = [ordered]@{}
    foreach ($k in $rec.Name.Keys) { $names[$k] = $rec.Name[$k] }
    $dirty = $false
    foreach ($code in $langOrder) {
        if (-not $rec.Candidates.ContainsKey($code) -or $refused.ContainsKey("$($rec.Id)|$code")) { continue }
        $written++
        $new = $rec.Candidates[$code]
        if ($names[$code] -ceq $new) { continue }
        if ($names[$code]) { $replaced.Add("$($rec.Id) $code - '$($names[$code])' -> '$new'") }
        $names[$code] = $new
        $dirty = $true
    }
    if (-not $dirty) { continue }
    $changed++
    $order = @($authored) + $langOrder
    $order += @($names.Keys | Where-Object { $order -notcontains $_ })
    $parts = foreach ($k in $order) { if ($names.Contains($k)) { (ConvertTo-JsonText $k) + ': ' + (ConvertTo-JsonText $names[$k]) } }
    $line = $lines[$rec.Line]
    $span = Get-NameSpan $line
    if ($null -eq $span) { Stop-Verdict 1 'FAIL' @("$($rec.Id) - no top-level name object on its line") }
    $lines[$rec.Line] = $line.Substring(0, $span[0]) + '{' + ($parts -join ', ') + '}' + $line.Substring($span[1] + 1)
}

foreach ($r in $replaced) { Write-Host ('  replaced: ' + $r) }
foreach ($k in ($refused.Keys | Sort-Object)) { Write-Host ('  refused:  ' + $refused[$k]) }
foreach ($t in ($records | ForEach-Object Ties | Sort-Object)) { Write-Host ('  tied:     ' + $t) }
Write-Host ("import-icon-names: records $($records.Count), matched $matched, names imported $written, " +
    "refused $($refused.Count), tied $(@($records | ForEach-Object Ties).Count), changed: $changed" + $(if ($DryRun) { ' (dry run)' } else { '' }))
if (-not $DryRun -and $changed) { [System.IO.File]::WriteAllText($vocabPath, ($lines -join "`n"), $utf8) }
exit 0
