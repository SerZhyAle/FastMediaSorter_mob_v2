
# Log AI Request with Analysis
# Usage: .\scripts\log-ai-request.ps1 -Question "your question" -Analyzer "GPT-4o" -Complexity "MEDIUM" -RecommendedModel "Sonnet 4.5" -Reasoning "explanation"

param(
    [Parameter(Mandatory = $true)]
    [string]$Question,
    
    [Parameter(Mandatory = $false)]
    [string]$DateTime = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss"),
    
    [Parameter(Mandatory = $false)]
    [string]$Analyzer = "Unknown",
    
    [Parameter(Mandatory = $false)]
    [ValidateSet("LOW", "MEDIUM", "HIGH", "CRITICAL")]
    [string]$Complexity = "MEDIUM",
    
    [Parameter(Mandatory = $false)]
    [string]$RecommendedModel = "Auto",
    
    [Parameter(Mandatory = $false)]
    [string]$Reasoning = "No reasoning provided"
)

$logFile = "temp\ai_prompts.xml"

# Ensure temp directory exists
if (-not (Test-Path "temp")) {
    New-Item -ItemType Directory -Path "temp" | Out-Null
}

# Create file if it doesn't exist
if (-not (Test-Path $logFile)) {
    @"
<?xml version="1.0" encoding="UTF-8"?>
<ai_prompts>
</ai_prompts>
"@ | Set-Content $logFile -Encoding UTF8
}

# Read current content
$xml = [xml](Get-Content $logFile)

# Create new prompt element
$newPrompt = $xml.CreateElement("prompt")

# datetime
$dateTimeNode = $xml.CreateElement("datetime")
$dateTimeNode.InnerText = $DateTime
$newPrompt.AppendChild($dateTimeNode) | Out-Null

# question
$questionNode = $xml.CreateElement("question")
$cdataSection = $xml.CreateCDataSection($Question)
$questionNode.AppendChild($cdataSection) | Out-Null
$newPrompt.AppendChild($questionNode) | Out-Null

# analyzer
$analyzerNode = $xml.CreateElement("analyzer")
$analyzerNode.InnerText = $Analyzer
$newPrompt.AppendChild($analyzerNode) | Out-Null

# complexity
$complexityNode = $xml.CreateElement("complexity")
$complexityNode.InnerText = $Complexity
$newPrompt.AppendChild($complexityNode) | Out-Null

# recommended_model
$modelNode = $xml.CreateElement("recommended_model")
$modelNode.InnerText = $RecommendedModel
$newPrompt.AppendChild($modelNode) | Out-Null

# reasoning
$reasoningNode = $xml.CreateElement("reasoning")
$reasoningCdata = $xml.CreateCDataSection($Reasoning)
$reasoningNode.AppendChild($reasoningCdata) | Out-Null
$newPrompt.AppendChild($reasoningNode) | Out-Null

# Append to root
$xml.ai_prompts.AppendChild($newPrompt) | Out-Null

# Save with pretty formatting
$settings = New-Object System.Xml.XmlWriterSettings
$settings.Indent = $true
$settings.IndentChars = "    "
$settings.Encoding = [System.Text.Encoding]::UTF8

$writer = [System.Xml.XmlWriter]::Create($logFile, $settings)
$xml.Save($writer)
$writer.Close()

Write-Host "✓ Request logged at $DateTime" -ForegroundColor Green
Write-Host "  Analyzer: $Analyzer | Complexity: $Complexity | Recommended: $RecommendedModel" -ForegroundColor Gray
