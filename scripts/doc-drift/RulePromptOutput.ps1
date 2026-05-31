# RulePromptOutput.ps1 - record renderer for the rule/prompt drift audit (S0315).
#
# Mirrors the scripts/doc-drift/Output.ps1 record grammar. The Find-* detectors emit
# executable-mismatch records; this module renders them verbatim. Dot-sourcing defines
# the functions only; it emits nothing. PowerShell 7+, -NoProfile safe.
#
# No category beyond the closed executable taxonomy is introduced here.

# --- Human-readable record stream -------------------------------------------

function Format-RulePromptReport {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Records,
        [switch] $Color
    )

    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($r in $Records) {
        $lines.Add(("{0} | {1} | {2} | expected: {3} | actual: {4}" -f `
            $r.mismatchKind, $r.sourceA, $r.sourceB, $r.expected, $r.actual))
    }

    $all      = @($Records)
    $total    = $all.Count
    $conflict = @($all | Where-Object { $_.severity -eq 'conflict' }).Count
    $stale    = @($all | Where-Object { $_.severity -eq 'stale' }).Count
    $missing  = @($all | Where-Object { $_.severity -eq 'missing' }).Count

    $lines.Add(("SUMMARY | total: {0} | conflict: {1} | stale: {2} | missing: {3}" -f `
        $total, $conflict, $stale, $missing))

    return $lines.ToArray()
}

# --- Machine-readable JSON --------------------------------------------------

function ConvertTo-RulePromptJson {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Records
    )

    $all     = @($Records)
    $total   = $all.Count
    $verdict = if ($total -gt 0) { 'DRIFT' } else { 'CLEAN' }

    $byKind = [ordered]@{}
    foreach ($r in $all) {
        if ($byKind.Contains($r.mismatchKind)) { $byKind[$r.mismatchKind]++ }
        else { $byKind[$r.mismatchKind] = 1 }
    }

    $obj = [pscustomobject]@{
        verdict = $verdict
        total   = $total
        byKind  = $byKind
        records = $all
    }

    return ($obj | ConvertTo-Json -Depth 6 -Compress)
}
