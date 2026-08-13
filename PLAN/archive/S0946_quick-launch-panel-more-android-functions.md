# S0946 - More Android OS-settings tiles in the quick-launch panel (Track A)

<!-- auto-approved by /spec-all - 2026-07-06 -->

**Ticket:** S0946
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-05
**Tier:** 3 - Moderate
**Source:** User request 2026-07-05 (`/spec-draft`)

> Approved - Track A scope (safe OS-settings catalog expansion) locked; owner example requests resolved to public settings tiles (quiz 2026-07-05); Track B toggles deferred. Ready for tactical decomposition.

## 0. Captured request

**Captured:** 2026-07-05

**Text:**

/spec-draft Расширить список анроид функций коттрые можно поставить в "окно быстрого запуска",кторая доступна жестом или из панели программ и сценариев. В частности меняя инресуют функции "Выключить debug", "включить wifi-расшанривание", включить реежим экономии", "закрепить портретный вид". Нужно произвести анализ что ещё можно включить в список

**Attachments:** none.

## 1. Current state

- The quick-launch panel already supports four tile sources:
  - Android app
  - FastMediaSorter feature
  - FastMediaSorter resource
  - Android OS settings
- The current FastMediaSorter feature catalog already includes 9 entries:
  - calculator
  - mini-game
  - OCR
  - streams
  - favorites
  - quick camera
  - quick voice
  - screen recording
  - link download
- The current Android OS settings catalog also already includes 9 entries:
  - Settings
  - Wi-Fi
  - Bluetooth
  - Display
  - Sound
  - Battery
  - Storage
  - App info
  - Date and time
- The current OS-settings path is curated and device-filtered:
  - only public or stable settings targets should be listed
  - each target is shown only if its intent resolves on the current device
- The current panel architecture launches screens/entry points.
  It does not yet model "change system state immediately with one tap" as a separate action class.

## 2. Problem

The owner wants a wider set of Android-level functions in the quick-launch panel, especially around:

- debug / developer-related actions
- Wi-Fi sharing / hotspot
- battery saver
- portrait lock

But these requests are not all the same type. They split into three different product/technical classes:

- open an Android settings screen
- toggle a system feature directly
- change a FastMediaSorter-only runtime setting

Right now the panel cleanly supports the first class, partly supports the second only for app-owned features, and does not have a dedicated abstraction for privileged/system toggles.

## 3. Initial analysis

### 3.1 Safe candidates that fit the current "Android OS settings" model

These are the strongest v1 candidates because they match the architecture introduced by S0663:
curated `Settings.ACTION_*` targets, filtered by `resolveActivity`.

- Developer options / application development settings
- Battery Saver settings
- Auto Rotate settings
- Accessibility settings
- Data usage settings
- Wireless settings
- NFC settings
- VPN settings
- Security settings
- Privacy settings
- Home / default-app settings

### 3.2 Assessment of the owner's example requests

1. **"Turn off debug"**
- Ambiguous.
- If this means **Android Developer options**, the low-risk interpretation is:
  - add **Open Developer options** as a new OS-settings tile
- If this means **disable debugging in one tap**, that is not the same as the current OS-settings model and needs separate feasibility work.
- If this means **FastMediaSorter debug tools/menu**, that is not an Android OS settings tile at all:
  - it would be a debug-only internal route
  - it makes sense only in debug builds

2. **"Enable Wi-Fi sharing"**
- This is the riskiest example in the request.
- Direct hotspot/tether enable is not a good first candidate for the current panel architecture.
- Safer fallback interpretations:
  - open hotspot/tethering settings if a stable public target exists on the device
  - otherwise open broader wireless settings
- This should not be promised as a universal one-tap action in v1.

3. **"Enable battery saver"**
- Safe interpretation:
  - add **Battery Saver settings**
- Separate interpretation:
  - direct one-tap battery-saver toggle
- The first fits the current model; the second needs a different action class and separate feasibility validation.

