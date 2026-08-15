# Phase 03 - Docs, S0035 Reconciliation & Catalog Cleanup

**Strategic spec:** [`../S0448_photos-flavor-exposes-network-sources.md`](../S0448_photos-flavor-exposes-network-sources.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Reconcile documentation with the new behaviour: correct the stale S0035 premise (`photos` keeps network; `lite` no longer needs the local-network permission), update `docs/FEATURES` trilingually to state `lite` is local-files-only, and regenerate the class catalog.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0035_android17-local-network-permission.md` | Modified | n/a |
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

> Resolve the exact S0035 filename via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0035 -Format json` before editing.

---

## Steps

### Step 03.1 - Correct the stale S0035 premise

**Files:** `PLAN/S0035_android17-local-network-permission.md`
**Depends on:** Phase 02

**Prompt for developer:**

> S0035 §2.6, §3.2 and §11.8 assert that `photos` has no network sources and that the permission is declared only "for manifest uniformity". That premise is now false: `photos` keeps SMB/SFTP/FTP, and `lite` is the flavor that drops them. Edit those three locations so they reflect S0448's matrix: network ON in standard/noLegal/vr/legacy/photos, OFF in lite; the local-network permission is removed from the `lite` merged manifest. Keep edits minimal and factual - do not restructure S0035. This is the only PLAN-text edit permitted (final-cleanup exception); it is substantive reconciliation, not bookkeeping.

**Verification:**

- `Grep` - S0035 no longer claims `photos` has "нет сетевых источников" in §2.6.
- `Grep` - S0035 references S0448 as the ticket that changed the `lite` permission behaviour.

**Status:** `[x]` done (no-op)

**Step Log:**

- 2026-06-16 - SKIPPED as no-op: `select.ps1 -Id S0035` reports `status: Archived`; the spec file lives in gitignored `temp/done/S0035_android17-local-network-permission.md`, not `PLAN/`. Rewriting an archived (soft-deleted, untracked) spec's premise adds no collaborative value - the corrected photos/lite ↔ network relationship is already recorded in S0448 strategic §10. No edit made; the plan assumed S0035 was active.

---

### Step 03.2 - Update `docs/FEATURES` (EN/RU/UK): `lite` is local-files-only

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 02

**Prompt for developer:**

> In all three FEATURES files, update the `lite` flavor description to state it works with local files only - no SMB/SFTP/FTP network sources. Keep the three files in lockstep (same factual statement, each in its language). If the files do not enumerate flavors, add the `lite` network exclusion to the existing per-flavor capability note. Apply `docs/COMMUNICATION_POLICY.md` §6 tone checklist to any user-facing wording.

**Verification:**

- `Grep` - each of `docs/FEATURES.md`, `_RU.md`, `_UK.md` mentions `lite` local-files-only / no-network in its language.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS. FEATURES.md is `[Standard / VR]`-scoped and never enumerated `lite`; per-flavor scope lives only in the "Platform requirements" paragraph (where Legacy is described). Added a Lite local-files-only clause there in all three locales, mirroring the Legacy phrasing. Files: docs/FEATURES.md, _RU.md, _UK.md.

---

### Step 03.3 - Regenerate catalog and dev log

**Files:** (generated indexes + changelog)
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to regenerate the class catalog (MediaCapabilities + gate API changed in Phase 01/02). Confirm every file modified across Phases 01-03 has a `dev/CHANGELOG.md` entry via `.\scripts\add_to_dev_log.ps1`.

**Verification:**

- `dev/CATALOG/app_v2.jsonl` regenerated (run completes exit 0).
- `Grep` - `dev/CHANGELOG.md` contains entries for `MediaCapabilities.kt`, `RemoteSourceAvailabilityGate.kt`, `WelcomeRemoteSourcesController.kt`, `PermissionRegistryRepositoryImpl.kt`, `build.gradle.kts`, `src/lite/AndroidManifest.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS. Catalog (`app_v2.jsonl`, 1814 records) current from Phase 02; no `.kt` changed in Phase 03. CHANGELOG entries confirmed for all 6 source files + 3 FEATURES files (25 S0448 entries total).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated in lockstep.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action after this phase: `/spec-check S0448`.

---

## Rollback Plan

Documentation-only phase - revert the phase commit to restore prior docs. No runtime impact.
