#requires -Version 7.0
<#
.SYNOPSIS
    S2870: the gate-placement registry must agree with where the gates are actually wired.

.DESCRIPTION
    CLAUDE.md Rule 33 places a gate by its subject and says a new gate names its scope class at
    birth, with "unnamed still means per ticket". Until this gate existed that requirement was
    prose, and so was every relocation decision the project had made: S1939 moved three gates out
    of the fast batch and recorded the reasoning in a .DESCRIPTION paragraph, which nothing can
    query and nothing can check. The registry (scripts/quality/gate-placement.jsonl) is the
    decision record; this script is what makes it binding, per Rule 33's own requirement that a
    relocation be a script with an exit code rather than a line of prose.

    It checks both directions, and the backward one is why it exists:

      FORWARD  a gate declared release-scope that someone re-adds to the closure now fails until
               the registry row is updated, which forces the mover to state a reason.
      BACKWARD a gate declared per-ticket that is NOT in post-change.ps1 fails. Membership in
               assert-fast-gates.ps1 does not satisfy per-ticket and never has: `fg` is a target a
               human types, while post-change.ps1 runs at every closure. That distinction is not
               pedantry - it is four recorded incidents. S2093 wired a parity gate into `fg` alone
               on the written premise that "the fast batch is what every closure already runs", and
               the step verified green. S2300 left assert-ctor-arg-slots.ps1 in `fg` alone and the
               236th AppSettings field crossed the JVM's 255-slot ceiling, killing the app in
               Application.onCreate on the owner's phone. S2306 left assert-migration-test-pairing
               in `fg` alone and a Room migration with no instrumented test deleted the owner's
               database on first launch. Every one of those gates existed, was correct, and passed
               when run by hand.

    Placement classes, and the runner that satisfies each:
      per-ticket          scripts/post-change.ps1
      release-scope       scripts/quality/assert-release-scope-gates.ps1
      prerelease-content  assert-prerelease-content-gates.ps1 or /spec-prerelease's own step
      build               scripts/release/standard-release-gate.ps1 or scripts/builders/*
      fast-batch          assert-fast-gates.ps1 ONLY - runs when an operator types fg
      hand-run            no runner references it
      stage               invoked by another gate, whose placement it inherits
      runner              an aggregator that runs other gates, with no placement of its own

    fast-batch is a measured state, not an aspiration: 33 of the 108 gates were in it on
    2026-09-10. This gate does not call that a defect - naming and counting it is what lets the
    next placement decision be made from a list instead of a guess (S2870 non-goal).

.PARAMETER Gate
    Exit non-zero on findings. Without it the script reports and exits 0.

.PARAMETER Quiet
    Suppress the per-record PASS chatter; findings still print.

.PARAMETER ChangedFiles
    The closure's changed set. Under the fixed-input contract (S2824) this gate is FATAL only when
    the set carries one of its declared inputs, and advisory when it carries none - the registry
    describes the whole tree, so judging it against another session's unrelated change would go red
    on debt that change does not own.

.PARAMETER RepoRoot
    Repository root. Defaults to the parent of scripts/quality.

.PARAMETER Registry
    Read this registry instead of the repository's own. Exists so the regression suite can judge
    fixed input rather than whatever the host tree currently carries.

.PARAMETER SourceMap
    JSON object mapping placement class to an array of runner paths, replacing the built-in map.
    For the suite only.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-gate-placement.ps1 -Gate

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  registry and wiring agree (or findings exist but -Gate was not passed).
      1  at least one finding, under -Gate with a declared input in the changed set.
      2  cannot verify - the registry is missing or a line is not valid JSON.
      3  advisory: findings exist but no declared input was in the changed set (S2824).
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    [string[]]$ChangedFiles,
    [string]$RepoRoot,
    [string]$Registry,
    [string]$SourceMap,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }
. (Join-Path $PSScriptRoot 'lib/gate-placement-registry.ps1')

$fixedInputLib = Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1'
if (Test-Path -LiteralPath $fixedInputLib) { . $fixedInputLib }

$registryPath = if ($Registry) { $Registry } else { Get-GatePlacementRegistryPath -RepoRoot $RepoRoot }

try {
    $records = Get-GatePlacementRecords -Path $registryPath
}
catch {
    Write-Error "assert-gate-placement: CANNOT VERIFY - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

$sourceOverride = $null
if ($SourceMap) {
    $sourceOverride = @{}
    foreach ($p in ($SourceMap | ConvertFrom-Json).PSObject.Properties) { $sourceOverride[$p.Name] = @($p.Value) }
}

$membership = Get-GatePlacementMembership -RepoRoot $RepoRoot -SourceOverride $sourceOverride
$stageParents = Get-GateStageParents -RepoRoot $RepoRoot
$runnerNames = Get-GatePlacementRunnerNames
$knownScopes = @(Get-GatePlacementPrecedence) + @('hand-run', 'stage', 'runner')
$knownKinds = @('gate', 'runner', 'forwarder', 'stage', 'closure-step')

$gateDir = Join-Path $RepoRoot 'scripts/quality'
$onDisk = @(Get-ChildItem -LiteralPath $gateDir -Filter 'assert-*.ps1' -File -Depth 1 | Sort-Object Name | ForEach-Object { $_.Name })

$findings = [System.Collections.Generic.List[string]]::new()

# 1. Exactly one record per gate on disk.
$byGate = @{}
foreach ($r in $records) {
    if ($byGate.ContainsKey($r.gate)) {
        $findings.Add("duplicate record for $($r.gate) (lines $($byGate[$r.gate].line) and $($r.line)) - a gate has exactly one placement.")
        continue
    }
    $byGate[$r.gate] = $r
}
foreach ($name in $onDisk) {
    if ($byGate.ContainsKey($name)) { continue }
    $implied = Resolve-GatePlacementScope -GateName $name -Membership $membership
    $findings.Add("$name has no registry record. Rule 33: a new gate names its scope class at birth. Its wiring implies '$implied' - add a row to scripts/quality/gate-placement.jsonl stating that, who decided it and why.")
}
foreach ($r in $records) {
    if ($onDisk -contains $r.gate) { continue }
    # A closure step is a labelled step of post-change.ps1 (dev-log, catalog-sync, ..), not a script
    # under scripts/quality. It is in the registry so the placement report can tell a step that
    # CANNOT find a defect from a gate that merely has not - otherwise the report nominates the
    # changelog writer as an expensive gate that never catches anything, forever.
    if ($r.kind -eq 'closure-step') { continue }
    $findings.Add("registry line $($r.line) names $($r.gate), which is not on disk. Delete the row, or restore the gate.")
}

# 2. Field vocabulary.
foreach ($r in $records) {
    if ($knownScopes -notcontains $r.scope) {
        $findings.Add("$($r.gate): unknown scope '$($r.scope)'. Known: $($knownScopes -join ', ').")
    }
    if ($knownKinds -notcontains $r.kind) {
        $findings.Add("$($r.gate): unknown kind '$($r.kind)'. Known: $($knownKinds -join ', ').")
    }
    if ($r.basis -notin @('judged', 'seeded')) {
        $findings.Add("$($r.gate): basis must be 'judged' or 'seeded', not '$($r.basis)'.")
    }
    if (-not "$($r.reason)".Trim()) {
        $findings.Add("$($r.gate): empty reason. The registry exists to carry the reason; a row without one records nothing.")
    }
    if ($r.kind -eq 'forwarder' -and "$($r.reason)" -notmatch 'dimension') {
        $findings.Add("$($r.gate): declared a forwarder but its reason names no umbrella dimension. Say which assert-source-gates.ps1 dimension runs the rule.")
    }
}

# 3. The declared class must match where the gate is actually wired, in both directions.
foreach ($r in $records) {
    if ($r.kind -eq 'closure-step') { continue }
    if ($onDisk -notcontains $r.gate) { continue }
    if ($knownScopes -notcontains $r.scope) { continue }

    $actual = Resolve-GatePlacementScope -GateName $r.gate -Membership $membership
    if ($actual -eq 'hand-run' -and $stageParents.ContainsKey($r.gate)) { $actual = 'stage' }

    if ($actual -eq $r.scope) { continue }

    if ($r.scope -eq 'per-ticket') {
        $findings.Add(("$($r.gate): declared per-ticket but scripts/post-change.ps1 does not reference it (actual placement: $actual). " +
            'A gate in the fast batch alone runs only when an operator types fg - that is how S2300 crashed the app and S2306 deleted the database. ' +
            'Wire it into post-change.ps1, or change the row to the class it really has and say who decided that.'))
    }
    else {
        $findings.Add("$($r.gate): declared '$($r.scope)' but its wiring says '$actual'. Update the runner, or update the row - and if the placement genuinely moved, record the ticket and the reason.")
    }
}

# 4. kind/scope agreement for the two structural kinds.
foreach ($r in $records) {
    $isRunnerFile = $runnerNames -contains $r.gate
    if ($isRunnerFile -and $r.kind -ne 'runner') {
        $findings.Add("$($r.gate) is an aggregator - kind must be 'runner', not '$($r.kind)'.")
    }
    if (-not $isRunnerFile -and $r.kind -eq 'runner') {
        $findings.Add("$($r.gate): kind 'runner' is reserved for the aggregators ($($runnerNames -join ', ')).")
    }
    if ($r.kind -eq 'stage' -and $r.scope -ne 'stage') {
        $findings.Add("$($r.gate): kind 'stage' requires scope 'stage'.")
    }
}

if (-not $Quiet) {
    Write-Host ("assert-gate-placement: {0} records, {1} gates on disk." -f $records.Count, $onDisk.Count)
    $judged = @($records | Where-Object { $_.basis -eq 'judged' }).Count
    Write-Host ("  judged {0}, seeded {1}" -f $judged, ($records.Count - $judged))
    foreach ($class in ($knownScopes)) {
        $n = @($records | Where-Object { $_.scope -eq $class }).Count
        if ($n) { Write-Host ("  {0,-20} {1,3}" -f $class, $n) }
    }
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) { Write-Host 'assert-gate-placement: PASS (registry matches the wiring).' -ForegroundColor Green }
    exit 0
}

# Fixed-input contract (S2824). The declared inputs are the registry, this gate's library, the five
# runner/build entry points, and EVERY gate script - a gate is added by creating one of those files,
# which is the case that must stay fatal.
$declaredInputs = @(
    $registryPath
    (Join-Path $PSScriptRoot 'lib/gate-placement-registry.ps1')
    (Join-Path $RepoRoot 'scripts/post-change.ps1')
    (Join-Path $RepoRoot 'scripts/quality/assert-fast-gates.ps1')
    (Join-Path $RepoRoot 'scripts/quality/assert-release-scope-gates.ps1')
    (Join-Path $RepoRoot 'scripts/quality/assert-prerelease-content-gates.ps1')
    (Join-Path $RepoRoot 'scripts/release/standard-release-gate.ps1')
) + @($onDisk | ForEach-Object { Join-Path $gateDir $_ })

$chargeable = $true
if (Get-Command Test-FixedInputsChargeable -ErrorAction SilentlyContinue) {
    $chargeable = Test-FixedInputsChargeable -ChangedFiles $ChangedFiles -InputPaths $declaredInputs
}

if (-not $chargeable) {
    Write-NotChargedVerdict -GateName 'assert-gate-placement' -Findings $findings
    if (-not $Gate) { exit 0 }
    exit 3
}

Write-Host ''
Write-Host ("assert-gate-placement: {0} finding(s)" -f $findings.Count) -ForegroundColor Red
foreach ($f in $findings) { Write-Host "  - $f" -ForegroundColor Red }
Write-Host ''
Write-Host '  The registry is scripts/quality/gate-placement.jsonl - one JSON object per line, fields:'
Write-Host '  gate, kind, scope, decided, ticket, basis (judged|seeded), reason.'

if (-not $Gate) { exit 0 }

exit 1
