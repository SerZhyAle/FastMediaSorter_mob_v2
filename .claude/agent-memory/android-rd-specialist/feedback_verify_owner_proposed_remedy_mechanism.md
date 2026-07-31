---
name: verify-owner-proposed-remedy-mechanism
description: The owner reports symptoms accurately but his proposed fix encodes a guessed mechanism - trace the real cause before implementing what he asked for
metadata:
  type: feedback
---

When the owner proposes a remedy ("move the text left", "make the buttons bigger"), treat the **symptom** as authoritative and the **mechanism** as a hypothesis. Trace the actual cause in code before implementing his fix. If the mechanism turns out different, say so plainly and put the real choice in front of him instead of quietly building what he asked for.

**Why:** 2026-07-29, S1273. His voice note said he keeps missing the paging spot in a PDF, lands on the text, and waits for text extraction; he proposed moving the text or shifting it left so the finger stops hitting "the seek place". There is no seek zone over a PDF page at all. The real cause, traced through `PlayerGestureSetupManager.configurePhotoViewGestures` and `PdfViewerManager.handlePdfFling`: paging is a *fling*, it is switched off entirely while the page is zoomed in (deliberately, so a zoomed page can pan), and unzoomed it needs both a distance and a velocity threshold. So on a dense document he zooms to read, loses every finger paging gesture, and his slow swipe attempts arrive as long presses that start text selection. Implementing "move the text left" would have cost a layout change in four files and fixed nothing. It also collapsed two tickets (S1273 + S1274 "cannot page with a finger") into one root cause.

Same session, opposite outcome worth remembering: in S1275 he proposed two remedies ("enlarge the buttons" or "increase the spacing") and both were mechanically sound - measurement only decided *which*. So this is not "distrust his fixes", it is "verify the mechanism, then either implement or explain".

**How to apply:**
- A spec quoting a single line from a voice note can invert the meaning. Read the **full transcript** in `PLAN/<id>_*/attachments/*.transcript.txt` before acting - S1273's original section 1 said the exact opposite of what he described, because it was written from one quoted sentence.
- Trace the gesture/dispatch path in code before accepting any "these two things overlap" premise; overlap in the user's mental model is often gesture arbitration in the code.
- When the mechanism differs from his guess, the ticket's value is the correction: state that his remedy would not have worked, and replace the vague question with the sharp one.
