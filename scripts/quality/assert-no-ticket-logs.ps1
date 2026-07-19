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

    Exit codes (S1070):
      0 - clean (or audit mode).
      1 - substantive failure: a forbidden permanent-log ticket id remains.
      2 - the gate itself cannot run (spec-catalog.jsonl missing - without it no
          probe's status can be resolved). Distinct from 1 on purpose.

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
    Write-Error "spec-catalog.jsonl not found at $catalog" -ErrorAction Continue
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

# Opener of a Timber log call. The call may span several physical lines, so the
# whole argument text is reconstructed from the source (see Get-CallEnd) before
# scanning - a per-line match would miss `Timber.d(\n  "Sxxxx: ..")` (S0948).
$openerRx = [regex]'Timber\.(?<level>[iwed])\('
# Freestanding ticket id in the reconstructed argument text. The id must not be
# part of a longer identifier (class names such as MigrateS0059UseCase or
# S0200AuthStateWipe legitimately embed an id and are NOT provenance tags) -
# hence the surrounding non-word boundaries.
$idRx = [regex]'(?<![A-Za-z0-9])S(?<num>\d{4})(?![0-9A-Za-z])'
# Probe form: Timber.d("Sxxxx: ..) - the string may sit on a later line, so the
# span is matched from its start and \s spans newlines.
$probeRx = [regex]'^Timber\.d\(\s*"S(?<num>\d{4}):'

# Reconstruct a Timber call from its 'Timber.<level>' start ($prefixStart) through
# the ')' that matches its opening '(' ($openParenIdx), tracking string and comment
# state. Returns @{ End = <index of that ')'>; Span = <call text with // and /* */
# comments blanked to spaces but string literals kept verbatim> }. Comments are
# blanked so a `// Sxxxx` rationale note sitting between call arguments is not
# mistaken for log text; string literals stay intact because that is exactly where a
# forbidden id lives. Parens inside strings/comments do not skew the depth count.
# Kotlin raw-triple-quote strings and char literals holding a quote are rare in
# Timber args and out of scope here.
function Get-SanitizedCall([string] $content, [int] $prefixStart, [int] $openParenIdx) {
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.Append($content.Substring($prefixStart, $openParenIdx - $prefixStart))
    $depth = 0
    $inStr = $false
    $inLine = $false
    $inBlock = $false
    $i = $openParenIdx
    $len = $content.Length
    $end = -1
    while ($i -lt $len) {
        $c = $content[$i]
        if ($inLine) {
            if ($c -eq "`n") { $inLine = $false; [void]$sb.Append($c) } else { [void]$sb.Append(' ') }
            $i++; continue
        }
        if ($inBlock) {
            if ($c -eq '*' -and ($i + 1) -lt $len -and $content[$i + 1] -eq '/') {
                $inBlock = $false; [void]$sb.Append('  '); $i += 2; continue
            }
            [void]$sb.Append($(if ($c -eq "`n") { $c } else { ' ' })); $i++; continue
        }
        if ($inStr) {
            [void]$sb.Append($c)
            if ($c -eq '\' -and ($i + 1) -lt $len) { [void]$sb.Append($content[$i + 1]); $i += 2; continue }
            if ($c -eq '"') { $inStr = $false }
            $i++; continue
        }
        if ($c -eq '"') { $inStr = $true; [void]$sb.Append($c); $i++; continue }
        if ($c -eq '/' -and ($i + 1) -lt $len -and $content[$i + 1] -eq '/') { $inLine = $true; [void]$sb.Append('  '); $i += 2; continue }
        if ($c -eq '/' -and ($i + 1) -lt $len -and $content[$i + 1] -eq '*') { $inBlock = $true; [void]$sb.Append('  '); $i += 2; continue }
        [void]$sb.Append($c)
        if ($c -eq '(') { $depth++ }
        elseif ($c -eq ')') { $depth--; if ($depth -eq 0) { $end = $i; break } }
        $i++
    }
    if ($end -lt 0) { $end = [Math]::Min($len - 1, $openParenIdx + 2000) }
    return @{ End = $end; Span = $sb.ToString() }
}

$findings = [System.Collections.Generic.List[object]]::new()

foreach ($root in $scanRoots) {
    $files = Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
    foreach ($file in $files) {
        $content = Get-Content -LiteralPath $file.FullName -Raw
        if ([string]::IsNullOrEmpty($content)) { continue }

        foreach ($m in $openerRx.Matches($content)) {
            $openParenIdx = $m.Index + $m.Length - 1
            $span = (Get-SanitizedCall $content $m.Index $openParenIdx).Span

            $idm = $idRx.Match($span)
            if (-not $idm.Success) { continue }

            # Opener line number + its physical line text (for comment filtering).
            $lineNo = ($content.Substring(0, $m.Index) -split "`n").Count
            $lineStart = $content.LastIndexOf("`n", $m.Index) + 1
            $lineText = $content.Substring($lineStart, $m.Index - $lineStart)
            # Skip when the opener sits in a comment - an id there is not log text.
            if ($lineText.Contains('//')) { continue }
            $trimmed = $lineText.TrimStart()
            if ($trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')) { continue }

            $level = $m.Groups['level'].Value
            $id = 'S' + $idm.Groups['num'].Value

            $allowed = $false
            if ($level -eq 'd') {
                $pm = $probeRx.Match($span)
                if ($pm.Success -and $blockNeedUserTest.Contains('S' + $pm.Groups['num'].Value)) {
                    $allowed = $true
                }
            }

            if (-not $allowed) {
                $reason = if ($level -ne 'd') {
                    "ticket id in permanent Timber.$level"
                } elseif ($probeRx.IsMatch($span)) {
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
                    Text   = (($span -split "`n")[0]).Trim()
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
