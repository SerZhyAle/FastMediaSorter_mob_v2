# Phase 05 — Auth Resources

**Strategic spec:** [`../S0174_nolegal-ytdlp-universal-extractor.md`](../S0174_nolegal-ytdlp-universal-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Add `facebook.com` to `KnownAuthResources` so that when yt-dlp cannot extract a Facebook URL without cookies, the coordinator shows the auth-required flow instead of `NoMediaFound`. Add trilingual strings for yt-dlp progress and error states.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] `KnownAuthResources.kt` is readable.
- [ ] `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` exist.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt` | Modified | ≤ 55 (currently 52 LOC) |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +4 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +4 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +4 lines |

---

## Steps

### Step 05.1 — Add facebook.com to KnownAuthResources

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `KnownAuthResources.kt`, add a `KnownAuthResource` entry for Facebook. Insert it after the existing list entries, before the closing `)` of `listOf(...)`:
>
> ```kotlin
> KnownAuthResource(
>     displayName = "Facebook",
>     host = "facebook.com",
>     loginUrl = "https://www.facebook.com/login",
>     // Facebook requires cookies for most public videos since 2023 —
>     // treat SocialPreviewOnly as an auth signal to prompt WebView login.
>     previewOnlyMeansLogin = true,
> ),
> ```
>
> No other changes to the file. Check `docs/COMMUNICATION_POLICY.md` §2 to confirm "Facebook" follows the same display-name pattern as existing entries. The `loginUrl` is user-visible only in the WebView dialog title — keep it as a plain URL.

**Verification:**

- `Grep` — `"Facebook"` appears in `KnownAuthResources.kt`.
- `Grep` — `"facebook.com"` appears in `KnownAuthResources.kt`.
- `Grep` — `previewOnlyMeansLogin = true` on the Facebook entry (not just any entry — verify by context).
- `Grep` — `displayName = "Facebook"` present.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 4/4 PASS. Files: KnownAuthResources.kt (modified). Dev log recorded.

---

### Step 05.2 — Add trilingual strings for yt-dlp extraction states

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase (independent of Step 05.1)

**Prompt for developer:**

> Add four string keys for yt-dlp progress and error messages. Follow `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist: friendly, action-oriented, no blame). Strings must pass the tone checklist before commit.
>
> **`values/strings.xml` (English):**
> ```xml
> <string name="link_download_ytdlp_extracting">Extracting media link…</string>
> <string name="link_download_ytdlp_downloading">Downloading via universal extractor…</string>
> <string name="link_download_ytdlp_error_extract">Could not extract media from this link.</string>
> <string name="link_download_ytdlp_error_runtime">Universal extractor is not available.</string>
> ```
>
> **`values-ru/strings.xml` (Russian):**
> ```xml
> <string name="link_download_ytdlp_extracting">Извлечение ссылки на медиа…</string>
> <string name="link_download_ytdlp_downloading">Загрузка через универсальный экстрактор…</string>
> <string name="link_download_ytdlp_error_extract">Не удалось извлечь медиа из этой ссылки.</string>
> <string name="link_download_ytdlp_error_runtime">Универсальный экстрактор недоступен.</string>
> ```
>
> **`values-uk/strings.xml` (Ukrainian):**
> ```xml
> <string name="link_download_ytdlp_extracting">Вилучення посилання на медіа…</string>
> <string name="link_download_ytdlp_downloading">Завантаження через універсальний екстрактор…</string>
> <string name="link_download_ytdlp_error_extract">Не вдалося вилучити медіа з цього посилання.</string>
> <string name="link_download_ytdlp_error_runtime">Універсальний екстрактор недоступний.</string>
> ```
>
> **Tone checklist (COMMUNICATION_POLICY §6):**
> - [ ] Strings are friendly and action-oriented — not blame-casting.
> - [ ] No technical jargon exposed to user ("yt-dlp" not used in user-visible strings).
> - [ ] Error strings state the outcome, not the cause.
> - [ ] All three locales have identical key sets.

**Verification:**

- `Grep` — `link_download_ytdlp_extracting` present in all three `strings.xml` files.
- `Grep` — `link_download_ytdlp_downloading` present in all three.
- `Grep` — `link_download_ytdlp_error_extract` present in all three.
- `Grep` — `link_download_ytdlp_error_runtime` present in all three.
- Strings pass COMMUNICATION_POLICY §6 checklist (verified by developer before committing).
- Run: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "link_download_ytdlp"` → exit code 0.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 6/6 PASS. Locale audit: exit 0, 4 keys in EN/RU/UK. Also moved CookieFileWriterTest from src/test/ to src/testNoLegal/ (was failing standard test compilation). Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. Dev log recorded.

---

### Step 05.3 — Verify KnownAuthResourcesTest still passes

**Files:** (no file change — test run)
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `./gradlew.bat testStandardDebugUnitTest --tests "*.KnownAuthResourcesTest"` (or equivalent). The existing tests must pass with the new `facebook.com` entry. If any test asserts `all.size == N` (specific count), update the assertion to reflect the new size.

**Verification:**

- Unit test run exits with code 0.
- `Grep` — `KnownAuthResourcesTest` does not contain a hardcoded size assertion that would fail after adding one entry (or if it does, the count is updated).

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 2/2 PASS. Tests pass (BUILD SUCCESSFUL). No hardcoded size assertion in KnownAuthResourcesTest. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `standardDebug` build passes — BUILD SUCCESSFUL (verified in Phase 04 criteria).
- [x] String locale audit: exit code 0 (2026-05-12).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 05 establishes: `facebook.com` in auth-sensitive host registry; trilingual strings for yt-dlp extraction states. Phase 06 finalises docs, catalog, and FEATURES update.

---

## Rollback Plan

Revert `KnownAuthResources.kt` to remove the Facebook entry. Remove the four `link_download_ytdlp_*` keys from all three `strings.xml` files. No data migration changed.
