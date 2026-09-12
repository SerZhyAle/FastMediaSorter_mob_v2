#requires -Version 7.0
<#
.SYNOPSIS
    The monitor page (S2406): a static HTML shell that repaints itself in place from a data script,
    and the data script itself.

.DESCRIPTION
    Dot-source this file; it defines two functions and writes nothing.

      Get-DevMonitorShellHtml -IntervalSeconds <n>
          One HTML document: inline CSS (one monospace face, plain tables, no animation, no
          transition), an inline renderer, and nothing external. Every <n> seconds it appends
          `<script src="snapshot.js?t=<now>">` to <head> and repaints from the object that script
          hands to window.__devMonitor - a classic <script src> from the same directory is the one
          subresource a file:// page may load in Chrome, Edge and Firefox (fetch and XHR are
          refused by CORS), which is what lets the page update without a reload, without a server
          and without a flicker at a 3 s cadence. Two consecutive load failures fall back to
          location.reload(). Once a second the header recomputes the snapshot age from the page's
          own clock and says `fresh`, `writer silent` (three intervals without a new snapshot) or
          `writer stopped` (the writer's last snapshot said so) - in words beside the colour.

      ConvertTo-DevMonitorDataScript -Snapshot <object>
          The text of snapshot.js: `window.__devMonitor(<json>);` with the snapshot serialised by
          ConvertTo-Json -Depth 8 -Compress.

    Labels are English (owner ruling 2026-09-02: English, minimalistic, no animations, a lot of
    readable data, every 3 seconds). Colour is limited to four classes - norm, ok, warn, bad - and
    every coloured cell also carries the word, so the page reads without colour. Colour marks the
    DEVIATION: a free lock, a live lease and a live agent are the norm and are drawn grey, so five
    bright green `free` cells no longer pull the eye away from the one row that is held or queued.

    Every collection is read through arr(), and render() runs inside a try/catch that reports the
    failure in the problems panel: ConvertTo-Json writes a one-element collection as a bare object,
    and `.forEach` on that object stopped the paint at the offending section while the header went
    on ticking a fresh age - eight tables blank with nothing on the page to say why (2026-09-10).

    A repaint is held, never dropped, while the reader has a selection, a button down, or has
    pressed `p` - innerHTML is replaced wholesale, so an unheld 3 s cadence made copying a path or
    an id impossible. The header says which of the two is holding, and the age keeps ticking.

    Look (owner ruling 2026-09-03): a terminal - black ground, a terminal face, and the vertical box
    of every row cut to what still separates it from the next, because the page is judged by how
    many live rows fit on one screen. The `running` table carries the agent's NICKNAME beside the
    pid; the join is by ticket, never by pid, for the reason recorded at the code.

    Exit codes: none - library.
#>

function ConvertTo-DevMonitorDataScript {
    param([Parameter(Mandatory)]$Snapshot)
    $json = $Snapshot | ConvertTo-Json -Depth 8 -Compress
    # `</script>` inside a note would end the inline block; the data script is external, but the
    # sequence is escaped anyway so the same text is safe wherever it lands.
    $json = $json -replace '</', '<\/'
    return "window.__devMonitor($json);`n"
}

function Get-DevMonitorShellHtml {
    param(
        [Parameter(Mandatory)][int]$IntervalSeconds,
        # The writer stamps the shell and every data script with the same value; a data script
        # carrying a different stamp tells an open tab that a newer shell exists, and it reloads once.
        [string]$Stamp = ''
    )
    $interval = [math]::Max(1, $IntervalSeconds)
    # Dark terminal palette and a compact row box (owner ruling 2026-09-03): black ground, one
    # terminal face, and every vertical measure cut to the minimum that still separates rows - the
    # page is read as a wall of live rows, so the count visible without scrolling is the metric.
    $css = @'
html{color-scheme:dark}
body{margin:0;padding:4px 8px 18px;background:#000;color:#c9d1d9;font:12px/1.2 "Cascadia Mono","JetBrains Mono","Fira Code",Consolas,"DejaVu Sans Mono",Menlo,monospace}
h1{display:none}
h2{font-size:12px;font-weight:700;margin:9px 0 1px;padding-bottom:0;border-bottom:1px solid #30363d;color:#79c0ff;text-transform:uppercase;letter-spacing:.06em;position:sticky;top:var(--headh,26px);background:#000;z-index:2}
h2 small{font-weight:400;font-size:11px;color:#6e7681;text-transform:none;letter-spacing:0}
.ok,.warn,.bad{font-weight:700}
.norm{color:#6e7681;font-weight:600}
.id{font-weight:700;color:#e6edf3}
.name{font-weight:700;color:#d2a8ff}
.big{font-weight:700;color:#e6edf3}
#head{font-size:12px}
#head .state{font-size:13px;font-weight:800;padding:0 5px}
#head.ok .state{color:#8b949e}
#head.warn .state{color:#e3b341;background:#3a2d00}
#head.bad .state{color:#fff;background:#8e1519}
tr.stop td{font-weight:700}
.st-ok{color:#3fb950;font-weight:700}
.st-warn{color:#e3b341;font-weight:700}
.st-dim{color:#6e7681}
table{border-collapse:collapse;width:100%;table-layout:auto}
th,td{text-align:left;vertical-align:top;padding:0 8px 0 0;border-bottom:1px solid #15191e;white-space:nowrap}
td.wrap{white-space:pre-wrap;word-break:break-word;min-width:18em}
td.wrapn{white-space:pre-wrap;word-break:break-word;min-width:9em}
th{color:#8b949e;font-weight:600}
tr.group td{color:#8b949e;padding-top:3px;border-bottom:none}
tr.none td{color:#6e7681}
tr.hasnote td{border-bottom:none}
tr.note td{padding:0 8px 2px 14px;white-space:pre-wrap;word-break:break-word}
tr.alarm td{background:#2d0f10}
tr.alarm:hover td{background:#3d1416}
tr:hover td{background:#0d1117}
#problems .line{padding:1px 4px;margin-bottom:1px}
#problems .line.bad{background:#2d0f10}
#problems .line.warn{background:#3a2d00}
#problems .clear{padding:1px 4px}
.dim{color:#6e7681}
.num{text-align:right;white-space:nowrap}
.ok{color:#3fb950}
.warn{color:#e3b341}
.bad{color:#f85149}
.bad.head{background:#2d0f10}
.warn.head{background:#3a2d00}
#head{display:flex;flex-wrap:wrap;gap:1px 14px;padding:3px 6px;border:1px solid #30363d;background:#0d1117;position:sticky;top:0;z-index:3}
#head.bad{border-color:#f85149}
#head.warn{border-color:#e3b341}
#head span b{font-weight:600;color:#e6edf3}
'@
    $js = @'
(function () {
  var INTERVAL = __INTERVAL__;
  var SHELL_STAMP = '__STAMP__';
  var snap = null;
  var errors = 0;
  var lastTaken = null;
  // How long an agent may be quiet before the page stops calling it live. Read in two places -
  // the roster's activity cut and the problems panel's abandoned-ticket rule - and shared so the
  // panel can never print ALL CLEAR above a red NO LIFE row. Reasoning at the roster's own comment.
  var ROSTER_ACTIVE_MINUTES = 10;

  function esc(v) {
    if (v === null || v === undefined) { return ''; }
    return String(v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function el(id) { return document.getElementById(id); }
  // Every collection on the snapshot is read through this. ConvertTo-Json emits a ONE-element
  // collection as a bare object, so `"devices":{..}` reached the page instead of `"devices":[{..}]`
  // and `.forEach` threw on it - which stopped render() dead at that section and left the eight
  // tables below it blank (measured 2026-09-10, one serial in the device park). The writer's own
  // unwrap is fixed at the source, but the collector is a canon forwarder this page cannot gate,
  // and the same shape can arrive from any of its fifteen arrays, so the renderer accepts both.
  // Array.isArray is deliberately not used: the same code is exercised under cscript //E:JScript,
  // which is ES3.
  function arr(v) {
    if (v === null || v === undefined) { return []; }
    if (Object.prototype.toString.call(v) === '[object Array]') { return v; }
    return [v];
  }
  function num(v, d) { if (v === null || v === undefined || v === '') { return '?'; } var n = Number(v); if (isNaN(n)) { return esc(v); } return n.toFixed(d === undefined ? 0 : d); }
  function mins(v) {
    if (v === null || v === undefined) { return '?'; }
    var m = Number(v); if (isNaN(m)) { return '?'; }
    if (m < 1) { return Math.round(m * 60) + 's'; }
    if (m < 90) { return Math.round(m) + 'm'; }
    return (m / 60).toFixed(1) + 'h';
  }
  // Every stamp is UTC and is shown as this machine's local clock. Both shapes the snapshot
  // carries are parsed by hand instead of being handed to the engine's date parser, because each
  // one breaks it in its own way:
  //   `2026-09-10T13:21:02.6633133Z` - a .NET round-trip string. The ECMAScript date format allows
  //      three fractional digits; with seven, the string falls through to whatever legacy parser
  //      the engine keeps, and the header's `since` and the locks table's `since` printed the raw
  //      27-character stamp where a clock belongs.
  //   `09/10/2026 13:58:35` - what [string] makes of a DateTime that ConvertFrom-Json built from a
  //      UTC field (culture en-US, Kind=Utc, and no marker survives the cast). new Date() reads
  //      that as LOCAL time, so gate health would have been drawn two hours behind every other
  //      section on this machine, silently and by exactly the local offset.
  // Epoch milliseconds are accepted too, so a caller holding a number need not round-trip through
  // toISOString(). Anything else falls back to the engine, and an unparsable value is printed as
  // it came rather than as `NaN`.
  function local(when) {
    if (when === null || when === undefined || when === '') { return ''; }
    var d = null;
    if (typeof when === 'number') { d = new Date(when); }
    else {
      var t = String(when);
      var m = t.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?/);
      if (m && !/[+\-]\d{2}:?\d{2}$/.test(t)) {
        d = new Date(Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6], m[7] ? +String(m[7]).substr(0, 3) : 0));
      }
      else {
        m = t.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})[ ,]+(\d{1,2}):(\d{2}):(\d{2})$/);
        if (m) { d = new Date(Date.UTC(+m[3], +m[1] - 1, +m[2], +m[4], +m[5], +m[6])); }
        else { d = new Date(t); }
      }
    }
    if (!d || isNaN(d.getTime())) { return esc(when); }
    function two(x) { return (x < 10 ? '0' : '') + x; }
    return two(d.getHours()) + ':' + two(d.getMinutes()) + ':' + two(d.getSeconds());
  }
  function cls(c, word) { return '<span class="' + c + '">' + esc(word) + '</span>'; }
  function id(v) { return v ? '<span class="id">' + esc(v) + '</span>' : ''; }
  function name(v) { return v ? '<span class="name">' + esc(v) + '</span>' : ''; }
  function bold(v) { return v ? '<b>' + esc(v) + '</b>' : ''; }
  function statusCls(st) {
    if (!st) { return ''; }
    // S2869: the canon snapshot collector truncates multi-word statuses at the first space (regex
    // \S+), so "In Progress" arrives as "In". Normalize it back until the canon regex is fixed.
    // "In" is not a status in the 13-status vocabulary, so the mapping is unambiguous.
    if (st === 'In') { st = 'In Progress'; }
    if (/^Block/.test(st)) { return cls('warn', st + ' (skipped)'); }
    if (st === 'In Progress' || st === 'Implemented' || st === 'Verified') { return '<span class="st-ok">' + esc(st) + '</span>'; }
    if (st === 'Draft') { return '<span class="st-dim">' + esc(st) + '</span>'; }
    return bold(st);
  }
  function table(id, head, rows, emptyText) {
    var h = '<table><thead><tr>';
    for (var i = 0; i < head.length; i++) { h += '<th' + (head[i].charAt(0) === '#' ? ' class="num"' : '') + '>' + esc(head[i].replace(/^#/, '')) + '</th>'; }
    h += '</tr></thead><tbody>';
    if (!rows || rows.length === 0) { h += '<tr class="none"><td colspan="' + head.length + '">' + esc(emptyText || 'none') + '</td></tr>'; }
    else { for (var r = 0; r < rows.length; r++) { h += rows[r]; } }
    h += '</tbody></table>';
    el(id).innerHTML = h;
  }
  function tr(cells, klass) {
    var h = '<tr' + (klass ? ' class="' + klass + '"' : '') + '>';
    for (var i = 0; i < cells.length; i++) {
      var c = cells[i]; var k = ''; var v = c; var span = 0;
      if (c && typeof c === 'object') {
        // A group caption spans the whole table. Without the span its text sits in column one and,
        // under table-layout:auto plus td.wrap's 18em floor, DICTATES that column's width - which is
        // what pushed the `ticket` column of `next up` a thousand pixels to the right.
        if (c.span) { span = c.span; }
        if (c.n !== undefined) { k = 'num'; v = c.n; }
        // `wrap` carries an 18em floor, and min-width on a cell applies to its whole COLUMN, so an
        // empty wrap cell still reserved 18em of the table for a column that had nothing to show -
        // measured on the live roster 2026-09-10: `holds` and `waiting for` were `-` on 17 of 18
        // rows and between them held some 650 px hostage, which is why every text column was
        // squeezed into a strip. An empty value drops the class, and `s` is the floor for a column
        // whose content is short and structured (a domain and a duration) rather than prose.
        else if (c.w !== undefined) { v = c.w; k = String(v).length ? 'wrap' : ''; }
        else if (c.s !== undefined) { v = c.s; k = String(v).length ? 'wrapn' : ''; }
      }
      h += '<td' + (k ? ' class="' + k + '"' : '') + (span ? ' colspan="' + span + '"' : '') + '>' + v + '</td>';
    }
    return h + '</tr>';
  }

  // A repaint replaces every table's innerHTML, which destroys any selection the reader is holding -
  // at a 3 s cadence that made copying a path, a ticket id or a session uuid a race nobody wins. So
  // the repaint is HELD (never dropped: the newest snapshot is kept and painted the moment the hold
  // ends) while text is selected, while a button is down, and while the reader has pressed `p`.
  var paused = false;
  var mouseDown = false;
  var holding = false;
  function selectionActive() {
    try { var sel = window.getSelection(); return !!(sel && String(sel).length); } catch (e) { return false; }
  }
  function shouldHold() { return paused || mouseDown || selectionActive(); }
  function maybeRender() {
    if (!snap || snap.takenAtUtc === lastTaken) { return; }
    if (shouldHold()) { holding = true; return; }
    holding = false;
    lastTaken = snap.takenAtUtc;
    // A renderer exception used to be INVISIBLE, which is how a one-element `devices` object went
    // unnoticed: the tables painted before the throw kept their last content, every table after it
    // stayed empty, the header went on ticking a fresh age, and nothing on the page said that half
    // of it was missing. The banner takes the place of the problems panel, which is the one section
    // read first, and names the message so the next failure is read rather than guessed.
    try { render(snap); }
    catch (e) {
      var msg = (e && e.message) ? e.message : String(e);
      var box = el('problems');
      if (box) {
        box.innerHTML = '<div class="line bad">' + cls('bad', 'PAGE ERROR') +
          ' <b>the renderer threw</b> ' + esc(msg) +
          ' <span class="dim">- sections below this one may be stale or empty; the snapshot itself is fine</span></div>';
      }
      var note = el('problems-note');
      if (note) { note.textContent = 'renderer error - this page is not showing the whole snapshot'; }
    }
  }
  document.addEventListener('mousedown', function () { mouseDown = true; });
  document.addEventListener('mouseup', function () { mouseDown = false; });
  document.addEventListener('keydown', function (e) {
    if (e.ctrlKey || e.metaKey || e.altKey) { return; }
    if (e.key === 'p' || e.key === 'P') { paused = !paused; maybeRender(); tickHeader(); }
  });

  function renderHead(s, state, stateClass, ageSec) {
    var w = s.writer || {};
    var head = el('head');
    var hold = paused ? '<span class="warn"> paused (p to resume)</span>'
      : (holding ? '<span class="warn"> repaint held while selecting</span>' : '');
    head.className = stateClass;
    head.innerHTML =
      '<span><b>' + esc(s.host) + '</b> ' + esc(s.repoRoot) + '</span>' +
      '<span>snapshot <b>' + esc(s.takenAtLocal) + '</b> age <b>' + Math.round(ageSec) + 's</b> <span class="state ' + stateClass + '">' + esc(state) + '</span></span>' +
      '<span>collected in <b>' + esc(s.durationMs) + ' ms</b>, refresh every <b>' + INTERVAL + 's</b></span>' +
      '<span>writer pid <b>' + esc(w.pid || '?') + '</b> ' + esc(w.state || '?') + ', tick ' + esc(w.tick === undefined ? '?' : w.tick) + ', since ' + local(w.startedAtUtc) + hold + '</span>' +
      (s.chatError ? '<span class="bad">chat: ' + esc(s.chatError) + '</span>' : '');
    // Sticky section headings park directly under this block, whose height changes as it wraps.
    document.documentElement.style.setProperty('--headh', head.offsetHeight + 'px');
  }

  // Problems (owner ruling 2026-09-10): the one section read before any other - lock queues, dead
  // leases and stuck tickets, and a build that crashed rather than just failed, collapsed to one
  // line each. A run with nothing here needs no further reading; a row names which section below
  // carries the detail. Deliberately independent of the roster further down: it reads only the
  // primitive arrays the snapshot already carries (locks, stalls, leases), so a bug in the roster
  // join can never hide it.
  //
  // `s.gates` is deliberately NOT a source here, even though "a failed gate" sounds like the
  // obvious read on "build crash". Measured live 2026-09-10: the canon collector marks a whole run
  // FAIL the moment ANY gate in it is SKIP (not applicable to that change set) - it never carried a
  // per-gate PASS/SKIP/FAIL split into `failures[]` (gate/scope/count only), only into the raw
  // temp/metrics/gate-executions.jsonl lines this page cannot read (S2413: "a source reader must
  // stay in the snapshot function"). On the live tree that turns nearly every post-change run red:
  // one sampled run carried 44 SKIP gates and zero real failures, reported as one giant FAIL. Redoing
  // that here would print the single noisiest line on the page on every ordinary run - exactly the
  // cry-wolf this panel exists to prevent. `gate health` below still shows it for whoever opens it.
  function renderProblems(s) {
    var items = [];
    function flag(sev, what, detail) { items.push({ sev: sev, what: what, detail: detail }); }

    arr(s.locks).forEach(function (k) {
      var q = arr(k.queue);
      if (!q.length) { return; }
      // "cold" matches the same rule the locks table below uses for a queue row: nobody has seen
      // the waiter in over 5 minutes, so it is not merely a busy domain but a waiter that may itself
      // be gone.
      var cold = q.some(function (t) { return t.lastSeenMinutes === null || t.lastSeenMinutes === undefined || t.lastSeenMinutes > 5; });
      var longest = 0;
      q.forEach(function (t) { if ((t.waitedMinutes || 0) > longest) { longest = t.waitedMinutes; } });
      flag(cold ? 'bad' : 'warn', 'lock queue', esc(k.domain) + ': ' + q.length + ' waiting, longest ' + mins(longest) + (cold ? ', waiter unseen' : ''));
    });

    arr(s.stalls).forEach(function (k) {
      // S2582: `no-cpu` is the build-domain rule alone (Build.* only) - the holder PROCESS is gone
      // while the lock is still held, which is a crashed or killed build, not merely a slow one.
      // Every other rule (`quiet-owner`, Code.* domains) is an agent gone silent mid-ticket, which
      // reads as a stuck ticket rather than a build problem.
      if (k.rule === 'no-cpu') {
        flag('bad', 'build crash', esc(k.domain) + ': holder process gone, held ' + mins(k.heldMinutes) + ', blocking ' + (k.queueDepth || 0));
      } else {
        flag('bad', 'stalled ticket', esc(k.domain) + ' held by ' + esc(k.name || 'unnamed') + ', blocking ' + (k.queueDepth || 0) + ', held ' + mins(k.heldMinutes));
      }
    });

    arr(s.leases).forEach(function (l) {
      if (l.liveness === 'foreign-stale' || l.liveness === 'unknown') {
        flag(l.liveness === 'foreign-stale' ? 'bad' : 'warn', 'dead lease', 'ticket ' + esc(l.id) + ' (' + esc(l.name || 'unnamed') + '), quiet ' + mins(l.lastSeenMinutes) + ', ' + esc(l.liveness));
        return;
      }
      // The fourth category, and the one the panel opened without (measured live 2026-09-10):
      // lease S2859 had been quiet 37 min, the roster painted its agent red as NO LIFE, and this
      // panel printed ALL CLEAR directly above it - because the harness judges a lease against a
      // 45-minute window and still called it `foreign-live`. The roster's ten-minute rule is the
      // stricter of the two and the one the reader sees, so the panel now applies it here too,
      // still from a primitive array (`leases`) rather than from the roster join. A ticket nobody
      // is waiting for produces no `stalls` entry, so this is the only place it can surface.
      if (l.lastSeenMinutes === null || l.lastSeenMinutes === undefined || l.lastSeenMinutes > ROSTER_ACTIVE_MINUTES) {
        flag('bad', 'abandoned ticket', 'ticket ' + esc(l.id) + ' held by ' + esc(l.name || 'unnamed') +
          ', quiet ' + mins(l.lastSeenMinutes) + ' - see agents');
      }
    });

    if (!items.length) {
      el('problems-note').textContent = 'all clear - the sections below are detail, not alarm';
      el('problems').innerHTML = '<div class="clear">' + cls('ok', 'ALL CLEAR') +
        '<span class="dim"> - no lock queue, no dead lease, no abandoned ticket, no stalled ticket, no build crash</span></div>';
      return;
    }
    items.sort(function (a, b) { return (a.sev === 'bad' ? 0 : 1) - (b.sev === 'bad' ? 0 : 1); });
    var bad = items.filter(function (p) { return p.sev === 'bad'; }).length;
    el('problems-note').textContent = bad + ' red, ' + (items.length - bad) + ' yellow - open the matching section below';
    el('problems').innerHTML = items.map(function (p) {
      return '<div class="line ' + p.sev + '">' + cls(p.sev, p.sev === 'bad' ? 'PROBLEM' : 'WARN') + ' <b>' + esc(p.what) + '</b> ' + p.detail + '</div>';
    }).join('');
  }

  function render(s) {
    var i, r, rows;
    renderProblems(s);

    // A running child is a process, and a nickname belongs to a SESSION, so the two are joined by
    // the ticket and never by the pid: measured 2026-09-03, one `-p` run showed children 27264 and
    // 20056 against a lease whose pid was 26808 - the child process is not the process that claimed
    // the ticket. Preference order matches the snapshot's own: the lease holder first (it is the
    // session that owns the work), then an agent holding that lease, then the last agent to have
    // written about the ticket. Nothing matches on a child that has claimed nothing yet, and the
    // cell says so rather than inventing a name.
    // The lease holder is the only AUTHORITATIVE source; the other two are guesses, and a guess is
    // marked as one rather than printed like a fact - measured 2026-09-03, two children on S2466
    // were labelled with an agent whose last message was `released S2466 (forced)`.
    var nameByTicket = {};
    function claim(ticket, nm, src) { if (ticket && nm && !nameByTicket[ticket]) { nameByTicket[ticket] = { name: nm, src: src }; } }
    arr(s.leases).forEach(function (l) { claim(l.id, l.name, 'holds the lease'); });
    arr(s.agents).forEach(function (a) { claim(a.lease, a.name, 'agent row carries this lease'); });
    arr(s.agents).forEach(function (a) { claim(a.lastTicket, a.name, 'last agent to write about the ticket - the lease is held by nobody'); });
    function ticketFromReason(reason) {
      var found = String(reason || '').match(/\bS\d{4}\b/);
      return found ? found[0] : '';
    }
    // The session id travels with the name so a lock row joins by eye with its roster row above -
    // a nickname alone was ambiguous exactly where it mattered, because an identity that never
    // posted to the chat is named by a slice of its own id.
    function lockExecutor(ticket, executor, sessionId) {
      var who = (executor ? name(executor) : '<span class="dim">unnamed</span>') +
        (sessionId ? ' <span class="dim">' + esc(shortId(sessionId)) + '</span>' : '');
      var owner = ticket ? nameByTicket[ticket] : null;
      if (!owner) { return who; }
      if (owner.name === executor) {
        return who + '<br><span class="dim">ticket ' + id(ticket) + ' owner</span>';
      }
      return who + '<br><span class="dim">ticket ' + id(ticket) + ' owner ' + name(owner.name) + '</span>';
    }

    // Two headless children on one ticket is either a launch race or an orphan left behind; the
    // pair looks perfectly ordinary row by row, so the duplication itself is what gets said.
    var perTicket = {};
    arr(s.children).forEach(function (c) { perTicket[c.ticket] = (perTicket[c.ticket] || 0) + 1; });

    // One roster keyed by session id (owner finding 2026-09-10). The page used to carry four
    // disjoint identity spaces: `running` admitted only live-lease owners and agents whose NEWEST
    // chat message was `kind=session`, so an agent that was actually working - posting phase,
    // progress or lock - was absent from it, while the lock table named holders and queue waiters
    // that appeared nowhere else. Measured on the 12:20 snapshot: Code.Scripts was held by
    // jade-gecko-0910-1120, which no other section listed, and its queue head printed as `codex-ta`.
    // Every source below already carries the session id - the page simply never joined on it, so
    // the reader was left to guess that four spellings were one agent.
    var roster = {};
    var order = [];
    function slot(sid) {
      if (!sid) { return null; }
      if (!roster[sid]) {
        roster[sid] = {
          id: sid, name: '', runtime: '', model: '', instance: '',
          ticket: '', ticketSrc: '', phase: '', phaseNote: '', phaseAge: null,
          holds: [], waits: [], seen: null, note: '', lastKind: '',
          silent: null, context: null, contextOver: false, liveness: ''
        };
        order.push(sid);
      }
      return roster[sid];
    }
    // The freshest evidence wins: a lease heartbeat, a chat message and a session record are three
    // different clocks on one agent, and the oldest of them would report a working agent as quiet.
    function freshest(row, m) {
      if (m === null || m === undefined) { return; }
      if (row.seen === null || m < row.seen) { row.seen = m; }
    }
    // A lease is the only authoritative ticket; the rest are marked as guesses, the same discipline
    // the child rows below already apply to a name.
    function ticketGuess(row, ticket, src) {
      if (!ticket || row.ticketSrc === 'lease') { return; }
      if (row.ticket && row.ticketSrc) { return; }
      row.ticket = ticket; row.ticketSrc = src;
    }
    // A uuid is unique in its first eight characters and a `codex-takeover-<epoch>` id is unique only
    // in its last: the harness name fallback slices the HEAD of both, which is why several distinct
    // codex sessions all printed as `codex-ta` and joined with nothing.
    function shortId(v) {
      var t = String(v || '');
      if (t.length <= 12) { return t; }
      if (/^[0-9a-f]{8}-/i.test(t)) { return t.slice(0, 8); }
      return '..' + t.slice(-8);
    }
    // The harness falls back to the id's first eight characters when an identity never posted a
    // message, so a `name` that is a prefix of its own id is not a nickname and must not read as one.
    function rosterName(row) {
      var real = row.name && !(row.name.length === 8 && row.id.indexOf(row.name) === 0);
      return (real ? name(row.name) : '<span class="dim">unnamed</span>') +
        ' <span class="dim">' + esc(shortId(row.id)) + '</span>';
    }
    var stalledDomains = {};
    arr(s.stalls).forEach(function (k) { stalledDomains[k.domain] = k.rule || 'stalled'; });

    arr(s.agents).forEach(function (a) {
      var row = slot(a.id); if (!row) { return; }
      if (!row.name) { row.name = a.name; }
      row.runtime = a.runtime; row.model = a.model; row.instance = a.instance;
      row.silent = a.silent; row.lastKind = a.lastKind; row.note = a.lastNote;
      row.context = a.contextBand; row.contextOver = a.contextOverThreshold;
      row.phase = a.phase; row.phaseNote = a.phaseNote; row.phaseAge = a.phaseAgeMinutes;
      if (a.lease) { row.ticket = a.lease; row.ticketSrc = 'lease'; }
      else { ticketGuess(row, a.phaseTicket, 'phase message'); ticketGuess(row, a.lastTicket, 'last message'); }
      freshest(row, a.ageMinutes);
    });
    arr(s.sessions).forEach(function (x) {
      var row = slot(x.id); if (!row) { return; }
      if (!row.name) { row.name = x.name; }
      if (!row.runtime || row.runtime === 'unknown') { row.runtime = x.runtime; }
      if (x.ticket) { row.ticket = x.ticket; row.ticketSrc = 'lease'; }
      freshest(row, x.ageMinutes);
    });
    arr(s.leases).forEach(function (l) {
      var row = slot(l.sessionId); if (!row) { return; }
      if (!row.name) { row.name = l.name; }
      row.ticket = l.id; row.ticketSrc = 'lease'; row.liveness = l.liveness;
      freshest(row, l.lastSeenMinutes);
    });
    arr(s.locks).forEach(function (k) {
      if (k.held && k.sessionId) {
        var row = slot(k.sessionId);
        if (!row.name) { row.name = k.name; }
        row.holds.push({ domain: k.domain, minutes: k.heldMinutes, reason: k.reason, stalled: stalledDomains[k.domain] });
        ticketGuess(row, ticketFromReason(k.reason), 'lock reason');
      }
      arr(k.queue).forEach(function (t, qi) {
        if (!t || !t.sessionId) { return; }
        var qr = slot(t.sessionId);
        if (!qr.name) { qr.name = t.name; }
        var cold = t.lastSeenMinutes === null || t.lastSeenMinutes === undefined || t.lastSeenMinutes > 5;
        qr.waits.push({ domain: k.domain, position: qi + 1, minutes: t.waitedMinutes, reason: t.reason, cold: cold });
        ticketGuess(qr, ticketFromReason(t.reason), 'queue reason');
      });
    });

    // Whoever is blocking someone reads first, then whoever is blocked, then by freshness: the two
    // questions the page is opened with are "who is holding this" and "who is stuck behind it".
    // A ticket outranks a bare `live` row: an agent that has claimed work is part of what is
    // happening, and one whose whole trace is a lock it released four minutes ago is not.
    function rank(row) {
      if (row.holds.length) { return 0; }
      if (row.waits.length) { return 1; }
      if (row.silent === true) { return 4; }
      if (row.ticket) { return 2; }
      return 3;
    }
    order.sort(function (a, b) {
      var ra = rank(roster[a]), rb = rank(roster[b]);
      if (ra !== rb) { return ra - rb; }
      var sa = roster[a].seen, sb = roster[b].seen;
      if (sa === null) { return 1; }
      if (sb === null) { return -1; }
      return sa - sb;
    });

    // The chat window is three hours wide, so the roster's raw length is a history, not a picture:
    // measured 2026-09-10, 38 of 60 agents had said nothing for 45 minutes and held nothing. Those
    // collapse into one line. The cut is ten minutes, not the harness `SilentMinutes` of 45 (owner
    // ruling 2026-09-10, narrowed from the thirty it opened at): an agent that finished its ticket
    // ten minutes ago is sitting quietly somewhere and is no longer part of what is happening now.
    // It is safe to cut this hard only because ownership overrides it - an agent holding a domain, a
    // queue place or a ticket is NEVER collapsed however quiet it is, which is precisely the stalled
    // holder the page exists to expose; without that override the tighter window would hide it.
    // Declared at the top of the page, not here: the problems panel judges an abandoned ticket by
    // the same number, and two copies of it would let the panel print ALL CLEAR over a red row.
    // What is OWNED - a lock domain, a queue position or a ticket lease - decides both that a row
    // survives the cut and that a quiet row is an alarm, so the two rules read one predicate. They
    // were written separately at first and disagreed on exactly one case: a quiet agent holding only
    // a ticket was collapsed into the hidden line, so the red row the cut exists to expose was the
    // one row it removed. Caught by a fixture, never by the live tree, which had no such agent.
    function ownsSomething(row) {
      return row.holds.length > 0 || row.waits.length > 0 || row.ticketSrc === 'lease';
    }
    var hidden = [];
    var shown = [];
    order.forEach(function (sid) {
      var row = roster[sid];
      if (ownsSomething(row)) { shown.push(sid); return; }
      if (row.seen === null || row.seen > ROSTER_ACTIVE_MINUTES) { hidden.push(row); return; }
      // A ticket of ANY provenance keeps a fresh row (owner ruling 2026-09-10, third pass): the
      // roster answers "who is working on what", and an agent with no ticket, no hold and no queue
      // place has nothing to put in that sentence. Measured on the 16:15 snapshot: of 18 rows called
      // active, half were sessions whose entire trace was `lock released Build.Phone` a few minutes
      // earlier - each costing two lines - so the half that WAS working did not fit on one screen.
      // Those go to the collapsed line, which names them with what they last did, so nothing is lost.
      if (!row.ticket) { hidden.push(row); return; }
      shown.push(sid);
    });

    var ROSTER_HEAD = ['state', 'agent', 'runtime/model', 'ticket', 'phase', 'holds', 'waiting for', '#seen'];
    // The note is the widest thing on the page and the only cell that wraps, so as a column it set
    // the height of every row it sat on and pushed the eight narrow columns into a strip on the left
    // (owner ruling 2026-09-10). It moves to a row of its own directly under its agent, spanning the
    // table; the agent row drops its bottom border so the pair still reads as one entry.
    function noteRow(html, alarm) {
      return tr([{ w: '<span class="dim">' + html + '</span>', span: ROSTER_HEAD.length }], 'note' + (alarm ? ' alarm' : ''));
    }
    rows = [];
    shown.forEach(function (sid) {
      var row = roster[sid];
      var stalled = row.holds.some(function (h) { return h.stalled; });
      // An agent that has gone quiet while still holding a domain or a ticket is the one failure the
      // page must not render as an ordinary row (owner ruling 2026-09-10): everyone queued behind it
      // waits on something that is not coming back. It is a WIDER net than the `stalls` array above -
      // that verdict needs a queue behind the holder before it fires, and a dead agent sitting on a
      // ticket nobody is waiting for still blocks that ticket. A stale lease liveness counts however
      // recent the chat is: the harness judges a lease by its own evidence, and that verdict wins.
      var quiet = row.seen === null || row.seen > ROSTER_ACTIVE_MINUTES;
      var dead = (quiet && ownsSomething(row)) || row.liveness === 'foreign-stale';
      var state, klass;
      if (stalled) { state = 'STALLED'; klass = 'bad'; }
      else if (dead) { state = 'NO LIFE'; klass = 'bad'; }
      else if (row.holds.length) { state = 'holds'; klass = 'warn'; }
      else if (row.waits.length) { state = 'waiting'; klass = 'warn'; }
      else if (row.silent === true) { state = 'SILENT'; klass = 'warn'; }
      else { state = 'live'; klass = 'norm'; }
      // A cell with nothing in it is a plain `-`, never a wrap cell: see the floor rule in tr().
      var DASH = '<span class="dim">-</span>';
      // `?/?` on fifteen rows out of eighteen is a column of punctuation. The runtime and the model
      // are printed when they are known and the cell says nothing when they are not.
      var known = [];
      if (row.runtime && row.runtime !== 'unknown') { known.push(esc(row.runtime)); }
      if (row.model && row.model !== 'unknown') { known.push(esc(row.model)); }
      if (row.instance && row.instance !== '-') { known.push(esc(row.instance)); }
      var ticket = row.ticket
        ? id(row.ticket) + (row.ticketSrc === 'lease' ? '' : '<span class="dim" title="' + esc(row.ticketSrc) + '"> ?</span>')
        : DASH;
      // The note carries the phase, not the `phase` field: a `/spec-all` stage boundary posts
      // `stage S1 done -> S2 (implementation)` as the NOTE and leaves `phase` empty, and gating the
      // whole cell on `phase` threw that sentence away - measured 2026-09-10, the one agent then
      // working printed `-` in the column the roster exists for while its stage sat in the snapshot.
      var phase = (row.phase || row.phaseNote)
        ? (row.phase ? esc(row.phase) : '') + (row.phaseNote ? (row.phase ? ' ' : '') + '<span class="dim">' + esc(row.phaseNote) + '</span>' : '') +
          (row.phaseAge !== null && row.phaseAge !== undefined ? ' <span class="dim">(' + mins(row.phaseAge) + ' ago)</span>' : '')
        : '';
      var holds = row.holds.length
        ? row.holds.map(function (h) {
            return '<span title="' + esc(h.reason) + '">' + cls(h.stalled ? 'bad' : 'warn', h.domain) + ' ' + mins(h.minutes) +
              (h.stalled ? ' ' + cls('bad', h.stalled) : '') + '</span>';
          }).join('<br>')
        : '';
      var waits = row.waits.length
        ? row.waits.map(function (q) {
            return '<span title="' + esc(q.reason) + '">' + cls(q.cold ? 'warn' : 'dim', q.domain + ' #' + q.position) +
              ' ' + mins(q.minutes) + (q.cold ? ' ' + cls('warn', 'cold') : '') + '</span>';
          }).join('<br>')
        : '';
      var context = row.contextOver ? ' ' + cls('bad', row.context) : '';
      var alarm = stalled || dead;
      // The alarm names what is still held and how long the silence has run, because the row above
      // says only that something is wrong - the reader's next question is always "on what".
      var held = row.holds.map(function (h) { return h.domain; })
        .concat(row.ticketSrc === 'lease' && row.ticket ? ['ticket ' + row.ticket] : []).join(' + ');
      // The note row is kept only where it SAYS something (owner ruling 2026-09-10, third pass:
      // "the row stays, but only when it is meaningful"). Two kinds of line earn no second row:
      //   - a `phase` message, whose text the phase cell one column to the left already prints
      //     verbatim - `lastNote` and `phaseNote` are the same string whenever the newest message is
      //     the phase, which was half the note rows on the live page, the same sentence twice;
      //   - `session started/ended`, which the state and the `seen` columns already carry.
      // An alarm always keeps its row: that red sentence is the reason the alarm exists.
      var noteIsPhaseEcho = row.lastKind === 'phase' && row.phaseNote && row.note === row.phaseNote;
      var noteIsSessionBookkeeping = row.lastKind === 'session';
      var note = '';
      if (row.note && !noteIsPhaseEcho && !noteIsSessionBookkeeping) {
        // A note is one chat line, and a chat line can be a paragraph: measured 2026-09-10, one gate
        // report ran some 600 characters and its row alone was five lines of the roster - the single
        // widest thing on the page after the fix that gave the text columns their width back. The
        // cut keeps the part that identifies the message and hangs the rest on the row's tooltip;
        // the chat section below still carries every line in full, which is where a note is read.
        var plain = String(row.note).replace(/\s+/g, ' ');
        var short = plain.length > 200 ? plain.substr(0, 198) + '..' : plain;
        note = (row.lastKind ? bold(row.lastKind) + ' ' : '') +
          (short === plain ? esc(plain) : '<span title="' + esc(plain) + '">' + esc(short) + '</span>');
      }
      if (dead) {
        note = cls('bad', 'quiet ' + mins(row.seen) + ' and still holding ' + (held || 'something') +
          (row.liveness === 'foreign-stale' ? '; the lease itself reads stale' : '')) +
          (note ? ' &middot; last: ' + note : '');
      }
      rows.push(tr([
        cls(klass, state),
        rosterName(row),
        (known.length ? known.join('/') : DASH) + context,
        ticket, { w: phase }, { s: holds }, { s: waits }, { n: mins(row.seen) }
      ], (alarm ? 'alarm ' : '') + (note ? 'hasnote' : '')));
      if (note) { rows.push(noteRow(note, alarm)); }
    });
    // A headless child is a PROCESS and a nickname belongs to a SESSION, so it keeps its own rows
    // below the sessions rather than being folded into one: measured 2026-09-03, one `-p` run showed
    // children 27264 and 20056 against a lease whose pid was 26808.
    arr(s.children).forEach(function (c) {
      var q = c.quietMinutes;
      var sameAsAge = q !== null && q !== undefined && c.ageMinutes !== null && c.ageMinutes !== undefined && Math.abs(q - c.ageMinutes) < 0.05;
      var qc = q === null || q === undefined ? 'dim' : (q >= 15 ? 'bad' : (q >= 5 ? 'warn' : (sameAsAge ? 'dim' : 'norm')));
      var lw = c.lastWriteMinutes === null || c.lastWriteMinutes === undefined ? 'nothing on disk yet'
        : (c.writeBeforeStart ? 'no write since start; ' + esc(c.lastWritePath) : mins(c.lastWriteMinutes) + ' ago  ' + esc(c.lastWritePath));
      var hit = nameByTicket[c.ticket];
      var who = hit
        ? (hit.src === 'holds the lease' ? name(hit.name) : '<span class="dim" title="' + esc(hit.src) + '">' + esc(hit.name) + ' ?</span>')
        : '<span class="dim">unnamed</span>';
      var tk = id(c.ticket) + (perTicket[c.ticket] > 1 ? ' ' + cls('warn', 'x' + perTicket[c.ticket] + ' duplicate') : '');
      rows.push(tr([
        '<span class="dim">child ' + esc(c.pid) + '</span>', who, esc(c.model), tk,
        '<span class="dim">-</span>', '<span class="dim">-</span>', '<span class="dim">-</span>',
        { n: mins(c.ageMinutes) }
      ], 'hasnote'));
      rows.push(noteRow(cls(qc, mins(q) + (q >= 15 ? ' quiet' : '')) + '  ' + lw, false));
    });
    if (hidden.length) {
      // Named, not merely counted: the reader is usually looking for one agent he remembers, and a
      // bare count would send him to the chat section to find out whether it is even on the machine.
      // The first three carry what they last did, because a collapsed row is now also where a FRESH
      // agent goes if it holds nothing and carries no ticket - the line has to be enough to tell
      // "finished and went quiet" from "just released a lock", without opening the chat.
      var some = hidden.slice(0, 6).map(function (r, ix) {
        var who = esc(r.name || shortId(r.id));
        if (ix > 2 || !r.note) { return who; }
        var what = String(r.note).replace(/\s+/g, ' ');
        if (what.length > 44) { what = what.substr(0, 42) + '..'; }
        return who + ' (' + esc((r.lastKind ? r.lastKind + ' ' : '') + what) + ', ' + mins(r.seen) + ')';
      }).join(', ');
      rows.push(tr([{
        w: '<span class="dim">' + hidden.length + ' agent(s) collapsed - no ticket, nothing held, ' +
          'nothing queued, or quiet over ' + ROSTER_ACTIVE_MINUTES + ' min: ' + some +
          (hidden.length > 6 ? ' and ' + (hidden.length - 6) + ' more' : '') + '</span>',
        span: ROSTER_HEAD.length
      }], 'group'));
    }
    el('agents-note').textContent = shown.length + ' working of ' + order.length + ' agent(s), ' +
      arr(s.children).length + ' headless child(ren); a row is here when it holds or is queued for a ' +
      'domain at any age, or carries a ticket and was seen in the last ' + ROSTER_ACTIVE_MINUTES + ' min';
    table('agents', ROSTER_HEAD, rows, 'no agent, lease, lock or headless child observable');

    rows = [];
    arr(s.leases).forEach(function (l) {
      var lv = l.liveness === 'foreign-stale' ? cls('bad', 'stale') : (l.liveness === 'unknown' ? cls('warn', 'unknown') : cls('norm', 'live'));
      // The lease pid and the `-p` child pid are different processes on the same ticket (measured
      // 2026-09-03: lease 26808, children 27264 and 20056), so the page says so instead of leaving
      // two unexplained numbers on one screen.
      var kids = arr(s.children).filter(function (c) { return c.ticket === l.id; }).map(function (c) { return c.pid; });
      var where = esc(l.host) + ' pid ' + esc(l.pid) + (kids.length ? ' <span class="dim">child ' + esc(kids.join(',')) + '</span>' : '');
      rows.push(tr([id(l.id), name(l.name), lv, { n: mins(l.ageMinutes) }, { n: mins(l.lastSeenMinutes) }, { w: esc(l.reason) }, where, '<span class="dim">' + esc(l.sessionId) + '</span>']));
    });
    table('leases', ['ticket', 'holder', 'liveness', '#claimed', '#last seen', 'reason', 'where', 'session'], rows, 'nothing leased');

    // S2855: the device park - what each test device is, who is driving it, and what was last
    // installed on it. Hidden entirely while the park is unknown: an always-present empty section
    // stops being read, the same rule the stalled-holders section above already follows.
    rows = [];
    arr(s.devices).forEach(function (d) {
      var leaseCell;
      if (d.lease) {
        var lv = d.lease.liveness === 'foreign-stale' ? cls('bad', 'stale')
          : (d.lease.liveness === 'unknown' ? cls('warn', 'unknown') : cls('norm', d.lease.liveness || 'live'));
        leaseCell = lv + ' <span class="dim">' + esc(shortId(d.lease.sessionId)) + ', ' + mins(d.lease.ageMinutes) + '</span>';
      } else {
        leaseCell = cls('norm', 'free');
      }
      var markCell;
      if (d.mark) {
        var variant = [d.mark.flavor, d.mark.buildType].filter(Boolean).join('/');
        var when = d.mark.installedAt ? local(Number(d.mark.installedAt)) : '';
        markCell = esc(d.mark.package) + ' <b>' + esc(d.mark.versionName) + '</b>' +
          (variant ? ' <span class="dim">[' + esc(variant) + ']</span>' : '') +
          ' <span class="dim">' + esc(d.mark.recordedBy || '') + (when ? ', ' + when : '') + '</span>';
      } else {
        markCell = '<span class="dim">no record</span>';
      }
      rows.push(tr([
        id(d.id),
        esc([d.model, d.role].filter(Boolean).join(' / ')) || '<span class="dim">-</span>',
        leaseCell,
        { w: markCell }
      ]));
    });
    el('devices-box').style.display = rows.length ? '' : 'none';
    if (rows.length) { table('devices', ['device', 'model / role', 'lease', 'last install'], rows, ''); }

    // S2413: the same array the terminal monitor draws, and hidden by the same rule - a section
    // that is present on every refresh stops being read long before the one run that needed it.
    rows = [];
    arr(s.stalls).forEach(function (k) {
      var pn = k.holderProcessAlive ? cls('warn', 'process alive - hung') : cls('bad', 'no process observable');
      // S2582: the rule is a column of its own, and the evidence cell follows it - a build row is
      // judged on CPU and a code row on owner silence, so one shared "quiet" column would print a
      // number the build rule never looked at.
      var rule = k.rule || 'quiet-owner';
      var evidence = rule === 'no-cpu'
        ? cls('bad', 'no CPU: tree ' + esc(k.treeCpuSeconds) + 's, engine ' + esc(k.engineCpuSeconds) + 's over ' + esc(k.sampleSeconds) + 's')
        : cls('bad', mins(k.quietMinutes) + ' quiet, limit ' + esc(k.thresholdMinutes) + 'm');
      rows.push(tr(['<b>' + esc(k.domain) + '</b>', name(k.name), esc(rule), evidence, { n: mins(k.heldMinutes) }, { n: k.queueDepth }, { n: mins(k.longestWaitMinutes) }, pn, { w: esc(k.reason) }]));
    });
    el('stalls-box').style.display = rows.length ? '' : 'none';
    if (rows.length) { table('stalls', ['domain', 'holder', 'rule', 'evidence', '#held', '#waiting', '#longest wait', 'process', 'reason'], rows, ''); }

    rows = [];
    // Five domains printed as five `free` rows is seven lines that never say anything; the one-line
    // form still NAMES every domain, so nothing is hidden, and the table expands the moment a single
    // domain is held or has a queue.
    var busy = arr(s.locks).filter(function (k) { return k.held || (k.queue && k.queue.length) || k.unreadable; });
    if (arr(s.locks).length && !busy.length) {
      rows.push(tr([{ w: cls('norm', 'all free') + '<span class="dim">: ' + esc(arr(s.locks).map(function (k) { return k.domain; }).join(', ')) + '</span>', span: 6 }], 'group'));
      // A free table says nothing about whether the machine is idle or simply between two holds:
      // measured 2026-09-03 over the chat's own lock events, a hold runs 1.5-147 s (median 20.8 s),
      // so minutes of genuine free time look exactly like a broken page. The trail is the answer -
      // the same acquire/release lines the chat already carries, hoisted next to the empty table.
      var trail = arr(s.chat).filter(function (m) { return m.kind === 'lock'; }).slice(0, 6);
      trail.forEach(function (m) {
        var verb = /^released/.test(m.note || '') ? '<span class="dim">' + esc(m.note) + '</span>' : cls('norm', m.note || '');
        rows.push(tr([{ w: '<span class="dim">' + esc(local(m.atUtc)) + '  ' + mins(m.ageMinutes) + ' ago</span>', span: 2 }, name(m.name), { w: verb, span: 3 }]));
      });
      if (!trail.length) { rows.push(tr([{ w: '<span class="dim">no lock taken inside the chat window</span>', span: 6 }])); }
    }
    (busy.length ? arr(s.locks) : []).forEach(function (k) {
      var q = arr(k.queue);
      var state, klass;
      if (k.held) { state = 'HELD'; klass = 'warn'; }
      else if (q.length) { state = 'free, ' + q.length + ' queued'; klass = 'warn'; }
      else { state = 'free'; klass = 'norm'; }
      var dom = k.legacy ? esc(k.domain.toUpperCase()) + '.LOCK (pre-split, covers every ' + esc(k.domain.toLowerCase()) + ' domain)' : esc(k.domain);
      if (k.held || q.length) { dom = '<b>' + dom + '</b>'; }
      var ticket = ticketFromReason(k.reason);
      rows.push(tr([dom, cls(klass, state), k.held ? lockExecutor(ticket, k.name, k.sessionId) : '', { n: k.held ? mins(k.heldMinutes) : '' }, k.held ? local(k.acquiredAtUtc) : '', { w: k.unreadable ? cls('bad', 'unreadable lock file') : esc(k.reason) }]));
      for (i = 0; i < q.length; i++) {
        var t = q[i];
        var cold = t.lastSeenMinutes === null || t.lastSeenMinutes === undefined || t.lastSeenMinutes > 5;
        var waiter = (t.name ? name(t.name) : '<span class="dim">unnamed</span>') +
          (t.sessionId ? ' <span class="dim">' + esc(shortId(t.sessionId)) + '</span>' : '');
        rows.push(tr(['<span class="dim">    #' + (i + 1) + ' queued</span>', cls(cold ? 'warn' : 'dim', 'waiting ' + mins(t.waitedMinutes)), waiter, { n: '' }, cls(cold ? 'warn' : 'dim', 'seen ' + mins(t.lastSeenMinutes) + (cold ? ' cold' : '')), { w: esc(t.reason) }]));
      }
    });
    table('locks', ['domain', 'state', 'lock executor', '#held', 'since', 'reason'], rows, 'no lock domain known');

    rows = [];
    arr(s.gates).forEach(function (g) {
      var names = arr(g.failures).map(function (f) {
        return String(f.gate) + ' [' + String(f.scope === 'set-named' ? 'named your file' : (f.scope || 'unknown')) + ']';
      });
      // Capped, with the rest in the cell's own tooltip: the canon collector marks a whole run FAIL
      // the moment one gate inside it is SKIP, so an ordinary closure lands here with forty-odd
      // names attached and twelve of those rows were the largest block of text on the page - the
      // one table nobody could read. Nothing is dropped, the count leads, and the full list is one
      // hover away. Why the panel above refuses this array outright: the comment at renderProblems.
      var failed = names.length === 0 ? '<span class="dim">clean</span>'
        : '<span title="' + esc(names.join(', ')) + '">' + names.length + ': ' +
          esc(names.slice(0, 4).join(', ')) + (names.length > 4 ? ' <span class="dim">and ' + (names.length - 4) + ' more (hover)</span>' : '') + '</span>';
      rows.push(tr([local(g.atUtc), cls(g.status === 'PASS' ? 'norm' : 'bad', g.status || '?'), esc(g.runner), { w: failed }]));
    });
    table('gates', ['time', 'status', 'runner', 'failed gates'], rows, 'source silent');

    rows = [];
    arr(s.watchdog).forEach(function (w) {
      rows.push(tr([esc(w.at), bold(w.action), { w: esc(w.detail) }]));
    });
    table('watchdog', ['time', 'action', 'detail'], rows, 'source silent');

    rows = [];
    var nu = s.nextUp || {};
    var NEXTUP_HEAD = ['rel', 'ticket', 'status', 'changed', 'lease'];
    arr(nu.rows).forEach(function (q) {
      if (q.kind === 'group') { rows.push(tr([{ w: '<span class="dim">' + esc(q.text) + '</span>', span: NEXTUP_HEAD.length }], 'group')); return; }
      var st = statusCls(q.status);
      var tk = q.leased ? cls('warn', 'taken ' + q.taken) : cls('norm', 'free');
      rows.push(tr([esc(q.rel), id(q.id) + ' <span class="dim">' + esc(q.slug) + '</span>', st, esc(q.changed), tk]));
    });
    el('nextup-note').textContent = nu.package ? 'package ' + nu.package + ', ' + arr(nu.rows).filter(function (x) { return x.kind === 'row'; }).length + ' of ' + (nu.totalInPackage || 0) + ' rows in file order (PLAN/RELEASE_QUEUE.md)' : 'no current package';
    table('nextup', NEXTUP_HEAD, rows, 'queue file not found');

    rows = [];
    arr(s.chat).forEach(function (m) {
      rows.push(tr([local(m.atUtc), { n: mins(m.ageMinutes) }, bold(m.kind), name(m.name), id(m.ticket ? m.ticket + (m.phase ? '/' + m.phase : '') : ''), { w: esc(m.note) }]));
    });
    table('chat', ['time', '#age', 'kind', 'agent', 'ticket', 'note'], rows, 'no messages');

    rows = [];
    arr(s.findings).forEach(function (f) {
      rows.push(tr([bold(f.topic), name(f.name), { n: mins(f.ageMinutes) }, local(f.expiresAt), esc(f.scopeCount) + (f.device ? ', device ' + esc(f.device) : ''), { w: esc(f.note) }]));
    });
    el('findings-note').textContent = arr(s.findings).length + ' alive, ' + (s.findingsDead || 0) + ' dead';
    table('findings', ['topic', 'by', '#age', 'expires', 'scope paths', 'note'], rows, 'no alive finding');

    rows = [];
    var FINISHED_HEAD = ['ticket', 'status', 'moved', 'outcome', '#took', 'model', 'finished'];
    arr(s.instances).forEach(function (inst) {
      rows.push(tr([{ w: '<span class="dim">instance ' + esc(inst.instance) + ': ' + esc(inst.recorded) + ' run, ' + esc(inst.moved) + ' moved, ' + esc(inst.stayed) + ' stayed put; idle ' + esc(inst.idleToday) + ', timeouts ' + esc(inst.timeoutsToday) + ', cheap model ' + esc(inst.cheapModelShare) + '%</span>', span: FINISHED_HEAD.length }], 'group'));
      arr(inst.rows).forEach(function (r) {
        var k = r.moved ? 'norm' : (r.outcome !== 'ok' ? 'bad' : 'warn');
        rows.push(tr([id(r.id), esc(r.statusBefore) + ' -> ' + statusCls(r.statusAfter), cls(k, r.moved ? 'moved' : 'stayed'), cls(r.outcome === 'ok' ? 'norm' : 'bad', r.outcome), { n: num(r.minutes) + ' min' }, esc(r.model), local(r.finishedAt)]));
      });
    });
    table('finished', FINISHED_HEAD, rows, 'no run journal yet');

    rows = [];
    arr(s.stop).forEach(function (f) { rows.push(tr([cls('warn', f.name), 'requested ' + mins(f.requestedMinutes) + ' ago', { w: arr(s.children).length ? 'waiting for the running ticket(s) to end' : 'nothing is running - the next start clears the flag' }], 'stop')); });
    table('stop', ['flag', 'age', 'meaning'], rows, 'no stop requested');
  }

  function tickHeader() {
    if (!snap) { return; }
    // A hold released between two snapshots repaints here, within a second, rather than waiting.
    maybeRender();
    var ageSec = (Date.now() - new Date(snap.takenAtUtc).getTime()) / 1000;
    if (isNaN(ageSec)) { ageSec = 0; }
    var state = 'fresh', klass = 'ok';
    if (snap.writer && snap.writer.state === 'stopped') { state = 'writer stopped'; klass = 'bad'; }
    else if (ageSec >= INTERVAL * 3) { state = 'writer silent'; klass = 'bad'; }
    else if (errors > 0) { state = 'fresh, last load failed'; klass = 'warn'; }
    renderHead(snap, state, klass, ageSec);
    // The title is a text node, not markup: esc() here put `&amp;` in the tab label of any host
    // whose name carries an ampersand.
    document.title = (klass === 'ok' ? '' : '[' + state + '] ') + 'dev monitor ' + (snap.host || '');
  }

  window.__devMonitor = function (s) {
    if (!s || s.schema !== 1) { el('head').className = 'bad'; el('head').textContent = 'snapshot schema ' + (s && s.schema) + ' is not the schema this shell renders (1) - restart the writer'; return; }
    // A restarted writer wrote a newer shell; this one reloads once to pick it up.
    if (s.writer && s.writer.shellStamp && SHELL_STAMP && s.writer.shellStamp !== SHELL_STAMP) { location.reload(); return; }
    snap = s;
    errors = 0;
    maybeRender();
    tickHeader();
  };

  function load() {
    var old = document.getElementById('data');
    if (old) { old.parentNode.removeChild(old); }
    var sc = document.createElement('script');
    sc.id = 'data';
    sc.src = 'snapshot.js?t=' + Date.now();
    sc.onerror = function () {
      errors++;
      if (errors >= 2) { location.reload(); return; }
      tickHeader();
      if (!snap) { el('head').className = 'bad'; el('head').textContent = 'snapshot.js did not load - is the writer running? (.\\a.ps1 rmw)'; }
    };
    document.head.appendChild(sc);
  }

  load();
  setInterval(load, INTERVAL * 1000);
  setInterval(tickHeader, 1000);
})();
'@
    $js = $js.Replace('__INTERVAL__', "$interval").Replace('__STAMP__', ($Stamp -replace "[^A-Za-z0-9:.\-TZ]", ''))

    $body = @'
<h1>dev monitor</h1>
<div id="head" class="warn">loading snapshot.js ..</div>
<h2>problems <small id="problems-note">read this first</small></h2>
<div id="problems"></div>
<h2>agents <small id="agents-note"></small></h2>
<div id="agents"></div>
<h2>ticket leases <small>what is claimed now</small></h2>
<div id="leases"></div>
<div id="devices-box" style="display:none">
<h2>devices <small>the test park: who is driving what, and what was last installed (S2855)</small></h2>
<div id="devices"></div>
</div>
<div id="stalls-box" style="display:none">
<h2>stalled holders <small>quiet, holding, and blocking someone</small></h2>
<div id="stalls"></div>
</div>
<h2>locks <small>per domain, in queue order; every name here also has a row under agents</small></h2>
<div id="locks"></div>
<h2>watchdog actions <small>latest reaper and supervisor work</small></h2>
<div id="watchdog"></div>
<h2>next up <small id="nextup-note"></small></h2>
<div id="nextup"></div>
<h2>chat <small>newest first</small></h2>
<div id="chat"></div>
<h2>findings <small id="findings-note"></small></h2>
<div id="findings"></div>
<h2>finished <small>run journals per instance</small></h2>
<div id="finished"></div>
<h2>stop <small>queue stop flags</small></h2>
<div id="stop"></div>
<h2>gate health <small>recent closures and batches</small></h2>
<div id="gates"></div>
'@

    return @"
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>dev monitor</title>
<style>
$css
</style>
</head>
<body>
$body
<script>
$js
</script>
</body>
</html>
"@
}
