# Спецификация (compact bugfix): S1575 - В альбомной ориентации контент мастера приветствия занимает узкую колонку посреди экрана

**Ticket:** S1575
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-11
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-11

**Текст:**

Reported by the owner on his own phone: "проблема с ориентацией на welcome экранах - не занимает весь экран, а только колонку посередине".

Measured on SM-G996U1 (Galaxy S21+), Android 15 (SDK 35), standard debug v2.60.8082.309, 2026-08-11, on welcome page 0 (`page_welcome_enhanced.xml`). Window config as reported by the platform: `sw384dp w853dp h384dp .. land`, density 450dpi (2.8125). uiautomator bounds:

- `viewPager` `[0,11][2400,922]` - 2400px = 853dp, the full window width
- `layoutContent` `[638,124][1763,877]` - 1125px = **exactly 400dp**, centred, leaving 226dp of empty backdrop on each side

The same page in portrait (`w384dp h853dp`): `layoutContent` `[28,114][1052,2079]` = 1024px = 364dp, i.e. the full width minus page padding. So the column is a landscape-only symptom.

Root cause is the qualifier the cap is keyed on. `layoutContent` carries `app:layout_constraintWidth_max="@dimen/welcome_content_max_width"` in every welcome page layout, portrait and landscape alike. The dimen is declared in exactly five buckets:

- `values/dimens.xml:113` = 600dp
- `values-sw320dp/dimens.xml:84` = 400dp
- `values-sw480dp/dimens.xml:79` = 500dp
- `values-sw600dp/dimens.xml:113` = 800dp
- `values-sw720dp/dimens.xml:76` = 960dp

Every one of them is a **smallestWidth** bucket, and smallestWidth is by definition rotation-invariant. This phone is sw384dp, so it resolves `values-sw320dp` = 400dp in both orientations. In portrait a 400dp cap is invisible (the window is 384dp wide); in landscape the window is 853dp and the cap becomes the whole visible defect. The cap can never react to how much width the window actually has, because no bucket it is declared in varies with orientation or available width.

This is a survivor of S1282, not a duplicate of it. S1282 fixed the neighbouring failure - keys declared in `values-land` that were dead because a `swNNNdp` bucket shadowed them - and it created the combined buckets `values-sw320dp-land/` and `values-sw480dp-land/`, into which it moved six welcome dimens (`welcome_page_padding`, `welcome_icon_size`, `welcome_icon_margin_top`, `welcome_title_margin_top`, `welcome_title_text_size`, `welcome_description_margin_top`). `welcome_content_max_width` was not among them and has no `values-land` declaration at all, so there was nothing for S1282's sweep to resurrect. The fix pattern and the target buckets therefore already exist in the tree; this key was simply left out of them.

Secondary observation on the same page, same run: with the content boxed into 400dp x 384dp, the `gridFeatures` role tiles (S1386) fall below the fold of the page's inner ScrollView and are not visible at all in landscape - `uiautomator` lists them in portrait and not in landscape. They remain reachable by scrolling. Whether that is acceptable once the width is fixed is a judgement call, not a separate defect.

Not affected: `layout-land/page_welcome_functionality.xml` is the one landscape welcome page that does not reference the dimen.

Evidence: `temp/scratch/RFCR110NBQJ_20260811_154136.png` (landscape screenshot), uiautomator bounds quoted above for both orientations.

**Захвачено во время:** device-sweep приветствия на реальном устройстве, по симптому от владельца

---

## 1. Проблема / симптом

В альбомной ориентации контент мастера приветствия занимает узкую колонку по центру экрана вместо всей его ширины. Устройство SM-G996U1, Android 15 (SDK 35), flavor standard, debug-сборка v2.60.8082.309.

Ширина колонки измерена и равна ровно 400dp при ширине окна 853dp - то есть 47% экрана, по 226dp пустого фона с каждой стороны. В книжной ориентации симптома нет: там окно шириной 384dp, и ограничение в 400dp просто ни на что не влияет.

Ограничение задаётся ресурсом `welcome_content_max_width`, объявленным только в бакетах по наименьшей ширине экрана (`swNNNdp`). Наименьшая ширина по определению не меняется при повороте, поэтому этот ресурс в принципе не способен отреагировать на альбомную ориентацию: он описывает, насколько велико устройство, а не сколько ширины есть у окна прямо сейчас.

Затронуты все страницы мастера, кроме альбомного варианта страницы функциональности - единственного, который этот ресурс не использует.

Побочное следствие на той же странице: плитки ролей приложения уходят за нижнюю границу прокручиваемой области и в альбомной ориентации не видны вовсе.

