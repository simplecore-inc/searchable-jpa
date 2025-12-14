# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Searchable JPA is a Spring Boot library that extends Spring Data JPA to provide dynamic search, sorting, and pagination functionality. It supports complex search conditions through annotations and builder patterns with high-performance cursor-based pagination.

### Multi-Module Structure
- **searchable-jpa-core**: Core library with search/sort/pagination logic
- **searchable-jpa-openapi**: OpenAPI/Swagger integration
- **spring-boot-starter-searchable-jpa**: Spring Boot auto-configuration starter

### Version Compatibility
- Version 1.0.0+: Spring Boot 3.2.x+ (Jakarta EE 9+)
- Version 0.1.x: Spring Boot 2.7.x (javax.* packages) - Deprecated

## CRITICAL PROJECT DEVELOPMENT RULES
**These rules are absolutely mandatory and must be referenced for all development work!**

### CRITICAL SUMMARY (Zero Tolerance)
- ✖ Absolutely NO emojis in code, documentation, or comments
- ✔ Use only log-symbols type symbols (✔ ✖ ⚠ ℹ) for status indicators
- ✔ English-only for all artifacts (code, comments, documentation, commit messages)
- ✖ Absolutely NO hardcoding of domain/schema names (entities, fields, tables, enums, methods, columns)
- ✖ Do not generate debug logs unless explicitly requested
- ✖ Do not add defensive logic for uncertain situations
- ✖ Do not write retry logic for uncertain states

### ZERO HARDCODING POLICY
**ABSOLUTE PROHIBITION OF HARDCODING — NO EXCEPTIONS — VIOLATIONS WILL BE REJECTED**

#### Strictly Forbidden Hardcoding
- Entity names (e.g., "TestPost", "User", "Order")
- Field names (e.g., "id", "name", "status")
- Database column names (e.g., "user_id", "created_at")
- Table names (e.g., "test_post", "user_table")
- Enum values (e.g., "PUBLISHED", "ACTIVE")
- Method names (e.g., "getId", "getName")
- Property names in configuration or mapping
- Any specific JPA entity or schema details

#### Required Dynamic Approaches
- Use JPA Metamodel (`EntityType`, `Attribute`) for dynamic field detection
- Use Reflection as fallback only
- Generic type parameters (`<T>`, `<ID>`)
- Configuration-based solutions

#### Enforcement
- Immediate rejection of code containing hardcoded names or schema details
- Zero tolerance for "temporary" or "convenience" hardcoding
- Dynamic-first: implement dynamic solutions from the start
- Tests must cover multiple entity types

### Symbol Usage Guidelines
Use ONLY the following symbols for status indicators:
- ✔ (success/completed/ok)
- ✖ (error/failed/no)
- ⚠ (warning/caution)
- ℹ (info/note)

NEVER use emojis or other Unicode symbols outside the approved list.

### Code Style Guidelines
- Do not add comments to indicate changes from previous code versions
- Find and solve the actual root cause; do not insert defensive logic
- Do not change the specified implementation approach unless explicitly instructed
- Do not write fallback or backup functionality except when errors are unavoidable
- All program artifacts must be written in English
- Use clear, descriptive English variable and function names
- Write concise comments for complex logic with proper grammar

### Git Commit Guidelines
- ALL commit messages MUST be written in English
- Use conventional commit format: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`
- Be clear and descriptive about what changed and why
- Do not perform git commit/push operations unless explicitly requested
- Do NOT add "Generated with Claude Code" or "Co-Authored-By: Claude" to commit messages

### Development Workflow
- When the same problem occurs more than twice, consult official documentation
- File Size Management: If a file exceeds 500 lines, prioritize refactoring
- Refactoring Safety: Ensure no duplicate functionality remains; fully remove obsolete code

### Java/Spring Specific Guidelines
- Follow Java naming conventions (PascalCase for classes, camelCase for methods/variables)
- Follow Spring Boot best practices and conventions
- Implement proper exception handling with meaningful messages
- Use Spring Data JPA repositories for database operations
- Follow RESTful API design principles
- Use Bean Validation annotations for input validation
- For logging and console output, use only approved symbols (✔ ✖ ⚠ ℹ)

## Architecture and Key Components

### Core Components
- **DefaultSearchableService**: Base service class for implementing search functionality
- **SearchableParams**: Annotation for marking controller parameters (GET requests)
- **SearchableField**: Annotation for DTO field configuration
- **SearchCondition**: Query condition representation (POST requests)
- **CursorManager**: Handles cursor-based pagination
- **SearchableParamsParser**: Converts request parameters to search conditions
- **OpenApiDocCustomiser**: Customizes OpenAPI documentation for searchable endpoints

### Dynamic ID Detection Pattern
Always use JPA Metamodel first, reflection as fallback:
```java
EntityType<T> entityType = entityManager.getMetamodel().entity(entityClass);
String idFieldName = entityType.getId(entityType.getIdType().getJavaType()).getName();
```

### Cursor Pagination Implementation
Located in `searchable-jpa-core/src/main/java/dev/simplecore/searchable/core/condition/cursor/`
- Uses dynamic field detection
- No hardcoded entity assumptions
- Supports composite keys

## Development Commands

### Build and Test
```bash
# Build entire project
./gradlew build

# Run tests (excludes performance tests)
./gradlew test

# Run performance tests separately
./gradlew performanceTest

# Run tests with specific database profile
./gradlew test -Dspring.profiles.active=h2    # H2 database (default)
./gradlew test -Dspring.profiles.active=mssql # SQL Server

# Clean build
./gradlew clean build
```

### Test Data Generation
```bash
# Generate small test dataset (10K records)
./gradlew generateSmallData

