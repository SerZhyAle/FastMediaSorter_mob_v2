# PHASE 01 — Extend Blocked Extensions + i18n

**Spec:** S0077
**Pillar:** A (routing fix), B (localization gap from S0063)
**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkThumbnailExtractionPolicy.kt` (~40 lines — no backup needed)
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

---

## Pre-condition

```powershell
# Confirm spec status
pwsh -File scripts/spec_catalog/select.ps1 -Id S0077 -Format json
# Confirm key file exists
Test-Path "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkThumbnailExtractionPolicy.kt"
```

Expected status: `Tactical` or `In Progress`.

---

## Step 1.1 — Add optical-disc extensions to `BLOCKED_EXTENSIONS`

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkThumbnailExtractionPolicy.kt`

Locate the `BLOCKED_EXTENSIONS` val (around line 28). Replace the entire `object` block as follows.

**Current `BLOCKED_EXTENSIONS` block:**
```kotlin
    val BLOCKED_EXTENSIONS: Set<String> = setOf(
        "avi"
    )
```

**Replace with:**
```kotlin
    val BLOCKED_EXTENSIONS: Set<String> = setOf(
        // AVI: setDataSource triggers status 0x80000000 almost immediately via NetworkMediaDataSource;
        //      the 10-second watchdog fires after every attempt. S0063.
        "avi",
        // BDMV / DVD optical-disc containers: neither NetworkFileModelLoader nor NetworkVideoFrameDecoder
        // handles these formats — both loaders reject them, causing NoModelLoaderAvailableException on
        // every bind. Block them here so the fast-path placeholder fires before Glide is invoked. S0077.
        "vob", "m2ts", "mts", "m2t", "ts", "ifo", "bup"
    )
```

**Also update the class-level KDoc** to mention the new category. Locate the comment that starts
`"AVI: [setDataSource] triggers..."` in the existing KDoc block (around line 23) and append a new
paragraph AFTER the existing AVI paragraph:

```
 *
 * BDMV/DVD (S0077): neither NetworkFileModelLoader nor NetworkVideoFrameDecoder handles
 * vob/m2ts/mts/m2t/ts/ifo/bup on network streams — both loaders reject these formats.
 * Blocking them here prevents NoModelLoaderAvailableException on every bind.
 * If a decoder for optical-disc containers is added in the future, remove the entries here.
```

**Verification:**
```powershell
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkThumbnailExtractionPolicy.kt" `
  -Pattern '"vob"'
```
Expected: 1 match.

```powershell
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkThumbnailExtractionPolicy.kt" `
  -Pattern '"m2ts"'
```
Expected: 1 match.

---

## Step 1.2 — Add RU translation for `thumbnail_unavailable_network_format`

**File:** `app_v2/src/main/res/values-ru/strings.xml`

The EN baseline (from `values/strings.xml` line 6):
```xml
<string name="thumbnail_unavailable_network_format">%1$s video file, thumbnail unavailable on network</string>
```

Add the RU translation inside the `<resources>` block (after the last existing `<string>` entry):
```xml
    <string name="thumbnail_unavailable_network_format">%1$s — видеофайл, превью по сети недоступно</string>
```

**Verification:**
```powershell
Select-String -Path "app_v2/src/main/res/values-ru/strings.xml" -Pattern "thumbnail_unavailable_network_format"
```
Expected: 1 match.

---

## Step 1.3 — Add UK translation for `thumbnail_unavailable_network_format`

**File:** `app_v2/src/main/res/values-uk/strings.xml`

Add the UK translation inside the `<resources>` block (after the last existing `<string>` entry):
```xml
    <string name="thumbnail_unavailable_network_format">%1$s — відеофайл, прев\'ю по мережі недоступне</string>
```

> Note: apostrophe in `прев'ю` must be escaped as `\'` in Android XML string resources.

**Verification:**
```powershell
Select-String -Path "app_v2/src/main/res/values-uk/strings.xml" -Pattern "thumbnail_unavailable_network_format"
```
Expected: 1 match.

---

## Step 1.4 — Build + Lint

```powershell
.\gradlew.bat assembleStandardDebug
.\gradlew.bat lintStandardDebug
```

Expected: BUILD SUCCESSFUL, 0 new lint errors.

---

## Step 1.5 — Update spec status to `In Progress`

```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id S0077 -Status "In Progress"
```

---

## Phase Completion Checklist

- [ ] `BLOCKED_EXTENSIONS` contains `"vob"`, `"m2ts"`, `"mts"`, `"m2t"`, `"ts"`, `"ifo"`, `"bup"`.
- [ ] KDoc updated with S0077 entry.
- [ ] `values-ru/strings.xml` contains `thumbnail_unavailable_network_format`.
- [ ] `values-uk/strings.xml` contains `thumbnail_unavailable_network_format`.
- [ ] Build passes.
- [ ] Lint clean.
