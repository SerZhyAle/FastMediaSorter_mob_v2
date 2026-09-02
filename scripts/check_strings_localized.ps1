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

    Source sets (S1280): one run audits one source set. Flavor-owned strings live outside src/main,
    so auditing main while the edit landed in src/vr would pass over files the change never touched.
    post-change.ps1 derives -SourceSet from the edited path; a manual invocation must pass it, or use
    -AllSourceSets to sweep every source set that owns a strings*.xml.

.PARAMETER KeyPrefix
    Prefix to filter string keys (e.g. "local_network_permission").
    Supports wildcards: "*network*". Omit it to audit every key.

.PARAMETER Module
    Android module to search (default: app_v2).

.PARAMETER SourceSet
    Android source set under <module>/src/ to search (default: main).
    Use flavor-specific values such as noLegal when auditing non-main resources.
    Ignored when -AllSourceSets is given.

.PARAMETER AllSourceSets
    Audit every source set under <module>/src/ that owns at least one strings*.xml, instead of a
    single one. With a -KeyPrefix, a source set that matches nothing is reported and skipped - only
    a prefix matching nothing ANYWHERE is an error.

.EXAMPLE
    pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "local_network_permission"
    pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "*permission*"
    pwsh -File scripts/check_strings_localized.ps1                       # every key in src/main
    pwsh -File scripts/check_strings_localized.ps1 -AllSourceSets        # every key, every source set
    pwsh -File scripts/check_strings_localized.ps1 -Module app_v2 -SourceSet noLegal -KeyPrefix "s0183_"

.OUTPUTS
    Exit codes:
      0 - every key present in en/ru/uk (gaps in the other declared locales are reported only).
      1 - at least one key missing from en, ru or uk, or the resource directory does not exist.
      3 - an explicitly given -KeyPrefix matched no keys in any audited source set. Silent success
          here used to hide both a mistyped prefix and a wrong source set (S1280). Without a prefix
          an empty result is a legitimate state, not an error - see the note at the exit branch.
#>
param(
    [string]$KeyPrefix,
    [string]$Module = "app_v2",
    [string]$SourceSet = "main",
    [switch]$AllSourceSets
)

. (Join-Path $PSScriptRoot 'utils/locale-set.ps1')

$repoRoot = Join-Path $PSScriptRoot '..'
$strictTags = Get-StrictLocales

# No prefix means "audit everything" - a gate that silently checked nothing would read as a pass.
$pattern = if (-not $KeyPrefix) { '*' } elseif ($KeyPrefix -match '\*') { $KeyPrefix } else { "$KeyPrefix*" }

function Get-StringOwningSourceSets {
    param([Parameter(Mandatory)][string]$ModuleName)

    $srcRoot = Join-Path $repoRoot "$ModuleName\src"
    if (-not (Test-Path $srcRoot)) { return @() }

    @(Get-ChildItem -Path $srcRoot -Directory | Where-Object {
            $res = Join-Path $_.FullName 'res'
            (Test-Path $res) -and
            @(Get-ChildItem -Path $res -Recurse -Filter 'strings*.xml' -File -ErrorAction SilentlyContinue).Count -gt 0
        } | ForEach-Object { $_.Name } | Sort-Object)
}

