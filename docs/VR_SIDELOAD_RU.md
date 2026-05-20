# Руководство по установке VR через sideload

Как установить FastMediaSorter VR APK на Meta Quest без использования магазина.

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

## Шаг 4: Собрать VR APK

```powershell
# Вариант A: Скрипт сборки (автоверсия + копирование в DOWNLOADS/)
.\scripts\builders\build-vr-debug.ps1

# Вариант B: Gradle напрямую
.\gradlew.bat assembleVrDebug
```

APK будет по пути:
```
app_v2/build/outputs/apk/vr/debug/app_v2-vr-debug.apk
```

Или, при использовании скрипта, также скопирован в:
```
DOWNLOADS/FastMediaSorter_vr_debug.apk
```

## Шаг 5: Установить на Quest

```powershell
# Вариант A: Сборка + установка одной командой
.\scripts\builders\build-vr-device.ps1

# Вариант B: Ручная установка
adb install -r -d DOWNLOADS\FastMediaSorter_vr_debug.apk
```

## Шаг 6: Запуск на Quest

Приложение появляется в разделе **Unknown Sources** в библиотеке Quest:

1. Наденьте шлем
2. Откройте **Библиотеку приложений**
3. Выберите **Unknown Sources** в фильтре (верхний правый угол)
4. Найдите **FastMediaSorter VR** и запустите

Также можно запустить через ADB:
```powershell
adb shell am start -n com.sza.fastmediasorter.vr.debug/com.sza.fastmediasorter.ui.main.MainActivity
```

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
