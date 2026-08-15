# Phase 04 — recommendation

**Strategic spec:** [`../S0193_lazy-init-research.md`](../S0193_lazy-init-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03 (required); Phase 02 (supporting — skipped)
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Synthesize all research findings into a single, unambiguous architectural recommendation. If improvements are warranted, create the child spec tickets that will implement them. Update the strategic spec with the decision and child spec references.

---

## Step 04.1 — Recommendation decision ✅

### Selected: Option B + variant B' (split into two child specs)

The architectural principle — «не загружай неиспользуемое в момент когда оно не нужно» — is confirmed by Phase 01 as systematically violated by `Application.onCreate` in `standard`. Phase 03 establishes that the fix is mechanical for 13 of 17 fields and a small focused refactor for the remaining 4.

The recommendation is split into two child specs:

**B — `dagger.Lazy<T>` for 13 trivial Application-level fields.** Mechanical refactor of `FastMediaSorterApp` + `AppStartupInitializer` constructor signature. No architectural change, no observable behavior change, no risk of breakage. → spec **S0194**, priority 60, Tier 2.

**B' — trigger-on-first-use for 4 network lifecycle fields.** Small but careful refactor: move OS-callback and lifecycle-observer registration out of `Application.onCreate` into `ConnectionGateRegistry` first-use code path. Touches S0061 / S0067 semantics so needs verification. → spec **S0195**, priority 55, Tier 3.

### Expected outcomes

- After S0194: heap pressure reduction unmeasured but observably absent — 8 of 13 fields are mechanically optimized; 5 more move from "eager singleton holding init-state" to "lazy reference unwrapped inside coroutines".
- After S0195: zero network-stack objects in heap when user has not opened a network resource in this session. `SmbConnectionManager`, `NetworkStateMonitor`, `SmbBackgroundLifecycleManager`, `NetworkLifecycleObserver` plus the entire transitive cluster (~14 objects from Phase 01 Step 01.3 NETWORK_ONLY group) are absent.
- Combined: `standard`-flavor user opening the app to browse local photos has the same in-process footprint as a `lite`-flavor user. The flavor distinction becomes about *compiled-in code size*, not *runtime memory*.

### Rejected options

- **Option A (stay-as-is)** — rejected. Phase 01 produced concrete evidence of unconditional NETWORK_ONLY object creation. The architectural principle is sufficient justification on its own.
- **Option C (scope narrowing for player singletons)** — rejected per Phase 03 Step 03.2: already lazy in practice, no measurable benefit.
- **Option D (flavor-onboarding hint)** — rejected as redundant. After B + B', `standard` users get `lite`-equivalent startup behavior automatically.
- **Option E (DFM for VR)** — rejected per Phase 03 Step 03.3: VR already lazy at runtime; DFM only saves install size at significant refactoring cost.

---

## Step 04.2 — Child spec tickets created ✅

- **S0194** — `lazy-hilt-singletons` — Tier 2, Priority 60, Status: Draft. File: `PLAN/S0194_lazy-hilt-singletons.md`. Scope: 13 trivial `dagger.Lazy<T>` conversions.
- **S0195** — `network-first-use-trigger` — Tier 3, Priority 55, Status: Draft. File: `PLAN/S0195_network-first-use-trigger.md`. Scope: 4 lifecycle-observer registrations move to first network use.

Both specs are minimal Draft stubs — owner can refine via `/spec-update` before approving.

---

## Step 04.3 — Strategic spec updates ✅

- §10 ("Связи с другими спеками") updated: lists S0194 and S0195.
- §9: ADR-3 added documenting final architectural decision.
- §6 verification: 3 of 5 items Resolved (§6.2, §6.3, §6.5); 2 deferred as supporting evidence (§6.1, §6.4) — Phase 02 was skipped per owner preference.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Recommendation document is recorded in Step 04.1.
- [x] Child specs S0194 and S0195 are created with strategic stubs.
- [x] Strategic spec §9 has ADR-3 for final decision (added below).
- [ ] Dev log entries will be added on commit.

---

## Handoff Notes to Next Phase

Phase 04 is the research conclusion. S0194 and S0195 proceed independently via their own `/spec-tech` and `/spec-dev` cycles. S0193 itself moves to `Verified` via Phase 05 + `/spec-check`.

Recommended execution order: **S0194 first** (low risk, isolated rewrite), **S0195 second** (depends on S0194 not landing first — but conceptually independent). S0194 and S0195 may also land in parallel under separate DEBUG-v00N branches.

---

## Rollback Plan

Research phase — no production code changed. Child spec stubs can be archived via `archive.ps1` if owner decides to abandon the direction. Strategic spec update can be reverted via git.
