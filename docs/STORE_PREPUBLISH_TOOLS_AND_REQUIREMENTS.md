# Pre-publication testing tools and requirements for eight distribution channels

> Mirror of `PLAN/S3363_store-prepublish-testing-requirements/research/01__store-prepublish-tools-and-requirements.md` (ticket S3363, researched 2026-09-21). That file is canonical - update both copies together. This mirror exists so the findings stay visible outside the ticket folder; the automated checks built on it are specced separately (store-prepublish-automated-checks).

Scope: Google Play Console (phone), Google Play Console (Wear OS), Meta Horizon Store (Quest 3), Samsung Galaxy Store, winget, Microsoft Store, Microsoft Edge Add-ons, Chrome Web Store.

How to read this file: entries marked **[Verified 2026-09-21]** were read live from the primary page on that date (page date quoted where the page shows one). Entries marked **[Reference]** are stable official URLs or community tools that were located during the same search session but whose full text was not re-read - check them at submission time.

---

## 1. Google Play Console (phone / tablet)

### Test before you submit

- **Testing tracks** - internal (up to 100 testers, no review), closed, open, then production. **[Verified 2026-09-21]**
- **Closed-testing production-access requirement**: personal developer accounts created after 2023-11-13 must run a closed test with **minimum 12 opted-in testers continuously for at least 14 days** before applying for production access; the counter requires consecutive opt-in days, production review usually takes 7 days or less. (Reduced from the original 20; organization accounts are exempt.) **[Verified 2026-09-21]**
- **Pre-launch report** (Play Console, Test and release > Pre-launch report): powered by Firebase Test Lab, a Robo crawler exercises the uploaded bundle on real devices; runs on every artifact upload and when a release is saved to production; reports stability, Android compatibility, performance and accessibility issues with stack traces and screenshots; devices cover phones, tablets, Wear OS and Chromebooks on Android 9+; configurable test-account credentials, Robo script, up to 3 deep links, up to 5 languages. Known gaps: launchers/widgets/keyboards/watch faces, rooted checks, purchases, Android TV/Automotive. **[Verified 2026-09-21]**
- **Core app quality checklist** (developer.android.com, page updated 2026-08-21): the minimum quality bar with per-criterion test steps - UX (full-window on foldables/orientations, light + dark themes, standard back, notification rules, 48 dp touch targets, 3:1 / 4.5:1 contrast, contentDescription), functionality (audio focus, PiP, sharesheet, no stray background services), performance/stability (60 fps, no StrictMode violations, no crashes/ANRs via Pre-launch report + Android Vitals, latest SDK target, Doze/App Standby), privacy/security (minimum permissions, runtime requests with rationale, encrypted transport, explicit `android:exported`, no dynamic code loading, **Android App Bundle mandatory for new apps since August 2021**). **[Verified 2026-09-21]**

### Requirements (current numbers)

- **Target API level** (Play Help, answer 11926878) **[Verified 2026-09-21]**:
  - Phone/tablet: new apps + updates must target **API 36 (Android 16) from 2026-08-31** (was API 35 from 2025-08-31).
  - Extension: can be requested to **2026-11-01** in Play Console.
  - Existing apps below the bar become hidden from users on newer Android versions rather than removed.
- Store listing / declarations done in Play Console "App content": privacy policy URL, Data safety form, ads declaration, content rating questionnaire, news / financial-features / government-app declarations. **[Reference]** (Play Console Help, stable pages - re-check the form list at submission).
- Release tooling for repeat submissions: **fastlane `supply`** (docs.fastlane.tools/actions/supply) and **Triple-T Gradle Play Publisher** (github.com/Triple-T/gradle-play-publisher) - community/de-facto standard upload and metadata tooling. **[Reference]**

---

## 2. Google Play Console (Wear OS)

### Test before you submit

