# FastMediaSorter - QR / `.fmscfg` import contract (Android side)

> Audience: the Android FastMediaSorter app developer implementing "Add resource -> import from companion".
> Status: implementation spec. The wire contract below is **frozen at schemaVersion 1** and is identical on both ends.
> Source of truth (Windows side): `ShareConfigBuilder.vb` in FastMediaSorter LITE, byte-faithful to the companion contract `docs/CONFIG_FORMAT.md` + `internal/config/schema.go`.

---

## 0. What this is, and what changed

The Windows app (FastMediaSorter LITE) can share PC folders with the phone over SFTP. It exports a **CompanionResourceConfig** - a small JSON document that carries everything the phone needs to add a working, read-only SFTP resource in one action: address(es), credentials, the server host-key fingerprint to pin, and the list of shared folders.

The phone receives it in one of two ways:

- **Scan a QR code** shown in the app, or
- **Open a `.fmscfg` file** saved from the app.

**What changed vs the earlier standalone-companion flow (nothing you must react to):** LITE now builds this JSON and renders the QR **itself** (previously a separate companion exe did). The bytes are still byte-faithful to the same schemaVersion-1 contract, so an importer written to this spec accepts both. The only practical difference: the internet (`portforward`) access path can now appear even when the port was forwarded **manually** on the router, not only via UPnP. Your parser needs no special case - it is just another `accessPaths` entry.

The Windows app never contacts any server on your behalf. The phone connects **directly** to the PC over SSH/SFTP.

---

## 1. Transport variants and decoding

A payload is either the **plain JSON** or a **compressed** variant. Decide by the first byte / prefix:

| Variant | How to detect | How to decode to JSON |
|---|---|---|
| Plain JSON | Payload starts with `{` | Use the string as-is (UTF-8). |
| Compressed | Payload starts with `FMSCFG1:` | Strip the `FMSCFG1:` prefix; the rest is **base64 (standard, may be padded) of gzip of the UTF-8 JSON**. base64-decode, then gunzip, then UTF-8-decode. |

- **File** (`*.fmscfg`): always plain JSON, UTF-8, **no BOM**. (Still run it through the same detector - be liberal.)
- **QR code**: plain JSON when small, else the `FMSCFG1:` compressed form (LITE switches to compressed when the JSON exceeds ~900 bytes, so dense QR codes stay scannable). **You must support both.** QR error-correction level is M (Medium) - irrelevant to decoding, any standard QR reader handles it.

Decode pipeline (Kotlin sketch):

```kotlin
fun decodeFmscfgPayload(raw: String): String {
    val s = raw.trim()
    if (s.startsWith("FMSCFG1:")) {
        val b64 = s.removePrefix("FMSCFG1:")
        val gz = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        return java.util.zip.GZIPInputStream(gz.inputStream())
            .readBytes().toString(Charsets.UTF_8)
    }
    return s // plain JSON
}
```

Note: base64 here is **standard** (`+`/`/`, `=` padding), not URL-safe. `Base64.DEFAULT` decodes it. Whitespace/newlines inside a scanned payload should be tolerated.

---

## 2. Schema (version 1)

The decoded JSON is a single object:

```jsonc
{
  "schemaVersion": 1,                       // int  - contract version
  "resourceName": "FastMediaSorter Companion on MARK", // string - suggested display name
  "protocol": "sftp",                       // string - ALWAYS "sftp"; reject anything else
  "accessPaths": [                          // array, ORDERED (try in order)
    { "kind": "lan",         "host": "192.168.1.100", "port": 55259 },
    { "kind": "portforward", "host": "46.54.0.135",   "port": 55259 }
  ],
  "username": "fms",                        // string - SFTP username
  "password": "ucDphzKZ4PMHr3G9nLzm5UPZ",   // string - SFTP password (embedded by design)
  "hostKeyFingerprintSha256": "SHA256:mOdVHW8J8EXU4qE+NR0NvQKR+j00xj8Db3LqcgMiKeI", // pin this (TOFU)
  "roots": [                                // array - shared folders
    { "virtualPath": "/MOV", "label": "MOV" }
  ],
  "createdAt": "2026-07-11T01:00:00Z"       // string - RFC 3339 UTC
}
```

