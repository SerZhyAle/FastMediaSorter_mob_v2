<#
.SYNOPSIS
    S2642: a phone/watch wire vocabulary outside the settings channel diverged, or a new mirrored one
    was added without being declared here.

.DESCRIPTION
    Eight vocabularies cross the Wear Data Layer as TEXT outside the settings channel, and each exists
    as two hand-written declarations in two separately compiled modules that share no artifact. The
    two are joined by nothing but matching characters: renaming or adding on one side compiles both
    modules and surfaces as an event the far side does not recognise, a transfer outcome nobody reads,
    or a player command that stops arriving - with no build error and no message to the owner.

    The settings channel has had this check since S2620 (assert-wear-settings-parity.ps1, check 13).
    This gate is its sibling for everything else. It is a separate file rather than four more rows in
    that one because that script is thirteen checks about the settings REGISTRY - its path table names
    settings files, its synopsis promises settings, and its exit-2 text enumerates the registry, the
    payload, the watch preferences, the doc catalog and the settings screen. Rows naming
    WearDataLayerPaths and WearFileTransfer would make every one of those sentences false. The parsers
    are shared instead, which is where the duplication actually mattered (S1621).

    Two halves:

    1. COMPARISON. Each declared row is compared as a SET - declaration order is never checked,
       because the EVENT_* constants are genuinely written in a different order on the two sides and
       that is not a defect. Constant families compare their name -> value MAP rather than their value
       set: swapping two constants' values leaves the set identical while inverting the wire.

    2. DISCOVERY. Every enum declared in BOTH modules' wire model trees must appear in the table
       below. This is what makes a new vocabulary impossible to add silently - the half that outlives
       the eight rows, since the rows only cover what existed when they were written.

    Discovery keys on a name declared in both modules, so a vocabulary whose two sides are NOT
    same-named is invisible to it and can only be covered by a row. S2641 added the first such row:
    the phone's ResourceType.WATCH_TRANSFERABLE subset against the watch's parseType branch list.
    A channel of that shape must be declared here by hand - nothing will discover it.

    A row is Mirrored - the two sides must declare the same vocabulary - or LocalOnly, which asserts
    the OPPOSITE: the name is shared by coincidence and the type must never reach the wire. LocalOnly
    is not an exemption from checking, it is a different check, so the asymmetry stays legal only
    while it stays off the wire.

.PARAMETER Gate
    Fail with exit 1 on a divergence. Without it a divergence is reported and the script exits 0,
    matching the advisory shape of the sibling gates in assert-fast-gates.ps1.

.PARAMETER Quiet
    Suppress the per-row listing; the verdict line and any finding still print.

.PARAMETER PhoneRoot
.PARAMETER WatchRoot
    Alternate module roots, for the regression suite's fixtures. Default to this repository's own.

.NOTES
    Exit codes:
      0 - parity holds; or a divergence was reported without -Gate.
      1 - a divergence was found and -Gate was passed; or a mirrored enum is declared in both modules
          and named by no row below.
      2 - a declared file could not be read, or a row parsed to zero members on either side, or a
          LocalOnly row carries no reason - so nothing was actually checked. A caller must tell this
          from 1: "found a defect" and "did not look" are different answers, and an empty parse
          reported as PASS is precisely the silent success this gate exists to prevent.
#>

[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    [string]$PhoneRoot,
    [string]$WatchRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/wear-vocabulary-parsers.ps1')

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $PhoneRoot) { $PhoneRoot = Join-Path $root 'app_v2/src/main/java/com/sza/fastmediasorter' }
if (-not $WatchRoot) { $WatchRoot = Join-Path $root 'wear/src/main/java/com/sza/fastmediasorter/wear' }

$problems = @()
$unreadable = @()

