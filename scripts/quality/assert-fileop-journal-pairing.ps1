#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: a destructive file operation declared in the transfer strategy trees must name how
    it reaches the mutation journal - a call here, a registering caller, or a read-only reason - and
    the set of operations doing none of those may never grow.

.DESCRIPTION
    S3371 (research 01, Finding 2). The app ships soft delete with restore and whole-folder undo,
    and the mechanism behind them is the mutation journal - domain/mutation/MutationJournal.kt
    records a Mutation.Delete / Move / Rename / BatchDelete, and Browse reconciles its list from
    those entries. Nothing required a NEW destructive operation to reach that journal. An operation
    added below the seam and never registered is invisible to the reconciler: the file is gone from
    the disk and still on the screen, and there is nothing to undo it with.

    That is the same structural hole assert-migration-test-pairing.ps1 was written for - a pairing
    held together by habit, where the missing half is silent - so this gate copies its shape: one
    subject per declaration, two explicit opt-outs, and a baseline that may shrink and never grow.

    SUBJECT. Every .kt file under the transfer strategy trees (see $script:ScanTrees below):
    data/transfer/ - the FileOperationStrategy interface, the per-protocol adapters under
    strategy/ (local, SAF, SMB, SFTP, FTP, cloud), the cross-protocol pairs under strategies/ and
    the byte-level FileAccess adapters under access/ - plus domain/transfer/, which carries the
    provider and progress contracts those adapters implement. The contract they all obey is
    docs/ARCHITECTURE.md "External I/O contract".

    DESTRUCTIVE DECLARATION. A declared `fun` whose name STARTS with one of the destructive verbs
    (see $script:DestructiveVerbs). The name is matched at the start so improveX and approveX are
    not swept in by "move". Declarations are counted, not call sites: an interface member with a
    default body is exactly as much a promise as an override, and FileOperationStrategy.kt declares
    five of them.

    PAIRED. The same file names the journal seam - the MutationJournal type, or one of the four
    Mutation variants. Same-file, like the migration gate's same-name pairing, because the point is
    that the author of the destructive operation had the registration in front of them.

    TWO OPT-OUTS, both explicit, neither silent:

      fileops-read-only-allowlist.txt - the operation reads, reports or disposes of state the app
      itself created, so there is nothing for the journal to undo: an in-memory progress map, a
      temp file the transfer wrote, a half-written destination the sink abandons. Every row carries
      a reason, and a row without one is refused - an opt-out nobody can re-judge in a year is how
      an allowlist turns into a suppression list (the same rule assert-no-secrets.ps1 applies to
      its own).

      fileop-journal-pairing-baseline.txt - REGISTERS ABOVE THE SEAM. S3371 froze these 59 rows as
      debt and called the list a worklist; S3376 researched what draining it would take and found
      the list is not drainable where it stands, so the class is structural rather than temporal.
      Every journal entry is keyed by a resource id (domain/mutation/Mutation.kt - all four variants
      carry one, and every MutationJournal method takes one as its first argument), and these trees
      address a path and credentials: a resource is a concept one or more layers up, where the Browse
      reconciler that consumes the journal also lives. Registration therefore belongs to the caller
      that owns the resource, and the four producers that exist all do it from there - the Player, the
      local FileObserver, the scheduler (ExecuteScheduledOperationUseCase) and the watch bridge
      (DeleteWatchRequestedFileUseCase), the last two wired by S3376 itself.
      Calling those 21 files "read-only" to reach a green run would still be the one genuinely wrong
      answer: the allowlist would then assert something false about deleteFile. The ratchet shape is
      kept for the same reason assert-migration-test-pairing.ps1 keeps it - the list may shrink when an
      operation leaves the trees, and may never grow.

    So a NEW destructive operation in these trees is the subject this gate exists for. It pairs
    with the journal, or it names the caller that registers it, or it says in one line why there is
    nothing to record.

    WHAT THIS GATE DOES NOT WATCH. The callers. Measured 2026-09-22 for S3376: 36 files outside these
    two trees reach a destructive transport operation and exactly one names the journal - but most of
    those 36 are not defects, because a caller that is itself the surface that will redraw (the Browse
    managers, compensated by a full reloadFiles() on the worker's terminal event) has nothing to
    reconcile. A useful predicate is "mutates and is NOT the surface that will redraw", which is not a
    grep. S3386 carries that design question with the measurement.

.PARAMETER Gate
    Exit 1 when an unpaired destructive operation is neither allowlisted nor baselined, or when an
    allowlist row carries no reason. Without it the script only reports.

.PARAMETER List
    Print every destructive declaration and its state, grouped by file.

.PARAMETER UpdateBaseline
    Rewrite the baseline from the current tree. Takes the Code.Scripts lock (Rule 23), because it
    writes a file under scripts/. Use only when deliberately accepting a new unpaired operation,
    which should be never: a growing list needs -Reason, and every added or dropped token is
    printed (S3460). Shrinking needs nothing.

.PARAMETER Reason
    Why -UpdateBaseline may admit a new unpaired operation. Recorded as a header line.

.PARAMETER RepoRoot
    Repository root to scan. Defaults to the parent of scripts/quality. The contract suite points
    it at a fixture tree so the refusals really execute.

.PARAMETER AllowlistPath
    Read this read-only allowlist instead of the one beside this script. For the suite.

.PARAMETER BaselinePath
    Read this baseline instead of the one beside this script. For the suite.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-fileop-journal-pairing.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-fileop-journal-pairing.ps1 -List

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every destructive declaration is paired, allowlisted or baselined (or reporting only).
      1  under -Gate: an unpaired declaration outside both opt-outs, or a reasonless allowlist row.
      2  cannot verify - not one scanned tree exists under -RepoRoot; or -UpdateBaseline would grow
         the baseline without -Reason (nothing is written).
      4  Code.Scripts is held by another session, so no baseline was written. The queue place is
         held - wait for the turn in the background and rerun.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$List,
    [switch]$UpdateBaseline,
    [string]$RepoRoot,
    [string]$AllowlistPath,
    [string]$BaselinePath,
    [string]$Reason,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }
