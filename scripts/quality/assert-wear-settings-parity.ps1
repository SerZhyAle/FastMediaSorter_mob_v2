#requires -Version 7.0
<#
.SYNOPSIS
    S2093: fails when a watch setting exists on one side of the phone/watch pair and not the other.

.DESCRIPTION
    The watch-settings list used to live in four independently maintained places - the transfer
    contract, the phone companion window, the watch settings screens and the published settings
    reference - so a setting added to one of them and not the others diverged silently and was found
    only when the owner could not see it where it was expected.

    S2093 made the list explicit in WearSettingsRegistry, mirrored per module. This gate checks the
    registry against every consumer derived from it and names the missing side. S2169 added the
    order dimension: the registry also declares the menu's group sequence and row order, and the
    gate compares that declaration against the rows both surfaces actually draw.

    Checks:
      1. The two WearSettingsRegistry copies list the same entry ids.
      2. A BOTH entry has a matching field in both WearSettingsPayload copies.
      3. A BOTH entry has a matching watch DataStore key.
      4. A non-BOTH entry carries a non-empty exceptionReason.
      5. A watch DataStore key a settings screen writes is present in the registry.
      6. An entry the owner can see is published in SettingsDocScopeCatalog.wearEntries.
      7. A BOTH entry's watch setter records an edit time (a stampedEdit call site).
      8. S2169 declaration completeness: the menu map names real entries exactly once, an entry
         with a watch row sits in the map exactly once, a mapped row declares its watchRowAnchor,
         a mapped non-WATCH_ONLY row declares its companionRowTag, and both registry copies agree
         on the map and on both anchor fields.
      9. S2169 watch order: each group's anchors resolve in that group's settings screens (or only
         in another group's screens, which is itself a finding), and per file the anchors follow
         the declared order.
     10. S2169 phone order: WearWatchSettingsGroup.kt draws every declared companionRowTag, and the
         rows appear grouped in the map's group order and in row order within each group. The
         wearViewMode / wearFileListViewMode / wearBackgroundMode_ prefixes match by occurrence,
         not as quoted whole tags.
     11. S2464 decoder contract table parity: every property in WearSettingsPayload has an exact
         matching entry in WearSettingsPayloadDecoder.EXPECTED with corresponding JsonKind matching
         its Kotlin type, no extra rows exist in EXPECTED, and the EXPECTED tables between phone and
         watch agree in keys, order and JsonKind.
     12. S2461 sync-time writer: markSynced is called only from the mirror store and
         MergeWearSettingsReportUseCase, across app_v2/src/main and app_v2/src/wearGms.

    A row literal may sit inside a helper composable defined below its call site (the bottom-helper
    idiom these screens use). Both order checks resolve such a literal to the helper's FIRST
    INVOCATION - the place the row is drawn - with the literal position breaking ties between rows
    inside one helper. Rows written inline resolve at their own literal position.

    Rule 33 class, stated at birth: PER-TICKET. Its evidence exists only at the moment of the change -
    the author is the one who knows whether a new setting was meant to be one-sided - and the settings
    reference it guards is read by agents between releases, where staleness poisons decisions.

.NOTES
    Exit codes:
      0 - parity holds; or a divergence was reported without -Gate, matching the advisory shape of
          the sibling gates in assert-fast-gates.ps1.
      1 - a divergence was found and -Gate was passed.
      2 - a source file could not be read (a registry, a payload, the watch preferences, the doc
          catalog, a settings screen or the companion window), or a registry parsed to zero entries,
          so nothing was actually checked. A caller must tell this from 1: "found a defect" and
          "did not look" are different answers.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$paths = [ordered]@{
    PhoneRegistry = 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsRegistry.kt'
    WatchRegistry = 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSettingsRegistry.kt'
    PhonePayload  = 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsPayload.kt'
    WatchPayload  = 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSettingsPayload.kt'
    WatchPrefs    = 'wear/src/main/java/com/sza/fastmediasorter/wear/data/preferences/WearPreferencesRepositoryImpl.kt'
    DocCatalog    = 'app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsDocScopeCatalog.kt'
    WatchMediaTypes   = 'wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/MediaTypesSettingsScreen.kt'
    WatchBrowseCatalog = 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/browse/BrowseCategoryCatalog.kt'
    WatchSlideshow = 'wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/SlideshowSettingsScreen.kt'
    WatchScreen    = 'wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/ScreenSettingsScreen.kt'
    WatchOther     = 'wear/src/main/java/com/sza/fastmediasorter/wear/ui/settings/OtherSettingsScreen.kt'
    PhoneRows      = 'app_v2/src/main/java/com/sza/fastmediasorter/ui/wear/companion/WearWatchSettingsGroup.kt'
    PhoneDecoder   = 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSettingsPayloadDecoder.kt'
    WatchDecoder   = 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSettingsPayloadDecoder.kt'
    PhoneMerge     = 'app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MergeWearSettingsReportUseCase.kt'
}

# S2169: which files a menu group's watch rows live in (keys into $paths)...
$groupFiles = @{
    MEDIA_TYPES = @('WatchMediaTypes', 'WatchBrowseCatalog')
    SLIDESHOW   = @('WatchSlideshow')
    SCREEN      = @('WatchScreen')
    OTHER       = @('WatchOther')
}

# S2169: ...and, per file, the function that draws the group's rows. A literal found inside any
# OTHER function escalates to that function's first invocation. BrowseCategoryCatalog declares its
# type sets at object level, so it needs no root and every literal resolves where it stands.
$orderRoots = @{
    WatchMediaTypes    = 'MediaTypesSettingsScreen'
    WatchSlideshow     = 'SlideshowSettingsScreen'
    WatchScreen        = 'ScreenSettingsScreen'
    WatchOther         = 'OtherSettingsScreen'
    WatchBrowseCatalog = ''
    PhoneRows          = 'WatchSettingsControls'
}

$text = @{}
foreach ($name in $paths.Keys) {
    $full = Join-Path $root $paths[$name]
    if (-not (Test-Path -LiteralPath $full)) {
        Write-Error "assert-wear-settings-parity: could not verify - missing $($paths[$name])" -ErrorAction Continue
        exit 2
    }
    $text[$name] = Get-Content -LiteralPath $full -Raw
}

# One WearSettingScope( .. ) block per entry. Parsed rather than executed, because the gate has to
# read both modules and neither compiles into the other.
function Read-RegistryEntries {
    param([string]$Source, [string]$Label)

    # Split on the constructor name rather than trying to balance parentheses: an exceptionReason is a
    # concatenated multi-line string containing its own brackets, which no single regex survives.
    $entries = @()
    $chunks = $Source -split 'WearSettingScope\('
    foreach ($body in ($chunks | Select-Object -Skip 1)) {
        $field = [regex]::Match($body, 'field\s*=\s*"(?<v>[^"]+)"')
        if (-not $field.Success) { continue }
        $key = [regex]::Match($body, 'watchPreferenceKey\s*=\s*"(?<v>[^"]+)"')
        $doc = [regex]::Match($body, 'docScopeId\s*=\s*"(?<v>[^"]+)"')
        $own = [regex]::Match($body, 'ownership\s*=\s*WearSettingOwnership\.(?<v>\w+)')
        $reason = [regex]::Match($body, 'exceptionReason\s*=\s*"(?<v>[^"]+)"')
        $anchor = [regex]::Match($body, 'watchRowAnchor\s*=\s*"(?<v>[^"]+)"')
        $tag = [regex]::Match($body, 'companionRowTag\s*=\s*"(?<v>[^"]+)"')
        $entries += [pscustomobject]@{
            Field     = $field.Groups['v'].Value
            WatchKey  = if ($key.Success) { $key.Groups['v'].Value } else { $null }
            DocScope  = if ($doc.Success) { $doc.Groups['v'].Value } else { $null }
            Ownership = if ($own.Success) { $own.Groups['v'].Value } else { 'UNKNOWN' }
            Reason    = if ($reason.Success) { $reason.Groups['v'].Value } else { $null }
            Anchor    = if ($anchor.Success) { $anchor.Groups['v'].Value } else { $null }
            Tag       = if ($tag.Success) { $tag.Groups['v'].Value } else { $null }
            Source    = $Label
        }
    }
    return $entries
}

# S2169: the menuRowsByGroup declaration, group id -> ordered field list, in declaration order.
function Read-MenuMap {
    param([string]$Source)

    $map = [ordered]@{}
    foreach ($m in [regex]::Matches($Source, '"(?<group>[A-Z_]+)"\s+to\s+listOf\((?<body>[^)]*)\)')) {
        $fields = @([regex]::Matches($m.Groups['body'].Value, '"(?<f>[^"]+)"') |
            ForEach-Object { $_.Groups['f'].Value })
        $map[$m.Groups['group'].Value] = $fields
    }
    return $map
}

# S2169: where a row anchor or tag is DRAWN in one file. A literal inside a non-root function
# escalates to that function's first invocation (bottom helpers are defined below their call
# sites); the literal index stays as the tie-break between rows sharing one helper.
function Get-RowPosition {
    param([string]$Source, [string]$Token, [string]$RootFun)

    $literal = $Source.IndexOf($Token, [System.StringComparison]::Ordinal)
    if ($literal -lt 0) { return $null }

    $declName = $null
    foreach ($decl in [regex]::Matches($Source, '(?m)^\s*(?:private\s+|internal\s+)?fun\s+(?<name>\w+)\s*\(')) {
        if ($decl.Index -gt $literal) { break }
        $declName = $decl.Groups['name'].Value
    }
    if ($null -eq $declName -or $declName -eq $RootFun) {
        return [pscustomobject]@{ Draw = $literal; Literal = $literal; Helper = $null; Invoked = $true }
    }
    foreach ($inv in [regex]::Matches($Source, "(?<!fun\s)\b$([regex]::Escape($declName))\s*\(")) {
        return [pscustomobject]@{ Draw = $inv.Index; Literal = $literal; Helper = $declName; Invoked = $true }
    }
    return [pscustomobject]@{ Draw = $literal; Literal = $literal; Helper = $declName; Invoked = $false }
}

# S2464: parses property names and their Kotlin types from data class WearSettingsPayload(...).
function Read-PayloadFields {
    param([string]$Source)

    $match = [regex]::Match($Source, '(?s)data\s+class\s+WearSettingsPayload\s*\((?<params>.*?)\)\s*(?:\{|$)')
    if (-not $match.Success) { return [ordered]@{} }
    $paramsBlock = $match.Groups['params'].Value
    $fields = [ordered]@{}
    foreach ($line in ($paramsBlock -split "`n")) {
        $cleanLine = $line.Trim()
        if ($cleanLine.StartsWith('//') -or $cleanLine.StartsWith('/*') -or [string]::IsNullOrWhiteSpace($cleanLine)) { continue }
        $cleanLine = $cleanLine -replace '//.*$', ''
        $serMatch = [regex]::Match($cleanLine, '@SerializedName\("(?<name>[^"]+)"\)')
        $valMatch = [regex]::Match($cleanLine, 'val\s+(?<name>\w+)\s*:\s*(?<type>.+?)(?:\s*=\s*.*|\s*,\s*$|\s*$)')
        if ($valMatch.Success) {
            $wireName = if ($serMatch.Success) { $serMatch.Groups['name'].Value } else { $valMatch.Groups['name'].Value }
            $propType = $valMatch.Groups['type'].Value.Trim()
            $fields[$wireName] = $propType
        }
    }
    return $fields
}

# S2464: parses the EXPECTED map entries from WearSettingsPayloadDecoder.kt (field name -> JsonKind name).
function Read-DecoderExpected {
    param([string]$Source)

    $match = [regex]::Match($Source, '(?s)EXPECTED\s*:\s*Map<String,\s*JsonKind>\s*=\s*linkedMapOf\((?<entries>.*?)\)')
    if (-not $match.Success) { return [ordered]@{} }
    $entriesBlock = $match.Groups['entries'].Value
    $expected = [ordered]@{}
    foreach ($m in [regex]::Matches($entriesBlock, '"(?<name>[^"]+)"\s+to\s+JsonKind\.(?<kind>[A-Z_]+)')) {
        $expected[$m.Groups['name'].Value] = $m.Groups['kind'].Value
    }
    return $expected
}

# S2464: maps a Kotlin type from WearSettingsPayload to its corresponding JsonKind enum name.
function Get-ExpectedJsonKind {
    param([string]$KotlinType)

    $base = $KotlinType.TrimEnd('?').Trim()
    if ($base -eq 'Boolean') { return 'BOOLEAN' }
    if ($base -in @('Int', 'Long', 'Float', 'Double', 'Short', 'Byte', 'Number')) { return 'NUMBER' }
    if ($base -eq 'String') { return 'STRING' }
    if ($base -match '^(?:Map|JsonObject|Set|List)<?') { return 'OBJECT' }
    return 'UNKNOWN'
}

$phoneEntries = Read-RegistryEntries -Source $text.PhoneRegistry -Label 'phone'
$watchEntries = Read-RegistryEntries -Source $text.WatchRegistry -Label 'watch'

if ($phoneEntries.Count -eq 0 -or $watchEntries.Count -eq 0) {
    Write-Error 'assert-wear-settings-parity: could not verify - a registry parsed to zero entries.' -ErrorAction Continue
    exit 2
}

$phoneByField = @{}
foreach ($e in $phoneEntries) { $phoneByField[$e.Field] = $e }
$watchByField = @{}
foreach ($e in $watchEntries) { $watchByField[$e.Field] = $e }

$findings = @()

# 1. Both copies list the same entry ids.
$phoneFields = $phoneEntries.Field | Sort-Object
$watchFields = $watchEntries.Field | Sort-Object
foreach ($missing in ($phoneFields | Where-Object { $_ -notin $watchFields })) {
    $findings += "S2093: '$missing' is in the phone WearSettingsRegistry and missing from the watch copy."
}
foreach ($missing in ($watchFields | Where-Object { $_ -notin $phoneFields })) {
    $findings += "S2093: '$missing' is in the watch WearSettingsRegistry and missing from the phone copy."
}

# The watch DataStore keys, and the fields the setters stamp.
$prefKeys = [regex]::Matches($text.WatchPrefs, '(?:boolean|int|long|string)PreferencesKey\("(?<v>[^"]+)"\)') |
    ForEach-Object { $_.Groups['v'].Value }
$stampedFields = [regex]::Matches($text.WatchPrefs, 'stampedEdit\("(?<v>[^"]+)"\)') |
    ForEach-Object { $_.Groups['v'].Value }
$docScopeKeys = [regex]::Matches($text.DocCatalog, 'key\s*=\s*"(?<v>[^"]+)"') |
    ForEach-Object { $_.Groups['v'].Value }

foreach ($entry in $phoneEntries) {
    $field = $entry.Field

    if ($entry.Ownership -eq 'BOTH') {
        # 2. A shared setting must ride the contract on both sides.
        if ($text.PhonePayload -notmatch "(?m)^\s*(?:@SerializedName\(""$([regex]::Escape($field))""\)\s*)?val\s+$([regex]::Escape($field))\b") {
            $findings += "S2093: '$field' is in the registry as BOTH but has no field in the phone WearSettingsPayload."
        }
        if ($text.WatchPayload -notmatch "(?m)^\s*val\s+$([regex]::Escape($field))\b") {
            $findings += "S2093: '$field' is in the registry as BOTH but has no field in the watch WearSettingsPayload."
        }
        # 3. A shared setting must be stored on the watch.
        if (-not $entry.WatchKey) {
            $findings += "S2093: '$field' is in the registry as BOTH but declares no watch DataStore key."
        } elseif ($entry.WatchKey -notin $prefKeys) {
            $findings += "S2093: '$field' names watch key '$($entry.WatchKey)', which WearPreferencesRepositoryImpl does not declare."
        }
        # 7. A shared setting must record when it changed, or the merge cannot rank it.
        if ($field -notin $stampedFields) {
            $findings += "S2093: '$field' is BOTH but no setter in WearPreferencesRepositoryImpl stamps it - add stampedEdit(""$field"")."
        }
    } else {
        # 4. A one-sided setting without a recorded reason is indistinguishable from a forgotten one.
        if ([string]::IsNullOrWhiteSpace($entry.Reason)) {
            $findings += "S2093: '$field' is $($entry.Ownership) with no exceptionReason - record why, or make it BOTH."
        }
    }

    # 6. What the owner can see must be published, or the reference stops describing the real set.
    if ($entry.DocScope -and $entry.DocScope -notin $docScopeKeys) {
        $findings += "S2093: '$field' names doc entry '$($entry.DocScope)', absent from SettingsDocScopeCatalog.wearEntries."
    }
}

# 5. A watch key a settings screen writes and the registry does not know about is the divergence
#    itself, arriving from the other direction.
$registryKeys = $phoneEntries.WatchKey | Where-Object { $_ }
foreach ($stamped in ($stampedFields | Sort-Object -Unique)) {
    if ($stamped -notin $phoneFields) {
        $findings += "S2093: WearPreferencesRepositoryImpl stamps '$stamped', which is in no WearSettingsRegistry entry."
    }
}
foreach ($entry in $watchEntries) {
    if ($entry.Ownership -ne 'BOTH') { continue }
    if ($entry.WatchKey -and $entry.WatchKey -notin $registryKeys) {
        $findings += "S2093: watch registry entry '$($entry.Field)' names key '$($entry.WatchKey)', which the phone copy does not."
    }
}

# 8. S2169 declaration completeness: the menu map and the entry list describe one world, and the
#    two copies agree on it. The watch copy drives this check and check 9 (the watch menu is the
#    canonical order, ADR-1); the phone copy drives check 10.
$phoneMap = Read-MenuMap -Source $text.PhoneRegistry
$watchMap = Read-MenuMap -Source $text.WatchRegistry
if (@($phoneMap.Keys).Count -eq 0 -or @($watchMap.Keys).Count -eq 0) {
    Write-Error 'assert-wear-settings-parity: could not verify - a registry parsed to an empty menu map.' -ErrorAction Continue
    exit 2
}
if ((@($phoneMap.Keys) -join '|') -ne (@($watchMap.Keys) -join '|')) {
    $findings += "S2169: menu map group sequence differs between copies - phone '$(@($phoneMap.Keys) -join ' -> ')' vs watch '$(@($watchMap.Keys) -join ' -> ')'."
} else {
    foreach ($group in $watchMap.Keys) {
        if ((@($phoneMap[$group]) -join '|') -ne (@($watchMap[$group]) -join '|')) {
            $findings += "S2169: menu map group $group differs between copies - phone [$(@($phoneMap[$group]) -join ', ')] vs watch [$(@($watchMap[$group]) -join ', ')]."
        }
    }
}
foreach ($p in $phoneEntries) {
    $w = $watchByField[$p.Field]
    if ($null -eq $w) { continue }
    if ($p.Anchor -ne $w.Anchor) {
        $findings += "S2169: '$($p.Field)' declares watchRowAnchor '$($p.Anchor)' in the phone copy and '$($w.Anchor)' in the watch copy."
    }
    if ($p.Tag -ne $w.Tag) {
        $findings += "S2169: '$($p.Field)' declares companionRowTag '$($p.Tag)' in the phone copy and '$($w.Tag)' in the watch copy."
    }
}
$mappedCount = @{}
foreach ($group in $watchMap.Keys) {
    if ($group -notin $groupFiles.Keys) {
        $findings += "S2169: menu map declares group $group, which has no settings screen in the gate's group-file table."
        continue
    }
    foreach ($f in $watchMap[$group]) {
        $mappedCount[$f] = 1 + $(if ($mappedCount.ContainsKey($f)) { $mappedCount[$f] } else { 0 })
    }
}
foreach ($f in $watchMap.Values | ForEach-Object { $_ }) {
    if ($f -notin $watchByField.Keys) {
        $findings += "S2169: menu map names field '$f', which is in no WearSettingsRegistry entry."
    }
}
foreach ($entry in $watchEntries) {
    $count = if ($mappedCount.ContainsKey($entry.Field)) { $mappedCount[$entry.Field] } else { 0 }
    if ($null -ne $entry.Anchor) {
        if ($count -eq 0) {
            $findings += "S2169: '$($entry.Field)' declares watchRowAnchor '$($entry.Anchor)' but sits in no menuRowsByGroup group."
        } elseif ($count -gt 1) {
            $findings += "S2169: '$($entry.Field)' appears $count times in menuRowsByGroup - a row has exactly one place."
        }
    } elseif ($count -gt 0) {
        $findings += "S2169: '$($entry.Field)' sits in menuRowsByGroup but declares no watchRowAnchor."
    }
    if ($count -gt 0 -and $entry.Ownership -ne 'WATCH_ONLY' -and $null -eq $entry.Tag) {
        $findings += "S2169: mapped row '$($entry.Field)' is $($entry.Ownership) but declares no companionRowTag."
    }
}

# 9. S2169 watch order: each mapped anchor resolves in its own group's screens, and per file the
#    resolved draw positions follow the declared order.
foreach ($group in $watchMap.Keys) {
    if ($group -notin $groupFiles.Keys) { continue }
    foreach ($f in $watchMap[$group]) {
        $entry = $watchByField[$f]
        if ($null -eq $entry -or $null -eq $entry.Anchor) { continue }
        $resolvingGroups = @()
        foreach ($scanGroup in $groupFiles.Keys) {
            foreach ($fileKey in $groupFiles[$scanGroup]) {
                if ($text[$fileKey].Contains($entry.Anchor)) { $resolvingGroups += $scanGroup }
            }
        }
        if ($resolvingGroups.Count -eq 0) {
            $findings += "S2169: watch row '$f' anchor '$($entry.Anchor)' matches no settings screen."
        } elseif ($group -notin $resolvingGroups) {
            $findings += "S2169: watch row '$f' anchor '$($entry.Anchor)' resolves only in group(s) [$($resolvingGroups -join ', ')], not in its own group $group."
        }
    }
    foreach ($fileKey in $groupFiles[$group]) {
        $present = @()
        foreach ($f in $watchMap[$group]) {
            $entry = $watchByField[$f]
            if ($null -eq $entry -or $null -eq $entry.Anchor) { continue }
            $pos = Get-RowPosition -Source $text[$fileKey] -Token $entry.Anchor -RootFun $orderRoots[$fileKey]
            if ($null -ne $pos) {
                $present += [pscustomobject]@{ Field = $f; Anchor = $entry.Anchor; Pos = $pos }
            }
        }
        for ($i = 1; $i -lt $present.Count; $i++) {
            $a = $present[$i - 1]
            $b = $present[$i]
            $ordered = $b.Pos.Draw -gt $a.Pos.Draw -or
                ($b.Pos.Draw -eq $a.Pos.Draw -and $b.Pos.Literal -gt $a.Pos.Literal)
            if (-not $ordered) {
                $findings += "S2169: watch group $group - '$($a.Field)' (anchor $($a.Anchor)) is declared before '$($b.Field)' (anchor $($b.Anchor)) but $($paths[$fileKey]) draws them the other way round."
            }
        }
    }
}

# 10. S2169 phone order: the companion window draws every declared tag, grouped and ordered like
#     the menu map. WATCH_ONLY rows carry no tag and no phone row.
$prevRow = $null
foreach ($group in $phoneMap.Keys) {
    if ($group -notin $groupFiles.Keys) { continue }
    foreach ($f in $phoneMap[$group]) {
        $entry = $phoneByField[$f]
        if ($null -eq $entry -or $null -eq $entry.Tag) { continue }
        $pos = Get-RowPosition -Source $text.PhoneRows -Token $entry.Tag -RootFun $orderRoots['PhoneRows']
        if ($null -eq $pos) {
            $findings += "S2169: companion row tag '$($entry.Tag)' (field $f) is declared but WearWatchSettingsGroup.kt never draws it."
            continue
        }
        if (-not $pos.Invoked) {
            $findings += "S2169: companion row tag '$($entry.Tag)' (field $f) sits inside helper '$($pos.Helper)', which the window never invokes."
            continue
        }
        if ($null -ne $prevRow) {
            $ordered = $pos.Draw -gt $prevRow.Pos.Draw -or
                ($pos.Draw -eq $prevRow.Pos.Draw -and $pos.Literal -gt $prevRow.Pos.Literal)
            if (-not $ordered) {
                $findings += "S2169: companion window draws '$($prevRow.Tag)' ($($prevRow.Field)) before '$($entry.Tag)' ($f), but the menu map declares group $($prevRow.Group) before $group with the rows in the opposite order."
            }
        }
        $prevRow = [pscustomobject]@{ Group = $group; Field = $f; Tag = $entry.Tag; Pos = $pos }
    }
}

# 11. S2464: every field in WearSettingsPayload must be listed in WearSettingsPayloadDecoder.EXPECTED
#     with matching JsonKind, no extra rows in EXPECTED, and phone/watch decoders in full symmetry.
#     A field present in the model but missing from its EXPECTED table is dropped on arrival and
#     decodes as null, so the new capability fails silently across builds (S2461/S2462 finding).
$sides = @(
    @{ Name = 'phone'; PayloadKey = 'PhonePayload'; DecoderKey = 'PhoneDecoder' },
    @{ Name = 'watch'; PayloadKey = 'WatchPayload'; DecoderKey = 'WatchDecoder' }
)
foreach ($side in $sides) {
    $payloadFields = Read-PayloadFields -Source $text[$side.PayloadKey]
    $decoderExpected = Read-DecoderExpected -Source $text[$side.DecoderKey]

    if ($payloadFields.Count -eq 0) {
        $findings += "S2464: could not parse any fields from $($side.Name) WearSettingsPayload."
        continue
    }
    if ($decoderExpected.Count -eq 0) {
        $findings += "S2464: could not parse EXPECTED map from $($side.Name) WearSettingsPayloadDecoder."
        continue
    }

    # 11a. Every field in WearSettingsPayload must have a matching EXPECTED entry with correct JsonKind
    foreach ($fName in $payloadFields.Keys) {
        if (-not $decoderExpected.Contains($fName)) {
            $findings += "S2464: '$fName' is declared in $($side.Name) WearSettingsPayload but missing from WearSettingsPayloadDecoder.EXPECTED table."
        } else {
            $expKind = Get-ExpectedJsonKind -KotlinType $payloadFields[$fName]
            $actKind = $decoderExpected[$fName]
            if ($expKind -eq 'UNKNOWN') {
                $findings += "S2464: '$fName' in $($side.Name) WearSettingsPayload has unsupported Kotlin type '$($payloadFields[$fName])'."
            } elseif ($expKind -ne $actKind) {
                $findings += "S2464: '$fName' is declared as '$($payloadFields[$fName])' in $($side.Name) WearSettingsPayload (expected JsonKind.$expKind) but WearSettingsPayloadDecoder.EXPECTED declares JsonKind.$actKind."
            }
        }
    }

    # 11b. Every entry in EXPECTED must correspond to a declared WearSettingsPayload field
    foreach ($expName in $decoderExpected.Keys) {
        if (-not $payloadFields.Contains($expName)) {
            $findings += "S2464: '$expName' is in $($side.Name) WearSettingsPayloadDecoder.EXPECTED but has no matching field in WearSettingsPayload."
        }
    }
}

# 11c. Phone and watch EXPECTED tables must be identical in keys, order, and JsonKind
$phoneExpected = Read-DecoderExpected -Source $text.PhoneDecoder
$watchExpected = Read-DecoderExpected -Source $text.WatchDecoder
if (@($phoneExpected.Keys).Count -gt 0 -and @($watchExpected.Keys).Count -gt 0) {
    if ((@($phoneExpected.Keys) -join '|') -ne (@($watchExpected.Keys) -join '|')) {
        $findings += "S2464: WearSettingsPayloadDecoder.EXPECTED field sequence differs between phone [$(@($phoneExpected.Keys) -join ', ')] and watch [$(@($watchExpected.Keys) -join ', ')]."
    } else {
        foreach ($k in $phoneExpected.Keys) {
            if ($phoneExpected[$k] -ne $watchExpected[$k]) {
                $findings += "S2464: WearSettingsPayloadDecoder.EXPECTED for '$k' differs in JsonKind: phone JsonKind.$($phoneExpected[$k]) vs watch JsonKind.$($watchExpected[$k])."
            }
        }
    }
}

# 12. S2461: the sync time may only be written where a full exchange completed. Moving this call to the
#     button press, or to an unrelated acknowledgement, makes the caption confidently wrong rather than
#     merely stale - a resources ack was doing exactly that until S2461 removed it.
$syncWriteAllowed = @('WearSettingsMirrorStore.kt', 'MergeWearSettingsReportUseCase.kt')
$syncRoots = @('app_v2/src/main', 'app_v2/src/wearGms')
foreach ($rel in $syncRoots) {
    $rootPath = Join-Path $root $rel
    if (-not (Test-Path -LiteralPath $rootPath)) {
        Write-Error "assert-wear-settings-parity: could not verify - missing source root $rel" -ErrorAction Continue
        exit 2
    }
    foreach ($file in Get-ChildItem -LiteralPath $rootPath -Recurse -File -Filter '*.kt') {
        if ($file.Name -in $syncWriteAllowed) { continue }
        if ((Get-Content -LiteralPath $file.FullName -Raw) -match 'markSynced\s*\(') {
            $findings += "S2461: $($file.Name) calls markSynced - only MergeWearSettingsReportUseCase may write the sync time, because only a merged report proves a full exchange completed."
        }
    }
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host "assert-wear-settings-parity: PASS - $($phoneEntries.Count) watch setting(s), every consumer and both row orders in step."
    }
    exit 0
}

Write-Error ("assert-wear-settings-parity: FAIL - " + $findings.Count + " divergence(s):`n" +
    (($findings | Sort-Object -Unique) -join "`n")) -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
