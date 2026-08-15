# 07 - Foreground-сервис и контроль пользователя (Android 14+)

**Research item:** §6.7
**Дата:** 2026-06-11
**Статус:** Resolved

## Вопрос

Какой тип foreground-сервиса корректен под захват экрана и как пользователь его явно останавливает.

## Находки (targetSdk 35 - правила Android 14+ применяются)

- Android 14 (API 34): каждый foreground-сервис обязан объявить `foregroundServiceType`. Для захвата экрана - `android:foregroundServiceType="mediaProjection"` + разрешения `FOREGROUND_SERVICE` и `FOREGROUND_SERVICE_MEDIA_PROJECTION`. Иначе - `MissingForegroundServiceTypeException`.
- Порядок обязателен: сначала `createScreenCaptureIntent()` (система показывает пользователю запрос/уведомление о захвате) → пользователь даёт согласие → запуск FGS с типом `mediaProjection` → затем `getMediaProjection()`.
- Согласие - на каждую сессию захвата; один экземпляр `MediaProjection` используется один раз (один `createVirtualDisplay`).
- Android 15 (см. артефакт 01): FGS из фона при наличии `SYSTEM_ALERT_WINDOW` разрешён только при уже видимом оверлей-окне - наш хэндл этому удовлетворяет.

## Вывод для спеки

- Тип FGS под захват - `mediaProjection`; манифест и разрешения добавляются на фазе `/spec-tech`.
- UX-следствие: посессионное согласие = либо системный запрос при каждом снимке, либо одна удерживаемая сессия на время активного хэндла (взвесить против «один экземпляр - один кадр»; вероятно пере-создание virtual display в рамках одной разрешённой сессии). Уточняется на фазе импла.
- Контроль пользователя: постоянное уведомление сервиса с действием «выключить»; после выключения - снять оверлей и сервис, освободить ресурсы захвата. На перезагрузку молча не подниматься.

## Источники

- [Foreground service types are required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Foreground service types | Background work](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Media projection | Android Developers](https://developer.android.com/media/grow/media-projection)
