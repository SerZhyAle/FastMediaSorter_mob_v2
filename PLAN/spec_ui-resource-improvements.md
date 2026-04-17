# Specification: UI Improvements for Resource Creation and Editing Screens

**Version**: 1.1  
**Status**: DRAFT, corrected against current codebase  
**Date**: 2026-04-17  
**Author**: User  

---

## 1. EXECUTIVE SUMMARY

This specification defines UI/UX improvements for resource creation and editing flows in portrait and landscape orientations.

The scope covers two existing UI paths:

- `AddResourceActivity` as the legacy multi-provider creation screen.
- `ResourceEditorActivity` + `ResourceEditorFragment` as the newer unified create/edit/copy flow.

The goal is to reduce vertical space usage, normalize grouping of related settings, keep primary actions visible, and remove option parity gaps across resource types and orientations.

---

## 2. FLAVOR SCOPE

| Feature | Standard | Lite | Photos | Legacy |
|---------|:--------:|:----:|:------:|:------:|
| Collapsible groups | ✓ | ✓ | ✓ | ✓ |
| Group state persistence | ✓ | ✓ | ✓ | ✓ |
| Resource creation/edit UI | ✓ | ✓ | ✓ | ✓ |

Notes:

- Feature availability inside a group remains governed by existing flavor flags and resource-type schema.
- The UI structure must not expose controls for unsupported capabilities in a given flavor.

---

## 3. REQUIREMENTS

### 3.1 Functional Requirements

#### F1: Sectioned Layout for Resource Screens

- **F1.1** Resource forms shall be organized into explicit sections using the existing View System and Material card/header pattern already used in `ResourceEditorFragment`.
- **F1.2** The canonical grouping model shall be:
  - **Group A: Main Information**: resource type, host/path/share/folder, name, username, password, authentication controls. Always visible. Not collapsible.
  - **Group B: Conditions / Scanning**: scan subdirectories, all files, hidden files, thumbnail behavior, remember file list, "Show subfolders separately". Collapsible.
  - **Group C: Media Types**: media type checkboxes and optional profile preset. Collapsible.
  - **Group D: Additional Options**: destination, read-only, comment, PIN, advanced or informational controls. Collapsible.
- **F1.3** Default state for new sessions shall be:
  - Group A expanded.
  - Groups B, C, D collapsed.
- **F1.4** `ResourceEditorFragment` may keep its existing section names if functionally equivalent, but the content distribution shall align with the canonical grouping above.

#### F2: Group State Persistence

- **F2.1** Expand/collapse state shall be persisted locally.
- **F2.2** Restored state shall be scoped at minimum by:
  - screen (`add_resource` vs `resource_editor`)
  - resource type (`LOCAL`, `SMB`, `SFTP`, `FTP`, `CLOUD` provider-specific branch when applicable)
  - orientation (`portrait`, `landscape`)
- **F2.3** Persistence must apply consistently to both creation and editing flows.
- **F2.4** Existing global section-state keys in `ResourceEditorFragment` are not sufficient for the final target because they do not distinguish resource type or orientation.

#### F3: Primary Action Visibility

- **F3.1** The primary action must remain visible without requiring a full scroll to the bottom.
- **F3.2** For the unified editor flow, the current fixed action bar pattern is the reference behavior.
- **F3.3** For `AddResourceActivity`, the preferred target is a pinned action container outside the scrollable content, not a floating FAB.
- **F3.4** The action area must remain accessible when the software keyboard is shown.

#### F4: Option Parity Audit

- **F4.1** Audit create and edit flows for all supported resource types: Local, SMB, SFTP, FTP, Google Drive, OneDrive, Dropbox.
- **F4.2** Audit portrait and landscape variants separately where distinct XML layouts exist.
- **F4.3** The control labeled in product text as **"Show subfolders separately"** and represented in the form schema by `SHOW_SUBFOLDERS_AS_ITEMS` shall be available wherever the corresponding resource strategy supports it.
- **F4.4** A parity issue is already known on the legacy add flow: this option is present in the unified editor schema/UI but not consistently exposed in the legacy creation layout.

#### F5: Test Connection Placement

- **F5.1** For network resources, the **Test Connection** action shall appear immediately after the authentication controls required to perform the test.
- **F5.2** For SMB this means after username/password and before share/path/name fields that depend on a valid connection.
- **F5.3** For SFTP/FTP this means after the selected authentication block and before path/name/media-type sections.
- **F5.4** Cloud providers are exempt from this rule when authentication is provider-driven rather than credential-form-driven.

### 3.2 Non-Functional Requirements

- **Performance**: Expand/collapse state restore must be visually immediate and remain below perceptible lag threshold.
- **Animation**: Section expand/collapse must remain smooth on typical API 26+ devices.
- **Accessibility**: Section headers and action bars must remain keyboard and screen-reader accessible.
- **Consistency**: New layout behavior must reuse existing project patterns instead of introducing a second UI system for the same feature area.

---

## 4. API-LEVEL AND UI-STACK ANALYSIS

- **Target SDK**: 35.
- **Minimum SDK**:
  - Standard / Lite / Photos: 26.
  - Legacy flavor: 23.
- **UI stack in scope**: XML layouts, View System, Material Components / Material 3 styling, `NestedScrollView`, `ConstraintLayout`, `LinearLayout`, `MaterialCardView`.
- **Current persistence technology in scope**: `SharedPreferences` is already used for section state in the editor flow and is acceptable for this task.
- **Out of scope for this spec**: migrating these screens to Compose.

---

## 5. ARCHITECTURE & DATA FLOW

