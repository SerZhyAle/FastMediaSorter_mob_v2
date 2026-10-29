#requires -Version 7.0
<#
.SYNOPSIS
    S4140 - render the usage-scheme illustrations (exchange server, live broadcast, home network) as SVG
    in every documented language.

.DESCRIPTION
    The pictures are drawn by scripts/docs/usage-schemes/Diagrams.ps1 with the primitives of SvgKit.ps1;
    every word on them comes from docs/media-exchange-portal/labels/labels.<lang>.json. One drawing
    and three label files means a translation can never drift from its picture's layout, and the SVGs
    under docs/media-exchange-portal/diagrams/ are render targets - never edit them.

    The label files must carry the same key set, and every key must be read by some picture: a key
    present in one language only, or read by nothing, is refused before anything is written.

.PARAMETER Check
    Render in memory and compare with the files on disk; write nothing. Exit 1 on any difference.

.PARAMETER Lang
    Languages to render. Defaults to every labels.<lang>.json found.

.PARAMETER OutDir
    Output directory, relative to the repository root.

.PARAMETER Preview
    Also write an HTML contact sheet of every rendered picture to this path (for a visual review in a
    browser). Ignored with -Check.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/render-usage-schemes.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/render-usage-schemes.ps1 -Check

.NOTES
    Exit codes:
      0  rendered (or, with -Check, everything on disk is current)
      1  -Check found a missing or stale picture
      2  invalid input: label files disagree on keys, a key is missing or unused, or a drawing failed
#>
[CmdletBinding()]
param(
    [switch] $Check,
    [string[]] $Lang,
    [string] $OutDir = 'docs/media-exchange-portal/diagrams',
    [string] $Preview
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$labelsDir = Join-Path $repoRoot 'docs/media-exchange-portal/labels'
. (Join-Path $PSScriptRoot 'usage-schemes/SvgKit.ps1')
. (Join-Path $PSScriptRoot 'usage-schemes/Diagrams.ps1')

$labelFiles = Get-ChildItem -Path $labelsDir -Filter 'labels.*.json' | Sort-Object Name
if (-not $labelFiles) {
    Write-Host "render-usage-schemes: no label files under $labelsDir"
    exit 2
}
$sets = [ordered]@{}
foreach ($f in $labelFiles) {
    $code = $f.Name -replace '^labels\.(.+)\.json$', '$1'
    try {
        $sets[$code] = Get-Content -Raw -Encoding utf8 $f.FullName | ConvertFrom-Json -AsHashtable
    }
    catch {
        Write-Host "render-usage-schemes: $($f.Name) is not valid JSON - $($_.Exception.Message)"
        exit 2
    }
}

$reference = @($sets['en'].Keys | Sort-Object)
foreach ($code in $sets.Keys) {
    $keys = @($sets[$code].Keys | Sort-Object)
    $missing = @($reference | Where-Object { $_ -notin $keys })
    $extra = @($keys | Where-Object { $_ -notin $reference })
    if ($missing -or $extra) {
        Write-Host "render-usage-schemes: labels.$code.json disagrees with labels.en.json"
        if ($missing) { Write-Host "  missing: $($missing -join ', ')" }
        if ($extra) { Write-Host "  extra:   $($extra -join ', ')" }
        exit 2
    }
}

# The render loop overwrites $script:Lang (the drawings read it), so the whole-set judgement is taken now.
$allLanguages = -not $Lang
$languages = if ($Lang) { $Lang } else { @($sets.Keys) }
$outRoot = Join-Path $repoRoot $OutDir
if (-not $Check -and -not (Test-Path $outRoot)) { New-Item -ItemType Directory -Force -Path $outRoot | Out-Null }

$stale = [System.Collections.Generic.List[string]]::new()
$written = 0
$rendered = [System.Collections.Generic.List[object]]::new()
foreach ($code in $languages) {
    if (-not $sets.Contains($code)) {
        Write-Host "render-usage-schemes: no labels.$code.json"
        exit 2
    }
    $script:Lang = $code
    $script:Labels = $sets[$code]
    foreach ($d in $script:UsageSchemeDiagrams) {
        Use-SvgStyle $d.style
        try {
            $svg = (& $d.build) + "`n"
        }
        catch {
            Write-Host "render-usage-schemes: $($d.name) ($code) failed - $($_.Exception.Message)"
            exit 2
        }
        $file = "$($d.name)-$code.svg"
        $path = Join-Path $outRoot $file
        $rendered.Add([pscustomobject]@{ file = $file; lang = $code; name = $d.name; style = $d.style })
        if ($Check) {
            $current = if (Test-Path $path) { [System.IO.File]::ReadAllText($path) } else { $null }
            if ($current -ne $svg) { $stale.Add("$OutDir/$file") }
        }
        else {
            [System.IO.File]::WriteAllText($path, $svg, [System.Text.UTF8Encoding]::new($false))
            $written++
        }
    }
}

$unused = @($reference | Where-Object { -not $script:LabelsUsed.Contains($_) })
if ($unused -and $allLanguages) {
    Write-Host "render-usage-schemes: label keys no picture reads: $($unused -join ', ')"
    exit 2
}

if ($Check) {
    if ($stale.Count) {
        Write-Host "render-usage-schemes: $($stale.Count) picture(s) missing or stale - run without -Check:"
        $stale | ForEach-Object { Write-Host "  $_" }
        exit 1
    }
    Write-Host "render-usage-schemes: $($rendered.Count) picture(s) current"
    exit 0
}

if ($Preview) {
    $rel = [System.IO.Path]::GetRelativePath((Split-Path -Parent ([System.IO.Path]::GetFullPath($Preview))), $outRoot).Replace('\', '/')
    $cards = foreach ($r in $rendered) {
        "<figure><img src=`"$rel/$($r.file)`" loading=`"lazy`"><figcaption>$($r.file) - $($r.style)</figcaption></figure>"
    }
    $html = "<!doctype html><meta charset=`"utf-8`"><title>Usage schemes</title><style>body{background:#777;font:14px sans-serif;margin:16px}figure{margin:0 0 24px}img{width:100%;max-width:1600px;display:block}</style>$($cards -join "`n")"
    [System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($Preview), $html, [System.Text.UTF8Encoding]::new($false))
}
Write-Host "render-usage-schemes: wrote $written picture(s) to $OutDir"
exit 0
