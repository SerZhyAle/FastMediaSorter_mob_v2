#requires -Version 7.0
<#
.SYNOPSIS
    Set or clear the `wearFlavors` axis on existing ALL_FEATURES records (S3264).

.DESCRIPTION
    The sanctioned writers cannot touch this field at all. `add.ps1` has no parameter for it and
    `patch.ps1` rebuilds the record from an [ordered] of the parameters it was given, so any patch
    of a record that carried `wearFlavors` DROPS the key - the same destruction S3209 measured for
    `gate`, one field over. Both are canon forwarders whose bodies this repository does not own
    (S2402), which is why this writer is project-side rather than a parameter added there.

    The field itself is S2090's: which WEAR-MODULE variant a capability exists in, a separate axis
    from `flavors`, which always answers "which PHONE build gates this". Absence asserts "every
    watch build", so naming every declared value is refused by validate.ps1 - clearing the key is
    how that assertion is written, never `-WearFlavors "standard,noLegal"`.

    Key order matches what `add.ps1` and `patch.ps1` emit, with `wearFlavors` immediately after
    `flavors`, so a record written here stays byte-comparable with one written there.

    Exit codes:
      0 - every requested id was updated (or already carried the requested value).
      1 - invalid arguments, or an inventory line that cannot be rebuilt.
      2 - an id was not found in the inventory; nothing was written.

.PARAMETER Ids
    One id, or several separated by commas or whitespace.

.PARAMETER WearFlavors
    The watch variants to declare, comma-separated. Values are validated against the profile's
    wear dimension by validate.ps1 afterwards, not here.

.PARAMETER Clear
    Drop the key instead of setting it - the assertion "every watch build has this".

.PARAMETER NoLegal
    Operate on docs/ALL_FEATURES_noLegal.jsonl.

.EXAMPLE
    pwsh -NoProfile -File scripts/all_features/set-wear-flavors.ps1 -Ids wear.blood-pressure -WearFlavors noLegal

.EXAMPLE
    pwsh -NoProfile -File scripts/all_features/set-wear-flavors.ps1 -Ids "wear.tiles,wear.motion_monitor" -WearFlavors noLegal
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Ids,
    [string]$WearFlavors = '',
    [switch]$Clear,
    [switch]$NoLegal,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '_lib.ps1')

$repoRoot = Resolve-FeatureRepoRoot -ScriptDir $PSScriptRoot
$dataFile = Get-FeatureInventoryPath -RepoRoot $repoRoot -NoLegal:$NoLegal

$idList = @($Ids -split '[,\s]+' | Where-Object { $_ } | ForEach-Object { $_.Trim() })
if ($idList.Count -eq 0) { Write-Error 'No id given.' -ErrorAction Continue; exit 1 }
if (-not $Clear -and -not $WearFlavors) {
    Write-Error 'Give -WearFlavors <values> or -Clear.' -ErrorAction Continue; exit 1
}
if ($Clear -and $WearFlavors) {
    Write-Error '-Clear and -WearFlavors are mutually exclusive.' -ErrorAction Continue; exit 1
}
$wanted = @($WearFlavors -split '[,\s]+' | Where-Object { $_ } | ForEach-Object { $_.Trim() })

$updated = [System.Collections.Generic.List[string]]::new()
Enter-FeatureLock -RepoRoot $repoRoot
try {
    $lines = Read-FeatureLines -Path $dataFile
    $out = [System.Collections.Generic.List[string]]::new()
    foreach ($line in $lines) {
        $obj = $null
        try { $obj = $line | ConvertFrom-Json -ErrorAction Stop } catch { $obj = $null }
        if (-not $obj -or ($idList -cnotcontains "$($obj.id)")) { $out.Add($line); continue }

        # Rebuilt from the record's OWN property order rather than from a fixed field list: a
        # writer that enumerates the fields it knows is exactly what strips the ones it does not.
        $rec = [ordered]@{}
        foreach ($p in $obj.PSObject.Properties) {
            if ($p.Name -ceq 'wearFlavors') { continue }
            $rec[$p.Name] = $p.Value
            if ($p.Name -ceq 'flavors' -and -not $Clear) { $rec['wearFlavors'] = $wanted }
        }
        if (-not $Clear -and -not $rec.Contains('wearFlavors')) {
            Write-Error "Record '$($obj.id)' has no 'flavors' key - the axis has no anchor to sit after." -ErrorAction Continue
            exit 1
        }
        $out.Add(($rec | ConvertTo-Json -Compress -Depth 5))
        $updated.Add("$($obj.id)")
    }

    $missing = @($idList | Where-Object { $updated -cnotcontains $_ })
    if ($missing.Count -gt 0) {
        Write-Error "Not found in $(Split-Path $dataFile -Leaf): $($missing -join ', '). Nothing was written." -ErrorAction Continue
        exit 2
    }
    Write-FeatureLines -Path $dataFile -Lines $out
}
finally { Exit-FeatureLock }

if (-not $Quiet) {
    $verb = if ($Clear) { 'cleared' } else { "set to [$($wanted -join ',')]" }
    Write-Host "[set-wear-flavors] wearFlavors $verb on $($updated.Count) record(s)" -ForegroundColor Green
}
exit 0
