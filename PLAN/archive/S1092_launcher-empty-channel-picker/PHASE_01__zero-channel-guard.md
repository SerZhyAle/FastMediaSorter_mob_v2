# Phase 01 - Zero-Channel Guard

**Strategic spec:** [`../S1092_launcher-empty-channel-picker.md`](../S1092_launcher-empty-channel-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-07-21
**Completed:** 2026-07-21

---

## Objective

When the user picks the "Channel" category for a new desktop cell, branch on channel-catalog emptiness: zero channels routes to Settings > Media > Streams with an explanatory toast; one or more opens the existing stream picker unchanged.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `ObserveStreamSourcesUseCase` resolves via Grep (already injected elsewhere in launcher pickers).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 400 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> No landscape layout touched (logic-only change). No new files.

---

## Steps

### Step 01.1 - Add stream-branch events to LauncherHomeEvent

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the existing `sealed interface LauncherHomeEvent` with two parameterless cases: `data object OpenStreamPicker : LauncherHomeEvent` and `data object OpenStreamsSettings : LauncherHomeEvent`. Do not remove or rename the existing `Message` case. These model the two outcomes of choosing the Channel category.

**Verification:**

- `Grep` - `data object OpenStreamPicker : LauncherHomeEvent` present exactly once.
- `Grep` - `data object OpenStreamsSettings : LauncherHomeEvent` present exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-07-21 - Verification 2/2 PASS. Files: LauncherHomeViewModel.kt (+6 LOC, two data objects).

---

### Step 01.2 - Add requestStreamCell() branch resolver to the ViewModel

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inject `ObserveStreamSourcesUseCase` into the constructor as `private val observeStreams: ObserveStreamSourcesUseCase`. Add `fun requestStreamCell()` that launches in `viewModelScope`, snapshots the catalog once via `observeStreams().first()`, and sends `LauncherHomeEvent.OpenStreamsSettings` when the list is empty, otherwise `LauncherHomeEvent.OpenStreamPicker`, through the existing `_events` channel. Keep the snapshot off the main thread by relying on the use case's own dispatcher (do not add `Dispatchers.IO` in the ViewModel). Import `kotlinx.coroutines.flow.first`.

**Verification:**

- `Grep` - `observeStreams: ObserveStreamSourcesUseCase` present in the constructor.
- `Grep` - `fun requestStreamCell` present.
- `Grep` - `LauncherHomeEvent.OpenStreamsSettings` and `LauncherHomeEvent.OpenStreamPicker` both sent inside the ViewModel.

**Status:** `[x]` done

**Step Log:**

- 2026-07-21 - Verification 3/3 PASS. Files: LauncherHomeViewModel.kt (+ constructor inject, requestStreamCell()).

---

### Step 01.3 - Add the trilingual redirect string

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add string key `launcher_edit_streams_enable_first` across EN/RU/UK in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_edit_streams_enable_first -En "Enable streams in Settings first" -Ru "Сначала включите трансляции в настройках" -Uk "Спочатку увімкніть трансляції в налаштуваннях"`. Copy must pass `docs/COMMUNICATION_POLICY.md` §2 (informational guidance formula) and §6 (tone checklist: plain, imperative, no jargon, `..` not `...`, plain hyphen).

**Verification:**

- `Grep` - `launcher_edit_streams_enable_first` present in all three `values*/strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_edit_streams_enable_first"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-07-21 - Verification 3/3 PASS. `launcher_edit_streams_enable_first` added EN/RU/UK; check_strings_localized exit 0.

---

### Step 01.4 - Route CATEGORY_STREAM through the ViewModel and handle both events

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `registerAddFlowListeners()`, change the `CATEGORY_STREAM` branch so it no longer opens the picker directly - call `viewModel.requestStreamCell()` (the `pendingRow`/`pendingCol` are already assigned just above the `when`). In the `viewModel.events` collector, extend the `when(event)` with: `LauncherHomeEvent.OpenStreamPicker ->` open the stream picker exactly as the old branch did (`openPicker(LauncherStreamPickerDialogFragment.newInstance(), LauncherStreamPickerDialogFragment.TAG)`); `LauncherHomeEvent.OpenStreamsSettings ->` `startActivity(SettingsActivity.openStreamsSectionIntent(this))` then show `Toast.makeText(this, R.string.launcher_edit_streams_enable_first, Toast.LENGTH_LONG).show()`. Add the `com.sza.fastmediasorter.ui.settings.SettingsActivity` import. Keep the `when` exhaustive over the sealed interface (no `else`).

**Verification:**

- `Grep` - `viewModel.requestStreamCell()` present in `LauncherHomeActivity.kt`.
- `Grep` - `LauncherHomeEvent.OpenStreamsSettings ->` and `LauncherHomeEvent.OpenStreamPicker ->` both present in the events collector.
- `Grep` - `SettingsActivity.openStreamsSectionIntent(this)` present.
- `Grep` - `import com.sza.fastmediasorter.ui.settings.SettingsActivity` present.

**Status:** `[x]` done

**Step Log:**

- 2026-07-21 - Verification 4/4 PASS. Files: LauncherHomeActivity.kt (CATEGORY_STREAM -> requestStreamCell; two event branches; import).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug; launcherEnabled mounts for standard).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the change via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (LauncherHomeViewModel public API changed).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

The Channel add-flow now has a data-driven fork; Phase 02 records the change (dev log, catalog) and does not touch FEATURES (strategic §8 = no change).

---

## Rollback Plan

Revert the phase commit(s) - no data migration, no schema change, one new string key and two enum cases.
