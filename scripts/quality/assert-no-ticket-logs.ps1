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
      3 - only the catalog-state half failed: some ticket is BlockNeedUserTest with no
          probe, and -ChangedFiles was supplied, so the caller asked to be judged on
          its own files. Distinct from 1 because the caller cannot fix it - the probe
          belongs at the entry of ANOTHER ticket's changed flow (S1912).

    Allowed-probe status is resolved against PLAN/spec-catalog.jsonl.

    Default mode reports findings and exits 0 (audit). With -Gate the script
    exits 1 when any forbidden occurrence remains (fail-closed hygiene gate).

.PARAMETER Gate
    Fail-closed: exit 1 if any forbidden permanent-log ticket id is found.

.PARAMETER Quiet
    Suppress the per-finding list; print only the expected/actual summary.

.PARAMETER ChangedFiles
    Repo-relative paths of the files the caller changed, comma-joined. Supplying it narrows
    the forbidden-log half to those files and downgrades the missing-probe half to exit 3,
    because that half reads catalog state and belongs to no file set. Omit it - as
    assert-fast-gates.ps1 and the release path do - and both halves stay project-wide and fatal.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1
    pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    # S1184/S1340: `pwsh -File` binds only the first element of a [string[]] and rejects the rest as
    # positional args, so callers comma-join and this splits it back.
    [string[]]$ChangedFiles
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

# S2324: the probe form, the multi-line call reconstruction and the baseline parse moved to
# scripts/quality/lib/blockneedusertest-probes.ps1 so that check-probe-present.ps1 - which
# refuses a transition INTO BlockNeedUserTest - decides "carries a probe" by the same code this
# gate uses. Two independent implementations of that sentence would let the closing gate refuse
# a ticket this gate passes (S1621).
. (Join-Path $PSScriptRoot 'lib/blockneedusertest-probes.ps1')

# Opener of a Timber log call. The call may span several physical lines, so the
# whole argument text is reconstructed from the source before scanning - a per-line
# match would miss `Timber.d(\n  "Sxxxx: ..")` (S0948).
$openerRx = Get-TimberOpenerRegex
# Freestanding ticket id in the reconstructed argument text. The id must not be
# part of a longer identifier (class names such as MigrateS0059UseCase or
# S0200AuthStateWipe legitimately embed an id and are NOT provenance tags) -
# hence the surrounding non-word boundaries. Local to this gate: it is how a FORBIDDEN
# id is spotted, which is this gate's own half of the invariant.
$idRx = [regex]'(?<![A-Za-z0-9])S(?<num>\d{4})(?![0-9A-Za-z])'
# Probe form: Timber.d("Sxxxx: ..) - the string may sit on a later line, so the
# span is matched from its start and \s spans newlines.
$probeRx = Get-TimberProbeFormRegex

$findings = [System.Collections.Generic.List[object]]::new()
# Ids for which a probe call actually exists in source, whatever its status. A stale probe counts
# here too: the tag is present, and its own finding above already reports the status mismatch
# (S1290). Populated from the probe match rather than the generic id match, so only a real
# Timber.d("Sxxxx: opener qualifies.
$probeIds = [System.Collections.Generic.HashSet[string]]::new()

