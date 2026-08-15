# Phase 02 - Strings for the per-slot app choice

**Strategic spec:** [`../S1036_gesture-launch-app-selection.md`](../S1036_gesture-launch-app-selection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - resources phase, independent of Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Reword the two `OPEN_APP` strings that describe the old bring-to-front meaning, and add the three new keys the inline row needs, all three locales in lockstep.

---

## Prerequisites

- [ ] Strategic §6 item 4 is `Resolved` - it is, as of 2026-08-09.
- [ ] `docs/COMMUNICATION_POLICY.md` §2 and §6 read before writing any copy.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a - key edits only |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a - key edits only |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a - key edits only |

> Edit through `scripts/utils/set-android-string.ps1` only. Hand-editing is reserved for plurals, string-arrays, comments and regrouping (CLAUDE.md "Post-Change" §3), none of which this phase does.

---

## Steps

### Step 02.1 - Reword the two `OPEN_APP` strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> `screenshot_gesture_action_open_app` currently reads "Open the main app window" and `gesture_action_explain_open_app` reads "Opens the main app window." Both describe the behaviour this ticket replaces. Reword each so the action reads as "launch the app you choose", and so the explanation states the fallback in one clause: with no app chosen, the gesture opens FastMediaSorter. Use `set-android-string.ps1 -Action set` once per key per locale with `-ExpectedOldValue` carrying the current text, so a concurrent edit fails loudly instead of being overwritten. Check the result against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist.

**Why:**

Strategic ADR-1 keeps the single `OPEN_APP` entry and redefines its meaning, so the picker entry the user reads while choosing it must describe the new meaning; §11 criterion 1 states the action item must read as launching the chosen app.

**Verification:**

- `Grep` - `main app window` returns zero hits in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screenshot_gesture_action_open_app"` exits 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "gesture_action_explain_open_app"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 4\4 PASS. `main app window` now returns 0 occurrences across `app_v2/src/main/res`; both `check_strings_localized.ps1` runs exit 0 with all keys present in en/ru/uk. EN reads "Launch a chosen app" / "Opens the app you pick for this gesture. With no app picked, opens FastMediaSorter."; RU and UK adapted naturally, keeping the existing RU/UK term for an app (`программу` / `програму`) that `app_picker_title` already uses. All six writes went through `set-android-string.ps1 -Action set` with `-ExpectedOldValue`, so a concurrent edit would have failed loudly. §6 checklist: no raw exception text, no bare "Are you sure?", no "completed successfully", the explanation states the fallback rather than dead-ending. Dev log recorded.

---

### Step 02.2 - Add the three inline-row keys across EN, RU and UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add three keys with one `set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>` call each, so parity is enforced in a single lockstep write: a label for the inline row that asks the user to pick an app, a value shown when no app is chosen for that slot, and an action label for clearing the choice. Do not invent a new "choose an app" dialog title - `app_picker_title` already exists and Phase 03 reuses it. Check each string against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §3.2 "Локализация" makes EN/RU/UK mandatory for exactly these three pieces of copy - the "укажите приложение" label, the reset action and the "приложение не выбрано" state - and §3.2 "Доступность" requires the chosen app to be conveyed as text rather than as an icon alone, which is what the label and the empty-state value carry.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "gesture_slot_app"` exits 0.
- `Grep` - each new key appears exactly once in each of the three `strings.xml` files.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Three keys added through `set-android-string.ps1 -Action add` (one lockstep call each, parity enforced at write time): `gesture_slot_app_label`, `gesture_slot_app_none`, `gesture_slot_app_reset`. `check_strings_localized.ps1 -KeyPrefix "gesture_slot_app"` reports 3 keys, all present in en/ru/uk, exit 0. `app_picker_title` was left alone and is not duplicated. §6 checklist: the not-chosen value carries an invitation to act ("tap to pick an app") rather than being a dead end, which is what the empty-state line of the checklist asks for. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0 (code and resources, BUILD SUCCESSFUL in 11s). `fc` rather than a full build because this phase changed only resources, and the ladder prefers the cheapest rung that proves the change.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "gesture_"` exits 0 - 53 keys, all present in en/ru/uk (run inside the closure's strings-audit gate).
- [x] Dev log entry added - one row naming the whole three-file set, through `post-change.ps1` (`post-change: PASS`).

### Phase-boundary audit (2026-08-09)

Not applicable beyond the tone gate: `Files Touched` is three resource files with no code surface, so Layers 1-4 of the audit protocol have nothing to inspect. The checks that do apply ran as gates - locale parity, string-format placeholders (delta 0) and the neuroslop dimensions (all at or below baseline).

---

## Handoff Notes to Next Phase

Phases 03 and 04 may reference the new keys directly; no further string work belongs to them. `app_picker_title` stays the picker's own title and is not duplicated.

---

## Rollback Plan

Revert the phase commit - resource-only change, no schema and no behaviour.
