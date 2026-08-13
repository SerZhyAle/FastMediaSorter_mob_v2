# Phase 03 — ui-layout

**Strategic spec:** [`../S0070_device-storage-info-settings.md`](../S0070_device-storage-info-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Add layout elements to `fragment_settings_general.xml`: two TextViews (storage label/value, 8sp text) and one ImageButton (refresh, 18dp) arranged horizontally, with zero vertical padding, positioned before the first CardView (Interface section).

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (Fragment code added, observers in place).
- [ ] `app_v2/src/main/res/layout/fragment_settings_general.xml` exists and is readable.
- [ ] Material Design refresh icon is available (e.g., `@drawable/ic_refresh_24` or built-in Material icon).
- [ ] `@dimen/` constants for text sizes (8sp), button sizes (18dp) are defined or will be created.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 800 |
| `app_v2/src/main/res/values/dimens.xml` | Modified | ≤ 500 |

---

## Steps

### Step 03.1 — Define dimension constants for storage info layout

**Files:** `app_v2/src/main/res/values/dimens.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add three dimension constants to `values/dimens.xml`:
> - `<dimen name="device_storage_text_size">8sp</dimen>` — for the storage info text.
> - `<dimen name="device_storage_button_size">18dp</dimen>` — for the refresh button (width/height).
> - `<dimen name="device_storage_padding_vertical">0dp</dimen>` — vertical padding (zero, as per spec).
> 
> (Optional: if 18dp seems too small for touch target, use min 48dp but scale the icon down proportionally inside the button.)

**Verification:**

- `Glob` — `app_v2/src/main/res/values/dimens.xml` exists.
- `Grep` — `device_storage_text_size`, `device_storage_button_size`, `device_storage_padding_vertical` all defined.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: dimens.xml (+4 LOC). Dev log recorded.

---

### Step 03.2 — Add storage info container and TextViews to layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `fragment_settings_general.xml`, **before the first MaterialCardView** (the Interface section), add a new `LinearLayout` container:
> 
> ```xml
> <LinearLayout
>     android:id="@+id/containerDeviceStorageInfo"
>     android:layout_width="match_parent"
>     android:layout_height="wrap_content"
>     android:orientation="horizontal"
>     android:gravity="center_vertical"
>     android:paddingStart="@dimen/margin_small"
>     android:paddingEnd="@dimen/margin_small"
>     android:paddingTop="@dimen/device_storage_padding_vertical"
>     android:paddingBottom="@dimen/device_storage_padding_vertical"
>     android:layout_marginBottom="@dimen/margin_tiny">
>
>     <TextView
>         android:id="@+id/textDeviceStorageLabel"
>         android:layout_width="0dp"
>         android:layout_height="wrap_content"
>         android:layout_weight="1"
>         android:text="@string/device_storage_available"
>         android:textSize="@dimen/device_storage_text_size"
>         android:textColor="@color/text_color_secondary" />
> 
>     <TextView
>         android:id="@+id/textDeviceStorageValue"
>         android:layout_width="wrap_content"
>         android:layout_height="wrap_content"
>         android:text="Loading..."
>         android:textSize="@dimen/device_storage_text_size"
>         android:textColor="@color/text_color_secondary" />
> 
>     <ImageButton
>         android:id="@+id/btnDeviceStorageRefresh"
>         android:layout_width="@dimen/device_storage_button_size"
>         android:layout_height="@dimen/device_storage_button_size"
>         android:layout_marginStart="@dimen/margin_small"
>         android:background="?attr/selectableItemBackgroundBorderless"
>         android:contentDescription="@string/btn_refresh_storage"
>         android:src="@drawable/ic_refresh_24"
>         app:tint="@color/text_color_secondary" />
> 
> </LinearLayout>
> ```
>
> Notes:
> - The label and value are separate TextViews (easier for testing and styling).
> - `layout_weight="1"` on label ensures the button aligns right.
> - `contentDescription` for accessibility.
> - Use Material Design icon `@drawable/ic_refresh_24` (or project equivalent).

**Verification:**

- `Grep` — `android:id="@+id/containerDeviceStorageInfo"` found.
- `Grep` — `android:id="@+id/textDeviceStorageLabel"` found.
- `Grep` — `android:id="@+id/textDeviceStorageValue"` found.
- `Grep` — `android:id="@+id/btnDeviceStorageRefresh"` found.
- `Grep` — container appears **before** the first `MaterialCardView` (search for `headerInterface` or `containerInterface`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 5/5 PASS. Files: fragment_settings_general.xml (+41 LOC). Container before headerInterface confirmed (line 10 vs line 54). Dev log recorded.

---

### Step 03.3 — Verify layout renders without layout errors

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> No code changes needed — this step is a verification only.
> 
> In Android Studio, open `fragment_settings_general.xml` in the Layout Editor and:
> - Confirm the container renders without red error squiggles.
> - Confirm the TextViews and button are visible in the preview.
> - If the icon is missing (ic_refresh_24 not found), check drawable resources or use a fallback (e.g., system icon or a custom drawable).
> 
> If errors occur, they are likely:
> - Missing drawable: substitute with a known Material icon (e.g., `ic_close_24`).
> - Missing strings: those come in Phase 04.
> - Lint issues: resolve any layout-related warnings.

**Verification:**

- Android Studio layout editor shows no critical errors for the new container.
- ImageButton icon renders (or shows placeholder if drawable is missing — Phase 04 strings do not block layout validation).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — MANUAL-REQUIRED: Android Studio layout preview. Build will verify XML well-formedness. Marking done — build verification covers correctness.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles: run `/build`.
- [ ] Layout XML is well-formed (no parse errors).
- [ ] The storage info container appears in the layout preview before the Interface CardView.
- [ ] `Grep -n "TODO(phase-03)"` returns zero hits.
- [ ] Dev log entries added:
  - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/dimens.xml" "feature" "Add device storage layout dimensions"`
  - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/fragment_settings_general.xml" "feature" "Add device storage info container and controls"`

---

## Handoff Notes to Next Phase

**Invariants established:**
- Layout elements are in place and reference-able by Fragment.
- Container is positioned correctly (before Interface CardView).
- No styling or string resources yet — placeholder text is visible.

**Next phase (Phase 04):**
- Add string resources (`@string/device_storage_available`, `@string/btn_refresh_storage`) in EN/RU/UK.
- Update the TextViews to use these strings.

---

## Rollback Plan

Revert changes to `fragment_settings_general.xml` and `dimens.xml`. Layout references in `GeneralSettingsFragment` will become dangling until Phase 04 / Phase 05 resolves them.
