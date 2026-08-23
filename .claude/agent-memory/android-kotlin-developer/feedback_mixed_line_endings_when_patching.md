---
name: mixed-line-endings-when-patching
description: Source files in this repo mix CRLF and LF, so a scripted multi-line anchor patch must normalise on read and restore the original ending on write
metadata:
  type: feedback
---

When patching a `.kt` file with a script (python/sed) using a multi-line anchor, normalise `\r\n` to `\n` on read and write the file back with the ending it originally had. Do not assume LF.

**Why:** the tree is genuinely mixed - `wear/ui/streams/StreamsScreen.kt` and `wear/ui/network/NetworkSourceGrid.kt` are CRLF while `wear/ui/common/ThumbnailCell.kt` is LF. A multi-line anchor written with `\n` silently fails to match on a CRLF file (the script reports "anchor not found", which costs a round trip), and normalising without restoring re-writes the whole file's endings, turning a three-line edit into a whole-file diff.

**How to apply:** any scripted edit spanning more than one line. Read with `open(p,'rb').decode('utf-8')`, remember `nl = '\r\n' if '\r\n' in raw else '\n'`, work on the normalised text, write back with `s.replace('\n', nl)`. Single-line `sed` substitutions are unaffected. The Edit tool handles this itself - this only bites hand-rolled scripts. See [[pwsh-efficiency]].
