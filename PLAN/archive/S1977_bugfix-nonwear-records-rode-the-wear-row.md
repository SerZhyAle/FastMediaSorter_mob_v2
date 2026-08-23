# Спецификация (compact bugfix): S1977 - Десять записей инвентаря не про часы держали набор флейворов строки Wear

**Ticket:** S1977
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-23
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-23

**Захвачено во время:** S1951 (снятие заявки на Wear-компаньон у флейвора `legacy`).

**Текст:**

Ten ALL_FEATURES records that are not about the watch carry flavors [legacy, noLegal, standard], a set
that matched no capability of theirs - it happened to equal the SUPPORT_WEAR_COMPANION row. S1951
flipped that row to [noLegal, standard], so the coincidence ended and the S1934 ungated-flavors ratchet
now counts all ten. Each needs its own gate decision.

Six are streams records (streams.stream-browser-grid-mode-live-channels-shown-as,
streams.offline-stream-play-soft-fail, streams.main_window_panel, streams.panel_inline_audio,
streams.reorder-pinned-streams, streams.clear-all-downloaded-channels) whose 40 sibling records already
carry the SUPPORT_STREAMS row [legacy, noLegal, standard, vr] and one sibling carries it explicitly
gated - so those six look like a missing vr, not a research question.

Four need real research: media-browsing.inline-audio-playback-in-browse-list,
video-player.d-pad-and-tv-remote-focus-navigation, widgets.voice-recorder-widget, and
streaming.live-stream-chromecast-cast - the last one spans two gating answers (it needs both
SUPPORT_STREAMS and SUPPORT_CAST, and vr has streams but no cast), which is exactly the S1933 shape.

S1951 raised scripts/all_features/unexplained-flavors-baseline.txt from 240 to 250 to hold these ten;
closing this ticket must lower it back by exactly the number fixed.

---

## 1. Проблема / симптом

Десять записей `docs/ALL_FEATURES.jsonl` несут набор `[legacy, noLegal, standard]`. Ни у одной из них
этот набор не выведен из системы сборки: он совпадал со строкой `SUPPORT_WEAR_COMPANION`, к возможностям
часов не относясь.

Замер 2026-08-23, пересчитан независимой реализацией правила S1934 и совпал с валидатором:

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0, 798 записей, храповик 250 против
  базовой линии 250. Ошибки нет ровно потому, что S1951 линию поднял.
- Все десять записей присутствуют в списке необъяснимых; ни одна не несёт поля `gate`.
- Строки `[legacy, noLegal, standard]` в `docs/FLAVOR_MATRIX.md` больше нет ни у одного из 23 флагов.

Заплатить за это может владелец сборки: `/skill-release` строит публичный showcase из диффа инвентаря,
поэтому запись обещает возможность там, где её нет, и умалчивает о сборке, где она есть. Три из десяти
записей вдобавок умалчивают о `vr`, у которого возможность есть.

---

## 2. Корневая причина

Набор брался не из системы сборки. Правило S1934 (ADR-1) требует выводить `flavors` из строки флага в
`docs/FLAVOR_MATRIX.md`, а при отсутствии гейта - брать полную шестёрку; ни один из этих двух выводов не
даёт `[legacy, noLegal, standard]` сегодня. До S1951 набор случайно совпадал со строкой чужого флага, и
храповик S1934 считал его объяснённым: проверка не отличает совпадение по смыслу от совпадения по числу.

Поэтому чинится не одно значение, а десять независимых решений «за каким флагом живёт эта возможность»,
каждое со своим доказательством.

---

## 3. Исправление

Девять записей получают гейт и набор, выведенный из матрицы. Десятая невыразима сегодняшней схемой и
передаётся отдельному тикету.

### Шесть записей стримов - `SUPPORT_STREAMS`

Гейт `SUPPORT_STREAMS`, набор `[legacy, noLegal, standard, vr]`.

- `app_v2/build.gradle.kts` объявляет `SUPPORT_STREAMS=true` во флейворе `vr`, и комментарий S0565 там
  прямо называет entry-point стримов. Отсутствие `vr` в наборе утверждало исключение, которого в сборке
  нет.
