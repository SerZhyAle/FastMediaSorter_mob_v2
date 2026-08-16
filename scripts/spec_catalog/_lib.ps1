# Shared helpers for spec_catalog scripts.
# Compatible with PowerShell 5.1 and 7+.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$libDir = $PSScriptRoot
if (-not $libDir) { $libDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repoRoot = (Resolve-Path (Join-Path $libDir '..\..')).Path
$script:RepoRoot = $repoRoot

# S1621: the research-section parser lives in its own leaf file so preview.ps1 can load it
# WITHOUT this library. Re-exported from here unchanged, so every existing caller of
# Get-SpecSectionLines / Get-OpenStatusPattern / Get-ResearchSectionHeadingPattern is
# untouched by the move.
. (Join-Path $libDir '_research-items.ps1')

$script:CatalogPath = Join-Path $repoRoot 'PLAN\spec-catalog.jsonl'
# Archived records live in a separate journal so the hot read path scans only
# active tickets. See PLAN/S0454_spec-catalog-journal-compaction.md.
$script:ArchivePath = Join-Path $repoRoot 'PLAN\spec-catalog-archive.jsonl'
# S1534: ids that were allocated and then removed from both journals. An id lands here only via
# purge-probe-records.ps1, and only ever gains rows. It exists because deleting a record is the one
# way an id can silently return to circulation: New-CatalogId is max+1 over what the journals hold,
# so removing the highest record would hand its id to the next ticket, and two different tickets
# would answer to one id in the dev log, the changelog and every commit message already written.
# Reading it here also lets validate.ps1 tell a deliberate hole from a lost record.
$script:BurnedIdsPath = Join-Path $repoRoot 'PLAN\spec-catalog-burned-ids.jsonl'
# S1534: redirect BOTH journals to an alternate directory. SCHEMA.md already named
# "alternate-catalog runs" a supported mode via $env:FMS_SKIP_RELEASE_QUEUE, but the paths
# themselves were hardcoded, so a test harness spawning CLI children had no way to write
# anywhere but production - which is how throwaway probes burned 21 real spec ids and raced a
# genuine insert into "Duplicate id". $script:RepoRoot is deliberately NOT redirected: spec .md
# files still resolve from the real PLAN/, so a sandboxed run reads live spec bodies.
# Fails loudly on a missing directory - silently creating a second production journal would
# split the catalog in two and neither half would know.
if ($env:FMS_SPEC_CATALOG_DIR) {
    $altDir = $env:FMS_SPEC_CATALOG_DIR
    if (-not (Test-Path -LiteralPath $altDir -PathType Container)) {
        throw "FMS_SPEC_CATALOG_DIR points at '$altDir', which is not an existing directory."
    }
    $script:CatalogPath = Join-Path $altDir 'spec-catalog.jsonl'
    $script:ArchivePath = Join-Path $altDir 'spec-catalog-archive.jsonl'
    $script:BurnedIdsPath = Join-Path $altDir 'spec-catalog-burned-ids.jsonl'
}
# Owner-facing release queue: which release package each open ticket belongs to, in the
# owner's hand-kept order. The catalog stays the source of truth for STATUS; the queue owns
# ORDER and RELEASE ASSIGNMENT, which no script may reshuffle. See PLAN/RELEASE_QUEUE.md.
$script:ReleaseQueuePath     = Join-Path $repoRoot 'PLAN\RELEASE_QUEUE.md'
$script:ReleaseReadyPath     = Join-Path $repoRoot 'PLAN\RELEASE_READY.md'
$script:ReleaseQueueDonePath = Join-Path $repoRoot 'PLAN\RELEASE_QUEUE_DONE.md'
$script:ReleaseQueueBacklog  = '--'
# S1698: how many duplicate ticket lines the last Sync-ReleaseQueue collapsed. Reported by
# release-queue.ps1 -Reconcile, because a silent repair of a line the owner can see is
# indistinguishable from the reconcile having done nothing - which is what the defect looked like.
$script:ReleaseQueueDuplicatesDropped = 0

$script:RequiredFields = @('id', 'name', 'status', 'priority', 'file', 'created', 'updated')
$script:StatusEnum = @(
    'Draft', 'Approved', 'Tactical', 'In Progress',
    'Implemented', 'Verified', 'Partial', 'Broken',
    'BlockByOtherTask', 'BlockNeedUserTest', 'BlockQuestions', 'BlockExternal',
    'Archived'
)
$script:IdPattern        = '^S\d{4}$'
# S1620: an archived record's file lives in PLAN/archive/, so the pattern admits that one
# optional segment. The `_spec_` ban and the Sxxxx_ prefix still apply in both locations -
# archiving must not become a way to smuggle a non-conforming name into the journal.
$script:FilePattern      = '^PLAN/(archive/)?S\d{4}_(?!spec_)'
$script:PriorityMin      = 0
$script:PriorityMax      = 100
$script:PriorityDefault  = 50
$script:StaleWarnDays    = 14
$script:StaleAlertDays   = 30

function Get-CatalogPath {
    return $script:CatalogPath
}

function Get-ArchivePath {
    return $script:ArchivePath
}

function Get-Now {
    return (Get-Date -Format 'yyyy-MM-dd HH:mm')
}

function Get-Today {
    return (Get-Date -Format 'yyyy-MM-dd')
}

function Read-JsonlFile {
    # Parse one JSONL journal file into a sorted-by-id object array.
    # Missing file -> empty array. Parse errors carry the file name + line.
    param([Parameter(Mandatory)][string] $Path)
    if (-not (Test-Path $Path)) {
        return ,@()
    }
    $raw = Get-Content -LiteralPath $Path -Encoding UTF8 -ErrorAction Stop
    if (-not $raw) { return ,@() }
    $records = New-Object System.Collections.Generic.List[object]
    $lineNo = 0
    $fileName = Split-Path -Leaf $Path
    foreach ($line in @($raw)) {
        $lineNo++
        $trim = "$line".Trim()
        if (-not $trim) { continue }
        try {
            $records.Add(($trim | ConvertFrom-Json))
        } catch {
            throw "Catalog parse error in ${fileName} at line ${lineNo}: $($_.Exception.Message)"
        }
    }
    $sorted = [object[]]@($records | Sort-Object -Property id)
    return ,$sorted
}

function Read-Catalog {
    # Default: active journal only (non-Archived). -IncludeArchived merges the
    # archive journal so full-catalog reviews still see every record.
    param([switch] $IncludeArchived)
    $active = Read-JsonlFile -Path $script:CatalogPath
    if (-not $IncludeArchived) {
        return ,([object[]]@($active))
    }
    $archived = Read-JsonlFile -Path $script:ArchivePath
    $merged = [object[]]@(@($active) + @($archived) | Sort-Object -Property id)
    return ,$merged
}

function Assert-Record {
    param([Parameter(Mandatory)] $Record)
    foreach ($f in $script:RequiredFields) {
        if (-not ($Record.PSObject.Properties.Name -contains $f)) {
            throw "Record missing required field '$f'."
        }
        if ($null -eq $Record.$f -or "$($Record.$f)" -eq '') {
            throw "Record field '$f' is empty."
        }
    }
    if ($Record.id -notmatch $script:IdPattern) {
        throw "Invalid id '$($Record.id)' - must match $script:IdPattern."
    }
    # S1504: a ticket name is a slug or a short title - a quote, a backslash or a control character
    # can only arrive from a mis-bound argument fragment, which is how S1474 was silently renamed
    # by a fragment of its own status note. Every one of the 1504 records at the time of writing
    # passes, so this rejects corruption without rejecting any legitimate name.
    if ([string]$Record.name -match '["\\\x00-\x1f]') {
        throw ("Invalid name '{0}' for {1} - a name must not contain a quote, a backslash or a control character (a mis-bound argument fragment is the usual source)." -f $Record.name, $Record.id)
    }
    if ($script:StatusEnum -notcontains $Record.status) {
        throw "Invalid status '$($Record.status)'. Allowed: $($script:StatusEnum -join ', ')."
    }
    $pri = [int]$Record.priority
    if ($pri -lt $script:PriorityMin -or $pri -gt $script:PriorityMax) {
        throw "Invalid priority '$($Record.priority)' - must be in $script:PriorityMin..$script:PriorityMax."
    }
    $fileNorm = ($Record.file -replace '\\', '/')
    if ($fileNorm -notmatch $script:FilePattern) {
        throw "Invalid file path '$($Record.file)' - must match $script:FilePattern (no '_spec_' segment)."
    }
    if ($fileNorm -match '\.\.') {
        throw "Invalid file path '$($Record.file)' - must not contain '..' segments."
    }
}

function Format-CatalogLines {
    # Validate + shape records into stable-key-order compact JSONL lines.
    # Shared by the active (Write-Catalog) and archive (Write-ArchiveCatalog) writers.
    param([Parameter(Mandatory)][object[]] $Records)
    $sorted = @($Records | Sort-Object -Property id)
    foreach ($r in $sorted) { Assert-Record -Record $r }
    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($r in $sorted) {
        # Re-shape to a fixed key order for stable diffs.
        $ordered = [ordered]@{
            id       = [string]$r.id
            name     = [string]$r.name
            status   = [string]$r.status
            priority = [int]$r.priority
        }
        if ($r.PSObject.Properties.Name -contains 'tier' -and $null -ne $r.tier -and "$($r.tier)" -ne '') {
            $ordered['tier'] = [int]$r.tier
        }
        $ordered['file']    = [string]$r.file
        $ordered['created'] = [string]$r.created
        $ordered['updated'] = [string]$r.updated
        $fixedKeys = @('id','name','status','priority','tier','file','created','updated')
        foreach ($prop in ($r.PSObject.Properties | Sort-Object Name)) {
            if ($fixedKeys -notcontains $prop.Name) {
                $ordered[$prop.Name] = $prop.Value
            }
        }
        $lines.Add(($ordered | ConvertTo-Json -Compress -Depth 5))
    }
    return $lines
}

function Write-JsonlFile {
    # Atomic write of pre-formatted JSONL lines: temp file + Move-Item -Force, UTF-8 no BOM.
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][AllowEmptyCollection()] $Lines
    )
    $payload = (@($Lines) -join "`n")
    if ($payload.Length -gt 0) { $payload += "`n" }
    $tmp = "$Path.tmp"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tmp, $payload, $utf8NoBom)
    Move-Item -LiteralPath $tmp -Destination $Path -Force
}

