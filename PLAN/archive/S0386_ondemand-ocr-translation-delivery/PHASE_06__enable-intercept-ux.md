# Phase 06 - Enable-Intercept & Download UX

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-06-09
**Completed:** 2026-06-09

---

## Objective

Intercept every point that turns OCR/translation/DTS on and, when the set is not installed, show a download offer with a size estimate, progress, and a success/error/refusal outcome - soft unavailability on refusal, no crash (strategic Pillar D, criteria §11.3/§11.5).

---

## Prerequisites

- [ ] Phase 03 ✅ Done (default OFF + migration flag).
- [ ] Phase 04 ✅ Done (downloader available).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/DeliveryPromptViewModel.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/DeliveryPromptDialogFragment.kt` | New | ≤ 300 |
| `app_v2/src/main/res/layout/dialog_delivery_prompt.xml` | New | ≤ 200 |
| `app_v2/src/main/res/layout-land/dialog_delivery_prompt.xml` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/DeliveryEnableInterceptor.kt` | New | ≤ 180 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

---

## Steps

### Step 06.1 - Prompt ViewModel over the downloader

**Files:** `ui/delivery/DeliveryPromptViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `DeliveryPromptViewModel` that exposes, for a requested `DeliverableSet`: the estimated download size (from `DeliverableSourceDescriptor`), and a state machine driven by `DeliverableSetDownloader.download(set)` covering offer → downloading(percent) → verifying → success → error → refused. On refusal it leaves the capability `DISABLED_BY_USER`. Collect the downloader Flow with `collectOnLifecycle`/`repeatOnLifecycle`, never a bare `lifecycleScope.launch { collect {} }` (Rule 20).

**Verification:**

- `Grep` - `class DeliveryPromptViewModel` matches once.
- `Grep` - `DeliverableSetDownloader` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 2/2 PASS. New: `ui/delivery/DeliveryPromptViewModel.kt` (+78 LOC, `@HiltViewModel`). `DeliveryPromptUiState` state machine (Idle/Offer/Downloading/Verifying/Success/Error/Refused); size estimate from descriptor `minSize` sum; collects downloader Flow in `viewModelScope`. Dev log recorded.

---

### Step 06.2 - Prompt dialog with portrait + landscape layouts

**Files:** `ui/delivery/DeliveryPromptDialogFragment.kt`, `res/layout/dialog_delivery_prompt.xml`, `res/layout-land/dialog_delivery_prompt.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Create `DeliveryPromptDialogFragment` showing the offer (with size), a progress indicator bound to the ViewModel state, and success/error/refusal results. Provide both portrait (`res/layout/`) and landscape (`res/layout-land/`) layouts. Accessibility: TalkBack labels, correct focus order, non-color-only state distinction; keyboard/D-pad/mouse focusability per Rule 17. Keep content inside `systemBars` + `displayCutout` safe bounds (Rule 18). No hardcoded `="#hex"` colors - use `?attr/`/`@color/` (Rule 20).

**Verification:**

- `Grep` - `class DeliveryPromptDialogFragment` matches once.
- `Glob` - both `res/layout/dialog_delivery_prompt.xml` and `res/layout-land/dialog_delivery_prompt.xml` exist.
- `Grep` - zero `"#` hex color literals in both layout files.
- `Grep` - `contentDescription` present in at least one layout file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 4/4 PASS. New: `ui/delivery/DeliveryPromptDialogFragment.kt` (+135 LOC, `@AndroidEntryPoint`, outcome via `setFragmentResult`) + portrait/landscape `dialog_delivery_prompt.xml` (LinearProgressIndicator, `?attr/` colors only, polite live region, contentDescription, in-layout focusable buttons). Strings referenced (added in 06.3). Dev log recorded.

---

### Step 06.3 - Add trilingual strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 06.2

**Prompt for developer:**

> Add the offer/size/progress/success/error/refusal/"default changed" strings in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>` per key (parity-enforced). Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (message formula per type) and §6 (tone checklist). Use `ё/Ё` in Russian.

**Verification:**

- `Grep` - each new key present in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (three hits per key).
- `Bash` - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "delivery_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 3/3 PASS. Added 10 `delivery_*` keys in EN/RU/UK lockstep via `set-android-string.ps1 -Action add` (UTF-8 driver script `temp/s0386_add_delivery_strings.ps1` to keep Cyrillic intact). Localization audit exit 0; §6 tone-checklist clean (human error w/ next step, no raw exception, fits 360dp). Error string de-interpolated in fragment (§2.2). Dev log recorded.

---

### Step 06.4 - Interceptor at the enable points

**Files:** `ui/delivery/DeliveryEnableInterceptor.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Create `DeliveryEnableInterceptor` invoked from the OCR/translation settings toggles, the player/PDF/EPUB/text OCR-translate triggers, the camera-OCR trigger, and the DTS-playback trigger: if the matching set is `NOT_INSTALLED`, show `DeliveryPromptDialogFragment`; otherwise proceed as today. Map each UI placement contract point (strategic §3.3 UI placement) to its `DeliverableSet`. Refusal returns control with the capability left off and a soft-unavailable message.

**Verification:**

- `Grep` - `class DeliveryEnableInterceptor` matches once.
- `Grep` - `NOT_INSTALLED` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 2/2 PASS. New: `ui/delivery/DeliveryEnableInterceptor.kt` (+58 LOC, `@Singleton @Inject`). `requireInstalled(host, set, onReady, onUnavailable)`: gate via `isInstalledBlocking`; on miss shows prompt + listens `setFragmentResultListener`, INSTALLED→onReady, REFUSED/FAILED→onUnavailable. UI-placement→set map in KDoc. Dev log recorded.

---

### Step 06.5 - Wire interceptor into the toggle + trigger call-sites

**Files:** `ui/settings/fragments/OtherMediaSettingsFragment.kt`, `ui/player/helpers/TranslationButtonManager.kt`, `ui/cameraocr/helpers/CameraOcrFlowManager.kt`
**Depends on:** Step 06.4

**Prompt for developer:**

> Route the existing OCR/translation enable actions and the camera-OCR entry through `DeliveryEnableInterceptor`. Settings toggle ON for an uninstalled set triggers the prompt; if the user refuses, leave the toggle visually OFF. Keep UI layer free of business logic - delegate to the interceptor/ViewModel.

**Verification:**

- `Grep` - `DeliveryEnableInterceptor` referenced in all three call-site files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 1/1 PASS (interceptor referenced in all 3 files). `OtherMediaSettingsFragment` → `@AndroidEntryPoint` + `@Inject` interceptor; translation/OCR toggles gate ON via `requireInstalled`, revert to OFF on refusal. `TranslationButtonManager` btnOk and `CameraOcrFlowManager.startCapture` resolve the `@Singleton` interceptor via `EntryPointAccessors` (no constructor cascade) and gate through the new `FragmentActivity` overload. Supporting edit: `DeliveryEnableInterceptor.kt` gained a `FragmentActivity` overload + co-located `@EntryPoint`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "delivery_"` exits 0.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Enabling an uninstalled set now drives the downloader through the prompt UX. Phase 07 makes the installed payload actually attach and run, and ensures re-enabling an installed set skips the prompt.

---

## Rollback Plan

Revert phase commit(s). UI-only addition; reverting restores direct enable behavior (which still works because Phase 05's stripped artifacts would be re-bundled only if Phase 05 is also reverted). No data migration.
