<#
.SYNOPSIS
    Build the signed Play bundle of the watch face (:watchface) and prove its signature (S4009).

.DESCRIPTION
    The face is its own Play app (com.sza.fastmediasorter.watchface), so its versionCode only has to
    rise inside its own package. -VersionName/-VersionCode carry the Wear campaign's shared stamp;
    omitted, the invocation clock applies through Get-BuildVersionStamp and the face takes the
    watch-derived code, as scripts/builders/build-wear-release.PS1 does for the watch.

    After gradle the bundle is proven signed (jarsigner -verify) with the pinned upload key
    (scripts/release/expected-signing-fingerprint.txt), its packaged version is read back from the
    bundle manifest, and it is copied to DOWNLOADS/FastMediaSorter_watchface_release.aab - the path
    the release publisher addresses. Deliberately NOT part of build-release-spectrum.ps1: the face
    releases with the Wear campaign, not with the phone spectrum (research 04).

    Exit codes:
      0 - signed bundle built, fingerprint matches the pin, bundle copied (or kept, -NoDistribute)
      1 - an argument is unusable, the keystore properties file is absent, the bundle is missing
          after a successful gradle run, the bundle is unsigned, the fingerprint differs from the
          pin, the packaged version differs from the requested one, or no JDK signing tool exists
      2 - the build lock wait ran out of time (Enter-BuildLockOrExit)
      3 - the launcher or toolchain JVM cannot start (Enter-BuildLockOrExit)
      <gradle exit> - the Gradle build itself failed

.PARAMETER VersionName
    Shared versionName of the Wear release. Supply together with -VersionCode or not at all.

.PARAMETER VersionCode
    versionCode to package. Supply together with -VersionName or not at all.

.PARAMETER NoDistribute
    Build and verify, but do not copy the bundle to DOWNLOADS.

.EXAMPLE
    pwsh -NoProfile -File scripts/builders/build-watchface-release.ps1
    Same as .\a.ps1 wfr.
#>
[CmdletBinding()]
param(
    [string] $VersionName,
    [int] $VersionCode,
    [switch] $NoDistribute
)

$projectRoot = (Resolve-Path "$PSScriptRoot\..\..\").Path

if ([string]::IsNullOrWhiteSpace($VersionName) -xor ($VersionCode -le 0)) {
    Write-Host "Error: -VersionName and -VersionCode must be supplied together." -ForegroundColor Red
    exit 1
}

# The gradle config signs only when this file exists, so a missing file would otherwise surface as
# an unsigned bundle after a full build. Refuse before any gradle work.
$keystoreProperties = @('.secrets\keystore.properties', 'keystore.properties') |
    ForEach-Object { Join-Path $projectRoot $_ } |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
if (-not $keystoreProperties) {
    Write-Host "Error: release keystore properties not found (.secrets\keystore.properties or keystore.properties) - the face bundle would be unsigned and Play refuses it." -ForegroundColor Red
    exit 1
}

$pinFile = Join-Path $projectRoot 'scripts\release\expected-signing-fingerprint.txt'
$expectedFingerprint = Get-Content -LiteralPath $pinFile -ErrorAction SilentlyContinue |
    Where-Object { $_ -notmatch '^\s*#' -and $_.Trim() -ne '' } |
    Select-Object -First 1
if (-not $expectedFingerprint) {
    Write-Host "Error: no pinned fingerprint in $pinFile." -ForegroundColor Red
    exit 1
}
$expectedFingerprint = $expectedFingerprint.Trim().ToUpperInvariant()

# JAVA_HOME can point at a JDK that is no longer installed, so each candidate is tested for the tool
# itself rather than trusted by name.
function Resolve-JdkTool {
    param([Parameter(Mandatory)][string] $Name)
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME "bin\$Name.exe" }
    $onPath = Get-Command $Name -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    $candidates += Get-ChildItem -Path "$env:ProgramFiles\Java" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName "bin\$Name.exe" }
    return $candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
}

$jarsigner = Resolve-JdkTool -Name 'jarsigner'
$keytool = Resolve-JdkTool -Name 'keytool'
if (-not $jarsigner -or -not $keytool) {
    Write-Host "Error: jarsigner/keytool not found in JAVA_HOME, PATH or $env:ProgramFiles\Java - the signature cannot be verified." -ForegroundColor Red
    exit 1
}

