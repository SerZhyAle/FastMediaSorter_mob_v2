# S1275 - "Back one page" and "back to first page" sit close enough to be confused

**Status:** Archived
**Priority:** 65

<!-- auto-approved by /spec-all - 2026-07-29 -->

## 0. Raw capture

Owner voice note, 2026-07-29, after a full day reading a 130 MB / 240-page PDF on a phone.
Audio and full transcript: `PLAN/S1273_pdf-reader-daily-use-friction/attachments/`.

Verbatim (RU):

> "но опять же бью стрелочку вернуться на одну страницу назад - попадаю на стрелку вернуться на первую страницу. Несколько раз было .. как с этим бороться: либо кнопки увеличить, либо чуть-чуть дистанцию между ними больше сделать"

Happened repeatedly, not once.

## 1. Problem

Two adjacent controls have wildly different costs. "Back one page" is trivially reversible. "Back to first page" throws away the reader's position in a 240-page document, and nothing warns or undoes it. Placing them adjacent with the current hit size makes an expensive action reachable by a near-miss of a cheap one.

Note the compounding: **S1274** leaves the arrows as the only way to turn pages, so this mis-tap is on the critical path, not an occasional accident.

## 2. Measured, not assumed

- The row is `res/layout{,-land}/player_pdf_controls_overlay_content.xml`. There is no `w600dp` variant.
- `btnPdfPrevPage` is the first child; `btnPdfHome` ("to begin") is the **immediately next** child, separated only by `layout_marginStart="@dimen/player_pdf_button_margin"`.
- `player_pdf_button_size` = **44dp**, below Android's 48dp minimum touch target.
- `player_pdf_button_margin` = **4dp**. So the two hit rects are 4dp apart, and their centres are 48dp apart - inside a single thumb contact patch.
- The row is **not** shared with the video / audio / image players. It is a PDF-only overlay, so this is not a player-family change.
- The two buttons already differ visually - `ic_skip_previous` tinted green versus `ic_fast_rewind` tinted blue. The miss is therefore motor, not visual: making them look more different would not help.

**The EPUB reader has the identical arrangement.** `res/layout{,-land}/player_epub_controls_overlay_content.xml` puts `btnEpubHome` ("to begin") straight after `btnEpubPrevChapter` with the same 4dp margin, and shares the same dimension resource. The owner only hit it in the PDF because that is what he read all day; fixing one reader and leaving the other is knowingly shipping the same trap twice.

## 3. Decision

The owner offered two shapes and accepted either: enlarge the buttons, or increase the distance between them a little ("чуть-чуть дистанцию между ними больше сделать").

**Increase the distance.** Enlarging is the one his layout cannot absorb: the PDF row already carries up to eleven children, and six of them (`btnTranslatePdf`, both translation font buttons, `btnGoogleLensPdf`, `btnSelectTextPdf`, `btnSearchPdf`) are `gone` by default and appear for exactly the kind of text-bearing document he was reading. With those visible the row is already at the edge of a 360dp screen; adding 4dp to every button would push it over. Spacing costs width only where it is needed.

Concretely: a dedicated `player_reader_home_gap` = **16dp** replaces the shared 4dp margin on the "to begin" button only, in both readers and both orientations. `player_pdf_button_margin` stays 4dp everywhere else, so no other control moves.

16dp is a deliberate first calibration of "чуть-чуть", not a derived optimum - it quadruples the gap while adding 12dp to a row that has weighted spacers to absorb it. If it is still mis-tapped, the number is one dimension to change.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** only the "to begin" button moves, by 12dp, away from the page-back arrow. Nothing is added, removed, or reordered, and the page arrows keep their positions.
- **Accessibility:** unchanged content descriptions and focus order. The buttons stay 44dp; this ticket does not close the 48dp gap, see §5.
- **Flavor scope:** all flavors that ship the document reader - the layouts live in `src/main/res` with no flavor variant.
- **Localization:** none - no new or changed strings.
- **Validation level:** on-device tap test in the owner's own reading flow; there is nothing here a unit test can assert.
- **Owner sign-off:** required - only he can say whether the new gap actually stops the mis-tap.
- **Related tickets:** S1273, S1274, S1276.

## 4. Phases

### Phase 01 - Separate the destructive neighbour

