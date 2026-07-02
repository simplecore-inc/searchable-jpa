package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableConfigurationException;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestUpdatableSearchDTO;
import dev.simplecore.searchable.test.entity.TestIdClassEntity;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import dev.simplecore.searchable.test.repository.TestPostRepository;
import dev.simplecore.searchable.test.service.TestIdClassEntityService;
import dev.simplecore.searchable.test.service.TestUpdatableEntityService;
import dev.simplecore.searchable.test.support.TestSqlCapture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies Category 5 (service layer) issues 5-1 through 5-7.
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ServiceLayerIssuesTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestUpdatableEntityService testUpdatableEntityService;

    @Autowired
    private TestIdClassEntityService testIdClassEntityService;

    @Autowired
    private TestPostRepository testPostRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        SearchableFieldUtils.clearCache();

        entityManager.createNativeQuery("DELETE FROM test_updatable").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM test_id_class_entity").executeUpdate();
        entityManager.flush();

        for (int i = 1; i <= 5; i++) {
            TestUpdatableEntity entity = new TestUpdatableEntity();
            entity.setName("Item " + i);
            entity.setStatus("ACTIVE");
            entity.setCategory("OLD");
            entityManager.persist(entity);
        }

        for (int i = 1; i <= 5; i++) {
            TestIdClassEntity entity = new TestIdClassEntity();
            entity.setTenantId("tenant1");
            entity.setEntityId((long) i);
            entity.setName("Entity " + i);
            entity.setDescription("original");
            entityManager.persist(entity);
        }

        entityManager.flush();
        entityManager.clear();
    }

    // ---- 5-2: inherited @MappedSuperclass field is updated ----

    @Test
    @Transactional
    @DisplayName("5-2: updateWithSearch copies an inherited (@MappedSuperclass) field")
    void updateCopiesInheritedField() {
        SearchCondition<TestUpdatableSearchDTO> condition =
                SearchConditionBuilder.create(TestUpdatableSearchDTO.class)
                        .where(w -> w.equals("status", "ACTIVE"))
                        .build();

        TestUpdatableEntity updateData = new TestUpdatableEntity();
        updateData.setCategory("NEW");   // inherited field

        long updated = testUpdatableEntityService.updateWithSearch(condition, updateData);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(5);
        List<TestUpdatableEntity> all = testUpdatableEntityService.findAllWithSearch(
                SearchConditionBuilder.create(TestUpdatableSearchDTO.class)
                        .where(w -> w.equals("status", "ACTIVE")).build()).getContent();
        assertThat(all).isNotEmpty();
        assertThat(all).allSatisfy(e -> assertThat(e.getCategory()).isEqualTo("NEW"));
    }

    // ---- 5-1: composite-key updateWithSearch works (no getId() assumption) ----

    @Test
    @Transactional
    @DisplayName("5-1/5-3: updateWithSearch on a composite-key entity succeeds without a configuration exception")
    void updateCompositeKeyEntity() {
        SearchCondition<TestIdClassEntity> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition(
                null, "tenantId", SearchOperator.EQUALS, "tenant1", null, "tenantId"));

        TestIdClassEntity updateData = new TestIdClassEntity();
        updateData.setDescription("updated");

        long updated = testIdClassEntityService.updateWithSearch(condition, updateData);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(5);
        List<TestIdClassEntity> all = entityManager.createQuery(
                "select e from TestIdClassEntity e where e.tenantId = 'tenant1'", TestIdClassEntity.class).getResultList();
        assertThat(all).allSatisfy(e -> assertThat(e.getDescription()).isEqualTo("updated"));
        // The composite key itself must be preserved (null fields in updateData are skipped).
        assertThat(all).allSatisfy(e -> assertThat(e.getEntityId()).isNotNull());
    }

    // ---- 5-5: updateWithSearch does not re-query each row via findById ----

    @Test
    @Transactional
    @DisplayName("5-5: updateWithSearch issues no per-row findById query")
    void updateDoesNotReQueryEachRow() {
        SearchCondition<TestUpdatableSearchDTO> condition =
                SearchConditionBuilder.create(TestUpdatableSearchDTO.class)
                        .where(w -> w.equals("status", "ACTIVE"))
                        .build();

        TestUpdatableEntity updateData = new TestUpdatableEntity();
        updateData.setCategory("NEW");

        TestSqlCapture.start();
        long updated = testUpdatableEntityService.updateWithSearch(condition, updateData);
        TestSqlCapture.stop();

        assertThat(updated).isEqualTo(5);
        // Only the initial findAll SELECT should run; there must be no per-row findById (which would
        // add 5 more SELECTs). Allow a small margin but far below the row count.
        long selects = TestSqlCapture.captured().stream()
                .filter(sql -> sql.trim().toLowerCase().startsWith("select"))
                .count();
        assertThat(selects).isLessThan(5L);
    }

    // ---- 5-4: projection must be an interface ----

    @Test
    @Transactional
    @DisplayName("5-4: findAllWithSearch accepts a projection interface and rejects a concrete class")
    void projectionMustBeInterface() {
        SearchCondition<TestUpdatableSearchDTO> condition =
                SearchConditionBuilder.create(TestUpdatableSearchDTO.class)
                        .where(w -> w.equals("status", "ACTIVE"))
                        .build();

        // Interface projection works.
        Page<NameView> projected = testUpdatableEntityService.findAllWithSearch(condition, NameView.class);
        assertThat(projected.getContent()).isNotEmpty();
        assertThat(projected.getContent().get(0).getName()).isNotBlank();

        // Concrete class is rejected.
        assertThatThrownBy(() -> testUpdatableEntityService.findAllWithSearch(condition, ConcreteProjection.class))
                .isInstanceOf(SearchableConfigurationException.class);
    }

    // ---- 5-6: extending through an intermediate abstract class resolves the entity type ----

    @Test
    @Transactional
    @DisplayName("5-6: a service extending through an intermediate abstract class resolves the entity type")
    void intermediateAbstractClassResolvesEntityType() {
        ConcretePostService service = new ConcretePostService(testPostRepository, entityManager);

        SearchCondition<dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO> condition =
                SearchConditionBuilder.create(dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO.class)
                        .where(w -> w.equals("status", dev.simplecore.searchable.test.enums.TestPostStatus.PUBLISHED))
                        .page(0).size(5)
                        .build();

        // Would previously throw ClassCastException on construction; must now work end-to-end.
        Page<TestPost> page = service.findAllWithSearch(condition);
        assertThat(page).isNotNull();
    }

    // ---- 5-7: constructor rejects null arguments eagerly ----

    @Test
    @Transactional
    @DisplayName("5-7: SearchableServiceDelegate constructor validates its arguments")
    void constructorRejectsNulls() {
        assertThatThrownBy(() -> new SearchableServiceDelegate<>(null, entityManager, TestPost.class))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SearchableServiceDelegate<>(testPostRepository, null, TestPost.class))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SearchableServiceDelegate<>(testPostRepository, entityManager, null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Spring Data projection interface.
     */
    public interface NameView {
        String getName();
    }

    /**
     * Concrete (non-interface) type, which must be rejected as a projection.
     */
    public static class ConcreteProjection {
        public String getName() {
            return null;
        }
    }

    /**
     * Intermediate abstract service to exercise multi-level generic resolution (5-6).
     */
    abstract static class AbstractPostService extends DefaultSearchableService<TestPost, Long> {
        protected AbstractPostService(JpaRepository<TestPost, Long> repository, EntityManager entityManager) {
            super(repository, entityManager);
        }
    }

    static class ConcretePostService extends AbstractPostService {
        ConcretePostService(JpaRepository<TestPost, Long> repository, EntityManager entityManager) {
            super(repository, entityManager);
        }
    }
}
