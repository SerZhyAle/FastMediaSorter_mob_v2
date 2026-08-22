<#
.SYNOPSIS
    Validate the ALL_FEATURES inventory (docs/ALL_FEATURES.jsonl) against the schema rules.

.DESCRIPTION
    Parses each JSONL line, checks required fields, flavor enum membership,
    id pattern + uniqueness, spec pattern, status enum, and EN-only ASCII for
    name/description. Prints a per-error report and exits non-zero on any
    violation; exits 0 when clean. An empty file is valid.

    -NoLegal validates docs/ALL_FEATURES_noLegal.jsonl instead.
    -Gate prints nothing on success (exit-code-only) for post-change wiring.

.EXAMPLE
    .\scripts\all_features\validate.ps1
.EXAMPLE
    .\scripts\all_features\validate.ps1 -Gate

.NOTES
    Exit codes:
      0 inventory valid, and the S1934 ungated-flavors ratchet is at or below its baseline.
      1 a schema rule was violated, the ratchet grew past its baseline, or the baseline is unreadable.
#>
param(
    [switch]$NoLegal,
    [switch]$Gate,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
trap { Write-Error $_; exit 1 }

$validFlavors = @("standard", "lite", "photos", "legacy", "vr", "noLegal")
$validStatus = @("active", "removed")
$required = @("id", "area", "name", "description", "flavors")

# S1934: the full flavor set. A capability nothing gates cannot be narrower than the app itself,
# so this set - or the row of some matrix flag - is the only shape an ungated record may claim.
$allFlavors = @("standard", "lite", "photos", "legacy", "vr", "noLegal")

function Test-NonAscii([string]$s) {
    foreach ($ch in $s.ToCharArray()) { if ([int][char]$ch -gt 127) { return $true } }
    return $false
}

function Get-FlavorMatrixFlags {
    <#
    .SYNOPSIS
        flag name -> the flavors it is ON in, read from the generated flavor matrix (S1929).
    .DESCRIPTION
        docs/FLAVOR_MATRIX.md is generated from the productFlavors block and is the only permitted
        source for this grid: CLAUDE.md forbids restating it from memory after S1392, where a
        summary in a prompt claimed `lite` had no audio and four documents followed it.

        The column order is read from the table's own header rather than assumed, so the parser
        does not silently transpose if a flavor is ever added or reordered.

        A cell is ON when it carries `[+]`. The trailing asterisk in `[+]*` / `[-]*` means the
        value was inherited from defaultConfig rather than declared by the flavor (see the file's
        own legend) - that is a fact about where the value came from, not about what it is.

        Returns an empty hashtable when the matrix cannot be read; the caller decides what that
        means rather than having a guess made for it here.
    #>
    param([Parameter(Mandatory)][string]$MatrixPath)

    $flags = @{}
    if (-not (Test-Path -LiteralPath $MatrixPath)) { return $flags }

    $columns = @()
    foreach ($line in (Get-Content -LiteralPath $MatrixPath -Encoding UTF8)) {
        if ($line -notmatch '^\s*\|') { continue }
        $cells = @($line.Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() })
        if ($cells.Count -lt 2) { continue }

        if ($columns.Count -eq 0) {
            # The header is the first table row whose leading cell is the flag column's title.
            if ($cells[0] -eq 'Flag') { $columns = @($cells[1..($cells.Count - 1)]) }
            continue
        }
        if ($cells[0] -match '^:?-{2,}') { continue }   # the alignment row

        $name = $cells[0].Trim('`')
        if ($name -notmatch '^[A-Z][A-Z0-9_]*$') { continue }

        $on = @()
        for ($i = 0; $i -lt $columns.Count -and ($i + 1) -lt $cells.Count; $i++) {
            if ($cells[$i + 1] -like '*[[]+]*') { $on += $columns[$i] }
        }
        $flags[$name] = $on
    }
    return $flags
}

# Resolve repo root
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
if (-not $repoRoot -or -not (Test-Path (Join-Path $repoRoot "settings.gradle.kts"))) {
    $repoRoot = (Get-Location).Path
}
$fileName = if ($NoLegal) { "ALL_FEATURES_noLegal.jsonl" } else { "ALL_FEATURES.jsonl" }
$dataFile = Join-Path (Join-Path $repoRoot "docs") $fileName

