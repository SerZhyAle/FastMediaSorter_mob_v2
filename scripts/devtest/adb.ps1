<#
.SYNOPSIS
  Generic ad-hoc adb swiss-army for fast manual work with connected emulators / devices.

.DESCRIPTION
  One entry point for the quick interactive device chores that are NOT covered by the
  purpose-bound scripts (device-ready.ps1 = pre-flight, extract-device-logs.ps1 = harvest,
  build-<flavor>-device.ps1 = build+install, maestro-run.ps1 = repeatable flows).

  Mirrors device-ready.ps1 conventions: same adb auto-discovery (adb is not on PATH on the
  dev machine), same -Json contract, same stable exit-code table. Runs natively - costs ~0
  LLM tokens; the agent reads only the compact verdict (or -Json object).

  Verbs (first positional argument):
    help                 list verbs (default when no verb given)
    devices              list online devices with model + Android version (no selection needed)
    props                selected device: model, Android release, SDK, density, wm size
    current              focused activity / package on the selected device
    launch               start the app (debug build: explicit component, bypassing the LeakCanary
                         launcher trap). Resolves the watch's own activity automatically when the
                         selected device reports watch characteristics and -Module was not given;
                         an EXPLICIT -Module that conflicts with the device is refused, mirroring
                         install's guard (S2992)
    stop                 force-stop the app
    logcat-clear         empty the device logcat buffer (alias: log-clear). Touches no app state
    wipe-data            DESTRUCTIVE: pm clear (app data, runtime grants and onboarding gone).
                         Requires -Yes
    clear                REMOVED - refuses and names its two replacements. It used to mean wipe-data
                         and was twice mistaken for "clear the log" (S1167, S1572)
    install              install -r -d an APK (-Apk <path>, or newest debug APK for -Flavor)
    uninstall            DESTRUCTIVE: uninstall the resolved package. Requires -Yes
    shot                 screenshot to temp/scratch/<device>_<TS>.png (screencap on device, then
                         pull). Warns when the focused window carries FLAG_SECURE, because such a
                         capture is black by design and reads as a rendering bug, and in that case
                         also pulls the uiautomator node tree beside it - the black frame is not
                         layout evidence, the tree is (-Json: secureWindow, treeFile, treeNodes)
    log                  the app's own log lines: every line whose pid belongs to an app
                         process, plus every line whose text names the package. -Tail N
                         (default 200), -Grep <regex>; full capture also to temp/scratch/
    tap                  input tap -X <x> -Y <y>
    swipe                input swipe from -X,-Y to -X2,-Y2 over -Duration ms (default 300).
                         A scroll is a swipe: this is how a list moves under uidump/tap-label
    uidump               dump the uiautomator node tree, save the XML, and print every node that
                         carries text or a content-description with its resource-id, its bounds and
                         its tap point. -Grep <regex> filters by label OR resource-id; -Ids also
                         lists the nodes named by an id alone (-Json: file, nodes[]). A refused
                         dump is retried and the read repeats until two consecutive trees agree,
                         so the listing describes the current screen even where the accessibility
                         tree lags it by seconds (S3238; tap-id and tap-label read the same way).
                         A screen carrying a live readout cannot be dumped at all and is refused
                         after one retry rather than three (S3289 - see exit code 7)
    tap-id               locate a node by its resource-id and tap the centre of its bounds:
                         -ResourceId <short-or-full> [-Exact] [-Index N]. PREFER THIS over tap-label:
                         a label is translated and an id is not, so a label-aimed call passes on the
                         locale it was written on and returns 8 everywhere else (S1879)
    tap-label            find a node by its text or content-description and tap the centre of its
                         bounds: -Label <substring> [-Exact] [-Index N]. Tapping a label instead of
                         a remembered coordinate is what survives a list that scrolled (S1847).
                         Right where there is no id to aim at - most of Compose on the watch
    clip-check           report content that leaves the physical display shape. The shape is READ
                         FROM THE DEVICE (mRoundedCorners), so a round watch and a rounded-corner
                         phone use one rule and neither is hardcoded. -Strict also fails on CLIPPED
    rotary               turn the watch bezel: -Axis <double> [-Repeat N]. The flow language has no
                         rotary expression, so a scenario that must reach its target by rotation
                         calls this around the flow rather than inside it (S2548). Watch only -
                         refuses on any other form factor rather than sending the event nowhere
    font-scale           read the device's system font scale, or set it with -Scale <n>. A large
                         font is a Play review criterion, so this is a measurement tool, not a
                         convenience: 1.0 restores the platform default. The value before the write
                         goes into the device state journal (S3201)
    state-begin          open an on-device run: put back whatever an earlier run left in the journal,
                         then snapshot wm density, wm size, font_scale and the app's DataStore files
                         into temp/DEVICE.STATE/<serial>.json (S3201)
    state-check          close an on-device run: compare the device with the journal, put every
                         drifted value back, print one RESTORED line per value, clear the journal.
                         -NoRestore reports the drift instead and exits 13 when there is any
    text                 input text -Text "<string>" (spaces handled)
    key                  input keyevent -Key <name-or-code> (e.g. BACK, 4, KEYCODE_HOME).
                         -LongPress sends it with FLAG_LONG_PRESS. -Repeat N sends it N times in
                         ONE `input keyevent` call, so the presses land inside one short window -
                         the water flashlight leaves only on three quick backs (S3394)
    prefs                pull settings.preferences.pb via run-as to temp/scratch/ (debuggable build only)
    pull                 fetch a file off the device: -Remote <path> [-Local <path>] [-Latest].
                         Without -Local the file lands in temp/scratch/ under its own name.
                         -Latest treats -Remote as a directory or glob and takes the newest match
    push                 send a local file OR directory to the device: -Local <path> -Remote <path>
    shell                arbitrary passthrough: -Cmd "<adb shell command>". A command that changes
                         `wm density`, `wm size` or a `settings` value records the original in the
                         device state journal first (S3201)

  Watch test parameters (S3201): `launch -GeometryMode <ORIGINAL|STORE> -ScreenDp <n>` starts the watch
  debug build with one-launch parameters that draw the other geometry or a smaller glass without
  writing a setting. The app is force-stopped first, so the parameters describe the whole launch.

  Why the device state journal exists (S3201). On 2026-09-16 one device test left `wm density 380`
  and a flipped app setting on the owner's watch; its recipe said "reset after" and nothing did,
  because putting state back was the agent's memory. Every verb here that changes device state now
  records the original before it runs, and state-begin / state-check put it back mechanically.
  Journal library: scripts/devtest/lib/device-state-journal.ps1.

  Why pull/push live here rather than in a bare `adb` call (S1578): the wrapper keeps the adb
  discovery, the device selection and the exit contract, and - the reason it was worth a ticket -
  the remote path never passes through bash, where MSYS silently rewrites `/sdcard/x` into a path
  inside the Git installation and adb then reports a missing remote object.

  Output streams (S1183). A verb whose product is DATA - devices, props, current, log, shell -
  writes that data to the SUCCESS stream, so it pipes and redirects:
      adb.ps1 shell -Cmd 'logcat -d' | Out-File temp/scratch/raw.log
  A verb whose product is an ACTION - launch, stop, tap, text, key, install, uninstall, push,
  logcat-clear, wipe-data - and every summary, warning or file-path line keeps the information
  stream, so a pipeline carries the payload alone and never the decoration. Before this split
  everything went through Write-Host: the redirect above created an EMPTY file, raised nothing,
  and printed the lines to the screen anyway - silent data loss that pushed callers back to raw
  adb.exe and cost them device selection and the exit codes. File-producing verbs (shot, prefs,
  pull) still announce their path on the information stream; take the path from -Json.

  Package resolution (verbs that act on the app): default debug id
  com.sza.fastmediasorter.debug; -Release switches to com.sza.fastmediasorter; -Package
  overrides explicitly. If the chosen id is not installed, the other variant is tried before
  giving up.

  How `log` decides a line is the app's (S1332): the pid set comes from `pidof <pkg>`, from
  `ps -A -o PID,NAME` (which also catches any :sub process), and from the `Start proc` lines
  in the capture itself (which recovers a process that already exited inside the window).
  A line is kept when its threadtime pid column is in that set, OR when its text names the
  package - the second arm is what keeps system-side lines such as `ANR in <pkg>` and
  `AndroidRuntime: Process: <pkg>`, which run under system_server's pid. An empty pid set is
  a legal state: the filter degrades to text-only matching and the verdict becomes WARN.
  When -Grep is given and the filter dropped lines the pattern matched, the verdict is WARN
  with a count. That is deliberately NOT an error and has no exit code of its own - a silent
  `OK 0 line(s)` was the whole defect, an error would break every caller. Do not add one.

  What clip-check calls a defect, and why it is not simply "the box left the circle" (S1847).
  uiautomator reports bounds ALREADY clipped to the screen, so the naive test fires on every list
  head and tail - measured on five real dumps, all five alarms were normal scrolling. Three classes:
    EDGE       the box touches a screen edge, so the viewport cut it. Nothing can be concluded
               about the element's full extent from this frame - scroll it inward and re-check
    CLIPPED    the box leaves the glass here, but the node has a scrollable ancestor and would fit
               if it were scrolled to the vertical centre, where the glass is widest. Normal
    OFF-GLASS  no scroll position can make it fit, or the node has no scrollable ancestor at all.
               This is the defect class, and the only one with an exit code
  A wide box around narrow centred glyphs therefore lands in CLIPPED, not OFF-GLASS: the tree knows
  the view box, never the glyphs, and a check that cries wolf gets ignored wholesale. For the same
  reason only LEAF nodes are judged: a container's box is the extent of the group, and the launcher's
  home-screen container - which carries a content-description and spans the whole wallpaper - was the
  first thing this verb reported as a defect on a perfectly normal phone screen.

  Exit codes (stable; mirror device-ready.ps1 where they overlap):
    0 - OK
    1 - adb not found, or bad arguments
    2 - no online device
    3 - multiple online devices and -DeviceId not supplied (for verbs needing a device)
    4 - target package not installed (for app verbs)
    5 - a destructive verb was refused: `clear` (removed), or `wipe-data`/`uninstall` without -Yes.
        Nothing was executed on the device
    6 - `pull`: the remote path does not exist on the device. Distinct from 7 because "the file was
        never written" and "the transfer failed" call for different next moves
    7 - the underlying adb command returned non-zero. For the three tree verbs it also covers the
        one refusal no wrapper can work around: `uiautomator dump` waits for 500 ms with no
        accessibility event anywhere on the device, budget 10 s, and offers no flag to shorten or
        skip that wait, so a window that emits a content-change event every ~100 ms is undumpable
        for as long as its readout is live (S3289). The refusal names it and costs one retry, not
        three - each refused attempt blocks for the full 10 s budget.
        For `clip-check` it also covers a display whose reported corner radius is impossible - wider
        than half its shorter side - on a shape that cannot be corrected from a second signal
        (S3357). Nothing is judged, because a guessed outline manufactures OFF-GLASS findings
    8 - `tap-label` / `tap-id`: no visible node carried that label or that resource-id, so NOTHING
        was tapped. Distinct from 7 because "the screen does not show it" and "the tap failed" call
        for different next moves - the first usually means an animation was still running, the
        list needs scrolling, or the target simply is not on this screen and the name was guessed
        rather than read off `uidump`
    9 - `clip-check`: at least one node is OFF-GLASS. EDGE and CLIPPED never reach this code
   10 - `clip-check -Strict`: at least one node is CLIPPED in THIS frame. Separate from 9 because
        the two answer different questions - 9 is "no scroll position saves it", 10 is "a reviewer
        photographing this frame sees a cut edge", which is the criterion Play actually applied
        when it rejected 26090503 on `Watch shapes` a second time
   11 - `font-scale`: refused to change a physical device's system setting without -Yes. WHICH
        devices may be changed at all is docs/DEVICE_FLEET.md, never this script
   12 - `rotary`: the selected device is not a watch, so it has no rotary encoder and NOTHING was
        sent. Distinct from 3 because "you did not say which device" and "you named one that cannot
        answer this verb" call for different next moves
   13 - `state-check`: the device still differs from the journal - with -NoRestore because nothing
        was put back, without it because a restore ran and the value did not come back. The journal
        is kept either way, so the next state-begin tries again

  Human output: one verdict line per verb (plus the data the verb produces).
  Machine output (with -Json): a single JSON object on stdout, all human noise suppressed.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 devices
  List online devices with model + Android version.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 shot -DeviceId emulator-5554
  Grab a screenshot from a specific device into temp/scratch/.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 log -Tail 400 -Grep "S0035|Network"
  Tail the last 400 app log lines, keep only those matching the regex.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 launch -Release
  Launch the release build (com.sza.fastmediasorter) unambiguously.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 logcat-clear
  Empty the logcat buffer. This is what "clear the log" means - it never touches app data.

