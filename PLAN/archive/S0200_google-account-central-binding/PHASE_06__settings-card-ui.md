# Phase 06 — Settings "Google Account" Card + Resource Indicator UI

**Strategic spec:** [`../S0200_google-account-central-binding.md`](../S0200_google-account-central-binding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (with 06.1 CctRefusalDialog refactor and 06.7 SettingsSearchIndex entry deferred)
**Depends on:** Phase 02, Phase 03, Phase 05
**Blocks:** none (last functional phase before cleanup)
**Steps done:** 5 / 7 (06.1 dialog refactor and 06.7 search index deferred — non-blocking polish)
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Surface the new identity domain in the UI. Add a "Google Account" `MaterialCardView` to `fragment_settings_general.xml` (+ landscape parity) showing email / display-name / avatar / sign-in / sign-out / diagnostics. Render a "needs sign-in" indicator on Drive resources in `ResourceAdapter`. All new strings in EN/RU/UK pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Phase 03 ✅ Done (`GoogleDomainBrowserLauncher` available for CCT diagnostics).
- [ ] Phase 05 ✅ Done (`ResourceEntity.needsSignIn` column populated by wipe).
- [ ] `/ui-clarify` gate ✅ resolved per INDEX.md (card placement above or below "App Data & Backups"; whether Drive sign-in buttons inside Backups card are extracted into the new card).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout/card_google_account.xml` | New | ≤ 200 |
| `app_v2/src/main/res/drawable/ic_google_account.xml` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/GoogleAccountSettingsViewModel.kt` | New | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GoogleAccountSettingsHelper.kt` | New | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 760 |
| `app_v2/src/main/res/values/strings_s0200.xml` | Modified | ≤ 100 |
| `app_v2/src/main/res/values-ru/strings_s0200.xml` | Modified | ≤ 100 |
| `app_v2/src/main/res/values-uk/strings_s0200.xml` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/helpers/CctRefusalDialog.kt` | New | ≤ 80 |

> `ResourceAdapter` is 738 LOC. Edits add a ~20-line block (status-text + visibility branch). Projection 758 ≈ ≤ 760. No backup needed (under 500 LOC limit was the spec rule for backup; ResourceAdapter is already over but is mature code — leave-as-is per project convention).
>
> `fragment_settings_general.xml` (portrait) and `fragment_settings_general.xml` (landscape) MUST receive the same card. Layout-land parity is MANDATORY (CLAUDE.md Strict Rule 12).

---

## Steps

### Step 06.1 — Create reusable `CctRefusalDialog`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/helpers/CctRefusalDialog.kt`
**Depends on:** —

**Prompt for developer:**

> Extract the four-times-duplicated CCT refusal dialog into one helper:
>
> ```kotlin
> object CctRefusalDialog {
>     fun show(context: Context, cctChecker: CctAvailabilityChecker, onRetry: () -> Unit) {
>         MaterialAlertDialogBuilder(context)
>             .setTitle(R.string.s0200_cct_unavailable_title)
>             .setMessage(R.string.s0200_cct_unavailable_message)
>             .setPositiveButton(R.string.s0200_cct_unavailable_retry) { _, _ ->
>                 if (cctChecker.isAvailable()) onRetry()
>                 else show(context, cctChecker, onRetry) // re-show until user installs a browser or cancels
>             }
>             .setNegativeButton(R.string.cancel, null)
>             .show()
>     }
> }
> ```
>
> Update the three call sites added in Phase 03 (Step 03.5) to use this helper instead of inline copies — drop their private `showCctUnavailableDialog()` methods.

**Verification:**

- `Glob` — `CctRefusalDialog.kt` exists.
- `Grep -rn "showCctUnavailableDialog" app_v2/src/main/java/com/sza/fastmediasorter/ui/` returns zero hits.
- `Grep -rn "CctRefusalDialog.show" app_v2/src/main/java/com/sza/fastmediasorter/ui/` matches at least 3 lines.
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 06.2 — Add Phase 06 string resources (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings_s0200.xml`, `app_v2/src/main/res/values-ru/strings_s0200.xml`, `app_v2/src/main/res/values-uk/strings_s0200.xml`
**Depends on:** —

**Prompt for developer:**

> Append the new keys to each file. Apply COMMUNICATION_POLICY §6 tone checklist before committing. Single-line value rule: every string fits 360 dp without truncation; verify by previewing the card in Phase 06.4.
>
> EN additions:
>
> ```xml
> <!-- S0200 — Google Account settings card -->
> <string name="s0200_card_title">Google Account</string>
> <string name="s0200_card_state_unbound_summary">Sign in once to use Google Drive backup and add Drive folders without re-entering your account.</string>
> <string name="s0200_card_state_unbound_cta">Sign in to Google</string>
> <string name="s0200_card_state_bound_summary">Connected as %1$s.</string>
> <string name="s0200_card_state_bound_sign_out">Sign out</string>
> <string name="s0200_card_state_needs_resign_in_summary">Reconnect %1$s to keep Drive working.</string>
> <string name="s0200_card_state_needs_resign_in_cta">Reconnect</string>
> <string name="s0200_card_authenticating">Signing in..</string>
> <string name="s0200_card_diag_cct_ok">Browser detected: %1$s.</string>
> <string name="s0200_card_diag_cct_missing">No supported browser installed. Install one to use Google.</string>
> <string name="s0200_card_diag_show">Diagnostics</string>
> <string name="s0200_sign_out_confirm_title">Sign out of Google?</string>
> <string name="s0200_sign_out_confirm_message">Your Drive folders stay in the list but will need a new sign-in to load files. Folder names and settings are kept.</string>
> <string name="s0200_sign_out_confirm_confirm">Sign out</string>
> <string name="s0200_resource_needs_sign_in_label">Sign-in needed</string>
> <string name="s0200_settings_search_keywords">google account sign in primary credential manager drive</string>
> ```
>
> RU additions (apply `ё`/`Ё`, `..`):
>
> ```xml
> <!-- S0200 — карточка Google-аккаунта -->
> <string name="s0200_card_title">Google-аккаунт</string>
> <string name="s0200_card_state_unbound_summary">Войдите один раз, чтобы делать резервные копии в Google Drive и добавлять папки без повторной авторизации.</string>
> <string name="s0200_card_state_unbound_cta">Войти в Google</string>
> <string name="s0200_card_state_bound_summary">Подключён аккаунт %1$s.</string>
> <string name="s0200_card_state_bound_sign_out">Выйти</string>
> <string name="s0200_card_state_needs_resign_in_summary">Войдите снова под %1$s, чтобы Drive продолжил работать.</string>
> <string name="s0200_card_state_needs_resign_in_cta">Войти снова</string>
> <string name="s0200_card_authenticating">Вход..</string>
> <string name="s0200_card_diag_cct_ok">Найден браузер: %1$s.</string>
> <string name="s0200_card_diag_cct_missing">Нет совместимого браузера. Установите его, чтобы пользоваться Google.</string>
> <string name="s0200_card_diag_show">Диагностика</string>
> <string name="s0200_sign_out_confirm_title">Выйти из Google?</string>
> <string name="s0200_sign_out_confirm_message">Папки Drive останутся в списке, но потребуется новый вход, чтобы открыть файлы. Имена папок и настройки сохраняются.</string>
> <string name="s0200_sign_out_confirm_confirm">Выйти</string>
> <string name="s0200_resource_needs_sign_in_label">Нужен вход</string>
> <string name="s0200_settings_search_keywords">гугл аккаунт войти основной credential manager диск</string>
> ```
>
> UK additions:
>
> ```xml
> <!-- S0200 — картка Google-акаунта -->
> <string name="s0200_card_title">Google-акаунт</string>
> <string name="s0200_card_state_unbound_summary">Увійдіть один раз, щоб робити резервні копії в Google Drive і додавати папки без повторної авторизації.</string>
> <string name="s0200_card_state_unbound_cta">Увійти до Google</string>
> <string name="s0200_card_state_bound_summary">Підключено акаунт %1$s.</string>
> <string name="s0200_card_state_bound_sign_out">Вийти</string>
> <string name="s0200_card_state_needs_resign_in_summary">Увійдіть знову під %1$s, щоб Drive продовжив працювати.</string>
> <string name="s0200_card_state_needs_resign_in_cta">Увійти знову</string>
> <string name="s0200_card_authenticating">Вхід..</string>
> <string name="s0200_card_diag_cct_ok">Знайдено браузер: %1$s.</string>
> <string name="s0200_card_diag_cct_missing">Немає сумісного браузера. Встановіть його, щоб користуватися Google.</string>
> <string name="s0200_card_diag_show">Діагностика</string>
> <string name="s0200_sign_out_confirm_title">Вийти з Google?</string>
> <string name="s0200_sign_out_confirm_message">Папки Drive залишаться в списку, але знадобиться новий вхід, щоб відкрити файли. Імена папок і налаштування зберігаються.</string>
> <string name="s0200_sign_out_confirm_confirm">Вийти</string>
> <string name="s0200_resource_needs_sign_in_label">Потрібен вхід</string>
> <string name="s0200_settings_search_keywords">гугл акаунт увійти основний credential manager диск</string>
> ```

**Verification:**

- `Grep -c "<string name=\"s0200_" app_v2/src/main/res/values/strings_s0200.xml` returns the same count for EN, RU, UK.
- Locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0200_card"` exits 0. Expected: 0. Actual: paste.
- Strings pass COMMUNICATION_POLICY §6 tone checklist (manual review — apply each point explicitly).
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 06.3 — Create `card_google_account.xml` layout + icon

**Files:** `app_v2/src/main/res/layout/card_google_account.xml`, `app_v2/src/main/res/drawable/ic_google_account.xml`
**Depends on:** Step 06.2

**Prompt for developer:**

> Model after `fragment_settings_backup_restore.xml:16-80` (research-identified template). Use `MaterialCardView` root. Include:
> - 24dp Google account icon (`ic_google_account.xml`) on the left.
> - `tvAccountTitle` (the localized "Google Account").
> - `tvAccountSummary` (the state-dependent summary text).
> - `tvAccountEmail` (visible only in `Bound` state).
> - `ivAccountAvatar` (24dp ImageView for the avatar, loaded via Glide in the helper).
> - `btnAccountAction` (the state-dependent CTA button).
> - `progressAccountAuth` (indeterminate `CircularProgressIndicator`, visible only in `Authenticating`).
> - `tvDiagnosticsLine` (visible when "Diagnostics" is toggled).
> - `btnDiagnosticsToggle` (text button).
>
> Focus traversal: every actionable child must have `android:focusable="true"`, `android:clickable="true"`, `android:nextFocusDown` chained to the next card on the screen. Mouse/keyboard/D-pad support per CLAUDE.md Rule 17.
>
> Create `ic_google_account.xml` as a simple monogram VectorDrawable — NOT the Google "G" logo (trademark restriction). A circle with `?attr/colorOnSurfaceVariant` background and "GA" or person-silhouette path is acceptable. Alternative: reuse `ic_account_circle_24` from material-icons if present.

**Verification:**

- `Glob` — `card_google_account.xml`, `ic_google_account.xml` both exist.
- `Grep -n "android:id=\"@\\+id/btnAccountAction\"" app_v2/src/main/res/layout/card_google_account.xml` matches exactly once.
- `Grep -n "android:focusable=\"true\"" app_v2/src/main/res/layout/card_google_account.xml` matches at least 2 lines.
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 06.4 — Embed card in `fragment_settings_general.xml` (portrait + landscape)

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 06.3

**Prompt for developer:**

> Both files MUST be modified in the same step — CLAUDE.md Strict Rule 12. Add `<include layout="@layout/card_google_account" .. />` in the position decided by `/ui-clarify` (INDEX Pre-Impl Blocker). Default placement IF `/ui-clarify` allows: directly ABOVE the "App Data & Backups" card (Drive backup logically depends on the primary account binding).
>
> Maintain the same horizontal margins / paddings as the surrounding cards.
>
> If `/ui-clarify` mandates extracting Drive sign-in buttons OUT of the Backups card INTO the new account card, do that in this step (move `btnSignInDrive` etc. into the new card binding; delete from old binding; update `GeneralSettingsBackupHelper` to no longer touch the moved widgets).

**Verification:**

- `Grep -n "card_google_account" app_v2/src/main/res/layout/fragment_settings_general.xml` matches exactly once.
- `Grep -n "card_google_account" app_v2/src/main/res/layout-land/fragment_settings_general.xml` matches exactly once.
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 06.5 — Implement `GoogleAccountSettingsViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/GoogleAccountSettingsViewModel.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> ```kotlin
> @HiltViewModel
> class GoogleAccountSettingsViewModel @Inject constructor(
>     private val identityRepository: GoogleIdentityRepository,
>     private val cctChecker: CctAvailabilityChecker,
>     private val resourceDao: ResourceDao,
>     savedStateHandle: SavedStateHandle
> ) : ViewModel() {
>
>     data class UiState(
>         val state: PrimaryGoogleAccountState,
>         val cctPackage: String?,           // null when no browser
>         val showDiagnostics: Boolean = false
>     )
>
>     private val showDiagnostics = MutableStateFlow(false)
>
>     val uiState: StateFlow<UiState> = combine(
>         identityRepository.state,
>         showDiagnostics
>     ) { state, diag ->
>         UiState(state = state, cctPackage = cctChecker.resolveCctPackage(), showDiagnostics = diag)
>     }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(PrimaryGoogleAccountState.Unbound, null))
>
>     fun signInPrimary(activity: Activity) { viewModelScope.launch { identityRepository.signInPrimary(activity, primaryScopes()) } }
>
>     fun signOutPrimary() {
>         viewModelScope.launch {
>             identityRepository.signOutPrimary()
>             resourceDao.markAllDriveNeedsSignIn(true)
>         }
>     }
>
>     fun reconnect(activity: Activity) = signInPrimary(activity)
>
>     fun toggleDiagnostics() { showDiagnostics.value = !showDiagnostics.value }
>
>     private fun primaryScopes() =
>         setOf(GoogleScope.DRIVE, GoogleScope.DRIVE_READONLY, GoogleScope.EMAIL, GoogleScope.PROFILE, GoogleScope.OPENID)
> }
> ```
>
> When sign-out succeeds, mark every Drive resource as `needs_sign_in = true` so the resource list immediately reflects the change. When sign-in succeeds, clear `needs_sign_in` for any resource matching `credentialsId == account.email` — emit via `resourceDao.clearNeedsSignInForCredentials(account.email)`.

**Verification:**

- `Glob` — `GoogleAccountSettingsViewModel.kt` exists.
- `Grep -n "@HiltViewModel"` matches exactly once.
- `Grep -n "fun signInPrimary"` matches exactly once.
- `Grep -n "fun signOutPrimary"` matches exactly once.
- `Grep -n "markAllDriveNeedsSignIn"` matches at least once.
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 06.6 — Implement `GoogleAccountSettingsHelper` + wire fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GoogleAccountSettingsHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 06.5

**Prompt for developer:**

> Helper:
>
> ```kotlin
> class GoogleAccountSettingsHelper(
>     private val fragment: Fragment,
>     private val viewModel: GoogleAccountSettingsViewModel,
>     private val cctRefusal: CctAvailabilityChecker
> ) {
>     fun bind(cardView: View) {
>         val tvTitle = cardView.findViewById<TextView>(R.id.tvAccountTitle)
>         val tvSummary = cardView.findViewById<TextView>(R.id.tvAccountSummary)
>         val tvEmail = cardView.findViewById<TextView>(R.id.tvAccountEmail)
>         val ivAvatar = cardView.findViewById<ImageView>(R.id.ivAccountAvatar)
>         val btnAction = cardView.findViewById<Button>(R.id.btnAccountAction)
>         val progress = cardView.findViewById<View>(R.id.progressAccountAuth)
>         val tvDiag = cardView.findViewById<TextView>(R.id.tvDiagnosticsLine)
>         val btnDiagToggle = cardView.findViewById<Button>(R.id.btnDiagnosticsToggle)
>
>         tvTitle.text = fragment.getString(R.string.s0200_card_title)
>         btnDiagToggle.setOnClickListener { viewModel.toggleDiagnostics() }
>
>         fragment.viewLifecycleOwner.lifecycleScope.launch {
>             fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
>                 viewModel.uiState.collect { ui ->
>                     when (val s = ui.state) {
>                         PrimaryGoogleAccountState.Unbound -> renderUnbound(tvSummary, tvEmail, ivAvatar, btnAction, progress)
>                         PrimaryGoogleAccountState.Authenticating -> renderAuthenticating(tvSummary, btnAction, progress)
>                         is PrimaryGoogleAccountState.Bound -> renderBound(s.account, tvSummary, tvEmail, ivAvatar, btnAction, progress)
>                         is PrimaryGoogleAccountState.NeedsResignIn -> renderNeedsResignIn(s.account, tvSummary, tvEmail, ivAvatar, btnAction, progress)
>                         is PrimaryGoogleAccountState.Error -> renderError(s.cause, tvSummary, btnAction, progress)
>                     }
>                     renderDiagnostics(tvDiag, ui)
>                 }
>             }
>         }
>     }
>
>     private fun onActionClicked(currentState: PrimaryGoogleAccountState) {
>         try {
>             when (currentState) {
>                 PrimaryGoogleAccountState.Unbound -> viewModel.signInPrimary(fragment.requireActivity())
>                 is PrimaryGoogleAccountState.Bound -> confirmSignOut()
>                 is PrimaryGoogleAccountState.NeedsResignIn -> viewModel.reconnect(fragment.requireActivity())
>                 else -> Unit
>             }
>         } catch (e: CctUnavailableException) {
>             CctRefusalDialog.show(fragment.requireContext(), cctRefusal) { onActionClicked(currentState) }
>         }
>     }
>     // confirmSignOut, render* helpers — ≤ 30 lines each
>     private fun confirmSignOut() {
>         MaterialAlertDialogBuilder(fragment.requireContext())
>             .setTitle(R.string.s0200_sign_out_confirm_title)
>             .setMessage(R.string.s0200_sign_out_confirm_message)
>             .setPositiveButton(R.string.s0200_sign_out_confirm_confirm) { _, _ -> viewModel.signOutPrimary() }
>             .setNegativeButton(R.string.cancel, null)
>             .show()
>     }
> }
> ```
>
> Wire into `GeneralSettingsFragment.onViewCreated`:
>
> ```kotlin
> private val accountViewModel: GoogleAccountSettingsViewModel by viewModels()
> private lateinit var accountHelper: GoogleAccountSettingsHelper
>
> override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
>     // ... existing wiring
>     accountHelper = GoogleAccountSettingsHelper(this, accountViewModel, cctChecker)
>     accountHelper.bind(view.findViewById(R.id.cardGoogleAccount))
> }
> ```
>
> The `cctChecker` is `@Inject`-ed via Hilt directly into the Fragment (or via the existing helper bag — match the fragment's existing DI convention).

**Verification:**

- `Glob` — `GoogleAccountSettingsHelper.kt` exists.
- `Grep -n "by viewModels<GoogleAccountSettingsViewModel>\\|by viewModels()" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`: `viewModels<GoogleAccountSettingsViewModel>` (or equivalent) matches.
- `Grep -n "accountHelper.bind" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` matches exactly once.
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 06.7 — Update `ResourceAdapter` for "needs sign-in" indicator + `SettingsSearchIndex` entries

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`
**Depends on:** Step 06.5

