# OpenAPI 통합

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: 2단계 쿼리 최적화](two-phase-query-optimization.md) | [다음: API 레퍼런스](api-reference.md)

---

Searchable JPA는 OpenAPI 3.0 및 Swagger UI와의 완벽한 통합을 제공합니다. 검색 API에 대한 문서를 자동으로 생성하고, 인터랙티브한 API 테스트 환경을 제공합니다.

## 설정

### 1. 의존성 추가

```gradle
dependencies {
    // Searchable JPA 스타터 (OpenAPI 기능 포함)
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:1.0.0-SNAPSHOT'

    // SpringDoc OpenAPI (Spring Boot 3.x 버전)
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
}
```

### 2. OpenAPI 설정

```java
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Post Search API")
                .version("1.0")
                .description("Searchable JPA를 사용한 게시글 검색 API")
            )
            .servers(List.of(
                new Server().url("http://localhost:8080").description("개발 서버"),
                new Server().url("https://api.example.com").description("운영 서버")
            ));
    }
}
```

### 3. 자동 설정 활성화

```yaml
# application.yml
springdoc:
  api-docs:
    enabled: true
    path: /api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    try-it-out-enabled: true
    operations-sorter: method
    tags-sorter: alpha
    
# Searchable JPA Swagger 설정
searchable:
  swagger:
    enabled: true
```

## @SearchableParams 어노테이션

`@SearchableParams` 어노테이션을 사용하여 GET 방식 검색 파라미터에 대한 OpenAPI 문서를 자동 생성할 수 있습니다.

### 기본 사용법

```java
@RestController
@RequestMapping("/api/posts")
public class PostController {
    
    @Operation(summary = "게시글 검색", description = "다양한 조건으로 게시글을 검색합니다")
    @GetMapping("/search")
    public Page<Post> searchPosts(
        @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params
    ) {
        SearchCondition<PostSearchDTO> condition = 
            new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
        return postService.findAllWithSearch(condition);
    }
}
```

### 생성되는 문서

위 코드는 다음과 같은 OpenAPI 문서를 자동 생성합니다:

```yaml
/api/posts/search:
  get:
    summary: 게시글 검색
    description: 다양한 조건으로 게시글을 검색합니다
    parameters:
      - name: id.equals
        in: query
        description: Post id (equals)
        required: false
        schema:
          type: integer
          format: int64
          example: 1
      - name: searchTitle.equals
        in: query
        description: Post title to search (equals)
        required: false
        schema:
          type: string
          maxLength: 100
          example: "Welcome to my blog"
      - name: searchTitle.contains
        in: query
        description: Post title to search (contains)
        required: false
        schema:
          type: string
          maxLength: 100
          example: "Spring"
      - name: status.equals
        in: query
        description: Post status (equals)
        required: false
        schema:
          type: string
          enum: [PUBLISHED, DRAFT, DELETED]
          example: "PUBLISHED"
      - name: viewCount.greaterThan
        in: query
        description: Number of views (greater than)
        required: false
        schema:
          type: integer
          format: int64
          example: 100
      - name: createdAt.between
        in: query
        description: Post creation date and time (between)
        required: false
        schema:
          type: string
          format: date-time
          example: "2024-01-01T00:00:00,2024-12-31T23:59:59"
      - name: sort
        in: query
        description: Sort criteria
        required: false
        schema:
          type: array
          items:
            type: string
            example: "createdAt,desc"
      - name: page
        in: query
        description: Page number (0-based)
        required: false
        schema:
          type: integer
          minimum: 0
          example: 0
      - name: size
        in: query
        description: Page size
        required: false
        schema:
          type: integer
          minimum: 1
          maximum: 100
          example: 10
```

## 고급 문서화

### 1. 상세한 필드 문서화

