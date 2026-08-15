# PositionalBinding = $false (S1504): a stray unnamed token is never intentional here, and the
# first free positional slot is $Name - so it renamed the ticket instead of failing. Backslash is
# not an escape character in PowerShell, so a note written `.. tagged \"S1474:\" ..` closes its
# string early and the remainder reaches the parameter binder as separate arguments. With
# positional binding off they die at bind time instead of overwriting a field.
[CmdletBinding(PositionalBinding = $false)]
param(
    # Not [Parameter(Mandatory)]: a mandatory parameter makes the host prompt before the
    # body runs, so -Help could never print. Absence is reported explicitly below instead.
    [string] $Id,
    [ValidateSet('Draft','Approved','Tactical','In Progress',
        'Implemented','Verified','Partial','Broken',
        'BlockByOtherTask','BlockNeedUserTest','BlockQuestions','BlockExternal',
        'Archived')]
    [string] $Status,
    [string] $Name,
    [string] $File,
    [int]    $Tier = -1,
    [int]    $Priority = -1,
    [string] $StatusNote = $null,  # non-empty: set note; empty '': clear note; $null/omit: preserve
    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/spec_catalog/update.ps1'
    exit $LASTEXITCODE
}
if (-not $Id) {
    Write-Error 'update.ps1 requires -Id <Sxxxx>. Run with -Help for the parameter list.' -ErrorAction Continue
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
if ($PSBoundParameters.ContainsKey('Priority')) {
    if ($Priority -lt 0 -or $Priority -gt 100) {
        throw "Invalid -Priority '$Priority' (must be 0..100)."
    }
}

# S1582: explicit acceptance-probe contracts are source-backed at write time.
# Unmarked prose remains free-form, so historic status notes are not reinterpreted.
if ($PSBoundParameters.ContainsKey('StatusNote') -and $StatusNote -ne '' -and
    $StatusNote -match '(?im)^\s*Probe\s+(literal|template|none)\s*:') {
    $repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $helperPath = Join-Path $repoRoot 'scripts/quality/lib/ticket-acceptance-probes.ps1'
    $sourceRoots = @((Join-Path $repoRoot 'app_v2/src'), (Join-Path $repoRoot 'wear/src'))
    if (-not (Test-Path -LiteralPath $helperPath) -or @($sourceRoots | Where-Object { -not (Test-Path -LiteralPath $_) }).Count -gt 0) {
        throw 'Cannot validate acceptance-probe contract: helper or source roots are unavailable.'
    }
    . $helperPath
    $probeResults = @(Test-TicketAcceptanceProbeNote -Ticket $Id -StatusNote $StatusNote -SourceRoots $sourceRoots)
    $invalidProbe = @($probeResults | Where-Object { $_.Outcome -ne 'pass' })
    if ($invalidProbe.Count -gt 0) {
        $reasons = ($invalidProbe | ForEach-Object { $_.Outcome }) -join ', '
        throw "Invalid acceptance-probe contract for ${Id}: $reasons."
    }
}

# Resolve from the active journal first, then the archive journal (so updating
# or reviving an Archived ticket still works under the split).
$records = [System.Collections.Generic.List[object]]::new()
# S1437: read -> mutate -> write is one critical section. Two processes holding the same
# snapshot lose one change entirely - the later write replaces the whole journal.
Enter-CatalogLock
foreach ($r in (Read-Catalog)) { $records.Add($r) }

$idx = -1
for ($i = 0; $i -lt $records.Count; $i++) {
    if ($records[$i].id -eq $Id) { $idx = $i; break }
}

$fromArchive = $false
$old = $null
if ($idx -ge 0) {
    $old = $records[$idx]
} else {
    foreach ($r in (Read-JsonlFile -Path (Get-ArchivePath))) {
        if ($r.id -eq $Id) { $old = $r; $fromArchive = $true; break }
    }
}
if ($null -eq $old) { throw "Record '$Id' not found." }

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

# Closing gates: any transition INTO a closed status runs the durable-evidence contract
# (S1606) and the carried-open-item contract (S1607). Both live in Assert-ClosingGates so
# this path and close.ps1 enforce the same set - a second copy here is what let close.ps1
# drift into enforcing nothing at all.
if ($PSBoundParameters.ContainsKey('Status')) {
    Assert-ClosingGates -Id $Id -OldStatus $oldStatus -NewStatus $Status
}

$updated.updated = Get-Now

Assert-Record -Record $updated

# Route the write to the journal that matches the NEW status, handling all four
# transitions (active<->active, active->archive, archive->active, archive->archive).
$newIsArchived = ($updated.status -eq 'Archived')
if ($newIsArchived) {
    Add-ArchiveRecord -Record $updated                       # upsert into archive
    if (-not $fromArchive) {                                 # was active: drop it there
        $remaining = @($records | Where-Object { $_.id -ne $Id })
        Write-Catalog -Records ([object[]]$remaining)
    }
} else {
    if ($fromArchive) {                                      # revive: archive -> active
        $archKeep = @((Read-JsonlFile -Path (Get-ArchivePath)) | Where-Object { $_.id -ne $Id })
        Write-ArchiveCatalog -Records ([object[]]$archKeep)
        $records.Add($updated)
        Write-Catalog -Records $records.ToArray()
    } else {                                                 # in-place active update
        $records[$idx] = $updated
        Write-Catalog -Records $records.ToArray()
    }
}

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

Exit-CatalogLock

Write-Output ("{0} {1} -> {2}" -f $Id, $oldStatus, $updated.status)
exit 0
