# Phase 04 — Print Messages

**Strategic spec:** [`../S0145_bugfix-print-rejected-context-not-activity.md`](../S0145_bugfix-print-rejected-context-not-activity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** —
**Completed:** 2026-05-10

---

## Objective

Make the user-visible print outcomes read in line with the communication policy: the "couldn't open the print dialog, here's the share menu instead" notice is reassuring and actionable; the last-resort "no print service" notice explains the situation and points at a next step rather than dead-ending. No logic change.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (the two notices it wired — `print_fallback_to_share`, `error_print_unavailable` — are the ones this phase rewords).
- [ ] `docs/COMMUNICATION_POLICY.md` re-read for the relevant message type before editing strings.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> No layout files touched — landscape parity not applicable.

---

## Steps

### Step 04.1 — Reword the print outcome notices in all three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In all three `strings.xml` files, revise: (1) `error_print_unavailable` — from the current bare "No print service available on this device" to a friendly, non-blaming line that states printing is not available here and suggests the alternative (sharing the file, or installing a print service), per `docs/COMMUNICATION_POLICY.md` §2 (error + next-step CTA); (2) `print_fallback_to_share` (added in Phase 03) — confirm it reads as "the print dialog couldn't open, so we've opened your share menu — pick a print target there", friendly and brief. Apply the author style: `..` not `...`; `ё`/`Ё` in Russian where grammatically correct. Do not touch `print_job_label`, `error_print_file_too_large`, `error_print_download_failed` (those are accurate and out of scope) unless the tone checklist flags them — if it does, fix in the same step.

**Verification:**

- `Grep` — `error_print_unavailable` present in all three `strings.xml` files (still defined).
- `Grep` — `print_fallback_to_share` present in all three `strings.xml` files.
- `Grep` — no occurrence of literal `...` (three dots) in the changed string values (use `..`).
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "print_"` — exit code 0.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_print"` — exit code 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 5/5 PASS. `error_print_unavailable` + `print_fallback_to_share` present in EN/RU/UK; no `...`; `check_strings_localized.ps1 -KeyPrefix "print_"` exit 0; `check_strings_localized.ps1 -KeyPrefix "error_print"` exit 0; §6 tone checklist OK (friendly, CTA present, no raw exception, no dead-end). EN: "Couldn't print — no print service found. Share the file or install a print app." / "Print dialog couldn't open — the share menu is now open. Pick a print app." RU/UK: matching semantics. Dev log recorded (3 files).

---

### Step 04.2 — No-op verification that no code references changed

**Files:** —
**Depends on:** Step 04.1

**Prompt for developer:**

> This phase is strings-only. Confirm no `.kt` file was modified: the string keys (`error_print_unavailable`, `print_fallback_to_share`) are unchanged — only their values were edited. No new keys, no removed keys beyond what Phase 03 already added.

**Verification:**

- `git diff --name-only` (manual) — only the three `strings.xml` files appear for this phase's commit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (manual). Phase 04 edits confined to three `strings.xml` files via `set-android-string.ps1`. No `.kt` file was opened or modified. String keys `error_print_unavailable` and `print_fallback_to_share` unchanged — only values reworded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` exit code 0 (`BUILD SUCCESSFUL in 1m 2s`).
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "print_"` and `-KeyPrefix "error_print"` — both exit code 0.
- [x] Dev log entry added for the three `strings.xml` files.
- [x] No catalog change (no `.kt` touched).

---

## Handoff Notes to Next Phase

All user-visible print strings are finalised in EN/RU/UK. Phase 05 reflects the new behaviour in `docs/FEATURES*` and regenerates the catalog.

---

## Rollback Plan

Revert the phase commit — string values revert; no logic, no schema, nothing persistent.
