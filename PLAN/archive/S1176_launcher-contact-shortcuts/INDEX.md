# Tactical Plan: S1176 - launcher-contact-shortcuts

**Strategic spec:** [`../S1176_launcher-contact-shortcuts.md`](../S1176_launcher-contact-shortcuts.md)
**Research inputs:** none - strategic §6 closed the scope and the data-access model on 2026-07-27
**Feature:** Contact shortcuts on the launcher desktop
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 4 / 4 done
**Last updated:** 2026-07-30

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | contact-cell-target | - | ✅ Done | 3/3 | [PHASE_01__contact-cell-target.md](PHASE_01__contact-cell-target.md) |
| 02 | contact-pick-flow | 01 | ✅ Done | 3/3 | [PHASE_02__contact-pick-flow.md](PHASE_02__contact-pick-flow.md) |
| 03 | contact-cell-rendering | 01 | ✅ Done | 2/2 | [PHASE_03__contact-cell-rendering.md](PHASE_03__contact-cell-rendering.md) |
| 04 | docs-catalog-cleanup | 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Contract shared by every phase

- **No contacts permission.** `READ_CONTACTS` and `CALL_PHONE` must not appear in any manifest when this lands - strategic §11.6, and neither is declared today. Everything comes from the system contact picker's one-time grant on the picked record.
- **Snapshot, not a live read.** What the cell stores is a snapshot taken at pin time (ADR-1). Nothing re-reads the address book afterwards; S1206 owns that later.
- **One command kind, three actions.** Profile / dial / message differ by outcome, not by nature (ADR-4). Encode one target kind with an action field so SMS and video call can be added without changing the storage format.
- **Storage format from first pin.** The encoded contact target becomes a persistence format the moment Phase 02 writes one. Do not restructure it afterwards.
- **Where code lives.** Domain and data go in `src/main`; anything drawing a desktop cell goes in `src/launcherEnabled/`. No `BuildConfig` flavor guard in `src/main`.
- **Privacy in logs.** No name, number or lookup key in any log line, including the `S1176:` device probes - log the action kind only.

---

## Pre-Implementation Blockers

None. Strategic §6 carries no open items: the owner closed both the v1 action set and the no-permission data model on 2026-07-27.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped as designed; `/skill-release`-owned. Capability recorded in `docs/ALL_FEATURES.jsonl`.
- [x] `dev/CHANGELOG.md` has an entry for the ticket.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated, and the four new public types carry a role.
- [x] New strings exist in EN/RU/UK and `scripts/check_strings_localized.ps1` exits 0.
- [x] No manifest in any flavor declares `READ_CONTACTS` or `CALL_PHONE`.
- [ ] `/spec-check S1176` returns `Verified` - waits on the device round.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1176`.

---

## Blockers Log

- none

---

## Change Log

- 2026-07-27 - Initial tactical plan authored by `/spec-tech`.
- 2026-07-30 - Phase 01 implemented and verified. Notes for whoever takes 02-04:
  - The `contact:` record is **percent-encoded per field**, then joined on `:`. Field positions are named constants and are a storage format from the first pinned cell: append, never reorder.
  - Two files outside the plan's list had to change, both because the command type is sealed: `ResolveLauncherCommandLabelUseCase` (its contact branch deliberately returns `iconRes = null`, since the cell binder draws the person's photo or monogram in Phase 03) and, earlier in the same session, the S1170 `FavoriteFile` case. Expect the same for any future command kind - that is the type doing its job.
  - `ic_phone` / `ic_message` / `ic_person` do **not** exist in this project. Do not add them for the cell: Phase 03 draws an avatar or a monogram, and a generic glyph would make every contact look alike.
  - Verified: 11 codec tests green; no `AndroidManifest.xml` in any flavor declares `READ_CONTACTS` or `CALL_PHONE`; `launcher_contact_*` strings present in en/ru/uk.
- 2026-07-30 - Phase 02 implemented. Two corrections to the written plan, both recorded in the phase file:
  - Placement is `addShortcut` at the tapped cell, not S1170's free-slot search - that rule belongs to the Settings entry point, which has no grid to point at.
  - The action is asked BEFORE the contact, because it selects the picker: `DIAL` uses the phone-number picker and so pins the exact number the user chose, instead of the app guessing among a contact's numbers.
  - Open on device: whether the picker's one-time grant reaches the contact's `Entity` sub-directory, which is where `MESSAGE` finds the rows messengers registered. `PROFILE` and `DIAL` read the picked URI itself and do not depend on it. A refused read degrades to "no channels".
- 2026-07-30 - Phases 03 and 04 implemented; ticket handed to device verification.
  - **The contact photo turned out to be unreachable under this ticket's own permission model**, so phase 03 ships the monogram alone and the photo became `S1319`. A photo needs either `READ_CONTACTS` - forbidden by §3.2 - or a copy kept at pin time, and that copy rides on the very grant-reach question the device round is there to answer. Building it first would be guessing (the project's own ADR-2 from S1189: diagnosis before functionality).
  - The monogram colour comes from the theme's container roles, not a hand-written palette, so it is correct in light and dark without anyone maintaining it; the seed is the lookup key, so a renamed contact keeps its colour.
  - The spoken form is `Call: Ivan`. `Call Ivan` would need the name in the dative in RU/UK, which no code can do for an arbitrary name.
  - A kapt failure mid-phase was a **concurrent-build artefact**, not a code defect: a sibling session's gradle run was mutating `app_v2/build/` at the same time. The identical source compiled clean once the tree was ours alone. `correctErrorTypes` was flipped to unmask it and restored to `true` afterwards.
