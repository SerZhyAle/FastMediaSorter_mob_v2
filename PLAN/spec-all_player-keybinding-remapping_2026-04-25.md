# spec-all pipeline log: player-keybinding-remapping

**Started:** 2026-04-25 (resumed — Stages 1-4 already completed in prior session)
**Idea source:** PLAN/spec_player-keybinding-remapping/INDEX.md
**Idea (excerpt):** Tactical Plan: player-keybinding-remapping. Strategic spec: ../spec_player-keybinding-remapping.md. Feature: PLAYER-KEYBINDING — Custom Playback Controls Remapping. Tier: 4 — Strategic (high risk). Status: In Progress. Phases: 1 / 8 done

---

## Stage log

- Stage 0 DONE — pipeline log initialised. Prior-run stages 1-4 confirmed via spec revision histories. Resuming from Stage 5 Phase 02 Done Criteria.
- Stage 1 DONE (prior run) — strategic spec created + Status: Approved → In Progress. File: PLAN/spec_player-keybinding-remapping.md
- Stage 2 DONE (prior run) — spec-update --apply-all applied. See spec Revision History.
- Stage 3 DONE (prior run) — tactical plan created. 8 phases. Index: PLAN/spec_player-keybinding-remapping/INDEX.md
- Stage 4 DONE (prior run) — spec-update --tactical --apply-all. See INDEX.md Revision History.
- Stage 5 Phase 01 DONE (prior run) — preparation-inventory. Steps: 8/8. Dev log recorded.

- Stage 5 Phase 04 DONE — input-devices-migration. Steps: 5/5. Build: PASS (assembleStandardDebug + 5 GamepadInputManagerTest).
  SPEC-PATCH (04.2): BROWSER surface stays on legacy literal tree (browser remapping out of scope for this feature).
  SPEC-PATCH (04.3): BUTTON_SECONDARY/TERTIARY/BACK/FORWARD constants removed; resolver-first dispatch via event.actionButton.
  NOTE: @AndroidEntryPoint added to MediaButtonRestartReceiver for Hilt injection.
- Stage 5 Phase 05 DONE 15:49 — vr-migration. Steps: 3/4 (Step 05.4 MANUAL-REQUIRED — on-device Quest 3 smoke test). Build: PASS (assembleStandardDebug + testVrDebugUnitTest, 35 tests).
  SPEC-PATCH (05.2a): InputSurface.VR_PLAYER in spec → actual enum value InputSurface.VR.
  SPEC-PATCH (05.2b): POINTER_CLICK_DOWN/UP special-cased before resolver (UI layer, no PlaybackCommand equivalent).
  MANUAL-REQUIRED (05.4): On-device Quest 3 smoke test deferred to human.
- Stage 5 Phase 02 DONE 10:00 — foundation. Steps: 8/8. Build: PASS (assembleStandardDebug, KeyBindingManagerTest). Catalog: scanned + 10 classes annotated.
  SPEC-PATCHED: set.ps1 -Status valid values are {new,tested,legacy,todo,unknown} — spec Phase 08 uses "stable" incorrectly; corrected to "new" everywhere.
- Stage 5 Phase 03 DONE — keyboard-migration. Steps: 5/6 (03.4 skipped OOS). Build: PASS (assembleStandardDebug + isolated unit tests).
  SPEC-PATCH (03.2): resolver intercepts PLAYER/VR_PLAYER surfaces only; legacy when(keyCode) tree retained for other surfaces.
  SPEC-PATCH (03.3): KEYCODE_ literals remain in debounce + scan-code table only; removed from public dispatch body.
  OOS-INLINE: 6 missing camera-capture strings added to all 3 locales; BrowseManagerInitializer.onCameraCaptureClicked() stub added (pre-existing spec_camera-capture-command gap).
- Stage 5 Phase 06 DONE 16:15 — remapping-ui. Steps: 6/7 (Step 06.7 MANUAL-REQUIRED — device smoke test deferred to human). Build: N/A (pre-build gate).
  Files created: SetBindingUseCase, ResetBindingUseCase, KeybindingRemapViewModel, KeybindingRowLabelFormatter, KeybindingListAdapter, CaptureDialogFragment, KeybindingRemapActivity, 3 XML layouts.
  Files modified: AndroidManifest.xml, SettingsActivity.kt, PlaybackSettingsFragment.kt, fragment_settings_playback.xml, strings.xml ×3 (EN/RU/UK, ~130 entries each).
  SPEC-PATCH (06.4): verification predicate `viewModel.state.collect` → `viewModel.state` (implementation uses `collectOnLifecycle` helper per project conventions).
  MANUAL-REQUIRED (06.7): On-device smoke test deferred to human.
  MANUAL-REQUIRED (Stage 5 Phase 06): clearAllOverrides added to InputBindingRepository to support per-row reset (delegates to dao.deleteByCommand).
- Stage 5 Phase 07 DONE 17:00 — reset-conflict-polish. Steps: 5/6 (Step 07.6 SKIPPED — §10 "undo window" resolved to immediate commit). Build: N/A (pre-build gate).
  Files created: ResetGroupUseCase.kt, ResetAllUseCase.kt, DetectConflictsUseCase.kt, ResetConfirmationDialog.kt.
  Files modified: InputBindingDao.kt (+deleteByCommandPrefix), InputBindingRepository.kt (+clearAllOverridesForGroup), KeybindingRemapViewModel.kt (+PendingConfirmation, conflicts, reset handlers), KeybindingListAdapter.kt (+group reset icon), KeybindingRemapActivity.kt (+handlePendingConfirmation), CaptureDialogFragment.kt (+block policy, tvConflict), dialog_capture_keybinding.xml (+tvConflict).
  SPEC-PATCH (07.3): Timber.d() present in ResetGroupUseCase/ResetAllUseCase — acceptable per Timber-only policy (not Log.d).
  Step 07.6: SKIPPED — Undo snackbar skipped per §10 resolution — immediate commit.
- Stage 5 Phase 08 DONE 17:05 — docs-catalog-cleanup. Steps: 5/5 (Step 08.5 N/A — file already absent). Build: N/A (pre-build gate).
  Files modified: docs/FEATURES.md, docs/FEATURES_RU.md, docs/FEATURES_UK.md (+Remappable controls bullet, Section 7), dev/CATALOG/app_v2.jsonl (regen + 20 classes annotated).
  Strategic spec: Status In Progress → Implemented. INDEX.md: Status Done, Phases 8/8.
  MANUAL-REQUIRED (Stage 5 aggregate): On-device E2E smoke test (06.7, 07 conflict visualiser, 07 group/global reset) deferred to human.
- Stage 6 DONE 17:10 — build gate. standard-debug: PASS (35s, 55 tasks, 32 executed). vr-debug: N/A (VR is a source set within standard flavor — compiled and passed as part of standardDebug).
- Stage 7 DONE 17:20 — audit loop. 1 iteration. spec-check outcome: Verified.
  Checks: 48 total — 41 PASS, 0 WARN, 0 FAIL, 5 MANUAL, 1 EXEMPT, 1 UNCHECKABLE.
  Two apparent FAIL predicates from subagent were false negatives (confirmed PASS by direct grep).
  Strategic spec: Status Implemented → Verified. Audit report: PLAN/spec_player-keybinding-remapping__audit_2026-04-25.md.
