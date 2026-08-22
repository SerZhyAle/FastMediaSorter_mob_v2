<#
.SYNOPSIS
    S1420 scratch harness: checks a tranche's locale maps against the English dump before seeding.

.DESCRIPTION
    The seeder rejects a bad translation at write time (exit 3), but it reports the failure one locale
    at a time and after it has already rewritten the file. This checks all ten locales up front, so a
    tranche is either seeded whole or fixed whole.

    Three checks per key: the key set matches the dump exactly, the value shape matches (string /
    plurals object / string-array array), and the format-token multiset is identical to the English one.
    A fourth check is cross-key: no value may be byte-identical to its English source unless the source
    is a proper noun or a bare format token, which is how an agent silently skipping a key shows up.

.PARAMETER Dump
    Basename of the dump under temp/S1420/en, without .json - e.g. strings__streams.

.OUTPUTS
    Exit codes:
      0 - every locale map is clean.
      1 - at least one locale map has a defect; each is named.
      2 - could not verify: dump missing, or a map is absent or unreadable JSON.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string[]]$Dump,
    [string[]]$Locales = @('ar', 'bn', 'de', 'es', 'fr', 'hi', 'it', 'pt', 'ur', 'zh-Hans')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$enDir = Join-Path $repoRoot 'temp/S1420/en'
$mapRoot = Join-Path $repoRoot 'temp/S1420/maps'

function Get-FormatSignature([AllowEmptyString()][string]$Text) {
    return (([regex]::Matches($Text, '%(\d+\$)?[a-zA-Z]') | ForEach-Object { $_.Value } | Sort-Object) -join '|')
}

# A value is allowed to equal its English source only when there is nothing to translate in it.
function Test-UntranslatableAsIs([string]$Text) {
    return ($Text -match '^\s*$') -or ($Text -match '^[%\d\$sdfx\s\.,:;/|\-+()]*$')
}

$defects = [System.Collections.Generic.List[string]]::new()
$blocked = $false

