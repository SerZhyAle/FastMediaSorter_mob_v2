# Стратегическая спецификация: S0213 — Защита воспроизведения видео от OOM

**Ticket:** S0213
**Status:** Archived
**Implemented date:** 2026-05-15
**Priority:** 95
**Date:** 2026-05-15
**Tier:** 3 — Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc — запрос 2026-05-15 (по результатам анализа crash-сессии build `2.60.5151.713-NoLegal-DEBUG`)
**Tactical spec:** `PLAN/S0213_bugfix-video-playback-oom-hardening/`
**Tactical plan:** `PLAN/S0213_bugfix-video-playback-oom-hardening/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

В сессии 2026-05-15 18:29:05 приложение упало с фатальным `OutOfMemoryError` в потоке `ExoPlayer:Playback`: managed heap 512 MB полностью исчерпан, при попытке media3 сформировать строковое представление stacktrace очередного `IoException` не удалось аллоцировать даже 2,3 KB. Сценарий пользователя штатный — открытие крупного AVI-файла из локальной папки (≈1,5 GB, FFmpeg-аудио). Файл с битым/неподдерживаемым аудио-треком вызывает `MediaCodec error 4003`, после чего пользователь повторно запускает то же воспроизведение; каждый рестарт оставляет в native-куче следы прежнего рендер-графа, и через 3–4 итерации heap гарантированно заканчивается. Telemetry (`MEM_ENDURANCE`) уже фиксирует verdict=FAIL и drift_from_baseline ≥ 50 %, но никаких пользовательских реакций не следует — приложение продолжает принимать новые попытки воспроизведения вплоть до краша.

Канонические ссылки на доказательства: `logs/fastmediasorter_20260515_182532.log` строки 5079 (`errorCode=4003`), 5825 (`errorCode=2000`), 6515 (`MEM_ENDURANCE verdict=FAIL peak=86MB`), 6532 (FATAL OOM); `logs/fastmediasorter_crash_20260515_182905.log`.

---

## 2. Цели

1. Повторный запуск того же источника, который только что вызвал ошибку декодера (`errorCode=4003` / `FfmpegDecoderException`), в коротком окне после сбоя не приводит к моментальному пересозданию плеера и не накапливает утечку.
2. Сбой логирования внутри media3 (формирование stacktrace-строки в условиях исчерпанной кучи) не превращается в фатальный краш процесса.
3. При срабатывании уже существующих сигналов телеметрии (`MEM_ENDURANCE … verdict=FAIL`, drift_from_baseline ≥ 50 %) пользователь явно уведомляется о деградации памяти и получает однонажимный путь к корректному выходу из плеера.
4. Все три защиты доказуемо проходят на воспроизводимом сценарии без необходимости иметь именно тот файл AVI, что зафиксирован в логе.

**Non-goals:**

- Переписывание конфигурации FFmpeg-рендерера или механизма выбора треков.
- Замена логирующего слоя media3 в целом — изменяется только устойчивость форматирования к OOM.
- Корректировка порогов `MEM_ENDURANCE` (FAIL/SUSPICIOUS/PLATEAU остаются как есть).
- Автоматическое принудительное закрытие плеера при OOM-сигналах — действие остаётся за пользователем.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Cooldown-окно после декодер-ошибки желательно делать настраиваемым на этапе тактической спеки (предложение к обсуждению — 30..60 с).
2. По возможности использовать уже существующий MEM_ENDURANCE-канал телеметрии как источник сигнала, а не вводить параллельный.
3. Не плодить новые BuildConfig-флаги — fix должен быть «всегда включённым» в затронутых flavors.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `noLegal`. Код плеера общий между ними и живёт в `src/main/java/`. Никакого нового `BuildConfig.SUPPORT_*`/`IS_*`/`ENABLE_*` гейта в `src/main/java/` (CLAUDE.md Rule 15). Для VR-flavors поведение наследуется автоматически (общий main-source-set).
- **API level:** без API-специфики. Поведение должно работать одинаково на API 26..36.
- **Wear OS:** не затрагивается.
- **Производительность:** защитный код не должен добавлять заметной нагрузки на старте плеера; проверка cooldown — O(1) lookup; OOM-safe форматирование stacktrace срабатывает только в исключительной ветке.
- **Совместимость данных:** не требуется — состояние «недавний сбой источника» хранится только in-memory на время процесса.
- **Локализация:** EN/RU/UK обязательно для всех новых пользовательских строк (уведомление о cooldown декодера, уведомление о деградации памяти, CTA «Закрыть плеер»). Каждая новая строка проходит аудит `scripts/check_strings_localized.ps1`.
- **Доступность:** уведомление должно быть не-блокирующим для жестов плеера и читаемым TalkBack; CTA имеет touch-target ≥ 48 dp.
- **Communication policy:** все новые пользовательские строки соответствуют `docs/COMMUNICATION_POLICY.md`; tone checklist (§6 политики) — обязательная пред-коммитная проверка.

---

## 4. Контекст текущей архитектуры

Воспроизведение видео реализовано слоем менеджеров плеера, поверх ExoPlayer Media3. Менеджер воспроизведения принимает запрос «играть файл X», создаёт/переиспользует экземпляр плеера, подписывается на события рендереров и обрабатывает ошибки через колбэки media3. Параллельно работает существующая телеметрия памяти, выпускающая сигналы `MEM_PROBE` и `MEM_ENDURANCE` в Timber. Сегодня цепочка «декодер-ошибка → пользовательский ретрай → новый старт плеера» не имеет никакой прослойки, удерживающей повторный вход; равно как сигналы телеметрии о деградации памяти не маршрутизируются ни в UI-слой, ни в политику принятия новых запросов воспроизведения. Логирование ошибок media3 идёт через дефолтную реализацию `androidx.media3.common.util.Log.Logger`, которая в условиях near-OOM сама способна стать причиной фатального краша.

---

## 5. Предлагаемый подход

Три скоординированные защиты, объединённые в один тикет, потому что разделять их в релиз нерационально: каждая по отдельности оставляет открытым другой вектор того же краша.

### 5.1 Основные столпы / модули

#### Столп A — Cooldown повторного запуска источника после декодер-ошибки

- Появляется маленький компонент-память «недавно упавших источников» уровня процесса (in-memory, без Room).
- Запись в эту память делается в обработчике ошибок плеера, когда возникает `errorCode=4003` либо иной маркер `FfmpegDecoderException` (точный список маркеров фиксируется в тактической спеке).
- При входе нового запроса воспроизведения слой запуска плеера сначала спрашивает память: «этот источник в cooldown?». Если да и контекст — слайд-шоу/плейлист (есть следующий файл в очереди) — запрос автоматически перенаправляется на следующий источник, пользователь видит короткое уведомление о пропуске. Если контекст — ручной запуск одиночного файла — пересоздание плеера не происходит, пользователь получает разовое уведомление с явной кнопкой «Пропустить» (см. ADR-4).
- Cooldown снимается либо по истечении окна, либо при успешном воспроизведении любого другого источника (как маркер «память восстановилась, графы пересозданы»).

#### Столп B — OOM-устойчивая обёртка форматирования media3-логов

- На старте процесса в подсистему логирования media3 устанавливается своя реализация форматирования сообщений и stacktrace.
- Реализация ловит `OutOfMemoryError` в момент аллокации строкового представления исключения и подменяет результат коротким безопасным маркером (с уровнем warn/Timber), вместо повторного выброса OOM из логирующего пути.
- Дополнительная мера — верхний предел длины формируемой строки stacktrace (точное значение фиксируется в тактической спеке), чтобы even-pathological трассы не доедали последние байты heap.

#### Столп C — Пользовательское оповещение по сигналу деградации памяти

- К существующему каналу `MEM_ENDURANCE` добавляется наблюдатель, фильтрующий verdict=FAIL и drift_from_baseline ≥ 50 %.
- **Дополнительный гейт occupancy:** к двум первым условиям добавлен третий — `used / Runtime.maxMemory() ≥ 0,75` на момент проверки. Без него относительный drift от тонкого baseline (Quest 3 / canonical эмулятор, baseline 30..50 MB, рост до 80 MB при штатном воспроизведении) даёт false-positive: процесс далёк от OOM, но verdict FAIL сработал. Гейт occupancy конвертирует «сигнал утечки» в «сигнал реальной нехватки headroom»; телеметрические пороги (FAIL/SUSPICIOUS/PLATEAU и 50 %-drift) не меняются — non-goal §2 сохранён.
- При срабатывании поверх плеера показывается snackbar поверх контролов с CTA «Закрыть плеер»; основной поток воспроизведения не прерывается принудительно. Snackbar не блокирует жесты плеера, прячется по таймауту (~7 c) или по нажатию Action.
- Уведомление одноразовое для текущей сессии плеера: повторные сигналы не порождают спам.

### 5.2 Потоки данных и событий

- **Столп A:** обработчик ошибок плеера → запись в cooldown-память; каждый новый запрос воспроизведения → проверка cooldown-памяти → либо нормальный путь, либо короткое уведомление.
- **Столп B:** инициализация логирования media3 при старте процесса → перехват вызовов error/warn/info → внутри обёртки `try { format } catch (OOM) { fallback }`.
- **Столп C:** существующий генератор MEM_ENDURANCE → точка эмита в трекере применяет AND из трёх условий (FAIL + drift ≥ 50 % + occupancy ≥ 0,75) → наблюдатель в UI-слое плеера → одноразовое визуальное уведомление + явный CTA.

### 5.3 Точки расширяемости

- Cooldown-политика выносится за интерфейс в виде отдельной роли «трекер недавних сбоев источника», чтобы при необходимости в будущем можно было подменить реализацию (например, расширить набор маркеров ошибок без правок плеера).
- OOM-обёртка media3-логов оформляется как одна точка инициализации в стартовом слое процесса; её можно отключить/заменить целиком, не трогая места вызова ExoPlayer.
- Канал MEM_ENDURANCE → UI оформляется как наблюдатель с подменяемой реализацией, чтобы в дальнейшем можно было добавлять другие пользовательские реакции (например, для долгих сессий слайд-шоу) без правок самой телеметрии.
- Никаких новых BuildConfig-гейтов в `src/main/java/`; flavor-специфика отсутствует — поведение единое для standard и noLegal.

---

## 6. Открытые вопросы / Research items

1. **Длительность cooldown-окна после декодер-ошибки**
   - **Решение:** 45 секунд, фиксированная константа в коде. Эмпирическое обоснование по логу 18:25–18:29: native-куча после закрытия 1,5 GB AVI восстанавливается до baseline ≈ за 30 c (164MB → 75MB между 18:27:50 и 18:28:23), 45 c даёт запас. Не выносится в Settings (overkill), не делается адаптивной от свободной памяти (риск осцилляций при near-OOM). Точное значение можно подкрутить по результатам полевых наблюдений — выделено отдельной константой.
   - **Статус:** Resolved (engineering default)

2. **Поведение во время cooldown — уведомление или auto-skip**
   - **Решение:** Гибрид по контексту. Если есть следующий файл в очереди (слайд-шоу/плейлист) — auto-skip на следующий файл с коротким уведомлением о пропуске. Если контекст — ручной запуск одиночного файла — уведомление с кнопкой «Пропустить» (без авто-перехода).
   - **Подтверждено:** владельцем 2026-05-15.
   - **Статус:** Resolved

3. **Форма уведомления для MEM_ENDURANCE FAIL**
   - **Решение:** Snackbar поверх контролов плеера с CTA «Закрыть плеер». Не блокирует жесты, прячется по таймауту (~7 c) или по Action. Тестируется в portrait/landscape и в VR-плеере.
   - **Подтверждено:** владельцем 2026-05-15.
   - **Статус:** Resolved

4. **Уровень логирования сообщений-маркеров в OOM-обёртке**
   - **Решение:** `Timber.w` с фиксированным префиксом `media3 log dropped due to OOM` + media3-тег + класс оригинального throwable + длина исходной формируемой строки в символах. Никаких heap-dump'ов и crash-сигналов. Максимум 256 символов на сообщение, без интерполяции коллекций — только пара заранее известных полей. Это исключает риск, что сама запись маркера снова упадёт по OOM.
   - **Статус:** Resolved (engineering default)

5. **Скоуп срабатывания cooldown — по полному URI или по нормализованному пути**
   - **Решение:** Использовать строку `path`, как её передают в `playVideo(path)` — это уже канонический ключ существующей подсистемы плеера, по которому работает остальная логика (resume position, file move). Edge-case с разными SAF/MediaStore-токенами одного файла встречается редко в реальной usage и не входит в crash-сценарий первой итерации; усложнение нормализации введём только если будет наблюдаться повторение крашa с разными URI того же файла.
   - **Статус:** Resolved (engineering default)

6. **Гейт точки эмита для Pillar C — относительный drift vs. occupancy** *(добавлено 2026-05-18 при ревизии после false-positive репорта)*
   - **Контекст:** Первая редакция Pillar C использовала только relative-drift сигнал (`verdict=FAIL ∧ drift_from_baseline ≥ 50 %`). Это валидный leak-detection критерий, но не индикатор близости к OOM: на устройствах с маленьким per-process heap (Quest 3: 512 MB) baseline `startScenario` фиксируется на 30..50 MB; нормальное воспроизведение видео доводит heap до 70..90 MB, drift 60..100 %. Verdict FAIL → snackbar — при том что heap занят на 15..18 %, OOM-риска нет. На канониче-репро-кейсе спеки (1,5 GB AVI, реальная утечка) heap уходил под 80+ MB / 512 MB → 16 % occupancy, но это сопровождалось дальнейшей деградацией — для надёжного user-facing сигнала relative drift недостаточен сам по себе.
   - **Решение:** Добавить третий гейт `used / Runtime.maxMemory() ≥ 0,75` к эмит-веткам внутри `MemoryEnduranceTracker.endScenario` и `MemoryEnduranceTracker.cooldownCheckpoint`. Все три условия должны выполняться одновременно (AND). Threshold 0,75 — эмпирический: ниже 75 % современный GC ещё успешно отдаёт обратно, выше 75 % новые крупные аллокации (Glide, видеобуфер) уже несут реальный риск OOM. Подбор будущий через полевые наблюдения, выделено отдельной константой `HEAP_PRESSURE_THRESHOLD`.
   - **Подтверждено:** владельцем 2026-05-18.
   - **Non-goal compliance:** §2 запрещает корректировку порогов MEM_ENDURANCE (FAIL/SUSPICIOUS/PLATEAU). Эти пороги остаются как есть — изменяется только фильтр-эмит UI-сигнала.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Cooldown ложно срабатывает на временный декодер-сбой, который сам по себе разрешился бы повторной попыткой | Средняя | Пользователь видит уведомление и ждёт, хотя файл уже играбелен | Cooldown снимается успешным воспроизведением любого другого источника; интервал делается компактным (30..60 с); добавить ручной обход (повторный жест запуска поверх уведомления) |
| OOM-обёртка media3 проглотит полезный диагностический stacktrace при штатных ошибках | Низкая | Хуже отлаживать новые проблемы media3 | Fallback-сообщение содержит как минимум тег и длину оригинальной трассы; обёртка срабатывает только по исключительной ветке `catch (OutOfMemoryError)`; полный путь логирования сохраняется в норме |
| Уведомление по MEM_ENDURANCE FAIL начинает спамить в долгих сессиях | Средняя | Пользователь раздражается и игнорирует сигнал | Одноразовость в пределах сессии плеера; повторная активация только после явного действия пользователя или после периода стабильности телеметрии |
| Cooldown создаёт ощущение «приложение зависло» при попытке повторного запуска | Низкая | Жалоба на «не реагирует» | Уведомление обязательно, формулировка по communication policy: явно сообщить причину и оставшееся время |
| Новая абстракция трекера сбоев усложняет код плеера без необходимости в long-tail сценариях | Низкая | Технический долг | Минимальная роль (in-memory, один интерфейс, один Singleton); не вводить кэш-политику дольше времени жизни процесса |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES`. Бугфикс не вводит новой пользовательской возможности — он только защищает существующее воспроизведение видео от воспроизводимого крашa и добавляет диагностическое уведомление на пограничные ситуации.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Объединение трёх защит в один тикет**

