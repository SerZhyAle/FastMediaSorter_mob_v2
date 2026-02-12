# Sort generated resource XMLs by semantic priority
# Priority: 1-ElementType, 2-Attribute, 3-ViewId, 4-Layout, 5-Value

param(
    [string]$InputFile = "temp\generated_resources\dimens.xml",
    [string]$OutputFile = "",  # Empty = overwrite input
    [string]$LibraryFile = "temp\resources_library.json",
    [switch]$DryRun = $false,
    [switch]$NoComments = $true  # Remove all comments by default
)

$ErrorActionPreference = "Stop"

Write-Host "=== Semantic Resource Sorting Script ===" -ForegroundColor Cyan
Write-Host "Input: $InputFile" -ForegroundColor Gray
Write-Host "Library: $LibraryFile" -ForegroundColor Gray
Write-Host "Dry Run: $(if ($DryRun) { 'YES' } else { 'NO' })" -ForegroundColor $(if ($DryRun) { 'Yellow' } else { 'Green' })
Write-Host ""

if (-not (Test-Path $InputFile)) {
    Write-Host "[!] ERROR: File not found: $InputFile" -ForegroundColor Red
    exit 1
}

# Load resources library for elementType metadata
$library = @{}
if (Test-Path $LibraryFile) {
    Write-Host "[*] Loading resources library..." -ForegroundColor Cyan
    $libJson = Get-Content -Path $LibraryFile -Raw -Encoding UTF8
    $libData = $libJson | ConvertFrom-Json -AsHashtable
    $library = $libData
    Write-Host "  [✓] Loaded $($library.Count) resources from library" -ForegroundColor Green
} else {
    Write-Host "[!] WARNING: Library file not found: $LibraryFile" -ForegroundColor Yellow
    Write-Host "    Sorting without element type information" -ForegroundColor Gray
}

# Load XML
[xml]$doc = Get-Content -Path $InputFile -Encoding UTF8
$resources = $doc.resources

# Find AUTO-GENERATED section marker
$allNodes = $resources.ChildNodes
$autoGenStartIndex = -1
$autoGenComment = $null

for ($i = 0; $i -lt $allNodes.Count; $i++) {
    $node = $allNodes[$i]
    if ($node.NodeType -eq 'Comment' -and $node.InnerText -match 'AUTO-GENERATED FROM LAYOUTS') {
        $autoGenStartIndex = $i
        $autoGenComment = $node
        break
    }
}

if ($autoGenStartIndex -eq -1) {
    Write-Host "[!] No AUTO-GENERATED section found" -ForegroundColor Yellow
    Write-Host "    File may not have auto-generated resources" -ForegroundColor Gray
    exit 0
}

Write-Host "[*] Found AUTO-GENERATED section at node index: $autoGenStartIndex" -ForegroundColor Cyan

# Extract auto-generated resources (everything after the marker)
$autoGenResources = @()
$existingResources = @()
$elementName = $null

for ($i = 0; $i -lt $allNodes.Count; $i++) {
    $node = $allNodes[$i]
    
    if ($node.NodeType -eq 'Element') {
        $elementName = $node.LocalName
        
        if ($i -le $autoGenStartIndex) {
            # Before auto-gen section
            $existingResources += $node
        }
        else {
            # After auto-gen section
            $resName = $node.GetAttribute('name')
            $resValue = $node.InnerText
            $comment = ''
            
            # Check if next node is comment
            if ($i + 1 -lt $allNodes.Count -and $allNodes[$i + 1].NodeType -eq 'Comment') {
                $comment = $allNodes[$i + 1].InnerText.Trim()
            }
            
            # Parse resource name: layout_viewId_attribute
            if ($resName -match '^([a-zA-Z0-9_]+)_([a-zA-Z0-9_]+)_([a-zA-Z0-9_]+)$') {
                $layout = $matches[1]
                $viewId = $matches[2]
                $attribute = $matches[3]
            }
            elseif ($resName -match '^([a-zA-Z_]+)$') {
                # Semantic names like "match_constraint", "bool_true"
                $layout = '_semantic'
                $viewId = '_semantic'
                $attribute = $resName
            }
            else {
                # Fallback
                $layout = '_other'
                $viewId = '_other'
                $attribute = $resName
            }
            
            # Get elementType from library
            $elementType = 'Unknown'
            if ($library.ContainsKey($resName)) {
                $libEntry = $library[$resName]
                if ($libEntry.elementType) {
                    $elementType = $libEntry.elementType
                }
            }
            
            $autoGenResources += [PSCustomObject]@{
                Name        = $resName
                Value       = $resValue
                Layout      = $layout
                ViewId      = $viewId
                ElementType = $elementType
                Attribute   = $attribute
                Comment     = $comment
                Node        = $node
            }
        }
    }
}