. "$PSScriptRoot\..\utils\build-version-stamp.ps1"
if ([string]::IsNullOrWhiteSpace($VersionName)) {
    $stamp = Get-BuildVersionStamp
    $VersionName = $stamp.VersionName
    $VersionCode = $stamp.WearVersionCode
}
$versionArgs = @("-Pfms.versionName=$VersionName", "-Pfms.versionCode=$VersionCode")

# The face has no build domain of its own: the registry widens it to the full build set.
. "$PSScriptRoot\..\utils\gradle-modules.ps1"
$buildDomains = @(Get-GradleModuleBuildDomains -Name 'watchface')

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "build-watchface-release.ps1" -Domain $buildDomains
try {
    Write-Host "Building watch face release bundle [:watchface:bundleRelease] - $VersionName ($VersionCode).." -ForegroundColor Cyan
    & "$projectRoot\gradlew.bat" ':watchface:bundleRelease' @versionArgs --configuration-cache
    if ($LASTEXITCODE -ne 0) {
        $gradleExit = $LASTEXITCODE
        Write-Host "`nError: watch face bundle build failed (gradle exit $gradleExit)." -ForegroundColor Red
        exit $gradleExit
    }

    $aabDir = Join-Path $projectRoot 'watchface\build\outputs\bundle\release'
    $aab = Get-ChildItem -Path $aabDir -Filter *.aab -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $aab) {
        Write-Host "Error: AAB not found in $aabDir after a successful gradle run." -ForegroundColor Red
        exit 1
    }

    $verifyOutput = & $jarsigner -verify $aab.FullName 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or $verifyOutput -notmatch 'jar verified') {
        Write-Host "Error: $($aab.Name) is not signed - jarsigner -verify said:`n$verifyOutput" -ForegroundColor Red
        exit 1
    }

    $certOutput = & $keytool -printcert -jarfile $aab.FullName 2>&1 | Out-String
    $shaMatch = [regex]::Match($certOutput, 'SHA256:\s*([0-9A-Fa-f:]{95})')
    if (-not $shaMatch.Success) {
        Write-Host "Error: no SHA-256 signer certificate readable from $($aab.Name)." -ForegroundColor Red
        exit 1
    }
    $actualFingerprint = $shaMatch.Groups[1].Value.ToUpperInvariant()
    Write-Host "Signer SHA-256: $actualFingerprint"
    if ($actualFingerprint -ne $expectedFingerprint) {
        Write-Host "Error: signer fingerprint differs from the pin $expectedFingerprint ($pinFile)." -ForegroundColor Red
        exit 1
    }

    # The manifest AGP merged for this bundle is the version Play will read; echoing the requested
    # pair would report what was asked for, not what was packaged.
    $bundleManifest = Join-Path $projectRoot 'watchface\build\intermediates\bundle_manifest\release\processApplicationManifestReleaseForBundle\AndroidManifest.xml'
    $manifestText = Get-Content -LiteralPath $bundleManifest -Raw -ErrorAction SilentlyContinue
    $packagedCode = [regex]::Match([string]$manifestText, 'android:versionCode="(\d+)"').Groups[1].Value
    $packagedName = [regex]::Match([string]$manifestText, 'android:versionName="([^"]+)"').Groups[1].Value
    if ($packagedCode -ne [string]$VersionCode -or $packagedName -ne $VersionName) {
        Write-Host "Error: packaged version '$packagedName ($packagedCode)' differs from the requested '$VersionName ($VersionCode)' ($bundleManifest)." -ForegroundColor Red
        exit 1
    }
    Write-Host "versionName: $packagedName"
    Write-Host "versionCode: $packagedCode"

    if ($NoDistribute) {
        Write-Host "Signed bundle at $($aab.FullName) - not copied (-NoDistribute)" -ForegroundColor Yellow
        exit 0
    }
    $downloadsDir = Join-Path $projectRoot 'DOWNLOADS'
    if (-not (Test-Path -LiteralPath $downloadsDir)) {
        New-Item -ItemType Directory -Path $downloadsDir | Out-Null
    }
    $aabDest = Join-Path $downloadsDir 'FastMediaSorter_watchface_release.aab'
    Copy-Item -LiteralPath $aab.FullName -Destination $aabDest -Force
    Write-Host "Signed watch face bundle copied to $aabDest ($([math]::Round($aab.Length / 1KB, 1)) KB)" -ForegroundColor Green
    exit 0
}
finally {
    Exit-AgentLock -Name 'Build' -Domains $buildDomains
}
