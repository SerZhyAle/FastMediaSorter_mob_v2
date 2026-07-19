#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: Android string values with format specifiers must remain syntactically valid and
    locale-consistent.

.DESCRIPTION
    Prevents the class of crashes where a string key exists in all locales but one locale carries an
    invalid `String.format` contract such as `%1``$d`, `%1 $s`, a missing conversion, or a locale
    type mismatch like EN `%1$d` vs RU `%1$s`.

    The scan covers `<string>` nodes across `values*/strings*.xml` for one module/source set. It
    reports three finding classes:
      - invalid local format syntax inside one locale value;
      - duplicate keys inside one locale's split string files;
      - cross-locale contract mismatch for the same key.

    Baseline lives in `scripts/quality/assert-string-format-baseline.txt` as finding ids. New ids
    fail the gate; fewer ids can be ratcheted down with `-UpdateBaseline`.
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline,
    [Parameter(ParameterSetName = 'Report')][switch]$List,
    [string]$Module = 'app_v2',
    [string]$SourceSet = 'main'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/android-string-format.ps1')

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resRoot = Join-Path $repoRoot "$Module/src/$SourceSet/res"
$baselineFile = Join-Path $PSScriptRoot 'assert-string-format-baseline.txt'

$localeDirs = @(
    @{ Locale = 'EN'; Dir = 'values' },
    @{ Locale = 'RU'; Dir = 'values-ru' },
    @{ Locale = 'UK'; Dir = 'values-uk' }
) | Where-Object { Test-Path (Join-Path $resRoot $_.Dir) }

if (-not (Test-Path $resRoot)) {
    Write-Error "Resource dir not found: $resRoot"
    exit 1
}

$entriesByKey = @{}
$findings = [System.Collections.Generic.List[object]]::new()

foreach ($localeDir in $localeDirs) {
    $dirPath = Join-Path $resRoot $localeDir.Dir
    $files = Get-ChildItem -LiteralPath $dirPath -Filter 'strings*.xml' -File -ErrorAction SilentlyContinue | Sort-Object Name
    foreach ($file in $files) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Encoding UTF8
        foreach ($node in @($xml.resources.string)) {
            if ($null -eq $node -or [string]::IsNullOrWhiteSpace($node.name)) { continue }

            $key = [string]$node.name
            $decodedValue = [string]$node.InnerText
            $formattedAttr = $node.Attributes['formatted']
            $isFormattedFalse = ($null -ne $formattedAttr) -and ($formattedAttr.Value -eq 'false')
            $relativeFile = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'

            if (-not $entriesByKey.ContainsKey($key)) {
                $entriesByKey[$key] = @{}
            }

            if ($entriesByKey[$key].ContainsKey($localeDir.Locale)) {
                $findings.Add([pscustomobject]@{
                    Id = ('duplicate|{0}|{1}|{2}' -f $key, $localeDir.Locale, $relativeFile)
                    Key = $key
                    Locale = $localeDir.Locale
                    File = $relativeFile
                    Type = 'duplicate-key'
                    Message = 'Duplicate string key across strings*.xml in the same locale.'
                })
                continue
            }

            $analysis = if ($isFormattedFalse) { Get-AndroidStringFormatAnalysis -Value '' } else { Get-AndroidStringFormatAnalysis -Value $decodedValue }
            $entry = [pscustomobject]@{
                Key = $key
                Locale = $localeDir.Locale
                File = $relativeFile
                Value = $decodedValue
                Analysis = $analysis
            }
            $entriesByKey[$key][$localeDir.Locale] = $entry

            foreach ($formatError in $analysis.Errors) {
                $findings.Add([pscustomobject]@{
                    Id = ('syntax|{0}|{1}|{2}|{3}|{4}' -f $key, $localeDir.Locale, $relativeFile, $formatError.Offset, $formatError.Code)
                    Key = $key
                    Locale = $localeDir.Locale
                    File = $relativeFile
                    Type = 'invalid-format'
                    Message = $formatError.Message
                })
            }
        }
    }
}

foreach ($key in ($entriesByKey.Keys | Sort-Object)) {
    $contractFindings = Compare-AndroidStringFormatContracts -Key $key -EntriesByLocale $entriesByKey[$key]
    foreach ($finding in $contractFindings) {
        $findings.Add($finding)
    }
}

$sortedFindings = @($findings | Sort-Object Id)
$currentIds = @($sortedFindings | ForEach-Object { $_.Id })

if ($List) {
    foreach ($finding in $sortedFindings) {
        Write-Host ("{0} | {1} | {2} | {3}" -f $finding.File, $finding.Locale, $finding.Key, $finding.Message)
    }
    Write-Host ''
}

if ($PSCmdlet.ParameterSetName -eq 'Update') {
    if (-not (Test-Path $baselineFile)) {
        Set-Content -LiteralPath $baselineFile -Value $currentIds
        Write-Host "string-format baseline SEEDED: $($currentIds.Count)"
        exit 0
    }

    $baselineIds = @((Get-Content -LiteralPath $baselineFile -ErrorAction SilentlyContinue) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $additions = @($currentIds | Where-Object { $baselineIds -notcontains $_ })

    if ($additions.Count -gt 0) {
        Write-Error "Refusing to RAISE baseline. New string-format findings were introduced - fix them instead."
        exit 1
    }

    if ($currentIds.Count -lt $baselineIds.Count -or (@($baselineIds | Where-Object { $currentIds -notcontains $_ }).Count -gt 0)) {
        Set-Content -LiteralPath $baselineFile -Value $currentIds
        Write-Host "string-format baseline ratcheted DOWN: $($baselineIds.Count) -> $($currentIds.Count)"
    }
    else {
        Write-Host "string-format baseline unchanged ($($baselineIds.Count))"
    }
    exit 0
}

if (-not (Test-Path $baselineFile)) {
    Write-Host "string-format: NO BASELINE yet | actual $($currentIds.Count) - run -UpdateBaseline to seed."
    exit 0
}

$baselineIds = @((Get-Content -LiteralPath $baselineFile -ErrorAction SilentlyContinue) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$newFindings = @($sortedFindings | Where-Object { $baselineIds -notcontains $_.Id })
$resolvedFindings = @($baselineIds | Where-Object { $currentIds -notcontains $_ })
$delta = $currentIds.Count - $baselineIds.Count

Write-Host ("string-format in {0}/src/{1}: baseline {2} | actual {3} | delta {4:+#;-#;0}" -f $Module, $SourceSet, $baselineIds.Count, $currentIds.Count, $delta)
if ($Gate -and $newFindings.Count -gt 0) {
    foreach ($finding in $newFindings | Select-Object -First 20) {
        Write-Host ("  NEW: {0} | {1} | {2} | {3}" -f $finding.File, $finding.Locale, $finding.Key, $finding.Message)
    }
    Write-Host "FAIL: string format findings grew above baseline. Fix invalid specifiers or locale contract mismatches before merge."
    exit 1
}
if (@($resolvedFindings).Count -gt 0) {
    Write-Host "Note: count is below baseline - run -UpdateBaseline to ratchet the cap down."
}
exit 0