Write-Host "  [✓] Existing resources: $($existingResources.Count)" -ForegroundColor Cyan
Write-Host "  [✓] Auto-generated resources: $($autoGenResources.Count)" -ForegroundColor Cyan

# Sort by: ElementType → Attribute → ViewId → Layout → Value
Write-Host ""
Write-Host "[*] Sorting by priority: ElementType → Attribute → ViewId → Layout..." -ForegroundColor Cyan

$sorted = $autoGenResources | Sort-Object -Property @(
    @{ Expression = { $_.ElementType }; Ascending = $true }
    @{ Expression = { $_.Attribute }; Ascending = $true }
    @{ Expression = { $_.ViewId }; Ascending = $true }
    @{ Expression = { $_.Layout }; Ascending = $true }
    @{ Expression = { $_.Value }; Ascending = $true }
)

Write-Host "  [✓] Sorted $($sorted.Count) resources" -ForegroundColor Green

# Preview grouping
Write-Host ""
Write-Host "=== Preview: Top 20 Grouped Resources ===" -ForegroundColor Cyan
$preview = $sorted | Select-Object -First 20 | ForEach-Object {
    $commentPart = if ($_.Comment) { " <!-- $($_.Comment) -->" } else { "" }
    Write-Host "  [$($_.ElementType)] $($_.Attribute) → $($_.ViewId) @ $($_.Layout) = $($_.Value)$commentPart" -ForegroundColor Gray
}

# Rebuild XML
if (-not $DryRun) {
    Write-Host ""
    Write-Host "[*] Rebuilding XML with sorted resources..." -ForegroundColor Cyan
    
    # Build new XML structure
    $xmlBuilder = [System.Text.StringBuilder]::new()
    [void]$xmlBuilder.AppendLine('<?xml version="1.0" encoding="utf-8"?>')
    [void]$xmlBuilder.AppendLine('<resources>')
    
    # Add existing resources (clean, no comments)
    foreach ($node in $existingResources) {
        $name = $node.GetAttribute('name')
        $value = $node.InnerText
        [void]$xmlBuilder.AppendLine("    <$($node.LocalName) name=`"$name`">$value</$($node.LocalName)>")
    }
    
    [void]$xmlBuilder.AppendLine()
    
    # Add sorted resources (no grouping comments if NoComments)
    foreach ($res in $sorted) {
        # Add resource entry without comments
        [void]$xmlBuilder.AppendLine("    <$elementName name=`"$($res.Name)`">$($res.Value)</$elementName>")
    }
    
    [void]$xmlBuilder.AppendLine('</resources>')
    
    # Save
    $outputPath = if ($OutputFile) { $OutputFile } else { $InputFile }
    $xmlContent = $xmlBuilder.ToString()
    [System.IO.File]::WriteAllText($outputPath, $xmlContent, [System.Text.Encoding]::UTF8)
    
    Write-Host "  [✓] Saved sorted XML to: $outputPath" -ForegroundColor Green
}

Write-Host ""
Write-Host "=== Sorting Complete ===" -ForegroundColor Green

if ($DryRun) {
    Write-Host "[i] Dry run - no files modified" -ForegroundColor Yellow
    Write-Host "    Run without -DryRun to apply changes" -ForegroundColor Gray
}
