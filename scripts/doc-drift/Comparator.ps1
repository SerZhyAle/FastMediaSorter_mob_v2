# Comparator.ps1 - classifies (pin, doc) mentions against canonical Gradle pins.
#
# Public entry: Compare-PinsToDocs -GradlePins <hashtable> -DocMentions <array>
# Returns: array of [pscustomobject] records:
#     Pin       = '<pin-name>'
#     Status    = 'PASS' | 'FAIL' | 'WARN' | 'SKIP' | 'MISSING' | 'INCONSISTENT'
#     Gradle    = '<canonical>' | $null
#     DocPath   = '<doc/path>'
#     DocValue  = '<doc-side value or composite>' | $null
#     Reason    = '<text>' | $null
#
# Contract: PLAN/S0271_truth_drift_detection/DECISIONS.md (D-2 required, D-3 range, D-4 policy)
# No external module dependencies. PowerShell 7+. -NoProfile safe.

# --- Range support (D-3) -----------------------------------------------------

$script:RangePlusSuffix  = '^(?<min>\d+(?:\.\d+)*)\+$'
$script:RangeGreaterEq   = '^>=\s*(?<min>\d+(?:\.\d+)*)$'
$script:RangeWildcard    = '^(?<min>\d+(?:\.\d+)*)\.x$'

function script:Test-IsRangeMarker {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Value)
    if ([string]::IsNullOrEmpty($Value)) { return $false }
    return ($Value -match $script:RangePlusSuffix) -or
           ($Value -match $script:RangeGreaterEq) -or
           ($Value -match $script:RangeWildcard)
}

function script:Get-VersionSegments {
    param([Parameter(Mandatory)][string] $Version, [int] $Pad = 4)
    $parts = $Version -split '\.'
    $nums = @()
    foreach ($p in $parts) {
        $n = 0
        if ([int]::TryParse($p, [ref]$n)) { $nums += $n } else { $nums += 0 }
    }
    while ($nums.Count -lt $Pad) { $nums += 0 }
    return $nums
}

function script:Compare-VersionSegments {
    param([int[]] $A, [int[]] $B)
    for ($i = 0; $i -lt [math]::Min($A.Count, $B.Count); $i++) {
        if ($A[$i] -lt $B[$i]) { return -1 }
        if ($A[$i] -gt $B[$i]) { return 1 }
    }
    return 0
}

function Test-VersionInsideRange {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $Range,
        [Parameter(Mandatory)][string] $Actual
    )
    $min = $null
    if ($Range -match $script:RangePlusSuffix) { $min = $Matches['min'] }
    elseif ($Range -match $script:RangeGreaterEq) { $min = $Matches['min'] }
    elseif ($Range -match $script:RangeWildcard) {
        # Wildcard X.Y.x: actual must start with X.Y. or equal X.Y
        $prefix = $Matches['min']
        if ($Actual -eq $prefix -or $Actual.StartsWith($prefix + '.')) { return $true }
        return $false
    }
    if ($null -eq $min) { return $false }
    $minSegs = Get-VersionSegments -Version $min
    $actualSegs = Get-VersionSegments -Version $Actual
    return ((Compare-VersionSegments -A $actualSegs -B $minSegs) -ge 0)
}

# --- Public entry ------------------------------------------------------------

function Compare-PinsToDocs {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][object] $GradlePins,
        [Parameter(Mandatory)][object[]] $DocMentions
    )

    $records = @()
    # Group mentions by pin (preserves manifest order via stable iteration).
    $byPin = @{}
    $pinOrder = @()
    foreach ($m in $DocMentions) {
        if (-not $byPin.ContainsKey($m.Pin)) {
            $byPin[$m.Pin] = @()
            $pinOrder += $m.Pin
        }
        $byPin[$m.Pin] += $m
    }

    foreach ($pinName in $pinOrder) {
        $gradleVal = $null
        if ($GradlePins.Contains($pinName)) {
            $gradleVal = [string]$GradlePins[$pinName]
        }
        foreach ($mention in ($byPin[$pinName] | Sort-Object DocPath)) {

            if ($null -eq $gradleVal) {
                $records += [pscustomobject]@{
                    Pin = $pinName; Status = 'SKIP'; Gradle = $null
                    DocPath = $mention.DocPath; DocValue = $null
                    Reason = 'gradle key not extracted by parser'
                }
                continue
            }

            $values = @($mention.Mentions)
            if ($values.Count -eq 0) {
                if ($mention.Required) {
                    $records += [pscustomobject]@{
                        Pin = $pinName; Status = 'MISSING'; Gradle = $gradleVal
                        DocPath = $mention.DocPath; DocValue = $null
                        Reason = 'required mention not found'
                    }
                } else {
                    $records += [pscustomobject]@{
                        Pin = $pinName; Status = 'SKIP'; Gradle = $gradleVal
                        DocPath = $mention.DocPath; DocValue = $null
                        Reason = 'not required in this doc'
                    }
                }
                continue
            }

            # Multi-mention divergence under allMustMatch (D-4)
            if ($values.Count -gt 1 -and $mention.Policy -eq 'allMustMatch') {
                $unique = $values | Select-Object -Unique
                if ($unique.Count -gt 1) {
                    $records += [pscustomobject]@{
                        Pin = $pinName; Status = 'INCONSISTENT'; Gradle = $gradleVal
                        DocPath = $mention.DocPath
                        DocValue = ($unique -join ' vs ')
                        Reason = $null
                    }
                    continue
                }
                # All identical: collapse to single value
                $values = @($unique[0])
            }

            # Single-value classification (or collapsed multi-identical, or firstOnly truncated)
            $docVal = [string]$values[0]
            if (Test-IsRangeMarker -Value $docVal) {
                if (Test-VersionInsideRange -Range $docVal -Actual $gradleVal) {
                    $records += [pscustomobject]@{
                        Pin = $pinName; Status = 'WARN'; Gradle = $gradleVal
                        DocPath = $mention.DocPath; DocValue = $docVal
                        Reason = 'range marker satisfied by gradle version'
                    }
                } else {
                    $records += [pscustomobject]@{
                        Pin = $pinName; Status = 'FAIL'; Gradle = $gradleVal
                        DocPath = $mention.DocPath; DocValue = $docVal
                        Reason = 'range marker not satisfied'
                    }
                }
            } elseif ($docVal -eq $gradleVal) {
                $records += [pscustomobject]@{
                    Pin = $pinName; Status = 'PASS'; Gradle = $gradleVal
                    DocPath = $mention.DocPath; DocValue = $docVal
                    Reason = $null
                }
            } else {
                $records += [pscustomobject]@{
                    Pin = $pinName; Status = 'FAIL'; Gradle = $gradleVal
                    DocPath = $mention.DocPath; DocValue = $docVal
                    Reason = $null
                }
            }
        }
    }
    return ,$records
}
