# Phase 01 - Persistence Inventory Gate

**Strategic spec:** [`../S1674_room-converters-store-enum-constant-names.md`](../S1674_room-converters-store-enum-constant-names.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2

## Objective

Create a static gate that inventories durable enum-name serialization and requires a matching name-preservation rule.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `scripts/quality/assert-enum-persistence-contract.ps1` | New | ≤ 450 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 450 |

## Steps

### Step 01.1 - Implement durable enum-name inventory gate

**Files:** `scripts/quality/assert-enum-persistence-contract.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Scan app source for enum `name` writes and matching `valueOf` reads at Room, DataStore, and SharedPreferences durability boundaries. Require every discovered enum to be covered by a base release rule that preserves its fields; emit the exact source and rule failure details.

**Why:**

Existing durable strings must remain readable after app updates, and a new persistence path must not silently bypass the protection.

**Verification:**

- `Glob` - `scripts/quality/assert-enum-persistence-contract.ps1` exists.
- `Grep` - `valueOf` and `SharedPreferences` are present in the new gate.
- PowerShell gate exits zero with the intended release rule present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - state set to done for S1674 step 01.1

### Step 01.2 - Register the gate in the fast static suite

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the enum persistence gate to the fast static gate map with a concise rationale that identifies durable enum names as an R8 update-safety contract.

**Why:**

The protection must run with routine quality checks so later edits cannot remove it unnoticed.

**Verification:**

- `Grep` - `assert-enum-persistence-contract.ps1` matches once in `scripts/quality/assert-fast-gates.ps1`.
- `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits zero.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Phase-boundary audit run with no unresolved P0/P1 finding.

## Handoff Notes to Next Phase

The gate reports the exact enum inventory and rule coverage required by the release configuration.
