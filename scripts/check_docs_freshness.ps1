<#
.SYNOPSIS
    Script to check the freshness of project documentation files.
.DESCRIPTION
    Scans the docs/ directory for markdown files, extracts the explicitly declared "Last Update" date
    (or similar localization), and compares it against the Git last modified date if possible.
#>

$docsDir = "docs"
$mdFiles = Get-ChildItem -Path $docsDir -Filter *.md -Recurse

$report = @()

foreach ($file in $mdFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    
    $declaredDate = "N/A"
    if ($content -match "\*\*(Last Update|Последнее обновление|Останнє оновлення):\*\*\s*([^\r\n]+)") {
        $declaredDate = $matches[2].Trim()
    }
    
    $fileModDate = $file.LastWriteTime.ToString("yyyy-MM-dd")
    
    $report += [PSCustomObject]@{
        Filename     = $file.Name
        DeclaredDate = $declaredDate
        FileModified = $fileModDate
    }
}

$report | Format-Table -AutoSize
