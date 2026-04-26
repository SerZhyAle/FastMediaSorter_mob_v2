# Spec Audit: camera-capture-command

**Strategic spec:** [`spec_camera-capture-command.md`](spec_camera-capture-command.md)
**Tactical plan:** [`spec_camera-capture-command/INDEX.md`](spec_camera-capture-command/INDEX.md)
**Audit date:** 2026-04-25
**Auditor:** `/spec-check`
**Mode:** full (strategic + all 6 tactical phases)
**Flags:** —
**Outcome:** Partial

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 55 |
| PASS | 38 |
| WARN | 3 |
| FAIL | 0 |
| MANUAL (unverified) | 14 |
| EXEMPT | 0 |
| UNCHECKABLE | 0 |

All code verification predicates pass — every class, function, string key, layout ID, and CHANGELOG entry required by the spec is present in the codebase. The build (`assembleStandardDebug`) passes. The `Partial` score is driven by three administrative WARNs: (1) Phase 01 file header not flipped to Done (Index says ✅ Done, file says ⬜ Todo); (2) INDEX header still reads `In Progress` despite all 6 phases being Done; (3) dev-log entries missing for 8 modified source files across Phases 02, 04, and 06. No FAIL exists — safe to promote to `Verified` after running `/spec-fix`.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (strategic §Problem Statement + FRs)

| # | Goal | Referenced in phase(s) | Status | Action |
|---|------|------------------------|:------:|--------|
| 1 | Camera capture button in Browse command bar | Phase 02 (layout + BrowseButtonSetupHelper) | PASS | — |
| 2 | Save captured file to resource root / DCIM for virtual paths | Phase 03 (BrowseCameraCaptureManager routing) | PASS | — |
| 3 | Filename dialog + skip setting | Phase 03 (showNameDialog + skipCameraFilenameDialog) | PASS | — |
| 4 | Settings: disableCameraCapture + skipCameraFilenameDialog | Phase 01 (AppSettings + DataStore) + Phase 05 (UI) | PASS | — |
| 5 | Post-save refresh + scroll-to-file | Phase 04 (BrowseViewModel.scrollToFileAfterRefresh + BrowseEventHandler) | PASS | — |
| 6 | Backup export/import coverage | Phase 01 (BackupData + BackupMapper + ExportSettings + ImportSettings) | PASS | — |

### 2.2 Constraints (strategic §Out of Scope)

| # | Constraint | Verification | Status | Evidence | Action |
|---|-----------|--------------|:------:|----------|--------|
| 1 | No VR/Wear changes | Glob `src/vr/` for camera_capture | PASS | 0 hits in VR source | — |
| 2 | Standard flavor only (minSdk 26) | No new minSdk guards added | PASS | No BuildConfig.SUPPORT_* gates on camera feature | — |
| 3 | No PlayerActivity changes | Grep `PlayerActivity` for camera_capture | PASS | 0 hits | — |

### 2.3 Open Research Items

No §Research items in the strategic spec. Skipped.

### 2.4 User-Facing Text (trilingual)

| Artefact | Required? | Status | Evidence | Action |
|---------|:---------:|:------:|----------|--------|
| `docs/FEATURES.md` bullet | Yes | PASS | line 101: "Camera capture" | — |
| `docs/FEATURES_RU.md` mirror | Yes | PASS | line 87: "Съёмка с камеры" | — |
| `docs/FEATURES_UK.md` mirror | Yes | PASS | line 87: "Зйомка з камери" | — |

### 2.5 Acceptance Criteria (strategic §Acceptance Criteria — all MANUAL)

