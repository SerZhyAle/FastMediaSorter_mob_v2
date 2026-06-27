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

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-detekt.ps1
    pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Gate
    pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module wear -Gate
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,
    [switch]$Gate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gradlew = Join-Path $repoRoot 'gradlew.bat'

if (-not (Test-Path $gradlew)) {
    Write-Host "assert-detekt: FAIL - gradlew.bat not found at $gradlew" -ForegroundColor Red
    exit 2
}

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
}

if ($exit -eq 0) {
    Write-Host "assert-detekt: PASS [$scopeLabel] (no new findings; baselines hold)." -ForegroundColor Green
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
