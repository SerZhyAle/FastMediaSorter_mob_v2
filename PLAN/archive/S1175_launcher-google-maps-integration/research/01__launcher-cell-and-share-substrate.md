# Research 01 - Launcher cell substrate and share ingestion

**Serves strategic §6 items:** 1 (place shortcut), 2 (driver-mode shortcut), 4 (person shortcut), 5 (place arrives by Share)
**Date:** 2026-08-09
**Mode:** read-only codebase survey

---

## Verdict summary

- The cell model already carries everything a geographic target needs: a sealed command hierarchy with a text codec, a tolerant decoder, and a percent-encoded multi-field payload precedent.
- External-app-initiated cell creation is already solved end to end by S1205 and is the exact shape a Maps share must reuse.
- Strategic §4 is wrong on one point: the app reads device location in two places today. Correction carried into research 03.
- Strategic §6 item 4 (person shortcut) needs **no code in this ticket** - S1205 shipped the mechanism.

---

## Files

| Path | Role |
|------|------|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt` | Sealed command hierarchy + `encode`/`decode` codec for the `target` TEXT column; 11 kinds, 300 LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCell.kt` | Domain cell model, `LauncherCellKind`, `LauncherOrientation` |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/LauncherCellEntity.kt` | Room entity `launcher_cells` + DAO (`firstRowBelowAll`, `findOverlapping`) |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Command dispatcher: `suspend fun launch(command: LauncherCellCommand): Boolean` |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/AcceptPinnedShortcutUseCase.kt` | S1205 - external request to cell, 67 LOC |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/pin/LauncherPinRequestActivity.kt` | S1205 receiving activity, declared in `src/launcherEnabled/AndroidManifest.xml` |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/LauncherDesktopRepository.kt` | `addCellInFirstFreeSlot(cell, columns): Long?` |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Row-major free-slot scan, transactional insert |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt` | Two-level add-cell picker (category, then sub-list), 318 LOC |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | `placeAtPendingSlot` - single write path for both add flows |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | `addCell` / `addCellInFirstFreeSlot` |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Existing generic ACTION_SEND receiver - file import, unrelated to cells |
| `app_v2/src/main/AndroidManifest.xml` | `.StandaloneTextSender` alias, `text/plain` ACTION_SEND, ships disabled |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherSensorPermissionManager.kt` | S1179 - per-gadget-key runtime permission asked at placement time |

---

## 1. Cell target encoding

The `target` column is a single TEXT field decoded by `LauncherCellCommand.decode`. Shape is `key` or `key:param`. Eleven kinds exist; none carries a coordinate pair or an external-app intent beyond a bare package launch.

Two properties matter for a geographic target:

- **Percent-encoded multi-field payload already exists.** `LauncherCellCommand.Contact` and `PinnedShortcut` encode each field with `URLEncoder` and join on `:`, because a user-supplied label may legitimately contain the separator. A place name from a Maps share has the same hazard and must use the same treatment.
- **Decoding is tolerant by contract.** Every `decode*` branch returns `null` on a truncated, unknown or malformed payload instead of throwing, so a cell written by a newer build never crashes an older one. A new kind must preserve this.

Consequence for §3.2 "новые виды целей добавляются без миграции схемы": confirmed - no Room migration is needed for either new command kind.

## 2. Command execution

`ExecuteLauncherCommandUseCase.launch` is a `when` over the sealed subtypes, building an `Intent`, adding `FLAG_ACTIVITY_NEW_TASK`, and wrapping `startActivity` in `runCatching`. A successful launch is journalled via `LauncherJournalRepository`. `ScheduledOp` / `LauncherAction` / `Section` return `false` because the host intercepts them earlier.

A geographic target and a driver-mode target both fit the external-intent branch shape - no new dispatch layer is required.

**Gap:** this class has **no unit test**, and neither does `AcceptPinnedShortcutUseCase`. Adding a sealed subtype here is easy to leave unverified. The codec itself is tested (`LauncherCellCommandTest.kt`, `LauncherContactCommandCodecTest.kt`), as is `LauncherCellDao` and `addCellInFirstFreeSlot`.

## 3. Cell creation paths

Two, converging on one write path:

1. User points at an empty square, or taps the toolbar add action -> `LauncherCellContentPickerDialogFragment` (category, then sub-list) -> `LauncherHomeActivity.placeAtPendingSlot(kind, target, spanW, spanH, ..)` -> `LauncherHomeViewModel` -> `LauncherDesktopRepository`.
2. External event, no pointed square -> `addCellInFirstFreeSlot(cell, columns)` directly. Used by `AcceptPinnedShortcutUseCase` (S1205) and `PlaceHomeWidgetOnLauncherDesktopUseCase`.

Path 2 is the established "an external event creates a cell autonomously" route and is what a Maps share must use. Column count for the current orientation comes from `desktopRepository.state()`; the free-slot scan is row-major and runs inside one Room transaction.

## 4. Share ingestion today

`ReceiveShareActivity` handles ACTION_SEND, but for file import: it materialises shared content into a temp file and offers a copy-to-destination dialog. It never touches `LauncherCellCommand` or the desktop repository.

Its `text/plain` entry point is the `.StandaloneTextSender` activity-alias in `app_v2/src/main/AndroidManifest.xml`, which **ships disabled** and is switched on by a user setting (Default apps > accept shared files).

**Collision risk:** a new `text/plain` ACTION_SEND receiver for Maps places would match the same share intent and can appear beside the existing entry in the system share sheet. Two entries of the same app with different meanings is a user-visible outcome that the strategic spec does not rule on. See "Decisions still owed" below.

## 5. S1205 already delivers §6 item 4

S1205 is `Verified` (2026-08-06). `LauncherPinRequestActivity` handles `android.content.pm.action.CONFIRM_PIN_SHORTCUT`, `AcceptPinnedShortcutUseCase` stores the request as a cell and the desktop launches it by identifier without reading the intent. Google Maps' own "add to home screen" for a shared-location person is exactly such a request.

Therefore strategic goal 4 requires **no new code in S1175**. It requires only a verification step that a Maps-published pinned shortcut lands and launches, which S1205's own audit left open (`Manual / on-device`, last unchecked item: "A genuinely foreign publisher .. not a third-party one").

## 6. Flavor gate

- `src/launcherEnabled/java` is mounted only by `standard` (`app_v2/build.gradle.kts:657`) and `noLegal` (`:688`). `legacy`, `vr`, `photos`, `lite` mount `src/launcherDisabled/java` instead.
- `SUPPORT_LAUNCHER` is `false` in `defaultConfig` (`:270`), `true` for `standard` (`:338`) and `noLegal` (`:413`).
- `launcherFlavors = setOf("standard", "noLegal")` (`:1135-1137`) is the single place `src/launcherEnabled/AndroidManifest.xml` is injected.

Consequence: a receiving activity declared in `src/launcherEnabled/AndroidManifest.xml` is absent from the other four flavors by construction - no `BuildConfig.IS_*` guard needed, satisfying §3.2 and CLAUDE.md Rule 14. This is the S1205 precedent.

## 7. Permission gate for the map gadget

`LauncherSensorPermissionManager` (S1179) maps a gadget key to the runtime permission it needs and is invoked at placement time via `LauncherHomeActivity.placeAfterAsking`. Its `PERMISSIONS` map currently covers compass, speed and steps. `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are already declared in `app_v2/src/main/AndroidManifest.xml`.

Consequence: §3.2's "запрашивать только при первом размещении гаджета, а не при старте приложения" is satisfied by adding one row to that map. No new permission-gate path.

---

## Corrections to the strategic spec

1. **§4 claim "в приложении нет чтения текущего положения устройства" is false.** Two platform-`LocationManager` readers exist: `data/sensors/MotionReadingSource.kt` (S1179) and `ui/cameracapture/helpers/CameraLocationProvider.kt` (S0766). Detail in research 03.
2. **§6 item 4 and §10 treat S1205 as a prerequisite for future work.** S1205 is `Verified`; the item is delivered, not pending.

---

## Decisions still owed before any UI phase

1. **Share-sheet collision.** Our app can offer two `text/plain` entries in the Maps share sheet - the existing file-import alias and the new place receiver. Needs a ruling on labelling or on routing both through one dispatcher.
2. **Launcher mode off.** A pin request only arrives when we are the default home screen; a share intent arrives regardless. A place shared while launcher mode is off would create a cell on a desktop the user never sees. Not covered by the S1205 precedent.
3. **§11.1 "и это видно пользователю заранее"** - when a shared place carries no parseable coordinates the cell opens the place rather than a route. The spec requires this be visible in advance but names no surface for it.
