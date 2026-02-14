# Resource Creation and Editing Improvement Specification

## 1. Purpose

Define a unified, maintainable, and performant architecture for resource creation, editing, and copying in FastMediaSorter.

The target outcome is a single behavioral model for all resource operations (`CREATE`, `EDIT`, `COPY`) with consistent validation, connection testing, and UI behavior.

## 2. Scope

### In Scope

1. Unification of Add/Edit/Copy business logic.
2. Unified form state model for all resource types.
3. Strategy-based validation and connection test flow.
4. Shared UI component for create/edit/copy modes.
5. Standardized error handling and field-level validation.
6. Non-blocking save flow with background verification.

### Out of Scope

1. Migration to Jetpack Compose.
2. Changes in repository storage schema not required by this refactor.
3. Full redesign of Settings or unrelated screens.
4. Rewriting network clients (SMB/SFTP/FTP/Cloud SDK internals).

## 3. Current Pain Points

1. Business logic duplication across `AddResourceViewModel`, `EditResourceViewModel`, and helper classes.
2. Repeated validation and credential handling rules.
3. Type-specific conditional branches spread across ViewModels and UI.
4. Inconsistent UX between manual create, copy, and edit.
5. High change cost when introducing a new `ResourceType`.

## 4. Objectives

1. **Single Source of Truth:** one form state and one orchestration path for all modes.
2. **Consistency:** identical validation and connection test semantics across modes.
3. **Extensibility:** adding a new resource type should require localized changes only.
4. **Responsiveness:** no blocking operations in Main thread during test/save.
5. **Observability:** deterministic error model and explicit operation states.

## 5. Target Architecture

### 5.1 Domain Contracts

#### ResourceEditorMode

`CREATE | EDIT | COPY`

#### ResourceFormData

Canonical mutable form model containing:

1. Common fields: `name`, `resourceType`, `path`, `isWritable`, `scanSubdirectories`, media-type flags.
2. Credentials fields: `host`, `port`, `username`, `password`, `domain`, token references.
3. Optional type-specific fields grouped by subtype.
4. Metadata: `sourceResourceId` (for edit/copy), `isDirty`, `lastValidationResult`.

#### ResourceValidationResult

1. `isValid: Boolean`
2. `fieldErrors: Map<FieldKey, ErrorCode>`
3. `globalErrors: List<ErrorCode>`

#### ResourceConnectionTestResult

1. `status: Success | Failure | Timeout | Canceled`
2. `latencyMs: Long?`
3. `errorCode: ConnectionErrorCode?`
4. `diagnosticMessage: String?`

### 5.2 Strategy Layer

Define `ResourceStrategy`:

1. `validate(data: ResourceFormData): ResourceValidationResult`
2. `testConnection(data: ResourceFormData): ResourceConnectionTestResult`
3. `normalizeBeforeSave(data: ResourceFormData): ResourceFormData`
4. `fieldSchema(): List<FieldDefinition>`

Implementations:

1. `LocalResourceStrategy`
2. `SmbResourceStrategy`
3. `SftpResourceStrategy`
4. `FtpResourceStrategy` (if applicable in current flow)
5. `CloudResourceStrategy` (provider-aware via sub-strategies)

### 5.3 Orchestration Layer

Introduce `ResourceEditorUseCase` (or equivalent orchestration service):

1. Load initial state by mode (`CREATE`, `EDIT`, `COPY`).
2. Route validation and connection tests to selected strategy.
3. Build persistence-ready resource model.
4. Save resource and trigger async post-save verification/scan.

## 6. UI Architecture

### 6.1 Unified Screen Component

Use one reusable UI component (`ResourceEditorFragment` or equivalent) for all modes.

Inputs:

1. `mode: ResourceEditorMode`
2. `resourceId` (for `EDIT` / `COPY` source)
3. preselected `resourceType` (optional)

Behavior:

1. Mode-specific title and primary action label.
2. Dynamic fields based on strategy-provided schema.
3. Immediate field-level validation rendering.
4. Explicit `Test Connection` action for network/cloud resources.

