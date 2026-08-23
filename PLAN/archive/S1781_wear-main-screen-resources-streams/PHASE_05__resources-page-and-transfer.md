# Phase 05 - Resources page and transfer

**Strategic spec:** [`../S1781_wear-main-screen-resources-streams.md`](../S1781_wear-main-screen-resources-streams.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 04
**Blocks:** none directly - Phase 08's "depends on all" covers it
**Steps done:** 4 / 4
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Transfer sends only the phone's selected resources instead of every registered one, and the watch Resources page honours the view mode and explains an empty transfer instead of showing a bare list.

---

## Prerequisites

- [ ] Phase 01, Phase 02 and Phase 04 are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SendResourcesToWatchUseCase.kt` | Modified | ≤ 105 (was ≤ 100; landed at 103) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/BeamAnimationDialog.kt` | Modified | ≤ 210 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkSourcesScreen.kt` | Modified | ≤ 520 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkSourceGrid.kt` | New | ≤ 150 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/viewmodel/NetworkSourcesUiState.kt` | Modified | ≤ 30 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/viewmodel/NetworkSourcesViewModel.kt` | Modified | ≤ 220 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearPreferencesRepository.kt` | Modified | ≤ 50 |
| `wear/src/main/res/values/strings.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 20 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/SendResourcesToWatchUseCaseTest.kt` | Modified | ≤ 280 (was ≤ 200; the file already stood at 215 before this phase, and the fakes are most of it) |

---

## Steps

### Step 05.1 - Send only the selected resources

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SendResourcesToWatchUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `WearResourceSelectionRepositoryImpl` into `SendResourcesToWatchUseCase` and filter `networkResources` down to `getSelectedIds()` before building `payloads`. When the selected set is empty, return early with a result that says nothing was sent rather than falling back to the old "every registered resource" behaviour, and update every caller of `invoke()` that assumed the old unconditional-send contract.

**Why:**

Strategic §5.1 "Набор передаваемых источников" - "Команда передачи перестаёт читать реестр целиком и читает набор. Пустой набор означает, что передавать нечего, и команда обязана сказать это, а не отправить всё" - and §11 criterion 6 both describe exactly this change; strategic §4 records that today's command reads the whole registry unconditionally, which is the behaviour this step retires.

**Correction applied during execution:** the step's `Files Touched` named only the use case, but its own prompt requires updating every caller that assumed the old contract, and one does. `WearSyncViewModel.startPush()` treats any success as "bytes are on the way" and enters `startAckTimeout()`; with nothing sent no watch ack can ever arrive, so an empty selection would have ended in the `wear_sync_no_ack` error after the full timeout - a misleading failure for a state that is not a failure. `WearSyncViewModel.kt` and `BeamAnimationDialog.kt` were added to `Files Touched`: the state model gains `WearSyncUiState.NothingSelected` and the dialog renders it as a neutral explanation naming the phone action, without the `Error` branch's red cross and Retry button (retrying changes nothing while the selection is empty).

**Verification:**

- `Grep` - `WearResourceSelectionRepositoryImpl` present in `SendResourcesToWatchUseCase.kt`.
- `Grep` - `getSelectedIds` present in `SendResourcesToWatchUseCase.kt`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 05.1: SendResourcesToWatchUseCase now injects WearResourceSelectionRepositoryImpl, returns SendResult(0,0) before any putDataItem when the selection is empty, and otherwise filters the registry down to the selected ids (hidden and non-network resources still excluded). Caller correction: WearSyncViewModel.startPush() no longer enters the ack timeout on a zero-send result - a new WearSyncUiState.NothingSelected renders in BeamAnimationDialog as a neutral explanation with a Close action, since the old path would have reported wear_sync_no_ack after the full timeout for a state that is not a failure. String wear_sync_nothing_selected added EN/RU/UK, naming the Resources for the watch screen as the action. Verified: grep WearResourceSelectionRepositoryImpl=2, getSelectedIds=1 in the use case; check_strings_localized -KeyPrefix wear_sync_nothing_selected exit 0; a.ps1 fk exit 0.

---

### Step 05.2 - Rename Network Storage to Resources and honour the view mode

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkSourcesScreen.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/viewmodel/NetworkSourcesViewModel.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearPreferencesRepository.kt`, `wear/src/main/res/values/strings.xml`
**Depends on:** Step 04.1 (repository shape confirmed on the phone side; no direct code dependency)

**Prompt for developer:**

> `NetworkSourcesScreen.kt` is already 440 lines before this step; take a timestamped backup before editing, per the file-size rule (Rule 5 puts it under `temp/`, which is scratch by design and is not part of this spec's durable evidence). Rename the user-visible "Network Storage" string to "Resources" (`R.string.network_storage` value only - keep the constant name unless every call site is updated in the same step) and rename the section label at its `HomeSectionCatalog` entry point from Phase 02 to match. Read `viewMode` from `WearPreferencesRepository` and render `NetworkSourcesUiState.Success` as a list or grid via `GridColumnFit.columnsFor`, the same rule Phase 03.2 used for the home screen - not a second, independent grid rule for this screen. Also add the write side of Phase 02's `lastUsedResourceName`: call `WearPreferencesRepository.setLastUsedResource(name)` at this screen's existing `browseSource` navigation call site, the same one `WearRoutes.browseSource()` is invoked from today.

**Why:**

ADR-1 makes the view mode one value shared by the home screen and this page, so a second independent mode here would contradict the strategic decision directly; strategic §6 item 3 records the owner's ruling that a second setting - and a second copy in Wear Companion - must not appear. The rename follows strategic §1's problem statement that "Network Storage" tells the user nothing about what the section contains. The `lastUsedResourceName` write belongs here because this is the only place `browseSource` is called today (the other call site, `HomeScreen.kt`, only reaches the generic Local `browse()`, not a named resource).

**Correction applied during execution:** adding the grid path inline pushed `NetworkSourcesScreen.kt` to 554 lines, past this step's own ≤ 520 budget, so the cell composables moved to a new `NetworkSourceGrid.kt` in the same package (screen 433, grid 142). The section label needed no rename - Phase 02 already gave the catalog entry `R.string.wear_section_resources`; only the screen title (`network_storage`) was renamed, and its value now matches that label in EN/RU/UK. `NetworkSourcesActions` widened from `private` to `internal` so the extracted file can see it.

**Verification:**

- The pre-edit backup required by Rule 5 was taken; it lives in scratch and is deliberately not cited as durable evidence.
- `Grep` - `GridColumnFit.columnsFor` present in `NetworkSourcesScreen.kt`.
- `Grep` - `setLastUsedResource` present in `NetworkSourcesScreen.kt` or `NetworkSourcesViewModel.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "network_storage"` - exit 0.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 05.2: the Resources page reads the shared viewMode from WearPreferencesRepository and renders sources through GridColumnFit.columnsFor - the same rule the home screen uses, inside BoxWithConstraints so the count comes from measured width, not from the mode name. List mode keeps the informative chip (name + server + hold-to-delete); grid mode draws 48 dp cells with a Storage icon and the resource name, each announcing its own name. Cell composables live in a new NetworkSourceGrid.kt because inline they pushed the screen past its budget. Screen title network_storage renamed to Resources / Ресурсы / Ресурси, matching Phase 02's wear_section_resources. NetworkSourcesViewModel gained rememberLastUsedResource(name), called at the single browseSource call site, feeding the home screen's Last used section. Verified: pre-edit backup taken per Rule 5 (scratch, not durable evidence); grep GridColumnFit.columnsFor=1 in the screen, setLastUsedResource=1 in the viewmodel; check_strings_localized -Module wear -KeyPrefix network_storage exit 0; a.ps1 fw exit 0; a.ps1 fk exit 0. Open for device check: long-press delete on a grid cell shares the Button's gesture area, unlike the chip - confirm it still fires.

---

### Step 05.3 - Add the empty-Resources hint

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkSourcesScreen.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/viewmodel/NetworkSourcesUiState.kt`, `wear/src/main/res/values/strings.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> `NetworkSourcesUiState.Empty` already exists as a state; give it copy that explains what to do rather than showing a bare empty list - name the phone action ("open Wear Companion on your phone and choose resources to send") without technical jargon. Add the string through `set-android-string.ps1 -Action add`, prefixed `wear_resources_empty_`.

**Why:**

Strategic §5.1 "Пустое состояние раздела Ресурсы" and §2.4 goal both require this; `docs/COMMUNICATION_POLICY.md` §2.4 "Empty state" formula - "Explain why there is no content + natural invitation to act" - is the exact template this hint follows, and §6's checklist item "no operation completed successfully phrasing... every empty state has an invitation to act" is what the hint must pass before it ships, since strategic §3.2 explicitly calls this a hint, not an error message.

**Verification:**

- `Grep` - `wear_resources_empty_` keys present in `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "wear_resources_empty_"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 05.3: the empty Resources page now explains the situation instead of stating a fact - wear_resources_empty_hint (EN/RU/UK) says nothing has arrived from the phone yet and names the action, opening Wear-companion on the phone and picking resources, matching COMMUNICATION_POLICY 2.4's why-plus-invitation shape. NetworkSourcesUiState needed no change: Empty already existed and only its copy was wrong. The string it replaced, no_network_sources, had no other reference and no PLAN scaffolding, so it was removed in the same step per Rule 20 rather than left as a second, contradicting empty message. Verified: check_strings_localized -Module wear -KeyPrefix wear_resources_empty_hint exit 0; grep no_network_sources in wear/src = 0; a.ps1 fw exit 0.

---

### Step 05.4 - Unit-test the selection-scoped transfer

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/SendResourcesToWatchUseCaseTest.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Extend or add the test class for `SendResourcesToWatchUseCase`. Assert: with a selection of two ids out of five registered resources, the payload sent to `WearableDataLayerRepository.putDataItem` contains exactly those two; with an empty selection, `putDataItem` is never called and the returned `Result` reports zero sent rather than throwing.

**Why:**

Strategic §11 criterion 6 - "Команда передачи отправляет на часы только отмеченные ресурсы" - is a strategic-level pass condition, and it is also the one line item strategic §7's top risk row exists to guard against; a test is the only durable proof the empty-set branch from Step 05.1 does not regress into "send everything" on some future refactor.

**Verification:**

- `Grep` - a test asserting the sent payload size equals the selection size.
- `Grep` - a test asserting `putDataItem` is not invoked for an empty selection.
- `.\a.ps1 fu` - the test class passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 05.4: SendResourcesToWatchUseCaseTest became a Robolectric class so the real WearResourceSelectionRepositoryImpl backs the selection, and every existing test now states its selection first - the old ones silently assumed send-everything. Two new cases cover the contract: a five-resource registry with ids 2 and 4 selected sends exactly those two (payload parsed back through Gson, not string-matched), and an empty selection returns sent=0 skipped=0 with putDataItem never invoked. Ran out of numeric order, before 05.3: the 05.1 constructor change left app_v2 test sources uncompilable, which made post-change's settings-doc-sync gate report CANNOT-VERIFY rather than a verdict. Verified: check-standard-fast -Mode Unit -Tests *SendResourcesToWatchUseCaseTest* exit 0; testStandardDebugUnitTest-filtered XML tests=8 failures=0 errors=0 at 2026-08-18T18:41:38Z (the unfiltered XML beside it is a stale 01:32 run with 6 tests - not the evidence).
- 2026-08-18 - Phase-05 boundary audit (layers 1-3): no P0/P1. AUDIT-P2: SendResourcesToWatchUseCase, a domain use case, now injects WearResourceSelectionRepositoryImpl, a data-layer concrete class with no domain interface - the dependency points the wrong way across the layer boundary. Left as is deliberately: step 04.1 chose the no-interface shape on purpose, two use cases already do the same, and adding the interface means a new Hilt binding whose module and scope no step names, which is a hard stop rather than a judgement call. AUDIT-P3: getSelectedIds() is a blocking preferences read reached from a Main-dispatched caller; it stays wrapped in StrictModeHelper like every other call site of that repository, so it follows the codebase's chosen pattern rather than introducing a new violation. AUDIT-P3: the screen collects with collectAsState while HomeScreen uses collectAsStateWithLifecycle - the file's own convention was followed, but the two screens now differ. UI evidence (S1338 gate), placement per strategic ADR-1 and the owner ruling in strategic 6 item 3: evidence/S1781_phase05_watch_resources_empty.png - the page titles Resources, not Network Storage, and the empty state explains that nothing has arrived and names the phone action; evidence/S1781_phase05_watch_home_scrolled.png - the home grid with the section catalog and the settings command bar. A first capture appeared to clip the Streams label; scrolling proved it a ScalingLazyColumn edge-scaling artifact, not a layout defect, so nothing was filed. Not proven here: the list-vs-grid rendering of real sources, because the watch emulator is unpaired and the page is Empty - that needs the paired watch at device-test time. Verified: a.ps1 dq exit 0; a.ps1 fw exit 0; check-standard-fast -Mode Assemble -Module wear exit 0; wear-debug.apk installed on emulator-5554.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/wear.jsonl` and `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module <wear|app_v2>`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`SendResourcesToWatchUseCase` reads the phone's selection; the Resources page honours the shared view mode, is renamed, writes the last-used-resource preference, and explains an empty transfer. No phase after this one depends on it directly - Phase 08's closing gate covers the whole changed set.

---

## Rollback Plan

Revert phase commit(s) - the transfer use case reverts to sending every registered resource, and the Resources page reverts to its Phase-01-era list-only, unrenamed state. No data migration.
