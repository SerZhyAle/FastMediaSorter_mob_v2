#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/quality/lib/nested-worktrees.ps1 (S2333).

.DESCRIPTION
    The library decides which paths four repository-wide walks must ignore. Its two failure
    modes are opposite and both silent, so both are asserted here:

      excluding too little - the stale worktree copy is judged and every finding doubles;
      excluding too much   - '.claude/' rules, commands and agent memory stop being judged,
                             which is the corpus only these gates inspect.

    The prefix-boundary cases are not decoration: a bare StartsWith makes 'FastMediaSorter_v2'
    swallow a neighbour named 'FastMediaSorter_v2_backup', and makes '.claude/worktrees' swallow
    '.claude/worktrees-old'. Both were real risks in the first draft.

.NOTES
    Uses a throwaway temp directory for the git-unavailable case; never touches the live tree.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\lib\nested-worktrees.ps1')

$passed = 0
$failed = 0

function Test-Case {
    param([string]$Name, [scriptblock]$Body)
    try {
        & $Body
        $script:passed++
        Write-Host "  PASS  $Name"
    } catch {
        $script:failed++
        Write-Host "  FAIL  $Name - $($_.Exception.Message)"
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

Write-Host 'nested-worktrees contract tests'

# ---------------------------------------------------------------- Test-InNestedWorktree
$prefixes = @('.claude/worktrees/agent-a9995e64f45b63648')

Test-Case 'a path inside a nested worktree is excluded' {
    Assert-True (Test-InNestedWorktree -RelativePath '.claude/worktrees/agent-a9995e64f45b63648/PLAN/S0394.md' -Prefixes $prefixes) `
        'expected exclusion for a file inside the worktree'
}

Test-Case 'the worktree directory itself is excluded' {
    Assert-True (Test-InNestedWorktree -RelativePath '.claude/worktrees/agent-a9995e64f45b63648' -Prefixes $prefixes) `
        'expected exclusion for the worktree root itself'
}

Test-Case 'a backslash path is excluded identically' {
    Assert-True (Test-InNestedWorktree -RelativePath '.claude\worktrees\agent-a9995e64f45b63648\docs\X.md' -Prefixes $prefixes) `
        'expected exclusion regardless of slash style'
}

Test-Case 'a .claude file outside any worktree is KEPT' {
    Assert-True (-not (Test-InNestedWorktree -RelativePath '.claude/agents/android-kotlin-developer.md' -Prefixes $prefixes)) `
        'agent definitions must still be judged'
}

Test-Case 'agent memory is KEPT' {
    Assert-True (-not (Test-InNestedWorktree -RelativePath '.claude/agent-memory/android-rd-specialist/MEMORY.md' -Prefixes $prefixes)) `
        'agent memory is the corpus these gates uniquely inspect'
}

Test-Case 'an ordinary working-tree path is KEPT' {
    Assert-True (-not (Test-InNestedWorktree -RelativePath 'app_v2/src/main/java/com/sza/fastmediasorter/util/PackageManagerCompat.kt' -Prefixes $prefixes)) `
        'working-tree sources must still be judged'
}

Test-Case 'a sibling directory sharing the prefix name is KEPT' {
    Assert-True (-not (Test-InNestedWorktree -RelativePath '.claude/worktrees-old/stash/PLAN/S1.md' -Prefixes @('.claude/worktrees'))) `
        "'.claude/worktrees-old' is not inside '.claude/worktrees'"
}

Test-Case 'an empty prefix list excludes nothing' {
    Assert-True (-not (Test-InNestedWorktree -RelativePath '.claude/worktrees/x/y.md' -Prefixes @())) `
        'no nested worktree means nothing is dropped'
}

# ---------------------------------------------------------------- Get-NestedWorktreeRelativePath
Test-Case 'git unavailable falls back to the static prefix instead of throwing' {
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("s2333-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $tmp -Force | Out-Null
    try {
        $result = Get-NestedWorktreeRelativePath -RepoRoot $tmp -Refresh
        Assert-True ($result -contains '.claude/worktrees') `
            "expected the static fallback, got: $($result -join ', ')"
    } finally {
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Test-Case 'the live repository resolves without throwing' {
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
    $result = @(Get-NestedWorktreeRelativePath -RepoRoot $repoRoot -Refresh)
    # Zero is legitimate - it means no nested worktree exists right now. The assertion is only
    # that every returned prefix is repo-RELATIVE, since a caller compares it against relative
    # paths and an absolute one would silently match nothing.
    foreach ($p in $result) {
        Assert-True (-not ($p -match '^[A-Za-z]:')) "prefix must be repo-relative, got '$p'"
        Assert-True (-not $p.StartsWith('/')) "prefix must be repo-relative, got '$p'"
    }
}

Test-Case 'a worktree outside the repository root is not returned' {
    # P:/ANDROID/FastMediaSorter_release is a registered sibling worktree in this repository.
    # It sits outside the root, so no scan root can ever produce a path under it.
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
    $result = @(Get-NestedWorktreeRelativePath -RepoRoot $repoRoot -Refresh)
    foreach ($p in $result) {
        Assert-True (-not ($p -match '(?i)FastMediaSorter_release')) `
            "a sibling worktree must not be reported as nested, got '$p'"
    }
}

# ---------------------------------------------------------------- Get-NestedWorktreeSkipNotice
Test-Case 'skip notice is empty when nothing was skipped' {
    Assert-True ([string]::IsNullOrEmpty((Get-NestedWorktreeSkipNotice -SkippedFileCount 0 -Prefixes $prefixes))) `
        'a walk that dropped nothing must say nothing'
}

Test-Case 'skip notice names the count when something was skipped' {
    $notice = Get-NestedWorktreeSkipNotice -SkippedFileCount 19705 -Prefixes $prefixes
    Assert-True ($notice -match '19705') 'the notice must carry the count'
    Assert-True ($notice -match 'worktree') 'the notice must say what was skipped'
}

Write-Host ''
Write-Host ("nested-worktrees: expected: 0 failures | actual: {0} failure(s) across {1} case(s)" -f $failed, ($passed + $failed))

if ($failed -gt 0) { exit 1 }
exit 0
