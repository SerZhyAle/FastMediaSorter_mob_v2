# Phase 02 - Register messenger targets + manifest queries

**Strategic spec:** [`../S0446_messenger-share-settings.md`](../S0446_messenger-share-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (title strings)
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Register three `ShareTarget`s (`telegram`, `whatsapp`, `instagram`) into the S0452 registry via Hilt multibinding, and declare WhatsApp/Instagram package visibility in the manifest. After this phase the three toggles auto-appear in the "Команды отправить файл в.." settings group (the settings fragment already renders one row per registered target and gates each by `ShareTargetAvailabilityResolver`); Telegram/WhatsApp/Instagram show disabled with "Not installed" when their client is absent. No menu-gating or send behavior yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`R.string.share_to_whatsapp` / `share_to_instagram` exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/WhatsAppShareTargets.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/InstagramShareTargets.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetModule.kt` | Modified | ≤ 120 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | + ~4 lines |

---

## Steps

### Step 02.1 - Package catalogues for WhatsApp & Instagram

**Files:** `core/share/WhatsAppShareTargets.kt`, `core/share/InstagramShareTargets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Mirror the existing `core/share/TelegramShareTargets.kt` pattern: two stateless `object`s, each holding `PACKAGE_IDS: List<String>` (preference-ordered) and `firstInstalledPackage(packageManager): String?` (`getPackageInfo` probe, false on `NameNotFoundException`). WhatsApp candidates: `com.whatsapp` then `com.whatsapp.w4b` (Business). Instagram candidate: `com.instagram.android`. No UI, no network. The final WhatsApp package list/order is pending Blocker B3 - start with these and adjust if research changes it.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `object WhatsAppShareTargets` and `object InstagramShareTargets` each match once.
- `Grep` - `com.whatsapp` and `com.instagram.android` present.
- `Grep -n "Log\.d\("` - zero hits in both files.

**Status:** `[ ]` not done

---

### Step 02.2 - Contribute three `ShareTarget`s via `@IntoSet`

**Files:** `core/share/di/ShareTargetModule.kt`
**Depends on:** Step 02.1, Phase 01

**Prompt for developer:**

> In the existing `ShareTargetModule` (currently only `@Multibinds`), add three `@Provides @IntoSet` functions returning `ShareTarget`, all with `defaultEnabled = ShareTargetDefault.ALWAYS_OFF` and `availability = ShareTargetAvailability.PACKAGE_INSTALLED`:
> - `telegram` - `titleRes = R.string.share_to_telegram`, `packages = TelegramShareTargets.PACKAGE_IDS`.
> - `whatsapp` - `titleRes = R.string.share_to_whatsapp`, `packages = WhatsAppShareTargets.PACKAGE_IDS`.
> - `instagram` - `titleRes = R.string.share_to_instagram`, `packages = InstagramShareTargets.PACKAGE_IDS`.
>
> Do not duplicate package lists - reference the catalogue objects. Keep ids exactly `"telegram"`, `"whatsapp"`, `"instagram"` (these are the DataStore tokens and gating keys used in Phases 04/05). `@Multibinds` may need to coexist with `@Provides` - if Dagger rejects both in one abstract class, split provider methods into a companion `object` module per the project's Hilt convention.

**Verification:**

- `Grep` - `@IntoSet` matches ≥ 3 times in `ShareTargetModule.kt`.
- `Grep` - `"telegram"`, `"whatsapp"`, `"instagram"` id literals all present.
- `Grep` - `ALWAYS_OFF` present (default-off mandate).
- `.\a.ps1 fk` - Kotlin/Hilt graph compiles.

**Status:** `[ ]` not done

---

### Step 02.3 - Declare WhatsApp/Instagram package visibility (API 30+)

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the existing `<queries>` block (which already declares Keep packages), add `<package android:name="..."/>` entries for `com.whatsapp`, `com.whatsapp.w4b`, `com.instagram.android`. Without these, `PackageManager.getPackageInfo` returns NameNotFound on API 30+ and the availability resolver would report the messenger uninstalled even when present (the toggle would stay disabled and the command hidden). Telegram visibility is already declared (S0303) - do not duplicate it.

**Verification:**

- `Grep` - `com.whatsapp`, `com.whatsapp.w4b`, `com.instagram.android` present inside the manifest `<queries>` block.
- `.\a.ps1 fr` - manifest/resources build passes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `.\a.ps1 fk` (Kotlin + Hilt) and `.\a.ps1 fr` (manifest) pass.
- [ ] Three toggles render in the "Send file to.." settings group on a manual open (deferred to device verification; statically: registry now returns 3 targets).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Registry now returns `telegram` / `whatsapp` / `instagram`; `ShareTargetAvailabilityResolver.isAvailable(target)` answers installed-ness using the new manifest visibility.
- Gating keys for Phases 04/05 are the exact ids `"telegram"`, `"whatsapp"`, `"instagram"`.

---

## Rollback Plan

Remove the three `@IntoSet` providers (registry returns to empty -> settings group hides), delete the two catalogue files, and drop the manifest `<package>` entries. No data migration.
