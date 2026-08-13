# Стратегическая спецификация: S0365 - Аудит ленивой инициализации и оптимизации ресурсов

**Ticket:** S0365  
**Status:** Archived
**Priority:** 70  
**Date:** 2026-06-05  
**Implemented date:** 2026-06-05
**Verified date:** 2026-06-05
**Tier:** 2 - Medium (strategic audit & infrastructure update)  
**Roadmap entry:** Infrastructure Performance  
**Tactical plan:** `PLAN/S0365_lazy-initialization-audit/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без мелких имён локальных переменных, детальных путей к временным файлам и конкретных лимитов строк.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec
- **Goal / expected outcome:** Provided by user - approve S0365 and prepare a tactical plan for the lazy-initialization audit and follow-up implementation waves.
- **Local anchor:** Provided by user - S0365
- **Scope boundaries / forbidden areas:** Delegated by user - stay within the lazy-initialization audit scope; do not expand into unrelated feature work or read-only zones.
- **Done / success signal:** Delegated by user - strategic spec is approved and a tactical folder with executable phases is created.
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions.
- **UI decisions / delegation:** N/A - no user-facing UI placement decision is requested in this ticket.

`Approved` is blocked while any mandatory line in this section contains `MISSING - requires owner input`.

---

## 1. Проблема

С развитием FastMediaSorter в проект добавляется большое количество опционального функционала (встроенная миниигра, фоновые видеозаставки плеера при проигрывании музыки, сложные виджеты, фоновые задачи). При отсутствии строгого контроля эти фичи начинают влиять на производительность приложения, даже если они полностью отключены в настройках или никогда не открывались пользователем:
1. **Замедление холодного старта (Cold Start):** Инициализация тяжелых менеджеров и синглтонов в DI-графе Hilt во время запуска процесса и первичной сборки зависимостей.
2. **Утечки памяти и избыточный Heap:** Тяжелые View-компоненты (например, `TextureView`, кастомные визуализаторы) парсятся и инфлейтятся в XML-разметке сразу при создании экранов, даже если они скрыты (`android:visibility="gone"`).
3. **Расход системных ресурсов:** Превентивное выделение памяти под медиа-плееры (`MediaPlayer`, `ExoPlayer`), декодеры (`MediaCodec`) или загрузка графических ресурсов (Glide) для опциональных фич.
4. **Отсутствие стандартов для будущей разработки:** В промптах агентов, инструкциях Copilot и архитектурной документации проекта не зафиксировано жесткое требование к «нулевому оверхеду» при выключенном состоянии фичи. При добавлении новых возможностей разработчики и AI-агенты могут легко нарушить эти принципы.

---

## 2. Цели

1. **Полный аудит кодовой базы:** Выявить все места, где опциональный или тяжелый функционал инициализируется преждевременно, расходуя процессорное время и оперативную память.
2. **Спецификация оптимизаций:** Подготовить конкретный список изменений (замена прямого внедрения на `dagger.Lazy`, перевод скрытых View на `ViewStub`, ленивая инициализация ресурсов плеера).
3. **Обновление стандартов разработки (Rule Enforcement):** Дополнить архитектурную документацию и репозиторные правила, чтобы зафиксировать требования к ленивой инициализации.
4. **«Обучение» AI-агентов и Copilot:** Обновить инструкции для AI-агентов и рабочие prompt-шаблоны, чтобы агенты автоматически проектировали и реализовывали новые фичи с соблюдением принципов ленивой загрузки и нулевого оверхеда в отключенном состоянии.

**Non-goals:**
- Уменьшение размера дистрибутива приложения (APK) — размер дистрибутива признан допустимым.
- Внедрение Dynamic Feature Modules (Play Feature Delivery) на данном этапе (оставляем как потенциальное расширение в будущем, если ленивой инициализации кода окажется недостаточно).
- Непосредственное проведение рефакторинга в рамках *этой* стратегической спецификации (задача данной спеки — аудит, согласование и обновление инфраструктуры правил/документов. Сами исправления кода будут выполняться в рамках тактических фаз).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца
1. Приложение не должно тратить ресурсы на фичи, которые выключены в настройках (`AppSettings`).
2. При добавлении любой новой фичи AI-агент должен автоматически проектировать её так, чтобы она не влияла на холодный запуск и память в выключенном состоянии.
3. Документация и правила проекта должны четко объяснять разработчикам, как применять `ViewStub`, `dagger.Lazy` и ленивую загрузку медиа.

### 3.2 Жёсткие ограничения
- **Сохранение функциональности:** Процесс аудита и изменения в правилах не должны ломать текущее поведение игры или плеера.
- **Инфраструктура правил:** Любые изменения в агентных инструкциях, prompt-шаблонах и репозиторных правилах должны строго соответствовать формату Codex-совместимых агентов и не нарушать существующие правила сборки и гигиены разработки.
- **Языковой контракт:** Все изменения в проектной документации и prompt-шаблонах выполняются на английском языке.
- **Flavor:** Изменения должны оставаться безопасными для `standard`, `lite`, `photos`, `legacy`, `vr` и `noLegal`; новые flavour-ветки в `src/main` не добавляются.
- **API level:** Без API-специфичной функциональности; поведение должно оставаться корректным на текущем baseline проекта (`minSdk 26`, `legacy` `minSdk 23`).
- **Wear OS:** Не затрагивается.
- **Производительность:** При выключенной опциональной функции не допускается дополнительная eager-инициализация тяжёлых DI-объектов, медиа-ресурсов или сложных overlay-деревьев.
- **Совместимость данных:** Без изменений схемы Room и без миграций данных.
- **Локализация:** Новые пользовательские строки не планируются; при вынужденном добавлении сохраняется паритет EN/RU/UK.
- **Доступность:** Если optional overlay будет переводиться на ленивую инфляцию, порядок фокуса, D-pad reachability и touch-targets должны сохраниться после inflation.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** N/A - S0365 does not introduce a new user-facing screen or placement decision. Existing Player and Browse surfaces keep their current entry points while optional overlays move to on-demand loading only.
- **Accessibility:** Lazy overlay inflation must preserve focus order, D-pad reachability, touch targets, and system-bar-safe placement in portrait and landscape.
- **Communication policy:** No new user-facing strings are planned. If a later phase adds or rewrites visible copy, it must pass `docs/COMMUNICATION_POLICY.md` Section 6 before merge.
- **Validation level:** `standardDebug` build must pass; affected Player, Standalone Player, and Browse entry flows must open without regressions; lazy overlays and remote-operation paths must still work after first-use inflation or first-use dependency resolution.
- **Owner sign-off:** 2026-06-05 - owner explicitly requested ticket approval plus tactical research for S0365.
- **Related tickets:** S0194 and S0195 are upstream precedents; no blocking external ticket is required before tactical execution.

---

## 4. Контекст текущей архитектуры

В проекте уже используются некоторые элементы отложенной инициализации:
- Правило `S0194` предписывает оборачивать 13 синглтонов уровня Application в `dagger.Lazy<T>` на этапе инициализации приложения.
- Инициализация сетевых хуков перенесена в отложенный загрузчик жизненного цикла сети (правило `S0195`).
- Тяжёлые задачи обслуживания отложены до отрисовки первого кадра.

Тем не менее, в UI-слое и в логике инициализации локальных менеджеров всё ещё могут присутствовать неоптимальные решения. Например, создание медиа-компонентов до реального требования или использование `android:visibility="gone"` для сложных UI-групп вместо `ViewStub`.

---

## 5. Предлагаемый подход

Работы разбиваются на два ключевых направления:
1. **Аналитический аудит существующего кода**
2. **Формализация правил и обучение инструментов**

### 5.1 Направления аудита кодовой базы

- **DI-граф (Hilt):** Анализ `@Inject` зависимостей в точках входа UI-слоя и моделях состояния экранов на предмет преждевременного создания объектов. Переход на `Lazy<T>` или `Provider<T>` там, где это необходимо.
- **UI-слой (XML Layouts):** Поиск тяжелых контейнеров, медиа-рендереров и элементов опциональных фич (миниигра, заставки), которые загружаются сразу. Подготовка рекомендаций по замене их на `ViewStub`.
- **Жизненный цикл медиа-ресурсов:** Анализ мест создания и утилизации `MediaPlayer`, `ExoPlayer`, захвата аппаратных кодеков и загрузки тяжелых битмапов через Glide.
- **ОС-уровень:** Анализ фоновых служб, ресиверов и виджетов на предмет возможности их динамического отключения через `PackageManager` при выключении фичи в настройках.

### 5.2 Формализация правил в документации и промптах

Для предотвращения деградации производительности в будущем, правила ленивой инициализации должны быть жестко закреплены в:
- **Репозиторных правилах и агентных инструкциях:** Добавление обязательного чек-листа оптимизации ресурсов перед коммитом изменений.
- **Архитектурной документации:** Создание нового раздела «Performance and Resource Optimization» с примерами применения `ViewStub`, `dagger.Lazy` и правил освобождения памяти.
- **Инструкциях для AI-агентов:** Интеграция системных директив о том, что любая новая фича или UI-компонент, управляемый через `AppSettings`, обязан реализовывать ленивую загрузку.
- **Рабочих prompt-шаблонах:** Обновление шаблонов проектирования и реализации, чтобы требования к производительности проверялись уже на стадии спецификации и исполнения.

---

## 6. Открытые вопросы / Research items

1. **Критерий «тяжести» View-компонента для перевода на `ViewStub`**
   - *Вопрос:* Какие именно элементы интерфейса мы считаем достаточно тяжелыми для оборачивания в `ViewStub`?
   - *Решение:* Любые контейнеры, содержащие видео-компоненты (`TextureView`, `SurfaceView`), сложные кастомные анимации (процедурные Canvas-анимации), или группы элементов, относящиеся к опциональному функционалу (например, панель управления игрой, оверлеи подсказок).
   - *Статус:* Resolved

2. **Динамические модули (Dynamic Features)**
   - *Вопрос:* Стоит ли планировать вынос графики игры и её кода в отдельный Gradle-модуль в рамках этой инициативы?
   - *Решение:* Нет, в рамках данной спецификации это признано нецелесообразным (Non-goal). Мы фокусируемся на оптимизации работы с памятью и процессором в рамках монолитной сборки через ленивую инициализацию.
   - *Статус:* Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Ложные срабатывания DI при ленивой инициализации | Средняя | NullPointerException или Hilt Injection Error при обращении к `.get()` из фоновых потоков | Тщательное тестирование DI-графа; проверка потокобезопасности фабрик Hilt |
| Задержка UI (UI Lag) при первом инфлейте `ViewStub` | Низкая | Микро-фриз интерфейса в момент активации фичи | Предварительный инфлейт `ViewStub` в фоновом потоке (через `AsyncLayoutInflater`) или в некритичные моменты простоя процессора |
| Разрастание и усложнение промптов агентов | Низкая | Превышение контекстного лимита LLM или игнорирование других правил | Формулировать правила максимально лаконично и структурированно, внедрять только ключевые директивы |

---

## 8. Влияние на документацию и правила проекта

В рамках S0365 будут изменены следующие категории артефактов:
1. **Архитектурная документация** — добавление раздела по ленивой загрузке и управлению ресурсами.
2. **Репозиторные правила** — фиксация проверок для `Lazy`, `ViewStub` и освобождения тяжёлых ресурсов.
3. **Инструкции для AI-агентов** — добавление директив по ленивой инициализации опциональных фич.
4. **Рабочие prompt-шаблоны** — обновление этапов проектирования и реализации с обязательной проверкой нулевого оверхеда.
5. **Профили инженерных агентов** — уточнение ожиданий к производительности и управлению памятью.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Использование `ViewStub` вместо динамического добавления View из кода (programmatic View creation)**
- *Решение:* Использовать декларативный `ViewStub` в XML-разметке.
- *Почему:* Декларативное описание структуры UI в XML упрощает поддержку, локализацию и визуальное редактирование. `ViewStub` позволяет сохранить структуру XML-разметки, не нагружая процессор и память при инициализации экрана.

**ADR-2: Локальная ленивая инициализация в UI-слое через `lazy` делегаты**
- *Решение:* Для объектов, не управляемых Hilt (например, вспомогательные хелперы разметки, локальные слушатели), использовать делегат `lazy(LazyThreadSafetyMode.NONE)` во избежание блокировок и оверхеда на синхронизацию потоков в главном UI-потоке.

---

## 10. Связи с другими спеками

- `S0194` - существующая база по `dagger.Lazy<T>` для application-level singleton wiring.
- `S0195` - отложенная инициализация сетевых lifecycle hooks, на которую S0365 опирается как на precedent.
- Блокирующих внешних spec-зависимостей на момент tactical planning нет.

---

## 11. Критерии готовности (strategic-level)

1. Создан детальный отчёт об аудите текущей кодовой базы FastMediaSorter с перечнем всех найденных мест преждевременной инициализации.
2. Архитектурная документация дополнена разделом об оптимизации производительности и правилах использования ленивой инициализации.
3. В репозиторных правилах зафиксированы требования к проверке использования `Lazy` и `ViewStub` для опциональных фич.
4. Инструкции для AI-агентов дополнены правилами ленивой инициализации и управления тяжёлыми ресурсами.
5. Обновлены рабочие prompt-шаблоны и профили агентов, влияющие на проектирование и реализацию.
6. Успешно выполнена сборка целевого debug-варианта для подтверждения отсутствия синтаксических или конфигурационных ошибок в изменённых файлах.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `PLAN/S0365_lazy-initialization-audit/INDEX.md`

---

## Last Audit

**Date:** 2026-06-06  
**Mode:** static + build + targeted re-audit  
**Flags:** -  
**Outcome:** Verified  
**Counts:** PASS 11 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Follow-up audit found one real gap in the previous closeout: browse local-start paths still eager-injected remote collaborators through `BrowseActivity`, `BrowseViewModel`, and `BrowseManagerInitializer`, so the earlier phase-03 verification overstated the actual lazy boundary.

That gap is now corrected. Browse remote clients (`SMB`, `SFTP`, `FTP`, Google Drive, Dropbox, OneDrive, credentials repo) are carried as `dagger.Lazy<T>` across the activity, view-model, and initializer path, SMB resolves only inside inline-audio SMB downloads, cloud clients resolve only on auth/upload/cloud-scan paths, and `BrowseFileOperationsManager` no longer accepts unused eager network collaborators.

`./build-debug.PS1` passed after the browse lazy-boundary correction, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` was rerun after the final Kotlin edits, `rg -n "S0365" app_v2/src/main/java` still returns no stale ticket-tagged probes, and the tactical package remains aligned to the canonical 5-phase scope. No on-device blocker remains because S0365 is an infrastructure/performance refactor with successful compile validation and no new user-facing flow that requires separate acceptance.

