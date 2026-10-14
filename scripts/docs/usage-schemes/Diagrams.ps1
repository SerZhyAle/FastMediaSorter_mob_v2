# Diagrams - the usage-scheme illustrations (S4140), one function per picture.
# Dot-sourced by scripts/docs/render-usage-schemes.ps1 after SvgKit.ps1.
#
# Facts drawn here come from contracts DEVICE-EXCHANGE, ANYWHERE-ACCESS and LIVE-BROADCAST and from the
# shipped records in docs/ALL_FEATURES.jsonl. Windows broadcasting is drawn as present by the owner's
# decision recorded in PLAN/S4140. Every address is from a documentation range (RFC 5737) or the
# private 192.168.1.0/24 example, never a real host.

Set-StrictMode -Version Latest

# A panel with a header (icon, title, optional mono line) and icon rows, used for the server and router.
function New-SvgPanel {
    param(
        [double]$X, [double]$Y, [double]$W, [double]$H, [string]$Icon, [string]$Title, [string]$MonoRaw = '',
        [object[]]$Rows = @(), [string]$Accent = 'server', [double]$RowStep = 40, [double]$RowSize = 15,
        [double]$RowTop = 0
    )
    $p = $script:Palette
    $ac = $p[$Accent]
    $filter = if ($script:Style -eq 'simple') { 'soft' } else { '' }
    $sb = [System.Text.StringBuilder]::new('<g>')
    [void]$sb.Append((New-SvgRect -X $X -Y $Y -W $W -H $H -Fill $p.panel -Stroke $ac -StrokeWidth 2 -Rx 18 -Filter $filter))
    [void]$sb.Append((New-SvgIcon -Name $Icon -X ($X + 20) -Y ($Y + 18) -Size 38 -Fill $ac))
    [void]$sb.Append((New-SvgText -X ($X + 70) -Y ($Y + 44) -Key $Title -Size 22 -Fill $p.text -Weight '700'))
    if ($MonoRaw) {
        [void]$sb.Append((New-SvgText -X ($X + $W - 20) -Y ($Y + 44) -Raw $MonoRaw -Size 15 -Fill $ac -Anchor end -Mono))
    }
    [void]$sb.Append("<line x1=`"$(Format-SvgNumber ($X + 16))`" y1=`"$(Format-SvgNumber ($Y + 66))`" x2=`"$(Format-SvgNumber ($X + $W - 16))`" y2=`"$(Format-SvgNumber ($Y + 66))`" stroke=`"$($p.panelStroke)`" stroke-width=`"1`"/>")
    $ry = if ($RowTop -gt 0) { $RowTop } else { $Y + 100 }
    foreach ($row in $Rows) {
        if ($row.ContainsKey('monoKey')) {
            [void]$sb.Append((New-SvgText -X ($X + 30) -Y $ry -Key $row.monoKey -Size $RowSize -Fill $p.text -Mono))
        }
        elseif ($row.ContainsKey('mono')) {
            [void]$sb.Append((New-SvgText -X ($X + 30) -Y $ry -Raw $row.mono -Size $RowSize -Fill $p.text -Mono))
        }
        else {
            if ($row.icon) { [void]$sb.Append((New-SvgIcon -Name $row.icon -X ($X + 22) -Y ($ry - 17) -Size 22 -Fill $ac)) }
            [void]$sb.Append((New-SvgText -X ($X + 56) -Y $ry -Key $row.key -Size $RowSize -Fill $p.text))
        }
        $ry += $RowStep
    }
    [void]$sb.Append('</g>')
    return $sb.ToString()
}

# A labelled background zone (a network, a group of devices).
function New-SvgZone {
    param(
        [double]$X, [double]$Y, [double]$W, [double]$H, [string]$Key, [string]$Icon = '', [string]$Accent = 'muted',
        [string]$Dash = '8 6', [switch]$LabelBottom
    )
    $p = $script:Palette
    $sb = [System.Text.StringBuilder]::new('<g>')
    [void]$sb.Append((New-SvgRect -X $X -Y $Y -W $W -H $H -Fill $p.zone -Stroke $p[$Accent] -StrokeWidth 1.5 -Rx 20 -Dash $Dash))
    # Bottom label for zones whose connectors leave through the top edge and would strike the text.
    $ly = if ($LabelBottom) { $Y + $H - 40 } else { $Y }
    $tx = $X + 20
    if ($Icon) { [void]$sb.Append((New-SvgIcon -Name $Icon -X ($X + 16) -Y ($ly + 12) -Size 24 -Fill $p[$Accent])); $tx = $X + 48 }
    [void]$sb.Append((New-SvgText -X $tx -Y ($ly + 31) -Key $Key -Size 15 -Fill $p[$Accent] -Weight '600'))
    [void]$sb.Append('</g>')
    return $sb.ToString()
}

function New-SvgHeading {
    param([string]$TitleKey, [string]$SubKey, [double]$X = 60, [double]$Y = 78, [double]$TitleSize = 38, [double]$SubSize = 19)
    $p = $script:Palette
    $t = New-SvgText -X $X -Y $Y -Key $TitleKey -Size $TitleSize -Fill $p.text -Weight '700'
    $s = New-SvgText -X $X -Y ($Y + $TitleSize * 0.6 + $SubSize + 8) -Key $SubKey -Size $SubSize -Fill $p.muted
    return $t + $s
}

# Big round icon badge for presentation slides.
function New-SvgOrb {
    param([double]$X, [double]$Y, [double]$R, [string]$Icon, [string]$Accent = 'server', [double]$IconScale = 0.9)
    $p = $script:Palette
    $sb = [System.Text.StringBuilder]::new('<g>')
    [void]$sb.Append((New-SvgCircle -X $X -Y $Y -R ($R + 18) -Fill $p[$Accent] -Opacity 0.12))
    [void]$sb.Append((New-SvgCircle -X $X -Y $Y -R $R -Fill $p.card -Stroke $p[$Accent] -StrokeWidth 3))
    $is = $R * $IconScale
    [void]$sb.Append((New-SvgIcon -Name $Icon -X ($X - $is / 2) -Y ($Y - $is / 2) -Size $is -Fill $p[$Accent]))
    [void]$sb.Append('</g>')
    return $sb.ToString()
}

function New-SvgBullet {
    param([double]$X, [double]$Y, [string]$Key, [string]$Accent = 'server', [double]$Size = 22)
    $p = $script:Palette
    return (New-SvgCircle -X $X -Y ($Y - $Size * 0.33) -R ($Size * 0.22) -Fill $p[$Accent]) + (New-SvgText -X ($X + $Size * 0.8) -Y $Y -Key $Key -Size $Size -Fill $p.text)
}

# ---------------------------------------------------------------------------------------------------
# Scheme 1 - access from anywhere through the exchange server
# ---------------------------------------------------------------------------------------------------