- **Wear OS emulators**: small round 1.2" (192 dp) and large round 1.39" (227 dp), Wear OS 3.0+ images; **Firebase Test Lab runs physical Pixel Watches** for instrumented tests. **[Verified 2026-09-21]** (Wear OS quality page, "Test your app" section)
- **Play Pre-launch report includes Wear OS devices** in its crawl matrix. **[Verified 2026-09-21]**
- **Wear OS app quality page** (developer.android.com/docs/quality-guidelines/wear-app-quality, updated 2026-08-06) is the review checklist Google Play actually judges; each criterion has an ID (WO-*). **[Verified 2026-09-21]**

### Requirements (current numbers)

- **Target API**: Wear apps must target **API 34+ from 2025-08-31**, **API 35+ from 2026-08-31**; apps at or below API 31 are not discoverable on newer Wear devices. **64-bit support mandatory from 2026-09-15.** **[Verified 2026-09-21]**
- Visual/UX (selection): WO-V2 touch targets 48x48 dp; WO-V3 swipe-to-close back navigation on almost all screens; WO-V4 use `OngoingActivity`/Live Update for long-running operations; WO-V13 black background for apps and tiles; WO-V14 minimum 12 sp essential / 10 sp non-essential text; WO-V15 splash icon 48 dp on black; WO-V16 content fits >= 192 dp circle; WO-V5 preserve state across background/foreground. **[Verified 2026-09-21]**
- Function/performance: WO-P2/P3 install-launch-run without crashes; WO-P5 non-standalone apps must work with the companion; WO-P6 no direct username/password entry on the watch; watch-face specific budgets (Watch Face Format): WO-P7 always-on <= 15% lit pixels, WO-P8 <= 10 MB ambient / <= 100 MB interactive memory, WO-P10 <= 8 complication slots. **[Verified 2026-09-21]**
- Listing: WO-G7 Wear + phone apps must share the same package name and signing key; WO-G3/G4 watch-face icons must be a centered circular face touching the icon edges (from **2026-07-15**); WO-G5/G6 screenshots 1:1, no device frames, no transparency; WO-G2 listing must not say "Android Wear". **[Verified 2026-09-21]**
- **Watch Face Format required for all new watch faces from January 2026.** **[Verified 2026-09-21]**

---

## 3. Meta Horizon Store (Quest 3)

### Test before you submit

- **Meta Quest Developer Hub (MQDH)** - official Windows/macOS desktop tool: deploy APK builds to a connected headset, capture logs, screencast, and **upload builds to release channels** (Alpha > Beta > Release Candidate > Live). Docs page "Deploy build on headset" updated 2026-04-17; downloads on developers.meta.com. **[Verified 2026-09-21]**
- **OVR Metrics Tool** - official on-device overlay (FPS, GPU/CPU utilization, latency); Meta's own VRC performance procedure is to use the app for up to **45 minutes** while watching OVR Metrics. Available on the Quest store and bundled with the Meta XR SDK. **[Verified 2026-09-21]** (cited by VRC.Quest.Performance.1)
- **VRC test plans** (.xls/.csv downloads linked from the VRC page) mirror the technical review criteria - but Meta marks them "slightly out of date; don't use them as a source of truth"; treat the VRC web pages as canonical. **[Verified 2026-09-21]**

### Requirements (current numbers)

- **VRC master page** (developers.meta.com/horizon/resources/publish-quest-req, updated **2026-08-19**) - checks are required/recommended per category: Packaging, Audio, Performance, Functional, Security, Tracking, Input, Asset, Ads, Accessibility, Streaming, Privacy Policy, Content, Publishing. **[Verified 2026-09-21]**
- Packaging: manifest conformance, **APK signature scheme v2, 64-bit binaries, APK < 1 GB, OBB < 4 GB**, supported engine/SDK version. **[Verified 2026-09-21]**
- Performance (VRC.Quest.Performance.1, updated 2025-10-22): run at an allowed refresh rate - interactive apps **72/80/90/96/100/120 Hz** (media apps may use 60 Hz); sustained render rate **>= 60 fps** (>= 30 fps with Application SpaceWarp + motion vectors); head-tracked graphics or a loading indicator **within 4 seconds of launch**; >= 85% render scaling recommended; no extended dips below the floor in a 45-minute OVR Metrics session. **[Verified 2026-09-21]**
- Functional: install/run stability, correct pause behavior, no stuck states or data loss, positional tracking works, multi-user handling. **[Verified 2026-09-21]**
- Security: entitlement check within 10 seconds (recommended); minimum permissions. Input: **focus awareness required**; hand-tracking correctness where used. **[Verified 2026-09-21]**
- Privacy Policy: valid URL, data collection/use/deletion explained. Publishing assets: transparent logo, cover art, screenshots, trailer <= 2 minutes. **[Verified 2026-09-21]**