# ── Cross-process serialization (S1437) ──────────────────────────────────────────────────────
#
# Write-JsonlFile above is atomic against a TORN read - temp file plus rename - and that was
# never the failure. The failure is a lost update: two processes call Read-Catalog, both hold the
# same snapshot, and the second Write-Catalog replaces the whole file with its own stale base
# plus its own change. The first change vanishes with no error. So the critical section has to
# span read -> mutate -> write, which is why this is a caller-level lock and not something
# Write-Catalog could do on its own.
#
# A system mutex, not a lock file: a journal rewrite is milliseconds, while the BUILD/CODE lock
# family is sized for edits and builds (3-60 min windows, queue directories, reservations) and
# would be absurd here. The mutex also dies with its process, so a crashed holder cannot wedge
# the catalog - that is what the AbandonedMutexException branch is for.

$script:CatalogMutex = $null

function Get-CatalogMutexName {
    # Per-checkout, so two clones on one machine do not serialize against each other. Mutex names
    # cannot contain '\' beyond the Global\ prefix, hence the hash rather than the path.
    $hash = [System.BitConverter]::ToString(
        [System.Security.Cryptography.MD5]::HashData([System.Text.Encoding]::UTF8.GetBytes($script:RepoRoot.ToLowerInvariant()))
    ).Replace('-', '')
    return "Global\FMS-SpecCatalog-$hash"
}

