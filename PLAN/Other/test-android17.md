# Тестовый сценарий: Android 17 — сбор дебаг-данных (флейвор `standard`)

**Цель:** собрать достаточные эмпирические данные для перевода `S0035` (`android17-local-network-permission`) из `Draft` в `Approved`, закрыть три open-вопроса (§6.1 Cast SDK, §6.2 SMBJ/SSHJ/FTP, §6.3 normal vs dangerous), и подтвердить §11 критерии готовности.

**Связь со специциями:** S0035 — единственный активный Android-17-тикет. Прочие тикеты Android-платформы (compileSdk/targetSdk bump) — отдельные будущие тикеты, не покрываются.

**Дата составления:** 2026-04-30

---

## 0. Контекст текущей сборки

- Текущее состояние: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26` (см. [`app_v2/build.gradle.kts:27`](../app_v2/build.gradle.kts)).
- В манифесте нет `ACCESS_LOCAL_NETWORK` (см. [`app_v2/src/main/AndroidManifest.xml`](../app_v2/src/main/AndroidManifest.xml)).
- Android 17 блокирует LAN **только** для приложений с `targetSdk ≥ 37`. На `targetSdk=35` поведение grandfathered и блокировки нет — это нужно учитывать при интерпретации результатов.

Поэтому требуется **два прохода** — Profile A (baseline, текущий targetSdk) и Profile B (форсированный targetSdk=37).

---

## 1. Prerequisites

### 1.1 Эмулятор Android 17 Beta 4

- Установить через SDK Manager: **System Image → Android 17 (API 37) → Google APIs ARM/x86_64**.
- Создать AVD:
  - Profile: Pixel 8 / 8 Pro (любая GMS-сборка).
  - RAM ≥ 4 GiB, Internal Storage ≥ 4 GiB.
  - Cold boot обязательно для первого прогона (`-no-snapshot-load`).
- Сетевой режим: **bridge / tap0**, не NAT. NAT эмулятора маскирует LAN-блокировку.

### 1.2 Тестовые серверы в LAN

- **SMB:** Windows-шара или Samba (любой доступный хост в той же подсети, что и эмулятор). Адрес: `smb://<host>/<share>`.
- **SFTP:** OpenSSH (`sshd`), порт 22, тестовая учётка с read-only.
- **FTP:** vsftpd / FileZilla Server, порт 21.
- **Chromecast:** реальный Chromecast / Chromecast-with-Google-TV в той же подсети. Эмулятор Cast Receiver не годится — он не широковещает по mDNS.

Все четыре сервиса должны быть доступны с хост-машины (`ping` + `nc -z`) до начала прогона. Записать IP в `temp/test-android17/setup.md` для повторяемости.

### 1.3 Логирование

- `adb logcat -c` перед каждым кейсом.
- `adb logcat -v time -f /sdcard/Download/test-<case>.log "*:V"` — захват всех приоритетов, чтобы не пропустить `SecurityException`.
- После каждого кейса — `adb pull /sdcard/Download/test-<case>.log temp/test-android17/<profile>/<case>.log`.
- Использовать `/log-reader` для post-mortem каждого файла.

---

## 2. Сборка

### 2.1 Profile A — baseline (текущий `targetSdk=35`)

```powershell
.\build-debug.ps1
```

Артефакт: `app_v2/build/outputs/apk/standardDebug/app_v2-standard-debug.apk`. Установить:

```powershell
adb install -r -d app_v2\build\outputs\apk\standard\debug\app_v2-standard-debug.apk
```

### 2.2 Profile B — форсированный `targetSdk=37`

**ВАЖНО:** только для тестирования. Не коммитить.

В `app_v2/build.gradle.kts` временно:

```kotlin
compileSdk = 37   // line 27 (current 35)
targetSdk  = 37   // line 38 (current 35)
```

Затем:

```powershell
.\build-debug.ps1
```

После прогона профиля B обязательно `git restore app_v2/build.gradle.kts`.

> **Если** SDK 37 ещё не установлен — `sdkmanager "platforms;android-37"`.

---

## 3. Сценарии

Все кейсы — **флейвор `standard`**, debug-вариант. Каждый кейс пишется в свой файл в `temp/test-android17/<profile>/<case>.log`.

