#requires -Version 7.0
<#
.SYNOPSIS
    Runs the recorded cases in guard-catalog-before-kt-search.cases.json through the hook (S1344).

.DESCRIPTION
    Pipes each recorded PreToolUse payload to .claude/hooks/guard-catalog-before-kt-search.ps1 and
    compares the hook's exit code with the case's expectedExit. The hook refuses tool calls for every
    future session in this repository, so its refusal surface is proved against recorded inputs
    rather than re-derived by hand each time someone touches it.

    Each case declares the marker state it runs under, because temp/catalog-touch.marker is the pass
    condition. The runner saves the marker before the run and restores it afterwards, so proving the
    hook never changes the state of the session that proves it.

    Exit codes:
      0 - every case returned its expected exit code.
      1 - at least one mismatch.
      2 - the runner cannot run: hook or case file missing, or the case file is malformed.
#>

param(
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Hook = Join-Path $Root 'hooks\guard-catalog-before-kt-search.ps1'
$CaseFile = Join-Path $PSScriptRoot 'guard-catalog-before-kt-search.cases.json'
$RepoRoot = Split-Path -Parent $Root
$Marker = Join-Path $RepoRoot 'temp\catalog-touch.marker'

if (-not (Test-Path -LiteralPath $Hook)) {
    Write-Error "run-guard-catalog-cases: hook not found at $Hook" -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $CaseFile)) {
    Write-Error "run-guard-catalog-cases: case file not found at $CaseFile" -ErrorAction Continue
    exit 2
}

try {
    $cases = @((Get-Content -LiteralPath $CaseFile -Raw -Encoding UTF8 | ConvertFrom-Json).cases)
}
catch {
    Write-Error "run-guard-catalog-cases: case file is not valid JSON - $_" -ErrorAction Continue
    exit 2
}

if ($cases.Count -eq 0) {
    Write-Error "run-guard-catalog-cases: case file holds no cases" -ErrorAction Continue
    exit 2
}

$savedMarker = if (Test-Path -LiteralPath $Marker) { Get-Content -LiteralPath $Marker -Raw } else { $null }
$mismatches = 0

# The hook records every refusal as pilot evidence for S1344 phase 03. Synthetic cases are not real
# tasks, so they are redirected to a scratch file instead of dev/sector-gate-pilot.jsonl - otherwise
# re-proving the hook would inflate the very count the pilot verdict is computed from.
$savedPilotLog = $env:FMS_GUARD_PILOT_LOG
$env:FMS_GUARD_PILOT_LOG = Join-Path $RepoRoot 'temp\S1344\case-run-refusals.jsonl'

try {
    foreach ($case in $cases) {
        if ($case.marker) {
            $dir = Split-Path -Parent $Marker
            if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
            Set-Content -LiteralPath $Marker -Value 'test-case marker' -Encoding UTF8
        }
        elseif (Test-Path -LiteralPath $Marker) {
            Remove-Item -LiteralPath $Marker -Force
        }

        $json = $case.payload | ConvertTo-Json -Depth 10 -Compress
        $json | & pwsh -NoProfile -File $Hook 2>$null | Out-Null
        $actual = $LASTEXITCODE

        $ok = ($actual -eq $case.expectedExit)
        if (-not $ok) { $mismatches++ }
        if (-not $Quiet -or -not $ok) {
            $verdict = if ($ok) { 'PASS' } else { 'FAIL' }
            Write-Host ("{0} {1,-46} expected: {2} | actual: {3}" -f $verdict, $case.name, $case.expectedExit, $actual)
        }
    }
}
finally {
    $env:FMS_GUARD_PILOT_LOG = $savedPilotLog
    if ($null -ne $savedMarker) {
        Set-Content -LiteralPath $Marker -Value $savedMarker -NoNewline -Encoding UTF8
    }
    elseif (Test-Path -LiteralPath $Marker) {
        Remove-Item -LiteralPath $Marker -Force
    }
}

if ($mismatches -gt 0) {
    Write-Error "run-guard-catalog-cases: $mismatches of $($cases.Count) case(s) returned an unexpected exit code" -ErrorAction Continue
    exit 1
}

Write-Host "run-guard-catalog-cases: expected: 0 mismatches | actual: 0 - $($cases.Count) case(s) PASS"
exit 0
