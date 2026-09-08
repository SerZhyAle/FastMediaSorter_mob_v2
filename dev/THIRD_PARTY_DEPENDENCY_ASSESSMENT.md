# Third-party dependency assessment - measured 2026-09-08

What each third-party library in this project costs, how deeply it is used, and whether it could go.
Ticket: S2680.

This document does not list licences. That is `docs/OPEN_SOURCE.md`, generated from
`scripts/docs/oss-licenses.psd1`, and it stays the only place a licence claim is made. The coordinate
list here is read from that same manifest cross-checked against both build files, so the two documents
cannot name different sets of libraries.

**Validity condition.** These numbers describe the `dependencies` blocks of
`app_v2/build.gradle.kts` and `wear/build.gradle.kts` as they stood on 2026-09-08. Any added,
removed or bumped coordinate invalidates the row it touches and every closure that contains it.

---

## 1. What was measured, and what was not

**Coordinate list.** Every coordinate declared in the `dependencies` block of either module,
including the flavor-scoped and build-type-scoped configurations, minus the four groups the ticket
puts out of scope - `androidx.*`, `com.google.android.*`, `com.google.dagger`, `org.jetbrains.*` -
minus test-only configurations. The bundled binary in `app_v2/libs/` is included; it carries no Maven
coordinate and appears as `local:fms-ffmpeg-dts`.

**Flavor coverage.** Read from the configuration name each declaration uses. A plain
`implementation` reaches all seven flavors; a quoted `"<flavor>Implementation"` reaches only the
flavors named. `docs/FLAVOR_MATRIX.md` is the authority on what each flavor is.

**Sizes.** Two numbers per coordinate:

- **own** - the bytes of that coordinate's own resolved artifact, taken from the local Gradle module
  cache (largest `.aar`/`.jar` for the resolved version, excluding `-sources` and `-javadoc`).
- **closure** - own plus every node reachable from it in the resolved runtime dependency graph.

The graphs come from `gradlew :app_v2:dependencies --configuration <variant>RuntimeClasspath` for
`standardDebug`, `noLegalDebug`, `liteDebug`, `vrDebug` and `fossDebug`, plus the wear module's
`standardDebug`. An `exclude` block removes the edge before resolution, so an excluded artifact never
enters the graph and never enters a closure - confirmed on `cz.adaptech:tesseract4android`, whose
excluded `-openmp` variant appears in none of the six graphs.

**What these numbers are not.** They are not APK contribution. R8 has not run: it strips unreachable
classes and shrinks resources per variant, so a library with a large artifact and two reachable
methods contributes almost nothing, while a native `.so` contributes its full packaged size regardless.
Measuring APK contribution needs a release build per variant and an APK-analyzer diff per library, which
this ticket did not do. Read every number below as "how much this library is", never as "how much this
library costs the user".

**Closures overlap.** Two libraries that both reach the AndroidX tail each report that tail in full.
Closure figures therefore compare a library against itself over time, not against its neighbour; for
comparison between libraries the `own` column is the honest one.

---

## 2. Summary

`n` = nodes in the closure. Verdict is `keep`, `replace` or `candidate`.

