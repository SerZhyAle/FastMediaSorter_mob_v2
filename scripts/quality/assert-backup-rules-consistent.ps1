#requires -Version 7.0
<#
.SYNOPSIS
    S1552/S3480: in every module the API 31+ data-extraction rules must repeat every exclusion the
    pre-31 backup rules state, and the manifest must point at both files.

.DESCRIPTION
    Each module (`app_v2`, `wear`) declares two backup configurations. `backup_rules.xml` governs Auto
    Backup up to API 30; `data_extraction_rules.xml` supersedes it from API 31 for both cloud backup and
    device-to-device transfer. Nothing ties them together, so an exclusion written in one silently stops
    applying on every device running the other.

    That is not hypothetical. Until S1552 `data_extraction_rules.xml` carried the generic value-resource
    root instead of `<data-extraction-rules>`, which is a perfectly valid resource file - the build
    packages it and lint says nothing - but it declares no rule whatsoever. For the file's whole life
    the settings DataStore and the Keystore-encrypted credentials database were eligible for cloud
    backup and D2D transfer on API 31+, against the intent written down in `backup_rules.xml`. The
    owner saw it as settings rolling back to an old snapshot after every reinstall.

    S3480 found the second shape of the same hole: the watch had `allowBackup="true"` and no rules
    files at all, so every Keystore-backed EncryptedSharedPreferences file on it was backed up and
    transferred. A gate that only looked at `app_v2` could not see it, so the module list is explicit
    and the manifest wiring is checked too.

    This gate fails when, for any listed module:
      - the main manifest enables backup without referencing both rules files;
      - either file is missing its expected root element (the original defect);
      - a `domain`+`path` pair excluded by `<full-backup-content>` is absent from `<cloud-backup>`;
      - the same pair is absent from `<device-transfer>`.

    A module whose manifest sets `allowBackup="false"` is skipped: it backs nothing up, so it needs no
    rules.

    Subset, not equality: the API 31+ file may exclude MORE than the pre-31 one, because the newer
    platform can express rules the older cannot. It may never exclude less - that is the direction
    that loses a protection.

    Deliberately blind to `<include>` elements. An include list changes the semantics from
    "everything except" to "only these", at which point a pairwise exclusion comparison means nothing;
    if the project ever adopts includes, this gate needs rewriting rather than extending.

.PARAMETER Root
    Repository root to judge. Defaults to this script's repository; a test points it at a fixture copy.

.NOTES
    Run from anywhere; paths are resolved relative to the repo root.

    Exit codes:
      0 - clean, every module wires both files and repeats every pre-31 exclusion in both API 31+ sections
      1 - at least one exclusion or manifest reference is missing, or a root element is wrong
      2 - cannot verify: a manifest or rules file is missing or is not well-formed XML
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    [string]$Root
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $Root) { $Root = (Resolve-Path "$PSScriptRoot/../..").Path }

$modules = @('app_v2', 'wear')
$androidNs = 'http://schemas.android.com/apk/res/android'

function Read-RulesXml {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Error "assert-backup-rules-consistent: file not found - '$Path'." -ErrorAction Continue
        exit 2
    }
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

$violations = [System.Collections.Generic.List[string]]::new()
$checked = [System.Collections.Generic.List[string]]::new()

foreach ($module in $modules) {
    $manifestXml = Read-RulesXml -Path (Join-Path $Root "$module/src/main/AndroidManifest.xml")
    $application = $manifestXml.SelectSingleNode('/manifest/application')
    if ($null -eq $application) {
        $violations.Add("${module}: main manifest has no <application> element.")
        continue
    }
    if ($application.GetAttribute('allowBackup', $androidNs) -eq 'false') { continue }

    $checked.Add($module)
    $wiring = [ordered]@{
        'fullBackupContent'   = '@xml/backup_rules'
        'dataExtractionRules' = '@xml/data_extraction_rules'
    }
    foreach ($attribute in $wiring.Keys) {
        $actual = $application.GetAttribute($attribute, $androidNs)
        if ($actual -ne $wiring[$attribute]) {
            $violations.Add("${module}: manifest <application> android:$attribute is '$actual', expected '$($wiring[$attribute])' - backup is enabled with no rules there.")
        }
    }

    $xmlDir = Join-Path $Root "$module/src/main/res/xml"
    $legacyXml = Read-RulesXml -Path (Join-Path $xmlDir 'backup_rules.xml')
    $modernXml = Read-RulesXml -Path (Join-Path $xmlDir 'data_extraction_rules.xml')

    $legacyRoot = $legacyXml.SelectSingleNode('/full-backup-content')
    if ($null -eq $legacyRoot) {
        $violations.Add("${module} backup_rules.xml: root element is not <full-backup-content> - the pre-31 rules declare nothing.")
    }
    $modernRoot = $modernXml.SelectSingleNode('/data-extraction-rules')
    if ($null -eq $modernRoot) {
        $violations.Add("${module} data_extraction_rules.xml: root element is not <data-extraction-rules> - the API 31+ rules declare nothing (this was the S1552 defect).")
    }
    if ($null -eq $legacyRoot -or $null -eq $modernRoot) { continue }

    $legacyKeys = Get-ExclusionKeys -Section $legacyRoot
    foreach ($sectionName in @('cloud-backup', 'device-transfer')) {
        $section = $modernRoot.SelectSingleNode($sectionName)
        if ($null -eq $section) {
            $violations.Add("${module} data_extraction_rules.xml: <$sectionName> section is absent - every pre-31 exclusion is unenforced there.")
            continue
        }

        $sectionKeys = Get-ExclusionKeys -Section $section
        foreach ($key in $legacyKeys) {
            if (-not $sectionKeys.Contains($key)) {
                $parts = $key -split '\|', 2
                $violations.Add("${module} data_extraction_rules.xml <$sectionName>: missing exclusion domain='$($parts[0])' path='$($parts[1])', which backup_rules.xml states.")
            }
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Error "assert-backup-rules-consistent: $($violations.Count) violation(s) - backup configuration is inconsistent." -ErrorAction Continue
    foreach ($violation in $violations) {
        Write-Host "  $violation"
    }
    Write-Host "  Fix: point the manifest at both rules files and repeat each pre-31 exclusion in both <cloud-backup> and <device-transfer>."
    exit 1
}

if (-not $Quiet) {
    Write-Host "assert-backup-rules-consistent: OK - modules [$($checked -join ', ')] wire both rules files and repeat every pre-31 exclusion."
}
exit 0
