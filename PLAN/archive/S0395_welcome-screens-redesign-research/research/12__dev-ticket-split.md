# 12 - Dev-Ticket Split Proposal

Strategic item: S0395 §6.12. Phase: 05, step 05.2.

> **Owner amendments 2026-06-10 (sign-off):** Skip button removed entirely (replaces the Skip-semantics fix in C); networks page ships DECORATIVE in C (3 tiles: SMB (intranet), (S)FTP, Cloud) and ticket H is FOLDED into S0391 as a replace-tiles-with-toggles phase; ticket I (upgrade pointer) DROPPED; D uses full info-rich tiles with minimal margins, small-screen profiles ordered first, auto-scroll to the pre-selected recommended tile.

## Question

How is the implementation sliced into dev tickets, with what dependencies and order, so that nothing waits without need?

## Sources

- `SYNTHESIS.md` (recommended structure, deviations), all artifacts `research/01-11`

## Findings

### Candidate tickets

- **A. `welcome-availability-contract`** - runtime availability interfaces for onboarding items (OCR, translation, VR, extensions visibility; pattern: `DeviceProfileAvailability` + per-source-set Hilt modules); retires the `ENABLE_TRANSLATION` BuildConfig reads from shared settings code (Rule 15 debt). Depends on: nothing. New strings: none. Comm-policy: n/a.
- **B. `onboarding-download-runner`** - app-scoped, process-surviving download queue (WorkManager-class) over existing `DeliverableSetDownloader` primitives; idempotent enqueue (installed-state aware); re-attachable inline-progress API; network constraint + retry. Depends on: nothing technically (primitives exist; S0386 is BlockNeedUserTest - see release gate below). New strings: ~6 (progress/pending/failed states), EN/RU/UK lockstep; copy passes COMMUNICATION_POLICY §6.
- **C. `welcome-skeleton-form-pages`** - page-list-as-data + collapse rule + "step X of N" indicator; Skip button REMOVED entirely (Next-only flow; untouched pages keep safe defaults, AUTO_SKIPPED-equivalent source for untouched profile); re-entry fixes (preset only on profile change + warning, no CLEAR_TASK); removal of 4 decorative pages + dead paths (PERMISSIONS view type, layouts, orphan strings, write-only pref); NEW decorative networks page - 3 tiles SMB (intranet), (S)FTP, Cloud (upgraded to toggles by S0391's added phase); touch-zones first-player-launch hint; "up to 30"→10 string fix; page 0 = language + theme rows (dual-write theme, applies-after-setup copy). Depends on: nothing. New strings: ~13. Comm-policy gate applies.
- **D. `welcome-profile-page`** - dedicated page 1: full info-rich tiles (icon+title+description) with minimal margins, small-screen profiles ordered first, auto-scroll to the pre-selected recommended tile, recommended badge, direct refresh hook (VP2 rebind workaround), D-pad row-edge key consumption. Dialog stays for Settings. Depends on: C. New strings: ~3.
- **E. `welcome-functionality-page`** - page 3 toggles (file-manager, audio, video, documents-master, OCR, translation, VR where available) + Extensions button + download wiring (enqueue-on-flip, inline progress) + "preset first, toggles render post-preset defaults" precedence. Depends on: A, B, C (build); S0386 `Verified` (release gate); F for the OCR/FFmpeg toggles on Play-acquired standard. New strings: ~12 incl. unavailability reasons.
- **F. `s0386-play-compliant-so-delivery`** (S0386 follow-up, not a welcome ticket) - Play-compliant `.so` delivery for store builds: dynamic-feature modules like translation, or installer-origin gate (direct download only on non-Play installs), or Play Asset Delivery. Also: per-flavor Extensions inventory filtering (lite/photos doomed rows). Depends on: S0386 `Verified`. Blocks: E's OCR toggle on standard only.
- **G. `welcome-permissions-page`** - permissions overlay → pager page; parameterized permission set for welcome mode (adaptive to profile/functionality choices; Settings mode unchanged); creates the missing landscape layout. Depends on: C. New strings: ~2.
- **H. FOLDED INTO S0391** (owner amendment): the decorative networks page ships in C; S0391's scope gains a phase replacing the 3 tiles with group toggles bulk-writing per-source states. No standalone welcome ticket; no `BlockByOtherTask` needed anywhere in the welcome lineage.
- **I. DROPPED** (owner decision): no upgrade pointer - upgraders reach the new flow only via the existing Settings entry.

### Dependency picture

- Independent starters: A, B, C (and F as soon as S0386 is Verified).
- C unblocks D and G; A+B+C unblock E; the networks-page toggle upgrade lives inside S0391 itself.
- No welcome ticket carries a `BlockByOtherTask`. E is created unblocked but its release on standard is gated by S0386 verification + F; record both in E's §10.
- Nothing else waits: lite/photos/legacy/noLegal builds of C/D/G are unaffected by F (the Play gate is standard-Play-specific).

### Recommended creation and implementation order

1. C (skeleton) - the keystone; largest cleanup payoff immediately; includes the decorative networks page.
2. A (availability contract) - parallel to C, no file overlap.
3. D and G - after C, parallel to each other.
4. B - parallel to D/G.
5. E - after A+B+C; device-test together with D/G.
6. F - S0386 follow-up on its own track; required before E's OCR toggle ships on standard-from-Play.
7. S0391 (existing ticket) - gains the tiles→toggles welcome phase; implemented on its own schedule.

### Estimated localization volume

~30 new keys total EN/RU/UK lockstep (per-ticket counts above); every user-visible string passes `docs/COMMUNICATION_POLICY.md` §6 tone checklist before integration (`set-android-string.ps1 -Action add` for lockstep parity).

## Conclusion

Seven tickets (post-amendment): A/B/C start immediately and in parallel; C is the keystone (skeleton, cleanup, Skip removal, page 0, decorative networks page); D/G follow C; E assembles the functionality page on A+B+C and is the only ticket gated by S0386 verification (and by F for Play-standard OCR); the networks-page toggle upgrade is a phase inside S0391, keeping it entirely off the welcome critical path; the upgrade pointer is dropped. This slicing keeps every ticket independently shippable and isolates the Play-policy fix in the S0386 lineage where it belongs.

## Impact on recommendation

- Ticket creation happens only after owner sign-off of `SYNTHESIS.md` (strategic §11.4); slugs above are ready for `insert.ps1`.
- S0395 §10 gains the explicit dependency note for E/F/H when tickets are created.
