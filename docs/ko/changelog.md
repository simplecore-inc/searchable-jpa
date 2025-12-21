# 변경 사항

이 문서는 searchable-jpa 라이브러리의 주요 변경 사항을 기록합니다.

> GitHub 커밋 링크를 통해 상세한 변경 내용을 확인할 수 있습니다.

---

## 최근 변경 사항

### 2024-12

#### CI/CD 개선
- **릴리즈 워크플로우 자동화** [`daa6676`](https://github.com/simplecore-inc/searchable-jpa/commit/daa6676)
  - SNAPSHOT 버전 자동 감지 및 릴리즈 버전 변환
  - 수동 버전 입력 제거
  - SNAPSHOT이 아닌 버전에서 릴리즈 시도 시 자동 중단
  - 보안 강화를 위한 환경 변수 사용

- **문서 빌드 워크플로우 개선** [`daa6676`](https://github.com/simplecore-inc/searchable-jpa/commit/daa6676)
  - SNAPSHOT 문서 폴더명을 `snapshot`으로 고정
  - 버전별 폴더 변경 방지

#### 새로운 기능
- **명시적 fetchFields 지원** [`9a177a7`](https://github.com/simplecore-inc/searchable-jpa/commit/9a177a7)
  - SearchCondition에 `fetchFields` 속성 추가
  - Lazy 로딩된 관계를 명시적으로 Fetch Join 가능
  - 중첩 경로 지원 (예: `author.profile`)
  - 보안을 위해 `@JsonIgnore` 적용 (서버 측에서만 설정 가능)
  - [상세 문서](/ko/relationship-and-two-phase-query.md#명시적-fetch-join-fetchfields)

#### 버그 수정
- **OpenAPI 예제 값 생성 개선** [`ac01315`](https://github.com/simplecore-inc/searchable-jpa/commit/ac01315)
  - 엣지 케이스에서 예제 값 생성 오류 수정

#### 문서화
- **문서 빌드 및 배포 시스템 추가** [`4723c7a`](https://github.com/simplecore-inc/searchable-jpa/commit/4723c7a)
  - GitHub Actions 기반 자동 문서 배포
  - 버전별 문서 관리 지원

- **한국어 문서 사이드바 추가** [`2c9bb11`](https://github.com/simplecore-inc/searchable-jpa/commit/2c9bb11)
  - 한국어 문서 네비게이션 개선

- **Docsify 설정 추가** [`147597b`](https://github.com/simplecore-inc/searchable-jpa/commit/147597b)
  - 문서 사이트 기본 설정 구성

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