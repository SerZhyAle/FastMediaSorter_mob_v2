# Спецификация (compact bugfix): S1956 - apply-doc-icons пишет img на несуществующий PNG и выходит с нулём

**Ticket:** S1956
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-22
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-22

**Захвачено во время:** S1931

**Текст:**

apply-doc-icons.ps1 emits an img tag for a drawable whose PNG was never generated, and exits 0

Found during S1931 (2026-08-22).

Symptom. `scripts/docs/apply-doc-icons.ps1` rewrote all three `docs/howto/index*.md` to `<img src="../icons/doc/ic_launcher_mode.png" ...>` while `docs/icons/doc/ic_launcher_mode.png` does not exist on disk. The script printed "1/14 emoji kinds replaced" for each file and exited 0. The published guide index would show three broken images and nothing would report it.

Evidence. The landing half of the same script DOES guard this - `Get-InlineSvg` throws "missing generated svg: <path> (run export-doc-icon-pngs.ps1)" when the SVG is absent (apply-doc-icons.ps1:33). The markdown half, `Convert-MarkdownEmoji` (apply-doc-icons.ps1:66-79), builds the `<img>` string from the drawable name with no Test-Path on the PNG at all. The asymmetry is the defect: one half refuses on a missing asset, the other half ships a dangling reference.

Root cause of the missing PNG in this instance. `scripts/docs/export-doc-icon-pngs.ps1` produces the SVG half itself but delegates rasterization to `scripts/docs/lib/rasterize_svgs.py`, which imports cairosvg -> cairocffi -> dlopen of native libcairo. That native library is absent on this machine (`OSError: no library called "cairo-2" was found`), so the PNG half fails while the SVG half succeeds, and the script exits 1 having written a partial asset set.

Two candidate fixes, not decided here.
1. `Convert-MarkdownEmoji` refuses on a missing PNG, mirroring `Get-InlineSvg`. Cheap, closes the silent-broken-image path.
2. `export-doc-icon-pngs.ps1` treats a partial asset set as fatal and does not leave the tree half-exported.

Also worth deciding separately: how the cairo native dependency is provisioned, since nothing in docs/ or dev/ records it and the 25 existing PNGs were generated on 2026-07-03 by an environment that no longer exists here.

---

## 1. Проблема / симптом

`scripts/docs/apply-doc-icons.ps1` пишет в markdown-поверхности тег `<img src="../icons/doc/<drawable>.png">` для drawable, у которого PNG на диске нет, печатает обычную строку `markdown: <file> - N/M emoji kinds replaced` и выходит с кодом 0. Опубликованный гайд показал бы битые картинки, и ни один прогон об этом не сообщил бы.

Эвиденс на текущем дереве:

- `docs/icons/doc/` содержит 26 `.svg` и только 25 `.png`; отсутствует `ic_launcher_mode.png` (SVG для него есть).
- `docs/icons/doc-icon-map.json` называет `ic_launcher_mode` в секции `landing` - та половина скрипта инлайнит SVG и на отсутствующий ассет ругается, поэтому сегодня битой ссылки в дереве нет. Перенос того же drawable в `howto`/`docsMap` (обычная правка карты) немедленно даёт молчаливую битую ссылку - защиты в markdown-половине нет вовсе.
- Обнаружено на S1931 (2026-08-22), когда карта временно называла `ic_launcher_mode` в `howto` и все три `docs/howto/index*.md` получили ссылку на несуществующий PNG при выходе 0.

Асимметрия внутри одного скрипта - и есть дефект: одна половина отказывается работать без ассета, вторая публикует висячую ссылку.

## 2. Корневая причина

- `Get-InlineSvg` (`apply-doc-icons.ps1:32-34`) делает `Test-Path` на `<drawable>.svg` и бросает `missing generated svg: <path> (run export-doc-icon-pngs.ps1)`. Список `$landingIcons` строится до первой записи в файл, поэтому отказ происходит раньше любой мутации.
- `Convert-MarkdownEmoji` (`apply-doc-icons.ps1:66-79`) собирает строку `<img>` из имени drawable и подставляет её в текст без единой проверки существования PNG. Проверки нет ни на входе, ни после записи.
- Отсутствие самого PNG в этом окружении - вторая, независимая причина: `scripts/docs/export-doc-icon-pngs.ps1` пишет SVG в цикле, а растеризацию делегирует `scripts/docs/lib/rasterize_svgs.py` (cairosvg -> cairocffi -> dlopen libcairo). Нативная libcairo на этой машине отсутствует (`OSError: no library called "cairo-2" was found`), растеризатор падает, экспортёр выходит 1 - но SVG-половина уже записана на диск, и дерево остаётся частично экспортированным. Ни `export`, ни `apply` не проверяют комплектность набора после прогона.

