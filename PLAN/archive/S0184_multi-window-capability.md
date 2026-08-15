# Стратегическая спецификация: S0184 — Multi-window capability для desktop-like Android-сред (VR, XR, ChromeOS, Chromebook/Googlebook, Samsung DeX)

**Ticket:** S0184
**Status:** Verified
**Priority:** 50
**Date:** 2026-05-18
**Tier:** 4 — Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc — запрос 2026-05-13, переориентация 2026-05-18
**Tactical spec:** `PLAN/S0184_multi-window-capability/`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Часть устройств, на которых работает приложение, поддерживает desktop-like мультиоконные сценарии: Meta Quest и Android XR-окружения позволяют развести несколько окон одного приложения в пространстве, ChromeOS и Chromebook/Googlebook трактуют Android-приложения как resizable окна на рабочем столе, Samsung DeX превращает телефон в десктоп с управляемыми окнами. На уровне Android-приложения это один и тот же capability: несколько одновременных, независимых window-сессий одной apk.

Текущая продуктовая рамка приложения описывает телефоны, планшеты, TV, head unit, VR и отдельные точечные window-aware сценарии, но не фиксирует единый контракт поведения для нескольких одновременных окон одного и того же приложения. Из-за этого:

- В кодовой базе сохранились window-aware entrypoint-ы и window-identity plumbing для отдельных поверхностей, но пользовательская экспозиция противоречива: часть action-ов скрыта или заглушена, тогда как публичный каталог возможностей всё ещё заявляет Multi-Window Mode.
- На ChromeOS/Chromebook resizable окна вскрывают нестабильные layout и action placement.
- На Samsung DeX и Android XR поведение не определено и не проверено.
- Нет продуктового контракта, какие поверхности обязаны корректно работать в параллельных окнах и как изолируется их состояние.

---

## 2. Цели

1. Зафиксировать multi-window capability как поддерживаемый класс сценариев приложения, а не как побочный эффект отдельных device-specific обходных путей.
2. Обеспечить предсказуемую работу ключевых app-surface-ов в resizable и параллельных окнах на всех целевых средах: VR, Android XR, ChromeOS, Chromebook/Googlebook, Samsung DeX.
3. Сохранить независимый window-context для параллельных окон без взаимного перетирания навигационного, selection и playback/viewer состояния.
4. Сделать поведение приложения понятным для keyboard, touchpad и pointer-first сценариев, характерных для laptop-like и desktop-like сред.
5. Не показывать сломанные или бессмысленные affordance там, где конкретная platform/window capability отсутствует.
6. В `standard`-версии и остальных app flavor-ах добавить пользовательскую настройку, разрешающую открытие поверхностей в отдельном окне.
7. При включённой настройке показать две базовые команды v1: «Открыть в новом окне» в меню ресурса и «Открыть в новом окне» в меню файла.

**Non-goals:**

- Создание отдельного flavor под любую из целевых сред.
- Vendor-specific OS интеграции вне самой Android app surface (phone-sync, Gemini workflows, DeX-launcher features, XR system shell).
- Интеграция с Google Books или Google Play Books — Googlebook здесь означает laptop-like Android device, а не книжный сервис.
- Полный редизайн всех экранов приложения в первой итерации.
- Поддержка multi-window на Wear OS.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Первая итерация должна опираться на уже существующие adaptive и multi-window механизмы платформы, а не строить отдельный «режим только для одного устройства».
2. Решение должно описываться через capability (resizable окна, несколько одновременных окон, keyboard/pointer-first), а не через бренд устройства.
3. В фокусе первой итерации должны быть основные пользовательские сценарии: запуск, навигация, открытие контента, параллельные окна и базовая управляемость с клавиатуры и указателя.
4. Иллюстративный сценарий, который удобно использовать при ручной проверке: три параллельных окна одной apk — в одном проигрывается музыка, во втором просматривается фото/видео, в третьем выполняются операции над файлами. Это пример продуктовой ценности, а не жёсткий v1 acceptance.
5. Настройка multi-window должна существовать уже в `standard` flavor. По умолчанию она включена только в средах, где runtime capability уверенно указывает на desktop-like или XR/panel multi-window: VR, Android XR Home Space, ChromeOS, Chromebook/Googlebook и Samsung DeX при надёжном определении. На обычных телефонах и планшетах она выключена по умолчанию, но может быть включена пользователем, если система реально поддерживает multi-window.
6. Команда из меню ресурса открывает Browse выбранного ресурса в новом независимом окне. Команда из меню файла открывает соответствующий viewer/player этого файла в новом независимом окне.

