#requires -Version 7.0
<#
.SYNOPSIS
    Refuse an acceptance criterion that rests on accumulated state without naming it (S1914).

.DESCRIPTION
    Reads only the acceptance section of `PLAN/Sxxxx_*.md`. A criterion that claims something
    survives - a migration, an import, an upgrade, a merge - is satisfied identically on a device
    where that something never existed. S1832 is the measured case: five criteria required pinned
    channels, order, history and a launcher cell to survive a schema upgrade, and the device carried
    no pinned channel and no cell at all. Run as written, every criterion passed, because nothing
    was there to lose. Only a migration log line printing a count told the difference, and only
    because a human had seeded the state by hand.

    So this gate asks one question of such a criterion: does it name the state that must exist
    before the run? The accepted form is not a new field but the wording the corpus already uses in
    three of eight sampled criteria - "a channel ALREADY PINNED AND WITH HISTORY survives ..".

    Deliberately narrow (strategic S1914 section 6.4). It fires only where state accumulates OUTSIDE
    the test session. A criterion about surviving a restart or a rotation is procedurally safe - the
    tester sets the toggle and then restarts, so the state is created by a step of the same scenario -
    and demanding a precondition there would be ritual.

.PARAMETER Gate
    Gate framing: exit 1 on any violation outside the baseline. Without it the script reports and
    exits 0, so it can be run for information without failing a closure.

.PARAMETER Quiet
    Suppress the informational counters. Violations are always printed.

.PARAMETER Path
    Audit a single file instead of the corpus. Used by the fixtures in the ticket's own proof and by
    an author checking one spec before closing it.

.PARAMETER UpdateBaseline
    Rewrite the baseline from the current corpus. Authoring mode, never used by a gate run.

.NOTES
    Exit codes:
      0 - clean, or violations reported in audit mode.
      1 - `-Gate` found a criterion outside the baseline that names no precondition.
      2 - the spec corpus or the baseline cannot be read.
#>
[CmdletBinding()]
param(
    [switch] $Gate,
    [switch] $Quiet,
    [string] $Path,
    [switch] $UpdateBaseline
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$planRoot = Join-Path $repoRoot 'PLAN'
$baselinePath = Join-Path $PSScriptRoot 'acceptance-precondition-baseline.txt'

# S1914 section 6.2. A criterion only interests this gate when it claims survival.
# Stems, not full forms: the corpus inflects these freely, and matching "сохраняется" while missing
# "сохраняет" is how the first run of this gate reported zero violations across 308 specs.
$survivalTriggers = @(
    'пережива', 'сохраня', 'не теря', 'уцеле', 'не потеря',
    'survive', 'preserved', 'retained', 'kept across'
)

# S1914 section 6.4. Narrow to state accumulated outside the test session. A restart or a rotation
# is created by a scenario step, so it is excluded on purpose rather than forgotten.
$accumulationMarkers = @(
    'миграц', 'импорт', 'апгрейд', 'обновлени схем', 'схем', 'слияни', 'банк', 'накопл',
    'migration', 'import', 'upgrade', 'schema', 'merge', 'accumulated', 'existing data', 'legacy'
)

$sessionLocalMarkers = @(
    'перезапуск', 'поворот', 'restart', 'rotation', 'relaunch', 'process death', 'recreate'
)

# A precondition is a named prior state. These are the shapes the corpus already uses.
$preconditionMarkers = @(
    'уже', 'ранее', 'существующ', 'заранее', 'предварительно', 'при наличии', 'непуст',
    'already', 'pre-existing', 'preexisting', 'previously', 'seeded', 'given a', 'given an',
    'with at least', 'non-empty', 'that exists', 'having'
)

function Test-ContainsAny {
    param([string] $Text, [string[]] $Needles)
    foreach ($needle in $Needles) {
        if ($Text -like "*$needle*") { return $true }
    }
    return $false
}

function Get-AcceptanceCriteria {
    <#
        Returns the criterion lines of a spec's acceptance section. The section is found by heading
        rather than by number, because specs written at different times number it differently.
    #>
    param([string] $FilePath)

    $lines = Get-Content -LiteralPath $FilePath -ErrorAction Stop
    $inSection = $false
    $criteria = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^#{2,3}\s') {
            $heading = $line.ToLowerInvariant()
            # The corpus writes this heading three ways - "Критерии приёмки", "Критерии
            # готовности" and "Acceptance criteria". Matching only the first found zero violations
            # across 308 specs while S1832 alone carries five, which is the same vacuous pass this
            # gate exists to refuse.
            $inSection = ($heading -match 'критери') -or ($heading -match 'acceptance')
            continue
        }
        if (-not $inSection) { continue }
        $trimmed = $line.Trim()
        if ($trimmed -match '^(-|\*|\d+\.)\s+\S') {
            $criteria += [pscustomobject]@{ Line = $i + 1; Text = $trimmed }
        }
    }
    return $criteria
}

