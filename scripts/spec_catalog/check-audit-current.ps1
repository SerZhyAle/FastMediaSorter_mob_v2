<#
.SYNOPSIS
    Gate: the recorded audit judged the task text the spec carries NOW (S2367).

.DESCRIPTION
    `Verified` asserts that an audit passed; `BlockNeedUserTest` asserts that the work is
    ready for a human to observe. Both are claims about a task, and neither is worth
    anything if the task changed after the claim was written.

    S2298 made the verdict provable by requiring the `## Last Audit` block. It cannot tell
    a verdict written against the current task from one written against a task the owner
    has since rewritten - and the owner does rewrite it: a spec is edited mid-run by the
    owner, by /spec-quiz writing an answer into it, and by a sibling session. The pipeline
    reads the spec once at Stage 0 and works from that reading for the rest of the run.

    This gate closes that window. The block records a `**Task fingerprint:**`, the gate
    recomputes it over the file on disk, and a mismatch means the audit judged different
    words than the ones the ticket is being closed against. The fix is never a flag: re-read
    the task and re-run the audit, which rewrites both the verdict and the stamp.

    What is hashed and why the pipeline's own writes are excluded from it lives in
    _task-fingerprint.ps1, shared verbatim with every writer, so this gate cannot refuse a
    stamp the writer considers correct (the S1621 rule).

    Deliberately silent about WHETHER the verdict is good: that judgement is /spec-check's,
    and a gate second-guessing it by keyword would be a verdict nobody could act on.

.PARAMETER Id
    Ticket id, Sxxxx.

.NOTES
    Exit codes:
      0 - the audit block stamps the current task text.
      1 - no stamp, or the stamp names a different task text than the file now carries.
      2 - bad invocation (malformed id, or an id no record carries), or catalog / spec
          unreadable. Kept distinct from 1 for the reason check-probe-present.ps1 states:
          "this ticket does not exist" and "this ticket was audited against stale text"
          call for opposite reactions, and exit 1 phrases the refusal as the latter.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

. (Join-Path $PSScriptRoot '_lib.ps1')
. (Join-Path $PSScriptRoot '_task-fingerprint.ps1')

if ($Id -notmatch '^S\d{4}$') {
    # -ErrorAction Continue, not a bare Write-Error: _lib.ps1 sets $ErrorActionPreference = 'Stop',
    # under which a bare Write-Error throws and the documented `exit 2` is never reached (S1070).
    Write-Error "Invalid -Id '$Id' (must match S####)." -ErrorAction Continue
    exit 2
}

$record = Find-Record -Id $Id
if (-not $record) {
    Write-Error "No record with id '$Id' in the spec catalog." -ErrorAction Continue
    exit 2
}

$specPath = Resolve-SpecPath -PathRef $record.file
if (-not (Test-Path -LiteralPath $specPath -PathType Leaf)) {
    Write-Error "Spec file not found on disk: $specPath" -ErrorAction Continue
    exit 2
}

$current = Get-SpecTaskFingerprint -Path $record.file
if (-not $current) {
    Write-Error "Could not read the task text of $($record.file)." -ErrorAction Continue
    exit 2
}

$recorded = Get-RecordedTaskFingerprint -Path $record.file

if (-not $recorded) {
    # Two different absences, one refusal: no block at all, or a block that never stamped what
    # it judged. They are not separated because the action is identical - run the audit - and a
    # second message would only invite guessing which one applies.
    Write-Output "FAIL $Id"
    Write-Output ("- {0}: the audit block records no task fingerprint." -f $record.file)
    Write-Output ""
    Write-Output "Nothing states which version of the task the recorded verdict judged, so the"
    Write-Output "verdict cannot be told apart from one written before the task was last edited."
    Write-Output "Do one of:"
    Write-Output ("  1. run the audit that writes both:  /spec-check {0}" -f $Id)
    Write-Output ("  2. re-read the task, then add to the '## Last Audit' block:")
    Write-Output ("     **Task fingerprint:** {0}" -f $current)
    exit 1
}

if ($recorded -ne $current) {
    Write-Output "FAIL $Id"
    Write-Output ("- {0}: the task changed after the recorded audit." -f $record.file)
    Write-Output ("  audited: {0}    on disk now: {1}" -f $recorded, $current)
    Write-Output ""
    Write-Output "The spec body was edited since the verdict was written - by the owner, by"
    Write-Output "/spec-quiz writing an answer in, or by another session. The recorded audit"
    Write-Output "judged different words than the ones this ticket is being closed against."
    Write-Output "Re-read the task as it stands and audit against it:"
    Write-Output ("  /spec-check {0}" -f $Id)
    Write-Output "If the re-read shows the work no longer covers the task, that is a Partial or"
    Write-Output "Broken verdict with the gap written into the spec - not a re-stamp."
    exit 1
}

Write-Output "PASS $Id"
Write-Output ("Audit judged the current task text (fingerprint {0})." -f $current)
exit 0