### 3.2 Жёсткие ограничения

- **Flavor:** все текущие Android app flavor-ы остаются in scope на уровне form-factor совместимости; существующие feature-gate по flavor продолжают работать без расширения объёма возможностей.
- **API level:** включение multi-window поведения должно опираться на реальные platform capability, а не только на маркетинговое имя устройства. Нижняя граница API проекта не меняется.
- **Wear OS:** не затрагивается.
- **Производительность:** несколько одновременных окон не должны провоцировать ненужное дублирование тяжёлых фоновых операций, полных пересканов, прогрева кэшей и агрессивного повторного построения UI.
- **Совместимость данных:** параллельные окна не должны ломать существующее сохранение навигационного контекста, последней позиции просмотра и пользовательских настроек поверхности.
- **Локализация:** EN/RU/UK обязательно. Новые пользовательские строки должны соответствовать `docs/COMMUNICATION_POLICY.md`; tone checklist из §6 политики — обязательный gate перед интеграцией.
- **Доступность:** клавиатурная навигация, предсказуемый focus order, TalkBack-совместимость и не-цветовое различение состояний обязательны.

---

## 4. Контекст текущей архитектуры

Архитектура приложения уже умеет несколько важных для multi-window вещей, но пока как набор разрозненных capability-path-ов. У flat/panel части уже есть large-window launch hints, keyboard-friendly сценарии, window-local resume-state для части потоков и явная identity окна для browse/player сценариев. Отдельно от этого уже существует platform-specific compatibility path для ChromeOS и собственный capability-detector для XR/Quest среды.

При этом текущий AS-IS неоднороден. Настройка multi-window и локализованные строки уже существуют, но видимые entrypoint-ы расходятся с этим контрактом: часть browse-side action-ов принудительно скрыта или превращена в no-op, а player-side доступность отдельного окна принудительно отключается независимо от сохранённой настройки. Persisted state тоже смешан: resume-state уже может быть window-local, но filter state и часть browse restore-механики остаются shared. Это хороший фундамент для S0184, но не готовая capability policy.

---

## 5. Предлагаемый подход

Поддержку multi-window нужно строить как form-factor capability layer над уже существующим Android-приложением. На стратегическом уровне это означает, что приложение должно уметь распознавать desktop-like или multi-window-capable среду, включать для неё корректный набор UI и navigation contract, а также поддерживать несколько независимых window-session-ов без конфликта состояния.

При этом flat/panel multi-window и immersive XR нельзя считать одним и тем же режимом. Общий multi-window contract относится к resizable panel/window surface-ам приложения. Immersive XR-host остаётся соседним capability-profile с отдельным fullscreen lifecycle и собственными affordance-правилами.

### 5.1 Основные столпы / модули

**Определение device capability и form-factor policy**

Единый слой, который решает, в какой capability-среде находится текущий процесс приложения (resizable single-window, multi-window parallel, XR-spatial) и какие правила UI/interaction при этом активируются. Решение строится по capability, а не по бренду; имя устройства — диагностический сигнал, а не gate.

**Window-session orchestration**

Каждое окно приложения получает собственную identity и собственный active context, чтобы несколько параллельных окон не делили одно и то же живое состояние навигации, выбора элементов и просмотра контента. Контракт явно описывает, что window-local, а что синхронизируется с глобальным состоянием.

**Adaptive surface contract**

