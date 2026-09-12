#requires -Version 7.0
<#
.SYNOPSIS
    S2760: compares stored latency records route against route, on one control task at a time.

.DESCRIPTION
    Groups the records written by record-run.ps1 by controlTaskId, then by routeId, and reports
    each route's stage durations, completion outcomes, required-validation results and rework
    counts as separate columns. They are never merged into one score: a single number would let a
    faster route hide a failed check inside it, which is the exact substitution this ticket exists
    to prevent.

    A group reaches the verdict 'insufficient-evidence' - and no winner is named - when any of the
    following holds. Each is a statement that the experiment was not run, not a tool failure:

      - the compared routes were judged against different acceptanceCriteria sets, so the two runs
        measure two different tasks;
      - a route has no run whose qualityVerdict is 'accepted', so its duration describes an
        unfinished result;
      - a required stage is unavailable on one route and measured on the other, so their totals are
        not decomposed the same way;
      - fewer than two routes carry records for the control task.

Exit codes: 0 every group reached a verdict; 1 a group carries unequal acceptance evidence;
2 there is not enough data to compare; 3 the record directory could not be read.
#>
[CmdletBinding()]
param(
    [string]$Path = 'temp/S2760/records',

    [string]$ControlTask,

    [switch]$Json
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Write-Fail([string]$message) {
    Write-Error $message -ErrorAction Continue
}

$recordDir = $Path
if (-not [System.IO.Path]::IsPathRooted($recordDir)) {
    $recordDir = Join-Path $repoRoot $Path
}

if (-not (Test-Path -LiteralPath $recordDir)) {
    Write-Fail "compare-routes: record directory not found: $recordDir"
    exit 3
}

$files = @(Get-ChildItem -LiteralPath $recordDir -Filter '*.json' -File -ErrorAction SilentlyContinue)
if ($files.Count -eq 0) {
    Write-Fail "compare-routes: no records under $recordDir - run record-run.ps1 first"
    exit 2
}

$records = @()
foreach ($file in $files) {
    try {
        $records += (Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json -Depth 20)
    } catch {
        Write-Fail "compare-routes: cannot parse $($file.Name): $($_.Exception.Message)"
        exit 3
    }
}

if ($ControlTask) {
    $records = @($records | Where-Object { $_.controlTaskId -eq $ControlTask })
    if ($records.Count -eq 0) {
        Write-Fail "compare-routes: no records for control task '$ControlTask'"
        exit 2
    }
}

$stages = @('firstResponseMs', 'modelDurationMs', 'toolDurationMs', 'queueWaitMs', 'totalCompletionMs')

function Get-StageCell($record, [string]$stage) {
    $value = $record.$stage
    if ($null -eq $value) { return [pscustomobject]@{ available = $false; valueMs = $null; reason = 'absent' } }
    if ($null -ne $value.valueMs) { return [pscustomobject]@{ available = $true; valueMs = [double]$value.valueMs; reason = $null } }
    return [pscustomobject]@{ available = $false; valueMs = $null; reason = [string]$value.unavailableReason }
}

$groups = @()
$unequalEvidence = $false
$insufficient = $false

foreach ($taskGroup in ($records | Group-Object -Property controlTaskId)) {
    $routes = @()
    foreach ($routeGroup in ($taskGroup.Group | Group-Object -Property routeId)) {
        $runs = @($routeGroup.Group)
        $stageSummary = [ordered]@{}
        foreach ($stage in $stages) {
            $cells = @($runs | ForEach-Object { Get-StageCell $_ $stage })
            $measured = @($cells | Where-Object { $_.available })
            if ($measured.Count -gt 0) {
                $values = @($measured | ForEach-Object { $_.valueMs })
                $stageSummary[$stage] = [pscustomobject]@{
                    available = $true
                    runs      = $measured.Count
                    minMs     = ($values | Measure-Object -Minimum).Minimum
                    medianMs  = (@($values | Sort-Object)[[int][math]::Floor(($values.Count - 1) / 2)])
                    maxMs     = ($values | Measure-Object -Maximum).Maximum
                }
            } else {
                $stageSummary[$stage] = [pscustomobject]@{
                    available = $false
                    runs      = 0
                    reason    = (@($cells | ForEach-Object { $_.reason } | Where-Object { $_ }) | Select-Object -First 1)
                }
            }
        }

        $validation = @()
        foreach ($run in $runs) {
            foreach ($item in @($run.validationEvidence)) {
                $validation += [pscustomobject]@{ check = $item.check; outcome = $item.outcome }
            }
        }

        $routes += [pscustomobject]@{
            routeId            = $routeGroup.Name
            runs               = $runs.Count
            stages             = [pscustomobject]$stageSummary
            outcomes           = @($runs | ForEach-Object { $_.qualityVerdict })
            acceptedRuns       = @($runs | Where-Object { $_.qualityVerdict -eq 'accepted' }).Count
            reworkTotal        = (@($runs | ForEach-Object { if ($null -ne $_.reworkCount) { [int]$_.reworkCount } else { 0 } }) | Measure-Object -Sum).Sum
            validationOutcomes = $validation
            failedChecks       = @($validation | Where-Object { $_.outcome -notmatch '^(0|PASS|pass)$' }).Count
            acceptanceKey      = (@($runs[0].acceptanceCriteria | Sort-Object) -join '||')
        }
    }

    $reasons = @()
    if ($routes.Count -lt 2) {
        $reasons += "only $($routes.Count) route(s) recorded for this control task"
    }
    if (@($routes | Select-Object -ExpandProperty acceptanceKey -Unique).Count -gt 1) {
        $reasons += 'compared routes were judged against different acceptance criteria'
        $unequalEvidence = $true
    }
    foreach ($route in $routes) {
        if ($route.acceptedRuns -eq 0) {
            $reasons += "route '$($route.routeId)' has no accepted run"
        }
        if ($route.failedChecks -gt 0) {
            $reasons += "route '$($route.routeId)' has $($route.failedChecks) failed mandatory check(s)"
        }
    }
    foreach ($stage in @('firstResponseMs', 'toolDurationMs', 'queueWaitMs', 'totalCompletionMs')) {
        $availability = @($routes | ForEach-Object { $_.stages.$stage.available } | Select-Object -Unique)
        if ($availability.Count -gt 1) {
            $reasons += "stage $stage is measured on one route and unavailable on another"
        }
    }

    $verdict = 'comparable'
    if ($reasons.Count -gt 0) {
        $verdict = 'insufficient-evidence'
        $insufficient = $true
    }

    $groups += [pscustomobject]@{
        controlTaskId = $taskGroup.Name
        taskClass     = $taskGroup.Group[0].taskClass
        verdict       = $verdict
        reasons       = $reasons
        routes        = $routes
    }
}

$report = [pscustomobject]@{
    schemaVersion = 1
    recordDir     = $recordDir
    groups        = $groups
}

if ($Json) {
    $report | ConvertTo-Json -Depth 12
} else {
    foreach ($group in $groups) {
        Write-Host "control task '$($group.controlTaskId)' [$($group.taskClass)] -> $($group.verdict)"
        foreach ($route in $group.routes) {
            $total = $route.stages.totalCompletionMs
            $totalText = if ($total.available) { "total median $([int]$total.medianMs) ms" } else { "total unavailable ($($total.reason))" }
            Write-Host "  route '$($route.routeId)': $($route.runs) run(s), $($route.acceptedRuns) accepted, $($route.failedChecks) failed check(s), rework $($route.reworkTotal), $totalText"
        }
        foreach ($reason in $group.reasons) { Write-Host "  reason: $reason" }
    }
}

if ($unequalEvidence) {
    Write-Fail 'compare-routes: at least one group carries unequal acceptance evidence and cannot be judged'
    exit 1
}
if ($insufficient) {
    Write-Fail 'compare-routes: not enough evidence to name a faster route'
    exit 2
}
exit 0
