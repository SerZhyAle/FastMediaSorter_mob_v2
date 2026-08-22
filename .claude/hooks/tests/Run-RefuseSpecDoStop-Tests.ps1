#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for .claude/hooks/refuse-spec-do-stop.ps1 - the Stop hook that keeps /spec-do endless.

.DESCRIPTION
    Every case drives the hook against a throwaway fixture tree (-RepoRoot) with a hand-built Stop
    payload on stdin, and asserts the verdict on stdout: a JSON block decision, or nothing at all.

    The cases that matter are the ALLOW ones. A hook that only ever refuses would hold every session
    in this working tree open forever, so "no marker", "another session's marker", "a stale marker"
    and "an idle wait in flight" are each pinned here.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$hook = Join-Path (Split-Path -Parent $PSScriptRoot) 'refuse-spec-do-stop.ps1'
if (-not (Test-Path -LiteralPath $hook)) {
    Write-Error "hook not found: $hook" -ErrorAction Continue
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$failures = 0
$root = Join-Path ([System.IO.Path]::GetTempPath()) ("spec-do-stop-tests-" + [guid]::NewGuid().ToString('n').Substring(0, 8))

function New-Fixture {
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $root 'temp') -Force | Out-Null
}

function Set-Marker([hashtable]$Body) {
    $token = $Body.token
    $path = Join-Path $root "temp/SPEC-DO.ACTIVE-$token.json"
    $Body | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $path -Encoding UTF8
}

function Set-WorkMarker([hashtable]$Body) {
    $path = Join-Path $root 'temp/SPEC-DO.WORK-test.json'
    $Body | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $path -Encoding UTF8
}

function Invoke-Hook([string]$SessionId) {
    $payload = @{ session_id = $SessionId; stop_hook_active = $false } | ConvertTo-Json -Compress
    return ($payload | & $pwshExe -NoProfile -File $hook -RepoRoot $root) -join ''
}

function Assert-Case([string]$Name, [string]$Actual, [bool]$ExpectBlock) {
    $blocked = $Actual -match '"decision"\s*:\s*"block"'
    if ($blocked -eq $ExpectBlock) {
        Write-Host "  PASS  $Name"
    }
    else {
        $script:failures++
        Write-Host "  FAIL  $Name - expected: block=$ExpectBlock | actual: block=$blocked ($Actual)"
    }
}

$now = Get-Date

# 1 - nothing armed: an ordinary session must always be able to finish.
New-Fixture
Assert-Case 'no marker allows the stop' (Invoke-Hook 'session-a') $false

# 2 - armed and unclaimed: the session ending a turn claims it and is refused.
New-Fixture
Set-Marker @{ token = 'aaa11111'; armedAt = $now.ToString('s'); sessionId = $null; blocks = 0 }
Assert-Case 'unclaimed marker blocks and is claimed' (Invoke-Hook 'session-a') $true
$claimed = Get-Content -LiteralPath (Join-Path $root 'temp/SPEC-DO.ACTIVE-aaa11111.json') -Raw | ConvertFrom-Json
if ($claimed.sessionId -ne 'session-a') { $failures++; Write-Host '  FAIL  claim writes the session id' } else { Write-Host '  PASS  claim writes the session id' }

# 3 - claimed by this session: still refused, and the rewrite keeps timestamps in ISO 's' shape
#     rather than the machine's locale format, which is what a round trip through ConvertFrom-Json
#     produces if the write-back is naive.
Assert-Case 'own claimed marker keeps blocking' (Invoke-Hook 'session-a') $true
# Assert on the raw JSON text, never on ConvertFrom-Json: PowerShell 7 revives an ISO-8601 string
# as a [datetime], so a parsed read reports the locale format whatever the file actually says.
$rewrittenRaw = Get-Content -LiteralPath (Join-Path $root 'temp/SPEC-DO.ACTIVE-aaa11111.json') -Raw
if ($rewrittenRaw -match '"armedAt":\s*"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"') {
    Write-Host '  PASS  armedAt survives the rewrite in ISO shape'
} else {
    $failures++; Write-Host "  FAIL  armedAt survives the rewrite in ISO shape - actual: $rewrittenRaw"
}

# 4 - claimed by another live session: this one is none of its business.
New-Fixture
Set-Marker @{ token = 'bbb22222'; armedAt = $now.ToString('s'); sessionId = 'session-b'; blocks = 3 }
Assert-Case "another session's marker allows the stop" (Invoke-Hook 'session-a') $false

# 5 - stale marker from a killed session: purged, not inherited.
New-Fixture
Set-Marker @{ token = 'ccc33333'; armedAt = $now.AddHours(-48).ToString('s'); sessionId = 'session-dead'; blocks = 9 }
Assert-Case 'stale marker allows the stop' (Invoke-Hook 'session-a') $false
if (Test-Path -LiteralPath (Join-Path $root 'temp/SPEC-DO.ACTIVE-ccc33333.json')) {
    $failures++; Write-Host '  FAIL  stale marker is purged'
} else { Write-Host '  PASS  stale marker is purged' }

# 6 - a live idle waiter allows the stop: blocking here would spin instead of wait.
New-Fixture
Set-Marker @{ token = 'ddd44444'; armedAt = $now.ToString('s'); sessionId = 'session-a'; blocks = 1 }
Set-WorkMarker @{ outcome = 'waiting'; checkedAt = $now.ToString('s') }
Assert-Case 'live idle waiter allows the stop' (Invoke-Hook 'session-a') $false

# 7 - a dead waiter does not: a wait nobody is running is not a wait.
Set-WorkMarker @{ outcome = 'waiting'; checkedAt = $now.AddHours(-3).ToString('s') }
Assert-Case 'stale waiter marker resumes blocking' (Invoke-Hook 'session-a') $true

# 8 - a finished waiter does not either: work is available, so the loop owes a round.
Set-WorkMarker @{ outcome = 'work'; checkedAt = $now.ToString('s') }
Assert-Case 'finished waiter resumes blocking' (Invoke-Hook 'session-a') $true

if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }

if ($failures -gt 0) {
    Write-Host "refuse-spec-do-stop tests: expected: 0 | actual: $failures failure(s)"
    exit 1
}
Write-Host 'refuse-spec-do-stop tests: PASS'
exit 0
