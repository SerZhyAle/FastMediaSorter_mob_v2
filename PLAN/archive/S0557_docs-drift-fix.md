# Стратегическая спецификация: S0557 - Исправление дрейфа пользовательской документации после S0366

**Ticket:** S0557
**Status:** Archived
**Priority:** 55
**Date:** 2026-06-20
**Tier:** 2 - Standard (docs / UX)
**Origin:** аудит реализации S0366 (user-docs-refresh)

---

## 1. Проблема

Аудит реализации S0366 показал, что рефреш в основном состоялся, но осталось 40 подтверждённых дефектов (адверсариальная верификация отсеяла 4 ложных). Часть из них - фактически неверные утверждения в публикуемых документах, часть - расхождение зеркал EN/RU/UK, часть - битые ссылки на лендингах из-за непоследовательной Jekyll-конфигурации.

Эффект для пользователя: неверная информация о возможностях флейворов, мёртвые ссылки на сайте и в README, устаревшие пути по настройкам, рассинхрон между языками.

---

## 2. Цели

1. Исправить фактические ошибки про возможности флейворов в публикуемых доках.
2. Привести Jekyll-конфигурацию (permalink) и ссылки трёх лендингов к одной рабочей схеме.
3. Выровнять зеркала EN/RU/UK по содержанию, структуре и ссылкам.
4. Обновить устаревшие пути навигации по настройкам под текущую структуру вкладок.
5. Починить битые внутренние якоря и дополнить карту документации.

---

## 3. Область действия

### 3.1 Включено

