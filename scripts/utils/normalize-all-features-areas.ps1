#requires -Version 7.0
<#
.SYNOPSIS
    One-shot migration: fold the free-string ALL_FEATURES 'area' values onto the closed
    vocabulary declared in docs/ALL_FEATURES.schema.json (S2842).

.DESCRIPTION
    The inventory's 'area' had been a free string and had accumulated 91 distinct values
    across 1059 records, near-duplicates included. This rewrites each record's 'area' to
    its canonical area and touches nothing else on the line.

    The map below is a function of the OLD VALUE ALONE. No record is re-judged by its own
    text, and that is what makes the 91-to-31 fold reviewable: every record that carried
    'Player' becomes 'Media Player', including the ones a human would file under video or
    audio. Re-classifying by content is a different job and needs a different ticket.

    Coverage is asserted rather than assumed: a value in the ledger that is neither a
    canonical area nor a mapped legacy spelling stops the run and is named. An unmapped
    value means this map is incomplete - never that the record may be left alone, because
    the gate that follows the migration would refuse it for whoever closes a ticket next.

    Idempotent: a canonical value maps to itself, so a second run reports zero changes.

    Exit codes:
      0 - clean: dry run reported its plan, or -Apply wrote the ledger.
      1 - the ledger carries a value this map does not cover, or a record is not JSON.
      2 - cannot run: the schema, its area enum, or the ledger is missing.

.PARAMETER Apply
    Write the ledger. Without it the run is a dry run and the file is not touched.

.PARAMETER NoLegal
    Operate on the gitignored private ledger docs/ALL_FEATURES_noLegal.jsonl.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/normalize-all-features-areas.ps1
    pwsh -NoProfile -File scripts/utils/normalize-all-features-areas.ps1 -Apply
    pwsh -NoProfile -File scripts/utils/normalize-all-features-areas.ps1 -Apply -NoLegal
#>
[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$NoLegal
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'code-lock-scope.ps1')
$schemaPath = Join-Path $repoRoot 'docs/ALL_FEATURES.schema.json'
$ledgerName = if ($NoLegal) { 'ALL_FEATURES_noLegal.jsonl' } else { 'ALL_FEATURES.jsonl' }
$ledgerPath = Join-Path $repoRoot "docs/$ledgerName"

