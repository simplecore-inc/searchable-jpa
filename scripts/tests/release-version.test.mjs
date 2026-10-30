import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

for (const stable of ['2.0.0', '1.1.3']) {
  test(`release ${stable} keeps historical notes and stable deployment examples`, t => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'searchable-version-'));
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const snapshot = stable + '-SNAPSHOT';
    const next = stable.split('.'); next[2] = String(Number(next[2]) + 1);
    const development = next.join('.') + '-SNAPSHOT';
    fs.writeFileSync(path.join(root, 'build.gradle'), `allprojects {\n version = '${snapshot}'\n}\n`);
    fs.writeFileSync(path.join(root, 'README.md'), `Use ${snapshot}`);
    for (const locale of ['en', 'ko']) {
      const dir = path.join(root, 'docs', locale); fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(path.join(dir, 'installation.md'), `Use ${snapshot}`);
      fs.writeFileSync(path.join(dir, 'documentation-guide.md'), `Deploy stable ${stable}`);
      fs.writeFileSync(path.join(dir, 'changelog.md'), `# Changes\n\n## ${snapshot} (${locale === 'ko' ? '미출시' : 'Unreleased'})\n\nFixed behavior.\n`);
    }
    const script = path.resolve('scripts/set-project-version.mjs');
    execFileSync(process.execPath, [script, stable], { cwd: root });
    execFileSync(process.execPath, [script, development], { cwd: root });
    assert.match(fs.readFileSync(path.join(root, 'README.md'), 'utf8'), new RegExp(development.replaceAll('.', '\\.')));
    for (const locale of ['en', 'ko']) {
      const dir = path.join(root, 'docs', locale);
      assert.equal(fs.readFileSync(path.join(dir, 'documentation-guide.md'), 'utf8'), `Deploy stable ${stable}`);
      assert.match(fs.readFileSync(path.join(dir, 'changelog.md'), 'utf8'), new RegExp(`## ${stable.replaceAll('.', '\\.')}\\n`));
      assert.equal(fs.readFileSync(path.join(dir, 'installation.md'), 'utf8'), `Use ${development}`);
    }
  });
}
