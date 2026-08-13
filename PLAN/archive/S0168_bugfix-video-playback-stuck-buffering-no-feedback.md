# Стратегическая спецификация: S0168 — ExoPlayer errorCode=1004 (stuck buffering) без обратной связи пользователю

**Ticket:** S0168
**Status:** Verified
**Priority:** 55
**Date:** 2026-05-11
**Updated:** 2026-05-12
**Tier:** 2 — Small (UX feedback + defensive playback handling)
**Roadmap entry:** Ad-hoc — лог `logs/fastmediasorter_20260511_220728.log` строка 11919; лог `logs/fastmediasorter_20260512_011618.log` строка 1611

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк.

---

## 1. Проблема

ExoPlayer выдаёт `errorCode=1004` при воспроизведении локальных видеофайлов:

```
E  VideoPlayerManager: Playback error — errorCode=1004
   Caused by: java.lang.IllegalStateException: Playback stuck buffering and not loading
              at ExoPlayerImplInternal.doSomeWork (errorCode=ERROR_CODE_TIMEOUT)
```

Приложение корректно переходит к следующему файлу, однако пользователь
**не получает никакого сообщения** о том, что файл не воспроизвёлся. Для пользователя это
выглядит как случайный пропуск видео без объяснений.

Ошибка воспроизводится в двух независимых сценариях:

### Сценарий 1 — большой файл

- **Лог**: `logs/fastmediasorter_20260511_220728.log`, строка 11919
- Файл: `4K HD.Club TCL Demonstrates Geographic Collection 100Mbps.mp4`, ~2.7 GB
- native heap в момент ошибки: ~188–194 MB allocated, free ~27 MB
- `MEM_ENDURANCE: verdict=FAIL` (baseline 49 MB, peak 70–73 MB)
- После ошибки пользователь несколько раз безуспешно пытался удалить тот же файл

### Сценарий 2 — маленький VP9-файл при хронически истощённом native heap

- **Лог**: `logs/fastmediasorter_20260512_011618.log`, строка 1611
- Файл: `readchan/b/webm/1778256193228879.webm`, VP9 (video/x-vnd.on2.vp9), 480×640, **818 KB**
- native heap к моменту старта VP9-декодера: 102 MB allocated, free **~11 MB**
- Heap хронически низкий c начала сессии — `ImagePreloadHelper: Preload skipped` 
  (free 4–12 MB) на протяжении всей предшествующей работы с изображениями
- `PrefetchLoadControl[createPlayer]: fallback standard defaults` — ExoPlayer получил
  некорректный контекст и упал на буферные дефолты, что усугубило нехватку памяти
- `MEM_ENDURANCE: verdict=SUSPICIOUS` в момент ошибки (FAIL появляется спустя 4 сек)
- После ошибки `nextFile() 4 → 5 / 116` — файл молча пропущен

**Общий корень**: VP9-декодер требует значительного native allocation при инициализации.
При free native heap < ~15–20 MB буферизация зависает → `Playback stuck buffering`.
Ни в одном из сценариев пользователь не видит уведомления.

---

## 2. Цели

1. При errorCode=1004 (`ERROR_CODE_TIMEOUT` — stuck buffering) пользователь получает
   ненавязчивое уведомление: название файла + сообщение «не удалось воспроизвести».
   Плеер при этом продолжает переходить к следующему файлу (текущее поведение сохраняется).
2. Записать факт ошибки воспроизведения по данному файлу в сессионный кеш неудачных файлов
   (`VideoExtractionFailurePersistence` или аналог), чтобы при повторном открытии файла
   в этой же сессии приложение предупредило о предыдущей неудаче.
3. Перед созданием ExoPlayer проверять native heap. Если `nativeFreeBytes < threshold (30 MB)` —
   выполнить `Glide.get(context).clearMemory()` + `System.gc()` и дождаться GC (один цикл).
   Если после этого heap всё ещё < threshold — предупредить пользователя, не пытаться
   воспроизводить автоматически.
4. Зафиксировать факт `PrefetchLoadControl: fallback standard defaults` как диагностический
   маркер — при его появлении логировать с уровнем W для последующего анализа.

**Non-goals:**
- Изменение логики ExoPlayer или нативного кодека — errorCode=1004 остаётся за рамками.
- Предварительная проверка «декодируемости» файла перед открытием плеера.

---

## 3. Ограничения

- **Flavor:** standard, lite, legacy, photos — все с video-плеером.
- **API level:** без специфики.
- **Wear OS:** не затрагивается.
- **Тон уведомления:** следовать `docs/COMMUNICATION_POLICY.md` §6 —
  нейтральное «Видео не удалось воспроизвести: <filename>», без технических деталей
  errorCode пользователю.
- **Локализация:** EN/RU/UK для новой строки уведомления.

---

## 4. Контекст текущей архитектуры

