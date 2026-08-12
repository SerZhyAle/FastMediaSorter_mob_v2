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
      remove - delete one key from every locale (scans all strings*.xml). Deletes whichever element
               declares it - <string>, <plurals> or <string-array> - including its <item> children.
               REFUSES when the key is still referenced anywhere under <module>/src (any source set, any
               resource kind); -Force overrides the refusal but never the scan.
      rename - rename one key across every locale (-Key -NewKey), for all three element kinds. Lists the
               references that must be rewritten to the new name, before touching any file. Advisory:
               renaming a referenced key is the normal case, so it never refuses.
      list   - list strings*.xml files per locale with their string counts.
      move   - relocate a key from its current strings*.xml into a thematic -File, in ALL THREE locales
               in lockstep, byte-preserving (verbatim block, no reserialization). Single key via -Key,
               or bulk via -Prefix (moves every residual strings.xml key whose name starts with -Prefix).
               ATOMIC per key: all source removals + target insertions are computed in memory and written
               only if every locale planned cleanly, so an abort can never lose a key. A key missing in
               any locale, or already in the target, is skipped (lockstep safety). Creates -File if absent.
      audit  - print, per locale, the sorted union of every <string>, <plurals> and <string-array> name
               across all strings*.xml plus a count. Diff-friendly (one "<LOCALE>\t<key>" line each) - the
               before/after oracle that proves no key was lost or duplicated by a multi-file move.
               (S0339; widened past <string> in S1568.)

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
    New string body for 'set'. Raw Android string text. Inline markup Android renders inside a
    <string> body (b, i, u, em, strong, sup, sub, strike, tt, big, small, br, xliff:g) is written
    through verbatim; any other tag-shaped fragment is refused rather than escaped into literal text
    (S1576). A '<' that is not tag-shaped, e.g. "1 < 2", is still escaped as text.

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

.PARAMETER KeyList
    'remove' batch mode. Path to a file holding one key per line; blank lines and lines starting with
    '#' are ignored, and anything after the key on a line is treated as a comment, so an audit report
    with trailing reasons can be fed in unedited. Builds the reference index ONCE for the whole list
    instead of walking the source tree per key. Mutually exclusive with -Key.

.PARAMETER ExpectedOldValue
    'set' safety guard. If the current decoded value differs, the script aborts.

.PARAMETER CreateIfMissing
    'set' only. Appends a new <string> before </resources> if the key does not exist.

.PARAMETER DryRun
    Prints the planned change without writing.

.PARAMETER Force
    'remove' only. Proceeds even though the key is still referenced. The reference scan still runs and still
    prints - only the refusal is waived.

.NOTES
    Exit codes:
      0 - the requested action completed.
      1 - invalid arguments, a lockstep/parity precondition failed, or the value carries markup this
          editor will not silently escape (thrown).
      3 - 'remove' refused: the key is still referenced under <module>/src. Pass -Force to override.
          In -KeyList batch mode this means AT LEAST ONE key was refused; the rest of the list was
          still applied. A partially refused batch never exits 0.

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

    # Validated at runtime against locales_config.xml, not by a ValidateSet: the declared set is data
    # (S1190) and a literal list here would pin the tool back to three languages.
    [string]$Locale,

    [string]$Key,

    [AllowEmptyString()]
    [string]$Value,

    [string]$En,
    [string]$Ru,
    [string]$Uk,

    # Optional per-locale values for the declared languages outside the strict trio, e.g.
    # -Translations @{ de = 'Abbrechen'; it = 'Annulla' }. A locale absent here is not an error.
    [hashtable]$Translations,
    [string]$NewKey,
    [string]$File = 'strings.xml',

    [string]$Prefix,

    [string]$KeyList,

    [string]$ExpectedOldValue,
    [switch]$CreateIfMissing,
    [switch]$DryRun,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\quality\lib\android-string-format.ps1')
. (Join-Path $PSScriptRoot '..\quality\lib\android-string-liveness.ps1')

