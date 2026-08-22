# Спецификация (compact bugfix): S1964 - растеризатор doc-иконок не запускается, нативная libcairo отсутствует

**Ticket:** S1964
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-22
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-22

**Захвачено во время:** S1956

**Текст:**

`scripts/docs/export-doc-icon-pngs.ps1` cannot produce the PNG half of the doc-icon asset set on this machine, and nothing records how it ever could.

Symptom. The exporter writes the SVG half itself and delegates rasterization to `scripts/docs/lib/rasterize_svgs.py`, which imports cairosvg -> cairocffi -> dlopen of native libcairo. Measured 2026-08-22 during S1956: `OSError: no library called "cairo-2" was found` (every candidate name tried: cairo-2, cairo, libcairo-2, libcairo.so.2, libcairo.2.dylib, libcairo-2.dll), rasterizer exits 1, exporter exits 1. Since S1956 the exporter also reports the resulting partial set explicitly, but it still cannot complete.

Consequence. `docs/icons/doc/` holds 26 SVG and 25 PNG - `ic_launcher_mode.png` is absent. Today no markdown surface references it (the map names that drawable only under `landing`, which inlines SVG), so nothing is broken; but the asset set cannot be regenerated, and any map edit that moves a PNG-less drawable into `howto`/`docsMap` now blocks on a refusal that nobody in this environment can clear.

Missing record. Nothing under `docs/` or `dev/` states how the cairo native dependency is provisioned. The 25 existing PNGs were generated on 2026-07-03 by an environment that no longer exists here.

Decide: provision libcairo (document the install), or replace the rasterization backend with one that carries its own native code / needs none, or drop the PNG half entirely and serve the markdown surfaces from inline SVG like the landing does.

---

## 1. Проблема / симптом

Симптом и эвиденс - в §0, повторно измерены 2026-08-22 при S1931: растеризатор падает на `dlopen` нативной libcairo, экспортёр выходит 1, `docs/icons/doc/` остаётся с 26 SVG и 25 PNG.

Практическая цена уже наблюдалась: S1931 пришлось откатить готовые строки индексов гидов и уйти в `BlockExternal`, потому что PNG для нового значка получить нечем. То есть это не спящий долг - он уже остановил тикет.

---

---

## 2. Корневая причина

`cairosvg` не несёт нативного кода. Он тянет `cairocffi`, а тот на импорте делает `dlopen` системной libcairo, которой на Windows нет по умолчанию: её ставит GTK-рантайм или другое приложение, попутно кладущее DLL на PATH. Поиск 2026-08-22 по вероятным местам (`Git\mingw64\bin`, `msys64\mingw64\bin`, GIMP, Inkscape) не дал ни одного `libcairo*`.

То есть конвейер зависит от **машинного** окружения, а не от `.venv`, который он же и объявляет своим (`export-doc-icon-pngs.ps1` проверяет только `.venv/Scripts/python.exe`). Пока DLL на машине была, всё работало и никто не заметил, что зависимость внешняя; ничего в `docs/` и `dev/` её не фиксирует, потому что её никогда и не ставили осознанно.

**Ключевое ограничение выбора.** Обе половины ассетов - решение владельца, записанное в шапке экспортёра: SVG инлайнится на посадочной, «*owner: "turn it into png to show on site"*» - про PNG для markdown-поверхностей. Значит третий вариант из §0 (отказаться от PNG и везде отдавать SVG) **отменяет решение владельца** и не может быть выбран без него.

---

---

## 3. Исправление

**Решено: вариант A, выполнен 2026-08-22.** Владелец запустил `/spec-all S1964`, то есть попросил довести тикет до конца, а из трёх путей два отпадают по причинам, записанным в самом тикете: C отменяет решение владельца («png to show on site») и потому недоступен, B оставляет зависимость машинной и ломает следующую чистую машину так же. Остаётся A - и он не является «внешней установкой» в том смысле, в каком ею является B: это обычное добавление зависимости в `.venv`, который конвейер и так объявляет своим окружением. Владельцу нечего решать там, где ограничения уже оставили один вариант.

**Вариант A - выбран и реализован. Сменить бэкенд на самодостаточный, без системных зависимостей.** `resvg-py` / `resvg-python` - обёртка над Rust-библиотекой resvg, распространяется готовыми колёсами с вшитым нативным кодом, системная cairo не нужна. Правится один файл - `scripts/docs/lib/rasterize_svgs.py`: он и сегодня получает готовый JSON заданий и делает ровно одно - `svg2png(bytestring, write_to, output_width, output_height)`. Зависимость переезжает из «машины» в `.venv`, где конвейер её уже и ищет. Цена: одна новая зависимость и разовая сверка, что растр совпадает с прежним; resvg рисует не тем же движком, что cairo, поэтому байтовое равенство 25 существующих PNG не гарантировано - сверять глазами, а не хэшем.

