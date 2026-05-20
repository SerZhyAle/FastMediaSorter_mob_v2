# Instrumentation Test Prerequisites

This file describes what must be present on the test device before running the androidTest suite.

---

## 1. Required Local Folder

The following folder must exist on the test device:

```
TestFixtures.TEST_LOCAL_FOLDER = /storage/emulated/0/TestMedia
```

Create it manually before running any test that references local media. Tests that use `context.cacheDir` create their own temp folders and do not require this.

---

## 2. Network-Required Tests

Some tests require a real network resource (SMB share, SFTP server, FTP server) and are annotated with `@NetworkRequired`. These tests use `assumeTrue(isNetworkTestEnabled())` internally and are **skipped automatically** when the network resource is unavailable or the flag is not set.

To run network tests, set the following in `local.properties` or as an instrumentation argument:

```
test.network.enabled=true
```

---

## 3. Test Constants Reference

All string constants are defined in `TestFixtures.kt`. Current values:

| Constant | Value |
|---|---|
| `DEFAULT_USER` | `test-default-user` |
| `DEFAULT_SHARE_PATH` | `/test-share` |
| `TEST_SMB_RESOURCE_NAME` | `Test-SMB` |
| `TEST_SFTP_RESOURCE_NAME` | `Test-SFTP` |
| `TEST_FTP_RESOURCE_NAME` | `Test-FTP` |
| `TEST_LOCAL_FOLDER` | `/storage/emulated/0/TestMedia` |
| `TEST_CLOUD_RESOURCE_NAME` | `Test-Cloud` |

To adapt these for a different test environment, edit **only** `TestFixtures.kt` - all tests reference these constants by name.

---

## 4. How to Add a New Device-Dependent Test

1. Annotate the test class or method with `@NetworkRequired`.
2. In `@Before` or at the start of the test, call `assumeTrue(isNetworkTestEnabled())` - the test will be skipped rather than failing if the environment is not configured.
3. Use `TestFixtures.*` constants for all resource names, paths, and credentials.
4. Document any additional device prerequisites in this file under a new section.

---

## 5. BD-TS Minimal Asset

`app_v2/src/androidTest/assets/test_media/minimal.ts` is a 188-byte single MPEG-TS packet (sync byte `0x47`, PAT PID, stuffed with `0xFF`). It is used by `BdTsPlaybackInstrumentationTest` and does not require a real video file on the device.
