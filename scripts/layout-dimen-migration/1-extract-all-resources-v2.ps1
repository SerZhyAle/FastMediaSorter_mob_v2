# Extract ALL attribute values from Layout files with SEMANTIC CONSOLIDATION
# Generates unified JSON library with smart naming (no value in name unless collision)
# Key format: layoutname_viewid_attribute (value added only for real collisions)

param(
    [string]$LayoutDir = "app_v2\src\main\res\layout",
    [string]$OutputFile = "temp\resources_library.json",
    [string]$ConsolidationMapFile = "temp\consolidation_map.json"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Universal Resource Extraction Script (v2 - Semantic Naming) ===" -ForegroundColor Cyan
Write-Host "Source: $LayoutDir" -ForegroundColor Gray
Write-Host "Output: $OutputFile" -ForegroundColor Gray
Write-Host "Consolidation Map: $ConsolidationMapFile" -ForegroundColor Gray
Write-Host ""

# Semantic mapping - ONLY context-aware and truly semantic names
# Generic names removed - all dimensions use full layout_viewId_attribute format
$semanticMap = @{
    'dimension' = @{
        '0dp' = 'context_aware'  # Special: match_constraint, spacing_none, dimension_zero
    }
    'boolean'   = @{
        'true'  = 'bool_true'
        'false' = 'bool_false'
    }
    'integer'   = @{
        '0' = 'int_zero'
        '1' = 'int_one'
    }
    'color_hex' = @{
        '#FFFFFF'   = 'white'
        '#000000'   = 'black'
        '#80FFFFFF' = 'white_50_alpha'
        '#00000000' = 'transparent'
    }
}

# Universal pattern to extract ALL attributes
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
    
    # Skip already referenced resources
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
    
    return $true
}

# Function to get semantic name for common values
# ONLY context-aware consolidation - all other values use full layout_viewId_attribute
function Get-SemanticName {
    param([string]$type, [string]$value, [string]$attribute)
    
    # Special handling for 0dp - context-aware naming
    if ($type -eq 'dimension' -and $value -eq '0dp') {
        if ($attribute -match 'layout_(width|height)') { return 'match_constraint' }
        if ($attribute -match '(padding|margin|inset)') { return 'spacing_none' }
        return 'dimension_zero'
    }
    
    # Truly semantic names (boolean, integer, color)
    if ($semanticMap.ContainsKey($type) -and $semanticMap[$type].ContainsKey($value)) {
        return $semanticMap[$type][$value]
    }
    
    # All other values: return null → use full semantic name
    return $null
}

# Storage - PHASE 1: Collect with base keys (without value)
$rawExtractions = @()  # Array of all extractions with metadata
$statsTotal = 0
$statsSkipped = 0
$statsSkippedByReason = @{
    'already_referenced' = 0
    'special_values'     = 0
    'system_attributes'  = 0
    'attribute_refs'     = 0
}

# Get layout files
$layoutPath = Join-Path $PSScriptRoot "..\..\$LayoutDir"
$layoutFiles = Get-ChildItem -Path $layoutPath -Filter "*.xml" -File

Write-Host "[*] Scanning $($layoutFiles.Count) layout files..." -ForegroundColor Cyan
Write-Host ""

