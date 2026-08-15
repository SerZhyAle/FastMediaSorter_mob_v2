# Phase 01 — String and Use Case

**Strategic spec:** [`../S0072_downloads-auto-destination.md`](../S0072_downloads-auto-destination.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** —
**Completed:** 2026-05-04

---

## Objective

Add the `R.string.resource_name_downloads` string key to all three locale files and create `ProvisionDownloadsDestinationUseCase` that auto-creates the Downloads folder as the first destination when no destinations exist yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. _(foundation — no prior phase)_
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDownloadsDestinationUseCase.kt` | New | ≤ 80 |

---

## Steps

### Step 01.1 — Add `resource_name_downloads` to EN strings

**Files:** `app_v2/src/main/res/values/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/src/main/res/values/strings.xml`, add the following entry in the "Resource / virtual names" block near the other `virtual_*` names (e.g., near `virtual_all_music`):
>
> ```xml
> <string name="resource_name_downloads">Downloads</string>
> ```

**Verification:**

- `Grep` — pattern `name="resource_name_downloads"` in `app_v2/src/main/res/values/strings.xml` matches exactly once.
- `Grep` — value is `>Downloads<` on that same line.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: values/strings.xml (+1 LOC). Dev log recorded.

---

### Step 01.2 — Add `resource_name_downloads` to RU strings

**Files:** `app_v2/src/main/res/values-ru/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `app_v2/src/main/res/values-ru/strings.xml`, add the following entry in the same relative position as in the EN file (near other `virtual_*` translations):
>
> ```xml
> <string name="resource_name_downloads">Загрузки</string>
> ```

**Verification:**

- `Grep` — pattern `name="resource_name_downloads"` in `app_v2/src/main/res/values-ru/strings.xml` matches exactly once.
- `Grep` — value is `>Загрузки<` on that same line.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: values-ru/strings.xml (+1 LOC). Dev log recorded.

---

### Step 01.3 — Add `resource_name_downloads` to UK strings

**Files:** `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `app_v2/src/main/res/values-uk/strings.xml`, add the following entry in the same relative position as in the EN file (near other `virtual_*` translations):
>
> ```xml
> <string name="resource_name_downloads">Завантаження</string>
> ```

**Verification:**

- `Grep` — pattern `name="resource_name_downloads"` in `app_v2/src/main/res/values-uk/strings.xml` matches exactly once.
- `Grep` — value is `>Завантаження<` on that same line.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: values-uk/strings.xml (+1 LOC). Dev log recorded.

---

### Step 01.4 — Create `ProvisionDownloadsDestinationUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDownloadsDestinationUseCase.kt` _(New)_
**Depends on:** Steps 01.1–01.3

**Prompt for developer:**

> Create a new file `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDownloadsDestinationUseCase.kt`.
>
> The class is `ProvisionDownloadsDestinationUseCase` with `@Inject constructor` (no `@Provides` needed — Hilt auto-binds it).
>
> Constructor parameters:
> - `@param:ApplicationContext private val context: Context`
> - `private val resourceRepository: ResourceRepository`
> - `private val resolveResourceIconUseCase: ResolveResourceIconUseCase`
>
> `suspend operator fun invoke(): Boolean` — returns `true` if the Downloads resource was created, `false` if skipped.
>
> Logic:
> 1. Resolve `downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath`.
> 2. Verify the path exists and is a directory (use `java.io.File(downloadsPath).isDirectory`). If not — return `false`.
> 3. Call `resourceRepository.getAllResourcesSync()`. If any resource is a destination AND its path equals `downloadsPath` — return `false` (idempotency guard).
> 4. Determine `destinationOrder = 0` (always slot 0 — no existing destinations at this point).
> 5. Determine `destinationColor = DestinationColors.getColorForDestination(destinationOrder)`.
> 6. Resolve `iconId = resolveResourceIconUseCase(path = downloadsPath, profile = ResourceProfile.NONE, type = ResourceType.LOCAL)`.
> 7. Build and insert a `MediaResource`:
>    - `id = 0`, `name = context.getString(R.string.resource_name_downloads)`
>    - `path = downloadsPath`, `type = ResourceType.LOCAL`
>    - `profile = ResourceProfile.NONE`
>    - `supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO, MediaType.GIF, MediaType.TEXT, MediaType.PDF, MediaType.EPUB)` (allFiles behavior makes the set irrelevant in practice, but include all for completeness)
>    - `isDestination = true`, `destinationOrder = destinationOrder`, `destinationColor = destinationColor`
>    - `isWritable = true`, `isReadOnly = false`
>    - `allFiles = true` (Downloads always shows all files — S0059 precedent)
>    - `scanSubdirectories = true`
>    - `displayOrder = Int.MAX_VALUE` (appended after virtual resources; will not interfere with their sequential display orders)
>    - `createdDate = System.currentTimeMillis()`, `fileCount = 0`
>    - `iconId = iconId`
> 8. Call `resourceRepository.addResource(resource)`.
> 9. Log with Timber: `"Provisioned Downloads destination on first launch"`.
> 10. Return `true`.
>
> Required imports: `android.content.Context`, `android.os.Environment`, `com.sza.fastmediasorter.R`, `com.sza.fastmediasorter.core.util.DestinationColors`, `com.sza.fastmediasorter.domain.model.*`, `com.sza.fastmediasorter.domain.repository.ResourceRepository`, `dagger.hilt.android.qualifiers.ApplicationContext`, `timber.log.Timber`, `javax.inject.Inject`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDownloadsDestinationUseCase.kt` exists.
- `Grep` — `class ProvisionDownloadsDestinationUseCase` matches exactly once in that file.
- `Grep` — `isDestination = true` present in that file.
- `Grep` — `allFiles = true` present in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file (Timber-only rule).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 5/5 PASS. Files: ProvisionDownloadsDestinationUseCase.kt (New, 70 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` exit 0 (BUILD SUCCESSFUL in 55s).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new public class added).

---

## Handoff Notes to Next Phase

- `ProvisionDownloadsDestinationUseCase` exists and compiles as a standalone injectable use case.
- The three locale files all carry `resource_name_downloads`.
- Phase 02 injects the new use case into `MainViewModel` and invokes it.

---

## Rollback Plan

Revert phase commit(s). No DB migration or schema change — the Downloads resource will remain in the DB on devices that ran the provisioning, but users can remove it like any other resource.