4. **"Lock portrait view"**
- This does not fit the current OS-settings catalog well.
- Current in-app rotation analysis:
  - FastMediaSorter already has an app-wide rotation policy
  - but it currently switches between **Follow OS** and **Sensor**
  - it does **not** provide a true **Lock Portrait** app-wide mode
- So "lock portrait" is better treated as a separate orientation feature request, not as just one more OS-settings shortcut.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0663 (created the OS-settings tile architecture this ticket extends), S0682 (relabelled/clarified the OS-settings picker), S0912 (adjacent FastMediaSorter-feature catalog expansion), S0439 (rotation-policy context for the deferred true portrait-lock).
- **Scope (this ticket = Track A only):** extend `OsShortcutCatalog` with eight more public `Settings.ACTION_*` tiles - Developer options, Battery saver, Auto-rotate, Accessibility, Wireless & networks, Data usage, NFC, VPN. Direct one-tap system toggles and a true system-wide portrait lock are Track B, out of scope here.
- **UI placement:** the new tiles appear in the existing OS-settings picker (`OsShortcutPickerDialogFragment`) alongside the current nine, using the same tile row (leading icon + label). No new screen or action class.
- **UI visibility:** each tile is shown only when its intent resolves on the device (existing `OsShortcutCatalog.available()` / `isResolvable()` `resolveActivity` filter); API-gated targets (auto-rotate API 31, data usage API 28, VPN API 24, battery saver API 22) self-hide on older devices, so a tile never leads nowhere.
- **UI fallback:** "enable Wi-Fi sharing" maps to the public **Wireless & networks** tile (`ACTION_WIRELESS_SETTINGS`) that houses tethering/hotspot - there is no stable public tethering deep-link, and direct hotspot enable stays Track B; "lock portrait" maps to the **Auto-rotate** tile as the Track A stand-in, with a true system-wide lock deferred to Track B (not settable via public API; relates S0439).
- **Behavior contract:** a tile launches the corresponding OS settings screen via its `Settings.ACTION_*` intent, identical to the current nine tiles; saved tiles persist by additive string key, so no data migration is needed.

### 3.4 Strong shortlist for a first expansion pass

If the goal is to expand the list quickly and safely, the best shortlist is:

- Developer options
- Battery Saver settings
- Auto Rotate settings
- Accessibility settings
- Wireless settings
- Data usage settings
- NFC settings
- VPN settings

### 3.5 Good follow-up candidates after the first pass

- Security settings
- Privacy settings
- Home settings
- Default apps settings

## 4. Proposed split of scope

This request likely needs to be split into two tracks rather than handled as one undifferentiated list-expansion task.

### Track A - safe catalog expansion (this ticket)

Expand the existing Android OS settings catalog with more public settings pages that behave like the current 9 entries. Confirmed additions from the owner's examples plus the §3.3 shortlist:

- Developer options (resolves "turn off debug")
- Auto Rotate settings (Track A stand-in for "lock portrait" until system-wide lock feasibility lands)
- Tethering / hotspot settings, wireless-settings fallback (resolves "enable Wi-Fi sharing")
- Battery Saver settings (resolves "enable battery saver")
- Accessibility, Data usage, NFC, VPN settings (§3.3 shortlist)

### Track B - direct-action / toggle requests

Handle these separately because they may require:

- privileged or restricted APIs
- vendor-specific intents
- a new panel action type beyond "open target screen"
- new FastMediaSorter settings/state models

This track includes the risky/ambiguous items from the request:

- direct hotspot enable
- direct battery-saver enable
- direct debug disable
- portrait lock

## 5. Open points (resolved 2026-07-05)

1. Open the Android screen or perform the action immediately?
   - **Resolved: both.** Track A (open-screen catalog tiles) ships in this ticket; Track B (one-tap toggles) is a separate later track.
2. In "turn off debug", what does **debug** mean?
   - **Resolved: open Android Developer options.** Add a `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` tile (Track A). Not the FMS debug menu, not a one-tap disable.