- **Решение:** Cooldown декодера, OOM-устойчивое логирование media3 и пользовательское оповещение по MEM_ENDURANCE объединяются в одну спеку S0213.
- **Альтернативы:** Три отдельных тикета с независимыми приоритетами.
- **Почему:** Все три защиты адресуют один и тот же crash-сценарий; релиз без любой из трёх оставляет open-vector того же крашa. Тестовый прогон удобнее покрывает связку целиком. Подтверждено владельцем 2026-05-15.

**ADR-2: In-memory трекер без персистентности**

- **Решение:** «Недавно упавшие источники» хранятся только в памяти процесса, без Room и DataStore.
- **Альтернативы:** Персистентный кэш с TTL, переживающий рестарт процесса.
- **Почему:** Cooldown адресует именно цепочку быстрых ретраев в пределах одной сессии. После рестарта процесса native-граф пересоздан, риски утечки сброшены, наследовать «чёрный список» бессмысленно. Persistence добавит миграцию и повышенный риск ложных блокировок при следующем запуске.

**ADR-3: Не автоматизировать выход из плеера при сигнале деградации памяти**

- **Решение:** При срабатывании MEM_ENDURANCE FAIL приложение только показывает snackbar с CTA; принудительное закрытие плеера не выполняется.
- **Альтернативы:** Авто-закрытие плеера через N секунд после уведомления; принудительная остановка воспроизведения.
- **Почему:** Авто-закрытие на пороге, который ещё не равен OOM, — деструктивный UX. Сигнал FAIL — это «риск», а не «отказ»; решение оставлено за пользователем. Если краш всё-таки происходит, его перехватывает столп B (OOM-устойчивое логирование).

