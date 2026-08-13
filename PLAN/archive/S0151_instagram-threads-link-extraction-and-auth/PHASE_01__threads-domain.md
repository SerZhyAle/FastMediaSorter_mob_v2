# Phase 01 — threads-domain

**Strategic spec:** [`../S0151_instagram-threads-link-extraction-and-auth.md`](../S0151_instagram-threads-link-extraction-and-auth.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** —
**Completed:** —

---

## Objective

Add `threads.com` as a recognized Threads domain in `KnownAuthResources` so proactive auth offers, manual auth picker, and WebView login entry points all fire for `threads.com` links the same way they do for `threads.net`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(none — foundation phase)*
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt` | Modified | ≤ 60 |

---

## Steps

### Step 01.1 — Add `threads.com` entry to `KnownAuthResources.all`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `KnownAuthResources.all`, insert a new `KnownAuthResource` entry for `threads.com` immediately after the existing `threads.net` entry. Use `displayName = "Threads"`, `host = "threads.com"`, `loginUrl = "https://www.threads.com/login"`. The existing `threads.net` entry and all other entries remain unchanged. `matchHost()` already handles both entries correctly via equality check — no logic changes required.

**Verification:**

- `Grep` — `KnownAuthResource("Threads", "threads.com"` matches in `KnownAuthResources.kt`.
- `Grep` — `"threads.net"` entry still present in same file.
- `Grep` — `Log\.d\(` returns zero hits in `KnownAuthResources.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt" "S0151 phase-01" "Add threads.com to KnownAuthResources"`.

---

## Handoff Notes to Next Phase

- `KnownAuthResources.all` now contains two Threads entries: `threads.net` and `threads.com`. `matchHost()` resolves both.
- Phase 02 builds on this by adding `isVideoFirstHost()` to the same file and wiring it into extraction strategies.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
