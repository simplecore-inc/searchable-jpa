package dev.simplecore.searchable.core.service.cursor;

import dev.simplecore.searchable.test.entity.TestPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import dev.simplecore.searchable.test.config.TestConfig;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:calculator_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
class CursorCalculatorTest {

    @Mock
    private JpaSpecificationExecutor<TestPost> specificationExecutor;

    private CursorCalculator<TestPost> cursorCalculator;

    @BeforeEach
    void setUp() {
        cursorCalculator = new CursorCalculator<>(specificationExecutor, TestPost.class);
    }

    @Test
    void testCalculateCursorValues_FirstPage() {
        // Given
        int targetOffset = -1; // Before first page
        Sort sort = Sort.by("title").ascending();

        // When
        Map<String, Object> result = cursorCalculator.calculateCursorValues(targetOffset, null, sort);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testCalculateCursorValues_WithValidOffset() {
        // Given
        int targetOffset = 9; // 10th record (0-based)
        Sort sort = Sort.by("title").ascending();
        
        TestPost testPost = new TestPost();
        testPost.setId(10L);
        testPost.setTitle("Test Title");
        testPost.setViewCount(100L);
        testPost.setCreatedAt(LocalDateTime.now());

        Page<TestPost> mockPage = new PageImpl<>(List.of(testPost));
        when(specificationExecutor.findAll(eq(null), any(PageRequest.class)))
                .thenReturn(mockPage);

        // When
        Map<String, Object> result = cursorCalculator.calculateCursorValues(targetOffset, null, sort);

        // Then
        assertThat(result).containsEntry("title", "Test Title");
    }

    @Test
    void testExtractCursorValues_SingleField() {
        // Given
        TestPost testPost = new TestPost();
        testPost.setTitle("Test Title");
        testPost.setViewCount(150L);
        
        Sort sort = Sort.by("title").ascending();

        // When
        Map<String, Object> result = cursorCalculator.extractCursorValues(testPost, sort);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result).containsEntry("title", "Test Title");
    }

    @Test
    void testExtractCursorValues_MultipleFields() {
        // Given
        TestPost testPost = new TestPost();
        testPost.setTitle("Test Title");
        testPost.setViewCount(150L);
        testPost.setCreatedAt(LocalDateTime.of(2024, 1, 1, 12, 0));
        
        Sort sort = Sort.by("title").ascending()
                       .and(Sort.by("viewCount").descending())
                       .and(Sort.by("createdAt").ascending());

        // When
        Map<String, Object> result = cursorCalculator.extractCursorValues(testPost, sort);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("title", "Test Title");
        assertThat(result).containsEntry("viewCount", 150L);
        assertThat(result).containsEntry("createdAt", LocalDateTime.of(2024, 1, 1, 12, 0));
    }

    @Test
    void testExtractCursorValues_WithNullValues() {
        // Given
        TestPost testPost = new TestPost();
        testPost.setTitle("Test Title");
        testPost.setViewCount(null); // null value
        
        Sort sort = Sort.by("title").ascending()
                       .and(Sort.by("viewCount").descending());

        // When
        Map<String, Object> result = cursorCalculator.extractCursorValues(testPost, sort);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("title", "Test Title");
        assertThat(result).containsEntry("viewCount", null);
    }

    @Test
    void testCalculateCursorValues_EmptyResult() {
        // Given
        int targetOffset = 100;
        Sort sort = Sort.by("title").ascending();
        
        Page<TestPost> emptyPage = new PageImpl<>(Collections.emptyList());
        when(specificationExecutor.findAll(eq(null), any(PageRequest.class)))
                .thenReturn(emptyPage);

        // When
        Map<String, Object> result = cursorCalculator.calculateCursorValues(targetOffset, null, sort);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testCalculateCursorValues_ExceptionHandling() {
        // Given
        int targetOffset = 10;
        Sort sort = Sort.by("title").ascending();
        
        when(specificationExecutor.findAll(eq(null), any(PageRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When
        Map<String, Object> result = cursorCalculator.calculateCursorValues(targetOffset, null, sort);

        // Then
        assertThat(result).isEmpty(); // Should return empty map on exception
    }
} 