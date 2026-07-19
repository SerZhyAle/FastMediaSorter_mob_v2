#requires -Version 7.0
<#
.SYNOPSIS
    S0720 static-analysis gate: run detekt (+ ktlint formatting) over :app_v2 and :wear.

.DESCRIPTION
    detekt is configured per-subproject in the root build.gradle.kts as a SEPARATE
    static gate - it is NOT wired into assemble*, so it never changes the runtime
    artifact or slows a normal build. This wrapper just invokes the two detekt tasks
    (or one task when -Module is provided) and translates the gradle exit code into
    a PASS/FAIL verdict.

    Ratchet contract: each module has a committed baseline
    (config/detekt/baseline-<module>.xml) freezing every pre-existing finding. detekt
    only fails on findings NOT in the baseline, so this gate blocks NEW smells while
    leaving the historical debt untouched. To re-freeze after an intentional refactor:
        .\gradlew.bat :app_v2:detektBaseline :wear:detektBaseline

    Runs lexically (no type resolution) - fast, no full compile.

    Modes:
      (default)  Report the verdict. Exit 0 on PASS.
      -Gate      Same run; exit 1 if detekt reports any NEW finding.

    Scope:
      (default)       Run detekt for both modules (:app_v2 + :wear).
      -Module app_v2  Run detekt for :app_v2 only.
      -Module wear    Run detekt for :wear only.

    Exit codes (S1070 contract; S1077 added 2 to the diff-scoped path):
      0  PASS - no new findings, or none in -ChangedFiles, or non-gate mode.
      1  FAIL - -Gate and detekt reported a new finding (in -ChangedFiles when diff-scoped).
      2  Cannot verify - gradlew.bat missing, or -ChangedFiles given but a run module's
         detekt.xml is absent/unparseable so the failure cannot be narrowed. Never a PASS:
         "could not check" is a different fact from "checked and found nothing".

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-detekt.ps1
    pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module wear -Gate
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,
    [switch]$Gate,
    # S0826: diff-scoped mode. When set, a project-wide detekt failure is re-judged against
    # only these files (repo-relative or absolute paths) - PASS if none of the NEW findings
    # land in them. Lets post-change.ps1 close a change on an always-dirty tree without
    # failing on other tickets' in-flight findings. Release/CI omit it for a full project gate.
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gradlew = Join-Path $repoRoot 'gradlew.bat'

if (-not (Test-Path $gradlew)) {
    Write-Host "assert-detekt: FAIL - gradlew.bat not found at $gradlew" -ForegroundColor Red
    exit 2
}

. (Join-Path $repoRoot "scripts/quality/lib/detekt-report.ps1")

$lockReason = if ($PSBoundParameters.ContainsKey('Module')) { "assert-detekt.ps1 -Module $Module" } else { "assert-detekt.ps1 (app_v2 + wear)" }
. (Join-Path $repoRoot "scripts/utils/agent-lock.ps1")
Enter-BuildLockOrExit -Reason $lockReason

Push-Location $repoRoot
try {
    $tasks = if ($PSBoundParameters.ContainsKey('Module')) {
        @(":${Module}:detekt")
    }
    else {
        @(':app_v2:detekt', ':wear:detekt')
    }

    $scopeLabel = if ($PSBoundParameters.ContainsKey('Module')) {
        $Module
    }
    else {
        'app_v2 + wear'
    }

    $output = & $gradlew @tasks 2>&1
    $exit = $LASTEXITCODE
}
finally {
    Pop-Location
    Exit-AgentLock -Name Build
}

if ($exit -eq 0) {
    Write-Host "assert-detekt: PASS [$scopeLabel] (no new findings; baselines hold)." -ForegroundColor Green
    exit 0
}

# S0826: diff-scoped re-judgement. detekt failed project-wide; if the caller named the
# changed files, the failure is re-judged against only them - PASS unless a NEW finding
# actually lands in a changed file. Source the findings from the Checkstyle XML report
# (a declared task output, restored on cache hits) - NOT stdout, whose per-finding lines
# vanish on a build-cache hit and would yield a false PASS. detekt's baseline suppresses
# old findings everywhere, so any file in the report is a genuinely-new finding; a file
# outside the changed set is another ticket's in-flight WIP, not this change.
if ($ChangedFiles -and $ChangedFiles.Count -gt 0) {
    $reportModules = if ($PSBoundParameters.ContainsKey('Module')) { @($Module) } else { @('app_v2', 'wear') }
    $report = Get-DetektFindingFiles -RepoRoot $repoRoot -Modules $reportModules
    # S1077: fail closed. detekt has already FAILED to reach this point, so an unreadable report means
    # the narrowing cannot be done - and "could not check" must never be reported as "clean". This used
    # to `continue` past a missing report into the empty-list branch below, printing PASS on top of the
    # failure. Distinct exit code from a real finding: 2 = cannot verify, 1 = verified and found.
    if (-not $report.Ok) {
        $why = "assert-detekt: cannot narrow a project-wide detekt failure - $($report.Reason). " +
        'Re-run detekt so the report exists; refusing to report PASS without reading it.'
        Write-Error $why -ErrorAction Continue
        exit 2
    }
    $findingFiles = $report.Files
    $normChanged = $ChangedFiles |
        ForEach-Object { (([string]$_) -replace '\\', '/').Trim().Trim('.', '/').ToLower() } |
        Where-Object { $_ }
    $mine = $findingFiles | Where-Object {
        $ff = $_
        $matched = $false
        foreach ($cf in $normChanged) { if ($ff -like "*$cf*") { $matched = $true; break } }
        $matched
    }
    if (@($mine).Count -eq 0) {
        Write-Host "assert-detekt: PASS [scoped] - $(@($findingFiles).Count) file(s) with new findings project-wide, none among changed files." -ForegroundColor Green
        exit 0
    }
    Write-Host "assert-detekt: NEW findings in changed file(s):" -ForegroundColor Red
    @($mine) | Select-Object -Unique | ForEach-Object { Write-Host "  $_" }
    if ($Gate) {
        Write-Host "assert-detekt: FAIL [scoped] - fix the detekt finding(s) in your changed files." -ForegroundColor Red
        exit 1
    }
    exit 0
}

# Surface the detekt summary lines so the failing rule(s) are visible without
# re-running gradle. detekt prints lines containing "(file path):(line):(col):" plus a
# trailing finding-count summary.
$output | Where-Object {
    $_ -match 'detekt|finding|\.kt:\d+:\d+:' -or $_ -match 'FAILURE|What went wrong'
} | Select-Object -Last 30 | ForEach-Object { Write-Host $_ }

if ($Gate) {
    Write-Host "assert-detekt: FAIL [$scopeLabel] - detekt found NEW issues above baseline. Fix them, or (if intentional) re-freeze via '.\gradlew.bat :app_v2:detektBaseline :wear:detektBaseline'." -ForegroundColor Red
    exit 1
}

Write-Host "assert-detekt: [$scopeLabel] detekt reported new findings (exit $exit)." -ForegroundColor Yellow
exit 0
