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
# Usage:
#   pwsh -File scripts/spec_catalog/close-and-log.ps1 `
#     -Id S0223 `
#     -Status Verified `
#     -DevLogs @(
#         '{"file":"PLAN/S0223_*.md","target":"spec-all","desc":"audit Partial -> Verified"}',
#         '{"file":"app_v2/src/main/java/.../X.kt","target":"spec-all","desc":"relabel outcome"}'
#       ) `
#     -FuncOp FIX -FuncDesc "Trace log outcome now reads SingleVideoSaved" `
#     -CatalogModule app_v2
#
# Each DevLogs entry is a JSON object with keys: file, target, desc.
# Pass -SkipCatalogSync to skip scan+render (use only when no .kt was touched).
# Pass -SkipFuncLog or omit -FuncOp/-FuncDesc to skip the feature-inventory record.
# Richer record: add -FeatArea, -FeatName, -FeatFlavors (csv), or explicit -FeatId.
# Pass -StatusOnly to update only the journal status without close.ps1 (keeps no closed_at).

[CmdletBinding()]
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
    [string]$FeatArea = 'General',
    [string]$FeatName = '',
    [string]$FeatFlavors = 'standard',
    [string]$CatalogModule = 'app_v2',
    [string]$StatusNote = '',
    [switch]$SkipCatalogSync,
    [switch]$SkipFuncLog,
    [switch]$StatusOnly
)

$ErrorActionPreference = 'Stop'

if ($Id -notmatch '^S\d{4}$') {
    Write-Error "Invalid -Id '$Id' (must match S####)"
    exit 2
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else {
    'pwsh'
}

$totalSw = [System.Diagnostics.Stopwatch]::StartNew()

function Step([string]$label, [scriptblock]$action) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Write-Host "  [$label] " -ForegroundColor Cyan -NoNewline
    try {
        & $action
    }
    catch {
        $sw.Stop()
        Write-Host "FAILED ($([int]$sw.Elapsed.TotalMilliseconds) ms)" -ForegroundColor Red
        Write-Host "    $_" -ForegroundColor Red
        throw
    }
    $sw.Stop()
    Write-Host "$([int]$sw.Elapsed.TotalMilliseconds) ms" -ForegroundColor DarkGray
}

Write-Host "close-and-log: $Id -> $Status" -ForegroundColor Yellow

# 1. Status update (close.ps1 stamps closed_at on Verified/Archived; update.ps1 otherwise).
$useClose = (-not $StatusOnly) -and ($Status -in @('Verified', 'Archived'))
Step "status" {
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
if ($DevLogs.Count -gt 0) {
    $devLogScript = Join-Path $root 'scripts/add_to_dev_log.ps1'
    Step "dev-log ($($DevLogs.Count))" {
        foreach ($entry in $DevLogs) {
            $parsed = $null
            try { $parsed = $entry | ConvertFrom-Json -ErrorAction Stop } catch {
                throw "Malformed DevLogs entry (expected JSON {file,target,desc}): $entry"
            }
            if (-not $parsed.file -or -not $parsed.target -or -not $parsed.desc) {
                throw "DevLogs entry missing field (file/target/desc): $entry"
            }
            & $devLogScript $parsed.file $parsed.target $parsed.desc | Out-Null
        }
    }
}

# 3. Feature inventory record (optional). S0489: records the delivered capability
# in docs/ALL_FEATURES.jsonl (the developer inventory that replaced FUNCTIONALITY.log).
# DELETE op marks the record removed; ADD/CHANGE/FIX keep it active.
if (-not $SkipFuncLog -and $FuncOp -and $FuncDesc) {
    Step "all-features" {
        $addScript = Join-Path $root 'scripts/all_features/add.ps1'
        $featName = if ($FeatName) { $FeatName } else { $FuncDesc.Substring(0, [Math]::Min(80, $FuncDesc.Length)) }
        $slugSrc = if ($FeatName) { $FeatName } else { $FuncDesc }
        $slug = ($slugSrc.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim('-')
        $slugParts = @($slug -split '-' | Where-Object { $_ })
        if ($slugParts.Count -gt 8) { $slugParts = $slugParts[0..7] }
        $slug = ($slugParts -join '-'); if (-not $slug) { $slug = 'item' }
        # S0665: the record id prefix is the AREA slug, not the spec id. validate.ps1
        # rejects 's####.' prefixes (a spec id is not an area). Mirror all_features/add.ps1's
        # '<area>.<feature>' kebab contract by slugifying -FeatArea the same way as the feature.
        $areaSlug = ($FeatArea.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim('-')
        if (-not $areaSlug) { $areaSlug = 'general' }
        $recId = if ($FeatId) { $FeatId } else { "$areaSlug.$slug" }
        $status = if ($FuncOp -eq 'DELETE') { 'removed' } else { 'active' }
        $addArgs = @{ Id = $recId; Area = $FeatArea; Name = $featName; Description = $FuncDesc; Flavors = $FeatFlavors; Spec = $Id; Status = $status; Quiet = $true }
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
Write-Host "Done in $([math]::Round($totalSw.Elapsed.TotalSeconds, 1)) s" -ForegroundColor Green
exit 0
