# Searchable JPA 자동 설정 가이드

## **자동 Hibernate 최적화**

searchable-jpa 라이브러리는 **자동으로 Hibernate 최적화 설정을 구성**하여 N+1 문제를 방지하고 성능을 향상시킵니다.

### 📋 **자동 적용되는 최적화 설정**

라이브러리를 의존성에 추가하기만 하면 다음 설정들이 **자동으로 적용**됩니다:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        # N+1 문제 방지
        default_batch_fetch_size: 100
        
        # 배치 처리 최적화
        jdbc:
          batch_size: 1000
          batch_versioned_data: true
        
        # 삽입/업데이트 순서 최적화
        order_inserts: true
        order_updates: true
        
        # 쿼리 최적화
        query:
          in_clause_parameter_padding: true
        
        # 연결 최적화
        connection:
          provider_disables_autocommit: true
```

### ⚙️ **설정 커스터마이징**

필요에 따라 기본값을 변경할 수 있습니다:

```yaml
searchable:
  hibernate:
    # 자동 최적화 활성화/비활성화
    auto-optimization: true
    
    # 배치 fetch 크기 (기본값: 100)
    default-batch-fetch-size: 150
    
    # JDBC 배치 크기 (기본값: 1000)
    jdbc-batch-size: 500
    
    # 기타 최적화 설정들
    batch-versioned-data: true
    order-inserts: true
    order-updates: true
    in-clause-parameter-padding: true
```

### **자동 최적화 비활성화**

자동 최적화를 비활성화하려면:

```yaml
searchable:
  hibernate:
    auto-optimization: false
```

또는 특정 설정만 직접 지정하려면:

```yaml
# 수동으로 설정하면 자동 설정보다 우선됩니다
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 200  # 자동 설정 대신 이 값 사용
```

### 📊 **성능 향상 효과**

#### **Before (자동 설정 없음)**
```sql
-- N+1 문제 발생
SELECT * FROM user_account WHERE id = ?  -- 1번
SELECT * FROM position WHERE id = ?      -- N번 (각 사용자마다)
SELECT * FROM organization WHERE id = ?  -- N번 (각 사용자마다)
```

#### **After (자동 설정 적용)**
```sql
-- 배치 로딩으로 최적화
SELECT * FROM user_account WHERE id IN (?, ?, ?, ...)     -- 1번
SELECT * FROM position WHERE id IN (?, ?, ?, ...)         -- 1번 (배치)
SELECT * FROM organization WHERE id IN (?, ?, ?, ...)     -- 1번 (배치)
```

### 🎯 **주요 이점**

#### 1. **개발자 편의성**
-  별도 설정 불필요
-  최적화 설정 자동 적용
-  실수로 인한 성능 문제 방지

#### 2. **즉시 적용되는 성능 향상**
-  N+1 문제 자동 방지
-  배치 처리 최적화
-  쿼리 계획 캐싱 개선

#### 3. **유연한 커스터마이징**
-  필요시 개별 설정 가능
-  프로젝트별 최적화 가능
-  단계적 비활성화 지원

### 📝 **사용 예시**

#### **기본 사용 (자동 최적화)**
```java
// 의존성만 추가하면 자동으로 최적화됨
@Service
public class UserService {
    
    @Autowired
    private DefaultSearchableService<User, UserSearchDTO> userService;
    
    public Page<UserSearchDTO> searchUsers(SearchCondition condition) {
        // 자동으로 배치 로딩 적용됨
        return userService.findAllWithSearch(condition);
    }
}
```

#### **커스텀 설정 사용**
```yaml
# application.yml
searchable:
  hibernate:
    auto-optimization: true
    default-batch-fetch-size: 200  # 더 큰 배치 크기
    jdbc-batch-size: 2000          # 더 큰 JDBC 배치
```

### 🔍 **설정 확인 방법**

애플리케이션 시작 시 로그에서 확인할 수 있습니다:

```
INFO  SearchableJpaConfiguration - Configuring automatic Hibernate optimizations for searchable-jpa...
INFO  SearchableJpaConfiguration - Applied Hibernate optimizations:
INFO  SearchableJpaConfiguration -   - default_batch_fetch_size: 100
INFO  SearchableJpaConfiguration -   - jdbc.batch_size: 1000
INFO  SearchableJpaConfiguration -   - order_inserts: true
INFO  SearchableJpaConfiguration -   - order_updates: true
INFO  SearchableJpaConfiguration -   - in_clause_parameter_padding: true
INFO  SearchableJpaConfiguration - These settings help prevent N+1 problems and improve performance automatically.
```

### ⚠️ **주의사항**

1. **기존 설정과의 충돌**
   - 기존에 `spring.jpa.properties.hibernate.*` 설정이 있다면 기존 설정이 우선됩니다
   - 자동 설정은 설정되지 않은 항목에만 적용됩니다

2. **메모리 사용량**
   - `default_batch_fetch_size`가 클수록 메모리 사용량이 증가할 수 있습니다
   - 애플리케이션 특성에 맞게 조정하세요

3. **데이터베이스 호환성**
   - 일부 설정은 특정 데이터베이스에서만 효과적일 수 있습니다
   - 성능 테스트를 통해 검증하는 것을 권장합니다

### 🎉 **결론**

searchable-jpa의 자동 Hibernate 최적화 기능으로:

- **설정의 복잡성 제거**: 개발자가 별도로 설정할 필요 없음
- **즉시 성능 향상**: 의존성 추가만으로 N+1 문제 해결
- **유연한 커스터마이징**: 필요시 세부 조정 가능
- **실수 방지**: 최적화 설정 누락으로 인한 성능 문제 예방

이제 **`batch_fetch_size`를 수동으로 설정할 필요가 없습니다!** 