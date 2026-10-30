import fs from 'node:fs';
const version = process.argv[2];
if (!/^\d+\.\d+\.\d+$/.test(version || '')) throw new Error('Expected stable version');
const changelog = fs.readFileSync('docs/en/changelog.md', 'utf8');
const marker = `## ${version}\n`;
const start = changelog.indexOf(marker);
if (start < 0) throw new Error(`Missing changelog for ${version}`);
const content = changelog.slice(start + marker.length).split('\n## ')[0].trim();
console.log(content.replaceAll(/\]\(\.\/([^)]*)\)/g, `](https://simplecore-inc.github.io/searchable-jpa/${version}/#/en/$1)`));
console.log('\nValidated with Java 17, the normal regression suite, performance tests and documentation navigation checks. Published artifacts: core, OpenAPI and Spring Boot starter.');
if (version.startsWith('1.')) {
  console.log('\n### Boot 3 dependency limitation\n\nThis release retains Spring Framework 6.2.19 for Boot 3 compatibility. CVE-2026-47884 (XsltView) and CVE-2026-47890 (SSE view fragments) remain in that public baseline. Spring provides the 6.2.20 fixes through Enterprise Support; public 7.0.9+ fixes require a Boot 4 migration. Searchable JPA does not configure those endpoints. Review your application against the [security documentation](https://simplecore-inc.github.io/searchable-jpa/' + version + '/#/en/security).');
}
