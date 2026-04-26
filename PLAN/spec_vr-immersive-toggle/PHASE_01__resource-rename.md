# Phase 01 — Resource Rename

**Strategic spec:** [`../spec_vr-immersive-toggle.md`](../spec_vr-immersive-toggle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — this is the foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-04-25
**Completed:** 2026-04-25

---

## Objective

Replace the "3D VR" label in the toggle button with "Immersive" in all three languages and remove the "3D" glyph from the enter-immersive icon.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Research §6.2 confirmed: `ic_vr_3d.xml` contains an explicit "3D" indicator path that must be replaced.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ~2800 lines (no growth) |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ~2800 lines (no growth) |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ~2800 lines (no growth) |
| `app_v2/src/main/res/drawable/ic_vr_3d.xml` | Modified | 17 lines (no growth) |

> No file exceeds 500 lines of change. No backup required.

---

## Steps

### Step 1.1 — Confirm visibility rule covers 2D video (no code change)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Read line 363 of `CommandPanelController.kt`. Confirm it reads:
> ```kotlin
> safeViews.btn3dVrCmd.isVisible = currentFile.type == MediaType.VIDEO
> ```
> This condition covers flat 2D MONO video — no code change needed. If the condition differs (e.g., it checks `isStereoscopic()` or `isSpherical()`), fix it to match the snippet above. Note the fix in the Blockers Log in INDEX.md and bump this step to `[x] done` only after the condition is correct.

**Verification:**

- `Grep` — pattern `btn3dVrCmd.isVisible = currentFile.type == MediaType.VIDEO` in `CommandPanelController.kt` returns exactly one match.
- `Grep` — pattern `isStereoscopic\(\)|isSpherical\(\)` within 5 lines of `btn3dVrCmd` in `CommandPanelController.kt` returns zero matches.

**Status:** `[x] done`

**Step Log:**

- 2026-04-25 — Verification 2/2 PASS. `btn3dVrCmd.isVisible = currentFile.type == MediaType.VIDEO` confirmed at line 363; no `isStereoscopic()/isSpherical()` near btn3dVrCmd. No code change required.

---

### Step 1.2 — Replace "3D" glyph in ic_vr_3d.xml with enter-arrow

**Files:** `app_v2/src/main/res/drawable/ic_vr_3d.xml`
**Depends on:** Step 1.1

**Prompt for developer:**

> Open `app_v2/src/main/res/drawable/ic_vr_3d.xml`. The file currently has three elements:
> 1. The headset outline path (keep unchanged).
> 2. Two `<path>` elements under the comment `<!-- "3D" text indicator at top -->` (remove both).
>
> Replace the two removed paths with a single enter-arrow path that mirrors the exit arrow in `ic_vr_exit.xml`. The exit arrow points right with a wall on the right; the enter arrow points left with a wall on the left:
>
> ```xml
>     <!-- Enter arrow at top-left -->
>     <path
>         android:fillColor="@android:color/white"
>         android:pathData="M10,3l-3,3 3,3V7h4V5h-4V3zM5,5H3v2h2V5z" />
> ```
>
> The final file must contain exactly two `<path>` elements: the headset outline and the enter arrow. Do not change `android:width`, `android:height`, `android:viewportWidth`, `android:viewportHeight`.

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable/ic_vr_3d.xml` exists.
- `Grep` — pattern `3D` (case-sensitive) in `ic_vr_3d.xml` returns zero matches (the comment and the glyph paths are gone).
- `Grep` — pattern `Enter arrow` in `ic_vr_3d.xml` returns one match (the replacement comment).
- `Grep` — pattern `<path` in `ic_vr_3d.xml` returns exactly 2 matches.

**Status:** `[x] done`

**Step Log:**

- 2026-04-25 — Verification 4/4 PASS. `3D` = 0 hits; `Enter arrow` comment = 1 hit; `android:pathData` count = 2 (headset + enter-arrow). Dev log recorded.

---

### Step 1.3 — Rename toggle strings in EN / RU / UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 1.2

**Prompt for developer:**

> Update the following two string keys in all three resource files. Do not change the key names — only the values.
>
> **EN** (`values/strings.xml`):
> ```xml
> <string name="vr_toggle_enter_description">Immersive view</string>
> <string name="vr_toggle_exit_description">Exit immersive</string>
> ```
>
> **RU** (`values-ru/strings.xml`):
> ```xml
> <string name="vr_toggle_enter_description">В иммерсив</string>
> <string name="vr_toggle_exit_description">Из иммерсива</string>
> ```
>
> **UK** (`values-uk/strings.xml`):
> ```xml
> <string name="vr_toggle_enter_description">В іммерсив</string>
> <string name="vr_toggle_exit_description">З іммерсиву</string>
> ```

**Verification:**

- `Grep` — pattern `Watch in 3D VR` in `values/strings.xml` returns zero matches.
- `Grep` — pattern `Exit 3D VR` in `values/strings.xml` returns zero matches.
- `Grep` — pattern `vr_toggle_enter_description.*Immersive view` in `values/strings.xml` returns one match.
- `Grep` — pattern `vr_toggle_exit_description.*Exit immersive` in `values/strings.xml` returns one match.
- `Grep` — pattern `vr_toggle_enter_description.*В иммерсив` in `values-ru/strings.xml` returns one match.
- `Grep` — pattern `vr_toggle_exit_description.*Из иммерсива` in `values-ru/strings.xml` returns one match.
- `Grep` — pattern `vr_toggle_enter_description.*В іммерсив` in `values-uk/strings.xml` returns one match.
- `Grep` — pattern `vr_toggle_exit_description.*З іммерсиву` in `values-uk/strings.xml` returns one match.

**Status:** `[x] done`

**Step Log:**

- 2026-04-25 — Verification 8/8 PASS. EN: "Immersive view"/"Exit immersive"; RU: "В иммерсив"/"Из иммерсива"; UK: "В іммерсив"/"З іммерсиву". Old "3D VR" strings gone. Dev log recorded for all 3 files.

---

## Phase Done Criteria

- [x] Every `Step 1.*` above is `[x] done`.
- [x] Project compiles — XML-only changes (strings + drawable); zero compilation risk; confirmed by owner.
- [x] `Grep` for `Watch in 3D VR` or `Exit 3D VR` across the entire `app_v2/src/` tree returns zero matches.
- [x] `Grep` for `3D` in `ic_vr_3d.xml` returns zero matches.
- [x] Dev log entry added for all four modified files:
  ```powershell
  .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "vr-immersive-toggle" "Rename vr_toggle_enter/exit_description to Immersive view / Exit immersive"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "vr-immersive-toggle" "RU: rename vr_toggle strings"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "vr-immersive-toggle" "UK: rename vr_toggle strings"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/drawable/ic_vr_3d.xml" "vr-immersive-toggle" "Replace 3D glyph with enter-arrow in ic_vr_3d"
  ```

---

## Handoff Notes to Next Phase

- `vr_toggle_enter_description` and `vr_toggle_exit_description` keys are unchanged; runtime binding in `VrToggleButtonManager.updateState()` continues to work without modification.
- Icon `ic_vr_3d` now shows headset + enter-arrow (left-pointing), `ic_vr_exit` shows headset + exit-arrow (right-pointing) — visually symmetric, distinguishable by arrow direction alone.
- Button visibility logic in `CommandPanelController` was confirmed correct (covers all video types) — no code change was made.

---

## Rollback Plan

Revert the four modified resource files. No data migration or user-facing state changed.