```java
public class PostSearchDTO {
    
    @Schema(
        description = "게시글 ID",
        example = "1",
        minimum = "1"
    )
    @SearchableField(operators = {EQUALS})
    private Long id;
    
    @Schema(
        description = "검색할 게시글 제목",
        example = "Spring Boot Tutorial",
        maxLength = 100
    )
    @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다")
    @SearchableField(operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true)
    private String title;
    
    @Schema(
        description = "게시글 상태",
        example = "PUBLISHED",
        allowableValues = {"PUBLISHED", "DRAFT", "DELETED"}
    )
    @SearchableField(operators = {EQUALS, NOT_EQUALS, IN, NOT_IN})
    private PostStatus status;
    
    @Schema(
        description = "조회수 범위",
        example = "100",
        minimum = "0"
    )
    @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN})
    private Long viewCount;
}
```

### 2. 커스텀 예제 생성

```java
@Configuration
public class CustomOpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Searchable JPA API")
                .version("1.0.0")
                .description("Searchable JPA를 사용한 검색 API"))
            .addServersItem(new Server().url("http://localhost:8080"));
    }

    // 추가적인 커스터마이징이 필요한 경우
    @Bean
    public OperationCustomizer customOperationCustomizer() {
        return (operation, handlerMethod) -> {
            // 커스텀 작업 추가 로직
            return operation;
        };
    }
}
```

## POST 방식 검색 문서화

### SearchCondition 스키마

```java
@RestController
public class PostController {
    
    @Operation(
        summary = "게시글 검색 (POST)",
        description = "JSON 형태의 복잡한 검색 조건으로 게시글을 검색합니다"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        description = "검색 조건",
        required = true,
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = SearchCondition.class),
            examples = {
                @ExampleObject(
                    name = "기본 검색",
                    summary = "제목과 상태로 검색",
                    value = """
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
                              "field": "status",
                              "searchOperator": "equals",
                              "value": "PUBLISHED"
                            }
                          ],
                          "sort": {
                            "orders": [
                              {
                                "field": "createdAt",
                                "direction": "desc"
                              }
                            ]
                          },
                          "page": 0,
                          "size": 10
                        }
                        """
                ),
                @ExampleObject(
                    name = "복합 조건 검색",
                    summary = "OR 조건을 포함한 복합 검색",
                    value = """
                        {
                          "conditions": [
                            {
                              "operator": "and",
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
                            },
                            {
                              "operator": "and",
                              "field": "viewCount",
                              "searchOperator": "greaterThan",
                              "value": 100
                            }
                          ],
                          "sort": {
                            "orders": [
                              {
                                "field": "viewCount",
                                "direction": "desc"
                              },
                              {
                                "field": "createdAt",
                                "direction": "desc"
                              }
                            ]
                          },
                          "page": 0,
                          "size": 20
                        }
                        """
                )
            }
        )
    )
    @PostMapping("/search")
    public Page<Post> searchPosts(
        @RequestBody @Validated SearchCondition<PostSearchDTO> searchCondition
    ) {
        return postService.findAllWithSearch(searchCondition);
    }
}
```

## 응답 스키마 문서화

### 페이징 응답

```java
@Schema(description = "페이징된 게시글 검색 결과")
public class PostPageResponse {
    
    @Schema(description = "게시글 목록")
    private List<Post> content;
    
    @Schema(description = "페이지 정보")
    private PageInfo pageable;
    
    @Schema(description = "전체 요소 수")
    private long totalElements;
    
    @Schema(description = "전체 페이지 수")
    private int totalPages;
    
    @Schema(description = "현재 페이지가 마지막 페이지인지 여부")
    private boolean last;
    
    @Schema(description = "현재 페이지 요소 수")
    private int numberOfElements;
}
```

### 2단계 쿼리 페이징 응답

```java
@Schema(description = "2단계 쿼리 최적화 페이징 응답")
public class CursorPageResponse<T> {
    
    @Schema(description = "현재 페이지 데이터")
    private List<T> content;
    
    @Schema(description = "다음 페이지 토큰", example = "eyJjcmVhdGVkQXQiOiIyMDI0LTAxLTE0VDE1OjIwOjAwIiwiaWQiOjk5fQ==")
    private String nextCursor;
    
    @Schema(description = "이전 페이지 토큰")
    private String previousCursor;
    
    @Schema(description = "다음 페이지 존재 여부")
    private boolean hasNext;
    
    @Schema(description = "이전 페이지 존재 여부")
    private boolean hasPrevious;
}
```

