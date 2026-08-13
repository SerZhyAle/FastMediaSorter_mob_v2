# Phase 04 — docs-catalog-cleanup

**Strategic spec:** [`../S0151_instagram-threads-link-extraction-and-auth.md`](../S0151_instagram-threads-link-extraction-and-auth.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03 (all phases done)
**Blocks:** nothing — final phase
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Update trilingual feature docs, regenerate the class catalog, and stamp all modified files in the dev changelog.

---

## Prerequisites

- [ ] All phases 01–03 are ✅ Done.
- [ ] Ticket is in `Verified` status (set by `/spec-check S0151` after on-device test).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | — |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | — |

---

## Steps

### Step 04.1 — Update `docs/FEATURES*.md`

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In all three FEATURES files (EN/RU/UK), locate the existing bullet for the "Download from shared link" feature area. Append a sub-bullet (or expand the existing bullet) to describe S0151 user-facing behaviour:
>
> **English (`docs/FEATURES.md`):**
> ```
> - Instagram and Threads links: downloads the real video or carousel images instead of the preview thumbnail; if only a preview was found, offers sign-in (or re-sign-in) before saving anything. Threads links on `threads.com` are recognized alongside `threads.net`.
> ```
>
> **Russian (`docs/FEATURES_RU.md`):**
> ```
> - Ссылки Instagram и Threads: скачивает реальное видео или изображения карусели вместо превью-картинки; если удалось извлечь только превью — предлагает авторизоваться (или войти заново). Ссылки Threads с домена `threads.com` распознаются наравне с `threads.net`.
> ```
>
> **Ukrainian (`docs/FEATURES_UK.md`):**
> ```
> - Посилання Instagram і Threads: завантажує реальне відео або зображення каруселі замість прев'ю-картинки; якщо вдалося отримати лише прев'ю — пропонує авторизуватися (або увійти знову). Посилання Threads з домену `threads.com` розпізнаються нарівні з `threads.net`.
> ```
>
> Use the `/doc-update` skill to apply and mirror the changes across all three files in one pass.

**Verification:**

- `Grep` — `threads.com` matches in `docs/FEATURES.md`.
- `Grep` — `threads.com` matches in `docs/FEATURES_RU.md`.
- `Grep` — `threads.com` matches in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 04.2 — Catalog regen and dev changelog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Regenerate the class catalog for the `app_v2` module:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then add dev changelog entries for every file modified in Phases 01–04:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt" "S0151" "Add threads.com entry and isVideoFirstHost()"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt" "S0151" "Add OpenResult.SocialPreviewOnly"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt" "S0151" "Signal SocialPreviewOnly for video-first social hosts"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt" "S0151" "Signal SocialPreviewOnly for video-first social hosts"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt" "S0151" "Handle SocialPreviewOnly fall-through, add Result.Failed.SocialPreviewOnly"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0151" "Add s0151_* strings (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0151" "Add s0151_* strings (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0151" "Add s0151_* strings (UK)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt" "S0151" "Handle SocialPreviewOnly UX: auth/reauth dialog with retry"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0151" "Document Instagram/Threads real-media extraction"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0151" "Document Instagram/Threads real-media extraction (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0151" "Document Instagram/Threads real-media extraction (UK)"
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.md` last modified timestamp is after Phase 02 completion.
- `Grep` — `KnownAuthResources` appears in `dev/CATALOG/app_v2.md`.
- `Grep` — `SocialPreviewOnly` appears in `dev/CATALOG/app_v2.md` (or in `app_v2.jsonl`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `dev/CATALOG/app_v2.jsonl` and `app_v2.md` are current.
- [ ] `/spec-check S0151` returns `Verified` (run after all phases and after on-device test removes `BlockNeedUserTest`).

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

No code changes in this phase. Revert doc and catalog commits independently if needed.
