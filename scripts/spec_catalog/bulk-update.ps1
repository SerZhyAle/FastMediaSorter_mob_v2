[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]] $Id,
    [ValidateSet('Draft','Approved','Tactical','In Progress',
        'Implemented','Verified','Partial','Broken',
        'BlockByOtherTask','BlockNeedUserTest','BlockQuestions','BlockExternal',
        'Archived')]
    [string] $Status,
    [ValidateRange(0,100)]
    [int]    $Priority = -1
)

# Convert terminating errors (Write-Error, throw, provider errors) into
# the documented `exit 1` so callers can rely on $LASTEXITCODE.
trap {
    Write-Host $_ -ForegroundColor Red
    exit 1
}

. (Join-Path $PSScriptRoot '_lib.ps1')

if (-not $PSBoundParameters.ContainsKey('Status') -and $Priority -lt 0) {
    Write-Error "At least one of -Status or -Priority must be provided."
    exit 1
}

# Phase 1: validate all ids before touching the journal
$errors = New-Object System.Collections.Generic.List[string]
$allRecords = [System.Collections.Generic.List[object]]::new()
# S1437: read -> mutate -> write is one critical section. Two processes holding the same
# snapshot lose one change entirely - the later write replaces the whole journal.
Enter-CatalogLock
foreach ($r in (Read-Catalog)) { $allRecords.Add($r) }

$indexMap = @{}
for ($i = 0; $i -lt $allRecords.Count; $i++) {
    $indexMap[$allRecords[$i].id] = $i
}

foreach ($ticketId in $Id) {
    if ($ticketId -notmatch '^S\d{4}$') {
        $errors.Add("Invalid id '$ticketId' (must match S####).")
        continue
    }
    if (-not $indexMap.ContainsKey($ticketId)) {
        $errors.Add("Record '$ticketId' not found.")
    }
}

if ($errors.Count -gt 0) {
    foreach ($e in $errors) { Write-Error $e }
    exit 1
}

# Phase 2: apply changes in memory and assert
$now = Get-Now
$results = New-Object System.Collections.Generic.List[string]

foreach ($ticketId in $Id) {
    $idx = $indexMap[$ticketId]
    $old = $allRecords[$idx]
    $oldStatus = $old.status

    $updated = [pscustomobject]@{
        id       = [string]$old.id
        name     = [string]$old.name
        status   = if ($PSBoundParameters.ContainsKey('Status')) { $Status } else { [string]$old.status }
        priority = if ($Priority -ge 0) { $Priority } else { [int]$old.priority }
        file     = [string]$old.file
        created  = [string]$old.created
        updated  = $now
    }
    if ($old.PSObject.Properties.Name -contains 'tier' -and $null -ne $old.tier -and "$($old.tier)" -ne '') {
        $updated | Add-Member -NotePropertyName 'tier' -NotePropertyValue ([int]$old.tier)
    }
    $fixedKeys = @('id','name','status','priority','tier','file','created','updated')
    foreach ($prop in $old.PSObject.Properties) {
        if ($fixedKeys -notcontains $prop.Name -and -not ($updated.PSObject.Properties.Name -contains $prop.Name)) {
            $updated | Add-Member -NotePropertyName $prop.Name -NotePropertyValue $prop.Value
        }
    }

    try { Assert-Record -Record $updated }
    catch { $errors.Add("$ticketId : $($_.Exception.Message)") }

    $allRecords[$idx] = $updated
    $results.Add(("{0} {1} -> {2} (priority: {3})" -f $ticketId, $oldStatus, $updated.status, $updated.priority))
}

if ($errors.Count -gt 0) {
    foreach ($e in $errors) { Write-Error $e }
    exit 1
}

# Phase 3: atomic writes, routing newly-Archived records into the archive journal.
# (Reviving an archived id is out of scope for bulk - use update.ps1 for that.)
$archivedNow = @($allRecords | Where-Object { $_.status -eq 'Archived' })
$activeNow   = @($allRecords | Where-Object { $_.status -ne 'Archived' })
if ($archivedNow.Count -gt 0) {
    $movingIds = @($archivedNow | ForEach-Object { $_.id })
    $existingArchive = Read-JsonlFile -Path (Get-ArchivePath)
    $mergedArchive = @($existingArchive | Where-Object { $movingIds -notcontains $_.id }) + $archivedNow
    Write-ArchiveCatalog -Records ([object[]]$mergedArchive)
}
Write-Catalog -Records ([object[]]$activeNow)

# Phase 4: mirror each new status into its spec file's **Status:** header so the
# in-file header never drifts from the journal (shared fail-soft helper; only on
# status changes - a priority-only batch leaves headers untouched).
if ($PSBoundParameters.ContainsKey('Status')) {
    foreach ($ticketId in $Id) {
        $rec = $allRecords[$indexMap[$ticketId]]
        [void](Sync-SpecHeaderStatus -PathRef $rec.file -Status $rec.status)
    }
}

Exit-CatalogLock

foreach ($line in $results) { Write-Output $line }
exit 0
