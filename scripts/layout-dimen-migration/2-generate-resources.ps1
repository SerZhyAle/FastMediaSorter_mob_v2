# Generate resource XMLs from extracted resources library
# MERGES with existing files - preserves all current resources

param(
    [string]$InputFile = "temp\resources_library.json",
    [string]$OutputDir = "temp\generated_resources",
    [string]$ExistingResourcesDir = "app_v2\src\main\res\values",
    [switch]$GroupByLayout = $false,
    [switch]$SortByValue = $false,
    [switch]$MergeWithExisting = $true,
    [string[]]$Types = @('dimension', 'color_hex', 'string_literal', 'integer', 'boolean')
)

$ErrorActionPreference = "Stop"

Write-Host "=== Universal Resource XML Generation Script (MERGE MODE) ===" -ForegroundColor Cyan
Write-Host "Input: $InputFile" -ForegroundColor Gray
Write-Host "Output: $OutputDir" -ForegroundColor Gray
Write-Host "Existing: $ExistingResourcesDir" -ForegroundColor Gray
Write-Host "Types: $($Types -join ', ')" -ForegroundColor Gray
Write-Host "Merge: $(if ($MergeWithExisting) { 'YES' } else { 'NO' })" -ForegroundColor $(if ($MergeWithExisting) { 'Green' } else { 'Yellow' })
Write-Host ""

# Validate input
if (-not (Test-Path $InputFile)) {
    Write-Host "[!] ERROR: Input file not found: $InputFile" -ForegroundColor Red
    Write-Host "    Run 1-extract-all-resources.ps1 first!" -ForegroundColor Yellow
    exit 1
}

# Load library
Write-Host "[*] Loading resources library..." -ForegroundColor Cyan
$libraryJson = Get-Content -Path $InputFile -Raw -Encoding UTF8
$library = $libraryJson | ConvertFrom-Json -AsHashtable

Write-Host "  [✓] Loaded $($library.Count) resource entries" -ForegroundColor Green

# Create output directory
if (-not (Test-Path $OutputDir)) {
    New-Item -Path $OutputDir -ItemType Directory -Force | Out-Null
    Write-Host "  [✓] Created output directory: $OutputDir" -ForegroundColor Green
}
else {
    Write-Host "  [i] Using existing directory: $OutputDir" -ForegroundColor Gray
}

# Filter by types
$filtered = $library.GetEnumerator() | Where-Object {
    $_.Value.resourceType -in $Types
}

if ($filtered.Count -eq 0) {
    Write-Host "[!] No resources found for types: $($Types -join ', ')" -ForegroundColor Yellow
    Write-Host "    Available types in library:" -ForegroundColor Gray
    $library.Values.resourceType | Select-Object -Unique | ForEach-Object {
        Write-Host "      - $_" -ForegroundColor Gray
    }
    exit 0
}

Write-Host "  [✓] Filtered to $($filtered.Count) entries (types: $($Types -join ', '))" -ForegroundColor Green

# Map resource types to XML element names
$typeToElement = @{
    'dimension'      = 'dimen'
    'color_hex'      = 'color'
    'string_literal' = 'string'
    'integer'        = 'integer'
    'boolean'        = 'bool'
}

# Map resource types to file names
$typeToFile = @{
    'dimension'      = 'dimens.xml'
    'color_hex'      = 'colors.xml'
    'string_literal' = 'strings.xml'
    'integer'        = 'integers.xml'
    'boolean'        = 'bools.xml'
}

# Function to load existing resources from XML
function Load-ExistingResources {
    param([string]$filePath, [string]$elementName)
    
    $existing = @{}
    
    if (-not (Test-Path $filePath)) {
        return $existing
    }
    
    try {
        [xml]$xml = Get-Content -Path $filePath -Encoding UTF8
        $resources = $xml.resources.SelectNodes("/resources/$elementName")
        
        foreach ($res in $resources) {
            $name = $res.GetAttribute('name')
            $value = $res.InnerText
            if ($name -and $value) {
                $existing[$name] = $value
            }
        }
        
        Write-Host "    [✓] Loaded $($existing.Count) existing $elementName resources" -ForegroundColor Cyan
    }
    catch {
        Write-Host "    [!] Error loading existing file: $_" -ForegroundColor Yellow
    }
    
    return $existing
}

$generatedFiles = @()
$existingResourcesPath = Join-Path $PSScriptRoot "..\..\$ExistingResourcesDir"

# Group by type
$byType = $filtered | Group-Object { $_.Value.resourceType }

Write-Host ""
Write-Host "=== Generating Resource Files ===" -ForegroundColor Cyan

