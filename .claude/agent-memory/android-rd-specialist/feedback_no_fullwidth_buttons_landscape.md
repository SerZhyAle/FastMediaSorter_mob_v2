---
name: no-fullwidth-buttons-landscape
description: Buttons must never stretch full screen width in landscape; width = text (wrap_content) or fixed by form logic; keypad grids / nav rails / full-row item buttons exempt
type: feedback
metadata:
  type: feedback
---

No button may stretch wide in landscape - not full screen AND not half-screen via weighted pairs. A button's width is sized to its text (`wrap_content`), or it lives in a wrapping `Flow` chip group, or it is fixed by genuine form logic (keypad only). Established as a project-wide UI invariant in S0605 (2026-06-22).

**Owner override (learned the hard way):** the owner's explicit "this is awful" about half-screen 50/50 weighted button pairs OUTRANKS any inline "equal-weight 0dp lets labels wrap" code comment. Do NOT treat such a comment as a Rule-8 exemption - half-screen weighted pairs must also be unified. The first S0605 pass wrongly left the `fragment_settings_general` export/import pairs citing that comment; the owner rejected it.

**Long-label groups (export/import, backup/restore):** simple `wrap_content` in a horizontal LinearLayout OVERFLOWS in portrait (LinearLayout never wraps; MaterialButton wrap_content stays one line). The correct fix is `androidx.constraintlayout.helper.widget.Flow` with `app:flow_wrapMode="chain"` + `flow_horizontalStyle="packed"` + `flow_horizontalBias="0"` - already used in `fragment_settings_general` as `flowDocLinks`. Buttons become text-sized and wrap to new lines. Use this pattern for any group of 2+ text buttons.

**Three offender patterns to fix on sight (any layout, any flavor):**
- Single button `android:layout_width="match_parent"` -> `wrap_content` + `layout_gravity` (center for primary/standalone; end for action bars; start to match a left-aligned icon+text menu).
- ConstraintLayout stretch: `0dp` with BOTH `constraintStart_toStartOf="parent"` and `constraintEnd_toEndOf="parent"` -> `wrap_content`, keep both anchors (centers it).
- Button-bar pair: two buttons in a horizontal LinearLayout each `0dp` + `layout_weight` -> each `wrap_content`, drop the weights, parent `gravity="end"`.

**Exempt - genuinely fixed by form logic, leave as-is (verified examples):**
- Keypad / numeric grids (calculator keypad) - ONLY genuine exemption the owner confirmed.
- Fixed-width vertical nav rails (`MaterialButtonToggleGroup` 156dp in `dialog_playback_control` landscape).
- Full-row selectable RecyclerView item buttons where the whole row is one tap target (`item_destination_button`, `item_list_selection`, `item_sort_option`).

**NOT exempt (converted to Flow chip groups in S0605 pass 2):**
- `dialog_folder_selection` folder-shortcut weighted grid - owner wants text-sized, not half-screen tiles.
- `fragment_settings_general` export/import + backup/restore weighted pairs.

**Why:** wide landscape screens turn `match_parent` buttons into absurdly stretched single bars; owner wants visual consistency. The reference pattern already in repo is `btnCancel` in `dialog_copy_to.xml` (`wrap_content` + `layout_gravity=center`).

**How to apply:** when editing or reviewing any `res/layout*` button, apply the rubric and keep `layout/` vs `layout-land/`/`sw480dp`/`sw720dp` parity (Rule 11). A half-width weighted pair does NOT literally violate "full screen", but the owner still wants generic action-bar pairs unified to end-aligned wrap_content - only the documented/grid/nav-rail exceptions above stay. When unsure whether a stacked 2-column row is a "grid" (leave) or a "bar" (unify): uniform same-kind tiles = grid; complementary action pair (Export|Import, Cancel|OK) = bar.