| Coordinate | Version | Flavors | own (B) | closure (B) | n | Files | Verdict |
|---|---|---|---:|---:|---:|---:|---|
| org.videolan.android:libvlc-all | 3.7.5 | noLegal | 93158778 | 100538358 | 49 | 1 | keep |
| com.google.mlkit:translate | 17.0.3 | standard, noLegal, legacy, vr | 27684160 | 38666870 | 65 | 2 | keep |
| cz.adaptech:tesseract4android | 4.8.0 | standard, noLegal, legacy, vr | 284 | 14727738 | 6 | 1 | keep |
| local:fms-ffmpeg-dts | bundled AAR | standard, noLegal, legacy, vr | 11495586 | 11495586 | 1 | 0 | keep |
| org.bouncycastle:bcprov-jdk18on | 1.75 | all 7 + wear (transitive) | 8321289 | 8321289 | 1 | 0 | candidate |
| com.dropbox.core:dropbox-core-sdk | 5.4.5 | all except foss | 8027912 | 8694022 | 4 | 3 | replace |
| com.android.tools:desugar_jdk_libs | 2.0.4 | all 7 (desugaring) | 5999671 | 5999671 | 1 | 0 | keep |
| org.khronos.openxr:openxr_loader_for_android | 1.1.48 | vr, noLegal | 3879002 | 3879002 | 1 | 0 | keep |
| com.google.mlkit:language-id | 17.0.6 | standard, noLegal, legacy, vr | 2335810 | 12417022 | 60 | 2 | keep |
| com.google.accompanist:accompanist-permissions | 0.34.0 | wear, all | 1041000 | 27238186 | 67 | 7 | candidate |
| com.squareup.okhttp3:okhttp | 4.12.0 | all 7 + wear | 789531 | 2985126 | 8 | 52 | keep |
| com.github.TeamNewPipe:NewPipeExtractor | v0.26.1 | noLegal | 788306 | 3700916 | 7 | 4 | keep |
| com.github.bumptech.glide:glide | 4.16.0 | all 7 | 703481 | 7720344 | 42 | 62 | keep |
| com.google.zxing:core | 3.5.3 | all 7 + wear | 607650 | 607650 | 1 | 5 | keep |
| com.hierynomus:smbj | 0.12.1 | all 7 + wear | 603711 | 9159041 | 5 | 21 | keep |
| com.microsoft.identity.client:msal | 6.0.1 | all except foss | 566810 | 19786261 | 93 | 2 | replace |
| com.github.mwiede:jsch | 0.2.26 | all 7 + wear | 559927 | 559927 | 1 | 17 | keep |
| com.github.chuckerteam.chucker:library | 4.0.0 | debug build type only | 457076 | 16044169 | 94 | 1 | keep |
| org.jsoup:jsoup | 1.17.2 (1.22.1 in noLegal) | all 7 | 445706 | 445706 | 1 | 9 | keep |
| com.github.pedroSG94.RootEncoder:library | 2.7.2 | standard, noLegal, legacy | 332477 | 7340610 | 33 | 1 | keep |
| commons-net:commons-net | 3.10.0 | all 7 + wear | 322780 | 322780 | 1 | 22 | keep |
| io.documentnode:epub4j-core | 4.2 | all 7 | 284595 | 289478 | 2 | 6 | keep |
| com.google.code.gson:gson | 2.10.1 | wear direct, app transitive | 283367 | 283367 | 1 | 125 | keep |
| net.lingala.zip4j:zip4j | 2.11.5 | all 7 | 210027 | 210027 | 1 | 2 | keep |
| net.openid:appauth | 0.11.1 | all except foss | 156668 | 8727065 | 47 | 1 | keep |
| io.noties.markwon:core | 4.6.2 | all 7 | 133475 | 2169705 | 6 | 1 | candidate |
| com.squareup.retrofit2:retrofit | 2.9.0 | all 7 + wear | 125435 | 3110561 | 9 | 5 | candidate |
| com.github.pedroSG94.RootEncoder:common | 2.7.2 | standard, noLegal, legacy | 98256 | 5597901 | 27 | 0 | keep |
| com.github.pedroSG94:RTSP-Server | 1.4.1 | standard, noLegal, legacy | 64802 | 7405412 | 34 | 1 | keep |
| org.nanohttpd:nanohttpd | 2.3.1 | standard, noLegal, lite, photos, legacy | 51211 | 51211 | 1 | 2 | keep |
| com.github.chrisbanes:PhotoView | 2.3.0 | all 7 | 40342 | 8381163 | 46 | 9 | keep |
| com.jakewharton.timber:timber | 5.0.1 | all 7 + wear | 32329 | 1866420 | 3 | 1229 | keep |
| io.coil-kt:coil-compose | 2.5.0 | wear, all | 20609 | 27065926 | 79 | 3 | keep |
| com.github.bumptech.glide:okhttp3-integration | 4.16.0 | all 7 | 18665 | 8890044 | 49 | 0 | keep |
| com.github.chuckerteam.chucker:library-no-op | 4.0.0 | release + benchmark | 9363 | 9363 | 1 | 0 | keep |
| com.squareup.retrofit2:converter-gson | 2.9.0 | all 7 + wear | 4618 | 3398546 | 11 | 0 | candidate |
| com.squareup.leakcanary:leakcanary-android | 2.12 | debug build type only | 4240 | - | - | 2 | keep |

37 coordinates. Verdicts: 30 `keep`, 2 `replace`, 5 `candidate`.

Three rows read wrong without a note:

- `cz.adaptech:tesseract4android` publishes a POM-only aggregator, hence 284 B. The implementation
  artifact inside its closure is 12832786 B.
- `org.videolan.android:libvlc-all` ships every ABI in one AAR; the `noLegal` `abiFilters` decide
  which slices are packaged, so the shipped fraction is far under 93 MB.
