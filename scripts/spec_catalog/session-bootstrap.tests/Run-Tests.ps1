<#
.SYNOPSIS
    Regression suite for scripts/spec_catalog/session-bootstrap.ps1 (S1596).

.DESCRIPTION
    The value of a bootstrap package is that a caller reading one payload learns what happened in
    every block. The failure that would destroy that value is silent: one child breaks, the package
    still exits 0 or blames the wrong block, and the driver picks a ticket on incomplete data.
    So the suite asserts the unhappy path as carefully as the happy one - case C checks that the
    SIBLING blocks are still ok, not merely that the exit code went non-zero.

    Isolation, following the S1534 rule the preview suite established:
      * The catalog journals are SNAPSHOT into a sandbox directory and the CLI is pointed at the
        copy via $env:FMS_SPEC_CATALOG_DIR. Reads stay real, no write can reach the live journal.
      * No id is ever taken from next-id.ps1 - this suite inserts no spec at all.
      * The session identity is overridden to a fixed probe value, so the per-session round-state
        file the package writes is the probe's own file and never the caller's. It is deleted in
        the finally block, which also asserts that no lease was left behind under that identity.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
    2 - the harness itself could not run (missing script, sandbox could not be built).
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$bootstrap = Join-Path $root 'scripts\spec_catalog\session-bootstrap.ps1'
if (-not (Test-Path -LiteralPath $bootstrap)) {
    Write-Error "session-bootstrap.tests: script under test not found at $bootstrap" -ErrorAction Continue
    exit 2
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else {
    'pwsh'
}

$probeSession = 's1596-bootstrap-tests'
$sandbox = Join-Path $root 'temp\S1596\bootstrap-tests-sandbox'
$sessionStateFile = Join-Path $root "temp\spec-next-session.$probeSession.json"
$leaseDir = Join-Path $root 'temp\SPEC-TICKET.LEASES'

$savedCatalogDir = $env:FMS_SPEC_CATALOG_DIR
$savedSessionId = $env:CLAUDE_CODE_SESSION_ID

$failures = New-Object System.Collections.Generic.List[string]
$passed = 0

function Assert-That {
    param(
        [Parameter(Mandatory)][string]$Case,
        [Parameter(Mandatory)][string]$What,
        [Parameter(Mandatory)][bool]$Condition,
        [string]$Detail = ''
    )
    if ($Condition) {
        Write-Host "  ok   $Case - $What" -ForegroundColor DarkGray
    } else {
        $script:failures.Add("$Case - $What$(if ($Detail) { " ($Detail)" })")
        Write-Host "  FAIL $Case - $What $Detail" -ForegroundColor Red
    }
}

function Invoke-Bootstrap {
    param([string[]]$Arguments = @())
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $captured = & $pwshExe -NoProfile -File $bootstrap @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    $text = ($captured | Out-String)
    $json = $null
    foreach ($line in ($text -split "`r?`n")) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith('{')) {
            try { $json = $trimmed | ConvertFrom-Json; break } catch { continue }
        }
    }
    return [PSCustomObject]@{ exitCode = $code; json = $json; text = $text.Trim() }
}

