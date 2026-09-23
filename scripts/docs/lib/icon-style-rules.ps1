#requires -Version 7.0
<#
.SYNOPSIS
    The measurable glyph style of ICON-RENDER 0.10 section 10 item A, shared by the icon contract
    exporter and scripts/quality/assert-icon-style.ps1.

.DESCRIPTION
    Dot-source after scripts/docs/lib/icon-contract-svg.ps1 (it needs Convert-Drawable and $ANDROID).
      Get-DrawableStructure <path>        viewport, paints, stroke widths, gradient of one <vector> file
      Invoke-GlyphMeasure <repoRoot> <jobs> ink box, centres, weight of each SVG, through
                                          scripts/docs/lib/measure_glyph_style.py in one Python process
      Test-GlyphStyle <structure> <metric> the rule violations, each @{ Rule; Value }
      Read-StyleExceptions <path>         name -> rule -> reason from an exceptions file
    Rule names: grid, paint, stroke, margin, centre, weight. An exceptions line is
    "name | rule | reason"; rule "*" excuses every rule for that name.
    Not a script: no exit codes of its own. Invoke-GlyphMeasure throws when Python or the measure
    fails; a caller maps that to its own "could not verify".
#>

$script:StyleMargin = 1.0
$script:StyleCentre = 1.0
$script:StyleWeight = 1.3
# ink edges come from a 10 px-per-unit raster; half a pixel of antialiasing is not a margin violation
$script:StyleEdgeTolerance = 0.05

function Get-DrawableStructure([string] $path) {
    $doc = New-Object System.Xml.XmlDocument
    $doc.LoadXml([System.IO.File]::ReadAllText($path).TrimStart([char]0xFEFF))
    $root = $doc.DocumentElement
    if ($root.LocalName -ne 'vector') { return $null }
    $paints = New-Object System.Collections.Generic.HashSet[string]
    $strokes = New-Object System.Collections.Generic.HashSet[string]
    $gradient = $false
    foreach ($n in $root.SelectNodes('//*')) {
        if ($n.LocalName -eq 'gradient') { $gradient = $true }
        if ($n.LocalName -ne 'path') { continue }
        foreach ($a in 'fillColor', 'strokeColor') {
            $v = $n.GetAttribute($a, $ANDROID).Trim()
            if (-not $v) { continue }
            if ($v -match '^#00[0-9A-Fa-f]{6}$' -or $v -ieq '@android:color/transparent') { continue }
            [void]$paints.Add($v.ToLower())
        }
        $sc = $n.GetAttribute('strokeColor', $ANDROID).Trim()
        $sw = $n.GetAttribute('strokeWidth', $ANDROID).Trim()
        if ($sc -and $sc -ine '@android:color/transparent' -and $sc -notmatch '^#00' -and $sw -and [double]::Parse($sw, [cultureinfo]::InvariantCulture) -gt 0) {
            [void]$strokes.Add($sw)
        }
    }
    return [pscustomobject]@{
        ViewportW    = [double]::Parse($root.GetAttribute('viewportWidth', $ANDROID), [cultureinfo]::InvariantCulture)
        ViewportH    = [double]::Parse($root.GetAttribute('viewportHeight', $ANDROID), [cultureinfo]::InvariantCulture)
        Paints       = @($paints | Sort-Object)
        StrokeWidths = @($strokes | Sort-Object)
        Gradient     = $gradient
    }
}

function Get-StylePython([string] $repoRoot) {
    foreach ($p in (Join-Path $repoRoot '.venv/Scripts/python.exe'), (Join-Path $repoRoot '.venv/bin/python')) {
        if (Test-Path -LiteralPath $p) { return $p }
    }
    return $null
}

