# Стратегическая спецификация: S0092 — Bugfix: SFTP range-read retry offset regression / overflow

**Ticket:** S0092
**Status:** Implemented
**Implemented date:** 2026-05-05
**Priority:** 92
**Date:** 2026-05-05
**Tier:** 1 — Quick Win
**Roadmap entry:** Ad-hoc — field logs 2026-05-05, Standard/core network playback blocker
**Tactical spec:** [`PLAN/S0092_bugfix-sftp-range-read-retry-overflow/INDEX.md`](S0092_bugfix-sftp-range-read-retry-overflow/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

SFTP playback и thumbnail extraction падают на одном и том же shared range-read substrate. Primary-path открывает поток сразу с нужного offset, а retry-path деградирует до полного открытия файла с последующим `skip(offset)`. Логи фиксируют ошибки именно в retry-сценарии: `SftpDataSource: Error opening SFTP file`, `SFTP range read failed`, `Error reading from network`, `Playback error — errorCode=3003`, `Playback error — errorCode=2000`, включая `ArrayIndexOutOfBoundsException` на больших смещениях.

---

## 2. Цели

1. Retry-path для SFTP range reads сохраняет ту же offset-semantics, что и primary-path.
2. Shared fix улучшает и playback, и thumbnail extraction без разветвления поведения.
3. Изменение остаётся локальным и безопасным для релизного окна.

**Non-goals:**

- Полная переработка SFTP connection pool.
- Новый buffering strategy для ExoPlayer.
- Полная стабилизация всех возможных сетевых отказов.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исправление должно быть минимальным и ориентированным на release-blocker.
2. Не нужно добавлять новый пользовательский UI.

### 3.2 Жёсткие ограничения

- **Flavor:** общий SFTP-код влияет на все flavor, где SFTP включён.
- **API level:** без platform forks.
- **Wear OS:** не затрагивается.
- **Производительность:** нельзя вносить дополнительный полный линейный проход по файлу на retry.
- **Совместимость данных:** нет.

---

## 4. Контекст текущей архитектуры

SFTP playback path и thumbnail path используют общий `readFileBytesRange()` helper. В primary path helper использует прямой offset-open, а в retry path — другой алгоритм с `skip(offset)`. Это нарушает инвариант одинаковой семантики между первой и второй попыткой и делает retry не просто повтором, а отдельным более хрупким кодовым путём.

---

## 5. Предлагаемый подход

Сделать retry path эквивалентным primary path: повторное чтение тоже должно стартовать напрямую с нужного offset, без `skip(offset)`. Это устраняет observed overflow/failure profile и не требует изменения внешнего API helper'а.

### 5.1 Основные столпы

**Retry-path parity.** Повторная попытка обязана повторять primary-path semantics, а не жить по отдельным правилам.

### 5.2 Потоки данных и событий

Не меняются caller contracts. Меняется только внутренняя реализация retry-ветки shared helper.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет для quick-fix фазы.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Наблюдаемый failure имеет ещё одну причину помимо retry-path | Средняя | Симптом ослабнет, но не исчезнет полностью | Узкая валидация и сохранение спек-потока для дальнейших итераций |
| Изменение заденет не только playback, но и thumbnail path | Низкая | Потребуется дополнительная регрессия | Shared helper already intended for both paths |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES*.md` — это исправление надёжности существующей SFTP functionality.

---

## 9. Архитектурные решения (ADR)

ADR нет — локальный bugfix shared helper'а.

---

## 10. Связи с другими спеками

- `S0047` `bugfix-sftp-pool-broken-channel`
- `S0051` `bugfix-network-datasource-pause-cancel`
- `S0066` / `S0067` network transient failure classification / invalidation
- `S0085` `enh-sftp-scan-performance`

---

## 11. Критерии готовности (strategic-level)

1. Retry path больше не использует `skip(offset)`.
2. Сборка проходит без новых ошибок.
3. Touched helper продолжает удовлетворять current callers без API changes.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: [`PLAN/S0092_bugfix-sftp-range-read-retry-overflow/INDEX.md`](S0092_bugfix-sftp-range-read-retry-overflow/INDEX.md)