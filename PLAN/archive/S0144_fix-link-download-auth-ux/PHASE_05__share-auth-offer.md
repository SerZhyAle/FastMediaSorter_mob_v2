# Phase 05 — Shared social link → offer to add authorization

**Strategic spec:** [`../S0144_fix-link-download-auth-ux.md`](../S0144_fix-link-download-auth-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 04
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

When the user shares a link from a known social resource and no auth session exists for that host, show a one-shot offer dialog before starting the download: "Add" opens the WebView-auth dialog at the resource's login URL and then resumes the download; "Skip" remembers the dismissal per host and continues the download as today.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`KnownAuthResources` exists).
- [ ] Phase 04 ✅ Done (WebView-auth dialog redirect-safe).
- [ ] `AuthSessionRepository.hasSession(host)` available (already present).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AuthOfferDismissalStore.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 420 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +4 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +4 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +4 |

---

## Steps

### Step 05.1 — Add `AuthOfferDismissalStore`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AuthOfferDismissalStore.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `@Singleton class AuthOfferDismissalStore @Inject constructor(@ApplicationContext private val context: Context)` backed by a plain (non-encrypted) `SharedPreferences` file (e.g. `link_download_auth_offer`). Expose `fun isDismissed(host: String): Boolean` and `fun markDismissed(host: String)` storing the lowercased host in a `StringSet`. No new Hilt `@Module` — constructor injection only. Add a one-line KDoc referencing S0144. Timber only if any logging is added.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AuthOfferDismissalStore.kt` exists.
- `Grep` — `class AuthOfferDismissalStore` matches exactly once.
- `Grep` — `fun isDismissed(` and `fun markDismissed(` present.
- `Grep` — `@Singleton` present.
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 5/5 PASS (class 1, isDismissed 1, markDismissed 1, @Singleton 1, Log.d 0). Files: data/link/auth/AuthOfferDismissalStore.kt (new). Dev log recorded.

---

### Step 05.2 — Emit a fragment result from `WebViewAuthDialogFragment` on dismiss

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `const val RESULT_KEY = "s0144_webview_auth_result"` and `const val RESULT_HOST = "host"`, `const val RESULT_SAVED = "saved"` to the companion. When the dialog finishes — both in `harvestAndDismiss()` (saved = true when a session was written, false otherwise) and in the cancel-button path — call `parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(RESULT_HOST to targetHost, RESULT_SAVED to <bool>))` immediately before `dismissAllowingStateLoss()`. Do not change the redirect-handling code from Phase 04. Keep Timber only.

**Verification:**

- `Grep` — `RESULT_KEY` present in the file.
- `Grep` — `setFragmentResult(` present in the file.
- `Grep` — `shouldOverrideUrlLoading` still present in the file (Phase 04 not regressed).
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS (RESULT_KEY present, setFragmentResult present, shouldOverrideUrlLoading still present, Log.d 0). Files: WebViewAuthDialogFragment.kt. Dev log recorded.

---

### Step 05.3 — Add offer-dialog strings (trilingual)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add four keys to all three `strings.xml` files. Tone: friendly, not alarming — follow `docs/COMMUNICATION_POLICY.md` §2 (confirmation/prompt formula) and the §6 checklist.
> - `auth_offer_dialog_title` — EN `Sign in to %1$s?`, RU `Войти в %1$s?`, UK `Увійти в %1$s?` (`%1$s` = resource display name).
> - `auth_offer_dialog_message` — EN `Saving an authorization lets the app download content from %1$s that needs sign-in.`, RU `Сохранённая авторизация позволит приложению скачивать с %1$s контент, для которого нужен вход.`, UK `Збережена авторизація дозволить застосунку завантажувати з %1$s контент, для якого потрібен вхід.`
> - `auth_offer_dialog_add` — EN `Add authorization`, RU `Добавить авторизацию`, UK `Додати авторизацію`.
> - `auth_offer_dialog_skip` — EN `Not now`, RU `Не сейчас`, UK `Не зараз`.
> Use `..` not `...`; keep `ё`/`Ё` in RU.

**Verification:**

- `Grep` — all four `auth_offer_dialog_*` keys present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — all four present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — all four present in `app_v2/src/main/res/values-uk/strings.xml`.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "auth_offer_dialog_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification PASS; `check_strings_localized.ps1 -KeyPrefix "auth_offer_dialog_"` exit 0 (4 keys OK in EN/RU/UK). Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. Dev log recorded.

---

### Step 05.4 — Offer flow in `ReceiveShareActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 05.1, Step 05.2, Step 05.3

**Prompt for developer:**

> Add `@Inject lateinit var authSessionRepository: AuthSessionRepository` and `@Inject lateinit var authOfferDismissalStore: AuthOfferDismissalStore`. In `processIntent`, where a detected URL is currently routed straight to `processLinkAutoDownload(url)` (the `url != null && settings.linkAutoDownloadEnabled` branch), first resolve `val resource = KnownAuthResources.matchHost(Uri.parse(url).host)`. If `resource != null`, `!authSessionRepository.hasSession(resource.host)`, and `!authOfferDismissalStore.isDismissed(resource.host)` → show a `MaterialAlertDialogBuilder` with title `auth_offer_dialog_title` (formatted with `resource.displayName`), message `auth_offer_dialog_message`, positive button `auth_offer_dialog_add`, negative button `auth_offer_dialog_skip`, non-cancelable:
> - Positive: register `supportFragmentManager.setFragmentResultListener(WebViewAuthDialogFragment.RESULT_KEY, this)` with a listener that calls `processLinkAutoDownload(url)` once (then clears itself), then `WebViewAuthDialogFragment.newInstance(resource.loginUrl).show(supportFragmentManager, "s0144_webview_auth_offer")`.
> - Negative (and dialog dismissed without choice): `authOfferDismissalStore.markDismissed(resource.host)` then `processLinkAutoDownload(url)`.
> Otherwise (no resource match / session exists / already dismissed) keep today's behaviour — go straight to `processLinkAutoDownload(url)`. Add `Timber.d("S0144: share-auth offer evaluated")` where the resource match is computed. Do not touch the file/stream path, the existing `AuthRequired` handling in `LinkAutoDownloadResultPresenter`, or `processLinkAutoDownload` itself. Keep the Activity thin — the new logic is dispatch + a dialog, no download/parsing logic. Timber only.

**Verification:**

- `Grep` — `authSessionRepository` and `authOfferDismissalStore` declared in `ReceiveShareActivity.kt`.
- `Grep` — `KnownAuthResources.matchHost(` present in `ReceiveShareActivity.kt`.
- `Grep` — `setFragmentResultListener(WebViewAuthDialogFragment.RESULT_KEY` present in `ReceiveShareActivity.kt`.
- `Grep` — `auth_offer_dialog_title` referenced in `ReceiveShareActivity.kt`.
- `Grep` — `Timber.d("S0144:` present in `ReceiveShareActivity.kt`.
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 6/6 PASS (authSessionRepository 2, authOfferDismissalStore 3, KnownAuthResources.matchHost 1, setFragmentResultListener 1, auth_offer_dialog_title 1, S0144 tag 1, Log.d 0). Files: ReceiveShareActivity.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` → BUILD SUCCESSFUL (2026-05-10).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated; `AuthOfferDismissalStore` present, role/status set.

---

## Handoff Notes to Next Phase

All four user-facing changes are now in place. Phase 06 documents them, regenerates the catalog, and runs the locale audit.

---

## Rollback Plan

Revert phase commit(s). `AuthOfferDismissalStore` writes to its own SharedPreferences file — orphaned data is harmless if the feature is reverted; no schema migration involved.
