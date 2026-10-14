<#
.SYNOPSIS
    Builds the S4166 dynamic-audit coverage data: manifest, capability list, scenes, budgets, matrix.

.DESCRIPTION
    Dot-source this file to get the functions below. It defines functions only, assigns nothing at
    script scope and never calls exit: an unreadable or inconsistent source THROWS, and the caller
    (the da.ps1 facade, a scratch driver, a test) maps that to its own exit code. Nothing in it
    reads the clock, a random source or the machine locale, so the same sources always produce
    byte-identical files (UTF-8 without BOM, LF, two-space pretty JSON, ordinal sort order).

    Functions, in the order the wrapper calls them:

      Get-DaInventory         resolves the inventory chain from the pointers in CLAUDE.md and
                              .sza-profile.json (paths.allFeatures, paths.allFeaturesPrivate,
                              paths.allFeaturesSchema, paths.featureMatrix), reads the public ledger
                              and the private ledger when present, hashes every source after CRLF to
                              LF normalisation, applies the exclusion rules and builds the manifest.
      Build-DaCapabilities    maps every capability to a scene, assigns its stratum and marks the
                              first-pass representative of each stratum.
      New-DaScenes            the scene catalog C01..C17 with atomic scenes.
      New-DaBudgets           every row of strategic section 5.5 as a hypothesis, version h1.
      New-DaMatrix            capability cells and scene cells; every applicable cell starts open.
      Write-DaAuditData       writes manifest.json, capabilities.json, scenes.json, budgets.json and
                              matrix.json into the audit folder (builds what it is not handed).
      Invoke-DaBuildInventory thin wrapper that calls the five builders and the writer in order.

    DENOMINATOR. A capability is an ACTIVE ledger record of the public ledger plus the private
    ledger when it is present, minus the explicit exclusions: records with status removed (listed
    for visibility, they are not active) and records whose id starts with tooling. (developer
    machinery, area General). A record that vanishes without an exclusion row is a shrink for the
    validator. Same-name id groups stay separate capabilities and are listed, never merged.
    manifest.floor.activeMin is the number of capabilities after exclusion, so comparing it with
    either the raw active count or the capability count can never false-fail.

    SCENE BY AREA. The area map is one function (Get-DaSceneAreas) and is also the `areas` list of
    scenes.json: C01 Setup & Onboarding; C05 Media Browsing, Main Screen; C06 ten viewer and player
    areas; C07 File Operations, Destinations; C08 Settings & Navigation, Localization, Permissions;
    C09 Launcher, Sharing; C10 Network & Cloud, Sources & Storage; C11 OCR & Translation, Camera,
    Screen Capture, Widgets, Broadcast, Wear OS, Usage Statistics, Diagnostics, Programs & Tools,
    Extensions & On-demand. C02, C03, C04, C12..C17 bind no inventory area.

    GENERAL RECORDS. Area General has no scene by area, so a small id-prefix table
    (Get-DaGeneralPrefixMap) names, per id prefix, the inventory area whose subject the id itself
    states; the scene follows from that area. Longest prefix wins. A General record that matches no
    row keeps scene = null and is a diagnosed coverage gap, never a guessed scene and never dropped.
    Prefixes are anchored on subjects the id names outright (backup, settings-search, video-player,
    sftp-resource, ocr, ..); cross-cutting UI polish (dialog-*, dpad-*, icon-only-*, landscape-*,
    ui.*) and distribution.* stay unmapped on purpose.

    STRATUM AND SAMPLE. stratum = area + reach set inside standard / noLegal / vr; for the Wear OS
    area (and any record that carries wearFlavors) the watch variant set, default standard and
    noLegal. A General record uses General/<scene> as its area so one stratum never spans scenes. A
    capability reachable in none of the three first-pass variants has reach outside-first-pass and
    is never sampled. The representative of a stratum is the lowest id in ordinal sort order; its
    cells on the first variant of the priority standard, noLegal, vr are flagged sampled, and a
    watch representative runs both reviewed geometries (small-round, large-round) on the first of
    wear-standard, wear-noLegal. Locale en for the run, plus one extra ar (RTL) walk per scene C05
    to C11 on the standard variant for the first sampled representative with a phone standard
    reach, preferring one that is neither a General record nor a network or cloud source. Sampling
    changes a flag, never the row count.

    CELL SHAPE. cells key = capability|scene|variant|context|property, sceneCells key =
    scene|atomic|variant|context|property, context = locale/input/state (the watch geometry id is
    the state). Properties O, E, S, L, U. Status is open or n/a, never measured here. An open cell
    carries reason, owner and nextStep; an n/a cell carries reason, source and condition. A scene
    is judged through sceneCells when it is not capabilityScoped or when it binds no inventory area
    (C02, C14 and C15 would otherwise have no cell at all).

    RESEARCH. Denominator, mapping, sampling: research/02__capability-inventory-and-sampling.md.
    JSON contract: research/04__audit-data-schema.md. Script obligations: research/06.
#>

function Get-DaFirstPassVariants {
    <#
    .SYNOPSIS
        The phone variants the first pass can reach, in sampling priority order.
    #>
    [CmdletBinding()]
    param()
    return @('standard', 'noLegal', 'vr')
}

function Get-DaPropertyIds {
    <#
    .SYNOPSIS
        The five audited properties: responsiveness, errors, system blocking, leaks, understanding.
    #>
    [CmdletBinding()]
    param()
    return @('O', 'E', 'S', 'L', 'U')
}

function Get-DaSceneAreas {
    <#
    .SYNOPSIS
        Scene id to the inventory areas it covers; the single source of the scene-by-area map.
    #>
    [CmdletBinding()]
    param()
    $map = [ordered]@{}
    $map['C01'] = @('Setup & Onboarding')
    $map['C02'] = @()
    $map['C03'] = @()
    $map['C04'] = @()
    $map['C05'] = @('Main Screen', 'Media Browsing')
    $map['C06'] = @('Audio Player', 'Documents', 'Drawing & Annotations', 'Image & GIF Viewer',
        'Media Player', 'Slideshow', 'Streams', 'Text Editor', 'Video Player', 'VR & OpenXR')
    $map['C07'] = @('Destinations', 'File Operations')
    $map['C08'] = @('Localization', 'Permissions', 'Settings & Navigation')
    $map['C09'] = @('Launcher', 'Sharing')
    $map['C10'] = @('Network & Cloud', 'Sources & Storage')
    $map['C11'] = @('Broadcast', 'Camera', 'Diagnostics', 'Extensions & On-demand', 'OCR & Translation',
        'Programs & Tools', 'Screen Capture', 'Usage Statistics', 'Wear OS', 'Widgets')
    $map['C12'] = @()
    $map['C13'] = @()
    $map['C14'] = @()
    $map['C15'] = @()
    $map['C16'] = @()
    $map['C17'] = @()
    return $map
}

function Get-DaGeneralPrefixMap {
    <#
    .SYNOPSIS
        The id-prefix table that assigns General records to the area whose subject the id names.
    .DESCRIPTION
        Matching is an ordinal StartsWith on the full id, longest prefix first. The area is then
        looked up in Get-DaSceneAreas, so the scene is always the one the area already has. Only
        ids that name their subject outright are listed; everything else stays scene = null.
    #>
    [CmdletBinding()]
    param()
    $rows = @(
        # Settings backup and restore, settings search, settings rows: the Settings surface.
        @('general.backup-', 'Settings & Navigation'),
        @('general.compact-elements-toggle', 'Settings & Navigation'),
        @('general.favorites-share-export-import', 'Settings & Navigation'),
        @('general.scheduled-operations-master-toggle', 'Settings & Navigation'),
        @('general.settings-', 'Settings & Navigation'),
        # Interface language and permission gating.
        @('general.audio-related-permissions-', 'Permissions'),
        @('general.every-screen-speaks-the-app-language', 'Localization'),
        @('general.interface-language-', 'Localization'),
        # First launch.
        @('general.enable-all-onboarding-', 'Setup & Onboarding'),
        @('general.splash-', 'Setup & Onboarding'),
        # Browsing.
        @('general.main-screen-', 'Main Screen'),
        @('general.mouse-band-select-', 'Media Browsing'),
        # Viewers and players.
        @('general.draw-editor-', 'Drawing & Annotations'),
        @('general.live-streams-', 'Streams'),
        @('general.manually-added-http-stream-urls-', 'Streams'),
        @('general.player-settings-dialog-', 'Media Player'),
        @('general.standalone-document-viewer-', 'Documents'),
        @('general.streams-search-field-', 'Streams'),
        @('general.video-player-', 'Video Player'),
        @('general.vr-install-cta-', 'VR & OpenXR'),
        # Sources, sharing, launcher, capture.
        @('general.browser-sign-in-', 'Network & Cloud'),
        @('general.network-folders-smb-', 'Network & Cloud'),
        @('general.sftp-resource-', 'Network & Cloud'),
        @('general.quick-launch-panel-', 'Launcher'),
        @('general.receiving-a-shared-link-', 'Sharing'),
        @('general.ocr-', 'OCR & Translation'),
        @('general.screenshot-gesture-', 'Screen Capture')
    )
    $out = [System.Collections.Generic.List[object]]::new()
    foreach ($r in $rows) {
        $out.Add([pscustomobject]@{ Prefix = [string]$r[0]; Area = [string]$r[1] })
    }
    # Longest prefix first, ties by ordinal text, so the lookup is deterministic and specific wins.
    # An explicit comparer, because Sort-Object follows the session culture and a hyphen sorts
    # differently under different cultures.
    $out.Sort([System.Comparison[object]] {
            param($a, $b)
            $c = $b.Prefix.Length.CompareTo($a.Prefix.Length)
            if ($c -ne 0) { return $c }
            return [string]::CompareOrdinal($a.Prefix, $b.Prefix)
        })
    return @($out)
}

