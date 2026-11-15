#requires -Version 7.0
<#
.SYNOPSIS
    S4645: every set baseline is compared with its copy at HEAD, and an identifier added since
    needs a row in scripts/quality/set-baseline-accepted.jsonl (CHECK-BASELINE 0.12 item B).

.DESCRIPTION
    Contract CHECK-BASELINE rule 3.4 binds every set baseline, not only the ones a tool rewrites:
    recomputing the findings from the tree catches a new finding the file does not list, but it cannot
    see a line added to the file in the same change - that line absorbs the finding and the run passes
    in silence, which rule 3.2 forbids ("a run that would raise it fails"). Item B.1 of the 2026-10-29
    amendment names the baseline file as committed (the copy at HEAD) as the snapshot.

    Scope: every inventory row (scripts/quality/baseline-inventory.jsonl) with `shape: set`, except a
    row whose owner is an `*-baseline-absorption.ps1` gate - the detekt and lint identifier snapshots
    are judged by that gate against a committed snapshot of their own.

    Judging: the working file's identifiers (non-blank lines that do not start with `#`) minus the
    identifiers of `git show HEAD:<file>`. A file with no copy at HEAD is a new class and is seeded, never
    a rise. A pruned identifier always passes. An added identifier is a finding unless the journal holds
    a row for that file and identifier.

    The update path (rule 3.6, item B.2) is the journal, not a commit, because this repository commits
    only at a release: `-Accept -File <baseline> -Reason '<why>' -Ticket Sxxxx` prints every identifier
    it accepts and appends one row per identifier. A row for an identifier that has since left the
    baseline is harmless and may be deleted by hand.

    Per ticket under the fixed-input contract (lib/fixed-input-scope.ps1): with -ChangedFiles a finding
    is charged only when the journal, this script or the finding's own baseline file is in the set.

.PARAMETER Gate
    Fail-closed: exit 1 when a chargeable finding exists.

.PARAMETER Quiet
    Suppress the PASS line.

.PARAMETER ChangedFiles
    Repo-relative paths the caller changed, comma-joined. Omit for the project-wide verdict.

.PARAMETER Accept
    Write journal rows for the unaccepted added identifiers of -File and exit 0.

.PARAMETER File
    With -Accept: the repo-relative set baseline whose added identifiers are accepted.

.PARAMETER Reason
    With -Accept: why the debt is accepted. Recorded on every row.

.PARAMETER Ticket
    With -Accept: the ticket id (Sxxxx) that owns the acceptance.

.PARAMETER MaxListed
    Ceiling on the per-file identifier listing; a truncated list states how many were dropped.

.PARAMETER RepoRoot
    Test seam: judge another repository root. Default is this repository.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-set-baseline-rise.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-set-baseline-rise.ps1 -Accept -File scripts/quality/help-map-baseline.txt -Reason 'screen X ships without a help page until S9999' -Ticket S9999

.NOTES
    Exit codes:
      0 - no unaccepted rise; findings without -Gate; or -Accept completed.
      1 - an unaccepted rise and -Gate was passed.
      2 - could not verify: the inventory or the journal is missing or holds a line that is not JSON,
          HEAD does not resolve, git failed, or -Accept was called with a missing or unknown argument.
      3 - findings exist, but neither the journal, this script nor any of their baselines is in -ChangedFiles.
      4 - -Accept: the Code domain of the journal is held by another session, so nothing was written.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    # `pwsh -File` binds only the first element of a [string[]], so callers comma-join.
    [string[]]$ChangedFiles,
    [switch]$Accept,
    [string]$File,
    [string]$Reason,
    [string]$Ticket,
    [int]$MaxListed = 50,
    [string]$RepoRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# git prints identifiers as UTF-8; the console default would split a non-ASCII identifier.
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

. (Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1')
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')

$root = if ($RepoRoot) { (Resolve-Path -LiteralPath $RepoRoot).Path } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$inventoryRel = 'scripts/quality/baseline-inventory.jsonl'
$journalRel = 'scripts/quality/set-baseline-accepted.jsonl'
$gateRel = 'scripts/quality/assert-set-baseline-rise.ps1'
$inventoryPath = Join-Path $root $inventoryRel
$journalPath = Join-Path $root $journalRel

Write-CheckSubject ([ordered]@{ scope = 'set-baselines'; mode = $(if ($Accept) { 'accept' } else { 'gate' }) })

function Stop-CouldNotVerify([string]$Text) {
    Write-Error "assert-set-baseline-rise: COULD NOT VERIFY - $Text" -ErrorAction Continue
    exit 2
}

function Get-RowText([object]$Row, [string]$Name) {
    $property = $Row.PSObject.Properties[$Name]
    if ($property) { return [string]$property.Value }
    return ''
}

function Read-JsonLines([string]$Path, [string]$Label) {
    $rows = [System.Collections.Generic.List[object]]::new()
    $number = 0
    foreach ($line in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        $number++
        if (-not $line.Trim()) { continue }
        try { $rows.Add(($line | ConvertFrom-Json)) }
        catch { Stop-CouldNotVerify "$Label line $number is not JSON." }
    }
    return , $rows.ToArray()
}

function Get-IdentifierSet([string[]]$Lines) {
    $set = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in $Lines) {
        $text = $line.Trim()
        if ($text -and -not $text.StartsWith('#')) { [void]$set.Add($text) }
    }
    return , $set
}

if (-not (Test-Path -LiteralPath $inventoryPath)) { Stop-CouldNotVerify "$inventoryRel is missing." }
$inventory = Read-JsonLines $inventoryPath $inventoryRel

