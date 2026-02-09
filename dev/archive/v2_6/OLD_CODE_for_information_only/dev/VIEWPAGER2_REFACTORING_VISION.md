# ViewPager2 Refactoring: Vision & Lessons Learned

**Дата создания:** 17 декабря 2024  
**Статус:** ОТЛОЖЕНО - требуется пошаговый подход  
**Причина отката:** Критические баги в core функциях, слишком большой объём изменений одновременно

---

## 🎯 ОСНОВНАЯ ИДЕЯ

Заменить монолитный `PlayerActivity` (3500+ строк) на модульную архитектуру с **ViewPager2** для листания медиа-файлов и **Fragment-based** проигрывателями для каждого типа контента.

### Проблемы текущей архитектуры (до рефакторинга)

**PlayerActivity - "God Class" (~3500 строк):**
- ❌ Вся логика отображения IMAGE/VIDEO/AUDIO/PDF/TEXT/EPUB в одном файле
- ❌ 7 менеджеров (ImageLoadingManager, VideoPlayerManager, PdfViewerManager, TextViewerManager, EpubViewerManager, MediaDisplayCoordinator, PlayerUiStateCoordinator) - каждый по 800-1500 строк
- ❌ Переключение между файлами через `showNext()`/`showPrevious()` - каждый раз destroy/create всех UI элементов
- ❌ Memory leaks при быстром листании (старые ExoPlayer/WebView не успевают освободиться)
- ❌ Невозможность preload следующего файла (тормоза при листании видео)
- ❌ Layout XML с 200+ элементами для всех типов контента одновременно
- ❌ Сложность добавления новых типов файлов (надо править 10+ файлов)

**Конкретные баги из-за монолитности:**
1. **Memory pressure:** При листании 10 видео подряд - OutOfMemoryError (каждое видео держит ExoPlayer в памяти)
2. **UI lag:** При переходе IMAGE→VIDEO - задержка 500-1000ms (создание ExoPlayer)
3. **Thread safety:** VideoPlayerManager.release() вызывается из UI thread вместо player thread → IllegalStateException
4. **Resource cleanup:** При быстром листании старый WebView (EPUB) не успевает destroy → ANR

---

## 🚀 ЧТО ХОТЕЛИ ПОЛУЧИТЬ (Преимущества ViewPager2)

### 1. **Модульная архитектура (Separation of Concerns)**

**Было:**
```
PlayerActivity (3500 lines)
├── ImageLoadingManager (700 lines)
├── VideoPlayerManager (1200 lines)  
├── PdfViewerManager (1080 lines)
├── TextViewerManager (914 lines)
├── EpubViewerManager (800 lines)
├── MediaDisplayCoordinator (930 lines)
└── PlayerUiStateCoordinator (600 lines)
```

**Планировалось:**
```
PlayerActivity (800 lines) - только ViewPager2 coordinator
├── ImagePlayerFragment (150 lines) - только IMAGE
├── VideoPlayerFragment (250 lines) - только VIDEO
├── AudioPlayerFragment (200 lines) - только AUDIO
├── PdfPlayerFragment (300 lines) - только PDF
├── TextPlayerFragment (180 lines) - только TEXT
└── EpubPlayerFragment (300 lines) - только EPUB
```

**Преимущества:**
- ✅ Каждый фрагмент отвечает ТОЛЬКО за свой тип контента
- ✅ Изменения в IMAGE не влияют на VIDEO/PDF/TEXT
- ✅ Легко добавить новый тип (создать 1 новый фрагмент)
- ✅ Код фрагмента < 300 строк → легко читать и тестировать

### 2. **Memory Optimization (Smart Page Lifecycle)**

**ViewPager2 offscreenPageLimit=1:**
- **Current page** - активен, полностью загружен
- **Next page (+1)** - preload в фоне (для VIDEO - buffering без autoplay)
- **Previous page (-1)** - в памяти для быстрого возврата
- **Other pages (±2, ±3...)** - уничтожены, память освобождена

**Пример (VIDEO):**
```
User on index 5 (video5.mp4):
- index 4: kept in memory (pause ExoPlayer)
- index 5: playing (current)
- index 6: buffering in background (prepareForPreload)
- index 3, 7: destroyed → ExoPlayer.release()
```

