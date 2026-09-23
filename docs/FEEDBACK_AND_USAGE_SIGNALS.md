# Feedback and usage signals - how this app hears from users and what it counts

**Last reconciled:** 2026-09-22
**Contract:** canon `rules/SUPPORT_AND_FEEDBACK.md` section 7 (what a product may count about its users).
**Sibling inventory:** `docs/SECURITY_POSTURE.md` - what the app can touch and what it can send.

The loop back from users runs on three tiers here: **contact**, **diagnostics** and **counting**. The first two
are a human act every time. The third runs without one, and this page is where its boundary is written down, so
that the public promise in `docs/PRIVACY_POLICY.md` (+ `-ru`, `-uk`) is a render of something checkable rather
than of someone's memory.

Scope: `app_v2` (seven flavors) and `wear`. The watch has no support channel and no counter of its own - a watch
report travels out inside the phone's log archive (`LogExportHelper.buildLogsZip`), and the phone is the only
place any of the three tiers exists.

---

## 1. Contact tier

`app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt` is the single factory;
no call site holds a URL or an address of its own.

| Channel | Destination | Started by |
|---|---|---|
| Help | the locale-aware how-to page on the docs site | the user opening help from settings, a dialog or an error surface |
| Report a problem | an `ACTION_SENDTO` mail intent, subject pre-filled with the product name and version | the user choosing it |
| Leave feedback | the Play listing (`market://`, with a web fallback when the Play app is absent) | the user choosing it |
| Crash report | an `ACTION_SEND` mail intent, optionally carrying the log archive | the user answering the crash prompt |

Every one of the four is an intent handed to an app the user picks, and the send is confirmed in that app. Nothing
here transmits on the product's own initiative.

**One declared address (contract §7.4).** All four mail paths read `SupportIntentFactory.SUPPORT_EMAIL`
(`sza@ukr.net`, the address the privacy policy and the site already publish), and each subject names the product
and its version through `SupportIntentFactory.versionedSubject`.

---

## 2. Diagnostics tier - pull and manual

- **On the device:** `core/logging/LogExportHelper.kt` packs the app's log files (and any watch report sitting
  beside them) into one cache archive and hands it to the system share sheet. Nothing is uploaded.
- **On the developer's side:** the archive reaches a locally synced drop folder; `scripts/utils/import-remote-logs.ps1`
  imports it, deduplicating by SHA-256 of the content rather than by filename, and `/newlog` drives the intake
  while `/log-reader` turns findings into tickets - one ticket per distinct problem, deduplicated by symptom.

The channel is **pull and manual** in both halves. There is no receiving service, no background upload and no
app-initiated intake; introducing one would change the privacy promise and is a capability with its own decision,
not a wider reading of this page.

---

## 3. Counting tier, and its boundary

Two stores, split exactly along the consent line. Both live on the app's shared `DataStore<Preferences>`; neither
is a database and neither leaves the device on its own.

### 3.1 The always-on basis

`data/local/preferences/StatsBaselineDataStore.kt` - four values, written on every launch regardless of any
toggle, because a first-run moment cannot be reconstructed after the fact:

- `stats_baseline_first_launch_epoch` - when the app first ran;
- `stats_baseline_first_install_version` and `stats_baseline_first_install_flavor` - what was installed then;
- `stats_baseline_launch_count` - how many times it has run.

First write wins for the three install facts; later launches never overwrite them.

### 3.2 The detailed half, behind consent

`data/local/preferences/StatsAggregateDataStore.kt`, fed by `data/stats/StatsSinkImpl.kt`, which holds the
`enableStatistics` setting as a cached flag and **returns immediately when collection is off** - a disabled sink
records nothing rather than recording and hiding. Enabled, events fold into an in-memory delta and a debounced
flush writes the whole batch in one operation, so a thousand-file move is one disk write and not a thousand.

What it counts is the completion of operations the user performed: files copied, moved, deleted, renamed and
archived with their byte totals, captures, views and listening time, edits, sources connected, sessions - the
`StatsKey` enum in `domain/stats/StatsModels.kt` is the full list, plus a per-media-type matrix of copy/move/delete.
All values are all-time totals; there are no period buckets.

### 3.3 The boundary itself

- **Withdrawal deletes.** Turning collection off calls `StatisticsRepository.wipeDetailed()`, which erases the
  detailed store and leaves the basis standing.
- **The screen exists only while collection is on.** The settings row that opens `StatisticsActivity` is bound to
  `enableStatistics` and disappears with it, so the screen is never offered over data that is not being kept.
- **No identifier links two runs.** Nothing in either store is a user, device, install or session id; the basis
  holds a timestamp, two build strings and a count, and the detailed half holds integers.
- **Data leaves only by a gesture.** `ui/statistics/StatisticsReportShareManager.kt` builds a plain-text report and
  offers two actions: mail it to the author, or export it through a chooser. Both are `ACTION_SEND` intents the user
  confirms in their own app. The report carries the app version, flavor, device model and OS version as technical
  context (`BuildStatisticsReportUseCase`) and no identifier.
- **There is no reset button** - an owner decision from the original ticket; withdrawal of consent is the only
  erasure path, and it is the one that matters.

---

## 4. What this page does not describe

Nothing here is an analytics, crash-reporting or advertising SDK: that claim is checked against the actual
dependency set by `scripts/quality/assert-security-posture.ps1`, and `docs/SECURITY_POSTURE.md` section 4 is where
it is recorded. A voluntary anonymous aggregate leaving the device, period-split counts, and any app-initiated
intake are each a separate capability requiring their own decision - described here as absent so that their
appearance is a choice rather than a side effect.

---

## 5. How this document is kept true

The counters are authored, not derivable: no mechanism can say which of them a user would consider tracking. What
is mechanical sits elsewhere - the dependency half of the no-telemetry claim in `assert-security-posture.ps1`, and
the permission and network inventories beside it. A counter added to `StatsKey` without a line here, or a public
page that stops agreeing with section 3, is a defect of this page, found when it is read rather than when a user
notices the gap.
