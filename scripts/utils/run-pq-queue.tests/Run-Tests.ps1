#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/utils/run-pq-queue.ps1 - the launcher behind `.\a.ps1 r0` (S4541).

.DESCRIPTION
    The launcher decides which project a long-running worker is started for, so a wrong match is the
    one failure that does real damage. Every case runs against a throwaway tree: FMS_REPO_ROOT is the
    repository, SZA_PORTFOLIO_ROOT holds the registry, SZA_HARNESS_ROOT holds a stub pq.ps1 that records
    its arguments and exits with STUB_EXIT, and USERPROFILE / HOME point at an empty folder so the real
    plugin cache is never read. No case starts a worker.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'run-pq-queue.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Error "script not found: $script" -ErrorAction Continue
    exit 1
}

$pwshExe = (Get-Process -Id $PID).Path
$failures = 0

$root = Join-Path ([System.IO.Path]::GetTempPath()) ('run-pq-queue-tests-' + [guid]::NewGuid().ToString('n').Substring(0, 8))
$repoDir = Join-Path $root 'repo'
$portfolioDir = Join-Path $root 'portfolio'
$harnessDir = Join-Path $root 'harness'
$homeDir = Join-Path $root 'home'
$stubOut = Join-Path $root 'stub-out.txt'
foreach ($d in @($repoDir, $portfolioDir, (Join-Path $harnessDir 'portfolio'), $homeDir)) {
    New-Item -ItemType Directory -Path $d -Force | Out-Null
}

# The stub is what the launcher must reach: it proves the arguments arrived and returns a chosen code.
$stub = @'
Set-Content -LiteralPath $env:STUB_OUT -Value ($args -join ' ') -Encoding UTF8
exit ([int]$(if ($env:STUB_EXIT) { $env:STUB_EXIT } else { 0 }))
'@
Set-Content -LiteralPath (Join-Path $harnessDir 'portfolio/pq.ps1') -Value $stub -Encoding UTF8

$saved = @{}
foreach ($name in 'FMS_REPO_ROOT', 'SZA_PORTFOLIO_ROOT', 'SZA_HARNESS_ROOT', 'USERPROFILE', 'HOME', 'STUB_OUT', 'STUB_EXIT') {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name)
}
$env:FMS_REPO_ROOT = $repoDir
$env:SZA_PORTFOLIO_ROOT = $portfolioDir
$env:SZA_HARNESS_ROOT = $harnessDir
$env:USERPROFILE = $homeDir
$env:HOME = $homeDir
$env:STUB_OUT = $stubOut
$env:STUB_EXIT = ''

function Set-Registry {
    param([object[]] $Repos)
    $json = [ordered]@{ schemaVersion = '1.0'; repos = $Repos } | ConvertTo-Json -Depth 4
    Set-Content -LiteralPath (Join-Path $portfolioDir 'repos.json') -Value $json -Encoding UTF8
}

# The registry spells the path the way pq register wrote it: other casing, forward slashes, a trailing one.
function Get-RegisteredSpelling {
    return (($repoDir.ToLowerInvariant() -replace '\\', '/') + '/')
}

function Invoke-Launcher {
    param([string[]] $LauncherArgs)
    Remove-Item -LiteralPath $stubOut -Force -ErrorAction SilentlyContinue
    $output = & $pwshExe -NoProfile -File $script @LauncherArgs 2>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Output = $output; Stub = $(if (Test-Path -LiteralPath $stubOut) { (Get-Content -LiteralPath $stubOut -Raw).Trim() } else { $null }) }
}

$script:executed = 0
function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    $script:executed++
    try {
        $message = & $Body
        if ($message) {
            Write-Host "FAIL  $Name - $message" -ForegroundColor Red
            $script:failures++
        }
        else {
            Write-Host "PASS  $Name" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

try {
    Set-Registry @(
        [ordered]@{ key = 'other'; path = (Join-Path $root 'elsewhere') },
        [ordered]@{ key = 'fix-key'; path = (Get-RegisteredSpelling) }
    )

    Assert-Case 'Q1 -DryRun names the matched key and the pq.ps1 and starts nothing' {
        $r = Invoke-Launcher @('-DryRun', '-MaxTickets', '3')
        if ($r.Code -ne 0) { return "expected exit 0, got $($r.Code)" }
        if ($r.Output -notmatch "repository 'fix-key'") { return 'the matched key is not printed' }
        if ($r.Output -notmatch 'pq\.ps1 start -Repo fix-key -MaxTickets 3') { return 'the command is not printed with the forwarded arguments' }
        if ($null -ne $r.Stub) { return 'the stub ran during a dry run' }
    }

    Assert-Case 'Q2 a real run forwards every argument after -Repo <key>' {
        $r = Invoke-Launcher @('-MaxTickets', '3', '-Until', '18:30', '-Drain')
        if ($r.Code -ne 0) { return "expected exit 0, got $($r.Code)" }
        if ($r.Stub -cne 'start -Repo fix-key -MaxTickets 3 -Until 18:30 -Drain') { return "pq received '$($r.Stub)'" }
    }

    Assert-Case 'Q3 pq exit code is the launcher exit code' {
        $env:STUB_EXIT = '3'
        try { $r = Invoke-Launcher @('-Drain') } finally { $env:STUB_EXIT = '' }
        if ($r.Code -ne 3) { return "expected exit 3, got $($r.Code)" }
    }

    Assert-Case 'Q4 an unregistered repository exits 2 and starts nothing' {
        Set-Registry @([ordered]@{ key = 'other'; path = (Join-Path $root 'elsewhere') })
        $r = Invoke-Launcher @('-Drain')
        if ($r.Code -ne 2) { return "expected exit 2, got $($r.Code)" }
        if ($null -ne $r.Stub) { return 'the stub ran for an unregistered repository' }
        if ($r.Output -notmatch 'pq register') { return 'the refusal does not name the registering command' }
    }

    Assert-Case 'Q5 a repository registered twice exits 2 and starts nothing' {
        Set-Registry @(
            [ordered]@{ key = 'one'; path = $repoDir },
            [ordered]@{ key = 'two'; path = (Get-RegisteredSpelling) }
        )
        $r = Invoke-Launcher @('-Drain')
        if ($r.Code -ne 2) { return "expected exit 2, got $($r.Code)" }
        if ($null -ne $r.Stub) { return 'the stub ran for an ambiguous match' }
    }

    Assert-Case 'Q6 no pq.ps1 anywhere exits 2' {
        Set-Registry @([ordered]@{ key = 'fix-key'; path = $repoDir })
        $env:SZA_HARNESS_ROOT = Join-Path $root 'nowhere'
        try { $r = Invoke-Launcher @('-Drain') } finally { $env:SZA_HARNESS_ROOT = $harnessDir }
        if ($r.Code -ne 2) { return "expected exit 2, got $($r.Code)" }
        if ($r.Output -notmatch 'pq\.ps1 not found') { return 'the refusal does not say pq.ps1 was not found' }
    }

    Assert-Case 'Q7 a missing registry exits 2' {
        Remove-Item -LiteralPath (Join-Path $portfolioDir 'repos.json') -Force
        $r = Invoke-Launcher @('-Drain')
        if ($r.Code -ne 2) { return "expected exit 2, got $($r.Code)" }
        if ($null -ne $r.Stub) { return 'the stub ran without a registry' }
    }
}
finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name]) }
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "run-pq-queue tests: $(if ($failures -gt 0) { 'FAIL' } else { 'PASS' }) (run=$script:executed skipped=0)"
if ($failures -gt 0) { exit 1 }
exit 0