foreach ($root in $scanRoots) {
    $files = Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }
    foreach ($file in $files) {
        $content = Get-Content -LiteralPath $file.FullName -Raw
        if ([string]::IsNullOrEmpty($content)) { continue }

        foreach ($m in $openerRx.Matches($content)) {
            $openParenIdx = $m.Index + $m.Length - 1
            $span = (Get-SanitizedTimberCallSpan -Content $content -PrefixStart $m.Index -OpenParenIndex $openParenIdx).Span

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
                if ($pm.Success) {
                    [void]$probeIds.Add('S' + $pm.Groups['num'].Value)
                    if ($blockNeedUserTest.Contains('S' + $pm.Groups['num'].Value)) {
                        $allowed = $true
                    }
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

# S1912: the two halves of this gate have different natures. A forbidden log id is a property of a
# file's CONTENT, so a caller that names its changed set can be judged on that set alone. A missing
# probe is a property of CATALOG STATE - a ticket sits in BlockNeedUserTest without one - and belongs
# to no file set at all: the probe must sit at the entry of that ticket's changed flow, which only its
# author knows. Judging both project-wide meant one session's minutes-long window between the status
# flip and the probe blocked every other session in the tree from closing (S1889, S1895).
$scopeSet = @($ChangedFiles |
    ForEach-Object { $_ -split ',' } |
    ForEach-Object { $_.Trim() -replace '\\', '/' } |
    Where-Object { $_ })
$scoped = $scopeSet.Count -gt 0
$outOfScope = @()
if ($scoped) {
    $outOfScope = @($findings | Where-Object { $_.File -notin $scopeSet })
    $findings = [System.Collections.Generic.List[object]]@($findings | Where-Object { $_.File -in $scopeSet })
    $actual = $findings.Count
}

# S1290 - the other direction of the same invariant. The gate already holds both sides in memory
# at this point, so the check costs neither an extra catalogue read nor a second source walk.
# Exceptions are an allow-list rather than a count: measured 2026-08-14, every real gap had a
# legitimate named reason (the ticket changes tooling or documentation, so a probe has nowhere to
# live), and a bare counter would have recorded that as anonymous debt.
$baselineFile = Get-ProbeBaselinePath -RepoRoot $repoRoot
$excused = Get-ExcusedProbeTickets -BaselinePath $baselineFile

$missingProbe = @($blockNeedUserTest | Where-Object { -not $probeIds.Contains($_) -and -not $excused.Contains($_) } | Sort-Object)

# S2299: a forbidden id whose own ticket sits in BlockNeedUserTest with no probe in source is almost
# always a probe typed at the wrong level - the author wanted a probe, the surrounding call was
# naturally informational, so they reached for Timber.i. The gate already held both facts and printed
# them as two unrelated lines (a forbidden id up here, a missing-probe entry below), which is how the
# Migration53To54 line survived weeks of runs unfixed. Both sets are in memory at this point, so
# naming the connection costs neither an extra catalogue read nor a second source walk.
foreach ($finding in (@($findings) + @($outOfScope))) {
    if ($blockNeedUserTest.Contains($finding.Ticket) -and -not $probeIds.Contains($finding.Ticket)) {
        $finding.Reason = '{0} - looks like a probe typed at the wrong level; the probe form is Timber.d("{1}: ..")' -f $finding.Reason, $finding.Ticket
    }
}

if (-not $Quiet -and $actual -gt 0) {
    Write-Host "Forbidden permanent-log ticket ids:`n"
    foreach ($f in ($findings | Sort-Object File, Line)) {
        Write-Host ("  {0}:{1}  [{2}]  {3}  - {4}" -f $f.File, $f.Line, $f.Ticket, $f.Level, $f.Reason)
    }
    Write-Host ''
}

if (-not $Quiet -and $scoped -and $outOfScope.Count -gt 0) {
    # Reported, never hidden: a finding outside the changed set is still real, it just is not this
    # caller's to fix. Silently dropping it would make the scoped run read as "the tree is clean".
    Write-Host ("Outside the changed set - reported, not charged to this run ({0}):`n" -f $outOfScope.Count)
    foreach ($f in ($outOfScope | Sort-Object File, Line)) {
        Write-Host ("  {0}:{1}  [{2}]  {3}" -f $f.File, $f.Line, $f.Ticket, $f.Reason)
    }
    Write-Host ''
}

if (-not $Quiet -and $missingProbe.Count -gt 0) {
    Write-Host "BlockNeedUserTest tickets with no probe in source:`n"
    foreach ($id in $missingProbe) {
        Write-Host ("  {0}  - status BlockNeedUserTest but no Timber.d(`"{0}: ..`") in app_v2/src or wear/src" -f $id)
    }
    Write-Host "  Add the probe, or excuse the ticket with a reason in scripts/quality/blockneedusertest-probe-baseline.txt`n"
}

Write-Host ("assert-no-ticket-logs: expected: 0 | actual: {0} forbidden log id(s), {1} missing probe(s)  (BlockNeedUserTest: {2}, probes in source: {3}, excused: {4})" -f
    $actual, $missingProbe.Count, $blockNeedUserTest.Count, $probeIds.Count, $excused.Count)

if ($Gate -and $actual -gt 0) { exit 1 }
# S1912: a missing probe is fatal on a project-wide run - the release path and assert-fast-gates.ps1
# both take that branch - but exits 3 for a caller that named its changed set, so post-change.ps1 can
# report it without charging one session for another session's half-finished ticket.
if ($Gate -and $missingProbe.Count -gt 0) {
    # S2075: the listing above is gated on -not $Quiet, so a -Quiet -Gate caller used to hit this exit
    # with nothing printed at all. This line always fires, independent of -Quiet.
    Write-Error ("assert-no-ticket-logs: {0} BlockNeedUserTest ticket(s) missing a probe in source - {1}" -f $missingProbe.Count, ($missingProbe -join ', ')) -ErrorAction Continue
    if ($scoped) { exit 3 } else { exit 1 }
}
exit 0
