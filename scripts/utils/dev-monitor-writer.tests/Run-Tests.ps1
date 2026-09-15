#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for scripts/utils/dev-monitor-writer.ps1 and scripts/utils/dev-monitor-html.ps1
    (S2406): the page shell, the data script and the detached writer's lifecycle.

.DESCRIPTION
    Runs against the live repository root (the snapshot is whatever the machine is doing) with
    every artifact under a fixture -OutDir beneath temp/S2406/, so a running owner's writer under
    temp/monitor/ is neither read nor touched.

    Pinned (strategic section 11 items 1, 4, 5, 7 and 9):
      - -Once writes exactly index.html and snapshot.js, no staging file left behind;
      - the data script is `window.__devMonitor(<json>);`, schema 1, with the writer block;
      - the shell is UTF-8, self-contained (no http/https/<link>), English, carries the loader for
        snapshot.js and the `writer silent` state, and has no animation, transition or keyframes;
      - start with -NoBrowser launches a detached writer whose pid file names a live process;
      - a second start prints `already running` and leaves the pid unchanged;
      - the tick counter grows while the loop runs (polled to a deadline, not timed);
      - -Stop ends the process within five seconds, removes the pid file, and the last snapshot says
        `stopped`;
      - no artifact the writer produces appears at the top level of temp/ (every one stays under
        -OutDir); a file another process wrote there is not this suite's business;
      - -Status exits 0 before and after.

    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
      2 - the fixture could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$writer = Join-Path $repoRoot 'scripts/utils/dev-monitor-writer.ps1'

$script:pass = 0
$script:fail = 0
function Assert-That([string]$name, [bool]$condition, [string]$detail = '') {
    if ($condition) { $script:pass++; Write-Host "  PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL  $name`n        $detail" -ForegroundColor Red }
}
function Invoke-Writer([string[]]$argv) {
    $out = & $pwshExe -NoProfile -File $writer @argv 2>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = $out }
}
function Read-Data([string]$path) {
    $raw = [IO.File]::ReadAllText($path)
    if (-not $raw.StartsWith('window.__devMonitor(')) { return $null }
    $json = $raw.Substring('window.__devMonitor('.Length).TrimEnd("`n", "`r", ' ')
    if (-not $json.EndsWith(');')) { return $null }
    try { return ($json.Substring(0, $json.Length - 2) | ConvertFrom-Json -ErrorAction Stop) } catch { return $null }
}
function Get-TempTopLevel {
    return @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'temp') -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.Name } | Sort-Object)
}

$run = Get-Date -Format 'yyyyMMdd-HHmmss'
$fixture = Join-Path $repoRoot "temp/S2406/writer-tests/$run/monitor"
try { New-Item -ItemType Directory -Path $fixture -Force | Out-Null }
catch { Write-Host "dev-monitor-writer tests: fixture could not be prepared - $_" -ForegroundColor Red; exit 2 }

