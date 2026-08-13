# Strategic Specification: S0224 — Threads Batch Download Notification Shows Inflated Item Count

**Ticket:** S0224
**Status:** Verified
**Priority:** 40
**Date:** 2026-05-16

---

## 1. Problem

When downloading a Threads carousel post, the download progress notification shows an inflated item count — e.g., "12 из 12" — that does not match the number of files actually saved.

Evidence from `logs/fastmediasorter_20260516_045552.log`:

- `@_amoremio_ua_` post: `sniffEmbeddedJson unique=3` → 3 files saved, `BatchCompleted`.
- `@ustina_sergeeva_` post: `sniffEmbeddedJson unique=7` → ~7 files saved, `BatchCompleted`.
- `@claragalinari` post: `sniffEmbeddedJson unique=6` → 6 files saved, `BatchCompleted`.
- User observes "12 из 12" in the notification for a download that saved fewer files.

Hypothesis: the notification progress counter may count all asset candidates seen by the dynamic extractor before deduplication, or it accumulates across multiple successive download worker invocations without being reset, or it counts HTTP round-trips rather than file saves.

The `unique=N` values in the log are post-dedup counts. The raw scanned count (pre-dedup) is not logged. If a carousel has 6 unique full-size images but the extractor visits 12 candidate URLs (including thumbnails and duplicates of different sizes), the notification total might reflect the raw candidate count.

---

## 2. Goals

1. The "N из M" (or "N of M") count in the download notification matches the number of files actually saved to disk.
2. The counter resets cleanly per download worker invocation — no accumulation across separate share events.
3. The fix applies to Threads carousels and to any other batch source using the same notification path.

**Non-goals:**

- Changing the visual design of the notification.
- Progress tracking granularity below "per file saved" — per-chunk progress is out of scope.

---

## 3. Constraints

- Flavor: `noLegal` (download features).
- The notification is updated from `LinkDownloadWorker` or `LinkDownloadWriter` — the fix lives in whichever component drives the notification progress counter.
- The counter must be accurate before any file is saved (so the user sees "1 of 6" not "1 of 12" from the first file).
- Related spec: **S0181** (`Verified`) — Threads image carousel scraper. The batch result path was introduced there; the notification counter may have been an edge case not covered.

---

## 4. Current Architecture Context

The download flow:

1. `DynamicExtractionStrategy` runs the embedded browser, `sniffEmbeddedJson` harvests candidate URLs, deduplication yields `unique=N` assets.
2. The batch of N assets is passed to `LinkDownloadWriter` for sequential download and save.
3. `LinkDownloadWorker` updates a foreground notification with progress (suppressed in this build due to `WAKE_LOCK` permission denial, but notification is still posted via a non-foreground path).

The exact source of the "12 из 12" total is unknown without reading the notification construction code. Candidates:
- Pre-dedup candidate list size passed to the notification constructor.
- Cumulative counter not reset between `batch=0` worker runs.
- Each HTTP request counted as a progress unit (the extractor issues multiple requests per carousel).

The notification's `total` field is set before downloads begin, so the resolution requires identifying where `total` is computed and ensuring it uses the post-dedup `unique` count.

---

## 5. Proposed Approach

1. Research spike: log the notification `total` value at construction time to confirm the discrepancy source.
2. Trace the value from `sniffEmbeddedJson unique=N` through the batch-result handoff to `LinkDownloadWorker` notification setup.
3. Ensure the notification `total` is set to the post-dedup count (`unique=N`), not raw candidate count.
4. Add a log line: `LinkDownloadNotification: set total=N` at the point the notification is constructed, to allow future verification without device-side testing.

---

## 6. Open Questions

None. This is primarily a research + small fix ticket; the tactical spec will confirm the root cause.

---

## 7. Risks

- Low. The counter is display-only; incorrect values do not affect actual downloaded files.
- If the total is computed correctly but the pre-dedup path is intentional (e.g., "we are fetching 12 things, 6 will succeed"), the fix may require a two-phase notification: show "pending" state until dedup completes, then show accurate total. This adds complexity; assess in tactical spec.

---

## 8. User Impact

UI polish only — no change to downloaded content. No `docs/FEATURES.md` update needed.

---

## 9. Related Specs

- **S0181** `Verified` — Threads image carousel scraper (introduced batch path)
- **S0197** `BlockNeedUserTest` — `sniffEmbeddedJson` extractor (provides the `unique=N` count)

---

## Last Audit

**Date:** 2026-05-17
**Mode:** strategic
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [ ] §11 — устройство: убедиться, что на Threads-карусели уведомление показывает корректное «N из M» (равно `batch.items.size`), а не raw candidate count. Static-аудит подтверждает источник totalItems из post-dedup batch; визуальное подтверждение остаётся за пользователем при следующем шаринге.