3. If direct hotspot enable is infeasible, is **Open hotspot/tethering settings** an acceptable fallback?
   - **Resolved: yes.** Prefer a stable public tethering target when it resolves, otherwise fall back to broader wireless settings (Track A). Direct enable stays out of v1.
4. What scope should "portrait lock" cover?
   - **Resolved: system-wide.** Owner wants a device-level portrait lock. Public API cannot set the system orientation directly (only the Auto Rotate toggle is exposed), so this is a Track B / follow-up feasibility item, not a Track A catalog tile. The pragmatic Track A stand-in is an **Auto Rotate settings** tile; a true system-wide lock needs separate feasibility work (relates S0439).

### Quiz decisions (2026-07-05)

- Open-screen vs one-tap toggle -> Both (Track A now, Track B later; keeps the catalog consistent - open-screen tiles first).
- Meaning of "debug" -> Open Developer options (trivial `Settings.ACTION_*` tile, fits the S0663 model).
- Hotspot fallback -> Open tethering settings, else wireless settings (direct enable is vendor-specific, deferred).
- Portrait-lock scope -> System-wide (not settable via public API; Auto Rotate settings tile as Track A stand-in, true lock deferred to Track B).

## 6. Rough recommendation

- Treat **OS settings pages** as the main expansion path for this ticket family.
- Treat **direct system toggles** as a separate product/technical family.
- Do not mix both into one implementation step, or the panel catalog becomes inconsistent:
  some items would merely open screens, while others would attempt privileged state changes.

## 10. Related

- S0663 - created the internal-route and Android-OS-settings architecture for the panel
- S0682 - relabelled and clarified the Android OS settings picker
- S0912 - expanded FastMediaSorter feature coverage to match Programs and Scenarios
- S0439 - adjacent rotation-policy context (important for any "lock portrait" follow-up)

## Implementation State (Track A - 2026-07-06)

- **Done.** Eight public `Settings.ACTION_*` tiles added to `OsShortcutCatalog` (`core/panel`): Developer options, Battery saver, Auto-rotate, Accessibility, Wireless networks, Data usage, NFC, VPN.
- Six new Material vector icons authored (`ic_developer_options`, `ic_screen_rotation`, `ic_accessibility`, `ic_wifi_tethering`, `ic_data_usage`, `ic_nfc`); Battery saver reuses `ic_battery`, VPN reuses `ic_lock`.
- EN/RU/UK labels added under the `app_launch_panel_os_*` key family (parity verified, 17 keys).
- Icon documentation tree regenerated: `docs/icons/icon-inventory.json`, per-icon SVGs, trilingual `ICON_LEGEND*`.
- No consumer change needed - the picker renders `OsShortcutCatalog.available()`, so new tiles self-register and self-hide via the existing `resolveActivity` filter.
- Track B (direct one-tap system toggles, true system-wide portrait lock) stays out of scope.

## Last Audit

**Date:** 2026-07-06
**Verdict:** Verified (static; P3 additive-catalog change, no new runtime logic)

- **Correctness.** All eight `Settings.ACTION_*` constants are valid public String constants and compile against compileSdk 35 (`.\a.ps1 fc` PASS). InlinedApi for auto-rotate (API 31), data usage (API 28) and VPN (API 24) is intended - the value is inlined so `available()` can probe it; suppressed with a WHY comment on the `targets` list.
- **Device-safety.** No new runtime logic: tiles reuse the production-verified `available()` / `isResolvable()` `resolveActivity` path shared by the original nine tiles, so an unresolvable target never renders. No device-only behaviour to confirm, hence Verified without a device.
- **Resource hygiene (Rule 20).** Every new icon and string is referenced by the catalog; no orphans. Icon inventory / SVG / legend gates pass (`assert-icon-inventory-sync.ps1 -Gate -IncludeExportTest` PASS).
- **Gates.** detekt scoped PASS (no new findings among changed files), neuroslop PASS, string parity PASS, ticket-log PASS.
- **Residual.** Track B toggles and true system-wide portrait lock remain a separate follow-up (relates S0439).