function Enter-CatalogLock {
    <#
    .SYNOPSIS
        Take the catalog write lock. Pair with Exit-CatalogLock in a finally.
    #>
    param([int]$TimeoutSeconds = 30)

    if ($script:CatalogMutex) { return }   # re-entrant within one process
    $mutex = New-Object System.Threading.Mutex($false, (Get-CatalogMutexName))
    $acquired = $false
    try {
        $acquired = $mutex.WaitOne([TimeSpan]::FromSeconds($TimeoutSeconds))
    }
    catch [System.Threading.AbandonedMutexException] {
        # The previous holder died mid-write. The mutex is ours; the journal itself is intact
        # because every write lands by rename. Proceeding is correct - refusing would wedge the
        # catalog until a reboot.
        $acquired = $true
    }
    if (-not $acquired) {
        $mutex.Dispose()
        throw "Catalog is locked by another process (waited ${TimeoutSeconds}s). Journal: $script:CatalogPath"
    }
    $script:CatalogMutex = $mutex
}

function Exit-CatalogLock {
    # Safe to call unconditionally from a finally, including when the lock was never taken.
    if (-not $script:CatalogMutex) { return }
    try { $script:CatalogMutex.ReleaseMutex() } catch { }
    $script:CatalogMutex.Dispose()
    $script:CatalogMutex = $null
}

function Invoke-CatalogTransaction {
    <#
    .SYNOPSIS
        Run a scriptblock holding the catalog write lock.
    .DESCRIPTION
        Convenience wrapper for callers whose whole mutation fits one scriptblock. A caller that
        needs its variables in its OWN scope - most of the mutators do, because they exit or
        print after writing - should use Enter-CatalogLock / Exit-CatalogLock with try/finally
        instead, since a scriptblock runs in a child scope and its assignments do not escape.
    #>
    param(
        [Parameter(Mandatory)][scriptblock]$Body,
        [int]$TimeoutSeconds = 30
    )
    Enter-CatalogLock -TimeoutSeconds $TimeoutSeconds
    try { & $Body }
    finally { Exit-CatalogLock }
}

