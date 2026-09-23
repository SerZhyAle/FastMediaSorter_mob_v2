#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/quality/assert-lock-path-coverage.ps1 (S3456).

.DESCRIPTION
    Every case passes -Entries and a fixture profile, so the verdict never depends on what the
    repository root holds at the moment the suite runs. The live-tree verdict is the gate's own
    job at release scope.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$gate = Join-Path (Split-Path -Parent $PSScriptRoot) 'assert-lock-path-coverage.ps1'
if (-not (Test-Path -LiteralPath $gate)) {
    Write-Error "gate not found: $gate" -ErrorAction Continue
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fixture = Join-Path $repoRoot 'temp/S3456/coverage-fixture'
if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
New-Item -ItemType Directory -Path $fixture -Force | Out-Null

$profilePath = Join-Path $fixture 'profile.json'
@'
{ "locks": { "pathRules": [
  { "pattern": "^app_v2/", "domain": "Code.Phone" },
  { "pattern": "^(benchmark)/", "domain": "Code" },
  { "pattern": "^PLAN/", "domain": null },
  { "pattern": "^config/detekt/", "domain": "Code" },
  { "pattern": "^README\\.md$", "domain": "Code.Scripts" }
] } }
'@ | Set-Content -LiteralPath $profilePath -Encoding utf8NoBOM

$failures = 0

function Invoke-Gate {
    param([string]$ProfileFile, [string[]]$List)
    $output = & $pwshExe -NoProfile -File $gate -ProfilePath $ProfileFile -Entries ($List -join ',') 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($output | Out-String) }
}

function Assert-Case {
    param([string]$Name, [scriptblock]$Body)
    $message = & $Body
    if ($message) {
        Write-Host "FAIL  $Name - $message" -ForegroundColor Red
        $script:failures++
    } else {
        Write-Host "PASS  $Name"
    }
}

Assert-Case 'matched set, including a full-set rule and an exemption, passes' {
    $r = Invoke-Gate -ProfileFile $profilePath -List @('app_v2/', 'benchmark/', 'PLAN/', 'README.md')
    if ($r.Code -ne 0) { "expected exit 0, got $($r.Code): $($r.Text)" }
}

Assert-Case 'an unmatched directory fails and is named' {
    $r = Invoke-Gate -ProfileFile $profilePath -List @('app_v2/', 'documentation/')
    if ($r.Code -ne 1) { "expected exit 1, got $($r.Code)" }
    elseif ($r.Text -notmatch 'UNMATCHED documentation/') { "finding does not name the entry: $($r.Text)" }
}

Assert-Case 'an unmatched root file fails and is named' {
    $r = Invoke-Gate -ProfileFile $profilePath -List @('README.md', 'build-debug.PS1')
    if ($r.Code -ne 1) { "expected exit 1, got $($r.Code)" }
    elseif ($r.Text -notmatch 'UNMATCHED build-debug\.PS1') { "finding does not name the entry: $($r.Text)" }
}

Assert-Case 'a directory rule does not cover a file of the same name' {
    $r = Invoke-Gate -ProfileFile $profilePath -List @('app_v2')
    if ($r.Code -ne 1) { "expected exit 1 for a root FILE named app_v2, got $($r.Code)" }
}

Assert-Case 'a subtree rule does not cover a sibling under the same root' {
    $r = Invoke-Gate -ProfileFile $profilePath -List @('config/detekt/detekt.yml', 'config/other/x.yml')
    if ($r.Code -ne 1) { "expected exit 1, got $($r.Code)" }
    elseif ($r.Text -notmatch 'UNMATCHED config/ - 1 path\(s\), e\.g\. config/other/x\.yml') { "finding does not name the sibling: $($r.Text)" }
}

Assert-Case 'matching is case-insensitive like the harness' {
    $r = Invoke-Gate -ProfileFile $profilePath -List @('readme.MD')
    if ($r.Code -ne 0) { "expected exit 0, got $($r.Code): $($r.Text)" }
}

Assert-Case 'an unreadable profile cannot verify' {
    $bad = Join-Path $fixture 'bad.json'
    Set-Content -LiteralPath $bad -Value '{ not json' -Encoding utf8NoBOM
    $r = Invoke-Gate -ProfileFile $bad -List @('app_v2/')
    if ($r.Code -ne 2) { "expected exit 2, got $($r.Code)" }
}

Assert-Case 'a missing profile cannot verify' {
    $r = Invoke-Gate -ProfileFile (Join-Path $fixture 'absent.json') -List @('app_v2/')
    if ($r.Code -ne 2) { "expected exit 2, got $($r.Code)" }
}

Remove-Item -LiteralPath $fixture -Recurse -Force

if ($failures -gt 0) {
    Write-Output "assert-lock-path-coverage tests: FAIL ($failures)"
    exit 1
}
Write-Output 'assert-lock-path-coverage tests: PASS'
exit 0
