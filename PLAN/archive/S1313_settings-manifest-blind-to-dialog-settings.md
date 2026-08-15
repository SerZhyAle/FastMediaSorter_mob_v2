# S1313 - The settings manifest stopped seeing settings the moment they moved into a dialog

**Status:** Archived
**Priority:** 40

<!-- discovered by /spec-dev S1087 - 2026-07-30, parked per CLAUDE.md 3.1 -->

## 0. Raw capture

Found while running Rule 22's mandatory regeneration for S1087 (a new launcher toggle). The manifest
regenerated cleanly and did not contain the new setting - nor any of its five siblings.

Evidence, measured on 2026-07-30 against the freshly regenerated
`docs/settings/settings-manifest.json` (208 entries, mtime 15:41:25):

- `rowLauncherShowTray` - 0 hits.
- `rowLauncherLockDesktop` - 0 hits.
- `launcher_settings_density_title` - 0 hits.
- `launcher_settings_show_tray_title` - 0 hits.
- The only launcher rows present are `rowLauncherModeEnabled` and `rowLauncherSettings`, both from
  `fragment_settings_general` - that is, the entry points, not the settings behind them.

Every manifest entry carries a `layout` field naming a `fragment_settings_*` layout. The scan is
therefore scoped to the settings *screens*; `dialog_launcher_settings.xml` is not a screen, so its rows
are invisible to it.

## 1. Why this matters

S1088 moved the launcher configuration rows out of the Operations settings group and into a dialog.
The rows stayed user-facing settings; the manifest scope did not follow them. Consequences:

- `docs/SETTINGS_REFERENCE*.md` - the published settings reference in three languages - documents no
  launcher setting at all, while the app has six.
- Rule 22 and its gate (`assert-settings-doc-sync.ps1`) keep passing, because the committed manifest
  genuinely matches the live scan. The gate is honest; its scope is what is wrong. A green gate that
  cannot see a whole class of settings is worse than a red one, because it certifies coverage it does
  not have.
- Every future ticket that adds a dialog-hosted setting will regenerate, see no diff, and reasonably
  conclude nothing was needed - as S1087 nearly did.

## 2. Goal

Закрыть слепую зону документации настроек, не трогая поведение in-app поиска. `docs/settings/settings-manifest.json`
и `SETTINGS_REFERENCE*.md` были построены на том же скане, что и настоящий поиск по настройкам
(`LayoutSettingsSearchSource` + `SettingsSearchLayoutCatalog` + `SettingsSearchTabMapping`), который
видит только 9 захардкоженных `fragment_settings_*` layout. Наивное расширение этого списка диалогами
(первая версия этого тикета) оказалось ошибкой: **S1035 §6.6 уже явно решил** - когда ряды жестов
переехали в диалог, поиск индексирует только точку входа (мастер-переключатель + кнопку), а не ряды
внутри диалога, потому что диалог не является частью view-иерархии Activity и `findViewById` внутрь
него не работает - расширение каталога поиска вернуло бы именно тот класс мёртвых результатов поиска,
который S0604/S1035 уже закрыли. Правильное решение (найдено уже готовым - см. §3 ниже): отдельный,
непересекающийся каталог `SettingsDocScopeCatalog`, который питает только манифест/докс, никогда
`SettingsSearchLayoutCatalog`.

## 3. Scope decision - implemented per a pre-existing tactical plan, corrected against live code

**Process note.** `/spec-next` picked up S1313 as `Draft` with `tactical_folder=true` in the preflight
payload. The first implementation pass (see git-free working-tree history is not authoritative, but the
first cut of this very file) missed that signal, classified the ticket as "Simple", and widened
`SettingsSearchLayoutCatalog` directly - reopening the S1035 decision above. `/spec-check`'s tactical-folder
read (`PLAN/S1313_settings-manifest-blind-to-dialog-settings/INDEX.md`, authored 2026-07-31 by a prior
`/spec-tech` run) caught this before final report: its "Architecture decision this plan implements"
section names S1035 explicitly and specifies a disjoint `SettingsDocScopeCatalog`. The wrong catalog
edit was reverted; implementation followed the tactical plan's 6 phases instead, with two corrections
made against live code during Phase 01/02 (below) - the plan's own numbers were not taken on faith
(project convention: plan files can be wrong, verify against the tree).

