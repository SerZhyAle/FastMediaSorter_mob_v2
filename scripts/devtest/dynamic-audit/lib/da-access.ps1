#requires -Version 7.0
<#
.SYNOPSIS
    Understanding and access layer of the S4166 dynamic-audit stand: switch cards, the owner's protocol, accessibility, RTL and text-size preconditions, message-copy review.

.DESCRIPTION
    Dot-source after lib/da-common.ps1 (the envelope gate also needs lib/da-state.ps1). Functions only:
    nothing is assigned at script scope and nothing here calls exit. A source that cannot be read
    THROWS, and the caller (the da.ps1 facade, a scratch driver, a test) maps that to its own exit code.

    U CARDS (strategic sections 5.4 and 6 item 5)
      New-DaUCards            one card per settings section that holds a TOGGLE_ROW entry; the card is the
                              first key of the section in ordinal order. Runtime fields start as null and
                              every result cell (en, ru, uk, ar) starts open.
      New-DaUProtocolText     the owner's execution sheet as Markdown text.
      Write-DaUCards          writes audit/ucards.json and reports/u-protocol.md, byte-stable on re-run.

    ACCESS PROBE (strategic section 5.4, rung V)
      Invoke-DaAccessProbe    for the screen in front: clickable or focusable nodes with no name, nodes whose
                              state is not exposed, the D-pad traversal order from repeated direction keys,
                              the same screen under the ar app locale with mirrored order checked from
                              bounds, and under font scales 1.3 and 2.0 with the existing clip check.
      Pure parsers, tested with synthetic dumps: ConvertFrom-DaUiTreeXml, Get-DaUnnamedNodes,
      Get-DaStateGaps, Get-DaFocusOrderReport, Test-DaMirroredOrder, ConvertFrom-DaClipCheckJson,
      ConvertFrom-DaFontScaleLines, ConvertFrom-DaAppLocaleLines.
      State changers (the only functions that change the device): Get-/Set-/Restore-DaFontScale and
      Get-/Set-/Restore-DaAppLocale. A Set-* function refuses without the object its Get-* sibling
      recorded, and a Restore-* function reads the value back and reports whether it matched.

      WHAT THE PROBE NEVER CLAIMS. It reports measured preconditions for screen-reader use. Whether
      TalkBack reads a screen correctly needs the reader and a participant, so the result carries
      TalkBackPass = false by construction and lists that pass as open.

      ENVELOPE. The probe runs only when Assert-DaAccessEnvelope finds the restore checkpoint of a
      verified backup that tracks system/font_scale (the facade writes it with BeginMutation). The per-app
      locale is not a tracked setting of the backup, so the probe writes its originals to
      state/access-original.json before the first change and deletes that file only after both values
      read back as recorded; Invoke-DaAccessRecover restores from it after an interrupted run.

    MESSAGE COPY (docs/COMMUNICATION_POLICY.md section 6, strategic section 3.2)
      Test-DaMessageCopy      the mechanically decidable items of the tone checklist are scored from the
                              captured text; every item that needs judgement is returned as manual with
                              rung R and is never scored. The audit creates no new strings.

    DEVICE ACCESS. The adb runner is a scriptblock parameter taking a string array and returning an object
    with ExitCode, Lines and Stderr (Invoke-DaAdb has that shape), so every device-facing function is
    exercised without a device by a recorded-output fake. The clip check is likewise injectable; its
    default runs scripts/devtest/adb.ps1 clip-check -Json.
#>

function Get-DaUCardLanguages {
    <#
    .SYNOPSIS
        The four languages of the U protocol: en, ru, uk and ar (ar is the RTL walk).
    #>
    return @('en', 'ru', 'uk', 'ar')
}

function Get-DaUCardAttempts {
    <#
    .SYNOPSIS
        Planned attempts per scenario (owner's decision, strategic section 6 item 5).
    #>
    return 15
}

function Get-DaAccessStatement {
    <#
    .SYNOPSIS
        The sentence every access result carries about what it does not prove.
    #>
    return 'Measured preconditions for screen-reader use, read from the UI tree on one emulator. A TalkBack pass is not claimed: screen-reader behaviour needs the reader and a participant.'
}

# ---------------------------------------------------------------------------------------------
# U cards and the owner's protocol
# ---------------------------------------------------------------------------------------------

function New-DaUserTask {
    <#
    .SYNOPSIS
        One plain imperative sentence built from the English switch title; direction-neutral on purpose.
    .PARAMETER TitleEn
        English title of the switch row.
    #>
    param([Parameter(Mandatory)][string]$TitleEn)
    $title = ($TitleEn -replace '\s+', ' ').Trim()
    return ('Find the setting "{0}" and switch it to the opposite of its current state.' -f $title)
}

function New-DaUCardResults {
    <#
    .SYNOPSIS
        The four result cells of a card, every one open with its reason, owner and next step.
    .PARAMETER CardId
        Card id the next step names.
    #>
    param([Parameter(Mandatory)][string]$CardId)
    $results = [ordered]@{}
    foreach ($lang in (Get-DaUCardLanguages)) {
        $direction = if ($lang -eq 'ar') { ' (right-to-left)' } else { '' }
        $results[$lang] = [ordered]@{
            status           = 'open'
            reason           = 'not run: the owner executes the protocol on the stand emulator and one participant is non-representative'
            owner            = 'owner'
            nextStep         = ('run scenario {0} in {1}{2} from reports/u-protocol.md and record the series' -f $CardId, $lang, $direction)
            attemptsPlanned  = (Get-DaUCardAttempts)
            attemptsRecorded = 0
            representative   = $false
        }
    }
    return $results
}

function New-DaUCards {
    <#
    .SYNOPSIS
        Builds the U switch cards from the settings manifest: one TOGGLE_ROW per sectionId.
    .DESCRIPTION
        The pick is the first key of the section in ordinal order, so the same manifest always gives
        the same cards. The manifest holds no default and no flavor gate, therefore valueBefore stays
        null and is read at run time from the backed-up device state. Throws when the manifest is
        absent, malformed, or holds no TOGGLE_ROW entry.
    .PARAMETER RepoRoot
        Repository root.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RepoRoot)
    $root = (Resolve-Path -LiteralPath $RepoRoot).Path
    $rel = 'docs/settings/settings-manifest.json'
    $path = Join-Path $root $rel
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "da-access: settings manifest not found: $rel" }
    $text = [System.IO.File]::ReadAllText($path, [System.Text.UTF8Encoding]::new($false))
    try { $manifest = $text | ConvertFrom-Json -Depth 64 }
    catch { throw "da-access: settings manifest is not valid JSON: $($_.Exception.Message)" }
    if ($null -eq $manifest -or $null -eq $manifest.entries) { throw "da-access: $rel has no entries list" }

    $bySection = [System.Collections.Generic.SortedDictionary[string, object]]::new([System.StringComparer]::Ordinal)
    $counts = [System.Collections.Generic.Dictionary[string, int]]::new([System.StringComparer]::Ordinal)
    $keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $rows = 0
    foreach ($e in @($manifest.entries)) {
        if ([string]$e.kind -ne 'TOGGLE_ROW') { continue }
        foreach ($field in @('key', 'sectionId', 'destination', 'titleEn', 'titleRu', 'titleUk')) {
            if ([string]::IsNullOrWhiteSpace([string]$e.$field)) { throw "da-access: a TOGGLE_ROW entry of $rel lacks '$field' (key '$($e.key)')" }
        }
        $rows++
        $sid = [string]$e.sectionId
        [void]$keys.Add([string]$e.key)
        if (-not $bySection.ContainsKey($sid)) { $bySection[$sid] = $e; $counts[$sid] = 1; continue }
        $counts[$sid] = $counts[$sid] + 1
        if ([string]::CompareOrdinal([string]$e.key, [string]$bySection[$sid].key) -lt 0) { $bySection[$sid] = $e }
    }
    if ($rows -eq 0) { throw "da-access: $rel holds no TOGGLE_ROW entry" }

    $cards = [System.Collections.Generic.List[object]]::new()
    foreach ($sid in $bySection.Keys) {
        $e = $bySection[$sid]
        $id = "U-$sid"
        $cards.Add([ordered]@{
                id                   = $id
                key                  = [string]$e.key
                sectionId            = $sid
                destination          = [string]$e.destination
                titleEn              = [string]$e.titleEn
                titleRu              = [string]$e.titleRu
                titleUk              = [string]$e.titleUk
                userTask             = (New-DaUserTask -TitleEn ([string]$e.titleEn))
                sectionToggleCount   = $counts[$sid]
                valueBefore          = $null
                prediction           = $null
                effectAfter          = $null
                persistsAfterRestart = $null
                dependencies         = $null
                wayBack              = $null
                reversibilityLimit   = $null
                results              = (New-DaUCardResults -CardId $id)
            })
    }

    $lf = $text -replace "`r`n", "`n"
    $sha = [System.Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([System.Text.UTF8Encoding]::new($false).GetBytes($lf))).ToLowerInvariant()
    return [ordered]@{
        schemaVersion = 1
        kind          = 'da-u-cards'
        generatedBy   = 'scripts/devtest/dynamic-audit/lib/da-access.ps1 New-DaUCards'
        source        = [ordered]@{
            path       = $rel
            sha256     = $sha
            toggleRows = $rows
            uniqueKeys = $keys.Count
            sections   = $bySection.Count
        }
        selectionRule = [ordered]@{
            entryKind           = 'TOGGLE_ROW'
            perSection          = 1
            pick                = 'lowest key in ordinal order'
            languages           = @(Get-DaUCardLanguages)
            attemptsPerScenario = (Get-DaUCardAttempts)
        }
        participant   = [ordered]@{
            who            = 'owner'
            count          = 1
            representative = $false
            note           = 'one participant is non-representative and cannot prove the 90 percent threshold; every result cell stays open'
        }
        runtimeFields = [ordered]@{
            valueBefore          = 'read at run time from the backed-up device state; the manifest carries no default'
            prediction           = 'written by the participant before the press'
            effectAfter          = 'observed after the press, immediately and after leaving the screen'
            persistsAfterRestart = 'observed after a force-stop and relaunch'
            dependencies         = 'other switches or screens that became unavailable or changed, and the message shown'
            wayBack              = 'how the participant returned the switch to the previous value without help'
            reversibilityLimit   = 'what the way back cannot restore, or null when it restores everything'
        }
        cards         = $cards.ToArray()
    }
}

