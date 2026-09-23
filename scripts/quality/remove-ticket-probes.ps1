#requires -Version 7.0
<#
.SYNOPSIS
    Remove exact temporary Timber probes for supplied archived ticket ids.
.DESCRIPTION
    The command is intentionally narrower than a general log rewriter: it removes only a
    `Timber.d("Sxxxx: ...")` call for an explicitly selected ticket id. It can
    resolve the selection from the archive journal and removes `import timber.log.Timber` only
    when the resulting file has no remaining Timber call. Every modified source file is backed up.

    A probe that was the whole body of an `.also {}` callback or of an `if`/`else` branch leaves
    that wrapper empty, which detekt reports as EmptyIfBlock/EmptyElseBlock (S3398). In a file it
    changed, the command therefore also removes an emptied `.also {}`, an empty `if` with no
    `else`, and an empty `else`, and turns `if (c) {} else { .. }` into `if (!(c)) { .. }`.
    It also removes a `LaunchedEffect(..) {}` or `SideEffect {}` left empty, and that effect's
    import once the file no longer calls it (S3401).
.NOTES
    Exit codes: 0 completed (including an idempotent no-op); 1 invalid input or write failure;
    3 refused - a named ticket is still in BlockNeedUserTest, so its probe is required (use -Force).
#>
[CmdletBinding()]
param(
    [string] $Id = '',
    [switch] $Archived,
    [switch] $Force,
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

# S2639: this remover named a ticket and never asked the catalog what that ticket's status was, so
# it would strip the probe of a ticket still parked in BlockNeedUserTest. That is the one direction
# CLAUDE.md section 2's invariant was never guarded in: Assert-ClosingGates checks the probe only on
# the transition INTO the status (and returns early when OldStatus equals NewStatus), so nothing
# re-checks afterwards and the loss surfaces only on a project-wide assert-fast-gates run. Measured
# 2026-09-06: three tickets - S2156, S2487, S2498 - sat in BlockNeedUserTest carrying no probe.
if (-not $Force) {
    $catalogPath = Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'
    if (Test-Path -LiteralPath $catalogPath) {
        $live = [System.Collections.Generic.List[string]]::new()
        foreach ($line in Get-Content -LiteralPath $catalogPath -Encoding utf8) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            try { $record = $line | ConvertFrom-Json } catch { continue }
            if ($record.status -eq 'BlockNeedUserTest' -and $ids.Contains([string] $record.id)) {
                $live.Add([string] $record.id)
            }
        }
        if ($live.Count -gt 0) {
            Write-Error ("remove-ticket-probes: refusing - still in BlockNeedUserTest: {0}. A probe must exist for as long as its ticket holds that status; move the ticket first, or pass -Force." -f ($live -join ', ')) -ErrorAction Continue
            exit 3
        }
    }
}

$probeStartPattern = [regex]'(?:timber\.log\.)?Timber\.d\(\s*"(?<id>S\d{4}):'
$timberCallPattern = [regex]'(?<![A-Za-z0-9_.])(?:timber\.log\.)?Timber\.'
$timberImportPattern = [regex]'(?m)^\s*import timber\.log\.Timber\r?\n'
# One key level of nested parentheses covers `LaunchedEffect(list.size)` and `LaunchedEffect(f(x))`.
$emptyEffectLine = '^[\t ]*(?:LaunchedEffect\((?:[^()\r\n]|\([^()\r\n]*\))*\)|SideEffect)[\t ]*\{\s*\}[\t ]*(?:\r?\n|$)'
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
        $start = $match.Index
        $length = $end - $match.Index + 1
        # S2925: a probe owns its line (Rule 2), so the line leaves with it. Cutting only the call
        # left an empty line in its place - measured on 2026-09-11 as 14 stray blank lines across
        # 16 files, two of them directly before a closing brace.
        if ($linePrefix.Trim() -eq '') {
            $lineEnd = $Content.IndexOf("`n", $end)
            $tailEnd = if ($lineEnd -lt 0) { $Content.Length } else { $lineEnd }
            if ($Content.Substring($end + 1, $tailEnd - $end - 1).Trim() -eq '') {
                $start = $lineStart
                $length = $(if ($lineEnd -lt 0) { $Content.Length } else { $lineEnd + 1 }) - $lineStart
            }
        }
        $spans.Add([pscustomobject]@{ Start = $start; Length = $length })
    }
    return $spans
}

# Probes live only in authored sources. Walking a module root also enumerates `build/`, whose
# generated files appear and vanish while a sibling build runs: the read then throws on a path the
# enumeration had just listed, so the suite reports a defect that is only the state of `build/`
# (S2295). Scoping to `src/` also removes any chance of rewriting generated output.
foreach ($sourceRoot in @('app_v2/src', 'wear/src')) {
    $root = Join-Path $repoRoot $sourceRoot
    if (-not (Test-Path -LiteralPath $root)) { continue }
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
        # A probe may be the sole statement in a branch. Keep the non-empty branch when the empty
        # arm has an `else`, and otherwise remove only the now-empty conditional wrapper.
        $after = [regex]::Replace(
            $after,
            '(?m)^(?<indent>[\t ]*)if\s*\((?<condition>[^\r\n]+)\)\s*\{\s*\}\s*else\s*\{',
            '${indent}if (!(${condition})) {'
        )
        $after = [regex]::Replace($after, '(?m)^[\t ]*if\s*\([^\r\n]+\)\s*\{\s*\}\r?\n?', '')
        $after = [regex]::Replace($after, '(?m)\}[\t ]*else\s*\{\s*\}', '}')
        # S3401: a probe that was the only statement of a Compose effect left `LaunchedEffect(k) {}`
        # behind - a coroutine launched on first composition that does nothing. Seven such shells sat
        # in wear on 2026-09-23 because detekt has no rule for an empty lambda argument. The
        # `empty-compose-effect` source gate now refuses one, so this exit path must not produce it.
        # A shell that opened its enclosing block also takes the blank line after it, or the block
        # would start with an empty line.
        $after = [regex]::Replace($after, "(?m)(?<=\{[\t ]*\r?\n)$emptyEffectLine(?:[\t ]*\r?\n)?", '')
        $after = [regex]::Replace($after, "(?m)$emptyEffectLine", '')
        foreach ($effect in @('LaunchedEffect', 'SideEffect')) {
            if (-not [regex]::IsMatch($after, "(?<![A-Za-z0-9_.])$effect\s*[({]")) {
                $after = [regex]::Replace($after, "(?m)^[\t ]*import androidx\.compose\.runtime\.$effect\r?\n", '')
            }
        }
        $after = [regex]::Replace($after, '(?m)[\t ]+(?=\r?$)', '')
        # S2925: collapse in the file's own newline style. A fixed CRLF here wrote two carriage
        # returns into an LF file on 2026-09-11 (WearSettingsStepperCell.kt).
        $eol = if ($before.Contains("`r`n")) { "`r`n" } else { "`n" }
        $after = [regex]::Replace($after, '(\r?\n){3,}', "$eol$eol")
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
