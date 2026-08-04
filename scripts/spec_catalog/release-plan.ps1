# release-plan.ps1 - Ordered release command-sequence generator for /spec-next --plan
#
# Read-only. Reads the active catalog and emits the full ordered command sequence
# that drives every open ticket to a releasable state, ending in the release tail.
# Unlike spec-next-preflight.ps1 (picks ONE next), this enumerates the WHOLE
# catalog into a phased, dependency-ordered plan:
#
#   Phase A - Implementation        (Draft/Approved/Tactical/In Progress/Partial/Broken
#                                     with no in-plan prerequisite)
#   Phase B - Dependency chains      (BlockByOtherTask, OR any impl spec whose statusNote
#                                     names an in-plan blocker; ordered after blockers)
#   Phase C - Verification           (Implemented -> spec-test-device+spec-check;
#                                     BlockNeedUserTest -> one batched /spec-sweep)
#   Phase D - Release                (/spec-prerelease -> /skill-release)
#   Deferred                         (BlockExternal/BlockQuestions - cannot drive from catalog)
#
# Every Draft and Approved present is listed (the caller asked for full coverage);
# heavy ones are annotated (epic / owner-gate) but never dropped.
#
# Ordering authority is PLAN/RELEASE_QUEUE.md - release package first, then the owner's line
# order inside that package. Catalog priority only breaks ties for a ticket the queue does not
# list. Dependency edges still win over both: a blocker is always emitted before its dependent.
#
# Status -> command map. The trailing command of a pair is the prior skill's own
# auto-chain (e.g. /spec-tech -> /spec-dev, /spec-fix -> /spec-check), listed
# explicitly so the sequence stays complete on the PRIMITIVE / blocked / --dry-run
# branches where that chain does not fire:
#   Draft            -> /spec-all
#   Approved         -> /spec-tech ; /spec-dev
#   Tactical         -> /spec-dev
#   In Progress      -> /spec-dev
#   Partial | Broken -> /spec-fix ; /spec-check
#   BlockByOtherTask -> unblock (update.ps1 -Status <pre-block>) FIRST, then /spec-tech ; /spec-dev
#                       (/spec-tech and /spec-dev both hard-abort while status is Block*)
#   Implemented      -> /spec-test-device ; /spec-check   (device step needs a device online)
#   BlockNeedUserTest-> collapsed into ONE /spec-sweep
#
# READ-ONLY by contract: no writes to the catalog, the skip-cache, or any spec.
#
# Usage:
#   pwsh -NoProfile -File scripts/spec_catalog/release-plan.ps1                 # text command block
#   pwsh -NoProfile -File scripts/spec_catalog/release-plan.ps1 -Format json    # structured plan
#   pwsh -NoProfile -File scripts/spec_catalog/release-plan.ps1 -Flavors "standard,vr"
#
# Exit codes: 0 - success (an empty catalog is a valid state). 1 - any error: a
#   catalog parse failure (caught by the trap) or a parameter-binding/usage error
#   PowerShell rejects before the body runs (e.g. an invalid -Format value).

[CmdletBinding()]
param(
    [ValidateSet('text', 'json')]
    [string]$Format = 'text',
    # Flavor scope passed through to the /skill-release line (informational only).
    [string]$Flavors = '',
    # Read an alternate JSONL instead of the live active catalog. Read-only test
    # hook (regression scenarios); never used by /spec-next, which omits it.
    [string]$CatalogFile = ''
)

trap {
    Write-Error $_
    exit 1
}

. (Join-Path $PSScriptRoot '_lib.ps1')