function Write-Catalog {
    # Writes the ACTIVE journal only. Archive records are never passed here.
    param([Parameter(Mandatory)][object[]] $Records)
    $lines = Format-CatalogLines -Records $Records
    Write-JsonlFile -Path $script:CatalogPath -Lines $lines
    # Single choke point for every catalog mutation (insert/update/complete/archive/delete/
    # bulk-update), so the release queue follows along without any skill knowing about it.
    Sync-ReleaseQueue -Records $Records
}

# ── Release queue (PLAN/RELEASE_QUEUE.md) ────────────────────────────────────────────────────

function Get-ReleaseQueuePath { return $script:ReleaseQueuePath }

function Get-ReleaseReadyPath { return $script:ReleaseReadyPath }

function Get-ReleaseQueueDonePath { return $script:ReleaseQueueDonePath }

function Get-ReleaseQueueDuplicatesDropped { return $script:ReleaseQueueDuplicatesDropped }

function Get-TicketBaseName {
    # 'PLAN/S1291_slug.md' -> 'S1291_slug'. The queue shows the spec FILE name, so one column
    # carries both the id and the human-readable slug.
    param([Parameter(Mandatory)][string] $File)
    return [System.IO.Path]::GetFileNameWithoutExtension($File)
}

function Format-ReleaseQueueLine {
    param(
        [Parameter(Mandatory)][string] $Release,
        [Parameter(Mandatory)][string] $Ticket,
        [Parameter(Mandatory)][string] $Changed,
        [Parameter(Mandatory)][string] $Status
    )
    return ('{0,-4} {1,-62} {2,-11} {3}' -f $Release, $Ticket, $Changed, $Status).TrimEnd()
}

function Read-ReleaseQueue { return ,(Read-ReleaseFile -Path $script:ReleaseQueuePath) }

function Read-ReleaseReady { return ,(Read-ReleaseFile -Path $script:ReleaseReadyPath) }

function Read-ReleaseFile {
    # Returns the file as an ordered list of line objects. Data lines are parsed; everything
    # else (heading, prose, blanks, the owner's own notes) is carried through verbatim so a
    # reconcile never rewrites anything the owner typed.
    param([Parameter(Mandatory)][string] $Path)
    $result = New-Object System.Collections.Generic.List[object]
    if (-not (Test-Path $Path)) { return ,$result }
    $raw = Get-Content -LiteralPath $Path -Encoding UTF8 -ErrorAction Stop
    foreach ($line in @($raw)) {
        # A data line is: <release> <Sxxxx_slug> <yyyy-MM-dd> <Status>. Anchoring on the id
        # shape keeps prose and the column header from ever being mistaken for data.
        if ("$line" -match '^\s*(\d+|--)\s+(S\d{4}_\S*)\s+(\d{4}-\d{2}-\d{2})\s+(\S.*?)\s*$') {
            $result.Add([pscustomobject]@{
                Kind    = 'ticket'
                Release = $Matches[1]
                Ticket  = $Matches[2]
                Changed = $Matches[3]
                Status  = $Matches[4]
                Id      = $Matches[2].Substring(0, 5)
            })
        } else {
            $result.Add([pscustomobject]@{ Kind = 'verbatim'; Text = "$line" })
        }
    }
    return ,$result
}

function Write-ReleaseQueue {
    param([Parameter(Mandatory)][AllowEmptyCollection()] $Lines)
    Write-ReleaseFile -Path $script:ReleaseQueuePath -Lines $Lines
}

function Write-ReleaseFile {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][AllowEmptyCollection()] $Lines
    )
    $out = New-Object System.Collections.Generic.List[string]
    # Iterate the List directly: @(..) around a List[object] of PSCustomObject throws
    # "Argument types do not match" (the array subexpression cannot build the PSObject[] copy).
    foreach ($l in $Lines) {
        if ($l.Kind -eq 'ticket') {
            $out.Add((Format-ReleaseQueueLine -Release $l.Release -Ticket $l.Ticket -Changed $l.Changed -Status $l.Status))
        } else {
            $out.Add($l.Text)
        }
    }
    $payload = [string]::Join("`r`n", $out.ToArray())
    if ($payload.Length -gt 0) { $payload += "`r`n" }
    $tmp = "$Path.tmp"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tmp, $payload, $utf8NoBom)
    Move-Item -LiteralPath $tmp -Destination $Path -Force
}

