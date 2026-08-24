#requires -Version 7.0
<#
.SYNOPSIS
    Remove exact temporary Timber probes for supplied archived ticket ids.
.DESCRIPTION
    The command is intentionally narrower than a general log rewriter: it removes only a
    `Timber.d("Sxxxx: ...")` call for an explicitly selected ticket id. It can
    resolve the selection from the archive journal and removes `import timber.log.Timber` only
    when the resulting file has no remaining Timber call. Every modified source file is backed up.
.NOTES
    Exit codes: 0 completed (including an idempotent no-op); 1 invalid input or write failure.
#>
[CmdletBinding()]
param(
    [string] $Id = '',
    [switch] $Archived,
    [string] $BackupDirectory = 'temp/scratch',
    [switch] $WhatIf
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

trap {
    Write-Error $_ -ErrorAction Continue
    exit 1
}

if ($Archived -and -not [string]::IsNullOrWhiteSpace($Id)) {
    throw 'Use either -Id or -Archived, not both.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ids = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
if ($Archived) {
    $archivePath = Join-Path $repoRoot 'PLAN/spec-catalog-archive.jsonl'
    if (-not (Test-Path -LiteralPath $archivePath)) { throw "Archive journal not found: $archivePath" }
    foreach ($line in Get-Content -LiteralPath $archivePath -Encoding utf8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $record = $line | ConvertFrom-Json
        if ($record.status -eq 'Archived') { [void] $ids.Add([string] $record.id) }
    }
} else {
    foreach ($candidate in ($Id -split ',')) {
        $ticketId = $candidate.Trim()
        if ($ticketId -eq '') { continue }
        if ($ticketId -notmatch '^S\d{4}$') { throw "Invalid ticket id '$ticketId'." }
        [void] $ids.Add($ticketId)
    }
}
if ($ids.Count -eq 0) { throw 'No ticket ids were resolved.' }

$probeStartPattern = [regex]'(?:timber\.log\.)?Timber\.d\(\s*"(?<id>S\d{4}):'
$timberCallPattern = [regex]'(?<![A-Za-z0-9_.])(?:timber\.log\.)?Timber\.'
$timberImportPattern = [regex]'(?m)^\s*import timber\.log\.Timber\r?\n'
$changed = [System.Collections.Generic.List[string]]::new()
$removed = 0

function Find-ProbeSpans {
    param([Parameter(Mandatory)][string] $Content)

    $spans = [System.Collections.Generic.List[object]]::new()
    foreach ($match in $probeStartPattern.Matches($Content)) {
        if (-not $ids.Contains($match.Groups['id'].Value)) { continue }
        $lineStart = $Content.LastIndexOf("`n", $match.Index) + 1
        $linePrefix = $Content.Substring($lineStart, $match.Index - $lineStart)
        if ($linePrefix.Contains('//') -or $linePrefix.TrimStart().StartsWith('*')) { continue }
        $open = $Content.IndexOf('(', $match.Index)
        $depth = 0
        $inString = $false
        $end = -1
        for ($index = $open; $index -lt $Content.Length; $index++) {
            $char = $Content[$index]
            if ($inString) {
                if ($char -eq '\\') { $index++; continue }
                if ($char -eq '"') { $inString = $false }
                continue
            }
            if ($char -eq '"') { $inString = $true; continue }
            if ($char -eq '(') { $depth++; continue }
            if ($char -eq ')') {
                $depth--
                if ($depth -eq 0) { $end = $index; break }
            }
        }
        if ($end -lt 0) { throw "Unterminated Timber probe in $($match.Groups['id'].Value)." }
        $spans.Add([pscustomobject]@{ Start = $match.Index; Length = $end - $match.Index + 1 })
    }
    return $spans
}

foreach ($sourceRoot in @('app_v2', 'wear')) {
    $root = Join-Path $repoRoot $sourceRoot
    foreach ($file in Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt') {
        $before = [System.IO.File]::ReadAllText($file.FullName)
        $spans = @(Find-ProbeSpans -Content $before | Sort-Object Start -Descending)
        $fileRemoved = $spans.Count
        $after = $before
        foreach ($span in $spans) { $after = $after.Remove($span.Start, $span.Length) }
        if ($fileRemoved -eq 0) { continue }
        # A probe can be the body of an `also` callback or occupy its own indented line. Keep the
        # surrounding expression valid and avoid introducing whitespace-only or duplicate blank lines.
        $after = [regex]::Replace($after, '\.also\s*\{\s*\}', '')
        $after = [regex]::Replace($after, '(?m)[\t ]+(?=\r?$)', '')
        $after = [regex]::Replace($after, '(\r?\n){3,}', "`r`n`r`n")
        if (-not $timberCallPattern.IsMatch($after)) {
            $after = $timberImportPattern.Replace($after, '')
        }
        $relative = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'
        if (-not $WhatIf) {
            $backupRoot = Join-Path $repoRoot $BackupDirectory
            New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
            $backupName = ($relative -replace '[\\/]', '__') + '.backup'
            Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $backupRoot $backupName) -Force
            [System.IO.File]::WriteAllText($file.FullName, $after, [System.Text.UTF8Encoding]::new($false))
        }
        $changed.Add($relative)
        $removed += $fileRemoved
    }
}

$mode = if ($WhatIf) { 'what-if' } else { 'applied' }
Write-Output "remove-ticket-probes: $mode; ids=$($ids.Count); probes=$removed; files=$($changed.Count)"
foreach ($path in $changed) { Write-Output "  $path" }
exit 0
