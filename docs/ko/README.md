# Searchable JPA 한국어 문서

[메인으로](../../README.md)

---

## 문서 목차

### 시작하기
1. [설치 가이드](installation.md)
2. [기본 사용법](basic-usage.md)
3. [자동 설정](auto-configuration.md)

### 핵심 기능
4. [검색 연산자](search-operators.md)
5. [2단계 쿼리 최적화](two-phase-query-optimization.md)
6. [관계형 데이터와 2단계 쿼리](relationship-and-two-phase-query.md)

### 고급 기능
7. [고급 기능](advanced-features.md)
8. [OpenAPI 통합](openapi-integration.md)
9. [API 레퍼런스](api-reference.md)

### 도움말
10. [자주 묻는 질문](faq.md)

---

## 빠른 시작

### 1. 의존성 추가
```xml
<dependency>
    <groupId>dev.simplecore</groupId>
    <artifactId>spring-boot-starter-searchable-jpa</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 엔티티 및 DTO 정의
```java
// 엔티티 정의 예제는 기본 사용법 문서 참조
// DTO 정의 예제는 기본 사용법 문서 참조
```

### 3. 서비스 구현
```java
// 서비스 구현 예제는 기본 사용법 문서 참조
```

### 4. 컨트롤러 구현
```java
// 컨트롤러 구현 예제는 기본 사용법 문서 참조
```

### 5. 검색 실행
```bash
# URL 파라미터 방식
GET /api/posts/search?title.contains=Spring&status.equals=PUBLISHED

# JSON 요청 방식은 기본 사용법 문서 참조
```

## 주요 특징

- **동적 검색**: 복잡한 검색 조건을 동적으로 구성
- **2단계 쿼리 최적화**: 대용량 데이터 처리를 위한 성능 최적화
- **복합 키 지원**: @IdClass와 @EmbeddedId 모두 지원
- **관계형 데이터**: N+1 문제 해결과 효율적인 JOIN 처리
- **타입 안전성**: 컴파일 타임 검증과 런타임 유효성 검사
- **Spring Boot 자동 설정**: 최소한의 설정으로 즉시 사용 가능
- **OpenAPI 통합**: Swagger 문서 자동 생성

## 지원하는 검색 연산자

- **비교**: equals, notEquals, greaterThan, lessThan 등
- **문자열**: contains, startsWith, endsWith 등  
- **범위**: between, notBetween
- **컬렉션**: in, notIn
- **NULL 체크**: isNull, isNotNull

상세한 연산자 목록과 사용법은 [검색 연산자](search-operators.md) 문서를 참조하세요.

---

더 자세한 사용법과 고급 기능은 각 문서를 참조하세요. 