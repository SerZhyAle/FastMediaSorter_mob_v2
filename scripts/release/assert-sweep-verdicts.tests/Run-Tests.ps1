#requires -Version 7.0
<#
.SYNOPSIS
    S4249 contract suite for scripts/release/assert-sweep-verdicts.ps1 - the release-point read of
    the phone and watch sweep verdicts. S4498 cases pin what "current" means for a verdict.

.DESCRIPTION
    Hermetic: fixture verdict files, fingerprint records and a groups file in a scratch tree under
    the system temp directory, passed through -RepoRoot. File times are set explicitly, so staleness
    is decided by the fixture and not by the clock. Pins strategic S4249 section 11.7: a verdict
    missing a blocking branch is never a pass.

    S4498: a verdict is stale when a group it JUDGED (verdictGroups: phone-src, wear-src) changed
    after the file was written, read from the group's `since` stamp. A re-record that left those
    groups alone moves recordedAt and nothing else, and must not stale the verdict; a moved group
    that the verdict did not judge (docs, scripts) must not either. The direction that matters is
    the false negative, so a case per judged group pins that its movement still stales.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "scripts/release/assert-sweep-verdicts.ps1"

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$script = Join-Path $repoRoot 'scripts/release/assert-sweep-verdicts.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Host "FAIL | not found: $script" -ForegroundColor Red
    exit 1
}

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
        return
    }
    Write-Host "FAIL | $Label" -ForegroundColor Red
    Write-Host "     | expected: $Expected | actual: $Actual" -ForegroundColor Red
    $script:failed++
}

