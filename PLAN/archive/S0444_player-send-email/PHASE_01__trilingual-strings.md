# Phase 01 - Trilingual strings

**Strategic spec:** [`../S0444_player-send-email.md`](../S0444_player-send-email.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase (produces `R.string.share_to_email`, consumed by Phase 02)
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 1
**Started:** -
**Completed:** -

---

## Objective

Add the single user-facing string for the Email command/toggle in EN/RU/UK lockstep. One key serves both the settings toggle title and the menu command title (the S0452 toggle reads `target.titleRes`; the menu item reads the same `titleRes`), mirroring how `share_to_telegram` serves both surfaces.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified (add key) | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified (add key) | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified (add key) | - |

---

## Steps

### Step 01.1 - Add `share_to_email` across EN/RU/UK

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one string key `share_to_email` to all three locales in lockstep. Key lives in the default `strings.xml` next to `share_to_telegram` (line ~2418), so pass no `-File` (default `strings.xml`). EN = "Send to Email". RU and UK follow `share_to_telegram`'s register ("Send to Telegram" -> "Отправить в Telegram" / "Надіслати в Telegram"); use "Отправить в Email" (RU) and "Надіслати в Email" (UK).
>
> Cyrillic caveat (memory: bash->pwsh boundary mojibakes RU/UK literals): do NOT pass Cyrillic values as arguments through the Bash tool. Either (a) invoke the PowerShell tool directly with the literals inline, or (b) author a tiny UTF-8 `.ps1` via Write and run it. Use:
>
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key share_to_email -En "Send to Email" -Ru "Отправить в Email" -Uk "Надіслати в Email"`
>
> Then verify the written bytes via the Grep tool / Read, NOT by echoing to the console.

**Verification:**

- `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key share_to_email` exit 0 (present in all three locales).
- `Grep -n "share_to_email"` over `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` - one hit each; Cyrillic intact (no `Ð`/`Ñ` mojibake).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "share_to_email"` exit 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] `check_strings_localized.ps1 -KeyPrefix "share_to_email"` exit 0.
- [ ] `R.string.share_to_email` now resolves - the Phase 02 registration that references it will compile (`.\a.ps1 fk` PASS when Phase 02 runs).

---

## Handoff Notes to Next Phase

- `R.string.share_to_email` is the title for both the (already-rendering) settings toggle and the Phase 03 menu command.

---

## Rollback Plan

`set-android-string.ps1 -Action remove -Key share_to_email` removes it from all locales.
