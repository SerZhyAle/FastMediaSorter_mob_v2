#requires -Version 7.0
<#
.SYNOPSIS
    S2125: fails when a string mirrored on the phone and the watch stops reading the same.

.DESCRIPTION
    :app_v2 and :wear compile separately with no shared resource artifact, so every label the owner
    sees on both sides physically exists twice - one key per module. S2093 introduced
    assert-wear-settings-parity.ps1, which checks that a watch setting EXISTS on both sides, but it
    never reads the text of the label. Editing one copy therefore left no trace on the other, and the
    divergence surfaced only on a live phone-plus-watch pair.

    The pairing is declared in wear-mirrored-strings.psd1 beside this script, never inferred from a
    matching key name. Measured 2026-08-27: 20 key names occur in both modules, 6 already differ in
    `en` and 8 more diverge in 12 places across bn/pt/ru/uk, several of them deliberately - the watch
    picks the shorter word where the round screen demands it.

    Checks:
      1. A Mirrored pair reads identically in every locale both sides declare it in.
      2. A Mirrored pair is declared by both sides in the same set of locales.
      3. An Independent record carries a non-empty Reason.
      4. A key name present in both modules' values/strings.xml is classified by the declaration.
         Without this the list ages in silence, which is the disease the ticket treats.

    Check 4 is what keeps the declaration honest: a new colliding key must be called Mirrored or
    Independent by the author who added it, because that intent cannot be recovered later.

    Rule 33 class, stated at birth: PER-TICKET, summoned conditionally. The evidence exists only at
    the moment of the change - only the author knows whether a new shared key was meant to read the
    same - which is the same class as the S2093 gate beside it. post-change.ps1 runs it only when the
    changed set carries a strings.xml under either module, so a session that touches no strings is
    never judged by it.

.NOTES
    Exit codes:
      0 - every declared pair is in step; or a divergence was reported without -Gate, matching the
          advisory shape of the sibling gates in assert-fast-gates.ps1.
      1 - a divergence was found and -Gate was passed.
      2 - could not verify: the declaration is missing, parses to zero pairs, or a strings.xml under
          a discovered locale could not be read as XML. A caller must tell this from 1 - "found a
          defect" and "did not look" are different answers.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$declarationPath = Join-Path $PSScriptRoot 'wear-mirrored-strings.psd1'
$phoneResRoot = Join-Path $root 'app_v2/src/main/res'
$watchResRoot = Join-Path $root 'wear/src/main/res'

foreach ($required in @($declarationPath, $phoneResRoot, $watchResRoot)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Error "assert-wear-mirrored-strings: could not verify - missing $required" -ErrorAction Continue
        exit 2
    }
}

try {
    $declaration = Import-PowerShellDataFile -LiteralPath $declarationPath
}
catch {
    Write-Error "assert-wear-mirrored-strings: could not verify - declaration unreadable: $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

if (-not $declaration.Contains('Pairs') -or @($declaration.Pairs).Count -eq 0) {
    Write-Error 'assert-wear-mirrored-strings: could not verify - declaration parsed to zero pairs.' -ErrorAction Continue
    exit 2
}
$pairs = @($declaration.Pairs)

# Read one strings.xml into name -> text. InnerXml rather than InnerText, so an entity or an inline
# tag that differs between the two sides is a difference rather than being normalised away.
function Read-StringTable {
    param([string]$Path)

    $table = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $table }

    try {
        $xml = [xml](Get-Content -LiteralPath $Path -Raw)
    }
    catch {
        Write-Error "assert-wear-mirrored-strings: could not verify - $Path is not readable XML: $($_.Exception.Message)" -ErrorAction Continue
        exit 2
    }

    if ($null -eq $xml.resources) { return $table }
    foreach ($node in $xml.resources.string) {
        if ($null -ne $node -and $node.name) { $table[$node.name] = $node.InnerXml }
    }
    return $table
}

# The locale set is discovered from the tree rather than pinned, so a fourteenth locale is picked up
# without editing this gate. Only a folder carrying strings.xml on at least one side is considered.
$localeNames = @(
    @(Get-ChildItem -LiteralPath $phoneResRoot -Directory -Filter 'values*') +
    @(Get-ChildItem -LiteralPath $watchResRoot -Directory -Filter 'values*')
) |
    Where-Object {
        (Test-Path -LiteralPath (Join-Path $phoneResRoot "$($_.Name)/strings.xml")) -or
        (Test-Path -LiteralPath (Join-Path $watchResRoot "$($_.Name)/strings.xml"))
    } |
    ForEach-Object { $_.Name } |
    Sort-Object -Unique

