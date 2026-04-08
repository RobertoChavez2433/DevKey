// WHY: Local log collection server for systematic debugging Deep mode.
// Receives structured logs from DevKeyLogger via HTTP, queryable by
// category and hypothesis tag. Run with: node tools/debug-server/server.js
const http = require('http');
const PORT = 3947;
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
        if (logs.length > MAX_ENTRIES) logs = logs.slice(-MAX_ENTRIES);
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
    logs = [];
    res.end(JSON.stringify({ cleared: count }));
    return;
  }

  res.writeHead(404);
  res.end('{"error":"not found"}');
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Debug server listening on http://127.0.0.1:${PORT}`);
});
