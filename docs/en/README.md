# Searchable JPA

English | [한국어](/ko/README.md)

Searchable JPA adds DTO-controlled filtering, sorting and pagination to Spring Data JPA. Applications declare searchable properties with `@SearchableField`, parse query parameters or JSON into `SearchCondition`, then execute through a service or delegate.

## Supported lines

| Library | Spring Boot | Branch | Status |
| --- | --- | --- | --- |
| 2.x | 4.1.x and later 4.x | `master` | Active |
| 1.x | 3.2.x–3.5.x | `spring-boot-3x` | Maintained |
| 0.1.x | 2.7.x | `spring-boot-2x` | Unmaintained |

This source tree builds `2.0.0` with Java 17, Spring Boot 4.1.2 and Hibernate 7.4.14.Final. Select the documentation version that matches your dependency. The development documentation can describe fixes that are absent from earlier releases.

## Features and boundaries

- Eighteen comparison, pattern, range, membership and null operators, with runtime DTO validation and entity-path mapping.
- Direct entity queries for simple single-key searches; ID selection followed by hydration for collection joins or composite keys. Independent collections are fetched separately.
- Pages, first-result lookup, interface projections and condition-based updates/deletes.
- Time-period counts with equal-width buckets, including a portable fallback for unsupported database functions.
- Jackson 2 public APIs plus a Jackson 3 deserializer for typed search bodies.
- Spring Boot configuration and SpringDoc operation documentation generated from the same DTO annotations.

Field names in builders are strings and are validated at runtime. Query performance still depends on indexes, selectivity, association size and database plans. The library does not supply application authorization, tenant isolation or a universal response DTO.

## Start here

1. [Install the matching library line](./installation.md), either from GitHub Packages or Maven Local.
2. [Define an entity, repository, DTO, service and controller](./basic-usage.md).
3. [Set page limits, timezone and Hibernate defaults](./auto-configuration.md).
4. [Choose operators and check their value formats](./search-operators.md).
5. [Review query strategy and relationship costs](./two-phase-query-optimization.md).

For example, a controller using the documented DTO can accept:

```http
GET /api/posts?title.contains=Spring&sort=createdAt.desc&page=0&size=20
```

The sort separator is a dot. Multiple sort fields use commas, for example `createdAt.desc,title.asc`.

## Modules

| Module | Responsibility |
| --- | --- |
| `searchable-jpa-core` | Conditions, validation, JPA execution and services |
| `searchable-jpa-openapi` | Search parameter documentation; depends on core |
| `spring-boot-starter-searchable-jpa` | Both modules and automatic Spring configuration |

See the [API reference](./api-reference.md), [advanced features](./advanced-features.md), [OpenAPI integration](./openapi-integration.md), [troubleshooting](./faq.md) and [changelog](./changelog.md). Documentation contributors can use the [build and verification guide](./documentation-guide.md).

## License and support

The [SimpleCORE License 1.0](./license.md) applies. Report reproducible bugs through [GitHub Issues](https://github.com/simplecore-inc/searchable-jpa/issues). Include the library, Java, Spring Boot and database versions, a minimal DTO/entity mapping and a sanitized failing request.
