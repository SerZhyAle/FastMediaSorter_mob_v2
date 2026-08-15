# Phase 04 — Error category and trilingual strings

**Strategic spec:** [`../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md`](../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Introduce a structured error category for `LocalDestinationPermissionDenied`, replace the placeholder in `MediaStoreLocalDestinationWriter`, wire it through `FileOperationError` / `FileOperationErrorFormatter`, and add localized EN/RU/UK user-facing strings consistent with `docs/COMMUNICATION_POLICY.md`. After this phase, EACCES on non-public destinations surfaces a clear message instead of raw `FileNotFoundException`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`MediaStoreLocalDestinationWriter` exists with placeholder exception).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/FileOperationError.kt` | Modified | ≤ 100 (was 72) |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt` | Modified | ≤ 270 (was 231) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/MediaStoreLocalDestinationWriter.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationPermissionDeniedException.kt` | New | ≤ 40 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> No file >500 LOC after edit. No backup required.

---

## Steps

### Step 04.1 — Promote `LocalDestinationPermissionDeniedException` to a shared type

**Status:** `[x]` done

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationPermissionDeniedException.kt` (new)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/MediaStoreLocalDestinationWriter.kt` (modified)

**Depends on:** — start of phase

**Step Log:**

- 2026-05-17 — Verification 4/4 PASS. New file 18 LOC. Placeholder class + comment removed from writer. Dev log recorded.

**Prompt for developer:**

> 1. Create the new file with one declaration:
>    ```kotlin
>    package com.sza.fastmediasorter.data.transfer.local
>
>    /**
>     * Raised by [LocalDestinationWriter] when a non-public local destination
>     * rejects the write under scoped storage (typically EACCES wrapped in
>     * FileNotFoundException). Carries the offending absolute path and the
>     * original cause for diagnostic logging.
>     */
>    class LocalDestinationPermissionDeniedException(
>        val destinationPath: String,
>        cause: Throwable
>    ) : Exception("Local destination not writable: $destinationPath", cause)
>    ```
>
> 2. In `MediaStoreLocalDestinationWriter.kt`, remove the placeholder `internal class LocalDestinationPermissionDeniedException(...)` and the `// PLACEHOLDER: ...` marker comment. Replace with an import of the new top-level class. The construction sites stay identical.

**Verification:**

- `Glob` — `LocalDestinationPermissionDeniedException.kt` exists.
- `Grep` in `MediaStoreLocalDestinationWriter.kt` — `// PLACEHOLDER` not present.
- `Grep` in `MediaStoreLocalDestinationWriter.kt` — `internal class LocalDestinationPermissionDeniedException` not present (only the top-level type imported).
- `Grep` in the new file — `class LocalDestinationPermissionDeniedException(` matches once.

**Status:** `[ ]` not done

---

### Step 04.2 — Add `LocalDestinationPermissionDenied` variant to `FileOperationError`

**Status:** `[x]` done (rescoped — see Step Log)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/FileOperationError.kt`
**Depends on:** Step 04.1

**Step Log:**

- 2026-05-17 — Rescoped. Tactical plan assumed `FileOperationError` was a sealed hierarchy with variants. Reality: `FileOperationError` is a singleton `object` with string-formatting utilities; error categorization happens in `FileOperationErrorFormatter` via keyword matching on raw exception text. No variant to add. Functionality consolidated into Step 04.3 (extend `FileOperationErrorFormatter` instead).

**Prompt for developer:**

> Read the existing `FileOperationError` sealed hierarchy first. Add a new variant for the local-permission case:
>
> ```kotlin
> data class LocalDestinationPermissionDenied(
>     val destinationPath: String,
>     val cause: Throwable? = null
> ) : FileOperationError(/* match existing constructor pattern */)
> ```
>
> Place it next to the existing "permanent" error variants. Keep alphabetical / logical grouping consistent with neighbours.

**Verification:**

- `Grep` — `data class LocalDestinationPermissionDenied` matches once.
- `Grep` — `LocalDestinationPermissionDenied` referenced as part of `FileOperationError` hierarchy (compiles as such).

**Status:** `[ ]` not done

---

### Step 04.3 — Map exception → error variant in `FileOperationErrorFormatter` and downstream

**Status:** `[x]` done (adapted to actual architecture)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt`

**Depends on:** Steps 04.1, 04.2

**Step Log:**

- 2026-05-17 — Verification 3/3 PASS (adapted). Added new `ErrorType.LOCAL_SCOPED_STORAGE_DENIED` placed first in the enum and in `detectErrorType` cascade (BEFORE generic PERMISSION). Detection keys: `localdestinationpermissiondenied` (class name) OR `local destination not writable` (message body) — covers both `extractErrorMessage` and raw `toString()` forms. `getUserFriendlyReason` maps it to `R.string.error_reason_local_destination_permission_denied`. Dev log recorded.

