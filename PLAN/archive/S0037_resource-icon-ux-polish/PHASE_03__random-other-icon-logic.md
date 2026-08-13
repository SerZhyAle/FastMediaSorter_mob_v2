# S0037 PHASE 03 — Random Other Icon Logic (П2)

**Статус:** ✅ Done  
**Completed:** 2026-04-30  
**Проблема:** При первой инсталляции все ресурсы категории «Other» получают одинаковый ico-05-001 («шарик»). Должна назначаться случайная из 20.  
**Риск:** Низкий — только изменение алгоритма выбора id, без API breaking change. `randomIdFor` уже существует в `ResourceIconRegistry`.

---

## Step Log

- 2026-04-30 — Step 3.1 PASS: imports added (`ResourceIconRegistry`, `ResourceIconSet`); `invoke` now branches on `SET_OTHER` → `randomIdFor`; `resolveForProfileChange` fallback uses `randomIdFor`. Step 3.2 PASS: `AddResourceActivity.kt:306` switched from `firstIdFor` to `randomIdFor`. Grep confirms 3 call sites. Dev log recorded.

---

## Затронутые файлы

| Файл | Изменение |
|------|-----------|
| `.../domain/usecase/ResolveResourceIconUseCase.kt` | Вызывать `randomIdFor` для SET_OTHER вместо `"ico-%02d-001"` |
| `.../ui/addresource/AddResourceActivity.kt` | Строка 306: `firstIdFor` → `randomIdFor` |

---

## Шаги

### Step 3.1 — ResolveResourceIconUseCase.kt: добавить import и изменить логику

**Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveResourceIconUseCase.kt`

**Import — добавить** (в блок imports, после существующих):
```kotlin
import com.sza.fastmediasorter.ui.icon.ResourceIconRegistry
import com.sza.fastmediasorter.ui.icon.ResourceIconSet
```

**Метод `invoke` — изменить возврат:**

Было:
```kotlin
        return "ico-%02d-001".format(setId)
```

Стало:
```kotlin
        // For "Other" set, pick a random icon so each new resource looks distinct
        return if (setId == SET_OTHER) ResourceIconRegistry.randomIdFor(ResourceIconSet.OTHER)
               else "ico-%02d-001".format(setId)
```

**Метод `resolveForProfileChange` — изменить fallback:**

Было:
```kotlin
    fun resolveForProfileChange(profile: ResourceProfile, type: ResourceType): String =
        invoke(path = "", profile = profile, type = type) ?: "ico-%02d-001".format(SET_OTHER)
```

Стало:
```kotlin
    fun resolveForProfileChange(profile: ResourceProfile, type: ResourceType): String =
        // Fallback to random Other icon so repeated profile-changes don't always yield the same icon
        invoke(path = "", profile = profile, type = type)
            ?: ResourceIconRegistry.randomIdFor(ResourceIconSet.OTHER)
```

### Step 3.2 — AddResourceActivity.kt: строка 306

**Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`

Было (строка 306):
```kotlin
        val initialIconId = ResourceIconRegistry.firstIdFor(ResourceIconSet.OTHER)
```

Стало:
```kotlin
        // Pick a random icon from the Other set so each new resource starts with a unique look
        val initialIconId = ResourceIconRegistry.randomIdFor(ResourceIconSet.OTHER)
```

---

## Важные замечания

1. `ResourceIconRegistry.randomIdFor(ResourceIconSet.OTHER)` использует `kotlin.random.Random.Default` — потокобезопасен, не требует seed.
2. Виртуальные пути (`VIRTUAL_PATH_RECENT` и др.) всегда получают фиксированные id через `fixedIconForVirtualPath` — эта ветка не затронута.
3. Для SET_MUSIC / SET_VIDEO / SET_IMAGE / SET_DOCS поведение не меняется (первая иконка набора). Только SET_OTHER → случайная.

---

## Verification

Юнит-тест (вручную или в тестовом классе):
```kotlin
val useCase = ResolveResourceIconUseCase()
val results = (1..20).map {
    useCase(path = "", profile = ResourceProfile.NONE, type = ResourceType.LOCAL)
}
println(results.distinct())
// Ожидаем: более 1 уникального id в выборке 20 вызовов
```

Интеграционная проверка: создать 5 ресурсов с профилем NONE → каждый должен получить разную иконку (или хотя бы не все одинаковые).

---

## Dev Log

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveResourceIconUseCase.kt" "ResolveResourceIconUseCase" "S0037 P2: use randomIdFor for SET_OTHER instead of fixed ico-05-001"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt" "AddResourceActivity" "S0037 P2: initial icon for Other resources randomised"
```