Ключевые поверхности приложения имеют согласованный contract для resizable окон: размещение action-ов, fallback поведения, допустимость split/multi-window и реакцию на изменение размера окна.

**Desktop input model**

Keyboard, pointer и touchpad рассматриваются как полноценные основные способы управления, а не как вторичный бонус поверх touch-only UX.

**User-facing multi-window toggle and entry points**

Во всех app flavor-ах, включая `standard`, появляется настройка, разрешающая явные multi-window команды. Дефолт включён только для уверенно multi-window-capable desktop-like/XR сред, а не по маркетинговому имени устройства. При выключенной настройке команды скрыты и приложение ведёт себя как раньше.

V1 entry points фиксированы:

- В меню ресурса команда «Открыть в новом окне» создаёт независимое Browse-окно для выбранного ресурса.
- В меню файла команда «Открыть в новом окне» создаёт независимое окно viewer/player для выбранного файла.

### 5.2 Потоки данных и событий

- Приложение запускается или переносится в multi-window-capable среду (resizable окно, второе окно, XR-пространство, DeX-десктоп).
- Слой capability/policy определяет window environment и активирует соответствующий adaptive contract.
- Пользователь открывает контент, переключается между поверхностями или создаёт второе/третье окно.
- Координатор окна выдаёт новой window-сессии собственную identity и собственный active context.
- UI и слой состояния синхронизируются внутри границ конкретного окна, а глобальное сохранение контекста обновляется только по безопасной политике commit.
- Keyboard/pointer interaction проходит через единые правила focus, shortcut и action availability для desktop-like среды.

### 5.3 Точки расширяемости

- Будущая оптимизация под специфические window patterns: snap layouts, picture-in-picture, внешние дисплеи, XR-spatial layouts.
- Более глубокая desktop productivity интеграция: drag-and-drop, richer shortcut layers, multi-pane workflows.
- Vendor-specific улучшения поверх базового capability-layer (DeX-launcher hints, XR-spatial affordance, ChromeOS shelf integration) без раздвоения архитектуры.

---

## 6. Открытые вопросы / Research items

1. **Какие поверхности входят в обязательный v1 scope**
   - **Вопрос:** какие app-surface-ы обязаны иметь полноценный multi-window contract в первой итерации.
   - **Исследование:** Android adaptive quality трактует поддержку как прохождение critical task flows в текущем display mode, а не как полную оптимизацию каждого экрана. Android multi-window docs отдельно требуют проверки обычного использования в разных конфигурациях окна, сохранения видимости essential functionality и приемлемой производительности при resize. S0028 уже доказала продуктовую ценность связки Browse + Player/viewer + file operations, но текущий код содержит исторические/отключённые S0028 entrypoint-ы после OpenXR-переезда, поэтому нужен baseline audit перед тактикой.
   - **Ответ для v1:** обязательный v1 scope — Main/resource entry, Browse/file list, unified Player/viewer для поддержанных flavor-ом типов медиа, file-operation panels и минимальный Settings/diagnostics путь для управления capability. Локальный аудит кодовой базы подтверждает, что отдельная window identity и отдельный запуск уже реально подготовлены прежде всего для browse/player потоков; остальные настройки, auth и редкие служебные flows должны быть resizable-safe, но не входят в acceptance параллельных окон первой итерации.
   - **Статус:** Researched

2. **Какая политика сохранения состояния нужна для параллельных окон**
   - **Вопрос:** что именно должно быть window-local, а что может оставаться общим global state при параллельной работе.
   - **Исследование:** Android multi-instance моделируется через отдельные root activity/task окна. Android 10+ multi-resume означает, что несколько окон могут оставаться активными одновременно; на старых версиях видимое неактивное окно может быть не `RESUMED`, но всё равно должно сохранять корректное пользовательское состояние. Архивная S0028 уже фиксировала конфликт общего resume-state между окнами.
   - **Ответ для v1:** гибридная модель. Window-local: navigation stack, active resource/file, selection, transient command panel/dialog state, viewer/player UI state и resume-state конкретного окна. Локальный аудит кодовой базы уже показывает, что resume-state живёт по window key, а live selection естественно изолирована экземпляром поверхности. Global: resource database, file mutations, credentials, app settings, feature flags, stored browse filters и per-resource last viewed / scroll restore. Конфликты глобального состояния решаются через существующую модель источников данных и явные refresh/commit rules, без primary-window приоритета.
   - **Статус:** Researched

