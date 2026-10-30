# Troubleshooting

## Dependency or startup failures

Use 2.x with Boot 4 and 1.x with Boot 3. Do not combine versions of the three Searchable JPA modules. Check `./gradlew dependencyInsight --dependency hibernate-core --configuration runtimeClasspath` for unexpected overrides. A successful build on one framework line does not establish compatibility with another major line.

For GitHub Packages authentication errors, check the repository URL, username, token scope and package access. For Maven Local, publish all three modules with `./gradlew publishToMavenLocal`, add `mavenLocal()` to the consuming project and request the exact published version.

If automatic configuration is absent, confirm the starter is on the runtime classpath, JPA infrastructure exists and `SearchableJpaConfiguration` has not been excluded. Enable Spring Boot's condition report with `debug=true`. `searchable.hibernate.auto-optimization=false` disables Hibernate defaults; it does not remove the search service API.

A repository passed to a service must implement both `JpaRepository` and `JpaSpecificationExecutor`. Configure entities and repositories as in an ordinary Spring Data JPA application. Let Hibernate detect the database dialect from JDBC metadata unless the application requires an explicit setting; obsolete dialect class names from older Hibernate versions can fail at startup.

## Rejected search input

Check the DTO property name, `entityField` mapping, allowed operators and `sortable=true`. Inherited fields participate. An empty annotation operator array permits all operators; it does not prohibit searching.

Query parameter operators use their wire names, such as `greaterThanOrEqualTo`. Use `sort=createdAt.desc`, not `sort=createdAt,desc`. Date-only values suit `LocalDate`; a `LocalDateTime` value needs both date and time. A full-day timestamp search usually uses an inclusive lower bound and an exclusive next-day upper bound.

`BETWEEN` needs exactly two ordered non-null values. `IN`/`NOT_IN` accept 1–1,000 non-null values. Pattern searches reject empty values and escape literal `%` and `_`. See [Search Operators](./search-operators.md) for exact formats.

Default request limits are 1,000 condition nodes, 32 levels of nesting and 16 sort fields. A page defaults to 20 rows and may request at most 1,000 unless configured otherwise. A page/size pair whose offset exceeds the JPA integer range is rejected. Oversized or malformed JSON is rejected by the HTTP message converter; direct Java APIs throw library validation/parse exceptions. Map application-level exceptions through your own controller advice.

## Unexpected AND/OR results

Nodes are evaluated in insertion order using each node's connector. `A.or(B).and(C)` means `(A OR B) AND C`. Use an explicit nested group for `A OR (B AND C)`. A group's outer connector remains meaningful even if the group has one child. The first connector has no preceding operand. See [Advanced Features](./advanced-features.md).

## Slow queries or extra SQL

Simple single-key searches can use a direct entity query. Collection joins and composite keys use an ID phase followed by entity loading. Automatic collection loading is enabled by default for compatibility and can issue a separate query for each independent collection. It does not guarantee a fixed total query count or load every nested relationship.

Inspect SQL with `org.hibernate.SQL=DEBUG`; Hibernate 6/7 bind logging uses `org.hibernate.orm.jdbc.bind=TRACE`. Bind logs can contain submitted values, so apply the application's logging policy. Measure the actual database plan, indexes, row counts and association sizes. Deep offsets still cost work even when the result page is small.

Use `searchable.hibernate.auto-fetch-collections=false` if the response does not need collections. Then map needed data within the transaction or use an explicit application fetch plan. Batch fetching reduces repeated lazy loads but does not turn every association into one SQL statement.

`findAllWithSearch` may skip COUNT when a short page proves the total. `findFirstWithSearch` requests the first matching row without COUNT and ignores the page offset. For exports, iterate bounded pages or define a dedicated application query.

## Composite keys, projections and writes

Both `@IdClass` and `@EmbeddedId` are supported. Map DTO fields to the actual key path; an embedded ID uses paths such as `id.tenantId`. Identity matching uses typed component values. Count queries count each root once with EXISTS when joins could duplicate it.

Interface projections use Spring projection support. Concrete projection classes are rejected; ModelMapper is used for update DTO conversion, not class projections. Projections do not guarantee SQL column pruning. Map responses while required lazy state is available.

Updates and deletes traverse matching entities in chunks of 200. Patch properties that are null or absent are not changed. Use nullable wrapper properties so an omitted primitive value is not interpreted as zero/false. IDs, versions, audit properties and `updatable=false` columns are protected. Entity callbacks and an existing application transaction are retained. A long outer transaction can still retain many managed entities; chunk size is not a promise of a small persistence context in that case.

## Time counts and JSON compatibility

Time buckets use `[start, end)` and exclude the final end boundary. Joined rows cannot count the same root twice. Aligned periods on supported databases use grouped epoch values; fine-grained or unsupported cases use conditional aggregation. Check the date property, timezone and exact period before comparing counts with a paginated list.

Jackson 2 helper signatures remain available. Typed Jackson 3 HTTP bodies are supported as an additional path. Use `SearchCondition<MySearchDto>` so the deserializer knows which DTO to validate. A raw `SearchCondition` does not supply that type. See [API Reference](./api-reference.md).

## Documentation navigation

The version selector preserves language, page and heading when the target page exists. If it is absent, navigation falls back to the selected version's home page in the same language, then English if necessary. A network failure keeps the current page and permits retry. A standalone local preview has no version selector; use the documented browser fixture to test versioned paths.
