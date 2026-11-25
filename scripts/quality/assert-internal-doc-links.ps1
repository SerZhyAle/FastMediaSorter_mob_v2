#requires -Version 7.0
<#
.SYNOPSIS
    S4653 - every relative Markdown link and anchor inside the internal corpus (docs/, dev/) resolves on disk.

.DESCRIPTION
    DOC-INTERNAL-QUALITY rule 3. assert-docs-crosslinks.ps1 resolves links the way the published site serves
    them, so a link inside a document that no page publishes was judged by nothing. This gate reads the
    Markdown source instead: every inline link `[text](target)` whose target is relative - no scheme, not
    site-absolute (`/..`), not a bare `#fragment` of the same file is still checked against its own headings -
    is resolved against the linking file's directory, and a `#fragment` must match a heading of the target
    by GitHub's slug rules. Links inside fenced code blocks and inline code spans are not links.

    Known broken targets live in -BaselinePath, one `<source file> -> <target>` per line; the baseline only
    shrinks: a broken link missing from it fails, and a row that now resolves, or whose source file is gone,
    fails as stale until the row is deleted. With -Files only those files are judged and stale rows of other
    files are not reported, so a ticket answers for its own links (Rule 33 per-ticket placement).

.PARAMETER Files
    Comma-separated repository-relative .md files to judge. Files outside docs/ and dev/ are ignored.
    Default: every .md under docs/ and dev/ except dev/archive/, node_modules and build trees.

.PARAMETER RepoRoot
    Repository root. Overridable so the contract suite can judge a synthetic tree.

.PARAMETER BaselinePath
    Shrink-only list of known broken links. Default: scripts/quality/internal-doc-links-baseline.txt.

.PARAMETER Quiet
    Print the verdict line only.

.PARAMETER Gate
    Accepted and ignored; assert-release-scope-gates.ps1 passes it to every gate it runs.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-internal-doc-links.ps1 -Files "docs/DEV_OPS.md"

.NOTES
    Exit codes:
      0 - every judged link resolves or is baselined.
      1 - a new broken link or a stale baseline row.
      2 - could not verify: RepoRoot or a named file does not exist.
