# 12 - Публикация лаунчера в Google Play

**Связано с §3.2 и ADR-6 спеки S0404.** Дата: 2026-07-16.

Заменяет снятый item 11 (упаковка kiosk в публикуемый flavor). Вопрос владельца: «его же можно будет пропихнуть в Google Store?»

## Ответ: бумаг нет вообще

Лаунчеры - обычный, хорошо населённый класс приложений в Play.

- **Нет категории лаунчера** в Developer Program Policy: ни одна из 12 категорий политики не покрывает launcher / home screen / default apps / kiosk / device admin.
- **Нет декларации** и **нет спец-ревью**: лаунчер ревьюится как обычное приложение.
- Ближайшая по смыслу [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646) не упоминает ни launcher, ни home screen, ни device admin, ни kiosk, ни lock task.
- Живые на Play (проверено 2026-07-16): Nova Launcher (100M+), Microsoft Launcher (50M+), Smart Launcher 6 (50M+), Niagara (10M+), Action Launcher (10M+), AIO Launcher (1M+), Lawnchair (500K+).
- **Удалений лаунчеров по политике за 2024-2026 не найдено.** Apex Launcher и Smart Launcher Pro мертвы по не-политическим причинам (заброшен разработчик / retired legacy-пакет). Nova не удалён - там смена владельца (Instabridge, январь 2026). PojavLauncher - ложное совпадение, это Minecraft-лаунчер.

## QUERY_ALL_PACKAGES: главный и единственный риск

**Лаунчеры НЕ входят в перечень разрешённых применений.** Это ключевой вывод, противоречащий распространённому мнению.

