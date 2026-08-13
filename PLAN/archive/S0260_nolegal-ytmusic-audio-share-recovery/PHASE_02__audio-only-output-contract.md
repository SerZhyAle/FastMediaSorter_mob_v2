# Phase 02 - Audio-Only Output Contract (YTMusic negative-criterion guard)

**Strategic spec:** [`../S0260_nolegal-ytmusic-audio-share-recovery.md`](../S0260_nolegal-ytmusic-audio-share-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** Phase 01 + INDEX Pre-Implementation Blockers (Q3 owner decision must be resolved)
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Enforce strategic §11 acceptance criterion #2 (no JPEG / thumbnail / preview / non-audio artifact ever lands in Downloads for a `music.youtube.com` share) as a last-line-of-defense MIME contract that runs independently of which root cause Phase 01 evidence reveals. The guard variant (strict vs. fallback-permitting) is wired according to the Q3 owner decision recorded in `/spec-update S0260`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Q3 owner decision recorded - INDEX shows one of: `Q3=audio-only-or-fail` OR `Q3=audio-only-with-explicit-fallback`. Without this value the wiring variant is undefined and the phase cannot start.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/link/MediaQualityPreference.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 650 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/YtMusicAudioOnlyContract.kt` | New | ≤ 120 |

> `YtMusicAudioOnlyContract.kt` lives under `domain/usecase/link/` because the guard is host-aware on the canonical URL (not flavor-specific) - the same guard applies in any future flavor that ships YTMusic share support. The noLegal-specific extraction strategies do not need flavor-source-set duplication.

---

## Steps

### Step 02.1 - Create `YtMusicAudioOnlyContract` guard class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/YtMusicAudioOnlyContract.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a new `@Singleton` class `YtMusicAudioOnlyContract` with a single function `fun validate(originalUrl: String, canonicalAudioOnly: Boolean, resultMime: String?, resultFileName: String?): ValidationOutcome`. The class is `@Inject`-able with no dependencies. `ValidationOutcome` is a sealed interface with two variants: `Accept` (no guard violation) and `Reject(reasonCode: String, fallbackAllowed: Boolean)`. Logic: if `canonicalAudioOnly == false` AND originalUrl host is not `music.youtube.com` → return `Accept` (this guard only applies to YTMusic-origin shares). Otherwise inspect `resultMime` and `resultFileName` extension - if MIME starts with `audio/` OR extension is one of `mp3|m4a|aac|opus|ogg|wav|flac` → `Accept`. If MIME starts with `image/` OR extension is `jpg|jpeg|png|gif|webp` → `Reject("ytmusic_thumbnail_artifact", fallbackAllowed = false)`. Any other case → `Reject("ytmusic_non_audio_artifact", fallbackAllowed = ${fallbackAllowed_per_Q3})`. The `fallbackAllowed` value is a constant defined per the Q3 decision: `false` when `Q3=audio-only-or-fail`, `true` when `Q3=audio-only-with-explicit-fallback`. Add KDoc citing S0260 strategic §11.2.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/YtMusicAudioOnlyContract.kt` exists.
- `Grep -n 'class YtMusicAudioOnlyContract'` returns exactly one hit on the declaration line.
- `Grep -n 'ytmusic_thumbnail_artifact'` returns exactly one hit (used as reason code, not as a string resource).
- `Grep -n 'fallbackAllowed'` returns at least two hits (data class field + per-Q3 constant).
- `assembleNoLegalDebug` and `assembleStandardDebug` both compile - the class is in main source set so both flavors must build.

**Status:** `[ ]` not done

---

### Step 02.2 - Wire guard into `LinkAutoDownloadCoordinator.handle` post-extraction

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `YtMusicAudioOnlyContract` via constructor. In `handle()` (around line 133-138) after `handleUrl()` returns and BEFORE `markLastUsed`, intercept the result when it is `Result.Saved` or `Result.FellBackToDownloads`. Extract the final MIME and file name from the result. Call `contract.validate(originalUrl = url, canonicalAudioOnly = canonical.audioOnly, resultMime = ..., resultFileName = ...)`. On `Accept` - pass through unchanged. On `Reject(reason, fallbackAllowed = false)` - delete the saved file and return `Result.Failed.Other(IllegalStateException(reason))`. On `Reject(reason, fallbackAllowed = true)` - log `Timber.w("S0260: contract fallback permitted reason=%s", reason)` and pass through (allowed deviation). All paths emit `Timber.i("S0260: contract outcome=%s reason=%s", outcome::class.simpleName, reasonOrNull)`. Update the constructor `@Inject` parameter list and the corresponding test (`LinkAutoDownloadCoordinatorTest`) to compile - the test does not need to assert guard behavior yet, that lands in Phase 04.

**Verification:**

- `Grep -n 'private val contract: YtMusicAudioOnlyContract' app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` returns exactly one hit.
- `Grep -n 'contract\.validate(' ...LinkAutoDownloadCoordinator.kt` returns exactly one hit.
- `Grep -n 'S0260: contract outcome'` returns exactly one hit.
- `assembleNoLegalDebug` and `assembleStandardDebug` both compile.
- `testStandardDebugUnitTest --tests LinkAutoDownloadCoordinatorTest` compiles (does not need new assertions).

**Status:** `[ ]` not done

---

### Step 02.3 - Surface guard rejection to user via existing communication channel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add one new string key `link_download_error_ytmusic_audio_only` in all three locale strings.xml files. Tone per `docs/COMMUNICATION_POLICY.md` §2 (error formula) and §6 checklist: short, factual, names the specific failure (audio download from YouTube Music could not produce an audio file), no jargon, no blame. EN draft: "Couldn't save the audio track from YouTube Music. The link did not return a playable audio file." RU draft: "Не получилось сохранить аудиодорожку из YouTube Music. Ссылка не вернула воспроизводимый аудиофайл." UK draft: "Не вдалося зберегти аудіодоріжку з YouTube Music. Посилання не повернуло відтворюваний аудіофайл." Author style rules (CLAUDE.md): use `..` not `...` (none here), `ё` where applicable (none in these strings). In the `Reject(fallbackAllowed=false)` branch of `handle()`, attach this string key into the `Result.Failed.Other` so the callbacks layer can show the existing error toast/dialog.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "link_download_error_ytmusic_audio_only"` exits 0.
- `Grep -n 'link_download_error_ytmusic_audio_only' app_v2/src/main/java` returns at least 1 hit (the constructor of the `IllegalStateException` or the `Result.Failed` variant referencing it).
- The three `strings.xml` files each contain exactly one occurrence of the key.
- Strings pass COMMUNICATION_POLICY §6 checklist (sentence-case where applicable, no exclamation marks, no second-person imperative blaming user).
- `assembleNoLegalDebug` and `assembleStandardDebug` both compile.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Both `assembleNoLegalDebug` and `assembleStandardDebug` compile - run `.\a.ps1 dq` and `.\a.ps1 sq` (assuming the `sq` shortcut exists; otherwise standard equivalent).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep -nE 'S0260: contract' app_v2/src/main` returns at least 2 hits (validate call + outcome trace).
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] String locale audit passes - `check_strings_localized.ps1 -KeyPrefix "link_download_error_ytmusic_audio_only"` exits 0.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.
- [ ] New class `YtMusicAudioOnlyContract` filled in via `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class YtMusicAudioOnlyContract -Role guard -Status active`.

---

## Handoff Notes to Next Phase

Phase 03 picks up the targeted extraction fix that satisfies criterion §11.1 (a playable audio file actually appears). Phase 02's guard is a floor: it cannot create an audio file out of nothing, only prevent non-audio artifacts from being saved.

---

## Rollback Plan

Revert the Phase 02 commit. The guard is purely additive - removal restores the prior behavior where any artifact yt-dlp returns is saved. No data migration; new string keys can stay or be reverted as a separate string-cleanup task.
