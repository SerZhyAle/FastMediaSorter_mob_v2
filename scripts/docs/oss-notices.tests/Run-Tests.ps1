<#
.SYNOPSIS
    S1495 - test suite for the OSS notice parser and generator.

.DESCRIPTION
    Asserts the parser's coordinate extraction and its shipping classification
    against synthetic fixtures, and asserts that a missing dependencies block is
    a refusal rather than an empty result.

    The shipping classification is what decides whether a library appears on a
    published legal page, so each configuration kind is asserted by name rather
    than by count alone.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/oss-notices.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0  every assertion passed
      1  at least one assertion failed
      2  could not verify: parser or fixture missing
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'

$parser = Join-Path $RepoRoot 'scripts/docs/OssDependencyParser.ps1'
$fixtures = Join-Path $PSScriptRoot 'fixtures'

foreach ($required in @($parser, (Join-Path $fixtures 'app_v2.build.gradle.kts'), (Join-Path $fixtures 'wear.build.gradle.kts'))) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Error "missing: $required" -ErrorAction Continue
        exit 2
    }
}

. $parser

$script:Failures = [System.Collections.Generic.List[string]]::new()

function Assert-That {
    param([Parameter(Mandatory)] [string] $Name, [Parameter(Mandatory)] [bool] $Condition, [string] $Detail)
    if ($Condition) {
        Write-Host "  PASS  $Name"
    } else {
        Write-Host "  FAIL  $Name$(if ($Detail) { " - $Detail" })"
        $script:Failures.Add($Name)
    }
}

$flavors = @('standard', 'noLegal', 'lite', 'photos', 'legacy', 'vr')
$app = Get-OssDependencies -GradleFile (Join-Path $fixtures 'app_v2.build.gradle.kts') -Module 'app_v2' -Flavors $flavors
$wear = Get-OssDependencies -GradleFile (Join-Path $fixtures 'wear.build.gradle.kts') -Module 'wear' -Flavors $flavors

function Get-Entry {
    param([object[]] $Set, [string] $Artifact)
    return @($Set | Where-Object { $_.Artifact -eq $Artifact })
}

Write-Host 'Parser - coordinate extraction'

Assert-That -Name 'bare implementation is captured' -Condition ((Get-Entry $app 'core-ktx').Count -eq 1)
Assert-That -Name 'quoted flavor configuration is captured' -Condition ((Get-Entry $app 'NewPipeExtractor').Count -eq 1)
Assert-That -Name 'declaration with a trailing block is captured' -Condition ((Get-Entry $app 'epub4j-core').Count -eq 1)
Assert-That -Name 'BOM-managed coordinate keeps an empty version' -Condition ((Get-Entry $app 'ui')[0].Version -eq '')
Assert-That -Name 'versioned coordinate keeps its version' -Condition ((Get-Entry $app 'core-ktx')[0].Version -eq '1.13.0')

Write-Host 'Parser - shapes that carry no coordinate'

Assert-That -Name 'commented-out declaration is ignored' -Condition ((Get-Entry $app 'media3-decoder-av1').Count -eq 0)
Assert-That -Name 'project dependency is ignored' -Condition ((Get-Entry $app 'lint-rules').Count -eq 0)
Assert-That -Name 'local files() dependency is ignored' -Condition (@($app | Where-Object { $_.Group -like '*libs*' }).Count -eq 0)
Assert-That -Name 'platform BOM is ignored' -Condition ((Get-Entry $app 'compose-bom').Count -eq 0)
Assert-That -Name 'variable argument is ignored' -Condition (@($app | Where-Object { $_.Group -eq 'composeBom' }).Count -eq 0)

Write-Host 'Parser - shipping classification'

$shippingCases = @{
    'core-ktx'            = $true
    'NewPipeExtractor'    = $true
    'openxr_loader_for_android' = $true
    'desugar_jdk_libs'    = $true
    'library-no-op'       = $true   # releaseImplementation ships; benchmarkImplementation does not
    'leakcanary-android'  = $false
    'junit'               = $false
    'hilt-android-compiler' = $false
}
foreach ($artifact in $shippingCases.Keys | Sort-Object) {
    $entries = Get-Entry $app $artifact
    $expected = $shippingCases[$artifact]
    $actual = @($entries | Where-Object { $_.Shipping }).Count -gt 0
    Assert-That -Name "$artifact shipping=$expected" -Condition ($actual -eq $expected) -Detail "got $actual"
}

$noOp = Get-Entry $app 'library-no-op'
Assert-That -Name 'benchmarkImplementation of the same artifact is not shipping' `
    -Condition (@($noOp | Where-Object { $_.Configuration -eq 'benchmarkImplementation' -and -not $_.Shipping }).Count -eq 1)
Assert-That -Name 'releaseImplementation of the same artifact is shipping' `
    -Condition (@($noOp | Where-Object { $_.Configuration -eq 'releaseImplementation' -and $_.Shipping }).Count -eq 1)

Write-Host 'Parser - wear module'

