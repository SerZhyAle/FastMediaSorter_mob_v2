# Phase 02 - Download from t.me public links

**Strategic spec:** [`../S0303_telegram-integration.md`](../S0303_telegram-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

> **Research resolved 2026-05-30** (see INDEX §6.1/§6.2). Two corrections vs the original authoring:
> - Fallback after the lightweight path is `"html"`/`"dynamic"`, NOT `"ytdlp"` (ytdlp's probe returns NotApplicable for `t.me`).
> - The `tgme_widget_message_*` selectors / CDN hosts were not live-verified (WebFetch unavailable); they are pinned against a canned fixture in the unit test and degrade gracefully to `OpenResult.NotFound` (cascade continues) on parse miss. Verify against a real `?embed=1` page before production reliance.

---

## Objective

Add a `noLegal`-only URL extraction strategy that recognises public `t.me/<channel>/<id>` posts and yields their attachment(s) into the existing auto-download pipeline, registered in the canonical strategy order and bound through the existing `noLegal` Hilt multibinding. Falls through to the existing universal extractor when the lightweight path does not apply. No code in `standard`.

---

## Prerequisites

- [ ] Phase 01 may be done or not - no dependency.
- [ ] Strategic §6.1 and §6.2 research items Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/TelegramExtractionStrategy.kt` | New | ≤ 300 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt` | Modified | ≤ 60 |

> Flavor isolation: the strategy and its Hilt binding live under `src/noLegal/java/`. The only `src/main` change is adding the strategy `id` to the shared `CANONICAL_ORDER` list - a neutral ordering token, not a `BuildConfig` gate. The `standard` APK contains zero Telegram-download code.

---

## Steps

### Step 02.1 - Add canonical ordering token for the Telegram strategy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the `"telegram"` id to `CANONICAL_ORDER` ahead of the universal extractor (`"ytdlp"`) so the lightweight native path is preferred and yt-dlp remains the fallback. Adding the token in shared code is harmless on flavors that bind no `"telegram"` strategy.

**Verification:**

- `Grep` - `"telegram"` present in the `CANONICAL_ORDER` declaration line.
- `Grep` - `"telegram"` appears before `"ytdlp"` in that list.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification 2/2 PASS. expected `"telegram"` in list:1 | actual:1; telegram before ytdlp | actual: `…"dailymotion","telegram","ytdlp"…`. Files: domain/usecase/link/LinkExtractionRegistry.kt (+2 LOC comment, list extended). Dev log recorded.

---

### Step 02.2 - Implement the public t.me extraction strategy

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/TelegramExtractionStrategy.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Implement a `UrlExtractionStrategy` with `id = "telegram"`. `probe` returns `Applicable` for supported public `t.me` post URLs (per the §6.2-resolved shape set) and `NotApplicable` otherwise. `open` resolves the post's attachment URL(s) via the §6.1-resolved public path; a single attachment returns `OpenResult.Stream`, a multi-attachment post returns `OpenResult.Batch`. Unsupported/private forms return `OpenResult.NotFound` with a clear reason. Report transfer progress through the supplied callback. `Timber` only.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/TelegramExtractionStrategy.kt` exists.
- `Grep` - `class TelegramExtractionStrategy` matches exactly once.
- `Grep` - `override val id` with value `"telegram"` present.
- `Grep` - `OpenResult.Batch` referenced (multi-attachment path).
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification 5/5 PASS. expected `class TelegramExtractionStrategy`:1 | actual:1; `override val id`="telegram" | actual:1; `OpenResult.Batch` | actual:2; `Log.d(` 0 | actual:0; Glob exists | actual: present. probe=Applicable for t.me/telegram.me host; open() classifies post (canonicalises `/s/`), parses `tgme_widget_message_*` photo/video via Jsoup, single→direct.open, album→Batch, reject forms→specific NotFound. Files: noLegal/.../TelegramExtractionStrategy.kt (+~165 LOC, new). Dev log recorded.

---

### Step 02.3 - Bind the Telegram strategy in the noLegal Hilt module

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add an `@Binds @IntoSet` method binding `TelegramExtractionStrategy` as a `UrlExtractionStrategy`, alongside the existing native extractors.

**Verification:**

- `Grep` - `TelegramExtractionStrategy` imported and bound in the module.
- `Grep` - `@IntoSet` count increased by one versus the prior binding set.

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification 2/2 PASS. expected `TelegramExtractionStrategy` refs>=2 | actual:2 (import+bind); `@IntoSet` +1 | actual: 6→7. Files: noLegal/di/NoLegalLinkDownloadModule.kt (+4 LOC). Dev log recorded.

---

### Step 02.4 - Verify the strategy participates in the cascade

**Files:** (test) `app_v2/src/testNoLegal/java/com/sza/fastmediasorter/data/link/nolegal/TelegramExtractionStrategyTest.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a unit test asserting `probe` returns `Applicable` for a representative public `t.me` post URL and `NotApplicable` for an unsupported/private form. Mock network access - no live calls.

**Verification:**

- `Glob` - the test file exists.
- `/build` - `assembleNoLegalDebug` assembles.
- Test class passes (per-class XML report shows the assertions green).

**Status:** `[x]` done

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Glob test file exists | actual: present; assembleNoLegalDebug | actual: BUILD SUCCESSFUL (v2.60.5301.539); per-class XML (TEST-…TelegramExtractionStrategyTest.xml) | actual: tests=11 failures=0 errors=0 skipped=0. Covers probe (t.me/telegram.me Applicable, foreign NotApplicable), reject reasons (private/invite/no-post-id), single-photo→direct.open, album→Batch, no-media→NotFound, /s/ canonicalisation. Files: testNoLegal/.../TelegramExtractionStrategyTest.kt (+~150 LOC, new). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `noLegalDebug` BUILD SUCCESSFUL (v2.60.5301.539).
- [x] `Grep` for `TODO(phase-02)` returns zero hits (actual: 0).
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - authoritative regen batched into Phase 04 Step 04.3.

---

## Handoff Notes to Next Phase

The new strategy is `noLegal`-only and must be flagged with `set.ps1 -NoFlavors "standard,lite,photos,legacy"` (vr inherits noLegal? no - vr does not include it) in Phase 04. Confirm the exact non-flavor list against the flavor inclusion hierarchy before recording.

---

## Rollback Plan

Revert phase commit(s) - removing the `"telegram"` token, the strategy file, and its binding leaves the cascade unchanged. No persistent state touched.