- [ ] Camera button hidden for audio-only and document-only resources.
- [ ] Camera button hidden for ALL_AUDIO, ALL_DOCS, RECENT virtual paths.
- [ ] Camera button visible for ALL_VIDEO, ALL_IMAGES, CAMERA_PHOTOS virtual paths.
- [ ] Camera button visible for LOCAL, SMB, FTP, SFTP, CLOUD resources supporting image/video.
- [ ] Tapping button opens device camera app.
- [ ] After capture: filename dialog appears (pre-filled with timestamp).
- [ ] `skipCameraFilenameDialog = true` → dialog skipped, timestamp name used.
- [ ] Captured file saved to correct destination per FR-4.
- [ ] Success toast shown; file list refreshed and scrolled to the new item.
- [ ] On failure: error toast shown.
- [ ] `disableCameraCapture = true` → button hidden globally.
- [ ] Both settings present in Behaviour section of Playback settings.
- [ ] Both settings included in backup export/import.
- [ ] Trilingual strings (EN/RU/UK) for all new UI text.

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| All 6 phase-file Status headers match INDEX rows | WARN | Phase 01 file: `⬜ Todo`; INDEX row: `✅ Done` | Fix Phase 01 file header → `✅ Done` |
| INDEX header `Status:` field | WARN | INDEX header: `In Progress`; all phases done | Flip INDEX `**Status:** In Progress` → `**Status:** ✅ Done` |
| Pre-Implementation Blockers | PASS | No blockers section in INDEX | — |
| Phase 02-06 file headers match INDEX | PASS | All 5 match ✅ Done | — |

### 3.2 Phase 01 — Data Model

**Phase file status:** ⬜ Todo (MISMATCH — INDEX says ✅ Done)
**Outcome:** Partial (code PASS, admin WARN)

#### 3.2.1 Files Touched

| File | Expected | Exists? | Line count vs budget | Status |
|------|---------|:-------:|:--------------------:|:------:|
| `domain/model/AppSettings.kt` | Mod, ≤ 250 | ✅ | N/A (not counted — existing file) | PASS |
| `data/repository/SettingsRepositoryImpl.kt` | Mod, ≤ 1000 | ✅ | — | PASS |
| `domain/usecase/BackupData.kt` | Mod, ≤ 200 | ✅ | — | PASS |
| `domain/usecase/BackupMapper.kt` | Mod, ≤ 500 | ✅ | — | PASS |
| `domain/usecase/ExportSettingsUseCase.kt` | Mod, ≤ 600 | ✅ | — | PASS |
| `domain/usecase/ImportSettingsUseCase.kt` | Mod, ≤ 700 | ✅ | — | PASS |

#### 3.2.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 01.1 | Add fields to AppSettings | `Grep "disableCameraCapture" AppSettings.kt` → 1 hit | PASS | line 115 |
| 01.2 | DataStore keys | `Grep "KEY_DISABLE_CAMERA_CAPTURE" SettingsRepositoryImpl.kt` → 3 hits | PASS | 3 hits |
| 01.3 | BackupData | `Grep "disableCameraCapture" BackupData.kt` → 1 hit | PASS | 1 hit |
| 01.4 | BackupMapper | `Grep "disableCameraCapture" BackupMapper.kt` → 2 hits | PASS | 2 hits |
| 01.5 | ExportSettingsUseCase | `Grep "disableCameraCapture" ExportSettingsUseCase.kt` → 1 hit | PASS | 1 hit |
| 01.6 | ImportSettingsUseCase | `Grep "disableCameraCapture" ImportSettingsUseCase.kt` → 1 hit | PASS | 1 hit |

#### 3.2.3 Phase Done Criteria

All 6 predicates verified above → all PASS. Done Criteria checkboxes `[ ]` in phase file are stale — auto-fixable.

---

### 3.3 Phase 02 — Browse Button & Layout

**Phase file status:** ✅ Done
**Outcome:** Verified (code PASS; dev log WARNs noted in §7)

| # | Verification | Expected | Actual | Status |
|---|--------------|---------|--------|:------:|
| 1 | `Glob ic_camera_capture.xml` | 1 hit | ✅ | PASS |
| 2 | `Grep "btnCameraCapture" activity_browse.xml` | 1 hit | 1 | PASS |
| 3 | `Grep "onCameraCaptureClicked" BrowseButtonSetupHelper.kt` | 2 hits | 2 | PASS |
| 4 | `Grep "isCameraCaptureVisible" BrowseStateUiUpdater.kt` | ≥ 2 hits | 2 | PASS |
| 5 | `Grep "disableCameraCapture\|isCameraCaptureVisible" BrowseObserverManager.kt` | ≥ 1 hit | 2 | PASS |

