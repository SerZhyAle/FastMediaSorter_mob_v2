# Scans AndroidManifest.xml source sets for a module and produces/updates the ACTIVITY_CATALOG JSONL.
# Manual fields (role, roleRu, tags, status, notes) are preserved on re-run.
#
# Usage:
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/scan.ps1 -Module app_v2
#   pwsh -File dev/ACTIVITY_CATALOG/scripts/scan.ps1 -Module wear

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("app_v2","wear")]
    [string]$Module,
    [string]$Root,
    [string]$OutFile
)

$ErrorActionPreference = "Stop"

if (-not $Root) {
    $Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
}
$Root = (Resolve-Path $Root).Path

if (-not $OutFile) {
    $OutFile = Join-Path $Root "dev\ACTIVITY_CATALOG\$Module.jsonl"
}

$ownPackage = "com.sza.fastmediasorter"

# ── Manifest source sets ──────────────────────────────────────────────────────

$sourceSets = @()
if ($Module -eq "app_v2") {
    $sourceSets = @(
        @{ name = "main";   path = "$Module\src\main\AndroidManifest.xml"   },
        @{ name = "vr";     path = "$Module\src\vr\AndroidManifest.xml"     },
        @{ name = "lite";   path = "$Module\src\lite\AndroidManifest.xml"   },
        @{ name = "photos"; path = "$Module\src\photos\AndroidManifest.xml" },
        @{ name = "legacy"; path = "$Module\src\legacy\AndroidManifest.xml" }
    )
} else {
    $sourceSets = @(
        @{ name = "main"; path = "wear\src\main\AndroidManifest.xml" }
    )
}

$allFlavors = @("standard","lite","photos","legacy","vr")

# Maps flavor sourceSet name → flavors where Activity is PRESENT
$flavorSetPresent = @{
    "main"   = @("standard","lite","photos","legacy")  # main = all non-vr flavors
    "vr"     = @("vr")
    "lite"   = @("lite")
    "photos" = @("photos")
    "legacy" = @("legacy")
}

# ── Parse manifests ───────────────────────────────────────────────────────────

# Key: fully-qualified class name → accumulated record hashtable
$scanned = [ordered]@{}

foreach ($ss in $sourceSets) {
    $manifestPath = Join-Path $Root $ss.path
    if (-not (Test-Path $manifestPath)) { continue }

    [xml]$manifest = Get-Content -Path $manifestPath -Encoding UTF8

    # Resolve package from manifest if present (wear module uses short names)
    $manifestPackage = $manifest.manifest.package
    if (-not $manifestPackage) { $manifestPackage = $ownPackage }

    foreach ($activity in $manifest.manifest.application.activity) {
        $rawName = $activity.GetAttribute("android:name")
        if (-not $rawName) { continue }

        # Expand relative name (.Foo → com.sza.fastmediasorter.Foo)
        if ($rawName.StartsWith(".")) {
            $fqn = $ownPackage + $rawName
        } elseif ($rawName.StartsWith($ownPackage)) {
            $fqn = $rawName
        } else {
            # Third-party activity - skip
            continue
        }

        $simpleName = $fqn.Split(".")[-1]

        # Collect intent-filter actions and categories
        $actions    = [System.Collections.Generic.List[string]]::new()
        $categories = [System.Collections.Generic.List[string]]::new()
        $isLauncher = $false

        $filters = $activity.GetElementsByTagName("intent-filter")
        foreach ($f in $filters) {
            $hasMain     = $false
            $hasLauncher = $false
            foreach ($child in $f.ChildNodes) {
                if ($child.LocalName -eq "action") {
                    $a = $child.GetAttribute("android:name")
                    if ($a -and -not $actions.Contains($a)) { $actions.Add($a) }
                    if ($a -eq "android.intent.action.MAIN") { $hasMain = $true }
                }
                if ($child.LocalName -eq "category") {
                    $c = $child.GetAttribute("android:name")
                    if ($c -and -not $categories.Contains($c)) { $categories.Add($c) }
                    if ($c -eq "android.intent.category.LAUNCHER" -or
                        $c -eq "android.intent.category.LEANBACK_LAUNCHER") { $hasLauncher = $true }
                }
            }
            if ($hasMain -and $hasLauncher) { $isLauncher = $true }
        }

        $exportedAttr = $activity.GetAttribute("android:exported")
        $isExported   = ($exportedAttr -eq "true")

        $key = "$Module|$simpleName"

        if (-not $scanned.Contains($key)) {
            $scanned[$key] = [ordered]@{
                class           = $simpleName
                package         = $fqn
                module          = $Module
                path            = ""
                sourceSet       = $ss.name
                exported        = $isExported
                launcher        = $isLauncher
                intentActions   = @($actions)
                intentCategories= @($categories)
                presentInSets   = [System.Collections.Generic.List[string]]::new()
                noFlavors       = @()
                loc             = 0
                lastTouched     = ""
                # manual (filled later from existing JSONL)
                role            = ""
                roleRu          = ""
                tags            = @()
                status          = "new"
                notes           = ""
            }
        }

        # Track which source sets this Activity appears in
        $scanned[$key].presentInSets.Add($ss.name) | Out-Null

        # Merge exported/launcher - if ANY sourceSet marks it exported/launcher, it is
        if ($isExported)  { $scanned[$key].exported = $true }
        if ($isLauncher)  { $scanned[$key].launcher = $true }
        foreach ($a in $actions) {
            if (-not $scanned[$key].intentActions.Contains($a)) {
                $scanned[$key].intentActions += $a
            }
        }
        foreach ($c in $categories) {
            if (-not $scanned[$key].intentCategories.Contains($c)) {
                $scanned[$key].intentCategories += $c
            }
        }
    }
}

