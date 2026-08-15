# Phase 03 - Capture wiring

**Strategic spec:** [`../S0468_screenshot-clipboard.md`](../S0468_screenshot-clipboard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

When `copyScreenshotToClipboard` is on, copy each captured screenshot to the clipboard from the live bitmap, independent of the post-capture action and the save destination, and confirm to the user.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`AppSettings.copyScreenshotToClipboard`).
- [ ] Phase 02 ✅ Done (`ImageClipboardWriter`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt` | Modified | ≤ 380 |

> Flavor placement: `ScreenCaptureService` lives in the `screenCapture` source set, mounted only into the `noLegal` flavor (per `app_v2/build.gradle.kts`). The clipboard option therefore activates only where gesture capture itself runs. `ImageClipboardWriter` (src/main) is available to this source set. No `BuildConfig.IS_*` guard is introduced.

---

## Steps

### Step 03.1 - Add the clipboard confirmation string (trilingual)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `screen_capture_copied_to_clipboard` across EN/RU/UK in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key screen_capture_copied_to_clipboard -En "Copied to clipboard" -Ru "<ru>" -Uk "<uk>"`. RU/UK use `ё`/`Ё` where correct. The confirmation must pass `docs/COMMUNICATION_POLICY.md` §2 message formula and the §6 tone checklist.

**Verification:**

- `Grep` - `screen_capture_copied_to_clipboard` matches in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screen_capture_copied_to_clipboard"` - exit 0.
- String passes COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification PASS: key present EN/RU/UK, parity check exit 0. Files: values/values-ru/values-uk strings.xml (+1 key each). Cyrillic verified via Read (no mojibake).

---

### Step 03.2 - Copy to clipboard in the capture flow

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt`
**Depends on:** Step 03.1, Phase 01, Phase 02

**Prompt for developer:**

> Inject `@Inject lateinit var imageClipboardWriter: Lazy<ImageClipboardWriter>`. In `processCapture`, the `settings` object is already read. When `settings.copyScreenshotToClipboard` is true, call `imageClipboardWriter.get().copyBitmap(bitmap)` BEFORE `saveScreenshotUseCase.get().invoke(bitmap, target)` (the use case recycles the bitmap in its `finally`, per research 03). Guard the clipboard call in its own `try`/`catch` so a clipboard failure never aborts the save, and vice versa. On clipboard success, show `Toast` `R.string.screen_capture_copied_to_clipboard` (a short confirmation, distinct from the existing "Saved to.." toast). Do not couple the clipboard result to `SaveResult`. Keep capability/behavior unchanged when the flag is off.

**Verification:**

- `Grep` - `imageClipboardWriter` matches at least twice in `ScreenCaptureService.kt` (field + call).
- `Grep` - `copyScreenshotToClipboard` matches once in `ScreenCaptureService.kt`.
- `Grep` - `screen_capture_copied_to_clipboard` matches once in `ScreenCaptureService.kt`.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS (imageClipboardWriter ×2, copyScreenshotToClipboard ×1, confirm string ×1, Log.d ×0). Files: ScreenCaptureService.kt (+9 LOC, screenCapture/noLegal source set). copyBitmap runs before the save use case recycles the bitmap. Build validation deferred to consolidated noLegal build.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles for the noLegal flavor - run `/build` (noLegal debug; do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Capture-side behavior is complete. Phase 04 exposes the toggle and authors the confirmation/label strings.

---

## Rollback Plan

Revert phase commit(s) - the clipboard branch is additive and gated by a default-off flag; capture behavior reverts to pre-S0468.