**Correction 1 - two wrong `hostKey` values.** The plan's Phase 01 surface table assigned
`btnSelectCameraPhotosDest` as the settings-screen entry point for both `dialog_camera_settings.xml`
and `dialog_camera_ocr_settings.xml`. That id is a real button in `fragment_settings_destinations.xml`,
but it is the "select camera-photos destination folder" picker, unrelated to opening either camera
dialog - grepping the codebase found no settings-screen control that opens either dialog at all (both
open only from `CameraSettingsCallbackHandler`/`CameraOcrTranslateActivity` inside the capture flow).
Both now carry an empty `hostKey`, same treatment as `dialog_player_settings`/`dialog_playback_control`/
`dialog_translation_settings`.

**Correction 2 - three surfaces produce noise or incomplete coverage, not settings.** The plan's Phase 01
table included `dialog_player_settings`, `dialog_playback_control`, and `dialog_slideshow_settings`.
Regenerating the manifest with them included showed why that is wrong:
- `dialog_player_settings` - its own KDoc: "Settings are applied immediately and persist for the current
  player session" - explicitly session-scoped, not persisted. Scanning it also surfaced generic
  `btnApply`/`btnCancel` action buttons as if they were settings.
- `dialog_playback_control` - an in-player quick-controls panel. Scanning it produced 18 raw entries,
  17 of which are live transport controls (mute, volume/brightness/hue/speed presets and resets,
  section-tab buttons) - not settings at all. The one plausible entry, `switchVrOverrideFormatType`, is
  session-scoped in the player ViewModel per an existing code comment ("Stereo mode is session-scoped").
- `dialog_slideshow_settings` - its primary setting (playback interval) is a `Slider` widget
  `LayoutSettingsSearchSource.kindFromTag` does not recognize at all; only one minor toggle
  (`cbPlayToEnd`) and two action buttons (`btnSelectMusic`/`btnClearMusic`) would be scanned, producing
  incomplete, misleading documentation for this surface.

All three are excluded via `docs/settings/settings-scope-exclusions.json` (category `session-scoped` /
`unsupported-widget` - two categories added to the plan's original four, with the same reason+category
shape).

**Final in-scope set - `SettingsDocScopeCatalog.surfaces` (6 layouts):**

- `dialog_launcher_settings.xml` -> `launcher`/GENERAL, hostKey `rowLauncherSettings`. The ticket's own
  trigger: `rowLauncherShowRecents`, `rowLauncherShowPinned`, `rowLauncherShowTray`,
  `rowLauncherReplaceStatusArea`, `rowLauncherDensity`, `rowLauncherLockDesktop`, `rowLauncherWallpaper`,
  plus the `rowLauncherOpenHomeSettings` action button.
- `dialog_edge_gesture_config.xml` -> `gestures`/OPERATIONS, hostKey `btnOpenEdgeGestureConfig`. 12
  `rowZone*Enabled`/`rowZone*StripVisible` toggles + 12 `rowGesture*Up/Right/Down` selection rows (4
  corners x 3 actions) + `rowCopyScreenshotToClipboard` + `headerEdgeGestureGeneral` + `btnEditAppPanel`
  + `btnSelectScreenshotDestination`.
- `dialog_default_apps.xml` -> `defaultApps`/OPERATIONS, hostKey `btnOpenDefaultAppsDialog`.
  `rowPrimaryMediaPlayer`, `rowAcceptSharedFiles`, plus the four `btnSettingsDefaultPlayer*` buttons.
- `dialog_camera_settings.xml` -> `camera`/OPERATIONS, hostKey `""` (see Correction 1). 7 rows:
  timer, grid, aspect ratio, resolution, white balance, manual sensor, HDR.
- `dialog_camera_ocr_settings.xml` -> `camera`/OPERATIONS, hostKey `""`. 1 row: `cbOcrOnly`.
- `dialog_translation_settings.xml` -> `translation`/MEDIA, hostKey `""`. 3 rows: font family, font
  size, lens style.

