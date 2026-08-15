# Phase 06 — Cast Guard

**Strategic spec:** [`../S0035_android17-local-network-permission.md`](../S0035_android17-local-network-permission.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Skip Cast SDK discovery when local-network permission is missing and keep the player-side Cast action failure-free on Android 17.

---

## Prerequisites

- [ ] Phase 01, Phase 02, and Phase 03 are ✅ Done.
- [ ] Cast remains in scope for the active flavour (`BuildConfig.SUPPORT_CAST == true`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Audit only | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Audit only | n/a |

---

## Steps

### Step 06.1 — Guard Cast initialisation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `CastMediaManager.init()`, check local-network permission before `CastContext.getSharedInstance(...)` or any discovery-related side effect. Missing permission must leave `castContext == null`, skip proxy-server work, and log a clear non-error info message.

**Verification:**

- `Grep` — `hasLocalNetworkPermission` appears in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`.
- `Grep` — `CastContext.getSharedInstance` still appears in that file behind the new permission guard.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS (hasLocalNetworkPermission + CastContext.getSharedInstance in CastMediaManager.kt). Dev log recorded.

---

### Step 06.2 — Guard the chooser dialog and settings fallback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Update `showCastDialog(activity)` so a missing local-network permission surfaces the new rationale/settings path instead of a generic `cast_unavailable` toast. If permission becomes granted later in the same session, `showCastDialog` must be able to lazily recover by re-checking the permission before opening the chooser.

**Verification:**

- `Grep` — `showCastDialog` still appears once in `CastMediaManager.kt`.
- `Grep` — `routeToLocalNetworkSettings|requestLocalNetworkPermission|local_network_permission_` appears inside `CastMediaManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS (showCastDialog once, routeToLocalNetworkSettings/requestLocalNetworkPermission present). Dev log recorded.

---

### Step 06.3 — Audit the player-side call sites

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Audit `PlayerManagerInitializer` and `PlayerActivity` to keep the existing lifecycle and click wiring intact. Only touch these files if the `CastMediaManager` helper contract changed. Do not redesign Cast button placement in S0035.

**Verification:**

- `Grep` — `CastMediaManager(` still appears in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`.
- `Grep` — `showCastDialog(this)` still appears in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS (CastMediaManager( in PlayerManagerInitializer.kt, showCastDialog(this) in PlayerActivity.kt). No call-site changes needed.

---

### Step 06.4 — Run the compile gate for the player slice

**Files:** none modified — verification only
**Depends on:** Step 06.3

**Prompt for developer:**

> Run:
>
> ```powershell
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```
>
> The player slice must compile before the final docs / catalog phase starts.

**Verification:**

- `Command` — `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification PASS. BUILD SUCCESSFUL (33s).

---

## Phase Done Criteria

- [x] Every Step 06.* above is `[x] done`.
- [x] Cast init is skipped when local-network permission is missing.
- [x] Cast chooser entry no longer crashes or shows a misleading generic unavailable message for missing permission.
- [x] PlayerActivity / initializer call sites remain minimal.

---

## Handoff Notes to Next Phase

Final phase only. The docs / catalog pass must mention Cast in user-facing copy if the implementation changed observable behaviour.

---

## Rollback Plan

Revert `CastMediaManager.kt` and any call-site adjustments together. Do not keep a half-guarded Cast init path.