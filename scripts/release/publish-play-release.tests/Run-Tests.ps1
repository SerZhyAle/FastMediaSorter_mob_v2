# Run-Tests.ps1 (S2346) - regression suite for the exit-code contract of the Play AAB uploader.
#
# Subject: scripts/release/publish-play-release.py, scripts/release/publish-play-release.ps1
#
# Why this suite exists at all: the behaviour it guards cannot be provoked. A 503 arrives when Google
# decides, and this script's own operation is one-way - it publishes a release - so the fix cannot be
# rehearsed against the live API even once. Without a deterministic check the only proof available is
# the next failed release, which is exactly how the sibling publisher's copy of this defect survived
# until S2345.
#
# What is asserted, because a suite that only ever goes green proves nothing:
#   * a 5xx and a rate limit are transient; a 400 and a 403 are NOT - and the 403 case is load-bearing
#     rather than symmetric, because Play's Foreground-service-permissions gate answers 403 and the
#     release flow has to keep reading it as "the owner must declare this", not as "try again later",
#   * a socket-level failure is transient and a plain ValueError is not,
#   * an exception carrying no `resp` answers False instead of raising a second exception from inside
#     the handler that is describing the first,
#   * EVERY bare .execute() belongs to a commit, and NO retried .execute() does. Both halves are the
#     point of this ticket: the idempotent calls must ask for the library's backoff, and the one-way
#     commit must not, because a retry after a lost response arrives at an edit Play already accepted
#     and returns 4xx - a rejected release that never happened,
#   * the bundle upload asks next_chunk for the retry rather than looping by hand,
#   * the PowerShell wrapper propagates 2 rather than collapsing it into 1.
#
# The classifier cases run in-process against the real module and real googleapiclient types, so they
# test the shipped code rather than a copy of its logic. The module is loaded through importlib
# because its file name carries hyphens and cannot be imported by name.
#
# Usage:  pwsh -NoProfile -File scripts/release/publish-play-release.tests/Run-Tests.ps1
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
$pyScript = Join-Path $repoRoot 'scripts/release/publish-play-release.py'
$ps1Script = Join-Path $repoRoot 'scripts/release/publish-play-release.ps1'
$venvPython = Join-Path $repoRoot '.venv/Scripts/python.exe'

