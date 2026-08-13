# Phase 01 - Contracts

**Strategic spec:** [`../S0249_vr-test-immersive-image-stage1a.md`](../S0249_vr-test-immersive-image-stage1a.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Extend the S0245 XR entry contract with a diagnostic bundled-image launch operation and structured result semantics.

---

## Prerequisites

- [ ] INDEX Pre-Implementation Blockers are closed.
- [ ] Working tree is clean or current branch ownership is confirmed.
- [ ] Existing KDoc in every touched XR contract is read before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryGateway.kt` | Modified | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryResult.kt` | New | <= 120 |
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrEntryGateway.kt` | Modified | <= 80 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt` | Modified | <= 160 |

---

## Steps

### Step 01.1 - Add entry result model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryResult.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add a sealed or enum result model for diagnostic image entry attempts. Include at least `Started`, `UnavailableNoRuntime`, `UnavailableDisabledByUser`, and `InitializationFailed`; keep technical error detail out of user-facing strings.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryResult.kt` exists.
- `Grep` - `Started` appears in `XrEntryResult.kt`.
- `Grep` - `UnavailableNoRuntime` appears in `XrEntryResult.kt`.
- `Grep` - `InitializationFailed` appears in `XrEntryResult.kt`.

**Status:** `[x]` done (2026-05-19)

---

### Step 01.2 - Extend XrEntryGateway

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryGateway.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `suspend fun enterDiagnosticImage(): XrEntryResult` to `XrEntryGateway`. Keep the existing `tryEnter()` method if current callers still compile against it; mark the old method as compatibility-only if needed.

**Verification:**

- `Grep` - `suspend fun enterDiagnosticImage(): XrEntryResult` appears in `XrEntryGateway.kt`.
- `Grep` - `suspend fun tryEnter(): Boolean` still appears if any current source caller remains.
- `Grep` - `BuildConfig.SUPPORT_` returns zero hits in `XrEntryGateway.kt`.

**Status:** `[x]` done (2026-05-19)

---

### Step 01.3 - Update no-op gateway

**Files:** `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrEntryGateway.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Implement `enterDiagnosticImage()` in the phone-only no-op gateway and return `XrEntryResult.UnavailableNoRuntime`. Do not add logging here unless existing no-op XR classes already log equivalent calls.

**Verification:**

- `Grep` - `enterDiagnosticImage` appears in `NoOpXrEntryGateway.kt`.
- `Grep` - `UnavailableNoRuntime` appears in `NoOpXrEntryGateway.kt`.
- `Grep` - `Log.d(` returns zero hits in `NoOpXrEntryGateway.kt`.

**Status:** `[x]` done (2026-05-19)

---

### Step 01.4 - Prepare real gateway seam

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/XrEntryGatewayImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Implement `enterDiagnosticImage()` in the real VR gateway using injected collaborators that will be added in later phases. Until Phase 02 wires runtime launch, return a structured initialization failure instead of a boolean stub.

**Verification:**

- `Grep` - `enterDiagnosticImage` appears in `XrEntryGatewayImpl.kt`.
- `Grep` - `XrEntryResult` appears in `XrEntryGatewayImpl.kt`.
- `Grep` - `Stage 0 stub` returns zero hits in `XrEntryGatewayImpl.kt`.
- `Grep` - `Log.d(` returns zero hits in `XrEntryGatewayImpl.kt`.

**Status:** `[x]` done (2026-05-19)

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x]` done.
- [x] Project compiles - `assembleStandardDebug` PASS (2.60.5190.059), `compileNoLegalDebugKotlin` PASS. NoLegal packaging `!zip.isFile()` AGP-quirk is environmental, not code-level; pre-existing tech debt.
- [x] Dev log entries added for `XrEntryGateway.kt`, `XrEntryResult.kt`, `NoOpXrEntryGateway.kt`, `XrEntryGatewayImpl.kt`.
- [x] `dev/CATALOG/scripts/scan.ps1 -Module app_v2` and `render.ps1 -Module app_v2` ran: 1370 records.

---

## Handoff Notes to Next Phase

The gateway exposes diagnostic image launch without leaking VR implementation types into phone flavors.

---

## Rollback Plan

Revert Phase 01 commit(s); no data migration or persisted state is introduced.