function Get-CurrentRelease {
    # Authority order: the queue's own header marker, then the DEBUG-v0NN branch name, then
    # the backlog bucket. The marker wins so the queue keeps working with no git available.
    foreach ($line in (Read-ReleaseQueue)) {
        if ($line.Kind -eq 'verbatim' -and $line.Text -match '^\s*current-(?:next-)?release:\s*(\d+)\s*$') {
            return $Matches[1]
        }
    }
    try {
        $branch = (& git rev-parse --abbrev-ref HEAD 2>$null)
        if ($LASTEXITCODE -eq 0 -and "$branch" -match 'DEBUG-v0*(\d+)') { return $Matches[1] }
    } catch {
        # git absent or not a repo - fall through to the backlog bucket.
    }
    return $script:ReleaseQueueBacklog
}

function Test-ReleaseReadyStatus {
    # Ready = the ticket's code is done as far as this release is concerned. Implemented and
    # Verified are self-evident; BlockNeedUserTest counts too, because some flows are very hard
    # to verify and the owner treats a long-pending device check as shipped - if it later turns
    # out broken it simply comes back as fresh work in a later package.
    param([Parameter(Mandatory)][string] $Status)
    return $Status -in @('Implemented', 'Verified', 'BlockNeedUserTest')
}

function Sync-ReleaseQueue {
    # Reconcile BOTH release files against the catalog and move tickets between them by status.
    #
    #   RELEASE_QUEUE.md  - work still to do before the release: everything below Implemented,
    #                       including tickets blocked on another ticket / a question / an
    #                       external party (the owner wants those visible and sortable).
    #   RELEASE_READY.md  - the release's finished content: Implemented, Verified,
    #                       BlockNeedUserTest. Not something to plan around any more.
    #
    # Invariants:
    #   - never reorders existing lines (the order is the owner's plan),
    #   - never rewrites the `rel` column - a ticket keeps its package when it moves file,
    #   - updates the changed-date ONLY when the status actually moved,
    #   - a status change across the ready boundary moves the line to the other file,
    #   - drops a line whose ticket left the active journal (archived or deleted),
    #   - never ADDS a ready ticket that is in neither file: it shipped in an earlier package,
    #   - keeps ONE line per ticket id across both files: the first occurrence wins its position
    #     and its `rel` column, every later one is dropped (S1698).
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Records)

    if ($env:FMS_SKIP_RELEASE_QUEUE) { return }
    if (-not (Test-Path $script:ReleaseQueuePath)) { return }

    $script:ReleaseQueueDuplicatesDropped = 0

    $byId = @{}
    foreach ($r in $Records) { $byId[$r.id] = $r }

    $today = Get-Today
    $seen = @{}
    $toReady = New-Object System.Collections.Generic.List[object]
    $toQueue = New-Object System.Collections.Generic.List[object]

    # One pass per file: keep what still belongs, collect what crossed the boundary.
    $keptQueue = Select-ReleaseLines -Path $script:ReleaseQueuePath -ById $byId -Seen $seen `
        -Today $today -WantReady $false -Moved $toReady
    $keptReady = Select-ReleaseLines -Path $script:ReleaseReadyPath -ById $byId -Seen $seen `
        -Today $today -WantReady $true -Moved $toQueue

    # A brand-new ticket is unfinished by definition, so it lands in the queue only.
    $current = Get-CurrentRelease
    foreach ($r in ($Records | Sort-Object -Property id)) {
        if ($seen.ContainsKey($r.id)) { continue }
        if (Test-ReleaseReadyStatus -Status $r.status) { continue }
        $changed = if ("$($r.updated)".Length -ge 10) { "$($r.updated)".Substring(0, 10) } else { $today }
        $toQueue.Add([pscustomobject]@{
            Kind    = 'ticket'
            Release = $current
            Ticket  = (Get-TicketBaseName -File $r.file)
            Changed = $changed
            Status  = $r.status
            Id      = $r.id
        })
    }

    Add-ReleaseLines -Target $keptQueue -Additions $toQueue
    Add-ReleaseLines -Target $keptReady -Additions $toReady

    Write-ReleaseFile -Path $script:ReleaseQueuePath -Lines $keptQueue
    if (Test-Path $script:ReleaseReadyPath) {
        Write-ReleaseFile -Path $script:ReleaseReadyPath -Lines $keptReady
    }
}

