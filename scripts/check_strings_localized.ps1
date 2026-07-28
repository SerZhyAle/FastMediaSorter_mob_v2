<#
.SYNOPSIS
    Checks that string keys exist in every declared locale, at two levels of strictness.

.DESCRIPTION
    The locale set comes from app_v2/src/main/res/xml/locales_config.xml through
    scripts/utils/locale-set.ps1 - this script does not restate it (S1190).

    Two levels, per strategic ADR-6:
      - STRICT (en, ru, uk): owner-authored. A key missing here is an ERROR and fails the run.
      - OTHER declared locales: machine-assisted. A missing key is counted and reported, never fatal.
        A locale whose values-XX directory does not exist yet is skipped entirely, so declaring a
        language before translating it does not turn the gate red.

    Strings marked translatable="false" are intentionally single-locale (e-mail addresses, URLs) and
    are never reported as missing.

.PARAMETER KeyPrefix
    Prefix to filter string keys (e.g. "local_network_permission").
    Supports wildcards: "*network*". Omit it to audit every key.

.PARAMETER Module
    Android module to search (default: app_v2).

.PARAMETER SourceSet
    Android source set under <module>/src/ to search (default: main).
    Use flavor-specific values such as noLegal when auditing non-main resources.

.EXAMPLE
    pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "local_network_permission"
    pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "*permission*"
    pwsh -File scripts/check_strings_localized.ps1                       # every key
    pwsh -File scripts/check_strings_localized.ps1 -Module app_v2 -SourceSet noLegal -KeyPrefix "s0183_"

.OUTPUTS
    Exit codes:
      0 - every key present in en/ru/uk (gaps in the other declared locales are reported only).
      1 - at least one key missing from en, ru or uk, or the resource directory does not exist.
#>
param(
    [string]$KeyPrefix,
    [string]$Module = "app_v2",
    [string]$SourceSet = "main"
)

. (Join-Path $PSScriptRoot 'utils/locale-set.ps1')

$resDir = "$PSScriptRoot\..\$Module\src\$SourceSet\res"

if (-not (Test-Path $resDir)) {
    Write-Error "Resource dir not found: $resDir" -ErrorAction Continue
    exit 1
}

$strictTags = Get-StrictLocales
$locales = @(
    Get-SupportedLocales | ForEach-Object {
        @{
            Tag    = $_.ToUpperInvariant()
            Code   = $_
            Dir    = (Get-LocaleResourceDir -Tag $_)
            Strict = (Test-StrictLocale -Tag $_)
        }
    } | Where-Object { $_.Strict -or (Test-Path (Join-Path $resDir $_.Dir)) }
)

# No prefix means "audit everything" - a gate that silently checked nothing would read as a pass.
$pattern = if (-not $KeyPrefix) { '*' } elseif ($KeyPrefix -match '\*') { $KeyPrefix } else { "$KeyPrefix*" }

$byLocale = @{}
foreach ($loc in $locales) {
    $dir = Join-Path $resDir $loc.Dir
    if (-not (Test-Path $dir)) {
        $byLocale[$loc.Tag] = @()
        Write-Warning "  [$($loc.Tag)] locale dir not found: $dir"
        continue
    }

    $files = Get-ChildItem -Path $dir -Filter "strings*.xml" -File | Sort-Object Name
    if ($files.Count -eq 0) {
        $byLocale[$loc.Tag] = @()
        Write-Warning "  [$($loc.Tag)] no strings*.xml files found in: $dir"
        continue
    }

    $keys = foreach ($file in $files) {
        [xml]$xml = Get-Content $file.FullName -Encoding UTF8
        $xml.resources.string |
        Where-Object { $_.name -like $pattern -and $_.translatable -ne "false" } |
        ForEach-Object { $_.name }
    }
    $byLocale[$loc.Tag] = @($keys)
}

# The key universe is what the strict locales declare: a key existing only in a translated locale is
# an orphan, and reporting it as "missing from EN" would invert the direction of the defect.
$strictLocales = @($locales | Where-Object { $_.Strict })
$allKeys = @($strictLocales | ForEach-Object { $byLocale[$_.Tag] }) | Sort-Object -Unique

if ($allKeys.Count -eq 0) {
    Write-Host "No keys matching '$pattern' found in any strict locale." -ForegroundColor Yellow
    exit 0
}

$strictMissing = 0
$optionalMissingByTag = @{}
$strictReport = New-Object System.Collections.Generic.List[string]

foreach ($key in $allKeys) {
    $missingStrict = @($strictLocales |
        Where-Object { $byLocale[$_.Tag] -notcontains $key } |
        ForEach-Object { $_.Tag })
    if ($missingStrict.Count -gt 0) {
        $strictMissing++
        $strictReport.Add(("{0,-56}  missing in {1}" -f $key, ($missingStrict -join ', ')))
    }
    foreach ($loc in $locales) {
        if ($loc.Strict) { continue }
        if ($byLocale[$loc.Tag] -notcontains $key) {
            $seen = if ($optionalMissingByTag.ContainsKey($loc.Tag)) { $optionalMissingByTag[$loc.Tag] } else { 0 }
            $optionalMissingByTag[$loc.Tag] = $seen + 1
        }
    }
}

Write-Host ""
Write-Host "Keys matching '$pattern': $($allKeys.Count)" -ForegroundColor Cyan

if ($strictReport.Count -gt 0) {
    Write-Host ""
    Write-Host "STRICT gaps ($($strictTags -join '/')) - these fail the run:" -ForegroundColor Red
    foreach ($line in $strictReport) { Write-Host "  $line" -ForegroundColor Red }
}

$optionalTags = @($locales | Where-Object { -not $_.Strict } | ForEach-Object { $_.Tag })
if ($optionalTags.Count -gt 0) {
    Write-Host ""
    Write-Host "Best-effort locales (reported, not fatal):" -ForegroundColor Yellow
    foreach ($tag in $optionalTags) {
        $gaps = if ($optionalMissingByTag.ContainsKey($tag)) { $optionalMissingByTag[$tag] } else { 0 }
        Write-Host ("  {0,-10} {1} of {2} key(s) not translated" -f $tag, $gaps, $allKeys.Count)
    }
}

Write-Host ""
if ($strictMissing -gt 0) {
    Write-Host "FAIL: $strictMissing key(s) missing from a strict locale ($($strictTags -join '/'))." -ForegroundColor Red
    exit 1
}

Write-Host "OK: all $($allKeys.Count) key(s) present in $($strictTags -join '/')." -ForegroundColor Green
exit 0
