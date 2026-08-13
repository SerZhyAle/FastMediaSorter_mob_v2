# S0037 PHASE 01 — Badge Size Increase (П4)

**Статус:** ✅ Done  
**Completed:** 2026-04-30  
**Проблема:** 4 символа типа ресурса в углу плашки слишком мелкие — увеличить в 1.5×  
**Риск:** Минимальный — только dimens.xml. Inset пересчитывается автоматически через dimens.

---

## Step Log

- 2026-04-30 — Step 1.1 PASS: dimens.xml line 653 → 27dp (grep confirmed). Step 1.2 PASS: ResourceIconComposer.kt:47-53 reads `R.dimen.resource_icon_badge_size` → no code change needed; inset auto-recomputes. Dev log recorded.

---

## Затронутые файлы

| Файл | Изменение |
|------|-----------|
| `app_v2/src/main/res/values/dimens.xml` | `resource_icon_badge_size`: 18dp → 27dp |
| `app_v2/src/main/java/.../ui/icon/ResourceIconComposer.kt` | Только визуальная проверка, no edit expected |

---

## Шаги

### Step 1.1 — Изменить dimens.xml

**Файл:** `app_v2/src/main/res/values/dimens.xml`  
**Строка ~653:**

Было:
```xml
<dimen name="resource_icon_badge_size">18dp</dimen>
```

Стало:
```xml
<dimen name="resource_icon_badge_size">27dp</dimen>
```

### Step 1.2 — Проверить inset логику в ResourceIconComposer

**Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/icon/ResourceIconComposer.kt`

Текущая логика (не редактировать, только проверить):
```kotlin
val badgePx = context.resources.getDimensionPixelSize(R.dimen.resource_icon_badge_size)
val totalPx = context.resources.getDimensionPixelSize(R.dimen.resource_icon_composite_size)
val inset = totalPx - badgePx
composite.setLayerInset(0, 0, 0, badgePx / 2, badgePx / 2)   // theme icon
composite.setLayerInset(1, inset, inset, 0, 0)                  // badge
```

После замены (xxhdpi, 3× density):
- `badgePx` = 27dp × 3 = 81px
- `totalPx` = 48dp × 3 = 144px
- `inset` = 144 − 81 = 63px (21dp) → badge смещается на 21dp от краёв (ранее 30dp)
- Layer 0 right/bottom inset = 81/2 = 40px (13.5dp) → тема-иконка обрезается 13.5dp снизу/справа

**Критерий OK:** badge занимает правый нижний угол без обрезки центра основной иконки. При 48dp composite и 27dp badge: badge = 56% от composite (vs 38% сейчас) — визуально заметный, не перекрывает центр.

Если при тестировании badge перекрывает лицо иконки — уменьшить до 24dp (50%, промежуточный вариант).

---

## Verification

```
grep -n "resource_icon_badge_size" app_v2/src/main/res/values/dimens.xml
# ожидаем: 27dp
```

Визуальная проверка: запустить `assembleStandardDebug`, открыть главный экран — badge в углу плашки должен быть в 1.5× крупнее.

---

## Dev Log

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/dimens.xml" "resource_icon_badge_size" "S0037 P4: badge size 18dp -> 27dp (1.5x increase)"
```
