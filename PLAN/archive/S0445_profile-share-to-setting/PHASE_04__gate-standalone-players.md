# Phase 04 - Gate the standalone players

**Strategic spec:** [`../S0445_profile-share-to-setting.md`](../S0445_profile-share-to-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Make the system-Share button obey the `system_share` flag in all five standalone hosts. Each host sets `btnShareCmd.isVisible = true` directly at button setup. Every host is `@AndroidEntryPoint`, injects `SettingsRepository`, and already observes `AppSettings` via `collectOnLifecycle(settingsRepository.getSettings())`. So each host can read the flag and drive button visibility reactively, without touching the shared standalone file-ops handler.

Standalone hosts (one show-point each):

1. Generic standalone player.
2. Photo/Video standalone.
3. Audio standalone.
4. Text standalone.
5. Document standalone.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] PIB-2 decision recorded (host-side gate chosen unless a reason emerged).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | Modified | ≤ 1000 |

---

## Steps

### Step 04.1 - Drive each standalone Share button from the flag

**Files:** all five standalone host activities (listed above).
**Depends on:** - start of phase

**Prompt for developer:**

> In each host, the Share button is wired in the button-setup block with `btnShareCmd.isVisible = true` (and a click listener that stays). Inject `IsShareTargetEnabledUseCase` (`@Inject lateinit var`) - it is `@Inject`-constructible, no entry point needed. Replace the hard `= true` with the flag:
>
> - Preferred (reactive): in the host's existing `collectOnLifecycle(settingsRepository.getSettings())` observer, set `binding.btnShareCmd.isVisible = isShareTargetEnabledUseCase("system_share", settings)`. Keep the click-listener wiring where it is (the listener may be attached once at setup; only visibility is reactive). If the host's setup runs before the first settings emission, set an initial `isVisible = false` at setup and let the observer flip it on - never leave it hard-`true`.
> - If a host has no settings observer touching these buttons, read settings once in the setup coroutine the host already uses (several read `settingsRepository.getSettings().first()` for other buttons) and gate there.
>
> Do the same edit in all five hosts. Keep each host's edit identical in shape so the audit is mechanical. Do not route this through the shared standalone file-ops handler (it has no settings access; widening it is heavier than the host-side gate).

**Verification:**

- `Grep` - `"system_share"` literal present in each of the five host files.
- `Grep` - `IsShareTargetEnabledUseCase` injected in each of the five host files.
- `Grep` - no remaining `btnShareCmd.isVisible = true` (literal `true`) in any of the five host files.
- Compiles via Step 04.2.

**Status:** `[ ]` not done

---

### Step 04.2 - Compile the standalone gating

**Files:** - (build only)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `.\a.ps1 fk`. With `system_share` OFF, none of the standalone hosts show their Share button; with it ON, all do. The send action itself is unchanged.

**Verification:**

- `.\a.ps1 fk` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `.\a.ps1 fk` exits 0.
- [ ] All five hosts gated identically; no literal `btnShareCmd.isVisible = true` remains across them.

---

## Handoff Notes to Next Phase

- All nine show-points (2 player + 2 browse + 5 standalone) are now gated. Phase 05 runs the completeness grep that proves no ungated show-point survived, then docs + catalog.

---

## Rollback Plan

Revert the five-file change. No data migration.
