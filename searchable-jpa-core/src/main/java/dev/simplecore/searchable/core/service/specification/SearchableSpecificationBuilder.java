package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.service.join.JoinManager;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final RelationshipAnalyzer<T> relationshipAnalyzer;
    private final JoinStrategyManager<T> joinStrategyManager;
    private final TwoPhaseQueryExecutor<T> twoPhaseQueryExecutor;
    private final EntityGraphManager<T> entityGraphManager;
    
    // Cache for relationship analysis to prevent redundant calls
    private volatile Set<String> cachedCommonToOneFields;
    private final Object cacheLock = new Object();

    public SearchableSpecificationBuilder(@NonNull SearchCondition<?> condition,
                                          @NonNull EntityManager entityManager,
                                          @NonNull Class<T> entityClass,
                                          @NonNull JpaSpecificationExecutor<T> specificationExecutor) {
        this.condition = condition;
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.specificationExecutor = specificationExecutor;
        this.relationshipAnalyzer = new RelationshipAnalyzer<>(entityManager, entityClass);
        this.joinStrategyManager = new JoinStrategyManager<>(entityManager, entityClass);
        this.twoPhaseQueryExecutor = new TwoPhaseQueryExecutor<>(condition, entityManager, entityClass, specificationExecutor);
        this.entityGraphManager = new EntityGraphManager<>(entityManager, entityClass);
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
     * If no sort conditions are specified, automatically adds PK sorting in ascending order.
     */
    private Sort createSort() {
        SearchCondition.Sort sortCondition = condition.getSort();
        List<Sort.Order> sortOrders;
        
        if (sortCondition == null || sortCondition.getOrders().isEmpty()) {
            // No sort conditions specified - automatically add PK sorting
            sortOrders = new java.util.ArrayList<>();
        } else {
            // Convert SearchCondition orders to Spring Data Sort orders
            sortOrders = sortCondition.getOrders().stream()
                    .map(this::createOrder)
                    .collect(Collectors.toList());
        }

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
                    sortOrders.add(Sort.Order.by(primaryKeyField));

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
                                       jakarta.persistence.criteria.CriteriaQuery<?> query,
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
     * @return SpecificationWithPageable containing specification and page request
     * @deprecated Use buildAndExecuteWithTwoPhaseOptimization() instead for optimized pagination
     */
    @Deprecated
    public SpecificationWithPageable<T> build() {
        return new SpecificationWithPageable<>(
                buildSpecification(),
                buildPageRequest()
        );
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
                Set<String> commonToOneFields = getCachedCommonToOneFields();
                log.debug("Adding common ToOne fields for non-count query: {}", commonToOneFields);
                allJoinPaths.addAll(commonToOneFields);
            }

            // Apply joins (with different strategy for count vs select queries)
            joinStrategyManager.applyJoins(root, allJoinPaths, isCountQuery);

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
     * Execute query with two-phase optimization.
     * Phase 1: Get IDs only (regular joins to avoid memory paging)
     * Phase 2: Load complete entities with smart fetch joins
     *
     * This method now applies two-phase optimization to ALL queries for consistent performance.
     */
    public Page<T> buildAndExecuteWithTwoPhaseOptimization() {
        PageRequest pageRequest = buildPageRequest();

        // Collect all fetch fields (explicit + auto-detected)
        Set<String> allFetchFields = new HashSet<>();

        // Add explicitly specified fetch fields from SearchCondition
        Set<String> explicitFetchFields = condition.getFetchFields();
        if (explicitFetchFields != null && !explicitFetchFields.isEmpty()) {
            allFetchFields.addAll(explicitFetchFields);
            log.debug("Explicit fetch fields from SearchCondition: {}", explicitFetchFields);
        }

        // Add auto-detected common ToOne fields
        Set<String> commonToOneFields = getCachedCommonToOneFields();
        allFetchFields.addAll(commonToOneFields);

        log.debug("All fetch fields for two-phase query: {}", allFetchFields);

        // Always use two-phase optimization for all queries
        return twoPhaseQueryExecutor.executeWithTwoPhaseOptimization(pageRequest, allFetchFields);
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
     * Get cached common ToOne fields to prevent redundant relationship analysis.
     * Uses double-checked locking pattern for thread-safe lazy initialization.
     */
    private Set<String> getCachedCommonToOneFields() {
        Set<String> result = cachedCommonToOneFields;
        if (result == null) {
            synchronized (cacheLock) {
                result = cachedCommonToOneFields;
                if (result == null) {
                    result = relationshipAnalyzer.detectCommonToOneFields();
                    cachedCommonToOneFields = result;
                    log.debug("Cached common ToOne fields: {}", result);
                }
            }
        }
        return result;
    }

    private PageRequest buildPageRequest() {
        Integer pageNum = condition.getPage();
        Integer sizeNum = condition.getSize();

        int page = pageNum != null ? Math.max(0, pageNum) : 0;
        int size = sizeNum != null ? (sizeNum > 0 ? sizeNum : DEFAULT_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        return PageRequest.of(page, size, createSort());
    }

}
