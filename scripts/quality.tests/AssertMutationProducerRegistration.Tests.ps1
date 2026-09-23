#requires -Version 7.0
<#
.SYNOPSIS
    S3386: contract suite for scripts/quality/assert-mutation-producer-registration.ps1.

.DESCRIPTION
    The gate refuses a component somebody has already written, so its refusals are demonstrated here
    rather than asserted. Every case builds a fixture tree with the real package layout and a fixture
    registry, and runs the gate against it with -RepoRoot - the whole verdict path executes, including
    the refusal text a developer reads at 2 a.m.

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
$gate = Join-Path $repoRoot 'scripts/quality/assert-mutation-producer-registration.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("mutation-producer-fixture-{0}" -f $PID)
$packageRelative = 'app_v2/src/main/java/com/sza/fastmediasorter'
$packageDir = Join-Path $fixtureRoot $packageRelative
$registry = Join-Path $fixtureRoot 'registry.txt'

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    foreach ($sub in @('worker', 'ui/browse/managers', 'domain/usecase')) {
        New-Item -ItemType Directory -Path (Join-Path $packageDir $sub) -Force | Out-Null
    }
    Set-Content -LiteralPath $registry -Value @('# fixture registry') -Encoding UTF8
}

function Set-Source([string]$RelativePath, [string[]]$Body) {
    $full = Join-Path $packageDir $RelativePath
    New-Item -ItemType Directory -Path (Split-Path -Parent $full) -Force | Out-Null
    Set-Content -LiteralPath $full -Value $Body -Encoding UTF8
}

function Set-Registry([string[]]$Rows) {
    Set-Content -LiteralPath $registry -Value (@('# fixture registry') + $Rows) -Encoding UTF8
}