**Final excluded set - `docs/settings/settings-scope-exclusions.json` (10 entries, up from the plan's 8):**
`dialog_add_stream`/`dialog_scheduled_operation`/`dialog_filter_resource` (`entity-editor` - per-item or
per-operation configuration, not a standing preference), `page_welcome_enhanced`/
`page_welcome_functionality`/`page_welcome_networks` (`onboarding` - deferred product decision, per the
tactical plan's own Pre-Implementation Blockers), `fragment_vr_settings_block` (`flavor-scoped` - lives
in `app_v2/src/vr/res/layout/`, does not resolve in the `standardDebug` test variant),
`dialog_player_settings`/`dialog_playback_control` (`session-scoped`, Correction 2),
`dialog_slideshow_settings` (`unsupported-widget`, Correction 2). `fragment_settings_media_container`
(the plan's 9th exclusion) turned out to need no entry at all once the completeness gate became a real
widget-tag sweep (§ mechanical gate below) - it has zero settings-row widgets, so the content-based
sweep never discovers it in the first place.

**New action-button exclusion in `LayoutSettingsSearchSource`:** every in-scope dialog ships a standard
header `MaterialButton android:id="@+id/btnClose"` (`DialogCancel` house style), and `dialog_camera_settings`/
`dialog_translation_settings` additionally ship `btnCancel`/`btnOk`/`btnCameraSettingsApply`/
`btnCameraSettingsCancel` confirm/cancel pairs - none of the 9 existing search-scope screens have any of
these ids (verified by grep before adding), so `DIALOG_ACTION_BUTTON_IDS` is a no-op for the pre-existing
search catalog and only trims real noise from the new doc-scope scan. Same rationale as the existing
S0604 `TRANSIENT_ACTION_BUTTON_IDS` de-index: dismissing/confirming a dialog is not a discoverable
setting.

**Mechanical gate (closes the recurrence, not just this one instance):**
`assert-settings-catalog-complete.ps1` no longer globs `fragment_settings_*.xml` by filename. It now
recurses every `res/layout*` under `app_v2/src` and selects layouts by a real widget-tag match
(`<...SettingsToggleRow` etc., anchored on `<` so a layout that only *mentions* the class name in a
comment - e.g. the widget's own `view_settings_toggle_row.xml` internal layout - is not a false
positive). Every discovered layout must land in `SettingsSearchLayoutCatalog`, `SettingsDocScopeCatalog`,
or `settings-scope-exclusions.json`, or the gate fails naming all three resolution paths; an exclusion
entry for a layout no longer found also fails, so the list cannot rot. Proven with fault injection
(temporarily removed `dialog_add_stream`'s exclusion entry, confirmed the gate fails and names it,
restored).

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** none of the layouts audited (in-scope or excluded) change - this spec only
  widens which already-shipped layouts a documentation-only scanner walks, via a catalog that never
  touches the in-app search index (S1035 boundary preserved).
- **Accessibility:** unchanged - no view added, removed, or reordered in any layout.
- **Validation level:** unit (`SettingsManifestExportTest` regeneration + `assert-settings-doc-sync.ps1`
  + `assert-settings-catalog-complete.ps1` fault-injection self-test) is sufficient - this is
  scanner/doc-generation logic, not a rendered UI change; on-device confirmation is a nice-to-have, not
  required to ship.
- **Owner sign-off:** not required - mechanical scanner/doc-completeness fix; the one open product
  decision (onboarding-wizard pages) stays explicitly deferred, not resolved by assumption.
- **Related tickets:** S1087, S1088, S1035, S0440, S0604, S0567.

## 4. Related

- **S1087** - the ticket that surfaced this; it adds a sixth launcher-dialog setting.
- **S1088** - moved the launcher rows from the settings screen into the dialog.
- **S1035** - moved the edge-gesture rows into a dialog and explicitly ruled (§6.6) that in-app search
  indexes only the entry point, never the dialog's internal rows. The architectural constraint this
  whole ticket implements around.
- **S0440** - original settings-manifest/search-index infrastructure (`SettingsManifestExportTest`,
  `assert-settings-doc-sync.ps1`, `assert-settings-catalog-complete.ps1`).
- **S0604** - precedent for de-indexing a non-setting button by id (`TRANSIENT_ACTION_BUTTON_IDS`).
- **S0567** - added `SettingsInputRow`/`SettingsDropdownRow`/`SettingsSelectionRow` to the recognized
  widget kinds this ticket now also finds inside dialogs.

## 5. Implementation

Full phase breakdown lives in the tactical folder:
[`PLAN/S1313_settings-manifest-blind-to-dialog-settings/INDEX.md`](S1313_settings-manifest-blind-to-dialog-settings/INDEX.md)
(6 phases, authored 2026-07-31 by a prior `/spec-tech` run, executed with the corrections in §3).

## 6. Non-goals

- Auto-opening a dialog and highlighting its internal row when a search result targets its entry point -
  the existing 25-retry/2s-then-silent `navigateToTarget` fallback already makes this safe (no crash,
  just no highlight past the entry point); building real cross-window highlight support is separate,
  larger scope, and orthogonal to S1035's decision to not index dialog-internal rows at all.
- Extending `LayoutSettingsSearchSource.kindFromTag` to recognize `Slider`/`CheckBox` (would be needed to
  usefully cover `dialog_slideshow_settings.xml`) - a different class of gap from this ticket's.
- Widening `SettingsSearchLayoutCatalog` itself to any of the six in-scope dialogs - explicitly rejected,
  see §2/§3 (S1035 §6.6).
- Resolving the onboarding-wizard pages' inclusion (`page_welcome_enhanced`/`_functionality`/`_networks`)
  - left as an explicit deferred product decision per the tactical plan's own blocker.

## Last Audit

**Date:** 2026-08-01
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 20 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Verified: `SettingsDocScopeCatalog` exists (83 LOC, within Phase 01's budget), disjoint from
`SettingsSearchLayoutCatalog`/`SettingsSearchTabMapping` (both unchanged, 9 entries each, zero code-level
coupling - S1035 boundary intact); `LayoutSettingsSearchSource.collect(List<Int>)` overload +
`DIALOG_ACTION_BUTTON_IDS` exclusion; `SettingsManifestExportTest` merges both scopes; manifest
regenerated to 258 entries (209 pre-existing unchanged + 49 new), confirmed byte-fresh by the gradle
verify-mode test (not just generate mode); annotations 257 unique keys / 0 orphans / en+ru+uk complete;
`SETTINGS_REFERENCE*.md` re-rendered with 5 new sections and "where to find it" lines, spot-checked for
readability (Launcher/Camera capture/On-screen translation tables read correctly); the completeness gate
rewritten to a widget-tag sweep across all `res/layout*` dirs, self-proven via fault injection (removed
`dialog_add_stream`'s exclusion, confirmed the gate names it, restored); `settings-scope-exclusions.json`
10 entries, all categorized; Rule 22 wording widened consistently in `CLAUDE.md`/`AGENTS.md`/
`.github/copilot-instructions.md`; document registry validated + `generate.ps1 -Check` clean; class
catalog entry set; `docs/ALL_FEATURES.jsonl` capability recorded; `.\a.ps1 fk` and the full detekt gate
both pass with zero new findings in the changed set; zero `Timber.d("S1313:` tags (consistent with
non-`BlockNeedUserTest` status); no stray references to the reverted first-attempt architecture or the
renamed `DIALOG_ACTION_BUTTON_IDS` constant.

EXEMPT: `docs/FEATURES*.md` trilingual check - strategic spec carries no FEATURES sentence, confirmed
untouched (owned by `/skill-release`; the separate `docs/ALL_FEATURES.jsonl` developer inventory does
carry a record, per Phase 06 Step 06.4).

### Manual / on-device

- [ ] Optional: confirm on-device that in-app Settings Search behavior is genuinely unchanged (no new
  dialog-hosted rows appear as search results) - not required to ship per the Approval-gate validation
  level decision (§3.3), since the code-level boundary is what's asserted, not runtime-observed, but
  would be a cheap confirmatory device check if one is available.

### Non-blocking, tracked separately

`post-change.ps1`'s own `settings-doc-sync-gate` intermittently raced its backgrounded `detekt-gate` on
`temp/BUILD.LOCK` (2 of 3 attempts this session) - a pre-existing facade ordering bug unrelated to this
ticket's content, reproduced, diagnosed, and parked as **S1349** (dedup-checked). Every gate the facade
runs was independently confirmed green by direct invocation; this did not block or weaken verification.
