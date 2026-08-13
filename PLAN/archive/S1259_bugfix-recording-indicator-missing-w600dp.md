# Спецификация (compact bugfix): S1259 - Индикатор записи отсутствует в layout-w600dp, падение при старте записи в ландшафте

**Ticket:** S1259
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-28
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-28

**Текст:**

нет текста - находка агента при разборе S1258 (выравнивание иконок верхней панели в ландшафте). Обнаружено при сравнении вариантов `activity_main.xml`.

**Захвачено во время:** S1258

---

## 1. Проблема / симптом

`layout/activity_main.xml` и `layout-land/activity_main.xml` содержат `<include android:id="@+id/recordingIndicatorContainer" layout="@layout/view_recording_indicator" ..>` - общий индикатор записи из S0774. В `layout-w600dp/activity_main.xml` этого блока нет: он единственное отличие между `layout-land` и `layout-w600dp` (`diff` даёт ровно строки 667..679 и больше ничего).

`RecordingIndicatorOverlayManager` ищет вьюхи через `findViewById` с невырожденным типом:

```kotlin
private val root: View by lazy { activity.findViewById(R.id.recordingIndicatorRoot) }
```

Тип `View`, а не `View?`, поэтому `findViewById`, вернувший null, роняет инициализатор `lazy` в `NullPointerException` при первом `show()`.

По правилу разрешения ресурсов Android `layout-w600dp` выигрывает у `layout-land`, когда устройство одновременно широкое и в ландшафте, а это большинство телефонов и планшетов в ландшафте. То есть в ландшафте индикатор отсутствует всегда, где ширина >= 600dp.

Ожидаемое падение: старт записи экрана (`MainScreenRecordingManager`) либо быстрой голосовой записи (`MainVoiceCaptureManager`) на главном экране в ландшафте.

Не проверено на устройстве - находка статическая. Первым шагом расследования нужен репро: ландшафт на ширине >= 600dp, старт записи, логкат.

---

## 2. Корневая причина

Подтверждено по живому дереву 2026-07-28:

- `recordingIndicatorContainer` include: `layout/activity_main.xml:635` есть, `layout-land/activity_main.xml:671` есть, `layout-w600dp/activity_main.xml` - отсутствует (единственное расхождение id-наборов между land и w600dp по всем четырём общим файлам вариантов).
- `RecordingIndicatorOverlayManager` - пять non-null `by lazy { activity.findViewById(..) }`; platform-тип `T!` при null падает NPE в инициализаторе lazy при первом `show()`.
- Резолюция ресурсов: на широком ландшафте (телефон в повороте ~914dp, головное устройство 1024dp) `layout-w600dp` бьёт `layout-land`, то есть «ландшафтная» копия с индикатором в этих конфигурациях вообще не используется.

Причина пропуска - S0774 внёс include в `layout/` и `layout-land/`, третий вариант выпал; Правило 11 требует парности только layout/layout-land и молчит о w600dp - механической проверки не было.

Второй, более глубокий слой (найден девайс-проверкой 2026-07-28): `<include android:id="@+id/recordingIndicatorContainer" ..>` **переопределяет id корня включаемого layout** (штатное поведение Android), поэтому `recordingIndicatorRoot` не существует в рантайме НИ В ОДНОМ варианте activity_main - все три include несут id на теге. Лукап root в менеджере всегда возвращал null: оригинальный non-null код упал бы NPE в любой ориентации, не только на w600dp. Дефект дремал, потому что оба потребителя индикатора (диктофон и запись экрана) выключены настройками по умолчанию. Доказательство: на исправленной разметке (include в APK подтверждён aapt2, line=678) запись шла (зелёная точка микрофона, MediaFocusControl), а деградационный `Timber.w("Recording indicator missing..")` сработал - root=null при живом include.

---

## 3. Исправление

Выполнено 2026-07-28, четыре части:

