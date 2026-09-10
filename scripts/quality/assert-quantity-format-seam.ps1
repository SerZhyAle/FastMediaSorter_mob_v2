#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: a user-facing quantity is formatted by the seam, never by a call site of its own.

.DESCRIPTION
    S2795 introduced one place where a quantity with two scales becomes a string - QuantityFormatter
    on the phone, WearUnitDateTimeFormatter on the watch, both driven by the app's UnitSystem. The
    ticket's own strategic spec records why a convention cannot hold that line: there were about forty
    independent formatting call sites, and a rule asking each new one to route through the seam is
    unverifiable without re-reading all of them. So this gate refuses the reintroduction instead.

    Refused in a user-facing source file:
      - java.text.SimpleDateFormat / java.time.DateTimeFormatter.ofPattern
      - android.text.format.DateFormat.getTimeFormat / .getDateFormat / .is24HourFormat
      - java.text.DateFormat.getDateTimeInstance / .getDateInstance / .getTimeInstance
      - a layout declaring BOTH android:format12Hour and android:format24Hour on one view, which hands
        the 12/24-hour choice back to the DEVICE - the exact behaviour ADR-2 replaced
      - a resource value ending in a hardcoded speed or altitude unit (" km/h", " mph", " m", " ft")

    Not refused, and this is the whole reason the gate needs a baseline: an INTERNAL timestamp is a
    storage and parsing contract, not something a user reads. File names, log file names, log line
    stamps, EXIF input parsing and export formats are read back by the app, so a unit-system-dependent
    format would break sorting, uniqueness or parsing. Those files are listed in the baseline with the
    reason each one is there, and the seam's own implementation is listed too.

    Modes:
      (default)        Report every offender against the baseline.
      -Gate            Exit 1 on an offender outside the baseline.
      -ChangedFiles    Judge only these paths (per-ticket closure scoping).
      -List            Print every offending file:line.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - pass: no offender outside the baseline, or a report/list run.
      1 - fail: an offender outside the baseline.
      2 - could not verify: a source root named below is missing from the checkout.
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Report')][switch]$List,
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$baselinePath = Join-Path $PSScriptRoot 'quantity-format-seam-baseline.txt'

$sourceRoots = @(
    'app_v2/src/main',
    'app_v2/src/launcherEnabled',
    'wear/src/main'
)

# Anchored on the call, not on the type name: an import line or a KDoc mention of the same symbol is
# not a formatting decision, and flagging one would push a file into the baseline for a comment.
$kotlinPatterns = @(
    'SimpleDateFormat\s*\(',
    'DateTimeFormatter\.ofPattern\s*\(',
    'DateFormat\.getTimeFormat\s*\(',
    'DateFormat\.getDateFormat\s*\(',
    'DateFormat\.is24HourFormat\s*\(',
    'DateFormat\.getDateTimeInstance\s*\(',
    'DateFormat\.getDateInstance\s*\(',
    'DateFormat\.getTimeInstance\s*\('
)

$missingRoots = @($sourceRoots | Where-Object { -not (Test-Path (Join-Path $repoRoot $_)) })
if ($missingRoots.Count -gt 0) {
    Write-Host "assert-quantity-format-seam: cannot verify - missing source root(s): $($missingRoots -join ', ')"
    exit 2
}

$baseline = @()
if (Test-Path $baselinePath) {
    $baseline = @(Get-Content $baselinePath |
        Where-Object { $_.Trim() -and -not $_.TrimStart().StartsWith('#') } |
        ForEach-Object { ($_ -split '\s*\|\s*')[0].Trim() })
}

function Test-Baselined {
    param([string]$RelativePath)
    foreach ($entry in $baseline) {
        if ($RelativePath -eq $entry) { return $true }
    }
    return $false
}

$scopePaths = $null
if ($ChangedFiles) {
    $scopePaths = @($ChangedFiles | ForEach-Object { $_.Replace('\', '/').TrimStart('./') })
}

$findings = New-Object System.Collections.Generic.List[string]

function Add-Finding {
    param([string]$RelativePath, [int]$LineNumber, [string]$Detail)
    if (Test-Baselined -RelativePath $RelativePath) { return }
    if ($scopePaths -and ($scopePaths -notcontains $RelativePath)) { return }
    $findings.Add("${RelativePath}:${LineNumber} - $Detail")
}

$kotlinRegex = [regex]::new(($kotlinPatterns -join '|'))

foreach ($root in $sourceRoots) {
    $absoluteRoot = Join-Path $repoRoot $root
    foreach ($file in Get-ChildItem -Path $absoluteRoot -Filter '*.kt' -Recurse -File) {
        $relative = $file.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
        if (Test-Baselined -RelativePath $relative) { continue }
        $lineNumber = 0
        foreach ($line in Get-Content $file.FullName) {
            $lineNumber++
            $match = $kotlinRegex.Match($line)
            if ($match.Success) {
                Add-Finding -RelativePath $relative -LineNumber $lineNumber -Detail "formats past the seam: $($match.Value)"
            }
        }
    }

    foreach ($file in Get-ChildItem -Path $absoluteRoot -Filter '*.xml' -Recurse -File) {
        $relative = $file.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
        if (Test-Baselined -RelativePath $relative) { continue }
        $text = Get-Content -Raw $file.FullName
        if ($text -match 'format12Hour' -and $text -match 'format24Hour') {
            Add-Finding -RelativePath $relative -LineNumber 1 -Detail 'declares both format12Hour and format24Hour - the DEVICE decides the clock length'
        }
    }
}

# The unit belongs to the seam's lexemes, so a value string ending in one is a second place deciding it.
$stringsFiles = Get-ChildItem -Path (Join-Path $repoRoot 'app_v2/src/main/res') -Filter 'strings.xml' -Recurse -File
foreach ($file in $stringsFiles) {
    $relative = $file.FullName.Substring($repoRoot.Length + 1).Replace('\', '/')
    if (Test-Baselined -RelativePath $relative) { continue }
    $lineNumber = 0
    foreach ($line in Get-Content $file.FullName) {
        $lineNumber++
        if ($line -notmatch '<string\s+name="([^"]+)"') { continue }
        $key = $Matches[1]
        if ($key -like 'unit_*') { continue }
        if ($line -match '(%\d+\$[ds]|%s|%d)\s*(km/h|mph|ft)\s*<') {
            Add-Finding -RelativePath $relative -LineNumber $lineNumber -Detail "string '$key' hardcodes a unit the seam decides"
        }
    }
}

if ($List -or -not $Gate) {
    foreach ($finding in $findings) { Write-Host "  $finding" }
}

Write-Host "assert-quantity-format-seam: $($findings.Count) offender(s) outside the baseline of $($baseline.Count) entry(ies)."

if ($Gate -and $findings.Count -gt 0) {
    Write-Host 'assert-quantity-format-seam: FAIL - route the value through QuantityFormatter (phone) or'
    Write-Host '  WearUnitDateTimeFormatter (watch). If this really is an INTERNAL timestamp - a file name, a'
    Write-Host '  log stamp, an export field or a parsed input - add the file to'
    Write-Host "  $baselinePath with the reason, because changing one of those breaks parsing or uniqueness."
    exit 1
}

Write-Host 'assert-quantity-format-seam: PASS'
exit 0