## 3. Исправление

1. `apply-doc-icons.ps1` - симметрия с `Get-InlineSvg`. Резолвить `<img>`-теги для всех markdown-поверхностей ДО первой записи: новая `Get-PngImgTag` делает `Test-Path` на `<drawable>.png` и бросает `missing generated png: <path> (run export-doc-icon-pngs.ps1)`. `Convert-MarkdownEmoji` получает уже готовые пары emoji->тег и ничего не собирает сама. Так частичная перезапись невозможна: скрипт либо отказывается целиком, либо переписывает всё.
2. `export-doc-icon-pngs.ps1` - частичный набор фатален. После растеризации сверить фактическое содержимое `docs/icons/doc/` с множеством `$expected`: любой недостающий файл печатается поимённо и даёт выход 1. Растеризатор, упавший на нативной зависимости, тоже уже даёт 1 - новая проверка ловит и «тихую» неполноту (растеризатор вышел 0, но файл не появился).

Провижининг самой нативной libcairo вынесен в отдельный тикет - это вопрос окружения, а не этих двух скриптов.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1931 - тикет, на котором дефект обнаружен; S0889 - завёл конвейер doc-иконок.
- Оба кандидата из §0 приняты: они закрывают разные половины пути (публикация висячей ссылки и оставленный частичный набор) и не конфликтуют.

## 4. Проверка

- Repro-фикстура живёт в `PLAN/S1956_bugfix-doc-icon-img-without-png/repro/`: карта, где drawable без PNG назван в `howto`, плюс пустышки `index*.html` и `docs/howto/index.md`. Команда: `pwsh -NoProfile -File scripts/docs/apply-doc-icons.ps1 -RepoRoot <repo>\PLAN\S1956_bugfix-doc-icon-img-without-pngepro`. Ожидаемо до фикса: markdown переписан, exit 0. Ожидаемо после фикса: `missing generated png: ..`, exit 1, SHA256 `docs/howto/index.md` остаётся `23ce2ff299fd41f497d6af124c1551833e23b74de4effb12a914c380d6af4472`.
- Реальное дерево: `apply-doc-icons.ps1` без аргументов выходит 0 и не меняет ни одного файла (сверка SHA256 всех целевых файлов до и после) - карта сегодня PNG-only drawable в markdown не называет.
- `export-doc-icon-pngs.ps1` на этой машине выходит 1 (нативная libcairo отсутствует); новая проверка комплектности не должна менять этот код выхода.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - оба скрипта продолжают декларировать свои коды выхода.

---

## Last Audit

**Date:** 2026-08-22
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Проверено:

- `Get-PngImgTag` делает `Test-Path` и бросает `missing generated png: <path> (run export-doc-icon-pngs.ps1)` (`apply-doc-icons.ps1:48`).
- Все markdown-теги резолвятся до первой записи (`apply-doc-icons.ps1:56-60`); цикл записи потребляет готовые пары (`:99`).
- `Convert-MarkdownEmoji` больше не собирает `<img>` сама - 0 вхождений `$p.drawable` в файле.
- `export-doc-icon-pngs.ps1:104` печатает поимённо недостающие ассеты и выходит 1.
- Repro-фикстура (`PLAN/S1956_bugfix-doc-icon-img-without-png/repro/`), отрицательный случай: exit 1, SHA256 `docs/howto/index.md` не изменился (`23ce2ff2..` до и после).
- Положительный случай (тот же прогон после подкладывания PNG под имя из карты): exit 0, emoji заменён на `<img>`.
- Реальное дерево: `apply-doc-icons.ps1` exit 0, `sha256sum -c` по всем 7 целевым файлам - OK.
- `export-doc-icon-pngs.ps1` на этой машине по-прежнему exit 1 (нативная libcairo отсутствует), 25 PNG и 26 SVG не изменились.
- Логика проверки комплектности прогнана изолированно: недостающий файл найден, полный набор молчит.
- `assert-exit-contract.ps1` PASS; `post-change.ps1 -ScopeToFile` PASS (Script, 2736 ms).

Отпочковано: **S1964** - провижининг нативной libcairo для растеризатора doc-иконок (вопрос окружения, вне контракта этого тикета).
