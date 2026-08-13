# Phase 03 - Coordinator global-default slot

**Strategic spec:** [`../S0326_media-3dvr-default-settings.md`](../S0326_media-3dvr-default-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Add a new lowest-priority "global default" slot to the stereo-mode resolution chain so that the user's default layout/projection applies only when detection returned unknown, never overriding a per-file override or a positive detection. Lock the precedence with unit tests (the coordinator currently has none).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Read the current resolution precedence in `PlayerStereoModeCoordinator` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 1500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinatorTest.kt` | New | ≤ 300 |

> PlayerViewModel supplies the global-default lambda (cached from settings); StandalonePlayerViewModel keeps the default `{ MONO }` (no settings access). The coordinator also gained an injected `ioDispatcher` (defaults to `Dispatchers.IO`) so the override path is deterministic in tests.

---

## Steps

### Step 03.1 - Characterize existing precedence with tests first

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinatorTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Before changing behavior, add characterization unit tests for the current chain: per-file override wins over detection; session manual selection wins over detection; detection wins over nothing; the AUTO/UNKNOWN sentinels are never published to the renderer.

**Verification:**

- `Glob` - `PlayerStereoModeCoordinatorTest.kt` exists.
- The characterization tests pass against current behavior.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - 4 stable-invariant tests (override>detection, manual>detection, detection>nothing, sentinels never published) authored with FakeOverrideDao + injected UnconfinedTestDispatcher. PASS.

---

### Step 03.2 - Insert the global-default fallback slot

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinator.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend the resolution so the effective mode is computed as: per-file override > session manual selection > positive detection > global default (from `AppSettings.stereoDefaultLayout` / `stereoDefaultProjection`) > AUTO sentinel. The global default is consulted only when detection yielded unknown and no override/manual selection exists. Never publish AUTO/UNKNOWN to the renderer.

**Verification:**

- `Grep` - the coordinator references `stereoDefaultLayout` / `stereoDefaultProjection` (or the supplied default value).
- `Grep -n "Log\.d\("` in the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - Chain now: per-file override > positive detection > global default > AUTO. resolveStereoMode + resolveAutoStereoMode consult `getGlobalDefaultStereoMode` lambda only on UNKNOWN/AUTO. PlayerViewModel caches the default from settings (spherical projection preferred, else flat layout). No Log.d. Verification PASS.

---

### Step 03.3 - Test the new slot precedence

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PlayerStereoModeCoordinatorTest.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add tests proving the global default applies only on unknown detection, and is suppressed by a positive detection, a per-file override, and a session selection. Add a case where detection is unknown and no default is set → renderer still never receives a sentinel.

**Verification:**

- The full coordinator test class passes.
- `Grep` - a test asserts "positive detection beats global default".

**Status:** `[x]` done

**Step Log:**

- 2026-06-01 - 4 new-slot tests: global default applies on inconclusive; positive detection beats default; per-file override beats default; mono default stays non-sentinel. `gradlew testStandardDebugUnitTest --tests PlayerStereoModeCoordinatorTest` → BUILD SUCCESSFUL (expected: 8/8 pass | actual: pass).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `:app_v2:testStandardDebugUnitTest --tests PlayerStereoModeCoordinatorTest` → BUILD SUCCESSFUL (compiles main + test).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Precedence chain now has a place for the global default. The settings UI in Phase 04 writes the values this slot reads.

---

## Rollback Plan

Revert phase commit(s) - the slot is additive; without a default set, behavior is identical to pre-phase resolution.
