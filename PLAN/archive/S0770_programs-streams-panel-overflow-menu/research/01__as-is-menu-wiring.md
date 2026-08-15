# S0770 research 01 - AS-IS menu wiring (exact targets)

Captured 2026-06-28. Feeds the tactical plan. All paths under `app_v2/src/main/java/com/sza/fastmediasorter/`.

## Programs panel (S0755)

- Manager: `ui/main/helpers/MainProgramsPanelManager.kt`. Inflates `R.layout.item_main_program` as `MaterialButton`, sets icon/text/contentDescription, `setOnClickListener { onItemSelected(model.id) }`. Overflow distribution in `applyOverflow()` measures children, pushes non-fitting items to a `PopupMenu`. Knows only `PanelItem(id, title, icon)`.
- Item layout: `res/layout/item_main_program.xml` - root IS a `MaterialButton` (must be wrapped to host a ⋮ overlay).
- Label rule bool: `R.bool.main_programs_panel_show_labels` (false in `values/`, true in `values-land/`).
- Menu population + click routing in `MainActivity.kt`:
  - `populateMainWindowDropdownMenu(popup, excludeStreams)` (line 709-748) - single source of items/order/gates.
  - `handleMainWindowMenuItem(itemId)` (line 684-704) - shared click routing (managers first, then direct ids).

### Program item ids -> launch + toggle + new-window eligibility

| Item | id const (value) | launch (handleMainWindowMenuItem) | AppSettings toggle for "Remove" | Self-window (Open-in-new-window)? |
| --- | --- | --- | --- | --- |
| Streams | `MENU_ITEM_STREAMS` (14, in `MainStreamsMenuManager`) | `Intent(ctx, StreamsActivity)` | none (owner: hide Remove) | yes |
| Quick Launch Panel | `MENU_ITEM_APP_LAUNCH_PANEL` (15, MainActivity) | `Intent(this, AppLaunchPanelActivity)` | none (always-on; hide Remove) | yes |
| Calculator | `MENU_ITEM_CALCULATOR` (1, MainActivity) | `CalculatorActivity.createIntent(this)` | `enableCalculator=false` | yes |
| Camera-OCR | `MENU_ITEM_CAMERA_OCR` (9, MainActivity) | `CameraOcrTranslateActivity.createIntent(this)` | `cameraOcrTranslationEnabled=false` | yes |
| Quick Camera | `MENU_ITEM_QUICK_CAMERA` (12, `MainQuickCaptureMenuManager`) | `onCamera()` -> camera capture flow | `disableCameraCapture=true, disableVideoCapture=true` | no (transient capture) |
| Quick Voice | `MENU_ITEM_QUICK_VOICE` (10, `MainQuickCaptureMenuManager`) | `onVoice()` -> voice capture flow | `micRecordingEnabled=false` | no (transient capture) |
| Link Download | `MENU_ITEM_LINK_DOWNLOAD` (13, `MainLinkDownloadMenuManager`) | `onLinkDownload()` -> in-place dialog | `linkAutoDownloadEnabled=false` | no (in-place dialog) |
| Mini-game | `MENU_ITEM_GAME` (2, `MainMiniGameMenuManager`) | `GameLaunchIntents.game(context)` | `embeddedGameEnabled=false` | yes |

- Local `isXxxEnabled` <- settings mapping: `MainActivity.kt:1112-1119`.
- Settings collector `MainActivity.kt:1096-1152` already calls `refreshPanels()` when any program toggle changes -> "Remove" -> setting flip -> panel auto-rebuilds, item drops. Capture `latestSettings = settings` here for the copy() base.
- Settings write: no MainActivity/MainViewModel wrapper. Use injected `settingsRepository` (`MainActivity.kt:152`): `lifecycleScope.launch { settingsRepository.updateSettings(latestSettings.copy(..)) }`. Repo: `suspend fun updateSettings(AppSettings)`.

## Streams panel (S0756)

- Manager: `ui/main/helpers/MainStreamsPanelManager.kt` (`scope: CoroutineScope` = `lifecycleScope`, `onOpenStreams`, `onPlayChannel`). Observes `observePinnedStreamSources()` -> `adapter.submitList`.
- Adapter: `ui/main/helpers/StreamPanelChannelAdapter.kt`, item `res/layout/item_main_stream_channel.xml` (root `LinearLayout` id `channelRoot` - wrap to host ⋮). `showLabels` driven by `R.bool.main_streams_panel_show_labels` (false `values/`, true `values-w600dp/`).
- Entry button: `btnStreamsPanelEntry` in `res/layout/view_main_streams_panel.xml` -> `onOpenStreams()` (`Intent(this, StreamsActivity)`). Always shows its label.
- Channel tap: `onPlayChannel(entity)` -> `StreamsActivity.createPlayIntent(this, channel.url)`.
- `StreamSourceEntity` fields: `id: String` (PK), `url: String`, `title: String`, `pinned: Boolean`, `sortIndex: Int`.

### Remove (unpin) gap - MUST ADD (in-scope)

- No unpin operation exists. `StreamSourceDao` has only `pin(id, sortIndex)` (sets `pinned=1`); `RemoveStreamSourceUseCase` deletes the whole row (`repository.remove` -> `dao.delete`). "Remove = unlock" requires unpin-without-delete.
- Add: DAO `@Query("UPDATE stream_sources SET pinned = 0 WHERE id = :id") suspend fun unpin(id: String)`; repository `suspend fun unpin(id: String)`; `UnpinStreamSourceUseCase` (`suspend operator fun invoke(id: String) = repository.unpin(id)`). After unpin, `observePinnedStreamSources()` (pinned=1) drops the channel automatically.
- Use cases live in `domain/usecase/streams/`; repo `data/repository/.../StreamSourceRepository*`; DAO `data/local/db/StreamSourceDao.kt`.

## Open-in-new-window mechanism (S0028/S0184/S0293)

- Availability expr (canonical, `MainActivity.kt:1131-1135`): `settings.allowSeparateWindow || MultiWindowCapabilityDetector.isMultiWindowActiveNow(this)`.
- Launch pattern (`openResourceInNewWindow`, `MainActivity.kt:1157-1165`): `intent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_MULTIPLE_TASK); startActivity(intent)` (+ a fresh UUID window-id extra for Browse; programs need only the flags).
- String: `R.string.action_open_in_separate_window` = "Open in new window" (reuse, do not add).

## Confirm dialog house style

- `showDeleteConfirmation` (`MainActivity.kt:1254-1264`): `MaterialAlertDialogBuilder(this[, Destructive style]).setTitle().setMessage(getString(.., name)).setPositiveButton(R.string.<verb>) {..}.setNegativeButton(android.R.string.cancel, null).show()`, guarded by `if (isFinishing || isDestroyed) return`. Standard AlertDialog buttons (NOT `Widget.FastMediaSorter.Button.Dialog*`; those are for custom-layout dialogs). Reversible remove -> plain (non-destructive) builder.

## Decisions baked in (owner, 2026-06-28)

- Trigger: visible ⋮ (top-end) only in label mode; long-press always. Body tap unchanged (open/launch).
- Remove hidden when no toggle/pin: Streams program item, Quick Launch Panel, Streams entry button.
- Remove always behind a confirm dialog.