# --- owner's release plan (PLAN/RELEASE_QUEUE.md) ------------------------------
# The catalog knows priority; only the queue knows which package a ticket ships in and in
# what order inside it. That ordering outranks priority everywhere in this plan. Priority
# survives as the fallback for a ticket the queue does not list. The -CatalogFile test hook
# reads an alternate journal, so the queue is skipped there to keep those runs deterministic.
$queueRank = @{}
$queueRel = @{}
$queueSide = @{}
if (-not $CatalogFile) {
    $seq = 0
    foreach ($line in (Read-ReleaseQueue)) {
        if ($line.Kind -ne 'ticket' -or $queueRank.ContainsKey($line.Id)) { continue }
        $queueRank[$line.Id] = $seq++; $queueRel[$line.Id] = [string]$line.Release; $queueSide[$line.Id] = 'queue'
    }
    foreach ($line in (Read-ReleaseReady)) {
        if ($line.Kind -ne 'ticket' -or $queueRank.ContainsKey($line.Id)) { continue }
        $queueRank[$line.Id] = $seq++; $queueRel[$line.Id] = [string]$line.Release; $queueSide[$line.Id] = 'ready'
    }
}
$relReadySide = 9000     # finished content awaiting its audit - not planned work
$relParked = 9100        # `--` in the queue: only when nothing in a package is left
$relOffQueue = 9500      # in neither file: fall back to priority

function Get-QueueBucket {
    param([Parameter(Mandatory)][string] $Id)
    if (-not $queueRel.ContainsKey($Id)) { return $relOffQueue }
    if ($queueSide[$Id] -eq 'ready') { return $relReadySide }
    if ($queueRel[$Id] -match '^\d+$') { return [int]$queueRel[$Id] }
    return $relParked
}

function Get-QueueOrder {
    param([Parameter(Mandatory)][string] $Id)
    if ($queueRank.ContainsKey($Id)) { return $queueRank[$Id] }
    return [int]::MaxValue
}

# --- status classification ----------------------------------------------------
$implStatuses = @('Draft', 'Approved', 'Tactical', 'In Progress', 'Partial', 'Broken', 'BlockByOtherTask')
$deferStatuses = @('BlockExternal', 'BlockQuestions')

function Get-Field {
    # StrictMode-safe optional property read.
    param($Record, [string]$Name)
    if ($Record.PSObject.Properties.Name -contains $Name) { return $Record.$Name }
    return $null
}

function Get-CommandsForStatus {
    param([string]$Status, [string]$Id)
    switch ($Status) {
        'Draft'            { return @("/spec-all $Id") }
        'Approved'         { return @("/spec-tech $Id", "/spec-dev $Id") }
        'Tactical'         { return @("/spec-dev $Id") }
        'In Progress'      { return @("/spec-dev $Id") }
        'Partial'          { return @("/spec-fix $Id", "/spec-check $Id") }
        'Broken'           { return @("/spec-fix $Id", "/spec-check $Id") }
        'BlockByOtherTask' { return @("/spec-tech $Id", "/spec-dev $Id") }
        'Implemented'      { return @("/spec-test-device $Id", "/spec-check $Id") }
        default            { return @() }
    }
}

# --- read + partition ---------------------------------------------------------
# Live active journal (Archived excluded), or an alternate file for the test hook.
$all = if ($CatalogFile) { @(Read-JsonlFile -Path $CatalogFile) } else { Read-Catalog }
$implSet = @($all | Where-Object { $implStatuses -contains $_.status })
$verifyImpl = @($all | Where-Object { $_.status -eq 'Implemented' })
$deviceTest = @($all | Where-Object { $_.status -eq 'BlockNeedUserTest' })
$deferred = @($all | Where-Object { $deferStatuses -contains $_.status })

$implIds = @{}
foreach ($r in $implSet) { $implIds[$r.id] = $true }

