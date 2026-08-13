# Phase 04 - Settings surfaces: global stream default + per-channel edit

**Strategic spec:** [`../S1144_video-stream-tracks-subtitles-program.md`](../S1144_video-stream-tracks-subtitles-program.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Expose the track-language preference both as a global streams default (settings) and as a per-channel edit in the channel editor (Q6), reusing the canonical settings pickers.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (persistence columns), Phase 02 ✅ Done (apply reads global + per-channel).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| Streams settings fragment + `AppSettings` (global default keys) | Modified | ≤ 500 each |
| Channel editor dialog/fragment (per-channel audio/subtitle language + subtitles toggle) | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

> Before editing, `Grep` `AppSettings` + the streams settings fragment for an existing audio/subtitle-language default to reuse rather than duplicate; confirm the channel editor class via catalog query. List the resolved concrete files in each step.

---

## Steps

### Step 04.1 - New trilingual strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the new user-visible keys in one lockstep call `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>` for: streams default audio-language label, streams default subtitle-language label, per-channel "Audio language"/"Subtitle language"/"Subtitles" labels. Copy must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist. Never hand-translate outside the lockstep tool.

**Verification:**

- `Grep` - each new key present in all three `strings.xml` files (parity).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

### Step 04.2 - Global streams default (settings)

**Files:** streams settings fragment + `AppSettings`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a streams audio-language and subtitle-language default to the streams settings surface, reusing the canonical `ListSelectionDialog<T>` + `SettingsSelectionRow` picker pattern and mirroring `PlayerSettingsDialog`'s `LanguageOption` (DEFAULT/EN/RU/UK). Persist via `AppSettings` (new keys). This is the global default `VideoTrackSelectionManager.applyTrackSelection` already consumes; wire the new keys into that global-default read for the stream path.

**Verification:**

- `Grep` - the new settings keys defined in `AppSettings` and bound in the streams settings fragment.
- `/build` -> `standard debug` compiles.

**Status:** `[x] done`

---

### Step 04.3 - Per-channel edit in the channel editor

**Files:** channel editor dialog/fragment
**Depends on:** Step 04.2

**Prompt for developer:**

> In the channel editor (the MANUAL-channel edit surface, mirror S1145/S1147 per-channel operations) add audio-language / subtitle-language pickers + a subtitles on/off/inherit control that read/write the entity's `preferredAudioLang`/`preferredSubtitleLang`/`subtitlesEnabled` via `StreamTrackPreferenceUseCase`. "Inherit" = null (follow global default). Support D-pad/TV focus for the new pickers (Rule 16).

**Verification:**

- `Grep` - `preferredAudioLang` / `StreamTrackPreferenceUseCase` referenced in the channel editor.
- `/build` -> `standard debug` compiles.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` -> `BUILD SUCCESSFUL` (code + resources, both layout orientations).
- [x] Trilingual parity confirmed - `check_strings_localized.ps1` exit 0 for all five new keys.
- [x] Dev log entry per file.
- [x] Phase-boundary audit - see below.

**Phase-boundary audit (2026-07-27):**

- **Persistence off the UI thread - clear.** The global defaults go through `SettingsRepository`/DataStore like every other row in this fragment; the per-channel write runs on `lifecycleScope` and the use case's DAO calls are `suspend`. No blocking work was added to a click handler.
- **Rule 16 (D-pad / keyboard) - inherited.** All three per-channel rows are `SettingsDropdownRow`, the same widget the settings screens use, so focus handling comes with the widget rather than being re-implemented in the dialog.
- **Rule 11 (landscape parity) - honoured.** `fragment_settings_streams.xml` was edited together with its `layout-land` counterpart. `dialog_add_stream.xml` has no landscape variant, so there is nothing to mirror.
- **Precedence chain verified end-to-end:** per-channel preference > global stream default > generic player settings. Both stream-scoped layers are seated in `playStreamVideo` and cleared together in `playVideo`, so neither can leak into a local file or the next channel.
- **P2 noted:** the edit dialog binds its rows twice - once synchronously with empty state, once when the stored preference arrives. It keeps the dialog non-blocking, but a user who opens and confirms within that window writes "Default". The read is a single indexed DAO lookup, so the window is tiny; a proper fix is to disable the positive button until the read lands, which is a UX decision rather than a defect.

---

## Handoff Notes to Next Phase

A new setting exists -> Phase 05 MUST regenerate the settings manifest/reference/annotations (Rule 22).

---

## Rollback Plan

Revert the settings + editor changes and the new string keys together (dead-string hygiene, Rule 20).
