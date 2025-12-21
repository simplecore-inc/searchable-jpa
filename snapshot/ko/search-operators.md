# 검색 연산자

Searchable JPA는 다양한 검색 연산자를 제공하여 복잡한 검색 조건을 구성할 수 있습니다. 이 문서는 모든 검색 연산자의 사용법과 예제를 설명합니다.

> **참고**: 기본적인 DTO 설정과 SearchableField 어노테이션 사용법은 [기본 사용법](basic-usage.md) 문서를 참조하세요.

## 비교 연산자 (Comparison Operators)

### EQUALS
값이 정확히 일치하는지 확인합니다.

```java
// URL 파라미터 방식
GET /api/posts/search?title.equals=Spring Boot

// JSON 방식
{
  "field": "title",
  "searchOperator": "equals",
  "value": "Spring Boot"
}
```

### NOT_EQUALS
값이 일치하지 않는지 확인합니다.

```java
// 사용 예제
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
값이 지정된 값보다 큰지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?viewCount.greaterThan=100
```

### GREATER_THAN_OR_EQUAL_TO
값이 지정된 값보다 크거나 같은지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?viewCount.greaterThanOrEqualTo=100
```

### LESS_THAN
값이 지정된 값보다 작은지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?viewCount.lessThan=1000
```

### LESS_THAN_OR_EQUAL_TO
값이 지정된 값보다 작거나 같은지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?viewCount.lessThanOrEqualTo=1000
```

## 문자열 패턴 연산자 (String Pattern Operators)

### CONTAINS
문자열이 지정된 부분 문자열을 포함하는지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?title.contains=Spring

// SQL: WHERE title LIKE '%Spring%'
```

### NOT_CONTAINS
문자열이 지정된 부분 문자열을 포함하지 않는지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?title.notContains=Test

// SQL: WHERE title NOT LIKE '%Test%'
```

### STARTS_WITH
문자열이 지정된 접두사로 시작하는지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?title.startsWith=Spring

// SQL: WHERE title LIKE 'Spring%'
```

### NOT_STARTS_WITH
문자열이 지정된 접두사로 시작하지 않는지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?title.notStartsWith=Draft

// SQL: WHERE title NOT LIKE 'Draft%'
```

### ENDS_WITH
문자열이 지정된 접미사로 끝나는지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?title.endsWith=Tutorial

// SQL: WHERE title LIKE '%Tutorial'
```

### NOT_ENDS_WITH
문자열이 지정된 접미사로 끝나지 않는지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?title.notEndsWith=Draft

// SQL: WHERE title NOT LIKE '%Draft'
```

## NULL 체크 연산자 (Null Check Operators)

### IS_NULL
필드 값이 NULL인지 확인합니다.

```java
@SearchableField(operators = {IS_NULL})
private String description;

// 사용 예제
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
필드 값이 NULL이 아닌지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?description.isNotNull

// SQL: WHERE description IS NOT NULL
```

## 컬렉션 연산자 (Collection Operators)

### IN
값이 지정된 목록에 포함되는지 확인합니다.

```java
@SearchableField(operators = {IN})
private PostStatus status;

// 사용 예제 (GET)
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
값이 지정된 목록에 포함되지 않는지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?status.notIn=DELETED,ARCHIVED

// SQL: WHERE status NOT IN ('DELETED', 'ARCHIVED')
```

## 범위 연산자 (Range Operators)

### BETWEEN
값이 지정된 범위 내에 있는지 확인합니다 (경계값 포함).

```java
@SearchableField(operators = {BETWEEN})
private Long viewCount;

// 숫자 범위
GET /api/posts/search?viewCount.between=100,1000

// 날짜 범위
GET /api/posts/search?createdAt.between=2024-01-01T00:00:00,2024-12-31T23:59:59

// SQL: WHERE view_count BETWEEN 100 AND 1000
```

```json
{
  "field": "viewCount",
  "searchOperator": "between",
  "value": 100,
  "value2": 1000
}
```

### NOT_BETWEEN
값이 지정된 범위 밖에 있는지 확인합니다.

```java
// 사용 예제
GET /api/posts/search?viewCount.notBetween=100,1000

// SQL: WHERE view_count NOT BETWEEN 100 AND 1000
```

## 데이터 타입별 사용 가능한 연산자

### 문자열 (String)
- EQUALS, NOT_EQUALS
- CONTAINS, NOT_CONTAINS
- STARTS_WITH, NOT_STARTS_WITH
- ENDS_WITH, NOT_ENDS_WITH
- IS_NULL, IS_NOT_NULL
- IN, NOT_IN

### 숫자 (Integer, Long, Double, BigDecimal)
- EQUALS, NOT_EQUALS
- GREATER_THAN, GREATER_THAN_OR_EQUAL_TO
- LESS_THAN, LESS_THAN_OR_EQUAL_TO
- BETWEEN, NOT_BETWEEN
- IS_NULL, IS_NOT_NULL
- IN, NOT_IN

### 날짜/시간 (LocalDate, LocalDateTime, Date)
- EQUALS, NOT_EQUALS
- GREATER_THAN, GREATER_THAN_OR_EQUAL_TO
- LESS_THAN, LESS_THAN_OR_EQUAL_TO
- BETWEEN, NOT_BETWEEN
- IS_NULL, IS_NOT_NULL

### 열거형 (Enum)
- EQUALS, NOT_EQUALS
- IN, NOT_IN
- IS_NULL, IS_NOT_NULL

### 불린 (Boolean)
- EQUALS, NOT_EQUALS
- IS_NULL, IS_NOT_NULL

## 복합 검색 조건 예제

### 여러 조건 조합

```bash
# 제목에 "Spring"이 포함되고 조회수가 100 이상인 게시글
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

### OR 조건

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
          "field": "status",
          "searchOperator": "equals",
          "value": "FEATURED"
        }
      ]
    }
  ]
}
```

### 그룹 조건

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

## 날짜/시간 형식

### LocalDateTime
```bash
# ISO 8601 형식
createdAt.greaterThan=2024-01-01T00:00:00
createdAt.between=2024-01-01T00:00:00,2024-12-31T23:59:59
```

### LocalDate
```bash
# 날짜만
publishedDate.equals=2024-01-01
publishedDate.between=2024-01-01,2024-12-31
```

## 특수 문자 처리

### URL 인코딩
특수 문자가 포함된 값은 URL 인코딩이 필요합니다.

```bash
# 공백 문자
GET /api/posts/search?title.contains=Spring%20Boot

# 특수 문자
GET /api/posts/search?title.contains=C%2B%2B
```

### 이스케이프 처리
JSON에서 특수 문자 사용 시 이스케이프 처리가 필요합니다.

```json
{
  "field": "content",
  "searchOperator": "contains",
  "value": "\"quoted text\""
}
```

## 성능 고려사항

### 인덱스 활용
- EQUALS, IN 연산자는 인덱스를 효율적으로 활용합니다
- CONTAINS, STARTS_WITH는 적절한 인덱스 설정이 필요합니다
- ENDS_WITH는 성능상 불리할 수 있습니다

### 대용량 데이터
- BETWEEN 연산자는 범위 검색에 효율적입니다
- IN 연산자의 값 목록이 너무 크면 성능이 저하될 수 있습니다
- 복합 조건 사용 시 적절한 인덱스를 설정하세요

## 다음 단계

- [2단계 쿼리 최적화](two-phase-query-optimization.md) - 고성능 페이징 시스템
- [고급 기능](advanced-features.md) - 복잡한 검색 조건과 중첩 쿼리
- [API 레퍼런스](api-reference.md) - 전체 API 문서