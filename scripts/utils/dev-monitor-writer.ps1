#requires -Version 7.0
<#
.SYNOPSIS
    The monitor page writer (S2406): keeps temp/monitor/index.html and temp/monitor/snapshot.js
    current every few seconds, detached from any agent session. `.\a.ps1 rmw`.

.DESCRIPTION
    Modes (one switch at most; none means start):

      start      If a writer is alive (writer.pid names a live pwsh) print it and open the page;
                 otherwise launch `-Loop` through start-detached.ps1 (S2400) so the writer outlives
                 this shell and this session, wait for the first snapshot.js, open index.html in the
                 default browser once (unless -NoBrowser) and print the page path.
      -Loop      The writer itself: write writer.pid, write the shell, then every -IntervalSeconds
                 take Get-DevMonitorSnapshot, add the `writer` block (pid, startedAtUtc,
                 intervalSeconds, state, tick) and replace snapshot.js atomically (temp name plus
                 Move-Item, so the browser never reads a half file). A STOP flag in the directory
                 ends the loop: one last snapshot with state `stopped`, pid file and flag removed.
      -Once      One shell plus one snapshot, no pid file, no loop - for tests and for a look.
      -Stop      Create the STOP flag, wait up to three intervals for the writer to leave on its
                 own, Stop-Process if it does not, remove writer.pid. Exit 0 whether or not a
                 writer was running.
      -Status    Print pid, alive, page path and snapshot age.

    Files, all under -OutDir (temp/monitor by default): index.html, snapshot.js, writer.pid, STOP,
    and the detached launcher's log. The writer never takes a lock, never posts to the agent chat
    (it is a viewer, not an agent), never runs gradle, the catalog sync or the queue ranker, and
    writes nothing outside -OutDir. Interval and budget: 3 s (owner ruling 2026-09-02) against a
    snapshot measured at 209-518 ms (median 229 ms, 2026-09-02).

    Exit codes:
      0  done - started, already running, once written, stopped (or nothing to stop), status shown.
      1  could not start: the launcher failed, the first snapshot never appeared, or -Loop found
         another live writer.
      2  the repository layout could not be read (temp/ missing under -RepoRoot).

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/dev-monitor-writer.ps1
    Start the detached writer and open the page.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/dev-monitor-writer.ps1 -Stop
#>
[CmdletBinding()]
param(
    [switch]$Loop,
    [switch]$Once,
    [switch]$Stop,
    [switch]$Status,

    # Seconds between snapshots. Three by the owner's ruling; never below one.
    [int]$IntervalSeconds = 3,

    # Finished tickets per instance, next-up rows, chat tail rows carried in every snapshot.
    [int]$Tail = 5,
    [int]$NextUp = 25,
    [int]$ChatTail = 40,

    # Start only: do not open the page in the browser.
    [switch]$NoBrowser,

    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,

    # Where the page, the data script, the pid file and the log live. Repo-relative or absolute.
    [string]$OutDir = 'temp/monitor'
)

$ErrorActionPreference = 'Stop'

$modes = @($Loop, $Once, $Stop, $Status | Where-Object { $_ }).Count
if ($modes -gt 1) {
    Write-Host 'dev-monitor-writer: pick one of -Loop, -Once, -Stop, -Status.' -ForegroundColor Red
    exit 1
}
if ($IntervalSeconds -lt 1) { $IntervalSeconds = 1 }