$vocabularies = @(
    @{ Name = 'WearDataLayerPaths route constants'; Kind = 'Mirrored'; Compare = 'constMapByValuePrefix'
       PhoneFile = 'service/WearDataLayerPaths.kt'; WatchFile = 'data/wear/WearDataLayerPaths.kt'
       ValuePrefix = '/fms/' },

    @{ Name = 'EVENT_* (WearEventEnvelope.eventType)'; Kind = 'Mirrored'; Compare = 'constMap'
       PhoneFile = 'service/WearDataLayerPaths.kt'; WatchFile = 'data/wear/WearDataLayerPaths.kt'
       Prefix = 'EVENT_' },

    @{ Name = 'WearStreamTransferAck.OUTCOME_*'; Kind = 'Mirrored'; Compare = 'companionConstMap'
       PhoneFile = 'domain/model/WearStreamTransferPayload.kt'; WatchFile = 'domain/model/WearStreamTransferPayload.kt'
       Class = 'WearStreamTransferAck'; Prefix = 'OUTCOME_' },

    @{ Name = 'WearFileTransferAck.OUTCOME_*'; Kind = 'Mirrored'; Compare = 'companionConstMap'
       PhoneFile = 'domain/model/WearFileTransfer.kt'; WatchFile = 'domain/model/WearFileTransferMetadata.kt'
       Class = 'WearFileTransferAck'; Prefix = 'OUTCOME_' },

    @{ Name = 'WearFileReceiveAck.OUTCOME_*'; Kind = 'Mirrored'; Compare = 'companionConstMap'
       PhoneFile = 'domain/model/WearFileTransfer.kt'; WatchFile = 'domain/model/WearFileTransferMetadata.kt'
       Class = 'WearFileReceiveAck'; Prefix = 'OUTCOME_' },

    @{ Name = 'WearPlaybackCommand'; Kind = 'Mirrored'; Compare = 'serializedVsPlain'
       PhoneFile = 'domain/model/WearPlaybackCommand.kt'; WatchFile = 'domain/model/WearPlaybackCommand.kt'
       Type = 'WearPlaybackCommand' },

    @{ Name = 'WearOpenOnPhoneOutcome'; Kind = 'Mirrored'; Compare = 'enum'
       PhoneFile = 'domain/model/WearOpenOnPhonePayload.kt'; WatchFile = 'domain/model/WearOpenOnPhonePayload.kt'
       Type = 'WearOpenOnPhoneOutcome' },

    @{ Name = 'WearPhoneResourceRequestKind'; Kind = 'Mirrored'; Compare = 'serializedVsPlain'
       PhoneFile = 'domain/model/WearPhoneResourcePayload.kt'; WatchFile = 'domain/model/WearPhoneResourcePayload.kt'
       Type = 'WearPhoneResourceRequestKind' },

    @{ Name = 'WearPhoneResourceResponseStatus'; Kind = 'Mirrored'; Compare = 'serializedVsPlain'
       PhoneFile = 'domain/model/WearPhoneResourcePayload.kt'; WatchFile = 'domain/model/WearPhoneResourcePayload.kt'
       Type = 'WearPhoneResourceResponseStatus' },

    # S2641: the one row whose two sides are not same-named, and the reason the DISCOVERY half below
    # cannot be the only guard. The phone puts ResourceType.name into a source payload after filtering
    # by the WATCH_TRANSFERABLE subset; the watch resolves it by explicit branch in parseType and drops
    # an unlisted name as a skipped record. Neither declaration is an enum the other module declares,
    # so widening one alone compiles both modules and loses the source at import with no message.
    @{ Name = 'ResourceType.WATCH_TRANSFERABLE / ImportNetworkSourcesUseCase.parseType'
       Kind = 'Mirrored'; Compare = 'namedSetVsWhenLiterals'
       PhoneFile = 'domain/model/Models.kt'; WatchFile = 'domain/usecase/ImportNetworkSourcesUseCase.kt'
       PhoneSet = 'WATCH_TRANSFERABLE'; WatchFunction = 'parseType' },

    # Not a wire vocabulary, but both copies' KDoc states the invariant in words - "the copy is
    # deliberate and the pair must move together" - so the row makes that claim checkable instead of
    # leaving it as a request in a comment.
    @{ Name = 'WearSyncLeg'; Kind = 'Mirrored'; Compare = 'enum'
       PhoneFile = 'domain/model/WearSyncOutcome.kt'; WatchFile = 'domain/model/WearSyncOutcome.kt'
       Type = 'WearSyncLeg' },

    @{ Name = 'WearFileReceiveOutcome'; Kind = 'LocalOnly'; Type = 'WearFileReceiveOutcome'
       PhoneFile = 'domain/model/WearFileTransfer.kt'; WatchFile = 'domain/model/WearFileTransferMetadata.kt'
       Reason = 'Each side describes what IT did with an incoming file and never sends the value. The watch translates it into the WearFileTransferAck.OUTCOME_* vocabulary in WearTransferOutcomeCoordinator before answering; the phone consumes it locally in ReceiveWatchFileUseCase. The wire vocabulary of this channel is WearFileReceiveAck.OUTCOME_*, checked as Mirrored above. The differing member sets (phone 5, watch 3) are therefore two local models, not one drifted pair - WearFileReceiveResult beside it differs in field composition for the same reason.' },

    @{ Name = 'WearSettingOwnership'; Kind = 'LocalOnly'; Type = 'WearSettingOwnership'
       PhoneFile = 'domain/model/WearSettingsRegistry.kt'; WatchFile = 'domain/model/WearSettingsRegistry.kt'
       Reason = 'Registry metadata, not payload: it is a field of WearSettingScope declaring which side may edit a setting, read at compile time by the settings tooling and never written into a WearSettingsPayload. The registry pair it belongs to is the subject of S2620 checks 1-10, which this ticket lists as a non-goal.' },

    @{ Name = 'WearSettingsFieldIssue'; Kind = 'LocalOnly'; Type = 'WearSettingsFieldIssue'
       PhoneFile = 'domain/model/WearSettingsDecodeResult.kt'; WatchFile = 'domain/model/WearSettingsDecodeResult.kt'
       Reason = 'Decode diagnostics: a side produces it while parsing a payload that ARRIVED and consumes it locally - WatchWearListenerService filters on WRONG_TYPE to decide what to log. It is never placed into an outgoing payload in either direction, so the two copies describe each side reading, not the two sides agreeing.' }
)