.PARAMETER Module
  Which module's build a call is about: 'app_v2' (default) or 'wear'. Only `install` and `launch`
  read it - the watch publishes under the phone's application id, so every other verb already
  addresses a watch correctly once -DeviceId points at one. For `launch` it selects the component,
  because the watch declares its own activity under its own code namespace; for `install` it
  selects `wear\build\outputs\apk\<flavor>\release` instead of the phone's flavored debug directory.
  Since S2090 the watch has a flavor dimension of its own, but only `standard` and `noLegal` - a
  phone-only flavor is refused by name. `install` also refuses when -Module
  disagrees with the selected device's `ro.build.characteristics` (watch vs not) - both modules
  share one applicationId (S1681), so the wrong -Module would otherwise silently replace whichever
  app is already on that device and still report success (S2043). `launch` carries the same
  conflict guard for an EXPLICIT -Module (S2992), and - since the common case is one device and no
  -Module at all - falls back to the SELECTED device's own characteristics rather than the
  'app_v2' default, so a lone watch launches its own activity with no -Module needed.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 install -Module wear -DeviceId 192.168.1.166:46551
  pwsh -NoProfile -File scripts/devtest/adb.ps1 launch -Module wear -DeviceId 192.168.1.166:46551
  Install the watch release build onto a paired watch and start it by its own component.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 launch -DeviceId 192.168.1.166:46551
  Launch a paired watch by its own activity with no -Module needed - the device's own
  characteristics decide; only a NAMED -Module that conflicts with the device is refused (S2992).

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 wipe-data -Yes
  Reset the app to a first-run state. One-way: settings, runtime grants and onboarding are gone.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 pull -Remote "/sdcard/DCIM/Camera/*.jpg" -Latest
  Grab the newest camera frame into temp/scratch/ - the usual "shoot, then look at the file" step.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 push -Local temp/scratch/fixture.mp4 -Remote /sdcard/Movies/
  Place a fixture on the device before a scenario runs.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 uidump -Grep "Settings|Media"
  See where the matching controls actually are before tapping anything.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 tap-id -ResourceId rowLauncherModeEnabled
  Tap the settings row by the name its layout gives it. The same call works on a device in any
  language, which the equivalent tap-label does not.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 tap-label -Label "Media Types"
  Tap the entry by name, so a list that scrolled since the last dump cannot land the tap on its
  neighbour - the failure this verb exists to prevent.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/adb.ps1 clip-check -OutDir temp/S1678
  Check the current screen against the display shape and keep the tree beside the ticket evidence.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Verb = 'help',
    [string]$DeviceId,
    [string]$Package,
    [switch]$Release,
    [int]$Tail = 200,
    [string]$Grep,
    [string]$Apk,
    [ValidateSet('standard', 'lite', 'photos', 'legacy', 'noLegal')]
    [string]$Flavor = 'standard',
    # Which module's build this call is about. Both modules publish under the SAME application id
    # (S1681 - Play Services routes the Data Layer by it), so this switch never selects a package:
    # it selects the launch component and the artifact directory, which are the only two things
    # that actually differ. Read by `install` and `launch` alone; every other verb is already
    # module-agnostic because the package resolution is.
    [ValidateSet('app_v2', 'wear')]
    [string]$Module = 'app_v2',
    [int]$X,
    [int]$Y,
    # swipe: the second point, and how long the gesture takes. A fling and a drag differ only in
    # duration, and a list that is flung keeps scrolling after the gesture ends - which is why the
    # default is a deliberate 300 ms drag rather than the snappier value.
    [int]$X2,
    [int]$Y2,
    [int]$Duration = 300,
    [string]$Text,
    [string]$Key,
    # key: send the press with FLAG_LONG_PRESS (`input keyevent --longpress`).
    [switch]$LongPress,
    [string]$Cmd,
    [string]$Remote,
    [string]$Local,
    # pull: read -Remote as a directory or glob and take its newest entry.
    [switch]$Latest,
    # tap-label / uidump: the label to match, against BOTH text and content-desc. Wear controls
    # frequently carry only a content-description (the player buttons carry nothing else), so a
    # text-only search finds nothing on exactly the screens that need this verb most.
    [string]$Label,
    [switch]$Exact,
    # tap-id: the resource-id to match. Accepts the short name a layout writes (`rowExport`) or the
    # full package-qualified value; unlike a label it does not change with the app locale (S1879).
    [string]$ResourceId,
    # uidump: also list the nodes named ONLY by a resource-id. Off by default - a real screen carries
    # dozens of them, and burying the labels is how this verb stops being readable.
    [switch]$Ids,
    # tap-label / tap-id: which match to take when the target is not unique (1-based, document order).
    [int]$Index = 1,
    # Destination directory for the file-producing verbs (shot, uidump, clip-check, prefs, pull).
    # Default stays temp/scratch/; point it at temp/Sxxxx/ to file the artifact with its ticket.
    [string]$OutDir,
    [switch]$Json,
    # clip-check: judge the frame the way a store reviewer does - a node that leaves the glass HERE
    # fails, even though scrolling could recentre it.
    [switch]$Strict,
    # font-scale: the multiplier to write. Omit it to read the current one; 1.0 is the default.
    [double]$Scale,
    # rotary: how far the bezel turns per step. Negative scrolls the other way; the platform reads it
    # as the encoder's axis value, so there is no "one notch" constant to default to.
    [double]$Axis,
    # rotary: how many times to repeat the turn. A list is scrolled by repeating a small turn, not by
    # sending one large axis value - the platform flings on the latter.
    # key: how many presses to send, all in one `input keyevent` call. One adb round trip per press is
    # slow enough over wireless adb to fall out of a screen's counting window (S3394, three backs).
    [int]$Repeat = 1,
    # Confirmation for the one-way verbs (wipe-data, uninstall). This script is called by agents and by
    # other scripts, so an interactive prompt is not available - a required flag is the only gate that can
    # actually fire. It waives the confirmation only: device selection and package resolution still run.
    [switch]$Yes,
    # launch (watch debug build): one-launch test parameters, read by the app and never persisted (S3201).
    [ValidateSet('ORIGINAL', 'STORE')]
    [string]$GeometryMode,
    [int]$ScreenDp,
    # state-check: report drift without putting anything back.
    [switch]$NoRestore
)

$ErrorActionPreference = 'Stop'

# Emit UTF-8 whatever shell launched us, and do it BEFORE the first write - the stdout writer is
# built on first output and keeps the encoding it was born with, so setting this later fixes
# nothing. When stdout is redirected, pwsh inherits the OEM codepage (cp866 on a Russian Windows),
# so every non-Latin label this script prints reaches the caller as mojibake. That is not cosmetic:
# `uidump` exists to tell a caller which label to pass to `tap-label`, and an unreadable listing
# makes the verb pair unusable on a localized device. It cost S2084 a whole device session - the
# labels came back as question marks, the caller concluded `-Label` was being corrupted in transit,
# and stopped. The argument was never corrupted; only this echo was.
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

# Canonical app coordinates (see install/builder scripts and /spec-test-device).
$BASE_PACKAGE   = 'com.sza.fastmediasorter'
$DEBUG_PACKAGE  = "$BASE_PACKAGE.debug"
$MAIN_ACTIVITY  = 'com.sza.fastmediasorter.ui.main.MainActivity'
# The watch declares `.MainActivity` under its own code namespace while publishing under the phone's
# application id, so the component differs even though the package does not (S1984).
$WEAR_MAIN_ACTIVITY = 'com.sza.fastmediasorter.wear.MainActivity'

. (Join-Path $PSScriptRoot 'lib/adb-log-filter.ps1')
. (Join-Path $PSScriptRoot 'lib/ui-tree.ps1')
. (Join-Path $PSScriptRoot 'lib/device-store-paths.ps1')
. (Join-Path $PSScriptRoot 'lib/device-state-journal.ps1')

# ---------- result shape ----------

$script:result = [ordered]@{
    ok       = $false
    exitCode = 0
    verb     = $Verb
    device   = $DeviceId
    package  = $null
    data     = $null
    reason   = $null
}

function Write-Line {
    param([string]$Text, [string]$Color = 'White')
    if (-not $Json) { Write-Host $Text -ForegroundColor $Color }
}

function Emit-Ok {
    param($Data)
    $script:result.ok       = $true
    $script:result.exitCode = 0
    $script:result.data     = $Data
    if ($Json) { $script:result | ConvertTo-Json -Compress -Depth 6 }
    exit 0
}

function Fail {
    param([int]$Code, [string]$Reason)
    $script:result.exitCode = $Code
    $script:result.reason   = $Reason
    if ($Json) {
        $script:result | ConvertTo-Json -Compress -Depth 6
    } else {
        Write-Host "FAIL ($Code) - $Reason" -ForegroundColor Red
    }
    exit $Code
}

# $PSBoundParameters inside a function describes THAT function's arguments, so the script's own
# binding must be captured here, at top level, while it is still in scope.
$script:ModuleWasNamed = $PSBoundParameters.ContainsKey('Module')

# ---------- adb discovery (parity with device-ready.ps1) ----------
# S1341: Find-Adb lives in lib/find-adb.ps1 so spec-prerelease.md and other callers
# share one discovery order instead of hand-rolling their own hardcoded fallback.

. "$PSScriptRoot/lib/find-adb.ps1"
. "$PSScriptRoot/../utils/find-build-artifact.ps1"
. "$PSScriptRoot/../utils/get-device-abi.ps1"

$adb = Find-Adb
if (-not $adb) {
    Fail 1 "adb.exe not found (checked ANDROID_HOME, ANDROID_SDK_ROOT, PATH, %LOCALAPPDATA%\Android\Sdk\platform-tools)"
}

# ---------- device enumeration / selection ----------

function Get-OnlineDevices {
    $raw = & $adb devices 2>$null
    if ($LASTEXITCODE -ne 0) { Fail 1 "adb devices returned exit $LASTEXITCODE" }
    $lines = $raw -split "`r?`n" | Where-Object { $_ -and $_ -notmatch '^\s*List of devices' }
    # Split on the TAB adb actually prints between id and state, never on whitespace: an mDNS
    # service name can contain a space. When two adb servers advertise the same watch, Android
    # publishes the second as "adb-<serial>-xxxx (2)._adb-tls-connect._tcp" - splitting that on
    # the first space put "(2)._adb-tls-connect._tcp<TAB>device" in $parts[1], the state test
    # failed, and the watch vanished from every verb with no message at all. Measured 2026-09-09:
    # `. iw` built the wear APK, then refused to install because the only device it could see was
    # the phone.
    $devs = foreach ($line in $lines) {
        $parts = ($line -split "`t", 2) | Where-Object { $_ }
        if ($parts.Count -ge 2 -and $parts[1].Trim() -eq 'device') { $parts[0].Trim() }
    }
    return @($devs)
}

