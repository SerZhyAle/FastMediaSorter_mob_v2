# Стратегическая спецификация: S1623 - Красный parity-тест разрешений на standard

**Ticket:** S1623
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-13
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при работе над S1585

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-13

**Симптом:** полный прогон юнит-тестов (`.\a.ps1 fu`) падает на `standard`: 3546 тестов, 1 упавший.

**Упавший тест:**

`com.sza.fastmediasorter.data.permissions.PermissionRegistryManifestParityTest ::
every registry row names a declared permission or a named exemption`

**Сообщение теста (дословно):**

```
java.lang.AssertionError: Registry rows naming a permission this variant does not declare:
[android.permission.ACTIVITY_RECOGNITION]. The row would offer to grant something the build
cannot hold - fix the gate, or add it to PermissionManifestExemptions.rowWithoutDeclaration
with the reason it is held another way.
```

**Собранные доказательства:**

- Строка реестра `activity_recognition` в `PermissionRegistryRepositoryImpl.kt` (около строки 295)
  закрыта гейтом `buildGates = setOf("SUPPORT_LAUNCHER")`.
- `SUPPORT_LAUNCHER` включён и в `standard`, и в `noLegal` (`docs/FLAVOR_MATRIX.md`).
- S1614 перенёс само разрешение из `src/launcherEnabled/AndroidManifest.xml` в
  `src/noLegal/AndroidManifest.xml`, потому что Play считает его health-разрешением.
- Итог: у `standard` гейт строки истинен, а разрешение в слитом манифесте отсутствует - именно то
  расхождение, которое ловит тест.
- Комментарий над строкой реестра всё ещё описывает прежнее положение вещей: он говорит, что без
  `buildGates` строка протекла бы в `lite/photos/legacy`, и не учитывает, что после S1614 гейт
  перестал совпадать с местом объявления.

**Почему это отдельный тикет, а не часть S1614:** S1614 возвращает разрешение в store-флейворы и
заблокирован внешним условием - заполнением анкеты Play Console «App content → Health apps». Пока это
условие не выполнено, полный прогон тестов остаётся красным у **любого** тикета, а не только у
связанных с шагомером. Здесь нужен либо корректный гейт строки, либо именованное исключение.

**Обнаружено при:** S1585 (навигационный ярлык). Файлы S1585 к падению отношения не имеют - изменялись
`domain/map/`, `data/map/`, `core/di/MapModule.kt` и приём шары лаунчера; реестр разрешений и манифесты
не затрагивались. Собственные тесты S1585 прошли: 5/5 и 9/9.

---

## 1. Проблема

- Строка реестра `activity_recognition` закрыта гейтом `SUPPORT_LAUNCHER`, который истинен и в
  `standard`, и в `noLegal`.
- Само разрешение `ACTIVITY_RECOGNITION` после S1614 объявлено только в
  `app_v2/src/noLegal/AndroidManifest.xml`, а этот манифест подмешивается лишь во флейвор `noLegal`.
- Из-за этого в `standard` строка есть, а объявления нет - и `PermissionRegistryManifestParityTest`
  падает: экран разрешений предложил бы выдать то, чего сборка не может держать.
- Красным становится весь прогон `.\a.ps1 fu` на `standard`, то есть любой тикет, а не только
  связанные с шагомером.
- Комментарий над строкой реестра описывает положение дел до S1614 и потому вводит в заблуждение
  следующего читателя.

### 1.1 Выбранное решение

- Гейт строки меняется на `IS_NO_LEGAL_FLAVOR` - тот же предикат, что уже закрывает соседнюю строку
  `request_install_packages`, объявленную в том же самом `src/noLegal/AndroidManifest.xml`.
- Новый флаг `DECLARES_ACTIVITY_RECOGNITION` не заводится: его значение было бы дословно
  `flavorName == "noLegal"`, то есть второе имя для уже существующего предиката, а второе имя для
  одного факта - это и есть тот дрейф, который гейт должен предотвращать.
- Переход S1614 в обратную сторону защищён самим тестом: как только разрешение вернётся в
  `src/launcherEnabled`, `standard` начнёт объявлять его без строки, и упадёт уже первое направление
  теста (`every declared permission is a registry row or a named exemption`).

---

## 3. Пожелания и ограничения

### 3.1 Ограничения

- Экран разрешений в `standard` не должен показывать строку шагомера - сборка не может держать это
  разрешение, а строка предлагала бы его выдать.
