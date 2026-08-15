# Phase 02 - Release Name Protection

**Strategic spec:** [`../S1674_room-converters-store-enum-constant-names.md`](../S1674_room-converters-store-enum-constant-names.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2

## Objective

Pin the discovered durable enum members in the base release rules and prove the standard minified build accepts the configuration.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/proguard-rules.pro` | Modified | ≤ 500 |
| `scripts/quality/assert-enum-persistence-contract.ps1` | Modified | ≤ 500 |

## Steps

### Step 02.1 - Preserve durable enum members in base release rules

**Files:** `app_v2/proguard-rules.pro`
**Depends on:** Phase 01

**Prompt for developer:**

> Add a narrow, documented base rule for the app enum members identified by the inventory. Preserve member names without changing retained strings, class reachability, Room schema, or flavor-specific behaviour.

**Why:**

The persisted format already contains enum names, so a migration-free fix must stop release optimization from changing those names.

**Verification:**

- `Grep` - the rule covers each inventory enum and does not use `allowobfuscation`.
- `pwsh -NoProfile -File scripts/quality/assert-enum-persistence-contract.ps1` exits zero.

**Status:** `[x]` done

### Step 02.2 - Prove the minified standard variant

**Files:** `app_v2/proguard-rules.pro`, `scripts/quality/assert-enum-persistence-contract.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Build the standard release variant and inspect its generated mapping or equivalent artifact through the gate so each persistent enum member retains its stored name.

**Why:**

Only a minified artifact can prove the intended R8 behaviour instead of merely validating rule text.

**Verification:**

- Standard release build exits zero.
- Artifact check reports no renamed persistent enum member.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Standard release build exit 0, fresh minified artifact plus app_v2/build/outputs/mapping/standardRelease/mapping.txt (174 MB, 16:17). Gate extended with -Mapping and run against it: PASS, 10 durable enums pinned, 8 verified in the mapping, no renamed member. The other 2 do not appear at all - R8 shrank them out of the standard release, which is reachability rather than a rename, so the gate does not count them as proof. Reader is streaming in one pass; the first array-based draft would have loaded 174 MB.

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Phase-boundary audit run with no unresolved P0/P1 finding.

## Handoff Notes to Next Phase

Persistent enum strings are unchanged and base release rules prove their member names remain stable.
