# Phase 07 - Service and operations files

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped - delivered by the bulk route, 2026-08-14
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** superseded - see Objective
**Started:** -
**Completed:** 2026-08-14 (by import, not by these steps)

---

## Objective

Translate the eleven remaining thematic files - input, file operations, sources, scheduling, widget, game, calculator, accounts and the `main`-side VR strings - in all ten locales. This phase closes strategic goal §2.1.

**Superseded 2026-08-14.** Same reason as Phase 06: every one of the eleven files rode in the single flat export and returned inside each locale's file. Evidence measured across all ten locales 2026-08-14: `strings_input.xml` 227 of 242 eligible, `strings_widget.xml` 61 of 61, `strings_game.xml` 60 of 60, `strings_vr.xml` 106 of 114 - identical in every locale. Strategic goal §2.1 is met to the depth the carve-out allows; what is left per locale is 89-100 keys, of which about 89 were never exported by design.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values-*/strings_input.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_file_operations.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_vr.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_sources.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_scheduled.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_widget.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_game.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_calculator.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_google_account.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_link_auth.xml` | New | n/a - generated |
| `app_v2/src/main/res/values-*/strings_resource_operations.xml` | New | n/a - generated |

> `strings_vr.xml` lives in `src/main/res` and ships in every flavor, so it belongs to this phase. The VR-only set under `src/vr/res` is Phase 08 and is blocked separately.

---

## Tranche procedure

As defined in [PHASE_02 "Tranche procedure"](PHASE_02__setup-screen-completion.md#tranche-procedure). Every step runs it with no `-KeyPrefix` - strategic §6.1 resolved that each file other than `strings.xml` is a whole tranche.

---

## Steps

### Step 07.1 - `strings_input.xml`

**Files:** `app_v2/src/main/res/values-*/strings_input.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the tranche procedure for `strings_input.xml`, 242 keys. Key names, gesture names and hardware button names follow the platform's own vocabulary in each language.

**Why:**

Strategic §2 requires full coverage of `main`, and this is the largest file left after `strings.xml`.

**Verification:**

- Key count per locale: expected 242, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 07.2 - `strings_file_operations.xml`

**Files:** `app_v2/src/main/res/values-*/strings_file_operations.xml`
**Depends on:** Step 07.1

**Prompt for developer:**

> Run the tranche procedure for `strings_file_operations.xml`, 141 keys. Copy, move and delete are destructive-action words; keep the distinction between them exact, because a mistranslation here loses a user's file.

**Why:**

Strategic §7 counts a mistranslation that survives the build as the failure mode this ticket has to guard against, and file operations are where that failure costs data rather than clarity.

**Verification:**

- Key count per locale: expected 141, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[ ]` not done

---

### Step 07.3 - `strings_vr.xml`

**Files:** `app_v2/src/main/res/values-*/strings_vr.xml`
**Depends on:** Step 07.2

**Prompt for developer:**

> Run the tranche procedure for `strings_vr.xml`, 114 keys. This is the `src/main` file, translated for all ten locales regardless of the open question in strategic §6.2 - that question governs the `src/vr` source set only.

**Why:**

Strategic §6.2 scopes the open owner decision to the flavor source sets, and this file ships in every flavor from `src/main`, so no decision gates it.

**Verification:**

- Key count per locale: expected 114, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 07.4 - `strings_sources.xml`

**Files:** `app_v2/src/main/res/values-*/strings_sources.xml`
**Depends on:** Step 07.3

**Prompt for developer:**

> Run the tranche procedure for `strings_sources.xml`, 83 keys. Protocol names - SFTP, SMB, WebDAV - stay as they are in every locale.

**Why:**

Strategic §2 requires full coverage of `main`.

**Verification:**

- Key count per locale: expected 83, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 07.5 - `strings_scheduled.xml`

**Files:** `app_v2/src/main/res/values-*/strings_scheduled.xml`
**Depends on:** Step 07.4

**Prompt for developer:**

> Run the tranche procedure for `strings_scheduled.xml`, 77 keys. Values carrying a quantity are `<plurals>`; supply every quantity class the target language needs, not only the ones English has.

**Why:**

Strategic §7 counts a formatting fault that survives the build as a shipped defect, and a missing plural class renders as the wrong quantity form to the user.

**Verification:**

- Key count per locale: expected 77, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 07.6 - `strings_widget.xml`

**Files:** `app_v2/src/main/res/values-*/strings_widget.xml`
**Depends on:** Step 07.5

**Prompt for developer:**

> Run the tranche procedure for `strings_widget.xml`, 61 keys. Widget labels are shown on the home screen in a fixed width, so prefer the shorter of two correct renderings.

**Why:**

Strategic §3.3 records that this ticket changes text inside existing elements without changing placement, so a translation that overflows its element is the one way this work can still break a layout.

**Verification:**

- Key count per locale: expected 61, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 07.7 - `strings_game.xml`

**Files:** `app_v2/src/main/res/values-*/strings_game.xml`
**Depends on:** Step 07.6

**Prompt for developer:**

> Run the tranche procedure for `strings_game.xml`, 60 keys.

**Why:**

Strategic §2 requires full coverage of `main`.

**Verification:**

- Key count per locale: expected 60, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 07.8 - `strings_calculator.xml`

**Files:** `app_v2/src/main/res/values-*/strings_calculator.xml`
**Depends on:** Step 07.7

**Prompt for developer:**

> Run the tranche procedure for `strings_calculator.xml`, 52 keys.

**Why:**

Strategic §2 requires full coverage of `main`.

**Verification:**

- Key count per locale: expected 52, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 07.9 - `strings_google_account.xml`

**Files:** `app_v2/src/main/res/values-*/strings_google_account.xml`
**Depends on:** Step 07.8

**Prompt for developer:**

> Run the tranche procedure for `strings_google_account.xml`, 33 keys. Product names - Google, Google Drive, Google Account - stay as they are; only the surrounding text is translated.

**Why:**

Strategic §2 requires full coverage of `main`.

**Verification:**

- Key count per locale: expected 33, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 07.10 - `strings_link_auth.xml`

**Files:** `app_v2/src/main/res/values-*/strings_link_auth.xml`
**Depends on:** Step 07.9

**Prompt for developer:**

> Run the tranche procedure for `strings_link_auth.xml`, 21 keys.

**Why:**

Strategic §2 requires full coverage of `main`.

**Verification:**

- Key count per locale: expected 21, actual must equal expected in all ten.
- Seeder exit code per locale: expected 0.

**Status:** `[ ]` not done

---

### Step 07.11 - `strings_resource_operations.xml`

**Files:** `app_v2/src/main/res/values-*/strings_resource_operations.xml`
**Depends on:** Step 07.10

**Prompt for developer:**

> Run the tranche procedure for `strings_resource_operations.xml`, 4 keys, then run the coverage gate and confirm `main` reports zero untranslated keys for all ten locales.

**Why:**

Strategic §11 criterion 1 makes a zero reading from the coverage gate the observable definition of goal §2.1 being met, and this is the last file that feeds it.

**Verification:**

- Key count per locale: expected 4, actual must equal expected in all ten.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1`: expected `0 of 4663 key(s) not translated` for each of the ten locales, actual must equal expected.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Strategic §11 criterion 1 met - coverage gate reports zero untranslated in `main` for all ten locales.
- [ ] Strategic §11 criterion 3 still met - `en`/`ru`/`uk` complete, no file under `values/`, `values-ru/` or `values-uk/` modified.
