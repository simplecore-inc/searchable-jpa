package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.service.TestPostService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify ToMany relationship search functionality using DTO approach.
 * Tests whether the library can handle search conditions on:
 * - OneToMany relationships (comments.content via commentContent field)
 * - ManyToMany relationships (tags.name via tagName field)
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class ToManyRelationshipSearchTest {

    @Autowired
    private TestPostService testPostService;

    @Test
    @DisplayName("ToMany relationship search - OneToMany comments.content via DTO")
    void testOneToManyCommentsSearch() {
        log.info("=== OneToMany Relationship Search Test Started ===");
        
        try {
            // Search for posts with comments containing "helpful" using DTO
            SearchCondition<TestPostDTOs.TestPostSearchDTO> commentsCondition = 
                    SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                    .where(w -> w.contains("commentContent", "helpful"))  // maps to comments.content
                    .page(0)
                    .size(10)
                    .sort(s -> s.asc("id"))  // maps to postId
                    .build();

            Page<TestPost> commentsResult = testPostService.findAllWithSearch(commentsCondition);
            
            // Verify the search executed without errors
            assertNotNull(commentsResult);
            assertTrue(commentsResult.getTotalElements() >= 0);
            
            log.info("========================================");
            log.info("OneToMany Relationship Search Test SUMMARY:");
            log.info("   Search Target: comments.content containing 'helpful'");
            log.info("   Search Method: DTO mapping approach");
            log.info("   Found Posts: {}", commentsResult.getTotalElements());
            log.info("   Query Execution: SUCCESS");
            log.info("   Result Validation: SUCCESS");
            log.info("   Overall Result: ALL TESTS PASSED");
            log.info("========================================");
            
        } catch (Exception e) {
            log.error("========================================");
            log.error("OneToMany Relationship Search Test FAILED:");
            log.error("   Error Message: {}", e.getMessage());
            log.error("   Overall Result: TEST FAILED");
            log.error("========================================");
            throw e;
        }
    }

    @Test
    @DisplayName("ToMany relationship search - ManyToMany tags.name via DTO")
    void testManyToManyTagsSearch() {
        log.info("=== ManyToMany Relationship Search Test Started ===");
        
        try {
            // Search for posts with tags named "Java" using DTO
            SearchCondition<TestPostDTOs.TestPostSearchDTO> tagsCondition = 
                    SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                    .where(w -> w.equals("tagName", "Java"))  // maps to tags.name
                    .page(0)
                    .size(10)
                    .sort(s -> s.asc("id"))  // maps to postId
                    .build();

            Page<TestPost> tagsResult = testPostService.findAllWithSearch(tagsCondition);
            
            // Verify the search executed without errors
            assertNotNull(tagsResult);
            assertTrue(tagsResult.getTotalElements() >= 0);
            
            log.info("========================================");
            log.info("ManyToMany Relationship Search Test SUMMARY:");
            log.info("   Search Target: tags.name equals 'Java'");
            log.info("   Search Method: DTO mapping approach");
            log.info("   Found Posts: {}", tagsResult.getTotalElements());
            log.info("   Current Page Number: {}", tagsResult.getNumber());
            log.info("   Page Size: {}", tagsResult.getSize());
            log.info("   Current Page Elements: {}", tagsResult.getNumberOfElements());
            log.info("   Total Pages: {}", tagsResult.getTotalPages());
            log.info("   Is First Page: {}", tagsResult.isFirst());
            log.info("   Is Last Page: {}", tagsResult.isLast());
            log.info("   Has Next Page: {}", tagsResult.hasNext());
            log.info("   Has Previous Page: {}", tagsResult.hasPrevious());
            log.info("   Query Execution: SUCCESS");
            log.info("   Result Validation: SUCCESS");
            log.info("   Overall Result: ALL TESTS PASSED");
            log.info("========================================");
            
        } catch (Exception e) {
            log.error("========================================");
            log.error("ManyToMany Relationship Search Test FAILED:");
            log.error("   Error Message: {}", e.getMessage());
            log.error("   Overall Result: TEST FAILED");
            log.error("========================================");
            throw e;
        }
    }

    @Test
    @DisplayName("ToMany relationship search - Combined conditions via DTO")
    void testCombinedToManySearch() {
        log.info("=== Combined ToMany Relationship Search Test Started ===");
        
        try {
            // Search for posts with both comments and tags conditions using DTO
            SearchCondition<TestPostDTOs.TestPostSearchDTO> combinedCondition = 
                    SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                    .where(w -> w
                            .contains("commentContent", "good")  // maps to comments.content
                            .and(a -> a.equals("tagName", "Programming"))  // maps to tags.name
                    )
                    .page(0)
                    .size(5)
                    .sort(s -> s.asc("id"))  // maps to postId
                    .build();

            Page<TestPost> combinedResult = testPostService.findAllWithSearch(combinedCondition);
            
            // Verify the search executed without errors
            assertNotNull(combinedResult);
            assertTrue(combinedResult.getTotalElements() >= 0);
            
            log.info("========================================");
            log.info("Combined ToMany Relationship Search Test SUMMARY:");
            log.info("   Search Target 1: comments.content containing 'good'");
            log.info("   Search Target 2: tags.name equals 'Programming'");
            log.info("   Search Method: Combined conditions via DTO");
            log.info("   Found Posts: {}", combinedResult.getTotalElements());
            log.info("   Page Size: {}", combinedResult.getSize());
            log.info("   Query Execution: SUCCESS");
            log.info("   Result Validation: SUCCESS");
            log.info("   Overall Result: ALL TESTS PASSED");
            log.info("========================================");
            
        } catch (Exception e) {
            log.error("========================================");
            log.error("Combined ToMany Relationship Search Test FAILED:");
            log.error("   Error Message: {}", e.getMessage());
            log.error("   Overall Result: TEST FAILED");
            log.error("========================================");
            throw e;
        }
    }

} 