- `com.github.chrisbanes:PhotoView` and `net.openid:appauth` have closures dominated by the AndroidX
  tail the app already carries for other reasons.

---

## 3. Per-library assessment

### Network transports

**com.hierynomus:smbj** - 21 files, data layer plus the watch's SMB source; the SMB network source
capability rests entirely on it. The only maintained pure-Java SMB2/3 client, and Android exposes no
SMB API at any level. Its cost is not its own 604 KB but the Bouncy Castle provider it pulls: 8.3 MB,
91% of its closure, reached by no line of our code. **keep.**

**com.github.mwiede:jsch** - 17 files; SFTP sources and the companion SFTP endpoint. 560 KB flat with
a closure of one, and the fork carries key exchanges the original JSch never gained. No platform
equivalent. **keep.**

**commons-net:commons-net** - 22 files; the FTP network source. 323 KB flat, closure of one,
Apache-maintained. No platform equivalent. **keep.**

**com.squareup.okhttp3:okhttp** - 52 files, and it is also what Glide and Chucker plug into.
`HttpURLConnection` exists at every API level, but replacing OkHttp would mean re-plumbing the image
loader and the debug inspector as well as every call site. **keep.**

**org.nanohttpd:nanohttpd** - 2 files, the broadcast HTTP server and the Cast proxy. 51 KB flat: the
lowest cost per capability in the inventory. Upstream is dormant, which for a frozen 51 KB server is
not a live risk. **keep.**

**com.squareup.retrofit2:retrofit + converter-gson** - 5 files total: one iTunes search interface on
the phone, one album-art interface on the watch. Two declarative surfaces issuing a handful of GET
calls, for 130 KB and a converter whose entire job is bridging to a Gson that is already present.
OkHttp and Gson can express both directly, in pure JVM code with no minimum API at all. **candidate.**

**org.bouncycastle:bcprov-jdk18on** - no direct import; it arrives through SMBJ, which has no
supported build without it. The largest transitive artifact in every flavor. The open question is not
whether to remove it but how much of it R8 actually keeps, which this ticket deliberately did not
measure. **candidate**, for measurement.

### Identity and cloud

**net.openid:appauth** - 1 file; the RFC 8252 OAuth path shared by the cloud providers, with Custom
Tabs already wired. 157 KB own, and its large closure is the AndroidX tail the app carries anyway.
It is also the replacement target named for MSAL below. **keep.**

**com.dropbox.core:dropbox-core-sdk** - 3 files. 8.0 MB own, in six of the seven flavors, for three
call sites. The Dropbox HTTP API v2 is plain JSON over HTTPS, and OkHttp, Gson and AppAuth are all
already in the graph; the replacement needs no API level above the `legacy` floor of 23. This is the
largest weight in the project that a rewrite could actually remove. **replace.**

**com.microsoft.identity.client:msal** - 2 files, obtaining a OneDrive token and nothing else.
567 KB own but a 19.8 MB closure, the largest in the phone graph. AppAuth against the Microsoft
identity platform v2 endpoints covers the same flow, in the same six flavors, with no new API-level
requirement. The one thing lost is broker-account support, which nothing here uses. **replace.**

### Media and rendering

**org.videolan.android:libvlc-all** - 1 file, `noLegal` only. 93 MB AAR, the largest artifact in the
project, though `abiFilters` package a slice of it. It is the only path to patented codecs and DVD/BD
ISO playback, and the flavor boundary is that feature's legal premise. **keep.**

**local:fms-ffmpeg-dts** - a bundled AAR registered as a Media3 renderer, no Kotlin import. 11.5 MB
for DTS, APE, WMA, WavPack, TTA and DSD decoding that no platform decoder provides. Built by hand
from `scripts/builders/build-ffmpeg-dts.sh`, so it does not move with a Maven bump - a maintenance
cost, not a size one. **keep.**

**com.github.pedroSG94:RTSP-Server + RootEncoder library/common** - three coordinates for one
capability, the on-device RTSP broadcast; 1 direct import. The only maintained Android RTSP server
with a camera pipeline attached, and JitPack's runtime-scoped POM is what forced the two extra
declarations. Landed in S2662 and too recent to reassess. **keep.**

**org.khronos.openxr:openxr_loader_for_android** - native loader, no import. 3.9 MB flat, required by
the headset renderer. Dead weight on a `noLegal` install that never meets a headset, but the graceful
fallback makes that harmless. **keep.**

