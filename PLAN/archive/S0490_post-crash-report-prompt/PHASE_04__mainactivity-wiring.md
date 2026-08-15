# Phase 04 - MainActivity wiring

**Strategic spec:** [`../S0490_post-crash-report-prompt.md`](../S0490_post-crash-report-prompt.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Invoke the prompt manager once from the first screen on a fresh launch, so a previous crash is offered to the user. Activity stays delegation-only.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ +6 LOC |

---

## Steps

### Step 04.1 - Call the manager on fresh launch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `MainActivity.onCreate`, after `super.onCreate(savedInstanceState)` (binding is attached by then), and only when `savedInstanceState == null` (fresh launch, not a recreation), construct `CrashReportPromptManager(this)` and call `maybeShowPrompt()`. Keep it a single delegated call - no detection or email logic in the Activity. Add the import for `CrashReportPromptManager`.

**Verification:**

- `Grep` - `CrashReportPromptManager` present in `MainActivity.kt`.
- `Grep` - `maybeShowPrompt` present in `MainActivity.kt`.
- `Grep` - `savedInstanceState == null` present near the call (guard).

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (import + savedInstanceState==null guard + maybeShowPrompt call). Placed after welcome/settings redirects. S0490 debug tag inserted in manager; `.\a.ps1 fc` BUILD SUCCESSFUL. Files: MainActivity.kt.

---

## Phase Done Criteria

- [ ] Step 04.1 is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` (this is the last code phase; the S0490 debug tag is inserted before this build).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

End-to-end: a previous-session crash → next launch → prompt → on consent the author receives the crash text + log ZIP. Phase 05 documents and regenerates the catalog.

---

## Rollback Plan

Revert the `MainActivity` edit - the manager becomes unreferenced but compilation holds.
