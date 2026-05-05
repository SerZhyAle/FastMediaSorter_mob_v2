# S0083 — Bugfix: WRITE_EXTERNAL_STORAGE на API 29–32 блокирует онбординг

**Status:** Verified  
**Priority:** 70  
<!-- auto-approved by /spec-all — 2026-05-04 -->

## Goal

На устройствах с Android 10–12 (API 29–32) и `targetSdk 29+` система никогда не выдаёт
`WRITE_EXTERNAL_STORAGE`. Текущая ветка `>= M` в `getRequiredMediaPermissions()` требует обе
разрешения, `hasRequiredMediaPermissions()` всегда возвращает `false`, и после выдачи
`READ_EXTERNAL_STORAGE` приложение всё равно показывает диалог «Permission Required».
Цель — разделить ветку `M..32` на `M..Q` (READ + WRITE) и `Q..TIRAMISU` (только READ).

## Phase 1 — Fix `getRequiredMediaPermissions()`

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`

### Steps

1. In `getRequiredMediaPermissions()`, replace the single `>= M` branch with two branches:
   - `>= Q && < TIRAMISU` (API 29–32): return `arrayOf(READ_EXTERNAL_STORAGE)` only.
   - `>= M && < Q` (API 23–28): return `arrayOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)`.
   - Keep `>= TIRAMISU` branch unchanged.
2. Update the inline comment to accurately describe the new split.

**Verification:**
- `getRequiredMediaPermissions()` on API 32 device/emulator returns only `[READ_EXTERNAL_STORAGE]`.
- `hasRequiredMediaPermissions()` returns `true` after user grants READ on API 32 emulator.
- No regression on API 33+ (granular media permissions unchanged).
- Log line `mediaPermissionsLauncher result` shows `allGranted=true` (or `hasRequiredAfter=true`) on API 32 after granting READ.

## Last Audit

**Date:** 2026-05-04  
**Mode:** strategic  
**Flags:** —  
**Outcome:** Verified  
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 0

### Manual / on-device

- [x] `hasRequiredMediaPermissions()` returns `true` on API 32 emulator after granting READ_EXTERNAL_STORAGE.
- [x] Log line `mediaPermissionsLauncher result` shows `allGranted=true` or `hasRequiredAfter=true` on API 32.
