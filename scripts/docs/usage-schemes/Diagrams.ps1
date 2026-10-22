# Diagrams - the usage-scheme illustrations (S4140), one function per picture.
# Dot-sourced by scripts/docs/render-usage-schemes.ps1 after SvgKit.ps1.
#
# Facts drawn here come from contracts DEVICE-EXCHANGE, ANYWHERE-ACCESS and LIVE-BROADCAST and from the
# shipped records in docs/ALL_FEATURES.jsonl. Windows broadcasting is drawn as present by the owner's
# decision recorded in PLAN/S4140. Every address is from a documentation range (RFC 5737) or the
# private 192.168.1.0/24 example, never a real host.

Set-StrictMode -Version Latest

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

# The six devices that hold or open your files; the router and the phone-as-server are means, not devices you
# reach your files on, so the cover leaves them to the section pictures.
$script:HeroDevices = @(
    @{ icon = 'phone'; k = 'dev.phone'; a = 'tunnel' }
    @{ icon = 'watch'; k = 'dev.watch'; a = 'media' }
    @{ icon = 'tv'; k = 'dev.tv'; a = 'media' }
    @{ icon = 'headset'; k = 'dev.headset'; a = 'lan' }
    @{ icon = 'laptop'; k = 'dev.laptop'; a = 'control' }
    @{ icon = 'desktop'; k = 'dev.windowspc'; a = 'control' }
)

# Hub and devices of the cover. Text sizes keep every word at 12 px or more where the page shows the picture:
# 40+ on the 1920 canvas in a 630 px column, 47+ on the 1080 canvas in a 280 px column (S4415).
function Add-HeroHub {
    param($Sb, [double]$Cx, [double]$Cy, [object[]]$Pos, [double]$TextSize, [double]$LabelGap)
    $p = $script:Palette
    $devices = $script:HeroDevices
    for ($i = 0; $i -lt $devices.Count; $i++) {
        [void]$Sb.Append((New-SvgLine -Points @($Cx, $Cy, $Pos[$i].x, $Pos[$i].y) -Color $devices[$i].a -Width 2.5 -Dash '3 9' -Arrow none -Opacity 0.8))
    }
    [void]$Sb.Append((New-SvgCircle -X $Cx -Y $Cy -R 190 -Fill 'url(#hub)'))
    [void]$Sb.Append((New-SvgOrb -X $Cx -Y $Cy -R 100 -Icon 'cloud' -Accent 'server'))
    [void]$Sb.Append((New-SvgText -X $Cx -Y ($Cy + 150) -Key 'hero.hub' -Size $TextSize -Fill $p.text -Weight '700' -Anchor middle))
    for ($i = 0; $i -lt $devices.Count; $i++) {
        $d = $devices[$i]
        [void]$Sb.Append((New-SvgOrb -X $Pos[$i].x -Y $Pos[$i].y -R 56 -Icon $d.icon -Accent $d.a -IconScale 0.95))
        [void]$Sb.Append((New-SvgText -X $Pos[$i].x -Y ($Pos[$i].y + $LabelGap) -Key $d.k -Size $TextSize -Fill $p.text -Weight '600' -Anchor middle))
    }
}

