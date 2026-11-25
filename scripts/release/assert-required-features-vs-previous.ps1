#requires -Version 7.0
<#
.SYNOPSIS
    Refuse a release bundle that requires a device feature the previous release did not require
    (S4668, contract GOOGLE-PLAY rule 9 and section 4 rung 2).

.DESCRIPTION
    Play filters devices by the required features of the bundle, implied ones included: in S0918 an
    activity locked to screenOrientation="portrait" implied a required android.hardware.screen.portrait
    and removed every landscape-only device (20 468 -> 17 510 supported devices between two bundles).
    The manifest source does not show this class, and `bundletool dump manifest` lists explicit
    features only, so the required set is read the way Play reads it: a universal APK built from the
    bundle (`bundletool build-apks --mode=universal`), then `aapt2 dump badging`.

    Direction: a feature that becomes REQUIRED narrows reach and fails this check, naming the
    uses-implied-feature trigger when badging gives one. A feature that is no longer required widens
    reach and is printed as information. The contract states the same direction since its
    2026-11-15 correction (rule 9, section 4 rung 2).

    Badging lines read:
      uses-feature: name='x'               required (also the older uses-feature:'x' form), unless
                                           the line carries required='false'
      uses-feature-not-required: name='x'  not required
      uses-implied-feature: name='x' reason='r'  (or uses-implied-feature:'x','r') - the trigger
    A dump with no `package:` line is not badging output and is refused (exit 2).

    The previous release is the newest retained release folder under the deobfuscation archive root
    - the root retain-deobfuscation.ps1 writes <versionCode>/ folders to and
    assert-fgs-types-vs-previous.ps1 reads - whose versionCode is below the judged bundle's. Only that
    folder is taken: an older bundle would be a baseline two releases back. retain-deobfuscation.ps1
    stores the mapping payload, manifest.json and the fgs-types snapshot there, not the .aab, so
    until a release folder also holds its bundle the default resolution exits 2 and the previous
    bundle is passed with -Previous (or its dump with -PreviousBadgingFile).

    The script never runs gradle and takes no lock. bundletool and aapt2 run only for an input given
    as a bundle; their scratch output goes under -WorkDir, never the repository root.

.PARAMETER Bundle
    The release bundle to judge. Default: the newest .aab under
    app_v2/build/outputs/bundle/standardRelease/ of this tree. Ignored with -BadgingFile.

.PARAMETER Previous
    The previous release's bundle. Default: the .aab in the newest retained release folder below
    the judged versionCode (see above). Ignored with -PreviousBadgingFile.

.PARAMETER ArchiveRoot
    The deobfuscation archive root holding <versionCode>/ folders. Default: the resolved
    Deobfuscation artifact sink, as retain-deobfuscation.ps1 resolves it.

.PARAMETER Bundletool
    bundletool: a .jar (run with java -jar) or an executable wrapper. Default: `bundletool` on PATH,
    else a bundletool*.jar in a PATH directory. None is installed on the workstation today.

.PARAMETER Aapt2
    aapt2. Default: the newest build-tools/<version>/aapt2(.exe) under the Android SDK found from
    ANDROID_HOME, ANDROID_SDK_ROOT or %LOCALAPPDATA%\Android\Sdk.

.PARAMETER BadgingFile
    Already-dumped `aapt2 dump badging` text of the judged bundle's universal APK, in place of
    -Bundle. Lets the suite run with neither tool.

.PARAMETER PreviousBadgingFile
    Already-dumped badging text of the previous release, in place of -Previous.

.PARAMETER WorkDir
    Scratch directory for the .apks, the universal APK and the dumps. Default: temp/S4668/required-features.

.PARAMETER Help
    Print this help and exit 0.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-required-features-vs-previous.ps1 -Bundletool C:\tools\bundletool-all.jar -Previous D:\releases\261000200\app_v2-standard-release.aab

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-required-features-vs-previous.ps1 -BadgingFile temp/S4668/new.txt -PreviousBadgingFile temp/S4668/old.txt

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - pass: the new bundle requires no feature the previous release did not; both sets printed.
      1 - fail: the new bundle requires a feature the previous release did not - it removes devices.
          Declare the feature with android:required="false" when the app does not need it, or remove
          its trigger, before the upload.
      2 - could not verify: a tool, the bundle, the previous release or a dump is missing, a tool
          failed, or its output is not badging text.
    There is no exit 4 here: the script runs no gradle and so never waits for a build lock.
