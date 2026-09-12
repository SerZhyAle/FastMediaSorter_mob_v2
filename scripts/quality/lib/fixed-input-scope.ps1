#requires -Version 7.0
<#
.SYNOPSIS
    S2824: shared chargeability test for a gate whose inputs are a fixed, enumerated file list.

.DESCRIPTION
    A parity gate reads a fixed set of named source files and judges one rule between them. Under
    post-change.ps1 -ScopeToFile that made it fatal on a divergence it could not attribute to the
    caller: measured 2026-09-10 over temp/metrics/gate-executions.jsonl, wear-settings-parity-gate
    failed 40 closures in seventeen days, and the failures came in runs - nineteen consecutive
    between 00:52 and 04:43 on 2026-09-03 - which is the signature of one divergence standing in the
    tree and charging nineteen different sessions, not of nineteen authors each breaking the pair.
    S2820 is the case that named it: a changed set lying entirely in wear/../ui/streams/ was refused
    by a divergence a sibling session was mid-way through creating.

    The test is the caller's changed set against the gate's OWN declared inputs, so the path list
    stays in one place - the gate (S1621). Duplicating those 24 paths into post-change.ps1's
    Test-AnyChangedFile trigger would give the same answer today and drift from it silently.

    A false answer here is the expensive one: it would let a real divergence through. So an absent
    or empty -ChangedFiles means "the caller did not narrow anything" and returns chargeable, which
    keeps the project-wide runs (a.ps1 fg, assert-release-scope-gates.ps1, the release path) exactly
    as fatal as they are today.

    Granularity is the whole input set, not the individual finding. A session that really did edit
    one declared input is still charged for a divergence between two others; attributing every
    finding to the files it was derived from would mean rewriting all fourteen of the parity gate's
    checks. What this kills is the observed class - a changed set lying wholly outside the inputs,
    which is 97% of the files that trigger the wear parity gate (825 trigger-eligible against 24
    chargeable).
#>

. (Join-Path $PSScriptRoot 'changed-files.ps1')

function ConvertTo-ComparablePath {
    <# Normalize to forward slashes, drop a leading ./, lowercase - so a gate declaring a path with
       either separator and a caller passing the other compare equal. #>
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) { return '' }
    return ((($Path -replace '\\', '/') -replace '^\./', '').Trim().TrimEnd('/')).ToLowerInvariant()
}

function Test-FixedInputsChargeable {
    <# True when this run may charge its findings to the caller: either the caller narrowed nothing,
       or at least one file the gate declares as an input is in the changed set. A declared input may
       be absolute (gates build theirs with Join-Path $root), so the comparison is suffix-or-equal on
       the changed path, which is repo-relative. #>
    [CmdletBinding()]
    param(
        [AllowEmptyCollection()][AllowNull()][string[]]$ChangedFiles,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$InputPaths
    )

    $changed = @(Expand-ChangedFiles -ChangedFiles $ChangedFiles |
            ForEach-Object { ConvertTo-ComparablePath -Path $_ } |
            Where-Object { $_ })
    if ($changed.Count -eq 0) { return $true }

    foreach ($declared in $InputPaths) {
        # Not $input - that is an automatic variable and assigning it shadows the pipeline binder.
        $declaredPath = ConvertTo-ComparablePath -Path $declared
        if (-not $declaredPath) { continue }
        foreach ($candidate in $changed) {
            if ($declaredPath -eq $candidate -or $declaredPath.EndsWith('/' + $candidate)) { return $true }
        }
    }
    return $false
}

function Write-NotChargedVerdict {
    <# The single wording every fixed-input gate prints when it declines to charge, so a reader who
       has met it in one gate recognises it in the next. #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$GateName,
        [Parameter(Mandatory = $true)][string[]]$Findings
    )
    Write-Host ("{0}: reported, not charged to this run - {1} divergence(s) stand in the tree, and no file " -f
        $GateName, $Findings.Count)
    Write-Host ("  this gate declares as an input is in -ChangedFiles, so the change in front of it cannot be " +
        "their cause. Their author owns them.")
    Write-Host ("  Re-run {0} with no -ChangedFiles for the project-wide verdict." -f $GateName)
    foreach ($finding in ($Findings | Sort-Object -Unique)) { Write-Host ("  {0}" -f $finding) }
}
