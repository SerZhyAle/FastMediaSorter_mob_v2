# fix-ellipsis-strings.ps1
# Replaces "..." with ".." in Android strings.xml UI string values.
# Only modifies content inside <string ...>...</string> tags - skips code, comments, attributes.

param(
    [string[]]$Files = @(
        "app_v2\src\main\res\values\strings.xml",
        "app_v2\src\main\res\values-ru\strings.xml",
        "app_v2\src\main\res\values-uk\strings.xml"
    ),
    [switch]$DryRun
)

$root = Split-Path -Parent $PSScriptRoot | Split-Path -Parent
foreach ($rel in $Files) {
    $path = Join-Path $root $rel
    if (-not (Test-Path $path)) {
        Write-Warning "File not found: $path"
        continue
    }

    $content = Get-Content $path -Raw -Encoding UTF8
    # Replace "..." inside string tag bodies only - uses regex that captures tag content
    $updated = $content -replace '(?<=<string[^>]*>(?:[^<]|\n)*?)\.\.\.', '..'

    if ($content -eq $updated) {
        Write-Host "[no change] $rel"
        continue
    }

    if ($DryRun) {
        $count = ([regex]::Matches($content, '(?<=<string[^>]*>(?:[^<]|\n)*?)\.\.\.') ).Count
        Write-Host "[dry-run]   $rel - would replace $count occurrence(s)"
    } else {
        [System.IO.File]::WriteAllText($path, $updated, [System.Text.UTF8Encoding]::new($false))
        $count = ([regex]::Matches($content, '(?<=<string[^>]*>(?:[^<]|\n)*?)\.\.\.') ).Count
        Write-Host "[done]      $rel - replaced $count occurrence(s)"
    }
}