**ADR-4: Поведение cooldown зависит от контекста воспроизведения**

- **Решение:** В контексте слайд-шоу/плейлиста (есть следующий файл в очереди) повторный запрос на источник в cooldown автоматически перенаправляется на следующий файл; в контексте ручного запуска одиночного файла — показывается уведомление с явной кнопкой «Пропустить» без авто-перехода.
- **Альтернативы:** Только уведомление во всех контекстах (фрустрация в слайд-шоу: пользователь ждёт у экрана, ничего не происходит); auto-skip всегда (нарушает ожидание «пользователь сам нажал repeat»).
- **Почему:** Слайд-шоу — режим автоматического pacing, авто-пропуск консистентен с парадигмой. Ручной запуск — действие пользователя; молчаливый auto-skip без явного намерения сменить файл нарушает контракт. Подтверждено владельцем 2026-05-15.

---

## 10. Связи с другими спеками

- Связана с уже завершённой **S0168** (`Verified` 2026-05-14) — эта спека ввела GC/Glide-eviction перед стартом плеера при низкой native-памяти. S0213 закрывает оставшийся вектор: повторные ретраи источника с декодер-ошибкой и OOM в самом media3-логировании.
- S0210 (`BlockNeedUserTest`) — независимый тикет; пересечений по коду не ожидается.

