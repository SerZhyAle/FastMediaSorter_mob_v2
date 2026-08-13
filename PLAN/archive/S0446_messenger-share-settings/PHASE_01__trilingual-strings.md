# Phase 01 - Trilingual strings

**Strategic spec:** [`../S0446_messenger-share-settings.md`](../S0446_messenger-share-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02 (titles needed at registration), Phase 03 (send-failure messages)
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Add the EN/RU/UK strings needed by the rest of the ticket: the three settings/menu titles ("Send to WhatsApp", "Send to Instagram"; Telegram title already exists as `share_to_telegram`) and the two send-failure messages for WhatsApp/Instagram. No code changes.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | + ~4 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | + ~4 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | + ~4 keys |

---

## Steps

### Step 01.1 - Add messenger title + failure strings (EN/RU/UK in lockstep)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add four new string keys across EN/RU/UK in lockstep using `scripts/utils/set-android-string.ps1 -Action add` (parity-enforced):
> - `share_to_whatsapp` - EN "Send to WhatsApp" (mirror the existing `share_to_telegram` wording).
> - `share_to_instagram` - EN "Send to Instagram".
> - `share_to_whatsapp_failed` - EN mirror of `share_to_telegram_failed` ("Unable to send to WhatsApp. Try the standard share instead.").
> - `share_to_instagram_failed` - EN mirror for Instagram.
>
> Cyrillic caveat (CLAUDE.md / agent memory): RU/UK literals corrupt when passed as PowerShell args through the Bash tool. Run `set-android-string.ps1` from the PowerShell tool (not Bash), or author a small UTF-8 `.ps1` via Write and execute it. Do not type Cyrillic into a Bash-tool command line. The unavailable-toggle subtitle (`settings_send_command_unavailable` = "Not installed") already exists from S0452 - do not re-add it.

**Verification:**

- `Grep` - `share_to_whatsapp`, `share_to_instagram`, `share_to_whatsapp_failed`, `share_to_instagram_failed` each present in `res/values/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "share_to_whatsapp"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "share_to_instagram"` exits 0.

**Status:** `[ ]` not done

---

### Step 01.2 - Verify RU/UK content is real Cyrillic, not mojibake

**Files:** `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Confirm the RU/UK values rendered as proper Cyrillic (the Cyrillic-boundary trap produces mojibake that the parity check does not catch). Verify by reading the files with the Read/Grep tools, not by echoing to the console. RU uses ё where grammatically correct (CLAUDE.md §1).

**Verification:**

- `Grep` (RU) - `share_to_whatsapp` line contains Cyrillic characters (e.g. matches `[А-Яа-яЁё]`).
- `Grep` (UK) - `share_to_instagram` line contains Cyrillic characters.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `check_strings_localized.ps1` clean for both new prefixes.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- `R.string.share_to_whatsapp` / `share_to_instagram` are the `titleRes` for the Phase 02 registrations.
- `R.string.share_to_whatsapp_failed` / `share_to_instagram_failed` are the toasts for the Phase 03 send paths.

---

## Rollback Plan

Remove the four added keys from all three locales via `set-android-string.ps1 -Action remove`.
