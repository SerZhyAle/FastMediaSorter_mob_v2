#requires -Version 7.0
<#
.SYNOPSIS
    Gate: a documented exit code must actually be reachable (S1070).

.DESCRIPTION
    Under `$ErrorActionPreference = 'Stop'` a bare `Write-Error` raises a TERMINATING
    error. Any `exit N` written after it never runs, so the process dies with 1 and the
    intended code is silently lost. The message still prints, which is exactly why this
    survives review: the output looks right and only the number lies.

    The cure is one token - `Write-Error $msg -ErrorAction Continue` - after which the
    following `exit N` executes and the caller sees the code the script meant to send.

    This gate exists because the class keeps regrowing rather than because any single
    site is dramatic: S0082-era mutators, then close-and-log.ps1 (S1063), then 18 sites
    across 9 scripts (S1070). CLAUDE.md Rule 19/20 - a recurring finding becomes a
    mechanical gate.

    A site is reported only when all three hold, so legitimate code is left alone:
      1. the file sets $ErrorActionPreference = 'Stop' (otherwise Write-Error is
         non-terminating already and the exit line is reached);
      2. a `Write-Error` carries no explicit -ErrorAction;
      3. an `exit N` with N != 1 sits on the same line or within the next few.
    N = 1 is excluded deliberately: the collapse also produces 1, so such a site is
    already delivering its intended code and rewriting it would be noise.

    KNOWN LIMITATION (accepted, not a bug to file): the scan is line-based, so a
    multi-line `Write-Error (...)` whose `-ErrorAction Continue` sits on a continuation
    line reads as rule 2 and gets flagged although the exit is reachable. It over-blocks,
    never under-blocks - so it nags, it does not let a broken exit code through. Workaround
    is one line: build the message into a variable first, then
    `Write-Error $msg -ErrorAction Continue`. Fixing it properly needs AST parsing rather
    than regex; judged not worth the machinery (ticket raised 2026-07-16 and archived).

    Exit codes:
      0 - no unreachable exit site (or report mode).
      1 - substantive failure: at least one unreachable exit site found.
      2 - the gate itself cannot run (scan root missing). Distinct from 1 on purpose -
          this gate must not commit the very sin it audits.

.PARAMETER Gate
    Fail-closed: exit 1 when any unreachable exit site is found.

.PARAMETER Path
    Optional scan root (default: the repository's scripts/ directory). Accepts a single
    file, so the tests can point the gate at a fixture.

.PARAMETER Quiet
    Print only the expected/actual summary line.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Path temp/probe.ps1
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [string]$Path = '',
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

# The gate's own refusal path must reach its own exit code - see the note above.
function Reject([string]$msg) {
    Write-Error $msg -ErrorAction Continue
    exit 2
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$scanRoot = if ($Path) { $Path } else { Join-Path $repoRoot 'scripts' }
if (-not (Test-Path $scanRoot)) { Reject "Scan root not found: $scanRoot" }

# How far after a Write-Error an `exit N` still counts as "the code it meant to send".
# 3 covers the observed shapes (same line; next line; a closing brace between).
$lookahead = 3

$files = if (Test-Path $scanRoot -PathType Leaf) {
    @(Get-Item -LiteralPath $scanRoot)
} else {
    # Skip scripts/<subject>.tests/ - a regression suite for this very gate has to carry the defect
    # verbatim as a fixture, and the scan is line-based, so it cannot tell a here-string fixture from
    # live code. Found by this gate's own harness on its first run. An explicit -Path still scans a
    # test file when one is aimed at deliberately.
    Get-ChildItem -LiteralPath $scanRoot -Recurse -File -Filter *.ps1 -ErrorAction SilentlyContinue |
        Where-Object { $_.DirectoryName -notmatch '\.tests($|[\\/])' }
}

$findings = @()
foreach ($f in $files) {
    $lines = Get-Content -LiteralPath $f.FullName -ErrorAction SilentlyContinue
    if (-not $lines) { continue }

    # Condition 1: the file runs under Stop. Without it Write-Error does not terminate.
    $underStop = $false
    foreach ($l in $lines) {
        if ($l -match '\$ErrorActionPreference\s*=\s*[''"]Stop[''"]') { $underStop = $true; break }
    }
    if (-not $underStop) { continue }

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        # Condition 2: a Write-Error with no explicit -ErrorAction. Skip comment lines -
        # a header explaining this very rule must not trip the gate that enforces it.
        if ($line -match '^\s*#') { continue }
        if ($line -notmatch 'Write-Error') { continue }
        if ($line -match '-ErrorAction') { continue }

        # Condition 3: an exit N (N != 1) within reach.
        for ($k = $i; $k -lt [Math]::Min($i + $lookahead + 1, $lines.Count); $k++) {
            if ($lines[$k] -match '\bexit\s+([2-9])\b') {
                $findings += [pscustomobject]@{
                    File = $f.FullName.Replace($repoRoot + [IO.Path]::DirectorySeparatorChar, '')
                    Line = $i + 1
                    Code = [int]$Matches[1]
                    Text = $line.Trim()
                }
                break
            }
        }
    }
}

if (-not $Quiet) {
    foreach ($x in $findings) {
        Write-Host ("  {0}:{1}  exit {2} unreachable - Write-Error terminates first" -f $x.File, $x.Line, $x.Code) -ForegroundColor Red
        Write-Host ("      {0}" -f $x.Text) -ForegroundColor DarkGray
    }
    if ($findings.Count -gt 0) {
        Write-Host ''
        Write-Host '  Fix: add -ErrorAction Continue to the Write-Error so the exit line is reached.' -ForegroundColor Yellow
    }
}

Write-Host ("assert-exit-contract: expected: 0 | actual: {0} unreachable exit site(s)" -f $findings.Count)

if ($Gate -and $findings.Count -gt 0) {
    Write-Host 'assert-exit-contract: FAIL - a documented exit code cannot be delivered.' -ForegroundColor Red
    exit 1
}
if (-not $Quiet -and $findings.Count -eq 0) {
    Write-Host 'assert-exit-contract: PASS - every exit code after a Write-Error is reachable.' -ForegroundColor Green
}
exit 0