#>
[CmdletBinding()]
param(
    [string] $Bundle,
    [string] $Previous,
    [string] $ArchiveRoot,
    [string] $Bundletool,
    [string] $Aapt2,
    [string] $BadgingFile,
    [string] $PreviousBadgingFile,
    [string] $WorkDir,
    [switch] $Help
)

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $WorkDir) { $WorkDir = Join-Path $projectRoot 'temp/S4668/required-features' }

# --- Badging -----------------------------------------------------------------------------------
function Read-Badging {
    <# Returns Problem (non-empty = unparsable), VersionCode, Package, Required (sorted), NotRequired, Implied (name -> reason). #>
    param([string] $Text, [string] $Origin)
    $result = [pscustomobject]@{
        Problem     = ''
        VersionCode = $null
        Package     = ''
        Required    = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
        NotRequired = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
        Implied     = @{}
    }
    $sawPackage = $false
    foreach ($raw in ($Text -split "`r?`n")) {
        $line = $raw.Trim()
        $m = [regex]::Match($line, "^package:\s*name='([^']*)'(.*)$")
        if ($m.Success) {
            $sawPackage = $true
            $result.Package = $m.Groups[1].Value
            $code = [regex]::Match($m.Groups[2].Value, "versionCode='(\d+)'")
            if ($code.Success) { $result.VersionCode = [long]$code.Groups[1].Value }
            continue
        }
        $m = [regex]::Match($line, "^uses-feature-not-required:\s*(?:name=)?'([^']+)'")
        if ($m.Success) { [void]$result.NotRequired.Add($m.Groups[1].Value); continue }
        $m = [regex]::Match($line, "^uses-implied-feature:\s*(?:name=)?'([^']+)'\s*,?\s*(?:reason=)?'([^']*)'")
        if ($m.Success) { $result.Implied[$m.Groups[1].Value] = $m.Groups[2].Value; continue }
        $m = [regex]::Match($line, "^uses-feature:\s*(?:name=)?'([^']+)'(.*)$")
        if ($m.Success) {
            if ($m.Groups[2].Value -match "required='false'") { [void]$result.NotRequired.Add($m.Groups[1].Value) }
            else { [void]$result.Required.Add($m.Groups[1].Value) }
        }
    }
    if (-not $sawPackage) { $result.Problem = "no package: line, so this is not aapt2 dump badging output: $Origin" }
    return $result
}

# --- Tools, resolved only when a bundle has to be dumped ----------------------------------------
$script:tools = $null
function Resolve-Tools {
    <# Returns Problem, Aapt2, BundletoolCommand (the argument prefix that runs bundletool). #>
    if ($script:tools) { return $script:tools }
    $t = [pscustomobject]@{ Problem = ''; Aapt2 = ''; BundletoolCommand = @() }

    $aapt = $Aapt2
    if (-not $aapt) {
        $exe = if ($IsWindows) { 'aapt2.exe' } else { 'aapt2' }
        $sdkRoots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android/Sdk' })) | Where-Object { $_ }
        foreach ($sdk in $sdkRoots) {
            $toolsDir = Join-Path $sdk 'build-tools'
            if (-not (Test-Path -LiteralPath $toolsDir -PathType Container)) { continue }
            $newest = Get-ChildItem -LiteralPath $toolsDir -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '^\d+(\.\d+)*' -and (Test-Path -LiteralPath (Join-Path $_.FullName $exe) -PathType Leaf) } |
                Sort-Object { [version](([regex]::Match($_.Name, '^\d+(\.\d+){0,3}').Value) + $(if ($_.Name -notmatch '\.') { '.0' } else { '' })) } -Descending |
                Select-Object -First 1
            if ($newest) { $aapt = Join-Path $newest.FullName $exe; break }
        }
    }
    if (-not $aapt -or -not (Test-Path -LiteralPath $aapt -PathType Leaf)) {
        $t.Problem = if ($aapt) { "aapt2 not found: $aapt" } else { 'aapt2 not found under build-tools of ANDROID_HOME, ANDROID_SDK_ROOT or %LOCALAPPDATA%\Android\Sdk; pass -Aapt2' }
        $script:tools = $t; return $t
    }
    $t.Aapt2 = $aapt

    $bt = $Bundletool
    if (-not $bt) {
        $onPath = Get-Command bundletool -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($onPath) { $bt = $onPath.Source }
        else {
            foreach ($dir in (($env:PATH -split [System.IO.Path]::PathSeparator) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) })) {
                $jar = Get-ChildItem -LiteralPath $dir -File -Filter 'bundletool*.jar' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
                if ($jar) { $bt = $jar.FullName; break }
            }
        }
    }
    if (-not $bt -or -not (Test-Path -LiteralPath $bt -PathType Leaf)) {
        $t.Problem = if ($bt) { "bundletool not found: $bt" } else { 'bundletool not found: pass -Bundletool <bundletool-all.jar> (none is on PATH)' }
        $script:tools = $t; return $t
    }
    if ($bt -like '*.jar') {
        $java = Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        $javaPath = if ($java) { $java.Source } elseif ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME ($(if ($IsWindows) { 'bin/java.exe' } else { 'bin/java' })) } else { '' }
        if (-not $javaPath -or -not (Test-Path -LiteralPath $javaPath -PathType Leaf)) {
            $t.Problem = "java not found (on PATH or under JAVA_HOME) to run $bt"
            $script:tools = $t; return $t
        }
        $t.BundletoolCommand = @($javaPath, '-jar', $bt)
    }
    else { $t.BundletoolCommand = @($bt) }
    $script:tools = $t
    return $t
}