Assert-That -Name 'wear coordinates are captured' -Condition ((Get-Entry $wear 'smbj').Count -eq 1)
Assert-That -Name 'wear test configuration is not shipping' -Condition (-not (Get-Entry $wear 'junit')[0].Shipping)
Assert-That -Name 'wear module is stamped on every entry' -Condition (@($wear | Where-Object { $_.Module -ne 'wear' }).Count -eq 0)

Write-Host 'Parser - refusal'

$broken = Join-Path ([System.IO.Path]::GetTempPath()) "s1495-no-deps-$PID.gradle.kts"
Set-Content -LiteralPath $broken -Value "android {`n    compileSdk = 36`n}`n" -Encoding utf8
try {
    $null = & pwsh -NoProfile -Command ". '$parser'; Get-OssDependencies -GradleFile '$broken' -Module 'app_v2' -Flavors @('standard')" 2>&1
    Assert-That -Name 'missing dependencies block exits 2' -Condition ($LASTEXITCODE -eq 2) -Detail "got $LASTEXITCODE"
} finally {
    Remove-Item -LiteralPath $broken -Force -ErrorAction SilentlyContinue
}

Write-Host 'Generator - idempotence, drift and refusal'

$generator = Join-Path $RepoRoot 'scripts/docs/generate-oss-notices.ps1'
$manifestPath = Join-Path $RepoRoot 'scripts/docs/oss-licenses.psd1'
$englishPage = Join-Path $RepoRoot 'docs/OPEN_SOURCE.md'

if (-not (Test-Path -LiteralPath $generator)) {
    Write-Error "missing: $generator" -ErrorAction Continue
    exit 2
}

$null = & pwsh -NoProfile -File $generator -Quiet 2>&1
Assert-That -Name 'generator exits 0 on the current tree' -Condition ($LASTEXITCODE -eq 0) -Detail "got $LASTEXITCODE"

$null = & pwsh -NoProfile -File $generator -Check -Quiet 2>&1
Assert-That -Name 'a freshly generated tree reports no drift' -Condition ($LASTEXITCODE -eq 0) -Detail "got $LASTEXITCODE"

# Alter one character of a rendered page; -Check must notice and the file must come back.
$pageBefore = [System.IO.File]::ReadAllText($englishPage)
try {
    [System.IO.File]::WriteAllText($englishPage, $pageBefore.Replace('# Open Source Notices', '# Open Source Notice'))
    $null = & pwsh -NoProfile -File $generator -Check -Quiet 2>&1
    Assert-That -Name 'a single altered character is drift' -Condition ($LASTEXITCODE -eq 1) -Detail "got $LASTEXITCODE"
} finally {
    [System.IO.File]::WriteAllText($englishPage, $pageBefore)
}

$null = & pwsh -NoProfile -File $generator -Check -Quiet 2>&1
Assert-That -Name 'restoring the page clears the drift' -Condition ($LASTEXITCODE -eq 0) -Detail "got $LASTEXITCODE"

# A shipping coordinate with no licence must stop the generator, not render a gap.
$manifestBefore = [System.IO.File]::ReadAllText($manifestPath)
try {
    [System.IO.File]::WriteAllText($manifestPath, $manifestBefore.Replace("'com.hierynomus:smbj'", "'com.hierynomus:smbj-REMOVED'"))
    $refusal = & pwsh -NoProfile -File $generator -Quiet 2>&1
    $refusalCode = $LASTEXITCODE
    Assert-That -Name 'an unlisted shipping coordinate exits 2' -Condition ($refusalCode -eq 2) -Detail "got $refusalCode"
    Assert-That -Name 'the refusal names the missing coordinate' -Condition (($refusal | Out-String) -match 'com\.hierynomus:smbj')
} finally {
    [System.IO.File]::WriteAllText($manifestPath, $manifestBefore)
    $null = & pwsh -NoProfile -File $generator -Quiet 2>&1
}

Assert-That -Name 'the tree is restored after the refusal case' -Condition ((& {
    $null = & pwsh -NoProfile -File $generator -Check -Quiet 2>&1
    $LASTEXITCODE
}) -eq 0)

Write-Host 'Generator - rendered pages'

foreach ($page in @('docs/OPEN_SOURCE.md', 'docs/OPEN_SOURCE.ru.md', 'docs/OPEN_SOURCE.uk.md')) {
    $full = Join-Path $RepoRoot $page
    Assert-That -Name "$page exists" -Condition (Test-Path -LiteralPath $full)
    $body = Get-Content -LiteralPath $full -Raw
    Assert-That -Name "$page names the GPL component" -Condition ($body -match 'NewPipeExtractor')
    Assert-That -Name "$page scopes the GPL component to noLegal" -Condition ($body -match 'NewPipeExtractor.*noLegal')
    Assert-That -Name "$page carries the generated-file banner" -Condition ($body -match 'generate-oss-notices\.ps1')
}

Write-Host ''
if ($script:Failures.Count -gt 0) {
    Write-Error "oss-notices tests: FAIL ($($script:Failures.Count)) - $($script:Failures -join ', ')" -ErrorAction Continue
    exit 1
}
Write-Host 'oss-notices tests: PASS'
exit 0
