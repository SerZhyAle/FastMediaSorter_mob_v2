# Play Console - All files access declaration (S1989)

Source of truth for the **Permissions Declaration Form** in Play Console
(`App content -> Sensitive app permissions -> All files access`).

The form lives only in the web console, so before this file existed its text could never be reviewed,
edited or re-read before submission. That is how the text below got submitted in a form a reviewer
could refute in a minute, and the update was rejected with
`All Files Access Permission policy: Not a core feature` on 2026-08-24.

Edit **here** first, then paste into the console. Do not compose the text in the console.

The other hand-pasted Console form is the Data safety location section, whose source is
`PLAY_DATA_SAFETY_LOCATION.md` (S2083). Same shape, same reason: no API endpoint, so the repository
is the only reviewable copy.

The listing half of the same argument lives in `play/listing/<locale>/` and is published by
`scripts/release/publish-play-listing.ps1`. Both halves are read together by the reviewer: the policy
requires the core function to be *prominently documented and promoted* in the store description, so a
declaration that claims file organizing and a description that reads as a media player fail as a pair.

---

## Usage selection

- `Core functionality` - **checked**.
- The other five usage options - **unchecked**.

The app has no backup, no antivirus, no document-management-suite and no file-transfer-only mode that
would justify a second box. Ticking more than the one true option invites the reviewer to test the
weakest of them.

---

## Field 1 - "All files access" (limit 500 characters)

Uses 491 of 500 characters.

```text
Core feature: sorting files into folders the user chooses. The user opens a source folder, views each file, then copies, moves, renames or deletes it into a destination folder anywhere on shared storage. This is the app's only purpose and the opening line of its store listing. Without all-files access the app cannot read the source folder the user picked or write into the destination folder, so the sort loop cannot run and the app does nothing. Every file type is sorted, not only media.
```

## Field 2 - "Technical reason" (limit 500 characters)

Uses 474 of 500 characters.

```text
MediaStore indexes media collections only; the app also sorts PDF, EPUB, TXT and archives, which it does not cover. A SAF grant is issued per document tree, so moving a file between two independently chosen folders needs one grant for each, and scheduled transfers run with no user present to grant them. Broad read and write over shared storage is what lets the user name any source and any destination once, then have single moves, batch runs and schedules work over them.
```

---

## Evidence video

The form takes a video link (an unlisted YouTube upload is accepted). Record the operation that is
broken without the permission, and nothing else:

1. Grant All files access, then open a source folder that is **not** a media collection - `Download`
   or a folder created by hand at the root of shared storage.
2. Show a file MediaStore does not index - a PDF, an EPUB or a `.zip`.
3. Move it into a destination folder chosen by the user elsewhere on shared storage, and show it
   arriving there.
4. Show a scheduled transfer between two folders running with no picker and no user interaction.

Do not pad the video with the viewers, the players, the camera or the radio. They are what made the
listing read as a media player in the first place.

### The take of 2026-08-24

**This is the link to paste into the form:** https://youtube.com/shorts/FstAoyHtCXI

Uploaded 2026-08-24 as `allfiles evidence` on the owner's channel. Verified reachable without signing
in - YouTube's oEmbed endpoint answers `200` for it, which it does for public and unlisted videos and
refuses for private ones. If that ever starts failing, the reviewer cannot open the video either, and
the submission dies quietly.

- File: `store_assets/allfiles_evidence.mp4`. 1 min 10 s, 1080x2424, no audio, 2.2 MB.

Two earlier links exist and neither may be pasted again. `hxMUsW3354g` went with the **rejected**
submission. `vgFAqm0ggGQ` is the first cut of this same footage: 129 of its 146 seconds are a still
frame, including 29 unbroken seconds of a motionless PDF immediately after the grant - long enough
that the owner read it as the recording having frozen, and a reviewer would have too.