if ($localeNames.Count -eq 0) {
    Write-Error 'assert-wear-mirrored-strings: could not verify - no values*/strings.xml found in either module.' -ErrorAction Continue
    exit 2
}

$phoneByLocale = [ordered]@{}
$watchByLocale = [ordered]@{}
foreach ($locale in $localeNames) {
    $phoneByLocale[$locale] = Read-StringTable (Join-Path $phoneResRoot "$locale/strings.xml")
    $watchByLocale[$locale] = Read-StringTable (Join-Path $watchResRoot "$locale/strings.xml")
}

$findings = @()
$comparisons = 0
$mirroredCount = 0

foreach ($pair in $pairs) {
    $phoneKey = [string]$pair.Phone
    $watchKey = [string]$pair.Watch
    $mode = [string]$pair.Mode
    $reason = if ($pair.Contains('Reason')) { [string]$pair.Reason } else { '' }
    $label = if ($phoneKey -eq $watchKey) { $phoneKey } else { "$phoneKey / $watchKey" }

    if ($mode -eq 'Independent') {
        # 3. An exception without a recorded reason is indistinguishable from a forgotten string -
        #    the same rule the S2093 registry applies to a one-sided setting.
        if ([string]::IsNullOrWhiteSpace($reason)) {
            $findings += "S2125: '$label' is Independent with no Reason - record why the two sides word it differently, or make it Mirrored."
        }
        continue
    }

    if ($mode -ne 'Mirrored') {
        $findings += "S2125: '$label' declares Mode '$mode' - only 'Mirrored' and 'Independent' exist."
        continue
    }

    $mirroredCount++

    foreach ($locale in $localeNames) {
        $hasPhone = $phoneByLocale[$locale].ContainsKey($phoneKey)
        $hasWatch = $watchByLocale[$locale].ContainsKey($watchKey)

        if ($hasPhone -and $hasWatch) {
            # 1. The pair must read the same.
            $comparisons++
            if ($phoneByLocale[$locale][$phoneKey] -ne $watchByLocale[$locale][$watchKey]) {
                $findings += ("S2125: '$label' differs in $locale - phone '" +
                    $phoneByLocale[$locale][$phoneKey] + "' vs watch '" +
                    $watchByLocale[$locale][$watchKey] + "'.")
            }
        }
        elseif ($hasPhone -or $hasWatch) {
            # 2. A mirrored pair translated on one side only is the divergence arriving early.
            $missingSide = if ($hasPhone) { 'watch' } else { 'phone' }
            $presentSide = if ($hasPhone) { 'phone' } else { 'watch' }
            $findings += "S2125: '$label' is declared in $locale on the $presentSide side only - the $missingSide side has no such key."
        }
    }
}

# 4. Completeness, judged from the other direction: a key name both modules declare and this file
#    classifies neither way. Only the default locale is swept - a key reaches a translation because
#    it exists in `values` first, so that is where a new collision appears.
$declaredPhoneKeys = @($pairs | ForEach-Object { [string]$_.Phone })
$declaredWatchKeys = @($pairs | ForEach-Object { [string]$_.Watch })
$phoneDefault = $phoneByLocale['values']
$watchDefault = $watchByLocale['values']

foreach ($key in ($phoneDefault.Keys | Sort-Object)) {
    if (-not $watchDefault.ContainsKey($key)) { continue }
    if ($key -in $declaredPhoneKeys -and $key -in $declaredWatchKeys) { continue }
    $findings += ("S2125: '$key' exists in both modules but wear-mirrored-strings.psd1 classifies it " +
        "neither Mirrored nor Independent - declare which it is.")
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host ("assert-wear-mirrored-strings: PASS - $mirroredCount mirrored pair(s), " +
            "$comparisons text comparison(s) across $($localeNames.Count) locale(s), no divergence.")
    }
    exit 0
}

Write-Error ("assert-wear-mirrored-strings: FAIL - " + $findings.Count + " divergence(s):`n" +
    (($findings | Sort-Object -Unique) -join "`n")) -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
