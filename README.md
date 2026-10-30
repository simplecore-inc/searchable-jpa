# Searchable JPA

DTO-controlled filtering, sorting, pagination and time counts for Spring Data JPA, with Spring Boot configuration and SpringDoc integration.

[English documentation](https://simplecore-inc.github.io/searchable-jpa/#/en/) · [한국어 문서](https://simplecore-inc.github.io/searchable-jpa/#/ko/) · [Changes](docs/en/changelog.md)

## Compatibility

| Library | Spring Boot | Branch |
| --- | --- | --- |
| 2.x | 4.1.x and later 4.x | `master` |
| 1.x | 3.2.x–3.5.x | `spring-boot-3x` |
| 0.1.x | 2.7.x | `spring-boot-2x` (unmaintained) |

This tree builds **1.1.4-SNAPSHOT**, tested with Java 17, Spring Boot 3.5.16 and Hibernate 6.6.59.Final. Keep all three library modules on the same version. Boot 3 applications should use the maintained 1.x line; moving the application to Boot 4 is a separate migration.

## Install and use

The starter includes core, OpenAPI support and automatic configuration. For local development:

```bash
./gradlew check performanceTest publishToMavenLocal
```

In the consuming project's `build.gradle`:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:1.1.4-SNAPSHOT'
}
```

Published versions are available from GitHub Packages; see [installation and credentials](docs/en/installation.md) ([한국어](docs/ko/installation.md)). The application supplies its datasource, entities, repositories and authorization rules.

Use `@SearchableField` on a DTO, a repository implementing `JpaRepository` and `JpaSpecificationExecutor`, and `DefaultSearchableService` or `SearchableServiceDelegate`. The [complete example](docs/en/basic-usage.md) ([한국어](docs/ko/basic-usage.md)) connects all components.

```http
GET /api/posts?title.contains=Spring&sort=createdAt.desc&page=0&size=20
```

The 18 built-in operators support comparisons, literal string patterns, ranges, membership and null checks. Property names and values are validated at runtime. Existing Jackson 2 APIs remain available, with additional Jackson 3 support for typed JSON bodies.

## Execution and configuration

Simple single-key searches can query entities directly. Collection joins and composite keys use ID selection followed by hydration; independent collections are fetched separately. Count queries avoid duplicate root entities, and provably short pages can skip COUNT. Search-based writes use chunks of 200 while preserving entity callbacks and caller transactions.

```yaml
searchable:
  default-page-size: 20
  max-page-size: 1000
  hibernate:
    auto-optimization: true
    auto-fetch-collections: true
```

See [all settings](docs/en/auto-configuration.md), [query strategy](docs/en/two-phase-query-optimization.md), [advanced features](docs/en/advanced-features.md), [API reference](docs/en/api-reference.md) and [troubleshooting](docs/en/faq.md). Indexes, data distribution and association size still determine database cost. Export requests need application-level limits.

## Build and verify

Use Java 17 or a supported newer JDK and the checked-in Gradle 9.8.2 wrapper:

```bash
./gradlew check performanceTest
npm ci
npm run test:docs
npm run test:docs:e2e
./scripts/build-docs.sh
```

Test CSV fixtures are generated deterministically during the build; a fresh checkout needs no manually generated dataset. Documentation checks require Node.js 22+ and the e2e browser runtime. See the [documentation guide](docs/en/documentation-guide.md) ([한국어](docs/ko/documentation-guide.md)) for local preview and versioned deployment.

Patch changes must reach both maintained branches. Use `Backport: spring-boot-3x` / `Forwardport: master` commit trailers and `git cherry-pick -x`; run `scripts/branch-sync/check.sh --local` before pushing. The release workflow checks the build, publishes packages, creates a GitHub release and advances the development version.

## License

[SimpleCORE License 1.0](LICENSE) applies. See the full terms before distributing or hosting the software. Licensing inquiries: [license@simplecore.kr](mailto:license@simplecore.kr).
