# API 레퍼런스

`2.0.0`의 애플리케이션용 공개 API입니다. 아래 패키지의 타입을 import한 뒤 간단한 타입명으로 사용하세요. 내부 쿼리 실행기는 애플리케이션 확장 규약이 아닌 구현 세부 사항입니다.

## 어노테이션

`SearchableField`는 `dev.simplecore.searchable.core.annotation` 패키지에 있습니다. 필드에 적용하며 런타임에 유지합니다.

| 속성 | 타입 | 기본값 | 의미 |
| --- | --- | --- | --- |
| `entityField` | `String` | `""` | 엔티티 검색 경로입니다. 빈 값이면 DTO 필드명을 사용합니다. |
| `operators` | `SearchOperator[]` | `{}` | 빈 배열이면 타입·값 검증을 통과하는 모든 연산자를 허용합니다. |
| `sortable` | `boolean` | false | 해당 DTO 필드의 정렬 입력 허용 여부입니다. |
| `sortField` | `String` | `""` | 정렬 경로입니다. 생략하면 entityField, DTO 필드명 순으로 확인합니다. |

`SearchableParams`는 `dev.simplecore.searchable.openapi.annotation` 패키지에 있습니다. 필수 `Class<?> value()`로 컨트롤러 파라미터의 OpenAPI 문서용 DTO를 지정합니다. 요청 변환은 수행하지 않습니다.

## SearchCondition<D>

패키지는 `dev.simplecore.searchable.core.condition`입니다.

| API | 동작 |
| --- | --- |
| `new SearchCondition<>()` | 빈 노드 목록과 fetch 집합을 생성합니다. 페이지는 미지정 상태일 수 있습니다. |
| `new SearchCondition<>(nodes, page, size)` | 조건을 초기화합니다. 깊은 복사를 보장하지 않습니다. |
| `getNodes()` | 순서가 있는 노드 목록입니다. JSON 속성명은 `conditions`입니다. |
| `getPage()` / `setPage(Integer)` | null 가능한 0부터 시작하는 페이지 번호입니다. |
| `getSize()` / `setSize(Integer)` | null 가능한 요청 크기입니다. |
| `getSort()` / `setSort(Sort)` | 정렬 정의입니다. |
| `getFetchFields()` / `setFetchFields(Set<String>)` | 서버 전용 엔티티 fetch 경로입니다. |
| `fromJson(String, Class<D>)` | 타입을 지정해 Jackson 2로 변환하고 DTO를 검증합니다. |
| `toJson()` | 라이브러리 매퍼로 들여쓰기한 JSON을 생성합니다. |

JSON 편의 메서드는 Jackson 2의 `JsonProcessingException` 시그니처를 유지합니다. 검증 오류에는 라이브러리 전용 예외 타입을 사용합니다. 타입을 지정한 Jackson 3 본문 변환도 어댑터를 통해 같은 검증을 수행합니다. 두 세대 모두 문서에 명시한 소문자·camelCase 연산자 이름을 사용합니다. `fetchFields`는 JSON 입출력에서 제외하며, 제출된 `entityField` 값 대신 DTO의 매핑 경로를 적용합니다.

중첩 타입은 다음과 같습니다.

- `Node`: 연결 연산자의 `getOperator`·`setOperator`를 제공합니다.
- `Condition`: 생성자는 `(LogicalOperator, String field, SearchOperator, Object value, Object value2, String entityField)`입니다. 조건값 getter와 연결 연산자·엔티티 경로 setter를 제공합니다.
- `Group`: 빈 생성자 또는 `(LogicalOperator, List<Node>)`로 생성합니다. 자식 순서와 외부 연결 연산자를 보관합니다.
- `Sort`: 순서가 있는 `Order` 목록과 `addOrder(Order)`를 제공합니다.
- `Order`: `(String field, Direction)` 또는 `(String field, Direction, String entityField)`로 생성합니다. `isAscending()`의 직렬화 결과는 호환성을 위해 유지하며 입력에서는 무시합니다.
- `Direction`: `ASC`·`DESC`이며 JSON 값은 `asc`·`desc`입니다.

## 빌더

| API | 용도 |
| --- | --- |
| `SearchConditionBuilder.create(DTO.class)` | DTO 검증을 사용하는 구성 시작 |
| `SearchConditionBuilder.from(existing, DTO.class)` | 구조를 복사한 뒤 조건 확장 |
| `where(Consumer<ConditionGroupBuilder>)` | 선언한 순서로 초기 조건 추가 |
| `and(...)` / `or(...)` | 앞의 전체 식에 그룹 결합 |
| `sort(Consumer<SortBuilder>)` | `asc(field)`·`desc(field)`로 정렬 지정 |
| `page(int)` / `size(int)` | 페이지 지정 |
| `fetchFields(String...)` / `fetchFields(Set<String>)` | 서버 fetch 경로 추가 |
| `build()` | 구조·DTO 제약 검증 후 조건 반환 |

정렬·페이지·크기·fetch 메서드는 `ChainedSearchCondition<D>`를 반환해 연속 호출을 유지합니다. `FirstCondition`과 `ChainedCondition`은 [검색 연산자](/ko/search-operators.md)의 18개 연산자를 제공합니다. 후자는 대응하는 `or...` 메서드와 중첩 `and`·`or` 소비자도 제공합니다. 범위 메서드는 두 경계값, IN 메서드는 컬렉션을 받습니다. NULL 검사는 필드명만 받습니다.

## URL 파서