3. **Как ранжируются целевые среды по приоритету и тестируемости**
   - **Вопрос:** при ограниченных ресурсах какая среда из (VR, Android XR, ChromeOS, Chromebook/Googlebook, Samsung DeX) должна получать первый круг ручной проверки, какая — второй, какая — best-effort.
   - **Исследование:** Google рекомендует тестировать adaptive behavior на Chromebook-class размере окна и проверять multi-window/freeform resize. Samsung DeX следует Android Multi-Window best practices плюс keyboard/mouse и runtime configuration changes. Android XR Home Space запускает совместимые Android-приложения в ограниченной панели, а Full Space является отдельной immersive/XR областью. Текущая проектная база уже имеет Quest/noLegal VR-контекст, но XR/DeX/ChromeOS доступность оборудования не подтверждена.
   - **Ответ для v1:** Ring 1 — ChromeOS/Chromebook panel mode и доступное VR/Quest или Android XR panel testing плюс emulator/freeform large-screen resize checks. Локальный аудит кодовой базы подтверждает, что именно для ChromeOS и XR уже существуют явные compatibility hooks, тогда как DeX пока не имеет собственного capability-detector или platform-specific policy. Ring 2 — Samsung DeX на реальном устройстве или удалённом/лабораторном прогоне. Ring 3 — Android XR Full Space как SDK/device-gated best-effort до появления надёжного test path. Нельзя закрывать S0184 как Verified без явной записи, какие кольца реально пройдены, а какие оставлены assumed-OK.
   - **Статус:** Researched

4. **Граница multi-window vs spatial XR**
   - **Вопрос:** считается ли XR-spatial размещение нескольких окон в пространстве частным случаем того же multi-window contract, или это отдельный capability-режим с собственными правилами.
   - **Исследование:** Android XR делит опыт на Home Space и Full Space. В Home Space несколько приложений работают side by side, совместимые mobile/large-screen Android apps запускаются без дополнительной разработки, окно имеет ограниченные bounds. В Full Space одно приложение получает фокус и может использовать spatial panels, environments, 3D content и spatial audio; spatial panel layout имеет собственные правила размещения, размеров и комфорта.
   - **Ответ для v1:** Android XR Home Space — частный случай universal multi-window contract. Android XR Full Space / spatial panels — отдельное расширение поверх общего contract и не входит в v1 S0184, кроме требования не ломать переходы и не смешивать immersive activity с resizable panel semantics. Локальный AS-IS это подтверждает: immersive XR-host уже живёт по отдельному fullscreen контракту и не должен считаться доказательством готовности panel multi-window.
   - **Статус:** Researched

### 6.1 Исследовательская база

- Android multi-window включает split-screen, picture-in-picture и desktop/freeform окна; desktop windowing даёт movable/resizable activity windows.
- `resizeableActivity` по умолчанию `true` для target API 24+, если значение не задано; Android 12 делает multi-window стандартным поведением на large screens; Android 16 на large screens игнорирует ограничения orientation/aspect/resizability.
- Android 10+ multi-resume оставляет несколько activity в `RESUMED`; для Android 9 и ниже видимое окно может быть `STARTED`, поэтому media playback нельзя завязывать только на `ON_PAUSE`.
- Multi-instance — task/root-activity сценарий. Для пользовательского создания нескольких экземпляров launcher activity должна быть resizable и не должна использовать launch mode, запрещающий multiple instances. Android 15 добавляет явный system-UI signal для multi-instance support.
- ChromeOS и Samsung DeX требуют того же ядра: resizable UI, корректная обработка runtime configuration changes, keyboard/mouse/pointer как first-class input.
- Android XR Home Space совпадает с desktop-like panel/window contract; Full Space и spatial panels требуют отдельного XR-дизайна.
- Workspace snapshot 2026-05-19 подтверждает, что отдельный запуск с новой identity окна и window-scoped resume-state уже подготовлен прежде всего для browse/player потоков, но не доведён до согласованной пользовательской экспозиции.
- Workspace snapshot 2026-05-19 подтверждает смешанную state model: часть live-state уже window-local, а filter persistence и часть browse restore-механики пока shared.
- Workspace snapshot 2026-05-19 подтверждает явные capability hooks для ChromeOS и XR/Quest, но не показывает отдельного DeX-specific detection/policy path.
- Workspace snapshot 2026-05-19 показывает doc/UI divergence: feature inventory и настройки уже обещают multi-window, тогда как часть reachable UI по-прежнему принудительно скрыта или заглушена.
- Подробный конспект источников: `temp/S0184_multi_window_research_2026-05-19.md`.

