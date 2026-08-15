# Phase 06 — docs-catalog-cleanup

**Strategic spec:** [`../S0157_link-auth-offer-and-dismissal-ux.md`](../S0157_link-auth-offer-and-dismissal-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04, Phase 05
**Blocks:** — (final phase)
**Steps done:** 4 / 4
**Started:** 2026-05-11
**Completed:** 2026-05-11

---

## Objective

Update user-facing docs, regenerate the class catalog, and run the full locale audit to close out S0157.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Phase 04 is ✅ Done.
- [ ] Phase 05 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/RECEIVING_LINKS_RU.md` | Modified | — |
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |

---

## Steps

### Step 06.1 — Update `docs/RECEIVING_LINKS_RU.md`

**Files:** `docs/RECEIVING_LINKS_RU.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Update the sections in `docs/RECEIVING_LINKS_RU.md` that describe the auth offer and dismissal model:
>
> - **§ Проактивное предложение авторизации**: update to describe 3-button dialog (Добавить / Пропустить / Не спрашивать), universal host coverage (not just `KnownAuthResources`), and the dismissed-record storage model (EncryptedCookieStore entry with `type=dismissed`, visible in settings).
> - **§ Настройки авторизации** (or equivalent section): describe that dismissed records appear in the list with a "(вы отказались)" label and a Delete button; deleting revokes the dismissal.
> - Remove references to `AuthOfferDismissalStore` as a separate store — its functionality is now merged into `EncryptedCookieStore`.
> - Add a note on the one-time wipe migration (S0157): existing sessions were wiped on first update.
>
> Use `..` (two dots), always use `ё`/`Ё` in Russian text. Follow the doc's existing section structure and heading style.

**Verification:**

- `Grep` — "три кнопки" or "Не спрашивать" appears in `RECEIVING_LINKS_RU.md`.
- `Grep` — "AuthOfferDismissalStore" does NOT appear as a current-implementation reference (only as historical context if at all).

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. "Не спрашивать" 5 hits; `AuthOfferDismissalStore` appears only as historical "упразднён" context. Files: RECEIVING_LINKS_RU.md (+16 lines net).

---

### Step 06.2 — Update `docs/FEATURES.md` + `_RU.md` + `_UK.md`

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> In the "Link sharing / Download from links" feature area, update the auth offer bullet to reflect:
>
> - EN: "When no sign-in exists for a host, the app offers to add one with three options: add now, skip this time, or never ask again for this site."
> - RU: "При получении ссылки с незнакомого хоста предлагается войти: добавить авторизацию, пропустить сейчас или больше не спрашивать для этого сайта."
> - UK: "При отриманні посилання з незнайомого хоста пропонується авторизуватися: додати авторизацію, пропустити зараз або більше не питати для цього сайту."
>
> Also add a bullet for the settings change:
>
> - EN: "Sites where the offer was permanently declined appear in the authorization settings list and can be re-enabled by deleting the entry."
> - RU: "Сайты, для которых выбрано «Не спрашивать», отображаются в списке авторизаций — запись можно удалить, чтобы предложение снова появлялось."
> - UK: "Сайти, для яких обрано «Не питати», відображаються у списку авторизацій — запис можна видалити, щоб пропозиція знову з'являлась."

**Verification:**

- `Grep` — "never ask again" or equivalent present in all three FEATURES files.
- `Grep` — dismissed-record settings note present in all three FEATURES files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. "never ask again" in FEATURES.md; "не спрашивать" in FEATURES_RU.md; "не питати" in FEATURES_UK.md. Dismissed-record settings note present in all three files. Files: FEATURES.md (+2 lines), FEATURES_RU.md (+2 lines), FEATURES_UK.md (+2 lines).

---

### Step 06.3 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> `AuthOfferDismissalStore` must no longer appear in the catalog. New entries for S0157 string files will not appear (they are resource files, not Kotlin classes). Verify `EncryptedCookieStore` entry shows `loc` consistent with the additions from Phase 01.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` modified date is today.
- `Grep` — `AuthOfferDismissalStore` does NOT appear in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. AuthOfferDismissalStore absent from app_v2.md (0 hits). catalog mod time today. Files: dev/CATALOG/app_v2.jsonl (regen), dev/CATALOG/app_v2.md (regen).

---

### Step 06.4 — Final locale audit and dev log

**Files:** all modified
**Depends on:** Step 06.3

**Prompt for developer:**

> 1. Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0157_"` — must exit code 0.
> 2. Run dev log for every file in "Files Touched" across all phases that hasn't already been logged:
>    ```powershell
>    .\scripts\add_to_dev_log.ps1 "docs/RECEIVING_LINKS_RU.md" "S0157-phase06" "Update auth offer and dismissal model documentation"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0157-phase06" "Add 3-button auth offer and dismissed-record settings bullets"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0157-phase06" "RU mirror — auth offer UX update"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0157-phase06" "UK mirror — auth offer UX update"
>    ```

**Verification:**

- `Bash` — `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0157_"` exits code 0.
- `Grep` — `RECEIVING_LINKS_RU.md` appears in `dev/CHANGELOG.md` with today's date.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. Locale audit s0157_ → 5 keys OK/OK/OK (exit 0). RECEIVING_LINKS_RU.md entry in CHANGELOG.md with today's date. Dev log recorded for all docs files.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] String locale audit passes.
- [ ] Catalog reflects current state (no `AuthOfferDismissalStore`).
- [ ] All six phases show ✅ Done in INDEX.md.
- [ ] INDEX.md `Status:` flipped to `Done`.
- [ ] Run `/spec-check S0157`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Docs-only changes (except catalog regen). Revert commit(s) to restore previous doc state.
