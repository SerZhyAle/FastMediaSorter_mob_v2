# Launcher Mode - Roadmap

**Owner:** Serhii
**Created:** 2026-07-18
**Scope:** Sequencing plan for the launcher-mode epic (S0404) and all its follow-on tickets.
**Status source of truth:** `PLAN/spec-catalog.jsonl` (via `scripts/spec_catalog/select.ps1`). The statuses below are a 2026-07-18 snapshot - always re-check live before acting.

---

## Where the launcher stands (2026-07-18)

- **S0404** (epic, iteration-1) - **Verified 2026-07-18**. Owner device pass passed (Wave 0), incl. the vendor Home-role/reboot gate (§6.14). All 10 tactical phases done (07.6/08.7 walkthroughs confirmed); `S0404:` probes removed; Phase 09 (user docs) extracted to S1102.
- The Windows-desktop model shipped: HOME surface, 2D cell grid (ADR-9), four gadgets (clock, playlist, streams, folder preview), taskbar + Start menu + recents + tray, per-profile seeding, independent portrait/landscape, guaranteed exit. Flavors: `standard` + `noLegal`.
- All refinement tickets below came out of the owner's on-device testing on 2026-07-18.

**Everything downstream gates on the S0404 device pass** - in particular the vendor gate (strategic §6 item 14): does the real target device keep the Home role across reboot? If a target device refuses, some refinements may be re-scoped.

---

## Ticket inventory (2026-07-18 snapshot)

| Wave | Ticket | Name | Status | Prio |
|:----:|--------|------|--------|:---:|
| 0 | S0404 | android-launcher-mode-profiles (epic) | **Verified** | 50 |
| 1 | S1096 | bugfix-launcher-edit-mode-taps | **BlockNeedUserTest** | 90 |
| 1 | S1097 | bugfix-launcher-recent-apps-taskbar | **BlockNeedUserTest** | 90 |
| 2 | S1090 | launcher-desktop-longpress-lock | Draft | 50 |
| 2 | S1100 | launcher-hide-tile-borders | Draft | 50 |
| 2 | S1091 | launcher-default-profile-shortcuts | Draft | 50 |
| 2 | S1101 | launcher-desktop-wallpaper-options | Draft | 50 |
| 2 | S1093 | launcher-widget-resize-to-max | Draft | 50 |
| 2 | S1094 | launcher-clock-widget-functional | Draft | 50 |
| 2 | S1089 | launcher-apps-scrollable-grid | Draft | 50 |
| 2 | S1095 | launcher-app-picker-layout | Draft | 50 |
| 2 | S1092 | launcher-empty-channel-picker | Draft | 50 |
| 2 | S1088 | system-launcher-settings-dialog | Draft | 50 |
| 2 | S1104 | launcher-welcome-toggle-layout | Draft | 50 |
| 3 | S1087 | system-status-area-replace-option | Draft | 50 |
| 3 | S1098 | launcher-status-indicators-clickable | Draft | 50 |
| 3 | S1099 | launcher-keep-nav-bar | Draft | 50 |
| 4 | S1103 | launcher-cell-actions-and-app-shortcuts | Draft | 50 |
| 5 | S0426 | home-screen-weather-block | BlockByOtherTask | 40 |
| 5 | S0427 | third-party-app-shortcuts | BlockByOtherTask | 40 |
| 5 | S0428 | home-screen-call-sms | BlockByOtherTask | 40 |
| 5 | S0429 | home-screen-google-content | BlockByOtherTask | 40 |
| 6 | S1102 | launcher-mode-user-docs | Draft | 45 |

---

## Wave 0 - Confirm the baseline (blocking gate)

- **S0404 device pass** on the real target device: enable via Settings + Welcome, Always/Just-once chooser, seeded desktop per profile, assemble/edit cells (every shortcut kind + all four gadgets), taskbar + Start menu, rotation independence, exit, and **reboot-into-desktop (vendor gate §6.14)**.
- Nothing else is worth polishing until the core is confirmed on real hardware. On success: `/spec-check S0404` -> Verified (removes the `S0404:` probes).

## Wave 1 - Bugfixes first (priority 90)

Defects in shipped iteration-1 behaviour - fix before any polish. **Both fixed 2026-07-18, awaiting device test (BlockNeedUserTest).**

