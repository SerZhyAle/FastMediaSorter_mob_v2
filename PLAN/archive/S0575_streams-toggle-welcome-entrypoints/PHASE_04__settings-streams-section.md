# Phase 04 - Streams settings section on the Media tab

**Strategic spec:** [`../S0575_streams-toggle-welcome-entrypoints.md`](../S0575_streams-toggle-welcome-entrypoints.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

## Step Log

- 2026-06-21 - Steps 04.1-04.4 Verification PASS. `SettingsToggleRow` uses `str_subtitle` (not str_summary). `settings_streams_summary` pre-existed from S0565 ("Открыть интернет-аудио, видео и RTSP-трансляции.", verified clean UTF-8) - reused, only section+toggle added. Parity exit 0. `.\a.ps1 fc` -> BUILD SUCCESSFUL (FragmentSettingsStreamsBinding generated; MediaSettingsFragment gains CapabilityAvailability inject + media_streams section). Dev logs batched at Phase 07.

---

## Objective

Add a dedicated collapsible "Streams" section to the Media settings tab, placed after the "Other" (Translation/OCR) section, hosting the single `enableStreams` master toggle. The section is hidden where Streams is not offered.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`enableStreams` field + `isStreamsAvailable()`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_media_container.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/fragment_settings_streams.xml` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/StreamsSettingsFragment.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | n/a |

> Landscape parity: `fragment_settings_media_container.xml` has NO `res/layout-land/` counterpart (confirmed) - portrait-only edit is correct, no mirror required.

---

## Steps

### Step 04.1 - Add the Streams card to the media container layout

**Files:** `res/layout/fragment_settings_media_container.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `MaterialCardView` containing a `CollapsibleSectionHeader` with id `@+id/headerStreams` and a `FrameLayout` with id `@+id/containerStreams`, placed immediately AFTER the existing `containerOther` card. Mirror the structure of the surrounding section cards exactly (same attributes, no hardcoded `#hex` - use `?attr/`/`@color/`). The header title uses `@string/settings_streams_section`.

**Verification:**

- `Grep` - `@+id/headerStreams` and `@+id/containerStreams` each match once in `fragment_settings_media_container.xml`.
- `Grep` - no `="#` hardcoded color literal introduced in the added card.

**Status:** `[x]` done

---

### Step 04.2 - Create the Streams settings child layout

**Files:** `res/layout/fragment_settings_streams.xml` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create a minimal layout with a single toggle row (id `@+id/rowEnableStreams`) of the same widget type `OtherMediaSettingsFragment` declares for `rowEnableTranslation` (the Media-settings switch row). Use `?attr/`/`@string/`/`@dimen/` resources only - no hardcoded colors. Toggle label `@string/settings_streams_toggle`, summary `@string/settings_streams_summary`. Ensure the row is focusable/clickable for D-pad/TV.

**Verification:**

- `Glob` - `res/layout/fragment_settings_streams.xml` exists.
- `Grep` - `@+id/rowEnableStreams` matches once.

**Status:** `[x]` done

---

### Step 04.3 - Create `StreamsSettingsFragment` and register the section

**Files:** `ui/settings/fragments/StreamsSettingsFragment.kt` (New), `ui/settings/fragments/MediaSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `StreamsSettingsFragment` (Hilt `@AndroidEntryPoint`) inflating `fragment_settings_streams.xml`, obtaining the shared `SettingsViewModel` via `by activityViewModels()`. Bind `rowEnableStreams` to `AppSettings.enableStreams`: observe the settings via `collectOnLifecycle` and set the row checked state (not a bare `lifecycleScope.launch { collect {} }`); on toggle write `viewModel.updateSettings(viewModel.settings.value.copy(enableStreams = isChecked))`. Mirror how `OtherMediaSettingsFragment` binds `rowEnableTranslation` (same `viewModel`, same `bindSwitch`-style helper). In `MediaSettingsFragment`, add `@Inject lateinit var capabilityAvailability: CapabilityAvailability` (it is NOT currently injected - the fragment injects `mediaCapabilities`/`vrMediaSection`), then add a `MediaChildSection(binding.headerStreams, binding.containerStreams, "media__streams", false, "media_streams", if (capabilityAvailability.isStreamsAvailable()) ({ StreamsSettingsFragment() }) else null)` entry right AFTER the `media_other` entry - following the existing `mediaCapabilities.supportsX`-style conditional-factory convention (null factory hides the section).

**Verification:**

- `Glob` - `ui/settings/fragments/StreamsSettingsFragment.kt` exists.
- `Grep` - `class StreamsSettingsFragment` matches once.
- `Grep` - `by activityViewModels` and `collectOnLifecycle` both present in `StreamsSettingsFragment.kt`.
- `Grep` - `viewModel.settings.value.copy(enableStreams` present in `StreamsSettingsFragment.kt`.
- `Grep` - `"media_streams"` matches once in `MediaSettingsFragment.kt`.
- `Grep` - `capabilityAvailability` (the new injection) referenced in `MediaSettingsFragment.kt`.

**Status:** `[x]` done

---

### Step 04.4 - Add the Streams settings strings (trilingual)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 04.1, Step 04.2

**Prompt for developer:**

> Add `settings_streams_section`, `settings_streams_toggle`, `settings_streams_summary` in EN/RU/UK via `scripts/utils/set-android-string.ps1 -Action add` (one lockstep call per key). Copy follows `docs/COMMUNICATION_POLICY.md` §2 (settings label/summary) and passes the §6 tone checklist (Ё/ё in RU, concise, no marketing).

**Verification:**

- `Grep` - `settings_streams_section`, `settings_streams_toggle`, `settings_streams_summary` each present in all three `strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_streams"` - exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] On a `standard` build the Media tab shows a collapsible "Streams" section after "Other"; the toggle persists across app restart.
- [ ] On a `lite`/`photos` build the section is absent (factory null).
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 07 (new `StreamsSettingsFragment`).

---

## Handoff Notes to Next Phase

- The master toggle is now user-visible; the menu-gating phase reads the same `enableStreams` value.

---

## Rollback Plan

Revert the phase commit(s) - additive layout + fragment; no persistence/migration touched (the `enable_streams` key already existed from Phase 01).
