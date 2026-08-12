#requires -Version 7.0
<#
.SYNOPSIS
    S1399: every notification small icon comes from NotificationIcons, never from a drawable literal.

.DESCRIPTION
    Thirteen call sites in eleven classes each picked their own small-icon drawable, because there was
    no default to reach for. Three background workers that have nothing to do with sound settled on the
    audio glyph, and the owner watched a music note while the app moved files on a schedule. S1399 gave
    the icon one owner - core/notification/NotificationIcons.STATUS_BAR - and repointed every call site
    at it.

    Nothing stopped a fourteenth call site from hardcoding its own, which is what this gate is for. It
    fails when a small-icon setter is handed an `R.drawable.` literal, wrapped onto its own line or not.
    The same regex covers all three builders in use here, because all three spell the setter identically:

      - NotificationCompat.Builder.setSmallIcon
      - Notification.Builder.setSmallIcon
      - Media3 DefaultMediaNotificationProvider.setSmallIcon

    Deliberately narrow. A quick-settings tile icon (`Icon.createWithResource`) and a widget glyph are
    not notification small icons and are not flagged - repointing those was explicitly out of S1399's
    scope. The sibling gate assert-fgs-notifications covers the other half of the rule: an icon that
    carries a `?attr` tint and so crashes startForeground outside the app theme.

    A family that genuinely needs its own icon is not meant to route around this gate. Add it as a named
    property on NotificationIcons next to its reason, and the call site reads that name instead.

.NOTES
    Run from anywhere; paths are resolved relative to the repo root.

    Exit codes:
      0 - clean, no drawable literal reaches a small-icon setter
      1 - at least one violation
      2 - cannot verify: no source root found to scan
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path "$PSScriptRoot/../..").Path
$srcRoots = @("$root/app_v2/src", "$root/wear/src") | Where-Object { Test-Path $_ }

if ($srcRoots.Count -eq 0) {
    Write-Error "assert-notification-small-icon: no source root under '$root' - nothing to scan." -ErrorAction Continue
    exit 2
}

$owner = 'NotificationIcons.STATUS_BAR'
$violations = [System.Collections.Generic.List[string]]::new()
$scanned = 0

foreach ($file in Get-ChildItem -Path $srcRoots -Recurse -Filter *.kt -File -ErrorAction SilentlyContinue) {
    $scanned++
    # Trailing `//` and `/* */` comment bodies are the one place a drawable name may still be
    # written without being a call - the S1399 rationale comments do exactly that.
    $stripped = [System.IO.File]::ReadAllLines($file.FullName) | ForEach-Object {
        [regex]::Replace($_, '(//.*$)|(/\*.*?\*/)', '')
    }
    # Matched over the whole file rather than line by line: a formatter that wraps the argument onto its
    # own line would otherwise slip a literal past the gate, which is the exact call site it exists to
    # catch. `\s*` spans the newline; the line number comes back from the match offset.
    $code = $stripped -join "`n"
    foreach ($match in [regex]::Matches($code, 'setSmallIcon\(\s*R\.drawable\.([A-Za-z0-9_]+)')) {
        $lineNumber = $code.Substring(0, $match.Index).Split("`n").Count
        $relative = $file.FullName.Substring($root.Length + 1) -replace '\\', '/'
        $violations.Add(("{0}:{1} - setSmallIcon(R.drawable.{2})" -f $relative, $lineNumber, $match.Groups[1].Value))
    }
}

if ($violations.Count -gt 0) {
    Write-Host "assert-notification-small-icon: FAIL ($($violations.Count) call site(s) naming a drawable literal)" -ForegroundColor Red
    $violations | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
    Write-Host "Fix: pass $owner instead. If this family truly needs its own icon, add a named property to" -ForegroundColor Cyan
    Write-Host "     core/notification/NotificationIcons.kt with the reason, and read that name here (S1399)." -ForegroundColor Cyan
    exit 1
}

if (-not $Quiet) {
    Write-Host "assert-notification-small-icon: PASS (no drawable literal reaches a small-icon setter; $scanned file(s) scanned)." -ForegroundColor Green
}
exit 0
