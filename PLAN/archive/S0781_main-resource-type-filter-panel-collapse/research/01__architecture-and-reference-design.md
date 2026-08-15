# S0781 Research - Architecture & Reference Design

**Captured:** 2026-07-01 (android-solution-researcher + inline verification)
**Scope:** app_v2; all flavors (standard/lite/photos/legacy). Feature area: main window resource-type tab strip; player copy/move panels (reference design).

---

## 1. Target panel - main-window resource-type filter

- The panel is a Material `TabLayout` `@id/tabResourceTypes`, sitting inside the main `AppBarLayout`.
- Tabs come from enum `ResourceTab` (ALL / LOCAL / SMB / FTP_SFTP / CLOUD / FAVORITES); the first tab `tab_all_resources` is the "Все"/"All" entry the idea mentions.
- Owner helper: `MainResourceTabsManager` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt`, ~113 LOC). Builds tabs, installs `OnTabSelectedListener`, holds bidirectional index<->ResourceTab map.
- Wired from `MainActivity` (`ui/main/MainActivity.kt`, **1517 LOC - already at/over the 1500 cap**). New click/collapse code must live in a NEW helper, never in the Activity.
- Active tab + filter state (`activeResourceTab`, `filterByType`, `filterByMediaType`, `filterByName`) live in `MainState`/`MainViewModel` (in-memory, per-session). Active tab is NOT persisted today (resets to ALL on restart).
- **Vanish rule:** `MainResourceTabsManager.createTabs()` sets `tabLayout.isVisible = false` when no remote source is enabled. The collapsed strip must obey the same rule (no remote -> neither tabs nor strip shown).

### Layout variants (all three must change in sync - CLAUDE.md Rule 11)
- `app_v2/src/main/res/layout/activity_main.xml` - `tabResourceTypes` ~line 323-337.
- `app_v2/src/main/res/layout-land/activity_main.xml` - `tabResourceTypes` ~line 345.
- `app_v2/src/main/res/layout-w600dp/activity_main.xml` - `tabResourceTypes` ~line 344.
- D-pad chain: surrounding buttons use `nextFocusDown="@id/tabResourceTypes"` in all three; the collapsed strip must remain a reachable focus stop.

---

## 2. Reference design - player copy/move collapse (mirror this behavior)

- `DestinationButtonsManager` (`ui/player/helpers/DestinationButtonsManager.kt`, ~694 LOC): collapse/expand of copy + move panels; persists state to `AppSettings`; in-memory cache (`cachedCopyCollapsed`/`cachedMoveCollapsed`) avoids a stale repopulate race on the settings `Flow` re-emission.
- `CollapsibleSectionHeader` (`ui/common/widget/CollapsibleSectionHeader.kt`, ~390 LOC): reusable header view, chevron animation, click-to-toggle, `setOnExpandedChangeListener`. **Player toggles on a plain TAP.**
- Reference layout: `app_v2/src/main/res/layout/player_bottom_panels_container_content.xml`. Each panel = `CollapsibleSectionHeader` + content grid. Backgrounds use NAMED colors `@color/activity_player_unified_copyToPanel_background` / `..._moveToPanel_background` (NOT inline hex in layout - the hex value lives in `colors.xml`). Strip labels use `@string/panel_copy_to` / `@string/panel_move_to`. Title/chevron tint `@color/white`, header bg `?attr/selectableItemBackground`.
- **Structural mismatch:** the tab strip is a `TabLayout`, not a header+content vertical stack. Do NOT force `CollapsibleSectionHeader` onto it. A bespoke lightweight manager toggling between the `TabLayout` (expanded) and a new collapsed strip view is cleaner.

---

## 3. Persistence - DataStore, NO Room migration

- `AppSettings` (`domain/model/AppSettings.kt`) already holds `copyPanelCollapsed` / `movePanelCollapsed` (lines ~210-211).
- Persisted by `SettingsRepositoryImpl` (`data/repository/SettingsRepositoryImpl.kt`) which injects **`DataStore<Preferences>`** (Jetpack DataStore, NOT Room). Keys are plain `booleanPreferencesKey(...)`; read with `preferences[KEY] ?: false`, written in the save block.
- Adding `resourceTypeTabCollapsed: Boolean = false` is trivial - mirror `copyPanelCollapsed` exactly:
  - `AppSettings` field.
  - `KEY_RESOURCE_TYPE_TAB_COLLAPSED = booleanPreferencesKey("resource_type_tab_collapsed")` in `SettingsRepositoryImpl`.
  - read in the `AppSettings(...)` mapping; write in the persist block.
  - mirror in `ImportSettingsUseCase` / `BackupMapper` / `BackupData` / `DeviceProfilePresetApplier` if those round-trip every flag (verify in F3 - copyPanelCollapsed appears there).
- These collapse flags are internal UI-state, NOT Settings-screen toggles -> NOT in the settings manifest, so Rule 22 (settings-doc-sync) does NOT apply (verify copyPanelCollapsed absence in manifest during F3).

---

## 4. Localization

- Strings: `app_v2/src/main/res/values/strings.xml` + `values-ru/strings.xml` + `values-uk/strings.xml`. Trilingual mandatory.
- Tooling: `scripts/utils/set-android-string.ps1 -Action add -Key <k> -En <..> -Ru <..> -Uk <..>` (parity-enforced). New strip-label key required, e.g. `main_resource_type_filter_strip`.

---

## 5. Flavor / gating

- No `BuildConfig.IS_*` guard touches `tabResourceTypes` or `MainResourceTabsManager`. The only gate is runtime `RemoteSourceAvailabilityGate.anyRemoteEnabled()` (the vanish rule). S0781 is flavor-agnostic.

---

## 6. Owner-resolved UI decisions (2026-07-01)

- **Collapse gesture:** long-press anywhere on the tab strip row.
- **Expand gesture:** plain TAP on the collapsed strip (player-analogous; collapse stays the deliberate long-press).
- **Strip color:** a dedicated named `@color` for the filter strip (like the player's green/blue), via theme/@color - no inline hex (Rule 19).
- **Persistence:** survives app restart, via the DataStore flag (mirrors player panels).
- **Label:** new trilingual string key ("фильтр типов ресурсов" / EN / UK).
- **Accessibility:** strip is a D-pad/keyboard focus stop; center/enter expands; distinguished by text label, not color alone.
- **Active-filter summary on strip:** out of scope for v1 (label only); kept as an extensibility point.

---

## 7. Files most likely edited (F3 input)

- NEW `ui/main/helpers/MainResourceTypeFilterPanelManager.kt` - owns expanded/collapsed toggle, persist read/write (cache-before-write like `DestinationButtonsManager`).
- `ui/main/helpers/MainResourceTabsManager.kt` - cooperate with collapsed state + vanish rule (or expose tab view for the new manager).
- `ui/main/MainActivity.kt` - wire the new manager (minimal; stay under cap).
- `res/layout/activity_main.xml` + `res/layout-land/..` + `res/layout-w600dp/..` - add collapsed strip view beside `tabResourceTypes`.
- `domain/model/AppSettings.kt` + `data/repository/SettingsRepositoryImpl.kt` (+ backup/import/preset mappers) - new persisted flag.
- `res/values*/strings.xml` (x3) - strip label.
- `res/values/colors.xml` (+ any theme-specific colors dirs) - dedicated strip color.
- `src/test/.../MainResourceTypeFilterPanelManagerTest.kt` - mirror `DestinationButtonsManagerTest`.
