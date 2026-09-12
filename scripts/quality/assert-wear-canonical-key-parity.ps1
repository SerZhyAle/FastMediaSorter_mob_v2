#requires -Version 7.0
<#
.SYNOPSIS
    S2579: fails when a watch mini-program's canonicalKey is neither a phone route key nor a
    declared watch-only program.

.DESCRIPTION
    WearAppId declares canonicalKey to be "the phone's route key for the same program, not a name
    invented here" - the identity that lets the watch list be absorbed into the phone's program
    registry later without renaming a key already saved on a device. Nothing compared the two
    literal sets: WearAppCatalogTest checks the watch against itself (route == canonicalKey), and
    both of those literals are written by hand in one module. So VOICE_RECORDER carried
    "voice_recorder" while the same program is addressed by the phone as "quick_voice", and the
    divergence stood while the enum grew from five entries to eight.

    The rule this gate judges is TWO-PART, not a strict subset. A program of the watch list may
    legitimately have no phone counterpart - motion_monitor and body_sensor read sensors the phone
    has no program for - and a gate that refuses a legitimate case gets switched off (ADR-1). So a
    canonicalKey passes if it is spelled exactly like one of InternalRouteCatalog's KEY_* values,
    or if it is listed in the watch-only baseline beside the reason it has no counterpart.

    Checks:
      1. Every WearAppId canonicalKey is a phone route key or a baseline row.
      2. Every baseline row names a canonicalKey that still exists in the enum - a row left behind
         after its program was renamed or removed excuses nothing and hides the next divergence.
      3. A baseline row that has since gained a phone counterpart is reported, so the exception
         list shrinks by itself rather than by someone remembering to look.

    Rule 33 class, stated at birth: PER-TICKET. Its subject is a ticket editing one of the two
    enums, not the state of a release: only the author of a new entry knows whether the program is
    meant to exist on the watch alone, and the answer is cheapest at the moment the entry is
    written.

    S2824 kept that class and narrowed what it charges, for the reason recorded in
    lib/fixed-input-scope.ps1: per-ticket placement means the AUTHOR pays, and a gate reading three
    named files while judging any changed set billed whichever session closed first. With
    -ChangedFiles a divergence between files the caller never opened is reported and not charged.

.PARAMETER Gate
    Fail-closed: exit 1 when a divergence is found and chargeable.

.PARAMETER Quiet
    Suppress the PASS line. Findings and the failure verdict are still printed.

.PARAMETER ChangedFiles
    Repo-relative paths of the files the caller changed, comma-joined. Supplying it lets the gate
    decline to charge a divergence when neither enum nor the baseline is among them. Omit it - as
    assert-fast-gates.ps1 and the release path do - and every divergence stays fatal.

.NOTES
    Exit codes:
      0 - the rule holds; or a divergence was reported without -Gate, matching the advisory shape
          of the sibling wear parity gates in assert-fast-gates.ps1.
      1 - a divergence was found and -Gate was passed.
      2 - could not verify: a source file is missing, or one of the two literal sets parsed to
          zero entries. A caller must tell this from 1 - "found a defect" and "did not look" are
          different answers, and an empty parse would otherwise report the silent PASS this script
          exists to prevent.
      3 - S2824: a divergence was found, but no file this gate declares as an input is in
          -ChangedFiles, so it is not attributable to this run. The findings are printed. Distinct
          from 1 because the caller cannot fix it and from 0 because something IS wrong in the tree.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    # S1184/S1340: `pwsh -File` binds only the first element of a [string[]] and rejects the rest as
    # positional args, so callers comma-join and Expand-ChangedFiles splits it back.
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# S2824: the chargeability test, shared with the other two fixed-input gates.
. (Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1')

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$watchEnumPath = Join-Path $root 'wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearApp.kt'
$phoneCatalogPath = Join-Path $root 'app_v2/src/main/java/com/sza/fastmediasorter/core/panel/InternalRouteCatalog.kt'
$baselinePath = Join-Path $PSScriptRoot 'wear-canonical-key-watch-only-baseline.txt'

foreach ($required in @($watchEnumPath, $phoneCatalogPath, $baselinePath)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Error "assert-wear-canonical-key-parity: could not verify - missing $required" -ErrorAction Continue
        exit 2
    }
}

