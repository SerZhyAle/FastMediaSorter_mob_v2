#requires -Version 7.0
<#
.SYNOPSIS
    S3371: contract suite for scripts/quality/assert-no-secrets.ps1.

.DESCRIPTION
    Every detector is seeded with a synthetic finding in a fixture tree, because a secret gate that
    has never refused anything is indistinguishable from one whose regexes no longer match. The
    fixtures live under the system temp folder and are removed in the finally block; the gate is
    pointed at them with -RepoRoot, and at a fixture allowlist with -AllowlistPath, so neither this
    repository's tree nor its real allowlist is touched to execute a refusal.

    The literals below are synthetic and are not credentials of anything. That is also why this file
    carries a file: entry in the real allowlist - it quotes the shapes the gate looks for.

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
$gate = Join-Path $repoRoot 'scripts/quality/assert-no-secrets.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("no-secrets-fixture-{0}" -f $PID)
$sourceDir = Join-Path $fixtureRoot 'app_v2/src/main/java'
$testDir = Join-Path $fixtureRoot 'app_v2/src/test/java'
New-Item -ItemType Directory -Path $sourceDir -Force | Out-Null
New-Item -ItemType Directory -Path $testDir -Force | Out-Null

function New-Fixture([string]$Relative, [string[]]$Lines) {
    $path = Join-Path $fixtureRoot $Relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    Set-Content -LiteralPath $path -Value $Lines -Encoding UTF8
    return $Relative
}

# The gate's own allowlist, copied so a fixture can rely on its entries without this suite being
# able to weaken the real one.
$goodAllowlist = Join-Path $fixtureRoot 'allowlist-good.txt'
Copy-Item -LiteralPath (Join-Path $repoRoot 'scripts/quality/no-secrets-allowlist.txt') -Destination $goodAllowlist