### 5.1 Current State

```
AddResourceActivity
├── Legacy XML per-provider form blocks
├── Mostly flat vertical structure
├── Partial pinned actions only for some branches
└── No unified collapsible-section model

ResourceEditorActivity
└── ResourceEditorFragment
    ├── XML card-based sections
    ├── Collapsible headers already implemented
    ├── SharedPreferences-based section persistence already implemented
    └── Fixed top action bar already implemented
```

### 5.2 Proposed Target State

```
Resource Form Screens
├── Shared section model
│   ├── Main Information (always visible)
│   ├── Conditions / Scanning (collapsible)
│   ├── Media Types (collapsible)
│   └── Additional Options (collapsible)
├── Screen-specific rendering
│   ├── AddResourceActivity -> legacy flow aligned to section model
│   └── ResourceEditorFragment -> existing sections refined to match model
├── Section state persistence
│   └── key(screen, resourceType, orientation, sectionId)
└── Primary actions outside scrollable content where applicable
```

### 5.3 Key Components

- **Existing reference implementation**: `ResourceEditorFragment` section header + card structure.
- **Section state manager**: lightweight local helper or repository wrapper for section expand/collapse state.
- **ViewModel role**: business validation, save/test actions, schema-driven visibility.
- **UI role**: render sections, react to toggles, and delegate persistence through the chosen state manager.

---

## 6. ARCHITECTURE COMPLIANCE

- **Clean Architecture**: resource validation, save, and connection-test behavior remain in ViewModel/use-case layers.
- **Schema-driven UI**: resource-type differences must continue to come from resource strategy/schema definitions, not hard-coded layout forks where avoidable.
- **MVVM**: UI observes form state and renders visibility; business rules stay out of Activity/Fragment code.
- **Transitional note**: current editor implementation stores section state directly in Fragment `SharedPreferences`. That is acceptable as current-state context, but the target design should avoid duplicating persistence logic independently in multiple screens.

---

## 7. TESTING PLAN

### 7.1 Unit Tests

- **UT1**: Section state save/load by `screen + resourceType + orientation + sectionId`.
- **UT2**: Section defaults remain stable when no saved state exists.
- **UT3**: State isolation between SMB and SFTP.
- **UT4**: State isolation between portrait and landscape.

### 7.2 Integration Tests

- **IT1**: Open unified editor for SMB, change section states, reopen, verify restore.
- **IT2**: Open legacy add flow for SMB/SFTP, change section states, reopen, verify restore.
- **IT3**: Rotate portrait -> landscape -> portrait and verify correct state restoration rules.
- **IT4**: Verify `SHOW_SUBFOLDERS_AS_ITEMS` visibility matches the active resource schema.
- **IT5**: Verify Test Connection appears after authentication controls for SMB and SFTP/FTP.

### 7.3 UI / E2E Tests

- **E2E1**: Create SMB resource and verify the primary add/save action remains reachable with collapsed and expanded sections.
- **E2E2**: Edit an existing resource and verify saved section state restoration.
- **E2E3**: Verify keyboard does not hide the primary action.
- **E2E4**: Verify screen reader focus order reaches section headers and primary action controls.

---

## 8. ACCESSIBILITY REQUIREMENTS

- **A1**: Every collapsible header must expose role and state semantics.
- **A2**: The header label and affordance icon must not rely on color alone.
- **A3**: Expand/collapse must be reachable through keyboard and D-pad navigation.
- **A4**: State changes must be announced to accessibility services.
- **A5**: Pinned action controls must stay in normal focus order and not become obscured by IME handling.

---

## 9. DECISIONS & TRADE-OFFS

### D1: Reuse Existing UI Pattern

**Decision**: Reuse and extend the existing XML/View-System section pattern from `ResourceEditorFragment`.

- **Rationale**: This matches the actual app architecture and minimizes visual inconsistency.
- **Trade-off**: Legacy `AddResourceActivity` still requires structural cleanup instead of a simple cosmetic patch.

### D2: Persistence Scope

**Decision**: Persist state by screen, resource type, orientation, and section id.

- **Rationale**: Screen-specific and type-specific preferences are valid, and portrait/landscape may need different defaults.
- **Example key**: `section_state_add_resource_smb_portrait_scanning`.

### D3: Primary Action Placement

**Decision**: Prefer pinned action containers outside the scroll area over floating buttons.

- **Rationale**: This is more consistent with the unified editor and less prone to keyboard overlap.
- **Alternative rejected**: FAB-only approach because it introduces a second interaction model for the same domain.

### D4: Scope Clarification

**Decision**: This specification is for form layout normalization and parity, not for a Compose migration.

- **Rationale**: The relevant screens in `app_v2` are implemented with XML/View System today.

---

## 10. OPEN ACTION ITEMS

- [ ] Audit `AddResourceActivity` provider blocks against the unified editor schema.
- [ ] Produce a field-parity matrix by resource type and orientation.
- [ ] Identify which legacy sections can be refactored to shared XML includes or helper builders.
- [ ] Define final persistence-key format and ownership.
- [ ] Validate IME behavior for pinned primary actions.
- [ ] Create Maestro coverage for section state, rotation, and parity.

---

## 11. REFERENCES

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceFormViewModel.kt`
- `app_v2/src/main/res/layout/activity_add_resource.xml`
- `app_v2/src/main/res/layout/fragment_resource_editor.xml`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/LocalResourceStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/SmbResourceStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/SftpResourceStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/FtpResourceStrategy.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/strategy/CloudResourceStrategy.kt`

---

**End of Specification**
