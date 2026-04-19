# Phase 3 — Standard CTA Integration

**Статус:** 🔴 Not started  
**Оценка:** ~3h  
**Spec-ref:** [../spec_openxr_3d_player.md](../spec_openxr_3d_player.md) — Sections 5.1, 5.2, Steps 5-7  
**Блокируется:** Phase 2 (нужны shared contracts)  
**Не требует:** физического Quest-девайса — вся работа на `standard` flavor, тестируется на телефоне/эмуляторе

---

## Предусловие

- [ ] Phase 2 завершена: все 4 contract-класса компилируются
- [ ] Резервные копии `PlayerActivity.kt` и `PlayerViewModel.kt` созданы (Phase 1.1)

---

## Назначение фазы

Интегрировать VR CTA в `standard` player:

- При открытии SBS/OU файла на телефоне показывать предложение установить VR-версию
- Обычные 2D-видео открываются без изменений
- `standard` не имеет локального XR launch path

---

## Задача 3.1 — `PlayerEntryCoordinator` (реализация)

**Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt`  
**Бюджет:** ≤ 250 строк

- [ ] **3.1.1** Прочитать как сейчас `PlayerActivity` принимает intent и как `StereoDetector` вызывается при старте воспроизведения
- [ ] **3.1.2** Создать interface и реализацию:

```kotlin
interface PlayerEntryCoordinator {
    fun resolveEntry(request: PlaybackEntryRequest): PlaybackEntryDecision
}

data class PlaybackEntryRequest(
    val flavorSupportsVr: Boolean,          // BuildConfig.SUPPORT_VR_PLAYER
    val currentDeviceClass: DeviceClass,    // PHONE / TABLET / HEADSET
    val detectedStereoMode: StereoMode,     // NONE / SBS / OU / ...
    val mediaType: MediaType                // VIDEO / IMAGE / AUDIO / ...
)

sealed class PlaybackEntryDecision {
    data object OpenStandardPlayer : PlaybackEntryDecision()
    data object ShowVrInstallCta : PlaybackEntryDecision()    // standard + 3D content
    data object OpenVrPlayer : PlaybackEntryDecision()         // vr flavor + headset
    data object ShowPhoneFallbackScreen : PlaybackEntryDecision() // vr flavor + phone
}
```

- [ ] **3.1.3** Реализовать логику маршрутизации:
  - `vr` + headset → `OpenVrPlayer`
  - `vr` + phone → `ShowPhoneFallbackScreen`
  - `standard` + StereoContent → `ShowVrInstallCta`
  - `standard` + обычный контент → `OpenStandardPlayer`
  - Любой не-video тип → `OpenStandardPlayer` (CTA только для video)
- [ ] **3.1.4** Добавить `@Inject constructor` для Hilt
- [ ] **3.1.5** Написать unit tests `PlayerEntryCoordinatorTest`:
  - [ ] standard + 2D → OpenStandardPlayer
  - [ ] standard + SBS → ShowVrInstallCta
  - [ ] vr + headset + 2D → OpenVrPlayer
  - [ ] vr + phone → ShowPhoneFallbackScreen

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/PlayerEntryCoordinator.kt" "PlayerEntryCoordinator" "Entry routing: standard shows VR install CTA on 3D content; vr routes to VrPlayerActivity or phone fallback"
```

---

## Задача 3.2 — Интеграция coordinator в `standard` player entry

- [ ] **3.2.1** Прочитать в `PlayerActivity.kt` метод, который вызывается при старте воспроизведения (предположительно `onStart` или вызов из ViewModel)
- [ ] **3.2.2** Добавить вызов `PlayerEntryCoordinator.resolveEntry()` **до** запуска текущего player flow:
  - Если `ShowVrInstallCta` → не запускать ExoPlayer; показать CTA (задача 3.3)
  - Если `OpenStandardPlayer` → continue существующий flow без изменений
  - Защитить от случайного `OpenVrPlayer` / `ShowPhoneFallbackScreen` в `standard` (`BuildConfig.SUPPORT_VR_PLAYER == false` гарантирует, что эти ветки недостижимы)
