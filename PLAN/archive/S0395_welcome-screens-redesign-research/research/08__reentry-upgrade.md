# 08 - Re-entry and Upgrade Behavior

Strategic item: S0395 §6.8. Phase: 04, step 04.1.

## Question

Do existing users see the redesigned onboarding after an update, can onboarding be re-run, and how do form pages pre-populate so re-entry never resets user choices?

## Sources

- `research/01__current-flow-inventory.md` (launch & re-entry section: `welcome_prefs/welcome_completed`, `btnOpenWelcome`, migration coupling)
- `research/06__page4-functionality-toggles.md` (settings readability, installed-state checks)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` + `ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` facts via Phase-01 report

## Findings

- Show condition: MainActivity redirects to welcome only while `welcome_prefs/welcome_completed` is false; the flag is set when the user completes or skips past the permissions step. App updates preserve prefs → existing users will NOT see the redesigned onboarding automatically.
- The flag is load-bearing beyond welcome: `RealDeviceProfileRepository.initializeMigrationIfNeeded()` reads it to decide existing-install migration (S0327). Its file/key must not be renamed or repurposed; "show redesigned onboarding once to upgraders" must NOT be implemented by resetting it.
- Manual re-entry exists today: Settings → General → open-welcome button. Two re-entry defects to not inherit: (a) completion re-applies the full profile preset over user-tuned settings WITHOUT the warning the Settings-side reapply shows; (b) exit uses `CLEAR_TASK`, discarding the Settings backstack.
- Pre-population sources all exist: language (`LocaleHelper`), theme (`ColorThemePrefs`/DataStore), profile (device-profile repository), S0391 toggles (settings layer, once implemented), functionality toggles (`allFiles`, `support*`, `enableOcr`, `enableTranslation` via `SettingsRepository`; XR master pref via a flavor-provided interface), default-app state (`isAlreadyDefaultPlayer` probe). The current page 0 already re-derives language/profile from persisted state on each bind - the same pattern extends to every form page.
- Re-download protection exists: installed-state checks (`DeliverableCapabilityRepository`) make a pre-populated ON toggle a no-op for downloads; only a transition OFF→ON enqueues.

## Options

- Upgrade exposure: (a) do nothing - upgraders meet new pages only via manual re-entry (zero risk, zero reach); (b) one-shot "see the new setup" prompt/banner pointing to the welcome entry (reach without forcing); (c) force-show once by a new versioned flag e.g. `welcome_shown_version` (max reach, max annoyance, extra migration surface). 
- Re-entry preset semantics: re-run applies preset only when the profile actually changed, or always-with-warning (mirroring Settings).

## Conclusion

Redesign keeps `welcome_completed` semantics untouched (migration coupling); upgraders do not auto-see the new flow - recommend option (b), a dismissible one-shot pointer, decided at SYNTHESIS. Every form page pre-populates from its persisted source (all sources exist; XR pref needs the same availability-interface treatment as visibility). Re-entry rules to fix in the dev tickets: apply preset on completion only if the profile changed (else skip), warn like Settings does, and return to the caller instead of `CLEAR_TASK`. Downloads are naturally idempotent thanks to installed-state checks.

## Impact on recommendation

- Recommended structure stores no new "wizard state" - pages render from real settings, which makes re-entry, upgrade and process death trivially consistent.
- Dev-ticket split: re-entry fixes ride with the skeleton ticket; the optional upgrade pointer is a separate tiny ticket the owner can drop.
- SYNTHESIS owner decision: upgrade exposure (default: one-shot pointer, never auto-forcing).
