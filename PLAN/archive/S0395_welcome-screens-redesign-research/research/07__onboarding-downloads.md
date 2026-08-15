# 07 - Downloads During Onboarding

Strategic item: S0395 §6.7. Phase: 03, step 03.2.

## Question

When does an element download start, how is progress surfaced, what happens offline/on failure, how does it relate to the page-2 network choice, and what does store policy allow for release builds?

## Sources

- `research/06__page4-functionality-toggles.md` (toggle → deliverable mapping, ViewModel-scoped downloader, interceptor anatomy)
- `delivery/INVENTORY.md` (asset sizes), `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/RealDeliverableSetDownloader.kt` facts via Phase-02 report
- `PLAN/S0386_ondemand-ocr-translation-delivery.md` (hosting: GitHub Release `delivery-so-v1`, SHA-256+size pinning; translation = Play dynamic feature on store flavors)
- Play policy: [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646) - "An app may not download executable code (e.g. dex, JAR, .so files) from a source other than Google Play"
- `research/04__page2-network-toggles.md` (what page 2 actually controls)

## Findings

### Sizes and realistic time

- OCR engines ~7.2 MB arm64 (noLegal/vr +Paddle → ~16.7 MB); optional rus/ukr OCR data ~15/11.6 MB; translation ~16.6 MB (Play DFM on store, bundled in noLegal/vr); audio visualizations 6.35 MB; FFmpeg-DTS ~7.7 MB.
- A typical eager selection (OCR + translation) ≈ 24 MB, worst case with both language packs ≈ 50 MB - seconds on Wi-Fi, tens of seconds on decent cellular; comparable to the install itself. No multi-hundred-MB scenario exists.

### Current mechanics vs onboarding needs

- Downloads today are ViewModel-scoped OkHttp cold flows (no WorkManager, no foreground service, no notification) - they die with their host screen. Acceptable behind a modal prompt; NOT acceptable for "toggle and keep swiping".
- SHA-256 + size verification and Installed-state marking already exist and are transport-independent.
- The enable-interceptor pattern (modal per capability) would stack sequential modal dialogs on a multi-toggle page - wrong tool for page 4.

### Offline and failure

- Onboarding must never dead-end (strategic §3.2). Correct semantics with existing primitives: a toggle flip records intent and enqueues a download; on failure/offline the element simply remains NOT installed - the Extensions screen already exposes per-element Retry, and the existing enable-interception at first feature use is a natural second chance. The feature setting (`enableOcr` etc.) is enabled only after install succeeds (today's invariant - keep it).
- Offline first run: toggles work (intents recorded), queue drains when connectivity appears (a WorkManager NetworkType constraint gives this for free); onboarding completes normally; a non-blocking "downloads pending - see Settings → Extensions" line on the final page covers expectations.

### Relation to page 2 (honest distinction)

- Page 2 / S0391 toggles govern REMOTE SOURCES (SMB/SFTP/FTP, cloud OAuth providers) - not the app's own HTTP downloads. Mechanically there is no coupling; a user who disabled all remote sources still expects an OCR download they explicitly requested. The only defensible network nuance for downloads is metered-awareness (constraint or size disclosure), which is a delivery concern, not an S0391 concern. Sizes shown next to toggles (the prompt already formats sizes) keep cellular users informed; given ≤50 MB worst case, defaulting to "any network, size disclosed" is reasonable - a metered-only-Wi-Fi option belongs to the Extensions screen later, not to page 4.

### Store policy for release builds

- Non-executable payloads (traineddata, mp4 backgrounds) are data - downloadable from GitHub on any build, policy-clean.
- Executable payloads (.so): Play forbids downloading executable code from non-Play sources. S0386 already routes TRANSLATION through SplitInstall on store flavors (APP_NOT_OWNED graceful failure on sideload). But OCR_ENGINES and FFMPEG_DTS `.so` payloads download directly from GitHub with no installer-origin gate found in research - on the Play-published standard build this is Device-and-Network-Abuse exposure. The exposure pre-exists S0395 (the Extensions screen offers the same downloads), but an onboarding toggle massively amplifies the hit rate.
- Mitigation options (S0386 follow-up scope, not page-4 scope): (a) ship OCR/FFmpeg engines as additional Play dynamic-feature modules on store flavors, mirroring translation; (b) installer-origin gate - direct `.so` download allowed only when the install is not Play-acquired (sideload/debug/noLegal); (c) Play Asset Delivery for the `.so` blobs (delivered through Play, loaded from app storage). Any of these unblocks the page-4 OCR toggle for standard.

## Options

- Download trigger: (a) enqueue immediately on toggle flip via an app-scoped runner with inline per-row progress - matches owner wish §3.1.3 ("during onboarding"); (b) collect intents, start everything on Finish with a single progress surface; (c) modal prompt per toggle (today's interceptor) - rejected, dialog stacking.
- Progress surface: inline per-toggle progress chip (no permission dependency) vs notification (needs POST_NOTIFICATIONS on 33+, already declared and part of the permission batch) - inline primary, notification optional once permission granted.
- Runner: WorkManager (persistence, retry, network constraints for free) vs app-scope coroutine + re-attachable state (lighter, dies with process). WorkManager is the better fit for "queued during onboarding, survives anything".

## Conclusion

Recommended lifecycle: toggle flip = record intent + enqueue in an app-scoped, process-surviving queue (WorkManager-class runner with network constraint and retry); inline progress on the toggle row; onboarding navigation never blocks; on success the capability setting flips ON (existing invariant), on failure/offline the element stays Available with Retry in the Extensions screen and at first-use interception - no dead-ends. Sizes are small enough (≤50 MB worst case) to download on any network with the size disclosed per toggle. Policy: data payloads are clean everywhere; `.so` payloads must NOT be fetched from GitHub on Play-acquired standard installs - resolving that (DFM / installer-origin gate / PAD) is a prerequisite S0386 follow-up before the OCR toggle ships on standard; noLegal/sideload builds are unaffected.

## Impact on recommendation

- Dev-ticket split gains: (1) background-download-runner ticket (WorkManager-class queue + inline progress API), (2) S0386 follow-up ticket "Play-compliant .so delivery for store builds" - a hard dependency of the page-4 ticket for the standard flavor only.
- Page-4 ticket consumes the runner; translation toggle on non-Play installs hidden (artifact 06 default).
- Final-page copy includes a pending-downloads line; POST_NOTIFICATIONS stays optional (inline progress primary).
