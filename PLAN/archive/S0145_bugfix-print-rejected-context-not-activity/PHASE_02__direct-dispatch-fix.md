# Phase 02 — Direct Dispatch Fix

**Strategic spec:** [`../S0145_bugfix-print-rejected-context-not-activity.md`](../S0145_bugfix-print-rejected-context-not-activity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped — field log 2026-05-10 confirmed `ContextThemeWrapper` wrap is insufficient (Samsung API 36 `SystemServiceRegistry` binds `ContextThemeWrapper` as `PrintManager.mContext` regardless of wrapping). Direct dispatch fix is not viable without a dedicated transparent Activity — product owner accepted Phase 03 fallback as the production solution. No code change required.
**Depends on:** Phase 01 ✅ Done; INDEX blockers §6.1/§6.2 ✅ Resolved
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Make `PrintManager.print()` / `PrintHelper.printBitmap()` accept the call on Samsung One UI (Android 13+, confirmed broken on Galaxy S25 / Android 16 / API 36). Root cause (§6.1 resolved): `Activity.getSystemService(PRINT_SERVICE)` on Samsung API 36 delegates via `ContextWrapper` chain, causing `SystemServiceRegistry` to bind `ContextThemeWrapper` as `PrintManager.mContext`; `PrintManager.print()` checks `mContext instanceof Activity` (line 519) → false → `IllegalStateException`. `ContextThemeWrapper` wrapping (§6.2 resolved: ruled out) does not help. Two viable approaches: (A) intercept `getSystemService` to force `PrintManager` to receive raw Activity as context; (B) dedicated transparent print Activity that calls `getSystemService` and `print()` directly as the launch-context Activity. Choose (A) first; fall back to (B) if (A) does not pass on-device testing.

---

## Prerequisites

- [x] Phase 01 ✅ Done.
- [x] Field log from Samsung One UI device captured — `fastmediasorter_20260510_201249.log` (Galaxy S25, Android 16 / API 36).
- [x] Strategic §6.1 Resolved — root cause is `ContextThemeWrapper` bound as `mContext` in `PrintManager` on Samsung API 36.
- [x] Strategic §6.2 Resolved — `ContextThemeWrapper` wrap ruled out.
- [x] Phase steps re-authored for approach (A): wrap `activity` in a minimal `ContextWrapper` subclass that overrides `getSystemService(PRINT_SERVICE)` to return a `PrintManager` instantiated via reflection with the raw Activity as context. If approach (A) fails on-device, replace with approach (B) (transparent print Activity) before clearing `⛔ Blocked` again.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt` | Modified | ≤ 420 |

> **Approach (B) fallback:** if approach (A) fails on-device, replace this table with: new transparent `PrintDispatchActivity` (`New`, ≤ 250 LOC), its `AndroidManifest.xml` entry, transparent theme in `res/values/themes.xml`. Landscape parity note: the Activity is invisible, so no `layout-land` counterpart applies. Update the steps accordingly and flip Phase 02 status back to `⬜ Not started` before re-implementing.

---

## Steps

> Approach (A): introduce a minimal `ActivityPrintContextWrapper` that overrides `getSystemService(PRINT_SERVICE)` to instantiate `PrintManager` via a public constructor overload (API 34+) or via the `android.print.IPrintManager` service path that passes the raw Activity as `outerContext`. The rest of the dispatch path (PDF, image, text) continues to use `activity` directly — only the `PrintManager` acquisition changes.

### Step 02.1 — Introduce `ActivityPrintContextWrapper` to force raw-Activity `mContext` in `PrintManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`
**Depends on:** prerequisites cleared

**Prompt for developer:**

> In `DocumentPrintManager`, replace the `getSystemService(Context.PRINT_SERVICE)` call in `dispatchPrint` with a targeted workaround that ensures the returned `PrintManager` holds the raw `PlayerActivity` (not a `ContextThemeWrapper`) as `mContext`. Preferred approach: subclass `android.content.ContextWrapper` (private inner class `ActivityPrintContextWrapper`) that wraps `activity` and overrides `getSystemService(String)` to return — for `PRINT_SERVICE` only — a `PrintManager` obtained by calling `super.getSystemService(PRINT_SERVICE)` **on the raw `activity`** reference, bypassing the `ContextWrapper` delegation chain. Use `ActivityPrintContextWrapper(activity).getSystemService(Context.PRINT_SERVICE) as? PrintManager` in `dispatchPrint`. Keep `runOnUiThread { … }`, the `try/catch` block (ADR-1), and the existing error branches unchanged. Do not touch `printImage` or `printText` — only the `PrintManager` acquisition in `dispatchPrint` changes.

**Verification:**

- `Grep` — `ActivityPrintContextWrapper` (or equivalent name) matches in `DocumentPrintManager.kt`.
- `Grep` — `getSystemService(Context.PRINT_SERVICE)` still present in `dispatchPrint` (the acquisition is not removed, just redirected).
- `Grep` — `runOnUiThread` still present in `DocumentPrintManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `DocumentPrintManager.kt`.

**Status:** `[ ]` not done

---

### Step 02.2 — Ensure `printImage` and `printText` are unaffected

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Verify `PrintHelper(activity)` in `printImage` still passes the raw Activity (it does not go through `getSystemService`, so no change is needed). In `printText`, confirm the `WebView` is still constructed with `activity` and that `dispatchPrint` (called from `onPageFinished`) now picks up the fixed `PrintManager`. No source change expected — this is a confirmation step; add a comment if the path is non-obvious.

**Verification:**

- `Grep` — `PrintHelper(activity)` matches in `DocumentPrintManager.kt` (unchanged).
- `Grep` — `WebView(activity)` or equivalent matches in `printText` region of `DocumentPrintManager.kt`.

**Status:** `[ ]` not done

---

### Step 02.3 — Re-insert the S0145 flow-entry debug tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> At the entry of `printCurrentFile`, add `Timber.d("S0145: printCurrentFile type=<type> path=<path>")`. One tag, filled with the actual media type and path. This tag is the on-device verification probe for the next `BlockNeedUserTest` round; it is removed when the ticket reaches `Verified`.

**Verification:**

- `Grep` — `Timber.d("S0145: printCurrentFile` matches exactly once in `DocumentPrintManager.kt`.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] No public API change — verify no new class visible outside `helpers/`.
- [ ] Spec status advanced to `BlockNeedUserTest` and `Timber.d("S0145: printCurrentFile")` re-inserted (Step 02.3).

---

## Handoff Notes to Next Phase

Direct dispatch now obtains `PrintManager` with raw Activity as `mContext`. Phase 03's system-print fallback still applies as the safety net; the `IllegalStateException` catch in `dispatchPrint` remains (ADR-1). On-device test on Samsung Galaxy S25 (Android 16 / API 36) must confirm no `IllegalStateException` in the log before moving to Phase 05.

---

## Rollback Plan

Revert the phase commit — `ActivityPrintContextWrapper` is removed, `dispatchPrint` reverts to `activity.getSystemService(PRINT_SERVICE)`. Phase 03 fallback still catches the `IllegalStateException`, so no crash regression. If approach (B) was chosen instead, also revert the transparent print Activity, its manifest entry, and theme.
