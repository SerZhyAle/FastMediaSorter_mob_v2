"""
ytdlp_utils.py — noLegal yt-dlp helper functions.
Called from YtDlpExtractionStrategy.kt via Chaquopy.
"""

# Hosts where an extractor's suitable() returns True but actual extraction fails
# in this bundled yt-dlp version (ThreadsIE was updated for threads.net only;
# threads.com support was added later). Probe returns None for these so the
# extraction chain falls through to html/dynamic/site strategies.
_PROBE_EXCLUDED_HOSTS = frozenset({"www.threads.com", "threads.com"})


def probe_url(url):
    """
    Check whether yt-dlp has a non-generic extractor for the given URL.
    Uses only URL pattern matching — no network calls, no downloads.

    Returns True if a specific extractor matches, None otherwise.
    Caller checks: result != null  →  Applicable.

    GenericIE is excluded: it matches almost any URL and would make every
    URL a candidate for yt-dlp regardless of actual support.
    """
    try:
        from urllib.parse import urlparse
        host = urlparse(url).hostname or ""
        if host in _PROBE_EXCLUDED_HOSTS:
            return None
    except Exception:
        pass
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


def download_to_file(url, cookie_file, out_dir, file_stem, user_agent=None):
    """
    Download media via yt-dlp to out_dir/<file_stem>.<ext>.
    Returns a dict {'path': str, 'title': str, 'ext': str} on success.
    Raises on failure — caller catches via Chaquopy runCatching.

    user_agent (S0182): UA pinned to the session at login time. Replays the same UA
    the server first saw with these cookies, avoiding fingerprint flags.

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
    import os
    out_template = os.path.join(out_dir, file_stem + '.%(ext)s')
    opts = {
        'quiet': True,
        'no_warnings': True,
        'format': (
            'best[ext=mp4]/best/'
            'best[ext=mp4][height<=720]/best[height<=720]/'
            'best[ext=mp4][height<=360]/best[height<=360]/'
            'best'
        ),
        'outtmpl': out_template,
        'socket_timeout': 60,
        # Concurrent fragment download for HLS/DASH — speeds up YouTube/Vimeo
        # without requiring ffmpeg (native fragment muxer handles single-stream HLS).
        'concurrent_fragment_downloads': 4,
        # Don't try to merge separate video+audio streams (we have no ffmpeg).
        # Forces selector to pick single-stream formats only.
        'allow_unplayable_formats': False,
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
        info = ydl.extract_info(url, download=True)
        if info is None:
            raise ValueError('yt-dlp returned no info for url=' + str(url))
        ext = info.get('ext', 'mp4')
        title = info.get('title', 'download')
        out_path = os.path.join(out_dir, file_stem + '.' + ext)
        if not os.path.exists(out_path):
            # yt-dlp may have picked a different extension — scan the dir for the file.
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
