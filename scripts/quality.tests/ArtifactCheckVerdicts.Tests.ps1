#requires -Version 7.0
<#
.SYNOPSIS
    S4367, S4379: tests for the exit-0 paths CHECK-VERDICT 0.12 item A.2 made exit 2 - the artifact and
    tool checks assert-16kb-alignment, assert-meta-packaging-limits, assert-wear-64bit-abi and
    assert-codex-transcript-hygiene, then assert-wear-phone-identity-parity and
    assert-stream-asset-revisions - for the opt-in flags the batch runners pass, and for the empty
    determined set of assert-icon-style.

.DESCRIPTION
    A check whose requested work did not run (no built artifact, no tool, no rollout, no keystore
    properties, no catalog) must end `<subject>: COULD NOT VERIFY (..)` with exit 2, and a caller
    that declared the input optional (-AllowMissingArtifact, -AllowOffline, -AllowNoCatalog) gets
    `NOT APPLICABLE` with exit 0 - never a pass. A comparison that ran and mismatched stays FAIL.
    The last stdout line is the verdict in every case (rule 2.11).

    Hermetic: each artifact check is copied into a sandbox tree whose repo root holds no build
    output, so "no artifact" is true whatever the working tree looks like. Nothing outside the
    sandbox is written; the codex check runs against an empty profile directory.

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

