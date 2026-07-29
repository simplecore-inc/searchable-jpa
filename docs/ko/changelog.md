# 변경 사항

이 문서는 searchable-jpa 라이브러리의 주요 변경 사항을 기록합니다.

> GitHub 커밋 링크로 상세한 변경 내용을 확인할 수 있습니다.

---

## 최근 변경 사항

### v1.1.1

#### 추가된 기능
- **시간 구간별 건수 집계**
  - `TimeBucketCounter`가 목록 조회에 쓰는 `SearchCondition`을 그대로 받아, 기간을 같은 폭의 구간으로 나눠 구간별 건수를 데이터베이스에서 집계
  - 스타터가 `timeBucketCounter` 빈을 자동 등록(`@ConditionalOnMissingBean`이므로 직접 정의한 빈이 우선)
  - `EpochMillisFunctionContributor`가 `epoch_millis` 함수를 데이터베이스별 표현으로 등록하고, 등록 대상이 아닌 데이터베이스에서는 구간별 조건부 합계로 집계
  - [상세 문서](/ko/advanced-features.md#시간-구간별-건수-집계)

---

## 버전별 변경 사항

### v1.1.0

이전 버전과 호환되지 않는 변경을 포함합니다.

#### 호환성이 깨지는 변경
- **예외 계층 통합** [`622d7f6`](https://github.com/simplecore-inc/searchable-jpa/commit/622d7f6)
  - `SearchableValidationException`과 `SearchableParseException`이 `SearchableException`(RuntimeException)만 상속
  - 두 예외 모두 `jakarta.validation.ValidationException` 상속 제거
  - `SearchableException` 아래 `SearchableConfigurationException`, `SearchableValidationException`, `SearchableOperationException`, `SearchableJoinException`, `SearchableParseException` 5종 유지
- **중첩 and()/or() 조건 그룹 평가 순서 변경**
  - `SearchConditionBuilder`로 만든 중첩 `and()`/`or()` 그룹은 연산자별로 묶이지 않고, 호출 순서대로(왼쪽에서 오른쪽으로) 각 노드가 자신의 연산자로 누적 결과와 결합
- **Hibernate 자동 설정 우선순위 변경**
  - 스타터가 등록하는 `searchable.hibernate.*` 기본값은 최저 우선순위로 적용되어 사용자가 지정한 Hibernate 설정이 항상 우선
  - 효과 없던 `@PostConstruct`의 `@ConditionalOnProperty` 제거, 배치 크기 속성에 1 이상 검증 추가
- **시간대 없는 날짜/시간 검색값의 해석 기준 변경**
  - 이전에는 배포 서버의 JVM 기본 시간대로 해석했으나, 이제 애플리케이션 시간대를 사용하며 미지정 시 UTC로 동작
  - 기준 시간대는 `searchable.date-time.default-timezone`, 호스트의 `applicationZoneId` 빈, `spring.jackson.time-zone`, UTC 순으로 결정

#### 개선 사항
- 페이지네이션: `@EmbeddedId` 속성명을 메타모델에서 조회, 마지막 페이지를 넘어가는 요청에서도 total count 정확히 계산, 복합 키 구분 카운트 처리, ToMany 정렬을 GROUP BY 집계로 안정화, `CompositeKeyQueryExecutor`/`SpecificationQuerySupport`로 로직 분리
- 파싱: 콤마 분할을 IN/NOT_IN/BETWEEN 등 다중값 연산자에만 적용, 빈 패턴 값 거부, BETWEEN 두 번째 값 검증
- 조인/조건 생성: LIKE 조건에 ESCAPE 절 추가(리터럴 `%`/`_` 정확히 처리), 빈 패턴 값 거부
- 서비스/유틸/예외: `@MappedSuperclass` 상속 필드 반영, UUID 값 파싱 지원
- 설정: 검색값 해석 기준 시간대를 지정하는 `searchable.date-time.default-timezone` 추가
- OpenAPI: IN/NOT_IN/BETWEEN 스키마에서 enum 값 유지, BETWEEN 예제의 상한/하한을 다른 값으로 생성, 상속 필드 문서화, 배열 타입 스키마 NPE 방어, 날짜/시간 파라미터에 타입별 `format`(date/partial-time/date-time) 부여

### v1.0.11
- `SearchableServiceDelegate`/`SearchableServiceSupport` 도입 ([`194f6e7`](https://github.com/simplecore-inc/searchable-jpa/commit/194f6e7))
  - `DefaultSearchableService`를 상속할 수 없는 서비스도 위임(composition) 방식으로 사용 가능
  - `SearchableServiceDelegate`가 검색/정렬/페이지네이션 로직을 담당하고, `SearchableServiceSupport`는 이를 연결하는 믹스인 인터페이스로 동작

### v1.0.10
- 과도한 info/debug 로그를 trace 레벨로 전환 ([`94f5fed`](https://github.com/simplecore-inc/searchable-jpa/commit/94f5fed))

### v1.0.9
- OpenAPI 문서 출력 간소화로 응답 크기 축소 ([`b9af1cb`](https://github.com/simplecore-inc/searchable-jpa/commit/b9af1cb))
- Context7 문서 색인 연동 설정 추가 ([`291609a`](https://github.com/simplecore-inc/searchable-jpa/commit/291609a))

### v1.0.8
- `orXXX()` 연산자와 중첩 그룹 조건이 사라지던 문제 수정 ([`9378984`](https://github.com/simplecore-inc/searchable-jpa/commit/9378984))
- 엔티티 메타데이터 캐싱 추가로 성능 개선 ([`8f21b7d`](https://github.com/simplecore-inc/searchable-jpa/commit/8f21b7d))

### v1.0.7
- OpenAPI 예제 값 생성 시 날짜/시간 값을 고정값으로 사용하도록 수정 ([`a6c8479`](https://github.com/simplecore-inc/searchable-jpa/commit/a6c8479))
- 로그와 문서에서 이모지 제거 ([`11893e6`](https://github.com/simplecore-inc/searchable-jpa/commit/11893e6), [`bab39bd`](https://github.com/simplecore-inc/searchable-jpa/commit/bab39bd))

### v1.0.6
- `OpenApiDocUtils`에 `LocalDate`/`LocalTime` 지원 추가 ([`089ef98`](https://github.com/simplecore-inc/searchable-jpa/commit/089ef98))
- 상세 디버그 로그를 trace 레벨로 전환 ([`ad4c9a4`](https://github.com/simplecore-inc/searchable-jpa/commit/ad4c9a4))

### v1.0.5
- 명시적 `fetchFields` 지원 추가 ([`9a177a7`](https://github.com/simplecore-inc/searchable-jpa/commit/9a177a7))
  - `SearchCondition`에 `fetchFields` 속성 추가
  - Lazy 로딩된 관계를 명시적으로 Fetch Join 가능
  - 중첩 경로 지원 (예: `author.profile`)
  - 보안을 위해 `@JsonIgnore` 적용 (서버 측에서만 설정 가능)
  - [상세 문서](/ko/relationship-and-two-phase-query.md#명시적-fetch-join-fetchfields)

### v1.0.4
- Docsify 기반 한국어 문서 사이트 구축 ([`4723c7a`](https://github.com/simplecore-inc/searchable-jpa/commit/4723c7a), [`2c9bb11`](https://github.com/simplecore-inc/searchable-jpa/commit/2c9bb11), [`147597b`](https://github.com/simplecore-inc/searchable-jpa/commit/147597b))
- OpenAPI 예제 값 생성 로직의 예외 상황 처리 개선 ([`ac01315`](https://github.com/simplecore-inc/searchable-jpa/commit/ac01315))

### v1.0.3
- JSON 타입 필드 검색 지원 추가 ([`ab55fa9`](https://github.com/simplecore-inc/searchable-jpa/commit/ab55fa9))
- 커밋 메시지 작성 가이드에 AI 서명 제외 규칙 추가 ([`73ad2df`](https://github.com/simplecore-inc/searchable-jpa/commit/73ad2df))

### v1.0.2
- 버전 번호만 올린 릴리스로 별도 변경 사항 없음

### v1.0.1
- `SearchConditionBuilder` 확장(상속) 지원 추가 ([`034ded0`](https://github.com/simplecore-inc/searchable-jpa/commit/034ded0))

### v1.0.0
- 최초 정식 릴리스
- Spring Boot 3.2.x+ 지원
- Jakarta EE 9+ 호환

---

## 관련 링크

- [GitHub 저장소](https://github.com/simplecore-inc/searchable-jpa)
- [이슈 트래커](https://github.com/simplecore-inc/searchable-jpa/issues)
- [전체 커밋 히스토리](https://github.com/simplecore-inc/searchable-jpa/commits/master)
