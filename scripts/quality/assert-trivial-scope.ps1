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

.PARAMETER Id
    Ticket id, echoed in the verdict.

.PARAMETER Files
    Comma-separated changed set, repository-relative.

.PARAMETER Deleted
    Comma-separated deleted paths; counted toward maxFiles, never judged as new.

.PARAMETER RepoRoot
    Work tree to judge. Defaults to this repository; the contract suite passes a fixture.

    Exit codes:
      0  trivial-scope: PASS - the set fits the checklist.
      1  trivial-scope: ESCALATE - the set outgrew it; the ticket continues on the Simple path.
      2  bad invocation - no -Files, no readable `trivial` profile key, or not a git work tree.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-trivial-scope.ps1 -Id S3200 -Files "app_v2/src/main/res/values/strings.xml"
#>
[CmdletBinding()]
param(
    [string]$Id = '',
    [string]$Files = '',
    [string]$Deleted = '',
    [string]$RepoRoot = ''
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

$changed = @(Split-PathList $Files)
$removed = @(Split-PathList $Deleted)
if ($changed.Count -eq 0 -and $removed.Count -eq 0) { Stop-BadInvocation 'no -Files given' }

$profilePath = Join-Path $RepoRoot '.sza-profile.json'
$trivial = $null
try { $trivial = (Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json).trivial }
catch { Stop-BadInvocation "unreadable profile '$profilePath': $($_.Exception.Message)" }
if ($null -eq $trivial) { Stop-BadInvocation "no 'trivial' key in $profilePath" }

& git -C $RepoRoot rev-parse --is-inside-work-tree *> $null
if ($LASTEXITCODE -ne 0) { Stop-BadInvocation "'$RepoRoot' is not a git work tree" }

$label = if ($Id) { "$Id " } else { '' }
function Stop-Escalate([string]$Reason) {
    Write-Host "trivial-scope: ${label}ESCALATE - $Reason"
    exit 1
}

$judgedChanged = @($changed | Where-Object { $_ -notmatch '^(PLAN|temp)/' })
$judgedRemoved = @($removed | Where-Object { $_ -notmatch '^(PLAN|temp)/' })
$total = $judgedChanged.Count + $judgedRemoved.Count
if ($total -gt [int]$trivial.maxFiles) {
    Stop-Escalate "$total files changed, the Trivial limit is $($trivial.maxFiles)"
}

$extensions = @($trivial.forbiddenPatternExtensions)
$patterns = @($trivial.forbiddenPatterns)
foreach ($path in $judgedChanged) {
    & git -C $RepoRoot cat-file -e "HEAD:$path" 2> $null
    if ($LASTEXITCODE -ne 0) {
        if ($trivial.forbidNewFiles) { Stop-Escalate "$path is a new file" }
        continue
    }
    if ([System.IO.Path]::GetExtension($path) -notin $extensions) { continue }
    # -U0 leaves only the changed lines; the '+++' file header is not an added line.
    $added = @(& git -C $RepoRoot diff -U0 HEAD -- $path |
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
