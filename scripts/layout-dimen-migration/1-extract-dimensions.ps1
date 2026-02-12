# Extract ALL attribute values from Layout files (dimensions, colors, drawables, strings, etc.)
# Generates unified JSON library with resource types classification
# Key format: layoutname_viewid_attribute_value

param(
    [string]$LayoutDir = "app_v2\src\main\res\layout",
    [string]$OutputFile = "temp\resources_library.json"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Universal Resource Extraction Script ===" -ForegroundColor Cyan
Write-Host "Source: $LayoutDir" -ForegroundColor Gray
Write-Host "Output: $OutputFile" -ForegroundColor Gray
Write-Host ""

# Universal pattern to extract ALL attributes
# Captures: namespace (android:|app:), attribute name, and value
$universalPattern = '(android:|app:)([a-zA-Z_][a-zA-Z0-9_]*)="([^"]+)"'

# Function to classify resource type
function Get-ResourceType {
    param([string]$value)
    
    if ($value -match '^@dimen/') { return 'dimen_ref' }
    if ($value -match '^@color/') { return 'color_ref' }
    if ($value -match '^@drawable/') { return 'drawable_ref' }
    if ($value -match '^@string/') { return 'string_ref' }
    if ($value -match '^@style/') { return 'style_ref' }
    if ($value -match '^@\+id/') { return 'id_ref' }
    if ($value -match '^@android:') { return 'android_ref' }
    if ($value -match '^\?attr/') { return 'attr_ref' }
    if ($value -match '\d+(dp|sp)$') { return 'dimension' }
    if ($value -match '^#[0-9A-Fa-f]{3,8}$') { return 'color_hex' }
    if ($value -match '^(true|false)$') { return 'boolean' }
    if ($value -match '^\d+$') { return 'integer' }
    if ($value -match '^(match_parent|wrap_content|0dp)$') { return 'special' }
    if ($value -match '^\d+(\.\d+)?(px|dip|mm|in|pt)$') { return 'dimension_other' }
    
    return 'string_literal'
}

# Function to determine if resource should be extracted
function Should-Extract {
    param([string]$type, [string]$attribute, [ref]$skipReason)
    
    # Skip special layout values
    if ($type -eq 'special') { 
        $skipReason.Value = 'special_values'
        return $false 
    }
    
    # Skip ID references
    if ($type -eq 'id_ref') { 
        $skipReason.Value = 'system_attributes'
        return $false 
    }
    
    # Skip android framework references
    if ($type -eq 'android_ref') { 
        $skipReason.Value = 'system_attributes'
        return $false 
    }
    
    # Skip attribute references
    if ($type -eq 'attr_ref') { 
        $skipReason.Value = 'attribute_refs'
        return $false 
    }
    
    # Skip already referenced resources (they're already unified)
    if ($type -match '_ref$') { 
        $skipReason.Value = 'already_referenced'
        return $false 
    }
    
    # Skip certain system attributes
    $skipAttributes = @('id', 'layout_constraintTop_toTopOf', 'layout_constraintBottom_toBottomOf',
        'layout_constraintStart_toStartOf', 'layout_constraintEnd_toEndOf',
        'layout_constraintLeft_toLeftOf', 'layout_constraintRight_toRightOf',
        'layout_constraintHorizontal_chainStyle', 'layout_constraintVertical_chainStyle',
        'orientation', 'gravity', 'visibility', 'enabled', 'clickable', 'focusable',
        'importantForAccessibility', 'contentDescription', 'baselineAligned',
        'ellipsize', 'singleLine', 'maxLines', 'lines')
    
    if ($skipAttributes -contains $attribute) { 
        $skipReason.Value = 'system_attributes'
        return $false 
    }
}

# Storage for extracted resources
$resourcesLibrary = @{}
$statsByType = @{}
$statsTotal = 0
$statsSkipped = 0
$statsSkippedByReason = @{
    'already_referenced' = 0
    'special_values'     = 0
    'system_attributes'  = 0
    'attribute_refs'     = 0
}

# Find all layout files
$layoutPath = Join-Path $PSScriptRoot "..\..\$LayoutDir"
$layoutFiles = Get-ChildItem -Path $layoutPath -Filter "*.xml" -File

Write-Host "[*] Processing $($layoutFiles.Count) layout files..." -ForegroundColor Cyan
Write-Host ""

foreach $skipReason = $null
if (-not (Should-Extract -type $resourceType -attribute $attribute -skipReason ([ref]$skipReason))) {
    $statsSkipped++
    if ($skipReason) {
        $statsSkippedByReason[$skipReason]++
    }O.Path]::GetFileNameWithoutExtension($file.Name)
    Write-Host "  Processing: $layoutName.xml" -ForegroundColor White
    
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
    
    # Extract all attributes
    $matches = [regex]::Matches($content, $universalPattern)
    
    $fileCount = 0
    foreach ($match in $matches) {
        $namespace = $match.Groups[1].Value  # android: or app:
        $attribute = $match.Groups[2].Value  # e.g., layout_width, textColor
        $fullValue = $match.Groups[3].Value  # e.g., 48dp, @color/primary, #FF0000
        
        # Classify resource type
        $resourceType = Get-ResourceType -value $fullValue
        
        # Check if should extract
        if (-not (Should-Extract -type $resourceType -attribute $attribute)) {
            $statsSkipped++
            continue
        }
        
        # Try to extract view ID from context
        $viewId = "unknown"
        $position = $match.Index
        
        # Search backwards for android:id
        $preContext = $content.Substring(0, $position)
        $idMatch = [regex]::Match($preContext, 'android:id="@\+id/([^"]+)"(?=[^>]*$)')
        
        if ($idMatch.Success) {
            $viewId = $idMatch.Groups[1].Value
        }
        
        # Generate full key
        $cleanValue = $fullValue -replace '[^a-zA-Z0-9]', '_'
        $key = "${layoutName}_${viewId}_${attribute}_${cleanValue}"
        
        # Store resource
        if (-not $resourcesLibrary.ContainsKey($key)) {
            $resourcesLibrary[$key] = @{
                layout       = $layoutName
                viewId       = $viewId
                attribute    = $attribute
                namespace    = $namespace
                value        = $fullValue
                resourceType = $resourceType
                resourceName = $key
                usages       = @()
            }
            
            # Track stats by type
            if (-not $statsByType.ContainsKey($resourceType)) {
                $statsByType[$resourceType] = 0
            }
            $statsByType[$resourceType]++
        }
        
        # Track usage
        $resourcesLibrary[$key].usages += @{
            file = $file.Name
            line = ($content.Substring(0, $position) -split "`n").Count
        }
        
        $fileCount++
        $statsTotal++
    }
    
    if ($fileCount -gt 0) {
        Write-Host "    [✓] Extracted $fileCount resources" -ForegroundColor Green
    }
    else {
        Write-Host "    [·] No extractable resources" -ForegroundColor Gray
    }
}