function Build-ExchangeSimple {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1600 -H 900 -TitleKey 'ex.simple.title' -DescKey 'ex.simple.desc'
    [void]$sb.Append((New-SvgHeading -TitleKey 'ex.simple.title' -SubKey 'ex.simple.sub'))
    $left = @(
        @{ y = 200; icon = 'desktop'; badge = 'folder'; t = 'dev.homepc'; s = 'ex.simple.pc.sub'; c = 'tunnel' }
        @{ y = 405; icon = 'desktop'; badge = 'folder'; t = 'dev.officepc'; s = 'ex.simple.pc.sub'; c = 'tunnel' }
        @{ y = 610; icon = 'laptop'; badge = ''; t = 'dev.laptop'; s = 'ex.simple.laptop.sub'; c = 'control' }
    )
    $right = @(
        @{ y = 250; icon = 'phone'; badge = ''; t = 'dev.phone'; s = 'ex.simple.phone.sub'; c = 'control' }
        @{ y = 500; icon = 'phone'; badge = 'folder'; t = 'dev.phoneserver'; s = 'ex.simple.phonesrv.sub'; c = 'tunnel' }
    )
    foreach ($n in $left) {
        [void]$sb.Append((New-SvgLine -Points @(360, ($n.y + 85), 650, 435) -Color $n.c -Width 4 -Arrow both -Curve 30))
    }
    foreach ($n in $right) {
        [void]$sb.Append((New-SvgLine -Points @(1240, ($n.y + 85), 950, 435) -Color $n.c -Width 4 -Arrow both -Curve -30))
    }
    [void]$sb.Append((New-SvgLine -Points @(670, 660, 760, 545) -Color 'media' -Width 4))
    [void]$sb.Append((New-SvgLine -Points @(930, 660, 840, 545) -Color 'media' -Width 4))
    [void]$sb.Append((New-SvgNode -X 650 -Y 330 -W 300 -H 210 -Icon 'cloud' -IconSize 70 -Title 'node.server' -Sub 'ex.simple.server.sub' -Accent 'server' -TitleSize 24 -SubSize 15 -Badge 'log'))
    foreach ($n in $left) {
        [void]$sb.Append((New-SvgNode -X 100 -Y $n.y -W 260 -H 170 -Icon $n.icon -Badge $n.badge -Title $n.t -Sub $n.s -Accent $n.c -TitleSize 20 -SubSize 15))
    }
    foreach ($n in $right) {
        [void]$sb.Append((New-SvgNode -X 1240 -Y $n.y -W 260 -H 170 -Icon $n.icon -Badge $n.badge -Title $n.t -Sub $n.s -Accent $n.c -TitleSize 20 -SubSize 15))
    }
    [void]$sb.Append((New-SvgNode -X 570 -Y 660 -W 200 -H 160 -Icon 'watch' -Title 'dev.watch' -Sub 'ex.simple.recv.sub' -Accent 'media' -TitleSize 20 -SubSize 15))
    [void]$sb.Append((New-SvgNode -X 830 -Y 660 -W 200 -H 160 -Icon 'tv' -Title 'dev.tv' -Sub 'ex.simple.recv.sub' -Accent 'media' -TitleSize 20 -SubSize 15))
    [void]$sb.Append((New-SvgText -X 800 -Y 868 -Key 'ex.simple.foot' -Size 18 -Fill $p.muted -Anchor middle))
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Build-ExchangeScheme {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1600 -H 940 -TitleKey 'ex.scheme.title' -DescKey 'ex.scheme.desc'
    [void]$sb.Append((New-SvgHeading -TitleKey 'ex.scheme.title' -SubKey 'ex.scheme.sub' -X 40 -Y 56 -TitleSize 30 -SubSize 16))

    # How-it-works panel, left of the server.
    [void]$sb.Append((New-SvgRect -X 40 -Y 120 -W 480 -H 300 -Fill $p.panel -Stroke $p.panelStroke -Rx 16))
    [void]$sb.Append((New-SvgText -X 60 -Y 154 -Key 'ex.how.title' -Size 18 -Fill $p.text -Weight '700'))
    $hy = 196
    for ($i = 1; $i -le 4; $i++) {
        [void]$sb.Append((New-SvgStepBadge -X 72 -Y ($hy - 6) -Number $i -R 13))
        [void]$sb.Append((New-SvgText -X 98 -Y $hy -Key "ex.how.$i" -Size 14.5 -Fill $p.text -LineHeight 1.3))
        $hy += 56
    }

    $rows = @(
        @{ icon = 'person'; key = 'srv.accounts' }
        @{ icon = 'tune'; key = 'srv.devices' }
        @{ icon = 'folder'; key = 'srv.directory' }
        @{ icon = 'log'; key = 'srv.log' }
        @{ icon = 'memory'; key = 'srv.tunnels' }
    )
    [void]$sb.Append((New-SvgPanel -X 560 -Y 120 -W 480 -H 300 -Icon 'server' -Title 'node.server' -MonoRaw '198.51.100.7:443' -Rows $rows -RowStep 42 -RowSize 15.5 -RowTop 214))

    # Legend panel, right of the server.
    [void]$sb.Append((New-SvgRect -X 1080 -Y 120 -W 480 -H 300 -Fill $p.panel -Stroke $p.panelStroke -Rx 16))
    [void]$sb.Append((New-SvgText -X 1100 -Y 154 -Key 'legend' -Size 18 -Fill $p.text -Weight '700'))
    [void]$sb.Append((New-SvgLegendItem -X 1100 -Y 196 -Color 'control' -Dash '7 6' -Key 'lg.control' -Size 14.5))
    [void]$sb.Append((New-SvgLegendItem -X 1100 -Y 236 -Color 'tunnel' -Key 'lg.tunnel' -Size 14.5))
    [void]$sb.Append((New-SvgLegendItem -X 1100 -Y 276 -Color 'media' -Key 'lg.media' -Size 14.5))
    [void]$sb.Append((New-SvgIcon -Name 'shield' -X 1100 -Y 306 -Size 22 -Fill $p.tunnel))
    [void]$sb.Append((New-SvgText -X 1134 -Y 323 -Key 'lg.note.dialout' -Size 14.5 -Fill $p.text))
    [void]$sb.Append((New-SvgIcon -Name 'disk' -X 1100 -Y 350 -Size 22 -Fill $p.tunnel))
    [void]$sb.Append((New-SvgText -X 1134 -Y 367 -Key 'lg.note.nodisk' -Size 14.5 -Fill $p.text))

    # Internet band; its caption is drawn after the connectors, on a plate of its own.
    [void]$sb.Append((New-SvgRect -X 40 -Y 452 -W 1520 -H 50 -Fill $p.zone -Stroke $p.zoneStroke -Rx 25 -Dash '4 6'))

    [void]$sb.Append((New-SvgZone -X 40 -Y 530 -W 740 -H 290 -Key 'zone.windows' -Icon 'windows' -Accent 'control' -LabelBottom))
    [void]$sb.Append((New-SvgZone -X 820 -Y 530 -W 740 -H 290 -Key 'zone.android' -Icon 'phone' -Accent 'tunnel' -LabelBottom))

    $devices = @(
        @{ x = 60; w = 220; icon = 'desktop'; badge = 'folder'; t = 'dev.homepc'; s = 'ex.dev.homepc.sub'; data = 'tunnel'; anchor = 590 }
        @{ x = 300; w = 220; icon = 'desktop'; badge = 'folder'; t = 'dev.officepc'; s = 'ex.dev.officepc.sub'; data = 'tunnel'; anchor = 660 }
        @{ x = 540; w = 220; icon = 'laptop'; badge = ''; t = 'dev.laptop'; s = 'ex.dev.laptop.sub'; data = 'tunnel'; anchor = 730 }
        @{ x = 840; w = 170; icon = 'phone'; badge = ''; t = 'dev.phone'; s = 'ex.dev.phone.sub'; data = 'tunnel'; anchor = 810 }
        @{ x = 1025; w = 170; icon = 'phone'; badge = 'folder'; t = 'dev.phoneserver'; s = 'ex.dev.phonesrv.sub'; data = 'tunnel'; anchor = 880 }
        @{ x = 1210; w = 160; icon = 'watch'; badge = ''; t = 'dev.watch'; s = 'ex.dev.recv.sub'; data = 'media'; anchor = 950 }
        @{ x = 1385; w = 160; icon = 'tv'; badge = ''; t = 'dev.androidtv'; s = 'ex.dev.recv.sub'; data = 'media'; anchor = 1010 }
    )
    foreach ($d in $devices) {
        $cx = $d.x + $d.w / 2
        [void]$sb.Append((New-SvgLine -Points @(($cx - 8), 580, ($d.anchor - 8), 422) -Color 'control' -Dash '7 6' -Width 2 -Arrow none))
        $arrow = if ($d.data -eq 'media') { 'start' } else { 'both' }
        [void]$sb.Append((New-SvgLine -Points @(($cx + 8), 580, ($d.anchor + 8), 422) -Color $d.data -Width 3 -Arrow $arrow))
    }
    $caption = Get-Label 'ex.scheme.internet'
    $plateW = 64 + $caption.Length * 7.9
    [void]$sb.Append((New-SvgRect -X 52 -Y 458 -W $plateW -H 38 -Fill $p.bg -Stroke $p.zoneStroke -Rx 19))
    [void]$sb.Append((New-SvgIcon -Name 'globe' -X 64 -Y 464 -Size 26 -Fill $p.control))
    [void]$sb.Append((New-SvgText -X 100 -Y 483 -Key 'ex.scheme.internet' -Size 15 -Fill $p.control -Weight '600'))
    foreach ($d in $devices) {
        [void]$sb.Append((New-SvgNode -X $d.x -Y 580 -W $d.w -H 180 -Icon $d.icon -Badge $d.badge -Title $d.t -Sub $d.s -Accent ($(if ($d.data -eq 'media') { 'media' } elseif ($d.x -lt 800) { 'control' } else { 'tunnel' })) -TitleSize 17 -SubSize 13 -IconSize 42))
    }
    [void]$sb.Append((New-SvgText -X 40 -Y 856 -Key 'ex.scheme.foot1' -Size 15 -Fill $p.text))
    [void]$sb.Append((New-SvgText -X 40 -Y 884 -Key 'ex.scheme.foot2' -Size 15 -Fill $p.text))
    [void]$sb.Append((New-SvgText -X 40 -Y 916 -Key 'foot.addresses' -Size 13 -Fill $p.muted))
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Build-ExchangeSteps {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1920 -H 1080 -TitleKey 'ex.steps.title' -DescKey 'ex.steps.desc'
    [void]$sb.Append((New-SvgWaves -W 1920 -H 1080 -Base 930 -Seed 11))
    [void]$sb.Append((New-SvgHeading -TitleKey 'ex.steps.title' -SubKey 'ex.steps.sub' -X 100 -Y 140 -TitleSize 54 -SubSize 26))
    $icons = @('person', 'folder', 'search', 'swap', 'lock')
    $accents = @('control', 'tunnel', 'server', 'media', 'tunnel')
    $xs = @(250, 605, 960, 1315, 1670)
    for ($i = 0; $i -lt 5; $i++) {
        if ($i -lt 4) {
            [void]$sb.Append((New-SvgLine -Points @(($xs[$i] + 115), 450, ($xs[$i + 1] - 115), 450) -Color 'muted' -Width 3 -Dash '2 10'))
        }
    }
    for ($i = 0; $i -lt 5; $i++) {
        [void]$sb.Append((New-SvgOrb -X $xs[$i] -Y 450 -R 92 -Icon $icons[$i] -Accent $accents[$i] -IconScale 0.85))
        [void]$sb.Append((New-SvgStepBadge -X ($xs[$i] - 72) -Y 375 -Number ($i + 1) -Accent $accents[$i] -R 26))
        [void]$sb.Append((New-SvgText -X $xs[$i] -Y 625 -Key "ex.steps.$($i + 1).t" -Size 30 -Fill $p.text -Weight '700' -Anchor middle))
        [void]$sb.Append((New-SvgText -X $xs[$i] -Y 675 -Key "ex.steps.$($i + 1).b" -Size 21 -Fill $p.muted -Anchor middle -LineHeight 1.35))
    }
    [void]$sb.Append((New-SvgIcon -Name 'shield' -X 100 -Y 982 -Size 34 -Fill $p.tunnel))
    [void]$sb.Append((New-SvgText -X 148 -Y 1008 -Key 'ex.steps.foot' -Size 22 -Fill $p.text))
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Build-ExchangeKeeps {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1920 -H 1080 -TitleKey 'ex.keeps.title' -DescKey 'ex.keeps.desc'
    [void]$sb.Append((New-SvgWaves -W 1920 -H 1080 -Base 1010 -Seed 5 -Amp 24))
    [void]$sb.Append((New-SvgHeading -TitleKey 'ex.keeps.title' -SubKey 'ex.keeps.sub' -X 100 -Y 140 -TitleSize 54 -SubSize 26))
    $cols = @(
        @{ x = 100; icon = 'disk'; accent = 'tunnel'; key = 'disk'; n = 4 }
        @{ x = 700; icon = 'memory'; accent = 'control'; key = 'mem'; n = 3 }
        @{ x = 1300; icon = 'block'; accent = 'danger'; key = 'never'; n = 3 }
    )
    foreach ($c in $cols) {
        [void]$sb.Append((New-SvgRect -X $c.x -Y 280 -W 520 -H 660 -Fill $p.panel -Stroke $p[$c.accent] -StrokeWidth 2 -Rx 26))
        [void]$sb.Append((New-SvgRect -X $c.x -Y 280 -W 520 -H 10 -Fill $p[$c.accent] -Rx 5))
        [void]$sb.Append((New-SvgOrb -X ($c.x + 90) -Y 380 -R 46 -Icon $c.icon -Accent $c.accent -IconScale 0.95))
        [void]$sb.Append((New-SvgText -X ($c.x + 160) -Y 393 -Key "ex.keeps.$($c.key)" -Size 34 -Fill $p.text -Weight '700'))
        $y = 500
        for ($i = 1; $i -le $c.n; $i++) {
            [void]$sb.Append((New-SvgBullet -X ($c.x + 44) -Y $y -Key "ex.keeps.$($c.key).$i" -Accent $c.accent -Size 23))
            $y += 105
        }
    }
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

# ---------------------------------------------------------------------------------------------------
# Scheme 2 - live broadcast, every device
# ---------------------------------------------------------------------------------------------------

function Build-BroadcastSimple {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1600 -H 900 -TitleKey 'br.simple.title' -DescKey 'br.simple.desc'
    [void]$sb.Append((New-SvgHeading -TitleKey 'br.simple.title' -SubKey 'br.simple.sub'))
    $senders = @(
        @{ y = 200; icon = 'phone'; badge = 'camera'; t = 'dev.phone'; s = 'br.simple.phone.sub' }
        @{ y = 410; icon = 'watch'; badge = 'mic'; t = 'dev.watch'; s = 'br.simple.watch.sub' }
        @{ y = 620; icon = 'desktop'; badge = 'camera'; t = 'dev.windowspc'; s = 'br.simple.pc.sub' }
    )
    $receivers = @(
        @{ y = 175; icon = 'phone'; t = 'dev.phone' }
        @{ y = 340; icon = 'watch'; t = 'dev.watch' }
        @{ y = 505; icon = 'tv'; t = 'dev.androidtv' }
        @{ y = 670; icon = 'desktop'; t = 'dev.windowspc' }
    )
    foreach ($s in $senders) { [void]$sb.Append((New-SvgLine -Points @(360, ($s.y + 85), 672, 480) -Color 'media' -Width 4 -Curve 25)) }
    foreach ($r in $receivers) { [void]$sb.Append((New-SvgLine -Points @(928, 480, 1240, ($r.y + 65)) -Color 'media' -Width 4 -Curve 25)) }
    [void]$sb.Append((New-SvgCircle -X 800 -Y 480 -R 150 -Fill $p.media -Opacity 0.08))
    [void]$sb.Append((New-SvgCircle -X 800 -Y 480 -R 128 -Fill $p.card -Stroke $p.media -StrokeWidth 3 -Filter 'soft'))
    [void]$sb.Append((New-SvgIcon -Name 'antenna' -X 760 -Y 395 -Size 80 -Fill $p.media))
    [void]$sb.Append((New-SvgText -X 800 -Y 510 -Key 'br.simple.hub' -Size 20 -Fill $p.text -Weight '700' -Anchor middle))
    [void]$sb.Append((New-SvgText -X 800 -Y 668 -Key 'br.simple.lan' -Size 16 -Fill $p.lan -Anchor middle))
    foreach ($s in $senders) {
        [void]$sb.Append((New-SvgNode -X 100 -Y $s.y -W 260 -H 170 -Icon $s.icon -Badge $s.badge -Title $s.t -Sub $s.s -Accent 'media' -TitleSize 20 -SubSize 15))
    }
    foreach ($r in $receivers) {
        [void]$sb.Append((New-SvgNode -X 1240 -Y $r.y -W 260 -H 130 -Icon $r.icon -Title $r.t -Accent 'tunnel' -TitleSize 19 -IconSize 44))
    }
    [void]$sb.Append((New-SvgText -X 230 -Y 182 -Key 'br.simple.on.air' -Size 17 -Fill $p.media -Weight '700' -Anchor middle))
    [void]$sb.Append((New-SvgText -X 1370 -Y 158 -Key 'br.simple.listen' -Size 17 -Fill $p.tunnel -Weight '700' -Anchor middle))
    [void]$sb.Append((New-SvgText -X 800 -Y 868 -Key 'br.simple.foot' -Size 18 -Fill $p.muted -Anchor middle))
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Build-BroadcastScheme {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1600 -H 1000 -TitleKey 'br.scheme.title' -DescKey 'br.scheme.desc'
    [void]$sb.Append((New-SvgHeading -TitleKey 'br.scheme.title' -SubKey 'br.scheme.sub' -X 40 -Y 56 -TitleSize 30 -SubSize 16))
    [void]$sb.Append((New-SvgZone -X 40 -Y 130 -W 400 -H 730 -Key 'zone.broadcasters' -Icon 'antenna' -Accent 'media'))
    [void]$sb.Append((New-SvgZone -X 1160 -Y 130 -W 400 -H 730 -Key 'zone.receivers' -Icon 'play' -Accent 'tunnel'))
    $senders = @(
        @{ y = 180; icon = 'phone'; badge = 'camera'; t = 'dev.androidphone'; s = 'br.b.phone.sub' }
        @{ y = 405; icon = 'watch'; badge = 'mic'; t = 'dev.wearwatch'; s = 'br.b.watch.sub' }
        @{ y = 630; icon = 'desktop'; badge = 'camera'; t = 'dev.windowspc'; s = 'br.b.pc.sub' }
    )
    $receivers = @(
        @{ y = 180; icon = 'phone'; t = 'dev.androidphone'; s = 'br.r.phone.sub' }
        @{ y = 350; icon = 'watch'; t = 'dev.wearwatch'; s = 'br.r.watch.sub' }
        @{ y = 520; icon = 'tv'; t = 'dev.androidtv'; s = 'br.r.tv.sub' }
        @{ y = 690; icon = 'desktop'; t = 'dev.windowspc'; s = 'br.r.pc.sub' }
    )
    # LAN direct route arches over the server: same Wi-Fi means no server at all.
    [void]$sb.Append("<path d=`"M400 250 C 560 110, 1040 110, 1200 252`" fill=`"none`" stroke=`"$($p.lan)`" stroke-width=`"3`" stroke-dasharray=`"9 7`" marker-end=`"url(#arr-lan)`"/>")
    [void]$sb.Append((New-SvgText -X 800 -Y 128 -Key 'br.scheme.lan' -Size 15 -Fill $p.lan -Weight '600' -Anchor middle))
    $i = 0
    foreach ($s in $senders) {
        [void]$sb.Append((New-SvgLine -Points @(400, ($s.y + 100), 560, (360 + $i * 90)) -Color 'media' -Width 3.5))
        $i++
    }
    $i = 0
    foreach ($r in $receivers) {
        [void]$sb.Append((New-SvgLine -Points @(1040, (330 + $i * 80), 1180, ($r.y + 75)) -Color 'media' -Width 3.5))
        $i++
    }
    [void]$sb.Append((New-SvgLine -Points @(1040, 670, 1180, 640) -Color 'control' -Dash '7 6' -Width 2.5 -Curve -30))
    [void]$sb.Append((New-SvgText -X 1100 -Y 712 -Key 'br.scheme.cast' -Size 14 -Fill $p.control -Weight '600' -Anchor middle))
    $rows = @(
        @{ icon = 'antenna'; key = 'br.srv.directory' }
        @{ icon = 'swap'; key = 'br.srv.relay' }
        @{ icon = 'memory'; key = 'br.srv.buffer' }
        @{ icon = 'cast'; key = 'br.srv.cast' }
        @{ icon = 'lock'; key = 'br.srv.tunnel' }
        @{ icon = 'log'; key = 'br.srv.log' }
    )
    [void]$sb.Append((New-SvgPanel -X 560 -Y 260 -W 480 -H 440 -Icon 'server' -Title 'node.server' -MonoRaw '198.51.100.7:443' -Rows $rows -RowStep 56 -RowSize 15 -RowTop 360 -Accent 'media'))
    foreach ($s in $senders) {
        [void]$sb.Append((New-SvgNode -X 60 -Y $s.y -W 340 -H 180 -Icon $s.icon -Badge $s.badge -Title $s.t -Sub $s.s -Accent 'media' -TitleSize 18 -SubSize 13.5 -IconSize 44))
    }
    foreach ($r in $receivers) {
        [void]$sb.Append((New-SvgNode -X 1180 -Y $r.y -W 360 -H 150 -Icon $r.icon -Title $r.t -Sub $r.s -Accent 'tunnel' -TitleSize 17 -SubSize 13 -IconSize 38))
    }
    [void]$sb.Append((New-SvgLegendItem -X 40 -Y 905 -Color 'media' -Key 'lg.broadcast' -Size 14.5))
    [void]$sb.Append((New-SvgLegendItem -X 560 -Y 905 -Color 'control' -Dash '7 6' -Key 'lg.cast' -Size 14.5))
    [void]$sb.Append((New-SvgLegendItem -X 1040 -Y 905 -Color 'lan' -Dash '9 7' -Key 'lg.lan' -Size 14.5))
    [void]$sb.Append((New-SvgText -X 40 -Y 950 -Key 'br.scheme.order' -Size 15 -Fill $p.text -Weight '600'))
    [void]$sb.Append((New-SvgText -X 40 -Y 980 -Key 'foot.addresses' -Size 13 -Fill $p.muted))
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Build-BroadcastOrder {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1600 -H 900 -TitleKey 'br.order.title' -DescKey 'br.order.desc'
    [void]$sb.Append((New-SvgHeading -TitleKey 'br.order.title' -SubKey 'br.order.sub'))
    $cards = @(
        @{ y = 200; icon = 'wifi'; accent = 'lan'; k = 1 }
        @{ y = 420; icon = 'antenna'; accent = 'media'; k = 2 }
        @{ y = 640; icon = 'lock'; accent = 'control'; k = 3 }
    )
    foreach ($c in $cards) {
        [void]$sb.Append((New-SvgRect -X 200 -Y $c.y -W 1200 -H 175 -Fill $p.card -Stroke $p.panelStroke -Rx 22 -Filter 'soft'))
        [void]$sb.Append((New-SvgRect -X 200 -Y $c.y -W 12 -H 175 -Fill $p[$c.accent] -Rx 6))
        [void]$sb.Append((New-SvgStepBadge -X 270 -Y ($c.y + 87) -Number $c.k -Accent $c.accent -R 30))
        [void]$sb.Append((New-SvgIcon -Name $c.icon -X 330 -Y ($c.y + 55) -Size 64 -Fill $p[$c.accent]))
        [void]$sb.Append((New-SvgText -X 425 -Y ($c.y + 62) -Key "br.order.$($c.k).t" -Size 28 -Fill $p.text -Weight '700'))
        [void]$sb.Append((New-SvgText -X 425 -Y ($c.y + 102) -Key "br.order.$($c.k).b" -Size 18 -Fill $p.muted -LineHeight 1.35))
        [void]$sb.Append((New-SvgRect -X 1190 -Y ($c.y + 62) -W 180 -H 50 -Fill $p[$c.accent] -Rx 25 -Opacity 0.14))
        [void]$sb.Append((New-SvgText -X 1280 -Y ($c.y + 94) -Key "br.order.$($c.k).p" -Size 19 -Fill $p[$c.accent] -Weight '700' -Anchor middle))
    }
    [void]$sb.Append((New-SvgLine -Points @(140, 290, 140, 368) -Color 'muted' -Width 3 -Arrow none))
    [void]$sb.Append((New-SvgLine -Points @(140, 434, 140, 505) -Color 'muted' -Width 3))
    [void]$sb.Append((New-SvgLine -Points @(140, 510, 140, 586) -Color 'muted' -Width 3 -Arrow none))
    [void]$sb.Append((New-SvgLine -Points @(140, 652, 140, 725) -Color 'muted' -Width 3))
    [void]$sb.Append((New-SvgText -X 140 -Y 400 -Key 'br.order.fallback' -Size 14 -Fill $p.muted -Anchor middle -Middle))
    [void]$sb.Append((New-SvgText -X 140 -Y 618 -Key 'br.order.fallback' -Size 14 -Fill $p.muted -Anchor middle -Middle))
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Build-BroadcastCast {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1920 -H 1080 -TitleKey 'br.cast.title' -DescKey 'br.cast.desc'
    [void]$sb.Append((New-SvgWaves -W 1920 -H 1080 -Base 1000 -Seed 3 -Amp 22))
    [void]$sb.Append((New-SvgHeading -TitleKey 'br.cast.title' -SubKey 'br.cast.sub' -X 100 -Y 140 -TitleSize 54 -SubSize 26))
    [void]$sb.Append((New-SvgOrb -X 300 -Y 520 -R 120 -Icon 'desktop' -Accent 'media'))
    [void]$sb.Append((New-SvgOrb -X 960 -Y 520 -R 120 -Icon 'cloud' -Accent 'server'))
    [void]$sb.Append((New-SvgOrb -X 1620 -Y 520 -R 120 -Icon 'tv' -Accent 'tunnel'))
    [void]$sb.Append((New-SvgText -X 300 -Y 700 -Key 'br.cast.pc' -Size 28 -Fill $p.text -Weight '700' -Anchor middle))
    [void]$sb.Append((New-SvgText -X 960 -Y 700 -Key 'node.server' -Size 28 -Fill $p.text -Weight '700' -Anchor middle))
    [void]$sb.Append((New-SvgText -X 1620 -Y 700 -Key 'br.cast.tv' -Size 28 -Fill $p.text -Weight '700' -Anchor middle))
    # 1 upload, 2 cast request (PC -> server), 3 offer, 4 answer (server <-> TV), 5 stream (server -> TV).
    [void]$sb.Append((New-SvgLine -Points @(440, 465, 820, 465) -Color 'media' -Width 5))
    [void]$sb.Append((New-SvgLine -Points @(440, 575, 820, 575) -Color 'control' -Width 3 -Dash '10 8'))
    [void]$sb.Append((New-SvgLine -Points @(1100, 430, 1480, 430) -Color 'control' -Width 3 -Dash '10 8'))
    [void]$sb.Append((New-SvgLine -Points @(1480, 505, 1100, 505) -Color 'tunnel' -Width 3 -Dash '10 8'))
    [void]$sb.Append((New-SvgLine -Points @(1100, 600, 1480, 600) -Color 'media' -Width 5))
    $badges = @(
        @{ x = 630; y = 430; n = 1; a = 'media' }, @{ x = 630; y = 540; n = 2; a = 'control' },
        @{ x = 1290; y = 395; n = 3; a = 'control' }, @{ x = 1290; y = 470; n = 4; a = 'tunnel' },
        @{ x = 1290; y = 565; n = 5; a = 'media' }
    )
    foreach ($b in $badges) { [void]$sb.Append((New-SvgStepBadge -X $b.x -Y $b.y -Number $b.n -Accent $b.a -R 20)) }
    $y = 800
    for ($i = 1; $i -le 5; $i++) {
        $x = if ($i -le 3) { 100 } else { 1000 }
        $yy = if ($i -le 3) { $y + ($i - 1) * 62 } else { $y + ($i - 4) * 62 }
        $a = $badges[$i - 1].a
        [void]$sb.Append((New-SvgStepBadge -X ($x + 20) -Y ($yy - 8) -Number $i -Accent $a -R 20))
        [void]$sb.Append((New-SvgText -X ($x + 56) -Y $yy -Key "br.cast.$i" -Size 23 -Fill $p.text))
    }
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

# ---------------------------------------------------------------------------------------------------
# Scheme 3 - the home network, no exchange server
# ---------------------------------------------------------------------------------------------------

function Build-HomeSimple {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1600 -H 900 -TitleKey 'home.simple.title' -DescKey 'home.simple.desc'
    [void]$sb.Append((New-SvgHeading -TitleKey 'home.simple.title' -SubKey 'home.simple.sub'))
    [void]$sb.Append((New-SvgZone -X 560 -Y 180 -W 980 -H 650 -Key 'home.zone.lan' -Icon 'home' -Accent 'lan' -Dash ''))
    [void]$sb.Append((New-SvgZone -X 40 -Y 180 -W 380 -H 650 -Key 'home.zone.outside' -Icon 'globe' -Accent 'control'))
    # Away: through the router's public address. At home: straight to the PC.
    [void]$sb.Append((New-SvgLine -Points @(360, 315, 600, 485) -Color 'control' -Width 4 -Curve -20))
    [void]$sb.Append((New-SvgLine -Points @(360, 640, 600, 535) -Color 'control' -Width 4 -Curve 20))
    [void]$sb.Append((New-SvgLine -Points @(820, 470, 960, 330) -Color 'tunnel' -Width 4 -Arrow both))
    [void]$sb.Append((New-SvgLine -Points @(1080, 590, 1080, 430) -Color 'lan' -Width 4 -Dash '9 7' -Arrow both))
    [void]$sb.Append((New-SvgLine -Points @(1340, 590, 1200, 430) -Color 'lan' -Width 4 -Dash '9 7' -Arrow both))
    [void]$sb.Append((New-SvgNode -X 80 -Y 230 -W 280 -H 170 -Icon 'phone' -Badge 'cellular' -Title 'dev.phone.away' -Sub 'home.simple.away1.sub' -Accent 'control' -TitleSize 19 -SubSize 15))
    [void]$sb.Append((New-SvgNode -X 80 -Y 555 -W 280 -H 170 -Icon 'laptop' -Title 'dev.laptop.hotel' -Sub 'home.simple.away2.sub' -Accent 'control' -TitleSize 19 -SubSize 15))
    [void]$sb.Append((New-SvgNode -X 600 -Y 420 -W 220 -H 170 -Icon 'router' -Title 'dev.router' -Sub 'home.simple.router.sub' -Accent 'control' -TitleSize 20 -SubSize 15))
    [void]$sb.Append((New-SvgNode -X 960 -Y 240 -W 300 -H 190 -Icon 'desktop' -Badge 'disk' -Title 'dev.mediapc' -Sub 'home.simple.pc.sub' -Accent 'tunnel' -TitleSize 21 -SubSize 15))
    [void]$sb.Append((New-SvgNode -X 960 -Y 590 -W 240 -H 170 -Icon 'phone' -Badge 'wifi' -Title 'dev.phone.home' -Sub 'home.simple.lan.sub' -Accent 'lan' -TitleSize 19 -SubSize 15))
    [void]$sb.Append((New-SvgNode -X 1240 -Y 590 -W 240 -H 170 -Icon 'tv' -Badge 'wifi' -Title 'dev.androidtv' -Sub 'home.simple.lan.sub' -Accent 'lan' -TitleSize 19 -SubSize 15))
    [void]$sb.Append((New-SvgText -X 800 -Y 868 -Key 'home.simple.foot' -Size 18 -Fill $p.muted -Anchor middle))
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Build-HomeScheme {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1600 -H 1000 -TitleKey 'home.scheme.title' -DescKey 'home.scheme.desc'
    [void]$sb.Append((New-SvgHeading -TitleKey 'home.scheme.title' -SubKey 'home.scheme.sub' -X 40 -Y 56 -TitleSize 30 -SubSize 16))
    [void]$sb.Append((New-SvgZone -X 40 -Y 130 -W 340 -H 740 -Key 'home.zone.outside' -Icon 'globe' -Accent 'control'))
    [void]$sb.Append((New-SvgZone -X 820 -Y 130 -W 740 -H 740 -Key 'home.zone.lan.cidr' -Icon 'home' -Accent 'lan'))

    # Outside -> router WAN.
    [void]$sb.Append((New-SvgLine -Points @(350, 285, 430, 420) -Color 'control' -Width 3.5))
    [void]$sb.Append((New-SvgLine -Points @(350, 505, 430, 470) -Color 'control' -Width 3.5))
    # Router -> forwarded servers.
    [void]$sb.Append((New-SvgLine -Points @(760, 470, 850, 300) -Color 'tunnel' -Width 3.5 -Arrow both))
    [void]$sb.Append((New-SvgLine -Points @(770, 520, 1240, 407) -Color 'tunnel' -Width 3.5 -Arrow both))
    # Inside clients -> servers, straight over the LAN.
    [void]$sb.Append((New-SvgLine -Points @(955, 590, 990, 405) -Color 'lan' -Width 3 -Dash '9 7' -Arrow both))
    [void]$sb.Append((New-SvgLine -Points @(1185, 590, 1060, 405) -Color 'lan' -Width 3 -Dash '9 7' -Arrow both))
    [void]$sb.Append((New-SvgLine -Points @(1420, 590, 1380, 405) -Color 'lan' -Width 3 -Dash '9 7' -Arrow both))

    [void]$sb.Append((New-SvgNode -X 60 -Y 180 -W 290 -H 200 -Icon 'phone' -Badge 'cellular' -Title 'dev.phone.away' -Sub 'home.scheme.away1.sub' -Accent 'control' -TitleSize 17 -SubSize 13))
    [void]$sb.Append((New-SvgNode -X 60 -Y 405 -W 290 -H 200 -Icon 'laptop' -Title 'dev.laptop.hotel' -Sub 'home.scheme.away2.sub' -Accent 'control' -TitleSize 17 -SubSize 13))
    [void]$sb.Append((New-SvgNode -X 60 -Y 630 -W 290 -H 210 -Icon 'pin' -Title 'home.public.title' -Sub 'home.public.sub' -Accent 'media' -TitleSize 17 -SubSize 13))

    $rows = @(
        @{ mono = 'WAN  203.0.113.25' }
        @{ key = 'home.r.forward'; icon = 'swap' }
        @{ mono = 'TCP 2222 -> 192.168.1.20:2222' }
        @{ mono = 'TCP 2223 -> 192.168.1.42:2222' }
        @{ key = 'home.r.dhcp'; icon = 'pin' }
        @{ monoKey = 'home.r.pc' }
        @{ monoKey = 'home.r.phone' }
    )
    [void]$sb.Append((New-SvgPanel -X 410 -Y 300 -W 360 -H 400 -Icon 'router' -Title 'dev.router' -Rows $rows -RowStep 42 -RowSize 14.5 -RowTop 400 -Accent 'control'))

    [void]$sb.Append((New-SvgNode -X 850 -Y 180 -W 330 -H 225 -Icon 'desktop' -Badge 'disk' -Title 'dev.mediapc' -Sub 'home.scheme.pc.sub' -Accent 'tunnel' -TitleSize 18 -SubSize 13.5))
    [void]$sb.Append((New-SvgNode -X 1210 -Y 180 -W 330 -H 225 -Icon 'phone' -Badge 'folder' -Title 'dev.phoneserver' -Sub 'home.scheme.phonesrv.sub' -Accent 'tunnel' -TitleSize 18 -SubSize 13.5))
    [void]$sb.Append((New-SvgNode -X 850 -Y 590 -W 210 -H 210 -Icon 'phone' -Badge 'wifi' -Title 'dev.phone.home' -Sub 'home.scheme.lan1.sub' -Accent 'lan' -TitleSize 17 -SubSize 13))
    [void]$sb.Append((New-SvgNode -X 1080 -Y 590 -W 210 -H 210 -Icon 'tv' -Badge 'wifi' -Title 'dev.androidtv' -Sub 'home.scheme.lan2.sub' -Accent 'lan' -TitleSize 17 -SubSize 13))
    [void]$sb.Append((New-SvgNode -X 1310 -Y 590 -W 230 -H 210 -Icon 'laptop' -Badge 'wifi' -Title 'dev.windowslaptop' -Sub 'home.scheme.lan3.sub' -Accent 'lan' -TitleSize 17 -SubSize 13))
    [void]$sb.Append((New-SvgText -X 850 -Y 838 -Key 'home.scheme.lanlabel' -Size 14 -Fill $p.lan -Weight '600'))

    [void]$sb.Append((New-SvgLegendItem -X 40 -Y 905 -Color 'control' -Key 'lg.internet' -Size 14.5))
    [void]$sb.Append((New-SvgLegendItem -X 560 -Y 905 -Color 'tunnel' -Key 'lg.forwarded' -Size 14.5))
    [void]$sb.Append((New-SvgLegendItem -X 1040 -Y 905 -Color 'lan' -Dash '9 7' -Key 'lg.lan.direct' -Size 14.5))
    [void]$sb.Append((New-SvgIcon -Name 'swap' -X 40 -Y 930 -Size 22 -Fill $p.tunnel))
    [void]$sb.Append((New-SvgText -X 72 -Y 947 -Key 'home.note.order' -Size 14.5 -Fill $p.text))
    [void]$sb.Append((New-SvgIcon -Name 'block' -X 40 -Y 960 -Size 22 -Fill $p.danger))
    [void]$sb.Append((New-SvgText -X 72 -Y 977 -Key 'home.note.cgnat' -Size 14.5 -Fill $p.danger))
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Build-HomeSteps {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1920 -H 1080 -TitleKey 'home.steps.title' -DescKey 'home.steps.desc'
    [void]$sb.Append((New-SvgWaves -W 1920 -H 1080 -Base 1030 -Seed 9 -Amp 20))
    [void]$sb.Append((New-SvgHeading -TitleKey 'home.steps.title' -SubKey 'home.steps.sub' -X 100 -Y 140 -TitleSize 54 -SubSize 26))
    $icons = @('pin', 'server', 'router', 'globe', 'qr', 'check')
    $accents = @('lan', 'tunnel', 'control', 'media', 'server', 'tunnel')
    for ($i = 0; $i -lt 6; $i++) {
        $col = $i % 3; $row = [math]::Floor($i / 3)
        $x = 100 + $col * 580; $y = 280 + $row * 360
        $a = $accents[$i]
        [void]$sb.Append((New-SvgRect -X $x -Y $y -W 560 -H 330 -Fill $p.panel -Stroke $p.panelStroke -StrokeWidth 1.5 -Rx 24))
        [void]$sb.Append((New-SvgRect -X $x -Y $y -W 560 -H 8 -Fill $p[$a] -Rx 4))
        [void]$sb.Append((New-SvgStepBadge -X ($x + 54) -Y ($y + 70) -Number ($i + 1) -Accent $a -R 28))
        [void]$sb.Append((New-SvgIcon -Name $icons[$i] -X ($x + 470) -Y ($y + 36) -Size 58 -Fill $p[$a] -Opacity 0.9))
        [void]$sb.Append((New-SvgText -X ($x + 40) -Y ($y + 150) -Key "home.steps.$($i + 1).t" -Size 30 -Fill $p.text -Weight '700'))
        [void]$sb.Append((New-SvgText -X ($x + 40) -Y ($y + 200) -Key "home.steps.$($i + 1).b" -Size 21 -Fill $p.muted -LineHeight 1.4))
    }
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

# One pairing code carries every address of the PC (Windows Share Manager): the local one, an IPv6 one and
# the internet one. The phone tries them in parallel and keeps the fastest that answers. Addresses are
# documentation ranges only (RFC 5737 / RFC 3849, example.net).
function Build-HomePairing {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1920 -H 1080 -TitleKey 'home.pair.title' -DescKey 'home.pair.desc'
    [void]$sb.Append((New-SvgWaves -W 1920 -H 1080 -Base 1030 -Seed 31 -Amp 20))
    [void]$sb.Append((New-SvgHeading -TitleKey 'home.pair.title' -SubKey 'home.pair.sub' -X 100 -Y 140 -TitleSize 54 -SubSize 26))

    $cardTop = 290; $cardH = 520; $cardW = 300
    $pcX = 80; $phoneX = 1540
    $laneX = 500; $laneW = 920; $laneH = 160
    $lanes = @(
        @{ n = 1; top = 290; icon = 'wifi'; a = 'lan'; addr = '192.168.1.20:2222' }
        @{ n = 2; top = 470; icon = 'globe'; a = 'server'; addr = '2001:db8:25::20' }
        @{ n = 3; top = 650; icon = 'router'; a = 'control'; addr = 'myhome.example.net:2222' }
    )

    [void]$sb.Append((New-SvgText -X ($laneX + $laneW / 2) -Y 272 -Key 'home.pair.lanes' -Size 21 -Fill $p.muted -Anchor middle))

    # The PC (left) and the phone (right): an orb, a name, what it holds, and the code between them.
    foreach ($side in @(
            @{ x = $pcX; a = 'tunnel'; icon = 'desktop'; t = 'home.pair.pc.t'; end = 'pc' }
            @{ x = $phoneX; a = 'control'; icon = 'phone'; t = 'home.pair.phone.t'; end = 'phone' }
        )) {
        $x = $side.x; $cx = $x + $cardW / 2; $ac = $p[$side.a]
        [void]$sb.Append((New-SvgRect -X $x -Y $cardTop -W $cardW -H $cardH -Fill $p.panel -Stroke $p.panelStroke -StrokeWidth 1.5 -Rx 24))
        [void]$sb.Append((New-SvgRect -X $x -Y $cardTop -W $cardW -H 8 -Fill $ac -Rx 4))
        [void]$sb.Append((New-SvgOrb -X $cx -Y ($cardTop + 92) -R 56 -Icon $side.icon -Accent $side.a))
        [void]$sb.Append((New-SvgText -X $cx -Y ($cardTop + 192) -Key $side.t -Size 26 -Fill $p.text -Weight '700' -Anchor middle))
        [void]$sb.Append((New-SvgRect -X ($x + 30) -Y ($cardTop + 335) -W ($cardW - 60) -H 2 -Fill $p.panelStroke -Rx 1))
        if ($side.end -eq 'pc') {
            [void]$sb.Append((New-SvgText -X $cx -Y ($cardTop + 270) -Key 'home.pair.pc.share' -Size 24 -Fill $ac -Anchor middle -Mono -Weight '700'))
            [void]$sb.Append((New-SvgText -X $cx -Y ($cardTop + 304) -Key 'home.pair.pc.sftp' -Size 18 -Fill $p.muted -Anchor middle -Mono))
            [void]$sb.Append((New-SvgIcon -Name 'qr' -X ($cx - 40) -Y ($cardTop + 358) -Size 80 -Fill $p.text))
            [void]$sb.Append((New-SvgText -X $cx -Y ($cardTop + 470) -Key 'home.pair.pc.qr' -Size 20 -Fill $p.muted -Anchor middle))
        }
        else {
            [void]$sb.Append((New-SvgText -X $cx -Y ($cardTop + 270) -Key 'home.pair.phone.s' -Size 20 -Fill $p.muted -Anchor middle))
            [void]$sb.Append((New-SvgIcon -Name 'check' -X ($cx - 40) -Y ($cardTop + 358) -Size 80 -Fill $ac))
            [void]$sb.Append((New-SvgText -X $cx -Y ($cardTop + 470) -Key 'home.pair.phone.mem' -Size 20 -Fill $p.muted -Anchor middle))
        }
    }

    # Three lanes, tried in parallel; the phone asks on the right, the answer arrives at the PC on the left.
    foreach ($l in $lanes) {
        $cy = $l.top + $laneH / 2; $ac = $p[$l.a]
        [void]$sb.Append((New-SvgRect -X $laneX -Y $l.top -W $laneW -H $laneH -Fill $p.panel -Stroke $ac -StrokeWidth 2 -Rx 24))
        [void]$sb.Append((New-SvgRect -X $laneX -Y $l.top -W 12 -H $laneH -Fill $ac -Rx 6))
        [void]$sb.Append((New-SvgStepBadge -X ($laneX + 56) -Y $cy -Number $l.n -Accent $l.a -R 26))
        [void]$sb.Append((New-SvgIcon -Name $l.icon -X ($laneX + 104) -Y ($cy - 29) -Size 58 -Fill $ac))
        [void]$sb.Append((New-SvgText -X ($laneX + 190) -Y ($l.top + 46) -Key "home.pair.$($l.n).t" -Size 30 -Fill $p.text -Weight '700'))
        [void]$sb.Append((New-SvgText -X ($laneX + 190) -Y ($l.top + 90) -Raw $l.addr -Size 27 -Fill $ac -Mono -Weight '700'))
        [void]$sb.Append((New-SvgText -X ($laneX + 190) -Y ($l.top + 122) -Key "home.pair.$($l.n).b" -Size 20 -Fill $p.muted))
        # The three connectors land side by side on each card instead of piling on one point.
        $ay = $cardTop + $cardH / 2 + ($l.n - 2) * 44
        [void]$sb.Append((New-SvgLine -Points @(($laneX), $cy, ($pcX + $cardW), $ay) -Color $l.a -Width 3.5))
        [void]$sb.Append((New-SvgLine -Points @($phoneX, $ay, ($laneX + $laneW), $cy) -Color $l.a -Width 3.5))
    }

    [void]$sb.Append((New-SvgIcon -Name 'key' -X 100 -Y 836 -Size 36 -Fill $p.server))
    [void]$sb.Append((New-SvgText -X 152 -Y 864 -Key 'home.pair.foot' -Size 24 -Fill $p.text))
    [void]$sb.Append((New-SvgIcon -Name 'wifi' -X 104 -Y 886 -Size 28 -Fill $p.lan))
    [void]$sb.Append((New-SvgText -X 152 -Y 907 -Key 'home.pair.foot2' -Size 20 -Fill $p.muted))
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

# ---------------------------------------------------------------------------------------------------
# Overview pictures
# ---------------------------------------------------------------------------------------------------

function Build-ThreeWays {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1920 -H 1080 -TitleKey 'ov.title' -DescKey 'ov.desc'
    [void]$sb.Append((New-SvgWaves -W 1920 -H 1080 -Base 1030 -Seed 21 -Amp 20))
    [void]$sb.Append((New-SvgHeading -TitleKey 'ov.title' -SubKey 'ov.sub' -X 100 -Y 140 -TitleSize 54 -SubSize 26))
    $cards = @(
        @{ x = 100; icon = 'home'; a = 'lan'; k = 'home' }
        @{ x = 700; icon = 'cloud'; a = 'server'; k = 'exchange' }
        @{ x = 1300; icon = 'antenna'; a = 'media'; k = 'broadcast' }
    )
    foreach ($c in $cards) {
        [void]$sb.Append((New-SvgRect -X $c.x -Y 270 -W 520 -H 720 -Fill $p.panel -Stroke $p[$c.a] -StrokeWidth 2 -Rx 28))
        [void]$sb.Append((New-SvgOrb -X ($c.x + 260) -Y 400 -R 82 -Icon $c.icon -Accent $c.a))
        [void]$sb.Append((New-SvgText -X ($c.x + 260) -Y 555 -Key "ov.$($c.k).t" -Size 36 -Fill $p.text -Weight '700' -Anchor middle))
        [void]$sb.Append((New-SvgText -X ($c.x + 260) -Y 600 -Key "ov.$($c.k).tag" -Size 22 -Fill $p[$c.a] -Weight '600' -Anchor middle))
        $y = 680
        for ($i = 1; $i -le 4; $i++) {
            [void]$sb.Append((New-SvgBullet -X ($c.x + 44) -Y $y -Key "ov.$($c.k).$i" -Accent $c.a -Size 21))
            $y += 76
        }
    }
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

function Build-EcosystemHero {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1920 -H 1080 -TitleKey 'hero.title' -DescKey 'hero.desc'
    [void]$sb.Append((New-SvgWaves -W 1920 -H 1080 -Base 960 -Seed 13 -Amp 40))
    [void]$sb.Append((New-SvgHeading -TitleKey 'hero.title' -SubKey 'hero.sub' -X 100 -Y 130 -TitleSize 58 -SubSize 26))
    $cx = 960; $cy = 640; $rx = 640; $ry = 270
    $devices = @(
        @{ icon = 'phone'; k = 'dev.phone'; a = 'tunnel' }
        @{ icon = 'watch'; k = 'dev.watch'; a = 'media' }
        @{ icon = 'tv'; k = 'dev.tv'; a = 'media' }
        @{ icon = 'headset'; k = 'dev.headset'; a = 'lan' }
        @{ icon = 'laptop'; k = 'dev.laptop'; a = 'control' }
        @{ icon = 'desktop'; k = 'dev.windowspc'; a = 'control' }
        @{ icon = 'router'; k = 'dev.router'; a = 'lan' }
        @{ icon = 'phone'; k = 'dev.phoneserver'; a = 'tunnel' }
    )
    $n = $devices.Count
    $pos = for ($i = 0; $i -lt $n; $i++) {
        $ang = -[math]::PI / 2 + $i * 2 * [math]::PI / $n
        @{ x = $cx + [math]::Cos($ang) * $rx; y = $cy + [math]::Sin($ang) * $ry }
    }
    for ($i = 0; $i -lt $n; $i++) {
        [void]$sb.Append((New-SvgLine -Points @($cx, $cy, $pos[$i].x, $pos[$i].y) -Color $devices[$i].a -Width 2.5 -Dash '3 9' -Arrow none -Opacity 0.8))
    }
    [void]$sb.Append((New-SvgCircle -X $cx -Y $cy -R 190 -Fill 'url(#hub)'))
    [void]$sb.Append((New-SvgOrb -X $cx -Y $cy -R 100 -Icon 'cloud' -Accent 'server'))
    [void]$sb.Append((New-SvgText -X $cx -Y ($cy + 150) -Key 'hero.hub' -Size 24 -Fill $p.text -Weight '700' -Anchor middle))
    for ($i = 0; $i -lt $n; $i++) {
        $d = $devices[$i]
        [void]$sb.Append((New-SvgOrb -X $pos[$i].x -Y $pos[$i].y -R 56 -Icon $d.icon -Accent $d.a -IconScale 0.95))
        [void]$sb.Append((New-SvgText -X $pos[$i].x -Y ($pos[$i].y + 100) -Key $d.k -Size 21 -Fill $p.text -Weight '600' -Anchor middle))
    }
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

# The picture set, in the order the docs page shows it. `name` is the published file stem.
$script:UsageSchemeDiagrams = @(
    @{ name = 'ecosystem-hero'; style = 'slide'; build = { Build-EcosystemHero } }
    @{ name = 'three-ways'; style = 'slide'; build = { Build-ThreeWays } }
    @{ name = 'exchange-simple'; style = 'simple'; build = { Build-ExchangeSimple } }
    @{ name = 'exchange-scheme'; style = 'scheme'; build = { Build-ExchangeScheme } }
    @{ name = 'exchange-steps'; style = 'slide'; build = { Build-ExchangeSteps } }
    @{ name = 'exchange-keeps'; style = 'slide'; build = { Build-ExchangeKeeps } }
    @{ name = 'broadcast-simple'; style = 'simple'; build = { Build-BroadcastSimple } }
    @{ name = 'broadcast-scheme'; style = 'scheme'; build = { Build-BroadcastScheme } }
    @{ name = 'broadcast-order'; style = 'simple'; build = { Build-BroadcastOrder } }
    @{ name = 'broadcast-cast'; style = 'slide'; build = { Build-BroadcastCast } }
    @{ name = 'home-simple'; style = 'simple'; build = { Build-HomeSimple } }
    @{ name = 'home-scheme'; style = 'scheme'; build = { Build-HomeScheme } }
    @{ name = 'home-steps'; style = 'slide'; build = { Build-HomeSteps } }
    @{ name = 'home-pairing'; style = 'slide'; build = { Build-HomePairing } }
)
