# Research 04 - Messenger recipient selection feasibility

**Strategic spec:** [`../../S0459_unified-send-to-menu.md`](../../S0459_unified-send-to-menu.md) §6 (item 4), §2 non-goal, ADR-9
**Status:** Resolved
**Date:** 2026-06-16
**Method:** web research on WhatsApp / Instagram share-intent capabilities.

---

## Question

Can WhatsApp / Instagram receive a file targeted at a specific recipient programmatically, or only "open the app with the attachment"?

## Findings

- **WhatsApp:** `ACTION_SEND` + `EXTRA_STREAM` + `setPackage("com.whatsapp")` opens WhatsApp's own contact picker - the user chooses the recipient. Direct-to-contact requires the **undocumented** `jid` extra (`"<phone>@s.whatsapp.net"`) or the `wa.me` / `api.whatsapp.com` URL scheme (text only, no file attachment). The `jid` route is unofficial, fragile across versions, and ToS-sensitive.
- **Instagram:** accepts only `image/*` or `video/*` via `ACTION_SEND` + `setPackage("com.instagram.android")`; routes into Instagram's own share flow (Story/feed/DM picker). No intent-level recipient selection. Documents/audio are rejected.
- General: file-to-specific-contact is not an officially supported intent contract on either app.

## Decision

- Do **not** attempt programmatic recipient selection. Confirms the existing §2 non-goal and §6 default.
- Send action for both = `ACTION_SEND` (single) / `ACTION_SEND_MULTIPLE` (multi) with `EXTRA_STREAM` + `FLAG_GRANT_READ_URI_PERMISSION`, `setPackage(<messenger>)`, falling back to the system chooser when the package is absent - i.e. reuse `SystemShareInvoker.invokeFiles`. The messenger app handles recipient choice.
- Reject the `jid` trick (undocumented / breakage / ToS risk).
- **Instagram applicability** = `{IMAGE, VIDEO, GIF}` (feeds research 02 `applicableTypes`). WhatsApp applicability = any.

## Spec impact

- §7 risk "мессенджеры ограничивают программный шаринг (Высокая)" resolved with the documented fallback already in the spec.
- WhatsApp / Instagram receiver registrations reuse the existing invoker; only package ids + `<queries>` entries + applicability differ. No new send infrastructure.
- Manifest `<queries>` must list `com.whatsapp` (+ `com.whatsapp.w4b` WhatsApp Business) and `com.instagram.android` (API 30+ package visibility) - feeds the `<queries>` pillar (§5.1).
- The package ids are already referenced in `AddResourceScanManager` (as media-scan folder paths), confirming no existing SHARE code for either app - registration is greenfield, and the ids are known-good.
