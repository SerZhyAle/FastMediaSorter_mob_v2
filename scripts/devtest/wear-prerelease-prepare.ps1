<#
.SYNOPSIS
    S1984 - prepare a watch for the pre-release run: qualify the device, build the release
    artifacts, record which artifact is about to be judged, install it and start it.

.DESCRIPTION
    The phone sweep has `prerelease-prepare.ps1`; the watch had nothing, so every watch release was
    assembled by hand from a set of gates chosen afresh each time, with no return code and no record
    of which artifact the run actually looked at (strategic S1984 section 1).

    This script is the watch half. It refuses anything that is not a watch, because both modules
    publish under one application id and a run that lands on the phone would produce a confident
    verdict about the wrong build. It builds through `scripts/builders/build-wear-release.PS1`
    rather than calling gradle, so the artifact the run judges is the artifact that path produces
    (strategic ADR-3), and that builder is also where `BUILD.LOCK` is taken - acquiring it here too
    would deadlock the chain against itself.

    Every device action goes through `scripts/devtest/adb.ps1`; this file contains no raw `adb` call.

.PARAMETER DeviceId
    Serial of the watch. Omitted: the script lists devices and refuses when the choice is not
    unambiguous - picking one would be guessing which device a release verdict is about.

.PARAMETER OutDir
    Where the run artifacts land. Default matches the directory the first hand-run sweep used.

.PARAMETER SkipBuild
    Use the artifacts already on disk instead of building. Recorded in the output, never silent.

.PARAMETER Json
    Emit the result object instead of the human lines.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/wear-prerelease-prepare.ps1 -DeviceId 192.168.1.166:46551

.NOTES
    Exit codes:
      0  prepared: artifacts recorded, installed and launched on a qualified watch
      1  a step failed: the build, the install or the launch returned non-zero
      2  could not verify: no unambiguous device, the device is not a watch or is below the
         module's minSdk, an artifact is missing, or a script this one calls is absent
#>
[CmdletBinding()]
param(
    [string]$DeviceId,

    [string]$OutDir = 'temp/scratch/wear-prerelease',

    [switch]$SkipBuild,

    # S2090: which watch variant this sweep judges. 'standard' is what reaches Play and stays the
    # default; 'noLegal' is the sideload variant, swept on request (ADR-6).
    [ValidateSet('standard', 'noLegal')]
    [string]$WearFlavor = 'standard',

    [switch]$Json
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$WEAR_MIN_SDK = 28
# The split where READ_EXTERNAL_STORAGE gives way to the three per-type media permissions.
$MEDIA_PERMISSION_SDK = 33
# The watch release publishes under the unsuffixed id; the debug build adds `.debug` and is a
# different app on the same device.
$RELEASE_PACKAGE = 'com.sza.fastmediasorter'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. "$PSScriptRoot\..\utils\find-build-artifact.ps1"
$adbWrapper = Join-Path $repoRoot 'scripts/devtest/adb.ps1'
$wearBuilder = Join-Path $repoRoot 'scripts/builders/build-wear-release.PS1'

$result = [ordered]@{
    ok               = $false
    exitCode         = 2
    device           = $null
    skipBuild        = [bool]$SkipBuild
    artifacts        = $null
    outDir           = $null
    launchedPackage  = $null
    grantedPermissions = @()
    reason           = $null
}

function Stop-Run {
    param([int]$Code, [string]$Reason)
    $result.exitCode = $Code
    $result.ok = ($Code -eq 0)
    $result.reason = $Reason
    if ($Json) { [pscustomobject]$result | ConvertTo-Json -Depth 6 -Compress }
    else { Write-Error "wear-prerelease-prepare: $Reason" -ErrorAction Continue }
    exit $Code
}

foreach ($required in @($adbWrapper, $wearBuilder)) {
    if (-not (Test-Path -LiteralPath $required)) { Stop-Run 2 "required script not found: $required" }
}

function Invoke-AdbVerb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $output = & pwsh -NoProfile -File $adbWrapper @Arguments 2>&1
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = ($output -join "`n") }
}

# --- Device selection -------------------------------------------------------------------------

$id = $DeviceId
if (-not $id) {
    $listing = Invoke-AdbVerb -Arguments @('devices', '-Json')
    if ($listing.Exit -ne 0) { Stop-Run 2 "could not list devices: $($listing.Output)" }
    try { $devices = @(($listing.Output | ConvertFrom-Json).data) }
    catch { Stop-Run 2 "could not parse the device listing: $($listing.Output)" }

    if ($devices.Count -eq 0) { Stop-Run 2 'no device is attached' }
    if ($devices.Count -gt 1) {
        $names = ($devices | ForEach-Object { "$($_.id) ($($_.model))" }) -join ', '
        Stop-Run 2 "several devices are attached ($names) - pass -DeviceId, because picking one would be guessing which device the verdict is about"
    }
    $id = $devices[0].id
}
$result.device = $id