$errors = New-Object System.Collections.Generic.List[string]
$unexplained = New-Object System.Collections.Generic.List[string]
$seenIds = @{}
$count = 0

# Read once for the whole file rather than per record - the matrix does not change mid-run.
$matrixFlags = Get-FlavorMatrixFlags -MatrixPath (Join-Path (Join-Path $repoRoot "docs") "FLAVOR_MATRIX.md")

if (Test-Path $dataFile) {
    $lineNo = 0
    foreach ($raw in (Get-Content -LiteralPath $dataFile -Encoding UTF8)) {
        $lineNo++
        if ($raw.Trim().Length -eq 0) { continue }
        $count++
        $obj = $null
        try { $obj = $raw | ConvertFrom-Json } catch {
            $errors.Add("L${lineNo}: not valid JSON"); continue
        }
        # Required fields
        foreach ($k in $required) {
            if (-not ($obj.PSObject.Properties.Name -contains $k)) {
                $errors.Add("L${lineNo}: missing required field '$k'")
            }
        }
        # id pattern + uniqueness
        if ($obj.id) {
            if ($obj.id -notmatch '^[a-z0-9]+(?:[-_][a-z0-9]+)*\.[a-z0-9]+(?:[-_][a-z0-9]+)*$') {
                $errors.Add("L${lineNo}: id '$($obj.id)' not kebab '<area>.<feature>'")
            }
            # S0543: the area-prefix must be an area slug, not a spec id (s####). Active records only;
            # 'removed' tombstones keep their frozen historical id.
            $idStatus = if ($obj.PSObject.Properties.Name -contains 'status' -and $obj.status) { "$($obj.status)" } else { "active" }
            if ($idStatus -ne 'removed' -and ($obj.id.Split('.')[0] -match '^s\d{4}$')) {
                $errors.Add("L${lineNo}: id '$($obj.id)' uses a spec id as area prefix; use the area slug")
            }
            if ($seenIds.ContainsKey($obj.id)) {
                $errors.Add("L${lineNo}: duplicate id '$($obj.id)' (first at L$($seenIds[$obj.id]))")
            } else {
                $seenIds[$obj.id] = $lineNo
            }
        }
        # flavors
        if ($obj.flavors) {
            if ($obj.flavors -isnot [array] -or $obj.flavors.Count -eq 0) {
                $errors.Add("L${lineNo}: flavors must be a non-empty array")
            } else {
                foreach ($f in $obj.flavors) {
                    if ($validFlavors -notcontains $f) {
                        $errors.Add("L${lineNo}: invalid flavor '$f'")
                    }
                }
            }
        }
        # gate (S1929) - NOTE: the record's `gate` field is unrelated to this script's -Gate switch,
        # which only silences success output.
        #
        # A record may name the BuildConfig flag its capability lives behind. When it does, its
        # flavors must equal that flag's row in the generated matrix: the field is what makes the
        # claim checkable at all, because nothing in the code says which capability sits behind
        # which flag. When it does not, nothing is claimed and nothing is checked - that silence is
        # an assertion ("behind no flag"), which is why documentation-only records keep their own
        # sets instead of being swept to a runtime flag's.
        if ($obj.PSObject.Properties.Name -contains 'gate' -and
            -not [string]::IsNullOrWhiteSpace("$($obj.gate)")) {
            $gateName = "$($obj.gate)".Trim()
            if ($matrixFlags.Count -eq 0) {
                $errors.Add("L${lineNo}: record names gate '$gateName' but the flavor matrix could not be read")
            }
            elseif (-not $matrixFlags.ContainsKey($gateName)) {
                # An unknown name is an error rather than a skip: a typo would otherwise turn the
                # check off for that record and look exactly like a record that passed.
                $errors.Add("L${lineNo}: gate '$gateName' is not a flag in docs/FLAVOR_MATRIX.md")
            }
            else {
                $expected = @($matrixFlags[$gateName] | Sort-Object)
                $actual = @($obj.flavors | Sort-Object)
                if (($expected -join ',') -ne ($actual -join ',')) {
                    $errors.Add("L${lineNo}: flavors disagree with gate '$gateName' - expected [$($expected -join ', ')], recorded [$($actual -join ', ')]")
                }
            }
        }
        elseif ($obj.flavors -is [array] -and $obj.flavors.Count -gt 0 -and $matrixFlags.Count -gt 0) {
            # Unexplained flavor set (S1934) - counted, not refused: 242 records predate the rule.
            # An ungated record may claim the full six (nothing narrows it) or exactly some flag's
            # row (that flag narrows it). Anything else names a reach the build does not produce.
            $actualSet = ($obj.flavors | Sort-Object) -join ','
            $explained = ($actualSet -eq (($allFlavors | Sort-Object) -join ','))
            if (-not $explained) {
                foreach ($row in $matrixFlags.Values) {
                    if ((($row | Sort-Object) -join ',') -eq $actualSet) { $explained = $true; break }
                }
            }
            if (-not $explained) { $unexplained.Add("$($obj.id) [$actualSet]") | Out-Null }
        }
        # spec
        if ($obj.PSObject.Properties.Name -contains 'spec' -and $null -ne $obj.spec) {
            if ("$($obj.spec)" -notmatch '^S\d{4}$') {
                $errors.Add("L${lineNo}: spec '$($obj.spec)' not Sxxxx or null")
            }
        }
        # status
        if ($obj.PSObject.Properties.Name -contains 'status' -and $obj.status) {
            if ($validStatus -notcontains $obj.status) {
                $errors.Add("L${lineNo}: invalid status '$($obj.status)'")
            }
        }
        # EN-only
        if ($obj.name -and (Test-NonAscii "$($obj.name)")) {
            $errors.Add("L${lineNo}: non-ASCII in name (EN-only)")
        }
        if ($obj.description -and (Test-NonAscii "$($obj.description)")) {
            $errors.Add("L${lineNo}: non-ASCII in description (EN-only)")
        }
    }
}

