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

6. **Multi-display AVD `screencap` contaminates the PNG.** On an AVD that reports `[Warning] Multiple displays...`, `adb exec-out screencap -p > shot.png` prepends the warning text to the binary, so the file fails as an image ("starts with [Warning]"). `screencap -d 0` errors `Display Id '0' is not valid`. Fix: capture raw and strip everything before the PNG magic `\x89PNG\r\n\x1a\n` (python `raw.find(b'\x89PNG\r\n\x1a\n')`, write from that offset). Alternative: `screencap` to a device path then `adb pull` - but device paths need `MSYS_NO_PATHCONV=1` in Git Bash or `/sdcard/..` becomes `C:/Program Files/Git/sdcard/..`. Seen 2026-06-25 on the wide near-square AVD (emulator-5556, 2076x2152, ~852dp - useful for testing wide-portrait/`isWideLayout` since it is technically portrait yet >=600dp wide).
**How to apply:** when a pulled screenshot reads as "JSON/text starts with [Warning]", strip-to-PNG-magic instead of retrying screencap.
