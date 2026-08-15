# Phase 03 - Manager + fragment wiring

**Strategic spec:** [`../S0435_settings-os-interaction-default-player.md`](../S0435_settings-os-interaction-default-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Wire the new default-player subgroup: a manager gates the four buttons by `MediaCapabilities` and dispatches each tap to the existing `DefaultPlayerHelper` registration flow; fix the group visibility so only the gesture subgroup (not the whole card) is hidden when the gesture capability is absent.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (binding fields exist and are non-null).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerSettingsManager.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 970 |

> `PlaybackSettingsFragment` is 917 LOC; keep the net delta small by delegating all button logic to the new manager (UI stays thin per CLAUDE.md Rule 3). Do not cross 1500 LOC.
> Registration entry points already exist in `DefaultPlayerHelper`: `showSetDefaultDialogForType(fragment, "image/*"|"audio/*"|"video/*")` and `showSetDefaultDocumentDialog(fragment)` for the docs button. Reuse them - do not re-implement registration.

---

## Steps

### Step 03.1 - Create `DefaultPlayerSettingsManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerSettingsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class DefaultPlayerSettingsManager` with a single `fun bind(fragment: Fragment, binding: FragmentSettingsPlaybackBinding, capabilities: MediaCapabilities)`. Behaviour:
> - Hide the whole `binding.layoutDefaultPlayerSubgroup` when `!capabilities.supportsDefaultPlayer`.
> - When supported, set per-button visibility: images by `capabilities.supportsImages`, audio by `capabilities.supportsAudio`, video by `capabilities.supportsVideo`, docs by `capabilities.supportsDocuments`. Use `androidx.core.view.isVisible`.
> - Set `binding.tvDefaultPlayerSettingsHint` text from `welcome_default_player_hint` via `HtmlCompat.fromHtml(..., FROM_HTML_MODE_LEGACY)` (mirrors the welcome page).
> - Wire clicks: images -> `DefaultPlayerHelper.showSetDefaultDialogForType(fragment, "image/*")`; audio -> `"audio/*"`; video -> `"video/*"`; docs -> `DefaultPlayerHelper.showSetDefaultDocumentDialog(fragment)`.
> - Do not auto-hide a button when already default (settings must keep all enabled buttons visible). No business logic beyond visibility + dispatch; no `BuildConfig` reads.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class DefaultPlayerSettingsManager` matches once.
- `Grep` - `fun bind(` present; `showSetDefaultDocumentDialog` and `showSetDefaultDialogForType` both referenced.
- `Grep -n "BuildConfig"` in the new file returns zero hits.
- `Grep -n "Log\.d\("` in the new file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification 5/5 PASS. Created DefaultPlayerSettingsManager (single onTypeClick dispatch; no BuildConfig/Log.d). post-change deferred to 03.4 (file edited again for the debug tag).

---

### Step 03.2 - Inject `MediaCapabilities` and bind the subgroup in the fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `@Inject lateinit var mediaCapabilities: MediaCapabilities` (import `com.sza.fastmediasorter.core.capability.MediaCapabilities`). Instantiate `DefaultPlayerSettingsManager` (field or local) and call `bind(this, binding, mediaCapabilities)` from `setupViews()`. The moved `rowFollowSystemRotation` wiring and accelerometer-gated `layoutFollowSystemRotation` visibility stay unchanged (ids preserved). The incoming-links row wiring stays unchanged (ids preserved). Leave the legacy `BuildConfig.SUPPORTS_DEFAULT_PLAYER` toggles (`rowPrimaryMediaPlayer` / `rowAcceptSharedFiles`) as-is - their Rule 14 migration is tracked in S0436.

**Verification:**

- `Grep` - `lateinit var mediaCapabilities: MediaCapabilities` present.
- `Grep` - `DefaultPlayerSettingsManager` referenced in the fragment.
- `Grep` - `.bind(this, binding, mediaCapabilities)` (or equivalent call) present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification 3/3 PASS. Injected MediaCapabilities (L52), manager field (L60), bind() in setupViews (L162).

---

### Step 03.3 - Gate the gesture subgroup instead of the whole card

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `setupSystemAppsSection()`, replace `binding.groupSystemApps.isVisible = false` (the whole-card hide when the gesture controller set is empty) with `binding.layoutScreenGesturesSubgroup.isVisible = false`. The card now also hosts the rotate checkbox, default-player, and incoming-links blocks, so it must stay visible on every flavor; only the noLegal-only gesture subgroup toggles. Keep the rest of the gesture wiring unchanged.

**Verification:**

- `Grep` - `binding.layoutScreenGesturesSubgroup.isVisible = false` present.
- `Grep` - `binding.groupSystemApps.isVisible = false` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification 2/2 PASS. Gate now hides layoutScreenGesturesSubgroup (L540) only; whole-card hide removed. Stale method comment updated.

---

### Step 03.4 - Add the BlockNeedUserTest debug tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerSettingsManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Insert exactly one `Timber.d("S0435: settings default-player button tapped, mime=<type>")` at the entry of the click-dispatch path (the single point where a button tap routes into `DefaultPlayerHelper`). This is the BlockNeedUserTest verification tag mandated by CLAUDE.md §2; it is removed when the ticket leaves BlockNeedUserTest. Do not add tags to any other flow.

**Verification:**

- `Grep` - `Timber.d("S0435:` matches exactly once across `app_v2/src` (in the manager).

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Tag inserted at onTypeClick, but the ticket-log gate (assert-no-ticket-logs) rejects an `Sxxxx:` log while the spec is not BlockNeedUserTest, and it scans all of src/main (blocks every post-change/build). Per project invariant, the probe was deferred: reverted during the phase, re-inserted at finalization atomically with the BlockNeedUserTest flip.
- 2026-06-15 - Re-inserted at finalization. Verification 1/1 PASS: one `Timber.d("S0435:` in src; `a.ps1 fk` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` in both touched files returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new manager class) - done in Phase 04.

---

## Handoff Notes to Next Phase

New public class `DefaultPlayerSettingsManager` needs a catalog entry (role + status). The default-player subgroup is now functional and gated by `MediaCapabilities`; the OS-interaction card is visible on all flavors. One S0435 debug tag is present (removed on `Verified`).

---

## Rollback Plan

Revert phase commit(s) - delete the manager and the fragment wiring. No data migration; layout from Phase 02 is inert without the wiring (buttons simply do nothing / subgroup shows).