foreach ($required in @($pyScript, $ps1Script, $venvPython)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "publish-play-release.tests: CANNOT RUN - not found: $required"
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

Write-Host "publish-play-release.tests (S2346): transient-failure exit-code contract`n"

# --- Cases 1-13: the classifier, in-process against the shipped module ---------------------------
# Each line is "<label>=<True|False>"; the expectations below are compared against that map, so a
# renamed or deleted classifier surfaces as every case failing rather than as a silent skip.
$probe = @'
import importlib.util, socket, ssl, sys
import httplib2
from google.auth.exceptions import TransportError
from googleapiclient.errors import HttpError

spec = importlib.util.spec_from_file_location('play_release_publisher', sys.argv[1])
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

def http(status):
    return HttpError(httplib2.Response({'status': status}), b'{"error":{"message":"x"}}', uri='https://x')

cases = [
    ('http503', http(503)),
    ('http500', http(500)),
    ('http429', http(429)),
    ('http400', http(400)),
    # The Foreground-service-permissions gate answers 403. It names an owner action, so it must
    # stay a finding rather than become "could not verify".
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

$probeFile = Join-Path ([IO.Path]::GetTempPath()) ("s2346-classifier-probe-{0}.py" -f $PID)
try {
    Set-Content -LiteralPath $probeFile -Value $probe -Encoding utf8
    $probeOut = & $venvPython $probeFile $pyScript 2>&1 | Out-String
    $probeExit = $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $probeFile -Force -ErrorAction SilentlyContinue
}

if ($probeExit -ne 0) {
    Write-Host "publish-play-release.tests: CANNOT RUN - the classifier probe failed:`n$probeOut"
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

# The release's fault, an owner action, or not an API failure at all -> stays a finding.
foreach ($defect in @('http400', 'http403', 'valueerror', 'noresp')) {
    Assert-That "$defect is NOT transient" `
        ($observed.ContainsKey($defect) -and $observed[$defect] -eq 'False') `
        "expected: False | actual: $(if ($observed.ContainsKey($defect)) { $observed[$defect] } else { '<case absent>' })"
}

Assert-That 'the retry count is positive' `
    ($observed.ContainsKey('numretries') -and [int]$observed['numretries'] -gt 0) `
    "expected: > 0 | actual: $(if ($observed.ContainsKey('numretries')) { $observed['numretries'] } else { '<absent>' })"

# --- Cases 14-17: which calls ask for the retry, and which must not ------------------------------
# Comments are stripped and whitespace collapsed first: every call chain in this script is written
# across several lines, and the constants' own comments name .execute() in prose.
$pyCode = (Get-Content -LiteralPath $pyScript |
    Where-Object { $_ -notmatch '^\s*#' }) -join ' '
$pyCode = [regex]::Replace($pyCode, '\s+', ' ')

# A call chain runs from `service.edits()` to its `.execute(`, so the text between the two says
# which endpoint is being called. That is exact where a fixed-width window would not be.
$chains = @(foreach ($m in [regex]::Matches($pyCode, '\.execute\(')) {
    $start = $pyCode.LastIndexOf('service.edits()', $m.Index)
    [pscustomobject]@{
        IsCommit  = ($start -ge 0) -and ($pyCode.Substring($start, $m.Index - $start) -match '\.commit\(')
        IsRetried = $pyCode.Substring($m.Index) -match '^\.execute\(\s*num_retries='
        Resolved  = $start -ge 0
    }
})

$unresolved = @($chains | Where-Object { -not $_.Resolved })
Assert-That 'every .execute() call chain resolves to an edits() endpoint' `
    ($chains.Count -gt 0 -and $unresolved.Count -eq 0) `
    "expected: at least one chain, 0 unresolved | actual: $($chains.Count) chain(s), $($unresolved.Count) unresolved - a chain the suite cannot attribute is a chain it cannot judge"

$unretriedNonCommit = @($chains | Where-Object { -not $_.IsCommit -and -not $_.IsRetried })
Assert-That 'every idempotent call asks for the library retry' `
    ($unretriedNonCommit.Count -eq 0) `
    "expected: 0 | actual: $($unretriedNonCommit.Count) bare non-commit call(s) - each is one place the run still dies on the first hiccup"

$retriedCommit = @($chains | Where-Object { $_.IsCommit -and $_.IsRetried })
Assert-That 'no commit asks for the library retry' `
    ($retriedCommit.Count -eq 0) `
    "expected: 0 | actual: $($retriedCommit.Count) retried commit(s) - a repeat after a lost response hits an edit Play already took and answers 4xx, i.e. a rejected release that never happened"

$commitCount = @($chains | Where-Object { $_.IsCommit }).Count
Assert-That 'both commit call sites are still present' `
    ($commitCount -eq 2) `
    "expected: 2 (the automatic path and the changes-held fallback, S1989) | actual: $commitCount"

# --- Cases 18-19: the bundle upload leans on the library rather than on a hand-rolled loop --------
Assert-That 'the resumable upload asks next_chunk for the retry' `
    ($pyCode -match 'next_chunk\(\s*num_retries=') `
    'without it the upload has no retry at all once the hand-rolled loop is gone'

Assert-That 'the hand-rolled chunk loop is gone' `
    ($pyCode -notmatch 'chunk_error') `
    'it retried any exception five times with no pause, spending requests on payload Play had already rejected'

# --- Cases 20-21: the wrapper carries code 2 outward ---------------------------------------------
$ps1Code = Get-Content -LiteralPath $ps1Script -Raw

Assert-That 'the wrapper has a branch that exits 2' `
    ($ps1Code -match '(?m)^\s*exit 2\s*$') `
    'without it the Python distinction dies at the process boundary and /skill-release files a transient outage as a failed publication'

Assert-That 'the wrapper never throws on the uploader exit code' `
    ($ps1Code -notmatch '(?m)^\s*throw\s+"Google Play Console publication') `
    'throw under $ErrorActionPreference = Stop exits 1, which is the collapse this ticket removed'

Write-Host ("`npublish-play-release.tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
