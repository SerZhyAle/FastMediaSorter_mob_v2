<#
.SYNOPSIS
  Insert a new spec catalog record. Supply the spec path with -File, or with
  -Slug to auto-build PLAN/Sxxxx_<slug>.md from the freshly allocated id.
.NOTES
  Exit codes: 0 ok; 1 error (bad call shape, invalid slug, duplicate id,
  active name clash, or record validation failure).
#>
[CmdletBinding()]
param(
    # Not [Parameter(Mandatory)]: a mandatory parameter makes the host prompt before the
    # body runs, so -Help could never print. Absence is reported explicitly below instead.
    [string] $Name,
    [string] $File,
    [ValidateSet('Draft','Approved','Tactical','In Progress',
        'Implemented','Verified','Partial','Broken',
        'BlockByOtherTask','BlockNeedUserTest','BlockQuestions','BlockExternal',
        'Archived')]
    [string] $Status = 'Draft',
    [int]    $Tier   = -1,
    [ValidateRange(0,100)]
    [int]    $Priority = 50,
    [string] $Id,
    # Alternative to -File: build PLAN/Sxxxx_<slug>.md after id allocation,
    # collapsing the old next-id.ps1 + insert.ps1 two-step into one call.
    [string] $Slug,
    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/spec_catalog/insert.ps1'
    exit $LASTEXITCODE
}
if (-not $Name) {
    Write-Error 'insert.ps1 requires -Name <ticket name>. Run with -Help for the parameter list.' -ErrorAction Continue
    exit 1
}

# Convert terminating errors (Write-Error, throw, provider errors) into
# the documented `exit 1` so callers can rely on $LASTEXITCODE.
trap {
    Write-Host $_ -ForegroundColor Red
    exit 1
}

. (Join-Path $PSScriptRoot '_lib.ps1')

# -File and -Slug are mutually exclusive; exactly one supplies the spec path.
# Validated up front so a bad call shape fails before New-CatalogId burns an id.
if ($File -and $Slug)          { throw "Provide either -File or -Slug, not both." }
if (-not $File -and -not $Slug) { throw "Provide -File <path> or -Slug <slug>." }
if ($Slug) {
    if ($Slug -notmatch '^[a-z0-9][a-z0-9_-]*$') {
        throw "Invalid -Slug '$Slug' - lowercase [a-z0-9_-], must start alphanumeric."
    }
    if ($Slug -match '^spec_') { throw "Invalid -Slug '$Slug' - must not start with 'spec_'." }
}

# S1437: the read, the id allocation, the duplicate check and the write are one critical section.
# New-CatalogId is "max + 1" with no reservation, so two concurrent inserts outside this lock can
# compute the same id, and the later write would drop the earlier record entirely.
# Released on the success path below; a throw hits the trap above and exits, and the OS releases
# the mutex with the process.
Enter-CatalogLock

$records = Read-Catalog

if (-not $Id) {
    $Id = New-CatalogId
} else {
    if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }
}

# -Slug path is built now that the id is final (e.g. S0123 -> PLAN/S0123_<slug>.md).
if ($Slug) { $File = 'PLAN/{0}_{1}.md' -f $Id, $Slug }

# Id uniqueness is global (an archived id must never be reissued); name clash is
# checked against active records only below (archived names may be reused).
if (Find-Record -Id $Id) { throw "Duplicate id '$Id'." }
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

Exit-CatalogLock

Write-Output $Id
exit 0