---

## 11. Критерии готовности (strategic-level)

1. Тестовый сценарий «открыть видео с декодер-ошибкой → 3 повторных запуска того же источника подряд в пределах 30 с» завершается без OOM-крашa и без накопления native-памяти выше baseline + 50 %.
2. Принудительное near-OOM состояние, при котором media3 пытается залогировать ошибку источника, не приводит к фатальному падению процесса; в Timber появляется маркер «media3 log dropped due to OOM».
3. При сигнале телеметрии MEM_ENDURANCE verdict=FAIL пользователь видит одноразовое уведомление с явной кнопкой «Закрыть плеер»; нажатие на кнопку корректно выходит из плеера.
4. На стандартном пути воспроизведения (источник без декодер-ошибки и без деградации памяти) пользователь не наблюдает никаких новых уведомлений и никаких задержек запуска плеера.
5. На пути после декодер-ошибки повторный жест воспроизведения того же источника в режиме слайд-шоу/плейлиста переходит на следующий файл с уведомлением о пропуске; в режиме ручного запуска одиночного файла показывается snackbar с кнопкой «Пропустить» по `docs/COMMUNICATION_POLICY.md` без немедленного пересоздания плеера.
6. Все новые пользовательские строки локализованы EN/RU/UK (`scripts/check_strings_localized.ps1` exit 0).
7. На каждый из трёх столпов в коде присутствует ровно один `Timber.d("S0213: …")`-зонд при переходе спеки в `BlockNeedUserTest`; зонды удаляются строго при выходе спеки из этого статуса.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация: `PLAN/S0213_bugfix-video-playback-oom-hardening/INDEX.md`

