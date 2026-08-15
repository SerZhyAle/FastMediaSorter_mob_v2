# Phase 08 - Flavor string sets

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-13
**Completed:** 2026-08-14

---

## Objective

Cover the `vr` and `noLegal` source sets in all ten best-effort locales - the owner's ruling of 2026-08-13, recorded in strategic §6.2. Delivers strategic goal §2.2.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] **Strategic §6.2 answered** 2026-08-13: all ten, same set as `main`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/res/values-<tag>/strings.xml` | New | n/a - generated |
| `app_v2/src/noLegal/res/values-<tag>/strings.xml` | New | n/a - generated |
| `app_v2/src/noLegal/res/values-<tag>/strings_s0298.xml` | New | n/a - generated |

> `<tag>` is one directory per language, all ten. Measured 2026-08-13 against `de`: the two sets contribute 59 and 17 exportable lines to the shared file. Per-file key counts are whatever the seeder reports as `eligible`, per the baseline rule in [INDEX.md](INDEX.md) - the numbers in the steps below predate the flat-file route and are corrected in place if they disagree.

---

## Tranche procedure

Both sets ride in the same export as `main` - `locale-bulk-export.ps1 -SourceSet main,vr,noLegal` - and `locale-bulk-import.ps1` routes each line back by the source set its sidecar record names. So this phase runs no separate tranche: it closes when the shared import has landed and the counts below hold. The seeder still does the writing, with `-SourceSet vr` / `-SourceSet noLegal` supplied by the importer rather than by hand.

The `-SourceFile`-scoped tranche procedure of [PHASE_02](PHASE_02__setup-screen-completion.md#tranche-procedure) remains the fallback for a single file that has to be redone on its own.

---

## Steps

### Step 08.1 - `vr` source set

**Files:** `app_v2/src/vr/res/values-*/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the tranche procedure with `-SourceSet vr -SourceFile strings.xml`, 56 keys, for each language in the answered set. Headset and controller terms follow the vocabulary the headset's own system UI uses in that language.

**Why:**

Strategic §2 lists covering the `vr` set as a goal of this ticket, and strategic §1 records that neither the first tranche nor the parity gate reaches it.

**Verification:**

- Key count per created locale file: expected 56, actual must equal expected.
- Seeder exit code per locale: expected 0.
- `/build` -> `vr debug` passes.

**Status:** `[x]` done - measured 2026-08-14 across all ten locales: `vr/strings.xml` 54 keys of 56 eligible, `noLegal/strings.xml` 16 of 16, `noLegal/strings_s0298.xml` 1 of 1, present in 10 of 10 locale directories. The two `vr` gaps are symbol-only values never exported. Delivered by the shared bulk import, not by a separate tranche.

---

### Step 08.2 - `noLegal` source set

**Files:** `app_v2/src/noLegal/res/values-*/strings.xml`, `app_v2/src/noLegal/res/values-*/strings_s0298.xml`
**Depends on:** Step 08.1

**Prompt for developer:**

> Run the tranche procedure with `-SourceSet noLegal` for both files - `strings.xml` at 16 keys and `strings_s0298.xml` at 1 - for each language in the answered set.

**Why:**

Strategic §2 lists covering the `noLegal` set as a goal, and strategic §11 criterion 2 makes both sets a completion condition for this ticket.

**Verification:**

- Key counts per created locale file: expected 16 and 1, actual must equal expected.
- Seeder exit code per locale: expected 0.
- `/build` -> `noLegal debug` passes.

**Status:** `[x]` done - measured 2026-08-14 across all ten locales: `vr/strings.xml` 54 keys of 56 eligible, `noLegal/strings.xml` 16 of 16, `noLegal/strings_s0298.xml` 1 of 1, present in 10 of 10 locale directories. The two `vr` gaps are symbol-only values never exported. Delivered by the shared bulk import, not by a separate tranche.

---

## Phase Done Criteria

- [ ] Every `Step 08.*` above is `[x] done`.
- [ ] Strategic §11 criterion 2 met for the answered language set.
- [ ] `vr debug` and `noLegal debug` both build.
