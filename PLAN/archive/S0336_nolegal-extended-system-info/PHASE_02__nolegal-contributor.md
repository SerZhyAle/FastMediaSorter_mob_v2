# Phase 02 - noLegal diagnostics contributor

**Strategic spec:** [`../S0336_nolegal-extended-system-info.md`](../S0336_nolegal-extended-system-info.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-03
**Completed:** 2026-06-03

**Step Log:**

- 2026-06-03 - Steps 02.1-02.4 Verification PASS (Grep predicates + `assembleNoLegalDebug` compiles: Hilt multibinding resolves; noLegal section strings 7/7/7 in EN/RU/UK). Process-log "ring buffer" field dropped (logs already available via the dedicated log dialog) and field labels kept fixed-English per strategic §3.2 - both autonomy-sanctioned (§3.3), noted here.

---

## Objective

Implement the real `ExtendedDiagnosticsContributor` for the noLegal flavor under `src/noLegal/java`, collecting the seven strategic §5.1 diagnostic categories with graceful degradation and correct `sensitive` flags, and bind it `@IntoSet`. Builds only into `noLegalDebug`; `standardDebug` is unaffected.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`ExtendedDiagnosticsContributor`, models, `@Multibinds` set exist).
- [ ] `dev/FLAVOR_DEVELOPMENT_RULES.md` re-read - all new files land in `src/noLegal/java`, never `src/main` or `src/vr`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/diagnostics/NoLegalDiagnosticsCollectors.kt` | New | ≤ 480 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/diagnostics/NoLegalExtendedDiagnosticsContributor.kt` | New | ≤ 180 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalExtendedDiagnosticsModule.kt` | New | ≤ 25 |
| `app_v2/src/noLegal/res/values/strings.xml` | Modified | - |
| `app_v2/src/noLegal/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/noLegal/res/values-uk/strings.xml` | Modified | - |

> **Flavor placement (MANDATORY):** all three `.kt` files live under `src/noLegal/java` - this source set compiles only for the noLegal flavor (it is NOT `src/vr/java`, which also feeds the Store-published `vr` flavor). The contributor MAY inject XR types because `src/vr/java` is mounted into noLegal. Strings live under `src/noLegal/res` so they are excluded from public APKs (strategic §11.2).
> No `BuildConfig.IS_*` / `SUPPORT_*` guard anywhere - isolation is by source set.

---

## Steps

### Step 02.1 - Implement the seven category collectors

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/diagnostics/NoLegalDiagnosticsCollectors.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an internal collector (object or class) in package `com.sza.fastmediasorter.diagnostics` exposing one builder per strategic §5.1 category, each returning `ExtendedDiagnosticsSection`. Every field read is wrapped defensively (try/catch → `"unknown"` / `"n/a"`; never throw - a failing source degrades one field, not the section). Target minSdk 26; gate any API-26+-only call and fall back to `n/a` below it. Categories and their `sensitive` flags:
> - **OS & Security:** root markers (su / busybox / Magisk / KernelSU / APatch presence on `PATH` and known paths), SELinux mode (`getenforce` / `/sys/fs/selinux/enforce`), Developer-options + ADB (`Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`, `ADB_ENABLED`), Xposed/LSPosed presence. Not sensitive.
> - **Permissions Audit:** effective state of `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`. Not sensitive.
> - **Installer & Signature:** installer package name; APK signing-cert SHA-256 (`sensitive = true`); presence of alternative stores. 
> - **noLegal Runtimes:** Python + yt-dlp version/date, `ffmpeg`/`ffprobe` presence, PaddleOCR/PaddleLite model files + load status, Tesseract `.traineddata` presence, OpenXR/VR runtime + VR profile (read via the XR seam injected in 02.2). Not sensitive.
> - **Mounts & File System:** `/proc/mounts` entries when readable, else external dirs; `/data` and `/cache` partition sizes. Mount paths `sensitive = true`.
> - **Network Diagnostics:** configured DNS servers, active VPN interfaces (`tun0`/`ppp0`), system HTTP proxy. Local addresses `sensitive = true`.
> - **Process Resources & Logs:** native heap (`Debug.getNativeHeapAllocatedSize`), open fd count (`/proc/self/fd`), active thread count, last ≤50 in-process log lines (best-effort; degrade to `n/a` if no source). Not sensitive.
>
> All section titles and field labels come from `R.string.nolegal_diag_*` (added in Step 02.4) via the injected `Context`. Timber only for any diagnostics-internal warning; the log message must describe the subject in plain English and must NOT embed `S0336`.

**Verification:**

- `Glob` - `NoLegalDiagnosticsCollectors.kt` exists under `src/noLegal/java/.../diagnostics/`.
- `Grep` - returns at least 7 `ExtendedDiagnosticsSection(` constructions.
- `Grep` - `sensitive = true` present (signature hash, mount paths, local network addresses).
- `Grep -n "Log\.d\("` - zero hits.
- `Grep` - no `BuildConfig.IS_` / `BuildConfig.SUPPORT_` in the file.

**Status:** `[x]` done

---

### Step 02.2 - Implement the contributor

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/diagnostics/NoLegalExtendedDiagnosticsContributor.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `@Singleton class NoLegalExtendedDiagnosticsContributor @Inject constructor(@ApplicationContext private val context: Context, ...)` implementing `ExtendedDiagnosticsContributor`. Inject the synchronous XR environment seam available to noLegal (e.g. `XrEnvironmentDetector` from `core/xr`) so the Runtimes category can report VR runtime/profile; read it defensively and degrade to `n/a` if it reports none. `sections()` returns the seven collector sections in strategic §5.1 order. The whole `sections()` body is defensive so a single failing collector yields its section with `unknown` fields rather than throwing.

**Verification:**

- `Glob` - `NoLegalExtendedDiagnosticsContributor.kt` exists.
- `Grep` - `class NoLegalExtendedDiagnosticsContributor` matches once, with `: ExtendedDiagnosticsContributor`.
- `Grep` - `override fun sections(): List<ExtendedDiagnosticsSection>` present.

**Status:** `[x]` done

---

### Step 02.3 - Bind the contributor into the multibound set

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalExtendedDiagnosticsModule.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) abstract class NoLegalExtendedDiagnosticsModule { @Binds @IntoSet abstract fun bindExtendedDiagnosticsContributor(impl: NoLegalExtendedDiagnosticsContributor): ExtendedDiagnosticsContributor }`. Package `com.sza.fastmediasorter.di`. Mirror the binding style of `NoLegalLinkDownloadModule.kt`.

**Verification:**

- `Glob` - `NoLegalExtendedDiagnosticsModule.kt` exists under `src/noLegal/java/.../di/`.
- `Grep` - `@Binds` and `@IntoSet` both present.
- `Grep` - return type `ExtendedDiagnosticsContributor`, parameter `NoLegalExtendedDiagnosticsContributor`.
- Build invariant: `assembleNoLegalDebug` compiles (contributor resolves into the set).

**Status:** `[x]` done

---

### Step 02.4 - Add localized noLegal diagnostics strings

**Files:** `app_v2/src/noLegal/res/values/strings.xml`, `app_v2/src/noLegal/res/values-ru/strings.xml`, `app_v2/src/noLegal/res/values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add the `nolegal_diag_*` keys (seven section titles plus the field labels referenced in 02.1) to all three noLegal `strings.xml` files in lockstep (EN / RU / UK). These keys live ONLY in the noLegal source set - never add them to `src/main/res`. Apply `docs/COMMUNICATION_POLICY.md` §6 tone checklist to the section titles (e.g. "Extended Diagnostics (noLegal)" parent framing). Field-value bodies stay technical English; only the labels are localized. Use `..` not `...` and correct `ё` in Russian.

**Verification:**

- `Grep` - each of the seven section-title keys present in all three noLegal `strings.xml` files (`expected: 3 files | actual: <n>` per key).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "nolegal_diag_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `/build` `noLegalDebug` compiles.
- [ ] `/build` `standardDebug` compiles AND none of the three `src/noLegal` `.kt` files are in its source set (isolation - guaranteed by source-set placement, spot-checked in Phase 04 catalog `-NoFlavors`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- With this phase merged, `noLegalDebug`'s `GatherSystemInfoUseCase` already appends the extended sections to `maskedText`/`fullText` and reports `hasSensitive = true`. Phase 03 only needs to surface the reveal action when `hasSensitive`.

---

## Rollback Plan

Revert the phase commit(s). Removing the `@IntoSet` binding returns the noLegal contributor set to empty; no main code or data surface changes.