| ID | Profile | Сценарий | Закрывает |
|----|---------|----------|-----------|
| A1 | A | SMB-ресурс, holiday path | §11.5 (≤API36 baseline) |
| A2 | A | SFTP-ресурс, holiday path | §11.5 |
| A3 | A | FTP-ресурс, holiday path | §11.5 |
| A4 | A | Chromecast обнаружение, видео-плеер | §11.5, §11.6 (отрицательный контроль) |
| B1 | B | SMB-ресурс, **впервые** (ожидается системный prompt) | §6.2, §11.1, §11.2 |
| B2 | B | SMB-ресурс, разрешение **отклонено** | §6.2, §11.2 |
| B3 | B | SMB, отказ → «Открыть настройки» → grant → возврат | §11.3, §11.4 |
| B4 | B | SFTP, разрешение **отклонено** | §6.2, §11.2 |
| B5 | B | FTP, разрешение **отклонено** | §6.2 |
| B6 | B | Chromecast, **разрешение есть** | §11.6 (положительный контроль) |
| B7 | B | Chromecast, **разрешение отклонено** (открытие плеера) | §6.1, §11.6 |
| B8 | B | Отзыв пермишна через Settings во время сессии | §6.3 (реактивность) |

### 3.1 Шаги для каждого кейса

#### A1 / A2 / A3 — baseline сетевые ресурсы

1. `adb shell pm clear com.sza.fastmediasorter.debug` — чистый старт.
2. `adb logcat -c && adb logcat -v time *:V > temp/test-android17/A/A1-smb.log &`.
3. Запустить приложение, добавить SMB-ресурс (Add Resource → SMB → ввести host/share/login).
4. Открыть ресурс, дождаться списка файлов.
5. Открыть один файл (видео/фото).
6. Закрыть приложение, остановить logcat.
7. **Ожидание:** никакого диалога `ACCESS_LOCAL_NETWORK`, ресурс работает без ошибок.
8. **Если появился диалог** — это регрессия (Android 17 не должен блокировать `targetSdk=35`).

#### A4 — baseline Chromecast

1. `adb shell pm clear com.sza.fastmediasorter.debug`.
2. Logcat → `temp/test-android17/A/A4-cast.log`.
3. Открыть локальное видео из `Internal storage`.
4. Кнопка Cast в плеере → ожидать список устройств.
5. **Ожидание:** Chromecast обнаруживается, можно начать трансляцию.
6. **Если** список пуст и в логе видны `SecurityException` или `EPERM` — регрессия (или сетевая проблема эмулятора, проверить NAT).

#### B1 — SMB первый запуск с `targetSdk=37`

1. Profile B APK установить, `pm clear`, logcat.
2. Add Resource → SMB → ввести параметры → **Save**.
3. **Ожидание:** системный prompt «App wants to access your local network» (точный текст зависит от Beta 4).
4. Принять разрешение.
5. Сканирование запускается, файлы появляются.
6. **Записать:**
   - Точный текст системного промпта (скриншот → `temp/test-android17/B/B1-prompt.png`).
   - Имя пермишна в Settings → Apps → FastMediaSorter → Permissions.
   - В логе — есть ли упоминания `ACCESS_LOCAL_NETWORK` от системы.

#### B2 / B4 / B5 — отказ в пермишне

1. `pm clear`, logcat.
2. Add Resource → SMB/SFTP/FTP → Save.
3. На системном промпте — **Deny**.
4. **Ожидание:** rationale-диалог приложения с двумя кнопками (Settings / Cancel).
5. **Записать:**
   - Точное исключение из библиотеки SMBJ / SSHJ / Apache Commons Net (полный stack trace в логе) — это закрывает §6.2.
   - Поведение UI: спиннер не висит бесконечно, ошибка явная, приложение не падает.
   - Проверить, что spinner снимается ≤5 секунд (записать тайминг).

#### B3 — grant через Settings

1. Старт от B2 (разрешение отклонено).
2. В rationale-диалоге → **Open Settings**.
3. Permissions → Local Network → Allow.
4. Кнопка Back → возврат в приложение.
5. **Ожидание:** ресурс становится доступным **без перезапуска** (S0035 §11.4).
6. **Записать:** есть ли в логе reactive-уведомление о смене пермишна, или нужен manual refresh.

#### B6 — Chromecast c пермишном

1. `pm clear`, logcat. Открыть приложение, дать разрешение проактивно через Add Resource → SMB → grant (используем тот же permission).
2. Открыть локальное видео → Cast button.
3. **Ожидание:** Chromecast виден, трансляция работает.