function New-DaUProtocolText {
    <#
    .SYNOPSIS
        Renders the owner's execution sheet (Markdown, LF) from a card document built by New-DaUCards.
    .PARAMETER Cards
        The document returned by New-DaUCards.
    #>
    param([Parameter(Mandatory)]$Cards)
    $sb = [System.Text.StringBuilder]::new(8192)
    $add = { param([string]$line) [void]$sb.Append($line).Append("`n") }
    $langs = (Get-DaUCardLanguages) -join ', '
    $attempts = Get-DaUCardAttempts
    $cardList = @($Cards.cards)
    $cells = $cardList.Count * (Get-DaUCardLanguages).Count

    & $add '# S4166 - U protocol: the owner''s execution sheet'
    & $add ''
    & $add '**Generated from:** `PLAN/S4166_deep-dynamic-audit/audit/ucards.json` (regenerate with the `BuildUCards` verb; never edit by hand)'
    & $add ('**Source manifest:** `{0}` sha256 `{1}`' -f $Cards.source.path, $Cards.source.sha256)
    & $add '**Participant:** the owner, alone'
    & $add '**Result class:** non-representative'
    & $add ('**Cards:** {0}, one TOGGLE_ROW per settings section ({1} rows in the manifest, {2} not sampled in the first pass)' -f $cardList.Count, $Cards.source.toggleRows, ($Cards.source.toggleRows - $cardList.Count))
    & $add ('**Cells:** {0} ({1} cards x {2} languages), {3} attempts planned for each' -f $cells, $cardList.Count, (Get-DaUCardLanguages).Count, $attempts)
    & $add ''
    & $add '---'
    & $add ''
    & $add '## 1. What this sheet can and cannot prove'
    & $add ''
    & $add '- One participant is non-representative: the predictions say how this reader understood each switch, not how users will.'
    & $add ('- {0} attempts per scenario cannot prove the 90 percent threshold. Even {0} correct out of {0} gives an exact one-sided 95 percent lower bound of about 82 percent (0.05 to the power 1/{0}); with zero failures at least 29 attempts are needed to clear 90 percent at that confidence.' -f $attempts)
    & $add '- Attempts of one person are not independent trials: from the second attempt on the owner knows the effect, so a series measures the repeatability of one reader and not discovery.'
    & $add '- Every result cell therefore stays `open`. A recorded series changes `attemptsRecorded` and the counters and never `status`; closing a U cell needs external participants and is a separate owner decision.'
    & $add '- TalkBack is the screen reader of this stand. The scripted access probe reports preconditions only and never claims a TalkBack pass; only the pass in section 6 observes one.'
    & $add ''
    & $add '## 2. Before the session'
    & $add ''
    & $add '- Target: an emulator only (serial `emulator-<N>`). The owner''s phone and the headset are never used (`docs/DEVICE_FLEET.md`).'
    & $add '- State: a verified backup exists and the restore checkpoint is written - `da.ps1 -Verb Backup`, `-Verb VerifyBackup`, `-Verb BeginMutation`; `-Verb Preflight` answers OK.'
    & $add '- Data: synthetic fixtures only, no real account and no network source. A switch whose effect needs a network source is recorded as unavailable with the reason and is never guessed.'
    & $add '- Build: the debug package of the variant under test, standard first.'
    & $add ('- Languages: {0}. The list is the owner''s decision of strategic section 6 item 5.' -f $langs)
    & $add ''
    & $add '## 3. One attempt, step by step'
    & $add ''
    & $add '1. Put the switch in the starting state of the card and read its value; the first attempt of a card records it as `valueBefore`.'
    & $add '2. Read the switch title in the language under test and write the prediction: what will change, where, and whether it survives a restart.'
    & $add '3. Press the switch once.'
    & $add '4. Observe and write `effectAfter`: what changed, at once and after leaving the screen.'
    & $add '5. Force-stop and relaunch the app and write `persistsAfterRestart`.'
    & $add '6. Write `dependencies`: other switches or screens that became unavailable or changed, and the message shown.'
    & $add '7. Return the switch to the previous value without hints and write `wayBack` and `reversibilityLimit`.'
    & $add '8. Score the attempt: prediction correct (yes or no), return unaided (yes or no).'
    & $add ''
    & $add '## 4. Scenarios'
    & $add ''
    & $add ('- A scenario is one card in one language: {0} cards x {1} languages = {2} cells, each with {3} planned attempts.' -f $cardList.Count, (Get-DaUCardLanguages).Count, $cells, $attempts)
    & $add '- A cell that is not run stays `open` with its reason, owner and next step; it is never marked `n/a`.'
    & $add '- The Arabic title is read on the device; the manifest carries titles in en, ru and uk only.'
    & $add ''
    foreach ($c in $cardList) {
        & $add ('### {0} - {1}' -f $c.id, $c.titleEn)
        & $add ''
        & $add ('- Task: {0}' -f $c.userTask)
        & $add ('- Where: destination `{0}`, section `{1}`, key `{2}`' -f $c.destination, $c.sectionId, $c.key)
        & $add ('- Titles: en "{0}"; ru "{1}"; uk "{2}"' -f $c.titleEn, $c.titleRu, $c.titleUk)
        & $add ('- Cells: {0}, each open, {1} attempts planned' -f $langs, $attempts)
        & $add ''
    }
    & $add '## 5. Languages and right-to-left'
    & $add ''
    & $add ('- Run the series of a card once per language ({0}). Set the language as the per-app locale of the debug package and put it back after the card.' -f $langs)
    & $add '- `ar` is right-to-left: judge the mirrored position of the switch and its title, and read the order of the elements around it.'
    & $add '- The scripted probe measures the mirrored order from bounds and restores the locale itself; a manual change is restored by hand.'
    & $add ''
    & $add '## 6. TalkBack, keyboard and D-pad'
    & $add ''
    & $add '- TalkBack pass: turn TalkBack on, then for each card write whether the name, the role and the state of the switch were announced, whether the order of focus was sensible, and where focus went after a dialog closed.'
    & $add '- Keyboard and D-pad pass: with focus moved only by direction keys and Enter, write whether the switch was reachable, whether the focus was visible, and whether the state change was visible.'
    & $add '- These passes record observations and are not scored as predictions; the prediction score comes from the touch series.'
    & $add '- Turning TalkBack on changes two tracked settings, `enabled_accessibility_services` and `accessibility_enabled`; turn it off at the end of the pass.'
    & $add ''
    & $add '## 7. Restore step'
    & $add ''
    & $add '- After each card: return the switch to the recorded `valueBefore`.'
    & $add '- After the session: turn TalkBack off, reset the language and the font scale if they were changed by hand, then run `da.ps1 -Verb Restore`; its verdict line must be OK.'
    & $add '- When the verdict is not OK: run `da.ps1 -Verb Recover` before any other run. A drifted state would make the next run compare against the wrong baseline.'
    & $add '- The access probe records the original font scale and locale in the state folder and removes the record only after both read back as recorded.'
    & $add ''
    & $add '## 8. Recording'
    & $add ''
    & $add '- Raw notes and screenshots stay under the ticket scratch root `temp/S4166/runs/<runId>/`; nothing with personal data goes into `PLAN/`.'
    & $add '- Only the counters and statuses are copied into `ucards.json`: `attemptsRecorded`, the correct predictions, the unaided returns and the text fields of the card.'
    & $add ''
    & $add '## 9. What stays open'
    & $add ''
    & $add '- Every U cell, until external participants run the same cards; the 90 percent threshold is not proven by this protocol.'
    & $add '- The TalkBack pass of every card, until the owner records it in section 6.'
    & $add '- Switches whose effect depends on a network source, a premium state or another flavor: recorded as open with the dependency named.'
    return $sb.ToString()
}

function Write-DaUCards {
    <#
    .SYNOPSIS
        Writes audit/ucards.json (UTF-8 without BOM, LF) and reports/u-protocol.md; returns path, size and hash of each.
    .PARAMETER RepoRoot
        Repository root.
    .PARAMETER AuditRoot
        Folder that receives ucards.json.
    .PARAMETER ReportsRoot
        Folder that receives u-protocol.md; created when absent.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [Parameter(Mandatory)][string]$AuditRoot,
        [Parameter(Mandatory)][string]$ReportsRoot
    )
    $doc = New-DaUCards -RepoRoot $RepoRoot
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $written = [System.Collections.Generic.List[object]]::new()

    $cardsPath = Join-Path $AuditRoot 'ucards.json'
    Write-DaJson -Path $cardsPath -InputObject $doc
    $protocolPath = Join-Path $ReportsRoot 'u-protocol.md'
    [void][System.IO.Directory]::CreateDirectory($ReportsRoot)
    $protocolBytes = $utf8.GetBytes((New-DaUProtocolText -Cards $doc))
    [System.IO.File]::WriteAllBytes($protocolPath, $protocolBytes)

    foreach ($p in @($cardsPath, $protocolPath)) {
        $bytes = [System.IO.File]::ReadAllBytes($p)
        $written.Add([pscustomobject]@{
                Path   = $p
                Bytes  = $bytes.Length
                Sha256 = [System.Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
            })
    }
    return $written.ToArray()
}

# ---------------------------------------------------------------------------------------------
# UI tree parsing (pure)
# ---------------------------------------------------------------------------------------------

function ConvertFrom-DaUiTreeXml {
    <#
    .SYNOPSIS
        Parses a uiautomator dump into a flat node list in document order, with parent index and flags.
    .DESCRIPTION
        Returns Ok, Error, IdleRefusal, Width, Height and Nodes. DTD processing is prohibited. Text that
        surrounds the hierarchy element (the trailing "dumped to" line) is ignored.
    .PARAMETER Xml
        Dump text.
    #>
    [CmdletBinding()]
    param([AllowNull()][AllowEmptyString()][string]$Xml)
    $fail = {
        param($why, $idle)
        [pscustomobject]@{ Ok = $false; Error = $why; IdleRefusal = [bool]$idle; Width = 0; Height = 0; Nodes = @() }
    }
    if ([string]::IsNullOrWhiteSpace($Xml)) { return (& $fail 'no tree text' $false) }
    if ($Xml -match 'could not get idle state') { return (& $fail 'uiautomator refused: the window never went idle (a live readout keeps emitting events)' $true) }
    $m = [regex]::Match($Xml, '(?s)<hierarchy.*?</hierarchy>')
    if (-not $m.Success) { return (& $fail 'no hierarchy element in the dump' $false) }

    $doc = [System.Xml.XmlDocument]::new()
    $doc.XmlResolver = $null
    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    try {
        $reader = [System.Xml.XmlReader]::Create([System.IO.StringReader]::new($m.Value), $settings)
        try { $doc.Load($reader) } finally { $reader.Dispose() }
    }
    catch { return (& $fail "the dump is not valid XML: $($_.Exception.Message)" $false) }

    $nodes = [System.Collections.Generic.List[object]]::new()
    $stack = [System.Collections.Generic.Stack[object]]::new()
    $rootChildren = @($doc.DocumentElement.ChildNodes | Where-Object { $_.NodeType -eq [System.Xml.XmlNodeType]::Element })
    for ($i = $rootChildren.Count - 1; $i -ge 0; $i--) { $stack.Push(@($rootChildren[$i], -1, 0)) }
    $boundsRx = [regex]'^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$'
    $maxX = 0
    $maxY = 0
    while ($stack.Count -gt 0) {
        $item = $stack.Pop()
        $el = $item[0]
        $parent = [int]$item[1]
        $depth = [int]$item[2]
        $index = $nodes.Count
        $b = $boundsRx.Match($el.GetAttribute('bounds'))
        $x1 = 0; $y1 = 0; $x2 = 0; $y2 = 0
        if ($b.Success) {
            $x1 = [int]$b.Groups[1].Value; $y1 = [int]$b.Groups[2].Value
            $x2 = [int]$b.Groups[3].Value; $y2 = [int]$b.Groups[4].Value
        }
        $resId = $el.GetAttribute('resource-id')
        $sep = $resId.IndexOf(':id/')
        $kids = @($el.ChildNodes | Where-Object { $_.NodeType -eq [System.Xml.XmlNodeType]::Element })
        $flag = { param($name) ($el.GetAttribute($name) -eq 'true') }
        if ($parent -lt 0) {
            if ($x2 -gt $maxX) { $maxX = $x2 }
            if ($y2 -gt $maxY) { $maxY = $y2 }
        }
        $nodes.Add([pscustomobject]@{
                Index         = $index
                Parent        = $parent
                Depth         = $depth
                Class         = $el.GetAttribute('class')
                ResId         = $resId
                ResIdShort    = if ($sep -ge 0) { $resId.Substring($sep + 4) } else { '' }
                Text          = $el.GetAttribute('text').Trim()
                Desc          = $el.GetAttribute('content-desc').Trim()
                Hint          = $el.GetAttribute('hint').Trim()
                Clickable     = (& $flag 'clickable')
                LongClickable = (& $flag 'long-clickable')
                Focusable     = (& $flag 'focusable')
                Focused       = (& $flag 'focused')
                Checkable     = (& $flag 'checkable')
                Checked       = (& $flag 'checked')
                Enabled       = ($el.GetAttribute('enabled') -ne 'false')
                Selected      = (& $flag 'selected')
                Scrollable    = (& $flag 'scrollable')
                Password      = (& $flag 'password')
                X1            = $x1
                Y1            = $y1
                X2            = $x2
                Y2            = $y2
                Width         = ($x2 - $x1)
                Height        = ($y2 - $y1)
                ChildCount    = $kids.Count
                Leaf          = ($kids.Count -eq 0)
            })
        for ($i = $kids.Count - 1; $i -ge 0; $i--) { $stack.Push(@($kids[$i], $index, ($depth + 1))) }
    }
    return [pscustomobject]@{ Ok = $true; Error = ''; IdleRefusal = $false; Width = $maxX; Height = $maxY; Nodes = $nodes.ToArray() }
}

function Get-DaEffectiveNames {
    <#
    .SYNOPSIS
        The name a screen reader would give each node: its own text, description or hint, else that of its descendants.
    .PARAMETER Nodes
        Node list of ConvertFrom-DaUiTreeXml.
    #>
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Nodes)
    $n = $Nodes.Count
    $names = [string[]]::new($n)
    $kids = @{}
    for ($i = 0; $i -lt $n; $i++) {
        $p = [int]$Nodes[$i].Parent
        if ($p -ge 0) {
            if (-not $kids.ContainsKey($p)) { $kids[$p] = [System.Collections.Generic.List[int]]::new() }
            $kids[$p].Add($i)
        }
    }
    for ($i = $n - 1; $i -ge 0; $i--) {
        $nd = $Nodes[$i]
        $own = if ($nd.Text) { $nd.Text } elseif ($nd.Desc) { $nd.Desc } elseif ($nd.Hint) { $nd.Hint } else { '' }
        if ($own) { $names[$i] = $own; continue }
        $parts = [System.Collections.Generic.List[string]]::new()
        if ($kids.ContainsKey($i)) {
            foreach ($c in $kids[$i]) { if ($names[$c]) { $parts.Add($names[$c]) } }
        }
        $names[$i] = ($parts -join ' ').Trim()
    }
    return , $names
}

