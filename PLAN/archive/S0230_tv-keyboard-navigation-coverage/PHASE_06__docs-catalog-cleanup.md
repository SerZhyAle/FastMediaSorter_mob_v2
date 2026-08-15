# Phase 06 — Docs + Catalog Cleanup (final)

**Strategic spec:** [`../S0230_tv-keyboard-navigation-coverage.md`](../S0230_tv-keyboard-navigation-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 02, 03, 04, 05
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Final phase per template. Refresh catalog + changelog + functionality log for every file modified in Phases 02–05. Confirm trilingual string parity for any `a11y_*` keys added in Phase 05. Set status to `BlockNeedUserTest` and insert S0230 verification tags at flow entries — the device-test gate runs after this phase.

Note: `docs/FEATURES*.md` is NOT updated — strategic §8 says "Без изменений" (this is a UX fix of existing capability, not a new feature).

---

## Prerequisites

- [ ] Phases 02, 03, 04, 05 all ✅ Done.
- [ ] Working tree clean of unrelated modifications.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | autogen |
| `dev/CATALOG/app_v2.md` | Modified (regen) | autogen |
| `dev/CHANGELOG.md` | Modified (append) | one entry per Phase 02–05 file |
| `dev/FUNCTIONALITY.log` | Modified (append) | one entry |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified (Timber tag) | ≤ +1 line |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified (Timber tag) | ≤ +1 line |
| `PLAN/S0230_tv-keyboard-navigation-coverage.md` | Modified (status flip) | — |

---

## Steps

### Step 06.1 — Catalog regen

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. If a new class was added in Phase 04 (`DialogAccessibilityHelper`), populate its `role` and `status` via `pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -ClassMatches "DialogAccessibilityHelper" -Role "Posts AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED on dialog open for TalkBack initial focus (S0230 §6.5)." -Status reviewed`.

**Verification:**

- `Grep -n 'DialogAccessibilityHelper' dev/CATALOG/app_v2.md` matches once.
- `Grep -n 'DialogAccessibilityHelper' dev/CATALOG/app_v2.jsonl` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

### Step 06.2 — Dev changelog entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> For every file modified in Phases 02–05, append one entry via `.\scripts\add_to_dev_log.ps1 "<paths>" "<target>" "<description>"`. Group by phase target — one combined entry per phase is acceptable when the work is cohesive. Never edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep -c '\[DEV_LOG\] .* | S0230 phase 0[2-5]'` in `dev/CHANGELOG.md` ≥ 4 (one per phase 02–05).

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

### Step 06.3 — Functionality log entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 06.2

**Prompt for developer:**

> Append one CHANGE entry summarising the round-2 expansion: list-screen focus polish, mouse safety, dialog TalkBack helper, accessibility content audit.
> `pwsh -File scripts/add_to_functionality_log.ps1 -Id S0230 -Op CHANGE -Description "Universal input coverage round 2: list-screen RecyclerView focus polish (descendantFocusability + getInitialFocusView for list Activities), mouse safety fixes (super.onTouchEvent + performClick + TOOL_TYPE_MOUSE pass-through), dialog TalkBack initial focus helper (DialogAccessibilityHelper posts AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED 100ms after show), accessibility content audit (contentDescription verbs + ViewCompat.addAccessibilityAction for long-press flows)"`

**Verification:**

- `Grep -c '\[S0230\] \[CHANGE\]' dev/FUNCTIONALITY.log` ≥ 2 (round 1 already wrote one entry; round 2 adds another).

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

### Step 06.4 — Insert S0230 verification tags + flip status to BlockNeedUserTest

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`, `PLAN/S0230_tv-keyboard-navigation-coverage.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags": the ticket is about to enter `BlockNeedUserTest`, so insert `Timber.d("S0230: ...")` at each changed flow entry. Two entry points (matching round 1):
> - `BaseActivity.dispatchKeyEvent` — right after `tvKeyRouter.route(event)` returns non-null: `Timber.d("S0230: dispatchKeyEvent routed ${event.keyCode} → $action in ${this::class.simpleName}")`.
> - `WelcomeActivity.onTvNavigation` — first line of method body: `Timber.d("S0230: WelcomeActivity.onTvNavigation action=$action page=$currentPage")`.
> Then flip status: `pwsh -File scripts/spec_catalog/update.ps1 -Id S0230 -Status BlockNeedUserTest`. In the strategic spec header, change `**Status:** Tactical` to `**Status:** BlockNeedUserTest`. Run `/build` standard debug — verify tags compile.

**Verification:**

- `Grep -c 'Timber\.d\("S0230:' app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` = 2.
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0230 -Format json` shows `"status":"BlockNeedUserTest"`.
- Strategic spec header `**Status:** BlockNeedUserTest`.
- `/build` standard debug returns BUILD SUCCESSFUL.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `pwsh -File scripts/spec_catalog/select.ps1 -Id S0230 -Format json` shows `BlockNeedUserTest`.
- [ ] Two `Timber.d("S0230:` tags present in code; zero stale tags (none for any other Sxxxx).
- [ ] `/build` standard debug PASS.

---

## Handoff Notes to Next Phase

**Final phase — see INDEX.md Completion Gate.** After Phase 06, the spec is in `BlockNeedUserTest`. Device verification follows the test instructions in the strategic spec's `## Last Audit` block (`adb shell input keyevent` codes for media keys + hardware buttons; TalkBack / mouse / D-pad walkthroughs). After successful device test, `/spec-check S0230` flips status to `Verified` and removes the Timber tags.

---

## Rollback Plan

Revert phase commit(s) — catalog regen and changelog entries are append-only artefacts; Timber tag insertion is one line per entry point; status flip is reversible via `update.ps1 -Status Tactical`.