#### B7 — Chromecast без пермишна

1. `pm clear`, logcat. Открыть локальное видео сразу (без сетевых ресурсов).
2. Cast button.
3. **Ожидание (зависит от §6.1):**
   - Вариант (а): Cast SDK бросает `SecurityException` → приложение перехватывает, Cast UI показывает «недоступно».
   - Вариант (б): SDK возвращает пустой список → UI «нет устройств».
4. **Записать в `temp/test-android17/B/B7-cast-result.md`:** какой из двух вариантов наблюдался + полный stack trace, если был — это закрывает §6.1.

#### B8 — отзыв пермишна во время сессии

1. Стартовая точка: B6 (всё работает).
2. Свернуть приложение → Settings → Apps → FastMediaSorter → Permissions → Local Network → Deny.
3. Вернуться в приложение через recents.
4. Попытаться обновить SMB-список / переподключиться к Cast.
5. **Ожидание:** graceful degradation — UI показывает rationale, без крэша.
6. **Записать:** срабатывает ли проверка пермишна на каждый сетевой вызов, или используется кэшированное состояние.

### 3.2 Профиль C — повторный запуск

После Profile B перезапустить приложение **без** `pm clear` и подтвердить, что выданный пермишн сохраняется между сессиями (один скриншот rationale-диалога, который **не появился**).

---

## 4. Что собрать в каждом логе

Минимум, что должно попасть в каждый `*.log`:

- Полный stack trace **любого** `SecurityException`, `ConnectException`, `EPERM`, `NetworkSecurityException`.
- Все строки с тегами `SmbConnection`, `SftpClient`, `FtpClient`, `Cast`, `MediaRouter`, `PermissionHelper`.
- Любые строки `permission=ACCESS_LOCAL_NETWORK`.
- Системные `ActivityManager` строки про permission grant/deny (имеют тег `PackageManager` или `PermissionController`).

Команда фильтрации после прогона:

```powershell
adb pull /sdcard/Download/test-B1.log temp\test-android17\B\
Select-String -Path temp\test-android17\B\*.log `
  -Pattern 'SecurityException|ConnectException|EPERM|ACCESS_LOCAL_NETWORK|PermissionController' `
  > temp\test-android17\B\digest.txt
```

---

## 5. Шаблон отчёта

После всех 12 кейсов создать `temp/test-android17/REPORT.md` со следующей структурой:

```markdown
# Android 17 Test Report — <дата>

## Setup
- Эмулятор: Pixel 8 / Android 17 Beta 4 / API 37
- Хост-сеть: <IP/маска>
- Серверы: SMB <IP>, SFTP <IP>, FTP <IP>, Cast <модель>

## Profile A — baseline (targetSdk=35)
- A1 SMB: PASS / FAIL — <одна строка>
- A2 SFTP: ...
- A3 FTP: ...
- A4 Cast: ...

## Profile B — targetSdk=37
- B1..B8: PASS / FAIL — <одна строка с ссылкой на лог>

## Закрытие open-вопросов S0035
- §6.1 Cast SDK без LAN: <вариант (а) / (б)> — see B7
- §6.2 SMBJ/SSHJ/Apache Commons Net: <классы исключений> — see B2/B4/B5
- §6.3 Permission classification: dangerous / normal — see B1 (имя пермишна в Settings)

## Регрессии и отклонения
- <список несоответствий ожиданиям>

## Вердикт
- S0035 готов к `Approved` / требует доработки спецификации.
```

После заполнения отчёта — обновить S0035: ответы на §6.1..§6.3 → статусы `Resolved`, и через `/spec-update S0035` довести спеку до состояния, готового к `/spec-tech`.

---

## 6. Чего не делать

- **Не** поднимать `targetSdk` в `main`-ветке. Profile B — только локальная правка, обязательный `git restore` после прогона.
- **Не** запускать сценарий на флейворах `lite`/`legacy`/`photos` в рамках этого документа — они вне scope. Их прогон планируется отдельным сценарием после стабилизации `standard`.
- **Не** использовать NAT-сеть эмулятора — она маскирует именно тот класс ошибок, который мы ищем.
- **Не** интерпретировать пустые результаты Cast в Profile A как баг — без `targetSdk=37` блокировки нет; пустой список означает сетевую проблему, не пермишн.
