# Analyze duplicate values in generated resources
# Groups resources by VALUE to find consolidation opportunities

param(
    [string]$InputFile = "temp\resources_library.json",
    [string]$OutputFile = "temp\duplicate_analysis.txt",
    [int]$MinUsageCount = 5  # Only show values used N+ times
)

$ErrorActionPreference = "Stop"

# Function to suggest semantic names (must be defined before use)
function Get-SemanticSuggestions {
    param([string]$value, [string]$type)
    
    $suggestions = @()
    
    switch ($type) {
        'dimension' {
            # ConstraintLayout match_constraint
            if ($value -eq '0dp') {
                $suggestions += "match_constraint (ConstraintLayout standard for 0dp in layout_width/height)"
                $suggestions += "spacing_none (for padding/margin contexts)"
            }
            # Standard padding/margins
            elseif ($value -eq '4dp') { $suggestions += "padding_tiny, margin_minimal" }
            elseif ($value -eq '8dp') { $suggestions += "padding_small, margin_small, padding_normal" }
            elseif ($value -eq '12dp') { $suggestions += "padding_normal, margin_medium" }
            elseif ($value -eq '16dp') { $suggestions += "padding_large, margin_large" }
            elseif ($value -eq '24dp') { $suggestions += "padding_huge, margin_xlarge" }
            elseif ($value -eq '32dp') { $suggestions += "padding_xxlarge, margin_xxlarge" }
            elseif ($value -eq '48dp') { $suggestions += "icon_size_large" }
            elseif ($value -eq '40dp') { $suggestions += "button_height, icon_size_xlarge" }
            elseif ($value -eq '20dp') { $suggestions += "icon_size_button" }
            elseif ($value -eq '2dp') { $suggestions += "margin_minimal, divider_height, stroke_width_thin" }
            elseif ($value -eq '1dp') { $suggestions += "divider_height, stroke_width_hairline" }
            # Text sizes
            elseif ($value -eq '10sp') { $suggestions += "text_size_tiny, text_size_caption" }
            elseif ($value -eq '12sp') { $suggestions += "text_size_small, text_size_body_small" }
            elseif ($value -eq '14sp') { $suggestions += "text_size_normal, text_size_body" }
            elseif ($value -eq '16sp') { $suggestions += "text_size_medium, text_size_body_large" }
            elseif ($value -eq '18sp') { $suggestions += "text_size_large, text_size_subtitle" }
            elseif ($value -eq '20sp') { $suggestions += "text_size_title, text_size_headline" }
            elseif ($value -eq '24sp') { $suggestions += "text_size_huge, text_size_display" }
            # Negative margins
            elseif ($value -eq '-6dp') { $suggestions += "margin_overlap_small (visual touch target expansion)" }
            # Constraints
            elseif ($value -eq '600dp') { $suggestions += "constraint_max_width_dialog (tablet-friendly)" }
            elseif ($value -eq '120dp') { $suggestions += "input_width_small, icon_size_welcome" }
            elseif ($value -eq '140dp') { $suggestions += "input_width_medium" }
            else { $suggestions += "Consider if this is a one-off value or should be semantic" }
        }
        'color_hex' {
            if ($value -match '^#[0-9A-F]{8}$') {
                $suggestions += "Check if matches existing color in colors.xml"
                $suggestions += "Consider semantic name based on usage context (not color value)"
            }
        }
        'integer' {
            if ($value -eq '1') { $suggestions += "weight_equal, priority_normal" }
            elseif ($value -eq '0') { $suggestions += "weight_zero, priority_none" }
            else { $suggestions += "Context-specific: maxLength, weight, priority, etc." }
        }
        'boolean' {
            $suggestions += "Consider if this should be configurable or hardcoded"
        }
        'string_literal' {
            $suggestions += "Move to strings.xml with localization support"
            $suggestions += "Use string resources for ALL user-visible text"
        }
    }
    
    if ($suggestions.Count -eq 0) {
        $suggestions += "Manual review required"
    }
    
    return $suggestions
}

Write-Host "=== Resource Duplicate Analysis ===" -ForegroundColor Cyan
Write-Host "Input: $InputFile" -ForegroundColor Gray
Write-Host "Output: $OutputFile" -ForegroundColor Gray
Write-Host "Min usage threshold: $MinUsageCount" -ForegroundColor Gray
Write-Host ""

# Load library
$libraryJson = Get-Content -Path $InputFile -Raw -Encoding UTF8
$library = $libraryJson | ConvertFrom-Json -AsHashtable

# Group by type and value
$byTypeAndValue = @{}

