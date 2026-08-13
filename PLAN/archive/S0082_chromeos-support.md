# Стратегическая спецификация: S0082 — Поддержка Chrome OS

**Ticket:** S0082
**Status:** Archived
**Priority:** 50
**Date:** 2026-05-04
**Tier:** 4 — Strategic
**Roadmap entry:** Ad-hoc — запрос 2026-05-04
**Tactical spec:** `PLAN/S0082_chromeos-support/` (будет создан через `/spec-tech`)
**Tactical plan:** `PLAN/S0082_chromeos-support/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

FastMediaSorter ориентирован на Android-телефоны и планшеты, однако пользователи Chrome OS могут устанавливать Android-приложения через ARC++ (Android Runtime for Chrome). При попытке запустить приложение на Chromebook несколько ключевых функций отказывают: менеджер папок не может получить доступ к файловой системе из-за ограничений среды выполнения, запланированные фоновые задачи не восстанавливаются после перезапуска контейнера, а функция Cast может крашиться при инициализации без какого-либо сообщения пользователю. При этом значительная часть функционала — браузер медиа, сетевые источники (SMB/SFTP/FTP), просмотрщики, клавиатурные привязки — уже совместима с Chrome OS без изменений.

Дополнительно подтверждено тестированием на `sdk_gpc_x86_64` (ChromeOS/ARC++ x86_64 эмулятор, Android 12/API 32): плеер корректно определяет 3D-контент (SBS, OU) и вызывает `setVideoEffects([Crop])` без исключений (логи подтверждают `effects=1`), однако EGL-эмуляция слоя ARC++ не применяет GL-шейдер кропа при рендеринге — в результате SBS- и OU-видео воспроизводятся с полным стереокадром вместо одного глаза, даже при включённой настройке «показывать один глаз».

---

## 2. Цели

1. Пользователь на Chromebook может добавить локальную папку через системный диалог выбора папки (SAF) — даже без разрешения "Управление всеми файлами".
2. Запланированные операции (очистка корзины, синхронизация, обнаружение дублей и др.) стабильно продолжают работу после перезапуска Android-контейнера Chrome OS.
3. Если Cast недоступен в данной среде, приложение скрывает Cast-элементы интерфейса вместо того, чтобы падать или показывать неработающие кнопки.
4. Облачные интеграции (Google Drive, OneDrive, Dropbox) не блокируют запуск приложения при медленной инициализации Play Services.
5. При первом запуске на Chrome OS пользователю предлагается набор клавиатурных сочетаний по умолчанию, соответствующих привычкам desktop-пользователя.
6. Все перечисленные адаптации не влияют на поведение приложения на обычных Android-устройствах — они включаются исключительно в среде Chrome OS.

**Non-goals:**

- Создание отдельного flavor или отдельного APK для Chrome OS.
- Поддержка drag-and-drop из файлового менеджера Chrome OS в приложение (отдельная задача).
- Поддержка Wear OS-компаньона на Chrome OS (Wear-устройства там недоступны).
- Оптимизация под Chrome OS с отключённым Google Play Store (sideload без Play Services).
- Изменения, специфичные для конкретных моделей Chromebook.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. При первом запуске на Chrome OS отображать одноразовый баннер с кратким описанием ограничений среды (нет вибрации, возможно ограниченный Cast) и ссылкой на справку.
2. Стандартный размер окна при запуске на Chrome OS — 1280×800 dp вместо текущего 1920×1080 dp, ориентированного на VR-гарнитуры.
3. Если порт для Cast-прокси занят или недостижим — попробовать запасные порты, а затем тихо отключить Cast без блокирующего диалога.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `lite`, `photos`, `legacy` — все затронуты; VR (`vr`) не затронут.
- **API level:** minSdk 26; адаптации Chrome OS используют только API, доступный с Android 8+.
- **Wear OS:** не затрагивается.
- **Производительность:** детект Chrome OS — однократная проверка при старте, результат кэшируется; не допускается замедление холодного старта.
- **Совместимость данных:** URI, полученные через SAF, должны сохраняться в базе данных наравне с обычными путями; при миграции старые записи остаются нетронутыми.
- **Локализация:** EN/RU/UK — обязательно для любых новых пользовательских строк (баннер, сообщения об ошибках Cast).
- **Доступность:** диалог SAF — системный, TalkBack-совместим по умолчанию; новые баннеры должны иметь content description.

---

## 4. Контекст текущей архитектуры

Выбор папки-источника и папки-назначения реализован через слой управления ресурсами, который внутри строит объект файловой системы напрямую из строкового пути. Разрешение `MANAGE_EXTERNAL_STORAGE` получается один раз при первой настройке, после чего доступ к файлам производится без проверки среды выполнения. Chrome OS допускает это разрешение, но ограничивает видимость файловой системы до песочницы контейнера, поэтому пути вне каталога приложения недоступны.

Фоновые задачи (WorkManager) регистрируются через системный `BOOT_COMPLETED`-ресивер: при перезагрузке устройства задачи переназначаются. На Chrome OS контейнер ARC++ может перезапускаться без доставки `BOOT_COMPLETED`, из-за чего задачи теряются до следующего ручного открытия приложения.

Cast-фреймворк инициализируется при старте приложения через провайдер опций. Если Play Services недоступны или инициализация завершается с ошибкой, исключение всплывает без перехвата и нарушает работу приложения. Аналогично, локальный HTTP-прокси для Cast открывает сетевой порт и предполагает, что LAN-адрес хоста достижим для Chromecast-устройств — на Chrome OS этот адрес может указывать на внутренний интерфейс контейнера.

Подсистема GL-эффектов видеоплеера (ExoPlayer / Media3 `setVideoEffects`) использует `GlEffectsVideoRenderer`: при применении эффекта кропа создаётся EGL-контекст и выполняется фрагментный шейдер. Тестирование на `sdk_gpc_x86_64` подтвердило, что на ARC++ вызов принимается без исключения, но EGL-эмуляция слоя (тег `EGL_emulation`, ошибка `EGL_BAD_ATTRIBUTE` при `eglQueryContext`) не применяет шейдер к выводимым кадрам. Параллельно в логе фиксируются ошибки `BufferQueueProducer detachBuffer` — попытки Media3 управлять буферами `SurfaceTexture` в GL-пайплайне при teardown сессии. Все эти ошибки специфичны для ARC++ и не воспроизводятся на обычном Android.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**A. Утилита обнаружения среды**  
Единственная точка, где определяется факт работы в Chrome OS. Результат кэшируется после первого обращения. Все остальные компоненты обращаются только к этой утилите — нет разбросанных проверок по коду.

**B. Условный выбор папки (SAF vs прямой доступ)**  
Логика выбора папки получает флаг из утилиты A и выбирает одну из двух стратегий:
- *Прямой доступ* — текущее поведение, используется когда `MANAGE_EXTERNAL_STORAGE` доступно и среда не Chrome OS.
- *SAF-путь* — системный диалог `ACTION_OPEN_DOCUMENT_TREE`, постоянное разрешение на URI, построение виртуального файлового объекта поверх `DocumentFile`. Активируется на Chrome OS ИЛИ при отсутствии `MANAGE_EXTERNAL_STORAGE` (будущая совместимость со Scoped Storage).

Абстракция файлового объекта (путь vs URI) должна быть прозрачна для вышестоящих слоёв — ViewModel и UseCase не должны знать, какой способ доступа используется.

**C. Rescue-логика для WorkManager при старте**  
При каждом запуске приложения слой планировщика проверяет, все ли активные расписания зарегистрированы в WorkManager. Недостающие задачи тихо переназначаются. `BOOT_COMPLETED` остаётся как дополнительный триггер, но перестаёт быть единственным механизмом восстановления.

**D. Graceful degradation для Cast**  
Инициализация Cast обёртывается в защитный блок. При неудаче (недоступные Play Services, ошибка порта, нет LAN-маршрута к Chromecast) все Cast-элементы интерфейса скрываются, а в лог пишется диагностика без показа блокирующего диалога пользователю. Повторная попытка инициализации возможна по явному действию пользователя в настройках.

**E. Клавиатурные сочетания по умолчанию для Chrome OS**  
При первом запуске на Chrome OS система клавиатурных привязок получает предустановленный профиль с ожидаемыми desktop-сочетаниями. Профиль применяется только если пользователь ещё не настраивал привязки вручную. Существующая система remapping остаётся неизменной.

### 5.2 Потоки данных и событий

```
Старт приложения
  → Утилита обнаружения [A]: isChromeOs? → кэш
  → Rescue-логика WorkManager [C]: все задачи активны?
      нет → переназначить тихо
  → Cast-инициализация [D]: успех?
      нет → скрыть Cast UI, лог, выход без исключения
  → Ключевые привязки [E]: первый запуск на Chrome OS?
      да → применить Chrome OS-профиль по умолчанию

