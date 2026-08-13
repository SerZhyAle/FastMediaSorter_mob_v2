# Phase 01 - Child Spec Matrix

**Strategic spec:** [`../S0267_cloud-auth-unified-storage-research.md`](../S0267_cloud-auth-unified-storage-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Create the canonical matrix of follow-on strategic specs so every implementation ticket has a fixed slug, scope boundary, dependency list, and source-anchor set before any child `/spec` run starts.

---

## Prerequisites

- [ ] Strategic §6 items are all Resolved.
- [ ] Strategic §11.2 recommendation is still Hybrid Mirror.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0267_cloud-auth-unified-storage-research/CHILD_SPECS.md` | New | ≤ 400 |

---

## Steps

### Step 01.1 - Create the required child-spec list

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/CHILD_SPECS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CHILD_SPECS.md`. Add a `## Required child specs` section with one subsection per child slug below, in this exact order:
>
> 1. `cloud-auth-storage-foundation`
> 2. `google-drive-auth-mirror`
> 3. `dropbox-auth-mirror`
> 4. `onedrive-auth-mirror`
> 5. `settings-authorizations-unified-sources`
> 6. `settings-authorizations-unified-ui`
>
> Each subsection must contain flat bullets for: `Goal`, `In scope`, `Out of scope`, `Depends on`, `Validation class`, and `Primary source anchors`. Use strategic §11.3 as the source of truth for what each child ticket owns.

**Verification:**

- `Glob` - `PLAN/S0267_cloud-auth-unified-storage-research/CHILD_SPECS.md` exists.
- `Grep` - `cloud-auth-storage-foundation` present.
- `Grep` - `settings-authorizations-unified-ui` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS. Files: PLAN/S0267_cloud-auth-unified-storage-research/CHILD_SPECS.md (new, +60 lines). Dev log recorded.

---

### Step 01.2 - Add the optional post-release ticket

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/CHILD_SPECS.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Append a `## Optional follow-up` section containing exactly one child slug: `cloud-auth-auditor-extension`. Mark it as post-release and explicitly state that it must not block the first unified-authorizations release. Include the same bullets as in Step 01.1.

**Verification:**

- `Grep` - `## Optional follow-up` present.
- `Grep` - `cloud-auth-auditor-extension` present exactly once.
- `Grep` - `post-release` present at least once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS (expected: each pattern present | actual: 1, 1, 2). Files: PLAN/S0267_cloud-auth-unified-storage-research/CHILD_SPECS.md (+15 lines).

---

### Step 01.3 - Add dependency waves and source anchors

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/CHILD_SPECS.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add two final sections to `CHILD_SPECS.md`:
>
> - `## Delivery waves`
> - `## Shared source anchors`
>
> `Delivery waves` must group the child specs into:
>
> - Wave 1: `cloud-auth-storage-foundation`
> - Wave 2: `google-drive-auth-mirror`, `dropbox-auth-mirror`, `onedrive-auth-mirror`
> - Wave 3: `settings-authorizations-unified-sources`, `settings-authorizations-unified-ui`
> - Wave 4: `cloud-auth-auditor-extension`
>
> `Shared source anchors` must list the concrete current-system files that future child specs should inspect first: `AppDatabase.kt`, `NetworkCredentialsEntity.kt`, `NetworkCredentialsDao.kt`, `NetworkCredentialsRepositoryImpl.kt`, `AuthSessionRepository.kt`, `AuthSessionRepositoryImpl.kt`, `AuthSessionsListFragment.kt`, `AuthSessionsListViewModel.kt`, `GoogleDriveAuthCoordinator.kt`, `GoogleDriveCredentialsManager.kt`, `DropboxClient.kt`, `DropboxClientUtils.kt`, `OneDriveAuthCoordinator.kt`, `CredentialAuditor.kt`, and `UnusedCredentialPolicy.kt`.

**Verification:**

- `Grep` - `## Delivery waves` present.
- `Grep` - `Wave 3: ` present.
- `Grep` - `AuthSessionsListFragment.kt` present.
- `Grep` - `UnusedCredentialPolicy.kt` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Initial wave header used `**Wave N (...):**` form; verification predicate `Wave 3: ` failed (expected: ≥1 | actual: 0). Reformatted to `Wave N: <slugs> (annotation)`. Re-ran verification 4/4 PASS (expected: each present | actual: 1, 1, 2, 2).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `CHILD_SPECS.md` lists 6 required child slugs and 1 optional child slug (expected: 7 | actual: 7 `### N.` headings).
- [x] `Grep` for `TODO(phase-01)` returns zero hits in `CHILD_SPECS.md` (the single match in this phase file is the criterion's self-reference).
- [x] Dev log entry added for `CHILD_SPECS.md` via `scripts/post-change.ps1` (3 entries: Phase 01.1, 01.2, 01.3).

---

## Handoff Notes to Next Phase

Phase 02 consumes the slugs and boundaries from `CHILD_SPECS.md` and turns them into exact `/spec` command lines and approval-gate packets.

---

## Rollback Plan

Revert this phase commit. This phase adds planning docs only and does not touch production code or spec-catalog records.
