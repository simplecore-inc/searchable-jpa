English | [한국어](/ko/README.md)

# Searchable JPA

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-green.svg)](https://spring.io/projects/spring-boot)
[![Jakarta EE](https://img.shields.io/badge/Jakarta%20EE-9%2B-blue.svg)](https://jakarta.ee/)
[![License](https://img.shields.io/badge/License-SCL--1.0-blue.svg)](../../LICENSE)

Searchable JPA is a library that extends Spring Data JPA to provide dynamic search, sorting, and pagination.

> [!TIP]
> The `spring-boot-starter-searchable-jpa` dependency alone gives you access to every feature.

## Key Features

| Feature | Description |
|------|------|
| **Dynamic Search** | Supports 18 search operators (EQUALS, CONTAINS, BETWEEN, and more) |
| **Flexible Sorting** | Multi-field sorting and dynamic sort conditions |
| **High-Performance Pagination** | Two-phase query-optimized pagination for large datasets |
| **Type Safety** | Compile-time validation and a type-safe builder pattern |
| **OpenAPI Integration** | Automatic Swagger documentation generation |
| **Various Data Types** | Support for strings, numbers, dates, enums, and nested objects |
| **Time Bucket Counting** | Counts rows per time bucket in the database, under the same search condition as the list |

## Quick Start

### 1. Add the Dependency

```gradle
dependencies {
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:${version}'
}
```

### 2. Configure the GitHub Packages Repository

Searchable JPA is published to GitHub Packages. Add the following to `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/simplecore-inc/searchable-jpa")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
                password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

> [!IMPORTANT]
> Your GitHub token needs the `read:packages` scope. Generate one at [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens).

### 3. Default Configuration (Optional)

```yaml
searchable:
  swagger:
    enabled: true                    # Automatic OpenAPI documentation generation (default: true)
  hibernate:
    auto-optimization: true          # Automatic Hibernate optimization (default: true)
    default-batch-fetch-size: 100    # Lazy-loading batch size (default: 100)
```

### 4. Define the DTO Class

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

### 5. Implement the Service Class

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    public PostService(PostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

### 6. Use It in a Controller

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

### 7. Call the API

```bash
# Search for posts whose title contains "Spring"
GET /api/posts/search?title.contains=Spring&sort=createdAt.desc&page=0&size=10
```

## Module Architecture

searchable-jpa-core, searchable-jpa-openapi, and spring-boot-starter-searchable-jpa are independent top-level Gradle modules. The diagram below shows the dependencies between them.

```
spring-boot-starter-searchable-jpa   Spring Boot auto-configuration starter
  +-- searchable-jpa-core            Core library
  +-- searchable-jpa-openapi         OpenAPI/Swagger support
        +-- searchable-jpa-core
```

## Supported Search Operators

| Category | Operators |
|----------|--------|
| **Comparison** | equals, notEquals, greaterThan, greaterThanOrEqualTo, lessThan, lessThanOrEqualTo |
| **String** | contains, notContains, startsWith, notStartsWith, endsWith, notEndsWith |
| **Range** | between, notBetween |
| **Collection** | in, notIn |
| **Null Check** | isNull, isNotNull |

For the full list of operators and usage details, see [Search Operators](./search-operators.md).

## Version Compatibility

| Library Version | Spring Boot Version | Jakarta EE | Status |
|----------------|------------------|------------|------|
| `1.0.0+` | `3.2.x+` | jakarta.* | Latest |
| `0.1.x` | `2.7.x` | javax.* | Deprecated |

## Tutorials

| Guide | Description |
|--------|------|
| [Installation Guide](./installation.md) | System requirements and installation instructions |
| [Basic Usage](./basic-usage.md) | Basic usage and examples |
| [Auto Configuration](./auto-configuration.md) | Spring Boot auto-configuration and Hibernate optimization options |
| [Search Operators](./search-operators.md) | All supported search operators |
| [Two-Phase Query Optimization](./two-phase-query-optimization.md) | High-performance pagination for large datasets |
| [Relationships and Two-Phase Query](./relationship-and-two-phase-query.md) | JPA relationship mapping and resolving the N+1 problem |
| [Advanced Features](./advanced-features.md) | Complex search conditions and advanced features |
| [OpenAPI Integration](./openapi-integration.md) | Automatic Swagger documentation generation |
| [API Reference](./api-reference.md) | Complete API documentation |
| [FAQ](./faq.md) | FAQ and troubleshooting |

## Requirements

- Java 17+
- Spring Boot 3.2.x+
- Gradle 8.5+

## License

This project is distributed under the [SimpleCORE License 1.0 (SCL-1.0)](./license.md).

## Team

Searchable JPA is developed by [SimpleCORE Inc.](https://simplecore.kr).

- **Website**: [simplecore.kr](https://simplecore.kr)
- **GitHub**: [github.com/simplecore-inc](https://github.com/simplecore-inc)
- **Contact**: license@simplecore.kr