$watchSource = Get-Content -LiteralPath $watchEnumPath -Raw
$phoneSource = Get-Content -LiteralPath $phoneCatalogPath -Raw

# The enum body only, so a key quoted in the class KDoc above it is not read as a declaration.
$enumBody = [regex]::Match($watchSource, '(?s)enum\s+class\s+WearAppId\s*\([^)]*\)\s*\{(?<body>.*)')
if (-not $enumBody.Success) {
    Write-Error 'assert-wear-canonical-key-parity: could not verify - WearAppId enum body not found.' -ErrorAction Continue
    exit 2
}

$watchKeys = [ordered]@{}
foreach ($m in [regex]::Matches($enumBody.Groups['body'].Value, '(?m)^\s*(?<id>[A-Z][A-Z0-9_]*)\s*\(\s*"(?<key>[^"]+)"\s*\)')) {
    $watchKeys[$m.Groups['key'].Value] = $m.Groups['id'].Value
}

$phoneKeys = @([regex]::Matches($phoneSource, 'const\s+val\s+KEY_[A-Z0-9_]+\s*=\s*"(?<key>[^"]+)"') |
    ForEach-Object { $_.Groups['key'].Value })

if (@($watchKeys.Keys).Count -eq 0 -or $phoneKeys.Count -eq 0) {
    Write-Error ('assert-wear-canonical-key-parity: could not verify - parsed ' +
        "$(@($watchKeys.Keys).Count) watch key(s) and $($phoneKeys.Count) phone key(s).") -ErrorAction Continue
    exit 2
}

# One key per line, everything from '#' onward being the reason the row exists.
$baselineKeys = @()
foreach ($line in (Get-Content -LiteralPath $baselinePath)) {
    $token = ($line -split '#', 2)[0].Trim()
    if ($token) { $baselineKeys += $token }
}

$findings = @()

foreach ($key in $watchKeys.Keys) {
    if ($key -in $phoneKeys) { continue }
    if ($key -in $baselineKeys) { continue }
    $findings += ("S2579: WearAppId.$($watchKeys[$key]) carries canonicalKey '$key', which is no " +
        'InternalRouteCatalog KEY_* value. Either spell it exactly as the phone key for the same ' +
        "program, or add '$key' to wear-canonical-key-watch-only-baseline.txt with the reason it " +
        'has no phone counterpart.')
}

foreach ($key in $baselineKeys) {
    if ($key -notin $watchKeys.Keys) {
        $findings += "S2579: baseline row '$key' names no WearAppId canonicalKey - remove the row, it excuses nothing."
    } elseif ($key -in $phoneKeys) {
        $findings += "S2579: baseline row '$key' now has a phone counterpart - remove the row, the exception is spent."
    }
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host ("assert-wear-canonical-key-parity: PASS - $(@($watchKeys.Keys).Count) watch program key(s) " +
            "against $($phoneKeys.Count) phone route key(s), $($baselineKeys.Count) declared watch-only.")
    }
    exit 0
}

# S2824: the baseline counts as a declared input - editing the exception list is editing this rule.
if (-not (Test-FixedInputsChargeable -ChangedFiles $ChangedFiles `
            -InputPaths @($watchEnumPath, $phoneCatalogPath, $baselinePath))) {
    Write-NotChargedVerdict -GateName 'assert-wear-canonical-key-parity' -Findings $findings
    exit 3
}

Write-Error ("assert-wear-canonical-key-parity: FAIL - " + $findings.Count + " divergence(s):`n" +
    (($findings | Sort-Object -Unique) -join "`n")) -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