foreach ($entry in $library.GetEnumerator()) {
    $data = $entry.Value
    $type = $data.resourceType
    $value = $data.value
    
    if (-not $byTypeAndValue.ContainsKey($type)) {
        $byTypeAndValue[$type] = @{}
    }
    
    if (-not $byTypeAndValue[$type].ContainsKey($value)) {
        $byTypeAndValue[$type][$value] = @()
    }
    
    $byTypeAndValue[$type][$value] += $entry
}

# Build report
$report = [System.Text.StringBuilder]::new()
[void]$report.AppendLine("=" * 80)
[void]$report.AppendLine("RESOURCE DUPLICATE ANALYSIS")
[void]$report.AppendLine("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
[void]$report.AppendLine("Min usage threshold: $MinUsageCount")
[void]$report.AppendLine("=" * 80)
[void]$report.AppendLine()

$consolidationOpportunities = 0
$totalSavings = 0

foreach ($type in ($byTypeAndValue.Keys | Sort-Object)) {
    $valueGroups = $byTypeAndValue[$type]
    
    # Filter by usage count
    $significant = $valueGroups.GetEnumerator() | Where-Object { $_.Value.Count -ge $MinUsageCount } | Sort-Object { -$_.Value.Count }
    
    if ($significant.Count -eq 0) {
        continue
    }
    
    [void]$report.AppendLine("=" * 80)
    [void]$report.AppendLine("TYPE: $type")
    [void]$report.AppendLine("=" * 80)
    [void]$report.AppendLine()
    
    foreach ($valueGroup in $significant) {
        $value = $valueGroup.Key
        $resources = $valueGroup.Value
        $count = $resources.Count
        
        $consolidationOpportunities++
        $totalSavings += ($count - 1)
        
        [void]$report.AppendLine("-" * 80)
        [void]$report.AppendLine("VALUE: $value")
        [void]$report.AppendLine("USAGE COUNT: $count resources")
        [void]$report.AppendLine("CONSOLIDATION POTENTIAL: Save $($count - 1) duplicates")
        [void]$report.AppendLine()
        [void]$report.AppendLine("SEMANTIC NAME SUGGESTIONS:")
        
        # Suggest semantic names based on value patterns
        $suggestions = Get-SemanticSuggestions -value $value -type $type
        foreach ($suggestion in $suggestions) {
            [void]$report.AppendLine("  • $suggestion")
        }
        [void]$report.AppendLine()
        
        [void]$report.AppendLine("RESOURCE NAMES (showing first 10):")
        $resourceNames = $resources | ForEach-Object { $_.Key } | Sort-Object | Select-Object -First 10
        foreach ($name in $resourceNames) {
            [void]$report.AppendLine("  - $name")
        }
        
        if ($resources.Count -gt 10) {
            [void]$report.AppendLine("  ... and $($resources.Count - 10) more")
        }
        
        [void]$report.AppendLine()
    }
    
    [void]$report.AppendLine()
}

# Summary
[void]$report.AppendLine("=" * 80)
[void]$report.AppendLine("SUMMARY")
[void]$report.AppendLine("=" * 80)
[void]$report.AppendLine()
[void]$report.AppendLine("Consolidation opportunities: $consolidationOpportunities value groups")
[void]$report.AppendLine("Potential resource savings: $totalSavings duplicates")
[void]$report.AppendLine()
[void]$report.AppendLine("RECOMMENDED ACTIONS:")
[void]$report.AppendLine("1. Review high-usage values (0dp, 8dp, 16dp, 10sp, etc.)")
[void]$report.AppendLine("2. Create semantic names (match_constraint, padding_normal, text_size_small)")
[void]$report.AppendLine("3. Use existing semantic resources where possible")
[void]$report.AppendLine("4. Run 3-replace-with-resources.ps1 with consolidated names")
[void]$report.AppendLine()

# Save report
$reportText = $report.ToString()
[System.IO.File]::WriteAllText($OutputFile, $reportText, [System.Text.Encoding]::UTF8)

Write-Host "[✓] Analysis complete!" -ForegroundColor Green
Write-Host "    Consolidation opportunities: $consolidationOpportunities" -ForegroundColor Cyan
Write-Host "    Potential savings: $totalSavings duplicates" -ForegroundColor Cyan
Write-Host "    Report saved to: $OutputFile" -ForegroundColor Gray
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Review report: type '$OutputFile'" -ForegroundColor Gray
Write-Host "  2. Create consolidation map (value → semantic name)" -ForegroundColor Gray
Write-Host "  3. Re-run 2-generate-resources.ps1 with consolidation" -ForegroundColor Gray
