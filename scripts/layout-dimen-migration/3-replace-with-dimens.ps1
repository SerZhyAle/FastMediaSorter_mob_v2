# Replace hardcoded dimension values with @dimen references
# Uses dimensions library to perform replacements across all layouts

param(
    [string]$LibraryFile = "temp\dimensions_library.json",
    [string]$LayoutDir = "app_v2\src\main\res\layout",
    [switch]$DryRun = $false,
    [switch]$Verbose = $false
)

$ErrorActionPreference = "Stop"

Write-Host "=== Dimension Replacement Script ===" -ForegroundColor Cyan
Write-Host "Library: $LibraryFile" -ForegroundColor Gray
Write-Host "Target: $LayoutDir" -ForegroundColor Gray
Write-Host "Mode: $(if ($DryRun) { 'DRY RUN (no changes)' } else { 'LIVE (will modify files)' })" -ForegroundColor $(if ($DryRun) { 'Yellow' } else { 'Red' })
Write-Host ""

# Load dimensions library
$libraryPath = Join-Path $PSScriptRoot "..\..\$LibraryFile"

if (-not (Test-Path $libraryPath)) {
    Write-Host "[!] Library file not found: $libraryPath" -ForegroundColor Red
    Write-Host "[!] Run 1-extract-dimensions.ps1 first" -ForegroundColor Yellow
    exit 1
}

Write-Host "[*] Loading dimensions library..." -ForegroundColor Cyan
$library = Get-Content -Path $libraryPath -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable

if ($library.Count -eq 0) {
    Write-Host "[!] No dimensions found in library" -ForegroundColor Red
    exit 1
}

Write-Host "  [✓] Loaded $($library.Count) dimension mappings" -ForegroundColor Green
Write-Host ""

# Build replacement map: value -> dimen_name
$replacementMap = @{}
foreach ($entry in $library.GetEnumerator()) {
    $data = $entry.Value
    $value = $data.value
    $dimenName = $entry.Key
    
    # Store all possible dimen names for this value (for deduplication later)
    if (-not $replacementMap.ContainsKey($value)) {
        $replacementMap[$value] = @()
    }
    $replacementMap[$value] += $dimenName
}

Write-Host "[*] Built replacement map for $($replacementMap.Count) unique values" -ForegroundColor Cyan
Write-Host ""

# Statistics
$statsFiles = 0
$statsReplacements = 0
$statsSkipped = 0

# Find all layout files
$layoutPath = Join-Path $PSScriptRoot "..\..\$LayoutDir"
$layoutFiles = Get-ChildItem -Path $layoutPath -Filter "*.xml" -File

Write-Host "[*] Processing $($layoutFiles.Count) layout files..." -ForegroundColor Cyan
Write-Host ""

foreach ($file in $layoutFiles) {
    $layoutName = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
    $originalContent = $content
    $fileReplacements = 0
    
    if ($Verbose) {
        Write-Host "  Processing: $layoutName.xml" -ForegroundColor White
    }
    
    # Process each dimension value in library
    foreach ($entry in $library.GetEnumerator()) {
        $data = $entry.Value
        $dimenKey = $entry.Key
        $value = $data.value
        $attribute = $data.attribute
        $namespace = $data.namespace
        
        # Skip if already @dimen reference
        if ($value -match '@dimen/') {
            continue
        }
        
        # Build regex pattern to match this specific attribute + value
        # Matches: android:attribute="value" or app:attribute="value"
        $pattern = "$namespace$attribute=`"$([regex]::Escape($value))`""
        $replacement = "$namespace$attribute=`"@dimen/$dimenKey`""
        
        # Count matches before replacement
        $matches = [regex]::Matches($content, $pattern)
        
        if ($matches.Count -gt 0) {
            $content = $content -replace $pattern, $replacement
            $fileReplacements += $matches.Count
            
            if ($Verbose) {
                Write-Host "    [→] $attribute: $value → @dimen/$dimenKey ($($matches.Count) times)" -ForegroundColor Cyan
            }
        }
    }
    
    # Write back if changes were made
    if ($fileReplacements -gt 0) {
        $statsFiles++
        $statsReplacements += $fileReplacements
        
        if (-not $DryRun) {
            $content | Out-File -FilePath $file.FullName -Encoding UTF8 -NoNewline
            Write-Host "  [✓] $layoutName.xml: $fileReplacements replacements" -ForegroundColor Green
        } else {
            Write-Host "  [·] $layoutName.xml: $fileReplacements replacements (DRY RUN)" -ForegroundColor Yellow
        }
    } else {
        $statsSkipped++
        if ($Verbose) {
            Write-Host "  [·] $layoutName.xml: No changes" -ForegroundColor Gray
        }
    }
}

Write-Host ""
Write-Host "=== Replacement Complete ===" -ForegroundColor Cyan
Write-Host "  Files modified: $statsFiles" -ForegroundColor Green
Write-Host "  Total replacements: $statsReplacements" -ForegroundColor Green
Write-Host "  Files skipped: $statsSkipped" -ForegroundColor Gray
Write-Host ""

if ($DryRun) {
    Write-Host "[!] DRY RUN - No files were modified" -ForegroundColor Yellow
    Write-Host "[*] Run without -DryRun to apply changes" -ForegroundColor Cyan
} else {
    Write-Host "[✓] All dimension values replaced with @dimen references" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Copy temp\generated_dimens.xml to app_v2\src\main\res\values\dimens.xml" -ForegroundColor White
    Write-Host "  2. Review and consolidate duplicate dimensions" -ForegroundColor White
    Write-Host "  3. Test build: .\scripts\builders\build-debug.PS1" -ForegroundColor White
    Write-Host "  4. Manually group common dimensions (padding, margins, text sizes)" -ForegroundColor White
}

# Generate replacement report
$report = @{
    timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    mode = if ($DryRun) { "DRY_RUN" } else { "LIVE" }
    filesProcessed = $layoutFiles.Count
    filesModified = $statsFiles
    filesSkipped = $statsSkipped
    totalReplacements = $statsReplacements
    dimensionsUsed = $library.Count
}

$reportPath = Join-Path $PSScriptRoot "..\..\temp\replacement_report.json"
$report | ConvertTo-Json -Depth 3 | Out-File -FilePath $reportPath -Encoding UTF8

Write-Host ""
Write-Host "[✓] Report saved: $reportPath" -ForegroundColor Green
