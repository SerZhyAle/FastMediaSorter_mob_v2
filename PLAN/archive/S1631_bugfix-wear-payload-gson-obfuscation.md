# Спецификация (compact bugfix): S1631 - Телефон шлёт часам Gson-payload с обфусцированными именами полей

**Ticket:** S1631
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-14
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14

**Текст:**

Найдено при разборе краша S1630 (обфускация полей Gson-модели). Тот же корневой класс дефекта, но на контракте телефон <-> часы.

Модели конверта и полезной нагрузки Wear существуют в двух копиях - в `app_v2` (`com.sza.fastmediasorter.domain.model.*`) и в `wear` (`com.sza.fastmediasorter.wear.domain.model.*`), тексты классов идентичны. Обмен идёт через Gson.

Сторона часов защищена: `wear/proguard-rules.pro` держит `-keep class com.sza.fastmediasorter.wear.domain.model.** { *; }` и `wear.data.**`, поэтому в release имена полей настоящие.

Сторона телефона не защищена ничем. Проверено по `mapping.txt` релизной сборки standardRelease:

```
com.sza.fastmediasorter.domain.model.WearEventEnvelope -> deg:
    java.lang.String eventType -> a
    int schemaVersion -> b
    long sentAt -> c
com.sza.fastmediasorter.domain.model.WearSettingsPayload -> keg:
    boolean audioEnabled -> a
    boolean videoEnabled -> b
com.sza.fastmediasorter.domain.model.WearSyncPayload -> neg:
    int version -> a
    long sentAt -> b
    java.lang.String phoneName -> c
com.sza.fastmediasorter.domain.model.WearFavoriteDeltaItem -> eeg:
    java.lang.String sourceId -> a
    java.lang.String filePath -> b
```

То есть release-телефон пишет `{"a":..,"b":..}`, а release-часы читают в поля `eventType`/`schemaVersion`/.., которых в JSON нет. Все поля остаются null/0/false. `schemaVersion` при этом тоже не доедет, то есть версионная защита конверта не сработает - несовместимость не будет распознана как несовместимость.

Пишущие точки на телефоне:
- `PushWearSettingsUseCase:18` - `gson.toJson(settings)`
- `SendPlaybackCommandUseCase:21,23` - команда + конверт
- `SendResourcesToWatchUseCase:84` - `gson.toJson(syncPayload)`
- `SendFavoritesDeltaUseCase` - дельта избранного

Читающая точка на часах: `WatchWearListenerService:73,74,86,87,100`.

Проявляется только в release-паре (debug не минифицируется, поэтому при отладке контракт цел) - ровно та причина, по которой дефект не виден в разработке.

Приоритет понижен до 45 против штатных 90 для bugfix: разработка Wear сейчас на паузе (S0552 BlockByOtherTask, S0902 BlockExternal), и тикет не должен обгонять активную очередь.

**Вложения:** нет

---

## 1. Проблема / симптом

В release-паре телефон пишет в Data Layer объект с ключами `a`, `b`, `c`, а часы читают его в поля с настоящими именами. Совпадений нет, поэтому на часах каждое поле остаётся `null`/`0`/`false`: настройки не применяются, список сетевых источников приходит пустым, команды плеера не распознаются. Конверт не спасает - `schemaVersion` тоже приходит под чужим ключом и читается как 0, то есть несовместимость не будет опознана как несовместимость и часы не смогут о ней сообщить.

В debug-паре контракт цел, потому что минификации нет - ровно поэтому дефект не виден при разработке. Эвиденс на уровне кода и mapping приведён в §0.

---

## 2. Корневая причина

Асимметрия keep-правил: `wear/proguard-rules.pro` держит модели обмена, `app_v2/proguard-rules.pro` - нет.

---

## 3. Исправление

`@SerializedName` на каждое поле восьми моделей контракта на стороне телефона: `WearEventEnvelope`, `WearSettingsPayload`, `WearSyncPayload`, `WearNetworkSourcePayload`, `WearFavoriteDeltaItem`, `WearFavoritesDeltaPayload`, а также `WearSourcesExportPayload` и `WearPlaybackStatePayload`.

Последние две в §0 не названы, но принадлежат тому же дефекту и добавлены после проверки направления обмена: их пишут часы, а читает телефон - `PhoneWearListenerService` (source set `wearGms`) разбирает их через `gson.fromJson`. Минифицирована здесь как раз читающая сторона, поэтому без закрепления ключей полностью ломается и обратное направление, а не только прямое. Аннотация выбрана вместо keep-правила по той же причине, что и в S1632: она задаёт имя ключа явно и переживает переименование свойства, а keep защищает только от обфускации. Механизм прочитан в `app_v2/proguard-rules.pro`: строки 135-137 держат `-keepclassmembers,allowobfuscation class * { @SerializedName <fields>; }` - поле не удаляется, но переименоваться может, поэтому ключ фиксирует именно значение аннотации, а её сохраняет `-keepattributes *Annotation*` на строке 129.

Имена ключей берутся равными сегодняшним именам свойств, потому что именно их читает сторона часов.

Расхождение копий проверено, а не предположено: наборы имён полей `WearEventEnvelope`, `WearSettingsPayload`, `WearFavoritesPayload` и `WearSyncPayload` в `app_v2` и в `wear` совпадают поле в поле. Единственное расхождение - watch-only класс `ImportResult` в файле `WearSyncPayload.kt` модуля `wear`, который в контракте не участвует.

Не входит в объём, но и не потеряно - оба пункта записаны здесь как открытые:

1. Контракту место в общем source set, а не в двух ручных копиях. Сегодня копии совпадают, но ничто этого не удерживает. **Статус:** Open. **Носитель:** `Carrier: S0552` - тикет возобновления разработки Wear, где переезд контракта уместен.
2. Тест, ловящий расхождение имён полей между модулями до релиза. **Статус:** Open. Завести его сейчас нельзя: юнит-набор не компилируется (S1635), поэтому новый тест был бы ненаблюдаемым. **Носитель:** `Carrier: S0552`.
3. Команда плеера уходит на часы как `gson.toJson(command.name)`, то есть по имени константы перечисления `WearPlaybackCommand`, а не по имени поля - `@SerializedName` его не закрывает. Держит ли R8 имена констант перечисления в этой сборке, по коду не определить. **Нужно выяснить:** найти `WearPlaybackCommand` в `mapping.txt` релизной сборки; если константы переименованы, нужно keep-правило именно на перечисление. **Статус:** Open. **Носитель:** `Carrier: S0552`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1630 (тот же класс дефекта, кэш файловых списков), S0737, S0719 (keep-правила для других Gson-моделей), S0552 / S0902 (состояние разработки Wear)

---

## 4. Проверка

Механическая часть, без устройства:

- Сборка зелёная, `@SerializedName` стоит на каждом поле восьми моделей, значения аннотаций совпадают с именами свойств на стороне часов.
- Аннотации доживают до рантайма благодаря `-keepattributes *Annotation*`; отдельной keep-записи на эти классы не требуется.

Устройство - настоящее доказательство, дефект существует только в release-паре:

- Release-телефон и release-часы: пуш настроек Wear доезжает с непустыми полями, список сетевых источников приходит непустым, команда плеера распознаётся.
- В логе часов `schemaVersion` конверта равен 1, а не 0.
