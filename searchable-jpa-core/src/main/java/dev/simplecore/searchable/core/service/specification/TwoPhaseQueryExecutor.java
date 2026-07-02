package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Executes two-phase queries for ToMany relationship optimization.
 *
 * <p>Phase 1: query the matching IDs only, with conditions, sorting, and pagination.
 * <br>Phase 2: query the full entities using batched IN clauses with fetch joins.
 * <br>Phase 3: compute the total count for accurate pagination.
 *
 * <p>Composite-key entities ({@code @IdClass} / {@code @EmbeddedId}) are delegated to
 * {@link CompositeKeyQueryExecutor}. Single primary-key handling lives here.
 */
@Slf4j
public class TwoPhaseQueryExecutor<T> {

    // Maximum number of IDs in a single IN clause to prevent database limitations.
    // Oracle has a 1000 limit, so we use 500 for a safety margin.
    private static final int MAX_IN_CLAUSE_SIZE = 500;

    private final SearchCondition<?> condition;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final JoinStrategyManager<T> joinStrategyManager;

    // Cached join paths for reuse between Phase 1 and the count query within the same execution.
    private Set<String> cachedJoinPaths;
    // Lazily created only for composite-key entities.
    private CompositeKeyQueryExecutor<T> compositeKeyExecutor;

    public TwoPhaseQueryExecutor(SearchCondition<?> condition,
                                 EntityManager entityManager,
                                 Class<T> entityClass,
                                 JpaSpecificationExecutor<T> specificationExecutor) {
        this.condition = condition;
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.specificationExecutor = specificationExecutor;
        this.joinStrategyManager = new JoinStrategyManager<>(entityManager, entityClass);
    }

