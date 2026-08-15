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
      1. the file runs under $ErrorActionPreference = 'Stop' - it either assigns the mode
         itself, or dot-sources a library that does (otherwise Write-Error is non-terminating
         already and the exit line is reached);
      2. a `Write-Error` carries no explicit -ErrorAction;
      3. an `exit N` with N != 1 sits on the same line or within the next few.

    S1547 corrected condition 1. It used to read the scanned file's own lines only - true for a
    file with no dot-sources, wrong for one with them. The 21 scripts sourcing
    scripts/spec_catalog/_lib.ps1 - the whole spec-catalog toolchain, the path every status
    transition takes - inherit Stop from it, so rule A skipped them all while the summary printed
    PASS. Found on a live example: check-owner-inputs.ps1 documented exit 2 and delivered 1.
    The resolver reads ONE level and only a literal path spelled from $PSScriptRoot; a path built
    from a variable is left unresolved, because its value is not statically known and a guess
    would carry the mode into files that may never run under it. Rules B and C were never
    affected - both run before this condition.
    N = 1 is excluded deliberately: the collapse also produces 1, so such a site is
    already delivering its intended code and rewriting it would be noise.

    RULE C - a non-zero exit must carry a reason (S1338 phase 09). 480 recorded agent
    failures reported nothing but `Exit code 1`, which forces a blind re-run with
    different arguments just to learn what went wrong. So an `exit N` with N non-zero
    must be preceded, within a short lookback, by any `Write-*` call carrying an argument
    (project helpers such as Write-FailLine count), a direct `[Console]::Error.Write*`, a
    `throw` whose text a trap prints, or (S1368) a pipeline whose tail renders to the
    success stream - ConvertTo-Json/Csv/Xml, Format-Table/List/Wide, Out-Host/Default/String.
    Out-Null, Out-File and Set-Content are excluded on purpose: they swallow or divert the
    output instead of showing it. Block-comment bodies and `#` lines are skipped,
    and `exit $var` is
    ignored because its value is not statically known. Rule C ratchets against
    scripts/quality/exit-reason-baseline.txt: the count may fall, never rise.

    KNOWN LIMITATION (accepted, not a bug to file): the scan is line-based, so a
    multi-line `Write-Error (...)` whose `-ErrorAction Continue` sits on a continuation
    line reads as rule 2 and gets flagged although the exit is reachable. It over-blocks,
    never under-blocks - so it nags, it does not let a broken exit code through. Workaround
    is one line: build the message into a variable first, then
    `Write-Error $msg -ErrorAction Continue`. Fixing it properly needs AST parsing rather
    than regex; judged not worth the machinery (ticket raised 2026-07-16 and archived).

    Exit codes:
      0 - no unreachable exit site, no silent script, Rule C at or below baseline
          (or report mode).
      1 - substantive failure: an unreachable exit site, a silent script, or Rule C
          above its baseline.
      2 - the gate itself cannot run (scan root missing). Distinct from 1 on purpose -
          this gate must not commit the very sin it audits.

.PARAMETER Gate
    Fail-closed: exit 1 when any unreachable exit site is found.