**Результат:**
- 🔥 Максимум 3 ExoPlayer в памяти вместо бесконечного накопления
- 🔥 Плавный переход к следующему видео (уже буферизовано)
- 🔥 Быстрый возврат назад (не надо заново загружать)

### 3. **Fragment Lifecycle (Auto Memory Management)**

**Android Fragment lifecycle hooks:**
- `onCreateView()` - создать UI только когда фрагмент появился на экране
- `onDestroyView()` - убить UI автоматически когда фрагмент вне экрана
- `onPageSelected()` - фрагмент стал видимым (start playback)
- `onPageUnselected()` - фрагмент ушёл с экрана (pause/stop)

**Без ViewPager2 (текущая реализация):**
```kotlin
fun showNext() {
    // Вручную destroy старый контент
    imageLoadingManager.cleanup()
    videoPlayerManager.release()
    pdfViewerManager.cleanup()
    
    // Вручную create новый контент
    when (newFile.type) {
        IMAGE -> imageLoadingManager.load(...)
        VIDEO -> videoPlayerManager.prepare(...)
        PDF -> pdfViewerManager.load(...)
    }
}
```
❌ **Проблема:** Легко забыть cleanup → memory leak

**С ViewPager2:**
```kotlin
// Android Fragment lifecycle делает ВСЁ автоматически
override fun onDestroyView() {
    super.onDestroyView()
    exoPlayer?.release() // Гарантированно вызовется
}
```
✅ **Преимущество:** Невозможно забыть release - Android сам управляет

### 4. **Preloading & Smart Buffering**

**ViewPager2 OnPageChangeCallback:**
```kotlin
viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
    override fun onPageSelected(position: Int) {
        // Current: play
        currentFragment.onPageSelected()
        
        // Next: preload
        nextFragment.prepareForPreload()
        
        // Previous-1: destroy
        previousFragment2.onPageUnselected()
    }
})
```

**Для VIDEO это означает:**
- **Current:** `exoPlayer.play()`
- **Next:** `exoPlayer.prepare()` БЕЗ autoplay → буферизация в фоне
- **Previous-1:** `exoPlayer.release()` → освобождение памяти

**Результат:**
- 🎥 Переход к следующему видео < 100ms (уже забуферено)
- 💾 Память не растёт бесконечно (старые плееры удаляются)

### 5. **Swipe Gestures (Native Support)**

ViewPager2 из коробки даёт:
- ✅ Swipe left/right для листания
- ✅ Плавная анимация перехода
- ✅ Edge effects (overscroll bounce)
- ✅ Accessibility support (TalkBack)
- ✅ RTL layout support (right-to-left languages)

**Текущая реализация:**
- Вручную обрабатываем свайпы через GestureDetector
- Нет плавной анимации (резкий переход)
- Нет поддержки RTL

### 6. **Легкость добавления новых типов файлов**

**Хотим добавить поддержку MARKDOWN (.md):**

**С монолитом (сейчас):**
1. Создать `MarkdownViewerManager` (~500 lines)
2. Добавить layout в `activity_player_unified.xml`
3. Изменить `PlayerActivity.displayMedia()` (+50 lines)
4. Изменить `MediaDisplayCoordinator` (+30 lines)
5. Добавить кнопки в command panel (+20 lines)
6. Обработать cleanup в `showNext()`/`showPrevious()`
7. Тестировать взаимодействие с IMAGE/VIDEO/PDF

**С ViewPager2 (планировалось):**
1. Создать `MarkdownPlayerFragment` (150 lines)
2. Добавить в `PlayerFragmentAdapter.createFragment()`:
   ```kotlin
   MediaType.MARKDOWN -> MarkdownPlayerFragment()
   ```
3. Готово ✅

**Время внедрения:** 2 дня → 2 часа

---

## ❌ ЧТО ПОШЛО НЕ ТАК (Уроки)

### 1. **Слишком большой объём изменений за раз**

**26 коммитов за 3 недели:**
- Phase 1-2: Инфраструктура ViewPager2
- Phase 3: VIDEO/AUDIO миграция
- Phase 4-5: PDF/TEXT/EPUB заглушки
- Phase 6: Memory optimization
- Phase 7: Базовая реализация фрагментов
- Phase 8-13: Network support + Position save/restore

**Результат:** Невозможно найти точку где "всё сломалось". Каждый этап добавлял новые баги.

### 2. **Потеря функциональности не отслеживалась**

