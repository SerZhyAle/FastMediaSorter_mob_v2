<#
.SYNOPSIS
    Build, verify, and optionally install the debug watch face APK (:watchface) (S4134).

.DESCRIPTION
    Builds the debug variant of :watchface (applicationId = com.sza.fastmediasorter.watchface.debug).
    Verifies that:
      1. applicationId is com.sza.fastmediasorter.watchface.debug
      2. versionName ends with -DEBUG
      3. The packaged res/raw/watchface.xml differs from watchface/src/main/res/raw/watchface.xml
         in exactly ONE line (the <Launch target> targeting com.sza.fastmediasorter.debug).
    Optionally installs the debug APK via adb (-Install [-DeviceId <serial>]).

    Exit codes:
      0 - build and verification succeeded (and install succeeded if requested)
      1 - verification failed, artifact missing, or invalid parameters
      2 - build lock wait timed out
      3 - JVM / toolchain start failure
      <gradle/adb exit> - Gradle or adb execution failed

.PARAMETER Install
    Install the built debug APK to a connected watch or emulator via adb.

.PARAMETER DeviceId
    Target device serial for adb install. Required if multiple devices are connected and no watch/emulator is distinct.

.PARAMETER NoBuild
    Skip Gradle build; verify and/or install existing watchface-debug.apk.

.EXAMPLE
    pwsh -NoProfile -File scripts/builders/build-watchface-debug.ps1
    Same as .\a.ps1 wfd.

.EXAMPLE
    pwsh -NoProfile -File scripts/builders/build-watchface-debug.ps1 -Install
    Build, verify, and install on connected watch/emulator.
#>
[CmdletBinding()]
param(
    [switch] $Install,
    [string] $DeviceId,
    [switch] $NoBuild
)