function Test-DaInteractiveNode {
    <#
    .SYNOPSIS
        Whether a node is a control a user can act on: visible, and clickable, checkable or focusable as a leaf.
    .PARAMETER Node
        One node of ConvertFrom-DaUiTreeXml.
    #>
    param([Parameter(Mandatory)]$Node)
    if ($Node.Width -le 0 -or $Node.Height -le 0) { return $false }
    if (-not ($Node.Clickable -or $Node.LongClickable -or $Node.Checkable -or $Node.Focusable)) { return $false }
    if ($Node.Scrollable -and -not $Node.Clickable) { return $false }
    if (-not $Node.Clickable -and -not $Node.Checkable -and -not $Node.Leaf) { return $false }
    return $true
}

function Get-DaUnnamedNodes {
    <#
    .SYNOPSIS
        Interactive nodes that neither carry text or a content description nor have a named descendant.
    .PARAMETER Nodes
        Node list of ConvertFrom-DaUiTreeXml.
    #>
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Nodes)
    $names = Get-DaEffectiveNames -Nodes $Nodes
    $found = [System.Collections.Generic.List[object]]::new()
    foreach ($nd in $Nodes) {
        if (-not (Test-DaInteractiveNode -Node $nd)) { continue }
        if ($names[$nd.Index]) { continue }
        $why = @()
        if ($nd.Clickable) { $why += 'clickable' }
        if ($nd.Checkable) { $why += 'checkable' }
        if ($nd.Focusable) { $why += 'focusable' }
        $found.Add([pscustomobject]@{
                Index   = $nd.Index
                Class   = $nd.Class
                ResId   = $nd.ResId
                Bounds  = ('{0},{1},{2},{3}' -f $nd.X1, $nd.Y1, $nd.X2, $nd.Y2)
                Enabled = $nd.Enabled
                Why     = ($why -join '+')
            })
    }
    return $found.ToArray()
}

function Get-DaStateGaps {
    <#
    .SYNOPSIS
        Nodes whose class is a two-state control but that do not expose the checkable attribute.
    .PARAMETER Nodes
        Node list of ConvertFrom-DaUiTreeXml.
    #>
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Nodes)
    $names = Get-DaEffectiveNames -Nodes $Nodes
    $rx = [regex]'(?i)(Switch|CheckBox|ToggleButton|RadioButton|CompoundButton|Checkable)\w*$'
    $found = [System.Collections.Generic.List[object]]::new()
    foreach ($nd in $Nodes) {
        if ($nd.Width -le 0 -or $nd.Height -le 0) { continue }
        $simple = ($nd.Class -split '\.')[-1]
        if (-not $rx.IsMatch($simple)) { continue }
        if ($nd.Checkable) { continue }
        $found.Add([pscustomobject]@{
                Index  = $nd.Index
                Class  = $nd.Class
                ResId  = $nd.ResId
                Name   = $names[$nd.Index]
                Bounds = ('{0},{1},{2},{3}' -f $nd.X1, $nd.Y1, $nd.X2, $nd.Y2)
                Why    = 'the class is a two-state control but checkable is false, so its state is not exposed'
            })
    }
    return $found.ToArray()
}

function Get-DaNodeIdentity {
    <#
    .SYNOPSIS
        A string that names a node on one screen: id, text, description and bounds.
    .PARAMETER Node
        One node.
    #>
    param([Parameter(Mandatory)]$Node)
    return ('{0}|{1}|{2}|{3},{4},{5},{6}' -f $Node.ResId, $Node.Text, $Node.Desc, $Node.X1, $Node.Y1, $Node.X2, $Node.Y2)
}

function Get-DaFocusOrderReport {
    <#
    .SYNOPSIS
        Judges a recorded D-pad traversal: where focus started, whether it moved, stalled, cycled or ran against reading order.
    .DESCRIPTION
        Steps are objects with Step, Key and Focused (the focused node or null). Step 0 is the focus
        found before the first press and is never a finding on its own. A stall or a return to a visited
        node is normal once every focusable node was visited (the end of a list, a wrap-around), so
        it becomes a finding only while focusable nodes are still unvisited and their number is known.
        Finding kinds: no-focus, stuck, revisit, order, unreached.
    .PARAMETER Steps
        Recorded steps in press order, step 0 first.
    .PARAMETER FocusableCount
        Focusable interactive nodes of the screen before the first press; 0 means unknown.
    .PARAMETER Tolerance
        Pixels of slack before a backwards move counts as an order inversion.
    .PARAMETER Rtl
        Reads the horizontal keys as mirrored.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Steps,
        [int]$FocusableCount = 0,
        [int]$Tolerance = 4,
        [switch]$Rtl
    )
    $seq = [System.Collections.Generic.List[object]]::new()
    $findings = [System.Collections.Generic.List[object]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $noFocus = [System.Collections.Generic.List[int]]::new()
    $prevId = $null
    $prev = $null
    $stuckAt = $null
    $stuckDistinct = 0
    $revisitAt = $null
    $revisitDistinct = 0
    $inversions = 0
    $presses = 0
    foreach ($s in $Steps) {
        $step = [int]$s.Step
        if ($step -gt 0) { $presses++ }
        $node = $s.Focused
        if ($null -eq $node) {
            if ($step -gt 0) { $noFocus.Add($step) }
            $seq.Add([pscustomobject]@{ Step = $step; Key = [string]$s.Key; Label = ''; ResId = ''; Bounds = ''; Moved = $false })
            continue
        }
        $id = Get-DaNodeIdentity -Node $node
        $moved = ($id -ne $prevId)
        $first = $seen.Add($id)
        $label = if ($node.Text) { $node.Text } elseif ($node.Desc) { $node.Desc } else { '' }
        $seq.Add([pscustomobject]@{
                Step   = $step
                Key    = [string]$s.Key
                Label  = $label
                ResId  = [string]$node.ResId
                Bounds = ('{0},{1},{2},{3}' -f $node.X1, $node.Y1, $node.X2, $node.Y2)
                Moved  = $moved
            })
        if ($step -gt 0 -and -not $moved -and $null -ne $prevId -and $null -eq $stuckAt) {
            $stuckAt = $step
            $stuckDistinct = $seen.Count
        }
        if ($step -gt 0 -and $moved -and -not $first -and $null -eq $revisitAt) {
            $revisitAt = $step
            $revisitDistinct = $seen.Count
        }
        if ($step -gt 0 -and $moved -and $first -and $null -ne $prev) {
            $k = ([string]$s.Key).ToUpperInvariant()
            $back = $false
            if ($k -match 'DPAD_DOWN|(^|_)DOWN$') { $back = ($node.Y1 -lt ($prev.Y1 - $Tolerance)) }
            elseif ($k -match 'DPAD_UP|(^|_)UP$') { $back = ($node.Y1 -gt ($prev.Y1 + $Tolerance)) }
            elseif ($k -match 'DPAD_RIGHT|(^|_)RIGHT$') { $back = if ($Rtl) { $node.X1 -gt ($prev.X1 + $Tolerance) } else { $node.X1 -lt ($prev.X1 - $Tolerance) } }
            elseif ($k -match 'DPAD_LEFT|(^|_)LEFT$') { $back = if ($Rtl) { $node.X1 -lt ($prev.X1 - $Tolerance) } else { $node.X1 -gt ($prev.X1 + $Tolerance) } }
            if ($back) { $inversions++ }
        }
        $prevId = $id
        $prev = $node
    }

    $distinct = $seen.Count
    if ($presses -gt 0 -and $noFocus.Count -eq $presses) {
        $findings.Add([pscustomobject]@{ Kind = 'no-focus'; Step = 1; Detail = 'no node took focus on any press: keyboard focus is not reachable on this screen' })
    }
    elseif ($noFocus.Count -gt 0) {
        $findings.Add([pscustomobject]@{ Kind = 'no-focus'; Step = $noFocus[0]; Detail = ('no focused node after press(es) {0}' -f ($noFocus -join ', ')) })
    }
    $known = ($FocusableCount -gt 0)
    if ($null -ne $stuckAt -and $known -and $stuckDistinct -lt $FocusableCount) {
        $findings.Add([pscustomobject]@{ Kind = 'stuck'; Step = $stuckAt; Detail = ('focus did not move at press {0} after {1} of {2} focusable node(s): a trap' -f $stuckAt, $stuckDistinct, $FocusableCount) })
    }
    if ($null -ne $revisitAt -and $known -and $revisitDistinct -lt $FocusableCount) {
        $findings.Add([pscustomobject]@{ Kind = 'revisit'; Step = $revisitAt; Detail = ('focus returned to a visited node at press {0} after {1} of {2} focusable node(s): a cycle' -f $revisitAt, $revisitDistinct, $FocusableCount) })
    }
    if ($inversions -gt 0) {
        $findings.Add([pscustomobject]@{ Kind = 'order'; Step = 0; Detail = ('{0} move(s) ran against the reading order of the pressed direction' -f $inversions) })
    }
    $complete = ($known -and $presses -ge $FocusableCount)
    $unreached = 0
    if ($known -and $distinct -lt $FocusableCount) { $unreached = $FocusableCount - $distinct }
    if ($complete -and $unreached -gt 0 -and $noFocus.Count -lt $presses) {
        $findings.Add([pscustomobject]@{ Kind = 'unreached'; Step = 0; Detail = ('{0} of {1} focusable node(s) never took focus in {2} press(es)' -f $unreached, $FocusableCount, $presses) })
    }
    return [pscustomobject]@{
        Presses                = $presses
        Sequence               = $seq.ToArray()
        Distinct               = $distinct
        FocusableCount         = $FocusableCount
        Complete               = $complete
        Unreached              = $unreached
        NoFocusSteps           = $noFocus.ToArray()
        StuckAt                = $stuckAt
        RevisitedAt            = $revisitAt
        ReadingOrderInversions = $inversions
        Findings               = $findings.ToArray()
    }
}

function Get-DaKeyedNodes {
    <#
    .SYNOPSIS
        Visible nodes that carry a resource id, keyed id#occurrence so repeated ids stay distinct.
    .PARAMETER Nodes
        Node list of ConvertFrom-DaUiTreeXml.
    #>
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Nodes)
    $map = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    $occurrence = @{}
    foreach ($nd in $Nodes) {
        if (-not $nd.ResId -or $nd.Width -le 0 -or $nd.Height -le 0) { continue }
        $c = 0
        if ($occurrence.ContainsKey($nd.ResId)) { $c = $occurrence[$nd.ResId] + 1 }
        $occurrence[$nd.ResId] = $c
        $map["$($nd.ResId)#$c"] = $nd
    }
    return , $map
}

