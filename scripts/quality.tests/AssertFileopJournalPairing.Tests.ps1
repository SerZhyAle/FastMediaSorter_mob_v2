#requires -Version 7.0
<#
.SYNOPSIS
    S3371: contract suite for scripts/quality/assert-fileop-journal-pairing.ps1.

.DESCRIPTION
    The gate refuses a destructive file operation somebody has already decided to write, so its
    refusals are demonstrated here rather than asserted. Every case builds a fixture tree with the
    real package layout, a fixture allowlist and a fixture baseline, and runs the gate against it
    with -RepoRoot - the whole verdict path executes, including the refusal text a developer will
    read at 2 a.m.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every test passed.
      1  at least one test failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:pass = 0
$script:fail = 0

function Test-Case([string]$Name, [scriptblock]$Body) {
    try {
        & $Body
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    }
    catch {
        $script:fail++
        Write-Host "  FAIL  $Name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Assert-Equal($Expected, $Actual, [string]$What) {
    if ($Expected -ne $Actual) { throw "$What - expected: $Expected | actual: $Actual" }
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gate = Join-Path $repoRoot 'scripts/quality/assert-fileop-journal-pairing.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("fileop-journal-fixture-{0}" -f $PID)
$treeRelative = 'app_v2/src/main/java/com/sza/fastmediasorter/data/transfer'
$treeDir = Join-Path $fixtureRoot $treeRelative
$allowlist = Join-Path $fixtureRoot 'allowlist.txt'
$baseline = Join-Path $fixtureRoot 'baseline.txt'

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $treeDir -Force | Out-Null
    Set-Content -LiteralPath $allowlist -Value @('# fixture allowlist') -Encoding UTF8
    Set-Content -LiteralPath $baseline -Value @('# fixture baseline') -Encoding UTF8
}

function Set-Strategy([string]$FileName, [string[]]$Body) {
    Set-Content -LiteralPath (Join-Path $treeDir $FileName) -Value $Body -Encoding UTF8
}

function Set-Allowlist([string[]]$Rows) {
    Set-Content -LiteralPath $allowlist -Value (@('# fixture allowlist') + $Rows) -Encoding UTF8
}

function Set-Baseline([string[]]$Tokens) {
    Set-Content -LiteralPath $baseline -Value (@('# fixture baseline') + $Tokens) -Encoding UTF8
}

function Invoke-Pairing {
    param([switch]$NoRoot)
    $argv = @('-NoProfile', '-NonInteractive', '-File', $gate, '-Gate', '-AllowlistPath', $allowlist, '-BaselinePath', $baseline)
    if (-not $NoRoot) { $argv += @('-RepoRoot', $fixtureRoot) }
    else { $argv += @('-RepoRoot', (Join-Path $fixtureRoot 'nowhere')) }
    $output = & $pwshExe @argv 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

# The four cases the phase names come first, in its order; the rest guard the opt-out surfaces.
try {
    Test-Case 'a new destructive operation with no journal reference is refused, by name' {
        Reset-Fixture
        Set-Strategy 'WebdavOperationStrategy.kt' @(
            'package com.sza.fastmediasorter.data.transfer',
            '',
            'class WebdavOperationStrategy {',
            '    suspend fun deleteFile(path: String): Result<Unit> = Result.success(Unit)',
            '}'
        )
        $r = Invoke-Pairing
        Assert-Equal 1 $r.ExitCode 'unpaired destructive operation verdict'
        if ($r.Output -notmatch 'WebdavOperationStrategy') { throw "the refusal did not name the class - output: $($r.Output)" }
        if ($r.Output -notmatch 'deleteFile') { throw "the refusal did not name the operation - output: $($r.Output)" }
    }

    Test-Case 'the same class passes once it registers the mutation' {
        Reset-Fixture
        Set-Strategy 'WebdavOperationStrategy.kt' @(
            'package com.sza.fastmediasorter.data.transfer',
            '',
            'import com.sza.fastmediasorter.domain.mutation.MutationJournal',
            '',
            'class WebdavOperationStrategy(private val journal: MutationJournal) {',
            '    suspend fun deleteFile(path: String): Result<Unit> = Result.success(Unit)',
            '}'
        )
        $r = Invoke-Pairing
        Assert-Equal 0 $r.ExitCode "paired destructive operation verdict - output: $($r.Output)"
    }

    Test-Case 'a read-only operation on the allowlist passes' {
        Reset-Fixture
        Set-Strategy 'WebdavProgressTracker.kt' @(
            'package com.sza.fastmediasorter.data.transfer',
            '',
            'class WebdavProgressTracker {',
            '    fun clearAll() { }',
            '}'
        )
        Set-Allowlist @("$treeRelative/WebdavProgressTracker.kt#clearAll # in-memory progress map, no filesystem effect.")
        $r = Invoke-Pairing
        Assert-Equal 0 $r.ExitCode "allowlisted read-only verdict - output: $($r.Output)"
    }

    Test-Case 'the same read-only operation is refused while it is not on the allowlist' {
        Reset-Fixture
        Set-Strategy 'WebdavProgressTracker.kt' @(
            'package com.sza.fastmediasorter.data.transfer',
            '',
            'class WebdavProgressTracker {',
            '    fun clearAll() { }',
            '}'
        )
        $r = Invoke-Pairing
        Assert-Equal 1 $r.ExitCode 'silent read-only operation verdict'
        if ($r.Output -notmatch 'clearAll') { throw "the refusal did not name the operation - output: $($r.Output)" }
    }

    Test-Case 'an allowlist row without a reason is itself refused' {
        Reset-Fixture
        Set-Strategy 'WebdavProgressTracker.kt' @(
            'package com.sza.fastmediasorter.data.transfer',
            '',
            'class WebdavProgressTracker {',
            '    fun clearAll() { }',
            '}'
        )
        Set-Allowlist @("$treeRelative/WebdavProgressTracker.kt#clearAll")
        $r = Invoke-Pairing
        Assert-Equal 1 $r.ExitCode 'reasonless allowlist row verdict'
        if ($r.Output -notmatch 'no reason') { throw "the refusal did not say why - output: $($r.Output)" }
    }

    Test-Case 'a baselined operation passes and does not dissolve the rule for its neighbours' {
        Reset-Fixture
        Set-Strategy 'LegacyOperationStrategy.kt' @(
            'package com.sza.fastmediasorter.data.transfer',
            '',
            'class LegacyOperationStrategy {',
            '    suspend fun deleteFile(path: String): Result<Unit> = Result.success(Unit)',
            '    suspend fun moveFile(from: String, to: String): Result<Unit> = Result.success(Unit)',
            '}'
        )
        Set-Baseline @("$treeRelative/LegacyOperationStrategy.kt#deleteFile")
        $r = Invoke-Pairing
        Assert-Equal 1 $r.ExitCode 'partially baselined class verdict'
        if ($r.Output -notmatch 'moveFile') { throw "the unbaselined operation was not named - output: $($r.Output)" }
        if ($r.Output -match 'declares deleteFile') { throw "the baselined operation was reported anyway - output: $($r.Output)" }
    }

    Test-Case 'a class with no destructive declaration is not a subject at all' {
        Reset-Fixture
        Set-Strategy 'WebdavListing.kt' @(
            'package com.sza.fastmediasorter.data.transfer',
            '',
            'class WebdavListing {',
            '    suspend fun listFiles(path: String): List<String> = emptyList()',
            '    suspend fun exists(path: String): Boolean = false',
            '    fun improveOrdering() { }',
            '}'
        )
        $r = Invoke-Pairing
        Assert-Equal 0 $r.ExitCode "non-destructive class verdict - output: $($r.Output)"
        if ($r.Output -match 'improveOrdering') { throw "the verb match was not anchored at the start of the name - output: $($r.Output)" }
    }

    Test-Case 'a tree that does not exist cannot verify rather than passing' {
        Reset-Fixture
        $r = Invoke-Pairing -NoRoot
        Assert-Equal 2 $r.ExitCode 'missing tree verdict'
        if ($r.Output -notmatch 'cannot verify') { throw "the run did not say it could not verify - output: $($r.Output)" }
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertFileopJournalPairing.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
