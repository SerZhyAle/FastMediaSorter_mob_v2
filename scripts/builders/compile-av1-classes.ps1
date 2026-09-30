<#!
.SYNOPSIS
Compiles the Media3 AV1 extension Java sources for the S1059 native AAR builder.

.DESCRIPTION
Step one of two: clones the media3 checkout at the app's pin (when absent) into the WSL home,
compiles decoder_av1's Java half against the Gradle-cached media3 artifacts and stages
classes.jar + AndroidManifest.xml for scripts/builders/build-dav1d-av1.sh.

Exit codes:
  0  staged
  1  JDK, android.jar or a media3 artifact is missing
  2  the media3 checkout or its AV1 sources are unavailable
  3  javac failed
  4  jar creation or staging failed
#>
[CmdletBinding()]
param(
    [string]$Media3Dir = '$HOME/media3-1.11.0',
    [string]$CompileSdk = 'android-37.0',
    [string]$Media3Version = '1.11.0'
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\..\utils\project-paths.ps1"
$ProjectRoot = Get-ProjectRoot
$Scratch = Join-Path $ProjectRoot 'temp\S1059'
$SrcDir = Join-Path $Scratch 'av1-src'
$ClassesDir = Join-Path $Scratch 'av1-classes'
$CpDir = Join-Path $Scratch 'av1-classpath'
$JarPath = Join-Path $Scratch 'classes.jar'
$ManifestPath = Join-Path $Scratch 'AndroidManifest.xml'

New-Item -ItemType Directory -Force -Path $Scratch | Out-Null
$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\javac.exe'))) {
    Write-Error "JAVA_HOME does not point at a JDK with javac: '$javaHome'" -ErrorAction Continue
    exit 1
}
$javac = Join-Path $javaHome 'bin\javac.exe'
$jar = Join-Path $javaHome 'bin\jar.exe'
$androidJar = Join-Path $env:LOCALAPPDATA "Android\Sdk\platforms\$CompileSdk\android.jar"
if (-not (Test-Path $androidJar)) {
    Write-Error "android.jar not found for $CompileSdk at $androidJar" -ErrorAction Continue
    exit 1
}

$cacheRoot = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
Remove-Item -Recurse -Force $CpDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $CpDir | Out-Null
$wanted = @('media3-decoder', 'media3-exoplayer', 'media3-common')
$cpEntries = @($androidJar)
foreach ($name in $wanted) {
    $artifactDir = Join-Path $cacheRoot "androidx.media3\$name\$Media3Version"
    $aar = Get-ChildItem -Path $artifactDir -Filter '*.aar' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $aar) {
        Write-Error "artifact not in Gradle cache: ${name}:${Media3Version}" -ErrorAction Continue
        exit 1
    }
    $extract = Join-Path $CpDir $name
    Expand-Archive -Path $aar.FullName -DestinationPath $extract -Force
    $classes = Join-Path $extract 'classes.jar'
    if (-not (Test-Path $classes)) {
        Write-Error "no classes.jar inside $($aar.FullName)" -ErrorAction Continue
        exit 1
    }
    $cpEntries += $classes
}
$annotationJar = Get-ChildItem -Path (Join-Path $cacheRoot 'androidx.annotation\annotation') -Filter '*.jar' -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1
if ($annotationJar) { $cpEntries += $annotationJar.FullName }
# Dav1dDecoder imports Guava's Preconditions; compile against the version media3-common declares.
$guavaJar = Get-ChildItem -Path (Join-Path $cacheRoot 'com.google.guava/guava/33.3.1-android') -Filter 'guava-*.jar' -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1
if (-not $guavaJar) {
    Write-Error 'guava 33.3.1-android not in Gradle cache' -ErrorAction Continue
    exit 1
}
$cpEntries += $guavaJar.FullName

Remove-Item -Recurse -Force $SrcDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $SrcDir | Out-Null
# The checkout must match the app's media3 pin: 1.11.0 reflects Libdav1dVideoRenderer, which an
# older branch does not contain.
wsl bash -c "[ -d $Media3Dir/.git ] || git clone -q --depth 1 --branch $Media3Version https://github.com/androidx/media.git $Media3Dir"
$tag = (wsl bash -c "git -C $Media3Dir describe --tags --exact-match" 2>$null)
if ($LASTEXITCODE -ne 0 -or $tag -ne $Media3Version) {
    Write-Error "media3 checkout $Media3Dir is at '$tag', expected $Media3Version" -ErrorAction Continue
    exit 2
}
$wslSrc = "$Media3Dir/libraries/decoder_av1/src/main/java"
$wslDest = wsl wslpath -a ($SrcDir -replace '\\', '/')
if (-not $wslDest) {
    Write-Error 'wslpath failed' -ErrorAction Continue
    exit 2
}
wsl bash -c "cp -r '$wslSrc/.' '$wslDest/'"
if ($LASTEXITCODE -ne 0) {
    Write-Error "could not copy AV1 sources out of $wslSrc" -ErrorAction Continue
    exit 2
}
$sources = Get-ChildItem -Path $SrcDir -Filter '*.java' -Recurse
if ($sources.Count -eq 0) {
    Write-Error "no AV1 Java sources landed in $SrcDir" -ErrorAction Continue
    exit 2
}
Remove-Item -Recurse -Force $ClassesDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null
$sourceList = Join-Path $Scratch 'av1-sources.txt'
$sources.FullName | Set-Content -Path $sourceList -Encoding UTF8
& $javac -nowarn -source 11 -target 11 -encoding UTF-8 -classpath ($cpEntries -join ';') -d $ClassesDir "@$sourceList" 2>&1 |
    Tee-Object -FilePath (Join-Path $Scratch 'av1-javac.log')
if ($LASTEXITCODE -ne 0) {
    Write-Error "javac failed - see $Scratch\av1-javac.log" -ErrorAction Continue
    exit 3
}
Remove-Item -Force $JarPath -ErrorAction SilentlyContinue
& $jar --create --file $JarPath -C $ClassesDir .
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $JarPath)) {
    Write-Error 'jar creation failed' -ErrorAction Continue
    exit 4
}
if (-not (& $jar --list --file $JarPath | Select-String -SimpleMatch 'androidx/media3/decoder/av1/Libdav1dVideoRenderer.class')) {
    Write-Error 'classes.jar lacks Libdav1dVideoRenderer - DefaultRenderersFactory would never load it' -ErrorAction Continue
    exit 4
}
@'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="androidx.media3.decoder.av1">
    <uses-sdk android:minSdkVersion="23" />
</manifest>
'@ | Set-Content -Path $ManifestPath -Encoding UTF8
wsl bash -c 'mkdir -p $HOME/av1-build/stage-in'
$jarWsl = wsl wslpath -a ($JarPath -replace '\\', '/')
$manifestWsl = wsl wslpath -a ($ManifestPath -replace '\\', '/')
$stageIn = '$HOME/av1-build/stage-in'
wsl bash -c ("cp '$jarWsl' " + $stageIn + "/classes.jar && cp '$manifestWsl' " + $stageIn + '/AndroidManifest.xml')
if ($LASTEXITCODE -ne 0) {
    Write-Error 'could not stage AV1 classes in WSL' -ErrorAction Continue
    exit 4
}
Write-Host "[OK] staged $($sources.Count) AV1 source file(s) at $stageIn"
