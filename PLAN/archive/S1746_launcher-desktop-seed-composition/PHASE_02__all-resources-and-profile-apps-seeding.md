# Phase 02 - All Resources and Profile Apps Seeding

**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2

## Objective

Update `SeedLauncherDesktopUseCase.kt` to seed all active user resources and profile-specific Android applications into their respective sections.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 550 |

## Steps

### Step 02.1 - Feed all resources and profile candidate apps into seed

**Files:** `SeedLauncherDesktopUseCase.kt`, `LauncherStarterSets.kt`

**Prompt for developer:**

> In `SeedLauncherDesktopUseCase`, retrieve all non-hidden resources from `resourceRepository` and pass them into `StarterResources`, and query candidate apps per profile (e.g. YouTube, Chrome, dialer for smartphone).

**Verification:**

- `SeedLauncherDesktopUseCase` places all user resources under the resources section.

**Status:** `[x]` done

### Step 02.2 - Unit test seeding composition

**Files:** `LauncherStarterSetsTest.kt`, `SeedLauncherDesktopUseCaseTest.kt`

**Prompt for developer:**

> Add unit tests verifying that all provided resources are seeded and sections are placed correctly.

**Verification:**

- Unit tests pass.

**Status:** `[x]` done