function Test-DaMirroredOrder {
    <#
    .SYNOPSIS
        Compares the same screen in a left-to-right and a right-to-left locale: asymmetric elements must swap sides.
    .DESCRIPTION
        Nodes are matched by resource id and occurrence, so labels in another language do not matter.
        A matched node whose centre is off the screen's vertical axis is asymmetric; it is mirrored when
        its new centre is within tolerance of the reflected old one. The verdict is inconclusive when the
        locale change did not reach the labels or no asymmetric element exists. Elements that stay put
        on purpose (a media seek bar) are candidates for review, not defects.
    .PARAMETER LtrNodes
        Nodes of the screen before the locale change.
    .PARAMETER RtlNodes
        Nodes of the same screen after it.
    .PARAMETER Width
        Display width in pixels.
    .PARAMETER TolerancePct
        Tolerance as a percentage of the width, at least 2 pixels.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$LtrNodes,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$RtlNodes,
        [Parameter(Mandatory)][int]$Width,
        [double]$TolerancePct = 2.0
    )
    $tol = [math]::Max(2.0, $Width * $TolerancePct / 100.0)
    $ltr = Get-DaKeyedNodes -Nodes $LtrNodes
    $rtl = Get-DaKeyedNodes -Nodes $RtlNodes
    $matched = 0
    $symmetric = 0
    $mirrored = 0
    $changed = 0
    $notMirrored = [System.Collections.Generic.List[object]]::new()
    foreach ($key in $ltr.Keys) {
        if (-not $rtl.ContainsKey($key)) { continue }
        $a = $ltr[$key]
        $b = $rtl[$key]
        $matched++
        $labelA = if ($a.Text) { $a.Text } else { $a.Desc }
        $labelB = if ($b.Text) { $b.Text } else { $b.Desc }
        if ($labelA -ne $labelB) { $changed++ }
        $ca = ($a.X1 + $a.X2) / 2.0
        $cb = ($b.X1 + $b.X2) / 2.0
        if ([math]::Abs($ca - ($Width / 2.0)) -le $tol) { $symmetric++; continue }
        if ([math]::Abs($cb - ($Width - $ca)) -le $tol) { $mirrored++; continue }
        $notMirrored.Add([pscustomobject]@{
                ResId      = $a.ResId
                LtrBounds  = ('{0},{1},{2},{3}' -f $a.X1, $a.Y1, $a.X2, $a.Y2)
                RtlBounds  = ('{0},{1},{2},{3}' -f $b.X1, $b.Y1, $b.X2, $b.Y2)
            })
    }
    $unmatchedLtr = 0
    foreach ($key in $ltr.Keys) { if (-not $rtl.ContainsKey($key)) { $unmatchedLtr++ } }
    $unmatchedRtl = 0
    foreach ($key in $rtl.Keys) { if (-not $ltr.ContainsKey($key)) { $unmatchedRtl++ } }
    $rtlScript = [regex]'[\u0590-\u08ff]'
    $scriptSeen = $false
    foreach ($nd in $RtlNodes) { if ($rtlScript.IsMatch($nd.Text) -or $rtlScript.IsMatch($nd.Desc)) { $scriptSeen = $true; break } }
    $applied = ($changed -gt 0 -or $scriptSeen)
    $asymmetric = $mirrored + $notMirrored.Count
    $verdict = 'pass'
    $reason = ('{0} of {1} asymmetric element(s) swapped sides' -f $mirrored, $asymmetric)
    if ($matched -eq 0) { $verdict = 'inconclusive'; $reason = 'no element could be matched by resource id between the two trees' }
    elseif (-not $applied) { $verdict = 'inconclusive'; $reason = 'the locale change did not reach any label of the screen' }
    elseif ($asymmetric -eq 0) { $verdict = 'inconclusive'; $reason = 'every matched element sits on the vertical axis, so mirroring cannot be observed' }
    elseif ($notMirrored.Count -gt 0) { $verdict = 'finding'; $reason = ('{0} of {1} asymmetric element(s) kept their side' -f $notMirrored.Count, $asymmetric) }
    return [pscustomobject]@{
        Verdict       = $verdict
        Reason        = $reason
        Width         = $Width
        Matched       = $matched
        Symmetric     = $symmetric
        Mirrored      = $mirrored
        NotMirrored   = $notMirrored.ToArray()
        UnmatchedLtr  = $unmatchedLtr
        UnmatchedRtl  = $unmatchedRtl
        ChangedLabels = $changed
        LocaleApplied = $applied
    }
}

function ConvertFrom-DaClipCheckJson {
    <#
    .SYNOPSIS
        Parses the one-line JSON answer of adb.ps1 clip-check -Json into Ok, Judged, OffGlass, FrameCut and findings.
    .DESCRIPTION
        Judged is false when the verb refused (exit 7: no trusted display shape) or the display reports
        no rounded corners, so nothing could leave the glass; the caller reports that as open, not as a pass.
    .PARAMETER Json
        Verb output; the last line that starts a JSON object is used.
    #>
    [CmdletBinding()]
    param([AllowNull()][AllowEmptyString()][string]$Json)
    $none = {
        param($why)
        [pscustomobject]@{ Ok = $false; Judged = $false; ExitCode = -1; Checked = 0; OffGlass = 0; FrameCut = 0; Radius = 0; Findings = @(); Reason = $why }
    }
    if ([string]::IsNullOrWhiteSpace($Json)) { return (& $none 'the clip check printed nothing') }
    $line = @($Json -split "`r?`n" | Where-Object { $_.TrimStart().StartsWith('{') }) | Select-Object -Last 1
    if (-not $line) { return (& $none 'the clip check printed no JSON object') }
    try { $o = $line | ConvertFrom-Json -Depth 16 }
    catch { return (& $none "the clip check answer is not valid JSON: $($_.Exception.Message)") }
    $code = if ($null -ne $o.exitCode) { [int]$o.exitCode } else { -1 }
    if ($null -eq $o.data) {
        $why = if ($o.reason) { [string]$o.reason } else { 'the clip check returned no data' }
        return [pscustomobject]@{ Ok = $false; Judged = $false; ExitCode = $code; Checked = 0; OffGlass = 0; FrameCut = 0; Radius = 0; Findings = @(); Reason = $why }
    }
    $radius = 0
    if ($null -ne $o.data.shape -and $null -ne $o.data.shape.radius) { $radius = [double]$o.data.shape.radius }
    $findings = @($o.data.findings | Where-Object { $null -ne $_ } | ForEach-Object {
            [pscustomobject]@{ Kind = [string]$_.kind; Label = [string]$_.label; Overflow = [double]$_.overflow }
        })
    $reason = if ($radius -le 0) { 'the display reports no rounded corners, so the clip check judged nothing' } else { '' }
    return [pscustomobject]@{
        Ok       = ($code -eq 0 -or $code -eq 9 -or $code -eq 10)
        Judged   = ($radius -gt 0)
        ExitCode = $code
        Checked  = [int]$o.data.checked
        OffGlass = [int]$o.data.offGlass
        FrameCut = [int]$o.data.frameCut
        Radius   = $radius
        Findings = $findings
        Reason   = $reason
    }
}

# ---------------------------------------------------------------------------------------------
# Device state: font scale and per-app locale (the only changers in this file)
# ---------------------------------------------------------------------------------------------

function ConvertFrom-DaFontScaleLines {
    <#
    .SYNOPSIS
        Parses `settings get system font_scale`: an untouched device answers null, which means the default.
    .PARAMETER Lines
        Output lines of the call.
    #>
    param([AllowNull()][string[]]$Lines)
    $raw = (@($Lines | ForEach-Object { [string]$_ }) -join '').Trim()
    if ([string]::IsNullOrWhiteSpace($raw) -or $raw -eq 'null') {
        return [pscustomobject]@{ Ok = $true; Present = $false; Value = $null; Numeric = 1.0 }
    }
    $d = 0.0
    $style = [System.Globalization.NumberStyles]::Float
    if ([double]::TryParse($raw, $style, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$d) -and $d -gt 0) {
        return [pscustomobject]@{ Ok = $true; Present = $true; Value = $raw; Numeric = $d }
    }
    return [pscustomobject]@{ Ok = $false; Present = $false; Value = $null; Numeric = 0.0 }
}

function ConvertFrom-DaAppLocaleLines {
    <#
    .SYNOPSIS
        Parses `cmd locale get-app-locales <pkg>`: "Locales for <pkg> for user 0 are [ar]"; [] means no override.
    .PARAMETER Lines
        Output lines of the call.
    #>
    param([AllowNull()][string[]]$Lines)
    $raw = (@($Lines | ForEach-Object { [string]$_ }) -join ' ')
    $m = [regex]::Match($raw, '(?i)\bare\s+\[([^\]]*)\]')
    if (-not $m.Success) { return [pscustomobject]@{ Ok = $false; Tags = @() } }
    $tags = @($m.Groups[1].Value -split ',' | ForEach-Object { ($_.Trim() -replace '_', '-') } | Where-Object { $_ })
    return [pscustomobject]@{ Ok = $true; Tags = $tags }
}

function Invoke-DaAccessAdb {
    <#
    .SYNOPSIS
        Runs the injected adb runner once and normalises its answer to ExitCode, Lines and Stderr; never throws.
    .PARAMETER Adb
        Scriptblock taking a string array of adb arguments.
    .PARAMETER Arguments
        Arguments after the serial.
    #>
    param([Parameter(Mandatory)][scriptblock]$Adb, [Parameter(Mandatory)][string[]]$Arguments)
    try { $r = & $Adb $Arguments }
    catch { return [pscustomobject]@{ ExitCode = -1; Lines = @(); Stderr = $_.Exception.Message } }
    if ($null -eq $r -or $r.PSObject.Properties.Name -notcontains 'ExitCode') {
        return [pscustomobject]@{ ExitCode = -1; Lines = @(); Stderr = 'the adb runner returned no result object' }
    }
    return [pscustomobject]@{
        ExitCode = [int]$r.ExitCode
        Lines    = @($r.Lines | ForEach-Object { [string]$_ })
        Stderr   = [string]$r.Stderr
    }
}

function Get-DaFontScaleState {
    <#
    .SYNOPSIS
        Records the current system font scale; the object it returns is what Set-DaFontScale and Restore-DaFontScale require.
    .PARAMETER Adb
        Injectable adb runner.
    #>
    param([Parameter(Mandatory)][scriptblock]$Adb)
    $r = Invoke-DaAccessAdb -Adb $Adb -Arguments @('shell', 'settings', 'get', 'system', 'font_scale')
    if ($r.ExitCode -ne 0) {
        return [pscustomobject]@{ Kind = 'font_scale'; Recorded = $false; Present = $false; Value = $null; Reason = "settings get failed (exit $($r.ExitCode)): $($r.Stderr)" }
    }
    $p = ConvertFrom-DaFontScaleLines -Lines $r.Lines
    if (-not $p.Ok) {
        return [pscustomobject]@{ Kind = 'font_scale'; Recorded = $false; Present = $false; Value = $null; Reason = "unreadable font_scale answer: $($r.Lines -join ' ')" }
    }
    return [pscustomobject]@{ Kind = 'font_scale'; Recorded = $true; Present = $p.Present; Value = $p.Value; Reason = '' }
}

function Set-DaFontScale {
    <#
    .SYNOPSIS
        Sets the system font scale; refuses unless the original was recorded first. Returns Ok, Applied and Reason.
    .PARAMETER Adb
        Injectable adb runner.
    .PARAMETER Original
        The object Get-DaFontScaleState returned before the first change.
    .PARAMETER Scale
        Multiplier, 0.85 to 2.0.
    .PARAMETER SettleMs
        Pause after the write so the configuration change reaches the app.
    #>
    param(
        [Parameter(Mandatory)][scriptblock]$Adb,
        [Parameter(Mandatory)]$Original,
        [Parameter(Mandatory)][ValidateRange(0.85, 2.0)][double]$Scale,
        [int]$SettleMs = 0
    )
    if ($Original.Kind -ne 'font_scale' -or -not $Original.Recorded) { throw 'da-access: font scale change refused: the original value was not recorded' }
    $text = $Scale.ToString('0.0##', [System.Globalization.CultureInfo]::InvariantCulture)
    $w = Invoke-DaAccessAdb -Adb $Adb -Arguments @('shell', 'settings', 'put', 'system', 'font_scale', $text)
    if ($w.ExitCode -ne 0) { return [pscustomobject]@{ Ok = $false; Applied = $null; Reason = "settings put failed (exit $($w.ExitCode)): $($w.Stderr)" } }
    if ($SettleMs -gt 0) { Start-Sleep -Milliseconds $SettleMs }
    $now = Get-DaFontScaleState -Adb $Adb
    $ok = ($now.Recorded -and $now.Present -and [math]::Abs([double]::Parse($now.Value, [System.Globalization.CultureInfo]::InvariantCulture) - $Scale) -lt 0.0005)
    $reason = if ($ok) { '' } else { "read back '$($now.Value)' after writing $text" }
    return [pscustomobject]@{ Ok = $ok; Applied = $now.Value; Reason = $reason }
}

function Restore-DaFontScale {
    <#
    .SYNOPSIS
        Puts the recorded font scale back (a value is written, an absent one is deleted) and reads it back. Returns Ok and Reason.
    .PARAMETER Adb
        Injectable adb runner.
    .PARAMETER Original
        The object Get-DaFontScaleState recorded.
    #>
    param([Parameter(Mandatory)][scriptblock]$Adb, [Parameter(Mandatory)]$Original)
    if ($Original.Kind -ne 'font_scale' -or -not $Original.Recorded) {
        return [pscustomobject]@{ Ok = $false; Reason = 'no recorded original font scale to restore' }
    }
    $callArgs = if ($Original.Present) { @('shell', 'settings', 'put', 'system', 'font_scale', [string]$Original.Value) } else { @('shell', 'settings', 'delete', 'system', 'font_scale') }
    $w = Invoke-DaAccessAdb -Adb $Adb -Arguments $callArgs
    if ($w.ExitCode -ne 0) { return [pscustomobject]@{ Ok = $false; Reason = "restore call failed (exit $($w.ExitCode)): $($w.Stderr)" } }
    $now = Get-DaFontScaleState -Adb $Adb
    if (-not $now.Recorded) { return [pscustomobject]@{ Ok = $false; Reason = "restored value unreadable: $($now.Reason)" } }
    $same = if ($Original.Present) {
        $now.Present -and [math]::Abs([double]::Parse($now.Value, [System.Globalization.CultureInfo]::InvariantCulture) - [double]::Parse([string]$Original.Value, [System.Globalization.CultureInfo]::InvariantCulture)) -lt 0.0005
    }
    else { -not $now.Present }
    $want = if ($Original.Present) { [string]$Original.Value } else { 'absent' }
    $got = if ($now.Present) { [string]$now.Value } else { 'absent' }
    return [pscustomobject]@{ Ok = [bool]$same; Reason = $(if ($same) { '' } else { "font scale is $got, recorded $want" }) }
}