# Capture at script scope: $PSBoundParameters inside a function refers to the function, not the script.
$valueBound = $PSBoundParameters.ContainsKey('Value')
$expectedOldBound = $PSBoundParameters.ContainsKey('ExpectedOldValue')

$repoRoot = Split-Path -Parent $PSScriptRoot | Split-Path -Parent
$resDir = Join-Path $repoRoot (Join-Path $Module 'src/main/res')
if (-not (Test-Path $resDir)) {
    throw "Resource dir not found for module '$Module': $resDir"
}

# S1190: the locale set is read from locales_config.xml. Two tiers, per strategic ADR-6:
#   $locales         - the strict trio, kept in lockstep. Every action that must not lose a key in a
#                      hand-authored language (add/move/rename parity) iterates exactly this set, so
#                      declaring a new language cannot start failing moves for a language nobody has
#                      translated yet.
#   $optionalLocales - declared languages beyond the trio whose values-XX directory already exists.
#                      Best-effort: written when a value is supplied, swept on remove/rename so a
#                      deleted key leaves no orphan behind.
. (Join-Path $PSScriptRoot 'locale-set.ps1')

$declaredLocaleTags = Get-SupportedLocales
$strictValueByTag = @{ en = $En; ru = $Ru; uk = $Uk }

$locales = @(
    Get-StrictLocales | ForEach-Object {
        @{
            Tag   = $_.ToUpperInvariant()
            Code  = $_
            Dir   = (Get-LocaleResourceDir -Tag $_)
            Value = $strictValueByTag[$_]
        }
    }
)

$optionalLocales = @(
    $declaredLocaleTags |
    Where-Object { -not (Test-StrictLocale -Tag $_) } |
    ForEach-Object {
        @{
            Tag   = $_.ToUpperInvariant()
            Code  = $_
            Dir   = (Get-LocaleResourceDir -Tag $_)
            Value = $(if ($Translations) { $Translations[$_] } else { $null })
        }
    } |
    Where-Object { Test-Path (Join-Path $resDir $_.Dir) }
)

# Every locale that actually has a directory on disk - the sweep set for read and delete actions.
$allLocales = @($locales + $optionalLocales)

$localeDirByTag = @{}
foreach ($tag in $declaredLocaleTags) { $localeDirByTag[$tag] = Get-LocaleResourceDir -Tag $tag }

if ($Locale) {
    # Resolve case-insensitively so '-Locale zh-hans' still finds the declared 'zh-Hans'.
    # Select-Object, not [0]: StrictMode turns indexing an empty result into an index-out-of-bounds
    # error that would mask the real "unknown locale" message.
    $resolvedLocale = $declaredLocaleTags | Where-Object { $_ -ieq $Locale.Trim() } | Select-Object -First 1
    if (-not $resolvedLocale) {
        throw "Unknown locale '$Locale'. Declared locales: $($declaredLocaleTags -join ', ')."
    }
    $Locale = $resolvedLocale
}

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

