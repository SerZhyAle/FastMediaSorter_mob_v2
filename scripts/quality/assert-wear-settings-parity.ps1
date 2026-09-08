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
     13. S2620 mirrored enum vocabulary parity: for every pair in $mirroredEnums the watch enum's
         member names and the phone mirror's vocabulary are the same SET, and every field the watch
         resolves through fromNameOrDefault is declared in that table with the type its call site
         actually uses.

    Check 13 is check 11 one level down. S2464 compares the field NAMES of the contract; a field
    whose value is an enum's constant name carries a second vocabulary INSIDE that value, and the two
    declarations of it are connected by nothing but matching text - the modules compile separately, so
    renaming a constant on one side alone compiles on both. The loss is silent in both directions: a
    phone-only constant is offered, chosen, sent, and collapsed to the default by fromNameOrDefault; a
    watch-only constant matches no row in the companion picker, which then shows a selection the owner
    never made. Sets, not order - declaration order already differs on a green tree, and on-screen
    order is check 9 and 10's subject. 13c is what keeps the table honest, the same job check 4 does
    for the declaration in assert-wear-mirrored-strings.ps1: a pair nobody declared produces no
    finding and no build error, so its absence is invisible unless something looks for it.

    Each side is read where it actually lives rather than where it ought to. The phone declares its
    three mirrors in two shapes - a real enum, or a const block under one prefix - since S2643 moved
    the view modes into WearSettingsPayload beside the other two. Before that they sat in the
    companion window as a listOf of value-to-label pairs and this gate read them there, because a
    gate that only starts working after an unrelated refactor lands would not have been run.

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
          catalog, a settings screen, the companion window, the watch apply use case or a mirrored
          enum declaration); or a registry parsed to zero entries; or a $mirroredEnums row parsed to
          zero members on either side; or ApplyWearSettingsUseCase yielded no enum-resolved call site
          at all - so nothing was actually checked. A caller must tell this from 1: "found a defect"
          and "did not look" are different answers, and every one of these cases would otherwise
          report the silent PASS that is exactly the failure this script exists to prevent.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# S2642: the Kotlin declaration parsers are shared with assert-wear-wire-vocabulary-parity.ps1 - see
# that file's header for why one copy rather than two.
. (Join-Path $PSScriptRoot 'lib/wear-vocabulary-parsers.ps1')

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
    WatchApply     = 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/ApplyWearSettingsUseCase.kt'
    PhonePowerSaving = 'app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PowerSavingTrigger.kt'
    WatchPowerSaving = 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/PowerSavingTrigger.kt'
    WatchViewMode    = 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearViewMode.kt'
    WatchBackgroundMode = 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearBackgroundMode.kt'
    WatchColorScheme = 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearColorScheme.kt'
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
    PhoneRows          = 'WearWatchSettingsGroup'
}

# S2620: the enum vocabularies whose MEMBER NAMES ride the settings contract as strings. One row per
# vocabulary, not per field - WearViewMode serves both viewMode and fileListViewMode.
#
# Declared here rather than in Kotlin because an annotation would have to be written twice, once per
# module, which is the hand-mirrored duplication this check exists to catch; and it cannot be derived
# from WearSettingsPayloadDecoder.EXPECTED, whose JsonKind is STRING for all five fields and so cannot
# tell a free-text field from one carrying a constant name. Same shape and lifetime as the two tables
# above, which is why it is a table here and not a .psd1 beside the script.
#
# 'Kind' says how to read a side, and there are two shapes: a real enum, or a const block under one
# prefix. There were three until S2643 - the companion window declared the view modes as a listOf of
# value-to-label pairs, the weakest of the forms, and S2620 could not consolidate it because that file
# carried three detekt findings it had not created. S2643 cleared them and moved the constants into
# WearSettingsPayload, so this row reads them by prefix like its two neighbours and the 'pairs' parser
# is gone. The listOf in the companion window survives as a label table only, and its values now come
# from the same constants this row reads.
$mirroredEnums = @(
    @{
        Name       = 'PowerSavingTrigger'
        Fields     = @('powerSavingTrigger')
        WatchKey   = 'WatchPowerSaving'; WatchKind = 'enum';  WatchType = 'PowerSavingTrigger'
        PhoneKey   = 'PhonePowerSaving'; PhoneKind = 'enum';  PhoneType = 'PowerSavingTrigger'
    },
    @{
        Name       = 'WearViewMode'
        Fields     = @('viewMode', 'fileListViewMode')
        WatchKey   = 'WatchViewMode';    WatchKind = 'enum';  WatchType = 'WearViewMode'
        PhoneKey   = 'PhonePayload';     PhoneKind = 'const'; PhonePrefix = 'VIEW_MODE_'
    },
    @{
        Name       = 'WearBackgroundMode'
        Fields     = @('backgroundMode')
        WatchKey   = 'WatchBackgroundMode'; WatchKind = 'enum';  WatchType = 'WearBackgroundMode'
        PhoneKey   = 'PhonePayload';        PhoneKind = 'const'; PhonePrefix = 'BACKGROUND_MODE_'
    },
    @{
        Name       = 'WearColorScheme'
        Fields     = @('colorScheme')
        WatchKey   = 'WatchColorScheme'; WatchKind = 'enum';  WatchType = 'WearColorScheme'
        PhoneKey   = 'PhonePayload';     PhoneKind = 'const'; PhonePrefix = 'COLOR_SCHEME_'
    }
)

