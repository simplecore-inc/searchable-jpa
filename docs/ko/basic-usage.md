# 기본 사용법

이 예제는 `2.0.0` starter 기준입니다. public 클래스마다 파일을 나누고, 애플리케이션의 컴포넌트·엔티티 탐색 경로에 배치하세요. 데이터소스와 일반 `@SpringBootApplication` 설정도 필요합니다. 문자열로 지정하는 필드 경로는 Java 컴파일 시점이 아닌 실행 시점에 검증합니다.

## 엔티티

```java
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class Post {
    @Id @GeneratedValue
    private Long id;
    private String title;
    private String status;
    private Long viewCount;
    private Instant createdAt;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public Long getViewCount() { return viewCount; }
    public Instant getCreatedAt() { return createdAt; }
}
```

조회용 예제입니다. 애플리케이션에 필요한 생성자, 변경 메서드와 감사 필드는 별도로 정의하세요. 기본 인자 없는 생성자는 JPA 요건을 충족합니다.

## 검색 DTO

검색 DTO에는 클라이언트가 검색·정렬할 수 있는 필드를 선언합니다. 상속한 검색 필드를 포함해 JavaBean getter를 제공하세요. DTO는 검증용 정의이며, 클라이언트가 필드를 채운 DTO 인스턴스를 제출하는 방식이 아닙니다.

```java
import dev.simplecore.searchable.core.annotation.SearchableField;
import java.time.Instant;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.*;

public class PostSearchDTO {
    @SearchableField(operators = {EQUALS, GREATER_THAN}, sortable = true)
    private Long id;
    @SearchableField(operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true)
    private String title;
    @SearchableField(operators = {EQUALS, IN})
    private String status;
    @SearchableField(operators = {GREATER_THAN_OR_EQUAL_TO, BETWEEN}, sortable = true)
    private Long viewCount;
    @SearchableField(operators = {BETWEEN, GREATER_THAN, LESS_THAN}, sortable = true)
    private Instant createdAt;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public Long getViewCount() { return viewCount; }
    public Instant getCreatedAt() { return createdAt; }
}
```

`operators`를 생략하면 값과 타입 검증을 통과하는 모든 연산자를 허용합니다. 공개 API에는 허용할 연산자를 명시하는 것이 좋습니다. `sortable`의 기본값은 false입니다. `entityField`는 DTO 필드를 엔티티 속성 경로에 연결하고, `sortField`는 정렬 경로만 별도로 지정합니다. [검색 연산자](./search-operators.md)에서 상세 규칙을 확인하세요.

## Repository와 서비스

```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
}
```

```java
import dev.simplecore.searchable.core.service.DefaultSearchableService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    public PostService(PostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

Repository는 두 인터페이스를 모두 상속해야 합니다. 서비스가 이미 다른 기본 클래스를 상속한다면 `SearchableServiceSupport`와 `SearchableServiceDelegate`를 사용하세요. [고급 기능](./advanced-features.md)에 예제가 있습니다.

## GET·POST 컨트롤러

```java
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.openapi.annotation.SearchableParams;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostService service;
    public PostController(PostService service) { this.service = service; }

    @GetMapping("/search")
    public Page<Post> search(@RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params) {
        return service.findAllWithSearch(new SearchableParamsParser<>(PostSearchDTO.class).convert(params));
    }

    @PostMapping("/search")
    public Page<Post> search(@RequestBody SearchCondition<PostSearchDTO> condition) {
        return service.findAllWithSearch(condition);
    }
}
```

`@SearchableParams`는 Map의 API 문서를 생성합니다. 실제 변환은 예제의 파서 호출에서 수행합니다. JSON 요청 본문에는 DTO 타입 인자를 유지하세요. Jackson 2와 Jackson 3에 같은 DTO 검증·매핑 규칙을 적용합니다. 외부 입력을 DTO 타입 없는 검색 조건으로 받지 마세요.

```bash
curl --get 'http://localhost:8080/api/posts/search' \
  --data-urlencode 'title.contains=Spring' \
  --data-urlencode 'sort=createdAt.desc,id.asc' \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=20'