function Select-ReleaseLines {
    # Walk one release file: refresh each ticket from the catalog, keep the ones that still
    # belong here, and hand the rest to [$Moved] for the sibling file.
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][hashtable] $ById,
        [Parameter(Mandatory)][hashtable] $Seen,
        [Parameter(Mandatory)][string] $Today,
        [Parameter(Mandatory)][bool] $WantReady,
        [Parameter(Mandatory)] $Moved
    )
    # ,$kept on every exit: a bare `return $list` unrolls into a fixed-size object[], and the
    # caller's later .Insert() would fail with "Collection was of a fixed size".
    $kept = New-Object System.Collections.Generic.List[object]
    if (-not (Test-Path $Path)) { return ,$kept }

    foreach ($line in (Read-ReleaseFile -Path $Path)) {
        if ($line.Kind -ne 'ticket') { $kept.Add($line); continue }
        # S1698: one line per id, across BOTH files. $Seen is shared by the two passes, so this
        # collapses a line duplicated inside one file and a ticket listed in queue AND ready
        # alike. Dropping the later occurrence is what makes -Reconcile the remedy -Validate
        # prints for "duplicate line": before this, both copies were refreshed and written back,
        # so the validator kept failing however many times the operator ran the fix.
        if ($Seen.ContainsKey($line.Id)) {
            $script:ReleaseQueueDuplicatesDropped++
            continue
        }
        if (-not $ById.ContainsKey($line.Id)) { continue }   # archived / deleted

        $rec = $ById[$line.Id]
        $Seen[$line.Id] = $true
        $changed = if ($line.Status -ne $rec.status) { $Today } else { $line.Changed }
        $row = [pscustomobject]@{
            Kind    = 'ticket'
            Release = $line.Release                      # the owner's package assignment survives
            Ticket  = (Get-TicketBaseName -File $rec.file)
            Changed = $changed
            Status  = $rec.status
            Id      = $line.Id
        }
        if ((Test-ReleaseReadyStatus -Status $rec.status) -eq $WantReady) { $kept.Add($row) } else { $Moved.Add($row) }
    }
    return ,$kept
}

function Add-ReleaseLines {
    # Append each addition after the last line of its own release block, so blocks stay grouped
    # and nothing jumps above work the owner already ordered.
    param(
        [Parameter(Mandatory)] $Target,
        [Parameter(Mandatory)] $Additions
    )
    # S1698: an id already standing in the target file is never appended a second time. The
    # Select pass makes this unreachable for well-formed input; it stays as the last barrier,
    # because this is the only place a line is created rather than carried over.
    $present = @{}
    foreach ($t in $Target) { if ($t.Kind -eq 'ticket') { $present[$t.Id] = $true } }

    foreach ($add in $Additions) {
        if ($present.ContainsKey($add.Id)) {
            $script:ReleaseQueueDuplicatesDropped++
            continue
        }
        $present[$add.Id] = $true
        $insertAt = -1
        for ($i = 0; $i -lt $Target.Count; $i++) {
            if ($Target[$i].Kind -eq 'ticket' -and $Target[$i].Release -eq $add.Release) { $insertAt = $i }
        }
        if ($insertAt -ge 0) { $Target.Insert($insertAt + 1, $add) } else { $Target.Add($add) }
    }
}

function Write-ArchiveCatalog {
    # Writes the ARCHIVE journal. Empty set allowed (e.g. nothing archived yet).
    param([AllowEmptyCollection()][object[]] $Records = @())
    $lines = Format-CatalogLines -Records $Records
    Write-JsonlFile -Path $script:ArchivePath -Lines $lines
}

function Add-ArchiveRecord {
    # Append (or replace-by-id) a single record into the archive journal.
    # Idempotent: re-archiving the same id replaces its row, never duplicates.
    param([Parameter(Mandatory)] $Record)
    # Direct assignment (not @()) - Read-JsonlFile uses the ,$arr anti-unroll
    # idiom, which @() would re-nest into a single element.
    $existing = Read-JsonlFile -Path $script:ArchivePath
    $kept = @($existing | Where-Object { $_.id -ne $Record.id })
    $kept += $Record
    Write-ArchiveCatalog -Records ([object[]]$kept)
}

function Get-BurnedIdsPath {
    return $script:BurnedIdsPath
}

function Read-BurnedIds {
    # Ids allocated and later removed from both journals. Missing file -> empty, never an error:
    # a repo that has never purged anything is the normal case.
    # Direct assignment (not @()) - Read-JsonlFile uses the ,$arr anti-unroll idiom, which @() would
    # re-nest into a single element, and the caller would then read .id off a bare array.
    $rows = Read-JsonlFile -Path $script:BurnedIdsPath
    return ,$rows
}

