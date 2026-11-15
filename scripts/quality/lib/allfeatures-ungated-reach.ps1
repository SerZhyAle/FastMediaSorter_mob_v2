#requires -Version 7.0
<#
.SYNOPSIS
    Find ledger records that sit on a flag's row without naming a gate (S2937, S4633).

.DESCRIPTION
    Dot-source this; it defines functions and returns nothing on its own.

    The canon-shipped all_features/validate.ps1 counts an ungated record as explained when its flavors
    set equals the full dimension or the row of ANY flag (S1934). A set that is right on purpose and
    one that is right by coincidence read the same, and measured 2026-09-11 that was 491 of 1085
    records. The `gate` field is the only thing that turns the coincidence into a claim validate.ps1
    can check, and it was unwritable through the sanctioned closure path until close-and-log.ps1 grew
    -FeatGate (canon plugin 2026.1113.8). This rule is the repo-side half.

    Two cases, two checks. A set that equals the row of exactly one flag is check 7 (S2937). A set that
    equals the row of SEVERAL flags at once ([noLegal,standard] is SUPPORT_LAUNCHER,
    SUPPORT_WEAR_COMPANION and SUPPORT_NETWORK_MONITOR) is check 8 (S4633): the reach is still narrowed
    by something, so the gate is still owed, and any of the sharing flags satisfies validate.ps1
    because their rows are identical. The two debts are baselined apart so each shrinks on its own.

    Kept out of assert-allfeatures-sync.ps1 so the rule has a contract suite that needs no ledger, no
    matrix file and no lock: assert-allfeatures-sync.tests/.

Exit codes: none - this file is a library and exits nothing.
#>

function Get-FlagRowGroup {
    <#
    .SYNOPSIS
        Signature -> string[] of the flags whose row it is (sorted), for every non-trivial row.
    .DESCRIPTION
        The full dimension and the empty set are left out: the first is S1934's own "nothing narrows
        this" claim and is legal for every record, the second is a row that names no flavor at all.
    .PARAMETER FlagRows
        flag name -> string[] of the flavors it is ON in (Get-FlavorMatrixTable .Flags).
    .PARAMETER FullSignature
        Get-FlavorSetSignature of the whole flavor dimension.
    #>
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$FlagRows,
        [Parameter(Mandatory)][string]$FullSignature
    )
    $bySignature = @{}
    foreach ($flag in $FlagRows.Keys) {
        $sig = Get-FlavorSetSignature -Flavors @($FlagRows[$flag])
        if ($sig -eq '' -or $sig -eq $FullSignature) { continue }
        if (-not $bySignature.ContainsKey($sig)) { $bySignature[$sig] = [System.Collections.Generic.List[string]]::new() }
        $bySignature[$sig].Add([string]$flag)
    }
    $groups = @{}
    foreach ($sig in $bySignature.Keys) { $groups[$sig] = [string[]]@($bySignature[$sig] | Sort-Object) }
    return $groups
}

function Get-SingleFlagRowMap {
    <#
    .SYNOPSIS
        Signature -> flag name, for every flag whose row no other flag shares.
    .PARAMETER FlagRows
        flag name -> string[] of the flavors it is ON in (Get-FlavorMatrixTable .Flags).
    .PARAMETER FullSignature
        Get-FlavorSetSignature of the whole flavor dimension.
    #>
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$FlagRows,
        [Parameter(Mandatory)][string]$FullSignature
    )
    $groups = Get-FlagRowGroup -FlagRows $FlagRows -FullSignature $FullSignature
    $unique = @{}
    foreach ($sig in $groups.Keys) {
        if ($groups[$sig].Count -eq 1) { $unique[$sig] = $groups[$sig][0] }
    }
    return $unique
}

function Get-SharedFlagRowMap {
    <#
    .SYNOPSIS
        Signature -> string[] of the flags sharing it (sorted), for every row two or more flags own.
    .PARAMETER FlagRows
        flag name -> string[] of the flavors it is ON in.
    .PARAMETER FullSignature
        Get-FlavorSetSignature of the whole flavor dimension.
    #>
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$FlagRows,
        [Parameter(Mandatory)][string]$FullSignature
    )
    $groups = Get-FlagRowGroup -FlagRows $FlagRows -FullSignature $FullSignature
    $shared = @{}
    foreach ($sig in $groups.Keys) {
        if ($groups[$sig].Count -ge 2) { $shared[$sig] = $groups[$sig] }
    }
    return $shared
}

