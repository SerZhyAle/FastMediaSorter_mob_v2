#requires -Version 7.0
<#
.SYNOPSIS
    S3438: every ratchet baseline in the repository declares its shape, owner, write path and
    CHECK-BASELINE rule-4 verdict in scripts/quality/baseline-inventory.jsonl.

.DESCRIPTION
    Contract CHECK-BASELINE (automated-checks catalog, section 3) has two shapes: a count ratchet
    (the whole file is a number) and a set baseline (one identifier per line). Rule 4: a count cannot
    see an absorption, so a class whose baseline can be re-frozen wholesale must be a set. The S3438
    audit read the write path of all 112 baselines; this gate keeps that answer current, so a new
    baseline cannot land without saying which shape it is and how it is written.

    Discovery is the union of `git ls-files -co --exclude-standard` and a disk walk of scripts/ and
    config/. The walk exists because two baselines sat under the `*token*` .gitignore blanket and
    were invisible to git (S3438) - a git-only discovery would have declared them absent.

    Refused:
      - a baseline file with no inventory row, and a row whose file is gone;
      - a `shape`, `write` or `verdict` outside its closed set;
      - `verdict: exception` without a `carrier` ticket token and a `reason`;
      - `write: wholesale` with `verdict: safe` - a writer that re-freezes the class in one call is
        exactly what rule 4 forbids, so it is either an exception with a carrier or it is fixed.

    Per ticket under the fixed-input contract (lib/fixed-input-scope.ps1): with -ChangedFiles, a
    finding is charged only when the inventory or the finding's own baseline file is in the set.

.PARAMETER Gate
    Fail-closed: exit 1 when a chargeable finding exists.

.PARAMETER Quiet
    Suppress the PASS line.

.PARAMETER ChangedFiles
    Repo-relative paths the caller changed, comma-joined. Omit for the project-wide verdict.

.NOTES
    Exit codes:
      0 - every baseline is declared and every row is well-formed; or findings without -Gate.
      1 - a chargeable finding and -Gate was passed.
      2 - could not verify: the inventory is missing, unreadable, or holds a line that is not JSON.
      3 - findings exist, but neither the inventory nor any of their files is in -ChangedFiles.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet,
    # `pwsh -File` binds only the first element of a [string[]], so callers comma-join.
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1')

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$inventoryRel = 'scripts/quality/baseline-inventory.jsonl'
$inventoryPath = Join-Path $root $inventoryRel

$shapes = @('count', 'set', 'xml')
$writes = @('down-only', 'seed-only', 'hand-only', 'raise-with-reason', 'guarded', 'wholesale')
$verdicts = @('safe', 'exception')

# An ART baseline profile and a test fixture share the word and are not ratchets.
$notABaseline = '(^|/)(V1|v2_6|spec_v2|dev/archive|PLAN|temp|build)/|\.tests/|/tests?/|/fixtures/|baseline-prof'

function Test-BaselineName([string]$Rel) {
    $name = [System.IO.Path]::GetFileName($Rel)
    return ($name -match 'baseline' -and $name -match '\.(txt|ids|xml)$' -and $Rel -notmatch $notABaseline)
}

if (-not (Test-Path -LiteralPath $inventoryPath)) {
    Write-Error "assert-baseline-inventory: could not verify - $inventoryRel is missing." -ErrorAction Continue
    exit 2
}

$rows = @{}
$findings = [System.Collections.Generic.List[pscustomobject]]::new()
function Add-Finding([string]$File, [string]$Text) { $findings.Add([pscustomobject]@{ File = $File; Text = "${File}: $Text" }) }

$lineNo = 0
foreach ($line in (Get-Content -LiteralPath $inventoryPath -Encoding UTF8)) {
    $lineNo++
    if (-not $line.Trim()) { continue }
    try { $row = $line | ConvertFrom-Json }
    catch {
        Write-Error "assert-baseline-inventory: could not verify - $inventoryRel line $lineNo is not JSON." -ErrorAction Continue
        exit 2
    }
    $file = [string]$row.file
    if (-not $file) { Add-Finding $inventoryRel "line $lineNo has no 'file'"; continue }
    if ($rows.ContainsKey($file)) { Add-Finding $file 'declared twice'; continue }
    $rows[$file] = $row
    $props = $row.PSObject.Properties.Name
    if ([string]$row.shape -notin $shapes) { Add-Finding $file "shape '$($row.shape)' is not one of $($shapes -join '|')" }
    if ([string]$row.write -notin $writes) { Add-Finding $file "write '$($row.write)' is not one of $($writes -join '|')" }
    if ([string]$row.verdict -notin $verdicts) { Add-Finding $file "verdict '$($row.verdict)' is not one of $($verdicts -join '|')" }
    if (-not ('owner' -in $props -and [string]$row.owner)) { Add-Finding $file "no 'owner'" }
    if ($row.verdict -eq 'exception') {
        $carrier = if ('carrier' -in $props) { [string]$row.carrier } else { '' }
        $reason = if ('reason' -in $props) { [string]$row.reason } else { '' }
        if ($carrier -notmatch '^S\d{4}$') { Add-Finding $file "exception without a 'carrier' ticket id" }
        if (-not $reason.Trim()) { Add-Finding $file "exception without a 'reason'" }
    }
    if ($row.write -eq 'wholesale' -and $row.verdict -eq 'safe') {
        Add-Finding $file "declares a wholesale writer as safe - fix the writer or record an exception with a carrier (CHECK-BASELINE rule 4)"
    }
}

$discovered = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$gitList = @(& git -C $root ls-files -co --exclude-standard 2>$null)
if ($LASTEXITCODE -ne 0) {
    Write-Error 'assert-baseline-inventory: could not verify - git ls-files failed.' -ErrorAction Continue
    exit 2
}
foreach ($rel in $gitList) { if (Test-BaselineName $rel) { [void]$discovered.Add($rel) } }
foreach ($dir in 'scripts', 'config') {
    $abs = Join-Path $root $dir
    if (-not (Test-Path -LiteralPath $abs)) { continue }
    foreach ($f in (Get-ChildItem -LiteralPath $abs -Recurse -File -Filter '*baseline*')) {
        $rel = [System.IO.Path]::GetRelativePath($root, $f.FullName) -replace '\\', '/'
        if (Test-BaselineName $rel) { [void]$discovered.Add($rel) }
    }
}

foreach ($rel in $discovered) {
    if (-not $rows.ContainsKey($rel)) { Add-Finding $rel "has no row in $inventoryRel - declare its shape, owner, write path and verdict" }
}
foreach ($rel in $rows.Keys) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $rel))) { Add-Finding $rel "is declared in $inventoryRel but does not exist" }
}

if ($findings.Count -eq 0) {
    if (-not $Quiet) {
        $exceptions = @($rows.Values | Where-Object { $_.verdict -eq 'exception' }).Count
        Write-Host ("assert-baseline-inventory: PASS - {0} baseline(s) declared, {1} on a dated exception." -f $rows.Count, $exceptions)
    }
    exit 0
}

$inputs = @($inventoryRel) + @($findings | ForEach-Object { $_.File })
if (-not (Test-FixedInputsChargeable -ChangedFiles $ChangedFiles -InputPaths $inputs)) {
    Write-NotChargedVerdict -GateName 'assert-baseline-inventory' -Findings @($findings | ForEach-Object { $_.Text })
    exit 3
}

Write-Error ("assert-baseline-inventory: FAIL - " + $findings.Count + " finding(s):`n" +
    ((@($findings | ForEach-Object { $_.Text }) | Sort-Object -Unique) -join "`n")) -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
