# Backup all Layout files before dimension migration
# Creates timestamped backup in temp/layout_backups/

param(
    [string]$LayoutDir = "app_v2\src\main\res\layout",
    [string]$BackupDir = "temp\layout_backups"
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupPath = Join-Path $PSScriptRoot "..\..\$BackupDir\backup_$timestamp"

Write-Host "=== Layout Backup Script ===" -ForegroundColor Cyan
Write-Host "Source: $LayoutDir" -ForegroundColor Gray
Write-Host "Target: $backupPath" -ForegroundColor Gray
Write-Host ""

# Create backup directory
if (-not (Test-Path $backupPath)) {
    New-Item -ItemType Directory -Path $backupPath -Force | Out-Null
    Write-Host "[+] Created backup directory" -ForegroundColor Green
}

# Find all layout files
$layoutPath = Join-Path $PSScriptRoot "..\..\$LayoutDir"
$layoutFiles = Get-ChildItem -Path $layoutPath -Filter "*.xml" -File

if ($layoutFiles.Count -eq 0) {
    Write-Host "[!] No layout files found" -ForegroundColor Yellow
    exit 1
}

Write-Host "[*] Found $($layoutFiles.Count) layout files" -ForegroundColor Cyan

# Copy files
$copied = 0
foreach ($file in $layoutFiles) {
    try {
        Copy-Item -Path $file.FullName -Destination $backupPath -Force
        $copied++
        Write-Host "  [OK] $($file.Name)" -ForegroundColor Green
    }
    catch {
        Write-Host "  [ERR] $($file.Name): $_" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "[✓] Backup complete: $copied/$($layoutFiles.Count) files" -ForegroundColor Green
Write-Host "[✓] Location: $backupPath" -ForegroundColor Green

# Create manifest
$manifest = @{
    timestamp  = $timestamp
    filesCount = $copied
    sourceDir  = $LayoutDir
    files      = $layoutFiles.Name
} | ConvertTo-Json -Depth 3

$manifestPath = Join-Path $backupPath "manifest.json"
$manifest | Out-File -FilePath $manifestPath -Encoding UTF8
Write-Host "[✓] Manifest: $manifestPath" -ForegroundColor Green
