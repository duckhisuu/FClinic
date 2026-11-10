// Local demo only: expose the existing Auth scope endpoint over verified TLS.
// The HTTP upstream and TLS listener are both restricted to loopback.
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const https = require('node:https');
const runtime = path.resolve(__dirname, '../../.runtime');
const tls = JSON.parse(fs.readFileSync(path.join(runtime, 'demo-scope-tls.private.json'), 'utf8'));
const server = https.createServer({ pfx: fs.readFileSync(tls.keyStore), passphrase: tls.password, minVersion: 'TLSv1.2' }, (req, res) => {
  if (req.method !== 'POST' || req.url !== '/api/v1/auth/scopes') {
    res.writeHead(404).end(); return;
  }
  const upstream = http.request({ hostname: '127.0.0.1', port: 8085, path: req.url, method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', Authorization: req.headers.authorization || '' },
    timeout: 3000 }, reply => {
    res.writeHead(reply.statusCode, { 'Content-Type': reply.headers['content-type'] || 'application/json', 'Cache-Control': 'no-store' });
    reply.pipe(res);
  });
  upstream.on('timeout', () => upstream.destroy());
  upstream.on('error', () => { if (!res.headersSent) res.writeHead(503); res.end(); });
  req.on('aborted', () => upstream.destroy());
  req.pipe(upstream);
});
server.requestTimeout = 5000;
server.headersTimeout = 5000;
server.listen(8445, '127.0.0.1', () => console.log('Demo scope TLS endpoint listening on 127.0.0.1:8445'));
