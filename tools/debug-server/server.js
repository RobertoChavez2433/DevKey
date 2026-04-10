// WHY: Local log collection server for systematic debugging Deep mode.
// Receives structured logs from DevKeyLogger via HTTP, queryable by
// category and hypothesis tag. Run with: node tools/debug-server/server.js
const http = require('http');
// WHY: Phase 2.2 extension adds wave-gating + ADB dispatch endpoints.
const waveGate = require('./wave-gate');
const adb = require('./adb-exec');
const PORT = parseInt(process.env.PORT || '3948', 10);
const MAX_ENTRIES = 30000;

let logs = [];

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);

  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Content-Type', 'application/json');

  // POST /log — receive a log entry
  if (req.method === 'POST' && url.pathname === '/log') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      try {
        const entry = JSON.parse(body);
        entry.ts = new Date().toISOString().slice(11, 23);
        logs.push(entry);
        // CRITICAL: In-place splice preserves array identity so long-polling
        // `waitFor` closures in wave-gate.js continue to see new appends.
        // Reassigning `logs = logs.slice(...)` would break the closure's reference.
        if (logs.length > MAX_ENTRIES) logs.splice(0, logs.length - MAX_ENTRIES);
        res.writeHead(200);
        res.end('{"ok":true}');
      } catch (e) {
        res.writeHead(400);
        res.end(JSON.stringify({error: e.message}));
      }
    });
    return;
  }

  // GET /health
  if (url.pathname === '/health') {
    const mem = process.memoryUsage();
    res.end(JSON.stringify({
      status: 'ok',
      entries: logs.length,
      maxEntries: MAX_ENTRIES,
      memoryMB: Math.round(mem.heapUsed / 1024 / 1024),
      uptimeSeconds: Math.round(process.uptime()),
    }));
    return;
  }

  // GET /logs?last=N&category=X&hypothesis=H001
  if (url.pathname === '/logs') {
    let filtered = logs;
    const cat = url.searchParams.get('category');
    const hyp = url.searchParams.get('hypothesis');
    const last = parseInt(url.searchParams.get('last') || '50');
    if (cat) filtered = filtered.filter(e => e.category === cat);
    if (hyp) filtered = filtered.filter(e => e.hypothesis === hyp);
    filtered = filtered.slice(-last);
    res.end(filtered.map(e => JSON.stringify(e)).join('\n'));
    return;
  }

  // GET /categories
  if (url.pathname === '/categories') {
    const counts = {};
    logs.forEach(e => { counts[e.category] = (counts[e.category] || 0) + 1; });
    res.end(JSON.stringify(counts));
    return;
  }

  // POST /clear
  if (req.method === 'POST' && url.pathname === '/clear') {
    const count = logs.length;
    // CRITICAL: Must preserve array identity — DO NOT reassign `logs = []`.
    // waveGate.waitFor captures `logs` by reference, and /log pushes to the
    // module-level `logs` binding. Reassigning the binding would leave the
    // waitFor closure reading from a dead array that never gains new entries.
    // In-place truncation keeps the reference stable for all code paths.
    logs.length = 0;
    res.end(JSON.stringify({ cleared: count }));
    return;
  }

  // ============================================================
  // Phase 2 extension — wave-gating + ADB dispatch endpoints.
  // ============================================================

  // GET /wait?category=DevKey/VOX&event=state_transition&match=<urlencoded-json>&timeout=5000
  //   Long-polls until a matching log entry appears, or timeout fires.
  if (req.method === 'GET' && url.pathname === '/wait') {
    const category = url.searchParams.get('category');
    const event = url.searchParams.get('event');
    const matchRaw = url.searchParams.get('match');
    const timeoutMs = parseInt(url.searchParams.get('timeout') || '5000');
    if (!category) {
      res.writeHead(400);
      res.end(JSON.stringify({ error: 'category required' }));
      return;
    }
    let matchData = null;
    if (matchRaw) {
      try { matchData = JSON.parse(matchRaw); }
      catch (e) {
        res.writeHead(400);
        res.end(JSON.stringify({ error: 'match must be valid JSON' }));
        return;
      }
    }
    waveGate.waitFor(logs, category, event, matchData, timeoutMs)
      .then(entry => {
        res.writeHead(200);
        res.end(JSON.stringify({ matched: true, entry }));
      })
      .catch(err => {
        res.writeHead(408); // Request Timeout
        res.end(JSON.stringify({ matched: false, error: err.message }));
      });
    return;
  }

  // POST /adb/tap?x=540&y=1775
  if (req.method === 'POST' && url.pathname === '/adb/tap') {
    const x = parseInt(url.searchParams.get('x'));
    const y = parseInt(url.searchParams.get('y'));
    if (Number.isNaN(x) || Number.isNaN(y)) {
      res.writeHead(400);
      res.end(JSON.stringify({ error: 'x and y required' }));
      return;
    }
    adb.tap(x, y).then(result => {
      res.writeHead(result.ok ? 200 : 500);
      res.end(JSON.stringify(result));
    });
    return;
  }

  // POST /adb/swipe?x1=X&y1=Y&x2=X&y2=Y&duration=300
  if (req.method === 'POST' && url.pathname === '/adb/swipe') {
    const x1 = parseInt(url.searchParams.get('x1'));
    const y1 = parseInt(url.searchParams.get('y1'));
    const x2 = parseInt(url.searchParams.get('x2'));
    const y2 = parseInt(url.searchParams.get('y2'));
    const duration = parseInt(url.searchParams.get('duration') || '300');
    if ([x1, y1, x2, y2].some(Number.isNaN)) {
      res.writeHead(400);
      res.end(JSON.stringify({ error: 'x1,y1,x2,y2 required' }));
      return;
    }
    adb.swipe(x1, y1, x2, y2, duration).then(result => {
      res.writeHead(result.ok ? 200 : 500);
      res.end(JSON.stringify(result));
    });
    return;
  }

  // POST /adb/broadcast (JSON body: {action, extras})
  if (req.method === 'POST' && url.pathname === '/adb/broadcast') {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      let parsed;
      try { parsed = JSON.parse(body || '{}'); }
      catch (e) {
        res.writeHead(400);
        res.end(JSON.stringify({ error: 'invalid JSON' }));
        return;
      }
      if (!parsed.action) {
        res.writeHead(400);
        res.end(JSON.stringify({ error: 'action required' }));
        return;
      }
      adb.broadcast(parsed.action, parsed.extras || {}).then(result => {
        res.writeHead(result.ok ? 200 : 500);
        res.end(JSON.stringify(result));
      });
    });
    return;
  }

  // GET /adb/logcat?tags=DevKeyPress,DevKeyMode,DevKeyMap,DevKeyBridge
  //   Dual-source observation for legacy Log.d tags that do NOT go through DevKeyLogger.
  if (req.method === 'GET' && url.pathname === '/adb/logcat') {
    const tagsRaw = url.searchParams.get('tags') || 'DevKeyPress';
    const tags = tagsRaw.split(',').map(t => t.trim()).filter(Boolean);
    adb.dumpLogcat(tags).then(result => {
      res.writeHead(result.ok ? 200 : 500);
      res.end(JSON.stringify(result));
    });
    return;
  }

  // POST /adb/logcat/clear
  if (req.method === 'POST' && url.pathname === '/adb/logcat/clear') {
    adb.clearLogcat().then(result => {
      res.writeHead(result.ok ? 200 : 500);
      res.end(JSON.stringify(result));
    });
    return;
  }

  // POST /wave/start?id=typing
  if (req.method === 'POST' && url.pathname === '/wave/start') {
    const waveId = url.searchParams.get('id');
    if (!waveId) {
      res.writeHead(400);
      res.end(JSON.stringify({ error: 'id required' }));
      return;
    }
    const wave = waveGate.startWave(logs, waveId);
    res.writeHead(200);
    res.end(JSON.stringify(wave));
    return;
  }

  // POST /wave/finish?id=typing&status=pass
  if (req.method === 'POST' && url.pathname === '/wave/finish') {
    const waveId = url.searchParams.get('id');
    const status = url.searchParams.get('status') || 'pass';
    if (!waveId) {
      res.writeHead(400);
      res.end(JSON.stringify({ error: 'id required' }));
      return;
    }
    waveGate.finishWave(waveId, status);
    res.writeHead(200);
    res.end(JSON.stringify({ ok: true, id: waveId, status }));
    return;
  }

  // GET /wave/status
  if (url.pathname === '/wave/status') {
    res.writeHead(200);
    res.end(JSON.stringify(waveGate.allWaves()));
    return;
  }

  res.writeHead(404);
  res.end('{"error":"not found"}');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`DevKey driver server listening on http://127.0.0.1:${PORT}`);
  console.log(`  Log:      POST /log  GET /logs  GET /health  GET /categories  POST /clear`);
  console.log(`  Wait:     GET /wait?category=X&event=Y&match=<json>&timeout=<ms>`);
  console.log(`  ADB:      POST /adb/tap  POST /adb/swipe  POST /adb/broadcast`);
  console.log(`            GET /adb/logcat?tags=...  POST /adb/logcat/clear`);
  console.log(`  Waves:    POST /wave/start?id=  POST /wave/finish?id=&status=  GET /wave/status`);
  console.log(`  ADB serial: ${process.env.ADB_SERIAL || '(default device)'}`);
});
