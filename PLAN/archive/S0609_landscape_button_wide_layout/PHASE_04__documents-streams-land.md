# Phase 04 - Documents and Streams landscape variants

**Strategic spec:** [`../S0609_landscape_button_wide_layout.md`](../S0609_landscape_button_wide_layout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (shared column convention)
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Create landscape variants for the two settings child fragments that currently fall back to portrait (`documents`, `streams`), satisfying CLAUDE.md Rule 11 parity and using the column convention where it saves height. The `media_container` 2-up card shell stays deferred (research 05, strategic §5.3 extensibility).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Read `research/05__fragments-without-landscape.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_documents.xml` | New | ≤ 150 |
| `app_v2/src/main/res/layout-land/fragment_settings_streams.xml` | New | ≤ 60 |

> Portrait `layout/fragment_settings_documents.xml` and `layout/fragment_settings_streams.xml` are the source of truth for the id set - the landscape variants MUST declare the identical id set so ViewBinding fields stay non-null in both orientations (avoid creating a new instance of the S0616 divergence bug). `media_container` gets no landscape file in this phase.

---

## Steps

### Step 04.1 - Create landscape documents layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_documents.xml` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create a landscape variant of `fragment_settings_documents.xml`. Start from the portrait file's exact id set (`rowSupportText`, `rowShowTextLineNumbers`, `rowSupportPdf`, `rowShowPdfThumbnails`, and any others present) - every id in portrait must exist in landscape. Portrait already pairs text+lineNumbers and pdf+thumbnails into weighted rows; preserve those and pair any remaining solo COMPACT toggle. Use the Phase 01 column shape and `nextFocusLeft/Right` on pairs. Keep WIDE rows full-width. Root container must match portrait's root type so it hosts correctly inside the media_container `FrameLayout`.

**Verification:**

- `Glob` - `app_v2/src/main/res/layout-land/fragment_settings_documents.xml` exists.
- `Bash` - every `android:id="@+id/..."` token in the portrait file also appears in the landscape file (id-set parity).
- `Grep` - `layout_weight="1"` present (at least the existing pairings preserved).
- `Grep -n "=\"#"` returns zero hardcoded hex colors.

**Status:** `[ ]` not done

---

### Step 04.2 - Create landscape streams layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_streams.xml` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create a landscape variant of `fragment_settings_streams.xml` declaring the identical id set as portrait (the support toggle + the tonal shortcut button). This fragment is tiny; the landscape file mainly exists for orientation parity (Rule 11) and to keep ViewBinding fields non-null. Match portrait's root container type. No column work is needed beyond what portrait has.

**Verification:**

- `Glob` - `app_v2/src/main/res/layout-land/fragment_settings_streams.xml` exists.
- `Bash` - portrait id set ⊆ landscape id set.
- `Grep -n "=\"#"` returns zero hardcoded hex colors.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] `.\a.ps1 fr` passes (both new layouts inflate).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the two new files.

---

## Handoff Notes to Next Phase

documents/streams now have landscape parity. media_container 2-up shell remains an explicit extensibility item (not built) - do not add it without owner direction.

---

## Rollback Plan

Delete the two new landscape files - no other surface affected.