function Select-Device {
    # @() guards the single-device case: a one-element array returned from a function
    # unwraps to a scalar string on assignment, and $devs[0] would then index a char.
    $devs = @(Get-OnlineDevices)
    if ($devs.Count -eq 0) { Fail 2 "no online device (boot an emulator or connect a phone, then re-run)" }
    if ($DeviceId) {
        if ($devs -notcontains $DeviceId) { Fail 2 "device '$DeviceId' is not online (online: $($devs -join ', '))" }
        return $DeviceId
    }
    if ($devs.Count -gt 1) {
        # A caller that named -Module has already said which KIND of device it means, and the
        # watch is distinguishable without asking the user: ro.build.characteristics carries
        # 'watch'. So `install -Module wear` with a phone and a paired watch online resolves to
        # the watch instead of refusing (a build that succeeded then failed only at the install
        # step, measured 2026-09-03 on `. iw`). Only an EXPLICIT -Module counts - the parameter
        # defaults to app_v2, and silently picking the phone for every unqualified verb would
        # replace a visible refusal with an invisible guess.
        if ($script:ModuleWasNamed) {
            $wantWatch = ($Module -eq 'wear')
            $matched = @($devs | Where-Object { (Test-WatchDevice $_) -eq $wantWatch })
            if ($matched.Count -eq 1) {
                Write-Host "Selected $($matched[0]) - the only online device matching -Module $Module." -ForegroundColor Gray
                return $matched[0]
            }
            # Wireless ADB can list the SAME physical device twice: the literal ip:port a user
            # `adb connect`-ed to, and the mDNS-advertised "adb-<serial>-xxxx._adb-tls-connect._tcp"
            # service name Android also publishes for it. -Module disambiguation then narrows to two
            # entries instead of one and still refuses, even though there is only one watch to pick
            # (measured 2026-09-04: watch online as both 192.168.1.166:39969 and
            # adb-RFGL1148CRZ-...tcp alongside one unrelated phone). Resolve by the device's own
            # ro.serialno/ro.boot.serialno: if every matched id reports the same real serial, they are
            # one device wearing two connection names, and installing to either reaches it - prefer
            # the literal ip:port form since that is what the user actually typed to connect it.
            if ($matched.Count -gt 1) {
                $bySerial = @{}
                foreach ($id in $matched) {
                    $serial = ((Invoke-Adb $id @('shell', 'getprop', 'ro.serialno') -AllowFail) -join '').Trim()
                    if (-not $serial) {
                        $serial = ((Invoke-Adb $id @('shell', 'getprop', 'ro.boot.serialno') -AllowFail) -join '').Trim()
                    }
                    if ($serial) {
                        if (-not $bySerial.ContainsKey($serial)) { $bySerial[$serial] = @() }
                        $bySerial[$serial] += $id
                    }
                }
                if ($bySerial.Keys.Count -eq 1) {
                    $sameDevice = $bySerial.Values | Select-Object -First 1
                    $preferred = $sameDevice | Where-Object { $_ -notmatch '\._tcp$' -and $_ -notlike 'adb-*' } | Select-Object -First 1
                    $chosen = if ($preferred) { $preferred } else { $sameDevice[0] }
                    Write-Host "Selected $chosen - one physical -Module $Module device online under $($sameDevice.Count) connection names ($($sameDevice -join ', '))." -ForegroundColor Gray
                    return $chosen
                }
            }
        }
        Fail 3 "multiple online devices ($($devs -join ', ')); pass -DeviceId"
    }
    return $devs[0]
}

# Run an `adb -s <id> ...` command, returning its raw stdout. Fails (7) on non-zero exit.
function Invoke-Adb {
    param([string]$Id, [string[]]$AdbArgs, [switch]$AllowFail)
    $out = & $adb -s $Id @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0 -and -not $AllowFail) {
        Fail 7 "adb $($AdbArgs -join ' ') failed (exit $LASTEXITCODE): $($out -join ' ')"
    }
    return $out
}

# S1506: a capture taken from a FLAG_SECURE window comes back black (or zero-byte) by design.
# That artifact was twice read as a rendering failure and cost a P90 ticket, so `shot` reports the
# flag next to the file instead of leaving the reader to guess. Every probe is best-effort: a
# screenshot must never fail on account of its own diagnostics.
function Test-SecureFocusedWindow {
    param([string]$Id)
    # `dumpsys window`, not `dumpsys window windows`: on Android 15 only the former prints the
    # mCurrentFocus line, and it carries the per-window "Window #N .." blocks as well, so one call
    # answers both halves.
    $raw = (Invoke-Adb $Id @('shell', 'dumpsys', 'window') -AllowFail) -join "`n"
    $focus = [regex]::Match($raw, 'mCurrentFocus=Window\{(\w+)')
    if (-not $focus.Success) { return $false }
    $focusHash = $focus.Groups[1].Value
    # mCurrentFocus only names the window; its flags live in that window's own block.
    foreach ($block in ($raw -split '(?m)^\s*Window #\d+ ')) {
        if ($block -notlike "Window{$focusHash*") { continue }
        # S1580: One UI prints the flags as a raw hex mask on its own line (`fl=81812180`) while AOSP
        # prints flag names, so matching the word SECURE answered "not protected" for every window on
        # a Samsung device - the exact false negative this probe exists to prevent. Read the number
        # when there is one (FLAG_SECURE is 0x2000) and keep the name match for the name-printing builds.
        $flags = [regex]::Match($block, '(?m)^\s*fl=#?([0-9a-fA-F]{1,16})\s*$')
        if ($flags.Success) {
            return [bool]([Convert]::ToUInt64($flags.Groups[1].Value, 16) -band 0x2000)
        }
        return [bool]($block -match '(?m)^\s*fl=[^\r\n]*\bSECURE\b')
    }
    return $false
}

# Resolve which app package id to act on: explicit -Package wins; else default debug
# (or release with -Release); fall back to the other variant if the chosen one is absent.
function Resolve-Package {
    param([string]$Id)
    $found = Find-InstalledPackage $Id
    if ($found) { return $found }
    $primary  = if ($Release) { $BASE_PACKAGE } else { $DEBUG_PACKAGE }
    $fallback = if ($Release) { $DEBUG_PACKAGE } else { $BASE_PACKAGE }
    Fail 4 "neither '$primary' nor '$fallback' is installed on $Id (build/install first)"
}

# The lookup half of Resolve-Package without the refusal: state-begin snapshots a device that may carry
# no app at all, and for it that is a legal state rather than exit 4.
function Find-InstalledPackage {
    param([string]$Id)
    if ($Package) { return $Package }
    $primary  = if ($Release) { $BASE_PACKAGE } else { $DEBUG_PACKAGE }
    $fallback = if ($Release) { $DEBUG_PACKAGE } else { $BASE_PACKAGE }
    foreach ($pkg in @($primary, $fallback)) {
        $pmRaw = & $adb -s $Id shell pm list packages $pkg 2>$null
        foreach ($pmLine in ($pmRaw -split "`r?`n")) {
            if ($pmLine.Trim() -eq "package:$pkg") { return $pkg }
        }
    }
    return $null
}

# Resolve which activity component `launch` should start. An EXPLICIT -Module is honoured as
# stated and refused on a conflict with the device - mirroring `install`'s guard (S1681/S2043),
# because a caller who named the wrong module needs to be told, not routed around. The common case
# is one device and no -Module at all though, and Select-Device's own module disambiguation (above)
# never runs for it - that branch exists only to pick among SEVERAL online devices - so an unnamed
# -Module used to trust the 'app_v2' default unconditionally and send `am start` at the phone's
# MainActivity on a watch (S2992): three /spec-sweep agents hit exactly that on 2026-09-11. An
# unnamed -Module now defers to what the selected device actually reports instead.
function Resolve-Activity {
    param([string]$Id)
    $isWatchDevice = Test-WatchDevice $Id
    if ($script:ModuleWasNamed) {
        if ($isWatchDevice -and $Module -ne 'wear') {
            Fail 1 "device $Id reports watch characteristics (ro.build.characteristics) but -Module is '$Module' - the phone's MainActivity does not exist in the wear build, since the watch declares its own activity under its own code namespace (S1984) while both modules share one applicationId (S1681). Pass -Module wear, or point -DeviceId at a phone."
        }
        if (-not $isWatchDevice -and $Module -eq 'wear') {
            Fail 1 "device $Id does not report watch characteristics but -Module wear was requested - the wear activity does not exist in the phone build. Point -DeviceId at the paired watch, or drop -Module wear."
        }
        if ($Module -eq 'wear') { return $WEAR_MAIN_ACTIVITY }
        return $MAIN_ACTIVITY
    }
    if ($isWatchDevice) { return $WEAR_MAIN_ACTIVITY }
    return $MAIN_ACTIVITY
}

# Form-factor signal shared by the round/rectangle fallback in Get-DisplayShape and the
# install-time module/device guard (S2043): both modules publish under one applicationId
# (S1681), so nothing else on the device can tell a phone install from a watch install apart.
function Test-WatchDevice {
    param([string]$Id)
    $chars = (Invoke-Adb $Id @('shell', 'getprop', 'ro.build.characteristics') -AllowFail) -join ''
    return $chars -match 'watch'
}

function Get-TempDir {
    # Ad-hoc CLI outputs are no-ticket scratch by nature (CLAUDE.md Rule 10.1) -> temp/scratch/.
    # -OutDir overrides that for work that IS ticket-bound, where Rule 10.1 asks for temp/Sxxxx/.
    $repoRoot = (Resolve-Path -Path (Join-Path $PSScriptRoot '..\..')).Path
    if ($OutDir) {
        $tempDir = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $repoRoot $OutDir }
        if (-not (Test-Path -LiteralPath $tempDir -PathType Container)) {
            New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
        }
        return (Resolve-Path -LiteralPath $tempDir).Path
    }
    $tempDir  = Join-Path (Join-Path $repoRoot 'temp') 'scratch'
    if (-not (Test-Path -Path $tempDir -PathType Container)) {
        New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    }
    return $tempDir
}

# Per-call timestamp. Plain Get-Date is fine here (this is an interactive CLI, not a
# replayable workflow), unlike the workflow runtime which forbids it.
function Get-Stamp { (Get-Date).ToString('yyyyMMdd_HHmmss') }

# ---------- device state journal (S3201) ----------

# Keyed by the device's own serial, never by the adb id: the watch is reached over wireless debugging
# and its ip:port changes every session, so an id-keyed journal written today would be invisible to
# tomorrow's state-begin - which is exactly the run that has to put it back.
function Get-StateSerial {
    param([string]$Id)
    if ($script:stateSerial) { return $script:stateSerial }
    $serial = ((Invoke-Adb $Id @('shell', 'getprop', 'ro.serialno') -AllowFail) -join '').Trim()
    if ([string]::IsNullOrWhiteSpace($serial) -or $serial -match '\s') { $serial = $Id }
    $script:stateSerial = $serial
    return $serial
}

function Get-StateJournalPath {
    param([string]$Id)
    # FMS_DEVICE_STATE_ROOT exists for the contract suite, which must not write the live store.
    $root = if ($env:FMS_DEVICE_STATE_ROOT) { $env:FMS_DEVICE_STATE_ROOT } else { (Resolve-Path -Path (Join-Path $PSScriptRoot '..\..')).Path }
    return (Get-DeviceStoreRecordPath -RepoRoot $root -Store State -Serial (Get-StateSerial $Id))
}

function Get-StateDataStoreDir {
    param([string]$JournalPath, [string]$Pkg)
    return (Join-Path ([System.IO.Path]::ChangeExtension($JournalPath, '.datastore')) $Pkg)
}

function Read-StateScalar {
    param([string]$Id, [string]$Key)
    $raw = (Invoke-Adb $Id (Get-DeviceStateReadCommand -Key $Key) -AllowFail) -join "`n"
    return (ConvertFrom-DeviceStateReading -Key $Key -Text $raw)
}

# Called by every verb that changes device state, BEFORE the change. The first original wins, so a
# second write of the same key in one run cannot overwrite the value the owner actually had.
function Save-StateOriginal {
    param([string]$Id, [string]$Key)
    $path = Get-StateJournalPath $Id
    $journal = Read-DeviceStateJournal -Path $path
    if ($null -eq $journal) { $journal = New-DeviceStateJournal -Serial (Get-StateSerial $Id) }
    if ($journal.entries.Contains($Key)) { return }
    Add-DeviceStateOriginal -Journal $journal -Key $Key -Original (Read-StateScalar $Id $Key) | Out-Null
    Write-DeviceStateJournal -Path $path -Journal $journal
}

# The app's DataStore directory as file name -> SHA-256, plus the bytes. run-as works on a debuggable
# build only; on any other build the listing is an error line, the name filter drops it, and the
# snapshot is empty - nothing a test does through this route can write there either.
function Get-DataStoreSnapshot {
    param([string]$Id, [string]$Pkg)
    $files = [ordered]@{}
    $bytesByName = @{}
    $listing = (Invoke-Adb $Id @('shell', "run-as $Pkg ls files/datastore") -AllowFail) -join "`n"
    foreach ($line in ($listing -split "`r?`n")) {
        $name = $line.Trim()
        if ($name -notmatch '^[\w.\-]+$') { continue }
        $encoded = (Invoke-Adb $Id @('shell', "run-as $Pkg base64 files/datastore/$name") -AllowFail) -join ''
        if ($encoded -notmatch '^[A-Za-z0-9+/=\s]+$') {
            Write-Host "WARN could not read files/datastore/$name of $Pkg - left out of the snapshot" -ForegroundColor Yellow
            continue
        }
        $bytes = [Convert]::FromBase64String(($encoded -replace '\s', ''))
        $files[$name] = Get-Sha256Hex -Bytes $bytes
        $bytesByName[$name] = $bytes
    }
    return @{ Files = $files; Bytes = $bytesByName }
}

function Format-StateValue {
    param($Value)
    if ($null -eq $Value -or "$Value" -eq '') { return '(none)' }
    return "$Value"
}