function Get-DaAppLocaleState {
    <#
    .SYNOPSIS
        Records the per-app locale of a package; the object it returns is what Set-DaAppLocale and Restore-DaAppLocale require.
    .PARAMETER Adb
        Injectable adb runner.
    .PARAMETER Package
        Application id.
    #>
    param(
        [Parameter(Mandatory)][scriptblock]$Adb,
        [Parameter(Mandatory)][ValidatePattern('^[A-Za-z][A-Za-z0-9_.]+$')][string]$Package
    )
    $r = Invoke-DaAccessAdb -Adb $Adb -Arguments @('shell', 'cmd', 'locale', 'get-app-locales', $Package)
    if ($r.ExitCode -ne 0) {
        return [pscustomobject]@{ Kind = 'app_locale'; Recorded = $false; Package = $Package; Tags = @(); Reason = "get-app-locales failed (exit $($r.ExitCode)): $($r.Stderr)" }
    }
    $p = ConvertFrom-DaAppLocaleLines -Lines $r.Lines
    if (-not $p.Ok) {
        return [pscustomobject]@{ Kind = 'app_locale'; Recorded = $false; Package = $Package; Tags = @(); Reason = "unreadable get-app-locales answer: $($r.Lines -join ' ')" }
    }
    return [pscustomobject]@{ Kind = 'app_locale'; Recorded = $true; Package = $Package; Tags = @($p.Tags); Reason = '' }
}

function Set-DaAppLocale {
    <#
    .SYNOPSIS
        Sets the per-app locale; refuses unless the original was recorded first. Returns Ok, Applied and Reason.
    .PARAMETER Adb
        Injectable adb runner.
    .PARAMETER Original
        The object Get-DaAppLocaleState returned before the first change.
    .PARAMETER Locale
        BCP-47 tag, for example ar.
    .PARAMETER SettleMs
        Pause after the call so the activity is recreated.
    #>
    param(
        [Parameter(Mandatory)][scriptblock]$Adb,
        [Parameter(Mandatory)]$Original,
        [Parameter(Mandatory)][ValidatePattern('^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$')][string]$Locale,
        [int]$SettleMs = 0
    )
    if ($Original.Kind -ne 'app_locale' -or -not $Original.Recorded) { throw 'da-access: locale change refused: the original value was not recorded' }
    $w = Invoke-DaAccessAdb -Adb $Adb -Arguments @('shell', 'cmd', 'locale', 'set-app-locales', [string]$Original.Package, '--locales', $Locale)
    if ($w.ExitCode -ne 0) { return [pscustomobject]@{ Ok = $false; Applied = @(); Reason = "set-app-locales failed (exit $($w.ExitCode)): $($w.Stderr)" } }
    if ($SettleMs -gt 0) { Start-Sleep -Milliseconds $SettleMs }
    $now = Get-DaAppLocaleState -Adb $Adb -Package ([string]$Original.Package)
    $ok = ($now.Recorded -and @($now.Tags).Count -gt 0 -and ([string]@($now.Tags)[0]).StartsWith($Locale, [System.StringComparison]::OrdinalIgnoreCase))
    $reason = if ($ok) { '' } else { "read back '$(@($now.Tags) -join ',')' after writing $Locale" }
    return [pscustomobject]@{ Ok = $ok; Applied = @($now.Tags); Reason = $reason }
}

function Restore-DaAppLocale {
    <#
    .SYNOPSIS
        Puts the recorded per-app locale back (an empty list clears the override) and reads it back. Returns Ok and Reason.
    .PARAMETER Adb
        Injectable adb runner.
    .PARAMETER Original
        The object Get-DaAppLocaleState recorded.
    #>
    param([Parameter(Mandatory)][scriptblock]$Adb, [Parameter(Mandatory)]$Original)
    if ($Original.Kind -ne 'app_locale' -or -not $Original.Recorded) {
        return [pscustomobject]@{ Ok = $false; Reason = 'no recorded original locale to restore' }
    }
    $callArgs = @('shell', 'cmd', 'locale', 'set-app-locales', [string]$Original.Package)
    if (@($Original.Tags).Count -gt 0) { $callArgs += @('--locales', (@($Original.Tags) -join ',')) }
    $w = Invoke-DaAccessAdb -Adb $Adb -Arguments $callArgs
    if ($w.ExitCode -ne 0) { return [pscustomobject]@{ Ok = $false; Reason = "restore call failed (exit $($w.ExitCode)): $($w.Stderr)" } }
    $now = Get-DaAppLocaleState -Adb $Adb -Package ([string]$Original.Package)
    if (-not $now.Recorded) { return [pscustomobject]@{ Ok = $false; Reason = "restored locale unreadable: $($now.Reason)" } }
    $want = (@($Original.Tags) -join ',')
    $got = (@($now.Tags) -join ',')
    $same = ([string]::Equals($want, $got, [System.StringComparison]::OrdinalIgnoreCase))
    return [pscustomobject]@{ Ok = $same; Reason = $(if ($same) { '' } else { "app locale is [$got], recorded [$want]" }) }
}

function Get-DaAccessOriginalPath {
    <#
    .SYNOPSIS
        Durable record of the originals the probe changes: access-original.json in the stand's state folder.
    #>
    return (Join-Path (Get-DaPaths).State 'access-original.json')
}

function Assert-DaAccessEnvelope {
    <#
    .SYNOPSIS
        Code 0 only inside the verified backup envelope: a restore checkpoint exists for the package and its backup is verified and tracks font_scale.
    .DESCRIPTION
        Returns Code and Reason. The facade writes the checkpoint with BeginMutation; without it a font
        scale change could not be restored from the backup after an interruption.
    .PARAMETER Package
        Application id the probe will work with.
    #>
    param([Parameter(Mandatory)][string]$Package)
    $codes = Get-DaExitCode
    $refused = { param($why) [pscustomobject]@{ Code = $codes.Refused; Reason = $why } }
    $pending = Read-DaJson -Path (Get-DaRestorePendingPath)
    if (-not $pending) { return (& $refused 'no restore checkpoint: run da.ps1 -Verb BeginMutation first; the probe changes font scale and locale only inside the verified backup envelope') }
    if ([string]$pending.package -ne $Package) { return (& $refused "the restore checkpoint is for $($pending.package), not $Package") }
    $manifest = Read-DaJson -Path ([string]$pending.manifestPath)
    if (-not $manifest) { return (& $refused "the backup manifest of the checkpoint is unreadable: $($pending.manifestPath)") }
    if ($manifest.schemaVersion -ne 1) { return (& $refused "backup manifest schemaVersion $($manifest.schemaVersion) is not 1") }
    if ([string]$manifest.verified.status -ne 'verified') { return (& $refused "backup $($manifest.runId) is $($manifest.verified.status), not verified") }
    if ([string]$manifest.package.name -ne $Package) { return (& $refused "backup $($manifest.runId) is for $($manifest.package.name), not $Package") }
    $tracked = @($manifest.settings | ForEach-Object { '{0}/{1}' -f $_.namespace, $_.key })
    if ($tracked -notcontains 'system/font_scale') { return (& $refused "backup $($manifest.runId) does not track system/font_scale, so a font scale change could not be restored from it") }
    return [pscustomobject]@{ Code = $codes.Ok; Reason = "inside the envelope of verified backup $($manifest.runId), run $($pending.runId)" }
}

function Invoke-DaClipCheckVerb {
    <#
    .SYNOPSIS
        Runs the existing adb.ps1 clip-check verb with -Json against a serial and returns its text.
    .PARAMETER Serial
        Emulator serial.
    .PARAMETER RepoRoot
        Repository root that holds scripts/devtest/adb.ps1.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$RepoRoot)
    $verb = Join-Path $RepoRoot 'scripts/devtest/adb.ps1'
    $pwsh = (Get-Process -Id $PID).Path
    $r = Invoke-DaProcess -FilePath $pwsh -ArgumentList @('-NoProfile', '-File', $verb, 'clip-check', '-DeviceId', $Serial, '-Json') -TimeoutMs 120000
    return ($r.Lines -join "`n")
}

function Read-DaAccessTree {
    <#
    .SYNOPSIS
        Dumps the current UI tree through the injected runner (the same exec-out path as adb.ps1 uidump) and parses it.
    .PARAMETER Adb
        Injectable adb runner.
    .PARAMETER Attempts
        Dump attempts; an idle refusal is retried once only, as adb.ps1 does.
    .PARAMETER RetryMs
        Pause between attempts.
    #>
    param([Parameter(Mandatory)][scriptblock]$Adb, [int]$Attempts = 3, [int]$RetryMs = 500)
    $parsed = $null
    for ($i = 1; $i -le $Attempts; $i++) {
        $r = Invoke-DaAccessAdb -Adb $Adb -Arguments @('exec-out', 'uiautomator', 'dump', '/dev/tty')
        $parsed = ConvertFrom-DaUiTreeXml -Xml ($r.Lines -join "`n")
        if ($parsed.Ok) { return $parsed }
        if ($parsed.IdleRefusal -and $i -ge 2) { break }
        if ($i -lt $Attempts -and $RetryMs -gt 0) { Start-Sleep -Milliseconds $RetryMs }
    }
    if ($null -eq $parsed) { return (ConvertFrom-DaUiTreeXml -Xml '') }
    return $parsed
}

function Get-DaScreenFacts {
    <#
    .SYNOPSIS
        Counts and name gaps of one parsed tree: nodes, interactive nodes, unnamed, state gaps, nodes past the display edge.
    .DESCRIPTION
        The edge count is a cheap sanity check only: accessibility bounds are clipped to the window, so
        a hit is rare. Text cut by ellipsis is invisible to the tree; the clip check judges the glass.
    .PARAMETER Tree
        Result of ConvertFrom-DaUiTreeXml.
    #>
    param([Parameter(Mandatory)]$Tree)
    $nodes = @($Tree.Nodes)
    $interactive = @($nodes | Where-Object { Test-DaInteractiveNode -Node $_ })
    $unnamed = @(Get-DaUnnamedNodes -Nodes $nodes)
    $gaps = @(Get-DaStateGaps -Nodes $nodes)
    $outside = @($nodes | Where-Object { $_.Width -gt 0 -and $_.Height -gt 0 -and $Tree.Width -gt 0 -and ($_.X1 -lt 0 -or $_.X2 -gt ($Tree.Width + 1)) })
    return [pscustomobject]@{
        Nodes            = $nodes.Count
        Interactive      = $interactive.Count
        FocusableCount   = @($interactive | Where-Object { $_.Focusable }).Count
        Unnamed          = $unnamed
        StateGaps        = $gaps
        OutsideDisplayX  = $outside.Count
        Width            = $Tree.Width
        Height           = $Tree.Height
    }
}