# --- dependency edges (any impl spec whose statusNote names an in-plan blocker) ---
# Edge "X after Y" means Y must be emitted before X. Edges are resolved for EVERY
# impl status, not only BlockByOtherTask: a Draft/Partial/etc. whose statusNote
# names a prerequisite must still sort after it (a status-gated scan would leave
# such a spec un-ordered and let it render before its blocker).
#
# Source: the ticket's `statusNote` ONLY, and only ids that follow a blocker cue
# word (blocked by / depends on / after / waits on / gated by / unblock ..). Two
# deliberate guards against fabricated edges:
#   1. Cue-gating - a bare Sxxxx mention ("see also S0610", "matches S0644 etalon",
#      "S0288 regression") is advisory, not a dependency, so it is ignored. CLAUDE.md
#      §4 makes the real blocker appear after such a cue in every Block* statusNote.
#   2. No-§10 - we never read the spec's bidirectional §10 "Related" block (via
#      preview.ps1); it lists sibling tickets and would inject reverse edges.
#   3. Mutual-pair drop - if the reverse edge already exists, the new one is dropped
#      (first-declared direction wins) so a stray reciprocal mention cannot form a
#      2-cycle. The Kahn backstop below is the final safety net regardless.
# A ticket with no cued statusNote degrades to priority order (no "after"), never
# a wrong edge.
$blockerCue = '(?i)(?:blocked\s+by|depends?\s+(?:on|upon)|gated\s+by|waits?\s+on|waiting\s+on|unblock(?:ed)?\s+(?:by|after|when)|\bafter)\s+(S\d{4})'
$blockersOf = @{}        # id -> @(blocker ids that are in the impl set)
foreach ($r in $implSet) { $blockersOf[$r.id] = @() }
foreach ($r in $implSet) {
    $note = Get-Field $r 'statusNote'
    if (-not $note) { continue }
    $edges = New-Object System.Collections.Generic.List[string]
    foreach ($m in [regex]::Matches([string]$note, $blockerCue)) {
        $c = $m.Groups[1].Value
        if ($c -eq $r.id) { continue }
        if (-not $implIds.ContainsKey($c)) { continue }            # blocker already done / not in plan
        if (@($blockersOf[$c]) -contains $r.id) { continue }       # reverse edge exists -> drop (no 2-cycle)
        if (-not $edges.Contains($c)) { $edges.Add($c) }
    }
    $blockersOf[$r.id] = @($edges | Sort-Object -Unique)
}

# --- topological order with priority tiebreak (Kahn) --------------------------
# Ready = all blockers already emitted. Among ready, pick the Compare-Candidate
# winner (priority desc, updated desc, id asc). Cycle backstop: if nothing is
# ready (a dependency cycle), treat all remaining as ready and release the same
# Compare-Candidate winner, so output stays deterministic regardless of cycles.
$byId = @{}
foreach ($r in $implSet) { $byId[$r.id] = $r }
$emitted = @{}
$ordered = New-Object System.Collections.Generic.List[object]
$remaining = New-Object System.Collections.Generic.List[string]
foreach ($r in $implSet) { $remaining.Add($r.id) }

function Compare-Candidate {
    param($A, $B)   # returns the "better" (earlier) record
    # Release plan first: package, then the owner's line order inside it.
    $ba = Get-QueueBucket -Id $A.id; $bb = Get-QueueBucket -Id $B.id
    if ($ba -ne $bb) { return ($(if ($ba -lt $bb) { $A } else { $B })) }
    $oa = Get-QueueOrder -Id $A.id; $ob = Get-QueueOrder -Id $B.id
    if ($oa -ne $ob) { return ($(if ($oa -lt $ob) { $A } else { $B })) }
    $pa = [int]$A.priority; $pb = [int]$B.priority
    if ($pa -ne $pb) { return ($(if ($pa -gt $pb) { $A } else { $B })) }
    $ua = [string](Get-Field $A 'updated'); $ub = [string](Get-Field $B 'updated')
    if ($ua -ne $ub) { return ($(if ($ua -gt $ub) { $A } else { $B })) }
    return ($(if ([string]$A.id -le [string]$B.id) { $A } else { $B }))
}

while ($remaining.Count -gt 0) {
    $ready = @()
    foreach ($id in $remaining) {
        $blocked = $false
        foreach ($b in $blockersOf[$id]) { if (-not $emitted.ContainsKey($b)) { $blocked = $true; break } }
        if (-not $blocked) { $ready += $id }
    }
    if ($ready.Count -eq 0) { $ready = @($remaining) }   # cycle backstop

    $pick = $null
    foreach ($id in $ready) {
        if ($null -eq $pick) { $pick = $byId[$id]; continue }
        $pick = Compare-Candidate $pick $byId[$id]
    }
    $ordered.Add($pick)
    $emitted[$pick.id] = $true
    [void]$remaining.Remove($pick.id)
}