---

## Revision History

- **2026-06-06** - by follow-up audit/fix (Codex, focus: browse lazy-boundary correction)
  - Outcome: `Verified` retained.
  - Corrected: browse local-start eager remote wiring in `BrowseActivity`, `BrowseViewModel`, `BrowseManagerInitializer`, `BrowseInlineAudioManager`, `BrowseResourceLoadManager`, and `BrowseFileOperationsManager`.
  - Validated: `./build-debug.PS1` PASS after the correction.
- **2026-06-05** - by `/spec-check` (Codex, focus: final audit, verification, closeout)
  - Outcome: `Verified`.
  - Confirmed: `.\build-debug.PS1` PASS, `scripts/catalog_sync.ps1 -Module app_v2` PASS, no stale `S0365` debug probes, tactical package reconciled to the canonical 5-phase set.
- **2026-06-05** - by `/spec-dev` (Codex, focus: implementation, audit, validation)
  - Completed tactical phases 01..05.
  - Validated final state with `.\build-debug.PS1` and `scripts/catalog_sync.ps1 -Module app_v2`.
- **2026-06-05** - by `/spec-tech` (Codex, focus: approval, tactical planning)
  - Status: `Draft -> Approved -> Tactical` on explicit owner request.
  - Created: `PLAN/S0365_lazy-initialization-audit/INDEX.md` + tactical phase package.
- **2026-06-05** - by `/spec-update` (Antigravity, focus: structure)
  - Applied: 2. Proposed (DISCUSS): 0.
- **2026-06-05** - by `/spec-update` (Codex, focus: language, structure, verifiability, consistency, completeness, style)
  - Applied: 10. Proposed (DISCUSS): 0.
