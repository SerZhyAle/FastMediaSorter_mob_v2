# Resource Editing and Copying Improvement Specification

## 1. Purpose

Define a safe, consistent, and scalable architecture for resource **editing** and **copying** that reuses the unified form model from [RESOURCE_CREATION_IMPROVEMENT_SPEC.md](RESOURCE_CREATION_IMPROVEMENT_SPEC.md).

The target is to eliminate duplicated logic, reduce user friction for duplication workflows, and prevent invalid runtime states during edits.

## 2. Scope

### In Scope

1. Unified `EDIT` and `COPY` behavior via shared form architecture.
2. Dirty-state tracking and reset/revert behavior.
3. Context-aware warnings and guardrails for critical field changes.
4. Duplicate/Clone entry points from resource list and edit screen.
5. Credential handling policy for copied resources.
6. Conflict handling for resource name/path collisions.

### Out of Scope

1. New resource type onboarding (covered by creation spec strategy model).
2. Storage schema redesign unless required for credential safety.
3. Full UI redesign outside editing/copying flow.
4. Bulk import from external files.

## 3. Current Problems

1. Editing can mutate critical fields while resource usage context is not clearly validated.
2. Copying similar resources requires too many repeated steps.
3. Copy defaults are not always safe or collision-proof.
4. Edit/Create screens diverge, increasing cognitive load and maintenance cost.

## 4. Objectives

1. **Safety:** prevent invalid configurations and unsafe transitions.
2. **Consistency:** use one form engine for `CREATE`, `EDIT`, `COPY`.
3. **Efficiency:** reduce steps for duplicate/variation workflows.
4. **Integrity:** preserve credential and destination correctness.

## 5. UX Requirements

### 5.1 Edit Workflow

1. `Save` is disabled until:
   - form is valid;
   - at least one meaningful field changed.
2. Add `Reset Changes` action to restore original persisted values.
3. Show contextual warning dialogs:
   - when enabling `ReadOnly` for a resource currently used as destination;
   - when path/endpoint changes that require re-scan/re-verification.
4. Add `Save as Copy` action in edit screen.

### 5.2 Copy/Duplicate Workflow

1. Add `Duplicate` action to resource list context menu.
2. `Duplicate` opens unified editor in `COPY` mode.
3. COPY defaults:
   - `id = 0` (new entity)
   - `name = "<sourceName> (Copy)"` with collision-safe auto-suffix.
4. Credentials behavior must be explicit:
   - `Keep credentials`
   - `Use new credentials`

### 5.3 Batch Variations (Optional Feature-Flag)

1. Add `Create Variations` flow from selected source.
2. User provides suffix/subfolder list.
3. System generates multiple resources with shared base settings.
4. Feature must be behind a flag until stability criteria are met.

## 6. Domain and State Model

### 6.1 Modes

`CREATE | EDIT | COPY`

### 6.2 Edit/Copy State Rules

#### EDIT

1. Load `originalResource`.
2. Initialize `currentFormState = originalResource -> formModel`.
3. Derive `hasChanges = currentFormState != originalSnapshot`.

#### COPY

1. Load `sourceResource`.
2. Initialize `currentFormState = sourceResource -> formModel`.
3. Force copy adjustments:
   - new identity (`id = 0`),
   - generated unique default name,
   - mode marker = `COPY`.
4. `hasChanges = true` is acceptable by design in COPY mode.

## 7. Credential Safety Policy

1. Credential reuse is allowed only if lifecycle semantics are safe.
2. If credentials are effectively 1:1 bound to resource ownership, COPY must clone credential records.
3. If credentials are shared entities, implement explicit reference-safe behavior.
4. No plaintext secret exposure in UI logs/errors.

## 8. Validation and Conflict Handling

### 8.1 Name Collision

1. Real-time validation for duplicate names in scope.
2. Auto-suggest strategy: `Name`, `Name 1`, `Name 2`, ...

### 8.2 Path/Endpoint Collision

1. Allow duplicate path usage when valid (different filters/roles).
2. Show warning when same endpoint/path already exists and suggest edit-existing alternative.

### 8.3 Critical Field Change Rules

Before save, run semantic checks for:

1. destination role compatibility,
2. read-only constraints,
3. required re-verification markers,
4. credentials completeness for selected resource type.

## 9. Technical Implementation Plan

### Phase 1: Logic Consolidation

1. Move copy prefill logic from helper classes into unified editor use-case.
2. Standardize edit/copy validators and warning rules.

### Phase 2: UI Convergence

1. Refactor edit screen to shared editor components.
2. Add dirty-state and reset behavior.

### Phase 3: Duplicate Entry Points

1. Add `Duplicate` from resource list context menu.
2. Add `Save as Copy` from edit screen.

### Phase 4: Credential Safety Hardening

1. Implement/review credential cloning or safe sharing policy.
2. Add migration safeguards if schema adaptation is required.

### Phase 5: Optional Batch Variations

1. Implement under feature flag.
2. Validate performance and rollback behavior.

## 10. Acceptance Criteria

### Functional

1. `EDIT` and `COPY` are handled by unified form orchestration.
2. Save is blocked for invalid state and no-change edit state.
3. `Duplicate` creates a new resource with safe defaults.
4. Name collisions are resolved predictably.

### Safety

1. Critical field transitions trigger contextual warnings.
2. Destination/read-only constraints are enforced.
3. Credential policy is deterministic and documented.

### UX

1. Edit and copy interactions are consistent with create flow.
2. User can revert unsaved edits in one action.

### Technical

1. No new Main-thread I/O in edit/copy flows.
2. No regression in existing resource operations.

## 11. Metrics

1. Reduce median clicks to duplicate similar resource (baseline vs target).
2. Reduce edit/copy validation error retries.
3. Zero crash regressions in credential-related edit/copy scenarios.

## 12. Risks and Mitigations

1. **Risk:** Credential ownership ambiguity causes side effects between resources.  
   **Mitigation:** explicit policy + integration tests for shared/duplicated credentials.
2. **Risk:** Over-warning degrades UX.  
   **Mitigation:** warn only on semantically critical transitions.
3. **Risk:** Batch variations create accidental duplicates/noise.  
   **Mitigation:** preview + confirmation + optional feature flag.

## 13. Definition of Done

1. Unified architecture handles `EDIT` and `COPY` in production path.
2. Acceptance criteria are satisfied across supported resource types.
3. Documentation reflects credential behavior and conflict policy.
4. Regression checklist passes for edit/copy critical scenarios.
