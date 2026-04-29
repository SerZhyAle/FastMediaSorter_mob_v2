# fix-ellipsis-docs.ps1
# Replaces "..." with ".." in documentation .md files.
# Skips code blocks (``` fenced) and inline code (`...`).

param(
    [string[]]$Dirs = @("docs", "PLAN"),
    [switch]$DryRun
)

$root = Split-Path -Parent $PSScriptRoot | Split-Path -Parent

function Replace-EllipsisInMarkdown {
    param([string]$Text)

    $lines = $Text -split "`n"
    $inFence = $false
    $result = [System.Collections.Generic.List[string]]::new()

    foreach ($line in $lines) {
        # Toggle fenced code block state
        if ($line -match '^\s*```') {
            $inFence = !$inFence
            $result.Add($line)
            continue
        }

        if ($inFence) {
            $result.Add($line)
            continue
        }

        # Outside code fences: replace ... but not inside backtick spans
        # Strategy: split by backtick segments, replace only in non-code parts
        $segments = $line -split '(`[^`]*`)'
        $newSegments = @()
        for ($i = 0; $i -lt $segments.Length; $i++) {
            if ($i % 2 -eq 0) {
                # Plain text segment — apply replacement
                $newSegments += $segments[$i] -replace '\.\.\.', '..'
            } else {
                # Inline code segment — keep as-is
                $newSegments += $segments[$i]
            }
        }
        $result.Add($newSegments -join '')
    }

    return $result -join "`n"
}

foreach ($dir in $Dirs) {
    $dirPath = Join-Path $root $dir
    if (-not (Test-Path $dirPath)) {
        Write-Warning "Directory not found: $dirPath"
        continue
    }

    $mdFiles = Get-ChildItem $dirPath -Filter "*.md" -File
    foreach ($file in $mdFiles) {
        $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
        $updated = Replace-EllipsisInMarkdown -Text $content

        if ($content -eq $updated) {
            continue
        }

        $rel = $file.FullName.Substring($root.Length + 1)
        if ($DryRun) {
            $count = ([regex]::Matches($content, '\.\.\.') ).Count
            Write-Host "[dry-run]   $rel — ~$count occurrence(s)"
        } else {
            [System.IO.File]::WriteAllText($file.FullName, $updated, [System.Text.UTF8Encoding]::new($false))
            $count = ([regex]::Matches($content, '\.\.\.') ).Count
            Write-Host "[done]      $rel — replaced ~$count occurrence(s)"
        }
    }
}