function Get-DeviceProp {
    param([Parameter(Mandatory)][string]$Name)
    $res = Invoke-AdbVerb -Arguments @('shell', '-Cmd', "getprop $Name", '-DeviceId', $id)
    if ($res.Exit -ne 0) { Stop-Run 2 "could not read $Name from ${id}: $($res.Output)" }
    return $res.Output.Trim()
}

$characteristics = Get-DeviceProp -Name 'ro.build.characteristics'
if ($characteristics -notmatch '(^|,)\s*watch\s*($|,)') {
    Stop-Run 2 "device $id is not a watch: ro.build.characteristics reads '$characteristics'"
}

$sdkRaw = Get-DeviceProp -Name 'ro.build.version.sdk'
$sdk = 0
if (-not [int]::TryParse($sdkRaw, [ref]$sdk)) {
    Stop-Run 2 "device $id reports an unreadable API level: ro.build.version.sdk reads '$sdkRaw'"
}
if ($sdk -lt $WEAR_MIN_SDK) {
    Stop-Run 2 "device $id is below the module minSdk: ro.build.version.sdk reads '$sdkRaw', the wear module requires $WEAR_MIN_SDK"
}

if (-not $Json) { Write-Host "wear-prerelease-prepare: device $id qualified (characteristics '$characteristics', sdk $sdk)" -ForegroundColor Green }

# --- Artifacts --------------------------------------------------------------------------------

if (-not $SkipBuild) {
    # No build lock is taken here: the builder takes it itself, and a second acquisition from the
    # same call chain would wait on a lock this chain already holds.
    #
    # -NoDistribute is what makes this branch runnable at all. The builder's default tail hands the
    # artifact out - DOWNLOADS, the build journal, the Google Drive mirror - so a run whose only job
    # is to judge a build would overwrite the Drive copy people install from and the AAB
    # publish-play-release.ps1 addresses by path, with an unstamped ad-hoc build. The phone sweep
    # sidesteps the same tail by refusing its own interactive builder outright. The artifact this
    # run judges is read from wear/build/outputs, which -NoDistribute does not touch, so ADR-3
    # still holds: the build comes from the recorded path, inside the run.
    & pwsh -NoProfile -File $wearBuilder -Artifact Both -NoDistribute -Flavor $WearFlavor
    if ($LASTEXITCODE -ne 0) { Stop-Run 1 "build-wear-release.PS1 exited $LASTEXITCODE" }
}
elseif (-not $Json) {
    Write-Host 'wear-prerelease-prepare: -SkipBuild - judging the artifacts already on disk' -ForegroundColor Yellow
}

function Get-BuiltArtifact {
    param(
        [Parameter(Mandatory)][string]$Directory,
        [Parameter(Mandatory)][string]$Extension
    )

    $dir = Join-Path $repoRoot $Directory
    if (-not (Test-Path -LiteralPath $dir)) { return $null }

    # The file comes from the shared resolver (S1972); the AAB branch still resolves by glob inside
    # it, because AGP writes no output-metadata.json beside a bundle.
    $file = Find-BuildArtifact -Dir $dir -Extension $Extension
    if (-not $file) { return $null }

    # Version is read from the element describing that file rather than from index 0. Every ABI
    # slice of one build carries the same version (S1972 §6.2), so any element would answer - but
    # tying it to the resolved file keeps the manifest self-consistent when a build emits several.
    $versionName = $null
    $versionCode = $null
    $metaPath = Join-Path $dir 'output-metadata.json'
    if (Test-Path -LiteralPath $metaPath) {
        try {
            $meta = Get-Content -LiteralPath $metaPath -Raw | ConvertFrom-Json
            $element = @($meta.elements) | Where-Object { $_.outputFile -eq $file.Name } | Select-Object -First 1
            if ($element) {
                $versionName = $element.versionName
                $versionCode = $element.versionCode
            }
        } catch { }
    }

    return [ordered]@{
        name        = $file.Name
        path        = $file.FullName
        sizeBytes   = $file.Length
        lastWrite   = $file.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')
        versionName = $versionName
        versionCode = $versionCode
    }
}

