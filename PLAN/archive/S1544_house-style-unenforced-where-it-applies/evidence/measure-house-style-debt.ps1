# measure-house-style-debt.ps1
# One-off measurement for S1544: how much house-text-style debt already sits in the two
# domains the canon declares mandatory - documentation prose and user-visible UI strings.
# Read-only. Exit codes: 0 measured.

param(
    [switch]$ListFiles
)

$ErrorActionPreference = 'Stop'
# Walk up to the repo root rather than counting directory levels, so the script keeps working
# wherever it is filed - it was authored under temp/ and now lives in the ticket's evidence folder.
$root = $PSScriptRoot
while ($root -and -not (Test-Path (Join-Path $root 'app_v2'))) { $root = Split-Path -Parent $root }
if (-not $root) { Write-Error 'measure-house-style-debt: repo root not found above this script.' -ErrorAction Continue; exit 2 }

function Get-ProseLines {
    # Strips fenced code blocks and inline code spans from a markdown file, so a count
    # reflects prose only - the canon excludes code from the style scope.
    param([string]$Path)
    $text = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $lines = $text -split "`n"
    $inFence = $false
    $out = [System.Collections.Generic.List[string]]::new()
    foreach ($line in $lines) {
        if ($line -match '^\s*```') { $inFence = -not $inFence; continue }
        if ($inFence) { continue }
        $stripped = [regex]::Replace($line, '`[^`]*`', '')
        $out.Add($stripped)
    }
    return $out
}

function Measure-Markdown {
    param([string[]]$Roots)
    $files = @()
    foreach ($r in $Roots) {
        $p = Join-Path $root $r
        if (Test-Path $p) { $files += Get-ChildItem $p -Filter '*.md' -File -Recurse }
    }
    $ellipsis = 0; $dash = 0; $filesEllipsis = 0; $filesDash = 0
    $detail = [System.Collections.Generic.List[object]]::new()
    foreach ($f in $files) {
        $prose = Get-ProseLines -Path $f.FullName
        $joined = $prose -join "`n"
        $e = ([regex]::Matches($joined, '\.\.\.|…')).Count
        $d = ([regex]::Matches($joined, '[–—―]')).Count
        $ellipsis += $e; $dash += $d
        if ($e -gt 0) { $filesEllipsis++ }
        if ($d -gt 0) { $filesDash++ }
        if ($e -gt 0 -or $d -gt 0) {
            $detail.Add([pscustomobject]@{ File = $f.FullName.Substring($root.Length + 1); Ellipsis = $e; Dash = $d })
        }
    }
    return [pscustomobject]@{
        FileCount = $files.Count
        Ellipsis = $ellipsis
        Dash = $dash
        FilesWithEllipsis = $filesEllipsis
        FilesWithDash = $filesDash
        Detail = $detail
    }
}

function Measure-Strings {
    $files = Get-ChildItem (Join-Path $root 'app_v2/src/main/res') -Filter 'strings*.xml' -File -Recurse
    $files += Get-ChildItem (Join-Path $root 'wear/src/main/res') -Filter 'strings*.xml' -File -Recurse -ErrorAction SilentlyContinue
    $detail = [System.Collections.Generic.List[object]]::new()
    $ellipsis = 0; $dash = 0
    foreach ($f in $files) {
        $text = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
        # Values only: the text between > and < inside string/item elements.
        $values = [regex]::Matches($text, '(?s)<(?:string|item)\b[^>]*>(.*?)</(?:string|item)>')
        $e = 0; $d = 0
        foreach ($m in $values) {
            $v = $m.Groups[1].Value
            $e += ([regex]::Matches($v, '\.\.\.|…')).Count
            $d += ([regex]::Matches($v, '[–—―]')).Count
        }
        $ellipsis += $e; $dash += $d
        if ($e -gt 0 -or $d -gt 0) {
            $detail.Add([pscustomobject]@{ File = $f.FullName.Substring($root.Length + 1); Ellipsis = $e; Dash = $d })
        }
    }
    return [pscustomobject]@{
        FileCount = $files.Count
        Ellipsis = $ellipsis
        Dash = $dash
        Detail = $detail
    }
}

$docs = Measure-Markdown -Roots @('docs')
$strings = Measure-Strings

Write-Output "=== S1544 house-style debt measurement ==="
Write-Output ""
Write-Output "docs/**/*.md (prose only, fences and inline code stripped)"
Write-Output ("  files scanned      : {0}" -f $docs.FileCount)
Write-Output ("  ellipsis occurences: {0} in {1} files" -f $docs.Ellipsis, $docs.FilesWithEllipsis)
Write-Output ("  long-dash occurences: {0} in {1} files" -f $docs.Dash, $docs.FilesWithDash)
Write-Output ""
Write-Output "strings*.xml (element values only)"
Write-Output ("  files scanned      : {0}" -f $strings.FileCount)
Write-Output ("  ellipsis occurences: {0}" -f $strings.Ellipsis)
Write-Output ("  long-dash occurences: {0}" -f $strings.Dash)

if ($ListFiles) {
    Write-Output ""
    Write-Output "--- docs detail (top 25 by total) ---"
    $docs.Detail | Sort-Object { -($_.Ellipsis + $_.Dash) } | Select-Object -First 25 | Format-Table -AutoSize | Out-String | Write-Output
    Write-Output "--- strings detail ---"
    $strings.Detail | Sort-Object { -($_.Ellipsis + $_.Dash) } | Format-Table -AutoSize | Out-String | Write-Output
}

exit 0
