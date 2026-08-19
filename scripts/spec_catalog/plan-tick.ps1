<#
.SYNOPSIS
    Batch state writer for tactical-plan steps (S1596).

.DESCRIPTION
    Executing a tactical plan used to cost one markdown edit per state change, on two surfaces:
    the step's inline marker in the phase file and the counters in INDEX.md. Measured over
    2026-08-05..11 that was 1 437 of 2 358 plan-file edits - about 61%, one turn each. This
    script moves an explicit list of steps in one operation.

    THERE IS NO WHOLE-PHASE FORM, and that is a safety property, not an omission. The cost of
    marking one unfinished step done exceeds the cost of the edits a short form would save, and
    the explicit list is also the execution trace: a process audit reads the transcript, and an
    invocation carrying ticket, phase and step numbers proves which steps were executed, where
    the hand edit it replaces only proved that some plan file changed.

    Every state is reversible through the same parameter, so a wrong batch is undone the way it
    was made.

.PARAMETER Id
    Ticket id, Sxxxx. Its plan folder is PLAN/<Id>_<slug>/.

.PARAMETER Phase
    Phase number, one or two digits. Selects PLAN/<Id>_<slug>/PHASE_<Phase>__*.md.

.PARAMETER Steps
    Comma-separated step numbers. Either bare (3,4,5) or fully qualified (02.3,02.4).
    A number with no matching step in the phase file is an error, never a silent no-op.

.PARAMETER State
    NotDone     -> `[ ]` not done
    InProgress  -> `[~]` in progress
    Done        -> `[x]` done
    Manual      -> `[manual - deferred]` <note>

.PARAMETER Note
    Trailing prose for the marker. On Done it replaces any existing trailing prose; without it
    existing prose is preserved. On Manual it is the body of the marker and is required.

.PARAMETER Log
    Text for the Step Log entry written when a step moves to Done. Defaults to -Note, then to a
    generated line. Separate from -Note because the marker carries a short clause and the log
    carries the account of the run; one parameter serving both forced a paragraph into the marker.

.PARAMETER Checkbox
    Comma-separated label fragments naming ordinary GFM bullets (- [ ] / - [x]) to flip.
    Mutually exclusive with -Steps. A fragment matching no bullet, or more than one, is an
    error naming the candidates - a guess here would tick the wrong gate silently.
    The separator is a comma, so a fragment may not contain one; pick a comma-free substring
    of the bullet instead.

.PARAMETER Target
    Which file the -Checkbox fragments are matched in: Phase (default) or Index.

.PARAMETER Json
    Emit a machine-readable summary instead of console lines.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/plan-tick.ps1 -Id S1596 -Phase 02 -Steps 3,4,5 -State Done
    Marks steps 02.3, 02.4 and 02.5 done in one call.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/plan-tick.ps1 -Id S1596 -Phase 02 -Steps 3,4,5 -State NotDone
    Undoes the batch above.

.EXIT CODES
    0 - every listed step was rewritten.
    1 - a listed step was not found, or a file could not be written.
    2 - usage error, or the plan folder or phase file does not exist.
    3 - INDEX.md and the phase file disagreed before the write; nothing was written at all.
    4 - a -Checkbox fragment matched no bullet, or matched more than one.
#>
[CmdletBinding(PositionalBinding = $false, DefaultParameterSetName = 'Steps')]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^S\d{4}$')]
    [string]$Id,

    [Parameter(Mandatory = $true, ParameterSetName = 'Steps')]
    [Parameter(Mandatory = $false, ParameterSetName = 'Checkbox')]
    [ValidatePattern('^\d{1,2}$')]
    [string]$Phase,

    [Parameter(Mandatory = $true, ParameterSetName = 'Steps')]
    [string]$Steps,

    [Parameter(Mandatory = $true, ParameterSetName = 'Checkbox')]
    [string]$Checkbox,

    [Parameter(ParameterSetName = 'Checkbox')]
    [ValidateSet('Phase', 'Index')]
    [string]$Target = 'Phase',

    [Parameter(Mandatory = $true)]
    [ValidateSet('NotDone', 'InProgress', 'Done', 'Manual')]
    [string]$State,

    [string]$Note = '',

    [string]$Log = '',

    [switch]$Json
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$planRoot = Join-Path $root 'PLAN'
# -Checkbox -Target Index needs no phase, so this must survive an absent -Phase.
$phaseNumber = if ([string]::IsNullOrWhiteSpace($Phase)) { '' } else { '{0:d2}' -f [int]$Phase }

