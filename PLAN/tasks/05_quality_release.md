# Phase 5 — Gate Review, Tests & Release Prep

**Статус:** 🔴 Not started  
**Оценка:** ~3h + QA время на Quest  
**Spec-ref:** [../spec_openxr_3d_player.md](../spec_openxr_3d_player.md) — Sections 9, 10, 11, ADR-2, Steps 11-15  
**Блокируется:** Phase 3 + Phase 4

---

## Предусловие

- [ ] Phase 3 завершена (CTA работает на standard)
- [ ] Phase 4 завершена (базовое воспроизведение на Quest работает)

---

## Задача 5.1 — Backend Gate Review (ADR-2)

> Проводится ОДИН РАЗ. Результат фиксируется ниже в этом файле.  
> Если хотя бы один из критериев срабатывает — переключить Hilt binding на `ExoVrPlaybackEngine`.

### Критерий 1 — LibVLC arm64 pre-build

- [ ] Проверить, что LibVLC arm64 .so присутствует в APK:

  ```powershell
  # Распаковать APK и проверить lib/arm64-v8a/
  $apk = "app_v2/build/outputs/apk/vr/debug/app-vr-debug.apk"
  Add-Type -A System.IO.Compression.FileSystem
  [IO.Compression.ZipFile]::OpenRead((Resolve-Path $apk)).Entries |
      Where-Object { $_.FullName -like "lib/arm64-v8a/*vlc*" } |
      Select-Object FullName, Length
  ```

- [ ] **Результат:**

  ```
  LibVLC arm64 .so найдены: ДА / НЕТ
  Дата проверки:
  ```

### Критерий 2 — Surface sync

- [ ] Тест: запустить LibVLC воспроизведение на Quest, проверить что первый кадр появляется в течение 3с
- [ ] **Результат:**

  ```
  Первый кадр: ДА (___мс) / НЕТ (зависает / crash)
  ```

### Критерий 3 — Audio latency

- [ ] Воспроизвести видео с чёткой аудио-синхронизацией (клэппербоард или похожее), проверить расхождение на Quest
- [ ] **Результат:**

  ```
  Аудио latency: ___ms (допустимо < 80ms) / ПРЕВЫШЕНО
  ```

### Решение Gate Review

- [ ] **Итог:** LibVLC остаётся primary / переключаемся на ExoPlayer

  ```
  Итоговый backend:
  Причина (если переключение):
  ```

- [ ] Если переключаемся на ExoPlayer — обновить binding в `VrModule.kt`:

  ```kotlin
  @Binds
  abstract fun bindVrPlaybackEngine(impl: ExoVrPlaybackEngine): VrPlaybackEngine
  ```

**Dev log (только если изменяли VrModule):**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt" "VrModule" "Backend gate review: switched to ExoVrPlaybackEngine as primary (reason: ___)"
```

---

## Задача 5.2 — Unit Tests (финальные)

- [ ] **5.2.1** `PlayerEntryCoordinatorTest` — если не написан в Phase 3:
  - [ ] standard + 2D → OpenStandardPlayer
  - [ ] standard + SBS → ShowVrInstallCta
  - [ ] vr + headset → OpenVrPlayer
  - [ ] vr + phone → ShowPhoneFallbackScreen
  - [ ] audio/image медиа → OpenStandardPlayer (CTA только для video)

- [ ] **5.2.2** `StereoDetectionFacadeTest`:
  - [ ] Facade корректно пробрасывает StereoMode из StereoDetector
  - [ ] `isStereoContent()` возвращает false для NONE

- [ ] **5.2.3** `PlaybackCommandModelTest`:
  - [ ] `forVrPlayback()` не содержит MoveFile, CopyFile, DeleteFile
  - [ ] `forStandardPlayback()` содержит все команды

- [ ] **5.2.4** `PlaybackPreferencesFacadeTest`:
  - [ ] Resume position сохраняется и читается корректно
  - [ ] Speed prefs без зависимости от ExoPlayer/LibVLC

- [ ] **5.2.5** `VrPlaybackEngineSelectorTest` (если нужен):
  - [ ] Fallback на ExoPlayer если LibVLC unavailable

Запустить все тесты:

```powershell
.\gradlew.bat testStandardDebugUnitTest
.\gradlew.bat testVrDebugUnitTest
```

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/test/java/com/sza/fastmediasorter/" "unit_tests" "Unit tests: PlayerEntryCoordinator, StereoDetectionFacade, PlaybackCommandModel, PlaybackPreferencesFacade"
```

---

## Задача 5.3 — String resources (финальная проверка)

- [ ] Проверить что все строки добавлены во все три locale:
  - `values/strings.xml` (EN)
  - `values-ru/strings.xml` (RU)
  - `values-uk/strings.xml` (UK)

  Строки для проверки:
  - `vr_install_cta_title`, `vr_install_cta_message`, `vr_install_cta_action`, `vr_install_cta_dismiss`
  - `vr_phone_fallback_title`, `vr_phone_fallback_message`, `vr_phone_fallback_close`, `vr_phone_fallback_open_standard`
  - Строки VR overlay команд (если добавлены в Phase 4)

