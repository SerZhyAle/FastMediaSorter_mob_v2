#requires -Version 7.0
<#
.SYNOPSIS
  Measures a built VR-flavor APK against the Meta VRC packaging limits (S3364).

.DESCRIPTION
  Measures a built VR-flavor APK against the Meta VRC packaging limits recorded in
  store-prepublish-thresholds.psd1 (verified 2026-09-21): APK size vs MetaMaxApkBytes,
  expansion file size vs MetaMaxObbBytes, minimum signature scheme MetaMinSignatureScheme
  via apksigner, and presence of MetaRequiredAbi among the lib/ entries. Every executed
  measurement is printed beside its limit; limits are read from the catalog, never
  hardcoded here.

  S0555 adds the v1 companion signature and the release-manifest contract of a 2D panel
  app (owner ruling 2026-09-30), read from the built APK with aapt2 because the merged
  values are stated by no source file: minSdk/targetSdk bands, installLocation auto,
  headtracking declared but not required, com.oculus.supportedDevices, debuggable off,
  and a MAIN+LAUNCHER activity that sets excludeFromRecents and carries no
  com.oculus.intent.category.VR - that category on the launch activity is what makes
  a listing immersive rather than 2D.

  Exit codes:
    0 - every executed measurement passes, OR there is nothing to verify (no built
        VR-flavor release APK on the tree - advisory skip, stated in output).
    1 - at least one measurement exceeds or misses its limit; every failing
        measurement is named as measured value vs limit.
    2 - usage error (-ApkPath/-ObbPath passed but not found, -ApkPath is not a
        readable zip while every measurement else passed).

  -Gate is accepted for uniformity with the other scripts/quality/assert-*.ps1 gates
  and is a no-op here: batch runners invoke every gate with -Gate, and a
  parameter-binding error must not read as a FAIL of the thing being audited.
#>
[CmdletBinding()]
param(
    [string] $ApkPath,
    [string] $ObbPath,
    [switch] $Gate
)

$ErrorActionPreference = 'Stop'

function Write-FailLine([string] $m) { Write-Error $m -ErrorAction Continue }

# scripts/quality -> scripts -> repo root
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Add-Type -AssemblyName System.IO.Compression.FileSystem

# --- Resolve thresholds from the shared catalog ------------------------------
$thresholds = Import-PowerShellDataFile (Join-Path $repoRoot 'scripts/quality/store-prepublish-thresholds.psd1')
$maxApk = $thresholds['MetaMaxApkBytes']
$maxObb = $thresholds['MetaMaxObbBytes']
$requiredAbi = $thresholds['MetaRequiredAbi']
$minScheme = $thresholds['MetaMinSignatureScheme']
$companionScheme = $thresholds['MetaCompanionSignatureScheme']
$manifestKeys = @('MetaMinSdkLow', 'MetaMinSdkHigh', 'MetaTargetSdkLow', 'MetaTargetSdkHigh', 'MetaSupportedDevices')
$pairs = @(@('MetaMaxApkBytes', $maxApk), @('MetaMaxObbBytes', $maxObb), @('MetaRequiredAbi', $requiredAbi),
    @('MetaMinSignatureScheme', $minScheme), @('MetaCompanionSignatureScheme', $companionScheme))
$pairs += @($manifestKeys | ForEach-Object { , @($_, $thresholds[$_]) })
foreach ($pair in $pairs) {
    if ($null -eq $pair[1]) {
        Write-FailLine "[meta] Usage error: $($pair[0]) missing from store-prepublish-thresholds.psd1."
        exit 2
    }
}

# --- Resolve the artifact ----------------------------------------------------
if ($PSBoundParameters.ContainsKey('ApkPath')) {
    if ([string]::IsNullOrWhiteSpace($ApkPath)) {
        Write-FailLine '[meta] Usage error: -ApkPath was passed empty.'
        exit 2
    }
    if (-not (Test-Path $ApkPath)) {
        Write-FailLine "[meta] Usage error: -ApkPath not found: $ApkPath"
        exit 2
    }
    if ($PSBoundParameters.ContainsKey('ObbPath')) {
        if ([string]::IsNullOrWhiteSpace($ObbPath)) {
            Write-FailLine '[meta] Usage error: -ObbPath was passed empty.'
            exit 2
        }
        if (-not (Test-Path $ObbPath)) {
            Write-FailLine "[meta] Usage error: -ObbPath not found: $ObbPath"
            exit 2
        }
    }
    $apk = (Resolve-Path $ApkPath).Path
}
else {
    # House convention: app_v2/build/outputs/apk/<flavor>/<buildType>/<module>-<flavor>-<buildType>.apk
    $apkRoot = Join-Path $repoRoot 'app_v2/build/outputs/apk'
    $apk = Get-ChildItem -Path $apkRoot -Recurse -Filter *.apk -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]vr[\\/]' -and $_.FullName -match '[\\/]release[\\/]' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $apk) {
        Write-Host "[meta] No built VR-flavor release APK under $apkRoot. Nothing to verify - advisory skip (exit 0)."
        exit 0
    }
}

