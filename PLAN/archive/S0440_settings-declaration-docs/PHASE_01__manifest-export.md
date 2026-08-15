# Phase 01 - Manifest Export

**Strategic spec:** [`../S0440_settings-declaration-docs.md`](../S0440_settings-declaration-docs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-17
**Completed:** 2026-06-19

---

## Objective

Emit a committed, machine-readable settings manifest produced by the existing in-app scan pipeline (no parallel logic), with trilingual titles per entry; no annotations, docs, or gate yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Robolectric is available for JVM unit tests (verified: `org.robolectric:robolectric:4.11.1`, `isIncludeAndroidResources = true`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsManifestExportTest.kt` | New | ≤ 250 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsManifestSerializer.kt` | New | ≤ 150 |
| `docs/settings/settings-manifest.json` | New (generated) | n/a |
| `app_v2/build.gradle.kts` | Modified | ≤ +6 |

> Manifest is a generated artifact: it is produced by the test, not hand-edited. The serializer lives in the test source set because the manifest is a build/doc artifact, not runtime app behavior (ADR-1: the in-app index stays the single source of truth - the test only re-uses it).

---

## Steps

### Step 01.1 - Define the manifest serializer

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsManifestSerializer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a small, dependency-free serializer that turns an ordered list of manifest entries into stable, pretty-printed JSON (2-space indent, keys in fixed order, entries sorted by `sectionId` then `key`) so byte-diffs are meaningful. Each entry carries: `key`, `sectionId`, `destination`, `layout` (resource entry name), `viewId` (resource entry name, not the int), `kind`, `titleEn`, `titleRu`, `titleUk`. Use `org.json.JSONObject`/`JSONArray` (already on the Android/Robolectric classpath) or manual string building - do not add a new dependency.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsManifestSerializer.kt` exists.
- `Grep` - `fun serialize(` present.
- `Grep` - no `import com.fasterxml`, no `import kotlinx.serialization` (no new dependency).

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. Files: SettingsManifestSerializer.kt (+78 LOC), incl. SettingsManifestEntry model. Hand-rolled JSON for stable key order. `key` = viewId resource name (separate int viewId field dropped as redundant - key already is the viewId name). Dev log recorded.

---

### Step 01.2 - Resolve titles in EN, RU, UK from the real scan

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsManifestExportTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Write a Robolectric test that instantiates `LayoutSettingsSearchSource` with the Robolectric application context and calls `collect()` to obtain `RawSettingsSearchEntry` list - this re-uses the exact scan the app ships. For each raw entry, derive `sectionId`/`destination` via `SettingsSearchTabMapping.assignmentFor(layoutResId)` and resolve the title text in EN, RU, UK by reading `titleResId` (or the inline title) under a context configured for each locale. Use the entry `viewId` resource name as the stable `key`. Skip entries that resolve to no text in any locale (mirror the pipeline's own filter).

**Verification:**

- `Grep` - `@RunWith(RobolectricTestRunner::class)` present.
- `Grep` - `LayoutSettingsSearchSource(` constructed in the test.
- `Grep` - `SettingsSearchTabMapping` referenced.
- `Grep` - all three locale tags `"en"`, `"ru"`, `"uk"` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS. Files: SettingsManifestExportTest.kt (+88 LOC). Reuses LayoutSettingsSearchSource scan + per-locale createConfigurationContext (mirrors LocalizedKeywordCollector). Context via RuntimeEnvironment.getApplication() (repo-standard; ApplicationProvider not on test classpath). Minimal non-empty assertion; generate/verify added in 01.3. Dev log recorded.

---

### Step 01.3 - Generate-or-verify mode and write the manifest

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsManifestExportTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In the same test, serialize the resolved entries and compare against the committed `docs/settings/settings-manifest.json`. Default (verify) mode: fail the test if the committed file is missing or differs, printing a unified-style hint. Generate mode: when system property `settings.manifest.generate=true` is set, (re)write the committed file instead of asserting. This single test is both the generator and the freshness check the gate will call.

**Verification:**

- `Grep` - `settings.manifest.generate` present in the test.
- `Grep` - `docs/settings/settings-manifest.json` path referenced in the test.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Files: SettingsManifestExportTest.kt (+38 LOC). Added `committed manifest is fresh` test with generate (`-Dsettings.manifest.generate=true`) / verify modes; repo root resolved by walking up to settings.gradle.kts. Dev log recorded.

---

### Step 01.4 - Produce the committed manifest

**Files:** `docs/settings/settings-manifest.json`
**Depends on:** Step 01.3

**Prompt for developer:**

> Gradle does not forward `-D` system properties to the test JVM by default; first add a one-line forwarder in `app_v2/build.gradle.kts` `testOptions.unitTests` so the `settings.manifest.generate` property reaches the test. Then run the export in generate mode to create the committed manifest, and re-run in verify mode to confirm it is stable. Commands: `./gradlew.bat testStandardDebugUnitTest --tests "*SettingsManifestExportTest" -Dsettings.manifest.generate=true` then the same without the `-D` flag.

**Verification:**

- `Glob` - `docs/settings/settings-manifest.json` exists.
- `Grep` - `"titleEn"`, `"titleRu"`, `"titleUk"` and `"sectionId"` present in the manifest.
- Test report XML for `SettingsManifestExportTest` shows the verify run passing (per-class report under `app_v2/build/test-results/testStandardDebugUnitTest/`).
- Record `expected: verify run PASS | actual: <PASS/FAIL>`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Manifest GENERATED + content-verified. Generate run (`-Dsettings.manifest.generate=true`) = full standard main + test compile SUCCESS (47s), produced `docs/settings/settings-manifest.json` (165 entries; all of titleEn/titleRu/titleUk/sectionId/destination/kind/layout present; RU/UK real translations, not EN fallback). Build forwarder added to build.gradle.kts. expected: verify run PASS | actual: BLOCKED - isolated verify run cannot compile `:app_v2` due to UNRELATED concurrent WIP in `app_v2/src/main/.../ui/statistics/StatisticsRowFormatter.kt`. Step held `[~]` until main compiles clean.
- 2026-06-19 - Unblocked: main tree now compiles (`.\a.ps1 fk` PASS). First verify run FAILED (ComparisonFailure) - committed manifest stale after 2 days of concurrent settings WIP. Regenerated (`-Dsettings.manifest.generate=true`) -> 171 entries (was 165; +80/-20 lines; 6 net new settings), then re-ran verify mode. expected: verify run PASS | actual: PASS (BUILD SUCCESSFUL). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `SettingsManifestExportTest` passes in verify mode (per-class XML report).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new test classes) via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`docs/settings/settings-manifest.json` is the canonical key set. Every key here must receive a trilingual annotation in Phase 02. The `sectionId` values are the join key for flavor availability in Phase 03.

---

## Rollback Plan

Revert phase commit(s) - delete the test, serializer, and the generated manifest. No data migration or user-facing surface changed.
