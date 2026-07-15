# Search Operators

Searchable JPA provides a variety of search operators for building complex search conditions. This document describes the usage and examples of every search operator.

> **Note**: For basic DTO configuration and `SearchableField` annotation usage, see [Basic Usage](./basic-usage.md).

## Comparison Operators

### EQUALS
Checks whether a value matches exactly.

```java
// URL parameter style
GET /api/posts/search?title.equals=Spring Boot

// JSON style
{
  "field": "title",
  "searchOperator": "equals",
  "value": "Spring Boot"
}
```

### NOT_EQUALS
Checks whether a value does not match.

```java
// Example usage
GET /api/posts/search?status.notEquals=DELETED
```

```json
{
  "field": "status",
  "searchOperator": "notEquals", 
  "value": "DELETED"
}
```

### GREATER_THAN
Checks whether a value is greater than the specified value.

```java
// Example usage
GET /api/posts/search?viewCount.greaterThan=100
```

### GREATER_THAN_OR_EQUAL_TO
Checks whether a value is greater than or equal to the specified value.

```java
// Example usage
GET /api/posts/search?viewCount.greaterThanOrEqualTo=100
```

### LESS_THAN
Checks whether a value is less than the specified value.

```java
// Example usage
GET /api/posts/search?viewCount.lessThan=1000
```

### LESS_THAN_OR_EQUAL_TO
Checks whether a value is less than or equal to the specified value.

```java
// Example usage
GET /api/posts/search?viewCount.lessThanOrEqualTo=1000
```

## String Pattern Operators

> **Note**: `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, and their `NOT_` counterparts are case-sensitive; no case-insensitive variant is provided. If the search value contains `%`, `_`, or `\`, these characters are automatically escaped and matched literally instead of being treated as SQL wildcards (see [Special Character Handling](#special-character-handling) below for examples). Passing an empty string as the search value raises an error rather than matching every row.

### CONTAINS
Checks whether a string contains the specified substring.

```java
// Example usage
GET /api/posts/search?title.contains=Spring

// SQL: WHERE title LIKE '%Spring%'
```

### NOT_CONTAINS
Checks whether a string does not contain the specified substring.

```java
// Example usage
GET /api/posts/search?title.notContains=Test

// SQL: WHERE title NOT LIKE '%Test%'
```

### STARTS_WITH
Checks whether a string starts with the specified prefix.

```java
// Example usage
GET /api/posts/search?title.startsWith=Spring

// SQL: WHERE title LIKE 'Spring%'
```

### NOT_STARTS_WITH
Checks whether a string does not start with the specified prefix.

```java
// Example usage
GET /api/posts/search?title.notStartsWith=Draft

// SQL: WHERE title NOT LIKE 'Draft%'
```

### ENDS_WITH
Checks whether a string ends with the specified suffix.

```java
// Example usage
GET /api/posts/search?title.endsWith=Tutorial

// SQL: WHERE title LIKE '%Tutorial'
```

### NOT_ENDS_WITH
Checks whether a string does not end with the specified suffix.

```java
// Example usage
GET /api/posts/search?title.notEndsWith=Draft

// SQL: WHERE title NOT LIKE '%Draft'
```

## Null Check Operators

### IS_NULL
Checks whether a field value is NULL.

```java
@SearchableField(operators = {IS_NULL, IS_NOT_NULL})
private String description;

// Example usage
GET /api/posts/search?description.isNull

// SQL: WHERE description IS NULL
```

```json
{
  "field": "description",
  "searchOperator": "isNull"
}
```

### IS_NOT_NULL
Checks whether a field value is not NULL.

```java
// Example usage
GET /api/posts/search?description.isNotNull

// SQL: WHERE description IS NOT NULL
```

## Collection Operators

### IN
Checks whether a value is included in the specified list.

```java
@SearchableField(operators = {IN, NOT_IN})
private PostStatus status;

// Example usage (GET)
GET /api/posts/search?status.in=PUBLISHED,DRAFT

// SQL: WHERE status IN ('PUBLISHED', 'DRAFT')
```

```json
{
  "field": "status",
  "searchOperator": "in",
  "value": ["PUBLISHED", "DRAFT"]
}
```

### NOT_IN
Checks whether a value is not included in the specified list.

```java
// Example usage
GET /api/posts/search?status.notIn=DELETED,ARCHIVED

// SQL: WHERE status NOT IN ('DELETED', 'ARCHIVED')
```

## Range Operators

### BETWEEN
Checks whether a value falls within the specified range (both bounds inclusive).

```java
@SearchableField(operators = {BETWEEN, NOT_BETWEEN})
private Long viewCount;

// Numeric range
GET /api/posts/search?viewCount.between=100,1000

// Date/time range
GET /api/posts/search?createdAt.between=2024-01-01T00:00:00,2024-12-31T23:59:59

// SQL: WHERE view_count BETWEEN 100 AND 1000
```

> **Note**: For how a date-only input is handled on fields that carry a time component, such as `LocalDateTime`, see [Date/Time Formats](#date-time-formats) below.

```json
{
  "field": "viewCount",
  "searchOperator": "between",
  "value": 100,
  "value2": 1000
}
```

### NOT_BETWEEN
Checks whether a value falls outside the specified range.

```java
// Example usage
GET /api/posts/search?viewCount.notBetween=100,1000