$failures = 0

# --- Measurement 1: APK size vs MetaMaxApkBytes ------------------------------
$apkBytes = (Get-Item -Path $apk).Length
Write-Host ("[meta] APK size: {0:N0} bytes vs MetaMaxApkBytes={1:N0} - {2}" -f $apkBytes, $maxApk, $(if ($apkBytes -lt $maxApk) { 'PASS' } else { 'FAIL' }))
if ($apkBytes -ge $maxApk) {
    $failures++
    Write-FailLine ("[meta] FAIL APK size {0:N0} exceeds MetaMaxApkBytes={1:N0} (1 GB)." -f $apkBytes, $maxApk)
}

# --- Measurement 2: OBB size vs MetaMaxObbBytes ------------------------------
if ($PSBoundParameters.ContainsKey('ObbPath')) {
    $obbBytes = (Get-Item -Path $ObbPath).Length
    Write-Host ("[meta] OBB size: {0:N0} bytes vs MetaMaxObbBytes={1:N0} - {2}" -f $obbBytes, $maxObb, $(if ($obbBytes -lt $maxObb) { 'PASS' } else { 'FAIL' }))
    if ($obbBytes -ge $maxObb) {
        $failures++
        Write-FailLine ("[meta] FAIL OBB size {0:N0} exceeds MetaMaxObbBytes={1:N0} (4 GB)." -f $obbBytes, $maxObb)
    }
}
else {
    Write-Host '[meta] OBB size: no -ObbPath given - measurement skipped (stated, not counted).'
}

# --- Measurement 3: signature scheme vs MetaMinSignatureScheme ----------------
function Resolve-BuildTool([string] $FileName, [string] $CommandName) {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk) {
        $lp = Join-Path $repoRoot 'local.properties'
        if (Test-Path $lp) {
            $line = Select-String -Path $lp -Pattern '^\s*sdk\.dir\s*=\s*(.+)$' | Select-Object -First 1
            if ($line) {
                # Java .properties escaping: '\\' -> '\', '\:' -> ':'
                $sdk = $line.Matches[0].Groups[1].Value.Trim() -replace '\\\\', '\' -replace '\\:', ':'
            }
        }
    }
    if ($sdk) {
        $hit = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName $FileName } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($hit) { return $hit }
    }
    $cmd = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

$apksigner = Resolve-BuildTool 'apksigner.bat' 'apksigner'
if (-not $apksigner) {
    Write-Host '[meta] Signature scheme: apksigner not found (SDK build-tools or PATH) - measurement skipped. Advisory skip of this measurement only.'
}
else {
    # apksigner judges v1 only for SDK levels below 24 and otherwise prints "v1 scheme: false" even
    # when META-INF carries a valid JAR signature, so the floor is forced under 24 to make it look.
    $verifyOutput = & $apksigner verify --verbose --min-sdk-version 23 $apk 2>&1
    # apksigner prints "Verified using v2 scheme (APK Signature Scheme v2): true" - the parenthesis
    # sits between the scheme and the colon, so a pattern with only whitespace there never matches.
    $schemePattern = "$minScheme scheme[^:]*:\s*true"
    $schemeOk = @($verifyOutput) | Where-Object { $_ -match $schemePattern }
    if ($schemeOk) {
        Write-Host "[meta] Signature scheme: $minScheme verified by apksigner vs MetaMinSignatureScheme=$minScheme - PASS"
    }
    else {
        $failures++
        $excerpt = (@($verifyOutput) | Select-Object -First 3) -join ' | '
        Write-FailLine "[meta] FAIL signature scheme $minScheme not verified (MetaMinSignatureScheme=$minScheme). apksigner said: $excerpt"
    }
    $companionOk = @($verifyOutput) | Where-Object { $_ -match "$companionScheme scheme[^:]*:\s*true" }
    if ($companionOk) {
        Write-Host "[meta] Signature scheme: $companionScheme verified by apksigner vs MetaCompanionSignatureScheme=$companionScheme - PASS"
    }
    else {
        $failures++
        Write-FailLine "[meta] FAIL signature scheme $companionScheme not verified (MetaCompanionSignatureScheme=$companionScheme) - a Quest target needs it beside $minScheme."
    }
}

