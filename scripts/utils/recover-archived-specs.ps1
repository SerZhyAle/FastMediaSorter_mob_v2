#requires -Version 7.0
<#
.SYNOPSIS
    S2592 one-shot repair: restore archived spec artefacts that still exist in git history.

.DESCRIPTION
    PLAN/archive/ was under version control for eleven days - S1620 re-included it on
    2026-08-13, and the commit of 2026-08-24 22:47:54 ("Stop tracking the local PLAN workspace
    and archives") removed it again, taking the WORKING COPIES with it, not just the index.
    Measured 2026-09-05: 1893 of 2283 records in PLAN/spec-catalog-archive.jsonl point at a file
    that is no longer on disk, and 1746 of those are still reachable inside that eleven-day
    window. This script brings them back.

    Scope is PLAN/archive/ and nothing else. A historical PLAN/Sxxxx_<slug>.md path is NOT
    restored: an active spec that was later renamed would come back as a stale duplicate beside
    the live file, which is a second defect rather than a repair.

    Method - one tree read per candidate commit instead of one git call per file. Only 112
    commits ever touched PLAN, so the newest tree carrying each path is found by walking those
    112 trees newest-first; the per-path alternative was 1746 `git rev-list` invocations over a
    repository this size. Content then comes out through `git archive --format=zip` into a
    staging directory and is copied in file by file, so nothing already on disk is overwritten and
    the git INDEX is never touched - `git checkout <sha> -- <path>` would have staged the paths
    and re-tracked the very directory the owner untracked on 2026-08-24.

    Zip rather than tar, and that is not cosmetic: extraction used to shell out to whichever `tar`
    the caller's PATH resolved, which from the Bash tool is the MSYS build. That build rejects a
    Windows `-C P:\..` destination and exits non-zero, so the recovery reported "tar extraction
    failed" from one shell and passed from another over the same commit.
    `[System.IO.Compression.ZipFile]` runs in-process and cannot diverge that way.

.PARAMETER DryRun
    Report what would be restored and write nothing.

.PARAMETER StagingDir
    Where the extraction lands. Defaults to temp/S2592/restore, removed on success.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/recover-archived-specs.ps1 -DryRun

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - recovery complete, or the dry run reported its plan.
      1 - at least one file could not be written.
      2 - could not run: the archive journal or git is unavailable.
#>
[CmdletBinding()]
param(
    [switch]$DryRun,
    [string]$StagingDir
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$journal = Join-Path $repoRoot 'PLAN\spec-catalog-archive.jsonl'
if (-not (Test-Path -LiteralPath $journal)) {
    Write-Error "recover-archived-specs: archive journal not found - $journal" -ErrorAction Continue
    exit 2
}
if (-not (Get-Command 'git' -ErrorAction SilentlyContinue)) {
    Write-Error "recover-archived-specs: 'git' is not on PATH - cannot recover." -ErrorAction Continue
    exit 2
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (-not $StagingDir) { $StagingDir = Join-Path $repoRoot 'temp\S2592\restore' }

Push-Location -LiteralPath $repoRoot
try {
    $records = @(Get-Content -LiteralPath $journal -Encoding utf8 |
        Where-Object { $_.Trim() -ne '' } | ForEach-Object { $_ | ConvertFrom-Json })

    $missingRecords = @($records | Where-Object {
        -not (Test-Path -LiteralPath (Join-Path $repoRoot (([string]$_.file) -replace '/', '\')))
    })
    Write-Host "archive records: $($records.Count)   artefact missing: $($missingRecords.Count)"
    if ($missingRecords.Count -eq 0) {
        Write-Host 'recover-archived-specs: nothing to do - every record resolves.' -ForegroundColor Green
        exit 0
    }

    # ---- newest tree carrying each PLAN/archive path -------------------------------------
    $commits = @(git log --all --format=%H -- PLAN 2>$null | Where-Object { $_ -and $_.Trim() -ne '' })
    if ($commits.Count -eq 0) {
        Write-Error 'recover-archived-specs: git history carries no PLAN path - nothing to recover from.' -ErrorAction Continue
        exit 2
    }
    Write-Host "commits touching PLAN: $($commits.Count) - reading their trees newest first .."

    $sourceOf = @{}
    foreach ($sha in $commits) {
        foreach ($path in (git ls-tree -r --name-only $sha -- PLAN/archive 2>$null)) {
            if ($path -and -not $sourceOf.ContainsKey($path)) { $sourceOf[$path] = $sha }
        }
    }
    Write-Host "distinct PLAN/archive paths in history: $($sourceOf.Count)"

    $needed = @($sourceOf.Keys | Where-Object {
        -not (Test-Path -LiteralPath (Join-Path $repoRoot ($_ -replace '/', '\')))
    })
    Write-Host "absent on disk and recoverable: $($needed.Count)"

    # A record counts as recovered when its own `file` is among the paths about to be written.
    $neededSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$needed)
    $recoverableRecords = @($missingRecords | Where-Object { $neededSet.Contains([string]$_.file) })
    $lostRecords = @($missingRecords | Where-Object { -not $neededSet.Contains([string]$_.file) })
    Write-Host "missing records recovered by this run: $($recoverableRecords.Count)"
    Write-Host "missing records history does not carry: $($lostRecords.Count)"

    if ($DryRun) {
        Write-Host ''
        Write-Host 'unrecoverable ids:' -ForegroundColor Yellow
        $lostRecords | Sort-Object { [string]$_.id } | ForEach-Object {
            Write-Host ("  {0}  {1}" -f $_.id, $_.file)
        }
        Write-Host ''
        Write-Host 'recover-archived-specs: DRY RUN - nothing was written.' -ForegroundColor Yellow
        exit 0
    }
    if ($needed.Count -eq 0) {
        Write-Host 'recover-archived-specs: history carries nothing that is absent on disk.' -ForegroundColor Yellow
        exit 0
    }

    # ---- extract, then copy only what is absent -------------------------------------------
    if (Test-Path -LiteralPath $StagingDir) { Remove-Item -LiteralPath $StagingDir -Recurse -Force }
    New-Item -ItemType Directory -Path $StagingDir -Force | Out-Null

    $byCommit = $needed | Group-Object { $sourceOf[$_] }
    $written = 0
    $failures = [System.Collections.Generic.List[string]]::new()

    foreach ($group in $byCommit) {
        $sha = $group.Name
        $zipPath = Join-Path $StagingDir "$sha.zip"
        # The whole PLAN/archive subtree in one call: a per-path argument list for 1746 paths
        # exceeds the command-line limit, and the surplus files are filtered out below anyway.
        git archive --format=zip -o $zipPath $sha PLAN/archive 2>$null
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $zipPath)) {
            $failures.Add("git archive failed for $sha")
            continue
        }
        $extractDir = Join-Path $StagingDir $sha
        New-Item -ItemType Directory -Path $extractDir -Force | Out-Null
        try { [System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, $extractDir) }
        catch { $failures.Add("zip extraction failed for ${sha}: $($_.Exception.Message)"); continue }

        foreach ($path in $group.Group) {
            $src = Join-Path $extractDir ($path -replace '/', '\')
            $dst = Join-Path $repoRoot ($path -replace '/', '\')
            if (-not (Test-Path -LiteralPath $src)) { $failures.Add("not in archive of ${sha}: $path"); continue }
            if (Test-Path -LiteralPath $dst) { continue }
            try {
                $parent = Split-Path -Parent $dst
                if (-not (Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
                Copy-Item -LiteralPath $src -Destination $dst
                $written++
            }
            catch { $failures.Add("write failed: $path - $($_.Exception.Message)") }
        }
        Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
    }

    Write-Host ''
    Write-Host "restored files: $written"
    Write-Host "unrecoverable records: $($lostRecords.Count)"
    $lostRecords | Sort-Object { [string]$_.id } | ForEach-Object {
        Write-Host ("  {0}  {1}" -f $_.id, $_.file) -ForegroundColor DarkGray
    }

    if ($failures.Count -gt 0) {
        Write-Host ''
        foreach ($f in $failures) { Write-Host "  FAIL: $f" -ForegroundColor Red }
        Write-Error ("recover-archived-specs: FAIL - {0} problem(s)." -f $failures.Count) -ErrorAction Continue
        exit 1
    }

    Remove-Item -LiteralPath $StagingDir -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "recover-archived-specs: PASS - $written file(s) restored." -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