# $jobs: list of @{ name; svg (text); viewportW; viewportH }. Returns name -> metric object.
function Invoke-GlyphMeasure([string] $repoRoot, $jobs) {
    $py = Get-StylePython $repoRoot
    if (-not $py) { throw 'no repo venv (.venv) - provision it from scripts/docs/lib/requirements.txt' }
    $work = Join-Path ([System.IO.Path]::GetTempPath()) ('icon-style-' + [guid]::NewGuid().ToString('N'))
    $null = New-Item -ItemType Directory -Force -Path $work
    try {
        $list = New-Object System.Collections.Generic.List[object]
        $i = 0
        foreach ($j in $jobs) {
            $i++
            $svgPath = Join-Path $work ("g$i.svg")
            [System.IO.File]::WriteAllText($svgPath, $j.svg)
            $list.Add([ordered]@{ name = $j.name; svg = $svgPath; viewportW = $j.viewportW; viewportH = $j.viewportH })
        }
        $jobsPath = Join-Path $work 'jobs.json'
        $outPath = Join-Path $work 'out.json'
        [System.IO.File]::WriteAllText($jobsPath, ($list | ConvertTo-Json -Depth 3 -AsArray))
        $err = & $py (Join-Path $repoRoot 'scripts/docs/lib/measure_glyph_style.py') $jobsPath $outPath 2>&1
        if ($LASTEXITCODE -ne 0) { throw "measure_glyph_style.py exit $LASTEXITCODE`: $($err -join ' ')" }
        $map = @{}
        foreach ($m in (Get-Content -LiteralPath $outPath -Raw | ConvertFrom-Json)) { $map[$m.name] = $m }
        return $map
    }
    finally { Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue }
}

function Test-GlyphStyle($structure, $metric) {
    $out = New-Object System.Collections.Generic.List[object]
    if ($structure.ViewportW -ne 24 -or $structure.ViewportH -ne 24) {
        $out.Add(@{ Rule = 'grid'; Value = "viewport $($structure.ViewportW)x$($structure.ViewportH)" })
    }
    if ($structure.Gradient -or $structure.Paints.Count -gt 1) {
        $out.Add(@{ Rule = 'paint'; Value = $(if ($structure.Gradient) { 'gradient' } else { ($structure.Paints -join ' ') }) })
    }
    $odd = @($structure.StrokeWidths | Where-Object { [double]::Parse($_, [cultureinfo]::InvariantCulture) -ne 2 })
    if ($odd.Count) { $out.Add(@{ Rule = 'stroke'; Value = 'stroke width ' + ($odd -join ', ') }) }
    if ($null -eq $metric -or $null -eq $metric.inkBox) { return $out }
    $b = $metric.inkBox
    $gridH = [double]$metric.grid[1]
    $lo = $script:StyleMargin - $script:StyleEdgeTolerance
    if ($b[0] -lt $lo -or $b[1] -lt $lo -or $b[2] -gt (24 - $lo) -or $b[3] -gt ($gridH - $lo)) {
        $out.Add(@{ Rule = 'margin'; Value = 'ink ' + ($b -join ',') })
    }
    $boxOff = [Math]::Max([Math]::Abs($metric.boxCentre[0]), [Math]::Abs($metric.boxCentre[1]))
    $massOff = [Math]::Max([Math]::Abs($metric.massCentre[0]), [Math]::Abs($metric.massCentre[1]))
    if ($boxOff -ge $script:StyleCentre -and $massOff -ge $script:StyleCentre) {
        $out.Add(@{ Rule = 'centre'; Value = "box $($metric.boxCentre -join ','), mass $($metric.massCentre -join ',')" })
    }
    if ($metric.weight -lt $script:StyleWeight) { $out.Add(@{ Rule = 'weight'; Value = "weight $($metric.weight)" }) }
    return $out
}

function Read-StyleExceptions([string] $path) {
    $map = @{}
    if (-not (Test-Path -LiteralPath $path)) { return $map }
    foreach ($line in [System.IO.File]::ReadAllLines($path)) {
        $t = $line.Trim()
        if (-not $t -or $t.StartsWith('#')) { continue }
        $parts = $t.Split('|', 3) | ForEach-Object { $_.Trim() }
        if ($parts.Count -lt 3) { continue }
        if (-not $map.ContainsKey($parts[0])) { $map[$parts[0]] = @{} }
        $map[$parts[0]][$parts[1]] = $parts[2]
    }
    return $map
}
