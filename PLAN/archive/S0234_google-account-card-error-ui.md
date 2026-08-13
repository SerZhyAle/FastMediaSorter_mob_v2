# Strategic Specification: S0234 — Google Account Settings card: surface sign-in errors to the user

**Ticket:** S0234
**Status:** BlockNeedUserTest
**Priority:** 70
**Date:** 2026-05-17

**Tactical plan:** `PLAN/S0234_google-account-card-error-ui/INDEX.md`

---

## 1. Problem

When a user taps the "Sign in" button on the Google Account Settings card and the sign-in fails (for any reason — `PlayServicesOutdated`, `NetworkError`, `UnknownError`), the card silently reverts to the same `Unbound` visual state. There is no toast, no inline error text, no diagnostic. The button text reads the same, the summary text reads the same. From the user's perspective the button "does nothing".

Log evidence (`logs/fastmediasorter_20260517_003023.log`, lines 1040..1060): seven taps on the Settings area at coordinates that correspond to the Google Account card region. No corresponding `CLICK: …` log lines because `GoogleAccountSettingsHelper` does not wrap the click with `UserActionLogger.wrapClickListener` — confirming that nothing user-visible happened, even though the click handler likely fired and the underlying sign-in attempt produced the same `PlayServicesOutdated` failure seen at 00:30:31 in `AddResourceActivity`.