function Sort-DaOrdinal {
    <#
    .SYNOPSIS
        Sorts strings by ordinal code-unit order, independent of the culture of the session.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyCollection()][string[]] $Values)
    $copy = [string[]]@($Values)
    [System.Array]::Sort($copy, [System.StringComparer]::Ordinal)
    return , $copy
}

function Get-DaLfNormalisedSha256 {
    <#
    .SYNOPSIS
        Lower-case SHA-256 of a file after CRLF to LF normalisation (byte exact otherwise).
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string] $Path)
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    # Latin1 maps every byte to one char, so the round trip cannot alter anything but the CR of CRLF.
    $latin = [System.Text.Encoding]::Latin1
    $text = $latin.GetString($bytes).Replace("`r`n", "`n")
    $hash = [System.Security.Cryptography.SHA256]::HashData($latin.GetBytes($text))
    return [System.Convert]::ToHexString($hash).ToLowerInvariant()
}

function ConvertTo-DaJsonString {
    <#
    .SYNOPSIS
        Quotes a string for JSON without touching non-ASCII text or the characters & < > '.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Text)
    if ($Text -notmatch '[\\"\u0000-\u001f]') { return '"' + $Text + '"' }
    $sb = [System.Text.StringBuilder]::new($Text.Length + 8)
    [void]$sb.Append('"')
    foreach ($ch in $Text.ToCharArray()) {
        switch ($ch) {
            '"' { [void]$sb.Append('\"') }
            '\' { [void]$sb.Append('\\') }
            "`n" { [void]$sb.Append('\n') }
            "`r" { [void]$sb.Append('\r') }
            "`t" { [void]$sb.Append('\t') }
            default {
                if ([int]$ch -lt 32) { [void]$sb.Append(('\u{0:x4}' -f [int]$ch)) } else { [void]$sb.Append($ch) }
            }
        }
    }
    [void]$sb.Append('"')
    return $sb.ToString()
}

function Add-DaJsonValue {
    <#
    .SYNOPSIS
        Appends one value to a StringBuilder as two-space pretty JSON (dictionaries keep their order).
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][System.Text.StringBuilder] $Builder,
        [AllowNull()] $Value,
        [int] $Level = 0
    )
    if ($null -eq $Value) { [void]$Builder.Append('null'); return }
    if ($Value -is [string]) { [void]$Builder.Append((ConvertTo-DaJsonString -Text $Value)); return }
    if ($Value -is [bool]) { [void]$Builder.Append($(if ($Value) { 'true' } else { 'false' })); return }
    if ($Value -is [int] -or $Value -is [long] -or $Value -is [int16] -or $Value -is [byte]) {
        [void]$Builder.Append($Value.ToString([System.Globalization.CultureInfo]::InvariantCulture)); return
    }
    if ($Value -is [double] -or $Value -is [decimal] -or $Value -is [single]) {
        [void]$Builder.Append($Value.ToString([System.Globalization.CultureInfo]::InvariantCulture)); return
    }
    $pad = ' ' * (2 * ($Level + 1))
    $padEnd = ' ' * (2 * $Level)
    if ($Value -is [System.Collections.IDictionary] -or $Value -is [System.Management.Automation.PSCustomObject]) {
        $pairs = [System.Collections.Generic.List[object]]::new()
        if ($Value -is [System.Collections.IDictionary]) {
            $names = [System.Collections.Generic.List[string]]::new()
            foreach ($k in $Value.Keys) { $names.Add([string]$k) }
            if ($Value -isnot [System.Collections.Specialized.OrderedDictionary]) {
                $arr = $names.ToArray()
                [System.Array]::Sort($arr, [System.StringComparer]::Ordinal)
                $names = [System.Collections.Generic.List[string]]::new($arr)
            }
            foreach ($n in $names) { $pairs.Add(@($n, $Value[$n])) }
        } else {
            foreach ($p in $Value.PSObject.Properties) { $pairs.Add(@($p.Name, $p.Value)) }
        }
        if ($pairs.Count -eq 0) { [void]$Builder.Append('{}'); return }
        [void]$Builder.Append("{`n")
        for ($i = 0; $i -lt $pairs.Count; $i++) {
            [void]$Builder.Append($pad)
            [void]$Builder.Append((ConvertTo-DaJsonString -Text ([string]$pairs[$i][0])))
            [void]$Builder.Append(': ')
            Add-DaJsonValue -Builder $Builder -Value $pairs[$i][1] -Level ($Level + 1)
            if ($i -lt $pairs.Count - 1) { [void]$Builder.Append(',') }
            [void]$Builder.Append("`n")
        }
        [void]$Builder.Append($padEnd)
        [void]$Builder.Append('}')
        return
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        $items = [System.Collections.Generic.List[object]]::new()
        foreach ($e in $Value) { $items.Add($e) }
        if ($items.Count -eq 0) { [void]$Builder.Append('[]'); return }
        $scalar = $true
        foreach ($e in $items) {
            if ($null -ne $e -and -not ($e -is [string] -or $e -is [bool] -or $e -is [int] -or $e -is [long] -or $e -is [double])) { $scalar = $false; break }
        }
        if ($scalar -and $items.Count -le 12) {
            [void]$Builder.Append('[')
            for ($i = 0; $i -lt $items.Count; $i++) {
                if ($i -gt 0) { [void]$Builder.Append(', ') }
                Add-DaJsonValue -Builder $Builder -Value $items[$i] -Level ($Level + 1)
            }
            [void]$Builder.Append(']')
            return
        }
        [void]$Builder.Append("[`n")
        for ($i = 0; $i -lt $items.Count; $i++) {
            [void]$Builder.Append($pad)
            Add-DaJsonValue -Builder $Builder -Value $items[$i] -Level ($Level + 1)
            if ($i -lt $items.Count - 1) { [void]$Builder.Append(',') }
            [void]$Builder.Append("`n")
        }
        [void]$Builder.Append($padEnd)
        [void]$Builder.Append(']')
        return
    }
    throw "da-inventory: cannot serialise a value of type $($Value.GetType().FullName)"
}

function ConvertTo-DaJsonText {
    <#
    .SYNOPSIS
        Serialises a value to deterministic pretty JSON text with LF line ends and a final newline.
    #>
    [CmdletBinding()]
    param([AllowNull()] $Value)
    $sb = [System.Text.StringBuilder]::new(1024)
    Add-DaJsonValue -Builder $sb -Value $Value -Level 0
    [void]$sb.Append("`n")
    return $sb.ToString()
}

function Read-DaLedger {
    <#
    .SYNOPSIS
        Reads one JSONL ledger into records; an unparsable line or a missing field throws.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][ValidateSet('public', 'private')][string] $Source
    )
    $records = [System.Collections.Generic.List[object]]::new()
    $lineNo = 0
    foreach ($line in [System.IO.File]::ReadAllLines($Path, [System.Text.UTF8Encoding]::new($false))) {
        $lineNo++
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        try { $obj = $line | ConvertFrom-Json -ErrorAction Stop }
        catch { throw "da-inventory: unparsable $Source ledger line ${lineNo}: $($_.Exception.Message)" }
        foreach ($need in 'id', 'area', 'name', 'flavors') {
            if ($null -eq $obj.PSObject.Properties[$need]) { throw "da-inventory: $Source ledger line $lineNo has no field $need" }
        }
        $records.Add($obj)
    }
    return , $records.ToArray()
}

