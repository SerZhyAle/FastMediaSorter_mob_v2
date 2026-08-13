# Phase 02 - Settings video-background download gate

**Strategic spec:** [`../S0407_audio-player-extra-visualizations.md`](../S0407_audio-player-extra-visualizations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-13
**Completed:** 2026-06-13

---

## Objective

Selecting the video-background empty-state mode in audio settings checks whether the visualization set is installed and offers to download it when not; refusal auto-reverts the selection to the previously persisted mode.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Delivery prompt infrastructure present (verified in `/spec-tech` step 2): `DeliveryEnableInterceptor`, `DeliveryPromptDialogFragment` already handle `DeliverableSet.AUDIO_VISUALIZATIONS`; strings `ext_audio_viz_title`/`_desc` exist in EN/RU/UK.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt` | Modified | ≤ 520 |

> No new strings, no new layout - reuses the existing delivery prompt dialog and its localized titles. No landscape layout involved.

---

## Steps

### Step 02.1 - Obtain the delivery interceptor in the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The fragment is already `@AndroidEntryPoint`. Add a Hilt field injection `@Inject lateinit var deliveryEnableInterceptor: com.sza.fastmediasorter.ui.delivery.DeliveryEnableInterceptor`. Do not introduce a new Hilt module - `DeliveryEnableInterceptor` is already `@Singleton @Inject`-constructed.

**Verification:**

- `Grep` - `lateinit var deliveryEnableInterceptor` matches once in the file.
- `Grep` - `import ...DeliveryEnableInterceptor` or fully-qualified reference present.
- `/build` compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-06-13 - Verification PASS (deliveryEnableInterceptor field ×1, FQ type reference). Compile gated with Phase Done Criteria (a.ps1 fk). Files: AudioSettingsFragment.kt.

---

### Step 02.2 - Gate the VISUALIZATION selection and auto-revert on refusal

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/AudioSettingsFragment.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `actvAudioEmptyStateMode.setOnItemClickListener`, keep the `isUpdatingFromSettings` guard. When `selectedKey == MODE_VISUALIZATION`, do NOT persist immediately; instead route through `deliveryEnableInterceptor.requireInstalled(this, DeliverableSet.AUDIO_VISUALIZATIONS, onReady = { viewModel.updateSettings(current.copy(audioEmptyStateMode = MODE_VISUALIZATION)) }, onUnavailable = { ... })`. In `onUnavailable` (user refused or download failed): do not persist VISUALIZATION, and revert the dropdown text to the currently persisted mode by reading `viewModel.settings.value.audioEmptyStateMode` and re-setting `actvAudioEmptyStateMode` text to its label (reuse the existing `emptyStateModeKeys` index + label list, normalizing legacy `GIF_LOOP` to `VISUALIZATION` as `observeData` already does). For every other `selectedKey`, persist directly as before. Per strategic §6.10-6.11: refusal must leave a usable non-video mode selected, never crash, never toast.

**Verification:**

- `Grep` - `requireInstalled(` present in the file with `DeliverableSet.AUDIO_VISUALIZATIONS`.
- `Grep` - `onUnavailable` (or the lambda reverting the dropdown) references `audioEmptyStateMode`.
- `Grep` - `Log\.d\(` returns zero hits in the file (Timber-only).
- `/build` compiles.
- Strings pass COMMUNICATION_POLICY §6 checklist - N/A: no new user-visible strings added (reuses existing localized delivery prompt).

**Status:** `[x] done`

**Step Log:**

- 2026-06-13 - Verification PASS (requireInstalled + DeliverableSet.AUDIO_VISUALIZATIONS present; onUnavailable -> revertEmptyStateModeSelection reads audioEmptyStateMode; Log.d 0; no new strings). Files: AudioSettingsFragment.kt.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (shared with Phase 01, Kotlin-only).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `AudioSettingsFragment.kt` (post-change.ps1).
- [x] Catalog regenerated via post-change.ps1; neuroslop + ticket-log gates PASS.

---

## Handoff Notes to Next Phase

Selecting the video background now offers the download and auto-reverts on refusal. Combined with Phase 01, a refused/absent set yields a usable non-video mode and never a crash. Phase 03 (asset registration) is the externally-blocked workstream.

---

## Rollback Plan

Revert the phase commit - selection reverts to the prior direct-persist behaviour; no persisted state or migration changed.
