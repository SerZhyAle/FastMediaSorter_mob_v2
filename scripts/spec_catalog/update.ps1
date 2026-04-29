[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id,
    [ValidateSet('Draft','Approved','Tactical','In Progress',
        'Implemented','Verified','Partial','Broken',
        'BlockByOtherTask','BlockNeedUserTest','BlockQuestions','BlockExternal',
        'Archived')]
    [string] $Status,
    [string] $Name,
    [string] $File,
    [int]    $Tier = -1,
    [int]    $Priority = -1
)

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }
if ($PSBoundParameters.ContainsKey('Priority')) {
    if ($Priority -lt 0 -or $Priority -gt 100) {
        throw "Invalid -Priority '$Priority' (must be 0..100)."
    }
}

$records = [System.Collections.Generic.List[object]]::new()
foreach ($r in (Read-Catalog)) { $records.Add($r) }

$idx = -1
for ($i = 0; $i -lt $records.Count; $i++) {
    if ($records[$i].id -eq $Id) { $idx = $i; break }
}
if ($idx -lt 0) { throw "Record '$Id' not found." }

$old = $records[$idx]
$oldStatus = $old.status

# Build mutable copy preserving all current fields.
$updated = [pscustomobject]@{
    id       = [string]$old.id
    name     = [string]$old.name
    status   = [string]$old.status
    priority = [int]$old.priority
    file     = [string]$old.file
    created  = [string]$old.created
    updated  = [string]$old.updated
}
if ($old.PSObject.Properties.Name -contains 'tier' -and $null -ne $old.tier -and "$($old.tier)" -ne '') {
    $updated | Add-Member -NotePropertyName 'tier' -NotePropertyValue ([int]$old.tier)
}

if ($PSBoundParameters.ContainsKey('Status'))   { $updated.status   = $Status }
if ($PSBoundParameters.ContainsKey('Name'))     { $updated.name     = $Name }
if ($PSBoundParameters.ContainsKey('File'))     { $updated.file     = ($File -replace '\\','/') }
if ($PSBoundParameters.ContainsKey('Priority')) { $updated.priority = $Priority }
if ($Tier -ge 0) {
    if ($updated.PSObject.Properties.Name -contains 'tier') { $updated.tier = $Tier }
    else { $updated | Add-Member -NotePropertyName 'tier' -NotePropertyValue $Tier }
}

$updated.updated = Get-Now

Assert-Record -Record $updated

$records[$idx] = $updated
Write-Catalog -Records $records.ToArray()

Write-Output ("{0} {1} -> {2}" -f $Id, $oldStatus, $updated.status)