```

GET 검색 조건은 AND로 연결합니다. 정렬 문법은 `field.asc,otherField.desc`입니다. `field,desc`는 올바른 문법이 아닙니다. OR나 중첩 그룹에는 JSON 본문을 사용하세요.

```json
{
  "conditions": [
    {"field": "title", "searchOperator": "contains", "value": "Spring"},
    {"operator": "and", "field": "status", "searchOperator": "in", "value": ["PUBLISHED", "DRAFT"]}
  ],
  "sort": {"orders": [{"field": "createdAt", "direction": "desc"}]},
  "page": 0,
  "size": 20
}
```

각 노드의 `operator`는 그 노드를 앞에서 계산한 결과에 연결합니다. 각 목록의 첫 노드에는 연결할 이전 결과가 없습니다. BETWEEN은 `value`와 `value2`, IN은 배열을 사용합니다. 클라이언트가 `entityField`나 `fetchFields`로 매핑·로딩 경로를 지정할 수는 없습니다.

## 서버의 그룹 구성

아래 조건은 `status = PUBLISHED AND (title에 Spring 포함 OR title에 JPA 포함)`을 뜻합니다. 외부 조건에 결합해도 내부 그룹의 범위를 유지합니다.

```java
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;

SearchCondition<PostSearchDTO> condition = SearchConditionBuilder.create(PostSearchDTO.class)
        .where(w -> w.equals("status", "PUBLISHED"))
        .and(g -> g.contains("title", "Spring").orContains("title", "JPA"))
        .sort(s -> s.desc("createdAt").asc("id"))
        .page(0).size(20)
        .build();
```

같은 목록의 연결 연산자는 왼쪽부터 평가합니다. SQL의 암묵적인 AND 우선순위를 적용하지 않습니다. 의도한 괄호마다 중첩 그룹을 명시하세요. `SearchConditionBuilder.from(existing, DTO.class)`는 구조를 복사한 뒤 확장합니다.

## 페이지와 응답

페이지를 생략하면 페이지 0과 설정한 기본 크기 20을 사용합니다. 페이지 크기는 양수이며, 설정한 최대 크기 이하여야 합니다. 기본 최대 크기는 1,000입니다. 정렬값이 같은 경우의 순서를 결정하도록 필요한 기본키 정렬을 추가합니다. 페이지 내용과 전체 건수는 조인된 자식 행이 아닌 검색 대상 루트 엔티티 기준입니다.

서비스는 Spring Data의 `Page<T>`를 반환합니다. HTTP 응답 형식은 애플리케이션의 Spring Data·Jackson 설정에 따라 달라집니다. 공개 응답 형식을 일정하게 유지하려면 별도의 응답 DTO로 변환하세요. JPA 엔티티를 그대로 반환하면 필드가 노출되거나 지연 로딩이 실행될 수 있으므로 응답 모델을 용도에 맞게 정의하세요.

![검색 요청 처리 흐름](./_images/search-request-flow.svg)

## 오류 처리와 권한

DTO 검증에서는 허용하지 않은 필드·연산자·정렬과 잘못된 값을 거부합니다. `SearchableParseException`과 `SearchableValidationException`은 `SearchableException`을 상속한 런타임 예외입니다. 컨트롤러 예외 처리에서 적절한 클라이언트 오류로 변환하세요. 잘못된 JSON 본문은 일반적으로 Spring MVC의 메시지 변환 오류 처리로 전달합니다.

라이브러리가 테넌트나 행별 권한 조건을 자동 추가하지는 않습니다. 파싱 후 서버에서 필수 범위를 추가하고, 클라이언트가 제어하는 OR 그룹 밖에 유지하세요. 그룹 결합과 쓰기 동작은 [고급 기능](./advanced-features.md)을 참조하세요.