if ($State -eq 'Manual' -and [string]::IsNullOrWhiteSpace($Note)) {
    Write-Error 'plan-tick: -State Manual requires -Note describing what is deferred and why.' -ErrorAction Continue
    exit 2
}

$planFolder = Get-ChildItem -LiteralPath $planRoot -Directory -Filter "$($Id)_*" -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $planFolder) {
    Write-Error "plan-tick: no tactical plan folder PLAN/$($Id)_<slug>/ - nothing to tick." -ErrorAction Continue
    exit 2
}

if ($PSCmdlet.ParameterSetName -eq 'Checkbox') {
    if ($State -notin @('NotDone', 'Done')) {
        Write-Error "plan-tick: -Checkbox supports -State NotDone or Done only; a GFM bullet has no '$State' form." -ErrorAction Continue
        exit 2
    }
    if ($Target -eq 'Phase' -and -not $PSBoundParameters.ContainsKey('Phase')) {
        Write-Error 'plan-tick: -Checkbox -Target Phase needs -Phase to say which phase file to match in.' -ErrorAction Continue
        exit 2
    }

    $boxFile = if ($Target -eq 'Index') {
        Join-Path $planFolder.FullName 'INDEX.md'
    } else {
        $candidate = Get-ChildItem -LiteralPath $planFolder.FullName -File -Filter "PHASE_$($phaseNumber)__*.md" -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($candidate) { $candidate.FullName } else { $null }
    }
    if (-not $boxFile -or -not (Test-Path -LiteralPath $boxFile)) {
        Write-Error "plan-tick: no file to match -Checkbox against for -Target $Target." -ErrorAction Continue
        exit 2
    }

    $boxLines = [System.IO.File]::ReadAllLines($boxFile)
    $boxMark = if ($State -eq 'Done') { 'x' } else { ' ' }
    $flipped = New-Object System.Collections.Generic.List[object]

    foreach ($rawFragment in ($Checkbox -split ',')) {
        $fragment = $rawFragment.Trim()
        if (-not $fragment) { continue }
        # Literal substring, NOT -like: a -like pattern reads backticks as escapes and brackets
        # as character classes, and plan files are made of `code spans` and [links]. A fragment
        # lifted straight out of the document would silently match nothing.
        $hits = @()
        for ($i = 0; $i -lt $boxLines.Length; $i++) {
            if ($boxLines[$i] -match '^\s*-\s*\[[ xX]\]\s*(.*)$') {
                $label = $Matches[1]
                if ($label.IndexOf($fragment, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                    $hits += $i
                }
            }
        }
        if ($hits.Count -ne 1) {
            $detail = if ($hits.Count -eq 0) { 'no bullet matched' } else { "matched $($hits.Count): " + (($hits | ForEach-Object { "line $($_ + 1)" }) -join ', ') }
            Write-Error "plan-tick: -Checkbox fragment '$fragment' is ambiguous - $detail. Nothing was written." -ErrorAction Continue
            exit 4
        }
        $line = $boxLines[$hits[0]]
        $boxLines[$hits[0]] = $line -replace '^(\s*-\s*)\[[ xX]\]', "`${1}[$boxMark]"
        $flipped.Add([PSCustomObject]@{ fragment = $fragment; line = $hits[0] + 1; after = $boxLines[$hits[0]].Trim() })
    }

    try {
        $boxRaw = [System.IO.File]::ReadAllText($boxFile)
        $boxNewline = if ($boxRaw -match "`r`n") { "`r`n" } else { "`n" }
        $boxTail = if ($boxRaw.EndsWith("`n")) { $boxNewline } else { '' }
        [System.IO.File]::WriteAllText($boxFile, ($boxLines -join $boxNewline) + $boxTail)
    } catch {
        Write-Error "plan-tick: could not write $(Split-Path -Leaf $boxFile): $($_.Exception.Message)" -ErrorAction Continue
        exit 1
    }

    $boxResult = [ordered]@{
        id      = $Id
        target  = $Target
        file    = (Split-Path -Leaf $boxFile)
        state   = $State
        flipped = @($flipped | ForEach-Object { $_.fragment })
        changed = $flipped.Count
    }
    if ($Json) {
        [PSCustomObject]$boxResult | ConvertTo-Json -Compress -Depth 4
    } else {
        Write-Host "plan-tick $Id $Target checkbox -> $State" -ForegroundColor Cyan
        foreach ($entry in $flipped) {
            Write-Host ("  line {0,-5} {1}" -f $entry.line, $entry.after) -ForegroundColor DarkGray
        }
    }
    exit 0
}

$phaseFile = Get-ChildItem -LiteralPath $planFolder.FullName -File -Filter "PHASE_$($phaseNumber)__*.md" -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $phaseFile) {
    Write-Error "plan-tick: no PHASE_$($phaseNumber)__*.md in $($planFolder.Name)." -ErrorAction Continue
    exit 2
}