if (-not $AllowlistPath) { $AllowlistPath = Join-Path $PSScriptRoot 'fileops-read-only-allowlist.txt' }
if (-not $BaselinePath) { $BaselinePath = Join-Path $PSScriptRoot 'fileop-journal-pairing-baseline.txt' }

# The trees the External I/O contract governs. A third one is a row here and no other edit.
$script:ScanTrees = @(
    'app_v2/src/main/java/com/sza/fastmediasorter/data/transfer'
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer'
)

# Anchored at the start of the operation name: "move" must not catch improveQuality, and "clear"
# must catch clearAll. Names that merely LOOK destructive are what the allowlist answers.
$script:DestructiveVerbs = @(
    'abort', 'clean', 'clear', 'delete', 'discard', 'drop', 'erase', 'evict', 'expire',
    'move', 'overwrite', 'prune', 'purge', 'remove', 'rename', 'trash', 'truncate',
    'unlink', 'wipe'
)

$script:DestructiveRx = [regex]('(?i)^(?:' + ($script:DestructiveVerbs -join '|') + ')')
$script:FunDeclarationRx = [regex]'(?m)^\s*(?:(?:override|open|private|internal|protected|abstract|suspend|inline|tailrec|operator|external|final)\s+)*fun\s+(?:<[^>]+>\s*)?([A-Za-z0-9_]+)'
# The journal seam, named the way a Kotlin file can name it: the injected type, or one of the four
# records it stores. A bare `record(` is deliberately not enough - half the tree has a method by
# some near name, and a gate that accepts a coincidence teaches nothing.
$script:JournalRx = [regex]'MutationJournal|Mutation\.(?:Delete|Move|Rename|BatchDelete)'

