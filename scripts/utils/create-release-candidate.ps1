#!/usr/bin/env pwsh
# create-release-candidate.ps1
# Creates a Release Candidate: bumps version, builds release APK/AAB,
# generates changelog, creates git tag, and optionally pushes.
#
# Usage:
#   .\create-release-candidate.ps1
#   .\create-release-candidate.ps1 -SkipBuild   # Only tag + changelog
#   .\create-release-candidate.ps1 -Push         # Auto-push tag to remote

param(
    [switch]$SkipBuild,   # Skip the actual Gradle build (for testing the script)
    [switch]$Push,        # Push git tag to remote after creating
    [string]$Flavor = "standard"  # Which flavor to use for release AAB
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
Set-Location $repoRoot

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " FastMediaSorter - Release Candidate" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# Step 1: Generate version (reuse existing logic from build-with-version.ps1)
$now = Get-Date
$firstYearDigit = [int]($now.Year.ToString()[0].ToString())
$lastYearDigit  = [int]($now.Year.ToString()[-1].ToString())
$firstMonthDigit  = [int]($now.Month.ToString("00")[0].ToString())
$secondMonthDigit = [int]($now.Month.ToString("00")[1].ToString())
$dayStr = $now.Day.ToString("00")
$firstHourDigit  = [int]($now.Hour.ToString("00")[0].ToString())
$secondHourDigit = [int]($now.Hour.ToString("00")[1].ToString())
$minuteStr = $now.Minute.ToString("00")
$firstMinuteDigit = [int]($minuteStr[0].ToString())

$versionCodeStr = $now.ToString("yyMMddHH") + $firstMinuteDigit.ToString()
$versionCode = [Convert]::ToInt32($versionCodeStr)
$versionName = "$firstYearDigit.$lastYearDigit$firstMonthDigit.$secondMonthDigit$dayStr$firstHourDigit.$secondHourDigit$minuteStr"

Write-Host ""
Write-Host "Version Name : $versionName" -ForegroundColor Green
Write-Host "Version Code : $versionCode" -ForegroundColor Green
Write-Host ""

# Step 2: Verify no uncommitted changes
$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Warning "Uncommitted changes detected:"
    git status --short
    $confirm = Read-Host "Continue anyway? (y/N)"
    if ($confirm -ne "y") { exit 1 }
}

# Step 3: Build Release (unless skipped)
if (-not $SkipBuild) {
    Write-Host "Building $Flavor release..." -ForegroundColor Cyan
    $flavorCapitalized = $Flavor.Substring(0,1).ToUpper() + $Flavor.Substring(1)

    # Run the version-bumping build script
    & "$repoRoot\dev\build-with-version.ps1"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Release build failed!"
        exit 1
    }
    Write-Host "Build succeeded." -ForegroundColor Green
} else {
    Write-Host "SkipBuild: skipping Gradle build." -ForegroundColor Yellow
}

# Step 4: Generate changelog
Write-Host ""
Write-Host "Generating changelog..." -ForegroundColor Cyan
$changelogFile = "temp\CHANGELOG_RC_$versionName.md"
New-Item -ItemType Directory -Force -Path "temp" | Out-Null
& "$scriptDir\generate-changelog.ps1" -OutputFile $changelogFile
Write-Host "Changelog saved to: $changelogFile" -ForegroundColor Green

# Step 5: Create git tag
$tagName = "rc/$versionName"
Write-Host ""
Write-Host "Creating git tag: $tagName" -ForegroundColor Cyan

# Read first 500 chars of changelog for tag message
$changelogContent = Get-Content $changelogFile -Raw -ErrorAction SilentlyContinue
$tagMessage = if ($changelogContent) {
    "RC $versionName`n`n" + $changelogContent.Substring(0, [Math]::Min(500, $changelogContent.Length))
} else {
    "Release Candidate $versionName"
}

git tag -a $tagName -m $tagMessage
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create tag $tagName"
    exit 1
}
Write-Host "Tag created: $tagName" -ForegroundColor Green

# Step 6: Push (optional)
if ($Push) {
    Write-Host ""
    Write-Host "Pushing tag to remote..." -ForegroundColor Cyan
    git push origin $tagName
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Tag pushed to remote." -ForegroundColor Green
    } else {
        Write-Warning "Failed to push tag. Push manually: git push origin $tagName"
    }
} else {
    Write-Host ""
    Write-Host "Tag NOT pushed. To push: git push origin $tagName" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " RC Complete: $tagName" -ForegroundColor Green
Write-Host " Changelog  : $changelogFile" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
