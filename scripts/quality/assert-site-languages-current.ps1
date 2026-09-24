#requires -Version 7.0
<#
.SYNOPSIS
    S1211 freshness gate: _data/languages.yml equals a fresh render from locales_config.xml.

.DESCRIPTION
    Re-runs scripts/docs/generate-site-languages.ps1 into a temporary file and compares it byte for
    byte with the committed _data/languages.yml. A generated file with no gate silently stops
    tracking its source, and then "a fourteenth language needs no template edit" (S1211 criterion 6)
    stops being testable: the app would gain the language and the site would not.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the verdict line.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-site-languages-current.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): RELEASE. Its subject is the whole site's language composition,
    not one ticket's file set; a stale list reaches a reader only when the site is published.

    Exit codes:
      0  _data/languages.yml is current
      1  _data/languages.yml is missing or differs from a fresh render
      2  cannot verify: the generator is missing or refused to render (its own message is shown)
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $Gate,
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'site-languages'; files = '_data/languages.yml,app_v2/src/main/res/xml/locales_config.xml' })

$generator = Join-Path $RepoRoot 'scripts/docs/generate-site-languages.ps1'
$committed = Join-Path $RepoRoot '_data/languages.yml'

if (-not (Test-Path -LiteralPath $generator)) {
    Write-Host "assert-site-languages-current: generator missing: $generator" -ForegroundColor Red
    exit 2
}

$tempDir = Join-Path $RepoRoot 'temp/scratch'
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$fresh = Join-Path $tempDir ("site-languages-{0}.yml" -f [guid]::NewGuid().ToString('N'))

try {
    $genOutput = & pwsh -NoProfile -File $generator -RepoRoot $RepoRoot -OutFile $fresh 2>&1
    $genExit = $LASTEXITCODE
    if ($genExit -ne 0) {
        Write-Host "assert-site-languages-current: FAIL-TO-VERIFY - the generator exited $genExit" -ForegroundColor Red
        $genOutput | ForEach-Object { Write-Host "  $_" }
        exit 2
    }

    if (-not (Test-Path -LiteralPath $committed)) {
        Write-Host "assert-site-languages-current: FAIL - _data/languages.yml is missing; run scripts/docs/generate-site-languages.ps1" -ForegroundColor Red
        exit 1
    }

    $expected = [System.IO.File]::ReadAllText($fresh)
    $actual = [System.IO.File]::ReadAllText($committed)
    if ($expected -ne $actual) {
        Write-Host "assert-site-languages-current: FAIL - _data/languages.yml differs from a fresh render of app_v2/src/main/res/xml/locales_config.xml; run scripts/docs/generate-site-languages.ps1" -ForegroundColor Red
        if (-not $Quiet) {
            Compare-Object ($actual -split "`n") ($expected -split "`n") |
                ForEach-Object { Write-Host ("  {0} {1}" -f ($(if ($_.SideIndicator -eq '=>') { 'expected' } else { 'actual  ' })), $_.InputObject) }
        }
        exit 1
    }

    Write-Host 'assert-site-languages-current: PASS (_data/languages.yml matches locales_config.xml)' -ForegroundColor Green
    exit 0
} finally {
    Remove-Item -LiteralPath $fresh -ErrorAction SilentlyContinue
}
