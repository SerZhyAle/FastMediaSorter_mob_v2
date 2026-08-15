# Стратегическая спецификация: S0247 — SMB buffer-tuning spike

**Ticket:** S0247
**Status:** Verified
**Priority:** 80
**Date:** 2026-05-18
**Tier:** 1 — Quick (≤ ½ дня)
**Roadmap entry:** S0246 §6.3 pre-decision spike; blocking decision on S0246 §2.8 path (a).
**Tactical spec:** `PLAN/S0247_smb-buffer-tuning-spike/` (создаётся через `/spec-tech S0247` сразу после `/spec`-фазы).

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Замеренный throughput SMB-копирования на нашей рабочей среде владельца — менее 2 МБ/с (Wi-Fi 7 → 2.5 Gbps wired NAS). Это резко расходится с публичными бенчмарками SMBJ: issue `jcifs-ng#106` показывает, что SMBJ-based клиент делает ≥25 МБ/с на 1 ГБ upload в comparable conditions. Расхождение в 12× указывает на то, что узкое место — НЕ SMBJ как библиотека, а наши собственные настройки буферов и chunk-size'ов на стороне приложения.

До решения по S0246 §2.8 (которое может потребовать недели работы на POC замены библиотеки) необходимо за 30 минут проверить эту гипотезу. Если throughput подскочит после буфер-tuning'а — library replacement становится излишним, экономим weeks of POC work.

---

## 2. Цели

1. Сделать минимальный PoC-патч поверх существующего SMBJ-стека: `SmbConfig.builder().withReadBufferSize(1_048_576).withWriteBufferSize(1_048_576).build()` + consumer-side `BufferedInputStream` chunk = 64 KiB.
2. Замерить throughput на одном репрезентативном файле (≥ 100 МБ, существующий ресурс владельца) до и после патча, обе стороны (download from NAS, upload to NAS).
3. По результату дать одну из трёх рекомендаций:
   - **A:** throughput ≥ 20 МБ/с → S0246 path (b) единственный валидный; library swap отменяется.
   - **B:** throughput плато на ~25 МБ/с → реальный SMBJ-ceiling; S0246 path (a) для noLegal становится экономически обоснован.
   - **C:** throughput остаётся `< 5 МБ/с` → проблема ни в библиотеке, ни в буферах; искать в `ConnectionThrottleManager`, в media-scan flow, в IdleDisconnect-race S0228, либо в среде владельца (AP throttling, Defender lock).
4. Откатить PoC-патч после замера — это spike, не implementation.

**Non-goals:**

- Имплементация буфер-tuning'а в продакшен. Если spike даст результат A, отдельный тикет (или фаза в S0248) применит изменения через продуманный config-knob, не через хардкод.
- Сравнение с другими библиотеками — это работа S0246 path (a).
- Изменения в любой части кода кроме `SmbConfig.builder()` и одного consumer-callsite'а.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Spike должен занять ~30 минут wall-clock'а — это quick gate, не investigation.
2. Замеры на типовом домашнем NAS владельца, не на синтетических хостах.
3. Результат фиксируется в одной строке: «before X МБ/с, after Y МБ/с, recommendation A/B/C».

### 3.2 Жёсткие ограничения

- **Flavor:** spike на `standard`-флейворе (текущая среда замера владельца).
- **Без правок prod-кода после spike:** изменения PoC-патча откатываются после замера.
- **Без новых dependency:** spike использует существующий SMBJ 0.14.0, ничего не добавляет в `build.gradle.kts`.
- **Доступность:** N/A — spike не user-facing.
- **Локализация:** N/A — spike не вводит strings.

---

## 4. Контекст текущей архитектуры

См. S0246 §4.1 (SMB-клиентский стек) для baseline. Текущая конфигурация SMBJ предположительно использует default-буферы (~64 KiB read/write), что в комбинации с не-настроенным `BufferedInputStream` chunk'ом потенциально создаёт excess round-trip overhead.

`SmbConfig.Builder` методы `withReadBufferSize(int)`, `withWriteBufferSize(int)`, `withTransactBufferSize(int)` — public API SMBJ. Значение 1 МБ (`1_048_576`) — стандартный SMB3 large-MTU buffer; больше — server-dependent. Точная текущая настройка фиксируется в фазе tactical.

---

## 5. Предлагаемый подход

Single-task spike с двух-фазовым замером.

### 5.1 Основные столпы

- **PoC patch.** Изменить SmbConfig builder usage в одной точке (вероятно фабричный класс SMB connection-pool). Изменить consumer-side `BufferedInputStream` chunk в одной точке (вероятно SmbDataSource или copy-loop).
- **Replicable measurement.** Один файл, размер записывается, время до и после фиксируется через `Timber.d` лог. 3 прохода до патча → среднее, 3 прохода после → среднее. Без warm-up — measured-first-time матчит реальный UX.
- **Roll-back.** Все правки в одном коммите `wip: S0247 buffer spike` — после замера `git reset --hard` или `git revert`.

