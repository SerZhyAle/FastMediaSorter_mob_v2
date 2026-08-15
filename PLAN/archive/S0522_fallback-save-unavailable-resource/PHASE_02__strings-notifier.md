# Phase 02 - Strings & Fallback Notifier

**Strategic spec:** [`../S0522_fallback-save-unavailable-resource.md`](../S0522_fallback-save-unavailable-resource.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05
**Steps done:** 3 / 3
**Started:** 2026-06-18
**Completed:** 2026-06-18

---

## Objective

Provide the single user-notification surface for an unavailability fallback: trilingual strings plus a `SaveFallbackNotifier` that shows a foreground toast or, for background flows, a system notification - only when the reason is `ResourceUnavailable`/`ResourceWriteFailed`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/save/SaveFallbackNotifier.kt` | New | ≤ 180 |

---

## Steps

### Step 02.1 - Add trilingual fallback-notice strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two string keys across EN/RU/UK in lockstep. (1) `save_fallback_resource_unavailable` - the notice text, `%1$s` = local folder label, `%2$s` = configured resource name (EN "Saved to %1$s - %2$s is unavailable"; RU "Сохранено в %1$s - %2$s недоступен"; UK "Збережено в %1$s - %2$s недоступний"). (2) `save_fallback_channel_name` - the system notification-channel label shown in Android settings (EN "Save fallback"; RU "Запасное сохранение"; UK "Запасне збереження"). Use `scripts/utils/set-android-string.ps1 -Action add` once per key (parity-enforced). Cyrillic literals must not be passed as pwsh CLI args from a Bash shell (mojibake) - run the script from pwsh directly, or author a small UTF-8 `.ps1`. Both keys must follow `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Grep` - `name="save_fallback_resource_unavailable"` and `name="save_fallback_channel_name"` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "save_fallback"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-18 - Verification 3/3 PASS. Added save_fallback_resource_unavailable + save_fallback_channel_name across EN/RU/UK; audit exit 0.

---

### Step 02.2 - Add SaveFallbackNotifier

**Files:** `core/save/SaveFallbackNotifier.kt` (New)
**Depends on:** Step 02.1, Phase 01 Step 01.2

**Prompt for developer:**

> Create `@Singleton class SaveFallbackNotifier @Inject constructor(@ApplicationContext context)`. Expose `fun notifyForeground(folderLabel: String, resourceName: String)` showing a `Toast` with `save_fallback_resource_unavailable`, and `fun notifyBackground(folderLabel: String, resourceName: String)` posting a system notification via `NotificationManagerCompat` on a dedicated low-importance channel created lazily (id `save_fallback`, channel name from `R.string.save_fallback_channel_name`). Add `fun notify(reason: SaveFallbackReason, folderLabel: String, resourceName: String, background: Boolean)` that does nothing for `NoResourceConfigured` and routes the other reasons to the foreground/background method by the `background` flag. Use `Timber` only; guard `POST_NOTIFICATIONS` on API 33+ by simply attempting the post inside the existing notification-permission model (no new permission request here - if the post throws/returns, log at `Timber.i` and move on, the file is already saved).

**Verification:**

- `Glob` - `core/save/SaveFallbackNotifier.kt` exists.
- `Grep` - `class SaveFallbackNotifier` matches once with `@Singleton`.
- `Grep` - `fun notify(`, `fun notifyForeground(`, `fun notifyBackground(` each present.
- `Grep` - `NoResourceConfigured` referenced (the silent branch).
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-18 - Verification 5/5 PASS. Created core/save/SaveFallbackNotifier.kt (Toast + system notification on low-importance channel, silent for NoResourceConfigured).

---

### Step 02.3 - String locale audit

**Files:** (verification only - no source edit)
**Depends on:** Step 02.1

**Prompt for developer:**

> Run the locale audit for the new key and fix any parity gap before proceeding.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "save_fallback"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-18 - Verification PASS. Locale audit for save_fallback prefix exit 0 (both keys EN/RU/UK).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class added).

---

## Handoff Notes to Next Phase

`SaveFallbackNotifier.notify(reason, folderLabel, resourceName, background)` is the only notification entry point. Save flows pass `background = true` for gesture-screenshot and worker-driven download, `false` for in-screen actions. Silent for `NoResourceConfigured`.

---

## Rollback Plan

Revert phase commit(s) and remove the added string key via `set-android-string.ps1 -Action remove`. No data migration or persisted surface changed.
