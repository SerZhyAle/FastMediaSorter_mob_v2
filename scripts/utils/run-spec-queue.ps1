<#
.SYNOPSIS
    Drive the release queue one ticket at a time, each in its own fresh Claude Code process.

.DESCRIPTION
    The cheap alternative to /spec-do and /spec-next. Those keep one conversation alive across every
    ticket, so the context only grows and each later ticket is billed against everything the session
    already carries. This script spends one OS process per ticket instead: the child starts with an
    empty context, runs /spec-all for exactly one id, and exits. The process boundary IS the /clear,
    and unlike a /clear nobody can forget to run it.

    Order comes from the same authority the pipeline uses - scripts/spec_catalog/spec-next-preflight.ps1,
    which ranks by PLAN/RELEASE_QUEUE.md (release package ascending, then the owner's line order) and
    applies eligibility, the skip cache and sibling leases. This script never re-derives an order of
    its own; it asks for the next ticket, runs it, and asks again.

    How a run ends does not matter. Verified, still blocked, a hard stop, a timeout, a crashed child -
    each is recorded and the loop moves to the next ticket. A ticket that comes back with the status it
    started with has no autonomous next step left, so it is dropped for the rest of the run rather than
    re-picked forever.

    Two or three instances may run side by side against one working tree, and that is the intended
    way to use it. Each one ranks, takes a ticket and works it alone:

      - The ranking already skips a ticket a live sibling holds, because spec-next-preflight.ps1
        reads the lease store.
      - The window between "I ranked it" and "my child claimed it" is a few seconds wide. If two
        instances land in it, the second child's own claim returns exit 3, /spec-all stops before
        doing any work, and this script records claim-lost and moves on. The wasted cost is one CLI
        startup, not a duplicated ticket.
      - -StartDelaySeconds staggers instances launched from the same keystroke so they do not rank
        on the same instant.
      - Everything heavier is already serialised by the repository's own locks: gradle waits on
        temp/BUILD.LOCK, a multi-file source edit waits on temp/CODE.LOCK.

    Give each instance its own -Instance name so the journal and the per-ticket logs do not collide.

    Model: chosen per ticket, from the ticket's own shape, by -ModelPolicy tiered (the default). The
    saving in this script comes from the process boundary, not from a weak model, so the strong tier
    is the default and the cheap tiers have to be earned:

      - Opus   - anything that still needs a decision: Draft, Approved, Tactical, In Progress,
                 Partial, Broken, or a spec at tier 4 and above. Designing, planning and writing
                 Kotlin against this architecture is where a weak model produces work that has to be
                 redone, which costs more than it saved.
      - Sonnet - Implemented, where the only work left is /spec-check auditing a spec against code
                 that already exists; and tier 1-2 tickets, which are single-surface fixes.
      - Haiku  - never for a whole ticket. It is a lookup tier: a full /spec-all run has to hold a
                 spec, a tactical plan, gate verdicts and a build log at once. Where haiku belongs is
                 inside a session, on the search and doc subagents (CLAUDE.md Rule 31), and that is a
                 property of those agent definitions, not of this script.

    Pass -ModelPolicy fixed with -Model <name> to override the whole run.

    Stopping: `.\a.ps1 rs`, or this script with -Stop, writes temp/STOP-SPEC-QUEUE and every instance
    finishes the ticket it is on, then stops cleanly; -Stop -Instance b stops one instance only, and
    -Stop -Kill also terminates the headless children instead of letting them finish. Ctrl+C stops
    it immediately and leaves the child's own work durable - every /spec-all writes its status and
    dev-log rows as it goes.

    Exit codes: 0 = the loop ran to a normal end (queue exhausted, -MaxTickets reached, or the stop file
                    appeared),
                2 = invalid invocation - the Claude CLI or the preflight script could not be found or
                    could not be parsed,
                3 = nothing ran - either nothing was eligible at the very first ranking, or a stop
                    is still draining (children from a previous run are alive and the shared stop
                    flag is set), which is a "not now", not a failure.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/run-spec-queue.ps1
    Work the queue from the top until it is exhausted.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/run-spec-queue.ps1 -MaxTickets 5 -TimeoutMinutes 45
    Five tickets, forty-five minutes each at most.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/run-spec-queue.ps1 -Ids "S2014,S2018" -DryRun
    Show what would run for two named tickets, without launching anything.
#>
[CmdletBinding()]
param(
    # Explicit ticket list, comma-separated, run in the order given. Omit to take them from the queue.
    [string] $Ids = '',

    # Stop after this many tickets. 0 means "until the queue is exhausted".
    [int] $MaxTickets = 0,

    # Hard ceiling per ticket. The child is killed with its whole process tree when it overruns.
    [int] $TimeoutMinutes = 90,

    # How the child's model is chosen. 'tiered' reads it off the ticket (see the description);
    # 'fixed' uses -Model for every ticket; 'default' passes nothing and lets the CLI decide.
    [ValidateSet('tiered', 'fixed', 'default')]
    [string] $ModelPolicy = 'tiered',

    # The model used when -ModelPolicy is 'fixed'.
    [string] $Model = 'opus',

    # Overrides for the tiered policy, so the split can be retuned without editing the script.
    [string] $StrongModel = 'opus',
    [string] $CheapModel = 'sonnet',

    # Name for this instance when several run in parallel. Keeps journals and per-ticket logs apart.
    [string] $Instance = 'a',

    # Random stagger before the first ranking, so instances launched together do not rank in lockstep.
    [int] $StartDelaySeconds = 0,

    # The command each child runs. {id} is replaced with the ticket id.
    [string] $PromptTemplate = '/spec-all {id}',

    # Permission mode for the child session.
    [ValidateSet('acceptEdits', 'auto', 'bypassPermissions', 'manual', 'dontAsk', 'plan')]
    [string] $PermissionMode = 'bypassPermissions',

    # Send the child's output to a per-ticket log file instead of this console. The file is written
    # when the ticket ENDS, not as it runs: the streams are drained into memory so a full pipe can
    # never block the child. For live output, run without -Quiet - it goes straight to this console.
    [switch] $Quiet,

    # Print the plan and exit without launching anything.
    [switch] $DryRun,

    # Ask the running instances to stop after the ticket each is on, then exit. With -Instance it
    # stops that one instance; without it, all of them.
    [switch] $Stop,

    # With -Stop: also kill the running children instead of letting them finish the current ticket.
    # The work already written to disk stays - every /spec-all records status and dev-log rows as it
    # goes - but the ticket in flight is left mid-run and its lease is dropped.
    [switch] $Kill,

    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,

    [switch] $Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot 'help.ps1') -Name 'scripts/utils/run-spec-queue.ps1'
    exit $LASTEXITCODE
}