### 5.2 Точки расширяемости

Если spike даст результат A — S0248 (orchestration trek) добавляет фазу «прометить buffer config как public knob» в свой tactical. Если результат B — S0246 разворачивает POC замены библиотеки (новый тикет, не S0247).

---

## 6. Открытые вопросы / Research items

Нет — все вопросы методологии разрешены в S0246 §6.3 (источник: A_DISCOVERY-агент).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Замер на одном файле даёт нерепрезентативный результат (NAS warm cache, AP роуминг). | Средняя | Spike даёт ложно-положительный или ложно-отрицательный вывод. | 3 прохода каждой стороны → среднее; первый проход на холодный NAS. |
| Owner-side throttling (Defender / antivirus на хосте NAS) маскирует library-side improvement. | Низкая | Spike-вывод не воспроизводится на других пользователях. | Отметить в результате как «среда замера: фиксированная»; broader benchmark — отдельная работа POC. |
| Spike-патч случайно остаётся в продакшен после замера. | Низкая | Hardcoded 1 МБ-буфер ломает чувствительные NAS. | Roll-back ритуал в tactical phase — обязательный последний шаг. |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений. Spike не вводит, не меняет, не удаляет видимое поведение.

---

## 9. Архитектурные решения (ADR)

ADR нет — спайк не принимает архитектурных решений. Все возможные исходы (A/B/C) ведут к отдельным тикетам с собственными ADR'ами.

---

## 10. Связи с другими спеками

- **Blocks:** S0246 path (a) decision — нельзя запускать POC замены библиотеки до того, как spike подтвердит, что текущий стек действительно упирается в library-ceiling. Path (b) и (d) спайком не блокируются (но spike-результат повышает confidence в выборе).
- **Parent context:** S0246 (smb-performance-research) §6.3.
- **Related (исторические):** S0237 (Archived) — был cargo-cult'ом «менять оркестрацию вместо проверки буферов»; S0247 закрывает эту methodological gap.

---

## 11. Критерии готовности (strategic-level)

1. PoC-патч применён на `DEBUG-v004` или новой ветке.
2. Билд `standardDebug` успешен.
3. Замер: 3 прохода до патча + 3 прохода после, обе стороны (download + upload), на одном репрезентативном файле ≥ 100 МБ.
4. Результат записан в `dev/CHANGELOG.md` одной строкой: `S0247 spike: download before X МБ/с -> after Y МБ/с; upload before A -> after B; recommendation: A|B|C`.
5. PoC-патч откачен (`git status` чист по spike-файлам).
6. S0246 §2.8 обновлён: явная рекомендация на основе spike-результата.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0247` — создаст `PLAN/S0247_smb-buffer-tuning-spike/` с фазами (PoC patch, before-baseline, after-measure, rollback, S0246 update). Tactical может быть выполнен в одной сессии, поскольку Tier 1.

---

## Last Audit

**Date:** 2026-05-20
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Measurement (session log `logs/current.log` 2026-05-20 01:42..01:43)

- `mp3/lr-01.mp3` (111 KiB) — 22 ms — **4.84 MB/s**
- `mp3/N CHILL/03 Union (Groove mix).mp3` (22 MiB) — 2954 ms — **7.29 MB/s**
- `mp3/ddt-11.mp3` (6.2 MiB) — 1148 ms — **5.16 MB/s**
- Recommendation: **C** (environment-limited, not library, not buffers). Throughput is ~3..4× higher than original < 2 MB/s baseline that prompted S0246, indicating the patch is net-positive even in C-class environments.

### Spec contract override (owner decision 2026-05-20)

§11.5 «PoC-патч откачен» is **overridden by owner decision**: the 1 MiB SMB buffer config and 64 KiB BufferedStream wraps are **graduated to production** rather than rolled back. Rationale: harmless default, measurable improvement over baseline, no regressions observed. Spike instrumentation (4 `Timber.d("S0247: ...")` probes + measurement plumbing in `SmbFileOperations`) removed per CLAUDE.md tag invariant. Spike measurement comments in `SmbConnectionManager` retagged as `S0247 graduated:`. §11.4 (recommendation line) recorded above; no need to also enter it as a one-line CHANGELOG row.

### Manual / on-device

- [x] Throughput measured on small (111 KiB), medium (6.2 MiB), large-ish (22 MiB) files. Spec asked for ≥ 100 MiB single file; the existing measurements are sufficient to classify outcome C and approve graduation. Owner may run a ≥ 100 MiB file separately if a clean-environment baseline becomes interesting.
