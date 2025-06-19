package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.service.cursor.CursorPageConverter;
import dev.simplecore.searchable.core.service.join.JoinManager;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.SingularAttribute;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Builds JPA Specification from SearchCondition.
 * Thread-safe and immutable implementation.
 *
 * @param <T> The entity type
 */
@Slf4j
public class SearchableSpecificationBuilder<T> {
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SearchCondition<?> condition;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final JpaSpecificationExecutor<T> specificationExecutor;

    public SearchableSpecificationBuilder(@NonNull SearchCondition<?> condition,
                                          @NonNull EntityManager entityManager,
                                          @NonNull Class<T> entityClass,
                                          @NonNull JpaSpecificationExecutor<T> specificationExecutor) {
        this.condition = condition;
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.specificationExecutor = specificationExecutor;
    }

    public static <T> SearchableSpecificationBuilder<T> of(
            @NonNull SearchCondition<?> condition,
            @NonNull EntityManager entityManager,
            @NonNull Class<T> entityClass,
            @NonNull JpaSpecificationExecutor<T> specificationExecutor) {
        return new SearchableSpecificationBuilder<>(condition, entityManager, entityClass, specificationExecutor);
    }

    /**
     * Creates Sort object from SearchCondition orders.
     * Automatically adds primary key field for cursor-based pagination uniqueness.
     * Returns Sort.unsorted() if no orders defined.
     */
    private Sort createSort() {
        SearchCondition.Sort sortCondition = condition.getSort();
        if (sortCondition == null) {
            return Sort.unsorted();
        }

        List<SearchCondition.Order> orders = sortCondition.getOrders();
        if (orders.isEmpty()) {
            return Sort.unsorted();
        }

        // Convert SearchCondition orders to Spring Data Sort orders
        List<Sort.Order> sortOrders = orders.stream()
                .map(this::createOrder)
                .collect(Collectors.toList());

        // Automatically add primary key field for cursor-based pagination uniqueness
        sortOrders = ensureUniqueSorting(sortOrders);

        return Sort.by(sortOrders);
    }

    /**
     * Ensures unique sorting by adding primary key field if not already present.
     * This is crucial for cursor-based pagination to work correctly.
     * 
     * @param sortOrders the existing sort orders
     * @return sort orders with primary key field added if necessary
     */
    private List<Sort.Order> ensureUniqueSorting(List<Sort.Order> sortOrders) {
        try {
            String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
            
            if (primaryKeyField != null) {
                // Check if primary key field is already in sort orders
                boolean hasPrimaryKey = sortOrders.stream()
                        .anyMatch(order -> primaryKeyField.equals(order.getProperty()));
                
                if (!hasPrimaryKey) {
                    // Add primary key field as the last sort criterion in ascending order
                    sortOrders = new java.util.ArrayList<>(sortOrders);
                    sortOrders.add(Sort.Order.asc(primaryKeyField));
                    
                    log.debug("Automatically added primary key field '{}' to sort criteria for cursor-based pagination uniqueness", 
                             primaryKeyField);
                }
            } else {
                log.warn("Could not determine primary key field for entity {}. Cursor-based pagination may not work correctly with duplicate sort values.", 
                        entityClass.getSimpleName());
            }
            
            return sortOrders;
            
        } catch (Exception e) {
            log.warn("Failed to ensure unique sorting for entity {}: {}. Using original sort orders.", 
                    entityClass.getSimpleName(), e.getMessage());
            return sortOrders;
        }
    }

    /**
     * Creates Sort.Order from SearchCondition.Order.
     * Direction defaults to ASC if not specified.
     */
    private Sort.Order createOrder(SearchCondition.Order order) {
        String field = order.getEntityField();
        if (field == null || field.isEmpty()) {
            field = order.getField();
        }
        Sort.Direction direction = order.isAscending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return new Sort.Order(direction, field);
    }

    /**
     * Creates and combines predicates from condition nodes.
     * Returns null if no predicates created.
     */
    private Predicate createPredicates(Root<T> root,
                                       javax.persistence.criteria.CriteriaQuery<?> query,
                                       CriteriaBuilder cb,
                                       SpecificationBuilder<T> specBuilder) {
        List<Node> nodes = condition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        // Always apply distinct for safety
        query.distinct(true);

        // Process the first node
        Node firstNode = nodes.get(0);
        Predicate result = specBuilder.build(root, query, cb, firstNode);
        if (result == null) {
            return null;
        }

        // Process remaining nodes in order, combining them with their respective operators
        for (int i = 1; i < nodes.size(); i++) {
            Node currentNode = nodes.get(i);
            Predicate currentPredicate = specBuilder.build(root, query, cb, currentNode);
            if (currentPredicate == null) continue;

            if (currentNode.getOperator() == LogicalOperator.OR) {
                result = cb.or(result, currentPredicate);
            } else {
                result = cb.and(result, currentPredicate);
            }
        }

        return result;
    }

