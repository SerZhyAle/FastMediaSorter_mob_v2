# Phase 1 — Foundation: Build & Scaffold

**Статус:** 🔴 Not started  
**Оценка:** ~3h  
**Spec-ref:** [../spec_openxr_3d_player.md](../spec_openxr_3d_player.md) — Sections 3.1, 3.3, 5.4, 7, Steps 1-3  
**Блокирует:** Phase 2, Phase 3, Phase 4 (все зависят от наличия `vr` flavor)

---

## Предусловия

- [ ] Прочитан полный спек [`spec_openxr_3d_player.md`](../spec_openxr_3d_player.md)
- [ ] Прочитан `docs/ARCHITECTURE.md` (понимание слоёв)
- [ ] Подключён Quest 3 или Quest Pro (понадобится в Phase 4, не сейчас)
- [ ] `./gradlew.bat assembleStandardDebug` проходит без ошибок (baseline проверка)

---

## Задача 1.1 — Backup больших файлов

> **Правило:** файлы > 500 строк бэкапятся в `temp/` перед любым изменением.

- [ ] **1.1.1** Проверить размер `PlayerActivity.kt`:

  ```powershell
  (Get-Content "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt").Count
  ```

- [ ] **1.1.2** Создать timestamped backup:

  ```powershell
  $ts = Get-Date -Format "yyyyMMdd_HHmm"
  Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt" `
            "temp/PlayerActivity_backup_$ts.kt"
  ```

- [ ] **1.1.3** То же для `PlayerViewModel.kt`:

  ```powershell
  Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt" `
            "temp/PlayerViewModel_backup_$ts.kt"
  ```

- [ ] **1.1.4** Проверить `PlaybackControlDialogFragment.kt`, бэкапить если > 500 строк

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "temp/" "backups" "Pre-Phase1 timestamped backups of PlayerActivity, PlayerViewModel"
```

---

## Задача 1.2 — Добавить `vr` flavor в `app_v2/build.gradle.kts`

> **Важно:** читать все существующие комментарии в `build.gradle.kts` перед редактированием.

- [ ] **1.2.1** Прочитать `app_v2/build.gradle.kts` целиком (раздел `productFlavors`)
- [ ] **1.2.2** Добавить flavor `vr` в блок `productFlavors {}`:

  ```kotlin
  vr {
      dimension = "version"
      applicationIdSuffix = ".vr"
      versionNameSuffix = "-vr"
      buildConfigField("Boolean", "SUPPORT_VR_PLAYER", "true")
  }
  ```

- [ ] **1.2.3** Убедиться, что у остальных flavors (`standard`, `lite`, `photos`, `legacy`) добавлено:

  ```kotlin
  buildConfigField("Boolean", "SUPPORT_VR_PLAYER", "false")
  ```

- [ ] **1.2.4** Добавить VR-специфичные зависимости (только для `vr` flavor):

  ```kotlin
  // OpenXR loader (official Android OpenXR loader)
  "vrImplementation"("org.khronos.openxr:openxr_loader_for_android:1.1.48")
  
  // LibVLC (primary VR backend candidate; заменить AAR-путём если Maven недоступен)
  // Примечание: LibVLC arm64 .so может потребовать pre-build шага — см. ADR-2 в спеке
  "vrImplementation"("org.videolan.android:libvlc-all:3.6.0")
  ```

- [ ] **1.2.5** Запустить сборку и убедиться, что оба flavor собираются:

  ```powershell
  .\gradlew.bat assembleVrDebug
  .\gradlew.bat assembleStandardDebug
  ```

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/build.gradle.kts" "build.gradle.kts" "Added vr product flavor, SUPPORT_VR_PLAYER BuildConfig flag, OpenXR loader and LibVLC dependencies"
```

---

## Задача 1.3 — Создать flavor source set `app_v2/src/vr/`

### 1.3.1 — Union Manifest

- [ ] Создать файл `app_v2/src/vr/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!--
      Union Manifest — содержит entries для двух store каналов.
      Все XR uses-feature объявлены с required="false" чтобы не блокировать
      установку на платформах, где фича не задекларирована (см. ADR-3 в спеке).
    -->

    <application>

        <activity
            android:name=".vr.VrPlayerActivity"
            android:configChanges="density|fontScale|keyboard|keyboardHidden|navigation|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"
            android:exported="false"
            android:resizeableActivity="false"
            android:screenOrientation="landscape"
            android:theme="@style/Theme.FastMediaSorter.VrPlayer">

            <!-- Meta Horizon Store: VR mode intent category -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="com.oculus.intent.category.VR" />
            </intent-filter>

            <!-- Google Play / Android XR: immersive entry point -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="com.google.intent.category.IMMERSIVE" />
            </intent-filter>

        </activity>

        <activity
            android:name=".vr.VrPhoneFallbackActivity"
            android:exported="false"
            android:theme="@style/Theme.FastMediaSorter" />

    </application>

    <!-- Meta Horizon Store: head tracking (required=false → не блокирует Google Play install) -->
    <uses-feature
        android:name="android.hardware.vr.headtracking"
        android:required="false"
        android:version="1" />

    <!-- Google Play / Android XR: immersive XR (required=false → не блокирует Quest install) -->
    <uses-feature
        android:name="android.hardware.xr.immersive"
        android:required="false" />

</manifest>
```

- [ ] **Проверка:** `./gradlew.bat assembleVrDebug` собирается без manifest merge ошибок

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/AndroidManifest.xml" "AndroidManifest" "Union Manifest for vr flavor: Meta Horizon Store + Google Play/Android XR entries, all XR features required=false"
```

### 1.3.2 — Структура пакетов

- [ ] Создать пустые placeholder файлы (или package-info.kt) в:
  - `app_v2/src/vr/java/com/sza/fastmediasorter/vr/`
  - `app_v2/src/vr/java/com/sza/fastmediasorter/vr/playback/`
  - `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/`
  - `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/`
  - `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/`

### 1.3.3 — Hilt module (skeleton)

- [ ] Создать `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt`:

```kotlin
package com.sza.fastmediasorter.vr.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Hilt module для VR flavor.
// Binding VrPlaybackEngine добавляется в Phase 4 после реализации движков.
@Module
@InstallIn(SingletonComponent::class)
object VrModule
```

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt" "VrModule" "Hilt module skeleton for vr flavor, VrPlaybackEngine binding to be added in Phase 4"
```

---

## Финальная проверка Phase 1

- [ ] `./gradlew.bat assembleVrDebug` — SUCCESS
- [ ] `./gradlew.bat assembleStandardDebug` — SUCCESS (регрессий нет)
- [ ] `./gradlew.bat lintVrDebug` — без новых critical errors
- [ ] `BuildConfig.SUPPORT_VR_PLAYER` равен `true` в `vr` variant и `false` в `standard`
- [ ] Manifest не содержит `required="true"` для XR features

## Gate → Phase 2

Обновить строку в [00_OVERVIEW.md](00_OVERVIEW.md): `Phase 1 | 🟢 Done`

---

## Заметки разработчика

> Заполняй по мере работы.

```
Дата начала:
Дата завершения:
Проблемы:
```
