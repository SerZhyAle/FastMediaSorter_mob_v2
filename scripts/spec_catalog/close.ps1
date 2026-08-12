[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id,
    [Parameter(Mandatory)]
    [ValidateSet('Verified','Archived')]
    [string] $Status
)

# Convert terminating errors (Write-Error, throw, provider errors) into
# the documented `exit 1` so callers can rely on $LASTEXITCODE.
trap {
    Write-Host $_ -ForegroundColor Red
    exit 1
}

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }

$records = [System.Collections.Generic.List[object]]::new()
# S1437: read -> mutate -> write is one critical section. Two processes holding the same
# snapshot lose one change entirely - the later write replaces the whole journal.
Enter-CatalogLock
foreach ($r in (Read-Catalog)) { $records.Add($r) }

$idx = -1
for ($i = 0; $i -lt $records.Count; $i++) {
    if ($records[$i].id -eq $Id) { $idx = $i; break }
}
if ($idx -lt 0) {
    Write-Error "Record '$Id' not found."
    exit 1
}

$old = $records[$idx]
$oldStatus = $old.status

if ($oldStatus -match '^Block') {
    Write-Error "Unblock first: update.ps1 -Id $Id -Status <previous>"
    exit 1
}

$today = Get-Today
$now   = Get-Now

$updated = [pscustomobject]@{
    id       = [string]$old.id
    name     = [string]$old.name
    status   = [string]$Status
    priority = [int]$old.priority
    file     = [string]$old.file
    created  = [string]$old.created
    updated  = $now
    closed_at = $today
}
if ($old.PSObject.Properties.Name -contains 'tier' -and $null -ne $old.tier -and "$($old.tier)" -ne '') {
    $updated | Add-Member -NotePropertyName 'tier' -NotePropertyValue ([int]$old.tier)
}
$fixedKeys = @('id','name','status','priority','tier','file','created','updated','closed_at')
foreach ($prop in $old.PSObject.Properties) {
    if ($fixedKeys -notcontains $prop.Name -and -not ($updated.PSObject.Properties.Name -contains $prop.Name)) {
        $updated | Add-Member -NotePropertyName $prop.Name -NotePropertyValue $prop.Value
    }
}

Assert-Record -Record $updated

# Closing to Archived relocates the record into the archive journal; closing to
# Verified keeps it in the active journal.
if ($Status -eq 'Archived') {
    Add-ArchiveRecord -Record $updated
    $remaining = @($records | Where-Object { $_.id -ne $Id })
    Write-Catalog -Records ([object[]]$remaining)
} else {
    $records[$idx] = $updated
    Write-Catalog -Records $records.ToArray()
}

# Mirror the terminal status into the spec file header the same way update.ps1 does,
# so closing a ticket cannot leave the human-readable spec stale.
if ($oldStatus -ne $Status) {
    if (Sync-SpecHeaderStatus -PathRef $updated.file -Status $Status) {
        Write-Host ("  header synced -> {0}" -f $Status) -ForegroundColor DarkGray
    }
}

Exit-CatalogLock

Write-Output ("{0} {1} -> {2} [closed {3}]" -f $Id, $oldStatus, $Status, $today)
exit 0
