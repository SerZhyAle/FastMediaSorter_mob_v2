#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Setup PowerShell alias for 'a' command
.DESCRIPTION
    Adds a function to PowerShell profile to enable 'a <command>' syntax
.NOTES
    Exit codes:
      0  the alias was added, or one was already present in the profile.
#>

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\project-paths.ps1"
$ProjectRoot = Get-ProjectRoot
$ProfilePath = $PROFILE

Write-Host "🔧 Setting up PowerShell alias..." -ForegroundColor Cyan
Write-Host ""

# Create profile if it doesn't exist
if (-not (Test-Path $ProfilePath)) {
    Write-Host "Creating PowerShell profile: $ProfilePath" -ForegroundColor Yellow
    New-Item -Path $ProfilePath -ItemType File -Force | Out-Null
}

# Check if alias already exists
$profileContent = Get-Content $ProfilePath -Raw -ErrorAction SilentlyContinue
if ($profileContent -match "function a \{") {
    Write-Host "⚠️  Alias 'a' already exists in profile" -ForegroundColor Yellow
    Write-Host "Profile: $ProfilePath" -ForegroundColor Gray
    exit 0
}

# Add function to profile
$aliasCode = @"

# FastMediaSorter project alias
function a {
    & "$ProjectRoot\a.ps1" @args
}
"@

Add-Content -Path $ProfilePath -Value $aliasCode

Write-Host "✅ Alias added to PowerShell profile!" -ForegroundColor Green
Write-Host ""
Write-Host "📝 Added function to: $ProfilePath" -ForegroundColor Gray
Write-Host ""
Write-Host "⚠️  ВАЖНО: Перезапустите PowerShell или выполните:" -ForegroundColor Yellow
Write-Host "   . `$PROFILE" -ForegroundColor Cyan
Write-Host ""
Write-Host "После этого можно использовать:" -ForegroundColor Gray
Write-Host "   a d    # Build debug" -ForegroundColor Green
Write-Host "   a db   # Fast debug without zip" -ForegroundColor Green
Write-Host "   a cd   # Clean + debug + zip" -ForegroundColor Green
Write-Host "   a cdb  # Clean + debug without zip" -ForegroundColor Green
Write-Host "   a cls  # Clean Gradle caches" -ForegroundColor Green
Write-Host "   a c    # Commit & push" -ForegroundColor Green
Write-Host "   a r    # Build release" -ForegroundColor Green
