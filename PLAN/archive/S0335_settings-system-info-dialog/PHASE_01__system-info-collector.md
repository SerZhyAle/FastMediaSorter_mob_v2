# Phase 01 - System Info Collector

**Strategic spec:** [`../S0335_settings-system-info-dialog.md`](../S0335_settings-system-info-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Introduce `GatherSystemInfoUseCase` that collects device, OS, app, memory, storage, display, and locale facts and formats them into one grouped human-readable string. No UI, no button wiring yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` | New | ≤ 150 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCaseTest.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 - Create `GatherSystemInfoUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `GatherSystemInfoUseCase` with `@Inject constructor(@ApplicationContext private val context: Context)` and `operator fun invoke(): String`. Constructor injection only - do NOT add a new Hilt `@Module`. The function gathers and returns a grouped, human-readable summary with fixed English field labels, one `key: value` per line, grouped with blank-line-separated sections: **Device** (manufacturer, model, product/device), **OS** (Android release + `SDK_INT`), **App** (`BuildConfig.VERSION_NAME`, `VERSION_CODE`, and the active flavor read read-only from `BuildConfig` - do not branch on it), **Memory** (total + available RAM via `ActivityManager.MemoryInfo`), **Storage** (total + available bytes via `StatFs` on external storage dir, formatted GB), **Display** (width×height px + density), **Locale** (current default locale tag). Wrap each system-service / `StatFs` access in a defensive try/catch so a single failing source yields an `unknown` value instead of throwing. Use `Timber` only (no `Log.d`). Do NOT embed `S0335` in any retained log line - ticket ids are reserved for the BlockNeedUserTest probe added in Phase 03.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` exists.
- `Grep` - `class GatherSystemInfoUseCase @Inject constructor` matches exactly once.
- `Grep` - `operator fun invoke(): String` present.
- `Grep -n "Log\.d\("` on the file returns zero hits.
- `Grep` - no `S0335` literal in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 5/5 PASS. Files: domain/usecase/GatherSystemInfoUseCase.kt (New, ~135 LOC). Dev log recorded.

---

### Step 01.2 - Unit test for the collector

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCaseTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a Robolectric (or context-mock) unit test that invokes the use case and asserts the returned string is non-blank and contains the expected section labels (`Device`, `OS`, `App`, `Memory`, `Storage`, `Display`, `Locale`) and the app version name. The test must not assert on dynamic values (free RAM/storage) - only on presence of labels and the static version field.

**Verification:**

- `Glob` - test file exists.
- `Grep` - `class GatherSystemInfoUseCaseTest` matches once.
- Affected unit test passes - run `testStandardDebugUnitTest --tests "*GatherSystemInfoUseCaseTest"` and inspect that class's XML report (expected: tests run ≥ 1, failures = 0).

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS. Test XML: tests=3 failures=0 errors=0 (expected failures=0 | actual=0). Files: test/.../GatherSystemInfoUseCaseTest.kt (New). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class) - deferred to Phase 04 batch sync.

---

## Handoff Notes to Next Phase

`GatherSystemInfoUseCase` is constructor-injectable and returns a ready-to-display `String`. Phase 03 field-injects it into the settings fragment and passes it to the diagnostics helper.

---

## Rollback Plan

Revert phase commit(s) - new isolated class, no data migration or user-facing surface changed.
