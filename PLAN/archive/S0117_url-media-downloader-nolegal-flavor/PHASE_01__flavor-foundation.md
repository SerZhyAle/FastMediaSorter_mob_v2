# Phase 01 - Flavor Foundation

**Strategic spec:** [../S0117_url-media-downloader-nolegal-flavor.md](../S0117_url-media-downloader-nolegal-flavor.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 1 / 1
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Introduce the `noLegal` product flavor, source-set wiring, and dependency isolation required to compile the sideload-only variant without touching the downloader logic yet.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] Backup for `app_v2/build.gradle.kts` created in `temp/`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | <= 500 |

> File projected >500 lines after change -> backup step required (timestamped copy in `temp/`). File >1000 lines -> split via Manager pattern first.

---

## Steps

### Step 01.1 - Add the noLegal flavor foundation

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the sideload-only `noLegal` flavor with `applicationIdSuffix = ".nolegal"`, `versionNameSuffix = "-NoLegal"`, and `BuildConfig.IS_NO_LEGAL_FLAVOR = true`. Wire the flavor to `src/streamingEnabled/java` and isolate the NewPipe GPL dependency behind `noLegalImplementation` so market flavors never resolve it.

**Verification:**

- `Grep` - `create("noLegal")` present in `app_v2/build.gradle.kts`.
- `Grep` - `buildConfigField("boolean", "IS_NO_LEGAL_FLAVOR", "true")` present in `app_v2/build.gradle.kts`.
- `Grep` - `"noLegalImplementation"("com.github.TeamNewPipe:NewPipeExtractor:v0.24.0")` present in `app_v2/build.gradle.kts`.
- `Command` - `./gradlew.bat :app_v2:compileNoLegalDebugKotlin` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification 4/4 PASS. Files: `app_v2/build.gradle.kts`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles for the touched slice via `:app_v2:compileNoLegalDebugKotlin`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] No catalog regen required because only Gradle flavor wiring changed.

---

## Handoff Notes to Next Phase

`noLegal` now exists as a first-class flavor and can host `src/noLegal/` Kotlin/res files without leaking GPL code into market variants.