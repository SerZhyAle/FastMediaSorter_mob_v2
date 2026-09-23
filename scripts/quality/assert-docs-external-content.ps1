# Quality Gate: Assert External Documentation Content & Decoupling
# Part of S3410 (docs-external-content-decoupling)
# Validates external Markdown recipes, frontmatter schemas, snippet references, and verifies HTML sync.

[CmdletBinding()]
param (
    [switch]$Check,
    [string]$ContentDir = "docs/content/recipes",
    [string]$SnippetDir = "docs/content/snippets"
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$contentRoot = Join-Path $repoRoot $ContentDir
$snippetRoot = Join-Path $repoRoot $SnippetDir
$generatorScript = Join-Path $repoRoot 'scripts/docs/generate-docs-pages.ps1'
$manifestPath = Join-Path $repoRoot 'docs/docs-pages-manifest.jsonl'

Write-Host "=== Documentation External Content Quality Gate ===" -ForegroundColor Cyan

$errors = [System.Collections.Generic.List[string]]::new()

# 1. Check directories and tools
if (-not (Test-Path $contentRoot)) {
    $errors.Add("External content directory missing at $contentRoot")
}
if (-not (Test-Path $snippetRoot)) {
    $errors.Add("External snippets directory missing at $snippetRoot")
}
if (-not (Test-Path $generatorScript)) {
    $errors.Add("Documentation page generator script missing at $generatorScript")
}

# 2. Load page manifest for ID cross-checking
$manifestPageIds = [System.Collections.Generic.HashSet[string]]::new()
if (Test-Path $manifestPath) {
    Get-Content $manifestPath | ForEach-Object {
        if (-not [string]::IsNullOrWhiteSpace($_)) {
            $p = ConvertFrom-Json $_
            if ($p.page_id) { $manifestPageIds.Add($p.page_id) | Out-Null }
        }
    }
}

# 3. Validate Markdown Recipes
if (Test-Path $contentRoot) {
    $recipes = Get-ChildItem -Path $contentRoot -Filter *.md
    Write-Host "Scanned $($recipes.Count) external Markdown recipe file(s)." -ForegroundColor Green

    if ($recipes.Count -eq 0) {
        $errors.Add("No Markdown recipe files found in $contentRoot")
    }

    foreach ($rec in $recipes) {
        $text = Get-Content $rec.FullName -Raw -Encoding utf8
        if ($text -notmatch '^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$') {
            $errors.Add("File $($rec.Name) missing YAML frontmatter delimiters (---)")
            continue
        }
        $yaml = $Matches[1]
        $body = $Matches[2].Trim()

        if ([string]::IsNullOrWhiteSpace($body)) {
            $errors.Add("File $($rec.Name) has empty body content.")
        }

        $required = @('page_id', 'title', 'description', 'category', 'ticket')
        foreach ($rf in $required) {
            $pattern = "(?m)^" + [regex]::Escape($rf) + ":\s*.+"
            if ($yaml -notmatch $pattern) {
                $errors.Add("File $($rec.Name) missing required frontmatter key '$rf'")
            }
        }

        # Check snippet references
        $snippetMatches = [regex]::Matches($yaml, 'path:\s*([^\r\n]+)')
        foreach ($sm in $snippetMatches) {
            $snipRel = $sm.Groups[1].Value.Trim().Trim('"').Trim("'")
            $snipFull = Join-Path $repoRoot $snipRel
            if (-not (Test-Path $snipFull)) {
                $errors.Add("File $($rec.Name) references missing snippet '$snipRel'")
            }
        }

        # Check image references
        $imgMatches = [regex]::Matches($yaml, 'src:\s*([^\r\n]+)')
        foreach ($im in $imgMatches) {
            $imgRel = $im.Groups[1].Value.Trim().Trim('"').Trim("'")
            $imgFull = Join-Path $repoRoot "documentation/$imgRel"
            if (-not (Test-Path $imgFull)) {
                $errors.Add("File $($rec.Name) references missing image '$imgRel'")
            }
        }
    }
}

# 4. Verify HTML Page Compilation & Synchronization
if (Test-Path $generatorScript) {
    Write-Host "`n--- Running Generator Synchronization Check ---" -ForegroundColor Yellow
    & pwsh -NoProfile -File $generatorScript -Check
    if ($LASTEXITCODE -ne 0) {
        $errors.Add("Documentation HTML pages are not in sync with external Markdown sources. Run generate-docs-pages.ps1.")
    }
}

# 5. Final Output
Write-Host ""
if ($errors.Count -gt 0) {
    Write-Host "assert-docs-external-content: FAILED with $($errors.Count) errors:" -ForegroundColor Red
    foreach ($err in $errors) {
        Write-Host "  - $err" -ForegroundColor Red
    }
    exit 1
}

Write-Host "assert-docs-external-content: PASS (All external Markdown recipes valid, snippets present, HTML synchronized)" -ForegroundColor Green
exit 0