# S1576: inline markup Android parses inside a <string> body. A '<' means two different things in a
# string value - literal text that must be escaped, and markup that must not be - and escaping both
# alike shipped a literal "&lt;b&gt;" to the user with no refusal and no warning.
$INLINE_MARKUP_TAGS = @('b', 'i', 'u', 'em', 'strong', 'sup', 'sub', 'strike', 'tt', 'big', 'small', 'br', 'xliff:g')
$INLINE_MARKUP_PATTERN = '(?i)</?(?:' + (($INLINE_MARKUP_TAGS | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')(?:\s[^<>]*?)?/?>'

# Escapes one run of plain text. Refuses anything tag-shaped: silently escaping an unrecognised tag
# is the same defect as before, just deferred to the next caller.
function ConvertTo-EscapedXmlSegment([AllowEmptyString()][string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return '' }
    $suspect = [regex]::Match($Text, '<[A-Za-z/][^<>]{0,40}>?')
    if ($suspect.Success) {
        throw ("Unsupported markup '$($suspect.Value)' in the value. This editor passes these inline tags " +
            "through verbatim: $($INLINE_MARKUP_TAGS -join ', '). Anything else would be escaped into " +
            'literal text and shipped to the user, so it is refused instead. Edit the file by hand if the ' +
            'tag is genuinely needed.')
    }
    $escaped = [System.Security.SecurityElement]::Escape($Text)
    if ($null -eq $escaped) { return '' }
    # S1567: must stay identical to ConvertTo-XmlText in scripts/utils/seed-locale-tranche.ps1, whose
    # .DESCRIPTION carries the measured AAPT2 truth table. A quote survives only when a backslash
    # precedes it after XML decoding, so &quot; is not a safe encoding. The optional leading backslash
    # makes the pass idempotent: an already-escaped value collapses to the same single-backslash form
    # rather than growing a second slash, which AAPT2 refuses for an apostrophe and ships as a literal
    # slash for a quote.
    $escaped = [regex]::Replace($escaped, '\\?&apos;', "\'")
    return [regex]::Replace($escaped, '\\?&quot;', '\"')
}

function ConvertTo-XmlText([AllowEmptyString()][string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return '' }
    $builder = [System.Text.StringBuilder]::new()
    $cursor = 0
    foreach ($tag in [regex]::Matches($Text, $INLINE_MARKUP_PATTERN)) {
        [void]$builder.Append((ConvertTo-EscapedXmlSegment $Text.Substring($cursor, $tag.Index - $cursor)))
        [void]$builder.Append($tag.Value)
        $cursor = $tag.Index + $tag.Length
    }
    [void]$builder.Append((ConvertTo-EscapedXmlSegment $Text.Substring($cursor)))
    return $builder.ToString()
}

function ConvertFrom-XmlText([AllowEmptyString()][string]$Text) {
    # S1567: the decode has to unwind the quote escape too, or -ExpectedOldValue never matches a value
    # written after the escaping fix and `get` prints the backslash back to the caller. HtmlDecode runs
    # first so the legacy \&quot; spelling reduces to \" before the backslash comes off.
    return ([System.Net.WebUtility]::HtmlDecode($Text)).Replace("\'", "'").Replace('\"', '"')
}

function Get-LocaleDir([string]$dir) { Join-Path $resDir $dir }

function Assert-FormatValue([string]$LocaleTag, [AllowEmptyString()][string]$Text) {
    $analysis = Get-AndroidStringFormatAnalysis -Value $Text
    if ($analysis.Errors.Count -eq 0) { return }
    $reasons = @($analysis.Errors | ForEach-Object { $_.Message }) -join ' '
    throw "Invalid string-format syntax in [$LocaleTag] value: $reasons"
}

function Assert-FormatParity([string]$KeyName, [hashtable]$ValuesByLocale) {
    $entries = @{}
    foreach ($localeTag in $ValuesByLocale.Keys) {
        $entries[$localeTag] = [pscustomobject]@{
            Locale = $localeTag
            File = ''
            Analysis = (Get-AndroidStringFormatAnalysis -Value $ValuesByLocale[$localeTag])
        }
    }

    $findings = @(Compare-AndroidStringFormatContracts -Key $KeyName -EntriesByLocale $entries)
    if ($findings.Count -eq 0) { return }
    $message = @($findings | ForEach-Object { $_.Message }) -join ' '
    throw "Format contract mismatch across locales for '$KeyName': $message"
}

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

# Scan root is <module>/src, not <module>/src/main: 40 of app_v2's 41 source-set directories are flavor,
# feature or test sets, and 222 keys of values/strings.xml are referenced ONLY from one of them - against
# which the old src/main-only scan printed "none", i.e. "safe to delete" (S1571). All three resource kinds
# are matched because <plurals> and <string-array> names share this file and previously had no check at all.
#
# S1568: the scan itself now lives in scripts/quality/lib/android-string-liveness.ps1 and is shared with
# the audit report and the ratchet gate. Removal and audit must answer from one definition of "a
# reference" (strategic ADR-2) - when they were separate functions, the shorter one was the one holding
# the safety catch on an irreversible deletion.
function Get-KeyReferences([string]$key) {
    $srcRoot = Join-Path $repoRoot (Join-Path $Module 'src')
    return @(Get-ResourceReferenceLocations -SrcRoot $srcRoot -Name $key -RepoRoot $repoRoot)
}

# Resolves what KIND of element declares this key in this file, then builds the regex that deletes that
# whole element including any <item> children. Returns $null when the file does not declare the key.
#
# S1568: the previous version hard-coded <string> on both ends, so a <plurals> or <string-array> name was
# reported as "not found in any locale" and quietly survived every removal - the audit found one genuinely
# dead <plurals> (sync_interval_hours) that could not be deleted at all. `<string\b` is also not a safe
# stand-in for "a string element": the g/- junction is a word boundary, so it matches `<string-array` too.
function Get-KeyRemovalRegex([string]$Content, [string]$Key) {
    $decl = [regex]::Match($Content, (New-ResourceDeclarationPattern -Name $Key))
    if (-not $decl.Success) { return $null }

    $kind = $decl.Groups['kind'].Value
    $esc = [regex]::Escape($Key)
    # Line-anchored and byte-preserving, exactly as before: match the element and the newline that ends
    # its line, so surrounding text and indentation of neighbours are untouched.
    return "(?m)^[ \t]*<$kind\b(?=[^>]*\bname\s*=\s*`"$esc`")(?:[^>]*)>(?s:.*?)</$kind>[ \t]*\r?\n"
}

# Deletes one key from every locale on disk, not just the strict trio: a key deleted only from en/ru/uk
# leaves an orphan in every translated locale, and no gate in the repository notices that (S1568).
# Returns $true when at least one locale carried it. Shared by the single-key and batch paths so the two
# cannot drift on what "removing a key" means.
function Remove-KeyFromLocales([string]$Key) {
    $removed = $false
    foreach ($loc in $allLocales) {
        foreach ($f in Get-StringFiles (Get-LocaleDir $loc.Dir)) {
            $content = [System.IO.File]::ReadAllText($f.FullName)
            $rx = Get-KeyRemovalRegex -Content $content -Key $Key
            if (-not $rx) { continue }
            $new = [regex]::Replace($content, $rx, '', 1)
            if ($DryRun) { Write-Host "[$($loc.Tag)] would remove '$Key' from $($f.Name)" -ForegroundColor Yellow }
            else { Save-File $f.FullName $new; Write-Host "[$($loc.Tag)] removed '$Key' from $($f.Name)" -ForegroundColor Green }
            $removed = $true
            break
        }
    }
    return $removed
}

# Prints the scan and hands the hits back, so a caller can decide BEFORE mutating. The old function only
# printed, and printed after the deletion had already happened - a post-mortem, not a safety check.
function Show-KeyReferences([string]$key, [string[]]$hits, [string]$note) {
    Write-Host ''
    if ($hits.Count -eq 0) {
        Write-Host "No references to '$key' anywhere under $Module/src." -ForegroundColor DarkGray
        return
    }
    Write-Host "References to '$key' under $Module/src ($($hits.Count)) - $note" -ForegroundColor Yellow
    foreach ($h in $hits) { Write-Host "  $h" -ForegroundColor DarkYellow }
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

    Assert-FormatValue -LocaleTag $Locale.ToUpperInvariant() -Text $Value
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
        # S1576: the escaped form is what actually lands in the file. Printing only -Value made the
        # preview unable to show the very substitution it exists to catch.
        Write-Host "Escaped: $escapedValue" -ForegroundColor DarkGreen
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
        if (-not $ok -and -not $DryRun) {
            Write-Error "move: key '$Key' was not moved into '$File' - see the message above." -ErrorAction Continue
            exit 1
        }
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
    foreach ($loc in $allLocales) {
        $dir = Get-LocaleDir $loc.Dir
        $keys = New-Object System.Collections.Generic.List[string]
        foreach ($f in Get-StringFiles $dir) {
            $content = [System.IO.File]::ReadAllText($f.FullName)
            # All three kinds: as the before/after oracle for a migration, an audit blind to <plurals>
            # and <string-array> would report "no change" across a deletion that did remove one.
            foreach ($m in [regex]::Matches($content, '<(?:string-array|plurals|string)\b[^>]*\bname\s*=\s*"([^"]+)"')) {
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
        foreach ($loc in $allLocales) {
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
        foreach ($loc in $allLocales) {
            $hit = Find-Key (Get-LocaleDir $loc.Dir) $Key
            $strict = Test-StrictLocale -Tag $loc.Code
            if ($hit) {
                Write-Host ("[{0}] {1}" -f $loc.Tag, $hit.File.Name) -ForegroundColor Green
                Write-Host ("      {0}" -f (ConvertFrom-XmlText $hit.Raw))
            }
            elseif ($strict) {
                # Only a hand-authored locale makes a lookup fail; a machine-translated one is a gap,
                # not an error (strategic ADR-6).
                $anyMiss = $true
                Write-Host ("[{0}] MISSING" -f $loc.Tag) -ForegroundColor Red
            }
            else {
                Write-Host ("[{0}] not translated" -f $loc.Tag) -ForegroundColor DarkGray
            }
        }
        if ($anyMiss) { exit 1 } else { exit 0 }
    }

    'add' {
        if (-not $Key) { throw "add requires -Key." }
        Test-KeySyntax $Key
        if (($null -eq $En) -or ($null -eq $Ru) -or ($null -eq $Uk)) {
            throw "add requires -En, -Ru and -Uk (locale parity is mandatory)."
        }
        foreach ($loc in $allLocales) {
            if (Test-StrictLocale -Tag $loc.Code) { Assert-FormatValue -LocaleTag $loc.Tag -Text $loc.Value }
            $existing = Find-Key (Get-LocaleDir $loc.Dir) $Key
            if ($existing) { throw "Key '$Key' already exists in [$($loc.Tag)] $($existing.File.Name) - aborting." }
        }
        Assert-FormatParity -KeyName $Key -ValuesByLocale @{ EN = $En; RU = $Ru; UK = $Uk }
        # Optional locales are best-effort: a supplied value is written, a missing one is silently
        # skipped, and a locale whose thematic file does not exist yet is reported rather than fatal.
        $suppliedOptional = @($optionalLocales | Where-Object { $_.Value })
        foreach ($loc in $suppliedOptional) {
            Assert-FormatValue -LocaleTag $loc.Tag -Text $loc.Value
        }
        foreach ($loc in ($locales + $suppliedOptional)) {
            $target = Join-Path (Get-LocaleDir $loc.Dir) $File
            if (-not (Test-Path $target)) {
                if (Test-StrictLocale -Tag $loc.Code) { throw "Target file not found: $target" }
                Write-Host "[$($loc.Tag)] skipped - $File does not exist yet" -ForegroundColor Yellow
                continue
            }
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
        if ($Key -and $KeyList) { throw "remove takes -Key or -KeyList, not both." }
        if (-not $Key -and -not $KeyList) { throw "remove requires -Key or -KeyList." }

        if ($Key) {
            # The gate runs first: removal is one-way across every locale on disk, so a reference
            # discovered afterwards is a build (or runtime) break with nothing left to undo it from.
            # @() at the call site, not only inside the function: PowerShell unrolls a single-element
            # array on return, and under StrictMode the resulting bare string has no .Count.
            $refs = @(Get-KeyReferences $Key)
            Show-KeyReferences $Key $refs 'each of these breaks if the key is removed'
            if ($refs.Count -gt 0 -and -not $Force) {
                Write-Error "remove refused: '$Key' is still referenced under $Module/src ($($refs.Count) hit(s)). Update those call sites first, or pass -Force." -ErrorAction Continue
                exit 3
            }
            if (-not (Remove-KeyFromLocales -Key $Key)) {
                Write-Host "Key '$Key' not found in any locale." -ForegroundColor Yellow
            }
            exit 0
        }

        # ----- batch -----
        # One reference index for the whole list, not one tree walk per key. The per-key scan reads all
        # 3892 source files, so a 397-key list would walk the tree 397 times while holding CODE.LOCK -
        # which is what makes running this in parallel with a live translation ticket possible (S1568).
        if (-not (Test-Path -LiteralPath $KeyList)) { throw "Key list not found: $KeyList" }
        $keys = @(
            [System.IO.File]::ReadAllLines($KeyList) |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -and -not $_.StartsWith('#') } |
            ForEach-Object { ($_ -split '\s+')[0] }
        )
        if ($keys.Count -eq 0) { throw "Key list is empty: $KeyList" }

        $srcRoot = Join-Path $repoRoot (Join-Path $Module 'src')
        Write-Host "Building reference index over $Module/src .." -ForegroundColor DarkGray
        $referenced = Get-ReferencedResourceNames -SrcRoot $srcRoot
        Write-Host "Indexed $($referenced.Count) referenced name(s); processing $($keys.Count) key(s)." -ForegroundColor DarkGray

        $removedCount = 0
        $refusedCount = 0
        $absentCount = 0
        foreach ($k in $keys) {
            if ($referenced.Contains($k) -and -not $Force) {
                # Locate only the refusals: the walk is per key, and a clean batch has none.
                $hits = @(Get-KeyReferences $k)
                Show-KeyReferences $k $hits 'refused - each of these breaks if the key is removed'
                $refusedCount++
                continue
            }
            if (Remove-KeyFromLocales -Key $k) { $removedCount++ } else { $absentCount++ }
        }

        $verb = if ($DryRun) { 'would remove' } else { 'removed' }
        Write-Host ''
        Write-Host "batch remove: $verb=$removedCount refused=$refusedCount absent=$absentCount of $($keys.Count) key(s)." -ForegroundColor Cyan

        # Exit 3 on ANY refusal: a partially refused batch that exited 0 would read as a clean run, and
        # the caller would move on believing the list was fully applied.
        if ($refusedCount -gt 0) {
            Write-Error "batch remove: $refusedCount key(s) refused - still referenced under $Module/src." -ErrorAction Continue
            exit 3
        }
        exit 0
    }

    'rename' {
        if (-not $Key) { throw "rename requires -Key." }
        if (-not $NewKey) { throw "rename requires -NewKey." }
        Test-KeySyntax $NewKey
        foreach ($loc in $allLocales) {
            $clash = Find-Key (Get-LocaleDir $loc.Dir) $NewKey
            if ($clash) { throw "Target key '$NewKey' already exists in [$($loc.Tag)] $($clash.File.Name) - aborting." }
        }
        # All three kinds, for the same reason as remove: a <plurals> or <string-array> rename that
        # silently matched nothing left the old name in place and reported success.
        $esc = [regex]::Escape($Key)
        $rx = "(<(?:string-array|plurals|string)\b[^>]*\bname\s*=\s*`")$esc(`")"

        # Advisory, not a gate: renaming a referenced key is the normal case, so a refusal here would break
        # the ordinary workflow. Printed before the mutation so the list is actionable rather than forensic.
        Show-KeyReferences $Key @(Get-KeyReferences $Key) "rewrite each to '$NewKey' - rename does not touch them"

        $renamed = $false
        # Every locale on disk, for the same reason as remove - a half-renamed key is a missing string.
        foreach ($loc in $allLocales) {
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
        exit 0
    }
}
