#requires -Version 7.0
<#
.SYNOPSIS
    Audit permanent Timber logs for embedded Sxxxx ticket ids.

.DESCRIPTION
    Scans app_v2 and wear Kotlin sources for `Sxxxx` ticket ids inside log
    messages. Per CLAUDE.md "Debug Verification Tags", a ticket id may appear in
    log text ONLY as a temporary probe of the exact form Timber.d("Sxxxx: ..")
    whose ticket is currently in status BlockNeedUserTest. Every other occurrence
    is a forbidden permanent-log ticket id:
      - any Sxxxx inside Timber.i / Timber.w / Timber.e;
      - any Sxxxx inside Timber.d that is not the "Sxxxx:" probe prefix;
      - a "Sxxxx:" probe whose ticket is NOT currently BlockNeedUserTest (stale).

    Allowed-probe status is resolved against PLAN/spec-catalog.jsonl.

    Default mode reports findings and exits 0 (audit). With -Gate the script
    exits 1 when any forbidden occurrence remains (fail-closed hygiene gate).

.PARAMETER Gate
    Fail-closed: exit 1 if any forbidden permanent-log ticket id is found.

.PARAMETER Quiet
    Suppress the per-finding list; print only the expected/actual summary.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1
    pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Repo root = two levels up from scripts/quality/
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$catalog = Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'

if (-not (Test-Path $catalog)) {
    Write-Error "spec-catalog.jsonl not found at $catalog"
    exit 2
}

# Build the set of tickets currently in BlockNeedUserTest (allowed-probe owners).
$blockNeedUserTest = [System.Collections.Generic.HashSet[string]]::new()
foreach ($line in Get-Content -LiteralPath $catalog) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try { $rec = $line | ConvertFrom-Json } catch { continue }
    if ($rec.status -eq 'BlockNeedUserTest') { [void]$blockNeedUserTest.Add($rec.id) }
}

$scanRoots = @(
    (Join-Path $repoRoot 'app_v2'),
    (Join-Path $repoRoot 'wear')
) | Where-Object { Test-Path $_ }

# Match a Timber call carrying a FREESTANDING ticket id in its argument text.
# The id must not be part of a longer identifier (class names such as
# MigrateS0059UseCase or S0200AuthStateWipe legitimately embed an id and are NOT
# provenance tags) - hence the surrounding non-word boundaries.
#   group 'level' = i|w|e|d ; group 'num' = first freestanding Sxxxx in the call.
$timberRx = [regex]'Timber\.(?<level>[iwed])\((?<args>[^\r\n]*?(?<![A-Za-z0-9])S(?<num>\d{4})(?![0-9A-Za-z])[^\r\n]*)'
# Probe form: Timber.d("Sxxxx: ..)
$probeRx = [regex]'Timber\.d\(\s*"S(?<num>\d{4}):'

$findings = [System.Collections.Generic.List[object]]::new()

foreach ($root in $scanRoots) {
    $files = Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
    foreach ($file in $files) {
        $lineNo = 0
        foreach ($text in Get-Content -LiteralPath $file.FullName) {
            $lineNo++
            $m = $timberRx.Match($text)
            if (-not $m.Success) { continue }

            # Skip comment lines - a ticket id in a comment is not log text.
            $trimmed = $text.TrimStart()
            if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')) { continue }
            $slashIdx = $text.IndexOf('//')
            if ($slashIdx -ge 0 -and $slashIdx -lt $m.Index) { continue }

            $level = $m.Groups['level'].Value
            $id = 'S' + $m.Groups['num'].Value

            $allowed = $false
            if ($level -eq 'd') {
                $pm = $probeRx.Match($text)
                if ($pm.Success) {
                    $probeId = 'S' + $pm.Groups['num'].Value
                    if ($blockNeedUserTest.Contains($probeId)) { $allowed = $true }
                }
            }

            if (-not $allowed) {
                $reason = if ($level -ne 'd') {
                    "ticket id in permanent Timber.$level"
                } elseif ($probeRx.IsMatch($text)) {
                    "stale probe (ticket not BlockNeedUserTest)"
                } else {
                    "ticket id in long-lived Timber.d (not a probe)"
                }
                $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/')
                $findings.Add([pscustomobject]@{
                    File   = ($rel -replace '\\', '/')
                    Line   = $lineNo
                    Level  = "Timber.$level"
                    Ticket = $id
                    Reason = $reason
                    Text   = $text.Trim()
                })
            }
        }
    }
}

$actual = $findings.Count

if (-not $Quiet -and $actual -gt 0) {
    Write-Host "Forbidden permanent-log ticket ids:`n"
    foreach ($f in ($findings | Sort-Object File, Line)) {
        Write-Host ("  {0}:{1}  [{2}]  {3}  - {4}" -f $f.File, $f.Line, $f.Ticket, $f.Level, $f.Reason)
    }
    Write-Host ''
}

Write-Host ("assert-no-ticket-logs: expected: 0 | actual: {0}  (allowed BlockNeedUserTest probes: {1})" -f $actual, $blockNeedUserTest.Count)

if ($Gate -and $actual -gt 0) { exit 1 }
exit 0