$tempBefore = Get-TempTopLevel
try {
    Write-Host 'Once'
    $once = Invoke-Writer @('-Once', '-OutDir', $fixture, '-NoBrowser')
    $files = @(Get-ChildItem -LiteralPath $fixture -File | ForEach-Object { $_.Name } | Sort-Object)
    Assert-That '-Once exits 0 and writes exactly index.html and snapshot.js' ($once.Code -eq 0 -and ($files -join ',') -eq 'index.html,snapshot.js') "exit=$($once.Code) files=$($files -join ',') $($once.Text)"
    $data = Read-Data (Join-Path $fixture 'snapshot.js')
    Assert-That 'data script is window.__devMonitor(<json>) with schema 1 and the writer block' ($null -ne $data -and $data.schema -eq 1 -and $data.writer.state -eq 'once' -and $data.writer.intervalSeconds -eq 3) ''
    $shell = [IO.File]::ReadAllText((Join-Path $fixture 'index.html'))
    Assert-That 'shell carries the charset, the loader and the silent state' ($shell -match '<meta charset="utf-8">' -and $shell -match 'snapshot\.js\?t=' -and $shell -match 'writer silent' -and $shell -match 'writer stopped') ''
    $forbidden = @('animation', 'transition', '@keyframes', 'http://', 'https://', '<link', '<img')
    $found = @($forbidden | Where-Object { $shell -match [regex]::Escape($_) })
    Assert-That 'shell has no animation, transition, keyframes or external reference' ($found.Count -eq 0) ($found -join ',')
    $missingSections = @(@('problems', 'agents', 'ticket leases', 'locks', 'gate health', 'watchdog actions', 'next up', 'chat', 'findings', 'finished', 'stop') | Where-Object { $shell -notmatch ('<h2>' + [regex]::Escape($_)) })
    Assert-That 'shell is English-labelled and lists every section' ($missingSections.Count -eq 0) ($missingSections -join ',')
    # S2869: the canon snapshot collector truncates "In Progress" to "In"; the page's statusCls
    # normalizes it back. The assertion reads the pattern off the shell so a refactor cannot drop it.
    Assert-That 'shell normalizes the truncated "In" status to "In Progress"' ($shell -match "st === 'In'") ''
    Assert-That 'shell labels a set-named failure for the affected file' ($shell -match 'named your file') ''
    Assert-That 'lock rows distinguish the executor from a ticket lease owner' (
        $shell -match 'lock executor' -and $shell -match 'ticket.*owner' -and $shell -match 'ticketFromReason'
    ) ''
    # The roster is the whole point of the section (owner finding 2026-09-10): four identity spaces
    # became one, so the assertion is that every identity SOURCE feeds the same keyed map. Naming the
    # sources rather than the rendered text is deliberate - a page that drops `s.locks` from the join
    # looks perfectly ordinary and silently reinstates the defect.
    $rosterSources = @('arr(s.agents).forEach', 'arr(s.sessions).forEach', 'arr(s.leases).forEach', 'arr(s.locks).forEach', 'arr(s.children).forEach')
    $missingSources = @($rosterSources | Where-Object { $shell -notmatch [regex]::Escape($_) })
    Assert-That 'the agent roster joins agents, sessions, leases, locks and children' (
        $missingSources.Count -eq 0 -and $shell -match 'function slot\(sid\)' -and $shell -match 'roster\[sid\]'
    ) ($missingSources -join ',')
    Assert-That 'a lock holder and a queue waiter carry the session id that joins them to the roster' (
        $shell -match 'function shortId' -and $shell -match 'lockExecutor\(ticket, k\.name, k\.sessionId\)' -and $shell -match 'shortId\(t\.sessionId\)'
    ) ''
    Assert-That 'headless children stay process-shaped, below the sessions' (
        $shell -match 'A headless child is a PROCESS' -and $shell -match "child ' \+ esc\(c\.pid\)"
    ) ''
    # The note is the only wrapping cell, so as a column it dictated every row's height (owner ruling
    # 2026-09-10). It lives on a spanning row of its own now, and the agent row above it drops its
    # border so the pair reads as one entry.
    Assert-That 'the last note is a spanning row under its agent, not a column' (
        $shell -match 'function noteRow' -and $shell -match "span: ROSTER_HEAD\.length" -and
        $shell -match 'tr\.hasnote td\{border-bottom:none\}' -and $shell -match 'tr\.note td\{' -and
        $shell -notmatch "'waiting for', '#seen', 'last note'"
    ) ''
    # Owner finding 2026-09-10, third pass ("ugly and uninformative"), three rules with one cause:
    # the roster spent its width and its height on cells that said nothing. `min-width` on a cell
    # applies to its whole COLUMN, so `holds` and `waiting for` - `-` on 17 of 18 live rows - held
    # some 650 px hostage between them and squeezed every text column into a strip; half the note
    # rows repeated, word for word, the phase cell one column to their left; and half the rows
    # called active were sessions whose entire trace was a lock released minutes ago.
    Assert-That 'an empty cell drops its wrap class, so a silent column reserves no width' (
        $shell -match "k = String\(v\)\.length \? 'wrap' : ''" -and
        $shell -match 'td\.wrapn\{' -and $shell -match '\{ s: holds \}' -and $shell -match '\{ s: waits \}'
    ) ''
    Assert-That 'a note row is dropped when it echoes the phase cell or is session bookkeeping' (
        $shell -match 'noteIsPhaseEcho' -and $shell -match 'noteIsSessionBookkeeping' -and
        $shell -match "row\.lastKind === 'phase' && row\.phaseNote && row\.note === row\.phaseNote" -and
        $shell -match 'plain\.length > 200'
    ) ''
    Assert-That 'an agent with no ticket, no hold and no queue place is collapsed, not listed' (
        $shell -match 'if \(!row\.ticket\) \{ hidden\.push\(row\); return; \}' -and
        $shell -match "agent\(s\) collapsed" -and $shell -match "'runtime/model'" -and
        $shell -notmatch "'\?</span>' : esc\(v\)"
    ) ''
    # A quiet agent that still owns something is the one failure the page must not render as an
    # ordinary row. The show rule and the alarm rule must read ONE predicate: when they were written
    # separately, a quiet agent holding only a ticket was collapsed into the hidden line, so the red
    # row the cut exists to expose was the single row it removed.
    Assert-That 'a quiet owner of a lock or a ticket is an alarm, and one predicate decides it' (
        $shell -match 'function ownsSomething' -and
        $shell -match "ownsSomething\(row\)\) \{ shown\.push\(sid\)" -and
        $shell -match 'quiet && ownsSomething\(row\)' -and
        $shell -match "foreign-stale" -and $shell -match "'NO LIFE'" -and $shell -match 'tr\.alarm td\{'
    ) ''
    # Gate health is reference, not a live signal, so it sits below everything that changes tick to
    # tick (owner ruling 2026-09-10). Problems opens the page ahead of the roster (owner ruling
    # 2026-09-10, second pass the same day): a terse red/yellow at-a-glance panel - lock queues, dead
    # leases and stuck tickets, gate/build failures - so a clean run needs no further reading and a
    # red run is told which section below to open.
    $sectionOrder = @([regex]::Matches($shell, '<h2>([a-z ]+)') | ForEach-Object { $_.Groups[1].Value.Trim() })
    Assert-That 'problems opens the page, the agent roster follows, and gate health closes it' (
        $sectionOrder.Count -gt 3 -and $sectionOrder[0] -eq 'problems' -and $sectionOrder[1] -eq 'agents' -and $sectionOrder[-1] -eq 'gate health'
    ) ($sectionOrder -join ' > ')
    Assert-That 'problems is computed from the primitive arrays, independent of the roster join' (
        $shell -match 'function renderProblems' -and $shell -match "renderProblems\(s\)" -and
        $shell -match "'ALL CLEAR'" -and $shell -match "'PROBLEM'" -and $shell -match "'lock queue'" -and
        $shell -match "'stalled ticket'" -and $shell -match "'dead lease'" -and $shell -match "'build crash'" -and
        $shell -match "'abandoned ticket'"
    ) ''
    # The panel and the roster must judge silence by ONE number. Measured live 2026-09-10: lease
    # S2859 had been quiet 37 min and the roster painted its owner red as NO LIFE, while this panel
    # printed ALL CLEAR directly above it - the harness calls a lease live for 45 min, and the panel
    # had no rule of its own. A second copy of the constant would let the two disagree again.
    Assert-That 'the abandoned-ticket rule and the roster cut share one constant' (
        $shell -match 'var ROSTER_ACTIVE_MINUTES = 10;' -and
        ([regex]::Matches($shell, 'var ROSTER_ACTIVE_MINUTES')).Count -eq 1 -and
        $shell -match 'l\.lastSeenMinutes > ROSTER_ACTIVE_MINUTES'
    ) ''
    # S2406, 2026-09-10: ConvertTo-Json writes a ONE-element collection as a bare object, and
    # `.forEach` on that object threw inside render() - which stopped the paint at the offending
    # section, left the eight tables below it blank, and said nothing, because the header keeps its
    # own clock and went on reporting a fresh age. One device in the park was enough to do it. Both
    # halves are asserted: no collection is read with a bare `|| []` any more, and a failure that
    # does get through is reported where the reader looks first.
    Assert-That 'every snapshot collection is read through arr(), and a render failure is visible' (
        $shell -match 'function arr\(v\)' -and $shell -notmatch '\(s\.[A-Za-z]+ \|\| \[\]\)' -and
        $shell -match 'try \{ render\(snap\); \}' -and $shell -match "'PAGE ERROR'"
    ) ''
    # Measured live 2026-09-10: the canon gate collector marks a whole run FAIL the moment any one
    # gate in it is SKIP (not applicable to the change set), with no per-gate status surviving into
    # `failures[]` - one sampled run carried 44 SKIP gates and zero real failures as a single FAIL.
    # Reusing `s.gates` here would make the summary panel red on nearly every ordinary run, which is
    # the opposite of what it exists for - so the panel must read a build crash from the stall rule
    # instead, and must not reintroduce `s.gates` as a source.
    Assert-That 'problems does not read s.gates as a source (SKIP-tainted at the collector)' (
        $shell -match 'function renderProblems[\s\S]*?\n  function render\(s\)' -and
        ($shell -replace '[\s\S]*?function renderProblems', '' -replace 'function render\(s\)[\s\S]*', '') -notmatch 's\.gates'
    ) ''
    # Read the stamp off the raw text: ConvertFrom-Json turns an ISO string into a DateTime, and the
    # page compares strings.
    $stampMatch = [regex]::Match([IO.File]::ReadAllText((Join-Path $fixture 'snapshot.js')), '"shellStamp":"([^"]+)"')
    Assert-That 'shell and data script carry the same stamp' ($stampMatch.Success -and $shell -match ("SHELL_STAMP = '" + [regex]::Escape($stampMatch.Groups[1].Value) + "'")) "stamp=$($stampMatch.Value)"
    $bytes = [IO.File]::ReadAllBytes((Join-Path $fixture 'index.html'))
    Assert-That 'shell is UTF-8 without BOM' (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)) ''

    Write-Host 'Devices block (S2855)'
    # Seed both device stores with a test serial, write one snapshot, and expect one merged devices
    # row carrying the mark. The stores live in the real temp/ (no root override exists), so the
    # case removes what it seeded and restores the directories it created - the top-level invariant
    # below would otherwise see them as new artifacts on a fresh machine.
    $registryDir = Join-Path $repoRoot 'temp/DEVICE.REGISTRY'
    $leaseDir = Join-Path $repoRoot 'temp/DEVICE.LEASES'
    $regExisted = Test-Path -LiteralPath $registryDir
    $leaseExisted = Test-Path -LiteralPath $leaseDir
    $seedSerial = 'test-park-device'
    try {
        New-Item -ItemType Directory -Path $registryDir -Force | Out-Null
        New-Item -ItemType Directory -Path $leaseDir -Force | Out-Null
        [pscustomobject]@{
            schema      = 1
            id          = $seedSerial
            model       = 'Seed Model'
            role        = 'suite fixture'
            lastInstall = [pscustomobject]@{
                package     = 'com.sza.fastmediasorter.debug'
                flavor      = 'noLegal'
                buildType   = 'debug'
                versionName = '9.9.9-seed'
                installedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                recordedBy  = 'writer-tests'
            }
            history     = @()
            updatedAt   = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        } | ConvertTo-Json -Compress | Set-Content -LiteralPath (Join-Path $registryDir "$seedSerial.json") -Encoding utf8NoBOM
        # The read-only baseline is taken AFTER seeding: what must not change is what the WRITER
        # does to the store, not what this fixture just put there.
        $filesBefore = @(Get-ChildItem -LiteralPath $registryDir -File -ErrorAction SilentlyContinue).Count
        $fixture2 = Join-Path $fixture '../monitor-devices'
        $once2 = Invoke-Writer @('-Once', '-OutDir', $fixture2, '-NoBrowser')
        $data2 = Read-Data (Join-Path $fixture2 'snapshot.js')
        $row = $null
        if ($null -ne $data2 -and $null -ne $data2.devices) {
            $row = @($data2.devices | Where-Object { $_.id -eq $seedSerial })[0]
        }
        # The SHAPE, read off the raw text rather than through ConvertFrom-Json, which hides it:
        # a park with exactly one device used to serialise as `"devices":{..}`, because a PowerShell
        # function's output is enumerated into the pipeline and the @() inside Get-DeviceParkRows is
        # undone at the call site. The page reads the field with .forEach, so that object stopped
        # render() at the devices section and blanked the eight tables below it. This suite could
        # not see it: the live park it seeds into usually holds another serial, so the array
        # survived by accident, and the assertion below passed while the page was broken.
        $rawData2 = [IO.File]::ReadAllText((Join-Path $fixture2 'snapshot.js'))
        Assert-That 'devices serialises as an array however many devices the park holds' (
            $rawData2 -match '"devices":\[' -and $rawData2 -notmatch '"devices":\{'
        ) (($rawData2 -split '"devices":')[-1].Substring(0, [math]::Min(40, (($rawData2 -split '"devices":')[-1]).Length)))
        Assert-That 'the snapshot carries a devices row for the seeded serial' (
            $once2.Code -eq 0 -and $null -ne $row -and $row.mark.versionName -eq '9.9.9-seed' -and
            $row.mark.package -eq 'com.sza.fastmediasorter.debug' -and $row.model -eq 'Seed Model'
        ) "exit=$($once2.Code) row=$($null -ne $row)"
        # Read-only proof: the write added no file to either store and removed none.
        $filesAfter = @(Get-ChildItem -LiteralPath $registryDir -File -ErrorAction SilentlyContinue).Count
        Assert-That 'the writer left both device stores exactly as it found them' (
            (Test-Path -LiteralPath (Join-Path $leaseDir "$seedSerial.json")) -eq $false -and $filesAfter -eq $filesBefore
        ) "before=$filesBefore after=$filesAfter"
    }
    finally {
        Remove-Item -LiteralPath (Join-Path $registryDir "$seedSerial.json") -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $leaseDir "$seedSerial.json") -Force -ErrorAction SilentlyContinue
        if (-not $regExisted) { Remove-Item -LiteralPath $registryDir -Force -ErrorAction SilentlyContinue }
        if (-not $leaseExisted) { Remove-Item -LiteralPath $leaseDir -Force -ErrorAction SilentlyContinue }
    }

    Write-Host 'Status before start'
    $st0 = Invoke-Writer @('-Status', '-OutDir', $fixture)
    Assert-That '-Status exits 0 with no writer' ($st0.Code -eq 0 -and $st0.Text -match 'not running') $st0.Text

    Write-Host 'Start, second start, ticks'
    $start = Invoke-Writer @('-NoBrowser', '-IntervalSeconds', '1', '-OutDir', $fixture)
    $pidInfo = $null
    try { $pidInfo = Get-Content -LiteralPath (Join-Path $fixture 'writer.pid') -Raw | ConvertFrom-Json } catch { $pidInfo = $null }
    $alive = $false
    if ($pidInfo) { try { $alive = $null -ne (Get-Process -Id ([int]$pidInfo.pid) -ErrorAction Stop) } catch { $alive = $false } }
    Assert-That 'start exits 0, pid file names a live process' ($start.Code -eq 0 -and $alive) "exit=$($start.Code) $($start.Text)"
    $again = Invoke-Writer @('-NoBrowser', '-OutDir', $fixture)
    $pidAgain = $null
    try { $pidAgain = (Get-Content -LiteralPath (Join-Path $fixture 'writer.pid') -Raw | ConvertFrom-Json).pid } catch { $pidAgain = $null }
    Assert-That 'second start says already running and keeps the pid' ($again.Code -eq 0 -and $again.Text -match 'already running' -and $pidInfo -and $pidAgain -eq $pidInfo.pid) "exit=$($again.Code) $($again.Text)"
    # Polled to a deadline instead of sleeping exactly one interval: a tick costs the collector
    # 0.2-1.0 s on an idle tree but was measured at 5.1 s with three runner instances and a hundred
    # agents in the chat window, so a flat 3 s sleep asserted that a BUSY machine is a broken writer
    # - the case failed once and passed on the immediate re-run (2026-09-10), which is the worst
    # shape a gate can have. The deadline is generous because what is under test is that the loop
    # ticks at all, never how fast it ticks.
    $d1 = Read-Data (Join-Path $fixture 'snapshot.js')
    $d2 = $d1
    $tickDeadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $tickDeadline) {
        Start-Sleep -Milliseconds 500
        $probe = Read-Data (Join-Path $fixture 'snapshot.js')
        if ($null -ne $probe -and $null -ne $d1 -and $probe.writer.tick -gt $d1.writer.tick) { $d2 = $probe; break }
        if ($null -ne $probe) { $d2 = $probe }
    }
    Assert-That 'the tick grows while the writer runs, state running' ($null -ne $d1 -and $null -ne $d2 -and $d2.writer.tick -gt $d1.writer.tick -and $d2.writer.state -eq 'running') "t1=$($d1.writer.tick) t2=$($d2.writer.tick) waited up to 30s"
    Assert-That 'no staging file is left between ticks' (@(Get-ChildItem -LiteralPath $fixture -Filter '*.tmp-*' -File).Count -eq 0) ''
    $st1 = Invoke-Writer @('-Status', '-OutDir', $fixture)
    Assert-That '-Status reports the running writer' ($st1.Code -eq 0 -and $st1.Text -match 'running, pid') $st1.Text

    Write-Host 'Stop'
    $stop = Invoke-Writer @('-Stop', '-IntervalSeconds', '1', '-OutDir', $fixture)
    $gone = $false
    $deadline = (Get-Date).AddSeconds(5)
    while ((Get-Date) -lt $deadline) {
        try { $null = Get-Process -Id ([int]$pidInfo.pid) -ErrorAction Stop; Start-Sleep -Milliseconds 200 } catch { $gone = $true; break }
    }
    $d3 = Read-Data (Join-Path $fixture 'snapshot.js')
    Assert-That '-Stop exits 0, the process is gone within five seconds' ($stop.Code -eq 0 -and $gone) "exit=$($stop.Code) $($stop.Text)"
    Assert-That 'pid file removed, last snapshot says stopped' (-not (Test-Path (Join-Path $fixture 'writer.pid')) -and $null -ne $d3 -and $d3.writer.state -eq 'stopped') "state=$($d3.writer.state)"
    $stopAgain = Invoke-Writer @('-Stop', '-OutDir', $fixture)
    Assert-That '-Stop with nothing running exits 0' ($stopAgain.Code -eq 0) $stopAgain.Text

    Write-Host 'Read-only outside -OutDir'
    $tempAfter = Get-TempTopLevel
    # The subject of this case is the writer, so it asks only about the writer: did any artifact it
    # produces land at temp/ root instead of under -OutDir? It deliberately does not diff the whole
    # directory. temp/ root is shared, and naming what siblings happen to write can only ever grow
    # into the next failure - S2998 added the five coordination files, and the r37 pre-release sweep
    # then failed this case on a Maestro trace the writer never wrote, which made
    # assert-release-scope-gates.ps1 exit 1 and held the release on a defect that did not exist
    # (S3025). A whitelist has no such tail: an artifact the writer cannot produce cannot fail it.
    $writerArtifacts = @('index.html', 'snapshot.js', 'writer.pid', 'STOP')
    $strayed = @($tempAfter | Where-Object {
        $tempBefore -notcontains $_ -and
        ($writerArtifacts -contains $_ -or $_ -like 'index.html.tmp-*' -or $_ -like 'snapshot.js.tmp-*')
    })
    Assert-That 'no writer artifact escaped -OutDir into temp/ root' ($strayed.Count -eq 0) ($strayed -join ',')
}
finally {
    # A writer the suite failed to stop must not outlive it.
    try {
        $left = $null
        if (Test-Path (Join-Path $fixture 'writer.pid')) { $left = (Get-Content -LiteralPath (Join-Path $fixture 'writer.pid') -Raw | ConvertFrom-Json).pid }
        if ($left) { Stop-Process -Id ([int]$left) -Force -ErrorAction SilentlyContinue }
    } catch { }
    Remove-Item -LiteralPath (Split-Path -Parent $fixture) -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host ("dev-monitor-writer tests: {0} passed, {1} failed" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
