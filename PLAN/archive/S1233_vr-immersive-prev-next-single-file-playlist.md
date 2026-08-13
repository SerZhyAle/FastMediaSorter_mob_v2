# S1233 - PREV/NEXT in the immersive HUD have nothing to navigate: the playlist holds one file

**Status:** Archived
**Priority:** 70

## 0. Raw capture

Owner, 2026-07-27, in the headset:

> "кнопки вперед/назад в HUD не переходят к следующему предыдущему ролику в спсике по ренсурсу"

## 1. Cause - not a wiring bug

The buttons are wired correctly. `DiagnosticXrActivity`'s HUD listener calls `playbackController.next()` / `.prev()`, which move `currentPlaylistIndex` through `mediaPlaylist`. The problem is what that list contains.

`DiagnosticXrActivity.prepareLaunchMedia()`:

```kotlin
mediaPlaylist = when (launchInput.launchMode) {
    VrLaunchMode.DIAGNOSTIC_PLAYLIST -> scanMediaFiles()
    VrLaunchMode.FILE_URI -> {
        val launchFile = resolveSingleLaunchFile(launchInput)
        ..
        listOf(launchFile)          // exactly one element
    }
    ..
}
```

Entering immersive from the player or from "Open in VR Cinema" uses `FILE_URI`, whose playlist is a **single-element list**. PREV and NEXT therefore have nowhere to go. In `DIAGNOSTIC_PLAYLIST` mode they do work - the diagnostic scan returns 19 files - which is why the buttons look functional when tested from the diagnostics entry and dead in real use.

## 2. What has to change

The launch contract, not the buttons. `VrLaunchInput` carries `fileUriString` - one file. To navigate a resource, the immersive host needs either:

- the surrounding file list plus the index of the launched file, passed through `VrLaunchPayloadHolder` (which already exists for exactly this kind of out-of-band payload), or
- the resource id plus the current path, and the host re-queries the file list itself.

The second keeps the intent small but pulls a repository dependency into the immersive host; the first keeps the host dumb but has to survive process death alongside the rest of the payload. Decide during tactical planning.

## 3. Constraints worth knowing before designing

- The flat player already owns an ordered playlist with a sort mode and a playback-order mode (`PlayerViewModel.playbackOrderMode`). Immersive should inherit the **same order the user is looking at**, not re-sort - otherwise "next" means different things in the two surfaces.
- Network resources: the list may contain `smb://` / `sftp://` paths. Immersive video playback of those is broken for a separate reason - see **S1218** and the note in **S1222** - so navigation must not crash when it lands on one.
- The immersive session is reused across media switches by design (`DiagnosticXrRenderThread` KDoc); do not relaunch the Activity per item, or every PREV/NEXT costs the ~5 s OpenXR re-init.

## Goal

Кнопки вперёд/назад в иммерсивном HUD должны листать тот же список файлов ресурса, который пользователь видит в плеере, а не упираться в playlist из одного элемента.

## 4. Decision - carry the ordered list, do not re-query by resource id

Section 2 left the choice to tactical planning. It resolves from the code, not from an owner preference; all three arguments point the same way.

- Section 3 requires immersive to inherit the order the user is looking at. That order is `PlayerViewModel.PlayerState.files`, already sorted by the player's sort mode. Re-querying by `resourceId` in the host would have to reproduce that sort from scratch, which is exactly the "next means different things in the two surfaces" failure section 3 names.
- The survivability worry in section 2 dissolves on inspection. The playlist rides **inside** `VrLaunchInput`, which already travels as a `VrLaunchPayloadHolder` token; process death already loses the whole launch input and `DiagnosticXrLaunchArgs.DEFAULT_INPUT` takes over. Adding fields to that payload introduces no failure mode the launch did not already have.
- Option 2 would pull a repository and the network stack into `src/vr`. The host today resolves `File`s and nothing else - see `resolveSingleLaunchFile`.

## 5. Which entries the caller may hand over

The host can open a local `File` and, for images only, materialise a `content://` URI into its cache. It has no SMB/SFTP/cloud client and must not grow one. So the **caller** filters, and "navigation must not crash on an `smb://` path" (section 3) becomes true by construction rather than by a guard.

An entry is handed over when all hold:

- not a directory;
- `MediaType` is `IMAGE` or `VIDEO` - `GIF` is short-circuited by `prepareLaunchMedia` and would strand navigation on a file the host refuses;
- `path` is local, i.e. starts with `/` or `file://`.

A network-only or cloud-only resource therefore collapses to an empty playlist, and the host keeps today's single-file behaviour - no regression, nothing new to crash on. This is acceptable rather than merely tolerable: immersive playback of network paths is separately broken (**S1218**), so the dropped entries are ones the host could not play anyway.

`NetworkFileManager.prepareFileForRead` returns a local file unchanged, so the launched image's URI (`Uri.fromFile(prepared)`) and its playlist entry (built from `path`) denote the same file. The index cannot drift.

## 6. Per-entry media type, not an extension guess

`loadCurrentMediaItem` decides image-vs-video with `file.extension in setOf("jpg","jpeg","png")` and sends everything else to ExoPlayer. That is wrong for a `.webp` or `.heic` image **today**, on a plain single-file launch, and playlist navigation would multiply the exposure by the size of the folder.

The fix here does not guess better - it stops guessing. The caller knows each entry's `MediaType` from the domain model, so the playlist carries `VrMediaType` per entry and the host branches on that. The single-file path gains the same correctness for free: `launchInput.mediaType` was always authoritative and was simply not consulted.

The diagnostic scan keeps `isVideoFilename`, because a filesystem scan of the test folder has no typed source to carry.

## Phase 1 - Extend the launch contract

- [x] `VrPlaylistEntry(fileUriString, mediaType)` added to `core/xr/VrLaunchContract.kt`, `Serializable` like its neighbours.
- [x] `VrLaunchInput` and `StartVrPlaybackRequest` gain `playlist: List<VrPlaylistEntry> = emptyList()` and `playlistIndex: Int = -1`; `VrLaunchInput.fromRequest` forwards both.
- [x] Defaults keep every existing caller and the `DEFAULT_INPUT` fallback compiling and behaving unchanged.
- **Verification:** `.\a.ps1 fk` - `BUILD SUCCESSFUL in 47s`, `compileStandardDebugKotlin` executed (not up-to-date).

The transport was checked rather than assumed: `StartVrPlaybackUseCaseImpl.normalizeRequest` uses `request.copy(..)` and only rewrites the `DIAGNOSTIC_PLAYLIST` case, and `VrLaunchInput.fromRequest` now forwards both new fields, so nothing between the caller and the host drops them.

## Phase 2 - Populate it at the launch surfaces

There are **three** `FILE_URI` callers, not one. Section 1 already named two of them ("from the player or from Open in VR Cinema"), so fixing only the player would have left the ticket's own problem statement half-addressed.

- [x] `core/xr/VrLaunchPlaylistFactory` owns the section 5 filter. Two entry points share it precisely so they cannot drift into disagreeing about what is navigable.
- [x] `PlayerVrLaunchManager` builds from `viewModel.state.value.files`.
- [x] `BrowseVrCinemaLaunchManager.launch` takes the surrounding folder; `BrowseFileOverflowMenuManager.showFor` grew a defaulted `siblings` parameter and `BrowseManagerInitializer` passes `currentState.mediaFiles`. One call site, so the default is belt-and-braces rather than load-bearing.
- [x] Fewer than two surviving entries, or the current file missing from them, hands over an empty playlist - navigation has nothing to offer and the host must not be told otherwise.
- **Verification:** `.\a.ps1 fk` exit 0.

`StandaloneVrCinemaLaunchManager` is deliberately untouched. Its hosts (`StandalonePlayerActivity`, `PhotoVideoStandaloneActivity`) hold `state.value.mediaFile` - one file arriving from another app's VIEW intent - so there is no surrounding list to inherit and an empty playlist is the correct answer, not an omission.

`ResourceVrCinemaLaunchManager` is also out of shape here: it dispatches `RESOURCE_BROWSE` to `ImmersiveBrowseActivity`, a grid browser with no HUD transport strip.

While there: `BrowseVrCinemaLaunchManager.isLocalPath` was dead (declared, never called) and the factory now owns that predicate, so it is gone (Rule 20).

## Phase 3 - Consume it in the immersive host

