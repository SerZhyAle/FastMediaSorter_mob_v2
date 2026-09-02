#requires -Version 7.0
<#
.SYNOPSIS
    Gate: a typographic dash in documentation prose fails the closure (S2216).

.DESCRIPTION
    The canon's check-compliance.ps1 flagged nine mirrored docs pages with SZA-STYLE01 on
    2026-08-28 - long dashes in prose, a regression that had entered after 2026-08-18, when the
    same check still returned zero. Nothing in this repository caught it: assert-neuroslop.ps1
    judges source files only, and no gate read docs prose at closure time. Meanwhile
    .github/workflows/jekyll-gh-pages.yml publishes docs/** on every push, so the defect reached
    the public site with no Android release involved - which is why this gate runs per change,
    scoped to the changed documentation set, rather than only at release scope.

    PROSE ONLY, BY THE LIBRARY'S OWN CUT. Fenced code blocks, indented blocks and backtick spans
    are skipped, mirroring the Prose area of lib/house-text-style.ps1 - a dash inside code the
    canon itself excludes is never reported. The dash pattern is not declared here either: it is
    the library's 'long-dash' rule, the same data fix-house-style.ps1 applies, so the fixer and
    this gate cannot drift apart.

    KNOWN DEBT IS NOT EXEMPT. The generated showcase pages docs/FEATURES_noLegal*.md carry long
    dashes written by the release pipeline; an unscoped corpus run names them and fails. That is
    deliberate: their fate (clean them, or teach the pipeline the house style) is a parked draft
    spec, and a baseline here would hide the decision instead of forcing it.

.PARAMETER ChangedFiles
    Judge these files alone, so one closure is not charged for another session's in-flight
    document. Accepts an array or post-change.ps1's comma-joined single string; members that are
    not .md, or do not exist, are skipped with a note. Omit to judge the whole docs/**/*.md corpus.

.PARAMETER Quiet
    Print the verdict line only.

.PARAMETER RepoRoot
    Repository root. Defaults to the directory two levels above this script.

.NOTES
    Exit codes:
      0 - no typographic dash in prose in the judged set
      1 - one or more findings, each printed as FAIL <path>:<line>: <count>
      2 - cannot verify: the docs corpus directory is missing, the house-style library declares
          no 'long-dash' rule, an explicitly judged file cannot be read, or -ChangedFiles named
          no .md file at all
#>
[CmdletBinding()]
param(
    [string[]] $ChangedFiles = @(),
    [switch] $Quiet,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/house-text-style.ps1')

# The pattern is data owned by the house-style library (S1544). Re-declaring it here is how the
# fixer and the gate would drift, which is the exact failure shape that library was extracted to
# end - before it existed, five fixers each carried a partial copy and none knew the long dash.
$longDashRule = Get-HouseStyleRules | Where-Object { $_.Name -eq 'long-dash' }
if (-not $longDashRule) {
    Write-Host "assert-doc-house-style: cannot verify - house-style library declares no 'long-dash' rule." -ForegroundColor Yellow
    exit 2
}
$dashPattern = [regex]::new($longDashRule.Pattern)

function Get-Relative {
    param([string] $Full)
    return $Full.Substring($RepoRoot.Length).TrimStart('\', '/').Replace('\', '/')
}

# One file -> one record per offending line. The in-prose cut mirrors Convert-HouseStyleProse in
# lib/house-text-style.ps1 line for line, so what this gate reports is exactly what the fixer
# would rewrite and nothing else.
function Find-DashFindings {
    param([string] $Path)
    $findings = New-Object System.Collections.Generic.List[object]
    $lines = [IO.File]::ReadAllLines($Path)
    $inFence = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^\s*```') { $inFence = -not $inFence; continue }
        if ($inFence) { continue }
        if ($line -match '^(?:\s{4}|\t)') { continue }
        $count = 0
        $segments = $line -split '(`[^`]*`)'
        # Odd segments are the backtick spans themselves and are passed through untouched.
        for ($s = 0; $s -lt $segments.Length; $s += 2) {
            $count += $dashPattern.Matches($segments[$s]).Count
        }
        if ($count -gt 0) {
            $findings.Add([pscustomobject]@{ File = (Get-Relative $Path); Line = $i + 1; Count = $count })
        }
    }
    return $findings
}

# -ChangedFiles binds both an array and post-change.ps1's comma-joined single string - the facade
# hands every scoped gate the joined form, which binds to a [string[]] parameter as one element.
$normChanged = @($ChangedFiles |
        Where-Object { $_ } |
        ForEach-Object { $_ -split ',' } |
        ForEach-Object { $_.Trim().Replace('\', '/') -replace '^\./', '' } |
        Where-Object { $_ -match '(?i)\.md$' })

$judged = New-Object System.Collections.Generic.List[string]
$scope = 'docs corpus'
if ($normChanged.Count -gt 0) {
    $scope = 'changed set'
    foreach ($rel in $normChanged) {
        $full = if ([IO.Path]::IsPathRooted($rel)) { $rel } else { Join-Path $RepoRoot $rel }
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
            if (-not $Quiet) { Write-Host "  skipped (not found): $rel" -ForegroundColor DarkGray }
            continue
        }
        $judged.Add((Resolve-Path -LiteralPath $full).Path)
    }
    if ($judged.Count -eq 0) {
        Write-Host 'assert-doc-house-style: cannot verify - -ChangedFiles named no readable .md file.' -ForegroundColor Yellow
        exit 2
    }
}
else {
    $docsRoot = Join-Path $RepoRoot 'docs'
    if (-not (Test-Path -LiteralPath $docsRoot -PathType Container)) {
        Write-Host "assert-doc-house-style: cannot verify - docs directory missing: $docsRoot" -ForegroundColor Yellow
        exit 2
    }
    foreach ($f in (Get-ChildItem -LiteralPath $docsRoot -Recurse -File -Filter *.md)) { $judged.Add($f.FullName) }
}

$all = New-Object System.Collections.Generic.List[object]
foreach ($path in $judged) {
    try {
        foreach ($f in (Find-DashFindings -Path $path)) { $all.Add($f) }
    }
    catch {
        Write-Host ("assert-doc-house-style: cannot verify - unreadable file: {0} ({1})" -f `
                (Get-Relative $path), $_.Exception.Message) -ForegroundColor Yellow
        exit 2
    }
}

if (-not $Quiet) {
    foreach ($f in $all) {
        Write-Host ("  FAIL {0}:{1}: {2} em/en-dash(es) in prose" -f $f.File, $f.Line, $f.Count) -ForegroundColor Red
    }
}

if ($all.Count -gt 0) {
    $fileCount = @($all | Select-Object -ExpandProperty File -Unique).Count
    $runCount = ($all | Measure-Object -Property Count -Sum).Sum
    Write-Host ("assert-doc-house-style: FAIL - {0} long-dash run(s) in prose, {1} line(s), {2} file(s) [{3}]." -f `
            $runCount, $all.Count, $fileCount, $scope) -ForegroundColor Red
    Write-Host "  Replace with the house-style hyphen: pwsh -NoProfile -File scripts/utils/fix-house-style.ps1 -Area Prose -Rules long-dash -Apply"
    exit 1
}

Write-Host ("assert-doc-house-style: PASS - no typographic dash in prose ({0} file(s) [{1}])." -f `
        $judged.Count, $scope) -ForegroundColor Green
exit 0
