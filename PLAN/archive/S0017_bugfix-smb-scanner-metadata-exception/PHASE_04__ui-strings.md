# Phase 04 — UI Localization Strings

**Status:** [x]

## Steps

### 4.1 values/strings.xml (English)

File: `app_v2/src/main/res/values/strings.xml`

Add before `</resources>`:
```xml
<string name="smb_metadata_errors_warning">%d file(s) indexed without metadata (read error)</string>
```

### 4.2 values-ru/strings.xml (Russian)

File: `app_v2/src/main/res/values-ru/strings.xml`

Add before `</resources>`:
```xml
<string name="smb_metadata_errors_warning">%d файл(ов) проиндексировано без метаданных (ошибка чтения)</string>
```

### 4.3 values-uk/strings.xml (Ukrainian)

File: `app_v2/src/main/res/values-uk/strings.xml`

Add before `</resources>`:
```xml
<string name="smb_metadata_errors_warning">%d файл(ів) проіндексовано без метаданих (помилка читання)</string>
```

**Verification:** `grep -r "smb_metadata_errors_warning" app_v2/src/main/res/` → 3 files.
