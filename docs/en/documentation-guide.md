# Documentation Guide

## Sources and local preview

English and Korean pages are paired under `docs/en/` and `docs/ko/`. Each locale has a sidebar and `_images/` directory. `docs/index.html` configures Docsify; `docs/assets/` contains shared version navigation. Run commands from the repository root:

```bash
./scripts/build-docs.sh
python3 -m http.server 3000 --directory build-docs
```

Open `http://localhost:3000/#/en/` or `http://localhost:3000/#/ko/`. Serve HTTP rather than opening `file://` URLs. The build copies both languages and shared assets, substitutes the cover's version from `build.gradle` (or `DOCS_VERSION`) and converts source links for Docsify. `build-docs/` is disposable output.

Java 17 and the checked-in Gradle wrapper are needed for library checks. Documentation checks require Node.js 22 or later, npm and the browser runtime used by e2e. Install the tooling and run:

```bash
npm ci
npm run test:docs
npm run test:docs:e2e
```

The e2e config starts a fixture server on port 8764, with root and `/searchable-jpa/` deployment paths, snapshots, two releases and an unversioned preview. The tests cover language/page/heading preservation, numeric version ordering, missing pages, failed requests and retry. The server stops when the test run ends. Keep that port available.

## Writing and review

Update both languages together and link new pages from both sidebars. Use `./basic-usage.md` for a neighbouring source page and `./_images/name.svg` for a figure. Use actual public API names, imported Java types and executable dependency coordinates. Mark partial snippets clearly and explain any application-specific classes.

A document's version describes its tagged source. Historical changelog entries describe historical behavior and must not be rewritten as current behavior. Configuration tables must match the starter property class; operator tables must match `SearchOperator`; examples must match parsing and serialization tests.

Before committing, build the docs, run navigation checks, inspect both languages in a browser, verify local links and compare diagrams with the described query path. Search is scoped by documentation version and locale. Code blocks use Prism; SVG figures and Mermaid are supported. GitHub alert markers are ordinary blockquotes unless an alert renderer is added.

## Versioned deployment

The `Deploy Documentation` GitHub Actions workflow publishes `gh-pages`. A documentation change on `master` refreshes the `snapshot/` folder. Manual dispatch with a stable version such as `2.0.1-SNAPSHOT` checks out `v2.0.1-SNAPSHOT` and builds its content. The workflow obtains current navigation tooling separately so archived text can retain its original content while sharing repaired navigation.

`scripts/prepare-docs.mjs` combines the previous site with the new build. It replaces the selected version directory, preserves other releases and the snapshot, updates shared navigation assets in archived HTML and generates `versions.json`. Releases use numeric descending order. Publishing a release does not delete the development docs.

The root page redirects to the highest available stable release, or the snapshot when there is no stable release. Query and hash state are retained. The selector operates on sibling version directories, preserves the requested language/page/heading and checks page availability before navigation. A missing page falls back to the language home page, then English. A failed network request leaves the current page open.

Published site: [Searchable JPA documentation](https://simplecore-inc.github.io/searchable-jpa/). GitHub Pages must serve the `gh-pages` branch root. Repository workflow permissions must allow the deployment workflow to write that branch.

## Troubleshooting

- A local unversioned preview intentionally omits the version selector. Test it through `npm run test:docs:e2e` or the fixture server (`node scripts/docs-test-server.mjs`).
- If the version list fails, use the visible retry button and inspect the parent `versions.json` response. Check project subpaths as well as domain-root paths.
- If content or search appears stale, verify the selected version and language, reload the page and inspect CDN/plugin requests. Docsify 5 plugins use `dist/plugins/` paths.
- If a figure is absent, check filename case and whether the SVG exists in both locale directories. Build output is regenerated, so edit `docs/`, not `build-docs/`.
- If a manual release deployment fails, verify that its exact `vX.Y.Z` tag exists and that the version input contains only three numeric components.
