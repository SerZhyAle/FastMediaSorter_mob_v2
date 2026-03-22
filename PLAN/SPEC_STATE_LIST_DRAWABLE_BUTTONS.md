# SPEC: StateListDrawable (Селекторы состояний) для кнопок

**Дата**: 2026-03-22
**Цель**: Добавить визуальную подсветку нажатия (pressed state) для всех интерактивных кнопок командных панелей через `StateListDrawable` / color-selector tint.

---

## Существующий паттерн (эталон)

Проект уже имеет два готовых инструмента:

### 1. `selector_player_button_tint.xml` — Color Tint Selector
```xml
<!-- app_v2/src/main/res/color/selector_player_button_tint.xml -->
<!-- Pressed: красный, Default: белый -->
app:tint="@color/selector_player_button_tint"
```

### 2. `ic_next_circle_selector.xml` — Drawable Selector
```xml
<!-- app_v2/src/main/res/drawable/ic_next_circle_selector.xml -->
<!-- Pressed: ic_next_circle_red_pressed, Default: ic_next_circle_red -->
android:src="@drawable/ic_next_circle_selector"
```

**В большинстве случаев достаточно просто добавить:**
```xml
app:tint="@color/selector_player_button_tint"
```

---

## Аудит кнопок — что нужно сделать

### ✅ ГОТОВО (имеют селектор)

**`custom_player_controls.xml`** — ВСЕ кнопки покрыты:
- `btnPlayPause`, `btnNextFile`, `btnPrevFile`, `btnRepeat`
- `btnRewind10`, `btnForward30`, `btnSpeed`
- `btnAudioTrack`, `btnSubtitleTrack`, `btnPictureInPicture`

---

### 🔴 ВЫСОКИЙ ПРИОРИТЕТ

#### Файл: `activity_player_unified.xml` (ландшафтная командная панель)
~29 кнопок без селектора:

| View ID | Drawable |
|---------|----------|
| `btnSearchTextCmd` | `ic_menu_search` |
| `btnTranslateTextCmd` | `ic_translate` |
| `btnTextSettingsCmd` | `ic_book` |
| `btnCopyTextCmd` | `ic_menu_save` |
| `btnEditTextCmd` | `ic_menu_edit` |
| `btnSearchPdfCmd` | `ic_menu_search` |
| `btnTranslatePdfCmd` | `ic_translate` |
| `btnPdfTextSettingsCmd` | `ic_book` |
| `btnGoogleLensPdfCmd` | `ic_google_lens` |
| `btnSearchEpubCmd` | `ic_menu_search` |
| `btnTranslateEpubCmd` | `ic_translate` |
| `btnEpubTextSettingsCmd` | `ic_book` |
| `btnOcrEpubCmd` | `ic_ocr` |
| `btnTranslateImageCmd` | `ic_translate` |
| `btnImageTextSettingsCmd` | `ic_book` |
| `btnGoogleLensImageCmd` | `ic_google_lens` |
| `btnLyricsCmd` | `ic_microphone` |
| `btnRenameCmd` | `ic_rename` |
| `btnEditCmd` | `ic_menu_edit` |
| `btnUndoCmd` | `ic_menu_revert` |
| `btnOverflowMenu` | `ic_more_vert` |
| `btnDeleteCmd` | `ic_menu_delete` |
| `btnFavorite` | `ic_star_outline` |
| `btnShareCmd` | `ic_share` |
| `btnInfoCmd` | `ic_menu_info_details` |
| `btnFullscreenCmd` | `ic_fullscreen` |
| `btnSlideshowCmd` | `ic_media_play` |
| `btnPreviousCmd` | `ic_media_previous` |
| `btnNextCmd` | `ic_media_next` |

**Действие**: добавить `app:tint="@color/selector_player_button_tint"` к каждой.

---

#### Файл: `activity_browse.xml` (операционная панель browse)
11 кнопок/FAB без селектора:

| View ID | Drawable |
|---------|----------|
| `btnResourceAction` | `ic_edit_20` |
| `btnCopy` | `ic_menu_save` |
| `btnMove` | `ic_menu_revert` |
| `btnRename` | `ic_menu_edit` |
| `btnDelete` | `ic_menu_delete` |
| `btnUndo` | `ic_menu_revert` |
| `btnShare` | `ic_share` |
| `fabScrollToTop` | `ic_arrow_upward` |
| `fabPageUp` | `ic_double_arrow_up` |
| `fabPageDown` | `ic_double_arrow_down` |
| `fabScrollToBottom` | `ic_arrow_downward` |

