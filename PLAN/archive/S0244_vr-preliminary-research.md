---
ticket: S0244
status: Verified
priority: 95
date: 2026-05-18
tier: 3
---

# S0244 - Preliminary research перед стартом VR-rewrite (S0240 §10.0)

**Ticket:** S0244
**Status:** Verified (artifacts populated, §6 criteria met 2026-05-18 03:21)
**Priority:** 95
**Date:** 2026-05-18
**Tier:** 3 - research/setup task (никакого app-кода не пишет; собирает факты, варианты и рекомендации)
**Roadmap entry:** `S0240 §10.0` - preliminary research перед Этапом 0 (`S0245`).
**Blocks:** `S0245` (Этап 0). Минимально требуются результаты `R-02` (структура Settings), `R-05` (manifest/flavor history), `R-06` + `R-07` (XR manifest decls).
**Запуск:** sub-agents по группам A/B/C параллельно (см. §5). Группа D отменена - `S0240 §11` уже закрыт.

---

## 1. Цель

Подготовить инвентаризацию фактов, опций и best practice по всем нерешённым техническим и продуктовым вопросам **до старта Этапа 0** в `S0240`. Получить за один заход:

- факты о существующем коде проекта (что было до удаления `S0241`, как устроен Settings, command panel, ExoPlayer state);
- факты о требованиях Meta (Quest 3 / Horizon Store) и Google (Android XR / Google Play);
- конкурентный анализ существующих VR-видеоплееров - что работает, что нет, что мы делаем лучше;
- черновик «варианты + рекомендация best practice» для 4 owner-вопросов из `S0240 §11`, чтобы владелец принимал решения не в вакууме.

Этот тикет не запускает разработку. Он насыщает следующий этап (Этап 0 в `S0240`) точными вводными.

---

## 2. Что НЕ делает этот тикет

- Не пишет ни одной строки `.kt` / `.cpp` приложения.
- Не правит `build.gradle.kts`, manifest-ы, `strings.xml`.
- Не аллоцирует id под Этап 0 / Этап 1 - это работа `/spec-tech S0240`.
- Не принимает решения за владельца по `S0240 §11` - только готовит черновик «варианты + рекомендация».
- Не публикует ничего в магазины.

---

## 3. Контракт результатов

Все артефакты складываются в подпапку **`PLAN/S0240_vr-stack-rewrite-epic/`**. Формат каждого артефакта - markdown, см. шаблоны в этой же подпапке (созданы вместе с этим тикетом).

| Файл | Что содержит | Источник записей |
|------|--------------|-------------------|
| `RESEARCH.md` | Результаты R-01..R-13 (см. ниже). По каждому: вопрос → источник → варианты → best practice → открытые риски. | Группы A + B из `S0240 §10.0`. |
| `COMPETITOR_ANALYSIS.md` | Таблица наблюдений по DeoVR / Bigscreen / Skybox / Pigasus / Quest TV / Android XR sample apps по чек-листу `S0240 §6.14`. Отдельная графа «что мы делаем лучше». | R-14. |
| `OWNER_QUESTIONS_DRAFT.md` | Для каждого из 4 вопросов `S0240 §11`: 2..3 варианта с pros/cons + рекомендация best practice + почему. | R-15 (зависит от R-01..R-14). |
| `STORE_TODO.md` | Накопительный список manifest-объявлений / permissions / submission-требований, всплывающих в R-06/R-07/R-10/R-11. Превращается в живой чек-лист для будущих submission-тикетов. | R-06, R-07, R-10, R-11 (и далее по ходу эпика). |

---

## 4. Research-задачи

Скопировано из `S0240 §10.0`, чтобы тикет был самодостаточным. Формат отдачи для каждой задачи - «Вопрос → Источник → Варианты (2..3) → Best practice → Открытые риски».

### Группа A - внутри кодовой базы (артефакты до удаления `S0241`)

- **R-01.** Что было в `src/vr/` до S0241: настройки VR, layout, строки, preference-keys. Источник: `git log --diff-filter=D` на момент завершения `S0241`, `dev/archive/`. Назначение: переиспользуемые строки и UI-паттерны для Этапа 0.
- **R-02.** Текущая структура Settings-экрана в `src/main/`. Источник: `dev/CATALOG/app_v2.md` (поиск `Settings` / `Preference`) → чтение конкретных файлов. Назначение: точка расширения для нового VR-блока.
- **R-03.** Command panel плоского плеера. Источник: `dev/CATALOG/app_v2.md` (`CommandPanel`). Назначение: куда вешать кнопку «Immerse» (Этап 1).
- **R-04.** ExoPlayer state transfer между активностями / фрагментами. Источник: `dev/CATALOG/app_v2.md` (`ExoPlayer` / `PlayerActivity`). Назначение: подход для «плоский → VR-host → плоский».
- **R-05.** Что осталось от старого `src/vr/AndroidManifest.xml`, нативного CMake-таргета и `productFlavors`-блока `app_v2/build.gradle.kts` в git history до `S0241`. Назначение: исходник для нового manifest-фрагмента и flavor-конфигурации.

