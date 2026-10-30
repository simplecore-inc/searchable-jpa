# 고급 기능

`1.1.3`의 서비스 구성, 프로젝션, 검색 범위, 쓰기와 시간별 집계를 설명합니다. 예제는 [기본 사용법](./basic-usage.md)의 엔티티와 Repository를 사용합니다.

## 서비스 조합

기존 서비스의 상위 클래스를 유지해야 한다면 믹스인 인터페이스를 구현하고 델리게이트를 반환하세요.

```java
import dev.simplecore.searchable.core.service.SearchableServiceDelegate;
import dev.simplecore.searchable.core.service.SearchableServiceSupport;
import jakarta.persistence.EntityManager;

public class PostService implements SearchableServiceSupport<Post, Long> {
    private final SearchableServiceDelegate<Post, Long> delegate;

    public PostService(PostRepository repository, EntityManager entityManager) {
        delegate = new SearchableServiceDelegate<>(repository, entityManager, Post.class);
    }

    @Override
    public SearchableServiceDelegate<Post, Long> getSearchableDelegate() {
        return delegate;
    }
}
```

Spring 의존성 주입을 사용할 때는 서비스를 애플리케이션 빈으로 등록하세요. Repository와 같은 영속성 단위에 속한 Spring 관리 `EntityManager`를 사용합니다. 기존 `DefaultSearchableService` 상속 클래스도 내부에서 같은 델리게이트를 사용합니다.

## 프로젝션

```java
public interface PostSummary {
    Long getId();
    String getTitle();
}
```

```java
var page = service.findAllWithSearch(condition, PostSummary.class);
```

프로젝션 타입은 인터페이스여야 합니다. 일반 DTO 클래스를 전달하면 `SearchableConfigurationException`이 발생합니다. 프로젝션 프록시는 로딩한 엔티티를 사용하며 SQL SELECT 열을 줄이지 않습니다. 중첩·계산 getter는 연관 데이터에 접근할 수 있으므로 필요한 fetch와 애플리케이션 트랜잭션 범위를 정하세요.

## 검색 조건 확장과 필수 범위

```java
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;

var scoped = SearchConditionBuilder.from(clientCondition, PostSearchDTO.class)
        .and(g -> g.equals("status", "PUBLISHED"))
        .build();
```

예제는 클라이언트 검색을 공개 게시글로 제한합니다. 실제 테넌트·권한 조건에는 서버에서 결정한 값과 내부 작업에 허용된 DTO 필드를 사용하세요. 클라이언트가 제출한 테넌트 값을 그대로 신뢰하면 안 됩니다. 클라이언트의 전체 검색식 뒤에 필수 조건을 추가하고, 이후에 검색 범위를 넓히는 OR를 추가하지 마세요.

`from`은 노드·정렬·fetch 구조를 복사한 뒤 확장하며 원본 노드의 연결 연산자를 변경하지 않습니다. 임의의 가변 값 객체는 호출자가 변경하지 않도록 관리해야 합니다. 그룹 연결은 호출 순서대로 평가합니다. `A AND (B OR C)`의 그룹을 유지하며, `(A AND B) OR C`로 바꾸지 않습니다.

![그룹 평가](./_images/condition-group-evaluation.svg)

## 일괄 수정·삭제