---

## 4. Samsung Galaxy Store

### Test before you submit

- **Remote Test Lab (RTL)** (developer.samsung.com/remote-test-lab): free remote control of **real Samsung hardware** from a browser - current fleet includes Galaxy S26 Ultra, Z Fold8/Flip8 foldables, Tab S11 tablets, QLED TVs, with a Galaxy Watch category; free account, paid Partner Program adds longer reservations and earlier devices. **[Verified 2026-09-21]**
- **Seller Portal** (seller.samsungapps.com): registration, "commercial seller status" application, submission and management of binaries and listings. **[Verified 2026-09-21]**
- **Galaxy Store Developer API** - Content Publish API: add/modify binaries, submit, change status, manage staged rollouts and **closed beta tests** programmatically. **[Verified 2026-09-21]**
- Samsung publishes **Self-Checklists** ("Self-Checklist (Galaxy)" plus watch and infringement variants) to run before submission. **[Verified 2026-09-21]** (referenced from the Galaxy Store overview page)

### Requirements (current)

- Commercial seller approval "can take several days"; D-U-N-S and international bank verification up to 10 business days; documents in English (or Korean for KR citizens). **[Verified 2026-09-21]**
- Review/certification of each binary by Samsung; listing metadata, content rating questionnaire and privacy policy handled through Seller Portal; precise listing-asset pixel specs live in Seller Portal help, not on the public overview page - re-check inside Seller Portal at submission. **[Verified at structure level 2026-09-21]**

---

## 5. winget (Windows Package Manager)

### Test before you submit

- **`winget validate <manifest-path>`** - local schema validation; **`winget install --manifest <path>`** - local end-to-end install test of the manifest before PR. **[Reference]** (winget-cli docs, stable)
- **microsoft/winget-create** - official manifest creator: `wingetcreate new` fills prompts and can open the PR to winget-pkgs directly. **[Verified 2026-09-21]** (referenced as the primary tool on the manifest docs page)
- **Komac** (github.com/russellbanks/Komac) - community manifest creator in Rust: deep installer analysis for Inno/NSIS/MSI/Burn, `new`/`update`/`remove`/`sync`, works without Git; 469 stars, nightly builds; has an associated "WinGet Releaser" GitHub Action for automated publishes. **[Verified 2026-09-21]**
- **microsoft/winget-pkgs PR pipeline** - every manifest PR is validated by Azure DevOps pipelines (shine-oss/winget-pkgs definitions 12 and 14) before human merge. **[Verified 2026-09-21]**

### Requirements (current numbers)

- Manifest docs updated **2026-09-14**: current **schema 1.12.0**; singleton manifest only for single installer + single locale, otherwise three files (version / defaultLocale / installer) plus optional locales. **[Verified 2026-09-21]**
- `InstallerSha256` (SHA-256 of the binary) required; `PackageIdentifier` unique in `Publisher.Package` form; one PR per package version; `PackageName`/`Publisher` must match Add/Remove Programs entries (export/upgrade correlation); MSI product codes recommended for upgrade; strings <= 100 chars per line. **[Verified 2026-09-21]**
- **All tools must support silent install** - an EXE without a silent switch cannot be listed; Inno/Nullsoft types get silent switches automatically. **[Verified 2026-09-21]**
- winget-pkgs README: community installers must be MSIX, MSI, APPX, MSIXBundle, APPXBundle or .exe; script-based installers not accepted. **[Verified 2026-09-21]**

