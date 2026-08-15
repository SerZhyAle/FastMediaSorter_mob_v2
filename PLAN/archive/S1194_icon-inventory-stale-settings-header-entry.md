# S1194 - Реестр иконок отстал от исходников, гейт постоянно в advisory-провале

**Ticket:** S1194
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-25
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - обнаружено при закрытии S1193, 2026-07-25

<!-- auto-approved by /spec-all - 2026-08-14 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-25 (побочная находка при закрытии S1193; к задаче отношения не имеет)

**Симптом:** гейт `icon-inventory-sync` проваливается на каждом прогоне `scripts/post-change.ps1`, независимо от того, какой файл менялся. Провал не относится к текущему изменению, поэтому фасад понижает его до advisory и закрытие не блокируется - но выводится он при каждом закрытии.

**Дословный вывод гейта (2026-07-25, воспроизведён трижды подряд на разных файлах):**

```text
icon-inventory-sync: inventory-vs-source freshness skipped (pass -IncludeExportTest / enforced in CI)
assert-icon-inventory-sync: FAIL (1 issue(s))
  [settings-source-fresh] inventory missing source entry
  'settings-header|headerAppData|settings_category_app_data|ic_cloud_upload|vector|true'
  - regenerate docs/icons/icon-inventory.json via IconInventoryExportTest generate mode
```

**Что это означает:** заголовок настроек `headerAppData` (строка `settings_category_app_data`, иконка `ic_cloud_upload`) существует в исходниках, но отсутствует в `docs/icons/icon-inventory.json`. Реестр иконок отстал от кода и не был перегенерирован после добавления этого заголовка.

**Почему это не чинится на месте:** регенерация требует прогона `IconInventoryExportTest` в режиме generate, то есть gradle-задачи, а не правки файла руками. Это отдельная работа со своей проверкой.

**Вложения:** нет.

---

## 1. Намерение

Вернуть гейту `icon-inventory-sync` смысл: его вывод должен означать «расхождений нет», а не быть фоновым шумом при каждом закрытии. Расхождение реестра с исходниками устраняется, а подсказка о том, как его устранить, делается исполнимой - сейчас она называет механизм (`IconInventoryExportTest` в режиме generate), а не команду, и буквальное следование ей приводит к голому вызову `gradlew` без `BUILD.LOCK`, что запрещено правилом 23.

---

## 2. Состояние на 2026-08-14 (перепроверено, не по памяти)

- Запись `settings-header|headerAppData|settings_category_app_data|ic_cloud_upload|vector|true` присутствует в `docs/icons/icon-inventory.json`. Реестр насчитывает 165 записей.
- Дешёвая часть гейта зелёная: `assert-icon-inventory-sync: PASS - 85 vector svg(s) present, no orphans, legend fresh, locales in parity.`
- Тяжёлая часть тоже зелёная: тот же скрипт с `-IncludeExportTest` прогоняет `IconInventoryExportTest` в режиме проверки и возвращает PASS, то есть коммит реестра совпадает с живыми реестрами приложения целиком, а не только в подмножестве настроек.
- Гейт больше не срабатывает на каждом закрытии: `post-change.ps1` включает его только когда в изменяемом наборе есть `docs/icons/**`, `docs/ICON_LEGEND*`, `fragment_settings_*.xml` или `values*/strings*.xml`.
- Со времени захвата в скрипте появился флаг `-RegenerateInventory`, который выполняет регенерацию под `BUILD.LOCK`.

Остаётся ровно одна невыполненная часть замысла - сообщения о провале по-прежнему называют механизм, а не команду.

---

## 3. Объём

- Заменить в трёх сообщениях о провале `assert-icon-inventory-sync.ps1` указание на «IconInventoryExportTest generate mode» и на `-Dicon.inventory.generate=true` на исполнимую команду с флагом `-RegenerateInventory`.
- Ничего в самом реестре, легенде и SVG не трогать: они доказанно свежие.

**Вне объёма:** сообщение JUnit-ассерта внутри `IconInventoryExportTest.kt`. Оно называет системное свойство `-Dicon.inventory.generate=true`, и это верно для своей аудитории - того, кто уже запустил тест из IDE или из gradle. Читателем при закрытии тикета является вывод PowerShell-гейта, и правило 23 адресовано именно ему.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1193 (тикет, при закрытии которого симптом захвачен), S0815 и S0939 (тикеты, создавшие сам гейт и его маршрутизацию)

---

## 4. Проверка