### 6.2 Источники

- Android multi-window support: https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
- Android multi-window and multi-resume: https://developer.android.com/guide/topics/large-screens/multi-window-mode-and-multi-resume
- Android adaptive app quality: https://developer.android.com/docs/quality-guidelines/adaptive-app-quality
- ChromeOS window management: https://developer.android.com/develop/devices/chromeos/learn/window-management
- Samsung DeX optimization: https://developer.samsung.com/samsung-dex/modify-optimizing.html
- Android XR overview: https://developer.android.com/xr
- Android XR foundations: https://developer.android.com/design/ui/xr/guides/foundations
- Android XR spatial UI: https://developer.android.com/design/ui/xr/guides/spatial-ui
- Android XR adaptive apps: https://developer.android.com/develop/adaptive-apps/guides/xr/build-adaptive-apps-for-xr

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Поддержка будет переусложнена из-за привязки к маркетинговому имени вместо capability-профиля | Высокая | Логика станет хрупкой и плохо переносимой между близкими средами | Строить решение вокруг platform/window/input capability и отдельно решать branding |
| Параллельные окна начнут перетирать состояние друг друга | Высокая | Потеря навигационного контекста, неверный restore, неожиданные переходы | Ввести window-scoped session context и явную политику commit в global state |
| Resizable окна вскроют нестабильные layout и action-placement сценарии | Высокая | На части размеров интерфейс станет непредсказуемым или неудобным | Зафиксировать adaptive surface contract и acceptance-matrix по размерам окон |
| Keyboard/touchpad сценарии окажутся второсортными по сравнению с touch-only UX | Средняя | Устройства будут поддерживаться формально, но не продуктово | Выделить desktop input model как отдельный столп и проверять key workflows на клавиатуре/указателе |
| Несколько окон усилят нагрузку на память, фоновые операции и кэши | Средняя | Лаги, перезагрузки поверхностей и деградация батареи | Избегать лишнего дублирования тяжёлых операций и повторного прогрева данных |
| Различия между VR/XR/ChromeOS/DeX окажутся больше, чем общий capability-слой | Средняя | Часть сред получит сломанный UX даже при формально корректной реализации | Иметь per-среду acceptance-checklist и допустимый перечень «assumed-OK» по приоритету |
| Настройка, docs/FEATURES и reachable UI останутся несогласованными по теме multi-window | Высокая | Пользователь и тестировщик увидят взаимоисключающие обещания и не поймут, что реально поддерживается | Перед release alignment свести feature inventory, видимые команды и capability-gate в один явный продуктовый контракт |

---

## 8. Влияние на пользователя (docs/FEATURES)

В публичном feature inventory уже есть отдельные упоминания Chrome OS и VR-specific Multi-Window Mode. После реализации нельзя добавлять дублирующий bullet: нужно обновить существующие capability-related записи в `docs/FEATURES.md` + `_RU` + `_UK`, чтобы они описывали единый desktop-like / panel multi-window contract и не создавали ложного впечатления, что multi-window остаётся только VR-спецфичей.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Поддержка строится вокруг capability-profile, а не вокруг одного бренда**

