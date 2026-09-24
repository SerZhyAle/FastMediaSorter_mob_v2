#requires -Version 7.0
<#
.SYNOPSIS
    S2870: shared reader for the gate-placement registry and for runner membership.

.DESCRIPTION
    Dot-sourced by assert-gate-placement.ps1 (the verifier) and by measure-gate-frequency.ps1 (the
    placement report). The rule lives here once so the verdict written at closure and the view the
    operator reads at the release boundary cannot disagree - the S1621 rule.

    Membership is read from a script's STRING TOKENS, never from its raw text. Every runner's
    comment-based help enumerates the gates it runs, and assert-fast-gates.ps1 additionally
    describes gates that LEFT it for release scope; a text grep therefore reports a gate as wired
    by the very paragraph explaining that it is not. Measured 2026-09-10 while seeding the
    registry: the grep answer was 43/60/18 gates against the token answer's 39/59/17, and all four
    of the extra names were prose.

    The prerelease stage is fed by a command file as well as by its gate script: step 0.6 of
    /spec-prerelease invokes assert-deobfuscation-retained.ps1 directly, so a membership map that
    reads only .ps1 runners reports that gate as wired nowhere.

    Ticket openness (CHECK-PLACEMENT 0.10 rule 7) is read from a spec-catalog journal path the
    caller names - one JSON Lines record per ticket carrying `id` and `status`. Verified counts as
    closed, not open: a finished ticket will never come back to judge the seeded record that names
    it. The functions take the path as a parameter and hold no repository-root default, so the
    fixture suite can drive them from sandbox trees.

.NOTES
    Exit codes (CLAUDE.md Rule 7): none - this file defines functions and returns no exit code.
    A caller that dot-sources it and passes an unreadable path gets a terminating error.
#>

# The runner that satisfies each placement class, in the order a gate's real placement is decided.
# per-ticket comes first because a gate wired into the closure IS per-ticket whatever else lists it.
# fast-batch comes LAST on purpose: membership in the hand-typed batch is what a gate falls back to
# when no closure, release or build runner claims it, and it is never what satisfies per-ticket.
$script:GatePlacementPrecedence = @('per-ticket', 'release-scope', 'prerelease-content', 'build', 'fast-batch')

# Aggregators that match the assert-* glob but run other gates rather than checking anything.
$script:GatePlacementRunnerFiles = @(
    'assert-fast-gates.ps1'
    'assert-release-scope-gates.ps1'
    'assert-prerelease-content-gates.ps1'
)

# The journal statuses that mean a ticket can no longer act on anything - the two finished states.
# A seeded record whose owner reached one of them will never be judged by it; every other status,
# and only an id this set answers for, counts as an open owner.
$script:GatePlacementClosedTicketStatuses = @('Verified', 'Archived')

function Get-GatePlacementRegistryPath {
    param([Parameter(Mandatory)][string]$RepoRoot)
    return (Join-Path $RepoRoot 'scripts/quality/gate-placement.jsonl')
}

# Both return plain string arrays of fixed length > 1, so they are emitted bare: wrapping them with
# the comma operator would hand the caller a ONE-element array holding the array, and `@(..)` around
# that does not unroll it - the known-scope list then reads as the single value 'System.Object[]'.
function Get-GatePlacementRunnerNames {
    return $script:GatePlacementRunnerFiles
}

function Get-GatePlacementPrecedence {
    return $script:GatePlacementPrecedence
}

<#
.SYNOPSIS
    Gate script names referenced by one file, read from string tokens (.ps1) or command lines (.md).
