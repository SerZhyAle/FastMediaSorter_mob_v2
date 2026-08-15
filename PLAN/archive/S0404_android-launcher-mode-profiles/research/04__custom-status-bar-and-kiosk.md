# 04 - Собственная строка состояния и kiosk-режим

**Связано с §6 спеки S0404 (новый item).** Дата: 2026-06-11.

Самая технически рискованная часть. Вопрос владельца: показывать **собственный бар «строки состояния»** (часы, сеть, батарея) вместо системного. Ключ: насколько можно убрать системный status bar и нарисовать свой - зависит от уровня привилегий, которых у обычного Play-приложения нет.

## Три уровня сокрытия системных баров

1. **Immersive sticky (любое приложение, без особых прав).** Скрытие system bars через контроллер инсетов с поведением «показать временно по свайпу». Системный status/navigation bar убираются, пока приложение на переднем плане, но свайп от края временно возвращает их. Нельзя запретить «шторку». Это максимум для `standard`/Play.

2. **Lock Task mode / screen pinning (закрепление экрана).** В этом режиме status bar пуст (системная информация и уведомления скрыты), кнопки Home/Overview скрыты. Без device owner закрепление требует подтверждения пользователя (ручной screen pinning). Если приложение - device owner, оно вайтлистит себя (`setLockTaskPackages`) и входит в режим (`startLockTask`) без подтверждения. На Android 9+ device owner может выборочно вернуть часть системного UI (`setLockTaskFeatures`).

3. **Device owner (полный контроль).** Назначается на «непровизионенном» устройстве: `adb shell dpm set-device-owner <pkg>/<DeviceAdminReceiver>` (нужен объявленный `DeviceAdminReceiver`), либо QR/zero-touch provisioning. Только этот уровень даёт настоящую замену системного status bar собственным баром на постоянной основе (вплоть до `setStatusBarDisabled`), вайтлист lock task без подтверждения, блокировку «шторки».

## Что это значит для собственного бара

- **Постоянная замена системного бара своим = только device owner.** Без него «свой бар» - это оверлей в верхней зоне при immersive sticky, который сосуществует с системным баром, доступным по свайпу. Это деградированный, но рабочий вариант.
- Device owner реалистичен именно на **выделенных устройствах** (настенная фоторамка, медиабокс), которые провизионят один раз. Это совпадает с целевыми сценариями фичи и объясняет noLegal-first (см. `06`).

## Источники данных для собственного бара (без особых прав)

- **Часы/дата:** системное время; виджет авто-обновляемых часов. Без разрешений.
- **Батарея:** `BatteryManager` / sticky-broadcast `ACTION_BATTERY_CHANGED` (уровень, зарядка). Без разрешений.
- **Сеть/Wi-Fi:** `ConnectivityManager` + NetworkCallback (тип сети, есть ли интернет). Нужно `ACCESS_NETWORK_STATE` (normal-разрешение, без рантайм-запроса).
- **Сила мобильного сигнала:** `TelephonyManager` / SignalStrength - частично требует `READ_PHONE_STATE` (рантайм) и доступно не на всех устройствах/версиях. Деградировать: при отсутствии прав показывать только тип сети, без «палочек».

## Выводы для спеки

- Собственная строка состояния - **способность с двумя уровнями**: полный (device owner: системный бар скрыт, свой бар постоянный, kiosk-блокировка) и базовый (без owner: immersive sticky + оверлейный бар, системный бар достижим свайпом).
- Полный kiosk (lock task без подтверждения, скрытый бар) требует device-owner-провизионинга - это операционное требование к устройству, не «фича по кнопке». Уместно для noLegal/выделенных устройств.
- Все источники данных бара, кроме силы сигнала, доступны без чувствительных прав; силу сигнала деградировать.
- Риск «застрял в kiosk»: нужен гарантированный выход (секретный жест/код) и понятный сценарий снятия device owner (`dpm remove-active-admin` / factory reset) - задокументировать.

## Источники

- [Lock task mode | Android Developers](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode)
- [DevicePolicyManager | Android Developers](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- [BatteryManager | Android Developers](https://developer.android.com/reference/android/os/BatteryManager)
- [Android Kiosk Apps and Custom MDM Using Device Owner (techyourchance)](https://www.techyourchance.com/android-kiosk-apps-and-in-house-mdms-using-device-owner/)
- [Kiosk restrictions: deep dive (emteria)](https://emteria.com/blog/android-kiosk-restrictions)
