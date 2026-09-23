<#
Run-Tests.ps1 - contract tests for assert-install-trust.ps1 (S3451).

Every case builds a throwaway tree under the system temp directory with its own declaration and
runs the gate against it, so no case depends on what the live site carries this minute. The
decisive case is "an undeclared page hands out the APK with no link" - the shape the published
docs/README*.md had for as long as the trust page existed, missed by two manual audits.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-install-trust.ps1')).Path
$passed = 0
$failed = 0
$fixtures = [System.Collections.Generic.List[string]]::new()

$declarationText = @'
@{
    Pages = @(
        @{ Locale = 'en'; Path = 'docs/TRUST.md'; Privacy = 'docs/PRIVACY.md'; PrivacyLink = 'PRIVACY.md'
           Headings = @('What you will see', 'Why it appears', 'What to tap', 'What the app never does') }
    )
    Surfaces = @( @{ Path = 'docs/DOWNLOADS.md'; Link = '(TRUST.md)' } )
    Markers = @( 'example.invalid/releases/latest' )
    Exempt = @( @{ Prefix = 'skip'; Reason = 'fixture' } )
    Claims = @( @{ Id = 'no-servers'; Locale = 'en'; Trust = 'no servers'; Privacy = 'No servers' } )
    ForbiddenPermissions = @('com.google.android.gms.permission.AD_ID')
    ManifestGlob = 'app_v2/src/*/AndroidManifest.xml'
    DependencyFiles = @('gradle/libs.versions.toml')
    ForbiddenArtifacts = @('firebase-analytics')
}
'@

$trustPage = @'
# Trust

## What you will see

Two screens.

## Why it appears

The key is new.

## What to tap

Install anyway.

## What the app never does

Same facts as the [privacy policy](PRIVACY.md).

- It has **no servers**.
'@

function New-Fixture {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ('s3451-' + [guid]::NewGuid().ToString('N'))
    $fixtures.Add($root)
    foreach ($dir in 'docs', 'skip', 'app_v2/src/main', 'gradle') {
        New-Item -ItemType Directory -Path (Join-Path $root $dir) -Force | Out-Null
    }
    Set-Content -LiteralPath (Join-Path $root 'install-trust.psd1') -Value $declarationText -Encoding utf8
    Set-Content -LiteralPath (Join-Path $root 'docs/TRUST.md') -Value $trustPage -Encoding utf8
    Set-Content -LiteralPath (Join-Path $root 'docs/PRIVACY.md') -Value "# Privacy`n`n- **No servers**: none.`n" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $root 'docs/DOWNLOADS.md') -Value "Get it: https://example.invalid/releases/latest - [why the warning](TRUST.md)`n" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $root 'skip/post.md') -Value "https://example.invalid/releases/latest`n" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $root 'app_v2/src/main/AndroidManifest.xml') -Value "<manifest>`n    <uses-permission android:name=`"android.permission.INTERNET`" />`n</manifest>`n" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $root 'gradle/libs.versions.toml') -Value "[libraries]`nglide = { group = `"com.github.bumptech.glide`", name = `"glide`" }`n" -Encoding utf8
    return $root
}

function Invoke-Case {
    param(
        [string]$Name,
        [scriptblock]$Mutate,
        [int]$ExpectedExit,
        [string]$ExpectedText
    )
    $root = New-Fixture
    if ($Mutate) { & $Mutate $root }
    $output = & pwsh -NoProfile -File $Gate -Root $root -Declaration (Join-Path $root 'install-trust.psd1') 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    $ok = ($exitCode -eq $ExpectedExit) -and (-not $ExpectedText -or $output.Contains($ExpectedText))
    if ($ok) {
        $script:passed++
        Write-Host "  PASS  $Name"
    }
    else {
        $script:failed++
        Write-Host "  FAIL  $Name - expected: exit $ExpectedExit '$ExpectedText' | actual: exit $exitCode" -ForegroundColor Red
        Write-Host $output
    }
}

