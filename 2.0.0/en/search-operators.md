# Search Operators

All 18 operators are listed below. A DTO's `@SearchableField` controls which are allowed. An empty operator array allows all operators; the value and underlying field type must still support the operation.

| Java constant | Wire name / builder method | Meaning |
| --- | --- | --- |
| `EQUALS` | `equals` | Equal to a value |
| `NOT_EQUALS` | `notEquals` | Not equal to a value |
| `GREATER_THAN` | `greaterThan` | Strictly greater |
| `GREATER_THAN_OR_EQUAL_TO` | `greaterThanOrEqualTo` | Greater than or equal |
| `LESS_THAN` | `lessThan` | Strictly less |
| `LESS_THAN_OR_EQUAL_TO` | `lessThanOrEqualTo` | Less than or equal |
| `CONTAINS` | `contains` | Literal substring |
| `NOT_CONTAINS` | `notContains` | No literal substring |
| `STARTS_WITH` | `startsWith` | Literal prefix |
| `NOT_STARTS_WITH` | `notStartsWith` | Not the literal prefix |
| `ENDS_WITH` | `endsWith` | Literal suffix |
| `NOT_ENDS_WITH` | `notEndsWith` | Not the literal suffix |
| `IS_NULL` | `isNull` | SQL NULL |
| `IS_NOT_NULL` | `isNotNull` | Not SQL NULL |
| `IN` | `in` | Member of a collection |
| `NOT_IN` | `notIn` | Not a member |
| `BETWEEN` | `between` | Inclusive lower and upper bounds |
| `NOT_BETWEEN` | `notBetween` | Outside the inclusive range |

## Request syntax

```text
title.contains=Spring
viewCount.greaterThanOrEqualTo=100
status.in=PUBLISHED,DRAFT
createdAt.between=2026-10-01T00:00:00Z,2026-10-31T23:59:59Z
content.isNull=true
sort=createdAt.desc,id.asc
```

The null-check parameter value is ignored: `isNull=false` still means IS NULL. Omit the condition to skip the check. GET filters are ANDed and use one Map entry per key. Percent-encode query values, especially `+`, `&`, spaces and non-ASCII characters; `curl --data-urlencode` avoids manual encoding errors.

IN/NOT_IN accept comma-separated GET values; JSON uses arrays. BETWEEN/NOT_BETWEEN require exactly two GET values, or `value` and `value2` in JSON. A comma inside a single-value operator remains literal. To include a literal comma in an IN member, use a JSON array instead of the GET comma syntax.

```json
{"conditions":[
  {"field":"viewCount","searchOperator":"between","value":100,"value2":500},
  {"operator":"and","field":"status","searchOperator":"in","value":["PUBLISHED","DRAFT"]}
]}
```

## Types and conversion

Strings, numeric primitives/wrappers, BigDecimal/BigInteger, booleans, enums, UUID and supported Java temporal types can be converted. Numeric comparison/range operators require compatible comparable values. Enum names are converted to constants; do not assume arbitrary display labels are accepted. Prefer equality, IN and null checks for enums and UUIDs unless your database mapping supports a meaningful ordering.

Boolean parsing accepts true/false, 1/0, yes/no, y/n and on/off, case-insensitively. Invalid values raise a parse/validation error. DTO field constraints such as `@Min`, `@Size` and `@Pattern` apply to the searched values; unrelated required DTO properties do not block a search. Both BETWEEN bounds are checked.

NULL is not an ordinary SQL value. Negative predicates such as NOT IN and NOT LIKE do not automatically include SQL NULL rows. Use an explicit OR IS NULL group when that is the intended result.

## Pattern matching

Pattern operators use case-insensitive comparison by lowering values and database expressions. Case behavior still depends on the database's locale/collation. Literal `%`, `_` and the escape character are escaped before LIKE, with an explicit escape clause. A user cannot inject a wildcard simply by putting `%` in a contains value. Empty or whitespace-only patterns are rejected.

A leading wildcard in contains/endsWith can prevent normal index use. Lowercasing can require a functional index or an appropriate database collation. Choose indexed equality/prefix searches or a dedicated full-text search where appropriate.

For a JSON-typed entity field, supported pattern operations compare its textual representation. They are not JSON-path queries and can depend on database serialization/casting. For structural JSON searches, use a database-aware repository query.

## Date and time

Prefer ISO input: `2026-10-30`, `12:34:56`, `2026-10-30T12:34:56`, or an offset/instant such as `2026-10-30T12:34:56+09:00`. The target DTO type determines conversion. A date-only BETWEEN lower bound expands to start of day, the upper bound to end of day for date-time types. Instant/offset conversion uses the [application timezone rules](/en/auto-configuration.md#timezone-resolution).

BETWEEN is inclusive and differs from `TimeBucketCounter`'s half-open periods. Database timestamp precision may round an end-of-day nanosecond. For adjacent reporting periods, use explicit start-inclusive/end-exclusive comparisons or the counter API and test actual storage precision.

## Logical groups and limits

The first node supplies the initial result. Later nodes combine with the accumulated result using `and` or `or`, left to right. Nested groups supply parentheses. Unknown logical operators are rejected. A group’s connector applies to the group as a whole, including a group with only one child.

A search accepts at most 1,000 condition/group nodes, 32 nested group levels, 1,000 collection values per condition and 16 sort fields. Page limits are separately configurable. See [Basic Usage](/en/basic-usage.md) and [Auto Configuration](/en/auto-configuration.md).
