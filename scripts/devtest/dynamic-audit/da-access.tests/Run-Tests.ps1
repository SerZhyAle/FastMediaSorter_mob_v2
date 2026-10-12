#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite of lib/da-access.ps1 (S4166 phase 06), run without a device or an emulator.

.DESCRIPTION
    Covers the whole understanding-and-access layer with synthetic inputs only: the U cards (ordinal
    pick, field set, byte-stable output, the real manifest), the uiautomator parser, the unnamed-node
    and state-gap detectors, the D-pad focus-order report, the mirrored-order check, the clip-check
    answer parser, the font-scale and per-app-locale recorders, the verified-backup envelope gate, the
    access probe end to end against a recorded-output fake device (including refusal before any adb
    call, restore failure and an unreadable locale), the interrupted-run recovery, and the message-copy
    review in en, ru, uk and ar.

    The fake device keeps the font scale, the per-app locale and the focus position, answers the exact
    adb argument lists the library sends, and records every call, so the suite can prove that nothing
    outside the settings put/delete and locale calls was sent. No emulator or device is contacted; every
    file is written under a fresh directory of the system temp folder and removed at the end, and the
    real state directory under temp/S4166 is never read or written.

    Non-ASCII samples are written as ~uXXXX markers and expanded by U at run time, so this file stays
    ASCII-only.

.NOTES
    Exit codes:
      0 - every case passed
      1 - a case failed
#>
# Subject: scripts/devtest/dynamic-audit/lib/da-access.ps1
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$daSuiteRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('da-access-suite-' + [guid]::NewGuid().ToString('N'))
$daLibDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'lib'
$daRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
. (Join-Path $daLibDir 'da-common.ps1')
. (Join-Path $daLibDir 'da-state.ps1')
. (Join-Path $daLibDir 'da-access.ps1')

$script:daPassed = 0
$script:daFailed = [System.Collections.Generic.List[string]]::new()
function Assert-Case {
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][bool]$Condition, [string]$Detail = '')
    if ($Condition) { $script:daPassed++ } else { $script:daFailed.Add("$Name $Detail"); Write-Host "FAIL: $Name $Detail" }
}

function U {
    param([Parameter(Mandatory)][string]$Text)
    return [regex]::Replace($Text, '~u([0-9a-fA-F]{4})', { param($m) [string][char][Convert]::ToInt32($m.Groups[1].Value, 16) })
}

function Write-TestText {
    param([string]$Path, [string]$Text)
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}

# ---- synthetic uiautomator dumps -------------------------------------------------------------

function B { param([bool]$Value) if ($Value) { return 'true' } else { return 'false' } }

function New-XmlNode {
    param([string]$Class = 'android.widget.TextView', [string]$Bounds = '[0,0][10,10]', [hashtable]$Attr = @{}, [string]$Inner = '')
    $a = [ordered]@{
        index = '0'; text = ''; 'resource-id' = ''; class = $Class; package = 'p'; 'content-desc' = ''
        checkable = 'false'; checked = 'false'; clickable = 'false'; enabled = 'true'; focusable = 'false'
        focused = 'false'; 'long-clickable' = 'false'; password = 'false'; scrollable = 'false'
        selected = 'false'; bounds = $Bounds
    }
    foreach ($k in $Attr.Keys) { $a[$k] = $Attr[$k] }
    $text = (@($a.Keys | ForEach-Object { '{0}="{1}"' -f $_, [System.Security.SecurityElement]::Escape([string]$a[$_]) })) -join ' '
    if ($Inner) { return "<node $text>$Inner</node>" }
    return "<node $text />"
}

function New-XmlTree {
    param([string]$Inner)
    return "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=`"0`">$Inner</hierarchy>"
}

function New-RootNode {
    param([string]$Inner)
    return (New-XmlNode -Class 'android.widget.FrameLayout' -Bounds '[0,0][1080,2400]' -Inner $Inner)
}

# ---- fake device -----------------------------------------------------------------------------

function New-FakeScreenXml {
    param($Fake)
    $rtl = ($Fake.Tag -like 'ar*')
    $mirror = { param([int]$x1, [int]$x2) if ($rtl -and -not $Fake.RtlNotMirrored) { return @((1080 - $x2), (1080 - $x1)) } else { return @($x1, $x2) } }
    $textTitle = if ($rtl) { U '~u0627~u0644~u0625~u0639~u062f~u0627~u062f~u0627~u062a' } else { 'Settings' }
    $textMenu = if ($rtl) { U '~u0627~u0644~u0642~u0627~u0626~u0645~u0629' } else { 'Menu' }
    $textRow = if ($rtl) { U '~u0625~u0638~u0647~u0627~u0631 ~u0627~u0644~u0633~u0627~u0639~u0629' } else { 'Show clock' }
    $textSave = if ($rtl) { U '~u062d~u0641~u0638' } else { 'Save' }
    $focus = $Fake.Focus

    $m = & $mirror 0 120
    $back = New-XmlNode -Class 'android.widget.ImageButton' -Bounds "[$($m[0]),100][$($m[1]),220]" -Attr @{
        'resource-id' = 'p:id/btnBack'; clickable = 'true'; focusable = 'true'; focused = (B ($focus -eq 0)) }
    $title = New-XmlNode -Bounds '[380,100][700,220]' -Attr @{ text = $textTitle; 'resource-id' = 'p:id/title' }
    $m = & $mirror 960 1080
    $menu = New-XmlNode -Class 'android.widget.ImageButton' -Bounds "[$($m[0]),100][$($m[1]),220]" -Attr @{
        'resource-id' = 'p:id/btnMenu'; 'content-desc' = $textMenu; clickable = 'true'; focusable = 'true'; focused = (B ($focus -eq 1)) }
    $m = & $mirror 40 600
    $rowLabel = New-XmlNode -Bounds "[$($m[0]),300][$($m[1]),420]" -Attr @{ text = $textRow; 'resource-id' = 'p:id/rowLabel' }
    $m = & $mirror 900 1040
    $switch = New-XmlNode -Class 'android.widget.Switch' -Bounds "[$($m[0]),320][$($m[1]),400]" -Attr @{ 'resource-id' = 'p:id/switchToggle' }
    $row = New-XmlNode -Class 'android.widget.LinearLayout' -Bounds '[40,300][1040,420]' -Attr @{
        'resource-id' = 'p:id/rowToggle'; clickable = 'true'; focusable = 'true'; focused = (B ($focus -eq 2)) } -Inner ($rowLabel + $switch)
    $save = New-XmlNode -Class 'android.widget.Button' -Bounds '[40,500][1040,600]' -Attr @{
        text = $textSave; 'resource-id' = 'p:id/btnSave'; clickable = 'true'; focusable = 'true'; focused = (B ($focus -eq 3)) }
    $extra = ''
    if ($Fake.Font -and [double]::Parse($Fake.Font, [System.Globalization.CultureInfo]::InvariantCulture) -ge 1.9) {
        $extra = New-XmlNode -Bounds '[900,700][1200,800]' -Attr @{ text = 'Wide'; 'resource-id' = 'p:id/wide' }
    }
    return (New-XmlTree -Inner (New-RootNode -Inner ($back + $title + $menu + $row + $save + $extra))) + "`nUI hierchary dumped to: /dev/tty"
}

function New-FakeDevice {
    param(
        [AllowNull()]$Font = $null,
        [string]$Tag = '',
        [switch]$LocaleBroken,
        [switch]$IgnoreDelete,
        [switch]$RtlNotMirrored,
        [switch]$TreeBroken
    )
    return [pscustomobject]@{
        Font           = $Font
        Tag            = $Tag
        Focus          = -1
        Calls          = [System.Collections.Generic.List[string]]::new()
        LocaleBroken   = [bool]$LocaleBroken
        IgnoreDelete   = [bool]$IgnoreDelete
        RtlNotMirrored = [bool]$RtlNotMirrored
        TreeBroken     = [bool]$TreeBroken
    }
}

$script:fake = $null
$script:fakeAdb = {
    param([string[]]$a)
    $f = $script:fake
    $line = $a -join ' '
    $f.Calls.Add($line)
    $res = { param($lines, [int]$code = 0) [pscustomobject]@{ ExitCode = $code; Lines = @($lines); Stderr = '' } }
    if ($line -eq 'exec-out uiautomator dump /dev/tty') {
        if ($f.TreeBroken) { return (& $res @('ERROR: could not get idle state.')) }
        return (& $res @((New-FakeScreenXml -Fake $f) -split "`n"))
    }
    if ($line -eq 'shell input keyevent KEYCODE_DPAD_DOWN') {
        if ($f.Focus -lt 3) { $f.Focus++ }
        return (& $res @())
    }
    if ($line -eq 'shell settings get system font_scale') {
        return (& $res @($(if ($null -eq $f.Font) { 'null' } else { $f.Font })))
    }
    if ($line -match '^shell settings put system font_scale (\S+)$') { $f.Font = $Matches[1]; return (& $res @()) }
    if ($line -eq 'shell settings delete system font_scale') {
        if (-not $f.IgnoreDelete) { $f.Font = $null }
        return (& $res @('Deleted 1 rows'))
    }
    if ($line -match '^shell cmd locale get-app-locales (\S+)$') {
        if ($f.LocaleBroken) { return (& $res @('Unknown command: locale') 255) }
        $t = if ($f.Tag) { ($f.Tag -replace '-', '_') } else { '' }
        return (& $res @("Locales for $($Matches[1]) for user 0 are [$t]"))
    }
    if ($line -match '^shell cmd locale set-app-locales (\S+) --locales (\S+)$') { $f.Tag = $Matches[2]; return (& $res @()) }
    if ($line -match '^shell cmd locale set-app-locales (\S+)$') { $f.Tag = ''; return (& $res @()) }
    return (& $res @('unexpected call') 1)
}

