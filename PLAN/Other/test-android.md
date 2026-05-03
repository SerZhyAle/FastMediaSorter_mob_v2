# Тестовый сценарий: Android эмулятор — сбор дебаг-данных (флейвор `standard`)

**Цель:** собрать достаточные эмпирические данные для перевода спек из `Implemented`
в `Verified` — S0004, S0025, S0029, S0030, S0048; подтвердить восстановление
SFTP-канала S0047 (`BlockNeedUserTest`).

**S0003** (link-receive-download) — **Verified** ✓, в этот сценарий не входит.
Каждый кейс пишется в отдельный лог; `/log-reader` применяется post-mortem.

**Связь со спецификациями:**

| Спека | Название | Статус |
|-------|----------|--------|
| S0004 | resource-icon-quick-slideshow | Implemented |
| S0025 | smb-fast-fail | Implemented |
| S0029 | bugfix-resume-position-end-of-file | Implemented |
| S0030 | bugfix-panel-stereo-dialog-ui | Implemented |
| S0047 | bugfix-sftp-pool-broken-channel | BlockNeedUserTest |
| S0048 | info-dialog-extended-metadata | Implemented |

**Дата составления:** 2026-05-02

---

## 0. Что не входит в этот сценарий

- VR / Meta Quest 3 — покрывается `test-vr.md`.
- Android 17 / LAN-пермишн — покрывается `test-android17.md`.
- Флейворы `lite` / `photos` / `legacy` — вне scope.

---

## 1. Prerequisites

### 1.1 Эмулятор

- Pixel 6 (или Pixel 8) с Google APIs, Android 12–14 (API 31–34).
- RAM ≥ 4 GiB, Internal Storage ≥ 4 GiB.
- Cold boot для первого прогона.

### 1.2 Тестовые ресурсы (для S0025, S0047 и S0003)

- **SMB-шара** — Windows-шара или Samba в той же подсети (для позитивных кейсов).
  Убедиться с хост-машины: `net view \\<host>`.
- **SFTP-сервер** (S0047, S0048) — OpenSSH в WSL или любой публичный тестовый SFTP-хост.
  Убедиться: `sftp user@host`.
- **Прямая ссылка на медиа** (S0003) — подойдёт любой публичный `https://` URL на
  `.jpg`/`.mp4` (например, `https://www.w3schools.com/html/mov_bbb.mp4`).
- **HTML-страница с `<video>`** — подойдёт любой видеохостинг с открытым
  `<video src="...">` в разметке.

### 1.3 Видеофайлы (для S0029)

Подготовить два файла на эмуляторе (`adb push`):

- `long.mp4` — длительность > 100 с (проверка 5-секундной near-end zone).
- `short.mp4` — длительность ≤ 60 с (проверка 5%-й near-end zone ≈ 3 с).

### 1.4 Аудиофайлы (для S0047 и S0048)

Подготовить на SFTP-сервере (и локально на эмуляторе для регресс-теста):

- `test.flac` — FLAC с тегами (артист, альбом, год) и встроенной обложкой.
- `test_cbr.mp3` — CBR MP3 с ID3v2-тегами и заголовком LAME.
- `test_vbr.mp3` — VBR MP3 без xing/lame-заголовка (для проверки скрытия битрейта).

### 1.5 Логирование

```bash
# Очистить лог перед кейсом
adb logcat -c

# Запустить захват (заменить <case> на ID кейса)
adb logcat -v time "*:V" > temp/test-android/<case>.log &

# После кейса — остановить logcat (Ctrl-C или kill %1)
```

Директорию создать заранее:

```bash
mkdir -p temp/test-android
```

После каждого прогона применять `/log-reader` к соответствующему `.log`.

---

## 2. Сборка

```powershell
.\build-debug.ps1
```

Артефакт: `app_v2/build/outputs/apk/standard/debug/app_v2-standard-debug.apk`.

```bash
adb install -r -d app_v2/build/outputs/apk/standard/debug/app_v2-standard-debug.apk
```

---

## 3. Сценарии

### S0004 — Quick Slideshow from Resource Icon

Три коротких smoke-кейса. Никаких серверов не нужно.

| ID | Сценарий | Ожидание |
|----|----------|----------|
| Q1 | Tap на иконку **VIDEO_LIBRARY**-ресурса (не на название) | Слайдшоу стартует без входа в браузер ресурса |
| Q2 | Tap на иконку **PHOTO_STORAGE**-ресурса | Слайдшоу фото стартует |
| Q3 | Long-press на ту же иконку | Обычный context-menu или ничего — слайдшоу НЕ стартует случайно |

**Шаги:**

