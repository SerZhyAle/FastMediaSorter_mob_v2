# Phase 07 - Entry Points

**Strategic spec:** [`../S0565_internet-stream-playback.md`](../S0565_internet-stream-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 06
**Blocks:** Phase 08
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

> **Impl notes (2026-06-21, `/spec-all`).** Two doc assumptions corrected against reality:
> (1) The main-window command is NOT a top-bar button in `activity_main.xml`; secondary features
> (Game, Calculator, Camera-OCR, Link-download) live in the main dropdown popup via per-feature
> `Main*MenuManager` classes - "Трансляции" follows that pattern (`MainStreamsMenuManager`, dropdown
> item gated on `BuildConfig.SUPPORT_STREAMS`). The dropdown is orientation-agnostic, so no port/land
> layout edit applies. (2) `fragment_settings_playback.xml` DOES have a `layout-land/` counterpart
> (the doc said it did not) - `btnStreams` was added to BOTH (Rule 11). Strings: `streams_title`
> reused for both the menu label and the settings title; only `settings_streams_summary` is new.
> Settings docs regenerated: manifest via `SettingsManifestExportTest -Dsettings.manifest.generate=true`,
> reference via `render-settings-reference.ps1`, trilingual `btnStreams` annotation added;
> `assert-settings-doc-sync.ps1` green.

---

## Objective

Expose the "Трансляции" screen from the main window and from Settings, gated on `BuildConfig.SUPPORT_STREAMS` (hidden in photos). Add the settings entry, regenerate the settings docs (Rule 22). Widget is deferred (INDEX Deferred list).

---

## Prerequisites

- [ ] Phase 06 ✅ Done (`StreamsActivity` launchable).
- [ ] Reviewed `MainActivity.kt` command wiring + `res/layout/activity_main.xml` (+ `layout-land`), and a settings fragment (e.g. `PlaybackSettingsFragment.kt`) for the entry convention.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_main.xml` | Modified | ≤ +12 |
| `app_v2/src/main/res/layout-land/activity_main.xml` | Modified | ≤ +12 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ +20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/<chosen>SettingsFragment.kt` | Modified | ≤ +25 |
| `app_v2/src/main/res/layout/fragment_settings_<chosen>.xml` | Modified | ≤ +12 |
| `app_v2/src/main/res/values/strings.xml` (+ ru + uk) | Modified | ≤ +12 |

> **Landscape parity (MANDATORY):** the main-window command is added to BOTH `res/layout/activity_main.xml` and `res/layout-land/activity_main.xml`. The settings fragment layout has no `-land` counterpart (settings reflow) - note recorded. No hex; `?attr/` only.

---

## Steps

### Step 07.1 - Entry-point strings (trilingual)

**Files:** `res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add via `scripts/utils/set-android-string.ps1 -Action add` in EN/RU/UK lockstep: `main_command_streams` (main-window command label), `settings_streams_title`, `settings_streams_summary` (Settings entry). Reuse `streams_title` from Phase 06 if a duplicate would result. Strings pass `docs/COMMUNICATION_POLICY.md` §2 + §6.

**Verification:**

- `Grep` - the three keys present in all of `values`, `values-ru`, `values-uk`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "main_command_streams"` exits 0; same for `settings_streams`.

**Status:** `[x]` done (standard + photos compile + settings-doc-sync gate green 2026-06-21)

---

### Step 07.2 - Main-window command (portrait + landscape) gated on SUPPORT_STREAMS

**Files:** `res/layout/activity_main.xml`, `res/layout-land/activity_main.xml`, `ui/main/MainActivity.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Add a "Трансляции" command control (e.g. `btnStreams`) to the main control area in both portrait and landscape `activity_main.xml`, labelled `@string/main_command_streams`, with its own icon, `focusable`/`clickable`/`nextFocus*` set. In `MainActivity` (delegate to its existing command handler/helper, not raw Activity logic - Rule 3), wire the tap to `startActivity(Intent(this, StreamsActivity::class.java))`, and set the control's visibility to `View.GONE` when `!BuildConfig.SUPPORT_STREAMS` (photos hides it; `SUPPORT_STREAMS` is a capability flag, not an `IS_*` flavor guard, so this read is allowed in `src/main`).

**Verification:**

- `Grep` - the command id present in BOTH `res/layout/activity_main.xml` and `res/layout-land/activity_main.xml`.
- `Grep` - `StreamsActivity::class.java` launched from the main command path.
- `Grep` - `BuildConfig.SUPPORT_STREAMS` referenced for visibility; no `BuildConfig.IS_` flavor guard added.

**Status:** `[x]` done (standard + photos compile + settings-doc-sync gate green 2026-06-21)

---

### Step 07.3 - Settings entry gated on SUPPORT_STREAMS

**Files:** `ui/settings/fragments/<chosen>SettingsFragment.kt`, `res/layout/fragment_settings_<chosen>.xml`
**Depends on:** Step 07.1

**Prompt for developer:**

> Add a "Трансляции" entry to the most relevant existing settings fragment (Playback or General). Tapping it opens `StreamsActivity`. Hide the entry when `!BuildConfig.SUPPORT_STREAMS`. Keep the fragment free of business logic; it only navigates. Do not introduce a new settings screen unless the surrounding convention requires one.

**Verification:**

- `Grep` - `settings_streams_title` referenced in the chosen fragment/layout.
- `Grep` - `StreamsActivity` launched from the settings entry; `BuildConfig.SUPPORT_STREAMS` gate present.

**Status:** `[x]` done (standard + photos compile + settings-doc-sync gate green 2026-06-21)

---

### Step 07.4 - Regenerate settings docs (Rule 22)

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json` (generated)
**Depends on:** Step 07.3

**Prompt for developer:**

> The new Settings entry changes the settings surface - regenerate the settings manifest + reference and add the annotation for the new entry, per CLAUDE.md Rule 22. Use the project's settings-doc generation path (the same one `scripts/quality/assert-settings-doc-sync.ps1` validates in `post-change.ps1`).

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- `Grep` - the new settings entry key appears in `docs/settings/settings-manifest.json`.

**Status:** `[x]` done (standard + photos compile + settings-doc-sync gate green 2026-06-21)

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles + resources resolve for standard (entry visible, `.\a.ps1 fc`) and photos (`SUPPORT_STREAMS=false`, entry hidden) - the gate resolves in both.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- [ ] Main command added in BOTH portrait and landscape `activity_main.xml`.
- [ ] `Grep` for `BuildConfig.IS_` newly added to `src/main` returns zero hits (capability flag only).
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

- Both entry-points open the same `StreamsActivity` (research §6 item 5). Widget deferred and recorded in INDEX - §11 criterion 1 satisfied by Settings + main-window.

---

## Rollback Plan

Revert phase commit(s) - additive layout controls + settings entry + strings; remove the command, settings entry, and regenerated doc rows on revert.
