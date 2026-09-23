#requires -Version 7.0
<#
.SYNOPSIS
    Gate: a component the framework starts, outside the surface tree, that changes user content must
    either reach the mutation journal or carry a registry row saying how the change reaches the screen.

.DESCRIPTION
    S3386, carrying the design question S3376 refused to answer inside its own scope.

    S3371 installed assert-fileop-journal-pairing.ps1 over the transport layer, and S3376 then found
    that layer structurally unable to journal: an entry is keyed by a resourceId and data/transfer/
    addresses a path and credentials. Registration belongs to the CALLER - and no gate watched the
    caller. Measured 2026-09-22, "every file outside the two transfer trees that reaches a destructive
    transport operation" is 36 files of which 35 are not defects, so a gate over that subject would
    freeze 35 rows and teach nothing.

    SUBJECT. Not "a file that deletes" but "a file that deletes and is NOT the surface that will
    redraw". A caller under ui/ belongs to a surface by construction - the Browse managers are
    compensated by a full reloadFiles() on the transfer worker's terminal event, so a journal entry
    there would be redundant rather than missing. What has no surface at all is a component the
    FRAMEWORK starts: a Worker, a Service, a BroadcastReceiver, a widget provider, a FileObserver.
    Nothing redraws because one of those ran. So the subject is a .kt file under $script:ScanTree,
    outside $script:SurfaceTrees, declaring a class whose supertype list names one of
    $script:EntryPointTypes, that changes user content.

    CHANGES USER CONTENT. The file matches $script:MutationRx, or one of its DECLARED COLLABORATORS
    does - a constructor `val` parameter or an `@Inject lateinit var` field, resolved by class name to
    a file in the same tree. One hop, and only through dependencies the class declares: an incidental
    import is not a collaborator, and a deeper walk would pull in most of the tree. Comments are
    stripped before every match, because a line that merely NAMES FileOperation.Delete in prose is not
    a mutation (DetectDuplicatesUseCase says exactly that, and matched a naive grep).

    REGISTERED. The file, or one of those same collaborators, names MutationRecorder or
    MutationJournal. Proven from source, so a producer that journals needs no registry row at all -
    the registry holds exceptions only, which is what keeps it the size of the live set.

    THE REGISTRY, scripts/quality/mutation-producer-registry.txt. Two dispositions, both authored,
    both re-judgeable:

      refresh:<Class.member> - the change reaches the screen by a compensating refresh instead of the
      journal. The gate resolves <Class> to a file in the tree and requires <member> to appear in it,
      so a refresh nobody wired cannot be claimed.

      own-state - the producer deletes only what the app itself created: a temp file, a cache entry,
      its own expired trash snapshot. There is nothing for the journal to undo.

    Every row carries a reason and a row without one is refused - the same rule the read-only allowlist
    of assert-fileop-journal-pairing.ps1 applies to its own, for the same reason: an opt-out nobody can
    re-judge in a year is how a registry turns into a suppression list.

    WHAT THIS GATE DOES NOT DO. It does not close a gap. It makes every surfaceless producer DECLARED,
    and the registry row is where the next reader re-judges one. It also does not overlap
    assert-fileop-journal-pairing.ps1: that gate judges destructive declarations below the seam, this
    one judges producers above it.

.PARAMETER Gate
    Exit 1 when a surfaceless mutation producer neither reaches the journal nor carries a valid
    registry row. Without it the script only reports.

.PARAMETER List
    Print every subject and its state.

.PARAMETER RepoRoot
    Repository root to scan. Defaults to the parent of scripts/quality. The contract suite points it at
    a fixture tree so the refusals really execute.

.PARAMETER RegistryPath
    Read this registry instead of the one beside this script. For the suite.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-mutation-producer-registration.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-mutation-producer-registration.ps1 -List

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every surfaceless producer journals or carries a valid registry row (or reporting only).
      1  under -Gate: an unregistered producer, a reasonless row, or a refresh: row naming a member
         that does not exist in the class it names.
      2  cannot verify - the scanned tree does not exist under -RepoRoot.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$List,
    [string]$RepoRoot,
    [string]$RegistryPath,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }
if (-not $RegistryPath) { $RegistryPath = Join-Path $PSScriptRoot 'mutation-producer-registry.txt' }

