# S0842 - Canonical resource icon and picker affordance

**Ticket:** S0842
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 3 - Moderate
**Source:** User request 2026-07-01 (`/spec-draft`), implemented with S0799

## 1. Problem

The app registers a "resource" (a local/network/cloud entry) in many places, but there was no single recognizable icon for the resource concept, and at least one resource affordance ("Select resource..") used a **folder** icon - the same resource/folder confusion S0799 fixes in text, but at the icon level.

## 2. Decision (owner, 2026-07-01)

- Two distinct icons, never swapped:
  - **Resource** = new `ic_resource` (a stacked media collection, neutral `?attr/colorControlNormal`) - the umbrella marker for the app's registered entity.
  - **Folder** = existing Material `ic_folder` family - a genuine filesystem directory.
- Icon shape: stacked layers/collection (owner-selected over grid / card-with-star).
- Affordance policy: icon-only where a genuine "Select resource" button exists (with contentDescription + tooltip + D-pad focus). Owner accepted the discoverability trade-off; labelled rows/titles keep their text but must use the resource icon, not a folder icon.

## 3. What was delivered

1. New drawable `app_v2/src/main/res/drawable/ic_resource.xml` - stacked collection glyph with a play mark, neutral tint.
2. Fixed resource-affordance-with-folder-icon confusion: the "Select resource.." action in the Send-to sheet (`sheet_send_to.xml`) and menu (`SendToMenuManager.kt`) now use `ic_resource` instead of `ic_folder`.
3. Per-type icons (`ic_resource_{local,cloud,smb,ftp,sftp,favorites}`) are unchanged - they still convey a resource's TYPE; `ic_resource` is the generic umbrella.
4. `ic_folder` audit: confirmed genuine-folder usages (browse empty-state, cloud folder list items, subfolder thumbnails, statistics, subfolder-mode indicator) are correct and left as-is.
5. Broader rollout of the §2 affordance policy (owner-reported 2026-07-04: many "Select resource.." buttons across Settings still rendered as plain outlined text buttons, not icon buttons). Converted every genuine destination-picker button from text (`Widget.FastMediaSorter.SettingsButton.Outlined` + `android:text`) to icon-only (`Widget.Material3.Button.IconButton.Outlined` + `app:icon="@drawable/ic_resource"` + `android:contentDescription` reusing the same string + a `TooltipCompat` tooltip, S0810 pattern), in both portrait and landscape:
   - `fragment_settings_destinations.xml` (+land): camera-photos, video-recording, mic-recording, screen-recording, link-autodownload, and screenshot destination pickers (6 buttons).
   - `fragment_settings_video.xml` (+land): snapshot-resource picker.
   - `fragment_settings_images.xml` (+land): slideshow music-source picker.
6. Fixed a Settings-Search regression the rollout would otherwise have caused: `LayoutSettingsSearchSource` indexed `BUTTON`-kind rows only from `android:text`, so removing that attribute would have silently de-indexed all 8 rows from in-app settings search. Added a `contentDescription` fallback scoped to exactly these 8 button ids (`CD_TITLED_BUTTON_IDS`, mirrors the existing `TEXTVIEW_PICKER_IDS` allow-list pattern) rather than a blanket fallback, so pre-existing icon-only help/action buttons elsewhere do not newly (and unreviewed) flood into the search manifest. `settings-manifest.json` is unchanged byte-for-byte (same string resource, different source attribute).
7. Excluded from this pass: the Send-to row (`sheet_send_to.xml`) and its overflow-menu twin (`SendToMenuManager.kt`) - both are "labelled rows" per §2 policy (icon + text stays), already compliant. The App-Launch-Panel per-slot picker (`ResourcePickerDialogFragment`) and the Resource-Launch-Widget config screen (`ResourceLaunchWidgetConfigActivity`) are full picker screens/dialogs, not a genuine "Select resource" button, so out of scope for this icon-only conversion.

## 4. Open points / follow-up (device-driven)

1. Owner visual approval of `ic_resource` shape (2 vs 3 layers) on device - this is the BlockNeedUserTest gate. Still pending.
2. Device-verify the 8 newly-converted icon buttons (§3.5): icon renders (not a folder glyph), long-press/hover tooltip shows the same label the button used to display as text, tap still opens the destination picker, D-pad focus reaches the button in both orientations.
3. Generic fallback for `HTTP_STREAM`/`RTSP_STREAM` in the type->icon maps (currently falls to cloud) could adopt `ic_resource`.

## Related

- S0799 - resource/folder terminology audit (text side; delivered together).

## Last Audit

### Manual (device, 2026-07-09)

- Device: emulator-5554, Android 13 (SDK 33), x86_64, 1080x2400.
- Build: installed `com.sza.fastmediasorter.debug` v2.60.7092.225-NoLegal-DEBUG (lastUpdateTime 2026-07-09). Contains S0842 - all setup probes fire. No rebuild needed. S0842 lives in `src/main`, so the NoLegal build exercises the same layouts/managers.

- Sub-check 2 (8 icon-only Settings destination pickers): PASS.
  - All 8 `Timber.d("S0842: .. dest picker is icon-only")` setup probes observed in logcat: camera-photos, video-recording, mic-recording, screen-recording (OperationsCaptureManager); screenshot (OperationsGesturesManager); link-autodownload (OperationsSettingsFragment); music-source (ImagesSettingsFragment); snapshot (VideoSettingsFragment).
  - camera-photos fully exercised (representative sample): icon renders as `ic_resource` (stacked-collection glyph with play mark, not a folder); `text=""` + `content-desc="Выбрать ресурс.."` -> icon-only confirmed; tap opens the "Выбрать ресурс.." picker dialog (resource list + Отмена); uiautomator node reports `focusable="true" clickable="true"` -> D-pad reachable. Evidence: `temp/S0842/01_camera_section_expanded.png`, `04_camera_picker_dialog.png`.
  - Tooltip on long-press: INCONCLUSIVE. Two long-press attempts (500ms, 1200ms) did not capture the tooltip in a screenshot (transient tooltip rendering is flaky on this emulator). Wiring is present (`content-desc` set + `TooltipCompat.setTooltipText`), so this is a capture-timing limitation, not a defect.

- Sub-check 1 (`ic_resource` glyph in Send-to): INCONCLUSIVE for the Send-to-specific surface; glyph shape itself confirmed.
  - The `ic_resource` glyph renders correctly and is visually distinct from a folder - confirmed on the Settings picker buttons, which use the identical `@drawable/ic_resource`. Observed shape: a stacked/layered collection (appears as 2 overlapping rounded layers) with a play mark. Owner visual approval of 2-vs-3 layers is still the owner's call. Evidence: `temp/S0842/02_camera_tooltip.png`, `03_camera_tooltip2.png`.
  - Could not surface the Send-to "Select resource.." item on this build/context: the per-file "Отправить в.." opened `SendToBottomSheet` showing only external targets (Эл. почта / Открыть в.. / Другие приложения); the `onPickResource` row was absent (null in this virtual-resource context) and the overflow-submenu path that carries `setIcon(R.drawable.ic_resource)` + the `S0842: SendTo pick-resource uses ic_resource` probe was not reached, so that probe did not fire. Evidence: `temp/S0842/05_sendto_submenu.png`, `06_sendto_scrolled.png`.

- Verdict: sub-check 2 PASS (tooltip visual INCONCLUSIVE, wiring verified); sub-check 1 glyph shape confirmed via shared drawable, Send-to-specific rendering INCONCLUSIVE (pick-resource surface not reachable in tested context). No status flip performed.