function Get-DestructiveDeclaration {
    <#
        One record per destructive declaration across every scanned tree. Returns an empty list
        when the trees hold none; the caller wraps the call in @( ) because an empty
        List[T] unrolls to $null on return and $null.Count throws under StrictMode.
    #>
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string[]]$Trees
    )

    $records = [System.Collections.Generic.List[object]]::new()

    foreach ($tree in $Trees) {
        $full = Join-Path $Root $tree
        if (-not (Test-Path -LiteralPath $full)) { continue }

        foreach ($file in Get-ChildItem -LiteralPath $full -Recurse -Filter '*.kt' -File) {
            $text = Get-Content -LiteralPath $file.FullName -Raw
            if (-not $text) { continue }

            $paired = $script:JournalRx.IsMatch($text)
            $relative = $file.FullName.Substring($Root.Length).TrimStart('\', '/').Replace('\', '/')

            $seen = [System.Collections.Generic.HashSet[string]]::new()
            foreach ($match in $script:FunDeclarationRx.Matches($text)) {
                $operation = $match.Groups[1].Value
                if (-not $script:DestructiveRx.IsMatch($operation)) { continue }
                if (-not $seen.Add($operation)) { continue }

                $records.Add([pscustomobject]@{
                        File      = $relative
                        Name      = $file.Name
                        Operation = $operation
                        Key       = "$relative#$operation"
                        Paired    = $paired
                    })
            }
        }
    }

    return $records
}

function Read-AllowlistRow {
    <#
        Rows are "<relative path>#<operation>   # <reason>". The reason is mandatory, so the
        parser returns the malformed rows too rather than dropping them - an opt-out that fails
        to parse must be visible, not absent.
    #>
    param([Parameter(Mandatory)][string]$Path)

    $rows = [System.Collections.Generic.List[object]]::new()
    if (-not (Test-Path -LiteralPath $Path)) { return $rows }

    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $Path) {
        $lineNumber++
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }

        $match = [regex]::Match($trimmed, '^(?<token>\S+)\s+#\s*(?<reason>\S.*)$')
        if ($match.Success) {
            $rows.Add([pscustomobject]@{
                    Token  = $match.Groups['token'].Value
                    Reason = $match.Groups['reason'].Value.Trim()
                    Line   = $lineNumber
                })
        }
        else {
            $rows.Add([pscustomobject]@{
                    Token  = ($trimmed -split '\s+')[0]
                    Reason = ''
                    Line   = $lineNumber
                })
        }
    }

    return $rows
}

$existingTrees = @($script:ScanTrees | Where-Object { Test-Path -LiteralPath (Join-Path $RepoRoot $_) })
if ($existingTrees.Count -eq 0) {
    [Console]::Error.WriteLine("assert-fileop-journal-pairing: cannot verify - not one scanned tree exists under '$RepoRoot' (looked for: $($script:ScanTrees -join ', ')).")
    exit 2
}

$records = @(Get-DestructiveDeclaration -Root $RepoRoot -Trees $existingTrees)
$unpaired = @($records | Where-Object { -not $_.Paired })

if ($UpdateBaseline) {
    . (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')
    . (Join-Path $PSScriptRoot 'lib/baseline-set-writer.ps1')
    $allowRows = @(Read-AllowlistRow -Path $AllowlistPath)
    $allowed = @($allowRows | Where-Object { $_.Reason } | ForEach-Object { $_.Token })
    $frozen = @($unpaired | Where-Object { $allowed -notcontains $_.Key } | ForEach-Object { $_.Key } | Sort-Object)
    $previous = @(Read-AllowlistRow -Path $BaselinePath | ForEach-Object { $_.Token })
    if (-not (Test-BaselineWrite -Gate 'assert-fileop-journal-pairing' -Previous $previous -Current $frozen -Reason $Reason)) {
        exit 2
    }
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path $BaselinePath -Reason 'assert-fileop-journal-pairing.ps1 -UpdateBaseline'
        $header = @(
            '# S3371 froze this list; S3376 decided what it is. Destructive operations in the transfer',
            '# strategy trees that register the mutation journal through their CALLER, not here. One',
            '# "<relative path>#<operation>" token per line.',
            '# A journal entry is keyed by a resource id and this layer addresses a path, so none of these',
            '# can register where it is declared - this is a named structural exclusion, not a queue of',
            '# work. Draining it by threading a resourceId down is refuted in dev/REFUTED_APPROACHES.md.',
            '# This list may SHRINK - when an operation leaves the trees - and may never grow. Regenerate',
            '# only when a new destructive transport operation is deliberately admitted, naming the',
            '# registering caller in the same change. Read the gate header before you do.'
        ) + @(Get-BaselineReasonLine -Reason $Reason)
        ($header + $frozen) | Set-Content -LiteralPath $BaselinePath -Encoding utf8NoBOM
    }
    finally { Exit-CodeLockScope -Scope $scope }
    Write-Host ("assert-fileop-journal-pairing: baseline rewritten - {0} unpaired operation(s) frozen." -f $frozen.Count)
    exit 0
}

