---
name: index-wear
description: Second-level pointer list for Wear OS memories - watch build and launch traps, rotation, Data Layer identity, the locked watch, Play publishing. Open when the task touches the wear module or a watch device.
metadata:
  type: reference
---

# Wear OS - pointers

Split out of `MEMORY.md` (2026-08-18): the top-level index is billed on every turn of every session,
and these five are needed only when the work actually touches the watch. Open this file when building,
installing or driving the `wear` module, or when publishing the watch app.

- [Watch traffic goes through the phone](project_wear_traffic_proxied_through_phone.md) - by default, at ~4 KB/s over BT LE. "Own network" is a false claim whenever the phone is in range.
- [fw builds no APK](project_wear_build_and_launch_gotchas.md) - `a.ps1 fw` compiles but never packages; `adb.ps1 launch` starts the phone Activity. Both fail quietly, so you install a stale APK and see the old screen.
- [Watches DO auto-rotate](project_wear_auto_rotation_is_real.md) - measured; the square screen hides it and the app declares nothing.
- [Data Layer ids must match](project_wear_data_layer_applicationid_mismatch.md) - fixed in S1681; never re-suffix the watch `applicationId` or the phone and watch stop seeing each other.
- [Watch is permanently locked](project_wear_watch_bootloader_permanently_locked.md) - One UI 8 killed OEM unlock; no root, Shizuku or system-permission route exists on the owner's watch.
- [Play publishing gaps](project_wear_play_publishing_gaps.md) - own track, AAB not APK, and the on-watch password step fails WO-P6.
- [Watch text input IS drivable](project_wear_text_input_is_drivable.md) - tap the field itself, then `input text`; the T9 window hides the value, read it with uiautomator dump
- [The bezel IS drivable](project_wear_rotary_bezel_is_drivable.md) - `input rotaryencoder scroll --axis SCROLL,n` reaches Compose; sign inverted, 136 px per unit; `sendevent` refused by SELinux
- [Emulator: black shots, dead ping](project_wear_emulator_capture_and_network_traps.md) - both false blockers; uiautomator still sees content