- **S1096** - in edit mode, tapping a cell still fires its action (tap the clock -> alarm launches), so cells cannot be dragged. Suppress cell activation while editing. Fix: transparent edit-scrim over each cell in `LauncherCellViewBinder.decorateForEdit` intercepts every touch (swallows the tap, carries the drag long-press), covering both shortcut clicks and a gadget's own internal touches uniformly.
- **S1097** - recent apps do not accumulate in the taskbar after the Start button. Root cause was product, not code: recents showed only third-party `app:` launches while the seeded desktop launches mostly internal targets. Owner decision 2026-07-18: recents = ALL launched commands. Fix: `recentApps` -> `recentCommands` (every kind, deduped), resolved via the shared `ResolveLauncherCommandLabelUseCase`, unavailable targets dropped; recents tap decodes and reruns the exact command.

## Wave 2 - Core UX polish

The bulk of on-device findings. Grouped by area; roughly independent, can be parallelised, but keep each area internally ordered.

- **Desktop surface & editing**
  - S1090 - long-press empty space enters edit mode; add a "lock desktop" toggle that disables it.
  - S1100 - draw cell/widget borders only in edit mode (cleaner surface otherwise).
  - S1091 - seed ~12-15 useful shortcuts into the default profile (All files / All music / All images / All documents per device profile); rename desktop "Settings" -> "Android settings".
  - S1101 - desktop wallpaper options: image/GIF (copied in, aspect-fill with crop), "Empty" (current), and a branded default animation (stripes + balls, as on the site / player splash - needs locating).
  - S1093 - let widgets (e.g. Channels) be resized on add up to full width and full height.
- **App menu & pickers**
  - S1089 - show apps in the launcher menu with labels in a scrollable grid, not a short single row.
  - S1095 - app picker dialog: taller sheet, readable title, two columns even in portrait.
- **Gadgets**
  - S1094 - make the clock gadget functional (tap -> alarms, optional calendar, huge digits with seconds) instead of decorative.
  - S1092 - when adding a "channel" cell with no channels present, guide the user to enable streams first instead of an empty picker.
- **Settings & activation**
  - S1088 - extract launcher settings into a dedicated "System launcher settings" dialog; keep an enable toggle + a button to open it in General UI settings; expose Android/app/launcher settings entry points from the launcher.
  - S1104 - the Welcome "Use as home screen" toggle must match every other toggle in the app: switch on the left, caption immediately to its right (not switch flung to the far edge).

## Wave 3 - System-bar strategy (decide together)

These three are about how far the launcher replaces system chrome. They interact - resolve the policy once.

- **S1087** - option "Replace the system notification/status area"; when off, do not duplicate clock/battery/signal in our own bar.
- **S1098** - if we do replace the system status area, our clock/battery/Wi-Fi/GSM indicators must be tappable.
- **S1099** - do NOT overlay the bottom system navigation bar (back/home/recents) - owner sees no value in covering it.

## Wave 4 - Feature expansion

- **S1103** - surface all internal features (quick-access panel, gestures, programs/scenarios, scheduled operations) as user-assignable desktop cells (except screenshot); let the panel itself launch from a cell. Part two (recognise third-party app launch options like "add calendar entry" and offer them when picking an app) **overlaps S0427** - lean on it, do not duplicate. Short research needed.

## Wave 5 - Sibling research-specs (re-evaluate the block)

Extracted from the original S0404 research; all `BlockByOtherTask` on S0404's home-surface/gadget contract. **That contract now exists** (S0404 phases 04/05B/06 done), so the first blocker premise is stale - re-assess unblocking each once S0404 is Verified.

- **S0426** - home-screen weather block (feeds the clock gadget).
- **S0427** - third-party app shortcuts (see S1103 overlap).
- **S0428** - home-screen call / SMS.
- **S0429** - home-screen Google content (owner-gated on external OAuth/CASA cost).

## Wave 6 - User documentation (gated last)

- **S1102** - HOW_TO + FAQ chapter (EN/RU/UK), site build. Deliberately written on the **stabilised** UX, i.e. after Waves 1-4 land and the owner signs off. Was S0404 Phase 09.

---

## Recommended order

1. Wave 0 (device pass) - unblocks everything.
2. Wave 1 bugfixes (S1096, S1097).
3. Wave 3 system-bar policy decision (S1087/S1098/S1099) early, because it constrains the tray/taskbar work touched by several Wave-2 tickets.
4. Wave 2 polish, area by area.
5. Wave 4 (S1103) + re-scope Wave 5 siblings against the now-existing contract.
6. Wave 6 docs (S1102) once UX is frozen; then `/skill-release` picks up the ALL_FEATURES entry for the public showcase.
