# Phase 05 — Fingerprint field in Add/Edit Resource form

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Expose `hostKeyFingerprint` in the resource creation/edit UI for SFTP rows, persist it on save, and route mismatch errors to a distinct error class in test-connection results.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

> **Realigned 2026-06-10 (in-session /spec-update, owner-approved):** the original plan assumed a separate `fragment_add_resource_sftp.xml` with `edittext_sftp_*` IDs and coordinators that read EditTexts. Reality: a single `activity_add_resource.xml` (no `layout-land` counterpart) using view-binding with `etSftp*`/`tilSftp*` IDs; field values are read by `AddResourceConnectionManager`/`AddResourceFormManager` and prefilled by `AddResourceHelper`, then passed as primitives to coordinators via `AddResourceViewModel`. Strings moved ahead of this phase (Step 05.1) because the layout/code reference them at compile time. Steps, files, and predicates below match the real structure.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` + `values-ru` + `values-uk` | Modified | n/a |
| `app_v2/src/main/res/layout/activity_add_resource.xml` (no `layout-land` counterpart) | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpFtpCoordinator.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpKeyCoordinator.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceHelper.kt` | Modified | n/a |

---

## Steps

### Step 05.1 — Trilingual strings (compile prerequisite)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `sftp_host_key_fingerprint_hint`, `sftp_host_key_fingerprint_invalid`, `sftp_host_key_mismatch_title`, `sftp_host_key_mismatch_body_format` to all three locales (text per former Phase 06.1). Moved here from Phase 06 because the layout (Step 05.2) and coordinators (05.3-05.4) reference them, and a missing resource breaks the Phase 05 build. Use `..` not `...`; `ё`/`Ё` in RU. Prefer `scripts/utils/set-android-string.ps1 -Action add`.

**Verification:**

- `Grep` — each of the four IDs matches exactly once in each of the three `strings.xml` files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 — Verification PASS (4/4 keys in EN/RU/UK; check_strings_localized OK; Cyrillic + apostrophe escaping + \n/%1$s verified). Added via set-android-string.ps1 -Action add. Dev log recorded.

---

### Step 05.2 — Add fingerprint field to `activity_add_resource.xml`

**Files:** `app_v2/src/main/res/layout/activity_add_resource.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a `TextInputLayout` (`@+id/tilSftpHostKeyFingerprint`, hint `@string/sftp_host_key_fingerprint_hint`) wrapping a `TextInputEditText` (`@+id/etSftpHostKeyFingerprint`, `inputType="textNoSuggestions"`, `maxLines="1"`) inside `layoutSftpFolder`, after the auth blocks and before `btnSftpTest`, so it is common to password and key auth. No `layout-land/activity_add_resource.xml` exists, so no landscape counterpart edit is required.

**Verification:**

- `Grep` — `etSftpHostKeyFingerprint` matches in `activity_add_resource.xml`.
- `Grep` — `sftp_host_key_fingerprint_hint` matches in `activity_add_resource.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 — Verification 2/2 PASS. tilSftpHostKeyFingerprint/etSftpHostKeyFingerprint added in layoutSftpFolder before btnSftpTest (common to both auth methods). No layout-land counterpart exists. Dev log recorded.

---

### Step 05.3 — Normalize + persist fingerprint in both coordinators and `AddResourceViewModel`

**Files:** `AddResourceSftpFtpCoordinator.kt`, `AddResourceSftpKeyCoordinator.kt`, `AddResourceViewModel.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Thread the raw fingerprint string from UI through `AddResourceViewModel` into both coordinators (new trailing default-null params: `expectedFingerprint` on the test methods, `hostKeyFingerprint` on the add methods). In each coordinator: blank → null (permissive); non-blank → `SshFingerprintNormalizer.canonical(..)`; if canonical is null emit `AddResourceEvent.ShowError(R.string.sftp_host_key_fingerprint_invalid)` and abort. Set `MediaResource.hostKeyFingerprint = canonical` on save; pass canonical to `smbOperationsUseCase.testSftpConnection(.., expectedFingerprint = canonical)` on test. On `HostKeyMismatchException` emit a distinct `AddResourceEvent.ShowTestResult(message, isSuccess = false)` whose message uses `sftp_host_key_mismatch_*` strings with `SshFingerprintNormalizer.shortForList`.

**Verification:**

- `Grep` — `SshFingerprintNormalizer.canonical` matches in each coordinator.
- `Grep` — `hostKeyFingerprint` matches at least twice in `AddResourceSftpFtpCoordinator.kt`.
- `Grep` — `HostKeyMismatchException` matches in each coordinator.
- `Grep` — `expectedFingerprint` matches in `AddResourceViewModel.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 — Verification PASS. FtpCoord (canonical 1, hostKeyFingerprint 3, HostKeyMismatch 2), KeyCoord (canonical 1, HostKeyMismatch 2), VM (expectedFingerprint 6). normalizeFingerprintOrEmitError + emitSftpTestFailure helpers in both coordinators; mismatch → distinct ShowTestResult with shortForList. Dev log recorded.

---

### Step 05.4 — Read + prefill the field in the UI managers

**Files:** `AddResourceConnectionManager.kt`, `AddResourceFormManager.kt`, `AddResourceHelper.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> `AddResourceConnectionManager.testSftpConnection()`: read `binding.etSftpHostKeyFingerprint` and pass to both `viewModel.testSftpConnectionWithKey(..)` and `viewModel.testSftpFtpConnection(..)`. `AddResourceFormManager.addSftpResource()`: read the same field and pass to `viewModel.addSftpResourceWithKey(..)` / `viewModel.addSftpFtpResource(..)`; add a `installTextInputTapFocusBridge` line for the new field. `AddResourceHelper`: in the SFTP edit-prefill block, `binding.etSftpHostKeyFingerprint.setText(resource.hostKeyFingerprint.orEmpty())`. Clearing the field saves null (permissive).

**Verification:**

- `Grep` — `etSftpHostKeyFingerprint` matches in each of the three files.
- `Grep` — `hostKeyFingerprint` matches in `AddResourceHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 — Verification PASS. ConnectionManager reads field → both test calls; FormManager reads field → both add calls + tap-focus bridge; Helper prefills etSftpHostKeyFingerprint from resource.hostKeyFingerprint. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — `assembleStandardDebug` BUILD SUCCESSFUL (2m55s, 2026-06-10).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

UI fully functional except for missing strings (forward-referenced in Step 05.1). Phase 06 adds the trilingual strings, regenerates docs/catalog, closes the loop.

---

## Rollback Plan

Revert phase commit(s); the new EditText is the only user-visible addition and depends on the Phase 06 strings — without those it shows raw resource-id placeholders, which is non-destructive but ugly. Rolling back Phase 05 alone is therefore safe and self-contained.
