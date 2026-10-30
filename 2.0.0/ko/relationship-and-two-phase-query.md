# 관계형 데이터와 2단계 쿼리

검색 조인은 조건에 맞는 루트를 선택하고, fetch 조인은 반환할 루트의 연관 데이터를 초기화합니다. 두 작업의 목적은 서로 다릅니다. 이 문서는 `2.0.0` 기준입니다.

## 연관 필드 공개

```java
import dev.simplecore.searchable.core.annotation.SearchableField;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;

public class AuthorSearchDTO {
    @SearchableField(entityField = "author.name", operators = {CONTAINS}, sortable = true,
            sortField = "author.name")
    private String authorName;
    public String getAuthorName() { return authorName; }
}
```

실제 엔티티에 `author` 관계가 있고 그 대상에 `name` 속성이 있어야 합니다. 클라이언트는 `authorName.contains=Kim` 또는 `sort=authorName.asc`를 사용합니다. 임의의 엔티티 경로를 선택하지 않으며, DTO 어노테이션으로 허용 범위를 정합니다.

## 관계별 동작

| 관계 | 루트 행에 미치는 영향 | 로딩 동작 |
| --- | --- | --- |
| ManyToOne / OneToOne | 루트당 최대 한 대상 | 루트의 직접 to-one 경로를 자동 fetch 대상으로 고려합니다. |
| OneToMany / ManyToMany | 루트 SQL 행이 늘어날 수 있음 | 검색·정렬에는 조인을 사용하고, to-many 경로가 있으면 ID로 페이지를 나눕니다. |
| 직접 컬렉션 내부의 to-one | 컬렉션을 통과해야 함 | 호환성을 위해 기본적으로 탐지하며, 설정으로 끌 수 있습니다. |
| 명시적 중첩 fetch | 경로에 따라 다름 | 엔티티 로딩에 적용하고 건수·투영용 쿼리에는 적용하지 않습니다. |

자동 탐지는 직접 to-one 속성과 직접 to-many 대상의 to-one 속성을 확인합니다. 연관 그래프 전체를 무제한으로 재귀 탐색하지 않습니다. 선택하지 않은 지연 로딩 관계에 나중에 접근하면 추가 쿼리가 실행될 수 있습니다.

## 명시적 Fetch Join (fetchFields)

```java
import java.util.Set;

condition.setFetchFields(Set.of("author", "comments.author", "tags"));
```

서버 코드에서 엔티티 관계 경로를 지정합니다. `fetchFields`는 JSON 바인딩에서 제외합니다. 응답에 필요한 경로만 지정하고, 요청에서 경로를 선택하게 한다면 애플리케이션 허용 목록으로 제한하세요. 자식 필드의 검색 조건은 루트를 선택합니다. 이후 fetch에서는 조건에 맞은 자식만이 아니라 선택된 루트의 전체 자식이 로딩될 수 있습니다.

서비스의 읽기 트랜잭션 안에서는 `comments`와 `tags`처럼 첫 컬렉션 경로가 독립적인 경우 각각 조회합니다. 영속성 컨텍스트가 같은 루트 인스턴스에 결과를 반영합니다. 첫 컬렉션 경로가 같은 fetch는 함께 조회합니다. 중첩된 여러 bag이나 복잡한 매핑 제약에는 별도의 애플리케이션 쿼리가 필요할 수 있습니다.

## 로딩과 트랜잭션 경계

`findAllWithSearch`와 `findFirstWithSearch`는 기존 트랜잭션을 사용하거나 읽기 전용 트랜잭션을 시작합니다. 읽기 전용 설정이 반복 가능한 읽기 격리 수준을 보장하지는 않습니다. 경계 안에서 초기화하지 않은 연관 데이터는 엔티티 분리 후 접근할 때 오류가 발생할 수 있습니다. OSIV는 애플리케이션에서 결정하며 라이브러리가 켜지 않습니다.

중첩 탐지로만 포함되는 컬렉션의 자동 로딩을 줄이려면 다음과 같이 설정하세요.

```yaml
searchable:
  hibernate:
    auto-fetch-collections: false
```

명시적 fetch는 계속 적용합니다. JPA의 `EAGER`, Hibernate 배치 로딩과 직렬화 과정에서 접근하는 연관 데이터는 각각의 규칙을 따릅니다. 모든 N+1 쿼리가 사라진다고 보장하지 않습니다.

## 복합 식별자

`@IdClass`에서는 여러 ID 속성을 사용합니다. `@EmbeddedId`에서는 내장 속성과 구성 요소의 이름을 확인합니다. ID 페이지 조회와 fetch 조건에서 구성 요소별 동등 비교를 유지합니다. 건수는 구분자로 연결한 키가 아닌 루트 조건으로 계산합니다. ID 클래스의 `equals`·`hashCode`를 포함한 JPA 식별자 요건을 지키세요.

## 운영 성능 확인

직렬화까지 포함한 실제 응답의 SQL을 확인하세요. SELECT 수, 컬렉션 크기, 외래키·검색·정렬 열의 인덱스를 점검합니다. 양방향 엔티티 그래프를 JSON으로 그대로 반환하기보다 애플리케이션 응답 DTO로 변환하세요. 필요한 열만 조회하거나 데이터베이스 전용 집계를 수행하려면 전용 Repository 메서드를 사용하세요.

직접 조회와 ID 우선 조회, 그룹 정렬의 의미는 [쿼리 최적화](/ko/two-phase-query-optimization.md), 배치 설정은 [자동 설정](/ko/auto-configuration.md)을 참조하세요.