function Add-BurnedIds {
    # Append-only, deduplicated by id. Never removes: an id that returns to circulation is exactly
    # the failure this registry exists to prevent.
    param(
        [Parameter(Mandatory)][string[]] $Ids,
        [Parameter(Mandatory)][string] $Reason
    )
    $existing = Read-JsonlFile -Path $script:BurnedIdsPath
    $known = @{}
    foreach ($e in $existing) { $known[$e.id] = $true }
    $rows = New-Object System.Collections.Generic.List[string]
    foreach ($e in $existing) { $rows.Add(($e | ConvertTo-Json -Compress)) }
    $added = 0
    foreach ($id in ($Ids | Sort-Object -Unique)) {
        if ($known.ContainsKey($id)) { continue }
        $rows.Add(([pscustomobject]@{ id = $id; reason = $Reason; burned = (Get-Today) } | ConvertTo-Json -Compress))
        $added++
    }
    Write-JsonlFile -Path $script:BurnedIdsPath -Lines ([string[]]$rows)
    return $added
}

function New-CatalogId {
    # Must scan archive too - an archived id must never be reissued - and the burned registry, whose
    # ids are gone from both journals yet just as spent (S1534).
    $records = Read-Catalog -IncludeArchived
    $max = 0
    foreach ($r in $records) {
        if ($r.id -match '^S(\d{4})$') {
            $n = [int]$Matches[1]
            if ($n -gt $max) { $max = $n }
        }
    }
    foreach ($b in (Read-BurnedIds)) {
        if ($b.id -match '^S(\d{4})$') {
            $n = [int]$Matches[1]
            if ($n -gt $max) { $max = $n }
        }
    }
    $next = $max + 1
    if ($next -gt 9999) { throw "Id space exhausted (S9999 reached)." }
    return ('S{0:D4}' -f $next)
}

function Find-Record {
    # Resolve by id against the active journal, falling back to the archive
    # journal on a miss so archived tickets stay reachable transparently.
    param([Parameter(Mandatory)][string] $Id)
    foreach ($r in (Read-JsonlFile -Path $script:CatalogPath)) {
        if ($r.id -eq $Id) { return $r }
    }
    foreach ($r in (Read-JsonlFile -Path $script:ArchivePath)) {
        if ($r.id -eq $Id) { return $r }
    }
    return $null
}

function Resolve-SpecPath {
    # Resolve a record's `file` (repo-relative, e.g. 'PLAN/S0290_*.md') or an
    # already-absolute path to an absolute filesystem path.
    param([Parameter(Mandatory)][string] $PathRef)
    $p = $PathRef -replace '/', '\'
    if ([System.IO.Path]::IsPathRooted($p)) { return $p }
    return (Join-Path $script:RepoRoot $p)
}

function Assert-ClosingGates {
    # Run every gate that guards a transition INTO a closed status, from the one place
    # both status-change paths can reach.
    #
    # Why here and not in update.ps1: the canonical closure path is /spec-check, which
    # runs close-and-log.ps1 -> close.ps1, and close.ps1 invoked no gate at all. A gate
    # wired only into update.ps1 guards the path used less often - which is the state
    # the S1606 durable-evidence gate was in until S1607 moved the call here.
    #
    # Archived is deliberately absent from the gated list: it closes a ticket that
    # already passed these gates, and blocking cleanup would make tidying the catalog
    # harder than leaving it untidy.
    param(
        [Parameter(Mandatory)][string] $Id,
        [string] $OldStatus,
        [Parameter(Mandatory)][string] $NewStatus
    )
    $gatedStatuses = @('Implemented', 'Verified')
    if ($gatedStatuses -notcontains $NewStatus -or $OldStatus -eq $NewStatus) { return }

    # S1606 - a closed spec must not cite evidence under disposable temp/.
    # S1607 - a closed spec must not strand an open question nobody owns.
    $checkers = @('check-evidence-durable.ps1', 'check-open-items-carried.ps1')
    foreach ($name in $checkers) {
        $checker = Join-Path $PSScriptRoot $name
        # A missing checker is tolerated, matching how the owner-inputs gate call behaves:
        # a partial checkout must not make the catalog unwritable.
        if (-not (Test-Path -LiteralPath $checker)) { continue }
        $output = & $checker -Id $Id 2>&1
        # Exit 2 fails too: "could not look" is not "found nothing".
        if ($LASTEXITCODE -ne 0) {
            Write-Host ""
            Write-Host ("Closing gate blocked {0} -> {1} ({2}):" -f $Id, $NewStatus, $name) -ForegroundColor Yellow
            $output | ForEach-Object { Write-Host $_ }
            Write-Host ""
            throw ("Cannot close '{0}': {1} reported exit {2}. Fix what it names, then re-run." -f $Id, $name, $LASTEXITCODE)
        }
    }

    # S1665 - advisories run after the hard gates and are structurally unable to stop a close: their
    # output is shown, their exit code is not read. The list is separate rather than a flag on the loop
    # above so that "this one can refuse" stays a property of the list a checker is in, not of a branch
    # someone can invert later by accident.
    #
    # Why advisory and not a gate: a capability record is mandatory only for a ticket that shipped
    # something user-facing, and the closing path cannot tell that apart from a tooling or documentation
    # ticket without reading intent. Refusing on a guess would make those tickets unclosable, which is a
    # worse failure than the reminder being ignored.
    $advisories = @('check-capability-recorded.ps1')
    foreach ($name in $advisories) {
        $checker = Join-Path $PSScriptRoot $name
        if (-not (Test-Path -LiteralPath $checker)) { continue }
        & $checker -Id $Id 2>&1 | ForEach-Object { Write-Host $_ }
    }
}


