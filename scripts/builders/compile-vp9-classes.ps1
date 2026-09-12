<#
.SYNOPSIS
    S1126 - compiles the media3 VP9 decoder module's Java half into a classes.jar
    and writes the AAR manifest beside it, ready for build-libvpx-vp9.sh to package.

.DESCRIPTION
    androidx.media3 publishes no decoder extension artifact on any Maven repository -
    the group index lists media3-decoder and nothing else - so unlike the FFmpeg DTS
    pipeline, which lifts classes.jar out of a prebuilt AAR, the VP9 Java half has to
    be compiled here. It is five files with no Kotlin.

    Compilation happens on Windows rather than in the WSL guest because the guest has
    neither a JDK nor a Linux Android SDK, and both already exist on this side.

    Output (consumed by build-libvpx-vp9.sh through VP9_STAGE_IN):
      <WSL>:~/vpx-build/stage-in/classes.jar
      <WSL>:~/vpx-build/stage-in/AndroidManifest.xml

.NOTES
    Exit codes:
      0  classes.jar and AndroidManifest.xml were written to the staging directory.
      1  the JDK, the android.jar or a required media3 artifact could not be located.
      2  the media3 VP9 sources could not be copied out of the WSL checkout.
      3  javac failed.
      4  the jar could not be written, or the staging copy back into WSL failed.
#>

[CmdletBinding()]
param(
    [string]$Media3Dir = '$HOME/ffmpeg-android-build/media',
    [string]$CompileSdk = 'android-36',
    [string]$Media3Version = '1.2.1'
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\..\utils\project-paths.ps1"
$ProjectRoot = Get-ProjectRoot

$Scratch = Join-Path $ProjectRoot 'temp\S1126'
$SrcDir = Join-Path $Scratch 'vp9-src'
$ClassesDir = Join-Path $Scratch 'vp9-classes'
$CpDir = Join-Path $Scratch 'vp9-classpath'
$JarPath = Join-Path $Scratch 'classes.jar'
$ManifestPath = Join-Path $Scratch 'AndroidManifest.xml'

New-Item -ItemType Directory -Force -Path $Scratch | Out-Null

# ---- locate the toolchain -----------------------------------------------------
$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\javac.exe'))) {
    Write-Error "JAVA_HOME does not point at a JDK with javac: '$javaHome'"
    exit 1
}
$javac = Join-Path $javaHome 'bin\javac.exe'
$jar = Join-Path $javaHome 'bin\jar.exe'

$androidJar = Join-Path $env:LOCALAPPDATA "Android\Sdk\platforms\$CompileSdk\android.jar"
if (-not (Test-Path $androidJar)) {
    Write-Error "android.jar not found for $CompileSdk at $androidJar"
    exit 1
}

# ---- gather the compile classpath --------------------------------------------
# media3-decoder / -exoplayer / -common carry the types the five VP9 sources extend;
# androidx.annotation carries @Nullable and friends. All four are already resolved by
# the app at this exact version, so the cache is guaranteed to hold them.
$cacheRoot = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
Remove-Item -Recurse -Force $CpDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $CpDir | Out-Null

$wanted = @(
    @{ Group = 'androidx.media3'; Name = 'media3-decoder';   Version = $Media3Version }
    @{ Group = 'androidx.media3'; Name = 'media3-exoplayer'; Version = $Media3Version }
    @{ Group = 'androidx.media3'; Name = 'media3-common';    Version = $Media3Version }
)

$cpEntries = @($androidJar)
foreach ($w in $wanted) {
    $dir = Join-Path $cacheRoot "$($w.Group)\$($w.Name)\$($w.Version)"
    $aar = Get-ChildItem -Path $dir -Filter '*.aar' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $aar) {
        Write-Error "artifact not in the Gradle cache: $($w.Name):$($w.Version) (looked under $dir)"
        exit 1
    }
    $extract = Join-Path $CpDir $w.Name
    New-Item -ItemType Directory -Force -Path $extract | Out-Null
    Expand-Archive -Path $aar.FullName -DestinationPath $extract -Force
    $inner = Join-Path $extract 'classes.jar'
    if (-not (Test-Path $inner)) {
        Write-Error "no classes.jar inside $($aar.FullName)"
        exit 1
    }
    $renamed = Join-Path $CpDir "$($w.Name).jar"
    Move-Item -Force $inner $renamed
    $cpEntries += $renamed
}

