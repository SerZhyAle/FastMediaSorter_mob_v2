---
name: avd-device-sweep-gotchas
description: Infra traps when driving the app on the headless Pixel_4 AVD via mobile-mcp/adb (input wedge, ACCESS_LOCAL_NETWORK, logcat death, UiAutomationService a11y crash)
type: feedback
---

Four recurring infra traps when running /spec-prerelease or /spec-test-device on the headless `Pixel_4` AVD (emulator-5554, Android 17). Each cost real time on the 2026-06-17 S0484 sweep.

1. **Touch input can wedge while keyevents survive.** All taps/swipes (adb `input` AND mobile-mcp) silently ignored, window focus correct, app not crashed. Test with `input keyevent KEYCODE_HOME` - if focus leaves the app, keys work and touch is wedged. Recovery: cold-reboot the emulator (`adb -s <id> emu kill`, then relaunch with the SAME args from the running process cmdline plus `-no-snapshot-load`). The 11 `virtio_input_multi_touch_*` devices are NORMAL for this AVD, not the cause.
**Why:** a wedged input dispatcher; only a fresh qemu launch clears it. AVD userdata (installed app, granted perms, seeded media, prefs) persists across reboot.
**How to apply:** if the first tap of a sweep does nothing, don't retry-loop - keyevent-probe, then reboot.

2. **SMB/SFTP/FTP need `android.permission.ACCESS_LOCAL_NETWORK` (Android 16+).** NetworkReachabilityGate blocks every network resource behind a runtime "Доступ к локальной сети" dialog even for public-internet endpoints. Grant headless: `adb shell pm grant com.sza.fastmediasorter.debug android.permission.ACCESS_LOCAL_NETWORK`. Also grant `MANAGE_EXTERNAL_STORAGE` via `appops set <pkg> MANAGE_EXTERNAL_STORAGE allow` for LOCAL listing.
**Why:** new OS local-network protection; the gate is blanket, not LAN-only.
**How to apply:** grant both before the network leg, or the listing hangs with no COMPLETE marker.

3. **Detached `adb logcat` capture can die mid-run.** A `Start-Process adb logcat` stream dropped ~7 min in (during heavy video/standalone phase), leaving a truncated run log. Recover the gap by dumping the device ring buffer into the log (`adb logcat -d -v time *:V >> run.log`) and restarting live capture; merge before the verdict. The verdict dedups errors and treats crash markers as boolean, so overlap from re-dumps is safe.
**How to apply:** before running prerelease-verdict, check the capture PID is alive and the log's last timestamp is recent; if not, ring-dump to fill.

4. **mobile-mcp `list_elements` coordinates are TOP-LEFT, not center.** Tap at `x+width/2, y+height/2`. And match rows by a UNIQUE field (e.g. `tvResourcePath`), never by a type label like `text="SFTP"` which also appears as `tvResourceType` on every SFTP row.

5. **mobile-mcp a11y tree can wedge entirely - `list_elements` throws `Cannot read properties of undefined (reading 'node')` on every call.** Root cause shows in logcat as repeated `FATAL EXCEPTION: main / IllegalStateException: UiAutomationService ... already registered!` in the **mobile-mcp instrumentation** process (its own pid, NOT com.sza.fastmediasorter). The harness UiAutomator service fails to register, so no accessibility tree is ever built and only blind coordinate taps remain - which drift into content tiles under the toolbar and make multi-step scenarios undriveable. The app is fine (0 app crashes). Seen 2026-06-24 on S0660 device run.
**Why:** a stale/duplicate UiAutomationService registration on the AVD; mobile-mcp cannot claim it.
**How to apply:** if `list_elements` errors with `reading 'node'`, grep the run log for `UiAutomationService ... already registered` to confirm it is the harness (not the app). Don't burn taps blind-navigating - restart the emulator AND the mobile-mcp server to clear the registration, or fall back to a real device / manual test. For a feature already built + compiled, declare the device run INCONCLUSIVE (harness), keep `BlockNeedUserTest`, and do NOT chain `/spec-check` (it would strip the debug probes prematurely).