function Sync-SpecHeaderStatus {
    # Mirror a status change (and optional human note) into the FIRST
    # '**Status:**' header block of a spec .md so the owner can read both
    # the status and its reason at a glance. Fail-soft: missing file or
    # absent header returns $false without throwing - the journal write is
    # never rolled back by a header problem. Only the first **Status:** match
    # is rewritten; deeper ones (ADR / Proposal blocks) keep their own values.
    #
    # StatusNote semantics:
    #   $null (omitted) - preserve any existing '**Status note:**' line as-is.
    #   non-empty string - upsert '**Status note:** <note>' right after **Status:**.
    #   empty string     - remove '**Status note:**' line if present.
    #
    # Returns $true when the header now reads the target Status.
    param(
        [Parameter(Mandatory)][string] $PathRef,
        [Parameter(Mandatory)][string] $Status,
        [string] $StatusNote = $null
    )
    try {
        $abs = Resolve-SpecPath -PathRef $PathRef
        if (-not (Test-Path -LiteralPath $abs -PathType Leaf)) { return $false }
        $raw = Get-Content -LiteralPath $abs -Raw

        # Capture: (1) **Status:** line body  (2) its line ending
        #          (3) optional immediately-following **Status note:** line with its ending
        $rx = [regex]'(?m)(^\*\*Status:\*\*[^\r\n]*)(\r?\n)(\*\*Status note:\*\*[^\r\n]*\r?\n?)?'
        $m  = $rx.Match($raw)
        if (-not $m.Success) { return $false }

        $lineEnd       = $m.Groups[2].Value   # \r\n or \n
        $newStatusLine = '**Status:** ' + $Status

        # Build replacement block: status line + optional note line
        if ($null -ne $StatusNote -and $StatusNote -ne '') {
            # Upsert note
            $newBlock = $newStatusLine + $lineEnd + '**Status note:** ' + $StatusNote + $lineEnd
        } elseif ($StatusNote -eq '') {
            # Clear note line
            $newBlock = $newStatusLine + $lineEnd
        } else {
            # $null - preserve existing note line verbatim (or nothing if absent)
            $newBlock = $newStatusLine + $lineEnd
            if ($m.Groups[3].Success -and $m.Groups[3].Value -ne '') {
                $newBlock += $m.Groups[3].Value
            }
        }

        # Splice: everything before the header block + the new block + everything after,
        # so an unrelated in-flight edit to the body survives verbatim - only the matched
        # lines are ever replaced.
        $patched = $raw.Substring(0, $m.Index) + $newBlock + $raw.Substring($m.Index + $m.Length)
        # No-op writes are skipped outright. A rewrite bumps mtime, which invalidates any
        # open editor/agent read state and produces a stale-file Edit failure - so a header
        # that already reads the target status must not touch the file at all.
        if ($patched -ne $raw) {
            $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
            $tmp = "$abs.tmp"
            [System.IO.File]::WriteAllText($tmp, $patched, $utf8NoBom)
            Move-Item -LiteralPath $tmp -Destination $abs -Force
        }
        return $true
    } catch {
        Write-Host ("  header sync warning ({0}): {1}" -f $PathRef, $_.Exception.Message) -ForegroundColor DarkYellow
        return $false
    }
}

function Get-DaysSinceUpdated {
    param([Parameter(Mandatory)][string] $Updated)
    try {
        $dt = [datetime]::ParseExact($Updated, 'yyyy-MM-dd HH:mm', $null)
        $span = (Get-Date) - $dt
        return [int][math]::Floor($span.TotalDays)
    } catch {
        return -1
    }
}

function Get-StaleLevel {
    param([Parameter(Mandatory)][string] $Status, [Parameter(Mandatory)][int] $Days)
    # Frozen states ignore staleness.
    $frozen = @('Verified', 'Archived', 'Implemented')
    if ($frozen -contains $Status) { return 'fresh' }
    if ($Days -lt 0)                          { return 'unknown' }
    if ($Days -ge $script:StaleAlertDays)     { return 'alert' }
    if ($Days -ge $script:StaleWarnDays)      { return 'warn' }
    return 'fresh'
}