$accepted = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
if (Test-Path -LiteralPath $journalPath) {
    foreach ($row in (Read-JsonLines $journalPath $journalRel)) {
        $acceptedFile = (Get-RowText $row 'file') -replace '\\', '/'
        $acceptedId = Get-RowText $row 'id'
        if ($acceptedFile -and $acceptedId) { [void]$accepted.Add("$acceptedFile`t$acceptedId") }
    }
}

$setRows = @($inventory | Where-Object {
        (Get-RowText $_ 'shape') -eq 'set' -and (Get-RowText $_ 'owner') -notmatch 'baseline-absorption\.ps1'
    })

& git -C $root rev-parse --verify --quiet 'HEAD^{commit}' *> $null
if ($LASTEXITCODE -ne 0) { Stop-CouldNotVerify "HEAD does not resolve in $root." }

$findings = [System.Collections.Generic.List[pscustomobject]]::new()
$seeded = 0
$compared = 0
$acceptedRises = 0
foreach ($row in $setRows) {
    $rel = (Get-RowText $row 'file') -replace '\\', '/'
    $absolute = Join-Path $root $rel
    # A declared row whose file is gone is assert-baseline-inventory's finding, not this gate's.
    if (-not $rel -or -not (Test-Path -LiteralPath $absolute)) { continue }

    & git -C $root cat-file -e "HEAD:$rel" 2>$null
    if ($LASTEXITCODE -ne 0) { $seeded++; continue }
    $committedLines = @(& git -C $root show "HEAD:$rel" 2>$null)
    if ($LASTEXITCODE -ne 0) { Stop-CouldNotVerify "git show HEAD:$rel failed." }

    $compared++
    $committed = Get-IdentifierSet $committedLines
    $working = Get-IdentifierSet @(Get-Content -LiteralPath $absolute -Encoding UTF8)
    $added = @($working | Where-Object { -not $committed.Contains($_) } | Sort-Object { $_ } -CaseSensitive)
    $unaccepted = @($added | Where-Object { -not $accepted.Contains("$rel`t$_") })
    $acceptedRises += $added.Count - $unaccepted.Count
    if ($unaccepted.Count -gt 0) {
        $findings.Add([pscustomobject]@{ File = $rel; Ids = $unaccepted })
    }
}

if ($Accept) {
    $target = ($File -replace '\\', '/')
    if ([string]::IsNullOrWhiteSpace($target) -or [string]::IsNullOrWhiteSpace($Reason) -or $Ticket -notmatch '^S\d{4}$') {
        Stop-CouldNotVerify '-Accept needs -File, -Reason and -Ticket Sxxxx.'
    }
    if (@($setRows | Where-Object { ((Get-RowText $_ 'file') -replace '\\', '/') -eq $target }).Count -eq 0) {
        Stop-CouldNotVerify "-File $target is not a set baseline judged here (not in the inventory as shape set, or owned by an absorption gate)."
    }
    $hit = @($findings | Where-Object { $_.File -eq $target })
    if ($hit.Count -eq 0) {
        Write-Host "assert-set-baseline-rise: nothing to accept for $target - no identifier was added since HEAD."
        exit 0
    }
    $date = Get-Date -Format 'yyyy-MM-dd'
    $lines = foreach ($id in $hit[0].Ids) {
        ([ordered]@{ file = $target; id = $id; reason = $Reason.Trim(); ticket = $Ticket; date = $date } | ConvertTo-Json -Compress)
    }
    $codeScope = $null
    try {
        $codeScope = Enter-CodeLockOrExit -Path $journalPath -Reason 'assert-set-baseline-rise.ps1 -Accept'
        [System.IO.File]::AppendAllText($journalPath, (($lines -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
    }
    finally { Exit-CodeLockScope -Scope $codeScope }
    Write-Host ("assert-set-baseline-rise: accepted {0} identifier(s) of {1} on the journal - name them in the dev-log row:" -f $hit[0].Ids.Count, $target) -ForegroundColor Yellow
    foreach ($id in ($hit[0].Ids | Select-Object -First $MaxListed)) { Write-Host "    + $id" }
    if ($hit[0].Ids.Count -gt $MaxListed) { Write-Host "    .. and $($hit[0].Ids.Count - $MaxListed) more" }
    exit 0
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host ("assert-set-baseline-rise: PASS - {0} set baseline(s) compared with HEAD, {1} seeded (no copy at HEAD), {2} accepted rise(s) on the journal." -f
            $compared, $seeded, $acceptedRises)
    }
    exit 0
}

$texts = foreach ($finding in $findings) {
    $lines = @($finding.Ids | Select-Object -First $MaxListed | ForEach-Object { "    + $_" })
    if ($finding.Ids.Count -gt $MaxListed) { $lines += "    .. and $($finding.Ids.Count - $MaxListed) more" }
    ("{0}: {1} identifier(s) added since HEAD and not on the journal`n{2}`n  accept: pwsh -NoProfile -File {3} -Accept -File {0} -Reason '<why>' -Ticket Sxxxx" -f
        $finding.File, $finding.Ids.Count, ($lines -join "`n"), $gateRel)
}

$inputs = @($journalRel, $gateRel) + @($findings | ForEach-Object { $_.File })
if (-not (Test-FixedInputsChargeable -ChangedFiles $ChangedFiles -InputPaths $inputs)) {
    Write-NotChargedVerdict -GateName 'assert-set-baseline-rise' -Findings @($texts)
    exit 3
}

Write-Error ("assert-set-baseline-rise: FAIL - {0} set baseline(s) accepted new identifiers (CHECK-BASELINE rule 3.2, item B):`n{1}" -f
    $findings.Count, ($texts -join "`n")) -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
