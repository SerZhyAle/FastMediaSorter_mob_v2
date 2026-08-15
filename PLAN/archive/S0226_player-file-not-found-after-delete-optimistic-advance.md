# Strategic Specification: S0226 — Player "Current File Not Found" After Delete + Optimistic Advance

**Ticket:** S0226
**Status:** Verified
<!-- auto-approved by /spec-all — 2026-05-16 -->
**Priority:** 60
**Date:** 2026-05-16

---

## 1. Problem

After deleting a file from within `PlayerActivity` (which triggers optimistic advance to the next file), reopening the player causes a "Current file not found" warning because `BrowseResourceStateManager` still holds the deleted file's path as `lastViewedFile`.

Evidence from `logs/fastmediasorter_20260516_045552.log`:

```
[10059] FileOperationsHandler.deleteCurrentFile: shouldConfirm=true
[10061] MediaFilesCache: Removed file .../573323465_...jpg from resource 7 (186 files remaining)
[10062] NEXT triggered by: Pre-delete: stop and optimistic advance
[10769] W/App: Current file not found: .../573323465_...jpg, trying initialFilePath
```

Sequence:

1. User deletes a file in `PlayerActivity`.
2. `MediaFilesCache.remove(path)` removes the file from the in-memory list.
3. `PlayerViewModel.nextFile()` is called (optimistic advance) → player moves to the next item.
4. `BrowseResourceStateManager.saveLastViewedFile(path)` is NOT called for the next file at this point (the advance may happen before `loadMediaFiles` runs in the new session).
5. `PlayerActivity` is recreated or a new one opens. `PlayerViewModel.loadMediaFiles()` queries `BrowseResourceStateManager.getLastViewedFile()` → gets the deleted file's path → file not in the current file list → `W: Current file not found`.
6. Player recovers via `initialFilePath` fallback, but the warning is visible in logcat and may cause a momentary UI glitch or incorrect starting position.

---

## 2. Goals

1. No `W: Current file not found` warning appears when the player opens after a delete-with-optimistic-advance sequence.
2. When a file is deleted, `BrowseResourceStateManager.lastViewedFile` is updated to the next file (the one the player advanced to) before the Activity is recreated.
3. The fix does not affect the `deleteCurrentFile` → advance → player session flow for normal (non-error) paths.

**Non-goals:**

- Changing the optimistic advance behaviour itself.
- Persisting the `lastViewedFile` through process death (current behaviour is already acceptable; the fix is in-process only).

---

## 3. Constraints

- The fix lives in the player layer (`PlayerViewModel`, `FileOperationsHandler`, or `BrowseResourceStateManager`).
- `BrowseResourceStateManager` must be updated atomically with the cache removal — before the next Activity re-creates.
- The next-file after advance may not be deterministic if the list is being re-sorted simultaneously; use the file that the `nextFile()` call selected.
- No new BuildConfig gates. No Room migration (state is in-memory or DataStore, not Room).
- The `saveLastViewedFile` call is already used in multiple places in the browse flow; the fix adds one more call site without changing the existing ones.

---

## 4. Current Architecture Context

The delete-and-advance flow:

1. `FileOperationsHandler.deleteCurrentFile()` → calls `MediaFilesCache.remove(currentFile)`.
2. `PlayerViewModel.nextFile()` is called with `Pre-delete: stop and optimistic advance` reason.
3. `nextFile()` selects the next file in the list and triggers playback of it.
4. `BrowseResourceStateManager.saveLastViewedFile(nextFile.path)` is NOT called at this point.
5. Later, when the browse screen or a new player session restores state, `getLastViewedFile()` returns the old (deleted) path → "Current file not found".

The missing call is at step 4. The `nextFile()` method knows which file was selected but does not inform `BrowseResourceStateManager`. The browse screen's `BrowseViewModel` also writes `lastViewedFile` via a different path when the user navigates the grid, but this path is not exercised during an in-player delete.

---

## 5. Proposed Approach

- After `PlayerViewModel.nextFile()` selects the next file following a delete, update `BrowseResourceStateManager.saveLastViewedFile(nextFilePath)` immediately.
- The entry point for this update is either inside `nextFile()` when it knows the advance reason is `PRE_DELETE`, or inside `FileOperationsHandler.deleteCurrentFile()` after the advance resolves.
- Prefer `FileOperationsHandler` as the call site — it already has the context of "file was deleted" and can pass the next file to `BrowseResourceStateManager` explicitly, keeping `nextFile()` agnostic to the state manager.

The fix is a single `saveLastViewedFile(next.path)` call with the appropriate resource ID, triggered from the delete completion path.

---

## 6. Open Questions

1. **What if there is no next file (last file deleted)?** — `lastViewedFile` should be cleared (set to `null` or empty), not left pointing to the deleted file.
   - **Proposal:** If `nextFile()` returns null (empty list after delete), call `saveLastViewedFile(null)` or clear the stored value.
   - **Status:** Resolve in tactical spec.

2. **Race with `MediaStore` rescan** — after delete, `MediaStore` may trigger a list refresh that arrives before or after the `saveLastViewedFile` call. Does this create a secondary "file not found" from a different source?
   - **Status:** Research in tactical spec; likely not a problem since the MediaStore refresh updates the adapter list, not `lastViewedFile`.

---

## 7. Risks

- Low risk. The fix is a single additional call to an existing method. The only regression surface is if `saveLastViewedFile` has unexpected side effects for the scroll position or grid state — verify by checking all call sites.

---

## 8. User Impact

Bug fix — no change to `docs/FEATURES.md`. The warning "Current file not found" is an internal log message; it may cause a subtle UI delay (the player lands on an incorrect starting file briefly). After fix, the player opens on the correct next file without the warning.

---

## 9. Related Specs

- **S0213** `Tactical` — video playback OOM hardening (player lifecycle management context)
- **S0188** `Verified` — slideshow stop on resource unavailable (related advance/delete interaction)

---

## Last Audit

**Date:** 2026-05-16
**Mode:** strategic
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 11 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Delete a file in `PlayerActivity`; reopen the player for the same resource; confirm no `W/App: Current file not found` line in logcat and player lands on correct next file.