.PARAMETER Path
    Optional scan root (default: the repository's scripts/ directory). Accepts a single
    file, so the tests can point the gate at a fixture.

.PARAMETER Quiet
    Print only the expected/actual summary line.

.PARAMETER ReasonBaseline
    Override the Rule C ratchet. Without it a repo scan reads
    scripts/quality/exit-reason-baseline.txt and a -Path fixture probe is report-only
    (it cannot know which of the repo's baselined sites it is looking at). Pass 0 to make
    a fixture assert that a reasonless exit is refused.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Path temp/probe.ps1
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [string]$Path = '',
    [switch]$Quiet,
    [int]$ReasonBaseline = -1
)

$ErrorActionPreference = 'Stop'

# The gate's own refusal path must reach its own exit code - see the note above.
function Reject([string]$msg) {
    Write-Error $msg -ErrorAction Continue
    exit 2
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path

# S1192: scripts/ is not the only place we keep executable scripts. The catalog tooling lives under
# dev/, is called in loops that test $LASTEXITCODE, and was outside this gate entirely - which is how
# seven scripts that never set an exit code at all went unnoticed until a wrapper reported five
# successful writes as five failures. Vendored trees (.venv, .github hooks) and agent-local scripts
# (.claude) stay out: not our code, not our contract.
$defaultRoots = @(
    (Join-Path $repoRoot 'scripts'),
    (Join-Path $repoRoot 'dev/CATALOG/scripts'),
    (Join-Path $repoRoot 'dev/ACTIVITY_CATALOG/scripts')
)
$scanRoots = if ($Path) { @($Path) } else { $defaultRoots | Where-Object { Test-Path $_ } }
if ($scanRoots.Count -eq 0) { Reject "No scan root found under: $repoRoot" }
foreach ($r in $scanRoots) { if (-not (Test-Path $r)) { Reject "Scan root not found: $r" } }

# Rule B applies only where a caller reads the exit code in a loop. Repo-wide it would flag ~150
# scripts whose callers rely on exceptions instead, which is a different (and legitimate) contract.
$silenceRuleRoots = @(
    (Join-Path $repoRoot 'dev/CATALOG/scripts'),
    (Join-Path $repoRoot 'dev/ACTIVITY_CATALOG/scripts')
)

# How far after a Write-Error an `exit N` still counts as "the code it meant to send".
# 3 covers the observed shapes (same line; next line; a closing brace between).
$lookahead = 3

# Rule C: how far BACK from an `exit N` a printed reason still counts as that exit's reason.
# 4 covers the observed shapes (same line via `;`; the line above; a closing brace or a
# blank line between the message and the exit).
$reasonLookback = 4
$reasonBaselineFile = Join-Path $PSScriptRoot 'exit-reason-baseline.txt'

# --- Condition 1, S1547: the terminating mode can be inherited ---------------------------------
# One predicate for both uses - the scanned file and any library it sources - so the two can never
# drift apart and let a mode through on one side that the other would refuse.
function Test-SetsStopMode([string[]]$lines) {
    foreach ($l in $lines) {
        # A commented-out assignment sets nothing. This matters most on the library side: a doc
        # comment in one shared file would otherwise hand the mode to every script sourcing it.
        if ($l -match '^\s*#') { continue }
        if ($l -match '\$ErrorActionPreference\s*=\s*[''"]Stop[''"]') { return $true }
    }
    return $false
}

# Literal, one level, anchored at $PSScriptRoot - the two forms actually written in this tree.
# `. $someVar` is deliberately not resolved; see the header note.
function Get-DotSourcedPaths([string[]]$lines, [string]$dir) {
    $paths = @()
    foreach ($l in $lines) {
        if ($l -match '^\s*#') { continue }
        if ($l -notmatch '^\s*\.\s') { continue }
        $m = [regex]::Match($l, 'Join-Path\s+\$PSScriptRoot\s+[''"]([^''"]+)[''"]')
        if ($m.Success) { $paths += (Join-Path $dir $m.Groups[1].Value); continue }
        $m = [regex]::Match($l, '^\s*\.\s+"?\$PSScriptRoot[\\/]([^"'']+?)"?\s*$')
        if ($m.Success) { $paths += (Join-Path $dir $m.Groups[1].Value) }
    }
    return $paths
}

# One shared library serves 21 callers here, so its verdict is memoised: the gate sits in the fast
# static battery and must not read the same file once per caller.
$script:stopModeMemo = @{}
function Test-LibrarySetsStop([string]$path) {
    if (-not $path) { return $false }
    $key = $path.ToLowerInvariant()
    if ($script:stopModeMemo.ContainsKey($key)) { return $script:stopModeMemo[$key] }
    $verdict = $false
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $libLines = Get-Content -LiteralPath $path -ErrorAction SilentlyContinue
        if ($libLines) { $verdict = Test-SetsStopMode $libLines }
    }
    $script:stopModeMemo[$key] = $verdict
    return $verdict
}

$files = @(foreach ($root in $scanRoots) {
    if (Test-Path $root -PathType Leaf) {
        Get-Item -LiteralPath $root
    } else {
        # Skip scripts/<subject>.tests/ - a regression suite for this very gate has to carry the defect
        # verbatim as a fixture, and the scan is line-based, so it cannot tell a here-string fixture from
        # live code. Found by this gate's own harness on its first run. An explicit -Path still scans a
        # test file when one is aimed at deliberately.
        Get-ChildItem -LiteralPath $root -Recurse -File -Filter *.ps1 -ErrorAction SilentlyContinue |
            Where-Object { $_.DirectoryName -notmatch '\.tests($|[\\/])' }
    }
})

$findings = @()
$silent = @()
$reasonless = @()
foreach ($f in $files) {
    $lines = Get-Content -LiteralPath $f.FullName -ErrorAction SilentlyContinue
    if (-not $lines) { continue }

    # Rule C: an `exit N` (N non-zero) with no message printed just before it. Unlike rules A
    # and B this one does not depend on $ErrorActionPreference, so it runs over every file.
    $inBlockComment = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        # A header's ".NOTES Exit codes:" prose is not code; block comments are skipped whole.
        if ($inBlockComment) {
            if ($line -match '#>') { $inBlockComment = $false }
            continue
        }
        if ($line -match '<#') { $inBlockComment = ($line -notmatch '#>'); continue }
        if ($line -match '^\s*#') { continue }
        if ($line -notmatch '(^|\s|\}|;)exit\s+([1-9]\d*)\b') { continue }
        $exitCode = [int]$Matches[2]

        $hasReason = $false
        $back = [Math]::Max(0, $i - $reasonLookback)
        for ($k = $i; $k -ge $back; $k--) {
            $cand = $lines[$k]
            if ($cand -match '^\s*#') { continue }
            # Any Write-* call with an argument counts, including project helpers such as
            # Write-FailLine / Write-Info, plus direct writes to the console streams.
            if ($cand -match '(Write-[A-Za-z]+\s+\S|\[Console\]::(Error|Out)\.Write)') { $hasReason = $true; break }
            if ($cand -match '^\s*throw\b') { $hasReason = $true; break }
            # S1368: a pipeline that renders to the success stream is a printed reason too. The
            # machine-readable verbs matter most - `-Verb CheckContext` signals its outcome as a
            # JSON object plus exit 3, and the only "fix" this heuristic used to accept would have
            # been a Write-Error announcing an expected result and corrupting that JSON. The tail
            # cmdlet is what decides: Out-Null / Out-File / Set-Content swallow or divert output
            # and are deliberately absent from the list.
            if ($cand -match '\|\s*(ConvertTo-(Json|Csv|Xml)|Format-(Table|List|Wide)|Out-(Host|Default|String))\b') { $hasReason = $true; break }
        }
        if (-not $hasReason) {
            $reasonless += [pscustomobject]@{
                File = $f.FullName.Replace($repoRoot + [IO.Path]::DirectorySeparatorChar, '')
                Line = $i + 1
                Code = $exitCode
                Text = $line.Trim()
            }
        }
    }

    # Rule B (S1192): under the roots whose callers read $LASTEXITCODE, a script must state its
    # outcome. Falling off the end leaves the caller's $LASTEXITCODE untouched - on a first iteration
    # that is empty, and `if ($LASTEXITCODE -ne 0)` calls a success a failure. It fails the other way
    # too: a real error goes unnoticed when the previous command happened to exit 0.
    $inSilenceRoot = $silenceRuleRoots | Where-Object { $f.FullName.StartsWith($_, [StringComparison]::OrdinalIgnoreCase) }
    if ($inSilenceRoot) {
        $declaresExit = $false
        foreach ($l in $lines) {
            if ($l -match '^\s*#') { continue }
            if ($l -match '(^|\s|\}|;)exit\s') { $declaresExit = $true; break }
        }
        if (-not $declaresExit) {
            $silent += $f.FullName.Replace($repoRoot + [IO.Path]::DirectorySeparatorChar, '')
        }
    }

    # Condition 1: the file runs under Stop. Without it Write-Error does not terminate. The mode
    # counts whether the file assigns it or inherits it from a library it sources (S1547).
    $inheritedFrom = $null
    $underStop = Test-SetsStopMode $lines
    if (-not $underStop) {
        foreach ($lib in (Get-DotSourcedPaths $lines $f.DirectoryName)) {
            if (Test-LibrarySetsStop $lib) {
                $underStop = $true
                $inheritedFrom = $lib.Replace($repoRoot + [IO.Path]::DirectorySeparatorChar, '')
                break
            }
        }
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
                    Inherited = $inheritedFrom
                }
                break
            }
        }
    }
}

