# Phase 02 - Add-resource contextual help link

**Strategic spec:** [`../S0994_companion-publish-folders-help-link.md`](../S0994_companion-publish-folders-help-link.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-07-11
**Completed:** 2026-07-11 (compile proof: shared `fc` - BUILD SUCCESSFUL)

---

## Objective

Add a contextual help affordance next to the companion import block on the add-resource screen that opens the publish-folders guide; visibility inherits the existing companion/SFTP gating.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (URL accessor + label string exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_add_resource.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 500 |

> Landscape parity: `res/layout-land/activity_add_resource.xml` does not exist - the screen uses a single portrait layout. No landscape counterpart to edit.

---

## Steps

### Step 02.1 - Add the help affordance to the companion block layout

**Files:** `app_v2/src/main/res/layout/activity_add_resource.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the SFTP companion block (near `btnSftpImportCompanion` / `btnSftpScanCompanionQr`), add a small help affordance (a text link or icon button) labelled by `@string/companion_publish_folders_guide` with a matching `contentDescription`. Make it `focusable`, `clickable`, with correct `nextFocus*` wiring for D-pad. No hardcoded hex colors - use `?attr/`/`@color/` (Rule 19). Keep it inside `systemBars`/`displayCutout` safe bounds.

**Verification:**

- `Grep` - a new view id (e.g. `btnSftpCompanionPublishHelp`) is present in `activity_add_resource.xml`.
- `Grep` - `@string/companion_publish_folders_guide` referenced in the layout.
- `Grep` - no `="#` hex literal added in the new block.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - Verification 3/3 PASS. Added `btnSftpCompanionPublishHelp` (Text style, `ic_help_outline_24`) after the companion button row; label + contentDescription = `@string/companion_publish_folders_guide`; no hex.

---

### Step 02.2 - Wire the click and inherit companion gating

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Wire the new affordance to open `SupportIntentFactory.companionPublishGuideUrl()` via `SupportIntentFactory.openUrl(..)` / `startActivity`, guarding against no-browser exactly like the settings link buttons. Its visibility must mirror the file-import companion button (shown whenever the SFTP/companion block is available), NOT the camera QR gate - so it appears on standard/photos/legacy/noLegal and is hidden wherever the companion block is hidden (lite/vr). Add the required `Timber.d("S0994: <entry-point>")` probe at this click flow entry (ticket enters `BlockNeedUserTest`). Logging via Timber only.

**Verification:**

- `Grep` - `companionPublishGuideUrl(` referenced in `AddResourceActivity.kt`.
- `Grep` - `Timber.d("S0994:` present exactly once for this flow.
- `Grep -n "Log\.d\("` in `AddResourceActivity.kt` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-07-11 - Verification 3/3 PASS. `openCompanionPublishGuide()` wired via `SupportIntentFactory.companionPublishGuideUrl()`; `ActivityNotFoundException` -> `settings_no_browser_for_docs` toast; `Timber.d("S0994:` probe present; no `Log.d`. Reachability inherits SFTP form (no explicit isVisible, like `btnSftpImportCompanion`).

---

## Phase Done Criteria

- [ ] Both steps are `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for the touched files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Contextual entry point live and gated by companion availability. Phase 05 regenerates the catalog for the touched Kotlin.

---

## Rollback Plan

Revert the phase commit - additive view + click handler, no data migration or persistent surface changed.