# Legacy spelling -> canonical area. Keys are matched case-insensitively, so the map does
# not need a row per capitalisation of the same word ('flashlight' / 'Flashlight').
$areaMap = [ordered]@{
    # Launcher: the home-screen replacement and everything reached from its surfaces.
    'Launcher & Desktop'        = 'Launcher'
    'Launcher Desktop'          = 'Launcher'
    'App Launch Panel'          = 'Launcher'
    'App-launch panel'          = 'Launcher'
    'Edge Gestures'             = 'Launcher'
    'Screen capture / Gestures' = 'Launcher'
    'Screen Gestures'           = 'Launcher'

    # The app's own main screen - distinct from the launcher above.
    'Resource List'             = 'Main Screen'
    'Resources'                 = 'Main Screen'

    'Browse'                    = 'Media Browsing'
    'File Browser'              = 'Media Browsing'

    # The mini-programs block.
    'Programs'                  = 'Programs & Tools'
    'Calculator'                = 'Programs & Tools'
    'Stopwatch'                 = 'Programs & Tools'
    'Front Flashlight'          = 'Programs & Tools'
    'flashlight'                = 'Programs & Tools'
    'Mirror'                    = 'Programs & Tools'
    'Game'                      = 'Programs & Tools'

    'Wear'                      = 'Wear OS'
    'wear-os'                   = 'Wear OS'
    'Wear OS UI'                = 'Wear OS'
    'Wear UI'                   = 'Wear OS'
    'Wear Settings'             = 'Wear OS'
    'Wear Player'               = 'Wear OS'
    'Wear Companion'            = 'Wear OS'
    'Wear OS Companion'         = 'Wear OS'
    'Wear Sync'                 = 'Wear OS'

    'Internet Streams'          = 'Streams'
    'Streaming'                 = 'Streams'
    'Broadcast'                 = 'Streams'

    # Records that do not say which medium they are about keep their own area.
    'Player'                    = 'Media Player'
    'Playback'                  = 'Media Player'
    'Standalone Player'         = 'Media Player'

    'Images'                    = 'Image & GIF Viewer'
    'Media / Image viewer'      = 'Image & GIF Viewer'

    'File transfer'             = 'File Operations'
    'File Saving'               = 'File Operations'
    'Import'                    = 'File Operations'
    'Scheduled operations'      = 'File Operations'
    'Link Download'             = 'File Operations'

    'Storage'                   = 'Sources & Storage'
    'Cloud'                     = 'Sources & Storage'

    'Network and Cloud'         = 'Network & Cloud'
    'Networking'                = 'Network & Cloud'
    'Network'                   = 'Network & Cloud'
    'Network Monitor'           = 'Network & Cloud'

    'Send-to menu'              = 'Sharing'
    'Resource Sharing'          = 'Sharing'

    'Capture'                   = 'Screen Capture'

    'Camera Capture'            = 'Camera'
    'Quick Capture'             = 'Camera'

    'VR'                        = 'VR & OpenXR'
    'VR and OpenXR'             = 'VR & OpenXR'
    'VR Cinema'                 = 'VR & OpenXR'
    'VR Player'                 = 'VR & OpenXR'

    'Settings'                  = 'Settings & Navigation'
    'User Interface'            = 'Settings & Navigation'
    'UI'                        = 'Settings & Navigation'
    'UI Polish'                 = 'Settings & Navigation'
    'Controls and keybindings'  = 'Settings & Navigation'
    'TV & Input'                = 'Settings & Navigation'

    'Settings > Permissions'    = 'Permissions'
    'Security'                  = 'Permissions'

    'Onboarding'                = 'Setup & Onboarding'
    'App Startup'               = 'Setup & Onboarding'

    'statistics'                = 'Usage Statistics'

    'Testing & Quality'         = 'Diagnostics'
    'System information'        = 'Diagnostics'

    'Extensions'                = 'Extensions & On-demand'
    'Delivery'                  = 'Extensions & On-demand'

    # A shipping channel is not a subject of its own, and it is not diagnostics either.
    'Distribution'              = 'General'
}

if (-not (Test-Path -LiteralPath $schemaPath)) {
    Write-Error "Schema not found at $schemaPath" -ErrorAction Continue; exit 2
}
$schema = Get-Content -LiteralPath $schemaPath -Raw -Encoding UTF8 | ConvertFrom-Json
$canonical = @($schema.properties.area.enum)
if ($canonical.Count -eq 0) {
    Write-Error "docs/ALL_FEATURES.schema.json declares no 'area' enum - nothing to migrate onto." -ErrorAction Continue; exit 2
}
if (-not (Test-Path -LiteralPath $ledgerPath)) {
    Write-Error "Ledger not found at $ledgerPath" -ErrorAction Continue; exit 2
}

# Ordinal, case-SENSITIVE. A default PowerShell hashtable answers ContainsKey('launcher')
# with the entry for 'Launcher', and `-eq` then calls the two equal, so ten lowercase
# 'launcher' records were left untouched as though they were already canonical. The gate
# that follows this migration compares with -ccontains and refused them; this is the same
# comparison, so the two cannot disagree about what "already canonical" means.
$canonLookup = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
foreach ($a in $canonical) { $canonLookup[[string]$a] = [string]$a }

$lines = @(Get-Content -LiteralPath $ledgerPath -Encoding UTF8)
$unmapped = [System.Collections.Generic.List[string]]::new()
$changes = [System.Collections.Generic.List[string]]::new()
$out = [System.Collections.Generic.List[string]]::new()
$lineNo = 0

