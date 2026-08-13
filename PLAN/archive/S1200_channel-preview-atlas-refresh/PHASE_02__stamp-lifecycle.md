# Phase 02 - Stamp lifecycle

**Strategic spec:** [`../S1200_channel-preview-atlas-refresh.md`](../S1200_channel-preview-atlas-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Write the stamp exactly where the install is recorded and clear it exactly where the payload is removed, so the two can never disagree (strategic §5).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/RealDeliverableSetDownloader.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableCapabilityRepositoryImpl.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableCapabilityRepository.kt` | Modified | ≤ 60 |

---

## Steps

### Step 02.1 - Record the stamp on install, drop it on uninstall

**Files:** `domain/delivery/DeliverableCapabilityRepository.kt`, `data/delivery/DeliverableCapabilityRepositoryImpl.kt`, `data/delivery/RealDeliverableSetDownloader.kt`

**Prompt for developer:**

> Widen `markInstalled(set)` to `markInstalled(set, stamp: String)` on the repository interface and impl, writing both the flag and the stamp. `uninstall(set)` already deletes the payload and clears the flag - clear the stamp there too. At the single call site in `RealDeliverableSetDownloader` (the one next to the existing `repository.markInstalled(set)`), pass the descriptor's `stamp` - the downloader already holds the descriptor it just verified, so no new lookup. Do not introduce a second write site: the whole point is that the flag and the stamp are set together.

**Verification:**

- `Grep` - `markInstalled(` takes a stamp argument in interface, impl and call site (three hits, no stragglers).
- `Grep -c "setStamp("` - exactly two (install and, via uninstall, the clear) - no third writer.
- `.\a.ps1 fk` compiles (proves every `markInstalled` caller was updated).

**Status:** `[x]` done

---

### Step 02.2 - Expose the staleness question

**Files:** `domain/delivery/DeliverableCapabilityRepository.kt`, `data/delivery/DeliverableCapabilityRepositoryImpl.kt`

**Prompt for developer:**

> Add `suspend fun isStale(set: DeliverableSet, expectedStamp: String): Boolean` returning false for a bundled set (nothing to compare - strategic §7) and for a set whose payload is absent (not installed is not stale), and otherwise `installedStamp(set) != expectedStamp`. KDoc the missing-stamp case explicitly: a payload installed before this ticket has no stamp and therefore reads as stale, which is the deliberate choice from strategic §5 - one redundant offer beats permanently hiding a real update.

**Verification:**

- `Grep` - `isStale` declared in the interface and implemented once.
- `Grep` - the impl returns false when `bundled.contains(set)`.
- `.\a.ps1 fk` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit - focus: no path can set the install flag without the stamp; `isStale` does no I/O beyond the DataStore read the flag path already does.

---

## Handoff Notes to Next Phase

- Staleness is answerable but nothing asks yet; Phase 03 surfaces it.

---

## Rollback Plan

Revert the phase commit(s); `markInstalled`'s signature change is the only cross-cutting edit.