# --- annotations (epic / owner-gate) ------------------------------------------
# Cheap heuristics from the catalog record; deeper detection lives in preview.ps1
# and is surfaced only when already loaded above.
function Get-Annotations {
    param($Record)
    $notes = @()
    $tier = Get-Field $Record 'tier'
    if ($null -ne $tier -and "$tier".Trim() -eq '5') { $notes += 'epic: decomposes into child tickets' }
    return $notes
}

# --- assemble phases ----------------------------------------------------------
# Phase B holds the dependency chains: any BlockByOtherTask spec (always needs an
# unblock first) PLUS any other impl spec that has an in-plan blocker edge. Phase A
# is the remainder (no prerequisite). Both are filtered out of the single global
# topo order $ordered, so a blocker in Phase A always renders before its dependent
# in Phase B, and chains inside Phase B keep their topo order. A Phase A item has
# no in-plan blocker by construction, so it can never depend on a Phase B item.
$phaseB = @($ordered | Where-Object { $_.status -eq 'BlockByOtherTask' -or @($blockersOf[$_.id]).Count -gt 0 })
$phaseBids = @{}; foreach ($x in $phaseB) { $phaseBids[$x.id] = $true }
$phaseA = @($ordered | Where-Object { -not $phaseBids.ContainsKey($_.id) })

$sortKeys = @(
    @{ Expression = { Get-QueueBucket -Id $_.id }; Descending = $false },
    @{ Expression = { Get-QueueOrder -Id $_.id }; Descending = $false },
    @{ Expression = { [int]$_.priority }; Descending = $true },
    @{ Expression = { [string]$_.updated }; Descending = $true },
    @{ Expression = { [string]$_.id }; Descending = $false }
)

function New-PlanItem {
    param($Record, [string[]]$After = @())
    $notes = Get-Annotations $Record
    # BlockByOtherTask cannot run /spec-tech | /spec-dev while still Block*; the
    # operator must restore its pre-block status first. The pre-block status is not
    # stored on the record, so emit the update.ps1 shape with a placeholder.
    $unblock = $null
    if ($Record.status -eq 'BlockByOtherTask') {
        $unblock = "update.ps1 -Id $($Record.id) -Status <pre-block status>"
    }
    [PSCustomObject]@{
        id       = $Record.id
        status   = $Record.status
        priority = [int]$Record.priority
        name     = $Record.name
        after    = @($After)
        unblock  = $unblock
        commands = @(Get-CommandsForStatus $Record.status $Record.id)
        notes    = @($notes)
    }
}

$itemsA = @($phaseA | ForEach-Object { New-PlanItem $_ -After $blockersOf[$_.id] })
$itemsB = @($phaseB | ForEach-Object { New-PlanItem $_ -After $blockersOf[$_.id] })
$itemsImpl = @($verifyImpl | Sort-Object $sortKeys | ForEach-Object { New-PlanItem $_ })

$deviceIds = @($deviceTest | Sort-Object $sortKeys | ForEach-Object { $_.id })

$deferredItems = @($deferred | Sort-Object $sortKeys |
    ForEach-Object {
        [PSCustomObject]@{ id = $_.id; status = $_.status; priority = [int]$_.priority; name = $_.name }
    })

$releaseLine = if ($Flavors.Trim()) { "/skill-release $($Flavors.Trim())" } else { '/skill-release' }

$plan = [PSCustomObject]@{
    generated_from = 'PLAN/spec-catalog.jsonl (active), ordered by PLAN/RELEASE_QUEUE.md'
    counts = [PSCustomObject]@{
        open               = $all.Count
        implementation     = $itemsA.Count
        blocked_chains     = $itemsB.Count
        verify_implemented = $itemsImpl.Count
        device_test        = $deviceIds.Count
        deferred           = $deferredItems.Count
    }
    phases = [PSCustomObject]@{
        A_implementation = $itemsA
        B_blocked_chains = $itemsB
        C_verification   = [PSCustomObject]@{
            device_sweep = [PSCustomObject]@{ command = '/spec-sweep'; count = $deviceIds.Count; ids = $deviceIds }
            implemented  = $itemsImpl
        }
        D_release = @('/spec-prerelease', $releaseLine)
    }
    deferred = $deferredItems
}