if (-not $Quiet) {
    foreach ($x in $findings) {
        Write-Host ("  {0}:{1}  exit {2} unreachable - Write-Error terminates first" -f $x.File, $x.Line, $x.Code) -ForegroundColor Red
        # Without this line the file looks innocent: it carries no assignment of its own, and the
        # reader has no way to see why the rule applied to it at all.
        if ($x.Inherited) {
            Write-Host ("      runs under Stop inherited from {0}" -f $x.Inherited) -ForegroundColor DarkGray
        }
        Write-Host ("      {0}" -f $x.Text) -ForegroundColor DarkGray
    }
    if ($findings.Count -gt 0) {
        Write-Host ''
        Write-Host '  Fix: add -ErrorAction Continue to the Write-Error so the exit line is reached.' -ForegroundColor Yellow
    }
    foreach ($s in $silent) {
        Write-Host ("  {0}  sets no exit code - the caller keeps whatever `$LASTEXITCODE it had" -f $s) -ForegroundColor Red
    }
    if ($silent.Count -gt 0) {
        Write-Host ''
        Write-Host '  Fix: end every terminating path with an explicit exit, and list the codes in the header.' -ForegroundColor Yellow
    }
}

# Rule C ratchets on a count. An explicit -Path run is a fixture probe, not the repo scan, so by
# default it reports without gating - it cannot know which of the repo's baselined sites it sees.
# -ReasonBaseline 0 turns a fixture into an assertion.
$reasonBaseline = if ($ReasonBaseline -ge 0) { $ReasonBaseline }
    elseif ($Path) { $reasonless.Count }
    elseif (Test-Path -LiteralPath $reasonBaselineFile) {
        [int]((Get-Content -LiteralPath $reasonBaselineFile -TotalCount 1).Trim())
    } else { 0 }

