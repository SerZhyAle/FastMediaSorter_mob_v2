#requires -Version 7.0
<#
.SYNOPSIS
    S3556: create one Tactical child ticket per audit slice from a partition manifest, idempotent by name.

.DESCRIPTION
    The whole-tree audit runs as many independent tickets so the queue runner can work them in
    parallel. This generator turns the manifest written by partition-audit-slices.ps1 into those
    tickets: for each slice it inserts a catalog record through the catalog's own CLI, writes a
    compact spec from .claude/templates/audit-slice-spec.md - strategic part plus two inline phases
    with step markers plan-tick.ps1 reads - and writes the slice's file list as the ticket's first
    research artifact. Status Tactical means "the plan is inside", so /spec-all resumes a child
    straight into /spec-dev.

    Slices are created in manifest order, which is risk order, so the queue rows land in it.
    A slice whose name already exists in the catalog is skipped, so a run interrupted half way is
    finished by running it again and nothing is created twice; a record whose spec or research
    artifact is missing on disk (the run died between the insert and the write) is repaired under
    the id the catalog already holds. A refused insert stops the run at once with exit 1: the
    records already created stay, the re-run skips them.

    No dev-log row is written per child - the campaign's closure writes the one row per ticket.

.PARAMETER Manifest
    The JSON manifest (schema audit-slices/1) written by partition-audit-slices.ps1.

.PARAMETER Parent
    The umbrella ticket (S####). Must equal the manifest's parent field.

.PARAMETER RepoRoot
    Repository root whose catalog receives the tickets. Defaults to two levels above this script;
    the contract suite passes a fixture.

.PARAMETER Template
    The child spec template. Defaults to .claude/templates/audit-slice-spec.md under RepoRoot.

.PARAMETER Tier
    Tier for every child (default 3).

.PARAMETER Priority
    Priority for every child (default 50); the queue's line order governs, not this number.

.PARAMETER Status
    Status the children are inserted with (default Tactical).

.PARAMETER Only
    Create at most this many slices, in manifest order - for a pilot. 0 means all.

.PARAMETER Quiet
    Print only the summary line and refusals.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/fanout-audit-slices.ps1 -Manifest temp/S3556/audit-slices.json -Parent S3556 -WhatIf

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - every slice created or skipped; with -WhatIf, the plan was printed and nothing written.
      1 - the catalog refused an insert or a file could not be written; the run stopped there.
      2 - cannot verify: the manifest, the template or the repo root cannot be read, the schema is
          not audit-slices/1, the parent does not match, or an unexpected error ended the run.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string] $Manifest,
    [Parameter(Mandatory)][string] $Parent,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $Template,
    [int] $Tier = 3,
    [ValidateRange(0, 100)]
    [int] $Priority = 50,
    [string] $Status = 'Tactical',
    [int] $Only = 0,
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

trap {
    Write-Host "fanout-audit-slices: unexpected error - $($_.Exception.Message)" -ForegroundColor Red
    exit 2
}

function Write-Refusal([string] $Text) { Write-Host "fanout-audit-slices: $Text" -ForegroundColor Red }

# --- inputs ------------------------------------------------------------------------------------

if ($Parent -notmatch '^S\d{4}$') { Write-Refusal "-Parent '$Parent' is not a ticket id (S####)."; exit 2 }
if (-not (Test-Path -LiteralPath $RepoRoot -PathType Container)) { Write-Refusal "repo root '$RepoRoot' does not exist."; exit 2 }
if (-not (Test-Path -LiteralPath $Manifest -PathType Leaf)) { Write-Refusal "manifest '$Manifest' does not exist."; exit 2 }
if (-not $Template) { $Template = Join-Path $RepoRoot '.claude/templates/audit-slice-spec.md' }
if (-not (Test-Path -LiteralPath $Template -PathType Leaf)) { Write-Refusal "template '$Template' does not exist."; exit 2 }
$insertScript = Join-Path $RepoRoot 'scripts/spec_catalog/insert.ps1'
$selectScript = Join-Path $RepoRoot 'scripts/spec_catalog/select.ps1'
foreach ($s in @($insertScript, $selectScript)) {
    if (-not (Test-Path -LiteralPath $s -PathType Leaf)) { Write-Refusal "catalog script '$s' is missing."; exit 2 }
}