**Что потеряли (обнаружили ПОЗДНО):**
- Translation/OCR overlay для PDF/TEXT (~600 lines)
- Search функции (~450 lines)
- Edit mode для TEXT (~200 lines)
- Fullscreen mode для PDF (~100 lines)
- Font settings dialogs (~250 lines)
- EPUB TOC/metadata (~350 lines)

**ИТОГО: ~1950 строк функций** пропало незаметно.

**Урок:** Нужен был **checklist** всех features ДО начала рефакторинга.

### 3. **Критичные баги не обнаружены вовремя**

**Найдены только при ручном тестировании:**

```
IllegalStateException: Player accessed on wrong thread
└─ VideoPlayerManager.release() вызван из UI thread
   └─ Фикс: withContext(Dispatchers.Main)

FileNotFoundException: двойной путь (/file.jpg/file.jpg)
└─ BaseFileOperationHandler построил неверный targetPath
   └─ Фикс: нужна переработка path resolution

SMBRuntimeException: Timeout expired  
└─ SmbClient connection pool не закрывает idle connections
   └─ Фикс: нужен connection cleanup timer

IllegalArgumentException: Failed to find configured root
└─ FileProvider path не обновлён для новой структуры кэша
   └─ Фикс: обновить file_paths.xml
```

**Урок:** Нужны **automated tests** для критичных функций (copy/move/share).

### 4. **Legacy код не удалён, создал конфликты**

**PlayerActivity содержит ОБА подхода:**
- ViewPager2 + Fragments (новый)
- Managers + direct views (старый)

**Проблемы:**
- Оба пытаются управлять одними кнопками → конфликты
- Оба слушают ViewModel events → двойная обработка
- Память расходуется на оба подхода

**Урок:** Удалять legacy код **СРАЗУ** после миграции, не держать "на всякий случай".

### 5. **Недостаточное тестирование на каждом этапе**

**Как было:**
- Phase 1-6: "BUILD SUCCESSFUL" ✅ → коммит
- Phase 7-13: "BUILD SUCCESSFUL" ✅ → коммит

**Как надо было:**
- Phase 1: Build ✅ → Manual test IMAGE ✅ → Automated test ✅ → Коммит
- Phase 2: Build ✅ → Manual test VIDEO ✅ → Automated test ✅ → Коммит
- ...

**Урок:** Каждый Phase = отдельная проверка всех функций.

---

## 📋 ПРАВИЛЬНЫЙ ПЛАН (для будущего внедрения)

### Стратегия: **Incremental Migration (Постепенная миграция)**

Вместо "всё сразу" → по 1 типу контента за раз, с полным тестированием.

### Phase 1: Подготовка инфраструктуры (БЕЗ изменения функциональности)

**Цель:** Создать ViewPager2 ПАРАЛЛЕЛЬНО существующему коду.

**Шаги:**
1. Создать `BasePlayerFragment` (base class для всех фрагментов)
2. Создать `PlayerFragmentAdapter` (routing по типам файлов)
3. Добавить ViewPager2 в `activity_player_unified.xml` (по умолчанию СКРЫТ)
4. Добавить настройку `useViewPager2: Boolean` в AppSettings (по умолчанию FALSE)
5. Коммит: "Infrastructure: ViewPager2 base (disabled by default)"

**Тестирование:**
- Build успешен ✅
- Функциональность НЕ изменилась ✅
- Можно откатить за 1 минуту ✅

**Время:** 1 день

---

### Phase 2: IMAGE миграция (первый тип, самый простой)

**Почему IMAGE первым:**
- Нет сложной логики (просто показ картинки)
- Нет ExoPlayer/WebView (меньше багов)
- Glide уже используется (легко портировать)

**Шаги:**
1. Создать `ImagePlayerFragment` (~150 lines):
   - Glide загрузка
   - PhotoView для zoom/pan
   - Сохранение zoom/pan состояния
2. Добавить в `PlayerFragmentAdapter`:
   ```kotlin
   MediaType.IMAGE -> ImagePlayerFragment()
   MediaType.GIF -> ImagePlayerFragment()
   ```
3. В `PlayerActivity.displayMedia()`:
   ```kotlin
   if (appSettings.useViewPager2 && file.type == IMAGE) {
       displayViaViewPager2()
   } else {
       displayViaLegacy() // старый код
   }
   ```
4. Коммит: "Phase 2: IMAGE via ViewPager2 (opt-in via settings)"

