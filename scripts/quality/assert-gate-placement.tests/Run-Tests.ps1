<#
Run-Tests.ps1 - contract tests for assert-gate-placement.ps1 (S2870).

Every case builds a throwaway repository root under the system temp directory and runs the gate
against it, so no case reads the live registry or the live runners - a suite judging those would
pass or fail by whatever the host tree happens to carry this minute.

The decisive case is "per-ticket declared, wired into the fast batch only". That is the shape that
crashed the app (S2300) and deleted the owner's database (S2306), and in both incidents the gate
involved passed every by-hand run, so it is proved here on fixed input rather than by argument.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-gate-placement.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

function New-Fixture {
    param(
        [hashtable[]]$Records = @(),
        [string[]]$GateFiles = @(),
        [string[]]$InPostChange = @(),
        [string[]]$InFastGates = @(),
        [string[]]$InReleaseScope = @(),
        [switch]$OmitRegistry,
        [string]$RawRegistry
    )

    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("s2870-" + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($root)
    New-Item -ItemType Directory -Path (Join-Path $root 'scripts/quality/lib') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'scripts/release') -Force | Out-Null

    foreach ($g in $GateFiles) {
        Set-Content -LiteralPath (Join-Path $root "scripts/quality/$g") -Encoding utf8 -Value @(
            '#requires -Version 7.0'
            '# fixture gate'
            'exit 0'
        )
    }

    # Runners reference their gates from STRING literals, the same shape the real ones use.
    function Write-Runner([string]$Path, [string[]]$Names) {
        $lines = @('#requires -Version 7.0', '$gates = @(')
        foreach ($n in $Names) { $lines += "    '$n'" }
        $lines += ')'
        Set-Content -LiteralPath $Path -Encoding utf8 -Value $lines
    }

    Write-Runner (Join-Path $root 'scripts/post-change.ps1') $InPostChange
    Write-Runner (Join-Path $root 'scripts/quality/assert-fast-gates.ps1') $InFastGates
    Write-Runner (Join-Path $root 'scripts/quality/assert-release-scope-gates.ps1') $InReleaseScope
    Write-Runner (Join-Path $root 'scripts/quality/assert-prerelease-content-gates.ps1') @()
    Write-Runner (Join-Path $root 'scripts/release/standard-release-gate.ps1') @()

    $registryPath = Join-Path $root 'scripts/quality/gate-placement.jsonl'
    if ($PSBoundParameters.ContainsKey('RawRegistry')) {
        Set-Content -LiteralPath $registryPath -Value $RawRegistry -Encoding utf8
    }
    elseif (-not $OmitRegistry) {
        # The three aggregators match the assert-* glob, so they need records like any other file
        # there. Every fixture gets them, so a case's own records stay about the case.
        $all = @($Records) + @(
            (New-Record -Gate 'assert-fast-gates.ps1' -Scope 'runner' -Kind 'runner' -Reason 'aggregator')
            (New-Record -Gate 'assert-release-scope-gates.ps1' -Scope 'runner' -Kind 'runner' -Reason 'aggregator')
            (New-Record -Gate 'assert-prerelease-content-gates.ps1' -Scope 'runner' -Kind 'runner' -Reason 'aggregator')
        )
        $lines = foreach ($r in $all) { ([pscustomobject]$r | ConvertTo-Json -Compress -Depth 3) }
        Set-Content -LiteralPath $registryPath -Value $lines -Encoding utf8
    }

    return $root
}

function New-Record {
    param([string]$Gate, [string]$Scope, [string]$Kind = 'gate', [string]$Reason = 'fixture reason', [string]$Basis = 'seeded')
    return @{ gate = $Gate; kind = $Kind; scope = $Scope; decided = '2026-09-10'; ticket = 'S2870'; basis = $Basis; reason = $Reason }
}

function Invoke-Gate {
    param([string]$Root, [string[]]$ChangedFiles)
    $argv = @('-NoProfile', '-File', $Gate, '-Gate', '-Quiet', '-RepoRoot', $Root)
    if ($PSBoundParameters.ContainsKey('ChangedFiles')) { $argv += @('-ChangedFiles'); $argv += $ChangedFiles }
    $out = & pwsh @argv 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out }
}

function Assert-Case {
    param([string]$Name, [int]$Expected, [pscustomobject]$Result, [string]$MustContain)

    $ok = $Result.ExitCode -eq $Expected
    if ($ok -and $MustContain) { $ok = $Result.Output -match [regex]::Escape($MustContain) }

    if ($ok) {
        $script:passed++
        Write-Host ("  PASS  {0}" -f $Name) -ForegroundColor Green
    }
    else {
        $script:failed++
        Write-Host ("  FAIL  {0}" -f $Name) -ForegroundColor Red
        Write-Host ("        expected exit {0}, got {1}" -f $Expected, $Result.ExitCode) -ForegroundColor Red
        if ($MustContain) { Write-Host ("        expected output to contain: {0}" -f $MustContain) -ForegroundColor Red }
        Write-Host ($Result.Output -split "`n" | Select-Object -First 12 | ForEach-Object { "        $_" })
    }
}

Write-Host 'assert-gate-placement contract tests (S2870)'