function Read-SideOrNull {
    param([string]$Base, [string]$Relative, [string]$Label, [string]$Family)

    $relNormalized = $Relative -replace '[/\\]', [System.IO.Path]::DirectorySeparatorChar
    $path = Join-Path $Base $relNormalized
    if (-not (Test-Path -LiteralPath $path)) {
        $script:unreadable += "$Family - $Label side missing: $Relative"
        return $null
    }
    return Get-Content -LiteralPath $path -Raw
}

function Format-VocabularyMap {
    param($Map)
    return @($Map.GetEnumerator() | Sort-Object Key | ForEach-Object { "$($_.Key)=$($_.Value)" })
}

function Test-LocalOnlyStaysOffTheWire {
    param([string]$Source, [string]$Label, [hashtable]$Row)

    $found = @()

    $list = Get-KotlinEnumMemberList -Source $Source -TypeName $Row.Type
    if ($null -ne $list -and $list -match '@SerializedName') {
        $found += "$($Row.Name) [$Label] carries @SerializedName on a member - it is on the wire now, so the LocalOnly row no longer describes it. Make it Mirrored, or stop serializing it."
    }

    foreach ($m in [regex]::Matches($Source, '(?s)data\s+class\s+(?<cls>\w+)\s*\((?<body>.*?)\n\)')) {
        $body = $m.Groups['body'].Value
        if ($body -match '@SerializedName' -and $body -match ":\s*$([regex]::Escape($Row.Type))\b") {
            $found += "$($Row.Name) [$Label] is the type of a field in the serialized class $($m.Groups['cls'].Value) - it reaches the wire, so the LocalOnly row no longer describes it."
        }
    }

    return $found
}

