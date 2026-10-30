import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import http from 'node:http';
import { execFileSync } from 'node:child_process';
import { prepareDocs } from './prepare-docs.mjs';

execFileSync('bash', ['scripts/build-docs.sh'], { stdio: 'ignore' });
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'searchable-docs-e2e-'));
const site = path.join(root, 'site');
prepareDocs({ previous: path.join(root, 'absent'), built: 'build-docs', output: site, version: 'snapshot' });
for (const version of ['1.9.0', '1.10.0']) {
  fs.cpSync('build-docs', path.join(site, version), { recursive: true });
}
for (const locale of ['en', 'ko']) fs.rmSync(path.join(site, '1.9.0', locale, 'advanced-features.md'));
fs.writeFileSync(path.join(site, 'versions.json'), JSON.stringify({ releases: ['1.10.0', '1.9.0'], snapshot: 'snapshot' }));
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.md': 'text/markdown', '.json': 'application/json', '.svg': 'image/svg+xml' };
const server = http.createServer((request, response) => {
  try {
    let pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const preview = pathname.startsWith('/preview/');
    pathname = pathname.replace(/^\/(?:searchable-jpa|preview)\//, '/');
    const base = preview ? path.resolve('build-docs') : site;
    let file = path.resolve(base, '.' + pathname);
    if (!file.startsWith(base + path.sep) && file !== base) throw new Error('Invalid path');
    if (fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
    response.writeHead(200, { 'Content-Type': (types[path.extname(file)] || 'application/octet-stream') + '; charset=utf-8' });
    response.end(fs.readFileSync(file));
  } catch (_) { response.writeHead(404); response.end('Not found'); }
});
server.listen(Number(process.env.PORT || 8764), '127.0.0.1');
function close() { server.close(); fs.rmSync(root, { recursive: true, force: true }); }
process.on('SIGTERM', () => { close(); process.exit(0); });
process.on('SIGINT', () => { close(); process.exit(0); });
