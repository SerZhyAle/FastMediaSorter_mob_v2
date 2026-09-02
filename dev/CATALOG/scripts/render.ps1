# Renders a JSONL catalogue into a human-readable Markdown view.
#
# Usage:
#   pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
#
# Exit codes:
#   0 - success.

param(
    [Parameter(Mandatory=$true)]
    [string]$Module,
    [string]$Root,
    [string]$InFile,
    [string]$OutFile
)

$ErrorActionPreference = "Stop"

if (-not $Root) {
    $Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
}
$Root = (Resolve-Path $Root).Path

if (-not $InFile)  { $InFile  = Join-Path $Root "dev\CATALOG\$Module.jsonl" }
if (-not $OutFile) { $OutFile = Join-Path $Root "dev\CATALOG\$Module.md" }

if (-not (Test-Path -LiteralPath $InFile)) { throw "Catalogue not found: $InFile (run scan.ps1 first)" }

$records = New-Object System.Collections.Generic.List[PSCustomObject]
foreach ($line in (Get-Content -Path $InFile -Encoding UTF8)) {
    if (-not $line) { continue }
    $records.Add(($line | ConvertFrom-Json))
}

$total = $records.Count
$byLayer = $records | Group-Object -Property layer | Sort-Object Name
$now = (Get-Date -Format 'yyyy-MM-dd HH:mm')

$candidateRoots = @(
    "$Module/src/main/java",
    "$Module/src/vr/java",
    "$Module/src/noLegal/java",
    "$Module/src/streamingEnabled/java",
    "$Module/src/ocrEnabled/java",
    "$Module/src/ocrDisabled/java",
    "$Module/src/screenCapture/java",
    "$Module/src/screenCapturePlay/java"
)

$sourceLinkCache = @{}

function Resolve-SourceLink([string]$module, [string]$relPath, [string]$root) {
    if ($sourceLinkCache.ContainsKey($relPath)) {
        return $sourceLinkCache[$relPath]
    }
    $relPathWin = $relPath -replace '/', '\'
    foreach ($candidateRoot in $candidateRoots) {
        $fullPath = Join-Path $root ("$candidateRoot\$relPathWin")
        if ([System.IO.File]::Exists($fullPath)) {
            $res = "$candidateRoot/$relPath"
            $sourceLinkCache[$relPath] = $res
            return $res
        }
    }
    $res = "$module/src/main/java/$relPath"
    $sourceLinkCache[$relPath] = $res
    return $res
}

$out = New-Object System.Collections.Generic.List[string]
$out.Add("# Catalogue: $Module")
$out.Add("")
$out.Add("_Generated: $now · $total classes_")
$out.Add("")
$out.Add("Source of truth: [$Module.jsonl]($Module.jsonl). This file is auto-generated - edit JSONL, then re-render.")
$out.Add("")

$out.Add("## Layer summary")
$out.Add("")
$out.Add("| Layer | Files | Total LOC |")
$out.Add("|-------|------:|----------:|")
foreach ($g in $byLayer) {
    $locSum = 0
    foreach ($item in $g.Group) { $locSum += $item.loc }
    $out.Add("| $($g.Name) | $($g.Count) | $locSum |")
}
$out.Add("")

# Pre-sort records once using native properties for fast C# sorting
$sortedRecords = $records | Sort-Object -Property layer, path

$out.Add("## Index")
$out.Add("")
$out.Add("| Path | Class | Layer | LOC | Last | Status | Role |")
$out.Add("|------|-------|-------|----:|------|--------|------|")
foreach ($r in $sortedRecords) {
    $sourceLink = Resolve-SourceLink $Module $r.path $Root
    $pathLink = "[$($r.path)]($sourceLink)"
    $role = if ($r.role) { $r.role } else { '_-_' }
    $status = if ($r.status) { $r.status } else { 'unknown' }
    $last = if ($r.lastTouched) { $r.lastTouched } else { '-' }
    $out.Add("| $pathLink | ``$($r.class)`` | $($r.layer) | $($r.loc) | $last | $status | $role |")
}
$out.Add("")

$out.Add("## Details")
$out.Add("")
$flagList = New-Object System.Collections.Generic.List[string]
foreach ($r in $sortedRecords) {
    $sourceLink = Resolve-SourceLink $Module $r.path $Root
    $flagList.Clear()
    if ($r.coroutines) { $flagList.Add('coroutines') }
    if ($r.userFeedback) { $flagList.Add('user-feedback') }
    if ($r.usesTimber) { $flagList.Add('timber') }
    if ($r.hasTests) { $flagList.Add('tests') }
    $flagsStr = if ($flagList.Count -gt 0) { ($flagList -join ' · ') } else { '-' }

    $seStr = if ($r.sideEffects -and $r.sideEffects.Count) { ($r.sideEffects -join ', ') } else { '-' }
    $injStr = if ($r.injected -and $r.injected.Count) { ($r.injected -join ', ') } else { '-' }
    $noFlavStr = if ($r.noFlavors -and $r.noFlavors.Count) { ($r.noFlavors -join ', ') } else { '-' }

    $out.Add("### ``$($r.class)`` - [$($r.path)]($sourceLink)")
    $out.Add("")
    $out.Add("**Layer:** $($r.layer) · **LOC:** $($r.loc) · **Last:** $(if ($r.lastTouched) { $r.lastTouched } else { '-' }) · **Status:** $(if ($r.status) { $r.status } else { 'unknown' }) · **NoFlavors:** $noFlavStr")
    $out.Add("")
    $out.Add("**Injected:** $injStr  ")
    $out.Add("**Side effects:** $seStr  ")
    $out.Add("**Flags:** $flagsStr")
    $out.Add("")
    $role = if ($r.role) { $r.role } else { '_(unfilled)_' }
    $out.Add("**Role:** $role")
    $out.Add("")
    if ($r.functions -and $r.functions.Count) {
        $out.Add("**Functions:**")
        $out.Add("")
        foreach ($f in $r.functions) {
            $desc = if ($f.description) { $f.description } else { '_(unfilled)_' }
            $out.Add("- ``$($f.name)`` - $desc")
        }
        $out.Add("")
    }
}

$outDir = Split-Path $OutFile -Parent
if (-not (Test-Path -LiteralPath $outDir)) { New-Item -Path $outDir -ItemType Directory -Force | Out-Null }
$out | Set-Content -Path $OutFile -Encoding UTF8

Write-Host "Rendered $total records -> $OutFile"

exit 0
