# Phase 01 - Strings

**Strategic spec:** [`../S0483_crash-report-email-button.md`](../S0483_crash-report-email-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 1 / 1
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Add the trilingual user-facing strings the crash-report button and email need: the button accessibility label/tooltip, the email subject, and the email body intro. No code yet.

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

### Step 01.1 - Add three trilingual string keys

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three string keys across EN/RU/UK in lockstep using one parity-enforced call per key:
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"`.
> Run this through the **PowerShell tool, not the Bash tool** - Cyrillic literals corrupt (mojibake) when passed as pwsh args through Bash. If the PowerShell path is unavailable, author a temporary UTF-8 `.ps1` via the Write tool and run it.
> Keys and copy:
> - `error_dialog_report_to_author` - EN: `Email crash report to author` · RU: `Отправить отчёт о сбое автору` · UK: `Надіслати звіт про збій автору`
> - `crash_report_email_subject` - EN: `FastMediaSorter crash report` · RU: `Отчёт о сбое FastMediaSorter` · UK: `Звіт про збій FastMediaSorter`
> - `crash_report_email_body_intro` - EN: `Please describe what you were doing when the problem occurred. Technical details and the app log are attached below.` · RU: `Опишите, что вы делали, когда возникла проблема. Технические данные и журнал приложения приложены ниже.` · UK: `Опишіть, що ви робили, коли виникла проблема. Технічні дані та журнал додатку додано нижче.`
> Copy must pass `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist): user-respecting, no blame, concise, no raw tech jargon in the visible label/subject.

**Verification:**

- `Grep` - `error_dialog_report_to_author` present in all three `strings.xml` (EN/RU/UK).
- `Grep` - `crash_report_email_subject` present in all three.
- `Grep` - `crash_report_email_body_intro` present in all three.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "crash_report_email"` - exit 0.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "error_dialog_report_to_author"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (3 keys present in EN/RU/UK; parity audits exit 0; Cyrillic intact). Files: values/values-ru/values-uk strings.xml.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Three string ids exist trilingually: `R.string.error_dialog_report_to_author`, `R.string.crash_report_email_subject`, `R.string.crash_report_email_body_intro`. Phase 03 resolves them in the dialog. No icon was added - Phase 03 reuses the existing `R.drawable.ic_send_email`.

---

## Rollback Plan

Revert the three `strings.xml` edits - no code references them until Phase 03.