$script:clipOk = '{"ok":true,"exitCode":0,"verb":"clip-check","device":"emulator-5554","package":null,"data":{"id":"emulator-5554","file":"x","shape":{"width":1080,"height":2400,"radius":105,"round":false,"trusted":true},"checked":9,"findings":[],"offGlass":0,"frameCut":0,"strict":false},"reason":null}'
$script:clipBad = '{"ok":false,"exitCode":9,"verb":"clip-check","device":"emulator-5554","package":null,"data":{"id":"emulator-5554","file":"x","shape":{"width":1080,"height":2400,"radius":105,"round":false,"trusted":true},"checked":9,"findings":[{"kind":"OFF-GLASS","label":"Title","overflow":14.2,"x1":0,"y1":0,"x2":10,"y2":10}],"offGlass":1,"frameCut":0,"strict":false},"reason":"1 node(s) off-glass"}'
$script:clipRefused = '{"ok":false,"exitCode":7,"verb":"clip-check","device":"emulator-5554","package":null,"data":null,"reason":"the display shape cannot be measured against: x. Nothing was judged"}'

$script:gateOk = { [pscustomobject]@{ Code = 0; Reason = 'test envelope' } }
$script:gateNo = { [pscustomobject]@{ Code = 2; Reason = 'no restore checkpoint' } }
$script:clipRunner = { param($scale) if ([double]$scale -ge 1.9) { $script:clipBad } else { $script:clipOk } }

