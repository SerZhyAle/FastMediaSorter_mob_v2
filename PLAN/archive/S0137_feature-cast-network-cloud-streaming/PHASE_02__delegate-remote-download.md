# Phase 02 — Delegate remote download to NetworkFileManager

**Strategic spec:** [`../S0137_feature-cast-network-cloud-streaming.md`](../S0137_feature-cast-network-cloud-streaming.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Replace the `openRemoteInputStream` stub and bespoke `downloadToTemp` logic with a single delegation to `NetworkFileManager.prepareFileForRead(file)`. After this phase, casting an SMB / SFTP / FTP / Cloud file produces a working `localFile` instead of `null`, removing the warn `openRemoteInputStream — network/cloud download not yet wired`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `NetworkFileManager.prepareFileForRead` is reachable from `CastMediaManager` via the constructor reference.
- [ ] `UnifiedFileCache` is the canonical sink for downloaded network files (verified in `NetworkFileManager.downloadNetworkFileForRead`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt` | Modified | ≤ 360 |

---

## Steps

### Step 02.1 — Replace `downloadToTemp` body with delegation to `NetworkFileManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Rewrite `private suspend fun downloadToTemp(file: MediaFile): File?` to delegate to `NetworkFileManager`:
>
> ```kotlin
> private suspend fun downloadToTemp(file: MediaFile): File? = withContext(Dispatchers.IO) {
>     try {
>         Timber.d("S0137: CastMediaManager.downloadToTemp — delegating to NetworkFileManager for ${file.name}")
>         networkFileManager.prepareFileForRead(file)
>     } catch (e: Exception) {
>         Timber.e(e, "CastMediaManager: download failed for ${file.name}")
>         null
>     }
> }
> ```
>
> Do not change any callers of `downloadToTemp`. The return contract (`File?`) is preserved.

**Verification:**

- `Grep` — `networkFileManager.prepareFileForRead\(file\)` matches exactly once in `CastMediaManager.kt`.
- `Grep` — `Timber.d\("S0137: CastMediaManager.downloadToTemp` matches exactly once.
- `Grep` — `FileOutputStream\(tempFile\)` returns zero matches in `CastMediaManager.kt`.
- `Grep` — `\$CAST_TEMP_FILE_NAME\.\$ext` returns zero matches in `CastMediaManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS. Files: ui/player/helpers/CastMediaManager.kt (-13 LOC). Dev log recorded.

---

### Step 02.2 — Remove the obsolete `openRemoteInputStream` stub and warn log

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Delete the entire `private fun openRemoteInputStream(file: MediaFile): InputStream?` method (currently around lines 339–347). Remove the now-unused imports `java.io.FileOutputStream` and `java.io.InputStream`. The warn message `CastMediaManager: openRemoteInputStream — network/cloud download not yet wired` must no longer be searchable.

**Verification:**

- `Grep` — `openRemoteInputStream` returns zero matches across the entire `app_v2/` source tree.
- `Grep` — `network/cloud download not yet wired` returns zero matches in `app_v2/`.
- `Grep` — `import java.io.FileOutputStream` returns zero matches in `CastMediaManager.kt`.
- `Grep` — `import java.io.InputStream` returns zero matches in `CastMediaManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS. Files: ui/player/helpers/CastMediaManager.kt (-15 LOC, deleted openRemoteInputStream stub + 2 unused imports). Dev log recorded.

---

### Step 02.3 — Retire the unused `cast_current` temp-file helpers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Delete `private fun deleteTempFile()` and remove its two call sites (`release()` and `handleSessionEnd()`). Also delete the `private const val CAST_TEMP_FILE_NAME = "cast_current"` line in the companion object. Rationale: `NetworkFileManager` now writes through `UnifiedFileCache`, which manages its own LRU and is shared with the player — `CastMediaManager` must not delete cache files.

**Verification:**

- `Grep` — `deleteTempFile` returns zero matches across `app_v2/`.
- `Grep` — `CAST_TEMP_FILE_NAME` returns zero matches across `app_v2/`.
- `Grep` — `cast_current` returns zero matches in `CastMediaManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `CastMediaManager.kt` (Timber-only invariant preserved).

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS. Files: ui/player/helpers/CastMediaManager.kt (-15 LOC, removed deleteTempFile, CAST_TEMP_FILE_NAME, two call sites). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `CastMediaManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] Manual smoke test (or note in handoff): `BlockNeedUserTest` for cast SMB / SFTP / FTP / Cloud file — receiver loads media; no `cast_error_file` toast.

---

## Handoff Notes to Next Phase

After Phase 02, `CastMediaManager` works for any source type that `NetworkFileManager` understands (SMB / SFTP / FTP / Cloud). Phase 03 layers size gates on top to prevent runaway downloads of large audio / image files. Cloud streaming (strategic F3) is implicitly delivered by Phase 02 — no separate phase needed.

---

## Rollback Plan

Revert the phase commit. The previous behaviour (network/cloud cast → `cast_error_file` toast) is restored; no data migration involved.
