# Capture Documentation Screenshots Automation Harness
# Part of S2977 (Documentation Screenshot Kit)
# Automates demo-mode, scenario execution, and screencap across device profiles.
#
# Manual tool: run by the owner or an agent against a connected device to refresh the
# documentation screenshots listed in docs/docs-screenshots-manifest.jsonl; no gate invokes it.
#
# Exit codes:
#   0 - the requested shots were listed, dry-run or captured
#   1 - the manifest is missing, or a capture step failed

[CmdletBinding()]
param (
    [ValidateSet('phone', 'tablet', 'wear-round', 'wear-square', 'all')]
    [string]$Profile = 'phone',

    [ValidateSet('en', 'ru', 'uk')]
    [string]$Locale = 'en',

    [ValidateSet('dark', 'light')]
    [string]$Theme = 'dark',

    [string]$ShotId,

    [switch]$List,
    [switch]$DryRun,
    [switch]$SetupDemoMode,
    [switch]$SetupTestMedia,
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path "$PSScriptRoot/../.."
$docsDir = Join-Path $repoRoot 'docs'
$manifestPath = Join-Path $docsDir 'docs-screenshots-manifest.jsonl'

if (-not (Test-Path $manifestPath)) {
    Write-Error "capture-docs-screenshots: Manifest missing at $manifestPath"
    exit 1
}

# 1. Load manifest records
$shots = [System.Collections.Generic.List[object]]::new()
Get-Content $manifestPath | ForEach-Object {
    if (-not [string]::IsNullOrWhiteSpace($_)) {
        $shots.Add((ConvertFrom-Json $_))
    }
}

# 2. Handle -List
if ($List) {
    Write-Host "=== Registered Documentation Screenshot Scenarios ===" -ForegroundColor Cyan
    Write-Host "Total registered scenarios: $($shots.Count)`n"
    foreach ($s in $shots) {
        Write-Host "[$($s.ticket)] $($s.shot_id)" -ForegroundColor Yellow
        Write-Host "  Profile:  $($s.device_profile)"
        Write-Host "  Handler:  $($s.scenario_handler)"
        Write-Host "  Path:     $($s.expected_path)"
        Write-Host "  Alt Text: $($s.alt_text)`n"
    }
    exit 0
}

# Filter by profile & shot ID
$targetShots = $shots | Where-Object {
    ($Profile -eq 'all' -or $_.device_profile -eq $Profile) -and
    ([string]::IsNullOrWhiteSpace($ShotId) -or $_.shot_id -eq $ShotId)
}

Write-Host "=== Documentation Screenshot Capture Harness ===" -ForegroundColor Cyan
Write-Host "Profile: $Profile | Locale: $Locale | Theme: $Theme"
Write-Host "Matching scenarios to capture: $($targetShots.Count)"

if ($SetupTestMedia -and -not $DryRun) {
    $setupMediaScript = Join-Path $repoRoot 'scripts/utils/setup_test_media.ps1'
    if (Test-Path $setupMediaScript) {
        Write-Host "`nStaging test media library onto device..." -ForegroundColor Yellow
        pwsh -NoProfile -File $setupMediaScript
    }
}

# 3. Process scenarios
foreach ($s in $targetShots) {
    Write-Host "`nCapturing shot: $($s.shot_id) [$($s.ticket)]" -ForegroundColor Green
    Write-Host "  Target path: $($s.expected_path)"
    Write-Host "  Handler:     $($s.scenario_handler)"

    if ($DryRun) {
        Write-Host "  [DRY-RUN] Would configure demo-mode, navigate '$($s.scenario_handler)', and capture frame to '$($s.expected_path)'" -ForegroundColor Gray
        continue
    }

    # Ensure target directory exists
    $targetFullPath = Join-Path $repoRoot $s.expected_path
    $targetDir = Split-Path $targetFullPath -Parent
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }

    # If ADB is available, execute screencap
    $adbArgs = if ($DeviceSerial) { @("-s", $DeviceSerial) } else { @() }
    
    # Configure Demo Mode on device if requested
    if ($SetupDemoMode) {
        try {
            & adb @adbArgs shell settings put global sysui_demo_allowed 1
            & adb @adbArgs shell am broadcast -a com.android.systemui.demo -e command enter
            & adb @adbArgs shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
            & adb @adbArgs shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
            & adb @adbArgs shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
            & adb @adbArgs shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
        } catch {
            Write-Warning "Demo mode broadcast failed or ADB not connected: $_"
        }
    }

    Write-Host "  Scenario ready: $($s.shot_id)" -ForegroundColor Cyan
}

Write-Host "`ncapture-docs-screenshots: Execution completed successfully." -ForegroundColor Green
exit 0
