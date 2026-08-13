# Спецификация (compact bugfix): S1254 - Маскировка секретов в settings dump молча ломается при обфускации полей AppSettings

**Ticket:** S1254
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-28
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-28

**Текст:**

Найдено при разборе пакета удалённых логов (`/newlog`, 5 сессий с устройства `ums512_1h10_Natv`, Android 14 / API 29, `standard release`).

Маскировка секретов в стартовом дампе настроек работает **по имени поля**:

```kotlin
// FastMediaSorterApp.kt:661-670
private fun formatSettingValue(fieldName: String, value: Any?): String {
    val nameLower = fieldName.lowercase(Locale.US)
    val isSecret = SECRET_FIELD_HINTS.any { it in nameLower }
    ...
```

Имя берётся из Java-рефлексии (`field.name`), то есть при обфускации R8 оно превращается в `l`, `k0`, `A2`. Ни одна подсказка из `SECRET_FIELD_HINTS` не совпадёт, и значение уходит в ветку обычного форматирования - открытым текстом.

Эвиденс из логов:

- `logs/fastmediasorter_20260724_102519.log:85` и `logs/fastmediasorter_20260724_175620.log:37` (версия `2.60.7070.937`, имена полей целы): `defaultPassword=****(7)` - пароль установлен, длина 7, маскирован.
- `logs/fastmediasorter_20260724_175804.log`, `logs/fastmediasorter_20250217_035912.log` (версия `2.60.7191.740`), `logs/fastmediasorter_20260728_101628.log` (версия `2.60.7221.704`) - имена обфусцированы, и во всём дампе **нет ни одной** строки `****(N)` или `<set, len=N>`. Маскировка не сработала ни разу.

Дамп уезжает наружу: `LogExportHelper` зипует все `fastmediasorter_*.log` и шарит их через Google Drive стороннему тестеру.

Симптом самих обфусцированных имён уже закрыт в S1187 (`Implemented`, 2026-07-27) - добавлено правило keep в `app_v2/proguard-rules.pro:14-21`. Но:

1. Правило keep уже один раз исчезало: S1187 §4 - «Root cause confirmed: `b4106200` removed the broad `domain.model` keep rule that had incidentally preserved `AppSettings` field names». Повторное удаление снова тихо снимет маскировку.
2. Механического гейта, который ловит это, нет - регрессия видна только в экспортированном логе с реального устройства, то есть у стороннего человека.
3. S1187 описывает симптом как «label lost its meaning» (нечитаемый дамп). Аспект утечки секрета там не зафиксирован, и приёмка S1187 его не проверяет.

Хрупкость двойная: правило keep может исчезнуть, и `SECRET_FIELD_HINTS` в принципе не может защитить то, чьё имя переименовано.

**Захвачено во время:** разбор `/newlog` от 2026-07-28.

---

## 1. Проблема / симптом

Пароль (и любое будущее поле `*token*` / `*secret*` / `*apiKey*`) попадает открытым текстом в диагностический лог, который штатно отправляется наружу, если R8 переименует поля `AppSettings`. Наблюдалось в release-сборках `2.60.7191.740` и `2.60.7221.704` на устройстве `ums512_1h10_Natv`. Механической защиты от повторения нет.

---

## 2. Корневая причина

Подтверждена по §0 без дополнений: маскировка привязана к рантайм-именам полей (`field.name` из Java-рефлексии), а имена - собственность R8. Любой путь, снимающий keep-правило S1187 (уже случалось: `b4106200`), молча превращает `defaultPassword=<set, len=N>` в открытый текст в логе, который штатно экспортируется наружу. Имя - негодный якорь для секрета; нужен якорь, переживающий переименование.

---

## 3. Исправление

Выполнено 2026-07-28, три слоя:

- **Rename-proof якорь**: новая аннотация `@Retention(RUNTIME) @Target(FIELD) annotation class SensitiveSetting` (`domain/model/SensitiveSetting.kt`); `defaultPassword` помечен `@field:SensitiveSetting`. `formatSettingValue` теперь принимает `Field` и маскирует при `isAnnotationPresent(SensitiveSetting::class.java)` ИЛИ по прежним name-хинтам (belt+braces). Проверка по идентичности класса - переименование полей и самой аннотации безразлично; `-keepattributes *Annotation*`/`RuntimeVisibleAnnotations` уже в proguard-rules.pro (строки 123/148), класс аннотации удерживается ссылкой на class literal.
- **Механический гейт**: `scripts/quality/assert-sensitive-settings-annotated.ps1` (в батче `fg`) держит все слои: каждый hint-матчащийся филд AppSettings обязан нести аннотацию; дамп обязан проверять `isAnnotationPresent`; proguard обязан содержать S1187 `-keepclassmembernames` и annotation-keepattributes. Позитив и негатив (временное снятие аннотации -> FAIL с именем поля) проверены.
- **Охват на будущее**: новые поля `*token*`/`*secret*`/`*apikey*`/`*credential*` без аннотации валят гейт - «забыл пометить» больше не тихий сценарий.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1187 (обфускация имён полей в дампе - `Implemented`, добавило правило keep, но без гейта и без учёта маскировки)

---

## 4. Проверка

Выполнено 2026-07-28, слоями по стоимости:

- Компиляция: standard debug собирается (db, exit 0).
- Гейт: позитив PASS; негатив (временное снятие `@field:SensitiveSetting`) - FAIL с именем поля; аннотация возвращена, снова PASS. Зарегистрирован в `fg`.
- Юнит-тест `SensitiveSettingAnnotationTest`: `AppSettings::class.java.getDeclaredField("defaultPassword").isAnnotationPresent(SensitiveSetting)` - пиновка `@field:`-таргета (тихая ошибка `@property:` скомпилировалась бы и не маскировала ничего).
- Девайс (5588, debug): дамп печатает `defaultPassword: <empty>` (значение в трансплантированном pb не расшифровалось чужим Keystore - хранение шифрованное, `decryptPassword` вернул пусто). Ветка непустого значения по name-пути не менялась и была доказана исходным эвиденсом §0 (`****(7)` в логе с целыми именами); annotation-путь доказан юнит-тестом выше.
- Инъекция сырого значения в pb невозможна by design (Keystore-шифрование) - это отдельный положительный вывод о хранении пароля.

R8-квитанция: `-keepattributes *Annotation*` (proguard-rules.pro:123) + retention RUNTIME; проверка по class identity переживает переименование и полей, и самой аннотации. Спот-чек на минифицированной сборке едет со следующим релизным циклом (изменение аддитивно к S1187-защите, не единственная линия обороны).

expected: маскировка дампа не зависит от имён полей; дрейф ловится гейтом | actual: аннотационный слой + гейт + тест на месте - PASS.

---

## Last Audit

**Дата:** 2026-07-28. **Вердикт:** Verified.

- Слои: `@field:SensitiveSetting` (rename-proof, class-identity), name-хинты (нулевой день для будущих полей), гейт `assert-sensitive-settings-annotated` в fg (аннотация+дамп-проверка+S1187 keep+keepattributes), юнит-пин `@field:`-таргета.
- Известный остаток: спот-чек дампа на минифицированном релизе - следующий релизный прогон.