function Select-UngatedOnRow {
    <#
    .SYNOPSIS
        Ungated records whose set is a key of RowMap, as (record, signature) pairs in ledger order.
    .DESCRIPTION
        Any non-blank `gate`, a NAME+NAME conjunction included, counts as gated: validate.ps1 then
        compares the set against the named row, a stronger claim than either check makes.
    #>
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Records,
        [Parameter(Mandatory)][System.Collections.IDictionary]$RowMap
    )
    $found = [System.Collections.Generic.List[object]]::new()
    foreach ($rec in $Records) {
        $names = $rec.PSObject.Properties.Name
        if ($names -contains 'gate' -and -not [string]::IsNullOrWhiteSpace("$($rec.gate)")) { continue }
        if (-not ($names -contains 'flavors')) { continue }
        $sig = Get-FlavorSetSignature -Flavors @($rec.flavors)
        if (-not $RowMap.ContainsKey($sig)) { continue }
        $found.Add([pscustomobject]@{ Id = "$($rec.id)"; Signature = $sig })
    }
    return $found.ToArray()
}

function Get-UngatedSingleFlagRecord {
    <#
    .SYNOPSIS
        Check 7's offenders: ungated, and their set equals one flag's row alone.
    .DESCRIPTION
        Returns one object per record with Id, Signature and Flag, in ledger order.
    .PARAMETER Records
        Parsed ledger records (ConvertFrom-Json objects), in file order.
    .PARAMETER FlagRows
        flag name -> string[] of the flavors it is ON in.
    .PARAMETER FullSignature
        Get-FlavorSetSignature of the whole flavor dimension.
    #>
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Records,
        [Parameter(Mandatory)][System.Collections.IDictionary]$FlagRows,
        [Parameter(Mandatory)][string]$FullSignature
    )
    $unique = Get-SingleFlagRowMap -FlagRows $FlagRows -FullSignature $FullSignature
    $found = [System.Collections.Generic.List[object]]::new()
    foreach ($hit in @(Select-UngatedOnRow -Records $Records -RowMap $unique)) {
        $found.Add([pscustomobject]@{ Id = $hit.Id; Signature = $hit.Signature; Flag = $unique[$hit.Signature] })
    }
    # Emitted unrolled: the caller wraps the call in @() and gets a flat array. A leading comma would
    # nest it, and @() around a nested one-element array reads as ONE record whose Id is every id.
    return $found.ToArray()
}

function Get-UngatedSharedRowRecord {
    <#
    .SYNOPSIS
        Check 8's offenders: ungated, and their set equals a row several flags share (S4633).
    .DESCRIPTION
        Returns one object per record with Id, Signature and Flags (string[], sorted), in ledger order,
        emitted unrolled like Get-UngatedSingleFlagRecord.
    .PARAMETER Records
        Parsed ledger records (ConvertFrom-Json objects), in file order.
    .PARAMETER FlagRows
        flag name -> string[] of the flavors it is ON in.
    .PARAMETER FullSignature
        Get-FlavorSetSignature of the whole flavor dimension.
    #>
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Records,
        [Parameter(Mandatory)][System.Collections.IDictionary]$FlagRows,
        [Parameter(Mandatory)][string]$FullSignature
    )
    $shared = Get-SharedFlagRowMap -FlagRows $FlagRows -FullSignature $FullSignature
    $found = [System.Collections.Generic.List[object]]::new()
    foreach ($hit in @(Select-UngatedOnRow -Records $Records -RowMap $shared)) {
        $found.Add([pscustomobject]@{ Id = $hit.Id; Signature = $hit.Signature; Flags = $shared[$hit.Signature] })
    }
    return $found.ToArray()
}

function Read-UngatedReachBaseline {
    <#
    .SYNOPSIS
        The ids a baseline file excuses. One id per line, `#` lines and blanks ignored.
    .PARAMETER Path
        The baseline file; the caller has already checked that it exists.
    #>
    param([Parameter(Mandatory)][string]$Path)
    $ids = [System.Collections.Generic.List[string]]::new()
    foreach ($row in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        $t = $row.Trim()
        if ($t.Length -eq 0 -or $t.StartsWith('#')) { continue }
        $ids.Add($t)
    }
    return $ids.ToArray()
}