| Field | Type | Required | Meaning / rules |
|---|---|---|---|
| `schemaVersion` | int | yes | Contract version. **Reject if greater than you support** (see 5.1). |
| `resourceName` | string | yes | Human-readable name to prefill for the created resource. User may rename. |
| `protocol` | string | yes | Always `"sftp"` in this version. **Reject any other value.** |
| `accessPaths` | array | yes, non-empty | Ordered ways to reach the server. Try in order (LAN first). See 3. |
| `accessPaths[].kind` | string | yes | `"lan"` or `"portforward"`. Treat unknown kinds as "try it anyway, lowest priority" (forward-compat), but current exporter only emits these two. |
| `accessPaths[].host` | string | yes | IPv4/hostname to dial. |
| `accessPaths[].port` | int | yes | TCP port (1..65535). |
| `username` | string | yes | SFTP username (currently always `fms`, but do not hardcode). |
| `password` | string | yes | SFTP password, high-entropy, embedded by design. Store encrypted at rest. |
| `hostKeyFingerprintSha256` | string | yes | `SHA256:<base64>` of the server host key. **Pin on first connect (TOFU); refuse if it ever changes.** See 4. |
| `roots` | array | yes, non-empty | Shared folders as the client sees them. |
| `roots[].virtualPath` | string | yes | Absolute virtual path on the server, e.g. `/MOV`. Always starts with `/`. |
| `roots[].label` | string | yes | Display label for the folder. |
| `createdAt` | string | yes | RFC 3339 UTC timestamp of the export. Informational (freshness / dedupe). |

**Field order in the JSON is fixed** by the exporter (as shown), but **parse order-independently** by field name - do not rely on positional parsing.

**Unknown fields:** ignore them (forward compatibility - see 5.1). Do not fail on extra keys.

Kotlin model:

```kotlin
data class CompanionConfig(
    val schemaVersion: Int,
    val resourceName: String,
    val protocol: String,
    val accessPaths: List<AccessPath>,
    val username: String,
    val password: String,
    val hostKeyFingerprintSha256: String,
    val roots: List<ConfigRoot>,
    val createdAt: String
)
data class AccessPath(val kind: String, val host: String, val port: Int)
data class ConfigRoot(val virtualPath: String, val label: String)
```

---

## 3. Access paths - connection strategy

`accessPaths` is **ordered by preference**: `lan` first, then `portforward`. The list is "the set of addresses that currently point at the same one SFTP server". All entries share the **same** username, password and host-key fingerprint - only host/port differ.

Recommended connect strategy when opening the resource:

1. Try entries **in listed order**. LAN first so nearby devices never round-trip the internet.
2. For each entry: open a TCP/SFTP connection to `host:port`, verify the host key (section 4), authenticate (section 5.2). First success wins - remember which entry worked for this session; on the next open, you may retry the last-good entry first, then fall back to the full list.
3. If all entries fail, surface a clear error (unreachable / wrong network / port not forwarded).

Notes:
- `lan` only works when the phone is on the **same network** as the PC. Off-network it will fail fast - fall through to `portforward`.
- A `portforward` entry may be present but **not yet reachable** if the user has not finished forwarding the port on their router. Treat a connection failure on one entry as "try the next", not a fatal import error - the resource is still valid and will work once the port is open.
- Do **not** assume exactly two entries. There may be one (LAN only) or more in future.

---

## 4. Host key: trust on first use (TOFU) - MANDATORY

The server presents an **ed25519** SSH host key. `hostKeyFingerprintSha256` is its fingerprint in the standard OpenSSH format:

```
SHA256:<base64(SHA-256(ssh-wire-format-public-key))>     // base64 std alphabet, no '=' padding
```

