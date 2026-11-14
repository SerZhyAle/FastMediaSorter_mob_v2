<#
.SYNOPSIS
    Carries the program's map name, "Fast Media Sorter & Organizer", through running text.

.DESCRIPTION
    DEVICE-EXCHANGE item CX (1.14): in a README, a help page, a portal page or a screen, a program is named by
    the Tool cell of its row in SITE-FAMILY-MAP section 2. The one-word "FastMediaSorter" and the repository's
    working name "FastMediaSorter v2" are second names, so this script rewrites them in running text (S4630).

    Never rewritten: code spans and fenced blocks, Markdown link targets, URLs, HTML/XML tags (except the
    visible alt/title/aria-label/content attribute values), JSON keys, comments, and every glued identifier
    (FastMediaSorter_mob_v2, Widget.FastMediaSorter.Button, FastMediaSorterApp, FastMediaSorter-x). The separate
    watch face keeps its own name "FastMediaSorter": a line carrying its Play id (fastmediasorter.watchface) and
    the Russian/Ukrainian "Циферблат FastMediaSorter" are left alone, and so is every `wear_watchface_*` string.

    Listed as ambiguous and left alone: a compound glued with a hyphen (FastMediaSorter-Katalog) and the stem
    followed by Watch, VR, LITE, Lite, Web, Widgets or "for Windows" - a hand decision each, because the
    words may name another program.

    The file's BOM and line endings are kept byte for byte; only the matched name changes.

.PARAMETER Paths
    Files or directories (recursed for .md, .html, .htm, .xml, .json, .jsonl, .txt).

.PARAMETER Check
    Write nothing; exit 1 when any rewritable occurrence remains.

.PARAMETER Json
    Print the per-file result as JSON instead of text lines.

.PARAMETER ReportPath
    Also write every ambiguous occurrence as "path:line: context" to this file.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/carry-program-name.ps1 -Paths README.md,docs/content -Check

.NOTES
    Exit codes:
      0  every file is clean, or the rewrite was written (no -Check)
      1  -Check found at least one rewritable occurrence
      2  bad arguments: a path does not exist
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string[]] $Paths,
    [switch] $Check,
    [switch] $Json,
    [string] $ReportPath
)

$ErrorActionPreference = 'Stop'

$extensions = @('.md', '.html', '.htm', '.xml', '.json', '.jsonl', '.txt')
$followers = 'Watch|watch|VR|LITE|Lite|Web|Widgets|for Windows'
$rewriteRx = [regex]::new(
    "(?<![\w/.@\-])FastMediaSorter(?: v2(?!\w)(?!\.\w))?(?![\w/\-])(?!\.\w)(?!\s+(?:$followers)\b)")
$ambiguousRx = [regex]::new("(?<![\w/.@\-])FastMediaSorter(?=-\w|\s+(?:$followers)\b)")
$urlRx = 'https?://[^\s)<>"''\]]+'
$visibleAttrRx = [regex]::new('\b(?:alt|title|aria-label|content)\s*=\s*"([^"]*)"')

function Get-ProtectedSpans {
    param([string] $Text, [string] $Extension)

    $patterns = switch ($Extension) {
        '.md' { @('```[\s\S]*?```', '~~~[\s\S]*?~~~', '`[^`\r\n]+`', '\]\([^)\r\n]*\)', '<!--[\s\S]*?-->', $urlRx) }
        { $_ -in '.html', '.htm' } {
            @('<!--[\s\S]*?-->', '<script[\s\S]*?</script>', '<style[\s\S]*?</style>', '<pre[\s\S]*?</pre>',
              '<code[\s\S]*?</code>', $urlRx)
        }
        '.xml' {
            @('<!--[\s\S]*?-->', '<!\[CDATA\[[\s\S]*?\]\]>', $urlRx,
              '<string name="wear_watchface[^"]*"[^>]*>[\s\S]*?</string>')
        }
        { $_ -in '.json', '.jsonl' } { @('"(?:[^"\\]|\\.)*"\s*:', $urlRx) }
        default { @($urlRx) }
    }
    # The separate watch face is its own product, named "FastMediaSorter" on its Play listing, with no map
    # row, so it keeps that name: a line carrying its Play id, and the Russian/Ukrainian "watch face" noun.
    $patterns = @($patterns) + @('[^\n]*fastmediasorter\.watchface[^\n]*', '[Цц]иферблат FastMediaSorter')
    $spans = [System.Collections.Generic.List[int[]]]::new()
    foreach ($p in $patterns) {
        foreach ($m in [regex]::Matches($Text, $p)) { $spans.Add([int[]]@($m.Index, ($m.Index + $m.Length))) }
    }
    if ($Extension -in '.md', '.html', '.htm', '.xml') {
        # A tag is protected except the attribute values a reader sees as text.
        foreach ($tag in [regex]::Matches($Text, '<[A-Za-z/!?][^<>]*>')) {
            $cursor = $tag.Index
            $end = $tag.Index + $tag.Length
            if ($Extension -ne '.xml') {
                foreach ($a in $visibleAttrRx.Matches($tag.Value)) {
                    $valueStart = $tag.Index + $a.Groups[1].Index
                    $spans.Add(@($cursor, $valueStart))
                    $cursor = $valueStart + $a.Groups[1].Length
                }
            }
            $spans.Add(@($cursor, $end))
        }
    }
    return , $spans
}