$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path.TrimEnd('\', '/')
if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot 'temp'))) {
    Write-Host "dev-monitor-writer: temp/ not found under $RepoRoot." -ForegroundColor Red
    exit 2
}
$outDirFull = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $RepoRoot $OutDir }
if (-not (Test-Path -LiteralPath $outDirFull)) { New-Item -ItemType Directory -Path $outDirFull -Force | Out-Null }
$outDirFull = (Resolve-Path -LiteralPath $outDirFull).Path.TrimEnd('\', '/')

$pagePath = Join-Path $outDirFull 'index.html'
$dataPath = Join-Path $outDirFull 'snapshot.js'
$pidPath = Join-Path $outDirFull 'writer.pid'
$stopPath = Join-Path $outDirFull 'STOP'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

. (Join-Path $PSScriptRoot 'dev-monitor-snapshot.ps1')
. (Join-Path $PSScriptRoot 'dev-monitor-html.ps1')

function Write-AtomicText {
    param([string]$Path, [string]$Text)
    $staging = "$Path.tmp-$PID"
    try {
        [System.IO.File]::WriteAllText($staging, $Text, $utf8NoBom)
        Move-Item -LiteralPath $staging -Destination $Path -Force
    }
    catch {
        Remove-Item -LiteralPath $staging -Force -ErrorAction SilentlyContinue
        throw
    }
}

function Read-WriterPid {
    if (-not (Test-Path -LiteralPath $pidPath)) { return $null }
    try { return (Get-Content -LiteralPath $pidPath -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop) }
    catch { return $null }
}

function Test-WriterAlive {
    param($Info)
    if ($null -eq $Info -or -not $Info.pid) { return $false }
    try {
        $p = Get-Process -Id ([int]$Info.pid) -ErrorAction Stop
        return ($p.ProcessName -match '^pwsh')
    }
    catch { return $false }
}

function Write-Shell {
    param([string]$Stamp)
    Write-AtomicText -Path $pagePath -Text (Get-DevMonitorShellHtml -IntervalSeconds $IntervalSeconds -Stamp $Stamp)
}

function Write-Data {
    param([string]$State, [int]$Tick, [string]$StartedAtUtc)
    $snapshot = Get-DevMonitorSnapshot -RepoRoot $RepoRoot -Tail $Tail -NextUp $NextUp -ChatTail $ChatTail
    # shellStamp equals the stamp baked into the shell this writer wrote; an open tab that was
    # loaded from an older shell sees the difference and reloads itself once.
    $snapshot | Add-Member -NotePropertyName 'writer' -NotePropertyValue ([pscustomobject][ordered]@{
        pid             = $PID
        startedAtUtc    = $StartedAtUtc
        intervalSeconds = $IntervalSeconds
        state           = $State
        tick            = $Tick
        shellStamp      = $StartedAtUtc
    }) -Force
    Write-AtomicText -Path $dataPath -Text (ConvertTo-DevMonitorDataScript -Snapshot $snapshot)
    return $snapshot.durationMs
}

function Get-SnapshotAgeSeconds {
    if (-not (Test-Path -LiteralPath $dataPath)) { return $null }
    return [math]::Round(((Get-Date) - (Get-Item -LiteralPath $dataPath).LastWriteTime).TotalSeconds)
}

function Open-Page {
    if ($NoBrowser) { return }
    try { Start-Process -FilePath $pagePath | Out-Null }
    catch { Write-Host "dev-monitor-writer: could not open the browser - $_" -ForegroundColor DarkYellow }
}

# --- -Status ---------------------------------------------------------------------------------------
if ($Status) {
    $info = Read-WriterPid
    $alive = Test-WriterAlive -Info $info
    $age = Get-SnapshotAgeSeconds
    if ($alive) { Write-Host ("writer: running, pid {0}, every {1} s, since {2}" -f $info.pid, $info.intervalSeconds, $info.startedAtUtc) -ForegroundColor Green }
    elseif ($null -ne $info) { Write-Host ("writer: not running (stale pid file names {0})" -f $info.pid) -ForegroundColor DarkYellow }
    else { Write-Host 'writer: not running' -ForegroundColor DarkYellow }
    Write-Host ("page:   {0}" -f $pagePath)
    Write-Host ("data:   {0}" -f $(if ($null -ne $age) { "snapshot.js written $age s ago" } else { 'no snapshot yet' }))
    exit 0
}

# --- -Stop -----------------------------------------------------------------------------------------
if ($Stop) {
    $info = Read-WriterPid
    if (-not (Test-WriterAlive -Info $info)) {
        Write-Host 'dev-monitor-writer: no writer is running.' -ForegroundColor DarkYellow
        Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
        exit 0
    }
    [System.IO.File]::WriteAllText($stopPath, 'stop', $utf8NoBom)
    $wait = [math]::Max(3, 3 * [int]$info.intervalSeconds)
    $deadline = (Get-Date).AddSeconds($wait)
    while ((Get-Date) -lt $deadline -and (Test-WriterAlive -Info $info)) { Start-Sleep -Milliseconds 250 }
    if (Test-WriterAlive -Info $info) {
        # The loop reads the flag between ticks; a writer that did not leave in three intervals is
        # stuck inside a tick, and the process is the only thing left to stop.
        Stop-Process -Id ([int]$info.pid) -Force -ErrorAction SilentlyContinue
        Write-Host ("dev-monitor-writer: stopped pid {0} (did not leave on its own within {1} s)." -f $info.pid, $wait) -ForegroundColor DarkYellow
    }
    else {
        Write-Host ("dev-monitor-writer: writer pid {0} stopped." -f $info.pid) -ForegroundColor Green
    }
    Remove-Item -LiteralPath $pidPath, $stopPath -Force -ErrorAction SilentlyContinue
    exit 0
}

# --- -Once -----------------------------------------------------------------------------------------
if ($Once) {
    $onceStamp = [DateTime]::UtcNow.ToString('o')
    Write-Shell -Stamp $onceStamp
    $ms = Write-Data -State 'once' -Tick 1 -StartedAtUtc $onceStamp
    Write-Host ("dev-monitor-writer: wrote {0} and snapshot.js ({1} ms)." -f $pagePath, $ms)
    exit 0
}

# --- -Loop (the detached process) -------------------------------------------------------------------
if ($Loop) {
    $other = Read-WriterPid
    if ((Test-WriterAlive -Info $other) -and [int]$other.pid -ne $PID) {
        Write-Host ("dev-monitor-writer: another writer is alive (pid {0}); not starting a second one." -f $other.pid) -ForegroundColor Red
        exit 1
    }
    $startedAt = [DateTime]::UtcNow.ToString('o')
    Write-AtomicText -Path $pidPath -Text (([pscustomobject]@{ pid = $PID; startedAtUtc = $startedAt; intervalSeconds = $IntervalSeconds }) | ConvertTo-Json -Compress)
    Write-Shell -Stamp $startedAt
    Write-Host ("dev-monitor-writer: loop started, pid {0}, every {1} s, page {2}" -f $PID, $IntervalSeconds, $pagePath)
    $tick = 0
    while (-not (Test-Path -LiteralPath $stopPath)) {
        $tick++
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $ms = Write-Data -State 'running' -Tick $tick -StartedAtUtc $startedAt
            if ($tick -eq 1 -or ($tick % 100) -eq 0) { Write-Host ("tick {0}: snapshot {1} ms" -f $tick, $ms) }
        }
        catch {
            # One failed tick is logged, never fatal: the page keeps the last good snapshot and its
            # header says how old it is.
            Write-Host ("tick {0} failed: {1}" -f $tick, $_) -ForegroundColor DarkYellow
        }
        $remaining = ($IntervalSeconds * 1000) - [int]$sw.ElapsedMilliseconds
        if ($remaining -gt 0) { Start-Sleep -Milliseconds $remaining }
    }
    try { [void](Write-Data -State 'stopped' -Tick $tick -StartedAtUtc $startedAt) } catch { Write-Host ("final snapshot failed: {0}" -f $_) -ForegroundColor DarkYellow }
    Remove-Item -LiteralPath $pidPath, $stopPath -Force -ErrorAction SilentlyContinue
    Write-Host ("dev-monitor-writer: stopped after {0} tick(s)." -f $tick)
    exit 0
}