- 40 соседних записей стримов уже несут ровно эту строку, а `streams.preset-enabled-catalog-autoload`
  несёт её с явным `gate: SUPPORT_STREAMS`.
- Ни одна из шести не сужается второй возможностью: сетка с кадрами упирается в `SUPPORT_VIDEO`, панель
  с радио - в `SUPPORT_AUDIO`, и пересечение каждой из этих строк со строкой `SUPPORT_STREAMS` равно
  самой строке `SUPPORT_STREAMS`. Конъюнкции, как у записи каста, здесь не возникает.

### `widgets.voice-recorder-widget` - `SUPPORT_MIC_RECORDING`

Гейт `SUPPORT_MIC_RECORDING`, набор `[legacy, noLegal, standard, vr]`.

- `app_v2/src/main/AndroidManifest.xml:541` объявляет провайдер, активность-трамплин и сервис виджета с
  комментарием «removed in lite/photos (SUPPORT_MIC_RECORDING=false)».
- `app_v2/src/lite/AndroidManifest.xml:55` и `app_v2/src/photos/AndroidManifest.xml:70` снимают все три
  компонента через `tools:node="remove"`; `app_v2/src/vr/AndroidManifest.xml` не снимает ничего.
- Гейт лежит на слое слияния манифестов, а не в рантайме: KDoc `QuickAudioRecorderWidgetProvider` прямо
  говорит, что класс не читает `BuildConfig`. В `lite` и `photos` у виджета нет точки входа вовсе - его
  нельзя поставить на экран, а не только не видно.
- `DECLARES_MIC_RECORDING` отвергнут: во флейворе `lite` он `true` при `SUPPORT_MIC_RECORDING=false`, и
  виджета там всё равно нет. Путаница этих двух флагов уже стоила одного тикета (S1459).
- Запись - надгробие (`status: removed`), и это выяснилось при правке. Возможность из приложения не
  исчезла: её описывает активная запись `widgets.quick-audio-recorder-widget`, а компоненты виджета
  по-прежнему объявлены в манифесте. Гейт надгробию всё равно ставится - храповик считает записи
  независимо от `status`, а утверждение «жил за `SUPPORT_MIC_RECORDING`» верно и для отгруженного
  прошлого. Активную запись-преемницу тикет не трогает: её набор и так равен строке того же флага,
  в классе необъяснимых она не числится, а гейт проставляется записи, до которой дошли руки, а не
  кампанией (S1933, ADR-4).

### `media-browsing.inline-audio-playback-in-browse-list` - `SUPPORT_AUDIO`

Гейт `SUPPORT_AUDIO`, набор `[legacy, lite, noLegal, standard, vr]`.

- Сам `BrowseInlineAudioManager` не читает ни одной возможности, и видимость кнопки play в
  `MediaFileAdapter` решается только типом файла.
- Единственный гейт лежит слоем выше, на выдаче списка: `GetMediaFilesUseCase.kt:461` и
  `ResolveScanFilterUseCase.kt:112` пропускают `MediaType.AUDIO` по `mediaCapabilities.supportsAudio`,
  который в каждом флейворе связан с `BuildConfig.SUPPORT_AUDIO`. Во флейворе `photos` аудиострок в
  списке не появляется вовсе, поэтому и играть в списке нечего.
- `ENABLE_PERSISTENT_AUDIO_PLAYBACK` отвергнут: все его потребители относятся к фоновому
  воспроизведению, и ни один не встречается на пути встроенного проигрывания в списке.
- `SUPPORT_LOCAL_NETWORK` отвергнут: SMB - лишь один из источников, чьи файлы бывают аудио; отдельного
  гейта у выкачивания в кэш с упреждающей загрузкой нет.

### `video-player.d-pad-and-tv-remote-focus-navigation` - `SUPPORT_VIDEO`

Гейт `SUPPORT_VIDEO`, набор `[legacy, lite, noLegal, standard, vr]`.

