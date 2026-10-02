#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for the contacts-declaration check of scripts/quality/assert-security-posture.ps1 (S4030).

.DESCRIPTION
    Every case runs the gate against a throw-away fixture tree passed as -RepoRoot, so no case reads or
    writes the repository's own docs, manifests or gradle.properties.

    The direction that matters most is the false PASS: a check that has only ever been seen on the one real
    repository state has never been seen to refuse. Hence a case per refusal (missing declaration, missing
    status file, absent marker, absent State line, unknown state, a rejected declaration while the rollback
    switch is on or absent) and two cases pinning the silent directions - a permission the build does not
    declare owes Google Play nothing, and a State line outside the contacts block is not read.

    The fixture holds the minimum the gate reads: docs/SECURITY_POSTURE.md, the strings file, one manifest,
    the declaration source, the status file and, per case, gradle.properties.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'assert-security-posture.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Error "script not found: $script" -ErrorAction Continue
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$failures = 0
$root = Join-Path ([System.IO.Path]::GetTempPath()) ('posture-tests-' + [guid]::NewGuid().ToString('n').Substring(0, 8))

$marker = '<!-- s4030:transcribed:contacts-declaration -->'
$postureHead = @'
# Security posture

**Last reconciled:** 2026-01-01

| Permission | Where | Flavors | Why | Rationale | Shown at request |
| --- | --- | --- | --- | --- | --- |
| `android.permission.INTERNET` | `app_v2/src/main` | all | network | `-` | no |
'@
$contactsRow = '| `android.permission.READ_CONTACTS` | `app_v2/src/main` | standard | pinned contact | `-` | no |'

function Write-Fixture {
    param(
        [string] $State = 'not filed',
        [string] $Date = '-',
        [bool] $ContactsDeclared = $true,
        [bool] $WithDeclaration = $true,
        [bool] $WithStatusFile = $true,
        [bool] $WithMarker = $true,
        [bool] $WithStateLine = $true,
        [string] $Properties,
        [string] $Trailer = ''
    )
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
    foreach ($dir in @('docs', 'store_assets', 'app_v2\src\main\res\values')) {
        New-Item -ItemType Directory -Path (Join-Path $root $dir) -Force | Out-Null
    }

    $posture = if ($ContactsDeclared) { "$postureHead`n$contactsRow`n" } else { "$postureHead`n" }
    Set-Content -LiteralPath (Join-Path $root 'docs\SECURITY_POSTURE.md') -Value $posture -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $root 'app_v2\src\main\res\values\strings.xml') -Value '<resources/>' -Encoding UTF8

    $contactsNode = if ($ContactsDeclared) {
        '<uses-permission android:name="android.permission.READ_CONTACTS" />'
    } else {
        '<uses-permission android:name="android.permission.READ_CONTACTS" tools:node="remove" />'
    }
    $manifest = @"
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.INTERNET" />
    $contactsNode
</manifest>
"@
    Set-Content -LiteralPath (Join-Path $root 'app_v2\src\main\AndroidManifest.xml') -Value $manifest -Encoding UTF8

    if ($WithDeclaration) {
        Set-Content -LiteralPath (Join-Path $root 'store_assets\PLAY_CONTACTS_DECLARATION.md') -Value '# Declaration' -Encoding UTF8
    }
    if ($WithStatusFile) {
        $lines = @('# Play publishing state', '', '### Contacts Permission declaration (S4030)', '')
        if ($WithMarker) { $lines += $marker; $lines += '' }
        if ($WithStateLine) { $lines += "**State:** $State" }
        $lines += "**Date:** $Date"
        $lines += '**Extension requested:** -'
        if ($Trailer) { $lines += ''; $lines += $Trailer }
        Set-Content -LiteralPath (Join-Path $root 'docs\PLAY_PUBLISHING_STATE.md') -Value ($lines -join "`n") -Encoding UTF8
    }
    if ($PSBoundParameters.ContainsKey('Properties')) {
        Set-Content -LiteralPath (Join-Path $root 'gradle.properties') -Value $Properties -Encoding UTF8
    }
}

function Invoke-Gate {
    param([switch] $Quiet)
    $gateArgs = @('-RepoRoot', $root)
    if ($Quiet) { $gateArgs += '-Quiet' }
    $text = (& $pwshExe -NoProfile -File $script @gateArgs 2>&1 | Out-String)
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = $text }
}