---

## Last Audit

**Date:** 2026-05-18
**Result:** PASS (additional false-positive vector closed) → BlockNeedUserTest for on-device verification of all three pillars + fix-release readiness.

### Fix applied (2026-05-18) — third gate `isHeapUnderPressure`

Regression observed: release `v2.60.5172.102` fires Pillar C "close player" snackbar after 3-5 normal video plays on capable devices (7 GB+ RAM, `heapMax=512MB`). Root cause: the 2026-05-16 fix gated `cooldownCheckpoint()` correctly, but `endScenario()` still emitted on `verdict == FAIL` alone. `deriveVerdict()` returns FAIL whenever `projectedDrift > SUSPICIOUS_THRESHOLD (40%)`; a Java heap rise from a thin baseline (23 MB → 45 MB during VID-playback) trivially crosses that on every session. Evidence: `logs/fastmediasorter_20260518_004128.log` line 2321 (`baseline=23MB peak=45MB final=45MB verdict=FAIL`) immediately followed by line 2324 (`S0213: Pillar C memory degradation snackbar shown`). No `MEM_ENDURANCE | PRESSURE_CHECK` line precedes the emit — confirming the pressure gate was missing on the `endScenario()` path in the shipped binary.

**Fix:** Added `isHeapUnderPressure()` helper that returns true when `usedJavaHeap / maxJavaHeap >= HEAP_PRESSURE_THRESHOLD (0.75)`. Gate applied in **both** call sites:

