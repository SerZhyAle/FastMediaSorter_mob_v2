# Phase 02 - Type-screen import entries (S0991) + acceptance

**Strategic spec:** [`../S0991_add-resource-import-file-barcode.md`](../S0991_add-resource-import-file-barcode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Steps done:** 3 / 3

---

## Objective

Add the two import entry points next to the four resource-type cards on the Add-resource screen (strategic goal 1), wired to the Phase 01 shared action source, with the barcode entry camera-gated. Then build-gate and hand to device verification.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_add_resource.xml` | Modified (import row in type grid) | ~651 -> ~670 |
| `app_v2/src/main/res/values/strings.xml` | Modified (1 section-label key) | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified (1 section-label key) | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified (1 section-label key) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified (wire 2 buttons) | ~510 -> ~520 |

---

## Steps

### Step 02.1 - Add the type-screen import row (layout + label string)

**Files:** `app_v2/src/main/res/layout/activity_add_resource.xml`, `values*/strings.xml`

**Prompt for developer:**

> Add one section-label string across EN/RU/UK:
> - `resource_import_section_title` = EN "Import a ready configuration" / RU "Импорт готовой конфигурации" / UK "Імпорт готової конфігурації" (via `set-android-string.ps1 -Action add`, native pwsh).
>
> In `activity_add_resource.xml`, inside the `layoutResourceTypes` GridLayout, **after** `cardCloudStorage`, append two grid children (each `android:layout_width="@dimen/match_constraint"` + `android:layout_columnWeight="1"`, mirroring the cards):
> 1. A `TextView` bound to `@string/resource_import_section_title` (bold, small top margin), acting as the section header.
> 2. A horizontal `LinearLayout` holding two `MaterialButton`s using `@style/Widget.Material3.Button.OutlinedButton` (import actions read distinct from the type cards), each `android:layout_width="0dp"` + `android:layout_weight="1"`:
>    - `@+id/btnImportFromFile` - text `@string/resource_import_from_file`, `app:icon="@drawable/ic_import"`, `android:textAllCaps="false"`.
>    - `@+id/btnImportFromBarcode` - text `@string/resource_import_from_barcode`, `app:icon="@drawable/ic_qr_scan"`, `android:textAllCaps="false"`.
>
> Placing them inside the grid makes them auto-hide when a type/section opens (the grid is set GONE in every `showXxxOptions()`), so no extra visibility wiring in the section-switch methods.

**Verification:**

- `Grep` - `btnImportFromFile` and `btnImportFromBarcode` present in the layout inside `layoutResourceTypes` (after `cardCloudStorage`).
- `Grep` - `resource_import_section_title` present in all three `strings.xml`.
- Project compiles - `.\a.ps1 fr` (resources/manifest) BUILD SUCCESSFUL.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 02.2 - Wire the type-screen buttons to the shared action source

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`

**Prompt for developer:**

> In `setupViews()`, wire the two new buttons to the Phase 01 shared methods:
> - `binding.btnImportFromFile.setOnClickListener { launchCompanionFileImport() }`
> - `binding.btnImportFromBarcode.isVisible = isBarcodeImportAvailable()` and `binding.btnImportFromBarcode.setOnClickListener { launchCompanionQrScan() }`.
>
> No new import logic - reuse the single action source (strategic §11.6). Ensure the barcode button gate matches the SFTP one so a camera-less device shows only "from file" in both places.

**Verification:**

- `Grep` - both new buttons wired to `launchCompanionFileImport()` / `launchCompanionQrScan()`; `btnImportFromBarcode.isVisible` uses `isBarcodeImportAvailable()`.
- Project compiles - `.\a.ps1 dq` (quiet standard debug) BUILD SUCCESSFUL.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 02.3 - Device verification + acceptance

**Files:** (no code) - on-device verification

**Prompt for developer:**

> With a device/emulator attached, verify strategic §11 criteria 1-6: (1) both entries visible next to the four type cards (EN/RU/UK); (2) "from file" opens SAF and a valid `.fmscfg` import adds a resource and closes the screen; (3) "by barcode" opens the camera scan and a valid QR adds a resource and closes the screen; (4) camera-less device hides "by barcode" in both places; (5) the SFTP header shows the same two relabelled buttons; (6) no duplicated import path. Harvest evidence via `/spec-test-device S0991`, then `/spec-check S0991` converts it to `Verified`/`Partial`/`Broken` and removes the `S0991:` probe tag on the transition out of `BlockNeedUserTest`. On the terminal verdict, advance S0992 to the same status (joint delivery).

**Verification:**

- `/spec-test-device S0991` evidence captured (type-screen + SFTP-header screenshots; one successful file import).
- `/spec-check S0991` returns `Verified`.
- `Grep` - zero `Timber.d("S0991:` remain in `.kt` after the verdict transition.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Two import entries render next to the type cards; barcode entry camera-gated.
- [ ] Both wired to the shared action source (no duplicated import logic).
- [ ] Standard debug builds green.
- [ ] Device verification passed; `/spec-check S0991` = `Verified`; S0992 advanced jointly.
- [ ] Capability recorded in `docs/ALL_FEATURES.jsonl` (strategic §8).

---

## Handoff Notes to Next Phase

Terminal phase. On `Verified`, both import entry points ship on one shared action source with EN/RU/UK labels; S0992 is closed jointly.

---

## Rollback Plan

Revert the layout / strings / activity changes from version control. No data migration.
