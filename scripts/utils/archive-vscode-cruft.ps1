<#
.SYNOPSIS
  Reclaims disk space from VSCode / VSCode Insiders user data and Claude Code transcripts.

.DESCRIPTION
  VSCode user data grows from agent work, not from logs - logs are ~20 MB while chat
  sessions, their SQLite state and webview storage run into gigabytes. This script
  sweeps that growth in three tiers, cheapest and safest first.

  Tier 1 - pure caches, DELETED because VSCode regenerates them:
    CachedExtensionVSIXs (already-unpacked .vsix downloads), CachedData,
    CachedProfilesData, Cache, Code Cache, GPUCache, Dawn*Cache, Crashpad reports, logs.

  Tier 2 - history, MOVED to the archive root so it stays recoverable:
    state.vscdb.backup copies, chat sessions and chat editing sessions older than
    -ChatOlderThanDays, WebStorage partitions untouched for -WebStorageOlderThanDays,
    local file History older than -HistoryOlderThanDays, and workspaceStorage entries
    whose workspace folder no longer exists.

  Tier 3 - judgement calls:
    VACUUM on each state.vscdb (lossless compaction; needs sqlite3 on PATH), and
    Claude Code transcripts older than -ClaudeOlderThanDays moved aside. Installed
    extensions are only REPORTED, never removed - which editor assistant you keep is
    not a decision a cleanup script gets to make.

  Tiers are cumulative: -Tier 2 runs tiers 1 and 2.

.PARAMETER Tier
  Highest tier to run, 1..3. Default 1.

.PARAMETER ArchiveRoot
  Where tier 2/3 content is moved. Default: %LOCALAPPDATA%\vscode-cruft-archive\<stamp>.
  Deliberately outside the repository - this is machine-level data, not project scratch.

.PARAMETER ChatOlderThanDays
  Age threshold for chat sessions and chat editing sessions. Default 30.

.PARAMETER WebStorageOlderThanDays
  Age threshold for WebStorage partitions. Default 90.

.PARAMETER HistoryOlderThanDays
  Age threshold for local file history entries. Default 60.

.PARAMETER ClaudeOlderThanDays
  Age threshold for Claude Code project transcripts (tier 3). Default 30.

.PARAMETER DryRun
  Report the plan and exit without touching anything.

.PARAMETER Force
  Include an editor that is currently running. Not recommended: an open editor holds
  state.vscdb, so the space is not returned and the database can be left corrupt.
  Without it, a running editor is skipped and the others are still processed - this
  script is usually launched from a terminal inside one of the editors, so a running
  installation is the normal case rather than an error.

.EXAMPLE
  pwsh -NoProfile -File scripts/utils/archive-vscode-cruft.ps1 -Tier 2 -DryRun
  Shows what tiers 1 and 2 would reclaim.

.NOTES
  Exit codes:
    0 - completed (or dry run completed)
    1 - one or more entries could not be removed or moved
    2 - could not verify: no VSCode data directory found, or every installation is
        running so nothing could be touched safely
