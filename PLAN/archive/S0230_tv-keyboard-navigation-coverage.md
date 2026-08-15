# Стратегическая спецификация: S0230 — Ревизия покрытия системы универсального ввода

**Ticket:** S0230
**Status:** BlockNeedUserTest
**Implemented date:** 2026-05-17
**Priority:** 60
**Date:** 2026-05-16
**Tier:** 3 — Moderate (ad-hoc — запрос 2026-05-16)
**Roadmap entry:** Ad-hoc — запрос 2026-05-16
**Tactical spec:** `PLAN/S0230_tv-keyboard-navigation-coverage/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Приложение взаимодействует с пользователями через восемь модальностей ввода: прикосновение (touch), мышь, физическая клавиатура, TV-пульт (D-pad), игровой контроллер/джойстик, кнопки руля автомобиля и медиа-клавиши с Bluetooth-гарнитур / Android Auto / AAC-пультов, физические аппаратные кнопки устройства (volume / mute / search / menu / headset hook), команды ОС через accessibility-сервисы (TalkBack, Voice Access). Текущая реализация покрывает эти модальности несистемно и без явного аудита.

Из 15 Activity только 4 подключены к централизованной системе обработки ввода (компонент обработки gamepad-ввода + компонент привязок клавиш, включая пользовательские комбинации); остальные 11 либо вообще не переопределяют обработчики событий, либо содержат заглушки без действий. Для клавиатуры и TV-пульта компонент gamepad-ввода явно возвращает `null` — он обрабатывает только источники `GAMEPAD`/`JOYSTICK`. Диалоги и нестандартные View не проходили систематического аудита ни по одной модальности.

Триггер задачи — экран приветствия полностью не реагирует на D-pad и клавиатуру на TV. Но это лишь первый обнаруженный пробел в системе покрытия, которая должна гарантировать корректное поведение для каждой модальности на каждом экране и диалоге.

---

## 2. Цели

1. Составлена матрица покрытия: каждый экран и диалог имеет декларированное поведение для каждой из 8 модальностей ввода — `handled` (явная обработка), `pass-through` (нативного поведения достаточно), или `not-applicable`.
2. Экран Welcome навигируется стрелками D-pad и клавишами Tab/Enter: стрелка вправо / Enter → следующая страница, стрелка влево → предыдущая страница, кнопки получают фокус и активируются Enter/DPAD_CENTER.
3. Все экраны имеют начальный фокус при открытии на TV — ни один экран не требует случайного нажатия для инициации навигации.
4. D-pad и клавиатурная навигация работают корректно на каждом экране: стрелки перемещают фокус, Enter / DPAD_CENTER подтверждают действие.
5. Mouse-клики обрабатываются на всех интерактивных элементах; отсутствуют экраны, где кастомный обработчик touch блокирует мышь.
6. Все интерактивные элементы участвуют в accessibility-дереве (TalkBack-фокус, `contentDescription` там где нужно) и не конфликтуют с системными accessibility-событиями.
7. Система обработки ввода централизована в базовом слое UI — добавление нового экрана не требует написания клавиатурной логики с нуля.
8. Компонент обработки gamepad-ввода и компонент привязок клавиш (включая пользовательские комбинации) продолжают работать без изменений для тех экранов, где они уже подключены.
9. Кнопки руля автомобиля (`MEDIA_PLAY_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREVIOUS`, `MEDIA_STOP`, `MEDIA_FAST_FORWARD`, `MEDIA_REWIND`) и `HEADSETHOOK` с Bluetooth-гарнитур транслируются в семантические медиа-действия — экран плеера/аудио может на них реагировать; остальные экраны пропускают их в систему без перехвата.
10. Физические аппаратные кнопки устройства (volume up/down/mute, hardware menu, search) транслируются в семантические действия — по умолчанию пропускаются в систему (volume → AudioManager), активные экраны могут переопределять при необходимости (например, menu → context menu).

**Non-goals:**

- Переработка самих привязок клавиш — только аудит точек подключения к Activity.
- Поддержка TV-пультов с дополнительными кастомными кнопками производителей.
- Ввод текста (поиск, текстовые поля) — нативная обработка ввода не меняется.
- Реализация специализированных accessibility-паттернов UI — только верификация и устранение конфликтов.
- Voice input как метод ввода данных — только обеспечение pass-through от ОС.
- Полноценный Leanback/TenFoot UI-дизайн (отдельная задача).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Экран Welcome исправить в приоритетном порядке — он первое, что видит пользователь на TV.
2. Унифицировать подход: базовый класс Activity должен дать «бесплатную» D-pad-совместимость любому наследнику без дополнительных усилий.
3. Сохранить возможность переопределения навигации в конкретных экранах (например, плеер использует стрелки для перемотки, а не для навигации по фокусу).
4. Аудит охватывает все экраны и диалоги — не только TV-специфичные случаи.

### 3.2 Жёсткие ограничения

- **Flavor:** все флейворы (`standard`, `lite`, `photos`, `legacy`), а также `noLegal` — базовый класс Activity общий для всех.
- **API level:** minSdk 26; решение не должно требовать API выше 26. TV-эмулятор — Android TV API 31+, физический Panasonic MX700 — Google TV (Android 11/12).
- **Wear OS:** не затрагивается.
- **Производительность:** обработка key-событий происходит в главном потоке — реализация должна быть O(1), без I/O и блокировок.
- **Совместимость данных:** нет Room-изменений.
- **Локализация:** ключевые изменения в коде не требуют строковых ресурсов. Если потребуются строки — EN/RU/UK обязательно.
- **Доступность:** изменения не должны ломать TalkBack и не создавать конфликтов с системными accessibility-событиями (AccessibilityService перехватывает фокус независимо от навигационного роутера). Кастомный фокус-менеджмент не должен препятствовать Voice Access. Touch target size: не меняется.

---

## 4. Контекст текущей архитектуры

Базовый класс Activity предоставляет ViewBinding lifecycle, локализацию, wakelock и диспатч touch-событий. Обработка ключевых событий не включена в базовый класс — каждая Activity решает это самостоятельно.

**Состояние по модальностям на сегодня:**

- **Touch:** нативно обрабатывается Android-фреймворком на всех экранах. Кастомные обработчики присутствуют на отдельных View — проверка на конфликты не проводилась.
- **Mouse:** Android транслирует mouse-клики в touch-события для стандартных View. Экраны с кастомным обработчиком touch или View, потребляющими все pointer-события, могут блокировать мышь — систематический аудит не проводился.
- **Клавиатура и TV-пульт:** из 15 Activity только 4 содержат реальную логику (плеер, standalone player, Browse, Main). Остальные 11 не перехватывают ключевые события осмысленно. Компонент обработки gamepad-ввода ориентирован исключительно на источники `INPUT_SOURCE_GAMEPAD/JOYSTICK` и явно возвращает `null` для `SOURCE_KEYBOARD` / `SOURCE_DPAD`. TV-пульт посылает `KEYCODE_DPAD_*` с источником `SOURCE_KEYBOARD` — они игнорируются компонентом gamepad-ввода. Листаемый слайдер не обрабатывает DPAD-стрелки нативно.
- **Джойстик / игровой контроллер:** частично покрыт компонентом обработки gamepad-ввода на 4 Activity; компонент привязок клавиш позволяет пользователю переопределять комбинации. Охват диалогов неизвестен.
- **Кнопки руля / медиа-клавиши Bluetooth:** до текущего расширения вообще не обрабатывались на уровне приложения. Кнопки руля, Android Auto и кнопки Bluetooth-гарнитур посылают `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_HEADSETHOOK` через `dispatchKeyEvent`. Без явного приёма эти события доходили до системного MediaSession и игнорировались плеером приложения.
- **Физические аппаратные кнопки:** `KEYCODE_VOLUME_UP/DOWN/MUTE`, `KEYCODE_MENU`, `KEYCODE_SEARCH` нативно обрабатываются Android (volume → AudioManager, menu → onCreateOptionsMenu в активных экранах). До текущего расширения отдельной точки перехвата на уровне приложения не было — экраны не могли реагировать на эти кнопки централизованно.
- **Accessibility (TalkBack / Voice Access):** аудит не проводился. Неизвестно, имеют ли все интерактивные элементы корректный `contentDescription`; неизвестно, есть ли конфликты между кастомным фокус-менеджментом и accessibility-деревом. Диалоги не проверялись на начальный TalkBack-фокус.

---

## 5. Предлагаемый подход

Ввести в базовый класс Activity централизованную точку диспатча клавиатурных событий, которая умеет обрабатывать TV-пульт и клавиатуру (не gamepad), и предоставляет наследникам hook для переопределения. Каждый экран регистрирует свою «поверхность навигации» — перечень допустимых действий в данном контексте — а базовый слой транслирует сырой KeyEvent в семантическое действие.

### 5.1 Основные столпы

**A. Аудит покрытия (матрица Activity × модальность)**
- Для каждого из 15 Activity и каждого диалога составляется строка матрицы: touch / mouse / keyboard / D-pad / gamepad / accessibility.
- Каждая ячейка получает статус: `handled` (явная корректная обработка), `pass-through` (нативное поведение достаточно — подтверждено), `gap` (выявлен пробел), `not-applicable` (модальность не применима к данному экрану).
- Матрица становится живым документом задачи: завершена, когда нет ячеек со статусом `gap`.

**B. Диспатч клавиатуры, TV-пульта, медиа-клавиш и аппаратных кнопок в базовом слое UI**
- Базовый класс Activity перехватывает точку диспатча ключевых событий и пропускает любые non-gamepad события через единый компонент-роутер.
- Gamepad-события по-прежнему маршрутизируются через компонент обработки gamepad-ввода — существующая логика не меняется.
- Роутер группирует семантические действия в три семейства:
  - **Nav** — `DPAD_LEFT/RIGHT/UP/DOWN/CENTER`, `ENTER`, `TAB`, `BACK` → `Next`, `Prev`, `Up`, `Down`, `Select`, `Back`.
  - **Media** — `MEDIA_PLAY_PAUSE`, `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_STOP`, `MEDIA_NEXT`, `MEDIA_PREVIOUS`, `MEDIA_FAST_FORWARD`, `MEDIA_REWIND`, `HEADSETHOOK` → `PlayPause`, `Play`, `Pause`, `Stop`, `MediaNext`, `MediaPrev`, `FastForward`, `Rewind`.
  - **Hardware** — `VOLUME_UP/DOWN/MUTE`, `MENU`, `SEARCH` → `VolumeUp`, `VolumeDown`, `VolumeMute`, `Menu`, `Search`.
- Наследник может полностью перехватить обработку, вернув `true` из hook-метода. По умолчанию Media и Hardware не перехватываются — экран приветствия, например, возвращает `false` и события доходят до системы (volume → AudioManager, media-keys → MediaSession).

**C. Фокус-менеджмент при запуске Activity**
- Базовый класс устанавливает начальный фокус на первый интерактивный элемент после инициализации компонентов отображения, если устройство является TV (определяется через системный API режима UI).
- Каждая Activity может переопределить, какой View получает фокус первым, через декларативный метод начального фокуса.

**D. Навигация слайдера на экране приветствия**
- Экран приветствия переопределяет hook навигационного события: `DPAD_RIGHT` / `ENTER` → следующая страница, `DPAD_LEFT` → предыдущая, `DPAD_CENTER` → активирует видимую кнопку действия.
- Кнопки внутри экрана приветствия становятся focusable и участвуют в стандартной Android-навигации по фокусу.

**E. Аудит и подключение Activity: keyboard / D-pad**
- Каждая из 11 Activity с неполной обработкой ввода проходит аудит: определяется, нужна ли ей кастомная логика поверх базового диспатча или стандартного поведения достаточно.
- Activity на основе прокручиваемых списков (Browse, Cloud Folder Pickers, Settings, Duplicates) как правило получают полную DPAD-навигацию «бесплатно» через нативный фокус — аудит подтверждает это и при необходимости убирает заглушки обработчика нажатия клавиши.
- Activity с нестандартными layout (Welcome, Resource Editor, Add Resource) требуют явного подключения.

**F. Mouse: верификация нативной обработки**
- Android транслирует mouse-клики в touch-события для стандартных View — явная доработка не требуется там, где нет кастомного touch-обработчика.
- Экраны с кастомным обработчиком touch или View, потребляющими все pointer-события, проходят точечный аудит и при необходимости корректируются.
- Каждый проверенный экран получает статус `handled` или `pass-through` в матрице покрытия.

**G. Accessibility: базовый аудит**
- Все интерактивные View проверяются на наличие корректного `contentDescription` или `labelFor`.
- Кастомный фокус-менеджмент не должен перехватывать accessibility-фокус у TalkBack и Voice Access.
- Каждый диалог получает начальный accessibility-фокус при открытии — без случайного нажатия.

### 5.2 Потоки данных и событий

- Ввод TV-пульта / клавиатуры поступает в точку диспатча базового класса Activity.
- Источник `GAMEPAD` → маршрутизируется в компонент обработки gamepad-ввода без изменений.
- Источник `KEYBOARD` / `DPAD` → новый TV-навигационный роутер транслирует в семантическое действие (`Next`, `Prev`, `Select`, `Back`, …).
- Семантическое действие поступает в hook навигационных событий наследника:
  - экран приветствия → переключение страницы слайдера;
  - экран настроек → стандартный фокус (pass-through);
  - экран плеера → перехватывает в пользу перемотки (уже реализовано).

### 5.3 Точки расширяемости

- Интерфейс навигационной поверхности — описывает набор разрешённых действий для конкретного экрана.
- Hook навигационных событий в базовом классе Activity — каждый наследник может подключиться или делегировать вверх.
- Декларативный метод начального фокуса — для задания начального фокуса без дублирования логики.
- Новый роутер должен быть расширяем для будущих платформ (RemoteControl SDK, Android 13+ TV input API) без переписывания базового класса.

---

## 6. Открытые вопросы / Research items

> Ответы зафиксированы по запросу владельца (2026-05-17) на основе best-practice из публичных источников: developer.android.com, Material Components, Microsoft Mobile Engineering, droidcon, Orange a11y guidelines.

1. **Экраны на прокручиваемых списках: D-pad без кастомной логики** — **Resolved.**
   - **Best practice:** Стандартный `RecyclerView` сам по себе не обеспечивает идеальную DPAD-навигацию на TV. Известные проблемы — потеря фокуса при достижении края списка и «провал» в соседний `View` при удержании стрелки. Рекомендации Android-команды:
     - На TV использовать `VerticalGridView` / `HorizontalGridView` из Leanback вместо обычного `RecyclerView` — у них фокус-менеджмент заточен под D-pad.
     - Если Leanback не подходит — задать `descendantFocusability="afterDescendants"` на `RecyclerView`, чтобы фокус автоматически уходил внутрь, как только адаптер сгенерирует View.
     - Для удержания фокуса на краях — `onInterceptFocusSearch` в кастомном `LayoutManager` или библиотека `dpad-aware-recycler-view`.
   - **Применение в S0230:** Cloud Folder Pickers, Duplicates, Browse и другие списочные экраны **не** мигрируют на Leanback (это вне скоупа спеки). Минимально достаточный шаг: при девайс-тесте проверить, что DPAD ходит по элементам без зависаний. Если фокус «вываливается» — добавить `descendantFocusability="afterDescendants"` на root `RecyclerView` и `getInitialFocusView() = recyclerView` в Activity. Полноценный фокус-edge-handling откладывается до отдельной задачи, если эмулятор покажет, что нативного поведения недостаточно.

2. **Экран плеера: конфликт DPAD между перемоткой и фокусом** — **Resolved.**
   - **Best practice:** Возвратное значение `dispatchKeyEvent` критично — `true` останавливает дальнейший флоу (focus traversal не происходит), `false` пускает событие в `ViewPostImeInputStage.performFocusNavigation`. Канонический паттерн для плеера: Activity перехватывает DPAD в `dispatchKeyEvent`, маршрутизирует в собственный handler (seek / overlay), возвращает `true`. Только если плеер сам решил «фокус важнее seek» (например, открыт `PlayerControlView`) — вызывает `super.dispatchKeyEvent` и пускает событие на focus traversal.
   - **Применение в S0230:** Текущий `PlayerActivity.dispatchKeyEvent` уже следует этому паттерну — после своих проверок вызывает `super.dispatchKeyEvent(event)`, который попадает в `BaseActivity` → `TvKeyRouter` → `onTvNavigation` (default `false`) → super (AppCompat) → `onKeyDown` → `keyboardHandler.handleKeyDown`. То есть seek по DPAD-стрелкам **работает через существующий `keyboardHandler`-путь**, а новый роутер просто не консумит, потому что `onTvNavigation` не переопределён. Никаких дополнительных изменений в плеере не требуется — поведение сохранено.
   - **Future enhancement:** Когда будет реализовано подключение `Media.*` к плеерам (отдельная спека), плеер может переопределить `onTvNavigation`, возвращать `true` для DPAD_LEFT/RIGHT и явно вызывать `seek()`, минуя `keyboardHandler`. Тогда `keyboardHandler` останется только для не-DPAD клавиш.

3. **Надёжность определения TV-режима** — **Resolved.**
   - **Best practice:** Официальная Android-документация (developer.android.com `training/tv/get-started/hardware`) рекомендует **двойную проверку**:
     1. `PackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)` — основной критерий.
     2. `UiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION` — вторичный.
   - `UI_MODE_TYPE_TELEVISION` отдельно подвержен false-positives на «fake Android TV»-устройствах и в редких случаях на телефонах с HDMI-out. `FEATURE_LEANBACK` объявляется устройством на уровне манифеста системы и менее подвержен такому шуму.
   - **Применение в S0230:** Расширить `BaseActivity.isTvDevice()` до OR-комбинации `FEATURE_LEANBACK` плюс существующая `UI_MODE_TYPE_TELEVISION`-проверка. Логика: TV-устройство = `hasSystemFeature(FEATURE_LEANBACK) || uiMode == UI_MODE_TYPE_TELEVISION`. Это покрывает Google TV / Android TV / Fire TV (Leanback заявлен) и не теряет совместимости с устройствами, заявляющими только UI_MODE.
   - **Action item:** небольшая правка `BaseActivity.isTvDevice()` (≈3 строки). Применить в текущем раунде кода.

4. **Mouse-блокировки** — **Resolved.**
   - **Best practice:** Android транслирует mouse-клики (`SOURCE_MOUSE`) в touch-события для стандартных `View`. Кастомные `View` ломают mouse-input в трёх типичных случаях:
     1. `onTouchEvent` перехватывает все события и не вызывает `super.onTouchEvent(event)` — стандартный click pipeline пропадает.
     2. Custom click без override `performClick()` — Lint warning «overrides onTouchEvent but not performClick»; нарушает accessibility и mouse-консистентность.
     3. `ViewGroup.onInterceptTouchEvent` возвращает `true` на ACTION_DOWN без проверки `MotionEvent.TOOL_TYPE_MOUSE` — кастомный жест съедает mouse-click.
   - **Применение в S0230:** Аудит свести к grep-проверке + точечному code review:
     - `Grep onTouchEvent` по `app_v2/src/main/java/**.kt` → найти кастомные View; для каждой проверить вызов `super.onTouchEvent` и наличие `performClick()`.
     - `Grep onInterceptTouchEvent` → проверить логику возврата `true`.
     - При обнаружении нарушений — поправить точечно (override `performClick`, вызвать super) при девайс-тесте.
   - **Action item:** добавить как отдельный grep-чек в device-test instructions §11.7.

5. **TalkBack-фокус в диалогах** — **Resolved.**
   - **Best practice:** `requestFocus()` НЕ перемещает TalkBack-фокус — TalkBack-фокус управляется только через `AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED`. Канонический рецепт:
     1. После `dialog.show()` запостить через `Handler(Looper.getMainLooper()).postDelayed { … }` (50–150 мс).
     2. Вызвать `view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)` на нужном элементе.
     - Альтернатива: `View.announceForAccessibility(text)` для broadcast-уведомлений, или `live region` для динамических текстов.
     - Material `AlertDialog` имеет известную проблему (Material Components issue #1400) — иконка может перехватывать фокус; ставить `android:importantForAccessibility="no"` на декоративные иконки.
   - **Применение в S0230:** Для `AlertDialog` / `DialogFragment`-вызовов через нашу обёртку `DialogHelper` (если есть) — централизовать пост-show focus-helper, который ставит accessibility-фокус на первый интерактивный элемент диалога. Декоративные иконки помечать `importantForAccessibility="no"`.
   - **Action item:** локализовать `DialogHelper` или эквивалент в каталоге → проверить, есть ли единая точка показа диалогов. Если есть — добавить focus-helper там. Если нет — отложить до отдельной спеки a11y-полировки.

6. **Voice Access с кастомными View** — **Resolved.**
   - **Best practice:** Voice Access работает поверх Accessibility-дерева:
     1. Каждая интерактивная кастомная `View` должна иметь `contentDescription` (для image-buttons / decorative-free элементов) или `android:text` (для TextView-подобных).
     2. `contentDescription` — глагол действия, **не** название визуала: «Удалить сообщение», не «Корзина».
     3. **Не** включать «button», «image» в `contentDescription` — accessibility-сервис добавляет тип сам.
     4. Для long-press и custom-action использовать `ViewCompat.addAccessibilityAction(view, label, command)` — даёт Voice Access прочитанное имя действия вместо generic «Double tap and hold».
     5. Для сложных custom-View (например, кастомный слайдер) — виртуальная иерархия через `ExploreByTouchHelper` (`androidx.customview.widget`).
     6. Если две View имеют одинаковый текст-label («Options», «Options»), пользователь Voice Access может говорить число вместо слова — для этого включить overlay через voice-command «Show numbers».
   - **Применение в S0230:** Сводится к двум grep-чекам в device-test:
     - Custom View без `contentDescription` / `android:contentDescription="@string/..."` → добавить локализованный label.
     - Кастомные long-press / swipe actions без `addAccessibilityAction` → добавить.
   - **Action item:** добавить в device-test instructions §11.8 пункт «Voice Access → Show numbers → walk every screen, no unnamed elements».

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Новый базовый диспатч конфликтует с компонентом обработки gamepad-ввода на экранах, где оба активны | Средняя | Двойная обработка одного ключевого события | Явная проверка источника события в роутере: `GAMEPAD` → компонент обработки gamepad-ввода, всё остальное → TV-роутер |
| Экран плеера: стрелки управляют фокусом вместо перемотки | Средняя | Регрессия в главном плеере | Экран плеера явно возвращает `true` из hook-метода навигационного события, блокируя базовый роутер |
| Фокус-менеджмент на телефоне создаёт нежелательный визуальный артефакт (обводка фокуса) | Средняя | Косметический баг на телефоне | Активировать фокус-менеджмент только при TV-режиме UI; фокус-декоратор можно скрыть через стиль темы |
| Некоторые Activity используют кастомные View, не поддерживающие атрибуты направления фокуса | Низкая | Фокус не переходит между элементами | Решается через атрибуты focusability и направления фокуса в XML при аудите каждой Activity |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md` — это UX-исправление существующей функциональности, не новая задекларированная фича. Пользователи TV-платформ получат работающую навигацию, но это не требует обновления публичного каталога фич.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Централизация в базовом классе Activity vs делегат-объект**

- **Решение:** TV-навигационный роутер реализуется как отдельный синглтон-компонент, который вызывается из точки диспатча базового класса Activity. Базовый класс Activity только владеет точкой входа и hook-методом.
- **Альтернативы:** (а) встроить логику напрямую в `BaseActivity`; (б) отдельный `abstract fun handleNavigationKey` без роутера.
- **Почему:** роутер как самостоятельный компонент проверяем в unit-тестах изолированно, не добавляет ответственности в `BaseActivity`, и заменяем без изменения базового класса.

**ADR-2: Scope TV-роутера — TV и клавиатура, не gamepad**

- **Решение:** новый роутер активен только для источников `SOURCE_KEYBOARD` и `SOURCE_DPAD`. Источники `SOURCE_GAMEPAD` / `SOURCE_JOYSTICK` по-прежнему обрабатывает только компонент обработки gamepad-ввода.
- **Альтернативы:** объединить оба роутера в один.
- **Почему:** gamepad и TV-пульт имеют разную семантику (аналоговые оси, повтор кнопок, дедзоны). Объединение усложнит код без выгоды для текущих экранов.

---

## 10. Связи с другими спеками

- S0220 (`google-tv-availability-research`) — параллельно. Результаты S0220 могут повлиять на приоритет данного спека, но не блокируют реализацию.

---

## 11. Критерии готовности (strategic-level)

1. Матрица покрытия заполнена для всех 15 Activity и основных диалогов; нет ячеек со статусом `gap`.
2. На TV-эмуляторе (Android TV API 31+) пользователь проходит весь экран Welcome с помощью D-pad и Enter без прикосновения к экрану.
3. На том же эмуляторе все основные экраны (Main, Browse, Settings, Add Resource, Duplicates, Cloud Folder Pickers) навигируются D-pad без touch.
4. Экран плеера продолжает перехватывать стрелки для перемотки — поведение не изменилось.
5. На физическом телефоне (не TV): нет видимых изменений в поведении фокуса и навигации по сравнению с текущим состоянием.
6. Все 4 экрана, ранее имевшие полную gamepad-интеграцию, продолжают работать с геймпадом и пользовательскими комбинациями идентично текущему поведению.
7. Mouse-клики корректно обрабатываются на всех интерактивных элементах всех экранов (верифицировано вручную или через pointer-events на эмуляторе).
8. TalkBack не теряет фокус при навигации через новый диспатч-роутер; каждый диалог получает начальный accessibility-фокус при открытии.
9. Кнопки руля автомобиля и медиа-клавиши Bluetooth-гарнитур (`MEDIA_PLAY_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREVIOUS`, `HEADSETHOOK`) транслируются роутером в семантические `Media` действия и доступны экранам через `onTvNavigation`; по умолчанию падают через в систему.
10. Физические аппаратные кнопки (`VOLUME_UP/DOWN/MUTE`, `MENU`, `SEARCH`) транслируются в `Hardware` действия; по умолчанию пропускаются в систему — volume управляет звуком, search/menu обрабатываются нативно там, где это релевантно.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0230` — создаст `PLAN/S0230_tv-keyboard-navigation-coverage/` с фазами.

---

## Proposed Structural Changes

### Proposal P-1 — Expand scope to universal input coverage audit  (proposed 2026-05-16 by claude-sonnet-4-5)

**Status:** Accepted
**Affected:** §1, §2, §3 Non-goals, §4, §5, §6, §11, title
**Rationale:** Owner confirmed S0230 is not specifically about TV — it is a revision/audit of the entire universal input coverage system. The app must handle ALL input modalities on ALL activities and dialogs:
- touch (screen taps)
- mouse clicks
- keyboard (physical / USB)
- TV remote (D-pad)
- game controller / joystick (already partially covered)
- OS accessibility commands — TalkBack, voice input for disabled users

The existing system (gamepad component + key binding component, including user-configurable custom combinations) already addresses some modalities. The task is to audit coverage gaps across the full surface and define what needs to be added. TV/Welcome was only the trigger event, not the scope.

**Suggested edits per section:**

> **Title:** "S0230 — Ревизия покрытия системы универсального ввода"
>
> **§1 Problem:** Переформулировать с «D-pad не работает на Welcome» на «система универсального ввода имеет непокрытые точки: часть Activity и диалогов не обрабатывает некоторые модальности корректно».
>
> **§2 Goals:** Добавить цели для каждой модальности; сформулировать задачу как «аудит + восполнение пробелов» вместо «добавить TV-роутер». Добавить явную цель: каждый экран и диалог имеет декларированное поведение для каждой из 6 модальностей (handled / pass-through / not-applicable).
>
> **§2 Non-goals:** Убрать «Leanback/TenFoot UI» из non-goals — он нерелевантен новому скоупу. Добавить явный non-goal: переработка самих привязок (только аудит точек подключения).
>
> **§3 Constraints:** Добавить ограничение: изменения не должны нарушать TalkBack (AccessibilityService) и не создавать конфликтов с системными accessibility-событиями.
>
> **§4 Context:** Расширить: описать текущее состояние каждой модальности — что уже работает (gamepad, частично keyboard), что не работает (mouse, TalkBack integration audit, voice passthrough), что неизвестно (полный аудит диалогов).
>
> **§5 Approach:** Реструктурировать:
> - (A) методология аудита: матрица Activity × модальность;
> - (B) TV-роутер (как есть, уже описан — это одна из ремедиаций);
> - (C) mouse: проверить, что клики обрабатываются нативно; найти экраны, где custom touch handling блокирует mouse;
> - (D) accessibility: убедиться, что все интерактивные элементы имеют `contentDescription` и participates в a11y tree; не конфликтуют с TalkBack-фокусом.
>
> **§6 Open Questions:** Добавить:
> - Есть ли экраны/диалоги с custom `onTouchEvent`, которые блокируют mouse-клики?
> - Какие диалоги не имеют начального TalkBack-фокуса?
> - Корректно ли голосовой ввод Android (Voice Access) работает с нашими кастомными View?
>
> **§11 Done Criteria:** Расширить: добавить критерии для mouse, TalkBack, и кастомных комбинаций; указать, что результат аудита — заполненная матрица покрытия, не только работающий Welcome на TV.

---

## Revision History

- **2026-05-16** — by `/spec-update` (`claude-sonnet-4-5`, focus: all)
  - Applied: 11 (удалены имена классов из стратегической спеки, заменена ASCII-диаграмма в §5.2 на список). Proposed (DISCUSS): 1 (P-1 — расширение скоупа на универсальный ввод).
- **2026-05-16** — P-1 принят владельцем, применён (`claude-sonnet-4-5`).
  - Заголовок спеки: «TV и клавиатурная навигация» → «Ревизия покрытия системы универсального ввода».
  - §1: переформулирована проблема — от «D-pad не работает на Welcome» к «пробелы в покрытии всех 6 модальностей».
  - §2: цели расширены (матрица покрытия, mouse, accessibility); Non-goals обновлены.
  - §3.1: добавлен wish 4 (аудит всех экранов и диалогов); §3.2 accessibility-ограничение расширено.
  - §4: полный контекст по всем модальностям (было только keyboard/gamepad).
  - §5.1: добавлен столп A (матрица аудита), B-E (существующие A-D переименованы), F (mouse), G (accessibility).
  - §6: добавлены вопросы 4–6 (mouse-блокировки, TalkBack-фокус в диалогах, Voice Access).
  - §11: критерии расширены с 5 до 8 (матрица, mouse, TalkBack).

- **2026-05-17** — расширение скоупа на медиа-клавиши и аппаратные кнопки (по запросу владельца в чате, `claude-sonnet-4-6`).
  - §1: модальностей 6 → 8 (добавлены кнопки руля автомобиля / медиа-клавиши Bluetooth, физические аппаратные кнопки).
  - §2 Goals: критерий §1 (матрица) расширен с 6 до 8 столбцов; добавлены цели 9 (медиа-клавиши) и 10 (аппаратные кнопки).
  - §4 Context: добавлены два пункта о текущем состоянии медиа-клавиш и аппаратных кнопок.
  - §5.1 столп B: переименован в «Диспатч клавиатуры, TV-пульта, медиа-клавиш и аппаратных кнопок», описаны три семейства семантических действий (Nav / Media / Hardware).
  - §11: добавлены критерии 9 (медиа-клавиши) и 10 (аппаратные кнопки).
  - Реализация: `TvNavAction` расширен под-интерфейсами `Nav`, `Media`, `Hardware` (+14 новых subtypes); `TvKeyRouter.route()` маппит `MEDIA_*`, `HEADSETHOOK`, `VOLUME_*`, `MENU`, `SEARCH`; source-фильтр упрощён до «всё не-gamepad»; `WelcomeActivity` явно пропускает `Media` / `Hardware` в систему.

## Last Audit

**Date:** 2026-05-17 (run 2 — `/spec-all` F5)
**By:** `/spec-all` (claude-sonnet-4-6)
**Result:** BlockNeedUserTest — code infrastructure complete for non-gamepad input router covering 8 modalities; coverage matrix and device verification deferred.

### §11 Criterion-by-criterion review

| # | Criterion | Code state | Device verification |
|---|-----------|-----------|---------------------|
| 1 | Coverage matrix (8 modalities × all screens + dialogs) | NOT DONE — requires manual audit | n/a |
| 2 | Welcome navigation by D-pad + Enter on TV emulator | DONE — handler + initial focus + focusable buttons | REQUIRED |
| 3 | Main/Browse/Settings/Add Resource/Duplicates/Cloud Folder Pickers via D-pad | INFRASTRUCTURE ONLY — relies on Android native focus; per-screen tuning not yet done | REQUIRED |
| 4 | Player retains arrow seek behaviour | PRESERVED — PlayerActivity.dispatchKeyEvent chains to BaseActivity; default `onTvNavigation` returns false → super → keyboardHandler still runs | REQUIRED |
| 5 | Phone (non-TV) behaviour unchanged | PRESERVED — `isTvDevice()` gates initial focus; routing emits actions but default no-op consumer | REQUIRED |
| 6 | Gamepad / key bindings unchanged on 4 existing screens | PRESERVED — TvKeyRouter excludes SOURCE_GAMEPAD/JOYSTICK | REQUIRED |
| 7 | Mouse clicks across all screens | NOT AUDITED — no code change in this round | REQUIRED |
| 8 | TalkBack / accessibility focus | NOT AUDITED — no code change in this round | REQUIRED |
| 9 | Car steering wheel / Bluetooth media keys | DONE (router emits Media.* for 8 keycodes); player surfaces not yet consume Media.* | REQUIRED (`adb shell input keyevent 79/85/86/87/88/89/90`) |
| 10 | Hardware buttons (volume / mute / menu / search) | DONE (router emits Hardware.* for 5 keycodes); pass-through default keeps volume → AudioManager | REQUIRED (`adb shell input keyevent 24/25/82/84/164`) |

### Implemented (cumulative)

- `TvNavAction` sealed interface with `Nav` / `Media` / `Hardware` sub-interfaces — 19 subtypes total.
- `TvKeyRouter` — `@Singleton`, routes every non-gamepad KeyEvent. Source filter rejects SOURCE_GAMEPAD/JOYSTICK only.
  - Nav keycodes: DPAD_LEFT/RIGHT/UP/DOWN/CENTER, ENTER, NUMPAD_ENTER, TAB (with SHIFT for Prev), BACK.
  - Media keycodes: MEDIA_PLAY_PAUSE, MEDIA_PLAY, MEDIA_PAUSE, MEDIA_STOP, MEDIA_NEXT, MEDIA_PREVIOUS, MEDIA_FAST_FORWARD, MEDIA_REWIND, HEADSETHOOK.
  - Hardware keycodes: VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE, MENU, SEARCH.
- `BaseActivity.dispatchKeyEvent` — routes through TvKeyRouter, offers result to `onTvNavigation` hook, falls through to super if not consumed.
- `BaseActivity.onTvNavigation` — open hook, default returns false.
- `BaseActivity.getInitialFocusView` — open hook for TV initial focus; called after `setupViews()` on TV devices only.
- `BaseActivity.isTvDevice()` — UI_MODE_TYPE_TELEVISION check.
- `WelcomeActivity.onTvNavigation` — Nav: DPAD_RIGHT/TAB → next page, DPAD_LEFT → prev page, DPAD_CENTER/ENTER → click visible primary button, BACK → system back; Media/Hardware → pass-through; Up/Down → focus traversal.
- `WelcomeActivity.getInitialFocusView` — returns `binding.btnNext`.
- `activity_welcome.xml` — `focusable="true"` + `clickable="true"` on all 4 nav buttons.

### Not done — follow-up work

- **Coverage matrix (§11.1)** — produce a table `Activity × {touch, mouse, keyboard, D-pad, gamepad, car wheel, hardware button, accessibility}` with `handled` / `pass-through` / `n/a` per cell. Out of scope for this round.
- **Per-screen audit (§11.3)** — Main/Browse/Settings/Add Resource/Duplicates/Cloud Folder Pickers may need `getInitialFocusView()` overrides; defer to device test to determine which screens are wrong.
- **Mouse audit (§11.7)** — grep `onTouchEvent` / `dispatchTouchEvent` overrides, verify pointer-input compatibility on emulator.
- **Accessibility audit (§11.8)** — TalkBack on emulator; check `contentDescription` on all interactive Views; dialog initial focus.
- **Player surfaces consuming Media.* / Hardware.*** — `PlayerActivity` / `StandalonePlayerActivity` / `VrPlayerActivity` currently use `keyboardHandler` via `onKeyDown`. Migration to `onTvNavigation(Media.*)` is a separate spec — would let car wheel buttons control playback directly.

### Device test instructions

Required for criteria §11.2–10. On Android TV emulator (API 31+) and/or physical Panasonic MX700:

- **D-pad nav (§11.2–3):** launch fresh install → Welcome auto-focuses `btnNext` → press DPAD_RIGHT twice → slides advance → DPAD_LEFT → back; press DPAD_CENTER on Finish → main flow opens. Then walk Main/Browse/Settings/Add Resource without touching the screen.
- **Player seek (§11.4):** open Player → DPAD_LEFT/RIGHT must seek media (existing `keyboardHandler` path), not move focus elsewhere.
- **Phone parity (§11.5):** repeat the same flow on a phone — no focus rectangles, no behaviour change.
- **Gamepad (§11.6):** with a connected gamepad, verify A/B/Start still work on PlayerActivity / BrowseActivity / MainActivity exactly as before.
- **Mouse (§11.7):** plug a USB mouse, click every menu item, dialog button, list row.
- **TalkBack (§11.8):** enable TalkBack — open every dialog from settings and confirm initial focus lands on a meaningful element.
- **Car wheel / media (§11.9):** with a player active, `adb shell input keyevent 85` (PLAY_PAUSE), `87` (NEXT), `88` (PREV). Logcat tag `S0230` should show the routed `Media.*` action. Player surfaces don't consume Media.* yet — events pass through to MediaSession.
- **Hardware (§11.10):** `adb shell input keyevent 24/25/164` (VOLUME_UP/DOWN/MUTE), `82` (MENU), `84` (SEARCH). Volume must change system audio; MENU/SEARCH log `S0230` Hardware action and default to pass-through.

### Verification tags

`Timber.d("S0230: …")` re-inserted at two flow entry points:
- `BaseActivity.dispatchKeyEvent` — fires when the router produces a non-null action (any source).
- `WelcomeActivity.onTvNavigation` — fires when the handler receives an action.

### Device-test findings (round 1, 2026-05-17, on TV emulator)

- **TAB on Welcome page did not focus the language picker buttons** (English / Русский / Українська). Root cause: `TvKeyRouter` mapped `KEYCODE_TAB` to `TvNavAction.Next`, which `WelcomeActivity.onTvNavigation` consumed as a page-advance — blocking the conventional focus-traversal semantics of TAB.
  - **Fix applied:** removed `KEYCODE_TAB` (and `SHIFT+TAB`) from `TvKeyRouter.route()`. TAB now flows through to Android's default focus traversal. `DPAD_LEFT/RIGHT` remain mapped to slider Next/Prev for TV-remote semantics.
  - **Documentation:** updated `TvNavAction.Nav.Next/Prev` KDoc, added explanatory comment block in `TvKeyRouter`.

- **ENTER / DPAD_CENTER on Welcome activated the visible Next/Finish button even when a different button had focus** (e.g. when a language button was focused, ENTER would advance the slider rather than selecting the language). Root cause: `WelcomeActivity.onTvNavigation(Select)` unconditionally synthesised a click on the visible CTA.
  - **Fix applied:** added focus-aware branch in `WelcomeActivity.onTvNavigation(Select)` — if `currentFocus` is a `Button` or `MaterialButton`, return `false` so Android delivers the keypress to the focused view as a click. Synthetic CTA-click only fires when no interactive view holds focus.