if ($errors.Count -gt 0) {
    if (-not $Gate) {
        Write-Host "ALL_FEATURES validation FAILED ($($errors.Count) error(s)) in docs/$fileName" -ForegroundColor Red
        foreach ($e in $errors) { Write-Host "  $e" -ForegroundColor Red }
    }
    exit 1
}

# S1934 ratchet. The public inventory only: the noLegal file has its own contents and no baseline.
if (-not $NoLegal) {
    $baselineFile = Join-Path $PSScriptRoot "unexplained-flavors-baseline.txt"
    if ($matrixFlags.Count -eq 0) {
        if (-not $Gate) {
            Write-Host "ALL_FEATURES: ungated-flavors ratchet SKIPPED - docs/FLAVOR_MATRIX.md unreadable" -ForegroundColor Yellow
        }
    }
    elseif (-not (Test-Path -LiteralPath $baselineFile)) {
        if (-not $Gate) {
            Write-Host "ALL_FEATURES: ungated-flavors ratchet SKIPPED - baseline file missing ($baselineFile)" -ForegroundColor Yellow
        }
    }
    else {
        $baseline = 0
        $baselineRaw = ((Get-Content -LiteralPath $baselineFile -Raw) -replace '\s', '')
        if (-not [int]::TryParse($baselineRaw, [ref]$baseline)) {
            Write-Host "ALL_FEATURES: ungated-flavors baseline is not an integer ($baselineFile)" -ForegroundColor Red
            exit 1
        }
        if ($unexplained.Count -gt $baseline) {
            Write-Host ("ALL_FEATURES: ungated-flavors ratchet FAILED - {0} record(s) claim a reach the build system does not produce, baseline {1}." -f $unexplained.Count, $baseline) -ForegroundColor Red
            Write-Host "  An ungated record carries the full six flavors, or exactly one flag's row in docs/FLAVOR_MATRIX.md (S1934)." -ForegroundColor Red
            foreach ($u in $unexplained) { Write-Host "  $u" -ForegroundColor Red }
            exit 1
        }
        if ($unexplained.Count -lt $baseline -and -not $Gate) {
            Write-Host ("ALL_FEATURES: ungated-flavors ratchet improved - {0} record(s) against baseline {1}; lower the baseline in {2}." -f $unexplained.Count, $baseline, $baselineFile) -ForegroundColor Yellow
        }
    }
}

if (-not $Gate -and -not $Quiet) {
    Write-Host "ALL_FEATURES validation PASS: $count record(s) in docs/$fileName" -ForegroundColor Green
}
exit 0
