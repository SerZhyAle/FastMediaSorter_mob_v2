# Phase 01 - Glossary term

**Strategic spec:** [`../S1919_launcher-naming-and-icon.md`](../S1919_launcher-naming-and-icon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 1 / 1
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

Record the launcher / desktop terminology in the published glossary in all three languages, so every later text edit copies one agreed wording instead of choosing its own.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - all three are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/V2_TERMS.md` | Modified | ≤ 40 |
| `docs/V2_TERMS_RU.md` | Modified | ≤ 40 |
| `docs/V2_TERMS_UK.md` | Modified | ≤ 40 |

---

## Steps

### Step 01.1 - Add the Launcher and Desktop entries to the glossary in three languages

**Files:** `docs/V2_TERMS.md`, `docs/V2_TERMS_RU.md`, `docs/V2_TERMS_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two entries to the "Основные термины" / main-terms list of each of the three glossary files, keeping each file's existing bullet form (`- **Term** - definition`).
>
> `Launcher` - the app replacing the Android home screen. RU: «**Лаунчер** - режим, в котором приложение заменяет домашний экран Android; кнопка «Домой» открывает рабочий стол приложения». UK: «**Лаунчер** - режим, у якому застосунок замінює домашній екран Android; кнопка «Додому» відкриває робочий стіл застосунку».
>
> `Desktop` - the launcher's own screen of cells. RU: «**Рабочий стол** - экран лаунчера с ячейками: ярлыками и гаджетами. Часть лаунчера, а не его синоним». UK: «**Робочий стіл** - екран лаунчера з комірками: ярликами та гаджетами. Частина лаунчера, а не його синонім».
>
> Both entries go in every file, including the English one, where the pair is Launcher / Desktop with the same distinction. Do not touch any other line.

**Why:**

Strategic ADR-3 makes the glossary the single home of this wording, because the ticket closes but texts keep being written, and without a published record the next author picks the word by taste again.

**Verification:**

- `Grep` - `Лаунчер` matches in `docs/V2_TERMS_RU.md` and in `docs/V2_TERMS_UK.md`.
- `Grep` - `Launcher` matches in `docs/V2_TERMS.md`.
- `Grep` - `Рабочий стол` matches in `docs/V2_TERMS_RU.md`; `Робочий стіл` matches in `docs/V2_TERMS_UK.md`.
- Each of the three files still carries its original `permalink:` front-matter line unchanged.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - Launcher and Desktop entries added to all three glossaries; grep confirms each term once per file, all three permalinks unchanged

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, documentation only.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The exact RU and UK wording for "лаунчер" and "рабочий стол" is now fixed in `docs/V2_TERMS_RU.md` / `_UK.md`. Phases 02 and 04 copy it rather than paraphrasing.

---

## Rollback Plan

Revert the phase commit - documentation only, no user data and no build surface touched.
