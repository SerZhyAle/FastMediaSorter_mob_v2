# Release build script for Wear OS
# Note: this build carries the version constants checked into wear/build.gradle.kts as-is,
# unless -VersionName/-VersionCode pin one explicitly.
# Nothing here is generated from the clock - a release version is stamped into BOTH modules
# by scripts/release/build-release-spectrum.ps1, so the watch and the phone stay one version.
#
# Exit codes:
#   0 - requested artifacts built and copied
#   1 - artifact missing after a successful Gradle run, or an argument is unusable
#   <gradle exit> - the Gradle build itself failed
[CmdletBinding()]
param(
    # Build the artifacts but publish none of them: no DOWNLOADS copy, no journal line, no Google
    # Drive mirror. For a caller that needs the artifact in wear/build/outputs and nothing else -
    # the pre-release run (S1984), whose job is to judge a build, not to hand it to anyone. Without
    # this the verification run overwrites the Drive copy and the AAB publish-play-release.ps1
    # addresses by path with an unstamped ad-hoc build, which is why that run's build branch had
    # never once been executed. The phone sweep avoids the same trap by refusing its own
    # distributing builder outright (scripts/devtest/prerelease-prepare.ps1, stage 2).
    [switch] $NoDistribute,

    # Apk  - sideload asset for GitHub releases (the historic behaviour, still the default).
    # Aab  - the only artifact the Play Wear OS track accepts; Play refuses an APK (S1707).
    # Both - one Gradle invocation produces the pair, so the store artifact and the sideload
    #        asset are provably the same build rather than two runs minutes apart.
    [ValidateSet('Apk', 'Aab', 'Both')]
    [string] $Artifact = 'Apk',

    # Pin the published version instead of whatever is checked in. Both must be given together:
    # a name without a code, or a code without a name, produces an artifact that disagrees with
    # the release it claims to be.
    [string] $VersionName,
    [int] $VersionCode
)

if ([string]::IsNullOrWhiteSpace($VersionName) -xor ($VersionCode -le 0)) {
    Write-Host "Error: -VersionName and -VersionCode must be supplied together." -ForegroundColor Red
    exit 1
}

$versionArgs = @()
if (-not [string]::IsNullOrWhiteSpace($VersionName)) {
    $versionArgs += "-Pfms.versionName=$VersionName"
    $versionArgs += "-Pfms.versionCode=$VersionCode"
}

$buildApk = $Artifact -in @('Apk', 'Both')
$buildAab = $Artifact -in @('Aab', 'Both')

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "build-wear-release.PS1"
try {

$gradleTasks = @()
if ($buildApk) { $gradleTasks += ':wear:assembleRelease' }
if ($buildAab) { $gradleTasks += ':wear:bundleRelease' }

$versionLabel = if ($versionArgs.Count -gt 0) { "$VersionName ($VersionCode)" } else { 'version as checked into wear/build.gradle.kts' }
Write-Host "Building Wear OS release [$($gradleTasks -join ', ')] - $versionLabel.." -ForegroundColor Cyan

# Start the Gradle build process
& "$PSScriptRoot\..\..\gradlew.bat" @gradleTasks @versionArgs --configuration-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$downloadsDir = "$projectRoot\DOWNLOADS"
if (-not $NoDistribute -and !(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}

if ($buildAab) {
    # The Play Wear OS track accepts a bundle only. Kept next to the phone AAB under the same
    # naming scheme so publish-play-release.ps1 can address either by path alone.
    $aabDir = Join-Path $projectRoot "wear\build\outputs\bundle\release"
    $aab = Get-ChildItem -Path $aabDir -Filter *.aab -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $aab) {
        Write-Host "Error: AAB not found in $aabDir" -ForegroundColor Red
        exit 1
    }
    if ($NoDistribute) {
        Write-Host "AAB built at $($aab.FullName) - not copied (-NoDistribute)" -ForegroundColor Yellow
    }
    else {
        $aabDest = Join-Path $downloadsDir "FastMediaSorter_wear_release.aab"
        Copy-Item -Path $aab.FullName -Destination $aabDest -Force
        Write-Host "AAB copied to $aabDest ($([math]::Round($aab.Length / 1MB, 2)) MB)" -ForegroundColor Green
    }
}

if ($buildApk) {

# Resolve the built APK path from AGP output metadata.
$apkDir = Join-Path $projectRoot "wear\build\outputs\apk\release"
# S1972: one resolver for every builder - it selects by ABI from output-metadata.json
# and refuses to guess, where this block used to take element 0 and then the newest file.
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
$resolvedArtifact = Find-BuildArtifact -Dir $apkDir
$apkPath = if ($resolvedArtifact) { $resolvedArtifact.FullName } else { $null }

if (-not $apkPath -or -not (Test-Path -Path $apkPath)) {
    Write-Host "Error: APK not found in $apkDir" -ForegroundColor Red
    exit 1
}

Write-Host "APK location: $apkPath" -ForegroundColor Cyan

if ($NoDistribute) {
    Write-Host "APK built - not copied, not journalled, not mirrored (-NoDistribute)" -ForegroundColor Yellow
}
else {

$destName = "FastMediaSorter_wear_release.apk"
Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | wear-release | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

# Mirror to Google Drive - raw file plus password ZIP. The pair used to be written out
# here; it now lives in one place so the release path can call the same thing.
& "$PSScriptRoot\..\utils\copy-to-drive.ps1" -Path "$downloadsDir\$destName" -Name $destName

}   # end if (-not $NoDistribute)

}   # end if ($buildApk)

}
finally {
    Exit-AgentLock -Name Build
}
