# Research 05 - Email send action (attachment)

**Strategic spec:** [`../../S0459_unified-send-to-menu.md`](../../S0459_unified-send-to-menu.md) §6 (item 5)
**Status:** Resolved
**Date:** 2026-06-16
**Method:** web research on Android email-with-attachment intent contracts; cross-check `SystemShareInvoker`.

---

## Question

How should the Email receiver attach the current file - `mailto:` (`ACTION_SENDTO`) or `ACTION_SEND` with `message/rfc822`?

## Findings

- `ACTION_SENDTO` (`mailto:`) does **not** carry attachments. `EXTRA_STREAM` is ignored; the subject/text extras only ever worked via an undocumented AOSP-Email/Gmail behaviour, never guaranteed.
- For an attachment, the correct contract is `ACTION_SEND` (one file) / `ACTION_SEND_MULTIPLE` (many) with `EXTRA_STREAM` + `FLAG_GRANT_READ_URI_PERMISSION`, plus `EXTRA_EMAIL` / `EXTRA_SUBJECT` / `EXTRA_TEXT`.
- Using MIME `message/rfc822` biases the chooser toward email clients (some non-mail apps may still appear - acceptable; a chooser title disambiguates).

## Decision

- Email receiver = `ACTION_SEND` / `ACTION_SEND_MULTIPLE` with the file's real MIME (or `message/rfc822` to bias toward mail apps) + `EXTRA_EMAIL` (empty - user fills recipient) + `EXTRA_SUBJECT`. Reuse `SystemShareInvoker.invokeFiles`; add the email extras at the call site (small additive overload or extras bundle).
- Do **not** use `mailto:` for the file-attach path. `mailto:` stays only for the no-attachment bug-report (`SupportIntentFactory.reportProblem`), which is OUT of this menu (ADR-6).
- Applicability = any type.

## Spec impact

- Resolves the inherited S0444 email-action note in §6.
- Email receiver registration reuses the existing invoker - no new send infrastructure; only the extras + a `<queries>` entry for common mail packages / `mailto` scheme so the chooser/availability resolves on API 30+.