#>
function Get-GateReferenceNames {
    param([Parameter(Mandatory)][string]$Path)

    $names = [System.Collections.Generic.HashSet[string]]::new()
    if (-not (Test-Path -LiteralPath $Path)) { return , $names }

    if ($Path -like '*.md') {
        # A command driver is prose around shell lines; only a line that actually invokes pwsh is
        # an invocation, so a gate merely named in a sentence does not count as wired.
        foreach ($line in (Get-Content -LiteralPath $Path)) {
            if ($line -notmatch 'pwsh') { continue }
            foreach ($m in [regex]::Matches($line, 'assert-[A-Za-z0-9-]+\.ps1')) { [void]$names.Add($m.Value) }
        }
        return , $names
    }

    $tokens = $null; $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors)
    foreach ($t in $tokens) {
        if ($t.Kind -notin @('StringLiteral', 'StringExpandable')) { continue }
        foreach ($m in [regex]::Matches([string]$t.Value, 'assert-[A-Za-z0-9-]+\.ps1')) { [void]$names.Add($m.Value) }
    }
    # Comma operator: a bare return enumerates the set into the pipeline, so a one-element set
    # arrives as a String, whose .Contains() is SUBSTRING matching and answers wrong in silence.
    return , $names
}

<#
.SYNOPSIS
    Gate script names referenced by one file and by the lib files it dot-sources.

