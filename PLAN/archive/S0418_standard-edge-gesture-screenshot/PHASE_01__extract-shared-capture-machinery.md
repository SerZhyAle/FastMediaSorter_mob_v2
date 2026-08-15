# Phase 01 - Extract shared capture machinery

**Goal:** Move the a11y-free MediaProjection machinery out of `src/noLegal` into a new shared source set `src/screenCapture`, mounted into `noLegal`, `standard`, `photos`. After this phase `noLegal` behaves identically (machinery just lives elsewhere); `standard`/`photos` gain the machinery on their classpath but no controller binding yet (capability still invisible there).

**Depends on:** none.

---

## Steps

### 1.1 Create shared source set and move machinery classes

Move these four files from `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/` to `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/` (package unchanged - `com.sza.fastmediasorter.screencapture`):

- `OverlayHostService.kt`
- `ScreenGestureOverlayManager.kt`
- `ScreenCaptureConsentActivity.kt`
- `ScreenCaptureService.kt`

Do not edit their contents except removing/repointing the stale a11y comment in `OverlayHostService` if it references the now-noLegal-only service (comment only, no code).

**Verification:** `app_v2/src/screenCapture/java/.../screencapture/` contains exactly those four `.kt` files; `app_v2/src/noLegal/java/.../screencapture/` retains only `ScreenshotAccessibilityService.kt`, `ScreenshotAccessibilityServiceHolder.kt`, `ScreenGestureOverlayControllerImpl.kt`. `grep -rn "ScreenshotAccessibility" app_v2/src/screenCapture/` returns nothing.

### 1.2 Move the notification drawable to the shared res

Move `app_v2/src/noLegal/res/drawable/ic_notification_screen_capture.xml` to `app_v2/src/screenCapture/res/drawable/ic_notification_screen_capture.xml`. White fill, no `?attr` tint (S0405 Test 1 invariant - small-icon must resolve without theme).

**Verification:** file exists at the new path; old path gone; `grep -n "attr" app_v2/src/screenCapture/res/drawable/ic_notification_screen_capture.xml` empty.

### 1.3 Mount the shared set into the three flavors

In `app_v2/build.gradle.kts` `sourceSets { }`:

- `getByName("standard")`: add `kotlin.directories.add("src/screenCapture/java")` and `res.directories.add("src/screenCapture/res")`.
- `getByName("photos")`: add the same two.
- `getByName("noLegal")`: add the same two.

Keep the existing shared-set additions intact.

**Verification:** `build.gradle.kts` shows `src/screenCapture/java` under standard, photos, noLegal; `src/screenCapture/res` under the same three.

### 1.4 Build noLegal - regression gate

`assembleNoLegalDebug` must compile and merge as before (machinery resolved from the shared set, a11y from `src/noLegal`).

**Verification:** `.\a.ps1 nd` (or `.\gradlew.bat :app_v2:assembleNoLegalDebug`) exits 0.

---

## Phase Done Criteria

- [ ] Four machinery classes live in `src/screenCapture/java`; only a11y classes + controller remain in `src/noLegal`.
- [ ] Drawable moved to `src/screenCapture/res`.
- [ ] `src/screenCapture/{java,res}` mounted into standard, photos, noLegal.
- [ ] `assembleNoLegalDebug` green.
