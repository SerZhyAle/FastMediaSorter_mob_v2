<#
.SYNOPSIS
    Patch selected fields of one existing ALL_FEATURES record in place, by id.

.DESCRIPTION
    Upsert-by-id (add.ps1) requires every field, which is clumsy when an audit only
    needs to adjust `flavors` or flip `status`. This patches a single record,
    preserving all other fields and the canonical key order. Errors (exit 2) if the
    id is not found - it never creates a new record.

    Flavor edit modes (pick one):
      -AddFlavors "vr,photos"     union into the current flavors
      -RemoveFlavors "lite"       subtract from the current flavors
      -SetFlavors "standard,vr"   replace the whole flavors list
    Other optional field patches: -Status, -Name, -Description, -Area, -Spec, -Gate.

    S1951: `gate` is preserved across a patch that does not mention it. Before that fix the
    rebuilt record simply omitted the key, so patching any field of a gated record silently
    asserted "behind no flag" - and validate.ps1 stopped checking that record's reach against
    the flag's row, which is the one thing the gate exists to do. Pass -Gate "" to clear it
    deliberately; that is the only way the key is dropped.

    -NoLegal targets docs/ALL_FEATURES_noLegal.jsonl.

.EXAMPLE
    .\scripts\all_features\patch.ps1 -Id audio-player.background-audio-service -AddFlavors vr
.EXAMPLE
    .\scripts\all_features\patch.ps1 -Id audio-player.pulsing-rings-backdrop -Status removed
