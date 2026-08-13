# Спецификация: S0785 - Флаг страны как fallback-иконка трансляции в списке

**Ticket:** S0785
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:**

Трансляции. список. Если у трансляции нет картинки из атласа иконок - показывать на её месте флажок страны трансляции (восстановит структуру списка).

---

## 1. Проблема

В списке трансляций ведущий 24dp-слот занимает логотип канала, нарезанный из sprite-атласа favicon (S0668). Если у канала нет тайла, слот скрывается (`GONE`), строка теряет ведущий элемент и «съезжает» - выравнивание списка ломается. У каталожных строк при этом есть страна (ISO alpha-2), которую можно показать флагом.

## 2. Цели

1. Когда у трансляции нет favicon-тайла, но известна страна - показать флаг страны в том же ведущем слоте, сохранив выравнивание строки.
2. Строки без тайла и без страны (ручные/импортированные) оставляют слот пустым - поведение S0668 сохраняется.

**Non-goals:**

- Загрузка внешних флаг-ассетов: переиспользуется существующий рендер флагов (`LanguageFlagFormatter`: custom-image RU/BY + regional-indicator emoji).
- Изменение сеточного режима трансляций (это S0784).

## 3. Ограничения

- **Flavor:** streams-функционал (standard); правки в `src/main`, инертны без streams-UI.
- **Локализация:** EN/RU/UK - добавлена строка `streams_country_flag`.
- **Доступность:** flag-слот несёт `contentDescription`; страна дублируется чипом страны в metadata-строке.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0668 (favicon-слот), S0761 (country-facet), S0784 (сеточные миниатюры - смежный слой streams).

## 4. Критерии готовности

1. Нет favicon + есть country -> в ведущем слоте флаг страны, `ivFavicon` GONE.
2. Нет favicon + нет country -> оба слота GONE (структура как раньше).
3. Есть favicon-тайл -> тайл VISIBLE, флаг GONE.
4. Проект компилируется; юнит-тесты адаптера зелёные.

## Реализация (2026-07-01, Simple-путь)

- `LanguageFlagFormatter.applyCountryFlagGlyph(view, countryCode)`: рендерит только флаг (без кода) - custom-image для RU/BY через тот же `ImageSpan`, иначе regional-indicator emoji; возвращает `false`, если флага нет (единый источник рендера флагов).
- `res/layout/item_stream_source.xml`: добавлен `tvFaviconFlag` (24dp) рядом с `ivFavicon`, тот же footprint (24dp + marginEnd 8dp), `visibility=gone`. Land-варианта нет.
- `StreamSourceAdapter`: `bindFavicon(source)` вместо `bindFavicon(url)`; при отсутствии тайла (null index или decode -> null) вызывает `showCountryFlagFallback(source.country)`. Взаимоисключение: показан ровно один из `ivFavicon`/`tvFaviconFlag`. Async-путь сохраняет guard `boundUrl == url` (recycle-safe).
- Строка `streams_country_flag` (EN "Country flag" / RU "Флаг страны" / UK "Прапор країни").
- Тесты `StreamSourceAdapterFaviconTest`: +3 кейса (fallback показан / скрыт без country / скрыт при наличии тайла) - зелёные.
- Компиляция `compileStandardDebugKotlin` - BUILD SUCCESSFUL; `testStandardDebugUnitTest --tests StreamSourceAdapterFaviconTest` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** открыть список трансляций; у каталожного канала без логотипа в ведущем слоте виден флаг страны, строки выровнены; у канала с логотипом - логотип; у ручного канала без страны - пустой слот.
