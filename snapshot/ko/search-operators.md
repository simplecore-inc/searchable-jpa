# 검색 연산자

지원하는 연산자는 18개입니다. DTO의 `@SearchableField`에서 허용할 연산자를 정합니다. 연산자 배열이 비어 있으면 모든 연산자를 허용하지만, 값과 실제 필드 타입이 해당 연산을 처리할 수 있어야 합니다.

| Java 상수 | 요청 이름·빌더 메서드 | 의미 |
| --- | --- | --- |
| `EQUALS` | `equals` | 값 일치 |
| `NOT_EQUALS` | `notEquals` | 값 불일치 |
| `GREATER_THAN` | `greaterThan` | 초과 |
| `GREATER_THAN_OR_EQUAL_TO` | `greaterThanOrEqualTo` | 이상 |
| `LESS_THAN` | `lessThan` | 미만 |
| `LESS_THAN_OR_EQUAL_TO` | `lessThanOrEqualTo` | 이하 |
| `CONTAINS` | `contains` | 문자열 포함 |
| `NOT_CONTAINS` | `notContains` | 문자열 미포함 |
| `STARTS_WITH` | `startsWith` | 문자열 시작 |
| `NOT_STARTS_WITH` | `notStartsWith` | 해당 문자열로 시작하지 않음 |
| `ENDS_WITH` | `endsWith` | 문자열 끝 |
| `NOT_ENDS_WITH` | `notEndsWith` | 해당 문자열로 끝나지 않음 |
| `IS_NULL` | `isNull` | SQL NULL |
| `IS_NOT_NULL` | `isNotNull` | SQL NULL이 아님 |
| `IN` | `in` | 컬렉션 원소와 일치 |
| `NOT_IN` | `notIn` | 컬렉션 원소와 불일치 |
| `BETWEEN` | `between` | 하한·상한 포함 범위 |
| `NOT_BETWEEN` | `notBetween` | 하한·상한 포함 범위의 바깥 |

## 요청 문법

```text
title.contains=Spring
viewCount.greaterThanOrEqualTo=100
status.in=PUBLISHED,DRAFT
createdAt.between=2026-10-01T00:00:00Z,2026-10-31T23:59:59Z
content.isNull=true
sort=createdAt.desc,id.asc
```

NULL 검사 파라미터의 값은 무시합니다. `isNull=false`도 IS NULL을 뜻합니다. 검사를 생략하려면 조건 자체를 보내지 마세요. GET 검색은 AND로 연결하며 키마다 Map 항목 하나를 사용합니다. `+`, `&`, 공백과 비ASCII 문자는 URL 인코딩하세요. `curl --data-urlencode`를 사용하면 직접 인코딩할 때의 오류를 줄일 수 있습니다.

IN·NOT_IN은 GET에서 쉼표로 나눈 값, JSON에서 배열을 받습니다. BETWEEN·NOT_BETWEEN은 GET에서 정확히 두 값, JSON에서 `value`와 `value2`를 받습니다. 단일 값 연산자의 쉼표는 일반 문자로 유지합니다. IN 원소 안에 쉼표가 있어야 한다면 GET 대신 JSON 배열을 사용하세요.

```json
{"conditions":[
  {"field":"viewCount","searchOperator":"between","value":100,"value2":500},
  {"operator":"and","field":"status","searchOperator":"in","value":["PUBLISHED","DRAFT"]}
]}
```

## 타입과 변환

문자열, 숫자 기본형·래퍼, BigDecimal·BigInteger, 불리언, enum, UUID와 지원하는 Java 날짜·시간 타입을 변환합니다. 비교·범위 연산에는 서로 비교할 수 있는 호환 값이 필요합니다. enum 이름을 상수로 변환하므로 임의의 표시 이름을 사용할 수 있다고 가정하지 마세요. enum과 UUID에는 데이터베이스 매핑상 의미 있는 순서가 필요하지 않다면 일치·IN·NULL 검사를 우선 사용하세요.

불리언은 대소문자 구분 없이 true/false, 1/0, yes/no, y/n, on/off를 받습니다. 잘못된 값에는 파싱·검증 오류가 발생합니다. `@Min`, `@Size`, `@Pattern` 같은 DTO 제약은 검색값에 적용합니다. 무관한 필수 DTO 속성은 검색을 막지 않습니다. BETWEEN은 양쪽 경계를 검증합니다.

SQL NULL은 일반 값이 아닙니다. NOT IN·NOT LIKE 같은 부정 조건에 NULL 행이 자동으로 포함되지는 않습니다. NULL도 포함해야 한다면 OR IS NULL 그룹을 명시하세요.

## 문자열 패턴

문자열 패턴은 입력과 데이터베이스 식을 소문자로 바꿔 비교합니다. 세부 대소문자 처리에는 데이터베이스의 로케일·정렬 규칙이 영향을 줍니다. `%`, `_`와 이스케이프 문자는 LIKE 적용 전에 이스케이프하고, SQL에도 ESCAPE 절을 지정합니다. 사용자가 contains 값에 `%`를 넣어 임의의 와일드카드를 만들 수는 없습니다. 비어 있거나 공백뿐인 패턴은 거부합니다.

contains·endsWith의 앞부분 와일드카드는 일반 인덱스 사용을 막을 수 있습니다. 소문자 변환에는 함수 인덱스나 적절한 데이터베이스 정렬 규칙이 필요할 수 있습니다. 용도에 따라 인덱스를 사용하는 일치·접두사 검색이나 전문 검색을 선택하세요.

JSON 타입 엔티티 필드에 지원하는 패턴 연산을 적용하면 텍스트 표현을 비교합니다. JSON 경로 검색이 아니며 데이터베이스의 직렬화·형 변환 방식에 영향을 받습니다. JSON 구조를 검색하려면 데이터베이스에 맞는 Repository 쿼리를 사용하세요.

## 날짜·시간

`2026-10-30`, `12:34:56`, `2026-10-30T12:34:56`, `2026-10-30T12:34:56+09:00` 같은 ISO 형식을 권장합니다. DTO 필드 타입에 따라 변환합니다. 날짜·시간 타입의 BETWEEN에 날짜만 지정하면 하한은 하루 시작, 상한은 하루 끝으로 확장합니다. Instant·오프셋 변환에는 [애플리케이션 시간대 규칙](/ko/auto-configuration.md#시간대-결정-순서)을 적용합니다.

BETWEEN은 양쪽 경계를 포함하며, 끝을 제외하는 `TimeBucketCounter` 기간과 다릅니다. 데이터베이스 시간 정밀도에 따라 하루 끝의 나노초가 반올림될 수 있습니다. 연속된 보고 기간에는 시작 포함·끝 제외 비교나 집계 API를 사용하고 실제 저장 정밀도를 검사하세요.

## 논리 그룹과 제한

첫 노드로 초기 결과를 정하고, 다음 노드를 `and`·`or`로 왼쪽부터 결합합니다. 중첩 그룹은 괄호에 해당합니다. 알 수 없는 논리 연산자는 거부합니다. 그룹의 연결 연산자는 자식이 하나일 때도 그룹 전체에 적용합니다.

조건·그룹 노드는 전체 1,000개, 중첩 그룹은 32단계, 조건별 컬렉션 값은 1,000개, 정렬 필드는 16개까지 허용합니다. 페이지 제한은 별도로 설정할 수 있습니다. [기본 사용법](/ko/basic-usage.md)과 [자동 설정](/ko/auto-configuration.md)을 참조하세요.
