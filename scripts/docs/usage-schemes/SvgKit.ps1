# SvgKit - drawing primitives for the usage-scheme illustrations (S4140).
# Dot-sourced by scripts/docs/render-usage-schemes.ps1; renders nothing on its own.
#
# Every number written into the SVG goes through Format-SvgNumber: string interpolation of a double is
# culture-invariant in PowerShell, but [string]::Format and ToString() are not, and a decimal comma
# inside a path silently breaks the whole picture on a Russian-locale workstation.

Set-StrictMode -Version Latest

$script:SvgFont = "'Segoe UI', Roboto, 'Noto Sans', 'Helvetica Neue', Arial, sans-serif"
$script:SvgMono = "Consolas, 'Roboto Mono', 'DejaVu Sans Mono', monospace"

# One palette per visual style. Keys are shared so a diagram can switch style without renaming colours.
$script:Palettes = @{
    scheme = @{
        bg = '#0b1220'; grid = '#14213a'; panel = '#101b2d'; panelStroke = '#2b3f60'; zone = '#0f1a2b'
        zoneStroke = '#24364f'; text = '#e6edf3'; muted = '#8fa3bf'; control = '#4fc3f7'; tunnel = '#3fb950'
        media = '#d29922'; lan = '#a78bfa'; danger = '#f87171'; server = '#2dd4bf'; card = '#13223a'
    }
    slide = @{
        bg = '#0a1628'; bg2 = '#0e3a3a'; panel = '#10243b'; panelStroke = '#1f4f5f'; zone = '#0d2034'
        zoneStroke = '#1f4a5c'; text = '#ffffff'; muted = '#a9bdd2'; control = '#38bdf8'; tunnel = '#34d399'
        media = '#fbbf24'; lan = '#c4b5fd'; danger = '#fca5a5'; server = '#2dd4bf'; card = '#12304a'
    }
    simple = @{
        bg = '#f6f8fb'; grid = '#e9eef5'; panel = '#ffffff'; panelStroke = '#d5dde8'; zone = '#eef3f9'
        zoneStroke = '#cfd9e6'; text = '#1f2933'; muted = '#5b6b7f'; control = '#1565c0'; tunnel = '#2e7d32'
        media = '#e65100'; lan = '#6a1b9a'; danger = '#c62828'; server = '#00796b'; card = '#ffffff'
    }
}