Разбор кода показал, что сама обработка ввода не гейтирована: `PlayerInputDispatcher` не читает ни
`BuildConfig`, ни возможностей, а разметка `custom_player_controls.xml` с тридцатью атрибутами
`focusable`/`nextFocus*` лежит только в `src/main` и ни одним флейвором не переопределяется. Полная
шестёрка тем не менее неверна, потому что граница записи проведена по видеоплееру: её имя и описание
говорят именно о нём. Во флейворе `photos` `SUPPORT_VIDEO=false`, видеофайлы до плеера не доходят через
тот же `applyFlavorMediaTypeRestrictions` (`GetMediaFilesUseCase.kt:460`), и сессии видеоплеера, в
которой работала бы навигация с пульта, там не бывает. Набор из пяти флейворов - истинное утверждение;
шестёрка обещала бы возможность в сборке, которая её не достигает.

Текущий набор неверен в любом из двух прочтений: он исключает и `lite`, и `vr`, где `SUPPORT_VIDEO`
объявлен `true`.

### `streaming.live-stream-chromecast-cast` - не чинится здесь

Запись остаётся с набором `[legacy, noLegal, standard]` и без гейта; храповик продолжает её считать.

Набор правдив: каст живого потока требует обеих возможностей сразу - поток за `SUPPORT_STREAMS`
`[legacy, noLegal, standard, vr]`, приёмник за `SUPPORT_CAST` `[legacy, lite, noLegal, photos, standard]`
(source set `src/castEnabled`, `vr` его не монтирует), и пересечение равно ровно текущему набору.
Выразить это нечем: `gate` одно-значное по схеме, а `validate.ps1` требует точного совпадения со строкой
названного флага. `SUPPORT_STREAMS` соврёт про `vr`, `SUPPORT_CAST` - про `lite` и `photos`.

Это не форма S1933: там у записи были две половины с разными ответами, и лечилось разрезом. Здесь
половин нет - «каст живого потока» не существует без обеих сторон. Расширение схемы до конъюнкции - это
собственное решение со своим риском, а именно ослабить храповик S1934, поэтому вопрос вынесен в S1982.

### Базовая линия храповика

`scripts/all_features/unexplained-flavors-baseline.txt` опускается с 250 до 241 - ровно на девять
исправленных записей. Записи, получившие `gate`, выпадают из счёта необъяснимых по построению проверки:
валидатор считает их по другой ветке, сверяя со строкой флага.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1951 (поднял baseline), S1929, S1933, S1934 (все Archived - разбор соответствия инвентаря матрице флейворов), S1982 (несёт вопрос о записи каста), S1459 (историческая путаница двух флагов микрофона)
- **Sensitive scope:** нет. Тикет не трогает исходники, ресурсы, строки, разрешения и публичный showcase; правятся только девелоперский инвентарь и число базовой линии.

---

## 4. Проверка

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0, 798 записей, храповик 241 против
  базовой линии 241, без строки «ratchet improved».
- Девять записей несут ожидаемые `gate` и `flavors`; расхождение любой из них со строкой своего флага
  валидатор отвергает сам (S1929).
- `streaming.live-stream-chromecast-cast` остаётся без `gate` с прежним набором.

---

## 5. Фазы

### Phase 01 - Девять записей получают гейт

#### Step 01.1 - Шесть записей стримов за `SUPPORT_STREAMS`

**Files:** `docs/ALL_FEATURES.jsonl`

**Prompt for developer:**

> Через `scripts/all_features/patch.ps1` проставить каждой из шести записей стримов
> (`streams.stream-browser-grid-mode-live-channels-shown-as`, `streams.offline-stream-play-soft-fail`,
> `streams.main_window_panel`, `streams.panel_inline_audio`, `streams.reorder-pinned-streams`,
> `streams.clear-all-downloaded-channels`) `-Gate SUPPORT_STREAMS` и
> `-SetFlavors "legacy,noLegal,standard,vr"`. Остальные поля не трогать.

**Why:**

§3, решение о стримах: `vr` объявляет `SUPPORT_STREAMS=true`, поэтому прежний набор утверждал исключение, которого в
сборке нет, а без поля `gate` верный набор остался бы непроверяемым и снова разъехался бы при следующем
сдвиге матрицы - ровно так эти шесть записей и попали в класс необъяснимых.

**Verification:**

- Run: `pwsh -NoProfile -File scripts/all_features/validate.ps1` - expected: exit 0.
- Каждая из шести записей несёт `"gate":"SUPPORT_STREAMS"` и ровно четыре флейвора.

**Status:** `[x]` done - 2026-08-23

