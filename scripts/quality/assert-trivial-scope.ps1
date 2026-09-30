#requires -Version 7.0
<#
.SYNOPSIS
    S3151: refuse a Trivial ticket whose changed set outgrew the Trivial checklist.

.DESCRIPTION
    /spec-all and /spec-code declare a ticket Trivial by checklist before any file exists (S3151
    ADR-3), so the declaration is checked here, at closure, against what was actually changed. The
    limits live in the `trivial` key of .sza-profile.json:
      maxFiles                    most paths the set may hold; deleted paths count
      forbidNewFiles              a path absent from HEAD is refused - new types, layouts and
                                  navigation graphs all arrive as new files
      forbiddenPatterns           regular expressions refused on added lines
      forbiddenPatternExtensions  extensions whose added lines are judged by forbiddenPatterns
    Paths under PLAN/ and temp/ are not judged: the Trivial spec itself lives there, gitignored.

    S3182: the base of the comparison is a per-ticket SNAPSHOT, not HEAD. This work tree always
    carries the uncommitted work of other open tickets, so every line a sibling left behind reads
    as added by this one - on S3180 a `data class` another ticket had written escalated a change
    that added no type at all. `-RecordBaseline` takes the snapshot before the first edit and
    writes its sha to temp/<Id>/trivial-base.txt; the judging run reads it from there. S3872: the
    snapshot is an unreferenced commit built from a private copy of the index (`git add -u`,
    `write-tree`, `commit-tree` under GIT_INDEX_FILE), so neither the real index nor the work tree
    is written and a stale index stat cannot refuse it the way it refused `git stash create`. A
    snapshot that still fails records HEAD plus a `fallback-to-HEAD` marker and says "fallback to
    HEAD" on its own line - at record time and again in every judging run. A missing, unreadable
    or pruned snapshot is NOT a refusal either: the run falls back to HEAD and says so.
    Limitation: untracked files are not recorded, so a sibling's brand-new file still reads as new
    to this ticket.

    S3515: a path the document registry (docs/DOCUMENT_REGISTRY.jsonl under -RepoRoot) marks
    `generated` is not judged at all - neither counted toward maxFiles nor tested as new. A
    generated document is rewritten by its generator, not chosen by the ticket: on S3514 the
    closure regenerated docs/SCRIPT_CHEATSHEET.md, it became the fourth file, and a three-file
    change left the Trivial path for the Simple one with nothing about the change being larger.

    S3166: a path git IGNORES is exempt from forbidNewFiles. A whole tree can be untracked by
    design - `.claude/` is here - and no commit ever holds it, so "absent from the base" cannot
    tell an edit from a creation there. The rule exists to catch new types, layouts and navigation
    graphs; applying it to an ignored tree escalated every edit under it instead.

.PARAMETER Id
    Ticket id, echoed in the verdict; also names the snapshot under temp/<Id>/.

.PARAMETER RecordBaseline
    Take the snapshot instead of judging. Requires -Id. Exits 0 after writing the sha.

.PARAMETER Files
    Comma-separated changed set, repository-relative.

.PARAMETER Deleted
    Comma-separated deleted paths; counted toward maxFiles, never judged as new.

