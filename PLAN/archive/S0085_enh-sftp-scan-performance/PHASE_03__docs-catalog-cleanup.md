# Phase 03 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0085_enh-sftp-scan-performance.md`](../S0085_enh-sftp-scan-performance.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Update trilingual feature docs, regenerate the module catalog, and record dev log entries for all files modified in this spec.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Project compiles cleanly.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 2000 |
| `docs/FEATURES_RU.md` | Modified | ≤ 2000 |
| `docs/FEATURES_UK.md` | Modified | ≤ 2000 |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |

---

## Steps

### Step 03.1 — Update FEATURES.md (EN)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md` §15 "Network Sources", find the bullet starting with **"Secure SFTP"**. Append the following clause after the existing sentence:
>
> `Scanning large SFTP libraries is significantly faster: file attributes (size, date) are read from the directory listing in a single protocol round-trip instead of one request per file. A progress counter is shown during the scan.`

**Verification:**

- `Grep` — `single protocol round-trip` returns exactly **one** hit in `docs/FEATURES.md`.

**Status:** `[ ]` not done

---

### Step 03.2 — Update FEATURES_RU.md

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `docs/FEATURES_RU.md` §15, find the bullet corresponding to "Secure SFTP". Append:
>
> `Сканирование больших SFTP-библиотек стало значительно быстрее: атрибуты файлов (размер, дата) считываются из ответа каталога за один сетевой запрос вместо отдельного запроса на каждый файл. В процессе сканирования отображается счётчик прогресса.`

**Verification:**

- `Grep` — `за один сетевой запрос` returns exactly **one** hit in `docs/FEATURES_RU.md`.

**Status:** `[ ]` not done

---

### Step 03.3 — Update FEATURES_UK.md

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `docs/FEATURES_UK.md` §15, find the bullet corresponding to "Secure SFTP". Append:
>
> `Сканування великих SFTP-бібліотек стало значно швидшим: атрибути файлів (розмір, дата) зчитуються з відповіді каталогу за один мережевий запит замість окремого запиту на кожен файл. Під час сканування відображається лічильник прогресу.`

**Verification:**

- `Grep` — `за один мережевий запит` returns exactly **one** hit in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 03.4 — Regenerate catalog and record dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run the following in sequence:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt" "S0085" "Add SftpFileListing data class; listFiles() returns attrs from ls response"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt" "S0085" "Eliminate per-file stat() in scanFolder/scanFolderPaged/listDirectoryContents; wire onProgress"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0085" "Update Secure SFTP bullet — single round-trip listing + progress indicator"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0085" "Update Secure SFTP bullet (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0085" "Update Secure SFTP bullet (UK)"
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and modification time is within this session.
- `Grep` — `SftpFileListing` returns at least one hit in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] EN/RU/UK FEATURES files updated and verified.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated with `SftpFileListing` visible.
- [ ] Dev log entries recorded for all modified files.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — no data migration or persistent state changed.
