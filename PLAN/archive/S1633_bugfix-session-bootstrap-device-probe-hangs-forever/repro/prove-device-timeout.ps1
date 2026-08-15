<#
.SYNOPSIS
    S1633 reproducer - prove the device block is now time-bounded and degrades.

.DESCRIPTION
    The bug is a probe that never returns, so the honest test needs a probe that never
    returns. Driving the real device-ready.ps1 into that state would mean killing the adb
    server on a machine other sessions share, so this harness stubs the probe instead.

    It copies scripts/spec_catalog/session-bootstrap.ps1 into temp/S1633/ and rewrites exactly
    three lines - the paths of the session, selection and device components - so the copy runs
    the REAL block logic (Invoke-ComponentBounded, the timeout, the degrade) against stubs. The
    diff is printed before the run so the three substitutions can be seen, not trusted.

    The device stub sleeps far past the bound; the session and selection stubs answer instantly
    with well-formed JSON.

    Expected: the package returns in about -TimeoutSec seconds with
      device.status   = failed
      device.exitCode = 124
      selection       = still present
    Before the fix the same shape ran 2613 s and printed nothing.

    Exit codes:
      0 - the package returned inside the bound and the device block degraded as specified.
      1 - it did not: the verdict text says which expectation failed.
      2 - the harness could not run (source script missing).
#>
param(
    [string] $SourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path,
    [ValidateRange(5, 60)][int] $TimeoutSec = 5
)

$ErrorActionPreference = 'Stop'

$original = Join-Path $SourceRoot 'scripts\spec_catalog\session-bootstrap.ps1'
if (-not (Test-Path -LiteralPath $original)) {
    Write-Error "prove-device-timeout: source not found: $original" -ErrorAction Continue
    exit 2
}

$sandbox = Join-Path $SourceRoot 'temp\S1633\sandbox'
if (Test-Path $sandbox) { Remove-Item $sandbox -Recurse -Force }
New-Item -ItemType Directory -Force -Path $sandbox | Out-Null

$slowProbe = Join-Path $sandbox 'stub-device-hangs.ps1'
@'
# Never answers inside any sane bound - this is the failure S1633 is about.
Start-Sleep -Seconds 600
exit 0
'@ | Set-Content -LiteralPath $slowProbe -Encoding UTF8

$sessionStub = Join-Path $sandbox 'stub-session.ps1'
@'
param([Parameter(ValueFromRemainingArguments = $true)] $Rest)
Write-Output '{"sessionId":"stub","stub":true}'
exit 0
'@ | Set-Content -LiteralPath $sessionStub -Encoding UTF8

$selectionStub = Join-Path $sandbox 'stub-selection.ps1'
@'
param([Parameter(ValueFromRemainingArguments = $true)] $Rest)
Write-Output '{"ranked":[],"selected":null,"selected_none_reason":"queue-exhausted","stub":true}'
exit 0
'@ | Set-Content -LiteralPath $selectionStub -Encoding UTF8

$leaseStub = Join-Path $sandbox 'stub-lease.ps1'
@'
param([Parameter(ValueFromRemainingArguments = $true)] $Rest)
exit 0
'@ | Set-Content -LiteralPath $leaseStub -Encoding UTF8

$copy = Join-Path $sandbox 'session-bootstrap.copy.ps1'
$text = [System.IO.File]::ReadAllText($original)
# The copy lives outside scripts/spec_catalog/, so the two $PSScriptRoot-relative resolutions are
# re-anchored to the real repo as well - they are plumbing, not the logic under test.
$patched = $text `
    -replace "(?m)^\. \(Join-Path \`$PSScriptRoot .*$", ". '$(Join-Path $SourceRoot 'scripts\utils\agent-lock.ps1')'" `
    -replace "(?m)^\`$root = .*$",            "`$root = '$SourceRoot'" `
    -replace "(?m)^\`$sessionScript = .*$",   "`$sessionScript = '$sessionStub'" `
    -replace "(?m)^\`$selectionScript = .*$", "`$selectionScript = '$selectionStub'" `
    -replace "(?m)^\`$leaseScript = .*$",     "`$leaseScript = '$leaseStub'" `
    -replace "(?m)^\`$deviceScript = .*$",    "`$deviceScript = '$slowProbe'"
[System.IO.File]::WriteAllText($copy, $patched)

Write-Host 'Substituted lines (the only differences from the shipped script):'
Select-String -LiteralPath $copy -Pattern '^(\$root = |\$(session|selection|lease|device)Script = |\. .*agent-lock)' |
    ForEach-Object { Write-Host "  $($_.Line)" }
Write-Host ''

$started = [System.Diagnostics.Stopwatch]::StartNew()
$raw = & pwsh -NoProfile -File $copy -DeviceTimeoutSec $TimeoutSec 2>&1 | Out-String
$packageExit = $LASTEXITCODE
$started.Stop()
$elapsed = [math]::Round($started.Elapsed.TotalSeconds, 1)

Write-Host "package exit=$packageExit after ${elapsed}s"
Write-Host $raw.Trim()
Write-Host ''

$payload = $null
foreach ($line in ($raw -split "`r?`n")) {
    $t = $line.Trim()
    if ($t.StartsWith('{')) { try { $payload = $t | ConvertFrom-Json } catch { continue } }
}

$problems = New-Object System.Collections.Generic.List[string]
if (-not $payload) { $problems.Add('the package printed no parseable JSON payload') }
else {
    if ($payload.device.status -ne 'failed') { $problems.Add("device.status expected 'failed', actual '$($payload.device.status)'") }
    if ($payload.device.exitCode -ne 124) { $problems.Add("device.exitCode expected 124, actual '$($payload.device.exitCode)'") }
    if (-not $payload.selection) { $problems.Add('selection block absent - the round could not start') }
}
# Generous ceiling: the bound plus process startup, still three orders below the 2613 s hang.
$ceiling = $TimeoutSec + 30
if ($elapsed -gt $ceiling) { $problems.Add("elapsed ${elapsed}s exceeded the ${ceiling}s ceiling") }

Remove-Item $sandbox -Recurse -Force -ErrorAction SilentlyContinue

if ($problems.Count -gt 0) {
    Write-Host 'prove-device-timeout: FAIL'
    $problems | ForEach-Object { Write-Host "  $_" }
    exit 1
}

Write-Host "prove-device-timeout: PASS - bounded at ${TimeoutSec}s, returned in ${elapsed}s, device block degraded to failed/124, selection intact."
exit 0
