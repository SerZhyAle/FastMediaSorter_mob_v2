# Phase 07 - docs-strings-catalog

**Goal:** Localization audit, capability inventory, catalog, device-test tags.

## Steps

- [x] **7.1** String localization audit: every new action label/explanation + group title present in EN/RU/UK. Verify: `scripts/check_strings_localized.ps1 -KeyPrefix "gesture_action_"` exit 0.
- [x] **7.2** Capability record: `scripts/all_features/add.ps1` - S1038 (area "Screen capture / Gestures", flavors "standard,noLegal"). If any new persisted setting surfaced a settings-doc obligation (Rule 22), regenerate; otherwise N/A (gesture actions are not settings-manifest rows). Verify: `docs/ALL_FEATURES.jsonl` has S1038; `assert-settings-doc-sync` green if touched.
- [x] **7.3** Catalog sync + device tags: `scripts/catalog_sync.ps1 -Module app_v2`; set role/status for new classes (catalog + handlers + adapter) via `set.ps1`. Insert the BlockNeedUserTest device probes (one `Timber.d("S1038: ..")` per changed action class flow) before the final build. Verify: catalog regenerated; fast gates green with status BlockNeedUserTest.

## Done criteria
- [x] Strings localized, capability recorded, catalog synced, device probes in place; ready for device verification.

## Step Log

- 2026-07-19 - Steps 7.1-7.3 done. 7.1: `check_strings_localized -KeyPrefix gesture_action_` OK (46 keys EN/RU/UK); full gesture_ prefix 50/50. 7.2: `all_features/add.ps1` recorded `gestures.expanded-action-catalog` (area "Screen capture / Gestures", flavors standard,noLegal) in docs/ALL_FEATURES.jsonl; `assert-settings-doc-sync` PASS (gesture actions are not settings-manifest rows - N/A, no regen needed). 7.3: `catalog_sync -Module app_v2` regenerated (2260 records); `set.ps1` set role+status=new for the 6 new classes (DeviceActionHandler, MediaActionHandler, LaunchActionHandler, GestureAccessibilityActions, GestureAccessibilityActionsModule, NoLegalGestureAccessibilityActions). Four BlockNeedUserTest probes inserted (one per changed action-class flow): `Timber.d("S1038: device/media/launch/system action %s", action)` at DeviceActionHandler/MediaActionHandler/LaunchActionHandler.handle and NoLegalGestureAccessibilityActions.perform.
- Verification: standard `a.ps1 fk` BUILD SUCCESSFUL (all code + 3 src/main probes green). noLegal `a.ps1 fkn` fails on EXACTLY ONE unrelated error - `StandalonePlayerActivity.kt:855` (S1114 vr-entry-button, a concurrent Tactical ticket mid-edit that added `onVrLaunchClicked`/`isVrEntryAvailable` to an interface without updating its anonymous impl). No S1038 file errors -> all S1038 noLegal code + the 4th probe compile clean (Kotlin reports every file's errors). Fast gates: no-ticket-logs PASS (S1038 probes in the allowed BlockNeedUserTest set), neuroslop PASS, detekt 0 S1038 findings (after refactoring performSystemAction to <=2 returns). Remaining whole-tree gate reds (listener-symmetry +1, detekt on SettingsRowStackManager/Sftp/VR/tests) are concurrent-WIP noise outside S1038's touched files.
- AUDIT-FIX: performSystemAction had 4 return statements (ReturnCount > 2 detekt). Refactored to a nullable `Int?` global-action lookup with a single degrade-return; re-verified 0 S1038 detekt findings.