#>
[CmdletBinding()]
param(
    [string] $Files = '',
    [string] $RepoRoot = '',
    [string] $BaselinePath = '',
    [switch] $Quiet,
    [switch] $Gate
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'docs'; scope = 'internal-doc-links'; files = $(if ($Files) { $Files } else { 'docs/**/*.md,dev/**/*.md' }) })
if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
if (-not (Test-Path -LiteralPath $RepoRoot -PathType Container)) {
    Write-Host "assert-internal-doc-links: COULD NOT VERIFY - no such directory: $RepoRoot" -ForegroundColor Yellow
    exit 2
}
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path.TrimEnd('\', '/')
if (-not $BaselinePath) { $BaselinePath = Join-Path $RepoRoot 'scripts/quality/internal-doc-links-baseline.txt' }

$corpusRoots = @('docs', 'dev')

function Get-Relative([string] $FullPath) {
    return $FullPath.Substring($RepoRoot.Length).TrimStart('\', '/').Replace('\', '/')
}

function Test-InCorpus([string] $Relative) {
    if ($Relative -like 'dev/archive/*') { return $false }
    # Gitignored class-catalog renders: the local corpus has its own registry (item K), and their links are
    # written by the catalog scanner, so a finding there is repaired in the scanner, never in the file.
    if ($Relative -like 'dev/CATALOG/*') { return $false }
    if ($Relative -match '(^|/)(node_modules|build)/') { return $false }
    foreach ($root in $corpusRoots) { if ($Relative.StartsWith("$root/", [StringComparison]::OrdinalIgnoreCase)) { return $true } }
    return $false
}

function Get-Slug([string] $Heading) {
    $text = ($Heading -replace '`', '' -replace '\[([^\]]*)\]\([^)]*\)', '$1').Trim().ToLowerInvariant()
    $text = [regex]::Replace($text, '[^\p{L}\p{Nd}\s_-]', '')
    return ($text -replace '\s', '-')
}

$slugCache = @{}
function Get-Slugs([string] $FullPath) {
    if ($slugCache.ContainsKey($FullPath)) { return $slugCache[$FullPath] }
    $slugs = [System.Collections.Generic.HashSet[string]]::new()
    $seen = @{}
    $inFence = $false
    foreach ($line in [IO.File]::ReadAllLines($FullPath)) {
        if ($line -match '^\s*(```|~~~)') { $inFence = -not $inFence; continue }
        if ($inFence) { continue }
        if ($line -match '^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$') {
            $headingText = $Matches[1]
            # kramdown (the Jekyll site) turns a trailing {#id} into the heading's id, so the page is linked by it.
            if ($headingText -match '\{#([A-Za-z0-9_:.-]+)\}\s*$') { [void]$slugs.Add($Matches[1].ToLowerInvariant()) }
            $slug = Get-Slug $headingText
            # GitHub suffixes a repeated heading slug with -1, -2, ..
            if ($seen.ContainsKey($slug)) { $seen[$slug]++; [void]$slugs.Add("$slug-$($seen[$slug])") } else { $seen[$slug] = 0; [void]$slugs.Add($slug) }
        }
        foreach ($anchor in [regex]::Matches($line, '<a\s+(?:name|id)="([^"]+)"')) { [void]$slugs.Add($anchor.Groups[1].Value.ToLowerInvariant()) }
    }
    $slugCache[$FullPath] = $slugs
    return $slugs
}

function Get-BrokenLinks([string] $FullPath) {
    $broken = [System.Collections.Generic.List[string]]::new()
    $directory = Split-Path -Parent $FullPath
    $inFence = $false
    foreach ($line in [IO.File]::ReadAllLines($FullPath)) {
        if ($line -match '^\s*(```|~~~)') { $inFence = -not $inFence; continue }
        if ($inFence) { continue }
        $prose = [regex]::Replace($line, '`[^`]*`', '')
        foreach ($match in [regex]::Matches($prose, '(?<!!)\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)')) {
            $target = $match.Groups[1].Value
            if ($target -match '^[a-zA-Z][a-zA-Z0-9+.-]*:' -or $target.StartsWith('/')) { continue }
            $pathPart, $fragment = $target -split '#', 2
            $pathPart = [Uri]::UnescapeDataString($pathPart)
            $resolved = if ($pathPart) { Join-Path $directory ($pathPart -replace '/', [IO.Path]::DirectorySeparatorChar) } else { $FullPath }
            # A published page links its sibling as `.html` - Jekyll renders `<name>.md` to `<name>.html`.
            if (-not (Test-Path -LiteralPath $resolved) -and $resolved -like '*.html') {
                $source = $resolved.Substring(0, $resolved.Length - 5) + '.md'
                if (Test-Path -LiteralPath $source) { $resolved = $source }
            }
            if (-not (Test-Path -LiteralPath $resolved)) { $broken.Add($target); continue }
            if ($fragment -and $resolved -like '*.md' -and (Test-Path -LiteralPath $resolved -PathType Leaf)) {
                if (-not (Get-Slugs $resolved).Contains([Uri]::UnescapeDataString($fragment).ToLowerInvariant())) { $broken.Add($target) }
            }
        }
    }
    return $broken
}

# --- the judged set ------------------------------------------------------------------------------
$scoped = [bool]$Files
$judged = [System.Collections.Generic.List[string]]::new()
if ($scoped) {
    foreach ($name in @($Files -split ',' | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ -like '*.md' })) {
        if (-not (Test-InCorpus $name)) { continue }
        $full = Join-Path $RepoRoot $name
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { continue }
        $judged.Add($name)
    }
    # CHECK-VERDICT rule 2.12: a declared set with no corpus Markdown is the empty set, never a fall back to the tree.
    if ($judged.Count -eq 0) {
        . (Join-Path $PSScriptRoot 'lib/changed-files.ps1')
        Write-EmptySetPass -GateName 'assert-internal-doc-links'
        exit 0
    }
} else {
    foreach ($root in $corpusRoots) {
        $rootFull = Join-Path $RepoRoot $root
        if (-not (Test-Path -LiteralPath $rootFull)) { continue }
        foreach ($file in @(Get-ChildItem -LiteralPath $rootFull -Recurse -File -Filter *.md -ErrorAction SilentlyContinue)) {
            $relative = Get-Relative $file.FullName
            if (Test-InCorpus $relative) { $judged.Add($relative) }
        }
    }
}

# --- baseline ------------------------------------------------------------------------------------
$baseline = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
if (Test-Path -LiteralPath $BaselinePath) {
    foreach ($line in [IO.File]::ReadAllLines($BaselinePath)) {
        $row = $line.Trim()
        if ($row -and -not $row.StartsWith('#')) { [void]$baseline.Add($row) }
    }
}

$findings = [System.Collections.Generic.List[string]]::new()
$brokenRows = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($relative in $judged) {
    foreach ($target in (Get-BrokenLinks (Join-Path $RepoRoot $relative))) {
        $row = "$relative -> $target"
        [void]$brokenRows.Add($row)
        if (-not $baseline.Contains($row)) {
            $findings.Add("$row does not resolve - fix the link, or the heading it names, in the same change")
        }
    }
}
$judgedSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$judged, [StringComparer]::OrdinalIgnoreCase)
foreach ($row in $baseline) {
    $source = ($row -split ' -> ', 2)[0]
    if ($scoped -and -not $judgedSet.Contains($source)) { continue }
    if (-not $brokenRows.Contains($row)) { $findings.Add("baseline row '$row' is stale (the link resolves or its file is gone) - delete the row") }
}

if ($findings.Count -gt 0) {
    Write-Host "assert-internal-doc-links: FAIL ($($findings.Count) finding(s))" -ForegroundColor Red
    $findings | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
if (-not $Quiet) { Write-Host "  files: $($judged.Count)   baselined broken links: $($baseline.Count)" -ForegroundColor Gray }
Write-Host "assert-internal-doc-links: PASS (every judged relative link resolves or is baselined)" -ForegroundColor Green
exit 0