$allowlistRows = @(Read-AllowlistRow -Path $AllowlistPath)
$reasonless = @($allowlistRows | Where-Object { -not $_.Reason })
$allowlist = @($allowlistRows | Where-Object { $_.Reason } | ForEach-Object { $_.Token })

$baseline = @()
if (Test-Path -LiteralPath $BaselinePath) {
    $baseline = @(Get-Content -LiteralPath $BaselinePath |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -and -not $_.StartsWith('#') })
}

if ($List) {
    foreach ($group in ($records | Group-Object -Property File | Sort-Object Name)) {
        Write-Host ("  {0}" -f $group.Name)
        foreach ($record in $group.Group) {
            $state = if ($record.Paired) { 'paired' }
            elseif ($allowlist -contains $record.Key) { 'read-only (allowlisted)' }
            elseif ($baseline -contains $record.Key) { 'unpaired (baselined)' }
            else { 'UNPAIRED' }
            Write-Host ("      {0,-24} {1}" -f $record.Operation, $state)
        }
    }
}

$findings = @($unpaired | Where-Object { $allowlist -notcontains $_.Key -and $baseline -notcontains $_.Key })

if ($reasonless.Count -gt 0) {
    Write-Host ("assert-fileop-journal-pairing: FAIL - {0} read-only allowlist row(s) carry no reason:" -f $reasonless.Count) -ForegroundColor Red
    foreach ($row in $reasonless) {
        Write-Host ("  {0}:{1} {2}" -f (Split-Path -Leaf $AllowlistPath), $row.Line, $row.Token) -ForegroundColor Red
    }
    Write-Host "  Write the row as '<path>#<operation>   # why nothing needs undoing'. An opt-out nobody can re-judge is a suppression." -ForegroundColor Red
}

if ($findings.Count -gt 0) {
    Write-Host ("assert-fileop-journal-pairing: FAIL - {0} destructive operation(s) reach neither the mutation journal nor an opt-out:" -f $findings.Count) -ForegroundColor Red
    foreach ($record in $findings) {
        Write-Host ("  {0} declares {1}() and never names MutationJournal" -f $record.File, $record.Operation) -ForegroundColor Red
    }
    Write-Host "  A destructive operation the journal never sees cannot be reconciled and cannot be undone." -ForegroundColor Red
    Write-Host "  Name the caller that records a Mutation for it - this layer has no resource id, so registration happens there (S3376 ADR-1) - or, when it disposes of state the app itself created, add a row with its reason to scripts/quality/fileops-read-only-allowlist.txt." -ForegroundColor Red
}

if ($findings.Count -gt 0 -or $reasonless.Count -gt 0) {
    if ($Gate) { exit 1 }
    exit 0
}

$paired = @($records | Where-Object { $_.Paired })
$allowlisted = @($unpaired | Where-Object { $allowlist -contains $_.Key })
$baselined = @($unpaired | Where-Object { $allowlist -notcontains $_.Key -and $baseline -contains $_.Key })
Write-Host ("assert-fileop-journal-pairing: PASS - {0} destructive declaration(s) across {1} tree(s), {2} paired with the journal, {3} read-only by allowlist, {4} registering above the seam (S3376)." -f `
        $records.Count, $existingTrees.Count, $paired.Count, $allowlisted.Count, $baselined.Count) -ForegroundColor Green
exit 0
