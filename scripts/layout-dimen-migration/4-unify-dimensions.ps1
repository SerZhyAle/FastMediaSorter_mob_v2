# Unify duplicate dimension values into semantic base resources
param(
    [string]$InputFile = "temp\generated_resources\dimens.xml",
    [string]$OutputFile = "temp\generated_resources\dimens_unified.xml"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Dimension Unification Script ===" -ForegroundColor Cyan
Write-Host "Input: $InputFile" -ForegroundColor Gray
Write-Host "Output: $OutputFile" -ForegroundColor Gray
Write-Host ""

# Define semantic base dimensions with their values
$baseDimensions = [ordered]@{
    # Spacing (margins/paddings)
    'spacing_tiny'              = '2dp'
    'spacing_small'             = '4dp'
    'spacing_normal'            = '8dp'
    'spacing_medium_small'      = '10dp'
    'spacing_medium'            = '16dp'
    'spacing_large'             = '24dp'
    'spacing_xlarge'            = '32dp'
    'spacing_xxlarge'           = '48dp'
    
    # Button sizes
    'button_size_small'         = '20dp'
    'button_size_standard'      = '40dp'
    'button_size_large'         = '48dp'
    
    # Special values
    'button_overlap'            = '-6dp'
    'dimension_min'             = '1dp'  # constraint hack for match_parent in some cases
    
    # Layout constraints
    'settings_button_max_width' = '600dp'
    'toolbar_button_max_width'  = '100dp'
    
    # Text sizes
    'text_size_tiny'            = '10sp'
    'text_size_small'           = '14sp'
    'text_size_medium'          = '20sp'
    'text_size_large'           = '24sp'
    'text_size_xlarge'          = '32sp'
}

# Load and parse XML
$inputPath = Join-Path $PSScriptRoot "..\..\" $InputFile
$outputPath = Join-Path $PSScriptRoot "..\..\" $OutputFile

if (-not (Test-Path $inputPath)) {
    Write-Host "[!] Input file not found: $inputPath" -ForegroundColor Red
    exit 1
}

$xml = [xml](Get-Content -Path $inputPath -Raw -Encoding UTF8)

# Create value-to-semantic mapping
$valueToSemantic = @{}
foreach ($entry in $baseDimensions.GetEnumerator()) {
    $valueToSemantic[$entry.Value] = $entry.Key
}

# Analyze dimensions and build replacement map
$replacementMap = @{}
$stats = @{
    'Total'   = $xml.resources.dimen.Count
    'Unified' = 0
    'Kept'    = 0
}

foreach ($dimen in $xml.resources.dimen) {
    $name = $dimen.name
    $value = $dimen.'#text'
    
    if ($valueToSemantic.ContainsKey($value)) {
        $semanticName = $valueToSemantic[$value]
        $replacementMap[$name] = $semanticName
        $stats['Unified']++
    }
    else {
        $stats['Kept']++
    }
}

Write-Host "[*] Analysis complete:" -ForegroundColor Cyan
Write-Host "  Total dimensions: $($stats['Total'])" -ForegroundColor White
Write-Host "  Will be unified: $($stats['Unified'])" -ForegroundColor Yellow
Write-Host "  Will be kept: $($stats['Kept'])" -ForegroundColor Green
Write-Host ""

# Build output XML
$output = New-Object System.Text.StringBuilder
[void]$output.AppendLine('<?xml version="1.0" encoding="utf-8"?>')
[void]$output.AppendLine('<resources>')
[void]$output.AppendLine('')
[void]$output.AppendLine('    <!-- ============================================================ -->')
[void]$output.AppendLine('    <!-- BASE DIMENSIONS (Semantic) -->')
[void]$output.AppendLine('    <!-- ============================================================ -->')
[void]$output.AppendLine('')

# Add base dimensions by category
[void]$output.AppendLine('    <!-- Spacing (margins/paddings) -->')
@('spacing_tiny', 'spacing_small', 'spacing_normal', 'spacing_medium_small', 'spacing_medium', 'spacing_large', 'spacing_xlarge', 'spacing_xxlarge') | ForEach-Object {
    [void]$output.AppendLine("    <dimen name=`"$_`">$($baseDimensions[$_])</dimen>")
}

[void]$output.AppendLine('')
[void]$output.AppendLine('    <!-- Button sizes -->')
@('button_size_small', 'button_size_standard', 'button_size_large') | ForEach-Object {
    [void]$output.AppendLine("    <dimen name=`"$_`">$($baseDimensions[$_])</dimen>")
}

[void]$output.AppendLine('')
[void]$output.AppendLine('    <!-- Special values -->')
@('button_overlap', 'dimension_min') | ForEach-Object {
    [void]$output.AppendLine("    <dimen name=`"$_`">$($baseDimensions[$_])</dimen>")
}

[void]$output.AppendLine('')
[void]$output.AppendLine('    <!-- Layout constraints -->')
@('settings_button_max_width', 'toolbar_button_max_width') | ForEach-Object {
    [void]$output.AppendLine("    <dimen name=`"$_`">$($baseDimensions[$_])</dimen>")
}

[void]$output.AppendLine('')
[void]$output.AppendLine('    <!-- Text sizes -->')
@('text_size_tiny', 'text_size_small', 'text_size_medium', 'text_size_large', 'text_size_xlarge') | ForEach-Object {
    [void]$output.AppendLine("    <dimen name=`"$_`">$($baseDimensions[$_])</dimen>")
}

[void]$output.AppendLine('')
[void]$output.AppendLine('    <!-- Already existing semantic dimensions -->')
[void]$output.AppendLine('    <dimen name="dimension_zero">0dp</dimen>')
[void]$output.AppendLine('    <dimen name="match_constraint">0dp</dimen>')
[void]$output.AppendLine('    <dimen name="spacing_none">0dp</dimen>')

[void]$output.AppendLine('')
[void]$output.AppendLine('    <!-- ============================================================ -->')
[void]$output.AppendLine('    <!-- DIMENSION ALIASES (mapped to base dimensions) -->')
[void]$output.AppendLine('    <!-- ============================================================ -->')
[void]$output.AppendLine('')

# Add aliased dimensions (sorted by value for easier navigation)
$aliasedDimensions = $xml.resources.dimen | Where-Object { $replacementMap.ContainsKey($_.name) } | Sort-Object { $_.name }
foreach ($dimen in $aliasedDimensions) {
    $name = $dimen.name
    $semanticName = $replacementMap[$name]
    [void]$output.AppendLine("    <dimen name=`"$name`">@dimen/$semanticName</dimen>")
}

# Add non-unified dimensions
[void]$output.AppendLine('')
[void]$output.AppendLine('    <!-- ============================================================ -->')
[void]$output.AppendLine('    <!-- UNIQUE DIMENSIONS (no unification) -->')
[void]$output.AppendLine('    <!-- ============================================================ -->')
[void]$output.AppendLine('')

$uniqueDimensions = $xml.resources.dimen | Where-Object { 
    -not $replacementMap.ContainsKey($_.name) -and 
    $_.name -notin @('dimension_zero', 'match_constraint', 'spacing_none')
} | Sort-Object { $_.name }

foreach ($dimen in $uniqueDimensions) {
    [void]$output.AppendLine("    <dimen name=`"$($dimen.name)`">$($dimen.'#text')</dimen>")
}

[void]$output.AppendLine('')
[void]$output.AppendLine('</resources>')

# Write output
$output.ToString() | Out-File -FilePath $outputPath -Encoding UTF8 -NoNewline

Write-Host "[✓] Unified dimens.xml created: $OutputFile" -ForegroundColor Green
Write-Host ""
Write-Host "Summary by semantic group:" -ForegroundColor Cyan

# Show unification statistics by base dimension
foreach ($entry in $baseDimensions.GetEnumerator()) {
    $semanticName = $entry.Key
    $value = $entry.Value
    $count = ($replacementMap.GetEnumerator() | Where-Object { $_.Value -eq $semanticName }).Count
    if ($count -gt 0) {
        Write-Host "  $semanticName ($value): $count aliases" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "[*] Next steps:" -ForegroundColor Cyan
Write-Host "  1. Review file: $OutputFile" -ForegroundColor White
Write-Host "  2. Copy to: app_v2\src\main\res\values\dimens.xml" -ForegroundColor White
Write-Host "  3. Run replacement script for dimensions" -ForegroundColor White
Write-Host "  4. Build and test" -ForegroundColor White
