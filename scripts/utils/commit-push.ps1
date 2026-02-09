# Commit and Push Script for FastMediaSorter v2

param(
    [string]$Message
)

# Check if a commit message was provided
if (-not $Message) {
    $Message = Read-Host "Enter commit message"
}

if (-not $Message) {
    Write-Host "No commit message provided. Exiting."
    exit 1
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

# Push to the current branch
Write-Host "Pushing to remote..."
$currentBranch = git rev-parse --abbrev-ref HEAD
git push --set-upstream origin $currentBranch

Write-Host "Commit and push completed successfully."