---

## 6. Microsoft Store

### Test before you submit

- **Windows App Certification Kit (WACK)** - official local certification preview (ships with the Windows SDK, `appcert.exe`): crash/hang tests, resource-use, privacy declarations and other Store onboarding checks for desktop and UWP packages; the Store itself runs equivalent tests on submission, so a local WACK run catches failures early. Pages: "Windows App Certification Kit" and "...Kit tests" (learn.microsoft.com/windows/uwp/debug-test-perf/), "The app certification process for MSIX apps" (learn.microsoft.com/windows/apps/publish/publish-your-app/msix/app-certification-process). **[Reference]** (located via live search 2026-09-21; direct page text not re-read)
- **Partner Center** submission with "Notes for certification" (demo credentials etc.), MSIX packaging, Store submission REST API for automation. **[Reference]**
- Community CI: GitHub Actions exist that run WACK and publish the report as check runs. **[Reference]**

### Requirements (current numbers)

- **Microsoft Store Policies version 7.20**, published **2026-09-15**, effective **2026-10-22** (learn.microsoft.com/windows/apps/publish/store-policies). Key pre-checkable items: **[Verified 2026-09-21]**
  - 10.1 distinct function/value, accurate metadata, unique title, <= 7 search terms; 10.1.4 active presence requirement.
  - 10.2 security: no dynamic code inclusion that changes declared functionality, no malware, no crypto-mining on device, clean uninstall, supported methods only for changing Windows settings; **10.2.9 alternative non-game distribution: MSI/EXE via HTTPS direct URL, binary PE-signed by a Microsoft Trusted Root CA, silent install required, no downloader stubs, versioned URL must never change after submission**.
  - 10.3 testable: working demo account in Notes for certification; servers must be up.
  - 10.4 usability: prompt startup, stays responsive, graceful shutdown.
  - 10.5 personal data: privacy policy URL mandatory in Partner Center for Win32/Desktop Bridge; modern cryptography; location settings respected; no sensitive data without consent.
  - 10.6 capabilities must be declared legitimately; 10.7 localization for all declared languages; 10.8 financial transactions (games/XBOX must use Store IAP; PC non-games may use a secure third-party API; products needing financial info require a company account); 10.9 notifications respect system settings.
  - 11.x content: general content capped at PEGI 12 / ESRB E10+; IARC age rating; UGC rules (terms of service + in-product reporting); gambling restrictions; **11.16 live generative AI content must be disclosed in metadata and Partner Center**.

---

## 7. Microsoft Edge Add-ons

### Test before you submit

- **Partner Center (Edge program)** flow: developer account > upload `.zip` package (validated on upload) > availability > properties > privacy declarations > per-language listings > "Notes for certification" > review, **certification can take up to 7 business days**. **[Verified 2026-09-21]** (publish guide updated 2026-09-02)
- **Porting**: "Port your Chrome extension to Microsoft Edge" guide - a Chromium MV3 extension ports with minimal changes. Local testing via `edge://extensions` developer mode. **[Verified 2026-09-21]** (referenced by the publish guide)
- There is no official Microsoft lint CLI for Edge; run Chromium/MV3 linting (Chrome tooling, below) plus the policy checklist manually. **[Verified at policy level 2026-09-21]**

### Requirements (current numbers)