# --- 1. green: registry agrees with the runners ------------------------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1', 'assert-beta.ps1') `
    -InPostChange @('assert-alpha.ps1') -InFastGates @('assert-beta.ps1') `
    -Records @((New-Record -Gate 'assert-alpha.ps1' -Scope 'per-ticket'),
               (New-Record -Gate 'assert-beta.ps1' -Scope 'fast-batch'))
Assert-Case -Name 'registry matching the wiring passes' -Expected 0 -Result (Invoke-Gate -Root $root)

# --- 2. THE decisive case: per-ticket declared, fast batch only ---------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1') `
    -InPostChange @() -InFastGates @('assert-alpha.ps1') `
    -Records @((New-Record -Gate 'assert-alpha.ps1' -Scope 'per-ticket'))
Assert-Case -Name 'per-ticket wired into fast batch only fails (S2300/S2306 shape)' -Expected 1 `
    -Result (Invoke-Gate -Root $root) -MustContain 'post-change.ps1 does not reference it'

# --- 3. a gate on disk with no record ------------------------------------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1', 'assert-orphan.ps1') `
    -InPostChange @('assert-alpha.ps1') `
    -Records @((New-Record -Gate 'assert-alpha.ps1' -Scope 'per-ticket'))
Assert-Case -Name 'gate with no registry record fails' -Expected 1 `
    -Result (Invoke-Gate -Root $root) -MustContain 'assert-orphan.ps1 has no registry record'

# --- 4. a record naming a file that is gone -----------------------------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1') `
    -InPostChange @('assert-alpha.ps1') `
    -Records @((New-Record -Gate 'assert-alpha.ps1' -Scope 'per-ticket'),
               (New-Record -Gate 'assert-vanished.ps1' -Scope 'hand-run'))
Assert-Case -Name 'record for an absent gate fails' -Expected 1 `
    -Result (Invoke-Gate -Root $root) -MustContain 'which is not on disk'

# --- 5. unknown scope vocabulary -----------------------------------------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1') `
    -InPostChange @('assert-alpha.ps1') `
    -Records @((New-Record -Gate 'assert-alpha.ps1' -Scope 'whenever'))
Assert-Case -Name 'unknown scope value fails' -Expected 1 `
    -Result (Invoke-Gate -Root $root) -MustContain "unknown scope 'whenever'"

# --- 6. a release-scope gate silently re-added to the closure ------------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1') `
    -InPostChange @('assert-alpha.ps1') -InReleaseScope @('assert-alpha.ps1') `
    -Records @((New-Record -Gate 'assert-alpha.ps1' -Scope 'release-scope' -Basis 'judged'))
Assert-Case -Name 'retired gate re-added to the closure fails' -Expected 1 `
    -Result (Invoke-Gate -Root $root) -MustContain "declared 'release-scope' but its wiring says 'per-ticket'"

# --- 7. empty reason ------------------------------------------------------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1') `
    -InPostChange @('assert-alpha.ps1') `
    -Records @((New-Record -Gate 'assert-alpha.ps1' -Scope 'per-ticket' -Reason ''))
Assert-Case -Name 'empty reason fails' -Expected 1 -Result (Invoke-Gate -Root $root) -MustContain 'empty reason'

# --- 8. missing registry is CANNOT VERIFY, not a finding -----------------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1') -InPostChange @('assert-alpha.ps1') -OmitRegistry
Assert-Case -Name 'absent registry exits 2' -Expected 2 -Result (Invoke-Gate -Root $root)

# --- 9. malformed JSON is CANNOT VERIFY ----------------------------------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1') -InPostChange @('assert-alpha.ps1') -RawRegistry '{ this is not json'
Assert-Case -Name 'malformed registry line exits 2' -Expected 2 -Result (Invoke-Gate -Root $root)

# --- 10. fixed-input contract: findings exist but the change touches nothing declared -------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1', 'assert-orphan.ps1') `
    -InPostChange @('assert-alpha.ps1') `
    -Records @((New-Record -Gate 'assert-alpha.ps1' -Scope 'per-ticket'))
Assert-Case -Name 'unrelated changed set is advisory, not fatal (S2824)' -Expected 3 `
    -Result (Invoke-Gate -Root $root -ChangedFiles @('app_v2/src/main/java/com/sza/Foo.kt'))

# --- 11. fixed-input contract: the change DOES touch a gate script --------------------------------
Assert-Case -Name 'changed set carrying a gate script is fatal' -Expected 1 `
    -Result (Invoke-Gate -Root $root -ChangedFiles @('scripts/quality/assert-orphan.ps1'))

# --- 12. a forwarder must name its umbrella dimension ---------------------------------------------
$root = New-Fixture -GateFiles @('assert-alpha.ps1') `
    -Records @((New-Record -Gate 'assert-alpha.ps1' -Scope 'hand-run' -Kind 'forwarder' -Reason 'thin wrapper'))
Assert-Case -Name 'forwarder with no dimension named fails' -Expected 1 `
    -Result (Invoke-Gate -Root $root) -MustContain 'names no umbrella dimension'

foreach ($f in $fixtures) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue }

Write-Host ''
Write-Host ("assert-gate-placement tests: {0} passed, {1} failed" -f $passed, $failed)
if ($failed -gt 0) { exit 1 }
exit 0
