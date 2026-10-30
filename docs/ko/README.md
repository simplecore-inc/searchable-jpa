# Searchable JPA

[English](/en/README.md) | 한국어

Searchable JPA는 Spring Data JPA에 DTO 기반 검색, 정렬, 페이지 조회 기능을 추가합니다. 애플리케이션에서 `@SearchableField`로 검색 가능한 속성을 지정하고, 쿼리 파라미터나 JSON을 `SearchCondition`으로 변환한 후 서비스 또는 위임 객체로 조회합니다.

## 지원 계열

| 라이브러리 | Spring Boot | 브랜치 | 상태 |
| --- | --- | --- | --- |
| 2.x | 4.1.x 이상 4.x | `master` | 개발 중 |
| 1.x | 3.2.x–3.5.x | `spring-boot-3x` | 유지보수 |
| 0.1.x | 2.7.x | `spring-boot-2x` | 유지보수 종료 |

현재 소스는 Java 17, Spring Boot 3.5.16, Hibernate 6.6.59.Final로 `1.1.4-SNAPSHOT`을 빌드합니다. 사용하는 의존성과 일치하는 문서 버전을 선택하세요. 개발 문서에는 이전 릴리스에 없는 수정 사항이 포함될 수 있습니다.

## 주요 기능과 적용 범위

- 비교, 문자열 패턴, 범위, 목록, null 검사를 위한 18개 연산자와 실행 시 DTO 검증, 엔티티 경로 매핑을 제공합니다.
- 단일 키의 단순 검색에서는 엔티티를 직접 조회합니다. 컬렉션 조인이나 복합 키가 있으면 ID를 선택한 후 엔티티를 조회하며, 독립된 컬렉션은 나누어 조회합니다.
- 페이지, 첫 결과 조회, 인터페이스 프로젝션, 검색 조건에 따른 수정·삭제를 지원합니다.
- 같은 폭의 시간 구간별 개수를 집계합니다. 데이터베이스 함수로 처리할 수 없으면 호환 가능한 집계 방식으로 전환합니다.
- 기존 Jackson 2 공개 API와 타입이 지정된 검색 본문의 Jackson 3 역직렬화를 지원합니다.
- Spring Boot 자동 설정과 DTO 어노테이션에 따른 SpringDoc 문서 생성을 제공합니다.

빌더의 필드명은 문자열이며 실행 시 검증합니다. 쿼리 성능은 인덱스, 검색 조건의 선택도, 연관 데이터 크기와 실행 계획에 따라 달라집니다. 접근 권한, 테넌트 격리, API 응답 DTO는 애플리케이션에서 구현해야 합니다.

## 시작 순서

1. [설치 가이드](./installation.md)에 따라 GitHub Packages 또는 Maven Local에서 맞는 계열을 설치하세요.
2. [기본 사용법](./basic-usage.md)에 따라 엔티티, 저장소, DTO, 서비스, 컨트롤러를 정의하세요.
3. [자동 설정](./auto-configuration.md)에서 페이지 제한, 시간대, Hibernate 기본값을 확인하세요.
4. [검색 연산자](./search-operators.md)에서 값의 형식과 허용 연산자를 선택하세요.
5. [조회 전략](./two-phase-query-optimization.md)에서 관계 매핑에 따른 비용을 확인하세요.

문서의 DTO와 컨트롤러는 다음과 같은 요청을 처리합니다.

```http
GET /api/posts?title.contains=Spring&sort=createdAt.desc&page=0&size=20
```

정렬 필드와 방향은 점으로 구분합니다. 여러 정렬 기준은 `createdAt.desc,title.asc`처럼 쉼표로 구분하세요.

## 모듈

| 모듈 | 역할 |
| --- | --- |
| `searchable-jpa-core` | 조건, 검증, JPA 실행, 서비스 |
| `searchable-jpa-openapi` | 검색 파라미터 문서 생성, core 의존 |
| `spring-boot-starter-searchable-jpa` | 두 모듈과 Spring 자동 설정 |

[API 참조](./api-reference.md), [고급 기능](./advanced-features.md), [OpenAPI 연동](./openapi-integration.md), [문제 해결](./faq.md), [변경 이력](./changelog.md)을 확인하세요. 문서를 수정할 때는 [빌드·검증 가이드](./documentation-guide.md)를 참고하세요.

## 라이선스와 지원

[SimpleCORE License 1.0](./license.md)을 적용합니다. 재현 가능한 오류는 [GitHub Issues](https://github.com/simplecore-inc/searchable-jpa/issues)에 등록하세요. 라이브러리, Java, Spring Boot, 데이터베이스 버전과 최소 DTO·엔티티 매핑, 민감한 정보를 제거한 실패 요청을 함께 작성하세요.