# ── Compute noFlavors ─────────────────────────────────────────────────────────

foreach ($key in $scanned.Keys) {
    $rec = $scanned[$key]
    $presentFlavors = [System.Collections.Generic.List[string]]::new()
    foreach ($ss in $rec.presentInSets) {
        if ($flavorSetPresent.ContainsKey($ss)) {
            foreach ($f in $flavorSetPresent[$ss]) {
                if (-not $presentFlavors.Contains($f)) { $presentFlavors.Add($f) }
            }
        }
    }
    # wear module has no flavor variants - noFlavors is always empty
    if ($Module -eq "wear") {
        $rec.noFlavors = @()
    } else {
        $absent = $allFlavors | Where-Object { -not $presentFlavors.Contains($_) }
        $rec.noFlavors = @($absent)
    }
    # Remove helper field
    $rec.Remove("presentInSets")
}

# ── Source-file correlation + git lastTouched ─────────────────────────────────

$srcRoots = @(
    (Join-Path $Root "$Module\src\main\java"),
    (Join-Path $Root "$Module\src\vr\java")
) | Where-Object { Test-Path $_ }

foreach ($key in $scanned.Keys) {
    $rec = $scanned[$key]
    $relPath = ($rec.package -replace '\.','\') + ".kt"

    foreach ($srcRoot in $srcRoots) {
        $fullPath = Join-Path $srcRoot $relPath
        if (Test-Path $fullPath) {
            # Determine which sourceSet root this is
            $ssName = if ($srcRoot -match "\\vr\\java$") { "vr" } else { "main" }

            # Relative path within source root (use forward slashes)
            $rec.path      = ($relPath -replace '\\','/')
            $rec.sourceSet = $ssName
            $rec.loc       = (Get-Content -Path $fullPath -Encoding UTF8).Count

            # git lastTouched
            Push-Location $Root
            try {
                $gitOut = (& git log -1 --format="%as" -- $fullPath 2>$null)
                $rec.lastTouched = if ($gitOut) { $gitOut.Trim() } else { "" }
            } finally {
                Pop-Location
            }
            break
        }
    }
}

# ── Merge manual fields from existing JSONL ───────────────────────────────────

$existing = [ordered]@{}
if (Test-Path $OutFile) {
    foreach ($line in (Get-Content -Path $OutFile -Encoding UTF8)) {
        if (-not $line.Trim()) { continue }
        $r = $line | ConvertFrom-Json -AsHashtable
        $k = "$($r.module)|$($r.class)"
        $existing[$k] = $r
    }
}

foreach ($key in $scanned.Keys) {
    $rec = $scanned[$key]
    if ($existing.Contains($key)) {
        $ex = $existing[$key]
        # Preserve non-empty manual fields
        foreach ($field in @("role","roleRu","tags","status","notes")) {
            if ($ex.ContainsKey($field)) {
                $val = $ex[$field]
                if ($null -ne $val -and $val -ne "") {
                    $rec[$field] = $val
                }
            }
        }
    }
}

# ── Write JSONL ───────────────────────────────────────────────────────────────

$outDir = Split-Path $OutFile
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$lines = foreach ($key in $scanned.Keys) {
    $scanned[$key] | ConvertTo-Json -Compress -Depth 5
}
($lines -join "`n") + "`n" | Set-Content -Path $OutFile -Encoding UTF8 -NoNewline

$total = $scanned.Count
Write-Host "Scanned $total Activities -> $OutFile"