---

#### Step 01.2 - Три записи вне стримов за своими флагами

**Files:** `docs/ALL_FEATURES.jsonl`

**Prompt for developer:**

> Через `patch.ps1` проставить: `widgets.voice-recorder-widget` -
> `-Gate SUPPORT_MIC_RECORDING -SetFlavors "legacy,noLegal,standard,vr"`;
> `media-browsing.inline-audio-playback-in-browse-list` -
> `-Gate SUPPORT_AUDIO -SetFlavors "legacy,lite,noLegal,standard,vr"`;
> `video-player.d-pad-and-tv-remote-focus-navigation` -
> `-Gate SUPPORT_VIDEO -SetFlavors "legacy,lite,noLegal,standard,vr"`.

**Why:**

§3, решения о виджете диктофона, встроенном аудио и навигации с пульта: у каждой из трёх гейт установлен по коду - снятием компонентов из манифеста для виджета и
фильтром типов медиа на выдаче списка для двух остальных, - и во всех трёх случаях прежний набор
исключал флейворы, где возможность есть.

**Verification:**

- Run: `pwsh -NoProfile -File scripts/all_features/validate.ps1` - expected: exit 0.
- Три записи несут ожидаемые `gate`; несовпадение со строкой флага валидатор отвергает сам.

**Status:** `[x]` done - 2026-08-23

---

### Phase 02 - Закрыть счёт

#### Step 02.1 - Опустить базовую линию до 241

**Files:** `scripts/all_features/unexplained-flavors-baseline.txt`

**Prompt for developer:**

> Записать в файл `241` вместо `250` - ровно на девять исправленных записей. Десятая,
> `streaming.live-stream-chromecast-cast`, остаётся в счёте.

**Why:**

§3, базовая линия храповика, и §0: S1951 поднял линию на десять, чтобы храповик не упал; оставить её поднятой значит освободить
место для девяти будущих необъяснимых записей, что и есть отказ храповика делать свою работу.

**Verification:**

- Run: `pwsh -NoProfile -File scripts/all_features/validate.ps1` - expected: exit 0, без строки
  «ratchet improved».
- Файл содержит одно целое число `241`.

**Status:** `[x]` done - 2026-08-23

---

## 6. Открытые вопросы / Research items

1. Как записи инвентаря выразить принадлежность двум флагам сразу, и не ослабит ли это храповик S1934.
   Касается `streaming.live-stream-chromecast-cast` (§3, решение о записи каста). Status: `Carrier: S1982`.

---

## 7. Non-goals

- Починка остальных 241 необъяснимых записей. Храповик держит класс от роста, и S1934 (ADR-2) прямо
  отказался чинить их вслепую: разбор каждой требует собственного доказательства.
- Расширение схемы `gate` до конъюнкции - это S1982.
- Сплошная проверка инвентаря на записи той же формы, что запись каста. S1933 (ADR-4) установил порядок:
  правило применяется к записи, до которой дошли руки, а не кампанией.
- Правка публичного showcase `docs/FEATURES*.md` - им владеет `/skill-release`.

<!-- auto-approved by /spec-code - 2026-08-23 -->

---

## Last Audit

**Date:** 2026-08-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 11 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Checked: `validate.ps1` exit 0 on 798 records with no "ratchet improved" line; baseline file holds
`241`; the six streams records carry `gate: SUPPORT_STREAMS` with four flavors; the three researched
records carry `SUPPORT_MIC_RECORDING` / `SUPPORT_AUDIO` / `SUPPORT_VIDEO` with the matrix row of each;
`streaming.live-stream-chromecast-cast` still ungated with its prior set; the §6 open item names
`Carrier: S1982` and that ticket resolves in the catalog; zero `Timber.d("S1977:` tags in `.kt`;
dev-log entry present; all three phase steps `[x] done`; `post-change.ps1 -ScopeToFile` PASS.

EXEMPT: trilingual showcase - the ticket delivers no user-visible capability, so `docs/FEATURES*.md`
and `docs/ALL_FEATURES.jsonl` gain no new record; only existing records' gating metadata changed.

### Manual / on-device

- Нет. Тикет не трогает ничего, что попадает в APK; проверка целиком механическая.
