# Phase 03 - Contact cell rendering

**Strategic spec:** [`../S1176_launcher-contact-shortcuts.md`](../S1176_launcher-contact-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Draw a contact cell as a person - avatar or monogram plus name - and give it a spoken description that names both the person and the action.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the target model exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 500 |
| `app_v2/src/launcherEnabled/res/drawable/bg_contact_monogram.xml` | New | ≤ 40 |
| `app_v2/src/main/res/values{,-ru,-uk}/strings.xml` | Modified | n/a |

---

## Steps

### Step 03.1 - Avatar with a monogram fallback

**Files:** `LauncherCellViewBinder.kt`, `bg_contact_monogram.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Bind a `contact:` cell to the person's photo, decoded at the cell's display size and cleared when the view detaches - the project's image-loading contract, not a raw decode. With no photo, draw a monogram from the display name over a shape whose colour is derived deterministically from the contact so the same person always looks the same. Colours come from `?attr/` or `@color/`; no hardcoded hex in the drawable (CLAUDE.md Rule 19). The name goes under the avatar the same way other shortcut cells label themselves.

**Verification:**

- ~~`Grep` - the project's image loader is used with an explicit display-size override.~~ **Not implementable - see below.**
- ~~`Grep` - a clear-on-detach call is present for that target.~~ **Not implementable - see below.**
- `Grep` - `="#` returns zero hits in `bg_contact_monogram.xml`.

**Status:** `[x]` done - monogram shipped, photo deferred

### The photo cannot be drawn under this ticket's own permission model (2026-07-30)

The step assumes the person's photo is available at render time. It is not, and the reason is the feature's founding constraint rather than an implementation gap.

A contact photo lives behind `ContactsContract`, and reading it needs `READ_CONTACTS` - the permission strategic §3.2 forbids. The only door this feature has is the one-time grant the system picker attaches to the picked record, and that grant dies with the pick flow. Drawing a photo on every desktop render would therefore need either the permission (forbidden) or a copy of the image kept at pin time (a file store, a stable key, and an orphan sweep - none of which this plan anticipated, and none of which exists).

Worse, capturing the photo at pin time rides on **the same unverified assumption as the messaging channels**: whether the picker's grant reaches a sub-path of the picked record. That fact is unknown until the device round. Building a photo store on top of it would be guessing, and if the answer is "no" every line of it is dead code that never once produces a picture.

**So this phase ships the monogram alone, and the device round answers whether the photo is even reachable** - the message-channel outcome in the `S1176:` probe tests exactly the same grant reach, so no extra probe is needed. That ordering is this project's own ADR-2 from S1189: diagnosis before functionality, because without facts from the device any change is a guess.

The monogram is not a placeholder for a missing feature. It satisfies goal §2.4's "recognisable": initials, a colour stable per person, and the name always under it - and it never becomes the sole difference between two contacts, which §3.2 requires and a photo alone would not guarantee.

Follow-up, gated on the device answer: `S1319`.

---

### Step 03.2 - Spoken description names the person and the action

**Files:** `LauncherCellViewBinder.kt`, `strings.xml` x3
**Depends on:** Step 03.1

**Prompt for developer:**

> Set a content description built from the action and the display name - "call Ivan", not "Ivan" (strategic §11.5). Add one parameterised string per action across EN/RU/UK in a single `set-android-string.ps1 -Action add` call; use a placeholder for the name so translators keep word order. Check the copy against `docs/COMMUNICATION_POLICY.md` §2 and §6. The monogram colour must not be the only thing distinguishing two contacts - the name is always present as text.

**Verification:**

- `Grep` - `contentDescription` set for contact cells from a parameterised string, not concatenated in code.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"` exits 0.
- Strings pass the `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

The spoken form is `Call: Ivan`, not `Call Ivan`. Russian and Ukrainian need the name in the dative there, and no code can decline an arbitrary contact name - so every locale gets a shape that stays correct whatever the name is, instead of one that reads naturally only in English. The string is built where the action is known (`ResolveLauncherCommandLabelUseCase`) and travels to the binder on the visual, so nothing is concatenated at the view layer.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `standard` and `noLegal`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `LauncherCellViewBinder` stays within its line budget.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Phase-boundary audit (2026-07-30)

The named risk - a stale image target in a recycled cell - **cannot arise here**, and not because it was handled: there is no image loading at all. The monogram is a `TextView` whose text, tint and text colour are all set on every bind, and the desktop rebuilds its views outright rather than recycling them (ADR-9), so no field survives one cell into the next.

Checked instead: the icon and the disc share one 44dp box and exactly one of them is visible after every bind, so a cell can never stack a glyph on initials; the colour index is remainder-mapped rather than `absoluteValue`, which would still be negative for `Int.MIN_VALUE` and would index off the front of the list; the seed is the lookup key, so a renamed contact keeps its colour; and the spoken label falls back to the caption for every non-contact cell, leaving the other seven kinds untouched.

---

## Handoff Notes to Next Phase

Final feature phase - Phase 04 records the capability and closes out.

---

## Rollback Plan

Revert the phase commit; cells fall back to the generic shortcut appearance and still work.