Дословно с [политики](https://support.google.com/googleplay/android-developer/answer/10158779):

> "Permitted uses include device search, antivirus apps, file managers, and browsers."

Слова «launcher» и «home screen» на странице **не встречаются**. Перечень незакрытый («include»), поэтому лаунчер может пройти только через общий core-functionality тест:

> "You must be able to adequately justify why a less intrusive method of app visibility will not sufficiently enable your app's policy-compliant user-facing core functionality."

И тут же invalid-use, который бьёт прямо:

> "When the required task can be done with a less broad app-visibility method."

Санкция за недекларированное/обманное применение:

> "may result in a suspension of your app and/or termination of your developer account."

Google-собственный lint: «most apps on Google Play are not allowed to have this permission» ([QueryAllPackagesPermission](https://googlesamples.github.io/android-custom-lint-rules/checks/QueryAllPackagesPermission.md.html)).

**Почему для нас риск выше, чем для чистого лаунчера.** Lawnchair шлёт `QUERY_ALL_PACKAGES` в Play-сборке и живёт - но это чистый лаунчер, у которого core purpose очевиден. У нас медиа-сортер с launcher-режимом: ревьюер видит медиа-приложение, и аргумент «без этого приложение сломано» заведомо слабее. Это толерантность, а не разрешение.

**Вывод: не использовать. Оно нам ничего не даёт (см. ниже).**

## `<queries>` покрывает потребность лаунчера целиком

Декларация `<queries>` с одним `<action MAIN>` + `<category LAUNCHER>` легальна по [ограничениям формы](https://developer.android.com/training/package-visibility/declaring) («exactly one `<action>`») и возвращает все приложения с launcher-активностью.

CommonsWare, дословно:

> "Since most apps have a launcher activity, this particular `<queries>` setup largely reverses the restrictions placed here by Android 11."

Что `QUERY_ALL_PACKAGES` добавил бы сверх: только пакеты **без** launcher-активности - то есть те, которые лаунчер всё равно не может запустить. Для нашей задачи прибавка нулевая.

Google прямо называет `<queries>` штатным путём: `QUERY_ALL_PACKAGES` - для «rare cases», где `<queries>` не хватает.

Оговорки:

- API 30+. На API < 30 (в т.ч. Android 8 автомагнитол владельца) фильтрации нет вовсе - **тест на старом устройстве не проверяет Play-safe путь**, проверять надо на Android 11+.
- `LauncherApps.getActivityList()` **не обходит** фильтрацию и требует той же декларации (уверенность средняя - официальная документация не называет метод явно; проверить на устройстве).

## Device admin / device owner

- **Ship `DeviceAdminReceiver` в Play-APK разрешено.** Политики против нет; Google-собственная Android Device Policy опубликована в Play. [Device admin deprecation](https://developers.google.com/android/work/device-admin-deprecation) - это enterprise-депрекация, потребительский путь (`USES_POLICY_WIPE_DATA`, `USES_POLICY_FORCE_LOCK`) сохранён явно.
- Триггеры отклонения - поведенческие: блокировка удаления, мимикрия под системные диалоги ([Mobile Unwanted Software](https://developers.google.com/android/play-protect/mobile-unwanted-software)).
- **Device owner из Play-установки недостижим никогда.** Провизионинг только при setup устройства (`dpm set-device-owner` / QR / NFC / zero-touch) на устройстве без аккаунтов.
- **Kiosk для Play-приложения фактически невозможен.** Без allowlist от DPC `startLockTask()` даёт лишь screen pinning, а «the person using the device can exit the mode whenever they want» ([Lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode)).
- Настоящий kiosk/EMM живёт в Android Enterprise + managed Google Play - другой канал дистрибуции, не публичный Play.

**Следствие для S0404:** раз носитель - `standard` (Play), kiosk недостижим в принципе → ADR-8 (снятие kiosk из эпика).

## Роль Home

- `RoleManager.ROLE_HOME` + `createRequestRoleIntent` - **API 29+**.
- На API 23-28 (наш minSdk 26 / legacy 23) API запроса роли нет. Пути: `Settings.ACTION_HOME_SETTINGS` либо включение второго `CATEGORY_HOME`-компонента через `setComponentEnabledSetting()`, что инвалидирует кэш дефолта и форсит системный выбор «Always / Just once».
- **Play-политики на ROLE_HOME нет.** Play ограничивает только default-handler для SMS/Phone/Assistant (там завязаны sensitive-разрешения); роль Home никаких разрешений не даёт, поэтому и декларации не существует.

## Побочная находка (вне S0404)

[Android developer verification](https://developer.android.com/developer-verification): приложения должны быть от верифицированного разработчика для установки на **сертифицированные** устройства - BR/ID/SG/TH к 2026-09-30, глобально в 2027. Касается всех приложений, не лаунчеров. Бьёт по sideload-каналу проекта (`noLegal`) → припарковано как **S1079**.

## Ключевые выводы для спеки

- Публикация лаунчера в Play не требует ни категории, ни декларации, ни спец-ревью → §3.2, флейвор `standard` как носитель.
- `<queries>` MAIN/LAUNCHER - единственный нужный механизм; `QUERY_ALL_PACKAGES` запрещён и бесполезен → ADR-6.
- Device owner из Play недостижим → ADR-8 (kiosk снят).
- Роль Home на API 26 берётся включением компонента → ADR-2.

## Источники

- [Developer Program Policy index](https://support.google.com/googleplay/android-developer/answer/16944162)
- [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646)
- [Use of QUERY_ALL_PACKAGES permission](https://support.google.com/googleplay/android-developer/answer/10158779)
- [Declare package visibility needs](https://developer.android.com/training/package-visibility/declaring)
- [`<queries>` element](https://developer.android.com/guide/topics/manifest/queries-element)
- [CommonsWare - package visibility](https://commonsware.com/R/pages/chap-package-005.html)
- [Lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode)
- [Device admin deprecation](https://developers.google.com/android/work/device-admin-deprecation)
- [Mobile Unwanted Software](https://developers.google.com/android/play-protect/mobile-unwanted-software)
- [Android developer verification](https://developer.android.com/developer-verification)