foreach ($typeGroup in $byType) {
    $resourceType = $typeGroup.Name
    $resources = $typeGroup.Group
    
    $elementName = $typeToElement[$resourceType]
    $fileName = $typeToFile[$resourceType]
    
    if (-not $elementName) {
        Write-Host "  [!] Unknown resource type: $resourceType (skipping)" -ForegroundColor Yellow
        continue
    }
    
    Write-Host ""
    Write-Host "[*] Generating $fileName ($($resources.Count) new entries)..." -ForegroundColor Cyan
    
    # Load existing resources if merge enabled
    $existingResources = @{}
    $existingContent = ""
    if ($MergeWithExisting) {
        $existingFile = Join-Path $existingResourcesPath $fileName
        $existingResources = Load-ExistingResources -filePath $existingFile -elementName $elementName
        
        # Read full existing content to preserve comments and structure
        if (Test-Path $existingFile) {
            $existingContentFull = Get-Content -Path $existingFile -Raw -Encoding UTF8
            # Extract content between <resources> tags
            if ($existingContentFull -match '(?s)<resources>(.*)</resources>') {
                $existingContent = $matches[1].Trim()
            }
        }
    }
    
    # Build XML
    $xmlBuilder = [System.Text.StringBuilder]::new()
    [void]$xmlBuilder.AppendLine('<?xml version="1.0" encoding="utf-8"?>')
    [void]$xmlBuilder.AppendLine('<resources>')
    
    # Add existing content if merging
    if ($MergeWithExisting -and $existingContent) {
        [void]$xmlBuilder.AppendLine()
        [void]$xmlBuilder.AppendLine("    <!-- ========================================= -->")
        [void]$xmlBuilder.AppendLine("    <!-- EXISTING RESOURCES (preserved) -->")
        [void]$xmlBuilder.AppendLine("    <!-- ========================================= -->")
        [void]$xmlBuilder.AppendLine($existingContent)
        [void]$xmlBuilder.AppendLine()
    }
    
    # Add new auto-generated section
    [void]$xmlBuilder.AppendLine("    <!-- ========================================= -->")
    [void]$xmlBuilder.AppendLine("    <!-- AUTO-GENERATED FROM LAYOUTS -->")
    [void]$xmlBuilder.AppendLine("    <!-- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') -->")
    [void]$xmlBuilder.AppendLine("    <!-- DO NOT EDIT - Regenerate using 2-generate-resources.ps1 -->")
    [void]$xmlBuilder.AppendLine("    <!-- ========================================= -->")
    [void]$xmlBuilder.AppendLine()
    
    # Sort resources
    if ($SortByValue) {
        $resources = $resources | Sort-Object { $_.Value.value }
    }
    elseif ($GroupByLayout) {
        $resources = $resources | Sort-Object { $_.Value.layout }, { $_.Key }
    }
    else {
        $resources = $resources | Sort-Object { $_.Key }
    }
    
    $currentLayout = $null
    $newCount = 0
    $skippedCount = 0
    
    foreach ($res in $resources) {
        $data = $res.Value
        
        # Skip if already exists in existing resources
        if ($MergeWithExisting -and $existingResources.ContainsKey($res.Key)) {
            $skippedCount++
            continue
        }
        
        # Add layout separator
        if ($GroupByLayout -and $data.layout -ne $currentLayout) {
            [void]$xmlBuilder.AppendLine()
            [void]$xmlBuilder.AppendLine("    <!-- ================================ -->")
            [void]$xmlBuilder.AppendLine("    <!-- Layout: $($data.layout) -->")
            [void]$xmlBuilder.AppendLine("    <!-- ================================ -->")
            $currentLayout = $data.layout
        }
        
        # Add resource entry
        $resName = $res.Key
        $value = $data.value
        $comment = ""
        
        # Add usage count
        if ($data.usages -and $data.usages.Count -gt 1) {
            $comment = " <!-- Used $($data.usages.Count) times -->"
        }
        
        [void]$xmlBuilder.AppendLine("    <$elementName name=`"$resName`">$value</$elementName>$comment")
        $newCount++
    }
    
    if ($skippedCount -gt 0) {
        [void]$xmlBuilder.AppendLine()
        [void]$xmlBuilder.AppendLine("    <!-- Skipped $skippedCount duplicate entries (already exist) -->")
    }
    
    [void]$xmlBuilder.AppendLine('</resources>')
    
    # Save
    $outputFile = Join-Path $OutputDir $fileName
    $xmlContent = $xmlBuilder.ToString()
    [System.IO.File]::WriteAllText($outputFile, $xmlContent, [System.Text.Encoding]::UTF8)
    
    $generatedFiles += $outputFile
    
    Write-Host "  [✓] ${fileName}:" -ForegroundColor Green
    if ($MergeWithExisting) {
        Write-Host "      Existing: $($existingResources.Count)" -ForegroundColor Cyan
        Write-Host "      New: ${newCount}" -ForegroundColor Green
        Write-Host "      Skipped (duplicates): ${skippedCount}" -ForegroundColor Yellow
        Write-Host "      Total: $($existingResources.Count + $newCount)" -ForegroundColor White
    }
    else {
        Write-Host "      Total: ${newCount}" -ForegroundColor Green
    }
    Write-Host "  [✓] Saved to: $outputFile" -ForegroundColor Green
}

Write-Host ""
Write-Host "=== Generation Complete ===" -ForegroundColor Green
Write-Host "Generated files:" -ForegroundColor Cyan
foreach ($file in $generatedFiles) {
    Write-Host "  • $file" -ForegroundColor Gray
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Review generated XMLs in: $OutputDir" -ForegroundColor Gray
Write-Host "  2. Check for value duplicates (different names, same value)" -ForegroundColor Gray
Write-Host "  3. Manually consolidate semantic equivalents" -ForegroundColor Gray
Write-Host "  4. Copy to app_v2/src/main/res/values/ (or merge manually)" -ForegroundColor Gray
Write-Host "  5. Run: .\scripts\layout-dimen-migration\3-replace-with-resources.ps1 -DryRun" -ForegroundColor Gray
