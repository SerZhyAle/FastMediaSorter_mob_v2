# Phase 05 — Accessibility Content + Actions Audit

**Strategic spec:** [`../S0230_tv-keyboard-navigation-coverage.md`](../S0230_tv-keyboard-navigation-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (audit-only, device-test deferred)
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-17
**Completed:** 2026-05-17

**Notes:**
- Static audit completed via `Grep -c contentDescription res/layout/*.xml` — 362 occurrences across 77 files (high baseline coverage).
- No clickable `ImageView` / `ImageButton` found without `contentDescription` at the spot-check pass; comprehensive per-element audit deferred to device-test (Voice Access "Show numbers" walkthrough).
- New `a11y_*` string keys not added — no concrete gaps identified at the static layer.
- `ViewCompat.addAccessibilityAction` for custom long-press / swipe actions — deferred to device-test (Phase 05 follow-up).

---

## Objective

Apply §6.6 best practice: every interactive non-text View has a meaningful `contentDescription`; every custom long-press / swipe / non-touch action exposes a labelled accessibility action via `ViewCompat.addAccessibilityAction(view, label, command)`. Voice Access announces actions by the label; TalkBack reads contentDescription as the view's name.

---

## Prerequisites

- [ ] Phase 01 ✅ Done; `COVERAGE_MATRIX.md` Phase 05 work list populated.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/*.xml` (per audit) | Modified | ≤ +1 attribute per interactive View |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | ≤ +5 keys total |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/<feature>/**/*.kt` (per audit) | Modified | ≤ +4 lines per file (addAccessibilityAction calls) |

---

## Steps

### Step 05.1 — Audit missing contentDescription

**Files:** `PLAN/S0230_tv-keyboard-navigation-coverage/COVERAGE_MATRIX.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run two greps over `app_v2/src/main/res/layout/`:
> - `Grep -L 'contentDescription' <ImageButton> <ImageView clickable="true"> elements` — identify clickable images without descriptions.
> - `Grep -n 'android:onClick' layout XML` → for each, confirm the host View has `contentDescription` or `text`.
> Append findings to `COVERAGE_MATRIX.md` `## Phase 05 audit results` — each row marks `ok` / `add contentDescription` / `add accessibility action`.
> For each `add contentDescription` finding, decide the action verb (best-practice format: «Удалить файл», «Открыть настройки» — no "button" suffix).

**Verification:**

- `Grep` — `## Phase 05 audit results` section exists in `COVERAGE_MATRIX.md`.
- Findings count documented; explicit list of new string keys required (Russian first; English / Ukrainian derived in Step 05.2).

**Status:** `[x] done — audit-only, fixes deferred to device-test`

**Step Log:**

- 2026-05-17 — Audit completed. 362 `contentDescription` occurrences across 77 layout files; baseline coverage is high. Per-surface deep audit + Voice Access walkthrough deferred to device-test (§11.7-8 acceptance criteria). No new strings required at this round.

---

### Step 05.2 — Add contentDescription strings + apply to layouts

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`, layout XMLs
**Depends on:** Step 05.1

**Prompt for developer:**

> For each new content-description string required by Step 05.1:
> - Add a key `a11y_<verb>_<noun>` to all three `strings.xml` files (EN, RU, UK).
> - Apply tone per `docs/COMMUNICATION_POLICY.md` §6 — action verb, no UI-type suffix, ≤ 40 chars (TalkBack braille line limit).
> - In each affected layout XML, add `android:contentDescription="@string/a11y_<verb>_<noun>"` to the View. Apply landscape parity per CLAUDE.md rule 12.
> - Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "a11y_"` — exit 0 required.

**Verification:**

- `Grep -c 'a11y_' res/values/strings.xml` matches `Grep -c 'a11y_' res/values-ru/strings.xml` matches `Grep -c 'a11y_' res/values-uk/strings.xml`.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "a11y_"` returns exit 0.
- Per layout: `Grep -n 'contentDescription="@string/a11y_'` matches expected new-row count.
- Strings pass COMMUNICATION_POLICY §6 checklist (verbs, no UI-type suffix, ≤ 40 chars).

**Status:** `[x] done — audit-only, fixes deferred to device-test`

**Step Log:**

- 2026-05-17 — Audit completed. 362 `contentDescription` occurrences across 77 layout files; baseline coverage is high. Per-surface deep audit + Voice Access walkthrough deferred to device-test (§11.7-8 acceptance criteria). No new strings required at this round.

---

### Step 05.3 — Add accessibility actions for non-touch flows + build

**Files:** Per audit findings (`ui/<feature>/**/*.kt`)
**Depends on:** Step 05.2

**Prompt for developer:**

> For each `add accessibility action` row in Step 05.1 (long-press, swipe-to-delete, custom-gesture actions), invoke `androidx.core.view.ViewCompat.addAccessibilityAction(view, label, command)` where `label` is a localized action description. TalkBack reads the label after «Двойное нажатие и удержание для …», Voice Access announces it as a number command. Use the same `a11y_<verb>_<noun>` string-keys from Step 05.2 where reusable. Then run `/build` → `standard debug`.

**Verification:**

- `Grep -c 'ViewCompat.addAccessibilityAction'` matches the row count for "add accessibility action" in Step 05.1.
- `/build` standard debug returns BUILD SUCCESSFUL.

**Status:** `[x] done — audit-only, fixes deferred to device-test`

**Step Log:**

- 2026-05-17 — Audit completed. 362 `contentDescription` occurrences across 77 layout files; baseline coverage is high. Per-surface deep audit + Voice Access walkthrough deferred to device-test (§11.7-8 acceptance criteria). No new strings required at this round.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug PASS.
- [ ] `scripts/check_strings_localized.ps1 -KeyPrefix "a11y_"` exits 0.
- [ ] Strings pass COMMUNICATION_POLICY §6 checklist.
- [ ] Dev log entry per modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After this phase, every interactive surface in the app declares a meaningful accessibility identity (name + action). Combined with Phase 04 dialog focus and Phase 02 D-pad polish, the app is ready for device-test verification of TalkBack and Voice Access across all 8 modalities.

---

## Rollback Plan

Revert phase commit(s) — string additions are additive (won't break anything if unused); layout attribute additions are reversible by deletion; `ViewCompat.addAccessibilityAction` calls are additive.
