<#
.SYNOPSIS
    Print the task-text fingerprint of a spec, for stamping into its `## Last Audit` block (S2367).

.DESCRIPTION
    The writer's half of the S2367 pair. `check-audit-current.ps1` refuses a transition into
    `Verified` or `BlockNeedUserTest` when the audit block does not stamp the task text the
    file carries now; this prints the value that stamp must hold.

    Both sides call Get-SpecTaskFingerprint in _task-fingerprint.ps1, so what counts as "the
    task" is defined once. The stamp is stable across the write itself: the `## Last Audit`
    block and the `**Status:**` header are excluded from the hash, so computing it before
    writing the block, and again after the status flip, yields the same value.

    Use -Line to emit the whole markdown line rather than the bare digest - a hand-typed
    stamp with the label spelled differently is a stamp the gate cannot read.

.PARAMETER Id
    Ticket id, Sxxxx. Resolved against the catalog. Mutually exclusive with -Path.

.PARAMETER Path
    Spec file, repo-relative or absolute. Use for a file the catalog does not carry yet.

.PARAMETER Line
    Emit `**Task fingerprint:** <digest>` instead of the bare digest.

.NOTES
    Exit codes:
      0 - fingerprint printed.
      2 - bad invocation (neither or both selectors, malformed id, id no record carries),
          or the spec file is unreadable. There is no exit 1: this script reports a value,
          it judges nothing.
#>
[CmdletBinding()]
param(
    [string] $Id,
    [string] $Path,
    [switch] $Line
)

. (Join-Path $PSScriptRoot '_lib.ps1')
. (Join-Path $PSScriptRoot '_task-fingerprint.ps1')

if (($Id -and $Path) -or (-not $Id -and -not $Path)) {
    Write-Error 'Give exactly one of -Id <Sxxxx> or -Path <spec file>.' -ErrorAction Continue
    exit 2
}

$pathRef = $Path
if ($Id) {
    if ($Id -notmatch '^S\d{4}$') {
        Write-Error "Invalid -Id '$Id' (must match S####)." -ErrorAction Continue
        exit 2
    }
    $record = Find-Record -Id $Id
    if (-not $record) {
        Write-Error "No record with id '$Id' in the spec catalog." -ErrorAction Continue
        exit 2
    }
    $pathRef = $record.file
}

$fingerprint = Get-SpecTaskFingerprint -Path $pathRef
if (-not $fingerprint) {
    Write-Error "Spec file not found or unreadable: $pathRef" -ErrorAction Continue
    exit 2
}

if ($Line) {
    Write-Output ("**Task fingerprint:** {0}" -f $fingerprint)
} else {
    Write-Output $fingerprint
}
exit 0