# S2090: the watch outputs sit under a flavor segment now. The sweep judges the artifact that ships,
# so it defaults to the store variant; a miss must name the variant AND the directory searched, because
# this script reports a path miss as "nothing got built" and the two read identically otherwise.
$apkDir = "wear/build/outputs/apk/$WearFlavor/release"
$aabDir = "wear/build/outputs/bundle/$WearFlavor/release"

$apk = Get-BuiltArtifact -Directory $apkDir -Extension 'apk'
$aab = Get-BuiltArtifact -Directory $aabDir -Extension 'aab'

if (-not $apk) { Stop-Run 2 "no $WearFlavor release APK under $apkDir - build without -SkipBuild" }
if (-not $aab) { Stop-Run 2 "no $WearFlavor release bundle under $aabDir - build without -SkipBuild" }

# The bundle directory carries no output-metadata.json, so the AAB has no version of its own to read.
# Both artifacts come out of one gradle invocation, so the APK's version is the bundle's version - and
# a verdict that named the file but left its version blank would not satisfy what it exists to prove.
if (-not $aab.versionName -and $apk.versionName) {
    $aab.versionName = $apk.versionName
    $aab.versionCode = $apk.versionCode
    $aab.versionSource = 'apk-of-the-same-build'
}

$outPath = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $repoRoot $OutDir }
New-Item -ItemType Directory -Path $outPath -Force | Out-Null
$result.outDir = $outPath

$manifest = [ordered]@{
    device    = $id
    skipBuild = [bool]$SkipBuild
    apk       = $apk
    aab       = $aab
}
$result.artifacts = $manifest
$manifestPath = Join-Path $outPath 'artifact.json'
[pscustomobject]$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

if (-not $Json) {
    Write-Host "wear-prerelease-prepare: judging $($apk.name) $($apk.versionName) ($($apk.versionCode)) and $($aab.name)" -ForegroundColor Cyan
}

# --- Install, clear the buffer, launch ---------------------------------------------------------

$install = Invoke-AdbVerb -Arguments @('install', '-Module', 'wear', '-Apk', $apk.path, '-DeviceId', $id)
if ($install.Exit -ne 0) { Stop-Run 1 "install failed: $($install.Output)" }

# Hard-grant the media permissions, the way the phone sweep hard-grants its own. The watch app puts
# a permission gate in front of the home list on a fresh install, so without this the run would walk
# a screen that asks for access instead of the app - and the OS dialog behind it needs taps that
# belong to the platform, not to anything this release is being judged on.
$grantTargets = if ($sdk -ge $MEDIA_PERMISSION_SDK) {
    @('android.permission.READ_MEDIA_AUDIO', 'android.permission.READ_MEDIA_VIDEO', 'android.permission.READ_MEDIA_IMAGES')
} else {
    @('android.permission.READ_EXTERNAL_STORAGE')
}
$granted = @()
foreach ($perm in $grantTargets) {
    $grant = Invoke-AdbVerb -Arguments @('shell', '-Cmd', "pm grant $RELEASE_PACKAGE $perm", '-DeviceId', $id)
    # A permission the build does not declare cannot be granted, and that is a property of the build
    # rather than a failure of the run - record which ones took and let the walk report what it sees.
    if ($grant.Exit -eq 0 -and $grant.Output -notmatch 'Exception|Operation not allowed|not a changeable') { $granted += $perm }
}
$result.grantedPermissions = $granted
if (-not $Json) { Write-Host "wear-prerelease-prepare: granted $($granted.Count)/$($grantTargets.Count) media permission(s)" -ForegroundColor Green }

# Clearing between install and launch is what makes the later log audit attributable to this run.
$clear = Invoke-AdbVerb -Arguments @('logcat-clear', '-DeviceId', $id)
if ($clear.Exit -ne 0) { Stop-Run 1 "logcat-clear failed: $($clear.Output)" }

# -Release is not optional here. Package resolution prefers the debug package when one is installed,
# and a watch that has ever run a debug build has one - so without this the run would install the
# release artifact and then start the debug app, producing a confident verdict about a build nobody
# is shipping. Observed on the first live run of this script (S1984).
$launch = Invoke-AdbVerb -Arguments @('launch', '-Module', 'wear', '-Release', '-DeviceId', $id)
if ($launch.Exit -ne 0) { Stop-Run 1 "launch failed: $($launch.Output)" }
$result.launchedPackage = 'com.sza.fastmediasorter'

$result.ok = $true
$result.exitCode = 0
if ($Json) { [pscustomobject]$result | ConvertTo-Json -Depth 6 -Compress }
else { Write-Host "wear-prerelease-prepare: PASS - $($apk.name) installed and running on $id; manifest $manifestPath" -ForegroundColor Green }
exit 0
