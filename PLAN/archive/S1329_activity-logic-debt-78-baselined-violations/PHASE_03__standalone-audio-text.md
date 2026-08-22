# Phase 03 - Standalone audio and text hosts

**Strategic spec:** [`../S1329_activity-logic-debt-78-baselined-violations.md`](../S1329_activity-logic-debt-78-baselined-violations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-08-13
**Completed:** 2026-08-13

> **Re-planned 2026-08-13** alongside Phase 02, for the same reason and against the same measurement: the
> forwarding shape dominates here too (Audio 10 of 11 sites, Text 10 of 11). Rationale in strategic §9 ADR-1.

---

## Objective

Move `AudioStandaloneActivity` and `TextStandaloneActivity` onto `StandaloneHostFactory` from Phase 02, and
handle the three sites that are **not** plain forwards. Fourteen violations.

This phase is where all four fix shapes appear in one place, so read the per-field table below rather than
applying one transformation uniformly:

- **F - factory** for the twenty forwarding sites, reusing Phase 02's methods plus two new text-lane ones.
- **V - ViewModel** for `searchLyricsUseCase`, the one field in the whole standalone family that the host
  genuinely calls behaviour on.
- **D - delete** for `TextStandaloneActivity.playbackPositionRepository`, which is injected and never read.

---

## Prerequisites

- [x] Phase 02 is ✅ Done - `StandaloneHostFactory` exists with its nine `create*` methods.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1` immediately before the first edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandaloneHostFactory.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt` | Modified (341 LOC) | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified (640 LOC - backup first) | < 640 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified (572 LOC - backup first) | < 572 |

> Both Activities exceed 500 LOC - take timestamped backups per Rule 5 before editing (CLAUDE.md Rule 5).
> Neither may grow. No `res/layout*` file is touched - landscape parity not applicable.

---

## Per-field map (measured 2026-08-13)

`AudioStandaloneActivity` - 7 fields, 11 sites:

| Field | Shape | Sites |
|---|:--:|---|
| `credentialsRepository` | F | `StandaloneViewManager` (~149), `FileInfoDialog` via `.get()` (~467) |
| `settingsRepository` | F | `StandaloneViewManager` (~155), `StandaloneFileOperationsHandler.getCurrentSettings` lambda (~189), `DestinationButtonsManager` (~204) |
| `playbackPositionRepository` | F | `StandaloneViewManager` (~156) |
| `resolveOpenInFmsTargetUseCase` | F | `StandaloneFileOperationsHandler` (~182) |
| `fileOperationUseCase` | F | `StandaloneFileOperationsHandler` (~190) |
| `getDestinationsUseCase` | F | `StandaloneFileOperationsHandler` (~191), `DestinationButtonsManager` (~205) |
| `searchLyricsUseCase` | **V** | `searchLyricsUseCase.execute(file)` in `showLyrics()` (~283) - a real call, not a forward |

`TextStandaloneActivity` - 7 fields, 11 sites:

| Field | Shape | Sites |
|---|:--:|---|
| `credentialsRepository` | F | `NetworkFileManager` (~193), `FileInfoDialog` via `.get()` (~476) |
| `settingsRepository` | F | `StandaloneFileOperationsHandler.getCurrentSettings` lambda (~150), `DestinationButtonsManager` (~165), `TranslationManager` (~210), `TextViewerManager` (~238) |
| `playbackPositionRepository` | **D** | none - injected at ~116 and never referenced |
| `resolveOpenInFmsTargetUseCase` | F | `StandaloneFileOperationsHandler` (~144) |
| `fileOperationUseCase` | F | `StandaloneFileOperationsHandler` (~151) |
| `getDestinationsUseCase` | F | `StandaloneFileOperationsHandler` (~152), `DestinationButtonsManager` (~166) |
| `saveTextNoteUseCase` | F | `TextEditorSaveFlow(saveTextNote = saveTextNoteUseCase::invoke)` (~131) - the method reference is handed on, so the factory supplies it |

Line numbers are a starting point, not a contract - locate each site by name.

---

## Steps

### Step 03.1 - Extend the factory and the ViewModel for the two extras

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandaloneHostFactory.kt`,
`app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Two additions, one per non-forward field.
>
> **Factory.** Add `SaveTextNoteUseCase` as a **ninth** `private val` constructor dependency and two methods:
> (Corrected 2026-08-13: this line read "seventh". The factory as built carries 8 parameters, because step 02.1
> also injects `SendToMenuManager` as its own prompt instructed. Nine is legal - `constructorThreshold: 10` and
> detekt reports at the threshold - but it is the last free slot, so add nothing else to this constructor.)
> `createTextViewerManager(..)` supplying `settingsRepository`, and `createTextEditorSaveFlow(..)` supplying the
> `saveTextNote` function value the host passes today as `saveTextNoteUseCase::invoke`. Change no manager's
> constructor signature (strategic §9 ADR-1).
>
> **ViewModel.** `searchLyricsUseCase` is the one field here the host actually calls behaviour on, so it takes
> the ViewModel shape rather than the factory shape. Constructor-inject `SearchLyricsUseCase` into
> `StandalonePlayerViewModel` and expose the lookup as a suspend function named for what the host does with it -
> not as a property returning the injected type. All five standalone hosts share this ViewModel; adding an
> audio-only operation to it is acceptable because the audio host is the only one that binds a lyrics view, but
> do not change any existing state or event in the class.
>
> Keep every line ≤ 120 characters and avoid bare numeric literals (CLAUDE.md Rule 19, detekt-clean-first).

**Verification:**

- `Grep` - `SaveTextNoteUseCase` matches at least once in `StandaloneHostFactory.kt`.
- `Grep` - `createTextViewerManager` and `createTextEditorSaveFlow` each match exactly once in `StandaloneHostFactory.kt`.
- `Grep` - `SearchLyricsUseCase` matches at least once in `StandalonePlayerViewModel.kt`.
- `Grep` - `^\s+val \w+: (SearchLyricsUseCase|SaveTextNoteUseCase)` (no `private` modifier) returns zero hits across both files.
- `Grep` - `git diff --stat` shows no change to `TextViewerManager.kt` or `TextEditorSaveFlow.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Factory gained SaveTextNoteUseCase as its ninth constructor dependency plus createTextViewerManager (7 parameters, exactly at the measured method ceiling) and createTextEditorSaveFlow (2). Both adapt to the managers as written: TextViewerManager keeps saveFlow as a caller-supplied parameter because only a writable local text file gets one and the host is what knows that, while textNoteStagingRegistry and loadingIndicatorCoordinator keep their null defaults as unified-player-only. createTextEditorSaveFlow hands the flow saveTextNoteUseCase::invoke, which is the seam S1195 already built into that class. StandalonePlayerViewModel took SearchLyricsUseCase as a private constructor dependency and exposes findLyricsFor(mediaFile, resolvedTitle, resolvedArtist), keeping the use case's own default arguments so the audio host loses no capability; no existing state or event changed and the constructor is at 8 of 10. Verification: createTextViewerManager and createTextEditorSaveFlow one hit each, SaveTextNoteUseCase and SearchLyricsUseCase present, no non-private val of either type, and git diff --stat empty for TextViewerManager.kt and TextEditorSaveFlow.kt. post-change PASS, detekt scoped clean over both files.

---

### Step 03.2 - Migrate AudioStandaloneActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Back up the file per Rule 5 first. Inject `StandaloneHostFactory`, delete all seven flagged fields and
> their imports, and route the ten forwarding sites through the factory per the per-field map above.
>
> `searchLyricsUseCase` is the exception: replace `searchLyricsUseCase.execute(file)` in `showLyrics()` with the
> suspend function added to `StandalonePlayerViewModel` in step 03.1. This host already binds that ViewModel via
> `by viewModels()`, so no new binding is needed.
>
> This host has no settings-collect site, so the inherited `appSettings` is not used here - do not add a read
> that did not exist.
>
> Leave `keyBindingManager`, `sendToMenuManager` and the network clients and handlers untouched - none is a
> domain type.

**Verification:**

- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `AudioStandaloneActivity.kt`.
- `Grep` - `standaloneHostFactory` matches at least once in `AudioStandaloneActivity.kt`.
- `Grep` - `searchLyricsUseCase` returns zero hits in `AudioStandaloneActivity.kt`.
- `Grep` - `showLyrics` still matches in `AudioStandaloneActivity.kt` (behaviour preserved, not deleted).
- `Grep` - `keyBindingManager` still matches in `AudioStandaloneActivity.kt`.
- File line count is lower than the backup taken at the start of this step.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - AudioStandaloneActivity now holds StandaloneHostFactory and keyBindingManager, nothing else injected. All ten forwards routed: StandaloneViewManager, StandaloneFileOperationsHandler via StandaloneFileOpsCallbacks, DestinationButtonsManager and FileInfoDialog. searchLyricsUseCase.execute(file) in showLyrics became viewModel.findLyricsFor(file), which is the V shape from step 03.1; showLyrics and the menu_lyrics entry are untouched, so the capability is preserved. As in phase 02 the routing orphaned the forwarded collaborators - the seven network clients, the five file-op handlers and sendToMenuManager all had zero readers afterwards - so they were deleted under Rule 20 with their imports; none is typed Repository or UseCase, so the count is unaffected. The plan's note that this host has no settings-collect site held: nothing was converted to appSettings and no read was added. AppSettings, stateIn, map and SharingStarted stay - keepScreenAwakeFor and the two capability StateFlows still use them. Comments moved rather than dropped (Rule 8): the S0612 destination-list note now sits on the destination-buttons site, the SAF-rename note stays on updateAudioMediaItem, the trimmed-layout note stays on the view-manager site, and the lazy-instantiation rationale is restated on the factory field. Verification: multiline @Inject repository/use-case grep 0 hits, standaloneHostFactory present, searchLyricsUseCase 0 hits, showLyrics and keyBindingManager present, 514 lines against the 576-line backup recorded before the edit post-change PASS with activity-logic delta 0.

---

### Step 03.3 - Migrate TextStandaloneActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Back up the file per Rule 5 first. Inject `StandaloneHostFactory`, delete all seven flagged fields and
> their imports, and route the ten forwarding sites through the factory per the per-field map above.
>
> `playbackPositionRepository` (~116) has **zero** call sites in this file - verified 2026-08-13 by grepping the
> full file text. Delete the field and its import outright; do not add a factory method for it and do not
> forward it anywhere. Before deleting, re-run the grep to confirm the field is still unreferenced - if a call
> site has appeared since, treat it as form F like the other hosts and say so in the step log.
>
> This host implements `SharePrintHost` and `DocumentPrintHost` - leave both interface implementations and every
> non-domain field (`sendToMenuManager`, `keyBindingManager`, `capabilityAvailability`, `mediaCapabilities`)
> exactly as they are.

**Verification:**

- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `TextStandaloneActivity.kt`.
- `Grep` - `standaloneHostFactory` matches at least once in `TextStandaloneActivity.kt`.
- `Grep` - `playbackPositionRepository` returns zero hits in `TextStandaloneActivity.kt` (dead field deleted, not relocated).
- `Grep` - `SharePrintHost` and `DocumentPrintHost` still match in `TextStandaloneActivity.kt`.
- File line count is lower than the backup taken at the start of this step.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - TextStandaloneActivity now holds StandaloneHostFactory plus keyBindingManager, capabilityAvailability and mediaCapabilities. Ten forwards routed: StandaloneFileOperationsHandler via StandaloneFileOpsCallbacks, DestinationButtonsManager, NetworkFileManager, TranslationManager, TextViewerManager and FileInfoDialog, and the TextEditorSaveFlow delegate now calls createTextEditorSaveFlow instead of building the flow around saveTextNoteUseCase::invoke itself. The D case was re-verified before deleting, as the prompt required: playbackPositionRepository had exactly one occurrence in the file, its own declaration, so it was deleted outright with its import rather than relocated - a text file has no playback position. Routing orphaned the same collaborator set as the other hosts (seven network clients, five file-op handlers, sendToMenuManager), all deleted under Rule 20 after measuring zero readers. SharePrintHost and DocumentPrintHost implementations untouched. One deliberate wording choice: the factory field's comment does not name the deleted repository, because the step's own predicate requires zero occurrences of that identifier in the file and a comment about an absent field carries little value in code - the reason is recorded here instead. Verification: multiline @Inject repository/use-case grep 0 hits, standaloneHostFactory present, playbackPositionRepository 0 hits, both print interfaces present, 465 lines against the 532-line backup recorded before the edit post-change PASS with activity-logic delta 0.
- 2026-08-13 - 2026-08-13 PHASE CLOSE. Build: a.ps1 dq BUILD SUCCESSFUL in 1m 10s, exit 0, hiltJavaCompileStandardDebug in the executed tasks - re-run after the audit fix below, because that fix changed an injected type and the earlier green build predated it. Counter: baseline 60, actual 46, delta -14, ratcheted to 46. Zero-diff: no change to PlayerActivity.kt, lint-baseline.xml, or any of TextViewerManager, TextEditorSaveFlow, StandaloneViewManager, StandaloneFileOperationsHandler, DestinationButtonsManager, TranslationManager, NetworkFileManager, FileInfoDialog, SearchLyricsUseCase, SaveTextNoteUseCase - no manager or use case signature moved. PhotoVideoStandaloneActivity.kt still carries its two pre-existing archived-probe deletions from before phase 02; read, not assumed. Zero hits for @Suppress(ActivityLogicViolation), Timber.d(S1329: and TODO(phase-03). PHASE-BOUNDARY AUDIT, background-playback focus, one P1 found and fixed inside the phase. FINDING P1: step 03.1 did what the plan said and constructor-injected SearchLyricsUseCase into StandalonePlayerViewModel, but Dagger builds constructor parameters eagerly and all five standalone hosts share that ViewModel. Opening a text or document file would therefore have constructed SearchLyricsUseCase and with it SmbClient (verified @Singleton), SftpClient, FtpClient, the credentials repository and the file cache - contradicting the invariant the hosts document in their own comment, that standalone opens local and content URIs far more often than network paths so the heavy clients stay behind dagger.Lazy. Fixed by holding the use case as Lazy<SearchLyricsUseCase> and resolving it inside findLyricsFor; the V shape is unchanged, it is still a private dependency and not a public property of an injected type. NOT FOUND, having been checked rather than assumed: findLyricsFor is still called from the host's own lifecycleScope and execute still does its own withContext(Dispatchers.IO), so neither the dispatcher nor cancellation-on-destroy changed; listener-symmetry reports new imbalance 0 on all three files; no media-session or ExoPlayer ownership moved, since StandaloneViewManager's construction site and signature are both untouched; TextStandaloneActivity keeps its explicit lazy delegates for translation and text viewing, so the S0872 release-only-if-created property survives.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `.\a.ps1 dq` (not `fk` alone - the factory's new dependency must resolve in the
      Hilt graph, and only `hiltJavaCompileStandardDebug` proves that).
- [x] `scripts/quality/assert-activity-logic-not-growing.ps1` reports `actual 46`, delta -14 from 60, and the
      baseline is ratcheted down to 46 with `-UpdateBaseline`.
- [x] `git diff --stat` shows **zero** changes to `PlayerActivity.kt` and `PhotoVideoStandaloneActivity.kt`.
- [x] `Grep` - `@Suppress("ActivityLogicViolation")` returns zero hits repository-wide.
- [x] `Grep` - `Timber.d("S1329:` returns zero hits (this ticket adds no probes).
- [x] `app_v2/lint-baseline.xml` unchanged - regenerated only in Phase 06.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for the phase via `.\scripts\add_to_dev_log.ps1` (one entry, not one per file).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `StandalonePlayerViewModel` public API changed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Focus: the audio host runs background playback -
      confirm no listener registration or media-session ownership changed (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

The whole standalone family in scope is clear; `StandaloneHostFactory` carries seven dependencies and eleven
`create*` methods. Phase 04 shares nothing with it - `ReceiveShareActivity` is a different subsystem with its
own managers - so the two may be worked in either order.

The count after this phase is 46: the two deferred files' 32, plus the 14 of Phases 04-05.

---

## Rollback Plan

Revert the phase commit(s). No data migration and no persisted-format change. Steps 03.2 and 03.3 touch disjoint
hosts and are independently revertible; step 03.1 is a prerequisite for both.
