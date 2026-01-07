package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.utils.JsonTypeDetector;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestJsonFieldEntityDTO;
import dev.simplecore.searchable.test.entity.TestJsonFieldEntity;
import dev.simplecore.searchable.test.repository.TestJsonFieldEntityRepository;
import dev.simplecore.searchable.test.service.TestJsonFieldEntityService;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class JsonFieldSearchTest {

    @Autowired
    private TestJsonFieldEntityService service;

    @Autowired
    private TestJsonFieldEntityRepository repository;

    @BeforeEach
    void setUp() {
        JsonTypeDetector.clearCache();
        SearchableFieldUtils.clearCache();
        repository.deleteAll();

        Map<String, String> descriptionEn = new HashMap<>();
        descriptionEn.put("en", "Welcome to our application");
        descriptionEn.put("ko", "애플리케이션에 오신 것을 환영합니다");

        Map<String, String> metadataJson = new HashMap<>();
        metadataJson.put("version", "1.0.0");
        metadataJson.put("author", "Test Author");

        TestJsonFieldEntity entity1 = TestJsonFieldEntity.builder()
                .name("Test Entity 1")
                .descriptionI18n(descriptionEn)
                .metadata(metadataJson)
                .build();

        Map<String, String> descriptionDe = new HashMap<>();
        descriptionDe.put("en", "Hello World");
        descriptionDe.put("de", "Hallo Welt");

        Map<String, String> metadataJson2 = new HashMap<>();
        metadataJson2.put("version", "2.0.0");
        metadataJson2.put("author", "Another Author");

        TestJsonFieldEntity entity2 = TestJsonFieldEntity.builder()
                .name("Test Entity 2")
                .descriptionI18n(descriptionDe)
                .metadata(metadataJson2)
                .build();

        repository.save(entity1);
        repository.save(entity2);
    }

    @Test
    @DisplayName("JSON field search - CONTAINS operator on Map field with @Convert and columnDefinition=TEXT")
    void testContainsOnJsonField() {
        log.info("=== JSON Field CONTAINS Search Test ===");

        SearchCondition<TestJsonFieldEntityDTO> condition =
                SearchConditionBuilder.create(TestJsonFieldEntityDTO.class)
                        .where(w -> w.contains("descriptionI18n", "Welcome"))
                        .page(0)
                        .size(10)
                        .build();

        Page<TestJsonFieldEntity> result = service.findAllWithSearch(condition);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Entity 1", result.getContent().get(0).getName());

        log.info("CONTAINS on JSON field: Found {} entities", result.getTotalElements());
    }

    @Test
    @DisplayName("JSON field search - STARTS_WITH operator on Map field")
    void testStartsWithOnJsonField() {
        log.info("=== JSON Field STARTS_WITH Search Test ===");

        SearchCondition<TestJsonFieldEntityDTO> condition =
                SearchConditionBuilder.create(TestJsonFieldEntityDTO.class)
                        .where(w -> w.startsWith("metadata", "{\""))
                        .page(0)
                        .size(10)
                        .build();

        Page<TestJsonFieldEntity> result = service.findAllWithSearch(condition);

        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 1);

        log.info("STARTS_WITH on JSON field: Found {} entities", result.getTotalElements());
    }

    @Test
    @DisplayName("JSON field search - NOT_CONTAINS operator on Map field")
    void testNotContainsOnJsonField() {
        log.info("=== JSON Field NOT_CONTAINS Search Test ===");

        SearchCondition<TestJsonFieldEntityDTO> condition =
                SearchConditionBuilder.create(TestJsonFieldEntityDTO.class)
                        .where(w -> w.notContains("descriptionI18n", "Welcome"))
                        .page(0)
                        .size(10)
                        .build();

        Page<TestJsonFieldEntity> result = service.findAllWithSearch(condition);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Entity 2", result.getContent().get(0).getName());

        log.info("NOT_CONTAINS on JSON field: Found {} entities", result.getTotalElements());
    }

    @Test
    @DisplayName("JSON field search - ENDS_WITH operator on Map field")
    void testEndsWithOnJsonField() {
        log.info("=== JSON Field ENDS_WITH Search Test ===");

        SearchCondition<TestJsonFieldEntityDTO> condition =
                SearchConditionBuilder.create(TestJsonFieldEntityDTO.class)
                        .where(w -> w.endsWith("metadata", "}"))
                        .page(0)
                        .size(10)
                        .build();

        Page<TestJsonFieldEntity> result = service.findAllWithSearch(condition);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());

        log.info("ENDS_WITH on JSON field: Found {} entities", result.getTotalElements());
    }

    @Test
    @DisplayName("JSON field search - Combined with regular string field")
    void testCombinedJsonAndStringFieldSearch() {
        log.info("=== Combined JSON and String Field Search Test ===");

        SearchCondition<TestJsonFieldEntityDTO> condition =
                SearchConditionBuilder.create(TestJsonFieldEntityDTO.class)
                        .where(w -> w
                                .contains("name", "Entity 1")
                                .and(a -> a.contains("descriptionI18n", "Welcome"))
                        )
                        .page(0)
                        .size(10)
                        .build();

        Page<TestJsonFieldEntity> result = service.findAllWithSearch(condition);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Entity 1", result.getContent().get(0).getName());

        log.info("Combined search: Found {} entities", result.getTotalElements());
    }

    @Test
    @DisplayName("JsonTypeDetector - Detect @Convert with JSON converter and columnDefinition=TEXT")
    void testJsonTypeDetectorWithConvertAnnotation() {
        boolean isJson = JsonTypeDetector.isJsonTypedField(
                null,
                TestJsonFieldEntity.class,
                "descriptionI18n"
        );

        assertTrue(isJson, "descriptionI18n should be detected as JSON-typed field");
    }

    @Test
    @DisplayName("JsonTypeDetector - Field without @Convert or columnDefinition should not be detected")
    void testJsonTypeDetectorWithRegularStringField() {
        boolean isJson = JsonTypeDetector.isJsonTypedField(
                null,
                TestJsonFieldEntity.class,
                "name"
        );

        assertFalse(isJson, "name (String field) should not be detected as JSON-typed field");
    }
}
