<#
.SYNOPSIS
    Refuses a .ps1 in the scanned roots that the PowerShell parser cannot read at all.

.DESCRIPTION
    Runs [System.Management.Automation.Language.Parser]::ParseFile over every .ps1 under
    scripts/, dev/CATALOG/scripts/ and dev/ACTIVITY_CATALOG/scripts/ - the same three roots
    assert-exit-contract.ps1 walks - and fails on any file that returns a parse error. Such a
    file cannot be executed at all: the interpreter dies before the first line of the body, so
    every exit code, parameter and guard its header advertises is fiction.

    Why a gate of its own (S2619). The whole static battery reads .ps1 line by line with regular
    expressions, and a per-line scanner cannot tell a valid file from an invalid one - it matches
    text either way. So a script wrecked by a truncated edit passes closure exactly like a working
    one and survives in the tree until somebody calls it. Two such files did: both halves of the
    dimen pipeline in scripts/layout-dimen-migration/, one with two unrelated fragments spliced
    mid-block, and neither was noticed by any gate. S2609 came closest - its AST detector falls
    back to the line scan for a file it cannot parse and names it in the report - but that stays
    soft by design, reporting the file and still exiting 0.

    No baseline file. The class is binary rather than a count: a file either parses or cannot run,
    so there is no legitimate resident population to ratchet down from, and the tree is at zero
    once S2619's deletion lands. A file needing an exception would be a file nobody can execute.

    Cheap by construction - it parses .ps1 text and starts no gradle daemon.

.NOTES
    Exit codes:
      0 - every scanned file parses.
      1 - at least one file has a parse error (path, line and first error printed).
      2 - the gate itself cannot run (no scan root found). Distinct from 1 on purpose: "found
          nothing" and "could not look" are different answers.

.PARAMETER Path
    Optional scan root, replacing the three defaults. Accepts a single file, so a regression
    suite can point the gate at a fixture.

.PARAMETER Quiet
    Print only the summary line. Used by the fast-gates battery.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-script-parses.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-script-parses.ps1 -Path temp/broken.ps1
#>
param(
    [string] $Path,
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# Same three roots as assert-exit-contract.ps1 (S1192): the catalog tooling under dev/ is executable
# code on the same footing as scripts/. Vendored trees and .claude/ stay out - not our code.
$defaultRoots = @(
    (Join-Path $repoRoot 'scripts'),
    (Join-Path $repoRoot 'dev/CATALOG/scripts'),
    (Join-Path $repoRoot 'dev/ACTIVITY_CATALOG/scripts')
)
$scanRoots = if ($Path) { @($Path) } else { $defaultRoots | Where-Object { Test-Path $_ } }
if ($scanRoots.Count -eq 0) {
    Write-Error "script-parses: cannot run - no scan root found under: $repoRoot" -ErrorAction Continue
    exit 2
}
foreach ($r in $scanRoots) {
    if (-not (Test-Path $r)) {
        Write-Error "script-parses: cannot run - scan root not found: $r" -ErrorAction Continue
        exit 2
    }
}

$files = @(foreach ($root in $scanRoots) {
    if (Test-Path $root -PathType Leaf) {
        Get-Item -LiteralPath $root
    } else {
        # Skip scripts/<subject>.tests/ - a regression suite for this very gate has to carry an
        # unparseable file verbatim as a fixture. An explicit -Path still scans one when aimed at
        # deliberately, which is how the fixture is asserted.
        Get-ChildItem -LiteralPath $root -Recurse -File -Filter *.ps1 -ErrorAction SilentlyContinue |
            Where-Object { $_.DirectoryName -notmatch '\.tests($|[\\/])' }
    }
})

$broken = @()
foreach ($f in $files) {
    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($f.FullName, [ref]$tokens, [ref]$errors)
    if ($errors -and $errors.Count -gt 0) {
        $first = $errors[0]
        $broken += [pscustomobject]@{
            Path    = $f.FullName.Replace($repoRoot + [IO.Path]::DirectorySeparatorChar, '')
            Line    = $first.Extent.StartLineNumber
            Message = $first.Message
            Count   = $errors.Count
        }
    }
}

if ($broken.Count -gt 0) {
    foreach ($b in $broken) {
        Write-Host ("  {0}" -f $b.Path) -ForegroundColor Yellow
        Write-Host ("    line {0}: {1}" -f $b.Line, $b.Message) -ForegroundColor Gray
        if ($b.Count -gt 1) {
            Write-Host ("    (+{0} more parse error(s) in this file)" -f ($b.Count - 1)) -ForegroundColor DarkGray
        }
    }
    Write-Error ("script-parses: FAIL - expected: 0 unparseable scripts | actual: {0} of {1} scanned. A file the parser refuses cannot be executed at all - repair or delete it." -f $broken.Count, $files.Count) -ErrorAction Continue
    exit 1
}

if (-not $Quiet) {
    Write-Host ("script-parses: PASS - expected: 0 unparseable scripts | actual: 0 of {0} scanned." -f $files.Count) -ForegroundColor Green
}
exit 0
