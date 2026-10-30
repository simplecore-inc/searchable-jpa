import fs from 'node:fs';
import path from 'node:path';

// Keep the tagged source, installation examples and subsequent snapshot coherent.
const next = process.argv[2];
if (!/^\d+\.\d+\.\d+(?:-SNAPSHOT)?$/.test(next || '')) throw new Error('Expected x.y.z[-SNAPSHOT]');
const build = fs.readFileSync('build.gradle', 'utf8');
const current = /^\s*version = '([^']+)'/m.exec(build)?.[1];
if (!current) throw new Error('Project version not found');
fs.writeFileSync('build.gradle', build.replace(`version = '${current}'`, `version = '${next}'`));
const pages = ['README.md'];
for (const locale of ['en', 'ko']) {
  for (const name of fs.readdirSync(`docs/${locale}`)) {
    if (name.endsWith('.md') && !['changelog.md', 'documentation-guide.md'].includes(name)) pages.push(path.join('docs', locale, name));
  }
  if (!next.endsWith('-SNAPSHOT')) {
    const file = `docs/${locale}/changelog.md`;
    const text = fs.readFileSync(file, 'utf8');
    fs.writeFileSync(file, text.replace(`## ${current} (${locale === 'ko' ? '미출시' : 'Unreleased'})`, `## ${next}`));
  }
}
for (const file of pages) fs.writeFileSync(file, fs.readFileSync(file, 'utf8').replaceAll(current, next));
console.log(`${current} -> ${next}`);