# Compares the device with the journal at -Path and, unless -ReportOnly, puts every drifted value
# back. Returns one object per drifted value. A DataStore file goes back through /data/local/tmp and
# a run-as copy after a force-stop: DataStore holds the file cached in the running process, and a
# copy under a live app is overwritten by its next write.
function Invoke-StateRestore {
    param([string]$Id, [string]$Path, [switch]$ReportOnly)
    $drift = [System.Collections.Generic.List[object]]::new()
    $journal = Read-DeviceStateJournal -Path $Path
    if ($null -eq $journal) { return $drift.ToArray() }
    foreach ($key in @($journal.entries.Keys)) {
        $original = $journal.entries[$key].original
        if ($key -like 'datastore.*') {
            $pkg = $key.Substring('datastore.'.Length)
            $recorded = $original.files
            $current = (Get-DataStoreSnapshot $Id $pkg).Files
            $fileDrift = @(Compare-DeviceStateFiles -Recorded $recorded -Current $current)
            if ($fileDrift.Count -eq 0) { continue }
            if (-not $ReportOnly) {
                Invoke-Adb $Id @('shell', 'am', 'force-stop', $pkg) | Out-Null
                $saved = Get-StateDataStoreDir -JournalPath $Path -Pkg $pkg
                foreach ($d in $fileDrift) {
                    if ($d.Action -eq 'remove') {
                        Invoke-Adb $Id @('shell', "run-as $pkg rm -f files/datastore/$($d.Name)") | Out-Null
                        continue
                    }
                    $tmp = "/data/local/tmp/fms_state_$($d.Name)"
                    Invoke-Adb $Id @('push', (Join-Path $saved $d.Name), $tmp) | Out-Null
                    Invoke-Adb $Id @('shell', "chmod 644 $tmp") | Out-Null
                    Invoke-Adb $Id @('shell', "run-as $pkg cp $tmp files/datastore/$($d.Name)") | Out-Null
                    Invoke-Adb $Id @('shell', "rm -f $tmp") -AllowFail | Out-Null
                }
            }
            foreach ($d in $fileDrift) {
                $drift.Add([pscustomobject]@{
                    key      = "$key/$($d.Name)"
                    current  = $current[$d.Name]
                    original = $recorded[$d.Name]
                    action   = $d.Action
                })
            }
            continue
        }
        $now = Read-StateScalar $Id $key
        if (-not (Compare-DeviceStateScalar -Original $original -Current $now)) { continue }
        if (-not $ReportOnly) {
            Invoke-Adb $Id (Get-DeviceStateRestoreCommand -Key $key -Original $original) | Out-Null
        }
        $drift.Add([pscustomobject]@{ key = $key; current = $now; original = $original; action = 'restore' })
    }
    return $drift.ToArray()
}

function Remove-StateJournal {
    param([string]$Path)
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
    $savedRoot = [System.IO.Path]::ChangeExtension($Path, '.datastore')
    if (Test-Path -LiteralPath $savedRoot) { Remove-Item -LiteralPath $savedRoot -Recurse -Force }
}

# ---------- uiautomator node tree ----------

# Dump the node tree and bring it back as parsed XML. The remote path never crosses bash, which is
# the same reason pull/push live in this script at all (S1578) - MSYS rewrites /sdcard/x into a path
# inside the Git installation, and adb then reports a missing remote object.
# S3238: on the Samsung fleet one dump is not a verdict. `uiautomator dump` refuses while it cannot
# reach an idle UI ('could not get idle state'), and the tree it does produce can lag the glass by
# seconds, so a tap aimed from a stale tree hits a screen that is gone. The dump call is therefore
# retried a bounded number of times, and -Stable re-dumps until two consecutive reads agree, which is
# what makes a single uidump / tap-id / tap-label answer describe the screen the caller is looking at.
#
# S3289 measured what that idle wait actually is and split the refusal in two. The dump calls
# UiAutomation.waitForIdle with a 500 ms quiet window and a 10 s budget; idle means no accessibility
# event of ANY type from ANY package reached the connection, and the command exposes no flag to
# shorten or skip it (`uiautomator help` lists only --verbose and --compressed). Two regimes follow,
# and they need different budgets:
#   transient - a window settling after a tap. Measured on RFCR110NBQJ: 2 refusals in 15 calls, the
#               next call through, so the state lives ~22 s. One retry clears it.
#   permanent - a window carrying a live readout. The same phone's player emits a content-change
#               event every 100-101 ms while a stream plays, which is ViewRootImpl's own coalescing
#               floor, so the 500 ms of quiet never arrives and the screen is undumpable for as long
#               as the readout runs. Retrying cannot help, and each refused attempt blocks for the
#               full 10 s budget (measured 11.10 s and 11.09 s), so three attempts burn ~35 s to
#               learn nothing.
# The idle refusal is therefore capped at one retry; every other failure answer keeps the full
# budget, because those are cheap and a different cause. The cure is app-side (S3293), and the one
# uiautomator-free tree source was measured unusable in S3289 - see dev/REFUTED_APPROACHES.md.
$script:UiTreeIdleRefusal = 'could not get idle state'
$script:UiTreeIdleAttempts = 2

# Why a refused dump is not something to wait out, in the words the caller needs at the moment of
# refusal. Kept beside the constants above so the two never drift.
function Get-UiTreeIdleRefusalText {
    param([int]$Attempts, [string]$LastOut)
    return ("uiautomator refused with 'could not get idle state' in $Attempts attempt(s); last answer was: " +
        "$($LastOut.Trim()). The dump waits for 500 ms with no accessibility event anywhere on the device " +
        "(budget 10 s) and has no flag to shorten or skip that wait. A window carrying a live readout - the " +
        "player while audio plays, the broadcast screen while broadcasting - emits a content-change event about " +
        "every 100 ms and never leaves that gap, so this screen will not dump however long you wait (S3289, " +
        "measured on RFCR110NBQJ). What works instead: `shot` for a screenshot, and this same screen once the " +
        "readout stops. There is no wrapper workaround - the app-side cure is S3293, and dumpsys was measured " +
        "unusable as a second tree source (dev/REFUTED_APPROACHES.md)")
}

function Get-UiTree {
    param([string]$Id, [string]$Destination, [int]$Attempts = 3, [switch]$Stable)
    $remote = '/sdcard/_fms_tree.xml'
    $retryDelayMs = 800
    $stableDelayMs = 900
    # Remove it FIRST. uiautomator refuses while the window is animating and writes nothing at all,
    # and `shot` uses this same remote path - so without this line a refused dump silently pulls the
    # previous screen's tree and every verb above reports confidently about a frame that is gone.
    $lastOut = ''
    $sawIdleRefusal = $false
    $budget = $Attempts
    for ($attempt = 1; $attempt -le $budget; $attempt++) {
        Invoke-Adb $Id @('shell', 'rm', '-f', $remote) -AllowFail | Out-Null
        $lastOut = (Invoke-Adb $Id @('shell', 'uiautomator', 'dump', $remote) -AllowFail) -join ' '
        if ($lastOut -match 'dumped to') { break }
        if ($lastOut -match $script:UiTreeIdleRefusal) {
            $sawIdleRefusal = $true
            if ($budget -gt $script:UiTreeIdleAttempts) { $budget = $script:UiTreeIdleAttempts }
        }
        if ($attempt -lt $budget) { Start-Sleep -Milliseconds ($retryDelayMs * $attempt) }
    }
    if ($lastOut -notmatch 'dumped to') {
        if ($sawIdleRefusal) { Fail 7 (Get-UiTreeIdleRefusalText -Attempts $budget -LastOut $lastOut) }
        Fail 7 ("uiautomator produced no tree in $budget attempt(s); last answer was: $($lastOut.Trim()). It " +
            "refuses while the window is still animating - let the screen settle and re-run")
    }
    Invoke-Adb $Id @('pull', $remote, $Destination) -AllowFail | Out-Null
    Invoke-Adb $Id @('shell', 'rm', '-f', $remote) -AllowFail | Out-Null
    if (-not (Test-Path -LiteralPath $Destination -PathType Leaf)) {
        Fail 7 "uiautomator reported a dump but the pull produced no file at $Destination - re-run"
    }
    # Explicit UTF8: uiautomator writes UTF-8 and the tree is the only place a non-Latin label
    # survives, so a default-encoding read turns every Cyrillic label into a row of question marks.
    $previous = Get-Content -LiteralPath $Destination -Raw -Encoding UTF8
    if (-not $Stable) {
        try { return [xml]$previous } catch { Fail 7 "the node tree at $Destination is not valid XML: $($_.Exception.Message)" }
    }
    # Stable read: re-dump until two consecutive trees agree. A lagging tree stays self-consistent
    # only until the accessibility layer catches up, so agreement across a pause is the cheapest
    # signal that the read now describes the glass. The newest read always wins - the file on disk
    # is the audit artifact and must match what this function returns - and a read that never
    # settles is reported, not silently passed off as current.
    $settled = $false
    $wentNonIdle = $false
    for ($read = 2; $read -le $Attempts; $read++) {
        Start-Sleep -Milliseconds $stableDelayMs
        Invoke-Adb $Id @('shell', 'rm', '-f', $remote) -AllowFail | Out-Null
        $dumpOut = (Invoke-Adb $Id @('shell', 'uiautomator', 'dump', $remote) -AllowFail) -join ' '
        # A readout that went live between the first read and this one will refuse every remaining
        # read for 10 s each and can never agree with anything (S3289). The first tree is already on
        # disk and is the best answer available, so stop paying for confirmation that cannot come.
        if ($dumpOut -match $script:UiTreeIdleRefusal) { $wentNonIdle = $true; break }
        if ($dumpOut -notmatch 'dumped to') { continue }
        $nextFile = "$Destination.next"
        Invoke-Adb $Id @('pull', $remote, $nextFile) -AllowFail | Out-Null
        Invoke-Adb $Id @('shell', 'rm', '-f', $remote) -AllowFail | Out-Null
        if (-not (Test-Path -LiteralPath $nextFile -PathType Leaf)) { continue }
        $current = Get-Content -LiteralPath $nextFile -Raw -Encoding UTF8
        Remove-Item -LiteralPath $nextFile -Force
        if ($current -eq $previous) { $settled = $true; break }
        $previous = $current
    }
    Set-Content -LiteralPath $Destination -Value $previous -NoNewline -Encoding UTF8
    if ($wentNonIdle) {
        Write-Host ("NOTE the screen stopped idling before the read could be confirmed on $Id - the listing is " +
            "the first tree and may lag the screen (S3289: a live readout blocks every further dump)") -ForegroundColor Yellow
    } elseif (-not $settled) {
        Write-Host "NOTE the tree did not settle after $Attempts reads on $Id - the listing may lag the screen" -ForegroundColor Yellow
    }
    try { return [xml]$previous } catch { Fail 7 "the node tree at $Destination is not valid XML: $($_.Exception.Message)" }
}

# Flatten the tree in document order, carrying down whether an ANCESTOR is scrollable. That flag is
# what separates "cut by the list it lives in" from "cut by the layout" in clip-check, and document
# order is what makes tap-label -Index reproducible between two dumps of the same screen.
# ---------- display shape ----------

# Read the physical glass outline off the device instead of hardcoding one (S1847). Measured
# 2026-08-20: a Galaxy Watch reports radius=240 on a 480x480 display with all four corner centres at
# (240,240) - a circle; a Galaxy S25 reports radius=105 on 1080x2340 with four distinct centres - a
# rounded rectangle. One corner-quadrant rule covers both, and the circle is just the case where the
# radius equals half the screen, so no watch-only branch is needed anywhere below.
#
# What the device prints is not automatically believable (S3357) - Resolve-DisplayRadius in
# lib/ui-tree.ps1 owns that judgement, and `trusted` on the returned shape is its answer.
function Get-DisplayShape {
    param([string]$Id)
    $sizeRaw = (Invoke-Adb $Id @('shell', 'wm', 'size') -AllowFail) -join "`n"
    # Override size wins when present: node bounds are reported in the overridden space.
    $m = [regex]::Match($sizeRaw, 'Override size:\s*(\d+)x(\d+)')
    if (-not $m.Success) { $m = [regex]::Match($sizeRaw, 'Physical size:\s*(\d+)x(\d+)') }
    if (-not $m.Success) { Fail 7 "could not read the display size from 'wm size': $sizeRaw" }
    $w = [int]$m.Groups[1].Value
    $h = [int]$m.Groups[2].Value

    $radii = @()
    $winRaw = (Invoke-Adb $Id @('shell', 'dumpsys', 'window', 'displays') -AllowFail) -join "`n"
    $block = [regex]::Match($winRaw, 'mRoundedCorners=RoundedCorners\{\[(.*?)\]\}')
    if ($block.Success) {
        $radii = @([regex]::Matches($block.Groups[1].Value, 'radius=(\d+)') | ForEach-Object { [int]$_.Groups[1].Value })
    }
    # One extra getprop, and only where the answer can change the reading: a square display is the
    # only one the round-watch fallback and the S3357 correction apply to.
    $isRoundWatch = ($w -eq $h) -and [bool](Test-WatchDevice $Id)

    $resolved = Resolve-DisplayRadius -Width $w -Height $h -Radii $radii -IsRoundWatch $isRoundWatch
    $radius = [int]$resolved.radius
    $isRound = ($radius * 2 -eq $w -and $radius * 2 -eq $h)
    return [ordered]@{
        width = $w; height = $h; radius = $radius; round = $isRound
        source = $resolved.source; trusted = [bool]$resolved.trusted; note = [string]$resolved.reason
    }
}

