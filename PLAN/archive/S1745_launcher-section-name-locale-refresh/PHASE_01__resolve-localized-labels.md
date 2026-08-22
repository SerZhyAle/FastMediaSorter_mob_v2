# Phase 01 - Resolve Localized Labels

**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 02
**Steps done:** 2 / 2

## Objective

Ensure launcher section titles and system command labels react immediately to language setting changes without requiring an app restart.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherDesktopUseCase.kt` | Modified | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherDesktopUseCaseTest.kt` | New | ≤ 500 |

## Steps

### Step 01.1 - Pass language to label resolver and use localized context

**Files:** `ResolveLauncherCommandLabelUseCase.kt`, `ResolveLauncherDesktopUseCase.kt`

**Prompt for developer:**

> Combine `language` in `ResolveLauncherDesktopUseCase`, and use `LocaleHelper.applyLocale(context, language)` in `ResolveLauncherCommandLabelUseCase` to resolve system strings while preserving custom `labelOverride`.

**Why:**

System section names must update immediately when interface language changes in settings.

**Verification:**

- `ResolveLauncherDesktopUseCase` reacts to `settings.language` changes.
- System section names reflect the selected locale.
- User-specified `labelOverride` remains unchanged.

**Status:** `[x]` done

### Step 01.2 - Unit test section title locale refresh

**Files:** `ResolveLauncherDesktopUseCaseTest.kt`

**Prompt for developer:**

> Add tests asserting section titles switch between locales when language setting changes, while user-renamed sections retain their custom label.

**Why:**

Ensures no regression in desktop resolution and confirms user vs system label precedence.

**Verification:**

- Unit tests pass for EN and RU/UK language changes.

**Status:** `[x]` done