- В `noLegal` поведение строки не меняется.
- Исключение в `PermissionManifestExemptions.rowWithoutDeclaration` не подходит: разрешение не
  «держится иначе», оно просто отсутствует в сборке, а исключение скрыло бы расхождение вместо
  того, чтобы его устранить.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1614 (Draft) - вернёт разрешение в store-флейворы после анкеты Play; данный
  тикет чинит красный прогон до того. S1585 - контекст обнаружения.
- **Flavor scope:** затрагивает `standard` (строка исчезает) и не меняет `noLegal`; `lite`,
  `photos`, `legacy`, `vr` строку не показывали и раньше.

---

## 4. Фазы

### Phase 01 - Gate the steps row where the permission is declared

**Objective:** the `activity_recognition` registry row appears only in the variant whose merged
manifest declares `ACTIVITY_RECOGNITION`, and its comment states the post-S1614 arrangement.

#### Step 01.1 - Move the row onto the noLegal gate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`

**Prompt for developer:**

> In the `activity_recognition` entry, replace `buildGates = setOf("SUPPORT_LAUNCHER")` with
> `buildGates = setOf("IS_NO_LEGAL_FLAVOR")`. Rewrite the comment above the entry to say that S1614
> moved the declaration into `src/noLegal/AndroidManifest.xml`, that the gate now mirrors that file
> exactly as the `request_install_packages` row above does, and that returning the declaration to
> `src/launcherEnabled` means returning this gate with it.

**Why:**

Without the change the row is live in `standard` while the merged manifest of `standard` carries no
`ACTIVITY_RECOGNITION`, so the permissions screen offers to grant something the build cannot hold and
`PermissionRegistryManifestParityTest` fails the whole unit run on that flavor.

**Verification:**

- `Grep` - `IS_NO_LEGAL_FLAVOR` appears three times in the file: the `request_install_packages` row,
  this row, and the `buildGateValues` resolver map.
- `Grep` - no `SUPPORT_LAUNCHER` gate remains within the `activity_recognition` entry.
- `Grep` - the comment above the entry names S1614.

**Status:** `[x]` done

#### Step 01.2 - Prove the parity test is green on standard

**Files:** none - verification only

**Prompt for developer:**

> Run `PermissionRegistryManifestParityTest` and `PermissionRegistryRepositoryImplTest` on the
> `standard` debug variant and read the result. Both must pass; the second one guards that every gate
> string still resolves to a mapped `BuildConfig` field.

**Why:**

The ticket exists because the full unit run is red on `standard`, so only a run of the failing test on
that variant proves the fix, and the mapping test is what catches a gate string with no value behind
it - the failure mode that silently disables a permission on a minified build.

**Verification:**

- Test report - `PermissionRegistryManifestParityTest` 3/3 passed.
- Test report - `PermissionRegistryRepositoryImplTest` all passed.

**Status:** `[x]` done

---

## 5. Состояние реализации

- `check-standard-fast.ps1 -Mode Unit -Flavor Standard -Tests "com.sza.fastmediasorter.data.permissions.*"`
  - exit 0, 2026-08-14 17:03.
- `PermissionRegistryManifestParityTest` - tests=3, failures=0.
- `PermissionRegistryRepositoryImplTest` - tests=9, failures=0.
- `PermissionRequestMarkerRepositoryImplTest` - tests=3, failures=0 (тот же прогон, соседний класс).
- `compileStandardDebugKotlin` в том же прогоне прошёл - отдельная сборка не нужна.

---

## Last Audit

**Date:** 2026-08-14
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 14 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- Не требуется. Экран разрешений рендерится из `getEntries()`, а `PermissionRegistryManifestParityTest`
  сверяет именно этот набор со слитым манифестом варианта, под которым идёт прогон - устройство
  повторило бы то же утверждение более слабым способом.

### Проверенные инварианты

- `IS_NO_LEGAL_FLAVOR` объявлен `true` только в блоке `create("noLegal")`, дефолт `false` покрывает
  `standard`, `lite`, `photos`, `legacy`, `vr` - ни один вариант не получает строку без объявления.
- Исключение в `PermissionManifestExemptions` не добавлялось: расхождение устранено, а не скрыто.
- Тегов `Timber.d("S1623:` в дереве нет - статус не `BlockNeedUserTest`.
- EXEMPT: `docs/FEATURES*` не трогается по тикету - витрина наполняется только `/skill-release`.
