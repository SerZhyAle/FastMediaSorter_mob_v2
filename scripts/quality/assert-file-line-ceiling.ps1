#requires -Version 7.0
<#
.SYNOPSIS
    Gate: a source file must not exceed the 1500-line ceiling of CLAUDE.md Rule 2 (S1270).

.DESCRIPTION
    Rule 2 has stated a 1500-line ceiling for a long time and nothing has ever measured it. Verified
    2026-08-21: no script under scripts/quality checks file length, and detekt's config carries
    LongMethod but no FileLength or LargeClass rule - so the ceiling was advice, not a limit. detekt
    would not have covered C++ in any case: it never sees a .cpp file.

    The cost of that was visible in one file. `app_v2/src/vr/cpp/xr_session.cpp` was captured at 2101
    lines on 2026-07-28 and measured at **2154** on 2026-08-21 - it grew by 53 lines while a ticket
    about its size sat open, because every VR ticket that touches it adds a little and none can
    reasonably be asked to split it.

    WHAT IT COUNTS. Physical lines of `.kt`, `.java`, `.cpp` and `.h` under the module source roots.
    Not statements, not tokens - the same number `wc -l` gives, so a disagreement with the gate is
    always resolvable by hand.

    RATCHETED. The count of files above the ceiling is compared with a baseline that may fall and
    never rise. The four files above it today are pre-existing; the point of the gate is that a
    fifth cannot appear unnoticed, and that a split which brings one below the line lowers the
    ceiling for good.

    WHY A COUNT AND NOT A LIST. A list would pin the offenders by name, and renaming a file would
    then read as a new violation. The count plus the printed worst offenders gives the same
    information without that trap.

.PARAMETER Gate
    Accepted for the fast-gate batch's uniform call shape; judging is already the default.

.PARAMETER Report
    Print every file above the ceiling and exit 0 regardless of the baseline.

.PARAMETER Quiet
    Print the verdict line only.

.PARAMETER Ceiling
    Line ceiling. Defaults to Rule 2's 1500.

.PARAMETER RepoRoot
    Repository root. Defaults to the directory two levels above this script.

.NOTES
    Exit codes:
      0 - at or below the baseline, or -Report was given
      1 - above the baseline: a file crossed the ceiling that did not before
      2 - cannot verify: a source root or the baseline file is missing or unreadable
#>
[CmdletBinding()]
param(
    [switch] $Gate,
    [switch] $Report,
    [switch] $Quiet,
    [ValidateRange(100, 100000)] [int] $Ceiling = 1500,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$sourceRoots = @('app_v2/src', 'wear/src')
$extensions = @('.kt', '.java', '.cpp', '.h')
$baselinePath = Join-Path $PSScriptRoot 'file-line-ceiling-baseline.txt'

$over = New-Object System.Collections.Generic.List[object]
foreach ($root in $sourceRoots) {
    $full = Join-Path $RepoRoot ($root -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $full)) {
        Write-Host "assert-file-line-ceiling: cannot verify - source root missing: $root" -ForegroundColor Yellow
        exit 2
    }
    foreach ($f in Get-ChildItem -LiteralPath $full -Recurse -File) {
        if ($extensions -notcontains $f.Extension.ToLowerInvariant()) { continue }
        if ($f.FullName -match '(^|[\\/])build([\\/]|$)') { continue }
        $lines = 0
        foreach ($_line in [IO.File]::ReadLines($f.FullName)) { $lines++ }
        if ($lines -gt $Ceiling) {
            $rel = $f.FullName.Substring($RepoRoot.Length).TrimStart('\', '/').Replace('\', '/')
            $over.Add([pscustomobject]@{ Path = $rel; Lines = $lines })
        }
    }
}

$sorted = @($over | Sort-Object -Property Lines -Descending)

if (-not $Quiet) {
    foreach ($o in $sorted) {
        Write-Host ("  {0,6} lines  {1}" -f $o.Lines, $o.Path) -ForegroundColor Yellow
    }
}

if ($Report) {
    Write-Host ("assert-file-line-ceiling: {0} file(s) above {1} lines - report mode." -f $sorted.Count, $Ceiling)
    exit 0
}

if (-not (Test-Path -LiteralPath $baselinePath)) {
    Write-Host "assert-file-line-ceiling: cannot verify - baseline file missing: $baselinePath" -ForegroundColor Yellow
    Write-Host "  Create it with the current count from: assert-file-line-ceiling.ps1 -Report"
    exit 2
}
$baseline = [int](Get-Content -LiteralPath $baselinePath -Raw).Trim()

if ($sorted.Count -gt $baseline) {
    Write-Host ("assert-file-line-ceiling: FAIL - {0} file(s) above {1} lines, baseline {2}." -f `
        $sorted.Count, $Ceiling, $baseline) -ForegroundColor Red
    Write-Host "  Rule 2: extract into a helper rather than growing the file. Run with -Report to see which."
    exit 1
}

Write-Host ("assert-file-line-ceiling: PASS - {0}/{1} file(s) above {2} lines." -f `
    $sorted.Count, $baseline, $Ceiling) -ForegroundColor Green
exit 0
