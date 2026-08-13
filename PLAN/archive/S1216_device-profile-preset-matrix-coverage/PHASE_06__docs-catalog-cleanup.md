# Phase 06 - Docs, catalog and capability record

**Strategic spec:** [`../S1216_device-profile-preset-matrix-coverage.md`](../S1216_device-profile-preset-matrix-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## Objective

Bring the developer documentation, the public feature docs, the capability inventory and the class catalog in line with what the previous phases changed.

---

## Prerequisites

- [ ] Phases 01 through 05 are ✅ Done.
- [ ] `.\a.ps1 fg` returns exit code 0.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/DEVICE_PROFILE_PRESET_MATRIX.md` | Modified | ≤ 200 |
| `docs/QUICK_START.md` | Modified | ≤ +1 sentence |
| `docs/QUICK_START_RU.md` | Modified | ≤ +1 sentence |
| `docs/QUICK_START_UK.md` | Modified | ≤ +1 sentence |
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ +1 record |

> `docs/FEATURES*.md` was in this list when the plan was written; CLAUDE.md Rule 11 reserves those
> three files for `/skill-release`, which generates them from the `ALL_FEATURES` diff. Step 06.4
> records the substitution.

---

## Steps

### Step 06.1 - Correct the stale developer reference

**Files:** `dev/DEVICE_PROFILE_PRESET_MATRIX.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Section 3 cites `screenshotGestureActionDown=SILENT_SCREENSHOT` as a live enum example; that field no longer exists and Phase 02 and Phase 03 removed its branch and row. Replace the example with a per-zone field such as `screenshotGestureLeftTopDown`. Section 4.6 still says the coverage checker should be "considered" for CI - replace that with the fact that it now runs inside `assert-fast-gates.ps1` and `post-change.ps1`. Section 3's "Known data caveats" list should lose the caveats the gate now enforces mechanically (the `defaultIconSize` slider-step rule) and keep the ones still owner-owned.

**Verification:**

- `Grep` - `screenshotGestureActionDown` returns zero hits in the file.
- `Grep` - `assert-device-profile-matrix` matches in the file.
- `Grep` - `Consider wiring it into CI` returns zero hits in the file.

**Status:** `[x]` done - the enum example now cites `screenshotGestureLeftTopDown`, section 4.6 states
the gate runs inside `assert-fast-gates.ps1` and `post-change.ps1`, and the `defaultIconSize` caveat
moved out of "owner to fix" into a new gate-enforced value-constraint list that also covers the
reader sliders and the launcher density set.

---

### Step 06.2 - Document the non-presettable registry

**Files:** `dev/DEVICE_PROFILE_PRESET_MATRIX.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a section describing the registry: where it lives, what a record means, and the rule that a new `AppSettings` field must get either a CSV row or a registry entry before the gate goes green. State explicitly that the applier's `else -> skip(..)` branch remains the runtime safety net and that the registry is the declaration, so a future reader does not treat one as redundant with the other.

**Verification:**

- `Grep` - `device-profile-nonpresettable.json` matches in the file.
- `Grep` - a heading containing `non-presettable` is present.

**Status:** `[x]` done - section 6 "Non-presettable settings registry (S1216)", including the
explicit statement that the applier's `else -> skip(..)` stays the runtime safety net while the
registry is the ahead-of-build declaration.

---

### Step 06.3 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.2

**Prompt for developer:**

> Add one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN only) describing the shipped capability: device profiles now seed reader, link-download, player-interaction, streams and launcher defaults, and the profile-change confirmation names how many settings will be overwritten. Read the record back and set its flavor list from what the gate actually allows rather than from assumption - the launcher part only takes effect on builds that ship the home surface.

**Verification:**

- `Grep` - `device profile` matches the new record in `docs/ALL_FEATURES.jsonl`.
- Value equality - `pwsh -NoProfile -File scripts/all_features/validate.ps1` returns exit code 0.

**Status:** `[x]` done - record
`settings.device-profile-presets-seed-reader-streams-launcher`, flavors
`standard,lite,photos,legacy,vr,noLegal` because the matrix and the applier live in `src/main` and
ship in every build; the launcher clause is qualified in the description because
`LauncherModeContract` gates it to the builds that compile the home surface. `validate.ps1` PASS,
617 records, exit 0.

---

### Step 06.4 - Update the user-facing docs

**Files:** `docs/QUICK_START.md`, `docs/QUICK_START_RU.md`, `docs/QUICK_START_UK.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Strategic §8 carries a FEATURES sentence, but CLAUDE.md Rule 11 reserves `docs/FEATURES*.md` for
> `/skill-release`, which regenerates all three locales from the `ALL_FEATURES` diff - editing them
> per-spec would be overwritten and would double-count the capability at release time. The
> user-facing surface that *is* per-spec editable and already describes what a profile seeds is the
> Quick Start guide (registry record `user-guides`), whose profile paragraph listed only layout,
> thumbnails, fullscreen, keep-awake, background audio and confirmations. Extend that list with what
> this ticket added, in all three locales in one edit. House text style applies: `..` rather than
> `...`, plain hyphen, Russian `ё` where grammatical.

**Verification:**

- `Grep` - the extended profile sentence is present in each of the three Quick Start files.
- `Grep` - `...` returns zero hits in the edited lines.
- Value equality - `docs/FEATURES*.md` untouched; strategic §8 wording carried by the
  `ALL_FEATURES` record from step 06.3, which is what `/skill-release` reads.

**Status:** `[x]` done

---

### Step 06.5 - Regenerate the catalog and close mechanically

**Files:** `dev/CATALOG/app_v2.jsonl` (generated, not committed)
**Depends on:** Step 06.4

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the ticket, then close through the `scripts/post-change.ps1` facade with `-ChangeType Mixed`. The catalog files under `dev/CATALOG/` are gitignored local indexes - regenerate them, never commit them.

**Verification:**

- Value equality - `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*CountProfilePresetOverridesUseCase*"` returns one record with a non-empty `role`.
- Value equality - `pwsh -NoProfile -File scripts/post-change.ps1 -File "dev/DEVICE_PROFILE_PRESET_MATRIX.md" -Target "docs" -Description "S1216 matrix coverage" -ChangeType Mixed -Module app_v2` returns exit code 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `.\a.ps1 fg` returns exit code 0.
- [ ] Document-registry loop closed: `scripts/document_registry/query.ps1` re-run for areas `settings` / `ui` and triggers `setting` / `user-feature`; the matched records `feature-inventory` (step 06.3), `ui-communication` (Phase 05 string rewrite) and `settings-reference` / `user-guides` (changed fresh-install defaults) each either updated or recorded as unchanged with a reason. Site, VR, Wear and icon records are untouched by this ticket.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. Strategic criteria 5 to 8 are device-observable and belong to the `/spec-dev` device-test step: apply `ebook_reader`, `car_head_unit`, `photo_frame` and `audio_player` on a clean install and confirm each differs from `personal_smartphone` in the promised way.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only.
