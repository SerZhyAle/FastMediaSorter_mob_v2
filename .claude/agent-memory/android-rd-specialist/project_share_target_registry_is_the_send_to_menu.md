---
name: share-target-registry-is-the-send-to-menu
description: The app's own unified "Send to.." menu is a Hilt-multibound ShareTargetRegistry, not the Android share sheet - a new recipient is two declarations, not a menu edit
metadata:
  type: project
---

A feature phrased as "add X to the send/share menu of a media file" almost never means the Android
system share sheet here. The app owns a unified in-app menu titled **"Send to.."**
(`share_to_menu_title`), built on **`ShareTargetRegistry`** with Hilt multibinding (S0452/S0459), and it
is already mounted on every media surface: browse row and overflow, player, standalone viewers, camera,
text viewer.

Adding a recipient is therefore two declarations, not a UI edit: one `ShareTarget` (`@IntoSet` in
`ShareTargetModule`) and one `ShareTargetHandler` (`@IntoMap` in `ShareTargetHandlerModule`). The
settings toggle, the fixed position in the list, the per-type filter (`applicableTypes`) and the
"applies to the first file" rule (`batchCapable = false`) all come from the existing mechanism.

**Why:** researching S1884 (2026-08-21) I searched for `ACTION_SEND` / `Intent.createChooser` and for
`*ActionCatalog*`, found only the system chooser, and wrote three candidate surfaces into the spec -
all three wrong. A sibling session answering the same question found the registry, and recorded that
none of my three options described the real surface. The cost was a spec that framed a two-declaration
change as an open architectural question.

**How to apply:** before speccing anything about sharing, sending, or "open this elsewhere" for a media
item, query the catalog for `*ShareTarget*` and read `ShareTargetRegistry` first. Only conclude the
system share sheet is meant if that registry genuinely cannot host the recipient. Related: the
stream-side precedent is `SendStreamToWatchUseCase` + `StreamMenuAction.SEND_TO_WATCH` (S1799), which is
a *different* menu - the streams overflow, not the media "Send to.." registry.
