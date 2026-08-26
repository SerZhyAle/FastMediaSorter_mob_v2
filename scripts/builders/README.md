# Build Scripts

Build automation scripts for FastMediaSorter v2.

## Debug Builds

Quick builds for development and testing:

```powershell
.\scripts\builders\build-debug.PS1              # Standard flavor, fast reusable debug build
.\scripts\builders\build-debug.PS1 -SkipZip     # Standard flavor without zip/GDrive archive
.\scripts\builders\build-debug.PS1 -AutoVersion # Standard flavor with timestamped app version
.\scripts\builders\build-debug-clean.PS1        # Clean + standard debug + zip
.\scripts\builders\build-debug-clean.PS1 -SkipZip # Clean + standard debug without zip
.\scripts\builders\build-standard-debug.ps1     # Standard flavor explicit
.\scripts\builders\build-lite-debug.ps1         # Lite flavor (no cloud/audio)
.\scripts\builders\build-photos-debug.ps1       # Photos only flavor
.\scripts\builders\build-legacy-debug.ps1       # Legacy flavor (no cloud)
```

Fast validation helpers:

```powershell
.\scripts\builders\check-standard-fast.ps1 -Mode Code
.\scripts\builders\check-standard-fast.ps1 -Mode Resources
.\scripts\builders\check-standard-fast.ps1 -Mode CodeAndResources
.\scripts\builders\check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.SomeClassTest"
```

## Cleanup

```powershell
.\scripts\builders\clean-gradle-caches.ps1      # Stop daemons + remove project caches + gradlew clean
```

## Release Builds

Optimized builds with ProGuard (requires keystore):

```powershell
.\scripts\builders\build-release.ps1            # Standard release
.\scripts\builders\build-standard-release.ps1   # Standard explicit
.\scripts\builders\build-lite-release.ps1       # Lite release
.\scripts\builders\build-photos-release.ps1     # Photos release
.\scripts\builders\build-legacy-release.ps1     # Legacy release
.\scripts\builders\build-aab-release.ps1        # AAB bundle for Play Store
```

## VR (OpenXR)

```powershell
.\scripts\builders\build-vr-debug.ps1                    # VR debug (Quest / Android XR)    | .\a.ps1 vrd
.\scripts\builders\build-vr-release.ps1                  # VR release APK (Meta Horizon Store) | .\a.ps1 vr
.\scripts\builders\build-vr-aab.ps1                      # VR release AAB + APK (Google Play / Android XR)
.\scripts\builders\build-vr-device.ps1                   # VR debug + ADB install + auto-launch (smoke tests only - no FOCUSED)
.\scripts\builders\install-vr-debug-to-device.ps1        # Install VR debug APK, NO launch   | .\a.ps1 ivrd
.\scripts\builders\install-vr-release-to-device.ps1      # Install VR release APK, NO launch | .\a.ps1 ivr
```

## Wear OS

```powershell
.\scripts\builders\build-wear-debug.PS1         # Wear debug (or: .\a.ps1 wd)
.\scripts\builders\build-wear-release.PS1       # Wear release
```

## Device Deployment

Build + install to connected device:

```powershell
.\scripts\builders\build-debug-device.ps1
.\scripts\builders\build-standard-device.ps1
.\scripts\builders\build-lite-device.ps1
.\scripts\builders\build-photos-device.ps1
.\scripts\builders\build-legacy-device.ps1
.\scripts\builders\build-vr-device.ps1          # Quest via ADB
```

## Universal

```powershell
.\scripts\builders\build-universal.ps1          # All flavors
.\scripts\builders\build-and-push-all.ps1       # Build all + push to GDrive
```

## Output Locations

- **Debug APKs**: `app_v2/build/outputs/apk/[flavor]/debug/`
- **Release APKs**: `app_v2/build/outputs/apk/[flavor]/release/`
- **AAB Bundle**: `app_v2/build/outputs/bundle/standardRelease/` or `vrRelease/`
- **Wear APKs**: `wear/build/outputs/apk/debug/` or `release/`
- **Auto-copy**: `DOWNLOADS/` (release builds only)
- **Failure diagnostics**: `.\a.ps1 bf` prints the relevant block from the latest saved `temp/*build*.log`. For a structured, agent-readable companion use `build-failure-digest.ps1` (alias `.\a.ps1 bfd`) - it reuses the same `bf` extraction and emits a compact JSON digest (command, exit code, first actionable failure with module/flavor/file/line/message, raw-log path, verdict) plus a concise human verdict. See [`build-failure-digest.SCHEMA.md`](build-failure-digest.SCHEMA.md) for the JSON contract.

## Note

All build scripts should be run from project root:

```powershell
cd c:\GIT\FastMediaSorter_mob_v2
.\scripts\builders\build-debug.PS1
```


Last release build notes: 260302034 (2.60.3020.341)
<en-US>
• Cloud storage: Google Drive, OneDrive, Dropbox support
• Network drives: SMB, SFTP, FTP
• Resource Profiles: adaptive performance for any device
• Smart metadata sorting: by artist, album, date taken
• Sort and organize photos, videos, audio from any source
• Fixed: OneDrive "Add account" did nothing in release builds
• Long-press version number in Settings to share diagnostic logs
</en-US>
<ru-RU>
• Облачные хранилища: Google Drive, OneDrive, Dropbox
• Сетевые диски: SMB, SFTP, FTP
• Профили ресурсов: адаптивная производительность
• Сортировка по метаданным: исполнитель, альбом, дата съёмки
• Организация фото, видео, аудио из любых источников
• Исправлено: OneDrive - «Добавить аккаунт» не реагировал на нажатие
• Долгое нажатие на версию в Настройках → отправка диагностических логов
</ru-RU>
<uk>
• Хмарні сховища: Google Drive, OneDrive, Dropbox
• Мережеві диски: SMB, SFTP, FTP
• Профілі ресурсів: адаптивна продуктивність
• Сортування за метаданими: виконавець, альбом, дата зйомки
• Організація фото, відео, аудіо з будь-яких джерел
• Виправлено: OneDrive - «Додати акаунт» не реагував на натискання
• Довге натискання на версію в Налаштуваннях → надсилання діагностичних логів
</uk>