**Примечание по FAB**: FloatingActionButton использует `backgroundTint` + `app:tint` — нужно проверить, поддерживает ли FAB color selector для `backgroundTint`. Если нет — только `app:tint` для иконки.

---

### 🟡 СРЕДНИЙ ПРИОРИТЕТ

#### Файл: `item_media_file.xml` (строки списка файлов)
6 кнопок:

| View ID | Drawable |
|---------|----------|
| `btnFavorite` | `ic_star_outline` |
| `btnCopyItem` | `ic_menu_save` |
| `btnMoveItem` | `ic_menu_revert` |
| `btnRenameItem` | `ic_menu_edit` |
| `btnDeleteItem` | `ic_menu_delete` |
| `btnPlayInline` | `ic_play_inline_outline` |

---

#### Файл: `item_media_file_grid_operations.xml` (грид)
4 кнопки:

| View ID | Drawable |
|---------|----------|
| `btnCopyItem` | `ic_menu_save` |
| `btnMoveItem` | `ic_menu_revert` |
| `btnRenameItem` | `ic_menu_edit` |
| `btnDeleteItem` | `ic_menu_delete` |

---

#### Файл: `player_search_panel_content.xml`
3 кнопки (имеют hardcoded `app:tint="#FFFFFF"` — нужна замена на selector):

| View ID | Действие |
|---------|----------|
| `btnSearchPrev` | заменить `#FFFFFF` → `@color/selector_player_button_tint` |
| `btnSearchNext` | заменить `#FFFFFF` → `@color/selector_player_button_tint` |
| `btnCloseSearch` | заменить `#FFFFFF` → `@color/selector_player_button_tint` |

---

#### Файл: `player_pdf_controls_overlay_content.xml` (портрет)
3 кнопки без селектора:

| View ID | Drawable |
|---------|----------|
| `btnPdfPrevPage` | `ic_media_previous` |
| `btnPdfHome` | `ic_media_rew` |
| `btnPdfNextPage` | `ic_media_next` |

---

#### Файл: `player_epub_controls_overlay_content.xml` (портрет)
3 кнопки без селектора:

| View ID | Drawable |
|---------|----------|
| `btnEpubPrevChapter` | `ic_media_previous` |
| `btnEpubHome` | `ic_media_rew` |
| `btnEpubNextChapter` | `ic_media_next` |

---

### 🟢 НИЗКИЙ ПРИОРИТЕТ

#### Файл: `player_lyrics_viewer_container.xml`
2 кнопки:

| View ID | Drawable |
|---------|----------|
| `btnTranslateLyrics` | `ic_translate` |
| `btnCloseLyricsViewer` | `ic_cancel` |

#### Файл: `dialog_translation_settings.xml`
1 кнопка:

| View ID | Drawable |
|---------|----------|
| `btnSwapLanguages` | `ic_swap_horizontal` |

---

### ⚠️ ОСОБЫЙ СЛУЧАЙ: Ландшафтные PDF/EPUB оверлеи

В ландшафтных вариантах PDF/EPUB controls кнопки используют **intentional color coding**:
- Зелёный (`#4CAF50`) — кнопки перехода (prev/next)
- Синий (`#2196F3`) — кнопки home/end

Для них нужны **кастомные селекторы** с сохранением цветов:

```xml
<!-- Пример: selector_pdf_nav_green_tint.xml -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true" android:color="#FF3333"/>
    <item android:color="#4CAF50"/>
</selector>
```

Файлы: landscape варианты `player_pdf_controls_overlay_content.xml` и `player_epub_controls_overlay_content.xml`

---

## Итог по кнопкам

| Приоритет | Кол-во кнопок | Файлы |
|-----------|--------------|-------|
| ✅ Готово | ~15 | `custom_player_controls.xml` |
| 🔴 Высокий | ~40 | `activity_player_unified.xml`, `activity_browse.xml` |
| 🟡 Средний | ~19 | item файлы, player overlay panels |
| 🟢 Низкий | 3 | lyrics, translation dialog |
| ⚠️ Особый | ~12 | ландшафтные PDF/EPUB оверлеи |
| **ИТОГО нужно** | **~74** | |

---

## Порядок реализации

1. `activity_player_unified.xml` — команд-панель (максимальная видимость)
2. `activity_browse.xml` — операции browse
3. `player_search_panel_content.xml` — конвертация hardcoded tint
4. `item_media_file.xml` + `item_media_file_grid_operations.xml`
5. PDF/EPUB portrait overlay panels
6. Ландшафтные PDF/EPUB (требуют кастомных селекторов)
7. Lyrics, translation dialog
