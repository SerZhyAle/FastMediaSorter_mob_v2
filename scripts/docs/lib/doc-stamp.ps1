# Last-edited stamp for Markdown and HTML documents - shared by stamp-doc-dates.ps1 and by every
# generator whose -Check compares its output with the file on disk.
#
# The stamp is one visible element carrying class="doc-stamp" whose text is `yy.MM.dd HH:mm`.
# Markdown gets `<sub class="doc-stamp">..</sub>` on its own line after the front matter and any
# leading HTML comments; HTML gets a small `<div class="doc-stamp">` straight after `<body>`.
#
# The date is the file's own LastWriteTime floored to the minute, and the writer puts that
# LastWriteTime back afterwards. A stamp older than the file's mtime therefore means exactly "edited
# since the stamp", with no state kept anywhere else.

Set-StrictMode -Version Latest

$script:DocStampFormat = 'yy.MM.dd HH:mm'
$script:DocStampDateRegex = '\d{2}\.\d{2}\.\d{2} \d{2}:\d{2}'
# One regex for both kinds: the date sits right after the opening tag of an element with the class.
$script:DocStampValueRegex = [regex]::new('(?<head><(?:sub|div) class="doc-stamp"[^>]*>)(?<date>' + $script:DocStampDateRegex + ')(?<tail><)')
# A whole stamp element, used to strip it before a generator compares its output with the disk.
$script:DocStampStripRegex = [regex]::new('(?m)^[ \t]*<sub class="doc-stamp">' + $script:DocStampDateRegex + '</sub>[ \t]*\r?\n(?:[ \t]*\r?\n)?|(?:\r?\n)?[ \t]*<div class="doc-stamp"[^>]*>' + $script:DocStampDateRegex + '</div>[ \t]*')
$script:DocHtmlStampStyle = 'font-size:11px;opacity:.6;margin:0;padding:2px 8px;text-align:right'

function Format-DocStampDate {
    param([Parameter(Mandatory)][datetime]$When)
    return $When.ToString($script:DocStampFormat, [cultureinfo]::InvariantCulture)
}

function ConvertFrom-DocStampDate {
    param([Parameter(Mandatory)][string]$Text)
    return [datetime]::ParseExact($Text, $script:DocStampFormat, [cultureinfo]::InvariantCulture)
}

function Get-DocStampKind {
    param([Parameter(Mandatory)][string]$Path)
    switch -Regex ($Path) {
        '\.md$'          { return 'md' }
        '\.html?$'       { return 'html' }
        default          { return $null }
    }
}

function Remove-DocStamp {
    # Text with the stamp element removed, for generator -Check comparisons.
    param([AllowNull()][string]$Text)
    if ($null -eq $Text) { return $null }
    return $script:DocStampStripRegex.Replace($Text, '')
}

function Get-DocStampValue {
    # The stamp's date text, or $null when the text carries none in its first 64 KB.
    param([Parameter(Mandatory)][string]$Text)
    $head = if ($Text.Length -gt 65536) { $Text.Substring(0, 65536) } else { $Text }
    $m = $script:DocStampValueRegex.Match($head)
    if ($m.Success) { return $m.Groups['date'].Value }
    return $null
}

function Get-DocEol {
    param([Parameter(Mandatory)][string]$Text)
    $i = $Text.IndexOf("`n")
    if ($i -gt 0 -and $Text[$i - 1] -eq "`r") { return "`r`n" }
    return "`n"
}

function Get-MdStampInsertIndex {
    # Offset right after Jekyll front matter, blank lines and leading HTML comment blocks.
    param([Parameter(Mandatory)][string]$Text)
    $pos = 0
    if ($Text -match '\A---[ \t]*\r?\n') {
        $close = [regex]::Match($Text.Substring($Matches[0].Length), '(?m)^---[ \t]*(?:\r?\n|\z)')
        if ($close.Success) { $pos = $Matches[0].Length + $close.Index + $close.Length }
    }
    while ($pos -lt $Text.Length) {
        $rest = $Text.Substring($pos, [Math]::Min(8192, $Text.Length - $pos))
        $blank = [regex]::Match($rest, '\A[ \t]*\r?\n')
        if ($blank.Success) { $pos += $blank.Length; continue }
        if ($rest.StartsWith('<!--')) {
            $end = $Text.IndexOf('-->', $pos, [StringComparison]::Ordinal)
            if ($end -lt 0) { break }
            $eol = $Text.IndexOf("`n", $end, [StringComparison]::Ordinal)
            $pos = if ($eol -lt 0) { $Text.Length } else { $eol + 1 }
            continue
        }
        break
    }
    return $pos
}

function Add-DocStamp {
    # Text with a stamp inserted (none present) or its date replaced (one present). $null = no place
    # to put one (an HTML fragment without <body>).
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][ValidateSet('md', 'html')][string]$Kind,
        [Parameter(Mandatory)][string]$Date
    )
    $m = $script:DocStampValueRegex.Match($Text)
    if ($m.Success -and $m.Index -lt 65536) {
        $g = $m.Groups['date']
        return $Text.Substring(0, $g.Index) + $Date + $Text.Substring($g.Index + $g.Length)
    }
    $eol = Get-DocEol -Text $Text
    if ($Kind -eq 'md') {
        $at = Get-MdStampInsertIndex -Text $Text
        $line = '<sub class="doc-stamp">' + $Date + '</sub>' + $eol + $eol
        return $Text.Substring(0, $at) + $line + $Text.Substring($at)
    }
    $body = [regex]::Match($Text, '(?i)<body(?:\s[^>]*)?>')
    if (-not $body.Success) { return $null }
    $div = '<div class="doc-stamp" style="' + $script:DocHtmlStampStyle + '">' + $Date + '</div>'
    $at = $body.Index + $body.Length
    return $Text.Substring(0, $at) + $eol + $div + $Text.Substring($at)
}

function Read-DocFile {
    param([Parameter(Mandatory)][string]$Path)
    $bytes = [IO.File]::ReadAllBytes($Path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $offset = if ($hasBom) { 3 } else { 0 }
    $text = [Text.UTF8Encoding]::new($false).GetString($bytes, $offset, $bytes.Length - $offset)
    return [pscustomobject]@{ Text = $text; HasBom = $hasBom }
}

function Write-DocFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][bool]$HasBom,
        [Parameter(Mandatory)][datetime]$KeepTime
    )
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($HasBom))
    [IO.File]::SetLastWriteTime($Path, $KeepTime)
}

function Update-DocStampInPlace {
    # Overwrites the 14 date bytes of an existing stamp without rewriting the file. A multi-megabyte
    # append-only log is another process's write target, and a whole-file rewrite here could drop a
    # row that process appended in between; the date is fixed-width ASCII, so no byte moves.
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Date,
        [Parameter(Mandatory)][datetime]$KeepTime
    )
    $fs = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::ReadWrite)
    try {
        $buf = New-Object byte[] ([Math]::Min(65536, [int]$fs.Length))
        $null = $fs.Read($buf, 0, $buf.Length)
        $m = $script:DocStampValueRegex.Match([Text.Encoding]::Latin1.GetString($buf))
        if (-not $m.Success) { return $false }
        $fs.Position = $m.Groups['date'].Index
        $bytes = [Text.Encoding]::ASCII.GetBytes($Date)
        $fs.Write($bytes, 0, $bytes.Length)
    } finally {
        $fs.Dispose()
    }
    [IO.File]::SetLastWriteTime($Path, $KeepTime)
    return $true
}
