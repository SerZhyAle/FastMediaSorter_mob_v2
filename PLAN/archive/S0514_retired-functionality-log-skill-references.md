# S0514 - Purge retired FUNCTIONALITY.log references from skill files

**Status:** Archived
**Priority:** 45
**Date:** 2026-06-18
**Tier:** 2 - Easy (ad-hoc)
**Origin:** parked by android-rd-specialist during the spec-pipeline consolidation pass (2026-06-18)

---

## 0. Inbox (raw capture)

`scripts/add_to_functionality_log.ps1` is a hard-erroring retired stub since S0489 (2026-06-17): it does `Write-Error "<retired S0489>"; exit 1` before any append. Several skill files still instruct calling it or grepping the dead `dev/FUNCTIONALITY.log`. If those branches execute they hard-fail mid-pipeline.

Stale references found (`grep FUNCTIONALITY\.log|add_to_functionality_log` over `.claude/commands/`):

- `skill-fix-release.md:278` - `.\scripts\add_to_functionality_log.ps1 -Id $SPEC_ID -Op FIX ..` in the release fix pipeline. **Hard break if reached.**
- `spec-fix.md:93` - individual-call fallback uses `add_to_functionality_log.ps1 -Op FIX`. Hard break on the fallback path (primary path uses `close-and-log.ps1`, so usually masked).
- `spec-arc.md:18` and `spec-arc.md:77` - `--removes-functionality` emits a `DELETE` line via `add_to_functionality_log.ps1`. Hard break when the flag is used.
- `skill-fix.md:84-85` and `ns.md:11` - "do NOT touch `dev/FUNCTIONALITY.log` / do NOT run `add_to_functionality_log.ps1`". Harmless but stale (name a dead artifact).

Already fixed inline during the consolidation pass (out of this ticket's scope): `spec-all.md:288` (`[FUNC_LOG MISSED]` grep check -> now greps `docs/ALL_FEATURES.jsonl`).

## Problem

The retirement (S0489) migrated the data and replaced the writer with `scripts/all_features/add.ps1` (inventory `docs/ALL_FEATURES.jsonl`), but did not sweep the skill prose that drives the release/fix/archive pipelines. The FIX and DELETE operations have no 1:1 in the new inventory model (active/removed status, no per-event op), so this is not a mechanical find-replace - each call site needs a semantic mapping decision.

## Goal (RU)

Вычистить все ссылки на ретайрнутый `add_to_functionality_log.ps1` и `dev/FUNCTIONALITY.log` из skill-файлов; заменить на корректную работу с `docs/ALL_FEATURES.jsonl` через `scripts/all_features/add.ps1`, продумав отображение операций FIX/DELETE на модель инвентаря (status active/removed).

## Acceptance criteria

1. Нет живых вызовов `add_to_functionality_log.ps1` и инструкций писать/грепать `dev/FUNCTIONALITY.log` в `.claude/commands/` (описательные «retired/replaced» упоминания допустимы).
2. Все 5 сайтов (skill-fix-release, spec-fix, spec-arc ×2, skill-fix, ns) выровнены на `docs/ALL_FEATURES.jsonl` / `scripts/all_features/add.ps1` с корректным маппингом FIX (active-upsert либо skip) и DELETE (removed).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0489 (FUNCTIONALITY.log retirement, Archived).

## Открытые вопросы (решены)

- **OP-MAP** (resolved) В инвентаре нет события FIX. Канон: pure bug fix не пишется в инвентарь (покрыт dev-log + git); если фикс меняет видимое поведение/описание существующей capability - upsert её записи как `active`. Уже реализовано в `close-and-log.ps1 -FuncOp FIX` -> `all_features/add.ps1` (active); skill-проза выровнена на этот механизм.
- **OP-DELETE** (resolved) `spec-arc --removes-functionality` -> `close-and-log.ps1 -FuncOp DELETE` -> `all_features/add.ps1 -Status removed` для `<area>.<feature>`. Уже реализовано; описание флага и individual-call fallback выровнены.

## Связь

- S0489 (features-allfeatures-split, Archived) - выполнил миграцию данных и ретайр стаба; этот тикет добивает осиротевшие ссылки в скиллах.

---

## Phase 01 - Align skill prose to the ALL_FEATURES inventory

**Files (`.claude/commands/`):** `skill-fix-release.md`, `spec-fix.md`, `spec-arc.md`, `skill-fix.md`, `ns.md`

**Done:**

- `skill-fix-release.md` Step 13a: retired `add_to_functionality_log.ps1 -Op FIX` (hard break) -> `scripts/all_features/add.ps1` active-upsert, only when a shipped capability changed; pure fixes captured by the Step 13 dev changelog + git.
- `spec-fix.md` individual-call fallback: dropped the retired FIX call; inventory upsert only when a capability's user-visible behaviour changed.
- `spec-arc.md` flag description + individual-call fallback: DELETE now via `all_features/add.ps1 -Status removed` (primary path already used `close-and-log.ps1 -FuncOp DELETE`).
- `skill-fix.md` + `ns.md`: stale "do NOT touch `dev/FUNCTIONALITY.log` / `add_to_functionality_log.ps1`" -> `docs/ALL_FEATURES.jsonl` / `scripts/all_features/add.ps1`.

**Status:** `[x]` done

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (Simple)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 2 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 0

- [PASS §1] `grep -rE 'add_to_functionality_log|dev/FUNCTIONALITY\.log' .claude/commands/` returns only descriptive "retired/replaced" prose; zero live calls or write/grep instructions.
- [PASS §2] All 5 sites edited; FIX -> active-upsert/skip, DELETE -> `-Status removed`, mapping consistent with the already-shipped `close-and-log.ps1 -FuncOp` behaviour (`-FuncOp DELETE` -> removed, else active).
- Semantic forks OP-MAP/OP-DELETE resolved from existing tooling (`close-and-log.ps1` + `all_features/add.ps1`), not a new design. Docs-only change - no build/catalog impact. Zero `Timber.d("S0514:` tags.
