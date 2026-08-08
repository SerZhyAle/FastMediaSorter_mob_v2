# Run-Tests.ps1 (S1504) - regression suite for argument binding in scripts/spec_catalog/update.ps1
# and insert.ps1, and for the `name` integrity check in _lib.ps1's Assert-Record.
#
# Regression origin: closing S1474 on 2026-08-08, a status note ending
# `Probe tags: 3 lines tagged \"S1474:\" in logcat.` silently renamed the ticket. Backslash is not
# an escape character in PowerShell, so the parser closed the string at the first \" and handed the
# binder three arguments instead of one: `Probe tags: 3 lines tagged \` bound to -StatusNote, and
# the unnamed remainder `S1474:\ in logcat.` bound POSITIONALLY to the first free slot - $Name,
# third in the param block. Assert-Record only required `name` to be non-empty, so the rename
# reached disk and surfaced only when a human read the record back. A renamed ticket breaks slug
# resolution (select.ps1 -Name) and the release-queue reconciliation that matches on name.
#
# The same failure shape was already fixed once on close-and-log.ps1 (S1063), where it had degraded
# from hard error to silent mis-bind as new optional [string] params were added. This suite pins the
# barrier itself (PositionalBinding = $false) rather than any single field, so it cannot rot the
# same way as the param blocks grow.
#
# What this runner touches (it is NOT hermetic):
#   * The subject ticket's statusNote and `updated` timestamp move. The prior note is captured up
#     front and restored per record in a finally block - never a whole-journal restore, because that
#     form silently reverts a sibling session's write under parallel /spec-do sessions (S1490).
#   * Every case passes -Status <the subject's CURRENT status>, so no lifecycle transition ever
#     happens and the suite is idempotent across repeated runs.
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/update.tests/Run-Tests.ps1
#         pwsh -NoProfile -File .../Run-Tests.ps1 -SubjectId S0223   # any live catalog id
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed, or the subject id could not be resolved.