function Get-BundleBadging {
    <# Builds the universal APK of one bundle and dumps its badging. Returns Text and Problem. #>
    param([string] $BundlePath, [string] $Label)
    $out = [pscustomobject]@{ Text = ''; Problem = '' }
    if (-not (Test-Path -LiteralPath $BundlePath -PathType Leaf)) { $out.Problem = "$Label bundle not found: $BundlePath"; return $out }
    $t = Resolve-Tools
    if ($t.Problem) { $out.Problem = $t.Problem; return $out }

    $dir = Join-Path $WorkDir $Label
    Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    $apks = Join-Path $dir 'universal.apks'
    $apk = Join-Path $dir 'universal.apk'

    $exe = $t.BundletoolCommand[0]
    $prefix = @($t.BundletoolCommand | Select-Object -Skip 1)
    $btLog = & $exe @prefix 'build-apks' "--bundle=$BundlePath" "--output=$apks" '--mode=universal' 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $apks -PathType Leaf)) {
        $out.Problem = "bundletool build-apks failed for $Label bundle (exit $LASTEXITCODE): $($btLog.Trim())"
        return $out
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apks)
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq 'universal.apk' } | Select-Object -First 1
        if (-not $entry) { $out.Problem = "no universal.apk inside $apks"; return $out }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $apk, $true)
    }
    finally { $zip.Dispose() }

    $text = & $t.Aapt2 'dump' 'badging' $apk 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { $out.Problem = "aapt2 dump badging failed for $Label (exit $LASTEXITCODE): $($text.Trim())"; return $out }
    # The dump is the evidence the release keeps; the APKs it came from are rebuilt on demand.
    [System.IO.File]::WriteAllText((Join-Path $dir 'badging.txt'), $text)
    Remove-Item -LiteralPath $apks, $apk -Force -ErrorAction SilentlyContinue
    $out.Text = $text
    return $out
}

function Get-BadgingInput {
    param([string] $DumpFile, [string] $BundlePath, [string] $Label)
    if ($DumpFile) {
        if (-not (Test-Path -LiteralPath $DumpFile -PathType Leaf)) { return [pscustomobject]@{ Text = ''; Problem = "$Label badging file not found: $DumpFile"; Origin = $DumpFile } }
        return [pscustomobject]@{ Text = [System.IO.File]::ReadAllText($DumpFile); Problem = ''; Origin = $DumpFile }
    }
    $dumped = Get-BundleBadging -BundlePath $BundlePath -Label $Label
    return [pscustomobject]@{ Text = $dumped.Text; Problem = $dumped.Problem; Origin = $BundlePath }
}

# --- The judged bundle ---------------------------------------------------------------------------
if (-not $BadgingFile -and -not $Bundle) {
    $bundleRoot = Join-Path $projectRoot 'app_v2/build/outputs/bundle/standardRelease'
    if (Test-Path -LiteralPath $bundleRoot -PathType Container) {
        $found = Get-ChildItem -LiteralPath $bundleRoot -File -Filter '*.aab' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($found) { $Bundle = $found.FullName }
    }
    if (-not $Bundle) {
        Write-Host "assert-required-features-vs-previous: COULD NOT VERIFY (no release bundle under $bundleRoot; pass -Bundle or -BadgingFile)" -ForegroundColor Red
        exit 2
    }
}
$newInput = Get-BadgingInput -DumpFile $BadgingFile -BundlePath $Bundle -Label 'new'
if ($newInput.Problem) {
    Write-Host "assert-required-features-vs-previous: COULD NOT VERIFY ($($newInput.Problem))" -ForegroundColor Red
    exit 2
}
$new = Read-Badging -Text $newInput.Text -Origin $newInput.Origin
if ($new.Problem) {
    Write-Host "assert-required-features-vs-previous: COULD NOT VERIFY ($($new.Problem))" -ForegroundColor Red
    exit 2
}

