# Стратегическая спецификация: S0229 — Стабилизация browse-side SMB audio metadata path

**Ticket:** S0229
**Status:** BlockNeedUserTest
<!-- auto-approved by /spec-all — 2026-05-16 -->
**Priority:** 75
**Date:** 2026-05-16
**Tier:** 3 — Moderate (ad-hoc, bugfix)
**Roadmap entry:** Ad-hoc — анализ `logs/current.log` 2026-05-16 (`EOFException`, `Handler on a dead thread`, frame spikes и noisy partial-read path в SMB audio metadata browse flow)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Во время browse-сценария с удалённой SMB-папкой и аудиофайлами приложение в idle-after-scroll режиме пытается подгрузить метаданные для видимых рядов. В `logs/current.log` от 2026-05-16 этот path сопровождается серией `XingSeeker data size mismatch`, множественными `Media3 MetadataRetriever expected miss on 65536 bytes`, одним warning-grade `EOFException`, `Handler on a dead thread` и заметными frame spikes (`158 ms`, `1205 ms`). Это уже не просто «чуть шумный лог» — это сигнал, что browse-side metadata enrichment конкурирует за стабильность с остальной UI/playback цепочкой.

Пользовательская поверхность может выглядеть как подлагивания списка, задержка обновления artist/title, sporadic log-noise и нестабильное поведение рядом с playback/reconnect path. Поскольку метаданные в browse — best-effort enhancement, такой объём риска и шума для него несоразмерен.

---

## 2. Цели

1. Browse-side SMB audio metadata enrichment не производит warning-grade ошибок для типовых partial-read исходов, если они ожидаемы и не влияют на пользователя.
2. Нормальный browse-сценарий не генерирует `Handler on a dead thread` в metadata path.
3. Подгрузка метаданных для видимых SMB audio rows не даёт заметного jank burst на уровне текущих `158 ms / 1205 ms` spikes.
4. Metadata path остаётся best-effort: при нестабильном формате или transport-состоянии он может тихо деградировать в skip/cache-only режим вместо каскада предупреждений.

**Non-goals:**

- Гарантировать полные embedded tags для каждого удалённого аудиоформата.
- Переписывать весь browse UI или менять пользовательские тексты.
- Решать общую transport-race проблему SMB idle timeout — это вынесено в S0228.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Browse metadata path должен оставаться улучшением качества списка, а не обязательным условием корректной работы экрана.
2. Ожидаемый шум partial-read сценариев должен оставаться на debug-уровне или ниже.
3. Если для устойчивости нужен более дешёвый parser/fallback, предпочтение отдаётся стабильности и scroll responsiveness, а не максимальной полноте тегов.

### 3.2 Жёсткие ограничения

- **Flavor:** все source set'ы, где доступен SMB browse + audio metadata enrichment.
- **API level:** без новой Android API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** metadata path не должен добавлять заметную задержку в scroll-idle recovery и не должен конкурировать с playback за критические ресурсы.
- **Совместимость данных:** формат уже сохранённых metadata cache entry не меняется.
- **Локализация:** пользовательские строки не добавляются.

---

## 4. Контекст текущей архитектуры

Browse-экран уже умеет после остановки scroll запускать enrichment для видимых удалённых audio rows. Эта цепочка читает partial bytes удалённого файла, пытается извлечь теги и затем частично перебиндить соответствующие элементы списка. По смыслу это background enhancement. По факту же partial-read path способен активировать достаточно тяжёлый extractor/lifecycle маршрут, который начинает вести себя как мини-playback without playback contract.

Ключевой architectural mismatch здесь в том, что browse metadata path допускает более агрессивную цену за попытку, чем оправдано его пользовательской ценностью. Если формат нестабилен на partial read, правильная реакция browse-path — рано отказаться, а не тянуть UI в expensive/noisy branch.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Столп A — явная политика partial-read outcomes.** Для browse metadata path нужно чётко отделить «ожидаемый miss на усечённом заголовке» от действительно аномального сбоя. Без этого warning budget размывается, а случайный EOF выглядит как почти-фатальная ошибка.

**Столп B — изоляция browse metadata от playback-centric extractor semantics.** Если текущий path использует слишком тяжёлый extractor/lifecycle маршрут, browse-case должен перейти на более дешёвый и self-contained вариант или на стабильный fallback/skip policy.

**Столп C — backpressure и graceful degradation.** Concurrent metadata tasks должны уметь снижать агрессию при low-memory/jank/transport instability: меньше параллелизма, cache-only mode, skip на проблемных форматах, но без поломки экрана.

### 5.2 Потоки данных и событий

После остановки scroll browse-экран инициирует metadata enrichment для видимых SMB audio rows. Для каждой единицы path либо читает partial bytes и успешно применяет metadata, либо быстро и тихо деградирует в skip/cache-only, не создавая тяжёлый хвост из warning/jank/dead-thread side effect'ов. Реальный playback flow при этом не должен зависеть от исхода browse metadata attempt.

### 5.3 Точки расширяемости

- Политика «какие partial-read outcomes считаются ожидаемыми» должна быть расширяема без переписывания UI-слоя.
- Если понадобится protocol-specific fallback для SMB, он должен оставаться отдельным от SFTP/FTP acceptance.
- Возможность полностью отключить enrich для отдельных format families при подтверждённой нестабильности должна быть предусмотрена как допустимый tactical вариант.

