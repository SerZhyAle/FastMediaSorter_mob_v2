# S1206 research - AS-IS contact cell pipeline

Date: 2026-08-08. Read-only survey of the launcher contact cell path, the permission plumbing and the
seam a live read has to enter through. Every claim carries a file and a line range.

## 1. Verdict on prior art

No live-contact read exists anywhere in `app_v2/src`. `ContactSnapshotDataSource` is the only class that
touches `ContactsContract`, and it reads exclusively at pick time under the one-time grant the system
picker attaches to the returned `Uri`. The drift-check verdict `DRIFT` for this ticket is a false
positive: it matched a commit message mentioning the id, and reports `code markers (S1206:): 0 in 0 files`.

## 2. Display path, end to end

- `LauncherCellDao.observeByOrientation` - Room `Flow`, emits on every desktop write.
  `data/local/db/LauncherCellEntity.kt:35-36`
- `LauncherDesktopRepositoryImpl.observeCells` maps rows through `toDomainOrNull`, producing `LauncherCell`
  that still holds the encoded `target` string.
  `data/repository/LauncherDesktopRepositoryImpl.kt:29-31,358-391`
- `ResolveLauncherDesktopUseCase.invoke` combines the cell flow with the radio-state flows and re-resolves
  the whole list on any emission, then calls `resolveVisual` once per cell.
  `domain/usecase/launcher/ResolveLauncherDesktopUseCase.kt:36-61`
- `ResolveLauncherCommandLabelUseCase.contactVisual` turns `LauncherContactTarget` into a
  `LauncherCommandVisual`: label plus `monogramSeed`, `iconRes = null`, and no photo of any kind. It reads
  only fields already inside the target, so it performs no I/O at all.
  `domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt:112-160`
- `LauncherHomeViewModel.cells` exposes the result as a `StateFlow` shared with
  `SharingStarted.WhileSubscribed(5_000L)`.
  `src/launcherEnabled/.../ui/launcher/LauncherHomeViewModel.kt:93-96`, constant at `:663`
- `LauncherCellViewBinder.bindShortcut` sets caption and icon; `bindMonogram` draws the initials disc when
  `visual.monogramSeed != null`.
  `src/launcherEnabled/.../ui/launcher/grid/LauncherCellViewBinder.kt:247-285,346-358`

## 3. The seam for a live read

`contactVisual` is the correct and only seam. It already runs once per cell, off the main thread
(`Dispatchers.IO` at `ResolveLauncherDesktopUseCase.kt:45`), inside the same `when` where every other
command kind resolves live data: `appVisual` queries `PackageManager` synchronously
(`ResolveLauncherCommandLabelUseCase.kt:198`), `pinnedShortcutVisual` queries `AppShortcutDataSource`
synchronously (`:225`). A live contact read fits that existing contract without a new seam.

The same resolver also feeds the taskbar through `LauncherHomeViewModel.pinnedIcons`
(`LauncherHomeViewModel.kt:127-143`), so any change lands in both surfaces at once.

## 4. Re-resolution trigger, and the precedent for it

`ResolveLauncherDesktopUseCase.invoke` already `combine`s the cell flow with radio-state flows precisely so
that state changing outside the database still reaches a tile - the S1441 precedent, rationale in the
comment at `ResolveLauncherDesktopUseCase.kt:31-35`. A permission-grant state flow enters the same
`combine` the same way.

This matters because `WhileSubscribed(5_000L)` means a Home -> grant dialog -> Home round trip inside five
seconds keeps the previously resolved list: the upstream is not cancelled, and no database write occurs, so
nothing re-resolves. Without an explicit grant-state input, the owner's "already-pinned cells convert
silently at next display" decision does not hold on the fastest and most likely path.

## 5. Permission plumbing

- `READ_CONTACTS` is declared unconditionally at `src/main/AndroidManifest.xml:22` (S1335).
- Registered as optional, request-on-demand, gated on `SUPPORT_LAUNCHER`, in
  `data/permissions/PermissionRegistryRepositoryImpl.kt:126-134` - so the Settings > Permissions row is
  invisible on flavors with no launcher even though the manifest declares the permission everywhere.
- Grant state is checked through `CheckPermissionStatusUseCase`
  (`domain/usecase/CheckPermissionStatusUseCase.kt:24-93`), which wraps
  `ContextCompat.checkSelfPermission(..) == PackageManager.PERMISSION_GRANTED` at `:78`.