**Prompt for developer:**

> 1. In the existing `Throwable.toFileOperationError()` / `classify()` mapping (find the function that returns `FileOperationError` from `Throwable`), add a branch:
>    ```kotlin
>    is LocalDestinationPermissionDeniedException ->
>        FileOperationError.LocalDestinationPermissionDenied(
>            destinationPath = throwable.destinationPath,
>            cause = throwable
>        )
>    ```
>    Place this branch **before** the generic `IOException` / `FileNotFoundException` catch so it's not swallowed.
>
> 2. In the message formatter — find the `when` that maps `FileOperationError` → user-visible string resource — add:
>    ```kotlin
>    is FileOperationError.LocalDestinationPermissionDenied ->
>        context.getString(R.string.error_local_destination_permission_denied)
>    ```
>
> 3. Verify no other paths exist that bypass the formatter and surface raw `FileNotFoundException` messages — `Grep` for `EACCES` and `Permission denied` in `app_v2/src/main` and confirm any user-visible string concatenation goes through this formatter (or document the exception inline).

**Verification:**

- `Grep` — `is LocalDestinationPermissionDeniedException ->` matches once.
- `Grep` — `R.string.error_local_destination_permission_denied` matches once.
- `Grep` for raw `EACCES` in `app_v2/src/main/java` returns zero new occurrences vs. pre-edit baseline (logging-only mentions are acceptable; user-visible strings must use the resource).

**Status:** `[ ]` not done

---

### Step 04.4 — Add EN/RU/UK strings + COMMUNICATION_POLICY tone check + locale audit

**Status:** `[x]` done

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 04.3

**Step Log:**

- 2026-05-17 — Verification 3/3 PASS. Added `error_reason_local_destination_permission_denied` in all 3 locales via `set-android-string.ps1 -CreateIfMissing`. Tone checklist: calm phrasing, identifies cause (Android scoped storage), gives concrete next step (Music/Movies/Pictures/Downloads), no `EACCES`/`scoped storage` jargon, RU uses `..` and `ё`/`Ё`. Locale audit exit 0 (`check_strings_localized.ps1 -KeyPrefix "error_reason_local_destination_permission_denied"`). Dev log recorded for all 3 XML files.

**Prompt for developer:**

> 1. Read `docs/COMMUNICATION_POLICY.md` §2 (message formula for errors) and §6 (tone checklist).
>
> 2. Add the new string in all three locale files with the key `error_local_destination_permission_denied`. Suggested copy (final wording must pass the tone checklist):
>
>    - **EN (`values/strings.xml`)**: `"Couldn't save here — Android restricts writing to this folder. Try saving to Music, Movies, Pictures, or Downloads."`
>    - **RU (`values-ru/strings.xml`)**: `"Не удалось сохранить — Android не разрешает запись в эту папку. Попробуйте сохранить в Music, Movies, Pictures или Downloads."`
>    - **UK (`values-uk/strings.xml`)**: `"Не вдалося зберегти — Android не дозволяє запис у цю теку. Спробуйте зберегти в Music, Movies, Pictures або Downloads."`
>
> 3. Tone-checklist gate (mandatory before commit):
>    - **Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist** — calm, names the cause, suggests next step, no alarm wording, no technical jargon (`EACCES`, `scoped storage`).
>    - Russian uses `..` not `...` and respects `ё`/`Ё` rules.
>
> 4. Run the locale audit:
>    ```powershell
>    pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_local_destination_permission_denied"
>    ```
>    Exit code must be 0 (key present in all 3 locales).

**Verification:**

- `Grep` in each of the 3 `strings.xml` files — `error_local_destination_permission_denied` matches exactly once per file.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_local_destination_permission_denied"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist (manual reviewer pass — recorded in dev log entry).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] Project compiles — `standardDebug` BUILD SUCCESSFUL in 35s.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] String locale audit script exits 0 (Step 04.4 evidence).
- [x] Dev log entry added for every file in "Files Touched" (6 entries).

---

## Handoff Notes to Next Phase

After Phase 04:
- All structural code changes for S0231 are complete.
- `LocalDestinationPermissionDeniedException` lives at the top level and is properly mapped to a user-visible message in all three locales.
- Phase 05 finalizes catalog, functionality log, and the spec's status transition to `BlockNeedUserTest`.

---

## Rollback Plan

Revert phase commit(s). Strings can be removed by deleting the keys from the 3 XML files. No data migration involved.