function Get-DaHashInputs {
    <#
    .SYNOPSIS
        The repo-relative sources whose hashes enter the manifest, with whether each is required.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)] $ProfilePaths)
    $rows = [System.Collections.Generic.List[object]]::new()
    $rows.Add([pscustomobject]@{ Path = [string]$ProfilePaths.allFeatures; Required = $true })
    $rows.Add([pscustomobject]@{ Path = [string]$ProfilePaths.allFeaturesPrivate; Required = $false })
    $rows.Add([pscustomobject]@{ Path = [string]$ProfilePaths.allFeaturesSchema; Required = $true })
    $rows.Add([pscustomobject]@{ Path = [string]$ProfilePaths.featureMatrix; Required = $true })
    foreach ($fixed in @('docs/flavors/flavor-matrix.json', 'app_v2/build.gradle.kts',
            'app_v2/src/main/res/xml/locales_config.xml', 'wear/src/main/res/xml/locales_config.xml',
            'docs/settings/settings-manifest.json', 'docs/settings/settings-annotations.json',
            'scripts/devtest/wear-shape-profiles.json', '.sza-profile.json', 'CLAUDE.md')) {
        $rows.Add([pscustomobject]@{ Path = $fixed; Required = $true })
    }
    return @($rows)
}

function Get-DaInventory {
    <#
    .SYNOPSIS
        Resolves the capability inventory chain, hashes the sources, applies the exclusion rules.
    .DESCRIPTION
        Returns an object with Manifest (the manifest.json content), Records (every active,
        non-excluded record, sorted by id, normalised), Exclusions, AreaEnum, FlavorEnum and
        WatchGeometries. Throws when a pointer, the profile, a required source or a ledger line
        cannot be read, because a denominator that cannot be read must not be rebuilt silently.
    .PARAMETER RepoRoot
        Repository root.
    .PARAMETER GeneratedAt
        Text for manifest.generatedAt. The default is the newest last-write time (UTC) of the
        sources, so regenerating from unchanged sources is byte identical.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [string] $GeneratedAt
    )
    $root = (Resolve-Path -LiteralPath $RepoRoot).Path
    $sep = [System.IO.Path]::DirectorySeparatorChar
    $resolve = { param($rel) Join-Path $root ($rel.Replace('/', $sep)) }

    $profilePath = & $resolve '.sza-profile.json'
    $claudePath = & $resolve 'CLAUDE.md'
    foreach ($p in @($profilePath, $claudePath)) {
        if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { throw "da-inventory: required source missing: $p" }
    }
    $prof = [System.IO.File]::ReadAllText($profilePath) | ConvertFrom-Json
    if ($null -eq $prof.paths) { throw 'da-inventory: .sza-profile.json has no paths block' }
    foreach ($k in 'allFeatures', 'allFeaturesPrivate', 'allFeaturesSchema', 'featureMatrix') {
        if ([string]::IsNullOrWhiteSpace([string]$prof.paths.$k)) { throw "da-inventory: .sza-profile.json paths.$k is not declared" }
    }
    $claudeText = [System.IO.File]::ReadAllText($claudePath)

    # Pointers: every inventory path the profile declares, and where CLAUDE.md names it too.
    $pointers = [System.Collections.Generic.List[object]]::new()
    foreach ($k in 'allFeatures', 'allFeaturesPrivate', 'allFeaturesSchema', 'featureMatrix') {
        $tok = [string]$prof.paths.$k
        $pointers.Add([ordered]@{ from = '.sza-profile.json'; token = $tok })
        if ($claudeText.Contains($tok, [System.StringComparison]::Ordinal)) {
            $pointers.Add([ordered]@{ from = 'CLAUDE.md'; token = $tok })
        }
    }
    $publicRel = [string]$prof.paths.allFeatures
    $privateRel = [string]$prof.paths.allFeaturesPrivate
    $schemaRel = [string]$prof.paths.allFeaturesSchema
    $claudeNamesPublic = $claudeText.Contains($publicRel, [System.StringComparison]::Ordinal)
    if (-not $claudeNamesPublic) {
        throw "da-inventory: CLAUDE.md no longer names the public ledger $publicRel; the inventory chain is broken"
    }
    $pointerKeys = $pointers | ForEach-Object { "$($_['token'])`n$($_['from'])" }
    $pointerOrder = Sort-DaOrdinal -Values @($pointerKeys)
    $pointerSorted = [System.Collections.Generic.List[object]]::new()
    foreach ($key in $pointerOrder) {
        foreach ($p in $pointers) { if ("$($p['token'])`n$($p['from'])" -eq $key) { $pointerSorted.Add($p); break } }
    }

    # Sources and hashes.
    $inputs = Get-DaHashInputs -ProfilePaths $prof.paths
    $sourceRows = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    $newest = [datetime]::MinValue
    foreach ($hi in $inputs) {
        $abs = & $resolve $hi.Path
        $exists = Test-Path -LiteralPath $abs -PathType Leaf
        if (-not $exists -and $hi.Required) { throw "da-inventory: required source missing: $($hi.Path)" }
        $sha = $null
        if ($exists) {
            $sha = Get-DaLfNormalisedSha256 -Path $abs
            $t = [System.IO.File]::GetLastWriteTimeUtc($abs)
            if ($t -gt $newest) { $newest = $t }
        }
        $sourceRows[$hi.Path] = [ordered]@{ path = $hi.Path; sha256 = $sha; required = [bool]$hi.Required }
    }
    $sourceOrder = Sort-DaOrdinal -Values @($sourceRows.Keys)
    $sources = [System.Collections.Generic.List[object]]::new()
    foreach ($k in $sourceOrder) { $sources.Add($sourceRows[$k]) }
    if (-not $GeneratedAt) {
        $GeneratedAt = $newest.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'", [System.Globalization.CultureInfo]::InvariantCulture)
    }

    # Schema vocabularies (area and flavor enums), never restated from memory.
    $schema = [System.IO.File]::ReadAllText((& $resolve $schemaRel)) | ConvertFrom-Json
    $areaEnum = [string[]]@($schema.properties.area.enum)
    $flavorEnum = [string[]]@($schema.properties.flavors.items.enum)
    if ($areaEnum.Count -eq 0 -or $flavorEnum.Count -eq 0) { throw 'da-inventory: the ledger schema carries no area or flavor enum' }
    $flavorCanon = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($f in $flavorEnum) { $flavorCanon[$f] = $f }

    # Ledgers.
    $publicRecords = Read-DaLedger -Path (& $resolve $publicRel) -Source public
    $privateAbs = & $resolve $privateRel
    $privatePresent = Test-Path -LiteralPath $privateAbs -PathType Leaf
    $privateRecords = @()
    $privateSha = $null
    if ($privatePresent) {
        $privateRecords = Read-DaLedger -Path $privateAbs -Source private
        $privateSha = Get-DaLfNormalisedSha256 -Path $privateAbs
    }

    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $all = [System.Collections.Generic.List[object]]::new()
    $publicActive = 0
    $privateActive = 0
    foreach ($pair in @(@('public', $publicRecords), @('private', $privateRecords))) {
        $src = [string]$pair[0]
        foreach ($raw in $pair[1]) {
            $id = [string]$raw.id
            if (-not $seen.Add($id)) { throw "da-inventory: duplicate capability id across the ledgers: $id" }
            $status = 'active'
            if ($null -ne $raw.PSObject.Properties['status'] -and -not [string]::IsNullOrWhiteSpace([string]$raw.status)) { $status = [string]$raw.status }
            $fl = [System.Collections.Generic.List[string]]::new()
            foreach ($f in @($raw.flavors)) {
                $name = [string]$f
                if ($flavorCanon.ContainsKey($name)) { $name = $flavorCanon[$name] }
                if (-not $fl.Contains($name)) { $fl.Add($name) }
            }
            $wear = $null
            if ($null -ne $raw.PSObject.Properties['wearFlavors'] -and $null -ne $raw.wearFlavors) {
                $wl = [System.Collections.Generic.List[string]]::new()
                foreach ($f in @($raw.wearFlavors)) {
                    $name = [string]$f
                    if ($flavorCanon.ContainsKey($name)) { $name = $flavorCanon[$name] }
                    if (-not $wl.Contains($name)) { $wl.Add($name) }
                }
                $wear = Sort-DaOrdinal -Values $wl.ToArray()
            }
            $gate = $null
            if ($null -ne $raw.PSObject.Properties['gate'] -and -not [string]::IsNullOrWhiteSpace([string]$raw.gate)) { $gate = [string]$raw.gate }
            if ($status -ne 'removed') { if ($src -eq 'public') { $publicActive++ } else { $privateActive++ } }
            $all.Add([pscustomobject]@{
                    Id = $id; Area = [string]$raw.area; Name = [string]$raw.name; Flavors = (Sort-DaOrdinal -Values $fl.ToArray())
                    WearFlavors = $wear; Gate = $gate; Source = $src; Status = $status
                })
        }
    }

    # Exclusions: removed and developer tooling, one reason each, sorted by id.
    $excl = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
    foreach ($r in $all) {
        if ($r.Status -eq 'removed') { $excl[$r.Id] = 'removed' }
        elseif ($r.Id.StartsWith('tooling.', [System.StringComparison]::Ordinal)) { $excl[$r.Id] = 'developer-tooling' }
    }
    $exclRows = [System.Collections.Generic.List[object]]::new()
    foreach ($k in (Sort-DaOrdinal -Values @($excl.Keys))) { $exclRows.Add([ordered]@{ id = $k; reason = $excl[$k] }) }

    # Capabilities: active and not excluded, ordinal id order.
    $byId = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    foreach ($r in $all) { if ($r.Status -ne 'removed' -and -not $excl.ContainsKey($r.Id)) { $byId[$r.Id] = $r } }
    $records = [System.Collections.Generic.List[object]]::new()
    foreach ($k in (Sort-DaOrdinal -Values @($byId.Keys))) { $records.Add($byId[$k]) }

    # Same-name groups over every active record (the research counts the excluded tooling pair too).
    $nameGroups = [System.Collections.Generic.Dictionary[string, System.Collections.Generic.List[string]]]::new([System.StringComparer]::Ordinal)
    foreach ($r in $all) {
        if ($r.Status -eq 'removed') { continue }
        $gk = "$($r.Area)`n$($r.Name)"
        if (-not $nameGroups.ContainsKey($gk)) { $nameGroups[$gk] = [System.Collections.Generic.List[string]]::new() }
        $nameGroups[$gk].Add($r.Id)
    }
    $groupRows = [System.Collections.Generic.List[object]]::new()
    $groupSort = [System.Collections.Generic.List[string]]::new()
    foreach ($g in $nameGroups.Values) {
        if ($g.Count -lt 2) { continue }
        $ids = Sort-DaOrdinal -Values $g.ToArray()
        $groupSort.Add(($ids -join "`n"))
    }
    foreach ($joined in (Sort-DaOrdinal -Values $groupSort.ToArray())) { $groupRows.Add([string[]]($joined -split "`n")) }

    # Watch geometries: the reviewed profiles of wear-shape-profiles.json, in file order.
    $wearPath = & $resolve 'scripts/devtest/wear-shape-profiles.json'
    $wearJson = [System.IO.File]::ReadAllText($wearPath) | ConvertFrom-Json
    $geoms = [System.Collections.Generic.List[string]]::new()
    foreach ($p in @($wearJson.profiles)) { if ($p.reviewedByPlay -eq $true) { $geoms.Add([string]$p.id) } }
    if ($geoms.Count -eq 0) { throw 'da-inventory: wear-shape-profiles.json lists no reviewed watch geometry' }

    $toolingCount = 0
    foreach ($e in $exclRows) { if ($e['reason'] -eq 'developer-tooling') { $toolingCount++ } }
    $activeTotal = $publicActive + $privateActive
    $capCount = $records.Count

    $manifest = [ordered]@{
        schemaVersion       = 1
        product             = 'android'
        generatedBy         = 'scripts/devtest/dynamic-audit/lib/da-inventory.ps1'
        generatedAt         = $GeneratedAt
        sources             = $sources
        pointers            = $pointerSorted
        privateLedger       = [ordered]@{
            declared = $true
            present  = [bool]$privatePresent
            count    = @($privateRecords).Count
            sha256   = $privateSha
        }
        counts              = [ordered]@{
            publicRecords = @($publicRecords).Count
            publicActive  = $publicActive
            privateActive = $privateActive
            active        = $activeTotal
            excluded      = $exclRows.Count
        }
        exclusions          = $exclRows
        duplicateNameGroups = $groupRows
        floor               = [ordered]@{ activeMin = $capCount }
        selectionRule       = [ordered]@{
            stratum         = 'area + reach set inside standard, noLegal, vr; for Wear OS and for a record with wearFlavors the watch variant set (default standard and noLegal); a General record uses General/<scene> as its area; a capability reachable in none of the three has reach outside-first-pass and is never sampled'
            representative  = 'lowest id in ordinal sort order within the stratum; its cells on the first variant of the priority are flagged sampled, a watch representative runs both reviewed geometries'
            variantPriority = @(Get-DaFirstPassVariants)
            locales         = @('en')
            rtlWalk         = [ordered]@{
                locale  = 'ar'
                scenes  = @('C05', 'C06', 'C07', 'C08', 'C09', 'C10', 'C11')
                variant = 'standard'
                target  = 'first sampled representative of the scene that reaches standard on the phone, preferring one that is neither a General record nor a network or cloud source'
            }
            generalPrefixMap = @(Get-DaGeneralPrefixMap | ForEach-Object { [ordered]@{ prefix = $_.Prefix; area = $_.Area } })
        }
    }

    return [pscustomobject]@{
        RepoRoot        = $root
        Manifest        = $manifest
        Records         = $records
        Exclusions      = $exclRows
        AreaEnum        = $areaEnum
        FlavorEnum      = $flavorEnum
        WatchGeometries = [string[]]$geoms.ToArray()
        ToolingCount    = $toolingCount
        Counts          = $manifest['counts']
    }
}

