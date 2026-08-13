# Phase 05 — Docs / Catalog / Cleanup

**Strategic spec:** [`../S0066_enh-network-transient-failure-classification-multi-protocol.md`](../S0066_enh-network-transient-failure-classification-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01–04
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Trilingual FEATURES update for the user-visible improvement (auto-recovery of thumbnails for SFTP/FTP after playback stops). Catalog regen for new + modified files. Dev-log sweep verifying every touched file has an entry.

---

## Prerequisites

- [ ] Phases 01–04 ✅ Done.
- [ ] Code-only changes verified by `/build` `standard debug`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (regen) | n/a |

---

## Steps

### Step 05.1 — Trilingual FEATURES update

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Add one bullet to each FEATURES file under the existing network-thumbnails / browse section. Wording per locale:
>
> - `docs/FEATURES.md` (EN): `Network thumbnail previews recover automatically after playback ends — for SMB, SFTP, and FTP connections (no manual refresh required).`
> - `docs/FEATURES_RU.md` (RU): `Превью на сетевых хранилищах автоматически восстанавливаются после остановки воспроизведения — для SMB, SFTP и FTP (ручное обновление не требуется).`
> - `docs/FEATURES_UK.md` (UK): `Перегляд мініатюр на мережевих сховищах автоматично відновлюється після зупинки відтворення — для SMB, SFTP та FTP (ручне оновлення не потрібне).`
>
> Author-style: use `..` not `...`; preserve `ё`/`Ё` in Russian. Place each bullet near the related "Network thumbnails" entry (consult the file for the right section).

**Verification:**

- `Grep` in `docs/FEATURES.md` — `recover automatically after playback ends` matches at least once.
- `Grep` in `docs/FEATURES_RU.md` — `автоматически восстанавливаются` matches at least once.
- `Grep` in `docs/FEATURES_UK.md` — `автоматично відновлюється` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Trilingual bullets added to FEATURES.md, FEATURES_RU.md, FEATURES_UK.md.

---

### Step 05.2 — Catalog regen + manual roles

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run the catalog refresh sequence:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> For the two new files, set role + status manually using the catalog `set.ps1` (consult `dev/CATALOG/README.md` for invocation):
>
> - `TransientReason.kt` — role: `domain-enum`, status: `Active`.
> - `NetworkResourceKey.kt` — role: `helper`, status: `Active`.
>
> Re-run `render.ps1` after `set.ps1` so the markdown reflects the manual fields.

**Verification:**

- `Grep` in `dev/CATALOG/app_v2.jsonl` — `TransientReason.kt` matches at least once.
- `Grep` in `dev/CATALOG/app_v2.jsonl` — `NetworkResourceKey.kt` matches at least once.
- `Grep` in `dev/CATALOG/app_v2.md` — `TransientReason` matches at least once.
- `Grep` in `dev/CATALOG/app_v2.md` — `NetworkResourceKey` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Catalog regen + manual roles set (TransientReason=new, NetworkResourceKey=tested). 913 records rendered.

---

### Step 05.3 — Dev log sweep

**Files:** —
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` contains a recent entry for every file modified across Phases 01–05:
>
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/TransientReason.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkResourceKey.kt`
> - `app_v2/src/test/java/com/sza/fastmediasorter/data/network/glide/NetworkResourceKeyTest.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkMediaDataSource.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt`
> - `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
> - `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
>
> For any missing file run:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "<path>" "spec-dev" "S0066: <one-line summary>"
> ```

**Verification:**

- `Grep` in `dev/CHANGELOG.md` — `TransientReason.kt` matches at least once.
- `Grep` in `dev/CHANGELOG.md` — `NetworkResourceKey.kt` matches at least once.
- `Grep` in `dev/CHANGELOG.md` — `NetworkMediaDataSource.kt` matches at least once with an `S0066` token nearby (within 5 lines).
- `Grep` in `dev/CHANGELOG.md` — `NetworkVideoFrameDecoder.kt` matches at least once with an `S0066` token nearby.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. All Phase 01-05 modified files have S0066 entries in dev/CHANGELOG.md.

---

### Step 05.4 — Final build gate

**Files:** —
**Depends on:** Steps 05.1–05.3

**Prompt for developer:**

> Run `/build` for `standard debug` one final time to confirm no regression after catalog/docs sweep.

**Verification:**

- `/build` skill returns success for `standard debug`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Final BUILD SUCCESSFUL (standard debug, 33s, v2.60.5031.812).

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] All Completion Gate items in `INDEX.md` are checked.
- [ ] `/spec-check S0066` ready to run.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit — docs/catalog only; no behavioral impact.
