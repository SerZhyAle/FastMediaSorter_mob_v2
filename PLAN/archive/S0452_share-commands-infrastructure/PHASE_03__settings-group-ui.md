# Phase 03 - Settings group UI

**Strategic spec:** [`../S0452_share-commands-infrastructure.md`](../S0452_share-commands-infrastructure.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Add a collapsible group "Команды отправить файл в.." on the Player settings tab that renders one toggle per registered `ShareTarget`, reflecting effective-enabled state and writing user toggles into `AppSettings.enabledShareTargets`.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | - |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 500 |

> **Landscape parity:** `res/layout-land/fragment_settings_playback.xml` exists - it is edited in lockstep with the portrait variant in Step 03.1.

---

## Steps

### Step 03.1 - Add group header + container to both layouts

**Files:** `res/layout/fragment_settings_playback.xml`, `res/layout-land/fragment_settings_playback.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `CollapsibleSectionHeader` (id e.g. `headerSendCommands`) and a vertical container (id e.g. `containerSendCommands`) following the existing section markup (sorting/file-ops/player-ui/touch-zones). The container holds dynamically-added toggle rows (added in code), so it can start empty. Use `?attr/`/`@color/` tokens only - no hardcoded `="#hex"`. Apply the identical addition to both portrait and landscape files.

**Verification:**

- `Grep` - `headerSendCommands` present in BOTH layout files.
- `Grep` - `containerSendCommands` present in BOTH layout files.
- `Grep` - no new `="#` hex literal added (search the diff region).

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - PASS. Added `headerSendCommands` + `containerSendCommands` MaterialCardView group to both `layout/` and `layout-land/fragment_settings_playback.xml`, mirroring the touch-zones section. No hex literals; `?attr/`/`@dimen/` tokens only.

---

### Step 03.2 - Add the group title string (trilingual)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one string key `settings_send_commands_group` via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key settings_send_commands_group -En "Send file to.." -Ru "Команды отправить файл в.." -Uk "Команди надіслати файл у.."`. Check the label against `docs/COMMUNICATION_POLICY.md` §2 (label formula) and §6 (tone checklist).

**Verification:**

- `Grep` - `settings_send_commands_group` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix settings_send_commands_group` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - PASS. Added `settings_send_commands_group` (+ `settings_send_command_unavailable` for the disabled-row subtitle) across EN/RU/UK via UTF-8 script (avoids bash->pwsh Cyrillic mojibake). check_strings_localized exit 0; Grep-verified Cyrillic.

---

### Step 03.3 - Register the new ExpandableSection

**Files:** `ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `KEY_SEND_COMMANDS_EXPANDED = "section_send_commands_expanded"` constant. Append an `ExpandableSection(binding.headerSendCommands, binding.containerSendCommands, KEY_SEND_COMMANDS_EXPANDED, false)` entry in `setupExpandableSections()` and include its key in the prefs read map. Default collapsed (`false`), mirroring existing sections.

**Verification:**

- `Grep` - `KEY_SEND_COMMANDS_EXPANDED` matches in `PlaybackSettingsFragment.kt`.
- `Grep` - `headerSendCommands` and `containerSendCommands` referenced in the fragment.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - PASS. Added `KEY_SEND_COMMANDS_EXPANDED`, the 5th `ExpandableSection`, and its prefs-read entry. Default collapsed.

---

### Step 03.4 - Render one toggle per registered target

**Files:** `ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 03.3, Phase 02 (`IsShareTargetEnabledUseCase`)

**Prompt for developer:**

> In the fragment, iterate `shareTargetRegistry.all()` and add one `SettingsToggleRow` (or the established toggle widget) to `containerSendCommands` per target, labelled by `target.titleRes`. Initial checked state = `IsShareTargetEnabledUseCase(target.id)`. On toggle, update `AppSettings.enabledShareTargets` (add id when ON, remove when OFF) via `viewModel.updateSettings(current.copy(enabledShareTargets = ...))`. Disable + visually mark (non-color) a row whose target is unavailable per `ShareTargetAvailabilityResolver`. Inject the registry/resolver/use-case via Hilt (fragment is already `@AndroidEntryPoint`). Collect settings via `collectOnLifecycle`, never a bare `lifecycleScope.launch { collect }`. With an empty registry this renders nothing - that is correct until Phase 04 seeds Telegram.

**Verification:**

- `Grep` - `shareTargetRegistry` (or injected registry field) referenced in `PlaybackSettingsFragment.kt`.
- `Grep` - `enabledShareTargets` written in the toggle handler.
- `Grep` - `collectOnLifecycle` used for the settings flow; `Grep` for bare `lifecycleScope.launch` around a `.collect` returns zero new hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - PASS. `setupSendCommandsGroup()` renders one `SettingsToggleRow` per `registry.all()` target: title from `titleRes`, initial state from `IsShareTargetEnabledUseCase`, disabled + "Not installed" subtitle when `!isAvailable`. Toggle updates enabled/disabled sets via `viewModel.updateSettings`. `observeData` refreshes rows (existing `collectOnLifecycle`). Empty registry renders nothing (correct until Phase 04). assembleStandardDebug PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` PASS (2026-06-16, brzjwmsev).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `check_strings_localized.ps1 -KeyPrefix settings_send_command` exits 0.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- The group renders from the registry; seeding a target in Phase 04 makes a toggle appear automatically.

---

## Rollback Plan

Revert phase commit(s) - additive UI section + one string key; no data migration.
