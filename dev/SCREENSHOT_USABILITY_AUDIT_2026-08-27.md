# Screenshot usability audit - 2026-08-27

Secondary usability and UI findings read off the accumulated test-screenshot corpus. Each frame was
originally captured to prove one specific thing for one ticket; this audit looks at everything **else**
in the frame. Produced by the `screenshot-usability-audit` skill (ticket S2108).

**Nothing here is implemented.** Each item the owner accepts becomes its own ticket.

## Run

| | |
|---|---|
| Freshness window | 7 days (default) |
| Candidates | 324 |
| Kept and read | 304 |
| Skipped | 20, all `near-black` |
| Coverage | 304/304 - every kept frame was read |
| Findings | 16 accepted, 3 rejected on verification |

The 20 skipped frames are `FLAG_SECURE` screens (Settings, Add Resource, Resource Editor) rendering
black by design. They are **not** defects and were withheld from analysis, which is what the skip
count is here to make visible.

Findings are grouped by category and ordered simple-to-complex within each group. Wear findings are
marked and were judged qualitatively only - never on geometry, because the round display makes
geometric readings unreliable.

---

## System bar safety

Both items in this group are collisions with a bar the content should sit clear of, so both are
CLAUDE.md Rule 17 territory rather than taste.

**1. The "Wear Companion" screen title is drawn through the status bar.** `moderate`
The title glyphs and the status-bar clock ("22:24") and status icons occupy the same pixels - no top
inset is applied to the title. Verified directly, not only reported by a reader.
Source: `temp/S2000/adb-R5CY9070WNB-dyLOim._adb-tls-connect._tcp_20260826_222425.png` (S2000)

**2. Desktop shortcut icons are cropped by the launcher's own taskbar strip.** `moderate`
The bottom row of shortcut icons is cut roughly in half by the taskbar drawn over it; only the top
portion of each icon is visible. Verified directly.
Source: `temp/S1906/RFCR110NBQJ_20260826_073700.png` (S1906)

---

## Contrast and legibility

**3. "Фото-OCR-перевод" wraps mid-word.** `simple`
Breaks as "Фото-OCR-перев" / "од" instead of on a word boundary, and does so identically everywhere
the tile appears - widget picker, desktop grid, app-functions list.
Source: `temp/S2062/gadget-picker-dark-mid1.png` (S2062)

**4. The "Назад" back label nearly vanishes into its background.** `simple`
Near-white label over the light decorative background on the "Плеер по умолчанию" onboarding step.
Source: `temp/scratch/RFCR110NBQJ_20260827_000802.png` (no ticket)

**5. The "Grant Access" button is dark grey on black.** `simple` · WEAR
The only actionable element on the screen barely separates from its background.
Source: `temp/S2055/adb-RFGL1148CRZ-2fv3Pn._adb-tls-connect._tcp_20260826_213426.png` (S2055)

**6. The permission-request banner's last line renders garbled.** `simple` · WEAR
Source: `temp/S2056/emulator-5554_20260826_165657.png` (S2056)

**7. The widget picker loses icon glyphs in light theme.** `moderate`
Погода, Превью папки, Избранное, Задачи по расписанию and Сейчас играет аудио show no icon in light
theme; the same rows render their icons correctly in dark theme. A theme-specific icon-tint problem
rather than missing assets.
Source: `temp/S2062/gadget-picker-light-top.png` (S2062)

**8. "Быстрый диктофон" loses its leading letter and its icon renders as a stray glyph.** `moderate`
Reads "Ыстрый диктофон"; the icon for this row and for "Случайная музыка" beside it both draw as a
text glyph rather than an icon shape.
Source: `temp/S2062/gadget-picker-dark-mid2-resursy.png` (S2062)

**9. Media grid tiles all truncate to the same prefix.** `moderate` · WEAR
Every tile title shows "S1945 Seed.." so the tiles cannot be told apart without opening them. The
truncation point discards exactly the part that distinguishes them.
Source: `temp/S2049/emulator-5554_20260826_011105.png` (S2049)

---

## Consistency

**10. Two Management settings rows carry no leading icon.** `simple`
"Copy, move and overwrite behavior" and "App behavior and operating rules" sit iconless in a list
where every sibling row has a glyph, leaving a ragged left edge.
Source: `temp/S1961/adb-R5CY9070WNB-dyLOim._adb-tls-connect._tcp_20260826_230310.png` (S1961)

**11. The Resources toolbar overflow carries a text label its siblings do not.** `simple`
"⋮" is labelled "Программы" while close, add, search, refresh, settings, grid, star and play in the
same toolbar are icon-only.
Source: `temp/S2062/RFCR110NBQJ_20260827_003257.png` (S2062)

**12. World-time city names stay in English on an otherwise Russian screen.** `simple`
"Tokyo", "Denver" untranslated while dates, labels and dialog titles around them are Russian.
Source: `temp/S1906/RFCR110NBQJ_20260826_073938.png` (S1906)

**13. The world-time widget changes its own time format between refreshes.** `simple`
Shows "2:39" at 07:39:38 and "2:42 PM" three minutes later, with no user action in between - the
same widget instance disagreeing with itself about the 12/24-hour suffix.
Source: `temp/S1906/RFCR110NBQJ_20260826_073938.png` (S1906)

**14. The slideshow interval stepper looks active while the slideshow is off.** `simple` · WEAR
"Enable Slideshow" is switched off, yet the minus/plus/"5s" stepper keeps full-strength button
styling, so nothing communicates that the setting is currently inert.
Source: `temp/scratch/wear-prerelease/emulator-5554_20260825_203718.png` (no ticket)

**15. The Streams filter and sort icons are not distinguishable from each other.** `simple` · WEAR
Both render as a stack of horizontal bars shrinking in length, so the two functions cannot be told
apart by icon alone at watch size.
Source: `temp/scratch/wear-prerelease/emulator-5554_20260825_203423.png` (no ticket)

**16. Several share-target rows show a blank placeholder instead of an app icon.** `moderate`
WhatsApp, Viber, Messenger, Instagram, TikTok and Keep Notes render as blank white squares while
Email, Google Lens, Open in.., Print, Watch and Other apps render icons in the same list.
Source: `temp/S1961/adb-R5CY9070WNB-dyLOim._adb-tls-connect._tcp_20260826_232334.png` (S1961)

---

## Rejected on verification

Recorded rather than dropped silently, because each one is a false-positive shape worth recognising
next run.

- **Two geometric claims about round watch frames** - "the third filter option is clipped by the round
  screen's edge" and "the top icon row overlaps the clock" - on `temp/scratch/s1948_filter.png` and
  `temp/scratch/s1948_streams.png`. Both frames are 480x480 watch captures that a path-based rule had
  routed into a phone batch. This is precisely the documented false positive the wear rule exists to
  prevent, so both were dropped and the routing was fixed to key on frame dimensions.
- **"The weather widget is an empty state with no call to action"** on
  `temp/S1906/RFCR110NBQJ_20260826_073700.png`. The frame does carry one - "Удерживайте, чтобы выбрать
  место" - so an unconfigured weather gadget is behaving as designed here, not violating the policy.

## Coverage notes

- Two five-byte Robolectric `.png` fixtures under `temp/gradle-tmp` are excluded by directory before
  the corpus is assembled; they are build scratch, not captures.
- 15 `temp/S1906/` frames were initially returned as "a third-party car head-unit launcher, not this
  app's UI" and re-read after that was found to be wrong - they are the app's own launcher, desktop
  and gadget surfaces. Findings 2, 12 and 13 come from that re-read.