This is exactly what `ssh-keygen -lf` / OpenSSH prints and what `net.schmizz.sshj` `SecurityUtils` / `KeyType` produce. Rules:

1. On import, store the fingerprint from the config as the **pinned** key for this resource.
2. On **every** connection, compute the SHA-256 fingerprint of the host key the server actually presents and require it to **equal** the pinned value (constant-time compare). 
3. If it does **not** match -> **refuse the connection** and show a "host key changed" security warning. Do not auto-update the pin. (A changed key means the PC was reinstalled / cleaned, or a MITM. The Windows side is documented to never regenerate the key while it is valid - a mismatch is meaningful.)
4. Never fall back to "accept any key". The embedded password is only safe because the endpoint is authenticated by this pin.

sshj example (pin the `SHA256:` fingerprint). `FingerprintVerifier.getInstance` accepts the OpenSSH `SHA256:<base64>` string directly and returns a `HostKeyVerifier` that does the correct SHA-256 comparison - use it rather than `SecurityUtils.getFingerprint` (which returns an **MD5** colon-hex fingerprint, not what we pin):

```kotlin
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.FingerprintVerifier

val ssh = SSHClient()
ssh.addHostKeyVerifier(FingerprintVerifier.getInstance(pinnedFingerprint)) // "SHA256:...."
// then ssh.connect(host, port); ssh.authPassword(username, password); val sftp = ssh.newSFTPClient()
```

If you verify the fingerprint yourself instead: compute `SHA-256` over the server key's SSH wire bytes, base64-encode (standard alphabet, **strip `=` padding**), prepend `SHA256:`, and constant-time compare with the pinned value. If your library hands you the digest differently, normalize both sides (strip `SHA256:` and padding) - ideally compare the raw 32-byte digests.

> JSch note: JSch does not expose a clean SHA-256 host-key pin; if you use JSch, implement a custom `HostKeyRepository`/`check` that computes SHA-256 of `HostKey.getKey()` (base64-decoded) and compares. sshj is the simpler path for TOFU pinning.

---

## 5. Parsing, validation, auth

### 5.1 Validation rules

- Accept `schemaVersion` `1` **and** `2` - this release targets both. Reject only when `schemaVersion` **>** the highest you implement (currently `2`) -> show "update the app" (partial import of nothing). A version lower than yours stays importable through additive back-compat. Rationale and forward-compat rules in §5.4.
- Reject when `protocol != "sftp"`.
- Reject when `accessPaths` or `roots` is empty, or when required string fields are blank (`username`, `password`, `hostKeyFingerprintSha256`).
- Ignore unknown top-level or nested fields (forward compat).
- Validate each `accessPaths[].port` in 1..65535 and `host` non-empty.
- `createdAt` is informational; tolerate parse failure (do not block import on it).

### 5.2 SFTP authentication

- Transport: **SSH2 / SFTP subsystem** (standard). Open one `session` channel, request subsystem `sftp`.
- Auth method: **password only** (`username` + `password` from the config). The server accepts exactly one credential pair and rejects everything else. Do **not** attempt public-key or keyboard-interactive.
- The server is **read-only**: treat all roots as read-only. Do not offer upload/delete/rename in the UI for these resources (writes will fail server-side anyway).

### 5.3 Roots -> resources

The SFTP server exposes **all** shared folders under a single virtual root `/`, each as a top-level directory named by its `virtualPath` (e.g. `/MOV`, `/Photos`). Recommended mapping (matches the shipped behavior):

- Create **one read-only resource per `roots[]` entry**, each rooted at its `virtualPath`, labelled with `roots[].label`, all sharing the same connection (host list + credentials + pinned key).
- Alternatively a single resource rooted at `/` that lists every shared folder - your product choice. Either way, `virtualPath` tells you where each folder lives.

### 5.4 Schema versioning & backward-compatibility audit