[CmdletBinding(PositionalBinding = $false)]
param(
    # Any id present in the active catalog. Its status is read and echoed straight back, so the
    # choice is inert - it defaults to the ticket that introduced these guarantees.
    [string]$SubjectId = 'S1504'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$updatePs1 = Join-Path $repoRoot 'scripts/spec_catalog/update.ps1'
$insertPs1 = Join-Path $repoRoot 'scripts/spec_catalog/insert.ps1'
$selectPs1 = Join-Path $repoRoot 'scripts/spec_catalog/select.ps1'
$catalog = Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

function Get-SubjectRecord {
    return (& $pwshExe -NoProfile -File $selectPs1 -Id $SubjectId -Format json | ConvertFrom-Json)
}

function Get-SubjectField([string]$field) {
    $rec = Get-SubjectRecord
    if ($rec.PSObject.Properties.Name -contains $field) { return $rec.$field }
    return $null
}

function Get-LineCount([string]$path) {
    if (Test-Path $path) { return @(Get-Content -LiteralPath $path).Count }
    return 0
}

$subject = Get-SubjectRecord
if (-not $subject) { Write-Host "Cannot resolve $SubjectId" -ForegroundColor Red; exit 1 }
$subjectStatus = $subject.status
$priorNote = if ($subject.PSObject.Properties.Name -contains 'statusNote') { $subject.statusNote } else { $null }
$priorName = $subject.name
Write-Host "subject: $SubjectId (status '$subjectStatus', name '$priorName')" -ForegroundColor DarkGray

try {
    # --- A: a plainly stray unnamed token must die at bind time. This is the invariant; case B is
    # the specific keystrokes that produced one in the field. ---
    Write-Host "A: a stray positional argument is rejected before any write" -ForegroundColor Yellow
    $updBefore = Get-SubjectField 'updated'
    $outA = & $pwshExe -NoProfile -File $updatePs1 -Id $SubjectId -Status $subjectStatus 'stray-token' 2>&1 | Out-String
    $exitA = $LASTEXITCODE
    Assert-That "A1 non-zero exit" ($exitA -ne 0) "exit=$exitA"
    Assert-That "A2 error names the stray positional arg" ($outA -match 'positional parameter cannot be found') "out=$($outA.Trim())"
    Assert-That "A3 name not overwritten" ((Get-SubjectField 'name') -eq $priorName) "name=$(Get-SubjectField 'name')"
    Assert-That "A4 record not mutated" ((Get-SubjectField 'updated') -eq $updBefore) "updated moved from $updBefore"

    # --- B: the S1474 keystrokes verbatim. Invoke-Expression is what makes this faithful - the
    # split happens in PowerShell's own parse of the command TEXT, so passing the note through a
    # variable (which re-quotes correctly) would not reproduce it at all. ---
    Write-Host "B: the S1474 note shape is rejected instead of renaming the ticket" -ForegroundColor Yellow
    $updBefore = Get-SubjectField 'updated'
    $corruptCall = '& "' + $pwshExe + '" -NoProfile -File "' + $updatePs1 + '" -Id ' + $SubjectId +
        " -Status '" + $subjectStatus + "'" +
        ' -StatusNote "Probe tags: 3 lines tagged \"' + $SubjectId + ':\" in logcat." 2>&1 | Out-String'
    $outB = Invoke-Expression $corruptCall
    $exitB = $LASTEXITCODE
    Assert-That "B1 non-zero exit" ($exitB -ne 0) "exit=$exitB"
    Assert-That "B2 error names the stray positional arg" ($outB -match 'positional parameter cannot be found') "out=$($outB.Trim())"
    Assert-That "B3 name survives (the S1474 defect)" ((Get-SubjectField 'name') -eq $priorName) "name=$(Get-SubjectField 'name')"
    Assert-That "B4 record not mutated" ((Get-SubjectField 'updated') -eq $updBefore) "updated moved from $updBefore"

    # --- C: the legitimate form must keep working. The probe-tag convention actively pushes quotes
    # into notes, since the tag reads Timber.d("Sxxxx: .."), so a fix that banned them would break
    # the very case the convention needs. ---
    Write-Host "C: a note carrying real double quotes round-trips verbatim" -ForegroundColor Yellow
    $goodNote = 'Probe tags: 3 lines tagged "' + $SubjectId + ':" in logcat.'
    $outC = & $pwshExe -NoProfile -File $updatePs1 -Id $SubjectId -Status $subjectStatus -StatusNote $goodNote 2>&1 | Out-String
    $exitC = $LASTEXITCODE
    Assert-That "C1 exit 0" ($exitC -eq 0) "exit=$exitC out=$($outC.Trim())"
    Assert-That "C2 note stored verbatim" ((Get-SubjectField 'statusNote') -eq $goodNote) "stored=$(Get-SubjectField 'statusNote')"
    Assert-That "C3 name untouched by the note" ((Get-SubjectField 'name') -eq $priorName) "name=$(Get-SubjectField 'name')"

    # --- D: the write-time net. Even if some future caller finds a new way to smuggle a fragment
    # into `name`, Assert-Record refuses it before it reaches disk. ---
    Write-Host "D: Assert-Record refuses a name carrying quote/backslash/control chars" -ForegroundColor Yellow
    . (Join-Path $repoRoot 'scripts/spec_catalog/_lib.ps1')
    $template = [pscustomobject]@{
        id = 'S9999'; name = 'legit-slug-name'; status = 'Draft'; priority = 50
        file = 'PLAN/S9999_legit-slug-name.md'; created = '2026-08-08'; updated = '2026-08-08 00:00'
    }
    $okBaseline = $true
    try { Assert-Record -Record $template } catch { $okBaseline = $false }
    Assert-That "D1 a normal slug still passes" $okBaseline "a legitimate name was rejected"
    foreach ($badName in @('S1474:\ in logcat.', 'name "quoted" fragment', "line`tbreak")) {
        $bad = $template.PSObject.Copy()
        $bad.name = $badName
        $rejected = $false
        try { Assert-Record -Record $bad } catch { $rejected = $true }
        Assert-That "D2 rejected: [$badName]" $rejected "Assert-Record accepted a corrupt name"
    }

    # --- E: insert.ps1 shares the exposure - $Name leads its param block, so a stray token would
    # name a brand-new record rather than fail the call. ---
    Write-Host "E: insert.ps1 rejects a stray positional argument with no record created" -ForegroundColor Yellow
    $catBefore = Get-LineCount $catalog
    $outE = & $pwshExe -NoProfile -File $insertPs1 -Name 's1504-binding-probe' -Slug 's1504-binding-probe' 'stray-token' 2>&1 | Out-String
    $exitE = $LASTEXITCODE
    Assert-That "E1 non-zero exit" ($exitE -ne 0) "exit=$exitE"
    Assert-That "E2 error names the stray positional arg" ($outE -match 'positional parameter cannot be found') "out=$($outE.Trim())"
    Assert-That "E3 no record created" ((Get-LineCount $catalog) -eq $catBefore) "catalog grew by $((Get-LineCount $catalog) - $catBefore)"
}
finally {
    # Per record, never the whole journal (S1490): restoring a backup of spec-catalog.jsonl would
    # revert whatever a sibling session wrote while this suite ran.
    if ($null -ne $priorNote) {
        & $pwshExe -NoProfile -File $updatePs1 -Id $SubjectId -Status $subjectStatus -StatusNote $priorNote | Out-Null
    } else {
        & $pwshExe -NoProfile -File $updatePs1 -Id $SubjectId -Status $subjectStatus -StatusNote '' | Out-Null
    }
    Write-Host "subject restored ($SubjectId status note)" -ForegroundColor DarkGray
}

Write-Host ""
if ($script:fail -eq 0) {
    Write-Host "update binding tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "update binding tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
