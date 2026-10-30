# Security and dependency scope

## Application boundary

Use a DTO as the allowlist for client filters and sorts. Apply tenant and authorization predicates from trusted server state after the client's complete expression. Search-based updates and deletes affect every matching entity, including the entire table when conditions are empty; authorize them explicitly.

The library validates condition structure and values, bounds pagination and uses Criteria predicates rather than concatenating SQL values. Applications remain responsible for request-body size, string length, rate limits, transaction/query timeouts, logging policy and response-field exposure. Do not bind server fetch paths from an unrestricted request.

## Verified dependency baseline (2026-10-30)

The Boot 4 line resolves Spring Framework 7.0.10 and was checked against OSV with no findings in its resolved Maven dependencies. npm tooling was also checked with no reported advisories. These results describe the checked versions and date, not a guarantee against unknown vulnerabilities or future advisories.

The Boot 3 line preserves Spring Framework 6.2.19 for compatibility with Boot 3.5.16. Tomcat is upgraded to 10.1.61 and Hibernate Validator to 8.0.6.Final. Two Spring Framework advisories remain in the public 6.2.19 baseline:

| Advisory | Application condition | Vendor severity |
| --- | --- | --- |
| [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) | `XsltView`, a `/**` view-rendering mapping and no explicit view name | Medium |
| [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) | View fragments sent over SSE with attacker-controlled data streamed to other users | Low |

Searchable JPA does not configure XSLT views or SSE fragment endpoints. A consuming application can configure them independently, so the dependency findings must still be assessed against that application. Do not interpret this library's ordinary search tests as a security test of those unrelated endpoints.

For both advisories, Spring lists **6.2.20 through Enterprise Support**, or **7.0.9 and later on the public 7.x line**, as fixed versions. Enterprise repository access is not part of this project's public build. Applications that have licensed access can align all Spring Framework modules to a supported fixed 6.2 release and run their integration suite. Moving to the 2.x library line requires an application-level Boot 4 migration. The maintenance build does not silently replace Spring 6 with Spring 7.

## Reproducing checks

Inspect the application's own resolved dependencies, since its BOM or explicit versions can override the library's tested baseline:

```bash
./gradlew :searchable-jpa-core:dependencies --configuration runtimeClasspath
./gradlew :searchable-jpa-core:dependencyInsight --dependency spring-webmvc --configuration runtimeClasspath
npm audit
```

Use an up-to-date advisory scanner on the resolved coordinates and verify reported conditions against vendor advisories. H2 integration and compatibility tests do not replace testing against the deployment database and application configuration.
