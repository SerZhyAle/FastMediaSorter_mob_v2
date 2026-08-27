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
    registry against every consumer derived from it and names the missing side.

    Checks:
      1. The two WearSettingsRegistry copies list the same entry ids.
      2. A BOTH entry has a matching field in both WearSettingsPayload copies.
      3. A BOTH entry has a matching watch DataStore key.
      4. A non-BOTH entry carries a non-empty exceptionReason.
      5. A watch DataStore key a settings screen writes is present in the registry.
      6. An entry the owner can see is published in SettingsDocScopeCatalog.wearEntries.
      7. A BOTH entry's watch setter records an edit time (a stampedEdit call site).

    Rule 33 class, stated at birth: PER-TICKET. Its evidence exists only at the moment of the change -
    the author is the one who knows whether a new setting was meant to be one-sided - and the settings
    reference it guards is read by agents between releases, where staleness poisons decisions.

.NOTES
    Exit codes:
      0 - parity holds; or a divergence was reported without -Gate, matching the advisory shape of
          the sibling gates in assert-fast-gates.ps1.
      1 - a divergence was found and -Gate was passed.
      2 - a source file could not be read, or a registry parsed to zero entries, so nothing was
          actually checked. A caller must tell this from 1: "found a defect" and "did not look" are
          different answers.
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
        $entries += [pscustomobject]@{
            Field     = $field.Groups['v'].Value
            WatchKey  = if ($key.Success) { $key.Groups['v'].Value } else { $null }
            DocScope  = if ($doc.Success) { $doc.Groups['v'].Value } else { $null }
            Ownership = if ($own.Success) { $own.Groups['v'].Value } else { 'UNKNOWN' }
            Reason    = if ($reason.Success) { $reason.Groups['v'].Value } else { $null }
            Source    = $Label
        }
    }
    return $entries
}

$phoneEntries = Read-RegistryEntries -Source $text.PhoneRegistry -Label 'phone'
$watchEntries = Read-RegistryEntries -Source $text.WatchRegistry -Label 'watch'

if ($phoneEntries.Count -eq 0 -or $watchEntries.Count -eq 0) {
    Write-Error 'assert-wear-settings-parity: could not verify - a registry parsed to zero entries.' -ErrorAction Continue
    exit 2
}

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

if ($findings.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host "assert-wear-settings-parity: PASS - $($phoneEntries.Count) watch setting(s), every consumer in step."
    }
    exit 0
}

Write-Error ("assert-wear-settings-parity: FAIL - " + $findings.Count + " divergence(s):`n" +
    (($findings | Sort-Object -Unique) -join "`n")) -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