$ErrorActionPreference = 'Stop'
Set-Location $RepoRoot

# ---------------------------------------------------------------------------------------------
# Preconditions
# ---------------------------------------------------------------------------------------------

$claudeCmd = Get-Command claude -ErrorAction SilentlyContinue
$claude = if ($claudeCmd) { $claudeCmd.Source } else { $null }
if (-not $claude) {
    $fallback = Join-Path $env:USERPROFILE '.local\bin\claude.exe'
    if (Test-Path $fallback) { $claude = $fallback }
}
if (-not $claude) {
    Write-Host "run-spec-queue: the Claude CLI was not found on PATH or at ~/.local/bin/claude.exe." -ForegroundColor Red
    exit 2
}

$preflight = Join-Path $RepoRoot 'scripts\spec_catalog\spec-next-preflight.ps1'
$select = Join-Path $RepoRoot 'scripts\spec_catalog\select.ps1'
$leaseScript = Join-Path $RepoRoot 'scripts\spec_catalog\ticket-lease.ps1'
foreach ($required in @($preflight, $select)) {
    if (-not (Test-Path $required)) {
        Write-Host "run-spec-queue: required script missing - $required" -ForegroundColor Red
        exit 2
    }
}

$tempDir = Join-Path $RepoRoot 'temp'
if (-not (Test-Path $tempDir)) { New-Item -ItemType Directory -Path $tempDir | Out-Null }
$stopFileAll = Join-Path $tempDir 'STOP-SPEC-QUEUE'
$stopFileMine = Join-Path $tempDir ("STOP-SPEC-QUEUE-{0}" -f $Instance)
$runDir = Join-Path $tempDir 'spec-queue'
if (-not (Test-Path $runDir)) { New-Item -ItemType Directory -Path $runDir | Out-Null }
$journal = Join-Path $runDir ("runs-{0}.jsonl" -f $Instance)