function Build-EcosystemHero {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1920 -H 1080 -TitleKey 'hero.title' -DescKey 'hero.desc'
    [void]$sb.Append((New-SvgWaves -W 1920 -H 1080 -Base 980 -Seed 13 -Amp 30))
    [void]$sb.Append((New-SvgText -X 100 -Y 150 -Key 'hero.title' -Size 64 -Fill $p.text -Weight '700'))
    $cx = 960; $cy = 600; $rx = 640; $ry = 270
    # Started a sixth of a turn off the vertical, so no device sits straight above or below the hub label.
    $pos = for ($i = 0; $i -lt $script:HeroDevices.Count; $i++) {
        $ang = -[math]::PI / 2 + [math]::PI / 6 + $i * [math]::PI / 3
        @{ x = $cx + [math]::Cos($ang) * $rx; y = $cy + [math]::Sin($ang) * $ry }
    }
    Add-HeroHub -Sb $sb -Cx $cx -Cy $cy -Pos $pos -TextSize 44 -LabelGap 120
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

# The portrait cover the start page shows at 768 px and below: the same words, two rows of three devices.
function Build-EcosystemHeroPhone {
    $p = $script:Palette
    $sb = Open-SvgDocument -W 1080 -H 1350 -TitleKey 'hero.title' -DescKey 'hero.desc'
    [void]$sb.Append((New-SvgWaves -W 1080 -H 1350 -Base 1270 -Seed 13 -Amp 24))
    # The title does not fit one line at this size, so it breaks at the space nearest its middle.
    $title = Get-Label 'hero.title'
    $mid = $title.Length / 2
    $cut = -1
    for ($i = 0; $i -lt $title.Length; $i++) {
        if ($title[$i] -eq ' ' -and ($cut -lt 0 -or [math]::Abs($i - $mid) -lt [math]::Abs($cut - $mid))) { $cut = $i }
    }
    $twoLines = if ($cut -gt 0) { $title.Substring(0, $cut) + "`n" + $title.Substring($cut + 1) } else { $title }
    [void]$sb.Append((New-SvgText -X 540 -Y 130 -Raw $twoLines -Size 64 -Fill $p.text -Weight '700' -Anchor middle -LineHeight 1.2))
    $cols = @(200, 540, 880)
    $pos = @(
        foreach ($x in $cols) { @{ x = $x; y = 360 } }
        foreach ($x in $cols) { @{ x = $x; y = 1030 } }
    )
    Add-HeroHub -Sb $sb -Cx 540 -Cy 695 -Pos $pos -TextSize 48 -LabelGap 120
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}
# A numbered "how to set it up" slide for one scenario page of the portal (S4242): four cards in two columns,
# or six in three, drawn like home-steps. Keys are <Prefix>.title, .desc, .sub and <Prefix>.<n>.t / .b.
function Build-ScenarioSteps {
    param([string]$Prefix, [string[]]$Icons, [string[]]$Accents, [int]$Seed = 9)
    $p = $script:Palette
    $count = $Icons.Count
    if ($count -ne 4 -and $count -ne 6) { throw "scenario slide '$Prefix' needs four or six cards, got $count" }
    $sb = Open-SvgDocument -W 1920 -H 1080 -TitleKey "$Prefix.title" -DescKey "$Prefix.desc"
    [void]$sb.Append((New-SvgWaves -W 1920 -H 1080 -Base 1030 -Seed $Seed -Amp 20))
    [void]$sb.Append((New-SvgHeading -TitleKey "$Prefix.title" -SubKey "$Prefix.sub" -X 100 -Y 140 -TitleSize 54 -SubSize 26))
    $cols = if ($count -eq 4) { 2 } else { 3 }
    $cardW = if ($count -eq 4) { 820 } else { 560 }
    $stepX = if ($count -eq 4) { 900 } else { 580 }
    for ($i = 0; $i -lt $count; $i++) {
        $col = $i % $cols; $row = [math]::Floor($i / $cols)
        $x = 100 + $col * $stepX; $y = 280 + $row * 360
        $a = $Accents[$i]
        [void]$sb.Append((New-SvgRect -X $x -Y $y -W $cardW -H 330 -Fill $p.panel -Stroke $p.panelStroke -StrokeWidth 1.5 -Rx 24))
        [void]$sb.Append((New-SvgRect -X $x -Y $y -W $cardW -H 8 -Fill $p[$a] -Rx 4))
        [void]$sb.Append((New-SvgStepBadge -X ($x + 54) -Y ($y + 70) -Number ($i + 1) -Accent $a -R 28))
        [void]$sb.Append((New-SvgIcon -Name $Icons[$i] -X ($x + $cardW - 90) -Y ($y + 36) -Size 58 -Fill $p[$a] -Opacity 0.9))
        [void]$sb.Append((New-SvgText -X ($x + 40) -Y ($y + 150) -Key "$Prefix.$($i + 1).t" -Size 30 -Fill $p.text -Weight '700'))
        [void]$sb.Append((New-SvgText -X ($x + 40) -Y ($y + 200) -Key "$Prefix.$($i + 1).b" -Size 21 -Fill $p.muted -LineHeight 1.4))
    }
    [void]$sb.Append('</svg>')
    return $sb.ToString()
}

# The picture set, in the order the docs page shows it. `name` is the published file stem.
$script:UsageSchemeDiagrams = @(
    @{ name = 'ecosystem-hero'; style = 'slide'; build = { Build-EcosystemHero } }
    @{ name = 'ecosystem-hero-phone'; style = 'slide'; build = { Build-EcosystemHeroPhone } }
    @{ name = 'exchange-simple'; style = 'simple'; build = { Build-ExchangeSimple } }
    @{ name = 'exchange-keeps'; style = 'slide'; build = { Build-ExchangeKeeps } }
    @{ name = 'broadcast-simple'; style = 'simple'; build = { Build-BroadcastSimple } }
    @{ name = 'broadcast-cast'; style = 'slide'; build = { Build-BroadcastCast } }
    @{ name = 'home-simple'; style = 'simple'; build = { Build-HomeSimple } }
    @{ name = 'home-steps'; style = 'slide'; build = { Build-HomeSteps } }
    @{ name = 'home-pairing'; style = 'slide'; build = { Build-HomePairing } }
    @{ name = 'steps-folders-exchange'; style = 'slide'; build = { Build-ScenarioSteps -Prefix 'sc.folders-exchange' -Seed 3 -Icons @('person', 'folder', 'swap', 'search', 'check', 'lock') -Accents @('control', 'tunnel', 'server', 'media', 'tunnel', 'lan') } }
    @{ name = 'steps-folders-drive'; style = 'slide'; build = { Build-ScenarioSteps -Prefix 'sc.folders-drive' -Seed 4 -Icons @('person', 'cloud', 'folder', 'swap') -Accents @('control', 'server', 'tunnel', 'media') } }
    @{ name = 'steps-broadcast-start'; style = 'slide'; build = { Build-ScenarioSteps -Prefix 'sc.broadcast-start' -Seed 5 -Icons @('camera', 'play', 'qr', 'antenna') -Accents @('media', 'tunnel', 'server', 'control') } }
    @{ name = 'steps-broadcast-receive'; style = 'slide'; build = { Build-ScenarioSteps -Prefix 'sc.broadcast-receive' -Seed 6 -Icons @('antenna', 'qr', 'key', 'cast') -Accents @('control', 'server', 'media', 'tunnel') } }
    @{ name = 'steps-watch'; style = 'slide'; build = { Build-ScenarioSteps -Prefix 'sc.watch' -Seed 7 -Icons @('server', 'watch', 'key', 'check', 'wifi', 'phone') -Accents @('server', 'control', 'media', 'tunnel', 'lan', 'control') } }
    @{ name = 'steps-schedule'; style = 'slide'; build = { Build-ScenarioSteps -Prefix 'sc.schedule' -Seed 8 -Icons @('tune', 'folder', 'swap', 'log') -Accents @('control', 'tunnel', 'media', 'server') } }
    @{ name = 'steps-phone-sharing'; style = 'slide'; build = { Build-ScenarioSteps -Prefix 'sc.phone-sharing' -Seed 10 -Icons @('phone', 'folder', 'home', 'cellular') -Accents @('server', 'tunnel', 'lan', 'media') } }
    @{ name = 'steps-my-devices'; style = 'slide'; build = { Build-ScenarioSteps -Prefix 'sc.my-devices' -Seed 12 -Icons @('qr', 'shield', 'key', 'phone', 'server', 'check') -Accents @('control', 'tunnel', 'media', 'lan', 'server', 'tunnel') } }
)
