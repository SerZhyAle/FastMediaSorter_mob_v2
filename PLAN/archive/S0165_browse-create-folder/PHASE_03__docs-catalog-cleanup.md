# Phase 03 — Docs, Catalog, Cleanup

**Strategic spec:** [`../S0165_browse-create-folder.md`](../S0165_browse-create-folder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** —
**Completed:** 2026-05-13

---

## Objective

Update the trilingual feature docs, regenerate the class catalog, and finalize the dev changelog — completing the spec lifecycle gate.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Build is green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 5 lines added |
| `docs/FEATURES_RU.md` | Modified | ≤ 5 lines added |
| `docs/FEATURES_UK.md` | Modified | ≤ 5 lines added |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |

---

## Steps

### Step 03.1 — Update trilingual feature docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 02 complete

**Prompt for developer:**

> In the **Browse** section of each file, add one bullet describing the new Create Folder button. Use the existing bullet style and tone consistent with `docs/COMMUNICATION_POLICY.md` (informative, feature-forward, no imperative commands, no ellipsis). Do not add a new section — append to the existing Browse feature list.
>
> - EN (`docs/FEATURES.md`): `- **Create Folder button** in Browse toolbar — visible for writable resources with "show subfolders as items" enabled; creates a folder in the current browsed path.`
> - RU (`docs/FEATURES_RU.md`): `- **Кнопка «Создать папку»** в тулбаре Browse — отображается для записываемых ресурсов с включёнными «каталогами как элементами»; создаёт папку в текущем подкаталоге.`
> - UK (`docs/FEATURES_UK.md`): `- **Кнопка «Створити папку»** на панелі Browse — відображається для записуваних ресурсів із увімкненими «каталогами як елементами»; створює папку в поточному підкаталозі.`
>
> Strings pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist: factual, no hype, no ellipsis, no imperative, no emojis.

**Verification:**

- `Grep` — `Create Folder button` present in `docs/FEATURES.md`.
- `Grep` — `Кнопка «Создать папку»` present in `docs/FEATURES_RU.md`.
- `Grep` — `Кнопка «Створити папку»` present in `docs/FEATURES_UK.md`.
- Strings pass COMMUNICATION_POLICY §6 checklist: no `...`, no imperative verbs addressing the user, no hype words.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: FEATURES.md, FEATURES_RU.md, FEATURES_UK.md (modified). Dev log recorded.

---

### Step 03.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run catalog scan and render for the `app_v2` module:
>
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> No manual edits to `dev/CATALOG/app_v2.jsonl` — the scan script preserves manual `role` and `status` fields.

**Verification:**

- `Grep` — `BrowseButtonSetupHelper` present in `dev/CATALOG/app_v2.md` (confirms scan ran).
- `Grep` — `BrowseStateUiUpdater` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. 1034 records scanned and rendered.

---

### Step 03.3 — Dev log entries

**Files:** `dev/CHANGELOG.md` (via script — do not edit directly)
**Depends on:** Step 03.2

**Prompt for developer:**

> Add dev log entries for all files modified across all phases. Run one line per file:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/drawable/ic_create_new_folder_24.xml" "S0165" "Add create_new_folder vector drawable"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/activity_browse.xml" "S0165" "Add btnCreateFolder to portrait toolbar"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/activity_browse.xml" "S0165" "Add btnCreateFolder to landscape toolbar"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt" "S0165" "Add onCreateFolderClicked callback and handler"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseStateUiUpdater.kt" "S0165" "Add updateCreateFolderButtonVisibility"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt" "S0165" "Wire onCreateFolderClicked to ResourceOpsMenuManager"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0165" "Document Create Folder toolbar button"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0165" "Document Create Folder toolbar button (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0165" "Document Create Folder toolbar button (UK)"
> ```

**Verification:**

- `Grep` — `S0165` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 1/1 PASS. 17 S0165 entries in CHANGELOG.md.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Run `/spec-check S0165` — must return `Verified` (or advance spec to `BlockNeedUserTest` if device test is required first).

---

## Handoff Notes to Next Phase

Final phase — see `INDEX.md` Completion Gate. After this phase, run `/spec-check S0165` to close the ticket.

---

## Rollback Plan

Revert phase commit(s) — no data migration or schema change.