**Вариант B (отклонён). Поставить нативную libcairo и записать это.** Дешевле в коде (ноль правок), но зависимость остаётся машинной: следующая чистая машина сломается так же, и лечится это только документом, который кто-то прочитает. Именно отсутствие такого документа и есть половина этого тикета.

**Вариант C (недоступен). Отказаться от PNG, отдавать markdown-поверхностям SVG.** Технически чище всего - нативной зависимости не остаётся вовсе, а `<img src="..svg">` браузеры рисуют. Но это **отмена решения владельца** («png to show on site»), и вдобавок `fill="currentColor"` вне инлайна разрешается в чёрный, а не в нынешний `#24292e`. Без явного согласия владельца не выбирается.

**Что сделано.** `resvg-py 0.4.0` поставлен в `.venv`; `scripts/docs/lib/rasterize_svgs.py` переведён с `cairosvg.svg2png` на `resvg_py.svg_to_bytes` (один вызов, как и было); появился `scripts/docs/lib/requirements.txt` - единственная запись о том, чем провижинится растеризатор; шапка экспортёра называет новый бэкенд и этот файл, а его сообщение об ошибке печатает готовую команду установки, то есть подсказка лежит там, где конвейер и ломается; `docs/DEV_OPS.md` рядом с `doc-icons-sync-gate` описывает и команду, и причину, по которой возврат к cairosvg запрещён.

**Про байтовое равенство.** Движок другой, поэтому 25 существующих PNG перерисованы не байт в байт - но расхождение измерено, а не предположено: попиксельно по всем 25 файлам средняя разница 0.137/255, худший файл `ic_shutter_photo.png` - 0.53/255 при максимуме 85 на кромках сглаживания. Увеличенная сверка старого и нового бок о бок (`PLAN/S1964_doc-icon-rasterizer-native-cairo-absent/compare-ic_shutter_photo-cairo-vs-resvg.png` - слева cairo, справа resvg, увеличено 3x) различий не показывает.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1956 - где обнаружено; S0889 - завёл конвейер doc-иконок.

---

## 4. Проверка

1. `pwsh -NoProfile -File scripts/docs/export-doc-icon-pngs.ps1` выходит 0, и `docs/icons/doc/` содержит одинаковое число `.svg` и `.png` (сегодня 26 против 25).
2. `ic_launcher_mode.png` существует - тот самый файл, на котором встал S1931.
3. `pwsh -NoProfile -File scripts/docs/apply-doc-icons.ps1` выходит 0 и, после правки S1956, не отказывается на отсутствующем PNG.
4. Зрительная сверка: пересозданные PNG не отличаются от прежних 25 на глаз (при варианте A движок другой, поэтому сверка глазами, а не по хэшу).
5. Способ провижининга записан там, где его найдут: `docs/DEV_OPS.md` либо шапка экспортёра - иначе следующая чистая машина повторит этот тикет.
6. Разблокировано: S1931 может доделать фазу 03 (строки индексов гидов) и уйти из `BlockExternal`.

---

## Last Audit

**Дата:** 2026-08-22 - `/spec-all`

Проверка §4, по пунктам:

- [x] 1. `scripts/docs/export-doc-icon-pngs.ps1` - exit 0, `rasterized 26 png(s)`, `svg+png emitted: 26`; в `docs/icons/doc/` 26 SVG и 26 PNG (было 26/25).
- [x] 2. `docs/icons/doc/ic_launcher_mode.png` существует - тот файл, на котором встал S1931.
- [x] 3. `scripts/docs/apply-doc-icons.ps1` - exit 0, отказа на отсутствующем PNG нет. Дополнительно `scripts/quality/assert-doc-icons-sync.ps1 -Gate` - PASS, 26 drawables.
- [x] 4. Сверка глазами: расхождение 0.137/255 в среднем по 25 файлам, худший 0.53/255; бок о бок неотличимы.
- [x] 5. Провижининг записан в трёх местах, которые читают при поломке: `scripts/docs/lib/requirements.txt`, шапка и hint экспортёра, `docs/DEV_OPS.md`.
- [x] 6. S1931 разблокирован: причина его `BlockExternal` устранена, статус возвращён в работу.

Оговорка, которую стоит знать: `cairosvg`/`cairocffi` остались в `.venv` - их никто не использует, удалять их этот тикет не стал.
