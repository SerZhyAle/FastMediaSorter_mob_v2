# S0705 - Remove orphan SettingsManager (DataStore key duplication hazard)

**Ticket:** S0705
**Status:** Archived
**Priority:** 70
**Date:** 2026-06-26
**Tier:** Ad-hoc (tech-debt / hazard)
**Source:** Parked by S0703 shared-state mutation audit (stage 2 data adjudication, confirmed REAL).

<!-- auto-approved by /spec-all - 2026-06-26 -->

## Goal

Убрать дремлющую угрозу split-brain записи в единый `DataStore<Preferences>` ("settings"). Класс `SettingsManager` - orphan: помечен `@Inject`, но не инжектится нигде в рантайме, при этом дублирует 14 ключей активного писателя `SettingsRepositoryImpl` с расходящимися дефолтами. Любой будущий `@Inject SettingsManager` мгновенно создал бы второго несогласованного писателя. Удаляем класс и его тест целиком (Rule 20/21), снимаем висячую ссылку из комментария.

## 0. Raw finding (audit evidence)

The app has a single `@Singleton DataStore<Preferences>` (file `"settings"`, `AppModule.provideDataStore()`). Two independent writer classes declare overlapping keys on it:
- `SettingsRepositoryImpl` - active production writer (`saveSettings()` writes ~80 keys in one `edit {}`).
- `SettingsManager` (`data/local/preferences/SettingsManager.kt`) - 31 setter methods, but NOT injected anywhere in runtime (orphan). Still compiles; Hilt would wire it on first `@Inject`.

14 keys are declared and written by BOTH: `language`, `show_player_hint_on_first_run`, `show_detailed_errors`, `text_size_max`, `support_text`, `support_pdf`, `crop_images_to_fullscreen`, `use_trash`, `enable_undo`, `max_recipients`, `enable_favorites`, `copy_panel_collapsed`, `move_panel_collapsed`, `enable_statistics`.

Defaults diverge (e.g. `supportText`: SettingsManager false vs SettingsRepositoryImpl true) -> split-brain if both ever used. Code comment already admits the overlap: `SettingsRepositoryImpl.kt:185` ("Same DataStore key as SettingsManager.ENABLE_STATISTICS").

## 1. Problem

Orphaned `SettingsManager` is a dormant hazard: one `@Inject SettingsManager` anywhere creates a second uncoordinated writer to the same DataStore keys with different defaults (split-brain). It is also dead weight (Rule 20/21).

## 2. Decision

**Remove `SettingsManager` entirely.** Verified truly orphan - no runtime injection, no DI binding, no keep/proguard reference; the preferred direction in the parked draft. Folding into `SettingsRepositoryImpl` is unnecessary: that class already owns every shared key.

### Evidence (research, 2026-06-26)

- Whole-`app_v2/src` grep for `SettingsManager` (the bare class, not the unrelated `PlayerSettingsManager` / `StandalonePlayerSettingsManager` / `DefaultPlayerSettingsManager`) -> only 3 references:
  1. its own definition `data/local/preferences/SettingsManager.kt:44`,
  2. its unit test `data/local/preferences/SettingsManagerTest.kt` (constructs it directly, not via Hilt),
  3. a stale comment in `SettingsRepositoryImpl.kt:185`.
- `SettingsManager.kt` also declares its own `data class AppSettings` in package `data.local.preferences`. Zero external imports of `com.sza.fastmediasorter.data.local.preferences.AppSettings`; the production model is the unrelated `domain.model.AppSettings` (122 files). Deleting the file removes the shadow safely.
- No `@Provides` / `@Binds SettingsManager`, no proguard/keep/xml reference (non-`.kt` grep empty).
- Package `data/local/preferences/` keeps other members after deletion (no empty-dir cleanup).

## 3. Phases

### Phase 1 - Delete orphan class, its test, and the stale reference

1. Delete `app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/SettingsManager.kt` (removes orphan `SettingsManager` class and orphan `data.local.preferences.AppSettings`).
   - Verification: file absent; `Grep "class SettingsManager "` over `app_v2/src/main` returns nothing.
2. Delete `app_v2/src/test/java/com/sza/fastmediasorter/data/local/preferences/SettingsManagerTest.kt`.
   - Verification: file absent; `Grep "SettingsManager"` over `app_v2/src` returns only the comment fixed in step 3 (none after the fix) and the unrelated `*PlayerSettingsManager` family.
3. In `SettingsRepositoryImpl.kt:185`, drop the dangling clause referencing `SettingsManager.ENABLE_STATISTICS`; keep the `S0473` privacy rationale (the WHY).
   - Verification: line no longer mentions `SettingsManager`; comment still explains opt-in default-OFF.
4. Build gate: `standard debug` compiles (`.\a.ps1 dq`).
   - Verification: BUILD SUCCESSFUL; no unresolved-reference to `SettingsManager` / `data.local.preferences.AppSettings`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0703 (parent audit that parked this finding).
- **Data / persistence impact:** n/a - dead-code removal. The orphan never executes at runtime; no DataStore write path changes. All 14 shared keys stay solely owned by `SettingsRepositoryImpl`; no migration, no stored-value change.

## Related

- Parent audit: S0703.

## Last Audit

**Date:** 2026-06-26
**Verdict:** Verified
**Auditor:** /spec-all (Simple path, inline impl)

Phase 1 predicates, all mechanically confirmed:

- `SettingsManager.kt` removed - expected: file absent | actual: deleted; `Grep "class SettingsManager "` over `app_v2/src/main` returns nothing.
- `SettingsManagerTest.kt` removed - expected: file absent | actual: deleted.
- No standalone `SettingsManager` reference left - expected: only the unrelated `*PlayerSettingsManager` family | actual: regex `(^|[^a-zA-Z])SettingsManager\b` over `app_v2/src` returns zero matches.
- Stale comment fixed in `SettingsRepositoryImpl.kt:185` - expected: no `SettingsManager` mention, S0473 privacy rationale kept | actual: now `// S0473: opt-in local usage statistics (default OFF for privacy).`
- Build gate `standard debug` (`.\a.ps1 dq`) - expected: BUILD SUCCESSFUL, no unresolved-reference | actual: BUILD SUCCESSFUL in 3m 34s; only pre-existing Media3/PauseAwareLoadControl deprecation warnings, none related to this change.

No residual gaps. The split-brain hazard is removed: all 14 previously-shared DataStore keys are now solely owned by `SettingsRepositoryImpl`. No runtime behavior or persisted data changed (orphan never executed). No user-visible capability delivered -> no `ALL_FEATURES` record.
