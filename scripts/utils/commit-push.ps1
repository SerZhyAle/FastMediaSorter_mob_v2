# Commit and Push Script for FastMediaSorter v2

param(
    [string]$Message,
    [switch]$NoPush
)

# Auto-generate commit message if not provided
if ([string]::IsNullOrWhiteSpace($Message)) {
    $Message = Get-Date -Format "yyMMddHHmm"
}

# Add all changes
Write-Host "Adding all changes..."
git add .

# Check if there are changes to commit
$status = git status --porcelain
if (-not $status) {
    Write-Host "No changes to commit. Exiting."
    exit 0
}

# Commit with the message
Write-Host "Committing with message: $Message"
git commit -m $Message

if ($NoPush) {
    Write-Host "Commit completed without push."
    exit 0
}

# Push to the current branch
Write-Host "Pushing to remote..."
$currentBranch = git rev-parse --abbrev-ref HEAD
git push --set-upstream origin $currentBranch

Write-Host "Commit and push completed successfully."
