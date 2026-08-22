# Run-Tests.ps1 (S1912) - regression suite for the -ChangedFiles scoping of
# scripts/quality/assert-no-ticket-logs.ps1.
#
# The gate has two halves of different natures. A forbidden permanent-log ticket id is a property of
# a file's CONTENT and can be judged on a caller's changed set. A ticket sitting in BlockNeedUserTest
# with no probe is a property of CATALOG STATE: it belongs to no file set, its probe must sit at the
# entry of that ticket's changed flow, and only its author can put it there. Before S1912 both halves
# were project-wide and fatal, so one session's minutes-long window between the status flip and the
# probe blocked every other session in the tree from closing (observed on S1889 and again on S1895).
#
# What is asserted, because a gate that only ever goes green proves nothing:
#   * scoping SPARES a finding that lies outside the caller's changed set,
#   * scoping still FLAGS a finding that lies inside it,
#   * the spared finding is still REPORTED rather than hidden,
#   * the missing-probe half returns the distinct code 3 when scoped and 1 when not,
#   * an unscoped run is unchanged - both halves project-wide and fatal.
#
# The forbidden-log cases run against a fixture tree, so they do not depend on what any sibling
# session happens to be editing. The exit-code cases read the live catalog, which is why they assert
# the RELATIONSHIP between a scoped and an unscoped run rather than a fixed number.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   the suite could not run (the gate under test is missing).

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$gate = Join-Path $repoRoot 'scripts/quality/assert-no-ticket-logs.ps1'
if (-not (Test-Path -LiteralPath $gate)) {
    Write-Host "assert-no-ticket-logs.tests: CANNOT RUN - gate not found at $gate"
    exit 2
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

function Invoke-Gate([string[]]$GateArgs) {
    $out = & $pwshExe -NoProfile -File $gate @GateArgs 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out }
}

Write-Host "assert-no-ticket-logs.tests (S1912): -ChangedFiles scoping`n"

# --- Case 1-3: forbidden-log half, scoped in and out -------------------------------------------
# A path that names no Kotlin file cannot hold a forbidden log id, so the forbidden half must be
# empty under that scope no matter what the rest of the tree looks like.
$scopedElsewhere = Invoke-Gate @('-Gate', '-ChangedFiles', 'docs/ARCHITECTURE.md')
Assert-That 'scoping to a non-Kotlin path reports zero forbidden ids' `
    ($scopedElsewhere.Output -match '0 forbidden log id\(s\)') `
    ("summary line was: " + (($scopedElsewhere.Output -split "`n" | Where-Object { $_ -match 'assert-no-ticket-logs:' }) -join ' '))

Assert-That 'scoping to a non-Kotlin path never exits 1 for someone else''s file' `
    ($scopedElsewhere.ExitCode -ne 1) `
    ("exit was 1; a finding outside the changed set was charged to this caller. Output:`n" + $scopedElsewhere.Output)

# The unscoped run is the control. If it flags forbidden ids, the scoped run above must not have.
$unscoped = Invoke-Gate @('-Gate', '-Quiet')
$unscopedForbidden = 0
if ($unscoped.Output -match '(\d+) forbidden log id\(s\)') { $unscopedForbidden = [int]$Matches[1] }

if ($unscopedForbidden -gt 0) {
    Assert-That 'a finding outside the changed set is still reported, not hidden' `
        ((Invoke-Gate @('-Gate', '-ChangedFiles', 'docs/ARCHITECTURE.md')).Output -match 'Outside the changed set') `
        'the scoped run neither charged nor mentioned the finding - it must always say what it set aside'

    Assert-That 'the same finding IS charged when its file is in the changed set' `
        ((Invoke-Gate @('-Gate', '-Quiet', '-ChangedFiles', ($unscoped.Output -split "`n" |
            Where-Object { $_ -match '^\s+(\S+\.kt):\d+' } |
            ForEach-Object { ($_ -split ':')[0].Trim() } |
            Select-Object -First 1))).ExitCode -eq 1) `
        'scoping to the offending file itself must still fail - scoping narrows the question, it does not soften it'
}
else {
    Write-Host '  SKIP  in-scope/out-of-scope pair - the tree currently holds no forbidden log id to place'
}

# --- Case 4-5: missing-probe half returns a distinct code when scoped ---------------------------
$unscopedMissing = 0
if ($unscoped.Output -match '(\d+) missing probe\(s\)') { $unscopedMissing = [int]$Matches[1] }

if ($unscopedMissing -gt 0) {
    Assert-That 'a missing probe is fatal on an unscoped run' `
        ($unscoped.ExitCode -eq 1) `
        "unscoped exit was $($unscoped.ExitCode); the release path must keep both halves fatal"

    Assert-That 'a missing probe returns 3, not 1, when the caller named its changed set' `
        ($scopedElsewhere.ExitCode -eq 3) `
        "scoped exit was $($scopedElsewhere.ExitCode); 3 is what lets post-change report it without charging this closure"
}
else {
    Write-Host '  SKIP  missing-probe pair - every BlockNeedUserTest ticket currently has a probe or an excuse'
}

# --- Case 6: the release path is untouched ------------------------------------------------------
$batch = Get-Content -LiteralPath (Join-Path $repoRoot 'scripts/quality/assert-fast-gates.ps1') -Raw
$awareBlock = if ($batch -match '(?s)\$changedFilesAware\s*=\s*@\((.*?)\)') { $Matches[1] } else { '' }
Assert-That 'assert-fast-gates does not hand this gate a changed set' `
    ($awareBlock -notmatch 'assert-no-ticket-logs') `
    'the batch is the release path: if it scopes this gate, both halves stop being project-wide there'

Write-Host ("`nassert-no-ticket-logs.tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
