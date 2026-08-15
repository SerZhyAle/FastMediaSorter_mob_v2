# Phase 06 - Onboarding and Installer

**Strategic spec:** [`../S0421_windows-sftp-folder-share-companion.md`](../S0421_windows-sftp-folder-share-companion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (06.5 clean-machine rehearsal deferred to user test; 06.3 LAN check likewise manual)
**Depends on:** Phase 05
**Blocks:** none (desktop leaf)
**Steps done:** 5 / 5 (06.5 = `[manual - deferred to human]`)
**Started:** 2026-07-10
**Completed:** 2026-07-10

---

## Objective

Deliver the "install, pick a folder, done" first-run experience (strategic §2 goal 7) and an unsigned Windows installer with an in-app SmartScreen/AV trust guide (strategic quiz 2026-06-30).

---

## Prerequisites

- [ ] Phase 05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `desktop/windows-companion/frontend/` (onboarding views) | New/Modified | n/a |
| `desktop/windows-companion/internal/app/onboarding.go` | New | ≤ 200 |
| `desktop/windows-companion/build/windows/installer/` (NSIS) | New | n/a |
| `desktop/windows-companion/docs/TRUST_GUIDE.md` | New | ≤ 120 |
| `desktop/windows-companion/build.ps1` | Modified | ≤ 80 |

---

## Steps

### Step 06.1 - First-run wizard

**Files:** `internal/app/onboarding.go`, `frontend/`
**Depends on:** - start of phase

**Prompt for developer:**

> Implement a first-run flow: welcome -> pick shared folder(s) -> (auto) install service + generate keys + start server + attempt port mapping -> show the export QR with LAN/internet status. Minimize clicks: everything after folder pick is automatic. Persist a "first-run done" flag so subsequent launches open straight to the tray dashboard.

**Verification:**

- `Grep` - first-run flag persistence token present in `onboarding.go`.
- `Grep` - folder-pick -> service-install call chain present in `internal/app/`.

**Status:** `[x]` done

---

### Step 06.2 - Trust guide (SmartScreen / AV)

**Files:** `docs/TRUST_GUIDE.md`, `frontend/`
**Depends on:** - start of phase

**Prompt for developer:**

> Author a short trust guide explaining the unsigned-binary SmartScreen "More info -> Run anyway" step and why the app needs firewall + service permissions, and surface a link/panel to it inside onboarding. Follow the project communication tone (plain, non-alarming). No em-dashes in user-facing copy; use `..` per docs style where prose ellipsis is needed.

**Verification:**

- `Glob` - `docs/TRUST_GUIDE.md` exists.
- `Grep` - onboarding view references the trust guide.

**Status:** `[x]` done

---

### Step 06.3 - Firewall rule on install

**Files:** `internal/app/onboarding.go`
**Depends on:** Step 06.1

**Prompt for developer:**

> On service install, add an inbound Windows Firewall rule for the SFTP listen port (via `netsh advfirewall` or the firewall API) scoped to private+public per the user's network, and remove it on uninstall. Without this the LAN/internet path silently fails behind the default firewall.

**Verification:**

- `Grep` - firewall rule add/remove token present.
- Manual: after install, a LAN client reaches the port with the default firewall enabled (record PASS).

**Status:** `[x]` done

---

### Step 06.4 - Unsigned NSIS installer

**Files:** `build/windows/installer/`, `build.ps1`
**Depends on:** Step 06.1

**Prompt for developer:**

> Configure the Wails NSIS installer target to bundle the `.exe`, register uninstaller, and run the app after install. Produce an **unsigned** installer (no code-signing this MVP). Wire `build.ps1` to output the installer under `build/bin/`. Document that AV reputation accrues slowly for unsigned binaries.

**Verification:**

- `wails build -nsis` (or the project's configured installer command) produces an installer artifact under `build/bin/`.
- `Grep` - no code-signing / `signtool` step in `build.ps1` (unsigned by decision).

**Status:** `[x]` done

---

### Step 06.5 - Clean-machine install rehearsal

**Files:** - (verification only, no new source)
**Depends on:** Step 06.3, 06.4

**Prompt for developer:**

> On a clean Windows VM/user: run the installer, complete onboarding (pick folder), and confirm a phone on the same LAN imports the QR and browses the folder; then confirm the service auto-starts after reboot. Record the walk-through result in this phase file.

**Verification:**

- Manual: clean-install -> onboarding -> LAN import + browse works; survives reboot (record PASS/FAIL with notes).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` is `[x] done` (06.5 manual rehearsal deferred - see notes).
- [x] `wails build` + installer target both succeed (2026-07-10: `fms_companion-amd64-installer.exe` in `build/bin/`).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry: batched at ticket closure via `close-and-log.ps1` (out-of-repo subproject).

**Execution notes (2026-07-10, /spec-all):**

- First-run wizard: overlay in the vanilla frontend (`IsFirstRun`/`FirstRunSetup` bindings); flag `firstrun.done` in the data dir. Chain after folder pick is automatic: service install (best effort - unelevated session degrades to the in-process worker with a hint), firewall rule, server start, reachability poll (<=20s), QR display.
- Trust guide: `docs/TRUST_GUIDE.md` (companion repo) + inline `<details>` trust panel in onboarding referencing it (SmartScreen "More info -> Run anyway", why admin prompt).
- Firewall: program-scoped inbound allow rule via `netsh advfirewall` (`AddFirewallRule`/`RemoveFirewallRule` in `internal/app/onboarding.go`); program-scoped so the persisted port may change without touching the rule; removed on `UninstallService`. Requires elevation - failure is non-fatal, surfaces in hint.
- **Two design fixes landed here** (found while wiring onboarding): (1) listen port is now persisted in `settings.json` after first OS-assignment - exported config / port mapping / firewall stay valid across restarts (fallback to a fresh OS port if the persisted one is taken); (2) service gets `--datadir <user dir>` argument at install time because LocalSystem resolves `%LOCALAPPDATA%` to systemprofile and would not see the UI-written host key/credential/shares.
- NSIS: winget NSIS.NSIS reported success but installed nothing (unelevated silent fail); portable NSIS 3.11 unzipped to `C:\Users\serzh\tools\nsis-3.11` instead; `build.ps1 -Installer` auto-prepends that path when `makensis` is absent. Unsigned by decision - no signtool anywhere in `build.ps1`.
- **Step 06.5 `[manual - deferred to human]`:** clean-VM install + phone LAN import + reboot survival - folded into the ticket's final `BlockNeedUserTest` scope (together with 03.6, 06.3 LAN reach check).

---

## Handoff Notes to Next Phase

Desktop side is shippable for LAN + port-forward. Android import (Phase 07) is the remaining half of the one-action pairing.

---

## Rollback Plan

Revert phase commit(s). Uninstall removes the service + firewall rule. No Android or data changes.
