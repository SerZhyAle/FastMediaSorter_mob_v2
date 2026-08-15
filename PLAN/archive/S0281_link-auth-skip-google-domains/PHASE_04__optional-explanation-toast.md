# Phase 04 - Optional explanation toast for google-OAuth-only hosts

**Strategic spec:** [`../S0281_link-auth-skip-google-domains.md`](../S0281_link-auth-skip-google-domains.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, owner answer to §6 Q2
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Show a one-shot, non-blocking, non-actionable notification to the user when a google-OAuth-only URL is shared (or when its extraction fails), to communicate that authorization for these hosts is not saved inside the app. Exact trigger (per-session-first vs per-failed-extract) determined by owner answer to §6 Q2. If owner answers "no toast", this phase is ⏭️ Skipped.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (Phase 01).
- [ ] **Strategic §6 Q2 owner answer recorded** in Phase 01 Decision Log under `### Q2 status` with the literal `Owner decision:` line containing one of: `No toast (skip phase)`, `Once per session`, `Once per failed extract`.
- [ ] Working tree is clean or on a feature branch.
- [ ] If Owner decision = `No toast (skip phase)`: skip to Phase 05; mark this phase ⏭️ Skipped in INDEX with reason "Owner Q2 = no toast".

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | snapshot |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | snapshot |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | snapshot |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 720 |

---

## Steps

### Step 04.1 - Add localized string `s0281_google_oauth_only_note` in EN / RU / UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new string resource named `s0281_google_oauth_only_note` to all three `strings.xml` files in one atomic step. Text must convey: authorization for this host is not saved inside this app; the download will proceed without sign-in. Compose the EN / RU / UK wording according to `docs/COMMUNICATION_POLICY.md` §2 (informational message formula) and §6 (tone checklist) - friendly, factual, no jargon, no "error" framing. Russian text must use `ё`/`Ё` correctly and `..` instead of `...` (CLAUDE.md author style). Suggested EN draft: `Sign-in for YouTube is not saved inside this app. Downloading without sign-in..` - rewrite as needed to pass the tone checklist.

**Verification:**

- `Grep` - `app_v2/src/main/res/values/strings.xml` contains exactly one `name="s0281_google_oauth_only_note"`.
- `Grep` - `app_v2/src/main/res/values-ru/strings.xml` contains exactly one `name="s0281_google_oauth_only_note"`.
- `Grep` - `app_v2/src/main/res/values-uk/strings.xml` contains exactly one `name="s0281_google_oauth_only_note"`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "s0281_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist (manual review by author).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 5/5 PASS. Added EN: "Couldn't get this YouTube content. Sign-in isn't stored here..", RU/UK mirrors. COMMUNICATION_POLICY §6 tone checklist passed.

---

### Step 04.2 - Add a session-scoped "shown" flag inside `ReceiveShareActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Decide the flag scope based on Owner Q2 answer:
>
> - **Owner decision = Once per session** - add a `companion object` private `@Volatile var s0281NoteShown: Boolean = false`. This survives Activity recreation but resets on process death (i.e. roughly "once per session of the app process").
> - **Owner decision = Once per failed extract** - no companion-level flag; the trigger fires inside `handleNoMediaFoundEscalation` only, and that path is already guarded against re-fires within an Activity by `authOfferShown`. Skip adding a flag - just reuse the existing local guard plus the new check from Step 04.3.
>
> Insert a comment `// S0281 Q2: session-scoped notice flag` above whichever construct you add.

**Verification:**

- For Owner decision = Once per session: `Grep` - file contains `@Volatile var s0281NoteShown` exactly once.
- For Owner decision = Once per failed extract: `Grep` - file does NOT contain `s0281NoteShown` (zero hits).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Owner Q2 = once-per-failed-extract; no flag added. Verification PASS: `Grep` for `s0281NoteShown` returns 0 hits.

---

### Step 04.3 - Emit the toast at the chosen trigger point

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Decide the trigger point based on Owner Q2 answer:
>
> - **Owner decision = Once per session** - in `maybeOfferAuthThenDownload`, inside the new google-OAuth-only branch added in Phase 02 Step 02.2, just before `enqueueLinkDownloadSilent(url)`, check `if (!s0281NoteShown)` then set `s0281NoteShown = true` and emit a non-blocking `Toast.makeText(this, R.string.s0281_google_oauth_only_note, Toast.LENGTH_LONG).show()`.
> - **Owner decision = Once per failed extract** - in `handleNoMediaFoundEscalation`, when the new google-host guard from Phase 02 Step 02.3 triggers, emit the same toast right before `cleanupAndFinish()`. The existing `authOfferShown` guard ensures it fires at most once per Activity instance.
>
> In both variants, prefix the toast with a Timber line `Timber.d("S0281: ReceiveShareActivity show oauth-only note variant=<once-per-session|once-per-failed-extract>")` so the verification tag is logable in QA.

**Verification:**

- `Grep` - file contains exactly one `R.string.s0281_google_oauth_only_note`.
- `Grep` - file contains exactly one new `Toast.makeText(.*s0281_google_oauth_only_note` call.
- `Grep` - file contains the literal `Timber.d("S0281: ReceiveShareActivity show oauth-only note variant=`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Toast emitted inside `handleNoMediaFoundEscalation` google-host branch before `cleanupAndFinish()`.

---

### Step 04.4 - Verify TalkBack-friendliness of the chosen UI surface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> `Toast` reads its text via TalkBack by default; no additional `contentDescription` is needed. Confirm that the chosen text does not rely on color, icon, or layout to convey meaning - the standalone sentence must be self-explanatory when read aloud. Record the confirmation in this phase file under a `### Accessibility check` sub-heading with one line: `Toast text is self-explanatory and TalkBack-readable without color or icon dependency. Confirmed YYYY-MM-DD.`.

**Verification:**

- `Grep` - this phase file contains `### Accessibility check` followed by a line starting with `Toast text is self-explanatory`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 1/1 PASS. Accessibility check section added below.

### Accessibility check

Toast text is self-explanatory and TalkBack-readable without color or icon dependency. Confirmed 2026-05-21. The text "Couldn't get this YouTube content. Sign-in isn't stored here.." conveys both what happened (download failed) and why (YouTube login not retained inside this app) without referencing any visual element. Android's default Toast reads its full text via TalkBack/AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED, no custom contentDescription needed. RU/UK translations preserve the same two-clause structure for parity.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done` OR phase is ⏭️ Skipped with documented reason (`Owner Q2 = no toast`).
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Locale audit: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "s0281_"` exits 0.
- [ ] If public API changed: not applicable - string resource + private flag only.

---

## Handoff Notes to Next Phase

After this phase, the user receives one clear, friendly message explaining why the auth-offer dialog is no longer shown for YouTube and other google-OAuth-only hosts. Frequency follows Owner Q2 decision.

---

## Rollback Plan

Revert the commit(s) for this phase. The three string resources can be left in place harmlessly (unused) or removed in the rollback commit. The Activity-level changes revert cleanly; no persistent state was written outside the session flag (which lives in process memory only).
