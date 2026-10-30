# 설치

이 문서는 `1.1.3` 소스 기준입니다. 의존성 버전은 2026년 10월 30일에 확인했습니다. `-SNAPSHOT`으로 끝나는 버전은 정식 릴리스가 아닌 개발 빌드입니다.

## 호환성

| Searchable JPA 계열 | Spring Boot 계열 | Java 패키지 | 소스 브랜치 |
| --- | --- | --- | --- |
| 2.x | 4.1.x 이후의 4.x | `jakarta.*` | `master` |
| 1.x | 3.2.x–3.5.x | `jakarta.*` | `spring-boot-3x` |
| 0.1.x | 2.7.x | `javax.*` | `spring-boot-2x` (유지보수 종료) |

현재 빌드는 Java 17, Spring Boot 3.5.16, Hibernate ORM 6.6.59.Final로 검증합니다. 소스와 바이트코드의 최소 버전은 Java 17입니다. 세 모듈은 같은 버전 계열로 사용하세요. Boot 3 애플리케이션은 애플리케이션 자체를 Boot 4로 전환할 때까지 1.x 계열을 사용합니다.

기존 1.x 서비스의 상속 구조, 델리게이트 생성 방식, 쿼리 파라미터 이름, Jackson 2 공개 메서드 시그니처를 유지합니다. Jackson 3에서도 DTO 타입을 지정한 HTTP 검색 본문을 처리합니다. 문서에 명시된 제한을 초과하는 요청은 보완된 검증에서 거부합니다. 제한값은 [자동 설정](./auto-configuration.md)을 참조하세요.

## 모듈 구성

| 아티팩트 | 구성 |
| --- | --- |
| `searchable-jpa-core` | 검색 조건, DTO 검증, JPA 조회, 서비스, 시간별 집계 |
| `searchable-jpa-openapi` | `@SearchableParams`, API 문서 생성, 예제 생성 및 core 모듈 |
| `spring-boot-starter-searchable-jpa` | 두 모듈과 Spring Boot 자동 설정 |

Spring MVC 애플리케이션에서는 starter를 사용하세요. 데이터소스, 엔티티, Repository, 컨트롤러와 접근 제어는 애플리케이션에서 정의합니다. Repository는 `JpaRepository`와 `JpaSpecificationExecutor`를 모두 상속해야 합니다.

## 로컬 설치

저장소의 Gradle wrapper와 Java 17 이상으로 실행하세요.

```bash
./gradlew check performanceTest publishToMavenLocal
```

세 모듈을 `~/.m2/repository/dev/simplecore/searchable/<artifact>/1.1.3/`에 설치합니다. Git 태그나 원격 게시 작업은 수행하지 않습니다. 명령을 다시 실행하면 로컬 스냅샷을 갱신합니다.

Gradle 소비자 프로젝트에서는 로컬 저장소를 먼저 조회하도록 설정하세요.

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:1.1.3'
}
```

저장소를 `settings.gradle`에서 관리한다면 그 파일에 `mavenLocal()`을 추가하세요. 이전 스냅샷이 캐시된 경우 소비자 프로젝트를 `--refresh-dependencies` 옵션으로 빌드하세요.

Maven은 로컬 저장소를 자동으로 조회합니다.

```xml
<dependency>
  <groupId>dev.simplecore.searchable</groupId>
  <artifactId>spring-boot-starter-searchable-jpa</artifactId>
  <version>1.1.3</version>
</dependency>
```

## GitHub Packages

원격에 게시된 버전을 사용할 때는 `https://maven.pkg.github.com/simplecore-inc/searchable-jpa` 저장소와 실제 게시된 버전을 지정하세요. 인증 정보는 커밋하는 파일에 작성하지 마세요.

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/simplecore-inc/searchable-jpa')
        credentials {
            username = System.getenv('GITHUB_USERNAME')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}
```

토큰에는 패키지 읽기 권한이 필요합니다. 라이브러리 소스 빌드는 Git에서 제외된 `gradle.properties`의 `gpr.user`·`gpr.token` 또는 `GITHUB_USERNAME`·`GITHUB_TOKEN` 환경 변수를 읽습니다. 원격 게시는 별도의 게시용 인증 정보를 사용하며, 로컬 게시에는 인증 정보가 필요하지 않습니다.

## 검증한 의존성 버전

| 구성 요소 | 버전 |
| --- | --- |
| Gradle wrapper | 9.8.2 |
| Spring Boot | 3.5.16 |
| Spring dependency management plugin | 1.1.7 |
| SpringDoc OpenAPI | 2.10.0 |
| Hibernate ORM | 6.6.59.Final |
| Jackson 2 databind / Java time | 2.22.4 |
| Jackson 3 databind | 3.2.4 |
| ModelMapper | 3.2.8 |
| HikariCP | 7.1.0 |
| H2 (테스트) | 2.5.252 |
| SQL Server JDBC (테스트 프로파일) | 13.6.1.jre11 |
| JUnit / Mockito | 6.1.3 / 5.24.0 |

전체 버전 지정은 루트 `build.gradle`에서 확인할 수 있습니다. Hibernate, Jackson, 로깅, 테스트 의존성을 함께 검증합니다. 소비자 애플리케이션의 의존성 관리 설정에서 전이 의존성 버전을 바꿀 수 있으므로 업그레이드 후 실제 런타임 클래스패스를 확인하세요. 애플리케이션의 데이터베이스에 맞는 JDBC 드라이버를 사용하세요. SQL Server 테스트 드라이버가 애플리케이션의 데이터소스를 설정하지는 않습니다.

## 검증 명령과 데이터베이스 프로파일

```bash
./gradlew test
./gradlew performanceTest
./gradlew build
```

일반 테스트에서는 `performance` 태그를 제외합니다. `performanceTest`는 해당 테스트를 별도로 실행하며, 조회 결과가 비어 있지 않은지도 검사합니다. 기본 통합 테스트는 H2를 사용합니다. SQL Server 테스트 프로파일에는 접속 가능한 데이터베이스가 필요합니다. H2 테스트 통과만으로 모든 데이터베이스의 실행 계획과 시간 정밀도가 같다고 판단할 수는 없습니다.

애플리케이션 예제는 [기본 사용법](./basic-usage.md), 의존성 문제 해결은 [자주 묻는 질문](./faq.md)을 참조하세요.

Boot 3에서 유료 Spring 수정 버전 또는 프레임워크 전환이 필요한 공지는 [보안과 의존성 적용 범위](./security.md)를 확인하세요.
