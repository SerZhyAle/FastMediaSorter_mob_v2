# fix-ellipsis.ps1
# Replaces "..." (three dots) with ".." (two dots) according to author style.
# Applies to strings.xml UI text and markdown documents.
# SKIPS: code blocks, inline code, URL paths (e.g. .../drive), file path placeholders.
#
# Usage:
#   .\scripts\utils\fix-ellipsis.ps1                    # dry run
#   .\scripts\utils\fix-ellipsis.ps1 -Apply             # apply changes
#   .\scripts\utils\fix-ellipsis.ps1 -Apply -Verbose    # apply with details
#   .\scripts\utils\fix-ellipsis.ps1 -File "path/to/file.xml" -Apply

param(
    [string]$File = "",
    [switch]$Apply,
    [switch]$Verbose
)

$root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent

# Target files
$xmlFiles = @(
    "$root\app_v2\src\main\res\values\strings.xml",
    "$root\app_v2\src\main\res\values-ru\strings.xml",
    "$root\app_v2\src\main\res\values-uk\strings.xml",
    "$root\wear\src\main\res\values\strings.xml",
    "$root\wear\src\main\res\values-ru\strings.xml",
    "$root\wear\src\main\res\values-uk\strings.xml",
    "$root\app_v2\src\debug\res\values\strings.xml"
)

$mdFiles = Get-ChildItem "$root\docs" -Filter "*.md" | Select-Object -ExpandProperty FullName

if ($File -ne "") {
    $xmlFiles = @()
    $mdFiles = @($File)
}

$totalChanges = 0

# ─── XML strings.xml processing ───────────────────────────────────────────────
# Replaces ... in text content of <string> tags.
# Does NOT change: attribute values, URLs with .../path patterns.
function Fix-XmlFile($path) {
    if (-not (Test-Path $path)) { return }
    $content = Get-Content $path -Encoding UTF8 -Raw
    $changes = 0

    # Match text content inside string tags, replace ... but not .../
    # Pattern: captures everything between > and < in string elements
    $newContent = [regex]::Replace($content,
        '(?<=<string[^>]*>)(.*?)(?=</string>)',
        {
            param($m)
            $text = $m.Value
            # Replace ... that is NOT followed by /  (to preserve URL paths like .../drive)
            $newText = [regex]::Replace($text, '\.\.\.(?!/)', '..')
            if ($newText -ne $text) { $script:changes++ }
            return $newText
        },
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )

    if ($newContent -ne $content) {
        $relPath = $path.Replace($root + "\", "")
        Write-Host "[XML] $relPath - replaced $changes occurrence(s)" -ForegroundColor Cyan
        if ($Apply) {
            $newContent | Set-Content $path -Encoding UTF8 -NoNewline
        }
        $script:totalChanges += $changes
    }
}

# ─── Markdown processing ───────────────────────────────────────────────────────
# Processes line-by-line, skipping code blocks and inline code.
function Fix-MdFile($path) {
    if (-not (Test-Path $path)) { return }
    $lines = Get-Content $path -Encoding UTF8
    $inCodeBlock = $false
    $fileChanged = $false
    $changes = 0

    $newLines = for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]

        # Toggle code block
        if ($line -match '^```') {
            $inCodeBlock = -not $inCodeBlock
            $line
            continue
        }

        if ($inCodeBlock) {
            # Inside fenced code block - do not change
            $line
            continue
        }

        # Skip lines that are purely code references (indent 4+ spaces or starts with tab)
        if ($line -match '^(\s{4}|\t)') {
            $line
            continue
        }

        # On remaining lines, replace ... but NOT when:
        # 1. Inside backtick sequences `...`
        # 2. Followed by / (URL/path placeholder like .../folder)
        $newLine = ""
        $pos = 0
        $inInlineCode = $false
        $chars = $line.ToCharArray()
        $j = 0
        while ($j -lt $chars.Length) {
            if ($chars[$j] -eq '`') {
                $inInlineCode = -not $inInlineCode
                $newLine += $chars[$j]
                $j++
            }
            elseif (-not $inInlineCode -and
                $j + 2 -lt $chars.Length -and
                $chars[$j] -eq '.' -and $chars[$j + 1] -eq '.' -and $chars[$j + 2] -eq '.' -and
                ($j + 3 -ge $chars.Length -or $chars[$j + 3] -ne '/')) {
                # Replace ... with ..
                $newLine += '..'
                $j += 3
                $changes++
            }
            else {
                $newLine += $chars[$j]
                $j++
            }
        }

        if ($newLine -ne $line) { $fileChanged = $true }
        $newLine
    }

    if ($fileChanged) {
        $relPath = $path.Replace($root + "\", "")
        Write-Host "[MD ] $relPath - replaced $changes occurrence(s)" -ForegroundColor Cyan
        if ($Apply) {
            $newLines | Set-Content $path -Encoding UTF8
        }
        $script:totalChanges += $changes
    }
}

# ─── Main ─────────────────────────────────────────────────────────────────────
Write-Host ""
if (-not $Apply) {
    Write-Host "DRY RUN mode — no files will be modified. Use -Apply to apply changes." -ForegroundColor Yellow
}
Write-Host "Processing strings.xml files..." -ForegroundColor Gray

foreach ($f in $xmlFiles) { Fix-XmlFile $f }

Write-Host "Processing markdown docs..." -ForegroundColor Gray
foreach ($f in $mdFiles) { Fix-MdFile $f }

Write-Host ""
if ($totalChanges -eq 0) {
    Write-Host "No '...' found — all files already use '..' style." -ForegroundColor Green
}
else {
    if ($Apply) {
        Write-Host "Done. Total replacements: $totalChanges" -ForegroundColor Green
    }
    else {
        Write-Host "Would replace $totalChanges occurrence(s). Run with -Apply to apply." -ForegroundColor Yellow
    }
}