# Material Design icon paths (Apache 2.0, the same family the site already embeds), 24x24 viewBox.
$script:IconPaths = @{
    phone    = 'M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z'
    watch    = 'M20 12c0-2.54-1.19-4.81-3.04-6.27L16 0H8l-.95 5.73C5.19 7.19 4 9.45 4 12s1.19 4.81 3.05 6.27L8 24h8l.96-5.73C18.81 16.81 20 14.54 20 12zM6 12c0-3.31 2.69-6 6-6s6 2.69 6 6-2.69 6-6 6-6-2.69-6-6z'
    tv       = 'M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z'
    desktop  = 'M20 2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h7v2H8v2h8v-2h-3v-2h7c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H4V4h16v12z'
    laptop   = 'M20 18c1.1 0 1.99-.9 1.99-2L22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2H0v2h24v-2h-4zM4 6h16v10H4V6z'
    server   = 'M20 13H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1v-6c0-.55-.45-1-1-1zM7 19c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM20 3H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1zM7 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z'
    cloud    = 'M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z'
    router   = 'M20.2 5.9l.8-.8C19.6 3.7 17.8 3 16 3s-3.6.7-5 2.1l.8.8C13 4.8 14.5 4.2 16 4.2s3 .6 4.2 1.7zm-.9.8c-.9-.9-2.1-1.4-3.3-1.4s-2.4.5-3.3 1.4l.8.8c.7-.7 1.6-1 2.5-1 .9 0 1.8.3 2.5 1l.8-.8zM19 13h-2V9h-2v4H5c-1.1 0-2 .9-2 2v4c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-4c0-1.1-.9-2-2-2zM8 18H6v-2h2v2zm3.5 0h-2v-2h2v2zm3.5 0h-2v-2h2v2z'
    home     = 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z'
    globe    = 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z'
    folder   = 'M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'
    lock     = 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z'
    antenna  = 'M7.76 16.24C6.67 15.16 6 13.66 6 12s.67-3.16 1.76-4.24l1.42 1.42C8.45 9.9 8 10.9 8 12c0 1.1.45 2.1 1.17 2.83l-1.41 1.41zm8.48 0C17.33 15.16 18 13.66 18 12s-.67-3.16-1.76-4.24l-1.42 1.42C15.55 9.9 16 10.9 16 12c0 1.1-.45 2.1-1.17 2.83l1.41 1.41zM12 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm8 2c0 2.21-.9 4.21-2.35 5.65l1.42 1.42C20.88 17.26 22 14.76 22 12s-1.12-5.26-2.93-7.070l-1.42 1.42C19.1 7.79 20 9.79 20 12zM6.35 6.35L4.93 4.93C3.12 6.74 2 9.24 2 12s1.12 5.26 2.93 7.07l1.42-1.42C4.9 16.21 4 14.21 4 12s.9-4.21 2.35-5.65z'
    camera   = 'M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z'
    mic      = 'M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z'
    person   = 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z'
    log      = 'M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z'
    wifi     = 'M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z'
    cellular = 'M2 22h20V2z'
    shield   = 'M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z'
    qr       = 'M3 11h8V3H3v8zm2-6h4v4H5V5zm-2 16h8v-8H3v8zm2-6h4v4H5v-4zM13 3v8h8V3h-8zm6 6h-4V5h4v4zm0 10h2v2h-2zm-6-6h2v2h-2zm2 2h2v2h-2zm-2 2h2v2h-2zm2 2h2v2h-2zm2-2h2v2h-2zm0-4h2v2h-2zm2 2h2v2h-2z'
    play     = 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z'
    cast     = 'M21 3H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11z'
    key      = 'M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.690 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z'
    check    = 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.590-7.59L19 8l-9 9z'
    block    = 'M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z'
    memory   = 'M15 9H9v6h6V9zm-2 4h-2v-2h2v2zm8-2V9h-2V7c0-1.1-.9-2-2-2h-2V3h-2v2h-2V3H9v2H7c-1.1 0-2 .9-2 2v2H3v2h2v2H3v2h2v2c0 1.1.9 2 2 2h2v2h2v-2h2v2h2v-2h2c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2zm-4 6H7V7h10v10z'
    disk     = 'M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z'
    search   = 'M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z'
    swap     = 'M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z'
    tune     = 'M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z'
    pin      = 'M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z'
    headset  = 'M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z'
}

function Format-SvgNumber([double]$Value) {
    return ([math]::Round($Value, 1)).ToString([cultureinfo]::InvariantCulture)
}

function ConvertTo-SvgText([string]$Text) {
    return $Text.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;').Replace('"', '&quot;')
}

# Label lookup. Every key a diagram reads is recorded so the driver can refuse a label file that
# carries a key no picture uses - a stale key is how a translation silently stops matching its picture.
$script:LabelsUsed = [System.Collections.Generic.HashSet[string]]::new()
function Get-Label([string]$Key) {
    [void]$script:LabelsUsed.Add($Key)
    if (-not $script:Labels.Contains($Key)) {
        throw "label '$Key' is missing from labels.$($script:Lang).json"
    }
    return [string]$script:Labels[$Key]
}