#>
[CmdletBinding()]
param(
    [ValidateRange(1, 3)]
    [int]$Tier = 1,
    [string]$ArchiveRoot,
    [int]$ChatOlderThanDays = 30,
    [int]$WebStorageOlderThanDays = 90,
    [int]$HistoryOlderThanDays = 60,
    [int]$ClaudeOlderThanDays = 30,
    [switch]$DryRun,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- discovery

$editors = @(
    [pscustomobject]@{ Name = 'Code (stable)';   Data = Join-Path $env:APPDATA 'Code';            Process = 'Code' }
    [pscustomobject]@{ Name = 'Code - Insiders'; Data = Join-Path $env:APPDATA 'Code - Insiders'; Process = 'Code - Insiders' }
) | Where-Object { Test-Path -LiteralPath $_.Data }

if ($editors.Count -eq 0) {
    Write-Error 'No VSCode user-data directory found under %APPDATA%.' -ErrorAction Continue
    exit 2
}

# Gate per editor, not globally: this script is normally invoked from a terminal inside
# one of the editors, so a global refusal would make that editor permanently unreachable
# while needlessly sparing the one that is closed.
$skipped = @()
foreach ($editor in $editors) {
    $editor | Add-Member -NotePropertyName Running -NotePropertyValue ([bool](Get-Process -Name $editor.Process -ErrorAction SilentlyContinue)) -Force
}
foreach ($editor in $editors | Where-Object Running) {
    if ($Force) {
        Write-Warning ("{0} is running and -Force was given - state.vscdb is held open, so its space will not be returned and the database may be left corrupt." -f $editor.Name)
    } else {
        $skipped += $editor.Name
    }
}
if ($skipped.Count -gt 0) {
    Write-Host ("Skipping (running): {0} - close it and re-run to reclaim its share." -f ($skipped -join ', '))
}

$targetEditors = @($editors | Where-Object { $Force -or -not $_.Running })
if ($targetEditors.Count -eq 0 -and $Tier -lt 3) {
    Write-Error 'Every VSCode installation is running - nothing can be reclaimed safely. Close an editor, or run from an external terminal with both closed.' -ErrorAction Continue
    exit 2
}

if (-not $ArchiveRoot) {
    $stamp = (Get-Date).ToString('yyyyMMdd_HHmmss')
    $ArchiveRoot = Join-Path $env:LOCALAPPDATA "vscode-cruft-archive\$stamp"
}

# ---------------------------------------------------------------- helpers

function Get-EntrySize {
    param([string]$Path)
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    if (-not $item) { return 0 }
    if (-not $item.PSIsContainer) { return $item.Length }
    return (Get-ChildItem -LiteralPath $Path -Recurse -File -Force -ErrorAction SilentlyContinue |
        Measure-Object -Property Length -Sum).Sum
}

$plan = [System.Collections.Generic.List[object]]::new()

function Add-Target {
    param(
        [string]$Path,
        [ValidateSet('delete', 'move')] [string]$Action,
        [string]$Group,
        [string]$Bucket = ''
    )
    if (-not (Test-Path -LiteralPath $Path)) { return }
    $plan.Add([pscustomobject]@{
        Path   = $Path
        Action = $Action
        Group  = $Group
        Bucket = $Bucket
        Bytes  = [long](Get-EntrySize -Path $Path)
    })
}

function Get-WorkspaceTarget {
    <# Resolves a workspaceStorage entry's workspace.json to a local path, or $null. #>
    param([string]$EntryPath)
    $json = Join-Path $EntryPath 'workspace.json'
    if (-not (Test-Path -LiteralPath $json)) { return $null }
    try {
        $parsed = Get-Content -LiteralPath $json -Raw -ErrorAction Stop | ConvertFrom-Json
    } catch {
        return $null
    }
    $uri = if ($parsed.folder) { $parsed.folder } else { $parsed.workspace }
    if (-not $uri) { return $null }
    if ($uri -notmatch '^file:///') { return $null }
    $local = [System.Uri]::UnescapeDataString(($uri -replace '^file:///', '')) -replace '/', '\'
    return $local
}

# ---------------------------------------------------------------- tier 1: caches

$cacheNames = @(
    'CachedExtensionVSIXs', 'CachedData', 'CachedProfilesData',
    'Cache', 'Code Cache', 'GPUCache', 'DawnGraphiteCache', 'DawnCache',
    'logs'
)
foreach ($editor in $targetEditors) {
    foreach ($name in $cacheNames) {
        Add-Target -Path (Join-Path $editor.Data $name) -Action delete -Group "T1 cache/$($editor.Name)"
    }
    foreach ($name in @('reports', 'new_reports', 'pending', 'completed')) {
        Add-Target -Path (Join-Path $editor.Data "Crashpad\$name") -Action delete -Group "T1 crashpad/$($editor.Name)"
    }
}

# ---------------------------------------------------------------- tier 2: history

if ($Tier -ge 2) {
    $chatCutoff = (Get-Date).AddDays(-$ChatOlderThanDays)
    $webCutoff = (Get-Date).AddDays(-$WebStorageOlderThanDays)
    $historyCutoff = (Get-Date).AddDays(-$HistoryOlderThanDays)

    foreach ($editor in $targetEditors) {
        $user = Join-Path $editor.Data 'User'
        $wsRoot = Join-Path $user 'workspaceStorage'

        if (Test-Path -LiteralPath $wsRoot) {
            foreach ($entry in Get-ChildItem -LiteralPath $wsRoot -Directory -Force -ErrorAction SilentlyContinue) {
                $target = Get-WorkspaceTarget -EntryPath $entry.FullName
                # An entry whose workspace folder is gone keeps no useful state.
                if ($target -and -not (Test-Path -LiteralPath $target)) {
                    Add-Target -Path $entry.FullName -Action move -Group "T2 dead-workspace/$($editor.Name)" -Bucket "workspaceStorage/$($entry.Name)"
                    continue
                }

                Add-Target -Path (Join-Path $entry.FullName 'state.vscdb.backup') -Action move -Group "T2 vscdb-backup/$($editor.Name)" -Bucket "workspaceStorage/$($entry.Name)"

                $chatDir = Join-Path $entry.FullName 'chatSessions'
                if (Test-Path -LiteralPath $chatDir) {
                    Get-ChildItem -LiteralPath $chatDir -File -Force -Filter *.jsonl -ErrorAction SilentlyContinue |
                        Where-Object { $_.LastWriteTime -lt $chatCutoff } |
                        ForEach-Object { Add-Target -Path $_.FullName -Action move -Group "T2 chatSessions/$($editor.Name)" -Bucket "workspaceStorage/$($entry.Name)/chatSessions" }
                }

                $editDir = Join-Path $entry.FullName 'chatEditingSessions'
                if (Test-Path -LiteralPath $editDir) {
                    Get-ChildItem -LiteralPath $editDir -Directory -Force -ErrorAction SilentlyContinue |
                        Where-Object { $_.LastWriteTime -lt $chatCutoff } |
                        ForEach-Object { Add-Target -Path $_.FullName -Action move -Group "T2 chatEditing/$($editor.Name)" -Bucket "workspaceStorage/$($entry.Name)/chatEditingSessions" }
                }
            }
        }

        Add-Target -Path (Join-Path $user 'globalStorage\state.vscdb.backup') -Action move -Group "T2 vscdb-backup/$($editor.Name)" -Bucket 'globalStorage'

        $webRoot = Join-Path $editor.Data 'WebStorage'
        if (Test-Path -LiteralPath $webRoot) {
            Get-ChildItem -LiteralPath $webRoot -Directory -Force -ErrorAction SilentlyContinue |
                Where-Object { $_.LastWriteTime -lt $webCutoff } |
                ForEach-Object { Add-Target -Path $_.FullName -Action move -Group "T2 WebStorage/$($editor.Name)" -Bucket 'WebStorage' }
        }

        $historyRoot = Join-Path $user 'History'
        if (Test-Path -LiteralPath $historyRoot) {
            Get-ChildItem -LiteralPath $historyRoot -Directory -Force -ErrorAction SilentlyContinue |
                Where-Object { $_.LastWriteTime -lt $historyCutoff } |
                ForEach-Object { Add-Target -Path $_.FullName -Action move -Group "T2 History/$($editor.Name)" -Bucket 'History' }
        }
    }
}

# ---------------------------------------------------------------- tier 3: judgement

$vacuumTargets = @()
if ($Tier -ge 3) {
    $claudeProjects = Join-Path $env:USERPROFILE '.claude\projects'
    if (Test-Path -LiteralPath $claudeProjects) {
        $claudeCutoff = (Get-Date).AddDays(-$ClaudeOlderThanDays)
        foreach ($project in Get-ChildItem -LiteralPath $claudeProjects -Directory -Force -ErrorAction SilentlyContinue) {
            # A session is a pair: <id>.jsonl plus an optional <id>/ directory holding
            # subagent and workflow transcripts. Age is judged on the newest of the two
            # so an active session is never split in half.
            $sessions = @{}
            foreach ($item in Get-ChildItem -LiteralPath $project.FullName -Force -ErrorAction SilentlyContinue) {
                $id = if ($item.PSIsContainer) { $item.Name } else { [System.IO.Path]::GetFileNameWithoutExtension($item.Name) }
                if (-not $sessions.ContainsKey($id)) { $sessions[$id] = [System.Collections.Generic.List[object]]::new() }
                $sessions[$id].Add($item)
            }
            foreach ($id in $sessions.Keys) {
                $parts = $sessions[$id]
                $newest = ($parts | ForEach-Object {
                    if ($_.PSIsContainer) {
                        $inner = Get-ChildItem -LiteralPath $_.FullName -Recurse -File -Force -ErrorAction SilentlyContinue |
                            Measure-Object -Property LastWriteTime -Maximum
                        if ($inner.Maximum) { $inner.Maximum } else { $_.LastWriteTime }
                    } else { $_.LastWriteTime }
                } | Measure-Object -Maximum).Maximum
                if ($newest -ge $claudeCutoff) { continue }
                foreach ($part in $parts) {
                    Add-Target -Path $part.FullName -Action move -Group 'T3 claude-transcripts' -Bucket "claude-projects/$($project.Name)"
                }
            }
        }
    }

    foreach ($editor in $targetEditors) {
        foreach ($db in @(
            (Join-Path $editor.Data 'User\globalStorage\state.vscdb'),
            (Join-Path $editor.Data 'User\workspaceStorage\*\state.vscdb')
        )) {
            $vacuumTargets += Get-ChildItem -Path $db -File -Force -ErrorAction SilentlyContinue
        }
    }
}

# ---------------------------------------------------------------- report

$totalMb = [math]::Round((($plan | Measure-Object -Property Bytes -Sum).Sum) / 1MB, 1)
Write-Host ''
Write-Host ("Plan: tier<={0}, {1} entries, {2} MB" -f $Tier, $plan.Count, $totalMb)
if ($plan.Count -gt 0) {
    $plan | Group-Object Group | ForEach-Object {
        [pscustomobject]@{
            MB      = [math]::Round((($_.Group | Measure-Object -Property Bytes -Sum).Sum) / 1MB, 1)
            Entries = $_.Count
            Action  = ($_.Group | Select-Object -First 1).Action
            Group   = $_.Name
        }
    } | Sort-Object MB -Descending | Format-Table -AutoSize | Out-String -Width 110 | Write-Host
}
Write-Host ("Deletions regenerate; moves land in: {0}" -f $ArchiveRoot)

if ($Tier -ge 3) {
    Write-Host ''
    Write-Host 'Tier 3 advisory - installed extensions are reported, never removed:'
    foreach ($editor in $editors) {
        $extRoot = if ($editor.Name -like '*Insiders*') {
            Join-Path $env:USERPROFILE '.vscode-insiders\extensions'
        } else {
            Join-Path $env:USERPROFILE '.vscode\extensions'
        }
        if (-not (Test-Path -LiteralPath $extRoot)) { continue }
        Get-ChildItem -LiteralPath $extRoot -Directory -Force -ErrorAction SilentlyContinue | ForEach-Object {
            [pscustomobject]@{ MB = [math]::Round((Get-EntrySize -Path $_.FullName) / 1MB, 1); Name = $_.Name }
        } | Sort-Object MB -Descending | Select-Object -First 5 |
            ForEach-Object { Write-Host ("  {0,8:N1} MB  {1}  [{2}]" -f $_.MB, $_.Name, $editor.Name) }
    }
    if ($vacuumTargets.Count -gt 0) {
        $sqlite = Get-Command sqlite3 -ErrorAction SilentlyContinue
        $vacuumMb = [math]::Round((($vacuumTargets | Measure-Object -Property Length -Sum).Sum) / 1MB, 1)
        if ($sqlite) {
            Write-Host ("  VACUUM candidates: {0} state.vscdb files, {1} MB now" -f $vacuumTargets.Count, $vacuumMb)
        } else {
            Write-Host ("  VACUUM skipped: sqlite3 not on PATH ({0} files, {1} MB could be compacted losslessly)" -f $vacuumTargets.Count, $vacuumMb)
        }
    }
}

if ($DryRun) {
    Write-Host ''
    Write-Host 'DryRun - nothing changed.'
    exit 0
}
if ($plan.Count -eq 0 -and $vacuumTargets.Count -eq 0) {
    Write-Host 'Nothing to reclaim.'
    exit 0
}

# ---------------------------------------------------------------- execute

$deleted = 0
$moved = 0
$failed = 0

foreach ($entry in $plan) {
    try {
        if ($entry.Action -eq 'delete') {
            Remove-Item -LiteralPath $entry.Path -Recurse -Force -ErrorAction Stop
            $deleted++
        } else {
            $destDir = if ($entry.Bucket) { Join-Path $ArchiveRoot $entry.Bucket } else { $ArchiveRoot }
            if (-not (Test-Path -LiteralPath $destDir)) {
                New-Item -ItemType Directory -Path $destDir -Force | Out-Null
            }
            Move-Item -LiteralPath $entry.Path -Destination $destDir -Force -ErrorAction Stop
            $moved++
        }
    } catch {
        Write-Warning ("{0} failed: {1} - {2}" -f $entry.Action, $entry.Path, $_.Exception.Message)
        $failed++
    }
}

$vacuumed = 0
$reclaimedMb = 0
if ($Tier -ge 3 -and $vacuumTargets.Count -gt 0) {
    $sqlite = Get-Command sqlite3 -ErrorAction SilentlyContinue
    if ($sqlite) {
        foreach ($db in $vacuumTargets) {
            try {
                $before = (Get-Item -LiteralPath $db.FullName -Force).Length
                & $sqlite.Source $db.FullName 'VACUUM;' 2>&1 | Out-Null
                if ($LASTEXITCODE -ne 0) { throw "sqlite3 exited $LASTEXITCODE" }
                $after = (Get-Item -LiteralPath $db.FullName -Force).Length
                $reclaimedMb += [math]::Round(($before - $after) / 1MB, 1)
                $vacuumed++
            } catch {
                Write-Warning ("VACUUM failed: {0} - {1}" -f $db.FullName, $_.Exception.Message)
                $failed++
            }
        }
    }
}

Write-Host ''
Write-Host ("Reclaimed {0} MB: deleted {1}, moved {2}" -f $totalMb, $deleted, $moved)
if ($vacuumed -gt 0) {
    Write-Host ("VACUUM: {0} databases compacted, {1} MB returned" -f $vacuumed, $reclaimedMb)
}
if ($moved -gt 0) {
    Write-Host ("Moved content is recoverable at: {0}" -f $ArchiveRoot)
}
if ($failed -gt 0) {
    Write-Error "$failed entries could not be processed" -ErrorAction Continue
    exit 1
}
exit 0