**com.github.bumptech.glide:glide + okhttp3-integration** - 62 files, plus custom `ModelLoader` and
decoder implementations for EPUB covers and PDF pages, plus a KSP processor. Coil is in the graph on
the watch, but unifying would mean rewriting the phone's decoder set, not deleting a dependency.
**keep.**

**io.coil-kt:coil-compose** - 3 files on the watch. 21 KB own and Compose-native, which Glide 4.16.0
is not. The project carries two image loaders, one per module; unifying would mean moving the watch to
Glide, which is the worse direction. **keep.**

**com.github.chrisbanes:PhotoView** - 9 files: pinch-zoom and rotation of a still image, and the
coordinate mapping the PDF selection layer is built on. 40 KB own, and the platform has no zoomable
`ImageView` at any API level. Upstream is quiet, which for a self-contained view is tolerable. **keep.**

### Documents and text

**io.documentnode:epub4j-core** - 6 files; the entire EPUB reader family depends on it for OPF and
NCX parsing. 285 KB with a closure of two. No equivalent. **keep.**

**org.jsoup:jsoup** - 9 files: link extraction, streaming-manifest sniffing, EPUB content and TOC,
lyrics search, and two `noLegal` strategies. 446 KB flat. `android.text.Html` parses for display and
cannot select nodes, so there is nothing to move to. **keep**, with a declaration defect noted in
section 5.

**net.lingala.zip4j:zip4j** - 2 files. `java.util.zip` cannot open a password-protected or AES entry
at any API level, which is the whole reason this is here; 210 KB flat is a fair price for it. **keep.**

**io.noties.markwon:core** - 1 file, `TextViewerManager`, rendering Markdown live in the text viewer.
133 KB own and 2.2 MB of closure for one call site. No platform Markdown API exists at any level, so
a replacement means a hand-written subset renderer over `SpannableStringBuilder` - trading correctness
for size rather than moving to the platform, which is why this is a candidate and not a `replace`.
**candidate.**

### Recognition

**cz.adaptech:tesseract4android** - 1 direct import behind the OCR engine seam; every OCR capability
in the ledger rests on it. 12.8 MB of native code in four flavors. ML Kit text recognition is the
obvious alternative and was already tried and removed outright by S0386 over Cyrillic quality, so it
is not proposed again. **keep.**

**com.google.mlkit:translate** - 2 files, with `language-id`. 27.7 MB own and 38.7 MB closure: the
largest weight in the standard flavor. It buys on-device translation with downloadable models, and
the EPUB, image and camera translation features are all sold as working offline; a cloud API would
break that premise rather than trade against it. **keep.**

**com.google.mlkit:language-id** - 2 files; source-language detection for the flow above. 2.3 MB, and
nothing of comparable accuracy exists offline. **keep.**

**com.google.zxing:core** - 5 files: the companion-config QR scan and the broadcast descriptor QR.
608 KB of pure JVM code with no GMS, which is what lets it work in `foss` where an ML Kit barcode
scanner could not. **keep.**

### Sideload-only extraction

**com.github.TeamNewPipe:NewPipeExtractor** - 4 files, `noLegal` only. 788 KB. Copyleft without a
linking exception, which is exactly why it is confined to the sideload flavor. **keep.**

### Infrastructure and tooling

**com.jakewharton.timber:timber** - 1229 files. A rule-level requirement at 32 KB. **keep.**

**com.google.code.gson:gson** - 125 files across both modules: settings export, wire descriptors,
cached payloads. `org.json` covers none of the reflective object mapping in use. **keep**, with a
declaration defect noted in section 5.

**com.android.tools:desugar_jdk_libs** - no import; a build-time capability. It is what puts
`java.time` on API 23-25, which is the `legacy` flavor's whole premise. Only the reachable subset is
rewritten into the APK. **keep.**

**com.github.chuckerteam.chucker (library / library-no-op)** - 1 file, debug only; release and
benchmark take the 9 KB no-op. In-app HTTP inspection at zero release cost. **keep.**

**com.squareup.leakcanary:leakcanary-android** - 2 files, debug only. Leak detection at zero release
cost. **keep.**

**com.google.accompanist:accompanist-permissions** - 7 files on the watch. 1.0 MB own for a runtime
permission pattern that `rememberLauncherForActivityResult` plus a small state holder can express
using only AndroidX already in the watch graph, with no API level above the watch floor of 28.
Accompanist retires modules as their APIs land in AndroidX, and this one has no successor yet, so the
replacement is ours to write whenever it happens. **candidate.**

---

## 4. Suggestions, best value first

Each item is written to be read on its own. Six items, one per `replace` or `candidate` verdict.