---

## 6. Открытые вопросы / Research items

1. **Откуда именно приходит `Handler on a dead thread`?**
   - **Вопрос:** Это прямой эффект browse metadata extractor path или только совпавший по времени сигнал из соседнего playback/extractor потока?
   - **Нужно выяснить:** нужен ли отдельный instrumentation marker вокруг browse metadata attempt, чтобы доказуемо связать warning с этим path.
   - **Статус:** Open

2. **Как трактовать `EOFException` на 64 KB partial read?**
   - **Вопрос:** Это ожидаемый partial-read miss, который должен быть downgraded до debug/skip, или аномалия, которую всё ещё нужно оставлять warning-grade?
   - **Нужно выяснить:** правило классификации по format family и transport context.
   - **Статус:** Open

3. **Нужен ли другой parser для browse-case?**
   - **Вопрос:** Достаточно ли ужесточить outcome policy вокруг текущего extractor, или browse metadata path должен перейти на более дешёвый parser/fallback без playback-like lifecycle?
   - **Нужно выяснить:** минимальный tactical вариант, который убирает dead-thread/jank без потери базовых artist/title данных.
   - **Статус:** Open

4. **Текущий лимит параллелизма адекватен ли для SMB large folders?**
   - **Вопрос:** Достаточен ли нынешний concurrency limit, если даже при нём видны jank spikes и noisy burst?
   - **Нужно выяснить:** нужен ли более строгий adaptive backpressure при low-memory и на медленных transport'ах.
   - **Статус:** Open

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Ужесточение skip policy уменьшит количество заполненных artist/title в browse | Средняя | Меньше metadata richness в списке | Зафиксировать, что browse metadata — best effort, а не correctness requirement |
| Переход на другой parser может потерять часть edge-case тегов/cover art | Средняя | Частичная деградация качества metadata на редких форматах | Сначала закрыть stability/jank, полноту тегов возвращать отдельным follow-up при необходимости |
| Слишком агрессивный backpressure замедлит обновление списка после scroll | Низкая | Пользователь увидит позднее появление artist/title | Деградировать только под pressure и на remote SMB rows, не трогая local/cache-hit path |
| Scope начнёт расползаться на transport race и memory reduction сразу | Средняя | Tactical plan потеряет фокус | Чётко держать S0228 как transport-race sibling, а S0207 как broader memory ticket |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`. Это bugfix и quality-hardening browse-слоя: пользователь не получает новую возможность, а теряет лаги и нестабильность в существующем metadata enhancement path.

---

## 9. Архитектурные решения (ADR)

**ADR-1: browse metadata — best effort, а не обязательный correctness path.**

- **Решение:** unstable/expensive metadata attempts можно пропускать или деградировать, если это сохраняет стабильность экрана.
- **Альтернативы:** любой ценой вытаскивать теги для каждого remote файла.
- **Почему:** пользовательский выигрыш от browse metadata ниже, чем цена dead-thread/jank/noise.

**ADR-2: warning budget принадлежит только аномалиям.**

- **Решение:** expected partial-read miss не должен конкурировать по уровню сигнала с настоящими transport/lifecycle дефектами.
- **Альтернативы:** логировать любой EOF/unsupported partial header как warning.
- **Почему:** иначе лог перестаёт быть диагностическим инструментом и скрывает реальные отказы.

**ADR-3: playback stability важнее полноты browse metadata.**

- **Решение:** если browse metadata path конкурирует с playback или UI responsiveness, приоритет у стабильности playback/UI.
- **Альтернативы:** держать одинаково агрессивный extractor path во всех режимах.
- **Почему:** browse enrichment не должен ухудшать базовую навигацию и воспроизведение.

---

## 10. Связи с другими спеками

- **S0207** (`In Progress`) — broader memory-reduction ticket, в которой этот metadata path рассматривается как один из pressure-кандидатов.
- **S0213** (`BlockNeedUserTest`) — playback hardening, поверх которой browse metadata noise/jank всё ещё может мешать чистому анализу session health.
- **S0219** (`In Progress`) — SFTP idle/retry race; напрямую не решает browse metadata instability, но близок по transport-layer symptom pattern.
- **S0228** (`Draft`) — sibling SMB idle-disconnect timer race, идущая рядом в той же лог-сессии.
- **S0169** (`Archived`) — предыдущий шумовой bugfix вокруг SMB audio metadata warnings; S0229 расширяет проблему от simple log-noise до stability/jank/dead-thread scope.

---

## 11. Критерии готовности (strategic-level)

1. Browse-сценарий с SMB audio folder не генерирует `Handler on a dead thread` в нормальной metadata enrichment цепочке.
2. Ожидаемые partial-read исходы на `65536` bytes не поднимаются до warning-grade без явного user-visible failure.
3. Metadata enrichment для видимых SMB audio rows не даёт jank burst уровня, наблюдавшегося в `logs/current.log`.
4. При нестабильном формате/path metadata chain тихо деградирует в skip/cache-only режим и не ломает browse/playback flows.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0229` — создать tactical breakdown с фокусом на outcome policy, parser/fallback decision и backpressure acceptance.