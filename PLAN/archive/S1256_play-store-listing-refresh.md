# S1256 - Play store listing refresh (screenshots + descriptions)

**Status:** Archived

## 0. Trigger

The live Google Play listing is stale. Read-only probe of `edits().listings()` on 2026-07-28 returned:

- `en-US` full description is the pre-S0497 "file organizer" text (3786 chars), written to justify `MANAGE_EXTERNAL_STORAGE` to policy review.
- The S0497 rewrite (June 2026) sits in `play/listing/` unpublished - live text and repo source diverge.
- Phone screenshots predate the Streams and Camera feature families.
- Only `en-US` carries images; `ru-RU` and `uk` inherit them.

## 1. Gap

Capabilities shipped since the S0497 rewrite and absent from both the live and the local description:

- Internet Streams - live TV and radio channels, pinned tiles, per-channel track memory, adaptive quality (S1061..S1220).
- Camera capture with device-matched lenses, zoom range and photo sizes (S1066, S1189).
- Scheduled operations with a local folder as sender or receiver (S1009).
- QR-code sharing of an SFTP resource (S1039).
- Downloadable extensions offering their newer version (S1200).

## 2. Decisions (owner, 2026-07-28)

- Rewrite the description around current capabilities; keep a dedicated all-files-access paragraph so the permission justification survives review.
- Ship 8 phone slots: browse, image-viewer, video-player, streams, slideshow, cloud-connect, reader, camera.
- Do not showcase the Launcher family - a home-screen replacement in the listing invites policy attention it does not need.
- Capture three full locale sets (en-US, ru-RU, uk-UA) with the app UI in the target language, not one set with translated captions.
- Publish live via `publish-play-listing.ps1 -Mode commit` once validate passes.

## 3. Scope

- `play/listing/captions.json` - slot set and localized captions.
- `play/listing/<locale>/{title,short_description,full_description}.txt`.
- `play/listing/<locale>/images/phoneScreenshots/*.png`.
- `scripts/release/compose-play-screenshots.py` - per-locale raw-shot lookup.
- `scripts/release/capture-play-screenshots.ps1` - per-locale output directory.

## 4. Out of scope

- Seven-inch, ten-inch and TV screenshots. The publisher only replaces `phoneScreenshots`; the existing sets stay live.
- Feature graphic, icon and TV banner.
- Any AAB upload. This is a listing-only edit.

## 5. Constraints

- Play caps title at 30 chars, short description at 80, full description at 4000.
- Play accepts a phone screenshot only with both edges in [320, 3840] and aspect ratio at most 2:1.
- Play uses `uk`, not `uk-UA`; the folder-to-language map in `publish-play-listing.py` already encodes this.
- The listing commit is one-way and may route through review.

## 6. Progress (2026-07-28)

- Texts rewritten for all three locales; every field inside the Play limits (longest is `ru-RU` full at 3031/4000).
- Slot set replaced: `widgets` dropped, `streams` and `camera` added, captions localized.
- Capture tooling extended for per-locale sets; `play/listing/README.md` documents the flow.
- `en-US` complete: all 8 slots captured and composed to 1200x2400.
- `slideshow` slot replaced by `music-player` (owner call) - the slideshow frame would have duplicated image-viewer, while the audio visualizer was absent from the set entirely.
- Locale replay scripted in `temp/S1256/capture-locale-set.ps1` after the en-US pass validated the navigation.
- `publish-play-listing.py` extended to the seven-inch and ten-inch screenshot slots; a type with no local folder is skipped so its live set survives untouched.
- Parked while driving the app: S1264 (audio player cannot be left with Back while playback is active) and S1265 (virtual resource names and descriptions stay English on a switched locale).

## 6.1 Automation traps hit during the locale replay

Each of these produced a plausible-looking screenshot of the wrong thing, and none surfaced as a non-zero exit code:

- A launch after force-stop resumes the last screen, so the first run captured the audio player as the "main screen" slot. Playback must be stopped (`input keyevent 86`) before the reset, otherwise the main screen bounces straight back into the player.
- `am start --activity-clear-task --activity-new-task` against a freshly force-stopped package leaves the launcher in front instead of starting the app; the plain launch verb works.
- A helper parameter named `$Args` shadowed PowerShell's automatic variable, so the `set-app-locales` call silently never reached the device and a whole set came out in English.
- The document name filter does not survive the locale switch, so the reader slot captured whatever sorted first - a stray UI-dump XML - instead of the book.

## 7. Content safety findings

- Local video corpus is unusable for a public listing: TikTok and Instagram reposts with visible watermarks and creator handles, one carrying political imagery.
- `3dvr/360_mono/NASA_Webb_EQUIRECT_360_MONO_720p.mp4` is public-domain NASA footage and doubles as a 360° feature demo - proposed source for the video-player slot, not yet pushed to the device.
- Stream catalog needs the media-type filter set to Audio before capture; unfiltered it surfaces a drone-strike news frame.
- Image slots must be picked by filename from the owner's own photography; the aggregate list also contains an OCR sample carrying real news text about a death.
- `test_doc_scanned.pdf` is a real signed consent letter naming a private individual with a home address, and `c:\Common\test_media` holds a second one; `test_doc_romcom.pdf` is erotic fiction. None may appear in any public capture.
- The only well-tagged audio track carries the Adele *Skyfall* single cover - James Bond artwork with the 007 mark and an actor likeness. A voice memo is used instead, which renders the app's own visualizer and carries no third-party rights.

## 8. Last Audit

**Дата:** 2026-07-28. **Вердикт:** Verified - витрина опубликована и подтверждена чтением живого состояния.

- `publish-play-listing.ps1 -Mode validate` - exit 0, 3 локали, по 8 изображений.
- `publish-play-listing.ps1 -Mode commit` - exit 0, edit `16592202688549518173` закоммичен.
- Пост-коммит read-only проба: en-US / ru-RU / uk несут новые заголовки, краткие и полные описания (2677 / 3031 / 3011 символов) и по 8 телефонных скриншотов. Раньше картинки были только у en-US.
- Планшетные и TV-слоты не тронуты: sevenInch 6, tenInch 2, tvScreenshots 4, featureGraphic, icon, tvBanner - ровно как до публикации, что подтверждает пропуск типов без локальной папки.
- `post-change.ps1` - все применимые гейты PASS; detekt-гейт не отработал из-за занятого `BUILD.LOCK` соседней сессии, к изменению отношения не имеет (Kotlin не затрагивался).

expected: живая витрина показывает новые тексты и 8 телефонных скриншотов в каждой локали | actual: так и есть - PASS.

**Замечание по S1264:** запаркованная во время работы находка про выход из плеера расследована параллельно и закрыта как «не баг» - диалог не отрисовывался под активным `wm size`-оверрайдом эмулятора. Это тот же оверрайд, под которым шла съёмка, поэтому каждый кадр проверялся глазами.

---

## 9. Verification

- `publish-play-listing.ps1 -Mode validate` exits 0 with no char-limit violation.
- `compose-play-screenshots.py` writes 8 files per locale and reports no missing slot.
- A post-commit read-only probe shows the new short description and 8 phone screenshots per locale.
