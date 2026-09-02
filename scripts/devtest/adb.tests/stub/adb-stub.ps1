# Stub `adb` for scripts/devtest/adb.tests/Run-Tests.ps1 (S2088).
#
# Reached only through the sibling adb.cmd, which `Find-Adb` picks off PATH once the suite has
# blanked ANDROID_HOME and ANDROID_SDK_ROOT. Answers the calls adb.ps1 makes from recorded device
# output, so every verb runs to its -Json branch with no device attached.
#
# DELIBERATELY NO param() BLOCK. With one, PowerShell tries to bind adb's own `-s <id>` as a
# parameter name and the call dies on argument binding before reaching the table below; with none,
# the whole command line lands in $args verbatim.
#
# Per-case input comes from the environment, set by the suite before each child process:
#   FMS_STUB_HOME      directory holding the fixtures and the miss report (required)
#   FMS_STUB_DEVICES   comma-separated online device ids (default emulator-5554; empty = none)
#   FMS_STUB_PACKAGES  comma-separated installed package ids (default the debug id)
#   FMS_STUB_TREE      fixture file `pull` returns for /sdcard/_fms_tree.xml
#   FMS_STUB_WM_SIZE   what `wm size` reports (default 1080x2400)
#   FMS_STUB_RADIUS    rounded-corner radius reported by dumpsys (default 0 = no rounded-corner data)
#   FMS_STUB_SECURE    1 = the focused window carries FLAG_SECURE
#   FMS_STUB_WATCH     1 = ro.build.characteristics reports a watch
#
# Exit codes:
#   0  - the call matched the table
#   99 - the call matched nothing; the signature is appended to $FMS_STUB_HOME/stub-misses.txt
#        and the suite fails on it. An unknown call must never answer with silence: a stub that
#        does turns any change in adb.ps1 into a silently green test, which is the defect class
#        this whole suite exists for (S2088 ADR-3).

$ErrorActionPreference = 'Stop'

$home_ = $env:FMS_STUB_HOME
if (-not $home_) { Write-Error 'adb-stub: FMS_STUB_HOME is not set'; exit 99 }

$call = @($args)
# adb's own device selector is uniform across every call adb.ps1 makes; drop it so the table
# below matches on what the call actually asks for.
if ($call.Count -ge 2 -and $call[0] -eq '-s') { $call = $call[2..($call.Count - 1)] }
$sig = ($call -join ' ')

function Get-Fixture {
    param([string]$Name)
    Get-Content -LiteralPath (Join-Path $home_ "fixtures/$Name") -Raw -Encoding UTF8
}

$devices = if ($null -ne $env:FMS_STUB_DEVICES) { @($env:FMS_STUB_DEVICES -split ',' | Where-Object { $_ }) }
           else { @('emulator-5554') }
$packages = if ($null -ne $env:FMS_STUB_PACKAGES) { @($env:FMS_STUB_PACKAGES -split ',' | Where-Object { $_ }) }
            else { @('com.sza.fastmediasorter.debug') }
$wmSize = if ($env:FMS_STUB_WM_SIZE) { $env:FMS_STUB_WM_SIZE } else { '1080x2400' }
$radius = if ($env:FMS_STUB_RADIUS) { [int]$env:FMS_STUB_RADIUS } else { 0 }

