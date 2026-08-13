# Phase 06 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0577_background-audio-playback-group.md`](../S0577_background-audio-playback-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** -
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Regenerate the settings documentation pipeline for the moved/renamed settings, record the delivered capability, and sync the class catalog and dev log. No app behavior changes.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done; project compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE.md` / `_RU.md` / `_UK.md` | Modified (generated) | n/a |
| `docs/settings/settings-annotations.json` | Modified | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |

---

## Steps

### Step 06.1 - Author the annotation for the new section header

**Files:** `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an annotation entry for the new `CollapsibleSectionHeader` (key `headerBackgroundAudio`) with `{en, ru, uk}` descriptions covering "background audio playback group on the Player tab, including the stream behavior". Reword the existing entries for `rowEnablePersistentAudioPlayback` and `rowShowNowPlayingPanel` if their description still implies the Media/Audio location. Keep entries keyed by view-id (sectionId is not part of the annotation key).

**Verification:**

- `Grep` - `headerBackgroundAudio` present in `settings-annotations.json` with non-empty en/ru/uk.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 1/1 PASS. headerBackgroundAudio annotation added (en/ru/uk); moved rows tab-neutral, no reword needed.

---

### Step 06.2 - Regenerate the settings manifest

**Files:** `docs/settings/settings-manifest.json`
**Depends on:** Step 06.1

**Prompt for developer:**

> Regenerate the manifest from the live scanner: `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests *SettingsManifestExportTest -Dsettings.manifest.generate=true`. This re-sorts entries by sectionId then key; `rowEnablePersistentAudioPlayback` and `rowShowNowPlayingPanel` move from `sectionId=audio` to `sectionId=playback`, and `headerBackgroundAudio` appears as a new SECTION_HEADER entry with the new title.

**Verification:**

- `Grep` - `headerBackgroundAudio` present in `settings-manifest.json`.
- `Grep` - the `rowEnablePersistentAudioPlayback` entry now carries the playback section/destination, not `audio`/`MEDIA`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. settings-manifest.json regenerated; headerBackgroundAudio present, rows -> sectionId=playback/PLAYBACK.

---

### Step 06.3 - Re-render the settings reference docs

**Files:** `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/SETTINGS_REFERENCE_UK.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Re-render the reference from manifest + annotations: `pwsh -NoProfile -File scripts/docs/render-settings-reference.ps1`. Do not hand-edit the generated `.md` files. If a new sectionId were introduced it would need `sectionOrder`/`sectionLabel` additions - but this phase reuses `sectionId=playback`, so no script change is needed.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0 (manifest fresh, annotations complete, reference byte-identical, HOW_TO paths valid).

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 1/1 PASS. SETTINGS_REFERENCE*.md re-rendered; assert-settings-doc-sync.ps1 OK (catalog/manifest/annotations/reference/HOW_TO all green).

---

### Step 06.4 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.3

**Prompt for developer:**

> Add one EN-only record via `scripts/all_features/add.ps1` describing: background-audio settings consolidated into a collapsible "Background audio playback" group on the Player tab, and the background-playback setting + on-exit choice now applying to audio streams. Do not edit `docs/FEATURES*.md` (release-time only). Validate with `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - a new record mentioning background audio + streams present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification PASS (own entry). streams.background-playback-and-exit added + well-formed. validate.ps1 red only on 2 pre-existing malformed rows (s0575/s0559) -> parked S0584.

---

### Step 06.5 - Catalog + dev log + neuroslop gate

**Files:** `dev/CHANGELOG.md` (via script), `dev/CATALOG/app_v2.jsonl` (regenerated)
**Depends on:** Step 06.4

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (new classes: `AudioExitBehaviorResolver`, `BackgroundAudioExitDialog`). Set `role`/`status` for the new classes via `set.ps1`. Add a batched dev-log entry for the ticket via `close-and-log.ps1 -DevLogs` (one entry per phase is acceptable; do not log per file). Run the neuroslop + deprecated-PM-flags gates via `scripts/post-change.ps1` on the touched Kotlin.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` exits 0.
- `dev/CHANGELOG.md` contains an S0577 entry.
- `dev/CATALOG/app_v2.jsonl` contains `AudioExitBehaviorResolver` and `BackgroundAudioExitDialog`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification PASS. assert-neuroslop + assert-deprecated-pm-flags green; catalog scanned/rendered (AudioExitBehaviorResolver, BackgroundAudioExitDialog role/status set); ..ps1 d BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- [ ] Full build sanity - `.\a.ps1 d` produces a debug APK (packaging proof for the settings + streams changes).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, `/spec-dev` inserts the `Timber.d("S0577: ..")` verification tags (one per changed flow) and advances the ticket to `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit(s); regenerated docs return to the prior manifest/reference. No source or behavior change in this phase.
