# Phase 01 — Settings Foundation

**Strategic spec:** [`../S0050_player-black-screen-mode.md`](../S0050_player-black-screen-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 5 / 5
**Completed:** 2026-05-02
**Started:** —
**Completed:** —

---

## Objective

Add `showBlackScreenButton: Boolean = false` to `AppSettings`, expose it through `SettingsViewModel`, add a toggle row in the Behaviour section of Playback Settings, and provide trilingual string resources.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `domain/model/AppSettings.kt` read and understood (data class fields).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/values/strings.xml` | Modified | existing file |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | existing file |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | existing file |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | existing layout |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 600 |

---

## Steps

### Step 1.1 — Add field to AppSettings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `val showBlackScreenButton: Boolean = false` to the `AppSettings` data class, in the Playback/Behaviour group of fields. Default must be `false`.

**Verification:**

- `Grep` — `showBlackScreenButton` matches in `AppSettings.kt`.
- `Grep` — `= false` appears on the same line as `showBlackScreenButton`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: domain/model/AppSettings.kt (+3 LOC). Dev log recorded.

---

### Step 1.2 — Add string resources (EN)

**Files:** `app_v2/src/main/res/values/strings.xml`
**Depends on:** Step 1.1

**Prompt for developer:**

> Add two string entries to `values/strings.xml`:
> - `settings_black_screen_button_title` → `"Show \"Black Screen\" button"`
> - `settings_black_screen_button_desc` → `"Display a button in the player toolbar to blank the screen while audio or video continues playing"`

**Verification:**

- `Grep` — `settings_black_screen_button_title` in `values/strings.xml`.
- `Grep` — `settings_black_screen_button_desc` in `values/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: values/strings.xml (+4 LOC). Dev log recorded.

---

### Step 1.3 — Add string resources (RU + UK)

**Files:** `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 1.2

**Prompt for developer:**

> Add the same two string keys to both locale files:
>
> **RU:**
> - `settings_black_screen_button_title` → `"Показывать кнопку «Чёрный экран»"`
> - `settings_black_screen_button_desc` → `"Отображать кнопку в панели плеера для включения чёрного экрана без остановки воспроизведения"`
>
> **UK:**
> - `settings_black_screen_button_title` → `"Показувати кнопку «Чорний екран»"`
> - `settings_black_screen_button_desc` → `"Відображати кнопку на панелі плеєра для вмикання чорного екрана без зупинки відтворення"`

**Verification:**

- `Grep` — `settings_black_screen_button_title` in `values-ru/strings.xml`.
- `Grep` — `settings_black_screen_button_title` in `values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: values-ru/strings.xml (+4 LOC), values-uk/strings.xml (+4 LOC). Dev log recorded.

---

### Step 1.4 — Add switch row to Behaviour section layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml`
**Depends on:** Step 1.3

**Prompt for developer:**

> Inside `@+id/containerBehaviour`, append a new toggle row following Architecture Pattern A (SwitchMaterial + title TextView + description TextView) immediately before the closing tag of the container. Use:
> - Switch id: `@+id/switchShowBlackScreenButton`
> - Title text: `@string/settings_black_screen_button_title`
> - Description text: `@string/settings_black_screen_button_desc`
> Match the exact XML pattern of existing rows in the same container (same dimensions, margins, text sizes from `@dimen/toggler_title_text_size` and `@dimen/toggler_desc_text_size`).

**Verification:**

- `Grep` — `switchShowBlackScreenButton` in `fragment_settings_playback.xml`.
- `Grep` — `settings_black_screen_button_title` in `fragment_settings_playback.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: layout/fragment_settings_playback.xml (+14 LOC). Dev log recorded.

---

### Step 1.5 — Bind switch in PlaybackSettingsFragment and SettingsViewModel

**Files:** `ui/settings/fragments/PlaybackSettingsFragment.kt`, `ui/settings/SettingsViewModel.kt`
**Depends on:** Step 1.4

**Prompt for developer:**

> In `PlaybackSettingsFragment`:
> 1. In `setupViews()` (or equivalent initialisation block), add binding for `binding.switchShowBlackScreenButton`:
>    - Set initial checked state from `viewModel.settings.value.showBlackScreenButton`.
>    - On `setOnCheckedChangeListener`: call `viewModel.updateSettings(current.copy(showBlackScreenButton = isChecked))`, guarded by the existing `isUpdatingFromSettings` flag.
> 2. In the `observeData()` settings-observer block, sync `binding.switchShowBlackScreenButton.isChecked` from the latest `AppSettings.showBlackScreenButton` (under the `isUpdatingFromSettings = true` guard).
>
> In `SettingsViewModel`:
> 1. In `resetPlaybackSection()` (or equivalent reset), add `showBlackScreenButton = false` to the reset copy.

**Verification:**

- `Grep` — `switchShowBlackScreenButton` in `PlaybackSettingsFragment.kt`.
- `Grep` — `showBlackScreenButton` in `PlaybackSettingsFragment.kt`.
- `Grep` — `showBlackScreenButton` in `SettingsViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: PlaybackSettingsFragment.kt (+9 LOC), SettingsViewModel.kt (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 1.* above is `[x] done`.
- [x] Project compiles — run `/build`. (auto-build — PASS)
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `AppSettings.showBlackScreenButton` field exists with default `false`.
- Switch in Behaviour section persists the value via `SettingsViewModel`.
- All three locales have `settings_black_screen_button_title` and `settings_black_screen_button_desc`.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed yet.
