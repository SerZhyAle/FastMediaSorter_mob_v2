# Phase 05 — Strings + Localization

**Strategic spec:** [`../S0058_vr-passthrough-camera-capture.md`](../S0058_vr-passthrough-camera-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 1 / 1
**Started:** —
**Completed:** 2026-05-05

---

## Objective

Add all passthrough-capture string resources to EN, RU, and UK `strings.xml` files in a single step. Eliminate any placeholder strings introduced in Phase 04.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] All five string keys referenced in Phase 03–04 are known (listed below in Step 05.1).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +8 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +8 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +8 lines |

---

## Steps

### Step 05.1 — Add passthrough capture strings in EN / RU / UK

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** — start of phase

**Prompt for developer:**

> Add the following string keys near the existing `vr_save_frame_*` block in each `strings.xml`:
>
> **English (`values/strings.xml`):**
> ```xml
> <string name="passthrough_capture_saved">Passthrough snapshot saved to %s</string>
> <string name="passthrough_capture_unavailable">Passthrough camera unavailable on this device</string>
> <string name="passthrough_capture_error">Failed to open passthrough camera</string>
> <string name="passthrough_capture_timeout">Passthrough capture timed out — try again</string>
> <string name="passthrough_capture_error_save">Failed to save passthrough snapshot</string>
> <string name="passthrough_capture_permission_denied">Camera access required — open app settings to grant permission</string>
> <string name="passthrough_capture_permission_needed">Camera permission is needed to capture passthrough snapshots</string>
> ```
>
> **Russian (`values-ru/strings.xml`):**
> ```xml
> <string name="passthrough_capture_saved">Снимок passthrough сохранён в %s</string>
> <string name="passthrough_capture_unavailable">Passthrough-камера недоступна на этом устройстве</string>
> <string name="passthrough_capture_error">Не удалось открыть passthrough-камеру</string>
> <string name="passthrough_capture_timeout">Время захвата passthrough истекло — попробуйте снова</string>
> <string name="passthrough_capture_error_save">Не удалось сохранить снимок passthrough</string>
> <string name="passthrough_capture_permission_denied">Нужен доступ к камере — откройте настройки приложения, чтобы разрешить</string>
> <string name="passthrough_capture_permission_needed">Для захвата passthrough-снимков требуется разрешение камеры</string>
> ```
>
> **Ukrainian (`values-uk/strings.xml`):**
> ```xml
> <string name="passthrough_capture_saved">Знімок passthrough збережено в %s</string>
> <string name="passthrough_capture_unavailable">Passthrough-камера недоступна на цьому пристрої</string>
> <string name="passthrough_capture_error">Не вдалося відкрити passthrough-камеру</string>
> <string name="passthrough_capture_timeout">Час захвату passthrough вичерпано — спробуйте ще раз</string>
> <string name="passthrough_capture_error_save">Не вдалося зберегти знімок passthrough</string>
> <string name="passthrough_capture_permission_denied">Потрібен доступ до камери — відкрийте налаштування застосунку, щоб дозволити</string>
> <string name="passthrough_capture_permission_needed">Для захоплення passthrough-знімків потрібен дозвіл камери</string>
> ```
>
> If any placeholder values were added in Phase 04, replace them now.
>
> After adding strings, run the locale-parity check:
> ```powershell
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "passthrough_capture"
> ```
> Exit code must be 0.

**Verification:**

- `Grep` — `passthrough_capture_saved` present in `values/strings.xml`.
- `Grep` — `passthrough_capture_saved` present in `values-ru/strings.xml`.
- `Grep` — `passthrough_capture_saved` present in `values-uk/strings.xml`.
- `Grep` — `passthrough_capture_permission_denied` present in all three files.
- Locale-parity script exits 0 for prefix `passthrough_capture`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 5/5 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml (+7 strings each). Placeholders removed from vr/res/values/strings.xml. Locale parity: 7/7 OK. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "passthrough_capture"` exits 0.
- [ ] Dev log entry added for all three `strings.xml` files.

---

## Handoff Notes to Next Phase

- All `R.string.passthrough_capture_*` keys are defined in EN, RU, UK.
- Build compiles cleanly with real string resources.
- Phase 06 updates feature docs and regenerates the catalog.

---

## Rollback Plan

Remove the added `<string>` entries from all three files. Revert to placeholder strings in VR source (or revert Phase 04 commit too).