1. `adb shell pm clear com.sza.fastmediasorter.debug`
2. `adb logcat -c && adb logcat -v time "*:V" > temp/test-android/Q1.log &`
3. Добавить хотя бы один VIDEO_LIBRARY и один PHOTO_STORAGE ресурс.
4. На главном экране: коротко нажать на иконку-тип (левая иконка плитки ресурса).
5. Убедиться, что слайдшоу открывается; записать, появляется ли ripple-анимация на иконке.
6. **Если** tap не работает — зафиксировать отсутствие реакции в `Q1.log`.

---

### S0025 — SMB / FTP / SFTP Fast Fail

Проверяем, что при отсутствии сети приложение отвечает мгновенно (≤ 3 с),
а не зависает со спиннером.

| ID | Условие | Ресурс | Ожидание |
|----|---------|--------|----------|
| N1 | Wi-Fi **выключен** (Airplane mode) | SMB | Ошибка «нет сети» без долгого ожидания |
| N2 | Wi-Fi выключен | SFTP | То же |
| N3 | Wi-Fi выключен | FTP | То же |
| N4 | Wi-Fi **включён**, SMB-хост **недоступен** (неправильный IP) | SMB | Timeout ≤ 10 с, без зависания UI |
| N5 | Wi-Fi **включён**, SMB-хост **доступен** | SMB | Нормальное сканирование |

**Шаги (пример для N1):**

1. `adb shell pm clear com.sza.fastmediasorter.debug`
2. Создать SMB-ресурс с любым адресом.
3. Включить Airplane mode через системные настройки (или `adb shell cmd connectivity airplane-mode enable`).
4. `adb logcat -c && adb logcat -v time "*:V" > temp/test-android/N1.log &`
5. Открыть SMB-ресурс.
6. **Записать:**
   - Время между тапом и появлением сообщения об ошибке (записать вручную).
   - Точный текст ошибки на экране.
   - В логе — строки `NetworkReachabilityGate`, `error_network_connection_lost`.
   - Нет ли `SmbConnectionManager` записей дольше 1 с после gate-отказа.

---

### S0029 — Resume Position End of File

| ID | Сценарий | Ожидание |
|----|----------|----------|
| R1 | Смотреть `long.mp4` до конца (STATE_ENDED) → закрыть → открыть снова | Запускается с начала (0 с) |
| R2 | Смотреть `long.mp4` до –4 с от конца (near-end zone, file > 100 с) → Back → открыть снова | Запускается с начала |
| R3 | Смотреть `long.mp4` до 50% → Back → открыть снова | Resume с ~50% |
| R4 | Смотреть `short.mp4` (60 с) до –2 с (5% = 3 с) → Back → открыть снова | Запускается с начала |

**Шаги (пример для R1):**

1. `adb push long.mp4 /sdcard/Download/long.mp4`
2. `adb shell pm clear com.sza.fastmediasorter.debug`
3. `adb logcat -c && adb logcat -v time "*:V" > temp/test-android/R1.log &`
4. Открыть `long.mp4` через Files ресурс (Internal storage → Download).
5. Дать видео доиграть до конца (или промотать к –10 с и ждать STATE_ENDED).
6. Нажать Back.
7. Открыть тот же файл повторно.
8. **Записать:**
   - Позиция открытия (отображается в контролах плеера).
   - В логе — строки `PlaybackCompletionDetector`, `markPlaybackCompleted`, `STATE_ENDED`.
   - Если resume появляется — полный stack trace.

**Примечание:** для R2/R4 — остановить видео вручную (Back), пока таймер показывает
нужное время (long.mp4 – 4 c, short.mp4 – 2 c). Записать точную позицию остановки.

---

### S0030 — Panel Stereo Dialog UI

| ID | Сценарий | Ожидание |
|----|----------|----------|
| P1 | Открыть `.mp4` в panel mode → тапнуть кнопку «⚙» → выбрать режим **AUTO** | Диалог показывает обнаруженный формат вторичной строкой (e.g. «Detected: Mono») |
| P2 | Выбрать ручной режим **SBS** → закрыть диалог → открыть снова | Диалог показывает SBS, не что-то иное |
| P3 | Открыть диалог в **ландшафтной** ориентации | Горизонтальный переключатель не обрезается / не переносится некорректно |
| P4 | Открыть диалог в **портретной** ориентации | То же — нет визуальных артефактов |

**Шаги для P1:**

1. `adb shell pm clear com.sza.fastmediasorter.debug`
2. `adb logcat -c && adb logcat -v time "*:V" > temp/test-android/P1.log &`
3. Открыть любой `.mp4` в обычном (panel) режиме.
4. В контролах плеера → кнопка настроек → PlaybackControlDialog.
5. Тап на AUTO в секции Stereo Format.
6. **Записать:**
   - Текст вторичной строки (есть ли «Detected: …»).
   - В логе — `PlaybackControlDialog`, `handleStereoModeSelection`, `effectiveStereoMode`.
   - Если диалог показывает `effectiveStereoMode` вместо `userMode` — это регрессия S0030.

