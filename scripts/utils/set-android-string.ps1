<#
.SYNOPSIS
    Surgical editor for Android <string> resources: set / add / get / remove / rename / list.

.DESCRIPTION
    Safe text-surgical tool for values/strings.xml, values-ru/strings.xml, values-uk/strings.xml and
    their thematic split files (strings_*.xml). Preserves surrounding file text, escaping, comments,
    BOM, and line endings - it never reserializes the file through [xml].

    Actions (-Action, default 'set' for backward compatibility):
      set    - update one key's value in ONE locale (-Locale -Key -Value). Original behavior.
               Supports -ExpectedOldValue guard and -CreateIfMissing upsert. Operates on strings.xml
               by default; pass -File strings_<theme>.xml to target a thematic split file.
      add    - create a new key in ALL THREE locales in lockstep (-Key -En -Ru -Uk [-File]).
               Fails if the key already exists in any strings*.xml of any locale.
      get    - print one key's value across EN/RU/UK (scans all strings*.xml). Exit 1 if missing anywhere.
      remove - delete one key from every locale (scans all strings*.xml). Reports .kt/@string references.
      rename - rename one key across every locale (-Key -NewKey). Reports .kt/@string references.
      list   - list strings*.xml files per locale with their string counts.
      move   - relocate a key from its current strings*.xml into a thematic -File, in ALL THREE locales
               in lockstep, byte-preserving (verbatim block, no reserialization). Single key via -Key,
               or bulk via -Prefix (moves every residual strings.xml key whose name starts with -Prefix).
               ATOMIC per key: all source removals + target insertions are computed in memory and written
               only if every locale planned cleanly, so an abort can never lose a key. A key missing in
               any locale, or already in the target, is skipped (lockstep safety). Creates -File if absent.
      audit  - print, per locale, the sorted union of every <string name> across all strings*.xml plus a
               count. Diff-friendly (one "<LOCALE>\t<key>" line each) - the before/after oracle that proves
               no key was lost or duplicated by a multi-file move. (S0339.)

    Locale parity: add/get/remove/rename/move always work on EN (values), RU (values-ru), UK (values-uk)
    together. set is single-locale by design (per-locale tone fixes).

.PARAMETER Action
    set | add | get | remove | rename | list (default: set).

.PARAMETER Module
    Module path relative to repo root, e.g. app_v2, wear.

.PARAMETER Locale
    Target locale for 'set': en -> values, ru -> values-ru, uk -> values-uk.

.PARAMETER Key
    Android string resource key.

.PARAMETER Value
    New string body for 'set'. Raw Android string text.

.PARAMETER En / -Ru / -Uk
    Raw (unescaped) per-locale values for 'add'. All three required - parity is mandatory.

.PARAMETER NewKey
    New key name for 'rename'.

.PARAMETER File
    Target strings file basename for 'set' / 'add' / 'move' (default strings.xml). For 'set' it selects
    the thematic split file holding the key. For 'move' it must be a thematic file (not the residual
    strings.xml).

.PARAMETER Prefix
    'move' bulk mode. Moves every key in the residual strings.xml whose name starts with this prefix.

.PARAMETER ExpectedOldValue
    'set' safety guard. If the current decoded value differs, the script aborts.

.PARAMETER CreateIfMissing
    'set' only. Appends a new <string> before </resources> if the key does not exist.

.PARAMETER DryRun
    Prints the planned change without writing.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale en -Key "cloud_check_failed" -Value "Could not check the cloud connection. Try again."

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key foo_title -En "Foo" -Ru "Фу" -Uk "Фу" -DryRun

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key app_name

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action rename -Key old_key -NewKey new_key

.EXAMPLE
    # Bulk-move every residual key prefixed "widget" into strings_widget.xml across EN/RU/UK:
    pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action move -Prefix widget -File strings_widget.xml

.EXAMPLE
    # Snapshot the per-locale key union (before/after a migration) to prove no loss:
    pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action audit