.DESCRIPTION
    S3254: a runner's argument vectors can live in a dot-sourced lib beside it (the Rule 2 ceiling
    extraction of post-change.ps1), so a gate's only textual reference may sit one hop from the
    runner's own text. This follows exactly that hop: string tokens of the runner naming a
    lib/*.ps1 are resolved against the repo root when they carry it, else against the runner's own
    directory, and the lib's token references are unioned in. One level only - no lib in this tree
    dot-sources another.
#>
function Get-GateReferenceNamesWithLibs {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$RepoRoot
    )

    $names = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($n in (Get-GateReferenceNames $Path)) { [void]$names.Add($n) }

    $tokens = $null; $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors)
    $runnerDir = Split-Path -Parent $Path
    foreach ($t in $tokens) {
        if ($t.Kind -notin @('StringLiteral', 'StringExpandable')) { continue }
        $lib = [string]$t.Value
        if ($lib -notmatch '(^|[\\/])lib[\\/][A-Za-z0-9_-]+\.ps1$') { continue }
        $libPath = if ($lib -match '^([A-Za-z]:|[\\/])') { $lib }
            elseif ($lib -match '^scripts[\\/]') { Join-Path $RepoRoot $lib }
            else { Join-Path $runnerDir $lib }
        if (-not (Test-Path -LiteralPath $libPath)) { continue }
        foreach ($n in (Get-GateReferenceNames $libPath)) { [void]$names.Add($n) }
    }
    return , $names
}

<#
.SYNOPSIS
    Map of placement class -> set of gate names the runners for that class actually reference.
#>
function Get-GatePlacementMembership {
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [hashtable]$SourceOverride
    )

    $sources = if ($SourceOverride) { $SourceOverride } else {
        @{
            'per-ticket'         = @('scripts/post-change.ps1')
            'release-scope'      = @('scripts/quality/assert-release-scope-gates.ps1')
            'prerelease-content' = @('scripts/quality/assert-prerelease-content-gates.ps1', '.claude/commands/spec-prerelease.md', '.claude/commands/spec-prerelease-wear.md')
            'fast-batch'         = @('scripts/quality/assert-fast-gates.ps1')
            'build'              = @('scripts/release/standard-release-gate.ps1')
        }
    }

    $map = @{}
    foreach ($class in $sources.Keys) {
        $set = [System.Collections.Generic.HashSet[string]]::new()
        foreach ($rel in $sources[$class]) {
            foreach ($n in (Get-GateReferenceNamesWithLibs -Path (Join-Path $RepoRoot $rel) -RepoRoot $RepoRoot)) { [void]$set.Add($n) }
        }
        $map[$class] = $set
    }

    # The build scripts invoke a couple of gates directly rather than through any runner.
    if (-not $SourceOverride) {
        $buildersDir = Join-Path $RepoRoot 'scripts/builders'
        if (Test-Path -LiteralPath $buildersDir) {
            foreach ($f in (Get-ChildItem -LiteralPath $buildersDir -Filter '*.ps1' -File)) {
                foreach ($n in (Get-GateReferenceNames $f.FullName)) { [void]$map['build'].Add($n) }
            }
        }
    }

    return $map
}

<#
.SYNOPSIS
    Placement class a gate's actual wiring implies, independent of what the registry declares.
#>
function Resolve-GatePlacementScope {
    param(
        [Parameter(Mandatory)][string]$GateName,
        [Parameter(Mandatory)][hashtable]$Membership
    )

    if ($script:GatePlacementRunnerFiles -contains $GateName) { return 'runner' }
    foreach ($class in $script:GatePlacementPrecedence) {
        if ($Membership.ContainsKey($class) -and $Membership[$class].Contains($GateName)) { return $class }
    }
    return 'hand-run'
}

<#
.SYNOPSIS
    Map of gate name -> the sibling gate that invokes it directly.

.DESCRIPTION
    A gate invoked by ANOTHER gate is a stage of it: it inherits its parent's placement and must
    not be read as unwired. assert-settings-catalog-complete.ps1 is stage 1 of
    assert-settings-doc-sync.ps1 and is referenced by no runner at all.
#>
function Get-GateStageParents {
    param([Parameter(Mandatory)][string]$RepoRoot)

    $parents = @{}
    $dir = Join-Path $RepoRoot 'scripts/quality'
    if (-not (Test-Path -LiteralPath $dir)) { return $parents }

    foreach ($f in (Get-ChildItem -LiteralPath $dir -Filter 'assert-*.ps1' -File -Depth 1 | Sort-Object Name)) {
        if ($script:GatePlacementRunnerFiles -contains $f.Name) { continue }
        foreach ($n in (Get-GateReferenceNames $f.FullName)) {
            if ($n -ne $f.Name -and -not $parents.ContainsKey($n)) { $parents[$n] = $f.Name }
        }
    }
    return $parents
}

<#
.SYNOPSIS
    Registry records, in file order, with the source line number for reporting.
#>
function Get-GatePlacementRecords {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) { throw "gate-placement registry not found: $Path" }

    $records = [System.Collections.Generic.List[object]]::new()
    $n = 0
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        $n++
        if (-not $line.Trim()) { continue }
        try { $rec = $line | ConvertFrom-Json }
        catch { throw "gate-placement registry line ${n}: not valid JSON - $($_.Exception.Message)" }
        $rec | Add-Member -NotePropertyName 'line' -NotePropertyValue $n -Force
        $records.Add($rec)
    }
    return , $records
}

<#
.SYNOPSIS
    Map of ticket id -> journal status, read from a spec-catalog journal.

.DESCRIPTION
    Returns $null when the path is missing or a line is not valid JSON - the caller decides what
    an unreadable journal means. The gate turns that into its exit-2 cannot-verify, exactly as it
    does for an unreadable registry; a fixture without a journal must say so, never read as
    "every owner is open".
#>
function Get-GatePlacementJournalStatuses {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) { return $null }

    $map = @{}
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        if (-not $line.Trim()) { continue }
        try { $rec = $line | ConvertFrom-Json } catch { return $null }
        if (-not $rec.id) { continue }
        $map[[string]$rec.id] = [string]$rec.status
    }
    return $map
}

<#
.SYNOPSIS
    Whether one ticket id can still act on the record that names it.

.DESCRIPTION
    Closed = status Verified, status Archived, or the id absent from the map. Open = any other
    journal status (Draft, Approved, Tactical, In Progress, Implemented, Partial, Broken, and the
    Block* states can all still act on their tickets). An absent or null map answers closed for
    everything, so a caller that skipped loading the journal cannot silently pass seeded records.
#>
function Test-GatePlacementOwnerOpen {
    param(
        [Parameter(Mandatory)][string]$TicketId,
        [AllowNull()][hashtable]$StatusMap
    )

    if (-not $StatusMap) { return $false }
    if (-not $StatusMap.ContainsKey($TicketId)) { return $false }
    return $script:GatePlacementClosedTicketStatuses -notcontains $StatusMap[$TicketId]
}