function Invoke-DaAccessProbe {
    <#
    .SYNOPSIS
        Measures accessibility, RTL and text-size preconditions of the screen in front; restores locale and font scale; never claims a TalkBack pass.
    .DESCRIPTION
        Order: gate (emulator serial, then the verified-backup envelope), baseline tree, D-pad traversal
        by repeated direction keys with the focused node after each press, the ar app locale with the
        mirrored-order check, then each font scale with the clip check. The locale and the font scale
        are changed only through Set-DaAppLocale and Set-DaFontScale after their originals were
        recorded, and both are restored in finally and read back. Returns Code (0 measured with no
        finding, 1 findings, 2 refused or cannot verify, including a restore that did not read back),
        Verdict, Findings, Open and the sections of each measurement.
    .PARAMETER Serial
        Emulator serial (emulator-N); any other serial is refused before an adb call.
    .PARAMETER Package
        Debug application id whose per-app locale is changed.
    .PARAMETER Screen
        Name of the screen for the report.
    .PARAMETER Adb
        Injectable runner taking a string array; default Invoke-DaAdb for the serial.
    .PARAMETER ClipCheck
        Injectable scriptblock taking the font scale and returning the clip-check JSON text.
    .PARAMETER EnvelopeGate
        Injectable gate returning Code and Reason; default Assert-DaAccessEnvelope.
    .PARAMETER RepoRoot
        Repository root for the default clip check; resolved from this library when empty.
    .PARAMETER FontScales
        Scales to test, each 0.85 to 2.0.
    .PARAMETER Locale
        The right-to-left locale of the walk.
    .PARAMETER MaxFocusSteps
        Upper bound of direction-key presses.
    .PARAMETER FocusKey
        Direction key sent for every press.
    .PARAMETER SettleMs
        Pause after a locale or font-scale change.
    .PARAMETER KeySettleMs
        Pause after a key press.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Serial,
        [ValidatePattern('^[A-Za-z][A-Za-z0-9_.]+$')][string]$Package = 'com.sza.fastmediasorter.debug',
        [string]$Screen = 'current screen',
        [scriptblock]$Adb,
        [scriptblock]$ClipCheck,
        [scriptblock]$EnvelopeGate,
        [string]$RepoRoot,
        [double[]]$FontScales = @(1.3, 2.0),
        [ValidatePattern('^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$')][string]$Locale = 'ar',
        [int]$MaxFocusSteps = 30,
        [ValidatePattern('^KEYCODE_[A-Z_]+$')][string]$FocusKey = 'KEYCODE_DPAD_DOWN',
        [int]$SettleMs = 700,
        [int]$KeySettleMs = 250
    )
    $codes = Get-DaExitCode
    $r = [ordered]@{
        Code         = $codes.Refused
        Verdict      = 'refused'
        Reason       = ''
        Serial       = $Serial
        Package      = $Package
        Screen       = $Screen
        Rung         = 'V'
        TalkBackPass = $false
        Statement    = (Get-DaAccessStatement)
        Baseline     = $null
        FocusOrder   = $null
        Rtl          = $null
        FontScales   = @()
        Restore      = [ordered]@{ FontScale = $null; Locale = $null }
        Findings     = @()
        Open         = @()
    }
    $findings = [System.Collections.Generic.List[object]]::new()
    $open = [System.Collections.Generic.List[string]]::new()
    $open.Add('TalkBack pass: not run by the probe; needs the reader and a participant (u-protocol.md section 6)')
    $finish = {
        param($code, $verdict, $reason)
        $r.Code = $code
        $r.Verdict = $verdict
        $r.Reason = $reason
        $r.Findings = $findings.ToArray()
        $r.Open = $open.ToArray()
        return [pscustomobject]$r
    }

    if ($Serial -notmatch '^emulator-\d+$') {
        return (& $finish $codes.Refused 'refused' 'the serial is not an emulator-N serial; the probe never touches another device (docs/DEVICE_FLEET.md)')
    }
    foreach ($s in $FontScales) {
        if ($s -lt 0.85 -or $s -gt 2.0) { return (& $finish $codes.Refused 'refused' "font scale $s is outside 0.85 to 2.0") }
    }
    if (-not $Adb) { $Adb = { param([string[]]$a) Invoke-DaAdb -Serial $Serial -Arguments $a }.GetNewClosure() }
    if (-not $EnvelopeGate) { $EnvelopeGate = { Assert-DaAccessEnvelope -Package $Package }.GetNewClosure() }
    if (-not $RepoRoot) { $RepoRoot = Get-DaRepoRoot }
    if (-not $ClipCheck) { $ClipCheck = { param($scale) Invoke-DaClipCheckVerb -Serial $Serial -RepoRoot $RepoRoot }.GetNewClosure() }

    $gate = & $EnvelopeGate
    if ($gate.Code -ne 0) { return (& $finish $codes.Refused 'refused' ([string]$gate.Reason)) }

    $base = Read-DaAccessTree -Adb $Adb
    if (-not $base.Ok) { return (& $finish $codes.Refused 'refused' "cannot verify: the baseline UI tree is unreadable: $($base.Error)") }
    $facts = Get-DaScreenFacts -Tree $base
    $r.Baseline = [ordered]@{
        Nodes = $facts.Nodes; Interactive = $facts.Interactive; Width = $facts.Width; Height = $facts.Height
        Unnamed = $facts.Unnamed; StateGaps = $facts.StateGaps
    }
    if ($facts.Unnamed.Count -gt 0) {
        $findings.Add([pscustomobject]@{ Area = 'name'; Kind = 'unnamed'; Count = $facts.Unnamed.Count; Detail = "$($facts.Unnamed.Count) clickable or focusable node(s) have no text and no content description" })
    }
    if ($facts.StateGaps.Count -gt 0) {
        $findings.Add([pscustomobject]@{ Area = 'state'; Kind = 'state-not-exposed'; Count = $facts.StateGaps.Count; Detail = "$($facts.StateGaps.Count) two-state control(s) do not expose checkable" })
    }

    $restoreFailed = [System.Collections.Generic.List[string]]::new()
    try {
        # D-pad traversal: only direction key presses, no Enter, so no control is activated.
        $stepCount = [math]::Max(3, [math]::Min($MaxFocusSteps, $facts.FocusableCount + 2))
        $steps = [System.Collections.Generic.List[object]]::new()
        $initial = @($base.Nodes | Where-Object { $_.Focused }) | Select-Object -Last 1
        $steps.Add([pscustomobject]@{ Step = 0; Key = 'initial'; TreeOk = $true; Focused = $initial })
        for ($i = 1; $i -le $stepCount; $i++) {
            $k = Invoke-DaAccessAdb -Adb $Adb -Arguments @('shell', 'input', 'keyevent', $FocusKey)
            if ($k.ExitCode -ne 0) { $open.Add("D-pad traversal stopped at press ${i}: the key call failed (exit $($k.ExitCode))"); break }
            if ($KeySettleMs -gt 0) { Start-Sleep -Milliseconds $KeySettleMs }
            $t = Read-DaAccessTree -Adb $Adb -Attempts 2
            $focused = $null
            if ($t.Ok) { $focused = @($t.Nodes | Where-Object { $_.Focused }) | Select-Object -Last 1 }
            $steps.Add([pscustomobject]@{ Step = $i; Key = $FocusKey; TreeOk = $t.Ok; Focused = $focused })
        }
        $r.FocusOrder = Get-DaFocusOrderReport -Steps $steps.ToArray() -FocusableCount $facts.FocusableCount
        foreach ($f in $r.FocusOrder.Findings) {
            $findings.Add([pscustomobject]@{ Area = 'focus'; Kind = $f.Kind; Count = 1; Detail = $f.Detail })
        }

        # State changes start here. The originals go to a durable record before the first write.
        $origFont = Get-DaFontScaleState -Adb $Adb
        $origLoc = Get-DaAppLocaleState -Adb $Adb -Package $Package
        $recordPath = Get-DaAccessOriginalPath
        if ($origFont.Recorded -or $origLoc.Recorded) {
            Write-DaJson -Path $recordPath -Durable -InputObject ([ordered]@{
                    schemaVersion = 1; serial = $Serial; package = $Package
                    fontScale = [ordered]@{ recorded = $origFont.Recorded; present = $origFont.Present; value = $origFont.Value }
                    locale    = [ordered]@{ recorded = $origLoc.Recorded; tags = @($origLoc.Tags) }
                })
        }

        # Right-to-left walk.
        $rtl = [ordered]@{ Locale = $Locale; Status = 'skipped'; Reason = ''; OriginalTags = @($origLoc.Tags); MirroredOrder = $null }
        if (-not $origLoc.Recorded) {
            $rtl.Reason = "the original app locale could not be read, so it could not be restored: $($origLoc.Reason)"
        }
        else {
            try {
                $set = Set-DaAppLocale -Adb $Adb -Original $origLoc -Locale $Locale -SettleMs $SettleMs
                if (-not $set.Ok) { $rtl.Reason = "the locale change did not apply: $($set.Reason)" }
                else {
                    $rt = Read-DaAccessTree -Adb $Adb
                    if (-not $rt.Ok) { $rtl.Reason = "the tree under $Locale is unreadable: $($rt.Error)" }
                    else {
                        $mo = Test-DaMirroredOrder -LtrNodes $base.Nodes -RtlNodes $rt.Nodes -Width $base.Width
                        $rtl.MirroredOrder = $mo
                        $rtl.Status = if ($mo.Verdict -eq 'inconclusive') { 'inconclusive' } else { 'measured' }
                        $rtl.Reason = $mo.Reason
                        if ($mo.Verdict -eq 'finding') {
                            $findings.Add([pscustomobject]@{ Area = 'rtl'; Kind = 'not-mirrored'; Count = $mo.NotMirrored.Count; Detail = $mo.Reason })
                        }
                        elseif ($mo.Verdict -eq 'inconclusive') { $open.Add("RTL ($Locale): $($mo.Reason)") }
                    }
                }
            }
            finally {
                $back = Restore-DaAppLocale -Adb $Adb -Original $origLoc
                $r.Restore.Locale = $back
                if (-not $back.Ok) { $restoreFailed.Add("locale: $($back.Reason)") }
            }
        }
        $r.Rtl = $rtl
        if ($rtl.Status -eq 'skipped') { $open.Add("RTL ($Locale): skipped - $($rtl.Reason)") }

        # Text-size walk.
        $scaleResults = [System.Collections.Generic.List[object]]::new()
        if (-not $origFont.Recorded) {
            $open.Add("font scales: skipped - the original font scale could not be read, so it could not be restored: $($origFont.Reason)")
        }
        else {
            try {
                foreach ($scale in $FontScales) {
                    $scaleText = $scale.ToString('0.0##', [System.Globalization.CultureInfo]::InvariantCulture)
                    $entry = [ordered]@{ Scale = $scale; Applied = $false; Reason = ''; Nodes = 0; Interactive = 0; OutsideDisplayX = 0; ClipCheck = $null }
                    $set = Set-DaFontScale -Adb $Adb -Original $origFont -Scale $scale -SettleMs $SettleMs
                    if (-not $set.Ok) { $entry.Reason = "the font scale did not apply: $($set.Reason)"; $scaleResults.Add($entry); $open.Add("font scale ${scaleText}: $($entry.Reason)"); continue }
                    $entry.Applied = $true
                    $ft = Read-DaAccessTree -Adb $Adb
                    if (-not $ft.Ok) { $entry.Reason = "the tree is unreadable: $($ft.Error)"; $scaleResults.Add($entry); $open.Add("font scale ${scaleText}: $($entry.Reason)"); continue }
                    $ff = Get-DaScreenFacts -Tree $ft
                    $entry.Nodes = $ff.Nodes
                    $entry.Interactive = $ff.Interactive
                    $entry.OutsideDisplayX = $ff.OutsideDisplayX
                    if ($ff.OutsideDisplayX -gt 0) {
                        $findings.Add([pscustomobject]@{ Area = 'text-size'; Kind = 'outside-display'; Count = $ff.OutsideDisplayX; Detail = "font scale ${scaleText}: $($ff.OutsideDisplayX) node(s) extend past the display edge horizontally" })
                    }
                    $clip = ConvertFrom-DaClipCheckJson -Json ([string](& $ClipCheck $scale))
                    $entry.ClipCheck = $clip
                    if (-not $clip.Ok -or -not $clip.Judged) {
                        $open.Add("font scale ${scaleText}: clip check judged nothing - $($clip.Reason)")
                    }
                    elseif ($clip.OffGlass -gt 0) {
                        $findings.Add([pscustomobject]@{ Area = 'text-size'; Kind = 'off-glass'; Count = $clip.OffGlass; Detail = "font scale ${scaleText}: $($clip.OffGlass) node(s) cannot fit on the glass at any scroll position" })
                    }
                    $scaleResults.Add($entry)
                }
            }
            finally {
                $back = Restore-DaFontScale -Adb $Adb -Original $origFont
                $r.Restore.FontScale = $back
                if (-not $back.Ok) { $restoreFailed.Add("font scale: $($back.Reason)") }
            }
        }
        $r.FontScales = $scaleResults.ToArray()

        if ($restoreFailed.Count -eq 0 -and (Test-Path -LiteralPath $recordPath -PathType Leaf)) {
            Remove-Item -LiteralPath $recordPath -Force
        }
    }
    catch {
        $why = "cannot verify: the probe stopped: $($_.Exception.Message)"
        if ($restoreFailed.Count -gt 0) { $why += '; restore incomplete (' + ($restoreFailed -join '; ') + ')' }
        elseif ($recordPath -and (Test-Path -LiteralPath $recordPath -PathType Leaf)) { Remove-Item -LiteralPath $recordPath -Force }
        return (& $finish $codes.Refused 'refused' $why)
    }

    if ($restoreFailed.Count -gt 0) {
        return (& $finish $codes.Refused 'refused' ("restore incomplete (" + ($restoreFailed -join '; ') + "); the originals stay in the access-original record and the restore checkpoint remains"))
    }
    if ($findings.Count -gt 0) {
        return (& $finish $codes.Finding 'finding' "$($findings.Count) precondition finding(s) on $Screen")
    }
    return (& $finish $codes.Ok 'ok' "no precondition finding on $Screen; $($open.Count) item(s) stay open")
}