# Step ids arrive either bare (3) or qualified (02.3). Both normalise to <phase>.<n>; a
# qualified id naming a different phase is a caller mistake worth refusing rather than
# silently retargeting.
$requested = New-Object System.Collections.Generic.List[string]
foreach ($token in ($Steps -split ',')) {
    $trimmed = $token.Trim()
    if (-not $trimmed) { continue }
    # The optional letter suffix is a real step id: a plan that inserts work between two already
    # ticked steps numbers it 4a rather than renumbering everything after it.
    if ($trimmed -match '^(\d{1,2})\.(\d{1,3})([a-z]?)$') {
        $tokenPhase = '{0:d2}' -f [int]$Matches[1]
        if ($tokenPhase -ne $phaseNumber) {
            Write-Error "plan-tick: step '$trimmed' names phase $tokenPhase but -Phase is $phaseNumber." -ErrorAction Continue
            exit 2
        }
        $requested.Add("$phaseNumber.$([int]$Matches[2])$($Matches[3])")
    } elseif ($trimmed -match '^(\d{1,3})([a-z]?)$') {
        $requested.Add("$phaseNumber.$([int]$Matches[1])$($Matches[2])")
    } else {
        Write-Error "plan-tick: '$trimmed' is not a step number." -ErrorAction Continue
        exit 2
    }
}
if ($requested.Count -eq 0) {
    Write-Error 'plan-tick: -Steps listed no step numbers.' -ErrorAction Continue
    exit 2
}

function Get-MarkerText {
    param(
        [Parameter(Mandatory)][string]$TargetState,
        [string]$Trailing = ''
    )
    switch ($TargetState) {
        'NotDone' { return '**Status:** `[ ]` not done' }
        'InProgress' { return '**Status:** `[~]` in progress' }
        'Manual' { return "**Status:** ``[manual - deferred]`` $Trailing" }
        default {
            if ($Trailing) { return "**Status:** ``[x]`` done - $Trailing" }
            return '**Status:** `[x]` done'
        }
    }
}

$lines = [System.IO.File]::ReadAllLines($phaseFile.FullName)

# Map every step heading to the index of the Status marker that belongs to it: the first
# marker after the heading and before the next heading. Scanning once keeps the rewrite
# independent of how much prose a step carries.
$markerIndexByStep = @{}
$currentStep = $null
for ($i = 0; $i -lt $lines.Length; $i++) {
    $line = $lines[$i]
    if ($line -match '^###\s+Step\s+(\d{1,2})\.(\d{1,3})([a-z]?)(?![\w.])') {
        $currentStep = "$('{0:d2}' -f [int]$Matches[1]).$([int]$Matches[2])$($Matches[3])"
        continue
    }
    if ($line -match '^##\s') { $currentStep = $null; continue }
    if ($currentStep -and $line -match '^\*\*Status:\*\*' -and -not $markerIndexByStep.ContainsKey($currentStep)) {
        $markerIndexByStep[$currentStep] = $i
    }
}

$missing = @($requested | Where-Object { -not $markerIndexByStep.ContainsKey($_) })
if ($missing.Count -gt 0) {
    Write-Error "plan-tick: no step marker for $($missing -join ', ') in $($phaseFile.Name) - nothing was written." -ErrorAction Continue
    exit 1
}

