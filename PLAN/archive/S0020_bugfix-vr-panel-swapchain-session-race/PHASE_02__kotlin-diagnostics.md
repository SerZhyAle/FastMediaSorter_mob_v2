# Phase 02 — Kotlin Diagnostics

**Strategic spec:** [`../S0020_bugfix-vr-panel-swapchain-session-race.md`](../S0020_bugfix-vr-panel-swapchain-session-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Add observable `panel ready` and `panel never came up` markers on the Kotlin side; align the Kotlin-side gate so it matches the native check (session handle valid, not "session running"); remove the redundant Kotlin pre-check that previously short-circuited a valid call.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Modified | ≤ 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelRenderer.kt` | Modified | ≤ 300 |

---

## Steps

### Step 02.1 — Promote success path log to `panel ready`

**Files:** `OpenXrSessionManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `createPanelSwapchain(width, height)`, after `OpenXrNative.nativeCreatePanelSwapchain(width, height)` returns:
>
> - On `true` → `Timber.i("OpenXrSessionManager: panel ready (size=%dx%d)", width, height)`.
> - On `false` → keep the existing warning AND add `Timber.w("OpenXrSessionManager: panel never came up (size=%dx%d)", width, height)`.
>
> These are the on-device-verifiable markers from strategic §11.1/§11.4. Use Timber, never `Log.d`.

**Verification:**

- `Grep` — `OpenXrSessionManager: panel ready` matches exactly once in `OpenXrSessionManager.kt`.
- `Grep` — `OpenXrSessionManager: panel never came up` matches exactly once in `OpenXrSessionManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `OpenXrSessionManager.kt` (Timber-only invariant).

**Status:** `[x]` done

---

### Step 02.2 — Single-retry on first cold-start failure

**Files:** `OpenXrSessionManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> If the very first `nativeCreatePanelSwapchain` returns `false` AND `running.get()` is `true` (i.e. the session is up from Kotlin's perspective but native rejected, which after Phase 01 should be impossible — but defensively), retry exactly once after a `Thread.sleep(50)` window. Log the retry as `Timber.i("OpenXrSessionManager: panel retry after initial false")`. If the retry also fails, fall through to the "panel never came up" warning. Do NOT loop more than once — strategic ADR-1 forbids polling.

**Verification:**

- `Grep` — `panel retry after initial false` matches exactly once in `OpenXrSessionManager.kt`.
- `Grep` — `Thread.sleep(50)` matches at most once near the retry block.

**Status:** `[x]` done

---

### Step 02.3 — Tighten renderer's fallback notice

**Files:** `VrInteractivePanelRenderer.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `VrInteractivePanelRenderer.initialize` (or whichever method calls `sessionManager.createPanelSwapchain`), when `swapchainReady = false`, additionally log `Timber.w("VrInteractivePanelRenderer: panel unavailable — falling back to 2D overlay")`. This is the user-side complement to the SessionManager's `panel never came up` marker. Keep the existing fallback engagement code unchanged.

**Verification:**

- `Grep` — `panel unavailable — falling back to 2D overlay` matches exactly once in `VrInteractivePanelRenderer.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `VrInteractivePanelRenderer.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build` for `vr debug`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries for `OpenXrSessionManager.kt` and `VrInteractivePanelRenderer.kt`.

---

## Handoff Notes to Next Phase

Phase 03 records the new diagnostic markers in CHANGELOG and refreshes catalog metadata. No FEATURES updates per strategic §8.

---

## Rollback Plan

Revert phase commit. Logging-only change; no behavioural impact beyond observability.
