# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1173_launcher-cells-translucent-over-wallpaper.md`](../S1173_launcher-cells-translucent-over-wallpaper.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## Objective

Regenerate the class catalog for the two new widgets and the moved class, record the delivered capability, and journal every touched file.

---

## Prerequisites

- [ ] Phases 01 to 03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CHANGELOG.md` | Modified via script | - |

> `dev/CATALOG/app_v2.jsonl` and its `.md` twin are gitignored local indexes - regenerate, never commit.

---

## Steps

### Step 04.1 - Regenerate the class catalog and set roles

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set the role and status for the two new widgets through `dev/CATALOG/scripts/set.ps1`. Both are shared UI widgets available to every flavor, so declare no flavor exclusions.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "OutlinedImageView"` returns one record.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -PathMatches "*common/widget/OutlinedTextView*"` returns one record.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -PathMatches "*cameracapture/OutlinedTextView*"` returns zero records.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 3/3 PASS. Catalog regenerated (2361 records). Both widgets carry a role and `status=new`; no `-NoFlavors` set, since both ship in every flavor.

---

### Step 04.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one record through `scripts/all_features/add.ps1` for the launcher desktop showing the wallpaper through its shortcut cells. Read the shipping flavors off the actual gate - the `launcherEnabled` source set and the launcher `BuildConfig` flag in `app_v2/build.gradle.kts` - never off a sibling record. Text is EN-only.

**Verification:**

- `Grep` - `S1173` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 2/2 PASS. `validate.ps1`: `PASS: 613 record(s)`. Record `launcher.wallpaper-visible-through-transparent-shortcut-cells`, area `Launcher`. Flavors `standard,noLegal` derived from the gate - `src/launcherEnabled` is mounted only in the `standard` block (`app_v2/build.gradle.kts:603`) and the `noLegal` block (`:631`); the existing `Launcher` records agree, but the list was read off the build file, not copied from them.

---

### Step 04.3 - Journal every touched file

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Batch one dev-log entry per logical change through `scripts/spec_catalog/close-and-log.ps1 -DevLogs`, passing a single JSON-array string. Do not hand-edit `dev/CHANGELOG.md`. Route the per-file mechanical closure through `scripts/post-change.ps1` with `-ScopeToFile`, since the working tree carries other tickets' work in progress.

**Verification:**

- `Grep` - `S1173` present in `dev/CHANGELOG.md`.
- `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` - reports no new findings for the touched files.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 2/2 PASS. `dev/CHANGELOG.md` holds 22 `S1173` entries, one per logical change across the four phases. `post-change.ps1 -ScopeToFile`: `PASS (Kotlin)` with `neuroslop-gate PASS`, `listener-symmetry-gate PASS (new imbalance 0)`, `detekt-gate PASS`, `ticket-log-audit PASS`.
- 2026-07-30 - Debug verification tag inserted at the desktop-render entry (`bind`) before the final build, per the `BlockNeedUserTest` contract; one tag covers both changed render flows because `editMode` is in the message. `ticket-log-audit` first FAILED (`expected 0 | actual 1`) because the tag landed while the ticket was still `In Progress` - the gate only tolerates probes for a ticket already in `BlockNeedUserTest`. Flipping the status first, then re-running, is the correct order; re-run reported `actual: 0` with 68 allowed probes.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2361 records rendered.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] Phase-boundary audit - skipped by contract: this phase's `Files Touched` is catalog, inventory and journal only, with no source behaviour to audit. The one source edit made during finalization (the debug tag) is a temporary probe removed on the `Verified` transition.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The remaining gate is on-device verification of contrast and focus over a real wallpaper.

---

## Rollback Plan

Nothing to roll back - the catalog is a regenerated local index and the journal is append-only.
