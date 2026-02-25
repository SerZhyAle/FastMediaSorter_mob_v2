# RELEASE CHECKLIST — FastMediaSorter v2

> Template for every release candidate. All items must be ✅ before release sign-off.
> Updated: see TZ_C1_RELEASE_TRAIN.md for process ownership.

---

## 1. Code Quality Gates (automated)

- [ ] CI pipeline passed: lint + unit tests + build (all flavors) — **GitHub Actions**
- [ ] No NEW lint errors introduced (baseline-clean)
- [ ] All unit tests pass (0 failures)
- [ ] Release APK / AAB builds without errors

## 2. Build Verification

- [ ] `standard` release build produced and signed (`assembleStandardRelease`)
- [ ] `lite` release build produced and signed
- [ ] `photos` release build produced and signed
- [ ] `legacy` release build produced and signed
- [ ] APK sizes reviewed — no unexpected size regression

## 3. Testing Coverage

- [ ] Maestro smoke suite passed on emulator (API 34/35)
- [ ] Maestro critical suite passed
- [ ] Manual regression on physical device (≥1 device)
- [ ] Cloud auth flows verified (Google Drive, OneDrive, Dropbox) — if changed
- [ ] Network sources (SMB, SFTP, FTP) verified — if changed

## 4. Version & Metadata

- [ ] `versionName` and `versionCode` generated correctly via `dev/build-with-version.ps1`
- [ ] What's New / Changelog updated for all locales (EN, RU, UK)
- [ ] `DOWNLOADS/builds_versions.lst` updated
- [ ] `DOWNLOADS/signatures.txt` updated

## 5. Documentation

- [ ] `README.md` / `docs/README_RU.md` / `docs/README_UK.md` updated if UX changed
- [ ] `docs/TROUBLESHOOTING.md` updated if any known issues resolved
- [ ] FAQ updated if new scenarios added

## 6. Security

- [ ] No hardcoded API keys or secrets in diff
- [ ] ProGuard mapping file archived for crash symbolication
- [ ] Tokens/passwords masked in logs (spot check recent logs)

## 7. Post-Release

- [ ] AAB uploaded to Play Console — Internal Testing track
- [ ] Smoke test on Internal Testing build
- [ ] Promote to Closed / Open Testing after 24h soak
- [ ] Monitor crash rate (< 1% per session) for 48h post-release

---

## Sign-Off

| Stage | Owner | Status | Date |
|-------|-------|--------|------|
| Code Quality | CI / Dev | | |
| Build Verification | Dev | | |
| Testing | Dev | | |
| Documentation | Dev | | |
| Security | Dev | | |
| Release to Store | Dev | | |

---

*Generated from TZ_C1_RELEASE_TRAIN.md*
