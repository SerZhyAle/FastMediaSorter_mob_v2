# 02 - Page 0: Language + Colour Theme

Strategic item: S0395 §6.2. Phase: 02, step 02.1.

## Question

Do greeting, language choice and colour-theme choice coexist on one page, and how does a theme/language change behave mid-onboarding (recreate, state survival)?

## Sources

- `app_v2/src/main/java/com/sza/fastmediasorter/core/theme/ColorThemePrefs.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/util/LocaleHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` (theme/locale apply points)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`, `WelcomeViewModel.kt`, `WelcomePagerAdapter.kt`
- `app_v2/src/main/res/layout/page_welcome_enhanced.xml` (+ `layout-land` counterpart), `values/strings.xml` (`color_theme_options`)

## Findings

- Colour theme IS the light/dark setting: exactly three values - AUTO (follow device, default), LIGHT, DARK - mapped to `AppCompatDelegate.setDefaultNightMode`. No separate dark-mode pref, no accent/AMOLED options. Localized labels already exist (`color_theme_options`, EN/RU/UK).
- Dual persistence (S0328 design): canonical DataStore `AppSettings.colorTheme` + synchronous SharedPreferences mirror (`color_theme_prefs/color_theme`) read before first Activity inflates. Any new writer MUST write both (DataStore via settings repository + `ColorThemePrefs.setMode`), else the next cold start renders first frames with the stale theme.
- Apply points: process start (`applySavedMode`) and post-first-frame DataStore reconciliation. Settings flow shows a "restart required" dialog then does a full in-app relaunch (`LocaleHelper.restartApp`) - but a restart is NOT technically required: `setDefaultNightMode` alone recreates affected activities.
- Welcome forces `delegate.localNightMode = MODE_NIGHT_NO` (pastel palette readability; the night welcome palette exists but was judged unreadable). Local override beats the default mode → a DARK pick produces ZERO visible change inside onboarding; first visible effect is MainActivity.
- DynamicColors (Material You) applied unconditionally on API 31+ - any static preview swatch would not match real post-onboarding colours.
- Language: persisted to SharedPreferences + `LocaleManager.applicationLocales` (API 33+) + DataStore mirror; applied per-activity via `attachBaseContext`. Welcome picker = 3-button `MaterialButtonToggleGroup`; on change API<33 calls `recreate()`, API 33+ the system recreates. Settings additionally offers "system default" which the welcome picker cannot express.
- Recreate survival is already solid: pager position (`KEY_CURRENT_PAGE` saved state), `WelcomeViewModel` in-memory state (recommended/selected profile - `detectDeviceProfile()` runs only in `init`), language itself (persisted before recreate). Profile and completion flags persist only at flow end.
- DANGER pattern: reusing the Settings theme flow (`restartApp`) mid-onboarding relaunches the task through MainActivity → fresh WelcomeActivity with no saved state - pager and in-memory selections reset. Must not be reused.
- Layout headroom: page 0 currently stacks language group + profile card + 6-tile grid + details inside a ScrollView (both orientations). Target page 0 drops the profile card (to page 1) and the marketing grid - a symmetric 3-button theme row costs less vertical space than what is freed.
- Extension mechanism exists: `WelcomePage` show-flags + callbacks (`showLanguagePicker`/`onLanguageSelected`) - a theme picker follows the same data-class pattern. New focusable controls participate in the existing D-pad traversal automatically.

## Options

- Theme pick feedback: (a) keep force-light welcome - theme applies after onboarding; needs explicit "applies after setup" helper copy and a non-colour checked indicator; (b) drop the force for live preview - requires reworking the night welcome palette first (it is unreadable by design record). 
- Control parity: theme row default-checks current value (AUTO on first run); optional symmetric question - add "system" language option for parity with Settings (content decision).

## Conclusion

Page 0 = greeting + 3-button language toggle + 3-button theme toggle is feasible today with the existing extension pattern and freed vertical space; no restart and no live-preview machinery is required if the force-light welcome stays (option a - recommended for scope: one helper line of copy instead of a palette rework). A theme write must hit DataStore AND the `ColorThemePrefs` mirror; never reuse the Settings `restartApp` pattern mid-flow. Language recreate behavior is already safe for page position and selections.

## Impact on recommendation

- Target page 0 composition confirmed viable; recommended deviation: theme applies-after-setup copy instead of live preview (cheap, honest).
- Dev-ticket scope note: page-0 ticket touches welcome page model + theme write path; zero new persistence.
- SYNTHESIS owner-decision candidates: live preview vs deferred (default: deferred); "system" language button parity (default: keep 3 explicit).
