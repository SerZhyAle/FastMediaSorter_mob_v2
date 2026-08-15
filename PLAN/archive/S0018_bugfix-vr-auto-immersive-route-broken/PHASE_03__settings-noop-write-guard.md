# Phase 03 — Settings No-op Write Guard

**Strategic spec:** [`../S0018_bugfix-vr-auto-immersive-route-broken.md`](../S0018_bugfix-vr-auto-immersive-route-broken.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent of Phase 01/02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Make `SettingsRepositoryImpl.updateSettings` idempotent: when the incoming `AppSettings` value equals the currently persisted one, short-circuit before touching DataStore. Eliminates the spam of `NO fields changed — possible no-op write` warnings on every settings screen open.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 1100 (current ~1000) |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImplTest.kt` | New | ≤ 200 |

---

## Steps

### Step 03.1 — Add idempotency short-circuit in `updateSettings`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `updateSettings(settings: AppSettings)`, after reading `current` from `getSettings().first()`, compare `current == settings` (data-class equality). If equal, log `Timber.v("SettingsRepo: updateSettings idempotent — skipping DataStore write")` and `return` before the existing `dataStore.edit { .. }` block. Keep the existing diff-detection diagnostic (it is now unreachable in the equal case, which is correct). Do NOT remove the `BuildConfig.DEBUG` diff block; relocate it before the early return so it still logs the (empty) diff in DEBUG builds for one release as a safety net, then the early return runs.

**Verification:**

- `Grep` — `idempotent — skipping DataStore write` matches exactly once in `SettingsRepositoryImpl.kt`.
- `Grep` — `current == settings` matches exactly once in `SettingsRepositoryImpl.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `SettingsRepositoryImpl.kt` (Timber-only invariant).

**Status:** `[x]` done

---

### Step 03.2 — Add unit test verifying idempotency

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImplTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create a new JUnit test class `SettingsRepositoryImplTest`. Use a fake or in-memory DataStore — if existing test infra has a `FakeDataStore` helper, reuse it; otherwise mock `dataStore.edit` and verify the lambda is not invoked when input equals current. The test scenario: seed repository with an `AppSettings` value, call `updateSettings(sameValue)`, assert `dataStore.edit` was invoked exactly zero times after seeding. A second test: call `updateSettings(differentValue)`, assert `dataStore.edit` invoked exactly once.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImplTest.kt` exists.
- `Grep` — `class SettingsRepositoryImplTest` matches exactly once in that file.
- `Grep` — `@Test` matches at least 2 times in that file.
- `Grep` — `idempotent` or `no-op` or `not invoked` matches in that file (test naming intent).

**Status:** `[x]` done

---

### Step 03.3 — Update the diff-detection inventory to cover all `AppSettings` fields

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> The DEBUG-only diff-detection block currently inspects only ~10 fields of `AppSettings`. Replace the manual list with the data-class equality result (already computed in Step 03.1) and a single `if (current != settings) Timber.d("SettingsRepo: updateSettings diff (changed)")` — the granular per-field log is no longer load-bearing because the early return makes "no diff" silent. Remove the now-redundant manual diff list. The DEBUG-only `try { .. } catch` wrapper around the read may also be removed (the read already happens in the live path).

**Verification:**

- `Grep` — `if (current.allFiles != settings.allFiles)` returns zero hits in `SettingsRepositoryImpl.kt` (manual list removed).
- `Grep` — `Timber.d("SettingsRepo: updateSettings diff` matches at most 1 time.
- `Grep` — `BuildConfig\.DEBUG` count in this file does not increase compared to current.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `standard debug`.
- [ ] New unit tests pass.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `SettingsRepositoryImpl.kt` and `SettingsRepositoryImplTest.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After this phase, opening any settings screen produces zero `NO fields changed` warnings. Phase 05 verifies this on device.

---

## Rollback Plan

Revert phase commit — no schema change, no DataStore migration; behaviour reverts to "always write, log no-op warning".
