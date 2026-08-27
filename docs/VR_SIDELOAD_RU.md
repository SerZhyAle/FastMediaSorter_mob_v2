---
layout: default
title: "Руководство по установке VR через sideload"
permalink: /docs/VR_SIDELOAD_RU.html
---

# Руководство по установке VR через sideload

Как установить рабочую иммерсивную VR-сборку (`noLegal`) на Meta Quest без использования магазина.

Флейвор `vr` - целевой канал для Meta Horizon Store / Google Play, но его иммерсивный рендеринг на
шлеме пока не подключён (эпик S0773) - см. [Обзор VR-редакции](VR_EDITION_RU.md). Сегодня
единственный канал с рабочим иммерсивным режимом - `noLegal`, его и ставит это руководство.

## Требования

1. **Meta Quest** - Quest 3, Quest Pro или Quest 2
2. **USB-C кабель** - для подключения шлема к ПК
3. **Developer Mode включён** на шлеме
4. **ADB** - Android Debug Bridge (входит в Android SDK Platform Tools)

## Шаг 1: Включить Developer Mode

1. Откройте приложение **Meta** на телефоне
2. Перейдите в **Меню → Устройства** и выберите ваш шлем
3. Нажмите **Настройки шлема → Developer Mode**
4. Включите **Developer Mode**
5. Перезагрузите шлем

> Если опции Developer Mode нет, сначала зарегистрируйтесь как разработчик на [developer.meta.com](https://developer.meta.com/).

## Шаг 2: Установить ADB

Если у вас уже есть Android Studio или Android SDK, ADB находится по пути:
```
C:\Users\<имя_пользователя>\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

Иначе скачайте Platform Tools с [developer.android.com/tools/releases/platform-tools](https://developer.android.com/tools/releases/platform-tools) и распакуйте в удобное место.

## Шаг 3: Подключить Quest к ПК

1. Подключите Quest к ПК через USB-C
2. Наденьте шлем - появится диалог **«Разрешить отладку по USB?»**
3. Отметьте **«Всегда разрешать с этого компьютера»** и нажмите **Разрешить**
4. Проверьте подключение в терминале:

```powershell
adb devices
```

Устройство должно отображаться со статусом `device` (не `unauthorized`).

## Шаг 4: Собрать noLegal APK

```powershell
.\a.ps1 nd
```

Это запускает `scripts/builders/build-nolegal-debug.ps1`, который передаёт флаги, нужные
`noLegal` (`-Pchaquopy.enabled=true --no-configuration-cache`), поэтому сборка не зависит от
локальной настройки в `local.properties`. APK окажется по пути:
```
app_v2/build/outputs/apk/noLegal/debug/
```

## Шаг 5: Установить на Quest

```powershell
.\a.ps1 ivn
```

Это запускает `scripts/builders/install-nolegal-debug-to-device.ps1`, который сам находит только
что собранный APK под ABI подключённого устройства и ставит его - не нужно вручную вводить имя
файла с версией. Скрипт только устанавливает, намеренно (см. Шаг 6).

## Шаг 6: Запуск на Quest

Приложение появляется в разделе **Unknown Sources** в библиотеке Quest:

1. Наденьте шлем
2. Откройте **Библиотеку приложений**
3. Выберите **Unknown Sources** в фильтре (верхний правый угол)
4. Найдите **FastMediaSorter (noLegal debug)** и запустите

Запускайте из библиотеки Quest, а не через `adb shell am start`: запуск через ADB пропускает
vrshell `launch_id`, нужный иммерсивной сессии для входа в focused XR, поэтому приложение
откроется, но шлем останется на плоском 2D-окне.

## ADB через Wi-Fi (беспроводное подключение)

Чтобы не использовать кабель после первоначальной настройки:

```powershell
# Пока ещё подключены по USB:
adb tcpip 5555

# Отключите USB, затем подключитесь по Wi-Fi:
adb connect <ip-адрес-quest>:5555
```

IP-адрес Quest можно найти в **Настройки → Wi-Fi → Подключённая сеть → Подробности**.

## Устранение неполадок

| Проблема | Решение |
|----------|---------|
| `adb devices` показывает `unauthorized` | Наденьте шлем и примите диалог отладки по USB |
| `adb devices` ничего не показывает | Проверьте кабель, попробуйте другой порт, убедитесь что Developer Mode включён |
| Приложение не видно в библиотеке | Ищите в разделе **Unknown Sources** |
| Приложение падает при запуске | Проверьте logcat: `adb logcat -s FastMediaSorter` |
| XR runtime недоступен | Убедитесь что прошивка Quest обновлена |

## Связанная документация

- [Обзор VR-редакции](VR_EDITION_RU.md) - что делает VR-редакция и чем отличается от стандартной
- [Скрипты сборки](../scripts/builders/README.md) - все доступные команды сборки
