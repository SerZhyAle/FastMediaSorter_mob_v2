---
name: shared-layout-fix-at-shared-layer
description: A visual defect in one dialog that shares a layout must be fixed in the shared component, not patched per host - the pickers proved a per-host patch resurfaces
type: feedback
---

When a dialog's visual defect comes from the *shared* layout or the boilerplate every host copies, fix the shared component (layout root + one helper) instead of patching the one host that was reported.

**Why:** S1095 fixed exactly this defect (transparent frame, title/search unreadable over the dim scrim, list stuck at a hardcoded 300dp) for `AppPickerDialogFragment` alone, with an explicit comment "only this picker; the shared layout stays untouched". The other seven pickers on `dialog_searchable_option_picker.xml` stayed broken and the owner re-reported it on 2026-07-29 for the panel-editor feature picker (S1286). The compiler never catches this - each fragment duplicates its own `onStart` window block, so a fix in one silently diverges from its siblings.

**How to apply:**
- Before fixing a dialog, grep the layout/binding name: several `DialogFragment`s inflating one `Dialog*Binding` are a family.
- A self-drawn `DialogFragment` (`onCreateView` + transparent window) has no surface of its own - the layout root must carry the opaque card, otherwise only the row backgrounds paint. A `MaterialAlertDialogBuilder` host already has one and must not get a second.
- Height: cap against the window (`MaxHeightLinearLayout` root + `wrap_content` list) rather than a fixed dimen, so short lists shrink and long ones scroll.
- Related family lesson for the players: [[player-family-glue-mirroring]].