if (-not $Quiet -and $reasonless.Count -gt 0) {
    foreach ($x in $reasonless) {
        Write-Host ("  {0}:{1}  exit {2} with no reason printed" -f $x.File, $x.Line, $x.Code) -ForegroundColor Red
        Write-Host ("      {0}" -f $x.Text) -ForegroundColor DarkGray
    }
    Write-Host ''
    Write-Host '  Fix: print why before exiting - Write-Error "<what failed and what fixes it>" -ErrorAction Continue.' -ForegroundColor Yellow
}

Write-Host ("assert-exit-contract: expected: 0 | actual: {0} unreachable exit site(s), {1} silent script(s), {2} reasonless exit(s) (baseline {3})" -f $findings.Count, $silent.Count, $reasonless.Count, $reasonBaseline)

if ($Gate -and ($findings.Count -gt 0 -or $silent.Count -gt 0 -or $reasonless.Count -gt $reasonBaseline)) {
    Write-Host 'assert-exit-contract: FAIL - a script cannot deliver the exit code it means to send, or exits without saying why.' -ForegroundColor Red
    exit 1
}
if (-not $Quiet -and $findings.Count -eq 0 -and $silent.Count -eq 0) {
    Write-Host 'assert-exit-contract: PASS - every exit code is reachable and every scanned script sets one.' -ForegroundColor Green
}
exit 0
