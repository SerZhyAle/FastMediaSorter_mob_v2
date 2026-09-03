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
    readable data, every 3 seconds). Colour is limited to three classes - ok, warn, bad - and every
    coloured cell also carries the word, so the page reads without colour.

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
    $css = @'
html{color-scheme:light}
body{margin:0;padding:12px 16px 40px;background:#fafafa;color:#222;font:13px/1.45 Consolas,"Cascadia Mono","DejaVu Sans Mono",Menlo,monospace}
h1{font-size:15px;font-weight:600;margin:0 0 6px}
h2{font-size:16px;font-weight:700;margin:20px 0 6px;padding-bottom:3px;border-bottom:1px solid #bbb}
h2 small{font-weight:400;font-size:12px;color:#777}
.ok,.warn,.bad{font-weight:700}
.id{font-weight:700;font-size:14px}
.name{font-weight:700}
.big{font-size:15px;font-weight:700}
#head{font-size:14px}
#head .state{font-size:18px;font-weight:800;padding:0 6px}
#head.ok .state{color:#1a7f37}
#head.warn .state{color:#9a6700;background:#fff5d6}
#head.bad .state{color:#fff;background:#c62828}
tr.stop td{font-size:15px;font-weight:700}
.st-ok{color:#1a7f37;font-weight:700}
.st-warn{color:#9a6700;font-weight:700}
.st-dim{color:#777}
table{border-collapse:collapse;width:100%;table-layout:auto}
th,td{text-align:left;vertical-align:top;padding:2px 10px 2px 0;border-bottom:1px solid #eee;white-space:nowrap}
td.wrap{white-space:pre-wrap;word-break:break-word;min-width:18em}
th{color:#666;font-weight:600}
tr.group td{color:#666;padding-top:6px;border-bottom:none}
tr.none td{color:#999}
.dim{color:#777}
.num{text-align:right;white-space:nowrap}
.ok{color:#1a7f37}
.warn{color:#9a6700}
.bad{color:#c62828}
.bad.head{background:#fde7e7}
.warn.head{background:#fff5d6}
#head{display:flex;flex-wrap:wrap;gap:4px 18px;padding:6px 8px;border:1px solid #ddd;background:#f2f2f2}
#head.bad{border-color:#c62828}
#head.warn{border-color:#9a6700}
#head span b{font-weight:600}
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
      var c = cells[i]; var k = ''; var v = c;
      if (c && typeof c === 'object') {
        if (c.n !== undefined) { k = 'num'; v = c.n; }
        else if (c.w !== undefined) { k = 'wrap'; v = c.w; }
      }
      h += '<td' + (k ? ' class="' + k + '"' : '') + '>' + v + '</td>';
    }
    return h + '</tr>';
  }

  function renderHead(s, state, stateClass, ageSec) {
    var w = s.writer || {};
    var head = el('head');
    head.className = stateClass;
    head.innerHTML =
      '<span><b>' + esc(s.host) + '</b> ' + esc(s.repoRoot) + '</span>' +
      '<span>snapshot <b>' + esc(s.takenAtLocal) + '</b> age <b>' + Math.round(ageSec) + 's</b> <span class="state ' + stateClass + '">' + esc(state) + '</span></span>' +
      '<span>collected in <b>' + esc(s.durationMs) + ' ms</b>, refresh every <b>' + INTERVAL + 's</b></span>' +
      '<span>writer pid <b>' + esc(w.pid || '?') + '</b> ' + esc(w.state || '?') + ', tick ' + esc(w.tick === undefined ? '?' : w.tick) + ', since ' + local(w.startedAtUtc) + '</span>' +
      (s.chatError ? '<span class="bad">chat: ' + esc(s.chatError) + '</span>' : '');
  }

  function render(s) {
    var i, r, rows;

    rows = [];
    (s.children || []).forEach(function (c) {
      var q = c.quietMinutes; var qc = q === null || q === undefined ? 'dim' : (q < 5 ? 'ok' : (q < 15 ? 'warn' : 'bad'));
      var lw = c.lastWriteMinutes === null || c.lastWriteMinutes === undefined ? 'nothing on disk yet'
        : (c.writeBeforeStart ? 'nothing since this run started; newest ' + esc(c.lastWritePath) : mins(c.lastWriteMinutes) + ' ago  ' + esc(c.lastWritePath));
      rows.push(tr(['<span class="big">' + esc(c.pid) + '</span>', id(c.ticket), esc(c.model), { n: mins(c.ageMinutes) }, cls(qc, mins(q) + (q >= 15 ? ' quiet' : '')), { w: lw }]));
    });
    table('children', ['pid', 'ticket', 'model', '#age', 'quiet', 'last write'], rows, 'no headless claude child is running');

    rows = [];
    (s.leases || []).forEach(function (l) {
      var lv = l.liveness === 'foreign-stale' ? cls('bad', 'stale') : (l.liveness === 'unknown' ? cls('warn', 'unknown') : cls('ok', 'live'));
      rows.push(tr([id(l.id), name(l.name), lv, { n: mins(l.ageMinutes) }, { n: mins(l.lastSeenMinutes) }, { w: esc(l.reason) }, esc(l.host) + ' pid ' + esc(l.pid), '<span class="dim">' + esc(l.sessionId) + '</span>']));
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
    (s.locks || []).forEach(function (k) {
      var q = k.queue || [];
      var state, klass;
      if (k.held) { state = 'HELD'; klass = 'warn'; }
      else if (q.length) { state = 'free, ' + q.length + ' queued'; klass = 'warn'; }
      else { state = 'free'; klass = 'ok'; }
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
      rows.push(tr([name(a.name), cls(a.silent ? 'warn' : 'ok', a.silent ? 'SILENT' : 'live'), { n: mins(a.ageMinutes) }, esc(a.runtime) + '/' + esc(a.model) + (a.instance && a.instance !== '-' ? ' ' + esc(a.instance) : ''), id(a.lease), { w: where }, bold(a.lastKind) + (a.lastTicket ? ' ' + id(a.lastTicket) : ''), { w: esc(a.lastNote) }]));
    });
    var w = s.windows || {};
    el('agents-note').textContent = (s.agents || []).length + ' agents in the ' + (w.retentionMinutes || '?') + ' min window, SILENT after ' + (w.silentMinutes || '?') + ' min';
    table('agents', ['agent', 'state', '#last msg', 'runtime/model', 'lease', 'phase', 'last kind', 'last note'], rows, 'nobody has written');

    rows = [];
    var nu = s.nextUp || {};
    (nu.rows || []).forEach(function (q) {
      if (q.kind === 'group') { rows.push(tr([{ w: '<span class="dim">' + esc(q.text) + '</span>' }, '', '', '', ''], 'group')); return; }
      var st = statusCls(q.status);
      var tk = q.leased ? cls('warn', 'taken ' + q.taken) : cls('ok', 'free');
      rows.push(tr([esc(q.rel), id(q.id) + ' <span class="dim">' + esc(q.slug) + '</span>', st, esc(q.changed), tk]));
    });
    el('nextup-note').textContent = nu.package ? 'package ' + nu.package + ', ' + (nu.rows || []).filter(function (x) { return x.kind === 'row'; }).length + ' of ' + (nu.totalInPackage || 0) + ' rows in file order (PLAN/RELEASE_QUEUE.md)' : 'no current package';
    table('nextup', ['rel', 'ticket', 'status', 'changed', 'lease'], rows, 'queue file not found');

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
    (s.instances || []).forEach(function (inst) {
      rows.push(tr([{ w: '<span class="dim">instance ' + esc(inst.instance) + ': ' + esc(inst.recorded) + ' run, ' + esc(inst.moved) + ' moved, ' + esc(inst.stayed) + ' stayed put</span>' }, '', '', '', '', '', ''], 'group'));
      (inst.rows || []).forEach(function (r) {
        var k = r.moved ? 'ok' : (r.outcome !== 'ok' ? 'bad' : 'warn');
        rows.push(tr([id(r.id), esc(r.statusBefore) + ' -> ' + statusCls(r.statusAfter), cls(k, r.moved ? 'moved' : 'stayed'), cls(r.outcome === 'ok' ? 'ok' : 'bad', r.outcome), { n: num(r.minutes) + ' min' }, esc(r.model), local(r.finishedAt)]));
      });
    });
    table('finished', ['ticket', 'status', 'moved', 'outcome', '#took', 'model', 'finished'], rows, 'no run journal yet');

    rows = [];
    (s.stop || []).forEach(function (f) { rows.push(tr([cls('warn', f.name), 'requested ' + mins(f.requestedMinutes) + ' ago', { w: (s.children || []).length ? 'waiting for the running ticket(s) to end' : 'nothing is running - the next start clears the flag' }], 'stop')); });
    table('stop', ['flag', 'age', 'meaning'], rows, 'no stop requested');
  }

  function tickHeader() {
    if (!snap) { return; }
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
    if (s.takenAtUtc !== lastTaken) { lastTaken = s.takenAtUtc; render(s); }
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
<h2>locks <small>who is building or editing, and who waits</small></h2>
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
