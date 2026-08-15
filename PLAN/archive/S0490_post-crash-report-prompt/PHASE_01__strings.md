# Phase 01 - Strings

**Strategic spec:** [`../S0490_post-crash-report-prompt.md`](../S0490_post-crash-report-prompt.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 05
**Steps done:** 1 / 1
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Add the trilingual strings for the post-crash prompt: title, message, and the positive button label. Subject/body of the email reuse the existing S0483 strings; the negative button reuses `R.string.cancel`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +3 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +3 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +3 keys |

---

## Steps

### Step 01.1 - Add three trilingual prompt strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three keys across EN/RU/UK in lockstep via the byte-preserving tool, invoked through the **PowerShell tool, not Bash** (Cyrillic args corrupt across the bash→pwsh boundary); if unavailable, author a temporary UTF-8 `.ps1` and run it. Call `set-android-string.ps1 -Action add -Module app_v2 -Key <key> -En <en> -Ru <ru> -Uk <uk>` once per key:
> - `crash_prompt_title` - EN: `Send crash report?` · RU: `Отправить отчёт о сбое?` · UK: `Надіслати звіт про збій?`
> - `crash_prompt_message` - EN: `The app closed unexpectedly last time. Send a crash report with the app log to the author?` · RU: `В прошлый раз приложение неожиданно закрылось. Отправить автору отчёт о сбое с журналом приложения?` · UK: `Минулого разу додаток несподівано закрився. Надіслати автору звіт про збій із журналом додатку?`
> - `crash_prompt_send` - EN: `Send report` · RU: `Отправить отчёт` · UK: `Надіслати звіт`
> Copy must pass `docs/COMMUNICATION_POLICY.md` §2/§6: no blame, no raw stack trace in the visible text, concise.

**Verification:**

- `Grep` - `crash_prompt_title`, `crash_prompt_message`, `crash_prompt_send` each present in all three `strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "crash_prompt"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (3 keys in EN/RU/UK; parity exit 0; Cyrillic intact). Files: values/values-ru/values-uk strings.xml.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`R.string.crash_prompt_title`, `R.string.crash_prompt_message`, `R.string.crash_prompt_send` exist trilingually. Phase 03 resolves them; the email subject/body reuse `crash_report_email_subject` / `crash_report_email_body_intro` from S0483.

---

## Rollback Plan

Revert the three `strings.xml` edits - no code references them until Phase 03.
