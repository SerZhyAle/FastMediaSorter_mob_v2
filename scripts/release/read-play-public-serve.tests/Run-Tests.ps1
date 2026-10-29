#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/release/read-play-public-serve.ps1 (S4463).

.DESCRIPTION
    Every case writes a small fixture page to a per-PID temp directory and passes it with -HtmlPath,
    so no case touches the network. The fixtures reproduce the shapes the real listing page has
    carried: the version under data-callback key 141, a version-shaped node without the key, and the
    page of 2026-10-27 that carries no version at all but still carries four noise strings that look
    like one (a server stamp, two feature-rollout versions, an SVG path fragment).

    Asserted because a suite that only goes green proves nothing:
      * a page with no version and a readable stamp is exit 0 with a null version - not a refusal,
      * noise strings are never promoted to a version,
      * two version-shaped nodes and a page with neither version nor stamp stay exit 2,
      * -RequireUpdatedAfter is strictly-after to the second, a date alone means the end of that day,
        and a value or page it cannot evaluate is exit 2 rather than a silent pass.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'read-play-public-serve.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Host "read-play-public-serve.tests: FAIL (run=0 skipped=0) - not found: $script" -ForegroundColor Red
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$root = Join-Path ([System.IO.Path]::GetTempPath()) "play-public-serve-tests-$PID"
Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $root -Force | Out-Null

$noise = 'a"0.0.0.0"b,[[["AAkuc69f"]]],"2.60.7042.357",null,[[["vaf_a",[2]]]],"2.60.3020.341",null,' +
    'c0 .24.04.47.09.7L8.04 9.81C7.5'
$stamp = '"146":[["Oct 21, 2026",[1792541532,297000000]]],"155":[null,[null,null,5]]'
$dateOnlyStamp = '"146":[["Oct 21, 2026"]],"155":[null]'
$keyedVersion = '"141":[[["2.61.0202.359"]]],"145":[null,"notes"],'
$emptyKey = '"141":[[null,[]],[[[36]],[null,[]]]],"145":[null,"notes"],'

function Invoke-Reader {
    param([string] $Page, [string[]] $ReaderArgs = @())
    $path = Join-Path $root ("page-{0}.html" -f [guid]::NewGuid().ToString('N'))
    [System.IO.File]::WriteAllText($path, $Page)
    $lines = @(& $pwshExe -NoProfile -File $script -HtmlPath $path @ReaderArgs 2>&1)
    $code = $LASTEXITCODE
    $stdout = ($lines | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] } | Out-String)
    return [pscustomobject]@{ Code = $code; Out = $stdout; All = ($lines | Out-String) }
}

# ConvertFrom-Json turns an ISO timestamp string into a DateTime before 7.5 offers -DateKind, so the
# JSON field is read from the text the reader printed.
function Get-JsonField {
    param([string] $Json, [string] $Key)
    $match = [regex]::Match($Json, '"' + [regex]::Escape($Key) + '":\s*(null|"([^"]*)")')
    if (-not $match.Success) { return '<absent>' }
    if ($match.Groups[1].Value -eq 'null') { return '' }
    return $match.Groups[2].Value
}

