# Phase 07 — Add S/FTP form UX simplification

**Strategic spec:** [`../S0046_sftp-key-auth-hardening.md`](../S0046_sftp-key-auth-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** —
**Steps done:** 5 / 5
**Started:** 2026-06-14
**Completed:** 2026-06-14

---

## Objective

Reduce cognitive load of the Add/Edit S/FTP resource screen and present host-key pinning as an optional, human-readable security block, per the approved UI clarification (`dev/S0046_ui_clarify_sftp_key_ux.md`, status READY). The host-key fingerprint field shipped in Phase 05 as a bare always-visible input; this phase moves it under a collapsible `Server verification` block, relabels the SSH-key fields with plain wording, adds a sign-in-method label, and restricts host-key UI to SFTP.

---

## Source of truth

`dev/S0046_ui_clarify_sftp_key_ux.md` — all placement, visibility, label, and copy decisions are approved there. This phase is presentation/labels/visibility only; the save/test/pin flows from Phases 01–05 are unchanged.

---

## Files Touched

| File | New / Modified |
|------|:--------------:|
| `app_v2/src/main/res/values/strings.xml` + `values-ru` + `values-uk` | Modified |
| `app_v2/src/main/res/values/strings_sources.xml` + `values-ru` + `values-uk` | Modified |
| `app_v2/src/main/res/layout/activity_add_resource.xml` (no `layout-land` counterpart) | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceHelper.kt` | Modified |

---

## Steps

### Step 07.1 — Trilingual strings

**Files:** `strings.xml` ×3, `strings_sources.xml` ×3
**Done:**

- Added `sftp_auth_method_label`, `sftp_server_verification_title`, `sftp_server_verification_subtitle`, `sftp_host_key_fingerprint_helper` to `strings.xml` (EN/RU/UK lockstep).
- Relabelled in `strings_sources.xml`: `sftp_private_key` → "Private SSH key", `sftp_private_key_hint` → "Paste the key or choose a file.", `sftp_key_passphrase_hint` → "Leave empty if the key is not encrypted." (EN/RU/UK).
- Softened `sftp_host_key_mismatch_title` / `sftp_host_key_mismatch_body_format` to a user-readable message that still prints expected + offered fingerprints (`%1$s`/`%2$s` preserved) — satisfies both the UI clarification and strategic §11.3.

**Verification:** `Grep` — each new key resolves 3/3 across `values/`, `values-ru/`, `values-uk/`; relabels present in all three `strings_sources.xml`. PASS.

### Step 07.2 — Sign-in method label

**Files:** `activity_add_resource.xml`
**Done:** Added `tvSftpAuthMethodLabel` (`@string/sftp_auth_method_label`, `labelFor=rgSftpAuthMethod`) above the auth-method radio group.

**Verification:** `fc` build includes the new binding field. PASS.

### Step 07.3 — Collapsible `Server verification` block (SFTP-only)

**Files:** `activity_add_resource.xml`, `AddResourceFormManager.kt`
**Done:**

- Wrapped the existing `tilSftpHostKeyFingerprint` field in a `MaterialCardView` (`cardSftpServerVerification`) with a `CollapsibleSectionHeader` (`headerSftpServerVerification`), an always-visible one-line subtitle (`tvSftpServerVerificationSubtitle`, discoverability while collapsed), and a collapsible content container (`contentSftpServerVerification`).
- Moved the block below `Test connection` and above `Remote path` (Test stays the primary first-screen action).
- Field now carries `helperText=@string/sftp_host_key_fingerprint_helper`.
- Bound the new header in `AddResourceFormManager.setupCollapsibleSections()` with section key `add_sftp_<orientation>_server_verification` (collapsed by default).

**Verification:** `fc` BUILD SUCCESSFUL. PASS.

### Step 07.4 — Protocol-gated visibility + edit reveal

**Files:** `AddResourceActivity.kt`, `AddResourceHelper.kt`
**Done:**

- Protocol toggle hides `cardSftpServerVerification` for FTP, shows it for SFTP (host keys are an SSH concept); explicit initial show in `showSftpFolderOptions()`.
- On edit, when a fingerprint is already pinned, the block is revealed (`setExpanded(true, notify=false)` + content VISIBLE) without persisting the expanded state for future new resources.

**Verification:** `fc` BUILD SUCCESSFUL. PASS.

### Step 07.5 — Accessibility

**Files:** `activity_add_resource.xml`
**Done:** Explicit `contentDescription` on the collapsed security header and the SSH-key upload button. Auth radios and field hints provide their own TalkBack labels.

**Verification:** `fr` resource processing OK (part of `fc`). PASS.

---

## Phase Done Criteria

- [x] Every `Step 07.*` is `[x]` done.
- [x] `assembleStandardDebug` resources+code compile — `a.ps1 fc` BUILD SUCCESSFUL (39s, 2026-06-14).
- [x] No `layout-land/activity_add_resource.xml` counterpart exists — no landscape edit required.
- [x] String locale parity verified (EN/RU/UK) for every added/changed key.

---

## Notes

- This phase changes presentation, labels, visibility, and validation-display only. The Phase 03 pin verifier, Phase 04 XML bootstrap, and Phase 05 persistence/test wiring are untouched, so the existing `Timber.d("S0046:` probes remain valid and the spec stays `BlockNeedUserTest`.
- Deferred (out of scope, low value): a dedicated friendly "key read failed" string — the existing file-picker read path is unchanged and the UI clarification listed that copy only as an example.
