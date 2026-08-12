<#
.SYNOPSIS
    S1190: seeds one locale's copy of a strings file from a translation map.

.DESCRIPTION
    Reads an English source file under res/values and writes the same file into the locale's own
    resource directory, keeping the source's element order and element kinds (<string>, <plurals>,
    <string-array>).

    It never invents text. A key the map does not translate is left out of the output rather than
    copied from English. An English copy would look translated to every counting tool while
    defeating Android's resource fallback - and that fallback is the whole reason a partial locale
    is a shipped state rather than a defect (strategic ADR-6).

    Placeholders are checked rather than trusted: a translation whose format-token multiset differs
    from the English one is rejected and omitted, because a dropped or retyped %1$s survives the
    build and crashes at format time in front of the user.

    Escaping matches ConvertTo-XmlText in scripts/utils/set-android-string.ps1 exactly - XML-escape,
    then both &apos; and &quot; back to the backslash form. The two are deliberately not shared: that
    tool keeps its helpers private inside its own body, and hoisting them into a module would rewrite
    a file this ticket does not otherwise touch. Changing escaping in one place means changing it in
    both.

    S1567: a quote or apostrophe survives the build only when a backslash precedes it after XML
    decoding, so &quot; is not a safe encoding - the XML parser decodes it before AAPT2's quoting
    pass and the character is then dropped exactly like a literal one. Measured with aapt2 compile
    plus aapt2 dump apc, build-tools 36.0.0:

        say "hello" now          -> say hello now      quotes dropped
        say \"hello\" now        -> say "hello" now    correct
        say &quot;hello&quot;   -> say hello now      quotes dropped
        say \&quot;hello\&quot; -> say "hello" now    correct
        say \\"hello\\" now      -> say \hello\ now    literal slash, quote dropped
        it\'s here               -> it's here          correct
        it&apos;s here           -> compile error      entity refused
        it\\'s here              -> compile error      double escape refused

    The escaping is therefore idempotent: a map may supply either the bare or the pre-escaped form of
    both characters and gets the same output. That is deliberate rather than tidy. Before S1567 the
    contract was asymmetric and undocumented - a quote had to arrive pre-escaped while an apostrophe
    had to arrive bare, because a pre-escaped apostrophe became \\' and AAPT2 refused the file.

.PARAMETER Module
    Module path relative to repo root. Default app_v2.

.PARAMETER SourceSet
    Gradle source set holding the resources. Default main. Use vr or noLegal to reach a flavor's own
    strings, which live outside src/main/res and are therefore invisible to the default.

.PARAMETER Merge
    Layer the supplied map over what the locale file already holds instead of replacing the file.

    Without it the seeder rewrites the locale file from the map alone, so a key the map omits is
    dropped - which silently deletes an earlier tranche when a file is seeded a second time. With it,
    an entry already in the locale file is carried through verbatim: it is re-emitted exactly as
    written, skipping both the placeholder check and the escaping pass, because it is already shipped
    text and re-escaping it would double every entity in it.

    -KeyPrefix scopes only what the supplied map may contribute. Under -Merge an existing entry
    outside the prefix still survives, which is what makes seeding one prefix at a time safe.

.PARAMETER SourceFile
    Basename of the source file under res/values, e.g. strings_setup.xml.

.PARAMETER Locale
    BCP-47 tag declared in app_v2/src/main/res/xml/locales_config.xml, e.g. de, ar, zh-Hans.
    The default locale (en) is refused - seeding it would overwrite the source.

.PARAMETER MapPath
    JSON map of key -> translation. Omit and pipe the same JSON in on stdin instead.
    Value shapes: string -> <string>; object of quantity -> text -> <plurals>; array -> <string-array>.

.PARAMETER KeyPrefix
    Optional filter - only source keys starting with this prefix are considered. Lets one thematic
    source file carry more than one tranche.

.PARAMETER DumpSource
    Print the eligible keys and their English bodies as a JSON map, then exit without writing.
    Each key comes out in the shape MapPath accepts back for that element kind - <string> as a
    string, <plurals> as an object of quantity -> text, <string-array> as an array - so the dump
    is a translatable map rather than only a listing.
    This is the only supported way to build a translation map: it uses the very regex the seeder
    later matches with, so the map cannot silently under-cover the source. A map assembled by an
    ad-hoc grep does - a multi-line or markup-carrying element slips through and the key is simply
    absent from the tranche with nothing reporting it.