#>
[CmdletBinding()]
param(
    [ValidateSet('set', 'add', 'get', 'remove', 'rename', 'list', 'move', 'audit')]
    [string]$Action = 'set',

    [string]$Module = 'app_v2',

    [ValidateSet('en', 'ru', 'uk')]
    [string]$Locale,

    [string]$Key,

    [AllowEmptyString()]
    [string]$Value,

    [string]$En,
    [string]$Ru,
    [string]$Uk,
    [string]$NewKey,
    [string]$File = 'strings.xml',

    [string]$Prefix,

    [string]$ExpectedOldValue,
    [switch]$CreateIfMissing,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Capture at script scope: $PSBoundParameters inside a function refers to the function, not the script.
$valueBound = $PSBoundParameters.ContainsKey('Value')
$expectedOldBound = $PSBoundParameters.ContainsKey('ExpectedOldValue')

$repoRoot = Split-Path -Parent $PSScriptRoot | Split-Path -Parent
$resDir = Join-Path $repoRoot (Join-Path $Module 'src/main/res')
if (-not (Test-Path $resDir)) {
    throw "Resource dir not found for module '$Module': $resDir"
}

$locales = @(
    @{ Tag = 'EN'; Dir = 'values';    Value = $En },
    @{ Tag = 'RU'; Dir = 'values-ru'; Value = $Ru },
    @{ Tag = 'UK'; Dir = 'values-uk'; Value = $Uk }
)
$localeDirByTag = @{ en = 'values'; ru = 'values-ru'; uk = 'values-uk' }

function Test-KeySyntax([string]$k) {
    if ($k -notmatch '^[A-Za-z0-9_.]+$') {
        throw "Invalid Android string key '$k'. Use letters, digits, underscore, or dot only."
    }
}

function Get-FileEncodingForWrite([string]$Path) {
    # Missing target (freshly created thematic file): write UTF-8 without an encoder BOM.
    # New-ThematicSkeleton already carries a literal U+FEFF in its content when the locale's
    # strings.xml is BOM-prefixed, so the byte-level BOM is preserved either way.
    if (-not (Test-Path $Path)) { return [System.Text.UTF8Encoding]::new($false) }
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    return [System.Text.UTF8Encoding]::new($hasBom)
}

function ConvertTo-XmlText([AllowEmptyString()][string]$Text) {
    $escaped = [System.Security.SecurityElement]::Escape($Text)
    if ($null -eq $escaped) { return '' }
    return $escaped.Replace('&apos;', "\'")
}

function ConvertFrom-XmlText([AllowEmptyString()][string]$Text) {
    return ([System.Net.WebUtility]::HtmlDecode($Text)).Replace("\'", "'")
}

function Get-LocaleDir([string]$dir) { Join-Path $resDir $dir }

function Get-StringFiles([string]$dir) {
    if (-not (Test-Path $dir)) { return @() }
    Get-ChildItem -Path $dir -Filter 'strings*.xml' -File | Sort-Object Name
}

function Save-File([string]$path, [string]$content) {
    $encoding = Get-FileEncodingForWrite -Path $path
    [System.IO.File]::WriteAllText($path, $content, $encoding)
}

# Returns @{ File=<FileInfo>; Raw=<raw inner xml> } for the first strings*.xml containing key, else $null.
function Find-Key([string]$dir, [string]$key) {
    $esc = [regex]::Escape($key)
    $rx = '<string\b(?=[^>]*\bname\s*=\s*"' + $esc + '")(?:[^>]*)>(?<value>.*?)</string>'
    foreach ($f in Get-StringFiles $dir) {
        $content = [System.IO.File]::ReadAllText($f.FullName)
        $m = [regex]::Match($content, $rx, [System.Text.RegularExpressions.RegexOptions]::Singleline)
        if ($m.Success) { return @{ File = $f; Raw = $m.Groups['value'].Value } }
    }
    return $null
}

function Report-References([string]$key) {
    Write-Host ''
    Write-Host "Code references to '$key' (NOT modified - handle manually):" -ForegroundColor Yellow
    $srcRoot = Join-Path $resDir '..'
    $patterns = @(
        @{ Glob = '*.kt';   Pat = "R\.string\.$([regex]::Escape($key))\b" },
        @{ Glob = '*.java'; Pat = "R\.string\.$([regex]::Escape($key))\b" },
        @{ Glob = '*.xml';  Pat = "@string/$([regex]::Escape($key))\b" }
    )
    $found = 0
    foreach ($p in $patterns) {
        $hits = Get-ChildItem -Path $srcRoot -Recurse -Filter $p.Glob -File -ErrorAction SilentlyContinue |
            Select-String -Pattern $p.Pat -Encoding UTF8 -ErrorAction SilentlyContinue
        foreach ($h in $hits) { $found++; Write-Host ("  {0}:{1}" -f $h.Path, $h.LineNumber) -ForegroundColor DarkYellow }
    }
    if ($found -eq 0) { Write-Host '  none' -ForegroundColor DarkGray }
}

# ----- single-locale set (original behavior, byte-for-byte compatible) -----
function Invoke-Set {
    if (-not $Locale) { throw "set requires -Locale en|ru|uk." }
    if (-not $Key) { throw "set requires -Key." }
    if (-not $valueBound) { throw "set requires -Value." }
    Test-KeySyntax $Key

    $filePath = Join-Path $resDir (Join-Path $localeDirByTag[$Locale] $File)
    if (-not (Test-Path $filePath)) { throw "$File not found for module '$Module' locale '$Locale': $filePath" }

    $content = [System.IO.File]::ReadAllText($filePath)
    $newline = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    $keyRegex = [regex]::Escape($Key)
    $pattern = '<string\b(?=[^>]*\bname\s*=\s*"' + $keyRegex + '")(?:[^>]*)>(?<value>.*?)</string>'

    $stringEntries = @()
    $currentEntry = [regex]::Match($content, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)
    while ($currentEntry.Success) { $stringEntries += $currentEntry; $currentEntry = $currentEntry.NextMatch() }
    if ($stringEntries.Count -gt 1) {
        throw "Key '$Key' appears $($stringEntries.Count) times in $filePath. Refuse to guess which entry to update."
    }

    $escapedValue = ConvertTo-XmlText $Value
    $updatedContent = $content
    $action = ''
    $oldDecodedValue = $null

    if ($stringEntries.Count -eq 1) {
        $match = $stringEntries[0]
        $oldDecodedValue = ConvertFrom-XmlText $match.Groups['value'].Value
        if ($expectedOldBound -and $oldDecodedValue -ne $ExpectedOldValue) {
            throw "ExpectedOldValue mismatch for key '$Key' in $filePath.`nExpected: $ExpectedOldValue`nActual:   $oldDecodedValue"
        }
        $tagText = $match.Value
        $openTagEnd = $tagText.IndexOf('>')
        $closeTagStart = $tagText.LastIndexOf('</string>', [System.StringComparison]::Ordinal)
        if ($openTagEnd -lt 0 -or $closeTagStart -lt 0) { throw "Could not rewrite key '$Key' in $filePath." }
        $newTagText = $tagText.Substring(0, $openTagEnd + 1) + $escapedValue + $tagText.Substring($closeTagStart)
        $updatedContent = $content.Substring(0, $match.Index) + $newTagText + $content.Substring($match.Index + $match.Length)
        $action = 'update'
    }
    else {
        if (-not $CreateIfMissing) { throw "Key '$Key' not found in $filePath. Use -CreateIfMissing to append it." }
        if ($expectedOldBound) { throw "ExpectedOldValue cannot be used together with -CreateIfMissing for a missing key." }
        $closingIndex = $content.LastIndexOf('</resources>', [System.StringComparison]::Ordinal)
        if ($closingIndex -lt 0) { throw "Could not find </resources> in $filePath." }
        $prefix = $content.Substring(0, $closingIndex)
        $separator = if ($prefix.EndsWith("`n") -or $prefix.EndsWith("`r")) { '' } else { $newline }
        $newEntry = '    <string name="' + $Key + '">' + $escapedValue + '</string>' + $newline
        $updatedContent = $prefix + $separator + $newEntry + $content.Substring($closingIndex)
        $action = 'create'
    }

    if ($updatedContent -eq $content) {
        Write-Host "[no change] ${Locale}:$Key in $filePath already matches the requested value." -ForegroundColor Yellow
        return
    }
    if ($DryRun) {
        Write-Host "[dry-run] $action ${Locale}:$Key in $filePath" -ForegroundColor Cyan
        if ($null -ne $oldDecodedValue) { Write-Host "Old: $oldDecodedValue" -ForegroundColor DarkGray }
        Write-Host "New: $Value" -ForegroundColor Green
        return
    }
    Save-File $filePath $updatedContent
    Write-Host "[done] $action ${Locale}:$Key in $filePath" -ForegroundColor Green
    if ($null -ne $oldDecodedValue) { Write-Host "Old: $oldDecodedValue" -ForegroundColor DarkGray }
    Write-Host "New: $Value" -ForegroundColor Green
}

# Skeleton for a freshly created thematic file, matching the locale's strings.xml BOM/EOL.
function New-ThematicSkeleton([string]$refPath) {
    $ref = [System.IO.File]::ReadAllText($refPath)
    $newline = if ($ref.Contains("`r`n")) { "`r`n" } else { "`n" }
    $bom = $ref.Length -gt 0 -and $ref[0] -eq [char]0xFEFF
    $body = '<?xml version="1.0" encoding="utf-8"?>' + $newline + '<resources>' + $newline + '</resources>' + $newline
    if ($bom) { return ([char]0xFEFF) + $body } else { return $body }
}

# Returns the raw, verbatim "<string ..>..</string>" block (no leading ws / trailing newline) for a key in a file, else $null.
function Get-RawStringBlock([string]$content, [string]$key) {
    $esc = [regex]::Escape($key)
    $rx = '<string\b(?=[^>]*\bname\s*=\s*"' + $esc + '")(?:[^>]*)>(?s:.*?)</string>'
    $m = [regex]::Match($content, $rx)
    if ($m.Success) { return $m.Value }
    return $null
}

# Regex that eats the whole physical line(s) of a key incl. leading ws + trailing EOL (for excision).
function Get-LineRemovalRegex([string]$key) {
    $esc = [regex]::Escape($key)
    return "(?m)^[ \t]*<string\b(?=[^>]*\bname\s*=\s*`"$esc`")(?:[^>]*)>(?s:.*?)</string>[ \t]*\r?\n"
}

# Insert a verbatim block before </resources> in an in-memory $content string.
function Add-BlockToContent([string]$content, [string]$block) {
    $newline = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
    $idx = $content.LastIndexOf('</resources>', [System.StringComparison]::Ordinal)
    if ($idx -lt 0) { throw "No </resources> in target content" }
    $prefix = $content.Substring(0, $idx)
    $sep = if ($prefix.EndsWith("`n") -or $prefix.EndsWith("`r")) { '' } else { $newline }
    $line = '    ' + $block + $newline
    return $prefix + $sep + $line + $content.Substring($idx)
}

# Move ONE key across EN/RU/UK in lockstep from its current file into $File. Returns $true on success.
# ATOMIC: every source-removal and target-insertion for all three locales is computed in memory first;
# files are written only after the full plan succeeds, so an abort mid-key can never lose a key.
function Move-OneKey([string]$key, [string]$targetFile) {
    $plan = @()
    foreach ($loc in $locales) {
        $dir = Get-LocaleDir $loc.Dir
        $hit = $null
        foreach ($f in Get-StringFiles $dir) {
            $content = [System.IO.File]::ReadAllText($f.FullName)
            $block = Get-RawStringBlock $content $key
            if ($block) { $hit = @{ File = $f.FullName; Block = $block }; break }
        }
        if (-not $hit) { Write-Host "  [SKIP] '$key' missing in [$($loc.Tag)] - locale mismatch" -ForegroundColor Red; return $false }
        $plan += @{ Tag = $loc.Tag; Dir = $dir; Source = $hit.File; Block = $hit.Block }
    }
    foreach ($p in $plan) {
        $targetPath = Join-Path $p.Dir $targetFile
        if (([System.IO.Path]::GetFullPath($p.Source)) -ieq ([System.IO.Path]::GetFullPath($targetPath))) {
            Write-Host "  [SKIP] '$key' already in $targetFile [$($p.Tag)]" -ForegroundColor DarkGray
            return $false
        }
    }
    if ($DryRun) { Write-Host "  [dry] move '$key' -> $targetFile (EN/RU/UK)" -ForegroundColor Cyan; return $true }

    # --- compute all writes in memory; nothing is persisted until every locale is planned cleanly ---
    $writes = [ordered]@{}   # path -> content (last write wins; same target accumulates across locales)
    $rx = Get-LineRemovalRegex $key
    foreach ($p in $plan) {
        $targetPath = Join-Path $p.Dir $targetFile
        # source content may already be pending an edit from this same key (only if src==target, excluded above)
        $srcContent = if ($writes.Contains($p.Source)) { $writes[$p.Source] } else { [System.IO.File]::ReadAllText($p.Source) }
        $srcNew = [regex]::Replace($srcContent, $rx, '', 1)
        if ($srcNew -eq $srcContent) { $srcNew = $srcContent.Replace($p.Block, '') }
        if ($srcNew -eq $srcContent) { throw "Could not excise '$key' from $($p.Source)" }
        $writes[$p.Source] = $srcNew

        $refStrings = Join-Path $p.Dir 'strings.xml'
        if (-not $writes.Contains($targetPath)) {
            if (-not (Test-Path $targetPath)) { $writes[$targetPath] = New-ThematicSkeleton $refStrings }
            else { $writes[$targetPath] = [System.IO.File]::ReadAllText($targetPath) }
        }
        $writes[$targetPath] = Add-BlockToContent $writes[$targetPath] $p.Block
    }
    # --- commit ---
    foreach ($path in $writes.Keys) { Save-File $path $writes[$path] }
    Write-Host "  [ok]  moved '$key' -> $targetFile (EN/RU/UK)" -ForegroundColor Green
    return $true
}

function Invoke-Move {
    if (-not $File -or $File -eq 'strings.xml') { throw "move requires -File <strings_theme.xml> (not the residual strings.xml)." }
    if ($Key) {
        Test-KeySyntax $Key
        $ok = Move-OneKey $Key $File
        if (-not $ok -and -not $DryRun) { exit 1 }
        exit 0
    }
    if (-not $Prefix) { throw "move requires -Key or -Prefix." }
    # Bulk: candidates are keys living in the RESIDUAL strings.xml of EN whose name starts with the prefix.
    $enResidual = Join-Path (Get-LocaleDir 'values') 'strings.xml'
    $content = [System.IO.File]::ReadAllText($enResidual)
    $names = [regex]::Matches($content, '<string\b[^>]*\bname\s*=\s*"([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
    $candidates = @($names | Where-Object { $_.StartsWith($Prefix) } | Sort-Object -Unique)
    Write-Host "[$Prefix] -> $File : $($candidates.Count) candidate(s) in residual strings.xml" -ForegroundColor Cyan
    $moved = 0; $failed = 0
    foreach ($k in $candidates) {
        if (Move-OneKey $k $File) { $moved++ } else { $failed++ }
    }
    Write-Host "[$Prefix] moved=$moved failed/skipped=$failed" -ForegroundColor $(if ($failed) { 'Yellow' } else { 'Green' })
    if ($failed -gt 0 -and -not $DryRun) { exit 1 }
    exit 0
}

function Invoke-Audit {
    foreach ($loc in $locales) {
        $dir = Get-LocaleDir $loc.Dir
        $keys = New-Object System.Collections.Generic.List[string]
        foreach ($f in Get-StringFiles $dir) {
            $content = [System.IO.File]::ReadAllText($f.FullName)
            foreach ($m in [regex]::Matches($content, '<string\b[^>]*\bname\s*=\s*"([^"]+)"')) {
                $keys.Add($m.Groups[1].Value)
            }
        }
        $sorted = $keys | Sort-Object -Unique
        Write-Host "## $($loc.Tag) count=$($sorted.Count)"
        foreach ($k in $sorted) { Write-Host ("{0}`t{1}" -f $loc.Tag, $k) }
    }
    exit 0
}