- [ ] Add `player_reader_home_gap` (16dp) to `app_v2/src/main/res/values/dimens.xml`, next to `player_pdf_button_margin`, with a comment naming why it exists.
- [ ] Point `btnPdfHome`'s `layout_marginStart` at it in `res/layout/player_pdf_controls_overlay_content.xml` and `res/layout-land/player_pdf_controls_overlay_content.xml` (Rule 11).
- [ ] Do the same for `btnEpubHome` in `res/layout/player_epub_controls_overlay_content.xml` and `res/layout-land/player_epub_controls_overlay_content.xml`.
- [ ] Leave every other `player_pdf_button_margin` reference untouched.
- **Verification:** `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Resources` exits 0; grep confirms exactly four `player_reader_home_gap` references and no change in the `player_pdf_button_margin` count elsewhere.

## 5. Non-goals

- Raising `player_pdf_button_size` from 44dp to the 48dp minimum. It is a real accessibility gap, but it widens a row that is already near overflow with the optional buttons shown, so it needs its own layout work rather than riding along here.
- Moving the jump-to-first / jump-to-last pair out of the arrow row entirely. That is a restructure the owner did not ask for.
- Any confirmation or undo for "back to first page".

## 6. Related

- **S1274** - while swiping cannot turn pages, these arrows carry all the traffic.
- **S1273** - the seek zone in the same reader.
- **S1276** - text selection in the same reader.

## Last Audit

### Manual (device test 2026-07-29)

Device: emulator-5554, `sdk_gphone16k_x86_64`, Android 17 (API 37), 2560x1600 @320dpi (1dp = 2px).
Build 2.60.7262.102-DEBUG (standard), installed 2026-07-29 13:55:39 - not rebuilt for this run.
Media: `test_doc_romcom.pdf` (48 pages) and `test_book.epub` (10 chapters) from
`scripts/utils/setup_test_media.ps1`. Bounds read with `uiautomator dump`, not from screenshots.
Evidence: `temp/S1275/measurements.md` plus the raw hierarchy XMLs and screenshots beside it.

**Criterion 1 - "to begin" is further from the page-back arrow, and a normal back tap misses it: PASS.**
- Expected gap 16dp | actual `btnPdfPrevPage` [4,2054][92,2142] -> `btnPdfHome` [124,2054][212,2142],
  gap 32px = **16.00dp**, centres 120px = 60dp apart. The 4dp build would read 8px / 4dp, centres 48dp.
- Tapping 1px inside the page-back button's right edge (x=90) went 4/48 -> 3/48, one page, not to the start.
- Tapping the centre of the gap (x=108) left the page at 3/48 - the corridor between the two hit rects is
  inert, so a near-miss now lands on nothing instead of on "to begin". Home itself still works: x=168 -> 1/48.
- The distance a miss must travel from the page-back centre to reach "to begin" went from 26dp to 38dp.

**Criterion 2 - the rest of the row did not move: PASS, with one measured side effect.**
- Expected no other control moves | actual: `btnPdfPrevPage` [4..92] and `btnPdfNextPage` [1508..1596] are
  unmoved (both sit against the row's padding edges), and the right-hand group including `btnSelectTextPdf`
  [1432..1500] is unmoved.
- The zoom pair and the page indicator did shift **+12px = +6dp to the right**. Two weight-1 spacers straddle
  that cluster, so each absorbs half of the 12dp added on the left. Measured directly: the spacers are equal
  in every geometry (439/439px portrait, 919/919px landscape, 42/42px at 393dp). This is inherent to the
  spacing mechanism chosen in section 3, not a regression - but it is a deviation from the status note's
  literal "zoom, page indicator .. are where they were", so it is recorded rather than glossed over.

**Criterion 3 - the same gap in landscape: PASS.**
- Expected 16dp | actual `res/layout-land` at 2560x1600: `btnPdfPrevPage` [4,1094][92,1182] ->
  `btnPdfHome` [124,1094][212,1182], gap 32px = **16.00dp**. Near-miss and dead-gap taps behave as in portrait.

**Criterion 4 - EPUB reader separated the same way: PASS.**
- Expected 16dp | actual portrait `btnEpubPrevChapter` [4,2054][92,2142] -> `btnEpubHome` [124,2054][212,2142]
  and landscape [4,1094][92,1182] -> [124,1094][212,1182], gap 32px = **16.00dp** in both.
- Tap test: 3/10 -> x=90 -> 2/10 (one chapter back), x=108 inert, x=168 -> 1/10.

**Extra - narrow-screen overflow risk from section 3: not reproduced.**
- At 1080x2400 @440dpi (393dp wide, the owner's screen class) the gap measures 44px = **16.00dp** and the two
  spacers still hold 42px (~15dp) each with the search and text-selection buttons visible. The row does not
  overflow. This used a `wm size`/`wm density` override, so it is a resource-bucket check, not a real device.