# ---------------------------------------------------------------------------------------------
# Stop mode - write the flag the running loops read, then leave. Nothing else in this script runs.
# ---------------------------------------------------------------------------------------------

if ($Stop) {
    $target = if ($PSBoundParameters.ContainsKey('Instance')) { $stopFileMine } else { $stopFileAll }
    $scope = if ($PSBoundParameters.ContainsKey('Instance')) { "instance '$Instance'" } else { 'every instance' }
    Set-Content -LiteralPath $target -Value ("stop requested {0}" -f (Get-Date -Format 's')) -Encoding UTF8
    Write-Host ("run-spec-queue: stop requested for {0} - {1}" -f $scope, $target) -ForegroundColor Yellow

    # Name what the stop is waiting for. The flag is only read between tickets, so a stop issued
    # during a 40-minute pipeline does nothing visible for 40 minutes - and silence is indistinguishable
    # from a broken command. Whoever asked for the stop is owed the reason it has not happened yet.
    $inFlight = @(Get-CimInstance Win32_Process -Filter "Name = 'claude.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -and $_.CommandLine -match '\s-p\s' })
    if ($inFlight.Count -eq 0) {
        Write-Host '  nothing is running - the next start clears this flag and proceeds.' -ForegroundColor DarkGray
    } else {
        Write-Host ''
        Write-Host ("  {0} ticket(s) still in flight - the stop takes effect when each one ENDS:" -f $inFlight.Count) -ForegroundColor Cyan
        foreach ($c in $inFlight) {
            $age = '?'
            try { $age = '{0:N0}' -f ((Get-Date) - $c.CreationDate).TotalMinutes } catch { $age = '?' }
            $ticket = if ($c.CommandLine -match '(S\d{4})') { $Matches[1] } else { '?' }
            Write-Host ("    pid {0,-7} {1,4} min   ticket {2}" -f $c.ProcessId, $age, $ticket) -ForegroundColor Cyan
        }
        Write-Host ''
        Write-Host '  A full pipeline routinely runs 30-60 minutes, so this is a wait, not a hang.' -ForegroundColor DarkGray
        Write-Host '  Watch it:   pwsh -NoProfile -File scripts/utils/monitor-spec-queue.ps1' -ForegroundColor DarkGray
        Write-Host '  Stop now:   .\a.ps1 rs -Kill    (abandons the ticket in flight; what it already wrote stands)' -ForegroundColor DarkGray
    }

    if ($Kill) {
        # Only the children this repository's runs started. A claude process serving the operator's
        # own interactive window must not be killed by a queue command.
        $victims = @(Get-CimInstance Win32_Process -Filter "Name = 'claude.exe'" -ErrorAction SilentlyContinue |
                Where-Object { $_.CommandLine -and $_.CommandLine -match '\s-p\s' })
        if ($victims.Count -eq 0) {
            Write-Host '  -Kill: no headless claude child is running.' -ForegroundColor DarkGray
        } else {
            foreach ($v in $victims) {
                Write-Host ("  -Kill: terminating pid {0}" -f $v.ProcessId) -ForegroundColor Red
                & taskkill.exe /PID $v.ProcessId /T /F 2>&1 | Out-Null
            }
            Write-Host '  the ticket in flight is left mid-run; what it already wrote to disk stands.' -ForegroundColor DarkYellow
        }
    }
    exit 0
}

# A stop file left behind by a previous run would stop this one before it began.
#
# The instance's own file is always cleared - starting this instance is an explicit instruction that
# supersedes an older stop aimed at it.
if (Test-Path $stopFileMine) {
    Write-Host "run-spec-queue: clearing this instance's leftover stop file - $stopFileMine" -ForegroundColor Yellow
    Remove-Item $stopFileMine -Force
}

# The shared file needs the opposite care, and getting it wrong strands the runner in two different
# ways. Nothing deletes it: the loop that reads it must leave it in place so the sibling instances
# see it too, so it outlives the run it stopped - and the NEXT run would then exit at its first check
# having done nothing, which reads exactly like a broken script. But blindly deleting it would let a
# freshly started instance cancel a stop that is still draining the other two.
#
# Live children are what tells the two apart: a stop still in progress has processes behind it.
$liveChildren = @(Get-CimInstance Win32_Process -Filter "Name = 'claude.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -match '\s-p\s' })
if (Test-Path $stopFileAll) {
    if ($liveChildren.Count -gt 0) {
        Write-Host "run-spec-queue: a stop is in progress - $($liveChildren.Count) child process(es) are still finishing." -ForegroundColor Red
        Write-Host "  Wait for them to exit (watch with: pwsh -NoProfile -File scripts/utils/monitor-spec-queue.ps1)," -ForegroundColor Red
        Write-Host "  then start again. Starting now would cancel the stop for the instances still draining." -ForegroundColor Red
        exit 3
    }
    Write-Host "run-spec-queue: clearing a stop flag left over from a finished run - $stopFileAll" -ForegroundColor Yellow
    Remove-Item $stopFileAll -Force
}

# ---------------------------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------------------------

function Get-TicketStatus {
    param([string] $Id)
    try {
        $raw = & pwsh -NoProfile -File $select -Id $Id -Format json 2>$null | Out-String
        if ([string]::IsNullOrWhiteSpace($raw)) { return $null }
        return ($raw | ConvertFrom-Json).status
    } catch {
        return $null
    }
}

function Get-NextQueuedTicket {
    param([string[]] $Exclude)
    # Never name this $args - PowerShell owns that automatic variable inside every function.
    $pfArgs = @('-NoProfile', '-File', $preflight)
    if ($Exclude.Count -gt 0) { $pfArgs += @('-Exclude', ($Exclude -join ',')) }
    $raw = & pwsh @pfArgs 2>$null | Out-String
    if ([string]::IsNullOrWhiteSpace($raw)) { return $null }
    try {
        $payload = $raw | ConvertFrom-Json
    } catch {
        Write-Host "run-spec-queue: preflight returned output that is not JSON - stopping." -ForegroundColor Red
        return 'PARSE_ERROR'
    }
    if (-not $payload.selected) { return $null }
    return $payload.selected
}

function Get-TicketRecord {
    param([string] $Id)
    try {
        $raw = & pwsh -NoProfile -File $select -Id $Id -Format json 2>$null | Out-String
        if ([string]::IsNullOrWhiteSpace($raw)) { return $null }
        return ($raw | ConvertFrom-Json)
    } catch {
        return $null
    }
}

function Select-ModelFor {
    <#
        The whole decision, in one place. It reads only what the catalog already knows - status and
        tier - so choosing a model costs nothing and never opens the spec.
    #>
    param($Ticket)

    if ($ModelPolicy -eq 'default') { return '' }
    if ($ModelPolicy -eq 'fixed') { return $Model }
    if ($null -eq $Ticket) { return $StrongModel }

    $status = [string]$Ticket.status

    # The two sources spell the tier differently and both are legitimate: select.ps1 returns the
    # integer 2, the preflight ranker returns the label "3 - Moderate (ad-hoc)". A plain TryParse
    # succeeds on the first and fails on the second, leaving tier 0 - so every queue-picked ticket
    # silently missed the cheap tier. Take the leading integer from whichever shape arrives.
    $tier = 0
    if ($null -ne $Ticket.tier -and ([string]$Ticket.tier) -match '^\s*(\d+)') {
        $tier = [int]$Matches[1]
    }

    # Implemented means the code is already in the tree and the run is an audit of it - reading and
    # comparing, not deciding. Tier 1-2 is a single-surface fix by the tier definition itself.
    if ($status -eq 'Implemented') { return $CheapModel }
    if ($tier -ge 1 -and $tier -le 2) { return $CheapModel }

    return $StrongModel
}

function Stop-ProcessTree {
    param([int] $ProcessId)
    # taskkill /T reaches the node and gradle children the CLI spawns; Stop-Process alone leaves them
    # holding BUILD.LOCK, which would stall every later ticket in the run.
    & taskkill.exe /PID $ProcessId /T /F 2>&1 | Out-Null
}

# ---------------------------------------------------------------------------------------------
# Build the work list
# ---------------------------------------------------------------------------------------------

$explicit = @()
if ($Ids.Trim()) {
    # The @() is load-bearing. A pipeline that yields ONE element returns a scalar string, and
    # indexing a string returns a character - so a single-id run picked "S" out of "S2014" and then
    # looked up a ticket that does not exist. Caught by the smoke test, 2026-08-25.
    $explicit = @($Ids.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

Write-Host ''
Write-Host 'run-spec-queue' -ForegroundColor Cyan
Write-Host ("  cli            : {0}" -f $claude)
Write-Host ("  order          : {0}" -f $(if ($explicit.Count) { "explicit ($($explicit -join ', '))" } else { 'PLAN/RELEASE_QUEUE.md via spec-next-preflight.ps1' }))
Write-Host ("  prompt         : {0}" -f $PromptTemplate)
Write-Host ("  permission     : {0}" -f $PermissionMode)
Write-Host ("  model policy   : {0}" -f $(
    switch ($ModelPolicy) {
        'tiered'  { "tiered ($StrongModel, $CheapModel for Implemented and tier 1-2)" }
        'fixed'   { "fixed ($Model)" }
        'default' { 'CLI default' }
    }))
Write-Host ("  timeout/ticket : {0} min" -f $TimeoutMinutes)
Write-Host ("  max tickets    : {0}" -f $(if ($MaxTickets -gt 0) { $MaxTickets } else { 'until the queue is exhausted' }))
Write-Host ("  instance       : {0}" -f $Instance)
Write-Host ("  stop files     : {0}  (all instances)" -f $stopFileAll)
Write-Host ("                   {0}  (this one)" -f $stopFileMine)
Write-Host ''

if ($DryRun) {
    if ($explicit.Count) {
        $i = 1
        foreach ($id in $explicit) {
            $rec = Get-TicketRecord -Id $id
            Write-Host ("  {0,2}. {1}  (status: {2}, tier {3}) -> model {4}" -f `
                    $i, $id, $rec.status, $rec.tier, (Select-ModelFor -Ticket $rec))
            $i++
        }
    } else {
        $next = Get-NextQueuedTicket -Exclude @()
        if ($next -and $next -ne 'PARSE_ERROR') {
            Write-Host ("  next up: {0}  (status: {1}, tier {2}) -> model {3}" -f `
                    $next.id, $next.status, $next.tier, (Select-ModelFor -Ticket $next))
            Write-Host '  the rest is re-ranked after every ticket, so only the head is knowable in advance.'
        } else {
            Write-Host '  nothing eligible.'
        }
    }
    Write-Host ''
    Write-Host 'run-spec-queue: dry run, nothing launched.' -ForegroundColor Yellow
    exit 0
}

# ---------------------------------------------------------------------------------------------
# The loop
# ---------------------------------------------------------------------------------------------

if ($StartDelaySeconds -gt 0) {
    $jitter = Get-Random -Minimum 0 -Maximum ($StartDelaySeconds + 1)
    Write-Host ("run-spec-queue: staggering {0}s before the first ranking." -f $jitter) -ForegroundColor DarkGray
    Start-Sleep -Seconds $jitter
}

$processed = New-Object System.Collections.Generic.List[string]
$results = New-Object System.Collections.Generic.List[object]
$explicitIndex = 0
$ranAny = $false

while ($true) {

    if (Test-Path $stopFileMine) {
        Write-Host ''
        Write-Host 'run-spec-queue: this instance was asked to stop - finishing here.' -ForegroundColor Yellow
        Remove-Item $stopFileMine -Force -ErrorAction SilentlyContinue
        break
    }
    if (Test-Path $stopFileAll) {
        Write-Host ''
        Write-Host 'run-spec-queue: shared stop file found - finishing here.' -ForegroundColor Yellow
        # Deliberately NOT removed: the other instances have to see it too. Whoever wrote it deletes it.
        break
    }

    if ($MaxTickets -gt 0 -and $processed.Count -ge $MaxTickets) {
        Write-Host ''
        Write-Host ("run-spec-queue: reached -MaxTickets {0}." -f $MaxTickets) -ForegroundColor Yellow
        break
    }

    # --- pick the next id -----------------------------------------------------------------
    $id = $null
    $ticket = $null
    if ($explicit.Count) {
        if ($explicitIndex -ge $explicit.Count) { break }
        $id = $explicit[$explicitIndex]
        $explicitIndex++
        $ticket = Get-TicketRecord -Id $id
    } else {
        $ticket = Get-NextQueuedTicket -Exclude $processed.ToArray()
        if ($ticket -eq 'PARSE_ERROR') { break }
        if (-not $ticket) {
            Write-Host ''
            Write-Host 'run-spec-queue: the queue has nothing else this run can pick up.' -ForegroundColor Yellow
            break
        }
        $id = $ticket.id
    }

    $statusBefore = if ($ticket) { [string]$ticket.status } else { Get-TicketStatus -Id $id }
    $childModel = Select-ModelFor -Ticket $ticket
    $started = Get-Date

    Write-Host ''
    Write-Host ('=' * 78) -ForegroundColor DarkGray
    Write-Host ("  {0}  [{1}]   ticket {2} of {3}" -f $id, $statusBefore, ($processed.Count + 1), $(if ($MaxTickets -gt 0) { $MaxTickets } else { '?' })) -ForegroundColor Cyan
    Write-Host ("  started {0}   model {1}   fresh context - this is a new process" -f `
            $started.ToString('HH:mm:ss'), $(if ($childModel) { $childModel } else { 'CLI default' })) -ForegroundColor DarkGray
    Write-Host ('=' * 78) -ForegroundColor DarkGray

    # --- run it in its own process --------------------------------------------------------
    $prompt = $PromptTemplate.Replace('{id}', $id)
    $childArgs = @('-p', $prompt, '--permission-mode', $PermissionMode)
    if ($childModel) { $childArgs += @('--model', $childModel) }

    # ProcessStartInfo.ArgumentList, never Start-Process -ArgumentList. Start-Process joins the array
    # into one command line WITHOUT quoting, so an element containing a space is split at the space:
    # '/spec-all S1949' reached the CLI as '-p /spec-all' plus a stray 'S1949', and every child then
    # aborted with "no argument" after a minute of Opus. .NET's ArgumentList escapes each element.
    # Measured 2026-08-25, three tickets in a row lost this way.
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $claude
    foreach ($a in $childArgs) { $psi.ArgumentList.Add([string]$a) }
    $psi.WorkingDirectory = $RepoRoot
    $psi.UseShellExecute = $false

    $logFile = $null
    if ($Quiet) {
        $logFile = Join-Path $runDir ("{0}.log" -f $id)
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        Write-Host ("  output -> {0}" -f $logFile) -ForegroundColor DarkGray
    }

    $outcome = 'ok'
    $exitCode = $null
    try {
        $proc = [System.Diagnostics.Process]::Start($psi)

        # Start draining before waiting. A redirected pipe that fills while nobody reads it blocks
        # the child forever, and the timeout below would then report a hang this script caused.
        $outTask = $null
        $errTask = $null
        if ($Quiet) {
            $outTask = $proc.StandardOutput.ReadToEndAsync()
            $errTask = $proc.StandardError.ReadToEndAsync()
        }

        if (-not $proc.WaitForExit($TimeoutMinutes * 60 * 1000)) {
            $outcome = 'timeout'
            Write-Host ''
            Write-Host ("  run-spec-queue: {0} exceeded {1} min - killing the process tree." -f $id, $TimeoutMinutes) -ForegroundColor Red
            Stop-ProcessTree -ProcessId $proc.Id
            # A killed child cannot release its ticket lease; drop it so a later run is not refused.
            & pwsh -NoProfile -File (Join-Path $RepoRoot 'scripts\spec_catalog\ticket-lease.ps1') -Verb Release -Id $id 2>&1 | Out-Null
        } else {
            $exitCode = $proc.ExitCode
        }

        if ($Quiet -and $logFile) {
            try {
                $body = ''
                if ($outTask -and $outTask.IsCompleted) { $body += $outTask.Result }
                if ($errTask -and $errTask.IsCompleted -and $errTask.Result) { $body += "`n--- stderr ---`n" + $errTask.Result }
                [System.IO.File]::WriteAllText($logFile, $body, [System.Text.Encoding]::UTF8)
            } catch {
                Write-Host ("  run-spec-queue: could not write {0} - {1}" -f $logFile, $_.Exception.Message) -ForegroundColor DarkYellow
            }
        }
    } catch {
        $outcome = 'launch-failed'
        Write-Host ("  run-spec-queue: could not launch the child for {0} - {1}" -f $id, $_.Exception.Message) -ForegroundColor Red
    }

    # Release the lease the child claimed, now that its process is gone. /spec-all asks the child to do
    # this itself before its final report, and the child does not reliably get there: S1884 claimed twice,
    # re-claimed at a phase boundary and never released, finishing 'ok' with the lease still held. A
    # cleanup step written in prose at the end of a 25-minute pipeline is not a finally block - this is.
    #
    # It has to be forced. The lease judges liveness by the write time of the owner's transcript, so one
    # that stopped a minute ago still reads as live for another 45 minutes. The parent knows better: it
    # started that process and watched it exit. The cost of leaving it is not cosmetic - the preflight
    # ranker skips a leased ticket, so the ticket that just advanced (the one most ready to continue) is
    # exactly the one locked out; measured with S1884 first in its package and passed over for the fourth.
    if (Test-Path -LiteralPath $leaseScript) {
        try {
            & pwsh -NoProfile -File $leaseScript -Verb Release -Id $id -Force *> $null
            if ($LASTEXITCODE -ne 0) {
                Write-Host ("  run-spec-queue: could not release the lease for {0} - it expires on its own." -f $id) -ForegroundColor DarkYellow
            }
        } catch {
            Write-Host ("  run-spec-queue: lease release for {0} failed - {1}" -f $id, $_.Exception.Message) -ForegroundColor DarkYellow
        }
    }

    $statusAfter = Get-TicketStatus -Id $id
    $elapsedSeconds = [int]((Get-Date) - $started).TotalSeconds
    $elapsed = [int]((Get-Date) - $started).TotalMinutes

    # A child that exits within a couple of minutes having changed nothing almost always lost the
    # claim to a parallel instance: /spec-all stage 0a.5 reports the holder and stops before any
    # work. Naming that outcome keeps the summary honest - it is not the same as "nothing to do".
    if ($outcome -eq 'ok' -and $statusBefore -eq $statusAfter -and $elapsedSeconds -lt 120) {
        $outcome = 'no-progress-or-claim-lost'
    }
    $ranAny = $true
    $processed.Add($id)

    # A ticket handed back with the status it started with has no autonomous next step; it stays in
    # $processed, so the re-ranking below never offers it again this run.
    $moved = ($statusBefore -ne $statusAfter)

    $record = [ordered]@{
        id           = $id
        model        = $childModel
        statusBefore = $statusBefore
        statusAfter  = $statusAfter
        moved        = $moved
        outcome      = $outcome
        exitCode     = $exitCode
        minutes      = $elapsed
        finishedAt   = (Get-Date).ToString('s')
    }
    $results.Add([pscustomobject]$record)
    Add-Content -Path $journal -Value ((([pscustomobject]$record) | ConvertTo-Json -Compress)) -Encoding UTF8

    $colour = if ($moved) { 'Green' } elseif ($outcome -ne 'ok') { 'Red' } else { 'Yellow' }
    Write-Host ''
    Write-Host ("  {0}: {1} -> {2}   ({3}, {4} min)" -f $id, $statusBefore, $statusAfter, $outcome, $elapsed) -ForegroundColor $colour
    if (-not $moved) {
        Write-Host ("  {0} did not move - dropped for the rest of this run." -f $id) -ForegroundColor DarkYellow
    }
}

# ---------------------------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------------------------

Write-Host ''
Write-Host ('=' * 78) -ForegroundColor DarkGray
Write-Host '  run-spec-queue summary' -ForegroundColor Cyan
Write-Host ('=' * 78) -ForegroundColor DarkGray

if ($results.Count -eq 0) {
    Write-Host '  nothing ran.'
} else {
    $results | Format-Table -AutoSize id, statusBefore, statusAfter, outcome, minutes | Out-String | Write-Host
    $movedCount = ($results | Where-Object { $_.moved }).Count
    Write-Host ("  {0} ticket(s) run, {1} moved, {2} stayed put." -f $results.Count, $movedCount, ($results.Count - $movedCount))
    Write-Host ("  journal: {0}" -f $journal)
}
Write-Host ''

if (-not $ranAny) { exit 3 }
exit 0
