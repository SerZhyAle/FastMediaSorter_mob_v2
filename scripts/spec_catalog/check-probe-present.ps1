[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

# Gate for a transition INTO BlockNeedUserTest - see S2324.
#
# Contract:
#   - CLAUDE.md Rule 2 states the debug-probe invariant as an equivalence: a
#     Timber.d("Sxxxx: ..") line exists in source if and only if Sxxxx is in
#     BlockNeedUserTest. The forward half is enforced hard - a probe whose ticket is not
#     in that status is a FAIL in assert-no-ticket-logs.ps1. The reverse half had no gate
#     at the moment it becomes violable, which is this transition.
#   - Measured 2026-09-02: 20 tickets sat in BlockNeedUserTest with no probe, and the set
#     was not a static remainder - it had turned over since 2026-09-01, gaining S1617,
#     S1955 and S1984 in a single day. A backlog that refills needs a gate, not a sweep.
#   - Why nothing caught it: post-change.ps1 hands the tree gate -ChangedFiles under
#     -ScopeToFile, which by S1912's design downgrades the missing-probe half to exit 3,
#     an advisory. The only fatal check left was assert-fast-gates.ps1, project-wide - so
#     it went red for whichever session happened to run it, over debt belonging to twenty
#     other tickets, which that session could not fix. /spec-check's own step 6 meanwhile
#     instructed "add one .. or the ticket-log gate refuses the close" - a refusal that
#     did not exist on any closing path.
#   - Assert-ClosingGates is the one point all three status-writing paths reach
#     (update.ps1, close.ps1, bulk-update.ps1), which is why the gate sits there rather
#     than in a command file that only one pipeline reads.
#   - The order this demands is the order Rule 2 already prescribes: the probe is the last
#     code edit BEFORE the status flip, so it is in the tree by the time this runs.
#   - Two ways to pass, and the refusal text names both. A ticket that changed only
#     documentation, scripts or resources has no executable path to instrument; it belongs
#     in scripts/quality/blockneedusertest-probe-baseline.txt with a stated reason. Refusing
#     those outright would make them unclosable, which is a worse failure than the gap.
#   - "Carries a probe" is decided by scripts/quality/lib/blockneedusertest-probes.ps1, the
#     same code assert-no-ticket-logs.ps1 uses. A second implementation would let this gate
#     refuse a transition the tree gate is content with (the S1621 rule); it is not
#     hypothetical, because a Timber call may span physical lines and a per-line search
#     finds a strictly smaller set.
#
# Exit codes: 0 = a probe exists, or the ticket is excused in the baseline.
#             1 = neither - the transition must not proceed.
#             2 = bad invocation (malformed id, or an id no record carries), or catalog /
#                 sources unreadable. Kept distinct from 1 because "this ticket does not
#                 exist" and "this ticket forgot its probe" call for opposite reactions, and
#                 exit 1 phrases the refusal as the latter - it would tell the operator to add
#                 a probe for an id that names nothing, or to excuse it in the baseline forever.

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') {
    # -ErrorAction Continue, not a bare Write-Error: _lib.ps1 sets $ErrorActionPreference = 'Stop',
    # under which a bare Write-Error throws and the documented `exit 2` is never reached (S1070).
    Write-Error "Invalid -Id '$Id' (must match S####)." -ErrorAction Continue
    exit 2
}

# A well-formed id that names no record is still a bad invocation, not a missing probe. Without
# this the gate answers "add a probe for S9999", which is unactionable, and the caller cannot tell
# a typo from a real refusal - the same "could not look is not found nothing" split the source-root
# check below makes. Matches check-audit-recorded.ps1, the sibling gate in this list.
$record = Find-Record -Id $Id
if (-not $record) {
    Write-Error "No record with id '$Id' in the spec catalog." -ErrorAction Continue
    exit 2
}

$probeLib = Join-Path $PSScriptRoot '../quality/lib/blockneedusertest-probes.ps1'
if (-not (Test-Path -LiteralPath $probeLib)) {
    Write-Error "Probe helper not found at $probeLib." -ErrorAction Continue
    exit 2
}
. $probeLib

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$sourceRoots = @(Get-ProbeSourceRoot -RepoRoot $repoRoot)
if ($sourceRoots.Count -eq 0) {
    # "Could not look" is not "found nothing" - a checkout without the modules must not
    # silently certify every ticket as probed.
    Write-Error "No source root to scan under $repoRoot (expected app_v2 and/or wear)." -ErrorAction Continue
    exit 2
}

$baselinePath = Get-ProbeBaselinePath -RepoRoot $repoRoot
$excused = Get-ExcusedProbeTickets -BaselinePath $baselinePath
if ($excused.Contains($Id)) {
    Write-Output "PASS $Id"
    Write-Output "Excused in blockneedusertest-probe-baseline.txt - no executable path to instrument."
    exit 0
}

$hit = Test-TicketProbeInSource -Id $Id -SourceRoots $sourceRoots
if ($hit.Found) {
    $rel = $hit.File.Substring($repoRoot.Length).TrimStart('\', '/')
    Write-Output "PASS $Id"
    Write-Output ("Probe present: {0}:{1}" -f $rel, $hit.Line)
    exit 0
}

Write-Output "FAIL $Id"
Write-Output ("- no Timber.d(`"{0}: ..`") in app_v2 or wear, and no row in blockneedusertest-probe-baseline.txt." -f $Id)
Write-Output ""
Write-Output "BlockNeedUserTest means a human still has to watch this run on a device."
Write-Output "Without the probe the log cannot tell 'the scenario went through the new code'"
Write-Output "from 'the scenario never reached it', so the device pass proves nothing."
Write-Output "Do one of:"
Write-Output ("  1. add one probe at the entry of the flow this ticket changed:  Timber.d(`"{0}: <what ran>`")" -f $Id)
Write-Output ("  2. if the ticket changed no executable path (docs, scripts, resources only), add a row")
Write-Output ("     to scripts/quality/blockneedusertest-probe-baseline.txt: `"{0}  <why a probe cannot exist, and what the human reads instead>`"" -f $Id)
Write-Output ""
Write-Output "Insert the probe BEFORE flipping the status - that is the order CLAUDE.md Rule 2 already prescribes."
exit 1
