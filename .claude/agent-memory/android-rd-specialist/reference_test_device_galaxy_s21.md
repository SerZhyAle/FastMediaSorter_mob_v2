---
name: test-device-galaxy-s21
description: Owner's physical Android devices - S21+ (SM-G996U1) blanket-authorized test device, S20 FE lent per-test, personal phone is Galaxy S25 FE (2026-07-28); check the serial before acting
metadata:
  type: reference
---

**Different phones appear, and they carry different permissions. Read the serial first.**

- **SM-G996U1 / RFCR110NBQJ - Galaxy S21+, Android 15.** Dedicated test device, blanket "do anything" authorization (details below).
- **SM-G781B / RFCRA133MXB - Galaxy S20 FE, Android 13 (SDK 33), 1080x2400.** A **working phone** of the owner, attached 2026-07-26 for one specific ticket. No blanket authorization: it carries his Telegram, his launcher layout, his lock screen (secure - adb cannot unlock it, ask him). Treat every change as borrowed: restore what you touch, and never grant a system role or change a default app on it. See [[never-grant-system-roles-on-owner-phone]].
- **Galaxy S25 FE - the owner's personal phone as of 2026-07-28** (stated in chat for S1261: sub-1x zoom values missing there; never attached over adb so far). Multi-lens Samsung, system camera offers 0.5x. Same borrowed-phone caution as the S20 FE if it ever appears on adb; camera tickets S1260/S1261/S1262 are acceptance-tested on it by the owner himself.

The owner has a dedicated physical test device for FastMediaSorter and authorized doing ANYTHING on it (install/uninstall, grant any permission incl. All-Files-Access + draw-over-apps, change settings, screen-capture, factory-style resets of app data). Confirmed 2026-06-27. **That authorization is device-specific - it does not transfer to whatever phone happens to be plugged in.**

- Device: Samsung Galaxy S21+ 5G, model **SM-G996U1**, adb serial **RFCR110NBQJ**, **Android 15 (SDK 35)**, **1080x2400 @ 450dpi** (true tall phone aspect 2.22).
- Ideal for: aspect-sensitive layout verification (S0670 compactness needs exactly this tall aspect - the local AVD is near-square 2076x2152), Android-15 specialUse/visible-overlay FGS (S0672/S0724), real-device draw/crop (S0676/S0679), real-storage MOVE/delete (S0710).
- NOT a Wear device - S0725/S0715 Wear LeakCanary still needs separate Wear hardware.
- The **release** package `com.sza.fastmediasorter` (Play build) is installed alongside; the debug build is `com.sza.fastmediasorter.debug` (separate package, no conflict).

**Why:** owner connected it mid-session and said "do anything on it", removing the usual device-state caution.

**How to apply:** when this serial/model is connected, drive device tests freely without asking about destructive permission grants or settings changes. Quirk: the device blips adb-offline mid-drive (Samsung) - on "device not found", just re-run `adb.ps1 devices` and continue; the prior tap usually registered. Verify it is still connected (serial may differ on reconnect) before acting.

**One UI automation limits (recurring, confirmed 2026-06-28 sweep):** Samsung One UI blocks several end-to-end UI paths from mobile-mcp - the OS EdgePanel **intercepts automated left-edge swipes**, and the launcher **does not auto-place pinned widgets/shortcuts** (drag-to-place isn't drivable; trampolines/panel activities are non-exported). So "swipe-to-trigger-gesture" and "tap-pinned-shortcut/widget-to-launch" sub-steps are NOT automatable. Verdict pattern that round: verify the request/seed *fires* (logcat `Timber.d("Sxxxx:` probe) + DataStore/overlay state + code path, mark the final launch/tap **manual** (still Verified, not FAIL) - applied to S0663/S0637/S0662(RIGHT/DOWN)/S0683; S0568 widget-tap had no probe fire at all so it stayed BlockNeedUserTest. Also: overlay windows (gesture strip 4px) are **excluded from MediaProjection screencap**, so visual overlay rendering needs a human eye, not a screenshot. Real device also reproduced two defects the specs blamed on emulators: S0613 `PrintManager.print()` "Can print only from an activity" IllegalStateException, and S0700 grid first-frame thumbnail black (ExoPlayer releases before frame lands).