- Rationale copy is assembled by `Context.permissionRationale(permission, task)` /
  `permissionRationaleShort(..)` at `ui/common/permissions/PermissionRationaleText.kt:24-35`, which pulls the
  registry's `descriptionRes` / `rationaleRes` plus a per-`PermissionTask` addendum
  (`PermissionEntry.taskAddenda`, `PermissionEntry.kt:56-61`). Precedent for adding one:
  `PermissionTask.QR_PAIRING` on the camera entry, `PermissionRegistryRepositoryImpl.kt:145-147`.

## 6. Request-at-pin-time precedent

`LauncherSensorPermissionManager` is the closest existing shape to the owner's decision: its
`placeAfterAsking(gadgetKey, place)` checks the grant, launches `ActivityResultContracts.RequestPermission()`
when needed, and defers the placement callback until the answer arrives.
`src/launcherEnabled/.../ui/launcher/helpers/LauncherSensorPermissionManager.kt:22-85`, deferral rationale at
`:44-48`. It is constructed in a field initialiser of `LauncherHomeActivity` (`:126`) because registering a
contract after `onStart` throws (KDoc `:18-20`).

A weaker precedent that does not gate the action: `requestPhoneStatePermission`, `LauncherHomeActivity.kt:120-121`.

## 7. Pin/creation flow

`LauncherContactPickManager.onPickResult`
(`src/launcherEnabled/.../ui/launcher/helpers/LauncherContactPickManager.kt:123-148`) ->
`LauncherHomeViewModel.resolveContactPick` (`:660`) -> `PickContactShortcutUseCase.invoke`
(`domain/usecase/launcher/PickContactShortcutUseCase.kt:48-55`) -> `ContactSnapshotDataSource`. On
`Outcome.Ready` the manager calls back into `addShortcut(LauncherCellCommand.Contact(target))`
(`LauncherHomeActivity.kt:131-136,859-866`). The permission request belongs immediately before the picker is
launched, mirroring `placeAfterAsking`.

## 8. Photo loading

Nothing in the project loads a contact photo today - no `photoUri` or `ContactPhoto` hits in `app_v2/src`.
Launcher cell icons flow through `LauncherCommandVisual.iconDrawable`, whose KDoc
(`ResolveLauncherCommandLabelUseCase.kt:33-37`, `equals`/`hashCode` at `:66-84`) requires a stable `iconKey`
for identity, because `LauncherCellViewBinder.bind` skips a rebuild when the `(cells, columns, editMode)`
triple compares equal (`LauncherCellViewBinder.kt:47,61-76`). A freshly decoded `Drawable` carries default
identity equality, so a live photo without a stable `iconKey` either thrashes the binder or never updates.

## 9. Flavor gating

`SUPPORT_LAUNCHER` is `[+]` on `standard` and `noLegal` only (`docs/FLAVOR_MATRIX.md:25`); those two mount
`src/launcherEnabled` (`app_v2/build.gradle.kts:644-647,676-678`). `lite`, `photos`, `legacy` and `vr` have
no launcher desktop and therefore no contact cells. `legacy`'s `minSdk 23` is never exercised by this
feature. `ContactsContract` photo APIs are unchanged since API 11, so no compat shim is needed.

## 10. Test coverage

Zero unit tests exist for `ResolveLauncherCommandLabelUseCase`, `ResolveLauncherDesktopUseCase`,
`LauncherCellViewBinder`, `PickContactShortcutUseCase`, `ContactSnapshotDataSource`,
`LauncherContactPickManager` or `LauncherSensorPermissionManager` - grepped across `app_v2/src/test` and
`app_v2/src/androidTest`, no hits for any of the seven. `CheckPermissionStatusUseCaseTest` and
`PermissionRegistryRepositoryImplTest` do exist and cover the permission side.

## 11. Risks carried into the plan

- Stale `iconKey` on a live photo drawable breaks the binder's rebuild guard. Highest risk item; fix by
  deriving the key from the same fallback chain the caption already uses.
- An unthrottled live read re-queries every contact cell on every combined emission, including each radio
  state change.
- A read added without the `runCatching` discipline of `ContactSnapshotDataSource` crashes when the
  permission is revoked mid-session.
- `WhileSubscribed(5_000L)` defeats "convert at next display" on a fast round trip - see section 4.
