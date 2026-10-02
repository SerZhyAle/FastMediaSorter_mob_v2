#requires -Version 7.0
<#
.SYNOPSIS
    S3455: contract suite for scripts/utils/restamp-canon.ps1.

.DESCRIPTION
    Drives the re-stamp against a fixture repository and a stub plugin root whose
    check-compliance.ps1 prints a fixed -PrintDigest answer, so neither this repository's stamp
    nor the installed plugin is read or written.

.NOTES
    Exit codes:
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
$subject = Join-Path $repoRoot 'scripts/utils/restamp-canon.ps1'
$pwshExe = (Get-Process -Id $PID).Path

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("restamp-canon-fixture-{0}" -f $PID)
$fixtureRepo = Join-Path $fixtureRoot 'repo'
$pluginRoot = Join-Path $fixtureRoot 'plugin'
$newReaderRoot = Join-Path $fixtureRoot 'plugin-new'
New-Item -ItemType Directory -Path $fixtureRepo -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $pluginRoot 'tools') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $newReaderRoot 'tools') -Force | Out-Null

$liveDigest = 'sha256:' + ('b' * 64)
$readerBody = @(
    'param([string]$RepoRoot, [switch]$PrintDigest)',
    "Write-Host 'canon version: 2026.09.22.2'",
    "Write-Host 'coreDigest:    $liveDigest'"
)
# The old reader ages the stamp from adoptedOn only; the new one names reconciledOn, which is
# exactly what the subject looks for to decide whether adoptedOn may stay put.
Set-Content -LiteralPath (Join-Path $pluginRoot 'tools/check-compliance.ps1') -Encoding UTF8 -Value $readerBody
Set-Content -LiteralPath (Join-Path $newReaderRoot 'tools/check-compliance.ps1') -Encoding UTF8 -Value (
    $readerBody + '# age from canon.reconciledOn, else canon.adoptedOn')

$stampPath = Join-Path $fixtureRepo '.sza-canon.json'

function Write-Stamp([string]$Version, [string]$Digest, [string]$AdoptedOn, [string]$ReconciledOn) {
    $lines = @(
        '{',
        '  "$comment": "fixture - the version word appears here and must survive.",',
        '',
        '  "canon": {',
        "    `"version`": `"$Version`",",
        "    `"coreDigest`": `"$Digest`",",
        "    `"adoptedOn`": `"$AdoptedOn`","
    )
    if ($ReconciledOn) { $lines += "    `"reconciledOn`": `"$ReconciledOn`"," }
    $lines += @(
        '    "model": "reference"',
        '  },',
        '  "exemptions": []',
        '}'
    )
    [System.IO.File]::WriteAllText($stampPath, ($lines -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
}

function Invoke-Subject([string[]]$Extra, [string]$Plugin = $pluginRoot) {
    $out = & $pwshExe -NoProfile -File $subject -RepoRoot $fixtureRepo -PluginRoot $Plugin -Date '2026-09-23' @Extra 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out }
}

$staleDigest = 'sha256:' + ('a' * 64)

try {
    Test-Case 'an old reader: version, digest and adoptedOn move, reconciledOn is inserted' {
        Write-Stamp '2026.09.06.1' $staleDigest '2026-08-18'
        $expected = [System.IO.File]::ReadAllText($stampPath).
            Replace('2026.09.06.1', '2026.09.22.2').
            Replace($staleDigest, $liveDigest).
            Replace('"adoptedOn": "2026-08-18",', "`"adoptedOn`": `"2026-09-23`",`n    `"reconciledOn`": `"2026-09-23`",")
        $r = Invoke-Subject @()
        Assert-Equal 0 $r.ExitCode "write verdict - output: $($r.Output)"
        Assert-Equal $expected ([System.IO.File]::ReadAllText($stampPath)) 'rewritten stamp text'
    }

    Test-Case 'a reader that honours reconciledOn: adoptedOn kept, reconciledOn inserted' {
        Write-Stamp '2026.09.06.1' $staleDigest '2026-08-18'
        $expected = [System.IO.File]::ReadAllText($stampPath).
            Replace('2026.09.06.1', '2026.09.22.2').
            Replace($staleDigest, $liveDigest).
            Replace('"adoptedOn": "2026-08-18",', "`"adoptedOn`": `"2026-08-18`",`n    `"reconciledOn`": `"2026-09-23`",")
        $r = Invoke-Subject @() $newReaderRoot
        Assert-Equal 0 $r.ExitCode "write verdict - output: $($r.Output)"
        Assert-Equal $expected ([System.IO.File]::ReadAllText($stampPath)) 'rewritten stamp text'
    }

    Test-Case 'an existing reconciledOn is rewritten in place, adoptedOn kept' {
        Write-Stamp '2026.09.06.1' $staleDigest '2026-08-18' '2026-09-01'
        $expected = [System.IO.File]::ReadAllText($stampPath).
            Replace('2026.09.06.1', '2026.09.22.2').
            Replace($staleDigest, $liveDigest).
            Replace('2026-09-01', '2026-09-23')
        $r = Invoke-Subject @() $newReaderRoot
        Assert-Equal 0 $r.ExitCode "write verdict - output: $($r.Output)"
        Assert-Equal $expected ([System.IO.File]::ReadAllText($stampPath)) 'rewritten stamp text'
    }

    Test-Case 'a current stamp is left byte-identical, adoption date included' {
        Write-Stamp '2026.09.22.2' $liveDigest '2026-09-01'
        $before = [System.IO.File]::ReadAllText($stampPath)
        $r = Invoke-Subject @()
        Assert-Equal 0 $r.ExitCode "current verdict - output: $($r.Output)"
        if ($r.Output -notmatch 'CURRENT') { throw "no CURRENT verdict word - output: $($r.Output)" }
        Assert-Equal $before ([System.IO.File]::ReadAllText($stampPath)) 'unchanged stamp text'
    }

    Test-Case 'a dry run writes nothing' {
        Write-Stamp '2026.09.06.1' ('sha256:' + ('a' * 64)) '2026-08-18'
        $before = [System.IO.File]::ReadAllText($stampPath)
        $r = Invoke-Subject @('-DryRun')
        Assert-Equal 0 $r.ExitCode "dry-run verdict - output: $($r.Output)"
        Assert-Equal $before ([System.IO.File]::ReadAllText($stampPath)) 'dry-run stamp text'
    }

    Test-Case 'a stamp missing a freshness field cannot be verified' {
        [System.IO.File]::WriteAllText($stampPath, '{ "canon": { "version": "2026.09.06.1" } }')
        $r = Invoke-Subject @()
        Assert-Equal 2 $r.ExitCode "missing-field verdict - output: $($r.Output)"
    }

    Test-Case 'no stamp at all cannot be verified' {
        Remove-Item -LiteralPath $stampPath -Force
        $r = Invoke-Subject @()
        Assert-Equal 2 $r.ExitCode "no-stamp verdict - output: $($r.Output)"
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("restamp-canon.Tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
