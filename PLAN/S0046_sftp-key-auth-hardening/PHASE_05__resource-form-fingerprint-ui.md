# Phase 05 — Fingerprint field in Add/Edit Resource form

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Expose `hostKeyFingerprint` in the resource creation/edit UI for SFTP rows, persist it on save, and route mismatch errors to a distinct error class in test-connection results.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_add_resource_sftp.xml` (or current SFTP layout — locate via `Grep "edittext_sftp"`) | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpFtpCoordinator.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpKeyCoordinator.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFinalizer.kt` (or whatever wraps `MediaResource` save — locate via `Grep`) | Modified | ≤ 300 |

---

## Steps

### Step 05.1 — Add fingerprint EditText to the SFTP form layout

**Files:** the SFTP layout XML (locate via `Grep -rn "@+id/edittext_sftp" app_v2/src/main/res/layout/`)
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new `EditText` with `android:id="@+id/edittext_sftp_host_key_fingerprint"` below the existing host/port/username/password block but above the action button row. `android:hint="@string/sftp_host_key_fingerprint_hint"`, `android:inputType="textNoSuggestions"`, `android:singleLine="true"`. The string resource is added in Phase 06 — at this step the string ID is forward-reference only; lint will flag missing translations until Phase 06 lands.

**Verification:**

- `Grep` — `edittext_sftp_host_key_fingerprint` matches in exactly one layout file.
- `Grep` — `sftp_host_key_fingerprint_hint` matches in exactly one layout file.

**Status:** `[ ]` not done

---

### Step 05.2 — Persist fingerprint via `AddResourceSftpFtpCoordinator`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpFtpCoordinator.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Read the `edittext_sftp_host_key_fingerprint` value when building `MediaResource` for save. Pass it through `SshFingerprintNormalizer.canonical(...)`; if input is non-empty and normalization returns null, surface a typed validation error to the user via the existing `bridge.emit(AddResourceEvent.Show*)` channel and abort save. Successful normalization → set `MediaResource.hostKeyFingerprint`. Empty input → `null` (legacy permissive mode preserved).

**Verification:**

- `Grep` — `edittext_sftp_host_key_fingerprint` matches in `AddResourceSftpFtpCoordinator.kt`.
- `Grep` — `SshFingerprintNormalizer.canonical` matches in `AddResourceSftpFtpCoordinator.kt`.
- `Grep` — `hostKeyFingerprint` matches at least twice in `AddResourceSftpFtpCoordinator.kt`.

**Status:** `[ ]` not done

---

### Step 05.3 — Same wiring in `AddResourceSftpKeyCoordinator`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpKeyCoordinator.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Mirror Step 05.2 inside the key-auth coordinator. Test-connection (`testSftpConnectionWithKey`) must pass the normalized fingerprint to `smbOperationsUseCase.testSftpConnection(...)` (already accepts `expectedFingerprint` after Phase 03). On `HostKeyMismatchException` → emit a distinct `AddResourceEvent.ShowTestResult(message, success=false)` whose message names both expected and actual fingerprints (use `SshFingerprintNormalizer.shortForList` for the inline form; full SHA256:base64 for the dialog body).

**Verification:**

- `Grep` — `expectedFingerprint` matches in `AddResourceSftpKeyCoordinator.kt`.
- `Grep` — `HostKeyMismatchException` matches in `AddResourceSftpKeyCoordinator.kt`.
- `Grep` — `SshFingerprintNormalizer` matches at least twice in `AddResourceSftpKeyCoordinator.kt`.

**Status:** `[ ]` not done

---

### Step 05.4 — Round-trip on edit-existing-resource path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFinalizer.kt` (or the file responsible for prefilling the form on edit — discover via `Grep -rn "edittext_sftp_username" app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/`)
**Depends on:** Step 05.3

**Prompt for developer:**

> When the form is opened to edit an existing `MediaResource`, prefill `edittext_sftp_host_key_fingerprint` with the canonical value already stored on the resource. Saving without changes preserves the value. Clearing the field saves `null` (resource reverts to permissive mode). No new state — uses existing finalizer plumbing.

**Verification:**

- `Grep` — `edittext_sftp_host_key_fingerprint` matches in the file modified in this step.
- `Grep` — `hostKeyFingerprint` matches at least twice in the file modified in this step.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

UI fully functional except for missing strings (forward-referenced in Step 05.1). Phase 06 adds the trilingual strings, regenerates docs/catalog, closes the loop.

---

## Rollback Plan

Revert phase commit(s); the new EditText is the only user-visible addition and depends on the Phase 06 strings — without those it shows raw resource-id placeholders, which is non-destructive but ugly. Rolling back Phase 05 alone is therefore safe and self-contained.