switch -Regex ($sig) {

    # ---- device enumeration ----
    '^devices$' {
        Write-Output 'List of devices attached'
        foreach ($d in $devices) { Write-Output "$d`tdevice" }
        exit 0
    }

    # ---- getprop ----
    '^shell getprop ro\.product\.model$'          { Write-Output 'Pixel 7'; exit 0 }
    '^shell getprop ro\.build\.version\.release$' { Write-Output '14'; exit 0 }
    '^shell getprop ro\.build\.version\.sdk$'     { Write-Output '34'; exit 0 }
    '^shell getprop ro\.build\.characteristics$'  {
        Write-Output $(if ($env:FMS_STUB_WATCH -eq '1') { 'nosdcard,watch' } else { 'emulator' })
        exit 0
    }
    '^shell getprop ro\.product\.cpu\.abilist$'   { Write-Output 'x86_64,arm64-v8a'; exit 0 }
    '^shell getprop ro\.product\.cpu\.abi$'       { Write-Output 'x86_64'; exit 0 }

    # ---- window manager ----
    '^shell wm density$' { Write-Output 'Physical density: 420'; exit 0 }
    '^shell wm size$'    { Write-Output "Physical size: $wmSize"; exit 0 }

    # ---- dumpsys ----
    '^shell dumpsys activity activities$' {
        Write-Output '  topResumedActivity=ActivityRecord{1a2b3c4 u0 com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity t42}'
        exit 0
    }
    '^shell dumpsys window displays$' {
        if ($radius -gt 0) {
            $c = @(1..4 | ForEach-Object { "RoundedCorner{position=P$_, radius=$radius, center=Point($($radius), $($radius))}" }) -join ', '
            Write-Output "  mRoundedCorners=RoundedCorners{[$c]}"
        } else {
            Write-Output '  mDisplayInfo=DisplayInfo{"Built-in Screen"}'
        }
        exit 0
    }
    '^shell dumpsys window windows$' {
        Write-Output '  mCurrentFocus=Window{abc1234 u0 com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity}'
        exit 0
    }
    '^shell dumpsys window$' {
        Write-Output '  mCurrentFocus=Window{abc1234 u0 com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity}'
        Write-Output '  Window #1 Window{abc1234 u0 com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity}:'
        Write-Output $(if ($env:FMS_STUB_SECURE -eq '1') { '    fl=00002000' } else { '    fl=81810100' })
        exit 0
    }
    '^shell dumpsys SurfaceFlinger --display-id$' { Write-Output 'Display 4619827259835644672 (HWC display 0)'; exit 0 }

    # ---- package resolution ----
    '^shell pm list packages (?<p>\S+)$' {
        $wanted = $Matches['p']
        foreach ($p in $packages) { if ($p -eq $wanted) { Write-Output "package:$p" } }
        exit 0
    }
    '^shell pm clear \S+$' { Write-Output 'Success'; exit 0 }

    # ---- app lifecycle ----
    '^shell am start -n \S+$'    { Write-Output 'Starting: Intent { cmp=com.sza.fastmediasorter.debug/.ui.main.MainActivity }'; exit 0 }
    '^shell am force-stop \S+$'  { exit 0 }
    '^logcat -c$'                { exit 0 }
    '^install -r -d .+$'         { Write-Output 'Success'; exit 0 }
    '^uninstall \S+$'            { Write-Output 'Success'; exit 0 }

    # ---- input ----
    '^shell input (tap|swipe|text|keyevent) .+$' { exit 0 }

    # ---- log harvesting ----
    '^logcat -d -v threadtime -t \d+$' { Write-Output (Get-Fixture 'logcat_threadtime.txt').TrimEnd(); exit 0 }
    '^shell pidof \S+$'                { Write-Output '4711'; exit 0 }
    '^shell ps -A -o PID,NAME$'        { Write-Output (Get-Fixture 'ps_a.txt').TrimEnd(); exit 0 }

    # ---- ui tree / screenshot plumbing ----
    '^shell rm -f \S+$'                   { exit 0 }
    '^shell uiautomator dump \S+$'        { Write-Output 'UI hierchary dumped to: /sdcard/_fms_tree.xml'; exit 0 }
    '^shell screencap -p( -d \d+)? \S+$'  { exit 0 }
    '^shell stat -c %s /sdcard/_fms_shot\.png$' { Write-Output '48211'; exit 0 }

    # ---- run-as (prefs) ----
    '^shell run-as \S+ base64 .+settings\.preferences_pb$' { Write-Output 'c2V0dGluZ3MtcHJlZnMtZml4dHVyZQ=='; exit 0 }

    # ---- pull / push ----
    '^shell ls -1pt .+$' {
        Write-Output 'newest.png'
        Write-Output 'older.png'
        exit 0
    }
    "^shell stat -c '%F\|%s' .+$" { Write-Output 'regular file|48211'; exit 0 }
    '^pull \S+ .+$' {
        # The destination is everything after the remote path: adb.ps1 hands a single local path,
        # which may contain spaces once -OutDir points at a temp directory.
        $remote = $call[1]
        $local  = ($call[2..($call.Count - 1)] -join ' ')
        switch -Regex ($remote) {
            '_fms_tree\.xml$' {
                $tree = $env:FMS_STUB_TREE
                if (-not $tree) { Add-Content -LiteralPath (Join-Path $home_ 'stub-misses.txt') -Value "pull tree with no FMS_STUB_TREE: $sig"; exit 99 }
                Copy-Item -LiteralPath $tree -Destination $local -Force
            }
            '_fms_shot\.png$' {
                # A PNG signature and nothing else: `shot` only ever asks whether the file exists.
                [System.IO.File]::WriteAllBytes($local, [byte[]]@(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            }
            default { Set-Content -LiteralPath $local -Value 'pulled fixture' -Encoding UTF8 }
        }
        Write-Output "1 file pulled, 0 skipped."
        exit 0
    }
    '^push .+$' { Write-Output '1 file pushed, 0 skipped.'; exit 0 }

    # ---- passthrough shell (the `shell` verb) ----
    '^shell .+$' { Write-Output "stub shell: $($call[1..($call.Count - 1)] -join ' ')"; exit 0 }
}

Add-Content -LiteralPath (Join-Path $home_ 'stub-misses.txt') -Value $sig
Write-Error "adb-stub: no table entry for '$sig'"
exit 99