# ---------- verbs ----------

switch ($Verb.ToLowerInvariant()) {

    'help' {
        if ($Json) { Emit-Ok @{ verbs = 'help,devices,props,current,launch,stop,logcat-clear,wipe-data,install,uninstall,shot,uidump,clip-check,log,tap,tap-id,tap-label,swipe,text,key,prefs,pull,push,shell,font-scale,state-begin,state-check' } }
        Write-Host "adb.ps1 - ad-hoc device swiss-army" -ForegroundColor Cyan
        Write-Host "Usage: pwsh -NoProfile -File scripts/devtest/adb.ps1 <verb> [options]" -ForegroundColor Gray
        Write-Host ""
        Write-Host "  devices    list online devices (model + Android version)" -ForegroundColor White
        Write-Host "  props      selected device props (model, release, sdk, density, size)" -ForegroundColor White
        Write-Host "  current    focused activity / package" -ForegroundColor White
        Write-Host "  launch     start app (debug: explicit component; auto-detects a watch device)" -ForegroundColor White
        Write-Host "  stop       force-stop app" -ForegroundColor White
        Write-Host "  logcat-clear  empty the logcat buffer (alias log-clear) - no app state touched" -ForegroundColor White
        Write-Host "  wipe-data  DESTRUCTIVE pm clear - needs -Yes (data, grants, onboarding gone)" -ForegroundColor Yellow
        Write-Host "  install    install -r -d (-Apk <path> | -Flavor <std|lite|photos|legacy|noLegal>)" -ForegroundColor White
        Write-Host "  uninstall  DESTRUCTIVE uninstall resolved package - needs -Yes" -ForegroundColor Yellow
        Write-Host "  shot       screenshot to temp/scratch/" -ForegroundColor White
        Write-Host "  log        logcat -d app tail (-Tail N, -Grep regex)" -ForegroundColor White
        Write-Host "  uidump     dump the UI node tree: labels, ids, bounds, tap points (-Grep regex, -Ids)" -ForegroundColor White
        Write-Host "  tap-id     tap a node by its resource-id: -ResourceId <s> [-Exact] [-Index N] - preferred" -ForegroundColor White
        Write-Host "  tap-label  tap a node by its text/content-desc: -Label <s> [-Exact] [-Index N]" -ForegroundColor White
        Write-Host "  clip-check report content leaving the display shape (read from the device); -Strict fails on CLIPPED" -ForegroundColor White
        Write-Host "  font-scale read the system font scale, or set it with -Scale <n> (1.0 = default)" -ForegroundColor White
        Write-Host "  state-begin  open a device run: restore leftovers, snapshot device + app DataStore state" -ForegroundColor White
        Write-Host "  state-check  close a device run: restore drift, one RESTORED line each (-NoRestore = report)" -ForegroundColor White
        Write-Host "  tap        input tap -X <x> -Y <y>" -ForegroundColor White
        Write-Host "  swipe      input swipe -X <x> -Y <y> -X2 <x> -Y2 <y> [-Duration ms]" -ForegroundColor White
        Write-Host "  text       input text -Text <string>" -ForegroundColor White
        Write-Host "  key        input keyevent -Key <name-or-code> [-LongPress] [-Repeat N]" -ForegroundColor White
        Write-Host "  prefs      pull settings.preferences.pb to temp/scratch/ (run-as)" -ForegroundColor White
        Write-Host "  pull       fetch a file: -Remote <path> [-Local <path>] [-Latest] -> temp/scratch/" -ForegroundColor White
        Write-Host "  push       send a file: -Local <path> -Remote <path>" -ForegroundColor White
        Write-Host "  shell      passthrough -Cmd <adb shell command>" -ForegroundColor White
        Write-Host ""
        Write-Host "Common options: -DeviceId <id> -Release -Package <id> -OutDir <dir> -Json" -ForegroundColor Gray
        exit 0
    }

    'devices' {
        $devs = @(Get-OnlineDevices)
        if ($devs.Count -eq 0) { Fail 2 "no online device" }
        $rows = foreach ($id in $devs) {
            # NB: avoid a local named $release - it case-collides with the [switch]$Release param.
            $model   = (& $adb -s $id shell getprop ro.product.model 2>$null | Out-String).Trim()
            $rel     = (& $adb -s $id shell getprop ro.build.version.release 2>$null | Out-String).Trim()
            $sdk     = (& $adb -s $id shell getprop ro.build.version.sdk 2>$null | Out-String).Trim()
            [pscustomobject]@{ id = $id; model = $model; android = $rel; sdk = $sdk }
        }
        if ($Json) { Emit-Ok @($rows) }
        foreach ($r in $rows) {
            Write-Output ("  {0,-20} {1}  Android {2} (SDK {3})" -f $r.id, $r.model, $r.android, $r.sdk)
        }
        Write-Host "OK $($rows.Count) device(s) online" -ForegroundColor Cyan
        exit 0
    }

    'props' {
        $id = Select-Device
        $script:result.device = $id
        # NB: avoid a local named $release - it case-collides with the [switch]$Release param.
        $model   = (Invoke-Adb $id @('shell', 'getprop', 'ro.product.model') | Out-String).Trim()
        $rel     = (Invoke-Adb $id @('shell', 'getprop', 'ro.build.version.release') | Out-String).Trim()
        $sdk     = (Invoke-Adb $id @('shell', 'getprop', 'ro.build.version.sdk') | Out-String).Trim()
        $density = (Invoke-Adb $id @('shell', 'wm', 'density') | Out-String).Trim()
        $size    = (Invoke-Adb $id @('shell', 'wm', 'size') | Out-String).Trim()
        $data = [ordered]@{ id = $id; model = $model; android = $rel; sdk = $sdk; density = $density; size = $size }
        if ($Json) { Emit-Ok $data }
        Write-Output "device : $id"
        Write-Output "model  : $model"
        Write-Output "android: $rel (SDK $sdk)"
        Write-Output "$density"
        Write-Output "$size"
        exit 0
    }

    'current' {
        $id = Select-Device
        $script:result.device = $id
        # The resumed-activity field is named differently across Android versions
        # (mResumedActivity / ResumedActivity: / topResumedActivity=); match the common stem.
        $dump = Invoke-Adb $id @('shell', 'dumpsys', 'activity', 'activities') -AllowFail
        $line = ($dump -split "`r?`n" | Where-Object { $_ -match 'ResumedActivity' } | Select-Object -First 1)
        if (-not $line) {
            $win  = Invoke-Adb $id @('shell', 'dumpsys', 'window', 'windows') -AllowFail
            $line = ($win -split "`r?`n" | Where-Object { $_ -match 'mCurrentFocus' } | Select-Object -First 1)
        }
        $line = if ($line) { $line.Trim() } else { '(unknown)' }
        if ($Json) { Emit-Ok @{ id = $id; current = $line } }
        Write-Output $line
        exit 0
    }

    'launch' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        # Explicit component avoids the debug LeakCanary launcher pre-empting the app launcher.
        $activity = Resolve-Activity $id
        $startArgs = @('shell', 'am', 'start', '-n', "$pkg/$activity")
        $hasScreenDp = $PSBoundParameters.ContainsKey('ScreenDp')
        if ($GeometryMode -or $hasScreenDp) {
            if ($activity -ne $WEAR_MAIN_ACTIVITY) {
                Fail 1 "-GeometryMode / -ScreenDp are watch test parameters, but the resolved component is the phone's $activity"
            }
            # A running activity would get these as a new intent over an already-composed screen; the
            # force-stop makes the parameters describe the whole launch, which is what they promise.
            Invoke-Adb $id @('shell', 'am', 'force-stop', $pkg) | Out-Null
            if ($GeometryMode) { $startArgs += @('--es', 'fms_test_geometry', $GeometryMode) }
            if ($hasScreenDp) { $startArgs += @('--ei', 'fms_test_screen_dp', "$ScreenDp") }
        }
        Invoke-Adb $id $startArgs | Out-Null
        if ($Json) {
            $dp = if ($hasScreenDp) { $ScreenDp } else { $null }
            Emit-Ok @{ id = $id; package = $pkg; component = "$pkg/$activity"; geometryMode = $GeometryMode; screenDp = $dp }
        }
        Write-Host "LAUNCHED $pkg/$activity on $id" -ForegroundColor Green
        exit 0
    }

    'stop' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        Invoke-Adb $id @('shell', 'am', 'force-stop', $pkg) | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg } }
        Write-Host "STOPPED $pkg on $id" -ForegroundColor Green
        exit 0
    }

    { $_ -in 'logcat-clear', 'log-clear' } {
        $id = Select-Device
        $script:result.device = $id
        Invoke-Adb $id @('logcat', '-c') | Out-Null
        if ($Json) { Emit-Ok @{ id = $id } }
        Write-Host "LOGCAT BUFFER CLEARED on $id" -ForegroundColor Green
        exit 0
    }

    # Was named 'clear'. Renamed because the old name sat two lines from 'log' in the verb list and was
    # twice read as "clear the log": S1167 (2026-07-26, a granted accessibility service revoked) and S1569
    # (2026-08-11, the owner's working phone wiped). See S1572.
    'wipe-data' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        if (-not $Yes) {
            Fail 5 "wipe-data refused: this erases $pkg data, runtime grants and onboarding on $id, and cannot be undone. Re-run with -Yes if that is what you want, or use logcat-clear to empty the log buffer."
        }
        $out = Invoke-Adb $id @('shell', 'pm', 'clear', $pkg) -AllowFail
        if (($out -join '') -notmatch 'Success') { Fail 7 "pm clear did not report Success: $($out -join ' ')" }
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg } }
        Write-Host "WIPED data for $pkg on $id" -ForegroundColor Green
        exit 0
    }

    # Refuses rather than forwards: forwarding to wipe-data would keep the misreading alive, and naming
    # both replacements is what turns the mistake into a corrected instruction.
    'clear' {
        Fail 5 "the 'clear' verb was removed because it was ambiguous. Did you mean 'logcat-clear' (empty the log buffer, touches nothing) or 'wipe-data -Yes' (erase app data, one-way)? Nothing was executed."
    }

    'install' {
        $id = Select-Device
        $script:result.device = $id
        # Both modules publish under one applicationId (S1681), so nothing in the OS stops a
        # phone-module install from silently replacing the app running on a paired watch, or the
        # reverse - the wrong artifact lands and this verb still reports success (S2043).
        $isWatchDevice = Test-WatchDevice $id
        if ($isWatchDevice -and $Module -ne 'wear') {
            Fail 1 "device $id reports watch characteristics (ro.build.characteristics) but -Module is '$Module' - installing the phone build here would silently replace the watch app, since both modules share one applicationId (S1681). Pass -Module wear, or point -DeviceId at a phone."
        }
        if (-not $isWatchDevice -and $Module -eq 'wear') {
            Fail 1 "device $id does not report watch characteristics but -Module wear was requested - installing the wear build onto a non-watch device is almost certainly a mistake. Point -DeviceId at the paired watch, or drop -Module wear."
        }
        # S2090: the watch declares its own `version` dimension now, but only over two of the phone's six.
        # Naming the accepted set beats naming the rejection: the wear set is a strict subset, so the
        # plausible mistake is asking a watch for a phone-only flavor.
        $wearFlavors = @('standard', 'noLegal')
        if ($Module -eq 'wear' -and $wearFlavors -notcontains $Flavor) {
            Fail 1 "-Module wear does not have flavor '$Flavor': the watch module declares $($wearFlavors -join ', ')"
        }
        $apkPath = $Apk
        if (-not $apkPath) {
            $repoRoot = (Resolve-Path -Path (Join-Path $PSScriptRoot '..\..')).Path
            $apkDir = if ($Module -eq 'wear') {
                Join-Path $repoRoot "wear\build\outputs\apk\$Flavor\release"
            } else {
                Join-Path $repoRoot "app_v2\build\outputs\apk\$Flavor\debug"
            }
            # Ask for the architecture of the device this verb already selected, so a split build
            # installs what the device can run instead of whichever slice was written last (S1972).
            $deviceAbi = Get-TargetDeviceAbi -Adb $adb -DeviceId $id
            try {
                $resolved = Find-BuildArtifact -Dir $apkDir -Abi $deviceAbi
                if ($resolved) { $apkPath = $resolved.FullName }
            } catch {
                Fail 1 $_.Exception.Message
            }
        }
        if (-not $apkPath -or -not (Test-Path -Path $apkPath -PathType Leaf)) {
            # -Module wear only ever looks in the release directory - a debug watch build is never
            # auto-resolved and always needs an explicit -Apk (S2043).
            $buildHint = if ($Module -eq 'wear') { 'only the release wear artifact is auto-resolved - pass -Apk <path> for a debug watch build, or build the wear release variant first' } else { "build the $Flavor debug variant first" }
            Fail 1 "APK not found (pass -Apk <path>, or $buildHint)"
        }
        Invoke-Adb $id @('install', '-r', '-d', $apkPath) | Out-Null
        # S2855: record the install mark best-effort. The device that just accepted the artifact is
        # the exact source of what it now runs, so the version is read back through dumpsys instead
        # of parsing the APK locally. A candidate only counts when its lastUpdateTime is fresh -
        # an untouched package that merely answers dumpsys is a pre-existing install, not what this
        # verb put here, and marking it would write a wrong fact. A recording failure prints one
        # line and never fails the install: accounting must not break the work it accounts for.
        try {
            foreach ($cand in @($DEBUG_PACKAGE, $BASE_PACKAGE)) {
                $dump = (Invoke-Adb $id @('shell', 'dumpsys', 'package', $cand) -AllowFail) -join "`n"
                if ($dump -match 'versionName=([^\s]+)') {
                    $recVersion = $Matches[1]
                    $recFresh = $true
                    if ($dump -match 'lastUpdateTime=(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})') {
                        $recFresh = (((Get-Date) - [datetime]$Matches[1]).TotalMinutes -lt 10)
                    }
                    if ($recFresh) {
                        $regArgs = @('-Verb', 'Record', '-Id', $id, '-Package', $cand,
                            '-Module', $Module, '-Flavor', $Flavor,
                            '-BuildType', $(if ($cand -eq $DEBUG_PACKAGE) { 'debug' } else { 'release' }),
                            '-VersionName', $recVersion, '-Artifact', (Split-Path -Path $apkPath -Leaf),
                            '-RecordedBy', 'adb.ps1 install')
                        if ($dump -match 'versionCode=(\d+)') { $regArgs += @('-VersionCode', $Matches[1]) }
                        & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'device-registry.ps1') @regArgs *> $null
                    }
                    break
                }
            }
        }
        catch {
            Write-Host "note: install recording failed ($_)" -ForegroundColor DarkYellow
        }
        if ($Json) { Emit-Ok @{ id = $id; apk = $apkPath } }
        Write-Host "INSTALLED $apkPath on $id" -ForegroundColor Green
        exit 0
    }

    'uninstall' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        if (-not $Yes) {
            Fail 5 "uninstall refused: this removes $pkg and everything it stored on $id, and cannot be undone. Re-run with -Yes if that is what you want."
        }
        Invoke-Adb $id @('uninstall', $pkg) | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg } }
        Write-Host "UNINSTALLED $pkg from $id" -ForegroundColor Green
        exit 0
    }

    'shot' {
        $id = Select-Device
        $script:result.device = $id
        $stamp = Get-Stamp
        $name  = "$($id -replace '[^A-Za-z0-9_.-]', '_')_$stamp.png"
        $local = Join-Path (Get-TempDir) $name
        $remote = '/sdcard/_fms_shot.png'
        # screencap-then-pull keeps the PNG bytes intact (a `>` redirect of exec-out
        # corrupts binary on Windows pipelines).
        # Some emulator images cannot resolve their own default display and answer a bare
        # `screencap -p` with the usage text plus a zero-byte file; naming the display id
        # explicitly succeeds there, so fall back to it instead of reporting a dead capture.
        Invoke-Adb $id @('shell', 'screencap', '-p', $remote) -AllowFail | Out-Null
        $remoteSize = (Invoke-Adb $id @('shell', 'stat', '-c', '%s', $remote) -AllowFail) -join ''
        if ($remoteSize.Trim() -notmatch '^[1-9][0-9]*$') {
            $displayRaw = (Invoke-Adb $id @('shell', 'dumpsys', 'SurfaceFlinger', '--display-id') -AllowFail) -join "`n"
            if ($displayRaw -match 'Display\s+(\d+)') {
                Invoke-Adb $id @('shell', 'screencap', '-p', '-d', $Matches[1], $remote) | Out-Null
            }
        }
        Invoke-Adb $id @('pull', $remote, $local) | Out-Null
        Invoke-Adb $id @('shell', 'rm', '-f', $remote) -AllowFail | Out-Null
        if (-not (Test-Path -Path $local -PathType Leaf)) { Fail 7 "screenshot pull produced no file" }
        $secureWindow = Test-SecureFocusedWindow $id
        # S1520: a black frame is not evidence of layout, and the /spec-dev UI gate asks for "a
        # screenshot" - so on the three sensitive screens the requirement was met while nothing was
        # shown. S1506 already advised reading `uiautomator dump` instead, but an advisory needs
        # someone to remember it at the right moment, which is the class of rule this repository
        # measures at 1-8% compliance. So take the tree HERE, in the same call, whenever the flag is
        # up: the honest artifact has to be the automatic one. Best-effort throughout - a screenshot
        # must never fail on account of its own diagnostics.
        $treeFile = $null
        $treeNodes = 0
        if ($secureWindow) {
            $remoteTree = '/sdcard/_fms_tree.xml'
            $treeLocal = Join-Path (Get-TempDir) ($name -replace '\.png$', '_tree.xml')
            Invoke-Adb $id @('shell', 'uiautomator', 'dump', $remoteTree) -AllowFail | Out-Null
            Invoke-Adb $id @('pull', $remoteTree, $treeLocal) -AllowFail | Out-Null
            Invoke-Adb $id @('shell', 'rm', '-f', $remoteTree) -AllowFail | Out-Null
            if (Test-Path -Path $treeLocal -PathType Leaf) {
                $treeFile = $treeLocal
                # The node count is printed because an empty tree next to a black frame looks exactly
                # like a healthy one in a Step Log that records only a path.
                $treeNodes = ([regex]'<node\b').Matches((Get-Content -LiteralPath $treeLocal -Raw)).Count
            }
        }
        if ($Json) { Emit-Ok @{ id = $id; file = $local; secureWindow = $secureWindow; treeFile = $treeFile; treeNodes = $treeNodes } }
        Write-Host "SHOT $local" -ForegroundColor Green
        if ($secureWindow) {
            Write-Host "NOTE the focused window carries FLAG_SECURE - this capture is expected to be" -ForegroundColor Yellow
            Write-Host "     black (or zero-byte). That is the flag working, not a rendering failure." -ForegroundColor Yellow
            Write-Host "     BaseActivity.applySecureFlagIfEnabled sets it on sensitive screens (Settings," -ForegroundColor Yellow
            Write-Host "     Add Resource, Resource Editor) while the 'secureSensitiveScreens' setting is on." -ForegroundColor Yellow
            if ($treeFile) {
                Write-Host ("TREE $treeFile  ({0} node(s))" -f $treeNodes) -ForegroundColor Green
                Write-Host "     THIS file, not the black frame, is the layout evidence a /spec-dev UI phase" -ForegroundColor Yellow
                Write-Host "     records in its Step Log. To capture the screen as an image instead, turn the" -ForegroundColor Yellow
                Write-Host "     'secureSensitiveScreens' setting off first. See docs/TEST_SCENARIOS.md." -ForegroundColor Yellow
            } else {
                Write-Host "     The node tree could not be captured, so this run produced NO layout evidence." -ForegroundColor Yellow
                Write-Host "     Retry, or turn the 'secureSensitiveScreens' setting off and shoot again." -ForegroundColor Yellow
            }
        }
        exit 0
    }

    'log' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        # -v threadtime is already the device default, but pinning it makes the pid-column
        # parse deterministic instead of dependent on the device's own default.
        $raw = Invoke-Adb $id @('logcat', '-d', '-v', 'threadtime', '-t', "$Tail") -AllowFail
        $rawLines = $raw -split "`r?`n"

        # S1332: pid is the ownership signal - the app's own Timber lines carry a bare class
        # tag and name neither the package nor the project, so text alone never matched them.
        # The text arm survives as the second arm, for system-side lines about the app.
        $appPids = @()
        $pidofOut = (Invoke-Adb $id @('shell', 'pidof', $pkg) -AllowFail) -join ' '
        $appPids += @($pidofOut -split '\s+' | Where-Object { $_ -match '^\d+$' } | ForEach-Object { [int]$_ })
        $psOut = (Invoke-Adb $id @('shell', 'ps', '-A', '-o', 'PID,NAME') -AllowFail) -join "`n"
        foreach ($row in ($psOut -split "`r?`n")) {
            if ($row -match '^\s*(?<p>\d+)\s+(?<n>\S+)\s*$') {
                $name = $Matches['n']
                if ($name -eq $pkg -or $name.StartsWith("${pkg}:")) { $appPids += [int]$Matches['p'] }
            }
        }
        $appPids += Get-AppPidsFromLog -Lines $rawLines -BasePackage $BASE_PACKAGE
        $appPids = @($appPids | Sort-Object -Unique)

        $patterns = @([regex]::Escape($pkg), [regex]::Escape($BASE_PACKAGE), 'FastMediaSorter')
        $lines = Select-AppLogLines -Lines $rawLines -AppPids $appPids -TextPatterns $patterns
        $coverage = if ($Grep) { Measure-FilterCoverage -RawLines $rawLines -KeptLines $lines -Pattern $Grep } else { $null }
        if ($Grep) { $lines = $lines | Where-Object { $_ -match $Grep } }
        $logFile = Join-Path (Get-TempDir) "adb_log_$(Get-Stamp).log"
        ($raw -join "`n") | Out-File -FilePath $logFile -Encoding UTF8
        if ($Json) {
            Emit-Ok @{
                id = $id; package = $pkg; matched = @($lines).Count; file = $logFile
                rawMatched = if ($coverage) { $coverage.rawMatched } else { $null }
                suppressed = if ($coverage) { $coverage.suppressed } else { 0 }
                appPids    = $appPids
            }
        }
        # S1183: the matched lines are this verb's PRODUCT, so they go to the success stream. They
        # used to be written with Write-Host, which never reaches a pipeline - so
        # `adb.ps1 log | Out-File raw.log` created an empty file while the screen showed the lines,
        # and the caller fell back to raw adb.exe, losing device selection and the exit codes. The
        # decorations below stay on the information stream: a summary is not data.
        foreach ($l in $lines) { Write-Output $l }
        # A filter that swallows matching lines must say so: the old silent OK 0 was
        # indistinguishable from an honest no-match and produced wrong Broken verdicts.
        # Never echo the caller's pattern - smoke.ps1 and prerelease-prepare.ps1 match their
        # own crash regex against this script's whole stdout.
        if ($coverage -and $coverage.suppressed -gt 0) {
            Write-Host "WARN $((@($lines)).Count) line(s) (window $Tail); full capture: $logFile" -ForegroundColor Yellow
            Write-Host "     the filter dropped $($coverage.suppressed) line(s) matching the pattern - read the capture file" -ForegroundColor Yellow
            exit 0
        }
        Write-Host "OK $((@($lines)).Count) line(s) (window $Tail); full capture: $logFile" -ForegroundColor Cyan
        exit 0
    }

    'tap' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $PSBoundParameters.ContainsKey('X') -or -not $PSBoundParameters.ContainsKey('Y')) {
            Fail 1 "tap needs -X <x> -Y <y>"
        }
        Invoke-Adb $id @('shell', 'input', 'tap', "$X", "$Y") | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; x = $X; y = $Y } }
        Write-Host "TAP ($X,$Y) on $id" -ForegroundColor Green
        exit 0
    }

    'swipe' {
        $id = Select-Device
        $script:result.device = $id
        foreach ($p in @('X', 'Y', 'X2', 'Y2')) {
            if (-not $PSBoundParameters.ContainsKey($p)) { Fail 1 "swipe needs -X <x> -Y <y> -X2 <x> -Y2 <y> (optional -Duration ms)" }
        }
        Invoke-Adb $id @('shell', 'input', 'swipe', "$X", "$Y", "$X2", "$Y2", "$Duration") | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; from = @($X, $Y); to = @($X2, $Y2); durationMs = $Duration } }
        Write-Host "SWIPE ($X,$Y) -> ($X2,$Y2) in ${Duration}ms on $id" -ForegroundColor Green
        exit 0
    }

    'rotary' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $PSBoundParameters.ContainsKey('Axis')) { Fail 1 "rotary needs -Axis <double> (negative turns the other way; optional -Repeat N)" }
        if ($Repeat -lt 1) { Fail 1 "rotary needs -Repeat >= 1" }
        # S2548: a rotary encoder is a watch input, and the two modules publish under one applicationId,
        # so nothing but the form factor can tell this call was aimed at the wrong device.
        if (-not (Test-WatchDevice -Id $id)) {
            Fail 12 ("refusing to turn a bezel on $id - ro.build.characteristics does not report a watch. " +
                "A rotary encoder exists only on a watch; aim the call with -DeviceId <watch serial>")
        }
        $written = $Axis.ToString([System.Globalization.CultureInfo]::InvariantCulture)
        for ($turn = 0; $turn -lt $Repeat; $turn++) {
            # S3233: emit --axis SCROLL,<val> so negative values (e.g. -2) are not parsed by platform input as unsupported option flag
            Invoke-Adb $id @('shell', 'input', 'rotaryencoder', 'scroll', '--axis', "SCROLL,$written") | Out-Null
        }
        if ($Json) { Emit-Ok @{ id = $id; axis = $Axis; repeat = $Repeat } }
        Write-Host "ROTARY axis $written x$Repeat on $id" -ForegroundColor Green
        exit 0
    }

    'uidump' {
        $id = Select-Device
        $script:result.device = $id
        $file  = Join-Path (Get-TempDir) ("uitree_$($id -replace '[^A-Za-z0-9_.-]', '_')_$(Get-Stamp).xml")
        $nodes = @(Get-UiNodes (Get-UiTree $id $file -Stable))
        if (-not $Ids) { $nodes = @($nodes | Where-Object { $_.labelled }) }
        # -Grep spans the identifier too, so the same regex serves both ways of naming a target.
        if ($Grep) { $nodes = @($nodes | Where-Object { $_.label -match $Grep -or $_.resId -match $Grep }) }
        if ($Json) { Emit-Ok @{ id = $id; file = $file; count = $nodes.Count; nodes = @($nodes) } }
        Write-Host "TREE $file" -ForegroundColor Green
        foreach ($n in $nodes) {
            # The label is the product of this verb, so it goes to the success stream and survives a
            # redirect; the file path and the count are decoration and stay on information (S1183).
            Write-Output ("{0,-40} {1,-4} {2,-28} tap {3},{4}   bounds {5},{6}..{7},{8}" -f `
                $n.label.Replace("`n", ' '), $n.source, $n.resIdShort, $n.tapX, $n.tapY, $n.x1, $n.y1, $n.x2, $n.y2)
        }
        $filterNote = if ($Grep) { " matching '$Grep'" } else { '' }
        $kindNote   = if ($Ids) { 'named' } else { 'labelled' }
        Write-Host ("OK {0} {1} node(s){2}" -f $nodes.Count, $kindNote, $filterNote) -ForegroundColor Cyan
        if (-not $Ids) {
            Write-Host "     -Ids also lists the nodes carrying only a resource-id (a switch, an icon)" -ForegroundColor Gray
        }
        exit 0
    }

    'tap-id' {
        # Argument check BEFORE device selection: a call with no target is wrong whether or not a
        # device is attached, and answering 2 ("no device") would send the caller after the wrong bug.
        if (-not $ResourceId) { Fail 1 "tap-id needs -ResourceId <name-or-full-id> (add -Exact for a whole-value match)" }
        $id = Select-Device
        $script:result.device = $id
        $file  = Join-Path (Get-TempDir) ("uitree_$($id -replace '[^A-Za-z0-9_.-]', '_')_$(Get-Stamp).xml")
        $nodes = @(Get-UiNodes (Get-UiTree $id $file -Stable))
        $hits  = @(Select-UiNodesById $nodes $ResourceId -Exact:$Exact)
        if ($hits.Count -eq 0) {
            Fail 8 "no visible node carries the resource-id '$ResourceId' - nothing was tapped. The tree is at $file; run 'uidump -Ids' and match an id from ITS output. Causes, commonest first: this node carries no resource-id at all (TabLayout tabs carry none - reach those with tap-label), the list needs scrolling, the screen is still animating"
        }
        if ($Index -lt 1 -or $Index -gt $hits.Count) {
            Fail 1 "-Index $Index is out of range: '$ResourceId' matches $($hits.Count) node(s)"
        }
        $hit = $hits[$Index - 1]
        Invoke-Adb $id @('shell', 'input', 'tap', "$($hit.tapX)", "$($hit.tapY)") | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; resourceId = $hit.resId; label = $hit.label; x = $hit.tapX; y = $hit.tapY; matches = $hits.Count; file = $file } }
        Write-Host ("TAP-ID '{0}' at {1},{2} on {3}" -f $hit.resId, $hit.tapX, $hit.tapY, $id) -ForegroundColor Green
        if ($hits.Count -gt 1) {
            Write-Host ("     {0} nodes match this id; tapped #{1}. Pass -Index to choose another, or -Exact so one name is not read as the start of another." -f $hits.Count, $Index) -ForegroundColor Yellow
        }
        exit 0
    }

    'tap-label' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Label) { Fail 1 "tap-label needs -Label <text-or-content-desc> (add -Exact for a whole-value match)" }
        $file  = Join-Path (Get-TempDir) ("uitree_$($id -replace '[^A-Za-z0-9_.-]', '_')_$(Get-Stamp).xml")
        $nodes = @(Get-UiNodes (Get-UiTree $id $file -Stable))
        $hits  = @($nodes | Where-Object {
            if ($Exact) { $_.text -eq $Label -or $_.desc -eq $Label }
            else { $_.text -like "*$Label*" -or $_.desc -like "*$Label*" }
        })
        if ($hits.Count -eq 0) {
            Fail 8 "no visible node carries '$Label' - nothing was tapped. The tree is at $file; run 'uidump' and match a label from ITS output rather than a guessed or translated one. Causes, commonest first: the name is not on this screen at all, the list needs scrolling, the screen is still animating"
        }
        if ($Index -lt 1 -or $Index -gt $hits.Count) {
            Fail 1 "-Index $Index is out of range: '$Label' matches $($hits.Count) node(s)"
        }
        $hit = $hits[$Index - 1]
        Invoke-Adb $id @('shell', 'input', 'tap', "$($hit.tapX)", "$($hit.tapY)") | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; label = $hit.label; source = $hit.source; x = $hit.tapX; y = $hit.tapY; matches = $hits.Count; file = $file } }
        Write-Host ("TAP-LABEL '{0}' ({1}) at {2},{3} on {4}" -f $hit.label.Replace("`n", ' '), $hit.source, $hit.tapX, $hit.tapY, $id) -ForegroundColor Green
        if ($hits.Count -gt 1) {
            Write-Host ("     {0} nodes carry this label; tapped #{1}. Pass -Index to choose another, or -Exact to narrow." -f $hits.Count, $Index) -ForegroundColor Yellow
        }
        exit 0
    }

    'font-scale' {
        $id = Select-Device
        $script:result.device = $id
        # An untouched device has never written the key and answers 'null'; the platform reads that
        # as the default, so report the number the UI would show rather than the literal - and never
        # cast the literal, which throws and takes the whole verb down.
        $readScale = {
            $raw = ((Invoke-Adb $id @('shell', 'settings', 'get', 'system', 'font_scale') -AllowFail) -join '').Trim()
            if ([string]::IsNullOrWhiteSpace($raw) -or $raw -eq 'null') { '1.0' } else { $raw }
        }
        $current = & $readScale
        if (-not $PSBoundParameters.ContainsKey('Scale')) {
            if ($Json) { Emit-Ok @{ id = $id; scale = [double]$current } }
            Write-Host "FONT SCALE $current on $id" -ForegroundColor Green
            exit 0
        }
        $isEmulator = $id -like 'emulator-*'
        if (-not $isEmulator -and -not $Yes) {
            Fail 11 ("refusing to change the system font scale of a physical device without -Yes. " +
                "Whether THIS device may be changed at all is docs/DEVICE_FLEET.md - read it and match the serial first")
        }
        # Invariant culture on purpose: a Russian Windows interpolates 1.3 as "1,3", which the
        # platform stores verbatim and then reads back as an unusable value.
        $written = $Scale.ToString([System.Globalization.CultureInfo]::InvariantCulture)
        Save-StateOriginal $id 'settings.system.font_scale'
        Invoke-Adb $id @('shell', 'settings', 'put', 'system', 'font_scale', $written) | Out-Null
        $now = & $readScale
        if ($Json) { Emit-Ok @{ id = $id; scale = [double]$now; previous = [double]$current; written = [double]$written } }
        Write-Host "FONT SCALE $current -> $now on $id" -ForegroundColor Green
        exit 0
    }

    'clip-check' {
        $id = Select-Device
        $script:result.device = $id
        $shape = Get-DisplayShape $id
        # Refuse before the tree is even dumped (S3357): a shape nothing can be measured against
        # produces confident OFF-GLASS findings about an outline the display does not have, and
        # "could not look" must never arrive as "the app is broken". Code 7 lands in the wrapper
        # class that lib/clip-shape-outcome.ps1 already counts as shape-unchecked, so a walk built
        # on this aborts its sweep instead of filing a release blocker.
        if (-not $shape.trusted) {
            Fail 7 ("the display shape cannot be measured against: {0}. Nothing was judged" -f $shape.note)
        }
        $file  = Join-Path (Get-TempDir) ("uitree_$($id -replace '[^A-Za-z0-9_.-]', '_')_$(Get-Stamp).xml")
        $nodes = @(Get-UiNodes (Get-UiTree $id $file))
        $findings = [System.Collections.Generic.List[object]]::new()
        # Labelled only: S1879 widened the tree to nodes named by a resource-id alone, and this
        # classification is calibrated against five recorded dumps. Judging the new nodes would move
        # counts that were measured, not chosen.
        $judged = @($nodes | Where-Object { $_.leaf -and $_.labelled })
        foreach ($n in $judged) {
            $v = Get-ClipVerdict $n $shape
            if ($null -eq $v) { continue }
            $findings.Add([ordered]@{
                kind = $v.kind; label = $n.label.Replace("`n", ' '); overflow = [math]::Round($v.overflow, 1)
                x1 = $n.x1; y1 = $n.y1; x2 = $n.x2; y2 = $n.y2
            }) | Out-Null
        }
        $offGlass = @($findings | Where-Object { $_.kind -eq 'OFF-GLASS' })
        # Strict answers the reviewer's question, not the developer's: Play photographs one frame and
        # calls a box that leaves the glass "cut off", where CLIPPED only means a scroll could save it.
        $frameCut = @($findings | Where-Object { $_.kind -eq 'CLIPPED' })
        if ($Json) {
            # ToArray(), never @($findings): the array subexpression around a PSObject-wrapped
            # List[object] throws "Argument types do not match" and killed this -Json path (S2079).
            $script:result.data = [ordered]@{ id = $id; file = $file; shape = $shape; checked = $judged.Count; findings = $findings.ToArray(); offGlass = $offGlass.Count; frameCut = $frameCut.Count; strict = [bool]$Strict }
            $script:result.exitCode = 0
            if ($Strict -and $frameCut.Count -gt 0) {
                $script:result.exitCode = 10
                $script:result.reason = "$($frameCut.Count) node(s) cut by the glass in this frame"
            }
            if ($offGlass.Count -gt 0) {
                $script:result.exitCode = 9
                $script:result.reason = "$($offGlass.Count) node(s) off-glass"
            }
            $script:result.ok = ($script:result.exitCode -eq 0)
            $script:result | ConvertTo-Json -Compress -Depth 6
            exit $script:result.exitCode
        }
        Write-Host "TREE $file" -ForegroundColor Green
        Write-Host ("SHAPE {0}x{1} corner radius {2}{3} - {4}" -f `
            $shape.width, $shape.height, $shape.radius, $(if ($shape.round) { ' (round)' } else { '' }), $shape.source) -ForegroundColor Gray
        if ($shape.note) { Write-Host ("NOTE {0}" -f $shape.note) -ForegroundColor Yellow }
        foreach ($f in $findings) {
            $colour = if ($f.kind -eq 'OFF-GLASS') { 'Red' } else { 'Yellow' }
            Write-Host ("{0,-10} {1,-36} bounds {2},{3}..{4},{5}  worst corner {6} px from its arc centre (limit {7})" -f `
                $f.kind, $f.label, $f.x1, $f.y1, $f.x2, $f.y2, $f.overflow, $shape.radius) -ForegroundColor $colour
        }
        if ($shape.radius -le 0) {
            Write-Host "OK - this display reports no rounded corners, so nothing can leave its glass" -ForegroundColor Cyan
            exit 0
        }
        if ($offGlass.Count -gt 0) {
            Fail 9 "$($offGlass.Count) node(s) cannot fit on the glass at any scroll position"
        }
        if ($Strict -and $frameCut.Count -gt 0) {
            Fail 10 ("{0} node(s) cut by the glass in this frame: {1}. Scrolling would recentre them, but a store reviewer judges the frame" -f `
                $frameCut.Count, (($frameCut | ForEach-Object { $_.label }) -join ', '))
        }
        $edgeCount = @($findings | Where-Object { $_.kind -eq 'EDGE' }).Count
        if ($Strict) {
            Write-Host ("CLEAN (strict) - {0} leaf node(s) checked, none off-glass and none cut in this frame ({1} EDGE)" -f `
                $judged.Count, $edgeCount) -ForegroundColor Cyan
            exit 0
        }
        Write-Host ("CLEAN - {0} leaf node(s) checked, none off-glass ({1} EDGE, {2} CLIPPED are normal scrolling; -Strict judges those too)" -f `
            $judged.Count, $edgeCount, $frameCut.Count) -ForegroundColor Cyan
        exit 0
    }

    'text' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Text) { Fail 1 "text needs -Text <string>" }
        # adb input text: spaces must be %s; escape shell-special chars.
        $encoded = $Text -replace ' ', '%s'
        Invoke-Adb $id @('shell', 'input', 'text', $encoded) | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; text = $Text } }
        Write-Host "TEXT typed on $id" -ForegroundColor Green
        exit 0
    }

    'key' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Key) { Fail 1 "key needs -Key <name-or-code> (e.g. BACK, 4, KEYCODE_HOME)" }
        if ($Repeat -lt 1) { Fail 1 "key needs -Repeat >= 1" }
        $keyArgs = @('shell', 'input', 'keyevent')
        if ($LongPress) { $keyArgs += '--longpress' }
        # `input keyevent` takes several codes and sends them back to back, which is what keeps a
        # burst inside one counting window; a loop of adb calls would pay a round trip per press.
        $keyArgs += @(1..$Repeat | ForEach-Object { $Key })
        Invoke-Adb $id $keyArgs | Out-Null
        if ($Json) { Emit-Ok @{ id = $id; key = $Key; longPress = [bool]$LongPress; repeat = $Repeat } }
        $keyNote = "$(if ($LongPress) { ' (long press)' })$(if ($Repeat -gt 1) { " x$Repeat" })"
        Write-Host "KEY $Key$keyNote on $id" -ForegroundColor Green
        exit 0
    }

    'prefs' {
        $id  = Select-Device
        $pkg = Resolve-Package $id
        $script:result.device = $id; $script:result.package = $pkg
        $local = Join-Path (Get-TempDir) "settings_$(Get-Stamp).preferences.pb"
        $encoded = & $adb -s $id shell "run-as $pkg base64 /data/data/$pkg/files/datastore/settings.preferences_pb" 2>$null
        if (-not $encoded) { Fail 7 "could not read settings.preferences.pb (non-debuggable build or file absent)" }
        try {
            $bytes = [Convert]::FromBase64String(($encoded -join ''))
        } catch {
            Fail 7 "could not decode settings.preferences.pb from $pkg"
        }
        [System.IO.File]::WriteAllBytes($local, $bytes)
        if ($Json) { Emit-Ok @{ id = $id; package = $pkg; file = $local } }
        Write-Host "PREFS $local" -ForegroundColor Green
        exit 0
    }

    'pull' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Remote) { Fail 1 "pull needs -Remote <device path> (add -Latest to take the newest match of a directory or glob)" }
        $remotePath = $Remote
        if ($Latest) {
            # -1t sorts newest first and the device shell expands the glob, so the mask never
            # reaches PowerShell, which would try to resolve it against the local filesystem.
            # -p marks directories with a trailing slash, which is how they are dropped here: "the
            # newest thing in this folder" means the newest file, never a subfolder.
            # @() guards the single-match case: one string indexed with [0] yields a char, not a line.
            $listing = @((Invoke-Adb $id @('shell', "ls -1pt $Remote") -AllowFail) -split "`r?`n" |
                Where-Object { $_ -and $_ -notmatch 'No such file|Permission denied' -and $_ -notmatch '/\s*$' })
            if ($listing.Count -eq 0) { Fail 6 "no file on $id matches '$Remote'" }
            $remotePath = $listing[0].Trim()
            # A listing prints bare names, not paths. The directory they belong to is -Remote itself when
            # it names a directory, and -Remote's parent when it carries a glob - getting this backwards
            # rebuilds a sibling of the real file and reports it as missing.
            if ($remotePath -notmatch '^/') {
                $parent = if ($Remote -match '[*?\[]') { $Remote -replace '/[^/]*$', '' } else { $Remote.TrimEnd('/') }
                $remotePath = "$parent/$remotePath"
            }
        }
        # %F alongside %s: a directory has a size too, so size alone cannot say what was pulled, and
        # `adb pull` of a directory lands a directory - which a file-only existence check reads as failure.
        # adb joins the shell arguments back into one command line for the device shell, so a path with
        # spaces or parentheses - `Download/App (1).apk`, exactly what -Latest tends to find - has to be
        # quoted here or the device shell splits it and the file reads as missing.
        $quotedRemote = "'" + ($remotePath -replace "'", "'\''") + "'"
        $statRaw = ((Invoke-Adb $id @('shell', "stat -c '%F|%s' $quotedRemote") -AllowFail) -join '').Trim()
        if ($statRaw -notmatch '^(?<kind>[^|]+)\|(?<size>\d+)$') { Fail 6 "'$remotePath' does not exist on $id" }
        $isDirectory = $Matches['kind'] -like '*directory*'
        $localPath = if ($Local) { $Local } else { Join-Path (Get-TempDir) (Split-Path -Path $remotePath -Leaf) }
        Invoke-Adb $id @('pull', $remotePath, $localPath) | Out-Null
        if (-not (Test-Path -Path $localPath)) { Fail 7 "pull of '$remotePath' produced no local file" }
        $bytes = if ($isDirectory) { 0 } else { (Get-Item -Path $localPath).Length }
        if ($Json) { Emit-Ok @{ id = $id; remote = $remotePath; file = $localPath; size = $bytes } }
        Write-Host "PULLED $localPath ($bytes bytes) from $remotePath" -ForegroundColor Green
        exit 0
    }

    'push' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Local -or -not $Remote) { Fail 1 "push needs -Local <local path> -Remote <device path>" }
        # Refused here rather than on the device: adb's own error for a missing source reads like a
        # device-side problem and sends the reader looking in the wrong place.
        if (-not (Test-Path -Path $Local)) { Fail 1 "local path '$Local' does not exist" }
        # A directory is accepted because adb push copies a tree natively; the Leaf-only test this
        # replaces refused one, which made seeding a media corpus fall back to a raw adb - the exact
        # call that rewrites /sdcard into a Windows path from a POSIX-style shell (S2602).
        $isDirectory = Test-Path -Path $Local -PathType Container
        Invoke-Adb $id @('push', $Local, $Remote) | Out-Null
        $kind = if ($isDirectory) { 'directory' } else { 'file' }
        if ($Json) { Emit-Ok @{ id = $id; local = $Local; remote = $Remote; kind = $kind } }
        Write-Host "PUSHED $kind $Local -> $Remote on $id" -ForegroundColor Green
        exit 0
    }

    'shell' {
        $id = Select-Device
        $script:result.device = $id
        if (-not $Cmd) { Fail 1 "shell needs -Cmd `"<adb shell command>`"" }
        $mutation = ConvertFrom-ShellMutation -Command $Cmd
        if ($mutation) { Save-StateOriginal $id $mutation.Key }
        $out = & $adb -s $id shell $Cmd 2>&1
        $code = $LASTEXITCODE
        if ($Json) { Emit-Ok @{ id = $id; cmd = $Cmd; exit = $code; out = ($out -join "`n") } }
        # S1183: the command's own output is this verb's product - success stream, so it can be piped
        # or redirected. `adb.ps1 shell -Cmd 'logcat -d' | Out-File raw.log` used to write an empty
        # file with no error at all, which is silent data loss rather than a visible failure.
        foreach ($l in $out) { Write-Output $l }
        if ($code -ne 0) { Fail 7 "shell command exit $code" }
        exit 0
    }

    'state-begin' {
        $id = Select-Device
        $script:result.device = $id
        $path = Get-StateJournalPath $id
        # A journal already here is a run that never closed. Its originals are the owner's real values,
        # so they go back before a new snapshot could record the leftovers as originals.
        $leftover = @(Invoke-StateRestore -Id $id -Path $path)
        Remove-StateJournal -Path $path
        $journal = New-DeviceStateJournal -Serial (Get-StateSerial $id)
        foreach ($key in @('wm.density', 'wm.size', 'settings.system.font_scale')) {
            Add-DeviceStateOriginal -Journal $journal -Key $key -Original (Read-StateScalar $id $key) | Out-Null
        }
        $pkg = Find-InstalledPackage $id
        $script:result.package = $pkg
        $dataStoreFiles = 0
        if ($pkg) {
            $snapshot = Get-DataStoreSnapshot $id $pkg
            $saved = Get-StateDataStoreDir -JournalPath $path -Pkg $pkg
            New-Item -ItemType Directory -Path $saved -Force | Out-Null
            foreach ($name in $snapshot.Bytes.Keys) {
                [System.IO.File]::WriteAllBytes((Join-Path $saved $name), $snapshot.Bytes[$name])
            }
            Add-DeviceStateOriginal -Journal $journal -Key "datastore.$pkg" -Original ([ordered]@{ files = $snapshot.Files }) | Out-Null
            $dataStoreFiles = $snapshot.Files.Count
        }
        Write-DeviceStateJournal -Path $path -Journal $journal
        if ($Json) { Emit-Ok @{ id = $id; journal = $path; keys = @($journal.entries.Keys); dataStoreFiles = $dataStoreFiles; restoredLeftovers = $leftover } }
        foreach ($d in $leftover) {
            Write-Host "RESTORED $($d.key) $(Format-StateValue $d.current) -> $(Format-StateValue $d.original) (left by an earlier run)" -ForegroundColor Yellow
        }
        Write-Host "STATE BEGIN $($journal.entries.Count) key(s), $dataStoreFiles DataStore file(s) on $id -> $path" -ForegroundColor Green
        exit 0
    }

    'state-check' {
        $id = Select-Device
        $script:result.device = $id
        $path = Get-StateJournalPath $id
        $hadJournal = Test-Path -LiteralPath $path
        $drift = @(Invoke-StateRestore -Id $id -Path $path -ReportOnly:$NoRestore)
        if ($NoRestore) {
            if ($drift.Count -gt 0) {
                if (-not $Json) {
                    foreach ($d in $drift) { Write-Host "DRIFT $($d.key) $(Format-StateValue $d.current) (was $(Format-StateValue $d.original))" -ForegroundColor Yellow }
                }
                Fail 13 "$($drift.Count) value(s) differ from the journal and were not put back (-NoRestore)"
            }
            if ($Json) { Emit-Ok @{ id = $id; journal = $path; hadJournal = $hadJournal; restored = @() } }
            Write-Host "STATE CLEAN on $id" -ForegroundColor Green
            exit 0
        }
        # Proof, not intent: the restore is judged by reading the device again.
        $remaining = @(Invoke-StateRestore -Id $id -Path $path -ReportOnly)
        if (-not $Json) {
            foreach ($d in $drift) { Write-Host "RESTORED $($d.key) $(Format-StateValue $d.current) -> $(Format-StateValue $d.original)" -ForegroundColor Yellow }
        }
        if ($remaining.Count -gt 0) {
            Fail 13 "$($remaining.Count) value(s) still differ after the restore: $(($remaining | ForEach-Object { $_.key }) -join ', ') - journal kept at $path"
        }
        Remove-StateJournal -Path $path
        if ($Json) { Emit-Ok @{ id = $id; journal = $path; hadJournal = $hadJournal; restored = $drift } }
        $note = if ($hadJournal) { '' } else { ' (no journal - nothing was recorded)' }
        Write-Host "STATE CHECK $($drift.Count) restored on $id$note" -ForegroundColor Green
        exit 0
    }

    default {
        Fail 1 "unknown verb '$Verb' (run with no verb, or 'help', for the list)"
    }
}