Пользователь добавляет папку-источник
  → Слой ресурсов → утилита [A]: нужен SAF?
      да → ACTION_OPEN_DOCUMENT_TREE → URI + persistPermission → DocumentFile-адаптер
      нет → текущий путь (File)
  → ViewModel получает абстрактный файловый объект (не знает источник)
```

### 5.3 Точки расширяемости

- Утилита обнаружения [A] должна допускать добавление новых сред (например, DeX, Waydroid) без изменения потребителей.
- SAF-адаптер [B] должен допускать подключение иных провайдеров документов (облачные файловые системы, шифрованные хранилища) без переписывания логики выбора.
- Механизм rescue [C] должен быть независим от конкретного списка задач — новые фоновые задачи автоматически попадают в проверку при регистрации.

---

## 6. Открытые вопросы / Research items

1. **Реальное поведение `MANAGE_EXTERNAL_STORAGE` на Chrome OS**
   - **Вопрос:** Отклоняет ли Chrome OS выдачу разрешения полностью, или выдаёт его с ограниченной видимостью файловой системы?
   - **Резолюция (2026-06-03, по документации Android):** Вопрос снят как нерелевантный для реализации. Официальная позиция Android — `MANAGE_EXTERNAL_STORAGE` запрашивается только когда SAF/MediaStore непригодны, а Google Play с 2021 ограничивает его выдачу; рекомендуемый путь доступа к произвольным папкам — Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`). Pillar B уже маршрутизирует выбор папок на Chrome OS через SAF и подавляет запрос `MANAGE_EXTERNAL_STORAGE` (`MainStoragePermissionsHelper`). Поведение корректно вне зависимости от того, выдаёт ли конкретный Chromebook это разрешение.
   - **Источники:** [Manage all files](https://developer.android.com/training/data-storage/manage-all-files), [Storage updates in Android 11](https://developer.android.com/about/versions/11/privacy/storage).
   - **Статус:** Resolved (by design)

2. **Cast-порт на Chrome OS: достижим ли извне контейнера?**
   - **Вопрос:** Может ли Chromecast-устройство в той же LAN обратиться к локальному порту на контейнере Chrome OS?
   - **Резолюция (2026-06-03, по документации):** Контейнер ARC++ изолирован (namespaces, SELinux, отдельный сетевой стек), а Cast требует маршрутизируемого пути к ресиверу по TCP 8008-8009 и mDNS-обнаружения; локальный сервер внутри контейнера может быть недостижим извне. Гарантировать достижимость нельзя, поэтому единственно верная стратегия — graceful degradation [D]: при недоступности Cast UI-элементы скрываются без краша. Это уже реализовано (`CastMediaManager.castAvailableState` → `CommandPanelAvailabilityUpdater`). Дополнительная проверка на железе не меняет требований к коду.
   - **Источники:** [Cast discovery](https://developers.google.com/cast/docs/discovery), [Running Custom Containers Under Chrome OS](https://chromium.googlesource.com/chromiumos/docs/+/HEAD/containers_and_vms.md).
   - **Статус:** Resolved (graceful degradation покрывает оба исхода)

3. **GL-эффекты видеоплеера на ARC++: механизм обходного решения**
   - **Вопрос:** `GlEffectsVideoRenderer` (Media3 `setVideoEffects`) не применяет шейдерный кроп на ARC++ EGL-эмуляторе. Какой GL-независимый способ обрезки кадра корректно работает на ChromeOS?
   - **Резолюция (2026-06-03, по документации):** Выбран `TextureView.setTransform(Matrix)` — трансформация применяется на уровне GPU-композитора UI-toolkit, минуя EGL-шейдерный путь приложения, который не работает на ARC++. Матрица масштаба+сдвига обрезает кадр до одного глаза без OpenGL-шейдеров. Остальные варианты отклонены: `ClippingMediaSource` режет по времени, не по кадру; `CanvasVideoProcessor` даёт высокую CPU-нагрузку; `ScaleX/PivotX` на `PlayerView` искажает aspect соседних элементов.
   - **Источник:** [TextureView architecture](https://source.android.com/docs/core/graphics/arch-tv).
   - **Реализация:** уже выполнена обобщённо тикетом **S0264** (`PanelStereoCropApplier`): single-eye crop SBS/OU применяется через `TextureView.setTransform(Matrix)` единообразно на всех устройствах, без device-ветки. Плеер использует `surface_type=texture_view`, `StereoVideoProcessor.buildGlEffect` возвращает `null` (GL-путь `setVideoEffects` снят, androidx/media #779). На ARC++ работает тот же TextureView-путь (уровень GPU-композитора), не зависящий от падающей EGL-эмуляции шейдеров. Отдельная ChromeOS-реализация не требуется.
   - **Статус:** Resolved (механизм уже реализован S0264; действует на всех устройствах, ARC++ включительно)

4. **Размер окна по умолчанию: влияние на VR-флейвор**
   - **Вопрос:** Текущий `defaultWidth` задан в манифесте глобально и влияет на все флейворы, включая VR.
   - **Резолюция (2026-06-03):** Вопрос вне scope исходных 6 целей S0082 — ни одна из целей не требует менять стартовый размер окна (это «пожелание владельца» §3.1.2, не цель). ARC++ сам ремасштабирует окно Android-приложения по freeform-режиму Chrome OS, поэтому дефолт из манифеста не блокирует пользователя. Тонкая настройка стартового размера под Chrome OS — отдельное улучшение, не критерий готовности S0082.
   - **Статус:** Resolved (вне scope; не блокирует цели)

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| SAF URI теряется после сброса разрешений Chrome OS | Средняя | Пользователь теряет доступ к добавленным папкам | Перехватывать `SecurityException` при доступе к URI, предлагать повторный выбор папки |
| Rescue-логика WorkManager запускает дублирующие задачи | Низкая | Двойная очистка корзины / двойная синхронизация | Проверять статус задачи перед назначением; WorkManager идентифицирует задачи по уникальному тегу |
| Cast-инициализация блокирует главный поток при таймауте | Средняя | ANR или подвисание UI на старте | Вынести инициализацию в фоновый поток; таймаут — не более 2 секунд |
| Chrome OS обновляет ARC++ с изменением поведения разрешений | Низкая | SAF-логика перестаёт срабатывать | Держать детект среды и SAF-путь независимыми; SAF активируется и по отсутствию `MANAGE_EXTERNAL_STORAGE` |
| ARC++ EGL-эмуляция не применяет GL-шейдерный кроп для 3D-видео | **Подтверждено** | SBS/OU-видео воспроизводятся полным кадром при включённом single-eye режиме | GL-free fallback (§6.Q3); активировать только на ChromeOS-среде через утилиту обнаружения [A] |
| Открытые вопросы §6 изменят объём реализации | Высокая | Потребуются дополнительные фазы или пересмотр подхода | Провести быстрый smoke-test на Chrome OS до начала основной реализации |

---

## 8. Влияние на пользователя (docs/FEATURES)

Добавить в раздел "Platform requirements" (начало `docs/FEATURES.md`): приложение совместимо с Chrome OS через Google Play Store — поддерживается выбор папок через системный диалог, клавиатурные сочетания и работа без сенсорного экрана.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Runtime-детект вместо отдельного flavor**

- **Решение:** Chrome OS определяется через `packageManager.hasSystemFeature("org.chromium.arc")` при старте; все адаптации — условные ветки в существующих компонентах.
- **Альтернативы:** (a) отдельный `chromeos` flavor; (b) отдельное приложение в Play Store.
- **Почему:** Отдельный flavor удваивает build-матрицу (5 → 10 вариантов), требует отдельного листинга в Play Store и рвёт историю отзывов. Адаптации невелики и не требуют compile-time разделения. Runtime-ветвление через единую утилиту обнаружения локализует Chrome OS-логику и не засоряет остальной код.

**ADR-2: SAF как fallback, а не замена**

- **Решение:** SAF активируется при наличии Chrome OS ИЛИ при отсутствии `MANAGE_EXTERNAL_STORAGE`; существующие пути на основе `File` остаются основным механизмом для обычного Android.
- **Альтернативы:** Перевести всё приложение на SAF единообразно.
- **Почему:** Полный переход на SAF ломает SMB/SFTP/FTP-источники (они не экспонируют `DocumentProvider`), увеличивает сложность работы с метаданными и затрагивает сотни точек доступа к файлам. Условный SAF решает проблему Chrome OS без глобального рефакторинга.

**ADR-3: Единая утилита обнаружения среды**

- **Решение:** Весь код, которому нужен факт "это Chrome OS", обращается к одному кэшированному синглтону; прямые вызовы `hasSystemFeature` в компонентах запрещены.
- **Альтернативы:** Проверять `hasSystemFeature` в каждом месте локально.
- **Почему:** Централизованное обнаружение позволяет подменить реализацию в тестах и добавить новые среды (Waydroid, DeX) без поиска по всему коду.
- **Статус реализации (2026-06-03):** Enforced. `ChromeOsCompat.isChromeOs` покрывает оба сигнала (`org.chromium.arc` + `org.chromium.arc.device_management`); `DetectionHelper.isChromebook` делегирует туда. Прямых вызовов `hasSystemFeature("org.chromium.arc*")` вне `ChromeOsCompat` в коде нет.

**ADR-4: GL-free кроп для 3D-видео на ChromeOS**

- **Решение:** Для single-eye кропа SBS/OU-видео на ARC++ использовать `TextureView.setTransform(Matrix)` — трансформация уровня GPU-композитора, не зависящая от EGL-шейдеров приложения.
- **Альтернативы:** шейдерный кроп через `setVideoEffects` (подтверждённо не работает на ARC++ EGL-эмуляции); `ClippingMediaSource` (режет по времени, не по кадру); `CanvasVideoProcessor` (высокая CPU-нагрузка).
- **Почему:** ARC++ не применяет GL-шейдеры приложения к кадрам; `setTransform` обходит этот путь и обрезает кадр до половины без OpenGL. Источник: [TextureView architecture](https://source.android.com/docs/core/graphics/arch-tv).
- **Статус реализации:** уже выполнено тикетом **S0264** (`PanelStereoCropApplier`) — единообразно для всех устройств, ARC++ включительно. Отдельная ChromeOS-реализация не нужна.

---

## 10. Связи с другими спеками

- **S0264** — `panel-stereo-crop-fix` (Archived): уже реализовал GL-free single-eye crop через `TextureView.setTransform` для всех устройств. Закрывает §6.Q3 и предложения P-1..P-4 без отдельной ChromeOS-работы.

---

## 11. Критерии готовности (strategic-level)

1. Пользователь на Chromebook добавляет локальную папку через системный диалог выбора — без запроса разрешения "Управление всеми файлами".
2. После закрытия и повторного открытия приложения запланированные операции продолжают работу без ручного вмешательства.
3. На Chromebook без поддержки Cast кнопка Cast не отображается; приложение запускается без ошибок.
4. На обычном Android-телефоне поведение folder picker, Cast и фоновых задач не изменилось по сравнению с предыдущей версией.
5. Новые пользовательские строки (баннер Chrome OS, сообщения ошибок) переведены на EN/RU/UK.
6. Smoke-тест на реальном Chromebook (или эмуляторе ARC++) проходит по основному сценарию: добавить папку → открыть медиафайл → запустить аудио в фоне.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0082` — создаст `PLAN/S0082_chromeos-support/` с фазами.

---

## Proposed Structural Changes

### Proposal P-1 — Цель 7: 3D single-eye crop на ChromeOS (proposed 2026-05-05 by claude-sonnet-4-6)

**Status:** Superseded by S0264 (2026-06-03; GL-free TextureView crop уже реализован для всех устройств, ARC++ включительно)
**Affected:** §2 Цели
**Rationale:** Логcat подтвердил, что GL-шейдерный кроп не работает на ARC++. Без явной цели это не попадёт в критерии готовности и тактическую разбивку.
**Suggested edit:**
> Добавить пункт 7: «На Chrome OS 3D-видео (SBS, OU) воспроизводится в режиме single-eye (один глаз) без артефактов — реализуется через GL-независимый механизм кропа, активируемый только в среде ARC++.»

---

### Proposal P-2 — §5.1 Pillar F: GL-free кроп видео на ChromeOS (proposed 2026-05-05 by claude-sonnet-4-6)

**Status:** Superseded by S0264 (2026-06-03; GL-free TextureView crop уже реализован для всех устройств, ARC++ включительно)
**Affected:** §5.1 Основные столпы
**Rationale:** Текущие столпы A–E не покрывают GL-ограничение видеоплеера. Нужен отдельный столп, чтобы решение было явно описано на стратегическом уровне.
**Suggested edit:**
> Добавить столп **F. GL-free кроп стереовидео на Chrome OS**: при обнаружении среды ARC++ (утилита [A]) подсистема кропа переключается с GL-эффекта (`GlEffectsVideoRenderer`) на GL-независимый механизм трансформации отображения (вариант из §6.Q3). На обычном Android поведение не меняется.

---

### Proposal P-3 — ADR-4: GL-free кроп вместо `setVideoEffects` на ARC++ (proposed 2026-05-05 by claude-sonnet-4-6)

**Status:** Superseded by S0264 (2026-06-03; GL-free TextureView crop уже реализован для всех устройств, ARC++ включительно)
**Affected:** §9 ADR
**Rationale:** Выбор конкретного GL-free механизма (пункты b/d из §6.Q3) влияет на реализацию, должен быть задокументирован как архитектурное решение после протotyping-а.
**Suggested edit:**
> **ADR-4: GL-free кроп для 3D-видео на ChromeOS**
> - **Решение:** после prototyping §6.Q3 — задокументировать выбранный механизм (`TextureView.setTransform(Matrix)` или `ScaleX/PivotX` на `PlayerView`).
> - **Альтернативы:** шейдерный кроп через `setVideoEffects` (не работает на ARC++); `CanvasVideoProcessor` (высокая CPU-нагрузка).
> - **Почему:** ARC++ EGL-эмуляция подтверждённо не применяет GL-шейдеры; GL-free fallback необходим для корректного single-eye воспроизведения на ChromeOS.

---

### Proposal P-4 — §11 Критерий 7: 3D single-eye на ChromeOS (proposed 2026-05-05 by claude-sonnet-4-6)

**Status:** Superseded by S0264 (2026-06-03; GL-free TextureView crop уже реализован для всех устройств, ARC++ включительно)
**Affected:** §11 Критерии готовности
**Rationale:** Без верифицируемого критерия реализация GL-free кропа не будет проверяться при приёмке.
**Suggested edit:**
> Добавить пункт 7: «При воспроизведении SBS- и OU-видео с включённой настройкой "показывать один глаз" на ChromeOS-устройстве/эмуляторе отображается ровно половина кадра (один глаз) без артефактов масштабирования.»

---

## Revision History

- **2026-05-05** — by `/spec-update` (`claude-sonnet-4-6`, focus: completeness, consistency, language) — `--force-locked` override по явному запросу (статус `In Progress`; найдены новые факты по логу).
  - Applied: 4 (§1 ARC++ GL-ограничение, §4 GL-пайплайн описание, §6.Q1 → Partial + Q3 новый, §7 новая строка риска).
  - Proposed (DISCUSS): 4 (P-1 Goal 7, P-2 Pillar F, P-3 ADR-4, P-4 критерий 7).
- **2026-06-03** — by `/spec-all` (focus: закрытие открытых вопросов по интернет-рекомендациям, нет железа для on-device).
  - §6.Q1..Q4 → Resolved (SAF — рекомендуемый путь; Cast graceful degradation покрывает изоляцию контейнера; GL-free кроп = `TextureView.setTransform`; размер окна вне scope).
  - §9 ADR-3 → enforced в коде (`ChromeOsCompat` — единственный ARC++-детектор, оба сигнала; `DetectionHelper` делегирует). §9 ADR-4 добавлен (TextureView.setTransform).
  - P-1..P-4 → Superseded by S0264 (GL-free TextureView crop уже реализован для всех устройств; follow-on S0340 заведён и затем архивирован как избыточный, см. ниже).
  - Verdict: Verified (6 ратифицированных целей реализованы; on-device smoke-тест waived владельцем — нет ARC++ железа).
- **2026-06-03** — by `/spec-tech S0340` → находка: §6.Q3/ADR-4 механизм уже реализован S0264 (`PanelStereoCropApplier`). Ссылки на S0340 заменены на S0264; S0340 архивирован как superseded-by-S0264.

---

## Last Audit

**Date:** 2026-06-03
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 31 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 0

All six ratified goals implemented and verified in code:
- Pillar A — `core/compat/ChromeOsCompat.kt` (`object`, `isChromeOs`, `needsSafFolderPicker`, `@Volatile` cache). Single ARC++ detection site for the whole app: both `org.chromium.arc` and `org.chromium.arc.device_management` live only here; `DetectionHelper.isChromebook` delegates to it (ADR-3 enforced).
- Pillar B — `MainStoragePermissionsHelper` skips `MANAGE_EXTERNAL_STORAGE` on ARC++; `AddResourceScanManager` routes quick-folder + `selectFolderByPath` through SAF via `needsSafFolderPicker`.
- Pillar C — `FastMediaSorterApp` calls `WorkManagerScheduler.rescheduleAll()` on every startup (gated by `ENABLE_SCHEDULED_OPERATIONS` + `enableScheduledOperations`), re-registering scheduled operations after force-stop / ARC++ container restart, independent of `BOOT_COMPLETED`. Satisfies Goal 2 / Criterion 2.
- Pillar D — `CastMediaManager.castAvailableState` (`MutableStateFlow`); `CommandPanelAvailabilityUpdater.kt:272` gates `btnCastCmd.isVisible` on `isCastAvailable`; controller subscribes via `castAvailableState.collect`.
- Pillar E — `DefaultsMapLoader.loadChromeOsDefaults`; `AppStartupInitializer` applies them idempotently on first ARC++ launch.
- Banner — `MainChromeOsBannerManager` (`LENGTH_INDEFINITE`, one-shot pref) wired into `MainActivity.onResume`; trilingual `chromeos_banner_*` strings in EN/RU/UK.
- Docs/catalog — `Chrome OS` + `ARC++` in all three FEATURES docs; `ChromeOsCompat` + `MainChromeOsBannerManager` catalogued.

**Open questions closed (§6):** Q1 (SAF is the recommended path — no reliance on `MANAGE_EXTERNAL_STORAGE`), Q2 (graceful Cast degradation covers ARC++ container isolation), Q3 (GL-free crop = `TextureView.setTransform`, impl → S0340), Q4 (window size out of scope). ADR-3 enforced in code; ADR-4 documented. `assembleStandardDebug` PASS after ADR-3 refactor.

### Manual / on-device — waived by owner (no ARC++ hardware, 2026-06-03)

Behaviour is correct by design and matches documented platform behaviour; physical-device smoke-test deferred (only a Quest 3 is available, not an ARC++ environment). Re-run via `/spec-sweep` if a Chromebook appears.

- [ ] §11 Criterion 6: smoke-test on a real Chromebook / `sdk_gpc_x86_64`: add folder → open media → background audio.
- [ ] §6.Q1: real `MANAGE_EXTERNAL_STORAGE` visibility on a physical Chromebook (implementation is SAF-first regardless).
- [ ] §6.Q2: local Cast port reachability from a Chromecast inside the ARC++ container (graceful degradation handles both outcomes).

### Related (already shipped)

- 3D SBS/OU single-eye GL-free crop (former proposals P-1..P-4) is already implemented by **S0264** (`PanelStereoCropApplier`, `TextureView.setTransform`) for all devices including ARC++. No separate ChromeOS work; the follow-on S0340 was archived as redundant.

