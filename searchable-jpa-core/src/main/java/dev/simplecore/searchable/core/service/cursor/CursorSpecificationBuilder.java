package dev.simplecore.searchable.core.service.cursor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds JPA Specification for cursor-based pagination conditions.
 * Creates WHERE clauses based on cursor values and sort criteria.
 *
 * @param <T> The entity type
 */
@Slf4j
public class CursorSpecificationBuilder<T> {

    /**
     * Builds cursor specification from cursor values and sort orders.
     * 
     * @param cursorValues map of field names to cursor values
     * @param sortOrders list of sort orders
     * @param forward true for forward pagination, false for backward
     * @return specification for cursor conditions
     */
    public Specification<T> buildCursorSpecification(@NonNull Map<String, Object> cursorValues,
                                                   @NonNull List<Sort.Order> sortOrders,
                                                   boolean forward) {
        if (cursorValues.isEmpty() || sortOrders.isEmpty()) {
            return null;
        }

        return (root, query, cb) -> {
            try {
                return createCursorCondition(root, cb, cursorValues, sortOrders, forward);
            } catch (Exception e) {
                log.warn("Failed to build cursor specification: {}", e.getMessage());
                return cb.conjunction(); // Return empty condition on error
            }
        };
    }

    /**
     * Creates cursor condition predicate.
     * For multiple sort fields, creates OR conditions for each level.
     * 
     * Example: sort by title ASC, viewCount DESC, id ASC
     * Cursor: {title: "Hello", viewCount: 100, id: 50}
     * 
     * Generates:
     * WHERE (title > 'Hello') 
     *    OR (title = 'Hello' AND viewCount < 100)
     *    OR (title = 'Hello' AND viewCount = 100 AND id > 50)
     */
    private Predicate createCursorCondition(Root<T> root,
                                          CriteriaBuilder cb,
                                          Map<String, Object> cursorValues,
                                          List<Sort.Order> sortOrders,
                                          boolean forward) {
        
        List<Predicate> orPredicates = new ArrayList<>();
        
        // Create OR conditions for each sort level
        for (int i = 0; i < sortOrders.size(); i++) {
            List<Predicate> andPredicates = new ArrayList<>();
            
            // Add equality conditions for all previous fields
            for (int j = 0; j < i; j++) {
                Sort.Order order = sortOrders.get(j);
                String fieldName = order.getProperty();
                Object cursorValue = cursorValues.get(fieldName);
                
                if (cursorValue != null) {
                    Path<Object> fieldPath = getFieldPath(root, fieldName);
                    andPredicates.add(cb.equal(fieldPath, cursorValue));
                }
            }
            
            // Add comparison condition for current field
            Sort.Order currentOrder = sortOrders.get(i);
            String currentField = currentOrder.getProperty();
            Object currentValue = cursorValues.get(currentField);
            
            if (currentValue != null) {
                Predicate comparisonPredicate = createComparisonPredicate(
                    root, cb, currentField, currentValue, currentOrder.getDirection(), forward
                );
                
                if (comparisonPredicate != null) {
                    andPredicates.add(comparisonPredicate);
                    
                    // Combine all AND conditions for this level
                    if (!andPredicates.isEmpty()) {
                        orPredicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
                    }
                }
            }
        }
        
        // Combine all OR conditions
        if (orPredicates.isEmpty()) {
            return cb.conjunction();
        } else if (orPredicates.size() == 1) {
            return orPredicates.get(0);
        } else {
            return cb.or(orPredicates.toArray(new Predicate[0]));
        }
    }

    /**
     * Creates comparison predicate for a single field.
     * 
     * @param root the root entity
     * @param cb criteria builder
     * @param fieldName field name
     * @param cursorValue cursor value
     * @param direction sort direction
     * @param forward pagination direction
     * @return comparison predicate
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate createComparisonPredicate(Root<T> root,
                                              CriteriaBuilder cb,
                                              String fieldName,
                                              Object cursorValue,
                                              Sort.Direction direction,
                                              boolean forward) {
        try {
            Path fieldPath = getFieldPath(root, fieldName);
            
            // Ensure the field value is Comparable
            if (!(cursorValue instanceof Comparable)) {
                log.warn("Field '{}' value is not Comparable: {}", fieldName, cursorValue.getClass());
                return null;
            }
            
            Comparable comparableValue = (Comparable) cursorValue;
            
            // Determine comparison operator based on sort direction and pagination direction
            boolean useGreaterThan = (direction == Sort.Direction.ASC && forward) || 
                                   (direction == Sort.Direction.DESC && !forward);
            
            if (useGreaterThan) {
                return cb.greaterThan(fieldPath, comparableValue);
            } else {
                return cb.lessThan(fieldPath, comparableValue);
            }
            
        } catch (Exception e) {
            log.warn("Failed to create comparison predicate for field '{}': {}", fieldName, e.getMessage());
            return null;
        }
    }

    /**
     * Gets field path supporting nested field access.
     * 
     * @param root the root entity
     * @param fieldPath field path (supports dot notation)
     * @return field path
     */
    private Path<Object> getFieldPath(Root<T> root, String fieldPath) {
        String[] pathParts = fieldPath.split("\\.");
        Path<Object> path = root.get(pathParts[0]);
        
        for (int i = 1; i < pathParts.length; i++) {
            path = path.get(pathParts[i]);
        }
        
        return path;
    }

    /**
     * Builds cursor specification for single sort field (optimized version).
     * 
     * @param fieldName field name
     * @param cursorValue cursor value
     * @param direction sort direction
     * @param forward pagination direction
     * @return specification for single field cursor
     */
    public Specification<T> buildSimpleCursorSpecification(@NonNull String fieldName,
                                                         @NonNull Object cursorValue,
                                                         @NonNull Sort.Direction direction,
                                                         boolean forward) {
        return (root, query, cb) -> {
            try {
                return createComparisonPredicate(root, cb, fieldName, cursorValue, direction, forward);
            } catch (Exception e) {
                log.warn("Failed to build simple cursor specification for field '{}': {}", fieldName, e.getMessage());
                return cb.conjunction();
            }
        };
    }
} 