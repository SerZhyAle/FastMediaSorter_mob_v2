# Phase 01 - DB schema: per-channel track preferences

**Strategic spec:** [`../S1144_video-stream-tracks-subtitles-program.md`](../S1144_video-stream-tracks-subtitles-program.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Add additive nullable columns to `stream_sources` for the per-channel preferred audio-language, subtitle-language, and subtitles-on flag (ADR-3), with the 42→43 migration and DAO accessors. No apply/write-back logic yet.

---

## Prerequisites

- [ ] `AppDatabase` is at `@Database(version = 42)` (confirmed) - free slot 42→43.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceEntity.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration42To43.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | Modified | ≤ 100 |

---

## Steps

### Step 01.1 - Add nullable preference columns to `StreamSourceEntity`

**Files:** `data/local/db/StreamSourceEntity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Append three nullable columns after `access` (Room uses the field name as the column name, matching the existing camelCase columns): `val preferredAudioLang: String? = null`, `val preferredSubtitleLang: String? = null`, `val subtitlesEnabled: Boolean? = null`. Add a one-line WHY comment: S1144 per-channel track memory (language-code, not raw index - ADR-2); `subtitlesEnabled` null = follow global default, true/false = per-channel override.

**Verification:**

- `Grep` - `val preferredAudioLang: String? = null`, `val preferredSubtitleLang: String? = null`, `val subtitlesEnabled: Boolean? = null` all present.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Verification 1/1 PASS. Files: StreamSourceEntity.kt (+7 LOC).

---

### Step 01.2 - Add `Migration42To43`

**Files:** `data/local/db/Migration42To43.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `MIGRATION_42_43 = object : Migration(42, 43)` mirroring `Migration41To42.kt` verbatim. In `migrate()` run three additive `ALTER TABLE stream_sources ADD COLUMN` statements: `preferredAudioLang TEXT`, `preferredSubtitleLang TEXT`, `subtitlesEnabled INTEGER` (Room maps `Boolean?` to nullable INTEGER). No data backfill - all default null.

**Verification:**

- `Glob` - `Migration42To43.kt` exists.
- `Grep` - `object : Migration(42, 43)` present.
- `Grep` - all three `ADD COLUMN` statements present.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Verification 3/3 PASS. Files: Migration42To43.kt (new, 22 LOC).

---

### Step 01.3 - Bump DB version, register migration, add DAO accessor

**Files:** `data/local/db/AppDatabase.kt`, `data/local/db/StreamSourceDao.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `AppDatabase.kt` bump `version = 42` to `version = 43` and add `MIGRATION_42_43` to the `addMigrations(...)` list (find where `MIGRATION_41_42` is registered - Hilt DB module or the builder). In `StreamSourceDao.kt` add `@Query("UPDATE stream_sources SET preferredAudioLang = :audioLang, preferredSubtitleLang = :subtitleLang, subtitlesEnabled = :subtitlesEnabled WHERE url = :url") suspend fun updateTrackPreferences(url: String, audioLang: String?, subtitleLang: String?, subtitlesEnabled: Boolean?)`. Read path reuses the existing `getByUrl(url)`.

**Verification:**

- `Grep` - `version = 43` in `AppDatabase.kt`.
- `Grep` - `MIGRATION_42_43` referenced in the migrations registration.
- `Grep` - `fun updateTrackPreferences(` in `StreamSourceDao.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Verification 3/3 PASS. Files: AppDatabase.kt (version 43), DatabaseModule.kt (register MIGRATION_42_43), StreamSourceDao.kt (+updateTrackPreferences).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `standard debug` BUILD SUCCESSFUL in 1m 41s (Room schema regen OK; no duplicate-migration error).
- [x] Dev log entry added for every file in "Files Touched" (batched at ticket close via close-and-log -DevLogs).
- [x] Phase-boundary audit (Layer 4 Room): migration additive-only, version bumped once (42->43), prior migrations untouched. No findings.

---

## Handoff Notes to Next Phase

`StreamSourceEntity` now carries `preferredAudioLang`/`preferredSubtitleLang`/`subtitlesEnabled`; `getByUrl` reads them and `updateTrackPreferences` writes them by stable URL key. Phase 02 wires apply-at-start + write-back onto these.

---

## Rollback Plan

Revert the version bump + migration + columns together. No shipped build has version 43 yet, so no forward-migration to undo.