function Get-DaCapabilityReach {
    <#
    .SYNOPSIS
        The first-pass phone variants and watch variants one capability is reachable in.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $Area,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]] $Flavors,
        [AllowNull()] $WearFlavors
    )
    $phone = [System.Collections.Generic.List[string]]::new()
    if ($Area -ne 'Wear OS') {
        foreach ($v in (Get-DaFirstPassVariants)) { if ($Flavors -contains $v) { $phone.Add($v) } }
    }
    $watch = [System.Collections.Generic.List[string]]::new()
    $wearList = @()
    if ($null -ne $WearFlavors) { $wearList = @($WearFlavors) }
    if ($wearList.Count -gt 0) {
        foreach ($v in @('standard', 'noLegal')) { if ($wearList -contains $v) { $watch.Add($v) } }
    } elseif ($Area -eq 'Wear OS') {
        $watch.Add('standard'); $watch.Add('noLegal')
    }
    return [pscustomobject]@{ Phone = [string[]]$phone.ToArray(); Watch = [string[]]$watch.ToArray() }
}

function Build-DaCapabilities {
    <#
    .SYNOPSIS
        Maps capabilities to scenes and strata and marks the first-pass representative of each.
    .DESCRIPTION
        Returns Items (capabilities.json items, sorted by id), Strata (one row per stratum with its
        scene, member count and representative) and Unmapped (ids with scene null).
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Inventory)
    $sceneAreas = Get-DaSceneAreas
    $areaScene = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
    foreach ($sc in $sceneAreas.Keys) { foreach ($a in $sceneAreas[$sc]) { $areaScene[$a] = $sc } }
    $prefixes = @(Get-DaGeneralPrefixMap)

    $items = [System.Collections.Generic.List[object]]::new()
    $strata = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
    $unmapped = [System.Collections.Generic.List[string]]::new()
    foreach ($r in $Inventory.Records) {
        $scene = $null
        $stratArea = $r.Area
        if ($r.Area -eq 'General') {
            foreach ($row in $prefixes) {
                if ($r.Id.StartsWith($row.Prefix, [System.StringComparison]::Ordinal)) {
                    if ($areaScene.ContainsKey($row.Area)) { $scene = $areaScene[$row.Area] }
                    break
                }
            }
            if ($null -ne $scene) { $stratArea = "General/$scene" }
        } elseif ($areaScene.ContainsKey($r.Area)) {
            $scene = $areaScene[$r.Area]
        }
        if ($null -eq $scene) { $unmapped.Add($r.Id) }

        $reach = Get-DaCapabilityReach -Area $r.Area -Flavors $r.Flavors -WearFlavors $r.WearFlavors
        $outside = $false
        if ($r.Area -eq 'Wear OS') {
            $stratKey = "$stratArea|watch:" + ($reach.Watch -join '+')
        } elseif ($reach.Phone.Count -gt 0) {
            $stratKey = "$stratArea|" + ($reach.Phone -join '+')
        } else {
            $stratKey = "$stratArea|outside-first-pass"
            $outside = $true
        }
        if (-not $strata.ContainsKey($stratKey)) {
            $strata[$stratKey] = [ordered]@{ stratum = $stratKey; scene = $scene; count = 0; representative = $null; outside = $outside }
        }
        $row = $strata[$stratKey]
        $row['count'] = [int]$row['count'] + 1
        # Items arrive in ordinal id order, so the first one seen is the lowest id of its stratum.
        if ($null -eq $row['representative'] -and $null -ne $scene -and -not $outside) { $row['representative'] = $r.Id }

        $items.Add([ordered]@{
                id          = $r.Id
                area        = $r.Area
                name        = $r.Name
                flavors     = [string[]]$r.Flavors
                wearFlavors = $r.WearFlavors
                gate        = $r.Gate
                source      = $r.Source
                scene       = $scene
                stratum     = $stratKey
                sampled     = $false
            })
    }
    foreach ($it in $items) {
        $rep = $strata[$it['stratum']]['representative']
        $it['sampled'] = ($null -ne $rep -and $rep -eq $it['id'])
    }
    $strataRows = [System.Collections.Generic.List[object]]::new()
    foreach ($k in (Sort-DaOrdinal -Values @($strata.Keys))) { $strataRows.Add($strata[$k]) }
    return [pscustomobject]@{ Items = $items; Strata = $strataRows; Unmapped = $unmapped }
}