function Measure-DoneMarkers {
    # AllowEmptyString is load-bearing: a markdown file is mostly blank lines, and a mandatory
    # [string[]] rejects an array containing one.
    param(
        [Parameter(Mandatory)][AllowEmptyString()][AllowEmptyCollection()][string[]]$Body,
        [Parameter(Mandatory)][hashtable]$MarkerMap
    )
    $done = 0
    foreach ($index in $MarkerMap.Values) {
        if ($Body[$index] -match '^\*\*Status:\*\*\s*`\[x\]`') { $done++ }
    }
    return $done
}

$totalSteps = $markerIndexByStep.Count
$doneBefore = Measure-DoneMarkers -Body $lines -MarkerMap $markerIndexByStep

# The index is checked BEFORE anything is written. Reporting a divergence after half the write
# has landed would be a report about damage this script had just done.
$indexFile = Join-Path $planFolder.FullName 'INDEX.md'
$indexLines = $null
$indexRowNumber = -1
if (Test-Path -LiteralPath $indexFile) {
    $indexLines = [System.IO.File]::ReadAllLines($indexFile)
    for ($i = 0; $i -lt $indexLines.Length; $i++) {
        $cells = $indexLines[$i] -split '\|'
        if ($cells.Count -lt 7) { continue }
        if ($cells[1].Trim() -notmatch '^\d{1,2}$') { continue }
        if (('{0:d2}' -f [int]$cells[1].Trim()) -ne $phaseNumber) { continue }
        $indexRowNumber = $i
        break
    }
}

if ($indexRowNumber -ge 0) {
    $recorded = ($indexLines[$indexRowNumber] -split '\|')[5].Trim()
    if ($recorded -match '^(\d+)\s*/\s*(\d+)$') {
        $recordedDone = [int]$Matches[1]
        $recordedTotal = [int]$Matches[2]
        if ($recordedDone -ne $doneBefore -or $recordedTotal -ne $totalSteps) {
            # Built first, then reported on one line: a Write-Error whose -ErrorAction lands on a
            # continuation line reads to assert-exit-contract as a bare terminating call.
            $divergence = "plan-tick: INDEX.md says phase $phaseNumber is $recorded but $($phaseFile.Name) has $doneBefore/$totalSteps - the two surfaces disagree, so nothing was written. Reconcile them first."
            Write-Error $divergence -ErrorAction Continue
            exit 3
        }
    }
}

$changed = New-Object System.Collections.Generic.List[object]
foreach ($step in $requested) {
    $index = $markerIndexByStep[$step]
    $before = $lines[$index]

    # Trailing prose on a done marker records why, and is not the caller's to lose: it survives
    # unless -Note explicitly replaces it.
    $trailing = $Note
    if ($State -eq 'Done' -and -not $Note -and $before -match '^\*\*Status:\*\*\s*`\[x\]`\s*done\s*-\s*(.+)$') {
        $trailing = $Matches[1].Trim()
    }

    $after = Get-MarkerText -TargetState $State -Trailing $trailing
    $lines[$index] = $after
    $changed.Add([PSCustomObject]@{ step = $step; line = $index + 1; before = $before; after = $after })
}

$doneAfter = Measure-DoneMarkers -Body $lines -MarkerMap $markerIndexByStep

# The phase file carries the same counter in its own header; leaving it stale while fixing the
# index would swap one divergence for another. Done before the write, so the file is touched once.
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match '^\*\*Steps done:\*\*') {
        $lines[$i] = "**Steps done:** $doneAfter / $totalSteps"
        break
    }
}