- фактические правки flavor-возможностей в `docs/FEATURES.md` (преамбула), `docs/HOW_TO*.md` (матрица и строки availability);
- Jekyll permalink и выравнивание расширений ссылок в `index.html`, `index-ru.html`, `index-uk.html`;
- доращивание недостающих секций и Q&A в `docs/HOW_TO_UK.md`, `docs/FAQ_UK.md`;
- сверка REPLACES / Methodology / бейджей в `docs/README*.md`;
- обновление путей навигации по настройкам в `docs/FAQ*.md`, `docs/QUICK_START*.md`, `docs/TROUBLESHOOTING*.md`;
- починка битых якорей `FEATURES.md#20-...`, `#smb-connection`, `#sftp-timeout`;
- дополнение `docs/DOCS_MAP.md` (VR-доки, актуальные даты и What's New).

### 3.2 Не входит

- изменение Kotlin/Java-кода приложения;
- пополнение витрины `docs/FEATURES*.md` новыми возможностями (Favorites, virtual resources, PIN, scheduled ops, delete-by-size) - это зона `/skill-release` из диффа ALL_FEATURES, не точечная правка;
- изменение noLegal-only документов.

### 3.3 Owner inputs (Approval gate)

- **Scope:** пользовательская документация и публичные лендинги; без кода приложения.
- **Validation level:** сверка с `app_v2/build.gradle.kts`, `docs/ALL_FEATURES.jsonl`, `docs/settings/settings-manifest.json`; проверка ссылок и паритета EN/RU/UK.
- **Owner sign-off:** 2026-06-20 - выбран вариант «новый спец + полный фикс».
- **Related tickets:** S0366 (Archived, источник находок).

---

## 4. Источники истины

- `app_v2/build.gradle.kts` - флаги SUPPORT_*/ENABLE_* по флейворам.
- `docs/ALL_FEATURES.jsonl` - канонический инвентарь возможностей.
- `docs/settings/settings-manifest.json` - текущая структура вкладок настроек.
- `_config.yml` + permalink front matter - модель деплоя GitHub Pages.

---

## 5. Предлагаемый подход

### 5.1 Фаза 1. Фактические flavor-ошибки

1. `docs/FEATURES.md` преамбула: убрать «without cloud integrations» у Legacy (legacy имеет cloud).
2. `docs/HOW_TO.md`: строка SMB availability без Lite; строка OCR/Translation = Standard, Legacy, XR/noLegal (без Lite/Photos); ячейка Photos+Cloud в матрице = доступно; убрать «OU planned for a future release».
3. Отразить те же правки в `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

### 5.2 Фаза 2. Jekyll и ссылки лендингов

1. Выбрать одну схему: добавить permalink во все пользовательские доки, на которые ссылаются лендинги (`README*`, `FEATURES*`, `DOCS_MAP`, `WHATS_NEW*`, `DOWNLOADS*`), и привести все три лендинга к `.html`.
2. Проверить, что каждый href резолвится в реальный permalink или существующий `.md`.
3. Убрать остаточные UK-матчеры из JS в `index-ru.html`.

### 5.3 Фаза 3. Навигация по настройкам

1. Заменить «Settings -> Quick Sort» на «Settings -> Operations» (destinations / File deletion and trash) в FAQ/QUICK_START/TROUBLESHOOTING (+RU/UK).
2. Заменить «Settings -> Documents» и «Settings -> General» для текста/PDF/перевода на «Settings -> Media» (+RU/UK).

### 5.4 Фаза 4. Зеркала EN/RU/UK

1. Добавить в `docs/HOW_TO_UK.md` недостающие сценарные секции и починить нумерацию TOC.
2. Добавить в `docs/FAQ_UK.md` Q&A про lens-style translation.
3. Согласовать REPLACES (инлайн vs вынос), Methodology и бейджи между EN/RU/UK README.

### 5.5 Фаза 5. Якоря и карта документации

1. Перенацелить `FEATURES.md#20-wear-os-companion-app` на рабочий таргет во всех README.
2. Убрать мёртвые фрагменты `#smb-connection`/`#sftp-timeout` в HOW_TO_RU/UK.
3. Добавить VR-доки в `docs/DOCS_MAP.md`, обновить даты и описание What's New.

---

## 6. Критерии готовности

1. Ни одно публикуемое утверждение о возможностях флейворов не противоречит `build.gradle.kts` и `ALL_FEATURES.jsonl`.
2. Все ссылки трёх лендингов резолвятся на задеплоенном сайте по единой схеме.
3. Зеркала EN/RU/UK совпадают по наличию секций, Q&A и целям ссылок.
4. Пути навигации по настройкам соответствуют текущим вкладкам манифеста.
5. Внутренние якоря в README и HOW_TO ведут на существующие заголовки.

---

## 7. Риски

- Правка permalink может временно рассинхронить старые внешние ссылки на `.md` - митигировать выбором схемы, совпадающей со ссылками самого приложения (`.html`).
- Доращивание UK-зеркал требует корректного перевода - сверять с EN/RU построчно.
- Случайная правка витрины FEATURES вне зоны `/skill-release` - держать границу из §3.2.

---

## 8. Следующий шаг

Перевести в тактический план (`/spec-tech S0557`) и выполнить по фазам.

---

## 9. Верификация (закрытие 2026-06-20)

Критерии §6 проверены против источников истины (`app_v2/build.gradle.kts`, `docs/ALL_FEATURES.jsonl`, `docs/settings/settings-manifest.json` + `strings_settings.xml`, `_config.yml`):

1. §6.1 - claims о возможностях флейворов сверены: cloud у Photos, OCR/Translation = Standard/Legacy/XR-noLegal, slideshow background music = standard/legacy/vr/noLegal/photos (`ALL_FEATURES.jsonl: slideshow.background-music-from-resource`). Противоречий с `build.gradle.kts` нет.
2. §6.2 - все навигационные доки лендингов отдаются по единой схеме `.html` через front matter + permalink. FEATURES.md/_RU/_UK получили front matter, ссылки лендингов переведены с `.md` на `.html` (была регрессия Фазы 2). `docs/SETTINGS_REFERENCE*` - генерируемые файлы вне §3.1; их permalink-дрейф запаркован как S0561.
3. §6.3 - паритет EN/RU/UK подтверждён по числу заголовков (HOW_TO 41, FAQ 76, QUICK_START 20, TROUBLESHOOTING 33, FEATURES 19 - совпадают). Доведены: перевод английских имён секций в FAQ_RU, недостающая секция «Wear OS» в FAQ_UK, зеркалирование чистки SMB/SFTP-якорей в HOW_TO/HOW_TO_RU.
4. §6.4 - пути по настройкам соответствуют вкладкам манифеста (Operations/Media), внутри трио QS/TS имена секций согласованы.
5. §6.5 - битый якорь Wear перенацелен на существующий `FEATURES.md#16-settings--navigation` (раздел содержит Wear OS Companion); мёртвые фрагменты `#smb-connection`/`#sftp-timeout` убраны во всех трёх HOW_TO; исправлен якорь аудио-раздела FEATURES_UK §9.

Запарковано: S0561 (SETTINGS_REFERENCE landing permalink, генератор).