- **Решение:** проектировать поддержку multi-window как общий desktop-like Android profile, частными случаями которого являются Googlebook/Chromebook, ChromeOS, Samsung DeX, Android XR и VR-shell.
- **Альтернативы:** brand-specific режимы для каждой целевой среды.
- **Почему:** уменьшает хрупкость решения и делает его переносимым между близкими платформами; имя устройства остаётся диагностическим сигналом, а не gate.

**ADR-2: Window identity и active state локальны для каждого окна**

- **Решение:** каждое окно получает собственный session context, а global state обновляется только по контролируемой политике.
- **Альтернативы:** одно общее live-state на все окна; частичная изоляция только для отдельных surface-ов.
- **Почему:** убирает гонки состояния и делает multi-window сценарии предсказуемыми, в том числе на VR, где исторически наблюдался клонированный second window.

**ADR-3: Первая итерация фокусируется на form-factor contract, а не на vendor-specific украшениях**

- **Решение:** сначала обеспечить корректный запуск, навигацию, управление, сохранение состояния и resizable UX на ключевых поверхностях.
- **Альтернативы:** ранний уход в deep integration с vendor-specific возможностями.
- **Почему:** базовый продуктовый контракт важнее специальных улучшений и даёт быструю проверяемую ценность.

**ADR-4: Целевая матрица сред — фиксированная, проверяется по capability**

- **Решение:** считать целевыми средами VR, Android XR, ChromeOS, Chromebook/Googlebook и Samsung DeX. Решение об активации multi-window поведения принимается по реальным platform capability, а не по имени бренда; имя бренда — диагностический сигнал.
- **Альтернативы:** ограничиться одной из сред; решать по списку брендов; принимать любой Android-host как multi-window-capable.
- **Почему:** фиксированный список делает acceptance-матрицу прозрачной, а capability-based gate сохраняет корректность при появлении новых совместимых устройств.

---

## 10. Связи с другими спеками

- **S0028 `vr-multi-window-playback` (Archived):** исторический предшественник отдельных окон в проекте. В текущем workspace snapshot от него остались строки, настройки и скрытые entrypoint-ы, но сам архивный стратегический файл отсутствует, поэтому S0184 должен опираться на текущий AS-IS проекта, а не на недоступный архивный текст.
- **Прямых стратегических тикетов по ChromeOS / DeX / Android XR как multi-window-capable средам в каталоге на момент обновления не найдено.**

---

## 11. Критерии готовности (strategic-level)

1. На каждой целевой среде из ADR-4 у приложения есть поддерживаемый flat/panel path, который остаётся продуктово пригодным в maximized и resizable окнах в рамках своего acceptance-кольца (первый круг / второй круг / best-effort — точная разбивка устанавливается в §6 question 3).
2. Ключевые пользовательские поверхности, вошедшие в v1 scope (§6 question 1), могут работать в нескольких параллельных окнах без конфликта состояния.
3. Клавиатурные и pointer-first сценарии на обязательных поверхностях остаются предсказуемыми и не требуют touch-only обходных путей.
4. Window-specific и immersive-only affordance показываются только там, где платформа и текущая поверхность действительно их поддерживают.
5. Пользовательские строки, help/error UX и docs/FEATURES для режима согласованы между EN/RU/UK и проходят tone checklist из communication policy.
6. В `standard` flavor настройка multi-window существует в настройках приложения. На VR, Android XR Home Space, ChromeOS, Chromebook/Googlebook и Samsung DeX она включена по умолчанию при положительном capability detection; на обычных телефонах/планшетах выключена по умолчанию.
7. При выключенной настройке команды открытия в новом окне скрыты в меню ресурса и меню файла.
8. При включённой настройке меню ресурса содержит «Открыть в новом окне»; команда открывает Browse выбранного ресурса в новом независимом окне без перетирания состояния исходного окна.
9. При включённой настройке меню файла содержит «Открыть в новом окне»; команда открывает viewer/player выбранного файла в новом независимом окне без перетирания состояния исходного окна.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация создана и реализована. Следующий шаг: device validation на ChromeOS/XR/VR/DeX-capable устройстве.