$script:ScanTree = 'app_v2/src/main/java/com/sza/fastmediasorter'

# The surface tree. Everything here belongs to a Fragment or an Activity that can redraw itself, so a
# mutation made from it is answered by its own refresh - that is the whole reason the subject is narrow.
$script:SurfaceTrees = @('ui/')

# Base types the Android framework instantiates and runs with no screen of the app's own attached.
$script:EntryPointTypes = @(
    'CoroutineWorker', 'ListenableWorker', 'Worker',
    'BroadcastReceiver', 'WearableListenerService', 'LifecycleService', 'JobIntentService',
    'JobService', 'TileService', 'AppWidgetProvider', 'FileObserver', 'Service'
)

# A call that changes user content on disk: the domain operation types, the transport verbs, and the
# raw java.io forms a component reaches for when it does it by hand.
$script:MutationRx = [regex]('FileOperation\.(?:Delete|Move|Rename)\b' +
    '|\.deleteFile\(|\.moveFile\(|\.renameFile\(' +
    '|executeDeleteDirectory|executeMoveDirectory|executeRenameDirectory' +
    '|moveToTrash|\.delete\(\)|\.deleteRecursively\(|\.renameTo\(')

$script:RecorderRx = [regex]'MutationRecorder|MutationJournal'

$script:EntryPointRx = [regex]('(?m)^\s*(?:(?:abstract|open|internal|private|sealed|final|data)\s+)*class\s+[A-Za-z0-9_]+[^{:]*:\s*[^{]*\b(?:' +
    ($script:EntryPointTypes -join '|') + ')\b')

# Declared dependencies only: a constructor `val` parameter or an @Inject field. An incidental import
# is not a collaborator - the class never asked for it, and treating it as one pulls in half the tree.
$script:CollaboratorRx = [regex]('(?m)^\s*(?:@[A-Za-z0-9_]+\s+)*(?:private\s+|internal\s+)?val\s+[A-Za-z0-9_]+\s*:\s*([A-Za-z0-9_]+)' +
    '|(?m)^\s*@Inject\s+lateinit\s+var\s+[A-Za-z0-9_]+\s*:\s*([A-Za-z0-9_]+)')

$script:LineCommentRx = [regex]'(?m)//.*$'
$script:BlockCommentRx = [regex]'(?s)/\*.*?\*/'

function Remove-KotlinComment {
    <#
        Strip block then line comments. The gate matches vocabulary, and a comment naming a mutation is
        prose about one, not one - DetectDuplicatesUseCase.kt line 133 is exactly that case.
    #>
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    $stripped = $script:BlockCommentRx.Replace($Text, ' ')
    return $script:LineCommentRx.Replace($stripped, ' ')
}

function Get-SourceIndex {
    <#
        One pass over the tree: comment-stripped text per relative path, and a class-name -> path map
        used to resolve a declared collaborator in one hop.
    #>
    param([Parameter(Mandatory)][string]$TreeFull)

    $texts = @{}
    $byClass = @{}
    foreach ($file in Get-ChildItem -LiteralPath $TreeFull -Recurse -Filter '*.kt' -File) {
        $relative = $file.FullName.Substring($TreeFull.Length).TrimStart('\', '/').Replace('\', '/')
        $raw = Get-Content -LiteralPath $file.FullName -Raw
        if (-not $raw) { $raw = '' }
        $texts[$relative] = Remove-KotlinComment -Text $raw
        $byClass[[System.IO.Path]::GetFileNameWithoutExtension($file.Name)] = $relative
    }
    return [pscustomobject]@{ Texts = $texts; ByClass = $byClass }
}

function Get-DeclaredCollaborator {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
        [Parameter(Mandatory)][hashtable]$ByClass
    )

    $names = [System.Collections.Generic.List[string]]::new()
    foreach ($match in $script:CollaboratorRx.Matches($Text)) {
        $name = if ($match.Groups[1].Success) { $match.Groups[1].Value } else { $match.Groups[2].Value }
        if ($name -and $ByClass.ContainsKey($name) -and -not $names.Contains($name)) { $names.Add($name) }
    }
    return $names
}