# --- Measurement 4: MetaRequiredAbi present under lib/ ------------------------
$abis = $null
try {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $abis = $zip.Entries.FullName |
            Where-Object { $_ -match '^lib/([^/]+)/' } |
            ForEach-Object { $Matches[1] } |
            Sort-Object -Unique
    }
    finally {
        $zip.Dispose()
    }
}
catch {
    if ($failures -gt 0) {
        Write-FailLine "[meta] FAIL lib/<abi> listing unreadable in $apk - $($_.Exception.Message)"
        $failures++
    }
    else {
        Write-FailLine "[meta] Usage error: cannot open $apk as a zip archive: $($_.Exception.Message)"
        exit 2
    }
}

if ($null -ne $abis) {
    if ($abis.Count -eq 0) {
        $failures++
        Write-FailLine "[meta] FAIL $apk carries no lib/<abi> entries at all - MetaRequiredAbi=$requiredAbi cannot be present."
    }
    else {
        Write-Host "[meta] ABI set: $($abis -join ', ')"
        if ($abis -notcontains $requiredAbi) {
            $failures++
            Write-FailLine "[meta] FAIL $requiredAbi missing from ABI set: $($abis -join ', ') (MetaRequiredAbi=$requiredAbi)."
        }
        else {
            Write-Host "[meta] ABI: $requiredAbi present vs MetaRequiredAbi=$requiredAbi - PASS"
        }
    }
}

# --- Measurement 5: release manifest of a 2D panel app (S0555) ----------------
# aapt2 prints the binary manifest as an indented tree: "E: <element>" opens a node and
# "A: <ns>:<attr>(0x..)=<value>" belongs to the nearest element indented less than it.
function Read-ManifestTree([string[]] $Lines) {
    $root = [pscustomobject]@{ Name = '#root'; Indent = -1; Attrs = @{}; Children = [System.Collections.Generic.List[object]]::new() }
    $stack = [System.Collections.Generic.List[object]]::new()
    $stack.Add($root)
    foreach ($line in $Lines) {
        if ($line -notmatch '^(\s*)(E|A): (.*)$') { continue }
        $indent = $Matches[1].Length
        $kind = $Matches[2]
        $body = $Matches[3]
        while ($stack.Count -gt 1 -and $stack[$stack.Count - 1].Indent -ge $indent) { $stack.RemoveAt($stack.Count - 1) }
        $parent = $stack[$stack.Count - 1]
        if ($kind -eq 'E') {
            $node = [pscustomobject]@{ Name = ($body -replace '\s*\(line=\d+\)$', ''); Indent = $indent; Attrs = @{}; Children = [System.Collections.Generic.List[object]]::new() }
            $parent.Children.Add($node)
            $stack.Add($node)
        }
        elseif ($body -match '^(?:http://schemas\.android\.com/apk/res/android:)?(\w+)(?:\(0x[0-9a-fA-F]+\))?=(.*)$') {
            $value = $Matches[2] -replace '\s*\(Raw: .*\)$', ''
            $parent.Attrs[$Matches[1]] = $value.Trim('"')
        }
    }
    return $root
}

function Find-Nodes($Node, [string] $Name) {
    foreach ($child in $Node.Children) {
        if ($child.Name -eq $Name) { $child }
        Find-Nodes $child $Name
    }
}

function Test-TrueValue([string] $Value) { return $Value -in @('true', '0xffffffff', '-1') }

