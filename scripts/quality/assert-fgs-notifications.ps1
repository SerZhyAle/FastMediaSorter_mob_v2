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
param([switch]$Gate)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$srcRoots = @("$root/app_v2/src", "$root/wear/src") | Where-Object { Test-Path $_ }

$violations = New-Object System.Collections.Generic.List[string]
$rel = { param($p) $p.Substring($root.Length + 1) -replace '\\', '/' }

$ktFiles = Get-ChildItem -Path $srcRoots -Recurse -Filter *.kt -File -ErrorAction SilentlyContinue

# ---- Part A: ?attr-tinted notification small icons ----
# Collect drawable names passed to setSmallIcon(R.drawable.X) (covers NotificationCompat.Builder,
# Notification.Builder and Media3 DefaultMediaNotificationProvider.setSmallIcon).
$iconRefs = @{}
foreach ($f in $ktFiles) {
    $i = 0
    foreach ($line in [System.IO.File]::ReadAllLines($f.FullName)) {
        $i++
        foreach ($m in [regex]::Matches($line, 'setSmallIcon\(\s*R\.drawable\.([A-Za-z0-9_]+)')) {
            $name = $m.Groups[1].Value
            if (-not $iconRefs.ContainsKey($name)) { $iconRefs[$name] = New-Object System.Collections.Generic.List[string] }
            $iconRefs[$name].Add(("{0}:{1}" -f (& $rel $f.FullName), $i))
        }
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
    $startsFgs = ($text -match 'setForeground\(') -or ($text -match 'startForeground\(') -or ($text -match 'ForegroundInfo\(')
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
    Write-Host "Fix A: give the FGS a plain white notification icon (fillColor #FFFFFFFF, no ?attr tint) - see ic_notification_audio / ic_notification_cloud_download." -ForegroundColor Cyan
    Write-Host "Fix B: ensure the NotificationChannel exists (ensureChannel/createNotificationChannel) before setForeground/startForeground." -ForegroundColor Cyan
    exit 1
}
Write-Host "assert-fgs-notifications: PASS (no ?attr notification icons; every FGS path creates its channel)." -ForegroundColor Green
exit 0
