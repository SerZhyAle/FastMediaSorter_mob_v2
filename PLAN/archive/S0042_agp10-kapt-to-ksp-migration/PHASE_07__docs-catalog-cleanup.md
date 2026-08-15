# Phase 07 — docs-catalog-cleanup

**Strategic spec:** [`../S0042_agp10-kapt-to-ksp-migration.md`](../S0042_agp10-kapt-to-ksp-migration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Blocks:** none — final phase
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Close out the migration: confirm dev log coverage for every file touched across all phases, and verify no catalog regen or FEATURES update is required.

---

## Prerequisites

- [ ] All phases 01–06 are ✅ Done.
- [ ] Full build (all 6 flavors × debug + wear) exits 0 with 0 deprecation warnings.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` (via script) | Modified | — |

---

## Steps

### Step 7.1 — Verify dev log coverage

**Files:** `dev/CHANGELOG.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Confirm that `dev/CHANGELOG.md` has an entry for every file modified across the migration. Required entries (each added via `.\scripts\add_to_dev_log.ps1`):
>
> - Phase 01: `app_v2/build.gradle.kts` — Switch Glide compiler kapt → ksp
> - Phase 02: `app_v2/build.gradle.kts` — Switch Room compiler kapt → ksp
> - Phase 03: `app_v2/build.gradle.kts` — Switch all Hilt compilers kapt/kaptAndroidTest → ksp/kspAndroidTest
> - Phase 04: `app_v2/build.gradle.kts` — Remove kotlin-kapt plugin and kapt{} config block
> - Phase 04: `gradle.properties` — Remove kapt-specific properties
> - Phase 05: `gradle.properties` — Remove legacy DSL compat flags android.builtInKotlin and android.newDsl
> - Phase 06: `wear/build.gradle.kts` — Migrate kotlinOptions to compilerOptions DSL
> - Phase 06 (if changed): `app_v2/build.gradle.kts` — Migrate vrUnlicensed sourceSets to Variant Sources API
>
> For any missing entry, run:
> ```
> .\scripts\add_to_dev_log.ps1 "<path>" "S0042" "<description>"
> ```

**Verification:**

- `Grep` — `S0042` in `dev/CHANGELOG.md` returns ≥ 7 hits (one per phase that modified a file).

**Status:** `[ ]` not done

---

### Step 7.2 — Confirm FEATURES.md and catalog exclusions

**Files:** none
**Depends on:** Step 7.1

**Prompt for developer:**

> Confirm that no changes are needed for:
> - `docs/FEATURES.md` — this migration is infrastructure-only; no user-facing feature changed (see strategic §8).
> - `dev/CATALOG/app_v2.jsonl` — no `.kt` files were modified; catalog regen is not required.
> - `dev/CATALOG/wear.jsonl` — same rationale.
>
> Run `/spec-check S0042` to trigger the final audit.

**Verification:**

- `Grep` — `FEATURES.md` contains no new bullet referencing KSP or kapt migration (infra change, not user-facing).
- `/spec-check S0042` run initiated.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] `Grep` — `S0042` in `dev/CHANGELOG.md` returns ≥ 7 hits.
- [ ] `/spec-check S0042` initiated.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After `/spec-check S0042` passes and returns `Verified`, the strategic spec status is updated to `Verified` and all `Timber.d("S0042:` debug tags (if any were added) are removed from `.kt` files.

> Note: this spec touches only `.gradle.kts` and `gradle.properties` files — no Kotlin source files, so no `Timber.d` tags were added. The Timber-tag removal step is a no-op.

---

## Rollback Plan

Final phase — no code changes. No rollback needed.
