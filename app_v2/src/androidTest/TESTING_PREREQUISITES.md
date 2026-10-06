<sub class="doc-stamp">26.09.26 22:32</sub>

# Instrumentation Test Prerequisites

The device tests under `app_v2/src/androidTest/` need a prepared device and a prepared app. Both are automatic since S3741 - nothing is set up by hand.

---

## 1. Running the suite

```powershell
.\a.ps1 fst -DeviceId <serial>
```

- Takes one free-hand device from `docs/DEVICE_FLEET.md` - an emulator or the test phone. Any other serial is refused before adb is touched.
- Long (two device runs); run it in the background.
- The connected task uninstalls the app and the test APK when it finishes, so the device ends with no app installed.
- Artifacts: `temp/selftest/<timestamp>/` - `provision.json`, `default/` and `hilt/` (JUnit XML and gradle log each), `verdict.json`.
- Exit codes: 0 every test that ran passed; 1 a test failed or provisioning failed on the device; 2 nothing could be verified (provisioning could not run, or a pass produced no JUnit XML, or zero tests ran).

`.\a.ps1 fam -DeviceId <serial>` still runs the Room migration tests alone - the release-blocking database-upgrade proof.

---

## 2. What is provisioned, and where

The connected task reinstalls the app, which wipes runtime grants and every private preference. So the work is split by what survives that reinstall.

**Device side - `scripts/devtest/selftest-provision.ps1`, run by `fst` first:**

- `/storage/emulated/0/TestMedia` (`TestFixtures.TEST_LOCAL_FOLDER`) is created and receives the fixture media from `assets/s0116_fixtures/` (`TestFixtures.DEVICE_FIXTURE_FILES`), then a media scan is requested.
- The window, transition and animator duration scales are set to 0 - Espresso refuses some actions while animations run.
- The network credentials file (section 3) is pushed to `/data/local/tmp/fms-selftest-network.properties`, or a stale copy is removed.

**App side - `selftest/SelfTestBaselineRule`, applied as a JUnit rule by a test:**

- Every dangerous permission the package requests is granted (media, notifications, camera, microphone, location, local network where the API level has it).
- Onboarding is marked complete with the full four-key `welcome_prefs` map.
- The collapsible settings sections start from their defaults.

---

## 3. Network endpoints

Network tests (`NetworkReachabilityDeviceTest`) verify port reachability and authenticated directory listing. They read their endpoints from a properties file that lives on the workstation, outside the repository:

- Path: `FMS_SELFTEST_NETWORK_FILE`, or `$HOME/.fms/selftest-network.properties` when the variable is unset.
- Keys per protocol (`smb`, `sftp`, `ftp`): `<p>.host`, `<p>.port`, `<p>.path`, `<p>.user`, `<p>.password`.
- A protocol with no `<p>.host` is not provisioned: its tests are skipped with the reason `network not provisioned: <p>`. The verdict lists every skip with its reason and never counts it as a pass.
- On the device the file is read through the shell uid (`UiAutomation`), and no script prints its values.

### Endpoint Configuration Matrix

| Environment | SMB host | SFTP host | FTP host | Notes |
|-------------|----------|-----------|----------|-------|
| Emulator | `10.0.2.2` | `10.0.2.2` | `10.0.2.2` | Routes to workstation loopback |
| Test Phone | LAN IP (e.g. `192.168.1.50`) | LAN IP | LAN IP | Must be reachable on local Wi-Fi |
| Docker / WSL | Host or bridged IP | Host or bridged IP | Host or bridged IP | Standard exposed ports |

Example `$HOME/.fms/selftest-network.properties`:

```properties
# SMB (port defaults to 445 if omitted; path is share name or share/subfolder)
smb.host=192.168.1.10
smb.port=445
smb.path=/test-share
smb.user=tester
smb.password=secret

# SFTP (port defaults to 22 if omitted; path defaults to . or /)
sftp.host=192.168.1.10
sftp.port=2222
sftp.path=/data
sftp.user=tester
sftp.password=secret

# FTP (port defaults to 21 if omitted)
ftp.host=192.168.1.10
ftp.port=21
ftp.path=/
ftp.user=tester
ftp.password=secret
```

---

## 4. The two passes

- **Default pass** - `FmsAndroidTestRunner`, the real `@HiltAndroidApp` application; every test except `@HiltAndroidTest` classes.
- **Hilt pass** - `-Pfms.hiltTestRunner=true` selects `FmsHiltTestRunner`, which creates `HiltTestApplication` so `@TestInstallIn` / `@UninstallModules` take effect, and runs only `@HiltAndroidTest` classes.
- One instrumentation process holds one Application, which is why the two kinds cannot share a pass. An explicit `annotation` / `notAnnotation` runner argument from the caller overrides the default filter.

---

## 5. Adding a device test

1. Put it under `selftest/` when it is part of the self-test coverage, otherwise beside the code it tests.
2. Apply `SelfTestBaselineRule` as a `@get:Rule` when it launches UI or reads shared storage.
3. For a network endpoint call `SelfTestNetworkConfig.assumeProtocol(..)` first.
4. Use `TestFixtures.*` constants for names and paths; a new fixture file goes into `assets/` and into `TestFixtures.DEVICE_FIXTURE_FILES` when the device needs it in `TestMedia`.
5. Wait on events (listeners, latches, `waitForIdleSync`), never on a fixed sleep.

---

## 6. Test constants reference

All constants live in `TestFixtures.kt`:

- `DEFAULT_USER` - `test-default-user`
- `DEFAULT_SHARE_PATH` - `/test-share`
- `TEST_SMB_RESOURCE_NAME` / `TEST_SFTP_RESOURCE_NAME` / `TEST_FTP_RESOURCE_NAME` / `TEST_CLOUD_RESOURCE_NAME`
- `TEST_LOCAL_FOLDER` - `/storage/emulated/0/TestMedia`
- `DEVICE_FIXTURE_FILES` - the fixture files provisioning pushes into `TEST_LOCAL_FOLDER`

---

## 7. BD-TS minimal asset

`assets/test_media/minimal.ts` is a 188-byte single MPEG-TS packet (sync byte `0x47`, PAT PID, stuffed with `0xFF`), used by `BdTsPlaybackInstrumentationTest`. Test-APK assets are opened through the instrumentation context (`InstrumentationRegistry.getInstrumentation().context`), never the app context.