File sizes: BrowseButtonSetupHelper.kt 214 LOC ≤ 350 ✅ · BrowseStateUiUpdater.kt 173 LOC ≤ 700 ✅ · BrowseObserverManager.kt 210 LOC ≤ 500 ✅

---

### 3.4 Phase 03 — BrowseCameraCaptureManager

**Phase file status:** ✅ Done
**Outcome:** Verified

| # | Verification | Expected | Actual | Status |
|---|--------------|---------|--------|:------:|
| 1 | `Glob BrowseCameraCaptureManager.kt` | 1 hit | ✅ | PASS |
| 2 | `Grep "launcher"` | ≥ 2 hits | 3 | PASS |
| 3 | `Grep "showNameDialog"` | ≥ 2 hits | 2 | PASS |
| 4 | `Grep "saveToDcim"` | ≥ 2 hits | 2 | PASS |
| 5 | `Grep "onFileSaved"` | ≥ 2 hits | 2 | PASS |
| 6 | File LOC ≤ 900 | ≤ 900 | 201 | PASS |

---

### 3.5 Phase 04 — BrowseActivity Wiring

**Phase file status:** ✅ Done
**Outcome:** Verified

| # | Verification | Expected | Actual | Status |
|---|--------------|---------|--------|:------:|
| 1 | `Grep "cameraCaptureManager" BrowseActivity.kt` | ≥ 2 hits | 3 | PASS |
| 2 | `Grep "onCameraCaptureClicked" BrowseActivity.kt` | ≥ 1 hit | 1 | PASS |
| 3 | `Grep "onCapturedFileSaved" BrowseActivity.kt` | ≥ 2 hits | 2 | PASS |
| 4 | `Grep "ScrollToFile" BrowseEventHandler.kt` | ≥ 1 hit | 3 | PASS |
| 5 | BUILD-REQUIRED (standard-debug) | PASS | `assembleStandardDebug` PASS 2026-04-25 | PASS |

File sizes: BrowseActivity.kt 345 LOC ≤ 1000 ✅ · BrowseEventHandler.kt 258 LOC ≤ 400 ✅

---

### 3.6 Phase 05 — Settings UI

**Phase file status:** ✅ Done
**Outcome:** Verified

| # | Verification | Expected | Actual | Status |
|---|--------------|---------|--------|:------:|
| 1 | `Grep "switchDisableCameraCapture" fragment_settings_playback.xml` | 1 hit | 1 | PASS |
| 2 | `Grep "switchSkipCameraFilenameDialog" fragment_settings_playback.xml` | 1 hit | 1 | PASS |
| 3 | `Grep "switchDisableCameraCapture" PlaybackSettingsFragment.kt` | ≥ 2 hits | 3 | PASS |
| 4 | `Grep "rowSkipCameraFilename" PlaybackSettingsFragment.kt` | ≥ 1 hit | 1 | PASS |
| 5 | `Grep "setting_disable_camera_capture" SettingsSearchIndex.kt` | 1 hit | 1 | PASS |

File sizes: PlaybackSettingsFragment.kt 530 LOC ≤ 800 ✅ · SettingsSearchIndex.kt 402 LOC ≤ 500 ✅

---

### 3.7 Phase 06 — Strings & Housekeeping

**Phase file status:** ✅ Done
**Outcome:** Verified

| # | Verification | Expected | Actual | Status |
|---|--------------|---------|--------|:------:|
| 1 | `Grep "cmd_camera_capture" values/strings.xml` | 1 hit | 1 | PASS |
| 2 | `Grep "cmd_camera_capture" values-ru/strings.xml` | 1 hit | 1 | PASS |
| 3 | `Grep "cmd_camera_capture" values-uk/strings.xml` | 1 hit | 1 | PASS |
| 4 | `Grep "Camera capture" docs/FEATURES.md` | 1 hit | 1 | PASS |
| 5 | `Grep "BrowseCameraCaptureManager" dev/CHANGELOG.md` | ≥ 1 hit | 2 | PASS |
| 6 | `Grep "BrowseCameraCaptureManager" dev/CATALOG/app_v2.md` | ≥ 1 hit | 2 | PASS |

