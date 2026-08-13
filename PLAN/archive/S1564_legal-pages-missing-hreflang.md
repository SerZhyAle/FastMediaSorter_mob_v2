# Стратегическая спецификация: S1564 - RU/UK версии юридических страниц не попадают в sitemap

**Ticket:** S1564
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-10
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - найдено при работе над S1495 2026-08-10
**Tactical spec:** [`PLAN/S1564_legal-pages-missing-hreflang/INDEX.md`](S1564_legal-pages-missing-hreflang/INDEX.md)

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - execute the full `/spec-all S1564` pipeline.
- **Goal / expected outcome:** Delegated by user - /spec-all auto-approval - publish the registered EN/RU/UK privacy-policy URLs as one complete sitemap hreflang cluster.
- **Local anchor:** Provided by user - ticket S1564, `legal-downloads` in `docs/DOCUMENT_REGISTRY.jsonl`, and `sitemap.xml`.
- **Scope boundaries / forbidden areas:** Delegated by user - /spec-all auto-approval - limit changes to document-registry metadata, its read-only suggestion helper, generated views, and S1564 tracking; do not alter legal text, Android sources, or the unrelated OSS-notice worktree changes.
- **Done / success signal:** Delegated by user - /spec-all auto-approval - registry validation, generation, and drift check exit 0; the three privacy-policy URLs each contain the EN/RU/UK plus x-default alternate set.
- **Autonomy rule:** Provided by user - `/spec-all` authorizes automated decisions that preserve the registry as the source of truth.
- **UI decisions / delegation:** N/A - no application UI changes.

---

<!-- Draft only (/spec-draft): keep this section. /spec omits it entirely. -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-10

**Захвачено во время:** S1495

**Текст:** текста владельца нет - находка при регистрации новых страниц в реестре документов.

**Симптом:** запись реестра `legal-downloads` объявляет три языка, но не задаёт `localized_urls`, поэтому генератор кладёт в `sitemap.xml` только английский URL. RU и UK версии политики приватности и условий использования не индексируются.

**Что установлено 2026-08-10:**

- `scripts/document_registry/generate.ps1` строит hreflang-кластер только из поля `localized_urls`. Без него запись считается одноязычной и получает одну строку `<loc>`.
- Среди опубликованных и индексируемых записей с тремя языками `localized_urls` есть у `site-landing`, `user-guides`, `feature-showcase`, `settings-reference` и у заведённой в S1495 `oss-notices`. Нет только у `legal-downloads`.
- Затронуты `docs/PRIVACY_POLICY.ru.md`, `docs/PRIVACY_POLICY.uk.md`, `docs/TERMS_OF_SERVICE_RU.md`, `docs/TERMS_OF_SERVICE_UK.md`, `docs/V2_TERMS_RU.md`, `docs/V2_TERMS_UK.md` и локализованные `docs/DOWNLOADS_*`.
- Именование локалей в этой группе несогласованно: у политики приватности суффикс `.ru.md`, у условий - `_RU.md`. Значит permalink каждого файла надо прочитать, а не вывести по шаблону.

**Почему это стоит отдельного тикета:** S1495 добавил свою запись с корректным `localized_urls` и на этом остановился - его область были OSS-уведомления. Здесь другая запись, другие документы и несогласованное именование файлов, которое надо разобрать до правки.

---

## 1. Проблема

При генерации `sitemap.xml` запись реестра `legal-downloads` объявляет три языка (`en`, `ru`, `uk`) но не содержит поля `localized_urls`. `scripts/document_registry/generate.ps1` строит hreflang-кластер только из `localized_urls` и для записей без этого поля считает запись одноязычной, поэтому в `sitemap.xml` попадает только английский URL. В результате русская и украинская версии юридических страниц не индексируются поисковыми системами.

---

## 2. Цели

1. Сделать так, чтобы `sitemap.xml` содержал корректные записи для RU и UK версий юридических страниц, включая полный hreflang-кластер и `x-default`.
2. Уменьшить риск появления 404 из `sitemap.xml` — при генерации использовать фактические `permalink` из front matter файлов, а не строить URL по шаблону имени файла.
3. Сделать явную карту `localized_urls` проверяемым источником данных, а вспомогательную утилиту безопасным способом подготовить рекомендации для будущих записей без автоправки реестра.

**Non-goals:**

- Переименование локализованных файлов ради единообразия суффиксов (это отдельная задача).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<не высказаны - находка агента>

### 3.2 Жёсткие ограничения

- `sitemap.xml` и `docs/DOCS_MAP.md` - render targets реестра, руками не правятся.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1495 - завёл запись `oss-notices` с корректным `localized_urls`, чем и выявил отсутствие его у соседней записи.

---

