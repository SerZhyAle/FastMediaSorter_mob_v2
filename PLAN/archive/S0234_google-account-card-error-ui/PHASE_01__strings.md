# Phase 01 — Strings (trilingual)

**Strategic spec:** [`../S0234_google-account-card-error-ui.md`](../S0234_google-account-card-error-ui.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Add all S0234 user-visible strings to `values/`, `values-ru/`, and `values-uk/` `strings_s0200.xml` files: per-reason summary + CTA pairs for the five `IdentityFailureReason` variants, plus the error-dialog title. No code consumers yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (none — foundation phase).
- [ ] Strategic spec §6 Decisions D1, D2, D3 read.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_s0200.xml` | Modified | ≤ 80 |
| `app_v2/src/main/res/values-ru/strings_s0200.xml` | Modified | ≤ 80 |
| `app_v2/src/main/res/values-uk/strings_s0200.xml` | Modified | ≤ 80 |

> No landscape parity concern — strings only.

---

## Steps

### Step 01.1 — Add S0234 string keys to all three locale files

**Files:** `app_v2/src/main/res/values/strings_s0200.xml`, `app_v2/src/main/res/values-ru/strings_s0200.xml`, `app_v2/src/main/res/values-uk/strings_s0200.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Append the following 12 keys to each of the three `strings_s0200.xml` files (EN under `values/`, RU under `values-ru/`, UK under `values-uk/`). Place them in a new `<!-- S0234 — sign-in error surfacing -->` comment block at the end of each file (before `</resources>`).
>
> Keys (English copy below — translate to RU / UK per the policy):
>
> - `s0234_error_dialog_title` — EN: `Google Drive sign-in failed`
> - `s0234_card_state_error_play_services_outdated_summary` — EN: `Google Play Services on this device is outdated, so signing in to Google Drive cannot complete. Update Play Services from the Play Store to continue.`
> - `s0234_card_state_error_play_services_outdated_cta` — EN: `Update Play Services`
> - `s0234_card_state_error_network_summary` — EN: `Sign-in could not reach Google. Check your internet connection and try again.`
> - `s0234_card_state_error_network_cta` — EN: `Retry`
> - `s0234_card_state_error_user_cancelled_summary` — EN: `Sign-in cancelled. Tap Sign in whenever you want to try again.`
> - `s0234_card_state_error_user_cancelled_cta` — EN: `Sign in`
> - `s0234_card_state_error_cct_unavailable_summary` — EN: `No supported browser is installed. Install Chrome, Firefox, Brave, or another browser that supports Chrome Custom Tabs, then try again.`
> - `s0234_card_state_error_cct_unavailable_cta` — EN: `Sign in`
> - `s0234_card_state_error_unknown_summary` — EN: `Sign-in failed for an unexpected reason. Try again — if it keeps failing, send the error report from the dialog.`
> - `s0234_card_state_error_unknown_cta` — EN: `Try again`
> - `s0234_card_state_error_diag_details_prefix` — EN: `Failure reason: %1$s` (placeholder receives the enum constant name, e.g. `PlayServicesOutdated`)
>
> RU translations — apply `..` (two dots, not `...`) and use `ё`/`Ё` where grammatically correct. Friendly + action-oriented tone (S0118 / `docs/COMMUNICATION_POLICY.md` §6). Reuse existing S0200 RU wording for Google-Drive surface words (e.g. `Google Драйв` / `Google Диск` — match `s0200_card_*` RU file).
>
> UK translations — same friendly tone; mirror the RU sentence structure.
>
> Strings pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist (positive next-step CTA, no jargon dump, no blaming the user).

**Verification:**

- `Grep` — `s0234_error_dialog_title` matches exactly once in each of the three files.
- `Grep` — `s0234_card_state_error_play_services_outdated_summary` matches exactly once in each of the three files.
- `Grep` — `s0234_card_state_error_unknown_cta` matches exactly once in each of the three files.
- `Grep` — `s0234_card_state_error_diag_details_prefix` matches exactly once in each of the three files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 4/4 PASS (`expected: 1 per file across 3 files | actual: 1×3 for s0234_error_dialog_title, s0234_card_state_error_play_services_outdated_summary, s0234_card_state_error_unknown_cta, s0234_card_state_error_diag_details_prefix`). Files: values/strings_s0200.xml, values-ru/strings_s0200.xml, values-uk/strings_s0200.xml (+~14 lines each). Dev log recorded.

---

### Step 01.2 — Locale audit

**Files:** none modified
**Depends on:** Step 01.1

**Prompt for developer:**

> Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0234_"` and confirm it exits with code 0. Any missing key in a locale must be added before this step is `[x] done`.

**Verification:**

- `Bash` — `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0234_"` exits 0 (`expected: 0 | actual: 0`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Verification 1/1 PASS. 12 keys present in EN/RU/UK. Dev log: not needed (no file modified).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] No build needed — strings are not consumed yet.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- All 12 string keys exist in three locales.
- Phase 02 may reference them by name without further locale checks.
- Phase 03 maps `IdentityFailureReason` → string-resource ids.

---

## Rollback Plan

Revert the three XML edits — no other code references the new keys yet.