- Разметка: include `recordingIndicatorContainer` добавлен в `layout-w600dp/activity_main.xml` перед закрывающим тегом CoordinatorLayout - байт-в-байт копия блока из `layout-land` (тот же комментарий S0774, top|end, отступы 8dp, gone).
- Лукап root: container-first - `findViewById(recordingIndicatorContainer) ?: findViewById(recordingIndicatorRoot)`; include-id переопределяет корневой id, так что рабочее имя - id тега include, а фолбэк покрывает будущего хоста без переопределения.
- Укрепление менеджера: все пять lookup'ов в `RecordingIndicatorOverlayManager` переведены на nullable + no-op деградацию (`show`/`updateTimer`/`setPaused`/`dismiss`); отсутствие индикатора в варианте разметки стоит индикатора, но не краша посреди записи. Однократный `Timber.w` при пропаже root.
- Механический гейт: `scripts/quality/assert-layout-variant-id-parity.ps1` - для каждого имени файла, существующего и в `res/layout-land`, и в `res/layout-w600dp`, наборы `android:id` обязаны совпадать; подключён в батч `assert-fast-gates.ps1` (`.\a.ps1 fg`). На момент включения все 4 общих файла в паритете; негативный тест (временное изъятие include) даёт FAIL/exit 1 с именем пропавшего id.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0774 - тикет, который завёл общий индикатор записи. S1258 - тикет, при работе над которым находка обнаружена.

---

## 4. Проверка

Минимум: ландшафт на ширине >= 600dp, старт записи, отсутствие `NullPointerException` в логкате, индикатор виден и его кнопки работают.

Выполнено 2026-07-28 на emulator-5588 (API 33, wm 1024x600@160 - рендерится именно layout-w600dp, сборка v2.60.7262.102 от 14:13):

- Диктофон включён в настройках (Management -> Voice recorder -> Enable microphone recording), пункт «Voice recording» появился в меню Programs, запись стартовала.
- Индикатор ВИДЕН: `recordingIndicatorContainer [798,56][1016,112]` top|end, внутри таймер (00:22 на кадре), пауза, стоп, отмена - `temp/S1259/indicator-live.png`, дамп `temp/S1259/ui-rec2.xml`.
- Кнопка Stop работает: пилюля погасла (контейнер исчез из дампа), `MainVoiceCaptureManager.save()` отработал (StrictMode-трейс пути сохранения в логкате).
- Логкат чист: ни `NullPointerException`, ни `FATAL`, ни деградационного `Recording indicator missing..` (до фикса лукапа root это предупреждение срабатывало - контрольная точка обеих причин).
- До включения контейнер-first лукапа тот же сценарий давал: запись идёт (зелёная точка микрофона), пилюли нет, `Timber.w` деградации в логкате - доказательство второго слоя §2.

Запись экрана отдельно не прогонялась: индикатор у неё тот же самый (общий `RecordingIndicatorOverlayManager`, те же id и тот же show()-путь S0774), специфика MediaProjection этим тикетом не затрагивалась.

expected: пилюля видна, кнопки работают, NPE нет | actual: всё так - PASS.

---

## Last Audit

**Дата:** 2026-07-28. **Вердикт:** Verified.

- Две причины: (1) include отсутствовал в layout-w600dp - вариант, который реально выигрывает на широком ландшафте; (2) include-id переопределяет корневой id включаемого layout, так что root-лукап по `recordingIndicatorRoot` был мёртв во всех вариантах - индикатор не показался бы нигде, а non-null lazy давал NPE-мину.
- Фикс: include добавлен в w600dp; лукап container-first с фолбэком; менеджер nullable + no-op деградация с одноразовым Timber.w; механический гейт id-паритета land/w600dp в `fg` (позитив и негатив проверены).
- Девайс-доказательство на emulator-5588 (см. §4): пилюля живая, стоп работает, логкат чист.
- P1-класс (краш посреди пользовательского сценария) закрыт; деградация переводит будущие пропуски вариантов из краша в потерю индикатора с warn-логом.