6b. **Some gesture TYPES are structurally non-injectable on the AVD even when normal taps work - distinct from #1's blanket wedge.** On the emulator-5554 API-35 AVD (2026-07-24 sweep): mouse **secondary-click** (`ACTION_BUTTON_PRESS` + `BUTTON_SECONDARY`, e.g. right-click context menu) is not injectable via adb `input` or mobile-mcp at all (S1111). Normal taps elsewhere in the app work fine, so it is not the #1 dispatcher wedge - the specific event class just has no injection path. Rebooting does not help.
**Why:** adb/mobile-mcp inject a plain touch; there is no synthetic path for a secondary mouse button.
**How to apply:** for a ticket whose ACCEPTANCE hinges on right-click / mouse-secondary, verify what IS observable (button present + placed + no crash, fix present in code, probe fires) and declare INCONCLUSIVE fast with that partial evidence - keep `BlockNeedUserTest`, needs a real device/mouse. Time-box interactive checks to 2-3 attempts.

6c. **The OS top-edge band swallows injected taps - this is what the 2026-07-24 "fullscreen tap-through is non-injectable" note actually was.** Corrected 2026-07-26 (S1115 re-run): a tap at **y=95 produces no TOUCH event at all**, while **y=150 works**, on the same screen and the same view. Nothing about fullscreen or SurfaceView is special - the top ~120 px is a system gesture/status band that eats injected input before the app sees it. The earlier diagnosis blamed the wrong thing and cost 36 min of fighting a phantom.
**Why:** the system reserves the top edge; `adb input tap` there is consumed by the OS, not routed to the focused window.
**How to apply:** when an injected tap on a top-anchored control does nothing, do NOT conclude the control or the surface is undriveable - re-aim below y≈120 and retry once. Buttons genuinely pinned to the very top edge (PDF/EPUB fullscreen toggles) stay untestable by injection; say so explicitly rather than calling the whole scenario non-injectable.

6d. **`SettingsActivity` captures BLACK on this AVD while its content is really there.** `adb exec-out screencap` of the settings screen returns an all-black frame (status bar only), yet `uiautomator dump` on the same screen lists every row, and taps land correctly. Confirmed twice on 2026-07-26 (S1107 run, then S1161). Any acceptance criterion phrased as "it LOOKS right" on a settings screen is unverifiable by screenshot here - S1161's re-flow-animation criterion had to stay unverified for exactly this reason.
**Why:** unclear (secure-surface or hardware-layer interaction with this AVD's renderer); the app is fine.
**How to apply:** on settings screens, verify layout from the `uiautomator` node bounds (they carry real x/y/width), not from a screenshot. Do not conclude "the screen is blank / failed to render" from a black capture - dump the tree first.

6. **Multi-display AVD `screencap` contaminates the PNG.** On an AVD that reports `[Warning] Multiple displays...`, `adb exec-out screencap -p > shot.png` prepends the warning text to the binary, so the file fails as an image ("starts with [Warning]"). `screencap -d 0` errors `Display Id '0' is not valid`. Fix: capture raw and strip everything before the PNG magic `\x89PNG\r\n\x1a\n` (python `raw.find(b'\x89PNG\r\n\x1a\n')`, write from that offset). Alternative: `screencap` to a device path then `adb pull` - but device paths need `MSYS_NO_PATHCONV=1` in Git Bash or `/sdcard/..` becomes `C:/Program Files/Git/sdcard/..`. Seen 2026-06-25 on the wide near-square AVD (emulator-5556, 2076x2152, ~852dp - useful for testing wide-portrait/`isWideLayout` since it is technically portrait yet >=600dp wide).
**How to apply:** when a pulled screenshot reads as "JSON/text starts with [Warning]", strip-to-PNG-magic instead of retrying screencap.
