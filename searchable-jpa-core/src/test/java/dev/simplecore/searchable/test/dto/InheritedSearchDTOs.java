package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.SearchableField;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;

/**
 * Fixtures for verifying that inherited {@code @SearchableField} declarations are honored
 * for entity-field/sort-field mapping and documentation generation.
 */
public class InheritedSearchDTOs {

    /**
     * Base DTO declaring searchable fields with custom entityField/sortField mappings.
     */
    public static class BaseSearchDTO {

        @SearchableField(entityField = "createdAt", operators = {EQUALS}, sortable = true)
        private String createdDate;

        @SearchableField(sortField = "updatedAt", operators = {EQUALS}, sortable = true)
        private String modifiedDate;

        public String getCreatedDate() {
            return createdDate;
        }

        public void setCreatedDate(String createdDate) {
            this.createdDate = createdDate;
        }

        public String getModifiedDate() {
            return modifiedDate;
        }

        public void setModifiedDate(String modifiedDate) {
            this.modifiedDate = modifiedDate;
        }
    }

    /**
     * Child DTO that inherits the base searchable fields and adds its own.
     */
    public static class ChildSearchDTO extends BaseSearchDTO {

        @SearchableField(operators = {EQUALS, CONTAINS})
        private String extra;

        public String getExtra() {
            return extra;
        }

        public void setExtra(String extra) {
            this.extra = extra;
        }
    }
}
