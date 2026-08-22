# Phase 01 - Transport carries a media type (schema v3)

**Strategic spec:** [`../S1846_wear-phone-browse-favourites-placeholders.md`](../S1846_wear-phone-browse-favourites-placeholders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

The watch-to-phone request carries a media type and the phone narrows its answer by it at both levels, so a chip receives only its own kind of file and the watch needs no filter of its own.

---

## Prerequisites

- [ ] Strategic §6.3 read - the owner's ruling that the schema moves to v3 inside this ticket.
- [ ] `research/02__phone-browse-and-favourites-as-is.md` §1 read.
- [ ] `temp/CODE.LOCK` acquired immediately before each source edit and released right after (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearPhoneResourcePayload.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearPhoneResourcePayload.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ListPhoneResourcePageUseCase.kt` | Modified | ≤ 260 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/PhoneResourceClient.kt` | Modified | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/model/WearPhoneResourcePayloadTest.kt` | Modified | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ListPhoneResourcePageUseCaseTest.kt` | Modified | ≤ 320 |

> **The payload exists TWICE**, once per module, and the two copies together ARE the wire contract. They are edited in the same step and never in separate ones: a field added on one side only is a silent protocol break that compiles cleanly in both modules.
>
> **S1697 probes live in `PhoneResourceClient.kt` and `ListPhoneResourcePageUseCase.kt`.** Carry them through unchanged - see the INDEX concurrency note.
>
> **Correction 2026-08-20, before any code was written.** Step 01.4 originally required `toWireMimeType()` to stop collapsing unknown types to null, following research artifact 02 §1 and strategic §5. Reading the function showed its KDoc states the opposite as a deliberate invariant: null means "metadata only", and the watch acts on it. The research finding was about CLIENT-side filtering; the owner chose SERVER-side filtering, which removes the need entirely - the phone returns only documents for the `documents` chip, so nothing on the watch has to tell a document from an unsupported binary. Strategic §5 corrected the same day.

---

## Steps

### Step 01.1 - Add the media type to both copies of the request and raise the schema to 3

**Files:** `wear/../domain/model/WearPhoneResourcePayload.kt`, `app_v2/../domain/model/WearPhoneResourcePayload.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In BOTH copies of the payload file, add a nullable `mediaType: String? = null` to `WearPhoneResourceRequest` and raise `WEAR_PHONE_RESOURCE_SCHEMA_VERSION` from 2 to 3. Keep the field last in the constructor so no positional call site shifts. Null means "no filter" and is what the unfiltered `Phone` chip sends.
>
> Use the media-type vocabulary the watch route already speaks - the value `WearRoutes.browsePhone(mediaType)` puts on the route - rather than inventing a second one, and write the accepted values into the field's KDoc so the two sides cannot drift apart on spelling.
>
> Add no version negotiation and no v2 fallback branch.

**Why:**

Strategic §13 records the owner's ruling verbatim - there is no installed watch base - so both sides move to v3 at once and a compatibility path would be dead code from the day it is written; research artifact 02 §1 establishes that the request carries no media type at all today, which is the reason the five chips cannot work.

**Verification:**

- `Grep` - `WEAR_PHONE_RESOURCE_SCHEMA_VERSION = 3` matches exactly once in EACH of the two payload files.
- `Grep` - `mediaType` matches inside `WearPhoneResourceRequest` in EACH of the two files.
- `Grep` - `SCHEMA_VERSION = 2` returns zero hits across `wear/src` and `app_v2/src`.
- `.\a.ps1 fw` exits 0 and `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `mediaType: String? = null` added last in `WearPhoneResourceRequest` in BOTH copies in one edit, with the route vocabulary (`photos`/`videos`/`music`/`documents`/`all`) written into its KDoc; `WEAR_PHONE_RESOURCE_SCHEMA_VERSION` is 3 in both. The phone copy carries `@SerializedName("mediaType")` like every sibling field - the wear copy has no Gson annotations at all, which is the pre-existing asymmetry, not a new one. No negotiation branch added. `fk` exit 0, `fw` exit 0.

---

### Step 01.2 - Send the media type from the watch client

**Files:** `wear/../data/wear/PhoneResourceClient.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Thread the media type into the request the client builds, defaulting to null when the caller passes none. Change no other field and leave the existing `Timber.d("S1697: ..")` line exactly as it is.

**Why:**

The request is built in exactly one place, so the field added in step 01.1 reaches the phone only if this builder passes it; without this step the phone would filter on a value that is always null and the five chips would keep showing everything.

**Verification:**

- `Grep` - `mediaType` matches in `PhoneResourceClient.kt`.
- `Grep` - `Timber.d("S1697:` still matches exactly once in that file - the pending ticket's probe survived.
- `.\a.ps1 fw` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `PhoneResourceClient.browse()` gained a third parameter defaulting to null and passes it into the request. `Timber.d("S1697:` still 1 hit in the file - the pending ticket probe carried through untouched. `fw` exit 0 (banner: `Fast wear check`).

---

### Step 01.3 - Filter the root listing by media type

**Files:** `app_v2/../domain/usecase/ListPhoneResourcePageUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the `ROOT` branch, when the request names a media type, keep only resources whose `supportedMediaTypes` intersect it. A request with no media type keeps today's behaviour exactly. Leave `isExposedToWatch()` as the first filter - visibility policy decides before the type does.

**Why:**

Research artifact 02 §1 establishes that a root wire item carries no usable type, because a directory's `mimeType` is always null, so the watch cannot narrow the root list itself and the phone is the only side able to answer which resources hold images.

**Verification:**

- `Grep` - the `ROOT` branch references `supportedMediaTypes` and the request's media type together.
- `Grep` - `isExposedToWatch` still matches in that branch and is applied before the type filter.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `ROOT` now chains a second filter after `isExposedToWatch()`, so visibility policy still decides first. The predicate is `holdsAnyOf(filter)`, which is true when the filter is null - a request naming no type behaves exactly as before, pinned by a test. `fk` exit 0.

---

### Step 01.4 - Narrow the folder listing by media type, and leave the wire mime type alone

**Files:** `app_v2/../domain/usecase/ListPhoneResourcePageUseCase.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In the `CHILDREN` branch, when the request names a media type, use it to narrow `supportedTypes` instead of the resource's own configuration. Map the route vocabulary to `MediaType` members: `photos` to IMAGE and GIF, `videos` to VIDEO, `music` to AUDIO, `documents` to TEXT, PDF, EPUB and OFFICE_DOCUMENT, and `all` to no narrowing at all.
>
> **Do NOT change `toWireMimeType()`.** Its KDoc states an invariant this ticket must keep: the watch decides playability from a coarse family, and null means "metadata only" - a value the watch already reads and acts on. Widening it to emit real subtypes would invite the watch to try a stream it cannot decode.

**Why:**

Server-side narrowing makes the client-side distinction unnecessary: once the phone returns only documents for the `documents` chip, the watch never has to tell a document from an unsupported binary, which is the only thing the wire mime type was going to be widened for - and widening it would silently override a documented invariant, which CLAUDE.md Rule 8 forbids.

**Verification:**

- `Grep` - the `CHILDREN` branch narrows `supportedTypes` by the request's media type.
- `Grep` - `toWireMimeType` is byte-identical to its state at phase start, KDoc included.
- `Grep` - the mapping covers all five route values, and `all` narrows nothing.
- `Grep` - the directory case still yields `mimeType = null`.
- `..ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `CHILDREN` narrows via `supportedMediaTypes.narrowedBy(filter)`, an intersection that can only shrink the resource own configuration - a resource never configured for video does not start showing video because a watch asked. `toWireMimeType()` untouched, per the correction recorded in this file header. The route-to-`MediaType` mapping lives in one private function with the five strings as named constants, and an unknown string falls through to "no filter" rather than an empty page. `fk` exit 0.

---

### Step 01.5 - Extend the two phone-side test classes to the new field

**Files:** `app_v2/src/test/.../WearPhoneResourcePayloadTest.kt`, `app_v2/src/test/.../ListPhoneResourcePageUseCaseTest.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add cases: a request with no media type returns what it returns today; a request naming images excludes an audio-only resource at root; a request naming documents returns only documents and no audio or image; a directory still carries a null mime type. Do not rewrite the existing cases - the old behaviour is the null-media-type behaviour and must stay pinned.

**Why:**

Strategic criterion 7 requires that the `Images` chip not show audio, and that is a claim about the phone's filtering rather than about any screen, so the phone's own test class is the only place it can be proven without a device.

**Verification:**

- `Grep` - both test files reference `mediaType`.
- The two classes pass via `--tests`, or `.\a.ps1 fu` passes.
- `Grep` - every pre-existing test method name still matches; none was deleted.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Five cases added across the two classes, none of the existing ones rewritten. Verified from the RESULTS XML, not from the build verdict: the runner writes a filtered run to `test-results/testStandardDebugUnitTest-filtered/`, and the unfiltered directory beside it was 25 minutes stale and still showed the OLD case list - reading that one would have certified nothing. Filtered XML: `ListPhoneResourcePageUseCaseTest` tests="12" failures="0" (was 8), `WearPhoneResourcePayloadTest` tests="7" failures="0" (was 6), and every new case name is present.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [ ] `.\a.ps1 fw` exits 0, `.\a.ps1 fk` exits 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `Grep` - the `Timber.d("S1697:` count is unchanged from the phase start.
- [x] Phase-boundary audit run - the wire contract is a durable format, so apply the persistence-contract lens of `docs/CODE_AUDIT_PROTOCOL.md`: both copies changed together, and no stored value depends on the schema number.

---

## Handoff Notes to Next Phase

The request can name a media type and the phone honours it. Phases 02 and 03 consume that; neither may add a second filtering path.

---

## Rollback Plan

Revert the phase commit. Nothing is persisted: the schema number travels inside each message, so a rollback restores v2 on both sides at once with no stored data to migrate.
