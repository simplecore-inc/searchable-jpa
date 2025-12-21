# 변경 사항

이 문서는 searchable-jpa 라이브러리의 주요 변경 사항을 기록합니다.

> GitHub 커밋 링크를 통해 상세한 변경 내용을 확인할 수 있습니다.

---

## 최근 변경 사항

### 2024-12

#### 새로운 기능
- **명시적 fetchFields 지원** [`9a177a7`](https://github.com/simplecore-inc/searchable-jpa/commit/9a177a7)
  - SearchCondition에 `fetchFields` 속성 추가
  - Lazy 로딩된 관계를 명시적으로 Fetch Join 가능
  - 중첩 경로 지원 (예: `author.profile`)
  - 보안을 위해 `@JsonIgnore` 적용 (서버 측에서만 설정 가능)
  - [상세 문서](ko/relationship-and-two-phase-query.md#명시적-fetch-join-fetchfields)

---

## 버전별 변경 사항

### v1.0.3
- 초기 안정화 버전
- 2단계 쿼리 최적화 도입
- 커서 기반 페이지네이션 지원

### v1.0.2
- OpenAPI 통합 개선
- 검색 연산자 확장

### v1.0.1
- 버그 수정 및 안정성 개선

### v1.0.0
- 최초 정식 릴리즈
- Spring Boot 3.2.x+ 지원
- Jakarta EE 9+ 호환

---

## 관련 링크

- [GitHub 저장소](https://github.com/simplecore-inc/searchable-jpa)
- [이슈 트래커](https://github.com/simplecore-inc/searchable-jpa/issues)
- [전체 커밋 히스토리](https://github.com/simplecore-inc/searchable-jpa/commits/master)