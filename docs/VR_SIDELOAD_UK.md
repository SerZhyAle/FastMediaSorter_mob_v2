---
layout: default
title: "Посібник зі встановлення VR через sideload"
permalink: /docs/VR_SIDELOAD_UK.html
---

# Посібник зі встановлення VR через sideload

Як встановити робочу імерсивну VR-збірку (`noLegal`) на Meta Quest без використання магазину.

Флейвор `vr` - цільовий канал для Meta Horizon Store / Google Play, але його імерсивний рендеринг
на шоломі ще не підключений (епік S0773) - див. [Огляд VR-редакції](VR_EDITION_UK.md). Сьогодні
єдиний канал із робочим імерсивним режимом - `noLegal`, його й встановлює цей посібник.

## Вимоги

1. **Meta Quest** - Quest 3, Quest Pro або Quest 2
2. **USB-C кабель** - для підключення шолома до ПК
3. **Developer Mode увімкнений** на шоломі
4. **ADB** - Android Debug Bridge (входить до Android SDK Platform Tools)

## Крок 1: Увімкнути Developer Mode

1. Відкрийте додаток **Meta** на телефоні
2. Перейдіть до **Меню → Пристрої** та оберіть ваш шолом
3. Натисніть **Налаштування шолома → Developer Mode**
4. Увімкніть **Developer Mode**
5. Перезавантажте шолом

> Якщо опції Developer Mode немає, спочатку зареєструйтесь як розробник на [developer.meta.com](https://developer.meta.com/).

## Крок 2: Встановити ADB

Якщо у вас вже є Android Studio або Android SDK, ADB знаходиться за шляхом:
```
C:\Users\<ім'я_користувача>\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

Інакше завантажте Platform Tools з [developer.android.com/tools/releases/platform-tools](https://developer.android.com/tools/releases/platform-tools) та розпакуйте в зручне місце.

## Крок 3: Підключити Quest до ПК

1. Підключіть Quest до ПК через USB-C
2. Надягніть шолом - з'явиться діалог **«Дозволити налагодження через USB?»**
3. Позначте **«Завжди дозволяти з цього комп'ютера»** та натисніть **Дозволити**
4. Перевірте підключення в терміналі:

```powershell
adb devices
```

Пристрій має відображатися зі статусом `device` (не `unauthorized`).

## Крок 4: Зібрати noLegal APK

```powershell
.\a.ps1 nd
```

Це запускає `scripts/builders/build-nolegal-debug.ps1`, який передає прапорці, потрібні `noLegal`
(`-Pchaquopy.enabled=true --no-configuration-cache`), тож збірка не залежить від локального
налаштування в `local.properties`. APK опиниться за шляхом:
```
app_v2/build/outputs/apk/noLegal/debug/
```

## Крок 5: Встановити на Quest

```powershell
.\a.ps1 ivn
```

Це запускає `scripts/builders/install-nolegal-debug-to-device.ps1`, який сам знаходить щойно
зібраний APK під ABI підключеного пристрою і встановлює його - не потрібно вручну вводити імʼя
файлу з версією. Скрипт лише встановлює, навмисно (див. Крок 6).

## Крок 6: Запуск на Quest

Додаток з'являється в розділі **Unknown Sources** у бібліотеці Quest:

1. Надягніть шолом
2. Відкрийте **Бібліотеку додатків**
3. Оберіть **Unknown Sources** у фільтрі (верхній правий кут)
4. Знайдіть **FastMediaSorter (noLegal debug)** та запустіть

Запускайте з бібліотеки Quest, а не через `adb shell am start`: запуск через ADB пропускає
vrshell `launch_id`, потрібний імерсивній сесії для входу в focused XR, тож додаток відкриється,
але шолом залишиться на плоскому 2D-вікні.

## ADB через Wi-Fi (бездротове підключення)

Щоб не використовувати кабель після початкового налаштування:

```powershell
# Поки ще підключені по USB:
adb tcpip 5555

# Від'єднайте USB, потім підключіться по Wi-Fi:
adb connect <ip-адреса-quest>:5555
```

IP-адресу Quest можна знайти в **Налаштування → Wi-Fi → Підключена мережа → Деталі**.

## Усунення несправностей

| Проблема | Рішення |
|----------|---------|
| `adb devices` показує `unauthorized` | Надягніть шолом та прийміть діалог налагодження через USB |
| `adb devices` нічого не показує | Перевірте кабель, спробуйте інший порт, переконайтеся що Developer Mode увімкнений |
| Додаток не видно в бібліотеці | Шукайте в розділі **Unknown Sources** |
| Додаток падає при запуску | Перевірте logcat: `adb logcat -s FastMediaSorter` |
| XR runtime недоступний | Переконайтеся що прошивка Quest оновлена |

## Пов'язана документація

- [Огляд VR-редакції](VR_EDITION_UK.md) - що робить VR-редакція та чим відрізняється від стандартної
- [Скрипти збірки](../scripts/builders/README.md) - всі доступні команди збірки
