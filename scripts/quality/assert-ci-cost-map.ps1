<#
.SYNOPSIS
    S3084 - CI cost map vs .github/workflows parity gate.

.DESCRIPTION
    docs/BUILD_VS_RELEASE.md carries the CI cost map: one table row per GitHub Actions workflow,
    naming its job keys, its trigger events and branches, and a Yes/No answer to "fires on a
    DEBUG-v0NN push?". Every consumer that needs to know what a push costs is routed to that table,
    and until this gate its accuracy rested entirely on a prose promise in the Maintenance section
    below it - which is not read by the ticket that edits a workflow file.

    That promise was broken three times, each time silently, each time in the same shape: a file
    under .github/workflows/ changed, the doc did not, and no run of any kind reported it.
      - android-ci.yml gained `branches: [ main, 'DEBUG-v*' ]` on its push trigger and the map kept
        answering No in the fires-on-DEBUG column. The inverted fact then propagated into four
        command drivers, one of them (.claude/commands/build.md) as a working instruction. S3082
        corrected the statements; this gate is what stops them going wrong again.
      - static-gates was added by S2874 and the row's job list did not move.
      - verify-wear was added by S3074 and the row's job list did not move.

    Checks, all fatal under -Gate:
      row       a workflow file has no table row, or a row names a file that does not exist
      jobs      the top-level keys under `jobs:` differ from the backticked job keys in column 1
      trigger   an event in `on:` is not stated in the trigger cell
      branch    a push branch is not named in the trigger cell
      debug     the DEBUG column disagrees with the push branch list it is derived from

    Deliberately NOT checked, because a gate that over-reaches is a gate that gets switched off:
      - Path filters. The three rows render them three different ways - one lists all seven globs,
        one summarises them as prose, one omits them - and gating that forces every row into a list
        format the table has no room for.
      - The prose in "What a DEBUG push actually costs". It is an argument, not a fact table.
      - Repository visibility, on which the zero-bill conclusion actually rests (S3082). Checking it
        needs a network call to the GitHub API, which no gate in this repository makes; a gate that
        fails when the network does is one that gets switched off. It stays a human re-check in the
        Maintenance prose.

    No YAML module is available to PowerShell here, so the workflow parser is indentation-aware over
    the raw lines: top-level blocks at column 0, event and job keys at exactly two spaces.

.PARAMETER Gate
    Exit non-zero on any finding. Without it the script reports and exits 0, which is the read-only
    mode used while correcting the table.

.PARAMETER Quiet
    Suppress the per-workflow progress lines. Findings are always printed.

.PARAMETER ChangedFiles
    S2824: repo-relative paths of the files the caller changed, comma-joined. Supplying it lets the
    gate decline to charge a divergence when none of the files it declares as an input is among
    them. Omit it - as assert-fast-gates.ps1 and the release path do - and every finding stays fatal.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-ci-cost-map.ps1
    pwsh -NoProfile -File scripts/quality/assert-ci-cost-map.ps1 -Gate -Quiet

.NOTES
    Exit codes:
      0  no findings (or findings reported without -Gate)
      1  -Gate and at least one finding
      2  could not verify: the workflow directory or docs/BUILD_VS_RELEASE.md is missing or
         unreadable, or the cost-map table cannot be located by its header row
      3  S2824: a divergence stands in the tree but no file this gate declares as an input is in
         -ChangedFiles, so it is not attributable to this run. The findings are printed. Distinct
         from 1 because the caller cannot fix it and from 0 because something IS wrong in the tree.
