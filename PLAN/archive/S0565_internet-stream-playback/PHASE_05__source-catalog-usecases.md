# Phase 05 - Source Catalog Use Cases

**Strategic spec:** [`../S0565_internet-stream-playback.md`](../S0565_internet-stream-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Domain use cases over `StreamSourceRepository`: observe the list, add a manual URL (with scheme/kind classification), import a remote `.m3u` playlist (download + parse + de-dup), pin a source to top, and remove a source.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (`StreamSourceRepository` injectable).
- [ ] Reviewed an existing `*UseCase` for the naming/structure convention (`VerbNounUseCase`, suspend `operator fun invoke`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ObserveStreamSourcesUseCase.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/AddStreamSourceUseCase.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamPlaylistUseCase.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/PinStreamSourceUseCase.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/RemoveStreamSourceUseCase.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/M3uPlaylistParser.kt` | New | ≤ 90 |

> Use cases are constructor-injectable (`@Inject`) - no Hilt module edits needed unless the project binds use cases explicitly; follow the surrounding convention.

---

## Steps

### Step 05.1 - `ObserveStreamSourcesUseCase` + `PinStreamSourceUseCase` + `RemoveStreamSourceUseCase`

**Files:** `.../usecase/streams/ObserveStreamSourcesUseCase.kt`, `PinStreamSourceUseCase.kt`, `RemoveStreamSourceUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Three thin use cases delegating to `StreamSourceRepository`: `ObserveStreamSourcesUseCase` returns `Flow<List<StreamSourceEntity>>`; `PinStreamSourceUseCase(id: String)` calls `repository.pinToTop(id)` (local favorite - strategic §3.3); `RemoveStreamSourceUseCase(source)` calls `repository.remove(source)`. Constructor-inject the repository.

**Verification:**

- `Glob` - all three files exist.
- `Grep` - `class ObserveStreamSourcesUseCase`, `class PinStreamSourceUseCase`, `class RemoveStreamSourceUseCase` each match once.

**Status:** `[x]` done (compileStandardDebugKotlin + compileLiteDebugKotlin PASS 2026-06-21)

---

### Step 05.2 - `AddStreamSourceUseCase` with scheme/kind classification

**Files:** `.../usecase/streams/AddStreamSourceUseCase.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> `AddStreamSourceUseCase(url: String, title: String?)` validates the URL scheme (`http`, `https`, `rtsp` only - reject others with a typed failure), derives `mediaKind`: `rtsp://` -> RTSP, an audio extension or no path extension -> AUDIO (radio default), a known video/HLS/DASH extension (`.m3u8`/`.mpd`/`.mp4`/...) -> VIDEO. Build a `StreamSourceEntity` (generate `id`, `sourceOrigin = MANUAL`, `addedAt = System.currentTimeMillis()`, next `sortIndex`), persist via `repository.add`. Return a sealed result (Success / InvalidUrl / Duplicate). Do not embed any `Sxxxx` ticket id in persistent logs.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class AddStreamSourceUseCase` present.
- `Grep` - scheme validation referencing `rtsp` and `http` present; a sealed/`when` result type present.

**Status:** `[x]` done (compileStandardDebugKotlin + compileLiteDebugKotlin PASS 2026-06-21)

---

### Step 05.3 - `M3uPlaylistParser`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/M3uPlaylistParser.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Pure parser: `fun parse(text: String): List<ParsedStreamEntry>` where `ParsedStreamEntry(url, title)`. Handle simple/extended `.m3u`: skip blank lines; a `#EXTINF:<dur>,<title>` line supplies the title for the next non-`#` URL line; plain non-`#` lines are bare URLs (title = url host). Ignore `.pls`/`.xspf` (deferred). Do NOT treat `.m3u8` HLS manifests as playlists - only parse when the body lacks `#EXT-X-` HLS tags (research §1: `.m3u8` ambiguity - differentiate by content, a `#EXT-X-` body is an HLS manifest the player handles directly, not a playlist to expand).

**Verification:**

- `Glob` - file exists.
- `Grep` - `#EXTINF` and `#EXT-X-` both referenced (parse vs HLS-manifest guard).
- `Grep` - `class M3uPlaylistParser` present.

**Status:** `[x]` done (compileStandardDebugKotlin + compileLiteDebugKotlin PASS 2026-06-21)

---

### Step 05.4 - `ImportStreamPlaylistUseCase` (download + parse + de-dup)

**Files:** `.../usecase/streams/ImportStreamPlaylistUseCase.kt`
**Depends on:** Step 05.2, Step 05.3

**Prompt for developer:**

> `ImportStreamPlaylistUseCase(listUrl: String)`: download the remote `.m3u` text on `Dispatchers.IO` using the project's existing HTTP client (locate the shared OkHttp/HttpURLConnection helper; do not add a new networking dependency), parse via `M3uPlaylistParser`, map each `ParsedStreamEntry` to a `StreamSourceEntity` (`sourceOrigin = IMPORTED`, classify `mediaKind` reusing the same logic as `AddStreamSourceUseCase` - extract that classification into a shared private helper or small mapper to avoid duplication), then `repository.addAllIgnoringDuplicates(...)`. Return a sealed result carrying the inserted count. Wrap network/parse failures in a typed failure with a `Timber.w` (no empty catch; no `Sxxxx` in the message).

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ImportStreamPlaylistUseCase` present.
- `Grep` - `addAllIgnoringDuplicates` invoked; `M3uPlaylistParser` referenced.
- `Grep` - no empty `catch {}` and no broad swallow without a log in this file.

**Status:** `[x]` done (compileStandardDebugKotlin + compileLiteDebugKotlin PASS 2026-06-21)

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] `Grep` - the `mediaKind` classification helper is defined once and referenced by both `AddStreamSourceUseCase` and `ImportStreamPlaylistUseCase` (no duplicated body).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

- The ViewModel (Phase 06) consumes these five use cases only - it never touches the DAO/repository directly (Clean layering).
- Import is `.m3u`-only by design; `.pls`/`.xspf` are deferred (INDEX Deferred list).

---

## Rollback Plan

Revert phase commit(s) - domain-only additions, no schema, no UI. Safe to delete the new files.
