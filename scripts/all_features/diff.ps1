<#
.SYNOPSIS
    Diff docs/ALL_FEATURES.jsonl between two git refs (release-diff for /skill-release).

.DESCRIPTION
    Compares the inventory at -From against -To (default HEAD / working tree) and
    prints records that were added or changed (by id). This is the input the
    release pipeline reads to decide which standout capabilities to add or update
    in the public FEATURES showcase. Exit 0 on success; prints nothing extra when
    there is no change.

.EXAMPLE
    .\scripts\all_features\diff.ps1 -From v1.2.0
.EXAMPLE
    .\scripts\all_features\diff.ps1 -From HEAD~50 -To HEAD
#>
param(
    [Parameter(Mandatory = $true)] [string]$From,
    [Parameter(Mandatory = $false)] [string]$To = "",
    [switch]$Quiet
)
$ErrorActionPreference = "Stop"
trap { Write-Error $_; exit 1 }

$path = "docs/ALL_FEATURES.jsonl"

function Read-Ref([string]$ref) {
    # empty ref => current working tree
    if ([string]::IsNullOrWhiteSpace($ref)) {
        if (Test-Path $path) { return Get-Content -LiteralPath $path -Encoding UTF8 } else { return @() }
    }
    $content = git show "${ref}:${path}" 2>$null
    if ($LASTEXITCODE -ne 0) { return @() }
    return $content
}

function ToMap($lines) {
    $map = @{}
    foreach ($l in $lines) {
        if ($l.Trim().Length -eq 0) { continue }
        try { $o = $l | ConvertFrom-Json } catch { continue }
        if ($o.id) { $map[$o.id] = $l }
    }
    return $map
}

$fromMap = ToMap (Read-Ref $From)
$toMap = ToMap (Read-Ref $To)

$added = New-Object System.Collections.Generic.List[object]
$changed = New-Object System.Collections.Generic.List[object]
foreach ($id in $toMap.Keys) {
    if (-not $fromMap.ContainsKey($id)) {
        $added.Add(($toMap[$id] | ConvertFrom-Json))
    } elseif ($fromMap[$id] -ne $toMap[$id]) {
        $changed.Add(($toMap[$id] | ConvertFrom-Json))
    }
}

if (-not $Quiet) {
    Write-Host "ALL_FEATURES diff $From..$(if($To){$To}else{'(working tree)'}): $($added.Count) added, $($changed.Count) changed" -ForegroundColor Cyan
    foreach ($r in $added) { Write-Host "  [ADD]    [$($r.area)] $($r.name)$(if($r.spec){" ($($r.spec))"})" }
    foreach ($r in $changed) { Write-Host "  [CHANGE] [$($r.area)] $($r.name)$(if($r.spec){" ($($r.spec))"})" }
}
exit 0