$script:failures = 0
$script:caseCount = 0
function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    $script:caseCount++
    try {
        $message = & $Body
        if ($message) { Write-Host "FAIL  $Name - $message" -ForegroundColor Red; $script:failures++ }
        else { Write-Host "PASS  $Name" -ForegroundColor Green }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

function Test-Json {
    param($Run, [hashtable] $Expect)
    if ($Run.Code -ne 0) { return "exit $($Run.Code), expected 0 - $($Run.All)" }
    foreach ($key in $Expect.Keys) {
        $actual = Get-JsonField -Json $Run.Out -Key $key
        if ($actual -ne "$($Expect[$key])") { return "$key is '$actual', expected '$($Expect[$key])'" }
    }
    return $null
}

Assert-Case 'version under data-callback key 141 is read' {
    $run = Invoke-Reader -Page ($keyedVersion + $noise + $stamp) -ReaderArgs @('-Json')
    Test-Json $run @{ servedVersion = '2.61.0202.359'; detectedBy = 'data-callback key 141'; updatedOn = 'Oct 21, 2026'; updatedAtUtc = '2026-10-21T00:12:12Z' }
}

Assert-Case 'version-shaped node without the key is read' {
    $run = Invoke-Reader -Page ($emptyKey + '[[["2.61.0202.359"]]],' + $noise + $stamp) -ReaderArgs @('-Json')
    Test-Json $run @{ servedVersion = '2.61.0202.359'; detectedBy = 'version-shaped data node' }
}

Assert-Case 'page of 2026-10-27 (noise only, stamp present) is exit 0 with a null version' {
    $run = Invoke-Reader -Page ($emptyKey + $noise + $stamp) -ReaderArgs @('-Json')
    $problem = Test-Json $run @{ servedVersion = ''; updatedOn = 'Oct 21, 2026'; updatedAtUtc = '2026-10-21T00:12:12Z' }
    if ($problem) { return $problem }
    $detectedBy = Get-JsonField -Json $run.Out -Key 'detectedBy'
    if ($detectedBy -notlike 'none*') { return "detectedBy is '$detectedBy', expected it to start with 'none'" }
    foreach ($candidate in '0.0.0.0', '2.60.7042.357', '2.60.3020.341', '24.04.47.09') {
        if ($run.Out -match [regex]::Escape($candidate)) { return "noise string $candidate leaked into the output" }
    }
}

Assert-Case 'human line names the missing version instead of printing one' {
    $run = Invoke-Reader -Page ($emptyKey + $noise + $stamp)
    if ($run.Code -ne 0) { return "exit $($run.Code), expected 0" }
    if ($run.Out -notmatch 'carries no version') { return "line is '$($run.Out.Trim())'" }
}

Assert-Case 'two version-shaped nodes are refused as ambiguous' {
    $run = Invoke-Reader -Page ('[[["2.61.0202.359"]]],[[["2.60.9260.042"]]],' + $noise + $stamp)
    if ($run.Code -ne 2) { return "exit $($run.Code), expected 2" }
    if ($run.All -notmatch 'ambiguous' -or $run.All -notmatch '2\.61\.0202\.359' -or $run.All -notmatch '2\.60\.9260\.042') {
        return "refusal does not name both candidates - $($run.All)"
    }
}

Assert-Case 'a page with neither version nor stamp is refused' {
    $run = Invoke-Reader -Page ($emptyKey + $noise)
    if ($run.Code -ne 2) { return "exit $($run.Code), expected 2" }
    if ($run.All -notmatch 'neither a version nor an update stamp') { return "refusal text - $($run.All)" }
}

Assert-Case 'a version with no stamp is still read' {
    $run = Invoke-Reader -Page ($keyedVersion + $noise) -ReaderArgs @('-Json')
    Test-Json $run @{ servedVersion = '2.61.0202.359'; updatedOn = ''; updatedAtUtc = '' }
}

Assert-Case 'a missing saved page is exit 2' {
    $out = & $pwshExe -NoProfile -File $script -HtmlPath (Join-Path $root 'absent.html') 2>&1 | Out-String
    if ($LASTEXITCODE -ne 2) { return "exit $LASTEXITCODE, expected 2 - $out" }
}

Assert-Case '-RequireVersionAbove: above passes, equal fails, no version cannot be evaluated' {
    $page = $keyedVersion + $noise + $stamp
    $above = Invoke-Reader -Page $page -ReaderArgs @('-RequireVersionAbove', '2.60.9260.042')
    if ($above.Code -ne 0) { return "above: exit $($above.Code), expected 0" }
    $equal = Invoke-Reader -Page $page -ReaderArgs @('-RequireVersionAbove', '2.61.0202.359')
    if ($equal.Code -ne 1) { return "equal: exit $($equal.Code), expected 1" }
    $none = Invoke-Reader -Page ($emptyKey + $noise + $stamp) -ReaderArgs @('-RequireVersionAbove', '2.60.9260.042')
    if ($none.Code -ne 2) { return "no version: exit $($none.Code), expected 2" }
    if ($none.All -notmatch 'RequireUpdatedAfter') { return 'no-version refusal does not point at -RequireUpdatedAfter' }
}

$boundaries = @(
    @{ Value = '2026-10-20T23:59:59Z'; Code = 0; Note = 'one second before the stamp' }
    @{ Value = '2026-10-21T00:12:11Z'; Code = 0; Note = 'one second before, to the second' }
    @{ Value = '2026-10-21T00:12:12Z'; Code = 1; Note = 'equal is not after' }
    @{ Value = '2026-10-21T00:12:13Z'; Code = 1; Note = 'later than the stamp' }
    @{ Value = '2026-10-21T03:00:00+03:00'; Code = 0; Note = 'an offset is honoured (equals 00:00Z)' }
    @{ Value = '2026-10-20'; Code = 0; Note = 'a date alone: update falls on a later day' }
    @{ Value = '2026-10-21'; Code = 1; Note = 'a date alone: update is on that same day' }
    @{ Value = 'not-a-date'; Code = 2; Note = 'unparseable value' }
)
foreach ($case in $boundaries) {
    Assert-Case "-RequireUpdatedAfter $($case.Value) -> exit $($case.Code) ($($case.Note))" {
        $run = Invoke-Reader -Page ($emptyKey + $noise + $stamp) -ReaderArgs @('-RequireUpdatedAfter', $case.Value)
        if ($run.Code -ne $case.Code) { return "exit $($run.Code), expected $($case.Code) - $($run.All)" }
    }
}

Assert-Case '-RequireUpdatedAfter on a date-only stamp cannot be evaluated' {
    $run = Invoke-Reader -Page ($emptyKey + $noise + $dateOnlyStamp) -ReaderArgs @('-RequireUpdatedAfter', '2026-10-20')
    if ($run.Code -ne 2) { return "exit $($run.Code), expected 2" }
    if ($run.All -notmatch 'no epoch update stamp') { return "refusal text - $($run.All)" }
}

Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue

if ($script:failures -gt 0) {
    Write-Host "read-play-public-serve.tests: FAIL ($($script:failures) of $($script:caseCount) cases)" -ForegroundColor Red
    exit 1
}
Write-Host "read-play-public-serve.tests: PASS (run=$($script:caseCount) skipped=0)" -ForegroundColor Green
exit 0
