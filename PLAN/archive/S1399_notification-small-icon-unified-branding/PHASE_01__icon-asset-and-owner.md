# Phase 01 - Icon asset and its single owner

**Strategic spec:** [`../S1399_notification-small-icon-unified-branding.md`](../S1399_notification-small-icon-unified-branding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Produce the one branded status-bar drawable and give it exactly one owner in code, so phase 02 has a single
symbol to point thirteen call sites at.

---

## Anchors

- `ic_app_logo.xml` - `app_v2/src/main/res/drawable/ic_app_logo.xml:5-14` - single path, `fillColor="@color/white"`, and the `android:tint="?attr/colorControlNormal"` at line 10 that must not survive the copy.
- `ic_notification_cloud_download.xml` - `app_v2/src/main/res/drawable/` - the fork-and-strip precedent, with its reason in the file's own header comment.
- `NotificationIds` - `app_v2/src/main/java/com/sza/fastmediasorter/core/notification/NotificationIds.kt:15` - the centralization precedent this phase extends.
- `NotificationIdsTest` - `app_v2/src/test/java/com/sza/fastmediasorter/core/notification/NotificationIdsTest.kt` - reflection-based uniqueness test; the place a companion assertion belongs.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_notification_app_logo.xml` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/notification/NotificationIcons.kt` | New | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/notification/NotificationIconsTest.kt` | New | ≤ 60 |

---

## Steps

### Step 01.1 - Fork the logo into a status-bar-safe drawable

**Files:** `app_v2/src/main/res/drawable/ic_notification_app_logo.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `ic_app_logo.xml` to `ic_notification_app_logo.xml` and drop the `android:tint` attribute, keeping the path and the white fill unchanged. Carry a header comment naming the reason the fork exists, as the two existing notification forks do. Do not add a tint of any kind - the system tints a status-bar icon itself.

**Why:**

Strategic ADR-1 and §3.2 record that a theme attribute in a notification small icon cannot resolve outside the app theme and throws `CannotPostForegroundServiceNotificationException` from `startForeground` - the defect S0405 and S0416 already fixed twice by forking exactly this way.

**Verification:**

- `Grep` - the new drawable contains no `?attr` occurrence.
- `Grep` - it declares exactly one `<path>` and its `fillColor` is a literal colour, not a theme attribute.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. `ic_notification_app_logo.xml` is `ic_app_logo.xml` minus `android:tint`: zero `?attr` occurrences, one `<path>`, `fillColor="@color/white"`. Header comment names S0405/S0416 as the reason the fork exists, matching the two sibling notification drawables.

---

### Step 01.2 - Give the icon one owner

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/notification/NotificationIcons.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `NotificationIcons` object beside `NotificationIds`, exposing a single `@DrawableRes` property naming the drawable from step 01.1 as the status-bar icon every notification uses. Its KDoc must say why it exists - one value, so a fourteenth call site cannot pick its own - mirroring how `NotificationIds`' KDoc records the id collision that created it.

**Why:**

Strategic §5.1.2 and ADR-2 choose the existing id-registry seam over a notification-builder factory, because a factory would rewrite notification construction in eleven classes to centralise one field, and MediaStyle builds its notification differently again.

**Verification:**

- `Grep` - exactly one `@DrawableRes` property is declared in the new object.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. `NotificationIcons` declares one `@DrawableRes` property, `STATUS_BAR`. `.\a.ps1 fk` exit 0.
- Written as `val`, not `const val`, on purpose: `R.drawable.*` is not guaranteed to be a compile-time constant under non-transitive R classes, so `const val` would be a build-configuration-dependent compile error rather than a style choice.

---

### Step 01.3 - Pin the asset's contract with a test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/notification/NotificationIconsTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Assert that the owner resolves to a non-zero drawable id. A JVM unit test cannot inspect the vector's contents, so do not pretend to check the tint here - the `?attr` absence is step 01.1's grep predicate and phase 03's gate, and this test only pins that the property is wired to a real resource.

**Why:**

Strategic §2.3 requires the icon to have one owner in code; a property pointing at nothing would satisfy every grep in this plan while breaking every notification at runtime.

**Verification:**

- `.\a.ps1 fu --tests "*NotificationIcons*"` passes, and the result XML shows the case actually ran.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Test written (`NotificationIconsTest.assertNotEquals(0, STATUS_BAR)`). Verification **could not run**, and not because of this ticket: `compileStandardDebugUnitTestKotlin` fails on four pre-existing errors in a sibling session's in-flight stream work - `StreamSourceCatalogMergeTest.kt:34`, `AddStreamSourceUseCaseTest.kt:34`, `UpdateStreamSourceUseCaseTest.kt:30` (all `No value passed for parameter streamPlayOutcomeDao`) and `StreamsViewModelAutoGridTest.kt:62` (`observeStreamPlayOutcomes`). A constructor gained a parameter and its callers in `src/test` were not updated. Not mine to fix - the shared tree has a live session on S1502, and editing its half-written tests would collide.
- Evidence this is external, not a regression here: `compileStandardDebugKotlin` succeeded in the same run and `.\a.ps1 fk` exits 0, so every main source including `NotificationIcons.kt` compiles. Only the *test* source set is red, and only in files this ticket never touched.
- Re-run `check-standard-fast.ps1 -Mode Unit -Tests "*NotificationIcons*"` once the sibling's tests compile; flip to `[x]` then.
- 2026-08-08 - **Verification PASS, blocker cleared.** The four `src/test` call sites now pass
  `streamPlayOutcomeDao()` / `observeStreamPlayOutcomes`, so the sibling session finished that work and
  `compileStandardDebugUnitTestKotlin` executes instead of failing.
  `check-standard-fast.ps1 -Mode Unit -Tests "*NotificationIcons*"` exit 0, and the case demonstrably
  ran rather than being filtered to nothing:
  `<testsuite name="…NotificationIconsTest" tests="1" skipped="0" failures="0" errors="0">` with
  `<testcase name="shared status bar icon resolves to a real drawable">`.
- The result XML is preserved at `temp/S1399/TEST-com.sza.fastmediasorter.core.notification.NotificationIconsTest.xml`
  on purpose: the first run's XML was gone from `app_v2/build/test-results/` within seconds, wiped by a
  sibling session's own gradle test run on this shared tree. Reading a verdict straight out of that
  directory is not safe while another agent is building - copy the artifact out, then cite the copy.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Phase-boundary audit, 2026-08-08.** Layer 1 only - `Files Touched` is one vector drawable, one
constant-holder object and its test, so Layers 2-4 (lifecycle/coroutine, listener ownership, Room) have
no surface here. Findings: none at P0/P1.

- Layer 1: `NotificationIcons` sits in `core/notification` beside the `NotificationIds` precedent it
  copies, holds no behaviour and injects nothing, so it crosses no layer boundary. It is a registry, not
  a `*Manager`, so CLAUDE.md Rule 6's `NounVerbManager` shape does not apply.
- Rule 20 (dead weight): nothing is orphaned yet - the four superseded drawables still have live callers
  until phase 02 repoints them, and 02.4 owns their retirement.
- Closure evidence: `post-change.ps1 -ScopeToFile` over the three files returned `PASS (Mixed)`, exit 0.
- Noted for phase 03, not a finding: `assert-fgs-notifications` already fails a notification icon that
  carries a `?attr` tint, so the new gate must cover the *other* half of ADR-4 - a small-icon setter
  handed an `R.drawable.` literal instead of the owner - rather than re-checking the tint.

---

## Handoff Notes to Next Phase

One drawable, one owner, nothing consuming it yet. Every notification still shows its old icon, so nothing
is user-visible until phase 02.

---

## Rollback Plan

Delete both new files. Nothing referenced them.