- [ ] Проверить typo и стиль:

  ```powershell
  .\scripts\utils\check-typo-lint.ps1
  ```

**Dev log (если строки обновлялись в этой фазе):**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "Final string resources check: VR CTA + phone fallback strings in EN/RU/UK"
```

---

## Задача 5.4 — Feature docs update

- [ ] Добавить bullet в `docs/FEATURES.md` (EN):

  ```
  - 3D video detection can recommend the dedicated VR edition for headset playback,
    while the VR edition uses a simplified cinema-style player for 2D and stereoscopic video.
  ```

- [ ] Добавить bullet в `docs/FEATURES_RU.md` (RU):

  ```
  - При обнаружении 3D-видео стандартная версия может предложить отдельную VR-редакцию
    для шлемов, а VR-версия использует упрощённый cinema-style player для 2D и stereoscopic видео.
  ```

- [ ] Добавить bullet в `docs/FEATURES_UK.md` (UK):

  ```
  - При виявленні 3D-відео стандартна версія може запропонувати окрему VR-редакцію
    для шоломів, а VR-версія використовує спрощений cinema-style player для 2D і stereoscopic відео.
  ```

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES.md" "Added VR edition CTA and cinema-mode player description"
.\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "FEATURES_RU.md" "Added VR edition CTA and cinema-mode player description (RU)"
.\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "FEATURES_UK.md" "Added VR edition CTA and cinema-mode player description (UK)"
```

---

## Задача 5.5 — Manual QA Matrix

> Заполнить вручную. Каждая строка — отдельная проверка.

| # | Сценарий | Платформа | Ожидаемый результат | Статус | Дата |
|---|----------|-----------|---------------------|--------|------|
| 1 | 2D-видео на `standard` | Телефон | Player открывается без VR CTA | ⬜ | — |
| 2 | SBS-файл на `standard` | Телефон | VR install CTA показывается | ⬜ | — |
| 3 | OU-файл на `standard` | Телефон | VR install CTA показывается | ⬜ | — |
| 4 | "Play anyway" в CTA | Телефон | Стандартный player запускается | ⬜ | — |
| 5 | "Open in store" в CTA | Телефон | Открывается Play Store / Horizon Store listing | ⬜ | — |
| 6 | `vr` APK на телефоне | Телефон | VrPhoneFallbackActivity показывается | ⬜ | — |
| 7 | 2D-видео на Quest — `vr` | Quest 3 | Cinema-mode VR player | ⬜ | — |
| 8 | SBS-файл на Quest — `vr` | Quest 3 | Per-eye stereoscopic rendering | ⬜ | — |
| 9 | OU-файл на Quest — `vr` | Quest 3 | Per-eye stereoscopic rendering | ⬜ | — |
| 10 | Overlay на Quest | Quest 3 | Overlay появляется по Menu | ⬜ | — |
| 11 | Play/Pause через overlay | Quest 3 | Работает | ⬜ | — |
| 12 | Exit через overlay | Quest 3 | Возврат в Horizon Home | ⬜ | — |
| 13 | Fallback backend (ExoPlayer) | Quest 3 | Воспроизведение работает | ⬜ | — |
| 14 | TalkBack — CTA кнопки | Телефон | Content descriptions читаются | ⬜ | — |
| 15 | Нет XR runtime | Телефон с `vr` APK | Graceful fallback, не crash | ⬜ | — |

Легенда: ⬜ не проверено / ✅ OK / ❌ Ошибка / 🟡 Частично

---

## Задача 5.6 — Lint и финальная сборка

- [ ] Lint без новых warnings:

  ```powershell
  .\gradlew.bat lintStandardDebug
  .\gradlew.bat lintVrDebug
  ```

- [ ] Release сборки:

  ```powershell
  .\gradlew.bat assembleVrRelease    # APK → Meta Horizon Store
  .\gradlew.bat bundleVrRelease      # AAB → Google Play
  .\gradlew.bat assembleStandardRelease  # регрессия не допускается
  ```

---

## Итоговый чеклист Release

- [ ] Все unit tests проходят (`testStandardDebugUnitTest`, `testVrDebugUnitTest`)
- [ ] Manual QA matrix заполнен: все критические пункты ✅
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` обновлены
- [ ] String resources: все строки в EN/RU/UK
- [ ] `dev/CHANGELOG.md` обновлён для всех изменённых файлов
- [ ] Backend gate review результат зафиксирован (задача 5.1)
- [ ] `assembleVrRelease` и `bundleVrRelease` собираются без ошибок

## Gate → Готово к store submission

Обновить строку в [00_OVERVIEW.md](00_OVERVIEW.md): `Phase 5 | 🟢 Done`

---

## Заметки разработчика

```
Дата начала:
Дата завершения:
Итог backend gate review: LibVLC / ExoPlayer
Известные quirks на Quest:
Проблемы при store submission:
```
