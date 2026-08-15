# Phase 04 - Capability inventory, catalog, dev-log

**Strategic spec:** [`../S0612_standalone-nonimage-copy-move-groups.md`](../S0612_standalone-nonimage-copy-move-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 3 / 3

---

## Objective

Record the delivered capability in the developer feature inventory, regenerate the class catalog if the public API changed,
and confirm dev-log coverage. No public showcase edit per-spec (release-time only).

---

## Steps

### Step 04.1 - Record the capability in `docs/ALL_FEATURES.jsonl`

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Phases 01-03 done

**Prompt for developer:**

> Add one EN record via `scripts/all_features/add.ps1` describing the delivered capability: copy/move the externally-opened
> file to a configured destination folder directly from the standalone audio, document and text viewers (copy keeps the
> viewer open, move closes it). Reference spec `S0612`. Validate via `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - `docs/ALL_FEATURES.jsonl` contains a record with `"spec":"S0612"`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

---

### Step 04.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl` + `.md` (gitignored local index)
**Depends on:** Phases 01-03 done

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once (the three hosts gained injected fields /
> constructor sites). If `post-change.ps1` was already used per file with catalog sync, this is a confirmation run.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[x]` done

**Step Log:**

---

### Step 04.3 - Confirm dev-log coverage

**Files:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Phases 01-03 done

**Prompt for developer:**

> Confirm each host phase produced a dev-log entry (one logical change per host is acceptable). If any is missing, add via
> `.\scripts\add_to_dev_log.ps1`. Never edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` references the three standalone hosts (or one batched S0612 entry per host).

**Status:** `[x]` done

**Step Log:**

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` has the S0612 record and validates.
- [ ] Catalog regenerated.
- [ ] Dev-log covers all three hosts.

---

## Rollback Plan

Inventory/catalog/dev-log are additive metadata - remove the added records if the feature is reverted.