---

### S0047 — SFTP Broken Channel Recovery

Проверяем, что однократный сетевой сбой на SFTP не блокирует последующее воспроизведение
на том же хосте без перезапуска приложения.

| ID | Сценарий | Ожидание |
|----|----------|----------|
| F1 | Воспроизведение SFTP-файла → Airplane mode ≥ 1 с → Airplane mode ВЫКЛ → повторно открыть тот же файл | Файл открывается без перезапуска приложения |
| F2 | После F1 — открыть **другой** файл на том же SFTP-хосте | Воспроизведение запускается без `SSH_FX_FAILURE` |
| F3 | В логах после F1 — событие отбраковки канала | Ровно одна строка «канал отбракован» (или `broken channel discarded`), а не серия `Error opening SFTP file` |
| F4 | Стабильная сеть — несколько последовательных открытий файлов на том же SFTP-хосте | В логах каналы переиспользуются (нет `new channel opened` на каждый файл) |

**Шаги для F1–F3:**

1. `adb shell pm clear com.sza.fastmediasorter.debug`
2. Добавить SFTP-ресурс и убедиться, что подключение работает (нормальный просмотр файлов).
3. `adb logcat -c && adb logcat -v time "*:V" > temp/test-android/F1.log &`
4. Открыть любой медиафайл (`test.flac` или `.mp4`) с SFTP-ресурса и дать ему запуститься.
5. Включить Airplane mode: `adb shell cmd connectivity airplane-mode enable`.
6. Подождать ≥ 1 с (чтобы pipe закрылся), затем отключить: `adb shell cmd connectivity airplane-mode disable`.
7. Открыть тот же файл повторно.
8. Открыть другой файл на том же хосте (F2).
9. **Записать:**
   - Открылся ли файл после F1 без сообщения об ошибке и без перезапуска.
   - В логе — наличие одной строки `broken`/`discarded` для канала вместо повторных `SSH_FX_FAILURE`.
   - Нет ли `SftpPoolManager` записей «Error opening» более одного раза подряд.

**Шаги для F4:**

1. `adb logcat -c && adb logcat -v time "*:V" > temp/test-android/F4.log &`
2. Открыть 3–5 разных файлов с одного SFTP-хоста последовательно (не через Airplane mode).
3. **Записать:** в логе кол-во строк `new channel` / `channel reused` — на ≥ 2-й файл ожидается reuse.

---

### S0048 — Info Dialog Extended Metadata

Проверяем, что диалог «Информация о файле» показывает теги, технические параметры и
структурированный путь для аудиофайлов по сети и локально.

| ID | Сценарий | Ожидание |
|----|----------|----------|
| I1 | FLAC на SFTP → info-диалог | Артист, альбом, заголовок, год, sample rate, bit depth; обложка через 1–2 с |
| I2 | CBR MP3 на SMB → info-диалог | ID3-теги + точный битрейт (e.g. «320 kbps») |
| I3 | VBR MP3 без xing/lame (локальный) → info-диалог | Поле битрейта **отсутствует** (нет «~XXX kbps» или пустой строки) |
| I4 | Любой файл на SFTP → блок «File Information» | Хост, порт, каталог, имя файла — на отдельных строках, не одной строкой URL |
| I5 | Кнопка «Копировать путь» → вставить в любое приложение | Содержимое буфера — полный URL со схемой (`sftp://...`) |
| I6 | Локальный `.mp4` → info-диалог | Никакое ранее отображавшееся поле не пропало (регресс-проверка) |

**Шаги для I1:**

1. `adb shell pm clear com.sza.fastmediasorter.debug`
2. Добавить SFTP-ресурс, положить `test.flac` с тегами и обложкой.
3. `adb logcat -c && adb logcat -v time "*:V" > temp/test-android/I1.log &`
4. В браузере ресурса: long-press на `test.flac` → «Информация о файле».
5. **Записать:**
   - Список отображаемых полей (артист / альбом / заголовок / год / sample rate / bit depth).
   - Появилась ли обложка (и за сколько секунд).
   - В логе — `AudioMetadataExtractor`, `InfoDialogViewModel`, `thumbnailPath`.
   - Нет ли `NullPointerException`, `OutOfMemoryError`.

**Шаги для I3 (проверка отсутствия битрейта на VBR-MP3):**

