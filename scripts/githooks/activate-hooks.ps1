#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Activate the repo-stored git hooks for this clone (spec S0752).
.DESCRIPTION
    Points git at scripts/githooks via core.hooksPath so the pre-push guard
    (block accidental direct push to `main` from the dev worktree) is active.
    core.hooksPath is per-clone config (not committed), so this must run once
    per clone. The release worktree shares the same .git, so it inherits the
    hook automatically. Idempotent - safe to re-run.
#>

$ErrorActionPreference = 'Stop'

$repoRoot = (git rev-parse --show-toplevel).Trim()
Set-Location $repoRoot

git config core.hooksPath 'scripts/githooks'

# Ensure the hook keeps its executable bit in the index (matters once committed
# and checked out on POSIX hosts; harmless on Windows).
git update-index --chmod=+x scripts/githooks/pre-push 2>$null

$active = (git config --get core.hooksPath)
Write-Host "core.hooksPath = $active" -ForegroundColor Green
Write-Host "pre-push guard active: direct push to 'main' from the dev worktree is blocked." -ForegroundColor Cyan
Write-Host "Override (intentional manual main push): set FMS_ALLOW_MAIN_PUSH=1." -ForegroundColor DarkGray