try {
    # ---- sandbox -------------------------------------------------------------
    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force }
    New-Item -ItemType Directory -Path $sandbox -Force | Out-Null
    foreach ($journal in @('spec-catalog.jsonl', 'spec-catalog-archive.jsonl', 'spec-catalog-burned-ids.jsonl')) {
        $source = Join-Path $root "PLAN\$journal"
        if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $sandbox $journal) -Force }
    }
    $env:FMS_SPEC_CATALOG_DIR = $sandbox
    $env:CLAUDE_CODE_SESSION_ID = $probeSession

    # ---- case A: -SkipDevice -------------------------------------------------
    Write-Host 'case A - device block skipped on demand' -ForegroundColor Cyan
    $a = Invoke-Bootstrap -Arguments @('-SkipDevice', '-Format', 'json')
    Assert-That -Case 'A' -What 'payload parsed' -Condition ($null -ne $a.json) -Detail $a.text
    if ($a.json) {
        Assert-That -Case 'A' -What 'device skipped' -Condition ($a.json.device.status -eq 'skipped') -Detail $a.json.device.status
        Assert-That -Case 'A' -What 'session ok' -Condition ($a.json.session.status -eq 'ok') -Detail $a.json.session.reason
        Assert-That -Case 'A' -What 'selection ok' -Condition ($a.json.selection.status -eq 'ok') -Detail $a.json.selection.reason
        Assert-That -Case 'A' -What 'exit 0' -Condition ($a.exitCode -eq 0) -Detail "exit=$($a.exitCode)"
        $passed++
    }

    # ---- case B: no claim without -Claim -------------------------------------
    Write-Host 'case B - nothing is claimed unless asked' -ForegroundColor Cyan
    $b = Invoke-Bootstrap -Arguments @('-SkipDevice', '-Format', 'json')
    if ($b.json) {
        Assert-That -Case 'B' -What 'lease skipped' -Condition ($b.json.lease.status -eq 'skipped') -Detail $b.json.lease.status
        Assert-That -Case 'B' -What 'claimed flag false' -Condition (-not $b.json.claimed)
        $probeLeases = @()
        if (Test-Path -LiteralPath $leaseDir) {
            $probeLeases = @(Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue |
                Where-Object {
                    try { ((Get-Content -LiteralPath $_.FullName -Raw) | ConvertFrom-Json).sessionId -eq $probeSession }
                    catch { $false }
                })
        }
        Assert-That -Case 'B' -What 'no lease file written' -Condition ($probeLeases.Count -eq 0) -Detail "$($probeLeases.Count) found"
        $passed++
    }

    # ---- case C: one child fails, siblings survive ---------------------------
    Write-Host 'case C - a failing child fails only its own block' -ForegroundColor Cyan
    # The failure is injected through a real component, not a fake: _lib.ps1 refuses a catalog
    # directory that does not exist, so the selection child dies on its own guard. The session
    # child does not dot-source _lib.ps1, which is exactly why it must survive this run - that
    # asymmetry is the whole point of the case.
    #
    # -Resume was tried first and rejected: the session component can adopt a legacy or sibling
    # state file, so "no state file on disk" is not a reliable failure.
    $env:FMS_SPEC_CATALOG_DIR = Join-Path $sandbox 'no-such-catalog-dir'
    $c = Invoke-Bootstrap -Arguments @('-SkipDevice', '-Format', 'json')
    $env:FMS_SPEC_CATALOG_DIR = $sandbox
    Assert-That -Case 'C' -What 'payload parsed' -Condition ($null -ne $c.json) -Detail $c.text
    if ($c.json) {
        Assert-That -Case 'C' -What 'selection block failed' -Condition ($c.json.selection.status -eq 'failed') -Detail $c.json.selection.status
        Assert-That -Case 'C' -What "child's own exit code kept" -Condition ($c.json.selection.exitCode -ne 0) -Detail "exitCode=$($c.json.selection.exitCode)"
        Assert-That -Case 'C' -What "child's own reason kept" -Condition ($c.json.selection.reason -match 'FMS_SPEC_CATALOG_DIR') -Detail $c.json.selection.reason
        Assert-That -Case 'C' -What 'session sibling still ok' -Condition ($c.json.session.status -eq 'ok') -Detail $c.json.session.status
        Assert-That -Case 'C' -What 'device sibling still skipped' -Condition ($c.json.device.status -eq 'skipped') -Detail $c.json.device.status
        Assert-That -Case 'C' -What 'failedBlocks names selection' -Condition ($c.json.failedBlocks -contains 'selection') -Detail ($c.json.failedBlocks -join ',')
        Assert-That -Case 'C' -What 'package exit 1' -Condition ($c.exitCode -eq 1) -Detail "exit=$($c.exitCode)"
        $passed++
    }

    # ---- case D: every block carries the same three fields -------------------
    Write-Host 'case D - block shape is uniform' -ForegroundColor Cyan
    $d = Invoke-Bootstrap -Arguments @('-SkipDevice', '-Format', 'json')
    if ($d.json) {
        $shapeOk = $true
        foreach ($name in @('session', 'device', 'selection', 'lease')) {
            $block = $d.json.$name
            $names = @($block.PSObject.Properties.Name)
            foreach ($field in @('status', 'exitCode', 'reason', 'payload')) {
                if ($names -notcontains $field) { $shapeOk = $false }
            }
        }
        Assert-That -Case 'D' -What 'all four blocks carry status/exitCode/reason/payload' -Condition $shapeOk
        $passed++
    }
} finally {
    $env:FMS_SPEC_CATALOG_DIR = $savedCatalogDir
    $env:CLAUDE_CODE_SESSION_ID = $savedSessionId

    if (Test-Path -LiteralPath $sandbox) { Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $sessionStateFile) { Remove-Item -LiteralPath $sessionStateFile -Force -ErrorAction SilentlyContinue }

    # The teardown asserts against the REAL surfaces, because a leak that goes unmeasured is how
    # the preview suite silently burned 21 spec ids before S1534.
    $leaked = @()
    if (Test-Path -LiteralPath $leaseDir) {
        $leaked = @(Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -ErrorAction SilentlyContinue |
            Where-Object {
                try { ((Get-Content -LiteralPath $_.FullName -Raw) | ConvertFrom-Json).sessionId -eq $probeSession }
                catch { $false }
            })
    }
    foreach ($stray in $leaked) {
        Remove-Item -LiteralPath $stray.FullName -Force -ErrorAction SilentlyContinue
        $failures.Add("teardown - probe lease leaked into the live store: $($stray.Name)")
    }
    if (Test-Path -LiteralPath $sessionStateFile) {
        $failures.Add('teardown - probe session state file survived deletion')
    }
}

Write-Host ''
if ($failures.Count -gt 0) {
    foreach ($failure in $failures) { Write-Host "FAIL $failure" -ForegroundColor Red }
    Write-Error "session-bootstrap.tests: $($failures.Count) assertion(s) failed across $passed case(s)." -ErrorAction Continue
    exit 1
}

Write-Host "session-bootstrap.tests: PASS - $passed cases passed (A skip-device, B no-claim, C partial failure, D block shape)." -ForegroundColor Green
exit 0