function Get-Subject {
    <#
        One record per surfaceless producer that changes user content. Wrapped in @( ) by the caller:
        an empty List[T] unrolls to $null on return and $null.Count throws under StrictMode.
    #>
    param([Parameter(Mandatory)][pscustomobject]$Index)

    $records = [System.Collections.Generic.List[object]]::new()

    foreach ($relative in ($Index.Texts.Keys | Sort-Object)) {
        $skip = $false
        foreach ($surface in $script:SurfaceTrees) { if ($relative.StartsWith($surface)) { $skip = $true } }
        if ($skip) { continue }

        $text = $Index.Texts[$relative]
        if (-not $script:EntryPointRx.IsMatch($text)) { continue }

        $collaborators = Get-DeclaredCollaborator -Text $text -ByClass $Index.ByClass
        $mutates = $script:MutationRx.IsMatch($text)
        $records1 = $script:RecorderRx.IsMatch($text)
        $through = ''

        foreach ($name in $collaborators) {
            $collaboratorText = $Index.Texts[$Index.ByClass[$name]]
            if (-not $collaboratorText) { continue }
            if (-not $mutates -and $script:MutationRx.IsMatch($collaboratorText)) {
                $mutates = $true
                $through = $name
            }
            if (-not $records1 -and $script:RecorderRx.IsMatch($collaboratorText)) { $records1 = $true }
        }

        if (-not $mutates) { continue }

        $records.Add([pscustomobject]@{
                File     = $relative
                Through  = $through
                Records  = $records1
            })
    }

    return $records
}

function Read-RegistryRow {
    <#
        Rows are "<relative path>  <disposition>  # <reason>". The reason is mandatory, so a malformed
        row is returned rather than dropped - an opt-out that fails to parse must be visible, not absent.
    #>
    param([Parameter(Mandatory)][string]$Path)

    $rows = [System.Collections.Generic.List[object]]::new()
    if (-not (Test-Path -LiteralPath $Path)) { return $rows }

    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $Path) {
        $lineNumber++
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }

        $match = [regex]::Match($trimmed, '^(?<file>\S+)\s+(?<disposition>\S+)\s+#\s*(?<reason>\S.*)$')
        if ($match.Success) {
            $rows.Add([pscustomobject]@{
                    File        = $match.Groups['file'].Value
                    Disposition = $match.Groups['disposition'].Value
                    Reason      = $match.Groups['reason'].Value.Trim()
                    Line        = $lineNumber
                })
        }
        else {
            $fields = $trimmed -split '\s+'
            $rows.Add([pscustomobject]@{
                    File        = $fields[0]
                    Disposition = if ($fields.Count -gt 1) { $fields[1] } else { '' }
                    Reason      = ''
                    Line        = $lineNumber
                })
        }
    }

    return $rows
}

function Test-RefreshRow {
    <#
        A refresh: row claims a compensating redraw. Resolve <Class> in the tree and require <member> to
        appear in it - an unwired refresh is exactly the claim this gate exists to stop being free.
        Returns the reason it is invalid, or an empty string when it holds.
    #>
    param(
        [Parameter(Mandatory)][string]$Disposition,
        [Parameter(Mandatory)][pscustomobject]$Index
    )

    $target = $Disposition.Substring('refresh:'.Length)
    if ($target -notmatch '^([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)$') {
        return "disposition '$Disposition' is not of the form refresh:<Class>.<member>"
    }
    $className = $Matches[1]
    $member = $Matches[2]
    if (-not $Index.ByClass.ContainsKey($className)) {
        return "refresh: names class '$className', which no file in the scanned tree declares"
    }
    $classText = $Index.Texts[$Index.ByClass[$className]]
    if ($classText -notmatch [regex]::Escape($member)) {
        return "refresh: names '$className.$member', and '$member' does not appear in $($Index.ByClass[$className])"
    }
    return ''
}

$treeFull = Join-Path $RepoRoot $script:ScanTree
if (-not (Test-Path -LiteralPath $treeFull)) {
    [Console]::Error.WriteLine("assert-mutation-producer-registration: cannot verify - the scanned tree does not exist under '$RepoRoot' (looked for: $($script:ScanTree)).")
    exit 2
}

$index = Get-SourceIndex -TreeFull (Resolve-Path -LiteralPath $treeFull).Path
$subjects = @(Get-Subject -Index $index)
$registryRows = @(Read-RegistryRow -Path $RegistryPath)