function Test-Violation {
    <# A criterion violates when it claims survival of accumulated state and names no prior state. #>
    param([string] $Text)

    $lower = $Text.ToLowerInvariant()
    if (-not (Test-ContainsAny -Text $lower -Needles $survivalTriggers)) { return $false }
    if (Test-ContainsAny -Text $lower -Needles $sessionLocalMarkers) { return $false }
    if (-not (Test-ContainsAny -Text $lower -Needles $accumulationMarkers)) { return $false }
    if (Test-ContainsAny -Text $lower -Needles $preconditionMarkers) { return $false }
    return $true
}

$specFiles = if ($Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        # Not Write-Error: with $ErrorActionPreference = 'Stop' it terminates the script before the
        # exit below, so the documented code 2 would never actually be delivered (CLAUDE.md Rule 7).
        [Console]::Error.WriteLine("assert-acceptance-preconditions: cannot read '$Path'")
        exit 2
    }
    @(Get-Item -LiteralPath $Path)
} else {
    if (-not (Test-Path -LiteralPath $planRoot)) {
        [Console]::Error.WriteLine("assert-acceptance-preconditions: cannot read the spec corpus at '$planRoot'")
        exit 2
    }
    @(Get-ChildItem -LiteralPath $planRoot -Filter 'S*.md' -File)
}

$violations = @()
foreach ($file in $specFiles) {
    foreach ($criterion in (Get-AcceptanceCriteria -FilePath $file.FullName)) {
        if (Test-Violation -Text $criterion.Text) {
            $violations += [pscustomobject]@{
                Key  = "$($file.Name):$($criterion.Line)"
                Text = $criterion.Text
            }
        }
    }
}

if ($UpdateBaseline) {
    $header = @(
        '# S1914 acceptance-precondition baseline.',
        '# A debt record, not an approval: every line is a criterion that claims accumulated state',
        '# survives without naming the state that must exist first. Entries leave this list when the',
        '# criterion is reworded; nothing is added except by a deliberate -UpdateBaseline run.'
    )
    Set-Content -LiteralPath $baselinePath -Value ($header + (@($violations) | ForEach-Object { $_.Key })) -Encoding utf8
    Write-Host "assert-acceptance-preconditions: baseline rewritten with $(@($violations).Count) entry(ies)."
    exit 0
}

[string[]] $baseline = @()
if (Test-Path -LiteralPath $baselinePath) {
    $baseline = @(Get-Content -LiteralPath $baselinePath |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and -not $_.StartsWith('#') })
}

$new = @($violations | Where-Object { $baseline -notcontains $_.Key })

if ($new.Count -gt 0) {
    Write-Host 'Acceptance criteria resting on accumulated state without naming a precondition:'
    Write-Host ''
    foreach ($violation in $new) {
        Write-Host "  $($violation.Key)"
        Write-Host "      $($violation.Text)"
    }
    Write-Host ''
    Write-Host 'Name the state that must exist before the run, in the criterion itself - for example'
    Write-Host '"a channel ALREADY PINNED AND WITH HISTORY survives the upgrade". A run on a device'
    Write-Host 'without that state passes such a criterion by having nothing to lose (S1832/S1914).'
}

if (-not $Quiet) {
    Write-Host ("assert-acceptance-preconditions: expected: 0 | actual: {0} new violation(s) ({1} spec(s), {2} baselined)" -f
        @($new).Count, @($specFiles).Count, @($baseline).Count)
}

if ($Gate -and $new.Count -gt 0) { exit 1 }
exit 0