- **Developer policies** (learn.microsoft.com/legal/microsoft-edge/extensions/developer-policies, ms.date **2026-07-24**): single purpose with narrow functionality; accurate metadata (<= 7 search terms, <= 21 words); no broken links/blockers; stable, must not freeze the browser; **no code obfuscation** (in package or fetched remotely); browser-settings changes only with consent through standard APIs; CSP compliance; updates only through Partner Center; testability (credentials in Notes for certification); **minimum permissions, "future-proofing" permissions forbidden**; privacy policy URL when personal data is touched; no gambling/paywall-circumvention/crypto-mining; "Mature" content declaration at submission. **[Verified 2026-09-21]**
- **Remote hosted code: supported for Manifest V2 only - not permitted in Manifest V3**; the Privacy page makes you declare remote-code usage. **[Verified 2026-09-21]**
- Listing assets (publish guide): description 250-10 000 chars; logo 300x300 recommended (min 128x128, 1:1); small tile 440x280; large tile 1400x560; up to 6 screenshots 640x480 or 1280x800; multi-language listings require `__MSG_*__` i18n placeholders + `default_locale` in the manifest or only one locale is detected. **[Verified 2026-09-21]**

---

## 8. Chrome Web Store

### Test before you submit

- **Chrome Developer Dashboard**: upload zip (**<= 2 GB** or rejected); dashboard tabs Package / Store listing / **Privacy (single purpose + per-data-type disclosure)** / Distribution / optional Test Instructions for reviewers; "Submit for review"; **deferred publishing** (manual publish within 30 days after approval); cancel review while pending. **[Verified 2026-09-21]**
- **New-publisher cap: max 2 published extensions** (themes exempt); lifting the cap requires sustained engagement/tenure. **[Verified 2026-09-21]**
- **Chrome Web Store API v2** (developer.chrome.com/docs/webstore/using-api): upload, publish, status, deploy percentage - official REST for CI. **[Verified 2026-09-21]** (referenced by the publish page)
- Community CLI: **chrome-webstore-upload-cli** (github.com/fregante/chrome-webstore-upload-cli, ~506 stars, MIT, used by Refined GitHub) - upload + publish from CI with OAuth2 refresh token; not an official Google product. **[Verified 2026-09-21]**
- Local: load unpacked via `chrome://extensions`; MV3 lint/compile checks via the `chrome.types`/`tsc` + `web-ext lint` (Mozilla, cross-checks some MV3 rules) - no Google-official linter. **[Reference]**

### Requirements (current)

- **Program policies** (developer.chrome.com/docs/webstore/program-policies) structure **[Verified 2026-09-21]**: safe ecosystem (malicious products, regulated goods); privacy (privacy policy, **Limited Use**, use of permissions, disclosure, handling); marketing (impersonation/IP, **deceptive installation tactics**, ads, affiliate ads, misleading behavior); quality (**spam & abuse, listing requirements, minimum functionality**); technical (**code readability requirements, API use, additional Manifest V3 requirements**, 2-step verification); enforcement (circumvention, appeals, repeat abuse).
- **Manifest V3 is the required format for new submissions**; remote hosted code restrictions and the MV3 service-worker model are part of the "Additional Requirements for Manifest V3" policy section. **[Verified at structure level 2026-09-21]**
- Google API usage in extensions additionally needs OAuth verification for restricted scopes - separate Google API console flow. **[Reference]**

---

## Cross-cutting notes

- **Common denominator across all eight channels**: accurate, non-misleading metadata; minimum-permission principle; a reachable privacy policy when any personal data is touched; testability (demo credentials, working backend); no obfuscated or remotely-loaded code on the browser/extension stores; explicit silent-install/automation support on winget and Microsoft Store direct-download.
- **Recency discipline**: Play target-API and tester rules, Wear quality page, Meta VRC page, Store Policies 7.20, Edge policies and the winget manifest docs were all confirmed live on 2026-09-21. Anything marked [Reference] should be re-opened at submission time - the extension stores in particular change policy text several times a year.
- Search-engine note: the web-search backend was rate-limited for part of the session; every requirement figure above was taken from a fetched primary page, not from search summaries.

## Follow-up for this repository (candidates, not decisions)