# --- The previous release --------------------------------------------------------------------
if (-not $PreviousBadgingFile -and -not $Previous) {
    if (-not $ArchiveRoot) {
        . (Join-Path $PSScriptRoot '../utils/project-paths.ps1')
        $ArchiveRoot = Get-ArtifactSink -Kind Deobfuscation -Quiet
    }
    if (-not $ArchiveRoot -or -not (Test-Path -LiteralPath $ArchiveRoot -PathType Container)) {
        Write-Host "assert-required-features-vs-previous: COULD NOT VERIFY (deobfuscation archive root not reachable: '$ArchiveRoot'; set FMS_SINK_DEOBFUSCATION, or pass -ArchiveRoot, -Previous or -PreviousBadgingFile)" -ForegroundColor Red
        exit 2
    }
    # A folder at or above the judged versionCode is this build (retained before the Play commit) or
    # a later one, never the previous release - the same rule assert-fgs-types-vs-previous.ps1 keeps.
    $judgedCode = if ($null -ne $new.VersionCode) { $new.VersionCode } else { [long]::MaxValue }
    $newest = Get-ChildItem -LiteralPath $ArchiveRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^\d+$' -and [long]$_.Name -lt $judgedCode } |
        Sort-Object { [long]$_.Name } -Descending | Select-Object -First 1
    if (-not $newest) {
        Write-Host "assert-required-features-vs-previous: COULD NOT VERIFY (no retained release folder below versionCode $judgedCode under $ArchiveRoot; pass -Previous or -PreviousBadgingFile)" -ForegroundColor Red
        exit 2
    }
    $aabs = @(Get-ChildItem -LiteralPath $newest.FullName -File -Filter '*.aab' -ErrorAction SilentlyContinue)
    if ($aabs.Count -gt 1) { $aabs = @($aabs | Where-Object { $_.Name -like '*standard*' }) }
    if ($aabs.Count -ne 1) {
        Write-Host "assert-required-features-vs-previous: COULD NOT VERIFY (retained release folder $($newest.FullName) holds $($aabs.Count) usable bundle(s), need one - retain-deobfuscation.ps1 keeps the mapping payload, manifest.json and fgs-types there, not the .aab; pass -Previous or -PreviousBadgingFile)" -ForegroundColor Red
        exit 2
    }
    $Previous = $aabs[0].FullName
}
$prevInput = Get-BadgingInput -DumpFile $PreviousBadgingFile -BundlePath $Previous -Label 'previous'
if ($prevInput.Problem) {
    Write-Host "assert-required-features-vs-previous: COULD NOT VERIFY ($($prevInput.Problem))" -ForegroundColor Red
    exit 2
}
$prev = Read-Badging -Text $prevInput.Text -Origin $prevInput.Origin
if ($prev.Problem) {
    Write-Host "assert-required-features-vs-previous: COULD NOT VERIFY ($($prev.Problem))" -ForegroundColor Red
    exit 2
}

# --- Verdict ------------------------------------------------------------------------------------
$added = @($new.Required | Where-Object { -not $prev.Required.Contains($_) })
$dropped = @($prev.Required | Where-Object { -not $new.Required.Contains($_) })
$codeText = { param($b) if ($null -ne $b.VersionCode) { "versionCode $($b.VersionCode)" } else { 'versionCode unknown' } }
$listText = { param($set) if ($set.Count -gt 0) { @($set) -join ', ' } else { '(none)' } }

Write-Host "required-features: new      $($new.Package) $(& $codeText $new) ($($newInput.Origin))"
Write-Host "required-features: previous $($prev.Package) $(& $codeText $prev) ($($prevInput.Origin))"
Write-Host "required-features: new required set: $(& $listText $new.Required)"
Write-Host "required-features: previous required set: $(& $listText $prev.Required)"
foreach ($f in $dropped) {
    Write-Host "required-features: info '$f' is no longer required - reach widens"
}
if ($added.Count -gt 0) {
    foreach ($f in $added) {
        $trigger = if ($new.Implied.ContainsKey($f)) { "implied: $($new.Implied[$f])" } else { 'declared in the manifest (no uses-implied-feature line)' }
        Write-Host "  uses-feature '$f' is newly required ($trigger) - it removes every device without it; declare it android:required=`"false`" or remove the trigger before the upload" -ForegroundColor Red
    }
    Write-Host "assert-required-features-vs-previous: FAIL ($($added.Count) newly required feature(s): $($added -join ', '))" -ForegroundColor Red
    exit 1
}
Write-Host "assert-required-features-vs-previous: PASS ($($new.Required.Count) required feature(s), none new; $($dropped.Count) no longer required)" -ForegroundColor Green
exit 0
