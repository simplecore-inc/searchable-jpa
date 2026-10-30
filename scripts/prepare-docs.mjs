import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { versions } = require('../docs/assets/version-selector.js');
const folder = /^(?:snapshot|\d+\.\d+\.\d+(?:-SNAPSHOT)?)$/;

export function prepareDocs({ previous, built, output, version }) {
  if (!/^(?:snapshot|\d+\.\d+\.\d+)$/.test(version)) throw new Error('Invalid documentation version');
  for (const input of [previous, built]) {
    const relative = path.relative(path.resolve(input), path.resolve(output));
    if (!relative || (!relative.startsWith('..') && !path.isAbsolute(relative))) throw new Error('Output must be outside source directories');
  }
  if (!fs.existsSync(path.join(built, 'index.html'))) throw new Error('Missing built documentation');
  fs.mkdirSync(output, { recursive: true });
  if (fs.existsSync(previous)) {
    for (const entry of fs.readdirSync(previous, { withFileTypes: true })) {
      if (entry.isDirectory() && folder.test(entry.name) && fs.existsSync(path.join(previous, entry.name, 'index.html'))) {
        fs.cpSync(path.join(previous, entry.name), path.join(output, entry.name), { recursive: true });
      }
    }
  }
  const destination = path.join(output, version);
  fs.rmSync(destination, { recursive: true, force: true });
  fs.cpSync(built, destination, { recursive: true });

  const directories = fs.readdirSync(output, { withFileTypes: true })
    .filter(entry => entry.isDirectory() && folder.test(entry.name) && fs.existsSync(path.join(output, entry.name, 'index.html')))
    .map(entry => entry.name);
  const sorted = versions({ releases: directories, snapshot: directories.includes('snapshot') ? 'snapshot' : null });
  const manifest = { releases: sorted.filter(name => name !== 'snapshot'), snapshot: sorted.includes('snapshot') ? 'snapshot' : null };
  // Upgrade navigation in archived pages as well, while preserving their versioned prose.
  for (const directory of directories) {
    const base = path.join(output, directory);
    fs.mkdirSync(path.join(base, 'assets'), { recursive: true });
    for (const file of ['version-selector.js', 'version-selector.css']) {
      fs.copyFileSync(new URL('../docs/assets/' + file, import.meta.url), path.join(base, 'assets', file));
    }
    const index = path.join(base, 'index.html');
    let html = fs.readFileSync(index, 'utf8');
    html = html.replace(/\s*<!-- Version selector -->\s*<script\b[^>]*>[\s\S]*?<\/script>/g, '');
    html = html.replace(/\s*<link\b[^>]*href=["']assets\/version-selector\.css["'][^>]*>/g, '');
    html = html.replace('</head>', '<link rel="stylesheet" href="assets/version-selector.css">\n</head>');
    const script = '<!-- Version selector -->\n<script src="assets/version-selector.js"></script>\n';
    if (html.includes('<!-- Docsify core -->')) html = html.replace('<!-- Docsify core -->', script + '<!-- Docsify core -->');
    else html = html.replace(/<script\b[^>]*src=["'][^"']*\/docsify(?:@[^/"']*)?(?:\/[^"']*)?["'][^>]*>/, script + '$&');
    if (!html.includes('src="assets/version-selector.js"')) throw new Error('Cannot locate Docsify entry in ' + index);
    fs.writeFileSync(index, html);
  }
  fs.writeFileSync(path.join(output, 'versions.json'), JSON.stringify(manifest, null, 2) + '\n');
  const preferred = manifest.releases[0] || manifest.snapshot;
  fs.writeFileSync(path.join(output, 'index.html'), `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>Searchable JPA documentation</title>
<meta http-equiv="refresh" content="0; url=./${preferred}/">
<script>location.replace('./${preferred}/' + location.search + location.hash);</script></head>
<body><a href="./${preferred}/">Open documentation</a></body></html>\n`);
  fs.writeFileSync(path.join(output, '.nojekyll'), '');
  return manifest;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [previous, built, output, version] = process.argv.slice(2);
  if (![previous, built, output, version].every(Boolean)) throw new Error('Usage: node scripts/prepare-docs.mjs <previous> <built> <output> <snapshot|x.y.z>');
  console.log(JSON.stringify(prepareDocs({ previous, built, output, version }), null, 2));
}
