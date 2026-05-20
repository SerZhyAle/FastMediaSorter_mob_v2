"""
ytdlp_utils.py - noLegal yt-dlp helper functions.
Called from YtDlpExtractionStrategy.kt via Chaquopy.
"""

# Hosts where an extractor's suitable() returns True but actual extraction fails
# in this bundled yt-dlp version (ThreadsIE was updated for threads.net only;
# threads.com support was added later). Probe returns None for these so the
# extraction chain falls through to html/dynamic/site strategies.
_PROBE_EXCLUDED_HOSTS = frozenset({"www.threads.com", "threads.com"})


def _is_probe_excluded(url):
    """
    Returns True when yt-dlp should not be used for the given URL.
    Covers both host-level exclusions and URL-pattern exclusions.

    S0223: Instagram /p/ posts are image-only. yt-dlp 2026.3.17 InstagramIE
    raises "There is no video in this post" for them, wasting ~10s probe time.
    /reel/ URLs must remain un-excluded so yt-dlp continues to handle videos.
    """
    try:
        from urllib.parse import urlparse
        parsed = urlparse(url)
        host = parsed.hostname or ""
        if host in _PROBE_EXCLUDED_HOSTS:
            return True
        if host in ("www.instagram.com", "instagram.com"):
            path = parsed.path or ""
            if path.startswith("/p/"):
                return True
    except Exception:
        pass
    return False


def probe_url(url):
    """
    Check whether yt-dlp has a non-generic extractor for the given URL.
    Uses only URL pattern matching - no network calls, no downloads.

    Returns True if a specific extractor matches, None otherwise.
    Caller checks: result != null  →  Applicable.

    GenericIE is excluded: it matches almost any URL and would make every
    URL a candidate for yt-dlp regardless of actual support.
    """
    if _is_probe_excluded(url):
        return None
    try:
        import yt_dlp
        ydl = yt_dlp.YoutubeDL({'quiet': True, 'no_warnings': True})
        ies = ydl._ies
        # In recent yt-dlp, _ies is an OrderedDict (name → instance).
        # Guard against older list-based layouts.
        try:
            ies_iter = ies.values()
        except AttributeError:
            ies_iter = ies
        for ie in ies_iter:
            if ie.__class__.__name__ == 'GenericIE':
                continue
            try:
                if ie.suitable(url):
                    return True
            except Exception:
                pass
    except Exception:
        pass
    return None