# androidx.annotation ships as a plain jar; take whatever version the cache holds,
# the annotations this module uses have been stable for the library's whole life.
$annotationJar = Get-ChildItem -Path (Join-Path $cacheRoot 'androidx.annotation\annotation-jvm') -Filter '*.jar' -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1
if (-not $annotationJar) {
    $annotationJar = Get-ChildItem -Path (Join-Path $cacheRoot 'androidx.annotation\annotation') -Filter '*.jar' -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1
}
if ($annotationJar) { $cpEntries += $annotationJar.FullName }

# ---- copy the sources out of WSL ---------------------------------------------
Remove-Item -Recurse -Force $SrcDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $SrcDir | Out-Null
$wslSrc = "$Media3Dir/libraries/decoder_vp9/src/main/java"
$wslDest = (wsl wslpath -a ($SrcDir -replace '\\', '/')) 2>$null
if (-not $wslDest) {
    Write-Error 'wslpath failed - is WSL running?' -ErrorAction Continue
    exit 2
}
wsl bash -c "cp -r '$wslSrc/.' '$wslDest/'"
if ($LASTEXITCODE -ne 0) {
    Write-Error "could not copy the VP9 sources out of $wslSrc" -ErrorAction Continue
    exit 2
}
$sources = Get-ChildItem -Path $SrcDir -Filter '*.java' -Recurse
if ($sources.Count -eq 0) {
    Write-Error "no .java sources landed in $SrcDir" -ErrorAction Continue
    exit 2
}
Write-Host "[INFO] $($sources.Count) source file(s) staged"

# ---- compile ------------------------------------------------------------------
Remove-Item -Recurse -Force $ClassesDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null
$cp = ($cpEntries -join ';')
$srcList = Join-Path $Scratch 'sources.txt'
$sources.FullName | Set-Content -Path $srcList -Encoding UTF8

# -source/-target 11: the module's own bytecode level is Java 8, which JDK 21 still
# accepts but warns about on every invocation; 11 is the lowest level that compiles
# clean and is still below anything the app's own toolchain rejects.
& $javac -nowarn -source 11 -target 11 -encoding UTF-8 -classpath $cp -d $ClassesDir "@$srcList" 2>&1 |
    Tee-Object -FilePath (Join-Path $Scratch 'javac.log')
if ($LASTEXITCODE -ne 0) {
    Write-Error "javac failed - see $Scratch\javac.log" -ErrorAction Continue
    exit 3
}

Remove-Item -Force $JarPath -ErrorAction SilentlyContinue
& $jar --create --file $JarPath -C $ClassesDir .
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $JarPath)) {
    Write-Error 'jar creation failed' -ErrorAction Continue
    exit 4
}

# ---- the AAR manifest ---------------------------------------------------------
# This library contributes no component, permission or resource, so the manifest is
# a package declaration and nothing else - but an AAR without one is invalid to AGP.
@'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="androidx.media3.decoder.vp9">
    <uses-sdk android:minSdkVersion="23" />
</manifest>
'@ | Set-Content -Path $ManifestPath -Encoding UTF8

# ---- hand both halves to the WSL builder --------------------------------------
wsl bash -c 'mkdir -p $HOME/vpx-build/stage-in'
$jarWsl = (wsl wslpath -a ($JarPath -replace '\\', '/'))
$manWsl = (wsl wslpath -a ($ManifestPath -replace '\\', '/'))
# The staging paths are built by concatenation, not interpolation: PowerShell owns
# $HOME too and would expand it to the Windows profile before bash ever saw it.
$stageIn = '$HOME/vpx-build/stage-in'
wsl bash -c ("cp '$jarWsl' " + $stageIn + "/classes.jar && cp '$manWsl' " + $stageIn + '/AndroidManifest.xml')
if ($LASTEXITCODE -ne 0) {
    Write-Error 'could not stage classes.jar into the WSL work directory' -ErrorAction Continue
    exit 4
}

Write-Host ''
Write-Host "[OK] classes.jar  : $JarPath ($([math]::Round((Get-Item $JarPath).Length / 1KB, 1)) KB)"
Write-Host "[OK] manifest     : $ManifestPath"
Write-Host "[OK] staged into  : ~/vpx-build/stage-in/"
Write-Host ''
Write-Host 'Next: wsl bash scripts/builders/build-libvpx-vp9.sh <project mount>'
exit 0
