# Стратегическая спецификация: S0167 — Ложный ERROR при отмене поиска обложки из-за lifecycle

**Ticket:** S0167
**Status:** Verified
**Priority:** 20
**Date:** 2026-05-11
**Tier:** 1 — Trivial (logging / classification fix)
**Roadmap entry:** Ad-hoc — лог `logs/fastmediasorter_20260511_220728.log`, строки 8464–8478

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк.

---

## 1. Проблема

При выходе из плеера (Activity.onDestroy) корутина `searchOnlineAndDisplayCover` отменяется
средствами lifecycle — это нормальное поведение. Однако блок catch в обработчике поиска
обложки перехватывает `JobCancellationException` / `CancellationException` и логирует его
как **E-level** (`Error searching cover art for: ...` + `EXCEPTION`).

В результате Logcat и любая система мониторинга видят ERROR там, где ошибки нет —
обложка просто не успела загрузиться, потому что пользователь ушёл с экрана.

**Наблюдаемые строки в логе (22:11:23):**

```
W  iTunes search failed for 'Гелена Великанова Ландыши': Job was cancelled
E  Error searching cover art for: 01-Гелена Великанова _Ландыши_.mp3
E  searchOnlineAndDisplayCover[1778530283282]: EXCEPTION  (JobCancellationException)
```

За несколько миллисекунд до этого — `PlayerLifecycleManager.onDestroy`.

Дополнительно: coverartarchive.org возвращает HTTP 404 для одного и того же release URL
трижды (строки лога 1028, 1214, 1401), потому что отрицательный результат не кешируется
между попытками для разных треков одного исполнителя.

---

## 2. Цели

1. `CancellationException` (и производные — `JobCancellationException`) в обработчике поиска
   обложки не логируются как E-level. Если scope уже завершается, корутина молча прекращает
   работу (опционально D-level лог «search cancelled due to lifecycle»).
2. HTTP 404 от coverartarchive.org и аналогичных источников кешируется на период жизни
   сессии — повторный запрос по тому же URL (или album-key) не выполняется.

**Non-goals:**
- Изменение самой логики поиска обложек (порядок источников, таймауты, число ретраев для
  реальных ошибок сети).
- Персистентный кеш отрицательных результатов между сессиями.

---

## 3. Ограничения

- **Flavor:** standard, lite, legacy — все, где есть аудио-плеер.
- **API level:** без специфики.
- **Wear OS:** не затрагивается.
- **Тон:** изменений UI-строк нет.

---

## 4. Контекст текущей архитектуры

Поиск обложки запускается корутиной в скоупе `PlayerViewModel` (или связанном
Activity-скоупе). При выходе из Activity скоуп отменяется, все дочерние корутины получают
`CancellationException`. Обработчик в `searchOnlineAndDisplayCover` использует
`try { ... } catch (e: Exception) { Timber.e(...) }` — ловит все `Exception` включая
`CancellationException`, которая является `RuntimeException` в Kotlin Coroutines.
Правило «не перехватывать `CancellationException`» в Kotlin Coroutines требует либо
проверки `e is CancellationException` с re-throw, либо явного разграничения типов.

Кеш отрицательных результатов coverart: судя по повторным 404-запросам в логе,
внутрисессионного кеша для неудачных ответов нет — каждый новый аудиофайл того же
исполнителя инициирует повторный запрос.

---

## 5. Предлагаемый подход

### 5.1 Исправление классификации CancellationException

В блоке catch `searchOnlineAndDisplayCover` добавить ветку до общего `Exception`:
- Если `e is CancellationException` — re-throw (стандартное правило корутин) или,
  если дизайн требует проглотить, логировать на уровне D с пометкой `lifecycle-cancel`.
- Оставшиеся исключения (реальные ошибки сети, парсинга) — логировать как E.

### 5.2 Кеш отрицательных ответов coverart

Ввести in-memory `Set<String>` (или `Map<key, timestamp>`) «known-negative cover keys» в
менеджере поиска обложек. Ключ — нормализованная пара `artist + album` (или URL релиза,
если он детерминирован). При 404 / «не найдено» — добавить в set; перед запросом —
проверять наличие в set и пропускать сетевой вызов.

---

## 6. Риски

| Риск | Оценка |
|---|---|
| Re-throw `CancellationException` может всплыть выше, если catch поверх не ожидает его | Low — SupervisorJob изолирует дочерние корутины; проверить цепочку вызовов |
| Кеш отрицательных результатов может скрыть реальное появление обложки в ходе сессии (если исполнитель добавил обложку в MusicBrainz пока приложение работает) | Very Low — сессия длится минуты/часы, MusicBrainz обновляется редко |

---

## 7. Открытые вопросы

1. Используется ли `supervisorScope` / `SupervisorJob` в цепочке вызовов над
   `searchOnlineAndDisplayCover`, или это `launch` напрямую в ViewModel-скоупе? — влияет
   на то, нужен ли re-throw или проглатывание достаточно.
2. Какой ключ использовать для кеша негативных результатов coverart — URL релиза (если он
   стабилен между треками одного альбома) или нормализованная пара artist+album?

---

## Last Audit

**Date:** 2026-05-14
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

§5.1 implemented in `AudioCoverArtLoader.kt`: `CancellationException` is caught before generic `Exception` and logged at D-level (`searchOnlineAndDisplayCover[$callId]: cancelled for ${file.name}`) — no false E-level on lifecycle teardown. §5.2 implemented as `companion object { private val knownMissingCoverUrls = ConcurrentHashMap.newKeySet<String>() }` — session-scoped negative cache keyed by URL; 404 responses populate the set and subsequent loads short-circuit. `is404NotFound(GlideException)` extracts the `HttpException.statusCode == 404` from `rootCauses`. No `Timber.d("S0167:` tags (status leaving Implemented → Verified — grep confirmed zero).
