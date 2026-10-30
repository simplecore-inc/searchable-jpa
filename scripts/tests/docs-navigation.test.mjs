import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import { prepareDocs } from '../prepare-docs.mjs';
const require = createRequire(import.meta.url);
const navigation = require('../../docs/assets/version-selector.js');

test('version context supports project sites, root sites and explicit index pages', () => {
  for (const url of ['https://example.test/searchable-jpa/1.1.1/', 'https://example.test/searchable-jpa/1.1.1/index.html']) {
    const result = navigation.context(url);
    assert.equal(result.current, '1.1.1');
    assert.equal(result.base.href, 'https://example.test/searchable-jpa/');
  }
  assert.equal(navigation.context('https://example.test/snapshot/').current, 'snapshot');
  assert.equal(navigation.context('http://localhost:3000/'), null);
  assert.equal(navigation.context('https://example.test/searchable-jpa/'), null);
});

test('manifest validation excludes traversal, prereleases and duplicates and sorts numerically', () => {
  assert.deepEqual(navigation.versions({ releases: ['1.9.0', '1.10.0', '../evil', '1.10.0', '2.0.0-RC1', 42], snapshot: 'snapshot' }),
    ['snapshot', '1.10.0', '1.9.0']);
  assert.deepEqual(navigation.versions({ releases: [], snapshot: '//evil.test' }), []);
  assert.throws(() => navigation.versions({ releases: '1.0.0' }));
});

test('navigation preserves language, document and heading within the deployment prefix', () => {
  const base = new URL('https://example.test/searchable-jpa/');
  assert.equal(navigation.target(base, '1.10.0', '#/ko/basic-usage.md?id=search').href,
    'https://example.test/searchable-jpa/1.10.0/#/ko/basic-usage.md?id=search');
  assert.equal(navigation.route('#/ko/').file, 'ko/README.md');
  assert.equal(navigation.route('#/ko/../../secret').file, 'en/README.md');
  assert.throws(() => navigation.target(base, '../evil', '#/ko/'));
});

test('release deployment retains snapshot, replaces same version and updates archived navigation', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'searchable-docs-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const previous = path.join(root, 'previous'), built = path.join(root, 'built'), output = path.join(root, 'output');
  const html = '<html><head></head><body><!-- Docsify core --><script src="https://cdn.test/docsify@4"></script><!-- Version selector --><script>oldSelector();</script></body></html>';
  for (const directory of [path.join(previous, 'snapshot'), path.join(previous, '1.9.0'), path.join(previous, '1.10.0'), built]) {
    fs.mkdirSync(directory, { recursive: true }); fs.writeFileSync(path.join(directory, 'index.html'), html);
  }
  fs.writeFileSync(path.join(previous, '1.10.0', 'obsolete.md'), 'old');
  fs.writeFileSync(path.join(built, 'new.md'), 'new');
  assert.deepEqual(prepareDocs({ previous, built, output, version: '1.10.0' }), { releases: ['1.10.0', '1.9.0'], snapshot: 'snapshot' });
  assert.ok(fs.existsSync(path.join(output, 'snapshot', 'index.html')));
  assert.ok(!fs.existsSync(path.join(output, '1.10.0', 'obsolete.md')));
  assert.ok(fs.existsSync(path.join(output, '1.10.0', 'new.md')));
  const archived = fs.readFileSync(path.join(output, '1.9.0', 'index.html'), 'utf8');
  assert.ok(!archived.includes('oldSelector'));
  assert.ok(archived.indexOf('assets/version-selector.js') < archived.indexOf('<!-- Docsify core -->'));
  assert.match(fs.readFileSync(path.join(output, 'index.html'), 'utf8'), /location\.hash/);
  assert.throws(() => prepareDocs({ previous, built, output, version: '../outside' }));
});
