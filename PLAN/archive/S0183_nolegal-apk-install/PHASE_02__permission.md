# Phase 02 — permission

**Strategic spec:** [`../S0183_nolegal-apk-install.md`](../S0183_nolegal-apk-install.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Add `REQUEST_INSTALL_PACKAGES` permission exclusively to the noLegal flavor manifest. Market flavors must not see this permission.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/AndroidManifest.xml` | Modified | +1 line |

---

## Steps

### Step 2.1 — Add `REQUEST_INSTALL_PACKAGES` to noLegal manifest

**Files:** `app_v2/src/noLegal/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Open `app_v2/src/noLegal/AndroidManifest.xml`. Add the following line inside the `<manifest>` root element, before `<application>`:
> ```xml
> <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
> ```
> Retain the existing `<application android:extractNativeLibs="true" .../>` element unchanged.
>
> Verify that `app_v2/src/main/AndroidManifest.xml` does **not** contain `REQUEST_INSTALL_PACKAGES` (it must stay in noLegal only).

**Verification:**

- `Grep` in `app_v2/src/noLegal/AndroidManifest.xml` — `REQUEST_INSTALL_PACKAGES` present.
- `Grep` in `app_v2/src/main/AndroidManifest.xml` — `REQUEST_INSTALL_PACKAGES` — zero hits.
- `Grep` in `app_v2/src/standard/AndroidManifest.xml` (if file exists) — `REQUEST_INSTALL_PACKAGES` — zero hits.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 2/2 PASS. noLegal manifest +1 line, main manifest zero hits. Dev log recorded.

---

## Phase Done Criteria

- [ ] Step 2.1 is `[x] done`.
- [ ] `assembleNoLegalDebug` APK: `aapt dump permissions <apk>` lists `android.permission.REQUEST_INSTALL_PACKAGES`.
- [ ] `assembleStandardDebug` APK: `aapt dump permissions <apk>` does **not** list `REQUEST_INSTALL_PACKAGES`.
- [ ] Dev log entry: `.\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/AndroidManifest.xml" "S0183" "Add REQUEST_INSTALL_PACKAGES permission (noLegal only)"`.

---

## Handoff Notes to Next Phase

`REQUEST_INSTALL_PACKAGES` is now declared for noLegal. `PackageManager.canRequestPackageInstalls()` will return a meaningful value at runtime. The permission requires no runtime grant dialog — it is toggled by the user via `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`.

---

## Rollback Plan

Remove the one `<uses-permission>` line from `src/noLegal/AndroidManifest.xml`. No data migration.