# Generate large test dataset (5M records)
./gradlew generateLargeData
```

### Publishing
```bash
# Publish to GitHub Packages
./gradlew publish
```

## Testing Strategy

### Database Compatibility
Tests must verify compatibility with both H2 and SQL Server databases:
- Default: H2 in-memory database
- MSSQL: `-Dspring.profiles.active=mssql`

### Test Categories
- Unit tests: Basic functionality
- Integration tests: Full stack with database
- Performance tests: Tagged with `@Tag("performance")`, run separately

### Dynamic Testing Requirements
- Test with multiple entity types (simple ID, composite ID, different field types)
- Verify no hardcoded assumptions
- Ensure dynamic approaches work across different entity structures
- NEVER modify test conditions or add hardcoding to make tests pass
- Solve one problem at a time systematically

### Test Fixtures
Located in `searchable-jpa-core/src/test/java/dev/simplecore/searchable/test/`
- Various entity types for comprehensive testing
- Performance test utilities

## Common Development Tasks

### Adding New Search Operator
1. Add operator to `SearchOperator` enum
2. Implement logic in `SpecificationBuilder`
3. Add validation in `SearchableFieldValidator`
4. Update documentation

### Implementing Custom Service
```java
@Service
public class YourService extends DefaultSearchableService<YourEntity, Long> {
    public YourService(YourRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

### Creating Search DTO
```java
public class YourSearchDTO {
    @SearchableField(operators = {EQUALS, CONTAINS}, sortable = true)
    private String fieldName;
    // ... other fields
}
```

## OpenAPI Integration

### Auto-Configuration
The library automatically configures OpenAPI documentation when SpringDoc is present. The configuration is handled by `SearchableOpenApiConfiguration` which registers `OpenApiDocCustomiser` as a bean named `searchConditionCustomizer`.

### Common Integration Issues

#### Issue: GroupedOpenApi Blocks Searchable JPA Features
When using `GroupedOpenApi`, the searchable-jpa OpenAPI customizations may not be applied.

**Cause**: `GroupedOpenApi` creates isolated API groups that don't automatically include global `OperationCustomizer` beans.

**Solutions**:

1. **Inject with @Qualifier (Recommended)**:
```java
@Bean
public GroupedOpenApi userAccountApi(
        @Qualifier("searchConditionCustomizer") OperationCustomizer searchConditionCustomizer) {
    return GroupedOpenApi.builder()
            .group("user-account-api")
            .displayName("User Account API")
            .pathsToMatch("/api/user/**")
            .addOperationCustomizer(searchConditionCustomizer)
            .build();
}
```

2. **Include All Customizers**:
```java
@Bean
public GroupedOpenApi userAccountApi(List<OperationCustomizer> customizers) {
    GroupedOpenApi.Builder builder = GroupedOpenApi.builder()
            .group("user-account-api")
            .displayName("User Account API")
            .pathsToMatch("/api/user/**");
    
    if (customizers != null && !customizers.isEmpty()) {
        customizers.forEach(builder::addOperationCustomizer);
    }
    
    return builder.build();
}
```

3. **Direct Type Injection**:
```java
@Bean
public GroupedOpenApi userAccountApi(
        @Autowired(required = false) OpenApiDocCustomiser searchableCustomizer) {
    GroupedOpenApi.Builder builder = GroupedOpenApi.builder()
            .group("user-account-api")
            .displayName("User Account API")
            .pathsToMatch("/api/user/**");
    
    if (searchableCustomizer != null) {
        builder.addOperationCustomizer(searchableCustomizer);
    }
    
    return builder.build();
}
```

#### Issue: Multiple OperationCustomizer Beans Conflict
Error: "required a single bean, but 2 were found"

**Cause**: Multiple `OperationCustomizer` implementations exist (e.g., from QueryDSL and Searchable JPA).

**Solution**: Use `@Qualifier("searchConditionCustomizer")` to specify the exact bean.

### OpenAPI Documentation Features
- Automatic parameter documentation for `@SearchableParams` annotated parameters
- Request body schema generation for `SearchCondition<T>` types
- Example generation for both GET and POST search endpoints
- Field-level documentation based on `@SearchableField` annotations

## Troubleshooting

### Common Issues
1. **Hardcoding Detection**: Any entity-specific code will fail tests with different entities
2. **Database Compatibility**: Native queries must work on both H2 and SQL Server
3. **Performance**: Cursor pagination must be used for large datasets, never OFFSET
4. **OpenAPI Integration**: GroupedOpenApi requires explicit customizer inclusion

### Verification Checklist
- [ ] No hardcoded entity/field names
- [ ] JPA Metamodel used for dynamic detection
- [ ] Tests pass with multiple entity types
- [ ] Both H2 and MSSQL tests pass
- [ ] No OFFSET-based pagination used
- [ ] OpenAPI documentation correctly generated for searchable endpoints

## Performance Considerations
- Use Spring Boot's performance monitoring
- Optimize database queries and indexes
- Apply caching strategies when appropriate
- Monitor and review application performance metrics
- Cursor-based pagination for large datasets (never use OFFSET)

## Documentation Guidelines
- Do not create new documentation files unless explicitly requested
- Maintain existing documentation only when specifically asked
- Prefer concise inline comments for complex logic over separate docs

## Examples of Good Commit Messages
- `feat: add dynamic ID field detection to CursorCalculator`
- `fix: remove hardcoded TestPost entity references`
- `refactor: replace hardcoded field names with JPA metamodel`
- `test: add dynamic entity testing for multiple entity types`
- `docs: update README with dynamic implementation guidelines`
- `fix: resolve OpenAPI customizer bean conflict with @Qualifier`