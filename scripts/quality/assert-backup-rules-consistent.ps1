#requires -Version 7.0
<#
.SYNOPSIS
    S1552: the API 31+ data-extraction rules must repeat every exclusion the pre-31 backup rules state.

.DESCRIPTION
    The app declares two backup configurations. `backup_rules.xml` governs Auto Backup up to API 30;
    `data_extraction_rules.xml` supersedes it from API 31 for both cloud backup and device-to-device
    transfer. Nothing ties them together, so an exclusion written in one silently stops applying on
    every device running the other.

    That is not hypothetical. Until S1552 `data_extraction_rules.xml` carried the generic value-resource
    root instead of `<data-extraction-rules>`, which is a perfectly valid resource file - the build
    packages it and lint says nothing - but it declares no rule whatsoever. For the file's whole life
    the settings DataStore and the Keystore-encrypted credentials database were eligible for cloud
    backup and D2D transfer on API 31+, against the intent written down in `backup_rules.xml`. The
    owner saw it as settings rolling back to an old snapshot after every reinstall.

    This gate fails when:
      - either file is missing its expected root element (the original defect);
      - a `domain`+`path` pair excluded by `<full-backup-content>` is absent from `<cloud-backup>`;
      - the same pair is absent from `<device-transfer>`.

    Subset, not equality: the API 31+ file may exclude MORE than the pre-31 one, because the newer
    platform can express rules the older cannot. It may never exclude less - that is the direction
    that loses a protection.

    Deliberately blind to `<include>` elements. An include list changes the semantics from
    "everything except" to "only these", at which point a pairwise exclusion comparison means nothing;
    if the project ever adopts includes, this gate needs rewriting rather than extending.

.NOTES
    Run from anywhere; paths are resolved relative to the repo root.

    Exit codes:
      0 - clean, every pre-31 exclusion is repeated in both API 31+ sections
      1 - at least one exclusion is missing, or a root element is wrong
      2 - cannot verify: a rules file is missing or is not well-formed XML
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path "$PSScriptRoot/../..").Path
$legacyPath = Join-Path $root 'app_v2/src/main/res/xml/backup_rules.xml'
$modernPath = Join-Path $root 'app_v2/src/main/res/xml/data_extraction_rules.xml'

foreach ($path in @($legacyPath, $modernPath)) {
    if (-not (Test-Path $path)) {
        Write-Error "assert-backup-rules-consistent: rules file not found - '$path'." -ErrorAction Continue
        exit 2
    }
}

function Read-RulesXml {
    param([string]$Path)

    try {
        return [xml](Get-Content -LiteralPath $Path -Raw)
    } catch {
        Write-Error "assert-backup-rules-consistent: '$Path' is not well-formed XML - $($_.Exception.Message)" -ErrorAction Continue
        exit 2
    }
}

# A rule's identity is the pair it matches on, so that is what gets compared across files.
function Get-ExclusionKeys {
    param([System.Xml.XmlNode]$Section)

    $keys = [System.Collections.Generic.HashSet[string]]::new()
    if ($null -eq $Section) { return ,$keys }

    foreach ($node in $Section.SelectNodes('exclude')) {
        $domain = $node.GetAttribute('domain')
        $path = $node.GetAttribute('path')
        [void]$keys.Add("$domain|$path")
    }

    # The comma is load-bearing: PowerShell unrolls an enumerable on output, so a bare
    # `return $keys` hands back $null for an empty set and a plain string[] otherwise -
    # and string[] has its own .Contains, so the caller's set logic would keep "working"
    # while comparing the wrong things.
    return ,$keys
}

$legacyXml = Read-RulesXml -Path $legacyPath
$modernXml = Read-RulesXml -Path $modernPath

$violations = [System.Collections.Generic.List[string]]::new()

$legacyRoot = $legacyXml.SelectSingleNode('/full-backup-content')
if ($null -eq $legacyRoot) {
    $violations.Add("backup_rules.xml: root element is not <full-backup-content> - the pre-31 rules declare nothing.")
}

$modernRoot = $modernXml.SelectSingleNode('/data-extraction-rules')
if ($null -eq $modernRoot) {
    $violations.Add("data_extraction_rules.xml: root element is not <data-extraction-rules> - the API 31+ rules declare nothing (this was the S1552 defect).")
}

if ($violations.Count -eq 0) {
    $legacyKeys = Get-ExclusionKeys -Section $legacyRoot

    foreach ($sectionName in @('cloud-backup', 'device-transfer')) {
        $section = $modernRoot.SelectSingleNode($sectionName)
        if ($null -eq $section) {
            $violations.Add("data_extraction_rules.xml: <$sectionName> section is absent - every pre-31 exclusion is unenforced there.")
            continue
        }

        $sectionKeys = Get-ExclusionKeys -Section $section
        foreach ($key in $legacyKeys) {
            if (-not $sectionKeys.Contains($key)) {
                $parts = $key -split '\|', 2
                $violations.Add("data_extraction_rules.xml <$sectionName>: missing exclusion domain='$($parts[0])' path='$($parts[1])', which backup_rules.xml states.")
            }
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Error "assert-backup-rules-consistent: $($violations.Count) violation(s) - the two backup configurations disagree." -ErrorAction Continue
    foreach ($violation in $violations) {
        Write-Host "  $violation"
    }
    Write-Host "  Fix: repeat each pre-31 exclusion in both <cloud-backup> and <device-transfer>."
    exit 1
}

if (-not $Quiet) {
    Write-Host "assert-backup-rules-consistent: OK - both API 31+ sections repeat every pre-31 exclusion."
}
exit 0