- **Play phone**: confirm `targetSdk` of the shipping `standard` flavor is >= 36 before the 2026-08-31 deadline (compileSdk is 37 per the project operations index, so the headroom exists); keep the pre-launch report green per release (operator slice already exists: `store_assets/PLAY_CONSOLE_CHECKLIST.md`).
- **Play Wear**: WO-G7 (same package + signing key for phone and wear) and the WO-V visual rules map onto S3362 (wear standard store first pass); 64-bit wear ABI check before 2026-09-15. Automated by S3364: WO-G7 identity parity runs as `scripts/quality/assert-wear-phone-identity-parity.ps1` and the 64-bit ABI as `scripts/quality/assert-wear-64bit-abi.ps1`, both in the release-scope gate batch; the WO-V visual rules stay manual.
- **Meta (S0555 / S0556)**: VRC packaging limits (APK < 1 GB) need a size measurement of the `vr` flavor APK; the OpenXR diagnostic renderer needs a 72 Hz sustained check with OVR Metrics; entitlement check and privacy-policy URL are submission blockers. Automated by S3364: the packaging limits run as `scripts/quality/assert-meta-packaging-limits.ps1` (APK size, OBB size, signature scheme v2, arm64-v8a) in the release-scope gate batch; the 72 Hz OVR Metrics pass, the entitlement check and the privacy-policy URL stay manual.
- **Samsung (S0775)**: RTL is the cheapest way to prove portrait/landscape and foldable behavior on Samsung hardware without owning it.
- **winget / Microsoft Store / Edge / Chrome**: no deliverable of this repository ships through these channels today (the desktop companion lives in a separate repo); treat this file as the entry point when those publications start.

## Sources

- Play target API: https://support.google.com/googleplay/android-developer/answer/11926878 [Verified 2026-09-21]
- Play personal-account testing: https://support.google.com/googleplay/android-developer/answer/14151465 [Verified 2026-09-21]
- Play pre-launch report: https://support.google.com/googleplay/android-developer/answer/7002270 [Verified 2026-09-21]
- Core app quality: https://developer.android.com/docs/quality-guidelines/core-app-quality (page 2026-08-21) [Verified 2026-09-21]
- Wear OS quality: https://developer.android.com/docs/quality-guidelines/wear-app-quality (page 2026-08-06) [Verified 2026-09-21]
- Meta VRC overview: https://developers.meta.com/horizon/resources/publish-quest-req (page 2026-08-19) [Verified 2026-09-21]
- Meta VRC performance: https://developers.meta.com/horizon/resources/vrc-quest-performance-1 (page 2025-10-22) [Verified 2026-09-21]
- MQDH deploy docs: https://developers.meta.com/horizon/documentation/native/ (Deploy build on headset, 2026-04-17) [Verified via search 2026-09-21]
- Samsung RTL: https://developer.samsung.com/remote-test-lab [Verified 2026-09-21]
- Galaxy Store overview: https://developer.samsung.com/galaxy-store/overview.html [Verified 2026-09-21]
- winget manifests: https://learn.microsoft.com/windows/package-manager/package/manifest (2026-09-14) [Verified 2026-09-21]
- winget-pkgs: https://github.com/microsoft/winget-pkgs [Verified 2026-09-21]
- Komac: https://github.com/russellbanks/Komac [Verified 2026-09-21]
- Store Policies 7.20: https://learn.microsoft.com/windows/apps/publish/store-policies (2026-09-15) [Verified 2026-09-21]
- WACK: https://learn.microsoft.com/windows/uwp/debug-test-perf/windows-app-certification-kit [Reference]
- Edge developer policies: https://learn.microsoft.com/legal/microsoft-edge/extensions/developer-policies (2026-07-24) [Verified 2026-09-21]
- Edge publish guide: https://learn.microsoft.com/microsoft-edge/extensions/publish/publish-extension (2026-09-02) [Verified 2026-09-21]
- CWS program policies: https://developer.chrome.com/docs/webstore/program-policies [Verified 2026-09-21]
- CWS publish: https://developer.chrome.com/docs/webstore/publish [Verified 2026-09-21]
- CWS CLI (community): https://github.com/fregante/chrome-webstore-upload-cli [Verified 2026-09-21]
- fastlane supply: https://docs.fastlane.tools/actions/supply/ [Reference]
