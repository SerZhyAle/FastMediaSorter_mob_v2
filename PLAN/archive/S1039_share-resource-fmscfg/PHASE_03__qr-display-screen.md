# Phase 03 - QR display screen

**Strategic spec:** [`../S1039_share-resource-fmscfg.md`](../S1039_share-resource-fmscfg.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Add `CompanionQrShareActivity` - a `FLAG_SECURE` screen that renders the QR bitmap for a payload string, shows the resource name, a scan hint, and a conditional "no password" note. Mirrors the existing `CompanionQrScanActivity` host pattern.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`QrCodeEncoder.encode` available).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_companion_qr_share.xml` | New | ≤ 90 |
| `app_v2/src/main/res/layout-land/activity_companion_qr_share.xml` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/qr/CompanionQrShareActivity.kt` | New | ≤ 120 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ +6 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +5 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +5 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +5 |

---

## Steps

### Step 03.1 - Screen strings (EN / RU / UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add four string keys in lockstep across all three locales using `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` (one call per key, parity-enforced with `-En -Ru -Uk`):
> - `companion_qr_share_title` - EN "Scan to add the resource" / RU "Отсканируйте, чтобы добавить ресурс" / UK "Відскануйте, щоб додати ресурс".
> - `companion_qr_share_hint` - EN "In FastMediaSorter: Add resource -> Scan QR" / RU "В FastMediaSorter: Добавить ресурс -> Сканировать QR" / UK "У FastMediaSorter: Додати ресурс -> Сканувати QR".
> - `companion_qr_share_no_password_note` - EN "No password included - the recipient enters it when importing." / RU "Пароль не включён - получатель введёт его при импорте." / UK "Пароль не включено - отримувач введе його під час імпорту."
> - `companion_qr_share_encode_failed` - EN "Could not generate the QR code." / RU "Не удалось создать QR-код." / UK "Не вдалося створити QR-код."
>
> Check the copy against `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist) before committing. Use `..` never `...`, plain hyphen, and Ё where grammatical.

**Verification:**

- `Grep` - each of the four keys present in all three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "companion_qr_share_"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

### Step 03.2 - Layout (portrait + landscape)

**Files:** `app_v2/src/main/res/layout/activity_companion_qr_share.xml`, `app_v2/src/main/res/layout-land/activity_companion_qr_share.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create a portrait layout: a vertical container inside `systemBars`/`displayCutout` safe bounds (respect insets, no edge-to-edge) with, top to bottom: a `TextView` `@id/tvQrTitle` (`@string/companion_qr_share_title`), a `TextView` `@id/tvQrResourceName` (bold, holds the dynamic resource name), a square `ImageView` `@id/imgQr` (centered, `adjustViewBounds`, content description `@string/companion_qr_share_title`), a `TextView` `@id/tvQrHint` (`@string/companion_qr_share_hint`), a `TextView` `@id/tvQrNoPasswordNote` (`@string/companion_qr_share_no_password_note`, `android:visibility="gone"`), and a `MaterialButton` `@id/btnQrClose` (label `@android:string/ok`). No hardcoded hex colors - use `?attr/`/`@color/`. Make the button focusable/clickable for D-pad. Create the `layout-land/` variant with the same ids (QR image beside the text block, or the same centered column constrained so the QR is not clipped in landscape).

**Verification:**

- `Glob` - both `layout/activity_companion_qr_share.xml` and `layout-land/activity_companion_qr_share.xml` exist.
- `Grep` - ids `imgQr`, `tvQrResourceName`, `tvQrNoPasswordNote`, `btnQrClose` present in both files.
- `Grep` - zero `="#` hardcoded color literals in either file.

**Status:** `[ ]` not done

---

### Step 03.3 - Create `CompanionQrShareActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/qr/CompanionQrShareActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `class CompanionQrShareActivity : AppCompatActivity()` using `ActivityCompanionQrShareBinding`. In `onCreate`, before `setContentView`, set `window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)`. Read three extras via a `companion object`: `EXTRA_PAYLOAD` (String), `EXTRA_RESOURCE_NAME` (String), `EXTRA_PASSWORD_INCLUDED` (Boolean, default true). Set `binding.tvQrResourceName.text` to the resource name. Compute a square size from the display metrics (e.g. `min(widthPx, heightPx) * 0.7` clamped to a sane max like 720) and call `QrCodeEncoder.encode(payload, size)`; on success set `binding.imgQr.setImageBitmap(bitmap)`, on any `WriterException`/`IllegalArgumentException` show `R.string.companion_qr_share_encode_failed` as a toast and `finish()`. Show `binding.tvQrNoPasswordNote` only when `EXTRA_PASSWORD_INCLUDED` is false. Wire `binding.btnQrClose` to `finish()`. Add `companion object { const val EXTRA_* ...; fun createIntent(context: Context, payload: String, resourceName: String, passwordIncluded: Boolean): Intent }`. KDoc references S1039 and the `FLAG_SECURE` rationale. No business logic beyond rendering; injects nothing.

**Verification:**

- `Glob` - `CompanionQrShareActivity.kt` exists.
- `Grep` - `FLAG_SECURE` present in the file.
- `Grep` - `fun createIntent` present with a `passwordIncluded` parameter.
- `Grep` - `QrCodeEncoder.encode` referenced.
- `Grep -n "Log\.d\("` - zero hits in the file (Timber only if any logging).

**Status:** `[ ]` not done

---

### Step 03.4 - Register the activity in the manifest

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 03.3

**Prompt for developer:**

> Register `<activity android:name=".ui.companionimport.qr.CompanionQrShareActivity" android:exported="false" android:theme="@style/..." />` next to the existing `CompanionQrScanActivity` entry (reuse the same theme that scan uses). No intent filters - it is launched internally only.

**Verification:**

- `Grep` - `CompanionQrShareActivity` present in `AndroidManifest.xml`.
- `Grep` - the entry carries `android:exported="false"`.
- Project compiles - run `/build`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`CompanionQrShareActivity.createIntent(context, payload, resourceName, passwordIncluded)` is the launch target for Phase 04's `MainEventHandler`. The screen is `FLAG_SECURE` and self-contained - callers only supply the payload string, resource name, and the password-included flag.

---

## Rollback Plan

Delete the new activity + layouts, revert the manifest and strings additions - no other code references them until Phase 04/05.