- `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1` - PASS.
- `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1 -IncludeExportTest` - PASS (доказывает, что правка сообщений не сломала разбор и что реестр по-прежнему свеж).
- `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Script` - PASS.

---

## 5. Открытые вопросы / Research items

1. **Должен ли гейт блокировать закрытие, когда расхождение касается изменяемого файла, и оставаться advisory только для чужого долга.**
   **Статус:** Resolved. Он уже так работает, и с двух сторон сразу. Во-первых, он вообще не запускается, если в изменяемом наборе нет иконочной документации или исходников настроек (`post-change.ps1`, условие `$runsIconInventoryGate`). Во-вторых, advisory он только под `-ScopeToFile`, то есть при закрытии на грязном дереве; без этого флага - в релизе и CI - он фатальный (`$ratchetRunner`). Захваченный симптом описывает состояние до этой маршрутизации плюс запись, которую просто долго никто не чинил.

2. **Есть ли смысл регенерировать реестр из post-change автоматически, раз она механическая.**
   **Статус:** Resolved. Нет. Регенерация - это gradle с Robolectric и захват `BUILD.LOCK`; она не укладывается в бюджет быстрого пути закрытия и превратила бы каждое закрытие в сборку. Ровно эту стоимость и снимает `-RegenerateInventory`: одна команда, взятая осознанно, когда гейт сообщил о расхождении.

3. **Полная инвентаризация расхождений между реестром и исходниками, а не только первая запись** (перенесено из «Отложенного исследования»).
   **Статус:** Resolved. Расхождений ноль. Дешёвая проверка сравнивает подмножество настроек множествами и сообщила бы и о недостающих, и о лишних записях; тяжёлая сравнивает весь сериализованный файл байт в байт с пересканированными реестрами. Обе зелёные 2026-08-14.

4. **Когда и в каком тикете появился заголовок `headerAppData` и почему реестр не обновили тогда же** (перенесено из «Отложенного исследования»).
   **Статус:** Resolved как не подлежащий выяснению и не влияющий на решение. Ответ живёт только в истории git, которая в этом репозитории не является источником состояния, а на выбор действия он не влияет: маршрутизация гейта и `-RegenerateInventory` закрывают повторение независимо от того, какой тикет пропустил регенерацию.

---

## 6. Approval gate

**Опасение при захвате:** не перезапишет ли регенерация ручные аннотации в `docs/icons/icon-annotations.json`.

**Снято.** Режим generate в `IconInventoryExportTest` пишет ровно один файл - `docs/icons/icon-inventory.json` (`file.writeText(expected)`, где `file` - `inventoryFile()`). Файл аннотаций тестом не открывается вовсе; его читает только рендерер легенды `scripts/docs/render-icon-legend.ps1`, который сливает оба файла на выходе.

---

## Last Audit

**Дата:** 2026-08-14. **Вердикт:** Verified.

**Что изменено:** три сообщения о провале в `scripts/quality/assert-icon-inventory-sync.ps1` (два в проверке `settings-source-fresh`, одно в `inventory-fresh`) теперь называют исполнимую команду `-RegenerateInventory` вместо механизма «IconInventoryExportTest generate mode» и системного свойства `-Dicon.inventory.generate=true`.

**Доказательства.**

- Захваченный симптом воспроизведён на изолированной копии дерева. Репродьюсер: `PLAN/S1194_icon-inventory-stale-settings-header-entry/repro/build-fakeroot.ps1` - он копирует дерево и удаляет из копии реестра запись `headerAppData`, после чего гейт запускается с `-RepoRoot <напечатанный путь>`. Гейт вернул exit 1 и напечатал строку `[settings-source-fresh] inventory missing source entry 'settings-header|headerAppData|settings_category_app_data|ic_cloud_upload|vector|true' - run: pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1 -RegenerateInventory`. Это тот же дословный текст, что в §0, но с исполнимым остатком.
- Рабочее дерево после правки: `assert-icon-inventory-sync.ps1` - PASS, exit 0.
- Тяжёлая проверка: `assert-icon-inventory-sync.ps1 -IncludeExportTest` - PASS (прогон `IconInventoryExportTest` под `BUILD.LOCK`), то есть коммит реестра совпадает с живыми реестрами приложения целиком.
- Закрытие: `post-change.ps1 -ChangeType Mixed -ScopeToFile` - `post-change: PASS`, exit 0.

**Остаточных пробелов нет.** Реестр свеж, легенда свежа, локали в паритете, все вопросы §5 закрыты ответами.