def download_to_file(url, cookie_file, out_dir, file_stem, user_agent=None, audio_only=False, progress_callback=None):
    """
    Download media via yt-dlp to out_dir/<file_stem>.<ext>.
    Returns a dict {'path': str, 'title': str, 'ext': str} on success.
    Raises on failure - caller catches via Chaquopy runCatching.

    user_agent (S0182): UA pinned to the session at login time. Replays the same UA
    the server first saw with these cookies, avoiding fingerprint flags.

    audio_only (S0190): when True, format selector picks bestaudio instead of
    best-progressive-video (used for YTMusic URLs after canonicalization).

    progress_callback (S0190 Phase 03): optional two-arg callable (downloaded: int, total: int).
    Called from yt-dlp's download thread via progress_hooks. total=-1 when unknown.

    Format cascade is single-stream only (no FFmpeg available on device for merge):
    1. Best MP4 progressive at any resolution (TikTok/IG can serve >=1080p like this)
    2. Best single-stream of any container at any resolution
    3. Best MP4 progressive capped at 720p (YouTube has format 22)
    4. Best single-stream capped at 720p
    5. Best MP4 capped at 360p (YouTube has format 18)
    6. Best of anything (last resort)

    For YouTube VOD without ffmpeg this lands on 720p (format 22) or 360p (format 18).
    For TikTok/IG this lands on whatever progressive MP4 is on offer (usually 1080p).
    yt-dlp's native HLS/DASH downloader is used when a manifest format wins selection.
    """
    import yt_dlp
    from yt_dlp.utils import DownloadError
    import os
    out_template = os.path.join(out_dir, file_stem + '.%(ext)s')

    def _on_progress(d):
        if progress_callback is None:
            return
        if d.get('status') != 'downloading':
            return
        downloaded = d.get('downloaded_bytes')
        total = d.get('total_bytes') or d.get('total_bytes_estimate')
        if downloaded is None:
            return
        try:
            # Java side accepts long; total may be None → pass -1 sentinel.
            progress_callback(int(downloaded), int(total) if total else -1)
        except Exception:
            # Never let a callback error abort the download.
            pass

    opts = {
        'quiet': True,
        'no_warnings': True,
        'format': (
            'bestaudio[ext=m4a]/bestaudio[ext=opus]/bestaudio[ext=mp3]/140/251/250/249/bestaudio'
            if audio_only else
            (
                'best[ext=mp4]/best/'
                'best[ext=mp4][height<=720]/best[height<=720]/'
                'best[ext=mp4][height<=360]/best[height<=360]/'
                'best'
            )
        ),
        'outtmpl': out_template,
        'socket_timeout': 60,
        # S0190: explicit pin - protects against upstream default changes.
        'http_chunk_size': 10485760,
        'retries': 3,
        'fragment_retries': 5,
        # S0190 Phase D: single-stream pacing - parallel chunks can re-trigger CDN throttling.
        'concurrent_fragment_downloads': 1,
        # Don't try to merge separate video+audio streams (we have no ffmpeg).
        # Forces selector to pick single-stream formats only.
        'allow_unplayable_formats': False,
        'youtube_include_dash_manifest': True,
        'youtube_include_hls_manifest': True,
        # S0190 Phase 03: bridge progress_callback (Java interface) into yt-dlp hooks.
        'progress_hooks': [_on_progress],
        # S0190: force preference for android then web YouTube clients.
        # Android client typically does NOT require PoToken and serves
        # progressive single-stream formats compatible with our no-ffmpeg setup.
        # web is a fallback for non-YouTube hosts whose extractor ignores this key.
        'extractor_args': {'youtube': {'player_client': ['android', 'web']}},
    }
    if cookie_file:
        opts['cookiefile'] = cookie_file
    if user_agent:
        opts['user_agent'] = user_agent
    with yt_dlp.YoutubeDL(opts) as ydl:
        try:
            info = ydl.extract_info(url, download=True)
        except DownloadError as e:
            if audio_only and 'Requested format is not available' in str(e):
                raise DownloadError(f'S0260: ytmusic_no_audio_format_available original={e}') from e
            raise
        if info is None:
            raise ValueError('yt-dlp returned no info for url=' + str(url))
        ext = info.get('ext', 'mp4')
        title = info.get('title', 'download')
        # S0260: yt-dlp selected-format diagnostic. Chaquopy bridges stdout to
        # Android logcat (tag python.stdout) so this line appears alongside the
        # Kotlin Timber traces during a YTMusic share device test. The four
        # fields distinguish H1/H2/H3 hypotheses: audio formats land on
        # vcodec=none + acodec=mp4a (or similar); video-only formats land on
        # vcodec=avc1/vp09 + acodec=none; combined progressive shows both.
        print(
            "S0260: python download done format_id={fid} ext={ext} vcodec={vc} acodec={ac} audio_only_hint={ao}".format(
                fid=info.get('format_id'),
                ext=ext,
                vc=info.get('vcodec'),
                ac=info.get('acodec'),
                ao=audio_only,
            )
        )
        out_path = os.path.join(out_dir, file_stem + '.' + ext)
        if not os.path.exists(out_path):
            # yt-dlp may have picked a different extension - scan the dir for the file.
            try:
                for f in os.listdir(out_dir):
                    if f.startswith(file_stem + '.'):
                        out_path = os.path.join(out_dir, f)
                        # Update ext from actual filename
                        actual_ext = f.rsplit('.', 1)[-1] if '.' in f else ext
                        ext = actual_ext
                        break
                else:
                    raise FileNotFoundError(
                        'Expected download not found in ' + out_dir + ' stem=' + file_stem
                    )
            except OSError as e:
                raise FileNotFoundError('Cannot scan output dir: ' + str(e))
        return {'path': out_path, 'title': title, 'ext': ext}
