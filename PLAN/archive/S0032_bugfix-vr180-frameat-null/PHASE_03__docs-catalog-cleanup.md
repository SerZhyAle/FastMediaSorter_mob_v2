# Phase 03 — Docs, Catalog & Cleanup

**Strategic spec:** [`../S0032_bugfix-vr180-frameat-null.md`](../S0032_bugfix-vr180-frameat-null.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Synchronize the trilingual feature documentation, regenerate the code catalog for `app_v2/`, and add the consolidated dev-log entries for every file touched in Phases 01–02.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Phase 02 ✅ Done.
- [ ] All implementation files compile and the manual probes from Phase 02 Done Criteria have passed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | — |
| `dev/CATALOG/app_v2.md` | Regenerated | — |
| `dev/CHANGELOG.md` | Appended via `add_to_dev_log.ps1` | — |

---

## Steps

### Step 03.1 — Trilingual feature docs update (Player resilience bullet)

**Files:**
- `docs/FEATURES.md`
- `docs/FEATURES_RU.md`
- `docs/FEATURES_UK.md`

**Depends on:** start of phase

**Prompt for developer:**

> Locate the Player section in each of the three feature docs (search for the heading that lists video-playback features — most likely `Player` or `Video player`). Append a single bullet to each. Use these exact wordings:
>
> | Locale | Bullet |
> |--------|--------|
> | EN | `- Resilient poster-frame extraction: VR180 / 7K videos and low-native-heap devices fall back to cached thumbnail or a localized "Thumbnail unavailable" placeholder; never an empty preview.` |
> | RU | `- Устойчивое извлечение превью: VR180 / 7K и устройства с малым native heap получают кадр из кэша или локализованную заглушку «Превью недоступно» — пустого превью больше не бывает.` |
> | UK | `- Стійке витягування прев'ю: VR180 / 7K та пристрої з малим native heap отримують кадр із кешу або локалізовану заглушку «Прев'ю недоступне» — порожнього прев'ю більше не буде.` |
>
> Russian and Ukrainian must respect the project author style: `..` (two dots, never three) for ellipsis. The bullets above contain no ellipsis, so no transformation is needed — but if existing surrounding bullets use `..`, do **not** convert them to `...`. Russian must use `ё` where grammatically correct — verify on save.

**Verification:**

- `Grep` — `Resilient poster-frame extraction` matches once in `docs/FEATURES.md`.
- `Grep` — `Устойчивое извлечение превью` matches once in `docs/FEATURES_RU.md`.
- `Grep` — `Стійке витягування прев'ю` matches once in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 3/3 PASS. Files: docs/FEATURES.md, docs/FEATURES_RU.md, docs/FEATURES_UK.md (+1 bullet each, appended to Player section). Dev log recorded.

---

### Step 03.2 — Catalog regeneration for `app_v2`

**Files:**
- `dev/CATALOG/app_v2.jsonl`
- `dev/CATALOG/app_v2.md`

**Depends on:** Step 03.1

**Prompt for developer:**

> Run, in order, from the project root:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> The scan picks up the new `VideoPosterExtractor.kt` automatically; manually fill its `role` (`utility — poster-frame extraction with fallback chain`) and `status` (`active`) via:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class VideoPosterExtractor -Role "utility — poster-frame extraction with fallback chain" -Status active
> ```
>
> Re-run `render.ps1` after `set.ps1`. Commit `app_v2.jsonl` and `app_v2.md` together with the implementation. Do not edit the JSONL by hand.

**Verification:**

- `Grep` in `dev/CATALOG/app_v2.jsonl` — `"VideoPosterExtractor"` matches at least once.
- `Grep` in `dev/CATALOG/app_v2.md` — `VideoPosterExtractor` referenced.
- `git status` — `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` show as Modified.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 3/3 PASS. Catalog scanned (819 records), `VideoPosterExtractor` annotated with role + status=new (set.ps1 ValidateSet uses `new|tested|legacy|todo|unknown` — `new` is the correct value for a freshly-added utility), re-rendered. Dev log recorded.

---

### Step 03.3 — Dev log entries for every modified file

**Files:** `dev/CHANGELOG.md` (appended via script — never edited directly)
**Depends on:** Step 03.2

**Prompt for developer:**

> For every file modified or created in Phases 01 and 02, append a dev-log line via the script. One invocation per file. Do not batch into a single line. Suggested target/description per file (adjust the description if scope differs):
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt" "S0032" "New utility — poster-frame extraction with preventive guards and fallback chain"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt" "S0032" "Wire VideoPosterExtractor into onRenderedFirstFrame; remove inline getFrameAtTime"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt" "S0032" "triggerVideoBackground — placeholder-aware contentDescription"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt" "S0032" "Pass placeholder flag to triggerVideoBackground"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt" "S0032" "Widen CACHED_THUMBNAIL_SIZE visibility for poster-extractor reuse"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0032" "Add poster_thumbnail_unavailable string"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0032" "Add poster_thumbnail_unavailable string (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0032" "Add poster_thumbnail_unavailable string (UK)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0032" "Player — resilient poster-frame extraction bullet"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0032" "Player — resilient poster-frame extraction bullet (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0032" "Player — resilient poster-frame extraction bullet (UK)"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0032" "Catalog regen for VideoPosterExtractor"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.md" "S0032" "Catalog regen for VideoPosterExtractor"
> ```
>
> Adjust the file list to match the actual git diff — drop any file not actually touched.

**Verification:**

- `Grep` in `dev/CHANGELOG.md` — `S0032` appears in at least 10 distinct entries.
- `Grep` in `dev/CHANGELOG.md` — `VideoPosterExtractor.kt` appears at least once.
- `Grep` in `dev/CHANGELOG.md` — `poster_thumbnail_unavailable` appears at least once.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 3/3 PASS. Dev log entries were appended incrementally as each step completed (per the spec-dev rule "Dev log per file, per step"); 25 distinct `S0032` entries are now in `dev/CHANGELOG.md`. No additional batch invocation needed.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] All three `docs/FEATURES*.md` files include the resilience bullet under Player.
- [x] `dev/CATALOG/app_v2.jsonl` includes `VideoPosterExtractor`.
- [x] `dev/CHANGELOG.md` has one entry per modified file (no batching).
- [x] `git status` shows no unstaged tactical-spec edits beyond the phase status flips.
- [x] `/spec-check S0032` is ready to run (no further code/doc work pending).

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md](INDEX.md) Completion Gate.

---

## Rollback Plan

Revert the phase commit; documentation, catalog, and changelog entries are non-load-bearing for runtime behaviour.