```java
public class StatusPatch {
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

```java
StatusPatch patch = new StatusPatch();
patch.setStatus("ARCHIVED");
long changed = service.updateWithSearch(condition, patch);
long deleted = service.deleteWithSearch(condition);
```

수정과 삭제는 독립된 예제입니다. 수정한 값에 따라 이후 삭제 조건에 해당하는 행이 달라질 수 있습니다. 두 작업은 요청 페이지에 포함된 행만이 아니라 조건에 맞는 모든 엔티티를 처리합니다. 조건이 비어 있으면 전체 테이블을 수정·삭제하므로 쓰기 전에 애플리케이션 권한과 필수 범위를 적용해야 합니다.

수정 규칙은 다음과 같습니다.

- 원본 요청에 실제로 있고 null이 아닌 영속 속성만 복사합니다. 일부 속성만 가진 수정 DTO 때문에 무관한 엔티티 기본형 필드를 초기화하지 않습니다.
- 식별자, 내장 식별자, 버전, 감사 관리 필드, static·final·비영속 속성과 `updatable=false` 열은 제외합니다.
- 컬렉션 속성은 제외합니다. 컬렉션이 아닌 연관 관계의 변경 동작은 유지하므로 애플리케이션에서 권한을 검증해야 합니다.
- null은 기존 값 유지입니다. null로 변경하라는 별도 표시는 제공하지 않습니다. 값을 비울 때는 전용 애플리케이션 작업을 사용하세요.
- 엔티티 객체의 기본형 0·false는 실제 값으로 처리합니다. 생략과 기본값 입력을 구분해야 한다면 필요한 속성만 가진 DTO에 null 가능한 타입을 사용하세요.

기본키 순서로 200건씩 조회하고, 변경 전에 다음 조회용 키를 저장한 뒤 각 단위에서 flush합니다. 검색 조건에 사용한 값을 수정해도 오프셋 이동 때문에 다음 키를 빠뜨리지 않습니다. 삭제는 Repository의 엔티티 삭제를 사용하므로 JPA 콜백과 cascade를 유지합니다. 단일 일괄 SQL 명령으로 실행하는 API는 아닙니다.

기존 트랜잭션이 있으면 그대로 참여하고, 없으면 전체 작업용 트랜잭션을 시작합니다. 처리 단위마다 별도로 커밋하지 않습니다. 델리게이트가 트랜잭션을 시작한 경우에만 처리한 루트를 분리해 메모리를 줄입니다. 호출자 트랜잭션에서는 기존 영속 상태를 유지합니다. 큰 cascade나 컬렉션은 여전히 메모리를 사용할 수 있습니다. 동시 쓰기는 데이터베이스 격리 수준과 엔티티 버전 규칙을 따르므로 유지보수 작업에 필요한 동시 실행 제어를 애플리케이션에서 구성하세요.

## 중첩 경로와 복합키

`@SearchableField(entityField="author.name")`처럼 DTO 별칭으로 연관 경로를 공개할 수 있습니다. 정렬 경로는 `sortField`, `entityField`, DTO 필드명 순서로 결정합니다. `@IdClass`와 `@EmbeddedId`는 JPA 메타모델에서 확인하며, 내장 ID 속성명이 반드시 `id`라고 가정하지 않습니다. 키 기준 쓰기에는 비교 가능한 올바른 ID 구성 요소와 엔티티 식별 매핑이 필요합니다.

명시적 fetch 경로는 서버에서 `builder.fetchFields("author", "comments.author")` 또는 `condition.setFetchFields(...)`로 지정합니다. fetch를 지정해도 검색 권한이 추가되지는 않습니다. [관계형 데이터](./relationship-and-two-phase-query.md)를 참조하세요.

## 시간 구간별 건수 집계

구간(bucket)은 일정한 기간(time window)을 나눈 단위입니다. `TimeBucketCounter`는 검색 조건을 적용한 뒤 데이터베이스에서 집계합니다. starter에서는 빈을 주입할 수 있고, core만 사용하면 직접 생성할 수 있습니다.

```java
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import java.time.Instant;
import java.util.List;

TimeBucketCounter counter = new TimeBucketCounter(entityManager);
List<Long> counts = counter.count(
        Post.class, repository, new SearchCondition<>(), "createdAt",
        Instant.parse("2026-10-01T00:00:00Z"),
        Instant.parse("2026-11-01T00:00:00Z"), 31);
```

`timeAxisField`는 `Instant`로 매핑된 루트 엔티티 속성명입니다. DTO 별칭이나 임의의 중첩 경로가 아닙니다. 애플리케이션 입력으로 시간축을 선택한다면 허용할 속성을 검증하세요. `from < to`, 구간 수 1–512를 사용합니다. 결과는 오래된 구간부터 정렬하며, 0건인 구간을 포함해 항상 지정한 개수만큼 반환합니다.

전체 기간과 각 구간은 시작을 포함하고 끝을 제외합니다. `from`은 포함하고 `to`는 제외하며, 내부 경계에 있는 데이터는 뒤 구간에 포함합니다. 자식 행 여러 개가 검색 조건에 맞아도 루트 엔티티는 한 번만 셉니다. 시간값이 null인 행은 시간 조건에서 제외합니다. 페이지·정렬·fetch 설정으로 집계 대상 행을 선택하지 않습니다.

밀리초 경계에 맞고 구간 수로 나누어떨어지는 기간은 지원 방언에서 epoch 숫자식을 사용해 그룹화할 수 있습니다. 밀리초보다 세밀한 경계, 나누어떨어지지 않는 기간, 산술 범위 초과 가능성이 있는 기간, Oracle과 미등록 방언에는 조건부 합계를 사용합니다. 경계는 정수 나노초로 계산하지만 실제 저장 정밀도는 데이터베이스 열과 JDBC 매핑에 따릅니다. 저장할 때 손실된 정밀도를 더 세밀한 조회로 복원할 수는 없습니다.

숫자식은 PostgreSQL·CockroachDB, H2, MySQL·MariaDB, SQL Server와 정수 밀리초를 사용하는 SQLite 매핑에 등록합니다. 이는 구현 경로이며 모든 데이터베이스의 통합 테스트 통과를 뜻하지 않습니다. Oracle은 DATE 변환 시 소수 초가 사라지므로 조건부 합계를 사용합니다. SQLite의 사용자 정의 시간 매핑은 별도로 검증하세요.

시간축과 자주 쓰는 범위 조건에 인덱스를 구성하세요. 조건부 합계에서는 구간 수가 SQL 식의 크기에 영향을 줍니다. 실제 데이터베이스에서 대표 기간과 경계 시각을 검사하세요.

## 메시지

`MessageUtils`는 라이브러리의 `messages/message` 번들과 Spring 로케일 컨텍스트를 사용합니다. 애플리케이션은 HTTP 경계에서 오류를 번역하거나 자체 오류 응답 모델을 구성할 수 있습니다. 메시지 문자열은 고정 오류 코드가 아니므로 프로그램의 분기에는 예외 타입과 애플리케이션 응답 규칙을 사용하세요.
