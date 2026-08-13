# Phase 01 - Typed outcome and user messages

**Strategic spec:** [`../S1055_sftp-reconnect-failure-classification.md`](../S1055_sftp-reconnect-failure-classification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-07-15

---

## Objective

Introduce the host-key-change typed outcome and wire both new outcomes (host-key-change, companion auth-reject) to user-facing messages, keeping the exhaustive `when` in the message mapper satisfied so the module compiles.

---

## Prerequisites

- [ ] Strategic §6 research items Resolved (both are).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapper.kt` | Modified | ≤ 150 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

---

## Steps

### Step 01.1 - Add the host-key-change sealed subtype

**Files:** `data/network/exceptions/NetworkExceptions.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new `class NetworkHostKeyChangedException(message: String = "Server host key changed", cause: Throwable? = null) : NetworkException(message, cause)` to the sealed hierarchy. It is a direct `NetworkException` subtype (NOT a `NetworkConnectionLostException` subclass) so it is non-transient by construction. Add a short KDoc: security-critical - the pinned server key no longer matches, a possible impersonation; never auto-retried, never auto-accepted. Do not touch `isTransient` (it returns true only for Timeout / ConnectionLost / RateLimit / ServerError, so the new type is excluded automatically).

**Verification:**

- `Grep` - `class NetworkHostKeyChangedException` matches exactly once in `NetworkExceptions.kt`.
- `Grep` - `: NetworkException(` on the same declaration (not `NetworkConnectionLostException`).

**Status:** `[x]` done

---

### Step 01.2 - Add trilingual strings for the two outcomes

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two string keys in lockstep across EN/RU/UK via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` (parity-enforced; invoke from a pwsh process to avoid the Bash->pwsh Cyrillic boundary). Copy must pass `docs/COMMUNICATION_POLICY.md` §2 (error formula) and §6 (tone checklist): plain language, no jargon, tell the user what happened and what to do.
> - `error_network_host_key_changed` (security warning). EN suggestion: "This shared folder's server identity changed since you paired. Nothing was loaded, to keep you safe. Re-pair only if you trust this network."
> - `error_companion_repair_needed` (re-pair prompt). EN suggestion: "This shared folder needs pairing again. Open the companion on your PC and scan its QR code."
> Provide COMMUNICATION_POLICY-compliant RU and UK translations (`..` not `...`, hyphen not em-dash, ё where correct).

**Verification:**

- `Grep` - `name="error_network_host_key_changed"` present in all three `strings.xml` files.
- `Grep` - `name="error_companion_repair_needed"` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "error_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.3 - Map both outcomes in the message mapper

**Files:** `data/network/exceptions/NetworkErrorMessageMapper.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> In `toMessageRes(exception: NetworkException)` add the exhaustive branch `is NetworkHostKeyChangedException -> R.string.error_network_host_key_changed`. In `toContextAwareMessage`, before the existing `isConnectivityError` early-return, add two guards: (a) `if (exception is NetworkHostKeyChangedException) return context.getString(R.string.error_network_host_key_changed)` - shown for any resource type; (b) for companion resources (`resourceType == ResourceType.SFTP || resourceType == ResourceType.FTP`) `if (exception is NetworkAccessDeniedException) return context.getString(R.string.error_companion_repair_needed)`. On the resource-open/navigation surface an `AccessDenied` means a credential failure (bad/changed password), not a file-permission result - see research/01 and research/02, so re-pair guidance is correct there. Keep the existing connectivity branch untouched.

**Verification:**

- `Grep` - `is NetworkHostKeyChangedException ->` matches once in `NetworkErrorMessageMapper.kt`.
- `Grep` - `error_companion_repair_needed` referenced once in `NetworkErrorMessageMapper.kt`.
- `/build` standard debug compiles (exhaustive `when` satisfied).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` standard debug.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The `NetworkHostKeyChangedException` type and both user messages now exist and compile. Phase 02 makes the classifier actually produce these types at runtime.

---

## Rollback Plan

Revert the phase commit(s) - no data migration or user-facing screen changed, only a new exception subtype and two string keys.
