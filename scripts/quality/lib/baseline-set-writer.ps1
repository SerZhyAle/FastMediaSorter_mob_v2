# Shared direction check for a gate's -UpdateBaseline writer (S3460, contract CHECK-BASELINE rule 6).
#
# Accepting new debt is an explicit act: the writer prints every identifier it is about to accept
# and refuses the harmful direction unless the caller states a -Reason. The safe direction - a
# baseline that only shrinks - is written without one, so tightening never needs ceremony.
#
# Which direction is harmful depends on what the file holds. A debt set grows harmfully
# (Additions); a retention set - identifiers that must keep holding - shrinks harmfully
# (Removals); a count floor is harmed by lowering it.
#
# Dot-source only. Both functions return $true when the write may proceed and $false when it must
# be refused; the caller exits 2 without touching the file.

function Test-BaselineWrite {
    param(
        [Parameter(Mandatory)][string]$Gate,
        [AllowEmptyCollection()][string[]]$Previous = @(),
        [AllowEmptyCollection()][string[]]$Current = @(),
        [string]$Reason,
        [ValidateSet('Additions', 'Removals')][string]$Guard = 'Additions'
    )

    $previousSet = [System.Collections.Generic.HashSet[string]]::new([string[]]@($Previous), [System.StringComparer]::Ordinal)
    $currentSet = [System.Collections.Generic.HashSet[string]]::new([string[]]@($Current), [System.StringComparer]::Ordinal)
    $added = @($currentSet | Where-Object { -not $previousSet.Contains($_) } | Sort-Object)
    $removed = @($previousSet | Where-Object { -not $currentSet.Contains($_) } | Sort-Object)

    Write-Host ("{0}: baseline delta - {1} added, {2} removed." -f $Gate, $added.Count, $removed.Count)
    foreach ($id in $added) { Write-Host "  + $id" }
    foreach ($id in $removed) { Write-Host "  - $id" }

    # @() around the whole if-expression: an empty array returned from `if` collapses to $null, and
    # $null.Count is a terminating error under the callers' StrictMode.
    $harmful = @(if ($Guard -eq 'Additions') { $added } else { $removed })
    if ($harmful.Count -gt 0 -and [string]::IsNullOrWhiteSpace($Reason)) {
        $verb = if ($Guard -eq 'Additions') { 'adds' } else { 'removes' }
        $why = "${Gate}: -UpdateBaseline $verb $($harmful.Count) identifier(s) and requires -Reason '<why>'. " +
        'Accepting that direction is an explicit act (CHECK-BASELINE rule 6); the baseline was not written.'
        Write-Error $why -ErrorAction Continue
        return $false
    }
    return $true
}

function Test-BaselineFloorWrite {
    param(
        [Parameter(Mandatory)][string]$Gate,
        [int]$Previous,
        [int]$Current,
        [string]$Reason
    )

    Write-Host ("{0}: floor {1} -> {2}." -f $Gate, $Previous, $Current)
    if ($Current -lt $Previous -and [string]::IsNullOrWhiteSpace($Reason)) {
        $why = "${Gate}: -UpdateBaseline lowers the floor from $Previous to $Current and requires -Reason '<why>'. " +
        'A lower floor hides lost records (CHECK-BASELINE rule 6); the baseline was not written.'
        Write-Error $why -ErrorAction Continue
        return $false
    }
    return $true
}

function Get-BaselineReasonLine {
    param([string]$Reason)
    if ([string]::IsNullOrWhiteSpace($Reason)) { return @() }
    return @("# Reason ($(Get-Date -Format 'yyyy-MM-dd')): $($Reason.Trim())")
}
