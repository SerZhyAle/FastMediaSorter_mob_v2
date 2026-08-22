# Compact specification: S1875 - Restore the missing S1777 deletion contract

**Ticket:** S1875
**Status:** Archived
**Priority:** 70
**Date:** 2026-08-21
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc finding while closing S1872 phase 02.

---

## Goal

The post-change facade must close a declared file deletion without treating the absent path as an unexpanded variable or a typo. A caller explicitly names removed paths, and the facade rejects a declaration when a named path still exists. Content-based gates continue to inspect only files that exist; the development log preserves the removed path as the only durable record of that change.

## 1. Problem / symptom

`scripts/post-change.ps1` accepts only existing values in `-File` and `-Files`. S1777 is marked Implemented, but the promised `-Deleted` parameter and its validation branch are absent from the tree. Closing the deletion of two obsolete scripts in S1872 therefore returned exit 2 before any applicable gate, catalog refresh or dev-log entry ran.

## 2. Root cause

The facade correctly refuses an arbitrary missing path, because a literal shell variable or typo must not receive a green closure. S1777 was closed from its intended design rather than the shipped implementation, so no explicit assertion distinguishes a deleted path from an invalid changed-file argument.

## 3. Fix

Add a separate declared-deletion input to the facade. It accepts paths only when they are absent, keeps them out of gates that require file contents, and records their removal in the logical change summary. A false deletion assertion remains an exit-2 cannot-verify result. Source deletions still refresh the relevant Kotlin catalog so an obsolete class cannot remain indexed.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1777, S1872.

## 4. Verification

The facade regression suite checks the declared-deletion parameter, the absent-path validation branch and the rejection of a still-existing path. PowerShell parsing, the script-cheatsheet sync and the project script gates must pass.

## 5. Done criteria

- The facade accepts an explicitly declared missing path without routing it to content-based validation.
- A path declared as deleted while still present returns exit 2 without writing a dev-log entry.
- The closure summary identifies declared removals and source deletions refresh the catalog.
- The regression suite and script-cheatsheet gate pass.

## Phase 01 - Restore deletion closure

**Objective:** Add the missing declared-deletion contract and protect it with a regression test.

**Files touched:** `scripts/post-change.ps1`, `scripts/post-change.tests/Run-Tests.ps1`, generated `docs/SCRIPT_CHEATSHEET.md`.

### Step 01.1 - Validate declared removals

**Prompt for developer:**

> Add `-Deleted` as an explicit optional path set. Split comma-separated paths, accept them only when absent, and retain exit 2 for a path declared deleted while it still exists. Keep `-File` and `-Files` for content inspection, but allow a declared deletion to be the only changed path.

**Why:**

The facade needs the caller's explicit assertion to distinguish a genuine removal from an invalid file argument without weakening the existing typo guard.

**Verification:**

- `-Deleted` appears in the PowerShell parameter block.
- The validation branch reports `named as deleted but still on disk`.

### Step 01.2 - Preserve closure bookkeeping

**Prompt for developer:**

> Keep deleted paths out of content-reading gates, include them in the dev-log summary with a `(deleted)` marker, and refresh the selected module catalog when a declared deletion is the only source change.

**Why:**

The facade must retain the gates that can inspect real files while keeping the deletion visible to the two bookkeeping outputs that survive it.

**Verification:**

- The changed-file routing excludes absent deleted paths from content gates.
- The catalog-sync condition accounts for declared deletions.

### Step 01.3 - Cover and publish the contract

**Prompt for developer:**

> Extend the post-change regression suite for the parameter and both validation outcomes, then regenerate the script cheatsheet from the PowerShell parameter block.

**Why:**

The contract was previously documented without shipping, so its executable regression coverage and generated call signature must now derive from the actual script.

**Verification:**

- `scripts/post-change.tests/Run-Tests.ps1` exits 0.
- `scripts/utils/help.ps1 -Check` exits 0.

## Last Audit

**Date:** 2026-08-21
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

- PASS: `-Deleted` is exposed by the facade and generated script reference.
- PASS: an existing path declared deleted exits 2 before any mutable closure step.
- PASS: absent deleted paths have separate routing, log markers and catalog-sync input.
- PASS: `scripts/post-change.tests/Run-Tests.ps1`, `help.ps1 -Check`, document-registry validation and `git diff --check` passed.
- PASS: the post-change closure wrote one S1875 development-log entry.
- EXEMPT: this is internal tooling, so no user-facing capability record is needed.
