# Спецификация (compact bugfix): S1682 - Телефон рисует успех синхронизации до ответа часов

**Ticket:** S1682
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-15
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-15

**Текст:**

Split out of S1681 during the 2026-08-15 device session. The owner's original report was "телефон показывает галочку ок - часы ждут прихода потом выпадают по таймауту". S1681 turned out to be the delivery layer being dead (mismatched `applicationId`) and is fixed. This ticket carries the second, independent half: even with delivery working, the phone's success indicator does not mean the watch received anything.

**Захвачено во время:** S1681

---

## 1. Проблема / симптом

`WearSyncViewModel.startPush` sets `WearSyncUiState.Success` inside the use case's `onSuccess`, guarded by `if (_uiState.value is Sending)`. The inline comment states the intent: success is meant to arrive over the ack flow, and this is a fallback "if ack not received".

The fallback cannot lose the race. `SendResourcesToWatchUseCase` returns as soon as `putDataItem` hands the bytes to Play Services, which is necessarily before any watch ack can travel back. So:

- the state is already `Success` by the time an ack arrives, and the ack collector in `init` tests `current is WearSyncUiState.Sending` before acting - that branch is therefore **unreachable in practice**, and with it the feature's only genuine delivery confirmation;
- the green check mark reports "Play Services accepted the bytes", never "the watch applied them".

Why this matters more now, not less: before S1681 the check mark was wrong because nothing was ever delivered. After S1681 delivery works and acks demonstrably arrive - the phone log of the 2026-08-15 verification shows `Watch ack received: {"added":0,"updated":0}` - so a correct indicator is now achievable for the first time, and the dead branch is the only thing standing between the user and a truthful one.

Note the verified round trip used the watch-initiated path (`PhoneWearListenerService` -> `SendResourcesToWatchUseCase`), which never enters `startPush`, so this defect was not exercised by that test and remains unproven on device. Reproduce it from the phone's own sync screen.

---

## 2. Корневая причина

§1 подтверждается по коду, дополнений не потребовалось. `startPush` объявляет `Success` внутри `onSuccess` сценария отправки, а тот возвращается сразу после передачи байтов в Play Services. Гонка не может быть проиграна: локальный возврат всегда быстрее сетевого путешествия туда и обратно. Сборщик ack проверяет `current is Sending` и к моменту прихода ответа видит уже `Success`, поэтому его ветка недостижима на практике.

Установлено дополнительно при разборе, в §1 этого не было:

- **Ветка ack читает только одно число из двух.** Ответ часов несёт `added` и `updated`, а разбор берёт `added`. Синхронизация, обновившая все существующие ресурсы и не добавившая ни одного, показала бы «0» - то есть даже когда ветка наконец заработает, она сообщит неверную величину. Чинится тем же заходом, иначе первый же настоящий ack соврёт по-новому.

---

## 3. Исправление

`startPush` больше не объявляет успех. После того как Play Services приняли байты, экран остаётся в состоянии отправки, и одновременно запускается таймер ожидания ответа:

- пришёл ack - таймер снимается, показывается успех с числом применённых ресурсов (`added` + `updated`);
- ответа нет 15 секунд - показывается ошибка «часы не подтвердили приём», с уже существующими кнопками «повторить» и «отмена».

