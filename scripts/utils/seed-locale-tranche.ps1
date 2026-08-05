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
    then &apos; back to \'. The two are deliberately not shared: that tool keeps its helpers private
    inside its own body, and hoisting them into a module would rewrite a file this ticket does not
    otherwise touch. Changing escaping in one place means changing it in both.

.PARAMETER Module
    Module path relative to repo root. Default app_v2.

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
    This is the only supported way to build a translation map: it uses the very regex the seeder
    later matches with, so the map cannot silently under-cover the source. A map assembled by an
    ad-hoc grep does - a multi-line or markup-carrying element slips through and the key is simply
    absent from the tranche with nothing reporting it.

.PARAMETER DryRun
    Report the plan and write nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/seed-locale-tranche.ps1 -SourceFile strings_setup.xml -Locale de -KeyPrefix welcome_ -MapPath temp/S1190/de_welcome.json

.OUTPUTS
    Exit codes:
      0 - the locale file was written, or planned under -DryRun. An empty map writes an empty
          <resources> block and is a success: it is the honest representation of "nothing translated yet".
      1 - unusable input: source file missing, locale not declared or is the default locale, or the
          map is not readable JSON.
      3 - written, but at least one supplied translation was rejected (key absent from the filtered
          source, or placeholder mismatch). The file is valid; the rejected keys are named so the
          caller fixes the map now instead of discovering the gap in a later tranche.
#>
[CmdletBinding()]
param(
    [string]$Module = 'app_v2',
    [Parameter(Mandatory = $true)][string]$SourceFile,
    [string]$Locale,
    [string]$MapPath,
    [string]$KeyPrefix,
    [switch]$DumpSource,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'locale-set.ps1')

$resDir = Join-Path $repoRoot "$Module/src/main/res"
$sourcePath = Join-Path $resDir "values/$SourceFile"
if (-not (Test-Path -LiteralPath $sourcePath)) {
    Write-Error "seed-locale-tranche: source not found: $sourcePath" -ErrorAction Continue
    exit 1
}

# Must stay identical to ConvertTo-XmlText in set-android-string.ps1 - see .DESCRIPTION.
function ConvertTo-XmlText([AllowEmptyString()][string]$Text) {
    $escaped = [System.Security.SecurityElement]::Escape($Text)
    if ($null -eq $escaped) { return '' }
    return $escaped.Replace('&apos;', "\'")
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

function Test-Eligible([string]$Name, [string]$Attrs) {
    if ($Attrs -match 'translatable\s*=\s*"false"') { return $false }
    if ($KeyPrefix -and -not $Name.StartsWith($KeyPrefix)) { return $false }
    return $true
}

if ($DumpSource) {
    $dump = [ordered]@{}
    foreach ($element in $elements) {
        if (-not (Test-Eligible $element.Groups[2].Value $element.Groups[3].Value)) { continue }
        $dump[$element.Groups[2].Value] = $element.Groups[4].Value
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

$out = [System.Collections.Generic.List[string]]::new()
$out.Add('<?xml version="1.0" encoding="utf-8"?>')
$out.Add("<!-- Generated by scripts/utils/seed-locale-tranche.ps1 from values/$SourceFile.")
$out.Add('     Untranslated keys are absent on purpose - Android falls back to English (S1190, ADR-6). -->')
$out.Add('<resources>')

$rejected = [System.Collections.Generic.List[string]]::new()
$eligible = [System.Collections.Generic.HashSet[string]]::new()
$written = 0

foreach ($element in $elements) {
    $kind = $element.Groups[1].Value
    $name = $element.Groups[2].Value
    $attrs = $element.Groups[3].Value
    $body = $element.Groups[4].Value
    if (-not (Test-Eligible $name $attrs)) { continue }
    [void]$eligible.Add($name)
    if (-not $map.ContainsKey($name)) { continue }
    $value = $map[$name]

    if ($kind -eq 'string') {
        if ($value -isnot [string]) { [void]$rejected.Add("$name (expected a string for <string>)"); continue }
        if ((Get-FormatSignature $body) -ne (Get-FormatSignature $value)) {
            [void]$rejected.Add("$name (placeholder mismatch)")
            continue
        }
        $out.Add("    <string name=`"$name`">$(ConvertTo-ResourceBody $value)</string>")
    } elseif ($kind -eq 'plurals') {
        if ($value -isnot [hashtable]) { [void]$rejected.Add("$name (expected an object for <plurals>)"); continue }
        $out.Add("    <plurals name=`"$name`">")
        foreach ($quantity in $value.Keys) {
            $out.Add("        <item quantity=`"$quantity`">$(ConvertTo-ResourceBody $value[$quantity])</item>")
        }
        $out.Add('    </plurals>')
    } else {
        if ($value -isnot [array]) { [void]$rejected.Add("$name (expected an array for <string-array>)"); continue }
        $out.Add("    <string-array name=`"$name`">")
        foreach ($item in $value) { $out.Add("        <item>$(ConvertTo-ResourceBody $item)</item>") }
        $out.Add('    </string-array>')
    }
    $written++
}

foreach ($key in $map.Keys) {
    if (-not $eligible.Contains($key)) { [void]$rejected.Add("$key (absent from the filtered source)") }
}

$out.Add('</resources>')
$outPath = Join-Path (Join-Path $resDir $localeDir) $SourceFile
Write-Host "seed-locale-tranche: $Locale <- values/$SourceFile | eligible $($eligible.Count) | written $written | rejected $($rejected.Count)"
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
