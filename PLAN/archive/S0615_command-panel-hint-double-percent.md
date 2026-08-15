# S0615 - command-panel hint overlay shows doubled percent (20%% instead of 20%)

**Status:** Archived

## 0. Raw capture

Parked from a `/spec-prerelease` sweep on emulator-5554 (Android 17), build 2.60.6211.547-DEBUG, while opening an image in the in-app player.

Symptom: the first-time command-panel help overlay renders the touch-zone percentages with a literal doubled percent sign:

```
Режим командной панели
Левые 20%% - Предыдущий файл
Центр 60%% - Выбор / Действия
Правые 20%% - Следующий файл
```

Expected: `20%`, `60%`, `20%` (single percent).

Evidence: `temp/s0484_screens/03_image_view.png`.

## 1. Root cause (verified in resources)

`hint_touch_zone_3zone` hard-codes `%%` in all three locales:

- `values/strings.xml:744` - "Left 20%% .. Center 60%% .. Right 20%%"
- `values-ru/strings.xml:704` - "Левые 20%% .. Центр 60%% .. Правые 20%%"
- `values-uk/strings.xml:697` - "Ліві 20%% .. Центр 60%% .. Праві 20%%"

The string has no positional format arguments, so it is displayed raw (via `getString`/`setText`, not `String.format`). A `%%` that is never run through `String.format` is rendered literally as `%%`. (Contrast `playback_control_brightness_value` = "..%1$+d%%" which IS formatted, so its `%%` correctly collapses.)

## 2. Proposed direction (Draft - not approved)

- Before editing: grep all usages of `hint_touch_zone_3zone` to confirm it is never passed through `String.format` / `getString(id, args)`. If it never is, the `%%` is wrong and should be `%`.
- Replace `20%%`/`60%%`/`20%%` with `20%`/`60%`/`20%` in EN, RU, UK in lockstep (byte-preserving edit; `%` inside an Android string resource needs no escaping when the string is not formatted).
- Re-run `scripts/check_strings_localized.ps1 -KeyPrefix hint_touch_zone_3zone`.
- If any caller DOES format the string, the correct fix is the opposite (keep `%%`, make all callers format) - resolve before editing.

## 3. Notes

- Low severity / cosmetic, but user-visible in a first-run help overlay across all 3 locales.
- Distinct from S0614 (sweep harness permission grant).

## 4. Implementation

- Confirmed `hint_touch_zone_3zone` is consumed only via `setText(R.string.hint_touch_zone_3zone)` in `PlayerTouchZoneSetupManager.showHintOverlay` (COMMAND_PANEL_3ZONE branch) - never through `String.format` / `getString(id, args)`, so `%%` was rendered literally.
- Replaced `20%%`/`60%%`/`20%%` with `20%`/`60%`/`20%` in EN/RU/UK in lockstep (string line only; the other 6 `%%` per file belong to genuinely formatted strings and were left untouched).
- `scripts/check_strings_localized.ps1 -KeyPrefix hint_touch_zone_3zone` -> EN/RU/UK OK (exit 0).
- No Kotlin change; not built (per request).

## Last Audit

**Date:** 2026-06-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Notes

- `hint_touch_zone_3zone` now carries single `%` in all three locales (`values/strings.xml:744` "Left 20% .. Center 60% .. Right 20%", `values-ru:704`, `values-uk:697`), each marked `formatted="false"`.
- Sole consumer is `PlayerTouchZoneSetupManager.kt:66` via `tvFirstRunHintText.setText(R.string.hint_touch_zone_3zone)` - never `String.format` / `getString(id, args)`, so the previous `%%` rendered literally and the single `%` now renders correctly.
- `scripts/check_strings_localized.ps1 -KeyPrefix hint_touch_zone_3zone` -> EN/RU/UK OK (exit 0).
- The other `%%` occurrences per file belong to genuinely formatted strings and were correctly left untouched.
- Debug-tag invariant PASS: string-resource change, no `.kt` touched, zero `Timber.d("S0615:` tags.
- FEATURES trilingual EXEMPT: cosmetic rendering fix in a first-run help overlay, not a new showcase capability.

### Manual / on-device

- [ ] Open an image in the in-app player on first run: the command-panel help overlay shows `20%` / `60%` / `20%` (single percent), not `20%%`.