$text = @{}
foreach ($name in $paths.Keys) {
    $full = Join-Path $root $paths[$name]
    if (-not (Test-Path -LiteralPath $full)) {
        Write-Error "assert-wear-settings-parity: could not verify - missing $($paths[$name])" -ErrorAction Continue
        exit 2
    }
    $text[$name] = Get-Content -LiteralPath $full -Raw
}

# S2655: the watch settings live in a themed section per topic, not in one class, so "the watch
# stores this key" and "the watch setter stamps this field" are questions about the whole
# data/preferences tree. Reading only WearPreferencesRepositoryImpl.kt reported every shared setting
# as unstored the day the sections were split out, while every key and every stampedEdit call was
# still exactly where it had always been.
$watchPrefsRoot = Join-Path $root 'wear/src/main/java/com/sza/fastmediasorter/wear/data/preferences'
$text.WatchPrefs = (Get-ChildItem -LiteralPath $watchPrefsRoot -Recurse -File -Filter '*.kt' |
    Sort-Object FullName |
    ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"

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

# S2620: the member names of one `enum class`, in declaration order. S2642 moved the parse itself into
# scripts/quality/lib/wear-vocabulary-parsers.ps1, shared with assert-wear-wire-vocabulary-parity.ps1:
# two gates answering "what is declared in this file" must answer with one body of code, or a fix
# landed in one leaves the other reading a different vocabulary and the two disagree in silence
# (S1621). The move also fixed a live defect - the previous line-by-line scan returned one member of
# four for a single-line `enum class X { A, B, C }`, and two such sides compared as one-against-one
# and passed green while the rest of the members drifted unchecked.
function Read-EnumMembers {
    param([string]$Source, [string]$TypeName)

    return Get-KotlinEnumMember -Source $Source -TypeName $TypeName
}

# S2620: the string VALUES of `const val <Prefix>.. = ".."`. The value is what travels, not the
# constant's own name, so this returns the same vocabulary Read-EnumMembers does for the other side.
# The shared library returns name -> value; this channel compares values alone, so the keys are
# dropped here rather than in the library, where the other gate needs them (S2642 ADR-3).
function Read-ConstValues {
    param([string]$Source, [string]$Prefix)

    return @((Get-KotlinConstMap -Source $Source -Prefix $Prefix).Values)
}

# S2620: which payload fields the watch resolves through an enum, read from the apply(..) call sites
# themselves. Split on the call name for the reason Read-RegistryEntries does - the lambda bodies
# contain their own brackets and no single balanced regex survives them. Returns field -> enum type.
function Read-AppliedEnumFields {
    param([string]$Source)

    $found = [ordered]@{}
    foreach ($chunk in (($Source -split 'apply\(\s*resolver\s*,') | Select-Object -Skip 1)) {
        $field = [regex]::Match($chunk, '^\s*"(?<v>[^"]+)"')
        if (-not $field.Success) { continue }
        $resolver = [regex]::Match($chunk, '(?<type>\w+)\.fromNameOrDefault\s*\(')
        if ($resolver.Success) { $found[$field.Groups['v'].Value] = $resolver.Groups['type'].Value }
    }
    return $found
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
            $findings += "S2093: '$field' names watch key '$($entry.WatchKey)', which no watch preferences section declares."
        }
        # 7. A shared setting must record when it changed, or the merge cannot rank it.
        if ($field -notin $stampedFields) {
            $findings += "S2093: '$field' is BOTH but no watch preferences setter stamps it - add stampedEdit(""$field"")."
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
        $findings += "S2093: a watch preferences setter stamps '$stamped', which is in no WearSettingsRegistry entry."
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

# 13. S2620: the enum vocabularies that cross the wire as strings agree member-for-member, and every
#     enum-valued field is declared as a pair. Only the field's VALUE is checked here - S2464 above
#     checks the field NAMES. A constant on one side only is lost either way: sent from the phone it
#     collapses to the watch's default in fromNameOrDefault, and reported from the watch it matches no
#     option in the companion picker, so the owner is shown a selection nobody made. fromNameOrDefault
#     is a safety net for two independently updated INSTALLS, never a licence for two declarations in
#     one checkout to differ - they are compiled from one tree at one commit.
$declaredEnumFields = @{}
foreach ($pair in $mirroredEnums) {
    $watchSet = if ($pair.WatchKind -eq 'enum') {
        Read-EnumMembers -Source $text[$pair.WatchKey] -TypeName $pair.WatchType
    } else {
        Read-ConstValues -Source $text[$pair.WatchKey] -Prefix $pair.WatchPrefix
    }
    $phoneSet = switch ($pair.PhoneKind) {
        'enum'  { Read-EnumMembers -Source $text[$pair.PhoneKey] -TypeName $pair.PhoneType }
        default { Read-ConstValues -Source $text[$pair.PhoneKey] -Prefix $pair.PhonePrefix }
    }
    foreach ($f in $pair.Fields) { $declaredEnumFields[$f] = $pair }

    # A side that parses to nothing means the parser lost its grip on a file it was pointed at, which
    # is "did not look" and not "they agree" - the distinction this script's exit 2 exists to keep.
    if (@($watchSet).Count -eq 0 -or @($phoneSet).Count -eq 0) {
        Write-Error ("assert-wear-settings-parity: could not verify - mirrored pair '$($pair.Name)' parsed to " +
            "$(@($watchSet).Count) watch member(s) and $(@($phoneSet).Count) phone member(s).") -ErrorAction Continue
        exit 2
    }

    # 13b: compared as SETS. Declaration order already differs on a green tree - WearBackgroundMode
    # lists NONE first and the phone's const block lists it last - and on-screen order is S2169's
    # subject, checked above against the menu map.
    foreach ($missing in (@($watchSet) | Where-Object { $_ -notin @($phoneSet) })) {
        $findings += "S2620: '$($pair.Name)' declares '$missing' on the watch and the phone mirror does not - the watch can report a value the phone has no option for."
    }
    foreach ($missing in (@($phoneSet) | Where-Object { $_ -notin @($watchSet) })) {
        $findings += "S2620: '$($pair.Name)' declares '$missing' on the phone and the watch does not - the phone can send a value fromNameOrDefault silently collapses to the default."
    }
}

# 13c: the table cannot go stale in silence. Every field the watch resolves through fromNameOrDefault
# must be declared above, and its call-site type must be the one the row names - otherwise a row can
# point at the wrong enum and pass while checking nothing.
$appliedEnumFields = Read-AppliedEnumFields -Source $text.WatchApply
if ($appliedEnumFields.Count -eq 0) {
    Write-Error ('assert-wear-settings-parity: could not verify - no enum-resolved apply() call site ' +
        'parsed out of ApplyWearSettingsUseCase.') -ErrorAction Continue
    exit 2
}
foreach ($field in $appliedEnumFields.Keys) {
    if (-not $declaredEnumFields.ContainsKey($field)) {
        $findings += "S2620: ApplyWearSettingsUseCase resolves '$field' through $($appliedEnumFields[$field]).fromNameOrDefault, which is in no `$mirroredEnums row - declare its mirrored pair so the two vocabularies are compared."
        continue
    }
    $declaredType = $declaredEnumFields[$field].WatchType
    if ($declaredType -and $appliedEnumFields[$field] -ne $declaredType) {
        $findings += "S2620: '$field' is resolved by $($appliedEnumFields[$field]).fromNameOrDefault but `$mirroredEnums declares its watch side as '$declaredType' - the row is checking a different vocabulary from the one that runs."
    }
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host "assert-wear-settings-parity: PASS - $($phoneEntries.Count) watch setting(s), $($mirroredEnums.Count) mirrored enum vocabular(ies), every consumer and both row orders in step."
    }
    exit 0
}

Write-Error ("assert-wear-settings-parity: FAIL - " + $findings.Count + " divergence(s):`n" +
    (($findings | Sort-Object -Unique) -join "`n")) -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
