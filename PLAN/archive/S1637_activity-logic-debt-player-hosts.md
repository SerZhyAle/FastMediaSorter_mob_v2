# Стратегическая спецификация: S1637 - оставшиеся 32 нарушения Rule 3 в двух плеерных хостах

**Ticket:** S1637
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-14
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - follow-up тикет, заведённый шагом 07.4 S1329
**Tactical spec:** `PLAN/S1637_activity-logic-debt-player-hosts/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14

**Захвачено во время:** S1329

**Текст:**

Follow-up to S1329: the 32 `ActivityLogicViolation` entries S1329 deliberately deferred - 20 in `PlayerActivity.kt` and 12 in `PhotoVideoStandaloneActivity.kt`. S1329 cleared the other 46 across 13 files and ratcheted the count gate to 32, so this remainder cannot grow while it waits.

The two files are bundled deliberately, not for convenience. About 15 of the 32 are one shared image/GIF edit cluster - rotate, flip, network edit, filter, adjust, merge overlay, plus three GIF use cases that exist only in the player. Splitting the two hosts across two tickets would build that shared facade twice, which is exactly the owner decision recorded in S1329 §3.1 (2026-08-02).

Three constraints carried over from S1329, each verified there rather than assumed:

- `PhotoVideoStandaloneActivity` builds five of the same managers S1329 moved behind `StandaloneHostFactory` (`NetworkFileManager`, `TranslationManager`, `DestinationButtonsManager`, `PdfViewerManager`, `EpubViewerManager`). This ticket injects that existing factory and must not re-add the six dependencies anywhere - not to the host, not to a new parallel factory.
- `PhotoVideoStandaloneActivity` also binds `StandalonePlayerViewModel`. S1329 extended that ViewModel only with the lyrics lookup (`SearchLyricsUseCase`); it deliberately did **not** grow the six-dependency surface the original S1329 plan proposed, and S1329 §9 ADR-1 records why that surface was refuted (a behavioural surface cannot hand an object to a manager constructor, so it would have forced signature changes on five shared managers).
- Manager constructor signatures stay untouched, for the same reason: the factory adapts to the manager as written.

Stale-probe check, performed at capture time: `S0995` is `Archived` (verified via `select.ps1` on 2026-08-14, archived 2026-08-13), and `grep 'Timber.d("S0995'` over `app_v2/src` returns zero hits - so no stale probe tag is waiting in `PhotoVideoStandaloneActivity`. Re-check before implementation rather than trusting this line.

Origin: S1329 (`activity-logic-debt-78-baselined-violations`), step 07.4 of its tactical plan.

---

## 1. Проблема

Два плеерных хоста держат 32 доменные зависимости прямым `@Inject`-полем - репозитории и use case, к которым активность обращается напрямую. Это нарушение Rule 3, и оно единственное, что осталось после S1329. Пользователь эффекта не видит; цена в том, что самые крупные и регрессионно-опасные экраны приложения знают о слое данных напрямую, а логику нельзя покрыть тестами через ViewModel.

---

## 2. Цели

1. Ни `PlayerActivity`, ни `PhotoVideoStandaloneActivity` не объявляют `@Inject`-поле доменного типа.
2. Счётчик `ActivityLogicViolation`, посчитанный по исходникам, равен нулю, и гейт опущен до нуля.
3. Общий кластер редактирования изображений собран один раз и переиспользован обоими хостами.
4. Поведение не меняется - ни строк, ни разметки, ни потоков воспроизведения.

**Non-goals:**

- Декомпозиция обоих файлов по LOC - отдельная тема.
- Расширение поверхности `StandalonePlayerViewModel` шестью зависимостями - отвергнуто в S1329 §9 ADR-1.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Ни один хост не должен вырасти по строкам ради этой правки. У `PlayerActivity.kt` осталось около 76 строк до потолка Rule 2 (§4), поэтому проводка фабрики должна убирать строки, а не добавлять.

### 3.2 Жёсткие ограничения

- **Flavor:** все варианты.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** перенос зависимости не должен создавать её раньше, чем создавала активность - где стоял `dagger.Lazy`, он сохраняется (Rule 18).
- **Совместимость данных:** миграций нет.
- **Локализация:** не затрагивается.
- **Доступность:** не затрагивается.

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S1329 (источник, там же образец фабричной формы), S0002 (декомпозиция больших файлов, Archived)

---

## 4. Контекст текущей архитектуры

Измерено 2026-08-14 по живому дереву; §0 - вербатим-захват и не правится, поэтому расхождения записаны здесь.

**Счёт подтверждён.** `assert-activity-logic-not-growing.ps1` даёт `baseline 32 | actual 32 | delta 0`, и разбивка ровно та, что названа в §0: 20 полей в `PlayerActivity.kt`, 12 в `PhotoVideoStandaloneActivity.kt`.

**Две поправки к §0, обе меняют объём работ.**

- §0 утверждает, что `PhotoVideoStandaloneActivity` сам строит пять менеджеров, уже спрятанных за `StandaloneHostFactory`. На самом деле их два: `TranslationManager` и `DestinationButtonsManager`, оба через `by lazy`. `NetworkFileManager`, `PdfViewerManager` и `EpubViewerManager` в этом файле не встречаются ни разу - у экрана просто нет ни PDF-, ни EPUB-, ни общей сетевой полосы. Работа по этому пункту меньше заявленной.
- `StandaloneHostFactory` (`ui/player/standalone/StandaloneHostFactory.kt`, 256 строк) уже используется четырьмя хостами - `TextStandaloneActivity`, `AudioStandaloneActivity`, `DocumentStandaloneActivity`, `StandalonePlayerActivity`. Ни один из двух хостов этого тикета к ней не подключён, что подтверждает саму посылку тикета.

**Размер как ограничение, а не как фон.** `PlayerActivity.kt` - 1424 строки при потолке 1500, то есть запас около 76 строк; `PhotoVideoStandaloneActivity.kt` - 1321. Любая проводка, которая добавляет строк больше, чем убирает, упирает первый файл в Rule 2 и втягивает в тикет декомпозицию, явно вынесенную в non-goals.

**Устаревшая проба.** `grep 'Timber.d("S0995'` по `app_v2/src` - ноль совпадений. Строка §0 перепроверена, а не принята на веру.

**Третья поправка, найдена при реализации 2026-08-14.** `DrawCropCompositor` не читает поле через хост: он принимает `MergeDrawOverlayUseCase` конструктором со времён S0679. Строки 25 и 104, названные в §6.1 как места сквозного чтения, - это сам параметр и его единственное использование; замер туда попал по grep-у имени, а не выражения `activity.mergeDrawOverlayUseCase`. Реальных сквозных чтений четыре, все в `PlayerDrawingSaveHelper` (230, 324, 512, 597), плюс одно построение `DrawCropCompositor` на строке 230, которому use case передаётся из хоста. Объём шага 01.1 из-за этого нулевой, бюджет полей не меняется.

---

## 5. Предлагаемый подход

Форму выбирает замер из §6.1, а не предпочтение: все пятнадцать обращений кластера - передача объекта, ни одного вызова поведения. Значит нужна фабрика, а не поведенческий фасад; фасад пришлось бы наделять поверхностью, которой никто не пользуется.

### 5.1 Основные столпы / модули

**Фабрика кластера редактирования.** Один поставщик на оба хоста, отдающий девять use case редактирования изображений и GIF. Хосты перестают объявлять их полями; получатели - `PlayerDialogHelper`, `ImageEditDialog`, `StandaloneDrawSaveHelper` - продолжают принимать их конструктором, как принимают сейчас. Сигнатуры менеджеров не трогаются (ограничение §0).

**Подключение `PhotoVideoStandaloneActivity` к существующей фабрике.** Только для двух менеджеров, которые он действительно строит сам (§4). Ни новой параллельной фабрики, ни повторного объявления зависимостей.

**Отдельный случай - `mergeDrawOverlayUseCase` в `PlayerActivity`.** Это не передача в конструктор и не вызов поведения, а третий вид: чтение поля насквозь. `PlayerDrawingSaveHelper` и `DrawCropCompositor` обращаются к `activity.mergeDrawOverlayUseCase` извне. Убрать поле нельзя, не тронув эти два места, поэтому они получают use case конструктором - как и все остальные получатели кластера. Это единственное место, где правка выходит за пределы двух файлов хостов, и его стоит сделать первым шагом, а не последним.

**Остаток.** Одиннадцать полей `PlayerActivity` и шесть `PhotoVideoStandaloneActivity` вне кластера разбираются по четырём формам S1329 §5.1 (фабрика, ViewModel, унаследованный поток настроек, мёртвое поле); какая форма к какому полю - решает тактический план, а не эта спека.

### 5.2 Потоки данных и событий

Hilt -> фабрика -> конструктор менеджера или диалога -> use case. Активность из цепочки выпадает целиком: сегодня она держит ссылку только затем, чтобы передать её дальше.

### 5.3 Точки расширяемости

Новый диалог редактирования получает зависимость из той же фабрики, не добавляя полей хосту. Появление третьего хоста с тем же кластером не порождает третьей копии.

---

## 6. Открытые вопросы / Research items

1. **Форма общего кластера редактирования изображений**
   - **Вопрос:** одна фабрика на оба хоста, или фасад с собственной поверхностью поведения?
   - **Статус:** Resolved - фабрика. Замер 2026-08-14 по всем пятнадцати обращениям: **15 передач объекта, 0 вызовов поведения**. В `PlayerActivity` восемь из девяти уходят именованными аргументами в конструктор `PlayerDialogHelper` (`PlayerManagerInitializer.kt:335-343`); в `PhotoVideoStandaloneActivity` все шесть - в конструкторы `ImageEditDialog` (строки 1023-1032) и `StandaloneDrawSaveHelper` (строка 193). Ни одна активность не вызывает `execute()` сама. Поведенческий фасад пришлось бы наделить поверхностью, которую никто не вызывает.
   - **Что замер вскрыл сверх вопроса:** пятнадцатое обращение, `mergeDrawOverlayUseCase` в `PlayerActivity`, не подходит ни под «передачу», ни под «поведение». Поле объявлено на строке 886 и больше в самом файле не упоминается: его читают снаружи - `PlayerDrawingSaveHelper.kt:230,324,512,597` и `DrawCropCompositor.kt:25,104` - через `activity.mergeDrawOverlayUseCase`. Форма исправления записана в §5.1; рамка «фабрика или фасад» этот случай не покрывала.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Перенос зависимостей меняет порядок инициализации в самом крупном плеерном хосте | Средняя | Регресс воспроизведения на реальном устройстве | Разбирать по одному хосту, каждый - самостоятельная единица с отдельной проверкой |
| Кластер редактирования изображений строится дважды | Средняя | Дублирование и расхождение поведения между хостами | Оба файла в одном тикете - ровно ради этого решение владельца S1329 §3.1 |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта; фабричная форма и её обоснование унаследованы из S1329 §9 ADR-1.

---

## 10. Связи с другими спеками

- S1329 - тикет-источник: очистил 46 нарушений из 78, оставил эти 32 и построил `StandaloneHostFactory`, который здесь переиспользуется.

---

## 11. Критерии готовности (strategic-level)

1. Ни один из двух хостов не объявляет `@Inject`-поле доменного типа.
2. Счётчик нарушений по исходникам равен нулю, и `scripts/quality/assert-activity-logic-not-growing.ps1` опущен до нуля.
3. Сборка зелёная, поведение не изменилось, `@Suppress("ActivityLogicViolation")` нигде не появился.

---

## Last Audit

**Date:** 2026-08-14
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 14 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Оба хоста больше не объявляют ни одного `@Inject`-поля доменного типа: `assert-activity-logic-not-growing.ps1 -Gate` даёт `baseline 0 | actual 0 | delta 0` и выходит с кодом 0, а `@Suppress("ActivityLogicViolation")` не встречается нигде в `app_v2/src` - счёт упал снятием полей, а не глушением гейта. Кластер редактирования собран один раз (`ImageEditFactory`) и потребляется обоими хостами, `dagger.Lazy` сохранён там, где он был у хоста (`credentialsRepository`). Оба файла стали короче: `PlayerActivity.kt` 1424 -> 1383, `PhotoVideoStandaloneActivity.kt` 1321 -> 1279, то есть пожелание §3.1 выполнено. Шесть тактических фаз `✅ Done`, строки INDEX совпадают с заголовками фаз, обе новые единицы проиндексированы в каталоге с ролью, `dev/CHANGELOG.md` несёт строки на каждое логическое изменение.

§8 - EXEMPT: спека сама объявляет «Без изменений в docs/FEATURES», поэтому трёхъязычная проверка не применяется.

### Manual / on-device

- [x] Воспроизведение и редактирование в обоих хостах после переноса зависимостей - verified on-device 2026-08-14 (SM-G996U1, Android 15; PASS/FAIL/SKIPPED 10/0/0, ноль ошибок приложения, все три пробы уровня `D`; риск §7 снят: три видео проиграны подряд с `onRenderedFirstFrame` и `Playback ready`).

---

## Revision History

- **2026-08-14** - by `/spec-test-device` (SM-G996U1 / Galaxy S21+, device RFCR110NBQJ, Android 15 / SDK 35)
  - Сценарий и разбор лога: [`S1637_activity-logic-debt-player-hosts/device-run-2026-08-14.md`](S1637_activity-logic-debt-player-hosts/device-run-2026-08-14.md) · PASS/FAIL/SKIPPED 10/0/0 · ошибок приложения в логе: 0
  - Все три пробы сработали на уровне `D`: `PlayerManagerInitializer` (кластер из `ImageEditFactory`), `PlayerViewerFactory` (позиция и трек-предпочтение из `PlayerHostFactory`), `PhotoVideoStandaloneActivity` (view manager из `StandaloneHostFactory`). Ни одного `FATAL EXCEPTION`, `MissingBinding` или обращения к неинициализированному `lateinit` - то есть ровно тех отказов, которыми проявилась бы сломанная перепроводка.
  - Риск §7 (регресс воспроизведения на реальном устройстве) снят замером: три видео проиграны подряд, `onRenderedFirstFrame` и `Playback ready` в логе.