**Тестирование:**
- Manual test: Листание 20 IMAGE файлов
- Проверка: Zoom/pan работает
- Проверка: Кнопки (delete/share/favorite) работают
- Проверка: Memory не растёт (Android Profiler)
- Коммит только после 100% работы

**Время:** 2-3 дня

---

### Phase 3: VIDEO миграция (самый критичный)

**Почему VIDEO сложнее:**
- ExoPlayer - тяжёлый объект (memory leaks)
- Нужен preload следующего видео
- Thread safety (player thread vs UI thread)

**Шаги:**
1. Создать `VideoPlayerFragment` (~250 lines):
   - ExoPlayer initialization
   - PlayerView binding
   - Position save/restore (уже есть PlaybackPositionRepository)
   - `prepareForPreload()` для Next page buffering
2. Lifecycle hooks:
   ```kotlin
   override fun onPageSelected() {
       exoPlayer?.play()
   }
   override fun onPageUnselected() {
       exoPlayer?.pause()
   }
   override fun onDestroyView() {
       exoPlayer?.release()
   }
   ```
3. ViewPager2 callback:
   ```kotlin
   onPageSelected(position) {
       currentFragment.onPageSelected()
       nextFragment.prepareForPreload() // buffering
       previous2Fragment.onPageUnselected() // cleanup
   }
   ```
4. Тестирование:
   - ❗ Листать 30 видео подряд → память < 500MB
   - ❗ Переход к следующему < 200ms
   - ❗ Нет IllegalStateException в логах
   - ❗ Position restore работает
5. Коммит: "Phase 3: VIDEO via ViewPager2 with smart preloading"

**Время:** 4-5 дней (с тщательным тестированием)

---

### Phase 4-6: AUDIO, PDF, TEXT (по аналогии)

Каждый тип - отдельный Phase с коммитом.

**AUDIO:** ~2 дня (похож на VIDEO)  
**PDF:** ~3 дня (PdfRenderer + PhotoView для zoom)  
**TEXT:** ~2 дня (TextView + scroll position)

---

### Phase 7: EPUB (самый сложный)

**Почему последним:**
- WebView - риск memory leaks
- HTML preprocessing
- TOC parsing
- Font settings

**Время:** 4-5 дней

---

### Phase 8: Advanced Features (постепенно)

**После того как ВСЕ типы мигрированы:**

1. **Translation/OCR overlay** (PDF/TEXT) - 3 дня
2. **Search functions** (PDF/TEXT/EPUB) - 2 дня
3. **Edit mode** (TEXT) - 1 день
4. **Fullscreen mode** (PDF) - 0.5 дня
5. **Font settings dialogs** - 1 день
6. **EPUB metadata/TOC** - 2 дня

**Каждый feature - отдельный коммит с тестированием.**

---

### Phase 9: Legacy Code Removal

**Только после 2 недель использования новой архитектуры:**
- Удалить ImageLoadingManager
- Удалить VideoPlayerManager (старый)
- Удалить legacy layouts
- Удалить `displayViaLegacy()` code paths

**Результат:** Очистка ~3000 строк мёртвого кода.

---

## 📊 СРАВНЕНИЕ ПОДХОДОВ

| Аспект | "Всё сразу" (то что сделали) | "Постепенно" (правильный план) |
|--------|------------------------------|-------------------------------|
| **Время внедрения** | 3 недели | 4-6 недель |
| **Риск потери функций** | ❌ ВЫСОКИЙ (потеряли 30%) | ✅ НИЗКИЙ (checklist на каждом этапе) |
| **Время на откат** | 1 минута (но теряем ВСЁ) | 1 минута (теряем только 1 Phase) |
| **Критичные баги** | ❌ МНОГО (copy/move/video) | ✅ МИНИМУМ (каждый Phase тестируется) |
| **Параллельная работа** | ❌ НЕВОЗМОЖНА (всё сломано) | ✅ ВОЗМОЖНА (opt-in через settings) |
| **Откат функций** | ❌ Всё или ничего | ✅ По типам (IMAGE работает, VIDEO откатили) |
| **Code review** | ❌ Невозможен (26 коммитов) | ✅ Простой (1 коммит = 1 фича) |

---

## 🎓 УРОКИ ДЛЯ ИИ (почему не справился)

### 1. **Контекст > 10,000 строк - информация теряется**

**PlayerActivity (3500 lines) + 7 managers (6000 lines) = 9500 lines**

