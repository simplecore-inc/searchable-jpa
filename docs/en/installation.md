# Installation

This guide describes the `2.0.0-SNAPSHOT` source line. Dependency versions below were checked on 2026-10-30. A version ending in `-SNAPSHOT` is a development build, not a stable release.

## Compatibility

| Searchable JPA line | Spring Boot line | Java packages | Source branch |
| --- | --- | --- | --- |
| 2.x | 4.1.x and later 4.x | `jakarta.*` | `master` |
| 1.x | 3.2.x–3.5.x | `jakarta.*` | `spring-boot-3x` |
| 0.1.x | 2.7.x | `javax.*` | `spring-boot-2x` (unmaintained) |

The current build is tested with Java 17, Spring Boot 4.1.2 and Hibernate ORM 7.4.14.Final. Java 17 is the minimum source and bytecode level. Use one library line consistently across all three modules. A Boot 3 application stays on the 1.x line until the application itself migrates to Boot 4.

Existing 2.x service subclasses, delegate construction, query parameter names and Jackson 2 public method signatures remain available. Jackson 3 supports typed HTTP search bodies. Corrected validation now rejects requests that exceed the documented limits; see [Auto Configuration](./auto-configuration.md).

## Modules

| Artifact | Contents |
| --- | --- |
| `searchable-jpa-core` | Conditions, DTO validation, JPA queries, services and time counts |
| `searchable-jpa-openapi` | `@SearchableParams`, operation customization and examples; includes core |
| `spring-boot-starter-searchable-jpa` | Both modules and Spring Boot auto-configuration |

Use the starter for a Spring MVC application. The application still supplies its datasource, entities, repositories, controllers and access-control rules. Repositories must implement both `JpaRepository` and `JpaSpecificationExecutor`.

## Local installation

Build with the committed Gradle wrapper and Java 17 or later:

```bash
./gradlew check performanceTest publishToMavenLocal
```

This installs all three modules under `~/.m2/repository/dev/simplecore/searchable/<artifact>/2.0.0-SNAPSHOT/`. The command performs no Git tagging or remote publication. Repeating it replaces the local snapshot.

A Gradle consumer can resolve the local snapshot first:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:2.0.0-SNAPSHOT'
}
```

If repository declarations live in `settings.gradle`, add `mavenLocal()` there. Gradle consumers that cached an older snapshot can run their build with `--refresh-dependencies` after installation.

A Maven consumer uses its local repository automatically:

```xml
<dependency>
  <groupId>dev.simplecore.searchable</groupId>
  <artifactId>spring-boot-starter-searchable-jpa</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## GitHub Packages

For a remotely published version, configure `https://maven.pkg.github.com/simplecore-inc/searchable-jpa` and select a version that actually exists in that repository. Keep credentials outside committed files:

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/simplecore-inc/searchable-jpa')
        credentials {
            username = System.getenv('GITHUB_USERNAME')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}
```

The token needs package-read permission. The source build reads `gpr.user` and `gpr.token` from an ignored `gradle.properties`, or `GITHUB_USERNAME` and `GITHUB_TOKEN`. Remote publication uses separate publication credentials; local publication needs none.

## Dependency baseline

| Component | Tested version |
| --- | --- |
| Gradle wrapper | 9.8.2 |
| Spring Boot | 4.1.2 |
| Spring dependency management plugin | 1.1.7 |
| SpringDoc OpenAPI | 3.2.0 |
| Hibernate ORM | 7.4.14.Final |
| Jackson 2 databind / Java time | 2.22.4 |
| Jackson 3 databind | 3.2.4 |
| ModelMapper | 3.2.8 |
| HikariCP | 7.1.0 |
| H2 (tests) | 2.5.252 |
| SQL Server JDBC (test profile) | 13.6.1.jre11 |
| JUnit / Mockito | 6.1.3 / 5.24.0 |

The root `build.gradle` contains the complete managed overrides. Hibernate, Jackson, logging and test dependencies are aligned and tested together. An application's own dependency management can override transitive versions: inspect its resolved runtime classpath when upgrading. Use its appropriate JDBC driver; the SQL Server test driver does not configure an application datasource.

## Verification and database profiles

```bash
./gradlew test
./gradlew performanceTest
./gradlew build
```

Regular tests exclude the `performance` tag; `performanceTest` runs it separately with nonempty data assertions. The default integration tests use H2. SQL Server configuration is a separate test profile and requires a reachable database. Passing the H2 suite does not establish identical plans or timestamp precision on every database.

Continue with [Basic Usage](./basic-usage.md) for complete application snippets and [FAQ](./faq.md) for resolution failures.

See [Security and dependency scope](./security.md) for the Boot 3 advisories that require an enterprise Spring fix or a framework migration.