`new SearchableParamsParser<D>(DTO.class).convert(Map<String,String>)`는 `field.operator=value`, `sort=field.asc,other.desc`, `page`, `size`를 받습니다. 검색 항목은 AND로 연결합니다. IN·NOT_IN·BETWEEN·NOT_BETWEEN에서만 쉼표로 값을 나눕니다. 일반 문자열 일치·포함 검색의 쉼표는 유지합니다. 단일 값 Map에는 같은 쿼리 키를 독립된 여러 조건으로 표현할 수 없습니다. 그룹이나 쉼표를 포함한 IN 원소에는 JSON을 사용하세요.

## 서비스 API

`SearchableService<T>`는 `dev.simplecore.searchable.core.service` 패키지에 있습니다.

| 메서드 | 반환 타입 | 의미 |
| --- | --- | --- |
| `findAllWithSearch(condition)` | `Page<T>` | 검색 루트의 페이지·정렬·fetch 조회 |
| `findAllWithSearch(condition, projectionInterface)` | `Page<P>` | 로딩한 엔티티의 인터페이스 프로젝션 |
| `findOneWithSearch(condition)` | `Optional<T>` | 0건이면 empty, 여러 건이면 유일성 오류 |
| `findFirstWithSearch(condition)` | `Optional<T>` | 정렬상 첫 루트, 요청 페이지 오프셋 무시, 전체 건수 조회 생략 |
| `countWithSearch(condition)` | `long` | 조건에 맞는 루트 건수 |
| `existsWithSearch(condition)` | `boolean` | 조건에 맞는 루트 존재 여부 |
| `updateWithSearch(condition, updateData)` | `long` | 전체 대상 수정, 보호 필드 제외, 200건 단위 처리 |
| `deleteWithSearch(condition)` | `long` | 전체 대상 삭제, 콜백·cascade 유지, 200건 단위 처리 |

`DefaultSearchableService<T,ID>` 생성자는 `(JpaRepository<T,ID>, EntityManager)`를 받고 구체 서비스의 타입에서 엔티티를 확인합니다. `SearchableServiceDelegate<T,ID>`는 여기에 `Class<T>`를 추가로 받습니다. Repository는 `JpaSpecificationExecutor<T>`도 구현해야 합니다. `SearchableServiceSupport<T,ID>`는 `getSearchableDelegate()`를 통해 서비스 기본 메서드를 제공합니다.

건수·존재·유일성·쓰기 작업은 요청 페이지로 결과를 제한하지 않지만 공통 조건 검증은 수행합니다. Spring Data가 `findOne`의 유일성 오류를 `IncorrectResultSizeDataAccessException`으로 변환할 수 있으므로 특정 제공자의 예외 포장 방식에 의존하지 마세요. 수정과 트랜잭션 규칙은 [고급 기능](/ko/advanced-features.md)에 있습니다.

## TimeBucketCounter

패키지는 `dev.simplecore.searchable.core.service.bucket`입니다. 메서드는 엔티티 타입, `JpaSpecificationExecutor<T>`, `SearchCondition<?>`, 시간축 필드명, 시작 `Instant`, 종료 `Instant`, 구간 수를 받아 `List<Long>`을 반환합니다.

`EntityManager`로 생성하거나 starter 빈을 주입합니다. `MAX_BUCKETS`는 512입니다. 루트 시간 속성은 `Instant`여야 하며 기간은 `[from,to)`입니다. 기간이나 구간 수가 잘못되면 `IllegalArgumentException`이 발생합니다. 결과 길이는 고정이며 루트 엔티티를 한 번씩 셉니다. [시간 구간 집계](/ko/advanced-features.md#시간-구간별-건수-집계)를 참조하세요.

## 예외와 검증

`SearchableException`은 `RuntimeException`을 상속합니다. 하위 타입은 `SearchableValidationException`, `SearchableParseException`, `SearchableConfigurationException`, `SearchableOperationException`, `SearchableJoinException`입니다. Jakarta Validation의 `ValidationException`은 상속하지 않습니다.

`SearchableFieldValidator`는 필드 어노테이션과 정렬 허용 여부를 검사합니다. `SearchConditionValidator`는 연산자와 값을 확인하고 변환한 뒤 검색 속성값에 Bean Validation을 적용합니다. 범위는 양쪽 경계를 검사합니다. 비어 있는 DTO 전체를 검증하거나 무관한 `@NotNull` 속성값을 요구하지 않습니다. NULL 검사 연산자는 입력값 제약 검증을 수행하지 않습니다. `SearchRequestLimits`는 [자동 설정](/ko/auto-configuration.md)에 명시한 구조·페이지 제한을 적용합니다.

## 유틸리티

| 타입 | 주요 기능 |
| --- | --- |
| `SearchableFieldUtils` | DTO 상속 필드, 검색·정렬 경로, 허용 연산자, 기본키·복합키 메타데이터, 조인 경로, `clearCache()` |
| `SearchableValueParser` | `parseValue(String, Class<?>)`, `parseValueForBetween(String, Class<?>, boolean isEndValue)` |
| `SearchableTimeZoneHolder` | 시간값 변환에서 공유하는 애플리케이션 시간대 |
| `MessageUtils` | 라이브러리 메시지의 로케일별 조회 |

애플리케이션 요청에는 빌더·파서·서비스 API를 우선 사용하세요. 노드 직접 생성과 메타데이터 유틸리티는 DTO 허용 목록 검증을 대신하지 않습니다.