.PARAMETER RepoRoot
    Work tree to judge. Defaults to this repository; the contract suite passes a fixture.

    Exit codes:
      0  trivial-scope: PASS - the set fits the checklist, or the baseline was recorded.
      1  trivial-scope: ESCALATE - the set outgrew it; the ticket continues on the Simple path.
      2  bad invocation - no -Files, -RecordBaseline without -Id, no readable `trivial` profile
         key, or not a git work tree.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-trivial-scope.ps1 -RecordBaseline -Id S3200

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-trivial-scope.ps1 -Id S3200 -Files "app_v2/src/main/res/values/strings.xml"
#>
[CmdletBinding()]
param(
    [string]$Id = '',
    [string]$Files = '',
    [string]$Deleted = '',
    [string]$RepoRoot = '',
    [switch]$RecordBaseline
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Stop-BadInvocation([string]$Reason) {
    Write-Host "trivial-scope: cannot judge - $Reason"
    exit 2
}

function Split-PathList([string]$Text) {
    return @($Text -split ',' |
            ForEach-Object { ($_.Trim() -replace '\\', '/') -replace '^\./', '' } |
            Where-Object { $_ })
}

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
if (-not (Test-Path -LiteralPath $RepoRoot)) { Stop-BadInvocation "repo root '$RepoRoot' does not exist" }

& git -C $RepoRoot rev-parse --is-inside-work-tree *> $null
if ($LASTEXITCODE -ne 0) { Stop-BadInvocation "'$RepoRoot' is not a git work tree" }

$label = if ($Id) { "$Id " } else { '' }
function Stop-Escalate([string]$Reason) {
    Write-Host "trivial-scope: ${label}ESCALATE - $Reason"
    exit 1
}
function Get-BaselinePath([string]$Ticket) {
    return (Join-Path $RepoRoot "temp/$Ticket/trivial-base.txt")
}
$HeadFallbackMarker = 'fallback-to-HEAD'

# S3872: `git stash create` refused a work tree whose index stat was stale ("not uptodate. Cannot
# merge") and the old code then recorded HEAD silently. A private copy of the index is refreshed and
# staged instead: the real index and the work tree are never written, and a failure is reported.
function New-WorkTreeSnapshot([string]$Parent) {
    $indexPath = "$(& git -C $RepoRoot rev-parse --git-path index 2> $null)".Trim()
    if (-not $indexPath) { return @{ Sha = ''; Reason = 'git rev-parse --git-path index failed' } }
    if (-not [System.IO.Path]::IsPathRooted($indexPath)) { $indexPath = Join-Path $RepoRoot $indexPath }
    if (-not (Test-Path -LiteralPath $indexPath)) { return @{ Sha = ''; Reason = "no index at $indexPath" } }
    $tempIndex = Join-Path ([System.IO.Path]::GetTempPath()) "trivial-scope-index-$PID-$([guid]::NewGuid().ToString('N'))"
    $previousIndex = $env:GIT_INDEX_FILE
    try {
        Copy-Item -LiteralPath $indexPath -Destination $tempIndex -Force
        $env:GIT_INDEX_FILE = $tempIndex
        # `add -u` stages tracked edits and deletions only, matching what `stash create` recorded.
        $addOut = & git -C $RepoRoot add -u 2>&1 | Out-String
        if ($LASTEXITCODE -ne 0) { return @{ Sha = ''; Reason = "git add -u on a private index: $($addOut.Trim())" } }
        $tree = "$(& git -C $RepoRoot write-tree 2> $null)".Trim()
        if (-not $tree) { return @{ Sha = ''; Reason = 'git write-tree on a private index failed' } }
        $sha = "$(& git -C $RepoRoot -c user.name=trivial-scope -c user.email=trivial-scope@localhost commit-tree $tree -p $Parent -m "trivial-scope baseline $Id" 2> $null)".Trim()
        if (-not $sha) { return @{ Sha = ''; Reason = 'git commit-tree failed' } }
        return @{ Sha = $sha; Reason = '' }
    }
    catch { return @{ Sha = ''; Reason = $_.Exception.Message } }
    finally {
        if ($null -eq $previousIndex) { Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue }
        else { $env:GIT_INDEX_FILE = $previousIndex }
        Remove-Item -LiteralPath $tempIndex -Force -ErrorAction SilentlyContinue
    }
}

if ($RecordBaseline) {
    if (-not $Id) { Stop-BadInvocation '-RecordBaseline needs -Id' }
    $head = "$(& git -C $RepoRoot rev-parse --verify -q HEAD 2> $null)".Trim()
    if (-not $head) { Stop-BadInvocation "'$RepoRoot' has no commit to use as a baseline" }
    $snapshot = New-WorkTreeSnapshot $head
    $basePath = Get-BaselinePath $Id
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $basePath) | Out-Null
    if ($snapshot.Sha) {
        Set-Content -LiteralPath $basePath -Value $snapshot.Sha -Encoding ascii
        Write-Host "trivial-scope: ${label}baseline $($snapshot.Sha)"
        exit 0
    }
    # The marker line lets the judging run repeat the warning instead of passing HEAD off as a snapshot.
    Set-Content -LiteralPath $basePath -Value @($head, $HeadFallbackMarker) -Encoding ascii
    Write-Host "trivial-scope: ${label}snapshot failed - $($snapshot.Reason)"
    Write-Host "trivial-scope: ${label}fallback to HEAD $head - uncommitted sibling lines will read as this ticket's"
    exit 0
}

$changed = @(Split-PathList $Files)
$removed = @(Split-PathList $Deleted)
if ($changed.Count -eq 0 -and $removed.Count -eq 0) { Stop-BadInvocation 'no -Files given' }

