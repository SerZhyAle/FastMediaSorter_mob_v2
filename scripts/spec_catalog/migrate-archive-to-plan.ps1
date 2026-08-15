[CmdletBinding()]
param(
    [switch] $Apply,
    [switch] $Help
)

# One-shot migration for S1620: move the closed-spec archive from the disposable,
# git-ignored temp/done/ into version-controlled PLAN/archive/, and repair the journal
# records whose `file` still points at the PLAN/ path the spec occupied before archiving.
#
# Background: archive.ps1 used to move the artefact but keep the original `file` value,
# so 1502 of 1504 archived records named a path that no longer existed. Both halves are
# fixed here - the files move, and every archived record is re-pointed at where its file
# actually ends up.
#
# Idempotent: a record already pointing into PLAN/archive/ with the file present is left
# alone, so a re-run after a partial migration completes the remainder instead of undoing it.
# Dry-run by default; pass -Apply to write.
#
# Exit codes: 0 - migration reported (or applied) with no failure; 1 - at least one record
# could not be migrated; 2 - the migration cannot run (journal or source directory missing).

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/spec_catalog/migrate-archive-to-plan.ps1'
    exit $LASTEXITCODE
}

. (Join-Path $PSScriptRoot '_lib.ps1')

$repoRoot   = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sourceDir  = Join-Path $repoRoot 'temp\done'
$targetDir  = Join-Path $repoRoot 'PLAN\archive'
$archiveJournal = Get-ArchivePath

if (-not (Test-Path -LiteralPath $archiveJournal)) {
    Write-Error "Archive journal not found: $archiveJournal" -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $sourceDir)) {
    Write-Error "Source directory not found: $sourceDir (nothing to migrate)." -ErrorAction Continue
    exit 2
}

# ForEach-Object, not @(..): Read-JsonlFile hands back one array-valued object, so @()
# wraps that single value instead of unrolling it and the loop below would see 1 "record"
# whose every field is the whole column.
$records = Read-JsonlFile -Path $archiveJournal | ForEach-Object { $_ }
if ($records.Count -eq 0) {
    Write-Output 'Archive journal is empty - nothing to migrate.'
    exit 0
}

$mode = if ($Apply) { 'APPLY' } else { 'DRY-RUN' }
Write-Output "migrate-archive-to-plan [$mode]: $($records.Count) archived record(s)"

$moved    = 0
$repointed = 0
$already  = 0
$missing  = New-Object System.Collections.Generic.List[string]

if ($Apply -and -not (Test-Path -LiteralPath $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
}

foreach ($r in $records) {
    $leaf = Split-Path -Path ([string]$r.file) -Leaf
    $stem = [System.IO.Path]::GetFileNameWithoutExtension($leaf)

    $targetFile = Join-Path $targetDir $leaf
    $sourceFile = Join-Path $sourceDir $leaf

    # Already migrated: record points into the archive and the file is there.
    if (([string]$r.file) -like 'PLAN/archive/*' -and (Test-Path -LiteralPath $targetFile)) {
        $already++
        continue
    }

    if (-not (Test-Path -LiteralPath $sourceFile)) {
        # The artefact is gone (swept long ago). Nothing to move and no honest path to
        # record - leave the stale reference rather than inventing one that also fails.
        $missing.Add(('{0} ({1})' -f $r.id, $leaf))
        continue
    }

    if ($Apply) {
        Move-Item -LiteralPath $sourceFile -Destination $targetFile -Force
        # The tactical folder travels with its spec; it carries the phase files and their
        # verification, which is most of what makes a closed ticket worth keeping.
        $sourceTactical = Join-Path $sourceDir $stem
        if (Test-Path -LiteralPath $sourceTactical -PathType Container) {
            Move-Item -LiteralPath $sourceTactical -Destination (Join-Path $targetDir $stem) -Force
        }
        $r.file = 'PLAN/archive/' + $leaf
    }
    $moved++
    $repointed++
}

if ($Apply) {
    Write-ArchiveCatalog -Records ([object[]]$records)
}

Write-Output ("  moved:      {0}" -f $moved)
Write-Output ("  re-pointed: {0}" -f $repointed)
Write-Output ("  already ok: {0}" -f $already)
Write-Output ("  artefact missing (left as-is): {0}" -f $missing.Count)
foreach ($m in $missing) { Write-Output "    - $m" }

if (-not $Apply) {
    Write-Output ''
    Write-Output 'DRY-RUN - nothing was written. Re-run with -Apply to perform the migration.'
}

# A missing artefact is a pre-existing loss, not a migration failure: the run did
# everything that was still possible, so it must not report a red exit.
exit 0