switch ($Action) {

    'set' { Invoke-Set; exit 0 }

    'move' { Invoke-Move }

    'audit' { Invoke-Audit }

    'list' {
        foreach ($loc in $locales) {
            $dir = Get-LocaleDir $loc.Dir
            Write-Host ''
            Write-Host "[$($loc.Tag)] $dir" -ForegroundColor Cyan
            foreach ($f in Get-StringFiles $dir) {
                $n = ([regex]::Matches([System.IO.File]::ReadAllText($f.FullName), '<string name=')).Count
                Write-Host ("  {0,-40} {1,5} strings" -f $f.Name, $n)
            }
        }
        exit 0
    }

    'get' {
        if (-not $Key) { throw "get requires -Key." }
        Write-Host ''
        $anyMiss = $false
        foreach ($loc in $locales) {
            $hit = Find-Key (Get-LocaleDir $loc.Dir) $Key
            if ($hit) {
                Write-Host ("[{0}] {1}" -f $loc.Tag, $hit.File.Name) -ForegroundColor Green
                Write-Host ("      {0}" -f (ConvertFrom-XmlText $hit.Raw))
            }
            else { $anyMiss = $true; Write-Host ("[{0}] MISSING" -f $loc.Tag) -ForegroundColor Red }
        }
        if ($anyMiss) { exit 1 } else { exit 0 }
    }

    'add' {
        if (-not $Key) { throw "add requires -Key." }
        Test-KeySyntax $Key
        if (($null -eq $En) -or ($null -eq $Ru) -or ($null -eq $Uk)) {
            throw "add requires -En, -Ru and -Uk (locale parity is mandatory)."
        }
        foreach ($loc in $locales) {
            $existing = Find-Key (Get-LocaleDir $loc.Dir) $Key
            if ($existing) { throw "Key '$Key' already exists in [$($loc.Tag)] $($existing.File.Name) - aborting." }
        }
        foreach ($loc in $locales) {
            $target = Join-Path (Get-LocaleDir $loc.Dir) $File
            if (-not (Test-Path $target)) { throw "Target file not found: $target" }
            $content = [System.IO.File]::ReadAllText($target)
            $newline = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }
            $escaped = ConvertTo-XmlText $loc.Value
            $line = '    <string name="' + $Key + '">' + $escaped + '</string>'
            $idx = $content.LastIndexOf('</resources>', [System.StringComparison]::Ordinal)
            if ($idx -lt 0) { throw "No </resources> in $target" }
            $prefix = $content.Substring(0, $idx)
            $separator = if ($prefix.EndsWith("`n") -or $prefix.EndsWith("`r")) { '' } else { $newline }
            $newContent = $prefix + $separator + $line + $newline + $content.Substring($idx)
            if ($DryRun) {
                Write-Host "[$($loc.Tag)] would insert into $File :" -ForegroundColor Yellow
                Write-Host "  $line"
            }
            else { Save-File $target $newContent; Write-Host "[$($loc.Tag)] added to $File" -ForegroundColor Green }
        }
        if (-not $DryRun) {
            Write-Host ''
            Write-Host "Validate parity: scripts/check_strings_localized.ps1 -KeyPrefix `"$Key`"" -ForegroundColor Cyan
        }
        exit 0
    }

    'remove' {
        if (-not $Key) { throw "remove requires -Key." }
        $esc = [regex]::Escape($Key)
        $rx = "(?m)^[ \t]*<string\b(?=[^>]*\bname\s*=\s*`"$esc`")(?:[^>]*)>(?s:.*?)</string>[ \t]*\r?\n"
        $removed = $false
        foreach ($loc in $locales) {
            foreach ($f in Get-StringFiles (Get-LocaleDir $loc.Dir)) {
                $content = [System.IO.File]::ReadAllText($f.FullName)
                if ($content -match $rx) {
                    $new = [regex]::Replace($content, $rx, '', 1)
                    if ($DryRun) { Write-Host "[$($loc.Tag)] would remove '$Key' from $($f.Name)" -ForegroundColor Yellow }
                    else { Save-File $f.FullName $new; Write-Host "[$($loc.Tag)] removed '$Key' from $($f.Name)" -ForegroundColor Green }
                    $removed = $true
                    break
                }
            }
        }
        if (-not $removed) { Write-Host "Key '$Key' not found in any locale." -ForegroundColor Yellow }
        Report-References $Key
        exit 0
    }

    'rename' {
        if (-not $Key) { throw "rename requires -Key." }
        if (-not $NewKey) { throw "rename requires -NewKey." }
        Test-KeySyntax $NewKey
        foreach ($loc in $locales) {
            $clash = Find-Key (Get-LocaleDir $loc.Dir) $NewKey
            if ($clash) { throw "Target key '$NewKey' already exists in [$($loc.Tag)] $($clash.File.Name) - aborting." }
        }
        $esc = [regex]::Escape($Key)
        $rx = "(<string\b[^>]*\bname\s*=\s*`")$esc(`")"
        $renamed = $false
        foreach ($loc in $locales) {
            foreach ($f in Get-StringFiles (Get-LocaleDir $loc.Dir)) {
                $content = [System.IO.File]::ReadAllText($f.FullName)
                if ($content -match $rx) {
                    $new = [regex]::Replace($content, $rx, "`${1}$NewKey`${2}", 1)
                    if ($DryRun) { Write-Host "[$($loc.Tag)] would rename '$Key' -> '$NewKey' in $($f.Name)" -ForegroundColor Yellow }
                    else { Save-File $f.FullName $new; Write-Host "[$($loc.Tag)] renamed '$Key' -> '$NewKey' in $($f.Name)" -ForegroundColor Green }
                    $renamed = $true
                    break
                }
            }
        }
        if (-not $renamed) { Write-Host "Key '$Key' not found in any locale." -ForegroundColor Yellow }
        Report-References $Key
        exit 0
    }
}