function Test-Protected {
    param([System.Collections.Generic.List[int[]]] $Spans, [int] $Index)
    foreach ($s in $Spans) {
        if ($Index -ge $s[0] -and $Index -lt $s[1]) { return $true }
    }
    return $false
}

function Get-LineNumber {
    param([string] $Text, [int] $Index)
    return ([regex]::Matches($Text.Substring(0, $Index), "`n")).Count + 1
}

$files = [System.Collections.Generic.List[string]]::new()
foreach ($p in $Paths) {
    foreach ($single in ($p -split ',')) {
        $item = $single.Trim()
        if (-not $item) { continue }
        if (-not (Test-Path -LiteralPath $item)) {
            Write-Error "carry-program-name: path not found: $item" -ErrorAction Continue
            exit 2
        }
        if ((Get-Item -LiteralPath $item).PSIsContainer) {
            Get-ChildItem -LiteralPath $item -Recurse -File |
                Where-Object { $extensions -contains $_.Extension.ToLowerInvariant() } |
                ForEach-Object { $files.Add($_.FullName) }
        } else {
            $files.Add((Resolve-Path -LiteralPath $item).Path)
        }
    }
}

$utf8 = [System.Text.UTF8Encoding]::new($false)
$results = [System.Collections.Generic.List[object]]::new()
$reportLines = [System.Collections.Generic.List[string]]::new()
$rewritableTotal = 0

foreach ($file in ($files | Sort-Object -Unique)) {
    $bytes = [System.IO.File]::ReadAllBytes($file)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $offset = if ($hasBom) { 3 } else { 0 }
    $text = $utf8.GetString($bytes, $offset, $bytes.Length - $offset)
    if ($text.IndexOf('FastMediaSorter', [StringComparison]::Ordinal) -lt 0) { continue }

    $ext = [System.IO.Path]::GetExtension($file).ToLowerInvariant()
    $spans = Get-ProtectedSpans -Text $text -Extension $ext
    $hits = @($rewriteRx.Matches($text) | Where-Object { -not (Test-Protected -Spans $spans -Index $_.Index) })
    $ambiguous = @($ambiguousRx.Matches($text) | Where-Object { -not (Test-Protected -Spans $spans -Index $_.Index) })
    if ($hits.Count -eq 0 -and $ambiguous.Count -eq 0) { continue }

    $relative = [System.IO.Path]::GetRelativePath((Get-Location).Path, $file).Replace('\', '/')
    foreach ($a in $ambiguous) {
        $line = Get-LineNumber -Text $text -Index $a.Index
        $from = [Math]::Max(0, $a.Index - 40)
        $context = $text.Substring($from, [Math]::Min(100, $text.Length - $from)) -replace '\s+', ' '
        $reportLines.Add("${relative}:${line}: $context")
    }
    $rewritableTotal += $hits.Count

    if (-not $Check -and $hits.Count -gt 0) {
        $name = if ($ext -in '.xml', '.html', '.htm') { 'Fast Media Sorter &amp; Organizer' } else { 'Fast Media Sorter & Organizer' }
        $builder = [System.Text.StringBuilder]::new($text)
        foreach ($h in ($hits | Sort-Object Index -Descending)) {
            [void]$builder.Remove($h.Index, $h.Length).Insert($h.Index, $name)
        }
        $outBytes = $utf8.GetBytes($builder.ToString())
        if ($hasBom) { $outBytes = [byte[]](0xEF, 0xBB, 0xBF) + $outBytes }
        [System.IO.File]::WriteAllBytes($file, $outBytes)
    }
    $results.Add([pscustomobject]@{ path = $relative; rewritable = $hits.Count; ambiguous = $ambiguous.Count })
}

if ($ReportPath) {
    $dir = Split-Path -Parent $ReportPath
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllLines($ReportPath, [string[]]$reportLines, $utf8)
}

$mode = if ($Check) { 'check' } else { 'write' }
if ($Json) {
    [pscustomobject]@{ mode = $mode; rewritable = $rewritableTotal; ambiguous = $reportLines.Count; files = $results } |
        ConvertTo-Json -Depth 4
} else {
    foreach ($r in $results) { "{0,4} rewritable {1,3} ambiguous  {2}" -f $r.rewritable, $r.ambiguous, $r.path }
    "carry-program-name: $mode - $rewritableTotal rewritable, $($reportLines.Count) ambiguous, $($results.Count) file(s)"
}

if ($Check -and $rewritableTotal -gt 0) { exit 1 }
exit 0
