#requires -Version 7.0
<#
.SYNOPSIS
    S1674: prevent R8 from renaming enum members persisted as durable strings.

.DESCRIPTION
    Room converters, DataStore and SharedPreferences retain values across application updates.
    An enum written with `name` and restored with `valueOf` needs a base release rule preserving
    its fields, otherwise a later R8 mapping can make the persisted value unreadable.

    With -Mapping, the check goes past the rule text and reads a real R8 mapping: rule wording that
    looks right still proves nothing about what the optimizer did, so a minified artifact is the only
    evidence that a persisted member name survived.

.EXIT CODES
    0 - every durable enum-name path has a matching base rule (and, with -Mapping, kept its name).
    1 - a durable enum-name path is unpinned, or the mapping shows a renamed member.
    2 - source, the base ProGuard file or the named mapping could not be inspected.
#>
[CmdletBinding()]
param(
    [string]$Mapping,
    [switch]$Gate,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$sourceRoot = Join-Path $repoRoot 'app_v2/src/main/java'
$proguardPath = Join-Path $repoRoot 'app_v2/proguard-rules.pro'

if (-not (Test-Path -LiteralPath $sourceRoot) -or -not (Test-Path -LiteralPath $proguardPath)) {
    Write-Error 'assert-enum-persistence-contract: app source or base ProGuard rules are missing.' -ErrorAction Continue
    exit 2
}

$enumDeclarations = @{}
$sourceFiles = @(Get-ChildItem -LiteralPath $sourceRoot -Filter '*.kt' -Recurse)
foreach ($file in $sourceFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    $packageMatch = [regex]::Match($text, '(?m)^package\s+([\w.]+)')
    if (-not $packageMatch.Success) { continue }
    foreach ($match in [regex]::Matches($text, '(?m)^\s*enum\s+class\s+(\w+)')) {
        $enumDeclarations[$match.Groups[1].Value] = "$($packageMatch.Groups[1].Value).$($match.Groups[1].Value)"
    }
}

$durableEnums = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($file in $sourceFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    $durableBoundary = $text -match '@TypeConverter|DataStore<Preferences>|SharedPreferences|getSharedPreferences'
    if (-not $durableBoundary) { continue }
    $hasEnumNameWrite = $text -match '\b\w+\.name\b'
    $hasEnumNameRead = $text -match '\b\w+\.valueOf\s*\('
    if (-not ($hasEnumNameWrite -or $hasEnumNameRead)) { continue }
    foreach ($match in [regex]::Matches($text, '\b(\w+)\.valueOf\s*\(')) {
        $name = $match.Groups[1].Value
        if ($enumDeclarations.ContainsKey($name)) { [void]$durableEnums.Add($enumDeclarations[$name]) }
    }
    foreach ($match in [regex]::Matches($text, '\b(\w+)\.name\b')) {
        $name = $match.Groups[1].Value
        if ($enumDeclarations.ContainsKey($name)) { [void]$durableEnums.Add($enumDeclarations[$name]) }
    }
}

$rules = Get-Content -LiteralPath $proguardPath -Raw
$missing = [System.Collections.Generic.List[string]]::new()
foreach ($enum in ($durableEnums | Sort-Object)) {
    $escaped = [regex]::Escape($enum)
    $rule = "(?s)-keepclassmembernames\s+enum\s+$escaped\s*\{\s*<fields>;\s*\}"
    if ($rules -notmatch $rule) { $missing.Add($enum) }
}

if ($durableEnums.Count -eq 0) {
    Write-Error 'assert-enum-persistence-contract: no durable enum-name path was discovered.' -ErrorAction Continue
    exit 2
}
if ($missing.Count -gt 0) {
    foreach ($enum in $missing) {
        Write-Error "assert-enum-persistence-contract: missing -keepclassmembernames enum rule for $enum." -ErrorAction Continue
    }
    exit 1
}
if (-not $Mapping) {
    if (-not ($Gate -or $Quiet)) {
        Write-Host "assert-enum-persistence-contract: PASS ($($durableEnums.Count) durable enum(s) pinned)."
    }
    exit 0
}

if (-not (Test-Path -LiteralPath $Mapping)) {
    Write-Error "assert-enum-persistence-contract: mapping file not found at $Mapping." -ErrorAction Continue
    exit 2
}

# An enum constant is a static field whose type is the enum itself, so its mapping line names the
# enum as the type and, when R8 left it alone, carries the same identifier on both sides of the arrow.
# Read in one streaming pass: a real standard-release mapping is ~170 MB, and loading it into an
# array costs minutes and gigabytes for a check that only ever needs the current line.
$targets = @{}
foreach ($enum in $durableEnums) { $targets[$enum] = $true }
$renamed = [System.Collections.Generic.List[string]]::new()
$inspected = 0
$currentEnum = $null

$reader = [System.IO.StreamReader]::new($Mapping)
try {
    while ($null -ne ($line = $reader.ReadLine())) {
        if ($line -notmatch '^\s') {
            $classLine = [regex]::Match($line, '^([\w.$]+)\s+->\s+')
            # Absent from the file means R8 removed the class outright - a reachability question,
            # not a rename, so it is not counted and not reported here.
            $currentEnum = if ($classLine.Success -and $targets.ContainsKey($classLine.Groups[1].Value)) {
                $inspected++
                $classLine.Groups[1].Value
            } else {
                $null
            }
            continue
        }
        if (-not $currentEnum) { continue }
        $member = [regex]::Match($line, '^\s+([\w.$]+)\s+(\w+)\s+->\s+(\w+)\s*$')
        if (-not $member.Success) { continue }
        if ($member.Groups[1].Value -ne $currentEnum) { continue }
        if ($member.Groups[2].Value -cne $member.Groups[3].Value) {
            $renamed.Add("$currentEnum.$($member.Groups[2].Value) -> $($member.Groups[3].Value)")
        }
    }
} finally {
    $reader.Dispose()
}

if ($renamed.Count -gt 0) {
    foreach ($entry in $renamed) {
        Write-Error "assert-enum-persistence-contract: persisted enum member renamed by R8 - $entry." -ErrorAction Continue
    }
    exit 1
}
if ($inspected -eq 0) {
    Write-Error "assert-enum-persistence-contract: no durable enum appears in $Mapping - nothing was proven." -ErrorAction Continue
    exit 2
}
if (-not ($Gate -or $Quiet)) {
    Write-Host "assert-enum-persistence-contract: PASS ($($durableEnums.Count) durable enum(s) pinned; $inspected verified against $Mapping)."
}
exit 0