#>
[CmdletBinding()]
param(
    [switch] $Gate,
    [switch] $Quiet,
    # S1184/S1340: `pwsh -File` binds only the first element of a [string[]] and rejects the rest as
    # positional args, so callers comma-join and Expand-ChangedFiles splits it back.
    [string[]] $ChangedFiles,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

# S2824: the chargeability test, shared with the other fixed-input gates.
. (Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1')

$workflowDir = Join-Path $RepoRoot '.github/workflows'
$docPath = Join-Path $RepoRoot 'docs/BUILD_VS_RELEASE.md'

# A branch name of the shape the DEBUG column asks about. The column's question is literal
# ("fires on a DEBUG-v0NN push?"), so the derivation tests a representative name against each
# declared branch pattern rather than pattern-matching the pattern.
$debugBranchSample = 'DEBUG-v037'

function Write-Info([string] $Message) {
    if (-not $Quiet) { Write-Host $Message }
}

function Fail-Unverifiable([string] $Message) {
    Write-Error "assert-ci-cost-map: $Message" -ErrorAction Continue
    exit 2
}

# Lines of the column-0 block introduced by $Key, excluding the key line itself. A block ends at the
# next line starting in column 0 that is neither blank nor a comment.
function Get-TopLevelBlock([string[]] $Lines, [string[]] $Keys) {
    $start = -1
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        foreach ($key in $Keys) {
            # YAML lets `on:` be quoted, and some linters insist on it because bare `on` is a boolean.
            if ($Lines[$i] -match ("^[`"']?" + [regex]::Escape($key) + "[`"']?\s*:")) { $start = $i; break }
        }
        if ($start -ge 0) { break }
    }
    if ($start -lt 0) { return @() }

    $block = [System.Collections.Generic.List[string]]::new()
    for ($i = $start + 1; $i -lt $Lines.Count; $i++) {
        $line = $Lines[$i]
        if ($line -match '^\S' -and $line -notmatch '^\s*#') { break }
        $block.Add($line)
    }
    return $block.ToArray()
}

# Keys at exactly two spaces of indentation inside an already-extracted top-level block.
function Get-ChildKeys([string[]] $Block) {
    $keys = [System.Collections.Generic.List[string]]::new()
    foreach ($line in $Block) {
        if ($line -match '^  ([A-Za-z0-9_-]+)\s*:') { $keys.Add($Matches[1]) }
    }
    return $keys.ToArray()
}

# The `branches:` list belonging to one event inside the `on:` block, in either the inline
# (`[ main, 'DEBUG-v*' ]`) or the block (`- item`) form.
function Get-EventBranches([string[]] $OnBlock, [string] $Event) {
    $inEvent = $false
    $branches = [System.Collections.Generic.List[string]]::new()

    for ($i = 0; $i -lt $OnBlock.Count; $i++) {
        $line = $OnBlock[$i]

        if ($line -match '^  ([A-Za-z0-9_-]+)\s*:') {
            $inEvent = ($Matches[1] -eq $Event)
            continue
        }
        if (-not $inEvent) { continue }

        if ($line -match '^\s{4}branches\s*:\s*(.*)$') {
            $inline = $Matches[1].Trim()
            if ($inline -match '^\[(.*)\]$') {
                foreach ($item in ($Matches[1] -split ',')) {
                    $value = $item.Trim().Trim("'", '"')
                    if ($value) { $branches.Add($value) }
                }
            } else {
                for ($j = $i + 1; $j -lt $OnBlock.Count; $j++) {
                    if ($OnBlock[$j] -match '^\s{6,}-\s*(.+)$') {
                        $branches.Add($Matches[1].Trim().Trim("'", '"'))
                        continue
                    }
                    if ($OnBlock[$j] -match '^\s*$') { continue }
                    break
                }
            }
            break
        }
    }
    return $branches.ToArray()
}

function Get-BacktickedTokens([string] $Text) {
    $tokens = [System.Collections.Generic.List[string]]::new()
    foreach ($match in [regex]::Matches($Text, '`([^`]+)`')) { $tokens.Add($match.Groups[1].Value) }
    return $tokens.ToArray()
}

function Test-GlobMatchesDebugBranch([string] $Pattern) {
    $regex = '^' + ([regex]::Escape($Pattern) -replace '\\\*', '.*') + '$'
    return $debugBranchSample -match $regex
}

if (-not (Test-Path -LiteralPath $workflowDir)) { Fail-Unverifiable "workflow directory not found: $workflowDir" }
if (-not (Test-Path -LiteralPath $docPath)) { Fail-Unverifiable "cost-map document not found: $docPath" }

# --- side one: the workflow files -------------------------------------------------------------

$workflowFacts = @{}
$workflowFiles = @(Get-ChildItem -LiteralPath $workflowDir -Filter '*.yml' -File | Sort-Object Name)
if ($workflowFiles.Count -eq 0) { Fail-Unverifiable "no *.yml workflow files under $workflowDir" }

foreach ($file in $workflowFiles) {
    $lines = @(Get-Content -LiteralPath $file.FullName)
    $onBlock = Get-TopLevelBlock -Lines $lines -Keys @('on')
    $jobsBlock = Get-TopLevelBlock -Lines $lines -Keys @('jobs')

    $workflowFacts[$file.Name] = [pscustomobject]@{
        Name       = $file.Name
        Events     = @(Get-ChildKeys -Block $onBlock)
        PushBr     = @(Get-EventBranches -OnBlock $onBlock -Event 'push')
        PrBr       = @(Get-EventBranches -OnBlock $onBlock -Event 'pull_request')
        Jobs       = @(Get-ChildKeys -Block $jobsBlock)
    }
    Write-Info ("  {0}: events [{1}], jobs [{2}]" -f $file.Name,
        ($workflowFacts[$file.Name].Events -join ', '), ($workflowFacts[$file.Name].Jobs -join ', '))
}

# --- side two: the cost-map table -------------------------------------------------------------

$docLines = @(Get-Content -LiteralPath $docPath)
$headerIndex = -1
for ($i = 0; $i -lt $docLines.Count; $i++) {
    # Located by its header row, never by line number: the surrounding prose grows (it did in S3082)
    # and a line-number anchor would silently start reading the wrong table.
    if ($docLines[$i] -match '^\|\s*Workflow\s*\|\s*Triggers\s*\|') { $headerIndex = $i; break }
}
if ($headerIndex -lt 0) { Fail-Unverifiable "cost-map table header row not found in $docPath" }

$rows = @{}
for ($i = $headerIndex + 2; $i -lt $docLines.Count; $i++) {
    if ($docLines[$i] -notmatch '^\|') { break }
    $cells = @($docLines[$i] -split '\|')
    if ($cells.Count -lt 5) { continue }

    $first = $cells[1]
    $fileToken = @(Get-BacktickedTokens -Text $first) | Where-Object { $_ -like '*.yml' } | Select-Object -First 1
    if (-not $fileToken) { continue }

    $rows[$fileToken] = [pscustomobject]@{
        DeclaredJobs = @(Get-BacktickedTokens -Text $first | Where-Object { $_ -notlike '*.yml' })
        Triggers     = $cells[2]
        Debug        = ($cells[3] -replace '\*\*', '').Trim()
    }
}
if ($rows.Count -eq 0) { Fail-Unverifiable "cost-map table has no parsable data rows in $docPath" }

# --- the five checks ---------------------------------------------------------------------------

$findings = [System.Collections.Generic.List[string]]::new()

foreach ($name in ($rows.Keys | Sort-Object)) {
    if (-not $workflowFacts.ContainsKey($name)) {
        $findings.Add("row | $name has a cost-map row but no such file under .github/workflows - drop the row or restore the workflow.")
    }
}

foreach ($name in ($workflowFacts.Keys | Sort-Object)) {
    $facts = $workflowFacts[$name]
    if (-not $rows.ContainsKey($name)) {
        $findings.Add("row | $name has no cost-map row in docs/BUILD_VS_RELEASE.md - add one naming its jobs, its triggers and whether it fires on a DEBUG push.")
        continue
    }
    $row = $rows[$name]

    $missingJobs = @($facts.Jobs | Where-Object { $_ -notin $row.DeclaredJobs })
    $staleJobs = @($row.DeclaredJobs | Where-Object { $_ -notin $facts.Jobs })
    if ($missingJobs.Count -gt 0) {
        $findings.Add("jobs | $name declares job(s) [$($missingJobs -join ', ')] the cost-map row does not name - add them to the row.")
    }
    if ($staleJobs.Count -gt 0) {
        $findings.Add("jobs | the $name row names job(s) [$($staleJobs -join ', ')] the workflow no longer declares - remove them from the row.")
    }

    if ('push' -in $facts.Events -and $row.Triggers -notmatch '(?i)\bpush\b') {
        $findings.Add("trigger | $name fires on push and the cost-map row does not say so - state the push trigger in the Triggers cell.")
    }
    if ('workflow_dispatch' -in $facts.Events -and $row.Triggers -notmatch '(?i)manual dispatch') {
        $findings.Add("trigger | $name declares workflow_dispatch and the cost-map row does not say 'manual dispatch' - add it to the Triggers cell.")
    }
    foreach ($branch in $facts.PrBr) {
        if ($row.Triggers -notmatch ("(?i)PR to ``" + [regex]::Escape($branch) + '`')) {
            $findings.Add("trigger | $name fires on a pull request to '$branch' and the cost-map row does not say 'PR to ``$branch``' - add it to the Triggers cell.")
        }
    }

    foreach ($branch in $facts.PushBr) {
        if ($row.Triggers -notmatch ('`' + [regex]::Escape($branch) + '`')) {
            $findings.Add("branch | $name pushes on branch '$branch' and the cost-map row does not name it - add ``$branch`` to the Triggers cell.")
        }
    }

    $expectedDebug = if (@($facts.PushBr | Where-Object { Test-GlobMatchesDebugBranch -Pattern $_ }).Count -gt 0) { 'Yes' } else { 'No' }
    if ($row.Debug -ne $expectedDebug) {
        $findings.Add("debug | $name push branches [$($facts.PushBr -join ', ')] make the DEBUG column '$expectedDebug', the row says '$($row.Debug)' - correct the column.")
    }
}

# --- verdict -------------------------------------------------------------------------------------

if ($findings.Count -eq 0) {
    Write-Info ("assert-ci-cost-map: PASS - {0} workflow(s) agree with the cost map in docs/BUILD_VS_RELEASE.md." -f $workflowFiles.Count)
    exit 0
}

$declaredInputs = @($workflowFiles | ForEach-Object { $_.FullName }) + @($docPath)
if (-not (Test-FixedInputsChargeable -ChangedFiles $ChangedFiles -InputPaths $declaredInputs)) {
    Write-NotChargedVerdict -GateName 'assert-ci-cost-map' -Findings $findings
    exit 3
}

Write-Host ("assert-ci-cost-map: FAIL - {0} divergence(s) between .github/workflows and the CI cost map in docs/BUILD_VS_RELEASE.md." -f $findings.Count) -ForegroundColor Red
Write-Host "  The workflow file is the truth; the table is what moves." -ForegroundColor Red
foreach ($finding in ($findings | Sort-Object -Unique)) { Write-Host ("  {0}" -f $finding) -ForegroundColor Red }
if ($Gate) { exit 1 }
exit 0