# PHASE 1: Extract all attributes with base keys
foreach ($file in $layoutFiles) {
    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
    $layoutName = $file.BaseName
    
    $matches = [regex]::Matches($content, $universalPattern)
    $fileCount = 0
    $fileSkipped = 0
    
    Write-Host "[$($layoutFiles.IndexOf($file) + 1)/$($layoutFiles.Count)] $layoutName.xml" -ForegroundColor Gray -NoNewline
    
    foreach ($match in $matches) {
        $namespace = $match.Groups[1].Value
        $attribute = $match.Groups[2].Value
        $fullValue = $match.Groups[3].Value
        $position = $match.Index
        
        $resourceType = Get-ResourceType -value $fullValue
        
        $skipReason = ''
        if (-not (Should-Extract -type $resourceType -attribute $attribute -skipReason ([ref]$skipReason))) {
            $fileSkipped++
            $statsSkipped++
            $statsSkippedByReason[$skipReason]++
            continue
        }
        
        # Extract view ID
        $viewId = 'unknown'
        $beforeMatch = $content.Substring(0, $position)
        $idPattern = 'android:id="@\+id/([a-zA-Z_][a-zA-Z0-9_]*)"'
        $idMatches = [regex]::Matches($beforeMatch, $idPattern)
        
        if ($idMatches.Count -gt 0) {
            $viewId = $idMatches[$idMatches.Count - 1].Groups[1].Value
        }
        
        # Extract element type (Button, TextView, etc.)
        $elementType = 'Unknown'
        # Find opening tag closest before android:id (search backwards from last id match position)
        if ($idMatches.Count -gt 0) {
            $idPosition = $idMatches[$idMatches.Count - 1].Index
            $beforeId = $content.Substring(0, $idPosition)
            # Find last opening tag before android:id
            $tagPattern = '<([a-zA-Z][a-zA-Z0-9._]*)\s+[^>]*$'
            $tagMatch = [regex]::Match($beforeId, $tagPattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)
            if ($tagMatch.Success) {
                $fullType = $tagMatch.Groups[1].Value
                # Extract simple name (e.g., "Button" from "android.widget.Button")
                if ($fullType -match '\.([^.]+)$') {
                    $elementType = $matches[1]
                }
                else {
                    $elementType = $fullType
                }
            }
        }
        
        # Base key WITHOUT value
        $baseKey = "${layoutName}_${viewId}_${attribute}"
        
        # Store extraction
        $rawExtractions += @{
            baseKey      = $baseKey
            layout       = $layoutName
            viewId       = $viewId
            elementType  = $elementType
            attribute    = $attribute
            namespace    = $namespace
            value        = $fullValue
            resourceType = $resourceType
            file         = $file.Name
            line         = ($content.Substring(0, $position) -split "`n").Count
        }
        
        $fileCount++
        $statsTotal++
    }
    
    if ($fileCount -gt 0) {
        Write-Host " → Extracted: $fileCount | Skipped: $fileSkipped" -ForegroundColor Green
    }
    else {
        Write-Host " → Extracted: 0 | Skipped: $fileSkipped" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "=== PHASE 1 Complete: Raw Extraction ===" -ForegroundColor Cyan
Write-Host "  Total extracted: $statsTotal" -ForegroundColor Green

# PHASE 2: Collision detection and semantic consolidation
Write-Host ""
Write-Host "=== PHASE 2: Semantic Consolidation & Collision Resolution ===" -ForegroundColor Cyan

$resourcesLibrary = @{}
$consolidationLog = @{
    semantic_consolidated = @{}  # value → semantic name → count
    collisions_detected   = @{}     # baseKey → array of values
    unique_resources      = 0
}

# Group by baseKey to detect collisions
$groupedByKey = $rawExtractions | Group-Object -Property baseKey

foreach ($group in $groupedByKey) {
    $baseKey = $group.Name
    $extractions = $group.Group
    
    # Get unique values for this key
    $uniqueValues = $extractions | Select-Object -ExpandProperty value -Unique
    
    if ($uniqueValues.Count -eq 1) {
        # No collision - single value for this attribute
        $ext = $extractions[0]
        $value = $ext.value
        $type = $ext.resourceType
        
        # Check for semantic mapping
        $semanticName = Get-SemanticName -type $type -value $value -attribute $ext.attribute
        
        if ($semanticName) {
            # Use semantic name
            $finalKey = $semanticName
            
            # Track consolidation
            if (-not $consolidationLog.semantic_consolidated.ContainsKey($value)) {
                $consolidationLog.semantic_consolidated[$value] = @{}
            }
            if (-not $consolidationLog.semantic_consolidated[$value].ContainsKey($semanticName)) {
                $consolidationLog.semantic_consolidated[$value][$semanticName] = 0
            }
            $consolidationLog.semantic_consolidated[$value][$semanticName]++
        }
        else {
            # Use base key (layout_view_attribute)
            $finalKey = $baseKey
        }
        
        # Create or update resource entry
        if (-not $resourcesLibrary.ContainsKey($finalKey)) {
            $resourcesLibrary[$finalKey] = @{
                layout       = $ext.layout
                viewId       = $ext.viewId
                elementType  = $ext.elementType
                attribute    = $ext.attribute
                namespace    = $ext.namespace
                value        = $value
                resourceType = $type
                resourceName = $finalKey
                usages       = @()
                semantic     = ($semanticName -ne $null)
            }
            $consolidationLog.unique_resources++
        }
        
        # Add all usages
        foreach ($extraction in $extractions) {
            $resourcesLibrary[$finalKey].usages += @{
                file = $extraction.file
                line = $extraction.line
            }
        }
    }
    else {
        # COLLISION DETECTED - multiple values for same baseKey
        $consolidationLog.collisions_detected[$baseKey] = $uniqueValues
        
        Write-Host "  [!] Collision: $baseKey → $($uniqueValues.Count) different values" -ForegroundColor Yellow
        
        # Create separate entries with value discriminators
        foreach ($value in $uniqueValues) {
            $matchingExtractions = $extractions | Where-Object { $_.value -eq $value }
            $ext = $matchingExtractions[0]
            $type = $ext.resourceType
            
            # Check semantic mapping first
            $semanticName = Get-SemanticName -type $type -value $value -attribute $ext.attribute
            
            if ($semanticName) {
                $finalKey = $semanticName
                
                # Track consolidation
                if (-not $consolidationLog.semantic_consolidated.ContainsKey($value)) {
                    $consolidationLog.semantic_consolidated[$value] = @{}
                }
                if (-not $consolidationLog.semantic_consolidated[$value].ContainsKey($semanticName)) {
                    $consolidationLog.semantic_consolidated[$value][$semanticName] = 0
                }
                $consolidationLog.semantic_consolidated[$value][$semanticName]++
            }
            else {
                # Add value discriminator for collision
                $cleanValue = $value -replace '[^a-zA-Z0-9]', '_'
                $finalKey = "${baseKey}_${cleanValue}"
            }
            
            if (-not $resourcesLibrary.ContainsKey($finalKey)) {
                $resourcesLibrary[$finalKey] = @{
                    layout       = $ext.layout
                    viewId       = $ext.viewId
                    elementType  = $ext.elementType
                    attribute    = $ext.attribute
                    namespace    = $ext.namespace
                    value        = $value
                    resourceType = $type
                    resourceName = $finalKey
                    usages       = @()
                    semantic     = ($semanticName -ne $null)
                    collision    = $true
                }
                $consolidationLog.unique_resources++
            }
            
            foreach ($extraction in $matchingExtractions) {
                $resourcesLibrary[$finalKey].usages += @{
                    file = $extraction.file
                    line = $extraction.line
                }
            }
        }
    }
}

Write-Host "  [✓] Unique resources after consolidation: $($consolidationLog.unique_resources)" -ForegroundColor Green
Write-Host "  [✓] Semantic consolidations: $($consolidationLog.semantic_consolidated.Count) values" -ForegroundColor Cyan
Write-Host "  [!] Collisions detected: $($consolidationLog.collisions_detected.Count) attributes" -ForegroundColor Yellow

# Save main library
Write-Host ""
Write-Host "[*] Saving resources library..." -ForegroundColor Cyan

$libraryJson = $resourcesLibrary | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($OutputFile, $libraryJson, [System.Text.Encoding]::UTF8)
Write-Host "  [✓] Saved: $OutputFile ($($resourcesLibrary.Count) resources)" -ForegroundColor Green

# Save consolidation log
$consolidationJson = $consolidationLog | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($ConsolidationMapFile, $consolidationJson, [System.Text.Encoding]::UTF8)
Write-Host "  [✓] Saved consolidation log: $ConsolidationMapFile" -ForegroundColor Green

# Statistics
Write-Host ""
Write-Host "=== Final Statistics ===" -ForegroundColor Cyan
Write-Host "  Raw extractions: $statsTotal" -ForegroundColor White
Write-Host "  Unique resources: $($resourcesLibrary.Count)" -ForegroundColor Green
Write-Host "  Consolidation savings: $($statsTotal - $resourcesLibrary.Count) duplicates removed" -ForegroundColor Green
Write-Host ""
Write-Host "  Skipped totals:" -ForegroundColor Yellow
foreach ($reason in $statsSkippedByReason.Keys | Sort-Object) {
    $count = $statsSkippedByReason[$reason]
    Write-Host "    - ${reason}: $count" -ForegroundColor Gray
}
Write-Host ""
Write-Host "  Semantic consolidations by value:" -ForegroundColor Cyan
$topConsolidations = $consolidationLog.semantic_consolidated.GetEnumerator() | 
ForEach-Object { 
    $value = $_.Key
    $total = ($_.Value.Values | Measure-Object -Sum).Sum
    [PSCustomObject]@{ Value = $value; Total = $total; SemanticNames = ($_.Value.Keys -join ', ') }
} | 
Sort-Object -Property Total -Descending | 
Select-Object -First 10

foreach ($item in $topConsolidations) {
    Write-Host "    $($item.Value) → $($item.SemanticNames) ($($item.Total) usages)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "=== Extraction Complete ===" -ForegroundColor Green
Write-Host "Next step: Run 2-generate-resources.ps1" -ForegroundColor Yellow