### 6.2 ViewModel Responsibilities

`ResourceFormViewModel` should:

1. Hold immutable `StateFlow<ResourceEditorUiState>`.
2. Expose intent handlers: `onFieldChanged`, `onTestConnection`, `onSave`, `onRetry`.
3. Never execute blocking I/O in Main dispatcher.
4. Emit one-off UI events via `SharedFlow`.

### 6.3 UI State Model

`ResourceEditorUiState` includes:

1. `formData`
2. `fieldStates`
3. `isTestingConnection`
4. `isSaving`
5. `connectionResult`
6. `saveResult`
7. `isReadOnlyMode` (if required by permissions)

## 7. Save and Verification Workflow

1. Validate form synchronously in ViewModel/use-case.
2. If valid, persist resource immediately.
3. Return success to UI without waiting full scan.
4. Launch background verification/initial scan job.
5. Update resource status (`PendingVerification` -> `Verified` / `NeedsAttention`).

## 8. Error Handling and Messaging

1. Use unified error code model (`ValidationErrorCode`, `ConnectionErrorCode`).
2. Centralize user-facing formatting in `ConnectionErrorFormatter`.
3. Ensure consistent wording across Add/Edit/Copy flows.
4. Preserve technical details in logs, user-safe message in UI.

## 9. Security Requirements

1. Credentials must be handled only through secure storage pathways.
2. No plaintext credentials in logs or exceptions.
3. Copy mode must never expose masked credentials unexpectedly.
4. Connection test diagnostics must redact secrets.

## 10. Performance Requirements

1. Connection test runs on `Dispatchers.IO` with strict timeout policy.
2. Form initialization must not block first frame.
3. Save action should complete UI response quickly (target < 300 ms excluding background verification).
4. No redundant rescans triggered by transient form changes.

## 11. Acceptance Criteria

### Functional

1. `CREATE`, `EDIT`, and `COPY` use the same form orchestration path.
2. Validation behavior is consistent for identical inputs in all modes.
3. Connection test behavior is consistent for each resource type.
4. New resource type integration requires only:
   - one strategy implementation,
   - schema entry,
   - optional UI labels.

### UX

1. Form fields are rendered consistently by resource type.
2. Error feedback is immediate and field-specific where applicable.
3. Save action does not block on full verification/scan.

### Technical

1. No Main-thread I/O introduced by this refactor.
2. Duplication in Add/Edit code paths is significantly reduced.
3. Existing persistence compatibility is preserved.

## 12. Rollout Plan

### Phase 1: Domain and Strategy Foundation

1. Introduce `ResourceFormData`, validation/test result models.
2. Implement strategy interfaces and migrate existing rules.

### Phase 2: Orchestration and ViewModel

1. Add `ResourceEditorUseCase`.
2. Introduce unified `ResourceFormViewModel`.

### Phase 3: UI Unification

1. Build shared editor screen.
2. Wire `CREATE`, `EDIT`, `COPY` modes.

### Phase 4: Post-save Verification Pipeline

1. Add asynchronous verification flow and statuses.
2. Ensure non-blocking user experience.

### Phase 5: Cleanup and Regression

1. Remove deprecated duplicated paths.
2. Run regression checks for all resource types and auth variants.

## 13. Risks and Mitigations

1. **Risk:** Regression in edge-case credential flows.  
   **Mitigation:** Add scenario matrix tests per strategy and mode.
2. **Risk:** UI regressions due to dynamic field rendering.  
   **Mitigation:** Snapshot/manual QA checklist by resource type.
3. **Risk:** Async verification introduces state race conditions.  
   **Mitigation:** Explicit resource status state machine and idempotent updates.

## 14. Definition of Done

1. Unified create/edit/copy architecture is in production code path.
2. Acceptance criteria are satisfied for all supported resource types.
3. Debug build passes without new critical issues in touched modules.
4. Deprecated duplicated logic paths are removed or clearly marked for removal.
