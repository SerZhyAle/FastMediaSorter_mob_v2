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
th{color:#8b949e;font-weight:600}
tr.group td{color:#8b949e;padding-top:3px;border-bottom:none}
tr.none td{color:#6e7681}
tr:hover td{background:#0d1117}
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

  function esc(v) {
    if (v === null || v === undefined) { return ''; }
    return String(v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function el(id) { return document.getElementById(id); }
  function num(v, d) { if (v === null || v === undefined || v === '') { return '?'; } var n = Number(v); if (isNaN(n)) { return esc(v); } return n.toFixed(d === undefined ? 0 : d); }
  function mins(v) {
    if (v === null || v === undefined) { return '?'; }
    var m = Number(v); if (isNaN(m)) { return '?'; }
    if (m < 1) { return Math.round(m * 60) + 's'; }
    if (m < 90) { return Math.round(m) + 'm'; }
    return (m / 60).toFixed(1) + 'h';
  }
  function local(iso) {
    if (!iso) { return ''; }
    var d = new Date(iso); if (isNaN(d.getTime())) { return esc(iso); }
    function two(x) { return (x < 10 ? '0' : '') + x; }
    return two(d.getHours()) + ':' + two(d.getMinutes()) + ':' + two(d.getSeconds());
  }
  function cls(c, word) { return '<span class="' + c + '">' + esc(word) + '</span>'; }
  function id(v) { return v ? '<span class="id">' + esc(v) + '</span>' : ''; }
  function name(v) { return v ? '<span class="name">' + esc(v) + '</span>' : ''; }
  function bold(v) { return v ? '<b>' + esc(v) + '</b>' : ''; }
  function statusCls(st) {
    if (!st) { return ''; }
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
        else if (c.w !== undefined) { k = 'wrap'; v = c.w; }
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
    render(snap);
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

  function render(s) {
    var i, r, rows;

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
    (s.leases || []).forEach(function (l) { claim(l.id, l.name, 'holds the lease'); });
    (s.agents || []).forEach(function (a) { claim(a.lease, a.name, 'agent row carries this lease'); });
    (s.agents || []).forEach(function (a) { claim(a.lastTicket, a.name, 'last agent to write about the ticket - the lease is held by nobody'); });

    // Two headless children on one ticket is either a launch race or an orphan left behind; the
    // pair looks perfectly ordinary row by row, so the duplication itself is what gets said.
    var perTicket = {};
    (s.children || []).forEach(function (c) { perTicket[c.ticket] = (perTicket[c.ticket] || 0) + 1; });

    rows = [];
    (s.children || []).forEach(function (c) {
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
      rows.push(tr(['<span class="big">' + esc(c.pid) + '</span>', who, tk, esc(c.model), { n: mins(c.ageMinutes) }, cls(qc, mins(q) + (q >= 15 ? ' quiet' : '')), { w: lw }]));
    });
    table('children', ['pid', 'agent', 'ticket', 'model', '#age', 'quiet', 'last write'], rows, 'no headless claude child is running');

    rows = [];
    (s.leases || []).forEach(function (l) {
      var lv = l.liveness === 'foreign-stale' ? cls('bad', 'stale') : (l.liveness === 'unknown' ? cls('warn', 'unknown') : cls('norm', 'live'));
      // The lease pid and the `-p` child pid are different processes on the same ticket (measured
      // 2026-09-03: lease 26808, children 27264 and 20056), so the page says so instead of leaving
      // two unexplained numbers on one screen.
      var kids = (s.children || []).filter(function (c) { return c.ticket === l.id; }).map(function (c) { return c.pid; });
      var where = esc(l.host) + ' pid ' + esc(l.pid) + (kids.length ? ' <span class="dim">child ' + esc(kids.join(',')) + '</span>' : '');
      rows.push(tr([id(l.id), name(l.name), lv, { n: mins(l.ageMinutes) }, { n: mins(l.lastSeenMinutes) }, { w: esc(l.reason) }, where, '<span class="dim">' + esc(l.sessionId) + '</span>']));
    });
    table('leases', ['ticket', 'holder', 'liveness', '#claimed', '#last seen', 'reason', 'where', 'session'], rows, 'nothing leased');

    // S2413: the same array the terminal monitor draws, and hidden by the same rule - a section
    // that is present on every refresh stops being read long before the one run that needed it.
    rows = [];
    (s.stalls || []).forEach(function (k) {
      var pn = k.holderProcessAlive ? cls('warn', 'process alive - hung') : cls('bad', 'no process observable');
      rows.push(tr(['<b>' + esc(k.domain) + '</b>', name(k.name), cls('bad', mins(k.quietMinutes) + ' quiet, limit ' + esc(k.thresholdMinutes) + 'm'), { n: mins(k.heldMinutes) }, { n: k.queueDepth }, { n: mins(k.longestWaitMinutes) }, pn, { w: esc(k.reason) }]));
    });
    el('stalls-box').style.display = rows.length ? '' : 'none';
    if (rows.length) { table('stalls', ['domain', 'holder', 'quiet', '#held', '#waiting', '#longest wait', 'process', 'reason'], rows, ''); }

    rows = [];
    // Five domains printed as five `free` rows is seven lines that never say anything; the one-line
    // form still NAMES every domain, so nothing is hidden, and the table expands the moment a single
    // domain is held or has a queue.
    var busy = (s.locks || []).filter(function (k) { return k.held || (k.queue && k.queue.length) || k.unreadable; });
    if ((s.locks || []).length && !busy.length) {
      rows.push(tr([{ w: cls('norm', 'all free') + '<span class="dim">: ' + esc((s.locks).map(function (k) { return k.domain; }).join(', ')) + '</span>', span: 6 }], 'group'));
      // A free table says nothing about whether the machine is idle or simply between two holds:
      // measured 2026-09-03 over the chat's own lock events, a hold runs 1.5-147 s (median 20.8 s),
      // so minutes of genuine free time look exactly like a broken page. The trail is the answer -
      // the same acquire/release lines the chat already carries, hoisted next to the empty table.
      var trail = (s.chat || []).filter(function (m) { return m.kind === 'lock'; }).slice(0, 6);
      trail.forEach(function (m) {
        var verb = /^released/.test(m.note || '') ? '<span class="dim">' + esc(m.note) + '</span>' : cls('norm', m.note || '');
        rows.push(tr([{ w: '<span class="dim">' + esc(local(m.atUtc)) + '  ' + mins(m.ageMinutes) + ' ago</span>', span: 2 }, name(m.name), { w: verb, span: 3 }]));
      });
      if (!trail.length) { rows.push(tr([{ w: '<span class="dim">no lock taken inside the chat window</span>', span: 6 }])); }
    }
    (busy.length ? (s.locks || []) : []).forEach(function (k) {
      var q = k.queue || [];
      var state, klass;
      if (k.held) { state = 'HELD'; klass = 'warn'; }
      else if (q.length) { state = 'free, ' + q.length + ' queued'; klass = 'warn'; }
      else { state = 'free'; klass = 'norm'; }
      var dom = k.legacy ? esc(k.domain.toUpperCase()) + '.LOCK (pre-split, covers every ' + esc(k.domain.toLowerCase()) + ' domain)' : esc(k.domain);
      if (k.held || q.length) { dom = '<b>' + dom + '</b>'; }
      rows.push(tr([dom, cls(klass, state), k.held ? name(k.name) : '', { n: k.held ? mins(k.heldMinutes) : '' }, k.held ? local(k.acquiredAtUtc) : '', { w: k.unreadable ? cls('bad', 'unreadable lock file') : esc(k.reason) }]));
      for (i = 0; i < q.length; i++) {
        var t = q[i];
        var cold = t.lastSeenMinutes === null || t.lastSeenMinutes === undefined || t.lastSeenMinutes > 5;
        rows.push(tr(['<span class="dim">    #' + (i + 1) + ' queued</span>', cls(cold ? 'warn' : 'dim', 'waiting ' + mins(t.waitedMinutes)), name(t.name), { n: '' }, cls(cold ? 'warn' : 'dim', 'seen ' + mins(t.lastSeenMinutes) + (cold ? ' cold' : '')), { w: esc(t.reason) }]));
      }
    });
    table('locks', ['domain', 'state', 'holder', '#held', 'since', 'reason'], rows, 'no lock domain known');

    rows = [];
    (s.agents || []).forEach(function (a) {
      var where = a.phaseTicket ? id(a.phaseTicket + (a.phase ? '/' + a.phase : '')) + (a.phaseNote ? ' ' + esc(a.phaseNote) : '') + ' <span class="dim">(' + mins(a.phaseAgeMinutes) + ' ago)</span>' : '<span class="dim">-</span>';
      // `unknown` printed in full for every agent is a column of noise that says the same thing a
      // dim `?` says; the field is still shown, so nothing is lost.
      var unk = function (v) { return (!v || v === 'unknown') ? '<span class="dim">?</span>' : esc(v); };
      rows.push(tr([name(a.name), cls(a.silent ? 'warn' : 'norm', a.silent ? 'SILENT' : 'live'), { n: mins(a.ageMinutes) }, unk(a.runtime) + '/' + unk(a.model) + (a.instance && a.instance !== '-' ? ' ' + esc(a.instance) : ''), id(a.lease), { w: where }, bold(a.lastKind) + (a.lastTicket ? ' ' + id(a.lastTicket) : ''), { w: esc(a.lastNote) }]));
    });
    var w = s.windows || {};
    el('agents-note').textContent = (s.agents || []).length + ' agents in the ' + (w.retentionMinutes || '?') + ' min window, SILENT after ' + (w.silentMinutes || '?') + ' min';
    table('agents', ['agent', 'state', '#last msg', 'runtime/model', 'lease', 'phase', 'last kind', 'last note'], rows, 'nobody has written');

    rows = [];
    var nu = s.nextUp || {};
    var NEXTUP_HEAD = ['rel', 'ticket', 'status', 'changed', 'lease'];
    (nu.rows || []).forEach(function (q) {
      if (q.kind === 'group') { rows.push(tr([{ w: '<span class="dim">' + esc(q.text) + '</span>', span: NEXTUP_HEAD.length }], 'group')); return; }
      var st = statusCls(q.status);
      var tk = q.leased ? cls('warn', 'taken ' + q.taken) : cls('norm', 'free');
      rows.push(tr([esc(q.rel), id(q.id) + ' <span class="dim">' + esc(q.slug) + '</span>', st, esc(q.changed), tk]));
    });
    el('nextup-note').textContent = nu.package ? 'package ' + nu.package + ', ' + (nu.rows || []).filter(function (x) { return x.kind === 'row'; }).length + ' of ' + (nu.totalInPackage || 0) + ' rows in file order (PLAN/RELEASE_QUEUE.md)' : 'no current package';
    table('nextup', NEXTUP_HEAD, rows, 'queue file not found');

    rows = [];
    (s.chat || []).forEach(function (m) {
      rows.push(tr([local(m.atUtc), { n: mins(m.ageMinutes) }, bold(m.kind), name(m.name), id(m.ticket ? m.ticket + (m.phase ? '/' + m.phase : '') : ''), { w: esc(m.note) }]));
    });
    table('chat', ['time', '#age', 'kind', 'agent', 'ticket', 'note'], rows, 'no messages');

    rows = [];
    (s.findings || []).forEach(function (f) {
      rows.push(tr([bold(f.topic), name(f.name), { n: mins(f.ageMinutes) }, local(f.expiresAt), esc(f.scopeCount) + (f.device ? ', device ' + esc(f.device) : ''), { w: esc(f.note) }]));
    });
    el('findings-note').textContent = (s.findings || []).length + ' alive, ' + (s.findingsDead || 0) + ' dead';
    table('findings', ['topic', 'by', '#age', 'expires', 'scope paths', 'note'], rows, 'no alive finding');

    rows = [];
    var FINISHED_HEAD = ['ticket', 'status', 'moved', 'outcome', '#took', 'model', 'finished'];
    (s.instances || []).forEach(function (inst) {
      rows.push(tr([{ w: '<span class="dim">instance ' + esc(inst.instance) + ': ' + esc(inst.recorded) + ' run, ' + esc(inst.moved) + ' moved, ' + esc(inst.stayed) + ' stayed put</span>', span: FINISHED_HEAD.length }], 'group'));
      (inst.rows || []).forEach(function (r) {
        var k = r.moved ? 'norm' : (r.outcome !== 'ok' ? 'bad' : 'warn');
        rows.push(tr([id(r.id), esc(r.statusBefore) + ' -> ' + statusCls(r.statusAfter), cls(k, r.moved ? 'moved' : 'stayed'), cls(r.outcome === 'ok' ? 'norm' : 'bad', r.outcome), { n: num(r.minutes) + ' min' }, esc(r.model), local(r.finishedAt)]));
      });
    });
    table('finished', FINISHED_HEAD, rows, 'no run journal yet');

    rows = [];
    (s.stop || []).forEach(function (f) { rows.push(tr([cls('warn', f.name), 'requested ' + mins(f.requestedMinutes) + ' ago', { w: (s.children || []).length ? 'waiting for the running ticket(s) to end' : 'nothing is running - the next start clears the flag' }], 'stop')); });
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
    document.title = (klass === 'ok' ? '' : '[' + state + '] ') + 'dev monitor ' + esc(snap.host);
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
<h2>running <small>headless claude children (-p)</small></h2>
<div id="children"></div>
<h2>ticket leases <small>what is claimed now</small></h2>
<div id="leases"></div>
<div id="stalls-box" style="display:none">
<h2>stalled holders <small>quiet, holding, and blocking someone</small></h2>
<div id="stalls"></div>
</div>
<h2>locks <small>who is building or editing, who waits, and - when nothing is held - who held one last</small></h2>
<div id="locks"></div>
<h2>agents <small id="agents-note"></small></h2>
<div id="agents"></div>
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
