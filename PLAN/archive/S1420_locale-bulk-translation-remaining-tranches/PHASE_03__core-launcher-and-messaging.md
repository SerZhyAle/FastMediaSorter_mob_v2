# Phase 03 - Core strings: launcher and messaging

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Translate the most-seen prefixes of `strings.xml` - the launcher, dialogs, errors and transient messages - into all ten locales.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Step 01.1's prefix-interaction predicate passed - under `-Merge` an out-of-prefix key survives. Without it each step here would delete the previous step's output.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-{ar,bn,de,es,fr,hi,it,pt,ur}/strings.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-b+zh+Hans/strings.xml` | New | n/a - generated |
| `scripts/utils/seed-locale-tranche.ps1` | Modified | ≤ 340 |

> These ten files do not exist yet - the first step of this phase creates them. Every later step merges into them.

---

## Tranche procedure

As defined in [PHASE_02 "Tranche procedure"](PHASE_02__setup-screen-completion.md#tranche-procedure). Each step below names its `-KeyPrefix` set; run the procedure once per prefix, or batch a step's prefixes into one map when they are seeded together.

`strings.xml` carries 3050 eligible keys across 358 first-token prefixes, so it is split by prefix rather than by count - strategic §6.1 resolved that the prefix is the only boundary in this file that lines up with a screen.

---

## Steps

### Step 03.1 - `launcher_` prefix

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the tranche procedure for `strings.xml -KeyPrefix launcher_`, 236 keys. This is the first write into `values-*/strings.xml`, so the ten files are created here; use `-Merge` anyway so the step is safe to re-run.

**Why:**

Strategic §5 orders tranches by user visibility and puts `strings.xml` first, and the launcher is the surface every user meets before any other.

**Verification:**

- Keys matching `name="launcher_` per locale file: expected 236, actual 236 in all ten.
- Seeder exit code per locale: expected 0, actual 0 in all ten - `eligible 236 | written 236 | rejected 0`.

**Status:** `[x]` done

---

### Step 03.2 - `dialog_` and `perm_` prefixes

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run the tranche procedure for `-KeyPrefix dialog_` (106 keys) and `-KeyPrefix perm_` (79 keys). Permission rationale text carries the user-visible justification the store listing repeats, so keep it a statement of what the app does with the permission, not a plea to grant it.

**Why:**

Strategic §5 orders by visibility, and a dialog or a permission prompt is modal - the user cannot proceed past it without reading it.

**Verification:**

- Keys matching `name="dialog_` per locale: expected 106, actual 106; `name="perm_`: expected 79, actual 79. Both hold in all ten.
- `launcher_` count still 236 in every locale - actual 236, so `-Merge` preserved Step 03.1. File totals 236 -> 342 -> 421 across the two seeder passes.
- Seeder exit code per locale per prefix: expected 0, actual 0 - `rejected 0` throughout.

**Status:** `[x]` done

**Tooling defect found and fixed inside this step:** `-DumpSource` emitted a `<plurals>` body as one raw XML string, but the writer accepts `plurals` only as an object of quantity -> text. The map built the documented way was therefore a map the seeder itself rejects, and `dialog_player_exit_pending_queue_message` would have been dropped from all ten locales with an exit 3 nobody could fix without hand-editing. `-DumpSource` now emits each element kind in the shape `-MapPath` reads back - string, object, array - and the round trip is proven on the corpus's 7 `plurals` and 1 `string-array` (`color_theme_options`, 9 items, not collapsed to a scalar). Phase 01 built the merge half of the round trip; this is the dump half, found only because `strings.xml` is the first tranche file that carries a plural.

---

### Step 03.3 - `error_`, `msg_` and `toast_` prefixes

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Run the tranche procedure for `-KeyPrefix error_` (145 keys), `-KeyPrefix msg_` (50) and `-KeyPrefix toast_` (29). These are the failure and confirmation messages, so `docs/COMMUNICATION_POLICY.md` §2's message formula applies to the source and the translation must not change which part of the formula is present - a message that names a cause and an action in English names both in every locale.

**Why:**

Strategic §7 treats a mixed-language window as reading like a defect, and an error message is the moment the user is already inclined to read the app as broken.

**Verification:**

- Keys per locale: `error_` expected 145, actual 145; `msg_` expected 50, actual 50; `toast_` expected 29, actual 29. All hold in all ten.
- Strings pass `COMMUNICATION_POLICY` §6 checklist - the cause-plus-action shape of the English source was carried into every locale, and no translation introduces an exception, an apology or a raw error code the source lacks.
- Earlier prefixes unchanged: `launcher_` 236, `dialog_` 106, `perm_` 79 - actual, in all ten. File totals 421 -> 566 -> 616 -> 645.
- `..` as ellipsis survived every locale (§5 of the policy): zero `…` characters in any of the 30 maps.

**Status:** `[x]` done

---

### Step 03.4 - `label_` and `no_` prefixes

**Files:** `app_v2/src/main/res/values-*/strings.xml`
**Depends on:** Step 03.3

**Prompt for developer:**

> Run the tranche procedure for `-KeyPrefix label_` (33 keys) and `-KeyPrefix no_` (23 keys). The `no_` family is empty-state text; keep it neutral rather than apologetic, matching the English source.

**Why:**

Strategic §7's mitigation is closing a surface completely, and labels and empty states sit inside the same screens the three steps above already translated.

**Verification:**

- Keys per locale: `label_` expected 33, actual 33; `no_` expected 23, actual 23. Both hold in all ten.
- Every prefix from steps 03.1-03.3 still at its expected count - actual, in all ten; file total 645 -> 701.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fr` BUILD SUCCESSFUL in 11s, exit 0. AAPT2 parsed and merged all ten locale files, which is what a resource-only change can break.
- [x] Ten `values-*/strings.xml` files exist, each holding 701 keys.

> Counts corrected against the seeder's own `-DumpSource` on 2026-08-09, per the INDEX rule that the seeder is the authority: `error_` 145 not 146, `no_` 23 not 24, so the phase total is 701 not 703.
- [x] No file under `values/`, `values-ru/` or `values-uk/` modified.

---

## Outcome - 2026-08-09

Eight prefixes closed in ten locales: 701 keys each, 7 010 translations. Per-locale file totals stepped 236 -> 342 -> 421 -> 566 -> 616 -> 645 -> 701, every seeder run `rejected 0`, so `-Merge` carried each earlier tranche through untouched rather than the counts merely agreeing at the end.

Closure verified against the source rather than against the seeder's own report: every eligible key in the eight prefixes, recomputed from `values/strings.xml` after the last write, is present in all ten locale files - `missing=0`, ten times. That check matters because the corpus grew mid-phase (4693 -> 4743 keys, another session adding to the same tree), and a count-only check cannot tell a closed prefix from one that gained a key after its tranche was dumped.

Coverage gate afterwards: 3628 of 4743 untranslated per locale, down from 4278 of 4693. Strict `en`/`ru`/`uk` parity still green, gate exit 0.

Two defects surfaced and were handled differently, on purpose:

- The `-DumpSource` plurals round trip was fixed inside step 03.2 - it blocked this phase's own tranche.
- 81 keys holding layout attribute literals with no `translatable="false"` were parked as **S1550** rather than fixed here: `strings.xml` is the strict source this plan must not edit, and changing eligibility mid-plan would move every remaining step's expected count. Ten translation agents each recognised and skipped them by hand, which is the judgement that will not survive the next pass - hence the ticket.
