#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for the overwrite rule in scripts/release/gen_fastlane_changelog.ps1 (S3027).

.DESCRIPTION
    A changelog file is the localized "What's new" Play and IzzyOnDroid show for a version that may
    already be published, so whichever copy is written last is what users read. A wear release once
    replaced the phone's notes and nothing noticed until an unrelated merge collided on the file.
    The rule this suite guards: identical content and a first write go through as before, a
    DIFFERING rewrite is refused with exit 3, and -Overwrite is the deliberate way past it.

    Every case runs against sandbox -WhatsNewRoot and -FastlaneRoot trees. The real fastlane
    metadata is never read or written.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
      2 - the fixture could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$subject = Join-Path $repoRoot 'scripts/release/gen_fastlane_changelog.ps1'

$script:pass = 0
$script:fail = 0

function Assert-That {
    param([string] $Name, [bool] $Condition, [string] $Detail)
    if ($Condition) {
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "  FAIL  $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
    }
}

$run = Get-Date -Format 'yyyyMMdd-HHmmss'
$sandbox = Join-Path $repoRoot "temp/S3027/gen-changelog-tests/$run"
$whatsNew = Join-Path $sandbox 'docs'
$fastlane = Join-Path $sandbox 'fastlane'
try {
    New-Item -ItemType Directory -Path $whatsNew -Force | Out-Null
    New-Item -ItemType Directory -Path $fastlane -Force | Out-Null
} catch {
    Write-Host "gen-fastlane-changelog tests: fixture could not be prepared - $_" -ForegroundColor Red
    exit 2
}

# The three locale sources the generator reads, each with its own "current release" marker.
function Write-Sources {
    param([Parameter(Mandatory)][string] $Bullet)
    $sources = @(
        @{ File = 'WHATS_NEW.md';    Marker = 'Current release:' },
        @{ File = 'WHATS_NEW-ru.md'; Marker = 'Текущий релиз:' },
        @{ File = 'WHATS_NEW-uk.md'; Marker = 'Поточний реліз:' }
    )
    foreach ($s in $sources) {
        $text = @(
            "# What's New",
            '',
            "**$($s.Marker) 9.99.9999.999** (test)",
            '',
            "- $Bullet",
            '',
            '---',
            '',
            '## Previous Release: 9.99.9999.998'
        ) -join "`n"
        [System.IO.File]::WriteAllText((Join-Path $whatsNew $s.File), $text, (New-Object System.Text.UTF8Encoding $false))
    }
}

function Invoke-Generator {
    param([string[]] $Extra = @())
    $arguments = @('-VersionCode', '999999999', '-VersionName', '9.99.9999.999',
                   '-WhatsNewRoot', $whatsNew, '-FastlaneRoot', $fastlane) + $Extra
    $out = & $pwshExe -NoProfile -File $subject @arguments 2>&1 | ForEach-Object { [string]$_ }
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($out -join "`n") }
}

$enPath = Join-Path $fastlane 'en-US/changelogs/999999999.txt'

try {
    Write-Host 'First write'
    Write-Sources -Bullet 'The phone notes nobody may overwrite.'
    $r1 = Invoke-Generator
    Assert-That 'a first write exits 0' ($r1.Code -eq 0) "exit=$($r1.Code) $($r1.Text)"
    Assert-That 'the en-US changelog exists' (Test-Path -LiteralPath $enPath) $enPath
    $first = if (Test-Path -LiteralPath $enPath) { [System.IO.File]::ReadAllText($enPath) } else { '' }
    Assert-That 'it carries the source bullet' ($first -match 'phone notes nobody may overwrite') $first

    Write-Host 'Identical rewrite stays idempotent'
    $r2 = Invoke-Generator
    Assert-That 'an identical rewrite exits 0' ($r2.Code -eq 0) "exit=$($r2.Code) $($r2.Text)"
    Assert-That 'the file is unchanged' ([System.IO.File]::ReadAllText($enPath) -eq $first) 'content moved'

    Write-Host 'Differing rewrite is refused'
    Write-Sources -Bullet 'Wear OS release notes that belong under the watch code.'
    $r3 = Invoke-Generator
    Assert-That 'a differing rewrite exits 3' ($r3.Code -eq 3) "exit=$($r3.Code) $($r3.Text)"
    Assert-That 'the refusal names the flag that allows it' ($r3.Text -match '-Overwrite') $r3.Text
    Assert-That 'the existing notes are untouched' ([System.IO.File]::ReadAllText($enPath) -eq $first) 'the refusal still wrote'

    Write-Host 'Overwrite is the deliberate way past'
    $r4 = Invoke-Generator -Extra @('-Overwrite')
    Assert-That '-Overwrite exits 0' ($r4.Code -eq 0) "exit=$($r4.Code) $($r4.Text)"
    Assert-That 'the new notes replaced the old' ([System.IO.File]::ReadAllText($enPath) -match 'belong under the watch code') 'content did not change'

    Write-Host 'The real tree is untouched'
    $realChangelog = Join-Path $repoRoot 'fastlane/metadata/android/en-US/changelogs/999999999.txt'
    Assert-That 'no changelog was written under the real fastlane root' (-not (Test-Path -LiteralPath $realChangelog)) $realChangelog
}
finally {
    Remove-Item -LiteralPath (Split-Path -Parent $sandbox) -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host ("gen-fastlane-changelog tests: {0} passed, {1} failed" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
