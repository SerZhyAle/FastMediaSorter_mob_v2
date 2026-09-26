#requires -Version 7.0
<#
.SYNOPSIS
    S0556: the Android XR release manifest must carry Play's dedicated-track contract and nothing of Meta's.

.DESCRIPTION
    The xr flavor ships to Google Play's dedicated Android XR track under the phone's package name.
    Its own manifest, app_v2/src/xr/AndroidManifest.xml, is what makes that artifact visible to the
    right devices and start in the right space, and it sits beside two VR manifests (src/vr, src/vrOnly)
    written for Meta's store. A line copied from either of those is the likely regression, and nothing
    at build time refuses it: the merger accepts a Meta permission or launch category without comment.

    Rules, each a finding of its own:
      package-identity   a merged manifest's package is com.sza.fastmediasorter - the XR track shares the
                         phone listing, so an applicationIdSuffix would publish a different app.
      openxr-required   <uses-feature android.software.xr.api.openxr> with android:required="true" -
                         the declaration Play's XR track filters on for an OpenXR app.
      openxr-library     <uses-native-library libopenxr.google.so> inside <application> - without it the
                         OpenXR loader finds no runtime on an Android XR device.
      spatial-optional   android.software.xr.api.spatial, when declared, is required="false" - the
                         browsing screens are Home Space panels and need no spatial SDK.
      full-space-start   every activity under com.sza.fastmediasorter.ui.xr carries
                         PROPERTY_XR_ACTIVITY_START_MODE = XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED,
                         the start mode Android XR requires of an OpenXR activity.
      no-meta            no com.oculus.* / oculus.* value and no android.hardware.vr.headtracking - all are
                         Meta Horizon Store contract, never Android XR.

    -ManifestPath may point at a merged manifest (app_v2/build/intermediates/merged_manifest/xrRelease/..)
    to judge what actually ships; the default judges the source file.

.PARAMETER ManifestPath
    Manifest to judge. Default: app_v2/src/xr/AndroidManifest.xml under the repository root.

.PARAMETER ChangedFiles
    Comma-separated changed set from the closure. When given and neither the manifest nor this script
    is in it, a finding is reported with exit 3 - it stands in the tree, but this change did not make it.

.PARAMETER Gate
    Accepted for the closure's calling convention; the verdict is the same with or without it.

.PARAMETER Quiet
    Print only findings and the verdict line.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-android-xr-manifest.ps1
    pwsh -NoProfile -File scripts/quality/assert-android-xr-manifest.ps1 -ManifestPath app_v2/build/intermediates/merged_manifest/xrDebug/processXrDebugMainManifest/AndroidManifest.xml

.NOTES
    Exit codes:
      0  every rule holds
      1  a rule is broken and the manifest or this script is in the changed set (or no set was given)
      2  could not verify: the manifest is absent or is not well-formed XML
      3  a rule is broken, but -ChangedFiles names neither the manifest nor this script
#>
[CmdletBinding()]
param(
    [string] $ManifestPath,
    [string] $ChangedFiles,
    [switch] $Gate,
    [switch] $Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$defaultRelative = 'app_v2/src/xr/AndroidManifest.xml'
if (-not $ManifestPath) { $ManifestPath = Join-Path $repoRoot $defaultRelative }
elseif (-not [IO.Path]::IsPathRooted($ManifestPath)) { $ManifestPath = Join-Path $repoRoot $ManifestPath }

if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    Write-Error "assert-android-xr-manifest: manifest not found: $ManifestPath" -ErrorAction Continue
    exit 2
}

