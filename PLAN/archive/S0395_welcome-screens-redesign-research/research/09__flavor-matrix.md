# 09 - Page × Flavor Matrix

Strategic item: S0395 §6.9. Phase: 04, step 04.2.

## Question

Which pages and toggles exist in each build flavor, what rule collapses an empty page, and is the onboarding usable in a VR headset?

## Sources

- `app_v2/build.gradle.kts` flavor blocks (gate table consolidated in `research/01__current-flow-inventory.md`)
- `dev/FLAVOR_DEVELOPMENT_RULES.md` (source-set discipline)
- `research/02__page0-language-theme.md`, `03__page1-device-profiles.md`, `04__page2-network-toggles.md`, `05__permissions-ordering.md`, `06__page4-functionality-toggles.md` (per-page availability rules)
- `temp/done/S0327_device-profile-onboarding.md` (VR onboarding prior art)

## Findings

Pages numbered by the RECOMMENDED order from artifact 05 (0 language+theme, 1 profile, 2 networks, 3 functionality, 4 permissions, 5 default app).

- Page 0 (language + theme): identical in ALL flavors - both settings are flavor-independent.
- Page 1 (device profile): all flavors; 10 tiles on standard/lite/photos/legacy (vrStub hides VR_HEADSET), 11 on noLegal/vr.
- Page 2 (networks, after S0391): protocol group (SMB/SFTP/FTP) in all flavors; cloud group hidden in lite (`SUPPORT_CLOUD=false`); full six elsewhere (standard/noLegal/photos/legacy/vr). Page absent everywhere until S0391 ships.
- Page 3 (functionality toggles): per-flavor subsets from artifact 06 - standard/legacy: file-manager, audio, video, documents, OCR*, translation* (*OCR also device-gated: API>=26 + RAM>=3GB - always hidden on legacy API 23-25 devices; translation hidden on non-Play installs of store flavors); lite: file-manager, audio, video (no docs/OCR/translation); photos: file-manager only among the proposed set (images-only build - audio/video/docs gates false); noLegal/vr: full set + VR toggle (XR master pref). Extensions-manager button: show only where at least one deliverable is usable (hide in lite/photos until the S0386 inventory-filtering follow-up lands).
- Page 4 (permissions): all flavors; content varies by API tier (artifact 05) and flavor (RECORD_AUDIO row hidden where `SUPPORT_AUDIO=false` - photos); legacy floor is API 23 (runtime storage pair, no special all-files screen pre-30, no POST_NOTIFICATIONS pre-33).
- Page 5 (default app): hidden in lite (`SUPPORTS_DEFAULT_PLAYER=false`); photos shows only the Images button; legacy on API<29 re-shows on every run (pre-Q probe limitation - existing behavior).

Collapse rule: a page renders only if it offers >=1 actionable item for this build+device; otherwise it is absent from the pager (no placeholder), and the page indicator reflects the actual count. Page count per flavor (pre-S0391 / post-S0391): standard 5/6, noLegal/vr 5/6, legacy 5/6, photos 4/5 (functionality page = file-manager toggle only - see Options), lite 3/4 (no functionality beyond file-manager+audio+video? lite keeps the functionality page with 3 toggles → lite 4/5; corrected: lite loses only default-app and cloud group → 4/5).

Source-set consequence: every per-flavor difference above flows through runtime capability/availability interfaces provided per source set (pattern: `DeviceProfileAvailability` + vrStub/vr Hilt modules) - never `BuildConfig` reads in shared welcome code (Rule 15; pre-existing `ENABLE_TRANSLATION` debt in the settings fragment must not be copied).

VR headset usability: onboarding runs as a flat panel on Quest (noLegal sideload); S0327 already shipped profile onboarding there. Inputs = controller pointer (acts as touch/mouse) + D-pad semantics; the existing welcome key-slider (S0289) covers it. Panel sw-bucket (→ grid columns) is not derivable from the repo - flag a device check on the first VR build with the new pages. Forced light mode applies in-headset too (acceptable; pastel pages).

## Options

- photos functionality page: keep a 1-toggle page (file-manager) vs fold that single toggle into another page (e.g. profile page footer) vs drop it for photos. Keeping a 1-item page is more uniform; folding saves a page in the smallest flavor.
- Extensions button in lite/photos: hide until inventory filtering is fixed (recommended) vs show with doomed rows (today's Extensions screen behavior).

## Conclusion

The same six-page skeleton serves all flavors with per-flavor item subsets resolved through availability interfaces; only lite/photos lose whole pages (default app; cloud group; possibly a thin functionality page in photos). The collapse rule "no actionable items → no page" plus a data-driven page list keeps the pager honest and the indicator correct. Legacy differs by API tier, not by page set. VR-family onboarding is usable today via panel + controller; the only unknown is the Quest sw-bucket for grid columns - a device check, not a design risk.

## Impact on recommendation

- Confirms the skeleton must be page-list-as-data with per-page availability predicates (the S0143 ADR-2 intent, finally implemented properly).
- photos 1-toggle page question goes to SYNTHESIS (default: keep uniform page).
- Dev-ticket split: the availability-contract ticket precedes all page tickets; VR device check rides the first noLegal build after page tickets land.