$reasonless = @($registryRows | Where-Object { -not $_.Reason })
$valid = @($registryRows | Where-Object { $_.Reason })
$byFile = @{}
foreach ($row in $valid) { $byFile[$row.File] = $row }

$badRefresh = [System.Collections.Generic.List[object]]::new()
foreach ($row in $valid) {
    if ($row.Disposition -eq 'own-state') { continue }
    if (-not $row.Disposition.StartsWith('refresh:')) {
        $badRefresh.Add([pscustomobject]@{ Row = $row; Problem = "unknown disposition '$($row.Disposition)' - use own-state or refresh:<Class>.<member>" })
        continue
    }
    $problem = Test-RefreshRow -Disposition $row.Disposition -Index $index
    if ($problem) { $badRefresh.Add([pscustomobject]@{ Row = $row; Problem = $problem }) }
}

if ($List) {
    foreach ($subject in $subjects) {
        $state = if ($subject.Records) { 'journals' }
        elseif ($byFile.ContainsKey($subject.File)) { "registry: $($byFile[$subject.File].Disposition)" }
        else { 'UNREGISTERED' }
        $suffix = if ($subject.Through) { " (via $($subject.Through))" } else { '' }
        Write-Host ("  {0,-62} {1}{2}" -f $subject.File, $state, $suffix)
    }
}

$findings = @($subjects | Where-Object { -not $_.Records -and -not $byFile.ContainsKey($_.File) })

if ($reasonless.Count -gt 0) {
    Write-Host ("assert-mutation-producer-registration: FAIL - {0} registry row(s) carry no reason:" -f $reasonless.Count) -ForegroundColor Red
    foreach ($row in $reasonless) {
        Write-Host ("  {0}:{1} {2}" -f (Split-Path -Leaf $RegistryPath), $row.Line, $row.File) -ForegroundColor Red
    }
    Write-Host "  Write the row as '<path>  <disposition>  # why the screen still ends up correct'. An opt-out nobody can re-judge is a suppression." -ForegroundColor Red
}

if ($badRefresh.Count -gt 0) {
    Write-Host ("assert-mutation-producer-registration: FAIL - {0} registry row(s) claim a compensation that does not hold:" -f $badRefresh.Count) -ForegroundColor Red
    foreach ($bad in $badRefresh) {
        Write-Host ("  {0}:{1} {2} - {3}" -f (Split-Path -Leaf $RegistryPath), $bad.Row.Line, $bad.Row.File, $bad.Problem) -ForegroundColor Red
    }
}

if ($findings.Count -gt 0) {
    Write-Host ("assert-mutation-producer-registration: FAIL - {0} surfaceless producer(s) change user content and neither journal nor declare how the screen stays correct:" -f $findings.Count) -ForegroundColor Red
    foreach ($subject in $findings) {
        $how = if ($subject.Through) { " (through $($subject.Through))" } else { '' }
        Write-Host ("  {0}{1}" -f $subject.File, $how) -ForegroundColor Red
    }
    Write-Host "  Nothing redraws because a Worker, a Service or a receiver ran, so an open Browse list keeps a row whose file is gone." -ForegroundColor Red
    Write-Host "  Record the change through MutationRecorder, or add a row to scripts/quality/mutation-producer-registry.txt:" -ForegroundColor Red
    Write-Host "    '<path>  refresh:<Class>.<member>  # the compensating refresh that redraws instead'" -ForegroundColor Red
    Write-Host "    '<path>  own-state  # it deletes only what the app itself created, so there is nothing to undo'" -ForegroundColor Red
}

if ($findings.Count -gt 0 -or $reasonless.Count -gt 0 -or $badRefresh.Count -gt 0) {
    if ($Gate) { exit 1 }
    exit 0
}

$journalling = @($subjects | Where-Object { $_.Records })
$refreshed = @($valid | Where-Object { $_.Disposition.StartsWith('refresh:') })
$ownState = @($valid | Where-Object { $_.Disposition -eq 'own-state' })
Write-Host ("assert-mutation-producer-registration: PASS - {0} surfaceless producer(s) change user content, {1} reach the journal, {2} declare a compensating refresh, {3} touch only app-owned state." -f `
        $subjects.Count, $journalling.Count, $refreshed.Count, $ownState.Count) -ForegroundColor Green
exit 0