.PARAMETER DryRun
    Report the plan and write nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/seed-locale-tranche.ps1 -SourceFile strings_setup.xml -Locale de -KeyPrefix welcome_ -MapPath temp/S1190/de_welcome.json

.EXAMPLE
    # Second tranche into a file the first one already seeded - without -Merge this would drop the first.
    pwsh -NoProfile -File scripts/utils/seed-locale-tranche.ps1 -SourceFile strings_setup.xml -Locale de -Merge -MapPath temp/S1420/maps/de/strings_setup.json

.EXAMPLE
    # A flavor's own strings, which do not live under src/main/res.
    pwsh -NoProfile -File scripts/utils/seed-locale-tranche.ps1 -SourceSet vr -SourceFile strings.xml -Locale de -Merge -MapPath temp/S1420/maps/de/vr_strings.json

.OUTPUTS
    Exit codes:
      0 - the locale file was written, or planned under -DryRun. An empty map writes an empty
          <resources> block and is a success: it is the honest representation of "nothing translated yet".
      1 - unusable input: source set has no res/values, source file missing, locale not declared or is
          the default locale, or the map is not readable JSON.
      3 - written, but at least one supplied translation was rejected (key absent from the filtered
          source, or placeholder mismatch). The file is valid; the rejected keys are named so the
          caller fixes the map now instead of discovering the gap in a later tranche.
#>
[CmdletBinding()]
param(
    [string]$Module = 'app_v2',
    [string]$SourceSet = 'main',
    [Parameter(Mandatory = $true)][string]$SourceFile,
    [string]$Locale,
    [string]$MapPath,
    [string]$KeyPrefix,
    [switch]$Merge,
    [switch]$DumpSource,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'locale-set.ps1')

$resDir = Join-Path $repoRoot "$Module/src/$SourceSet/res"
if (-not (Test-Path -LiteralPath (Join-Path $resDir 'values'))) {
    Write-Error "seed-locale-tranche: source set '$SourceSet' has no res/values - tried $resDir" -ErrorAction Continue
    exit 1
}
$sourcePath = Join-Path $resDir "values/$SourceFile"
if (-not (Test-Path -LiteralPath $sourcePath)) {
    Write-Error "seed-locale-tranche: source not found: $sourcePath" -ErrorAction Continue
    exit 1
}

# S1586: AAPT2 reads a backslash as an escape introducer, so a literal one is consumed and the
# character never reaches the user - silently, with no build warning. Doubling every backslash is not
# an option: the seeded corpus carries intended \n line breaks, and a blanket pass would put two
# visible characters on screen in place of each. So only a backslash that introduces nothing AAPT2
# knows is doubled. The match takes \\ as one unit, which is what makes the pass idempotent - an
# already-escaped value keeps its pair instead of growing a third slash. Two consequences worth
# stating: a literal backslash directly before n, t or u is indistinguishable from the escape it
# spells and must be written doubled in the source value; and a leading \@ / \? is out of scope,
# because an unescaped @ or ? in that position fails the build loudly rather than losing a character.
$script:AaptEscapeRx = [regex]'\\(u[0-9a-fA-F]{4}|[nt''"\\])?'

function ConvertTo-AaptBackslash([AllowEmptyString()][string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return '' }
    return $script:AaptEscapeRx.Replace($Text, {
            param($m)
            if ($m.Groups[1].Success) { return $m.Value }
            return '\\'
        })
}

# Must stay identical to ConvertTo-XmlText in set-android-string.ps1 - see .DESCRIPTION.
function ConvertTo-XmlText([AllowEmptyString()][string]$Text) {
    $escaped = [System.Security.SecurityElement]::Escape((ConvertTo-AaptBackslash $Text))
    if ($null -eq $escaped) { return '' }
    # The optional leading backslash is what makes this idempotent: a value that already arrived
    # escaped matches too and collapses to the same single-backslash form, instead of growing a
    # second slash that AAPT2 either refuses (apostrophe) or ships literally (quote).
    $escaped = [regex]::Replace($escaped, '\\?&apos;', "\'")
    return [regex]::Replace($escaped, '\\?&quot;', '\"')
}

function Get-FormatSignature([AllowEmptyString()][string]$Text) {
    return (([regex]::Matches($Text, '%(\d+\$)?[a-zA-Z]') | ForEach-Object { $_.Value } | Sort-Object) -join '|')
}

