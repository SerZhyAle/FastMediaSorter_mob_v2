# Phase 02 - Wire menu icons

**Strategic spec:** [`../S0478_send-to-menu-icons.md`](../S0478_send-to-menu-icons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Give every receiver a per-target `iconRes`, render an icon on each overflow-submenu item (real app icon, else the target glyph), and document the icon-policy reversal.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (six new drawables exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetModule.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToMenuManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTargetIconResolver.kt` | Modified | ≤ 90 |

---

## Steps

### Step 02.1 - Assign iconRes to all ten receivers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetModule.kt`

**Depends on:** Phase 01

**Prompt for developer:**

> Set the `iconRes` argument on every `ShareTarget(..)` declaration: `system_share` = `R.drawable.ic_share`; `open_in` = `R.drawable.ic_open_in_browse`; `print` = `R.drawable.ic_print`; `email` = `R.drawable.ic_send_email`; `keep_text` = `R.drawable.ic_send_note`; `keep_drawing` = `R.drawable.ic_send_note_brush`; `lens` = `R.drawable.ic_google_lens`; `telegram` = `R.drawable.ic_send_plane`; `whatsapp` = `R.drawable.ic_send_chat`; `instagram` = `R.drawable.ic_send_camera`. Brand analogs are the fallback glyph only - the installed app icon still wins at render time (Step 02.2). Do not change ids, titles, availability, applicable types, or batch flags.

**Verification:**

- `Grep` - `iconRes = R.drawable.ic_send_camera` present (instagram analog wired).
- `Grep` - `iconRes = R.drawable.ic_send_plane` present.
- `Grep` - `iconRes = R.drawable.ic_send_chat` present.
- `Grep -c` - `iconRes = R.drawable` in `ShareTargetModule.kt` returns 10.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS (10× `iconRes = R.drawable`; camera/plane/chat present). Files: ShareTargetModule.kt (+10 LOC).

---

### Step 02.2 - Render an icon on each overflow-submenu item

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToMenuManager.kt`

**Depends on:** Step 02.1

**Prompt for developer:**

> In `buildOverflowSubMenu`, set an icon on each receiver `MenuItem` added in the loop: resolve `iconResolver.resolveIcon(target)` (installed app icon) and, when null, fall back to the target's `iconRes` loaded via `ContextCompat.getDrawable(activity, target.iconRes)`. Apply the drawable with `setIcon(..)` on the item before attaching the click listener. The parent submenu item keeps its existing `ic_share`. The player overflow `PopupMenu` already calls `setForceShowIcon(true)` in `CommandPanelController`, so the icons will render in the nested popup.

**Verification:**

- `Grep` - `iconResolver.resolveIcon(target)` present in `SendToMenuManager.kt`.
- `Grep` - `ContextCompat.getDrawable` present in `SendToMenuManager.kt`.
- `Grep` - `.setIcon(` present inside `buildOverflowSubMenu` (receiver-item icon, not only the submenu header item).
- `Grep -n "Log\.d\("` on `SendToMenuManager.kt` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS (`item.setIcon(` receiver-item at line 109; `resolveIcon`/`ContextCompat.getDrawable` present; `Log.d` 0). Files: SendToMenuManager.kt (+9 LOC, +1 import).

---

### Step 02.3 - Document the ADR-5 reversal in the icon resolver

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTargetIconResolver.kt`

**Depends on:** Step 02.1

**Prompt for developer:**

> Update the class KDoc to record the S0478 reversal of S0459 ADR-5: a package-backed target still prefers the installed app's launcher icon; a logical or not-installed target now falls back to its own meaningful per-target glyph (`ShareTarget.iconRes`) instead of the generic share glyph. No behaviour change in this file - `resolveIcon` still returns the app icon or null; the per-target glyph fallback lives in the presentations. Keep the comment WHY-focused, no restating of code.

**Verification:**

- `Grep` - `S0478` present in `ShareTargetIconResolver.kt` (KDoc reference to the reversal).
- `Grep` - `per-target glyph` or `iconRes` referenced in the updated KDoc.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (`S0478` + `per-target [ShareTarget.iconRes] glyph` in KDoc). Files: ShareTargetIconResolver.kt (KDoc only).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `verifyNoPlatformNames` passes (run `.\gradlew.bat :app_v2:verifyNoPlatformNames`) - no brand-name token introduced.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All ten receivers carry an `iconRes`; both menu presentations (bottom sheet via its existing `resolveIcon → iconRes → ic_share` chain, overflow submenu via Step 02.2) now show a recognizable icon. Phase 03 regenerates the catalog and records the dev log; device-test verification of submenu rendering (§6.1) happens at `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit(s) - no data migration or persisted state changed; reverting restores the prior generic-glyph behaviour.
