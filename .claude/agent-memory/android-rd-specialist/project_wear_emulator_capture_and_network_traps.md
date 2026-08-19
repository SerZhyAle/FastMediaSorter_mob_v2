---
name: wear-emulator-capture-and-network-traps
description: Wear OS AVD - screencap returns a solid-black frame for the app window, and ping fails even though the network is validated; both read as blockers and neither is one
metadata:
  type: project
---

Two false blockers on the Wear OS emulator (`sdk_gwear_x86_64`, SDK 37, 480x480 @320, AVD
`Wear_OS_XL_Round`), both measured 2026-08-19.

**1. `screencap` captures the app window as solid black.** `adb exec-out screencap -p` returns a
valid 1975-byte 480x480 PNG that is entirely black - I opened it myself to confirm, so this is
not an agent misreading a file size. The watch face captures correctly from the same device, so
the capture path works in general; only the app's own window is black. It is NOT `FLAG_SECURE` -
`wear/src/main` contains no `FLAG_SECURE` anywhere. Disabling hardware overlays
(`service call SurfaceFlinger 1008 i32 1`) does **not** fix it.

**How to apply:** `uiautomator dump` still sees the real content (it returned the title
"FastMedia Wear" plus Resources / Phone / Local / Streams / Apps and a Settings icon on a screen
whose screenshot was black). So structural and text checks are fine on this AVD; anything whose
evidence is a **picture** - round-corner clipping, cover art, plate colour, layout overlap -
cannot be settled here and needs the real Galaxy Watch 7. Restore overlays afterwards with
`i32 0` if you toggled them.

**2. `ping` fails while the network is fine.** `ping -c 2 8.8.8.8` reports 100% packet loss
because QEMU's user-mode stack does not forward ICMP. `dumpsys connectivity` on the same device
showed `IS_VALIDATED`, `INTERNET&...&VALIDATED`, DNS 10.0.2.3 and a default route via 10.0.2.2.
There is no `curl` on the device to test with.

**How to apply:** never conclude "no internet" on an AVD from ping. Read `dumpsys connectivity`
and look for `VALIDATED`. A subagent reported the ping failure as a hard blocker for the streams
ticket; it was not.

Related: [[avd-evidence-traps-width-and-logs]], [[wear-build-and-launch-gotchas]],
[[settings-screenshots-black-flag-secure]].
