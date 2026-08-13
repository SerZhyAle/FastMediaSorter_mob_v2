# Phase 01 — Print Environment Probe

**Strategic spec:** [`../S0145_bugfix-print-rejected-context-not-activity.md`](../S0145_bugfix-print-rejected-context-not-activity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02 (its research is resolved from a log produced by this build)
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Make the print path emit, at WARN level, a complete snapshot of the print environment and the player Activity's base-context chain on every print invocation and every print failure, so a single field log from an affected Samsung device resolves strategic §6.1 and §6.2 without a live debugging session. No behaviour change.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Confirm whether the uncommitted working-tree changes to `DocumentPrintManager.kt` (the `printEnvDiagnostics()` helper and the `"print invoked — …"` WARN trace) are present; if so, extend them rather than re-adding.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt` | Modified | ≤ 420 |

> File is ~355 lines; projected < 420 after change — under the 500-line backup threshold, no backup step required.

---

## Steps

### Step 01.1 — Add a base-context-chain inspector to the print diagnostics

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `DocumentPrintManager`, add a private helper that walks the player Activity's context chain — repeatedly unwrapping `android.content.ContextWrapper.getBaseContext()` starting from the Activity — and returns a compact string listing each level's concrete class name and whether it `is android.app.Activity`, plus the class name of `activity.applicationContext`. Keep it allocation-light and exception-safe (wrap in try/catch, return `"unavailable"` on failure). Do not change any printing behaviour.

**Verification:**

- `Grep` — in `DocumentPrintManager.kt`, a new `private fun` whose body references `getBaseContext` and `ContextWrapper` (case-sensitive) matches.
- `Grep -n "Log\.d\("` over `DocumentPrintManager.kt` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS. Added `private fun contextChainDiagnostics()` to `DocumentPrintManager.kt` (walks `ContextWrapper.getBaseContext()` chain from the player Activity, flags `is android.app.Activity`, appends `appCtx`; exception-safe). Files: `DocumentPrintManager.kt` (+~30 LOC). Dev log recorded.

---

### Step 01.2 — Include the context chain and PrintManager identity in the WARN snapshot

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the existing print-environment diagnostic string (`printEnvDiagnostics()` or equivalent) so it also reports: the result of the Step 01.1 context-chain inspector; the concrete class name of the `PrintManager` obtained via `activity.getSystemService(Context.PRINT_SERVICE)`; and `Build.VERSION.RELEASE` / `Build.VERSION.INCREMENTAL`. Ensure this enriched snapshot is logged at WARN both when `printCurrentFile` is invoked and inside every `catch` branch in `dispatchPrint` and `printImage`. Tag each such log line with the literal prefix `S0145:` so it is greppable in the field log (e.g. `Timber.w("S0145: print env — …")`).

**Verification:**

- `Grep` — `S0145:` matches in `DocumentPrintManager.kt` at least three times (invocation trace + dispatch catch + image catch).
- `Grep` — `getSystemService(Context.PRINT_SERVICE)` still present (not removed).
- `Grep` — `Build.VERSION.INCREMENTAL` present in `DocumentPrintManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS (`S0145:` ×7; `getSystemService(Context.PRINT_SERVICE)` ×3; `Build.VERSION.INCREMENTAL` ×1). Extended `printEnvDiagnostics()` with `release`/`incremental`, `PrintManager` concrete class, and `ctxChain=[…]`; prefixed `S0145:` on the invocation trace and all print-failure WARN/ERROR lines (`dispatchPrint` null + both catches, `printImage` catch, `printText` WebView catch). Files: `DocumentPrintManager.kt`. Dev log recorded.

---

### Step 01.3 — Insert the S0145 flow-entry debug tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> At the entry of `printCurrentFile`, add `Timber.d("S0145: printCurrentFile type=<type> path=<path>")` (filled with the actual media type and path). One tag per changed flow entry — do not scatter it across helpers. This tag and the temporary print-environment diagnostics are removed when the ticket moves to `Verified`.

**Verification:**

- `Grep` — `Timber.d("S0145: printCurrentFile` matches exactly once in `DocumentPrintManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 1/1 PASS (`Timber.d("S0145: printCurrentFile` ×1). Added the flow-entry tag as the first line of `printCurrentFile`. Files: `DocumentPrintManager.kt`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` exit code 0 (`BUILD SUCCESSFUL in 49s`; only a pre-existing `WelcomeActivity` deprecation warning, unrelated).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `DocumentPrintManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] No public API change → catalog regen via `scan.ps1` / `render.ps1` for `app_v2` (run together with Phase 03's new class — auto-fields refresh only).

---

## Handoff Notes to Next Phase

The next field log from a Samsung One UI device built with this phase will contain a `S0145: print env — …` WARN line per print attempt, exposing: the Activity context-chain (which level, if any, is the real `Activity`), the `PrintManager` concrete class, and the firmware build. Use that to resolve strategic §6.1 (which context is bound) and §6.2 (whether a themed-context wrap suffices), then concretise Phase 02 steps and clear the INDEX blockers.

---

## Rollback Plan

Revert the phase commit — diagnostics-only, no data migration or user-facing surface changed.