// SQL: WHERE view_count NOT BETWEEN 100 AND 1000
```

## Supported Operators by Data Type

### String
- EQUALS, NOT_EQUALS
- CONTAINS, NOT_CONTAINS
- STARTS_WITH, NOT_STARTS_WITH
- ENDS_WITH, NOT_ENDS_WITH
- IS_NULL, IS_NOT_NULL
- IN, NOT_IN

### Numeric (Integer, Long, Double, BigDecimal)
- EQUALS, NOT_EQUALS
- GREATER_THAN, GREATER_THAN_OR_EQUAL_TO
- LESS_THAN, LESS_THAN_OR_EQUAL_TO
- BETWEEN, NOT_BETWEEN
- IS_NULL, IS_NOT_NULL
- IN, NOT_IN

### Date/Time (LocalDate, LocalDateTime, Date)
- EQUALS, NOT_EQUALS
- GREATER_THAN, GREATER_THAN_OR_EQUAL_TO
- LESS_THAN, LESS_THAN_OR_EQUAL_TO
- BETWEEN, NOT_BETWEEN
- IS_NULL, IS_NOT_NULL

### Enum
- EQUALS, NOT_EQUALS
- IN, NOT_IN
- IS_NULL, IS_NOT_NULL

### UUID
- EQUALS, NOT_EQUALS
- IN, NOT_IN
- IS_NULL, IS_NOT_NULL

### Boolean
- EQUALS, NOT_EQUALS
- IS_NULL, IS_NOT_NULL

## Composite Search Condition Examples

> **Note**: Each item in the `conditions` array uses its own `operator` field to specify how it combines with the result accumulated so far (`"and"` or `"or"`; defaults to `"and"` when omitted). Since the first item in the array has nothing to combine with, its `operator` value is ignored; from the second item onward, each item's own `operator` value determines how it combines with the preceding result. The `operator` that wraps a group (a nested `conditions` array) only controls how that group as a whole combines with other items in the same array -- it has no effect on how the group's own children combine with each other. To OR the conditions inside a group, set `"operator": "or"` on each condition inside the group individually, not just on the condition wrapping the group.

### Combining Multiple Conditions

```bash
# Posts whose title contains "Spring" and whose view count is at least 100
GET /api/posts/search?title.contains=Spring&viewCount.greaterThan=100
```

```json
{
  "conditions": [
    {
      "operator": "and",
      "field": "title",
      "searchOperator": "contains",
      "value": "Spring"
    },
    {
      "operator": "and", 
      "field": "viewCount",
      "searchOperator": "greaterThan",
      "value": 100
    }
  ]
}
```

### OR Conditions

```json
{
  "conditions": [
    {
      "operator": "or",
      "conditions": [
        {
          "field": "status",
          "searchOperator": "equals",
          "value": "PUBLISHED"
        },
        {
          "operator": "or",
          "field": "status",
          "searchOperator": "equals",
          "value": "FEATURED"
        }
      ]
    }
  ]
}
```

### Grouped Conditions

```json
{
  "conditions": [
    {
      "operator": "and",
      "conditions": [
        {
          "operator": "or",
          "conditions": [
            {
              "field": "title",
              "searchOperator": "contains",
              "value": "Spring"
            },
            {
              "operator": "or",
              "field": "title",
              "searchOperator": "contains",
              "value": "Java"
            }
          ]
        }
      ]
    },
    {
      "operator": "and",
      "field": "status",
      "searchOperator": "equals",
      "value": "PUBLISHED"
    }
  ]
}
```

## Date/Time Formats

### LocalDateTime
```bash
# ISO 8601 format
createdAt.greaterThan=2024-01-01T00:00:00
createdAt.between=2024-01-01T00:00:00,2024-12-31T23:59:59
```

### LocalDate
```bash
# Date only
publishedDate.equals=2024-01-01
publishedDate.between=2024-01-01,2024-12-31
```

### Supplying Only a Date to BETWEEN
When you apply a BETWEEN/NOT_BETWEEN condition to a field that carries a time component -- `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`, `Instant`, or `Date` -- and supply only a date, the lower bound is automatically filled in as `00:00:00` on that date, and the upper bound as `23:59:59.999999999` on that date.

```bash
# createdAt: a LocalDateTime field
createdAt.between=2024-01-01,2024-12-31

# Actual condition: createdAt >= 2024-01-01T00:00:00 AND createdAt <= 2024-12-31T23:59:59.999999999
```

`LocalDate` and `LocalTime` fields deal only in date-only or time-only units to begin with, so this adjustment does not apply -- the input value is used exactly as given.

## Special Character Handling

### URL Encoding
Values that contain special characters require URL encoding.

```bash
# Space character
GET /api/posts/search?title.contains=Spring%20Boot

# Special character
GET /api/posts/search?title.contains=C%2B%2B
```

### Escaping Wildcard Characters
In values passed to pattern-matching operators such as `CONTAINS`, `STARTS_WITH`, and `ENDS_WITH`, the characters `%`, `_`, and `\` are escaped and matched literally instead of being treated as SQL wildcards.

```json
{
  "field": "title",
  "searchOperator": "contains",
  "value": "50% 할인"
}
```

This condition matches only rows whose `title` value literally contains the string `50% 할인`; the `%` does not act as a wildcard standing in for an arbitrary substring.

### Escape Handling
Special characters used inside JSON must be escaped accordingly.

```json
{
  "field": "content",
  "searchOperator": "contains",
  "value": "\"quoted text\""
}
```

## Performance Considerations

### Index Utilization
- `EQUALS` and `IN` make efficient use of indexes.
- `CONTAINS` and `STARTS_WITH` require an appropriate index configuration.
- `ENDS_WITH` prepends a wildcard before the search term, so it cannot use an index and performs worse as a result.

### Large Datasets
- `BETWEEN` is efficient for range searches.
- Supplying too large a value list to `IN` degrades performance.
- Set up appropriate indexes when combining multiple conditions.

## Next Steps

- [Two-Phase Query Optimization](./two-phase-query-optimization.md) - a high-performance pagination system
- [Advanced Features](./advanced-features.md) - complex search conditions and nested queries
- [API Reference](./api-reference.md) - complete API documentation
