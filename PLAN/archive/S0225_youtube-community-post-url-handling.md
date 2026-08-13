# Strategic Specification: S0225 — YouTube Community Post URL Fails Silently

**Ticket:** S0225
**Status:** Verified
**Priority:** 45
**Date:** 2026-05-16

---

## 1. Problem

Sharing a YouTube Community Post URL (`http://youtube.com/post/UgkXXXXX?surface=shorts&data=...`) to the app results in a silent failure: the app returns `outcome=Other` and shows a generic error notification. The user receives no actionable feedback.

Evidence from `logs/fastmediasorter_20260516_045552.log` (line 11777–11813):

```
E/App: YtDlpExtractionStrategy: open failed url=http://youtube.com/post/UgkxGrZszixBmaE9Zbbn...
com.chaquo.python.PyException: DownloadError: ERROR: [youtube:tab] post:
    This channel does not have a UgkxGrZszixBmaE9ZbbfnKlt09p_hUj4ty55 tab
...
I/App: LinkDownloadWorker: done result=Other
D/App: S0202: MainActivity received share result ... outcome=Other notification=true
```

YouTube Community Posts are not video content. yt-dlp's `[youtube:tab]` extractor recognises the URL but cannot produce a downloadable media stream — these posts contain text, images, and polls. yt-dlp correctly reports the error; the app does not translate this into a user-facing explanation.

Secondary observation: the YouTube account stored in the app has visitor-only cookies (`VISITOR_INFO1_LIVE`, `PREF`, `GPS`, etc.) without authentication tokens (`SID`, `SSID`, `SAPISID`, `LOGIN_INFO`). The app treats this as a valid signed-in session (`[S0166] known social: host=youtube.com accounts=1`), but the session is anonymous. For Community Posts, even real auth would not enable yt-dlp download, so this is a separate concern (see §5).

---

## 2. Goals

1. When a YouTube Community Post URL (`/post/UgkX...`) is shared, the app shows an informative message: "YouTube Community Posts cannot be downloaded" (or equivalent per `docs/COMMUNICATION_POLICY.md`), instead of a generic `outcome=Other` error.
2. The error is surfaced at the earliest detection point — before yt-dlp is invoked — to avoid the 1-second RTT to yt-dlp and the confusing `DownloadError`.
3. The failure does not trigger a re-auth prompt (the issue is not auth; it is an unsupported content type).

**Optional goal (out of scope for initial implementation, capture in open questions):**

4. Extract the embedded image from the Community Post HTML (OG image / cover image) and offer it as a downloadable preview, similar to how `SocialPreviewOnly` handles Instagram posts that can't be fully extracted.

**Non-goals:**

- Downloading text, polls, or other structured data from Community Posts.
- Auth-gated Community Post access (members-only posts).

---

## 3. Constraints

- Flavor: `noLegal` (and `standard` if the URL routing is shared).
- URL detection must not false-positive on channel tab URLs that yt-dlp does support (e.g., `/channel/UCxxx/videos`).
- The `youtube.com/post/UgkX...` pattern is specific — the `Ugk` prefix distinguishes Community Post IDs from other YouTube entity IDs.
- No new BuildConfig gates in `src/main/java/`.
- Any new user-facing string requires EN/RU/UK localisation and `check_strings_localized.ps1` audit.
- Related: **S0190** (`BlockNeedUserTest`) — `nolegal-youtube-shorts-ytmusic-extraction`; the URL routing table for YouTube lives there.

---

## 4. Current Architecture Context

YouTube URL routing in `LinkAutoDownloadCoordinator` / `S0190`:

- `youtube.com/shorts/XXX` → canonicalized → yt-dlp video extraction → succeeds.
- `youtube.com/post/UgkXXX` → passes through the same yt-dlp strategy → `DownloadError` → `outcome=Other`.

The `outcome=Other` result is a catch-all for unhandled strategy failures. The notification for `Other` presumably shows a generic error. Without a URL-pattern check before dispatching to yt-dlp, the app cannot distinguish "Community Post" from "video that yt-dlp failed to fetch for network reasons."

The YouTube session cookie set (`count=7`) consists entirely of non-auth visitor cookies. The `AccountSelectionManager` reports `accounts=1`, meaning the app shows YouTube as an active account, but the account is anonymous (no login). This is a display accuracy issue tracked under §5.

---

## 5. Proposed Approach

### Primary: Early URL pattern check

- In the YouTube URL routing / pre-dispatch step, detect `/post/Ugk` pattern.
- Short-circuit before invoking any extraction strategy.
- Emit `outcome=UnsupportedContentType` (new result variant, or reuse an existing one if semantically appropriate).
- Show a user-friendly snackbar / notification: "Community posts cannot be downloaded. Open in browser to view." with a "Open" action linking to the original URL.

### Secondary: YouTube visitor-session accuracy

- In the account management UI, detect when a stored YouTube session contains only visitor cookies (no `SID`, `SSID`, `SAPISID`, `LOGIN_INFO`).
- Display a visual indicator (e.g., "Not signed in" badge) or remove the account entry from the signed-in list.
- This prevents the user from assuming they are signed in to YouTube when they are not.
- **This is a separate smaller task** — defer to a child task in the tactical spec, or open as S0226-bis if scope warrants.

---

## 6. Open Questions

1. **OG image extraction for Community Posts** — is it worth fetching the post's cover image (if any) as a fallback download?
   - **Proposal:** Defer. Community Posts are primarily text; images are optional. A follow-up spec can add this if users request it.
   - **Status:** Deferred.

2. **Visitor-session display accuracy for YouTube** — should this be part of S0225 or a separate spec?
   - **Proposal:** Separate sub-task within S0225's tactical plan (not a new spec ID). Low risk of scope creep.
   - **Status:** Open — confirm with owner before writing tactical spec.

3. **`outcome=UnsupportedContentType`** — is this a new `LinkDownloadResult` variant or is an existing one reusable?
   - **Status:** To be resolved in tactical spec.

---

## 7. Risks

- URL pattern matching for `/post/Ugk` is fragile if YouTube changes the ID format. Mitigate: match on `/post/` segment presence only (less precise but more durable), and add a log line for future tracing.
- Visitor-session detection for YouTube may have edge cases if YouTube adds new cookie names for auth. Mitigate: use an allowlist of known auth cookie names (`SID`, `SSID`, `SAPISID`, `LOGIN_INFO`, `__Secure-1PSID`) rather than blocklisting visitor cookies.

---

## 8. User Impact

No new capability — this is a quality-of-life fix (better error message for an unsupported URL type). No change to `docs/FEATURES.md`.

---

## 9. Related Specs

- **S0190** `BlockNeedUserTest` — YouTube/YtMusic extraction coordinator (routing table)
- **S0182** `BlockNeedUserTest` — sticky UA across download stacks
- **S0166** `Verified` — known-social detection and account selection

---

## Last Audit

**Date:** 2026-05-17
**Mode:** strategic
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] §6 Q2 — visitor-session display accuracy for YouTube (badge / removal of fake "signed in" entry when only `VISITOR_INFO1_LIVE` cookies are stored). Open в §6, ждёт решения владельца — отдельный sub-task в рамках S0225 либо новый тикет. Не входил в реализованный primary scope.