# --- start (default) ---------------------------------------------------------------------------------
$info = Read-WriterPid
if (Test-WriterAlive -Info $info) {
    Write-Host ("dev-monitor-writer: already running, pid {0}, every {1} s (since {2})." -f $info.pid, $info.intervalSeconds, $info.startedAtUtc) -ForegroundColor Green
    Write-Host ("page: {0}" -f $pagePath)
    Open-Page
    exit 0
}
Remove-Item -LiteralPath $pidPath, $stopPath -Force -ErrorAction SilentlyContinue

$launcher = Join-Path $PSScriptRoot 'start-detached.ps1'
$childArgs = '-Loop -IntervalSeconds {0} -Tail {1} -NextUp {2} -ChatTail {3} -RepoRoot "{4}" -OutDir "{5}"' -f $IntervalSeconds, $Tail, $NextUp, $ChatTail, $RepoRoot, $outDirFull
$launch = & $launcher -Command 'scripts/utils/dev-monitor-writer.ps1' -Arguments $childArgs -OutDir $outDirFull -Label 'writer' 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    Write-Host ("dev-monitor-writer: the launcher failed ({0}):`n{1}" -f $LASTEXITCODE, $launch.Trim()) -ForegroundColor Red
    exit 1
}
$deadline = (Get-Date).AddSeconds(15)
$seen = $false
while ((Get-Date) -lt $deadline) {
    if ((Test-Path -LiteralPath $dataPath) -and (Test-WriterAlive -Info (Read-WriterPid))) { $seen = $true; break }
    Start-Sleep -Milliseconds 250
}
if (-not $seen) {
    Write-Host ("dev-monitor-writer: the writer did not produce a first snapshot within 15 s. Launcher said:`n{0}" -f $launch.Trim()) -ForegroundColor Red
    exit 1
}
$info = Read-WriterPid
Write-Host ("dev-monitor-writer: running, pid {0}, every {1} s." -f $info.pid, $IntervalSeconds) -ForegroundColor Green
Write-Host ("page: {0}" -f $pagePath)
Write-Host ("stop: pwsh -NoProfile -File ./a.ps1 rmw -Stop")
Open-Page
exit 0
