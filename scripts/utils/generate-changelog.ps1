#!/usr/bin/env pwsh
# generate-changelog.ps1
# Generates a CHANGELOG section from git log between two refs (tags or commits).
# Output is formatted for Play Store "What's New" and GitHub release notes.
#
# Usage:
#   .\generate-changelog.ps1                        # from last tag to HEAD
#   .\generate-changelog.ps1 -From v1.0 -To HEAD
#   .\generate-changelog.ps1 -MaxEntries 30 -OutputFile "CHANGELOG_DRAFT.md"

param(
    [string]$From = "",          # Start ref (exclusive). Empty = auto-detect last tag
    [string]$To = "HEAD",       # End ref (inclusive)
    [int]$MaxEntries = 50,       # Cap number of entries per category
    [string]$OutputFile = "",    # Write to file (optional)
    [switch]$PlayStore           # Format for Play Store (plain text, 500 char limit)
)

$ErrorActionPreference = "Stop"

# Navigate to repo root
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
Set-Location $repoRoot

# Auto-detect last tag if From not specified
if ([string]::IsNullOrEmpty($From)) {
    try {
        $lastTag = git describe --tags --abbrev=0 2>$null
        if ($lastTag) {
            $From = $lastTag
            Write-Host "Auto-detected last tag: $lastTag" -ForegroundColor Gray
        } else {
            $From = (git rev-list --max-parents=0 HEAD).Trim()
            Write-Host "No tags found, using initial commit: $From" -ForegroundColor Yellow
        }
    } catch {
        $From = (git rev-list --max-parents=0 HEAD).Trim()
    }
}

$range = if ($From) { "$From..$To" } else { $To }
Write-Host "Generating changelog for range: $range" -ForegroundColor Cyan

# Collect commits
$format = "%s|%an|%ad|%H"
$rawCommits = git log $range --format=$format --date=short --no-merges 2>&1

if (-not $rawCommits -or $rawCommits -match "fatal:") {
    Write-Warning "No commits found for range: $range"
    exit 0
}

# Category keywords (matches commit message prefix)
$categories = [ordered]@{
    "feat"     = @{ label = "✨ New Features"; entries = @() }
    "fix"      = @{ label = "🐛 Bug Fixes"; entries = @() }
    "perf"     = @{ label = "⚡ Performance"; entries = @() }
    "refactor" = @{ label = "♻️ Refactoring"; entries = @() }
    "test"     = @{ label = "🧪 Tests"; entries = @() }
    "ci"       = @{ label = "🔧 CI/Build"; entries = @() }
    "docs"     = @{ label = "📖 Documentation"; entries = @() }
    "chore"    = @{ label = "🗂 Chore"; entries = @() }
    "other"    = @{ label = "💡 Other Changes"; entries = @() }
}

$totalCount = 0

foreach ($line in $rawCommits) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $parts = $line -split '\|', 4
    $subject = $parts[0].Trim()
    $shortHash = if ($parts.Count -ge 4) { $parts[3].Substring(0, [Math]::Min(7, $parts[3].Length)) } else { "" }

    # Determine category from conventional commit prefix
    $matched = $false
    foreach ($prefix in @("feat", "fix", "perf", "refactor", "test", "ci", "docs", "chore")) {
        if ($subject -match "^$prefix(\(.+\))?(!)?:") {
            $body = ($subject -replace "^$prefix(\(.+\))?(!)?:\s*", "").Trim()
            $scope = if ($subject -match "^$prefix\((.+?)\)") { " [$($Matches[1])]" } else { "" }
            if ($categories[$prefix].entries.Count -lt $MaxEntries) {
                $categories[$prefix].entries += "- $body$scope ($shortHash)"
            }
            $matched = $true
            break
        }
    }

    if (-not $matched) {
        # Heuristic: A-D task codes (e.g. "A4:", "B2:")
        if ($subject -match "^[A-D]\d+:") {
            $body = ($subject -replace "^[A-D]\d+:\s*", "").Trim()
            if ($categories["other"].entries.Count -lt $MaxEntries) {
                $categories["other"].entries += "- $body ($shortHash)"
            }
        } else {
            if ($categories["other"].entries.Count -lt $MaxEntries) {
                $categories["other"].entries += "- $subject ($shortHash)"
            }
        }
    }
    $totalCount++
}

# Build output
$lines = @()
$date = (Get-Date).ToString("yyyy-MM-dd")

if (-not $PlayStore) {
    $lines += "## Changelog - $date"
    $lines += ""
    $lines += "> Range: \`$range\`  |  Commits: $totalCount"
    $lines += ""
}

foreach ($key in $categories.Keys) {
    $cat = $categories[$key]
    if ($cat.entries.Count -eq 0) { continue }

    if ($PlayStore) {
        $lines += $cat.label
    } else {
        $lines += "### " + $cat.label
    }

    foreach ($entry in $cat.entries) {
        $lines += $entry
    }
    $lines += ""
}

$output = $lines -join "`n"

if ([string]::IsNullOrEmpty($OutputFile)) {
    Write-Output $output
} else {
    $output | Set-Content -Path $OutputFile -Encoding UTF8
    Write-Host "Changelog written to: $OutputFile" -ForegroundColor Green
}

if ($PlayStore) {
    $charCount = $output.Length
    $color = if ($charCount -le 500) { "Green" } else { "Red" }
    Write-Host "Play Store character count: $charCount / 500" -ForegroundColor $color
}