function Assert-LastLine([string[]]$Lines, [string]$Pattern, [string]$What) {
    $last = @($Lines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { "$_" }) | Select-Object -Last 1
    if ($last -cnotmatch $Pattern) { throw "$What - last stdout line was: $last" }
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pwsh = (Get-Process -Id $PID).Path
$sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ("artifact-check-verdicts.$PID")

# Runs a script as a child and returns its exit code and stdout lines (stderr is discarded: the
# verdict contract is about stdout).
function Invoke-Check([string]$Script, [string[]]$Arguments = @()) {
    $lines = @(& $pwsh -NoProfile -NonInteractive -File $Script @Arguments 2>$null)
    return [pscustomobject]@{ Exit = [int]$LASTEXITCODE; Lines = $lines }
}

New-Item -ItemType Directory -Path (Join-Path $sandbox 'scripts/quality') -Force | Out-Null
try {
    foreach ($name in 'assert-16kb-alignment.ps1', 'assert-meta-packaging-limits.ps1', 'assert-wear-64bit-abi.ps1', 'store-prepublish-thresholds.psd1') {
        Copy-Item -LiteralPath (Join-Path $repoRoot "scripts/quality/$name") -Destination (Join-Path $sandbox 'scripts/quality') -Force
    }
    $align = Join-Path $sandbox 'scripts/quality/assert-16kb-alignment.ps1'
    $meta = Join-Path $sandbox 'scripts/quality/assert-meta-packaging-limits.ps1'
    $wear = Join-Path $sandbox 'scripts/quality/assert-wear-64bit-abi.ps1'

    # --- assert-16kb-alignment ------------------------------------------------------------------
    Test-Case '16kb: no scan root is COULD NOT VERIFY, exit 2' {
        $r = Invoke-Check $align @('-ScanRoot', (Join-Path $sandbox 'does-not-exist'))
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-16kb-alignment: COULD NOT VERIFY \(' 'verdict'
    }
    Test-Case '16kb: a scan root with no 64-bit .so is COULD NOT VERIFY, exit 2' {
        $empty = Join-Path $sandbox 'empty-root'
        New-Item -ItemType Directory -Path $empty -Force | Out-Null
        $r = Invoke-Check $align @('-ScanRoot', $empty)
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-16kb-alignment: COULD NOT VERIFY \(' 'verdict'
    }
    Test-Case '16kb: a missing -Readelf is COULD NOT VERIFY, exit 2' {
        $empty = Join-Path $sandbox 'empty-root'
        New-Item -ItemType Directory -Path $empty -Force | Out-Null
        $r = Invoke-Check $align @('-ScanRoot', $empty, '-Readelf', (Join-Path $sandbox 'no-such-readelf.exe'))
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-16kb-alignment: COULD NOT VERIFY \(-Readelf path not found' 'verdict'
    }

    # --- assert-wear-64bit-abi ------------------------------------------------------------------
    Test-Case 'wear: no built APK is COULD NOT VERIFY, exit 2' {
        $r = Invoke-Check $wear
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-wear-64bit-abi: COULD NOT VERIFY \(no built wear release APK' 'verdict'
    }
    Test-Case 'wear: no built APK with -AllowMissingArtifact is NOT APPLICABLE, exit 0' {
        $r = Invoke-Check $wear @('-Gate', '-AllowMissingArtifact')
        Assert-Equal 0 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-wear-64bit-abi: NOT APPLICABLE \(' 'verdict'
    }
    Test-Case 'wear: a missing -ApkPath is COULD NOT VERIFY even with -AllowMissingArtifact' {
        $r = Invoke-Check $wear @('-ApkPath', (Join-Path $sandbox 'no.apk'), '-AllowMissingArtifact')
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-wear-64bit-abi: COULD NOT VERIFY \(' 'verdict'
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    function New-Apk([string]$Path, [string[]]$Entries) {
        $zip = [System.IO.Compression.ZipFile]::Open($Path, 'Create')
        try {
            foreach ($e in $Entries) { [void]$zip.CreateEntry($e) }
        }
        finally { $zip.Dispose() }
    }
    Test-Case 'wear: an APK with arm64-v8a is PASS, exit 0' {
        $apk = Join-Path $sandbox 'with64.apk'
        New-Apk $apk @('lib/arm64-v8a/libx.so', 'lib/armeabi-v7a/libx.so')
        $r = Invoke-Check $wear @('-ApkPath', $apk)
        Assert-Equal 0 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-wear-64bit-abi: PASS' 'verdict'
    }
    Test-Case 'wear: an APK without arm64-v8a is FAIL, exit 1' {
        $apk = Join-Path $sandbox 'without64.apk'
        New-Apk $apk @('lib/armeabi-v7a/libx.so')
        $r = Invoke-Check $wear @('-ApkPath', $apk)
        Assert-Equal 1 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-wear-64bit-abi: FAIL \(' 'verdict'
    }
    Test-Case 'wear: an APK with no lib/ entries is FAIL, exit 1' {
        $apk = Join-Path $sandbox 'nolib.apk'
        New-Apk $apk @('classes.dex')
        $r = Invoke-Check $wear @('-ApkPath', $apk)
        Assert-Equal 1 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-wear-64bit-abi: FAIL \(' 'verdict'
    }

    # --- assert-meta-packaging-limits -----------------------------------------------------------
    Test-Case 'meta: no built VR APK is COULD NOT VERIFY, exit 2' {
        $r = Invoke-Check $meta
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-meta-packaging-limits: COULD NOT VERIFY \(no built VR-flavor release APK' 'verdict'
    }
    Test-Case 'meta: no built VR APK with -AllowMissingArtifact is NOT APPLICABLE, exit 0' {
        $r = Invoke-Check $meta @('-Gate', '-AllowMissingArtifact')
        Assert-Equal 0 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-meta-packaging-limits: NOT APPLICABLE \(' 'verdict'
    }
    Test-Case 'meta: a missing -ApkPath is COULD NOT VERIFY even with -AllowMissingArtifact' {
        $r = Invoke-Check $meta @('-ApkPath', (Join-Path $sandbox 'no.apk'), '-AllowMissingArtifact')
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-meta-packaging-limits: COULD NOT VERIFY \(' 'verdict'
    }
    Test-Case 'meta: an unreadable APK is COULD NOT VERIFY, exit 2, when nothing else failed' {
        # The size measurement passes and the signature tool may or may not exist; a file that is not
        # a zip leaves the lib/ listing unreadable, which is the rule 2.1 case "could not check".
        $apk = Join-Path $sandbox 'not-a-zip.apk'
        Set-Content -LiteralPath $apk -Value 'not a zip' -Encoding ASCII
        $r = Invoke-Check $meta @('-ApkPath', $apk)
        Assert-LastLine $r.Lines '^assert-meta-packaging-limits: (COULD NOT VERIFY|FAIL) \(' 'verdict'
        if ($r.Exit -notin 1, 2) { throw "expected exit 1 or 2, got $($r.Exit)" }
    }

    # --- assert-codex-transcript-hygiene --------------------------------------------------------
    $codex = Join-Path $repoRoot 'scripts/quality/assert-codex-transcript-hygiene.ps1'
    function Invoke-Codex([hashtable]$EnvMap) {
        $saved = @{}
        $names = @('CLAUDECODE', 'CLAUDE_CODE_SESSION_ID', 'FMS_AGENT_RUNTIME', 'USERPROFILE', 'HOME')
        foreach ($n in $names) { $saved[$n] = [Environment]::GetEnvironmentVariable($n) }
        try {
            foreach ($n in $names) { [Environment]::SetEnvironmentVariable($n, $null) }
            $profileDir = Join-Path $sandbox 'empty-profile'
            New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
            [Environment]::SetEnvironmentVariable('USERPROFILE', $profileDir)
            [Environment]::SetEnvironmentVariable('HOME', $profileDir)
            foreach ($k in $EnvMap.Keys) { [Environment]::SetEnvironmentVariable($k, $EnvMap[$k]) }
            return Invoke-Check $codex @('-Id', 'S9999')
        }
        finally {
            foreach ($n in $names) { [Environment]::SetEnvironmentVariable($n, $saved[$n]) }
        }
    }
    Test-Case 'codex: a Claude Code process is NOT APPLICABLE, exit 0' {
        $r = Invoke-Codex @{ CLAUDECODE = '1' }
        Assert-Equal 0 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-codex-transcript-hygiene: NOT APPLICABLE \(' 'verdict'
    }
    Test-Case 'codex: a ZCode process with no rollout is NOT APPLICABLE, exit 0' {
        $r = Invoke-Codex @{ FMS_AGENT_RUNTIME = 'ZCode' }
        Assert-Equal 0 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-codex-transcript-hygiene: NOT APPLICABLE \(' 'verdict'
    }
    Test-Case 'codex: an unidentified process with no rollout is COULD NOT VERIFY, exit 2' {
        $r = Invoke-Codex @{}
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-codex-transcript-hygiene: COULD NOT VERIFY \(' 'verdict'
    }

    # --- assert-delivery-size-estimates ---------------------------------------------------------
    $delivery = Join-Path $repoRoot 'scripts/quality/assert-delivery-size-estimates.ps1'
    $noGh = Join-Path $sandbox 'no-such-gh.exe'
    Test-Case 'delivery: no gh is COULD NOT VERIFY, exit 2' {
        $r = Invoke-Check $delivery @('-GhPath', $noGh)
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-delivery-size-estimates: COULD NOT VERIFY \(gh not available' 'verdict'
    }
    Test-Case 'delivery: no gh with -AllowOffline is NOT APPLICABLE, exit 0' {
        $r = Invoke-Check $delivery @('-GhPath', $noGh, '-AllowOffline')
        Assert-Equal 0 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-delivery-size-estimates: NOT APPLICABLE \(gh not available' 'verdict'
    }

    # --- assert-wear-phone-identity-parity ------------------------------------------------------
    $identityRoot = Join-Path $sandbox 'identity'
    New-Item -ItemType Directory -Path (Join-Path $identityRoot 'scripts/quality') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot 'scripts/quality/assert-wear-phone-identity-parity.ps1') -Destination (Join-Path $identityRoot 'scripts/quality') -Force
    $identity = Join-Path $identityRoot 'scripts/quality/assert-wear-phone-identity-parity.ps1'
    function Write-Gradle([string]$Module, [string]$AppId) {
        $dir = Join-Path $identityRoot $Module
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $dir 'build.gradle.kts') -Encoding ASCII -Value @(
            "android { defaultConfig { applicationId = `"$AppId`" } }",
            'val keys = findRootSecretFile(".secrets/keystore.properties", "keystore.properties")')
    }
    Test-Case 'identity: matching ids with no keystore properties is COULD NOT VERIFY, exit 2' {
        Write-Gradle 'app_v2' 'com.example.app'
        Write-Gradle 'wear' 'com.example.app'
        $r = Invoke-Check $identity @('-Gate')
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-wear-phone-identity-parity: COULD NOT VERIFY \(not compared: release signing' 'verdict'
    }
    Test-Case 'identity: an id mismatch beside the missing keystore is FAIL, exit 1' {
        Write-Gradle 'wear' 'com.example.other'
        $r = Invoke-Check $identity @('-Gate')
        Assert-Equal 1 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-wear-phone-identity-parity: FAIL \(' 'verdict'
    }
    Test-Case 'identity: matching ids and one keystore on both sides is PASS, exit 0' {
        Write-Gradle 'wear' 'com.example.app'
        $secrets = Join-Path $identityRoot '.secrets'
        New-Item -ItemType Directory -Path $secrets -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $secrets 'keystore.properties') -Encoding ASCII -Value @('storeFile=release.jks', 'keyAlias=release')
        $r = Invoke-Check $identity @('-Gate')
        Assert-Equal 0 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-wear-phone-identity-parity: PASS \(' 'verdict'
    }

    # --- assert-stream-asset-revisions ----------------------------------------------------------
    $streamRev = Join-Path $repoRoot 'scripts/quality/assert-stream-asset-revisions.ps1'
    $noCatalog = Join-Path $sandbox 'no-catalog'
    Test-Case 'stream: an unreachable catalog is COULD NOT VERIFY, exit 2' {
        $r = Invoke-Check $streamRev @('-Gate', '-Quiet', '-CatalogRoot', $noCatalog)
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-stream-asset-revisions: COULD NOT VERIFY \(' 'verdict'
    }
    Test-Case 'stream: an unset FMS_CONTRACTS_ROOT is COULD NOT VERIFY, exit 2' {
        $savedRoot = [Environment]::GetEnvironmentVariable('FMS_CONTRACTS_ROOT')
        try {
            [Environment]::SetEnvironmentVariable('FMS_CONTRACTS_ROOT', $null)
            $r = Invoke-Check $streamRev @('-Gate', '-Quiet')
        }
        finally { [Environment]::SetEnvironmentVariable('FMS_CONTRACTS_ROOT', $savedRoot) }
        Assert-Equal 2 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-stream-asset-revisions: COULD NOT VERIFY \(' 'verdict'
    }
    Test-Case 'stream: an unreachable catalog with -AllowNoCatalog is NOT APPLICABLE, exit 0' {
        $r = Invoke-Check $streamRev @('-Gate', '-Quiet', '-CatalogRoot', $noCatalog, '-AllowNoCatalog')
        Assert-Equal 0 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-stream-asset-revisions: NOT APPLICABLE \(' 'verdict'
    }

    # --- assert-icon-style ----------------------------------------------------------------------
    $iconStyle = Join-Path $repoRoot 'scripts/quality/assert-icon-style.ps1'
    Test-Case 'icon-style: a changed set with no product glyph is PASS over 0 files, exit 0' {
        $r = Invoke-Check $iconStyle @('-Gate', '-Quiet', '-ChangedFiles', 'docs/none.md')
        Assert-Equal 0 $r.Exit 'exit code'
        Assert-LastLine $r.Lines '^assert-icon-style: PASS \(0 file\(s\) inspected\)$' 'verdict'
    }

    # --- the fast-gates batch opts the stream gate in -------------------------------------------
    Test-Case 'fast-gates: assert-stream-asset-revisions.ps1 is passed -AllowNoCatalog' {
        $fastText = Get-Content -LiteralPath (Join-Path $repoRoot 'scripts/quality/assert-fast-gates.ps1') -Raw
        if ($fastText -notmatch "'assert-stream-asset-revisions\.ps1'\s*=\s*@\([^)]*'-AllowNoCatalog'") {
            throw 'the gate table has no -AllowNoCatalog entry for assert-stream-asset-revisions.ps1'
        }
    }

    # --- the release-scope runner opts the artifact and network gates in ------------------------
    $runnerText = Get-Content -LiteralPath (Join-Path $repoRoot 'scripts/quality/assert-release-scope-gates.ps1') -Raw
    foreach ($pair in @(
            @('assert-wear-64bit-abi.ps1', '-AllowMissingArtifact'),
            @('assert-meta-packaging-limits.ps1', '-AllowMissingArtifact'),
            @('assert-delivery-size-estimates.ps1', '-AllowOffline'))) {
        Test-Case "runner: $($pair[0]) is passed $($pair[1])" {
            $pattern = "'" + [regex]::Escape($pair[0]) + "'\s*=\s*@\('" + [regex]::Escape($pair[1]) + "'\)"
            if ($runnerText -notmatch $pattern) { throw "the gate table has no $($pair[1]) entry for $($pair[0])" }
        }
    }
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "ArtifactCheckVerdicts.Tests: $(if ($script:fail -gt 0) { 'FAIL' } else { 'PASS' }) (run=$($script:pass + $script:fail) skipped=0)"
if ($script:fail -gt 0) { exit 1 }
exit 0