Не дубликат S1282: тот тикет чинил соседний отказ - ключи из `values-land`, задавленные бакетом `swNNNdp`, - и создал комбинированные бакеты `values-sw320dp-land` и `values-sw480dp-land`, куда перенёс шесть других welcome-ресурсов. Этого ключа среди них нет, и объявления в `values-land` у него никогда не было, поэтому подхватывать той правке было нечего.

---

## 2. Корневая причина

Ресурс описывает не тот параметр, от которого зависит ответ.

`welcome_content_max_width` объявлен ровно в пяти бакетах, и все пять - по наименьшей ширине экрана: `values` 600dp, `values-sw320dp` 400dp, `values-sw480dp` 500dp, `values-sw600dp` 800dp, `values-sw720dp` 960dp. Наименьшая ширина - характеристика устройства, а не окна: при повороте она не меняется по определению. Поэтому значение отвечает на вопрос «насколько велик аппарат», тогда как ограничению ширины нужен ответ на «сколько ширины есть у окна сейчас».

Меньшие значения в телефонных бакетах существуют ровно по одной причине - чтобы уместиться в узкое книжное окно: 600dp базового бакета не влезают в 384dp. То есть 400dp - это не решение о том, какой ширины должен быть контент, а обход книжного ограничения. В альбомной ориентации этого ограничения нет, а понижение остаётся - и становится самим дефектом.

Порядок разрешения ресурсов это подтверждает и заодно даёт место для правки: Android сверяет квалификаторы в фиксированном порядке, и `smallestWidth` проверяется раньше `orientation`. Поэтому `values-sw320dp` побеждает `values-land`, а вот комбинированный `values-sw320dp-land` побеждает `values-sw320dp`. Такие бакеты в дереве уже есть - их завёл S1282 ровно под этот класс отказов, - и в них этого ключа просто нет.

Планшетные бакеты чинить не нужно: `values-sw600dp` 800dp при альбомном окне около 960dp и `values-sw720dp` 960dp при окне около 1280dp ограничением почти не связывают. Дефект локализован в двух телефонных бакетах.

---

## 3. Исправление

Механическая часть однозначна: объявить `welcome_content_max_width` в `values-sw320dp-land/dimens.xml` и `values-sw480dp-land/dimens.xml`, рядом с шестью welcome-ресурсами, которые S1282 туда уже перенёс. Больше ничего менять не требуется - разметка страниц, `values-land` и планшетные бакеты не трогаются.

Неоднозначно было ровно одно - само число. Владелец выбрал **полную ширину окна**: в обоих телефонных альбомных бакетах предел не должен связывать вовсе. Рассмотренные кандидаты, посчитанные на измеренном окне 853dp:

- **600dp** (базовое значение из `values/dimens.xml`). Выводится из дерева, а не придумывается: это и есть проектная величина предела, которую телефонные бакеты понижали только ради книжной ориентации. Даёт 70% ширины окна, поля по 126dp. Длина строки описания остаётся в читаемых пределах. Рекомендовалось, отклонено.
- **Полная ширина окна** - **выбрано**. Буквально отвечает на формулировку симптома. Компромисс принят осознанно: на 853dp строка описания заметно длиннее типографски читаемой.

Промежуточные значения (720dp, 800dp) рассматривались как тот же выбор, сдвинутый по шкале, и не выбраны.

Реализация выбранного варианта. `layout_constraintWidth_max` принимает только размерность - «без предела» отдельным токеном не выражается, поэтому в обоих бакетах объявляется заведомо не связывающее значение `9999dp` с EN-комментарием, объясняющим, почему число такое (решение владельца по S1575: в альбомной ориентации на телефоне предел снят). Значение одинаково в `values-sw320dp-land` и `values-sw480dp-land`: масштабирование по размеру устройства остаётся за планшетными бакетами, которые правка не трогает.

### 3.3 Owner inputs (Approval gate)

- **Максимальная ширина контента мастера в альбомной ориентации на телефоне:** полная ширина окна - предел снимается, то есть объявляется заведомо не связывающим (`9999dp`). Решено владельцем 2026-08-11 через `/spec-quiz`.
- **Одинаковость по бакетам:** одно и то же значение в `values-sw320dp-land` и `values-sw480dp-land`; планшетные бакеты не трогаются. Решено владельцем 2026-08-11 через `/spec-quiz`.
- **Related tickets:** S1282 (комбинированные `-land` бакеты и сам шаблон исправления - предшественник, этот ключ в его правку не попал), S1386 (плитки ролей на этой же странице - потребитель освободившейся ширины), S1237 (тот же ресурс на странице плеера по умолчанию, поднимал его для больших экранов), S1377 (перестроение мастера при повороте - работает, проверено в том же прогоне)

