# Task 1: Default Slideshow Mode for Audio and Video Resources

## Objective
Automatically enable Slideshow mode (sequential file playback) by default when the Player is launched for Audio Library or Video Library resources.

---

## Context & Current Behavior

Сейчас Slideshow mode активируется пользователем вручную. При открытии Player для любого ресурса `isSlideShowActive = false` по умолчанию. Задача — изменить поведение инициализации **только** для ресурсов с профилем `ResourceProfile.AUDIO_LIBRARY` и `ResourceProfile.VIDEO_LIBRARY`.

---

## Affected Files

| File | Role |
|------|------|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Slideshow state: `isSlideShowActive`, `setSlideshowActive()` |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt` | `ResourceProfile` enum: `AUDIO_LIBRARY`, `VIDEO_LIBRARY` |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Entry point, передаёт параметры в ViewModel при запуске |

---

## Requirements

### R1 — Автоактивация Slideshow при запуске Player

- **Trigger:** Player открывается (Activity `onCreate` / ViewModel инициализируется).
- **Condition:** `resourceProfile == ResourceProfile.AUDIO_LIBRARY || resourceProfile == ResourceProfile.VIDEO_LIBRARY`
- **Action:** При выполнении условия вызвать `viewModel.setSlideshowActive(true)` **до первого рендера UI** (до первого наблюдения состояния из View).
- **Scope:** Только при старте сессии воспроизведения. Если пользователь сам отключил Slideshow (`toggleSlideshow()`) — не активировать повторно при переключении файлов.

### R2 — Сохранение пользовательского управления

- Пользователь должен иметь возможность отключить и снова включить Slideshow через стандартный UI-тоггл (`toggleSlideshow()`) без ограничений.
- Флаг "был ли Slideshow активирован автоматически" отдельно хранить **не нужно** — достаточно того, что при следующем запуске Player флаг снова выставляется в `true`.
- Состояние `isSlideShowActive` **не персистируется** между сессиями (не пишется в DataStore). Каждый запуск Player начинается с дефолтного значения, которое для AUDIO_LIBRARY/VIDEO_LIBRARY теперь равно `true`.

### R3 — Ресурсы без AUDIO_LIBRARY / VIDEO_LIBRARY

- Для всех остальных `ResourceProfile` поведение не изменяется: `isSlideShowActive = false` по умолчанию.

---

## Implementation Plan

1. **Locate init block in `PlayerViewModel`** — найти место, где ViewModel принимает параметры запуска (через `SavedStateHandle` или через Intent extras).
2. **Read `resourceProfile`** — убедиться, что тип ресурса доступен в ViewModel на момент инициализации.
3. **Add conditional auto-activation:**
   ```kotlin
   // PlayerViewModel.kt — init block or init function
   if (resourceProfile == ResourceProfile.AUDIO_LIBRARY ||
       resourceProfile == ResourceProfile.VIDEO_LIBRARY) {
       setSlideshowActive(true)
   }
   ```
4. **Unit test:** покрыть сценарии: AUDIO_LIBRARY → slideshow=true, VIDEO_LIBRARY → slideshow=true, PHOTO_STORAGE → slideshow=false.

---

## Edge Cases & Risks

- **Deep linking / resume from background:** проверить, что повторный `onCreate` (например, после смерти процесса и восстановления) тоже корректно выставляет флаг.
- **Inline playback vs Player screen:** убедиться, что автоактивация **не** затрагивает inline воспроизведение прямо в Browse-списке. Slideshow в Browse — отдельная логика.
- **ViewModel scope:** если ViewModel шарится между несколькими экранами — проверить, что авто-активация не мешает другим экранам.