Reading `GoogleAccountSettingsHelper.render()` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GoogleAccountSettingsHelper.kt:55..122`):

- `is PrimaryGoogleAccountState.Error -> { ... setText(R.string.s0200_card_state_unbound_summary); ... setText(R.string.s0200_card_state_unbound_cta) ... }` — identical to the `Unbound` branch.

The `IdentityFailureReason` enum (`PlayServicesOutdated`, `CctUnavailable`, `UserCancelled`, `NetworkError`, `UnknownError`) is carried inside the state but not consulted by the renderer.

---

## 2. Goals

1. Every `PrimaryGoogleAccountState.Error` transition produces a visible response: either an inline card surface change (summary text + secondary action) or a transient indicator (snackbar) — owner UX picks the format.
2. The shown message is specific to the `IdentityFailureReason` — at minimum `PlayServicesOutdated` and `NetworkError` get distinct copy. `UnknownError` falls back to a generic "Sign-in failed — try again later" with optional diagnostic line if Diagnostics toggle is on.
3. The card recovers cleanly: tapping the action button after an Error attempts sign-in again (no need to relaunch Settings).
4. `UserActionLogger.logButtonClick("GoogleAccountAction", "Settings")` fires on every action-button tap, so future log captures show whether the click reached the handler.

**Non-goals:**

- Implementing the technical fallback (Play Services repair, legacy GoogleSignIn) — that's S0233.
- Changing the card layout structure (`card_google_account.xml`) beyond adding/repurposing existing TextView slots.
- Touching the Drive resource list — error indication on individual resources is the existing `needs_sign_in` flag.
- Localization beyond EN/RU/UK (default policy).

---

## 3. Constraints

- **Communication policy:** all new strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist (friendly tone, action-orientation, clarity of next step).
- **Locale audit:** every new `strings.xml` key added to EN/RU/UK in the same commit; `scripts/check_strings_localized.ps1 -KeyPrefix "s0234_"` must pass.
- **Card layout:** reuse existing slots in `card_google_account.xml` where possible — `tvAccountSummary` for the message, `tvDiagnosticsLine` for the diagnostic detail (already gated by the Diagnostics toggle), no new widgets unless the tactical spec proves they're required.
- **Accessibility:** error state must be readable by TalkBack (not colour-only).
- **No new domain types:** the `IdentityFailureReason` enum stays as-is. If S0233 adds new variants (`PlayServicesUnavailable(diagnostic)`), this spec adopts them without further enum work.
- **No state leak:** Error state must clear on the next successful sign-in attempt; the card returns to `Bound` summary without stale error copy.

---

## 4. Current Architecture Context

`GoogleAccountSettingsHelper.render()` switches on `ui.state` (`PrimaryGoogleAccountState`). The `Error` branch is a verbatim copy of the `Unbound` branch — no use of `state.cause`. The card hosts: title, summary, email line (gone in Unbound/Error), avatar (gone), action button, progress, diagnostics toggle, diagnostics line.

`GoogleAccountSettingsViewModel.UiState` (`app_v2/src/main/java/.../ui/settings/GoogleAccountSettingsViewModel.kt:35..39`) carries `state`, `cctPackage`, `showDiagnostics`. There is no transient event channel (no `Channel<UiEvent>` / `SharedFlow<Effect>`) — only the durable state. The dialog approach (Decision D2) requires adding such a channel; a `Channel<Event>(Channel.BUFFERED)` exposed as `Flow<Event>` is sufficient and matches the rest of the codebase's Kotlin Flow conventions.

`UserActionLogger.wrapClickListener` is the project's standard click telemetry helper. Other settings cards use it; this one does not — a small consistency gap.

`com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(context, title, message, details, actionButtonText, onActionClick)` already exists and is the canonical "rich error" UI primitive across the app. It handles Activity finishing/destroyed safely, ships Copy / Share / Save-to-file built in, and supports a custom positive button replacing Share. This spec reuses it (Decision D2) rather than building a new transient surface.

---

## 5. Proposed Approach

Three orthogonal pieces, locked here for tactical:

**A — Per-reason copy + reason-driven action button text on the card.**

Map every `IdentityFailureReason` to:

- A summary string `R.string.s0234_card_state_error_<reason>_summary` shown in `tvAccountSummary`.
- A CTA string `R.string.s0234_card_state_error_<reason>_cta` shown on `btnAccountAction`.

Reason → button text contract (locked by Decision D1):

| `IdentityFailureReason` | Button text (EN) | Tap behaviour |
|-------------------------|------------------|---------------|
| `PlayServicesOutdated` | "Update Play Services" | Open Play Store to `com.google.android.gms` (use existing helper if any; otherwise `Intent.ACTION_VIEW` to `market://details?id=com.google.android.gms`) |
| `NetworkError` | "Retry" | Re-trigger `signInPrimary` |
| `UserCancelled` | "Sign in" | Re-trigger `signInPrimary` (card visually returns toward Unbound — see Decision D3) |
| `CctUnavailable` | "Sign in" | Re-trigger `signInPrimary` |
| `UnknownError` | "Try again" | Re-trigger `signInPrimary` |

**B — One-shot `ErrorDialog` on Error transition (Decision D2).**

Reuse the existing `com.sza.fastmediasorter.ui.dialog.ErrorDialog` (full-featured: title, message, optional collapsible details, built-in Copy / Share / Save-to-file actions, optional positive button replacing Share). On every transition INTO `Error`, fire a one-shot event from `GoogleAccountSettingsViewModel` consumed by `SettingsFragment`/`GoogleAccountSettingsHelper`, which calls `ErrorDialog.show(..)` with:

- `title` = `R.string.s0234_error_dialog_title` ("Google Drive sign-in failed").
- `message` = the per-reason summary string from (A) — same copy as the card.
- `details` = `state.cause.name` + cause exception message (if `state.detail` exists), passed as the collapsible details string.
- `actionButtonText` = per-reason CTA from (A).
- `onActionClick` = the same retry/Play-Store action as the card button.

Mechanics:

- ViewModel adds `private val _events = Channel<Event>(Channel.BUFFERED)` exposed as `events: Flow<Event>`. `Event.ShowSignInError(reason: IdentityFailureReason, detail: String?)`.
- ViewModel emits on every fresh `state` value of type `Error` (not on every emission of the same value — distinct by state instance / cause is enough; tactical decides the exact distinct-until-changed predicate).
- Fragment collects `events` in `viewLifecycleOwner.lifecycleScope` and calls `ErrorDialog.show(..)`. Dialog suppression on `Activity.isFinishing/isDestroyed` is already inside `ErrorDialog.show`.
- Rotation: collected events are not replayed by a `Channel`, so the dialog does not pop up again on rotation — matches expected one-shot semantics.

