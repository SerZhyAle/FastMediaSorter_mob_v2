#requires -Version 7.0
<#
.SYNOPSIS
    S3043 - refuse a release build while any Timber.d("Sxxxx: probe stands in a module's src/main.

.DESCRIPTION
    The phone release (/skill-release Step 12c) archives shipped tickets and deletes their probes
    in the same sweep. The watch release (/skill-release-wear) has no equivalent step, so a watch
    release ships every Timber.d("Sxxxx: probe in wear/src/main to wear:production.

    This gate refuses to pass while any probe of the form Timber.d("Sxxxx: ..") exists in the named
    module's src/main tree, regardless of ticket status. Unlike assert-no-ticket-logs.ps1, which
    allows probes whose ticket is in BlockNeedUserTest (correct for the per-closure invariant),
    this gate treats ALL probes as release blockers: a release build must carry no probe code at all.

    Runs from /spec-prerelease-wear Step 0 only. The phone pre-release sweep is not affected: the
    phone release has its own Step 12c archive sweep that deletes probes after the build, and the
    phone release ships no watch build.

    Probe detection uses the shared blockneedusertest-probes.ps1 library (opener regex, probe-form
    regex, call-span reconstruction) so this gate's definition of "a probe" cannot drift from
    assert-no-ticket-logs.ps1 (S1621).

.PARAMETER Module
    The module whose src/main tree to scan. 'wear' is the intended target; 'app_v2' is accepted
    and always exits 0, because the phone release handles its own probes via Step 12c.

.PARAMETER Quiet
    Suppress the per-finding list; print only the expected/actual summary.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-no-release-probes.ps1 -Module wear

.NOTES
    Exit codes (S1070):
      0 - no probes found in the module's src/main.
      1 - one or more probes found; the release must not proceed.
      2 - cannot verify: the module directory is absent, or the shared harness library is unavailable.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,

    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# The phone release has its own archive mechanism (Step 12c) that deletes probes after the build.
# Running this gate against app_v2 would block the phone release for probes that Step 12c is
# designed to handle, so it is a deliberate no-op.
if ($Module -eq 'app_v2') {
    Write-Host "assert-no-release-probes: app_v2 skipped (phone release handles probes via Step 12c)."
    exit 0
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scanRoot = Join-Path $repoRoot "$Module/src/main"

if (-not (Test-Path -LiteralPath $scanRoot)) {
    Write-Error "assert-no-release-probes: module src/main not found at $scanRoot" -ErrorAction Continue
    exit 2
}

# S1621: dot-source the shared probe library so the opener, probe-form and call-span helpers are
# the same ones assert-no-ticket-logs.ps1 uses. Two independent probe definitions would let this
# gate pass what the tree gate fails (or vice versa).
try {
    . (Join-Path $PSScriptRoot 'lib/blockneedusertest-probes.ps1')
} catch {
    Write-Error "assert-no-release-probes: shared harness library unavailable - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

$openerRx = Get-TimberOpenerRegex
$probeRx = Get-TimberProbeFormRegex

$findings = [System.Collections.Generic.List[object]]::new()

$files = Get-ChildItem -LiteralPath $scanRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/](build|\.gradle|\.kotlin)[\\/]' }

foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file.FullName -Raw
    if ([string]::IsNullOrEmpty($content)) { continue }

    foreach ($m in $openerRx.Matches($content)) {
        $level = $m.Groups['level'].Value
        # Only Timber.d can carry a probe; Timber.i/w/e with a ticket id is a forbidden permanent
        # log, not a probe, and assert-no-ticket-logs.ps1 already gates that.
        if ($level -ne 'd') { continue }

        $openParenIdx = $m.Index + $m.Length - 1
        $span = (Get-SanitizedTimberCallSpan -Content $content -PrefixStart $m.Index -OpenParenIndex $openParenIdx).Span

        $pm = $probeRx.Match($span)
        if (-not $pm.Success) { continue }

        # Skip when the opener sits in a comment - an id there is not log text.
        $lineStart = $content.LastIndexOf("`n", $m.Index) + 1
        $lineText = $content.Substring($lineStart, $m.Index - $lineStart)
        if ($lineText.Contains('//')) { continue }
        $trimmed = $lineText.TrimStart()
        if ($trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')) { continue }

        $lineNo = ($content.Substring(0, $m.Index) -split "`n").Count
        $ticketId = 'S' + $pm.Groups['num'].Value
        $rel = $file.FullName.Substring($repoRoot.Length).TrimStart('\', '/')
        $lineEnd = $content.IndexOf("`n", $m.Index)
        if ($lineEnd -lt 0) { $lineEnd = $content.Length }
        $wholeLine = $content.Substring($lineStart, $lineEnd - $lineStart).TrimEnd("`r").Trim()

        $findings.Add([pscustomobject]@{
            File   = ($rel -replace '\\', '/')
            Line   = $lineNo
            Ticket = $ticketId
            Text   = $wholeLine
        })
    }
}

$actual = $findings.Count

if (-not $Quiet -and $actual -gt 0) {
    Write-Host "Release probes in $Module/src/main:`n"
    foreach ($f in ($findings | Sort-Object File, Line)) {
        Write-Host ("  {0}:{1}  [{2}]  {3}" -f $f.File, $f.Line, $f.Ticket, $f.Text)
    }
    Write-Host ''
    Write-Host "  Resolve the BlockNeedUserTest tickets (which deletes their probes on status exit),"
    Write-Host "  then re-run the pre-release sweep. A release build must carry no probe code."
}

Write-Host ("assert-no-release-probes: expected: 0 | actual: {0} release probe(s) in {1}/src/main" -f $actual, $Module)

if ($actual -gt 0) { exit 1 }
exit 0
