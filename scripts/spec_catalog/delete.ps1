[CmdletBinding()]
param(
    [string] $Id,
    [switch] $Confirm,
    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/spec_catalog/delete.ps1'
    exit $LASTEXITCODE
}
if (-not $Id) {
    Write-Error 'delete.ps1 requires -Id <Sxxxx>. Run with -Help for the parameter list.' -ErrorAction Continue
    exit 1
}

# Convert terminating errors (Write-Error, throw, provider errors) into
# the documented `exit 1` so callers can rely on $LASTEXITCODE.
trap {
    Write-Host $_ -ForegroundColor Red
    exit 1
}

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }

$records = [System.Collections.Generic.List[object]]::new()
# S1437: read -> mutate -> write is one critical section. The early-exit paths below leave the
# lock held until the process ends, which releases it - these scripts exit immediately after.
Enter-CatalogLock
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
    exit 0
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
# Relocate into the archive journal (append) and drop from the active one, so
# the split invariant holds (no Archived row in the active journal).
Add-ArchiveRecord -Record $archived
$remaining = @($records | Where-Object { $_.id -ne $Id })
Write-Catalog -Records ([object[]]$remaining)
Exit-CatalogLock
Write-Output "$Id Archived."
exit 0
