#requires -Version 7.0
<#
.SYNOPSIS
    S3556: report the state of a whole-tree audit campaign - coverage, slice statuses, severity totals, spawned tickets.

.DESCRIPTION
    A campaign of many slice tickets is not read ticket by ticket; this script reads the manifest
    of partition-audit-slices.ps1, the catalog and every child's `## Last Audit` block once and
    prints one table plus totals. Its exit code is the campaign's verdict, so the umbrella ticket's
    own closure can rest on it.

    Per slice: the catalog record found by name (or `not created`), the `**Findings:** P0 n · P1 n
    · P2 n · P3 n` line, the number of finding lines whose action is `inline`, and the ids of the
    `**Spawned:**` line. A slice is closed when its status is Verified or Archived.

    Coverage: the shipped Kotlin files are enumerated again with the manifest's own parameters
    and compared with the union of the manifest's files - `uncovered` (in the tree, in no slice),
    `removed` (in a slice, no longer in the tree) and `duplicated` (in two slices). The exclusion
    rules are the same as in partition-audit-slices.ps1 and are kept in step by hand; a change in
    one is a change in both.

    P0/P1 without action: a finding line of severity 0 or 1 whose action is neither `inline` nor
    an id the catalog knows. These block the campaign as long as they exist.

.PARAMETER Manifest
    The JSON manifest (schema audit-slices/1).