The recut drops only dead air; every screen and transition survives. Find the dead air mechanically
rather than by eye - `ffmpeg -i <video> -vf "freezedetect=n=-45dB:d=2" -f null -` lists every hold
with its timestamp. Run it from the PowerShell tool: `ffmpeg` is not on the Bash tool's PATH, and
piping a missing binary through `grep` prints nothing, which looks exactly like a clean result.
- Build: `standard release` **2.60.8241.413**, which carries the S1992 fix. This matters: on the
  build that was reviewed, granting the permission left the `Permissions required` dialog on screen,
  so the reviewer's own path ended with the app saying the permission did not work.
- Device: `emulator-5554` (Android 15). The owner's phone was deliberately not touched, because the
  scenario requires revoking `MANAGE_EXTERNAL_STORAGE`.

What is on screen, in order: the `Permissions required` rationale; the system `All files access`
screen being switched on; the app returning with no dialog left over; the source folder
`/storage/emulated/0/Inbox` listing five files MediaStore does not index (PDF, EPUB, ZIP, TXT); the
invoice PDF rendered inside the app with the `Copy to..` / `Move to..` destination bars; the move
into `/storage/emulated/0/Archive/Invoices` and the file arriving there; the scheduled rule
`Inbox -> Books, every 24h, documents only`; its run; the operations log listing four `MOVE .. OK`
lines; and `Books` holding the three moved documents.

**One honest caveat, in case a reviewer asks.** Step 4 above says "running with no user
interaction". The rule in the video is configured with a real schedule (`At 14:36, every 24h`) but
is fired by its `Run now` button, because waiting for a timed firing is not filmable. What the
footage does prove is the part the permission is about: the transfer runs against two folders chosen
in advance, with no SAF picker and no per-file grant. If a reviewer challenges that specific point,
re-shoot with the start time set a few minutes ahead and let it fire on its own.

Fixture, so the take is reproducible: source `/sdcard/Inbox`, destinations `/sdcard/Archive/Invoices`
and `/sdcard/Archive/Books`, all three added as resources (the two destinations marked
`Mark for quick sort`). Every document in it is synthetic - generated for this recording. Do not
reuse `c:\Common\test_media`: its PDFs are real signed documents naming a real person, and one of
them reached an earlier take before it was caught.

---

## What the previous, rejected submission said

Kept verbatim so the next operator does not re-derive it. Each of the three lines below is enough on
its own to lose the review.

- `SAF insufficient for batch file sorting across directories` - refutable. SAF does perform batch
  operations: `ACTION_OPEN_DOCUMENT_TREE` yields a tree, and `DocumentsContract.moveDocument` and
  `copyDocument` work inside it. The true statement is narrower and is the one used above: a grant
  covers one tree, so a move between two independently chosen folders needs a grant for each.
- `File organizer apps explicitly permitted MANAGE_EXTERNAL_STORAGE per Google policy` - a verdict,
  and the reviewer is the one who delivers it. Describe the operation that breaks instead.
- The declaration claimed a file organizer while the store description opened with
  `an all-in-one file manager and media player` and then spent most of its length on the video
  player, music, TV, radio, OCR and AR translation. That listing has since been rewritten to lead
  with sorting; keep it that way, and keep both halves in step whenever either is edited.

---

## App category

The live category is `Photography`, which disagrees with a declaration whose core functionality is
file organizing. The category is not itself a rejection reason, and changing it is the owner's call -
see `PLAY_CONSOLE_CHECKLIST.md` section D.

---

## Submission is manual

The Play Developer API exposes no endpoint for the Permissions Declaration Form, so this text is
pasted by hand and the video link is attached by hand. `publish-play-listing.ps1` publishes the store
listing only; it cannot touch this form.

While the app is under a policy enforcement, Play also refuses to send an edit for review
automatically - `publish-play-listing.py` therefore commits the listing edit **held**, and the
operator sends it from `Publishing overview -> Send changes for review` together with the
resubmitted declaration.
