# S0770 - Tactical plan: per-item overflow menu on programs (S0755) + streams (S0756) panels

**Ticket:** S0770
**Status:** Tactical
**Strategic:** `PLAN/S0770_programs-streams-panel-overflow-menu.md`
**Research:** `research/01__as-is-menu-wiring.md` (exact file:line targets + id/toggle map)

> Approach: each panel element gains a context menu (Open / Open-in-new-window / Remove). The panel managers stay pure renderers; `MainActivity` supplies per-item action providers (new-window launcher, remove action incl. confirm dialog). A shared `PanelItemContextMenu` builds the popup for both panels. Trigger = visible ⋮ (top-end) when labels show + long-press always; body tap unchanged. "Remove" = flip the program's existing settings toggle (programs) or unpin the channel (streams). Unpin does not exist yet - add DAO/repo/use-case (in-scope; `pinned` column already present, no schema bump).

Owner decisions baked in (2026-06-28): hybrid trigger; Remove hidden for items without a toggle/pin (Streams program item, Quick Launch Panel, Streams entry button); Remove behind a confirm dialog. Multi-window availability + launch flags reuse S0293 (`allowSeparateWindow || MultiWindowCapabilityDetector.isMultiWindowActiveNow`; `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_MULTIPLE_TASK`).

---

## Phase 1 - Data layer: unpin a channel (in-scope gap)

- [x] `data/local/db/StreamSourceDao.kt`: add `@Query("UPDATE stream_sources SET pinned = 0 WHERE id = :id") suspend fun unpin(id: String)`. Mirror the existing `pin(id, sortIndex)` shape.
  - Verification: method present; compiles; no schema/version change (column `pinned` already exists).
- [x] `StreamSourceRepository` (interface + impl under `data/repository/.../streams`): add `suspend fun unpin(id: String) = dao.unpin(id)` mirroring `pinToTop`/`remove`.
  - Verification: interface + impl carry `unpin`; impl delegates to DAO.
- [x] New `domain/usecase/streams/UnpinStreamSourceUseCase.kt`: `class UnpinStreamSourceUseCase @Inject constructor(private val repository: StreamSourceRepository) { suspend operator fun invoke(id: String) = repository.unpin(id) }`. Mirror `PinStreamSourceUseCase`.
  - Verification: file compiles; Hilt resolves via `@Inject` constructor (no module needed).

## Phase 2 - Shared per-item context menu helper

- [x] New `ui/main/helpers/PanelItemContextMenu.kt`: `object` with `data class Action(@StringRes val titleRes: Int, val onClick: () -> Unit)` and `fun show(anchor: View, actions: List<Action>)` that builds a `PopupMenu(anchor.context, anchor)`, adds each action by index, routes clicks. Empty list -> no-op.
  - Verification: compiles; used by both panel managers in later phases.

## Phase 3 - Programs panel: item layout + manager + MainActivity providers

- [x] `res/layout/item_main_program.xml`: wrap the current `MaterialButton` in a `FrameLayout` (wrap_content x `main_panel_row_height`). Keep the button as `@+id/btnProgram` (body, fills frame). Add `@+id/btnProgramMenu` = small borderless ⋮ `ImageButton`/`MaterialButton` (icon `@drawable/ic_more_vert` or existing overflow icon), `layout_gravity="top|end"`, `visibility="gone"`, `focusable=true`, `contentDescription=@string/panel_item_menu_more`, hit-area >= 48dp. Reserve end padding on the body so the ⋮ does not overlap the label in label mode. No `layout-land` counterpart for this item (label rule is a bool, not a layout variant).
  - Verification: `aapt2` build of the item; ids `btnProgram` + `btnProgramMenu` present.
- [x] `MainProgramsPanelManager`: change constructor to also receive `newWindowActionFor: (itemId: Int) -> (() -> Unit)?` and `removeActionFor: (itemId: Int) -> (() -> Unit)?`. In `rebuild()` inflate the frame, bind `btnProgram` (icon/text/cd/body click = `onItemSelected`), set ⋮ visibility = `showLabels`, wire ⋮ click + `btnProgram` long-press -> `showItemMenu(model, anchor)`. `showItemMenu` builds `PanelItemContextMenu` actions: Open (always -> `onItemSelected(id)`), Open-in-new-window (if `newWindowActionFor(id) != null`), Remove (if `removeActionFor(id) != null`). `applyOverflow`/`measureItemWidth` keep working on the frame child.
  - Verification: compiles; visible items show ⋮ only in label mode; long-press opens menu in both modes; body tap still launches.