The output file (`.fmscfg` / QR) is built by the LITE/Windows side (`ShareConfigBuilder`), not by the Go worker. A future two-tier "packages of typed shares" model on the sender reorganizes **how the sender picks roots**, not the **shape** of the emitted file: it still serializes the selected `roots[]` plus their per-root parameters. Every future concept maps onto an existing or additive field:

- Package = subset of shares -> `roots[]`
- Per-share resource name -> `resourceName` / `roots[].label`
- Per-share type (e.g. "video library") -> `roots[].profile` (v2 field)
- Hard read-only per share -> `roots[].readOnly` (already enforced worker-side, S1016)
- Soft read-only in a package -> `roots[].readOnly` (hint to the client)
- Destination / PIN / slideshow -> `isDestination` / `accessPin` / `slideshowInterval` (v2 fields)
- Shared login -> `username` / `password`

Because typed shares carry `profile` (a v2 field), future exports will almost always be `schemaVersion:2`. Therefore a client shipped now must, to stay forward-compatible without a re-release:

1. **Accept `schemaVersion` 1 AND 2** (see §5.1). A v1-only client would reject every typed share and every destination.
2. **Ignore unknown fields** - additive v2+ fields must not fail parsing.
3. **Degrade unknown enum tokens softly** - unknown `roots[].profile`, `mediaTypes`, or `accessPaths[].kind` values fall back to a safe default (treat as a plain browsable read-only root / skip the unreachable path), never reject the whole config.

With those three, future extensions are additive-only: a new optional field is ignored by an already-shipped client. Compatibility breaks only if the producer raises `schemaVersion` above what the client implements, changes the meaning of an existing field, or makes an unknown field mandatory - none of which the future model requires. Conclusion: the current contract is stable; ship the Android client on it, provided the three rules above hold. The single practical pre-release check is that the parser accepts `schemaVersion == 2`, not strictly `== 1`.

---

## 6. Security notes (surface these to the user)

- The QR code and `.fmscfg` file **contain the password**. Treat them as secrets: warn the user not to screenshot/share them. (The Windows app shows the same warning.)
- Store the imported `password` **encrypted at rest** (Android Keystore / EncryptedSharedPreferences), not in plaintext.
- The pinned host-key fingerprint is what makes the embedded password safe - never weaken the TOFU check (section 4).
- `portforward` means the PC is intentionally reachable from the internet on that port. That is the user's choice on the Windows side; the phone just connects.

---

## 7. Test vectors

### 7.1 Canonical vector (frozen - byte-identical on both ends)

Plain-JSON QR / file content:

```json
{"schemaVersion":1,"resourceName":"Home PC","protocol":"sftp","accessPaths":[{"kind":"lan","host":"192.168.1.23","port":2022},{"kind":"portforward","host":"203.0.113.7","port":2022}],"username":"fms","password":"k7PmQ2wXr9TzS4vGnHb3JdLe","hostKeyFingerprintSha256":"SHA256:8f6TQvCbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYqhk","roots":[{"virtualPath":"/Photos","label":"Photos"},{"virtualPath":"/Music","label":"Music"}],"createdAt":"2026-07-10T12:00:00Z"}
```

Keep this as a parser fixture and assert every field decodes as in the table above.

### 7.2 Real LITE output - external ON (LAN + port-forward, one QR)

Produced live by `ShareConfigBuilder.Build(status, includeExternal:=True)`:

```json
{"schemaVersion":1,"resourceName":"FastMediaSorter Companion on MARK","protocol":"sftp","accessPaths":[{"kind":"lan","host":"192.168.1.100","port":55259},{"kind":"portforward","host":"46.54.0.135","port":55259}],"username":"fms","password":"ucDphzKZ4PMHr3G9nLzm5UPZ","hostKeyFingerprintSha256":"SHA256:mOdVHW8J8EXU4qE+NR0NvQKR+j00xj8Db3LqcgMiKeI","roots":[{"virtualPath":"/MOV","label":"MOV"}],"createdAt":"2026-07-11T01:00:00Z"}
```