# S1723: the phase file's own header used to be left alone, so a finished phase read
# "Status: not started" inside the very file documenting it while INDEX.md said Done for the same
# phase - measured on 2026-08-17 as 74 of 138 phase files in PLAN/. The counter above and the row
# in the index were kept in step with each other and with nothing else. Recomputed from the
# markers, never incremented, for the same reason the index row is: a header that already drifted
# by hand must end up equal to what the steps say, not equal to itself plus one.
#
# S1710: the two surfaces disagreed on the way down. The header recomputed all three states, while
# the index row only ever moved up - Not started -> In Progress -> Done - so a step reopened after a
# phase had finished left the row reading "✅ Done" above a "2/5" count, and **Phases:** below counts
# exactly those ticks, which made the whole plan claim one more finished phase than it had. One
# computed label now feeds both surfaces, so neither can drift from the markers in either direction.
$phaseStatusLabel = if ($doneAfter -ge $totalSteps -and $totalSteps -gt 0) {
    '✅ Done'
} elseif ($doneAfter -gt 0) {
    '🚧 In Progress'
} else {
    '⬜ Not started'
}
$phaseStatusText = "**Status:** $phaseStatusLabel"
$today = Get-Date -Format 'yyyy-MM-dd'
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match '^\*\*Status:\*\*\s*(⛔|⏭)') {
        # Blocked and Skipped are set by a person and carry a reason no step counter can express, so
        # a tick leaves them standing; the operator clears them deliberately. The index row below
        # applies the same exemption, because the two surfaces have to agree about it too.
        continue
    } elseif ($lines[$i] -match '^\*\*Status:\*\*\s*(⬜|🚧|✅)') {
        # Only the header form is touched: a step's own "**Status:** `[x] done`" line never starts
        # with one of these glyphs, so it cannot be hit by this branch.
        $lines[$i] = $phaseStatusText
    } elseif ($lines[$i] -match '^\*\*Started:\*\*\s*-\s*$' -and $doneAfter -gt 0) {
        $lines[$i] = "**Started:** $today"
    } elseif ($lines[$i] -match '^\*\*Completed:\*\*' -and $doneAfter -ge $totalSteps -and $totalSteps -gt 0) {
        $lines[$i] = "**Completed:** $today"
    } elseif ($lines[$i] -match '^\*\*Completed:\*\*' -and $doneAfter -lt $totalSteps) {
        # A step reopened after the phase was finished: the completion date is no longer true.
        $lines[$i] = '**Completed:** -'
    }
}

# Durable trace. The primary machine-readable record of an execution is this script's own
# invocation - a process audit reads transcripts, and the ticket, phase and step numbers are
# right there in the arguments. The Step Log is the in-repository copy, so someone reading the
# plan sees the same fact as someone reading the transcript. No other file is written.
if ($State -eq 'Done' -and $changed.Count -gt 0) {
    $body = New-Object System.Collections.Generic.List[string]
    $body.AddRange([string[]]$lines)
    $stamp = (Get-Date).ToString('yyyy-MM-dd')

    # Descending, so an insertion never invalidates a marker index still to be processed.
    foreach ($entry in ($changed | Sort-Object -Property line -Descending)) {
        # S1710: a re-tick that moved no marker gets no line unless the caller brought text of their
        # own. The default "state set to done" would record a transition that did not happen, while
        # an explicit -Log on an already-done step is exactly how a phase-boundary note is appended.
        if ($entry.before -eq $entry.after -and -not $Log -and -not $Note) { continue }

        $markerLine = $entry.line - 1
        # NOT $note: PowerShell variable names are case-insensitive, so a local $note IS the
        # $Note parameter. Writing one step's default text into it made the next step reuse it.
        $logNote = if ($Log) { $Log } elseif ($Note) { $Note } else { "state set to done for $Id step $($entry.step)" }
        # The stamp is prepended below, so a caller who pasted today's date into -Log would get
        # it twice. Strip a leading ISO date rather than making every caller remember.
        $logNote = $logNote -replace '^\s*\d{4}-\d{2}-\d{2}\s*-\s*', ''

        $existingLog = -1
        for ($i = $markerLine + 1; $i -lt $body.Count; $i++) {
            if ($body[$i] -match '^###\s' -or $body[$i] -match '^##\s' -or $body[$i] -match '^---\s*$') { break }
            if ($body[$i] -match '^\*\*Step Log:\*\*') { $existingLog = $i; break }
        }

        if ($existingLog -ge 0) {
            # A horizontal rule also starts with a dash, so "keep walking while the line is a
            # bullet" would step straight over the end of the step's own block.
            $insertAt = $existingLog + 1
            while ($insertAt -lt $body.Count -and
                   $body[$insertAt] -notmatch '^---\s*$' -and
                   ($body[$insertAt].Trim() -eq '' -or $body[$insertAt] -match '^\s*-\s')) {
                $insertAt++
            }
            while ($insertAt -gt $existingLog + 1 -and $body[$insertAt - 1].Trim() -eq '') { $insertAt-- }
            $body.Insert($insertAt, "- $stamp - $logNote")
        } else {
            $body.InsertRange($markerLine + 1, [string[]]@('', '**Step Log:**', '', "- $stamp - $logNote"))
        }
    }
    $lines = $body.ToArray()
}