function New-DaAtomic {
    <#
    .SYNOPSIS
        One atomic scene row of scenes.json.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $Id,
        [Parameter(Mandatory)][string] $Title,
        [Parameter(Mandatory)][string[]] $Variants,
        [Parameter(Mandatory)][string[]] $Inputs,
        [Parameter(Mandatory)][string[]] $States,
        [bool] $Applicable = $true,
        [string] $NaReason,
        [string] $NaSource
    )
    $reason = $null
    $source = $null
    if (-not $Applicable) { $reason = $NaReason; $source = $NaSource }
    return [ordered]@{
        id         = $Id
        title      = $Title
        variants   = [string[]]$Variants
        inputs     = [string[]]$Inputs
        states     = [string[]]$States
        applicable = $Applicable
        naReason   = $reason
        naSource   = $source
    }
}

function New-DaScenes {
    <#
    .SYNOPSIS
        The scene catalog C01..C17 of strategic section 5.1 with atomic scenes.
    .DESCRIPTION
        Atomic scenes are split by action, source, failure, variant, language, input and state. A
        scene with capabilityScoped = false (C03, C04, C12, C13, C16, C17) is judged through the
        sceneCells of the matrix; C04 is n/a with the reason that an RDP session is not an Android
        app lifecycle. C02, C14 and C15 bind no inventory area and are judged through sceneCells too.
    .PARAMETER Inventory
        Optional; supplies the reviewed watch geometry ids (small-round, large-round).
    #>
    [CmdletBinding()]
    param([AllowNull()] $Inventory)
    $geo = @('small-round', 'large-round')
    if ($null -ne $Inventory -and $null -ne $Inventory.WatchGeometries) { $geo = @($Inventory.WatchGeometries) }
    $p3 = @('standard', 'noLegal', 'vr')
    $w2 = @('wear-standard', 'wear-noLegal')
    $touch = @('touch')
    $allIn = @('touch', 'keyboard', 'dpad', 'mouse')
    $def = @('default')
    $areas = Get-DaSceneAreas
    $naSrc = 'PLAN/S4166_deep-dynamic-audit.md section 5.1'
    $scenes = [System.Collections.Generic.List[object]]::new()
    $add = {
        param([string]$Id, [string]$Title, [bool]$Scoped, $Atomics)
        $scenes.Add([ordered]@{
                id               = $Id
                title            = $Title
                areas            = [string[]]@($areas[$Id])
                capabilityScoped = $Scoped
                atomic           = @($Atomics)
            })
    }
    $A = { param($i, $t, $v, $inp, $st) New-DaAtomic -Id $i -Title $t -Variants $v -Inputs $inp -States $st }

    & $add 'C01' 'First launch of an empty profile, permissions, onboarding; cold and warm start' $true @(
        (& $A 'C01.cold-start' 'Cold start of an empty profile' $p3 $touch $def),
        (& $A 'C01.warm-start' 'Warm start after the first launch' $p3 $touch $def),
        (& $A 'C01.permission-flow' 'Permission requests during first launch, granted and denied' $p3 $touch @('granted', 'denied')),
        (& $A 'C01.onboarding-flow' 'Onboarding pages, language and theme choice' $p3 @('touch', 'keyboard', 'dpad') $def)
    )
    & $add 'C02' 'Orderly exit, closing a screen, cancelling the exit; abnormal termination' $true @(
        (& $A 'C02.orderly-exit' 'Orderly exit by back or the exit command' $p3 $touch $def),
        (& $A 'C02.exit-cancelled' 'Exit cancelled before it completes' $p3 $touch $def),
        (& $A 'C02.close-screen' 'Close a window or a screen without leaving the app' $p3 $touch $def),
        (& $A 'C02.force-stop' 'Abnormal termination by force-stop' $p3 $touch $def)
    )
    & $add 'C03' 'Sleep and wake, lock and unlock during an active action and while idle' $false @(
        (& $A 'C03.sleep-wake' 'Device sleep and wake' $p3 $touch @('action-active', 'idle')),
        (& $A 'C03.lock-unlock' 'Device lock and unlock' $p3 $touch @('action-active', 'idle')),
        (& $A 'C03.activity-while-idle' 'Background work and timers while the device idles' $p3 $touch @('screen-on', 'screen-off')),
        (& $A 'C03.ambient-wake-watch' 'Watch ambient mode and wake' $w2 $touch $geo)
    )
    & $add 'C04' 'Windows only: RDP connect and disconnect, DPI or monitor change and return' $false @(
        (New-DaAtomic -Id 'C04.rdp-session' -Title 'RDP session connect, disconnect, DPI or monitor change, return' -Variants @('standard') -Inputs $touch -States $def `
            -Applicable $false -NaReason 'RDP session of a Windows host is not an Android app lifecycle' -NaSource $naSrc)
    )
    & $add 'C05' 'Open a large folder, sort, filter, search, change the source' $true @(
        (& $A 'C05.open-large-folder' 'Open a large folder' $p3 $allIn $def),
        (& $A 'C05.sort' 'Change the sort order' $p3 $allIn $def),
        (& $A 'C05.filter' 'Apply and clear a filter' $p3 $allIn $def),
        (& $A 'C05.search' 'Search by name' $p3 $allIn $def),
        (& $A 'C05.change-source' 'Switch to another source or tab' $p3 $allIn $def)
    )
    & $add 'C06' 'View a photo, GIF or document; play audio, video or a stream; page, pause, seek, stop' $true @(
        (& $A 'C06.view-image' 'Open and view an image' $p3 $allIn $def),
        (& $A 'C06.play-gif' 'Play an animated GIF' $p3 $touch $def),
        (& $A 'C06.open-document' 'Open a document or a PDF page' $p3 $allIn $def),
        (& $A 'C06.edit-text' 'Open and edit a text file' $p3 $allIn $def),
        (& $A 'C06.play-audio' 'Play an audio file' $p3 $touch $def),
        (& $A 'C06.play-video' 'Play a video file' $p3 $allIn $def),
        (& $A 'C06.play-stream' 'Play a stream' $p3 $touch $def),
        (& $A 'C06.slideshow' 'Run a slideshow' $p3 $touch $def),
        (& $A 'C06.draw-annotate' 'Draw or annotate on an image' $p3 $touch $def),
        (& $A 'C06.view-vr' 'View 3D or VR content' $p3 $touch $def),
        (& $A 'C06.page-swipe' 'Page through items with swipe or keys' $p3 $allIn $def),
        (& $A 'C06.transport-controls' 'Pause, seek and stop during playback' $p3 $allIn $def)
    )
    & $add 'C07' 'Copy, move, rename, delete, cancel; undo, trash, restore' $true @(
        (& $A 'C07.copy' 'Copy items to a destination' $p3 $touch $def),
        (& $A 'C07.move' 'Move items to a destination' $p3 $touch $def),
        (& $A 'C07.rename' 'Rename an item' $p3 $touch $def),
        (& $A 'C07.delete' 'Delete items' $p3 $touch $def),
        (& $A 'C07.cancel-operation' 'Cancel a running operation' $p3 $touch $def),
        (& $A 'C07.undo' 'Undo the last operation' $p3 $touch $def),
        (& $A 'C07.trash-restore' 'Move to trash and restore' $p3 $touch $def)
    )
    & $add 'C08' 'Change settings and toggles, PIN, language, theme, profile; restore the previous value' $true @(
        (& $A 'C08.change-toggle' 'Switch a toggle on and off' $p3 $allIn @('on', 'off')),
        (& $A 'C08.change-value' 'Change a value or a choice setting' $p3 $allIn $def),
        (& $A 'C08.set-pin' 'Set, change and clear a PIN' $p3 $touch $def),
        (& $A 'C08.switch-language' 'Switch the interface language' $p3 $touch $def),
        (& $A 'C08.switch-theme' 'Switch the colour theme' $p3 $touch $def),
        (& $A 'C08.switch-profile' 'Switch the device or resource profile' $p3 $touch $def),
        (& $A 'C08.restore-previous-value' 'Return a setting to its previous value' $p3 $touch $def),
        (& $A 'C08.grant-revoke-permission' 'Grant and revoke a runtime permission' $p3 $touch @('granted', 'revoked'))
    )
    & $add 'C09' 'Launcher, external open, share, clipboard, shortcuts; switch to a neighbouring app' $true @(
        (& $A 'C09.use-launcher' 'Use the built-in launcher' $p3 $allIn $def),
        (& $A 'C09.open-externally' 'Open a file with an external app' $p3 $touch $def),
        (& $A 'C09.share-out' 'Share items to another app' $p3 $touch $def),
        (& $A 'C09.receive-share' 'Receive a shared item or link' $p3 $touch $def),
        (& $A 'C09.clipboard' 'Copy to and paste from the clipboard' $p3 $touch $def),
        (& $A 'C09.keyboard-shortcut' 'Use a hardware keyboard shortcut' $p3 @('keyboard') $def),
        (& $A 'C09.switch-to-neighbour-app' 'Switch to a neighbouring app and back' $p3 $touch $def)
    )
    & $add 'C10' 'Connect a local, removable, network or cloud source; sign in and out; authorization expiry' $true @(
        (& $A 'C10.connect-local-source' 'Connect a local folder or storage' $p3 $touch $def),
        (& $A 'C10.connect-removable-source' 'Connect a removable or USB source' $p3 $touch $def),
        (& $A 'C10.connect-network-source' 'Connect a network share' $p3 $touch $def),
        (& $A 'C10.connect-cloud-source' 'Connect a cloud account source' $p3 $touch $def),
        (& $A 'C10.sign-in' 'Sign in to a source' $p3 $touch $def),
        (& $A 'C10.sign-out' 'Sign out of a source' $p3 $touch $def),
        (& $A 'C10.authorization-expiry' 'Authorization expires during use' $p3 $touch $def)
    )
    & $add 'C11' 'Print, OCR and translation, camera and cast, widgets, scheduler, monitoring, broadcast, companion and Wear' $true @(
        (& $A 'C11.print' 'Print an item' $p3 $touch $def),
        (& $A 'C11.ocr-translate' 'Recognise and translate text' $p3 $touch $def),
        (& $A 'C11.camera' 'Capture with the camera' $p3 $touch $def),
        (& $A 'C11.cast' 'Cast to another screen' $p3 $touch $def),
        (& $A 'C11.screen-capture' 'Capture the screen or a region' $p3 $touch $def),
        (& $A 'C11.widgets' 'Use a home screen widget' $p3 $touch $def),
        (& $A 'C11.scheduler' 'Run a scheduled operation' $p3 $touch $def),
        (& $A 'C11.monitoring' 'Network monitoring and diagnostics' $p3 $touch $def),
        (& $A 'C11.broadcast' 'Broadcast a source to other devices' $p3 $touch $def),
        (& $A 'C11.usage-statistics' 'Open usage statistics' $p3 $touch $def),
        (& $A 'C11.programs-tools' 'Use a program or a tool' $p3 $touch $def),
        (& $A 'C11.extension-on-demand' 'Download an on-demand extension' $p3 $touch $def),
        (& $A 'C11.companion-phone' 'Phone side of the watch companion' $p3 $touch $def),
        (& $A 'C11.wear-watch' 'Use the watch app' $w2 $touch $geo)
    )
    & $add 'C12' 'Input storm: held key, rapid switching, opening and closing, repeated commands' $false @(
        (& $A 'C12.held-key' 'A key held down' $p3 @('keyboard', 'dpad') $def),
        (& $A 'C12.rapid-switching' 'Rapid switching between views or items' $p3 @('touch', 'dpad') $def),
        (& $A 'C12.rapid-open-close' 'Rapid opening and closing of one screen' $p3 $touch $def),
        (& $A 'C12.repeated-command' 'The same command repeated without waiting' $p3 @('touch', 'keyboard') $def),
        (& $A 'C12.watch-key-storm' 'Rapid touch or key input on the watch' $w2 $touch $geo)
    )
    & $add 'C13' 'Event storm: file changes, reconnect, clipboard, messages and notifications, competing jobs' $false @(
        (& $A 'C13.file-change-burst' 'Burst of file system changes' $p3 $touch $def),
        (& $A 'C13.reconnect-flapping' 'Repeated reconnect of a source' $p3 $touch $def),
        (& $A 'C13.clipboard-change-burst' 'Burst of clipboard changes' $p3 $touch $def),
        (& $A 'C13.notification-burst' 'Burst of messages and notifications' $p3 $touch $def),
        (& $A 'C13.competing-jobs' 'Competing background jobs' $p3 $touch $def),
        (& $A 'C13.watch-link-flap' 'Phone link drops and returns repeatedly' $w2 $touch $geo)
    )
    & $add 'C14' 'Large inputs: 1k, 10k, 100k items, long names, Unicode and RTL, large or corrupt media and lists' $true @(
        (& $A 'C14.items-1k' 'Folder or list with 1000 items' $p3 $touch $def),
        (& $A 'C14.items-10k' 'Folder or list with 10000 items' $p3 $touch $def),
        (& $A 'C14.items-100k' 'Folder or list with 100000 items' $p3 $touch $def),
        (& $A 'C14.long-names' 'Very long names and paths' $p3 $touch $def),
        (& $A 'C14.unicode-names' 'Unicode names and mixed scripts' $p3 $touch $def),
        (& $A 'C14.rtl-names' 'Right-to-left names' $p3 $touch $def),
        (& $A 'C14.large-media' 'Very large media files' $p3 $touch $def),
        (& $A 'C14.corrupt-media' 'Corrupt or truncated media files' $p3 $touch $def),
        (& $A 'C14.large-lists' 'Very long lists of sources, programs or streams' $p3 $touch $def)
    )
    & $add 'C15' 'Failures: denied, full disk, missing, read-only or locked file, network, DNS, TLS, timeout, corrupt input' $true @(
        (& $A 'C15.permission-denied' 'Access denied' $p3 $touch $def),
        (& $A 'C15.disk-full' 'No space left on the destination' $p3 $touch $def),
        (& $A 'C15.file-missing' 'File or folder missing' $p3 $touch $def),
        (& $A 'C15.file-read-only' 'Read-only file or destination' $p3 $touch $def),
        (& $A 'C15.file-locked' 'Locked or in-use file' $p3 $touch $def),
        (& $A 'C15.network-unreachable' 'Network unreachable' $p3 $touch $def),
        (& $A 'C15.dns-failure' 'Name resolution failure' $p3 $touch $def),
        (& $A 'C15.tls-failure' 'TLS handshake or certificate failure' $p3 $touch $def),
        (& $A 'C15.timeout' 'Operation timeout' $p3 $touch $def),
        (& $A 'C15.corrupt-input' 'Corrupt or unparsable input' $p3 $touch $def)
    )
    & $add 'C16' 'Long idle with the window open or in the background, feature on and off' $false @(
        (& $A 'C16.idle-foreground' 'Idle with the window open' $p3 $touch @('feature-on', 'feature-off')),
        (& $A 'C16.idle-background' 'Idle in the background' $p3 $touch @('feature-on', 'feature-off')),
        (& $A 'C16.watch-idle' 'Idle on the watch' $w2 $touch $geo)
    )
    & $add 'C17' 'Repeat the C05-C11 cycles, change language and text size, accessible input only' $false @(
        (& $A 'C17.repeat-cycles' 'Repeat the C05 to C11 cycles' $p3 $touch $def),
        (& $A 'C17.switch-language' 'Change the language between cycles' $p3 $touch @('to-ru', 'to-uk', 'to-ar-rtl')),
        (& $A 'C17.text-size' 'Change the system text size' $p3 $touch @('large', 'largest')),
        (& $A 'C17.accessible-input-only' 'Work with accessible input only' $p3 @('keyboard', 'dpad', 'mouse', 'talkback') $def)
    )
    return [pscustomobject]@{ Scenes = $scenes }
}

function New-DaBudgets {
    <#
    .SYNOPSIS
        Strategic section 5.5 as budgets.json: every row a hypothesis, version h1.
    #>
    [CmdletBinding()]
    param()
    $both = @('android', 'windows')
    $rows = @(
        @('B01', 'ui-ping-wm-null', 'ms', 50, 100, 'timeout 500 ms; timeout share 0', 'Active window after warm-up; separate API error counter', @('windows')),
        @('B02', 'key-to-visible-reaction', 'ms', 100, 200, 'no lost input', 'Ordinary input; a heavy operation must show quickly that the command was accepted', $both),
        @('B03', 'acknowledge-progress-and-cancel', 'ms', $null, $null, 'long action acknowledged <=200 ms; cancel acknowledged <=500 ms', 'Completion time is set separately per size and source', $both),
        @('B04', 'cold-warm-start-and-exit', 's', $null, $null, 'cold start <=3 s; warm start <=1 s; orderly exit <=2 s to an observable result', 'Empty fixture; finishing of a background operation is kept apart from closing the UI', $both),
        @('B05', 'ui-busy-intervals', 'ms', $null, $null, 'no interval >100 ms without input handling', 'Trace, not the duration of one internal function', $both),
        @('B06', 'neighbour-program', 'ms', $null, $null, 'p95 increase <=10 ms and <=20% relative to the baseline (both limits)', 'When the baseline is near zero an absolute limit is assessed', $both),
        @('B07', 'hook-callback-clipboard-focus', 'ms', $null, 5, 'callback p99 <=5 ms; clipboard hold <=50 ms; 0 unexpected focus grabs', 'Only when the chosen feature uses such an integration', $both),
        @('B08', 'neighbour-disk-network', 'percent', $null, $null, 'p95 latency and throughput degradation <=20%', 'Same load profile; contention and error kept separate', $both),
        @('B09', 'memory-after-warm-up', 'MiB/hour', $null, $null, 'upper 95% bound of growth <=1 MiB/hour and <=0.1 MiB/cycle', 'Retained state, not the peak working set', $both),
        @('B10', 'handles-and-threads', 'objects/hour', $null, $null, '<=1 object/hour; return to the plateau within +-2 after a cycle', 'Each class separately; child processes included; GDI and USER classes are n/a on Android at metric level', $both),
        @('B11', 'timers-com-subscriptions', 'objects', $null, $null, '0 residual objects of a closed feature', 'An observable ownership proof is mandatory', $both),
        @('B12', 'logs-temp-registry', 'MiB', $null, $null, 'logs <=100 MiB per profile with rotation; temp <=10 MiB after cleanup; registry 0 unintended writes', 'Bounded caches are judged against a documented limit', $both),
        @('B13', 'errors-and-understanding', 'percent', $null, $null, '100% of injections with a known outcome and invariants; U >=90%', 'Finite fault catalogue and a stated participant set', $both)
    )
    $items = [System.Collections.Generic.List[object]]::new()
    foreach ($r in $rows) {
        $items.Add([ordered]@{
                id        = [string]$r[0]
                metric    = [string]$r[1]
                unit      = [string]$r[2]
                p95       = $r[3]
                p99       = $r[4]
                bound     = [string]$r[5]
                condition = [string]$r[6]
                appliesTo = [string[]]@($r[7])
                state     = 'hypothesis'
            })
    }
    return [ordered]@{
        schemaVersion = 1
        version       = 'h1'
        state         = 'hypothesis'
        source        = 'PLAN/S4166_deep-dynamic-audit.md section 5.5'
        items         = $items
    }
}

function Get-DaOpenCellText {
    <#
    .SYNOPSIS
        Reason and next step of an open cell, by variant, sampling and property.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $Variant,
        [Parameter(Mandatory)][bool] $Sampled,
        [Parameter(Mandatory)][string] $Property,
        [bool] $NetworkScoped = $false,
        [bool] $RtlWalk = $false
    )
    $reason = $null
    $next = $null
    if ($Variant -eq 'vr') {
        $reason = 'needs headset, no emulator runs the vr flavor'
        $next = 'run the vr pass on the owner headset'
    } elseif ($Variant -eq 'outside-first-pass') {
        $reason = 'outside first pass: reachable only in lite, photos, legacy, foss or xr builds'
        $next = 'decide whether to widen the variant scope'
    } elseif (-not $Sampled) {
        $reason = 'not sampled, first pass'
        $next = 'extend the first-pass sample or run in a later pass'
    } elseif ($NetworkScoped) {
        $reason = 'sampled, but a network or cloud source needs an external service; the owner allowed local emulator storage only'
        $next = 'owner decision on a disposable external source'
    } elseif ($RtlWalk) {
        $reason = 'RTL walk (ar) selected for the first pass, not measured yet'
        $next = 'stand run in the ar locale'
    } else {
        $reason = 'sampled for the first pass, not measured yet'
        switch ($Property) {
            'O' { $next = 'stand run: responsiveness probes (phases 03 and 05)' }
            'E' { $next = 'stand run: fault injection and invariants (phase 04)' }
            'S' { $next = 'stand run: neighbour-impact probe (phase 03)' }
            'L' { $next = 'stand run: growth and resource slopes (phase 05)' }
            default { $next = 'owner protocol run (phase 06), not representative' }
        }
    }
    return [pscustomobject]@{ Reason = $reason; NextStep = $next }
}

function New-DaMatrix {
    <#
    .SYNOPSIS
        Builds matrix.json: capability cells and scene cells, every applicable cell open.
    .DESCRIPTION
        One cell per capability, reachable first-pass variant, context and property O, E, S, L, U.
        A capability reachable in no first-pass variant gets variant outside-first-pass, never n/a.
        Unmapped capabilities (scene null) get no cell: the validator reports them as a gap.
        Returns the matrix object plus counters for the summary.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] $Capabilities,
        [Parameter(Mandatory)] $Scenes,
        [AllowNull()] $Inventory
    )
    $props = @(Get-DaPropertyIds)
    $geo = @('small-round', 'large-round')
    if ($null -ne $Inventory -and $null -ne $Inventory.WatchGeometries) { $geo = @($Inventory.WatchGeometries) }
    $cells = [System.Collections.Generic.List[object]]::new()
    $sceneCells = [System.Collections.Generic.List[object]]::new()
    $keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

    # RTL walk target per scene C05..C11: the first sampled capability that reaches standard on the
    # phone. Items arrive in id order, so the first of the best tier wins; the tiers keep a General
    # fix note or a source that needs an external service from being the one RTL walk of a scene.
    $arTarget = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
    foreach ($sc in @('C05', 'C06', 'C07', 'C08', 'C09', 'C10', 'C11')) {
        $pick = @($null, $null, $null)
        foreach ($it in $Capabilities.Items) {
            if ($it['scene'] -ne $sc -or -not $it['sampled']) { continue }
            $reach = Get-DaCapabilityReach -Area $it['area'] -Flavors $it['flavors'] -WearFlavors $it['wearFlavors']
            if ($reach.Phone -notcontains 'standard') { continue }
            $tier = 0
            if ($it['area'] -eq 'General') { $tier = 2 } elseif ($it['area'] -eq 'Network & Cloud') { $tier = 1 }
            if ($null -eq $pick[$tier]) { $pick[$tier] = $it['id'] }
        }
        foreach ($candidate in $pick) {
            if ($null -ne $candidate) { $arTarget[$sc] = $candidate; break }
        }
    }

    foreach ($it in $Capabilities.Items) {
        $scene = $it['scene']
        if ($null -eq $scene) { continue }
        $id = [string]$it['id']
        $rep = [bool]$it['sampled']
        $net = ($it['area'] -eq 'Network & Cloud')
        $reach = Get-DaCapabilityReach -Area $it['area'] -Flavors $it['flavors'] -WearFlavors $it['wearFlavors']
        $plan = [System.Collections.Generic.List[object]]::new()
        for ($i = 0; $i -lt $reach.Phone.Count; $i++) {
            $plan.Add(@($reach.Phone[$i], 'en/touch/default', ($rep -and $i -eq 0), $false))
        }
        for ($i = 0; $i -lt $reach.Watch.Count; $i++) {
            foreach ($g in $geo) {
                $plan.Add(@(('wear-' + $reach.Watch[$i]), "en/touch/$g", ($rep -and $i -eq 0 -and $it['area'] -eq 'Wear OS'), $false))
            }
        }
        if ($plan.Count -eq 0) { $plan.Add(@('outside-first-pass', 'en/touch/default', $false, $false)) }
        if ($arTarget.ContainsKey($scene) -and $arTarget[$scene] -eq $id) { $plan.Add(@('standard', 'ar/touch/default', $true, $true)) }
        foreach ($p in $plan) {
            $variant = [string]$p[0]
            $context = [string]$p[1]
            $sampled = [bool]$p[2]
            $rtl = [bool]$p[3]
            foreach ($prop in $props) {
                $text = Get-DaOpenCellText -Variant $variant -Sampled $sampled -Property $prop -NetworkScoped $net -RtlWalk $rtl
                $key = "$id|$scene|$variant|$context|$prop"
                if (-not $keys.Add($key)) { throw "da-inventory: duplicate matrix cell key $key" }
                $cells.Add([ordered]@{
                        key        = $key
                        capability = $id
                        scene      = $scene
                        variant    = $variant
                        context    = $context
                        property   = $prop
                        status     = 'open'
                        sampled    = $sampled
                        reason     = $text.Reason
                        owner      = 'owner'
                        nextStep   = $text.NextStep
                    })
            }
        }
    }

    foreach ($sc in $Scenes.Scenes) {
        $sceneBound = (-not [bool]$sc['capabilityScoped']) -or (@($sc['areas']).Count -eq 0)
        if (-not $sceneBound) { continue }
        foreach ($at in $sc['atomic']) {
            $variants = @($at['variants'])
            for ($vi = 0; $vi -lt $variants.Count; $vi++) {
                $variant = [string]$variants[$vi]
                foreach ($inp in @($at['inputs'])) {
                    foreach ($st in @($at['states'])) {
                        $context = "en/$inp/$st"
                        foreach ($prop in $props) {
                            $key = "$($sc['id'])|$($at['id'])|$variant|$context|$prop"
                            if (-not $keys.Add($key)) { throw "da-inventory: duplicate matrix cell key $key" }
                            if (-not [bool]$at['applicable']) {
                                $sceneCells.Add([ordered]@{
                                        key       = $key
                                        scene     = [string]$sc['id']
                                        atomic    = [string]$at['id']
                                        variant   = $variant
                                        context   = $context
                                        property  = $prop
                                        status    = 'n/a'
                                        sampled   = $false
                                        reason    = [string]$at['naReason']
                                        source    = [string]$at['naSource']
                                        condition = 'the audited product is the Android app (owner decision 2026-10-11, strategic section 6 item 1)'
                                    })
                                continue
                            }
                            $sampled = ($vi -eq 0)
                            $text = Get-DaOpenCellText -Variant $variant -Sampled $sampled -Property $prop
                            $sceneCells.Add([ordered]@{
                                    key      = $key
                                    scene    = [string]$sc['id']
                                    atomic   = [string]$at['id']
                                    variant  = $variant
                                    context  = $context
                                    property = $prop
                                    status   = 'open'
                                    sampled  = $sampled
                                    reason   = $text.Reason
                                    owner    = 'owner'
                                    nextStep = $text.NextStep
                                })
                        }
                    }
                }
            }
        }
    }
    return [pscustomobject]@{
        Matrix    = [ordered]@{ schemaVersion = 1; cells = $cells; sceneCells = $sceneCells }
        Cells     = $cells
        SceneCells = $sceneCells
        ArTargets = $arTarget
    }
}

function Write-DaJsonFile {
    <#
    .SYNOPSIS
        Writes one value as deterministic JSON (UTF-8 without BOM, LF) and returns size and hash.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $Path,
        [AllowNull()] $Value
    )
    $text = ConvertTo-DaJsonText -Value $Value
    $dir = [System.IO.Path]::GetDirectoryName($Path)
    [void][System.IO.Directory]::CreateDirectory($dir)
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($text)
    [System.IO.File]::WriteAllBytes($Path, $bytes)
    $hash = [System.Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
    return [pscustomobject]@{ Path = $Path; Bytes = $bytes.Length; Sha256 = $hash }
}

function Write-DaAuditData {
    <#
    .SYNOPSIS
        Writes manifest.json, capabilities.json, scenes.json, budgets.json and matrix.json.
    .DESCRIPTION
        Builds whatever it is not handed (inventory, capabilities, scenes, budgets, matrix), so
        `Write-DaAuditData -RepoRoot <root> -AuditRoot <dir>` is a complete build on its own.
    .PARAMETER AuditRoot
        Target folder; defaults to PLAN/S4166_deep-dynamic-audit/audit under the repository root.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [string] $AuditRoot,
        [string] $GeneratedAt,
        [AllowNull()] $Inventory,
        [AllowNull()] $Capabilities,
        [AllowNull()] $Scenes,
        [AllowNull()] $Budgets,
        [AllowNull()] $MatrixResult
    )
    $root = (Resolve-Path -LiteralPath $RepoRoot).Path
    if (-not $AuditRoot) { $AuditRoot = Join-Path $root 'PLAN/S4166_deep-dynamic-audit/audit' }
    if ($null -eq $Inventory) { $Inventory = Get-DaInventory -RepoRoot $root -GeneratedAt $GeneratedAt }
    if ($null -eq $Capabilities) { $Capabilities = Build-DaCapabilities -Inventory $Inventory }
    if ($null -eq $Scenes) { $Scenes = New-DaScenes -Inventory $Inventory }
    if ($null -eq $Budgets) { $Budgets = New-DaBudgets }
    if ($null -eq $MatrixResult) { $MatrixResult = New-DaMatrix -Capabilities $Capabilities -Scenes $Scenes -Inventory $Inventory }

    $files = [ordered]@{}
    $files['manifest.json'] = $Inventory.Manifest
    $files['capabilities.json'] = [ordered]@{ schemaVersion = 1; items = $Capabilities.Items }
    $files['scenes.json'] = [ordered]@{ schemaVersion = 1; scenes = $Scenes.Scenes }
    $files['budgets.json'] = $Budgets
    $files['matrix.json'] = $MatrixResult.Matrix
    $written = [System.Collections.Generic.List[object]]::new()
    foreach ($name in $files.Keys) {
        $target = Join-Path $AuditRoot $name
        $written.Add((Write-DaJsonFile -Path $target -Value $files[$name]))
    }
    return $written
}

function Get-DaStatusCounts {
    <#
    .SYNOPSIS
        Counts a list of cells by status, as an ordered dictionary.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)] $Cells)
    $counts = [System.Collections.Generic.SortedDictionary[string, int]]::new([System.StringComparer]::Ordinal)
    foreach ($c in $Cells) {
        $s = [string]$c['status']
        if ($counts.ContainsKey($s)) { $counts[$s] = $counts[$s] + 1 } else { $counts[$s] = 1 }
    }
    $out = [ordered]@{}
    foreach ($k in $counts.Keys) { $out[$k] = $counts[$k] }
    return $out
}

function Invoke-DaBuildInventory {
    <#
    .SYNOPSIS
        Builds the inventory, capabilities, scenes, budgets and matrix and writes the five files.
    .DESCRIPTION
        Returns a summary object (counts, strata per scene, cell counts by status and sampling and
        the written files with size and sha256) so the caller can print or assert on it.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [string] $AuditRoot,
        [string] $GeneratedAt
    )
    $inv = Get-DaInventory -RepoRoot $RepoRoot -GeneratedAt $GeneratedAt
    $cap = Build-DaCapabilities -Inventory $inv
    $scenes = New-DaScenes -Inventory $inv
    $budgets = New-DaBudgets
    $mat = New-DaMatrix -Capabilities $cap -Scenes $scenes -Inventory $inv
    $written = Write-DaAuditData -RepoRoot $RepoRoot -AuditRoot $AuditRoot -Inventory $inv -Capabilities $cap `
        -Scenes $scenes -Budgets $budgets -MatrixResult $mat

    $strataByScene = [System.Collections.Generic.SortedDictionary[string, int]]::new([System.StringComparer]::Ordinal)
    $strataTotal = 0
    foreach ($s in $cap.Strata) {
        if ($s['outside'] -or $null -eq $s['scene']) { continue }
        $strataTotal++
        $sc = [string]$s['scene']
        if ($strataByScene.ContainsKey($sc)) { $strataByScene[$sc] = $strataByScene[$sc] + 1 } else { $strataByScene[$sc] = 1 }
    }
    $strataScene = [ordered]@{}
    foreach ($k in $strataByScene.Keys) { $strataScene[$k] = $strataByScene[$k] }
    $sampledCells = 0
    foreach ($c in $mat.Cells) { if ($c['sampled']) { $sampledCells++ } }
    $sampledScene = 0
    foreach ($c in $mat.SceneCells) { if ($c['sampled']) { $sampledScene++ } }
    $mapped = 0
    foreach ($it in $cap.Items) { if ($null -ne $it['scene']) { $mapped++ } }

    return [pscustomobject]@{
        Counts            = $inv.Manifest['counts']
        Capabilities      = $cap.Items.Count
        Mapped            = $mapped
        Unmapped          = @($cap.Unmapped)
        StrataSampled     = $strataTotal
        StrataByScene     = $strataScene
        StrataAll         = $cap.Strata.Count
        Cells             = $mat.Cells.Count
        CellsByStatus     = Get-DaStatusCounts -Cells $mat.Cells
        SampledCells      = $sampledCells
        SceneCells        = $mat.SceneCells.Count
        SceneCellsByStatus = Get-DaStatusCounts -Cells $mat.SceneCells
        SampledSceneCells = $sampledScene
        ArTargets         = $mat.ArTargets
        Written           = $written
    }
}
