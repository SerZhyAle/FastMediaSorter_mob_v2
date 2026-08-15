# Phase 04 - Docs, catalog, cleanup, device-test handoff

**Strategic spec:** [`../S0563_camera-unified-entry-mode-switch.md`](../S0563_camera-unified-entry-mode-switch.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 03
**Blocks:** -
**Steps done:** 0 / 2

---

## Objective

Record the delivered capability, refresh the local catalog, and hand off for on-device verification.

---

## Steps

### Step 4.1 - Capability inventory + catalog + dev log

**Prompt for developer:**

> Record the shippable capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1`
> (EN-only): the main-screen quick-capture menu now exposes a single "Camera" action with an in-screen
> photo/video switch. Run `scripts/catalog_sync.ps1 -Module app_v2` once for the new/changed classes.
> Add one batched dev-log entry for the S0563 change set.

**Verification:**

- `Grep` - `S0563` present in `docs/ALL_FEATURES.jsonl`.
- `dev/CATALOG/app_v2.jsonl` regenerated (local gitignored index).

**Status:** `[ ]` not done

---

### Step 4.2 - Device-test handoff

**Prompt for developer:**

> Confirm the `Timber.d("S0563:` device-test tag is in place (one tag, at the unified camera entry).
> Set status `BlockNeedUserTest` with a note describing what to verify on a real device: the merged
> "Camera" menu item launches the host; the `PHOTO|VIDEO` switch appears only when both modes are
> enabled and rebinds the preview; a switched-to-video recording saves a playable file to Movies and a
> photo to DCIM/Camera; fixed-mode entry points (OCR, resource capture, widget) still launch in their
> fixed mode with no switch.

**Verification:**

- Catalog status = `BlockNeedUserTest` with a non-empty status note.
- Exactly one `S0563:` Timber tag in `.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Both steps `[x] done`.
- [ ] Capability recorded, catalog synced, status `BlockNeedUserTest`.

---

## Rollback Plan

Docs/catalog only - revert the inventory and dev-log entries.
