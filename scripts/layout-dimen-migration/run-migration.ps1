# Master script for full layout dimension migration
# Runs all migration steps with user confirmations

param(
    [switch]$SkipBackup = $false,
    [switch]$AutoApply = $false
)

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     LAYOUT DIMENSION MIGRATION - MASTER PIPELINE          ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will:" -ForegroundColor White
Write-Host "  1. Backup all layout files" -ForegroundColor Gray
Write-Host "  2. Extract dimension values to JSON library" -ForegroundColor Gray
Write-Host "  3. Generate dimens.xml from library" -ForegroundColor Gray
Write-Host "  4. Replace hardcoded values with @dimen references" -ForegroundColor Gray
Write-Host ""

if (-not $AutoApply) {
    $confirm = Read-Host "Continue? (y/n)"
    if ($confirm -ne 'y') {
        Write-Host "[!] Migration cancelled" -ForegroundColor Yellow
        exit 0
    }
}

# ============================================
# STEP 0: BACKUP
# ============================================
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "STEP 0: BACKUP LAYOUT FILES" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

if ($SkipBackup) {
    Write-Host "[!] Backup skipped (SkipBackup flag)" -ForegroundColor Yellow
} else {
    & "$scriptDir\0-backup-layouts.ps1"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[✗] Backup failed" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Read-Host "Press Enter to continue to extraction..."

# ============================================
# STEP 1: EXTRACT
# ============================================
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "STEP 1: EXTRACT DIMENSIONS" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

& "$scriptDir\1-extract-dimensions.ps1"

if ($LASTEXITCODE -ne 0) {
    Write-Host "[✗] Extraction failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[*] Review summary: temp\dimensions_library_summary.json" -ForegroundColor Cyan
Read-Host "Press Enter to continue to dimens.xml generation..."

# ============================================
# STEP 2: GENERATE DIMENS.XML
# ============================================
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "STEP 2: GENERATE DIMENS.XML" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "Select organization mode:" -ForegroundColor Yellow
Write-Host "  1. Alphabetical (default)" -ForegroundColor White
Write-Host "  2. Grouped by layout" -ForegroundColor White
Write-Host "  3. Sorted by value" -ForegroundColor White
Write-Host ""

if (-not $AutoApply) {
    $mode = Read-Host "Enter choice (1-3)"
} else {
    $mode = "1"
}

switch ($mode) {
    "2" { & "$scriptDir\2-generate-dimens.ps1" -GroupByLayout }
    "3" { & "$scriptDir\2-generate-dimens.ps1" -SortByValue }
    default { & "$scriptDir\2-generate-dimens.ps1" }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "[✗] Generation failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
Write-Host "║  ⚠️  MANUAL REVIEW REQUIRED                                ║" -ForegroundColor Yellow
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
Write-Host ""
Write-Host "Before continuing, please:" -ForegroundColor White
Write-Host "  1. Open: temp\generated_dimens.xml" -ForegroundColor Cyan
Write-Host "  2. Review duplicate dimensions" -ForegroundColor Cyan
Write-Host "  3. Consolidate common values" -ForegroundColor Cyan
Write-Host "  4. Create semantic groups (margins, text sizes, etc.)" -ForegroundColor Cyan
Write-Host ""
Write-Host "Example consolidation:" -ForegroundColor Gray
Write-Host "  Before: activity_main_button_height_48dp" -ForegroundColor DarkGray
Write-Host "          activity_settings_icon_size_48dp" -ForegroundColor DarkGray
Write-Host "  After:  button_height_standard" -ForegroundColor Green
Write-Host "          icon_size_medium" -ForegroundColor Green
Write-Host ""

if (-not $AutoApply) {
    Read-Host "Press Enter when review is complete..."
}

# ============================================
# STEP 3: DRY RUN
# ============================================
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "STEP 3: DRY RUN REPLACEMENT" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

Write-Host "[*] Running dry run to preview changes..." -ForegroundColor Yellow
& "$scriptDir\3-replace-with-dimens.ps1" -DryRun -Verbose

if ($LASTEXITCODE -ne 0) {
    Write-Host "[✗] Dry run failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[*] Review the output above" -ForegroundColor Cyan

if (-not $AutoApply) {
    $confirm = Read-Host "Apply changes to layout files? (y/n)"
    if ($confirm -ne 'y') {
        Write-Host "[!] Migration cancelled" -ForegroundColor Yellow
        Write-Host "[*] To resume: .\3-replace-with-dimens.ps1" -ForegroundColor Cyan
        exit 0
    }
}

# ============================================
# STEP 4: APPLY CHANGES
# ============================================
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Red
Write-Host "STEP 4: APPLY REPLACEMENT" -ForegroundColor Red
Write-Host "═══════════════════════════════════════════════════════════" -ForegroundColor Red
Write-Host ""

Write-Host "⚠️  WARNING: This will modify all layout files!" -ForegroundColor Red
Write-Host ""

if (-not $AutoApply) {
    $finalConfirm = Read-Host "Type 'APPLY' to confirm"
    if ($finalConfirm -ne 'APPLY') {
        Write-Host "[!] Migration cancelled" -ForegroundColor Yellow
        exit 0
    }
}

& "$scriptDir\3-replace-with-dimens.ps1" -Verbose

if ($LASTEXITCODE -ne 0) {
    Write-Host "[✗] Replacement failed" -ForegroundColor Red
    Write-Host "[*] Restore from backup: temp\layout_backups\" -ForegroundColor Yellow
    exit 1
}

# ============================================
# FINALIZATION
# ============================================
Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║  ✅ MIGRATION COMPLETE                                     ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Copy dimens.xml to resources:" -ForegroundColor White
Write-Host "     Copy-Item temp\generated_dimens.xml app_v2\src\main\res\values\dimens.xml" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. Build project:" -ForegroundColor White
Write-Host "     .\scripts\builders\build-debug.PS1" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. If build fails, restore from backup:" -ForegroundColor White
Write-Host "     Copy-Item temp\layout_backups\backup_*\* app_v2\src\main\res\layout\" -ForegroundColor Gray
Write-Host ""
Write-Host "  4. Review reports:" -ForegroundColor White
Write-Host "     - temp\dimensions_library_summary.json" -ForegroundColor Gray
Write-Host "     - temp\replacement_report.json" -ForegroundColor Gray
Write-Host ""

Write-Host "Output files:" -ForegroundColor Cyan
Write-Host "  📁 Backups: temp\layout_backups\" -ForegroundColor Gray
Write-Host "  📄 Library: temp\dimensions_library.json" -ForegroundColor Gray
Write-Host "  📄 Dimens:  temp\generated_dimens.xml" -ForegroundColor Gray
Write-Host "  📄 Report:  temp\replacement_report.json" -ForegroundColor Gray
Write-Host ""

Write-Host "[✓] Done!" -ForegroundColor Green