**C — Click telemetry.**

Wrap `btnAccountAction.setOnClickListener` with `UserActionLogger.wrapClickListener("GoogleAccountAction", "Settings")`. Same for `btnDiagnosticsToggle`. Independent of A/B; ships in the same patch.

**ADR notes for tactical:**

- ADR-1: Per-reason copy (A) lives in the existing summary slot — no new TextView; keeps card layout footprint stable.
- ADR-2: Error surfacing uses the existing `ErrorDialog` (Decision D2) rather than a new snackbar or auto-shown diagnostics line. Reuses copy/share/save plumbing; no new transient-event UI primitive needed.
- ADR-3: Action button text varies by `IdentityFailureReason` (Decision D1). The card is not just visual — its CTA leads the user toward the actual remedy (e.g., updating Play Services), not toward a retry that will fail the same way.
- ADR-4: `UserCancelled` produces a short message in the dialog ("Sign-in cancelled — tap Sign in to try again") (Decision D3). The card itself still renders in Error state with the per-reason copy from (A) until the next interaction.

---

## 6. Decisions

1. **D1 — Action button text varies by reason.** PlayServicesOutdated → "Update Play Services" (opens Play Store), NetworkError → "Retry", UserCancelled / CctUnavailable → "Sign in", UnknownError → "Try again". Full mapping in §5 / (A). Rationale: a one-size CTA cannot lead to the correct remedy for `PlayServicesOutdated`; mapping per reason is the smallest honest UX.
2. **D2 — Use existing `ErrorDialog` for error surfacing.** No tap-to-copy on an inline diagnostics line; the dialog already provides Copy / Share / Save-to-file. ViewModel emits a one-shot event on each Error transition; Fragment shows the dialog. Rationale: zero new UX primitives, reuse of existing infrastructure that already handles activity lifecycle safely.
3. **D3 — `UserCancelled` shows "Sign-in cancelled" in the dialog.** The card displays the same copy in `tvAccountSummary` with the "Sign in" CTA. Rationale: confirms to the user that the system registered the cancellation — closes the "button does nothing" perception even though the cause is the user's own dismissal.

---

## 7. Risks

- **Low** — text-only change, no logic refactor.
- **Locale parity** — easy to forget UK translation; mitigated by step 4 (post-change locale audit).
- **Style consistency** — error tone must match `COMMUNICATION_POLICY` ("friendly + actionable"). Wording reviewer pass needed.

---

## 8. User Impact

User sees why Google Drive sign-in failed and what to do about it.

- **EN:** Google Drive sign-in errors now explain what went wrong (Google Play Services outdated, no internet, cancelled) and how to retry.
- **RU:** Ошибки входа в Google Drive теперь объясняют, что пошло не так (устаревшие Google Play Услуги, нет интернета, отменено) и как повторить попытку.
- **UK:** Помилки входу в Google Drive тепер пояснюють, що пішло не так (застарілі Google Play Послуги, немає інтернету, скасовано) і як повторити спробу.

Bug-adjacent UX improvement; `docs/FEATURES.md` not updated. `dev/FUNCTIONALITY.log` gets a CHANGE entry once shipped.

---

## 9. Related Specs

- **S0200** `Implemented` — Central Google account binding. Defined the card, the state machine, the renderer. This spec fills the missing UX gap on `Error`.
- **S0233** `Draft` (parallel) — Technical fallback for Credential Manager failures. The two together close the loop: S0233 makes more sign-ins succeed; S0234 makes failures legible.
- **S0118 / `docs/COMMUNICATION_POLICY.md`** — Tone/text checklist applied to every new string.
