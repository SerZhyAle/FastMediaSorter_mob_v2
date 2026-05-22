[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Name,
    [Parameter(Mandatory)][string] $File,
    [ValidateSet('Draft','Approved','Tactical','In Progress',
        'Implemented','Verified','Partial','Broken',
        'BlockByOtherTask','BlockNeedUserTest','BlockQuestions','BlockExternal',
        'Archived')]
    [string] $Status = 'Draft',
    [int]    $Tier   = -1,
    [ValidateRange(0,100)]
    [int]    $Priority = 50,
    [string] $Id
)

# Convert terminating errors (Write-Error, throw, provider errors) into
# the documented `exit 1` so callers can rely on $LASTEXITCODE.
trap {
    Write-Host $_ -ForegroundColor Red
    exit 1
}

. (Join-Path $PSScriptRoot '_lib.ps1')

$records = Read-Catalog

if (-not $Id) {
    $Id = New-CatalogId
} else {
    if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }
}

foreach ($r in $records) {
    if ($r.id -eq $Id) { throw "Duplicate id '$Id'." }
}
$activeNameClash = $records | Where-Object { $_.name -eq $Name -and $_.status -ne 'Archived' }
if ($activeNameClash) {
    throw "Active record with name '$Name' already exists (id $($activeNameClash[0].id))."
}

$now = Get-Now
$today = Get-Today

$record = [pscustomobject]@{
    id       = $Id
    name     = $Name
    status   = $Status
    priority = $Priority
    file     = ($File -replace '\\', '/')
    created  = $today
    updated  = $now
}
if ($Tier -ge 0) {
    $record | Add-Member -NotePropertyName 'tier' -NotePropertyValue $Tier
}

Assert-Record -Record $record

$list = [System.Collections.Generic.List[object]]::new()
foreach ($r in $records) { $list.Add($r) }
$list.Add($record)
Write-Catalog -Records $list.ToArray()

Write-Output $Id
exit 0
