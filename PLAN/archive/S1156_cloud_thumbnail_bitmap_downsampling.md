**Status:** Archived

# S1156 - Cloud thumbnail bitmap downsampling / memory

## 0. Raw capture (verbatim)

Source: Google Play Console -> App quality -> recommended actions, release `2.60.7221.704` (captured 2026-07-24).

> **Improve your app's performance with bitmap image optimization**
> Your app is manually downloading and decoding images from the network in the following places:
> - Decoded in ev.y
> - Downloaded in com.dropbox.core.http.StandardHttpRequestor.toResponse
> - Downloaded in k36.b
> - Downloaded in u73.d
>
> This can lead to excessive memory usage, slow performance, and app crashes. To reduce memory usage, we recommend using an image-loading library to automatically handle downsampling, caching, and memory management for you.
> Technical quality. Release name: 2.60.7221.704

## Symptom

Play detects manual network image download + decode. `Decoded in ev.y` (obfuscated) points at a bitmap decode path that may not downsample to the target view size. The `com.dropbox.core.http.StandardHttpRequestor` download is the Dropbox SDK fetching bytes (expected); the concern is the decode side.

## Suspected area

- Cloud/network thumbnail loaders: `data/network/glide/**`, `CloudThumbnailData`, `GoogleDriveThumbnailData`, `NetworkFileData`.
- Question: do cloud previews route through Glide with `override(w,h)` / downsampling, or is a full-size bitmap decoded into memory before display?

## Research findings (2026-07-24)

**The suspected area is clean. Both network image paths already downsample to the display size.**

- Cloud thumbnails (`CloudThumbnailModelLoader`, `GoogleDriveThumbnailModelLoader`) are Glide `ModelLoader<_, InputStream>` and their fetchers declare `getDataClass() = InputStream`. The app never decodes those bytes itself - Glide owns the decode and downsamples to the target size. There is no full-size bitmap in memory on this path.
- The `com.dropbox.core.http.StandardHttpRequestor.toResponse` download Play names is the Dropbox SDK fetching the bytes that feed exactly that `InputStream` fetcher. It is the expected half of a correct Glide integration, not a manual decode.
- The one path that does decode by hand is the network PDF thumbnail (`NetworkPdfThumbnailLoader`, a `ModelLoader<_, Bitmap>`). That is unavoidable - a PDF page is rendered by `PdfRenderer`, not decoded by Glide - but it is not undersampled: the fetcher receives Glide's requested `width`/`height`, derives `targetWidth`/`targetHeight` from the page aspect ratio, and even logs `target=WxH` in its profile line.

So no evidence of the reported failure mode (full-size network bitmap held in memory) exists in the code. Play's advisory appears to be pattern-matched on the bytecode shape "download bytes, then decode", which the PDF path legitimately has.

**Deobfuscation was blocked on the wrong mapping, and the reason matters.** The local `standardRelease` mapping (2026-07-20) resolved the three reported symbols to `AppLaunchPanelActivity$panelLifecycleCallbacks$1`, `ICustomTabsService$Stub` and `DefaultLivePlaybackSpeedControl$Builder` - none of which download or decode anything. That was not a finding, it was proof the mapping belonged to a different build: R8 names are assigned per build and do not survive across them.

## Deobfuscation (2026-08-15)

**Mapping source.** Play Console does not hand the uploaded `mapping.txt` back - the `ReTrace mapping file` row in App bundle explorer offers deletion only. The mapping was recovered instead from the shipped bundle itself: App bundle explorer -> artifact `260722170` (`2.60.7221.704`) -> Downloads -> Assets -> `Original file`, then `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` extracted from the AAB. Size 168 809 876 bytes, matching the 169 MB the console reports for that release's ReTrace file. Header confirms the build: `compiler_version: 9.2.14`, `min_api: 26`, `pg_map_id: f94d6847e45c15d199d2d4bc66bc934b1d8303182b7dac310ff47bbe7a75fa3b`.

**Resolved symbols.** All three are library code. No app class appears in the advisory.

- `ev.y` (the "Decoded in" site) - `com.bumptech.glide.load.resource.bitmap.ImageReader.decodeBitmap(BitmapFactory$Options)`, all three reader variants (`ByteBufferReader`, `InputStreamImageReader`, `ParcelFileDescriptorImageReader`), plus the `InputStreamRewinder.rewindAndGet` and `ByteBufferUtil.toStream` frames inlined into it. The residual class `ev` is a horizontal R8 merge - it carries the `$r8$classId` synthetic field and its first constituent is `androidx.startup.AppInitializer` - so the class name alone is misleading; every original method behind member `y` is a Glide decode frame.
- `k36.b` - `com.bumptech.glide.load.data.HttpUrlFetcher.loadDataWithRedirects`, with `buildAndConfigureConnection`, `getStreamForSuccessfulRequest` and `ContentLengthInputStream.obtain` inlined in. Glide's own HTTP fetcher.
- `u73.d` - `androidx.media3.datasource.DefaultHttpDataSource.open`, with `HttpUtil.getContentLength` inlined in. Media3's HTTP data source, which serves media streaming and never decodes an image at all.

**Verdict: false positive, confirmed.** Play's advisory recommends adopting an image-loading library that handles downsampling and caching. The decode site it names *is* that library - Glide's own `ImageReader.decodeBitmap`. The two download sites are Glide's own fetcher and Media3's data source, and the fourth named site, `com.dropbox.core.http.StandardHttpRequestor.toResponse`, is the Dropbox SDK feeding bytes into that same Glide fetcher. The detector pattern-matched the bytecode shape "download bytes, then decode" inside the libraries and attributed it to the app. This is exactly what the 2026-07-24 code research predicted; the mapping turns that prediction into evidence.

## Remaining work

- None. No code change: there is nothing in app code to fix.
- The metric refreshes on its own with any subsequent release; `2.60.8122.034` has already shipped since.

## Notes

- Not a blocker; parked from a Play Console recommendation triage.
- Companion R8 item parked separately.
