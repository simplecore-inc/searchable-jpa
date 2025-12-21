package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.service.join.JoinManager;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Executes two-phase queries for ToMany relationship optimization.
 * 
 * Phase 1: Query IDs only with conditions and pagination
 * Phase 2: Query full entities using IN clause with the retrieved IDs
 * 
 * Features:
 * - Automatic IN clause batching to prevent database limitations
 * - Memory-efficient processing for large result sets
 * - Count query elimination for improved performance
 */
@Slf4j
public class TwoPhaseQueryExecutor<T> {
    
    // Maximum number of IDs in a single IN clause to prevent database limitations
    // Oracle has 1000 limit, so we use 500 for safety margin
    private static final int MAX_IN_CLAUSE_SIZE = 500;
    
    private final SearchCondition<?> condition;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final RelationshipAnalyzer<T> relationshipAnalyzer;
    private final JoinStrategyManager<T> joinStrategyManager;

    public TwoPhaseQueryExecutor(SearchCondition<?> condition,
                                 EntityManager entityManager,
                                 Class<T> entityClass,
                                 JpaSpecificationExecutor<T> specificationExecutor) {
        this.condition = condition;
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.specificationExecutor = specificationExecutor;
        this.relationshipAnalyzer = new RelationshipAnalyzer<>(entityManager, entityClass);
        this.joinStrategyManager = new JoinStrategyManager<>(entityManager, entityClass);
    }

    /**
     * Two-phase query optimization is now applied to ALL queries for consistent performance.
     * This method is deprecated and will be removed in future versions.
     * 
     * @deprecated All queries now use two-phase optimization by default
     */
    @Deprecated
    public boolean shouldUseTwoPhaseQuery(Set<String> toManyPaths) {
        return true; // Always use two-phase optimization
    }

    /**
     * Executes optimized two-phase query with automatic IN clause batching.
     *
     * @deprecated Use {@link #executeWithTwoPhaseOptimization(PageRequest, Set)} instead
     */
    @Deprecated
    public Page<T> executeWithTwoPhaseOptimization(PageRequest pageRequest) {
        return executeWithTwoPhaseOptimization(pageRequest, Collections.emptySet());
    }

