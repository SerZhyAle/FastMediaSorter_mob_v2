# Phase 01 - stream-resume-store

**Strategic spec:** [`../S1152_resume-stream-on-launch.md`](../S1152_resume-stream-on-launch.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-22
**Completed:** 2026-07-22

---

## Objective

Introduce a standalone, prefs-backed persistence for the last active stream (`StreamResumeState` model + `StreamResumeStateRepository` interface + SharedPreferences impl + Hilt binding). No save points and no startup routing yet.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StreamResumeState.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/StreamResumeStateRepository.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamResumeStateRepositoryImpl.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt` | Modified | ≤ 500 |

---

## Steps

### Step 01.1 - Add `StreamResumeState` domain model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StreamResumeState.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a `data class StreamResumeState` with fields: `url: String`, `title: String`, `mediaKind: String` (the persisted `StreamSourceEntity.mediaKind`, "AUDIO" or "VIDEO"), `wasPlaying: Boolean`, `savedAt: Long`. Pure domain type, no Android imports. Add a brief KDoc: persisted snapshot of the last active stream, used to resume it on cold start (S1152).

**Verification:**

- `Glob` - the file exists.
- `Grep` - `data class StreamResumeState` matches exactly once.
- `Grep` - `val mediaKind: String` and `val wasPlaying: Boolean` present.

**Status:** `[x]` done

---

### Step 01.2 - Add `StreamResumeStateRepository` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/StreamResumeStateRepository.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `interface StreamResumeStateRepository` with three suspend functions: `suspend fun save(state: StreamResumeState)`, `suspend fun get(): StreamResumeState?`, `suspend fun clear()`. Single-slot (no windowId - there is only one "last active stream"). Add a `companion object` with `const val RESUME_TTL_MS = 48L * 60 * 60 * 1000` to mirror the media resume TTL.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `interface StreamResumeStateRepository` matches exactly once.
- `Grep` - `suspend fun save`, `suspend fun get`, `suspend fun clear` all present.
- `Grep` - `RESUME_TTL_MS` present.

**Status:** `[x]` done

---

### Step 01.3 - Add `StreamResumeStateRepositoryImpl` (SharedPreferences)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamResumeStateRepositoryImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `class StreamResumeStateRepositoryImpl @Inject constructor(@param:ApplicationContext private val context: Context) : StreamResumeStateRepository`. Mirror `ResumeStateRepositoryImpl`: back it with a single `SharedPreferences` named `"stream_resume_state_prefs"`; do all reads/writes on `withContext(Dispatchers.IO)`. `save` writes url/title/mediaKind/wasPlaying/savedAt. `get` returns null when the url key is absent, otherwise reconstructs `StreamResumeState`. `clear` calls `edit().clear().apply()`. Log via `Timber.d` only. Keep every log line ≤ 120 chars.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class StreamResumeStateRepositoryImpl` matches exactly once.
- `Grep` - `: StreamResumeStateRepository` present (implements interface).
- `Grep -n "Log\.d\("` - zero hits in this file.

**Status:** `[x]` done

---

### Step 01.4 - Bind repository in Hilt `RepositoryModule`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/RepositoryModule.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `RepositoryModule`, add an `@Binds abstract fun bindStreamResumeStateRepository(impl: StreamResumeStateRepositoryImpl): StreamResumeStateRepository`, mirroring the existing `bindResumeStateRepository`. Add the two required imports.

**Verification:**

- `Grep` - `bindStreamResumeStateRepository` matches exactly once.
- `Grep` - `import ..StreamResumeStateRepositoryImpl` and `import ..StreamResumeStateRepository` present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes) - deferred to Phase 04.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`StreamResumeStateRepository` is now injectable. Phase 02 injects it into the streams flow to write/clear the record; Phase 03 injects it into the startup helper to read it.

---

## Rollback Plan

Revert the phase commit(s) - three new files + one `@Binds` line; no data migration or user-facing surface changed.
