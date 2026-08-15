# S0037 PHASE 02 — Drawable Assets (П1 + П3)

**Статус:** ✅ Done  
**Completed:** 2026-04-30  
**Проблемы:** П1 — иконка видео не выглядит как видеокамера; П3 — иконка SMB нечитаема  
**Риск:** Низкий — только XML векторные drawable. Логика не меняется.

---

## Step Log

- 2026-04-30 — Step 2.1 PASS: ico_02_001.xml rewritten (camera body with evenOdd lens ring + viewfinder + carry handle). Step 2.2 PASS: ic_resource_smb.xml rewritten (blue folder + 3 connected white nodes). Both retain original `<vector>` attributes; tints unchanged. Dev log recorded for both files.

---

## Затронутые файлы

| Файл | Изменение |
|------|-----------|
| `app_v2/src/main/res/drawable/ico_02_001.xml` | П1: заменить pathData на ретро-видеокамеру |
| `app_v2/src/main/res/drawable/ic_resource_smb.xml` | П3: заменить pathData на папку + LAN-узлы |

---

## Шаги

### Step 2.1 — ico_02_001.xml (П1 — ретро-видеокамера)

**Файл:** `app_v2/src/main/res/drawable/ico_02_001.xml`

Сохранить атрибуты тега `<vector>` без изменений:
```xml
android:width="24dp"
android:height="24dp"
android:viewportWidth="24"
android:viewportHeight="24"
android:tint="?attr/colorOnSurface"
```

Заменить всё содержимое `<path>` элементов на новые:

```xml
<!-- Camera body (rounded rect) with lens as ring (evenOdd) and lens center filled -->
<path
    android:fillColor="#000000"
    android:fillType="evenOdd"
    android:pathData="
        M3,9 C3,7.9 3.9,7 5,7 L18,7 C19.1,7 20,7.9 20,9 V19 C20,20.1 19.1,21 18,21
        L5,21 C3.9,21 3,20.1 3,19 Z
        M8.5,14 m-4,0 a4,4 0,1,0 8,0 a4,4 0,1,0 -8,0
        M8.5,14 m-2,0 a2,2 0,1,0 4,0 a2,2 0,1,0 -4,0" />
<!-- Viewfinder eyepiece protruding to the right -->
<path
    android:fillColor="#000000"
    android:pathData="M20,11 H23 V17 H20 Z" />
<!-- Top carry handle / grip bar -->
<path
    android:fillColor="#000000"
    android:pathData="M7,4 H17 V7 H7 Z" />
```

**Логика evenOdd для тела камеры:**
- Subpath 1 — прямоугольник: ЗАПОЛНЕН (count=1, нечётный)
- Subpath 2 — внешняя окружность линзы r=4: кольцевая зона ПУСТАЯ (count=2, чётный → дыра в теле)
- Subpath 3 — внутренняя окружность r=2: стекло линзы ЗАПОЛНЕНО (count=3, нечётный)

Результат: тело камеры сплошное, в нём кольцевой ободок линзы (как у камеры), центр линзы — стекло.

**Примечание о tint:** `android:tint="?attr/colorOnSurface"` применяется к полному векторному drawable — все `fillColor="#000000"` заменятся на текущий `colorOnSurface`. Это поведение идентично остальным иконкам набора ico-02.

### Step 2.2 — ic_resource_smb.xml (П3 — сетевая папка)

**Файл:** `app_v2/src/main/res/drawable/ic_resource_smb.xml`

Атрибуты тега `<vector>` сохранить (без android:tint, как в оригинале):
```xml
android:width="24dp"
android:height="24dp"
android:viewportWidth="24"
android:viewportHeight="24"
```

Заменить всё содержимое на:

```xml
<!-- Folder body with tab -->
<path
    android:fillColor="#1565C0"
    android:pathData="M10,4H4C2.9,4 2,4.9 2,6L2,18C2,19.1 2.9,20 4,20H20
        C21.1,20 22,19.1 22,18V8C22,6.9 21.1,6 20,6H12L10,4Z" />
<!-- Network connector lines between three nodes -->
<path
    android:fillColor="@android:color/transparent"
    android:strokeColor="#FFFFFF"
    android:strokeWidth="1.5"
    android:pathData="M9,16 L13.5,12 M13.5,12 L18,16" />
<!-- Left node -->
<path
    android:fillColor="#FFFFFF"
    android:pathData="M9,16m-2,0a2,2 0,1,0 4,0a2,2 0,1,0 -4,0" />
<!-- Center top node -->
<path
    android:fillColor="#FFFFFF"
    android:pathData="M13.5,12m-2,0a2,2 0,1,0 4,0a2,2 0,1,0 -4,0" />
<!-- Right node -->
<path
    android:fillColor="#FFFFFF"
    android:pathData="M18,16m-2,0a2,2 0,1,0 4,0a2,2 0,1,0 -4,0" />
```

Концепция: синяя папка (идентична исходному контейнеру) + три соединённых белых кружка — стандартная топология LAN/Ethernet. Читается как «сетевая папка» даже при 27dp badge.

---

## Verification

1. Открыть `ico_02_001.xml` в Android Studio → "Show Preview" → камера должна иметь корпус, ободок линзы, центральный кружок (стекло), рукоять сверху и видоискатель справа.
2. Открыть `ic_resource_smb.xml` → синяя папка с тремя соединёнными белыми узлами.
3. Запустить `assembleStandardDebug`, проверить главный экран — badge SMB-ресурса должен показывать новую иконку.

---

## Dev Log

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/drawable/ico_02_001.xml" "ico_02_001" "S0037 P1: redesign as retro camcorder silhouette (evenOdd lens ring)"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/drawable/ic_resource_smb.xml" "ic_resource_smb" "S0037 P3: redesign as network folder (folder + 3 LAN nodes)"
```