Write-Host "": $statsSkipped" -ForegroundColor Yellow
Write-Host ""

Write-Host "Extracted resources by type:" -ForegroundColor Yellow
$statsByType.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
    Write-Host "  $($_.Key): $($_.Value)" -ForegroundColor Green
}
Write-Host ""

Write-Host "Skipped items (already unified or system):" -ForegroundColor Yellow
$statsSkippedByReason.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
    $reason = switch ($_.Key) {
        'already_referenced' { 'Already using resources (@dimen, @string, @drawable, etc.)' }
        'special_values' { 'Special values (match_parent, wrap_content, 0dp)' }
        'system_attributes' { 'System attributes (id, constraints, orientation, etc.)' }
        'attribute_refs' { 'Theme attributes (?attr/)' }
        default { $_.Key }
    }
    Write-Host "  $reason

Write-Host "Resources by type:" -ForegroundColor Yellow
$statsByType.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
    Write-Host "  $($_.Key): $($_.Value)" -ForegroundColor Gray
}
Write-Host ""

# Save to JSON
$outputPath = Join-Path $PSScriptRoot "..\..\$OutputFile"
$outputDir = Split-Path -Parent $outputPath

if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$json = $resourcesLibrary | ConvertTo-Json -Depth 5
$json | Out-File -FilePath $outputPath -Encoding UTF8

Write-Host "[✓] Library saved: $outputPath" -ForegroundColor Green
Write-Host "[✓] Size: $([math]::Round((Get-Item $outputPath).Length / 1KB, 2)) KB" -ForegroundColor Green

# Generate summary report
$summary = @{
    timestamp       = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    filesProcessed  = $layoutFiles.Count
    totalExtracted  = $statsTotal
    uniqueResources = $resourcesLibrary.Count
    skipped         = $statsSkipped
    byType          = $statsByType
    topAttributes   = $resourcesLibrary.Values | Group-Object -Property attribute | 
    Sort-Object Count -Descending | 
    Select-Object -First 15 | 
    ForEach-Object { @{ attribute = $_.Name; count = $_.Count } }
    topValues       = $resourcesLibrary.Values | Group-Object -Property value | 
    Sort-Object Count -Descending | 
    Select-Object -First 15 | 
    ForEach-Object { @{ value = $_.Name; count = $_.Count; type = $_.Group[0].resourceType } }
}

$summaryPath = $outputPath -replace '\.json$', '_summary.json'
$summary | ConvertTo-Json -Depth 4 | Out-File -FilePath $summaryPath -Encoding UTF8

Write-Host "[✓] Summary: $summaryPath" -ForegroundColor Green
Write-Host ""
Write-Host "[*] Next: Review summary and run 2-generate-resources.ps1" -ForegroundColor Cyan