#>
param(
    [Parameter(Mandatory = $true)] [string]$Id,
    [string]$NewId = "",
    [string]$AddFlavors = "",
    [string]$RemoveFlavors = "",
    [string]$SetFlavors = "",
    [ValidateSet("active", "removed")] [string]$Status = "",
    [string]$Name = "",
    [string]$Description = "",
    [string]$Area = "",
    [string]$Spec = "",
    [string]$Gate = "",
    [switch]$NoLegal,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
trap { Write-Error $_; exit 1 }

$validFlavors = @("standard", "lite", "photos", "legacy", "vr", "noLegal")
function Fail([string]$m) { Write-Error $m; exit 1 }
function Test-NonAscii([string]$s) { foreach ($c in $s.ToCharArray()) { if ([int][char]$c -gt 127) { return $true } } return $false }
function Split-Csv([string]$s) { return @($s -split '[,\s]+' | Where-Object { $_ } | ForEach-Object { $_.Trim() }) }

# repo root
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
. (Join-Path $scriptDir '_lib.ps1')
$repoRoot = Resolve-FeatureRepoRoot -ScriptDir $scriptDir
$fileName = if ($NoLegal) { "ALL_FEATURES_noLegal.jsonl" } else { "ALL_FEATURES.jsonl" }
$dataFile = Get-FeatureInventoryPath -RepoRoot $repoRoot -NoLegal:$NoLegal
if (-not (Test-Path $dataFile)) { Fail "Not found: $dataFile" }

$idN = $Id.Trim()

# S1537: the whole read -> patch -> write path is the critical section. Unlike add.ps1 the
# validation here runs INSIDE it, one record at a time, so every Fail can fire with the lock
# held - hence the finally.
Enter-FeatureLock -RepoRoot $repoRoot
try {
    $lines = Read-FeatureLines -Path $dataFile

    $found = $false
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($l in $lines) {
        $o = $null
        try { $o = $l | ConvertFrom-Json } catch { $o = $null }
        if (-not $o -or $o.id -ne $idN) { $out.Add($l); continue }
        $found = $true

        # current values
        $area = if ($Area) { $Area.Trim() } else { "$($o.area)" }
        $name = if ($Name) { $Name.Trim() } else { "$($o.name)" }
        $desc = if ($Description) { ($Description -replace '\s+', ' ').Trim() } else { "$($o.description)" }
        # NB: local must not be named $spec - it would alias the [string]-typed $Spec param (case-insensitive) and coerce $null to "".
        $specVal = if ($PSBoundParameters.ContainsKey('Spec')) {
            if ($Spec) { $Spec.Trim() } else { $null }
        } elseif ([string]::IsNullOrEmpty([string]$o.spec)) { $null } else { "$($o.spec)" }
        $status = if ($Status) { $Status } elseif ($o.PSObject.Properties.Name -contains 'status' -and $o.status) { "$($o.status)" } else { "active" }
        # NB: local must not be named $gate - it would alias the [string]-typed $Gate param.
        # Three states, same shape as $Spec above: unbound keeps the record's own gate, -Gate "FLAG"
        # sets it, -Gate "" drops the key (the assertion "behind no flag at all", S1929).
        $gateVal = if ($PSBoundParameters.ContainsKey('Gate')) {
            if ($Gate) { $Gate.Trim() } else { $null }
        } elseif ([string]::IsNullOrEmpty([string]$o.gate)) { $null } else { "$($o.gate)" }
        $flavors = @($o.flavors)

        if ($SetFlavors) {
            $flavors = Split-Csv $SetFlavors
        } else {
            if ($AddFlavors) { $flavors = @($flavors + (Split-Csv $AddFlavors)) }
            if ($RemoveFlavors) { $rm = Split-Csv $RemoveFlavors; $flavors = @($flavors | Where-Object { $rm -notcontains $_ }) }
        }
        $flavors = @($flavors | Select-Object -Unique)

        # validate
        if ($flavors.Count -eq 0) { Fail "Record '$idN' would have empty flavors." }
        foreach ($f in $flavors) { if ($validFlavors -notcontains $f) { Fail "Invalid flavor '$f'." } }
        if ((Test-NonAscii $name) -or (Test-NonAscii $desc)) { Fail "EN-only: non-ASCII in name/description." }
        if ($specVal -and "$specVal" -notmatch '^S\d{4}$') { Fail "Invalid spec '$specVal'." }
        # S1982: same shape check add.ps1 applies, so a gate cannot enter the inventory through the
        # patch path unchecked. The flag's existence and the flavors it implies stay with validate.ps1.
        if ($gateVal) {
            if ("$gateVal" -cnotmatch '^[A-Z][A-Z0-9_]*(?:\+[A-Z][A-Z0-9_]*)*$') {
                Fail "Invalid gate '$gateVal'. Expected a BuildConfig flag, or flags joined by '+' when the capability needs all of them at once."
            }
            $gateTerms = @("$gateVal" -split '\+')
            if (@($gateTerms | Sort-Object -Unique).Count -ne $gateTerms.Count) {
                Fail "Invalid gate '$gateVal'. A repeated flag narrows nothing - the second term is meant to be a different flag."
            }
        }

        $writeId = if ($NewId) { $NewId.Trim() } else { $idN }
        if ($writeId -notmatch '^[a-z0-9]+(?:[-_][a-z0-9]+)*\.[a-z0-9]+(?:[-_][a-z0-9]+)*$') { Fail "Invalid new id '$writeId'." }

        $rec = [ordered]@{ id = $writeId; area = $area; name = $name; description = $desc; flavors = $flavors; spec = $specVal; status = $status }
        # Same slot add.ps1 uses, so a patched record is byte-comparable with an added one.
        if ($gateVal) { $rec.Insert(5, 'gate', $gateVal) }
        $out.Add(($rec | ConvertTo-Json -Compress -Depth 5))
    }

    # The not-found exit lives outside the section: reporting it while still holding the lock
    # would make every caller behind us wait on a call that changes nothing.
    if ($found) { Write-FeatureLines -Path $dataFile -Lines $out }
}
finally { Exit-FeatureLock }

if (-not $found) { Write-Error "Id '$idN' not found in docs/$fileName" -ErrorAction Continue; exit 2 }

if (-not $Quiet) { Write-Host "[patch] $idN -> flavors=[$($flavors -join ',')] gate=$(if ($gateVal) { $gateVal } else { '(none)' }) status=$status (docs/$fileName)" -ForegroundColor Green }
exit 0
