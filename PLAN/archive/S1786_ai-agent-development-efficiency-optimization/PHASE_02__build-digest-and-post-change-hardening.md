# Phase 02 - Build Failure Digest and Post-Change Hardening

**Strategic spec:** [`../S1786_ai-agent-development-efficiency-optimization.md`](../S1786_ai-agent-development-efficiency-optimization.md)  
**Tactical index:** [`INDEX.md`](INDEX.md)  
**Status:** ✅ Done
**Depends on:** Phase 01  
**Blocks:** Phase 03  
**Steps done:** 3 / 3
**Started:** 2026-08-17
**Completed:** 2026-08-17

---

## Objective

Integrate automatic `build-failure-digest.ps1` (`bfd`) output when Gradle compiles fail, fix file-deletion closure bug (S1777), and add early argument checks in `post-change.ps1` to prevent red closure attempts.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/builders/check-standard-fast.ps1` | Modified | ≤ 400 |
| `scripts/builders/build-debug.PS1` | Modified | ≤ 500 |
| `scripts/post-change.ps1` | Modified | ≤ 1200 |

---

## Steps

### Step 02.1 - Auto-print build-failure-digest on compile errors

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Integrated bfd in check-standard-fast.ps1 and build-debug.PS1, fixed deletion S1777 and added early hints in post-change.ps1
**Files:** `scripts/builders/check-standard-fast.ps1`, `scripts/builders/build-debug.PS1`  
**Depends on:** start of phase  

**Prompt for developer:**

> When Gradle execution finishes with a non-zero exit code in `check-standard-fast.ps1` and `build-debug.PS1`, invoke `scripts/builders/build-failure-digest.ps1` to parse the failure and output a concise, structured error block (file, line, symbol, message) to stderr before exiting.

**Why:**

`build-failure-digest.ps1` exists but was disconnected from build scripts, forcing agents to waste turns reading long raw logs.

**Verification:**

- `Grep` - `build-failure-digest.ps1` is referenced in `scripts/builders/check-standard-fast.ps1` and `scripts/builders/build-debug.PS1`.

---

### Step 02.2 - Fix file deletion handling in post-change.ps1 (S1777)

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Integrated bfd in check-standard-fast.ps1 and build-debug.PS1, fixed deletion S1777 and added early hints in post-change.ps1
**Files:** `scripts/post-change.ps1`  
**Depends on:** Step 02.1  

**Prompt for developer:**

> In `scripts/post-change.ps1`, modify the initial path validation loop: if a candidate file path does not exist on disk, check `git status --porcelain` to verify if it was deleted/removed in git. If deleted, treat as valid deleted change rather than rejecting with `exit 2 (could not verify)`. Skip file-existence gates for deleted paths while running registry/catalog sync and dev-log.

**Why:**

Rule 20 requires deleting dead code, but `post-change.ps1` currently rejects deletions with exit 2, forcing agents to bypass the closure facade.

**Verification:**

- `Grep` - deleted file git status check is present in `scripts/post-change.ps1`.

---

### Step 02.3 - Add early hints for -ScopeToFile and -RegistryAck

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Integrated bfd in check-standard-fast.ps1 and build-debug.PS1, fixed deletion S1777 and added early hints in post-change.ps1
**Files:** `scripts/post-change.ps1`  
**Depends on:** Step 02.2  

**Prompt for developer:**

> In `scripts/post-change.ps1`, perform document-registry path matching before executing slow static gates. If modified files intersect registered documents and `-RegistryAck` is missing, or if working tree contains foreign uncommitted files and `-ScopeToFile` is omitted, emit an immediate helpful diagnostic guidance line.

**Why:**

Missing `-RegistryAck` and full-tree drift were responsible for dozens of second-pass red runs of `post-change.ps1` in the 14-day telemetry corpus.

**Verification:**

- `Grep` - early registry check or `-ScopeToFile` guidance line in `scripts/post-change.ps1`.

---

## Phase Done Criteria

- [ ] All 3 steps show `[x] done`.
- [ ] `post-change.ps1` successfully validates deleted files without error 2.
