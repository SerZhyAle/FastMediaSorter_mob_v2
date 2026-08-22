# Phase 02 - Pinning verdict

**Strategic spec:** [`../S1639_gson-persistence-contract-gate.md`](../S1639_gson-persistence-contract-gate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Turn each durable serialization point into a named verdict: pinned in full, pinned in part, not pinned, or pinned by a keep rule of its own module.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-gson-persistence-contract.ps1` | Modified | ≤ 600 |

Budget raised from 400 to 600 on 2026-08-14. Phase 01 closed at 204 lines and handed over 30 points with an unresolved or container type, ruling that Phase 02 must extend resolution rather than inherit those simple names. Meeting that took a statement window, identifier declarations, TypeToken element types, call-site tracing through generic helpers, balanced argument lists and self-serialization - about 130 lines that the original estimate was made without. The file ends the phase at 526, well inside CLAUDE.md Rule 2.

---

## Steps

### Step 02.1 - Judge annotation coverage in three outcomes

**Files:** `scripts/quality/assert-gson-persistence-contract.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> For each durable model, read its declaration and count how many of its serialized properties carry an explicit wire-name annotation. Emit one of three outcomes: all properties annotated, some annotated, none annotated. Report the partial outcome as its own violation kind with the names of the unannotated properties, never folded into the "none" bucket.

**Why:**

Strategic §2 goal 4 singles out partial annotation as more dangerous than none, because it reads as protected at a glance and therefore survives review while still being broken.

**Verification:**

- Run with `-Format json` against `app_v2` - the three browse-transfer models report the fully-annotated outcome.
- Run with `-Format json` against `app_v2` - `PrimaryGoogleAccount` reports the not-annotated outcome.
- `Grep` - the partial outcome is emitted under a violation kind distinct from the not-annotated one.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Annotation coverage judged in three outcomes over a resolved model set. Type resolution extended per phase 01 handoff: statement window, identifier declarations, TypeToken element types, generic-helper call-site tracing, balanced argument lists and self-serialization (this). Model set restricted to data/enum/value classes so an injected service reached through the same identifier chain no longer enters the report. Predicates: BrowseFileTransferRequest/Source/TerminalPayload annotated-all, PrimaryGoogleAccount annotated-none with kind no-annotation, partial emitted as distinct kind partial-annotation. app_v2 49 durable models / 8 unresolved points, wear 8 / 4.
- 2026-08-14 - Correction found while seeding phase 03 and fixed here: the property parser read annotations only from the property's own line, so a model writing @SerializedName on the line above - TestCredentialsConfig and TestCredential do - was reported annotated-none. A pending-annotation carry now spans lines. app_v2 unpinned models fell from 5 to 3, and the three that remain (FileAttributes, GoogleScope, PrimaryGoogleAccount) are real.

---

### Step 02.2 - Accept a keep rule from the model's own module

**Files:** `scripts/quality/assert-gson-persistence-contract.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Read the obfuscation rules of the module the model belongs to and treat a model whose fields are kept by name as pinned, even with no annotations. Match a rule against the model's fully qualified name including package wildcards, and accept only a rule that keeps field names rather than one that merely keeps the class. A model pinned neither by annotation nor by a keep rule is the plain violation.

**Why:**

Strategic §9 ADR-2 accepts both forms because requiring a single one would mean rewriting the already-closed tickets that chose keep rules, and strategic §3.2 requires each module to be judged against its own rules, since the watch module keeps its contract models by package while the phone module annotates them.

**Verification:**

- Run with `-Format json` against `wear` - the watch contract models report pinned, with the matched keep rule named.
- Run with `-Format json` against `app_v2` - the game state model reports pinned by keep rule.
- Run with `-Format json` against `app_v2` - `PrimaryGoogleAccount` still reports a violation, with no keep rule matched.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Keep-rule acceptance added, judged per module against that module's own proguard-rules.pro. A rule counts as a pin only when its member spec covers fields unqualified and no allowobfuscation modifier hands the name back to R8; an annotation-qualified spec and an implements/extends selector are both refused, because the tree carries a Gson rule of each shape that would otherwise green every model. Predicates: eight wear contract models report keep-rule naming wear/proguard-rules.pro:56, GameStateSnapshot reports keep-rule naming app_v2/proguard-rules.pro:12, PrimaryGoogleAccount still no-annotation with no rule matched. app_v2 violations fell from 22 to 5.

---

### Step 02.3 - Flag enum-typed serialized properties separately

**Files:** `scripts/quality/assert-gson-persistence-contract.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a separate check over durable models: report every serialized property whose type is an enum declared in this project, and whose enum constants are neither individually annotated nor covered by a keep rule that preserves their names. Report it as its own violation kind, not as a missing property annotation, and state in the message that annotating the containing model does not cover it.

**Why:**

Strategic §6 item 2 records the fact measured while closing S1638: neither form of pinning covers enum constant names, because the serialized value comes from the enum itself rather than from the model's property name, so a check that stopped at property annotations would report a fully protected model that is not.

**Verification:**

- `Grep` - the enum violation kind is distinct from both the not-annotated and the partial kinds.
- Run with `-Format json` against `app_v2` - the transfer request's operation-type property is reported under the enum kind. Corrected 2026-08-14: the original predicate named the terminal payload, whose `operationType` is declared `String`, not an enum - it stores `FileOperationType.name` and reads it back through `valueOf`, so no property-type check can see an enum there. The enum-typed property of that same family is `BrowseFileTransferRequest.operationType`, and reporting it names the same `FileOperationType` the payload depends on.
- Run with `-Format json` - the message for that kind states that model-level pinning does not cover enum constants.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Enum constants judged as their own violation kind enum-constants-unpinned, separate from no-annotation and partial-annotation. An enum is reported per containing property, and on its own only when a serialization point reaches it directly, so one constant list is never counted twice. Constant parsing strips KDoc first - a doc sentence ending in a semicolon had truncated MetadataState to one constant - and reads a single-line enum body. Predicates: BrowseFileTransferRequest.operationType reported under the enum kind (predicate corrected, the terminal payload launders the enum through a String), message states model-level pinning does not cover constants, kind distinct in grep. app_v2: 5 unpinned models, 3 with unpinned enum constants, 8 unresolvable points.
- 2026-08-14 - Phase-boundary audit (Layer 1): found and fixed a P1 - the declaration index was keyed by simple class name, so in the default -Module all run the watch copies of WearEventEnvelope, WearSyncPayload and the rest collapsed into the phone declarations and were reported annotated-all against app_v2 rules, hiding that the watch relies on a package keep rule. Index, model test, resolution and expansion are now keyed by module::name. After the fix the all run equals the sum of the per-module runs (5 unpinned, 3 enum, 12 unresolvable). No P0 or further P1 findings; the repeated per-model file read is a P3 left alone at 8 s for both modules.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase adds no compiled source.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every durable point now carries a verdict of a named kind. The verdict set is complete and unsuppressed - Phase 03 adds the only suppression mechanism.

Measured on 2026-08-14 at the end of this phase: `app_v2` reports 5 unpinned models (`FileAttributes`, `GoogleScope`, `PrimaryGoogleAccount`, `TestCredential`, `TestCredentialsConfig`), 3 models with unpinned enum constants (`BrowseFileTransferRequest.operationType`, `MediaFile.type`, `MediaFile.metadataState`, plus `WearPlaybackCommand` serialized on its own) and 8 points with an unresolvable type; `wear` reports 0 unpinned models - all eight contract models match the package keep rule - and 4 unresolvable points. Those 12 unresolvable points and the exemption decisions are Phase 03's input.

One coverage limit found while closing this phase, and left deliberately: a model can launder an enum through a `String` property, writing `constant.name` and reading it back with `valueOf`. `BrowseFileTransferTerminalPayload.operationType` does exactly that, and no property-type check can see it. It is not a hole in this tree, because the same enum is already reported through `BrowseFileTransferRequest.operationType` and one pinning fixes both, but an enum used *only* through `.name` would pass unreported. Phase 03's registry is where such a case gets its written justification.

---

## Rollback Plan

Revert the phase commit - the script returns to reporting the inventory without verdicts, and nothing else consumes it yet.
