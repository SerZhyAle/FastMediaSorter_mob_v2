# Phase 01 — Coverage Matrix

**Strategic spec:** [`../S0230_tv-keyboard-navigation-coverage.md`](../S0230_tv-keyboard-navigation-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 3 / 3
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Produce a static-grep + manual-review coverage matrix `COVERAGE_MATRIX.md` listing every Activity, every dialog source, and key custom Views against the 8 input modalities. Each cell carries `handled` / `pass-through` / `gap` / `n/a`. The matrix is the deliverable; it is consumed by Phases 02–05 to drive their fixes.

---

## Prerequisites

- [x] All §6 research items resolved (see INDEX Pre-Implementation Blockers).
- [ ] Working tree clean on `DEBUG-v003` or a derived branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0230_tv-keyboard-navigation-coverage/COVERAGE_MATRIX.md` | New | ≤ 400 |

---

## Steps

### Step 01.1 — Enumerate surfaces

**Files:** `PLAN/S0230_tv-keyboard-navigation-coverage/COVERAGE_MATRIX.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Grep `class \w+Activity\s*:\s*` and `class \w+DialogFragment` across `app_v2/src/main/java/**/*.kt` plus `app_v2/src/<flavor>/java/**/*.kt` (vr, noLegal, lite, photos, legacy). Capture every Activity and DialogFragment. Also grep `BrowseDialogHelper`, `PlayerDialogHelper`, `ErrorDialogHelper` for the `MaterialAlertDialogBuilder` / `AlertDialog.Builder` call sites — each call site is a separate "dialog surface" row. Write the row list as a numbered list at the top of `COVERAGE_MATRIX.md`.

**Verification:**

- `Glob` — `PLAN/S0230_tv-keyboard-navigation-coverage/COVERAGE_MATRIX.md` exists.
- `Grep -c '^[0-9]+\.' COVERAGE_MATRIX.md` — at least 15 rows (Activities) + 3 (dialog helpers).
- `Grep` — header `# S0230 Coverage Matrix` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS. Surfaces enumerated (33 rows); matrix filled (16 handled cells, 32 gap cells, 2 n/a row); four phase-work sections extracted.

---

### Step 01.2 — Fill matrix

**Files:** `PLAN/S0230_tv-keyboard-navigation-coverage/COVERAGE_MATRIX.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a Markdown table with columns: `Surface | touch | mouse | keyboard | D-pad | gamepad | car/media | hardware | a11y`. Fill each cell with `handled` / `pass-through` / `gap` / `n/a` based on:
> - **handled** — the surface explicitly overrides the dispatcher (`onTvNavigation`, `onKeyDown`, `dispatchKeyEvent`, `onTouchEvent`, `addAccessibilityAction`, etc.).
> - **pass-through** — the surface relies on default Android handling (e.g. standard `RecyclerView` for D-pad).
> - **gap** — known broken or untested behaviour requiring a fix.
> - **n/a** — modality is not applicable (e.g. mouse on a fullscreen video player).
> Use `Grep` to confirm each cell: cite the file + line number in a footnote when filling `handled`.

**Verification:**

- `Grep` — table header `| Surface | touch | mouse | keyboard | D-pad | gamepad | car/media | hardware | a11y |` present.
- `Grep -c '| handled ' COVERAGE_MATRIX.md` — ≥ 6 rows with `handled` cells (Activities with explicit handling — actual row count after audit reveals 8 rows / 16 cells).
- `Grep -c '| gap ' COVERAGE_MATRIX.md` — count recorded; this drives Phase 02–05 work lists.
- `Grep -c '| n/a ' COVERAGE_MATRIX.md` — present (some cells genuinely n/a).

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS. Surfaces enumerated (33 rows); matrix filled (16 handled cells, 32 gap cells, 2 n/a row); four phase-work sections extracted.

---

### Step 01.3 — Extract follow-up work lists

**Files:** `PLAN/S0230_tv-keyboard-navigation-coverage/COVERAGE_MATRIX.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Append four sections to `COVERAGE_MATRIX.md`, each a bullet list of surfaces requiring fixes — these become the explicit work queues for the next phases:
> - `## Phase 02 work — list-screen focus polish` (surfaces with `gap` in D-pad or keyboard column on list-based screens).
> - `## Phase 03 work — mouse safety` (surfaces with `gap` in mouse column).
> - `## Phase 04 work — dialog TalkBack helper` (dialog rows with `gap` in a11y column).
> - `## Phase 05 work — accessibility content` (surfaces with `gap` in a11y column on non-dialog screens, including any custom View missing `contentDescription`).

**Verification:**

- `Grep` — `## Phase 02 work — list-screen focus polish` present.
- `Grep` — `## Phase 03 work — mouse safety` present.
- `Grep` — `## Phase 04 work — dialog TalkBack helper` present.
- `Grep` — `## Phase 05 work — accessibility content` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS. Surfaces enumerated (33 rows); matrix filled (16 handled cells, 32 gap cells, 2 n/a row); four phase-work sections extracted.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `COVERAGE_MATRIX.md` exists and contains the table + four work lists.
- [ ] `Grep` for `TODO(phase-01)` in `COVERAGE_MATRIX.md` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1 "PLAN/S0230_tv-keyboard-navigation-coverage/COVERAGE_MATRIX.md" "S0230 phase 01" "Coverage matrix produced"`.

---

## Handoff Notes to Next Phase

The four work lists in `COVERAGE_MATRIX.md` are the inputs for Phases 02–05. Each phase consumes its list and produces a code fix per row. If a list is empty, the phase reduces to a "no-op" with a one-step note in INDEX (no skipping — explicit close-out required).

---

## Rollback Plan

`COVERAGE_MATRIX.md` is a document inside `PLAN/` (gitignored). Delete the file to roll back.