$profilePath = Join-Path $RepoRoot '.sza-profile.json'
$trivial = $null
try { $trivial = (Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json).trivial }
catch { Stop-BadInvocation "unreadable profile '$profilePath': $($_.Exception.Message)" }
if ($null -eq $trivial) { Stop-BadInvocation "no 'trivial' key in $profilePath" }

$base = 'HEAD'
$baseSource = 'HEAD (no snapshot)'
if ($Id) {
    $basePath = Get-BaselinePath $Id
    if (Test-Path -LiteralPath $basePath) {
        $candidate = ''
        $recordLines = @()
        try { $recordLines = @(Get-Content -LiteralPath $basePath | ForEach-Object { "$_".Trim() } | Where-Object { $_ }) }
        catch { $recordLines = @() }
        if ($recordLines.Count -gt 0) { $candidate = $recordLines[0] }
        if ($recordLines -contains $HeadFallbackMarker) {
            $candidate = ''
            $baseSource = 'fallback to HEAD - the snapshot failed at -RecordBaseline, sibling lines count as this ticket''s'
        }
        if ($candidate) {
            & git -C $RepoRoot cat-file -e "$candidate^{commit}" 2> $null
            if ($LASTEXITCODE -eq 0) {
                $base = $candidate
                $baseSource = "snapshot $($candidate.Substring(0, [Math]::Min(12, $candidate.Length)))"
            }
            else { $baseSource = 'HEAD (snapshot pruned)' }
        }
    }
}
Write-Host "trivial-scope: ${label}baseline: $baseSource"

$generatedPatterns = [System.Collections.Generic.List[string]]::new()
$registryPath = Join-Path $RepoRoot 'docs/DOCUMENT_REGISTRY.jsonl'
if (Test-Path -LiteralPath $registryPath) {
    foreach ($line in (Get-Content -LiteralPath $registryPath -Encoding UTF8)) {
        if (-not "$line".Trim()) { continue }
        # A malformed line is the registry validator's finding, not this gate's: skip it.
        $record = $null
        try { $record = "$line" | ConvertFrom-Json } catch { continue }
        if (-not ($record.PSObject.Properties.Name -contains 'generated') -or -not $record.generated) { continue }
        foreach ($p in @($record.paths)) { $generatedPatterns.Add(([string]$p -replace '\\', '/')) }
    }
}
function Test-GeneratedPath([string]$Path) {
    foreach ($pattern in $generatedPatterns) {
        if ($Path -ieq $pattern -or $Path -ilike $pattern -or $Path -ilike "$pattern/*") { return $true }
    }
    return $false
}

$judgedChanged = [System.Collections.Generic.List[string]]::new()
foreach ($path in @($changed | Where-Object { $_ -notmatch '^(PLAN|temp)/' })) {
    if (Test-GeneratedPath $path) {
        Write-Host "trivial-scope: ${label}$path is generated (document registry) - not counted."
        continue
    }
    $judgedChanged.Add($path)
}
$judgedRemoved = @($removed | Where-Object { $_ -notmatch '^(PLAN|temp)/' -and -not (Test-GeneratedPath $_) })
$total = $judgedChanged.Count + $judgedRemoved.Count
if ($total -gt [int]$trivial.maxFiles) {
    Stop-Escalate "$total files changed, the Trivial limit is $($trivial.maxFiles)"
}

$extensions = @($trivial.forbiddenPatternExtensions)
$patterns = @($trivial.forbiddenPatterns)
foreach ($path in $judgedChanged) {
    & git -C $RepoRoot cat-file -e "${base}:$path" 2> $null
    if ($LASTEXITCODE -ne 0) {
        & git -C $RepoRoot check-ignore -q -- $path 2> $null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "trivial-scope: ${label}$path is gitignored - no commit holds it, so it is not judged as new."
            continue
        }
        if ($trivial.forbidNewFiles) { Stop-Escalate "$path is a new file" }
        continue
    }
    if ([System.IO.Path]::GetExtension($path) -notin $extensions) { continue }
    # -U0 leaves only the changed lines; the '+++' file header is not an added line.
    $added = @(& git -C $RepoRoot diff -U0 $base -- $path |
            Where-Object { $_ -match '^\+(?!\+\+)' } |
            ForEach-Object { $_.Substring(1) })
    foreach ($line in $added) {
        foreach ($pattern in $patterns) {
            if ($line -match $pattern) { Stop-Escalate "$path adds '$($line.Trim())' (pattern $pattern)" }
        }
    }
}

Write-Host "trivial-scope: ${label}PASS - $total file(s) within the Trivial checklist."
exit 0
