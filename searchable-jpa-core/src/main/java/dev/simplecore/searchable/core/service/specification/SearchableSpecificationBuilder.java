package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.service.cursor.CursorPageConverter;
import dev.simplecore.searchable.core.service.join.JoinManager;
import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import javax.persistence.EntityManager;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.ManagedType;
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
     * Returns Sort.unsorted() if no orders defined.
     */
    private Sort createSort() {
        SearchCondition.Sort sortCondition = condition.getSort();
        if (sortCondition == null) {
            return Sort.unsorted();
        }

        List<SearchCondition.Order> orders = sortCondition.getOrders();
        return orders.isEmpty()
                ? Sort.unsorted()
                : Sort.by(orders.stream()
                .map(this::createOrder)
                .collect(Collectors.toList()));
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

    private Specification<T> buildSpecification() {
        return (root, query, cb) -> {
            // Apply fetch joins only for non-count queries
            Set<String> joinPaths = new HashSet<>();
            if (!query.getResultType().equals(Long.class)) {
                joinPaths = extractJoinPaths(condition.getNodes());
            }

            // Apply joins in correct order
            if (!joinPaths.isEmpty()) {
                applyJoins(root, joinPaths);
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

    private void applyJoins(Root<T> root, Set<String> paths) {
        // 1. Clear existing joins
        Set<Join<T, ?>> joins = (Set<Join<T, ?>>) root.getJoins();
        joins.clear();

        // 2. Find first ToMany path
        String toManyFetchPath = null;
        for (String path : paths) {
            if (isToManyPath(root, path)) {
                toManyFetchPath = path;
                break;
            }
        }

        // 3. Apply fetch joins first
        for (String path : paths) {
            if (!isToManyPath(root, path) || path.equals(toManyFetchPath)) {
                root.fetch(path, JoinType.LEFT);
            }
        }

        // 4. Apply regular joins for remaining ToMany paths
        for (String path : paths) {
            if (isToManyPath(root, path) && !path.equals(toManyFetchPath)) {
                root.join(path, JoinType.LEFT);
            }
        }
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
}
