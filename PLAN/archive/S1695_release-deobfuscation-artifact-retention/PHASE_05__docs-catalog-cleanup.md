# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1695_release-deobfuscation-artifact-retention.md`](../S1695_release-deobfuscation-artifact-retention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Make the retention scheme discoverable from the documents that already claim it is required, and close the ticket mechanically.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/RELEASE_READINESS_STANDARD.md` | Modified | ≤ 25 |
| `docs/DEV_OPS.md` | Modified | ≤ 60 |
| `scripts/release/README.md` | Modified | ≤ 40 |

---

## Steps

### Step 05.1 - Point the readiness standard at its implementation

**Files:** `docs/RELEASE_READINESS_STANDARD.md`
**Depends on:** - start of phase

**Prompt for developer:**

> The retention requirement in the "Diagnostics and deobfuscation retention" section and the matching line in the operator evidence pack both declare retention REQUIRED without naming anything that performs it. Name the three scripts now: retention happens in the release build, retrieval is `fetch-deobfuscation.ps1`, and the requirement is enforced by `assert-deobfuscation-retained.ps1` at pre-release step 0.6.
>
> State the archive location and the `versionCode` key. Leave the requirement text itself unchanged - only its implementation reference is new.

**Why:**

Strategic §1 records that this document declared retention mandatory while nothing in the repository implemented it, which is the condition that let the gap survive until a crash investigation exposed it; a requirement that names its enforcement cannot drift back into that state unnoticed. Strategic §11 criterion 3 makes automatic detection part of the definition of done, so the document must name the gate that performs it.

**Verification:**

- `Grep` - `retain-deobfuscation` present in `docs/RELEASE_READINESS_STANDARD.md`.
- `Grep` - `assert-deobfuscation-retained` present in the same file.
- `Grep` - the original REQUIRED sentence is still present verbatim.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - RELEASE_READINESS_STANDARD.md now names its own enforcement: the retaining script, the archive path and key, the one-command recovery, the gating prerelease step 0.6, the measured cost, and the pre-baseline fallback. The original REQUIRED sentence is untouched; the evidence-pack line now says the proof is the gate verdict rather than a manual copy.

---

### Step 05.2 - Write the operator loop into DEV_OPS

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a "Deobfuscation retention" section to `docs/DEV_OPS.md` covering: what is retained and what is not, the archive layout `<root>\<versionCode>\<variant>-deobfuscation.zip` plus `manifest.json`, the one-command retrieval, and the decoding loop from a Play Console crash report to a decoded stack trace.
>
> Record two facts a reader cannot derive: releases published before the baseline are recoverable only from the store, which returns the mapping solely inside the full bundle; and the gate is deliberately not part of `assert-fast-gates.ps1` because it depends on a cloud folder that is not present on every machine.
>
> State the measured payload size and retention duration from the Phase 01 handoff notes.

**Why:**

Strategic ADR-1 keeps the store as the fallback for pre-baseline releases and records that the console offers no direct mapping download, so an operator hitting an old crash needs the fallback path written down rather than rediscovered. Strategic §3.2 names disk space and release build time as this ticket's performance dimensions, which makes the measured numbers part of what the documentation owes the reader.

**Verification:**

- `Grep` - `Deobfuscation retention` heading present in `docs/DEV_OPS.md`.
- `Grep` - `manifest.json` and `fetch-deobfuscation` both present.
- `Grep` - the section states why the gate is absent from the fast-gate batch.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - DEV_OPS gains a DEOBFUSCATION RETENTION section: what is and is not retained with the measured 21.02 MB / 1.7 s, the archive layout, the unattended wiring, the crash-decoding loop, the gating step 0.6, the stated reason the gate is kept out of the fast-gate batch (cloud folder absent on some machines trains people to ignore environmental failures), and the pre-baseline fallback including that Play Console offers deletion rather than download for the mapping row.

---

### Step 05.3 - Register the scripts in the release handbook

**Files:** `scripts/release/README.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add `retain-deobfuscation.ps1` and `fetch-deobfuscation.ps1` to the script table, the flag table and the order-of-operations section of `scripts/release/README.md`, matching how the existing publishers are documented there.
>
> Mark retention as invoked automatically by the build scripts, so nobody adds a manual call and produces a second, weaker-provenance record of the same release.

**Why:**

Strategic §3.1 rules out any per-release manual step, and a handbook entry that reads like an instruction to run something by hand would reintroduce exactly that; the neighbouring entries are the pattern that keeps the release scripts operable by someone who did not write them.

**Verification:**

- `Grep` - both script names present in `scripts/release/README.md`.
- `Grep` - the retention entry states it is invoked automatically.
- `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet.ps1` exits 0, or the cheatsheet is regenerated by its own generator until it does.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Both scripts registered in scripts/release/README.md: script table, two new flag tables, and the invocation order which deliberately carries a confirm step rather than a retention step. Retention entry states it is invoked automatically and warns that a hand call with -Mapping would downgrade bundle-grade evidence. Cheatsheet regenerated, assert-script-cheatsheet-sync OK.

---

### Step 05.4 - Close the ticket mechanically

**Files:** none - closure tooling only
**Depends on:** Step 05.3

**Prompt for developer:**

> Run the closure facade over the whole changed set, naming every file the five phases touched:
>
> `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<all changed files>" -ScopeToFile -Target "release-deobfuscation-retention" -Description "S1695: retain R8 mapping and native symbols per versionCode" -ChangeType Tooling`
>
> Then run the document-registry loop's closing commands: `validate.ps1`, `generate.ps1`, `generate.ps1 -Check`.
>
> Do not add a `docs/ALL_FEATURES.jsonl` record: this ticket ships no user-facing capability, only release tooling. Do not touch `docs/FEATURES*.md`.
>
> Set the status with `update.ps1 -Id S1695 -Status Implemented`. This ticket needs no `BlockNeedUserTest` pass because nothing here runs on a device and no Kotlin changed, so no `Timber.d("S1695: ..")` probe is inserted at any point.

**Why:**

Strategic §8 states there is no change to `docs/FEATURES`, and the capability inventory records shipped user capabilities rather than release tooling, so adding a record there would misfile this work. The absence of any device surface is what makes the debug-probe invariant vacuous here rather than skipped - the tag rule binds to `BlockNeedUserTest`, which this ticket never enters.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` (or `PASS WITH ADVISORIES` with each advisory read and addressed) and exits 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- `Grep` - `S1695:` returns zero hits across `**/*.kt`.
- `select.ps1 -Id S1695 -Format json` reports `Implemented`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Closure facade PASS over the 12-file set with registry ack for repository-rules and developer-operations. Registry loop: validate PASS (29 records), generate + generate -Check current. Zero S1695 probe tags in Kotlin, as expected for a ticket with no device surface. No ALL_FEATURES record and no FEATURES edit: release tooling, not a user capability. The durable-evidence gate blocked the first close because phase 01 cited measurements under temp/; rewritten as a reproducing command plus expected results, gate now PASS. Status Implemented.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1` - performed by the closure facade in step 05.4.
- [x] If public API changed: not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate.

---

## Rollback Plan

Revert the three documentation edits. Revert phase commits in reverse order - no data migration and no user-facing surface changed.