function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
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

function Test-Passes {
    param([string] $Expect)
    $run = Invoke-Gate
    if ($run.Code -ne 0) { return "expected exit 0, got $($run.Code): $($run.Text.Trim())" }
    if ($Expect -and $run.Text -notmatch [regex]::Escape($Expect)) { return "output does not carry '$Expect': $($run.Text.Trim())" }
    return $null
}

function Test-Refuses {
    $run = Invoke-Gate
    if ($run.Code -ne 1) { return "expected exit 1, got $($run.Code): $($run.Text.Trim())" }
    if ($run.Text -notmatch 'contacts-declaration:') { return "refusal does not name contacts-declaration: $($run.Text.Trim())" }
    return $null
}

try {
    Assert-Case 'C1 a complete fixture in state "not filed" passes and shows the state' {
        Write-Fixture
        Test-Passes -Expect 'state=not filed'
    }

    Assert-Case 'C2 state "filed" passes' {
        Write-Fixture -State 'filed'
        Test-Passes -Expect 'state=filed'
    }

    Assert-Case 'C3 state "approved" passes' {
        Write-Fixture -State 'approved'
        Test-Passes -Expect 'state=approved'
    }

    Assert-Case 'C4 state "extension requested" passes' {
        Write-Fixture -State 'extension requested' -Date '2026-10-02'
        Test-Passes -Expect 'state=extension requested'
    }

    Assert-Case 'C5 the recorded date reaches the verdict line' {
        Write-Fixture -State 'filed' -Date '2026-10-02'
        Test-Passes -Expect 'date=2026-10-02'
    }

    Assert-Case 'C6 -Quiet still prints the state suffix on the verdict line' {
        Write-Fixture -State 'approved'
        $run = Invoke-Gate -Quiet
        if ($run.Code -ne 0) { return "expected exit 0, got $($run.Code): $($run.Text.Trim())" }
        if ($run.Text -notmatch 'contacts-declaration: state=approved') { return "quiet output lost the suffix: $($run.Text.Trim())" }
        return $null
    }

    Assert-Case 'C7 a missing declaration source fails' {
        Write-Fixture -WithDeclaration $false
        Test-Refuses
    }

    Assert-Case 'C8 a missing status file fails' {
        Write-Fixture -WithStatusFile $false
        Test-Refuses
    }

    Assert-Case 'C9 an absent marker fails' {
        Write-Fixture -WithMarker $false
        Test-Refuses
    }

    Assert-Case 'C10 a block without a State line fails' {
        Write-Fixture -WithStateLine $false
        Test-Refuses
    }

    Assert-Case 'C11 an unknown state fails' {
        Write-Fixture -State 'pending review'
        Test-Refuses
    }

    Assert-Case 'C12 state "rejected" with no gradle.properties fails' {
        Write-Fixture -State 'rejected'
        Test-Refuses
    }

    Assert-Case 'C13 state "rejected" with fms.readContacts=on fails' {
        Write-Fixture -State 'rejected' -Properties "org.gradle.jvmargs=-Xmx1g`nfms.readContacts=on"
        Test-Refuses
    }

    Assert-Case 'C14 state "rejected" with gradle.properties silent about the switch fails' {
        Write-Fixture -State 'rejected' -Properties 'org.gradle.jvmargs=-Xmx1g'
        Test-Refuses
    }

    Assert-Case 'C15 state "rejected" with fms.readContacts=off passes' {
        Write-Fixture -State 'rejected' -Properties "org.gradle.jvmargs=-Xmx1g`nfms.readContacts=off"
        Test-Passes -Expect 'state=rejected'
    }

    Assert-Case 'C16 a permission declared only as a removal owes nothing and says so' {
        Write-Fixture -ContactsDeclared $false -WithDeclaration $false -WithStatusFile $false
        Test-Passes -Expect 'not required (permission not declared)'
    }

    Assert-Case 'C17 a State line after the next HTML comment is not read as the contacts state' {
        Write-Fixture -State 'filed' -Trailer "<!-- another:block -->`n**State:** bogus"
        Test-Passes -Expect 'state=filed'
    }
}
finally {
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
}

if ($failures -gt 0) {
    Write-Host "assert-security-posture tests: $failures case(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host 'assert-security-posture tests: all cases passed.' -ForegroundColor Green
exit 0
