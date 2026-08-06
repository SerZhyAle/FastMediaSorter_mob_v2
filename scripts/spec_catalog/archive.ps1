[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

# Under _lib.ps1's $ErrorActionPreference = 'Stop', Write-Error/throw raise
# terminating errors that abort the script before reaching `exit 1`. The trap
# below converts any such terminating error into a proper exit-1 contract,
# so callers can rely on $LASTEXITCODE.
trap {
    Write-Host $_ -ForegroundColor Red
    exit 1
}

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }

$record = Find-Record -Id $Id
if (-not $record) {
    Write-Error "Record '$Id' not found."
    exit 1
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$doneDir  = Join-Path $repoRoot 'temp\done'
if (-not (Test-Path $doneDir)) {
    New-Item -ItemType Directory -Path $doneDir -Force | Out-Null
}

# Locate artefacts in PLAN/
$planDir      = Join-Path $repoRoot 'PLAN'
$slug         = $record.name
# Prefer the catalog path because some specs were renamed after ticket creation.
$recordFileRelative = if ($record.PSObject.Properties.Name -contains 'file' -and "$($record.file)" -ne '') {
    [string]$record.file
} else {
    "PLAN/${Id}_${slug}.md"
}
$recordFilePath = Join-Path $repoRoot ($recordFileRelative -replace '/', '\')
$fallbackSpecFile = Join-Path $planDir "${Id}_${slug}.md"

$candidateSpecFiles = [System.Collections.Generic.List[string]]::new()
$candidateSpecFiles.Add($recordFilePath)
if ($fallbackSpecFile -ne $recordFilePath) {
    $candidateSpecFiles.Add($fallbackSpecFile)
}

$specFile = $null
foreach ($candidate in $candidateSpecFiles) {
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        $specFile = $candidate
        break
    }
}

$candidateTacticalDirs = [System.Collections.Generic.List[string]]::new()
# Strip the extension to get the tactical folder path; ChangeExtension($p, $null)
# yields "path." in PowerShell 7+ which then fails to resolve, so build it manually.
$recordTacticalDir = Join-Path `
    ([System.IO.Path]::GetDirectoryName($recordFilePath)) `
    ([System.IO.Path]::GetFileNameWithoutExtension($recordFilePath))
$candidateTacticalDirs.Add($recordTacticalDir)
$fallbackTacticalDir = Join-Path $planDir "${Id}_${slug}"
if ($fallbackTacticalDir -ne $recordTacticalDir) {
    $candidateTacticalDirs.Add($fallbackTacticalDir)
}

$tacticalDirs = [System.Collections.Generic.List[string]]::new()
foreach ($candidate in $candidateTacticalDirs) {
    if (Test-Path -LiteralPath $candidate -PathType Container) {
        $tacticalDirs.Add($candidate)
    }
}

if ($record.status -eq 'Archived' -and $null -eq $specFile -and $tacticalDirs.Count -eq 0) {
    Write-Error "$Id is already Archived."
    exit 1
}
if ($record.status -eq 'Archived') {
    Write-Warning "$Id is already Archived in the catalog; continuing to move remaining artefacts from PLAN/."
}

$moved = New-Object System.Collections.Generic.List[string]

if ($null -ne $specFile) {
    # Set the in-file header to Archived before moving, so the artefact in
    # temp/done/ matches the journal (shared fail-soft helper, first line only).
    [void](Sync-SpecHeaderStatus -PathRef $specFile -Status 'Archived')
    $specFileName = Split-Path -Path $specFile -Leaf
    Move-Item -LiteralPath $specFile -Destination (Join-Path $doneDir $specFileName) -Force
    $moved.Add($specFileName)
} else {
    Write-Warning "Strategic file not found in PLAN for '$recordFileRelative' - skipping file move."
}

foreach ($tacticalDir in $tacticalDirs) {
    $tacticalDirName = Split-Path -Path $tacticalDir -Leaf
    Move-Item -LiteralPath $tacticalDir -Destination (Join-Path $doneDir $tacticalDirName) -Force
    $moved.Add("${tacticalDirName}/")
}

# Mark Archived in journal, preserve optional fields
$allRecords = [System.Collections.Generic.List[object]]::new()
# S1437: read -> mutate -> write is one critical section. Two processes holding the same
# snapshot lose one change entirely - the later write replaces the whole journal.
Enter-CatalogLock
foreach ($r in (Read-Catalog)) { $allRecords.Add($r) }

$idx = -1
for ($i = 0; $i -lt $allRecords.Count; $i++) {
    if ($allRecords[$i].id -eq $Id) { $idx = $i; break }
}

# When the id is no longer in the active journal (already moved to archive),
# fall back to the record resolved earlier via Find-Record's archive fallback.
$old = if ($idx -ge 0) { $allRecords[$idx] } else { $record }
$archived = [pscustomobject]@{
    id       = [string]$old.id
    name     = [string]$old.name
    status   = 'Archived'
    priority = 0
    file     = [string]$old.file
    created  = [string]$old.created
    updated  = (Get-Now)
}
if ($old.PSObject.Properties.Name -contains 'tier' -and $null -ne $old.tier -and "$($old.tier)" -ne '') {
    $archived | Add-Member -NotePropertyName 'tier' -NotePropertyValue ([int]$old.tier)
}
$fixedKeys = @('id','name','status','priority','tier','file','created','updated')
foreach ($prop in $old.PSObject.Properties) {
    if ($fixedKeys -notcontains $prop.Name -and -not ($archived.PSObject.Properties.Name -contains $prop.Name)) {
        $archived | Add-Member -NotePropertyName $prop.Name -NotePropertyValue $prop.Value
    }
}

Assert-Record -Record $archived
# Physically relocate: append to the archive journal, drop from the active one.
# Add-ArchiveRecord replaces by id (idempotent); active removal is a no-op if
# the id was already moved out.
Add-ArchiveRecord -Record $archived
$remaining = @($allRecords | Where-Object { $_.id -ne $Id })
Write-Catalog -Records ([object[]]$remaining)

$movedStr = if ($moved.Count -gt 0) { $moved -join ', ' } else { '(no files found)' }
Exit-CatalogLock

Write-Output ("$Id archived [priority -> 0]. Moved: $movedStr -> temp/done/")
exit 0