**1. Replace the Dropbox SDK with direct REST calls.**
Removes `com.dropbox.core:dropbox-core-sdk` 5.4.5 - 8.0 MB of resolved bytes from six of the seven
flavors - and touches three files. Dropbox HTTP API v2 is plain JSON over HTTPS; OkHttp, Gson and
AppAuth are already in every one of those flavors, so nothing new is added and no API level above 23
is needed. What breaks: token refresh, chunked upload sessions and error-code mapping stop being
handed to us and become code to write and test against a live account. What is in the way: nothing
technical - only that the rewrite must be verified against a real Dropbox account per flavor, which
needs credentials this repository does not hold.

**2. Replace MSAL with AppAuth for the OneDrive sign-in.**
Removes `com.microsoft.identity.client:msal` 6.0.1 - 567 KB own but the largest closure in the phone
graph at 19.8 MB - and touches two files that do nothing but obtain a token. AppAuth is already
declared in the same six flavors, so this deletes a dependency without adding one, at no new API
level. What breaks: broker-based accounts (Microsoft Authenticator / Company Portal single sign-on)
stop working; nothing in this project uses them, but a corporate OneDrive account might expect them.
What is in the way: verification needs a real Microsoft account, and the redirect URI has to be
re-registered for the AppAuth flow.

**3. Measure how much of Bouncy Castle actually ships.**
`org.bouncycastle:bcprov-jdk18on` 1.75 is 8.3 MB, the largest transitive artifact in every flavor and
the wear module, and no line of our code imports it - it arrives through SMBJ, which has no supported
build without it. Before anyone proposes removing or shrinking it, someone has to know what R8 keeps:
that means a release build with and without the SMB feature reachable, diffed by APK analyzer. What
breaks: nothing - this is a measurement, not a change. What is in the way: it needs a release build
per variant, which is precisely the work this assessment scoped out.

**4. Drop Accompanist Permissions from the watch.**
Removes `com.google.accompanist:accompanist-permissions` 0.34.0 - 1.0 MB own - and touches seven
watch files. The replacement is `rememberLauncherForActivityResult` plus a small state holder, using
AndroidX already in the watch graph, at no API level above the module's floor of 28. What breaks: the
rationale-showing behaviour and multi-permission grouping have to be re-implemented, and permission
UI on a watch is awkward to test by hand. What is in the way: nothing - this one is self-contained
and needs no external account.

**5. Drop Retrofit and its Gson converter.**
Removes `com.squareup.retrofit2:retrofit` and `converter-gson` 2.9.0 - 130 KB - and touches five
files: one iTunes search interface on the phone and one album-art interface on the watch. Both are a
handful of GET calls that OkHttp plus Gson express directly, in pure JVM code with no minimum API.
What breaks: the declarative interfaces become hand-written request builders and response parsers, so
the two call sites get longer while the graph gets smaller. What is in the way: nothing, but the
benefit is small enough that it is only worth doing while those two files are already open.

**6. Replace Markwon with a subset Markdown renderer.**
Removes `io.noties.markwon:core` 4.6.2 - 133 KB own, 2.2 MB closure - and touches one file,
`TextViewerManager`. Unlike every other item here there is no platform API to move to: Android has no
Markdown renderer at any level, so this means writing a `SpannableStringBuilder` renderer for the
subset the text viewer actually shows. What breaks: correctness. Markwon implements CommonMark; a
subset renderer will get tables, nested lists, reference links and inline HTML wrong, and a `.md`
file that renders badly is a visible defect where 133 KB is not. What is in the way: nothing
technical - only that the trade is unfavourable unless the size becomes load-bearing for a specific
flavor budget.

---

## 5. Declaration defects found while measuring

Neither is a verdict about a library; both are places where the build files claim something the
resolved graph does not.

**`app_v2` uses Gson in 83 files and declares it nowhere.** It arrives transitively through
`com.squareup.retrofit2:converter-gson`. This is the same defect S2509 fixed for the wear module,
where the reasoning was that a cross-module wire contract may not rest on a version a Retrofit bump
could change or drop. Suggestion 5 above would remove that transitive path entirely, so the two are
linked: dropping Retrofit without first declaring Gson outright would break 83 files.

**`noLegal` resolves jsoup 1.22.1 while the build file declares 1.17.2.** NewPipeExtractor requests
the newer version and wins the conflict, so one flavor parses HTML with a different library version
than the declaration states, and the four `noLegal`-only extraction strategies are the code most
affected by it.