# Android's inline styling tags are markup, not text: escaping them would turn <b>Always</b> into
# visible angle brackets. Everything else stays escaped, so this is an allowlist rather than a hole.
function ConvertTo-ResourceBody([AllowEmptyString()][string]$Text) {
    $escaped = ConvertTo-XmlText $Text
    foreach ($tag in @('b', 'i', 'u', 'small', 'big')) {
        $escaped = $escaped.Replace("&lt;$tag&gt;", "<$tag>").Replace("&lt;/$tag&gt;", "</$tag>")
    }
    return $escaped.Replace('&lt;br/&gt;', '<br/>')
}

$sourceText = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$eol = if ($sourceText.Contains("`r`n")) { "`r`n" } else { "`n" }
$elements = [regex]::Matches($sourceText, '(?s)<(string|plurals|string-array)\s+name="([^"]+)"([^>]*)>(.*?)</\1>')

# Split deliberately: translatability is a property of the resource, the prefix is a property of the
# request. -Merge has to honour the first and ignore the second when deciding what already-shipped
# text survives - see .PARAMETER Merge.
function Test-Translatable([string]$Attrs) {
    return ($Attrs -notmatch 'translatable\s*=\s*"false"')
}

function Test-InPrefix([string]$Name) {
    return (-not $KeyPrefix) -or $Name.StartsWith($KeyPrefix)
}

function Test-Eligible([string]$Name, [string]$Attrs) {
    return (Test-Translatable $Attrs) -and (Test-InPrefix $Name)
}

if ($DumpSource) {
    $dump = [ordered]@{}
    foreach ($element in $elements) {
        if (-not (Test-Eligible $element.Groups[2].Value $element.Groups[3].Value)) { continue }
        $name = $element.Groups[2].Value
        $body = $element.Groups[4].Value
        # The dump has to round-trip: a <plurals> body emitted as one string comes back as a string,
        # and the writer below rejects it because it expects an object keyed by quantity. Same for
        # <string-array> and its array. Emitting the writer's own shapes is what makes -DumpSource
        # "the only supported way to build a map" true for all three element kinds (S1420).
        switch ($element.Groups[1].Value) {
            'plurals' {
                $quantities = [ordered]@{}
                foreach ($item in [regex]::Matches($body, '(?s)<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>')) {
                    $quantities[$item.Groups[1].Value] = $item.Groups[2].Value
                }
                $dump[$name] = $quantities
            }
            'string-array' {
                $items = [System.Collections.Generic.List[string]]::new()
                foreach ($item in [regex]::Matches($body, '(?s)<item[^>]*>(.*?)</item>')) {
                    $items.Add($item.Groups[1].Value)
                }
                $dump[$name] = $items.ToArray()
            }
            default { $dump[$name] = $body }
        }
    }
    Write-Output ($dump | ConvertTo-Json -Depth 4)
    exit 0
}

if (-not $Locale) {
    Write-Error "seed-locale-tranche: -Locale is required unless -DumpSource is used." -ErrorAction Continue
    exit 1
}
$localeDir = $null
try { $localeDir = Get-LocaleResourceDir -Tag $Locale } catch { $localeDir = $null }
if (-not $localeDir -or $localeDir -eq 'values') {
    Write-Error "seed-locale-tranche: '$Locale' is not a declared locale, or is the default one." -ErrorAction Continue
    exit 1
}

$rawMap = ''
if ($MapPath) {
    if (-not (Test-Path -LiteralPath $MapPath)) {
        Write-Error "seed-locale-tranche: map not found: $MapPath" -ErrorAction Continue
        exit 1
    }
    $rawMap = Get-Content -LiteralPath $MapPath -Raw -Encoding UTF8
} elseif ([Console]::IsInputRedirected) {
    $rawMap = [Console]::In.ReadToEnd()
}
if ([string]::IsNullOrWhiteSpace($rawMap)) { $rawMap = '{}' }
$map = $null
try { $map = $rawMap | ConvertFrom-Json -AsHashtable } catch { $map = $null }
if ($null -eq $map) {
    Write-Error "seed-locale-tranche: map is not readable JSON." -ErrorAction Continue
    exit 1
}

$outPath = Join-Path (Join-Path $resDir $localeDir) $SourceFile

# Under -Merge the locale file's current entries become the base layer. The whole element text is kept,
# not the decoded body: re-rendering shipped text would escape entities that are already escaped.
$carried = @{}
if ($Merge -and (Test-Path -LiteralPath $outPath)) {
    $existingText = Get-Content -LiteralPath $outPath -Raw -Encoding UTF8
    foreach ($existing in [regex]::Matches($existingText, '(?s)<(string|plurals|string-array)\s+name="([^"]+)"([^>]*)>(.*?)</\1>')) {
        $carried[$existing.Groups[2].Value] = ($existing.Value -replace "`r`n|`n", $eol)
    }
}

