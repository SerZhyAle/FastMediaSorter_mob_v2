# Replace hardcoded resource values with resource references (@dimen, @color, @string, etc.)
# Uses resources library to perform replacements across all layouts

param(
    [string]$LibraryFile = "temp\resources_library.json",
    [string]$LayoutDir = "app_v2\src\main\res\layout",
    [switch]$DryRun = $false,
    [switch]$Verbose = $false,
    [string[]]$Types = @('dimension', 'color_hex', 'string_literal', 'integer', 'boolean')
)

$ErrorActionPreference = "Stop"

Write-Host "=== Universal Resource Replacement Script ===" -ForegroundColor Cyan
Write-Host "Library: $LibraryFile" -ForegroundColor Gray
Write-Host "Target: $LayoutDir" -ForegroundColor Gray
Write-Host "Types: $($Types -join ', ')" -ForegroundColor Gray
Write-Host "Mode: $(if ($DryRun) { 'DRY RUN (no changes)' } else { 'LIVE (will modify files)' })" -ForegroundColor $(if ($DryRun) { 'Yellow' } else { 'Red' })
Write-Host ""

# Load resources library
$libraryPath = Join-Path $PSScriptRoot "..\..\$LibraryFile"

if (-not (Test-Path $libraryPath)) {
    Write-Host "[!] Library file not found: $libraryPath" -ForegroundColor Red
    Write-Host "[!] Run 1-extract-dimensions.ps1 first" -ForegroundColor Yellow
    exit 1
}

Write-Host "[*] Loading resources library..." -ForegroundColor Cyan
$library = Get-Content -Path $libraryPath -Raw -Encoding UTF8 | ConvertFrom-Json -AsHashtable

if ($library.Count -eq 0) {
    Write-Host "[!] No resources found in library" -ForegroundColor Red
    exit 1
}

Write-Host "  [✓] Loaded $($library.Count) resource mappings" -ForegroundColor Green
Write-Host ""

# Map resource types to reference prefixes
$typeToPrefix = @{
    'dimension' = '@dimen/'
    'color_hex' = '@color/'
    'string_literal' = '@string/'
    'integer' = '@integer/'
    'boolean' = '@bool/'
}

# Filter resources by requested types
$filteredLibrary = @{}
foreach ($entry in $library.GetEnumerator()) {
    $data = $entry.Value
    if ($Types -contains $data.resourceType) {
        $filteredLibrary[$entry.Key] = $data
    }
}

Write-Host "[*] Filtered to $($filteredLibrary.Count) resources for replacement" -ForegroundColor Cyan
Write-Host ""

# Statistics
$statsFiles = 0
$statsReplacements = 0
$statsSkipped = 0
$statsByType = @{}

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
    $fileReplacementsByType = @{}
    
    if ($Verbose) {
        Write-Host "  Processing: $layoutName.xml" -ForegroundColor White
    }
    
    # Process each resource in library
    foreach ($entry in $filteredLibrary.GetEnumerator()) {
        $data = $entry.Value
        $resourceKey = $entry.Key
        $value = $data.value
        $attribute = $data.attribute
        $namespace = $data.namespace
        $resourceType = $data.resourceType
        
        # Get resource reference prefix
        $prefix = $typeToPrefix[$resourceType]
        if (-not $prefix) {
            continue
        }
        
        # Build regex pattern to match this specific attribute + value
        # Matches: android:attribute="value" or app:attribute="value"
        $escapedValue = [regex]::Escape($value)
        $pattern = "$namespace$attribute=`"$escapedValue`""
        $replacement = "$namespace$attribute=`"$prefix$resourceKey`""
        
        # Count matches before replacement
        $matches = [regex]::Matches($content, $pattern)
        
        if ($matches.Count -gt 0) {
            $content = $content -replace $pattern, $replacement
            $fileReplacements += $matches.Count
            
            # Track by type
            if (-not $fileReplacementsByType.ContainsKey($resourceType)) {
                $fileReplacementsByType[$resourceType] = 0
            }
            $fileReplacementsByType[$resourceType] += $matches.Count
            
            if ($Verbose) {
                Write-Host "    [→] $attribute ($resourceType): $value → $prefix$resourceKey ($($matches.Count)x)" -ForegroundColor Cyan
            }
        }
    }
    
    # Write back if changes were made
    if ($fileReplacements -gt 0) {
        $statsFiles++
        $statsReplacements += $fileReplacements
        
        # Aggregate by type
        foreach ($typeCount in $fileReplacementsByType.GetEnumerator()) {
            if (-not $statsByType.ContainsKey($typeCount.Key)) {
                $statsByType[$typeCount.Key] = 0
            }
            $statsByType[$typeCount.Key] += $typeCount.Value
        }
        
        if (-not $DryRun) {
            $content | Out-File -FilePath $file.FullName -Encoding UTF8 -NoNewline
            Write-Host "  [✓] $layoutName.xml: $fileReplacements replacements" -ForegroundColor Green
        } else {
            Write-Host "  [·] $layoutName.xml: $fileReplacements replacements (DRY RUN)" -ForegroundColor Yellow
        }
        
        if ($Verbose) {
            foreach ($typeCount in $fileReplacementsByType.GetEnumerator()) {
                Write-Host "      - $($typeCount.Key): $($typeCount.Value)" -ForegroundColor DarkGray
            }
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

Write-Host "Replacements by type:" -ForegroundColor Yellow
$statsByType.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
    Write-Host "  $($_.Key): $($_.Value)" -ForegroundColor Gray
}
Write-Host ""

if ($DryRun) {
    Write-Host "[!] DRY RUN - No files were modified" -ForegroundColor Yellow
    Write-Host "[*] Run without -DryRun to apply changes" -ForegroundColor Cyan
} else {
    Write-Host "[✓] All resource values replaced with references" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  1. Copy generated XMLs from temp\generated_resources\ to app_v2\src\main\res\values\" -ForegroundColor White
    Write-Host "  2. Test build: .\scripts\builders\build-debug.PS1" -ForegroundColor White
    Write-Host "  3. Review and consolidate duplicate resources" -ForegroundColor White
    Write-Host "  4. Create semantic resource groups (e.g., common_margin_small)" -ForegroundColor White
}

# Generate replacement report
$report = @{
    timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    mode = if ($DryRun) { "DRY_RUN" } else { "LIVE" }
    filesProcessed = $layoutFiles.Count
    filesModified = $statsFiles
    filesSkipped = $statsSkipped
    totalReplacements = $statsReplacements
    byType = $statsByType
    resourcesUsed = $filteredLibrary.Count
    types = $Types
}

$reportPath = Join-Path $PSScriptRoot "..\..\temp\replacement_report.json"
$report | ConvertTo-Json -Depth 3 | Out-File -FilePath $reportPath -Encoding UTF8

Write-Host ""
Write-Host "[✓] Report saved: $reportPath" -ForegroundColor Green