1. `adb push test_vbr.mp3 /sdcard/Download/test_vbr.mp3`
2. Открыть info-диалог для `test_vbr.mp3` через Files ресурс.
3. **Записать:** присутствует ли строка «Битрейт» в диалоге. Ожидание — строки нет.

**Шаги для I4–I5:**

1. Открыть info-диалог для любого файла на SFTP.
2. Записать содержимое блока «File Information» (скриншот приветствуется).
3. Нажать кнопку «Копировать путь» → вставить в Notes/Chrome.
4. **Записать:** текст в буфере и сравнить со схемой `sftp://host:port/…`.

---


## 4. Что собирать в каждом логе

Минимум:

- Полный stack trace любого `Exception`, `Error`, `NullPointerException`, `SecurityException`.
- Строки тегов `NetworkReachabilityGate`, `SmbConnectionManager`, `FtpClient`, `SftpClient`.
- Строки `PlaybackCompletionDetector`, `markPlaybackCompleted`, `STATE_ENDED`, `resumeState`.
- Строки `LinkAutoDownload`, `ReceiveShare`, `MediaExtractor`.
- Строки `PlaybackControlDialog`, `handleStereoModeSelection`.
- Строки `SftpPoolManager`, `broken`, `discarded`, `SSH_FX_FAILURE`, `Pipe closed`.
- Строки `AudioMetadataExtractor`, `InfoDialogViewModel`, `thumbnailPath`, `PictureFrame`, `APIC`.

Фильтр-команда после прогона:

```powershell
Select-String -Path temp\test-android\*.log `
  -Pattern 'Exception|NetworkReachabilityGate|markPlaybackCompleted|STATE_ENDED|LinkAutoDownload|handleStereoModeSelection|SSH_FX_FAILURE|Pipe closed|broken|discarded|AudioMetadataExtractor|thumbnailPath' `
  > temp\test-android\digest.txt
```

---

## 5. Шаблон отчёта

После всех кейсов создать `temp/test-android/REPORT.md`:

```markdown
# Android Emulator Test Report — <дата>

## Setup
- Эмулятор: Pixel N / Android N (API N)
- Сборка: <хэш коммита>

## S0004 — Quick Slideshow from Icon
- Q1 VIDEO: PASS / FAIL — <одна строка>
- Q2 PHOTO: ...
- Q3 Long-press: ...

## S0025 — Network Fast Fail
- N1 SMB, Airplane: PASS / FAIL — <строка + тайминг>
- N2..N5: ...

## S0029 — Resume Position EOF
- R1..R4: PASS / FAIL — <строка с позицией resume>

## S0030 — Panel Stereo Dialog
- P1..P4: PASS / FAIL — <строка>

## S0047 — SFTP Broken Channel Recovery
- F1 Airplane + retry: PASS / FAIL — <восстановился без перезапуска?>
- F2 Next file after recovery: PASS / FAIL — <SSH_FX_FAILURE в логе?>
- F3 Log check: PASS / FAIL — <одна строка "discarded" vs серия ошибок>
- F4 Channel reuse: PASS / FAIL — <кол-во new channel opens на N файлов>

## S0048 — Info Dialog Extended Metadata
- I1 FLAC/SFTP tags: PASS / FAIL — <какие поля видны, обложка да/нет>
- I2 CBR MP3/SMB bitrate: PASS / FAIL — <значение битрейта>
- I3 VBR MP3 no bitrate: PASS / FAIL — <строка битрейта отсутствует?>
- I4 Structured path: PASS / FAIL — <хост/путь на отдельных строках?>
- I5 Copy button: PASS / FAIL — <содержимое буфера>
- I6 Local regression: PASS / FAIL

## Регрессии
- <список несоответствий ожиданиям>

## Вердикт
- S0004: Verified / требует фикса
- S0025: Verified / требует фикса
- S0029: Verified / требует фикса
- S0030: Verified / требует фикса
- S0047: Verified / требует фикса
- S0048: Verified / требует фикса
```

После заполнения отчёта — перевести закрытые спеки через
`/spec-check S<XXXX>` либо вручную через `update.ps1 -Id S<XXXX> -Status Verified`.

---

## 6. Чего не делать

- **Не** тестировать VR или XR режим на эмуляторе — эмулятор не поддерживает OpenXR.
- **Не** интерпретировать отсутствие SMB-шары как сетевой fast-fail (N4–N5 требуют
  Wi-Fi включённым; шара просто недостижима).
- **Не** коммитить временные APK и логи из `temp/`.
- **Не** использовать NAT-режим эмулятора для SMB-тестов — он маскирует реальные
  сетевые ошибки.
- **Не** интерпретировать «мягкую деградацию» S0048 (диалог без расширенных полей)
  как FAIL — это ожидаемый fallback при неподдерживаемом формате или FTP без `REST`.
