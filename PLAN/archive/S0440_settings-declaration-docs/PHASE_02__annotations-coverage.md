# Phase 02 - Annotations and Coverage

**Strategic spec:** [`../S0440_settings-declaration-docs.md`](../S0440_settings-declaration-docs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Author the human-written "what it does" descriptions for every manifest key in EN/RU/UK, kept in a sidecar file keyed to the manifest; provide a coverage check that proves no key is undocumented.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `docs/settings/settings-manifest.json` exists with the full key set.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-annotations.json` | New | n/a |
| `scripts/docs/check-settings-annotations.ps1` | New | ≤ 180 |

> Descriptions live here, NOT in `strings.xml` - they are documentation copy and must not bloat the APK or ship to users (strategic §3.3).

---

## Steps

### Step 02.1 - Define the annotations file shape

**Files:** `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the annotations file as a JSON object keyed by the manifest `key`. Each value is an object `{ "en": "..", "ru": "..", "uk": ".." }` holding one short sentence describing what the setting does (not what it is named). Seed the file with every key present in `docs/settings/settings-manifest.json`, leaving values to be filled in Step 02.2. Keep keys sorted to match the manifest order for clean diffs.

**Verification:**

- `Glob` - `docs/settings/settings-annotations.json` exists.
- `Grep` - every manifest `key` from `docs/settings/settings-manifest.json` appears as a key in the annotations file (cross-checked by the Step 02.3 script).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. `settings-annotations.json` created keyed by manifest `key`, 169 unique keys (manifest has 171 entries; `rowConfirmDelete` + `rowDetailedErrors` appear in 2 sections each - deduped by key). 0 missing vs manifest, 0 orphan. Authored via friendly-android-doc-writer pass.

---

### Step 02.2 - Write the descriptions in EN/RU/UK

**Files:** `docs/settings/settings-annotations.json`
**Depends on:** Step 02.1

**Prompt for developer:**

> Fill every `en`/`ru`/`uk` value with a concrete, user-facing description of the setting's effect. Follow `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist) - plain, action-first, no filler. Use `..` not `...`, mandatory ё/Ё in RU. Each language must be a real translation, not a placeholder or copy of EN.

**Verification:**

- `Grep` - no empty string values (`": ""`) remain in the annotations file.
- `Grep` - no `"...”`/`"..."` three-dot ellipsis in the file.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. All 169 keys have non-empty en/ru/uk (0 empty), 0 three-dot ellipsis occurrences. RU uses ё/Ё, `..` not `...`; action-first effect descriptions per COMMUNICATION_POLICY §2/§6.

---

### Step 02.3 - Coverage and parity checker

**Files:** `scripts/docs/check-settings-annotations.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Write a PowerShell script that loads the manifest and the annotations, then fails (exit 1) if: any manifest key is missing from annotations, any annotation key is orphaned (not in the manifest), or any entry lacks a non-empty `en`/`ru`/`uk` value. Exit 0 with a one-line summary on success. This is the coverage/parity report that doubles as the "index of existing documentation" deliverable (strategic §2 goal 2).

**Verification:**

- `Glob` - `scripts/docs/check-settings-annotations.ps1` exists.
- Run `pwsh -NoProfile -File scripts/docs/check-settings-annotations.ps1`; record `expected: exit 0 | actual: <code>`.
- Temporarily remove one annotation value and confirm the script exits 1, then restore (record both runs).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. Script created (dedups manifest keys; checks missing/orphan/empty). positive run on real files: expected exit 0 | actual 0 ("169 unique keys, all en/ru/uk present, 0 orphans"). negative run on a temp copy with one blanked `en`: expected exit 1 | actual 1 (named the offending key). No real-file mutation (negative test used a temp copy).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `check-settings-annotations.ps1` exits 0 against the full manifest.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`settings-annotations.json` + `settings-manifest.json` together are the full input to the reference renderer in Phase 03. Coverage is guaranteed, so the renderer may assume every key has a description.

---

## Rollback Plan

Revert phase commit(s) - delete the annotations file and the checker. No code or user-facing surface changed.