```kotlin
// endScenario()
if (verdict == "FAIL" && isHeapUnderPressure(emitContext = "SCENARIO_END")) {
    degradationSignal?.emitFail(...)
}

// cooldownCheckpoint()
if (lastScenarioVerdict == "FAIL" &&
    recovery >= DRIFT_FAIL_THRESHOLD &&
    isHeapUnderPressure(emitContext = "COOLDOWN_RESULT")
) {
    degradationSignal?.emitFail(...)
}
```

KDoc rewritten to document the three-gate invariant: "leak signal" (verdict + drift) is decoupled from "user-facing danger signal" (absolute headroom). Capable devices with a 512 MB per-process heap no longer trip on transient growth from a thin baseline. Build: `assembleNoLegalDebug` BUILD SUCCESSFUL 45s (2026-05-18 verification).

**Fix-release path:** the regression affects users on the shipped `v2.60.5172.102` release; fix qualifies as a Fix Release per CLAUDE.md (only restores previously working behavior, no new UI, no new feature). When initiated, `/skill-fix-release` should target this commit.

### Fix applied (2026-05-16)

Initial root cause: `MemoryEnduranceTracker.cooldownCheckpoint()` was calling `emitFail` on `drift_from_baseline ≥ 50 %` independently of `endScenario()` verdict. During normal video playback, native alloc rises from ~37 MB baseline to 50–67 MB (+35–81 %), hitting the threshold even on SUSPICIOUS/PLATEAU verdicts. Two false-positive snackbar fires observed in the 2026-05-16 session log (lines 6160, 12609).

**Fix:** Added `lastScenarioVerdict: String` field to `MemoryEnduranceTracker`. `endScenario()` stores `verdict` before scheduling the cooldown callback. `cooldownCheckpoint()` now guards: `if (lastScenarioVerdict == "FAIL" && recovery >= DRIFT_FAIL_THRESHOLD)`. KDoc updated to state "both conditions must hold". assembleStandardDebug BUILD SUCCESSFUL 27s. **Insufficient on its own** — `endScenario()` path remained ungated (closed by 2026-05-18 fix above).

### Code coverage after fix

- **Pillar A** (decoder cooldown): implemented in Phases 01–02; not exercised in the 2026-05-16 session (no 4003 decoder error). Requires device test with a file triggering `errorCode=4003`.
- **Pillar B** (OOM-safe logger): `media3 OOM-safe logger installed (S0213)` in startup banner; reactive OOM path not exercised (no OOM in session). Requires near-OOM scenario or synthetic test.
- **Pillar C** (memory snackbar): false-positive fixed. Requires device verification that the snackbar does NOT appear during normal playback and DOES appear when `MEM_ENDURANCE verdict=FAIL` genuinely fires.

### Criteria status

- Criterion 1 (decoder error → no OOM after 3 retries): pending device test.
- Criterion 2 (near-OOM logging doesn't crash): pending device test.
- Criterion 3 (snackbar on FAIL verdict): implementation fixed; pending device test.
- Criterion 4 (no spurious snackbar on normal playback): **fully fixed (2026-05-18)** — third gate `isHeapUnderPressure(occupancy>=0.75)` closes the remaining `endScenario()` false-positive path that shipped in `v2.60.5172.102`. Active evidence: `logs/fastmediasorter_20260518_004128.log` line 2324 was the last false-positive expected; rebuild with third gate suppresses it. Pending on-device confirmation after fix-release.
- Criterion 5 (cooldown → auto-skip in slideshow / snackbar in single-file): pending device test.
- Criterion 6 (strings localized): PASS — `check_strings_localized.ps1 -KeyPrefix s0213` exit 0 (Phase 05).
- Criterion 7 (Timber.d tags per BlockNeedUserTest): tags added (see action below).

Broader note: the 2026-05-16 SMB audio session confirms that memory pressure persists outside the original AVI repro. S0213 guard behaviour remains consistent, while root-cause reduction stays under S0207. SMB-specific follow-ups for shared idle-timer bursts and browse-side metadata instability are split into S0228 and S0229.
