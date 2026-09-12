#requires -Version 7.0
<#
.SYNOPSIS
    Read docs/FLAVOR_MATRIX.md - flag name -> the flavors it is ON in, plus the full dimension.

.DESCRIPTION
    Dot-source this; it defines functions and returns nothing on its own.

    docs/FLAVOR_MATRIX.md is generated from the productFlavors block of app_v2/build.gradle.kts and
    is the only permitted source for this grid (CLAUDE.md Rule 8, S1392 - a summary of the grid in a
    prompt claimed `lite` had no audio and four documents followed it). Every repo-side gate that
    needs a flag's row reads it through here rather than re-parsing the table, so two gates cannot
    disagree about what a row is (S1621). The canon-shipped all_features/validate.ps1 carries its own
    equivalent parser because it ships to other projects and cannot reach into this repository; the
    two are kept behaviourally identical on purpose - the column order read from the header, and a
    cell counted ON only for `[+]`.

    Why `[+]*` counts and `n/a` does not: the trailing asterisk means the value was inherited from
    defaultConfig instead of declared by the flavor (the file's own legend), which is a fact about
    where the value came from, not about what it is. `n/a` means the field is absent from that
    flavor's BuildConfig entirely, so the capability has no entry point there at all.

Exit codes: none - this file is a library and exits nothing. Get-FlavorMatrixFlags returns an empty
hashtable when the matrix cannot be read, so the caller decides what absence means instead of having
a guess made for it here.
#>

# Resolved once at dot-source time, so the functions below do not depend on the caller's own depth
# under the repository root. The prefix is S2441's: a dot-sourced assignment lands in the CALLER's
# scope, where a plain name silently rebinds one of its variables.
$szaFlavorMatrixLibDir = $PSScriptRoot

function Get-FlavorMatrixPath {
    <#
    .SYNOPSIS
        Absolute path of docs/FLAVOR_MATRIX.md for the repository this library sits in.
    #>
    # scripts/quality/lib -> scripts/quality -> scripts -> the repository root.
    $repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $szaFlavorMatrixLibDir))
    return (Join-Path $repoRoot 'docs/FLAVOR_MATRIX.md')
}

function Get-FlavorMatrixTable {
    <#
    .SYNOPSIS
        The parsed matrix: .Flags (flag name -> string[] of flavors it is ON in) and .Flavors (the
        full dimension, in the table's own column order).
    .PARAMETER MatrixPath
        Defaults to this repository's docs/FLAVOR_MATRIX.md.
    #>
    param([string]$MatrixPath = '')

    if (-not $MatrixPath) { $MatrixPath = Get-FlavorMatrixPath }

    $flags = @{}
    $columns = @()
    if (-not (Test-Path -LiteralPath $MatrixPath)) {
        return [pscustomobject]@{ Flags = $flags; Flavors = $columns; Path = $MatrixPath }
    }

    foreach ($line in (Get-Content -LiteralPath $MatrixPath -Encoding UTF8)) {
        if ($line -notmatch '^\s*\|') { continue }
        $cells = @($line.Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() })
        if ($cells.Count -lt 2) { continue }

        if ($columns.Count -eq 0) {
            # The header is the first table row whose leading cell is the flag column's title. Read
            # rather than assumed, so adding or reordering a flavor does not silently transpose.
            if ($cells[0] -eq 'Flag') { $columns = @($cells[1..($cells.Count - 1)]) }
            continue
        }
        if ($cells[0] -match '^:?-{2,}') { continue }

        $name = $cells[0].Trim('`')
        # `minSdk` is a row of numbers, not a boolean flag; the upper-case test excludes it.
        if ($name -notmatch '^[A-Z][A-Z0-9_]*$') { continue }

        $on = @()
        for ($i = 0; $i -lt $columns.Count -and ($i + 1) -lt $cells.Count; $i++) {
            if ($cells[$i + 1] -like '*[[]+]*') { $on += $columns[$i] }
        }
        $flags[$name] = $on
    }

    return [pscustomobject]@{ Flags = $flags; Flavors = $columns; Path = $MatrixPath }
}

function Get-FlavorMatrixFlags {
    <#
    .SYNOPSIS
        flag name -> string[] of the flavors it is ON in. Empty when the matrix cannot be read.
    #>
    param([string]$MatrixPath = '')
    return (Get-FlavorMatrixTable -MatrixPath $MatrixPath).Flags
}

function Get-FlavorSetSignature {
    <#
    .SYNOPSIS
        Order-independent signature of a flavor set, for comparing a record's set against a row.
    .DESCRIPTION
        Sorted and comma-joined. A record lists its flavors in whatever order its author typed them
        and a matrix row is in column order, so comparing the two as written reports a difference
        that is not one.
    #>
    param([string[]]$Flavors)
    if (-not $Flavors) { return '' }
    return (($Flavors | Sort-Object) -join ',')
}