function Invoke-DaAccessRecover {
    <#
    .SYNOPSIS
        Restores the font scale and locale recorded by an interrupted probe and removes the record. Returns Ok, Reason and Steps.
    .PARAMETER Adb
        Injectable adb runner.
    .PARAMETER Path
        Record path; default access-original.json in the state folder.
    #>
    param([Parameter(Mandatory)][scriptblock]$Adb, [string]$Path)
    if (-not $Path) { $Path = Get-DaAccessOriginalPath }
    $rec = Read-DaJson -Path $Path
    if (-not $rec) { return [pscustomobject]@{ Ok = $true; Reason = 'no interrupted access probe'; Steps = @() } }
    $steps = [System.Collections.Generic.List[string]]::new()
    $ok = $true
    if ($rec.fontScale.recorded) {
        $o = [pscustomobject]@{ Kind = 'font_scale'; Recorded = $true; Present = [bool]$rec.fontScale.present; Value = $rec.fontScale.value }
        $b = Restore-DaFontScale -Adb $Adb -Original $o
        $steps.Add("font scale: $(if ($b.Ok) { 'restored' } else { $b.Reason })")
        if (-not $b.Ok) { $ok = $false }
    }
    if ($rec.locale.recorded) {
        $o = [pscustomobject]@{ Kind = 'app_locale'; Recorded = $true; Package = [string]$rec.package; Tags = @($rec.locale.tags) }
        $b = Restore-DaAppLocale -Adb $Adb -Original $o
        $steps.Add("locale: $(if ($b.Ok) { 'restored' } else { $b.Reason })")
        if (-not $b.Ok) { $ok = $false }
    }
    if ($ok) { Remove-Item -LiteralPath $Path -Force }
    return [pscustomobject]@{ Ok = $ok; Reason = $(if ($ok) { 'originals restored' } else { 'restore incomplete; the record is kept' }); Steps = $steps.ToArray() }
}

# ---------------------------------------------------------------------------------------------
# Message copy (docs/COMMUNICATION_POLICY.md section 6)
# ---------------------------------------------------------------------------------------------

function Get-DaCopyPatterns {
    <#
    .SYNOPSIS
        Regular expressions of the decidable copy checks, per check and language; ASCII source, Unicode through escapes.
    #>
    $q = '[?\u061f\uff1f]'
    return [ordered]@{
        QuestionMark = $q
        RawException = @(
            '\b(?:[a-z_][a-z0-9_]*\.){2,}[A-Z][A-Za-z0-9]*(?:Exception|Error|Throwable)\b',
            '\b[A-Z][A-Za-z0-9]+(?:Exception|Throwable)\b',
            '\b[A-Z][A-Za-z0-9]+Error\b',
            '\bat\s+[\w$.]+\([\w$.]+(?::\d+)?\)',
            '\bCaused by:',
            '\b(?:STATUS|ERROR|SQLITE|HTTP)_[A-Z0-9_]{3,}\b',
            '\bE(?:NOENT|ACCES|PERM|IO|NOSPC|EXIST|ROFS|HOSTUNREACH|CONNREFUSED|CONNRESET|TIMEDOUT|NETUNREACH)\b',
            '(?i)\b(?:errno|error code|err|error|code|status)\s*[:=#]?\s*-?\d{2,}\b',
            '\b0x[0-9a-fA-F]{4,}\b',
            '(?i)^\s*(?:null|undefined|none|nil)\s*$',
            '^\s*[{\[]\s*"[^"]+"\s*:'
        )
        SuccessBoilerplate = @(
            '(?i)\bsuccessful(?:ly)?\b',
            '(?i)\boperation\s+(?:has\s+been\s+|was\s+)?(?:completed|performed|executed|finished)\b',
            '(?i)\b\u0443\u0441\u043f\u0435\u0448\u043d\w*',
            '(?i)\b\u043e\u043f\u0435\u0440\u0430\u0446\u0438\u044f\s+(?:\u0431\u044b\u043b\u0430\s+)?(?:\u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430|\u0432\u044b\u043f\u043e\u043b\u043d\u0435\u043d\u0430)\b',
            '(?i)\b\u0443\u0441\u043f\u0456\u0448\u043d\w*',
            '(?i)\b\u043e\u043f\u0435\u0440\u0430\u0446\u0456\u044f\s+(?:\u0431\u0443\u043b\u0430\s+)?(?:\u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430|\u0432\u0438\u043a\u043e\u043d\u0430\u043d\u0430)\b',
            '\u0628\u0646\u062c\u0627\u062d',
            '\u0627\u0643\u062a\u0645\u0644\u062a\s+\u0627\u0644\u0639\u0645\u0644\u064a\u0629',
            '\u062a\u0645\u062a\s+\u0627\u0644\u0639\u0645\u0644\u064a\u0629'
        )
        NextStep = @(
            '(?i)(?:^|[.!?;:,]\s*|\s[-\u2013\u2014]\s*|\b(?:please|then|you can|you may|you should|try to)\s+)(?:try|retry|check|make sure|ensure|open|go to|grant|allow|choose|select|add|free up|connect|reconnect|wait|restart|sign in|log in|update|contact|report|tap|press|enable|disable|turn on|turn off|use|insert|plug|remove|rename|pick|scan|install|reinstall)\b',
            '(?i)\b(?:try again|again later|later|retry)\b',
            '(?i)\b(?:\u043f\u043e\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435|\u043f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435|\u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u0435|\u0443\u0431\u0435\u0434\u0438\u0442\u0435\u0441\u044c|\u043e\u0442\u043a\u0440\u043e\u0439\u0442\u0435|\u0440\u0430\u0437\u0440\u0435\u0448\u0438\u0442\u0435|\u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435|\u043f\u043e\u0434\u043e\u0436\u0434\u0438\u0442\u0435|\u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0438\u0442\u0435\u0441\u044c|\u0432\u043a\u043b\u044e\u0447\u0438\u0442\u0435|\u043f\u0435\u0440\u0435\u0437\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u0435|\u043e\u0431\u0440\u0430\u0442\u0438\u0442\u0435\u0441\u044c|\u0434\u043e\u0431\u0430\u0432\u044c\u0442\u0435|\u043e\u0441\u0432\u043e\u0431\u043e\u0434\u0438\u0442\u0435|\u043d\u0430\u0436\u043c\u0438\u0442\u0435|\u0432\u043e\u0439\u0434\u0438\u0442\u0435|\u043e\u0431\u043d\u043e\u0432\u0438\u0442\u0435|\u043f\u043e\u0437\u0436\u0435)\b',
            '(?i)\b\u0435\u0449[\u0435\u0451] \u0440\u0430\u0437\b',
            '(?i)\b(?:\u0441\u043f\u0440\u043e\u0431\u0443\u0439\u0442\u0435|\u043f\u0435\u0440\u0435\u0432\u0456\u0440\u0442\u0435|\u043f\u043e\u0432\u0442\u043e\u0440\u0456\u0442\u044c|\u043f\u0435\u0440\u0435\u043a\u043e\u043d\u0430\u0439\u0442\u0435\u0441\u044f|\u0432\u0456\u0434\u043a\u0440\u0438\u0439\u0442\u0435|\u0434\u043e\u0437\u0432\u043e\u043b\u044c\u0442\u0435|\u0432\u0438\u0431\u0435\u0440\u0456\u0442\u044c|\u043e\u0431\u0435\u0440\u0456\u0442\u044c|\u0437\u0430\u0447\u0435\u043a\u0430\u0439\u0442\u0435|\u043f\u0456\u0434\u043a\u043b\u044e\u0447\u0456\u0442\u044c\u0441\u044f|\u0443\u0432\u0456\u043c\u043a\u043d\u0456\u0442\u044c|\u043f\u0435\u0440\u0435\u0437\u0430\u043f\u0443\u0441\u0442\u0456\u0442\u044c|\u0437\u0432\u0435\u0440\u043d\u0456\u0442\u044c\u0441\u044f|\u0434\u043e\u0434\u0430\u0439\u0442\u0435|\u0437\u0432\u0456\u043b\u044c\u043d\u0456\u0442\u044c|\u043d\u0430\u0442\u0438\u0441\u043d\u0456\u0442\u044c|\u0443\u0432\u0456\u0439\u0434\u0456\u0442\u044c|\u043e\u043d\u043e\u0432\u0456\u0442\u044c|\u043f\u0456\u0437\u043d\u0456\u0448\u0435)\b',
            '(?i)\b\u0449\u0435 \u0440\u0430\u0437\b',
            '\u062d\u0627\u0648\u0644|\u062a\u062d\u0642\u0642|\u0623\u0639\u062f|\u062a\u0623\u0643\u062f|\u0627\u0641\u062a\u062d|\u0627\u062e\u062a\u0631|\u0627\u0633\u0645\u062d|\u0627\u0646\u062a\u0638\u0631|\u0627\u062a\u0635\u0644|\u0644\u0627\u062d\u0642|\u0645\u0631\u0629 \u0623\u062e\u0631\u0649|\u0645\u0631\u0647 \u0627\u062e\u0631\u0649'
        )
        ConfirmOpener = @(
            '(?i)^\s*(?:are you sure|are you certain|do you really want|do you want to|really|sure|continue|proceed|confirm|ok to proceed)\b',
            '(?i)^\s*(?:\u0432\u044b \u0443\u0432\u0435\u0440\u0435\u043d\u044b|\u0443\u0432\u0435\u0440\u0435\u043d\u044b \u043b\u0438 \u0432\u044b|\u0432\u044b \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0442\u0435\u043b\u044c\u043d\u043e \u0445\u043e\u0442\u0438\u0442\u0435|\u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0442\u0435\u043b\u044c\u043d\u043e \u0445\u043e\u0442\u0438\u0442\u0435|\u0442\u043e\u0447\u043d\u043e|\u043f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0442\u044c)',
            '(?i)^\s*(?:\u0432\u0438 \u0432\u043f\u0435\u0432\u043d\u0435\u043d\u0456|\u0432\u043f\u0435\u0432\u043d\u0435\u043d\u0456|\u0432\u0438 \u0434\u0456\u0439\u0441\u043d\u043e \u0445\u043e\u0447\u0435\u0442\u0435|\u0434\u0456\u0439\u0441\u043d\u043e \u0445\u043e\u0447\u0435\u0442\u0435|\u0442\u043e\u0447\u043d\u043e|\u043f\u0440\u043e\u0434\u043e\u0432\u0436\u0438\u0442\u0438)',
            '^\s*(?:\u0647\u0644 \u0623\u0646\u062a \u0645\u062a\u0623\u0643\u062f|\u0647\u0644 \u062a\u0631\u064a\u062f \u0627\u0644\u0645\u062a\u0627\u0628\u0639\u0629|\u0647\u0644 \u062a\u0631\u063a\u0628 \u0641\u064a \u0627\u0644\u0645\u062a\u0627\u0628\u0639\u0629)'
        )
        Consequence = @(
            '(?i)\b(?:will|won''t|will not|cannot|can''t|can not|permanently|irreversibl\w*|undone|lost|no longer|forever|overwritten|erased)\b',
            '\d',
            '(?i)\b(?:\u0431\u0443\u0434\u0435\u0442|\u0431\u0443\u0434\u0443\u0442|\u043d\u0435\u043b\u044c\u0437\u044f|\u043d\u0435\u0432\u043e\u0437\u043c\u043e\u0436\u043d\u043e|\u0431\u0435\u0437\u0432\u043e\u0437\u0432\u0440\u0430\u0442\u043d\u043e|\u043d\u0430\u0432\u0441\u0435\u0433\u0434\u0430|\u0443\u0434\u0430\u043b\u0438\u0442|\u0443\u0434\u0430\u043b\u0435\u043d\u044b|\u043f\u043e\u0442\u0435\u0440\u044f\u043d\u044b)\b',
            '(?i)\b(?:\u0431\u0443\u0434\u0435|\u0431\u0443\u0434\u0443\u0442\u044c|\u043d\u0435 \u043c\u043e\u0436\u043d\u0430|\u043d\u0435\u043c\u043e\u0436\u043b\u0438\u0432\u043e|\u0431\u0435\u0437\u043f\u043e\u0432\u043e\u0440\u043e\u0442\u043d\u043e|\u043d\u0430\u0437\u0430\u0432\u0436\u0434\u0438|\u0432\u0438\u0434\u0430\u043b\u0438\u0442\u044c|\u0432\u0438\u0434\u0430\u043b\u0435\u043d\u043e|\u0432\u0442\u0440\u0430\u0447\u0435\u043d\w*)\b',
            '\u0633\u064a\u062a\u0645|\u0644\u0627 \u064a\u0645\u0643\u0646|\u0646\u0647\u0627\u0626\u064a|\u0628\u0634\u0643\u0644 \u062f\u0627\u0626\u0645'
        )
        Emoji = @('[\uD83C-\uD83E][\uDC00-\uDFFF]', '[\u2b50\u2764\u2705\u274c]')
    }
}