$out = [System.Collections.Generic.List[string]]::new()
$out.Add('<?xml version="1.0" encoding="utf-8"?>')
$out.Add("<!-- Generated by scripts/utils/seed-locale-tranche.ps1 from values/$SourceFile.")
$out.Add('     Untranslated keys are absent on purpose - Android falls back to English (S1190, ADR-6). -->')
$out.Add('<resources>')

$rejected = [System.Collections.Generic.List[string]]::new()
$eligible = [System.Collections.Generic.HashSet[string]]::new()
$written = 0

# A rejected replacement must not also delete the translation the locale already shipped. The run still
# fails with exit 3 so the caller fixes the map, but the file it leaves behind is no worse than before.
function Add-CarriedFallback([string]$Name) {
    if (-not $carried.ContainsKey($Name)) { return }
    $script:out.Add('    ' + $carried[$Name])
    $script:written++
}

foreach ($element in $elements) {
    $kind = $element.Groups[1].Value
    $name = $element.Groups[2].Value
    $attrs = $element.Groups[3].Value
    $body = $element.Groups[4].Value
    if (-not (Test-Translatable $attrs)) { continue }
    if (Test-InPrefix $name) { [void]$eligible.Add($name) }

    # A key the caller did not supply this run: keep whatever the locale already had, or skip it.
    if (-not ((Test-InPrefix $name) -and $map.ContainsKey($name))) {
        if ($carried.ContainsKey($name)) {
            $out.Add('    ' + $carried[$name])
            $written++
        }
        continue
    }
    $value = $map[$name]

    if ($kind -eq 'string') {
        if ($value -isnot [string]) { [void]$rejected.Add("$name (expected a string for <string>)"); Add-CarriedFallback $name; continue }
        if ((Get-FormatSignature $body) -ne (Get-FormatSignature $value)) {
            [void]$rejected.Add("$name (placeholder mismatch)")
            Add-CarriedFallback $name
            continue
        }
        # S1577: carry the source element's attributes. Dropping them silently stripped
        # formatted="false" from every seeded locale of a key whose body holds literal percent signs,
        # which aapt then read as positional substitutions. translatable="false" cannot reach here -
        # Test-Translatable filtered it out above.
        $out.Add("    <string name=`"$name`"$attrs>$(ConvertTo-ResourceBody $value)</string>")
    } elseif ($kind -eq 'plurals') {
        if ($value -isnot [hashtable]) { [void]$rejected.Add("$name (expected an object for <plurals>)"); Add-CarriedFallback $name; continue }
        $out.Add("    <plurals name=`"$name`"$attrs>")
        foreach ($quantity in $value.Keys) {
            $out.Add("        <item quantity=`"$quantity`">$(ConvertTo-ResourceBody $value[$quantity])</item>")
        }
        $out.Add('    </plurals>')
    } else {
        if ($value -isnot [array]) { [void]$rejected.Add("$name (expected an array for <string-array>)"); Add-CarriedFallback $name; continue }
        $out.Add("    <string-array name=`"$name`"$attrs>")
        foreach ($item in $value) { $out.Add("        <item>$(ConvertTo-ResourceBody $item)</item>") }
        $out.Add('    </string-array>')
    }
    $written++
}

foreach ($key in $map.Keys) {
    if (-not $eligible.Contains($key)) { [void]$rejected.Add("$key (absent from the filtered source)") }
}

$out.Add('</resources>')
Write-Host "seed-locale-tranche: $Locale <- $SourceSet/values/$SourceFile | eligible $($eligible.Count) | written $written | rejected $($rejected.Count)"
foreach ($reason in $rejected) { Write-Host "  rejected: $reason" }

if ($DryRun) {
    Write-Host "seed-locale-tranche: -DryRun, nothing written to $outPath"
} else {
    $outDir = Split-Path -Parent $outPath
    if (-not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
    [System.IO.File]::WriteAllText($outPath, ($out -join $eol) + $eol, [System.Text.UTF8Encoding]::new($false))
    Write-Host "seed-locale-tranche: wrote $outPath"
}

if ($rejected.Count -gt 0) {
    Write-Error "seed-locale-tranche: $($rejected.Count) supplied translation(s) rejected." -ErrorAction Continue
    exit 3
}
exit 0