try {
    # Preserve the file's own newline convention rather than imposing one: these files are
    # diffed constantly and a wholesale line-ending flip would swamp the real change.
    $raw = [System.IO.File]::ReadAllText($phaseFile.FullName)
    $newline = if ($raw -match "`r`n") { "`r`n" } else { "`n" }
    $trailingNewline = if ($raw.EndsWith("`n")) { $newline } else { '' }
    [System.IO.File]::WriteAllText($phaseFile.FullName, ($lines -join $newline) + $trailingNewline)
} catch {
    Write-Error "plan-tick: could not write $($phaseFile.Name): $($_.Exception.Message)" -ErrorAction Continue
    exit 1
}

$today = (Get-Date).ToString('yyyy-MM-dd')
$indexUpdated = $false

if ($indexRowNumber -ge 0) {
    # Recompute, never increment. An index that already drifted by a hand edit must end up
    # equal to what the phase file says, not equal to itself plus one.
    $cells = $indexLines[$indexRowNumber] -split '\|'
    $cells[5] = " $doneAfter/$totalSteps "
    if ($cells[4] -notmatch '⛔|⏭') {
        # The same computed label the phase header just took, exempting only the two operator-set
        # states. Assigned rather than compared, so the row moves down as readily as up.
        $cells[4] = " $phaseStatusLabel "
    }
    $indexLines[$indexRowNumber] = ($cells -join '|')

    $phaseRows = 0
    $phaseRowsDone = 0
    foreach ($line in $indexLines) {
        $rowCells = $line -split '\|'
        if ($rowCells.Count -lt 7) { continue }
        if ($rowCells[1].Trim() -notmatch '^\d{1,2}$') { continue }
        $phaseRows++
        if ($rowCells[4] -match '✅') { $phaseRowsDone++ }
    }

    for ($i = 0; $i -lt $indexLines.Length; $i++) {
        if ($indexLines[$i] -match '^\*\*Phases:\*\*') {
            $indexLines[$i] = "**Phases:** $phaseRowsDone / $phaseRows done"
        } elseif ($indexLines[$i] -match '^\*\*Last updated:\*\*') {
            # Read mechanically by the drift check's tactical-index freshness proof. A batch
            # writer that stopped maintaining it would fix cost and break a gate.
            $indexLines[$i] = "**Last updated:** $today"
        }
    }

    try {
        $indexRaw = [System.IO.File]::ReadAllText($indexFile)
        $indexNewline = if ($indexRaw -match "`r`n") { "`r`n" } else { "`n" }
        $indexTail = if ($indexRaw.EndsWith("`n")) { $indexNewline } else { '' }
        [System.IO.File]::WriteAllText($indexFile, ($indexLines -join $indexNewline) + $indexTail)
        $indexUpdated = $true
    } catch {
        Write-Error "plan-tick: phase file written but INDEX.md could not be updated: $($_.Exception.Message)" -ErrorAction Continue
        exit 1
    }
}

$result = [ordered]@{
    id           = $Id
    phase        = $phaseNumber
    file         = $phaseFile.Name
    state        = $State
    steps        = @($changed | ForEach-Object { $_.step })
    changed      = $changed.Count
    doneAfter    = $doneAfter
    totalSteps   = $totalSteps
    indexUpdated = $indexUpdated
}

if ($Json) {
    [PSCustomObject]$result | ConvertTo-Json -Compress -Depth 4
} else {
    Write-Host "plan-tick $Id phase $phaseNumber -> $State" -ForegroundColor Cyan
    foreach ($entry in $changed) {
        Write-Host ("  step {0,-8} line {1,-5} {2}" -f $entry.step, $entry.line, $entry.after) -ForegroundColor DarkGray
    }
}

exit 0
