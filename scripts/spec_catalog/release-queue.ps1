# release-queue.ps1 - operator CLI for PLAN/RELEASE_QUEUE.md, the owner's release plan.
#
# The queue answers "what ships in which release package, in what order" - a question the
# catalog cannot answer, because the catalog knows status but not intent. Division of labour:
#
#   catalog (PLAN/spec-catalog.jsonl)  owns  status, priority, dates - written by the skills
#   queue   (PLAN/RELEASE_QUEUE.md)    owns  release assignment + order - written by the OWNER
#
# Every catalog mutation reconciles the queue automatically (Sync-ReleaseQueue, called from
# Write-Catalog in _lib.ps1), so no skill needs to know this file exists. That sync never
# reorders lines: a script may add, update a status/date, or drop an archived ticket, but the
# sequence itself is the owner's plan and stays untouched.
#
# Actions:
#   -Reconcile            Force a sync against the catalog now (the automatic path, on demand).
#   -Validate             Report drift: unknown tickets, duplicates, status mismatches.
#   -List [-Release N] [-WithLeases]
#                         Print the queue, optionally one release block and its live leases.
#   -SetCurrent N         Point the "current-release:" marker at package N.
#   -Ship -Release N      Move block N into PLAN/RELEASE_QUEUE_DONE.md and advance the marker.
#         [-Version X]    Stamp the shipped block with the released version name.
#         [-DryRun]       Show what -Ship would move, change nothing.
#
# Usage:
#   pwsh -NoProfile -File scripts/spec_catalog/release-queue.ps1 -Reconcile
#   pwsh -NoProfile -File scripts/spec_catalog/release-queue.ps1 -List -Release 30
#   pwsh -NoProfile -File scripts/spec_catalog/release-queue.ps1 -List -Release 30 -WithLeases
#   pwsh -NoProfile -File scripts/spec_catalog/release-queue.ps1 -Ship -Release 30 -Version 2.60.7300.900
#
# Exit codes:
#   0  success (for -Validate: no drift).
#   1  error, or -Validate found drift.
#   2  cannot verify - PLAN/RELEASE_QUEUE.md is missing (seed it before using the queue).
[CmdletBinding(DefaultParameterSetName = 'List')]
param(
    [Parameter(ParameterSetName = 'Reconcile')][switch] $Reconcile,
    [Parameter(ParameterSetName = 'Validate')][switch] $Validate,
    [Parameter(ParameterSetName = 'List')][switch] $List,
    # List the finished side (PLAN/RELEASE_READY.md) instead of the work-remaining queue.
    [Parameter(ParameterSetName = 'List')][switch] $Ready,
    # Append a read-only projection of active ticket leases to the selected queue rows.
    [Parameter(ParameterSetName = 'List')][switch] $WithLeases,
    [Parameter(ParameterSetName = 'SetCurrent', Mandatory = $true)][int] $SetCurrent,
    [Parameter(ParameterSetName = 'Ship', Mandatory = $true)][switch] $Ship,
    [Parameter(ParameterSetName = 'Ship', Mandatory = $true)]
    [Parameter(ParameterSetName = 'List')][string] $Release = '',
    [Parameter(ParameterSetName = 'Ship')][string] $Version = '',
    [Parameter(ParameterSetName = 'Ship')][switch] $DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_lib.ps1')

trap {
    Write-Error $_ -ErrorAction Continue
    exit 1
}

$queuePath = Get-ReleaseQueuePath
if (-not (Test-Path $queuePath)) {
    Write-Error "Release queue not found: $queuePath" -ErrorAction Continue
    exit 2
}

function Get-QueueTickets {
    param([Parameter(Mandatory)][AllowEmptyCollection()] $Lines)
    return @($Lines | Where-Object { $_.Kind -eq 'ticket' })
}

function Get-ActiveTicketLeases {
    $leaseScript = Join-Path $PSScriptRoot 'ticket-lease.ps1'
    $fixturePath = $env:FMS_TICKET_LEASE_STATUS_FIXTURE
    if (-not [string]::IsNullOrWhiteSpace($fixturePath)) {
        if (-not (Test-Path -LiteralPath $fixturePath)) {
            throw "Ticket lease fixture not found: $fixturePath"
        }
        $raw = Get-Content -LiteralPath $fixturePath -Raw -Encoding utf8
        if (-not $raw) { return @() }
        return @($raw | ConvertFrom-Json)
    }
    $pwsh = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
        "$env:ProgramFiles\PowerShell\7\pwsh.exe"
    } else {
        'pwsh'
    }
    $raw = & $pwsh -NoProfile -File $leaseScript -Verb Status -Json
    if ($LASTEXITCODE -ne 0) {
        throw "ticket-lease Status failed with exit code $LASTEXITCODE"
    }
    if (-not $raw) { return @() }
    return @($raw | ConvertFrom-Json)
}

