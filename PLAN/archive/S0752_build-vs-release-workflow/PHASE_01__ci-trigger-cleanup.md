# Phase 01 - CI Trigger Cleanup

**Strategic spec:** [`../S0752_build-vs-release-workflow.md`](../S0752_build-vs-release-workflow.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-06-27
**Completed:** 2026-06-27

---

## Objective

Remove the dead `develop` branch trigger from `android-ci.yml` so the documented CI cost map matches the real workflow configuration.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.github/workflows/android-ci.yml` | Modified | ≤ 5 |

---

## Steps

### Step 01.1 - Drop `develop` from push trigger

**Files:** `.github/workflows/android-ci.yml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `on.push.branches` list, change `[ main, develop ]` to `[ main ]`. The `develop` branch does not exist in this repo (only `main` + `DEBUG-v0NN`); the trigger is dead and misleads the cost map. Do not touch the `pull_request` or `workflow_dispatch` triggers, and do not alter the other two workflow files.

**Verification:**

- `Grep` - `branches: \[ main \]` present in `on.push` block of `.github/workflows/android-ci.yml`.
- `Grep` - `develop` returns zero hits in `.github/workflows/android-ci.yml`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS. Removed `develop` from `on.push.branches` (now `[ main ]`); also fixed stale `develop` mention in the build-flavors comment (file had been restructured externally to verify/build-flavors/release-check). `develop` now zero hits.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] `Grep` for `develop` in `.github/workflows/android-ci.yml` returns zero hits.
- [ ] Dev log entry added for `.github/workflows/android-ci.yml`.

---

## Handoff Notes to Next Phase

CI now fires only on `main` push + PR to `main` + manual dispatch. The cost map in Phase 03 can state this as fact.

---

## Rollback Plan

Revert the one-line change - no behavioral dependency on the `develop` trigger existed.
