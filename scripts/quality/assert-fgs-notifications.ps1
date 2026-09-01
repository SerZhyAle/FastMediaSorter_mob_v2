<#
.SYNOPSIS
  Guards against the Android 16 "Bad notification for startForeground"
  (CannotPostForegroundServiceNotificationException) crash class.

.DESCRIPTION
  Two independent root causes produced this crash on Samsung Android 16 (S0405, S0416):

    A) A notification small icon (setSmallIcon) backed by a vector drawable that carries
       android:tint="?attr/...". The theme attribute cannot be resolved when the system
       inflates the status-bar icon outside the app theme, so the notification is rejected.

    B) A foreground-service start path (setForeground / startForeground / ForegroundInfo)
       that builds a notification without ensuring its NotificationChannel exists first.
       Posting a foreground notification to a missing channel is rejected on Android 16.

  This gate fails the build/post-change if either pattern reappears.

.NOTES
  Run from anywhere; paths are resolved relative to the repo root.
  Exit 0 = clean, Exit 1 = violations found.
#>
param(
    [switch]$Gate,
    # The changed set. A violation of either rule can only be INTRODUCED by a changed Kotlin file
    # (a new setSmallIcon call, a new foreground-service path) or by a changed drawable (a tint
    # added to an icon already in use), so a scoped run judges exactly those and skips the rest of
    # the tree. An unscoped run keeps the strict project-wide verdict.
    [string]$ChangedFiles
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$srcRoots = @("$root/app_v2/src", "$root/wear/src") | Where-Object { Test-Path $_ }

$violations = New-Object System.Collections.Generic.List[string]
$rel = { param($p) $p.Substring($root.Length + 1) -replace '\\', '/' }

$changedSet = @()
foreach ($entry in @($ChangedFiles)) {
    $changedSet += @($entry -split ',' | ForEach-Object { $_.Trim().Replace('\', '/') } | Where-Object { $_ })
}
# A changed drawable can only be judged against every call site, so its presence widens the run back.
$changedDrawable = @($changedSet | Where-Object { $_ -match '(^|/)res/drawable[^/]*/.+\.xml$' }).Count -gt 0
$scoped = $changedSet.Count -gt 0 -and -not $changedDrawable

if ($scoped) {
    # Resolve the named files directly. Walking both source trees to then discard 3695 of 3696
    # entries is the cost the scoping exists to avoid.
    $ktFiles = @($changedSet |
        Where-Object { $_ -like '*.kt' } |
        ForEach-Object { Join-Path $root $_ } |
        Where-Object { Test-Path -LiteralPath $_ } |
        ForEach-Object { Get-Item -LiteralPath $_ })
}
else {
    $ktFiles = @(Get-ChildItem -Path $srcRoots -Recurse -Filter *.kt -File -ErrorAction SilentlyContinue)
}

# ---- Part A: ?attr-tinted notification small icons ----
# Collect drawable names passed to setSmallIcon(R.drawable.X) (covers NotificationCompat.Builder,
# Notification.Builder and Media3 DefaultMediaNotificationProvider.setSmallIcon).
$iconRefs = @{}
# Read each file once as one string and match the whole text, rather than looping its lines.
# Same regex, same verdict; the per-line loop cost 4.4 s of the gate's 5.2 s on 3696 files
# because it ran the interpreter over ~600k lines to find the two call sites that exist.
# A line number is still reported, computed from the match offset only for a match that hit.
foreach ($f in $ktFiles) {
    $text = [System.IO.File]::ReadAllText($f.FullName)
    if (-not $text.Contains('setSmallIcon')) { continue }
    foreach ($m in [regex]::Matches($text, 'setSmallIcon\(\s*R\.drawable\.([A-Za-z0-9_]+)')) {
        $name = $m.Groups[1].Value
        $lineNumber = ([regex]::Matches($text.Substring(0, $m.Index), "`n")).Count + 1
        if (-not $iconRefs.ContainsKey($name)) { $iconRefs[$name] = New-Object System.Collections.Generic.List[string] }
        $iconRefs[$name].Add(("{0}:{1}" -f (& $rel $f.FullName), $lineNumber))
    }
}
foreach ($name in $iconRefs.Keys) {
    $drawables = Get-ChildItem -Path $srcRoots -Recurse -File -Filter "$name.xml" -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]drawable[^\\/]*[\\/]' }
    foreach ($d in $drawables) {
        $content = [System.IO.File]::ReadAllText($d.FullName)
        $noComments = [regex]::Replace($content, '(?s)<!--.*?-->', '')   # ignore ?attr mentions in comments
        if ($noComments -match 'android:tint\s*=\s*"\?attr') {
            $used = ($iconRefs[$name] | Select-Object -First 3) -join ', '
            $violations.Add("A) notification small icon '$name' has android:tint=`"?attr`" -> $(& $rel $d.FullName) (used at $used)")
        }
    }
}

# ---- Part B: FGS notification builders without channel creation ----
foreach ($f in $ktFiles) {
    $text = [System.IO.File]::ReadAllText($f.FullName)
    # Cheap literal pre-filter before the regexes: only a file naming one of these can violate B.
    if (-not ($text.Contains('setForeground(') -or $text.Contains('startForeground(') -or $text.Contains('ForegroundInfo('))) { continue }
    $startsFgs = $true
    $buildsNotif = $text -match 'Notification(Compat)?\.Builder\('
    if ($startsFgs -and $buildsNotif) {
        $ensuresChannel = ($text -match 'createNotificationChannel') -or ($text -match 'ensureChannel') -or ($text -match 'ensureNotificationChannel')
        if (-not $ensuresChannel) {
            $violations.Add("B) $(& $rel $f.FullName) starts a foreground service and builds a notification but never creates/ensures its channel")
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Host "assert-fgs-notifications: FAIL ($($violations.Count) issue(s))" -ForegroundColor Red
    $violations | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
    Write-Host "Fix A: use the shared branded icon - NotificationIcons.STATUS_BAR (ic_notification_app_logo), a plain white vector with no ?attr tint." -ForegroundColor Cyan
    Write-Host "Fix B: ensure the NotificationChannel exists (ensureChannel/createNotificationChannel) before setForeground/startForeground." -ForegroundColor Cyan
    exit 1
}
$judged = if ($scoped) { "$($ktFiles.Count) changed Kotlin file(s)" } else { "$($ktFiles.Count) Kotlin file(s), whole tree" }
Write-Host "assert-fgs-notifications: PASS - $judged (no ?attr notification icons; every FGS path creates its channel)." -ForegroundColor Green
exit 0