# Returns a result record instead of exiting, so -AllSourceSets can aggregate across source sets.
# Status: OK | FAIL | EMPTY | NORES
function Invoke-SourceSetAudit {
    param([Parameter(Mandatory)][string]$Name)

    $resDir = Join-Path $repoRoot "$Module\src\$Name\res"
    if (-not (Test-Path $resDir)) {
        Write-Error "Resource dir not found: $resDir" -ErrorAction Continue
        return [pscustomobject]@{ SourceSet = $Name; Status = 'NORES'; KeyCount = 0; StrictMissing = 0 }
    }

    $locales = @(
        Get-SupportedLocales -Module $Module | ForEach-Object {
            @{
                Tag    = $_.ToUpperInvariant()
                Code   = $_
                Dir    = (Get-LocaleResourceDir -Tag $_)
                Strict = (Test-StrictLocale -Tag $_)
            }
        } | Where-Object { $_.Strict -or (Test-Path (Join-Path $resDir $_.Dir)) }
    )

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
        Write-Host "[$Name] no keys matching '$pattern' in any strict locale." -ForegroundColor Yellow
        return [pscustomobject]@{ SourceSet = $Name; Status = 'EMPTY'; KeyCount = 0; StrictMissing = 0 }
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
    Write-Host "[$Name] keys matching '$pattern': $($allKeys.Count)" -ForegroundColor Cyan

    if ($strictReport.Count -gt 0) {
        Write-Host ""
        Write-Host "[$Name] STRICT gaps ($($strictTags -join '/')) - these fail the run:" -ForegroundColor Red
        foreach ($line in $strictReport) { Write-Host "  $line" -ForegroundColor Red }
    }

    $optionalTags = @($locales | Where-Object { -not $_.Strict } | ForEach-Object { $_.Tag })
    if ($optionalTags.Count -gt 0) {
        Write-Host ""
        Write-Host "[$Name] best-effort locales (reported, not fatal):" -ForegroundColor Yellow
        foreach ($tag in $optionalTags) {
            $gaps = if ($optionalMissingByTag.ContainsKey($tag)) { $optionalMissingByTag[$tag] } else { 0 }
            Write-Host ("  {0,-10} {1} of {2} key(s) not translated" -f $tag, $gaps, $allKeys.Count)
        }
    }

    Write-Host ""
    if ($strictMissing -gt 0) {
        Write-Host "[$Name] FAIL: $strictMissing key(s) missing from a strict locale ($($strictTags -join '/'))." -ForegroundColor Red
        return [pscustomobject]@{ SourceSet = $Name; Status = 'FAIL'; KeyCount = $allKeys.Count; StrictMissing = $strictMissing }
    }

    Write-Host "[$Name] OK: all $($allKeys.Count) key(s) present in $($strictTags -join '/')." -ForegroundColor Green
    return [pscustomobject]@{ SourceSet = $Name; Status = 'OK'; KeyCount = $allKeys.Count; StrictMissing = 0 }
}

if ($AllSourceSets) {
    $targets = Get-StringOwningSourceSets -ModuleName $Module
    if ($targets.Count -eq 0) {
        Write-Error "No source set under $Module/src owns a strings*.xml." -ErrorAction Continue
        exit 1
    }
    Write-Host "Auditing $($targets.Count) source set(s): $($targets -join ', ')" -ForegroundColor Cyan
}
else {
    $targets = @($SourceSet)
}

$results = @(foreach ($target in $targets) { Invoke-SourceSetAudit -Name $target })

if ($targets.Count -gt 1) {
    Write-Host ""
    Write-Host "Summary:" -ForegroundColor Cyan
    foreach ($r in $results) {
        Write-Host ("  {0,-16} {1,-6} {2} key(s), {3} strict gap(s)" -f $r.SourceSet, $r.Status, $r.KeyCount, $r.StrictMissing)
    }
}

if (@($results | Where-Object { $_.Status -eq 'NORES' }).Count -gt 0) { exit 1 }

$totalStrictMissing = ($results | Measure-Object -Property StrictMissing -Sum).Sum
if ($totalStrictMissing -gt 0) {
    Write-Host ""
    Write-Host "FAIL: $totalStrictMissing key(s) missing from a strict locale ($($strictTags -join '/'))." -ForegroundColor Red
    exit 1
}

# Every target came back empty: the prefix is mistyped, or it lives in a source set not audited.
# Only a NARROWING pattern can be wrong this way. '*' means "audit everything", and an empty result
# under it means the source set owns no translatable strings - which src/debug legitimately does,
# every key there being translatable="false" (S1280). Failing that would turn a correct state into a
# red gate. Test the resolved pattern, not $KeyPrefix: post-change.ps1 passes '*' explicitly.
$emptyEverywhere = @($results | Where-Object { $_.Status -eq 'EMPTY' }).Count -eq $results.Count
if ($emptyEverywhere -and $pattern -ne '*') {
    Write-Error "No keys matching '$pattern' found in any strict locale of: $($targets -join ', '). Check the prefix and the source set." -ErrorAction Continue
    exit 3
}
if ($emptyEverywhere) {
    Write-Host ""
    Write-Host "OK: no translatable strings to check in $($targets -join ', ')." -ForegroundColor Green
    exit 0
}

Write-Host ""
Write-Host "OK: no strict-locale gaps in $($targets -join ', ')." -ForegroundColor Green
exit 0
