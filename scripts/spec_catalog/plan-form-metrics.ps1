# plan-form-metrics.ps1 - Pilot measurement recorder for S1343 (robo-english tactical form)
#
# Problem this solves:
#   S1343 asks whether a compressed "robo-english" tactical step form is worth adopting.
#   Strategic §1 and §7 both expect the effect to sit inside noise, and that is exactly the
#   regime where eyeballing a table returns the answer the reader already expected.
#   The verdict rule of strategic §6.3 therefore lives here, computed from recorded rows,
#   so the decision step is a lookup rather than a judgement call.
#
# Storage: dev/spec-form-pilot.jsonl (newline-delimited JSON, one row per record)
# Row schema:
#   { "kind": "selection|measurement|verdict", "ticket": "Sxxxx", "form": "current|robo",
#     "toolCalls": <int>, "questions": <int>, "divergences": <int>, "note": "<free text>",
#     "recordedAt": "<ISO-8601>" }
#
# Verdict rule (strategic S1343 §6.3, owner ruling 2026-08-02):
#   A ticket counts as IMPROVED when, comparing its robo row against its current row,
#     (toolCalls decreased OR questions decreased) AND divergences did not increase.
#   Result is 'adopt' when at least 2 of 3 measured tickets improved, otherwise 'reject'.
#
# Usage:
#   plan-form-metrics.ps1 -Action add -Kind selection   -Ticket S0123 [-Note "3 phases / 11 steps"]
#   plan-form-metrics.ps1 -Action add -Kind measurement -Ticket S0123 -Form robo `
#                         -ToolCalls 14 -Questions 2 -Divergences 1 [-Note "..."]
#   plan-form-metrics.ps1 -Action add -Kind verdict     -Note "gate"  [-Ticket -]
#   plan-form-metrics.ps1 -Action list                              # prints every recorded row
#   plan-form-metrics.ps1 -Action summary                           # computes adopt/reject
#
# Exit codes (S1070):
#   0 - action done.
#   2 - bad arguments, or the data file exists but is unreadable/malformed.
#   3 - -Action summary ran with fewer than two tickets measured in both forms.
#       Distinct from 2 on purpose: "not enough data yet" is not "the file is broken".

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('add', 'list', 'summary')]
    [string]$Action,

    [ValidateSet('selection', 'measurement', 'verdict')]
    [string]$Kind,

    [string]$Ticket = '',

    [ValidateSet('current', 'robo')]
    [string]$Form = '',

    [Nullable[int]]$ToolCalls,
    [Nullable[int]]$Questions,
    [Nullable[int]]$Divergences,

    [string]$Note = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dataPath = Join-Path $root 'dev\spec-form-pilot.jsonl'

function Read-Rows {
    if (-not (Test-Path $dataPath)) { return @() }
    $rows = @()
    $lineNo = 0
    foreach ($line in (Get-Content -Path $dataPath)) {
        $lineNo++
        if (-not $line -or $line.Trim() -eq '') { continue }
        try {
            $rows += ($line | ConvertFrom-Json)
        }
        catch {
            Write-Error "Malformed JSON at $dataPath line ${lineNo}: $line" -ErrorAction Continue
            exit 2
        }
    }
    return $rows
}

function Add-Row {
    $record = [ordered]@{
        kind        = $Kind
        ticket      = $Ticket
        form        = $Form
        toolCalls   = $ToolCalls
        questions   = $Questions
        divergences = $Divergences
        note        = $Note
        recordedAt  = (Get-Date).ToString('s')
    }
    $json = ([PSCustomObject]$record | ConvertTo-Json -Depth 4 -Compress)
    $parent = Split-Path -Parent $dataPath
    if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent | Out-Null }
    Add-Content -Path $dataPath -Value $json -Encoding utf8NoBOM
    Write-Output "recorded: $json"
}

# Pair every ticket's robo row against its current row and apply the §6.3 rule.
function Get-TicketComparisons([object[]]$rows) {
    $measurements = @($rows | Where-Object { $_.kind -eq 'measurement' })
    $comparisons = @()
    foreach ($id in ($measurements.ticket | Sort-Object -Unique)) {
        $current = $measurements | Where-Object { $_.ticket -eq $id -and $_.form -eq 'current' } | Select-Object -First 1
        $robo = $measurements | Where-Object { $_.ticket -eq $id -and $_.form -eq 'robo' } | Select-Object -First 1
        if (-not $current -or -not $robo) { continue }

        $cheaper = ($robo.toolCalls -lt $current.toolCalls) -or ($robo.questions -lt $current.questions)
        $noWorse = ($robo.divergences -le $current.divergences)
        $comparisons += [PSCustomObject]@{
            Ticket           = $id
            ToolCallsCurrent = $current.toolCalls
            ToolCallsRobo    = $robo.toolCalls
            QuestionsCurrent = $current.questions
            QuestionsRobo    = $robo.questions
            DivergesCurrent  = $current.divergences
            DivergesRobo     = $robo.divergences
            Improved         = ($cheaper -and $noWorse)
        }
    }
    return $comparisons
}

if ($Action -eq 'add') {
    if (-not $Kind) {
        Write-Error "-Kind is required for action 'add'" -ErrorAction Continue
        exit 2
    }
    if ($Kind -eq 'measurement') {
        if ($Ticket -notmatch '^S\d{4}$') {
            Write-Error "-Ticket must match S#### for a measurement row (got '$Ticket')" -ErrorAction Continue
            exit 2
        }
        if (-not $Form) {
            Write-Error "-Form (current|robo) is required for a measurement row" -ErrorAction Continue
            exit 2
        }
        if ($null -eq $ToolCalls -or $null -eq $Questions -or $null -eq $Divergences) {
            Write-Error "-ToolCalls, -Questions and -Divergences are all required for a measurement row" -ErrorAction Continue
            exit 2
        }
    }
    if ($Kind -eq 'selection' -and $Ticket -notmatch '^S\d{4}$') {
        Write-Error "-Ticket must match S#### for a selection row (got '$Ticket')" -ErrorAction Continue
        exit 2
    }
    if ($Kind -eq 'verdict' -and -not $Note) {
        Write-Error "-Note is required for a verdict row (record what was decided)" -ErrorAction Continue
        exit 2
    }
    Add-Row
    exit 0
}

$rows = Read-Rows

if ($Action -eq 'list') {
    if ($rows.Count -eq 0) {
        Write-Output "no rows recorded yet ($dataPath)"
        exit 0
    }
    foreach ($row in $rows) {
        Write-Output ($row | ConvertTo-Json -Depth 4 -Compress)
    }
    Write-Output "total rows: $($rows.Count)"
    exit 0
}

# -Action summary
$comparisons = Get-TicketComparisons $rows
if ($comparisons.Count -lt 2) {
    Write-Error "summary needs at least two tickets measured in BOTH forms; found $($comparisons.Count)" -ErrorAction Continue
    exit 3
}

$comparisons | Format-Table -AutoSize | Out-String | Write-Output

$improved = @($comparisons | Where-Object { $_.Improved }).Count
$verdict = if ($improved -ge 2) { 'adopt' } else { 'reject' }

Write-Output "tickets improved: $improved / $($comparisons.Count) (threshold: 2)"
Write-Output $verdict
exit 0