Изменённый файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt`. Новая строка `wear_sync_no_ack` в EN/RU/UK.

Новых состояний интерфейса и новых веток в диалоге не появилось: `Sending` и `Error` уже нарисованы и уже несут нужный смысл. Это осознанно - вариант с отдельным состоянием «отправлено, ждём часы» потребовал бы новой ветки диалога и новых строк ради сообщения, которое пульсирующая анимация отправки уже передаёт.

Проверка компиляцией: `.\a.ps1 fk` - `BUILD SUCCESSFUL`, `Fast check passed`, exit 0.

### 3.3 Owner inputs (Approval gate)

- **Решение по открытому вопросу принято автором спеки, не владельцем.** Владелец 2026-08-15 отказался отвечать на вопросы по этой партии тикетов и попросил выполнять. Выбран вариант «ждать ответа, затем честный итог или честная ошибка» - тот, который §1 этого же тикета обосновывает как единственный, где индикатор перестаёт врать. Значение таймаута (15 секунд) выбрано автором и не является решением владельца. Если владелец предпочтёт второй вариант из исходной формулировки - два раздельных состояния «отправлено» и «получено часами» - это надстройка над текущей правкой, а не её переделка.
- **Flavor scope:** телефонное приложение; экран синхронизации с часами живёт в общем коде, флейворных различий у него нет.
- **Localization:** одна новая строка `wear_sync_no_ack`, заведена в EN/RU/UK; остальные десять локалей - в общем предрелизном цикле (S1627).
- **Validation level:** проверка на устройстве по phone-initiated пути, то есть с кнопки отправки на телефоне; watch-initiated путь этот дефект не задевает.

- **Related tickets:** S1681 (bugfix-wear-sources-push-never-arrives) - родительская находка, доставка починена там; без неё этот тикет нерешаем. S1631 - становится проверяемым после S1681.

---

## 4. Проверка

Сценарий начинается с кнопки отправки на телефоне, не с часов - watch-initiated путь в `startPush` не заходит и дефект не воспроизводит.

1. Часы рядом и в сети. На телефоне нажать отправку на часы. Ожидается: анимация отправки держится до ответа часов, затем галочка с числом применённых ресурсов. В логе телефона - `S1682: success now driven by the watch ack, applied=<N>`.
2. Число на экране должно совпадать с суммой `added` и `updated` из строки лога `Watch ack received`.
3. Часы выключить или увести из зоны. Нажать отправку. Ожидается: через 15 секунд крестик и текст «часы не подтвердили приём», кнопки «повторить» и «отмена» работают. В логе - `S1682: no watch ack within 15000 ms`.
4. Ключевой негативный признак по всему сценарию: галочка не должна появляться раньше ответа часов ни разу.

## 5. Ход проверки 2026-08-15

Первая попытка проверки в тот же день **не считается**: на телефоне стояла сборка от 04:32, то есть собранная до этой правки. Признак, по которому это установлено, а не предположено: в логе телефона есть `PhoneWearListenerService: Watch ack received: {"added":9,"updated":10}`, но нет ни одной строки `S1682:`, при том что отладочный уровень в этом же захвате пишется (513 строк уровня D). Метка живёт в изменённом коде, значит изменённый код на телефоне не исполнялся.

Полезное, что эта попытка всё же дала:

- **Ответ часов приходит меньше чем за секунду** при живой паре телефон-часы. Таймаут в 15 секунд выбран с запасом на спящие часы и не мешает быстрому случаю.
- **Числа в ответе ненулевые и различны**: `added=9`, `updated=10`. То есть разбор только поля `added` показал бы 9 вместо 19 - дефект из §2 воспроизводится на реальных данных, а не только теоретически.
- Путь до экрана: настройки открываются с `FLAG_SECURE`, снимок чёрный, поэтому ориентироваться надо по дереву элементов; кнопка `btnWearCompanion` лежит в разделе «Общие» ниже видимой области, и быстрее всего до неё добираться поиском по настройкам.
- В альбомной ориентации лист «Wear-компаньон» почти целиком уходит за нижний край экрана - видно только заголовок. Проверять надо в портрете. Заведено отдельно как S1691.

После установки сборки `v2.60.8151.455` все четыре пункта §4 пройдены на телефоне владельца.

1. **Часы на связи.** `15:01:53.838 Watch ack received: {"added":9,"updated":10}` -> `15:01:53.841 S1682: success now driven by the watch ack, applied=19`. Ветка ack, которая по §1 была недостижима, теперь ведёт показ успеха. Лог: `temp/S1682/ack-driven-success.log`.
2. **Число верное.** `applied=19` = 9 + 10. Старый разбор показал бы 9.
3. **Часы недоступны.** Пакет приложения на часах отключён (`pm disable-user`), отправка нажата в 15:02:23, в 15:02:38.088 - `S1682: no watch ack within 15000 ms`, ровно через 15 секунд. На экране красный крестик, текст «Часы не подтвердили приём. Возможно, они выключены или вне зоны.», кнопки «Повторить» и «Отмена». Снимок: `temp/S1682/no-ack-error-after-15s.png`, лог: `temp/S1682/no-ack-timeout.log`. Пакет на часах возвращён в enabled сразу после проверки.
4. **Галочка ни разу не появилась раньше ответа.** В отрицательном сценарии на шестой секунде успеха нет, а на девятнадцатой вместо него ошибка.

Побочно: `am force-stop` на часах для этой проверки **не годится** - Play Services поднимают слушателя Data Layer заново, и ack всё равно приходит. Отключать надо пакет.

Отладочные метки удалены. Строка таймаута заменена на постоянную `Timber.w` без идентификатора тикета - условие «часы не ответили» диагностически ценно и после закрытия. Компиляция после удаления: `.\a.ps1 fk` - `BUILD SUCCESSFUL in 44s`, exit 0.
