<#
.SYNOPSIS
  Runs /spec-all for release-queue tickets one at a time.

.DESCRIPTION
  Reads ticket order from PLAN/RELEASE_QUEUE.md, skips every Block* and terminal
  status, claims a ticket lease, launches Claude Code for that ticket, and polls
  the catalog until Claude Code exits.

  One Claude Code session is NOT one ticket. /spec-all is resume-first: a session
  ends at a phase boundary or when its context runs out, exits 0, and leaves the
  ticket mid-flight - the documented continuation is to run /spec-all <id> again,
  which picks the ticket up from its current state. A non-terminal status at exit
  is therefore a failure only when the session achieved nothing at all. This
  driver relaunches the same ticket while each attempt moves it (catalog status
  changed, or anything under the ticket's PLAN files was written), bounded by
  -MaxAttemptsPerTicket, and stops for operator review when an attempt makes no
  progress. A terminal status (Implemented, Verified, or Block*) advances the
  loop, so unfinished work is never skipped.

  Three coordination guards, all taken from 2026-08-08 runs where sessions
  collided on one ticket:
    - A ticket whose id appears in the reason of a held or queued coordination
      lock - any of the five domains, code or build - is passed over: a sibling
      is driving it right now. Only /spec-next claims a lease, so "unleased"
      does not mean "free".
    - temp/spec-all-queue.lock admits one driver per checkout. Several drivers
      rank the same queue, so they all pick the same head.
    - Tickets are taken through scripts/spec_catalog/ticket-lease.ps1, so one a
      live sibling session already drives is passed over instead of worked twice.
      The lease store keys ownership off CLAUDE_CODE_SESSION_ID and otherwise
      falls back to the calling process's pid - useless here, since every call is
      a fresh pwsh - so this driver supplies a stable id for its own lease calls
      only, never for the Claude Code session it starts, which owns its own.

.EXAMPLE
  pwsh -NoProfile -File scripts/spec_catalog/run-spec-all-queue.ps1

.EXAMPLE
  pwsh -NoProfile -File scripts/spec_catalog/run-spec-all-queue.ps1 -DryRun

.NOTES
  Exit codes:
    0  Queue exhausted, MaxTickets reached, or DryRun completed.
    1  Invalid environment, a helper script failed, or Claude Code could not be started.
    2  RELEASE_QUEUE.md contains a malformed ticket line.
    3  Ticket stalled: an attempt made no progress, or the attempt budget ran out,
       with the ticket still in a non-terminal status.
    4  Another instance of this driver is already running in this checkout.
#>
[CmdletBinding()]
param(
    [ValidateRange(5, 300)]
    [int] $PollSeconds = 30,
    [ValidateRange(0, 10000)]
    [int] $MaxTickets = 0,
    [ValidateRange(1, 20)]
    [int] $MaxAttemptsPerTicket = 3,
    [ValidateRange(1, 120)]
    [int] $HeartbeatMinutes = 5,
    [string] $ClaudeCommand = 'claude',
    [switch] $DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\utils\agent-lock.ps1')

$rootPath = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$queuePath = Join-Path $rootPath 'PLAN\RELEASE_QUEUE.md'
$selectScript = Join-Path $PSScriptRoot 'select.ps1'
$leaseScript = Join-Path $PSScriptRoot 'ticket-lease.ps1'
$instanceLockPath = Join-Path $rootPath 'temp\spec-all-queue.lock'
$pwshCommand = (Get-Command pwsh -ErrorAction Stop).Source
$driverSessionId = "queue-driver-$PID"
$terminalStatuses = @('Implemented', 'Verified')
# The optional trailing group is the live-occupancy marker the release files carry since
# 2026-09-01 ('[taken 15:42, /spec-all, be08adb0]'). Without it every taken row reads as a
# malformed line and this runner exits 2 on the owner's own plan; the status group is lazy so
# the marker cannot be swallowed into the status instead.
$ticketPattern = '^\s*(?<release>\d+|--)\s+(?<ticket>S\d{4}_[a-z0-9][a-z0-9-]*)\s+(?<changed>\d{4}-\d{2}-\d{2})\s+(?<status>\S(?:.*?\S)?)(?:\s+\[taken\s[^\]]*\])?\s*$'

function Get-CatalogRecord {
    param([Parameter(Mandatory)][string] $TicketId)

    $output = & $pwshCommand -NoProfile -File $selectScript -Id $TicketId -Format json
    if ($LASTEXITCODE -ne 0) {
        throw "Cannot read catalog status for $TicketId."
    }
    if (-not $output -or $output -eq '[]') {
        throw "Ticket $TicketId is not present in the catalog."
    }
    return ($output | ConvertFrom-Json)
}

function Test-TerminalStatus {
    param([Parameter(Mandatory)][string] $Status)

    return $Status -in $terminalStatuses -or $Status -like 'Block*'
}

function Invoke-TicketLease {
    param(
        [Parameter(Mandatory)][ValidateSet('Claim', 'Release', 'Status')][string] $Verb,
        [string] $TicketId
    )

    $previousSessionId = $env:CLAUDE_CODE_SESSION_ID
    $env:CLAUDE_CODE_SESSION_ID = $driverSessionId
    try {
        $leaseArgs = @('-NoProfile', '-File', $leaseScript, '-Verb', $Verb, '-Json', '-Reason', 'run-spec-all-queue.ps1')
        if ($TicketId) { $leaseArgs += @('-Id', $TicketId) }
        $output = & $pwshCommand @leaseArgs
        return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($output -join '') }
    } finally {
        if ([string]::IsNullOrWhiteSpace($previousSessionId)) {
            Remove-Item Env:\CLAUDE_CODE_SESSION_ID -ErrorAction SilentlyContinue
        } else {
            $env:CLAUDE_CODE_SESSION_ID = $previousSessionId
        }
    }
}

function Get-TicketBusySignal {
    <#
        Answers "is another session already driving this ticket" for a sibling the lease store
        cannot see. Only /spec-next claims a lease; a ticket driven by /spec-dev or /spec-all
        claims none, so an unleased ticket is not a free ticket. What such a session does leave is
        its ticket id inside the reason of the lock it holds or waits for ("spec-dev S1178 phase
        03") - the same signal S1448 already trusts for lease liveness. Both queues self-clean on
        read, so a dead sibling's entry disappears rather than parking the ticket forever.

        Observed 2026-08-08: without this the driver started /spec-all S1178 while another session
        sat in the CODE.LOCK queue on that ticket's phase 03. That session's own Stage 0 refused to
        write anything, the attempt came back empty, and the whole queue stopped on exit 3.
    #>
    param([Parameter(Mandatory)][string] $TicketId)

    $pattern = [regex]::Escape($TicketId)
    # S2170: every concrete domain, never the two bare names. A bare name resolves to the ONE
    # pre-split file that nothing writes any more, so this test read "free" for every sibling and
    # the driver went back to starting tickets a live session was already on - the exact failure
    # the function was written to prevent.
    foreach ($lockName in @(Resolve-AgentLockDomains -Name 'Code') + @(Resolve-AgentLockDomains -Name 'Build')) {
        $status = Get-AgentLockStatus -Name $lockName
        if ($status.Exists -and -not $status.Stale -and [string] $status.Reason -match $pattern) {
            return "$($lockName.ToUpper()).LOCK held for '$([string] $status.Reason)'"
        }

        foreach ($queued in @(Get-AgentLockQueue -Name $lockName)) {
            if ('reason' -notin $queued.PSObject.Properties.Name) { continue }
            $reason = [string] $queued.reason
            if ($reason -match $pattern) { return "$($lockName.ToUpper()).LOCK queue: '$reason'" }
        }
    }

    return $null
}

function Get-LiveInstanceLock {
    if (-not (Test-Path -LiteralPath $instanceLockPath)) { return $null }
    try { $data = Get-Content -LiteralPath $instanceLockPath -Raw -Encoding UTF8 | ConvertFrom-Json }
    catch { return $null }

    $fields = $data.PSObject.Properties.Name
    if ('pid' -notin $fields -or 'startTicks' -notin $fields) { return $null }

    $owner = Get-Process -Id ([int] $data.pid) -ErrorAction SilentlyContinue
    if ($null -eq $owner) { return $null }
    # A recycled pid is a different process, so the number alone cannot prove the owner is alive.
    try { $ownerStartTicks = $owner.StartTime.Ticks } catch { return $null }
    if ($ownerStartTicks -ne [long] $data.startTicks) { return $null }

    return $data
}

function Enter-InstanceLock {
    $live = Get-LiveInstanceLock
    if ($null -ne $live -and [int] $live.pid -ne $PID) {
        $startedAt = if ('startedAt' -in $live.PSObject.Properties.Name) { [string] $live.startedAt } else { 'unknown' }
        $message = "Another release-queue driver is already running (PID {0}, started {1}). Two drivers rank the same queue and drive the same ticket from two sessions. Stop it, or delete {2} if the process is gone." -f `
            $live.pid, $startedAt, $instanceLockPath
        Write-Error $message -ErrorAction Continue
        exit 4
    }

    New-Item -ItemType Directory -Path (Split-Path -Parent $instanceLockPath) -Force | Out-Null
    $payload = [ordered]@{
        schema     = 1
        pid        = $PID
        startTicks = (Get-Process -Id $PID).StartTime.Ticks
        host       = $env:COMPUTERNAME
        startedAt  = (Get-Date).ToString('s')
    }
    [System.IO.File]::WriteAllText($instanceLockPath, ($payload | ConvertTo-Json -Compress))

    # Two drivers starting in the same instant both pass the check above; the file keeps one
    # winner, so whoever does not read its own pid back stands down.
    $confirmed = Get-LiveInstanceLock
    if ($null -eq $confirmed -or [int] $confirmed.pid -ne $PID) {
        Write-Error 'Another release-queue driver claimed the instance lock at the same moment.' -ErrorAction Continue
        exit 4
    }
}

function Exit-InstanceLock {
    if (-not (Test-Path -LiteralPath $instanceLockPath)) { return }
    try { $data = Get-Content -LiteralPath $instanceLockPath -Raw -Encoding UTF8 | ConvertFrom-Json }
    catch { return }
    if ('pid' -in $data.PSObject.Properties.Name -and [int] $data.pid -eq $PID) {
        Remove-Item -LiteralPath $instanceLockPath -Force -ErrorAction SilentlyContinue
    }
}

function Get-TicketStamp {
    <#
        Progress signal for one attempt. Catalog status alone is too coarse: a session can close a
        whole phase without touching the catalog (status stays In Progress from first phase to
        last), so a status-only comparison reads real work as a stall. The ticket's PLAN files -
        the spec and its tactical folder - are written by every phase, so their newest write time
        is the second half of the signal.
    #>
    param([Parameter(Mandatory)][string] $TicketId)

    $record = Get-CatalogRecord -TicketId $TicketId
    $specPath = Join-Path $rootPath ([string] $record.file)
    $folderPath = [System.IO.Path]::ChangeExtension($specPath, $null)

    $items = @()
    if (Test-Path -LiteralPath $specPath) { $items += Get-Item -LiteralPath $specPath }
    if (Test-Path -LiteralPath $folderPath) { $items += @(Get-ChildItem -LiteralPath $folderPath -Recurse -File) }

    $latestWriteUtc = [datetime]::MinValue
    foreach ($item in $items) {
        if ($item.LastWriteTimeUtc -gt $latestWriteUtc) { $latestWriteUtc = $item.LastWriteTimeUtc }
    }

    return [pscustomobject]@{
        Status       = [string] $record.status
        PlanWriteUtc = $latestWriteUtc
    }
}

function Get-NextQueueTicket {
    param([switch] $Claim)

    if (-not (Test-Path -LiteralPath $queuePath)) {
        throw "Release queue not found: $queuePath"
    }

    # Read holders, not just ids: this driver's own lease on the ticket it just ran is in the store
    # too, and reporting that one as a sibling's sends whoever reads the console hunting a session
    # that does not exist.
    $leases = @()
    if (-not $Claim) {
        $held = Invoke-TicketLease -Verb Status
        if ($held.ExitCode -eq 0 -and $held.Output) { $leases = @($held.Output | ConvertFrom-Json) }
    }

    foreach ($line in Get-Content -LiteralPath $queuePath -Encoding UTF8) {
        if ($line -notmatch '^\s*(?:\d+|--)\s+') { continue }
        if ($line -notmatch $ticketPattern) {
            Write-Error "Malformed release-queue ticket line: $line" -ErrorAction Continue
            exit 2
        }

        # Read every capture before the first external call - any later -match replaces $Matches,
        # and an empty id would launch a bare /spec-all, which aborts at Stage 0 and burns a session.
        $queueName = [string] $Matches.ticket
        $release = [string] $Matches.release
        $ticketId = $queueName.Substring(0, 5)
        $slug = $queueName.Substring(6)

        $status = [string] (Get-CatalogRecord -TicketId $ticketId).status
        if (Test-TerminalStatus -Status $status) { continue }

        $busy = Get-TicketBusySignal -TicketId $ticketId
        if ($null -ne $busy) {
            Write-Host "$ticketId is driven by another session ($busy) - passing over it."
            continue
        }

        if ($Claim) {
            $lease = Invoke-TicketLease -Verb Claim -TicketId $ticketId
            if ($lease.ExitCode -eq 3) {
                Write-Host "$ticketId is leased by a live sibling session - passing over it."
                continue
            }
            if ($lease.ExitCode -ne 0) {
                throw "Cannot claim the ticket lease for $ticketId (ticket-lease.ps1 exited $($lease.ExitCode))."
            }
        } else {
            # Select-Object, not [0]: under StrictMode Latest indexing an empty result throws.
            $holder = $leases | Where-Object { [string] $_.id -eq $ticketId } | Select-Object -First 1
            if ($null -ne $holder) {
                if ([string] $holder.sessionId -eq $driverSessionId) {
                    Write-Host "$ticketId is the ticket this driver just ran - passing over it."
                } else {
                    Write-Host "$ticketId is leased by a live sibling session ($($holder.sessionId)) - passing over it."
                }
                continue
            }
        }

        return [pscustomobject]@{
            Id        = $ticketId
            QueueName = $queueName
            Slug      = $slug
            Release   = $release
            Status    = $status
        }
    }

    return $null
}

function Show-NextQueuePreview {
    <#
        Every exit path names the ticket this driver would take next. Without it the run ends on
        "Reached MaxTickets" or on a stall and says nothing about what is now at the head of the
        queue, so the operator has to re-derive the answer by hand.
    #>
    param([Parameter(Mandatory)][string] $Label)

    try { $next = Get-NextQueueTicket }
    catch {
        Write-Host "$Label - cannot read the queue: $($_.Exception.Message)"
        return
    }

    if ($null -eq $next) {
        Write-Host "$Label - nothing eligible: every queue ticket is terminal, blocked, or held by a live sibling."
        return
    }
    Write-Host "$Label - $($next.Id) $($next.Slug) (rel $($next.Release), status: $($next.Status))"
}

function Start-SpecAll {
    param(
        [Parameter(Mandatory)] $Ticket,
        [Parameter(Mandatory)][string] $Status,
        [Parameter(Mandatory)][int] $Attempt
    )

    if ($Ticket.Id -notmatch '^S\d{4}$') {
        throw "Refusing to start: '$($Ticket.Id)' is not a ticket id. A bare /spec-all aborts at Stage 0 and spends a whole session on nothing."
    }

    $ticketTempPath = Join-Path $rootPath (Join-Path 'temp' $Ticket.Id)
    New-Item -ItemType Directory -Path $ticketTempPath -Force | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $stdoutPath = Join-Path $ticketTempPath "spec-all_$stamp.stdout.log"
    $stderrPath = Join-Path $ticketTempPath "spec-all_$stamp.stderr.log"
    $prompt = "/spec-all $($Ticket.Id)"
    Write-Host "Attempt $Attempt/$MaxAttemptsPerTicket - starting $prompt : $($Ticket.Slug) (rel $($Ticket.Release), status: $Status)."
    try {
        $commandPath = (Get-Command $ClaudeCommand -ErrorAction Stop).Source
        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = $commandPath
        $startInfo.WorkingDirectory = $rootPath
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        [void] $startInfo.ArgumentList.Add('--permission-mode')
        [void] $startInfo.ArgumentList.Add('auto')
        [void] $startInfo.ArgumentList.Add('--print')
        [void] $startInfo.ArgumentList.Add($prompt)

        $process = [System.Diagnostics.Process]::new()
        $process.StartInfo = $startInfo
        [void] $process.Start()
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process | Add-Member -NotePropertyName StdoutTask -NotePropertyValue $stdoutTask
        $process | Add-Member -NotePropertyName StderrTask -NotePropertyValue $stderrTask
        $process | Add-Member -NotePropertyName StdoutPath -NotePropertyValue $stdoutPath
        $process | Add-Member -NotePropertyName StderrPath -NotePropertyValue $stderrPath
        return $process
    } catch {
        throw "Cannot start Claude Code command '$ClaudeCommand': $($_.Exception.Message)"
    }
}

function Wait-SpecAll {
    param(
        [Parameter(Mandatory)] $Process,
        [Parameter(Mandatory)][string] $TicketId,
        [Parameter(Mandatory)][string] $Status
    )

    $observedStatus = $Status
    $startedAt = Get-Date
    $lastBeatAt = $startedAt
    $terminalObserved = $false
    while (-not $Process.HasExited -and -not $terminalObserved) {
        Start-Sleep -Seconds $PollSeconds
        # Re-claiming our own lease is a no-op that refreshes its heartbeat, which is what keeps a
        # sibling ranker from judging a multi-hour ticket abandoned and taking it.
        [void] (Invoke-TicketLease -Verb Claim -TicketId $TicketId)
        $currentStatus = [string] (Get-CatalogRecord -TicketId $TicketId).status
        if ($currentStatus -ne $observedStatus) {
            Write-Host "$TicketId status: $observedStatus -> $currentStatus"
            $observedStatus = $currentStatus
        }
        if (Test-TerminalStatus -Status $observedStatus) {
            $terminalObserved = $true
            break
        }
        # A /spec-all session runs for hours and prints nothing until it exits (--print buffers the
        # whole answer), so without this the console looks hung. One line per HeartbeatMinutes.
        if (((Get-Date) - $lastBeatAt).TotalMinutes -ge $HeartbeatMinutes) {
            $runningMinutes = [int] ((Get-Date) - $startedAt).TotalMinutes
            Write-Host "  .. $TicketId running $runningMinutes min (status: $observedStatus)"
            $lastBeatAt = Get-Date
        }
    }

    if ($terminalObserved -and -not $Process.HasExited) {
        Write-Host "  Stopping Claude Code for $TicketId after terminal status $observedStatus."
        try { $Process.Kill($true) } catch { }
        $Process.WaitForExit()
    }

    [System.IO.File]::WriteAllText($Process.StdoutPath, $Process.StdoutTask.GetAwaiter().GetResult())
    [System.IO.File]::WriteAllText($Process.StderrPath, $Process.StderrTask.GetAwaiter().GetResult())
    Write-Host "  Claude Code exited $($Process.ExitCode); log: $($Process.StdoutPath)"
}

$claude = Get-Command $ClaudeCommand -ErrorAction SilentlyContinue
if ($null -eq $claude) {
    Write-Error "Claude Code command not found: $ClaudeCommand" -ErrorAction Continue
    exit 1
}

Enter-InstanceLock
try {
    $ticketBudget = if ($MaxTickets -eq 0) { 'unbounded' } else { $MaxTickets }
    Write-Host "Release-queue driver: tickets $ticketBudget, attempts per ticket $MaxAttemptsPerTicket, poll ${PollSeconds}s."

    $completedCount = 0
    while ($MaxTickets -eq 0 -or $completedCount -lt $MaxTickets) {
        if ($DryRun) {
            Show-NextQueuePreview -Label 'Dry run: would take'
            exit 0
        }

        $ticket = Get-NextQueueTicket -Claim
        if ($null -eq $ticket) {
            Write-Host 'Release queue has no eligible non-blocked ticket that is free to take.'
            exit 0
        }

        try {
            $attempt = 0
            while ($true) {
                $attempt++
                # Re-check between attempts: a sibling can start on this ticket while attempt N runs,
                # and relaunching into that is how a whole session gets spent on a refusal.
                if ($attempt -gt 1) {
                    $busyNow = Get-TicketBusySignal -TicketId $ticket.Id
                    if ($null -ne $busyNow) {
                        Write-Host "$($ticket.Id) is now driven by another session ($busyNow) - leaving it to that session and moving on."
                        break
                    }
                }

                $before = Get-TicketStamp -TicketId $ticket.Id
                $process = Start-SpecAll -Ticket $ticket -Status $before.Status -Attempt $attempt
                Wait-SpecAll -Process $process -TicketId $ticket.Id -Status $before.Status

                $after = Get-TicketStamp -TicketId $ticket.Id
                if (Test-TerminalStatus -Status $after.Status) {
                    Write-Host "$($ticket.Id) finished with terminal status: $($after.Status)"
                    $completedCount++
                    break
                }

                $progressed = ($after.Status -ne $before.Status) -or ($after.PlanWriteUtc -gt $before.PlanWriteUtc)
                if (-not $progressed) {
                    $message = "Attempt {0} for {1} exited {2} and moved nothing - status is still {3}, and no PLAN file was written. Read {4} and resume manually." -f `
                        $attempt, $ticket.Id, $process.ExitCode, $after.Status, $process.StdoutPath
                    Write-Error $message -ErrorAction Continue
                    Show-NextQueuePreview -Label 'Queue behind it'
                    exit 3
                }

                if ($attempt -ge $MaxAttemptsPerTicket) {
                    $message = "{0} advanced but is still {1} after {2} attempts (-MaxAttemptsPerTicket). Read {3} and resume manually, or re-run with a larger budget." -f `
                        $ticket.Id, $after.Status, $attempt, $process.StdoutPath
                    Write-Error $message -ErrorAction Continue
                    Show-NextQueuePreview -Label 'Queue behind it'
                    exit 3
                }

                Write-Host "$($ticket.Id) advanced to $($after.Status) but is not finished - resuming it in a fresh session."
            }
        } finally {
            $release = Invoke-TicketLease -Verb Release -TicketId $ticket.Id
            if ($release.ExitCode -ne 0) {
                Write-Host "Warning: could not release the lease on $($ticket.Id) (exit $($release.ExitCode)); it expires on its own."
            }
        }
    }

    Write-Host "Reached MaxTickets: $MaxTickets"
    Show-NextQueuePreview -Label 'Next in the queue'
    exit 0
} finally {
    Exit-InstanceLock
}
