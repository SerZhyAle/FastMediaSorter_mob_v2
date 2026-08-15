# Phase 01 - Foundation: section model + localized labels

**Strategic spec:** [`../S0337_system-info-extended-fields.md`](../S0337_system-info-extended-fields.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Introduce a section data model and refactor `GatherSystemInfoUseCase` to build the summary from a list of sections with localized labels, migrating the existing S0335 fields without changing visible content beyond label source.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/systeminfo/SystemInfoSection.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCaseTest.kt` | Modified | ≤ 150 |

---

## Steps

### Step 01.1 - Add `SystemInfoSection` data model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/systeminfo/SystemInfoSection.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a small data holder `SystemInfoSection(val title: String, val fields: List<Pair<String, String>>)` and a top-level function or extension that renders a `List<SystemInfoSection>` into the existing grouped text format (title line, then `  key: value` per field, blank line between sections). No Android dependencies in this file. Timber not required.

**Verification:**

- `Glob` - `SystemInfoSection.kt` exists.
- `Grep` - `data class SystemInfoSection` matches once.
- `Grep` - `val fields: List<Pair<String, String>>` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS. Files: core/systeminfo/SystemInfoSection.kt (New). Dev log recorded.

---

### Step 01.2 - Add localized section/field label strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add localized string keys for the existing S0335 sections and fields under a `sysinfo_` prefix: section headers `sysinfo_section_device`, `sysinfo_section_user`, `sysinfo_section_os`, `sysinfo_section_app`, `sysinfo_section_memory`, `sysinfo_section_storage`, `sysinfo_section_display`, `sysinfo_section_locale`, `sysinfo_section_time`; and field labels for the fields already present (manufacturer, model, product, user name, android version, api level, app version, build, flavor, total/available RAM, total/available storage, resolution, density, current locale, current time, timezone). Provide real EN/RU/UK values; apply Author Style (`..`, `ё`/`Ё`). Run new strings through `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `Grep` - `sysinfo_section_device` present in each of the three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "sysinfo_"` exits 0 (expected 0 | actual record).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. 27 sysinfo_ keys present EN/RU/UK; check_strings_localized EXIT=0 (expected 0 | actual 0). Neutral descriptive labels pass §6. Dev log recorded.

---

### Step 01.3 - Refactor use case to section-based localized build

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCaseTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Refactor `invoke()` to assemble a `List<SystemInfoSection>` and render it via the Step 01.1 helper. Each section is built by its own private function wrapped in the existing defensive `safe`/`safeList` pattern. Replace the hard-coded English section/field labels with `context.getString(R.string.sysinfo_...)`. Keep all existing S0335 fields and values; only the label source changes. Keep `invoke(): String` signature (the caller already runs it on `Dispatchers.IO`). Update the unit test: assert the rendered string contains the localized section header values for device/os/app/time (read via a test context) and still contains the app version name.

**Verification:**

- `Grep` - `context.getString(R.string.sysinfo_section_device)` (or equivalent localized lookup) present in the use case.
- `Grep -n "Log\.d\("` on the use case returns zero hits.
- Affected unit test passes - run `testStandardDebugUnitTest --tests "*GatherSystemInfoUseCaseTest"`; XML report failures=0 (expected 0 | actual record).

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. Localized lookup via `label(R.string.sysinfo_section_device)` -> context.getString (equivalent). Log.d=0. Test XML tests=3 failures=0 errors=0 (expected 0 | actual 0). Fixed unescaped UK apostrophes (Пам\'ять, Ім\'я) that broke resource compilation. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public type) - deferred to Phase 06 batch.

---

## Handoff Notes to Next Phase

Sections are now data-driven with localized labels. New phases add sections by adding a private builder + its `sysinfo_` strings, then appending the section to the list.

---

## Rollback Plan

Revert phase commit(s) - new data holder + internal refactor; visible content equals S0335 (labels localized).
