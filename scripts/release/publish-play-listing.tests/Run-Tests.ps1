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

# --- Cases 13-18: package and listing root (S4009) -----------------------------------------------
# main() runs in-process against a fake service that records every endpoint call, over a temporary
# three-locale root shaped like the watch face's tree.
$rootProbe = @'
import importlib.util, os, sys, tempfile

spec = importlib.util.spec_from_file_location('play_listing_publisher', sys.argv[1])
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class Call:
    def __init__(self, recorder, group, name, kwargs, result):
        recorder.append((group, name, kwargs))
        self._result = result
    def execute(self, **_):
        return self._result

class Endpoint:
    def __init__(self, recorder, group, results):
        self._recorder, self._group, self._results = recorder, group, results
    def __getattr__(self, name):
        return lambda **kw: Call(self._recorder, self._group, name, kw, self._results.get(name, {}))

class Edits(Endpoint):
    def listings(self):
        return Endpoint(self._recorder, 'listings', {})
    def images(self):
        return Endpoint(self._recorder, 'images', {})

class Service:
    def __init__(self, recorder):
        self._recorder = recorder
    def edits(self):
        return Edits(self._recorder, 'edits', {'insert': {'id': 'e1'}})

def run(argv):
    recorder = []
    mod.service_account.Credentials.from_service_account_file = staticmethod(lambda *a, **k: None)
    mod.build = lambda *a, **k: Service(recorder)
    mod.MediaFileUpload = lambda path, mimetype: path
    sys.argv = ['publish-play-listing.py'] + argv
    code = 0
    try:
        mod.main()
    except SystemExit as exc:
        code = exc.code or 0
    return code, recorder

root = tempfile.mkdtemp()
for folder in ('en-US', 'ru-RU', 'uk-UA'):
    base = os.path.join(root, folder)
    os.makedirs(os.path.join(base, 'images', 'wearScreenshots'))
    for name, text in (('title.txt', 'Face'), ('short_description.txt', 'Short'),
                       ('full_description.txt', 'Full')):
        with open(os.path.join(base, name), 'w', encoding='utf-8') as f:
            f.write(text)
    open(os.path.join(base, 'images', 'icon.png'), 'wb').close()
    open(os.path.join(base, 'images', 'wearScreenshots', '01.png'), 'wb').close()

defaults = mod.parse_args(['validate'])
locales, _ = mod.resolve_locales(defaults['listing_root'])
print('defaultpackage=%s' % defaults['package_name'])
print('defaultlocales=%d' % len(locales))
print('declaredlocales=%d' % len(mod.LOCALES))

code, calls = run(['validate', '--package', 'probe.other.app', '--listing-root', root])
print('rootexit=%d' % code)
print('rootpackages=%s' % ','.join(sorted({str(k.get('packageName')) for _, _, k in calls})))
print('rootlanguages=%s' % ','.join(sorted({k['language'] for g, n, k in calls if g == 'listings' and n == 'update'})))
print('rootdeletes=%d' % len([1 for g, n, _ in calls if g == 'listings' and n.startswith('delete')]))
print('roottypes=%s' % ','.join(sorted({k['imageType'] for g, n, k in calls if g == 'images' and n == 'deleteall'})))

os.makedirs(os.path.join(root, 'xx-QQ'))
code, calls = run(['validate', '--package', 'probe.other.app', '--listing-root', root])
print('unknownexit=%d' % code)
print('unknowncalls=%d' % len(calls))
'@

$rootProbeFile = Join-Path ([IO.Path]::GetTempPath()) ("s4009-listing-probe-{0}.py" -f $PID)
try {
    Set-Content -LiteralPath $rootProbeFile -Value $rootProbe -Encoding utf8
    $rootOut = & $venvPython $rootProbeFile $pyScript 2>&1 | Out-String
    $rootExit = $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $rootProbeFile -Force -ErrorAction SilentlyContinue
}
$lr = @{}
foreach ($line in ($rootOut -split "`r?`n")) {
    if ($line -match '^\s*([a-z]+)=(.*?)\s*$') { $lr[$Matches[1]] = $Matches[2] }
}
function Get-RootObservation([string]$key) {
    if ($lr.ContainsKey($key)) { return $lr[$key] }
    return "<absent; probe exit $rootExit>"
}

Assert-That 'the default package is the declared phone package' `
    ((Get-RootObservation 'defaultpackage') -eq 'com.sza.fastmediasorter') `
    "expected: com.sza.fastmediasorter | actual: $(Get-RootObservation 'defaultpackage')"

Assert-That 'the default root still publishes every declared locale' `
    ((Get-RootObservation 'defaultlocales') -eq (Get-RootObservation 'declaredlocales') -and (Get-RootObservation 'defaultlocales') -match '^\d+$') `
    "expected: $(Get-RootObservation 'declaredlocales') | actual: $(Get-RootObservation 'defaultlocales')"

Assert-That 'a three-locale root publishes to the given package only' `
    ((Get-RootObservation 'rootexit') -eq '0' -and (Get-RootObservation 'rootpackages') -eq 'probe.other.app') `
    "expected: exit 0, packages probe.other.app | actual: exit $(Get-RootObservation 'rootexit'), packages $(Get-RootObservation 'rootpackages')"

Assert-That 'a three-locale root updates exactly its three Play languages' `
    ((Get-RootObservation 'rootlanguages') -eq 'en-US,ru-RU,uk') `
    "expected: en-US,ru-RU,uk | actual: $(Get-RootObservation 'rootlanguages')"

Assert-That 'no listing is ever deleted and only present image slots are replaced' `
    ((Get-RootObservation 'rootdeletes') -eq '0' -and (Get-RootObservation 'roottypes') -eq 'icon,wearScreenshots') `
    "expected: 0 listing deletes, slots icon,wearScreenshots | actual: $(Get-RootObservation 'rootdeletes') deletes, slots $(Get-RootObservation 'roottypes')"

Assert-That 'a folder with no Play language exits 1 before any API call' `
    ((Get-RootObservation 'unknownexit') -eq '1' -and (Get-RootObservation 'unknowncalls') -eq '0') `
    "expected: exit 1, 0 calls | actual: exit $(Get-RootObservation 'unknownexit'), $(Get-RootObservation 'unknowncalls') calls"

Write-Host ("`npublish-play-listing.tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
