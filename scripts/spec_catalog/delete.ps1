[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id,
    [switch] $Confirm
)

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }

$records = [System.Collections.Generic.List[object]]::new()
foreach ($r in (Read-Catalog)) { $records.Add($r) }

$idx = -1
for ($i = 0; $i -lt $records.Count; $i++) {
    if ($records[$i].id -eq $Id) { $idx = $i; break }
}
if ($idx -lt 0) { throw "Record '$Id' not found." }

if (-not $Confirm) {
    Write-Output "Would soft-delete $Id ($($records[$idx].name) - current status: $($records[$idx].status)). Re-run with -Confirm to apply."
    exit 1
}

$old = $records[$idx]
if ($old.status -eq 'Archived') {
    Write-Output "$Id already Archived (no-op)."
    return
}

$archived = [pscustomobject]@{
    id       = [string]$old.id
    name     = [string]$old.name
    status   = 'Archived'
    priority = [int]$old.priority
    file     = [string]$old.file
    created  = [string]$old.created
    updated  = (Get-Now)
}
if ($old.PSObject.Properties.Name -contains 'tier' -and $null -ne $old.tier -and "$($old.tier)" -ne '') {
    $archived | Add-Member -NotePropertyName 'tier' -NotePropertyValue ([int]$old.tier)
}

Assert-Record -Record $archived
$records[$idx] = $archived
Write-Catalog -Records $records.ToArray()
Write-Output "$Id Archived."
