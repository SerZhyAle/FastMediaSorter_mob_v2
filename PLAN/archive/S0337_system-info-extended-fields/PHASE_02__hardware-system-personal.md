# Phase 02 - Hardware, System config, Personal fields

**Strategic spec:** [`../S0337_system-info-extended-fields.md`](../S0337_system-info-extended-fields.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Add the Hardware section, the System-config section, and the permission-free Personal fields to the summary, all with localized labels and API-level gating.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> If the use case projects >500 lines after edit, create a timestamped backup in `temp/` first.

---

## Steps

### Step 02.1 - Add Hardware section

**Files:** `GatherSystemInfoUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a Hardware section builder returning: logical CPU cores (`Runtime.getRuntime().availableProcessors()`), supported ABIs (`Build.SUPPORTED_ABIS` joined), SoC manufacturer+model (`Build.SOC_MANUFACTURER`/`Build.SOC_MODEL`, only on API ≥ 31 — else `Build.HARDWARE`), kernel version (`System.getProperty("os.version")`), GLES version (`ActivityManager.getDeviceConfigurationInfo().glEsVersion`), max heap (`Runtime.getRuntime().maxMemory()` formatted), and 64-bit process (`Process.is64Bit()`, API ≥ 23). Gate API-31 fields with `Build.VERSION.SDK_INT` and fall back to `unknown`/`Build.HARDWARE`. Use the defensive `safe`/`safeList` wrappers.

**Verification:**

- `Grep` - Hardware section builder present (e.g. `sysinfo_section_hardware` referenced in use case).
- `Grep` - `Build.SUPPORTED_ABIS` and `availableProcessors` present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS (section_hardware=1, SUPPORTED_ABIS=1, availableProcessors=1, Log.d=0). Compile gate at phase end.

---

### Step 02.2 - Add System-config + Personal fields section(s)

**Files:** `GatherSystemInfoUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a System-config section: locales list (`LocaleList.getDefault()` joined, API ≥ 24 else single locale), 24-hour format (`DateFormat.is24HourFormat(context)`), boot count (`Settings.Global.getInt(cr, BOOT_COUNT)`, API ≥ 24), security patch (`Build.VERSION.SECURITY_PATCH`), build fingerprint/tags/type (`Build.FINGERPRINT`, `Build.TAGS`, `Build.TYPE`), installer/install source (`PackageManager.getInstallSourceInfo(pkg).installingPackageName` on API ≥ 30 else `getInstallerPackageName(pkg)`), first install + last update time (`PackageInfo.firstInstallTime`/`lastUpdateTime`, formatted). Add Personal fields (included per owner decision §6.6): device name (`Settings.Global.getString(cr, Settings.Global.DEVICE_NAME)`, API ≥ 25), default input method id (`Settings.Secure.getString(cr, DEFAULT_INPUT_METHOD)`). User name already exists from S0335 (keep). API-gate every field with `unknown` fallback.

**Verification:**

- `Grep` - `Settings.Global.DEVICE_NAME` and `firstInstallTime` present.
- `Grep` - `SECURITY_PATCH` and `is24HourFormat` present.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS (DEVICE_NAME=1, firstInstallTime=1, SECURITY_PATCH=1, is24HourFormat=1, Log.d=0). Compile gate at phase end.

---

### Step 02.3 - Add localized strings for Hardware + System + Personal (EN/RU/UK)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `sysinfo_section_hardware`, `sysinfo_section_system` headers and `sysinfo_field_*` labels for every field added in 02.1/02.2 (cpu cores, abis, soc, kernel, gles, max heap, process bits, locales, hour format, boot count, security patch, fingerprint, tags, build type, installer, first install, last update, device name, input method). Real EN/RU/UK values; Author Style; pass §6 tone checklist.

**Verification:**

- `Grep` - `sysinfo_section_hardware` and `sysinfo_section_system` present in each of the three files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "sysinfo_"` exits 0 (expected 0 | actual record).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. 48 sysinfo_ keys present EN/RU/UK; EXIT=0 (expected 0 | actual 0). Neutral labels pass §6. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Hardware and System sections established; pattern for API-gated fields proven.

---

## Rollback Plan

Revert phase commit(s) - additive sections only, no data migration.
