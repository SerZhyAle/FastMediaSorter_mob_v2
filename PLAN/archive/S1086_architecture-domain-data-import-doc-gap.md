# S1086 - ARCHITECTURE dependency rule vs practice: domain imports data directly

**Status:** Archived
**Priority:** 25
**Tier:** 5
**Date:** 2026-07-18
**Source:** parked from S0404 Phase 08 audit (2026-07-17)

## 0. Raw capture

Auditor finding (S0404 Phase 08, dimension: flavor/spec compliance). Project-wide doc-vs-reality gap, pre-dates S0404.

**Symptom:** `docs/ARCHITECTURE.md` states "Domain: UseCases. Repository interfaces only .. Dependency Rule: UI -> Domain -> Data", but long-standing practice has dozens of `domain/usecase/*.kt` and `domain/repository/*.kt` importing concrete `data.local` / `data.model` classes directly. This is consistent, deliberate convention in the codebase - not a new violation - but the documented rule and the code disagree.

**Evidence:**
- `domain/repository/DeviceProfileRepository.kt` returns `data.model.DeviceProfile`/`DeviceProfileType`; same `data.model` import in `ApplyProfilePresetUseCase`, `ProfileImpliesAllFilesUseCase`, `DeviceProfileDetector`, `RestoreDeletedUseCase`, `core/AppShortcutsManager`, `core/xr/VrProfileSettingsSync`.
- 15+ domain use cases import `data.local.LocalMediaScanner`/`.VIRTUAL_PATH_*` directly, incl. `domain/usecase/panel/SeedDefaultAppLaunchPanelUseCase.kt` (the precedent S0404's own seed use case followed).

**Why its own ticket:** resolving it is either a real cross-file refactor (introduce a domain-owned abstraction for the shared enums/constants) or a deliberate `ARCHITECTURE.md` rewrite that documents the actual, accepted boundary - each needs its own research + review. Not fixable inside a feature ticket.

## 1. Next step

`/spec S1086`. Decide: rewrite the doc to match reality, or define the abstraction and migrate. Likely doc-first.

**Decision (2026-07-18, DocVsCode):** doc-first. Rewrite `docs/ARCHITECTURE.md` to document the accepted domain -> `data.local`/`data.model` boundary. Do NOT refactor the 47 files that import those directly - the grep count exceeds the initial 15-20 estimate, and the convention is deliberate and consistent, so a cross-file refactor is disproportionate for a doc-gap.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0404 (parent audit, Phase 08 - source of this finding).
- **Decision (DocVsCode):** rewrite `docs/ARCHITECTURE.md` to document the accepted domain -> `data.local`/`data.model` boundary; do NOT refactor the 47 offending files. `docs/ARCHITECTURE.md` is a registered doc, so the rewrite lands at impl time (not in this spec), followed by document-registry regen and validate.

### Quiz decisions (2026-07-18)

- Rewrite the doc, or refactor 47 files to satisfy the documented rule? -> Rewrite `docs/ARCHITECTURE.md` to match the accepted boundary (the domain -> `data.local`/`data.model` convention is deliberate, consistent, and predates S0404; a 47-file refactor is disproportionate for a doc-gap).

## Implementation (2026-07-19)

Doc-only change, no code touched (per the DocVsCode decision).

- Rewrote the "Three-Layer Structure" + "Dependency Rule" section of `docs/ARCHITECTURE.md`. The old text asserted "Domain: UseCases. Repository interfaces only" and a strict "Dependency Rule: UI -> Domain -> Data" that implied domain has zero compile-time dependency on data. Replaced with an honest "accepted convention" subsection that separates the enforced **runtime call direction** from the deliberately non-strict **compile-time** dependencies.
- Documented the real boundary, verified live (not the spec's initial narrow framing):
  - 89 of 303 `domain/*.kt` files import a concrete `data.*` type (~29%, "roughly a third").
  - Breadth is a dozen-plus `data.*` subpackages, not just `data.local`/`data.model` (top: `data.local` 60, `data.transfer` 25, `data.repository` 21, `data.network` 21, `data.cloud` 14).
  - `domain/repository/` is genuinely interfaces-only (21 interfaces, 0 concrete classes), so that half of the old claim was correct and is preserved - but interfaces such as `DeviceProfileRepository` do expose `data.model` types in their signatures.
- Stated the rationale (shared value types defined once, not mirrored; a wrapper-everything refactor touches dozens of files for no behavioural gain) and the forward rule (importing a concrete `data.*` type from a use case is acceptable; add a domain abstraction only for a real DI/testing/flavor seam), so future audits do not re-flag this as a violation.
- `docs/ARCHITECTURE.md` is a registered doc (`architecture` record): registry `validate` PASS (23 records), `generate` + `generate -Check` current.
- Scope check: the false strict-rule claim lived only in `docs/ARCHITECTURE.md`. Sibling docs (`V2_Specification*.md`, `dev/TECH_REQUIREMENTS.md`) only name the layering topic / pattern label and stay accurate - left unchanged.