## 에러 응답 문서화

```java
@Schema(description = "API 에러 응답")
public class ErrorResponse {
    
    @Schema(description = "에러 코드", example = "VALIDATION_ERROR")
    private String code;
    
    @Schema(description = "에러 메시지", example = "검색 조건이 올바르지 않습니다")
    private String message;
    
    @Schema(description = "상세 에러 정보")
    private List<FieldError> errors;
    
    @Schema(description = "요청 시각")
    private LocalDateTime timestamp;
}

@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "검색 성공",
        content = @Content(schema = @Schema(implementation = PostPageResponse.class))
    ),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 요청",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    ),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
})
@PostMapping("/search")
public Page<Post> searchPosts(@RequestBody SearchCondition<PostSearchDTO> condition) {
    return postService.findAllWithSearch(condition);
}
```

## 태그와 그룹화

```java
@RestController
@RequestMapping("/api/posts")
@Tag(name = "게시글 검색", description = "게시글 검색 관련 API")
public class PostController {
    
    @Operation(
        summary = "게시글 검색 (GET)",
        description = "쿼리 파라미터를 사용한 게시글 검색",
        tags = {"게시글 검색", "GET 방식"}
    )
    @GetMapping("/search")
    public Page<Post> searchPostsGet(/* ... */) {
        // ...
    }
    
    @Operation(
        summary = "게시글 검색 (POST)",
        description = "JSON 바디를 사용한 게시글 검색",
        tags = {"게시글 검색", "POST 방식"}
    )
    @PostMapping("/search")
    public Page<Post> searchPostsPost(/* ... */) {
        // ...
    }
    
    @Operation(
        summary = "2단계 쿼리 검색",
        description = "대용량 데이터를 위한 2단계 쿼리 최적화 검색",
        tags = {"게시글 검색", "2단계 쿼리"}
    )
    @GetMapping("/cursor-search")
    public Page<Post> searchPosts(/* ... */) {
        // ...
    }
}
```

## 보안 문서화

```java
@SecurityRequirement(name = "bearerAuth")
@Operation(
    summary = "관리자 게시글 검색",
    description = "관리자 권한이 필요한 게시글 검색"
)
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/search")
public Page<Post> adminSearch(/* ... */) {
    // ...
}

// OpenAPI 설정에서 보안 스키마 정의
@Bean
public OpenAPI secureOpenAPI() {
    return new OpenAPI()
        .components(new Components()
            .addSecuritySchemes("bearerAuth", 
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            )
        );
}
```

## 실제 사용 예제

### Swagger UI에서 테스트

1. **기본 검색 테스트**
   ```
   GET /api/posts/search?title.contains=Spring&status.equals=PUBLISHED&page=0&size=10
   ```

2. **복합 조건 검색 테스트**
   ```json
   POST /api/posts/search
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
     ],
     "sort": {
       "orders": [
         {
           "field": "createdAt",
           "direction": "desc"
         }
       ]
     },
     "page": 0,
     "size": 10
   }
   ```

3. **2단계 쿼리 최적화 테스트**
   ```
   GET /api/posts/cursor-search?sort=createdAt,desc&size=10
   GET /api/posts/cursor-search?cursor=eyJjcmVhdGVkQXQiOiIyMDI0LTAxLTE0VDE1OjIwOjAwIiwiaWQiOjk5fQ==&size=10
   ```

## 문서 접근

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/api-docs`
- **OpenAPI YAML**: `http://localhost:8080/api-docs.yaml`

## 프로덕션 고려사항

### 1. 보안 설정

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false  # 프로덕션에서는 비활성화
  swagger-ui:
    enabled: false  # 프로덕션에서는 비활성화
```

### 2. 문서 최적화

```java
@Profile("!prod")
@Configuration
public class OpenApiConfig {
    // 개발/테스트 환경에서만 활성화
}
```

## 다음 단계

- [API 레퍼런스](api-reference.md) - 전체 API 문서
- [FAQ](faq.md) - 자주 묻는 질문들

---

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: 2단계 쿼리 최적화](two-phase-query-optimization.md) | [다음: API 레퍼런스](api-reference.md) 