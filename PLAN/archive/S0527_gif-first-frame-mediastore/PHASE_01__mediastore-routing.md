# Phase 01 - MediaStore routing for GIF first-frame save

**Strategic spec:** [`../S0527_gif-first-frame-mediastore.md`](../S0527_gif-first-frame-mediastore.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** -
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Route the saved GIF first frame through the shared MediaStore-aware local writer instead of a direct `FileOutputStream` into the public Downloads directory, so the save no longer fails with EACCES on API 29+ scoped storage. The `execute(gifPath): Result<String>` signature is unchanged - callers are untouched.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveGifFirstFrameUseCase.kt` | Modified | ≤ 120 |

---

## Steps

### Step 01.1 - Delegate the write to the local destination writer

**Files:** `domain/usecase/SaveGifFirstFrameUseCase.kt`

**Prompt for developer:**

> Inject `LocalDestinationClassifier` and `LocalDestinationWriter` into the constructor (both are constructor-injectable singletons; no new Hilt module). Replace the direct `FileOutputStream(File(downloadsDir, ..))` block: build the target absolute path under `Environment.DIRECTORY_DOWNLOADS`, `classify(targetPath)`, `open(category, overwrite = true)`, compress the first frame PNG into `sink.outputStream` (do not wrap in `.use` - `commit()` flushes/closes), then `commit()` and return its `Result<String>` (the saved path/URI). On `open`/`commit` failure return `Result.failure(e)`; on a compress exception call `sink.abort()` and fail. Keep `gifDecoder.clear()` on every exit. Keep `MediaStoreNotifier.notifyFile` only under `Build.VERSION.SDK_INT < Q` (pre-Q legacy write is a plain file the system has not indexed; on API 29+ the MediaStore publish indexes it). Remove the now-unused `FileOutputStream` import.

**Verification:**

- `Grep` - `LocalDestinationWriter` and `LocalDestinationClassifier` injected in the constructor.
- `Grep` - `destinationWriter.open(` and `.commit()` present.
- `Grep` - no `FileOutputStream(` remains in the file.
- `Grep` - `MediaStoreNotifier.notifyFile` is guarded by an SDK_INT < Q check.

**Status:** `[x]` done

---

### Step 01.2 - Compile

**Files:** (verification only)

**Prompt for developer:**

> Build `standard` to confirm the refactor and Hilt wiring compile.

**Verification:**

- `.\a.ps1 fc` - code + resources compile clean.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (`.\a.ps1 fc`).
- [x] Dev log entry added for the modified file.

---

## Handoff Notes to Next Phase

The save path now goes through the shared writer. Final phase regenerates the catalog and finalises the changelog.

---

## Rollback Plan

Revert the phase commit; the use case returns to the direct `FileOutputStream` write.