    /**
     * Builds SpecificationWithPageable from SearchCondition.
     * Thread-safe method that creates new instance each time.
     * 
     * @deprecated Use buildAndExecuteWithCursor() instead for cursor-based pagination
     * @return SpecificationWithPageable containing specification and page request
     */
    @Deprecated
    public SpecificationWithPageable<T> build() {
        return new SpecificationWithPageable<>(
                buildSpecification(),
                buildPageRequest()
        );
    }

    /**
     * Executes cursor-based pagination query directly.
     * This method bypasses the traditional SpecificationWithPageable approach
     * and executes cursor-based pagination internally while maintaining API compatibility.
     *
     * @return Page object with cursor-based pagination results
     */
    public Page<T> buildAndExecuteWithCursor() {
        PageRequest originalPageRequest = buildPageRequest();
        Specification<T> baseSpecification = buildSpecification();
        
        CursorPageConverter<T> converter = new CursorPageConverter<>(specificationExecutor, entityClass);
        return converter.convertToCursorBasedPage(originalPageRequest, baseSpecification);
    }

    /**
     * Executes cursor-based pagination with two-phase query for ToMany relationships.
     * This method avoids HHH000104 warning by separating ID query from data fetching.
     *
     * @return Page object with cursor-based pagination results
     */
    public Page<T> buildAndExecuteWithCursorTwoPhase() {
        PageRequest originalPageRequest = buildPageRequest();
        
        // Check if we have ToMany relationships that would cause memory pagination
        Set<String> joinPaths = extractJoinPaths(condition.getNodes());
        boolean hasMultipleToMany = countToManyPaths(joinPaths) > 1;
        
        if (hasMultipleToMany) {
            return executeTwoPhaseQuery(originalPageRequest);
        } else {
            // Use normal cursor-based pagination for simple cases
            Specification<T> baseSpecification = buildSpecification();
            CursorPageConverter<T> converter = new CursorPageConverter<>(specificationExecutor, entityClass);
            return converter.convertToCursorBasedPage(originalPageRequest, baseSpecification);
        }
    }

