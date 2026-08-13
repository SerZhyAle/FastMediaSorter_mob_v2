# Phase 04 — string-resources

**Strategic spec:** [`../S0070_device-storage-info-settings.md`](../S0070_device-storage-info-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Add localized string resources for device storage info in English, Russian, and Ukrainian (`values/strings.xml`, `values-ru/strings_ru.xml`, `values-uk/strings_uk.xml`).

---

## Prerequisites

- [ ] `app_v2/src/main/res/values/strings.xml` exists.
- [ ] `app_v2/src/main/res/values-ru/strings_ru.xml` exists.
- [ ] `app_v2/src/main/res/values-uk/strings_uk.xml` exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-ru/strings_ru.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-uk/strings_uk.xml` | Modified | ≤ 1500 |

---

## Steps

### Step 04.1 — Add English strings to `values/strings.xml`

**Files:** `app_v2/src/main/res/values/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/src/main/res/values/strings.xml`, add two string resources:
> 
> ```xml
> <string name="device_storage_available">Device storage available:</string>
> <string name="btn_refresh_storage">Refresh storage info</string>
> ```

**Verification:**

- `Grep` — `<string name="device_storage_available">` found in `strings.xml`.
- `Grep` — `<string name="btn_refresh_storage">` found in `strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: values/strings.xml (+4 LOC). Dev log recorded.

---

### Step 04.2 — Add Russian strings to `values-ru/strings_ru.xml`

**Files:** `app_v2/src/main/res/values-ru/strings_ru.xml`
**Depends on:** — start of phase (parallel with Step 04.1)

**Prompt for developer:**

> In `app_v2/src/main/res/values-ru/strings_ru.xml`, add the Russian equivalents:
> 
> ```xml
> <string name="device_storage_available">На устройстве доступно:</string>
> <string name="btn_refresh_storage">Обновить информацию о месте</string>
> ```

**Verification:**

- `Glob` — `app_v2/src/main/res/values-ru/strings_ru.xml` exists.
- `Grep` — `<string name="device_storage_available">` found in `strings_ru.xml`.
- `Grep` — `<string name="btn_refresh_storage">` found in `strings_ru.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: values-ru/strings.xml (+4 LOC). Dev log recorded.

---

### Step 04.3 — Add Ukrainian strings to `values-uk/strings_uk.xml`

**Files:** `app_v2/src/main/res/values-uk/strings_uk.xml`
**Depends on:** — start of phase (parallel with Step 04.1)

**Prompt for developer:**

> In `app_v2/src/main/res/values-uk/strings_uk.xml`, add the Ukrainian equivalents:
> 
> ```xml
> <string name="device_storage_available">На пристрої доступно:</string>
> <string name="btn_refresh_storage">Оновити інформацію про місце</string>
> ```

**Verification:**

- `Glob` — `app_v2/src/main/res/values-uk/strings_uk.xml` exists.
- `Grep` — `<string name="device_storage_available">` found in `strings_uk.xml`.
- `Grep` — `<string name="btn_refresh_storage">` found in `strings_uk.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: values-uk/strings.xml (+4 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles: run `/build`.
- [ ] All three string resources exist in their respective files (EN/RU/UK).
- [ ] `Grep -n "TODO(phase-04)"` returns zero hits.
- [ ] Dev log entries added:
  - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "feature" "Add device storage info strings (EN)"`
  - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings_ru.xml" "feature" "Add device storage info strings (RU)"`
  - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings_uk.xml" "feature" "Add device storage info strings (UK)"`

---

## Handoff Notes to Next Phase

**Invariants established:**
- String resources exist in all three language variants.
- Layout and Fragment can now reference these strings without compile errors.
- UI is feature-complete and localized.

**Next phase (Phase 05):**
- Update `docs/FEATURES.md` + `FEATURES_RU.md` + `FEATURES_UK.md` with the feature description.
- Regenerate `dev/CATALOG/app_v2.jsonl` and `.md` if public API changed.
- Run final dev log entry for the spec.

---

## Rollback Plan

Revert the three string resource additions. Fragment and layout will continue to reference these strings, but they will become compile-time errors until Phase 05 resolves them.
