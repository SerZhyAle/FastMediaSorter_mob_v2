# Phase 03 - Navigation discovery and en-US capture

**Strategic spec:** [`../S1266_play-listing-tablet-screenshots.md`](../S1266_play-listing-tablet-screenshots.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (tooling), Phase 02 (content)
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

> **Device id:** `emulator-5556` - see the Phase 01 header note.
>
> **Blocker found and cleared before any capture succeeded:** the app setting "Secure sensitive
> screens" (ON by default, S1045) puts `FLAG_SECURE` on the Add Resource screen, so the
> `cloud-connect` slot - and the whole add-a-resource flow this phase needed in order to register the
> Phase 02 content folders - captured as a **pure black PNG** with no error and a zero exit code.
> `temp/S1266/patch_secure_pref.py` flips `secure_sensitive_screens` to false directly in the app's
> Preferences DataStore (the app's own settings UI was slower to drive than the byte edit). Recorded
> in strategic §4.3, because any future re-shoot hits it again on a fresh device.

---

## Objective

Discover the tablet's own tap coordinates for all 8 slots (the phone's `capture-locale-set.ps1`
coordinates do not transfer - wide layout, different panel positions) and capture a verified-correct
en-US raw screenshot set, before spending the locale-replication effort in Phase 04.

---

## Prerequisites

- [ ] Phase 01 done - `temp/S1266/prep-shot-tablet.ps1` exists and runs clean.
- [ ] Phase 02 done - all content on-device and MediaStore-indexed.
- [ ] `temp/S1256/ui-probe.ps1` available and usable at the tablet's device id (device-agnostic per
      its own `-DeviceId` param - confirmed during `/spec-tech` research).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S1266/capture-locale-set-tablet.ps1` | New | ≤ 160 |
| `temp/play-shots-tablet/en-US/*.png` (8 files) | New (device-generated output) | - |

---

## Steps

### Step 03.1 - Source the image-viewer photo, then discover navigation and capture en-US

**Files:** `temp/S1266/capture-locale-set-tablet.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> First, source the image-viewer slot's content: strategic §4.2 calls for "a public-domain photograph
> selected by the agent" (explicitly not the owner's personal photo). Pick one clearly-public-domain
> image (a NASA/US-government photo is the safest unambiguous choice, consistent with the video-player
> slot's own NASA sourcing) and push it to the tablet's browse-visible storage alongside the other
> Phase 02 content, then MediaStore-scan.
>
> Then build `temp/S1266/capture-locale-set-tablet.ps1` as a tablet-geometry sibling of
> `temp/S1256/capture-locale-set.ps1`: same `Reset-ToMainTop` / `Tap` / `Swipe` / `Back` / `Save`
> function shapes, but:
> - Calls `temp/S1266/prep-shot-tablet.ps1` instead of the phone `prep-shot.ps1`.
> - `Save` calls `scripts/release/capture-play-screenshots.ps1` with `-DeviceId emulator-5554` and
>   writes into `temp/play-shots-tablet/<locale>/` (matches Phase 01's compose-script `--tablet`
>   convention) - `capture-play-screenshots.ps1` itself does not need changes for this (it always
>   writes under `temp/play-shots/<locale>/`; either extend it with a `-ShotsDir` override in this
>   step if it does not already support one, or `Move-Item` the result into the tablet tree after each
>   `Save` call - pick whichever is the smaller, more mechanical change and note the choice in this
>   step's log).
> - Every tap coordinate is **discovered live**, not guessed from the phone script: drive the app by
>   hand (or via `temp/S1256/ui-probe.ps1`-style element dumps) at the tablet's actual
>   `2560x1600@320` (`1280x800dp`) geometry, screen by screen, recording the exact coordinates that
>   work before writing them into the script. Expect the wide layout to place panels differently than
>   the phone's single-column layout - do not assume any phone coordinate carries over.
> - Reuses `Reset-ToMainTop`'s core idea (stop playback, force-stop, launch, verify `btnAddResource`
>   reachable) - the CameraCaptureActivity SDK 37 letterboxing quirk noted in strategic §4.1 affects
>   only the camera slot's own screen, not this reset routine.
> - Covers the reader slot's name-filter flow (does not survive locale switch, per S1256's own
>   in-script comment) using the new PDF's actual title/filename instead of "Alice" - re-derive the
>   filter text from what Phase 02 Step 02.2 actually named the file.
>
> Run the script once for `en-US` and inspect `temp/play-shots-tablet/en-US/*.png` - 8 files expected.

**Verification:**

- `Glob` - `temp/S1266/capture-locale-set-tablet.ps1` exists.
- `Glob` - `temp/play-shots-tablet/en-US/*.png` returns exactly 8 files (`browse`, `image-viewer`,
  `video-player`, `streams`, `music-player`, `reader`, `cloud-connect`, `camera`).
- Each PNG's dimensions match the tablet's actual capture geometry (not the phone's 1080x2400) -
  check via any image-metadata read, not by eye alone.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. `temp/S1266/capture-locale-set-tablet.ps1` written and run
  for `en-US`, exit 0, all eight `SAVED <slot>` lines printed. PIL reports all 8 PNGs at
  **2560x1600** - the tablet's geometry, not the phone's 1080x2400.
- **Raw-tree routing:** chose `Move-Item` after each `Save` over adding a `-ShotsDir` override to
  `scripts/release/capture-play-screenshots.ps1`, per this step's "pick whichever is smaller" -
  it is one line in a scratch script instead of a new parameter on a release-owned script.
- **Navigation is entirely new, as predicted.** At 1280dp the app draws a five-column resource grid
  and a single-row toolbar; not one phone coordinate transferred. Discovered live via
  `temp/S1256/ui-probe.ps1`. Non-obvious findings worth keeping:
  - A resource tile must be tapped on its **label**, not its icon - `ivResourceTypeIcon` carries the
    content-description `Start slideshow` on several tiles and launches a slideshow instead of
    opening the folder.
  - The manual-path dialog opens with its "Special Folders" and "Quick Select" sections expanded,
    which pushes the path field and `Validate and Select` off-screen; collapsing both headers first
    makes the dialog compact and the state persists.
  - The soft keyboard overlays the dialog's buttons: a blind tap on the probed `Validate` coordinate
    lands on a key and types a character into the path. `keyevent 111` (ESC) hides the keyboard and
    restores the probed geometry - the same class of trap as S1256's "Apply sits higher while the
    keyboard is open".
  - The Programs popup does not appear in `uiautomator dump` at all; its two entries (`Streams`,
    `Camera`) were measured off a verified screenshot instead.
  - The PDF next-page control is `btnPdfNextPage` at the far bottom-right, not the blue arrow beside
    it (which is next-*file*).
- **`Open-BrowseItem` opens files by name, not grid position.** The browse grid is date-sorted, so
  re-pushing a file silently reorders it; a positional tap had already opened the wrong photo once.
- **Image-viewer needed a second pass.** The first `image-viewer` frame showed heavy horizontal
  streaks down both sides: `photoView` measures 2560x1343 (aspect 1.906), the source still was 3:2,
  and the gap was filled by `ivDynamicBackground`, the app's blurred-backdrop effect, which smears
  into bands at that extreme stretch. Re-cropped the NASA still to exactly 2560x1343 and re-shot -
  the frame now fills the viewport edge to edge with no backdrop visible at all. The Quick Sort
  `Copy to..` / `Move to..` panels were also collapsed (they occupied ~25% of the frame expanded).

---

### Step 03.2 - Visual QA of the en-US set before locale replication

**Files:** none (review only - no code/content change if QA passes)
**Depends on:** Step 03.1

**Prompt for developer:**

> Read all 8 `temp/play-shots-tablet/en-US/*.png` files (image read, not just existence check) and
> confirm, per slot:
> - Correct screen is showing (not a mistimed capture of a transition/loading state or the wrong
>   screen entirely - S1256's own documented failure mode).
> - No forbidden content visible anywhere in frame (S1256 §7 list - third-party watermark, personal
>   document, trademarked poster) - including background/list items visible behind the focused item,
>   not just the focused item itself.
> - `camera` slot: confirm whether the SDK 37 letterboxed-portrait behavior (strategic §4.1) makes the
>   frame look broken/unacceptable for a store listing: if so, this is a real finding for the
>   strategic spec (note it in `## 4` as an update, do not silently ship a bad frame) rather than a
>   step to route around here.
> Any slot failing QA -> go back to Step 03.1, fix that slot's coordinates/content, re-capture just
> that slot (`Save '<slot>'` alone), re-QA. Do not proceed to Phase 04 with a known-bad frame.

**Verification:**

- Written confirmation (in this step's log, not a separate file) that all 8 en-US frames passed both
  checks (correct screen, content-safe) - explicit per-slot PASS, not a blanket "looks fine".

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 1/1 PASS. All eight `temp/play-shots-tablet/en-US/*.png` opened and
  looked at as images, not merely listed. Per-slot verdict:
  - `browse` - **PASS.** Five-column resource grid, ALL tab. Every tile renders as an icon or an
    empty placeholder; no thumbnail of any quarantined file appears, which was the specific risk.
  - `image-viewer` - **PASS** (second capture). NASA `iss071e365062` Acklins Island still, full-bleed,
    no backdrop streaks, no identifiable people.
  - `video-player` - **PASS.** NASA Webb 360 clip in the Goddard clean room, transport controls and
    progress bar visible at 00:14 / 01:47. Public domain; the figures in shot are in clean-room suits,
    not identifiable.
  - `streams` - **PASS.** Three-column channel catalog. Every visible entry is a music radio station
    (Blues, Jazz, Country, Hip Hop, Disco, Reggae, Latin, Darkwave) with a generic station favicon.
    None of the S1256 §7 stream offenders - no war-footage frame, no suggestive station logo - is on
    screen. The one video entry (`+SBT Novelas`) shows a country flag, no imagery.
  - `music-player` - **PASS.** Visualizer mid-playback (pause icon showing, 00:17 / 00:18) over the
    voice memo, so the app's own graphics render instead of third-party album art.
  - `cloud-connect` - **PASS.** Local / Network (SMB) / SFTP-FTP / Cloud Storage side by side, which
    is exactly what the caption claims. Google Drive, OneDrive and Dropbox appear as integration
    names in body text - allowed, they name real supported integrations. No credential field on screen.
  - `reader` - **PASS.** Alice in Wonderland body text, page 13/26. Public domain, and the Project
    Gutenberg branding was stripped when the PDF was built, so none is visible.
  - `camera` - **PASS on both gate criteria**, with a finding. Correct screen (PHOTO/VIDEO selector,
    shutter, lens switch) and content-safe (the emulator's synthetic room scene - no real people, no
    third-party imagery).
- **Camera letterbox: raised, not routed around**, exactly as this step instructs. `previewViewCamera`
  measures `[680,0][1880,1600]` - 1200 px of 2560, so 47% of the frame is black. Verified from the
  manifest that this is deliberate, not a capture defect: `CameraCaptureActivity` is
  `screenOrientation="portrait"` (S0754/S0924) and carries
  `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` (S0934) specifically to keep the letterboxed
  behavior. A real tablet owner sees this same frame, so it is honest but visually poor for a store
  listing. Written up in strategic **§4.3** with the options; the decision belongs to the owner at the
  Phase 05 manual gate, alongside the other 23 images. Not silently shipped, and not silently fixed.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] 8/8 en-US raw screenshots exist, verified correct-screen and content-safe.
- [ ] `temp/S1266/capture-locale-set-tablet.ps1` is reusable as-is for Phase 04's ru-RU/uk-UA passes
      (only the `-Locale` param changes between runs - if any coordinate turned out
      locale-UI-dependent, that is itself a Phase 04 finding, not assumed here).

---

## Handoff Notes to Next Phase

Phase 04 runs `capture-locale-set-tablet.ps1 -Locale ru-RU` and `-Locale uk-UA` using the coordinates
this phase already validated - no new discovery expected, only the app-locale-driven UI text differs.

---

## Rollback Plan

Low-risk: all outputs are gitignored `temp/` scratch and on-device content; nothing tracked-repo is at
risk from a bad capture. Re-run affected steps.