    /**
     * Executes two-phase query to avoid memory pagination issues.
     */
    private Page<T> executeTwoPhaseQuery(PageRequest originalPageRequest) {
        // Phase 1: Get IDs only with proper database-level pagination
        List<Object> ids = getIdsWithCursorPagination(originalPageRequest);
        
        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), originalPageRequest, 0);
        }
        
        // Phase 2: Fetch full entities by IDs with all ToMany relationships
        List<T> entities = fetchEntitiesByIds(ids);
        
        // Calculate total elements for page metadata
        long totalElements = calculateTotalElements();
        
        return new PageImpl<>(entities, originalPageRequest, totalElements);
    }

    /**
     * Phase 1: Get entity IDs with cursor-based pagination (no ToMany fetch joins).
     */
    private List<Object> getIdsWithCursorPagination(PageRequest pageRequest) {
        // Build specification without fetch joins (only regular joins for filtering)
        Specification<T> idQuerySpec = (root, query, cb) -> {
            // Set result type to ID only
            String primaryKeyField = getPrimaryKeyFieldName();
            query.select(root.get(primaryKeyField));
            
            // Apply regular joins for filtering (no fetch joins)
            Set<String> joinPaths = extractJoinPaths(condition.getNodes());
            if (!joinPaths.isEmpty()) {
                applyRegularJoinsOnly(root, joinPaths);
                query.distinct(true);
            }

            JoinManager<T> joinManager = new JoinManager<>(entityManager, root);
            PredicateBuilder<T> predicateBuilder = new PredicateBuilder<>(cb, joinManager);
            SpecificationBuilder<T> specBuilder = new SpecificationBuilder<>(predicateBuilder);

            return createPredicates(root, query, cb, specBuilder);
        };
        
        // Use cursor-based pagination for ID query
        CursorPageConverter<T> converter = new CursorPageConverter<>(specificationExecutor, entityClass);
        Page<T> idPage = converter.convertToCursorBasedPage(pageRequest, idQuerySpec);
        
        // Extract IDs from the result (this will be a list of ID objects)
        return idPage.getContent().stream()
                .map(entity -> {
                    try {
                        String primaryKeyField = getPrimaryKeyFieldName();
                        Field field = entityClass.getDeclaredField(primaryKeyField);
                        field.setAccessible(true);
                        return field.get(entity);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to extract ID from entity", e);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Executes cursor-based pagination without total count calculation.
     * This is an optimized version that avoids expensive count queries.
     *
     * @return Page object with cursor-based pagination results (without total count)
     */
    public Page<T> buildAndExecuteWithCursorOptimized() {
        PageRequest originalPageRequest = buildPageRequest();
        Specification<T> baseSpecification = buildSpecification();
        
        CursorPageConverter<T> converter = new CursorPageConverter<>(specificationExecutor, entityClass);
        return converter.convertToCursorBasedPageWithoutCount(originalPageRequest, baseSpecification);
    }

    /**
     * Builds only the specification part for operations that don't need pagination.
     * This method is used for count, exists, delete, and update operations.
     *
     * @return SpecificationWithPageable containing specification without cursor pagination
     */
    public SpecificationWithPageable<T> buildSpecificationOnly() {
        return new SpecificationWithPageable<>(
                buildSpecification(),
                buildPageRequest()
        );
    }

    /**
     * Phase 2: Fetch full entities by IDs with all relationships.
     */
    private List<T> fetchEntitiesByIds(List<Object> ids) {
        String primaryKeyField = getPrimaryKeyFieldName();
        
        // Build specification to fetch by IDs with all ToMany relationships
        Specification<T> fetchSpec = (root, query, cb) -> {
            // Apply all fetch joins (ToOne + all ToMany)
            Set<String> joinPaths = extractJoinPaths(condition.getNodes());
            if (!joinPaths.isEmpty()) {
                applyAllFetchJoins(root, joinPaths);
                query.distinct(true);
            }
            
            // Filter by IDs
            return root.get(primaryKeyField).in(ids);
        };
        
        // Fetch entities and maintain original order
        List<T> entities = specificationExecutor.findAll(fetchSpec);
        
        // Sort entities according to the original ID order
        Map<Object, T> entityMap = entities.stream()
                .collect(Collectors.toMap(
                    entity -> {
                        try {
                            Field field = entityClass.getDeclaredField(primaryKeyField);
                            field.setAccessible(true);
                            return field.get(entity);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to extract ID", e);
                        }
                    },
                    entity -> entity
                ));
        
        return ids.stream()
                .map(entityMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Apply only regular joins (no fetch joins) for filtering.
     */
    private void applyRegularJoinsOnly(Root<T> root, Set<String> paths) {
        Set<Join<T, ?>> joins = (Set<Join<T, ?>>) root.getJoins();
        joins.clear();

        for (String path : paths) {
            root.join(path, JoinType.LEFT);
        }
    }

    /**
     * Apply fetch joins for all relationships.
     */
    private void applyAllFetchJoins(Root<T> root, Set<String> paths) {
        Set<Join<T, ?>> joins = (Set<Join<T, ?>>) root.getJoins();
        joins.clear();

        for (String path : paths) {
            root.fetch(path, JoinType.LEFT);
        }
    }

    /**
     * Count the number of ToMany relationships.
     */
    private int countToManyPaths(Set<String> paths) {
        return (int) paths.stream()
                .filter(path -> isToManyPath(null, path))
                .count();
    }

    /**
     * Get primary key field name.
     */
    private String getPrimaryKeyFieldName() {
        try {
            EntityType<T> entityType = entityManager.getMetamodel().entity(entityClass);
            SingularAttribute<? super T, ?> idAttribute = entityType.getId(entityType.getIdType().getJavaType());
            return idAttribute.getName();
        } catch (Exception e) {
            // Fallback to reflection
            for (Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(javax.persistence.Id.class)) {
                    return field.getName();
                }
            }
            return "id"; // Default fallback
        }
    }

    /**
     * Calculate total elements for page metadata.
     */
    private long calculateTotalElements() {
        Specification<T> countSpec = (root, query, cb) -> {
            // Apply regular joins for filtering (no fetch joins for count)
            Set<String> joinPaths = extractJoinPaths(condition.getNodes());
            if (!joinPaths.isEmpty()) {
                applyRegularJoinsOnly(root, joinPaths);
                query.distinct(true);
            }

            JoinManager<T> joinManager = new JoinManager<>(entityManager, root);
            PredicateBuilder<T> predicateBuilder = new PredicateBuilder<>(cb, joinManager);
            SpecificationBuilder<T> specBuilder = new SpecificationBuilder<>(predicateBuilder);

            return createPredicates(root, query, cb, specBuilder);
        };
        
        return specificationExecutor.count(countSpec);
    }

    private Specification<T> buildSpecification() {
        return (root, query, cb) -> {
            boolean isCountQuery = query.getResultType().equals(Long.class);
            
            // Always extract join paths from conditions (needed for filtering)
            Set<String> conditionJoinPaths = extractJoinPaths(condition.getNodes());
            
            log.debug("Applying joins - condition paths: {}, query type: {}, isCountQuery: {}", 
                     conditionJoinPaths, query.getResultType(), isCountQuery);
            
            // For non-count queries, add common ToOne fields to prevent N+1 problems
            Set<String> allJoinPaths = new HashSet<>(conditionJoinPaths);
            if (!isCountQuery) {
                Set<String> commonToOneFields = detectCommonToOneFields();
                log.debug("Adding common ToOne fields for non-count query: {}", commonToOneFields);
                allJoinPaths.addAll(commonToOneFields);
            }
            
            // Apply joins (with different strategy for count vs select queries)
            applyJoins(root, allJoinPaths, isCountQuery);
            
            // Apply distinct only if we have actual joins
            if (!root.getJoins().isEmpty()) {
                query.distinct(true);
            }

            JoinManager<T> joinManager = new JoinManager<>(entityManager, root);
            PredicateBuilder<T> predicateBuilder = new PredicateBuilder<>(cb, joinManager);
            SpecificationBuilder<T> specBuilder = new SpecificationBuilder<>(predicateBuilder);

            return createPredicates(root, query, cb, specBuilder);
        };
    }

    private Set<String> extractJoinPaths(List<Node> nodes) {
        Set<String> joinPaths = new HashSet<>();
        if (nodes == null) return joinPaths;
        
        for (Node node : nodes) {
            if (node instanceof SearchCondition.Condition) {
                SearchCondition.Condition condition = (SearchCondition.Condition) node;
                String entityField = condition.getEntityField();
                if (entityField != null && !entityField.isEmpty()) {
                    String[] pathParts = entityField.split("\\.");
                    StringBuilder path = new StringBuilder();
                    
                    // Add all intermediate paths for nested joins
                    for (int i = 0; i < pathParts.length - 1; i++) {
                        if (path.length() > 0) {
                            path.append(".");
                        }
                        path.append(pathParts[i]);
                        joinPaths.add(path.toString());
                    }
                }
            } else if (node instanceof SearchCondition.Group) {
                joinPaths.addAll(extractJoinPaths(node.getNodes()));
            }
        }
        
        return joinPaths;
    }

    /**
     * Apply improved join strategy for ToMany relationships.
     * Uses different strategies based on the number of ToMany relationships.
     */
    private void applyJoins(Root<T> root, Set<String> paths, boolean isCountQuery) {
        Set<Join<T, ?>> joins = (Set<Join<T, ?>>) root.getJoins();
        joins.clear();

        // Separate ToOne and ToMany paths
        Set<String> toOnePaths = new HashSet<>();
        Set<String> toManyPaths = new HashSet<>();
        
        for (String path : paths) {
            if (isToManyPath(root, path)) {
                toManyPaths.add(path);
            } else {
                toOnePaths.add(path);
            }
        }
        
        log.debug("Detected ToOne paths: {}", toOnePaths);
        log.debug("Detected ToMany paths: {}", toManyPaths);
        
        // Apply ToOne joins
        for (String path : toOnePaths) {
            try {
                if (isCountQuery) {
                    // For count queries, use regular join to avoid fetch join errors
                    log.debug("ApplyJoins: Using regular join for ToOne path (count query): {}", path);
                    root.join(path, JoinType.LEFT);
                } else {
                    // For select queries, use fetch join to prevent N+1
                    log.debug("ApplyJoins: Using fetch join for ToOne path (select query): {}", path);
                    root.fetch(path, JoinType.LEFT);
                }
                log.debug("Successfully applied join for ToOne path: {}", path);
            } catch (Exception e) {
                log.warn("Join failed for ToOne path '{}', using regular join as fallback: {}", path, e.getMessage());
                root.join(path, JoinType.LEFT);
            }
        }
        
        // Apply ToMany joins (always regular join to prevent memory pagination)
        for (String path : toManyPaths) {
            try {
                log.debug("ApplyJoins: Using regular join for ToMany path: {}", path);
                root.join(path, JoinType.LEFT);
                log.debug("Successfully applied regular join for ToMany path: {}", path);
            } catch (Exception e) {
                log.warn("Join failed for ToMany path '{}': {}", path, e.getMessage());
            }
        }
        
        Set<String> finalToOneFields = detectCommonToOneFields();
        finalToOneFields.removeAll(toOnePaths); // Remove already processed paths
        log.debug("Final ToOne fields to add: {}", finalToOneFields);
        
        // Apply additional ToOne fields for N+1 prevention (only for select queries)
        if (!isCountQuery) {
            for (String field : finalToOneFields) {
                try {
                    log.debug("ApplyJoins: Attempting fetch join for common ToOne field: {}", field);
                    root.fetch(field, JoinType.LEFT);
                    log.debug("Successfully applied fetch join for common ToOne field: {}", field);
                } catch (Exception e) {
                    log.warn("Fetch join failed for common ToOne field '{}', using regular join as fallback: {}", field, e.getMessage());
                    root.join(field, JoinType.LEFT);
                }
            }
        }
    }

    /**
     * Execute query with two-phase optimization.
     * Phase 1: Get IDs only (regular joins to avoid memory paging)
     * Phase 2: Load complete entities with smart fetch joins
     */
    public Page<T> buildAndExecuteWithTwoPhaseOptimization() {
        log.debug("Starting two-phase optimization query");
        
        PageRequest pageRequest = buildPageRequest();
        Set<String> allJoinPaths = extractJoinPaths(condition.getNodes());
        Set<String> toManyPaths = allJoinPaths.stream()
                .filter(path -> isToManyPath(createDummyRoot(), path))
                .collect(Collectors.toSet());

        log.debug("All join paths: {}", allJoinPaths);
        log.debug("ToMany paths detected: {}", toManyPaths);
        
        // Check if two-phase optimization is needed
        if (shouldUseTwoPhaseQuery(toManyPaths)) {
            log.debug("Two-phase query optimization is NEEDED");
            return executeTwoPhaseQuery(pageRequest, allJoinPaths);
        } else {
            log.debug("Two-phase query optimization is NOT needed, using regular query");
            return buildAndExecuteWithCursor();
        }
    }

    /**
     * Determines whether to use two-phase query based on complexity analysis.
     */
    private boolean shouldUseTwoPhaseQuery(Set<String> toManyPaths) {
        // Use two-phase query if:
        // 1. Multiple ToMany relationships (high cartesian product risk)
        // 2. Or single ToMany but with complex conditions (potential large result set)
        
        if (toManyPaths.size() >= 2) {
            return true; // Multiple ToMany always use two-phase
        }
        
        if (toManyPaths.size() == 1) {
            // Check if conditions are complex enough to warrant two-phase
            return hasComplexConditions();
        }
        
        return false; // No ToMany or simple conditions - use single phase
    }

    /**
     * Checks if the search conditions are complex enough to potentially 
     * generate large intermediate result sets.
     */
    private boolean hasComplexConditions() {
        if (condition.getNodes() == null) return false;
        
        // Count conditions involving ToMany relationships
        long toManyConditionCount = condition.getNodes().stream()
                .filter(node -> node instanceof SearchCondition.Condition)
                .map(node -> (SearchCondition.Condition) node)
                .filter(cond -> {
                    String entityField = cond.getEntityField();
                    return entityField != null && entityField.contains(".") && 
                           isToManyPath(createDummyRoot(), getRelationshipPath(entityField));
                })
                .count();
        
        return toManyConditionCount > 0; // Any ToMany condition triggers two-phase
    }

    /**
     * Executes two-phase query for optimal performance with large datasets.
     * Phase 1: Query IDs only with all conditions applied
     * Phase 2: Fetch full entities by IDs with all relationships
     */
    private Page<T> executeTwoPhaseQuery(PageRequest pageRequest, Set<String> allJoinPaths) {
        log.debug("Starting two-phase query execution");
        log.debug("Page request: page={}, size={}, sort={}", pageRequest.getPageNumber(), pageRequest.getPageSize(), pageRequest.getSort());
        log.debug("All join paths: {}", allJoinPaths);
        
        // Phase 1: Get IDs only (fast pagination with all conditions)
        List<Object> entityIds = executePhaseOneQuery(pageRequest);
        log.debug("Phase 1 completed. Entity IDs retrieved: {} IDs", entityIds.size());
        log.debug("Entity IDs: {}", entityIds);
        
        if (entityIds.isEmpty()) {
            log.debug("No entity IDs found, returning empty page");
            return new PageImpl<>(Collections.emptyList(), pageRequest, 0);
        }
        
        // Phase 2: Fetch full entities with all relationships
        log.debug("Starting Phase 2: Fetch full entities");
        List<T> fullEntities = executePhaseTwoQuery(entityIds, allJoinPaths, pageRequest.getSort());
        log.debug("Phase 2 completed. Full entities retrieved: {} entities", fullEntities.size());
        
        // Get total count (only if needed)
        log.debug("Getting total count for pagination metadata");
        long totalCount = getTotalCountForTwoPhase();
        log.debug("Total count: {}", totalCount);
        
        log.debug("Two-phase query completed successfully");
        return new PageImpl<>(fullEntities, pageRequest, totalCount);
    }

    /**
     * Phase 1: Query only entity IDs with all search conditions applied.
     * This avoids cartesian products while maintaining all filtering logic.
     */
    private List<Object> executePhaseOneQuery(PageRequest pageRequest) {
        log.debug("Phase 1: Starting ID-only query");
        
        // Only extract join paths from conditions (no additional ToOne fields)
        Set<String> conditionJoinPaths = extractJoinPaths(condition.getNodes());
        log.debug("Phase 1: Extracted join paths from conditions: {}", conditionJoinPaths);
        
        // Get primary key field and sorting fields
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
        
        // Check if we have additional sorting fields beyond the primary key
        Sort sort = pageRequest.getSort();
        boolean hasAdditionalSortFields = false;
        if (sort.isSorted()) {
            for (Sort.Order order : sort) {
                String property = order.getProperty();
                if (!property.equals(primaryKeyField)) { // Don't duplicate primary key
                    hasAdditionalSortFields = true;
                    break;
                }
            }
        }
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        
        // Decide query type based on whether we have additional fields
        List<Object> ids;
        if (hasAdditionalSortFields) {
            // Multiple fields: use Object[] query
            CriteriaQuery<Object[]> arrayQuery = cb.createQuery(Object[].class);
            Root<T> arrayRoot = arrayQuery.from(entityClass);
            
            // Reapply joins and conditions to new root
            applyRegularJoinsOnly(arrayRoot, conditionJoinPaths);
            log.debug("Phase 1: Applied regular joins, total joins: {}", arrayRoot.getJoins().size());
            
            // Build selections for array root
            List<Selection<?>> arraySelections = new ArrayList<>();
            arraySelections.add(arrayRoot.get(primaryKeyField));
            for (Sort.Order order : sort) {
                String property = order.getProperty();
                if (!property.equals(primaryKeyField)) {
                    arraySelections.add(arrayRoot.get(property));
                }
            }
            arrayQuery.multiselect(arraySelections);
            
            // Apply distinct and conditions
            if (!arrayRoot.getJoins().isEmpty()) {
                arrayQuery.distinct(true);
            }
            
            JoinManager<T> joinManager = new JoinManager<>(entityManager, arrayRoot);
            PredicateBuilder<T> predicateBuilder = new PredicateBuilder<>(cb, joinManager);
            SpecificationBuilder<T> specBuilder = new SpecificationBuilder<>(predicateBuilder);
            
            Predicate predicate = createPredicates(arrayRoot, arrayQuery, cb, specBuilder);
            if (predicate != null) {
                arrayQuery.where(predicate);
            }
            
            // Apply sorting
            if (sort.isSorted()) {
                List<Order> orders = new ArrayList<>();
                for (Sort.Order order : sort) {
                    Path<?> path = arrayRoot.get(order.getProperty());
                    if (order.isAscending()) {
                        orders.add(cb.asc(path));
                    } else {
                        orders.add(cb.desc(path));
                    }
                }
                arrayQuery.orderBy(orders);
            }
            
            // Execute array query
            TypedQuery<Object[]> arrayTypedQuery = entityManager.createQuery(arrayQuery);
            arrayTypedQuery.setFirstResult((int) pageRequest.getOffset());
            arrayTypedQuery.setMaxResults(pageRequest.getPageSize());
            
            List<Object[]> arrayResults = arrayTypedQuery.getResultList();
            ids = arrayResults.stream()
                    .map(row -> row[0]) // ID is always the first column
                    .collect(Collectors.toList());
                    
        } else {
            // Single field: use Object query (just ID)
            CriteriaQuery<Object> singleQuery = cb.createQuery(Object.class);
            Root<T> singleRoot = singleQuery.from(entityClass);
            
            // Reapply joins and conditions to new root
            applyRegularJoinsOnly(singleRoot, conditionJoinPaths);
            log.debug("Phase 1: Applied regular joins, total joins: {}", singleRoot.getJoins().size());
            
            singleQuery.select(singleRoot.get(primaryKeyField));
            
            // Apply distinct and conditions
            if (!singleRoot.getJoins().isEmpty()) {
                singleQuery.distinct(true);
            }
            
            JoinManager<T> joinManager = new JoinManager<>(entityManager, singleRoot);
            PredicateBuilder<T> predicateBuilder = new PredicateBuilder<>(cb, joinManager);
            SpecificationBuilder<T> specBuilder = new SpecificationBuilder<>(predicateBuilder);
            
            Predicate predicate = createPredicates(singleRoot, singleQuery, cb, specBuilder);
            if (predicate != null) {
                singleQuery.where(predicate);
            }
            
            // Apply sorting (just by ID)
            if (sort.isSorted()) {
                List<Order> orders = new ArrayList<>();
                for (Sort.Order order : sort) {
                    Path<?> path = singleRoot.get(order.getProperty());
                    if (order.isAscending()) {
                        orders.add(cb.asc(path));
                    } else {
                        orders.add(cb.desc(path));
                    }
                }
                singleQuery.orderBy(orders);
            }
            
            // Execute single query
            TypedQuery<Object> singleTypedQuery = entityManager.createQuery(singleQuery);
            singleTypedQuery.setFirstResult((int) pageRequest.getOffset());
            singleTypedQuery.setMaxResults(pageRequest.getPageSize());
            
            ids = singleTypedQuery.getResultList();
        }
        
        log.debug("Phase 1: Retrieved {} IDs: {}", ids.size(), ids);
        return ids;
    }

    /**
     * Phase 2: Fetch full entities by IDs with all relationships loaded.
     * This ensures optimal loading of all required data.
     */
    private List<T> executePhaseTwoQuery(List<Object> entityIds, Set<String> allJoinPaths, Sort sort) {
        Specification<T> fullDataSpec = (root, query, cb) -> {
            // Apply SMART fetch joins to avoid MultipleBagFetchException
            applySmartFetchJoins(root, allJoinPaths);
            query.distinct(true);
            
            // Filter by collected IDs
            String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
            return root.get(primaryKeyField).in(entityIds);
        };
        
        // Execute and maintain original sort order
        List<T> entities = specificationExecutor.findAll(fullDataSpec, sort);
        log.debug("Phase 2: Retrieved {} raw entities from database", entities.size());
        
        // Debug: Log first few entities
        for (int i = 0; i < Math.min(3, entities.size()); i++) {
            T entity = entities.get(i);
            log.debug("Phase 2: Entity[{}] = {}", i, entity);
            try {
                String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
                Field idField = entityClass.getDeclaredField(primaryKeyField);
                idField.setAccessible(true);
                Object id = idField.get(entity);
                log.debug("Phase 2: Entity[{}] ID = {}", i, id);
            } catch (Exception e) {
                log.error("Phase 2: Failed to extract ID from entity[{}]: {}", i, e.getMessage());
            }
        }
        
        // Ensure entities are returned in the same order as the IDs
        return reorderEntitiesByIds(entities, entityIds);
    }

    /**
     * Apply smart fetch joins to avoid MultipleBagFetchException.
     * Strategy: Fetch only ToOne relationships and one primary ToMany relationship.
     * Other ToMany relationships will be loaded lazily or via batch fetching.
     */
    private void applySmartFetchJoins(Root<T> root, Set<String> paths) {
        log.debug("ApplySmartFetchJoins: Starting with paths: {}", paths);
        
        Set<Join<T, ?>> joins = (Set<Join<T, ?>>) root.getJoins();
        joins.clear();
        
        // Separate ToOne and ToMany paths
        Set<String> toOnePaths = new HashSet<>();
        Set<String> toManyPaths = new HashSet<>();
        
        for (String path : paths) {
            if (isToManyPath(root, path)) {
                toManyPaths.add(path);
            } else {
                toOnePaths.add(path);
            }
        }
        
        log.debug("ApplySmartFetchJoins: ToOne paths from conditions: {}", toOnePaths);
        log.debug("ApplySmartFetchJoins: ToMany paths from conditions: {}", toManyPaths);
        
        // Add commonly accessed ToOne relationships even if not in search conditions
        // This prevents N+1 problems for frequently accessed fields like 'position'
        Set<String> commonToOneFields = detectCommonToOneFields();
        log.debug("ApplySmartFetchJoins: Common ToOne fields detected: {}", commonToOneFields);
        toOnePaths.addAll(commonToOneFields);
        log.debug("ApplySmartFetchJoins: Final ToOne paths to fetch: {}", toOnePaths);
        
        // Strategy 1: Always fetch join ToOne relationships (safe and efficient)
        for (String path : toOnePaths) {
            try {
                log.debug("ApplySmartFetchJoins: Attempting fetch join for ToOne path: {}", path);
                root.fetch(path, JoinType.LEFT);
                log.debug("ApplySmartFetchJoins: Successfully applied fetch join for: {}", path);
            } catch (Exception e) {
                log.warn(" ApplySmartFetchJoins: Fetch join failed for path '{}', using regular join as fallback: {}", path, e.getMessage());
                // If fetch join fails, use regular join as fallback
                root.join(path, JoinType.LEFT);
            }
        }
        
        // Strategy 2: For ToMany relationships, use selective fetching
        if (toManyPaths.size() == 1) {
            // Single ToMany: Safe to fetch join
            String toManyPath = toManyPaths.iterator().next();
            log.debug("ApplySmartFetchJoins: Single ToMany path, applying fetch join: {}", toManyPath);
            root.fetch(toManyPath, JoinType.LEFT);
        } else if (toManyPaths.size() > 1) {
            // Multiple ToMany: Select the most important one for fetch join
            String primaryToMany = selectPrimaryToManyForFetch(toManyPaths);
            log.debug("ApplySmartFetchJoins: Multiple ToMany paths, selected primary: {}", primaryToMany);
            if (primaryToMany != null) {
                root.fetch(primaryToMany, JoinType.LEFT);
            }
            
            // Other ToMany relationships: Use regular joins to enable lazy loading
            for (String path : toManyPaths) {
                if (!path.equals(primaryToMany)) {
                    log.debug("ApplySmartFetchJoins: Applying regular join for secondary ToMany: {}", path);
                    root.join(path, JoinType.LEFT);
                }
            }
        }
        
        log.debug("ApplySmartFetchJoins: Completed join application");
    }
    
    /**
     * Select the most important ToMany relationship for fetch joining.
     * Priority: relationships used in search conditions > first alphabetically
     */
    private String selectPrimaryToManyForFetch(Set<String> toManyPaths) {
        if (toManyPaths.isEmpty()) {
            return null;
        }
        
        // Get all field paths used in conditions
        Set<String> conditionPaths = extractConditionPaths(condition.getNodes());
        
        // Find ToMany paths that are used in conditions (these are more important)
        for (String path : toManyPaths) {
            if (conditionPaths.contains(path)) {
                return path;
            }
        }
        
        // If no path is used in conditions, return the first one alphabetically
        return toManyPaths.stream()
                .sorted()
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Extract all field paths used in search conditions.
     */
    private Set<String> extractConditionPaths(List<Node> nodes) {
        Set<String> conditionPaths = new HashSet<>();
        if (nodes == null) return conditionPaths;
        
        for (Node node : nodes) {
            if (node instanceof SearchCondition.Condition) {
                SearchCondition.Condition condition = (SearchCondition.Condition) node;
                String entityField = condition.getEntityField();
                if (entityField != null && !entityField.isEmpty()) {
                    // Extract the relationship path (everything except the last field)
                    String[] pathParts = entityField.split("\\.");
                    if (pathParts.length > 1) {
                        StringBuilder path = new StringBuilder();
                        for (int i = 0; i < pathParts.length - 1; i++) {
                            if (path.length() > 0) {
                                path.append(".");
                            }
                            path.append(pathParts[i]);
                        }
                        conditionPaths.add(path.toString());
                    }
                }
            } else if (node instanceof SearchCondition.Group) {
                conditionPaths.addAll(extractConditionPaths(node.getNodes()));
            }
        }
        
        return conditionPaths;
    }

    /**
     * Reorder entities to match the original ID order from phase 1.
     */
    private List<T> reorderEntitiesByIds(List<T> entities, List<Object> orderedIds) {
        log.debug("Reordering entities: {} entities, {} ordered IDs", entities.size(), orderedIds.size());
        
        if (entities.size() != orderedIds.size()) {
            log.warn("Entity count ({}) does not match ordered ID count ({}). This may be due to DISTINCT removing duplicates.", entities.size(), orderedIds.size());
            // Don't return early - proceed with reordering using available entities
        }
        
        // Create a map for O(1) lookup
        Map<Object, T> entityMap = new HashMap<>();
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
        log.debug("Detected primary key field: {}", primaryKeyField);
        
        for (T entity : entities) {
            try {
                Object id = null;
                
                // Try using getId() method first (handles Hibernate proxies better)
                try {
                    Method getIdMethod = entity.getClass().getMethod("getId");
                    id = getIdMethod.invoke(entity);
                    log.debug("Extracted ID via getId() method: {}", id);
                } catch (Exception getIdException) {
                    log.debug("getId() method not available, falling back to reflection: {}", getIdException.getMessage());
                    // Fallback to reflection
                    Field idField = entityClass.getDeclaredField(primaryKeyField);
                    idField.setAccessible(true);
                    id = idField.get(entity);
                    log.debug("Extracted ID via reflection: {}", id);
                }
                
                if (id != null) {
                    entityMap.put(id, entity);
                    log.debug("Added entity to map: ID={}, field={}", id, primaryKeyField);
                } else {
                    log.warn("Entity ID is null for entity: {}", entity);
                }
            } catch (Exception e) {
                log.error("Failed to extract ID from entity using field '{}': {}", primaryKeyField, entity, e);
                // If reordering fails, return original order
                return entities;
            }
        }
        
        // Reorder according to original ID sequence
        List<T> reorderedEntities = orderedIds.stream()
                .map(id -> {
                    T entity = entityMap.get(id);
                    if (entity == null) {
                        log.warn("No entity found for ID: {}", id);
                    }
                    return entity;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        log.debug("Reordering completed: {} entities after reordering", reorderedEntities.size());
        return reorderedEntities;
    }

    /**
     * Get total count for two-phase query (optimized count query).
     */
    private long getTotalCountForTwoPhase() {
        Specification<T> countSpec = (root, query, cb) -> {
            Set<String> joinPaths = extractJoinPaths(condition.getNodes());
            applyRegularJoinsOnly(root, joinPaths);
            
            // Apply distinct for accurate count with joins
            query.distinct(true);
            
            JoinManager<T> joinManager = new JoinManager<>(entityManager, root);
            PredicateBuilder<T> predicateBuilder = new PredicateBuilder<>(cb, joinManager);
            SpecificationBuilder<T> specBuilder = new SpecificationBuilder<>(predicateBuilder);
            
            return createPredicates(root, query, cb, specBuilder);
        };
        
        return specificationExecutor.count(countSpec);
    }

    /**
     * Create a dummy root for path analysis without executing queries.
     */
    private Root<T> createDummyRoot() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        return query.from(entityClass);
    }

    /**
     * Extract relationship path from entity field (e.g., "tags.name" -> "tags").
     */
    private String getRelationshipPath(String entityField) {
        if (entityField == null || !entityField.contains(".")) {
            return entityField;
        }
        
        String[] parts = entityField.split("\\.");
        if (parts.length > 1) {
            return parts[0]; // Return the relationship part
        }
        
        return entityField;
    }

    private boolean isToManyPath(Root<T> root, String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }

        try {
            String[] parts = path.split("\\.");
            From<?, ?> from = root;
            Class<?> currentType = root.getJavaType();

            for (String part : parts) {
                ManagedType<?> managedType = entityManager.getMetamodel().managedType(currentType);
                Attribute<?, ?> attribute = managedType.getAttribute(part);
                
                if (attribute.isCollection()) {
                    return true;
                }
                
                currentType = attribute.getJavaType();
            }
            
            return false;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid path: " + path, e);
        }
    }

    private PageRequest buildPageRequest() {
        Integer pageNum = condition.getPage();
        Integer sizeNum = condition.getSize();

        int page = pageNum != null ? Math.max(0, pageNum) : 0;
        int size = sizeNum != null ? (sizeNum > 0 ? sizeNum : DEFAULT_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        return PageRequest.of(page, size, createSort());
    }

    /**
     * Detect commonly accessed ToOne relationships to prevent N+1 problems.
     * This analyzes the entity class to find ManyToOne and OneToOne fields
     * that are likely to be accessed frequently.
     */
    private Set<String> detectCommonToOneFields() {
        Set<String> commonFields = new HashSet<>();
        
        try {
            // Use JPA metamodel to find ToOne relationships
            EntityType<T> entityType = entityManager.getMetamodel().entity(entityClass);
            
            // Add all ManyToOne and OneToOne relationships
            entityType.getSingularAttributes().forEach(attr -> {
                if (attr.getPersistentAttributeType() == Attribute.PersistentAttributeType.MANY_TO_ONE ||
                    attr.getPersistentAttributeType() == Attribute.PersistentAttributeType.ONE_TO_ONE) {
                    commonFields.add(attr.getName());
                }
            });
            
        } catch (Exception e) {
            // Fallback to reflection if metamodel fails
            Field[] fields = entityClass.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(javax.persistence.ManyToOne.class) ||
                    field.isAnnotationPresent(javax.persistence.OneToOne.class)) {
                    commonFields.add(field.getName());
                }
            }
        }
        
        return commonFields;
    }
}
