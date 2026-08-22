# Спецификация (compact bugfix): S1629 - мёртвый строковый ключ network_monitor_local_ip_label

**Ticket:** S1629
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-14
**Tier:** 1 - Quick Win (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14

**Текст:**

Обнаружено автоматически при прогоне `.\a.ps1 fg` в ходе S1627 (фаза 04). Вербатим вывод гейта:

```
assert-unreferenced-strings: declared=2849 unreferenced=8 baseline=7 new=1 slack=0
  NEW  network_monitor_local_ip_label             <string> nothing under app_v2/src references it
Write-Error: assert-unreferenced-strings: FAIL - 1 new unreferenced name(s) in app_v2/strings.xml. Reference them, delete them with 'set-android-string.ps1 -Action remove', or add them to scripts/quality/assert-unreferenced-strings-baseline.txt with a reason.
```

Значение ключа в `app_v2/src/main/res/values/strings.xml`: `Local IP`.

К задаче S1627 отношения не имеет: та не добавляла и не удаляла ни одного ключа, кроме временного `s1627_probe_key`, снятого сразу после замера. Дедуп по каталогу выполнен - `search.ps1` по `network_monitor`, `unreferenced`, `dead string` не вернул ни одной записи.

---

## 1. Проблема / симптом

Ключ `network_monitor_local_ip_label` объявлен в `app_v2/src/main/res/values/strings.xml`, но ни один файл под `app_v2/src` на него не ссылается - ни в одном source set, включая flavor-, feature- и test-наборы (гейт сканирует их все, см. `docs/DEV_OPS.md`, "Unreferenced string keys - S1568").

Гейт `assert-unreferenced-strings` работает по allow-list, а не по счётчику, и slack равен нулю - поэтому единственный новый ключ роняет весь батч `.\a.ps1 fg` для любого агента и любого тикета, пока не будет разрешён.

Приоритет 90 именно поэтому: сам дефект мелкий, но он блокирует общий гейт.

---

## 2. Корневая причина

Ключ - сирота без владельца, а не заготовка. Разобрано измерением, а не выбором из трёх версий:

- Экран сетевого монитора уже написан и живёт: соседние ключи того же семейства (`network_monitor_section_wifi`, `network_monitor_status_available` и прочие) ссылаются из `ui/networkmonitor/**` и из `res/layout/fragment_network_monitor_*.xml`. То есть версия «экран ещё не написан» отпадает.
- Ни `network_monitor_local_ip_label`, ни любое другое вхождение `local_ip` не встречается нигде под `app_v2/src` вне файлов локализации - ни в Kotlin, ни в разметке, ни в одном flavor- или test-наборе.
- S1617 (network-monitor-readability-and-diagnostics, Approved) владеет этим экраном, и строка про локальный IP была бы уместна в его диагностической половине, но его спека такой строки не называет. Значит, ключ не «положен заранее под S1617», а именно осиротел.

Побочное наблюдение: ключ успел разъехаться по всем тринадцати локалям - мёртвую строку перевели вместе с живыми.

---

## 3. Исправление

Удалить ключ во всех тринадцати локалях через `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action remove -Key network_monitor_local_ip_label`.

Почему удаление, а не запись в baseline с причиной: у ключа нет владельца, которого можно было бы в этой причине назвать - S1617 его не заявляет. Rule 20 (dead-weight hygiene) требует удалять осиротевшие строковые ключи, а не консервировать их. Обратная стоимость невелика: если S1617 всё же захочет строку про локальный IP, ключ заводится заново и попадает в ближайший бандл на перевод, который владелец всё равно делает пакетом на релиз.

Дубликат: S1624 (`orphaned-network-monitor-string-key`) описывает ровно этот же ключ и ровно тот же вывод гейта, заведён днём раньше при S1329 фаза 00. Оба дедуп-поиска промахнулись друг мимо друга, потому что искали по имени ключа с подчёркиваниями (`network_monitor`), а слаг тикета написан через дефисы (`network-monitor`). S1624 архивирован как дубликат в пользу этого тикета - здесь compact-форма и заполненное расследование.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1568 (гейт мёртвых ключей и его baseline), S1627 (обнаружено при прогоне гейтов)

---

## 4. Проверка

Обе стороны наблюдения сняты в этой сессии, а не взяты из §0.

**До.** `scripts/quality/assert-fast-gates.ps1`, 2026-08-14 13:20 - `assert-unreferenced-strings` FAIL, батч красный:

```
assert-unreferenced-strings: declared=2851 unreferenced=8 baseline=7 new=1 slack=0
  NEW  network_monitor_local_ip_label             <string> nothing under app_v2/src references it
```

**После.** Ключ снят во всех тринадцати локалях (`set-android-string.ps1 -Action remove` отчитался по каждой), затем:

```
assert-unreferenced-strings: declared=2850 unreferenced=7 baseline=7 new=0 slack=0
assert-unreferenced-strings: PASS - every declared name is referenced or explicitly baselined.
```

exit 0. Паритет локалей после удаления проверен отдельно: `check_strings_localized.ps1 -KeyPrefix network_monitor` - `all 164 key(s) present in en/ru/uk`, разрывов нет.

Пользовательского эффекта у изменения нет: строка не была ни на одном экране - именно поэтому гейт её и нашёл.