# --- render -------------------------------------------------------------------
if ($Format -eq 'json') {
    $plan | ConvertTo-Json -Depth 8 -Compress
    exit 0
}

$pad = 18   # command-verb column width for alignment
function Format-CmdLine {
    param([string]$Command, [string]$Comment)
    if ($Comment) { return ('{0,-' + $pad + '} # {1}') -f $Command, $Comment }
    return $Command
}

$out = New-Object System.Collections.Generic.List[string]
$c = $plan.counts
$out.Add('# === RELEASE SEQUENCE (generated from spec-catalog) ===')
$out.Add("# open=$($c.open)  implement=$($c.implementation)  blocked-chain=$($c.blocked_chains)  implemented=$($c.verify_implemented)  device-test=$($c.device_test)  deferred=$($c.deferred)")
$out.Add('')

$out.Add('# -- Phase A: implementation (release-queue order: package, then line order; every Draft/Approved listed) --')
if ($itemsA.Count -eq 0) { $out.Add('#   (none)') }
foreach ($it in $itemsA) {
    $tag = "$($it.status) p$($it.priority): $($it.name)"
    if ($it.notes.Count -gt 0) { $tag += '  [' + ($it.notes -join '; ') + ']' }
    $first = $true
    foreach ($cmd in $it.commands) {
        $out.Add((Format-CmdLine $cmd ($(if ($first) { $tag } else { '' }))))
        $first = $false
    }
}
$out.Add('')

$out.Add('# -- Phase B: dependency chains (each item runs after its blocker; BlockByOtherTask rows need an unblock first) --')
if ($itemsB.Count -eq 0) { $out.Add('#   (none)') }
foreach ($it in $itemsB) {
    $after = if ($it.after.Count -gt 0) { ' (after ' + ($it.after -join ', ') + ')' } else { '' }
    $tag = "$($it.status) p$($it.priority)${after}: $($it.name)"
    if ($it.unblock) {
        $out.Add("#   $($it.id): unblock first - $($it.unblock) - then (/spec-tech only if it never reached Tactical):")
    }
    $first = $true
    foreach ($cmd in $it.commands) {
        $out.Add((Format-CmdLine $cmd ($(if ($first) { $tag } else { '' }))))
        $first = $false
    }
}
$out.Add('')

$out.Add('# -- Phase C: device-test backlog -> Verified --')
if ($deviceIds.Count -gt 0) {
    $out.Add((Format-CmdLine '/spec-sweep' "batch-test $($deviceIds.Count) BlockNeedUserTest tickets"))
} else {
    $out.Add('#   (no BlockNeedUserTest backlog)')
}
if ($itemsImpl.Count -gt 0) {
    $out.Add('#   Implemented tickets: /spec-test-device needs a device online; with none, skip to /spec-check (static audit)')
}
foreach ($it in $itemsImpl) {
    $tag = "Implemented p$($it.priority): $($it.name)"
    $out.Add((Format-CmdLine "/spec-test-device $($it.id)" $tag))
    $out.Add((Format-CmdLine "/spec-check $($it.id)" ''))
}
$out.Add('')

$out.Add('# -- Phase D: release (run from a DEBUG-v00N branch) --')
foreach ($cmd in $plan.phases.D_release) { $out.Add($cmd) }
$out.Add('')

$out.Add('# -- Deferred (external/human gate - cannot drive from catalog) --')
if ($deferredItems.Count -eq 0) { $out.Add('#   (none)') }
foreach ($d in $deferredItems) {
    $out.Add("#   $($d.id) $($d.status) p$($d.priority): $($d.name)")
}

$out -join "`n"
exit 0