$projectRoot = (Resolve-Path "$PSScriptRoot\..\..\").Path

if (-not $NoBuild) {
    . "$PSScriptRoot\..\utils\gradle-modules.ps1"
    $buildDomains = @(Get-GradleModuleBuildDomains -Name 'watchface')

    . "$PSScriptRoot\..\utils\agent-lock.ps1"
    Enter-BuildLockOrExit -Reason "build-watchface-debug.ps1" -Domain $buildDomains
    try {
        Write-Host "Building watch face debug APK [:watchface:assembleDebug].." -ForegroundColor Cyan
        & "$projectRoot\gradlew.bat" ':watchface:assembleDebug' --configuration-cache
        if ($LASTEXITCODE -ne 0) {
            $gradleExit = $LASTEXITCODE
            Write-Host "`nError: watch face debug build failed (gradle exit $gradleExit)." -ForegroundColor Red
            exit $gradleExit
        }
    }
    finally {
        Exit-AgentLock -Name 'Build' -Domains $buildDomains
    }
}

$apkPath = Join-Path $projectRoot 'watchface\build\outputs\apk\debug\watchface-debug.apk'
if (-not (Test-Path -LiteralPath $apkPath)) {
    Write-Host "Error: debug APK not found at $apkPath." -ForegroundColor Red
    exit 1
}

$metadataPath = Join-Path $projectRoot 'watchface\build\outputs\apk\debug\output-metadata.json'
$appId = $null
$versionName = $null
$versionCode = $null
if (Test-Path -LiteralPath $metadataPath) {
    try {
        $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
        $appId = $metadata.applicationId
        if ($metadata.elements -and $metadata.elements.Count -gt 0) {
            $versionName = $metadata.elements[0].versionName
            $versionCode = $metadata.elements[0].versionCode
        }
    }
    catch {
        Write-Host "Warning: could not parse $metadataPath : $_" -ForegroundColor DarkYellow
    }
}

$expectedAppId = 'com.sza.fastmediasorter.watchface.debug'
if ($appId -ne $expectedAppId) {
    Write-Host "Error: applicationId '$appId' does not match expected '$expectedAppId'." -ForegroundColor Red
    exit 1
}

if ($versionName -notmatch '-DEBUG$') {
    Write-Host "Error: versionName '$versionName' does not end with '-DEBUG'." -ForegroundColor Red
    exit 1
}

Write-Host "applicationId=$appId" -ForegroundColor Green
Write-Host "versionName=$versionName" -ForegroundColor Green
Write-Host "versionCode=$versionCode" -ForegroundColor Green

# Verify res/raw/watchface.xml packaged in the APK
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
$entry = $zip.GetEntry('res/raw/watchface.xml')
if (-not $entry) {
    $zip.Dispose()
    Write-Host "Error: res/raw/watchface.xml not found inside $apkPath." -ForegroundColor Red
    exit 1
}

$stream = $entry.Open()
$reader = [System.IO.StreamReader]::new($stream)
$apkXml = $reader.ReadToEnd()
$reader.Dispose()
$stream.Dispose()
$zip.Dispose()

$sourceXmlPath = Join-Path $projectRoot 'watchface\src\main\res\raw\watchface.xml'
if (-not (Test-Path -LiteralPath $sourceXmlPath)) {
    Write-Host "Error: source watchface.xml not found at $sourceXmlPath." -ForegroundColor Red
    exit 1
}
$sourceXml = Get-Content -LiteralPath $sourceXmlPath -Raw

$apkLines = $apkXml -split "`r?`n"
$sourceLines = $sourceXml -split "`r?`n"

if ($apkLines.Count -ne $sourceLines.Count) {
    Write-Host "Error: packaged watchface.xml line count ($($apkLines.Count)) differs from source ($($sourceLines.Count))." -ForegroundColor Red
    exit 1
}

$diffs = [System.Collections.Generic.List[object]]::new()
for ($i = 0; $i -lt $sourceLines.Count; $i++) {
    if ($sourceLines[$i] -ne $apkLines[$i]) {
        $diffs.Add([PSCustomObject]@{
            LineNumber = $i + 1
            Source = $sourceLines[$i]
            Packaged = $apkLines[$i]
        })
    }
}

if ($diffs.Count -ne 1) {
    Write-Host "Error: expected exactly 1 line diff in watchface.xml (<Launch target>), found $($diffs.Count):" -ForegroundColor Red
    foreach ($d in $diffs) {
        Write-Host "  Line $($d.LineNumber):`n    Source:   $($d.Source)`n    Packaged: $($d.Packaged)" -ForegroundColor Yellow
    }
    exit 1
}

$expectedOldTarget = 'target="com.sza.fastmediasorter/com.sza.fastmediasorter.wear.MainActivity"'
$expectedNewTarget = 'target="com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity"'
$diff = $diffs[0]
if ($diff.Source -notmatch [regex]::Escape($expectedOldTarget) -or $diff.Packaged -notmatch [regex]::Escape($expectedNewTarget)) {
    Write-Host "Error: line $($diff.LineNumber) difference is not the expected Launch target substitution:" -ForegroundColor Red
    Write-Host "    Source:   $($diff.Source)" -ForegroundColor Yellow
    Write-Host "    Packaged: $($diff.Packaged)" -ForegroundColor Yellow
    exit 1
}

Write-Host "Verified: packaged res/raw/watchface.xml matches source except line $($diff.LineNumber) (<Launch target> updated to debug watch app)." -ForegroundColor Green

if ($Install) {
    # adb is usually not on PATH on this workstation; a bare `& adb` throws a terminating-less error that
    # leaves $LASTEXITCODE untouched, which let a failed install print "Installed successfully" and exit 0.
    . "$PSScriptRoot\..\devtest\lib\find-adb.ps1"
    $adbExe = Find-Adb
    if (-not $adbExe) {
        Write-Host "Error: -Install requested but adb was not found (ANDROID_HOME, PATH, default SDK location)." -ForegroundColor Red
        exit 1
    }

    $targetSerial = $DeviceId
    if ([string]::IsNullOrWhiteSpace($targetSerial)) {
        $adbOut = & $adbExe devices 2>&1
        $devices = @()
        foreach ($line in ($adbOut -split "`r?`n")) {
            if ($line -match '^([^\s]+)\s+device$') {
                $devices += $Matches[1]
            }
        }

        if ($devices.Count -eq 0) {
            Write-Host "Error: -Install requested but no devices found via adb." -ForegroundColor Red
            exit 1
        }
        if ($devices.Count -eq 1) {
            $targetSerial = $devices[0]
        }
        else {
            $watchDev = $devices | Where-Object { $_ -like '*RFGL*' -or $_ -like 'emulator*' } | Select-Object -First 1
            if ($watchDev) {
                $targetSerial = $watchDev
            }
            else {
                Write-Host "Error: multiple devices found ($($devices -join ', ')). Specify -DeviceId <serial>." -ForegroundColor Red
                exit 1
            }
        }
    }

    Write-Host "Installing $expectedAppId to device $targetSerial.." -ForegroundColor Cyan
    $global:LASTEXITCODE = 0
    & $adbExe -s $targetSerial install -r $apkPath
    if (-not $?) {
        $adbExit = if ($LASTEXITCODE) { $LASTEXITCODE } else { 1 }
        Write-Host "Error: adb install failed on $targetSerial (exit $adbExit)." -ForegroundColor Red
        exit $adbExit
    }
    if ($LASTEXITCODE -ne 0) {
        $adbExit = $LASTEXITCODE
        Write-Host "Error: adb install failed on $targetSerial (exit $adbExit)." -ForegroundColor Red
        exit $adbExit
    }
    Write-Host "Installed debug watch face successfully on $targetSerial." -ForegroundColor Green
    Write-Host "Note: If re-installing after package-id changes, remove old dev face from watch before reinstall so slot bindings refresh." -ForegroundColor DarkGray
}

exit 0
