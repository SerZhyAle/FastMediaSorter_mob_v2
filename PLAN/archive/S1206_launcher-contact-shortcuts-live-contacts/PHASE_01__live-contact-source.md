# Phase 01 - Live contact source

**Strategic spec:** [`../S1206_launcher-contact-shortcuts-live-contacts.md`](../S1206_launcher-contact-shortcuts-live-contacts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Introduce the single data-layer seam that reads a contact's current name and photo by lookup key under
`READ_CONTACTS`, caches the answer, and publishes a change signal; nothing renders it yet.

---

## Prerequisites

- [x] Strategic §4 questions are Resolved (both are, quiz 2026-08-06).
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LiveContactDetails.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/LiveContactDataSource.kt` | New | ≤ 220 |

> No file in this phase exceeds 500 LOC, so no backup step is required.
>
> **Flavor placement.** Both files sit in `src/main`, matching the existing `ContactSnapshotDataSource`:
> strategic §3.3 scopes the feature to launcher builds, but the reading code has no flavor-specific
> behaviour and the launcher source set is what gates whether any contact cell exists at all.

---

## Steps

### Step 01.1 - Add the `LiveContactDetails` model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LiveContactDetails.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `LiveContactDetails` as a data class holding `displayName: String` and `photoUri: String?`. Both
> describe what the address book says right now, as opposed to `LauncherContactTarget`, which holds what
> it said at pin time. Add a KDoc naming that distinction and stating that `photoUri` is the thumbnail
> URI, not the full-size one, because the cell draws it in a 44dp box.

**Why:**

Strategic §2 states the permission buys "чтение имени, фотографии .. на каждый показ ячейки", so the live
answer needs a type of its own rather than overwriting the stored snapshot, which §3.3 keeps as the
fallback for a deleted contact or a failed read.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LiveContactDetails.kt` exists.
- `Grep` - `data class LiveContactDetails` matches exactly once.
- `Grep` - `photoUri` present.

**Status:** `[x]` done

---

### Step 01.2 - Read live name and photo by lookup key

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/LiveContactDataSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Singleton class LiveContactDataSource @Inject constructor(@ApplicationContext context: Context)`
> with `suspend fun read(lookupKey: String): LiveContactDetails?` running on `Dispatchers.IO`. Return null
> immediately when `lookupKey` is blank or when `READ_CONTACTS` is not granted - check the grant with
> `ContextCompat.checkSelfPermission`. Otherwise resolve the contact through
> `Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)` and query
> `DISPLAY_NAME_PRIMARY` and `PHOTO_THUMBNAIL_URI`. Wrap the query in `runCatching` exactly as
> `ContactSnapshotDataSource.queryOrNull` does, degrading to null and logging only the failure class name.
> Log nothing about the person - no name, number, lookup key or photo URI.

**Why:**

Strategic §3.3 makes the stored snapshot the fallback "на случай, когда контакт удалён или чтение не
удалось", which only holds if every failure path returns null instead of throwing; a bare query would
crash the desktop when the user revokes the permission while the launcher is on screen.

**Verification:**

- `Grep` - `class LiveContactDataSource` matches exactly once.
- `Grep` - `suspend fun read(` present.
- `Grep` - `CONTENT_LOOKUP_URI` present.
- `Grep` - `PHOTO_THUMBNAIL_URI` present.
- `Grep` - `runCatching` present.
- `Grep` - `checkSelfPermission` present.
- `Grep` - `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

---

### Step 01.3 - Publish a change signal that emits before any change

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/LiveContactDataSource.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `fun changes(): Flow<Unit>` built with `callbackFlow`: register a `ContentObserver` on
> `ContactsContract.Contacts.CONTENT_URI` with `notifyForDescendants = true`, emit `Unit` on
> `onChange`, and unregister in `awaitClose`. Emit one `Unit` immediately on collection, before any
> observer fires. Guard the registration with `runCatching` so a device that refuses the observer still
> yields a flow that emits its initial value.

**Why:**

Strategic §1 names "переименование контакта в системе ярлык не подхватывает" as the first limitation this
ticket removes, and a rename produces no database write of ours, so only a contacts observer can reach the
cell. The immediate first emission is mandatory rather than cosmetic: Phase 02 puts this flow into the
`combine` in `ResolveLauncherDesktopUseCase`, and `combine` withholds its first value until every input has
emitted, so an observer-only flow would leave the whole desktop blank until some contact changed.

**Verification:**

- `Grep` - `fun changes()` present.
- `Grep` - `ContentObserver` present.
- `Grep` - `awaitClose` present.
- `Grep` - `notifyForDescendants` present or `true` passed positionally to `registerContentObserver`.

**Status:** `[x]` done

---

### Step 01.4 - Cache reads and drop the cache on change

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/LiveContactDataSource.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add an in-memory cache keyed by lookup key that `read` consults before querying and fills after a
> successful query. Clear it whenever the observer from Step 01.3 fires, before emitting downstream. Guard
> the map for concurrent access, since resolution runs per cell on `Dispatchers.IO`. Cache a negative
> answer too, so a contact that is genuinely gone is not re-queried on every emission.

**Why:**

Strategic §3.4 records that without a cache "каждая эмиссия объединённого потока перечитывает контакт для
каждой ячейки, включая эмиссии от смены состояния радио" - the desktop already re-resolves every cell
whenever Wi-Fi or Bluetooth changes, so an uncached live read multiplies address-book queries by every
unrelated radio event.

**Verification:**

- `Grep` - a cache field declaration is present in the file.
- `Grep` - the cache is cleared inside the observer callback path.
- Read the file - `read` consults the cache before calling `contentResolver.query`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, `Fast check passed` (2026-08-08).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added - batched for the whole ticket at Phase 05 closure, per CLAUDE.md section 12 journaling granularity (one entry per logical change, not per file).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Observer registration and removal are symmetric; the read is off the main thread; the cache is concurrent and holds no Drawable; a missing grant returns null before the cache is consulted, so a grant arriving later is not masked by a cached negative.

---

## Handoff Notes to Next Phase

`LiveContactDataSource.read(lookupKey)` returns null for every "cannot answer" case - blank key, permission
absent, contact gone, provider refusal - so Phase 02 needs one null branch, not four. `changes()` always
emits once on collection, which is what makes it safe to `combine`.

---

## Rollback Plan

Revert phase commit(s) - two new files, no caller yet, no data migration or user-facing surface changed.
