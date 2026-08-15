# Research 02 - Happy-eyeballs probe: cost, timeout, parallelism

**§6 item:** 2 - probe tuning without added latency or false rejects
**Status:** Resolved
**Method:** reading existing SFTP connection timeouts + reachability semantics (2026-07-12).

## Finding: probe with a raw TCP connect, short timeout, parallel, LAN-first grace

- Reachability = "can a TCP connection to host:port be opened", which is a **raw `java.net.Socket().connect(InetSocketAddress(host, port), timeoutMs)`** on `Dispatchers.IO`, immediately closed. This is far cheaper than a full SFTP/JSch handshake (no key exchange, no auth) and is exactly what times out in the field failure (`SocketTimeoutException` at `Socket.connect`).
- The full SFTP connect timeout is `SftpConnectionTester.CONNECTION_TIMEOUT = 10_000` ms - too long for a candidate probe. A reachable LAN or a working port-forward completes the TCP handshake in well under a second; an unreachable one is dead weight. Use a **short probe timeout (~2500-3000 ms per candidate)**; on failure the candidate is simply dropped.
- **Parallelism (happy-eyeballs):** launch a probe coroutine per candidate concurrently. Prefer the LAN candidate: if the LAN probe succeeds within a short grace window it wins even if a WAN probe also succeeded (LAN is faster and independent of port-forward). Contract order (LAN first) is the preference order. If LAN fails, the first WAN candidate to connect wins. If all fail, resolution fails and the caller surfaces the normal connection error - no candidate should extend the wait beyond one probe timeout.
- **Caching:** cache the winning endpoint per `(resource identity + network epoch)` with a short TTL so steady-state file operations, thumbnails and playback reads pay zero probe latency. Only a cold connection in a new network epoch pays one probe round. Warm/pooled sessions in `SftpConnectionPool` are unaffected - they already hold a live transport and never re-enter resolution.

## Design implication

- Resolver exposes a suspend `resolve(host, port): Endpoint?` returning the reachable candidate (cached or freshly probed). Probe = parallel raw-socket race with LAN-first grace, per-candidate timeout ~2.5-3 s.
- Do not route the probe through JSch/SftpClient - a bare socket keeps it cheap and independent of auth/host-key.
- The 10 s SFTP handshake timeout stays for the actual connection; the probe only selects which host that handshake targets.