function Invoke-Producer {
    param([switch]$NoRoot)
    $root = if ($NoRoot) { Join-Path $fixtureRoot 'nowhere' } else { $fixtureRoot }
    $argv = @('-NoProfile', '-NonInteractive', '-File', $gate, '-Gate', '-RegistryPath', $registry, '-RepoRoot', $root)
    $output = & $pwshExe @argv 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

# A worker that deletes user content directly and says nothing about it - the shape the gate exists for.
$silentWorker = @(
    'package com.sza.fastmediasorter.worker',
    '',
    'class ArchiveSweepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {',
    '    override suspend fun doWork(): Result {',
    '        handler.deleteFile(path)',
    '        return Result.success()',
    '    }',
    '}'
)

try {
    Test-Case 'a new surfaceless producer with no registration is refused, by name' {
        Reset-Fixture
        Set-Source 'worker/ArchiveSweepWorker.kt' $silentWorker
        $r = Invoke-Producer
        Assert-Equal 1 $r.ExitCode 'unregistered producer verdict'
        if ($r.Output -notmatch 'ArchiveSweepWorker') { throw "the refusal did not name the file - output: $($r.Output)" }
        if ($r.Output -notmatch 'MutationRecorder') { throw "the refusal did not name the remedy - output: $($r.Output)" }
    }

    Test-Case 'the same worker passes once it names the recorder' {
        Reset-Fixture
        Set-Source 'worker/ArchiveSweepWorker.kt' @(
            'package com.sza.fastmediasorter.worker',
            '',
            'import com.sza.fastmediasorter.domain.mutation.MutationRecorder',
            '',
            'class ArchiveSweepWorker(private val recorder: MutationRecorder) : CoroutineWorker(context, params) {',
            '    override suspend fun doWork(): Result {',
            '        handler.deleteFile(path)',
            '        recorder.recordDelete(resourceId, path, type)',
            '        return Result.success()',
            '    }',
            '}'
        )
        $r = Invoke-Producer
        Assert-Equal 0 $r.ExitCode "recorder-naming producer verdict - output: $($r.Output)"
    }

    Test-Case 'a declared collaborator that records satisfies the producer' {
        Reset-Fixture
        Set-Source 'worker/ArchiveSweepWorker.kt' @(
            'package com.sza.fastmediasorter.worker',
            '',
            'class ArchiveSweepWorker(',
            '    private val sweepUseCase: SweepArchiveUseCase',
            ') : CoroutineWorker(context, params) {',
            '    override suspend fun doWork(): Result = Result.success()',
            '}'
        )
        Set-Source 'domain/usecase/SweepArchiveUseCase.kt' @(
            'package com.sza.fastmediasorter.domain.usecase',
            '',
            'import com.sza.fastmediasorter.domain.mutation.MutationRecorder',
            '',
            'class SweepArchiveUseCase(private val recorder: MutationRecorder) {',
            '    suspend operator fun invoke(path: String) {',
            '        handler.deleteFile(path)',
            '        recorder.recordDelete(1L, path, type)',
            '    }',
            '}'
        )
        $r = Invoke-Producer
        Assert-Equal 0 $r.ExitCode "one-hop registration verdict - output: $($r.Output)"
    }

    Test-Case 'an own-state registry row passes' {
        Reset-Fixture
        Set-Source 'worker/ArchiveSweepWorker.kt' $silentWorker
        Set-Registry @('worker/ArchiveSweepWorker.kt  own-state  # deletes only the temp file it wrote.')
        $r = Invoke-Producer
        Assert-Equal 0 $r.ExitCode "own-state row verdict - output: $($r.Output)"
    }

    Test-Case 'a refresh row naming a real member passes' {
        Reset-Fixture
        Set-Source 'worker/ArchiveSweepWorker.kt' $silentWorker
        Set-Source 'ui/browse/managers/BrowseOpsManager.kt' @(
            'package com.sza.fastmediasorter.ui.browse.managers',
            '',
            'class BrowseOpsManager {',
            '    fun handleTerminalEvent() { viewModel.reloadFiles() }',
            '}'
        )
        Set-Registry @('worker/ArchiveSweepWorker.kt  refresh:BrowseOpsManager.handleTerminalEvent  # the surface that started it reloads.')
        $r = Invoke-Producer
        Assert-Equal 0 $r.ExitCode "valid refresh row verdict - output: $($r.Output)"
    }

    Test-Case 'a refresh row naming a member that does not exist is refused' {
        Reset-Fixture
        Set-Source 'worker/ArchiveSweepWorker.kt' $silentWorker
        Set-Source 'ui/browse/managers/BrowseOpsManager.kt' @(
            'package com.sza.fastmediasorter.ui.browse.managers',
            '',
            'class BrowseOpsManager {',
            '    fun handleTerminalEvent() { viewModel.reloadFiles() }',
            '}'
        )
        Set-Registry @('worker/ArchiveSweepWorker.kt  refresh:BrowseOpsManager.onSweepFinished  # invented.')
        $r = Invoke-Producer
        Assert-Equal 1 $r.ExitCode 'unwired refresh verdict'
        if ($r.Output -notmatch 'onSweepFinished') { throw "the refusal did not name the missing member - output: $($r.Output)" }
    }

    Test-Case 'a registry row without a reason is itself refused' {
        Reset-Fixture
        Set-Source 'worker/ArchiveSweepWorker.kt' $silentWorker
        Set-Registry @('worker/ArchiveSweepWorker.kt  own-state')
        $r = Invoke-Producer
        Assert-Equal 1 $r.ExitCode 'reasonless row verdict'
        if ($r.Output -notmatch 'no reason') { throw "the refusal did not say why - output: $($r.Output)" }
    }

    Test-Case 'a comment naming a mutation is not a subject' {
        Reset-Fixture
        Set-Source 'worker/ArchiveSweepWorker.kt' @(
            'package com.sza.fastmediasorter.worker',
            '',
            'class ArchiveSweepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {',
            '    // Counting only - the removals flow through the subsequent FileOperation.Delete elsewhere.',
            '    /* .deleteFile( is named here in prose and nowhere else. */',
            '    override suspend fun doWork(): Result = Result.success()',
            '}'
        )
        $r = Invoke-Producer
        Assert-Equal 0 $r.ExitCode "comment-only match verdict - output: $($r.Output)"
        if ($r.Output -match 'ArchiveSweepWorker') { throw "a comment was treated as a mutation - output: $($r.Output)" }
    }

    Test-Case 'a mutating entry point under ui/ is not a subject' {
        Reset-Fixture
        Set-Source 'ui/browse/BrowseMediaObserver.kt' @(
            'package com.sza.fastmediasorter.ui.browse',
            '',
            'class BrowseMediaObserver(path: String) : FileObserver(path) {',
            '    fun purge() { handler.deleteFile(path) }',
            '}'
        )
        $r = Invoke-Producer
        Assert-Equal 0 $r.ExitCode "surface-tree verdict - output: $($r.Output)"
        if ($r.Output -match 'BrowseMediaObserver') { throw "a surface-tree class was made a subject - output: $($r.Output)" }
    }

    Test-Case 'a surfaceless class that changes nothing is not a subject' {
        Reset-Fixture
        Set-Source 'worker/ReportWorker.kt' @(
            'package com.sza.fastmediasorter.worker',
            '',
            'class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {',
            '    override suspend fun doWork(): Result {',
            '        repository.listFiles(path)',
            '        return Result.success()',
            '    }',
            '}'
        )
        $r = Invoke-Producer
        Assert-Equal 0 $r.ExitCode "non-mutating worker verdict - output: $($r.Output)"
        if ($r.Output -match 'ReportWorker') { throw "a non-mutating worker was made a subject - output: $($r.Output)" }
    }

    Test-Case 'a tree that does not exist cannot verify rather than passing' {
        Reset-Fixture
        $r = Invoke-Producer -NoRoot
        Assert-Equal 2 $r.ExitCode 'missing tree verdict'
        if ($r.Output -notmatch 'cannot verify') { throw "the run did not say it could not verify - output: $($r.Output)" }
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertMutationProducerRegistration.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