### Quiz decisions (2026-08-11)

- Максимальная ширина контента мастера в альбомной ориентации на телефоне → полная ширина окна (рекомендовалось 600dp как выводимое из дерева; владелец предпочёл буквальное устранение симптома и принял более длинную строку описания)
- Одно ли значение в обоих телефонных альбомных бакетах → одно и то же (шкала по размеру устройства остаётся за планшетными бакетами, которые правка не трогает)

---

## 4. Проверка

Прогнана 2026-08-11, все пункты закрыты.

- Ключ объявлен в обоих телефонных альбомных бакетах: grep по `values-sw320dp-land/dimens.xml` и `values-sw480dp-land/dimens.xml` находит `welcome_content_max_width`. `expected: 2 | actual: 2`.
- `.\a.ps1 fr` - ресурсы и манифест собираются. `expected: exit 0 | actual: exit 0` (BUILD SUCCESSFUL, 17s).
- `scripts/post-change.ps1 -ChangeType Xml -ScopeToFile` по обоим файлам - `expected: PASS | actual: PASS` (exit 0, 7733 ms).
- На устройстве, обе ориентации одной страницы (SM-G996U1, окно 853dp в альбоме, плотность 2.8125): ширина `layoutContent` по `uiautomator` равна всей доступной ширине, а не 400dp. `expected: 821dp в альбоме, 364dp в книжной | actual: 725dp в альбоме, 364dp в книжной`.
  - Книжная сходится точно: 1024px = 364dp, ровно как в замере раздела 0 - предел там не связывал и не связывает.
  - Альбомная даёт 2040px = 725dp вместо предсказанных 821dp, и разницу объясняет не предел, а системные врезки, которые предсказание не учло. Дерево на момент замера: `viewPager` `[0,11][2400,922]` (всё окно), хост страницы `[135,79][2265,922]` - по 135px = 48dp врезки с каждой стороны (справа это `navigationBarBackground` `[2265,0][2400,1080]`, слева симметричная компенсация), и уже внутри него отступ страницы 16dp = 45px на сторону. Итого 853 - 2·48 - 2·16 = 725dp, что совпадает с замером до dp.
  - Вывод по существу проверки: предел не связывает вовсе - ширину задают только системные врезки и отступ страницы, то есть выбранный вариант «полная ширина окна» реализован. Прежние 400dp выросли до 725dp.
- Побочное наблюдение раздела 0 само не ушло: плитки ролей (`gridFeatures` `[301,833][2220,877]`) в альбоме теперь присутствуют в дереве, но обрезаны нижней границей ScrollView - видна примерно половина ряда. `expected: видны целиком | actual: обрезаны`. Это вертикальная посадка, а не ширина, и в §3 этой правки она не входит. Отдельный тикет не заводится: наблюдение принадлежит S1386, чей `Status note` дословно требует проверить те же плитки в обеих ориентациях на реальном устройстве, - там оно и должно быть закрыто.
- Доказательства: `temp/scratch/RFCR110NBQJ_20260811_232646.png` (альбомный экран после правки), дампы `temp/scratch/s1575_land.xml` и `temp/scratch/s1575_port.xml`.
- Планшетные бакеты не задеты: `values-sw600dp` (800dp) и `values-sw720dp` (960dp) не менялись. `expected: без изменений | actual: без изменений`.

---

## Last Audit

**Date:** 2026-08-11
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Checked: the key is declared in `values-sw320dp-land` and `values-sw480dp-land` (2 hits, `9999dp` in both); the five pre-existing buckets are byte-identical (`values` 600dp, `values-sw320dp` 400dp, `values-sw480dp` 500dp, `values-sw600dp` 800dp, `values-sw720dp` 960dp), so portrait and tablet behaviour is untouched; `a.ps1 fr` exit 0; `post-change -ChangeType Xml -ScopeToFile` PASS over both files; dev log carries the change; zero `Timber.d("S1575:` tags anywhere in `.kt`, matching a non-`BlockNeedUserTest` status; on-device measurement on the reporting device confirms 400dp -> 725dp in landscape with portrait unchanged at 364dp.

EXEMPT: FEATURES trilingual - this is a compact bugfix spec with no §8 block, and the welcome flow already carries its `ALL_FEATURES` record (`First-launch welcome flow`); a layout defect fix adds no new capability.

### Manual / on-device

- [x] Landscape width on SM-G996U1, both orientations - done 2026-08-11, evidence in §4.
- [ ] Role tiles clipped at the ScrollView bottom in landscape - belongs to S1386, which is already `BlockNeedUserTest` for exactly this check.
