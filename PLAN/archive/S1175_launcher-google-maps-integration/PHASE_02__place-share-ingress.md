# Phase 02 - Place Share Ingress

**Strategic spec:** [`../S1175_launcher-google-maps-integration.md`](../S1175_launcher-google-maps-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 2 / 2

## Objective

Receive a place shared from Maps only while Launcher Mode is available, then place its cell in the desktop.

## Steps

### Step 02.1 - Parse a shared place and place its cell

**Files:** new `ui/launcher/share/LauncherPlaceShareActivity.kt`, new `domain/usecase/launcher/AcceptLauncherPlaceUseCase.kt`, share-parser tests
**Depends on:** Phase 01

**Prompt for developer:**

> Parse coordinates first, then useful free text, then the received URL. Add a route cell on successful input or a place-display cell with a one-time explanatory toast when only a URL can be retained.

**Why:** Strategic criteria 1 and 8 define Share as the only place input and require an honest fallback when a route cannot be formed.

**Verification:**

- Parser unit tests cover coordinate, text, URL, and blank payloads.
- Done: parsing extracted to `LauncherPlaceShareParser`; `LauncherPlaceShareParserTest` runs 6 tests, 0 failures (`check-standard-fast.ps1 -Mode Unit -Tests "*LauncherPlaceShareParserTest*"`, exit 0).

**Status:** `[x]` done

### Step 02.2 - Gate the dedicated share alias with HOME

**Files:** `src/launcherEnabled/AndroidManifest.xml`, `core/launcher/LauncherRoleManager.kt`, launcher string and drawable resources
**Depends on:** Step 02.1

**Prompt for developer:**

> Declare a separately labelled `text/plain` share alias for the new receiver and enable or disable it in the same component transaction as the HOME activity.

**Why:** Strategic ADR-8 prevents a share from creating a cell on a desktop the user cannot access and avoids conflating file import with place sharing.

**Verification:**

- Standard manifest packages the alias only for launcher-enabled flavors.
- `a.ps1 fc` passes.
- Done: alias `.LauncherPlaceShare` carries its own label and icon and is flipped inside `setLauncherComponentsEnabled` together with the HOME component; `app_v2/build.gradle.kts` injects `src/launcherEnabled/AndroidManifest.xml` only for `standard` and `noLegal`. `check-standard-fast.ps1 -Mode CodeAndResources` exit 0.

**Status:** `[x]` done

## Phase Done Criteria

- [x] All steps are `[x] done`.
- [x] `a.ps1 fc` passes.
