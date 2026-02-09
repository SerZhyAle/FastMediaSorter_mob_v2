# AVD Setup for Maestro Tests

# Настройка виртуального устройства для тестов Maestro

## Рекомендуемая конфигурация эмулятора

### 1. Характеристики AVD

**Рекомендуемые параметры** (Android Studio AVD Manager):

```
Device:      Pixel 6 или Pixel 5 (стандартное устройство)
API Level:   API 33 (Android 13.0) или API 34 (Android 14.0)
Target:      Google APIs (НЕ Google Play - быстрее)
RAM:         4096 MB (минимум 3072 MB)
VM Heap:     512 MB
Internal:    2048 MB
SD Card:     512 MB (или больше для тестовых медиа)
```

**Причины выбора**:

- API 33/34: современные версии, минимум багов Maestro
- Google APIs: без Play Store = быстрее, меньше фоновых процессов
- 4GB RAM: достаточно для стресс-тестов, не вызывает OOM

### 2. Hardware Settings (AVD Manager → Advanced Settings)

```
Graphics:            Hardware - GLES 2.0 (CRITICAL для производительности)
Multi-Core CPU:      4 cores (минимум 2)
Boot option:         Cold boot (для lifecycle тестов)
Device Frame:        Disable (экономит ресурсы хоста)
```

**⚠ КРИТИЧНО**: `Graphics: Hardware` — без этого эмулятор будет лагать, тесты будут падать по таймауту.

### 3. Ускорение производительности

#### Windows (Hyper-V или HAXM)

**Проверка**:

```powershell
# Проверить что Hyper-V включен (Windows 10 Pro/Enterprise)
Get-WindowsOptionalFeature -Online -FeatureName Microsoft-Hyper-V

# Или HAXM установлен (если Hyper-V недоступен)
sc query intelhaxm
```

**Включить ускорение**:

- Android Studio → Tools → SDK Manager → SDK Tools
- Установить: `Intel x86 Emulator Accelerator (HAXM)` (если нет Hyper-V)
- Перезагрузка системы

#### Проверка ускорения в эмуляторе

Запустить эмулятор, открыть терминал:

```powershell
adb shell getprop ro.kernel.qemu.gles
# Должно быть: 1 (hardware acceleration активна)
```

### 4. Настройки эмулятора после старта

#### Отключить анимации (ОБЯЗАТЕЛЬНО)

```powershell
# Подключиться к эмулятору
adb shell settings put global window_animation_scale 0.0
adb shell settings put global transition_animation_scale 0.0
adb shell settings put global animator_duration_scale 0.0
```

**Или через GUI** (рекомендуется закрепить):

1. Settings → System → About emulated device
2. Тап 7 раз по "Build number" (активирует Developer options)
3. Settings → System → Developer options
4. Найти раздел "Drawing":
   - **Window animation scale**: Animation off
   - **Transition animation scale**: Animation off
   - **Animator duration scale**: Animation off

#### Оптимизация памяти

```powershell
# Отключить Google Services (экономит RAM)
adb shell pm disable-user --user 0 com.google.android.gms
adb shell pm disable-user --user 0 com.google.android.gsf

# Очистить логи перед тестами
adb logcat -c

# Проверить свободную память
adb shell cat /proc/meminfo | grep MemAvailable
# Должно быть минимум 1.5 GB доступно
```

#### Установить Storage для тестов

```powershell
# Создать директории для тестовых медиа
adb shell mkdir -p /sdcard/Pictures/test_media
adb shell mkdir -p /sdcard/Movies/test_media
adb shell mkdir -p /sdcard/Music/test_media

# Настроить права
adb shell chmod 777 /sdcard/Pictures/test_media
adb shell chmod 777 /sdcard/Movies/test_media
adb shell chmod 777 /sdcard/Music/test_media
```

### 5. Загрузка тестовых медиа файлов

**Минимальный набор** (для smoke/stress тестов):

```powershell
# Из корня проекта
cd test_media

# Загрузить тестовые файлы
adb push sample_image.jpg /sdcard/Pictures/test_media/
adb push sample_video.mp4 /sdcard/Movies/test_media/
adb push sample_audio.mp3 /sdcard/Music/test_media/

# Обновить MediaStore (чтобы файлы отобразились)
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/test_media/sample_image.jpg
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/test_media/sample_video.mp4
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/test_media/sample_audio.mp3
```

**Если test_media файлы не существуют** — создайте dummy файлы:

```powershell
# В temp/ создадим тестовые файлы
cd temp

# Создать тестовый файл изображения (1x1 PNG)
[Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==") | Set-Content -Path test.png -Encoding Byte

adb push test.png /sdcard/Pictures/test_media/test.png
```

### 6. Snapshot для быстрого восстановления

**Создать snapshot после настройки** (рекомендуется):

1. Настроить эмулятор по инструкциям выше
2. Установить FastMediaSorter debug APK
3. Android Studio → AVD Manager → Actions (dropdown рядом с эмулятором)
4. **Snapshots** → **Take Snapshot** → имя: `maestro_tests_ready`

