package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.test.dto.InheritedSearchDTOs.BaseSearchDTO;
import dev.simplecore.searchable.test.dto.InheritedSearchDTOs.ChildSearchDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies issue 6-1: inherited {@code @SearchableField} entityField/sortField mappings must be
 * resolved by walking the class hierarchy, matching the behavior of SearchableFieldValidator.
 */
class SearchableFieldUtilsInheritanceTest {

    @Test
    @DisplayName("6-1: inherited entityField mapping is resolved for subclass DTO")
    void inheritedEntityFieldIsResolved() {
        // createdDate is declared in BaseSearchDTO with entityField = "createdAt"
        assertThat(SearchableFieldUtils.getEntityFieldFromDto(ChildSearchDTO.class, "createdDate"))
                .isEqualTo("createdAt");
    }

    @Test
    @DisplayName("6-1: inherited sortField mapping is resolved for subclass DTO")
    void inheritedSortFieldIsResolved() {
        // modifiedDate is declared in BaseSearchDTO with sortField = "updatedAt"
        assertThat(SearchableFieldUtils.getSortFieldFromDto(ChildSearchDTO.class, "modifiedDate"))
                .isEqualTo("updatedAt");
    }

    @Test
    @DisplayName("6-1: sortField falls back to inherited entityField when sortField is absent")
    void inheritedEntityFieldUsedForSortWhenNoSortField() {
        assertThat(SearchableFieldUtils.getSortFieldFromDto(ChildSearchDTO.class, "createdDate"))
                .isEqualTo("createdAt");
    }

    @Test
    @DisplayName("6-1: field declared directly on the subclass still resolves")
    void ownFieldResolves() {
        assertThat(SearchableFieldUtils.getEntityFieldFromDto(ChildSearchDTO.class, "extra"))
                .isEqualTo("extra");
    }

    @Test
    @DisplayName("6-1: unknown field falls back to the given field name")
    void unknownFieldFallsBack() {
        assertThat(SearchableFieldUtils.getEntityFieldFromDto(ChildSearchDTO.class, "doesNotExist"))
                .isEqualTo("doesNotExist");
    }

    @Test
    @DisplayName("6-1: getSearchableFields collects inherited and own annotated fields")
    void getSearchableFieldsCollectsHierarchy() {
        List<String> names = SearchableFieldUtils.getSearchableFields(ChildSearchDTO.class).stream()
                .map(Field::getName)
                .collect(Collectors.toList());

        assertThat(names).containsExactlyInAnyOrder("extra", "createdDate", "modifiedDate");
    }

    @Test
    @DisplayName("6-1: getSearchableFields on the base returns only its own fields")
    void getSearchableFieldsBaseOnly() {
        List<String> names = SearchableFieldUtils.getSearchableFields(BaseSearchDTO.class).stream()
                .map(Field::getName)
                .collect(Collectors.toList());

        assertThat(names).containsExactlyInAnyOrder("createdDate", "modifiedDate");
    }
}
