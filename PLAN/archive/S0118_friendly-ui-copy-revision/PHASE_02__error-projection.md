# Phase 02 - Error Projection

**Strategic spec:** [`../S0118_friendly-ui-copy-revision.md`](../S0118_friendly-ui-copy-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Project shared error flows through the S0118 contract so compact notifications stay short, technical detail stays secondary, and repeated error families sound consistent.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Shared copy baseline keys exist in EN, RU, and UK.
- [ ] Current error notifier behavior is captured before edits.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiMessageProjector.kt` | New | <= 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/AppErrorNotifier.kt` | Modified | <= 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/ToastThrottler.kt` | Modified | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt` | Modified | <= 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/ConnectionErrorFormatter.kt` | Modified | <= 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapper.kt` | Modified | <= 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt` | Modified | <= 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt` | Modified | <= 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatterTest.kt` | New | <= 240 |
| `app_v2/src/test/java/com/sza/fastmediasorter/util/ConnectionErrorFormatterTest.kt` | New | <= 240 |

---

## Steps

### Step 02.1 - Add the shared message projector

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiMessageProjector.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a small UI projector that takes `UiMessageSpec` and renders the short form to Snackbar or Toast while exposing the detailed form to dialog surfaces. Keep it Android-UI aware but free of feature-specific strings.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/copy/UiMessageProjector.kt` exists.
- `Grep` - `class UiMessageProjector` or `object UiMessageProjector` matches exactly once.
- `Grep` - `UiMessageSpec` present in method signatures.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: UiMessageProjector.kt (+118 LOC). Dev log recorded.

---

### Step 02.2 - Rewrite formatter outputs to the shared contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/util/ConnectionErrorFormatter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Refactor the shared file-operation, connection, and network error builders so they emit short primary copy plus optional technical detail instead of punctuation-heavy or emoji-prefixed blobs. Keep the current error classification behavior intact while rewriting the user-visible wording.

**Verification:**

- `Grep` - `❌|⚠|💡|📄|📂|📁|🔧` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt`.
- `Grep` - `UiMessageSpec` present in the touched formatter files.
- `Grep` - `toContextAwareMessage` still present in `NetworkErrorMessageMapper.kt`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: FileOperationErrorFormatter.kt (emoji-strip + formatAsSpec, 230 LOC), ConnectionErrorFormatter.kt (+formatAsSpec, 263 LOC), NetworkErrorMessageMapper.kt (+toUiSpec, 91 LOC). Note: file LOCs over per-step plan budget but well under the 1500 hard limit. Dev log recorded.

---

### Step 02.3 - Route shared error surfaces through the projector

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/AppErrorNotifier.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/util/ToastThrottler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Update the shared notifier and browse error manager to use the new projector and the revised short-vs-detailed contract. Preserve the existing settings gate for detailed errors and the current debug-only network-notification policy unless a strategic follow-up explicitly changes it.

**Verification:**

- `Grep` - `UiMessageProjector` present in `BrowseErrorDisplayManager.kt` or `AppErrorNotifier.kt`.
- `Grep` - `showDetailedErrors` still present in `BrowseErrorDisplayManager.kt`.
- `Grep` - `if (!BuildConfig.DEBUG) return false` still present in `ToastThrottler.kt` unless the strategic spec is updated.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. BrowseErrorDisplayManager now routes through UiMessageProjector. Dev log recorded.

---

### Step 02.4 - Add shared error-copy tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatterTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/util/ConnectionErrorFormatterTest.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add unit tests for the rewritten shared formatters. Cover one authentication failure, one timeout, and one generic fallback path, and assert that the primary copy is short and free of emoji-like prefixes.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatterTest.kt` exists.
- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/util/ConnectionErrorFormatterTest.kt` exists.
- `Grep` - `emoji` or `timeout` appears in a test method name.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: FileOperationErrorFormatterTest.kt (+106 LOC), ConnectionErrorFormatterTest.kt (+85 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - BUILD SUCCESSFUL in 35s.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` regenerated after the touched `.kt` files change.

---

## Handoff Notes to Next Phase

Shared error emitters now speak one tone and expose detail separately, so settings and feature screens can migrate without re-deciding the error copy formula.

---

## Rollback Plan

Revert the Phase 02 commit(s). No data schema, migration, or persisted preference shape changes are involved.