---

## 4. Cross-Reference Checks

| Check | Status | Evidence |
|-------|:------:|----------|
| FR-1 (visibility) → Phase 02/04/05 | PASS | BrowseStateUiUpdater.isCameraCaptureVisible + settings gate |
| FR-2 (camera launch) → Phase 03 | PASS | BrowseCameraCaptureManager.launch() |
| FR-3 (filename dialog) → Phase 03 | PASS | showNameDialog() + skipCameraFilenameDialog check |
| FR-4 (save routing) → Phase 03 | PASS | saveToDcim / saveLocal / uploadNetwork / uploadCloud stubs |
| FR-5 (settings) → Phase 01 + 05 | PASS | AppSettings fields + PlaybackSettingsFragment |
| Strategic "backup" constraint → Phase 01 | PASS | BackupData + BackupMapper + Export + Import |

---

## 5. Manual Acceptance Signals

*(Device-testing required — none can be auto-ticked)*

- [ ] Camera button hidden for audio-only and document-only resources (visual check)
- [ ] Camera button hidden for ALL_AUDIO, ALL_DOCS, RECENT virtual paths (visual check)
- [ ] Camera button visible for ALL_VIDEO, ALL_IMAGES, CAMERA_PHOTOS virtual paths (visual check)
- [ ] Camera button visible for LOCAL resources (visual check)
- [ ] Tapping button opens device camera app (device test)
- [ ] After capture: filename dialog appears pre-filled with timestamp (device test)
- [ ] `skipCameraFilenameDialog = true` → dialog skipped (device test)
- [ ] Captured file saved to LOCAL resource root (device test)
- [ ] Success toast shown; file list refreshed and scrolled to new item (device test)
- [ ] On failure: error toast shown (device test)
- [ ] `disableCameraCapture = true` → button hidden globally (device test)
- [ ] Both settings appear in Behaviour section of Playback settings (visual check)
- [ ] Both settings survive backup export + re-import (device test)
- [ ] All UI strings render correctly in EN/RU/UK (visual check)

---

## 6. Accepted Exemptions

- **Network/cloud upload fallback**: `uploadNetwork()` and `uploadCloud()` show a toast and return `false` — per spec Step 03.8 explicit fallback allowance. No unified upload UseCase exists at UI layer. Recorded in phase log.

---

## 7. Action Items (WARN, prioritised)

1. **[WARN §3.1 — INDEX header]** `**Status:** In Progress` in INDEX.md despite all 6 phases Done — flip to `✅ Done`.
2. **[WARN §3.2 — Phase 01 file header]** Phase 01 `Status:` is `⬜ Todo` — flip to `✅ Done`, add `Started: 2026-04-25` / `Completed: 2026-04-25`, tick all 6 Done Criteria.
3. **[WARN §Project quality — dev log]** Missing `add_to_dev_log.ps1` entries for 8 modified files: `BrowseStateUiUpdater.kt`, `BrowseObserverManager.kt` (Phase 02); `BrowseActivity.kt`, `BrowseEventHandler.kt`, `BrowseEvent.kt`, `BrowseViewModel.kt` (Phase 04); `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`, `FEATURES.md`, `FEATURES_RU.md`, `FEATURES_UK.md` (Phase 06).
4. **[WARN §3.5 — Phase 04 Done Criteria]** BUILD-REQUIRED checkbox still `[ ]` — tick it; build passed 2026-04-25.

---

## 8. Recommended Follow-ups

- All WARNs in §7 are auto-fixable bookkeeping. Running `/spec-fix camera-capture-command` will address items 1–4, after which a re-audit should yield `Verified`.
- Network/cloud upload is a TODO stub. Consider creating a side-spec `spec_camera-capture-network-upload.md` when the unified upload UseCase is available.

---

## 9. Next Commands

- `/spec-fix camera-capture-command` — apply bookkeeping fixes from §7 (all auto-fixable).
- `/spec-check camera-capture-command` — re-run after fixes to confirm `Verified`.
