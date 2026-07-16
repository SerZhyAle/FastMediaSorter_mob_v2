# close-and-log.ps1 - Batch-finalize a spec in one pwsh invocation
#
# Replaces this 6-7 separate pwsh launches per /spec-all finalization:
#   close.ps1 -Id Sxxxx -Status Verified
#   add_to_dev_log.ps1 "<spec-file>" "<source>" "<msg>"
#   add_to_dev_log.ps1 "<file1>" "<source>" "<msg1>"  (× N changed files)
#   all_features/add.ps1 (record the delivered capability in docs/ALL_FEATURES.jsonl)
#   dev/CATALOG/scripts/scan.ps1 -Module app_v2
#   dev/CATALOG/scripts/render.ps1 -Module app_v2
#
# Usage - -DevLogs is ONE string holding a JSON ARRAY of {file,target,desc} objects:
#   pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
#     -Id S0223 `
#     -Status Verified `
#     -DevLogs '[{"file":"PLAN/S0223_*.md","target":"spec-all","desc":"audit Partial -> Verified"},{"file":"app_v2/src/main/java/.../X.kt","target":"spec-all","desc":"relabel outcome"}]' `
#     -FuncOp FIX -FuncDesc "Trace log outcome now reads SingleVideoSaved" `
#     -FeatArea "Video Player" -FeatName "Trace log outcome label" -FeatFlavors "standard,legacy" `
#     -CatalogModule app_v2
#
# The array string is expanded in-script. Do NOT pass a PowerShell array literal
# (-DevLogs @('{..}','{..}')) through `pwsh -File`: it binds only the first element and the
# leftovers become positional args - see the S1063 note below. An in-process call
# (`& ./close-and-log.ps1 -DevLogs $arr`) accepts a real array too, but the JSON-array string
# works in both, so prefer it everywhere.
# Pass -SkipCatalogSync to skip scan+render (use only when no .kt was touched).
# Pass -SkipFuncLog or omit -FuncOp/-FuncDesc to skip the feature-inventory record.
# Recording a capability (-FuncOp) REQUIRES -FeatArea, -FeatName and -FeatFlavors (csv); -FeatId is
# optional and defaults to '<area-slug>.<name-slug>'. See the S1072 note below.
# Pass -StatusOnly to update only the journal status without close.ps1 (keeps no closed_at).
#
# S1063 - two invariants keep a bad call from half-closing a ticket:
#   * PositionalBinding is OFF. Passing a multi-element array to -DevLogs through `pwsh -File`
#     leaves the extra elements as positional args; with positional binding on they silently
#     landed in the next unbound param (-FeatId) and only surfaced at the all-features step,
#     after the status flip had already been written. Now such a call dies at bind time.
#   * Every argument is shape-checked in the pre-flight below, before the first mutation.
#
# S1072 - the capability record must be told, never guessed. -FeatArea/-FeatName/-FeatFlavors used
# to default to 'General' / an 80-char cut of -FuncDesc / 'standard'. Those produce a record that is
# structurally valid, plausible, and false - validate.ps1 passes it, and the lie surfaces only in the
# release showcase generated from this inventory. They are now required whenever -FuncOp is given.
# Exit codes: 0 all steps done | 1 a step failed (see the aggregate report) | 2 bad arguments
# (nothing was mutated).

[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Mandatory = $true)][string]$Id,
    [Parameter(Mandatory = $true)]
    [ValidateSet('Draft', 'Approved', 'Tactical', 'In Progress',
        'Implemented', 'Verified', 'Partial', 'Broken',
        'BlockByOtherTask', 'BlockNeedUserTest', 'BlockQuestions', 'BlockExternal',
        'Archived')]
    [string]$Status,
    [string[]]$DevLogs = @(),
    [ValidateSet('ADD', 'CHANGE', 'DELETE', 'FIX', '')]
    [string]$FuncOp = '',
    [string]$FuncDesc = '',
    [string]$FeatId = '',
    # S1072: no defaults. These three state facts about the shipped capability (which area owns it,
    # what it is called, which builds carry it) that this script cannot derive - see the pre-flight.
    [string]$FeatArea = '',
    [string]$FeatName = '',
    [string]$FeatFlavors = '',
    [string]$CatalogModule = 'app_v2',
    [string]$StatusNote = '',
    # S0960: route the feature record to gitignored docs/ALL_FEATURES_noLegal.jsonl
    # (noLegal-only capabilities, e.g. vr source set) instead of docs/ALL_FEATURES.jsonl.
    [switch]$FeatNoLegal,
    [switch]$SkipCatalogSync,
    [switch]$SkipFuncLog,
    [switch]$StatusOnly
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# Pre-flight: validate every argument shape BEFORE any mutation. A malformed
# argument must cost nothing - previously -FeatId was only shape-checked inside
# all_features/add.ps1, i.e. after the status flip and the first dev-log had
# already landed, leaving the ticket closed but the inventory unsynced (S1063).
# ---------------------------------------------------------------------------