- [x] `MainActivity`: capture `private var latestSettings: AppSettings? = null` in the settings collector (line ~1096). Add `private fun isNewWindowAvailable(): Boolean` (the canonical OR expr). Add `private fun programNewWindowActionFor(itemId): (() -> Unit)?` mapping self-window ids (Streams, AppLaunchPanel, Calculator, Camera-OCR, Mini-game) to `{ launchInNewWindow(intent) }` (null when unavailable or non-self-window). Add `private fun launchInNewWindow(intent: Intent)` (add the two flags + `startActivity`). Add `private fun programRemoveActionFor(itemId): (() -> Unit)?` mapping removable ids to `{ confirmRemoveProgram(titleRes, applyCopy) }` (null for Streams/AppLaunchPanel). Add `private fun confirmRemoveProgram(...)` (MaterialAlertDialog, positive = Remove -> `lifecycleScope.launch { settingsRepository.updateSettings(copy) }`). Wire the two providers into `MainProgramsPanelManager` construction (line ~797).
  - Verification: `.\a.ps1 fk` compiles; removable map matches research table; Streams/AppLaunchPanel return null for Remove.

## Phase 4 - Streams panel: chip layout + adapter + manager + entry button

- [x] `res/layout/item_main_stream_channel.xml`: wrap the `LinearLayout` (`channelRoot`) in a `FrameLayout` (or add a top-end ⋮ overlay). Add `@+id/btnChannelMenu` ⋮ (same style/cd/hit-area as programs), `layout_gravity="top|end"`, `visibility="gone"`. Keep `channelRoot` content + contentDescription. Mirror in any width-qualified variant if one exists (none expected - rule is a bool).
  - Verification: `aapt2` build; `btnChannelMenu` present; chip still renders favicon/label.
- [x] `StreamPanelChannelAdapter`: add `onChannelOverflow: (StreamSourceEntity, View) -> Unit`. In `bind`: set ⋮ visibility = `showLabels`, ⋮ click -> `onChannelOverflow(source, btnChannelMenu)`, `channelRoot` long-press -> `onChannelOverflow(source, channelRoot)`. Body tap unchanged (`onChannelClick`).
  - Verification: compiles; ⋮ visible only in label mode; long-press fires overflow.
- [x] `MainStreamsPanelManager`: add constructor callbacks `onOpenChannelNewWindow: (StreamSourceEntity) -> Unit`, `onRemoveChannel: (StreamSourceEntity) -> Unit`, `isNewWindowAvailable: () -> Boolean`. Pass `onChannelOverflow` to the adapter -> build `PanelItemContextMenu`: Open (`onPlayChannel`), Open-in-new-window (if `isNewWindowAvailable()`), Remove (`onRemoveChannel`). Wire the entry button (`btnStreamsPanelEntry`) ⋮/long-press -> menu: Open (`onOpenStreams`), Open-in-new-window (if available); NO Remove. Add a ⋮ to the entry button layout in `view_main_streams_panel.xml` (visible, since the entry always has a label) + long-press.
  - Verification: compiles; channel menu has Remove, entry menu does not.
- [x] `MainActivity`: inject `UnpinStreamSourceUseCase`. Construct `MainStreamsPanelManager` (line ~804) with: `onOpenChannelNewWindow = { ch -> launchInNewWindow(StreamsActivity.createPlayIntent(this, ch.url)) }`, `onRemoveChannel = { ch -> confirmRemoveChannel(ch) }`, `isNewWindowAvailable = ::isNewWindowAvailable`. Add `private fun confirmRemoveChannel(ch)` (MaterialAlertDialog -> `lifecycleScope.launch { unpinStreamSource(ch.id) }`). The pinned-sources flow drops the channel automatically.
  - Verification: `.\a.ps1 fk` compiles; unpin reaches the use case.

## Phase 5 - Strings (EN/RU/UK) + audit

- [x] Add via `scripts/utils/set-android-string.ps1 -Action add` (EN/RU/UK parity): `panel_item_menu_more` (⋮ cd, "More" / "Ещё" / "Більше"), `panel_item_action_open` ("Open" / "Открыть" / "Відкрити" - reuse an existing generic open string if one exists, else add), `panel_item_action_remove` ("Remove" / "Убрать" / "Прибрати"), `panel_remove_program_title`, `panel_remove_program_message` (`%1$s`), `panel_remove_channel_title`, `panel_remove_channel_message` (`%1$s`). Reuse `action_open_in_separate_window` for Open-in-new-window.
  - Verification: `scripts/check_strings_localized.ps1 -KeyPrefix "panel_"` exit 0.

## Phase 6 - Build + gates + device gate

- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`; fill role/status for new classes (`UnpinStreamSourceUseCase`, `PanelItemContextMenu`) via `set.ps1`.
- [x] `.\a.ps1 fc` (code + resources) green; resolve detekt/neuroslop/settings-doc-sync/dialog-cancel-style gates on touched files.
- [x] Insert `Timber.d("S0770: ..")` probe at the per-item menu entry (programs `showItemMenu`, streams `onChannelOverflow`) as the final code edits before the last build; status -> `BlockNeedUserTest`.
  - Device test: on programs panel, each item shows ⋮ in landscape / long-press in portrait; menu = Open (+ Open-in-new-window when multi-window on; not for quick-capture/link items) (+ Remove for togglable programs, hidden for Streams/Quick Launch Panel); Remove confirms then the program drops. On streams panel, each channel chip + the Streams entry button show the menu; channel Remove confirms then unpins (channel leaves the panel, stays in catalog); entry button has no Remove. Open-in-new-window opens a separate window on a multi-window device (DeX/ChromeOS/desktop).
