# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1421_launcher-own-notification-area.md`](../S1421_launcher-own-notification-area.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Record the delivered capability, regenerate the class catalog, and prove on the built artifacts that the
four launcher-less flavors gained nothing and that no manifest gained a permission.

---

## Prerequisites

- [x] Phases 01-05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via `scripts/all_features/add.ps1`) | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Modified (via `scripts/post-change.ps1`) | - |

---

## Steps

### Step 06.1 - Prove no permission and no flavor leak

**Files:** none - verification step against built artifacts
**Depends on:** - start of phase

**Prompt for developer:**

> Build `lite`, `photos` and `legacy` debug and confirm none of them carries a signal-strip class or layout,
> then grep every flavor manifest for a permission added by this ticket. The whole feature lives in
> `src/launcherEnabled`, so a hit means a file landed in the wrong source set.

**Why:**

Strategic §7 lists both as acceptance criteria - nothing appears or breaks in `lite`, `photos`, `legacy`
and `vr`, and no flavor's manifest gains a permission - and §4.3 records the owner's decision that this
ticket asks for no permission at all.

**Verification:**

- `Grep -rn "LauncherSignal" app_v2/src/main app_v2/src/lite app_v2/src/photos app_v2/src/legacy app_v2/src/vr` returns zero hits.
- `Grep -rn "uses-permission" app_v2/src/*/AndroidManifest.xml` shows no line absent before this ticket.
- `.\a.ps1 fk` and `.\a.ps1 fkn` both exit 0.

**Status:** `[x]` done

---

### Step 06.2 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the strip in
> English, with `spec` set to `S1421` and the flavor list taken from the gate, not from memory - the feature
> ships wherever `src/launcherEnabled` is mounted, which `docs/FLAVOR_MATRIX.md` shows is `standard` and
> `noLegal`. Do not touch `docs/FEATURES*.md`; that file is `/skill-release`-owned.

**Why:**

CLAUDE.md §11 makes `docs/ALL_FEATURES.jsonl` the inventory every spec records its delivered capability in,
and strategic §7 delivers a user-visible capability.

**Verification:**

- `Grep -n "S1421"` in `docs/ALL_FEATURES.jsonl` returns exactly one record.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 06.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once, then set `role` and `status` on
> the new classes with `dev/CATALOG/scripts/set.ps1`. Add the flavor hint
> `-NoFlavors "lite,photos,legacy,vr"` on every class from this ticket, so the catalog states the isolation
> the source set enforces.

**Why:**

CLAUDE.md §12 requires the catalog to be regenerated when a ticket adds public classes, and this one adds
eight.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*LauncherSignal*"` lists the new classes.
- Each new class carries a non-empty `role`.

**Status:** `[x]` done

---

### Step 06.4 - Close the ticket mechanically

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Close through `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<every file this ticket touched>" -Target "S1421" -Description "<one line>" -ChangeType Mixed -ScopeToFile`
> and read its verdict - only the bare word `PASS` is a clean result. One dev-log entry for the ticket, not
> one per file.

**Why:**

CLAUDE.md §12 routes mechanical closure through the `post-change.ps1` facade, which is what runs the gates
before writing the changelog row.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS`.
- `dev/CHANGELOG.md` carries exactly one new entry for S1421.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 06.1 PASS. `LauncherSignal` matches 0 files under `src/main`, `src/lite`, `src/photos`, `src/legacy` and `src/vr` - the whole feature is inside `src/launcherEnabled`, which only `standard` and `noLegal` mount. No manifest was touched by this ticket at all, so no flavor gained a permission; `git diff --stat` on the flavor manifests shows only `lite` and `photos`, both from other in-flight tickets (S1442 / S1454), not this one. `.\a.ps1 fk` and `.\a.ps1 fkn` both exit 0.
- 2026-08-07 - Step 06.2 PASS. One record added via `all_features/add.ps1`: `launcher.status-strip-signals`, area `Launcher`, flavors `standard,noLegal` taken from the source-set gate rather than memory, `spec` = S1421. `validate.ps1` PASS, 667 records. The first attempt used a bare kebab id and was refused - ids are `<area>.<feature>`. `docs/FEATURES*.md` untouched.
- 2026-08-07 - Step 06.3 PASS. `catalog_sync.ps1 -Module app_v2` reported the index already current; `query.ps1 -ClassMatches "*LauncherSignal*"` lists all 10 new declarations. Each carries a `role`, `status new` and `-NoFlavors "lite,photos,legacy,vr"`, set through `set.ps1` and re-rendered (2574 records).
- 2026-08-07 - Step 06.4 PASS. `post-change.ps1` over the whole 24-file changed set: `post-change: PASS`. It took three runs to get there, and the two failures were both real:
  - `ticket-log-audit` FAIL (expected 0, actual 5). Correct refusal: the five `Timber.d("S1421:` probes were in the tree while the ticket was still `In Progress`, and that gate only tolerates them under `BlockNeedUserTest`. Fixed by flipping the status first, which is the documented order.
  - `listener-symmetry` FAIL. Also correct, and only visible at ticket scope: judged file by file every step passed, but over the union the manager registered a lifecycle observer and a window-insets listener that nothing ever removed. Fixed by tearing both down in `unbind()`, in the reverse order `bind()` registered them, rather than relying on the manager happening to die with its window. Re-checked: `new imbalance 0`, then rebuilt (`.\a.ps1 dq` exit 0, BUILD SUCCESSFUL in 58s) and re-closed.
  - One advisory remained, `document-registry` on `feature-inventory`. Read the record: its paths are `docs/ALL_FEATURES.jsonl` and `docs/ALL_FEATURES.schema.json`, and the new entry uses only existing fields, so the schema sibling needs no change. Acknowledged with `-RegistryAck "feature-inventory"`; that run printed `document-registry PASS`.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `/build` on `standard debug` passes - `.\a.ps1 dq` exit 0, BUILD SUCCESSFUL in 58s (the run that
      validated the implementation, the five debug probes and the listener-symmetry fix together).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13). See audit note below.

---

## Phase-boundary audit (2026-08-07)

`Files Touched` is documentation and generated indexes, which the protocol skips - but this phase's closure
run judged the *whole* ticket at once, and that is where its one finding came from.

- **P1 - FIXED in this phase.** `LauncherStatusStripManager` registered a `DefaultLifecycleObserver` and a
  window-insets listener in `bind()` and removed neither. Per-step closures never saw it, because each judged
  one file against its own baseline; the union did. Both are now undone in `unbind()`.
- P3 - the gate's arithmetic over a set is not the sum of its parts: the manager alone reported 0 new
  imbalance and `WorkManagerScheduler.kt` alone reported 0, yet the two together reported 1, reproducibly.
  The finding it surfaced was real, so this is a reporting question rather than a false positive - parked
  rather than chased here.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The strategic §7 criterion about the idle strip stays open until
the owner answers §5.2, so `/spec-check` closes this ticket `Partial` unless that answer landed first.

---

## Rollback Plan

Revert phase commit(s) - documentation and index regeneration only, no source or user-facing surface changed.