$aapt2 = Resolve-BuildTool 'aapt2.exe' 'aapt2'
if (-not $aapt2) {
    Write-Host '[meta] Manifest: aapt2 not found (SDK build-tools or PATH) - measurement skipped. Advisory skip of this measurement only.'
}
else {
    $tree = Read-ManifestTree @(& $aapt2 dump xmltree --file AndroidManifest.xml $apk 2>&1 | ForEach-Object { "$_" })
    $manifest = @(Find-Nodes $tree 'manifest') | Select-Object -First 1
    if (-not $manifest) {
        $failures++
        Write-FailLine "[meta] FAIL aapt2 printed no manifest element for $apk."
    }
    else {
        $manifestFail = [System.Collections.Generic.List[string]]::new()
        $sdk = @(Find-Nodes $manifest 'uses-sdk') | Select-Object -First 1
        $minSdk = [int]($sdk.Attrs['minSdkVersion'] ?? 0)
        $targetSdk = [int]($sdk.Attrs['targetSdkVersion'] ?? 0)
        $minLow = $thresholds['MetaMinSdkLow']; $minHigh = $thresholds['MetaMinSdkHigh']
        $tgtLow = $thresholds['MetaTargetSdkLow']; $tgtHigh = $thresholds['MetaTargetSdkHigh']
        Write-Host "[meta] minSdk $minSdk vs band $minLow-$minHigh; targetSdk $targetSdk vs band $tgtLow-$tgtHigh"
        if ($minSdk -lt $minLow -or $minSdk -gt $minHigh) { $manifestFail.Add("minSdk $minSdk outside $minLow-$minHigh") }
        if ($targetSdk -lt $tgtLow -or $targetSdk -gt $tgtHigh) { $manifestFail.Add("targetSdk $targetSdk outside $tgtLow-$tgtHigh") }

        $installLocation = $manifest.Attrs['installLocation']
        Write-Host "[meta] installLocation: $installLocation (auto = 0)"
        if ($installLocation -notin @('0', 'auto')) { $manifestFail.Add("installLocation '$installLocation' is not auto") }

        $headtracking = @(Find-Nodes $manifest 'uses-feature') |
            Where-Object { $_.Attrs['name'] -eq 'android.hardware.vr.headtracking' } | Select-Object -First 1
        if (-not $headtracking) {
            $manifestFail.Add('android.hardware.vr.headtracking is not declared - Meta signs v2 only with it present')
        }
        else {
            $required = $headtracking.Attrs['required']
            Write-Host "[meta] headtracking required=$required (2D panel app: must not be true)"
            if (Test-TrueValue $required) { $manifestFail.Add('headtracking required=true is the immersive value, not the 2D one') }
        }

        $application = @(Find-Nodes $manifest 'application') | Select-Object -First 1
        $devices = @(Find-Nodes $application 'meta-data') |
            Where-Object { $_.Attrs['name'] -eq 'com.oculus.supportedDevices' } | Select-Object -First 1
        $expectedDevices = $thresholds['MetaSupportedDevices']
        $actualDevices = if ($devices) { $devices.Attrs['value'] } else { '<absent>' }
        Write-Host "[meta] supportedDevices: $actualDevices vs MetaSupportedDevices=$expectedDevices"
        if ($actualDevices -ne $expectedDevices) { $manifestFail.Add("supportedDevices '$actualDevices' is not '$expectedDevices'") }

        if (Test-TrueValue $application.Attrs['debuggable']) { $manifestFail.Add('application is debuggable') }

        $activities = @(Find-Nodes $application 'activity') + @(Find-Nodes $application 'activity-alias')
        $launchers = @($activities | Where-Object {
                $filters = @(Find-Nodes $_ 'intent-filter') | Where-Object {
                    (@(Find-Nodes $_ 'action') | Where-Object { $_.Attrs['name'] -eq 'android.intent.action.MAIN' }) -and
                    (@(Find-Nodes $_ 'category') | Where-Object { $_.Attrs['name'] -eq 'android.intent.category.LAUNCHER' })
                }
                [bool]$filters
            })
        if ($launchers.Count -eq 0) { $manifestFail.Add('no activity carries MAIN + LAUNCHER') }
        foreach ($launcher in $launchers) {
            $name = $launcher.Attrs['name']
            $vrCategory = [bool](@(Find-Nodes $launcher 'category') | Where-Object { $_.Attrs['name'] -eq 'com.oculus.intent.category.VR' })
            Write-Host "[meta] launch activity: $name excludeFromRecents=$($launcher.Attrs['excludeFromRecents']) vrCategory=$vrCategory"
            if (-not (Test-TrueValue $launcher.Attrs['excludeFromRecents'])) { $manifestFail.Add("launch activity $name does not set excludeFromRecents=true") }
            if ($vrCategory) { $manifestFail.Add("launch activity $name carries com.oculus.intent.category.VR - the listing would be immersive, not 2D") }
        }

        if ($manifestFail.Count -eq 0) {
            Write-Host '[meta] Manifest: 2D panel release contract - PASS'
        }
        else {
            $failures += $manifestFail.Count
            foreach ($m in $manifestFail) { Write-FailLine "[meta] FAIL manifest: $m." }
        }
    }
}

if ($failures -gt 0) {
    Write-FailLine "[meta] $failures Meta packaging violation(s) in $apk."
    exit 1
}

Write-Host "[meta] PASS - every executed measurement is within the Meta VRC packaging limits."
exit 0
