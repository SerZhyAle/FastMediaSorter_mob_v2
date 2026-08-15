# Phase 02 - Menu Strings

**Strategic spec:** [`../S0660_stream-card-overflow-actions-menu.md`](../S0660_stream-card-overflow-actions-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Add the trilingual (EN/RU/UK) strings the new overflow commands need: the `Edit` and `Send link` menu labels, the share-chooser title and the edit-dialog title.

---

## Prerequisites

- [ ] `streams_edit`, `streams_send_link` keys confirmed absent (verified in `/spec-tech` step 2).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 8 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 8 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 8 |

> Landscape variant absent - string resources are orientation-independent.

---

## Steps

### Step 02.1 - Add the four trilingual menu/dialog strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add four keys lockstep across EN/RU/UK with one `scripts/utils/set-android-string.ps1 -Action add` call per key (parity-enforced): `streams_edit` (EN "Edit" / RU "Изменить" / UK "Змінити"), `streams_send_link` (EN "Send link" / RU "Отправить ссылку" / UK "Надіслати посилання"), `streams_share_chooser_title` (EN "Share stream link" / RU "Поделиться ссылкой на трансляцию" / UK "Поділитися посиланням на трансляцію"), `streams_edit_dialog_title` (EN "Edit channel" / RU "Изменить канал" / UK "Змінити канал"). Use Ё where grammatical. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 message formula (action labels are imperative verbs) and §6 tone checklist (concise, no trailing punctuation on labels).

**Verification:**

- `Grep` - each of `streams_edit`, `streams_send_link`, `streams_share_chooser_title`, `streams_edit_dialog_title` present in all three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS (all 49 streams_ keys EN/RU/UK parity; Cyrillic intact). Files: strings.xml ×3 (+4 keys each). Dev log recorded.

---

## Phase Done Criteria

- [ ] Step 02.1 is `[x] done`.
- [ ] `scripts/check_strings_localized.ps1 -KeyPrefix "streams_"` exits 0.
- [ ] Dev log entry added for the strings change.

---

## Handoff Notes to Next Phase

Phase 03 references `R.string.streams_edit`, `R.string.streams_send_link`, `R.string.streams_share_chooser_title`, `R.string.streams_edit_dialog_title`. All exist after this phase.

---

## Rollback Plan

Revert the phase commit - removes four string keys; no code references them until Phase 03.
