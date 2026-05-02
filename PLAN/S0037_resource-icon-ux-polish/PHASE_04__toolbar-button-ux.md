# S0037 PHASE 04 — Toolbar Icon Button UX (П5)

**Статус:** ✅ Done  
**Completed:** 2026-04-30  
**Проблема:** Кнопка иконки ресурса в тулбаре AddResourceActivity/редактора почти невидима на тёмном тулбаре — нет подложки, иконка сливается с фоном.  
**Риск:** Низкий. Только изменение XML layout и новый drawable. Функциональность кнопки не меняется.

---

## Step Log

- 2026-04-30 — Step 4.1 PASS: created `bg_icon_button_dark.xml` (oval, #CC000000). Step 4.2 PASS: `toolbar_icon_action.xml` rewritten — `android:background=@drawable/bg_icon_button_dark`, `android:foreground=?attr/selectableItemBackgroundBorderless`, removed `app:tint`. Dev log recorded for both files.

---

## Затронутые файлы

| Файл | Изменение |
|------|-----------|
| `app_v2/src/main/res/drawable/bg_icon_button_dark.xml` | Новый файл: тёмный полупрозрачный круг |
| `app_v2/src/main/res/layout/toolbar_icon_action.xml` | Добавить подложку, ripple через `foreground` |

---

## Шаги

### Step 4.1 — Создать bg_icon_button_dark.xml

**Файл:** `app_v2/src/main/res/drawable/bg_icon_button_dark.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Dark semi-transparent circular background for the pick-icon button in the toolbar.
     Provides contrast so the icon is visible regardless of toolbar color (S0037 P5). -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#CC000000" />
</shape>
```

`#CC000000` = чёрный 80% opacity — достаточно для контраста, не перебивает Material3 цветовую схему тулбара.

### Step 4.2 — Обновить toolbar_icon_action.xml

**Файл:** `app_v2/src/main/res/layout/toolbar_icon_action.xml`

Текущее состояние (ImageButton в одну строку):
```xml
<ImageButton ... android:background="?attr/selectableItemBackgroundBorderless"
    android:padding="8dp"
    app:tint="?attr/colorOnPrimary"
    android:visibility="gone" />
```

Изменения:
1. `android:background` → `@drawable/bg_icon_button_dark` (тёмная подложка-круг)
2. `android:foreground` → `?attr/selectableItemBackgroundBorderless` (ripple поверх)
3. Убрать `app:tint="?attr/colorOnPrimary"` — иконка видна на тёмном фоне без дополнительного tint

**Итоговый файл:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Reusable pick-icon action button. Include at the right edge of a toolbar or action bar.
     Access the view as binding.btnPickIcon (no include-id needed). -->
<ImageButton xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/btnPickIcon"
    android:layout_width="40dp"
    android:layout_height="40dp"
    android:background="@drawable/bg_icon_button_dark"
    android:foreground="?attr/selectableItemBackgroundBorderless"
    android:contentDescription="@string/cd_pick_resource_icon"
    android:padding="8dp"
    android:scaleType="fitCenter"
    android:src="@drawable/ic_folder"
    android:visibility="gone" />
```

**Почему `foreground` для ripple:** `android:background` занят drawable-подложкой. Ripple эффект прикрепляется через `android:foreground` — стандартный паттерн Material3 для кнопок с кастомными фонами. `selectableItemBackgroundBorderless` = круглый ripple без границ.

---

## Verification

1. Открыть AddResourceActivity → видеть кнопку иконки в тулбаре — тёмный кружок с иконкой поверх. Видна на любом цвете тулбара (светлый/тёмный).
2. Нажать кнопку → ripple эффект срабатывает (полупрозрачный ripple поверх тёмного круга).
3. Убедиться что кнопка отображается с кастомной иконкой ресурса после выбора (функциональность не изменилась).

---

## Dev Log

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/drawable/bg_icon_button_dark.xml" "bg_icon_button_dark" "S0037 P5: new dark circular background for toolbar pick-icon button"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/toolbar_icon_action.xml" "btnPickIcon" "S0037 P5: use dark bg + foreground ripple, remove tint"
```
