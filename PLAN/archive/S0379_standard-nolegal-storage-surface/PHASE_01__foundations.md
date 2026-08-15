# Phase 01 - Foundations

**Strategic spec:** [`../S0379_standard-nolegal-storage-surface.md`](../S0379_standard-nolegal-storage-surface.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Prepare shared storage helpers so removable-volume tree URIs can be resolved and written without changing existing path-based flows.

---

## Prerequisites

- [ ] Working tree is on a feature branch.
- [ ] Strategic §6.8 and §6.9 remain Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/UriPathResolver.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/SafHelper.kt` | Modified | ≤ 320 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Switch removable-volume path resolution to official API first

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/UriPathResolver.kt`
**Depends on:** - start of phase

**Prompt for developer:**

- Keep the existing public `getPath(context, uri)` contract.
- For removable-storage volumes on API 30+, use `StorageVolume.directory` first.
- Keep the reflection fallback for lower APIs and for devices where the official directory is unavailable.
- Preserve current primary-volume and Downloads behavior.
- Do not change callers in this step.

**Verification:**

- `UriPathResolver.kt` contains an API 30+ `StorageVolume.directory` branch.
- `UriPathResolver.kt` still contains a legacy fallback for lower APIs.
- `fun getPath(context: Context, uri: Uri): String?` signature is unchanged.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS. Files: `core/util/UriPathResolver.kt`. Dev log recorded.

### Step 01.2 - Add SAF tree helpers for writable child-document routing

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/utils/SafHelper.kt`
**Depends on:** 01.1

**Prompt for developer:**

- Keep all existing delete and normalization helpers intact.
- Add small helper functions for writable tree destinations:
- detect tree/document URI kind reliably;
- resolve a tree root `DocumentFile`;
- find or create a writable child file by display name under a tree URI;
- replace an existing child when overwrite is allowed.
- Keep the helpers protocol-agnostic and reusable from copy/move use cases.

**Verification:**

- `SafHelper.kt` contains a helper that resolves `DocumentFile.fromTreeUri(...)`.
- `SafHelper.kt` contains a helper that creates or reuses a child document under a tree URI.
- Existing `deleteContentUri(...)` and `normalizeContentUri(...)` functions remain present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS. Files: `utils/SafHelper.kt`. Dev log recorded.

---

## Phase Done Criteria

- [x] Shared helper layer can resolve removable-volume roots without requiring reflection on API 30+.
- [x] Shared helper layer exposes reusable SAF tree child-file creation for later phases.
- [x] No caller behavior changed outside helper classes.
