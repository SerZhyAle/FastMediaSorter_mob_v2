# Phase 01 — Foundations

**Strategic spec:** [`../S0158_player-large-buttons.md`](../S0158_player-large-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Introduce the preference key for Big Buttons Mode into `PlayerLayoutModePrefs`, add a `shortTitleResId` field to `PlayerCommand`, and add all new string resources (toggle label, help text, short labels for top-panel bar-capable commands) in EN/RU/UK.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. _(foundation phase — none)_
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLayoutModePrefs.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 390 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 01.1 — Add `isBigButtonsMode` / `setBigButtonsMode` to `PlayerLayoutModePrefs`

**Files:** `PlayerLayoutModePrefs.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `PlayerLayoutModePrefs` (SharedPreferences file `"player_layout_mode"`), add:
> - `private const val KEY_BIG_BUTTONS_MODE = "big_buttons_mode"`
> - `fun isBigButtonsMode(context: Context): Boolean` — reads the key, default `false`
> - `fun setBigButtonsMode(context: Context, enabled: Boolean)` — writes the key
>
> Same pattern as the existing `isCompact` / `setCompact` pair. No restart is required (ADR-2: flag is read once at player init). Do not modify `applyControlsThemeOverlay`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLayoutModePrefs.kt` exists.
- `Grep` — `KEY_BIG_BUTTONS_MODE` present in that file.
- `Grep` — `fun isBigButtonsMode` present in that file.
- `Grep` — `fun setBigButtonsMode` present in that file.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerLayoutModePrefs.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 4/4 PASS. Files: PlayerLayoutModePrefs.kt (+14 LOC). Dev log recorded.

---

### Step 01.2 — Add `shortTitleResId` to `PlayerCommand` enum

**Files:** `CommandPanelLayoutPlanner.kt`
**Depends on:** — can run in parallel with Step 01.1



**Prompt for developer:**

> In `CommandPanelLayoutPlanner.PlayerCommand`, add a new field `val shortTitleResId: Int` as the last enum constructor parameter.
>
> **Rule:** `shortTitleResId = 0` means "no dedicated short label — use `titleResId` at runtime". Only bar-capable commands (group 1 and selected group 2) get non-zero values. String resource IDs are added in Step 01.3 — use the `R.string.big_btn_short_*` names listed below and assign them now (the string keys must exist by the time code compiles; add Step 01.3 before building).
>
> Non-zero assignments (group-1, priorities 5–70):
>
> | Command | shortTitleResId |
> |---------|----------------|
> | PLAYBACK_ORDER | `R.string.big_btn_short_playback_order` |
> | DELETE | `R.string.big_btn_short_delete` |
> | FAVORITE | `R.string.big_btn_short_favorite` |
> | SHARE | `R.string.big_btn_short_share` |
> | INFO | `R.string.big_btn_short_info` |
> | FULLSCREEN | `R.string.big_btn_short_fullscreen` |
> | SLIDESHOW | `R.string.big_btn_short_slideshow` |
> | RANDOM | `R.string.big_btn_short_random` |
>
> Selected group-2 bar-capable commands (priorities 195–490) that can reach the bar when group-1 is sparse for a given media type:
>
> | Command | shortTitleResId |
> |---------|----------------|
> | BLACK_SCREEN | `R.string.big_btn_short_black_screen` |
> | RENAME | `R.string.big_btn_short_rename` |
> | EDIT | `R.string.big_btn_short_edit` |
> | UNDO | `R.string.big_btn_short_undo` |
> | CAST | `R.string.big_btn_short_cast` |
> | SAVE_FRAME | `R.string.big_btn_short_save_frame` |
> | LYRICS | `R.string.big_btn_short_lyrics` |
> | ROTATION_TOGGLE | `R.string.big_btn_short_rotation` |
> | SEARCH_PDF | `R.string.big_btn_short_search` |
> | TRANSLATE_PDF | `R.string.big_btn_short_translate` |
> | PDF_TEXT_SETTINGS | `R.string.big_btn_short_text_settings` |
> | OCR_PDF | `R.string.big_btn_short_ocr` |
> | SEARCH_TEXT | `R.string.big_btn_short_search` |
> | TRANSLATE_TEXT | `R.string.big_btn_short_translate` |
> | TEXT_SETTINGS | `R.string.big_btn_short_text_settings` |
> | EDIT_TEXT | `R.string.big_btn_short_edit` |
> | COPY_TEXT | `R.string.big_btn_short_copy` |
> | SEARCH_EPUB | `R.string.big_btn_short_search` |
> | TRANSLATE_EPUB | `R.string.big_btn_short_translate` |
> | EPUB_TEXT_SETTINGS | `R.string.big_btn_short_text_settings` |
> | OCR_EPUB | `R.string.big_btn_short_ocr` |
> | TRANSLATE_IMAGE | `R.string.big_btn_short_translate` |
> | IMAGE_TEXT_SETTINGS | `R.string.big_btn_short_text_settings` |
> | OCR_IMAGE | `R.string.big_btn_short_ocr` |
> | GOOGLE_LENS_IMAGE | `R.string.big_btn_short_lens` |
>
> All remaining commands (group 3, overflow-only): `shortTitleResId = 0`.
>
> Add `shortTitleResId` as the **last** parameter so all existing call sites of the enum constructor remain valid — Kotlin enum constructors are positional, so existing entries must be updated to pass the new argument. Simplest approach: add `shortTitleResId: Int = 0` with a default value so only the entries listed above need explicit assignment.

**Verification:**

- `Grep` — `shortTitleResId` present in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `big_btn_short_playback_order` referenced in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `big_btn_short_random` referenced in `CommandPanelLayoutPlanner.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `CommandPanelLayoutPlanner.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 4/4 PASS. Files: CommandPanelLayoutPlanner.kt (+55 LOC). Dev log recorded.

---

### Step 01.3 — Add string resources (EN / RU / UK)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** — can run in parallel with Steps 01.1 and 01.2

**Prompt for developer:**

> Add the following string keys to all three locale files. Check `docs/COMMUNICATION_POLICY.md` §2 (toggle/preference message formula) and §6 (tone checklist) before committing the toggle label and help text.
>
> **Toggle label + help text** (EN / RU / UK):
>
> | Key | EN | RU | UK |
> |-----|-----|-----|-----|
> | `big_buttons_mode_title` | `Big Buttons Mode` | `Режим большие кнопки` | `Режим великі кнопки` |
> | `big_buttons_mode_summary` | `Doubles button height and expands controls to full width — ideal for car head units` | `Удваивает высоту кнопок и растягивает управление на всю ширину — удобно для автомагнитол` | `Подвоює висоту кнопок і розтягує керування на всю ширину — зручно для автомагнітол` |
> | `tooltip_big_buttons_mode_title` | `Big Buttons Mode` | `Режим большие кнопки` | `Режим великі кнопки` |
> | `tooltip_big_buttons_mode_message` | `All player control buttons expand to 2× height and full screen width. Top toolbar fits up to 8 buttons; remaining commands move to the overflow menu. In landscape, the tall bottom panel may reduce the playback area.` | `Все кнопки управления увеличиваются вдвое по высоте и занимают всю ширину экрана. На верхней панели помещается до 8 кнопок; остальные переходят в меню. В горизонтальной ориентации увеличенная нижняя панель может уменьшить область воспроизведения.` | `Усі кнопки керування збільшуються вдвічі по висоті та займають всю ширину екрана. На верхній панелі міститься до 8 кнопок; решта переходять до меню. У горизонтальній орієнтації збільшена нижня панель може зменшити область відтворення.` |
>
> **Short labels for top-panel buttons** (same key in all locales unless language-specific):
>
> | Key | EN | RU | UK |
> |-----|-----|-----|-----|
> | `big_btn_short_playback_order` | `Order` | `Порядок` | `Порядок` |
> | `big_btn_short_delete` | `Delete` | `Удалить` | `Видалити` |
> | `big_btn_short_favorite` | `Fav` | `Избр.` | `Вибр.` |
> | `big_btn_short_share` | `Share` | `Отпр.` | `Надісл.` |
> | `big_btn_short_info` | `Info` | `Инфо` | `Інфо` |
> | `big_btn_short_fullscreen` | `Full` | `Экран` | `Екран` |
> | `big_btn_short_slideshow` | `Slide` | `Слайд` | `Слайд` |
> | `big_btn_short_random` | `Random` | `Случ.` | `Вип.` |
> | `big_btn_short_black_screen` | `Dark` | `Тёмн.` | `Темн.` |
> | `big_btn_short_rename` | `Rename` | `Переим.` | `Перейм.` |
> | `big_btn_short_edit` | `Edit` | `Ред.` | `Ред.` |
> | `big_btn_short_undo` | `Undo` | `Отмена` | `Скас.` |
> | `big_btn_short_cast` | `Cast` | `Cast` | `Cast` |
> | `big_btn_short_save_frame` | `Frame` | `Кадр` | `Кадр` |
> | `big_btn_short_lyrics` | `Lyrics` | `Текст` | `Текст` |
> | `big_btn_short_rotation` | `Rotate` | `Пов.` | `Пов.` |
> | `big_btn_short_search` | `Search` | `Поиск` | `Пошук` |
> | `big_btn_short_translate` | `Transl.` | `Перев.` | `Перекл.` |
> | `big_btn_short_text_settings` | `Config` | `Настр.` | `Налаш.` |
> | `big_btn_short_ocr` | `OCR` | `OCR` | `OCR` |
> | `big_btn_short_copy` | `Copy` | `Копир.` | `Копір.` |
> | `big_btn_short_lens` | `Lens` | `Lens` | `Lens` |
>
> Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "big_btn"` and `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "big_buttons_mode"` — both must exit 0.
>
> Strings pass `COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `Grep` — `big_buttons_mode_title` present in `res/values/strings.xml`.
- `Grep` — `big_buttons_mode_title` present in `res/values-ru/strings.xml`.
- `Grep` — `big_buttons_mode_title` present in `res/values-uk/strings.xml`.
- `Grep` — `big_btn_short_random` present in `res/values/strings.xml`.
- `Grep` — `big_btn_short_random` present in `res/values-ru/strings.xml`.
- `Grep` — `big_btn_short_random` present in `res/values-uk/strings.xml`.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "big_btn"` exits 0.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "big_buttons_mode"` exits 0.
- Strings pass `COMMUNICATION_POLICY.md` §6 tone checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 9/9 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml (+26 keys each). Locale audit exits 0. Tone checklist PASS. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 01 establishes: preference key in `player_layout_mode` SharedPreferences; `PlayerCommand.shortTitleResId` field (default 0); all string resources for toggle + short labels. Phase 02 can now reference these without compile errors.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