### 7.3 Real LITE output - external OFF (LAN only)

Same, with the `portforward` entry absent (the external toggle drops it):

```json
{"schemaVersion":1,"resourceName":"FastMediaSorter Companion on MARK","protocol":"sftp","accessPaths":[{"kind":"lan","host":"192.168.1.100","port":55259}],"username":"fms","password":"ucDphzKZ4PMHr3G9nLzm5UPZ","hostKeyFingerprintSha256":"SHA256:mOdVHW8J8EXU4qE+NR0NvQKR+j00xj8Db3LqcgMiKeI","roots":[{"virtualPath":"/MOV","label":"MOV"}],"createdAt":"2026-07-11T01:00:00Z"}
```

### 7.4 Compressed variant

For a `FMSCFG1:` fixture, take any JSON above, `gzip` it, base64 (standard) the result, prefix `FMSCFG1:`. Because gzip headers (mtime) are not byte-stable across libraries, assert the **round-trip** (decode -> equals the original JSON), not exact bytes. Any config whose JSON exceeds ~900 bytes (many roots / long paths) arrives in this form.

### 7.5 Forward-compat vector - `schemaVersion` 2 (typed shares)

Illustrates the future two-tier model (§5.4): `schemaVersion` is `2`, `roots[]` carry additive v2 fields (`profile`, `readOnly`), plus a top-level additive field (`isDestination`). A client shipped now must import this successfully - accept version `2`, honor `readOnly`, and **ignore/degrade** every field it does not yet understand (`profile`, `isDestination`, and the unknown `profile` token `book_library`), never rejecting the config.

```json
{"schemaVersion":2,"resourceName":"Home PC","protocol":"sftp","accessPaths":[{"kind":"lan","host":"192.168.1.23","port":2022},{"kind":"portforward","host":"203.0.113.7","port":2022}],"username":"fms","password":"k7PmQ2wXr9TzS4vGnHb3JdLe","hostKeyFingerprintSha256":"SHA256:8f6TQvCbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYqhk","isDestination":false,"roots":[{"virtualPath":"/Movies","label":"Movies","profile":"video_library","readOnly":true},{"virtualPath":"/Books","label":"Books","profile":"book_library","readOnly":true}],"createdAt":"2026-07-13T09:00:00Z"}
```

Assert: version `2` accepted; two read-only resources created (`/Movies`, `/Books`); `profile:"video_library"` may drive UI if implemented, `book_library` (unknown) degrades to a plain browsable read-only root; `isDestination` ignored if unimplemented; no parse failure.

---

## 8. Importer acceptance checklist

- [ ] Decodes plain-JSON QR and `.fmscfg` file (UTF-8, tolerant of whitespace/no-BOM).
- [ ] Decodes `FMSCFG1:` + base64(gzip) QR (round-trip equals plain JSON).
- [ ] Parses all fields of the canonical vector (7.1) correctly.
- [ ] Accepts `schemaVersion` `1` **and** `2` (imports the §7.5 typed-share vector); rejects only `schemaVersion` > supported with an "update the app" message.
- [ ] Rejects `protocol != "sftp"` and empty `accessPaths`/`roots`.
- [ ] Ignores unknown fields without failing, and degrades unknown enum tokens (`roots[].profile`, `mediaTypes`, `accessPaths[].kind`) to a safe default instead of rejecting.
- [ ] Connects trying `accessPaths` in order (LAN first), falling through on failure.
- [ ] Pins `hostKeyFingerprintSha256` (TOFU) and **refuses** on later mismatch; never accepts an unpinned key.
- [ ] Authenticates with `username`/`password` (password auth only), treats the resource as read-only.
- [ ] Creates one read-only resource per `root` (or one rooted at `/`), stores the password encrypted.
- [ ] Browses and plays media from the shared folder over SFTP (LAN), and over the internet when a `portforward` path is reachable.
```