foreach ($v in $vocabularies) {
    $phone = Read-SideOrNull -Base $PhoneRoot -Relative $v.PhoneFile -Label 'phone' -Family $v.Name
    $watch = Read-SideOrNull -Base $WatchRoot -Relative $v.WatchFile -Label 'watch' -Family $v.Name
    if ($null -eq $phone -or $null -eq $watch) { continue }

    if ($v.Kind -eq 'LocalOnly') {
        if ([string]::IsNullOrWhiteSpace($v.Reason)) {
            $unreadable += "$($v.Name) - LocalOnly row carries no Reason. A row exempting a type without saying why is a hole, not a decision."
            continue
        }
        $problems += Test-LocalOnlyStaysOffTheWire -Source $phone -Label 'phone' -Row $v
        $problems += Test-LocalOnlyStaysOffTheWire -Source $watch -Label 'watch' -Row $v
        continue
    }

    $phoneMap = [ordered]@{}
    $watchMap = [ordered]@{}
    switch ($v.Compare) {
        'constMap' {
            $phoneMap = Get-KotlinConstMap -Source $phone -Prefix $v.Prefix
            $watchMap = Get-KotlinConstMap -Source $watch -Prefix $v.Prefix
        }
        'constMapByValuePrefix' {
            $phoneMap = Get-KotlinConstMapByValuePrefix -Source $phone -ValuePrefix $v.ValuePrefix
            $watchMap = Get-KotlinConstMapByValuePrefix -Source $watch -ValuePrefix $v.ValuePrefix
        }
        'companionConstMap' {
            $phoneMap = Get-KotlinCompanionConstMap -Source $phone -ClassName $v.Class -Prefix $v.Prefix
            $watchMap = Get-KotlinCompanionConstMap -Source $watch -ClassName $v.Class -Prefix $v.Prefix
        }
        'enum' {
            Get-KotlinEnumMember -Source $phone -TypeName $v.Type | ForEach-Object { $phoneMap[$_] = $_ }
            Get-KotlinEnumMember -Source $watch -TypeName $v.Type | ForEach-Object { $watchMap[$_] = $_ }
        }
        'namedSetVsWhenLiterals' {
            # S2641: a subset declaration against a branch list. Compared as a set of names, because
            # neither side has an order the wire can observe.
            Get-KotlinNamedSetMember   -Source $phone -SetName $v.PhoneSet       | ForEach-Object { $phoneMap[$_] = $_ }
            Get-KotlinWhenBranchLiteral -Source $watch -FunctionName $v.WatchFunction | ForEach-Object { $watchMap[$_] = $_ }
        }
        'serializedVsPlain' {
            # The phone pins its wire names with @SerializedName and the watch resolves the raw name
            # against its member list, so this is the only pairing that reflects what travels.
            Get-KotlinEnumSerializedName -Source $phone -TypeName $v.Type | ForEach-Object { $phoneMap[$_] = $_ }
            Get-KotlinEnumMember        -Source $watch -TypeName $v.Type | ForEach-Object { $watchMap[$_] = $_ }
        }
    }

    if ($phoneMap.Count -eq 0) { $unreadable += "$($v.Name) - phone side parsed to zero members."; continue }
    if ($watchMap.Count -eq 0) { $unreadable += "$($v.Name) - watch side parsed to zero members."; continue }

    $phoneFmt = Format-VocabularyMap $phoneMap
    $watchFmt = Format-VocabularyMap $watchMap
    if (($phoneFmt -join ';') -ne ($watchFmt -join ';')) {
        $onlyPhone = @($phoneFmt | Where-Object { $_ -notin $watchFmt })
        $onlyWatch = @($watchFmt | Where-Object { $_ -notin $phoneFmt })
        $msg = "$($v.Name) diverges."
        if ($onlyPhone) { $msg += " Phone only: $($onlyPhone -join ', ')." }
        if ($onlyWatch) { $msg += " Watch only: $($onlyWatch -join ', ')." }
        $problems += $msg
    }
}

# Discovery. Keyed on the fact of a mirrored declaration rather than on a call shape: the eight
# families are received in as many different ways - valueOf, Gson's own adapter, a string branch -
# so there is no single call site to read, the way the settings channel's apply(..) form allowed.
function Get-DeclaredEnumName {
    param([string]$Dir)

    $names = @()
    if (-not (Test-Path $Dir)) { return $names }
    foreach ($file in Get-ChildItem -Path $Dir -Filter 'Wear*.kt' -File) {
        $src = Get-Content $file.FullName -Raw
        foreach ($m in [regex]::Matches($src, '(?m)^\s*enum\s+class\s+(?<n>\w+)')) {
            $names += $m.Groups['n'].Value
        }
    }
    return @($names | Sort-Object -Unique)
}

$phoneEnums = Get-DeclaredEnumName -Dir (Join-Path $PhoneRoot 'domain/model')
$watchEnums = Get-DeclaredEnumName -Dir (Join-Path $WatchRoot 'domain/model')
$mirroredNames = @($phoneEnums | Where-Object { $_ -in $watchEnums })

$declaredTypes = @($vocabularies | Where-Object { $_.ContainsKey('Type') } | ForEach-Object { $_.Type })
foreach ($name in $mirroredNames) {
    if ($name -notin $declaredTypes) {
        $problems += "$name is declared as an enum in BOTH modules but no row in this gate names it. Add a row - Mirrored if its names cross the wire, LocalOnly with a Reason if they do not. Silencing the check is not one of the two."
    }
}

$mirroredCount = @($vocabularies | Where-Object { $_.Kind -eq 'Mirrored' }).Count
$localCount    = @($vocabularies | Where-Object { $_.Kind -eq 'LocalOnly' }).Count

if (-not $Quiet) {
    foreach ($v in $vocabularies) { Write-Host "  [$($v.Kind)] $($v.Name)" }
}

if ($unreadable.Count -gt 0) {
    foreach ($u in $unreadable) { Write-Error "assert-wear-wire-vocabulary-parity: COULD NOT CHECK - $u" -ErrorAction Continue }
    exit 2
}

if ($problems.Count -gt 0) {
    foreach ($p in $problems) { Write-Error "assert-wear-wire-vocabulary-parity: $p" -ErrorAction Continue }
    if ($Gate) { exit 1 }
    exit 0
}

Write-Host "assert-wear-wire-vocabulary-parity: PASS - $mirroredCount mirrored vocabular(ies), $localCount local-only, $($mirroredNames.Count) mirrored enum name(s) discovered and all declared."
exit 0
