
# Script to add Jekyll Frontmatter to documentation files
$docsDir = "$PSScriptRoot\..\docs"
$files = Get-ChildItem -Path $docsDir -Filter "*.md"

foreach ($file in $files) {
    $content = Get-Content -Path $file.FullName -Raw
    
    # Check if frontmatter already exists
    if ($content.StartsWith("---")) {
        Write-Host "Skipping $($file.Name): Already has frontmatter" -ForegroundColor Gray
        continue
    }

    # Extract title from first line (e.g. "# Title")
    $reader = [System.IO.StringReader]::new($content)
    $firstLine = $reader.ReadLine()
    $title = $file.BaseName
    
    if ($firstLine -match "^#\s+(.+)") {
        $title = $matches[1].Trim()
        # Escape special characters in title for YAML
        $title = $title -replace '"', '\"'
    }
    
    # Create frontmatter
    $frontmatter = @"
---
layout: default
title: "$title"
permalink: /docs/$($file.BaseName).html
---

"@
    
    # Prepend frontmatter
    $newContent = $frontmatter + $content
    Set-Content -Path $file.FullName -Value $newContent -Encoding UTF8
    
    Write-Host "Updated $($file.Name) with title: $title" -ForegroundColor Green
}
