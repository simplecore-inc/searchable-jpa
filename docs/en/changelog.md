# Changelog

This document records the notable changes to the searchable-jpa library.

> See the linked GitHub commits for the full details of each change.

---

## Recent Changes

### 1.1.0-SNAPSHOT (in development)

This development version addresses findings from a code review and includes breaking changes.

#### Breaking Changes
- **Consolidated exception hierarchy** [`622d7f6`](https://github.com/simplecore-inc/searchable-jpa/commit/622d7f6)
  - `SearchableValidationException` and `SearchableParseException` now extend only `SearchableException` (a `RuntimeException`)
  - Both exceptions no longer extend `jakarta.validation.ValidationException`
  - `SearchableException` retains five subclasses: `SearchableConfigurationException`, `SearchableValidationException`, `SearchableOperationException`, `SearchableJoinException`, and `SearchableParseException`
- **Changed evaluation order for nested and()/or() condition groups**
  - Nested `and()`/`or()` groups built with `SearchConditionBuilder` are no longer grouped by operator. Each node is now combined with the accumulated result using its own operator, in call order (left to right).
- **Changed priority of Hibernate auto-configuration**
  - The `searchable.hibernate.*` defaults registered by the starter now apply at the lowest priority, so any Hibernate settings you configure always take precedence.
  - Removed an ineffective `@ConditionalOnProperty` on a `@PostConstruct` method, and added validation requiring batch size properties to be 1 or greater.
- **Changed how timezone-less date/time search values are interpreted**
  - Previously interpreted in the deployment host's JVM default timezone; now interpreted in the application timezone, defaulting to UTC when unset.
  - The timezone is resolved in this order: `searchable.date-time.default-timezone`, an `applicationZoneId` bean from the host, `spring.jackson.time-zone`, then UTC.

#### Improvements
- **Pagination**: resolves `@EmbeddedId` property names from the metamodel, computes the total count correctly even for requests beyond the last page, handles distinct counting for composite keys, stabilizes to-many sorting with GROUP BY aggregation, and separates the logic into `CompositeKeyQueryExecutor` and `SpecificationQuerySupport`
- **Parsing**: applies comma splitting only to multi-value operators such as IN, NOT_IN, and BETWEEN, rejects empty pattern values, and validates the second BETWEEN value
- **Joins and condition building**: adds an ESCAPE clause to LIKE conditions to correctly handle literal `%` and `_` characters, and rejects empty pattern values
- **Service, utilities, and exceptions**: honors inherited fields from `@MappedSuperclass`, and adds support for parsing UUID values
- **Configuration**: adds `searchable.date-time.default-timezone` to set the timezone used to interpret search values
- **OpenAPI**: preserves enum values in IN/NOT_IN/BETWEEN schemas, generates distinct upper and lower bounds for BETWEEN examples, documents inherited fields, guards array-type schema generation against NPEs, and assigns a per-type `format` (date/partial-time/date-time) to date/time parameters

---

## Changes by Version

### v1.0.11
- Introduced `SearchableServiceDelegate` and `SearchableServiceSupport` ([`194f6e7`](https://github.com/simplecore-inc/searchable-jpa/commit/194f6e7))
  - Services that cannot extend `DefaultSearchableService` can now use composition instead
  - `SearchableServiceDelegate` handles the search, sort, and pagination logic, while `SearchableServiceSupport` is a mixin interface that wires it in

### v1.0.10
- Downgraded excessive info/debug logs to trace level ([`94f5fed`](https://github.com/simplecore-inc/searchable-jpa/commit/94f5fed))

### v1.0.9
- Reduced response size by simplifying OpenAPI documentation output ([`b9af1cb`](https://github.com/simplecore-inc/searchable-jpa/commit/b9af1cb))
- Added Context7 documentation indexing configuration ([`291609a`](https://github.com/simplecore-inc/searchable-jpa/commit/291609a))

### v1.0.8
- Fixed an issue where `orXXX()` operators and nested group conditions were dropped ([`9378984`](https://github.com/simplecore-inc/searchable-jpa/commit/9378984))
- Improved performance by adding entity metadata caching ([`8f21b7d`](https://github.com/simplecore-inc/searchable-jpa/commit/8f21b7d))

### v1.0.7
- Fixed OpenAPI example generation to use fixed date/time values ([`a6c8479`](https://github.com/simplecore-inc/searchable-jpa/commit/a6c8479))
- Removed emojis from logs and documentation ([`11893e6`](https://github.com/simplecore-inc/searchable-jpa/commit/11893e6), [`bab39bd`](https://github.com/simplecore-inc/searchable-jpa/commit/bab39bd))

### v1.0.6
- Added `LocalDate`/`LocalTime` support to `OpenApiDocUtils` ([`089ef98`](https://github.com/simplecore-inc/searchable-jpa/commit/089ef98))
- Downgraded verbose debug logs to trace level ([`ad4c9a4`](https://github.com/simplecore-inc/searchable-jpa/commit/ad4c9a4))

### v1.0.5
- Added explicit `fetchFields` support ([`9a177a7`](https://github.com/simplecore-inc/searchable-jpa/commit/9a177a7))
  - Added a `fetchFields` property to `SearchCondition`
  - Lets you explicitly fetch-join lazily loaded relationships
  - Supports nested paths (for example, `author.profile`)
  - Applies `@JsonIgnore` for security, so it can only be set on the server side
  - [Full details](/en/relationship-and-two-phase-query.md#explicit-fetch-join-fetchfields)

### v1.0.4
- Built a Docsify-based Korean documentation site ([`4723c7a`](https://github.com/simplecore-inc/searchable-jpa/commit/4723c7a), [`2c9bb11`](https://github.com/simplecore-inc/searchable-jpa/commit/2c9bb11), [`147597b`](https://github.com/simplecore-inc/searchable-jpa/commit/147597b))
- Improved exception handling in the OpenAPI example generation logic ([`ac01315`](https://github.com/simplecore-inc/searchable-jpa/commit/ac01315))

### v1.0.3
- Added support for searching JSON-typed fields ([`ab55fa9`](https://github.com/simplecore-inc/searchable-jpa/commit/ab55fa9))
- Added a rule to the commit message guidelines excluding AI signatures ([`73ad2df`](https://github.com/simplecore-inc/searchable-jpa/commit/73ad2df))

### v1.0.2
- Version-number-only release with no other changes

### v1.0.1
- Added support for extending (subclassing) `SearchConditionBuilder` ([`034ded0`](https://github.com/simplecore-inc/searchable-jpa/commit/034ded0))

### v1.0.0
- First stable release
- Supports Spring Boot 3.2.x+
- Compatible with Jakarta EE 9+

---

## Related Links

- [GitHub Repository](https://github.com/simplecore-inc/searchable-jpa)
- [Issue Tracker](https://github.com/simplecore-inc/searchable-jpa/issues)
- [Full Commit History](https://github.com/simplecore-inc/searchable-jpa/commits/master)
