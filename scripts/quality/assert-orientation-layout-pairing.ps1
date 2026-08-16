#requires -Version 7.0
<#
.SYNOPSIS
  S1549: pairs an <activity> that absorbs orientation via android:configChanges with
  ownership of a landscape layout, so the pair is caught mechanically instead of by a
  third accidental discovery.

.DESCRIPTION
  A screen that declares android:configChanges with 'orientation' is never recreated on
  rotation, so it never re-inflates its res/layout-land/*.xml - that variant only applies
  on a landscape cold start. The same defect was already fixed pointwise twice (S0692
  Streams, S1377 Welcome) without anyone inventorying the project. This gate is that
  inventory: it walks every AndroidManifest.xml under app_v2/src, extracts each <activity>
  element, and flags one whose configChanges contains the word 'orientation' while a
  landscape layout exists for it.

  Landscape ownership is resolved from the resource tree, not a name list (strategic
  sec.5.3): the activity's simple class name maps to its activity_*.xml by the project's
  naming convention, and an explicit 'layout:' line in the exception file covers an
  indirect owner whose landscape layout belongs to a hosted fragment/page rather than to
  the activity itself.

  Landscape ownership lines in the exception file map an activity to a landscape layout its
  simple name cannot reach by convention: a layout shared across several activities
  ('layout:activity_player_unified=PlayerActivity,StandalonePlayerActivity'), a layout whose
  name breaks the activity_* convention ('layout:activity_standalone_photo_video=PhotoVideoStandaloneActivity'),
  or a layout owned indirectly through a hosted fragment/page ('layout:fragment_auth_sessions_list=AuthSessionsActivity',
  'layout:page_welcome.*=WelcomeActivity'). The '.*' suffix is a prefix wildcard over layout
  base names. A layout line whose named layout has no landscape file is inert - it maps
  nothing until such a file exists.

  Two skips make the finding precise (strategic sec.6.3):
    - an activity pinned by android:screenOrientation cannot rotate, so absorption is inert;
    - an activity listed in the exception file was fixed by the keep-absorption branch and
      re-applies its landscape layout in code.

  This phase ships the gate in REPORT mode only. It is wired into a runner in phase 08,
  once the exception file holds only real exceptions.

.PARAMETER Gate
  Fail (exit 1) when at least one activity pairs absorption with a landscape layout.

.PARAMETER List
  Print every defective activity, one per line, then exit 0. This is the authoritative
  defect set every later phase verifies against.

.NOTES
  Exit codes (CLAUDE.md Rule 7):
    0  every activity clean, or a non-gate report/-List run.
    1  -Gate and at least one activity absorbs orientation while owning a landscape layout.
    2  cannot verify - no manifest found, or the source root does not exist.
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'List')][switch]$List
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$srcRoot = Join-Path $root 'app_v2/src'
$exceptionsPath = Join-Path $PSScriptRoot 'orientation-layout-pairing-exceptions.txt'

if (-not (Test-Path -LiteralPath $srcRoot)) {
    Write-Error "assert-orientation-layout-pairing: source root not found: $srcRoot" -ErrorAction Continue
    exit 2
}

# Convert a PascalCase simple class name to the snake_case layout base the project uses
# (CalculatorActivity -> activity_calculator). The trailing 'Activity' token is dropped.
function ConvertTo-LayoutBase {
    param([string]$SimpleName)
    $n = $SimpleName -replace 'Activity$', ''
    $snake = [regex]::Replace($n, '(?<!^)([A-Z])', '_$1').ToLowerInvariant()
    return "activity_$snake"
}

# --- Load the exception file: bare exempt activity names + 'layout:' owner mappings. ---
# A mapping line is 'layout:<base|base.*>=<SimpleName>[,<SimpleName>..]'. The reason suffix
# after ' #' is required for exempt lines; a mapping line carries one for the same reason.
$exemptActivities = @{}
# list of [PSCustomObject]@{ Layout; Prefix (bool); Owners (string[]) }
$layoutOwnerMappings = New-Object System.Collections.Generic.List[object]
if (Test-Path -LiteralPath $exceptionsPath) {
    foreach ($line in [System.IO.File]::ReadAllLines($exceptionsPath)) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $name = ($t -split '\s+#')[0].Trim()
        if ($name -eq '') { continue }
        if ($name -like 'layout:*') {
            $body = $name.Substring(7).Trim()
            $eq = $body.IndexOf('=')
            if ($eq -lt 1) { continue }
            $layoutKey = $body.Substring(0, $eq).Trim()
            $isPrefix = $layoutKey.EndsWith('.*')
            $layoutBase = if ($isPrefix) { $layoutKey.Substring(0, $layoutKey.Length - 2) } else { $layoutKey }
            $owners = @($body.Substring($eq + 1) -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
            if ($layoutBase -ne '' -and $owners.Count -gt 0) {
                $layoutOwnerMappings.Add([PSCustomObject]@{ Layout = $layoutBase; Prefix = $isPrefix; Owners = $owners })
            }
        } else {
            $exemptActivities[$name] = $true
        }
    }
}

# --- Index every landscape layout base name across all resource trees. -----------------
$landscapeLayouts = @{}
$layoutDirs = Get-ChildItem -Path $srcRoot -Recurse -Directory -Filter 'layout-land' -ErrorAction SilentlyContinue
foreach ($dir in $layoutDirs) {
    foreach ($file in (Get-ChildItem -Path $dir.FullName -Filter '*.xml' -File -ErrorAction SilentlyContinue)) {
        $landscapeLayouts[$file.BaseName] = $true
    }
}

# --- Walk every manifest and correlate each <activity> against the landscape set. ------
$manifests = @(Get-ChildItem -Path $srcRoot -Recurse -Filter 'AndroidManifest.xml' -File -ErrorAction SilentlyContinue)
if ($manifests.Count -eq 0) {
    Write-Error "assert-orientation-layout-pairing: no AndroidManifest.xml found under $srcRoot" -ErrorAction Continue
    exit 2
}

$fixedOrientations = @('portrait', 'reversePortrait', 'sensorPortrait', 'userPortrait',
    'landscape', 'reverseLandscape', 'sensorLandscape', 'userLandscape', 'locked')

$findingsByActivity = @{}
foreach ($m in $manifests) {
    $text = [System.IO.File]::ReadAllText($m.FullName)
    foreach ($element in [regex]::Matches($text, '(?s)<activity\b[^>]*?/?>')) {
        $el = $element.Value
        $nameMatch = [regex]::Match($el, 'android:name\s*=\s*"([^"]+)"')
        if (-not $nameMatch.Success) { continue }
        $fullName = $nameMatch.Groups[1].Value
        $simple = ($fullName -split '\.')[-1]

        $ccMatch = [regex]::Match($el, 'android:configChanges\s*=\s*"([^"]+)"')
        if (-not $ccMatch.Success) { continue }
        if ($ccMatch.Groups[1].Value -notmatch '\borientation\b') { continue }

        # Skip 1: pinned orientation cannot rotate, so absorption is inert.
        $soMatch = [regex]::Match($el, 'android:screenOrientation\s*=\s*"([^"]+)"')
        if ($soMatch.Success -and ($fixedOrientations -contains $soMatch.Groups[1].Value)) { continue }

        # Skip 2: an explicit exception with a reason (fixed by the keep-absorption branch).
        if ($exemptActivities.ContainsKey($simple)) { continue }

        # Landscape ownership: own layout by the activity_* convention, plus every layout
        # mapped to this activity by an exception-file 'layout:' line (exact or '.*' prefix).
        $owned = New-Object System.Collections.Generic.List[string]
        $ownLayout = ConvertTo-LayoutBase -SimpleName $simple
        if ($landscapeLayouts.ContainsKey($ownLayout)) { $owned.Add($ownLayout) }
        foreach ($map in $layoutOwnerMappings) {
            if ($map.Owners -notcontains $simple) { continue }
            foreach ($layoutName in $landscapeLayouts.Keys) {
                $match = if ($map.Prefix) { $layoutName.StartsWith($map.Layout) } else { $layoutName -eq $map.Layout }
                if ($match -and ($owned -notcontains $layoutName)) { $owned.Add($layoutName) }
            }
        }
        if ($owned.Count -eq 0) { continue }

        # Keyed by simple name so a layout shared across source sets reports the activity once.
        if (-not $findingsByActivity.ContainsKey($simple)) {
            $findingsByActivity[$simple] = New-Object System.Collections.Generic.List[string]
        }
        foreach ($layoutName in $owned) {
            if ($findingsByActivity[$simple] -notcontains $layoutName) { $findingsByActivity[$simple].Add($layoutName) }
        }
    }
}

# --- Report / gate verdict. -------------------------------------------------------------
$findings = New-Object System.Collections.Generic.List[string]
foreach ($simple in ($findingsByActivity.Keys | Sort-Object)) {
    $findings.Add(("{0}  [{1}]" -f $simple, (($findingsByActivity[$simple] | Sort-Object) -join ', ')))
}
foreach ($f in $findings) { Write-Host $f }

if ($Gate) {
    if ($findings.Count -gt 0) {
        Write-Host ("FAIL: {0} activit(ies) absorb orientation while owning a landscape layout" -f $findings.Count) -ForegroundColor Red
        exit 1
    }
    Write-Host "assert-orientation-layout-pairing: OK - no absorption/landscape pair" -ForegroundColor Green
    exit 0
}

Write-Host ("assert-orientation-layout-pairing: {0} activit(ies) absorb orientation while owning a landscape layout (report mode)" -f $findings.Count)
exit 0
