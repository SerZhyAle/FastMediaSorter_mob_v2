# 08 - Запуск shortcut'ов сторонних приложений с домашней поверхности

**Связано с §6 спеки S0404 (новый независимый item 8).** Дата: 2026-06-15.

## Чем это отличается от item 2

Item 2 / Phase 03 перечисляет и запускает **само приложение** - его главную `MAIN`/`LAUNCHER`-активность (launch-intent). Этот item - про другое: показать и запустить **app-shortcut'ы, которые публикует стороннее приложение** (Google Maps «Проложить маршрут домой», Chrome «Новая вкладка инкогнито», Samsung-приложения и т.п.) - те самые быстрые действия, что обычный лаунчер показывает по долгому нажатию на иконку. Это надстройка над реестром приложений, но технически отдельная способность.

## API и ограничение «только дефолтный лаунчер»

- Перечисление: `LauncherApps.getShortcuts(ShortcutQuery, UserHandle)` с флагами `FLAG_MATCH_MANIFEST` (статические из манифеста приложения), `FLAG_MATCH_DYNAMIC` (динамические), `FLAG_MATCH_PINNED` (закреплённые). Фильтр по `packageName` отдаёт shortcut'ы конкретного приложения.
- Запуск: `LauncherApps.startShortcut(...)` по выбранному `ShortcutInfo`.
- **Жёсткое требование:** `getShortcuts`/`startShortcut` доступны только текущему дефолтному лаунчеру (или активному voice-interaction-сервису); иначе `SecurityException`. Это не баг, а контракт платформы.
- **Синергия с S0404:** эпик уже получает роль Home (research 01). Как только приложение - дефолтный лаунчер, этот API становится легитимно доступен **без** `QUERY_ALL_PACKAGES` и без иных чувствительных разрешений. То есть фича не добавляет Play-рисков сверх того, что уже есть у реестра приложений.

## Уровень API и деградация

- App-shortcuts и `LauncherApps.getShortcuts` - с API 25.
- `standard`/`photos`/`legacy*`-цели на minSdk 26 - покрыты. Но флейвор `legacy` имеет minSdk 23: на API 23-24 app-shortcut'ов в платформе нет вовсе → деградировать (на этих устройствах остаётся только запуск главной активности из item 2, секция shortcut'ов скрыта).
- Пакет может публиковать ноль shortcut'ов → показывать только плитку самого приложения, без под-списка.

## Закрепление (pinned) - точка расширяемости

- Настоящий лаунчер может принимать запросы закрепления от других приложений (`LauncherApps.PinItemRequest`, когда стороннее приложение вызывает `requestPinShortcut`) и хранить закреплённые shortcut'ы.
- Для первой реализации достаточно показывать уже опубликованные manifest+dynamic shortcut'ы «избранных приложений профиля». Приём pin-запросов и хранение pinned-набора - расширение, не обязательное к первой версии.

## Вендорная специфика (Google/Samsung)

- Приложения Google/Samsung - обычные публикаторы shortcut'ов; отдельной обработки не требуют, поднимаются единообразно.
- Часть OEM-приложений отдаёт богатые динамические shortcut'ы (маршруты, недавние контакты) - это плюс к ценности фичи на профильной поверхности, без спец-кода.

## Не-лаунчерный fallback - не нужен

Существует обходной путь чтения только статических shortcut'ов не-лаунчером (через `PackageManager` `GET_META_DATA` + разбор `shortcuts.xml`), но он частичный (нет dynamic/pinned) и хрупкий. Поскольку S0404 держит роль Home, он не нужен. Рекомендация: гейтить секцию shortcut'ов условием «приложение - текущий дефолтный лаунчер» и не пытаться читать их до получения роли.

## Мульти-профиль пользователя

`getShortcuts` принимает `UserHandle`; для первой итерации - основной профиль пользователя (как и item 2). Рабочие профили - расширяемость реестра.

## Выводы для спеки

- Новая способность поверх реестра приложений (Phase 03) и роли Home (research 01); строго гейтится условием «приложение - текущий дефолтный лаунчер».
- Использует `LauncherApps.getShortcuts` + `startShortcut`; без `QUERY_ALL_PACKAGES`, без чувствительных разрешений → Play-совместима на всех целевых флейворах с ролью Home.
- Только API 25+; `legacy` на API 23-24 деградирует на запуск главной активности.
- Флейвор-сплит не нужен (нет чувствительных разрешений) - в отличие от реестра приложений.
- Вынесена в отдельную тех-спеку (S0427) как независимый поток поверх лаунчера.

## Источники

- [LauncherApps.ShortcutQuery | API reference | Android Developers](https://developer.android.com/reference/android/content/pm/LauncherApps.ShortcutQuery)
- [App shortcuts overview | Android Developers](https://developer.android.com/develop/ui/views/launch/shortcuts)
- [Handling shortcuts when building an Android Launcher (Medium)](https://medium.com/android-news/nhandling-shortcuts-when-building-an-android-launcher-5908d0bb50d2)
- [Retrieve and launch app shortcuts on Android without being a launcher (Medium)](https://medium.com/@pnhdroid/retrieve-and-launch-app-shortcuts-on-android-without-being-a-launcher-5af039ead4f8)
