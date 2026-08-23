# Phase 02 - Prove the gate goes red, measure it, and settle the manifest question

**Strategic spec:** [`../S1915_blockneedusertest-without-a-packaging-build.md`](../S1915_blockneedusertest-without-a-packaging-build.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

Three numbers and one verdict on the record: the gate fails on a broken layout, it costs a measured number of seconds on a warm daemon, and strategic §6 item 5 is answered by an observed exit code rather than by the name of a target.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `JAVA_HOME` resolves to a JDK that exists - otherwise every measurement in this phase reads as a red gate (S1928).
- [ ] Gradle daemon warm - a cold daemon measures the daemon, not the gate.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/BUILD_TEST_FAST_PATH.md` | Modified | ≤ 20 added |
| `scripts/post-change.ps1` | Modified | ≤ 10 added |
| `PLAN/S1915_blockneedusertest-without-a-packaging-build/PHASE_02__measure-and-manifest-coverage.md` | Modified | evidence recorded inline |

> Evidence stays in this phase file, not under `temp/` - a closed spec must not cite disposable paths (`scripts/spec_catalog/check-evidence-durable.ps1`).

---

## Steps

### Step 02.1 - Prove the gate fails on a broken layout

**Files:** a scratch layout under `app_v2/src/main/res/layout/`
**Depends on:** - start of phase

**Prompt for developer:**

> Introduce a deliberately unlinkable layout - reference an attribute or drawable that does not exist - close it through the facade with `-ChangeType Xml`, and record the gate's exit code and the failing aapt line verbatim in this file. Remove the broken layout afterwards and re-run the same closure to confirm it returns to green.

**Why:**

Strategic §11 criterion 1 requires the gate to fail on a resource that does not link, and a gate that has only ever been observed passing is indistinguishable from a gate that cannot fail - which is the exact failure S1899 and S1881 are about.

**Verification:**

- Recorded in this file: the broken-layout run shows `[resource-link-gate]` FAIL with a non-zero exit and an aapt message naming the missing reference.
- Recorded in this file: the clean re-run shows `[resource-link-gate]` PASS, and `post-change: PASS`.
- `Glob` - no scratch layout remains under `app_v2/src/main/res/layout/`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 02.1

---

### Step 02.2 - Answer strategic §6 item 5 by measurement

**Files:** `app_v2/src/main/AndroidManifest.xml` (temporarily), `scripts/post-change.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Break the manifest deliberately - an unresolvable activity name or a malformed attribute - run the gate, and read the exit code. Restore the manifest immediately afterwards. If the resource-processing task fails, record that and change nothing. If it passes, add the manifest-merging task to the gate's invocation so a broken manifest cannot pass, and record which task was missing.

**Why:**

Strategic §6 item 5 is the one research item left Open, and its two branches lead to different code: the project already calls this target a "resources/manifest" check, but §6 records that as a description rather than as evidence, and shipping on a description is how `fk` came to be quoted as proof for the watch module.

**Verification:**

- Recorded in this file: the observed exit code from the broken-manifest run, and which of the two branches it selected.
- `Grep` - `app_v2/src/main/AndroidManifest.xml` contains no scratch edit; the file matches its pre-step content.
- Strategic §6 item 5 `Статус:` reads `Resolved` with the observed answer.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 02.2

---

### Step 02.3 - Measure the cost and record the number

**Files:** `docs/BUILD_TEST_FAST_PATH.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Time the gate on a warm daemon for a single-flavor set and for a two-flavor set. Add both numbers to the "Foreground or background" material in `docs/BUILD_TEST_FAST_PATH.md` beside the existing fast-target measurements, stating the module and the flavor count each number covers. If either number exceeds the 120 s foreground threshold, say so explicitly and raise it in the final report rather than silently accepting it.

**Why:**

Strategic §3.1 asks for the cost as a number in the document that already holds the other fast-target measurements, because §7 names "the gate makes closures expensive and people route around it" as the highest-probability risk and that risk can only be judged against a measurement.

**Verification:**

- `Grep` - `docs/BUILD_TEST_FAST_PATH.md` contains the gate's name and a measured duration.
- Recorded in this file: both timings with the flavor count each covers.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 02.3

---

## Evidence (2026-08-21, warm daemon, `app_v2`)

**Step 02.1 - the gate goes red on a broken layout.** A layout referencing an absent drawable was added, closed through the facade with `-ChangeType Xml`, and removed in the same call. The facade printed:

```
  resource-link: module app_v2, flavor(s) Standard
     com.sza.fastmediasorter.app_v2-mergeStandardDebugResources-117:/layout/s1915_link_probe.xml:5: error:
     resource drawable/s1915_definitely_absent (aka com.sza.fastmediasorter.debug:drawable/s1915_definitely_absent) not found.
     error: failed linking file resources.
  [resource-link-gate] FAIL (15903 ms) - child exit code 1
post-change: FAIL (1 gate(s), Xml)
  failed: resource-link-gate (exit 1)
```

Facade exit 1, and the run wrote no changelog row - the fatal-findings barrier stops before the mutating steps. The clean re-run after removing the probe returned `BUILD SUCCESSFUL`, exit 0. Before this gate the same closure passed green, which is the whole defect.

**Step 02.2 - the resource-processing target does cover the manifest.** `android:icon` on the `<application>` element was pointed at an absent mipmap, the target was run directly, and the manifest was restored from a backup and verified byte-identical with `diff -q`:

```
ERROR: app_v2\src\main\AndroidManifest.xml:217:5-945:19: AAPT: error:
resource mipmap/s1915_absent_icon (aka com.sza.fastmediasorter.debug:mipmap/s1915_absent_icon) not found.
BUILD FAILED in 9s
```

Exit 1. The merged manifest is an input to this target, so the branch that would have added a separate manifest-merging task is not taken and the gate ships unchanged. Strategic §6 item 5 is Resolved on this observation.

**Step 02.3 - cost.** 1.9 s with nothing to relink; 10.6 s for a flavor whose configuration cache was cold; 15.9 s on the red path; 41.8 s for a full relink after a real resource change. Every figure is inside the 120 s foreground threshold, and a two-flavor set doubles the worst case to roughly 84 s, still inside it. Recorded in `docs/BUILD_TEST_FAST_PATH.md`.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - proven by Step 02.1's clean re-run, which links the whole resource set.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for the phase via `scripts/post-change.ps1`.
- [ ] Working tree carries no scratch layout and no scratch manifest edit.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every strategic §11 criterion except 3 is now evidenced. Phase 03 carries only documentation and the ticket's own closure.

---

## Rollback Plan

Revert the `docs/BUILD_TEST_FAST_PATH.md` paragraph and any manifest-task addition from Step 02.2 - the measurement steps mutate nothing that survives the phase.
