# Phase 01 — Environment Detection Foundation

**Strategic spec:** [`../S0082_chromeos-support.md`](../S0082_chromeos-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Introduce `ChromeOsCompat` — the single cached source of truth for ARC++ environment detection — so all downstream phases can branch without scattering raw `hasSystemFeature` calls.

---

## Prerequisites

- [ ] All Pre-Implementation Blockers in INDEX.md are unchecked — resolve them before starting.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/compat/ChromeOsCompat.kt` | **New** | ≤ 70 |

---

## Steps

### Step 1.1 — Create ChromeOsCompat utility object

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/compat/ChromeOsCompat.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `ChromeOsCompat.kt` in `core/compat/`. It is a top-level Kotlin `object` (no Hilt injection — it needs no lifecycle and is called before DI is ready in some sites). Expose two functions:
>
> - `isChromeOs(context: Context): Boolean` — returns `true` if `packageManager.hasSystemFeature("org.chromium.arc")`. Cache the result in a private `@Volatile var` on first call; subsequent calls return the cached value without re-querying the PackageManager.
> - `needsSafFolderPicker(context: Context): Boolean` — returns `true` if `isChromeOs(context)` OR if `Environment.isExternalStorageManager()` returns `false` on API 30+ (i.e. `MANAGE_EXTERNAL_STORAGE` is not granted). On API < 30 returns `false` (legacy storage model, SAF not needed).
>
> Use Timber for a single `Timber.d("ChromeOsCompat: isChromeOs=$result")` log on first detection. No other logging.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/compat/ChromeOsCompat.kt` exists.
- `Grep` — `object ChromeOsCompat` matches exactly once in that file.
- `Grep` — `fun isChromeOs` present in that file.
- `Grep` — `fun needsSafFolderPicker` present in that file.
- `Grep` — `@Volatile` present in that file (proves caching).
- `Grep` for `hasSystemFeature("org.chromium.arc")` in `app_v2/src/main/java/**/*.kt` — matches exactly once, in `ChromeOsCompat.kt`.

**Status:** `[ ]` not done

---

### Step 1.2 — Verify no scattered ARC++ feature checks exist

**Files:** read-only audit, no changes
**Depends on:** Step 1.1

**Prompt for developer:**

> Grep the entire `app_v2/src/main/java` tree for `org.chromium.arc`. The only match must be inside `ChromeOsCompat.kt`. If any other file contains the string, extract the check into a call to `ChromeOsCompat.isChromeOs(context)` instead.

**Verification:**

- `Grep` for `org.chromium.arc` in `app_v2/src/main/java/**/*.kt` — exactly one match, in `ChromeOsCompat.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `ChromeOsCompat.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`ChromeOsCompat.isChromeOs(context)` and `ChromeOsCompat.needsSafFolderPicker(context)` are available for all subsequent phases. No Hilt module needed — call directly with any `Context`.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
