# FastMediaSorter VR-редакція

## Що таке VR-редакція?

FastMediaSorter VR - це виділена редакція FastMediaSorter, призначена для VR-шоломів: Meta Quest 3, Quest Pro, Quest 2 та пристроїв Android XR (Samsung Project Moohan та надалі). Це **повноцінний медіаплеєр**, ідентичний стандартній версії, розширений стереоскопічним рендерингом через OpenXR.

VR-редакція - не окремий додаток, а той самий код, зібраний як `vr` product flavor з додатковим шаром OpenXR-рендерингу.

## Ключові відмінності від стандартної версії

| Функція | Standard | VR |
|---------|----------|----|
| Стереоскопічний рендеринг (SBS/OU) | Crop-прев'ю одного ока | Повний per-eye рендеринг через OpenXR |
| Перегляд 3D-фото | Плоске відображення | Per-eye стерео з UV-crop |
| Cinema mode | Немає | Плоский віртуальний екран у VR-просторі |
| Зберегти кадр (3D-контент) | 2D PNG | SBS PNG з обома ракурсами |
| Вкладка 3DVR у діалозі Control | Прихована | Активна (override формату, IPD) |
| Блок VR у налаштуваннях | Прихований | Активний (автовизначення, режим рендерингу) |
| Wear OS companion | Включений | Не включений |
| Цільові пристрої | Телефони, планшети | VR-шоломи |

## Що ідентично

Все інше працює так само - файлові операції (копіювати, перемістити, видалити, перейменувати), сортування, обране, мережеві диски (SMB, SFTP, FTP), хмарні сховища (Google Drive, OneDrive, Dropbox), субтитри, вибір аудіодоріжки, таймер сну, слайд-шоу та всі елементи навігації.

## Як це працює

1. **На VR-шоломі:** `VrPlayerActivity` успадковує `PlayerActivity` і додає `VrOpenXrRenderManager` як шар рендерингу. Вивід ExoPlayer направляється на OpenXR Surface замість екрана телефону. Per-eye рендеринг виконується `VrStereoRenderer`.

2. **На звичайному телефоні:** Якщо VR APK запущено на телефоні без XR-рантайму, показується екран-заглушка з пропозицією встановити стандартну версію.

3. **У стандартній версії:** При виявленні 3D-контенту (SBS/OU) з'являється діалог із пропозицією встановити VR-редакцію для перегляду в шоломі.

## Підтримуваний контент

| Тип контенту | Рендеринг |
|-------------|-----------|
| SBS-відео (Side-by-Side) | Per-eye стерео |
| OU-відео (Over-Under) | Per-eye стерео |
| SBS/OU-фото | Per-eye стерео через Bitmap → GL texture |
| 2D-відео | Cinema mode (плоский віртуальний екран) |
| 2D-фото | Cinema mode |
| Аудіо | Стандартне відтворення (успадковане) |

## Дистрибуція

| Магазин | Платформа | Збірка |
|---------|-----------|--------|
| Meta Horizon Store | Quest 3 / Quest Pro / Quest 2 | `assembleVrRelease` |
| Google Play | Пристрої Android XR | `bundleVrRelease` (AAB) |

Ім'я пакета: `com.sza.fastmediasorter.vr`

## Команди збірки

```powershell
# Debug
.\scripts\builders\build-vr-debug.ps1

# Debug + встановлення на Quest через ADB
.\scripts\builders\build-vr-device.ps1

# Release APK (Meta Horizon Store)
.\scripts\builders\build-vr-release.ps1

# Release AAB + APK (Google Play / Android XR)
.\scripts\builders\build-vr-aab.ps1

# Gradle безпосередньо
.\gradlew.bat assembleVrDebug
.\gradlew.bat assembleVrRelease
.\gradlew.bat bundleVrRelease
```

## Технічні обмеження

- **Підтримувана ABI:** тільки `arm64-v8a`. Meta Quest 2/3/Pro та гарнітури Android XR - виключно 64-бітний ARM; для VR-флейворів не збираються шари `armeabi-v7a` чи `x86_64`.
- **Мінімальний Android:** API 26 (Android 8.0). Quest 2 ≈ Android 10, Quest 3 ≥ Android 12.
- **XR-рантайм:** на запуску потрібен OpenXR 1.1.48+. Без XR-рантайму додаток показує екран-заглушку і не стартує відтворення.
- **Нативний код:** постачається OpenXR loader AAR плюс `openxr_native.so` (C++ міст, зібраний CMake). Додає ~8 МБ нативного навантаження поверх стандартної збірки.
- **Немає Wear OS companion:** у шоломів немає парного годинника, тому `SUPPORT_WEAR_COMPANION = false`.
- **Package ID:** `com.sza.fastmediasorter.vr` спільний для обох флейворів `vr` та `vrUnlicensed` - встановлення одного замінює інший на пристрої.
- **DTS / розширені кодеки:** завжди вшиті через `fms-ffmpeg-dts.aar`. Hardware Quest скрізь arm64, тож single-ABI AAR достатньо кожному VR-користувачу.

### Канали дистрибуції

Обидва флейвори дають однаковий `applicationId`, тому одночасно на пристрої може стояти лише один:

- **`vr`** - Meta Horizon Store / Google Play. Модерована дистрибуція. Якщо магазин відхилить DTS-декодер, збірка `vr` може вийти без нього, а користувачів sideload-каналу спрямовують на `vrUnlicensed`.
- **`vrUnlicensed`** - ADB sideload через Developer Mode. Пряма дистрибуція для досвідчених користувачів; завжди містить DTS незалежно від політики магазинів.

### Fallback на телефоні

Якщо VR APK запущено на звичайному телефоні (XR-рантайм не виявлено), `VrPlayerActivity` показує статичний екран-заглушку з пропозицією встановити стандартну редакцію. Відтворення не стартує - VR-збірка не розрахована на плоский екран.

## Пов'язана документація

- [Встановлення VR через sideload](VR_SIDELOAD_UK.md) - як встановити VR APK на Quest без магазину
- [Функції](FEATURES_UK.md) - повний перелік функцій
- [Архітектура](ARCHITECTURE.md) - огляд архітектури проекту