### Группа B - внешние источники (web)

- **R-06.** Android XR manifest declarations и intent-категории. Источник: developer.android.com (Android XR docs), референс-проекты Google.
- **R-07.** Meta Quest manifest declarations, оптимальные настройки для Quest 3, manifest-категории. Источник: developer.oculus.com / developer.meta.com.
- **R-08.** Android XR emulator: какая системная сборка в Android Studio AVD, как поднять, limitations. Источник: Android Studio + web.
- **R-09.** OpenXR loader licensing и distribution, Khronos OpenXR SDK для Android, Meta-specific extensions. Источник: khronos.org, developer.meta.com. Назначение: подтверждение / отмена разделения `vr` / `vrUnlicensed`.
- **R-10.** Google Play XR distribution model: эффекты `required=true/false` для XR feature. Источник: developer.android.com, Play Console docs.
- **R-11.** Meta Store submission process: путь публикации, tooling, требования к подписанию. Источник: developer.meta.com.
- **R-12.** Privacy policy hosting options: GitHub Pages vs внешний хостинг vs `docs/`. Источник: web + рекомендации Google/Meta.
- **R-13.** Store assets pipeline для VR: скриншоты / видео из иммерса. Источник: web research.

### Группа C - конкурентный анализ

- **R-14.** Анализ DeoVR / Bigscreen / Skybox / Pigasus / Quest TV / Android XR sample apps по чек-листу `S0240 §6.14`. Источник: установка приложений на Quest 3 (где доступны) + web (обзоры, гайды), документация Android XR samples. Результат: `COMPETITOR_ANALYSIS.md` + графа «что мы делаем лучше».

### Группа D - черновик ответов на §11 - ОТМЕНЕНО

- **R-15. ОТМЕНЕНО (2026-05-18 02:46).** Владелец ответил на все 4 вопроса `S0240 §11` напрямую в документе. `OWNER_QUESTIONS_DRAFT.md` переоформлен как зафиксированные решения. R-15 больше не нужен.

---

## 5. Параллельная схема выполнения

Группы A, B, C независимы друг от друга - можно запускать одновременно через sub-agents:

- **Sub-agent 1 (Group A, codebase research):** `general-purpose` или `android-solution-researcher` - копает в `dev/CATALOG/`, `git log`, `dev/archive/`.
- **Sub-agent 2 (Group B, web research):** `general-purpose` - web search по developer.android.com / developer.meta.com / khronos.org.
- **Sub-agent 3 (Group C, competitor analysis):** `general-purpose` - web research по конкурентам + чтение их store listings.

Group D (R-15) выполняется **после** того, как Group A/B/C завершены (зависит от их результатов).

Каждый sub-agent получает чёткий брифинг (см. CLAUDE.md «Parallel Sub-Agents»): что искать, в каком формате отдавать, какой файл наполнять.

---

## 6. Критерий завершения (`Verified`)

- В `PLAN/S0240_vr-stack-rewrite-epic/` присутствуют все 4 артефакта: `RESEARCH.md`, `COMPETITOR_ANALYSIS.md`, `OWNER_QUESTIONS_DRAFT.md`, `STORE_TODO.md`.
- `RESEARCH.md` содержит записи по всем R-01..R-13 в формате «Вопрос → Источник → Варианты → Best practice → Открытые риски».
- `COMPETITOR_ANALYSIS.md` содержит наблюдения по минимум 4 из 6 заявленных конкурентов (если 2 недоступны - фиксируется как «недоступен для анализа: причина»).
- `OWNER_QUESTIONS_DRAFT.md` содержит черновик «варианты + best practice» по всем 4 вопросам `S0240 §11`.
- `STORE_TODO.md` инициализирован - содержит хотя бы скелет разделов «Google Play» и «Meta Store» с первыми накопленными пунктами.
- Запись в `dev/CHANGELOG.md` + functionality log (если применимо - research не user-visible, скорее всего пропускаем).

После `Verified` владелец читает `OWNER_QUESTIONS_DRAFT.md`, отвечает на `S0240 §11`, и запускает `/spec-tech S0240` для нарезки Этапа 0.

---

## 7. Открытые вопросы внутри S0244

- **Доступность Quest 3 приложений для анализа.** Часть конкурентов (DeoVR, Skybox) доступна как платные приложения. Покупаем для анализа или используем только обзоры? - для текущего тикета достаточно web-обзоров и видео-демонстраций; покупка опциональна.
- **Объём web research для R-06..R-13.** На каждой задаче возможно глубокое погружение. Ограничиваем 3..5 ключевых источников на задачу.

