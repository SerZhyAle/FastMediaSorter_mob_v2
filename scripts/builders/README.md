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

`build-vr-release.ps1` is the only VR builder here. Debug builds, the AAB and installing all go
through Gradle and `scripts/devtest/adb.ps1` - `adb.ps1 install -Flavor` has no `vr` value, so the
APK is named explicitly.

```powershell
.\scripts\builders\build-vr-release.ps1                  # VR release APK (Meta Horizon Store) | .\a.ps1 vr
.\gradlew.bat assembleVrDebug                            # VR debug (Quest / Android XR)
.\gradlew.bat bundleVrRelease                            # VR release AAB (Google Play / Android XR)
.\scripts\devtest\adb.ps1 install -Apk app_v2\build\outputs\apk\vr\debug\FastMediaSorter_vr_debug_v<version>.apk
```

Install only - do not auto-launch a VR build over ADB. That skips the vrshell launch_id path, so the
Activity never reaches FOCUSED and immersive entry cannot be judged; launch from the headset library.

## Wear OS

```powershell
.\scripts\builders\build-wear-debug.PS1         # Wear debug (or: .\a.ps1 wd)
.\scripts\builders\build-wear-release.PS1       # Wear release
```

## Native decoder extensions (media3)

Two media3 decoder extensions are built from source and shipped as local AARs. Neither can be
fetched instead: `androidx.media3` publishes no decoder extension artifact on Google Maven at any
version - its group index lists `media3-decoder` and nothing else.

Both are pinned to the media3 version the app links against. **Raising the media3 pin invalidates
both AARs** - they must be rebuilt from the matching source tree, or the renderer ABI diverges.

```powershell
.\scripts\builders\build-ffmpeg-dts-wsl.ps1               # FFmpeg audio: DTS, APE, WMA, WavPack, TTA, DSD
pwsh -NoProfile -File .\scripts\builders\compile-vp9-classes.ps1   # VP9 step 1: the Java half
wsl bash scripts/builders/build-libvpx-vp9.sh /mnt/<drive>/<path to checkout>   # VP9 step 2: native + AAR
```

| | FFmpeg DTS | libvpx VP9 |
|---|---|---|
| Artifact | `app_v2/libs/fms-ffmpeg-dts.aar` | `app_v2/libs/fms-vpx.aar` |
| Source pin | media3 1.2.1 | media3 1.2.1 + libvpx `v1.8.0` |
| Builder | `build-ffmpeg-dts.sh` | `build-libvpx-vp9.sh` |
| ABIs | four | four |
| Ticket | `PLAN/spec_ffmpeg-custom-build-dts.md` | S1126 |

Why VP9 takes two commands and DTS takes one: the DTS builder lifts `classes.jar` out of the
prebuilt `media3-decoder-ffmpeg` AAR sitting in the Gradle cache, and no such artifact exists for
VP9, so its five Java sources are compiled here. That compile runs on the Windows side because the
WSL guest has neither a JDK nor a Linux Android SDK.

Both builders verify 16 KB LOAD-segment alignment (`-Wl,-z,max-page-size=16384`) inside the build
and fail rather than emit a slice Play would reject. The AAR is wired per flavor in
`app_v2/build.gradle.kts`; `lite` and `photos` receive neither, since they ship no video path.

## Device Deployment

Build + install to connected device:

```powershell
.\scripts\builders\build-debug-device.ps1
.\scripts\builders\build-standard-device.ps1
.\scripts\builders\build-lite-device.ps1
.\scripts\builders\build-photos-device.ps1
.\scripts\builders\build-legacy-device.ps1
.\scripts\builders\build-nolegal-device.ps1
```

Every one of them takes `-DeviceId <serial>`, defaulting to `ANDROID_SERIAL`:

```powershell
.\scripts\builders\build-standard-device.ps1 -DeviceId RFCR110NBQJ
```

- `build-standard-device.ps1` rebuilds everything from scratch (S3094 reuse-disabling flags), so it
  measured `BUILD SUCCESSFUL in 4m 51s` on 2026-09-18 with 49 of 49 tasks executed. Minutes of
  silence under a named task - `mergeExtDex`, `ksp`, `compileKotlin`, `dexBuilder` - is the normal
  shape of that run, not a stall. A heartbeat names the running task every 60 silent seconds, and a
  run past the 45-minute ceiling is stopped with exit 124 (S3290, `docs/DEV_OPS.md`).
- With several devices online and no serial given, a watch is ignored and the single remaining
  phone-class device is used; anything less clear-cut refuses and names every online id.
- A failed install or launch ends the script with that `adb` call's exit code. Before S3169 the
  builders called `adb` with no serial, so a paired watch made every step fail while the script
  still printed its success line and exited 0.
- Each device build ends by writing one bounded logcat snapshot of the install-and-launch window to
  `temp/logcat_<flavor>_<stamp>.log` and starts no background capture. Before S3297 the same five
  scripts left an `adb logcat` stream running per build - eight at once on 2026-09-18, 1.8 GB on
  disk - and the stream began after `am start`, so it never held the launch it was there to record.
  A capture spanning a whole scenario is still the job of the test flow that owns the scenario.

VR has no build+install script - see the VR section above.

## Universal

```powershell
.\scripts\builders\build-universal.ps1          # All flavors
.\scripts\builders\build-and-push-all.ps1       # Build all + push to GDrive
```

## Output Locations

- **Debug APKs**: `app_v2/build/outputs/apk/[flavor]/debug/`
- **Release APKs**: `app_v2/build/outputs/apk/[flavor]/release/`
- **AAB Bundle**: `app_v2/build/outputs/bundle/standardRelease/` or `vrRelease/`
- **Wear APKs**: `wear/build/outputs/apk/<flavor>/debug/` or `.../<flavor>/release/`, where `<flavor>` is `standard` (default) or `noLegal` (S2090)
- **Auto-copy**: `DOWNLOADS/` (release builds only)
- **Failure diagnostics**: `.\a.ps1 bf` prints the relevant block from the latest saved `temp/*build*.log`. For a structured, agent-readable companion use `build-failure-digest.ps1` (alias `.\a.ps1 bfd`) - it reuses the same `bf` extraction and emits a compact JSON digest (command, exit code, first actionable failure with module/flavor/file/line/message, raw-log path, verdict) plus a concise human verdict. See [`build-failure-digest.SCHEMA.md`](build-failure-digest.SCHEMA.md) for the JSON contract.

## Note

All build scripts should be run from project root:

```powershell
cd <your FastMediaSorter_mob_v2 checkout>
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