try {
    Invoke-Case 'clean tree passes' $null 0 'PASS'

    Invoke-Case 'undeclared page handing out the APK fails' {
        param($r) Set-Content -LiteralPath (Join-Path $r 'docs/NEW_PAGE.md') -Value "Download: https://example.invalid/releases/latest`n" -Encoding utf8
    } 1 '[UNDECLARED] docs/NEW_PAGE.md'

    Invoke-Case 'root-level page handing out the APK fails' {
        param($r) Set-Content -LiteralPath (Join-Path $r 'landing.html') -Value '<a href="https://example.invalid/releases/latest">APK</a>' -Encoding utf8
    } 1 '[UNDECLARED] landing.html'

    Invoke-Case 'declared surface without its link fails' {
        param($r) Set-Content -LiteralPath (Join-Path $r 'docs/DOWNLOADS.md') -Value "Get it: https://example.invalid/releases/latest`n" -Encoding utf8
    } 1 '[SURFACE] docs/DOWNLOADS.md'

    Invoke-Case 'declared surface deleted fails' {
        param($r) Remove-Item -LiteralPath (Join-Path $r 'docs/DOWNLOADS.md')
    } 1 '[SURFACE] docs/DOWNLOADS.md is declared'

    Invoke-Case 'sections out of order fail' {
        param($r)
        $p = Join-Path $r 'docs/TRUST.md'
        $t = (Get-Content -LiteralPath $p -Raw) -replace '## Why it appears', '## PLACEHOLDER' -replace '## What to tap', '## Why it appears' -replace '## PLACEHOLDER', '## What to tap'
        Set-Content -LiteralPath $p -Value $t -Encoding utf8
    } 1 '[ORDER]'

    Invoke-Case 'deleted trust page fails' {
        param($r) Remove-Item -LiteralPath (Join-Path $r 'docs/TRUST.md')
    } 1 '[ORDER] docs/TRUST.md (en) is missing'

    Invoke-Case 'claim absent from the privacy policy fails' {
        param($r) Set-Content -LiteralPath (Join-Path $r 'docs/PRIVACY.md') -Value "# Privacy`n`nWe run a server.`n" -Encoding utf8
    } 1 "[CLAIM] docs/PRIVACY.md: counterpart 'No servers'"

    Invoke-Case 'never-does section without the privacy link fails' {
        param($r)
        $p = Join-Path $r 'docs/TRUST.md'
        Set-Content -LiteralPath $p -Value ((Get-Content -LiteralPath $p -Raw) -replace '\(PRIVACY\.md\)', '') -Encoding utf8
    } 1 'does not link PRIVACY.md'

    Invoke-Case 'advertising id in a manifest fails' {
        param($r) Set-Content -LiteralPath (Join-Path $r 'app_v2/src/main/AndroidManifest.xml') -Value "<manifest>`n    <uses-permission android:name=`"com.google.android.gms.permission.AD_ID`" />`n</manifest>`n" -Encoding utf8
    } 1 '[CODE] app_v2/src/main/AndroidManifest.xml declares'

    Invoke-Case 'advertising id removed with tools:node passes' {
        param($r) Set-Content -LiteralPath (Join-Path $r 'app_v2/src/main/AndroidManifest.xml') -Value "<manifest>`n    <uses-permission android:name=`"com.google.android.gms.permission.AD_ID`" tools:node=`"remove`" />`n</manifest>`n" -Encoding utf8
    } 0 'PASS'

    Invoke-Case 'analytics artifact in the dependency set fails' {
        param($r) Add-Content -LiteralPath (Join-Path $r 'gradle/libs.versions.toml') -Value 'fa = { group = "com.google.firebase", name = "firebase-analytics" }' -Encoding utf8
    } 1 "[CODE] gradle/libs.versions.toml names 'firebase-analytics'"

    Invoke-Case 'unreadable declaration cannot verify' {
        param($r) Set-Content -LiteralPath (Join-Path $r 'install-trust.psd1') -Value '@{ Pages = ' -Encoding utf8
    } 2 'CANNOT VERIFY'
}
finally {
    foreach ($f in $fixtures) { Remove-Item -LiteralPath $f -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host "assert-install-trust tests: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
exit 0