---

## Last Audit

2026-05-18 03:21 - **Verified (via `/spec-all S0244`).** Все 4 артефакта в `PLAN/S0240_vr-stack-rewrite-epic/` заполнены через 3 параллельных research sub-agents (Group A - codebase R-01..R-05; Group B - web R-06..R-13; Group C - competitors R-14):

- `RESEARCH.md` - 63 KB. R-01..R-13 покрыты полностью, формат «Вопрос → Источник → Варианты → Best practice → Открытые риски» соблюдён. Каждая запись содержит ссылки на конкретные файлы кодовой базы (Group A: с commit-hashes `abc5c291`, `e7c20d95`, `c1456a85`) или реально открытые web-URL (Groups B/C). Сводная таблица best practice + сводный list открытых рисков для эпика S0240.
- `COMPETITOR_ANALYSIS.md` - 35 KB. 5 конкурентов проанализированы полностью (10/10 чек-лист): DeoVR, Skybox, Pigasus, Quest TV, Android XR samples. Bigscreen Beta - частично (поверхностно по public docs; deeper controller mapping недоступен без device-test). Сводка «что мы делаем лучше» (10 пунктов) + consolidated anti-patterns list (10 пунктов) - оба привязаны к product vision из `S0240 §1.0`.
- `OWNER_QUESTIONS_DRAFT.md` - все 4 owner-вопроса (Q1..Q4) закрыты в `S0240 §11` 2026-05-18; документ переоформлен как зафиксированные решения. R-15 (черновик ответов) отменён, что отражено в файле.
- `STORE_TODO.md` - стартовый скелет насыщен резолюшнами R-06/R-07/R-09/R-10/R-11/R-12/R-13: явные значения manifest-фрагментов для Google Play (XR) и Meta Store (Quest), AppAuth для Google OAuth на обоих target'ах, MQDH Cast 2.0 capture pipeline, single-APK distribution для Play, рекомендация переоформить `vrUnlicensed` flavor как buildType debug (Apache 2.0 Khronos loader - нет лицензионных оснований для split).

Ключевые cross-cutting находки (для эпика):

1. **`vr` / `vrUnlicensed` flavor split не имеет лицензионной основы** (R-09). Khronos OpenXR loader - Apache 2.0; Meta использует тот же loader. Рекомендация: единый `vr` source set; side-load - buildType debug, не отдельный flavor.
2. **Quest требует `minSdk = 29`** (R-07) против phone `minSdk = 26` - расхождение flavor'ов; через `minSdk = 29` в `vr`-flavor `defaultConfig`.
3. **Google OAuth на Quest** требует AppAuth web-flow вместо GoogleSignIn SDK (R-12) - нет GMS на Horizon OS. Унификация phone+Quest на AppAuth - clean refactor; отдельная задача.
4. **Google Play XR - single-APK single-track** рекомендован Google (R-10); все XR `<uses-feature required="false">` для phone visibility.
5. **Meta SLA публично не опубликован**, буфер 2..6 недель перед релизом (R-11).
6. **OpenXR в эмуляторе Android XR не работает** (R-08) - VR-стек тестируется только на Quest 3 до выхода Samsung Galaxy XR.

§6 критерии Verified выполнены:
- 4 артефакта присутствуют ✅
- R-01..R-13 покрыты в `RESEARCH.md` ✅
- COMPETITOR_ANALYSIS.md покрывает ≥4 из 6 конкурентов ✅ (5 полных + 1 частичный)
- OWNER_QUESTIONS_DRAFT.md закрывает все 4 §11 вопроса ✅
- STORE_TODO.md инициализирован + насыщен ✅
- Functionality log - пропущен (research не user-visible per §6).

После Verified: владелец читает `OWNER_QUESTIONS_DRAFT.md` (уже зафиксированные решения), `RESEARCH.md` (особенно «Сводный list открытых рисков») и запускает `/spec-tech S0240` для нарезки Этапа 0 на основе R-01..R-05 (codebase entry points).

2026-05-18 02:46 - `R-15` отменён: владелец ответил на `S0240 §11` напрямую в документе, черновик не требуется. Остаются R-01..R-14. `R-12` получил дополнительную под-задачу: уточнить разницу Google OAuth между Quest (нет SSO, нужен web-flow) и Android XR (есть SSO).

2026-05-18 02:37 - создан как preliminary research-тикет для `S0240`. Цель - параллельно с ожиданием ответов на `S0240 §11` собрать все необходимые факты, конкурентные паттерны и черновики опций для информированного решения. Не пишет app-кода; только готовит вводные для Этапа 0.