$root = Join-Path ([System.IO.Path]::GetTempPath()) ('s4249-verdicts-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $root 'temp') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $root 'scripts\quality') -Force | Out-Null
$recordedAt = [datetime]'2026-10-14T10:00:00'
$groupsPath = Join-Path $root 'scripts\quality\release-scope-fingerprint.groups.json'

function Write-Groups {
    param([bool]$WithVerdictGroups = $true)
    $body = [ordered]@{ groups = [ordered]@{ 'phone-src' = @('app_v2/src'); 'wear-src' = @('wear/src'); docs = @('docs') } }
    if ($WithVerdictGroups) { $body['verdictGroups'] = [ordered]@{ phone = @('phone-src'); wear = @('wear-src') } }
    Set-Content -LiteralPath $groupsPath -Encoding utf8NoBOM -Value ($body | ConvertTo-Json -Depth 5)
}

# One record per scope. $Since maps a group to its `since` stamp; a group left out of the map gets
# the record's own recordedAt, and -NoSince writes the pre-S4498 shape with no `since` at all.
function Write-Records {
    param([datetime]$At = $recordedAt, [hashtable]$Since = @{}, [switch]$NoSince, [string[]]$DropGroup = @())
    $layout = @{ 'RELEASE-FREEZE-FINGERPRINTS.json' = @('phone-src', 'wear-src', 'docs'); 'RELEASE-FREEZE-FINGERPRINTS-WEAR.json' = @('wear-src', 'docs') }
    foreach ($name in $layout.Keys) {
        $groups = [ordered]@{}
        foreach ($group in $layout[$name]) {
            if ($DropGroup -contains $group) { continue }
            $entry = [ordered]@{ hash = 'AAAA0000AAAA0000'; files = 3 }
            if (-not $NoSince) {
                $entry['since'] = if ($Since.ContainsKey($group)) { ([datetime]$Since[$group]).ToString('s') } else { $At.ToString('s') }
            }
            $groups[$group] = $entry
        }
        Set-Content -LiteralPath (Join-Path $root "temp\$name") -Encoding utf8NoBOM `
            -Value ([ordered]@{ schema = 1; recordedAt = $At.ToString('s'); groups = $groups } | ConvertTo-Json -Depth 6)
    }
}

function New-Verdict {
    param([string]$Name, [bool]$Pass = $true, [bool]$Blocked = $false, [datetime]$Written = $recordedAt.AddMinutes(30))
    $path = Join-Path $root $Name
    Set-Content -LiteralPath $path -Encoding utf8NoBOM -Value (@{ pass = $Pass; blocked = $Blocked; breakdown = @{} } | ConvertTo-Json)
    (Get-Item -LiteralPath $path).LastWriteTime = $Written
    return $path
}

function Invoke-Check {
    param([string[]]$CheckArgs)
    $null = & pwsh -NoProfile -File $script @CheckArgs -RepoRoot $root 2>&1
    return $LASTEXITCODE
}

try {
    Write-Groups
    Write-Records
    $phone = New-Verdict -Name 'phone.json'
    $small = New-Verdict -Name 'small.json'
    $large = New-Verdict -Name 'large.json'

    Assert-Equal -Label 'both pass exits 0' -Expected 0 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$large"))

    $wearFail = New-Verdict -Name 'wear-fail.json' -Pass $false
    Assert-Equal -Label 'a failed watch verdict exits 1' -Expected 1 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$wearFail"))

    $wearBlocked = New-Verdict -Name 'wear-blocked.json' -Pass $false -Blocked $true
    Assert-Equal -Label 'a blocked verdict exits 1' -Expected 1 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$wearBlocked"))

    $phoneStale = New-Verdict -Name 'phone-stale.json' -Written $recordedAt.AddMinutes(-5)
    Assert-Equal -Label 'a verdict file older than the judged group exits 1' -Expected 1 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phoneStale, '-WearVerdicts', "$small,$large"))

    Assert-Equal -Label 'a missing watch verdict exits 2' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$(Join-Path $root 'absent.json')"))

    Assert-Equal -Label '-NoWear with a reason and a passing phone verdict exits 0' -Expected 0 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-NoWear', '-NoWearReason', 'phone-only hotfix'))

    Assert-Equal -Label '-NoWear without a reason is refused' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-NoWear'))

    Assert-Equal -Label 'no watch verdict and no -NoWear is refused' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone))

    # S4498: the re-record the procedure orders after a moved compare is newer than every verdict.
    # Only docs moved, so recordedAt and docs.since are new and both judged groups kept their stamp.
    $later = $recordedAt.AddHours(2)
    Write-Records -At $later -Since @{ docs = $later; 'phone-src' = $recordedAt; 'wear-src' = $recordedAt }
    Assert-Equal -Label 'S4498 a re-record that moved only a non-judged group leaves both verdicts current' -Expected 0 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$large"))

    Write-Records -At $later -Since @{ 'wear-src' = $later; 'phone-src' = $recordedAt }
    Assert-Equal -Label 'S4498 a moved wear-src stales the watch verdicts' -Expected 1 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$large"))
    Assert-Equal -Label 'S4498 a moved wear-src leaves the phone verdict current' -Expected 0 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-NoWear', '-NoWearReason', 'phone-only check'))

    Write-Records -At $later -Since @{ 'phone-src' = $later; 'wear-src' = $recordedAt }
    Assert-Equal -Label 'S4498 a moved phone-src stales the phone verdict' -Expected 1 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-NoWear', '-NoWearReason', 'phone-only check'))
    $fresh = New-Verdict -Name 'phone-fresh.json' -Written $later.AddMinutes(5)
    Assert-Equal -Label 'S4498 a verdict rewritten after the move is current again' -Expected 0 `
        -Actual (Invoke-Check @('-PhoneVerdict', $fresh, '-NoWear', '-NoWearReason', 'phone-only check'))
    Assert-Equal -Label 'S4498 a moved phone-src leaves the watch verdicts current' -Expected 0 `
        -Actual (Invoke-Check @('-PhoneVerdict', $fresh, '-WearVerdicts', "$small,$large"))

    Write-Records -At $later -NoSince
    Assert-Equal -Label 'S4498 a record with no since falls back to recordedAt and stales an older verdict' -Expected 1 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$large"))

    Write-Records -At $later -DropGroup @('wear-src')
    Assert-Equal -Label 'S4498 a record that does not carry a judged group exits 2' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$large"))

    Write-Records
    Write-Groups -WithVerdictGroups $false
    Assert-Equal -Label 'S4498 a groups file with no verdictGroups exits 2' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$large"))

    Write-Groups
    Remove-Item -LiteralPath (Join-Path $root 'temp\RELEASE-FREEZE-FINGERPRINTS-WEAR.json') -Force
    Assert-Equal -Label 'a scope with no fingerprint record exits 2' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$large"))
}
finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "assert-sweep-verdicts.tests: $(if ($script:failed -gt 0) { 'FAIL' } else { 'PASS' }) (run=$($script:passed + $script:failed) skipped=0)"
if ($script:failed -gt 0) { exit 1 }
exit 0
