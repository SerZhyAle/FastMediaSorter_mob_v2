# S1473 research 03 - applying one facet, and the focus chain after the row grows

**Questions (strategic §6.3 plus the §4 defect):** how does the inline trigger write the media-kind facet without disturbing the others, and how do two new icons join the D-pad chain?

**Verdict:** a dedicated single-facet entry point in the ViewModel, and a focus-link update in both orientation layouts.

---

## The existing apply path cannot be reused as is

The ViewModel exposes exactly one filter-apply entry point, and every one of its parameters carries a
default: category, topic, language and country default to null, media kind defaults to "all", and the
pinned-only flag defaults to false. It is written for the filter dialog, which always submits the
complete facet set at once.

Calling it from the inline trigger with only the media kind therefore resets rubric, language, country
and the pinned-only flag to their defaults - silently, because every argument is legal. Passing the
current values from the UI instead is worse: it moves the knowledge of what the complete facet set is
into the view layer, and every future facet has to be threaded through the trigger.

A separate entry point that copies the filter state and replaces only the media kind is the correct
shape. It must keep two behaviours the existing path owns:

- session persistence of the chosen facet, so the next open restores it;
- the video-facet display switch, which remembers the current display mode when entering the video
  facet, forces grid while it is active, and restores the remembered mode on leaving. That behaviour is
  what makes video previews meaningful, and the trigger would break it by writing the facet directly.

Extracting the shared tail (persist plus display switch) and calling it from both entry points keeps one
implementation of both behaviours.

## Focus chain

The control row is a horizontal group whose members declare their neighbours explicitly - the search
field points right at the filter button, the filter button points left at the search field and right at
sort, and both point down into the list. Inserting two icons between the search field and the filter
button breaks three of those links.

The links are declared in both orientation layouts, which are byte-identical in this region, so the edit
is the same in each and Rule 11 parity applies. The landscape relocation does not complicate it: the
manager reparents the whole row as one view, so links inside the row survive the move untouched.

One existing subtlety to preserve: the filter button carries the initial touch-mode focus request, so the
soft keyboard does not open over a short landscape screen on launch. The new icons must not take that
request.

## Status

Resolved. Feeds strategic §5.1 pillar D, §7 risk rows two and five, and §6 item 3.