function Invoke-FakeProbe {
    param($Fake, [string]$Serial = 'emulator-5554', [scriptblock]$Gate = $script:gateOk, [scriptblock]$Clip = $script:clipRunner)
    $script:fake = $Fake
    return (Invoke-DaAccessProbe -Serial $Serial -Package 'com.sza.fastmediasorter.debug' -Screen 'settings' `
            -Adb $script:fakeAdb -ClipCheck $Clip -EnvelopeGate $Gate -SettleMs 0 -KeySettleMs 0)
}

function Get-CopyCheck {
    param($Result, [int]$Index = 0, [Parameter(Mandatory)][string]$Item)
    return (@($Result.Results[$Index].Checks | Where-Object { $_.Item -eq $Item }) | Select-Object -First 1)
}

$daSavedState = $env:DA_STATE_DIR
try {
    New-Item -ItemType Directory -Path $daSuiteRoot -Force | Out-Null
    $env:DA_STATE_DIR = Join-Path $daSuiteRoot 'state'
    $libPath = Join-Path $daLibDir 'da-access.ps1'

    # 1. the library is functions only, ASCII, and names its policy
    $errs = $null; $tok = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile($libPath, [ref]$tok, [ref]$errs)
    $nonFn = @($ast.EndBlock.Statements | Where-Object { $_ -isnot [System.Management.Automation.Language.FunctionDefinitionAst] })
    Assert-Case 'library parses' ($errs.Count -eq 0)
    Assert-Case 'library holds functions only at script scope' ($nonFn.Count -eq 0 -and $null -eq $ast.ParamBlock)
    $libText = [System.IO.File]::ReadAllText($libPath)
    Assert-Case 'library is ASCII only' (-not [regex]::IsMatch($libText, '[^\x00-\x7F]'))
    Assert-Case 'library calls exit nowhere' (-not [regex]::IsMatch($libText, '(?m)^\s*exit\b'))
    Assert-Case 'library names the communication policy' ($libText.Contains('COMMUNICATION_POLICY'))
    Assert-Case 'library declares Invoke-DaAccessProbe once' (([regex]::Matches($libText, '(?m)^function Invoke-DaAccessProbe\b')).Count -eq 1)
    Assert-Case 'library declares Test-DaMessageCopy once' (([regex]::Matches($libText, '(?m)^function Test-DaMessageCopy\b')).Count -eq 1)
    Assert-Case 'library carries no drive-letter literal' (-not [regex]::IsMatch($libText, '(?<![\w$.\\])[A-Za-z]:[\\/][\w.$]'))

    # 2. U cards from a synthetic manifest
    $repo = Join-Path $daSuiteRoot 'repo'
    $entries = @(
        [ordered]@{ key = 'rowB'; sectionId = 'audio'; destination = 'MEDIA'; layout = 'l'; kind = 'TOGGLE_ROW'; titleEn = 'Show  clock'; titleRu = 'ru b'; titleUk = 'uk b' },
        [ordered]@{ key = 'rowa'; sectionId = 'audio'; destination = 'MEDIA'; layout = 'l'; kind = 'TOGGLE_ROW'; titleEn = 'Lowercase first'; titleRu = 'ru a'; titleUk = 'uk a' },
        [ordered]@{ key = 'aaaButton'; sectionId = 'audio'; destination = 'MEDIA'; layout = 'l'; kind = 'BUTTON'; titleEn = 'A button'; titleRu = 'r'; titleUk = 'u' },
        [ordered]@{ key = 'rowV'; sectionId = 'video'; destination = 'MEDIA'; layout = 'l'; kind = 'TOGGLE_ROW'; titleEn = 'Video row'; titleRu = 'ru v'; titleUk = 'uk v' },
        [ordered]@{ key = 'rowZ2'; sectionId = 'zeta'; destination = 'OPERATIONS'; layout = 'l'; kind = 'TOGGLE_ROW'; titleEn = 'Z two'; titleRu = 'ru z2'; titleUk = 'uk z2' },
        [ordered]@{ key = 'rowZ1'; sectionId = 'zeta'; destination = 'OPERATIONS'; layout = 'l'; kind = 'TOGGLE_ROW'; titleEn = 'Z one'; titleRu = 'ru z1'; titleUk = 'uk z1' },
        [ordered]@{ key = 'sel'; sectionId = 'onlybuttons'; destination = 'GENERAL'; layout = 'l'; kind = 'SELECTION_ROW'; titleEn = 'Sel'; titleRu = 'r'; titleUk = 'u' }
    )
    Write-TestText -Path (Join-Path $repo 'docs/settings/settings-manifest.json') -Text (([ordered]@{ entries = $entries } | ConvertTo-Json -Depth 8) + "`n")
    $doc = New-DaUCards -RepoRoot $repo
    $cards = @($doc.cards)
    Assert-Case 'one card per toggle section' ($cards.Count -eq 3) "count=$($cards.Count)"
    Assert-Case 'section without a toggle yields no card' (@($cards | Where-Object { $_.sectionId -eq 'onlybuttons' }).Count -eq 0)
    Assert-Case 'cards are in ordinal section order' ((@($cards | ForEach-Object { $_.sectionId }) -join ',') -eq 'audio,video,zeta')
    Assert-Case 'pick is the lowest key in ordinal order' ($cards[0].key -eq 'rowB' -and $cards[2].key -eq 'rowZ1') "$($cards[0].key) $($cards[2].key)"
    Assert-Case 'card id is U- plus the section' ($cards[0].id -eq 'U-audio')
    Assert-Case 'section toggle count is carried' ($cards[0].sectionToggleCount -eq 2 -and $cards[1].sectionToggleCount -eq 1)
    Assert-Case 'user task is a plain imperative from titleEn' ($cards[0].userTask -eq 'Find the setting "Show clock" and switch it to the opposite of its current state.') $cards[0].userTask
    $want = 'id,key,sectionId,destination,titleEn,titleRu,titleUk,userTask,sectionToggleCount,valueBefore,prediction,effectAfter,persistsAfterRestart,dependencies,wayBack,reversibilityLimit,results'
    Assert-Case 'card carries exactly the specified fields' ((@($cards[0].Keys) -join ',') -eq $want) (@($cards[0].Keys) -join ',')
    Assert-Case 'runtime fields start null' ($null -eq $cards[0].valueBefore -and $null -eq $cards[0].prediction -and $null -eq $cards[0].effectAfter -and $null -eq $cards[0].persistsAfterRestart -and $null -eq $cards[0].dependencies -and $null -eq $cards[0].wayBack -and $null -eq $cards[0].reversibilityLimit)
    Assert-Case 'results hold exactly en ru uk ar' ((@($cards[0].results.Keys) -join ',') -eq 'en,ru,uk,ar')
    $allOpen = $true
    foreach ($c in $cards) {
        foreach ($lang in @('en', 'ru', 'uk', 'ar')) {
            $cell = $c.results[$lang]
            if ($cell.status -ne 'open' -or $cell.owner -ne 'owner' -or [string]::IsNullOrWhiteSpace($cell.nextStep) -or [string]::IsNullOrWhiteSpace($cell.reason) -or $cell.attemptsPlanned -ne 15 -or $cell.attemptsRecorded -ne 0 -or $cell.representative -ne $false) { $allOpen = $false }
        }
    }
    Assert-Case 'every result cell is open with owner reason and next step' $allOpen
    Assert-Case 'document is schemaVersion 1 and says non-representative' ($doc.schemaVersion -eq 1 -and $doc.participant.representative -eq $false -and $doc.participant.note -like '*non-representative*')
    Assert-Case 'source counts' ($doc.source.toggleRows -eq 5 -and $doc.source.sections -eq 3 -and $doc.source.uniqueKeys -eq 5)

    $audit = Join-Path $daSuiteRoot 'out/audit'
    $reports = Join-Path $daSuiteRoot 'out/reports'
    $w1 = @(Write-DaUCards -RepoRoot $repo -AuditRoot $audit -ReportsRoot $reports)
    $w2 = @(Write-DaUCards -RepoRoot $repo -AuditRoot $audit -ReportsRoot $reports)
    Assert-Case 'two files written' ($w1.Count -eq 2 -and (Test-Path -LiteralPath (Join-Path $audit 'ucards.json')) -and (Test-Path -LiteralPath (Join-Path $reports 'u-protocol.md')))
    Assert-Case 're-run is byte-identical' ($w1[0].Sha256 -eq $w2[0].Sha256 -and $w1[1].Sha256 -eq $w2[1].Sha256)
    foreach ($f in @((Join-Path $audit 'ucards.json'), (Join-Path $reports 'u-protocol.md'))) {
        $bytes = [System.IO.File]::ReadAllBytes($f)
        $name = Split-Path -Leaf $f
        Assert-Case "$name has no BOM" (-not ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF))
        Assert-Case "$name has no CR and ends with LF" (-not ($bytes -contains 13) -and $bytes[-1] -eq 10)
    }
    $parsed = [System.IO.File]::ReadAllText((Join-Path $audit 'ucards.json')) | ConvertFrom-Json -Depth 32
    Assert-Case 'ucards.json is valid JSON with three cards' (@($parsed.cards).Count -eq 3 -and $parsed.cards[0].sectionId -eq 'audio')
    $proto = [System.IO.File]::ReadAllText((Join-Path $reports 'u-protocol.md'))
    foreach ($needle in @('non-representative', '15 attempts', 'en, ru, uk, ar', 'TalkBack', 'D-pad', 'Restore', 'cannot prove the 90 percent threshold', 'U-audio', 'U-zeta')) {
        Assert-Case "protocol states '$needle'" ($proto.Contains($needle))
    }
    Assert-Case 'protocol has no table row and no ellipsis or long dash' (-not [regex]::IsMatch($proto, '(?m)^\s*\|') -and -not $proto.Contains('...') -and -not [regex]::IsMatch($proto, '[^\x00-\x7F]'))
    $headings = @([regex]::Matches($proto, '(?m)^#{1,6} .+$') | ForEach-Object { $_.Value })
    Assert-Case 'protocol headings are unique' (($headings | Select-Object -Unique).Count -eq $headings.Count)

    # 2b. refusal paths of the card builder
    $threw = $false; try { New-DaUCards -RepoRoot (Join-Path $daSuiteRoot 'nowhere') | Out-Null } catch { $threw = $true }
    Assert-Case 'absent repo root throws' $threw
    $repoNone = Join-Path $daSuiteRoot 'repo-none'
    Write-TestText -Path (Join-Path $repoNone 'docs/settings/settings-manifest.json') -Text '{"entries":[{"key":"b","sectionId":"s","destination":"D","kind":"BUTTON","titleEn":"x","titleRu":"x","titleUk":"x"}]}'
    $threw = $false; try { New-DaUCards -RepoRoot $repoNone | Out-Null } catch { $threw = $true }
    Assert-Case 'manifest without a TOGGLE_ROW throws' $threw
    $repoBad = Join-Path $daSuiteRoot 'repo-bad'
    Write-TestText -Path (Join-Path $repoBad 'docs/settings/settings-manifest.json') -Text '{"entries":[{"key":"b","sectionId":"s","destination":"D","kind":"TOGGLE_ROW","titleEn":"","titleRu":"x","titleUk":"x"}]}'
    $threw = $false; try { New-DaUCards -RepoRoot $repoBad | Out-Null } catch { $threw = $true }
    Assert-Case 'toggle without titleEn throws' $threw
    $repoJunk = Join-Path $daSuiteRoot 'repo-junk'
    Write-TestText -Path (Join-Path $repoJunk 'docs/settings/settings-manifest.json') -Text '{ not json'
    $threw = $false; try { New-DaUCards -RepoRoot $repoJunk | Out-Null } catch { $threw = $true }
    Assert-Case 'malformed manifest throws' $threw

    # 2c. the live manifest: one card per distinct section, computed independently
    $liveManifest = [System.IO.File]::ReadAllText((Join-Path $daRepoRoot 'docs/settings/settings-manifest.json')) | ConvertFrom-Json -Depth 32
    $liveToggles = @($liveManifest.entries | Where-Object { $_.kind -eq 'TOGGLE_ROW' })
    $liveSections = @($liveToggles | ForEach-Object { [string]$_.sectionId } | Select-Object -Unique)
    $liveDoc = New-DaUCards -RepoRoot $daRepoRoot
    Assert-Case 'live manifest: one card per toggle section' (@($liveDoc.cards).Count -eq $liveSections.Count -and $liveSections.Count -gt 0) "cards=$(@($liveDoc.cards).Count) sections=$($liveSections.Count)"
    $minOk = $true
    foreach ($c in @($liveDoc.cards)) {
        $keysIn = @($liveToggles | Where-Object { [string]$_.sectionId -eq $c.sectionId } | ForEach-Object { [string]$_.key })
        [array]::Sort($keysIn, [System.StringComparer]::Ordinal)
        if ($keysIn[0] -ne $c.key) { $minOk = $false }
    }
    Assert-Case 'live manifest: every card holds the ordinal-first key of its section' $minOk
    Assert-Case 'live manifest: toggle row count matches' ($liveDoc.source.toggleRows -eq $liveToggles.Count)

    # 3. uiautomator parser
    $tree = New-XmlTree -Inner (New-RootNode -Inner (
            (New-XmlNode -Class 'android.widget.ImageButton' -Bounds '[0,100][120,220]' -Attr @{ 'resource-id' = 'com.x.debug:id/btnBack'; clickable = 'true'; focusable = 'true' }) +
            (New-XmlNode -Bounds '[380,100][700,220]' -Attr @{ text = 'A & B'; hint = 'typed' })))
    $p = ConvertFrom-DaUiTreeXml -Xml ($tree + "`nUI hierchary dumped to: /dev/tty")
    Assert-Case 'parser: ok with trailing dump line' ($p.Ok -and $p.Nodes.Count -eq 3)
    Assert-Case 'parser: width and height from the root' ($p.Width -eq 1080 -and $p.Height -eq 2400)
    Assert-Case 'parser: parent index and depth' ($p.Nodes[1].Parent -eq 0 -and $p.Nodes[1].Depth -eq 1 -and $p.Nodes[0].Parent -eq -1 -and $p.Nodes[0].Leaf -eq $false)
    Assert-Case 'parser: flags, short id and bounds' ($p.Nodes[1].Clickable -and $p.Nodes[1].Focusable -and $p.Nodes[1].ResIdShort -eq 'btnBack' -and $p.Nodes[1].X2 -eq 120 -and $p.Nodes[1].Width -eq 120)
    Assert-Case 'parser: entity decoded and hint read' ($p.Nodes[2].Text -eq 'A & B' -and $p.Nodes[2].Hint -eq 'typed')
    $idle = ConvertFrom-DaUiTreeXml -Xml 'ERROR: could not get idle state.'
    Assert-Case 'parser: idle refusal is flagged' (-not $idle.Ok -and $idle.IdleRefusal)
    Assert-Case 'parser: empty text refused' (-not (ConvertFrom-DaUiTreeXml -Xml '').Ok -and -not (ConvertFrom-DaUiTreeXml -Xml $null).Ok)
    Assert-Case 'parser: broken XML refused' (-not (ConvertFrom-DaUiTreeXml -Xml '<hierarchy><node').Ok -and -not (ConvertFrom-DaUiTreeXml -Xml '<hierarchy><node a="1"></hierarchy>').Ok)
    $dtd = '<!DOCTYPE h [<!ENTITY e "x">]><hierarchy><node text="&e;" bounds="[0,0][1,1]"/></hierarchy>'
    Assert-Case 'parser: a doctype entity is refused, not expanded' (-not (ConvertFrom-DaUiTreeXml -Xml $dtd).Ok)

    # 4. unnamed nodes and state gaps
    $inner = (
        (New-XmlNode -Class 'android.widget.ImageButton' -Bounds '[0,0][100,100]' -Attr @{ clickable = 'true'; focusable = 'true'; 'resource-id' = 'p:id/unnamedIcon' }) +
        (New-XmlNode -Class 'android.widget.LinearLayout' -Bounds '[0,100][1000,200]' -Attr @{ clickable = 'true'; focusable = 'true'; 'resource-id' = 'p:id/namedRow' } -Inner (New-XmlNode -Bounds '[0,100][500,200]' -Attr @{ text = 'Row title' })) +
        (New-XmlNode -Class 'android.widget.ImageView' -Bounds '[0,200][100,300]' -Attr @{ 'resource-id' = 'p:id/decor' }) +
        (New-XmlNode -Class 'android.widget.ImageButton' -Bounds '[0,0][0,0]' -Attr @{ clickable = 'true'; 'resource-id' = 'p:id/hidden' }) +
        (New-XmlNode -Class 'androidx.recyclerview.widget.RecyclerView' -Bounds '[0,300][1000,900]' -Attr @{ scrollable = 'true'; focusable = 'true'; 'resource-id' = 'p:id/list' } -Inner (New-XmlNode -Bounds '[0,300][500,400]' -Attr @{ text = 'item' })) +
        (New-XmlNode -Class 'android.widget.TextView' -Bounds '[0,900][100,1000]' -Attr @{ focusable = 'true'; 'resource-id' = 'p:id/emptyFocus' }) +
        (New-XmlNode -Class 'android.widget.ImageButton' -Bounds '[0,1000][100,1100]' -Attr @{ clickable = 'true'; 'content-desc' = 'Close'; 'resource-id' = 'p:id/closeBtn' }) +
        (New-XmlNode -Class 'android.widget.EditText' -Bounds '[0,1100][600,1200]' -Attr @{ clickable = 'true'; focusable = 'true'; hint = 'Name'; 'resource-id' = 'p:id/nameField' }) +
        (New-XmlNode -Class 'android.widget.Switch' -Bounds '[0,1200][100,1300]' -Attr @{ checkable = 'false'; 'resource-id' = 'p:id/sw1' }) +
        (New-XmlNode -Class 'android.widget.Switch' -Bounds '[0,1300][100,1400]' -Attr @{ checkable = 'true'; 'content-desc' = 'Wi-Fi'; 'resource-id' = 'p:id/sw2' }) +
        (New-XmlNode -Class 'com.google.android.material.materialswitch.MaterialSwitch' -Bounds '[0,1400][100,1500]' -Attr @{ 'resource-id' = 'p:id/sw3' }) +
        (New-XmlNode -Class 'android.widget.TextView' -Bounds '[0,1500][100,1600]' -Attr @{ text = 'plain'; 'resource-id' = 'p:id/plain' }))
    $p4 = ConvertFrom-DaUiTreeXml -Xml (New-XmlTree -Inner (New-RootNode -Inner $inner))
    $un = @(Get-DaUnnamedNodes -Nodes $p4.Nodes)
    $unIds = @($un | ForEach-Object { $_.ResId }) | Sort-Object
    Assert-Case 'unnamed: icon button and empty focus stop only' ($unIds.Count -eq 2 -and $unIds[0] -eq 'p:id/emptyFocus' -and $unIds[1] -eq 'p:id/unnamedIcon') ($unIds -join ',')
    Assert-Case 'unnamed: reports class bounds and why' ($un[0].Why -ne '' -and $un[0].Bounds -ne '' -and $un[0].Class -ne '')
    $gaps = @(Get-DaStateGaps -Nodes $p4.Nodes)
    $gapIds = @($gaps | ForEach-Object { $_.ResId }) | Sort-Object
    Assert-Case 'state gaps: unchecked two-state classes only' ($gapIds.Count -eq 2 -and $gapIds[0] -eq 'p:id/sw1' -and $gapIds[1] -eq 'p:id/sw3') ($gapIds -join ',')
    $oneNode = ConvertFrom-DaUiTreeXml -Xml (New-XmlTree -Inner (New-XmlNode -Class 'android.widget.ImageButton' -Bounds '[0,0][50,50]' -Attr @{ clickable = 'true' }))
    Assert-Case 'unnamed: a single-node tree works' (@(Get-DaUnnamedNodes -Nodes $oneNode.Nodes).Count -eq 1)
    Assert-Case 'unnamed: an empty node list works' (@(Get-DaUnnamedNodes -Nodes @()).Count -eq 0 -and @(Get-DaStateGaps -Nodes @()).Count -eq 0)

    # 5. D-pad focus order
    function New-FocusNode { param([string]$Id, [int]$Y = 0, [int]$X = 0) [pscustomobject]@{ ResId = $Id; Text = $Id; Desc = ''; X1 = $X; Y1 = $Y; X2 = $X + 100; Y2 = $Y + 50 } }
    function New-FocusStep { param([int]$Step, $Node, [string]$Key = 'KEYCODE_DPAD_DOWN') [pscustomobject]@{ Step = $Step; Key = $Key; TreeOk = $true; Focused = $Node } }
    $nA = New-FocusNode 'a' 100; $nB = New-FocusNode 'b' 200; $nC = New-FocusNode 'c' 300; $nD = New-FocusNode 'd' 400
    $good = @((New-FocusStep 0 $null 'initial'), (New-FocusStep 1 $nA), (New-FocusStep 2 $nB), (New-FocusStep 3 $nC), (New-FocusStep 4 $nD), (New-FocusStep 5 $nD), (New-FocusStep 6 $nD))
    $r1 = Get-DaFocusOrderReport -Steps $good -FocusableCount 4
    Assert-Case 'focus: a clean traversal has no finding' ($r1.Findings.Count -eq 0 -and $r1.Distinct -eq 4 -and $r1.Presses -eq 6 -and $r1.Complete -and $r1.Unreached -eq 0) ($r1.Findings | ForEach-Object { $_.Kind })
    Assert-Case 'focus: the stall at the end is recorded but not a finding' ($r1.StuckAt -eq 5)
    $trap = @((New-FocusStep 0 $null 'initial'), (New-FocusStep 1 $nA), (New-FocusStep 2 $nB), (New-FocusStep 3 $nB), (New-FocusStep 4 $nB), (New-FocusStep 5 $nB), (New-FocusStep 6 $nB))
    $r2 = Get-DaFocusOrderReport -Steps $trap -FocusableCount 4
    $k2 = @($r2.Findings | ForEach-Object { $_.Kind })
    Assert-Case 'focus: a premature stall is a trap and leaves nodes unreached' ($k2 -contains 'stuck' -and $k2 -contains 'unreached' -and $r2.StuckAt -eq 3 -and $r2.Unreached -eq 2) ($k2 -join ',')
    $cycle = @((New-FocusStep 0 $null 'initial'), (New-FocusStep 1 $nA), (New-FocusStep 2 $nB), (New-FocusStep 3 $nA), (New-FocusStep 4 $nB), (New-FocusStep 5 $nA))
    $r3 = Get-DaFocusOrderReport -Steps $cycle -FocusableCount 4
    Assert-Case 'focus: a cycle before all nodes were seen is a finding' (@($r3.Findings | Where-Object { $_.Kind -eq 'revisit' }).Count -eq 1 -and $r3.RevisitedAt -eq 3)
    $wrap = @((New-FocusStep 0 $null 'initial'), (New-FocusStep 1 $nA), (New-FocusStep 2 $nB), (New-FocusStep 3 $nC), (New-FocusStep 4 $nD), (New-FocusStep 5 $nA))
    $r4 = Get-DaFocusOrderReport -Steps $wrap -FocusableCount 4
    Assert-Case 'focus: a wrap-around after all nodes is not a finding' ($r4.Findings.Count -eq 0 -and $r4.RevisitedAt -eq 5)
    $none = @((New-FocusStep 0 $null 'initial'), (New-FocusStep 1 $null), (New-FocusStep 2 $null), (New-FocusStep 3 $null))
    $r5 = Get-DaFocusOrderReport -Steps $none -FocusableCount 3
    Assert-Case 'focus: no focus on any press is one finding' ($r5.Findings.Count -eq 1 -and $r5.Findings[0].Kind -eq 'no-focus' -and $r5.Distinct -eq 0)
    $inv = @((New-FocusStep 0 $null 'initial'), (New-FocusStep 1 $nB), (New-FocusStep 2 $nA), (New-FocusStep 3 $nC))
    $r6 = Get-DaFocusOrderReport -Steps $inv -FocusableCount 3
    Assert-Case 'focus: a move against the reading order is counted' ($r6.ReadingOrderInversions -eq 1 -and @($r6.Findings | Where-Object { $_.Kind -eq 'order' }).Count -eq 1)
    $r7 = Get-DaFocusOrderReport -Steps $trap -FocusableCount 0
    Assert-Case 'focus: an unknown focusable count raises no stall finding' (@($r7.Findings | Where-Object { $_.Kind -in @('stuck', 'unreached') }).Count -eq 0)
    $hx = @((New-FocusStep 0 $null 'initial'), (New-FocusStep 1 (New-FocusNode 'h1' 0 500) 'KEYCODE_DPAD_RIGHT'), (New-FocusStep 2 (New-FocusNode 'h2' 0 100) 'KEYCODE_DPAD_RIGHT'))
    Assert-Case 'focus: horizontal order honours Rtl' ((Get-DaFocusOrderReport -Steps $hx -FocusableCount 2).ReadingOrderInversions -eq 1 -and (Get-DaFocusOrderReport -Steps $hx -FocusableCount 2 -Rtl).ReadingOrderInversions -eq 0)
    Assert-Case 'focus: empty steps work' ((Get-DaFocusOrderReport -Steps @()).Findings.Count -eq 0)

    # 6. mirrored order
    function New-MirrorNode { param([string]$Id, [int]$X1, [int]$X2, [string]$Text = '') [pscustomobject]@{ ResId = $Id; Text = $Text; Desc = ''; X1 = $X1; Y1 = 100; X2 = $X2; Y2 = 200; Width = ($X2 - $X1); Height = 100 } }
    $ltrN = @((New-MirrorNode 'back' 0 120 'Back'), (New-MirrorNode 'title' 380 700 'Settings'), (New-MirrorNode 'menu' 960 1080 'Menu'))
    $rtlGood = @((New-MirrorNode 'back' 960 1080 (U '~u0631~u062c~u0648~u0639')), (New-MirrorNode 'title' 380 700 (U '~u0627~u0644~u0625~u0639~u062f~u0627~u062f~u0627~u062a')), (New-MirrorNode 'menu' 0 120 (U '~u0627~u0644~u0642~u0627~u0626~u0645~u0629')))
    $mo1 = Test-DaMirroredOrder -LtrNodes $ltrN -RtlNodes $rtlGood -Width 1080
    Assert-Case 'mirror: swapped sides pass' ($mo1.Verdict -eq 'pass' -and $mo1.Matched -eq 3 -and $mo1.Mirrored -eq 2 -and $mo1.Symmetric -eq 1 -and $mo1.NotMirrored.Count -eq 0 -and $mo1.LocaleApplied) $mo1.Reason
    $rtlSame = @((New-MirrorNode 'back' 0 120 (U '~u0631~u062c~u0648~u0639')), (New-MirrorNode 'title' 380 700 'x'), (New-MirrorNode 'menu' 960 1080 'y'))
    $mo2 = Test-DaMirroredOrder -LtrNodes $ltrN -RtlNodes $rtlSame -Width 1080
    Assert-Case 'mirror: elements that kept their side are a finding' ($mo2.Verdict -eq 'finding' -and $mo2.NotMirrored.Count -eq 2 -and $mo2.NotMirrored[0].ResId -eq 'back')
    $rtlNoLabels = @((New-MirrorNode 'back' 960 1080 'Back'), (New-MirrorNode 'title' 380 700 'Settings'), (New-MirrorNode 'menu' 0 120 'Menu'))
    $mo3 = Test-DaMirroredOrder -LtrNodes $ltrN -RtlNodes $rtlNoLabels -Width 1080
    Assert-Case 'mirror: unchanged labels mean the locale did not apply' ($mo3.Verdict -eq 'inconclusive' -and -not $mo3.LocaleApplied)
    $centred = @((New-MirrorNode 'only' 440 640 'a'))
    $mo4 = Test-DaMirroredOrder -LtrNodes $centred -RtlNodes @((New-MirrorNode 'only' 440 640 'b')) -Width 1080
    Assert-Case 'mirror: only centred elements are inconclusive' ($mo4.Verdict -eq 'inconclusive')
    $mo5 = Test-DaMirroredOrder -LtrNodes @((New-MirrorNode '' 0 100 'a')) -RtlNodes @((New-MirrorNode '' 900 1000 'b')) -Width 1080
    Assert-Case 'mirror: nodes without ids cannot be matched' ($mo5.Verdict -eq 'inconclusive' -and $mo5.Matched -eq 0)
    $ltrRep = @((New-MirrorNode 'row' 40 600 'one'), (New-MirrorNode 'row' 40 600 'two'))
    $rtlRep = @((New-MirrorNode 'row' 480 1040 'x'), (New-MirrorNode 'row' 480 1040 'y'))
    $mo6 = Test-DaMirroredOrder -LtrNodes $ltrRep -RtlNodes $rtlRep -Width 1080
    Assert-Case 'mirror: repeated ids match by occurrence' ($mo6.Matched -eq 2 -and $mo6.Mirrored -eq 2 -and $mo6.Verdict -eq 'pass')

    # 7. clip-check answer
    $c1 = ConvertFrom-DaClipCheckJson -Json $script:clipOk
    Assert-Case 'clip: clean answer is judged' ($c1.Ok -and $c1.Judged -and $c1.OffGlass -eq 0 -and $c1.Checked -eq 9 -and $c1.Radius -eq 105)
    $c2 = ConvertFrom-DaClipCheckJson -Json ("noise`n" + $script:clipBad)
    Assert-Case 'clip: off-glass answer carries its findings' ($c2.Ok -and $c2.Judged -and $c2.OffGlass -eq 1 -and $c2.ExitCode -eq 9 -and $c2.Findings[0].Kind -eq 'OFF-GLASS')
    $c3 = ConvertFrom-DaClipCheckJson -Json $script:clipRefused
    Assert-Case 'clip: a refusal judged nothing' (-not $c3.Ok -and -not $c3.Judged -and $c3.ExitCode -eq 7 -and $c3.Reason -like '*shape*')
    $c4 = ConvertFrom-DaClipCheckJson -Json ($script:clipOk -replace '"radius":105', '"radius":0')
    Assert-Case 'clip: a display without rounded corners judged nothing' ($c4.Ok -and -not $c4.Judged -and $c4.Reason -like '*no rounded corners*')
    Assert-Case 'clip: empty and garbage answers are not ok' (-not (ConvertFrom-DaClipCheckJson -Json '').Ok -and -not (ConvertFrom-DaClipCheckJson -Json 'not json').Ok -and -not (ConvertFrom-DaClipCheckJson -Json '{ nope').Ok)

    # 8. value parsers
    Assert-Case 'font parser: null is the default' ((ConvertFrom-DaFontScaleLines -Lines @('null')).Present -eq $false -and (ConvertFrom-DaFontScaleLines -Lines @('null')).Ok)
    Assert-Case 'font parser: a number is present' ((ConvertFrom-DaFontScaleLines -Lines @('1.3')).Present -and (ConvertFrom-DaFontScaleLines -Lines @('1.3')).Numeric -eq 1.3)
    Assert-Case 'font parser: empty is the default, junk is unreadable' ((ConvertFrom-DaFontScaleLines -Lines @()).Ok -and -not (ConvertFrom-DaFontScaleLines -Lines @('abc')).Ok -and -not (ConvertFrom-DaFontScaleLines -Lines @('1,3')).Ok)
    $l1 = ConvertFrom-DaAppLocaleLines -Lines @('Locales for com.x for user 0 are [ar]')
    Assert-Case 'locale parser: one tag' ($l1.Ok -and @($l1.Tags).Count -eq 1 -and $l1.Tags[0] -eq 'ar')
    $l2 = ConvertFrom-DaAppLocaleLines -Lines @('Locales for com.x for user 0 are []')
    Assert-Case 'locale parser: empty list means no override' ($l2.Ok -and @($l2.Tags).Count -eq 0)
    $l3 = ConvertFrom-DaAppLocaleLines -Lines @('Locales for com.x for user 0 are [en_US,ar]')
    Assert-Case 'locale parser: underscores become hyphens' ($l3.Ok -and (@($l3.Tags) -join ',') -eq 'en-US,ar')
    Assert-Case 'locale parser: an error text is unreadable' (-not (ConvertFrom-DaAppLocaleLines -Lines @('Unknown command: locale')).Ok)

    # 9. state recorders and restorers against the fake device
    $script:fake = New-FakeDevice
    $o1 = Get-DaFontScaleState -Adb $script:fakeAdb
    Assert-Case 'font state: untouched device is recorded as absent' ($o1.Recorded -and -not $o1.Present)
    $threw = $false; try { Set-DaFontScale -Adb $script:fakeAdb -Original ([pscustomobject]@{ Kind = 'font_scale'; Recorded = $false }) -Scale 1.3 | Out-Null } catch { $threw = $true }
    Assert-Case 'font change refuses without a recorded original' $threw
    $threw = $false; try { Set-DaFontScale -Adb $script:fakeAdb -Original ([pscustomobject]@{ Kind = 'app_locale'; Recorded = $true }) -Scale 1.3 | Out-Null } catch { $threw = $true }
    Assert-Case 'font change refuses the wrong kind of original' $threw
    $s1 = Set-DaFontScale -Adb $script:fakeAdb -Original $o1 -Scale 1.3
    Assert-Case 'font change applies and reads back' ($s1.Ok -and $script:fake.Font -eq '1.3')
    $rb1 = Restore-DaFontScale -Adb $script:fakeAdb -Original $o1
    Assert-Case 'font restore of an absent value deletes the key' ($rb1.Ok -and $null -eq $script:fake.Font)
    $script:fake = New-FakeDevice -Font '1.15'
    $o2 = Get-DaFontScaleState -Adb $script:fakeAdb
    Set-DaFontScale -Adb $script:fakeAdb -Original $o2 -Scale 2.0 | Out-Null
    $rb2 = Restore-DaFontScale -Adb $script:fakeAdb -Original $o2
    Assert-Case 'font restore of a value writes it back' ($rb2.Ok -and $script:fake.Font -eq '1.15')
    $script:fake = New-FakeDevice -IgnoreDelete -Font '1.3'
    $o3 = [pscustomobject]@{ Kind = 'font_scale'; Recorded = $true; Present = $false; Value = $null }
    $rb3 = Restore-DaFontScale -Adb $script:fakeAdb -Original $o3
    Assert-Case 'font restore that did not read back is reported' (-not $rb3.Ok -and $rb3.Reason -like '*recorded absent*') $rb3.Reason
    Assert-Case 'font scale outside its range is refused' ($(try { Set-DaFontScale -Adb $script:fakeAdb -Original $o2 -Scale 3.5; $false } catch { $true }))
    Assert-Case 'font restore without a record is refused' (-not (Restore-DaFontScale -Adb $script:fakeAdb -Original ([pscustomobject]@{ Kind = 'font_scale'; Recorded = $false })).Ok)

    $script:fake = New-FakeDevice -Tag 'en-US'
    $lo = Get-DaAppLocaleState -Adb $script:fakeAdb -Package 'com.sza.fastmediasorter.debug'
    Assert-Case 'locale state records the original tags' ($lo.Recorded -and (@($lo.Tags) -join ',') -eq 'en-US')
    $ls = Set-DaAppLocale -Adb $script:fakeAdb -Original $lo -Locale 'ar'
    Assert-Case 'locale change applies and reads back' ($ls.Ok -and $script:fake.Tag -eq 'ar')
    $lr = Restore-DaAppLocale -Adb $script:fakeAdb -Original $lo
    Assert-Case 'locale restore puts the original back' ($lr.Ok -and $script:fake.Tag -eq 'en-US')
    $script:fake = New-FakeDevice
    $lo2 = Get-DaAppLocaleState -Adb $script:fakeAdb -Package 'com.sza.fastmediasorter.debug'
    Set-DaAppLocale -Adb $script:fakeAdb -Original $lo2 -Locale 'ar' | Out-Null
    $lr2 = Restore-DaAppLocale -Adb $script:fakeAdb -Original $lo2
    Assert-Case 'locale restore of no override clears it' ($lr2.Ok -and $script:fake.Tag -eq '')
    $script:fake = New-FakeDevice -LocaleBroken
    $lo3 = Get-DaAppLocaleState -Adb $script:fakeAdb -Package 'com.sza.fastmediasorter.debug'
    Assert-Case 'locale state is unrecorded when the service answers an error' (-not $lo3.Recorded)
    Assert-Case 'locale change refuses without a recorded original' ($(try { Set-DaAppLocale -Adb $script:fakeAdb -Original $lo3 -Locale 'ar' | Out-Null; $false } catch { $true }))
    Assert-Case 'a bad package id is refused at the boundary' ($(try { Get-DaAppLocaleState -Adb $script:fakeAdb -Package 'x; rm -rf' | Out-Null; $false } catch { $true }))

    # 10. envelope gate through the real state directory override
    $stateDir = $env:DA_STATE_DIR
    New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
    $env1 = Assert-DaAccessEnvelope -Package 'com.sza.fastmediasorter.debug'
    Assert-Case 'envelope: no checkpoint is refused' ($env1.Code -eq 2 -and $env1.Reason -like '*BeginMutation*')
    $manifestPath = Join-Path $daSuiteRoot 'backup/manifest.json'
    $mkManifest = {
        param([string]$Status, [string]$Pkg, [bool]$TrackFont)
        $settings = @([ordered]@{ namespace = 'system'; key = 'screen_off_timeout' })
        if ($TrackFont) { $settings += [ordered]@{ namespace = 'system'; key = 'font_scale' } }
        [ordered]@{ schemaVersion = 1; runId = 'b1'; verified = [ordered]@{ status = $Status }; package = [ordered]@{ name = $Pkg }; settings = $settings }
    }
    $mkPending = { param([string]$Pkg) Write-DaJson -Path (Join-Path $stateDir 'restore-pending.json') -InputObject ([ordered]@{ schemaVersion = 1; runId = 'r1'; package = $Pkg; manifestPath = $manifestPath }) }
    $pkg = 'com.sza.fastmediasorter.debug'
    Write-DaJson -Path $manifestPath -InputObject (& $mkManifest 'verified' $pkg $true)
    & $mkPending $pkg
    $env2 = Assert-DaAccessEnvelope -Package $pkg
    Assert-Case 'envelope: verified backup tracking font scale is accepted' ($env2.Code -eq 0) $env2.Reason
    Write-DaJson -Path $manifestPath -InputObject (& $mkManifest 'unverified' $pkg $true)
    Assert-Case 'envelope: unverified backup is refused' ((Assert-DaAccessEnvelope -Package $pkg).Code -eq 2)
    Write-DaJson -Path $manifestPath -InputObject (& $mkManifest 'verified' $pkg $false)
    Assert-Case 'envelope: a backup that does not track font scale is refused' (((Assert-DaAccessEnvelope -Package $pkg).Reason) -like '*font_scale*')
    Write-DaJson -Path $manifestPath -InputObject (& $mkManifest 'verified' 'com.other' $true)
    Assert-Case 'envelope: another package is refused' ((Assert-DaAccessEnvelope -Package $pkg).Code -eq 2)
    Write-DaJson -Path $manifestPath -InputObject (& $mkManifest 'verified' $pkg $true)
    & $mkPending 'com.other'
    Assert-Case 'envelope: a checkpoint for another package is refused' ((Assert-DaAccessEnvelope -Package $pkg).Code -eq 2)
    Remove-Item -LiteralPath (Join-Path $stateDir 'restore-pending.json') -Force

    # 11. the probe end to end against the fake device
    $allowed = @(
        '^exec-out uiautomator dump /dev/tty$',
        '^shell input keyevent KEYCODE_DPAD_DOWN$',
        '^shell settings get system font_scale$',
        '^shell settings put system font_scale [0-9.]+$',
        '^shell settings delete system font_scale$',
        '^shell cmd locale get-app-locales [A-Za-z0-9_.]+$',
        '^shell cmd locale set-app-locales [A-Za-z0-9_.]+( --locales [A-Za-z-]+)?$'
    )
    $recordPath = Get-DaAccessOriginalPath
    $pr = Invoke-FakeProbe -Fake (New-FakeDevice)
    Assert-Case 'probe: findings give code 1' ($pr.Code -eq 1 -and $pr.Verdict -eq 'finding') $pr.Reason
    $kinds = @($pr.Findings | ForEach-Object { $_.Kind })
    Assert-Case 'probe: unnamed node found' ($kinds -contains 'unnamed' -and $pr.Baseline.Unnamed.Count -eq 1 -and $pr.Baseline.Unnamed[0].ResId -eq 'p:id/btnBack') ($kinds -join ',')
    Assert-Case 'probe: state gap found' ($kinds -contains 'state-not-exposed' -and $pr.Baseline.StateGaps.Count -eq 1)
    Assert-Case 'probe: focus order measured with a clean traversal' ($pr.FocusOrder.Distinct -eq 4 -and $pr.FocusOrder.Findings.Count -eq 0 -and $pr.FocusOrder.Presses -eq 6) "distinct=$($pr.FocusOrder.Distinct) presses=$($pr.FocusOrder.Presses)"
    Assert-Case 'probe: focus sequence follows the document order' ((@($pr.FocusOrder.Sequence | Where-Object { $_.Step -gt 0 -and $_.Step -le 4 } | ForEach-Object { $_.ResId }) -join ',') -eq 'p:id/btnBack,p:id/btnMenu,p:id/rowToggle,p:id/btnSave')
    Assert-Case 'probe: rtl walk measured and mirrored' ($pr.Rtl.Status -eq 'measured' -and $pr.Rtl.MirroredOrder.Verdict -eq 'pass' -and $pr.Rtl.MirroredOrder.NotMirrored.Count -eq 0) "$($pr.Rtl.Status) $($pr.Rtl.Reason)"
    Assert-Case 'probe: both font scales measured' (@($pr.FontScales).Count -eq 2 -and $pr.FontScales[0].Applied -and $pr.FontScales[1].Applied)
    Assert-Case 'probe: large text findings (edge and glass)' ($kinds -contains 'outside-display' -and $kinds -contains 'off-glass')
    Assert-Case 'probe: font scale restored and read back' ($pr.Restore.FontScale.Ok -and $null -eq $script:fake.Font)
    Assert-Case 'probe: locale restored and read back' ($pr.Restore.Locale.Ok -and $script:fake.Tag -eq '')
    Assert-Case 'probe: the durable record is gone after a verified restore' (-not (Test-Path -LiteralPath $recordPath))
    Assert-Case 'probe: never claims a TalkBack pass' ($pr.TalkBackPass -eq $false -and $pr.Statement -like '*not claimed*' -and (@($pr.Open | Where-Object { $_ -like 'TalkBack pass*' }).Count -eq 1) -and $pr.Rung -eq 'V')
    $outside = @($script:fake.Calls | Where-Object { $c = $_; -not (@($allowed | Where-Object { $c -match $_ }).Count) })
    Assert-Case 'probe: only dump, direction key, font scale and locale calls were sent' ($outside.Count -eq 0) ($outside -join ' | ')
    Assert-Case 'probe: no activation key and no other settings namespace was touched' (@($script:fake.Calls | Where-Object { $_ -match 'ENTER|pm |am |wm |global|secure|uninstall|clear' }).Count -eq 0)

    $prDevice = New-FakeDevice -Font '1.15' -Tag 'en-US'
    $pr2 = Invoke-FakeProbe -Fake $prDevice
    Assert-Case 'probe: a recorded original value and locale come back' ($prDevice.Font -eq '1.15' -and $prDevice.Tag -eq 'en-US' -and $pr2.Restore.FontScale.Ok -and $pr2.Restore.Locale.Ok)

    $prNo = New-FakeDevice
    $pr3 = Invoke-FakeProbe -Fake $prNo -Gate $script:gateNo
    Assert-Case 'probe: a refused envelope sends no adb call' ($pr3.Code -eq 2 -and $pr3.Verdict -eq 'refused' -and $prNo.Calls.Count -eq 0 -and $pr3.Reason -like '*checkpoint*')
    $prSer = New-FakeDevice
    $pr4 = Invoke-FakeProbe -Fake $prSer -Serial 'RFCR110NBQJ'
    Assert-Case 'probe: a non-emulator serial is refused before any adb call' ($pr4.Code -eq 2 -and $prSer.Calls.Count -eq 0)
    $pr4b = Invoke-FakeProbe -Fake (New-FakeDevice) -Serial 'emulator-5554; rm'
    Assert-Case 'probe: a malformed emulator serial is refused' ($pr4b.Code -eq 2)

    $prLoc = New-FakeDevice -LocaleBroken
    $pr5 = Invoke-FakeProbe -Fake $prLoc
    Assert-Case 'probe: an unreadable locale is never changed' (@($prLoc.Calls | Where-Object { $_ -like '*set-app-locales*' }).Count -eq 0 -and $pr5.Rtl.Status -eq 'skipped')
    Assert-Case 'probe: the text-size walk still runs and restores' (@($pr5.FontScales).Count -eq 2 -and $null -eq $prLoc.Font)
    Assert-Case 'probe: the skipped walk is listed as open' (@($pr5.Open | Where-Object { $_ -like 'RTL*skipped*' }).Count -eq 1)

    $prStuck = New-FakeDevice -IgnoreDelete
    $pr6 = Invoke-FakeProbe -Fake $prStuck
    Assert-Case 'probe: a restore that does not read back is code 2' ($pr6.Code -eq 2 -and $pr6.Reason -like 'restore incomplete*' -and -not $pr6.Restore.FontScale.Ok)
    Assert-Case 'probe: the durable record is kept after a failed restore' (Test-Path -LiteralPath $recordPath)
    $rec = Invoke-DaAccessRecover -Adb $script:fakeAdb
    Assert-Case 'recover: a stuck restore stays incomplete and keeps the record' (-not $rec.Ok -and (Test-Path -LiteralPath $recordPath))
    $script:fake.IgnoreDelete = $false
    $rec2 = Invoke-DaAccessRecover -Adb $script:fakeAdb
    Assert-Case 'recover: restores from the record and removes it' ($rec2.Ok -and $null -eq $script:fake.Font -and -not (Test-Path -LiteralPath $recordPath)) ($rec2.Steps -join ';')
    Assert-Case 'recover: nothing to do without a record' ((Invoke-DaAccessRecover -Adb $script:fakeAdb).Ok)

    $prTree = New-FakeDevice -TreeBroken
    $pr7 = Invoke-FakeProbe -Fake $prTree
    Assert-Case 'probe: an unreadable tree is code 2 and changes nothing' ($pr7.Code -eq 2 -and $pr7.Reason -like '*unreadable*' -and @($prTree.Calls | Where-Object { $_ -match 'put|delete|set-app-locales' }).Count -eq 0)

    $prMis = New-FakeDevice -RtlNotMirrored
    $pr8 = Invoke-FakeProbe -Fake $prMis
    Assert-Case 'probe: an unmirrored layout is a finding' (@($pr8.Findings | Where-Object { $_.Kind -eq 'not-mirrored' }).Count -eq 1 -and $pr8.Rtl.MirroredOrder.Verdict -eq 'finding')

    $clipRefusedRunner = { param($scale) $script:clipRefused }
    $pr9 = Invoke-FakeProbe -Fake (New-FakeDevice) -Clip $clipRefusedRunner
    Assert-Case 'probe: a refused clip check is open, not a pass' (@($pr9.Open | Where-Object { $_ -like 'font scale*clip check judged nothing*' }).Count -eq 2 -and -not (@($pr9.Findings | ForEach-Object { $_.Kind }) -contains 'off-glass'))

    $prThrow = New-FakeDevice -Font '1.15' -Tag 'en-US'
    $pr11 = Invoke-FakeProbe -Fake $prThrow -Clip { param($scale) throw 'clip runner exploded' }
    Assert-Case 'probe: an exception mid-walk restores both values and is code 2' ($pr11.Code -eq 2 -and $pr11.Reason -like '*exploded*' -and $prThrow.Font -eq '1.15' -and $prThrow.Tag -eq 'en-US' -and $pr11.Restore.FontScale.Ok -and $pr11.Restore.Locale.Ok)
    Assert-Case 'probe: the record of a restored run is removed after an exception' (-not (Test-Path -LiteralPath $recordPath))

    $pr10 = Invoke-DaAccessProbe -Serial 'emulator-5554' -Adb $script:fakeAdb -ClipCheck $script:clipRunner -EnvelopeGate $script:gateOk -FontScales @(3.0) -SettleMs 0
    Assert-Case 'probe: a font scale outside the range is refused up front' ($pr10.Code -eq 2 -and $pr10.Reason -like '*outside*')

    # 12. message copy
    $r = Test-DaMessageCopy -Message @(
        [pscustomobject]@{ Id = 'raw'; Kind = 'error-dialog'; Text = 'java.net.SocketTimeoutException: timeout' },
        [pscustomobject]@{ Id = 'smb'; Kind = 'error'; Text = 'SMB STATUS_BAD_NETWORK_NAME' },
        [pscustomobject]@{ Id = 'good'; Kind = 'error-network'; Text = "Couldn't connect - check the server address and try again." },
        [pscustomobject]@{ Id = 'nostep'; Kind = 'error-dialog'; Text = "Couldn't save the file." },
        [pscustomobject]@{ Id = 'code'; Kind = 'error'; Text = 'Error 500' },
        [pscustomobject]@{ Id = 'inline'; Kind = 'error'; Text = "Couldn't load the list because the server did not answer in time." },
        [pscustomobject]@{ Id = 'terse'; Kind = 'error'; Text = 'Failed' },
        [pscustomobject]@{ Id = 'tofail'; Kind = 'error-dialog'; Text = 'Failed to open the file.' },
        [pscustomobject]@{ Id = 'sure'; Kind = 'confirm'; Text = 'Are you sure?' },
        [pscustomobject]@{ Id = 'sure2'; Kind = 'confirm'; Text = 'Are you sure you want to delete?' },
        [pscustomobject]@{ Id = 'conseq'; Kind = 'confirm'; Text = 'This will remove all saved connections. You can add them again later.' },
        [pscustomobject]@{ Id = 'digits'; Kind = 'confirm'; Text = 'Delete 12 files?' },
        [pscustomobject]@{ Id = 'named'; Kind = 'confirm'; Text = 'Delete this file?' },
        [pscustomobject]@{ Id = 'boiler'; Kind = 'success'; Text = 'The operation has been completed successfully.' },
        [pscustomobject]@{ Id = 'opdone'; Kind = 'success'; Text = 'Operation completed.' },
        [pscustomobject]@{ Id = 'done'; Kind = 'success'; Text = 'Done.' },
        [pscustomobject]@{ Id = 'emoji'; Kind = 'toast'; Text = ('Moved 3 files ' + [char]0xD83D + [char]0xDE00) },
        [pscustomobject]@{ Id = 'details'; Kind = 'error-dialog'; Text = "Couldn't connect - check the server address."; Details = 'java.net.SocketTimeoutException: timeout' },
        [pscustomobject]@{ Id = 'button'; Kind = 'error-dialog'; Text = "Couldn't connect."; Buttons = @('Retry', 'Cancel') },
        [pscustomobject]@{ Id = 'empty'; Kind = 'empty'; Text = 'No items found.' },
        [pscustomobject]@{ Id = 'blank'; Kind = 'error'; Text = '' }
    )
    $byId = @{}
    foreach ($x in $r.Results) { $byId[$x.Id] = $x }
    $st = { param($id, $item) (@($byId[$id].Checks | Where-Object { $_.Item -eq $item }) | Select-Object -First 1).Status }
    Assert-Case 'copy: raw exception text fails' ((& $st 'raw' 'no-raw-exception') -eq 'fail')
    Assert-Case 'copy: a status constant fails' ((& $st 'smb' 'no-raw-exception') -eq 'fail')
    Assert-Case 'copy: an error code fails' ((& $st 'code' 'no-raw-exception') -eq 'fail')
    Assert-Case 'copy: a human error passes both error checks' ((& $st 'good' 'no-raw-exception') -eq 'pass' -and (& $st 'good' 'error-next-step') -eq 'pass')
    Assert-Case 'copy: an error dialog with no next step fails' ((& $st 'nostep' 'error-next-step') -eq 'fail')
    Assert-Case 'copy: "failed to open" is not read as a next step' ((& $st 'tofail' 'error-next-step') -eq 'fail')
    Assert-Case 'copy: a long inline error with no step is left to the reviewer' ((& $st 'inline' 'error-next-step') -eq 'manual')
    Assert-Case 'copy: a terse inline error fails' ((& $st 'terse' 'error-next-step') -eq 'fail')
    Assert-Case 'copy: a bare confirmation fails' ((& $st 'sure' 'confirm-states-consequence') -eq 'fail' -and (& $st 'sure2' 'confirm-states-consequence') -eq 'fail')
    Assert-Case 'copy: a confirmation with a consequence passes' ((& $st 'conseq' 'confirm-states-consequence') -eq 'pass' -and (& $st 'digits' 'confirm-states-consequence') -eq 'pass')
    Assert-Case 'copy: a short question naming the action is manual' ((& $st 'named' 'confirm-states-consequence') -eq 'manual')
    Assert-Case 'copy: operation-completed-successfully fails' ((& $st 'boiler' 'no-success-boilerplate') -eq 'fail' -and (& $st 'opdone' 'no-success-boilerplate') -eq 'fail')
    Assert-Case 'copy: "Done." passes' ((& $st 'done' 'no-success-boilerplate') -eq 'pass')
    Assert-Case 'copy: an emoji image fails' ((& $st 'emoji' 'no-emoji-image') -eq 'fail')
    Assert-Case 'copy: details are not scanned as the headline' ((& $st 'details' 'no-raw-exception') -eq 'pass')
    Assert-Case 'copy: an action button counts as a next step' ((& $st 'button' 'error-next-step') -eq 'pass')
    Assert-Case 'copy: an empty state gets the invitation item as manual' ((& $st 'empty' 'empty-state-invitation') -eq 'manual')
    Assert-Case 'copy: no captured text is manual and not scored' ((& $st 'blank' 'text-captured') -eq 'manual' -and $byId['blank'].Failed -eq 0)
    $manualOk = $true
    foreach ($x in $r.Results) {
        foreach ($ch in @($x.Checks | Where-Object { $_.Status -eq 'manual' })) { if ($ch.Rung -ne 'R') { $manualOk = $false } }
        foreach ($ch in @($x.Checks | Where-Object { $_.Status -in @('pass', 'fail') })) { if ($ch.Rung -ne 'V') { $manualOk = $false } }
        if (@($x.Checks | Where-Object { $_.Item -in @('locale-parity', 'tone-and-voice', 'fits-360dp') -and $_.Status -eq 'manual' }).Count -ne 3 -and $x.Id -ne 'blank') { $manualOk = $false }
    }
    Assert-Case 'copy: manual items carry rung R, scored items rung V, judgement items always listed' $manualOk
    Assert-Case 'copy: summary counts failures and manual items separately' ($r.Failed -eq (@($r.Results | Measure-Object -Property Failed -Sum).Sum) -and $r.Manual -gt 0 -and $r.Policy -like '*COMMUNICATION_POLICY.md section 6*')
    Assert-Case 'copy: a plain string is accepted' ((Test-DaMessageCopy -Message 'Done.').Messages -eq 1 -and (Test-DaMessageCopy -Message @()).Messages -eq 0)

    # 12b. ru, uk, ar
    $rtext = @{
        ruOk     = U '~u041d~u0435 ~u0443~u0434~u0430~u043b~u043e~u0441~u044c ~u043f~u043e~u0434~u043a~u043b~u044e~u0447~u0438~u0442~u044c~u0441~u044f. ~u041f~u0440~u043e~u0432~u0435~u0440~u044c~u0442~u0435 ~u0430~u0434~u0440~u0435~u0441 ~u0441~u0435~u0440~u0432~u0435~u0440~u0430 ~u0438 ~u043f~u043e~u0432~u0442~u043e~u0440~u0438~u0442~u0435 ~u043f~u043e~u043f~u044b~u0442~u043a~u0443.'
        ukOk     = U '~u041d~u0435 ~u0432~u0434~u0430~u043b~u043e~u0441~u044f ~u043f~u0456~u0434~u043a~u043b~u044e~u0447~u0438~u0442~u0438~u0441~u044f. ~u041f~u0435~u0440~u0435~u0432~u0456~u0440~u0442~u0435 ~u0430~u0434~u0440~u0435~u0441~u0443 ~u0441~u0435~u0440~u0432~u0435~u0440~u0430 ~u0442~u0430 ~u043f~u043e~u0432~u0442~u043e~u0440~u0456~u0442~u044c ~u0441~u043f~u0440~u043e~u0431~u0443.'
        arOk     = U '~u062a~u0639~u0630~u0631 ~u0627~u0644~u0627~u062a~u0635~u0627~u0644. ~u062a~u062d~u0642~u0642 ~u0645~u0646 ~u0639~u0646~u0648~u0627~u0646 ~u0627~u0644~u062e~u0627~u062f~u0645 ~u0648~u062d~u0627~u0648~u0644 ~u0645~u0631~u0629 ~u0623~u062e~u0631~u0649.'
        ruBare   = U '~u041d~u0435 ~u0443~u0434~u0430~u043b~u043e~u0441~u044c ~u0441~u043e~u0445~u0440~u0430~u043d~u0438~u0442~u044c ~u0444~u0430~u0439~u043b.'
        ruSucc   = U '~u041e~u043f~u0435~u0440~u0430~u0446~u0438~u044f ~u0443~u0441~u043f~u0435~u0448~u043d~u043e ~u0437~u0430~u0432~u0435~u0440~u0448~u0435~u043d~u0430.'
        ukSucc   = U '~u041e~u043f~u0435~u0440~u0430~u0446~u0456~u044f ~u0443~u0441~u043f~u0456~u0448~u043d~u043e ~u0432~u0438~u043a~u043e~u043d~u0430~u043d~u0430.'
        arSucc   = U '~u062a~u0645~u062a ~u0627~u0644~u0639~u0645~u0644~u064a~u0629 ~u0628~u0646~u062c~u0627~u062d'
        ruSure   = U '~u0412~u044b ~u0443~u0432~u0435~u0440~u0435~u043d~u044b?'
        ukSure   = U '~u0412~u0438 ~u0432~u043f~u0435~u0432~u043d~u0435~u043d~u0456?'
        arSure   = U '~u0647~u0644 ~u0623~u0646~u062a ~u0645~u062a~u0623~u0643~u062f~u061f'
        ruConseq = U '~u0412~u0441~u0435 ~u0441~u043e~u0445~u0440~u0430~u043d~u0451~u043d~u043d~u044b~u0435 ~u043f~u043e~u0434~u043a~u043b~u044e~u0447~u0435~u043d~u0438~u044f ~u0431~u0443~u0434~u0443~u0442 ~u0443~u0434~u0430~u043b~u0435~u043d~u044b. ~u0418~u0445 ~u043c~u043e~u0436~u043d~u043e ~u0434~u043e~u0431~u0430~u0432~u0438~u0442~u044c ~u0441~u043d~u043e~u0432~u0430.'
        ukConseq = U '~u0423~u0441~u0456 ~u0437~u0431~u0435~u0440~u0435~u0436~u0435~u043d~u0456 ~u043f~u0456~u0434~u043a~u043b~u044e~u0447~u0435~u043d~u043d~u044f ~u0431~u0443~u0434~u0435 ~u0432~u0438~u0434~u0430~u043b~u0435~u043d~u043e. ~u0407~u0445 ~u043c~u043e~u0436~u043d~u0430 ~u0434~u043e~u0434~u0430~u0442~u0438 ~u0437~u043d~u043e~u0432~u0443.'
        arConseq = U '~u0633~u064a~u062a~u0645 ~u062d~u0630~u0641 ~u062c~u0645~u064a~u0639 ~u0627~u0644~u0627~u062a~u0635~u0627~u0644~u0627~u062a ~u0627~u0644~u0645~u062d~u0641~u0648~u0638~u0629.'
    }
    $r2 = Test-DaMessageCopy -Message @(
        [pscustomobject]@{ Id = 'ruOk'; Kind = 'error-network'; Text = $rtext.ruOk },
        [pscustomobject]@{ Id = 'ukOk'; Kind = 'error-network'; Text = $rtext.ukOk },
        [pscustomobject]@{ Id = 'arOk'; Kind = 'error-network'; Text = $rtext.arOk },
        [pscustomobject]@{ Id = 'ruBare'; Kind = 'error-dialog'; Text = $rtext.ruBare },
        [pscustomobject]@{ Id = 'ruSucc'; Kind = 'success'; Text = $rtext.ruSucc },
        [pscustomobject]@{ Id = 'ukSucc'; Kind = 'success'; Text = $rtext.ukSucc },
        [pscustomobject]@{ Id = 'arSucc'; Kind = 'success'; Text = $rtext.arSucc },
        [pscustomobject]@{ Id = 'ruSure'; Kind = 'confirm'; Text = $rtext.ruSure },
        [pscustomobject]@{ Id = 'ukSure'; Kind = 'confirm'; Text = $rtext.ukSure },
        [pscustomobject]@{ Id = 'arSure'; Kind = 'confirm'; Text = $rtext.arSure },
        [pscustomobject]@{ Id = 'ruConseq'; Kind = 'confirm'; Text = $rtext.ruConseq },
        [pscustomobject]@{ Id = 'ukConseq'; Kind = 'confirm'; Text = $rtext.ukConseq },
        [pscustomobject]@{ Id = 'arConseq'; Kind = 'confirm'; Text = $rtext.arConseq }
    )
    $by2 = @{}
    foreach ($x in $r2.Results) { $by2[$x.Id] = $x }
    $st2 = { param($id, $item) (@($by2[$id].Checks | Where-Object { $_.Item -eq $item }) | Select-Object -First 1).Status }
    foreach ($l in @('ru', 'uk', 'ar')) {
        Assert-Case "copy $l`: an error with a next step passes" ((& $st2 "${l}Ok" 'error-next-step') -eq 'pass' -and (& $st2 "${l}Ok" 'no-raw-exception') -eq 'pass')
        Assert-Case "copy $l`: success boilerplate fails" ((& $st2 "${l}Succ" 'no-success-boilerplate') -eq 'fail')
        Assert-Case "copy $l`: a bare confirmation fails" ((& $st2 "${l}Sure" 'confirm-states-consequence') -eq 'fail')
        Assert-Case "copy $l`: a confirmation with a consequence passes" ((& $st2 "${l}Conseq" 'confirm-states-consequence') -eq 'pass')
    }
    Assert-Case 'copy ru: an error dialog with no next step fails' ((& $st2 'ruBare' 'error-next-step') -eq 'fail')
}
finally {
    $env:DA_STATE_DIR = $daSavedState
    if (Test-Path -LiteralPath $daSuiteRoot) { Remove-Item -LiteralPath $daSuiteRoot -Recurse -Force -ErrorAction SilentlyContinue }
}

Write-Host ("da-access.tests: {0} passed, {1} failed" -f $script:daPassed, $script:daFailed.Count)
if ($script:daFailed.Count -gt 0) { exit 1 }
exit 0