try { $manifestObj = Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json }
catch { Write-Refusal "manifest cannot be parsed: $($_.Exception.Message)"; exit 2 }
if (-not ($manifestObj.PSObject.Properties.Name -contains 'schema') -or $manifestObj.schema -cne 'audit-slices/1') {
    Write-Refusal "manifest schema is not audit-slices/1."; exit 2
}
if ([string]$manifestObj.parent -cne $Parent) { Write-Refusal "manifest parent '$($manifestObj.parent)' does not match -Parent '$Parent'."; exit 2 }
$slices = @($manifestObj.slices | Sort-Object -Property { [int]$_.index })
if ($slices.Count -eq 0) { Write-Refusal 'manifest holds no slice.'; exit 2 }
$considered = if ($Only -gt 0 -and $Only -lt $slices.Count) { @($slices | Select-Object -First $Only) } else { $slices }

$templateText = [IO.File]::ReadAllText($Template, [System.Text.Encoding]::UTF8)
# The template's own authoring comments are not part of a ticket.
$templateText = ($templateText -replace '(?m)^<!--.*-->\r?\n', '').TrimStart("`r", "`n")
$nl = if ($templateText -match "`r`n") { "`r`n" } else { "`n" }

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$utf8 = [System.Text.UTF8Encoding]::new($false)
$today = Get-Date -Format 'yyyy-MM-dd'

# --- existing records, read once ---------------------------------------------------------------

$catalogJson = & $pwshExe -NoProfile -File $selectScript -Format json -IncludeArchived 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { Write-Refusal "the catalog could not be read (select.ps1 exit $LASTEXITCODE): $($catalogJson.Trim())"; exit 2 }
$existingByName = @{}
try {
    foreach ($rec in @(($catalogJson | ConvertFrom-Json))) {
        if ($rec -and $rec.PSObject.Properties.Name -contains 'name') { $existingByName[[string]$rec.name] = [string]$rec.id }
    }
}
catch { Write-Refusal "the catalog listing could not be parsed: $($_.Exception.Message)"; exit 2 }

# --- per slice ---------------------------------------------------------------------------------

function Get-Bullets([object[]] $Items, [string] $Empty) {
    if (-not $Items -or $Items.Count -eq 0) { return "- $Empty" }
    return (($Items | ForEach-Object { "- ``$_``" }) -join $nl)
}

