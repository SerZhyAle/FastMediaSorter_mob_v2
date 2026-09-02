# Run-Tests.ps1 (S2345) - regression suite for the exit-code contract of the Play listing publisher.
#
# Subject: scripts/release/publish-play-listing.py, scripts/release/publish-play-listing.ps1
#
# Why this suite exists at all: the behaviour it guards cannot be provoked. A 503 arrives from Google
# when Google decides, so without a deterministic check the fix is verifiable only by waiting for the
# next outage - which is how the defect survived in the first place. Two clean runs on 2026-09-02
# reported exit 1, the code this repository reads as "found a defect", for a listing with nothing
# wrong in it.
#
# What is asserted, because a suite that only ever goes green proves nothing:
#   * a 5xx and a rate limit are transient; a 400 and a 403 are NOT (the narrow boundary is the
#     whole point - demoting rejected payload to "could not verify" would hide a real defect),
#   * a socket-level failure is transient and a plain ValueError is not,
#   * an exception carrying no `resp` answers False instead of raising a second exception from
#     inside the handler that is describing the first,
#   * every .execute() call asks for the retry - one bare call is one place the transaction still
#     dies on the first hiccup,
#   * the PowerShell wrapper propagates 2 rather than collapsing it into 1.
#
# The classifier cases run in-process against the real module and real googleapiclient types, so they
# test the shipped code rather than a copy of its logic. The module is loaded through importlib
# because its file name carries hyphens and cannot be imported by name.
#
# Usage:  pwsh -NoProfile -File scripts/release/publish-play-listing.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   the suite could not run (a subject is missing, or the project virtual environment is absent).

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pyScript = Join-Path $repoRoot 'scripts/release/publish-play-listing.py'
$ps1Script = Join-Path $repoRoot 'scripts/release/publish-play-listing.ps1'
$venvPython = Join-Path $repoRoot '.venv/Scripts/python.exe'

foreach ($required in @($pyScript, $ps1Script, $venvPython)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "publish-play-listing.tests: CANNOT RUN - not found: $required"
        exit 2
    }
}

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        $script:pass++
        Write-Host ("  PASS  {0}" -f $name)
    }
    else {
        $script:fail++
        Write-Host ("  FAIL  {0}`n          {1}" -f $name, $detail)
    }
}

Write-Host "publish-play-listing.tests (S2345): transient-failure exit-code contract`n"

# --- Cases 1-9: the classifier, in-process against the shipped module ---------------------------
# Each line is "<label>=<True|False>"; the expectations below are compared against that map, so a
# renamed or deleted classifier surfaces as every case failing rather than as a silent skip.
$probe = @'
import importlib.util, os, socket, ssl, sys
import httplib2
from google.auth.exceptions import TransportError
from googleapiclient.errors import HttpError

spec = importlib.util.spec_from_file_location('play_listing_publisher', sys.argv[1])
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

def http(status):
    return HttpError(httplib2.Response({'status': status}), b'{"error":{"message":"x"}}', uri='https://x')

cases = [
    ('http503', http(503)),
    ('http500', http(500)),
    ('http429', http(429)),
    ('http400', http(400)),
    ('http403', http(403)),
    ('sockettimeout', socket.timeout()),
    ('connreset', ConnectionResetError()),
    ('sslerror', ssl.SSLError()),
    # Neither of these derives from OSError, so each needs its own name in the classifier.
    ('dnsfailure', httplib2.ServerNotFoundError('unable to find the server')),
    ('authtransport', TransportError('token endpoint unreachable')),
    ('valueerror', ValueError('boom')),
    ('noresp', Exception('bare')),
]
for label, exc in cases:
    print('%s=%s' % (label, mod._is_transient(exc)))
print('numretries=%d' % mod.API_NUM_RETRIES)
'@

$probeFile = Join-Path ([IO.Path]::GetTempPath()) ("s2345-classifier-probe-{0}.py" -f $PID)
try {
    Set-Content -LiteralPath $probeFile -Value $probe -Encoding utf8
    $probeOut = & $venvPython $probeFile $pyScript 2>&1 | Out-String
    $probeExit = $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $probeFile -Force -ErrorAction SilentlyContinue
}

if ($probeExit -ne 0) {
    Write-Host "publish-play-listing.tests: CANNOT RUN - the classifier probe failed:`n$probeOut"
    exit 2
}

$observed = @{}
foreach ($line in ($probeOut -split "`r?`n")) {
    if ($line -match '^\s*([a-z0-9]+)=(.+?)\s*$') { $observed[$Matches[1]] = $Matches[2] }
}

# Google's or the network's fault -> "could not verify".
foreach ($transient in @('http503', 'http500', 'http429', 'sockettimeout', 'connreset', 'sslerror',
        'dnsfailure', 'authtransport')) {
    Assert-That "$transient is transient" `
        ($observed.ContainsKey($transient) -and $observed[$transient] -eq 'True') `
        "expected: True | actual: $(if ($observed.ContainsKey($transient)) { $observed[$transient] } else { '<case absent>' })"
}

# The listing's fault, or not an API failure at all -> stays a defect.
foreach ($defect in @('http400', 'http403', 'valueerror', 'noresp')) {
    Assert-That "$defect is NOT transient" `
        ($observed.ContainsKey($defect) -and $observed[$defect] -eq 'False') `
        "expected: False | actual: $(if ($observed.ContainsKey($defect)) { $observed[$defect] } else { '<case absent>' })"
}

Assert-That 'the retry count is positive' `
    ($observed.ContainsKey('numretries') -and [int]$observed['numretries'] -gt 0) `
    "expected: > 0 | actual: $(if ($observed.ContainsKey('numretries')) { $observed['numretries'] } else { '<absent>' })"

# --- Case 10: no API call is left without the retry ---------------------------------------------
# Comment lines are stripped first: the constant's own comment names .execute() in prose and would
# otherwise read as an unretried call.
$pyCode = (Get-Content -LiteralPath $pyScript |
    Where-Object { $_ -notmatch '^\s*#' }) -join "`n"
$bareExecute = [regex]::Matches($pyCode, '\.execute\(\s*\)').Count
$totalExecute = [regex]::Matches($pyCode, '\.execute\(').Count
$retriedExecute = [regex]::Matches($pyCode, '\.execute\(num_retries=').Count

Assert-That 'no .execute() call is left without num_retries' `
    ($bareExecute -eq 0 -and $totalExecute -eq $retriedExecute) `
    "expected: 0 bare, total == retried | actual: bare=$bareExecute total=$totalExecute retried=$retriedExecute"

# --- Cases 11-12: the wrapper carries code 2 outward ---------------------------------------------
$ps1Code = Get-Content -LiteralPath $ps1Script -Raw

Assert-That 'the wrapper has a branch that exits 2' `
    ($ps1Code -match '(?m)^\s*exit 2\s*$') `
    'without it the Python distinction dies at the process boundary and no caller can see it'

Assert-That 'the wrapper never throws on the uploader exit code' `
    ($ps1Code -notmatch '(?m)^\s*throw\s+"Google Play listing') `
    'throw under $ErrorActionPreference = Stop exits 1, which is the collapse this ticket removed'

Write-Host ("`npublish-play-listing.tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
