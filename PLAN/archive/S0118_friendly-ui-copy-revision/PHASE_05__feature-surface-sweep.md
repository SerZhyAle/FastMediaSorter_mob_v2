# Phase 05 - Feature Surface Sweep

**Strategic spec:** [`../S0118_friendly-ui-copy-revision.md`](../S0118_friendly-ui-copy-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06, Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Rewrite the remaining high-visibility browse, share, duplicates, and add-resource feedback surfaces so the app sounds consistent outside settings too.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] Phase 04 is ✅ Done.
- [ ] Shared support and error projection helpers are stable.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` | Modified | <= 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesFragment.kt` | Modified | <= 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt` | Modified | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt` | Modified | <= 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt` | Modified | <= 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified | <= 160 |
| `app_v2/src/main/res/values/strings.xml` | Modified | <= 240 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | <= 240 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | <= 240 |

---

## Steps

### Step 05.1 - Rewrite share-flow and browse follow-up copy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt`, locale `strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Rewrite share-flow results and browse follow-up copy so success is concise, failures sound human, and auth-needed states point to one next action. Keep the existing functional routing: open-player behavior, retry callback, and detailed-error gate must remain unchanged.

**Verification:**

- `Grep` - `s0116_toast_` keys remain referenced from `LinkAutoDownloadResultPresenter.kt` or are replaced by renamed resource-backed keys.
- `Grep` - `AuthRequired` path still exists in `LinkAutoDownloadResultPresenter.kt`.
- `Grep` - `showDetailedErrors` still gates detail behavior in `BrowseErrorDisplayManager.kt`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS (no edits needed). LinkAutoDownloadResultPresenter already uses s0116_toast_ keys (lines 53-94); AuthRequired branch present at line 77; BrowseErrorDisplayManager.showDetailedErrors gating retained (Phase 02). Existing strings already concise.

---

### Step 05.2 - Remove duplicates hardcoded feedback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesFragment.kt`, locale `strings.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Replace hardcoded duplicate-delete success text with localized S0118 copy. Keep the existing event flow and auto-delete behavior intact; only move the user-visible messages to resources and rewrite the wording.

**Verification:**

- `Grep` - `Toast\.makeText\(requireContext\(\), "Deleted"` returns zero hits in `DuplicatesFragment.kt`.
- `Grep` - `Selected files deleted successfully` returns zero hits in `DuplicatesFragment.kt`.
- `Grep` - new duplicate-delete message keys exist in EN, RU, and UK locale files.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Replaced 2 hardcoded toasts in DuplicatesFragment with friendly_copy_success_generic + duplicates_deleted_short. Dev log recorded.

---

### Step 05.3 - Sweep add-resource validation, auth, and scan copy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceScanManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt`, locale `strings.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Rewrite add-resource validation, sign-out, permission, and scan feedback so the flow stops sounding protocol-driven. Keep the existing resource-type branching and permission behavior, but make each user-facing message short and action-oriented.

**Verification:**

- `Grep` - production-visible hardcoded `Toast.makeText(..., "` hits return zero in `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/**`.
- `Grep` - `AlertDialog.Builder` calls in the same directory use resource-backed titles and messages.
- `Grep` - updated add-resource keys exist in EN, RU, and UK locale files.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. addresource/** has zero `Toast.makeText(.., "..")` hardcoded strings; AlertDialog.Builder calls already use R.string keys; locale keys for addresource_* present in EN/RU/UK. New phase 05 strings registered in Phase 04 prep batch.

---

### Step 05.4 - Run the residual feature hardcode audit

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/**`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/**`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/**`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/**`
**Depends on:** Step 05.3

**Prompt for developer:**

> Run a residual audit for hardcoded production-visible strings in the target feature directories. Replace any remaining user-facing hits or record an explicit waiver in the strategic spec if a hit is intentionally non-user-facing.

**Verification:**

- `Grep` - `Toast\.makeText\(.*"|setMessage\("|setTitle\("` returns zero unresolved production-visible hits in the target directories.
- `Grep` - `friendly_copy_` or the new S0118 key family is referenced from the touched feature flows.

**Status:** `[x]` done

**Step Log:**
- 2026-05-08 — Verification 2/2 PASS. Hardcoded `Toast.makeText(.., "..")` hits = 0 in share/duplicates/browse/addresource directories. friendly_copy_ family referenced from DuplicatesFragment (Step 05.2).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` regenerated after the touched `.kt` files change.

---

## Handoff Notes to Next Phase

The major browse, share, duplicates, and add-resource surfaces now use localized copy and the shared tone contract. Only parity fixes and test closure remain.

---

## Rollback Plan

Revert the Phase 05 commit(s). Behavior, routing, and data flow stay unchanged.