$created = 0
$skipped = 0
$repaired = 0
foreach ($slice in $considered) {
    $name = [string]$slice.name
    $fileCount = [int]$slice.fileCount
    $loc = [int]$slice.loc
    $repair = $false
    if ($existingByName.ContainsKey($name)) {
        $id = $existingByName[$name]
        $specOnDisk = Test-Path -LiteralPath (Join-Path $RepoRoot "PLAN/${id}_$name.md") -PathType Leaf
        $artifactOnDisk = Test-Path -LiteralPath (Join-Path $RepoRoot "PLAN/${id}_$name/research/01__slice-files.md") -PathType Leaf
        if ($specOnDisk -and $artifactOnDisk) {
            $skipped++
            if (-not $Quiet) { Write-Host "skip: $id $name" }
            continue
        }
        # The record exists but its files do not: a run died between the insert and the write. The
        # name-based skip would leave that ticket empty forever, so the files are written under the
        # id the catalog already holds.
        if (-not $PSCmdlet.ShouldProcess($name, 'repair audit slice ticket files')) {
            Write-Host "plan: repair $id $name ($fileCount files, $loc LOC)"
            continue
        }
        $repair = $true
    }
    else {
        if (-not $PSCmdlet.ShouldProcess($name, 'create audit slice ticket')) {
            Write-Host "plan: $name ($fileCount files, $loc LOC)"
            continue
        }
        $insertOut = & $pwshExe -NoProfile -File $insertScript -Name $name -Slug $name -Status $Status -Tier $Tier -Priority $Priority 2>&1 | Out-String
        $id = [regex]::Match($insertOut, 'S\d{4}(?=\s*$)').Value
        if ($LASTEXITCODE -ne 0 -or -not $id) {
            Write-Refusal "the catalog refused the insert of '$name' (exit $LASTEXITCODE): $($insertOut.Trim())"
            Write-Host "fanout: stopped - created $created, skipped $skipped, of $($considered.Count) slices; re-run to continue."
            exit 1
        }
    }

    $module = [string]$slice.module
    $isWear = $module -ceq 'wear'
    $indexDigits = [regex]::Match($name, '^audit-slice-(\d+)-').Groups[1].Value
    if (-not $indexDigits) { $indexDigits = [string]$slice.index }
    $packages = @($slice.packages | ForEach-Object { [string]$_ })
    $siblings = @($slice.siblings | ForEach-Object { [string]$_ })
    $fastCheck = if ($isWear) { '.\a.ps1 fw' } else { '.\a.ps1 fk' }
    $unitCheck = if ($isWear) { '.\a.ps1 fwu' } else { 'pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "<fqcn>"' }
    $detektNote = if ([bool]$slice.detektAmbiguous) { 'по имени файла, неоднозначно - верхняя граница' } else { 'по имени файла' }
    $filesArtifact = "PLAN/${id}_$name/research/01__slice-files.md"

    $body = $templateText
    $body = $body.Replace('{{ID}}', $id).Replace('{{SLUG}}', $name).Replace('{{INDEX}}', $indexDigits)
    $body = $body.Replace('{{TITLE}}', [string]$slice.title).Replace('{{PARENT}}', $Parent).Replace('{{DATE}}', $today)
    $body = $body.Replace('{{PRIORITY}}', [string]$Priority).Replace('{{MODULE}}', $module).Replace('{{SOURCE_SET}}', [string]$slice.sourceSet)
    $body = $body.Replace('{{PACKAGES}}', (Get-Bullets $packages 'none')).Replace('{{FILE_COUNT}}', [string]$fileCount).Replace('{{LOC}}', [string]$loc)
    $body = $body.Replace('{{RISK}}', [string]$slice.risk).Replace('{{LINT}}', [string]$slice.lint).Replace('{{DETEKT}}', [string]$slice.detekt)
    $body = $body.Replace('{{DETEKT_NOTE}}', $detektNote).Replace('{{SIBLINGS}}', (Get-Bullets $siblings 'none'))
    $body = $body.Replace('{{FAST_CHECK}}', $fastCheck).Replace('{{UNIT_CHECK}}', $unitCheck).Replace('{{FILES_ARTIFACT}}', $filesArtifact)

    $specPath = Join-Path $RepoRoot "PLAN/${id}_$name.md"
    $artifactPath = Join-Path $RepoRoot ($filesArtifact -replace '/', [IO.Path]::DirectorySeparatorChar)
    try {
        [IO.File]::WriteAllText($specPath, $body, $utf8)
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $artifactPath) | Out-Null
        $art = [System.Collections.Generic.List[string]]::new()
        $art.Add("# Research 01 - Files of audit slice $indexDigits")
        $art.Add('')
        $art.Add("**Ticket:** $id")
        $art.Add("**Parent:** $Parent")
        $art.Add("**Module / source set:** $module / $($slice.sourceSet)")
        $art.Add("**Files:** $fileCount, $loc lines, risk $($slice.risk), lint $($slice.lint), detekt $($slice.detekt) ($detektNote)")
        $art.Add('')
        $art.Add('## Packages')
        $art.Add('')
        foreach ($p in $packages) { $art.Add("- ``$p``") }
        $art.Add('')
        $art.Add('## Files')
        $art.Add('')
        $art.Add('| Path | LOC | Signals | Lint | Detekt |')
        $art.Add('| --- | ---: | ---: | ---: | ---: |')
        foreach ($f in @($slice.files)) { $art.Add("| ``$($f.path)`` | $($f.loc) | $($f.signals) | $($f.lint) | $($f.detekt) |") }
        $art.Add('')
        $art.Add('## Siblings')
        $art.Add('')
        if ($siblings.Count -eq 0) { $art.Add('- none') } else { foreach ($s in $siblings) { $art.Add("- $s") } }
        [IO.File]::WriteAllText($artifactPath, (($art -join "`n") + "`n"), $utf8)
    }
    catch {
        Write-Refusal "$id is in the catalog but its files could not be written: $($_.Exception.Message)"
        Write-Host "fanout: stopped - created $created, skipped $skipped, of $($considered.Count) slices; re-run to continue."
        exit 1
    }
    $existingByName[$name] = $id
    if ($repair) {
        $repaired++
        Write-Host "repaired: $id $name ($fileCount files, $loc LOC)"
    }
    else {
        $created++
        if (-not $Quiet) { Write-Host "created: $id $name ($fileCount files, $loc LOC)" }
    }
}

$limitNote = if ($considered.Count -ne $slices.Count) { " (manifest $($slices.Count))" } else { '' }
if ($repaired -gt 0) { Write-Host "fanout: repaired $repaired record(s) that had no files" }
Write-Host "fanout: created $created, skipped $skipped, of $($considered.Count) slices$limitNote"
exit 0