- [ ] **3.2.3** Инжектировать `PlayerEntryCoordinator` через Hilt (field injection в Activity)

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt" "PlayerActivity" "Integrated PlayerEntryCoordinator at entry; CTA shown on 3D content in standard flavor"
```

---

## Задача 3.3 — CTA UI: диалог предложения VR-версии

- [ ] **3.3.1** Добавить string resources:

  `values/strings.xml`:

  ```xml
  <string name="vr_install_cta_title">This is a 3D video</string>
  <string name="vr_install_cta_message">For the best experience, try the VR edition of FastMediaSorter on your Quest headset.</string>
  <string name="vr_install_cta_action">Open in store</string>
  <string name="vr_install_cta_dismiss">Play anyway</string>
  ```

  `values-ru/strings.xml`:

  ```xml
  <string name="vr_install_cta_title">Это 3D-видео</string>
  <string name="vr_install_cta_message">Для лучшего просмотра используйте VR-версию FastMediaSorter на шлеме Quest.</string>
  <string name="vr_install_cta_action">Открыть в магазине</string>
  <string name="vr_install_cta_dismiss">Воспроизвести</string>
  ```

  `values-uk/strings.xml`:

  ```xml
  <string name="vr_install_cta_title">Це 3D-відео</string>
  <string name="vr_install_cta_message">Для кращого перегляду скористайтесь VR-версією FastMediaSorter на шоломі Quest.</string>
  <string name="vr_install_cta_action">Відкрити в магазині</string>
  <string name="vr_install_cta_dismiss">Відтворити</string>
  ```

- [ ] **3.3.2** Реализовать CTA: MaterialAlertDialog или BottomSheet в `PlayerActivity`:
  - "Open in store" → Intent на `com.sza.fastmediasorter.vr` в Play Store / Horizon Store
  - "Play anyway" → продолжить существующий standard playback flow
  - Content descriptions заданы (TalkBack accessibility)
  - Touch targets ≥ 48dp

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "VR install CTA strings: EN"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "strings-ru" "VR install CTA strings: RU"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "strings-uk" "VR install CTA strings: UK"
```

---

## Задача 3.4 — Заменить Toast-only 3D реакцию в `PlayerPlaybackCallbackImpl`

- [ ] **3.4.1** Найти в `PlayerPlaybackCallbackImpl.kt` место, где сейчас показывается Toast при детекте 3D
- [ ] **3.4.2** Заменить на event в `PlayerViewModel` → coordinator-driven CTA показывается через `PlayerActivity`
- [ ] **3.4.3** Toast удалить (или оставить как secondary notification только если CTA уже была dismissed)

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt" "PlayerPlaybackCallbackImpl" "Replaced Toast-only 3D reaction with coordinator-driven CTA event"
```

---

## Задача 3.5 — Убрать ложный XR-launch path из `PlaybackControlDialogFragment`

- [ ] **3.5.1** Проверить `PlaybackControlDialogFragment.kt` на наличие кода, который мог бы запустить XR / VR напрямую из `standard`
- [ ] **3.5.2** Если есть — удалить или заменить guard `if (!BuildConfig.SUPPORT_VR_PLAYER) return`
- [ ] **3.5.3** Убедиться что Stereo section в диалоге работает корректно как crop-mode переключатель (не как VR launcher)

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt" "PlaybackControlDialogFragment" "Removed any false local XR-launch path in standard flavor; stereo section stays as crop-mode toggle"
```

---

## Финальная проверка Phase 3

- [ ] Открыть SBS/OU файл на `standard` flavor → CTA показывается
- [ ] Тапнуть "Play anyway" → стандартный player запускается как раньше
- [ ] Открыть обычный 2D-файл → CTA не показывается
- [ ] `./gradlew.bat lintStandardDebug` — без новых warnings
- [ ] `./gradlew.bat testStandardDebugUnitTest` — все тесты проходят
- [ ] String resources присутствуют в EN, RU, UK

## Gate → Phase 5 (частично; Phase 4 параллельно)

Обновить строку в [00_OVERVIEW.md](00_OVERVIEW.md): `Phase 3 | 🟢 Done`

---

## Заметки разработчика

```
Дата начала:
Дата завершения:
Проблемы:
Что изменилось по сравнению со спеком:
```
