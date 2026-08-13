# Phase 01 - Now-playing parse + format foundation

**Strategic spec:** [`../S1142_audio-stream-metadata-display.md`](../S1142_audio-stream-metadata-display.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Introduce a pure `NowPlayingMetadata` model with a best-effort ICY-string parser, extend `StreamTitleFormatter` with a single-string now-playing line, and cover both with unit tests. No player, notification, or UI wiring in this phase.

---

## Prerequisites

- [ ] Strategic §6 Q1/Q2 resolved (they are).
- [ ] Working tree state acknowledged (single-dev WIP is normal).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/NowPlayingMetadata.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamTitleFormatter.kt` | Modified | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/playback/NowPlayingMetadataTest.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamTitleFormatterTest.kt` | New / Modified | ≤ 120 |

---

## Steps

### Step 01.1 - Add `NowPlayingMetadata` model + ICY parser

**Files:** `core/playback/NowPlayingMetadata.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a pure Kotlin `data class NowPlayingMetadata(val artist: String?, val title: String)` in package `com.sza.fastmediasorter.core.playback`. Add a `companion object` with `fun parse(streamTitle: String): NowPlayingMetadata?` implementing ADR-2: trim input; return `null` for blank; split on the FIRST `" - "` separator into `(artist, title)` with both sides trimmed; when there is no separator (or a side is blank) put the whole string in `title` and leave `artist = null`. Add `fun trackLine(): String` returning `"$artist - $title"` when artist is non-blank, else `title`. No Android imports - keep it JVM-pure so it unit-tests without Robolectric.

**Verification:**

- `Glob` - `core/playback/NowPlayingMetadata.kt` exists.
- `Grep` - `data class NowPlayingMetadata` matches exactly once.
- `Grep` - `fun parse(streamTitle: String): NowPlayingMetadata?` present.
- `Grep` - `fun trackLine()` present.
- `Grep -n "import android"` in the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Verification 5/5 PASS. Files: core/playback/NowPlayingMetadata.kt (new, 39 LOC). Dev log recorded.

---

### Step 01.2 - Extend `StreamTitleFormatter` with the now-playing line (ADR-3/ADR-5)

**Files:** `ui/streams/StreamTitleFormatter.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fun nowPlayingLine(stationTitle: String, meta: NowPlayingMetadata?): String` to the existing `object StreamTitleFormatter`. It must reuse `display(stationTitle)` for the station segment (S0691 dedup), then append the track: when `meta` is null or its `trackLine()` is blank return the station alone; otherwise return `"$station - ${meta.trackLine()}"` (single string, no visual field separation - ADR-5). Import `com.sza.fastmediasorter.core.playback.NowPlayingMetadata`. Do not mutate stored entity titles - this is display-only.

**Verification:**

- `Grep` - `fun nowPlayingLine(stationTitle: String, meta: NowPlayingMetadata?): String` present.
- `Grep` - `display(stationTitle)` referenced inside `nowPlayingLine`.
- `/build` -> `standard debug` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - Grep 2/2 PASS (compile validated at phase-end build). Files: StreamTitleFormatter.kt (+14 LOC). Dev log recorded.

---

### Step 01.3 - Unit tests for parser + formatter (criterion 6)

**Files:** `core/playback/NowPlayingMetadataTest.kt`, `ui/streams/StreamTitleFormatterTest.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> Write JUnit4 tests. `NowPlayingMetadataTest`: `"Artist - Title"` -> `artist="Artist", title="Title"`; string with no `" - "` -> `artist=null, title=<whole>`; blank -> `null`; leading/trailing spaces trimmed; a value with multiple `" - "` splits only on the first; `trackLine()` returns `"Artist - Title"` and falls back to bare title when artist null. `StreamTitleFormatterTest` (create if absent, else extend): `nowPlayingLine` with null meta returns `display(station)`; with a parsed meta returns `"station - Artist - Title"`; a `Name (Name)` station still dedups via `display`. Run the suite.

**Verification:**

- `Glob` - both test files exist.
- `Grep` - `class NowPlayingMetadataTest` and `class StreamTitleFormatterTest` present.
- `.\a.ps1 fu` (or `.\gradlew.bat testStandardDebugUnitTest --tests "*NowPlayingMetadataTest" --tests "*StreamTitleFormatterTest"`) - both classes pass.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - testStandardDebugUnitTest (both classes) BUILD SUCCESSFUL. Files: NowPlayingMetadataTest.kt (new), StreamTitleFormatterTest.kt (+4 tests). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `/build` -> `standard debug`.
- [ ] New parser + formatter tests pass.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 04 (new public `NowPlayingMetadata` class).
- [ ] Phase-boundary audit - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`NowPlayingMetadata.parse` / `.trackLine()` and `StreamTitleFormatter.nowPlayingLine` are the shared contract consumed by the service (Phase 02) and the inline/grid surfaces (Phase 03).

---

## Rollback Plan

Revert phase commit(s) - new pure files + one added formatter function, no data migration or user-facing surface changed.
