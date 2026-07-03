<#
.SYNOPSIS
    S0440 Phase 04 - composite settings-doc drift gate.

.DESCRIPTION
    One gate that proves the settings documentation is in sync with the app.
    Stages (any failure -> exit 1 naming the failing stage):
      1. catalog completeness (assert-settings-catalog-complete.ps1)
      2. manifest freshness (the SettingsManifestExportTest verify run)
      3. annotation coverage/parity (check-settings-annotations.ps1)
      4. reference freshness (re-render to a temp dir and byte-diff the committed
         published SETTINGS_REFERENCE*.md files)
      5. HOW_TO recipe freshness (S0558 - the "Settings -> .." recipes in the
         HOW_TO guides resolve against the manifest and stay in EN/RU/UK parity)
    Exit 0 only when all stages pass.

    Run with -Gate from post-change.ps1; without -Gate it behaves identically
    (the switch exists so the call site reads intentionally).
#>
param(
    [switch] $Gate,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch] $SkipManifestTest  # escape hatch for environments without a JVM/gradle
)

$ErrorActionPreference = 'Stop'
. (Join-Path $RepoRoot "scripts/utils/agent-lock.ps1")

function Fail([string] $stage, [string] $detail) {
    Write-Host "settings-doc-sync: FAIL at stage '$stage'" -ForegroundColor Red
    if ($detail) { Write-Host "  $detail" }
    exit 1
}

# Stage 1 - catalog completeness ------------------------------------------------
& (Join-Path $PSScriptRoot 'assert-settings-catalog-complete.ps1') -RepoRoot $RepoRoot
if ($LASTEXITCODE -ne 0) { Fail 'catalog-complete' 'a settings layout with rows is missing from SettingsSearchLayoutCatalog' }

# Stage 2 - manifest freshness (verify-mode test) -------------------------------
if (-not $SkipManifestTest) {
    Enter-BuildLockOrExit -Reason "assert-settings-doc-sync.ps1 (SettingsManifestExportTest)"
    Push-Location $RepoRoot
    try {
        & ".\gradlew.bat" ":app_v2:testStandardDebugUnitTest" "--tests" "*SettingsManifestExportTest" | Out-Null
        $manifestExit = $LASTEXITCODE
    } finally { Pop-Location; Exit-AgentLock -Name Build }
    if ($manifestExit -ne 0) { Fail 'manifest-fresh' 'committed settings-manifest.json differs from the live scan - regenerate with -Dsettings.manifest.generate=true' }
} else {
    Write-Host "settings-doc-sync: manifest test skipped (-SkipManifestTest)" -ForegroundColor Yellow
}

# Stage 3 - annotation coverage/parity ------------------------------------------
& (Join-Path $RepoRoot 'scripts/docs/check-settings-annotations.ps1')
if ($LASTEXITCODE -ne 0) { Fail 'annotations' 'a manifest key is unannotated, orphaned, or has an empty en/ru/uk value' }

# Stage 4 - reference freshness (re-render + diff) ------------------------------
$tmp = Join-Path $RepoRoot 'temp/_settings_ref_gate'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
try {
    & (Join-Path $RepoRoot 'scripts/docs/render-settings-reference.ps1') -RepoRoot $RepoRoot -OutDir $tmp | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'reference-render' 'renderer returned non-zero' }
    $published = @('SETTINGS_REFERENCE.md', 'SETTINGS_REFERENCE_RU.md', 'SETTINGS_REFERENCE_UK.md')
    foreach ($f in $published) {
        $committed = Join-Path $RepoRoot "docs/$f"
        $fresh = Join-Path $tmp $f
        if (-not (Test-Path $committed)) { Fail 'reference-fresh' "committed docs/$f is missing - run render-settings-reference.ps1" }
        $a = [System.IO.File]::ReadAllText($committed)
        $b = [System.IO.File]::ReadAllText($fresh)
        if ($a -ne $b) { Fail 'reference-fresh' "docs/$f is stale - re-run scripts/docs/render-settings-reference.ps1" }
    }
} finally {
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
}

# Stage 5 - HOW_TO settings-path freshness (S0558) ------------------------------
& (Join-Path $PSScriptRoot 'assert-howto-settings-paths.ps1') -RepoRoot $RepoRoot
if ($LASTEXITCODE -ne 0) { Fail 'howto-paths' 'a HOW_TO "Settings -> .." recipe drifted from the manifest - see the lines above' }

Write-Host "settings-doc-sync: OK - catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync." -ForegroundColor Green
exit 0
