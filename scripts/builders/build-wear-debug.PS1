# Quick debug build script for Wear OS
# Version format: Y.YM.MDDH.Hmm - byte-identical to app_v2. The watch versionCode is yyMMddHH
# (8 digits); app_v2 appends the first minute digit and gets 9, so the two never collide under the
# shared applicationId (S1681). A release stamps both modules from one timestamp via
# scripts/release/build-release-spectrum.ps1; this script stamps only its own debug artifact.

param(
    # The wear debug APK is a sideload DISTRIBUTION artifact - it is copied to DOWNLOADS and to
    # Google Drive and installed on the watch by hand - so it must carry the real build timestamp
    # by default (S1816). Frozen versions belong to the compile-only fast checks (fw/fwr/fwu) that
    # produce no APK. Without a stamp two watch builds separated by any amount of code change
    # report one identical versionName, and the device cannot say which one it is running.
    # Pass -AutoVersion:$false to opt back into the checked-in frozen version when
    # configuration-cache reuse matters more than a fresh version.
    [switch]$AutoVersion = $true,

    # S2090: which watch variant to build. 'standard' mirrors what ships and stays the default;
    # 'noLegal' is the sideload variant.
    [ValidateSet('standard', 'noLegal')]
    [string]$Flavor = 'standard'
)

. "$PSScriptRoot\..\utils\agent-lock.ps1"
. "$PSScriptRoot\..\utils\project-paths.ps1"
Enter-BuildLockOrExit -Reason "build-wear-debug.PS1" -Domain Build.Wear
try {

# AGP capitalises the flavor inside a task name and lower-cases it inside an output path.
$taskFlavor = $Flavor.Substring(0, 1).ToUpperInvariant() + $Flavor.Substring(1)

Write-Host "Building Wear OS debug APK [$Flavor].." -ForegroundColor Cyan

# Start the Gradle build process (Debug only for speed)
$gradleArgs = @(":wear:assemble${taskFlavor}Debug", "--configuration-cache")
. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
if ($AutoVersion) {
    # 8 digits, deliberately one shorter than app_v2's 9: the documented derivation is
    # wear = floor(app / 10), and Play refuses two artifacts repeating a versionCode under one
    # applicationId. assert-module-version-parity.ps1 guards the checked-in pair; this override is
    # per-invocation and invisible to it.
    $stamp = Get-BuildVersionStamp
    $versionCodeInt = $stamp.WearVersionCode
    $versionName = $stamp.VersionName
    Write-Host "Version override: $versionName (code: $versionCodeInt)" -ForegroundColor Green
    $gradleArgs += @(
        "-Pfms.versionCode=$versionCodeInt",
        "-Pfms.versionName=$versionName"
    )
}
else {
    Write-Host "Mode: frozen-version build (-AutoVersion:`$false, config-cache reuse)" -ForegroundColor Yellow
}

& "$PSScriptRoot\..\..\gradlew.bat" @gradleArgs

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nBuild Failed! Exiting..." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nBuild Successful!" -ForegroundColor Green

# Copy to DOWNLOADS folder
$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"
$downloadsDir = "$projectRoot\DOWNLOADS"

# Resolve the built APK path from AGP output metadata.
$apkDir = Join-Path $projectRoot "wear\build\outputs\apk\$Flavor\debug"
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

if (!(Test-Path -Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir | Out-Null
}
$destName = "FastMediaSorter_wear_debug.apk"
# Judge what was produced, not what was intended. A packaging path that lost its version
# property still writes a fresh file - only the artifact's own metadata shows that the
# version inside it belongs to an older build (S1873). Checked before the copy, because the
# copy is what puts it in DOWNLOADS and on Google Drive.
if ($AutoVersion) {
    & (Join-Path $PSScriptRoot "..\quality\assert-artifact-version-fresh.ps1") -Path $apkDir
    if ($LASTEXITCODE -eq 1) {
        Write-Host "Refusing to distribute an artifact whose version is not its own." -ForegroundColor Red
        exit 1
    }
}

Copy-Item -Path $apkPath -Destination "$downloadsDir\$destName" -Force
Write-Host "APK copied to $downloadsDir\$destName" -ForegroundColor Green

# Log build to journal
$journalPath = "$downloadsDir\builds_versions.lst"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$logEntry = "$timestamp | wear-debug | $destName"
Add-Content -Path $journalPath -Value $logEntry
Write-Host "Build logged to journal" -ForegroundColor Gray

# The watch APK reaches the Commander folder too, unlike its five siblings that mirror to Drive
# alone: it is the one debug artifact routinely sideloaded from there onto a paired watch.
& "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName

}
finally {
    Exit-AgentLock -Name 'Build' -Domains @('Build.Wear')
}
