# Стратегическая спецификация: S1105 - Welcome first page app icon

**Ticket:** S1105
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-18
**Tier:** 1 - Quick Win

## Problem

The first onboarding page uses a decorative hero image above the welcome text. It does not reinforce the app brand on the very first screen the user sees.

## Approach

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` - replace the first welcome page header image resource with the app icon drawable so the existing enhanced welcome layout shows brand-first artwork.

## Done criteria

- `WelcomeActivity` builds with the first onboarding page pointing to the app icon resource.

## Last Audit

### Manual (device 2026-07-19)

- Device: emulator-5554 (sdk_gphone64_x86_64, Android 15 / SDK 35), build v2.60.7182.317-DEBUG (standard debug).
- Method: `pm clear` to force first-run, launch app, MainActivity redirected to WelcomeActivity first onboarding page, screenshot portrait then landscape.
- Portrait: PASS. expected: branded app icon renders above the "Welcome to FastMediaSorter!" title on first page | actual: branded FastMediaSorter icon (diamond with four directional arrows) shown centered directly above the welcome title, page indicator on dot 1 of 6. Evidence: temp/S1105/portrait_first_page.png.
- Landscape: PASS. expected: app icon still above the title, layout intact, no overlap/clipping | actual: icon remains centered above the welcome title, no overlap or clipping, page indicator on dot 1 of 6 (lower content scrolls as normal). Evidence: temp/S1105/landscape_first_page.png.
- Logcat `S1105:` markers: none (visual ticket, no debug probe expected).
- Verdict: PASS (portrait + landscape).