## 4. Контекст текущей архитектуры

Реестр документов `docs/DOCUMENT_REGISTRY.jsonl` является единственным источником для производных артефактов `docs/DOCS_MAP.md` и `sitemap.xml`. Поле `localized_urls` — необязательное: когда оно присутствует, генератор выписывает по записи по одной строке `<url>` для каждого языка и формирует внутри каждой записи полный hreflang-кластер (включая `x-default`). При его отсутствии генератор текущей версии считает запись одноязычной и выпускает единственную `<loc>` со значением `url`.

Локализованные исходники имеют нерегулярные суффиксы: часть файлов использует `.ru.md`, часть — `_RU.md`. Перmalinkы объявляются в YAML front matter каждого файла (если файл использует шаблон), и именно их следует читать как источник правды для публичного URL.

---

## 5. Предлагаемый подход

1. В `legal-downloads` явным образом задать `localized_urls` из фактических `permalink` файлов `PRIVACY_POLICY*.md`: `en`, `ru`, `uk`.
2. Не добавлять эвристику в `scripts/document_registry/generate.ps1`: запись содержит несколько независимых наборов документов, и автоматический выбор одного файла по языку мог бы выпустить неверный URL. Реестр остаётся единственным источником данных генератора.
3. Исправить read-only `scripts/document_registry/suggest_localized_urls.ps1`, чтобы он разбирался PowerShell и распознавал `.ru.md`, `_RU.md`, `.uk.md`, `_UK.md`, `.en.md`, `_EN.md` и обычный EN `.md`. Утилита только предлагает значения для ручной проверки, ничего не записывает.
4. Запустить `validate.ps1`, генерацию и `generate.ps1 -Check`; проверить полный кластер в `sitemap.xml`.

Причины выбора:
- явные значения в реестре однозначно описывают выбранный публичный документ и не смешивают независимые юридические страницы в один языковой кластер.
- чтение `permalink` в helper остаётся безопасной подготовкой данных и не меняет публичные данные без проверки.

---

## 6. Открытые вопросы / Research items

1. **Фактические permalink локализованных юридических страниц**
   - **Вопрос:** какой permalink объявлен в front matter каждого из шести файлов, и совпадает ли он с тем, что ожидает сайт?
   - **Статус:** Resolved - `PRIVACY_POLICY.md` -> `/docs/PRIVACY_POLICY.html`, `.ru.md` -> `/docs/PRIVACY_POLICY.ru.html`, `.uk.md` -> `/docs/PRIVACY_POLICY.uk.html`; они записаны в `legal-downloads.localized_urls`.
2. **Покрытие:** какие записи в `docs/DOCUMENT_REGISTRY.jsonl` с `languages` включают `ru`/`uk`, но не имеют `localized_urls` (список для ручной проверки)?
   - **Статус:** Resolved - на момент проверки отсутствуют опубликованные индексируемые многоязычные записи без `localized_urls`.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Неверный permalink в `localized_urls` уводит в 404 из sitemap | Средняя | Битая ссылка на юридическую страницу в поисковой выдаче | Прочитать front matter каждого файла, а не выводить URL по шаблону |

---

## 8. Влияние на пользователя (docs/FEATURES)

Возможностей не добавляет.

---

## 9. Архитектурные решения (ADR)

ADR: Генератор использует только явный `localized_urls` из реестра. Read-only helper может извлечь кандидаты из front matter для ручного решения, но не заменяет источник правды и не выбирает один URL из группы независимых документов автоматически.

---

## 10. Связи с другими спеками

- S1495 - OSS-уведомления, соседняя запись реестра.

---

## 11. Критерии готовности (strategic-level)

1. `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` возвращает exit code 0 (сгенерированные представления актуальны).
2. `sitemap.xml` содержит по одной `<url>` для каждой локализованной версии юридических страниц (`/docs/...` для `en`, `ru`, `uk`) и внутри каждой записи присутствует полный набор `xhtml:link hreflang` включая `x-default`.
3. Для всех URL, включённых в `sitemap.xml`, ручная проверка HTTP GET возвращает 200 (или корректный редирект) — отсутствие явных 404 для юридических страниц.
4. Код генератора прошёл базовую проверку (скрипт выполняется без ошибок) и покрывает нерегулярные суффиксы локалей (например `.ru.md` и `_RU.md`).
5. Документация реализации и список изменённых файлов добавлены в задачу S1564 (tactical spec) и в `dev/CHANGELOG.md` через `scripts/add_to_dev_log.ps1`.

## Last Audit

**Date:** 2026-08-11
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 17 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- All closed automatically: local registry validation, generation, zero-drift check, XML cluster assertion, and HTTP GET checks for EN/RU/UK returned PASS.