function Find-DaCopyPattern {
    <#
    .SYNOPSIS
        The first fragment of a text that one of the patterns matches, or null.
    .PARAMETER Text
        Text to scan.
    .PARAMETER Patterns
        Regular expressions.
    #>
    param([AllowNull()][AllowEmptyString()][string]$Text, [Parameter(Mandatory)][string[]]$Patterns)
    if ([string]::IsNullOrEmpty($Text)) { return $null }
    foreach ($p in $Patterns) {
        $m = [regex]::Match($Text, $p)
        if ($m.Success -and $m.Length -gt 0) { return $m.Value }
    }
    return $null
}

function New-DaCopyCheck {
    <#
    .SYNOPSIS
        One check row of a copy review: item, policy reference, status, rung, evidence and detail.
    .PARAMETER Item
        Check id.
    .PARAMETER Policy
        Where the policy states it.
    .PARAMETER Status
        pass, fail, manual or n/a.
    .PARAMETER Rung
        V for a mechanical result on observed text, R for a judgement left to a reviewer.
    .PARAMETER Evidence
        The matched fragment, trimmed.
    .PARAMETER Detail
        One-line reason.
    #>
    param(
        [Parameter(Mandatory)][string]$Item,
        [Parameter(Mandatory)][string]$Policy,
        [Parameter(Mandatory)][ValidateSet('pass', 'fail', 'manual', 'n/a')][string]$Status,
        [Parameter(Mandatory)][ValidateSet('V', 'R')][string]$Rung,
        [string]$Evidence = '',
        [string]$Detail = ''
    )
    $ev = $Evidence
    if ($ev.Length -gt 80) { $ev = $ev.Substring(0, 80) }
    return [pscustomobject]@{ Item = $Item; Policy = $Policy; Status = $Status; Rung = $Rung; Evidence = $ev; Detail = $Detail }
}

function ConvertTo-DaCopyRecord {
    <#
    .SYNOPSIS
        Normalises a captured message (a string or an object with Id, Kind, Locale, Title, Text, Details, Buttons).
    .PARAMETER Message
        Captured message.
    .PARAMETER Ordinal
        Position, used for the default id.
    #>
    param([Parameter(Mandatory)]$Message, [Parameter(Mandatory)][int]$Ordinal)
    $kinds = @('error', 'error-dialog', 'error-network', 'confirm', 'success', 'toast', 'empty', 'progress', 'info', 'unknown')
    $get = { param($name) if ($Message.PSObject.Properties.Name -contains $name) { $Message.$name } else { $null } }
    if ($Message -is [string]) {
        return [pscustomobject]@{ Id = "M$Ordinal"; Kind = 'unknown'; Locale = ''; Title = ''; Text = [string]$Message; Details = ''; Buttons = @() }
    }
    $kind = [string](& $get 'Kind')
    if ($kinds -notcontains $kind) { $kind = 'unknown' }
    $id = [string](& $get 'Id')
    return [pscustomobject]@{
        Id      = $(if ($id) { $id } else { "M$Ordinal" })
        Kind    = $kind
        Locale  = [string](& $get 'Locale')
        Title   = [string](& $get 'Title')
        Text    = [string](& $get 'Text')
        Details = [string](& $get 'Details')
        Buttons = @((& $get 'Buttons') | Where-Object { $_ } | ForEach-Object { [string]$_ })
    }
}

function Test-DaMessageCopy {
    <#
    .SYNOPSIS
        Reviews captured message text against the tone checklist of docs/COMMUNICATION_POLICY.md section 6.
    .DESCRIPTION
        Scored (rung V, status pass or fail): raw exception text or an error code as the primary
        message, a confirmation that states no consequence, a success phrase of the "operation
        completed successfully" kind, an error with no next step, and an emoji image. Judgement items
        (tone, an empty-state invitation, fit at 360 dp, EN/RU/UK parity, legal text, a human
        explanation) are returned with status manual and rung R and are never counted as failures.
        Matching is by pattern in en, ru, uk and ar, so a pass is "no known defect shape found", not a
        proof of good copy. The primary message is the title plus the text; Details may carry the
        technical part and is not scanned.
    .PARAMETER Message
        One or more captured messages: strings, or objects with Id, Kind (error, error-dialog,
        error-network, confirm, success, toast, empty, progress, info), Locale, Title, Text, Details
        and Buttons.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Message)
    $pat = Get-DaCopyPatterns
    $policy = 'docs/COMMUNICATION_POLICY.md section 6'
    $results = [System.Collections.Generic.List[object]]::new()
    $i = 0
    foreach ($m in $Message) {
        $i++
        $rec = ConvertTo-DaCopyRecord -Message $m -Ordinal $i
        $checks = [System.Collections.Generic.List[object]]::new()
        $primary = ((@($rec.Title, $rec.Text) | Where-Object { $_ }) -join ' ').Trim()
        $isError = $rec.Kind -in @('error', 'error-dialog', 'error-network')

        if ([string]::IsNullOrWhiteSpace($primary)) {
            $checks.Add((New-DaCopyCheck -Item 'text-captured' -Policy $policy -Status 'manual' -Rung 'R' -Detail 'no text was captured for this message, so no decidable item was judged'))
        }
        else {
            # 6 item 1: raw exception text or an error code as the primary message.
            $raw = Find-DaCopyPattern -Text $primary -Patterns $pat.RawException
            if ($raw) { $checks.Add((New-DaCopyCheck -Item 'no-raw-exception' -Policy "$policy item 1; 2.2" -Status 'fail' -Rung 'V' -Evidence $raw -Detail 'raw exception text or an error code is the primary message; it belongs in a Details section')) }
            else { $checks.Add((New-DaCopyCheck -Item 'no-raw-exception' -Policy "$policy item 1; 2.2" -Status 'pass' -Rung 'V')) }

            # 6 item 3: "operation completed successfully" phrasing.
            $succ = Find-DaCopyPattern -Text $primary -Patterns $pat.SuccessBoilerplate
            if ($succ) { $checks.Add((New-DaCopyCheck -Item 'no-success-boilerplate' -Policy "$policy item 3; 2.6" -Status 'fail' -Rung 'V' -Evidence $succ -Detail 'a success phrase of the operation-completed-successfully kind')) }
            else { $checks.Add((New-DaCopyCheck -Item 'no-success-boilerplate' -Policy "$policy item 3; 2.6" -Status 'pass' -Rung 'V')) }

            # 6 item 8: emoji images.
            $emoji = Find-DaCopyPattern -Text $primary -Patterns $pat.Emoji
            if ($emoji) { $checks.Add((New-DaCopyCheck -Item 'no-emoji-image' -Policy "$policy item 8; 1" -Status 'fail' -Rung 'V' -Evidence $emoji -Detail 'an emoji image appears in the message')) }
            else { $checks.Add((New-DaCopyCheck -Item 'no-emoji-image' -Policy "$policy item 8; 1" -Status 'pass' -Rung 'V')) }

            # 6 item 2: a confirmation must state what will happen.
            if ($rec.Kind -eq 'confirm') {
                $sentences = @([regex]::Split($primary, '(?<=[.!?\u061f\uff1f])\s+') | Where-Object { $_.Trim() })
                $declarative = @($sentences | Where-Object {
                        $_ -notmatch ($pat.QuestionMark + '\s*$') -and ([regex]::Matches($_, '[\p{L}\p{N}]+')).Count -ge 3
                    })
                $cue = Find-DaCopyPattern -Text $primary -Patterns $pat.Consequence
                $opener = Find-DaCopyPattern -Text $primary -Patterns $pat.ConfirmOpener
                if ($declarative.Count -gt 0 -or $cue) {
                    $checks.Add((New-DaCopyCheck -Item 'confirm-states-consequence' -Policy "$policy item 2; 2.7" -Status 'pass' -Rung 'V' -Evidence $(if ($cue) { $cue } else { $declarative[0] })))
                }
                elseif ($opener) {
                    $checks.Add((New-DaCopyCheck -Item 'confirm-states-consequence' -Policy "$policy item 2; 2.7" -Status 'fail' -Rung 'V' -Evidence $opener -Detail 'a bare confirmation: it asks and states no consequence'))
                }
                else {
                    $checks.Add((New-DaCopyCheck -Item 'confirm-states-consequence' -Policy "$policy item 2; 2.7" -Status 'manual' -Rung 'R' -Detail 'a short question that names the action but no consequence; a reviewer judges whether that is enough'))
                }
            }

            # 6 item 4: an error needs a next step.
            if ($isError) {
                $stepText = (@($primary) + @($rec.Buttons)) -join '. '
                $step = Find-DaCopyPattern -Text $stepText -Patterns $pat.NextStep
                $words = ([regex]::Matches($primary, '[\p{L}\p{N}]+')).Count
                if ($step) { $checks.Add((New-DaCopyCheck -Item 'error-next-step' -Policy "$policy item 4; 2.2, 2.3, 2.8" -Status 'pass' -Rung 'V' -Evidence $step)) }
                elseif ($rec.Kind -eq 'error' -and $words -ge 4) {
                    $checks.Add((New-DaCopyCheck -Item 'error-next-step' -Policy "$policy item 4; 2.2" -Status 'manual' -Rung 'R' -Detail 'no next step found in an inline error; the policy asks for one only if available, so a reviewer judges whether one was'))
                }
                else {
                    $checks.Add((New-DaCopyCheck -Item 'error-next-step' -Policy "$policy item 4; 2.2, 2.3, 2.8" -Status 'fail' -Rung 'V' -Detail 'the error names no next step and offers no action button that does'))
                }
            }
        }

        # Judgement items: never scored.
        $manual = @(
            @('tone-and-voice', 'COMMUNICATION_POLICY section 1', 'friendly, clear, brief; no bureaucratic construction'),
            @('fits-360dp', "$policy item 6", 'spot-check at 360 dp width without truncation (clip check of a captured frame)'),
            @('locale-parity', "$policy item 7", 'run scripts/check_strings_localized.ps1; parity is a property of the string set, not of one capture'),
            @('legal-and-machine-text-untouched', "$policy item 9", 'legal texts and machine-readable artifacts are not edited by the audit')
        )
        if ($isError) {
            $manual += , @('human-explanation', "$policy item 4", 'the text says what happened in human terms')
            $manual += , @('single-next-step-channel', 'COMMUNICATION_POLICY section 3', 'at most one contextual channel, never in a toast')
        }
        if ($rec.Kind -eq 'empty') { $manual += , @('empty-state-invitation', "$policy item 5; 2.4", 'the empty state explains why and invites an action') }
        foreach ($mi in $manual) {
            $checks.Add((New-DaCopyCheck -Item $mi[0] -Policy $mi[1] -Status 'manual' -Rung 'R' -Detail $mi[2]))
        }

        $failed = @($checks | Where-Object { $_.Status -eq 'fail' }).Count
        $results.Add([pscustomobject]@{
                Id     = $rec.Id
                Kind   = $rec.Kind
                Locale = $rec.Locale
                Text   = $primary
                Checks = $checks.ToArray()
                Failed = $failed
                Passed = @($checks | Where-Object { $_.Status -eq 'pass' }).Count
                Manual = @($checks | Where-Object { $_.Status -eq 'manual' }).Count
            })
    }
    $arr = $results.ToArray()
    return [pscustomobject]@{
        Policy   = $policy
        Messages = $arr.Count
        Failed   = [int](@($arr | Measure-Object -Property Failed -Sum).Sum)
        Manual   = [int](@($arr | Measure-Object -Property Manual -Sum).Sum)
        Results  = $arr
    }
}