# Multi-line text: a label's "\n" becomes one tspan per line, the block centred on Y when -Middle.
function New-SvgText {
    param(
        [double]$X, [double]$Y, [string]$Key, [string]$Raw, [double]$Size = 20, [string]$Fill = '#fff',
        [string]$Weight = '400', [ValidateSet('start', 'middle', 'end')][string]$Anchor = 'start',
        [double]$LineHeight = 1.25, [switch]$Mono, [switch]$Middle, [double]$Opacity = 1
    )
    $text = if ($Key) { Get-Label $Key } else { $Raw }
    $lines = $text -split "`n"
    $lh = $Size * $LineHeight
    $y0 = if ($Middle) { $Y - (($lines.Count - 1) * $lh) / 2 + $Size * 0.35 } else { $Y }
    $family = if ($Mono) { $script:SvgMono } else { $script:SvgFont }
    $op = if ($Opacity -lt 1) { " opacity=`"$(Format-SvgNumber $Opacity)`"" } else { '' }
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append("<text x=`"$(Format-SvgNumber $X)`" y=`"$(Format-SvgNumber $y0)`" font-family=`"$family`" font-size=`"$(Format-SvgNumber $Size)`" font-weight=`"$Weight`" fill=`"$Fill`" text-anchor=`"$Anchor`"$op>")
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $dy = if ($i -eq 0) { 0 } else { $lh }
        [void]$sb.Append("<tspan x=`"$(Format-SvgNumber $X)`" dy=`"$(Format-SvgNumber $dy)`">$(ConvertTo-SvgText $lines[$i])</tspan>")
    }
    [void]$sb.Append('</text>')
    return $sb.ToString()
}

function New-SvgIcon {
    param([string]$Name, [double]$X, [double]$Y, [double]$Size = 48, [string]$Fill = '#fff', [double]$Opacity = 1)
    $s = Format-SvgNumber ($Size / 24)
    $op = if ($Opacity -lt 1) { " opacity=`"$(Format-SvgNumber $Opacity)`"" } else { '' }
    if ($Name -eq 'windows') {
        # The four-pane window mark, drawn rather than borrowed: a vendor logo is a trademark.
        return "<g transform=`"translate($(Format-SvgNumber $X) $(Format-SvgNumber $Y)) scale($s)`" fill=`"$Fill`"$op><rect x=`"2`" y=`"2`" width=`"9.5`" height=`"9.5`" rx=`"1`"/><rect x=`"12.5`" y=`"2`" width=`"9.5`" height=`"9.5`" rx=`"1`"/><rect x=`"2`" y=`"12.5`" width=`"9.5`" height=`"9.5`" rx=`"1`"/><rect x=`"12.5`" y=`"12.5`" width=`"9.5`" height=`"9.5`" rx=`"1`"/></g>"
    }
    if (-not $script:IconPaths.ContainsKey($Name)) { throw "unknown icon '$Name'" }
    return "<g transform=`"translate($(Format-SvgNumber $X) $(Format-SvgNumber $Y)) scale($s)`"$op><path d=`"$($script:IconPaths[$Name])`" fill=`"$Fill`"/></g>"
}

function New-SvgRect {
    param(
        [double]$X, [double]$Y, [double]$W, [double]$H, [string]$Fill = 'none', [string]$Stroke = 'none',
        [double]$StrokeWidth = 1.5, [double]$Rx = 12, [string]$Dash = '', [string]$Filter = '', [double]$Opacity = 1
    )
    $d = if ($Dash) { " stroke-dasharray=`"$Dash`"" } else { '' }
    $f = if ($Filter) { " filter=`"url(#$Filter)`"" } else { '' }
    $op = if ($Opacity -lt 1) { " opacity=`"$(Format-SvgNumber $Opacity)`"" } else { '' }
    return "<rect x=`"$(Format-SvgNumber $X)`" y=`"$(Format-SvgNumber $Y)`" width=`"$(Format-SvgNumber $W)`" height=`"$(Format-SvgNumber $H)`" rx=`"$(Format-SvgNumber $Rx)`" fill=`"$Fill`" stroke=`"$Stroke`" stroke-width=`"$(Format-SvgNumber $StrokeWidth)`"$d$f$op/>"
}

function New-SvgCircle {
    param([double]$X, [double]$Y, [double]$R, [string]$Fill = 'none', [string]$Stroke = 'none', [double]$StrokeWidth = 2, [double]$Opacity = 1, [string]$Filter = '')
    $op = if ($Opacity -lt 1) { " opacity=`"$(Format-SvgNumber $Opacity)`"" } else { '' }
    $f = if ($Filter) { " filter=`"url(#$Filter)`"" } else { '' }
    return "<circle cx=`"$(Format-SvgNumber $X)`" cy=`"$(Format-SvgNumber $Y)`" r=`"$(Format-SvgNumber $R)`" fill=`"$Fill`" stroke=`"$Stroke`" stroke-width=`"$(Format-SvgNumber $StrokeWidth)`"$op$f/>"
}

# A connector through any number of points; -Curve bends a two-point line into a quadratic arc.
# Marker ids come from New-SvgDefs, one per palette colour, so the arrowhead always matches its line.
function New-SvgLine {
    param(
        [double[]]$Points, [string]$Color = 'control', [double]$Width = 2.5, [string]$Dash = '',
        [ValidateSet('end', 'start', 'both', 'none')][string]$Arrow = 'end', [double]$Curve = 0, [double]$Opacity = 1
    )
    $p = $script:Palette
    $stroke = if ($p.ContainsKey($Color)) { $p[$Color] } else { $Color }
    $mid = if ($p.ContainsKey($Color)) { $Color } else { 'muted' }
    if ($Points.Count -eq 4 -and $Curve -ne 0) {
        $mx = ($Points[0] + $Points[2]) / 2; $my = ($Points[1] + $Points[3]) / 2
        $dx = $Points[2] - $Points[0]; $dy = $Points[3] - $Points[1]
        $len = [math]::Sqrt($dx * $dx + $dy * $dy); if ($len -eq 0) { $len = 1 }
        $cx = $mx - $dy / $len * $Curve; $cy = $my + $dx / $len * $Curve
        $d = "M$(Format-SvgNumber $Points[0]) $(Format-SvgNumber $Points[1]) Q$(Format-SvgNumber $cx) $(Format-SvgNumber $cy) $(Format-SvgNumber $Points[2]) $(Format-SvgNumber $Points[3])"
    }
    else {
        $parts = for ($i = 0; $i -lt $Points.Count; $i += 2) {
            $cmd = if ($i -eq 0) { 'M' } else { 'L' }
            "$cmd$(Format-SvgNumber $Points[$i]) $(Format-SvgNumber $Points[$i + 1])"
        }
        $d = $parts -join ' '
    }
    $dashAttr = if ($Dash) { " stroke-dasharray=`"$Dash`"" } else { '' }
    $ms = if ($Arrow -in 'start', 'both') { " marker-start=`"url(#arr-$mid-s)`"" } else { '' }
    $me = if ($Arrow -in 'end', 'both') { " marker-end=`"url(#arr-$mid)`"" } else { '' }
    $op = if ($Opacity -lt 1) { " opacity=`"$(Format-SvgNumber $Opacity)`"" } else { '' }
    return "<path d=`"$d`" fill=`"none`" stroke=`"$stroke`" stroke-width=`"$(Format-SvgNumber $Width)`" stroke-linecap=`"round`" stroke-linejoin=`"round`"$dashAttr$ms$me$op/>"
}

function New-SvgDefs([string]$Style) {
    $p = $script:Palette
    $sb = [System.Text.StringBuilder]::new('<defs>')
    foreach ($name in 'control', 'tunnel', 'media', 'lan', 'danger', 'server', 'muted', 'text') {
        $c = $p[$name]
        [void]$sb.Append("<marker id=`"arr-$name`" viewBox=`"0 0 10 10`" refX=`"8.5`" refY=`"5`" markerWidth=`"7`" markerHeight=`"7`" orient=`"auto`"><path d=`"M0 0L10 5L0 10z`" fill=`"$c`"/></marker>")
        [void]$sb.Append("<marker id=`"arr-$name-s`" viewBox=`"0 0 10 10`" refX=`"1.5`" refY=`"5`" markerWidth=`"7`" markerHeight=`"7`" orient=`"auto`"><path d=`"M10 0L0 5L10 10z`" fill=`"$c`"/></marker>")
    }
    [void]$sb.Append('<filter id="soft" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="4" stdDeviation="8" flood-color="#000" flood-opacity="0.18"/></filter>')
    [void]$sb.Append('<filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="14"/></filter>')
    if ($Style -eq 'slide') {
        [void]$sb.Append("<linearGradient id=`"bgGrad`" x1=`"0`" y1=`"0`" x2=`"1`" y2=`"1`"><stop offset=`"0`" stop-color=`"$($p.bg)`"/><stop offset=`"1`" stop-color=`"$($p.bg2)`"/></linearGradient>")
        [void]$sb.Append("<radialGradient id=`"hub`" cx=`"0.5`" cy=`"0.5`" r=`"0.5`"><stop offset=`"0`" stop-color=`"$($p.server)`" stop-opacity=`"0.55`"/><stop offset=`"1`" stop-color=`"$($p.server)`" stop-opacity=`"0`"/></radialGradient>")
    }
    if ($Style -eq 'scheme') {
        [void]$sb.Append("<pattern id=`"grid`" width=`"40`" height=`"40`" patternUnits=`"userSpaceOnUse`"><path d=`"M40 0H0V40`" fill=`"none`" stroke=`"$($p.grid)`" stroke-width=`"1`"/></pattern>")
    }
    [void]$sb.Append('</defs>')
    return $sb.ToString()
}

# The brand's waves-and-particles motif as a still frame (contract WAVE-PARTICLES describes the motion;
# a static picture keeps only the shape). Seeded, so a re-render is byte-identical and -Check stays green.
function New-SvgWaves {
    param([double]$W, [double]$H, [double]$Base, [int]$Seed = 7, [double]$Amp = 38)
    $p = $script:Palette
    $rnd = [System.Random]::new($Seed)
    $sb = [System.Text.StringBuilder]::new('<g aria-hidden="true">')
    $colors = @($p.server, $p.control, $p.media)
    for ($k = 0; $k -lt 3; $k++) {
        $phase = $k * 1.7; $freq = 0.0045 + $k * 0.0012; $a = $Amp * (1 - $k * 0.22)
        $pts = for ($x = 0; $x -le $W; $x += 16) {
            $y = $Base + $k * 26 + [math]::Sin($x * $freq + $phase) * $a
            "$(Format-SvgNumber $x) $(Format-SvgNumber $y)"
        }
        [void]$sb.Append("<polyline points=`"$($pts -join ' ')`" fill=`"none`" stroke=`"$($colors[$k])`" stroke-width=`"2`" opacity=`"$(Format-SvgNumber (0.32 - $k * 0.07))`"/>")
    }
    for ($i = 0; $i -lt 46; $i++) {
        $x = $rnd.NextDouble() * $W
        $y = $Base - 90 + $rnd.NextDouble() * 220
        $r = 1 + $rnd.NextDouble() * 2.6
        $o = 0.15 + $rnd.NextDouble() * 0.45
        [void]$sb.Append((New-SvgCircle -X $x -Y $y -R $r -Fill $p.server -Opacity $o))
    }
    [void]$sb.Append('</g>')
    return $sb.ToString()
}

# Device node: an icon plate with a title and an optional subtitle under it, used by all three styles.
function New-SvgNode {
    param(
        [double]$X, [double]$Y, [string]$Icon, [string]$Title, [string]$Sub = '', [string]$Accent = 'server',
        [double]$W = 200, [double]$H = 150, [double]$IconSize = 46, [double]$TitleSize = 18, [double]$SubSize = 14,
        [string]$Badge = ''
    )
    $p = $script:Palette
    $ac = $p[$Accent]
    $sb = [System.Text.StringBuilder]::new('<g>')
    $filter = if ($script:Style -eq 'simple') { 'soft' } else { '' }
    [void]$sb.Append((New-SvgRect -X $X -Y $Y -W $W -H $H -Fill $p.card -Stroke $p.panelStroke -Rx 16 -Filter $filter))
    [void]$sb.Append((New-SvgRect -X $X -Y $Y -W $W -H 6 -Fill $ac -Rx 3))
    [void]$sb.Append((New-SvgIcon -Name $Icon -X ($X + $W / 2 - $IconSize / 2) -Y ($Y + 18) -Size $IconSize -Fill $ac))
    if ($Badge) {
        [void]$sb.Append((New-SvgCircle -X ($X + $W / 2 + $IconSize / 2 + 2) -Y ($Y + 22) -R 15 -Fill $p.card -Stroke $ac -StrokeWidth 2))
        [void]$sb.Append((New-SvgIcon -Name $Badge -X ($X + $W / 2 + $IconSize / 2 - 9) -Y ($Y + 13) -Size 18 -Fill $ac))
    }
    $ty = $Y + 18 + $IconSize + $TitleSize + 6
    [void]$sb.Append((New-SvgText -X ($X + $W / 2) -Y $ty -Key $Title -Size $TitleSize -Fill $p.text -Weight '600' -Anchor middle))
    if ($Sub) {
        $titleLines = (Get-Label $Title).Split("`n").Count
        [void]$sb.Append((New-SvgText -X ($X + $W / 2) -Y ($ty + $TitleSize * 1.25 * ($titleLines - 1) + $SubSize + 8) -Key $Sub -Size $SubSize -Fill $p.muted -Anchor middle))
    }
    [void]$sb.Append('</g>')
    return $sb.ToString()
}

# Legend row: a short line sample in the connector's own colour and dash, then its meaning.
function New-SvgLegendItem {
    param([double]$X, [double]$Y, [string]$Color, [string]$Dash = '', [string]$Key, [double]$Size = 15)
    $p = $script:Palette
    $line = New-SvgLine -Points @($X, $Y, ($X + 54), $Y) -Color $Color -Dash $Dash -Width 3
    $label = New-SvgText -X ($X + 66) -Y ($Y + $Size * 0.35) -Key $Key -Size $Size -Fill $p.text
    return $line + $label
}

# Numbered step badge for the step-by-step slides.
function New-SvgStepBadge {
    param([double]$X, [double]$Y, [int]$Number, [string]$Accent = 'server', [double]$R = 22)
    $p = $script:Palette
    $c = New-SvgCircle -X $X -Y $Y -R $R -Fill $p[$Accent]
    $t = New-SvgText -X $X -Y ($Y + $R * 0.36) -Raw "$Number" -Size ($R * 1.05) -Fill $p.bg -Weight '700' -Anchor middle
    return $c + $t
}

# Opening and closing of one picture. Title and description are localized for screen readers.
function Open-SvgDocument {
    param([double]$W, [double]$H, [string]$TitleKey, [string]$DescKey)
    $p = $script:Palette
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append("<svg xmlns=`"http://www.w3.org/2000/svg`" viewBox=`"0 0 $W $H`" width=`"$W`" height=`"$H`" role=`"img`" aria-labelledby=`"t d`" lang=`"$($script:Lang)`">")
    [void]$sb.Append("<title id=`"t`">$(ConvertTo-SvgText (Get-Label $TitleKey))</title>")
    [void]$sb.Append("<desc id=`"d`">$(ConvertTo-SvgText (Get-Label $DescKey))</desc>")
    [void]$sb.Append((New-SvgDefs $script:Style))
    switch ($script:Style) {
        'slide' { [void]$sb.Append("<rect width=`"$W`" height=`"$H`" fill=`"url(#bgGrad)`"/>") }
        'scheme' {
            [void]$sb.Append("<rect width=`"$W`" height=`"$H`" fill=`"$($p.bg)`"/>")
            [void]$sb.Append("<rect width=`"$W`" height=`"$H`" fill=`"url(#grid)`"/>")
        }
        default { [void]$sb.Append("<rect width=`"$W`" height=`"$H`" fill=`"$($p.bg)`"/>") }
    }
    return $sb
}

function Use-SvgStyle([string]$Style) {
    $script:Style = $Style
    $script:Palette = $script:Palettes[$Style]
}
