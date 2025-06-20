package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.service.join.JoinManager;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes two-phase queries for optimal performance with large datasets and ToMany relationships.
 * Phase 1: Query IDs only with all conditions applied
 * Phase 2: Fetch full entities by IDs with all relationships
 */
@Slf4j
public class TwoPhaseQueryExecutor<T> {

    private final SearchCondition<?> condition;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final RelationshipAnalyzer<T> relationshipAnalyzer;
    private final JoinStrategyManager<T> joinStrategyManager;
    private final EntityGraphManager<T> entityGraphManager;

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
        this.entityGraphManager = new EntityGraphManager<>(entityManager, entityClass);
    }

    /**
     * Execute query with two-phase optimization.
     * Phase 1: Get IDs only (regular joins to avoid memory paging)
     * Phase 2: Load complete entities with smart fetch joins
     */
    public Page<T> executeWithTwoPhaseOptimization(PageRequest pageRequest) {
        log.debug("Starting two-phase optimization query");

        Set<String> allJoinPaths = extractJoinPaths(condition.getNodes());
        Set<String> toManyPaths = allJoinPaths.stream()
                .filter(path -> relationshipAnalyzer.isToManyPath(createDummyRoot(), path))
                .collect(Collectors.toSet());

        log.debug("All join paths: {}", allJoinPaths);
        log.debug("ToMany paths detected: {}", toManyPaths);

        // Check if two-phase optimization is needed
        if (shouldUseTwoPhaseQuery(toManyPaths)) {
            log.debug("Two-phase query optimization is NEEDED");
            return executeTwoPhaseQuery(pageRequest, allJoinPaths);
        } else {
            log.debug("Two-phase query optimization is NOT needed, using regular query");
            throw new UnsupportedOperationException("Regular query should be handled by caller");
        }
    }

    /**
     * Determines whether to use two-phase query based on complexity analysis.
     */
    public boolean shouldUseTwoPhaseQuery(Set<String> toManyPaths) {
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
     * Executes two-phase query for optimal performance with large datasets.
     */
    private Page<T> executeTwoPhaseQuery(PageRequest pageRequest, Set<String> allJoinPaths) {
        log.debug("Starting two-phase query execution");

        // Phase 1: Get IDs only (fast pagination with all conditions)
        List<Object> entityIds = executePhaseOneQuery(pageRequest);
        log.debug("Phase 1 completed. Entity IDs retrieved: {} IDs", entityIds.size());

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
     */
    private List<Object> executePhaseOneQuery(PageRequest pageRequest) {
        log.debug("Phase 1: Starting ID-only query");

        Set<String> conditionJoinPaths = extractJoinPaths(condition.getNodes());
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        Root<T> singleRoot = null;

        Sort sort = pageRequest.getSort();
        boolean needsDistinct = !conditionJoinPaths.isEmpty();
        boolean hasSortFields = sort.isSorted() && sort.stream().anyMatch(order -> !order.getProperty().equals(primaryKeyField));

        if (needsDistinct && hasSortFields) {
            // Use multi-select to include sort fields for H2 compatibility
            return executePhaseOneQueryWithSortFields(pageRequest, conditionJoinPaths, primaryKeyField);
        } else {
            // Standard ID-only query
            CriteriaQuery<Object> singleQuery = cb.createQuery(Object.class);
            singleRoot = singleQuery.from(entityClass);

            joinStrategyManager.applyRegularJoinsOnly(singleRoot, conditionJoinPaths);

            singleQuery.select(singleRoot.get(primaryKeyField));

            if (needsDistinct) {
                singleQuery.distinct(true);
            }

            Predicate predicate = createPredicates(singleRoot, singleQuery, cb);
            if (predicate != null) {
                singleQuery.where(predicate);
            }

            // Apply sorting
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

            TypedQuery<Object> singleTypedQuery = entityManager.createQuery(singleQuery);
            singleTypedQuery.setFirstResult((int) pageRequest.getOffset());
            singleTypedQuery.setMaxResults(pageRequest.getPageSize());

            List<Object> ids = singleTypedQuery.getResultList();
            log.debug("Phase 1: Retrieved {} IDs", ids.size());
            return ids;
        }
    }

    /**
     * Execute phase 1 query including sort fields for H2 DISTINCT compatibility.
     */
    private List<Object> executePhaseOneQueryWithSortFields(PageRequest pageRequest, Set<String> conditionJoinPaths, String primaryKeyField) {
        log.debug("Phase 1: Using multi-select query for H2 DISTINCT compatibility");

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> multiQuery = cb.createQuery(Object[].class);
        Root<T> multiRoot = multiQuery.from(entityClass);

        joinStrategyManager.applyRegularJoinsOnly(multiRoot, conditionJoinPaths);

        // Build selection list: primary key + sort fields
        List<Selection<?>> selections = new ArrayList<>();
        selections.add(multiRoot.get(primaryKeyField));

        Sort sort = pageRequest.getSort();
        for (Sort.Order order : sort) {
            if (!order.getProperty().equals(primaryKeyField)) {
                selections.add(multiRoot.get(order.getProperty()));
            }
        }

        multiQuery.multiselect(selections);
        multiQuery.distinct(true);

        Predicate predicate = createPredicates(multiRoot, multiQuery, cb);
        if (predicate != null) {
            multiQuery.where(predicate);
        }

        // Apply sorting
        if (sort.isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order order : sort) {
                Path<?> path = multiRoot.get(order.getProperty());
                if (order.isAscending()) {
                    orders.add(cb.asc(path));
                } else {
                    orders.add(cb.desc(path));
                }
            }
            multiQuery.orderBy(orders);
        }

        TypedQuery<Object[]> multiTypedQuery = entityManager.createQuery(multiQuery);
        multiTypedQuery.setFirstResult((int) pageRequest.getOffset());
        multiTypedQuery.setMaxResults(pageRequest.getPageSize());

        List<Object[]> results = multiTypedQuery.getResultList();

        // Extract only the primary key values (first column)
        List<Object> ids = results.stream()
                .map(row -> row[0])
                .collect(Collectors.toList());

        log.debug("Phase 1: Retrieved {} IDs with sort fields", ids.size());
        return ids;
    }

    /**
     * Phase 2: Fetch full entities by IDs with all relationships loaded.
     */
    private List<T> executePhaseTwoQuery(List<Object> entityIds, Set<String> allJoinPaths, Sort sort) {
        Specification<T> fullDataSpec = (root, query, cb) -> {
            joinStrategyManager.applySmartFetchJoins(root, allJoinPaths);
            query.distinct(true);

            String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
            return root.get(primaryKeyField).in(entityIds);
        };

        List<T> entities = specificationExecutor.findAll(fullDataSpec, sort);
        log.debug("Phase 2: Retrieved {} raw entities from database", entities.size());

        return reorderEntitiesByIds(entities, entityIds);
    }

    /**
     * Reorder entities to match the original ID order from phase 1.
     */
    private List<T> reorderEntitiesByIds(List<T> entities, List<Object> orderedIds) {
        log.debug("Reordering entities: {} entities, {} ordered IDs", entities.size(), orderedIds.size());

        Map<Object, T> entityMap = new HashMap<>();
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);

        for (T entity : entities) {
            try {
                Object id = null;

                try {
                    Method getIdMethod = entity.getClass().getMethod("getId");
                    id = getIdMethod.invoke(entity);
                } catch (Exception getIdException) {
                    Field idField = entityClass.getDeclaredField(primaryKeyField);
                    idField.setAccessible(true);
                    id = idField.get(entity);
                }

                if (id != null) {
                    entityMap.put(id, entity);
                }
            } catch (Exception e) {
                log.error("Failed to extract ID from entity: {}", e.getMessage());
                return entities;
            }
        }

        List<T> reorderedEntities = orderedIds.stream()
                .map(entityMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.debug("Reordering completed: {} entities after reordering", reorderedEntities.size());
        return reorderedEntities;
    }

    /**
     * Get total count for two-phase query.
     */
    private long getTotalCountForTwoPhase() {
        Specification<T> countSpec = (root, query, cb) -> {
            Set<String> joinPaths = extractJoinPaths(condition.getNodes());
            joinStrategyManager.applyRegularJoinsOnly(root, joinPaths);

            query.distinct(true);

            return createPredicates(root, query, cb);
        };

        return specificationExecutor.count(countSpec);
    }

    private Root<T> createDummyRoot() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        return query.from(entityClass);
    }

    private Set<String> extractJoinPaths(List<SearchCondition.Node> nodes) {
        Set<String> joinPaths = new HashSet<>();
        if (nodes == null) return joinPaths;

        for (SearchCondition.Node node : nodes) {
            if (node instanceof SearchCondition.Condition) {
                SearchCondition.Condition condition = (SearchCondition.Condition) node;
                String entityField = condition.getEntityField();
                if (entityField != null && !entityField.isEmpty()) {
                    String[] pathParts = entityField.split("\\.");
                    StringBuilder path = new StringBuilder();

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

    private boolean hasComplexConditions() {
        if (condition.getNodes() == null) return false;

        long toManyConditionCount = condition.getNodes().stream()
                .filter(node -> node instanceof SearchCondition.Condition)
                .map(node -> (SearchCondition.Condition) node)
                .filter(cond -> {
                    String entityField = cond.getEntityField();
                    return entityField != null && entityField.contains(".") &&
                            relationshipAnalyzer.isToManyPath(createDummyRoot(), getRelationshipPath(entityField));
                })
                .count();

        return toManyConditionCount > 0;
    }

    private String getRelationshipPath(String entityField) {
        if (entityField == null || !entityField.contains(".")) {
            return entityField;
        }

        String[] parts = entityField.split("\\.");
        if (parts.length > 1) {
            return parts[0];
        }

        return entityField;
    }

    private Predicate createPredicates(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<SearchCondition.Node> nodes = condition.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        query.distinct(true);

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
} 