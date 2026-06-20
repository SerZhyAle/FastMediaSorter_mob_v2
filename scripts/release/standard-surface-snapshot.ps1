<#
.SYNOPSIS
  S0553 - canonical standard production surface snapshot.

.DESCRIPTION
  Emits the machine-readable standard release baseline by folding two sources
  (research/01 - standard-baseline-source-of-truth):
    - docs/ALL_FEATURES.jsonl : records whose `flavors` contains "standard" and
      whose `status` is "active" (the developer capability inventory).
    - app_v2/build.gradle.kts  : the `standard` product-flavor BuildConfig flags
      (SUPPORT_*, ENABLE_*, SUPPORTS_DEFAULT_PLAYER).

  docs/FEATURES.md is the curated showcase and is intentionally NOT consulted -
  the gate baseline is derived from the two machine sources only.

  Exit codes:
    0 - snapshot emitted (default) / no regression candidates (-CheckRegressions)
    1 - regression candidate(s) found (only with -CheckRegressions)
    2 - infrastructure abort (source files missing / unreadable)

.PARAMETER OutFile
  Where to write the snapshot JSON. Default: temp/standard-surface-snapshot.json.

.PARAMETER Json
  Write the snapshot object to stdout instead of (only) the file.

.PARAMETER CheckRegressions
  Cross-check standard-supported capabilities against the BuildConfig flags and
  list any whose gating flag is false/absent (candidate S0553 §5.2 regression).

.EXAMPLE
  pwsh -NoProfile -File scripts/release/standard-surface-snapshot.ps1 -Json

.EXAMPLE
  pwsh -NoProfile -File scripts/release/standard-surface-snapshot.ps1 -CheckRegressions
#>
[CmdletBinding()]
param(
    [string]$OutFile = 'temp/standard-surface-snapshot.json',
    [switch]$Json,
    [switch]$CheckRegressions
)

$ErrorActionPreference = 'Stop'
trap { Write-Host "ERROR: $_" -ForegroundColor Red; exit 1 }

$repoRoot   = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$featuresJsonl = Join-Path $repoRoot 'docs/ALL_FEATURES.jsonl'
$buildGradle   = Join-Path $repoRoot 'app_v2/build.gradle.kts'

if (-not (Test-Path $featuresJsonl)) { Write-Host 'ALL_FEATURES.jsonl not found' -ForegroundColor Red; exit 2 }
if (-not (Test-Path $buildGradle))   { Write-Host 'build.gradle.kts not found' -ForegroundColor Red; exit 2 }

# ---- standard capabilities from the developer inventory -------------------
$capabilities = @()
foreach ($line in Get-Content -Path $featuresJsonl) {
    $t = $line.Trim()
    if (-not $t) { continue }
    $rec = $t | ConvertFrom-Json
    if (($rec.flavors -contains 'standard') -and ($rec.status -eq 'active')) {
        $capabilities += [ordered]@{ id = $rec.id; area = $rec.area; name = $rec.name }
    }
}

# ---- standard BuildConfig flags ------------------------------------------
$gradleRaw = Get-Content -Raw -Path $buildGradle
# Isolate the `create("standard") { ... }` segment: from the standard marker up to
# the next flavor `create("` declaration. Split is robust to line-number drift.
$stdStart = $gradleRaw.IndexOf('create("standard")')
if ($stdStart -lt 0) { Write-Host 'standard flavor block not found' -ForegroundColor Red; exit 2 }
$rest = $gradleRaw.Substring($stdStart + 'create("standard")'.Length)
$nextCreate = $rest.IndexOf('create("')
$stdBlock = if ($nextCreate -ge 0) { $rest.Substring(0, $nextCreate) } else { $rest }

$flags = [ordered]@{}
foreach ($m in [regex]::Matches($stdBlock, 'buildConfigField\(\s*"boolean"\s*,\s*"(\w+)"\s*,\s*"(true|false)"')) {
    $flags[$m.Groups[1].Value] = [bool]($m.Groups[2].Value -eq 'true')
}

# ---- regression check ----------------------------------------------------
# Flags that define the standard market surface and MUST be true for standard.
$expectedTrue = @(
    'SUPPORT_VIDEO','SUPPORT_AUDIO','SUPPORT_IMAGES','SUPPORT_CLOUD','SUPPORT_LOCAL_NETWORK',
    'SUPPORT_DOCUMENTS','ENABLE_ANIMATIONS','ENABLE_EPUB','ENABLE_TRANSLATION',
    'ENABLE_PERSISTENT_AUDIO_PLAYBACK','SUPPORTS_DEFAULT_PLAYER','SUPPORT_WEAR_COMPANION','SUPPORT_CAST'
)
$regressionCandidates = @()
foreach ($f in $expectedTrue) {
    if (-not $flags.Contains($f)) { $regressionCandidates += "$f (absent)" }
    elseif (-not $flags[$f])      { $regressionCandidates += "$f (false)" }
}

$snapshot = [ordered]@{
    generatedFor        = 'standard'
    sources             = @('docs/ALL_FEATURES.jsonl', 'app_v2/build.gradle.kts')
    buildConfig         = $flags
    capabilities        = $capabilities
    capabilityCount     = $capabilities.Count
    regressionCandidates = $regressionCandidates
}

# ---- output --------------------------------------------------------------
$outDir = Split-Path -Parent (Join-Path $repoRoot $OutFile)
if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
$snapshotJson = ConvertTo-Json -InputObject $snapshot -Depth 6
Set-Content -Path (Join-Path $repoRoot $OutFile) -Value $snapshotJson -Encoding utf8

if ($CheckRegressions) {
    if ($regressionCandidates.Count -gt 0) {
        Write-Host "REGRESSION CANDIDATES ($($regressionCandidates.Count)):" -ForegroundColor Red
        $regressionCandidates | ForEach-Object { Write-Host "  - $_" }
        exit 1
    }
    Write-Host "standard surface clean: $($capabilities.Count) capabilities, no flag regressions" -ForegroundColor Green
    exit 0
}

if ($Json) { $snapshotJson }
else { Write-Host "snapshot written: $OutFile ($($capabilities.Count) capabilities)" -ForegroundColor Green }
exit 0
