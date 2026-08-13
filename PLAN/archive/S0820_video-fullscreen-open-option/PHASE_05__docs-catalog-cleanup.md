# Phase 05 - Docs, settings-doc sync, and catalog cleanup

**Strategic spec:** [`../S0820_video-fullscreen-open-option.md`](../S0820_video-fullscreen-open-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Bring the settings manifest/annotations/reference docs, the shippable-capability inventory, and the class catalog in sync with the shipped setting (CLAUDE.md Rule 22). No further source-behavior changes.

---

## Prerequisites

- [ ] Phase 01, 02, 03, 04 are all ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/settings/settings-annotations.json` | Modified | - |
| `docs/SETTINGS_REFERENCE.md` | Regenerated | - |
| `docs/SETTINGS_REFERENCE_RU.md` | Regenerated | - |
| `docs/SETTINGS_REFERENCE_UK.md` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified (new record) | - |
| `dev/CATALOG/app_v2.jsonl` / `.md` | Regenerated, gitignored | - |

---

## Steps

### Step 05.1 - Regenerate the settings manifest

**Files:** `docs/settings/settings-manifest.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `.\gradlew.bat ":app_v2:testStandardDebugUnitTest" "--tests" "*SettingsManifestExportTest" "-Dsettings.manifest.generate=true"` - quote the `-D` flag - to regenerate the manifest from the live `AppSettings`/layout scan, picking up `rowOpenVideoInFullscreen`.

**Verification:**

- `Grep` - `rowOpenVideoInFullscreen` in `docs/settings/settings-manifest.json`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification PASS. Blocked initially by an unrelated pre-existing test-compile error (`RestoreFromGoogleDriveUseCaseTest.kt` - ambiguous `any()` after concurrent ticket S0876 added a `SettingsRepository.updateSettings` overload) - fixed inline as a trivial one-liner (explicit `any<AppSettings>()`) per CLAUDE.md 3.1, not parked. Files: docs/settings/settings-manifest.json (regenerated), app_v2/src/test/.../RestoreFromGoogleDriveUseCaseTest.kt (+1 import, 1-line fix, unrelated to S0820 scope).

---

### Step 05.2 - Add the settings annotation entry

**Files:** `docs/settings/settings-annotations.json`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a `"rowOpenVideoInFullscreen"` entry keyed exactly as the manifest emitted it (View id, not the Kotlin field name or DataStore key - match the existing entries' keying convention), with non-empty `en`/`ru`/`uk` one-sentence explanations in the same tone as neighboring Video-section entries.

**Verification:**

- `pwsh -NoProfile -File scripts/docs/check-settings-annotations.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification PASS. `check-settings-annotations.ps1`: OK - 207 unique keys, all en/ru/uk present, 0 orphans. Files: docs/settings/settings-annotations.json (+5 LOC).

---

### Step 05.3 - Re-render the settings reference docs

**Files:** `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/SETTINGS_REFERENCE_UK.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/docs/render-settings-reference.ps1` and commit the resulting diff verbatim - do not hand-edit these files.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1 -SkipManifestTest` exits 0 (manifest freshness already proven in Step 05.1; `-SkipManifestTest` avoids re-running the slow gradle test here).

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification PASS. settings-doc-sync: OK - catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync. Files: docs/SETTINGS_REFERENCE.md, docs/SETTINGS_REFERENCE_RU.md, docs/SETTINGS_REFERENCE_UK.md, docs/SETTINGS_REFERENCE_noLegal.md (all regenerated).

---

### Step 05.4 - Record the shippable capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - independent of 05.1-05.3, may run any time after Phase 04

**Prompt for developer:**

> Run `scripts/all_features/add.ps1` for the new capability: area "Video Player" (or "Settings" if that better matches existing area naming for settings toggles - check a few existing Video-section records first), one-sentence EN description matching strategic §8 ("Video files opened from Browse can launch straight into fullscreen playback, controlled by a new Video Settings toggle - on by default for every device profile except Audio Player and E-book Reader."), `spec: "S0820"`, `flavors: ["standard","lite","photos","legacy"]` per strategic §3.2 (no VR/noLegal-only scoping - the player host and settings screen are shared across all of them).

**Verification:**

- `Grep` - `"spec":"S0820"` in `docs/ALL_FEATURES.jsonl` matches once.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification PASS. Record id `video-player.open-in-fullscreen-from-browse` added, spec S0820. Validation: PASS, 476 records. Files: docs/ALL_FEATURES.jsonl (+1 record).

---

### Step 05.5 - Catalog sync

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (gitignored, regenerate only)
**Depends on:** Phase 04 (all source files exist)

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

**Verification:**

- Command exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification PASS. Scanned 1728 files, 2098 records. Files: dev/CATALOG/app_v2.jsonl, dev/CATALOG/app_v2.md (regenerated, gitignored).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Final full build - `.\a.ps1 d` - PASS (37s).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entries added for every file touched across Phase 01-05 (batched per phase, see below).
- [x] `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` (full, no `-SkipManifestTest`) exits 0 as a final sanity pass.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. Next step outside this tactical plan is `/spec-dev`'s completion handoff into `/spec-check S0820`.

---

## Rollback Plan

Low-risk: documentation/catalog regeneration only, no application behavior. Revert this phase's commit(s) independently of Phase 01-04 if needed.