    /**
     * Executes optimized two-phase query with automatic IN clause batching.
     *
     * @param pageRequest the pagination request
     * @param fetchFields the fields to explicitly fetch join in Phase 2
     */
    public Page<T> executeWithTwoPhaseOptimization(PageRequest pageRequest, Set<String> fetchFields) {
        // Phase 1: Get IDs only
        List<Object> ids = executePhaseOneQuery(pageRequest);

        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageRequest, 0);
        }

        // Phase 2: Get full entities using batched IN clauses with fetch joins
        List<T> entities = executePhaseTwoQuery(ids, pageRequest.getSort(), fetchFields);

        // Phase 3: Get total count for accurate pagination
        long totalCount = executeCountQuery();

        return new PageImpl<>(entities, pageRequest, totalCount);
    }

    /**
     * Phase 1: Execute query to get IDs only with conditions and pagination.
     */
    private List<Object> executePhaseOneQuery(PageRequest pageRequest) {
        Set<String> joinPaths = extractJoinPaths(condition.getNodes());
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
        
        // Handle composite key entities
        if ("__COMPOSITE_KEY__".equals(primaryKeyField)) {
            return executePhaseOneQueryWithCompositeKey(pageRequest, joinPaths);
        }

        // Use regular query for single primary key
        return executeRegularPhaseOneQuery(pageRequest, joinPaths, primaryKeyField);
    }

    /**
     * Phase 1 for composite key entities: Execute query to get composite IDs.
     */
    private List<Object> executePhaseOneQueryWithCompositeKey(PageRequest pageRequest, Set<String> joinPaths) {
        List<String> idFields = SearchableFieldUtils.getCompositeKeyFieldNames(entityManager, entityClass);
        
        if (idFields.isEmpty()) {
            log.warn("No composite key fields found for entity {}, falling back to regular query", entityClass.getSimpleName());
            return executeRegularPhaseOneQuery(pageRequest, joinPaths, "id"); // fallback
        }
        
        // Check if this is @EmbeddedId or @IdClass
        boolean isEmbeddedId = isEmbeddedIdEntity();
        log.debug("Composite key type for {}: {}", entityClass.getSimpleName(), 
            isEmbeddedId ? "@EmbeddedId" : "@IdClass");
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> multiSelectQuery = cb.createQuery(Object[].class);
        Root<T> root = multiSelectQuery.from(entityClass);
        joinStrategyManager.applyRegularJoinsOnly(root, joinPaths);

        // Build selection list: all ID fields + sort columns
        List<Selection<?>> selections = new ArrayList<>();
        for (String idField : idFields) {
            if (isEmbeddedId) {
                // For @EmbeddedId: root.get("id").get("fieldName")
                selections.add(root.get("id").get(idField));
            } else {
                // For @IdClass: root.get("fieldName")
                selections.add(root.get(idField));
            }
        }
        
        // Add sort columns if needed (excluding ID fields already included)
        for (org.springframework.data.domain.Sort.Order sortOrder : pageRequest.getSort()) {
            String sortProperty = sortOrder.getProperty();
            // Skip __COMPOSITE_KEY__ marker and ID fields already included
            if (!"__COMPOSITE_KEY__".equals(sortProperty) && !idFields.contains(sortProperty)) {
                selections.add(getPath(root, sortProperty));
            }
        }
        
        multiSelectQuery.multiselect(selections).distinct(true);

        // Apply search conditions
        Predicate predicate = createPredicates(root, multiSelectQuery, cb);
        if (predicate != null) {
            multiSelectQuery.where(predicate);
        }

        // Apply sorting (skip __COMPOSITE_KEY__ marker)
        if (pageRequest.getSort().isSorted()) {
            List<Order> orders = pageRequest.getSort().stream()
                .filter(sortOrder -> !"__COMPOSITE_KEY__".equals(sortOrder.getProperty()))
                .map(sortOrder -> sortOrder.isAscending() 
                    ? cb.asc(getPath(root, sortOrder.getProperty()))
                    : cb.desc(getPath(root, sortOrder.getProperty())))
                .collect(Collectors.toList());
            if (!orders.isEmpty()) {
                multiSelectQuery.orderBy(orders);
            }
        }

        // Execute query
        TypedQuery<Object[]> typedQuery = entityManager.createQuery(multiSelectQuery);
        typedQuery.setFirstResult((int) pageRequest.getOffset());
        typedQuery.setMaxResults(pageRequest.getPageSize());
        
        // Return composite key objects (Object[] arrays representing each composite key)
        return typedQuery.getResultList().stream()
                .map(row -> {
                    // Create composite key representation
                    if (idFields.size() == 1) {
                        return row[0]; // Single ID field
                    } else {
                        // Multiple ID fields - return as array for composite key
                        Object[] compositeKey = new Object[idFields.size()];
                        System.arraycopy(row, 0, compositeKey, 0, idFields.size());
                        return compositeKey;
                    }
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Regular Phase 1 query (extracted for reuse).
     */
    private List<Object> executeRegularPhaseOneQuery(PageRequest pageRequest, Set<String> joinPaths, String primaryKeyField) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        Root<T> root;

        // Determine if we need to include sort columns in SELECT for SQL Server compatibility
        boolean needsSortColumnsInSelect = pageRequest.getSort().isSorted() && 
                                         !pageRequest.getSort().stream()
                                                    .allMatch(order -> order.getProperty().equals(primaryKeyField));

        if (needsSortColumnsInSelect) {
            // Use multi-select to include sort columns for SQL Server compatibility
            CriteriaQuery<Object[]> multiSelectQuery = cb.createQuery(Object[].class);
            root = multiSelectQuery.from(entityClass);
            joinStrategyManager.applyRegularJoinsOnly(root, joinPaths);

            // Build selection list: ID + sort columns
            List<Selection<?>> selections = new ArrayList<>();
            selections.add(root.get(primaryKeyField));
            
            for (org.springframework.data.domain.Sort.Order sortOrder : pageRequest.getSort()) {
                if (!sortOrder.getProperty().equals(primaryKeyField)) {
                    selections.add(getPath(root, sortOrder.getProperty()));
                }
            }
            
            multiSelectQuery.multiselect(selections).distinct(true);

            // Apply search conditions
            Predicate predicate = createPredicates(root, multiSelectQuery, cb);
            if (predicate != null) {
                multiSelectQuery.where(predicate);
            }

            // Apply sorting
            List<Order> orders = pageRequest.getSort().stream()
                .map(sortOrder -> sortOrder.isAscending() 
                    ? cb.asc(getPath(root, sortOrder.getProperty()))
                    : cb.desc(getPath(root, sortOrder.getProperty())))
                .collect(Collectors.toList());
            multiSelectQuery.orderBy(orders);

            // Execute query and extract IDs
            TypedQuery<Object[]> typedQuery = entityManager.createQuery(multiSelectQuery);
            typedQuery.setFirstResult((int) pageRequest.getOffset());
            typedQuery.setMaxResults(pageRequest.getPageSize());
            
            return typedQuery.getResultList().stream()
                    .map(row -> row[0]) // Extract ID from first column
                    .collect(Collectors.toList());
        } else {
            // Simple ID-only select when sorting by primary key or no sorting
            CriteriaQuery<Object> singleSelectQuery = cb.createQuery(Object.class);
            root = singleSelectQuery.from(entityClass);
            joinStrategyManager.applyRegularJoinsOnly(root, joinPaths);
            
            singleSelectQuery.select(root.get(primaryKeyField)).distinct(true);

            // Apply search conditions
            Predicate predicate = createPredicates(root, singleSelectQuery, cb);
            if (predicate != null) {
                singleSelectQuery.where(predicate);
            }

            // Apply sorting
            if (pageRequest.getSort().isSorted()) {
                List<Order> orders = pageRequest.getSort().stream()
                    .map(sortOrder -> sortOrder.isAscending() 
                        ? cb.asc(getPath(root, sortOrder.getProperty()))
                        : cb.desc(getPath(root, sortOrder.getProperty())))
                    .collect(Collectors.toList());
                singleSelectQuery.orderBy(orders);
            }

            // Execute query
            TypedQuery<Object> typedQuery = entityManager.createQuery(singleSelectQuery);
            typedQuery.setFirstResult((int) pageRequest.getOffset());
            typedQuery.setMaxResults(pageRequest.getPageSize());
            
            return typedQuery.getResultList();
        }
    }

    /**
     * Phase 3: Execute count query to get total number of records.
     */
    private long executeCountQuery() {
        Set<String> joinPaths = extractJoinPaths(condition.getNodes());
        
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> root = countQuery.from(entityClass);
        
        // Apply joins for count query (using regular joins only to avoid duplicates)
        joinStrategyManager.applyRegularJoinsOnly(root, joinPaths);
        
        // Use countDistinct for accurate count when joins are present
        if (joinPaths.isEmpty()) {
            countQuery.select(cb.count(root));
        } else {
            String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
            log.debug("Count query - Entity: {}, Primary key field: {}, Join paths: {}", 
                entityClass.getSimpleName(), primaryKeyField, joinPaths);
            
            // Handle composite key entities for count query
            if ("__COMPOSITE_KEY__".equals(primaryKeyField)) {
                // For composite keys, use simple count(root) instead of countDistinct
                // This is because SQL Server doesn't support COUNT(DISTINCT col1, col2) syntax
                // and we're already applying joins to avoid duplicates at the specification level
                log.debug("Using simple count for composite key entity to avoid SQL Server limitations");
                countQuery.select(cb.count(root));
            } else {
                // Regular single primary key
                log.debug("Using countDistinct for single primary key: {}", primaryKeyField);
                countQuery.select(cb.countDistinct(root.get(primaryKeyField)));
            }
        }
        
        // Apply search conditions
        Predicate predicate = createPredicates(root, countQuery, cb);
        if (predicate != null) {
            countQuery.where(predicate);
        }
        
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Phase 2: Execute batched queries to get full entities using IN clauses.
     * Automatically splits large ID lists into smaller batches to prevent database limitations.
     *
     * @deprecated Use {@link #executePhaseTwoQuery(List, Sort, Set)} instead
     */
    @Deprecated
    private List<T> executePhaseTwoQuery(List<Object> ids, Sort sort) {
        return executePhaseTwoQuery(ids, sort, Collections.emptySet());
    }

    /**
     * Phase 2: Execute batched queries to get full entities using IN clauses.
     * Automatically splits large ID lists into smaller batches to prevent database limitations.
     *
     * @param ids the entity IDs from Phase 1
     * @param sort the sort criteria
     * @param fetchFields the fields to explicitly fetch join
     */
    private List<T> executePhaseTwoQuery(List<Object> ids, Sort sort, Set<String> fetchFields) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        // If ID count is within safe limit, execute single query
        if (ids.size() <= MAX_IN_CLAUSE_SIZE) {
            return executeSingleInQuery(ids, sort, fetchFields);
        }

        // Split into batches and execute multiple queries
        return executeBatchedInQueries(ids, sort, fetchFields);
    }

    /**
     * Execute single IN query for small ID lists.
     *
     * @deprecated Use {@link #executeSingleInQuery(List, Sort, Set)} instead
     */
    @Deprecated
    private List<T> executeSingleInQuery(List<Object> ids, Sort sort) {
        return executeSingleInQuery(ids, sort, Collections.emptySet());
    }

    /**
     * Execute single IN query for small ID lists with fetch joins.
     *
     * @param ids the entity IDs to query
     * @param sort the sort criteria
     * @param fetchFields the fields to explicitly fetch join
     */
    private List<T> executeSingleInQuery(List<Object> ids, Sort sort, Set<String> fetchFields) {
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);

        // Handle composite key entities
        if ("__COMPOSITE_KEY__".equals(primaryKeyField)) {
            return executeSingleInQueryWithCompositeKey(ids, sort, fetchFields);
        }

        // Build specification with fetch joins
        Specification<T> spec = (root, query, cb) -> {
            // Apply fetch joins for explicitly specified fields
            if (fetchFields != null && !fetchFields.isEmpty() && !Long.class.equals(query.getResultType())) {
                for (String fetchField : fetchFields) {
                    try {
                        if (fetchField.contains(".")) {
                            // Handle nested paths
                            applyNestedFetchJoin(root, fetchField);
                        } else {
                            // Simple path - check if not already fetched
                            boolean alreadyFetched = root.getFetches().stream()
                                .anyMatch(fetch -> fetch.getAttribute().getName().equals(fetchField));
                            if (!alreadyFetched) {
                                root.fetch(fetchField, JoinType.LEFT);
                            }
                        }
                        log.debug("Applied fetch join for field: {}", fetchField);
                    } catch (Exception e) {
                        log.warn("Failed to apply fetch join for field '{}': {}", fetchField, e.getMessage());
                    }
                }
            }

            return root.get(primaryKeyField).in(ids);
        };

        List<T> entities = specificationExecutor.findAll(spec, sort);
        return reorderEntitiesByIds(entities, ids);
    }

    /**
     * Applies fetch join for nested paths by building the path step by step.
     */
    private void applyNestedFetchJoin(Root<T> root, String nestedPath) {
        String[] pathParts = nestedPath.split("\\.");
        From<?, ?> currentFrom = root;

        for (String part : pathParts) {
            // Check if this part is already fetched
            boolean alreadyFetched = currentFrom.getFetches().stream()
                .anyMatch(fetch -> fetch.getAttribute().getName().equals(part));

            if (!alreadyFetched) {
                currentFrom = (From<?, ?>) currentFrom.fetch(part, JoinType.LEFT);
                log.debug("Applied nested fetch join for path part: {}", part);
            } else {
                // Find existing fetch to continue the path
                currentFrom = (From<?, ?>) currentFrom.getFetches().stream()
                    .filter(fetch -> fetch.getAttribute().getName().equals(part))
                    .findFirst()
                    .orElse(null);
                if (currentFrom == null) {
                    log.warn("Expected fetch not found for path part: {}", part);
                    return;
                }
                log.debug("Reusing existing fetch for path part: {}", part);
            }
        }
    }
    
    /**
     * Execute single IN query for composite key entities.
     *
     * @deprecated Use {@link #executeSingleInQueryWithCompositeKey(List, Sort, Set)} instead
     */
    @Deprecated
    private List<T> executeSingleInQueryWithCompositeKey(List<Object> ids, Sort sort) {
        return executeSingleInQueryWithCompositeKey(ids, sort, Collections.emptySet());
    }

    /**
     * Execute single IN query for composite key entities with fetch joins.
     */
    private List<T> executeSingleInQueryWithCompositeKey(List<Object> ids, Sort sort, Set<String> fetchFields) {
        List<String> idFields = SearchableFieldUtils.getCompositeKeyFieldNames(entityManager, entityClass);
        boolean isEmbeddedId = isEmbeddedIdEntity();
        
        if (idFields.isEmpty()) {
            log.warn("No composite key fields found for entity {}, falling back to regular query", entityClass.getSimpleName());
            return Collections.emptyList();
        }
        
        log.debug("Entity {} is using {} composite key with fields: {}", 
            entityClass.getSimpleName(), 
            isEmbeddedId ? "@EmbeddedId" : "@IdClass", 
            idFields);
        
        // Build specification for composite key matching
        Specification<T> spec = (root, query, cb) -> {
            // Apply fetch joins for explicitly specified fields
            if (fetchFields != null && !fetchFields.isEmpty() && !Long.class.equals(query.getResultType())) {
                for (String fetchField : fetchFields) {
                    try {
                        if (fetchField.contains(".")) {
                            applyNestedFetchJoin(root, fetchField);
                        } else {
                            boolean alreadyFetched = root.getFetches().stream()
                                .anyMatch(fetch -> fetch.getAttribute().getName().equals(fetchField));
                            if (!alreadyFetched) {
                                root.fetch(fetchField, JoinType.LEFT);
                            }
                        }
                        log.debug("Applied fetch join for field: {}", fetchField);
                    } catch (Exception e) {
                        log.warn("Failed to apply fetch join for field '{}': {}", fetchField, e.getMessage());
                    }
                }
            }

            List<Predicate> orPredicates = new ArrayList<>();

            log.debug("Building composite key conditions for {} IDs, fields: {}", ids.size(), idFields);
            
            for (Object id : ids) {
                if (id instanceof Object[]) {
                    Object[] compositeKey = (Object[]) id;
                    log.debug("Processing composite key: {}", Arrays.toString(compositeKey));
                    
                    if (compositeKey.length == idFields.size()) {
                        List<Predicate> andPredicates = new ArrayList<>();
                        for (int i = 0; i < idFields.size(); i++) {
                            String fieldName = idFields.get(i);
                            Object fieldValue = compositeKey[i];
                            log.debug("Adding condition: {} = {}", fieldName, fieldValue);
                            
                            // For @EmbeddedId, access fields through the embedded id object
                            // For @IdClass, access fields directly on the root
                            Path<?> fieldPath = isEmbeddedId ? root.get("id").get(fieldName) : root.get(fieldName);
                            andPredicates.add(cb.equal(fieldPath, fieldValue));
                        }
                        orPredicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
                    } else {
                        log.warn("Composite key length mismatch: expected {}, got {}", idFields.size(), compositeKey.length);
                    }
                } else {
                    // Single ID field case
                    if (idFields.size() == 1) {
                        log.debug("Processing single ID: {}", id);
                        String fieldName = idFields.get(0);
                        Path<?> fieldPath = isEmbeddedId ? root.get("id").get(fieldName) : root.get(fieldName);
                        orPredicates.add(cb.equal(fieldPath, id));
                    } else {
                        log.warn("Expected composite key array but got single value: {}", id);
                    }
                }
            }
            
            log.debug("Built {} OR predicates", orPredicates.size());
            return orPredicates.isEmpty() ? cb.disjunction() : cb.or(orPredicates.toArray(new Predicate[0]));
        };

        // Filter out __COMPOSITE_KEY__ from sort orders
        Sort filteredSort = Sort.by(
            sort.stream()
                .filter(order -> !"__COMPOSITE_KEY__".equals(order.getProperty()))
                .collect(Collectors.toList())
        );
        
        List<T> entities = specificationExecutor.findAll(spec, filteredSort);
        return reorderEntitiesByIds(entities, ids);
    }

    /**
     * Execute multiple batched IN queries for large ID lists.
     * Combines results while preserving original order.
     *
     * @deprecated Use {@link #executeBatchedInQueries(List, Sort, Set)} instead
     */
    @Deprecated
    private List<T> executeBatchedInQueries(List<Object> ids, Sort sort) {
        return executeBatchedInQueries(ids, sort, Collections.emptySet());
    }

    /**
     * Execute multiple batched IN queries for large ID lists with fetch joins.
     * Combines results while preserving original order.
     *
     * @param ids the entity IDs from Phase 1
     * @param sort the sort criteria
     * @param fetchFields the fields to explicitly fetch join
     */
    private List<T> executeBatchedInQueries(List<Object> ids, Sort sort, Set<String> fetchFields) {
        List<T> allResults = new ArrayList<>();

        // Split IDs into batches
        for (int i = 0; i < ids.size(); i += MAX_IN_CLAUSE_SIZE) {
            int endIndex = Math.min(i + MAX_IN_CLAUSE_SIZE, ids.size());
            List<Object> batchIds = ids.subList(i, endIndex);

            log.debug("Executing batch {}/{} with {} IDs",
                (i / MAX_IN_CLAUSE_SIZE) + 1,
                (ids.size() + MAX_IN_CLAUSE_SIZE - 1) / MAX_IN_CLAUSE_SIZE,
                batchIds.size());

            // Execute query for this batch with fetch joins
            List<T> batchResults = executeSingleInQuery(batchIds, sort, fetchFields);
            allResults.addAll(batchResults);
        }

        log.debug("Executed {} batches, total results: {}",
            (ids.size() + MAX_IN_CLAUSE_SIZE - 1) / MAX_IN_CLAUSE_SIZE,
            allResults.size());

        // Final ordering is maintained by reorderEntitiesByIds in executeSingleInQuery
        return allResults;
    }

    /**
     * Reorder entities to match ID order from phase 1.
     */
    private List<T> reorderEntitiesByIds(List<T> entities, List<Object> orderedIds) {
        Map<String, T> entityMap = new HashMap<>();

        for (T entity : entities) {
            try {
                Object id = getEntityId(entity);
                if (id != null) {
                    String keyStr = createComparableKey(id);
                    entityMap.put(keyStr, entity);
                }
            } catch (Exception e) {
                log.warn("Failed to get entity ID for reordering: {}", e.getMessage());
                return entities; // Return original order if ID extraction fails
            }
        }

        List<T> reorderedEntities = new ArrayList<>();
        for (Object orderedId : orderedIds) {
            String keyStr = createComparableKey(orderedId);
            T entity = entityMap.get(keyStr);
            if (entity != null) {
                reorderedEntities.add(entity);
            } else {
                log.warn("No entity found for ordered key: {}", keyStr);
            }
        }
        
        log.debug("Reordered {} entities out of {} ordered IDs", reorderedEntities.size(), orderedIds.size());
        return reorderedEntities;
    }
    
    /**
     * Create a comparable string key for composite keys.
     */
    private String createComparableKey(Object id) {
        if (id instanceof Object[]) {
            Object[] compositeKey = (Object[]) id;
            return Arrays.toString(compositeKey);
        } else {
            return String.valueOf(id);
        }
    }

    /**
     * Extract entity ID using reflection.
     */
    private Object getEntityId(T entity) throws Exception {
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
        if (primaryKeyField == null) {
            throw new RuntimeException(MessageUtils.getMessage("executor.primary.key.not.found", new Object[]{entityClass.getSimpleName()}));
        }

        // Handle composite key entities
        if ("__COMPOSITE_KEY__".equals(primaryKeyField)) {
            return getCompositeEntityId(entity);
        }

        // Try getter method first
        String getterName = "get" + Character.toUpperCase(primaryKeyField.charAt(0)) + primaryKeyField.substring(1);
        try {
            Method getter = entityClass.getMethod(getterName);
            return getter.invoke(entity);
        } catch (NoSuchMethodException e) {
            // Fallback to direct field access
            Field field = entityClass.getDeclaredField(primaryKeyField);
            field.setAccessible(true);
            return field.get(entity);
        }
    }
    
    /**
     * Extract composite entity ID as Object array.
     */
    private Object getCompositeEntityId(T entity) throws Exception {
        List<String> idFields = SearchableFieldUtils.getCompositeKeyFieldNames(entityManager, entityClass);
        boolean isEmbeddedId = isEmbeddedIdEntity();
        
        if (idFields.isEmpty()) {
            throw new RuntimeException(MessageUtils.getMessage("executor.composite.key.not.found", new Object[]{entityClass.getSimpleName()}));
        }
        
        if (idFields.size() == 1) {
            // Single ID field
            String idField = idFields.get(0);
            return getCompositeFieldValue(entity, idField, isEmbeddedId);
        } else {
            // Multiple ID fields - create composite key array
            Object[] compositeKey = new Object[idFields.size()];
            for (int i = 0; i < idFields.size(); i++) {
                String idField = idFields.get(i);
                compositeKey[i] = getCompositeFieldValue(entity, idField, isEmbeddedId);
            }
            return compositeKey;
        }
    }
    
    /**
     * Extract field value from composite key entity.
     * Handles both @EmbeddedId and @IdClass entities.
     */
    private Object getCompositeFieldValue(T entity, String fieldName, boolean isEmbeddedId) throws Exception {
        if (isEmbeddedId) {
            // For @EmbeddedId: get the embedded id object first, then the field from it
            Method idGetter = entityClass.getMethod("getId");
            Object embeddedId = idGetter.invoke(entity);
            if (embeddedId == null) {
                throw new RuntimeException(MessageUtils.getMessage("executor.embedded.id.null", new Object[]{entityClass.getSimpleName()}));
            }
            
            // Get field from embedded ID object
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                Method fieldGetter = embeddedId.getClass().getMethod(getterName);
                return fieldGetter.invoke(embeddedId);
            } catch (NoSuchMethodException e) {
                Field field = embeddedId.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(embeddedId);
            }
        } else {
            // For @IdClass: get field directly from entity
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            try {
                Method getter = entityClass.getMethod(getterName);
                return getter.invoke(entity);
            } catch (NoSuchMethodException e) {
                Field field = entityClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(entity);
            }
        }
    }

    private Set<String> extractJoinPaths(List<SearchCondition.Node> nodes) {
        Set<String> joinPaths = new HashSet<>();
        if (nodes == null) return joinPaths;

        for (SearchCondition.Node node : nodes) {
            if (node instanceof SearchCondition.Condition) {
                SearchCondition.Condition condition = (SearchCondition.Condition) node;
                String entityField = condition.getEntityField();
                if (entityField != null && entityField.contains(".")) {
                    String[] pathParts = entityField.split("\\.");
                    StringBuilder path = new StringBuilder();
                    for (int i = 0; i < pathParts.length - 1; i++) {
                        if (path.length() > 0) path.append(".");
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

    private Predicate createPredicates(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<SearchCondition.Node> nodes = condition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        JoinManager<T> joinManager = new JoinManager<>(entityManager, root);
        PredicateBuilder<T> predicateBuilder = new PredicateBuilder<>(cb, joinManager);
        SpecificationBuilder<T> specBuilder = new SpecificationBuilder<>(predicateBuilder);

        SearchCondition.Node firstNode = nodes.get(0);
        Predicate result = specBuilder.build(root, query, cb, firstNode);
        if (result == null) {
            return null;
        }

        for (int i = 1; i < nodes.size(); i++) {
            SearchCondition.Node currentNode = nodes.get(i);
            Predicate currentPredicate = specBuilder.build(root, query, cb, currentNode);
            if (currentPredicate == null) continue;

            if (currentNode.getOperator() == dev.simplecore.searchable.core.condition.operator.LogicalOperator.OR) {
                result = cb.or(result, currentPredicate);
            } else {
                result = cb.and(result, currentPredicate);
            }
        }

        return result;
    }
    
    /**
     * Checks if the entity uses @EmbeddedId composite key.
     */
    private boolean isEmbeddedIdEntity() {
        try {
            EntityType<?> entityType = entityManager.getMetamodel().entity(entityClass);
            if (entityType.hasSingleIdAttribute()) {
                SingularAttribute<?, ?> idAttribute = entityType.getId(entityType.getIdType().getJavaType());
                // Use reflection to check for @EmbeddedId annotation
                try {
                    Field field = entityClass.getDeclaredField(idAttribute.getName());
                    return field.isAnnotationPresent(jakarta.persistence.EmbeddedId.class);
                } catch (NoSuchFieldException e) {
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to check if entity {} uses @EmbeddedId: {}", entityClass.getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * Get path for a potentially nested property.
     * Handles properties like "position.name" by creating appropriate joins.
     */
    private Path<?> getPath(Root<T> root, String property) {
        if (!property.contains(".")) {
            // Simple property
            return root.get(property);
        }
        
        // Nested property - need to create joins
        String[] parts = property.split("\\.");
        Path<?> path = root;
        
        for (int i = 0; i < parts.length - 1; i++) {
            // Create join for each intermediate path
            if (path instanceof From) {
                From<?, ?> from = (From<?, ?>) path;
                Join<?, ?> join = null;
                
                // Check if join already exists
                for (Join<?, ?> existingJoin : from.getJoins()) {
                    if (existingJoin.getAttribute().getName().equals(parts[i])) {
                        join = existingJoin;
                        break;
                    }
                }
                
                // Create new join if not exists
                if (join == null) {
                    join = from.join(parts[i], JoinType.LEFT);
                }
                path = join;
            }
        }
        
        // Get the final property
        return path.get(parts[parts.length - 1]);
    }
} 