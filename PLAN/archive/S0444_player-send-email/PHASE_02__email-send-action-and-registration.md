# Phase 02 - Email send action and registration

**Strategic spec:** [`../S0444_player-send-email.md`](../S0444_player-send-email.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (provides `R.string.share_to_email`) + S0452 registry (already Verified)
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Add the Email send capability and register it as the first real `ShareTarget` (`id = "email"`) into the S0452 multibinding. After this phase the toggle auto-appears in the settings "Send file to.." group (no settings-UI code), but no player-menu command is wired yet. No strings, menu, or gating changes here.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done: `R.string.share_to_email` exists in all three locales (the title this phase's `ShareTarget` references).
- [ ] S0452 foundation present: `core/share/ShareTarget.kt`, `ShareTargetRegistry.kt`, `ShareTargetAvailabilityResolver.kt`, `di/ShareTargetModule.kt`, `domain/usecase/IsShareTargetEnabledUseCase.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/EmailShareInvoker.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/EmailShareTargetModule.kt` | New | ≤ 60 |
| `app_v2/src/main/AndroidManifest.xml` | Modified (conditional - see Step 02.1) | - |

---

## Steps

### Step 02.1 - Email send invoker

**Files:** `core/share/EmailShareInvoker.kt` (+ conditional `AndroidManifest.xml`)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `EmailShareInvoker` - a host-agnostic object/helper that launches an email compose intent with one file attachment. Use `Intent.ACTION_SEND` with `type = "message/rfc822"`, `putExtra(Intent.EXTRA_STREAM, uri)`, `clipData = ClipData.newRawUri(null, uri)`, and `addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)`, launched via `Intent.createChooser(..)`. Do NOT use `ACTION_SENDTO`/`mailto:` - it drops the attachment on most mail clients (strategic ADR-1). Prefer to reuse the existing `SystemShareInvoker.invokeFiles(context, uris, mime = "message/rfc822", chooserTitle = ..)` rather than hand-rolling a second `ACTION_SEND` path; if `SystemShareInvoker` is reused directly, this file may be a thin wrapper or omitted (fold into the manager call in Phase 03). The chooser title is `R.string.share_to_email`, already added in Phase 01, so it resolves now - no placeholder needed.
>
> Manifest (conditional): with `availability = ALWAYS` + chooser launch there is NO package probe, so a `<queries>` entry is NOT required. Add an `<intent>` query for `ACTION_SEND` + `message/rfc822` to `app_v2/src/main/AndroidManifest.xml` ONLY if you decide to pre-resolve / prefer a specific mail package via `resolveActivity`/`getPackageInfo`. Default decision: do not add; leave the manifest unchanged.

**Verification:**

- `Glob` - `core/share/EmailShareInvoker.kt` exists (or, if folded into the manager, this step's grep targets the manager file instead).
- `Grep -n "ACTION_SENDTO"` in the new/changed share code - zero hits.
- `Grep -n "message/rfc822"` - present in the send path.
- `Grep -n "Log\.d\("` in the touched file - zero hits.

**Status:** `[ ]` not done

---

### Step 02.2 - Email `ShareTarget` declaration + `@IntoSet` module

**Files:** `core/share/di/EmailShareTargetModule.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `EmailShareTargetModule` (`@Module @InstallIn(SingletonComponent::class)`) that contributes the Email `ShareTarget` to the S0452 multibinding via `@Provides @IntoSet`. Build the target as: `ShareTarget(id = "email", titleRes = R.string.share_to_email, iconRes = <decided in Phase 03; null is fine here>, defaultEnabled = ShareTargetDefault.ON_IF_INTERNET, availability = ShareTargetAvailability.ALWAYS, packages = emptyList())`. This is the first real entry in the (previously empty) `Set<ShareTarget>` - the S0452 `@Multibinds` seam in `ShareTargetModule` already makes the set injectable. Do NOT edit `ShareTargetRegistry`, `ShareTargetAvailabilityResolver`, `ShareTargetModule`, or `IsShareTargetEnabledUseCase` (foundation is closed for extension by registration only). `R.string.share_to_email` was added in Phase 01, so this reference resolves at build time.

**Verification:**

- `Glob` - `core/share/di/EmailShareTargetModule.kt` exists.
- `Grep -n "@IntoSet"` - present in the module.
- `Grep -n "id = \"email\""` - present (the stable registry/DataStore token).
- `Grep -n "ShareTargetDefault.ON_IF_INTERNET"` and `ShareTargetAvailability.ALWAYS` - both present.
- `Grep -n "class ShareTargetRegistry|class ShareTargetAvailabilityResolver"` over `core/share/ShareTargetRegistry.kt`/`ShareTargetAvailabilityResolver.kt` - unchanged (no diff to foundation classes).

**Status:** `[ ]` not done

---

### Step 02.3 - Build-green with the registry no longer empty

**Files:** (no new files - validation step)
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Confirm the Hilt graph still compiles now that `Set<ShareTarget>` has one element. Run `.\a.ps1 fk` (or `assembleStandardDebug`). The settings "Send file to.." group, previously hidden while the registry was empty, now renders one row ("Send to Email") with no extra settings code (S0452 `PlaybackSettingsFragment.setupSendCommandsGroup` reads `shareTargetRegistry.all()`). This is a side effect to expect, not a task.

**Verification:**

- `.\a.ps1 fk` exit 0 (record PASS/FAIL + log path).
- `Grep -n "setupSendCommandsGroup"` over `PlaybackSettingsFragment.kt` - unchanged (no diff; group auto-renders).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` PASS (record log path).
- [ ] `Grep` for `ACTION_SENDTO` across `core/share/` - zero hits.
- [ ] Foundation classes (`ShareTargetRegistry`, `ShareTargetAvailabilityResolver`, `ShareTargetModule`, `IsShareTargetEnabledUseCase`) are unmodified.
- [ ] Dev-log + catalog deferred to Phase 04, per plan.

---

## Handoff Notes to Next Phase

- The Email target is now registered; `IsShareTargetEnabledUseCase("email", settings)` and `ShareTargetAvailabilityResolver.isAvailable(emailTarget)` are the gate Phase 03 must wire into the player menu.
- `EmailShareInvoker` (or the reused `SystemShareInvoker.invokeFiles(.., "message/rfc822", ..)`) is the send entry point Phase 03's callback calls.
- The settings toggle already works end-to-end after this phase; only the menu command remains.

---

## Rollback Plan

Revert phase commit(s) - new files only. Removing `EmailShareTargetModule` empties the registry again and re-hides the settings group; no data migration.
