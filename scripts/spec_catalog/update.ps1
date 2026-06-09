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
    [int]    $Priority = -1,
    [string] $StatusNote = $null   # non-empty: set note; empty '': clear note; $null/omit: preserve
)

# Convert terminating errors (Write-Error, throw, provider errors) into
# the documented `exit 1` so callers can rely on $LASTEXITCODE.
trap {
    Write-Host $_ -ForegroundColor Red
    exit 1
}

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
$fixedKeys = @('id','name','status','priority','tier','file','created','updated')
foreach ($prop in $old.PSObject.Properties) {
    if ($fixedKeys -notcontains $prop.Name -and -not ($updated.PSObject.Properties.Name -contains $prop.Name)) {
        $updated | Add-Member -NotePropertyName $prop.Name -NotePropertyValue $prop.Value
    }
}

if ($PSBoundParameters.ContainsKey('Status'))   { $updated.status   = $Status }
if ($PSBoundParameters.ContainsKey('Name'))     { $updated.name     = $Name }
if ($PSBoundParameters.ContainsKey('File'))     { $updated.file     = ($File -replace '\\','/') }
if ($PSBoundParameters.ContainsKey('Priority')) { $updated.priority = $Priority }

# StatusNote: explicit non-null value updates the record; auto-clear when leaving Block* without a note.
$resolvedNote = $null   # $null = don't touch header note; '' = clear; non-empty = set
if ($PSBoundParameters.ContainsKey('StatusNote')) {
    $resolvedNote = $StatusNote   # caller's explicit intent (may be '' to clear)
    if ($StatusNote -ne '') {
        if ($updated.PSObject.Properties.Name -contains 'statusNote') { $updated.statusNote = $StatusNote }
        else { $updated | Add-Member -NotePropertyName 'statusNote' -NotePropertyValue $StatusNote }
    } else {
        # Explicit clear: remove field from record
        if ($updated.PSObject.Properties.Name -contains 'statusNote') {
            $updated.PSObject.Properties.Remove('statusNote')
        }
    }
} elseif ($PSBoundParameters.ContainsKey('Status') -and $oldStatus -like 'Block*' -and $updated.status -notlike 'Block*') {
    # Leaving a Block* status without a new note: auto-clear so stale notes don't linger.
    $resolvedNote = ''
    if ($updated.PSObject.Properties.Name -contains 'statusNote') {
        $updated.PSObject.Properties.Remove('statusNote')
    }
}
if ($Tier -ge 0) {
    if ($updated.PSObject.Properties.Name -contains 'tier') { $updated.tier = $Tier }
    else { $updated | Add-Member -NotePropertyName 'tier' -NotePropertyValue $Tier }
}

# Owner-Inputs gate: any transition INTO Approved requires the spec file's
# §3.3 "Owner inputs (Approval gate)" subsection to be present and every bullet
# inside it to carry a concrete value. The set of bullets is decided by /spec
# at draft time based on the spec's detected character (relevance-driven; not
# a hardcoded list). Universally required: 'Related tickets'.
# The gate runs only on the specific transition $oldStatus -> Approved.
if ($PSBoundParameters.ContainsKey('Status') -and $Status -eq 'Approved' -and $oldStatus -ne 'Approved') {
    $checker = Join-Path $PSScriptRoot 'check-owner-inputs.ps1'
    if (Test-Path $checker) {
        $checkerOutput = & $checker -Id $Id 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host ""
            Write-Host "Owner-Inputs gate blocked promotion of $Id -> Approved:" -ForegroundColor Yellow
            $checkerOutput | ForEach-Object { Write-Host $_ }
            Write-Host ""
            throw ("Cannot promote '{0}' to Approved: §3.3 Owner inputs incomplete. Fill every bullet present in §3.3 with a concrete value (placeholders in square brackets are rejected), ensure 'Related tickets' is present, then re-run update.ps1." -f $Id)
        }
    }
}

$updated.updated = Get-Now

Assert-Record -Record $updated

$records[$idx] = $updated
Write-Catalog -Records $records.ToArray()

# Mirror the new status into the spec file's human-readable **Status:** header so
# it never drifts from the journal (the owner reads that header directly). Shared
# fail-soft helper in _lib.ps1; only the first header line is touched, deeper
# ADR / Proposal **Status:** lines are left alone. The journal write above is the
# source of truth and is never rolled back by a header problem.
$statusChanged = $PSBoundParameters.ContainsKey('Status') -and $oldStatus -ne $updated.status
$noteExplicit  = $PSBoundParameters.ContainsKey('StatusNote')
if ($statusChanged -or $noteExplicit) {
    if (Sync-SpecHeaderStatus -PathRef $updated.file -Status $updated.status -StatusNote $resolvedNote) {
        $noteHint = if ($null -ne $resolvedNote -and $resolvedNote -ne '') { ' + note' } elseif ($resolvedNote -eq '') { ' (note cleared)' } else { '' }
        Write-Host ("  header synced -> {0}{1}" -f $updated.status, $noteHint) -ForegroundColor DarkGray
    }
}

Write-Output ("{0} {1} -> {2}" -f $Id, $oldStatus, $updated.status)
exit 0