# -ErrorAction Continue is load-bearing: this script runs with $ErrorActionPreference = 'Stop',
# under which a plain Write-Error throws and kills the script before `exit 2` can run, so the
# documented exit-2 contract silently degraded to exit 1 (S1063).
function Reject([string]$msg) {
    Write-Error $msg -ErrorAction Continue
    exit 2
}

if ($Id -notmatch '^S\d{4}$') { Reject "Invalid -Id '$Id' (must match S####)" }

# Cross-process transport form: `pwsh -File` binds only the FIRST element of a multi-element
# array argument, so callers from another process pass one string holding a JSON ARRAY.
# Expand it here into the canonical one-JSON-object-per-element shape.
if ($DevLogs.Count -eq 1 -and $DevLogs[0].TrimStart().StartsWith('[')) {
    try {
        $DevLogs = @($DevLogs[0] | ConvertFrom-Json -ErrorAction Stop | ForEach-Object {
                $_ | ConvertTo-Json -Compress
            })
    }
    catch {
        Reject "Malformed -DevLogs JSON array: $_"
    }
}

# Parse once here so a malformed entry is rejected pre-mutation and the dev-log step
# below only ever walks known-good records.
$parsedDevLogs = New-Object System.Collections.Generic.List[object]
foreach ($entry in $DevLogs) {
    $parsed = $null
    try { $parsed = $entry | ConvertFrom-Json -ErrorAction Stop }
    catch { Reject "Malformed -DevLogs entry (expected JSON {file,target,desc}): $entry" }
    if (-not $parsed.file -or -not $parsed.target -or -not $parsed.desc) {
        Reject "-DevLogs entry missing field (file/target/desc): $entry"
    }
    $parsedDevLogs.Add($parsed)
}

# Mirror all_features/add.ps1's '<area>.<feature>' kebab contract so a bad id is caught here
# rather than three mutations later.
if ($FeatId -ne '' -and $FeatId -notmatch '^[a-z0-9]+(?:[-_][a-z0-9]+)*\.[a-z0-9]+(?:[-_][a-z0-9]+)*$') {
    Reject "Invalid -FeatId '$FeatId'. Expected kebab '<area>.<feature>' (lowercase)."
}

if (-not $SkipFuncLog -and (($FuncOp -ne '') -xor ($FuncDesc -ne ''))) {
    Reject "-FuncOp and -FuncDesc must be supplied together (FuncOp='$FuncOp', FuncDesc='$FuncDesc')."
}