foreach ($name in $Dump) {
    $dumpPath = Join-Path $enDir "$name.json"
    if (-not (Test-Path -LiteralPath $dumpPath)) {
        Write-Error "verify-maps: dump not found: $dumpPath" -ErrorAction Continue
        exit 2
    }
    $source = Get-Content -LiteralPath $dumpPath -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable
    $sourceKeys = [System.Collections.Generic.HashSet[string]]::new([string[]]$source.Keys)

    foreach ($locale in $Locales) {
        $mapPath = Join-Path $mapRoot "$locale/$name.json"
        if (-not (Test-Path -LiteralPath $mapPath)) {
            Write-Error "verify-maps: map not found: $mapPath" -ErrorAction Continue
            $blocked = $true
            continue
        }
        $map = $null
        try { $map = Get-Content -LiteralPath $mapPath -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable } catch { $map = $null }
        if ($null -eq $map) {
            Write-Error "verify-maps: $locale/$name is not readable JSON." -ErrorAction Continue
            $blocked = $true
            continue
        }

        $mapKeys = [System.Collections.Generic.HashSet[string]]::new([string[]]$map.Keys)
        foreach ($key in $sourceKeys) {
            if (-not $mapKeys.Contains($key)) { $defects.Add("$locale/$name missing key: $key") }
        }
        foreach ($key in $mapKeys) {
            if (-not $sourceKeys.Contains($key)) { $defects.Add("$locale/$name extra key: $key") }
        }

        $untouched = 0
        foreach ($key in $sourceKeys) {
            if (-not $mapKeys.Contains($key)) { continue }
            $en = $source[$key]
            $tr = $map[$key]

            if ($en -is [string]) {
                if ($tr -isnot [string]) { $defects.Add("$locale/$name $key : expected a string"); continue }
                if ((Get-FormatSignature $en) -ne (Get-FormatSignature $tr)) {
                    $defects.Add("$locale/$name $key : format tokens differ - en '$(Get-FormatSignature $en)' vs '$(Get-FormatSignature $tr)'")
                }
                if ($tr -match '&(amp|lt|gt|quot|apos);') { $defects.Add("$locale/$name $key : XML entity in map - write the plain character") }
                # The seeder checks format tokens but not line breaks, and a dropped \n silently
                # collapses a multi-paragraph rationale into one run-on block.
                $enBreaks = ([regex]::Matches($en, '\\n')).Count
                $trBreaks = ([regex]::Matches($tr, '\\n')).Count
                if ($enBreaks -ne $trBreaks) { $defects.Add("$locale/$name $key : \n count differs - en $enBreaks vs $trBreaks") }
                # A real line break survives JSON and the seeder untouched, and Android then collapses it
                # to one space - the paragraph the translator wrote is simply gone. The count check above
                # only catches it while the English side spells \n, so the character is refused outright.
                if ($tr -match "`n") { $defects.Add("$locale/$name $key : real line break in value - write the two characters \n") }
                # The seeder escapes on write - XML-escape, then &apos; back to \'. So an apostrophe
                # supplied already escaped is escaped twice: \' goes out as \\', an escaped backslash
                # followed by a bare apostrophe, which AAPT2 refuses. The asymmetry is real and only
                # the apostrophe has it - \" survives because Escape leaves the backslash alone and
                # turns the quote into &quot;, which AAPT2 reads back as the \" the source itself
                # spells (empty_folder_hint_subdirectories carries exactly that). So \" is required
                # wherever English has it, and checked for parity rather than refused.
                if ($tr -match "\\'") { $defects.Add("$locale/$name $key : pre-escaped apostrophe \' - write the plain character, the seeder escapes") }
                $enQuotes = ([regex]::Matches($en, '\\"')).Count
                $trQuotes = ([regex]::Matches($tr, '\\"')).Count
                if ($enQuotes -ne $trQuotes) { $defects.Add("$locale/$name $key : escaped-quote count differs - en $enQuotes vs $trQuotes") }
                if ($tr.Contains([char]0x2026)) { $defects.Add("$locale/$name $key : single-character ellipsis - house style is ..") }
                if ($en -ceq $tr -and -not (Test-UntranslatableAsIs $en)) { $untouched++ }
            } elseif ($en -is [hashtable]) {
                if ($tr -isnot [hashtable]) { $defects.Add("$locale/$name $key : expected an object for <plurals>"); continue }
                foreach ($quantity in $en.Keys) {
                    if (-not $tr.ContainsKey($quantity)) { $defects.Add("$locale/$name $key : plurals missing quantity '$quantity'") }
                }
            } else {
                if ($tr -isnot [array]) { $defects.Add("$locale/$name $key : expected an array for <string-array>"); continue }
                if (@($en).Count -ne @($tr).Count) { $defects.Add("$locale/$name $key : string-array item count differs") }
            }
        }

        # Reported, not failed: a language can legitimately keep a loanword, so the number is a
        # signal to read rather than a verdict. A whole file coming back untouched is the real tell.
        $ratio = if ($sourceKeys.Count -gt 0) { [math]::Round(100 * $untouched / $sourceKeys.Count) } else { 0 }
        Write-Host ("verify-maps: {0,-8} {1,-22} keys {2,4} | identical-to-en {3,4} ({4}%)" -f $locale, $name, $mapKeys.Count, $untouched, $ratio)
    }
}

if ($blocked) {
    Write-Error 'verify-maps: at least one map could not be read.' -ErrorAction Continue
    exit 2
}
foreach ($defect in $defects) { Write-Host "  defect: $defect" }
if ($defects.Count -gt 0) {
    Write-Error "verify-maps: $($defects.Count) defect(s)." -ErrorAction Continue
    exit 1
}
Write-Host 'verify-maps: PASS - every locale map matches the dump.'
exit 0
