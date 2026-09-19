#requires -Version 7.0
<#
.SYNOPSIS
    S3150: a repository PowerShell script must stay under the CLAUDE.md Rule 2 file-size ceiling.

.DESCRIPTION
    Rule 2 caps a source file at 2000 lines. For Kotlin that ceiling is read by reviewers and by the
    helper-extraction rule; for the repository's own scripts nothing measured it at all, so the one
    file that crossed it did so unnoticed and was found by hand while another ticket was being
    closed: scripts/post-change.ps1 measured 2221 lines before S3147, 2230 after it, and 2314 on
    2026-09-16. That file runs at every closure in every runtime, so each new closure step grew it
    further and no check said a word.

    A HARD LIMIT, NOT A RATCHET. The count-ratchet gates in this directory judge a delta against
    HEAD because the debt they guard was accumulated by many tickets and cannot be attributed. This
    ceiling is not debt - it is a rule with a number in it, and the measurement that made the gate
    switchable-on is that after S3150's extraction NO script in the tree is above it (the largest is
    scripts/quality/lib/source-matchers.ps1 at 1444 lines). A baseline would therefore record
    nothing, and a ratchet would silently accept the next 2300-line file as long as it arrived in one
    commit.

    SCOPED TO THE CHANGED SET AT A CLOSURE. Under -ChangedFiles only the scripts this change actually
    touched are judged, so a closure is never refused over a file it did not open - the same scoping
    rule CLAUDE.md section 12 states for every per-ticket gate. Without it every discovered script is
    judged, which is what the release scope and a hand run want.

    WHAT COUNTS AS A REPOSITORY SCRIPT. Every .ps1 under scripts/ and .claude/, plus the .ps1 files
    at the repository root (a.ps1 and its siblings). The read-only zones (V1/, v2_6/, spec_v2/,
    dev/archive/) are outside the rule's reach by CLAUDE.md Rule 4, and temp/ holds generated and
    scratch scripts that no rule governs.

.PARAMETER Gate
    Exit 1 on a file above the ceiling. Without it the finding is printed and the exit code stays 0.

.PARAMETER Quiet
    Suppress the clean-run summary; findings always print.

.PARAMETER ChangedFiles
    Comma-separated changed-file set. Judges only the .ps1 files in it. Accepts a single string
    because `pwsh -File` binds `-ChangedFiles a.ps1,b.ps1` as one array element.

.PARAMETER MaxLines
    The ceiling. Defaults to 2000, the number in CLAUDE.md Rule 2.

.PARAMETER RepoRoot
    Repository root. Defaults to the parent of this script's directory.

.NOTES
    Exit codes:
      0  every judged script is within the ceiling (or a finding stands without -Gate).
      1  a judged script is above the ceiling, with -Gate.
      2  could not verify - the repository root or every discovery root is unreadable.
#>
param(
    [switch] $Gate,
    [switch] $Quiet,
    [string] $ChangedFiles,
    [int] $MaxLines = 2000,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $RepoRoot)) {
    Write-Error "script-file-size: cannot run - repository root not found: $RepoRoot" -ErrorAction Continue
    exit 2
}

$discoveryRoots = @('scripts', '.claude') |
    ForEach-Object { Join-Path $RepoRoot $_ } |
    Where-Object { Test-Path -LiteralPath $_ }

$candidates = [System.Collections.Generic.List[string]]::new()

if ($ChangedFiles) {
    foreach ($entry in ($ChangedFiles -split ',')) {
        $relative = $entry.Trim() -replace '\\', '/'
        if (-not $relative -or $relative -notmatch '\.ps1$') { continue }
        # A path outside the discovery roots is not this gate's subject, and neither is a deleted one.
        if ($relative -notmatch '^(scripts/|\.claude/|[^/]+\.ps1$)') { continue }
        $full = Join-Path $RepoRoot $relative
        if (Test-Path -LiteralPath $full) { $candidates.Add((Resolve-Path -LiteralPath $full).Path) }
    }
}
else {
    if ($discoveryRoots.Count -eq 0) {
        Write-Error "script-file-size: cannot run - no discovery root under $RepoRoot" -ErrorAction Continue
        exit 2
    }
    foreach ($discovered in (Get-ChildItem -Path $discoveryRoots -Recurse -Filter '*.ps1' -File)) {
        $candidates.Add($discovered.FullName)
    }
    foreach ($discovered in (Get-ChildItem -Path $RepoRoot -Filter '*.ps1' -File)) {
        $candidates.Add($discovered.FullName)
    }
}

$findings = [System.Collections.Generic.List[object]]::new()
foreach ($candidate in ($candidates | Sort-Object -Unique)) {
    $lineCount = (Get-Content -LiteralPath $candidate).Count
    if ($lineCount -le $MaxLines) { continue }
    $relative = $candidate.Substring($RepoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
    $findings.Add([pscustomobject]@{ Path = $relative; Lines = $lineCount })
}

if ($findings.Count -gt 0) {
    Write-Host "script-file-size: FAIL - $($findings.Count) script(s) above the $MaxLines-line ceiling (CLAUDE.md Rule 2):" -ForegroundColor Red
    foreach ($finding in ($findings | Sort-Object Lines -Descending)) {
        Write-Host ("  {0} - {1} lines (+{2})" -f $finding.Path, $finding.Lines, ($finding.Lines - $MaxLines)) -ForegroundColor Red
    }
    Write-Host '  Extract a self-contained block into a dot-sourced library beside its siblings (scripts/quality/lib/),' -ForegroundColor Yellow
    Write-Host '  keeping the caller''s scope: the extraction must not change what the script does.' -ForegroundColor Yellow
    if ($Gate) { exit 1 }
    exit 0
}

if (-not $Quiet) {
    $scopeNote = if ($ChangedFiles) { 'in the changed set' } else { 'in scripts/, .claude/ and the repository root' }
    Write-Host "script-file-size: OK - $($candidates.Count) script(s) $scopeNote, none above the $MaxLines-line ceiling." -ForegroundColor Green
}
exit 0
