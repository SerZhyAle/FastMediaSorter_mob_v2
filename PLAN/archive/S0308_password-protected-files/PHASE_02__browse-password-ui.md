# Phase 02 - Browse Password UI

**Strategic spec:** [`../S0308_password-protected-files.md`](../S0308_password-protected-files.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Ask for a ZIP password in Browse and execute extraction only after a valid password preflight.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist before integration.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseEvent.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | existing >500 - backup required |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveManager.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveDialogManager.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt` | Modified | ≤ 260 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

---

## Steps

### Step 02.1 - Add password prompt events

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseEvent.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`
**Depends on:** Phase 01 done

**Prompt for developer:**

> Add a Browse event that carries the archive file and target folder name for a password prompt. Add a ViewModel entry point that accepts a `CharArray` password and delegates to the archive manager. Do not store the password beyond the current call chain.

**Verification:**

- `Grep` - `ShowArchivePasswordDialog` exists in `BrowseEvent.kt`.
- `Grep` - `fun extractArchiveWithPassword` exists in `BrowseViewModel.kt`.
- `Grep` - `Log.d(` returns zero hits in both modified Kotlin files.

**Evidence:**

- `grep_search`: expected `ShowArchivePasswordDialog` present | actual present.
- `grep_search`: expected `fun extractArchiveWithPassword` present | actual present.
- `grep_search`: expected `Log.d(` count 0 in modified ViewModel/Event files | actual 0.

**Status:** `[x]` done

---

### Step 02.2 - Add localized password strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add EN/RU/UK strings for archive password title, message, hint, wrong password, unsupported protected file, and external open action. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Grep` - `protected_archive_password_title` exists in all three strings files.
- `Grep` - `protected_archive_password_wrong` exists in all three strings files.
- `Grep` - `protected_file_open_external` exists in all three strings files.
- `Command` - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix protected_` exits 0.
- `Strings pass COMMUNICATION_POLICY §6 checklist`.

**Evidence:**

- `grep_search`: expected `protected_archive_password_title` in EN/RU/UK | actual present in all three.
- `grep_search`: expected `protected_archive_password_wrong` in EN/RU/UK | actual present in all three.
- `grep_search`: expected `protected_file_open_external` in EN/RU/UK | actual present in all three.
- `post-change`: expected `scripts/check_strings_localized.ps1 -KeyPrefix protected_` PASS | actual PASS, 7 keys present in EN/RU/UK.
- `Strings pass COMMUNICATION_POLICY §6 checklist`: actual PASS, short factual text, no blame, no sensitive data.

**Status:** `[x]` done

---

### Step 02.3 - Prompt and route password extraction

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveDialogManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `prepareExtraction`, detect encrypted ZIPs and show the password prompt before the normal extract confirmation. In the dialog, use one text password field and OK / Cancel buttons. On OK, pass a transient `CharArray` password to ViewModel; on Cancel, do nothing. On invalid password, show one message and stop the attempt.

**Verification:**

- `Grep` - `showArchivePasswordDialog` exists in `BrowseArchiveDialogManager.kt`.
- `Grep` - `InputType.TYPE_TEXT_VARIATION_PASSWORD` exists in `BrowseArchiveDialogManager.kt`.
- `Grep` - `isPasswordRequired` exists in `BrowseArchiveManager.kt`.
- `Grep` - `extractArchiveWithPassword` exists in `BrowseManagerInitializer.kt` + `BrowseViewModel.kt` (the `ShowArchivePasswordDialog` event is handled in `BrowseEventHandler.kt`).
- `Grep` - `Log.d(` returns zero hits in all modified Kotlin files.

**Evidence:**

- `grep_search`: expected `showArchivePasswordDialog` present | actual present.
- `grep_search`: expected `InputType.TYPE_TEXT_VARIATION_PASSWORD` present | actual present.
- `grep_search`: expected `isPasswordRequired` present in manager flow | actual present.
- `grep_search`: expected `extractArchiveWithPassword` present in handler/initializer flow | actual present.
- `grep_search`: expected `Log.d(` count 0 in modified Kotlin files | actual 0.

**Status:** `[x]` done

---

### Step 02.4 - Prevent target creation on wrong password

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseArchiveManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Validate archive password access before calling directory creation. If validation returns missing or invalid password, emit the matching Browse event/message and return without creating the extraction directory.

**Verification:**

- `Grep` - `validateArchiveAccess` exists in `BrowseArchiveManager.kt`.
- `Grep` - `createDirectoryUseCase` appears after `validateArchiveAccess` in `BrowseArchiveManager.kt`.
- `Grep` - `protected_archive_password_wrong` exists in `BrowseArchiveManager.kt`.

**Evidence:**

- `grep_search`: expected `validateArchiveAccess` present | actual present.
- `grep_search`: expected `createDirectoryUseCase` after `validateArchiveAccess` in `BrowseArchiveManager.kt` | actual `validateArchiveAccess` line 195, `createDirectoryUseCase` line 200.
- `grep_search`: expected `protected_archive_password_wrong` present | actual present.
- `gradlew`: `JAVA_HOME=C:\Program Files\Java\jdk-17; .\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.domain.usecase.ExtractArchiveUseCaseTest"` exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - targeted unit test command compiled `standardDebug` and exited 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Phase 03 reuses the user-facing unsupported-protection strings and fallback wording.

---

## Rollback Plan

Revert phase commits. User-facing ZIP extraction falls back to the pre-S0308 generic failure path.