`VideoPlayerManager` обрабатывает `onPlayerError` от ExoPlayer. При errorCode=1004
текущее поведение: вызвать `PlayerViewModel.nextFile()` (переход к следующему файлу)
без показа какого-либо сообщения. Система `VideoExtractionFailurePersistence` (очистка
которой логируется в Browse при PullToRefresh) уже существует для сессионного кеша
ошибок, но судя по логу (`cleared 0 entries`) для errorCode=1004 в неё ничего не
пишется — она используется для другого (ошибки извлечения thumbnail).

---

## 5. Предлагаемый подход

### 5.1 Уведомление при errorCode=1004

В обработчике `onPlayerError`:
- Если `errorCode == 1004 (ERROR_CODE_TIMEOUT)` — показать Toast / Snackbar с
  именем файла и строкой «не удалось воспроизвести».
- Дальнейшее поведение (переход к следующему файлу) не меняется.

### 5.2 Сессионный кеш ошибок воспроизведения

Записать путь файла в set «не воспроизведённые в этой сессии». При следующем открытии
этого же файла плеером — показать предупреждение («В этой сессии файл не воспроизвёлся
ранее»). Сбрасывать set при PullToRefresh в Browse (уже происходит для аналогичного кеша).

### 5.3 Pre-check native heap перед созданием ExoPlayer

Перед `VideoPlayerManager.createPlayer()`:
1. Получить `Debug.getNativeHeapFreeSize()`.
2. Если `free < 30 MB` — вызвать `Glide.get(context).clearMemory()` + `Runtime.getRuntime().gc()`.
3. Если `free` по-прежнему `< 30 MB` (после GC):
   - Показать предупреждение «Недостаточно памяти для воспроизведения файла».
   - НЕ запускать ExoPlayer — предотвратить errorCode=1004 до его возникновения.
   - Дать пользователю выбор: пропустить / попробовать всё равно.
4. Если `free >= 30 MB` — запустить как обычно (текущий path).

Порог 30 MB — эмпирический, основан на VP9-сценарии (free=11 MB при crash).
VP9-декодер при инициализации аллоцирует ~20–30 MB native.

### 5.4 Диагностика PrefetchLoadControl

Если `PrefetchLoadControl` логирует `fallback standard defaults` — повысить уровень
лога с D до W, чтобы эти случаи фильтровались при анализе логов.

---

## 6. Риски

| Риск | Оценка |
|---|---|
| Threshold 30 MB может давать ложные срабатывания на устройствах с большим heap | Med — активируется только при реальном исчерпании; false positive на топовых телефонах маловероятен |
| GC + clearMemory перед каждым видео добавляет latency | Low — вызывать только при free < 30 MB (исключительный path) |
| Toast во время анимации перехода к следующему файлу может быть пропущен пользователем | Low — рассмотреть Snackbar с persistent display |
| MEM_ENDURANCE verdict=SUSPICIOUS не равноценен FAIL — detection delay ~4 сек | Med — pre-check heap устраняет первопричину, делая MEM_ENDURANCE для этого сценария избыточным |

---

## 7. Открытые вопросы

1. ~~Какой порог размера файла считать «крупным» для pre-check?~~ — Порог 30 MB нативной
   памяти не зависит от размера файла (VP9 на 818 KB даёт crash при free=11 MB). Threshold
   **определён**: 30 MB `Debug.getNativeHeapFreeSize()`.
2. Использовать существующий `VideoExtractionFailurePersistence` для записи ошибок
   воспроизведения errorCode=1004, или создать отдельный сессионный кеш?
3. Показывать Toast или Snackbar — и на каком экране (плеер уже перешёл к следующему
   файлу)?

---

## Last Audit

**Date:** 2026-05-14
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

Re-audit confirms 2026-05-12 verdict still holds. All §5 deliverables present in code: §5.1 `ERROR_CODE_TIMEOUT` branch in `VideoPlayerManager.onPlayerError` routes through `PlayerPlaybackCallbackImpl.onPlaybackError` to Toast; §5.2 `VideoPlaybackFailureSessionCache` (`markFailed`/`hasFailure`/`clearAll`) wired with pre-play Toast and PullToRefresh clear; §5.3 `NATIVE_HEAP_PREPLAY_THRESHOLD_BYTES = 30 MB` guard in `VideoPlayerManager` with `Glide.get(context).clearMemory()` + `Runtime.getRuntime().gc()` + `warning_low_memory_playback` Toast; §5.4 `PrefetchLoadControlFactory` `Timber.w` for fallback path. Trilingual `warning_low_memory_playback` confirmed in `values/`, `values-ru/`, `values-uk/`. No `Timber.d("S0168:` tags (status leaving Implemented → Verified — grep confirmed zero).

### Manual / on-device

- [ ] Reproduce scenario 2: play VP9/WebM file with native heap < 30 MB free; confirm pre-play warning Toast appears and ExoPlayer is not started.
