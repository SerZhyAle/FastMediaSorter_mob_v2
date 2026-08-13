# Спецификация (compact bugfix): S1372 - post-change решает применимость гейта по одному файлу вместо всего набора

**Ticket:** S1372
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-03
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-03

**Текст:**

Автозахват при закрытии S1363. Вызов был такой:

```
scripts/post-change.ps1 -Files "<12 .kt>,scripts/quality/lib/source-matchers.ps1,scripts/quality/assert-swallowed-cancellation.ps1,scripts/quality/assert-swallowed-cancellation.tests/Run-Tests.ps1,scripts/quality/swallowed-cancellation-baseline.txt,PLAN/S1363_*.md,PLAN/S1371_*.md" -ScopeToFile -ChangeType Mixed
```

В наборе есть новый скрипт `scripts/quality/assert-swallowed-cancellation.ps1`, тем не менее закрытие напечатало:

```
[script-cheatsheet-sync-gate] SKIP (0 ms) - not applicable - touched file is not a repo script or the script cheatsheet
```

и завершилось вердиктом `PASS WITH ADVISORIES (1)`.

Запущенный сразу после этого вручную тот же гейт покраснел:

```
Write-Error: docs/SCRIPT_CHEATSHEET.md is stale - run: pwsh -NoProfile -File scripts/utils/help.ps1 -Generate
CHEATSHEET=1
```

---

## 1. Проблема / симптом

Формулировка «touched file is not a repo script» стоит в единственном числе, хотя передан был `-Files` из 18 путей. Применимость каждого гейта считается по одному файлу, а не по всему набору.

Последствие серьёзнее пропущенной шпаргалки: `post-change` - это механизм закрытия, и его вердикт `PASS` читается как «все применимые гейты отработали». Если применимость считается по одному файлу, то в любом мультифайловом закрытии часть гейтов молча не запускается, а вердикт всё равно выглядит чистым. Отказ не громкий, а тихий, и заметен только если запустить гейт руками.

**Второе воспроизведение, 2026-08-03.** Закрытие S1370 вызвано с набором из пяти файлов, последним - `docs/ALL_FEATURES.jsonl`. Печать:

```
[all-features-gate] SKIP (0 ms) - not applicable - touched file is not an ALL_FEATURES artifact
```

При этом `[document-registry]` в том же прогоне инвентарь увидел и потребовал подтверждения. То есть два гейта в одном запуске разошлись во мнении о том, что было изменено.

---

## 2. Корневая причина

`scripts/post-change.ps1` строит полный набор в `$changedFiles`, но затем выводит `$normFile` из одного `$File` - первого элемента набора - и считает по нему все применимости с path-триггером.

Гейты, считающие по одному файлу:

- `string-format-gate`
- `all-features-gate`
- `settings-doc-sync-gate`
- `howto-settings-paths-gate`
- `icon-inventory-sync-gate`
- `dialog-cancel-style-gate`
- `orientation-implied-feature-gate`
- `script-cheatsheet-sync-gate`

Той же ошибкой затронуты два производных значения: набор ресурсов (`$resourceSourceSet`) и набор для аудита ресурсов (`$auditSourceSet`) - оба выводятся из `$File`, поэтому в смешанном закрытии flavor-ресурса, названного не первым, гейт судит не тот source set.

Правильная форма в том же файле уже есть в двух местах и служит образцом: `document-registry` (строка 645) нормализует и перебирает весь `$changedFiles`, а `detekt-baseline-absorption` (строка 264) сопоставляет регулярку со всем набором. Комментарий у второго прямо называет S1372 как тикет общего исправления и запрещает копировать однофайловую форму.

Гейты, считающие по `$resolvedChangeType`, дефектом не затронуты - тип задаётся вызывающим на весь набор.

---

## 3. Исправление

Ввести в `post-change.ps1` предикат, отвечающий на вопрос «есть ли в наборе хотя бы один файл, подходящий под шаблон», и перевести на него все восемь применимостей с path-триггером. Производные значения набора ресурсов выбирать по первому подходящему файлу набора, а не по `$File`.

`$File` остаётся первичным путём только там, где нужен ровно один путь: строка dev-лога и заголовок прогона.

### 3.3 Owner inputs (Approval gate)

- **Validation level:** запуск `post-change.ps1` с `-Files`, где триггерный файл стоит не первым, и сравнение печати гейта с его же ручным запуском.
- **Related tickets:** S1363 (где это всплыло), S1338 (там вводился `-ScopeToFile` и мультифайловое закрытие), S1370 (второе воспроизведение).

---

## 4. Проверка

1. Закрытие с `-Files`, где `.ps1` стоит не первым, запускает `script-cheatsheet-sync-gate`, а не пропускает его.
2. Закрытие с `-Files`, где `docs/ALL_FEATURES.jsonl` стоит не первым, запускает `all-features-gate`.
3. Закрытие одним `-File`, не подходящим ни под один path-триггер, по-прежнему печатает те же SKIP - поведение однофайловых вызовов не меняется.
4. Формулировка SKIP говорит про набор, а не про один файл, чтобы печать больше не вводила в заблуждение.

---

## Last Audit

**Date:** 2026-08-03
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Однофайловый вызов (`-File`, путь без path-триггера) печатает тот же набор SKIP, что и до правки. Проверено по построению: набор из одного элемента - вырожденный случай того же предиката; отдельного прогона после правки не делалось, чтобы не плодить третью строку dev-лога на один тикет.