switch ($PSCmdlet.ParameterSetName) {

    'Reconcile' {
        $records = Read-Catalog
        $before = (Get-QueueTickets -Lines (Read-ReleaseQueue)).Count
        Sync-ReleaseQueue -Records $records
        $after = (Get-QueueTickets -Lines (Read-ReleaseQueue)).Count
        Write-Output ("release-queue: reconciled - {0} ticket(s) before, {1} after" -f $before, $after)
        exit 0
    }

    'Validate' {
        $records = Read-Catalog
        $byId = @{}
        foreach ($r in $records) { $byId[$r.id] = $r }

        $tickets = @()
        $tickets += Get-QueueTickets -Lines (Read-ReleaseQueue)
        $tickets += Get-QueueTickets -Lines (Read-ReleaseReady)
        $problems = New-Object System.Collections.Generic.List[string]
        $seen = @{}

        foreach ($t in $tickets) {
            if ($seen.ContainsKey($t.Id)) {
                $problems.Add("duplicate line: $($t.Ticket)")
                continue
            }
            $seen[$t.Id] = $true
            if (-not $byId.ContainsKey($t.Id)) {
                $problems.Add("not in the active catalog (archived or deleted?): $($t.Ticket)")
                continue
            }
            $rec = $byId[$t.Id]
            if ($rec.status -ne $t.Status) {
                $problems.Add("status drift: $($t.Ticket) queue='$($t.Status)' catalog='$($rec.status)'")
            }
            $expected = Get-TicketBaseName -File $rec.file
            if ($expected -ne $t.Ticket) {
                $problems.Add("renamed spec file: queue='$($t.Ticket)' catalog='$expected'")
            }
        }

        foreach ($r in $records) {
            # A ready ticket in neither file shipped in an earlier package - not drift.
            if (Test-ReleaseReadyStatus -Status $r.status) { continue }
            if (-not $seen.ContainsKey($r.id)) {
                $problems.Add("missing from the queue: $(Get-TicketBaseName -File $r.file) [$($r.status)]")
            }
        }

        if ($problems.Count -eq 0) {
            Write-Output ("release-queue: OK - {0} ticket(s), no drift" -f $tickets.Count)
            exit 0
        }
        Write-Output ("release-queue: DRIFT - {0} problem(s)" -f $problems.Count)
        foreach ($p in $problems) { Write-Output "  $p" }
        Write-Output "Fix with: release-queue.ps1 -Reconcile"
        exit 1
    }

    'SetCurrent' {
        $lines = Read-ReleaseQueue
        $updated = $false
        foreach ($line in $lines) {
            if ($line.Kind -eq 'verbatim' -and $line.Text -match '^\s*current-(?:next-)?release:\s*\d+\s*$') {
                $line.Text = "current-next-release: $SetCurrent"
                $updated = $true
            }
        }
        if (-not $updated) {
            Write-Error "No 'current-next-release:' marker in $queuePath" -ErrorAction Continue
            exit 1
        }
        Write-ReleaseQueue -Lines $lines
        Write-Output "release-queue: current release -> $SetCurrent"
        exit 0
    }

    'Ship' {
        # The shipped content is the READY file - the queue holds what did NOT make it.
        $readyPath = Get-ReleaseReadyPath
        $lines = Read-ReleaseFile -Path $readyPath
        $shipping = @(Get-QueueTickets -Lines $lines | Where-Object { $_.Release -eq $Release })
        if ($shipping.Count -eq 0) {
            Write-Error "Nothing ready for release $Release (PLAN/RELEASE_READY.md)" -ErrorAction Continue
            exit 1
        }

        # Leftovers stay the owner's call: they are re-sorted into a later package by hand.
        $leftover = @(Get-QueueTickets -Lines (Read-ReleaseQueue) | Where-Object { $_.Release -eq $Release })
        if ($leftover.Count -gt 0) {
            Write-Output ("release-queue: WARNING - {0} unfinished ticket(s) still assigned to release {1}:" -f $leftover.Count, $Release)
            foreach ($u in $leftover) { Write-Output ("  {0,-18} {1}" -f $u.Status, $u.Ticket) }
            Write-Output "  Move them to a later package (or --) in PLAN/RELEASE_QUEUE.md; they do not ship."
        }

        if ($DryRun) {
            Write-Output ("release-queue: DRY RUN - would move {0} ready ticket(s) out of release {1}" -f $shipping.Count, $Release)
            exit 0
        }

        $stamp = Get-Today
        $title = if ($Version) { "## release $Release - v$Version - $stamp" } else { "## release $Release - $stamp" }
        $block = New-Object System.Collections.Generic.List[string]
        $block.Add($title)
        $block.Add('')
        foreach ($t in $shipping) {
            $block.Add((Format-ReleaseQueueLine -Release $t.Release -Ticket $t.Ticket -Changed $t.Changed -Status $t.Status))
        }
        $block.Add('')

        $donePath = Get-ReleaseQueueDonePath
        $existing = if (Test-Path $donePath) { @(Get-Content -LiteralPath $donePath -Encoding UTF8) } else { @() }
        # Newest release on top: the head of the file is always the last thing shipped.
        $headerLen = 0
        while ($headerLen -lt $existing.Count -and $existing[$headerLen] -notmatch '^## release ') { $headerLen++ }
        $merged = New-Object System.Collections.Generic.List[string]
        for ($i = 0; $i -lt $headerLen; $i++) { $merged.Add($existing[$i]) }
        foreach ($b in $block) { $merged.Add($b) }
        for ($i = $headerLen; $i -lt $existing.Count; $i++) { $merged.Add($existing[$i]) }

        $payload = [string]::Join("`r`n", $merged.ToArray()) + "`r`n"
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText("$donePath.tmp", $payload, $utf8NoBom)
        Move-Item -LiteralPath "$donePath.tmp" -Destination $donePath -Force

        # Drop the shipped block from the ready file.
        $remaining = New-Object System.Collections.Generic.List[object]
        foreach ($line in $lines) {
            if ($line.Kind -eq 'ticket' -and $line.Release -eq $Release) { continue }
            $remaining.Add($line)
        }
        Write-ReleaseFile -Path $readyPath -Lines $remaining

        # Advance the marker, which lives in the queue - the file the owner plans in.
        $queueLines = Read-ReleaseQueue
        foreach ($line in $queueLines) {
            if ($line.Kind -eq 'verbatim' -and $line.Text -match '^\s*current-(?:next-)?release:\s*\d+\s*$') {
                $line.Text = "current-next-release: $([int]$Release + 1)"
            }
        }
        Write-ReleaseQueue -Lines $queueLines

        Write-Output ("release-queue: shipped release {0} - {1} ticket(s) moved to {2}" -f $Release, $shipping.Count, (Split-Path $donePath -Leaf))
        Write-Output ("release-queue: current release -> {0}" -f ([int]$Release + 1))
        exit 0
    }

    default {
        $source = if ($Ready) { Read-ReleaseReady } else { Read-ReleaseQueue }
        $tickets = Get-QueueTickets -Lines $source
        if ($Release) { $tickets = @($tickets | Where-Object { $_.Release -eq $Release }) }
        foreach ($t in $tickets) {
            Write-Output (Format-ReleaseQueueLine -Release $t.Release -Ticket $t.Ticket -Changed $t.Changed -Status $t.Status)
        }
        Write-Output ("--- {0} ticket(s); current release: {1}" -f $tickets.Count, (Get-CurrentRelease))
        if ($WithLeases) {
            $visibleIds = @{}
            foreach ($ticket in $tickets) { $visibleIds[$ticket.Id] = $true }
            $leases = @(Get-ActiveTicketLeases | Where-Object { $visibleIds.ContainsKey([string] $_.id) })
            Write-Output '--- active ticket leases'
            if ($leases.Count -eq 0) {
                Write-Output '(none)'
            } else {
                foreach ($lease in $leases) {
                    Write-Output ("[lease] {0} session={1} age={2}m liveness={3} reason={4}" -f `
                            $lease.id, $lease.sessionId, $lease.ageMinutes, $lease.liveness, $lease.reason)
                }
            }
        }
        exit 0
    }
}
