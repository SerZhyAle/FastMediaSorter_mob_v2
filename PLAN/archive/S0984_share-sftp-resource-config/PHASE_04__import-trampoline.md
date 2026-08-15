# Phase 04 - Import trampoline (receive `.fmscfg` attachment -> one-tap import)

**Strategic spec:** [`../S0984_share-sftp-resource-config.md`](../S0984_share-sftp-resource-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-07-11
**Completed:** 2026-07-11

---

## Objective

Add `CompanionConfigImportActivity` (transparent trampoline) plus manifest `ACTION_VIEW`/`ACTION_SEND` filters so tapping a received `.fmscfg` attachment in Telegram/email leads straight to a compact confirm dialog and one-tap import - the strategic core requirement (owner 23:37).

---

## Prerequisites

- [ ] Phase 01 ✅ Done (relaxed parser + import use case tolerate passwordless / no-fingerprint configs).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` (+`values-ru`, `values-uk`) | Modified | - |
| `app_v2/src/main/res/layout/dialog_companion_import_confirm.xml` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/CompanionConfigImportActivity.kt` | New | ≤ 180 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | - |

> `dialog_companion_import_confirm.xml`: landscape variant absent - not needed (single-column dialog content in a `ScrollView`, `wrap_content`). No `res/layout-land` counterpart to edit.

---

## Steps

### Step 04.1 - Add trilingual import strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add in lockstep across EN/RU/UK via `set-android-string.ps1 -Action add`: `companion_import_title`, `companion_import_confirm_message` (a `%1$s` name / `%2$s` host / `%3$d` folder-count formatted summary), `companion_import_no_fingerprint_warning` ("This server has not been verified - connect only if you trust the sender."), `companion_import_password_label` ("Password for this server"), `companion_import_action` (positive "Import"), `companion_import_invalid_file` (polite "This file is not a FastMediaSorter access file."), `companion_import_success` (`%1$s` host), `companion_import_failed`. Follow `docs/COMMUNICATION_POLICY.md` §2/§6; `..` not `...`, plain hyphen, Ё where grammatical. Run `check_strings_localized.ps1 -KeyPrefix "companion_import"`.

**Verification:**

- `Grep` - each key present in all three `strings.xml`.
- `check_strings_localized.ps1 -KeyPrefix "companion_import"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

### Step 04.2 - Confirm dialog layout (with optional password field)

**Files:** `res/layout/dialog_companion_import_confirm.xml` (New)
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `dialog_companion_import_confirm.xml`: a `ScrollView` > vertical `LinearLayout` with a summary `TextView` id `textImportSummary`, a warning `TextView` id `textNoFingerprintWarning` (`android:visibility="gone"`), and a `com.google.android.material.textfield.TextInputLayout` id `layoutImportPassword` wrapping a `TextInputEditText` id `editImportPassword` (`android:inputType="textPassword"`, hint `@string/companion_import_password_label`, `android:visibility="gone"`). No hardcoded hex colors - theme attributes only.

**Verification:**

- `Glob` - `dialog_companion_import_confirm.xml` exists.
- `Grep` - `textImportSummary`, `textNoFingerprintWarning`, `editImportPassword` present.
- `Grep` - no `="#` hex color in the file.

**Status:** `[x] done`

---

### Step 04.3 - `CompanionConfigImportActivity`

**Files:** `ui/companionimport/CompanionConfigImportActivity.kt` (New)
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `@AndroidEntryPoint class CompanionConfigImportActivity : AppCompatActivity()` mirroring `ResourceImportActivity`'s shape (transparent host - reuse `Theme.FastMediaSorter.Transparent`). Inject `CompanionConfigParser` and `ImportCompanionConfigUseCase`. `onCreate`: `resolveUri()` (`ACTION_SEND` -> `IntentCompat.getParcelableExtra(intent, EXTRA_STREAM, Uri)`, else `intent.data`); null -> `Timber.w` + `finish()`. Then `lifecycleScope.launch`: read bytes on `Dispatchers.IO` (cap 64 KB - reuse the same guard value as the use case), `parser.parse(bytes)` in try/catch; on `CompanionConfigException`/any error -> show `companion_import_invalid_file` result dialog + finish. On success show the confirm dialog: inflate `dialog_companion_import_confirm.xml`, set `textImportSummary` to `companion_import_confirm_message` with `dto.resourceName ?: host`, `host`, `dto.roots.size`; show `textNoFingerprintWarning` when `dto.hostKeyFingerprintSha256.isNullOrBlank()`; show `layoutImportPassword` when `dto.password.isNullOrEmpty()`. Positive `companion_import_action` -> build `finalDto = if (dto.password.isNullOrEmpty()) dto.copy(password = editImportPassword.text?.toString().orEmpty()) else dto`, call `importCompanionConfigUseCase.import(finalDto)`, map `Result` to `companion_import_success` (host) / `companion_import_failed`, show a result dialog, finish. Negative -> finish. Use `MaterialAlertDialogBuilder` standard buttons (matches `ResourceImportActivity`). All user-facing text from strings; `Timber` for errors only.

**Verification:**

- `Glob` - `ui/companionimport/CompanionConfigImportActivity.kt` exists.
- `Grep` - `class CompanionConfigImportActivity` matches once; `importCompanionConfigUseCase.import(` and `dto.copy(password =` present.
- `Grep` - `resolveUri` and `EXTRA_STREAM` present (VIEW+SEND handling).
- Build predicate covered by Phase Done Criteria.

**Status:** `[x] done`

---

### Step 04.4 - Register activity + intent filters in the manifest

**Files:** `AndroidManifest.xml`
**Depends on:** Step 04.3

**Prompt for developer:**

> Register `<activity android:name=".ui.companionimport.CompanionConfigImportActivity" android:exported="true" android:excludeFromRecents="true" android:configChanges="orientation|screenSize|keyboardHidden" android:theme="@style/Theme.FastMediaSorter.Transparent">` near `ResourceImportActivity`. Intent-filters: (1) `ACTION_VIEW` + DEFAULT + BROWSABLE, `scheme="content"`, `mimeType="application/octet-stream"` - the tap-from-Telegram/email path; (2) `ACTION_VIEW` + DEFAULT + BROWSABLE, `scheme="content"`, `mimeType="application/vnd.fms.companion-config+json"`; (3) `ACTION_VIEW` + DEFAULT + BROWSABLE, `scheme="file"`, `host="*"`, `pathPattern=".*\\.fmscfg"`, `mimeType="*/*"` - best-effort file managers; (4) `ACTION_SEND` + DEFAULT, `mimeType="application/vnd.fms.companion-config+json"` - reliable own-export share. Add a WHY comment: octet-stream VIEW is registered deliberately (owner requirement 2026-07-10 23:37 "import on tap"); it reverses the S0422 chooser-noise decision for `.fmscfg` only, and junk is rejected by content validation in the activity. Mirrors `ResourceImportActivity`'s `src/main` flavor posture (the same `lite` "Open with" presence already ships for `.fmsr`).

**Verification:**

- `Grep` - `CompanionConfigImportActivity` present in `AndroidManifest.xml`.
- `Grep` - `application/octet-stream` and `.*\\.fmscfg` both present in the new block.
- `.\a.ps1 fr` (resources/manifest) passes.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] `check_strings_localized.ps1 -KeyPrefix "companion_import"` exits 0.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (`CompanionConfigImportActivity`).

---

## Handoff Notes to Next Phase

Both halves are live: export writes a `.fmscfg`, import receives one via attachment tap or share sheet. The round trip (export on device A -> Telegram -> tap on device B) is the on-device verification for `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit(s). The activity + manifest block are additive; removing them drops the external-import surface with no migration.