function Invoke-Gate([string]$Changed, [string]$Allowlist = $goodAllowlist) {
    $output = & $pwshExe -NoProfile -NonInteractive -File $gate -Gate -RepoRoot $fixtureRoot -AllowlistPath $Allowlist -ChangedFiles $Changed 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

try {
    Test-Case 'a clean source file passes' {
        $rel = New-Fixture 'app_v2/src/main/java/Clean.kt' @(
            'class Clean {',
            '    private val endpoint = "https://example.invalid/api"',
            '}'
        )
        $r = Invoke-Gate $rel
        Assert-Equal 0 $r.ExitCode "clean file verdict - output: $($r.Output)"
    }

    Test-Case 'a private-key block is refused' {
        $rel = New-Fixture 'app_v2/src/main/java/Key.kt' @(
            'val pem = """',
            '-----BEGIN RSA PRIVATE KEY-----',
            '"""'
        )
        $r = Invoke-Gate $rel
        Assert-Equal 1 $r.ExitCode 'private-key verdict'
        if ($r.Output -notmatch 'private-key-block') { throw "the refusal did not name the detector - output: $($r.Output)" }
    }

    Test-Case 'a provider-shaped token is refused' {
        $rel = New-Fixture 'app_v2/src/main/java/Provider.kt' @(
            'val awsAccess = "AKIA' + ('Q7RXMB4NLZ2CV5TD') + '"'
        )
        $r = Invoke-Gate $rel
        Assert-Equal 1 $r.ExitCode 'provider-token verdict'
        if ($r.Output -notmatch 'provider-token') { throw "the refusal did not name the detector - output: $($r.Output)" }
    }

    Test-Case 'a credential-shaped assignment is refused' {
        $rel = New-Fixture 'app_v2/src/main/java/Assign.kt' @(
            'val password = "Gk7#vQ2rLp9Xz4Tw"'
        )
        $r = Invoke-Gate $rel
        Assert-Equal 1 $r.ExitCode 'secret-assignment verdict'
        if ($r.Output -notmatch 'secret-assignment') { throw "the refusal did not name the detector - output: $($r.Output)" }
    }

    Test-Case 'a placeholder value is not a finding' {
        $rel = New-Fixture 'app_v2/src/main/java/Placeholder.kt' @(
            'val password = "your_password_here"',
            'val token = "${runtimeToken}"'
        )
        $r = Invoke-Gate $rel
        Assert-Equal 0 $r.ExitCode "placeholder verdict - output: $($r.Output)"
    }

    Test-Case 'a credential-shaped assignment in a test source is not a finding' {
        $rel = New-Fixture 'app_v2/src/test/java/AssignTest.kt' @(
            'val password = "Gk7#vQ2rLp9Xz4Tw"'
        )
        $r = Invoke-Gate $rel
        Assert-Equal 0 $r.ExitCode "test-source verdict - output: $($r.Output)"
    }

    Test-Case 'a private key in a test source is still refused' {
        $rel = New-Fixture 'app_v2/src/test/java/KeyTest.kt' @(
            '-----BEGIN PRIVATE KEY-----'
        )
        $r = Invoke-Gate $rel
        Assert-Equal 1 $r.ExitCode 'test-source private-key verdict'
    }

    Test-Case 'an allowlisted literal passes' {
        $rel = New-Fixture 'app_v2/src/main/java/Allowed.kt' @(
            'val integrity = "sha512-8xK3mQ7vRt2wZp9LcYbN4jHdFgA6sEuI1oTrXvBkWqCzMnPyJ0"'
        )
        $r = Invoke-Gate $rel
        Assert-Equal 0 $r.ExitCode "allowlisted literal verdict - output: $($r.Output)"
    }

    Test-Case 'the same literal without its allowlist entry is refused' {
        $bare = Join-Path $fixtureRoot 'allowlist-bare.txt'
        Set-Content -LiteralPath $bare -Value '# no entries' -Encoding UTF8
        $rel = New-Fixture 'app_v2/src/main/java/Allowed.kt' @(
            'val integrity = "sha512-8xK3mQ7vRt2wZp9LcYbN4jHdFgA6sEuI1oTrXvBkWqCzMnPyJ0"'
        )
        $r = Invoke-Gate $rel $bare
        Assert-Equal 1 $r.ExitCode 'un-allowlisted literal verdict'
        if ($r.Output -notmatch 'high-entropy-literal') { throw "the refusal did not name the detector - output: $($r.Output)" }
    }

    Test-Case 'an allowlist entry with no reason is refused' {
        $bad = Join-Path $fixtureRoot 'allowlist-bad.txt'
        Set-Content -LiteralPath $bad -Value 'file:app_v2/src/main/java/Clean.kt' -Encoding UTF8
        $rel = New-Fixture 'app_v2/src/main/java/Clean.kt' @('class Clean')
        $r = Invoke-Gate $rel $bad
        Assert-Equal 1 $r.ExitCode 'malformed allowlist verdict'
        if ($r.Output -notmatch 'no reason comment') { throw "the refusal did not say why - output: $($r.Output)" }
    }

    Test-Case 'a missing allowlist cannot verify rather than passing' {
        $rel = New-Fixture 'app_v2/src/main/java/Clean.kt' @('class Clean')
        $r = Invoke-Gate $rel (Join-Path $fixtureRoot 'allowlist-absent.txt')
        Assert-Equal 2 $r.ExitCode 'missing allowlist verdict'
    }

    Test-Case 'a vendored lock file is out of scope' {
        $rel = New-Fixture 'scripts/mcp/thing/node_modules/package-lock.json' @(
            '{ "integrity": "sha512-8xK3mQ7vRt2wZp9LcYbN4jHdFgA6sEuI1oTrXvBkWqCzMnPyJ0" }'
        )
        $r = Invoke-Gate $rel (Join-Path $fixtureRoot 'allowlist-bare.txt')
        Assert-Equal 0 $r.ExitCode "vendored path verdict - output: $($r.Output)"
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertNoSecrets.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