❌ ИИ не может держать в памяти все взаимосвязи → забывает features.

### 2. **Нет автоматических тестов - ИИ не знает что сломал**

```kotlin
// ИИ видит:
"BUILD SUCCESSFUL" ✅

// ИИ НЕ видит:
IllegalStateException: Player on wrong thread ❌
FileNotFoundException: wrong path ❌
```

### 3. **Legacy код + новый код = конфликты**

ИИ не может решить:
- Удалить старый код? (потеряем функции)
- Оставить старый код? (конфликты)

Правильно: Удалять СРАЗУ, но ИИ боится это делать.

### 4. **Refactoring требует человеческого планирования**

ИИ хорош в:
- ✅ Написать новый класс по spec
- ✅ Исправить конкретный баг
- ✅ Добавить feature в существующий код

ИИ плох в:
- ❌ Спланировать 10-этапную миграцию
- ❌ Предвидеть side effects
- ❌ Решить "удалить или оставить"

---

## ✅ РЕКОМЕНДАЦИИ ДЛЯ БУДУЩИХ РЕФАКТОРИНГОВ

### 1. **Человек планирует, ИИ исполняет**

**Человек:**
1. Создать детальный план (Phase 1-9)
2. Определить критерии успеха для каждого Phase
3. Создать checklist функций
4. Решить что удалять/оставлять

**ИИ:**
1. Реализовать Phase 1 по spec
2. Исправить баги в Phase 1
3. Дождаться одобрения человека
4. Перейти к Phase 2

### 2. **Маленькие шаги с тестированием**

**Каждый коммит:**
- ≤ 300 строк изменений
- 1 чётко определённая цель
- Manual test ОБЯЗАТЕЛЕН
- Automated test (если критичная функция)

### 3. **Feature flags для безопасности**

```kotlin
// Всегда opt-in для новых фич
if (appSettings.enableViewPager2) {
    newImplementation()
} else {
    legacyImplementation() // откат за 1 секунду
}
```

### 4. **Удалять legacy код немедленно**

**После успешной миграции IMAGE:**
- Удалить ImageLoadingManager СРАЗУ
- Удалить IMAGE-related layout из старого XML
- Не держать "на всякий случай"

### 5. **Создать regression test suite**

```kotlin
@Test fun testCopyFile() { ... }
@Test fun testMoveFile() { ... }
@Test fun testShareFile() { ... }
@Test fun testVideoPlayback() { ... }
```

Запускать ДО и ПОСЛЕ каждого Phase.

---

## 🔮 БУДУЩЕЕ: Когда вернуться к ViewPager2?

### Критерии готовности:

1. ✅ Создан checklist всех функций (200+ пунктов)
2. ✅ Написаны automated tests для критичных функций
3. ✅ Утверждён пошаговый план (Phase 1-9)
4. ✅ Есть 6-8 недель непрерывного времени
5. ✅ Настроен feature flag механизм
6. ✅ Есть rollback план для каждого Phase

### Предварительный timeline:

- **Week 1:** Infrastructure + IMAGE
- **Week 2:** VIDEO (с тщательным тестированием)
- **Week 3:** AUDIO + PDF
- **Week 4:** TEXT + EPUB basics
- **Week 5:** Advanced features (Translation/Search)
- **Week 6:** Font settings, Edit mode, Fullscreen
- **Week 7-8:** Bug fixing + Legacy cleanup

**Total:** 8 недель до полной миграции.

---

## 📌 ЗАКЛЮЧЕНИЕ

**ViewPager2 рефакторинг - ПРАВИЛЬНАЯ ИДЕЯ:**
- ✅ Модульность
- ✅ Memory optimization
- ✅ Preloading
- ✅ Легкость расширения

**Но требует ОСТОРОЖНОГО подхода:**
- ❌ Не "всё сразу"
- ✅ Маленькие шаги
- ✅ Тестирование на каждом этапе
- ✅ Feature flags
- ✅ Immediate legacy cleanup

**Когда вернуться:**
Когда будет время на 6-8 недель методичной работы с тестированием каждого шага.

**А пока:**
Откатиться к рабочей версии, фиксить баги, добавлять features в текущую архитектуру.

---

**Автор:** GitHub Copilot (Claude Sonnet 4.5)  
**Дата:** 17 декабря 2024  
**Версия документа:** 1.0
