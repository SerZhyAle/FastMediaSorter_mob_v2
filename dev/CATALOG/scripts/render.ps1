# Renders a JSONL catalogue into a human-readable Markdown view.
#
# Usage:
#   pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2

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

if (-not (Test-Path $InFile)) { throw "Catalogue not found: $InFile (run scan.ps1 first)" }

$records = @()
foreach ($line in (Get-Content -Path $InFile -Encoding UTF8)) {
    if (-not $line) { continue }
    $records += ($line | ConvertFrom-Json -AsHashtable)
}

$total = $records.Count
$byLayer = $records | Group-Object -Property layer | Sort-Object Name
$now = (Get-Date -Format 'yyyy-MM-dd HH:mm')

function Resolve-SourceLink([string]$module, [string]$relPath, [string]$root) {
    # Must stay in sync with scan.ps1 $srcRoots.
    $candidateRoots = @(
        "$module/src/main/java",
        "$module/src/vr/java",
        "$module/src/noLegal/java",
        "$module/src/streamingEnabled/java",
        "$module/src/ocrEnabled/java",
        "$module/src/ocrDisabled/java",
        "$module/src/screenCapture/java",
        "$module/src/screenCapturePlay/java"
    )
    foreach ($candidateRoot in $candidateRoots) {
        $candidate = Join-Path $root ($candidateRoot -replace '/', '\\')
        $fullPath = Join-Path $candidate ($relPath -replace '/', '\\')
        if (Test-Path $fullPath) {
            return "$candidateRoot/$relPath"
        }
    }
    return "$module/src/main/java/$relPath"
}

$out = New-Object System.Collections.ArrayList
[void]$out.Add("# Catalogue: $Module")
[void]$out.Add("")
[void]$out.Add("_Generated: $now · $total classes_")
[void]$out.Add("")
[void]$out.Add("Source of truth: [$Module.jsonl]($Module.jsonl). This file is auto-generated - edit JSONL, then re-render.")
[void]$out.Add("")

[void]$out.Add("## Layer summary")
[void]$out.Add("")
[void]$out.Add("| Layer | Files | Total LOC |")
[void]$out.Add("|-------|------:|----------:|")
foreach ($g in $byLayer) {
    $locSum = ($g.Group | Measure-Object -Property loc -Sum).Sum
    [void]$out.Add("| $($g.Name) | $($g.Count) | $locSum |")
}
[void]$out.Add("")

[void]$out.Add("## Index")
[void]$out.Add("")
[void]$out.Add("| Path | Class | Layer | LOC | Last | Status | Role |")
[void]$out.Add("|------|-------|-------|----:|------|--------|------|")
foreach ($r in ($records | Sort-Object -Property @{Expression={$_.layer}}, @{Expression={$_.path}})) {
    $sourceLink = Resolve-SourceLink $Module $r.path $Root
    $pathLink = "[$($r.path)]($sourceLink)"
    $role = if ($r.role) { $r.role } else { '_-_' }
    $status = if ($r.status) { $r.status } else { 'unknown' }
    $last = if ($r.lastTouched) { $r.lastTouched } else { '-' }
    [void]$out.Add("| $pathLink | ``$($r.class)`` | $($r.layer) | $($r.loc) | $last | $status | $role |")
}
[void]$out.Add("")

[void]$out.Add("## Details")
[void]$out.Add("")
foreach ($r in ($records | Sort-Object -Property @{Expression={$_.layer}}, @{Expression={$_.path}})) {
    $sourceLink = Resolve-SourceLink $Module $r.path $Root
    $flags = @()
    if ($r.coroutines) { $flags += 'coroutines' }
    if ($r.userFeedback) { $flags += 'user-feedback' }
    if ($r.usesTimber) { $flags += 'timber' }
    if ($r.hasTests) { $flags += 'tests' }
    $flagsStr = if ($flags.Count) { ($flags -join ' · ') } else { '-' }

    $seStr = if ($r.sideEffects -and $r.sideEffects.Count) { ($r.sideEffects -join ', ') } else { '-' }
    $injStr = if ($r.injected -and $r.injected.Count) { ($r.injected -join ', ') } else { '-' }
    $noFlavStr = if ($r.noFlavors -and $r.noFlavors.Count) { ($r.noFlavors -join ', ') } else { '-' }

    [void]$out.Add("### ``$($r.class)`` - [$($r.path)]($sourceLink)")
    [void]$out.Add("")
    [void]$out.Add("**Layer:** $($r.layer) · **LOC:** $($r.loc) · **Last:** $(if ($r.lastTouched) { $r.lastTouched } else { '-' }) · **Status:** $(if ($r.status) { $r.status } else { 'unknown' }) · **NoFlavors:** $noFlavStr")
    [void]$out.Add("")
    [void]$out.Add("**Injected:** $injStr  ")
    [void]$out.Add("**Side effects:** $seStr  ")
    [void]$out.Add("**Flags:** $flagsStr")
    [void]$out.Add("")
    $role = if ($r.role) { $r.role } else { '_(unfilled)_' }
    [void]$out.Add("**Role:** $role")
    [void]$out.Add("")
    if ($r.functions -and $r.functions.Count) {
        [void]$out.Add("**Functions:**")
        [void]$out.Add("")
        foreach ($f in $r.functions) {
            $desc = if ($f.description) { $f.description } else { '_(unfilled)_' }
            [void]$out.Add("- ``$($f.name)`` - $desc")
        }
        [void]$out.Add("")
    }
}

$outDir = Split-Path $OutFile -Parent
if (-not (Test-Path $outDir)) { New-Item -Path $outDir -ItemType Directory -Force | Out-Null }
$out | Set-Content -Path $OutFile -Encoding UTF8

Write-Host "Rendered $total records -> $OutFile"