    /**
     * Executes the optimized two-phase query with automatic IN clause batching.
     *
     * @param pageRequest the pagination request
     * @param fetchFields the fields to explicitly fetch join in Phase 2
     */
    public Page<T> executeWithTwoPhaseOptimization(PageRequest pageRequest, Set<String> fetchFields) {
        this.cachedJoinPaths = extractJoinPaths();

        // Phase 1: get the IDs for the requested page.
        List<Object> ids = executePhaseOneQuery(pageRequest);

        // Phase 3: total count is always computed from the count query, independent of Phase 1,
        // so an over-the-end page offset still reports the true total.
        long totalCount = executeCountQuery();

        // Strip the internal composite-key ordering marker before exposing the pageable to callers.
        PageRequest responsePageRequest = sanitizePageRequest(pageRequest);

        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), responsePageRequest, totalCount);
        }

        // Phase 2: load the full entities for the collected IDs. Ordering is restored from the
        // Phase 1 ID order by reorderEntitiesByIds, so Phase 2 does not re-sort in the database.
        List<T> entities = executePhaseTwoQuery(ids, fetchFields);
        return new PageImpl<>(entities, responsePageRequest, totalCount);
    }

    /**
     * Phase 1: collect IDs for the requested page.
     */
    private List<Object> executePhaseOneQuery(PageRequest pageRequest) {
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);

        if (SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(primaryKeyField)) {
            CompositeKeyQueryExecutor<T> composite = compositeExecutor();
            if (!composite.hasIdFields()) {
                throw new SearchableOperationException(
                        MessageUtils.getMessage("executor.composite.key.not.found",
                                new Object[]{entityClass.getSimpleName()}));
            }
            return composite.executePhaseOne(pageRequest, cachedJoinPaths);
        }

        return executeRegularPhaseOneQuery(pageRequest, cachedJoinPaths, primaryKeyField);
    }

    private CompositeKeyQueryExecutor<T> compositeExecutor() {
        if (compositeKeyExecutor == null) {
            compositeKeyExecutor = new CompositeKeyQueryExecutor<>(
                    condition, entityManager, entityClass, specificationExecutor, joinStrategyManager);
        }
        return compositeKeyExecutor;
    }

    /**
     * Phase 1 for single primary-key entities.
     */
    private List<Object> executeRegularPhaseOneQuery(PageRequest pageRequest, Set<String> joinPaths, String primaryKeyField) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        boolean needsSortColumnsInSelect = pageRequest.getSort().isSorted()
                && !pageRequest.getSort().stream().allMatch(order -> order.getProperty().equals(primaryKeyField));

        if (needsSortColumnsInSelect) {
            // GROUP BY the primary key with deterministic aggregates so that sorting through a
            // to-many relationship cannot produce duplicate/missing IDs across page boundaries.
            CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
            Root<T> root = query.from(entityClass);
            joinStrategyManager.applyRegularJoinsOnly(root, joinPaths);

            List<Selection<?>> selections = new ArrayList<>();
            selections.add(root.get(primaryKeyField));

            List<Order> orders = new ArrayList<>();
            boolean primaryKeyOrdered = false;
            for (Sort.Order sortOrder : pageRequest.getSort()) {
                String property = sortOrder.getProperty();
                if (property.equals(primaryKeyField)) {
                    orders.add(sortOrder.isAscending()
                            ? cb.asc(root.get(primaryKeyField))
                            : cb.desc(root.get(primaryKeyField)));
                    primaryKeyOrdered = true;
                } else {
                    Path<?> sortPath = SpecificationQuerySupport.getPath(root, property);
                    Expression<?> aggregate = SpecificationQuerySupport.aggregateForSort(cb, sortPath, sortOrder.isAscending());
                    selections.add(aggregate);
                    orders.add(sortOrder.isAscending() ? cb.asc(aggregate) : cb.desc(aggregate));
                }
            }
            if (!primaryKeyOrdered) {
                orders.add(cb.asc(root.get(primaryKeyField)));
            }

            query.multiselect(selections);
            Predicate predicate = SpecificationQuerySupport.createPredicates(condition, entityManager, root, query, cb);
            if (predicate != null) {
                query.where(predicate);
            }
            query.groupBy(root.get(primaryKeyField));
            query.orderBy(orders);

            TypedQuery<Object[]> typedQuery = entityManager.createQuery(query);
            typedQuery.setFirstResult((int) pageRequest.getOffset());
            typedQuery.setMaxResults(pageRequest.getPageSize());

            return typedQuery.getResultList().stream()
                    .map(row -> row[0])
                    .collect(Collectors.toList());
        }

        // Simple ID-only select when sorting by the primary key or not sorting.
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<T> root = query.from(entityClass);
        joinStrategyManager.applyRegularJoinsOnly(root, joinPaths);

        query.select(root.get(primaryKeyField)).distinct(true);

        Predicate predicate = SpecificationQuerySupport.createPredicates(condition, entityManager, root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }

        if (pageRequest.getSort().isSorted()) {
            List<Order> orders = pageRequest.getSort().stream()
                    .map(sortOrder -> sortOrder.isAscending()
                            ? cb.asc(SpecificationQuerySupport.getPath(root, sortOrder.getProperty()))
                            : cb.desc(SpecificationQuerySupport.getPath(root, sortOrder.getProperty())))
                    .collect(Collectors.toList());
            query.orderBy(orders);
        }

        TypedQuery<Object> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageRequest.getOffset());
        typedQuery.setMaxResults(pageRequest.getPageSize());

        return typedQuery.getResultList();
    }

    /**
     * Phase 3: total count of matching records, guaranteeing distinct counting when joins are present.
     */
    private long executeCountQuery() {
        Set<String> joinPaths = (cachedJoinPaths != null) ? cachedJoinPaths : extractJoinPaths();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> root = countQuery.from(entityClass);
        joinStrategyManager.applyRegularJoinsOnly(root, joinPaths);

        if (joinPaths.isEmpty()) {
            countQuery.select(cb.count(root));
        } else if (compositeKeyExecutor != null) {
            // Composite key: count distinct keys via a portable concatenated-key expression.
            countQuery.select(cb.countDistinct(compositeKeyExecutor.buildDistinctCountKeyExpression(root, cb)));
        } else {
            String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
            countQuery.select(cb.countDistinct(root.get(primaryKeyField)));
        }

        Predicate predicate = SpecificationQuerySupport.createPredicates(condition, entityManager, root, countQuery, cb);
        if (predicate != null) {
            countQuery.where(predicate);
        }

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    /**
     * Phase 2: load entities for the collected IDs using batched IN queries, then restore Phase 1 order.
     */
    private List<T> executePhaseTwoQuery(List<Object> ids, Set<String> fetchFields) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> loaded = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += MAX_IN_CLAUSE_SIZE) {
            List<Object> batch = ids.subList(i, Math.min(i + MAX_IN_CLAUSE_SIZE, ids.size()));
            loaded.addAll(loadBatch(batch, fetchFields));
        }

        return reorderEntitiesByIds(loaded, ids);
    }

    private List<T> loadBatch(List<Object> ids, Set<String> fetchFields) {
        if (compositeKeyExecutor != null) {
            return compositeKeyExecutor.executePhaseTwo(ids, fetchFields);
        }

        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
        Specification<T> spec = (root, query, cb) -> {
            // Apply distinct so to-many fetch joins do not emit duplicate root rows.
            query.distinct(true);
            SpecificationQuerySupport.applyFetchJoins(root, query, fetchFields);
            return root.get(primaryKeyField).in(ids);
        };

        // No ORDER BY: the final order is restored by reorderEntitiesByIds from the Phase 1 IDs.
        return specificationExecutor.findAll(spec, Sort.unsorted());
    }

    /**
     * Reorders entities to match the ID order established in Phase 1.
     */
    private List<T> reorderEntitiesByIds(List<T> entities, List<Object> orderedIds) {
        Map<String, T> entityMap = new HashMap<>(entities.size());
        for (T entity : entities) {
            try {
                Object id = extractEntityId(entity);
                if (id != null) {
                    entityMap.put(createComparableKey(id), entity);
                }
            } catch (Exception e) {
                log.warn("Failed to get entity ID for reordering: {}", e.getMessage());
                return entities;
            }
        }

        List<T> reordered = new ArrayList<>(orderedIds.size());
        for (Object orderedId : orderedIds) {
            T entity = entityMap.get(createComparableKey(orderedId));
            if (entity != null) {
                reordered.add(entity);
            }
        }
        return reordered;
    }

    private Object extractEntityId(T entity) {
        if (compositeKeyExecutor != null) {
            return compositeKeyExecutor.extractId(entity);
        }
        // Single primary key: resolve the identifier from the persistence provider without
        // assuming a getId() method name.
        return entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }

    private String createComparableKey(Object id) {
        return (id instanceof Object[]) ? Arrays.toString((Object[]) id) : String.valueOf(id);
    }

    /**
     * Rebuilds the pageable without the internal composite-key ordering marker so it is safe to expose.
     */
    private PageRequest sanitizePageRequest(PageRequest pageRequest) {
        List<Sort.Order> cleaned = pageRequest.getSort().stream()
                .filter(order -> !SearchableFieldUtils.COMPOSITE_KEY_MARKER.equals(order.getProperty()))
                .collect(Collectors.toList());
        Sort sort = cleaned.isEmpty() ? Sort.unsorted() : Sort.by(cleaned);
        return PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize(), sort);
    }

    private Set<String> extractJoinPaths() {
        return SearchableFieldUtils.extractJoinPaths(condition.getNodes());
    }
}
