<#
reset-catalog-touch-marker.ps1 - Claude Code SessionStart hook (S1344).

Removes temp/catalog-touch.marker so every session starts with the Kotlin-search gate armed.

Why this exists: strategic section 6.4 fixes the marker as valid for the whole session and
explicitly forbids a TTL or a call counter. Nothing in that ruling expires the file, so
without this hook the marker written by the first ever dev/CATALOG/scripts/query.ps1 call
survives forever and guard-catalog-before-kt-search.ps1 takes its pass branch in every later
session - a gate that is inert everywhere except the machine-hour it was authored in. Deleting
the marker when a session begins implements "valid until the end of the session" literally,
with no timeout and no counter.

Concurrent sessions: a second session starting removes a marker the first one earned, so that
first session is refused once more and re-runs its query. The failure mode is one extra
catalogue lookup, which is the direction this gate wants to err in.

Contract:
  exit 0 always - a session must never fail to start because of this hook.

Exit codes (CLAUDE.md Rule 7):
  0  the marker is absent afterwards, or could not be removed and was left alone.

Registered per-project in .claude/settings.json alongside the PreToolUse gate it serves.
#>

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$MarkerFile = Join-Path $RepoRoot 'temp\catalog-touch.marker'

try {
    if (Test-Path -LiteralPath $MarkerFile) {
        Remove-Item -LiteralPath $MarkerFile -Force
    }
}
catch {
    # A locked or unreadable marker leaves the gate open for this session, which is the same
    # behaviour as before this hook existed - never worth blocking a session start over.
    [Console]::Error.WriteLine("reset-catalog-touch-marker: could not remove $MarkerFile - $_")
}

exit 0