foreach ($line in $lines) {
    $lineNo++
    if ($line.Trim().Length -eq 0) { $out.Add($line); continue }

    try { $obj = $line | ConvertFrom-Json -ErrorAction Stop }
    catch {
        Write-Error "L${lineNo}: not valid JSON - the ledger must be repaired before it can be migrated." -ErrorAction Continue
        exit 1
    }

    $old = [string]$obj.area
    $new = $null
    if ($canonLookup.ContainsKey($old)) { $new = $canonLookup[$old] }
    elseif ($areaMap.Contains($old)) { $new = [string]$areaMap[$old] }
    else {
        # Case-insensitive second pass: the ledger holds 'flashlight' and 'statistics'
        # beside their capitalised twins, and a map row per casing is noise.
        foreach ($k in $areaMap.Keys) {
            if ([string]::Equals($k, $old, [StringComparison]::OrdinalIgnoreCase)) { $new = [string]$areaMap[$k]; break }
        }
        if (-not $new) {
            foreach ($k in $canonLookup.Keys) {
                if ([string]::Equals($k, $old, [StringComparison]::OrdinalIgnoreCase)) { $new = $canonLookup[$k]; break }
            }
        }
    }

    if (-not $new) {
        if (-not $unmapped.Contains($old)) { $unmapped.Add($old) }
        $out.Add($line)
        continue
    }

    if ($new -ceq $old) { $out.Add($line); continue }

    # Rewrite the area only. Re-serialising the whole object would reorder its keys and
    # rewrite every escape in the file, turning a classification fix into a 1059-line diff
    # nobody can review - so the substitution is on the raw line, anchored to the field.
    $pattern = '("area"\s*:\s*)"(?:[^"\\]|\\.)*"'
    $replacement = '${1}' + ($new | ConvertTo-Json -Compress)
    $rewritten = [System.Text.RegularExpressions.Regex]::Replace($line, $pattern, $replacement, 'None', [TimeSpan]::FromSeconds(5))
    # -ceq, not -eq: a fold that only changes case ('launcher' -> 'Launcher') leaves a line
    # that -eq calls identical, and this guard would report the rewrite as having failed.
    if ($rewritten -ceq $line) {
        Write-Error "L${lineNo}: could not locate the 'area' field to rewrite." -ErrorAction Continue
        exit 1
    }
    $out.Add($rewritten)
    $changes.Add("L${lineNo}: '$old' -> '$new'")
}

if ($unmapped.Count -gt 0) {
    Write-Host "normalize-all-features-areas: FAIL - $($unmapped.Count) area value(s) in docs/$ledgerName are covered by neither the schema enum nor the legacy map:" -ForegroundColor Red
    foreach ($u in $unmapped) { Write-Host "    '$u'" -ForegroundColor Red }
    Write-Host "  Add a row to the map in this script, or add the value to 'area.enum' in docs/ALL_FEATURES.schema.json." -ForegroundColor Yellow
    exit 1
}

$distinctAfter = @($out | Where-Object { $_.Trim().Length -gt 0 } | ForEach-Object { ($_ | ConvertFrom-Json).area } | Sort-Object -Unique)

Write-Host ("normalize-all-features-areas: docs/{0} | records {1} | area changes {2} | distinct areas after {3}" -f `
    $ledgerName, @($lines | Where-Object { $_.Trim().Length -gt 0 }).Count, $changes.Count, $distinctAfter.Count)

if (-not $Apply) {
    foreach ($c in $changes) { Write-Host "  $c" }
    Write-Host "  DRY RUN - nothing written. Re-run with -Apply." -ForegroundColor Yellow
    exit 0
}

# The field's previous content is not recoverable from the migrated file, so the copy is
# taken before the write and not after it (strategic spec section 3.2).
$backupDir = Join-Path $repoRoot 'temp/S2842'
if (-not (Test-Path -LiteralPath $backupDir)) { New-Item -ItemType Directory -Path $backupDir -Force | Out-Null }
$backup = Join-Path $backupDir "$ledgerName.pre-S2842"
Copy-Item -LiteralPath $ledgerPath -Destination $backup -Force

$payload = ($out -join "`n")
if ($payload.Length -gt 0) { $payload += "`n" }
$tmp = "$ledgerPath.tmp"
$scope = $null
try {
    $scope = Enter-CodeLockOrExit -Path @($ledgerPath, $tmp) -Reason 'normalize-all-features-areas.ps1 -Apply'
    [System.IO.File]::WriteAllText($tmp, $payload, (New-Object System.Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $tmp -Destination $ledgerPath -Force
}
finally { Exit-CodeLockScope -Scope $scope }

Write-Host "  backup: $backup"
Write-Host "  APPLIED - $($changes.Count) record(s) rewritten." -ForegroundColor Green
exit 0
