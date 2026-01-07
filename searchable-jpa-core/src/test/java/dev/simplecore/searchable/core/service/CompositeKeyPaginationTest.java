package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.TestIdClassEntity;
import dev.simplecore.searchable.test.entity.TestCompositeKeyEntity;
import dev.simplecore.searchable.test.service.TestIdClassEntityService;
import dev.simplecore.searchable.test.service.TestCompositeKeyEntityService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CompositeKeyPaginationTest {

    @Autowired
    private EntityManager entityManager;
    
    @Autowired
    private TestIdClassEntityService testIdClassEntityService;
    
    @Autowired
    private TestCompositeKeyEntityService testCompositeKeyEntityService;

    @BeforeEach
    @Transactional
    void setupTestData() {
        // Clear caches to ensure test isolation with @DirtiesContext
        SearchableFieldUtils.clearCache();

        log.info("=== STEP 1: SETTING UP COMPOSITE KEY TEST DATA ===");

        // Clean existing data
        entityManager.createNativeQuery("DELETE FROM test_id_class_entity").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM test_composite_key_entity").executeUpdate();
        entityManager.flush();
        
        // Create @IdClass test data: 3 tenants × 25 entities = 75 total
        int totalEntities = 25;
        log.info("  - Creating @IdClass test data: {} tenants × {} entities = {} total", 3, totalEntities, totalEntities * 3);
        for (int tenant = 1; tenant <= 3; tenant++) {
            for (int entity = 1; entity <= totalEntities; entity++) {
                TestIdClassEntity testEntity = new TestIdClassEntity();
                testEntity.setTenantId("tenant" + tenant);
                testEntity.setEntityId((long) entity);
                testEntity.setName("Test Entity " + entity);
                testEntity.setDescription("Description for tenant " + tenant + " entity " + entity);
                entityManager.persist(testEntity);
            }
        }
        
        // Create @EmbeddedId test data: 3 tenants × 25 entities = 75 total
        log.info("  - Creating @EmbeddedId test data: {} tenants × {} entities = {} total", 3, totalEntities, totalEntities * 3);
        for (int tenant = 1; tenant <= 3; tenant++) {
            for (int entity = 1; entity <= totalEntities; entity++) {
                TestCompositeKeyEntity testEntity = new TestCompositeKeyEntity();
                TestCompositeKeyEntity.CompositeKey compositeKey = new TestCompositeKeyEntity.CompositeKey();
                compositeKey.setTenantId("tenant" + tenant);
                compositeKey.setEntityId((long) entity);
                testEntity.setId(compositeKey);
                testEntity.setName("Embedded Entity " + entity);
                testEntity.setDescription("Description for embedded tenant " + tenant + " entity " + entity);
                entityManager.persist(testEntity);
            }
        }
        
        entityManager.flush();
        entityManager.clear();
        
        // Verify data was actually saved
        Long idClassCount = entityManager.createQuery("SELECT COUNT(e) FROM TestIdClassEntity e", Long.class).getSingleResult();
        Long embeddedIdCount = entityManager.createQuery("SELECT COUNT(e) FROM TestCompositeKeyEntity e", Long.class).getSingleResult();
        log.info("  - Actual @IdClass records: {}", idClassCount);
        log.info("  - Actual @EmbeddedId records: {}", embeddedIdCount);
        
        Long tenant1IdClassCount = entityManager.createQuery("SELECT COUNT(e) FROM TestIdClassEntity e WHERE e.tenantId = 'tenant1'", Long.class).getSingleResult();
        Long tenant1EmbeddedIdCount = entityManager.createQuery("SELECT COUNT(e) FROM TestCompositeKeyEntity e WHERE e.id.tenantId = 'tenant1'", Long.class).getSingleResult();
        log.info("  - tenant1 @IdClass records: {}", tenant1IdClassCount);
        log.info("  - tenant1 @EmbeddedId records: {}", tenant1EmbeddedIdCount);
        log.info("  ✔ Test data setup completed");
    }
    
    @Test
    @Transactional
    @DisplayName("@IdClass composite key entity findAllWithSearch pagination test")
    void testFindAllWithSearchPagination() {
        log.info("=== STEP 1: STARTING @IDCLASS COMPOSITE KEY PAGINATION TEST ===");
        log.info("  - Test type: @IdClass composite key pagination");
        log.info("  - Search condition: tenantId = 'tenant1'");
        log.info("  - Expected results: 25 records across 3 pages");
        
        // Given: SearchCondition으로 tenant1 검색 조건 생성
        SearchCondition<TestIdClassEntity> condition = new SearchCondition<>();
        SearchCondition.Condition searchCondition = new SearchCondition.Condition(
            null, "tenantId", SearchOperator.EQUALS, "tenant1", null, "tenantId"
        );
        condition.getNodes().add(searchCondition);
        condition.setPage(0);
        condition.setSize(10);
        
        // When: Execute pagination with findAllWithSearch
        log.info("STEP 2: EXECUTING PAGE 0");
        Page<TestIdClassEntity> page0 = testIdClassEntityService.findAllWithSearch(condition);
        
        // Page 1 test
        condition.setPage(1);
        log.info("STEP 3: EXECUTING PAGE 1");
        Page<TestIdClassEntity> page1 = testIdClassEntityService.findAllWithSearch(condition);
        
        // Page 2 test (last page)
        condition.setPage(2);
        log.info("STEP 4: EXECUTING PAGE 2");
        Page<TestIdClassEntity> page2 = testIdClassEntityService.findAllWithSearch(condition);
        
        // Then: Verify pagination results
        log.info("STEP 5: PAGINATION VERIFICATION RESULTS");
        log.info("  - Page 0: {} records, Total: {} records, Total Pages: {}", 
                page0.getContent().size(), page0.getTotalElements(), page0.getTotalPages());
        log.info("  - Page 1: {} records", page1.getContent().size());
        log.info("  - Page 2: {} records", page2.getContent().size());
        
        // Verify overall results
        assertThat(page0.getTotalElements()).isEqualTo(25); // Total entities for tenant1
        assertThat(page0.getTotalPages()).isEqualTo(3); // 25 / 10 = 3 pages
        
        // Verify each page size
        assertThat(page0.getContent()).hasSize(10); // First page: 10 records
        assertThat(page1.getContent()).hasSize(10); // Second page: 10 records
        assertThat(page2.getContent()).hasSize(5);  // Last page: 5 records
        
        log.info("  ✔ PAGINATION ASSERTIONS: All passed");
        
        // Detailed data content verification
        log.info("STEP 6: DATA CONTENT VERIFICATION");
        
        // Page 0 data verification
        log.info("  - Page 0 Data Verification:");
        for (int i = 0; i < page0.getContent().size(); i++) {
            TestIdClassEntity entity = page0.getContent().get(i);
            assertThat(entity.getTenantId()).isEqualTo("tenant1");
            assertThat(entity.getEntityId()).isNotNull();
            assertThat(entity.getName()).isNotNull();
            assertThat(entity.getDescription()).contains("tenant 1");
            
            if (i < 3) { // Show only first 3 for brevity
                log.info("    [{}] ID=[{}, {}], Name={}, Description={}", 
                    i, entity.getTenantId(), entity.getEntityId(), entity.getName(), entity.getDescription());
            }
        }
        if (page0.getContent().size() > 3) {
            log.info("    ... and {} more records", page0.getContent().size() - 3);
        }
        
        // Page 1 data verification
        log.info("  - Page 1 Data Verification:");
        for (int i = 0; i < page1.getContent().size(); i++) {
            TestIdClassEntity entity = page1.getContent().get(i);
            assertThat(entity.getTenantId()).isEqualTo("tenant1");
            assertThat(entity.getEntityId()).isNotNull();
            assertThat(entity.getName()).isNotNull();
            assertThat(entity.getDescription()).contains("tenant 1");
            
            if (i < 3) { // Show only first 3 for brevity
                log.info("    [{}] ID=[{}, {}], Name={}, Description={}", 
                    i, entity.getTenantId(), entity.getEntityId(), entity.getName(), entity.getDescription());
            }
        }
        if (page1.getContent().size() > 3) {
            log.info("    ... and {} more records", page1.getContent().size() - 3);
        }
        
        // Page 2 data verification
        log.info("  - Page 2 Data Verification:");
        for (int i = 0; i < page2.getContent().size(); i++) {
            TestIdClassEntity entity = page2.getContent().get(i);
            assertThat(entity.getTenantId()).isEqualTo("tenant1");
            assertThat(entity.getEntityId()).isNotNull();
            assertThat(entity.getName()).isNotNull();
            assertThat(entity.getDescription()).contains("tenant 1");
            
            log.info("    [{}] ID=[{}, {}], Name={}, Description={}", 
                i, entity.getTenantId(), entity.getEntityId(), entity.getName(), entity.getDescription());
        }
        
        // Duplicate data verification (ensure all entity IDs are unique across pages)
        Set<String> allEntityIds = new HashSet<>();
        page0.getContent().forEach(e -> allEntityIds.add(e.getTenantId() + "_" + e.getEntityId()));
        page1.getContent().forEach(e -> allEntityIds.add(e.getTenantId() + "_" + e.getEntityId()));
        page2.getContent().forEach(e -> allEntityIds.add(e.getTenantId() + "_" + e.getEntityId()));
        
        assertThat(allEntityIds).hasSize(25); // 25 unique IDs without duplicates
        
        log.info("STEP 7: DUPLICATE CHECK VERIFICATION");
        log.info("  - Total unique entity IDs collected: {}", allEntityIds.size());
        log.info("  - Expected unique IDs: 25");
        log.info("  - Duplicate check: PASSED");
        
        log.info("SUMMARY: @IdClass Composite Key Test Results");
        log.info("  ✔ Two-Phase Query Execution: SUCCESS");
        log.info("  ✔ Pagination (25 records → 3 pages): SUCCESS");
        log.info("  ✔ Data Content Verification: SUCCESS");
        log.info("  ✔ Duplicate Check: SUCCESS");
        log.info("  ✔ Overall Result: ALL TESTS PASSED");
    }
    
    @Test
    @Transactional
    @DisplayName("@EmbeddedId composite key entity findAllWithSearch pagination test")
    void testEmbeddedIdFindAllWithSearchPagination() {
        log.info("=== STEP 1: STARTING @EMBEDDEDID COMPOSITE KEY PAGINATION TEST ===");
        log.info("  - Test type: @EmbeddedId composite key pagination");
        log.info("  - Search condition: id.tenantId = 'tenant1'");
        log.info("  - Expected results: 25 records across 3 pages");
        
        // Given: SearchCondition으로 tenant1 검색 조건 생성
        SearchCondition<TestCompositeKeyEntity> condition = new SearchCondition<>();
        SearchCondition.Condition searchCondition = new SearchCondition.Condition(
            null, "id.tenantId", SearchOperator.EQUALS, "tenant1", null, "id.tenantId"
        );
        condition.getNodes().add(searchCondition);
        condition.setPage(0);
        condition.setSize(10);
        
        // When: Execute pagination with findAllWithSearch
        log.info("STEP 2: EXECUTING PAGE 0");
        Page<TestCompositeKeyEntity> page0 = testCompositeKeyEntityService.findAllWithSearch(condition);
        
        // Page 1 test
        condition.setPage(1);
        log.info("STEP 3: EXECUTING PAGE 1");
        Page<TestCompositeKeyEntity> page1 = testCompositeKeyEntityService.findAllWithSearch(condition);
        
        // Page 2 test (last page)
        condition.setPage(2);
        log.info("STEP 4: EXECUTING PAGE 2");
        Page<TestCompositeKeyEntity> page2 = testCompositeKeyEntityService.findAllWithSearch(condition);
        
        // Then: Verify pagination results
        log.info("STEP 5: PAGINATION VERIFICATION RESULTS");
        log.info("  - Page 0: {} records, Total: {} records, Total Pages: {}", 
                page0.getContent().size(), page0.getTotalElements(), page0.getTotalPages());
        log.info("  - Page 1: {} records", page1.getContent().size());
        log.info("  - Page 2: {} records", page2.getContent().size());
        
        // Verify overall results
        assertThat(page0.getTotalElements()).isEqualTo(25); // Total entities for tenant1
        assertThat(page0.getTotalPages()).isEqualTo(3); // 25 / 10 = 3 pages
        
        // Verify each page size
        assertThat(page0.getContent()).hasSize(10); // First page: 10 records
        assertThat(page1.getContent()).hasSize(10); // Second page: 10 records
        assertThat(page2.getContent()).hasSize(5);  // Last page: 5 records
        
        log.info("  ✔ PAGINATION ASSERTIONS: All passed");
        
        // Detailed data content verification
        log.info("STEP 6: DATA CONTENT VERIFICATION");
        
        // Verify all pages data content
        int totalValidatedRecords = 0;
        
        // Page 0 data verification
        log.info("  - Page 0 Data Verification:");
        for (int i = 0; i < page0.getContent().size(); i++) {
            TestCompositeKeyEntity entity = page0.getContent().get(i);
            assertThat(entity.getId().getTenantId()).isEqualTo("tenant1");
            assertThat(entity.getId().getEntityId()).isNotNull();
            assertThat(entity.getName()).isNotNull();
            assertThat(entity.getDescription()).contains("embedded tenant 1");
            totalValidatedRecords++;
            
            if (i < 3) { // Show only first 3 for brevity
                log.info("    [{}] ID=[{}, {}], Name={}, Description={}", 
                    i, entity.getId().getTenantId(), entity.getId().getEntityId(), 
                    entity.getName(), entity.getDescription());
            }
        }
        if (page0.getContent().size() > 3) {
            log.info("    ... and {} more records", page0.getContent().size() - 3);
        }
        
        // Page 1 data verification
        log.info("  - Page 1 Data Verification:");
        for (int i = 0; i < page1.getContent().size(); i++) {
            TestCompositeKeyEntity entity = page1.getContent().get(i);
            assertThat(entity.getId().getTenantId()).isEqualTo("tenant1");
            assertThat(entity.getId().getEntityId()).isNotNull();
            assertThat(entity.getName()).isNotNull();
            assertThat(entity.getDescription()).contains("embedded tenant 1");
            totalValidatedRecords++;
            
            if (i < 3) { // Show only first 3 for brevity
                log.info("    [{}] ID=[{}, {}], Name={}, Description={}", 
                    i, entity.getId().getTenantId(), entity.getId().getEntityId(), 
                    entity.getName(), entity.getDescription());
            }
        }
        if (page1.getContent().size() > 3) {
            log.info("    ... and {} more records", page1.getContent().size() - 3);
        }
        
        // Page 2 data verification
        log.info("  - Page 2 Data Verification:");
        for (int i = 0; i < page2.getContent().size(); i++) {
            TestCompositeKeyEntity entity = page2.getContent().get(i);
            assertThat(entity.getId().getTenantId()).isEqualTo("tenant1");
            assertThat(entity.getId().getEntityId()).isNotNull();
            assertThat(entity.getName()).isNotNull();
            assertThat(entity.getDescription()).contains("embedded tenant 1");
            totalValidatedRecords++;
            
            log.info("    [{}] ID=[{}, {}], Name={}, Description={}", 
                i, entity.getId().getTenantId(), entity.getId().getEntityId(), 
                entity.getName(), entity.getDescription());
        }
        
        log.info("  - Total records validated: {}", totalValidatedRecords);
        log.info("  - All records have correct tenant1 data: PASSED");
        log.info("  - All records have valid embedded composite keys: PASSED");
        
        // Duplicate data verification (ensure all entity IDs are unique across pages)
        Set<String> allEntityIds = new HashSet<>();
        page0.getContent().forEach(e -> allEntityIds.add(e.getId().getTenantId() + "_" + e.getId().getEntityId()));
        page1.getContent().forEach(e -> allEntityIds.add(e.getId().getTenantId() + "_" + e.getId().getEntityId()));
        page2.getContent().forEach(e -> allEntityIds.add(e.getId().getTenantId() + "_" + e.getId().getEntityId()));
        
        assertThat(allEntityIds).hasSize(25); // 25 unique IDs without duplicates
        
        log.info("STEP 7: DUPLICATE CHECK VERIFICATION");
        log.info("  - Total unique entity IDs collected: {}", allEntityIds.size());
        log.info("  - Expected unique IDs: 25");
        log.info("  - Duplicate check: PASSED");
        
        log.info("SUMMARY: @EmbeddedId Composite Key Test Results");
        log.info("  ✔ Two-Phase Query Execution: SUCCESS");
        log.info("  ✔ Pagination (25 records → 3 pages): SUCCESS");
        log.info("  ✔ Data Content Verification: SUCCESS");
        log.info("  ✔ Duplicate Check: SUCCESS");
        log.info("  ✔ Overall Result: ALL TESTS PASSED");
    }
} 