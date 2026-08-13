# Phase 6 — Device verification

**Status:** TODO

## Goal

Verify all Done Criteria from strategic spec §11 on TV emulator (Android TV API 31+).

## Verification checklist

- [ ] WelcomeActivity: navigate all pages with DPAD_RIGHT/LEFT/ENTER; no touch required
- [ ] WelcomeActivity: initial focus on btnNext when Activity opens on TV
- [ ] WelcomeActivity: DPAD_CENTER activates visible Next/Finish button
- [ ] SettingsActivity: DPAD UP/DOWN moves focus through preference items
- [ ] MainActivity / BrowseActivity: DPAD navigation unchanged (existing)
- [ ] PlayerActivity: DPAD arrows still seek (not stolen by new base router)
- [ ] Phone (non-TV): no visible focus ring change on Welcome; buttons still respond to tap
- [ ] Gamepad on 4 existing screens: behaviour unchanged

## Deferred open questions (from strategic spec §6)

- §6.1 — RecyclerView free DPAD: confirmed by code review for list-based screens; to be confirmed on device.
- §6.2 — Player DPAD conflict: verified by architecture analysis (PlayerActivity overrides dispatchKeyEvent first); confirm on device.
- §6.3 — TV mode detection reliability: verify on emulator; MX700 deferred (device not available in automated run).
- §6.4 — Mouse blocking: code review found no hard blockers; confirm on emulator with USB mouse.
- §6.5 — TalkBack dialog focus: deferred — accessibility audit is a separate pass.
- §6.6 — Voice Access custom views: deferred — accessibility audit is a separate pass.
