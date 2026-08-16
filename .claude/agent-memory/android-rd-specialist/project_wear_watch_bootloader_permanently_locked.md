---
name: wear-watch-bootloader-permanently-locked
description: The owner's Galaxy Watch 7 shipped on One UI 8 / Android 16, so its bootloader can never be unlocked - no root, no custom ROM, no system-privileged workaround is available on that device
metadata:
  type: project
---

Measured on the attached watch 2026-08-16 via `getprop`:

- `ro.product.model=SM-L310`, `ro.build.version.oneui=80000` (One UI 8), `ro.build.version.release=16`
- `ro.build.display.id=L310XXS2BZF4`, built 2026-06-24; CSC `SIO` (Slovenia), `ro.omc.multi_csc=OXM`
- `ro.boot.flash.locked=1`, `ro.boot.verifiedbootstate=green`, and **no `oem_unlock` property exists at all**

Samsung removed bootloader unlocking in One UI 8 - the unlock logic is absent from the bootloader itself, so it cannot be brute-forced or restored by flashing. A device that shipped on One UI 8 stays locked.

**Why:** it closes an entire class of proposals before they are researched. Any answer of the shape "grant the watch app shell/system privileges", "adb-persist a WindowManager override", "use Shizuku/root on the watch" is dead on this hardware, and reflashing a different firmware does not revive it. The owner asked on 2026-08-16 whether reflashing a freshly bought watch was worth it; the answer was no.

**How to apply:**

- Never propose root, Shizuku, a signature-level permission, or a custom ROM as a route on the owner's watch. The only privileged channel is an `adb shell` command re-issued after every reboot.
- A wear-side feature must be implementable with ordinary app permissions, or it is not implementable. Related: [[wear-auto-rotation-is-real]].
- Reflashing is only ever worth discussing for a CSC/region change (regional feature availability), never for privileges.
