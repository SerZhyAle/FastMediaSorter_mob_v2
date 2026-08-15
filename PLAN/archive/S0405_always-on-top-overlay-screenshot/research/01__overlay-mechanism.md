# 01 - Механизм always-on-top оверлея и его стоимость

**Research item:** §6.1
**Дата:** 2026-06-11
**Статус:** Resolved (предварительно, по официальной документации)

## Вопрос

Можно ли держать постоянный always-on-top хэндл поверх других приложений на актуальных Android, и какова цена.

## Находки

- Always-on-top окно строится через `SYSTEM_ALERT_WINDOW` + `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`; рисуется поверх остальных приложений.
- Android 12+: окна `TYPE_APPLICATION_OVERLAY` считаются недоверенными. Введён `HIDE_OVERLAY_WINDOWS` - приложение может запретить рисовать оверлеи поверх себя (банковские/чувствительные экраны). Получить `SYSTEM_ALERT_WINDOW` стало труднее.
- Android 15 (targetSdk 35, наш pin): приложение с `SYSTEM_ALERT_WINDOW` может запускать foreground-сервис из фона только при наличии уже видимого оверлей-окна. То есть сначала показать видимый `TYPE_APPLICATION_OVERLAY`, затем стартовать FGS.
- Разрешение «Display over other apps» система считает безопасным для приложений из доверенных источников (Google Play) - это снижает, но не убирает policy-внимание.
- `TYPE_APPLICATION_OVERLAY` доступен с API 26 - совпадает с minSdk standard; на legacy (API 23..25) нужен иной (deprecated) тип окна.

## Вывод для спеки

- Контракт включения: явный пользовательский opt-in → запрос `SYSTEM_ALERT_WINDOW` → показать видимый хэндл → стартовать лёгкий foreground-сервис (порядок важен для Android 15).
- Нельзя рисовать поверх окон с `FLAG_SECURE` и системных диалогов; уважать `HIDE_OVERLAY_WINDOWS`.
- На legacy поведение оверлея отличается - вход в §6.5 (возможное исключение flavor `legacy`).

## Источники

- [Behavior changes: all apps (Android 12)](https://developer.android.com/about/versions/12/behavior-changes-all)
- [Behavior changes: Apps targeting Android 15 or higher](https://developer.android.com/about/versions/15/behavior-changes-15)
- [Changes to foreground services](https://developer.android.com/develop/background-work/services/fgs/changes)
- [How to Draw Over Other Apps in Android (GeeksforGeeks)](https://www.geeksforgeeks.org/android/how-to-draw-over-other-apps-in-android/)
