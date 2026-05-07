[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') { throw "Invalid -Id '$Id' (must match S####)." }

$record = Find-Record -Id $Id
if (-not $record) {
    Write-Error "Record '$Id' not found."
    exit 1
}
if ($record.status -eq 'Archived') {
    Write-Error "$Id is already Archived."
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
$specFile     = Join-Path $planDir "${Id}_${slug}.md"
$tacticalDir  = Join-Path $planDir "${Id}_${slug}"

$moved = New-Object System.Collections.Generic.List[string]

if (Test-Path $specFile) {
    Move-Item -LiteralPath $specFile -Destination (Join-Path $doneDir "${Id}_${slug}.md") -Force
    $moved.Add("${Id}_${slug}.md")
} else {
    Write-Warning "Strategic file not found at PLAN/${Id}_${slug}.md — skipping file move."
}

if (Test-Path $tacticalDir) {
    Move-Item -LiteralPath $tacticalDir -Destination (Join-Path $doneDir "${Id}_${slug}") -Force
    $moved.Add("${Id}_${slug}/")
}

# Mark Archived in journal, preserve optional fields
$allRecords = [System.Collections.Generic.List[object]]::new()
foreach ($r in (Read-Catalog)) { $allRecords.Add($r) }

$idx = -1
for ($i = 0; $i -lt $allRecords.Count; $i++) {
    if ($allRecords[$i].id -eq $Id) { $idx = $i; break }
}

$old = $allRecords[$idx]
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
$allRecords[$idx] = $archived
Write-Catalog -Records $allRecords.ToArray()

$movedStr = if ($moved.Count -gt 0) { $moved -join ', ' } else { '(no files found)' }
Write-Output ("$Id archived [priority -> 0]. Moved: $movedStr -> temp/done/")
