# Device Fleet

Authoritative roster of the physical devices, emulators and the workstation an agent may act on in this
repository, and of what each one permits. **Authorization is per device, never per "a device is attached"** -
the same command is routine on one phone and forbidden on the next, so read the identity first and this file
second, before the first `adb` call of a task.

Connection mechanics (adb discovery, the watch's wireless pairing, leases) are not repeated here: they live in
`docs/DEV_OPS.md` and in `scripts/devtest/adb.ps1`, `device-ready.ps1`, `device-lease.ps1`. This file answers
only "which device is this, and what am I allowed to do to it".

## The identity rule

1. Run `pwsh -NoProfile -File scripts/devtest/adb.ps1 devices` and match the serial or service name against the
   roster below. A serial that is in no row is an **unknown device**: treat it as a foreign phone (read-only,
   nothing destructive, ask the owner) until it is added here.
2. Re-check after any reconnect. Samsung devices blip offline mid-drive and a reconnect can hand you a different
   entry in the list; acting on a remembered `-s` value is how a personal phone gets a test phone's treatment.
3. The permissions below are the whole grant. Anything not listed as allowed on a device is not allowed there,
   even when it is routine on another device in the same room.

## Roster

### Galaxy S21+ - the test phone

- Identity: `SM-G996U1`, adb serial `RFCR110NBQJ`, Android 15 (SDK 35), 1080x2400 @ 450dpi (tall aspect 2.22).
- Role: dedicated test hardware. It runs under the owner's Google account but carries **no personal data, no
  personal settings and no content he needs** - it exists to be experimented on.
- Allowed: everything. Install and uninstall any flavor, `pm clear`, wipe app data, grant any permission
  including All-Files-Access, draw-over-apps and system roles, change device system settings, screen capture.
- Not a pair target: it is **not** paired with the Galaxy Watch 7, so no phone-to-watch acceptance can run here.
  A watch bond printed by `dumpsys bluetooth_manager` on this phone is a dated log line, not a live pairing.

### Galaxy S25 FE - the owner's personal phone

- Identity: `SM-S731B`, adb serial `R5CY9070WNB`, Android 16 (SDK 36).
- Role: the owner's own phone, and deliberately a two-build device. It carries **the current `noLegal` debug
  build with his real private data, settings, sources and resources** - that content is the point of the device,
  not a leftover - and, side by side, **the STANDARD build installed from the store**, which he keeps in order to
  watch what real users receive on update.
- It is the phone the Galaxy Watch 7 is paired with, so every phone-plus-watch ticket runs here, under these limits.
- Allowed:
  - Build and install a fresh `noLegal` debug **over the top** of the existing one (same package, data preserved) -
    `.\a.ps1 nd` then `ivn`, or `adb.ps1 install` with that artifact.
  - Grant runtime permissions to the debug package with `pm grant` (media, notifications and the like).
  - Read freely: logcat, screenshots, `uidump`, `dumpsys`, and driving the debug app's UI with taps and swipes.
- Forbidden:
  - `pm clear` or any other wipe of app data - the private content is irreplaceable.
  - `uninstall` of either package.
  - System roles, default-app changes, and device-level system settings changes (see the standing rule on not
    granting system roles on the owner's phone).
  - Touching the store STANDARD build beyond reading its version (`dumpsys package com.sza.fastmediasorter`).
    It is an update tracker, not a test target: replacing or clearing it destroys the comparison it exists for.

### Galaxy Watch 7 - the development watch

- Identity: `SM-L310`, stable serial part `RFGL1148CRZ`, Wear OS on Android 16 (SDK 36), round 480x480 @ 340dpi.
  Reached only over wireless debugging - no cable exists - and the adb service token is not stable, so discover
  it every session instead of reusing a literal.
- Role: development target. The watch app is under active build-out, so at this stage the whole device is fair game.
- Paired with the **S25 FE**, not with the S21+.
- Allowed: everything. Install, uninstall and `pm clear` the watch debug package; change watch system settings
  freely; reboot freely. The store STANDARD build on the watch carries no protection either.
- Known cost, not a prohibition: a reboot ends the adb session. Wear OS does not restore wireless debugging by
  itself, so getting back in needs the owner physically on the watch. Do the rest of the on-device work first and
  write the measurements to a file before sending a reboot.
- Hardware fact that settles specs: the watch has a microphone and **no camera**. Video capture on the watch is
  impossible on this target - cut it at the spec, do not research it.
- Future shape (not yet true): the watch will hold the `noLegal` **debug** build alongside the store STANDARD one,
  mirroring the phone. When that lands, the watch debug artifact to install is the `noLegal` flavor.

### Galaxy S20 FE - rare guest phone

- Identity: `SM-G781B`, adb serial `RFCRA133MXB`, Android 13 (SDK 33), 1080x2400.
- Role: a working phone of the owner, attached occasionally for one specific ticket. Not part of the standing
  fleet and usually absent.
- Allowed: read-only probing - logcat, screenshots, `uidump`, driving the app already installed.
- Forbidden: installs the owner did not ask for, `pm clear`, uninstall, system roles, default-app changes, any
  system settings change. Restore anything touched, and ask before going further.

### Emulators - free hand

- Role: the default target for anything that does not need real hardware.
- Allowed: everything, without asking. Create and delete AVDs, download system images and SDK components, run any
  form factor - phone, tablet, foldable, TV, watch - and install any flavor on them.
- One limit that is not about permission: a **Wear AVD is not paired with anything**
  (`settings get secure clockwork_paired_device_bt_address` returns `null`), so no phone-to-watch acceptance can
  run on it. Read that setting before accepting a wear emulator as the target; watch-standalone work is fine there.

### This workstation - development and personal at once

- Role: the owner's personal machine as well as the build machine.
- Allowed: everything the project needs inside the repository and `temp/`, plus installing SDK components,
  system images and AVDs without asking.
- Forbidden: shutting down or rebooting the machine, and killing processes that are not yours. Gradle daemons,
  adb and emulators this session started are yours; the owner's browsers, editors, downloads and windows are not.

## Default target order

When a ticket needs "some device" and several are online, take them in this order:

1. **Emulator** - nothing on it is precious, and it is replaceable.
2. **Galaxy S21+** - when real hardware matters: a true tall aspect, real storage moves and deletes, a
   real-device permission or overlay behaviour, or an emulator-suspect defect.
3. **Galaxy S25 FE** - only when the ticket genuinely needs it: the owner's real data set, or the live pair with
   the Galaxy Watch 7. Everything done there stays inside the allowed list above.

Deviating from that order is fine when the ticket says why; deviating silently is not.

## Which build goes on which device

- S25 FE: `noLegal` debug (`.\a.ps1 nd` / `ivn`), installed over the existing one. Never `standard` - the store
  build already occupies that role on this phone.
- S21+: whatever the ticket needs; no build on it is precious.
- Galaxy Watch 7: today whatever the ticket needs; once the future shape above lands, `noLegal` debug.
- Emulators: whatever the ticket needs.
- The `wear` module has both `standard` and `noLegal` flavors, so a watch artifact always carries a flavor choice -
  name it explicitly rather than assuming there is only one.

## Maintenance

This file is the authority. When a device joins or leaves, changes hands, or its permissions change, edit **here**
and let the rules and skills keep pointing at it - never restate a device permission inside a command, a skill or
an agent definition, because a restatement goes stale silently and the copy that is read is the wrong one.
