# Phase 05 - Docs, settings-manifest and catalog cleanup

**Strategic spec:** [`../S0994_companion-publish-folders-help-link.md`](../S0994_companion-publish-folders-help-link.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-07-11
**Completed:** 2026-07-11 (full `.\a.ps1 d` BUILD SUCCESSFUL - APK v2.60.7110.431)

---

## Objective

Regenerate the settings documentation for the new General link button, record the new capability, and sync the class catalog.

---

## Prerequisites

- [ ] Phases 02, 03, 04 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Modified (generated) | - |
| `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/SETTINGS_REFERENCE_UK.md` | Modified (generated) | - |
| `docs/settings/settings-annotations.json` | Modified | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | - |

---

## Steps

### Step 05.1 - Regenerate settings manifest, reference and annotations (Rule 22)

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> The new `btnCompanionPublishGuide` is a General-settings link entry (its siblings `btnUserGuide` / `btnHowToGuides` / `btnPrivacyPolicy` are already in the manifest). Regenerate the settings manifest + reference and add its annotation so the doc-sync gate passes. Use the project's settings-doc regeneration path, then verify with the gate.

**Verification:**

- `Grep` - `btnCompanionPublishGuide` present in `docs/settings/settings-manifest.json`.
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - PASS. Regenerated `settings-manifest.json` (generate-mode export test) -> `btnCompanionPublishGuide` entry with EN/RU/UK titles; added annotation; re-rendered `SETTINGS_REFERENCE*.md`. `assert-settings-doc-sync.ps1` exit 0 (all 5 stages).

---

### Step 05.2 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one capability record via `scripts/all_features/add.ps1` (EN-only) describing the in-app help link to the PC-side companion publish-folders guide (both entry points). Do NOT edit `docs/FEATURES*.md` - those are `/skill-release`-owned.

**Verification:**

- `Grep` - a new record mentioning `publish` companion guide present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - PASS. Added `network.companion_publish_guide` (Network & Cloud, flavors standard/photos/legacy/noLegal, Spec S0994) via `add.ps1`; `validate.ps1` exit 0 (511 records).

---

### Step 05.3 - Sync the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1, Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once (touched `SupportIntentFactory`, `AddResourceActivity`, `GeneralSettingsViewSetupHelper`, `SettingsViewModel`).

**Verification:**

- Command exits 0; `dev/CATALOG/app_v2.jsonl` regenerated.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - PASS. `catalog_sync.ps1 -Module app_v2` OK (2143 records; also re-scanned in finalize).

---

### Step 05.4 - Dev log and final static gates

**Files:** (dev log / gates - no new source)
**Depends on:** Step 05.3

**Prompt for developer:**

> Ensure one dev-log entry per logical change exists (batch via `close-and-log.ps1 -DevLogs`). Run the fast static gate batch (`.\a.ps1 fg`) to confirm neuroslop / listener-symmetry / flavor-flag / ticket-log gates pass on the touched files.

**Verification:**

- `.\a.ps1 fg` reports PASS.
- `Grep -n "Log\.d\("` across touched Kotlin returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - PASS. `.\a.ps1 fg` all fast gates green (ticket-log accepted 20 BNUT probes incl. 2x S0994; neuroslop PASS). Diff-scoped detekt PASS - no new findings in the 4 touched `.kt`. Dev logs batched via `close-and-log` (8 logical entries).

---

## Phase Done Criteria

- [ ] All four steps are `[x] done`.
- [ ] Full build proof: run `/build` (`.\a.ps1 d`) - BUILD SUCCESSFUL.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Settings-doc-sync gate and ALL_FEATURES validate pass.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. Ticket then moves to `BlockNeedUserTest` (S0994 debug probes present from Phases 02/03) for on-device verification of both entry points across flavors.

---

## Rollback Plan

Revert the generated-doc and catalog changes together with the phase commits they document - no user data affected.
