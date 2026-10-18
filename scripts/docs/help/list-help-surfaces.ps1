<#
.SYNOPSIS
    Enumerates the phone app's user-facing surfaces for the in-app help map (CONTEXT-HELP rule 19).

.DESCRIPTION
    The source of truth is the code, never memory:
    - every <activity> of app_v2/src/main/AndroidManifest.xml (aliases are not activities), minus the
      explicit exclusion list below;
    - every Kotlin class under app_v2/src/main/java that extends DialogFragment, BottomSheetDialogFragment,
      AppCompatDialogFragment or the project's BaseAppBottomSheet.
    Surface id = the class simple name. Flavor-only manifests are not read yet: a flavor surface joins the
    map when its slice is written.

    The gate scripts/quality/assert-help-map.ps1 dot-sources this file and calls Get-HelpSurfaces, so the
    enumeration lives in that one function.

    Get-WearHelpSurfaces (S4291) enumerates the watch app the same way from its route table: every
    composable(..) destination under wear/src/main/java. The watch has one activity, so a route is the only
    declaration of a screen; the surface id is the route reference as written (WearRoutes.HOME).

.PARAMETER Json
    Print a JSON array of { id, kind, source } instead of one id per line.

.PARAMETER RepoRoot
    Repository root; defaults to three levels above this script's folder.

.PARAMETER Module
    Which app to list when run standalone: phone (default) or wear.

.NOTES
    Exit codes:
      0 - surfaces listed
      2 - the manifest or the source root was not found
#>
[CmdletBinding()]
param(
    [switch]$Json,
    [string]$RepoRoot = '',
    [ValidateSet('phone', 'wear')]
    [string]$Module = 'phone'
)

# Not surfaces: third-party sign-in receivers, and trampolines that finish before drawing anything.
$script:HelpSurfaceExclusions = @(
    'RedirectUriReceiverActivity',          # AppAuth redirect receiver
    'AuthActivity',                         # Dropbox SDK sign-in
    'BrowserTabActivity',                   # MSAL sign-in
    'GoogleDriveAuthCompletionActivity',    # finishes the OAuth round trip, no UI
    'GoogleDriveAuthResolutionActivity',    # system consent resolution, no own UI
    'FlashlightToggleActivity',             # toggles the torch and finishes
    'WatchListenLaunchActivity',            # launcher trampoline
    'CameraLaunchActivity',                 # widget trampoline
    'PhotoCaptureLaunchActivity',           # widget trampoline
    'ScreenRecordingLaunchActivity',        # widget trampoline
    'LinkDownloadLaunchActivity',           # widget trampoline
    'StreamPlayLaunchActivity',             # widget trampoline
    'StandalonePlayerDispatcherActivity',   # routes "Open with" to a standalone player
    'PrintDispatchActivity'                 # routes a print request, no own UI
)

function Get-HelpSurfaces {
    param([Parameter(Mandatory)][string]$Root)

    $manifest = Join-Path $Root 'app_v2/src/main/AndroidManifest.xml'
    $javaRoot = Join-Path $Root 'app_v2/src/main/java'
    if (-not (Test-Path -LiteralPath $manifest) -or -not (Test-Path -LiteralPath $javaRoot)) {
        throw "help surfaces: source not found under $Root"
    }

    $surfaces = [System.Collections.Generic.List[object]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new()

    $manifestText = [IO.File]::ReadAllText($manifest)
    foreach ($m in [regex]::Matches($manifestText, '<activity\s+android:name="([^"]+)"')) {
        $id = ($m.Groups[1].Value -split '\.')[-1]
        if ($script:HelpSurfaceExclusions -contains $id) { continue }
        if ($seen.Add($id)) {
            $surfaces.Add([pscustomobject]@{ id = $id; kind = 'activity'; source = 'AndroidManifest.xml' })
        }
    }

    $dialogPattern = '(?m)^\s*(?:(?:open|abstract|internal|private)\s+)*class\s+(\w+)[^{\n]*?:\s*(?:DialogFragment|BottomSheetDialogFragment|AppCompatDialogFragment|BaseAppBottomSheet)\b'
    Get-ChildItem -LiteralPath $javaRoot -Recurse -Filter '*.kt' -File | ForEach-Object {
        $text = [IO.File]::ReadAllText($_.FullName)
        foreach ($m in [regex]::Matches($text, $dialogPattern)) {
            $id = $m.Groups[1].Value
            if ($text -match "abstract\s+class\s+$id\b") { continue }
            if ($seen.Add($id)) {
                $rel = [IO.Path]::GetRelativePath($Root, $_.FullName).Replace('\', '/')
                $surfaces.Add([pscustomobject]@{ id = $id; kind = 'dialog'; source = $rel })
            }
        }
    }

    return $surfaces | Sort-Object id
}

function Get-WearHelpSurfaces {
    param([Parameter(Mandatory)][string]$Root)

    $javaRoot = Join-Path $Root 'wear/src/main/java'
    if (-not (Test-Path -LiteralPath $javaRoot -PathType Container)) {
        throw "wear help surfaces: source not found under $Root"
    }

    # A destination is `composable(` then an optional `route =` then a route reference or a literal; the
    # call is often split over lines, so the gap is whitespace of any kind.
    $routePattern = '\bcomposable\(\s*(?:route\s*=\s*)?(?:(\w+Routes\.\w+)|"([^"]+)")'
    $surfaces = [System.Collections.Generic.List[object]]::new()
    $seen = [System.Collections.Generic.HashSet[string]]::new()

    Get-ChildItem -LiteralPath $javaRoot -Recurse -Filter '*.kt' -File | ForEach-Object {
        $text = [IO.File]::ReadAllText($_.FullName)
        foreach ($m in [regex]::Matches($text, $routePattern)) {
            $id = if ($m.Groups[1].Success) { $m.Groups[1].Value } else { $m.Groups[2].Value }
            if ($seen.Add($id)) {
                $rel = [IO.Path]::GetRelativePath($Root, $_.FullName).Replace('\', '/')
                $surfaces.Add([pscustomobject]@{ id = $id; kind = 'route'; source = $rel })
            }
        }
    }

    return $surfaces | Sort-Object id
}

if ($MyInvocation.InvocationName -ne '.') {
    if (-not $RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path }
    try {
        $list = if ($Module -eq 'wear') { Get-WearHelpSurfaces -Root $RepoRoot } else { Get-HelpSurfaces -Root $RepoRoot }
    } catch {
        Write-Error $_.Exception.Message
        exit 2
    }
    if ($Json) { $list | ConvertTo-Json -Depth 3 } else { $list | ForEach-Object { $_.id } }
    exit 0
}
