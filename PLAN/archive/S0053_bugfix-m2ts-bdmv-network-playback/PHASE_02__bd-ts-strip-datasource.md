# Phase 02 — BdTsStripDataSource

**Strategic spec:** [`../S0053_bugfix-m2ts-bdmv-network-playback.md`](../S0053_bugfix-m2ts-bdmv-network-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 4
**Started:** —
**Completed:** 2026-05-02

---

## Objective

Implement `BdTsStripDataSource` and `BdTsStripDataSourceFactory` — a transparent `DataSource` wrapper that strips the 4-byte Blu-ray timestamp header from each 192-byte BD-TS packet, exposing a standard 188-byte MPEG-TS stream to ExoPlayer's `TsExtractor`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/` directory exists (confirmed: contains `SftpDataSource.kt`, `SmbDataSource.kt`, `FtpDataSource.kt`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSource.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSourceFactory.kt` | New | ≤ 40 |

---

## Steps

### Step 2.1 — Create BdTsStripDataSource

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSource.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the file with the following content. The class wraps any `DataSource` and transforms each 192-byte BD-TS packet into a 188-byte MPEG-TS packet by discarding the 4-byte timestamp prefix.
>
> **Key invariant for `open(DataSpec)`:**  
> A seek to logical position `tsPos` (in the 188-byte stream) maps to BD-TS byte position:
> ```
> packetIndex = tsPos / TS_PACKET_SIZE          // which 188-byte packet
> byteWithinPacket = tsPos % TS_PACKET_SIZE     // offset inside that packet
> bdPos = packetIndex * BD_PACKET_SIZE + BD_HEADER_SIZE + byteWithinPacket
> ```
> The upstream `DataSpec` must carry this translated position so SFTP/SMB/FTP DataSources seek correctly.
>
> **Key invariant for `read(buffer, offset, length)`:**  
> Read is packet-aware: before reading data bytes from the current packet, consume any remaining bytes in the 192-byte packet including the 4-byte header of the NEXT packet. Use a 192-byte scratch buffer (`packetBuf`) to read one full BD-TS packet at a time; expose only bytes 4..191 (the TS payload).
>
> ```kotlin
> package com.sza.fastmediasorter.data.network.datasource
>
> import androidx.media3.common.C
> import androidx.media3.datasource.DataSource
> import androidx.media3.datasource.DataSpec
> import androidx.media3.datasource.TransferListener
> import timber.log.Timber
>
> internal class BdTsStripDataSource(private val upstream: DataSource) : DataSource {
>
>     companion object {
>         private const val BD_PACKET_SIZE = 192
>         private const val TS_PACKET_SIZE = 188
>         private const val BD_HEADER_SIZE = BD_PACKET_SIZE - TS_PACKET_SIZE // 4
>     }
>
>     private val packetBuf = ByteArray(BD_PACKET_SIZE)
>     private var tsPayloadOffset = BD_HEADER_SIZE    // current read cursor within packetBuf (starts after header)
>     private var tsPayloadEnd = BD_HEADER_SIZE       // exclusive end of valid TS bytes in packetBuf
>     private var opened = false
>
>     override fun open(dataSpec: DataSpec): Long {
>         // Translate logical TS position → BD-TS byte position in the upstream
>         val tsPos = dataSpec.position
>         val packetIndex = tsPos / TS_PACKET_SIZE
>         val byteWithinPacket = (tsPos % TS_PACKET_SIZE).toInt()
>         val bdPos = packetIndex * BD_PACKET_SIZE + BD_HEADER_SIZE + byteWithinPacket
>
>         val translatedSpec = dataSpec.buildUpon()
>             .setPosition(bdPos)
>             .build()
>
>         val upstreamLength = upstream.open(translatedSpec)
>         opened = true
>
>         // Reset packet buffer state; if we opened mid-packet, pre-position inside it
>         tsPayloadOffset = BD_HEADER_SIZE + byteWithinPacket
>         tsPayloadEnd = BD_HEADER_SIZE   // no full packet read yet; will be loaded on first read()
>
>         val tsLength = if (upstreamLength == C.LENGTH_UNSET.toLong()) {
>             C.LENGTH_UNSET.toLong()
>         } else {
>             // Approximate: round full packets + remaining bytes
>             val fullPackets = upstreamLength / BD_PACKET_SIZE
>             val tail = (upstreamLength % BD_PACKET_SIZE).coerceAtMost(TS_PACKET_SIZE.toLong())
>             fullPackets * TS_PACKET_SIZE + tail
>         }
>         Timber.d("BdTsStripDataSource: open tsPos=%d bdPos=%d upstreamLength=%d tsLength=%d", tsPos, bdPos, upstreamLength, tsLength)
>         return tsLength
>     }
>
>     override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
>         if (length == 0) return 0
>         var bytesRead = 0
>         while (bytesRead < length) {
>             if (tsPayloadOffset >= tsPayloadEnd) {
>                 // Load next BD-TS packet
>                 var totalRead = 0
>                 while (totalRead < BD_PACKET_SIZE) {
>                     val n = upstream.read(packetBuf, totalRead, BD_PACKET_SIZE - totalRead)
>                     if (n == C.RESULT_END_OF_INPUT) {
>                         return if (bytesRead > 0) bytesRead else C.RESULT_END_OF_INPUT
>                     }
>                     totalRead += n
>                 }
>                 tsPayloadOffset = BD_HEADER_SIZE
>                 tsPayloadEnd = BD_PACKET_SIZE
>             }
>             val toCopy = minOf(tsPayloadEnd - tsPayloadOffset, length - bytesRead)
>             System.arraycopy(packetBuf, tsPayloadOffset, buffer, offset + bytesRead, toCopy)
>             tsPayloadOffset += toCopy
>             bytesRead += toCopy
>         }
>         return bytesRead
>     }
>
>     override fun getUri() = upstream.uri
>
>     override fun close() {
>         if (opened) {
>             upstream.close()
>             opened = false
>             tsPayloadOffset = BD_HEADER_SIZE
>             tsPayloadEnd = BD_HEADER_SIZE
>         }
>     }
>
>     override fun addTransferListener(transferListener: TransferListener) {
>         upstream.addTransferListener(transferListener)
>     }
> }
> ```

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSource.kt` exists.
- `Grep` — `class BdTsStripDataSource` matches exactly once.
- `Grep` — `BD_PACKET_SIZE = 192` present.
- `Grep` — `BD_HEADER_SIZE = BD_PACKET_SIZE - TS_PACKET_SIZE` present.
- `Grep` — `Log\.d(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 5/5 PASS. Files: BdTsStripDataSource.kt (new, 85 LOC). class present, BD_PACKET_SIZE=192, BD_HEADER_SIZE=BD_PACKET_SIZE-TS_PACKET_SIZE, no Log.d.

---

### Step 2.2 — Create BdTsStripDataSourceFactory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSourceFactory.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Create the factory that wraps any `DataSource.Factory` and returns `BdTsStripDataSource`:
>
> ```kotlin
> package com.sza.fastmediasorter.data.network.datasource
>
> import androidx.media3.datasource.DataSource
>
> internal class BdTsStripDataSourceFactory(
>     private val upstream: DataSource.Factory
> ) : DataSource.Factory {
>     override fun createDataSource(): DataSource =
>         BdTsStripDataSource(upstream.createDataSource())
> }
> ```

**Verification:**

- `Glob` — `BdTsStripDataSourceFactory.kt` exists in `data/network/datasource/`.
- `Grep` — `class BdTsStripDataSourceFactory` matches exactly once.
- `Grep` — `BdTsStripDataSource(upstream.createDataSource())` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: BdTsStripDataSourceFactory.kt (new, 9 LOC). class + createDataSource() delegation present.

---

### Step 2.3 — Unit-test the position translation formula

**Files:** (no test file created — formula is verified analytically here)
**Depends on:** Step 2.1

**Prompt for developer:**

> Verify the seek math analytically (no file write needed):
>
> - Seek to tsPos = 0 → bdPos = 0 * 192 + 4 + 0 = 4. Correct: first payload byte of first packet.
> - Seek to tsPos = 188 → bdPos = 1 * 192 + 4 + 0 = 196. Correct: first byte of second packet payload.
> - Seek to tsPos = 100 → bdPos = 0 * 192 + 4 + 100 = 104. Correct: byte 100 inside first packet payload.
> - Seek to tsPos = 1000 → packetIndex = 5, byteWithin = 60 → bdPos = 5*192+4+60 = 1024. Check: packet 5 starts at 5*192=960, header is at 960..963, payload starts at 964, byte 60 of payload = 1024. Correct.
>
> No code change needed — this step marks the formula as verified.

**Verification:**

- This step is analytical. Mark `[x]` after confirming all four examples above are correct.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Analytical verification PASS. All 4 examples correct: tsPos 0→4, 188→196, 100→104, 1000→1024.

---

### Step 2.4 — Verify no Log.d usage in new files

**Files:** Both new files from 2.1 and 2.2
**Depends on:** Steps 2.1, 2.2

**Prompt for developer:**

> Run:
> ```
> Grep pattern "Log\.d\(" in BdTsStripDataSource.kt and BdTsStripDataSourceFactory.kt
> ```
> Expect zero matches.

**Verification:**

- `Grep` — `Log\.d(` returns zero hits in `BdTsStripDataSource.kt`.
- `Grep` — `Log\.d(` returns zero hits in `BdTsStripDataSourceFactory.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. No Log.d in either new file.

---

## Phase Done Criteria

- [x] Every `Step 2.*` above is `[x] done`.
- [x] Project compiles — run `/build` (do not invoke gradle directly). (auto-build — PASS, 2026-05-02)
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (new classes). [deferred to Phase 04]

---

## Handoff Notes to Next Phase

Phase 02 establishes:
- `BdTsStripDataSource` — transparent 192→188 byte adapter, seekable.
- `BdTsStripDataSourceFactory` — wraps any `DataSource.Factory`.

Phase 03 wires these into the SFTP, SMB, and FTP playback helpers for `.m2ts`/`.m2t` files.

---

## Rollback Plan

Revert phase commit(s) — new files only, no existing code changed. No migration or data risk.
