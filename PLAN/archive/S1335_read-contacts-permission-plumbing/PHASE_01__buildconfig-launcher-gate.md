# Phase 01 - BuildConfig launcher gate

**Strategic spec:** [`../S1335_read-contacts-permission-plumbing.md`](../S1335_read-contacts-permission-plumbing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Give the launcher its first `BuildConfig` field (`SUPPORT_LAUNCHER`) so Phase 02's permission entry
has something to gate on - today the launcher is isolated purely by source set, with no field at all.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none - foundation phase)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | +3 lines |

> This file is 1554 lines; per CLAUDE.md Rule 5 (backup before editing >500 LOC), take a timestamped
> copy under `temp/S1335/` before editing.

---

## Steps

### Step 01.1 - Add the `SUPPORT_LAUNCHER` field

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `app_v2/build.gradle.kts` to `temp/S1335/build.gradle.kts.<timestamp>.bak` first (1554
> lines, over the Rule 5 threshold). Add `buildConfigField("boolean", "SUPPORT_LAUNCHER", "false")` to
> `defaultConfig` immediately after the existing `buildConfigField("boolean", "IS_NO_LEGAL_FLAVOR",
> "false")` line (currently line 268) - same declare-once-default-false pattern, not a separate literal
> in all six flavor blocks. Then override it to `true` in exactly the two flavor blocks that mount
> `src/launcherEnabled/`: add `buildConfigField("boolean", "SUPPORT_LAUNCHER", "true")` inside
> `create("standard")` (after its `SUPPORT_LOCAL_NETWORK` line, currently :317) and inside
> `create("noLegal")` (after its `IS_NO_LEGAL_FLAVOR` override, currently :401). Do not touch `lite`,
> `photos`, `legacy`, or `vr` - they inherit the `defaultConfig` default of `false` unchanged.

**Verification:**

- `Grep` - `app_v2/build.gradle.kts` contains exactly 3 occurrences of
  `buildConfigField("boolean", "SUPPORT_LAUNCHER",` (one `"false"` in `defaultConfig`, two `"true"` -
  one inside `create("standard")`, one inside `create("noLegal")`).
- `.\a.ps1 fk` (Kotlin compile, standard) succeeds - proves the new field is visible to
  `BuildConfig.SUPPORT_LAUNCHER` for Phase 02 to reference.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 2/2 PASS. Files: `app_v2/build.gradle.kts` (+3 lines: defaultConfig
  default `false`, `standard` + `noLegal` overrides `true`). Backup:
  `temp/S1335/build.gradle.kts.<timestamp>.bak`. `.\a.ps1 fk` PASS.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (`.\a.ps1 fk` suffices, no resource/manifest change this phase).
- [x] Dev log entry added for `app_v2/build.gradle.kts` via `.\scripts\add_to_dev_log.ps1` (via `post-change.ps1`).
- [x] Phase-boundary audit run - Layer 1 only (gradle config, no Kotlin/lifecycle/Room surface): field
      declared once with a safe default and two explicit overrides, matching the `IS_NO_LEGAL_FLAVOR`
      precedent exactly. No P0/P1 findings.

---

## Handoff Notes to Next Phase

`BuildConfig.SUPPORT_LAUNCHER` exists, `true` only on `standard` and `noLegal`. Phase 02's
`resolveFlavorGate` arm and the new `PermissionEntry.flavorGates` both reference it by the literal
string `"SUPPORT_LAUNCHER"`.

---

## Rollback Plan

Remove the three `buildConfigField` lines - no data migration, no user-facing surface changed (the
field has no consumer until Phase 02 lands).
