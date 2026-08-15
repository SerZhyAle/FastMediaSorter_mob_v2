# Phase 05 - Manifest routing + default-player toggle

**Strategic spec:** [`../S0380_split-standalone-player.md`](../S0380_split-standalone-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Cut over external-intent routing from the single `StandalonePlayerActivity` to the new specialized activities + dispatcher, and update the default-player toggle so it enables/disables the new components in lockstep with the aliases. This is the only behavior-affecting phase.

---

## Prerequisites

- [ ] Phase 03 ✅ Done, Phase 04 ✅ Done.
- [ ] `src/main/AndroidManifest.xml` backed up to `temp/` before edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerManager.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/DefaultPlayerStateBootstrapper.kt` | Modified | ≤ 300 |

---

## Steps

### Step 05.1 - Register activities and re-point routing

**Files:** `src/main/AndroidManifest.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up the manifest first. Declare the 4 specialized activities + the dispatcher. Re-target the existing VIEW aliases (`StandaloneAudioPlayer`/`VideoPlayer`/`ImagePlayer`/`DocsPlayer`) to the matching specialized activity (audio→Audio, video+image→PhotoVideo, docs→Document, with a text-specific path to Text). Register the dispatcher for typeless / `*/*` VIEW intents. Keep all aliases `android:enabled="false"` (runtime-toggled). Preserve `supportsPictureInPicture`, themes, `configChanges`, `exported` exactly as the originals.

**Verification:**

- `Grep` - manifest contains `PhotoVideoStandaloneActivity`, `AudioStandaloneActivity`, `DocumentStandaloneActivity`, `TextStandaloneActivity`, `StandalonePlayerDispatcherActivity`.
- `Grep` - each VIEW alias `targetActivity` updated to a specialized activity (no longer all `StandalonePlayerActivity`).
- `Grep` - dispatcher has an intent-filter with `*/*` or no `mimeType` data.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS. Manifest backed up to `temp/AndroidManifest.xml.bak_*`. Declared the 4 specialized activities (`PhotoVideoStandaloneActivity` with `supportsPictureInPicture`, `AudioStandaloneActivity`, `DocumentStandaloneActivity`, `TextStandaloneActivity` - all `exported=false`, FullScreen theme) + `StandalonePlayerDispatcherActivity` (`exported=false`, Transparent theme). Re-targeted all VIEW aliases off `StandalonePlayerActivity`: Audio→Audio, Video→PhotoVideo, Image→PhotoVideo, Docs→Document (removed `text/plain` from Docs). Added `.StandaloneTextPlayer`→Text and `.StandaloneTypelessPlayer`→Dispatcher (scheme-only filter, no mimeType → matches typeless intents). All aliases kept `android:enabled="false"`; `supportsPictureInPicture`/themes/`configChanges`/`exported` preserved on the retained `StandalonePlayerActivity`. Removed the Text-lane interim always-on `text/plain` filter (text now routes via the toggled alias). Build: standardDebug `BUILD SUCCESSFUL in 35s`. Files: src/main/AndroidManifest.xml.

---

### Step 05.2 - Update default-player toggle for new components

**Files:** `DefaultPlayerManager.kt`, `DefaultPlayerHelper.kt`, `DefaultPlayerStateBootstrapper.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Update the component-enable/disable logic so the "default player" feature toggles the new aliases/activities consistently. No alias may be left enabled while its target is gone. The toggle set must match the manifest alias names exactly. Preserve the existing user-facing default-player UX and strings.

**Verification:**

- `Grep` - the three files reference the current alias component names; no reference to a removed/renamed target remains (`expected: 0 stale | actual: <record>`).
- `Grep -n "Log\.d\("` returns zero hits in touched files.
- Build: `/build` standardDebug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS. `DefaultPlayerManager.viewAliasesForFlavor()` extended with `.StandaloneTextPlayer` + `.StandaloneTypelessPlayer` (both unconditional - text matches the unconditional text share-receiver; the dispatcher is the catch-all that forwards to whichever specialized activity fits at runtime). The 4 existing VIEW alias suffixes (Audio/Video/Image/Docs) are unchanged - re-targeting kept their names, so no stale component reference remains (`expected: 0 stale | actual: 0`). `DefaultPlayerHelper` + `DefaultPlayerStateBootstrapper` delegate to the manager and hardcode no alias names (grep: 0), so no edit needed there. Log.d in touched files (`expected: 0 | actual: 0`); Timber only. Build: standardDebug `BUILD SUCCESSFUL in 35s`. Files: ui/settings/helpers/DefaultPlayerManager.kt.

---

### Step 05.3 - Resolve system-selector overlap (residual §6 note)

**Files:** `src/main/AndroidManifest.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Verify no two enabled components register overlapping exact MIME types that would surface a duplicate "Open with" chooser. Where image and video share one activity, ensure both alias filters target it without conflicting with the dispatcher's generic filter. Document the resolved behavior in the phase handoff.

**Verification:**

- `Grep` - no duplicate exact `mimeType` across two distinct enabled targets for the same family.
- Build: `/build` standardDebug passes (`expected: BUILD SUCCESSFUL | actual: <record>`).
- Debug tag for device test added per CLAUDE.md when the ticket enters `BlockNeedUserTest`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS. Programmatic scan of all VIEW `*Player` aliases: no exact `mimeType` maps to two different `targetActivity` values (`expected: 0 dups | actual: 0`). Image+Video deliberately share `PhotoVideoStandaloneActivity` but via disjoint `image/*` vs `video/*` filters (no concrete-type collision). Resolved the one real overlap found during this step: the Text alias's broad `text/*` would have collided with the Docs alias's `text/rtf` (RTF is a document) - narrowed the Text VIEW alias to `text/plain` only, leaving RTF with documents. Dispatcher overlap resolved structurally: dropped the `*/*` mimeType filter (it would have duplicated every concrete per-type alias in the chooser) and kept only a scheme-only filter, which uniquely matches typeless intents (per-type aliases require a declared mimeType, so they never match a null-type intent) - zero chooser overlap. Generic `*/*` intents still reach the app via the per-type family wildcards. Build: standardDebug `BUILD SUCCESSFUL in 35s`. Debug verification tags: deferred to the finalization step (inserted as the last code edits before the final pre-`BlockNeedUserTest` build, per CLAUDE.md), not here. Files: src/main/AndroidManifest.xml. Real-world `*/*` chooser behavior to confirm in the device-test round.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - run `/build` (standardDebug).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Routing now points to specialized activities; the dispatcher covers typeless/`*/*`. Default-player toggle is in sync. Phase 06 verifies all flavors build and that flavor-specific player hosts are placed correctly.

---

## Rollback Plan

Revert the manifest + toggle commits to restore single-activity routing. Backup of the manifest is in `temp/`. Because aliases stay `enabled="false"` by default, a botched cutover does not break the default launch path until the user opts into "default player".