## Revision History

- **2026-05-19** — by `/spec-dev` (`GPT-5`, focus: implementation)
  - Applied: реализован v1-контракт владельца: capability-based default для настройки, команды «Открыть в новом окне» в меню ресурса и файла, реактивация player command panel action, сохранение launch context для нового окна. Статус переведён в BlockNeedUserTest, потому что требуется проверка на реальном desktop/XR multi-window окружении.
  - Proposed (DISCUSS): 0.

- **2026-05-19** — by `/spec-update` (`GPT-5.4`, focus: local AS-IS alignment)
   - Applied: добавлена локальная codebase-дельта к research draft: уточнены реальные browse/player scope, смешанная state model, ChromeOS/XR capability hooks, отсутствие отдельного DeX policy path и doc/UI divergence между настройкой, feature inventory и reachable multi-window entrypoint-ами. Обновлены риск по contract drift, секция `docs/FEATURES` без дублирования, связь с архивным S0028 и strategic done criteria для flat/panel vs immersive semantics.
   - Proposed (DISCUSS): 0.

- **2026-05-19** — by `/spec-update` (`GPT-5`, focus: owner contract clarification)
  - Applied: явно зафиксирован v1 UI-контракт владельца: настройка multi-window в `standard` и остальных flavor-ах, дефолт включён на capability-positive VR/XR/ChromeOS/Chromebook/Googlebook/DeX средах, две команды «Открыть в новом окне» в меню ресурса и меню файла.
  - Proposed (DISCUSS): 0.
- **2026-05-19** — by `/spec-update` (`GPT-5`, focus: research)
  - Applied: добавлен research pass по Android multi-window, ChromeOS, Samsung DeX и Android XR; §6 items переведены в `Researched` с v1-ответами; добавлена исследовательская база и ссылка на `temp/S0184_multi_window_research_2026-05-19.md`.
  - Proposed (DISCUSS): 0.
- **2026-05-18** — by `/spec-update` (manual, focus: scope realignment)
  - Applied: переориентация с brand-specific Googlebook на capability-first multi-window contract для VR / Android XR / ChromeOS / Chromebook (Googlebook) / Samsung DeX. Файл переименован `S0184_googlebook-multiwindow-support.md` → `S0184_multi-window-capability.md` (id S0184 сохраняется). Добавлен иллюстративный three-window сценарий (music / viewer / file ops) как ориентир продуктовой ценности без жёсткого acceptance. Закрыт прежний open question про brand vs capability (ответ зафиксирован в ADR-1 и ADR-4). Закрыт прежний open question про platform matrix (ответ — фиксированный список целевых сред в ADR-4). Добавлен новый open question про границу multi-window vs spatial XR. Статус: BlockQuestions → Draft.
  - Proposed (DISCUSS): 0.
- **2026-05-14** — by `/spec-update` (`GPT-5.4`, focus: consistency, completeness)
  - Applied: перепозиционирование S0184 с ошибочной reader/book трактовки на поддержку Googlebook как нового класса Android-совместимых laptop-like устройств с мультиоконностью. Proposed (DISCUSS): 0.

## Last Audit

**Date:** 2026-05-19
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 16 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 0

### Manual / on-device

- [ ] ChromeOS / Chromebook panel + freeform resize: verify "Open in new window" entrypoints, two parallel Browse + Player windows keep navigation, selection, viewer state independent (§11 #1, #2 — Ring 1).
- [ ] Quest 3 / Android XR Home Space panel: verify capability-positive default ON, parallel windows do not clone state, immersive XR-host is not auto-activated by panel actions (§11 #1, #2, #4 — Ring 1).
- [ ] Samsung DeX desktop mode: verify multi-instance launch, keyboard + pointer reachability of "Open in new window" in both resource and file overflow menus (§11 #3, §3.2 desktop input — Ring 2).
