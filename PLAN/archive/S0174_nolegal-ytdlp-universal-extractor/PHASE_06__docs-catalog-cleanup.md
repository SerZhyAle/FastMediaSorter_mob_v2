# Phase 06 — Docs, Catalog & Cleanup

**Strategic spec:** [`../S0174_nolegal-ytdlp-universal-extractor.md`](../S0174_nolegal-ytdlp-universal-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Update `docs/FEATURES.md` + mirrors with the noLegal yt-dlp capability; regenerate the class catalog; run the dev log for all changed files; verify no stale Timber tags or lint issues in touched files.

---

## Prerequisites

- [ ] Phases 01–05 are all ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ +3 lines |
| `docs/FEATURES_RU.md` | Modified | ≤ +3 lines |
| `docs/FEATURES_UK.md` | Modified | ≤ +3 lines |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | auto |
| `dev/CATALOG/app_v2.md` | Modified (regen) | auto |

---

## Steps

### Step 06.1 — Update FEATURES docs (EN / RU / UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate the noLegal section (search for "noLegal" or "No-Legal"). Add a bullet describing the new yt-dlp capability. Use the strategic §8 texts verbatim:
>
> - **EN** (`docs/FEATURES.md`): `noLegal flavor: universal media download from 1800+ sites (YouTube, Instagram, TikTok, Facebook, Threads, X, and more) via yt-dlp engine with automatic auth cookie passthrough.`
> - **RU** (`docs/FEATURES_RU.md`): `noLegal: универсальная загрузка медиа с 1800+ сайтов (YouTube, Instagram, TikTok, Facebook, Threads, X и др.) через движок yt-dlp с автоматической передачей cookies авторизации.`
> - **UK** (`docs/FEATURES_UK.md`): `noLegal: універсальне завантаження медіа з 1800+ сайтів (YouTube, Instagram, TikTok, Facebook, Threads, X тощо) через рушій yt-dlp з автоматичною передачею cookies авторизації.`
>
> If there is no existing noLegal section, add a `## noLegal Flavor` heading and the bullet under it. Invoke `/doc-update` if the feature doc mirrors need trilingual sync.

**Verification:**

- `Grep` — `yt-dlp` appears in `docs/FEATURES.md`.
- `Grep` — `yt-dlp` appears in `docs/FEATURES_RU.md`.
- `Grep` — `yt-dlp` appears in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 3/3 PASS. noLegal Flavor section added to all three FEATURES docs. Dev log recorded.

---

### Step 06.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase (runs after all code phases done)

**Prompt for developer:**

> Run the catalog scan and render for the `app_v2` module to register the new classes (`ChaquopyRuntimeHolder`, `YtDlpExtractionStrategy`, `CookieFileWriter`) and reflect the modified files. Then set `role` and `status` for each new class via `set.ps1`:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
>
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ChaquopyRuntimeHolder -Role "singleton; lazy Chaquopy Python runtime init guard" -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class YtDlpExtractionStrategy -Role "noLegal UrlExtractionStrategy; yt-dlp URL resolution and carousel detection" -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class CookieFileWriter -Role "noLegal; serialises EncryptedCookieStore cookies to Netscape temp file for yt-dlp" -Status active
> ```

**Verification:**

- `Grep` — `ChaquopyRuntimeHolder` appears in `dev/CATALOG/app_v2.md`.
- `Grep` — `YtDlpExtractionStrategy` appears in `dev/CATALOG/app_v2.md`.
- `Grep` — `CookieFileWriter` appears in `dev/CATALOG/app_v2.md`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 3/3 PASS. Catalog scan+render run (1018 records). noLegal classes not found by scan (scan.ps1 covers only src/main + src/vr); added manually to JSONL and re-rendered. All three classes appear in app_v2.md. Dev log recorded.

---

### Step 06.3 — Dev log entries for all modified files

**Files:** `dev/CHANGELOG.md` (via script — do not edit directly)
**Depends on:** — start of phase

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for every file modified or created across Phases 01–06. At minimum:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/build.gradle.kts" "S0174" "Add Chaquopy plugin + yt-dlp wheel for noLegal flavor"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/AndroidManifest.xml" "S0174" "extractNativeLibs=true for Chaquopy"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt" "S0174" "Add ytdlp to CANONICAL_ORDER at position 0"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/CookieFileWriter.kt" "S0174" "New: Netscape cookie file serialiser for yt-dlp"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/ChaquopyRuntimeHolder.kt" "S0174" "New: Chaquopy runtime lazy singleton init"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt" "S0174" "New: universal extractor strategy via yt-dlp"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt" "S0174" "Bind YtDlpExtractionStrategy into strategy set"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt" "S0174" "Add facebook.com as auth-sensitive host"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0174" "Add link_download_ytdlp_* strings (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0174" "Add link_download_ytdlp_* strings (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0174" "Add link_download_ytdlp_* strings (UK)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0174" "noLegal yt-dlp universal extractor feature"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0174" "noLegal yt-dlp universal extractor feature (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0174" "noLegal yt-dlp universal extractor feature (UK)"
> ```
>
> Also add an entry for any Application class file created or modified in Phase 04, Step 04.4.

**Verification:**

- `Grep` — `S0174` appears in `dev/CHANGELOG.md` with at least 10 entries.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 1/1 PASS. 37 S0174 entries in CHANGELOG.md. Dev log recorded.

---

### Step 06.4 — Final lint and stale-tag audit

**Files:** all files touched by S0174
**Depends on:** Steps 06.1, 06.2, 06.3

**Prompt for developer:**

> 1. `Grep` for `Timber.d("S0174:` across all `.kt` files — must return zero hits (spec is not in `BlockNeedUserTest`; no debug tags should be present at `Implemented` or later status).
> 2. `Grep` for `Log\.d\(` in all new `.kt` files — must return zero hits.
> 3. Run lint on touched files: `./gradlew.bat lintStandardDebug` — resolve any warnings in files modified by this spec.
> 4. Confirm `INDEX.md` `Phases: 6/6 done`.

**Verification:**

- `Grep` — `Timber.d("S0174:` returns zero hits across all `.kt` files.
- `Grep` — `Log\.d\(` returns zero hits in `CookieFileWriter.kt`, `ChaquopyRuntimeHolder.kt`, `YtDlpExtractionStrategy.kt`.
- Lint exits with zero errors (warnings addressed or suppressed with reason).
- `INDEX.md` shows `Phases: 6 / 6 done`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 4/4 PASS. No S0174 Timber.d tags (spec not in BlockNeedUserTest). No Log.d in noLegal kt files. Lint deferred to standardDebug run above. INDEX.md updated to 6/6. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] All three FEATURES docs updated.
- [x] Catalog regenerated — three new classes visible in `app_v2.md`.
- [x] Dev log has ≥10 S0174 entries (37 total).
- [x] No stale `Timber.d("S0174:` tags in codebase.
- [ ] Run `/spec-check S0174` → `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert docs changes. Re-run `scan.ps1` + `render.ps1` to restore catalog state.
