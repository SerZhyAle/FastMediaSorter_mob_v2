# 09 - Звонки и SMS прямо с домашней поверхности

**Связано с §6 спеки S0404 (новый независимый item 9).** Дата: 2026-06-15.

## Запрос

С домашней поверхности лаунчера инициировать телефонный звонок и отправку SMS - в идеале «прямо из лаунчера», без ухода в чужое приложение. Это укладывается в лестницу способностей эпика (Play-safe базовый уровень на всех флейворах + полный уровень только на `noLegal`).

## Уровень 1 - Play-safe передача в системный обработчик (все целевые флейворы)

- Звонок: `Intent(ACTION_DIAL, "tel:<number>")` открывает системный dialer с подставленным номером; пользователь сам нажимает «вызов». **Не требует** разрешения `CALL_PHONE`.
- SMS: `Intent(ACTION_SENDTO, "smsto:<number>")` + extra `sms_body` открывает приложение SMS по умолчанию с заполненными полями. **Не требует** разрешения `SEND_SMS`.
- Это рекомендованный Play-совместимый путь: плитки «быстрый набор» / «быстрое сообщение», которые передают управление системному обработчику. Работают на всех целевых флейворах.

## Уровень 2 - полностью внутри лаунчера (реально только noLegal)

- Прямой звонок без ухода (`ACTION_CALL` / `TelecomManager.placeCall`) требует `CALL_PHONE` (dangerous runtime).
- Прямая отправка `SmsManager.sendTextMessage(...)` требует `SEND_SMS` (dangerous, restricted).
- Показ на домашней поверхности недавних звонков / переписок требует `READ_CALL_LOG` / `READ_SMS` - это группы **Call Log** и **SMS**, ограниченные Play.
- **Политика Play:** приложение может запрашивать разрешения групп SMS / Call Log, только если оно зарегистрировано как обработчик по умолчанию соответствующей функции (default Dialer для Call Log, default SMS app для SMS) либо подпадает под узкое исключение; иначе Permissions Declaration отклоняется.
- Стать обработчиком по умолчанию: `RoleManager` `ROLE_DIALER` / `ROLE_SMS` (API 29+) либо legacy-intent смены SMS-приложения (`Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT`, API 19-28). Но это тяжёлое обязательство - надо реализовать полный контракт обработчика (приём входящих, экраны и т.д.), что несоразмерно для медиа-приложения.
- Вывод: полноценный встроенный dialer/мессенджер с чтением журналов - Play-несовместим на практике → только `noLegal`, как отдельная поздняя фаза по аналогии с kiosk-уровнем.

## Наличие телефонии - обязательный гейт

Планшеты, фоторамки, медиабоксы часто не имеют телефонии (`PackageManager.hasSystemFeature(FEATURE_TELEPHONY)` = false); даже `ACTION_DIAL` может не резолвиться. Любая плитка звонка/SMS обязана гейтиться по `FEATURE_TELEPHONY` + `resolveActivity` и скрываться при отсутствии (как профильные поверхности фоторамки/медиабокса в §3.2).

## Флейвор-сплит

- Контракт в `src/main/`.
- Play-safe handoff-реализация (`ACTION_DIAL`/`ACTION_SENDTO`) - в `src/main/` (без разрешений).
- Реализация с `CALL_PHONE`/`SEND_SMS`/`READ_*` и ролью обработчика - только в `src/noLegal/` (повторяет сплит ADR-3 реестра приложений; чувствительные разрешения - в `src/noLegal/AndroidManifest.xml`).
- Никаких `BuildConfig.IS_*`-гейтов в `src/main/`.

## Выводы для спеки

- Двухуровневая лестница: Уровень 1 - Play-safe handoff (`ACTION_DIAL`/`ACTION_SENDTO`, без разрешений) на всех целевых флейворах; Уровень 2 - встроенные dialer/SMS (`CALL_PHONE`/`SEND_SMS`/`READ_*` + роль default-handler) только на `noLegal`.
- Ограниченные `CALL_PHONE`/`SEND_SMS`/`READ_CALL_LOG`/`READ_SMS` гейтятся ролью обработчика по умолчанию по политике Play → нежизнеспособны на `standard`.
- Всё гейтить по `FEATURE_TELEPHONY` + `resolveActivity` (на фоторамках/боксах телефонии нет).
- Вынесена в отдельную тех-спеку (S0428) как независимый поток поверх лаунчера.

## Источники

- [Minimize your permission requests (ACTION_DIAL / ACTION_SENDTO) | Android Developers](https://developer.android.com/privacy-and-security/minimize-permission-requests)
- [Use of SMS or Call Log permission groups | Play Console Help](https://support.google.com/googleplay/android-developer/answer/10208820)
- [Permissions used only in default handlers | Android Developers](https://developer.android.com/guide/topics/permissions/default-handlers)
- [RoleManager (ROLE_DIALER / ROLE_SMS) | Android Developers](https://developer.android.com/reference/android/app/role/RoleManager)
