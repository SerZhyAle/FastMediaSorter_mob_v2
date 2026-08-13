# Phase 05 — docs-catalog-cleanup

**Strategic spec:** [`../S0183_nolegal-apk-install.md`](../S0183_nolegal-apk-install.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Regenerate the class catalog, add dev changelog entries, and update `docs/FEATURES_noLegal.md` + mirrors with the new APK install capability.

---

## Prerequisites

- [ ] All preceding phases are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |
| `dev/CHANGELOG.md` | Modified (via script) | — |
| `docs/FEATURES_noLegal.md` | Modified | +15 lines |
| `docs/FEATURES_noLegal_RU.md` | Modified | +15 lines |
| `docs/FEATURES_noLegal_UK.md` | Modified | +15 lines |

---

## Steps

### Step 5.1 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run catalog scan and render for the `app_v2` module:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File "dev/CATALOG/scripts/scan.ps1" -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File "dev/CATALOG/scripts/render.ps1" -Module app_v2
> ```
> Then set `role` and `status` for the two new noLegal classes via `set.ps1`:
> ```powershell
> # BrowseApkInstallHandlerImpl
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File "dev/CATALOG/scripts/set.ps1" -Module app_v2 `
>     -Class "BrowseApkInstallHandlerImpl" -Role "noLegal APK install handler; full install flow, launcher registration, permission rationale" -Status "active"
> # BrowseApkInstallModule
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File "dev/CATALOG/scripts/set.ps1" -Module app_v2 `
>     -Class "BrowseApkInstallModule" -Role "Hilt @Binds module; provides BrowseApkInstallHandlerImpl as BrowseApkInstallHandler (noLegal only)" -Status "active"
> ```
> Also set roles for the two main-sourceSet classes:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File "dev/CATALOG/scripts/set.ps1" -Module app_v2 `
>     -Class "BrowseApkInstallHandler" -Role "abstract contract for APK install flow; injected as Optional into BrowseManagerInitializer" -Status "active"
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File "dev/CATALOG/scripts/set.ps1" -Module app_v2 `
>     -Class "BrowseApkInstallOptionalModule" -Role "Hilt @BindsOptionalOf module; absent in market flavors" -Status "active"
> ```

**Verification:**

- `Grep` in `dev/CATALOG/app_v2.jsonl` — `BrowseApkInstallHandlerImpl` present.
- `Grep` in `dev/CATALOG/app_v2.jsonl` — `BrowseApkInstallHandler` present.
- `dev/CATALOG/app_v2.md` modification timestamp is newer than Phase 04 completion.

**Status:** `[x] done`

**Step Log:**
- 2026-05-14 — Verification 2/2 PASS. scan.ps1 + render.ps1 run; roles set for all 4 classes (new). Dev log recorded.

---

### Step 5.2 — Add dev changelog entries

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 5.1

**Prompt for developer:**

> Run `add_to_dev_log.ps1` for every file modified across all phases. Minimum entries:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandler.kt" "S0183" "New abstract class: contract for noLegal APK install flow"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/di/BrowseApkInstallOptionalModule.kt" "S0183" "New Hilt @BindsOptionalOf module for BrowseApkInstallHandler"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/BrowseApkInstallHandlerImpl.kt" "S0183" "New noLegal APK install handler: permission check, rationale dialog, FileProvider URI, ACTION_INSTALL_PACKAGE flow"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/di/BrowseApkInstallModule.kt" "S0183" "New Hilt @Binds module for noLegal APK install handler"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/AndroidManifest.xml" "S0183" "Add REQUEST_INSTALL_PACKAGES permission (noLegal only)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0183" "Add s0183_* APK install strings (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0183" "Add s0183_* APK install strings (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0183" "Add s0183_* APK install strings (UK)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/bottom_sheet_binary_file.xml" "S0183" "Add btnInstallApk (gone by default; visible in noLegal for .apk files)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt" "S0183" "Accept optional BrowseApkInstallHandler; show Install button for .apk in noLegal"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt" "S0183" "Inject Optional<BrowseApkInstallHandler>; call registerLaunchers; pass to BrowseBinaryFileHandler"
> ```

**Verification:**

- `Grep` in `dev/CHANGELOG.md` — `S0183` present (≥11 occurrences).

**Status:** `[x] done`

**Step Log:**
- 2026-05-14 — Verification 1/1 PASS. 27 S0183 entries in CHANGELOG.md (≥11). Dev log recorded for all 11 phase files.

---

### Step 5.3 — Update `docs/FEATURES_noLegal.md` + RU + UK

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** Step 5.2

**Prompt for developer:**

> Add entry `§3. APK Install from Browse` to all three `FEATURES_noLegal` files. Place it after §2 (Native Kotlin Site Extractors). Follow the existing entry template.
>
> **EN entry (`docs/FEATURES_noLegal.md`):**
> ```markdown
> ### 3. APK Install from Browse
>
> **Flavor gate:** `noLegal` only
> **Epic:** S0156
> **Spec:** S0183
>
> - Tap any `.apk` file in Browse to get an "Install" button in the file action menu.
> - Checks `REQUEST_INSTALL_PACKAGES` permission and routes to system Settings if not granted.
> - Launches the system PackageInstaller UI — the user always sees and confirms the install dialog.
> - Reports install success, cancellation, or failure via a toast.
> - **Why not in market builds:** `REQUEST_INSTALL_PACKAGES` triggers Google Play high-risk permission review and is rejected for file-manager use cases without explicit MDM/enterprise/store context.
> ```
>
> Write equivalent entries in RU and UK using the same structure. Do not add entries to `docs/FEATURES.md`, `docs/FEATURES_RU.md`, or `docs/FEATURES_UK.md` — those are public files.

**Verification:**

- `Grep` in `docs/FEATURES_noLegal.md` — `S0183` present.
- `Grep` in `docs/FEATURES_noLegal_RU.md` — `S0183` present.
- `Grep` in `docs/FEATURES_noLegal_UK.md` — `S0183` present.
- `Grep` in `docs/FEATURES.md` — `S0183` — zero hits (must not appear in public file).

**Status:** `[x] done`

**Step Log:**
- 2026-05-14 — Verification 4/4 PASS. §3 APK Install added to FEATURES_noLegal + RU + UK. S0183 absent in public FEATURES.md. Dev log recorded.

---

### Step 5.4 — Advance spec status and run `/spec-check`

**Files:** `PLAN/spec-catalog.jsonl` (via script)
**Depends on:** Step 5.3

**Prompt for developer:**

> Run:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File "scripts/spec_catalog/update.ps1" -Id S0183 -Status Implemented
> ```
> Then run `/spec-check S0183` to advance to `Verified` after on-device test passes.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0183 -Format json` — `status` field is `Implemented` or `Verified`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-14 — Verification 1/1 PASS. S0183 status → Implemented via update.ps1.

---

## Phase Done Criteria

- [x] Every `Step 5.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` contains entries for all 4 new classes.
- [x] `dev/CHANGELOG.md` has ≥11 `S0183` entries (27 found).
- [x] `docs/FEATURES_noLegal.md` entry §3 present.
- [x] `docs/FEATURES.md` unchanged (0 S0183 hits).

---

## Handoff Notes to Next Phase

Final phase. Run `/spec-check S0183` after on-device test to advance to `Verified`.

---

## Rollback Plan

Final cleanup phase — no functional code changed. Revert doc changes if needed.
