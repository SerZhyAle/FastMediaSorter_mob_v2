# Phase 01 — Predefined Resource Classifier

**Strategic spec:** [`../S0059_predefined-recent-downloads-all-files.md`](../S0059_predefined-recent-downloads-all-files.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Introduce a single canonical classifier that recognises the predefined "Recent" virtual resource and a LOCAL resource pointing at the system Downloads directory, and exposes the desired default for the `allFiles` flag of any predefined resource. No call sites are wired yet — pure foundation.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none)
- [ ] Strategic §6 research items blocking this phase are Resolved (see [INDEX.md](INDEX.md) Pre-Implementation Blockers).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/util/PredefinedResourceClassifier.kt` | New | ≤ 80 |

---

## Steps

### Step 01.1 — Create `PredefinedResourceClassifier.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/PredefinedResourceClassifier.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a Kotlin `object PredefinedResourceClassifier` in package `com.sza.fastmediasorter.util`. Expose three public functions:
> - `fun isPredefinedRecent(path: String): Boolean` — returns true iff `path == LocalMediaScanner.VIRTUAL_PATH_RECENT`.
> - `fun isPredefinedDownloads(path: String, type: ResourceType): Boolean` — returns true iff `type == ResourceType.LOCAL` and `path` equals `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath`. Resolve the canonical path lazily once per call (the directory accessor is cheap; do not cache across process lifetime to stay tolerant of multi-user storage edge cases).
> - `fun defaultAllFilesForPredefined(path: String, type: ResourceType): Boolean?` — returns `true` when `isPredefinedRecent(path)` or `isPredefinedDownloads(path, type)` is true; returns `null` otherwise (caller keeps its own default).
> Use Timber only — no `Log.d`. No comments unless they encode a non-obvious invariant. The file must stay under 80 lines.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/util/PredefinedResourceClassifier.kt` exists.
- `Grep` — `^object PredefinedResourceClassifier` matches exactly once.
- `Grep` — `fun isPredefinedRecent\(path: String\): Boolean` matches exactly once.
- `Grep` — `fun isPredefinedDownloads\(path: String, type: ResourceType\): Boolean` matches exactly once.
- `Grep` — `fun defaultAllFilesForPredefined\(path: String, type: ResourceType\): Boolean\?` matches exactly once.
- `Grep` — `Log\.d\(` returns zero hits in the new file.

**Status:** `[ ]` not done

---

### Step 01.2 — Reference `VIRTUAL_PATH_RECENT` from `LocalMediaScanner`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/PredefinedResourceClassifier.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside `PredefinedResourceClassifier`, import `com.sza.fastmediasorter.data.local.LocalMediaScanner` and use `LocalMediaScanner.VIRTUAL_PATH_RECENT` as the canonical Recent path. Do not duplicate the literal `"virtual://recent"` string — single source of truth.

**Verification:**

- `Grep` — `import com.sza.fastmediasorter.data.local.LocalMediaScanner` present in the new file.
- `Grep` — `LocalMediaScanner.VIRTUAL_PATH_RECENT` matches at least once in the new file.
- `Grep` — `"virtual://recent"` literal does **not** appear in the new file (zero hits).

**Status:** `[ ]` not done

---

### Step 01.3 — Compile + dev log

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/PredefinedResourceClassifier.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Run `/build` to confirm the project compiles with the new file. Then add a dev-log entry via `.\scripts\add_to_dev_log.ps1`.

**Verification:**

- Build succeeds (no compile errors).
- `Grep` — `PredefinedResourceClassifier.kt` matches at least once in the most recent line of `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the new file via `.\scripts\add_to_dev_log.ps1`.
- [ ] No public API changed in any other module — catalog regen deferred to Phase 04.

---

## Handoff Notes to Next Phase

- `PredefinedResourceClassifier.isPredefinedRecent(path)`, `isPredefinedDownloads(path, type)` and `defaultAllFilesForPredefined(path, type)` are the **only** sanctioned predicates for "is this resource a predefined Recent / Downloads?" in this feature. Phases 02 and 03 must call them; do not re-derive the path checks inline.
- The Downloads canonical path is intentionally read at call time. If a future phase introduces caching, do it inside the classifier — never in callers.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed. The added file is unreferenced, so deletion is safe.