# S1072: a capability record asserts facts - the owning area, the display name, and the exact set of
# builds that ship the feature. This script knows none of them. It used to guess ('General', an
# 80-char cut of -FuncDesc, and 'standard'), which produced records that are structurally valid,
# superficially plausible and simply false; validate.ps1 passes them and the error only surfaces in
# the release showcase built off this inventory. Demand the facts rather than invent them. The escape
# hatch is unchanged: a change that ships no capability passes -SkipFuncLog or omits -FuncOp.
# Checked after the -FeatId/xor rules above so a call that is wrong in several ways still reports the
# most specific reason first.
if (-not $SkipFuncLog -and $FuncOp -ne '') {
    $missingFeat = @()
    if ($FeatArea -eq '') { $missingFeat += '-FeatArea' }
    if ($FeatName -eq '') { $missingFeat += '-FeatName' }
    if ($FeatFlavors -eq '') { $missingFeat += '-FeatFlavors' }
    if ($missingFeat.Count -gt 0) {
        Reject ("-FuncOp '$FuncOp' records a capability, so $($missingFeat -join ', ') must be supplied. " +
            'Pass -SkipFuncLog (or omit -FuncOp/-FuncDesc) when the change ships no capability.')
    }
    # The record id is '<area-slug>.<feature-slug>'; an area with no alphanumeric content cannot
    # produce one, and the old 'general' fallback for that case was another silent guess.
    if ((($FeatArea.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim('-')) -eq '') {
        Reject "-FeatArea '$FeatArea' has no alphanumeric content to build a record id from."
    }
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else {
    'pwsh'
}

$totalSw = [System.Diagnostics.Stopwatch]::StartNew()
$doneSteps = New-Object System.Collections.Generic.List[string]
$failedSteps = New-Object System.Collections.Generic.List[string]

# -Fatal marks a step whose failure makes every later step meaningless (only the status
# flip qualifies). Every other step reports and yields to the next one, so one broken step
# no longer strands the rest of the closure half-applied.
function Step([string]$label, [scriptblock]$action, [switch]$Fatal) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Write-Host "  [$label] " -ForegroundColor Cyan -NoNewline
    try {
        & $action
    }
    catch {
        $sw.Stop()
        Write-Host "FAILED ($([int]$sw.Elapsed.TotalMilliseconds) ms)" -ForegroundColor Red
        Write-Host "    $_" -ForegroundColor Red
        $failedSteps.Add("$label - $_")
        if ($Fatal) {
            Write-Host "close-and-log: aborted at [$label] - later steps skipped" -ForegroundColor Red
            exit 1
        }
        return
    }
    $sw.Stop()
    Write-Host "$([int]$sw.Elapsed.TotalMilliseconds) ms" -ForegroundColor DarkGray
    $doneSteps.Add($label)
}

Write-Host "close-and-log: $Id -> $Status" -ForegroundColor Yellow

# 1. Status update (close.ps1 stamps closed_at on Verified/Archived; update.ps1 otherwise).
$useClose = (-not $StatusOnly) -and ($Status -in @('Verified', 'Archived'))
Step "status" -Fatal {
    if ($useClose) {
        $closePath = Join-Path $PSScriptRoot 'close.ps1'
        & $pwshExe -File $closePath -Id $Id -Status $Status
        if ($LASTEXITCODE -ne 0) { throw "close.ps1 exited $LASTEXITCODE" }
    }
    else {
        $updatePath = Join-Path $PSScriptRoot 'update.ps1'
        $updateArgs = @('-File', $updatePath, '-Id', $Id, '-Status', $Status)
        if ($StatusNote -ne '') { $updateArgs += @('-StatusNote', $StatusNote) }
        & $pwshExe @updateArgs
        if ($LASTEXITCODE -ne 0) { throw "update.ps1 exited $LASTEXITCODE" }
    }
}

# 2. Dev logs (batched in-process so no pwsh respawn per entry).
if ($parsedDevLogs.Count -gt 0) {
    $devLogScript = Join-Path $root 'scripts/add_to_dev_log.ps1'
    Step "dev-log ($($parsedDevLogs.Count))" {
        # Per-entry isolation: one unwritable entry must not swallow the others.
        $entryFails = @()
        foreach ($p in $parsedDevLogs) {
            try { & $devLogScript $p.file $p.target $p.desc | Out-Null }
            catch { $entryFails += "$($p.file): $_" }
        }
        if ($entryFails.Count -gt 0) { throw ($entryFails -join ' | ') }
    }
}

# 3. Feature inventory record (optional). S0489: records the delivered capability
# in docs/ALL_FEATURES.jsonl (the developer inventory that replaced FUNCTIONALITY.log).
# DELETE op marks the record removed; ADD/CHANGE/FIX keep it active.
if (-not $SkipFuncLog -and $FuncOp -and $FuncDesc) {
    Step "all-features" {
        $addScript = Join-Path $root 'scripts/all_features/add.ps1'
        # S1072: -FeatName is the name, verbatim. It used to fall back to $FuncDesc cut at 80 chars,
        # which slices mid-word and is never a name - only the caller knows what the feature is called.
        # The pre-flight guarantees -FeatName/-FeatArea are non-empty by the time we get here.
        $slug = ($FeatName.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim('-')
        $slugParts = @($slug -split '-' | Where-Object { $_ })
        if ($slugParts.Count -gt 8) { $slugParts = $slugParts[0..7] }
        $slug = ($slugParts -join '-'); if (-not $slug) { $slug = 'item' }
        # S0665: the record id prefix is the AREA slug, not the spec id. validate.ps1
        # rejects 's####.' prefixes (a spec id is not an area). Mirror all_features/add.ps1's
        # '<area>.<feature>' kebab contract by slugifying -FeatArea the same way as the feature.
        $areaSlug = ($FeatArea.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim('-')
        $recId = if ($FeatId) { $FeatId } else { "$areaSlug.$slug" }
        # Named $recStatus, not $status: PowerShell variable names are case-insensitive, so a
        # local $status would collide with this script's $Status parameter (S1063).
        $recStatus = if ($FuncOp -eq 'DELETE') { 'removed' } else { 'active' }
        $addArgs = @{ Id = $recId; Area = $FeatArea; Name = $FeatName; Description = $FuncDesc; Flavors = $FeatFlavors; Spec = $Id; Status = $recStatus; Quiet = $true }
        if ($FeatNoLegal) { $addArgs.NoLegal = $true }
        & $addScript @addArgs | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "all_features/add.ps1 exited $LASTEXITCODE" }
    }
}

# 4. Catalog sync (optional).
if (-not $SkipCatalogSync) {
    Step "catalog scan" {
        $scanPath = Join-Path $root 'dev/CATALOG/scripts/scan.ps1'
        & $pwshExe -File $scanPath -Module $CatalogModule | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "scan.ps1 exited $LASTEXITCODE" }
    }
    Step "catalog render" {
        $renderPath = Join-Path $root 'dev/CATALOG/scripts/render.ps1'
        & $pwshExe -File $renderPath -Module $CatalogModule | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "render.ps1 exited $LASTEXITCODE" }
    }
}

$totalSw.Stop()

if ($failedSteps.Count -gt 0) {
    $doneLabel = if ($doneSteps.Count -gt 0) { $doneSteps -join ', ' } else { '(none)' }
    Write-Host ""
    Write-Host "close-and-log: $($failedSteps.Count) step(s) FAILED in $([math]::Round($totalSw.Elapsed.TotalSeconds, 1)) s" -ForegroundColor Red
    Write-Host "  applied: $doneLabel" -ForegroundColor DarkGray
    foreach ($f in $failedSteps) { Write-Host "  FAILED:  $f" -ForegroundColor Red }
    exit 1
}

Write-Host "Done in $([math]::Round($totalSw.Elapsed.TotalSeconds, 1)) s" -ForegroundColor Green
exit 0
