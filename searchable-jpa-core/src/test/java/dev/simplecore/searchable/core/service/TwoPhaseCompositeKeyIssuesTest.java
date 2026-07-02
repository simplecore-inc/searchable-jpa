package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestOrderItemSearchDTO;
import dev.simplecore.searchable.test.entity.TestIdClassEntity;
import dev.simplecore.searchable.test.entity.TestOrderItem;
import dev.simplecore.searchable.test.entity.TestOrderNote;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.service.TestIdClassEntityService;
import dev.simplecore.searchable.test.service.TestOrderItemService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the composite-key portions of Category 1:
 * 1-1 (embedded id attribute name resolved dynamically),
 * 1-2 (over-the-end page still reports the true total),
 * 1-3 (composite count is distinct through to-many joins),
 * 1-4 (sorting through a to-many relationship keeps pagination stable),
 * 1-5 (the internal ordering marker is never exposed on the returned pageable).
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class TwoPhaseCompositeKeyIssuesTest {

    private static final int ITEM_COUNT = 30;
    private static final int NOTES_PER_ITEM = 3;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestOrderItemService testOrderItemService;

    @Autowired
    private TestIdClassEntityService testIdClassEntityService;

    @BeforeEach
    @Transactional
    void setUp() {
        SearchableFieldUtils.clearCache();

        entityManager.createNativeQuery("DELETE FROM test_order_note").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM test_order_item").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM test_id_class_entity").executeUpdate();
        entityManager.flush();

        // @EmbeddedId items (attribute name is "orderItemId", not "id"), each with several notes.
        for (int line = 1; line <= ITEM_COUNT; line++) {
            TestOrderItem item = new TestOrderItem();
            item.setOrderItemId(new TestOrderItem.OrderItemKey("WH1", (long) line));
            item.setName("Item " + line);
            for (char c = 'a'; c < 'a' + NOTES_PER_ITEM; c++) {
                TestOrderNote note = new TestOrderNote();
                note.setContent(String.format("note-%02d-%c", line, c));
                item.addNote(note);
            }
            entityManager.persist(item);
        }

        // @IdClass entities: 25 for tenant1 (for 1-2 and 1-5).
        for (int entity = 1; entity <= 25; entity++) {
            TestIdClassEntity e = new TestIdClassEntity();
            e.setTenantId("tenant1");
            e.setEntityId((long) entity);
            e.setName("Entity " + entity);
            e.setDescription("desc " + entity);
            entityManager.persist(e);
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @Transactional
    @DisplayName("1-1: @EmbeddedId attribute name is resolved from the metamodel, not assumed to be 'id'")
    void embeddedIdAttributeNameResolved() {
        assertThat(SearchableFieldUtils.getEmbeddedIdAttributeName(entityManager, TestOrderItem.class))
                .isEqualTo("orderItemId");
        // Single primary key and @IdClass entities have no embedded id attribute.
        assertThat(SearchableFieldUtils.getEmbeddedIdAttributeName(entityManager, TestPost.class)).isNull();
        assertThat(SearchableFieldUtils.getEmbeddedIdAttributeName(entityManager, TestIdClassEntity.class)).isNull();
    }

    @Test
    @Transactional
    @DisplayName("1-1: paginating an @EmbeddedId entity whose id attribute is not 'id' works end-to-end")
    void embeddedIdPaginationWorks() {
        SearchCondition<TestOrderItemSearchDTO> condition =
                SearchConditionBuilder.create(TestOrderItemSearchDTO.class)
                        .where(w -> w.equals("warehouseCode", "WH1"))
                        .sort(s -> s.asc("lineNo"))
                        .page(0)
                        .size(10)
                        .build();

        Page<TestOrderItem> page = testOrderItemService.findAllWithSearch(condition);

        assertThat(page.getTotalElements()).isEqualTo(ITEM_COUNT);
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getContent().get(0).getOrderItemId().getWarehouseCode()).isEqualTo("WH1");
    }

    @Test
    @Transactional
    @DisplayName("1-2: requesting a page past the end still reports the true total (not 0)")
    void overTheEndPageReportsTrueTotal() {
        // @IdClass entity: 25 records, size 10 -> 3 pages. Request page index 5 (offset 50, empty).
        SearchCondition<TestIdClassEntity> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition(
                null, "tenantId", dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS,
                "tenant1", null, "tenantId"));
        condition.setPage(5);
        condition.setSize(10);

        Page<TestIdClassEntity> page = testIdClassEntityService.findAllWithSearch(condition);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    @Transactional
    @DisplayName("1-3: composite count is distinct through a to-many join (not inflated by child rows)")
    void compositeCountIsDistinctThroughToManyJoin() {
        // Filter that forces a join onto the to-many notes collection.
        SearchCondition<TestOrderItemSearchDTO> condition =
                SearchConditionBuilder.create(TestOrderItemSearchDTO.class)
                        .where(w -> w.contains("noteContent", "note-"))
                        .page(0)
                        .size(10)
                        .build();

        Page<TestOrderItem> page = testOrderItemService.findAllWithSearch(condition);

        // Each of the 30 items has NOTES_PER_ITEM notes; a naive count(root) would report 90.
        assertThat(page.getTotalElements()).isEqualTo(ITEM_COUNT);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(10);
    }

    @Test
    @Transactional
    @DisplayName("1-4: sorting a composite-key entity through a to-many field keeps pages disjoint")
    void toManySortKeepsPagesDisjoint() {
        Set<String> seenKeys = new HashSet<>();
        int totalSeen = 0;
        for (int pageIndex = 0; pageIndex < 3; pageIndex++) {
            SearchCondition<TestOrderItemSearchDTO> condition =
                    SearchConditionBuilder.create(TestOrderItemSearchDTO.class)
                            .where(w -> w.equals("warehouseCode", "WH1"))
                            .sort(s -> s.asc("noteContent"))
                            .page(pageIndex)
                            .size(10)
                            .build();

            Page<TestOrderItem> page = testOrderItemService.findAllWithSearch(condition);
            totalSeen += page.getContent().size();
            page.getContent().forEach(item ->
                    seenKeys.add(item.getOrderItemId().getWarehouseCode() + "#" + item.getOrderItemId().getLineNo()));
        }

        // No duplicates and no missing rows across the three pages.
        assertThat(totalSeen).isEqualTo(ITEM_COUNT);
        assertThat(seenKeys).hasSize(ITEM_COUNT);
    }

    @Test
    @Transactional
    @DisplayName("1-5: the internal composite-key ordering marker is not exposed on the returned pageable")
    void compositeKeyMarkerNotExposed() {
        SearchCondition<TestIdClassEntity> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition(
                null, "tenantId", dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS,
                "tenant1", null, "tenantId"));
        condition.setPage(0);
        condition.setSize(10);

        Page<TestIdClassEntity> page = testIdClassEntityService.findAllWithSearch(condition);

        boolean markerExposed = page.getPageable().getSort().stream()
                .anyMatch(order -> SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(order.getProperty()));
        assertThat(markerExposed).isFalse();
    }
}
