# Task 7: Player Control Buttons Layout Reorganization

## Objective
Переставить кнопки управления в нижней панели проигрывателя: до `PrevFile | Rewind | Play | Forward | NextFile`, а также увеличить отступы между пятиркой центральных кнопок и остальными элементами.

---

## Context & Current State

**XML файл:** `app_v2/src/main/res/layout/custom_player_controls.xml`

**Текущий порядок** (LinearLayout, horizontal, center gravity):

| # | ID | Размер |
|---|----|---------|
| 1 | `exo_repeat` | small |
| 2 | `btnRewind10` | small, marginStart |
| 3 | **`exo_prev_file`** | **medium, marginStart** |
| 4 | `exo_play_pause` | large, marginHorizontal |
| 5 | **`exo_next_file`** | **medium** |
| 6 | `btnForward30` | small, marginStart |
| 7 | `exo_speed` | small, marginStart |
| 8 | `btnAudioTrack` | small, marginStart, gone |
| 9 | `btnSubtitleTrack` | small, marginStart, gone |
| 10 | `btnPictureInPicture` | small, marginStart |

**Целевой порядок:**

| # | ID | Примечание |
|---|----|--------------|
| 1 | `exo_repeat` | без изменений |
| 2 | **`exo_prev_file`** | ← перемещён |
| 3 | **`btnRewind10`** | ← перемещён |
| 4 | `exo_play_pause` | центр, без изменений |
| 5 | **`btnForward30`** | ← перемещён |
| 6 | **`exo_next_file`** | ← перемещён |
| 7 | `exo_speed` | без изменений |
| 8 | `btnAudioTrack` | без изменений |
| 9 | `btnSubtitleTrack` | без изменений |
| 10 | `btnPictureInPicture` | без изменений |

---

## Affected Files

| File | Role |
|------|------|
| `app_v2/src/main/res/layout/custom_player_controls.xml` | **Единственный файл для редактирования** |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ExoPlayerControlsManager.kt` | Проверить: ссылки на `btnRewind10`, `btnForward30` по ID |

---

## Requirements

### R1 — Перестановка порядка в XML

Изменить порядок `ImageButton` в `custom_player_controls.xml`:

**Старый порядок:**`exo_repeat` → `btnRewind10` → `exo_prev_file` → `exo_play_pause` → `exo_next_file` → `btnForward30` → ...

**Новый порядок:**`exo_repeat` → **`exo_prev_file`** → **`btnRewind10`** → `exo_play_pause` → **`btnForward30`** → **`exo_next_file`** → ...

Для этого достаточно переставить блоки `<ImageButton>` местами в XML. ID, поведение, стили, drawable — не менять.

### R2 — Увеличить отступы между центральными 5 кнопками и вторичными

- Между `exo_repeat` и `exo_prev_file` — добавить `android:layout_marginEnd` или Space-View.
- Между `exo_next_file` и `exo_speed` — добавить `android:layout_marginStart` на `exo_speed`.
- Значение: использовать новую dimen-переменную (`@dimen/player_group_separator_margin`) или удвоенное значение `player_button_margin_start`.

### R3 — Нетронуть поведение

- **Никаких изменений в Java/Kotlin**. Все click listeners `btnRewind10`, `btnForward30`, `exo_prev_file`, `exo_next_file` в `ExoPlayerControlsManager.kt` назначены по ID — им безразлично, так как ID не меняются.
- Интервал rewind/forward, скорость плейбека, состояния `gone`/`visible` кнопок — не менять.

---

## Implementation Plan

1. **Резервная копия**: сохранить `custom_player_controls.xml` в `temp/` с timestamp.

2. **Редактиповать `custom_player_controls.xml`**:
   - Вырезать `btnRewind10` блок из позиции 2.
   - Вставить `btnRewind10` блок после `exo_prev_file` (позиция 3).
   - Аналогично для `btnForward30`/`exo_next_file`.
   - На `exo_prev_file` добавить `android:layout_marginStart="@dimen/player_group_separator_margin"` (отделяет от `exo_repeat`).
   - На `exo_speed` добавить/увеличить `android:layout_marginStart` (отделяет от `exo_next_file`).
   - `btnRewind10` и `btnForward30` могут сохранить прежний `marginStart` — ближнее к play/pause.

3. **Добавить dimen**: в `app_v2/src/main/res/values/dimens.xml` (или аналог):
   ```xml
   <dimen name="player_group_separator_margin">20dp</dimen>
   ```
   Подобрать значение по визуальному результату в Layout Editor.

4. **Проверить `ExoPlayerControlsManager.kt`**: все биндинги по ID — ID не меняются, поэтому код в котором не надо править.

5. **Визуальная проверка**: запустить Layout Editor или приложение на устройстве. Проверить: нажатие btnRewind10 мотает вназад, btnForward30 — вперёд (функционально).

---

## Edge Cases & Risks

- **ExoPlayer custom controls**: `app:controller_layout_id="@layout/custom_player_controls"` в `activity_player_unified.xml`. Перестановка View внутри не затрагивает ExoPlayer логику — только доп. ко визуальному порядку кнопок.
- **RTL-локализация**: проверить `android:layoutDirection` — если не поддерживается, в приоритете LTR.
- **Таблет в landscape**: на маленьких экранах все 10 кнопок могут не поместиться. Убедиться: нажаточные `btnAudioTrack`, `btnSubtitleTrack`, `btnPictureInPicture` по умолчанию `gone`, не занимают места.
- **Интервал rewind/forward**: `btnRewind10` = 10с, `btnForward30` = 30с (anhадимо по именам кнопок). Изменение интервала в задачу не входит.