**Использование**:

```powershell
# Запустить эмулятор из snapshot (быстрый старт ~5 сек)
emulator -avd Pixel_6_API_33 -snapshot maestro_tests_ready
```

### 7. Скрипт автоматической подготовки

**Создать** `scripts/setup-avd-for-tests.ps1`:

```powershell
# Setup AVD for Maestro Tests
# Usage: .\scripts\utils\setup-avd-for-tests.ps1

$adb = "adb"

Write-Host "🚀 Setting up AVD for Maestro tests..." -ForegroundColor Cyan

# Wait for device
Write-Host "Waiting for device..." -ForegroundColor Yellow
& $adb wait-for-device

# Disable animations
Write-Host "Disabling animations..." -ForegroundColor Yellow
& $adb shell settings put global window_animation_scale 0.0
& $adb shell settings put global transition_animation_scale 0.0
& $adb shell settings put global animator_duration_scale 0.0

# Clear logcat
Write-Host "Clearing logcat..." -ForegroundColor Yellow
& $adb logcat -c

# Create test directories
Write-Host "Creating test media directories..." -ForegroundColor Yellow
& $adb shell mkdir -p /sdcard/Pictures/test_media
& $adb shell mkdir -p /sdcard/Movies/test_media
& $adb shell mkdir -p /sdcard/Music/test_media

# Grant permissions proactively
Write-Host "Granting storage permissions to app..." -ForegroundColor Yellow
& $adb shell pm grant com.sza.fastmediasorter.debug android.permission.READ_EXTERNAL_STORAGE
& $adb shell pm grant com.sza.fastmediasorter.debug android.permission.WRITE_EXTERNAL_STORAGE
& $adb shell pm grant com.sza.fastmediasorter.debug android.permission.READ_MEDIA_IMAGES
& $adb shell pm grant com.sza.fastmediasorter.debug android.permission.READ_MEDIA_VIDEO
& $adb shell pm grant com.sza.fastmediasorter.debug android.permission.READ_MEDIA_AUDIO

Write-Host "✅ AVD configured for testing!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Upload test media files (see AVD_SETUP_FOR_TESTS.md)" -ForegroundColor Gray
Write-Host "  2. Run: .\scripts\utils\run-maestro-smoke.ps1" -ForegroundColor Gray
```

### 8. Проверка конфигурации

**Если adb не найден в терминале**:

```powershell
# Быстрое решение (временное для текущей сессии)
.\scripts\add-adb-to-path.ps1

# Теперь можно использовать adb без полного пути
adb devices
```

**Перед запуском тестов**:

```powershell
# Проверить что эмулятор готов
adb shell getprop sys.boot_completed
# Должно вернуть: 1

# Проверить анимации выключены
adb shell settings get global window_animation_scale
# Должно вернуть: 0.0 или null

# Проверить доступную память
adb shell dumpsys meminfo | findstr "Free RAM"
# Должно быть минимум 1 GB

# Проверить что app установлено
adb shell pm list packages | findstr fastmediasorter
# Должно вернуть: package:com.sza.fastmediasorter.debug
```

### 9. Troubleshooting

#### Эмулятор лагает / тесты падают по timeout

```powershell
# 1. Проверить Hardware Graphics
emulator -avd Your_AVD -gpu host

# 2. Увеличить RAM эмулятора (AVD Manager)
# Minimum 3 GB, рекомендуется 4 GB

# 3. Закрыть другие ресурсоемкие приложения на хосте
```

#### Low memory warnings

```powershell
# Очистить кеш приложений
adb shell pm trim-caches 500M

# Перезапустить эмулятор с большим heap
emulator -avd Your_AVD -memory 4096
```

#### Maestro не находит элементы UI

```powershell
# Убедиться что анимации выключены (см. раздел 4)
# Увеличить timeout в Maestro YAML:
# - waitForAnimationToEnd: 
#     timeout: 5000
```

---

## Рекомендации для CI/CD

Для GitHub Actions / Jenkins используйте:

- API 30 (стабильнее для CI)
- Headless mode: `emulator -avd CI_AVD -no-window -no-audio`
- Snapshot boot: быстрый старт (~10 сек вместо 60)

---

## Сводка (Quick Reference)

| Параметр              | Значение                          |
|-----------------------|-----------------------------------|
| API Level             | 29-34 (Android 10-14)             |
| RAM                   | 2560 MB (budget) / 3072 MB (mid)  |
| Graphics              | Hardware - GLES 2.0               |
| Animations            | OFF (все 3 настройки)             |
| CPU Cores             | 4                                 |
| Storage               | 2 GB internal + SD                |

**Команда после старта эмулятора**:

```powershell
adb shell settings put global window_animation_scale 0.0 && adb shell settings put global transition_animation_scale 0.0 && adb shell settings put global animator_duration_scale 0.0
```

**Запуск тестов**:

```powershell
.\scripts\utils\run-maestro-smoke.ps1        # Smoke tests
.\scripts\utils\run-maestro-stress.ps1 -Monitor -Report  # Stress tests
```
