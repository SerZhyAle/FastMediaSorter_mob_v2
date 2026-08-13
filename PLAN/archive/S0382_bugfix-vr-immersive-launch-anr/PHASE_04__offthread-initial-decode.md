# Phase 04 - Off-Main-Thread Initial Decode

**Strategic spec:** [`../S0382_bugfix-vr-immersive-launch-anr.md`](../S0382_bugfix-vr-immersive-launch-anr.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none functionally - independent of transport phases
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Move the immersive host's initial frame decode off the main thread and show an explicit loading state, so the focus transition no longer blocks the main thread. Supersedes the prior S0290 Phase 11 assumption (strategic ADR-1).

---

## Prerequisites

- [ ] **BLOCKER** Strategic §6.2 Resolved - loading-state presentation decided via `/ui-clarify`.
- [ ] **BLOCKER** Strategic §6.3 Resolved - texture-buffer readiness ordering confirmed (async decode cannot reach first render with an empty/black buffer).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 1200 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +1 key |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +1 key |

> `DiagnosticXrActivity` is large; back it up to `temp/` before editing if it remains >500 lines. Exact loading-surface files depend on the `/ui-clarify` outcome and are appended when the blocker resolves.

---

## Steps

### Step 04.1 - Replace runBlocking initial decode with a background decode

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `decodeBundledAsset` and `decodeImageToActivityBytes`, remove the `runBlocking { decode… }` calls and perform the decode on `Dispatchers.IO` within the Activity's lifecycle scope. The first render must not start until the texture buffer is populated - gate the render-thread handoff on decode completion per the §6.3 research outcome. Remove the stale S0290 Phase 11 WHY-comment claiming the main-thread jank is invisible; replace it with a one-line note that decode runs off-main-thread (strategic ADR-1).

**Verification:**

- `Grep -n "runBlocking"` on the file returns zero hits in `decodeBundledAsset` / `decodeImageToActivityBytes`.
- `Grep -n "invisible to the user"` on the file returns zero hits (stale comment removed).

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. `runBlocking` expected: 0 | actual: 0; `invisible to the user` expected: 0 | actual: 0. `decodeBundledAsset`/`decodeImageToActivityBytes` now `suspend` with decode+RGBA copy on `Dispatchers.IO`; `proceedWithInitialization` launches `prepareInitialFrame()` and gates `maybeStartRenderThread` on new `initialDecodeComplete` flag (re-triggered from the decode-completion callback). `runBlocking` import removed. Files: DiagnosticXrActivity.kt. Dev log batched at phase end.

---

### Step 04.2 - Add the loading-state surface

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` (+ surface files per `/ui-clarify`)
**Depends on:** Step 04.1

**Prompt for developer:**

> Show the loading state decided in `/ui-clarify` (§6.2) while the decode runs, and dismiss it on the first rendered frame. Honor system-bar / cutout insets on the 2D phase per CLAUDE.md Rule 18, and input coverage per Rule 17 if the surface carries any control.

**Verification:**

- `Grep` - the loading-state view/flag toggle is present and is cleared on first-frame.
- Static insets check: the loading surface applies system-bar inset padding (Rule 18) - `Grep` for the inset application call.

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. Overlay toggle present (`loadingOverlay = overlay` set, `loadingOverlay = null` cleared via `dismissInitialLoadingOverlay()` in `onRenderThreadSessionReady`); insets `applySystemBarInsetPadding()` applied on the indicator (Rule 18). `setContentView` now wraps the SurfaceView in a FrameLayout root with a 2D ProgressBar + `R.string.vr_immersive_preparing` label (added in 04.3). Files: DiagnosticXrActivity.kt. Dev log batched at phase end.

---

### Step 04.3 - Loading string, trilingual

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add one loading string key across EN/RU/UK in lockstep via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "…" -Ru "…" -Uk "…"`. Text must pass `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist); RU/UK use `..` and `ё`/`Ё` where applicable.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<key>"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. `check_strings_localized.ps1 -KeyPrefix vr_immersive_preparing` expected: exit 0 | actual: exit 0 (EN/RU/UK all OK). Key `vr_immersive_preparing` added to `strings_vr.xml` in lockstep via `set-android-string.ps1 -Action add` (EN "Preparing immersive mode..", RU "Готовлю immersive-режим..", UK "Готую immersive-режим.."); Cyrillic verified by Grep (no mojibake), `..` per style rule, §6 tone PASS (progress status, no raw error / no "are you sure"). Files: strings_vr.xml ×3. Dev log batched at phase end.

---

### Step 04.4 - Build gate + device probe tag

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Steps 04.1, 04.2, 04.3

**Prompt for developer:**

> As the final code edit before the build, insert one `Timber.d("S0382: <immersive entry decode off main thread>")` at the immersive-entry flow entry (per CLAUDE.md Debug Verification Tags - the ticket enters `BlockNeedUserTest`). Then build `noLegal` debug.

**Verification:**

- `Grep -n "Timber.d(\"S0382:"` shows exactly one tag at the immersive-entry flow entry.
- `/build` (noLegal debug) succeeds - `expected: BUILD SUCCESSFUL | actual: <record>`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. One probe tag at the immersive-entry flow entry: `Grep "Timber.d(\"S0382:"` expected: 1 | actual: 1 (DiagnosticXrActivity.kt:323). `.\a.ps1 nd` (noLegal debug) expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL in 1m 7s (temp/s0382_build_nd.log). Single build validates Phase 04 code + tag. Files: DiagnosticXrActivity.kt. Dev log batched at phase end.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 nd` (noLegal debug) expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL in 1m 7s.
- [x] `Grep` for `TODO(phase-04)` returns zero hits - expected: 0 | actual: 0.
- [x] Dev log entry added for every file in "Files Touched" - via the finalization `close-and-log` batch.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - via the finalization `close-and-log` batch (`-CatalogModule app_v2`).

---

## Handoff Notes to Next Phase

Initial decode runs off the main thread behind a loading state; strategic ADR-1 assumption reversal is realized in code. On-device verification on Quest 3 closes strategic §11 criteria 1 and 4.

---

## Rollback Plan

Revert phase commit(s). No data migration. Reverting restores the synchronous `runBlocking` decode and removes the loading string (also revert the trilingual string add).
