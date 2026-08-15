# Phase 05 - QR share dialog wiring

**Strategic spec:** [`../S1039_share-resource-fmscfg.md`](../S1039_share-resource-fmscfg.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Offer "Show QR" alongside "Send file" in the existing share-access dialog and route the choice to the matching ViewModel call. This is the user-visible wiring that makes the QR path reachable.

---

## Prerequisites

- [ ] Phase 04 ✅ Done (`shareSftpResourceConfigAsQr` exists).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +2 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +2 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +2 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainSftpShareManager.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ +8 |

---

## Steps

### Step 05.1 - "Show QR" action string (EN / RU / UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `sftp_share_show_qr_action` in lockstep across all three locales via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key sftp_share_show_qr_action -En "Show QR" -Ru "Показать QR" -Uk "Показати QR"`. Keep it a short button label (§2 message formula for an action). `..` never `...`.

**Verification:**

- `Grep` - `sftp_share_show_qr_action` present in all three `strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "sftp_share_show_qr"` - exit 0.

**Status:** `[ ]` not done

---

### Step 05.2 - Method choice in `MainSftpShareManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainSftpShareManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add `enum class ShareMethod { FILE, QR }` to the class. Change the callback to `onConfirm: (includePassword: Boolean, method: ShareMethod) -> Unit`. Keep the positive button (`sftp_share_export_action`) firing `onConfirm(!omitPassword.isChecked, ShareMethod.FILE)`, and add a neutral button (`sftp_share_show_qr_action`) firing `onConfirm(!omitPassword.isChecked, ShareMethod.QR)`. Negative stays cancel. The password checkbox and private-network warning stay shared by both methods. Update the KDoc to mention the two share methods.

**Verification:**

- `Grep` - `enum class ShareMethod` present.
- `Grep` - `setNeutralButton` present referencing `sftp_share_show_qr_action`.
- `Grep` - callback signature includes `method: ShareMethod`.

**Status:** `[ ]` not done

---

### Step 05.3 - Route the choice in `MainActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Update the `onShareSftpAccessClick` lambda (~line 951) so the `sftpShareManager.show(resource) { includePassword, method -> .. }` block routes on `method`: `MainSftpShareManager.ShareMethod.FILE -> viewModel.shareSftpResourceConfig(resource, includePassword)`; `MainSftpShareManager.ShareMethod.QR -> viewModel.shareSftpResourceConfigAsQr(resource, includePassword)`. Use a `when` on `method`, no `else` (exhaustive over the enum). Keep the change inside the existing lambda - do not add new helper methods to the Activity (LOC ceiling).

**Verification:**

- `Grep` - `shareSftpResourceConfigAsQr` referenced in `MainActivity.kt`.
- `Grep` - `ShareMethod.QR` referenced in `MainActivity.kt`.
- Project compiles - run `/build`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The QR share path is reachable end-to-end: resource menu -> "Share access" dialog -> "Show QR" -> `CompanionQrShareActivity`. Remaining work is catalog/inventory/docs bookkeeping.

---

## Rollback Plan

Revert the phase commit - restores the single-action dialog (file share only). The domain plumbing from Phase 04 stays dormant.