$androidNs = 'http://schemas.android.com/apk/res/android'
try {
    [xml]$doc = Get-Content -LiteralPath $ManifestPath -Raw
}
catch {
    Write-Error "assert-android-xr-manifest: not well-formed XML: $ManifestPath - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

function Get-AndroidAttr([System.Xml.XmlElement]$Node, [string]$Name) {
    return $Node.GetAttribute($Name, $androidNs)
}

. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{
        module = 'app_v2'
        flavor = 'Xr'
        files  = [IO.Path]::GetRelativePath($repoRoot, $ManifestPath).Replace('\', '/')
    })

$findings = [Collections.Generic.List[string]]::new()
$manifestNode = $doc.DocumentElement

# A source manifest carries no package attribute; a merged one does, and it must be the phone's. The
# debug and staging build types append their own suffix, which no store build carries.
$package = $manifestNode.GetAttribute('package')
if ($package -and $package -notmatch '^com\.sza\.fastmediasorter(\.debug|\.staging)?$') {
    $findings.Add("package-identity: package is '$package', not com.sza.fastmediasorter")
}
$features = @($manifestNode.SelectNodes('uses-feature'))

$openxr = @($features | Where-Object { (Get-AndroidAttr $_ 'name') -eq 'android.software.xr.api.openxr' })
if ($openxr.Count -eq 0) {
    $findings.Add('openxr-required: no <uses-feature android:name="android.software.xr.api.openxr">')
}
elseif ((Get-AndroidAttr $openxr[0] 'required') -ne 'true') {
    $findings.Add('openxr-required: android.software.xr.api.openxr is not android:required="true"')
}

$spatial = @($features | Where-Object { (Get-AndroidAttr $_ 'name') -eq 'android.software.xr.api.spatial' })
foreach ($s in $spatial) {
    if ((Get-AndroidAttr $s 'required') -ne 'false') {
        $findings.Add('spatial-optional: android.software.xr.api.spatial must be android:required="false"')
    }
}

$application = $manifestNode.SelectSingleNode('application')
$libraries = if ($application) { @($application.SelectNodes('uses-native-library')) } else { @() }
if (-not ($libraries | Where-Object { (Get-AndroidAttr $_ 'name') -eq 'libopenxr.google.so' })) {
    $findings.Add('openxr-library: no <uses-native-library android:name="libopenxr.google.so"> inside <application>')
}

$activities = if ($application) { @($application.SelectNodes('activity')) } else { @() }
foreach ($activity in $activities) {
    $name = Get-AndroidAttr $activity 'name'
    if ($name -notlike 'com.sza.fastmediasorter.ui.xr.*') { continue }
    $startMode = @($activity.SelectNodes('property') | Where-Object {
            (Get-AndroidAttr $_ 'name') -eq 'android.window.PROPERTY_XR_ACTIVITY_START_MODE'
        })
    if ($startMode.Count -eq 0 -or (Get-AndroidAttr $startMode[0] 'value') -ne 'XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED') {
        $findings.Add("full-space-start: $name lacks PROPERTY_XR_ACTIVITY_START_MODE = XR_ACTIVITY_START_MODE_FULL_SPACE_UNMANAGED")
    }
}

# Removal markers (tools:node="remove") name a permission in order to strip it, so they are not
# declarations; every other element is.
$toolsNs = 'http://schemas.android.com/tools'
foreach ($element in @($doc.SelectNodes('//*'))) {
    if ($element.GetAttribute('node', $toolsNs) -eq 'remove') { continue }
    foreach ($attr in @($element.Attributes)) {
        if ($attr.NamespaceURI -ne $androidNs) { continue }
        if ($attr.Value -match '^(com\.)?oculus\.' -or $attr.Value -eq 'android.hardware.vr.headtracking') {
            $findings.Add("no-meta: <$($element.LocalName)> declares $($attr.Value)")
        }
    }
}

$relative = [IO.Path]::GetRelativePath($repoRoot, $ManifestPath).Replace('\', '/')
if ($findings.Count -eq 0) {
    Write-Host "assert-android-xr-manifest: PASS ($relative)"
    exit 0
}

foreach ($f in $findings) { Write-Host "  $f" -ForegroundColor Red }

if ($ChangedFiles) {
    $set = @($ChangedFiles.Split(',') | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ })
    $inputs = @($relative, 'scripts/quality/assert-android-xr-manifest.ps1')
    $touched = @($set | Where-Object { $p = $_; $inputs | Where-Object { $p -like "*$_" } })
    if ($touched.Count -eq 0) {
        Write-Host "assert-android-xr-manifest: $($findings.Count) finding(s) in $relative, which this change does not touch"
        exit 3
    }
}
Write-Host "assert-android-xr-manifest: FAIL - $($findings.Count) finding(s) in $relative" -ForegroundColor Red
exit 1