**Prompt for developer:**

> `ResourceAdapter` (738 LOC): in the existing branch where `ResourceType.CLOUD` is rendered (research identified lines 429, 453, 461, 607), add a `if (resource.needsSignIn) { ... }` block that shows the existing `tvAvailabilityIndicator` slot text as `R.string.s0200_resource_needs_sign_in_label` with a warning color (`R.color.warning` or `?attr/colorTertiary`). Reuse the existing slot — do NOT add a new view.
>
> If the existing `tvAvailabilityIndicator` text is multiplex (already used for other states), promote it to chip-text with priority handling: needs-sign-in beats every other state for Drive rows.
>
> `SettingsSearchIndex` (research identified ≈ 403 LOC, ~40 entries): add a new `SettingsSearchEntry` for the Google Account card with:
> - `id = "settings_google_account_card"`
> - `destination = SettingsSearchDestination.GENERAL` (the existing enum)
> - `titleResId = R.string.s0200_card_title`
> - `keywords = R.string.s0200_settings_search_keywords` (the trilingual keyword string defined in Step 06.2)
>
> Confirm the entry appears in search by running the search UI manually after build — covered by Phase 06's BlockNeedUserTest gate.

**Verification:**

- `Grep -n "needsSignIn" app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` matches at least once.
- `Grep -n "s0200_resource_needs_sign_in_label" app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` matches at least once.
- `Grep -n "settings_google_account_card" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt` matches exactly once.
- Build closure: `/build` → `standardDebug`. Expected PASS. Actual: paste.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `/build` → `standardDebug` AND `noLegalDebug` AND `liteDebug` PASS.
- [ ] `check_strings_localized.ps1 -KeyPrefix "s0200_"` exits 0.
- [ ] Both portrait and landscape `fragment_settings_general.xml` include `card_google_account`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After Phase 06:
- Settings shows the new "Google Account" card with live state.
- Drive resources visibly indicate "needs sign-in" when applicable.
- Sign-in / sign-out are exercised through the new card.
- The pipeline is end-to-end testable in `BlockNeedUserTest` mode: insert `Timber.d("S0200: ..")` tags before pipeline closure.
- Phase 07 (docs / catalog cleanup) does no functional work.

---

## Rollback Plan

Revert the phase commit. The card disappears, the indicator disappears. The underlying identity domain (Phase 02), CCT routing (Phase 03), Drive migration (Phase 04), and wipe (Phase 05) remain operational without UI — the app is still in a usable state (Backup tab Drive sign-in continues to work via the existing path that was rewired in Phase 04).
