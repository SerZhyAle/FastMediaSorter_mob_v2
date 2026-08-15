# Phase 03 — Docs, Catalog, Changelog Cleanup

**Strategic spec:** [`../S0133_accept-shared-files-default-on.md`](../S0133_accept-shared-files-default-on.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Reflect the new default-ON behavior in the trilingual feature docs, regenerate the class catalog for the new bootstrapper, and ensure the dev changelog covers every modified file.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Phase 02 ✅ Done.
- [ ] Working tree contains the Phase 01–02 changes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ +3 lines |
| `docs/FEATURES_RU.md` | Modified | ≤ +3 lines |
| `docs/FEATURES_UK.md` | Modified | ≤ +3 lines |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |
| `dev/CHANGELOG.md` | Modified (append-only via script) | — |

> No `res/values/strings.xml` keys added or removed in this spec — string-locale audit is not required.

---

## Steps

### Step 03.1 — Update trilingual FEATURES docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the existing entry about share-receiver / "accept shared files" in each of the three FEATURES files (search for `share` / `общи` / `спіль`). Update the entry to state that the feature is enabled by default on fresh installs and applies on the first app launch; the user can disable it in `Settings → Playback → Default Media Player → Accept shared files`. Keep the wording terse — one sentence per language. If no existing entry mentions the toggle, add a one-line bullet under the share-receiver / OS integration section that already exists.
>
> Verify EN/RU/UK parity: the same fact is described in all three files.

**Verification:**

- `Grep -i "default.*shared|shared.*default"` matches at least once in `docs/FEATURES.md`.
- `Grep -i "по умолчанию.*общи|общи.*по умолчанию"` matches at least once in `docs/FEATURES_RU.md`.
- `Grep -i "за замовчуванням.*спіль|спіль.*за замовчуванням"` matches at least once in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. EN: 2 hits, RU: 1 hit, UK: 1 hit. Bullet inserted right after the "Default player system hooks" / "Интеграция как плеер по умолчанию" / "Інтеграція як плеєр за замовчуванням" entries.

---

### Step 03.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — independent of Step 03.1

**Prompt for developer:**

> Run the catalog scan + render for `app_v2`:
>
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/scan.ps1   -Module app_v2
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then fill `role` + `status` for the new class via `set.ps1` (see `dev/CATALOG/README.md`):
>
> - class: `DefaultPlayerStateBootstrapper`
> - role: `bootstrap`
> - status: `active`

**Verification:**

- `Grep -n "DefaultPlayerStateBootstrapper"` matches at least once in `dev/CATALOG/app_v2.jsonl`.
- `Grep -n "DefaultPlayerStateBootstrapper"` matches at least once in `dev/CATALOG/app_v2.md`.
- The jsonl record for `DefaultPlayerStateBootstrapper` contains `"role"` and `"status"` non-empty.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. `scan.ps1` indexed 994 files; `render.ps1` regenerated `app_v2.md`. `set.ps1` set `role="Idempotent process-start hook reconciling SEND/VIEW component aliases with DataStore"`, `status=new` (substituted for the spec's `active` which is not in the catalogue's `ValidateSet` — allowed values are `new/tested/legacy/todo/unknown`; `new` is the correct semantic for freshly added code).

---

### Step 03.3 — Append dev changelog entries

**Files:** `dev/CHANGELOG.md` (via script — never edit by hand)
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` once per modified file (Phase 01 + Phase 02 + this phase):
>
> 1. `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` — `S0133` — `Default-ON for acceptSharedFiles`.
> 2. `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` — `S0133` — `DataStore read fallback for acceptSharedFiles → true`.
> 3. `app_v2/src/main/java/com/sza/fastmediasorter/core/init/DefaultPlayerStateBootstrapper.kt` — `S0133` — `Idempotent system-component-state bootstrap on every process start`.
> 4. `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` — `S0133` — `Wire DefaultPlayerStateBootstrapper into onCreate`.
> 5. `docs/FEATURES.md` (and the two locale mirrors) — `S0133` — `Document default-ON behavior for accept shared files`.

**Verification:**

- `Grep -n "S0133"` matches at least 5 distinct lines in `dev/CHANGELOG.md`.
- `Grep -n "DefaultPlayerStateBootstrapper.kt"` matches at least once in `dev/CHANGELOG.md`.
- `Grep -n "AppSettings.kt"` AND `Grep -n "SettingsRepositoryImpl.kt"` AND `Grep -n "FastMediaSorterApp.kt"` each match at least once in `dev/CHANGELOG.md` for this date.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. 18 lines mention `S0133` in `dev/CHANGELOG.md`; `DefaultPlayerStateBootstrapper.kt`/`AppSettings.kt`/`SettingsRepositoryImpl.kt`/`FastMediaSorterApp.kt` each ≥1 hit on 2026-05-10. Dev log entries written for 7 files (4 source + 3 FEATURES mirrors + 1 catalog jsonl).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project still compiles — `BUILD SUCCESSFUL in 30s` (verified 2026-05-10 after catalog regen).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Trilingual parity check passed (Step 03.1 verifications).
- [x] Catalog jsonl + md updated and committed alongside source changes.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md](INDEX.md) Completion Gate. Ready for `/spec-check S0133`.

---

## Rollback Plan

- Revert the FEATURES doc lines (one bullet per language).
- Restore `dev/CATALOG/app_v2.jsonl` + `app_v2.md` to the pre-bootstrap revision (or simply re-run scan/render after Phase 01/02 are reverted).
- Dev changelog entries remain — they are append-only and do not affect runtime.
