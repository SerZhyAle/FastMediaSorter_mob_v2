# Phase 02 - The readiness probe picks a free device

**Strategic spec:** [`../S1926_device-lease-for-parallel-sessions.md`](../S1926_device-lease-for-parallel-sessions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

`device-ready.ps1` gains an opt-in mode that resolves `multiple-devices` by claiming the first device carrying no live foreign lease, and reports a distinct state when every device is taken.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] At least two devices online, so the multiple-devices path is reachable at all.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/device-ready.ps1` | Modified | ≤ 60 added |

---

## Steps

### Step 02.1 - Add the opt-in selection mode

**Files:** `scripts/devtest/device-ready.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a switch that turns `multiple-devices` from a refusal into a selection: walk the online devices in a single ordering function, skip any carrying a live foreign lease, claim the first one left, and continue the probe's existing package and version checks against it. Without the switch the probe behaves exactly as it does today, `multiple-devices` included. Keep the ordering in one function so a future preference rule has one place to live.

**Why:**

Strategic ADR-2 requires the change to be opt-in because sibling sessions are running right now and silently changing a shared tool's behaviour mid-run is the same class of surprise this ticket removes; §5.3 requires the single ordering point so "prefer an emulator over the owner's phone" lands in one place later.

**Verification:**

- `Grep` - the new switch appears in the `param()` block and is documented in the header.
- Run: `pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Json` with several devices online - expected: unchanged `multiple-devices`, `ready: false`.
- Run: the same call with the switch - expected: `ready: true` and a named `selectedDevice`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1926 step 02.1

---

### Step 02.2 - Add the all-taken state

**Files:** `scripts/devtest/device-ready.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> When every online device carries a live foreign lease, report a state distinct from both `no-device` and `multiple-devices`, with a reason naming which session holds what. Document it in the header's state list next to the others.

**Why:**

Strategic §11.6 requires the three answers to be distinguishable, because "there is no device" and "every device is busy" call for different next moves - the first ends the device stage, the second means try again later.

**Verification:**

- Recorded in this file: the probe's output with every online device leased to a forged foreign session, showing the distinct state and the holder names.
- `Grep` - the new state appears in the header's documented state list.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1926 step 02.2

---

### Step 02.3 - Confirm two sessions cannot take the same device

**Files:** none - observation only
**Depends on:** Step 02.2

**Prompt for developer:**

> With at least two devices online, run the selection twice under two different session identities and confirm they select different devices. Record both selections. Release both leases afterwards and confirm the store is empty.

**Why:**

Strategic §2.1 is the whole point of the ticket, and it is the one claim that neither the lease's unit behaviour nor the probe's single-run output can demonstrate on its own - only two selections side by side show the arbitration working.

**Verification:**

- Recorded in this file: the two selected serials, which must differ.
- Run: `pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb List` after release - expected: empty.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1926 step 02.3

---

## Evidence (2026-08-21, three devices attached: `RFCR110NBQJ`, `emulator-5554`, `emulator-5556`)

**Step 02.1 - opt-in, and the default is untouched.**

| Call | state | ready | selected |
| --- | --- | --- | --- |
| `device-ready.ps1 -Json` | `multiple-devices` | False | - |
| `device-ready.ps1 -ClaimFree -Json` | `ready` | True | `RFCR110NBQJ` |

The first row is the behaviour every running sibling session still gets, which is what ADR-2 required.

**Step 02.3 - a device held by a sibling is skipped.** With a forged foreign lease on `RFCR110NBQJ`, the same `-ClaimFree` call returned `selected=emulator-5554`. That is the arbitration §2.1 asks for: two sessions selecting at the same time land on different devices. Note the mechanism - the probe does not list free devices and then take one, it simply attempts the claim on each candidate in turn and keeps the first that succeeds, so there is no gap between deciding and taking.

**Step 02.2 - "all busy" is not "no device".** With forged foreign leases on all three:

```
state=all-devices-leased ready=False statusCode=7
reason: every online device is leased by another session (RFCR110NBQJ, emulator-5554,
emulator-5556); this is not 'no device' - retry later or run device-lease.ps1 -Verb Status
```

Distinct from both `no-device` and `multiple-devices`, as §11.6 requires, and the reason names every holder.

**A risk row was wrong and has been corrected rather than mitigated.** The first `-ClaimFree` run selected the physical `RFCR110NBQJ`, which strategic §7 had rated a low-probability hazard described as "the owner's phone". It is neither: that serial is the *dedicated test device* carrying blanket authorization for installs, permissions and resets, and treating it as off-limits would repeat an over-application of the personal-handset rule that has already cost two corrections. §7 now records the accurate version - the preference, if one is ever wanted, is about cost rather than danger, and `Get-DevicePreferenceOrder` is where it would go.

No lease was left behind: `leases left = 0`. Nothing was installed, launched or changed on any device - this phase read state and wrote lease files only.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] No lease left behind in `temp/DEVICE.LEASES/`.
- [ ] Nothing was installed, launched or changed on any device - this phase reads state and writes lease files only.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Selection and arbitration are proven. Phase 03 carries documentation and closure only.

---

## Rollback Plan

Revert the probe's added switch - the lease script stays and simply goes unread, exactly the Phase 01 end state.
