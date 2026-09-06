#requires -Version 7.0
<#
.SYNOPSIS
    S2608. Does the harness that actually RUNS match the canon checkout it was authored in?

.DESCRIPTION
    Rule 8 (S2402) makes the process harness consumed here and authored in the SZA canon, and every
    local path under scripts/ is a generated forwarder resolving a shipped copy. That leaves a gap
    nothing observes: a ticket in THIS repository can reach Verified on a mechanism that lives in
    another repository's working tree, and no check connects the two. The audit reads the spec, the
    spec names the canon file, the canon file carries the fix - and the copy the forwarder resolves
    may be a different file entirely.

    Measured 2026-09-05, which is why this exists rather than being proposed: S2500 (Verified
    2026-09-03) and S2578 (Verified 2026-09-05) both existed only as UNCOMMITTED modifications in
    the canon working tree plus one hand-patched plugin-cache version directory. Seventeen harness
    files differed between the two. The next `claude plugin update` writes a new version directory,
    the forwarder prefers it as the higher version, and every such fix disappears with no failure
    anywhere - the mechanism is simply gone and the tickets still read Verified.

    This does not judge WHICH side is right. A canon checkout ahead of the running copy means the
    fix has not been delivered; a running copy ahead of the canon means a copy was hand-patched and
    the canon is about to overwrite it. Both are worth seeing and neither is decidable from the file
    contents, so the report names the drift and the direction, and the reader decides.

    ADVISORY BY CONSTRUCTION (Rule 33). The subject is the machine's plugin deployment, not any
    changed file, so a ticket cannot be blamed for the drift and must not be blocked by it - that is
    exactly the "red for whichever session happened to run it, over debt belonging to other tickets"
    failure the probe-present gate records. It reports and returns 3; only a caller that has decided
    drift is fatal should treat that as one.

.PARAMETER Quiet
    Suppress the per-file lines and print only the verdict.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-harness-drift.ps1

.EXIT CODES
    0 - looked, and the running harness matches the canon checkout.
    2 - could not look: no resolvable harness, or no canon checkout on this machine. Distinct from 3
        because "did not look" and "found drift" are opposite answers, and a canon checkout is a
        machine-local path that is legitimately absent on any machine but the author's.
    3 - looked, and found drift. Advisory - see the note above.
#>
[CmdletBinding()]
param(
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path

# Both roots come from the resolvers the forwarders themselves use, never from a restatement of the
# search order: a second copy of that order would drift from the one that decides what actually
# runs, and this gate would then compare two files neither of which is the one being executed.
try {
    . (Join-Path $repoRoot 'scripts/utils/agent-lock.ps1')
    $runningHarness = Split-Path -Parent (Split-Path -Parent (Get-SzaHarnessScript 'locks/ticket-lease.ps1'))
}
catch {
    Write-Host "assert-harness-drift: DID NOT LOOK - the shipped harness could not be resolved ($_)." -ForegroundColor Yellow
    exit 2
}

try {
    . (Join-Path $repoRoot 'scripts/utils/project-paths.ps1')
    $canonHarness = Join-Path (Get-CanonRoot) 'tools/harness'
}
catch {
    Write-Host "assert-harness-drift: DID NOT LOOK - the canon root could not be resolved ($_)." -ForegroundColor Yellow
    exit 2
}

if (-not (Test-Path -LiteralPath $canonHarness -PathType Container)) {
    Write-Host "assert-harness-drift: DID NOT LOOK - no canon checkout at $canonHarness." -ForegroundColor DarkGray
    Write-Host '  This is normal on any machine but the canon author its. Set SZA_CANON_ROOT to compare.' -ForegroundColor DarkGray
    exit 2
}

function Get-NormalisedHash([string]$Path) {
    # Line endings are compared out deliberately. The canon checkout is LF in the working tree and
    # CRLF after git touches it, and the plugin package normalises again on install, so a raw hash
    # reports every harness file as drifted on every machine and the gate becomes noise on its
    # first run. What matters is whether the CODE differs.
    $text = (Get-Content -LiteralPath $Path -Raw -ErrorAction Stop) -replace "`r`n", "`n"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { return [System.BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '') }
    finally { $sha.Dispose() }
}

$drift = @()
foreach ($canonFile in @(Get-ChildItem -LiteralPath $canonHarness -Recurse -File -Filter '*.ps1' -ErrorAction SilentlyContinue)) {
    $relative = $canonFile.FullName.Substring($canonHarness.Length).TrimStart('\', '/')
    $shippedPath = Join-Path $runningHarness $relative
    if (-not (Test-Path -LiteralPath $shippedPath -PathType Leaf)) {
        $drift += [pscustomobject]@{ File = $relative; State = 'absent from the running harness' }
        continue
    }
    try {
        if ((Get-NormalisedHash $canonFile.FullName) -ne (Get-NormalisedHash $shippedPath)) {
            $newer = if ($canonFile.LastWriteTimeUtc -gt (Get-Item -LiteralPath $shippedPath).LastWriteTimeUtc) { 'canon is newer' } else { 'running copy is newer' }
            $drift += [pscustomobject]@{ File = $relative; State = "differs ($newer)" }
        }
    }
    catch {
        $drift += [pscustomobject]@{ File = $relative; State = 'unreadable on one side' }
    }
}

Write-Host "assert-harness-drift: running harness $runningHarness" -ForegroundColor DarkGray
Write-Host "assert-harness-drift: canon checkout  $canonHarness" -ForegroundColor DarkGray

if ($drift.Count -eq 0) {
    Write-Host 'assert-harness-drift: PASS - the running harness matches the canon checkout.' -ForegroundColor Green
    exit 0
}

if (-not $Quiet) {
    foreach ($entry in $drift) { Write-Host "  $($entry.File) - $($entry.State)" -ForegroundColor Yellow }
}
Write-Host "assert-harness-drift: ADVISORY - $($drift.Count) harness file(s) drifted between the canon checkout and the copy that runs." -ForegroundColor Yellow
Write-Host '  A fix present only in the canon working tree is not delivered: commit it and publish the plugin.' -ForegroundColor Yellow
Write-Host '  A fix present only in the running copy is one plugin update from being lost.' -ForegroundColor Yellow
exit 3