.PARAMETER Parent
    The umbrella ticket (S####). Must equal the manifest's parent.

.PARAMETER RepoRoot
    Repository root. Defaults to two levels above this script; the contract suite passes a fixture.

.PARAMETER OutMarkdown
    Optional path for the same report as a Markdown page.

.PARAMETER Json
    Print the summary object as JSON instead of the table.

.PARAMETER Quiet
    Print only the totals, the coverage line and the verdict.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/summarize-audit-slices.ps1 -Manifest temp/S3556/audit-slices.json -Parent S3556

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - campaign closed: every slice Verified or Archived, 0 uncovered, 0 duplicated, no P0/P1 without action.
      3 - campaign open: at least one slice open or not created, an uncovered file, or a P0/P1 without action; the report is still written.
      2 - cannot verify: the manifest, the catalog or a child's spec file cannot be read, the schema or the parent does not match, or an unexpected error ended the run.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Manifest,
    [Parameter(Mandatory)][string] $Parent,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $OutMarkdown,
    [switch] $Json,
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

trap {
    Write-Host "summarize-audit-slices: unexpected error - $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}

function Write-Refusal([string] $Text) { Write-Host "summarize-audit-slices: $Text" -ForegroundColor Red }

$closedStatuses = @('Verified', 'Archived')
$modules = @('app_v2', 'wear')

# --- inputs ------------------------------------------------------------------------------------

if ($Parent -notmatch '^S\d{4}$') { Write-Refusal "-Parent '$Parent' is not a ticket id (S####)."; exit 2 }
if (-not (Test-Path -LiteralPath $RepoRoot -PathType Container)) { Write-Refusal "repo root '$RepoRoot' does not exist."; exit 2 }
if (-not (Test-Path -LiteralPath $Manifest -PathType Leaf)) { Write-Refusal "manifest '$Manifest' does not exist."; exit 2 }
try { $manifestObj = Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json }
catch { Write-Refusal "manifest cannot be parsed: $($_.Exception.Message)"; exit 2 }
if (-not ($manifestObj.PSObject.Properties.Name -contains 'schema') -or $manifestObj.schema -cne 'audit-slices/1') { Write-Refusal 'manifest schema is not audit-slices/1.'; exit 2 }
if ([string]$manifestObj.parent -cne $Parent) { Write-Refusal "manifest parent '$($manifestObj.parent)' does not match -Parent '$Parent'."; exit 2 }
$slices = @($manifestObj.slices | Sort-Object -Property { [int]$_.index })
if ($slices.Count -eq 0) { Write-Refusal 'manifest holds no slice.'; exit 2 }
$includeTests = [bool]$manifestObj.params.includeTests
$includeDebug = [bool]$manifestObj.params.includeDebug

$selectScript = Join-Path $RepoRoot 'scripts/spec_catalog/select.ps1'
if (-not (Test-Path -LiteralPath $selectScript -PathType Leaf)) { Write-Refusal "catalog script '$selectScript' is missing."; exit 2 }
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

# --- catalog, read once ------------------------------------------------------------------------

$catalogJson = & $pwshExe -NoProfile -File $selectScript -Format json -IncludeArchived 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { Write-Refusal "the catalog could not be read (select.ps1 exit $LASTEXITCODE): $($catalogJson.Trim())"; exit 2 }
$byName = @{}
$byId = @{}
try {
    foreach ($rec in @(($catalogJson | ConvertFrom-Json))) {
        if (-not $rec) { continue }
        $byName[[string]$rec.name] = $rec
        $byId[[string]$rec.id] = $rec
    }
}
catch { Write-Refusal "the catalog listing could not be parsed: $($_.Exception.Message)"; exit 2 }

# --- per slice ---------------------------------------------------------------------------------

$findingRx = [regex]'^- P([0-3]) · (.+?):(\d+) · L([1-6]) · (.+?) · evidence: (.+?) · action: (.+?)\s*$'
$rows = [System.Collections.Generic.List[object]]::new()
$totals = [ordered]@{ P0 = 0; P1 = 0; P2 = 0; P3 = 0 }
$noAction = [System.Collections.Generic.List[string]]::new()
$spawnedAll = [System.Collections.Generic.List[string]]::new()
$open = 0; $closed = 0

foreach ($slice in $slices) {
    $name = [string]$slice.name
    $status = 'not created'; $id = '-'
    $counts = [ordered]@{ P0 = 0; P1 = 0; P2 = 0; P3 = 0 }
    $inline = 0
    $spawned = @()
    if ($byName.ContainsKey($name)) {
        $rec = $byName[$name]
        $id = [string]$rec.id; $status = [string]$rec.status
        $specPath = Join-Path $RepoRoot (([string]$rec.file) -replace '/', [IO.Path]::DirectorySeparatorChar)
        if (-not (Test-Path -LiteralPath $specPath -PathType Leaf)) { Write-Refusal "spec file of $id ($name) is missing: $($rec.file)"; exit 2 }
        $lines = [IO.File]::ReadAllLines($specPath)
        $auditStart = -1
        for ($i = 0; $i -lt $lines.Length; $i++) { if ($lines[$i] -match '^##\s+Last Audit\s*$') { $auditStart = $i; break } }
        if ($auditStart -ge 0) {
            $inFindings = $false
            for ($i = $auditStart + 1; $i -lt $lines.Length; $i++) {
                $line = $lines[$i]
                if ($line -match '^###\s+Findings\s*$') { $inFindings = $true; continue }
                if ($line -match '^###\s+') { $inFindings = $false }
                $m = [regex]::Match($line, '^\*\*Findings:\*\*\s+P0\s+(\d+)\s+·\s+P1\s+(\d+)\s+·\s+P2\s+(\d+)\s+·\s+P3\s+(\d+)')
                if ($m.Success) {
                    $counts.P0 = [int]$m.Groups[1].Value; $counts.P1 = [int]$m.Groups[2].Value
                    $counts.P2 = [int]$m.Groups[3].Value; $counts.P3 = [int]$m.Groups[4].Value
                    continue
                }
                if ($line -match '^\*\*Spawned:\*\*\s*(.*)$') {
                    $spawned = @([regex]::Matches($Matches[1], 'S\d{4}') | ForEach-Object { $_.Value } | Select-Object -Unique)
                    continue
                }
                if ($inFindings) {
                    $f = $findingRx.Match($line)
                    if (-not $f.Success) { continue }
                    $sev = [int]$f.Groups[1].Value
                    $action = $f.Groups[7].Value.Trim()
                    if ($action -ceq 'inline') { $inline++ }
                    $actionIsTicket = $action -match '^S\d{4}$' -and $byId.ContainsKey($action)
                    if ($sev -le 1 -and $action -cne 'inline' -and -not $actionIsTicket) {
                        $noAction.Add("$id $($f.Groups[2].Value):$($f.Groups[3].Value) P$sev action: $action")
                    }
                }
            }
        }
    }
    $isClosed = $closedStatuses -contains $status
    if ($isClosed) { $closed++ } else { $open++ }
    foreach ($k in @('P0', 'P1', 'P2', 'P3')) { $totals[$k] += [int]$counts[$k] }
    foreach ($s in $spawned) { if (-not $spawnedAll.Contains($s)) { $spawnedAll.Add($s) } }
    $rows.Add([ordered]@{
        index = [int]$slice.index; ticket = $id; name = $name; status = $status
        P0 = $counts.P0; P1 = $counts.P1; P2 = $counts.P2; P3 = $counts.P3
        inline = $inline; spawned = @($spawned)
    })
}

$spawnedByStatus = [ordered]@{}
foreach ($s in $spawnedAll) {
    $st = if ($byId.ContainsKey($s)) { [string]$byId[$s].status } else { 'unknown' }
    if ($spawnedByStatus.Contains($st)) { $spawnedByStatus[$st] += 1 } else { $spawnedByStatus[$st] = 1 }
}

# --- coverage ----------------------------------------------------------------------------------

# The same exclusion rules as partition-audit-slices.ps1 (Get-SourceSetExcluded); kept in step by hand.
function Test-SetExcluded([string] $SetName) {
    if (-not $includeTests -and ($SetName -cmatch '^(test|androidTest|benchmark)' -or $SetName -cmatch '^test[A-Z]')) { return $true }
    if (-not $includeDebug -and ($SetName -ceq 'debug' -or $SetName -cmatch 'Debug$')) { return $true }
    return $false
}

$treePaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($module in $modules) {
    $srcRoot = Join-Path $RepoRoot "$module/src"
    if (-not (Test-Path -LiteralPath $srcRoot -PathType Container)) { continue }
    foreach ($set in @(Get-ChildItem -LiteralPath $srcRoot -Directory)) {
        if (Test-SetExcluded $set.Name) { continue }
        foreach ($langRoot in @('java', 'kotlin')) {
            $root = Join-Path $set.FullName $langRoot
            if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
            foreach ($f in @(Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt')) {
                $relToRoot = $f.FullName.Substring($root.Length).TrimStart('\', '/') -replace '\\', '/'
                [void]$treePaths.Add("$module/src/$($set.Name)/$langRoot/$relToRoot")
            }
        }
    }
}
$manifestPaths = [System.Collections.Generic.Dictionary[string, int]]::new([System.StringComparer]::Ordinal)
foreach ($slice in $slices) {
    foreach ($f in @($slice.files)) {
        $p = [string]$f.path
        if ($manifestPaths.ContainsKey($p)) { $manifestPaths[$p]++ } else { $manifestPaths[$p] = 1 }
    }
}
$uncovered = @($treePaths | Where-Object { -not $manifestPaths.ContainsKey($_) } | Sort-Object)
$removed = @($manifestPaths.Keys | Where-Object { -not $treePaths.Contains($_) } | Sort-Object)
$duplicated = @($manifestPaths.Keys | Where-Object { $manifestPaths[$_] -gt 1 } | Sort-Object)
# A manifest built from -FileList covers only its own list; the tree comparison then says nothing.
$fileListMode = [bool]([string]$manifestObj.params.fileList)
if ($fileListMode) { $uncovered = @(); $removed = @() }
$coverageLine = "Coverage: $($uncovered.Count) uncovered, $($removed.Count) removed, $($duplicated.Count) duplicated$(if ($fileListMode) { ' (file-list manifest: tree comparison skipped)' })"

# --- verdict -----------------------------------------------------------------------------------

$campaignClosed = ($open -eq 0 -and $uncovered.Count -eq 0 -and $duplicated.Count -eq 0 -and $noAction.Count -eq 0)
$verdictWord = if ($campaignClosed) { 'CLOSED' } else { 'OPEN' }
$summary = [ordered]@{
    parent = $Parent; generatedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    slices = $slices.Count; open = $open; closed = $closed
    findings = $totals; inline = ($rows | ForEach-Object { $_.inline } | Measure-Object -Sum).Sum
    spawned = $spawnedAll.Count; spawnedByStatus = $spawnedByStatus
    uncovered = $uncovered.Count; removed = $removed.Count; duplicated = $duplicated.Count
    p0p1WithoutAction = $noAction.Count; verdict = $verdictWord
}

$report = [System.Collections.Generic.List[string]]::new()
$report.Add('| # | Ticket | Name | Status | P0 | P1 | P2 | P3 | Inline | Spawned |')
$report.Add('| ---: | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |')
foreach ($r in $rows) {
    $sp = if ($r.spawned.Count -gt 0) { $r.spawned -join ' ' } else { '-' }
    $report.Add("| $($r.index) | $($r.ticket) | $($r.name) | $($r.status) | $($r.P0) | $($r.P1) | $($r.P2) | $($r.P3) | $($r.inline) | $sp |")
}
$report.Add('')
$report.Add("Slices: $($slices.Count) - closed $closed, open $open")
$report.Add("Findings: P0 $($totals.P0) · P1 $($totals.P1) · P2 $($totals.P2) · P3 $($totals.P3) · inline fixes $($summary.inline)")
$spawnedText = if ($spawnedByStatus.Count -eq 0) { 'none' } else { ($spawnedByStatus.GetEnumerator() | ForEach-Object { "$($_.Key) $($_.Value)" }) -join ', ' }
$report.Add("Spawned tickets: $($spawnedAll.Count) ($spawnedText)")
$report.Add($coverageLine)
foreach ($u in $uncovered) { $report.Add("  uncovered: $u") }
foreach ($d in $duplicated) { $report.Add("  duplicated: $d") }
$report.Add("P0/P1 without action: $(if ($noAction.Count -eq 0) { 'none' } else { $noAction.Count })")
foreach ($n in $noAction) { $report.Add("  $n") }
$report.Add("summarize-audit-slices: campaign $verdictWord")

if ($OutMarkdown) {
    $md = [System.Collections.Generic.List[string]]::new()
    $md.Add("# Campaign roll-up - $Parent")
    $md.Add('')
    $md.Add("**Generated:** $($summary.generatedAt)")
    $md.Add("**Manifest:** $Manifest")
    $md.Add('')
    foreach ($l in $report) { $md.Add($l) }
    $mdDir = Split-Path -Parent $OutMarkdown
    if ($mdDir -and -not (Test-Path -LiteralPath $mdDir)) { New-Item -ItemType Directory -Force -Path $mdDir | Out-Null }
    [IO.File]::WriteAllText($OutMarkdown, (($md -join "`n") + "`n"), [System.Text.UTF8Encoding]::new($false))
}

if ($Json) {
    $summary | ConvertTo-Json -Depth 6
}
elseif ($Quiet) {
    foreach ($l in $report) { if ($l -notmatch '^\|') { Write-Host $l } }
}
else {
    foreach ($l in $report) { Write-Host $l }
}

if ($campaignClosed) { exit 0 }
exit 3