- [x] `DiagnosticXrActivity.mediaPlaylist` becomes `List<PlaylistItem>` carrying `File` + `VrMediaType`; `currentPlaylistIndex` starts at the launched file's index rather than 0.
- [x] `prepareLaunchMedia`'s `FILE_URI` branch resolves the carried playlist and falls back to the single launched file when it is empty or resolves to nothing. List and starting index are now decided together instead of the index being assumed afterwards.
- [x] `loadCurrentMediaItem` branches on the entry's `mediaType`.
- [x] The session is not relaunched per item - `navigateToNextMedia` / `navigateToPrevMedia` are untouched in shape, honouring the reuse contract in section 3.
- **Verification:** `.\a.ps1 fk`, `.\a.ps1 fkn` and a `vr`-flavor Kotlin compile all exit 0, with `compileNoLegalDebugKotlin` and `compileVrDebugKotlin` both executed rather than up-to-date.

`src/vr/java` reaches **two** flavors, which is why both were compiled. It is the `vr` flavor's own source set, mounted by AGP's flavor-name convention - the proof is that `MediaCapabilitiesModule` exists once per flavor under `src/legacy`, `src/lite`, `src/photos`, `src/standard` and `src/vr`, with **no** `noLegal` copy. `noLegal` then borrows the same directory by hand (`build.gradle.kts:611`, plus res and manifest), which is exactly why it needs no module of its own. The explicit `add` in the `noLegal` block reads at a glance like the only mount and is not.

Two further guesses fell out with the carried type, both of them live bugs before this change:

- `prepareInitialFrame` decoded `mediaPlaylist.firstOrNull()`. With a real playlist that is the wrong file - entering immersive on the tenth item of a folder would have decoded the first one.
- `onRenderThreadSessionReady` restarted video only when `isVideoFilename(..)` said so, and that set is `{mp4, mkv}`; a `.webm` or `.mov` item silently never resumed after a session was recreated.

## Gate verdict - read this before assuming green

- `.\a.ps1 fg` (fast static gates): **PASS**, 0 failures. `assert-no-ticket-logs` went `2 -> 0` once the ticket moved to `BlockNeedUserTest`; the two entries it flagged were this ticket's own probes, legal only in that status.
- `.\a.ps1 fk` / `fkn` / `vr` code check: **PASS**, with `compileStandardDebugKotlin`, `compileNoLegalDebugKotlin` and `compileVrDebugKotlin` all actually executing.
- `assert-detekt -Gate -ChangedFiles ..`: **FAIL**, and knowingly so. Six findings across four touched files; five are untouched pre-existing debt that the diff-scoped gate resurfaces because the edits shifted their signatures - `LongParameterList 37/10` and `ImportOrdering` on `BrowseManagerInitializer` (constructor and import block, neither edited), `TooGenericExceptionCaught` at `PlayerVrLaunchManager:143`, `CyclomaticComplexMethod 30/20` on `showFor` (no branch was added), and `SpacingBetweenDeclarationsWithComments` on an S1114 comment in `VrLaunchContract`.

The sixth is mine: `LongParameterList` on `showFor` went **20/8 to 21/8**. It is accepted rather than dodged. The alternatives were hidden mutable state in the launch manager, or wrapping two parameters in a context object purely to sit back under a threshold the function is already far past - cosmetic, not a fix. **S1252** owns restructuring this menu and now names the signature explicitly in its section 4a.

Three findings that *were* mine were fixed before this verdict: `SerialVersionUIDInSerializableClass` on `VrPlaylistEntry`, and `ReturnCount` on both `isImmersiveNavigable` and `resolveCarriedPlaylist`.

## Documentation

`docs/VR_*.md` (registry record `vr-docs`) needs no edit. `VR_CONTROLS.md` already documents the aiming-ray trigger and thumbstick as moving to the **next / previous file**; this change makes the shipped behaviour match what the page has been promising, rather than describing something new.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1218, S1222, S1228, S1232

## 7. Related

- **S1232** - the same strip's summon/exit rework.
- **S1228** - the strip itself.
- **S1222** - the immersive browser ignores stereo config on playback; a resource-wide playlist in the player makes that inconsistency more visible, since both surfaces will then traverse the same files.
