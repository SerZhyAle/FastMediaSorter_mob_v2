# Tactical Plan: S0999 - Two-column "Send file to.." settings list

**Ticket:** S0999
**Status:** Tactical
**Strategic spec:** `PLAN/S0999_settings-send-commands-two-column.md` (§5 approach + §6 owner-resolved decisions are authoritative - all 4 questions decided via quiz)

## Goal (RU)

Список тогглов ShareTarget группы «Send file to..» (таб Playback) раскладывается в 2 колонки в ландшафте и в портрете от `sw600dp`; узкий портрет остаётся одноколоночным. Число колонок - из `@integer`-ресурса (готово к 3+ бакетам без правки Kotlin). Распределение - column-major balanced.

## Decisions (from strategic §6, do not re-litigate)

- Columns source of truth: `@integer/settings_send_commands_columns` - `values/`=1, `values-land/`=2, `values-sw600dp/`=2. No `values-sw600dp-land/` needed (sw shadows land; both = 2).
- Max 2 columns. Column-major balanced (left column longer by 1 when odd). `<=1` target -> 1 full-width column (current behaviour).
- No new strings; toggles/data/async label-icon loading unchanged.

## Phase overview

| Phase | Title | Status |
|-------|-------|--------|
| 01 | Columns resource + horizontal container + column-major population | Pending |

## Blockers

- None to implement (all decisions resolved from spec §6).
- **Device-verification (F3 terminal):** landscape shows 2 columns / narrow portrait 1 / rotation switches count without losing toggle state / D-pad + TalkBack focus order logical are visual+interaction criteria (§11.1-11.5) -> ticket lands `BlockNeedUserTest`; `/spec-test-device` drains when a device is attached.

## Completion gate

- `standard debug` builds green (detekt-clean).
- One `Timber.d("S0999: …")` probe at the group-rebuild entry (present only while BlockNeedUserTest).
- `columns=1` path byte-for-byte equivalent to today (no narrow-portrait regression).
- `docs/ALL_FEATURES.jsonl` ADD deferred to Verified (device-confirmed).
