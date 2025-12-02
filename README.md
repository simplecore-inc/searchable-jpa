# Searchable JPA

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green.svg)](https://spring.io/projects/spring-boot)
[![Jakarta EE](https://img.shields.io/badge/Jakarta%20EE-9%2B-blue.svg)](https://jakarta.ee/)
[![License](https://img.shields.io/badge/License-SimpleCORE%201.0-blue.svg)](LICENSE)

Searchable JPA is a library that extends Spring Data JPA to provide dynamic search, sorting, and pagination functionality. It allows complex search conditions to be implemented using simple annotations and builder patterns, and supports high-performance cursor-based pagination even for large datasets.

## Version Compatibility

### Version-Specific Spring Boot Support

| Library Version | Spring Boot Version | Jakarta EE | Status |
|----------------|---------------------|------------|--------|
| `0.1.x` | `2.7.x` | javax.* | Deprecated |
| `1.0.0+` | `3.2.x+` | jakarta.* | Latest |

### Important: Version Compatibility Notice

- **1.0.0+ versions**: Only supports Spring Boot 3.2.x+ (Jakarta EE 9+)
- **0.1.x versions**: Only supports Spring Boot 2.7.x (uses javax.* packages)
- **No mixing with lower versions**: Using different versions simultaneously may cause classpath conflicts

### Spring Boot 2.x → 3.x Migration

When upgrading to Spring Boot 3.x, the following changes are required:

1. **Dependency Version Changes**:
   ```gradle
   // 0.1.x version (Spring Boot 2.x)
   implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:0.1.x'

   // 1.0.0+ version (Spring Boot 3.x)
   implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:1.0.0+'
   ```

2. **Jakarta EE Migration**:
   - Change all `javax.*` imports to `jakarta.*`
   - Update JPA-related imports in application code if they are used directly

## System Requirements

- **Java**: 17+
- **Spring Boot**: 3.2.5+ (1.0.0+ versions)
- **Spring Boot**: 2.7.x (0.1.x versions)
- **Jakarta EE**: 9+ (1.0.0+ versions)
- **javax.* support**: 2.7.x and below (0.1.x versions)

## Spring Boot 3.2.5 Migration Completed


## Key Features

- **Dynamic Search**: Supports 20+ search operators (EQUALS, CONTAINS, BETWEEN, etc.)
- **Flexible Sorting**: Multi-field sorting and dynamic sort conditions
- **High-Performance Pagination**: Cursor-based pagination for large dataset processing
- **Type Safety**: Compile-time validation and type-safe builder patterns
- **OpenAPI Integration**: Automatic Swagger documentation generation
- **Multiple Data Types**: Support for strings, numbers, dates, enums, and nested objects

## Quick Start

### 1. Add Dependency

```gradle
implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:1.0.0-SNAPSHOT'
```

### 2. Application Configuration (Optional)

Configure `application.yml` for library usage:

```yaml
# Searchable JPA Configuration (All optional, defaults exist)
searchable:
  swagger:
    enabled: true                    # Enable automatic OpenAPI documentation generation (default: true)
  max-page-size: 1000               # Maximum page size (default: 1000)
  default-page-size: 20             # Default page size (default: 20)
```

### 3. Define DTO Class

```java
public class PostSearchDTO {
    @SearchableField(operators = {EQUALS, CONTAINS}, sortable = true)
    private String title;

    @SearchableField(operators = {EQUALS}, sortable = true)
    private PostStatus status;

    @SearchableField(operators = {GREATER_THAN, LESS_THAN}, sortable = true)
    private LocalDateTime createdAt;
}
```

### 4. Implement Service Class

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    public PostService(PostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

### 5. Use in Controller

```java
@RestController
public class PostController {
    @GetMapping("/api/posts/search")
    public Page<Post> searchPosts(
        @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params
    ) {
        SearchCondition<PostSearchDTO> condition =
            new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
        return postService.findAllWithSearch(condition);
    }
}
```

### 6. API Call

```bash
# Search posts containing "Spring" in title
GET /api/posts/search?title.contains=Spring&sort=createdAt,desc&page=0&size=10
```

## Documentation

### Korean Documentation
- [Installation Guide](docs/ko/installation.md) - System requirements and installation instructions
- [Basic Usage](docs/ko/basic-usage.md) - Basic usage methods and examples
- [Search Operators](docs/ko/search-operators.md) - All supported search operators
- [Advanced Features](docs/ko/advanced-features.md) - Complex search conditions and advanced features
- [Two-Phase Query Optimization](docs/ko/two-phase-query-optimization.md) - High-performance cursor-based pagination
- [Relational Data and Two-Phase Query](docs/ko/relationship-and-two-phase-query.md) - JPA relationship mapping and N+1 problem resolution
- [Auto Configuration](docs/ko/auto-configuration.md) - Spring Boot auto-configuration settings
- [OpenAPI Integration](docs/ko/openapi-integration.md) - Automatic Swagger documentation generation
- [API Reference](docs/ko/api-reference.md) - Complete API documentation
- [FAQ](docs/ko/faq.md) - Frequently asked questions and troubleshooting

### English Documentation
*Coming Soon* - English documentation is currently being prepared.




## License

This project is distributed under the [SimpleCORE License 1.0](LICENSE).

---

Try implementing easier and faster search functionality with **Searchable JPA**! 