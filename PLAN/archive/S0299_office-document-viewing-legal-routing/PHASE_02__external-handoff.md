# Phase 02 - External Handoff

**Strategic spec:** [`../S0299_office-document-viewing-legal-routing.md`](../S0299_office-document-viewing-legal-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-28
**Completed:** 2026-05-28

---

## Objective

Route Office documents to installed external viewers from Browse/Player and standalone document intents.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] UI behavior delegated by owner: direct external handoff with localized missing-viewer fallback.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentOpenManager.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/MediaDisplayCoordinator.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerUiStateCoordinatorCallbackImpl.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerShareManager.kt` | Modified | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | ≤ 660 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt` | Modified | ≤ 180 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoLaunchManager.kt` | Modified | ≤ 320 |

---

## Steps

### Step 02.1 - Add external Office document opener

**Files:** `OfficeDocumentOpenManager.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Create a helper that opens a prepared Office document file or URI with `ACTION_VIEW`, grants read permission, uses the Office MIME from `MediaTypeUtils`, and excludes this app package from the chooser candidates to avoid self-launch loops.

**Verification:**

- `Glob` - `OfficeDocumentOpenManager.kt` exists.
- `Grep` - `queryIntentActivities` exists in `OfficeDocumentOpenManager.kt`.
- `Grep` - `activity.packageName` exists in `OfficeDocumentOpenManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 3/3 PASS. Files: `OfficeDocumentOpenManager.kt`. Helper creates explicit external viewer intents and excludes this package.

### Step 02.2 - Route PlayerActivity Office documents to the opener

**Files:** `MediaDisplayCoordinator.kt`, `PlayerUiStateCoordinator.kt`, `PlayerUiStateCoordinatorCallbackImpl.kt`, `PlayerShareManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a `displayOfficeDocument` callback path. Prepare network/cloud/content Office files through `NetworkFileManager.prepareFileForRead`, launch the external viewer, and finish PlayerActivity because FMS does not render Office documents internally.

**Verification:**

- `Grep` - `displayOfficeDocument` exists in `MediaDisplayCoordinator.kt`, `PlayerUiStateCoordinator.kt`, and `PlayerUiStateCoordinatorCallbackImpl.kt`.
- `Grep` - `openOfficeDocument` exists in `PlayerShareManager.kt`.
- `Grep` - `prepareFileForRead` exists in `PlayerShareManager.kt`.
- `Grep` - `OFFICE_DOCUMENT` exists in `MediaDisplayCoordinator.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 4/4 PASS. Files: Player display coordinator path. Browse-launched Office files now prepare through `NetworkFileManager` and hand off externally.

### Step 02.3 - Route standalone document intents to the opener

**Files:** `StandaloneViewManager.kt`, `AndroidManifest.xml`, `DefaultPlayerHelper.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Make `StandaloneDocsPlayer` accept DOC, DOCX, RTF, and ODT MIME types. When `StandalonePlayerActivity` resolves `OFFICE_DOCUMENT`, prepare the received URI via `NetworkFileManager` and hand it to the external opener.

**Verification:**

- `Grep` - `application/msword` exists in `AndroidManifest.xml`.
- `Grep` - `showOfficeDocument` exists in `StandaloneViewManager.kt`.
- `Grep` - `application/vnd.openxmlformats-officedocument.wordprocessingml.document` exists in `DefaultPlayerHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 3/3 PASS. Files: `StandaloneViewManager.kt`, `AndroidManifest.xml`, `DefaultPlayerHelper.kt`. Standalone document alias accepts Office MIME and redirects to external viewers.

### Step 02.4 - Keep secondary open-with MIME routing coherent

**Files:** `BrowseBinaryFileHandler.kt`, `FileInfoLaunchManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Reuse Office MIME resolution in existing open/share helpers so Office files never fall back to `application/octet-stream` when they are opened through a secondary action.

**Verification:**

- `Grep` - `officeMimeTypeForFileName` exists in every file listed for this step.
- `Grep` - no `TODO(phase-02)` hits under `app_v2/src/main/java`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 2/2 PASS. Files: `BrowseBinaryFileHandler.kt`, `FileInfoLaunchManager.kt`. Secondary open-with MIME paths use Office MIME resolution.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\build-debug.PS1` exit 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every modified file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exit 0.

---

## Handoff Notes to Next Phase

Office documents open externally and never invoke an embedded parser in `standard`.

---

## Rollback Plan

Revert phase edits; no data migration or dependency addition is introduced.
