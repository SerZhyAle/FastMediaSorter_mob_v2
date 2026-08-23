# Phase 01 - Starter Sets Sections Order

**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 02
**Steps done:** 2 / 2

## Objective

Restructure section catalog and starter set order in `LauncherStarterSets.kt` and `LauncherSectionCatalog.kt` to place unsectioned items at top, followed by widgets, resources, app functions, and android apps sections.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherSectionCatalog.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 550 |

## Steps

### Step 01.1 - Define new section keys in catalog and strings

**Files:** `LauncherSectionCatalog.kt`, `strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`

**Prompt for developer:**

> Add `SECTION_WIDGETS`, `SECTION_RESOURCES`, `SECTION_ANDROID_APPS` to `LauncherSectionCatalog` alongside existing `SECTION_APP_FUNCTIONS` and `SECTION_MAIN`. Add EN/RU/UK strings.

**Verification:**

- `LauncherSectionCatalog.all` contains the ordered sections.

**Status:** `[x]` done

### Step 01.2 - Update Starter Sets item ordering

**Files:** `LauncherStarterSets.kt`

**Prompt for developer:**

> Order starter set items: unsectioned top items (clock / search / hero widgets) -> widgets section -> resources section -> app functions section -> android apps section.

**Verification:**

- `LauncherStarterSets.itemsFor` outputs items in the required order.

**Status:** `[x]` done
